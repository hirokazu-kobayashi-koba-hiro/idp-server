#!/bin/bash
# Run UPSERT benchmark + capture PostgreSQL lock observations.
# Outputs lock waits from server log + pg_stat_statements aggregate per N value.
#
# Usage: ./bench_with_lock_observation.sh

set -e

cd "$(dirname "$0")"

PG_CONTAINER="postgres-primary"
RESULT_DIR="results-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$RESULT_DIR"

THREADS=100
OPS=200

for N in 1 32 128; do
  echo ""
  echo "===================================="
  echo "Benchmark with N=$N buckets"
  echo "===================================="

  # 1. Reset pg_stat_statements
  docker exec "$PG_CONTAINER" psql -U idpserver -d idpserver -At -c \
    "SELECT pg_stat_statements_reset();" > /dev/null

  # 2. Capture log file position BEFORE bench
  LOG_START_LINE=$(docker logs "$PG_CONTAINER" 2>&1 | wc -l)

  # 3. Run benchmark
  python3 bench_statistics_upsert.py \
    --threads $THREADS --ops $OPS --buckets $N \
    > "$RESULT_DIR/bench-N${N}.txt" 2>&1

  echo "  ✓ bench complete → $RESULT_DIR/bench-N${N}.txt"

  # 4. Collect lock waits from PG log (after LOG_START_LINE)
  docker logs "$PG_CONTAINER" 2>&1 | tail -n +"$LOG_START_LINE" \
    | grep -E "still waiting|deadlock|acquired ShareLock|waiting for" \
    > "$RESULT_DIR/locks-N${N}.log" || true
  LOCK_LINES=$(wc -l < "$RESULT_DIR/locks-N${N}.log" | tr -d ' ')
  echo "  ✓ lock log lines: $LOCK_LINES → $RESULT_DIR/locks-N${N}.log"

  # 5. pg_stat_statements aggregate (UPSERT only)
  docker exec "$PG_CONTAINER" psql -U idpserver -d idpserver -P pager=off -c "
    SELECT
      calls,
      total_exec_time::numeric(10,2) AS total_ms,
      mean_exec_time::numeric(10,3) AS mean_ms,
      stddev_exec_time::numeric(10,3) AS stddev_ms,
      min_exec_time::numeric(10,3) AS min_ms,
      max_exec_time::numeric(10,3) AS max_ms,
      rows
    FROM pg_stat_statements
    WHERE query ILIKE '%INSERT INTO statistics_events%'
      AND query ILIKE '%ON CONFLICT%'
    ORDER BY total_exec_time DESC
    LIMIT 5;
  " > "$RESULT_DIR/pgss-N${N}.txt" 2>&1
  echo "  ✓ pg_stat_statements → $RESULT_DIR/pgss-N${N}.txt"
done

echo ""
echo "===================================="
echo "Comparison summary"
echo "===================================="
echo ""
for N in 1 32 128; do
  echo "--- N=$N ---"
  echo "App-side metrics:"
  grep -E "ops/sec:|latency:" "$RESULT_DIR/bench-N${N}.txt" | sed 's/^/  /'
  echo "PG lock log lines: $(wc -l < $RESULT_DIR/locks-N${N}.log | tr -d ' ')"
  echo "PG pg_stat_statements (UPSERT mean/max ms):"
  awk '/^[ ]*[0-9]+ \|/ {print "  calls=" $1 " mean=" $5 " max=" $11}' "$RESULT_DIR/pgss-N${N}.txt" | head -1
  echo ""
done

echo "All results in: $RESULT_DIR"
