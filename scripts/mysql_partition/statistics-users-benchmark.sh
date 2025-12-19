#!/usr/bin/env bash
#
# MySQL パーティションテーブル vs 通常テーブル 性能比較スクリプト
# 対象: statistics_yearly_users, statistics_monthly_users, statistics_daily_users
#
# 使用方法:
#   ./scripts/mysql_partition/statistics-users-benchmark.sh [行数]
#
# 例:
#   ./scripts/mysql_partition/statistics-users-benchmark.sh          # デフォルト: 10万行
#   ./scripts/mysql_partition/statistics-users-benchmark.sh 500000   # 50万行
#   ./scripts/mysql_partition/statistics-users-benchmark.sh 1000000  # 100万行
#

set -e

# ヘルプ表示
show_help() {
    echo "使用方法: $0 [オプション] [行数]"
    echo ""
    echo "オプション:"
    echo "  --help          このヘルプを表示"
    echo "  --cleanup       テスト用テーブルのみ削除"
    echo ""
    echo "引数:"
    echo "  行数            テスト行数 (デフォルト: 100000)"
    echo ""
    echo "例:"
    echo "  $0              # デフォルト: 10万行"
    echo "  $0 500000       # 50万行"
    echo "  $0 1000000      # 100万行"
    echo "  $0 --cleanup    # テスト用テーブル削除のみ"
    echo ""
    echo "環境変数:"
    echo "  MYSQL_CONTAINER  コンテナ名 (デフォルト: idp-mysql)"
    echo "  MYSQL_USER       ユーザー名 (デフォルト: idpserver)"
    echo "  MYSQL_PASSWORD   パスワード (デフォルト: idpserver)"
    echo "  MYSQL_DB         データベース名 (デフォルト: idpserver)"
}

# 引数処理
if [ "${1:-}" = "--help" ] || [ "${1:-}" = "-h" ]; then
    show_help
    exit 0
fi

# 設定
CONTAINER_NAME="${MYSQL_CONTAINER:-idp-mysql}"
DB_USER="${MYSQL_USER:-idpserver}"
DB_PASSWORD="${MYSQL_PASSWORD:-idpserver}"
DB_NAME="${MYSQL_DB:-idpserver}"

# --cleanup オプションの処理
if [ "${1:-}" = "--cleanup" ]; then
    CLEANUP_ONLY=true
    ROW_COUNT=0
else
    CLEANUP_ONLY=false
    ROW_COUNT="${1:-100000}"
fi

# 色付き出力
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m'

log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_test() { echo -e "${CYAN}[TEST]${NC} $1"; }
log_result() { echo -e "${MAGENTA}[RESULT]${NC} $1"; }

# MySQLコマンド実行
mysql_exec() {
    docker exec "$CONTAINER_NAME" mysql -u"$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" -e "$1" 2>/dev/null
}

mysql_exec_quiet() {
    docker exec "$CONTAINER_NAME" mysql -u"$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" -sN -e "$1" 2>/dev/null
}

# ミリ秒取得
get_time_ms() {
    python3 -c 'import time; print(int(time.time() * 1000))' 2>/dev/null || echo $(($(date +%s) * 1000))
}

# コンテナチェック
check_container() {
    log_info "MySQLコンテナを確認中..."
    if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
        log_error "コンテナ '$CONTAINER_NAME' が見つかりません"
        log_info "docker-compose -f docker-compose-mysql.yaml up -d でMySQLを起動してください"
        exit 1
    fi
    log_success "コンテナ '$CONTAINER_NAME' が稼働中"
}

# ==============================================================================
# DAILY USERS テーブル
# ==============================================================================
create_daily_users_tables() {
    log_info "statistics_daily_users テスト用テーブルを作成中..."

    mysql_exec "
    -- 既存テーブルを削除
    DROP TABLE IF EXISTS statistics_daily_users_normal;
    DROP TABLE IF EXISTS statistics_daily_users_partitioned;

    -- 1. 通常テーブル（パーティションなし）
    CREATE TABLE statistics_daily_users_normal (
        tenant_id CHAR(36) NOT NULL,
        stat_date DATE NOT NULL,
        user_id CHAR(36) NOT NULL,
        user_name VARCHAR(255) NOT NULL DEFAULT '',
        last_used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (tenant_id, stat_date, user_id),
        KEY idx_daily_normal_tenant_date (tenant_id, stat_date)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

    -- 2. パーティションテーブル（日別）
    CREATE TABLE statistics_daily_users_partitioned (
        tenant_id CHAR(36) NOT NULL,
        stat_date DATE NOT NULL,
        user_id CHAR(36) NOT NULL,
        user_name VARCHAR(255) NOT NULL DEFAULT '',
        last_used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (tenant_id, stat_date, user_id),
        KEY idx_daily_partitioned_tenant_date (tenant_id, stat_date)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    PARTITION BY RANGE COLUMNS(stat_date) (
        PARTITION p202401 VALUES LESS THAN ('2024-02-01'),
        PARTITION p202402 VALUES LESS THAN ('2024-03-01'),
        PARTITION p202403 VALUES LESS THAN ('2024-04-01'),
        PARTITION p202404 VALUES LESS THAN ('2024-05-01'),
        PARTITION p202405 VALUES LESS THAN ('2024-06-01'),
        PARTITION p202406 VALUES LESS THAN ('2024-07-01'),
        PARTITION p202407 VALUES LESS THAN ('2024-08-01'),
        PARTITION p202408 VALUES LESS THAN ('2024-09-01'),
        PARTITION p202409 VALUES LESS THAN ('2024-10-01'),
        PARTITION p202410 VALUES LESS THAN ('2024-11-01'),
        PARTITION p202411 VALUES LESS THAN ('2024-12-01'),
        PARTITION p202412 VALUES LESS THAN ('2025-01-01'),
        PARTITION p_future VALUES LESS THAN MAXVALUE
    );
    "

    log_success "statistics_daily_users テーブル作成完了"
}

insert_daily_users_data() {
    local table_name=$1

    log_info "${table_name} に ${ROW_COUNT} 行を挿入中..."
    local start_time=$(get_time_ms)

    # バッチ挿入（10000行ずつ）
    local batch_size=10000
    local inserted=0

    while [ $inserted -lt $ROW_COUNT ]; do
        local remaining=$((ROW_COUNT - inserted))
        local current_batch=$((remaining < batch_size ? remaining : batch_size))

        mysql_exec "
        INSERT INTO ${table_name} (tenant_id, stat_date, user_id, user_name, last_used_at, created_at)
        SELECT
            CONCAT('00000000-0000-0000-0000-00000000000', FLOOR(RAND() * 10)),
            DATE_ADD('2024-01-01', INTERVAL FLOOR(RAND() * 365) DAY),
            UUID(),
            CONCAT('user_', FLOOR(RAND() * 10000)),
            DATE_ADD('2024-01-01', INTERVAL FLOOR(RAND() * 365 * 24 * 60) MINUTE),
            NOW()
        FROM (
            SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
            UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10
        ) a,
        (
            SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
            UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10
        ) b,
        (
            SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
            UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10
        ) c
        LIMIT ${current_batch};
        "

        inserted=$((inserted + current_batch))
        printf "\r${BLUE}[INFO]${NC} 挿入中: ${inserted}/${ROW_COUNT} 行  "
    done

    local end_time=$(get_time_ms)
    local duration=$(( (end_time - start_time) / 1000 ))
    echo ""
    log_success "${table_name}: ${duration}秒で挿入完了"
}

# ==============================================================================
# MONTHLY USERS テーブル
# ==============================================================================
create_monthly_users_tables() {
    log_info "statistics_monthly_users テスト用テーブルを作成中..."

    mysql_exec "
    DROP TABLE IF EXISTS statistics_monthly_users_normal;
    DROP TABLE IF EXISTS statistics_monthly_users_partitioned;

    CREATE TABLE statistics_monthly_users_normal (
        tenant_id CHAR(36) NOT NULL,
        stat_month DATE NOT NULL,
        user_id CHAR(36) NOT NULL,
        user_name VARCHAR(255) NOT NULL DEFAULT '',
        last_used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (tenant_id, stat_month, user_id),
        KEY idx_monthly_normal_tenant_month (tenant_id, stat_month)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

    CREATE TABLE statistics_monthly_users_partitioned (
        tenant_id CHAR(36) NOT NULL,
        stat_month DATE NOT NULL,
        user_id CHAR(36) NOT NULL,
        user_name VARCHAR(255) NOT NULL DEFAULT '',
        last_used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (tenant_id, stat_month, user_id),
        KEY idx_monthly_partitioned_tenant_month (tenant_id, stat_month)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    PARTITION BY RANGE COLUMNS(stat_month) (
        PARTITION p2023 VALUES LESS THAN ('2024-01-01'),
        PARTITION p2024 VALUES LESS THAN ('2025-01-01'),
        PARTITION p2025 VALUES LESS THAN ('2026-01-01'),
        PARTITION p_future VALUES LESS THAN MAXVALUE
    );
    "

    log_success "statistics_monthly_users テーブル作成完了"
}

insert_monthly_users_data() {
    local table_name=$1

    log_info "${table_name} に ${ROW_COUNT} 行を挿入中..."
    local start_time=$(get_time_ms)

    local batch_size=10000
    local inserted=0

    while [ $inserted -lt $ROW_COUNT ]; do
        local remaining=$((ROW_COUNT - inserted))
        local current_batch=$((remaining < batch_size ? remaining : batch_size))

        mysql_exec "
        INSERT INTO ${table_name} (tenant_id, stat_month, user_id, user_name, last_used_at, created_at)
        SELECT
            CONCAT('00000000-0000-0000-0000-00000000000', FLOOR(RAND() * 10)),
            DATE_FORMAT(DATE_ADD('2023-01-01', INTERVAL FLOOR(RAND() * 24) MONTH), '%Y-%m-01'),
            UUID(),
            CONCAT('user_', FLOOR(RAND() * 10000)),
            DATE_ADD('2023-01-01', INTERVAL FLOOR(RAND() * 730 * 24 * 60) MINUTE),
            NOW()
        FROM (
            SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
            UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10
        ) a,
        (
            SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
            UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10
        ) b,
        (
            SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
            UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10
        ) c
        LIMIT ${current_batch};
        "

        inserted=$((inserted + current_batch))
        printf "\r${BLUE}[INFO]${NC} 挿入中: ${inserted}/${ROW_COUNT} 行  "
    done

    local end_time=$(get_time_ms)
    local duration=$(( (end_time - start_time) / 1000 ))
    echo ""
    log_success "${table_name}: ${duration}秒で挿入完了"
}

# ==============================================================================
# YEARLY USERS テーブル
# ==============================================================================
create_yearly_users_tables() {
    log_info "statistics_yearly_users テスト用テーブルを作成中..."

    mysql_exec "
    DROP TABLE IF EXISTS statistics_yearly_users_normal;
    DROP TABLE IF EXISTS statistics_yearly_users_partitioned;

    CREATE TABLE statistics_yearly_users_normal (
        tenant_id CHAR(36) NOT NULL,
        stat_year DATE NOT NULL,
        user_id CHAR(36) NOT NULL,
        user_name VARCHAR(255) NOT NULL DEFAULT '',
        last_used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (tenant_id, stat_year, user_id),
        KEY idx_yearly_normal_tenant_year (tenant_id, stat_year),
        KEY idx_yearly_normal_last_used (tenant_id, last_used_at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

    CREATE TABLE statistics_yearly_users_partitioned (
        tenant_id CHAR(36) NOT NULL,
        stat_year DATE NOT NULL,
        user_id CHAR(36) NOT NULL,
        user_name VARCHAR(255) NOT NULL DEFAULT '',
        last_used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (tenant_id, stat_year, user_id),
        KEY idx_yearly_partitioned_tenant_year (tenant_id, stat_year),
        KEY idx_yearly_partitioned_last_used (tenant_id, last_used_at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    PARTITION BY RANGE COLUMNS(stat_year) (
        PARTITION p2022 VALUES LESS THAN ('2023-01-01'),
        PARTITION p2023 VALUES LESS THAN ('2024-01-01'),
        PARTITION p2024 VALUES LESS THAN ('2025-01-01'),
        PARTITION p2025 VALUES LESS THAN ('2026-01-01'),
        PARTITION p_future VALUES LESS THAN MAXVALUE
    );
    "

    log_success "statistics_yearly_users テーブル作成完了"
}

insert_yearly_users_data() {
    local table_name=$1

    log_info "${table_name} に ${ROW_COUNT} 行を挿入中..."
    local start_time=$(get_time_ms)

    local batch_size=10000
    local inserted=0

    while [ $inserted -lt $ROW_COUNT ]; do
        local remaining=$((ROW_COUNT - inserted))
        local current_batch=$((remaining < batch_size ? remaining : batch_size))

        mysql_exec "
        INSERT INTO ${table_name} (tenant_id, stat_year, user_id, user_name, last_used_at, created_at)
        SELECT
            CONCAT('00000000-0000-0000-0000-00000000000', FLOOR(RAND() * 10)),
            DATE_FORMAT(DATE_ADD('2022-01-01', INTERVAL FLOOR(RAND() * 36) MONTH), '%Y-%m-01'),
            UUID(),
            CONCAT('user_', FLOOR(RAND() * 10000)),
            DATE_ADD('2022-01-01', INTERVAL FLOOR(RAND() * 1095 * 24 * 60) MINUTE),
            NOW()
        FROM (
            SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
            UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10
        ) a,
        (
            SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
            UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10
        ) b,
        (
            SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
            UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10
        ) c
        LIMIT ${current_batch};
        "

        inserted=$((inserted + current_batch))
        printf "\r${BLUE}[INFO]${NC} 挿入中: ${inserted}/${ROW_COUNT} 行  "
    done

    local end_time=$(get_time_ms)
    local duration=$(( (end_time - start_time) / 1000 ))
    echo ""
    log_success "${table_name}: ${duration}秒で挿入完了"
}

# ==============================================================================
# 統計情報更新
# ==============================================================================
analyze_tables() {
    log_info "統計情報を更新中..."
    mysql_exec "
    ANALYZE TABLE statistics_daily_users_normal;
    ANALYZE TABLE statistics_daily_users_partitioned;
    ANALYZE TABLE statistics_monthly_users_normal;
    ANALYZE TABLE statistics_monthly_users_partitioned;
    ANALYZE TABLE statistics_yearly_users_normal;
    ANALYZE TABLE statistics_yearly_users_partitioned;
    "
    log_success "統計情報更新完了"
}

# ==============================================================================
# 性能テスト
# ==============================================================================
run_benchmark() {
    local query=$1
    local start_time=$(get_time_ms)
    mysql_exec_quiet "$query" > /dev/null
    local end_time=$(get_time_ms)
    echo $((end_time - start_time))
}

run_daily_users_benchmarks() {
    echo ""
    echo "=============================================="
    echo " statistics_daily_users 性能比較テスト"
    echo "=============================================="
    echo ""

    local tenant_id=$(mysql_exec_quiet "SELECT tenant_id FROM statistics_daily_users_normal LIMIT 1")

    # テスト1: 特定日のDAU COUNT
    log_test "テスト1: 特定日のDAU COUNT (tenant_id指定)"
    local test1_normal=$(run_benchmark \
        "SELECT COUNT(DISTINCT user_id) FROM statistics_daily_users_normal WHERE tenant_id = '${tenant_id}' AND stat_date = '2024-03-15'")
    local test1_partitioned=$(run_benchmark \
        "SELECT COUNT(DISTINCT user_id) FROM statistics_daily_users_partitioned WHERE tenant_id = '${tenant_id}' AND stat_date = '2024-03-15'")

    # テスト2: 月間範囲のDAU推移
    log_test "テスト2: 1ヶ月間のDAU推移"
    local test2_normal=$(run_benchmark \
        "SELECT stat_date, COUNT(DISTINCT user_id) as dau FROM statistics_daily_users_normal WHERE tenant_id = '${tenant_id}' AND stat_date >= '2024-03-01' AND stat_date < '2024-04-01' GROUP BY stat_date ORDER BY stat_date")
    local test2_partitioned=$(run_benchmark \
        "SELECT stat_date, COUNT(DISTINCT user_id) as dau FROM statistics_daily_users_partitioned WHERE tenant_id = '${tenant_id}' AND stat_date >= '2024-03-01' AND stat_date < '2024-04-01' GROUP BY stat_date ORDER BY stat_date")

    # テスト3: 全期間の総ユニークユーザー
    log_test "テスト3: 全期間の総ユニークユーザー"
    local test3_normal=$(run_benchmark \
        "SELECT COUNT(DISTINCT user_id) FROM statistics_daily_users_normal WHERE tenant_id = '${tenant_id}'")
    local test3_partitioned=$(run_benchmark \
        "SELECT COUNT(DISTINCT user_id) FROM statistics_daily_users_partitioned WHERE tenant_id = '${tenant_id}'")

    # テスト4: 特定ユーザーの活動履歴
    log_test "テスト4: 特定ユーザーの活動履歴"
    local user_id=$(mysql_exec_quiet "SELECT user_id FROM statistics_daily_users_normal WHERE tenant_id = '${tenant_id}' LIMIT 1")
    local test4_normal=$(run_benchmark \
        "SELECT stat_date, last_used_at FROM statistics_daily_users_normal WHERE tenant_id = '${tenant_id}' AND user_id = '${user_id}' ORDER BY stat_date DESC LIMIT 30")
    local test4_partitioned=$(run_benchmark \
        "SELECT stat_date, last_used_at FROM statistics_daily_users_partitioned WHERE tenant_id = '${tenant_id}' AND user_id = '${user_id}' ORDER BY stat_date DESC LIMIT 30")

    # 結果表示
    echo ""
    printf "%-35s %15s %15s %15s\n" "テスト" "通常(ms)" "パーティション(ms)" "改善率"
    printf "%-35s %15s %15s %15s\n" "-----------------------------------" "---------------" "---------------" "---------------"

    print_result "特定日のDAU COUNT" "$test1_normal" "$test1_partitioned"
    print_result "1ヶ月間のDAU推移" "$test2_normal" "$test2_partitioned"
    print_result "全期間ユニークユーザー" "$test3_normal" "$test3_partitioned"
    print_result "特定ユーザー活動履歴" "$test4_normal" "$test4_partitioned"
}

run_monthly_users_benchmarks() {
    echo ""
    echo "=============================================="
    echo " statistics_monthly_users 性能比較テスト"
    echo "=============================================="
    echo ""

    local tenant_id=$(mysql_exec_quiet "SELECT tenant_id FROM statistics_monthly_users_normal LIMIT 1")

    # テスト1: 特定月のMAU COUNT
    log_test "テスト1: 特定月のMAU COUNT"
    local test1_normal=$(run_benchmark \
        "SELECT COUNT(DISTINCT user_id) FROM statistics_monthly_users_normal WHERE tenant_id = '${tenant_id}' AND stat_month = '2024-03-01'")
    local test1_partitioned=$(run_benchmark \
        "SELECT COUNT(DISTINCT user_id) FROM statistics_monthly_users_partitioned WHERE tenant_id = '${tenant_id}' AND stat_month = '2024-03-01'")

    # テスト2: 年間MAU推移
    log_test "テスト2: 年間MAU推移"
    local test2_normal=$(run_benchmark \
        "SELECT stat_month, COUNT(DISTINCT user_id) as mau FROM statistics_monthly_users_normal WHERE tenant_id = '${tenant_id}' AND stat_month >= '2024-01-01' AND stat_month < '2025-01-01' GROUP BY stat_month ORDER BY stat_month")
    local test2_partitioned=$(run_benchmark \
        "SELECT stat_month, COUNT(DISTINCT user_id) as mau FROM statistics_monthly_users_partitioned WHERE tenant_id = '${tenant_id}' AND stat_month >= '2024-01-01' AND stat_month < '2025-01-01' GROUP BY stat_month ORDER BY stat_month")

    # テスト3: 複数年のMAU比較
    log_test "テスト3: 複数年のMAU比較"
    local test3_normal=$(run_benchmark \
        "SELECT YEAR(stat_month) as year, COUNT(DISTINCT user_id) as unique_users FROM statistics_monthly_users_normal WHERE tenant_id = '${tenant_id}' GROUP BY YEAR(stat_month) ORDER BY year")
    local test3_partitioned=$(run_benchmark \
        "SELECT YEAR(stat_month) as year, COUNT(DISTINCT user_id) as unique_users FROM statistics_monthly_users_partitioned WHERE tenant_id = '${tenant_id}' GROUP BY YEAR(stat_month) ORDER BY year")

    # テスト4: 特定月範囲でのユーザー検索
    log_test "テスト4: 6ヶ月間のユニークユーザー"
    local test4_normal=$(run_benchmark \
        "SELECT COUNT(DISTINCT user_id) FROM statistics_monthly_users_normal WHERE tenant_id = '${tenant_id}' AND stat_month >= '2024-01-01' AND stat_month <= '2024-06-01'")
    local test4_partitioned=$(run_benchmark \
        "SELECT COUNT(DISTINCT user_id) FROM statistics_monthly_users_partitioned WHERE tenant_id = '${tenant_id}' AND stat_month >= '2024-01-01' AND stat_month <= '2024-06-01'")

    # 結果表示
    echo ""
    printf "%-35s %15s %15s %15s\n" "テスト" "通常(ms)" "パーティション(ms)" "改善率"
    printf "%-35s %15s %15s %15s\n" "-----------------------------------" "---------------" "---------------" "---------------"

    print_result "特定月のMAU COUNT" "$test1_normal" "$test1_partitioned"
    print_result "年間MAU推移" "$test2_normal" "$test2_partitioned"
    print_result "複数年のMAU比較" "$test3_normal" "$test3_partitioned"
    print_result "6ヶ月間ユニークユーザー" "$test4_normal" "$test4_partitioned"
}

run_yearly_users_benchmarks() {
    echo ""
    echo "=============================================="
    echo " statistics_yearly_users 性能比較テスト"
    echo "=============================================="
    echo ""

    local tenant_id=$(mysql_exec_quiet "SELECT tenant_id FROM statistics_yearly_users_normal LIMIT 1")

    # テスト1: 特定年のYAU COUNT
    log_test "テスト1: 特定年のYAU COUNT"
    local test1_normal=$(run_benchmark \
        "SELECT COUNT(DISTINCT user_id) FROM statistics_yearly_users_normal WHERE tenant_id = '${tenant_id}' AND stat_year = '2024-04-01'")
    local test1_partitioned=$(run_benchmark \
        "SELECT COUNT(DISTINCT user_id) FROM statistics_yearly_users_partitioned WHERE tenant_id = '${tenant_id}' AND stat_year = '2024-04-01'")

    # テスト2: 複数年のYAU推移
    log_test "テスト2: 複数年のYAU推移"
    local test2_normal=$(run_benchmark \
        "SELECT YEAR(stat_year) as year, COUNT(DISTINCT user_id) as yau FROM statistics_yearly_users_normal WHERE tenant_id = '${tenant_id}' GROUP BY YEAR(stat_year) ORDER BY year")
    local test2_partitioned=$(run_benchmark \
        "SELECT YEAR(stat_year) as year, COUNT(DISTINCT user_id) as yau FROM statistics_yearly_users_partitioned WHERE tenant_id = '${tenant_id}' GROUP BY YEAR(stat_year) ORDER BY year")

    # テスト3: 直近アクティブユーザー検索
    log_test "テスト3: 直近アクティブユーザー検索 (last_used_at > 30日前)"
    local test3_normal=$(run_benchmark \
        "SELECT COUNT(*) FROM statistics_yearly_users_normal WHERE tenant_id = '${tenant_id}' AND stat_year >= '2024-01-01' AND last_used_at > DATE_SUB(NOW(), INTERVAL 30 DAY)")
    local test3_partitioned=$(run_benchmark \
        "SELECT COUNT(*) FROM statistics_yearly_users_partitioned WHERE tenant_id = '${tenant_id}' AND stat_year >= '2024-01-01' AND last_used_at > DATE_SUB(NOW(), INTERVAL 30 DAY)")

    # テスト4: 非アクティブユーザー検索（休眠ユーザー）
    log_test "テスト4: 非アクティブユーザー検索 (last_used_at < 180日前)"
    local test4_normal=$(run_benchmark \
        "SELECT COUNT(*) FROM statistics_yearly_users_normal WHERE tenant_id = '${tenant_id}' AND stat_year >= '2024-01-01' AND last_used_at < DATE_SUB(NOW(), INTERVAL 180 DAY)")
    local test4_partitioned=$(run_benchmark \
        "SELECT COUNT(*) FROM statistics_yearly_users_partitioned WHERE tenant_id = '${tenant_id}' AND stat_year >= '2024-01-01' AND last_used_at < DATE_SUB(NOW(), INTERVAL 180 DAY)")

    # 結果表示
    echo ""
    printf "%-35s %15s %15s %15s\n" "テスト" "通常(ms)" "パーティション(ms)" "改善率"
    printf "%-35s %15s %15s %15s\n" "-----------------------------------" "---------------" "---------------" "---------------"

    print_result "特定年のYAU COUNT" "$test1_normal" "$test1_partitioned"
    print_result "複数年のYAU推移" "$test2_normal" "$test2_partitioned"
    print_result "直近アクティブユーザー" "$test3_normal" "$test3_partitioned"
    print_result "非アクティブユーザー" "$test4_normal" "$test4_partitioned"
}

print_result() {
    local test_name=$1
    local normal=$2
    local partitioned=$3
    local improvement=""

    if [ -n "$normal" ] && [ -n "$partitioned" ] && [ "$normal" -gt 0 ]; then
        if [ "$partitioned" -lt "$normal" ]; then
            local pct=$(( (normal - partitioned) * 100 / normal ))
            improvement="${pct}% 改善"
        elif [ "$partitioned" -gt "$normal" ]; then
            local pct=$(( (partitioned - normal) * 100 / normal ))
            improvement="${pct}% 低下"
        else
            improvement="同等"
        fi
    fi
    printf "%-35s %15d %15d %15s\n" "$test_name" "${normal:-0}" "${partitioned:-0}" "$improvement"
}

# ==============================================================================
# テーブルサイズ表示
# ==============================================================================
show_table_sizes() {
    echo ""
    echo "=============================================="
    echo " テーブルサイズ"
    echo "=============================================="
    echo ""

    mysql_exec "
    SELECT
        TABLE_NAME,
        ROUND(DATA_LENGTH / 1024 / 1024, 2) AS data_mb,
        ROUND(INDEX_LENGTH / 1024 / 1024, 2) AS index_mb,
        ROUND((DATA_LENGTH + INDEX_LENGTH) / 1024 / 1024, 2) AS total_mb,
        TABLE_ROWS
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME LIKE 'statistics%users%'
    ORDER BY TABLE_NAME;
    "
}

# ==============================================================================
# パーティション情報表示
# ==============================================================================
show_partition_info() {
    echo ""
    echo "=============================================="
    echo " パーティション情報"
    echo "=============================================="
    echo ""

    mysql_exec "
    SELECT
        TABLE_NAME,
        PARTITION_NAME,
        TABLE_ROWS
    FROM information_schema.PARTITIONS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME LIKE 'statistics%partitioned'
      AND TABLE_ROWS > 0
    ORDER BY TABLE_NAME, PARTITION_NAME;
    "
}

# ==============================================================================
# DROP PARTITION vs DELETE 比較
# ==============================================================================
test_drop_vs_delete() {
    echo ""
    echo "=============================================="
    echo " DROP PARTITION vs DELETE 性能比較"
    echo "=============================================="
    echo ""

    log_info "テスト用に追加データを挿入中..."

    # 削除テスト用のデータを挿入
    mysql_exec "
    INSERT INTO statistics_daily_users_normal (tenant_id, stat_date, user_id, user_name, last_used_at, created_at)
    SELECT
        '99999999-9999-9999-9999-999999999999',
        '2024-01-15',
        UUID(),
        'delete_test',
        NOW(),
        NOW()
    FROM (
        SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
        UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10
    ) a,
    (
        SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
        UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10
    ) b,
    (
        SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
        UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10
    ) c
    LIMIT 5000;

    INSERT INTO statistics_daily_users_partitioned (tenant_id, stat_date, user_id, user_name, last_used_at, created_at)
    SELECT
        '99999999-9999-9999-9999-999999999999',
        '2024-01-15',
        UUID(),
        'delete_test',
        NOW(),
        NOW()
    FROM (
        SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
        UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10
    ) a,
    (
        SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
        UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10
    ) b,
    (
        SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
        UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10
    ) c
    LIMIT 5000;
    "

    local rows_to_delete=$(mysql_exec_quiet "SELECT COUNT(*) FROM statistics_daily_users_normal WHERE tenant_id = '99999999-9999-9999-9999-999999999999'")
    log_info "削除対象: ${rows_to_delete} 行"

    # DELETE テスト
    log_test "DELETE で削除中..."
    local delete_start=$(get_time_ms)
    mysql_exec "DELETE FROM statistics_daily_users_normal WHERE tenant_id = '99999999-9999-9999-9999-999999999999';"
    local delete_end=$(get_time_ms)
    local delete_time=$((delete_end - delete_start))

    # DROP PARTITION テスト（パーティション追加→DROP）
    log_test "DROP PARTITION で削除中..."
    # 新しいパーティションを作成して削除テスト
    mysql_exec "ALTER TABLE statistics_daily_users_partitioned REORGANIZE PARTITION p202401 INTO (
        PARTITION p20240101 VALUES LESS THAN ('2024-01-16'),
        PARTITION p20240116 VALUES LESS THAN ('2024-02-01')
    );"

    local drop_start=$(get_time_ms)
    mysql_exec "ALTER TABLE statistics_daily_users_partitioned DROP PARTITION p20240101;"
    local drop_end=$(get_time_ms)
    local drop_time=$((drop_end - drop_start))

    echo ""
    printf "%-25s %15s\n" "操作" "実行時間(ms)"
    printf "%-25s %15s\n" "-------------------------" "---------------"
    printf "%-25s %15d\n" "DELETE" "$delete_time"
    printf "%-25s %15d\n" "DROP PARTITION" "$drop_time"

    if [ "$drop_time" -lt "$delete_time" ]; then
        local speedup=$((delete_time / (drop_time + 1)))
        log_success "DROP PARTITION は DELETE の約 ${speedup}x 高速"
    fi
}

# ==============================================================================
# クリーンアップ
# ==============================================================================
cleanup() {
    log_info "テスト用テーブルを削除中..."
    mysql_exec "
    DROP TABLE IF EXISTS statistics_daily_users_normal;
    DROP TABLE IF EXISTS statistics_daily_users_partitioned;
    DROP TABLE IF EXISTS statistics_monthly_users_normal;
    DROP TABLE IF EXISTS statistics_monthly_users_partitioned;
    DROP TABLE IF EXISTS statistics_yearly_users_normal;
    DROP TABLE IF EXISTS statistics_yearly_users_partitioned;
    "
    log_success "クリーンアップ完了"
}

# ==============================================================================
# メイン処理
# ==============================================================================
main() {
    echo ""
    echo "╔══════════════════════════════════════════════════════════════╗"
    echo "║   MySQL パーティション vs 通常テーブル ベンチマーク           ║"
    echo "╚══════════════════════════════════════════════════════════════╝"
    echo ""

    check_container

    # --cleanup オプションの場合はクリーンアップのみ
    if [ "$CLEANUP_ONLY" = true ]; then
        cleanup
        echo ""
        echo "=============================================="
        log_success "クリーンアップ完了"
        echo "=============================================="
        exit 0
    fi

    log_info "テスト行数: ${ROW_COUNT} 行"
    echo ""

    # テーブル作成
    create_daily_users_tables
    create_monthly_users_tables
    create_yearly_users_tables

    # データ挿入
    echo ""
    echo "=============================================="
    echo " データ挿入"
    echo "=============================================="
    insert_daily_users_data "statistics_daily_users_normal"
    insert_daily_users_data "statistics_daily_users_partitioned"
    insert_monthly_users_data "statistics_monthly_users_normal"
    insert_monthly_users_data "statistics_monthly_users_partitioned"
    insert_yearly_users_data "statistics_yearly_users_normal"
    insert_yearly_users_data "statistics_yearly_users_partitioned"

    # 統計情報更新
    analyze_tables

    # サイズ表示
    show_table_sizes
    show_partition_info

    # ベンチマーク実行
    run_daily_users_benchmarks
    run_monthly_users_benchmarks
    run_yearly_users_benchmarks

    # DROP vs DELETE テスト
    test_drop_vs_delete

    # クリーンアップ確認
    echo ""
    read -p "テスト用テーブルを削除しますか? (Y/n): " confirm
    if [ "$confirm" != "n" ] && [ "$confirm" != "N" ]; then
        cleanup
    fi

    echo ""
    echo "=============================================="
    log_success "ベンチマーク完了"
    echo "=============================================="
}

main "$@"
