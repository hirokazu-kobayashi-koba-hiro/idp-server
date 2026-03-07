#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
CLUSTER_NAME="idp-local"
NAMESPACE="idp"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log()  { echo -e "${GREEN}[INFO]${NC} $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
err()  { echo -e "${RED}[ERROR]${NC} $*" >&2; }

# -------------------------------------------------------
# 1. Prerequisites check
# -------------------------------------------------------
log "Checking prerequisites..."
for cmd in kind kubectl docker; do
  if ! command -v "$cmd" &>/dev/null; then
    err "$cmd is not installed. Please install it first."
    exit 1
  fi
done
log "All prerequisites met."

# -------------------------------------------------------
# 2. Create kind cluster (skip if exists)
# -------------------------------------------------------
if kind get clusters 2>/dev/null | grep -q "^${CLUSTER_NAME}$"; then
  log "Cluster '${CLUSTER_NAME}' already exists. Skipping creation."
else
  log "Creating kind cluster '${CLUSTER_NAME}'..."
  kind create cluster --config "$SCRIPT_DIR/kind-config.yaml"
fi

kubectl cluster-info --context "kind-${CLUSTER_NAME}"

# -------------------------------------------------------
# 3. Build Docker images
# -------------------------------------------------------
log "Building Docker images..."

docker build -t idp-server:latest "$PROJECT_ROOT" &
docker build -t idp-postgres-primary:15 "$PROJECT_ROOT/docker/postgresql/primary" &
docker build -t idp-postgres-user-init:latest \
  -f "$PROJECT_ROOT/libs/idp-server-database/Dockerfile-postgres-init" \
  "$PROJECT_ROOT/libs/idp-server-database" &
docker build -t idp-flyway-migrator:latest \
  -f "$PROJECT_ROOT/libs/idp-server-database/Dockerfile-flyway" \
  "$PROJECT_ROOT/libs/idp-server-database" &

wait
log "All images built."

# -------------------------------------------------------
# 4. Load images into kind
# -------------------------------------------------------
log "Loading images into kind..."
kind load docker-image --name "$CLUSTER_NAME" \
  idp-server:latest \
  idp-postgres-primary:15 \
  idp-postgres-user-init:latest \
  idp-flyway-migrator:latest
log "Images loaded."

# -------------------------------------------------------
# 5. Apply namespace + secret + configmap
# -------------------------------------------------------
log "Applying namespace, secrets, and configmap..."
kubectl apply -f "$SCRIPT_DIR/manifests/namespace.yaml"
kubectl apply -f "$SCRIPT_DIR/manifests/secret.yaml"
kubectl apply -f "$SCRIPT_DIR/manifests/configmap.yaml"

# -------------------------------------------------------
# 6. Create postgres-init-scripts ConfigMap from files
# -------------------------------------------------------
log "Creating postgres-init-scripts ConfigMap..."
kubectl -n "$NAMESPACE" delete configmap postgres-init-scripts --ignore-not-found
kubectl -n "$NAMESPACE" create configmap postgres-init-scripts \
  --from-file="00-init-app-user.sh=$PROJECT_ROOT/libs/idp-server-database/postgresql/init/00-init-app-user.sh" \
  --from-file="01-add-bypassrls.sh=$PROJECT_ROOT/libs/idp-server-database/postgresql/init/01-add-bypassrls.sh" \
  --from-file="02-init-partman.sh=$PROJECT_ROOT/libs/idp-server-database/postgresql/init/02-init-partman.sh"

# -------------------------------------------------------
# 7. Deploy PostgreSQL
# -------------------------------------------------------
log "Deploying PostgreSQL..."
kubectl apply -f "$SCRIPT_DIR/manifests/postgres.yaml"
log "Waiting for PostgreSQL pod to be created..."
until kubectl -n "$NAMESPACE" get pod postgres-0 &>/dev/null; do
  sleep 2
done
log "Waiting for PostgreSQL to be ready..."
kubectl -n "$NAMESPACE" wait --for=condition=Ready pod/postgres-0 --timeout=120s

# -------------------------------------------------------
# 8. Run postgres-user-init Job
# -------------------------------------------------------
log "Running postgres-user-init Job..."
kubectl -n "$NAMESPACE" delete job postgres-user-init --ignore-not-found
kubectl apply -f "$SCRIPT_DIR/manifests/job-postgres-user-init.yaml"
log "Waiting for postgres-user-init to complete..."
kubectl -n "$NAMESPACE" wait --for=condition=Complete job/postgres-user-init --timeout=120s

# -------------------------------------------------------
# 9. Run flyway-migrator Job
# -------------------------------------------------------
log "Running flyway-migrator Job..."
kubectl -n "$NAMESPACE" delete job flyway-migrator --ignore-not-found
kubectl apply -f "$SCRIPT_DIR/manifests/job-flyway.yaml"
log "Waiting for flyway-migrator to complete..."
kubectl -n "$NAMESPACE" wait --for=condition=Complete job/flyway-migrator --timeout=180s

# -------------------------------------------------------
# 10. Deploy Redis
# -------------------------------------------------------
log "Deploying Redis..."
kubectl apply -f "$SCRIPT_DIR/manifests/redis.yaml"
kubectl -n "$NAMESPACE" rollout status deployment/redis --timeout=60s

# -------------------------------------------------------
# 11. Deploy idp-server + Service + HPA
# -------------------------------------------------------
log "Deploying idp-server..."
kubectl apply -f "$SCRIPT_DIR/manifests/idp-server.yaml"
log "Waiting for idp-server rollout..."
kubectl -n "$NAMESPACE" rollout status deployment/idp-server --timeout=300s

# -------------------------------------------------------
# 12. Install Metrics Server (for HPA)
# -------------------------------------------------------
if ! kubectl get deployment metrics-server -n kube-system &>/dev/null; then
  log "Installing Metrics Server..."
  kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
  # Patch for kind (kubelet-insecure-tls)
  kubectl -n kube-system patch deployment metrics-server \
    --type='json' \
    -p='[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]'
  log "Metrics Server installed. CPU/memory metrics will be available in 1-2 minutes."
else
  log "Metrics Server already installed."
fi

# -------------------------------------------------------
# 13. Print connection info
# -------------------------------------------------------
echo ""
echo -e "${GREEN}================================================${NC}"
echo -e "${GREEN} idp-server is running on kind!${NC}"
echo -e "${GREEN}================================================${NC}"
echo ""
echo "  Health check:  curl http://localhost:8080/actuator/health"
echo "  Pods:          kubectl get pods -n idp"
echo "  HPA:           kubectl get hpa -n idp"
echo "  Metrics:       kubectl top pods -n idp  (available after 1-2 min)"
echo "  Logs:          kubectl logs -n idp -l app=idp-server -f"
echo "  Cleanup:       bash $SCRIPT_DIR/down.sh"
echo ""
