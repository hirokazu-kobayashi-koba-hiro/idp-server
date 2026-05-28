#!/usr/bin/env bash
# ================================================
# 02. Drop parent partitioned GIN index with retry
# ================================================
#
# 戦略:
#   ・親 partitioned index DROP は ACCESS EXCLUSIVE が必要
#   ・常時 INSERT が走るため lock 競合の可能性
#   ・lock_timeout=200ms + リトライで「取れた瞬間に成功」を狙う
#   ・取れなくても lock_timeout で即時諦めるので INSERT は止まらない
#
# 実行タイミング:
#   ・traffic 最小の時間帯推奨 (例: 深夜 3:00 AM)
#   ・10-30 TPS なら 1-5 トライで成功する見込み
#
# 前提:
#   ・01-drop-children-bulk.sql を先に実行済み
#   ・今日の partition の子 GIN だけが残った状態
#
# 使い方:
#   ./02-drop-parent-retry.sh                # 環境変数から接続情報を取る
#   PGHOST=localhost PGPORT=5432 PGUSER=idp PGDATABASE=idpserver \
#     ./02-drop-parent-retry.sh
# ================================================

set -u

INDEX_NAME="${INDEX_NAME:-idx_events_detail_jsonb}"
MAX_ATTEMPTS="${MAX_ATTEMPTS:-50}"
LOCK_TIMEOUT="${LOCK_TIMEOUT:-200ms}"
RETRY_INTERVAL_SEC="${RETRY_INTERVAL_SEC:-2}"

echo "============================================"
echo " Drop parent partitioned GIN index"
echo "============================================"
echo "  index:           ${INDEX_NAME}"
echo "  max attempts:    ${MAX_ATTEMPTS}"
echo "  lock_timeout:    ${LOCK_TIMEOUT}"
echo "  retry interval:  ${RETRY_INTERVAL_SEC}s"
echo "  PGHOST:          ${PGHOST:-(default)}"
echo "  PGDATABASE:      ${PGDATABASE:-(default)}"
echo "  PGUSER:          ${PGUSER:-(default)}"
echo "============================================"

# 削除前確認
EXISTS=$(psql -tAc "SELECT 1 FROM pg_class WHERE relname = '${INDEX_NAME}' AND relkind = 'I'" 2>/dev/null)
if [[ -z "${EXISTS}" ]]; then
    echo "Index '${INDEX_NAME}' not found. Already dropped?"
    exit 0
fi

for ((i=1; i<=MAX_ATTEMPTS; i++)); do
    echo "[attempt ${i}/${MAX_ATTEMPTS}] trying DROP INDEX..."

    OUTPUT=$(psql -v ON_ERROR_STOP=1 -c "SET lock_timeout = '${LOCK_TIMEOUT}'; DROP INDEX ${INDEX_NAME};" 2>&1)
    RC=$?

    if [[ ${RC} -eq 0 ]]; then
        echo ""
        echo "SUCCESS on attempt ${i}"
        echo "============================================"

        # 確認
        STILL_EXISTS=$(psql -tAc "SELECT 1 FROM pg_class WHERE relname = '${INDEX_NAME}' AND relkind = 'I'" 2>/dev/null)
        if [[ -z "${STILL_EXISTS}" ]]; then
            echo "Verified: index no longer exists"
        else
            echo "WARNING: psql reported success but index still exists. Investigate."
            exit 2
        fi
        exit 0
    fi

    if echo "${OUTPUT}" | grep -q "canceling statement due to lock timeout"; then
        echo "  -> lock timeout (expected, retrying)"
    else
        echo "  -> unexpected error:"
        echo "${OUTPUT}" | sed 's/^/     /'
        echo ""
        echo "Aborting. Investigate the error above."
        exit 3
    fi

    sleep "${RETRY_INTERVAL_SEC}"
done

echo ""
echo "FAILED after ${MAX_ATTEMPTS} attempts."
echo "Retry later or increase MAX_ATTEMPTS."
echo "============================================"
exit 1
