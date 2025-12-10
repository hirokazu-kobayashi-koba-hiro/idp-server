#!/usr/bin/env bash
#
# pg_partman パーティション動作検証スクリプト
#
# 検証内容:
#   1. データ挿入（自動パーティション振り分け）
#   2. メンテナンス実行（新規パーティション作成）
#   3. 保持ポリシー動作（古いパーティション削除）
#   4. クエリ性能（パーティションプルーニング）
#
# 使用方法:
#   ./scripts/pg_partman/verify-pg_partman.sh
#

set -e

# 設定
CONTAINER_NAME="${POSTGRES_CONTAINER:-postgres-primary}"
DB_USER="${POSTGRES_USER:-idpserver}"
DB_NAME="${POSTGRES_DB:-idpserver}"
ROW_COUNT="${ROW_COUNT:-100000}"

# 色付き出力
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_test() { echo -e "${CYAN}[TEST]${NC} $1"; }

# PostgreSQLコマンド実行
psql_exec() {
    docker exec "$CONTAINER_NAME" psql -U "$DB_USER" -d "$DB_NAME" -c "$1"
}

psql_exec_quiet() {
    docker exec "$CONTAINER_NAME" psql -U "$DB_USER" -d "$DB_NAME" -t -c "$1" 2>/dev/null | tr -d ' \n'
}

# コンテナチェック
check_container() {
    if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
        log_error "コンテナ '$CONTAINER_NAME' が見つかりません"
        exit 1
    fi
}

# テーブル存在確認
check_tables() {
    log_info "pg_partman管理テーブルを確認中..."

    local tables=("statistics_daily_users_pm" "statistics_monthly_users_pm" "statistics_yearly_users_pm")

    for table in "${tables[@]}"; do
        local exists=$(psql_exec_quiet "SELECT count(*) FROM pg_tables WHERE tablename = '${table}'")
        if [ "$exists" != "1" ]; then
            log_error "テーブル ${table} が存在しません"
            log_info "先に statistics-users-pg_partman.sh を実行してください"
            exit 1
        fi
    done

    log_success "すべてのテーブルが存在します"
}

# ==============================================================================
# テスト1: データ挿入（自動パーティション振り分け）
# ==============================================================================
test_data_insertion() {
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    log_test "テスト1: データ挿入（自動パーティション振り分け）"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    # statistics_daily_users_pm
    # 現在日付を基準に過去6ヶ月〜未来3ヶ月のデータを挿入
    # pg_partmanのpremake=3なので、現在+3ヶ月先までパーティションが存在
    log_info "statistics_daily_users_pm: ${ROW_COUNT}行挿入中..."
    local start_time=$(date +%s.%N)

    psql_exec "
    INSERT INTO statistics_daily_users_pm (tenant_id, stat_date, user_id, last_used_at, created_at)
    SELECT
        ('00000000-0000-0000-0000-00000000000' || (s % 10))::uuid,
        (CURRENT_DATE - interval '3 months') + ((s % 180) || ' days')::interval,
        gen_random_uuid(),
        NOW(),
        NOW()
    FROM generate_series(1, ${ROW_COUNT}) s;
    " > /dev/null

    local end_time=$(date +%s.%N)
    local duration=$(echo "$end_time - $start_time" | bc)
    log_success "daily_users: ${duration}秒で挿入完了"

    # statistics_monthly_users_pm
    # 現在年を基準に過去2年〜未来2年のデータを挿入
    log_info "statistics_monthly_users_pm: ${ROW_COUNT}行挿入中..."
    start_time=$(date +%s.%N)

    psql_exec "
    INSERT INTO statistics_monthly_users_pm (tenant_id, stat_month, user_id, last_used_at, created_at)
    SELECT
        ('00000000-0000-0000-0000-00000000000' || (s % 10))::uuid,
        date_trunc('month', (date_trunc('year', CURRENT_DATE) - interval '1 year') + ((s % 36) || ' months')::interval)::date,
        gen_random_uuid(),
        NOW(),
        NOW()
    FROM generate_series(1, ${ROW_COUNT}) s;
    " > /dev/null

    end_time=$(date +%s.%N)
    duration=$(echo "$end_time - $start_time" | bc)
    log_success "monthly_users: ${duration}秒で挿入完了"

    # statistics_yearly_users_pm
    # 現在年を基準に過去1年〜未来2年のデータを挿入
    # pg_partmanはpremake=2なので、現在+2年先までパーティションが存在
    log_info "statistics_yearly_users_pm: ${ROW_COUNT}行挿入中..."
    start_time=$(date +%s.%N)

    psql_exec "
    INSERT INTO statistics_yearly_users_pm (tenant_id, stat_year, user_id, last_used_at, created_at)
    SELECT
        ('00000000-0000-0000-0000-00000000000' || (s % 10))::uuid,
        date_trunc('year', (date_trunc('year', CURRENT_DATE) - interval '1 year') + ((s % 4) || ' years')::interval)::date,
        gen_random_uuid(),
        NOW(),
        NOW()
    FROM generate_series(1, ${ROW_COUNT}) s;
    " > /dev/null

    end_time=$(date +%s.%N)
    duration=$(echo "$end_time - $start_time" | bc)
    log_success "yearly_users: ${duration}秒で挿入完了"

    # パーティション分布確認
    echo ""
    echo "--- パーティション分布 (daily_users) ---"
    psql_exec "
    SELECT
        tableoid::regclass as partition,
        count(*) as rows
    FROM statistics_daily_users_pm
    GROUP BY tableoid
    ORDER BY partition
    LIMIT 15;
    "

    echo ""
    echo "--- パーティション分布 (monthly_users) ---"
    psql_exec "
    SELECT
        tableoid::regclass as partition,
        count(*) as rows
    FROM statistics_monthly_users_pm
    GROUP BY tableoid
    ORDER BY partition;
    "

    echo ""
    echo "--- パーティション分布 (yearly_users) ---"
    psql_exec "
    SELECT
        tableoid::regclass as partition,
        count(*) as rows
    FROM statistics_yearly_users_pm
    GROUP BY tableoid
    ORDER BY partition;
    "
}

# ==============================================================================
# テスト2: メンテナンス実行
# ==============================================================================
test_maintenance() {
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    log_test "テスト2: メンテナンス実行"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    log_info "partman.run_maintenance_proc() 実行中..."

    local start_time=$(date +%s.%N)
    psql_exec "CALL partman.run_maintenance_proc();"
    local end_time=$(date +%s.%N)
    local duration=$(echo "$end_time - $start_time" | bc)

    log_success "メンテナンス完了: ${duration}秒"

    # 新規作成されたパーティション確認
    echo ""
    echo "--- 現在のパーティション構成 ---"
    psql_exec "
    SELECT
        parent.relname as parent_table,
        count(child.relname) as partition_count
    FROM pg_inherits
    JOIN pg_class parent ON pg_inherits.inhparent = parent.oid
    JOIN pg_class child ON pg_inherits.inhrelid = child.oid
    WHERE parent.relname IN (
        'statistics_daily_users_pm',
        'statistics_monthly_users_pm',
        'statistics_yearly_users_pm'
    )
    GROUP BY parent.relname
    ORDER BY parent.relname;
    "
}

# ==============================================================================
# テスト3: クエリ性能（パーティションプルーニング）
# ==============================================================================
test_query_performance() {
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    log_test "テスト3: クエリ性能（パーティションプルーニング）"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    # 固定のテナントIDを使用（データ挿入時に使用したもの）
    local tenant_id="00000000-0000-0000-0000-000000000001"

    # テスト3-1: 特定日のDAU（パーティションプルーニング効果）
    log_info "テスト3-1: 特定日のDAU COUNT"
    echo ""
    psql_exec "
    EXPLAIN (ANALYZE, COSTS OFF, TIMING OFF)
    SELECT count(DISTINCT user_id)
    FROM statistics_daily_users_pm
    WHERE tenant_id = '${tenant_id}'
      AND stat_date = '2024-03-15';
    "

    # テスト3-2: 月間範囲クエリ
    log_info "テスト3-2: 月間範囲クエリ"
    echo ""
    psql_exec "
    EXPLAIN (ANALYZE, COSTS OFF, TIMING OFF)
    SELECT stat_date, count(DISTINCT user_id) as dau
    FROM statistics_daily_users_pm
    WHERE tenant_id = '${tenant_id}'
      AND stat_date >= '2024-03-01'
      AND stat_date < '2024-04-01'
    GROUP BY stat_date
    ORDER BY stat_date;
    "

    # テスト3-3: 特定年のYAU
    log_info "テスト3-3: 特定年のYAU COUNT"
    echo ""
    psql_exec "
    EXPLAIN (ANALYZE, COSTS OFF, TIMING OFF)
    SELECT count(DISTINCT user_id)
    FROM statistics_yearly_users_pm
    WHERE tenant_id = '${tenant_id}'
      AND stat_year = '2024-01-01';
    "

    # 実行時間比較
    echo ""
    echo "--- 実行時間比較 ---"

    local test1=$(psql_exec_quiet "
    EXPLAIN (ANALYZE, FORMAT JSON)
    SELECT count(DISTINCT user_id) FROM statistics_daily_users_pm
    WHERE tenant_id = '${tenant_id}' AND stat_date = '2024-03-15'" |
    grep -o '"Execution Time": [0-9.]*' | head -1 | grep -o '[0-9.]*')

    local test2=$(psql_exec_quiet "
    EXPLAIN (ANALYZE, FORMAT JSON)
    SELECT count(DISTINCT user_id) FROM statistics_daily_users_pm
    WHERE tenant_id = '${tenant_id}' AND stat_date >= '2024-01-01' AND stat_date < '2024-04-01'" |
    grep -o '"Execution Time": [0-9.]*' | head -1 | grep -o '[0-9.]*')

    local test3=$(psql_exec_quiet "
    EXPLAIN (ANALYZE, FORMAT JSON)
    SELECT count(DISTINCT user_id) FROM statistics_yearly_users_pm
    WHERE tenant_id = '${tenant_id}' AND stat_year = '2024-01-01'" |
    grep -o '"Execution Time": [0-9.]*' | head -1 | grep -o '[0-9.]*')

    printf "%-40s %15s\n" "クエリ" "実行時間(ms)"
    printf "%-40s %15s\n" "----------------------------------------" "---------------"
    printf "%-40s %15.2f\n" "特定日のDAU COUNT" "${test1:-0}"
    printf "%-40s %15.2f\n" "3ヶ月間のDAU COUNT" "${test2:-0}"
    printf "%-40s %15.2f\n" "特定年のYAU COUNT" "${test3:-0}"
}

# ==============================================================================
# テスト4: 保持ポリシー動作確認
# ==============================================================================
test_retention_policy() {
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    log_test "テスト4: 保持ポリシー動作確認（シミュレーション）"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    log_info "現在の保持ポリシー設定:"
    psql_exec "
    SELECT
        parent_table,
        retention,
        retention_keep_table,
        retention_keep_index
    FROM partman.part_config
    WHERE parent_table LIKE 'public.statistics_%_pm';
    "

    log_info "保持期間外のパーティション確認（削除対象候補）:"
    psql_exec "
    SELECT
        parent.relname as parent_table,
        child.relname as partition,
        CASE
            WHEN child.relname LIKE '%_default' THEN 'DEFAULT'
            ELSE 'DATA'
        END as type
    FROM pg_inherits
    JOIN pg_class parent ON pg_inherits.inhparent = parent.oid
    JOIN pg_class child ON pg_inherits.inhrelid = child.oid
    WHERE parent.relname IN (
        'statistics_daily_users_pm',
        'statistics_monthly_users_pm',
        'statistics_yearly_users_pm'
    )
    ORDER BY parent.relname, child.relname;
    "

    log_warn "注意: 実際の削除はrun_maintenance_proc()実行時に行われます"
    log_info "テスト環境では retention_keep_table = false のため自動削除されます"
}

# ==============================================================================
# テスト5: DEFAULTパーティション動作
# ==============================================================================
test_default_partition() {
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    log_test "テスト5: DEFAULTパーティション動作"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    # 範囲外データの挿入テスト
    log_info "範囲外データ（2030年）の挿入テスト..."

    psql_exec "
    INSERT INTO statistics_daily_users_pm (tenant_id, stat_date, user_id)
    VALUES (
        '00000000-0000-0000-0000-000000000001'::uuid,
        '2030-06-15'::date,
        gen_random_uuid()
    );
    " 2>/dev/null || log_warn "挿入失敗（DEFAULTパーティションなし）"

    # DEFAULTパーティションの確認
    echo ""
    echo "--- DEFAULTパーティション状態 ---"
    psql_exec "
    SELECT
        parent.relname as parent_table,
        child.relname as partition,
        (SELECT count(*) FROM pg_class c2 WHERE c2.relname = child.relname) as exists
    FROM pg_inherits
    JOIN pg_class parent ON pg_inherits.inhparent = parent.oid
    JOIN pg_class child ON pg_inherits.inhrelid = child.oid
    WHERE parent.relname IN (
        'statistics_daily_users_pm',
        'statistics_monthly_users_pm',
        'statistics_yearly_users_pm'
    )
    AND child.relname LIKE '%_default'
    ORDER BY parent.relname;
    "
}

# ==============================================================================
# サマリー表示
# ==============================================================================
show_summary() {
    echo ""
    echo "=============================================="
    echo " 検証サマリー"
    echo "=============================================="
    echo ""

    psql_exec "
    SELECT
        'daily_users_pm' as table_name,
        count(*) as total_rows,
        pg_size_pretty(pg_total_relation_size('statistics_daily_users_pm')) as total_size
    FROM statistics_daily_users_pm
    UNION ALL
    SELECT
        'monthly_users_pm',
        count(*),
        pg_size_pretty(pg_total_relation_size('statistics_monthly_users_pm'))
    FROM statistics_monthly_users_pm
    UNION ALL
    SELECT
        'yearly_users_pm',
        count(*),
        pg_size_pretty(pg_total_relation_size('statistics_yearly_users_pm'))
    FROM statistics_yearly_users_pm;
    "

    echo ""
    log_success "検証完了"
    echo ""
    echo "クリーンアップ: ./scripts/pg_partman/cleanup-pg_partman.sh"
}

# ==============================================================================
# ヘルプ
# ==============================================================================
show_help() {
    echo "pg_partman パーティション動作検証スクリプト"
    echo ""
    echo "使用方法: $0 [オプション]"
    echo ""
    echo "オプション:"
    echo "  -h, --help     このヘルプを表示"
    echo "  -q, --quick    クイックテスト（データ挿入のみ）"
    echo ""
    echo "環境変数:"
    echo "  POSTGRES_CONTAINER  コンテナ名 (デフォルト: postgres-primary)"
    echo "  ROW_COUNT           挿入行数 (デフォルト: 100000)"
    echo ""
    echo "例:"
    echo "  $0                      # フル検証"
    echo "  ROW_COUNT=10000 $0      # 少ないデータで検証"
}

# ==============================================================================
# メイン
# ==============================================================================
main() {
    case "${1:-}" in
        -h|--help)
            show_help
            exit 0
            ;;
        -q|--quick)
            check_container
            check_tables
            test_data_insertion
            show_summary
            exit 0
            ;;
    esac

    echo ""
    echo "=============================================="
    echo " pg_partman 動作検証"
    echo "=============================================="
    echo " コンテナ: ${CONTAINER_NAME}"
    echo " データベース: ${DB_NAME}"
    echo " 挿入行数: ${ROW_COUNT}"
    echo "=============================================="
    echo ""

    check_container
    check_tables

    test_data_insertion
    test_maintenance
    test_query_performance
    test_retention_policy
    test_default_partition

    show_summary
}

main "$@"
