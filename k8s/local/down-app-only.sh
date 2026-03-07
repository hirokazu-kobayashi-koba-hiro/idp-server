#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
CLUSTER_NAME="idp-local"
COMPOSE_FILE="$PROJECT_ROOT/docker-compose-kind.yaml"

echo "Deleting kind cluster '${CLUSTER_NAME}'..."
kind delete cluster --name "$CLUSTER_NAME"

echo "Stopping Docker Compose (kind mode)..."
docker compose -f "$COMPOSE_FILE" down

echo "Done."
