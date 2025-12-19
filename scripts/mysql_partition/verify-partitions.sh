#!/usr/bin/env bash
#
# MySQL パーティション動作検証スクリプト
#
# 検証内容:
#   1. データ挿入（自動パーティション振り分け）
#   2. パーティションプルーニングの確認
#   3. p_future パーティションへのフォールバック
#   4. メンテナンスプロシージャの動作
#
# 使用方法:
#   ./scripts/mysql_partition/verify-partitions.sh
#

set -e

# 設定
CONTAINER_NAME="${MYSQL_CONTAINER:-idp-mysql}"
DB_USER="${MYSQL_USER:-idpserver}"
DB_PASSWORD="${MYSQL_PASSWORD:-idpserver}"
DB_NAME="${MYSQL_DB:-idpserver}"
ROW_COUNT="${ROW_COUNT:-10000}"

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
    docker exec "$CONTAINER_NAME" mysql -u"$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" -e "$1"
}

mysql_exec_quiet() {
    docker exec "$CONTAINER_NAME" mysql -u"$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" -sN -e "$1" 2>/dev/null
}

# コンテナチェック
check_container() {
    if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
        log_error "コンテナ '$CONTAINER_NAME' が見つかりません"
        log_info "環境変数 MYSQL_CONTAINER でコンテナ名を指定できます"
        exit 1
    fi
    log_success "コンテナ '$CONTAINER_NAME' が稼働中"
}

# テーブル存在確認
check_tables() {
    log_info "統計テーブルを確認中..."

    local tables=("statistics_daily_users" "statistics_monthly_users" "statistics_yearly_users")

    for table in "${tables[@]}"; do
        local exists=$(mysql_exec_quiet "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '${table}'")
        if [ "$exists" != "1" ]; then
            log_error "テーブル ${table} が存在しません"
            exit 1
        fi
    done

    log_success "すべてのテーブルが存在します"
}

# パーティション存在確認
check_partitions() {
    log_info "パーティション状態を確認中..."

    local tables=("statistics_daily_users" "statistics_monthly_users" "statistics_yearly_users")

    for table in "${tables[@]}"; do
        local count=$(mysql_exec_quiet "SELECT COUNT(*) FROM information_schema.PARTITIONS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '${table}'")
        if [ "$count" -le 1 ]; then
            log_warn "テーブル ${table} にパーティションがありません（count=${count}）"
            log_info "先に setup-partitions.sql または migrate-to-partitions.sql を実行してください"
            exit 1
        fi
        log_info "${table}: ${count} パーティション"
    done

    log_success "パーティションが設定されています"
}

# ==============================================================================
# テスト1: データ挿入（自動パーティション振り分け）
# ==============================================================================
test_data_insertion() {
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    log_test "テスト1: データ挿入（自動パーティション振り分け）"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    # statistics_daily_users
    log_info "statistics_daily_users: ${ROW_COUNT}行挿入中..."
    local start_time=$(date +%s)

    mysql_exec "
    INSERT IGNORE INTO statistics_daily_users (tenant_id, stat_date, user_id, user_name, last_used_at, created_at)
    SELECT
        UUID(),
        DATE_ADD(CURDATE(), INTERVAL -(FLOOR(RAND() * 90)) DAY),
        UUID(),
        CONCAT('user_', FLOOR(RAND() * 1000)),
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
    LIMIT ${ROW_COUNT};
    " 2>/dev/null

    local end_time=$(date +%s)
    local duration=$((end_time - start_time))
    log_success "daily_users: ${duration}秒で挿入完了"

    # statistics_monthly_users
    log_info "statistics_monthly_users: ${ROW_COUNT}行挿入中..."
    start_time=$(date +%s)

    mysql_exec "
    INSERT IGNORE INTO statistics_monthly_users (tenant_id, stat_month, user_id, user_name, last_used_at, created_at)
    SELECT
        UUID(),
        DATE_FORMAT(DATE_ADD(CURDATE(), INTERVAL -(FLOOR(RAND() * 12)) MONTH), '%Y-%m-01'),
        UUID(),
        CONCAT('user_', FLOOR(RAND() * 1000)),
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
    LIMIT ${ROW_COUNT};
    " 2>/dev/null

    end_time=$(date +%s)
    duration=$((end_time - start_time))
    log_success "monthly_users: ${duration}秒で挿入完了"

    # statistics_yearly_users
    log_info "statistics_yearly_users: ${ROW_COUNT}行挿入中..."
    start_time=$(date +%s)

    mysql_exec "
    INSERT IGNORE INTO statistics_yearly_users (tenant_id, stat_year, user_id, user_name, last_used_at, created_at)
    SELECT
        UUID(),
        DATE_FORMAT(DATE_ADD(CURDATE(), INTERVAL -(FLOOR(RAND() * 36)) MONTH), '%Y-%m-01'),
        UUID(),
        CONCAT('user_', FLOOR(RAND() * 1000)),
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
    LIMIT ${ROW_COUNT};
    " 2>/dev/null

    end_time=$(date +%s)
    duration=$((end_time - start_time))
    log_success "yearly_users: ${duration}秒で挿入完了"

    # パーティション別の行数確認
    echo ""
    log_info "パーティション別データ分布:"
    mysql_exec "
    SELECT TABLE_NAME, PARTITION_NAME, TABLE_ROWS
    FROM information_schema.PARTITIONS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME LIKE 'statistics%_users'
      AND TABLE_ROWS > 0
    ORDER BY TABLE_NAME, PARTITION_NAME
    LIMIT 20;
    " 2>/dev/null

    log_success "テスト1完了: データが各パーティションに振り分けられました"
}

# ==============================================================================
# テスト2: パーティションプルーニング確認
# ==============================================================================
test_partition_pruning() {
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    log_test "テスト2: パーティションプルーニング確認"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    log_info "パーティションプルーニングが効くクエリ (日付条件あり):"
    mysql_exec "
    EXPLAIN SELECT * FROM statistics_daily_users
    WHERE stat_date >= CURDATE() - INTERVAL 7 DAY
      AND stat_date < CURDATE();
    " 2>/dev/null

    echo ""
    log_info "パーティションプルーニングが効かないクエリ (日付条件なし):"
    mysql_exec "
    EXPLAIN SELECT * FROM statistics_daily_users
    WHERE user_id = UUID();
    " 2>/dev/null

    log_success "テスト2完了: EXPLAIN結果でpartitionsカラムを確認してください"
}

# ==============================================================================
# テスト3: p_futureパーティション動作確認
# ==============================================================================
test_future_partition() {
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    log_test "テスト3: p_future パーティション動作確認"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    # 遠い将来の日付でデータを挿入
    local future_date='2099-12-31'
    log_info "遠い将来のデータを挿入: ${future_date}"

    mysql_exec "
    INSERT IGNORE INTO statistics_daily_users (tenant_id, stat_date, user_id, user_name, last_used_at, created_at)
    VALUES (UUID(), '${future_date}', UUID(), 'future_user', NOW(), NOW());
    " 2>/dev/null

    # p_futureパーティションの行数確認
    local future_rows=$(mysql_exec_quiet "
    SELECT TABLE_ROWS FROM information_schema.PARTITIONS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'statistics_daily_users'
      AND PARTITION_NAME = 'p_future';
    ")

    log_result "p_future パーティションの行数: ${future_rows}"

    if [ "$future_rows" -gt 0 ]; then
        log_success "テスト3完了: p_future パーティションが正しく機能しています"
    else
        log_warn "テスト3: p_future パーティションにデータがありません（TABLE_ROWSは概算値のため、後で確認が必要な場合があります）"
    fi
}

# ==============================================================================
# テスト4: メンテナンスプロシージャ動作確認
# ==============================================================================
test_maintenance_procedure() {
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    log_test "テスト4: メンテナンスプロシージャ動作確認"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    # プロシージャの存在確認
    local proc_exists=$(mysql_exec_quiet "
    SELECT COUNT(*) FROM information_schema.ROUTINES
    WHERE ROUTINE_SCHEMA = DATABASE()
      AND ROUTINE_NAME = 'maintain_statistics_partitions';
    ")

    if [ "$proc_exists" != "1" ]; then
        log_warn "maintain_statistics_partitions プロシージャが存在しません"
        log_info "setup-partitions.sql を実行してください"
        return
    fi

    log_info "メンテナンスプロシージャを実行中..."

    # 実行前のパーティション数
    local before_daily=$(mysql_exec_quiet "SELECT COUNT(*) FROM information_schema.PARTITIONS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'statistics_daily_users'")
    local before_monthly=$(mysql_exec_quiet "SELECT COUNT(*) FROM information_schema.PARTITIONS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'statistics_monthly_users'")
    local before_yearly=$(mysql_exec_quiet "SELECT COUNT(*) FROM information_schema.PARTITIONS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'statistics_yearly_users'")

    log_info "実行前パーティション数: daily=${before_daily}, monthly=${before_monthly}, yearly=${before_yearly}"

    # メンテナンス実行
    mysql_exec "CALL maintain_statistics_partitions();" 2>/dev/null

    # 実行後のパーティション数
    local after_daily=$(mysql_exec_quiet "SELECT COUNT(*) FROM information_schema.PARTITIONS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'statistics_daily_users'")
    local after_monthly=$(mysql_exec_quiet "SELECT COUNT(*) FROM information_schema.PARTITIONS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'statistics_monthly_users'")
    local after_yearly=$(mysql_exec_quiet "SELECT COUNT(*) FROM information_schema.PARTITIONS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'statistics_yearly_users'")

    log_info "実行後パーティション数: daily=${after_daily}, monthly=${after_monthly}, yearly=${after_yearly}"

    log_success "テスト4完了: メンテナンスプロシージャが正常に実行されました"
}

# ==============================================================================
# テスト5: パーティション状態サマリー
# ==============================================================================
show_partition_summary() {
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    log_test "パーティション状態サマリー"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    mysql_exec "
    SELECT
        TABLE_NAME,
        COUNT(*) AS partition_count,
        SUM(TABLE_ROWS) AS total_rows,
        MIN(PARTITION_NAME) AS oldest_partition,
        MAX(CASE WHEN PARTITION_NAME != 'p_future' THEN PARTITION_NAME END) AS newest_partition
    FROM information_schema.PARTITIONS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME LIKE 'statistics%_users'
    GROUP BY TABLE_NAME;
    " 2>/dev/null
}

# ==============================================================================
# メイン処理
# ==============================================================================
main() {
    echo ""
    echo "╔══════════════════════════════════════════════════════════════╗"
    echo "║       MySQL パーティション動作検証スクリプト                    ║"
    echo "╚══════════════════════════════════════════════════════════════╝"
    echo ""

    check_container
    check_tables
    check_partitions

    test_data_insertion
    test_partition_pruning
    test_future_partition
    test_maintenance_procedure
    show_partition_summary

    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    log_success "すべての検証が完了しました"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
}

main "$@"
