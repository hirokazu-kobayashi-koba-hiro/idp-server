#!/bin/bash
#
# Deadlock reproduction test runner
#
# Strategy:
#   Put pg_sleep on authentication_interactions (not authentication_transaction).
#   This slows CASCADE DELETE per-row, keeping B's transaction open and holding
#   locks on auth_interactions rows while A returns from the FIDO mock and starts
#   its own DB writes.
#
#   With 3 interaction rows × 2s sleep = 6s CASCADE window.
#   A's FIDO mock returns in 2s → A starts DB writes while B is mid-CASCADE.
#
# Usage: ./run-deadlock-test.sh [SLEEP_SECONDS]
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
E2E_DIR="$(cd "$SCRIPT_DIR/../../../" && pwd)"

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_USER="${DB_USER:-idp}"
DB_NAME="${DB_NAME:-idpserver}"
DB_PASSWORD="${DB_PASSWORD:-WftkeJdd2odzHwWoIJa00YDP9qgm1DjP}"

SLEEP_SECONDS="${1:-2}"

export PGPASSWORD="$DB_PASSWORD"

psql_exec() {
  psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c "$1" 2>&1
}

cleanup() {
  echo ""
  echo "3. Cleaning up triggers..."
  psql_exec "
    DROP TRIGGER IF EXISTS trg_delay_cascade_delete_interactions ON authentication_interactions;
    DROP FUNCTION IF EXISTS delay_cascade_delete_interactions();
  "
  echo "   Triggers removed."
}
trap cleanup EXIT

echo "=== Deadlock Reproduction Test ==="
echo "   sleep per interaction row: ${SLEEP_SECONDS}s"
echo ""

echo "1. Creating BEFORE DELETE trigger on authentication_interactions with pg_sleep(${SLEEP_SECONDS}s)..."
psql_exec "
CREATE OR REPLACE FUNCTION delay_cascade_delete_interactions()
RETURNS TRIGGER AS \$\$
BEGIN
  RAISE NOTICE 'delay_cascade_delete_interactions: sleeping %s sec for tx_id=%, type=%',
    ${SLEEP_SECONDS}, OLD.authentication_transaction_id, OLD.interaction_type;
  PERFORM pg_sleep(${SLEEP_SECONDS});
  RETURN OLD;
END;
\$\$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_delay_cascade_delete_interactions ON authentication_interactions;
CREATE TRIGGER trg_delay_cascade_delete_interactions
  BEFORE DELETE ON authentication_interactions
  FOR EACH ROW
  EXECUTE FUNCTION delay_cascade_delete_interactions();
"
echo "   Trigger created on authentication_interactions."
echo ""

echo "2. Running deadlock test..."
echo ""
cd "$E2E_DIR"

TEST_EXIT_CODE=0
npm test -- \
  --testPathPattern="monkey/concurrent-authentication-transaction" \
  --testNamePattern="concurrent fido-uaf-authentication should not cause deadlock" \
  --testTimeout=30000 \
  2>&1 || TEST_EXIT_CODE=$?

echo ""
if [ $TEST_EXIT_CODE -ne 0 ]; then
  echo "=== TEST FAILED (exit code: $TEST_EXIT_CODE) ==="
  echo "   Deadlock or error was reproduced!"
else
  echo "=== TEST PASSED ==="
  echo "   No deadlock detected."
fi

exit $TEST_EXIT_CODE
