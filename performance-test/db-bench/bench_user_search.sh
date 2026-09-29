#!/bin/bash
# 管理API のユーザー検索 (preferred_username) を計測する。
#
# UserFindListService は COUNT を必ず先に実行してから一覧を引くため、両方を別々に測る。
# 現行実装は前後ワイルドカードの ILIKE '%x%' で、b-tree が原理的に使えない。
#
#   ./bench_user_search.sh                    # 100万行・本番相当の行幅で計測
#   ./bench_user_search.sh --profile thin     # JSONB を埋めない薄い行
#   ./bench_user_search.sh --rows 3000000
#   ./bench_user_search.sh --no-seed          # 投入済みデータで再計測
#   ./bench_user_search.sh --cleanup          # 計測用テナントを削除して終了
#
# 計測条件について:
#   * 並列 ON / OFF の両方を測る。商用は他の負荷でワーカーを取れないことがあり、
#     直列に落ちると倍以上遅くなるため。
#   * shared hit / read を出す。全部キャッシュに乗っているかどうかで数値の意味が変わる。
#   * 管理APIの接続ユーザー idp_admin_user は rolbypassrls = t なので、
#     RLS は管理API検索の性能に影響しない（superuser で測っても条件は同じ）。
#
# 対象は PostgreSQL のみ。MySQL は LOWER() 関数適用のため素の索引が効かない。

set -euo pipefail
cd "$(dirname "$0")"

PG_CONTAINER="${PG_CONTAINER:-postgres-primary}"
PG_USER="${PG_USER:-idpserver}"
PG_DB="${PG_DB:-idpserver}"

BENCH_TENANT="bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
PROVIDER="idp-server"
ROWS=1000000
REPEAT=3
PROFILE="realistic"
DO_SEED=1

while [ $# -gt 0 ]; do
  case "$1" in
    --rows) ROWS="$2"; shift 2 ;;
    --repeat) REPEAT="$2"; shift 2 ;;
    --profile) PROFILE="$2"; shift 2 ;;
    --no-seed) DO_SEED=0; shift ;;
    --cleanup) DO_SEED=-1; shift ;;
    -h|--help) sed -n '2,25p' "$0"; exit 0 ;;
    *) echo "unknown option: $1" >&2; exit 1 ;;
  esac
done

psql_run() { docker exec -i "$PG_CONTAINER" psql -U "$PG_USER" -d "$PG_DB" -v ON_ERROR_STOP=1 "$@"; }

if [ "$DO_SEED" = "-1" ]; then
  echo "計測用テナントを削除中..."
  psql_run -q -c "DROP INDEX IF EXISTS idx_bench_user_lower_preferred_username;"
  psql_run -q -c "DELETE FROM idp_user WHERE tenant_id = '$BENCH_TENANT'::uuid;"
  psql_run -q -c "VACUUM ANALYZE idp_user;"
  echo "完了。"
  exit 0
fi

RESULT_DIR="results-user-search-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$RESULT_DIR"

# ---------------------------------------------------------------- seed
if [ "$DO_SEED" = "1" ]; then
  echo "計測用データを投入中 (${ROWS} 行 / profile=${PROFILE})..."
  psql_run -q -c "DELETE FROM idp_user WHERE tenant_id = '$BENCH_TENANT'::uuid;"

  if [ "$PROFILE" = "thin" ]; then
    PHONE_EXPR="NULL"; CUSTOM_EXPR="NULL"; CLAIMS_EXPR="NULL"
  else
    # 本番で埋まっている列: email / phone_number / custom_properties / verified_claims。
    # verified_claims は OIDC4IDA の形を模した構造にする。JSONB が大きいと TOAST に
    # 出されて heap から消えるため、インライン行幅が現実的な範囲に収まるようにしている。
    PHONE_EXPR="'+8190' || lpad((g % 100000000)::text, 8, '0')"
    CUSTOM_EXPR="jsonb_build_object(
        'member_number', 'M-' || lpad(g::text, 10, '0'),
        'member_rank', (ARRAY['bronze','silver','gold','platinum'])[1 + g % 4],
        'branch_code', lpad((g % 900)::text, 4, '0'),
        'segment', (ARRAY['retail','corporate','staff'])[1 + g % 3],
        'last_kyc_at', to_char(now() - (g % 900 || ' days')::interval, 'YYYY-MM-DD'))"
    CLAIMS_EXPR="jsonb_build_object(
        'verification', jsonb_build_object(
          'trust_framework', 'jp_aml',
          'time', to_char(now() - (g % 900 || ' days')::interval, 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"'),
          'verification_process', md5(g::text),
          'evidence', jsonb_build_array(jsonb_build_object(
            'type', 'document',
            'method', 'pipp',
            'time', to_char(now() - (g % 900 || ' days')::interval, 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"'),
            'document_details', jsonb_build_object(
              'type', 'idcard',
              'issuer', jsonb_build_object('name', 'Tokyo', 'country', 'JP'),
              'document_number', lpad((g % 1000000000)::text, 12, '0'),
              'date_of_issuance', '2020-01-01',
              'date_of_expiry', '2030-01-01')))),
        'claims', jsonb_build_object(
          'given_name', 'Given' || (g % 5000),
          'family_name', 'Family' || (g % 3000),
          'birthdate', '19' || lpad((50 + g % 50)::text, 2, '0') || '-01-01',
          'address', jsonb_build_object(
            'country', 'JP',
            'region', 'Tokyo',
            'locality', 'Chiyoda',
            'street_address', lpad((g % 9999)::text, 4, '0') || ' Test Street',
            'postal_code', lpad((g % 9999999)::text, 7, '0'))))"
  fi

  psql_run -q <<SQL
INSERT INTO idp_user (
  id, tenant_id, provider_id, preferred_username, name, given_name, family_name,
  nickname, email, phone_number, custom_properties, verified_claims,
  status, created_at, updated_at
)
SELECT
  gen_random_uuid(),
  '$BENCH_TENANT'::uuid,
  '$PROVIDER',
  'user' || lpad(g::text, 8, '0') || '@example.com',
  'Name ' || g,
  'Given' || (g % 5000),
  'Family' || (g % 3000),
  'nick' || g,
  'user' || lpad(g::text, 8, '0') || '@example.com',
  $PHONE_EXPR,
  $CUSTOM_EXPR,
  $CLAIMS_EXPR,
  'REGISTERED',
  now() - (g || ' seconds')::interval,
  now()
FROM generate_series(1, $ROWS) g;
SQL
  # DELETE では heap が縮まないため、前回投入分の空きページを実測が拾ってしまう。
  # VACUUM FULL でテーブルを書き直してから測る。
  echo "VACUUM FULL ANALYZE 実行中（前回分の heap 肥大を除去）..."
  psql_run -q -c "VACUUM FULL idp_user;"
  psql_run -q -c "ANALYZE idp_user;"
fi

TOTAL=$(psql_run -At -c "SELECT count(*) FROM idp_user WHERE tenant_id='$BENCH_TENANT'::uuid;")
AVG_ROW=$(psql_run -At -c "SELECT round(avg(pg_column_size(t.*))) FROM idp_user t WHERE tenant_id='$BENCH_TENANT'::uuid;")
HEAP=$(psql_run -At -c "SELECT pg_size_pretty(pg_relation_size('idp_user'));")
TOASTED=$(psql_run -At -c "SELECT pg_size_pretty(COALESCE(pg_total_relation_size(reltoastrelid),0)) FROM pg_class WHERE relname='idp_user';")
SHARED_BUF=$(psql_run -At -c "SHOW shared_buffers;")
MAXPAR=$(psql_run -At -c "SHOW max_parallel_workers_per_gather;")

echo ""
echo "行数=$TOTAL  平均行幅=${AVG_ROW}B  heap=$HEAP  TOAST=$TOASTED  shared_buffers=$SHARED_BUF"
echo ""

# ---------------------------------------------------------------- runner
declare -a LABELS=() TIMES=() SCANS=() BUFS=()

measure() {
  local label="$1" sql="$2" parallel="$3"
  local prefix="" best="" plan="" t scan buf
  [ "$parallel" = "off" ] && prefix="SET max_parallel_workers_per_gather = 0; "
  for _ in $(seq 1 "$REPEAT"); do
    plan=$(psql_run -At -c "${prefix}EXPLAIN (ANALYZE, BUFFERS, TIMING ON) $sql" 2>&1)
    t=$(echo "$plan" | grep -o 'Execution Time: [0-9.]*' | tail -1 | grep -o '[0-9.]*' || echo "")
    [ -z "$t" ] && continue
    if [ -z "$best" ] || awk "BEGIN{exit !($t < $best)}"; then best="$t"; fi
  done
  echo "$plan" > "$RESULT_DIR/${label}.txt"
  scan=$(echo "$plan" | grep -oE 'Parallel Seq Scan|Seq Scan|Index Only Scan|Index Scan|Bitmap Heap Scan' | head -1 || true)
  buf=$(echo "$plan" | grep -oE 'shared hit=[0-9]+( read=[0-9]+)?' | head -1 || true)
  LABELS+=("$label"); TIMES+=("${best:-NA}"); SCANS+=("${scan:--}"); BUFS+=("${buf:--}")
  printf '  %-40s %9s ms  %-18s %s\n' "$label" "${best:-NA}" "${scan:--}" "${buf:--}"
}

count_sql() { echo "SELECT COUNT(*) FROM idp_user WHERE idp_user.tenant_id = '$BENCH_TENANT'::uuid AND $1"; }
list_sql()  { echo "SELECT id, created_at FROM idp_user WHERE idp_user.tenant_id = '$BENCH_TENANT'::uuid AND $1 ORDER BY idp_user.created_at DESC, idp_user.id DESC LIMIT 20 OFFSET 0"; }

# 管理者が入力する値。3案で同じ入力を使って比較する。
NEEDLE_ONE="user00000042@example.com"   # 1 件だけ一致
NEEDLE_MANY="user000001"                # 前方一致で多数
NEEDLE_ALL="@example.com"               # 全件一致
NEEDLE_ZERO="zzzzzzzzzzzz"              # 一致なし

echo "== 現行実装 ILIKE '%x%' / 並列ON =="
for c in "one:$NEEDLE_ONE" "many:$NEEDLE_MANY" "all:$NEEDLE_ALL" "zero:$NEEDLE_ZERO"; do
  n="${c%%:*}"; v="${c#*:}"
  measure "current_count_${n}_par" "$(count_sql "idp_user.preferred_username ILIKE '%${v}%'")" on
  measure "current_list_${n}_par"  "$(list_sql  "idp_user.preferred_username ILIKE '%${v}%'")" on
done

echo ""
echo "== 現行実装 ILIKE '%x%' / 並列OFF（混雑時の想定）=="
for c in "one:$NEEDLE_ONE" "all:$NEEDLE_ALL" "zero:$NEEDLE_ZERO"; do
  n="${c%%:*}"; v="${c#*:}"
  measure "current_count_${n}_ser" "$(count_sql "idp_user.preferred_username ILIKE '%${v}%'")" off
  measure "current_list_${n}_ser"  "$(list_sql  "idp_user.preferred_username ILIKE '%${v}%'")" off
done

echo ""
echo "== 案A: 完全一致（既存 uk_preferred_username / 索引追加なし）=="
measure "A_count_one" "$(count_sql "idp_user.provider_id = '$PROVIDER' AND idp_user.preferred_username = '${NEEDLE_ONE}'")" on
measure "A_list_one"  "$(list_sql  "idp_user.provider_id = '$PROVIDER' AND idp_user.preferred_username = '${NEEDLE_ONE}'")" on
measure "A_count_zero" "$(count_sql "idp_user.provider_id = '$PROVIDER' AND idp_user.preferred_username = '${NEEDLE_ZERO}'")" on

echo ""
echo "== 案B: 前方一致（関数索引なし）=="
psql_run -q -c "DROP INDEX IF EXISTS idx_bench_user_lower_preferred_username;"
measure "B_noidx_count_one"  "$(count_sql "lower(idp_user.preferred_username) LIKE lower('${NEEDLE_ONE}') || '%'")" on
measure "B_noidx_count_many" "$(count_sql "lower(idp_user.preferred_username) LIKE lower('${NEEDLE_MANY}') || '%'")" on

echo ""
echo "== 案B: 前方一致（関数索引あり）=="
psql_run -q -c "CREATE INDEX idx_bench_user_lower_preferred_username ON idp_user (tenant_id, lower(preferred_username) text_pattern_ops);"
psql_run -q -c "ANALYZE idp_user;"
IDX_SIZE=$(psql_run -At -c "SELECT pg_size_pretty(pg_relation_size('idx_bench_user_lower_preferred_username'));")
echo "  索引サイズ: $IDX_SIZE"
measure "B_idx_count_one"  "$(count_sql "lower(idp_user.preferred_username) LIKE lower('${NEEDLE_ONE}') || '%'")" on
measure "B_idx_list_one"   "$(list_sql  "lower(idp_user.preferred_username) LIKE lower('${NEEDLE_ONE}') || '%'")" on
measure "B_idx_count_many" "$(count_sql "lower(idp_user.preferred_username) LIKE lower('${NEEDLE_MANY}') || '%'")" on
measure "B_idx_count_zero" "$(count_sql "lower(idp_user.preferred_username) LIKE lower('${NEEDLE_ZERO}') || '%'")" on

# ---------------------------------------------------------------- report
SUMMARY="$RESULT_DIR/SUMMARY.md"
{
  echo "# ユーザー検索 (preferred_username) 計測結果"
  echo ""
  echo "| 項目 | 値 |"
  echo "|---|---|"
  echo "| 計測日時 | $(date '+%Y-%m-%d %H:%M:%S') |"
  echo "| profile | $PROFILE |"
  echo "| 行数 | $TOTAL |"
  echo "| 平均行幅 | ${AVG_ROW} bytes |"
  echo "| heap / TOAST | $HEAP / $TOASTED |"
  echo "| shared_buffers | $SHARED_BUF |"
  echo "| max_parallel_workers_per_gather | $MAXPAR |"
  echo "| 案B の索引サイズ | $IDX_SIZE |"
  echo "| 試行回数 | ${REPEAT}（最速値を採用） |"
  echo ""
  echo "検索語: one=\`$NEEDLE_ONE\` many=\`$NEEDLE_MANY\` all=\`$NEEDLE_ALL\` zero=\`$NEEDLE_ZERO\`"
  echo ""
  echo "| クエリ | Execution Time (ms) | スキャン方式 | Buffers |"
  echo "|---|---:|---|---|"
  for i in "${!LABELS[@]}"; do
    echo "| \`${LABELS[$i]}\` | ${TIMES[$i]} | ${SCANS[$i]} | ${BUFS[$i]} |"
  done
  echo ""
  echo "EXPLAIN の全文は同ディレクトリの各 .txt を参照。"
} > "$SUMMARY"

echo ""
cat "$SUMMARY"
echo ""
echo "結果: $RESULT_DIR/"
