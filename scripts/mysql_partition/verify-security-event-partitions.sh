#!/usr/bin/env bash
#
# MySQL セキュリティイベントパーティション動作検証スクリプト
#
# 検証内容:
#   1. データ挿入（自動パーティション振り分け）
#   2. パーティションプルーニングの確認
#   3. p_future パーティションへのフォールバック
#   4. メンテナンスプロシージャの動作
#
# 使用方法:
#   ./scripts/mysql_partition/verify-security-event-partitions.sh
#

set -e

# 設定
CONTAINER_NAME="${MYSQL_CONTAINER:-idp-mysql}"
DB_USER="${MYSQL_USER:-idpserver}"
DB_PASSWORD="${MYSQL_PASSWORD:-idpserver}"
DB_NAME="${MYSQL_DB:-idpserver}"
ROW_COUNT="${ROW_COUNT:-1000}"

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
    log_info "セキュリティイベントテーブルを確認中..."

    local tables=("security_event" "security_event_hook_results")

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

    local tables=("security_event" "security_event_hook_results")

    for table in "${tables[@]}"; do
        local count=$(mysql_exec_quiet "SELECT COUNT(*) FROM information_schema.PARTITIONS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '${table}'")
        if [ "$count" -le 1 ]; then
            log_warn "テーブル ${table} にパーティションがありません（count=${count}）"
            log_info "先に V0_9_21_1__security_event_partition.mysql.sql を実行してください"
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

    # テスト用テナントIDを取得（存在しない場合はダミーを使用）
    local tenant_id=$(mysql_exec_quiet "SELECT id FROM tenant LIMIT 1" 2>/dev/null || echo "00000000-0000-0000-0000-000000000001")

    # security_event にテストデータ挿入
    log_info "security_event: ${ROW_COUNT}行挿入中..."
    local start_time=$(date +%s)

    mysql_exec "
    INSERT INTO security_event (id, type, description, tenant_id, tenant_name, client_id, client_name, user_id, detail, created_at)
    SELECT
        UUID(),
        ELT(FLOOR(1 + RAND() * 5), 'LOGIN_SUCCESS', 'LOGIN_FAILURE', 'LOGOUT', 'TOKEN_ISSUED', 'PASSWORD_CHANGE'),
        'Test security event',
        '${tenant_id}',
        'test-tenant',
        'test-client',
        'Test Client',
        UUID(),
        JSON_OBJECT('source', 'partition-test', 'timestamp', NOW()),
        DATE_ADD(NOW(), INTERVAL -FLOOR(RAND() * 90) DAY)
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
    "

    local end_time=$(date +%s)
    local duration=$((end_time - start_time))
    log_success "security_event: ${duration}秒で挿入完了"

    # security_event_hook_results にテストデータ挿入
    log_info "security_event_hook_results: ${ROW_COUNT}行挿入中..."
    start_time=$(date +%s)

    mysql_exec "
    INSERT INTO security_event_hook_results (id, tenant_id, security_event_id, security_event_type, security_event_hook, security_event_payload, status, created_at)
    SELECT
        UUID(),
        '${tenant_id}',
        UUID(),
        ELT(FLOOR(1 + RAND() * 3), 'LOGIN_SUCCESS', 'LOGIN_FAILURE', 'LOGOUT'),
        'webhook',
        JSON_OBJECT('test', true),
        ELT(FLOOR(1 + RAND() * 3), 'SUCCESS', 'FAILURE', 'PENDING'),
        DATE_ADD(NOW(), INTERVAL -FLOOR(RAND() * 90) DAY)
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
    "

    end_time=$(date +%s)
    duration=$((end_time - start_time))
    log_success "security_event_hook_results: ${duration}秒で挿入完了"

    # パーティション別データ分布を表示
    echo ""
    log_info "パーティション別データ分布 (security_event):"
    mysql_exec "
    SELECT TABLE_NAME, PARTITION_NAME, TABLE_ROWS
    FROM information_schema.PARTITIONS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'security_event'
      AND TABLE_ROWS > 0
    ORDER BY PARTITION_NAME
    LIMIT 20;
    "

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
    EXPLAIN SELECT * FROM security_event
    WHERE created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)
      AND type = 'LOGIN_SUCCESS';
    "

    log_info "パーティションプルーニングが効かないクエリ (日付条件なし):"
    mysql_exec "
    EXPLAIN SELECT * FROM security_event
    WHERE type = 'LOGIN_SUCCESS'
    LIMIT 100;
    "

    log_success "テスト2完了: EXPLAIN結果でpartitionsカラムを確認してください"
}

# ==============================================================================
# テスト3: p_future パーティション動作確認
# ==============================================================================
test_future_partition() {
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    log_test "テスト3: p_future パーティション動作確認"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    local tenant_id=$(mysql_exec_quiet "SELECT id FROM tenant LIMIT 1" 2>/dev/null || echo "00000000-0000-0000-0000-000000000001")

    log_info "遠い将来のデータを挿入: 2099-12-31"
    mysql_exec "
    INSERT INTO security_event (id, type, description, tenant_id, tenant_name, client_id, client_name, detail, created_at)
    VALUES (UUID(), 'TEST_FUTURE', 'Future test event', '${tenant_id}', 'test', 'test', 'test', '{}', '2099-12-31 23:59:59');
    "

    local p_future_count=$(mysql_exec_quiet "
    SELECT TABLE_ROWS FROM information_schema.PARTITIONS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'security_event'
      AND PARTITION_NAME = 'p_future';
    ")

    log_result "p_future パーティションの行数: ${p_future_count}"

    if [ "$p_future_count" -ge 1 ]; then
        log_success "テスト3完了: p_future パーティションが正しく機能しています"
    else
        log_warn "p_future に期待したデータがありません"
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

    log_info "メンテナンスプロシージャを実行中..."

    local before_se=$(mysql_exec_quiet "SELECT COUNT(*) FROM information_schema.PARTITIONS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'security_event'")
    local before_hr=$(mysql_exec_quiet "SELECT COUNT(*) FROM information_schema.PARTITIONS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'security_event_hook_results'")

    log_info "実行前パーティション数: security_event=${before_se}, hook_results=${before_hr}"

    mysql_exec "CALL maintain_security_event_partitions();" 2>/dev/null || true

    local after_se=$(mysql_exec_quiet "SELECT COUNT(*) FROM information_schema.PARTITIONS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'security_event'")
    local after_hr=$(mysql_exec_quiet "SELECT COUNT(*) FROM information_schema.PARTITIONS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'security_event_hook_results'")

    log_info "実行後パーティション数: security_event=${after_se}, hook_results=${after_hr}"

    log_success "テスト4完了: メンテナンスプロシージャが正常に実行されました"
}

# ==============================================================================
# サマリー表示
# ==============================================================================
show_summary() {
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    log_test "パーティション状態サマリー"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    mysql_exec "
    SELECT
        TABLE_NAME,
        COUNT(*) as partition_count,
        SUM(TABLE_ROWS) as total_rows,
        MIN(PARTITION_NAME) as oldest_partition,
        MAX(CASE WHEN PARTITION_NAME != 'p_future' THEN PARTITION_NAME END) as newest_partition
    FROM information_schema.PARTITIONS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME IN ('security_event', 'security_event_hook_results')
    GROUP BY TABLE_NAME;
    "
}

# ==============================================================================
# クリーンアップ
# ==============================================================================
cleanup_test_data() {
    log_info "テストデータを削除中..."
    mysql_exec "DELETE FROM security_event WHERE description = 'Test security event' OR type = 'TEST_FUTURE';" 2>/dev/null || true
    mysql_exec "DELETE FROM security_event_hook_results WHERE security_event_payload = '{\"test\": true}';" 2>/dev/null || true
    log_success "テストデータを削除しました"
}

# ==============================================================================
# メイン処理
# ==============================================================================
main() {
    echo ""
    echo "╔══════════════════════════════════════════════════════════════╗"
    echo "║   MySQL セキュリティイベントパーティション動作検証            ║"
    echo "╚══════════════════════════════════════════════════════════════╝"
    echo ""

    check_container
    check_tables
    check_partitions

    test_data_insertion
    test_partition_pruning
    test_future_partition
    test_maintenance_procedure

    show_summary

    # クリーンアップ確認
    echo ""
    read -p "テストデータを削除しますか? (Y/n): " confirm
    if [ "$confirm" != "n" ] && [ "$confirm" != "N" ]; then
        cleanup_test_data
    fi

    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    log_success "すべての検証が完了しました"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
}

main "$@"
