#!/usr/bin/env bash
set -euo pipefail

# =========================================================
# idp-server だけ kind にデプロイ（DB/Redis/nginx は Docker Compose）
#
# 構成:
#   docker-compose-kind.yaml : postgres, redis, nginx
#   kind cluster             : idp-server Pods + HPA
#
# リクエストフロー:
#   https://api.local.dev → nginx → kind NodePort → idp-server
#   idp-server → host.docker.internal → postgres/redis
# =========================================================

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
CLUSTER_NAME="idp-local"
NAMESPACE="idp"
ENV_FILE="$PROJECT_ROOT/.env"
COMPOSE_FILE="$PROJECT_ROOT/docker-compose-kind.yaml"

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

if [ ! -f "$ENV_FILE" ]; then
  err ".env file not found at $ENV_FILE"
  err "Run: cp .env.example .env && ./init-generate-env.sh postgresql"
  exit 1
fi

CERTS_DIR="$PROJECT_ROOT/docker/nginx/certs"
if [ ! -d "$CERTS_DIR" ]; then
  err "TLS certificates not found at $CERTS_DIR"
  err "Run: ./scripts/setup-local-subdomain.sh"
  exit 1
fi

# -------------------------------------------------------
# 2. Stop default Docker Compose (port conflict)
# -------------------------------------------------------
log "Stopping default Docker Compose services (if running)..."
docker compose -f "$PROJECT_ROOT/docker-compose.yaml" down 2>/dev/null || true

# -------------------------------------------------------
# 3. Start kind-specific Docker Compose (DB/Redis/nginx)
# -------------------------------------------------------
log "Starting Docker Compose (kind mode)..."
docker compose -f "$COMPOSE_FILE" up -d --build

log "Waiting for PostgreSQL to be healthy..."
until docker inspect --format='{{.State.Health.Status}}' postgres-primary 2>/dev/null | grep -q "healthy"; do
  sleep 2
done
log "PostgreSQL is healthy."

log "Waiting for init jobs to complete..."
# postgres-user-init → flyway → pg-cron-setup は depends_on で順序制御済み
# 最後の pg-cron-setup の終了を待つ
for i in $(seq 1 60); do
  STATUS=$(docker inspect --format='{{.State.Status}}' idp-server-pg-cron-setup-1 2>/dev/null || echo "not_found")
  if [ "$STATUS" = "exited" ]; then
    EXIT_CODE=$(docker inspect --format='{{.State.ExitCode}}' idp-server-pg-cron-setup-1 2>/dev/null || echo "1")
    if [ "$EXIT_CODE" = "0" ]; then
      log "All init jobs completed successfully."
      break
    else
      err "pg-cron-setup exited with code $EXIT_CODE"
      exit 1
    fi
  fi
  sleep 3
done

log "Waiting for Redis..."
until docker inspect --format='{{.State.Status}}' redis-kind 2>/dev/null | grep -q "running"; do
  sleep 1
done
log "Docker Compose services are ready."

# -------------------------------------------------------
# 4. Load .env
# -------------------------------------------------------
log "Loading .env..."
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

# -------------------------------------------------------
# 5. Create kind cluster (skip if exists)
# -------------------------------------------------------
if kind get clusters 2>/dev/null | grep -q "^${CLUSTER_NAME}$"; then
  log "Cluster '${CLUSTER_NAME}' already exists. Skipping creation."
  kind export kubeconfig --name "$CLUSTER_NAME"
else
  log "Creating kind cluster '${CLUSTER_NAME}'..."
  kind create cluster --config "$SCRIPT_DIR/kind-config.yaml"
fi

kubectl cluster-info --context "kind-${CLUSTER_NAME}"

# -------------------------------------------------------
# 6. Build & load idp-server image only
# -------------------------------------------------------
log "Building idp-server image..."
docker build -t idp-server:latest "$PROJECT_ROOT"

log "Loading image into kind..."
kind load docker-image --name "$CLUSTER_NAME" idp-server:latest

# -------------------------------------------------------
# 7. Apply namespace
# -------------------------------------------------------
log "Applying namespace..."
kubectl apply -f "$SCRIPT_DIR/manifests/namespace.yaml"

# -------------------------------------------------------
# 8. Create Secret from .env values
# -------------------------------------------------------
log "Creating secrets from .env..."
kubectl -n "$NAMESPACE" delete secret idp-secrets --ignore-not-found
kubectl -n "$NAMESPACE" create secret generic idp-secrets \
  --from-literal="IDP_SERVER_API_KEY=${IDP_SERVER_API_KEY}" \
  --from-literal="IDP_SERVER_API_SECRET=${IDP_SERVER_API_SECRET}" \
  --from-literal="ENCRYPTION_KEY=${ENCRYPTION_KEY}" \
  --from-literal="IDP_DB_ADMIN_PASSWORD=${IDP_DB_ADMIN_PASSWORD}" \
  --from-literal="IDP_DB_APP_PASSWORD=${IDP_DB_APP_PASSWORD}"

# mkcert rootCA を Secret としてロード（entrypoint.sh が /app/certs/rootCA.pem を読む）
kubectl -n "$NAMESPACE" delete secret root-ca-cert --ignore-not-found
kubectl -n "$NAMESPACE" create secret generic root-ca-cert \
  --from-file=rootCA.pem="$CERTS_DIR/rootCA.pem"

# -------------------------------------------------------
# 9. Create ConfigMap from .env + kind overrides
#
# docker-compose.yaml と同一の環境変数を設定。
# 差分は DB/Redis の接続先のみ（host.docker.internal 経由）。
# -------------------------------------------------------
log "Creating configmap from .env..."
kubectl -n "$NAMESPACE" delete configmap idp-config --ignore-not-found
kubectl -n "$NAMESPACE" create configmap idp-config \
  --from-literal="SERVER_URL=https://api.local.dev" \
  --from-literal="ADMIN_TENANT_ID=${ADMIN_TENANT_ID}" \
  --from-literal="DATABASE_TYPE=POSTGRESQL" \
  --from-literal="DB_WRITER_URL=jdbc:postgresql://host.docker.internal:5432/idpserver" \
  --from-literal="DB_WRITER_USER_NAME=idp_app_user" \
  --from-literal="DB_WRITER_TIMEOUT=2000" \
  --from-literal="DB_WRITER_MAX_POOL_SIZE=30" \
  --from-literal="DB_WRITER_MIN_IDLE=5" \
  --from-literal="DB_READER_URL=jdbc:postgresql://host.docker.internal:5433/idpserver" \
  --from-literal="DB_READER_USER_NAME=idp_app_user" \
  --from-literal="DB_READER_TIMEOUT=2000" \
  --from-literal="DB_READER_MAX_POOL_SIZE=10" \
  --from-literal="DB_READER_MIN_IDLE=5" \
  --from-literal="CONTROL_PLANE_DB_WRITER_URL=jdbc:postgresql://host.docker.internal:5432/idpserver" \
  --from-literal="CONTROL_PLANE_DB_WRITER_USER_NAME=idp_admin_user" \
  --from-literal="CONTROL_PLANE_DB_WRITER_TIMEOUT=2000" \
  --from-literal="CONTROL_PLANE_DB_WRITER_MAX_POOL_SIZE=10" \
  --from-literal="CONTROL_PLANE_DB_WRITER_MIN_IDLE=5" \
  --from-literal="CONTROL_PLANE_DB_READER_URL=jdbc:postgresql://host.docker.internal:5433/idpserver" \
  --from-literal="CONTROL_PLANE_DB_READER_USER_NAME=idp_admin_user" \
  --from-literal="CONTROL_PLANE_DB_READER_TIMEOUT=2000" \
  --from-literal="CONTROL_PLANE_DB_READER_MAX_POOL_SIZE=10" \
  --from-literal="CONTROL_PLANE_DB_READER_MIN_IDLE=5" \
  --from-literal="REDIS_HOST=host.docker.internal" \
  --from-literal="REDIS_PORT=6379" \
  --from-literal="CACHE_ENABLE=true" \
  --from-literal="CACHE_TIME_TO_LIVE_SECOND=300" \
  --from-literal="TOKEN_CACHE_ENABLED=${TOKEN_CACHE_ENABLED:-true}" \
  --from-literal="REDIS_CACHE_DATABASE=1" \
  --from-literal="REDIS_CACHE_PASSWORD=" \
  --from-literal="REDIS_CACHE_TIMEOUT=10000" \
  --from-literal="SESSION_REDIS_ENABLE=true" \
  --from-literal="REDIS_SESSION_DATABASE=0" \
  --from-literal="REDIS_SESSION_PASSWORD=" \
  --from-literal="REDIS_SESSION_TIMEOUT=10000" \
  --from-literal="REDIS_SESSION_MAX_TOTAL=20" \
  --from-literal="REDIS_SESSION_MAX_IDLE=3" \
  --from-literal="REDIS_SESSION_MIN_IDLE=2" \
  --from-literal="IDP_AUTH_SESSION_SAME_SITE=${IDP_AUTH_SESSION_SAME_SITE:-Lax}" \
  --from-literal="IDP_AUTH_SESSION_SECURE=true" \
  --from-literal="IDP_SERVER_SHUTDOWN_DELAY=5s" \
  --from-literal="SERVER_TOMCAT_THREADS_MAX=300" \
  --from-literal="SERVER_TOMCAT_THREADS_MIN_SPARE=50" \
  --from-literal="LOGGING_LEVEL_ROOT=info" \
  --from-literal="LOGGING_LEVEL_WEB=info" \
  --from-literal="LOGGING_LEVEL_IDP_SERVER_AUTHENTICATION_INTERACTORS=debug" \
  --from-literal="LOGGING_LEVEL_IDP_SERVER_PLATFORM=debug" \
  --from-literal="LOGGING_LEVEL_IDP_SERVER_HTTP_REQUEST_EXECUTOR=debug" \
  --from-literal="LOGGING_LEVEL_IDP_SERVER_CORE_OPENID=${LOGGING_LEVEL_IDP_SERVER_CORE_OPENID:-debug}" \
  --from-literal="LOGGING_LEVEL_IDP_SERVER_CORE_ADAPTERS=${LOGGING_LEVEL_IDP_SERVER_CORE_ADAPTERS:-debug}" \
  --from-literal="LOGGING_LEVEL_IDP_SERVER_ADAPTERS_SPRING_BOOT=${LOGGING_LEVEL_IDP_SERVER_ADAPTERS_SPRING_BOOT:-debug}" \
  --from-literal="LOGGING_LEVEL_IDP_SERVER_AUTHENTICATORS_WEBAUTHN4J=${LOGGING_LEVEL_IDP_SERVER_AUTHENTICATORS_WEBAUTHN4J:-debug}" \
  --from-literal="LOGGING_LEVEL_REQUEST_RESPONSE_LOGGING_FILTER=${LOGGING_LEVEL_REQUEST_RESPONSE_LOGGING_FILTER:-debug}" \
  --from-literal="IDP_LOGGING_REQUEST_RESPONSE_ENABLED=${IDP_LOGGING_REQUEST_RESPONSE_ENABLED:-false}" \
  --from-literal="IDP_LOGGING_REQUEST_RESPONSE_MASK_TOKENS=${IDP_LOGGING_REQUEST_RESPONSE_MASK_TOKENS:-true}" \
  --from-literal="IDP_LOGGING_REQUEST_RESPONSE_MAX_BODY_SIZE=${IDP_LOGGING_REQUEST_RESPONSE_MAX_BODY_SIZE:-10000}" \
  --from-literal="IDP_LOGGING_REQUEST_RESPONSE_ENDPOINTS=${IDP_LOGGING_REQUEST_RESPONSE_ENDPOINTS:-/v1/tokens,/v1/authorizations,/v1/backchannel/authentications,/v1/userinfo}" \
  --from-literal="SECURITY_EVENT_CORE_POOL_SIZE=${SECURITY_EVENT_CORE_POOL_SIZE:-5}" \
  --from-literal="SECURITY_EVENT_MAX_POOL_SIZE=${SECURITY_EVENT_MAX_POOL_SIZE:-30}" \
  --from-literal="SECURITY_EVENT_QUEUE_CAPACITY=${SECURITY_EVENT_QUEUE_CAPACITY:-5000}" \
  --from-literal="SECURITY_EVENT_RETRY_QUEUE_CAPACITY=${SECURITY_EVENT_RETRY_QUEUE_CAPACITY:-5000}" \
  --from-literal="USER_LIFECYCLE_EVENT_CORE_POOL_SIZE=${USER_LIFECYCLE_EVENT_CORE_POOL_SIZE:-5}" \
  --from-literal="USER_LIFECYCLE_EVENT_MAX_POOL_SIZE=${USER_LIFECYCLE_EVENT_MAX_POOL_SIZE:-10}" \
  --from-literal="USER_LIFECYCLE_EVENT_QUEUE_CAPACITY=${USER_LIFECYCLE_EVENT_QUEUE_CAPACITY:-1000}" \
  --from-literal="AUDIT_LOG_CORE_POOL_SIZE=${AUDIT_LOG_CORE_POOL_SIZE:-5}" \
  --from-literal="AUDIT_LOG_MAX_POOL_SIZE=${AUDIT_LOG_MAX_POOL_SIZE:-30}" \
  --from-literal="AUDIT_LOG_QUEUE_CAPACITY=${AUDIT_LOG_QUEUE_CAPACITY:-5000}" \
  --from-literal="JAVA_TOOL_OPTIONS=-Xms256m -Xmx1g -XX:MaxGCPauseMillis=100"

# -------------------------------------------------------
# 10. Deploy idp-server + Service + HPA
# -------------------------------------------------------
log "Deploying idp-server..."
kubectl apply -f "$SCRIPT_DIR/manifests/idp-server.yaml"

# -------------------------------------------------------
# 10.1 hostAliases: ローカルドメイン → Docker ホスト
#
# Pod 内から api.local.dev 等へアクセスするため、
# host.docker.internal の実 IP を /etc/hosts に追加する。
# （フェデレーション callback など自分自身への HTTP 呼び出しに必要）
# -------------------------------------------------------
log "Configuring hostAliases for local domains..."
HOST_IP=$(docker exec "${CLUSTER_NAME}-control-plane" getent hosts host.docker.internal | awk '{print $1}')
if [ -z "$HOST_IP" ]; then
  warn "Could not resolve host.docker.internal. Skipping hostAliases."
else
  log "host.docker.internal -> $HOST_IP"
  kubectl -n "$NAMESPACE" patch deployment idp-server --type='json' -p="[
    {\"op\":\"replace\",\"path\":\"/spec/template/spec/hostAliases\",\"value\":[
      {\"ip\":\"${HOST_IP}\",\"hostnames\":[\"api.local.dev\",\"mtls.api.local.dev\",\"auth.local.dev\",\"auth.idp.local\",\"auth-cp.idp.local\",\"sample.local.dev\"]}
    ]}
  ]"
fi

log "Waiting for idp-server rollout..."
kubectl -n "$NAMESPACE" rollout status deployment/idp-server --timeout=300s

# -------------------------------------------------------
# 11. Install Metrics Server (for HPA)
# -------------------------------------------------------
if ! kubectl get deployment metrics-server -n kube-system &>/dev/null; then
  log "Installing Metrics Server..."
  kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
  kubectl -n kube-system patch deployment metrics-server \
    --type='json' \
    -p='[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]'
  log "Metrics Server installed. CPU/memory metrics will be available in 1-2 minutes."
else
  log "Metrics Server already installed."
fi

# -------------------------------------------------------
# 12. Print connection info
# -------------------------------------------------------
echo ""
echo -e "${GREEN}================================================${NC}"
echo -e "${GREEN} idp-server on kind is ready!${NC}"
echo -e "${GREEN}================================================${NC}"
echo ""
echo "  構成:"
echo "    docker-compose-kind.yaml : postgres, redis, nginx"
echo "    kind cluster (idp-local) : idp-server Pods + HPA"
echo ""
echo "  リクエストフロー:"
echo "    https://api.local.dev → nginx → kind → idp-server Pods"
echo "    idp-server → host.docker.internal → postgres/redis"
echo ""
echo "  Health (direct):  curl http://localhost:8080/actuator/health"
echo "  Health (nginx):   curl -k https://api.local.dev/actuator/health"
echo "  Pods:             kubectl get pods -n idp"
echo "  HPA:              kubectl get hpa -n idp"
echo "  Metrics:          kubectl top pods -n idp  (available after 1-2 min)"
echo "  Logs:             kubectl logs -n idp -l app=idp-server -f"
echo "  Cleanup:          bash $SCRIPT_DIR/down-app-only.sh"
echo ""
