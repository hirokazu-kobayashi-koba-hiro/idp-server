#!/usr/bin/env bash
#
# Rotates the key encryption key (KEK) of a local docker compose stack and checks, phase by phase,
# that stored external tokens stay readable without being rewritten.
#
#   setup              current settings          link two accounts, remember their tokens
#   rotated            new active, old passive   tokens open; re-wrap the data keys
#   old-removed        new only                  tokens open; nothing needs the old KEK
#   removed-too-early  old only                  the status API reports unopenable data keys
#   restored           old active, new passive   re-wrap back; tokens open
#
# The stack is put back on its original settings at the end, also on failure.
# Not part of `npm test`: every phase needs a restart with other key settings. Run it before a
# release and after changing key handling.
#
# Usage (from the repository root or e2e/):  e2e/scripts/kek-rotation.sh

set -euo pipefail

E2E_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ROOT_DIR="$(cd "$E2E_DIR/.." && pwd)"
TMP_DIR="$E2E_DIR/.tmp"
DISCOVERY="https://api.local.test/67e7eae6-62b0-4500-9eff-87459f63fc66/.well-known/openid-configuration"

mkdir -p "$TMP_DIR"
cd "$ROOT_DIR"

ORIGINAL_KEY="$(grep '^ENCRYPTION_KEY=' .env | cut -d= -f2-)"
if [[ -z "$ORIGINAL_KEY" ]]; then
  echo "ENCRYPTION_KEY is not set in $ROOT_DIR/.env" >&2
  exit 1
fi
# The new KEK exists only for this run.
NEW_KEY="$(openssl rand -base64 32)"

restart() {
  local keys="$1" active="$2"
  echo "==> restart: active=${active:-default} keys=$(echo "$keys" | tr ',' '\n' | cut -d: -f1 | tr '\n' ' ')"
  ENCRYPTION_KEYS="$keys" ENCRYPTION_ACTIVE_KEY_ID="$active" \
    docker compose up -d --no-build --force-recreate idp-server-1 idp-server-2 >/dev/null 2>&1
  for _ in $(seq 1 60); do
    if [[ "$(curl -sk -o /dev/null -w '%{http_code}' "$DISCOVERY")" == "200" ]]; then
      docker compose exec -T nginx nginx -s reload >/dev/null 2>&1 || true
      sleep 3
      return 0
    fi
    sleep 5
  done
  echo "idp-server did not come up" >&2
  return 1
}

run_phase() {
  echo "==> phase: $1"
  (cd "$E2E_DIR" && KEY_ROTATION_PHASE="$1" npm test -- --testPathPattern="control_plane/system/kek-rotation")
}

trap 'restart "" "" || true' EXIT

restart "" ""
run_phase setup

restart "rotation-new:$NEW_KEY,default:$ORIGINAL_KEY" rotation-new
run_phase rotated

restart "rotation-new:$NEW_KEY" rotation-new
run_phase old-removed

restart "" ""
run_phase removed-too-early

restart "default:$ORIGINAL_KEY,rotation-new:$NEW_KEY" default
run_phase restored

echo "==> KEK rotation checks passed"
