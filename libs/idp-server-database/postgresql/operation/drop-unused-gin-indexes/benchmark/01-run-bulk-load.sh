#!/usr/bin/env bash
# ================================================
# Bulk load N million rows into security_event using pgbench
# ================================================
#
# 目的:
#   ・本番 7000万データ規模での DROP INDEX 挙動を推測するため、
#     500万行クラスのデータを local に用意する
#
# 仕組み:
#   ・pgbench で並列に多重 INSERT (100 行/tx)
#   ・created_at は過去 31 日にランダム分散 → 複数 partition に書き込み
#   ・GIN を維持したまま load する (本番想定の書き込みコスト含む)
#
# 使い方:
#   ./01-run-bulk-load.sh                    # default: 500万行
#   TARGET_ROWS=10000000 ./01-run-bulk-load.sh
#   CLIENTS=16 ./01-run-bulk-load.sh         # 並列度上げる
#
# 環境変数:
#   TARGET_ROWS   目標行数 (default: 5000000)
#   CLIENTS       並列クライアント数 (default: 8)
#   JOBS          pgbench スレッド数 (default: 4)
#   CONTAINER     postgres コンテナ名 (default: postgres-primary)
#   PGUSER        DB user (default: idp)
#   PGDATABASE    DB name (default: idpserver)
# ================================================

set -u

TARGET_ROWS="${TARGET_ROWS:-5000000}"
CLIENTS="${CLIENTS:-8}"
JOBS="${JOBS:-4}"
CONTAINER="${CONTAINER:-postgres-primary}"
PGUSER="${PGUSER:-idp}"
PGDATABASE="${PGDATABASE:-idpserver}"

ROWS_PER_TX=100
TX_PER_CLIENT=$(( TARGET_ROWS / CLIENTS / ROWS_PER_TX ))
ACTUAL_TOTAL=$(( CLIENTS * TX_PER_CLIENT * ROWS_PER_TX ))

echo "============================================"
echo " Bulk load benchmark"
echo "============================================"
echo "  target rows:     ${TARGET_ROWS}"
echo "  rows / tx:       ${ROWS_PER_TX}"
echo "  clients:         ${CLIENTS}"
echo "  tx / client:     ${TX_PER_CLIENT}"
echo "  actual total:    ${ACTUAL_TOTAL}"
echo "  container:       ${CONTAINER}"
echo "============================================"

# 事前確認
BEFORE=$(docker exec "${CONTAINER}" psql -U "${PGUSER}" -d "${PGDATABASE}" -tAc "SELECT count(*) FROM security_event")
echo "Before:           ${BEFORE} rows"
echo ""

# pgbench script を container に転送
docker cp "$(dirname "$0")/bulk_insert.sql" "${CONTAINER}:/tmp/bulk_insert.sql"

# load 開始
START=$(date +%s)
echo "[$(date +%T)] Starting pgbench bulk load..."
echo ""

docker exec "${CONTAINER}" pgbench \
    -U "${PGUSER}" -d "${PGDATABASE}" \
    -f /tmp/bulk_insert.sql \
    -c "${CLIENTS}" -j "${JOBS}" \
    -t "${TX_PER_CLIENT}" \
    --no-vacuum -P 10 2>&1 | tail -30

END=$(date +%s)
DURATION=$(( END - START ))

# 事後確認
AFTER=$(docker exec "${CONTAINER}" psql -U "${PGUSER}" -d "${PGDATABASE}" -tAc "SELECT count(*) FROM security_event")
INSERTED=$(( AFTER - BEFORE ))
RATE=$(( INSERTED / (DURATION > 0 ? DURATION : 1) ))

echo ""
echo "============================================"
echo " Result"
echo "============================================"
echo "  duration:        ${DURATION}s"
echo "  inserted:        ${INSERTED} rows"
echo "  effective rate:  ${RATE} rows/sec"
echo "  before:          ${BEFORE} rows"
echo "  after:           ${AFTER} rows"
echo "============================================"

# 現在の GIN サイズ
echo ""
echo "--- Current GIN index size ---"
docker exec "${CONTAINER}" psql -U "${PGUSER}" -d "${PGDATABASE}" -c "
SELECT
    c.relname AS index,
    pg_size_pretty(
        COALESCE((
            SELECT SUM(pg_relation_size(child.oid))
            FROM pg_inherits inh JOIN pg_class child ON child.oid = inh.inhrelid
            WHERE inh.inhparent = c.oid
        ), 0)
    ) AS aggregated_size,
    (SELECT count(*) FROM pg_inherits inh WHERE inh.inhparent = c.oid) AS child_count
FROM pg_class c
WHERE c.relname = 'idx_events_detail_jsonb';
"

# partition ごとの分布
echo ""
echo "--- Row distribution across partitions (top 10 by size) ---"
docker exec "${CONTAINER}" psql -U "${PGUSER}" -d "${PGDATABASE}" -c "
SELECT
    inhrelid::regclass AS partition,
    pg_size_pretty(pg_relation_size(inhrelid)) AS table_size,
    pg_size_pretty(pg_total_relation_size(inhrelid)) AS total_with_idx
FROM pg_inherits
WHERE inhparent = 'security_event'::regclass
ORDER BY pg_total_relation_size(inhrelid) DESC
LIMIT 10;
"
