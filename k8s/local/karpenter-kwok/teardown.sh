#!/usr/bin/env bash
set -euo pipefail

# =========================================================
# Karpenter + KWOK 検証環境クリーンアップ
# =========================================================

CLUSTER_NAME="karpenter-kwok"
KARPENTER_CLONE_DIR="/tmp/karpenter"

# Colors
GREEN='\033[0;32m'
NC='\033[0m'

log() { echo -e "${GREEN}[INFO]${NC} $*"; }

# -------------------------------------------------------
# 1. Delete kind cluster
# -------------------------------------------------------
if kind get clusters 2>/dev/null | grep -q "^${CLUSTER_NAME}$"; then
  log "Deleting kind cluster '${CLUSTER_NAME}'..."
  kind delete cluster --name "$CLUSTER_NAME"
else
  log "Cluster '${CLUSTER_NAME}' does not exist. Skipping."
fi

# -------------------------------------------------------
# 2. Clean up Karpenter clone directory
# -------------------------------------------------------
if [ -d "$KARPENTER_CLONE_DIR" ]; then
  log "Removing Karpenter clone at ${KARPENTER_CLONE_DIR}..."
  rm -rf "$KARPENTER_CLONE_DIR"
fi

log "Done."
