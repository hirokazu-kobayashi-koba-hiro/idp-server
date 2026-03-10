#!/usr/bin/env bash
set -euo pipefail

# =========================================================
# Karpenter + KWOK ローカル検証環境セットアップ
#
# Kind クラスタ上に KWOK プロバイダーを使った Karpenter を
# デプロイし、仮想ノードによるオートスケーリングを検証する。
# =========================================================

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CLUSTER_NAME="karpenter-kwok"
KARPENTER_VERSION="v1.8.2"
KARPENTER_CLONE_DIR="/tmp/karpenter"

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
for cmd in kind kubectl docker go helm; do
  if ! command -v "$cmd" &>/dev/null; then
    err "$cmd is not installed. Please install it first."
    exit 1
  fi
done
log "All prerequisites met."

# Ensure GOPATH/bin is in PATH (for ko, controller-gen, etc.)
export PATH="$(go env GOPATH)/bin:$PATH"

# Install ko if missing
if ! command -v ko &>/dev/null; then
  log "Installing ko..."
  go install github.com/google/ko@latest
fi


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
# 3. Clone Karpenter repository
# -------------------------------------------------------
if [ -d "$KARPENTER_CLONE_DIR/.git" ]; then
  log "Karpenter repository already exists at ${KARPENTER_CLONE_DIR}."
  cd "$KARPENTER_CLONE_DIR"
  git fetch --tags
  git checkout "$KARPENTER_VERSION"
else
  log "Cloning Karpenter ${KARPENTER_VERSION}..."
  git clone --branch "$KARPENTER_VERSION" --depth 1 \
    https://github.com/kubernetes-sigs/karpenter.git "$KARPENTER_CLONE_DIR"
  cd "$KARPENTER_CLONE_DIR"
fi

# -------------------------------------------------------
# 4. Install KWOK into the cluster
# -------------------------------------------------------
log "Installing KWOK..."
export KIND_CLUSTER_NAME="$CLUSTER_NAME"
make install-kwok

# -------------------------------------------------------
# 5. Build and deploy Karpenter with KWOK provider
#
# Makefile の apply-with-kind は verify → build-with-kind の順で実行するが、
# verify は CI 向けの完全な検証（golangci-lint, helm-docs, actionlint 等
# 13個以上のツールが必要）のためローカルでは不要。
# ここでは build-with-kind 相当のビルドと helm install を直接実行する。
# -------------------------------------------------------
log "Building Karpenter controller image..."
export KO_DOCKER_REPO="kind.local"

# go generate で CRD と DeepCopy を生成（controller-gen が必要）
if ! command -v controller-gen &>/dev/null; then
  log "Installing controller-gen..."
  go install sigs.k8s.io/controller-tools/cmd/controller-gen@latest
fi
go generate ./...
# controller-tools の既知バグ回避: parameterized types が未展開になる
# https://github.com/kubernetes-sigs/controller-tools/issues/756
perl -i -pe 's/sets\.Set/sets.Set[string]/g' pkg/scheduling/zz_generated.deepcopy.go
# CRD を kwok/charts にコピー（Helm chart に含めるため）
cp -r pkg/apis/crds kwok/charts/

# ko build でコンテナイメージをビルドし Kind に直接ロード
CONTROLLER_IMG=$(KO_DOCKER_REPO="${KO_DOCKER_REPO}" ko build sigs.k8s.io/karpenter/kwok)
IMG_REPOSITORY=$(echo "$CONTROLLER_IMG" | cut -d ":" -f 1)
IMG_TAG="latest"

log "Deploying Karpenter controller via Helm..."
kubectl apply -f kwok/charts/crds
helm upgrade --install karpenter kwok/charts --namespace kube-system --skip-crds \
  --set logLevel=debug \
  --set controller.resources.requests.cpu=1 \
  --set controller.resources.requests.memory=1Gi \
  --set controller.resources.limits.cpu=1 \
  --set controller.resources.limits.memory=1Gi \
  --set settings.featureGates.nodeRepair=true \
  --set settings.featureGates.staticCapacity=true \
  --set controller.image.repository="$IMG_REPOSITORY" \
  --set controller.image.tag="$IMG_TAG"

# -------------------------------------------------------
# 6. Wait for Karpenter controller
# -------------------------------------------------------
log "Waiting for Karpenter controller to be ready..."
kubectl -n kube-system wait --for=condition=Ready pod \
  -l app.kubernetes.io/name=karpenter --timeout=120s

# -------------------------------------------------------
# 7. Taint control-plane node
#
# CriticalAddonsOnly:NoSchedule により、デモ Pod が
# コントロールプレーンにスケジュールされることを防ぐ。
# Karpenter 等のシステム Pod は toleration を持つため影響なし。
# -------------------------------------------------------
log "Tainting control-plane node..."
CONTROL_PLANE_NODE=$(kubectl get nodes \
  -l node-role.kubernetes.io/control-plane \
  -o jsonpath='{.items[0].metadata.name}')
kubectl taint nodes "$CONTROL_PLANE_NODE" \
  CriticalAddonsOnly:NoSchedule --overwrite 2>/dev/null || true

# -------------------------------------------------------
# 8. Apply NodePool + KWOKNodeClass
# -------------------------------------------------------
log "Applying NodePool and KWOKNodeClass..."
kubectl apply -f "$SCRIPT_DIR/manifests/nodepool.yaml"

# -------------------------------------------------------
# 9. Apply demo workload (0 replicas)
# -------------------------------------------------------
log "Applying demo workload (0 replicas)..."
kubectl apply -f "$SCRIPT_DIR/manifests/demo-workload.yaml"

# -------------------------------------------------------
# 10. Print usage info
# -------------------------------------------------------
echo ""
echo -e "${GREEN}================================================${NC}"
echo -e "${GREEN} Karpenter + KWOK environment is ready!${NC}"
echo -e "${GREEN}================================================${NC}"
echo ""
echo "  Scale up:      kubectl scale deployment demo-inflate --replicas=5"
echo "  Watch nodes:   kubectl get nodes -w"
echo "  Scale down:    kubectl scale deployment demo-inflate --replicas=0"
echo "  Karpenter log: kubectl -n kube-system logs -l app.kubernetes.io/name=karpenter -f"
echo "  Verify:        bash $SCRIPT_DIR/verify.sh"
echo "  Cleanup:       bash $SCRIPT_DIR/teardown.sh"
echo ""
