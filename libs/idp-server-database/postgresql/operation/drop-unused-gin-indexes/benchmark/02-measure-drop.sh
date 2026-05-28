#!/usr/bin/env bash
# ================================================
# Measure DROP INDEX timing with detailed timing breakdown
# ================================================
#
# 目的:
#   ・現在のデータ量での DROP INDEX 所要時間を計測
#   ・lock 保持時間 / 全体時間 / 削減サイズを記録
#   ・複数 scale で記録すれば外挿に使える
#
# 使い方:
#   ./02-measure-drop.sh             # 1 回計測
#   RUNS=5 ./02-measure-drop.sh      # 5 回計測 (rollback して繰り返し)
#
# 出力:
#   各 run の wall-clock 時間と前後のサイズを CSV 形式でも出力
# ================================================

set -u

RUNS="${RUNS:-1}"
CONTAINER="${CONTAINER:-postgres-primary}"
PGUSER="${PGUSER:-idp}"
PGDATABASE="${PGDATABASE:-idpserver}"
INDEX_NAME="${INDEX_NAME:-idx_events_detail_jsonb}"
INDEX_DEF="${INDEX_DEF:-CREATE INDEX ${INDEX_NAME} ON security_event USING GIN (detail jsonb_path_ops)}"

echo "============================================"
echo " DROP INDEX timing measurement"
echo "============================================"
echo "  runs:            ${RUNS}"
echo "  index:           ${INDEX_NAME}"
echo "  container:       ${CONTAINER}"
echo "============================================"
echo ""

# 計測前の状態
ROW_COUNT=$(docker exec "${CONTAINER}" psql -U "${PGUSER}" -d "${PGDATABASE}" -tAc "SELECT count(*) FROM security_event")
PARTITION_COUNT=$(docker exec "${CONTAINER}" psql -U "${PGUSER}" -d "${PGDATABASE}" -tAc "SELECT count(*) FROM pg_inherits WHERE inhparent='security_event'::regclass")
SHARED_BUFFERS=$(docker exec "${CONTAINER}" psql -U "${PGUSER}" -d "${PGDATABASE}" -tAc "SHOW shared_buffers")

echo "Environment:"
echo "  row_count:       ${ROW_COUNT}"
echo "  partition_count: ${PARTITION_COUNT}"
echo "  shared_buffers:  ${SHARED_BUFFERS}"
echo ""

CSV_HEADER="run,row_count,partition_count,gin_size_bytes,gin_size_pretty,drop_ms,success"
echo "${CSV_HEADER}" > /tmp/drop_measure.csv
echo "${CSV_HEADER}"

for ((run=1; run<=RUNS; run++)); do
    # GIN サイズ取得 (DROP 前)
    GIN_SIZE_BYTES=$(docker exec "${CONTAINER}" psql -U "${PGUSER}" -d "${PGDATABASE}" -tAc "
        SELECT COALESCE(SUM(pg_relation_size(child.oid)), 0)
        FROM pg_inherits inh
        JOIN pg_class child ON child.oid = inh.inhrelid
        JOIN pg_class parent ON parent.oid = inh.inhparent
        WHERE parent.relname = '${INDEX_NAME}';
    ")
    GIN_SIZE_PRETTY=$(docker exec "${CONTAINER}" psql -U "${PGUSER}" -d "${PGDATABASE}" -tAc "
        SELECT pg_size_pretty(${GIN_SIZE_BYTES}::bigint);
    ")

    # GIN 不在チェック
    if [[ "${GIN_SIZE_BYTES}" == "0" ]] || [[ -z "${GIN_SIZE_BYTES}" ]]; then
        echo "[run ${run}] GIN not present, creating first..."
        docker exec "${CONTAINER}" psql -U "${PGUSER}" -d "${PGDATABASE}" -c "${INDEX_DEF};" > /dev/null 2>&1
        # サイズ取り直し
        GIN_SIZE_BYTES=$(docker exec "${CONTAINER}" psql -U "${PGUSER}" -d "${PGDATABASE}" -tAc "
            SELECT COALESCE(SUM(pg_relation_size(child.oid)), 0)
            FROM pg_inherits inh
            JOIN pg_class child ON child.oid = inh.inhrelid
            JOIN pg_class parent ON parent.oid = inh.inhparent
            WHERE parent.relname = '${INDEX_NAME}';
        ")
        GIN_SIZE_PRETTY=$(docker exec "${CONTAINER}" psql -U "${PGUSER}" -d "${PGDATABASE}" -tAc "
            SELECT pg_size_pretty(${GIN_SIZE_BYTES}::bigint);
        ")
    fi

    # DROP 実行 + 時間計測 (psql の \timing で ms 精度)
    OUTPUT=$(docker exec "${CONTAINER}" psql -U "${PGUSER}" -d "${PGDATABASE}" \
        -c "\timing on" \
        -c "SET lock_timeout='5s';" \
        -c "DROP INDEX ${INDEX_NAME};" 2>&1)

    # \timing 出力から ms を抽出
    DROP_MS=$(echo "${OUTPUT}" | grep -oE 'Time: [0-9.]+ ms' | tail -1 | grep -oE '[0-9.]+')
    DROP_MS="${DROP_MS:-N/A}"
    SUCCESS="yes"
    if ! echo "${OUTPUT}" | grep -q "DROP INDEX"; then
        SUCCESS="no"
        DROP_MS="failed"
    fi

    # CSV 出力
    LINE="${run},${ROW_COUNT},${PARTITION_COUNT},${GIN_SIZE_BYTES},${GIN_SIZE_PRETTY},${DROP_MS},${SUCCESS}"
    echo "${LINE}"
    echo "${LINE}" >> /tmp/drop_measure.csv

    # rollback (次の run のため) - 最後の run は rollback しない
    if [[ ${run} -lt ${RUNS} ]]; then
        echo "  (recreating index for next run...)"
        CREATE_OUT=$(docker exec "${CONTAINER}" psql -U "${PGUSER}" -d "${PGDATABASE}" -c "${INDEX_DEF};" 2>&1)
        if ! echo "${CREATE_OUT}" | grep -q "CREATE INDEX"; then
            echo "  ERROR creating index: ${CREATE_OUT}"
            break
        fi
    fi
done

echo ""
echo "============================================"
echo " Results saved to /tmp/drop_measure.csv (inside container)"
echo "============================================"
docker cp "${CONTAINER}:/tmp/drop_measure.csv" "$(dirname "$0")/drop_measure_$(date +%Y%m%d_%H%M%S).csv" 2>/dev/null && \
    echo "Copied to host: $(dirname "$0")/drop_measure_$(date +%Y%m%d_%H%M%S).csv"
