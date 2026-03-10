#!/usr/bin/env bash
set -euo pipefail

# =========================================================
# Karpenter + KWOK 動作検証スクリプト
#
# 1. Karpenter コントローラーの稼働確認
# 2. デモワークロードを 5 replicas にスケール → ノード追加確認
# 3. 0 replicas に戻す → Consolidation でノード削除確認
# =========================================================

CLUSTER_NAME="karpenter-kwok"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log()  { echo -e "${GREEN}[INFO]${NC} $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
err()  { echo -e "${RED}[ERROR]${NC} $*" >&2; }

# -------------------------------------------------------
# 0. Switch context
# -------------------------------------------------------
kubectl config use-context "kind-${CLUSTER_NAME}"

# -------------------------------------------------------
# 1. Karpenter controller health check
# -------------------------------------------------------
log "Checking Karpenter controller..."
KARPENTER_PHASE=$(kubectl -n kube-system get pods \
  -l app.kubernetes.io/name=karpenter \
  -o jsonpath='{.items[0].status.phase}' 2>/dev/null || echo "NotFound")

if [ "$KARPENTER_PHASE" = "Running" ]; then
  log "Karpenter controller is Running."
else
  err "Karpenter controller is not running (phase: ${KARPENTER_PHASE})."
  kubectl -n kube-system get pods -l app.kubernetes.io/name=karpenter
  exit 1
fi

INITIAL_NODE_COUNT=$(kubectl get nodes --no-headers | wc -l | tr -d ' ')
log "Initial node count: ${INITIAL_NODE_COUNT}"

# -------------------------------------------------------
# 2. Scale up → KWOK node provisioning
# -------------------------------------------------------
log "Scaling demo-inflate to 5 replicas..."
kubectl scale deployment demo-inflate --replicas=5

log "Waiting for KWOK nodes to be provisioned (up to 60s)..."
KWOK_PROVISIONED=false
for i in $(seq 1 30); do
  CURRENT_NODE_COUNT=$(kubectl get nodes --no-headers | wc -l | tr -d ' ')
  if [ "$CURRENT_NODE_COUNT" -gt "$INITIAL_NODE_COUNT" ]; then
    log "KWOK nodes provisioned! Node count: ${INITIAL_NODE_COUNT} -> ${CURRENT_NODE_COUNT}"
    KWOK_PROVISIONED=true
    break
  fi
  sleep 2
done

if [ "$KWOK_PROVISIONED" = false ]; then
  err "Timed out waiting for KWOK nodes to be provisioned."
  echo ""
  log "=== Nodes ==="
  kubectl get nodes -o wide
  echo ""
  log "=== Pending Pods ==="
  kubectl get pods --field-selector=status.phase=Pending -o wide
  echo ""
  log "=== Karpenter Logs (last 20 lines) ==="
  kubectl -n kube-system logs -l app.kubernetes.io/name=karpenter --tail=20
  exit 1
fi

echo ""
log "=== Nodes after scale-up ==="
kubectl get nodes -o wide
echo ""
log "=== Pods after scale-up ==="
kubectl get pods -o wide

# -------------------------------------------------------
# 3. Scale down → Consolidation
# -------------------------------------------------------
log "Scaling demo-inflate to 0 replicas..."
kubectl scale deployment demo-inflate --replicas=0

log "Waiting for consolidation (up to 120s)..."
CONSOLIDATED=false
for i in $(seq 1 60); do
  CURRENT_NODE_COUNT=$(kubectl get nodes --no-headers | wc -l | tr -d ' ')
  if [ "$CURRENT_NODE_COUNT" -eq "$INITIAL_NODE_COUNT" ]; then
    log "All KWOK nodes removed by consolidation!"
    CONSOLIDATED=true
    break
  fi
  sleep 2
done

if [ "$CONSOLIDATED" = false ]; then
  warn "KWOK nodes not yet fully consolidated. This may take more time."
  warn "Current node count: $(kubectl get nodes --no-headers | wc -l | tr -d ' ')"
fi

echo ""
log "=== Final Nodes ==="
kubectl get nodes -o wide
echo ""
log "=== NodePool Status ==="
kubectl get nodepool default -o wide 2>/dev/null || kubectl get nodepool default

echo ""
log "Verification complete."
