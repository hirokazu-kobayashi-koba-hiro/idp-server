#!/usr/bin/env bash
#
# MySQL Event Scheduler 自動実行検証スクリプト
#
# 目的:
#   - Event Schedulerの自動メンテナンス実行を確認
#   - 短い間隔（1分）で自動実行させて動作を確認
#
# 使用方法:
#   ./scripts/mysql_partition/event-scheduler-autorun-test.sh
#
# 注意:
#   - テスト後は元の設定に戻すことを推奨
#   - Event Schedulerが有効である必要がある
#

set -e

# 設定
CONTAINER_NAME="${MYSQL_CONTAINER:-idp-mysql}"
DB_USER="${MYSQL_USER:-idpserver}"
DB_PASSWORD="${MYSQL_PASSWORD:-idpserver}"
DB_NAME="${MYSQL_DB:-idpserver}"

# テスト用設定
TEST_EVENT_NAME="evt_test_autorun"
TEST_INTERVAL_SECONDS=30

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
log_step() { echo -e "${CYAN}[STEP]${NC} $1"; }
log_test() { echo -e "${MAGENTA}[TEST]${NC} $1"; }

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
        exit 1
    fi
    log_success "コンテナ '$CONTAINER_NAME' が稼働中"
}

# ==============================================================================
# Event Scheduler状態確認
# ==============================================================================
check_event_scheduler() {
    log_step "Event Scheduler の状態を確認中..."

    local status=$(mysql_exec_quiet "SHOW VARIABLES LIKE 'event_scheduler';" | awk '{print $2}')

    echo ""
    log_info "Event Scheduler: ${status}"

    if [ "$status" != "ON" ]; then
        log_warn "Event Scheduler が無効です。有効化します..."
        mysql_exec "SET GLOBAL event_scheduler = ON;"
        log_success "Event Scheduler を有効化しました"
    else
        log_success "Event Scheduler は有効です"
    fi
}

# ==============================================================================
# 現在のイベント確認
# ==============================================================================
show_current_events() {
    log_step "現在登録されているイベント一覧:"

    mysql_exec "
    SELECT
        EVENT_NAME,
        STATUS,
        EVENT_TYPE,
        INTERVAL_VALUE,
        INTERVAL_FIELD,
        LAST_EXECUTED
    FROM information_schema.EVENTS
    WHERE EVENT_SCHEMA = DATABASE();
    "
}

# ==============================================================================
# テスト用イベント作成
# ==============================================================================
create_test_event() {
    log_step "テスト用イベントを作成中..."

    # テスト用ログテーブル作成
    mysql_exec "
    CREATE TABLE IF NOT EXISTS event_scheduler_test_log (
        id INT AUTO_INCREMENT PRIMARY KEY,
        executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        message VARCHAR(255)
    );
    "

    # 古いテストイベントを削除
    mysql_exec "DROP EVENT IF EXISTS ${TEST_EVENT_NAME};"

    # テスト用イベント作成（30秒間隔）
    mysql_exec "
    CREATE EVENT ${TEST_EVENT_NAME}
    ON SCHEDULE EVERY ${TEST_INTERVAL_SECONDS} SECOND
    STARTS CURRENT_TIMESTAMP
    ON COMPLETION PRESERVE
    ENABLE
    COMMENT 'Test event for autorun verification'
    DO
        INSERT INTO event_scheduler_test_log (message)
        VALUES (CONCAT('Event executed at ', NOW()));
    "

    log_success "テストイベント '${TEST_EVENT_NAME}' を作成しました（${TEST_INTERVAL_SECONDS}秒間隔）"
}

# ==============================================================================
# 自動実行の監視
# ==============================================================================
monitor_event_execution() {
    log_step "イベント自動実行を監視中..."
    log_info "Ctrl+C で監視を終了できます"
    echo ""

    local wait_time=$((TEST_INTERVAL_SECONDS * 3 + 10))
    log_info "${wait_time}秒間監視します（${TEST_INTERVAL_SECONDS}秒間隔 x 3回 + バッファ）"
    echo ""

    local start_time=$(date +%s)
    local end_time=$((start_time + wait_time))
    local last_count=0
    local check_interval=5

    while [ $(date +%s) -lt $end_time ]; do
        local current_count=$(mysql_exec_quiet "SELECT COUNT(*) FROM event_scheduler_test_log;")
        local last_executed=$(mysql_exec_quiet "
            SELECT COALESCE(DATE_FORMAT(LAST_EXECUTED, '%Y-%m-%d %H:%i:%s'), 'never')
            FROM information_schema.EVENTS
            WHERE EVENT_SCHEMA = DATABASE()
              AND EVENT_NAME = '${TEST_EVENT_NAME}';
        ")

        if [ "$current_count" != "$last_count" ]; then
            log_success "イベント実行検出! (実行回数: ${current_count}, 最終実行: ${last_executed})"
            last_count=$current_count
        else
            local remaining=$((end_time - $(date +%s)))
            printf "\r${BLUE}[INFO]${NC} 監視中... 実行回数: ${current_count}, 最終実行: ${last_executed}, 残り: ${remaining}秒  "
        fi

        sleep $check_interval
    done

    echo ""
    echo ""

    # 最終結果
    local final_count=$(mysql_exec_quiet "SELECT COUNT(*) FROM event_scheduler_test_log;")

    if [ "$final_count" -ge 2 ]; then
        log_success "テスト成功: イベントが ${final_count} 回実行されました"
    elif [ "$final_count" -eq 1 ]; then
        log_warn "イベントが ${final_count} 回しか実行されませんでした"
    else
        log_error "テスト失敗: イベントが実行されませんでした"
    fi

    echo ""
    log_info "実行ログ:"
    mysql_exec "SELECT * FROM event_scheduler_test_log ORDER BY executed_at DESC LIMIT 10;"
}

# ==============================================================================
# メインイベントの自動実行テスト
# ==============================================================================
test_main_event() {
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    log_test "メンテナンスイベントの状態確認"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    local main_event="evt_maintain_statistics_partitions"
    local event_exists=$(mysql_exec_quiet "
        SELECT COUNT(*) FROM information_schema.EVENTS
        WHERE EVENT_SCHEMA = DATABASE()
          AND EVENT_NAME = '${main_event}';
    ")

    if [ "$event_exists" != "1" ]; then
        log_warn "メインイベント '${main_event}' が存在しません"
        log_info "setup-partitions.sql を実行してください"
        return
    fi

    mysql_exec "
    SELECT
        EVENT_NAME,
        STATUS,
        INTERVAL_VALUE,
        INTERVAL_FIELD,
        STARTS,
        LAST_EXECUTED
    FROM information_schema.EVENTS
    WHERE EVENT_SCHEMA = DATABASE()
      AND EVENT_NAME = '${main_event}';
    "

    local last_executed=$(mysql_exec_quiet "
        SELECT COALESCE(DATE_FORMAT(LAST_EXECUTED, '%Y-%m-%d %H:%i:%s'), 'never')
        FROM information_schema.EVENTS
        WHERE EVENT_SCHEMA = DATABASE()
          AND EVENT_NAME = '${main_event}';
    ")

    log_info "最終実行時刻: ${last_executed}"

    if [ "$last_executed" = "never" ] || [ "$last_executed" = "NULL" ]; then
        log_info "イベントはまだ一度も実行されていません（次回のスケジュール時刻に実行されます）"
    else
        log_success "イベントは正常にスケジュールされています"
    fi
}

# ==============================================================================
# 一時的にイベント間隔を短くしてテスト
# ==============================================================================
test_with_short_interval() {
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    log_test "短い間隔でのメンテナンスイベントテスト"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    local main_event="evt_maintain_statistics_partitions"

    # 元の設定を保存
    local original_interval=$(mysql_exec_quiet "
        SELECT CONCAT(INTERVAL_VALUE, ' ', INTERVAL_FIELD)
        FROM information_schema.EVENTS
        WHERE EVENT_SCHEMA = DATABASE()
          AND EVENT_NAME = '${main_event}';
    ")

    if [ -z "$original_interval" ]; then
        log_warn "メインイベントが存在しないためスキップします"
        return
    fi

    log_info "元のスケジュール: ${original_interval}"
    log_warn "イベント間隔を1分に変更してテストします"

    read -p "続行しますか? (y/N): " confirm
    if [ "$confirm" != "y" ] && [ "$confirm" != "Y" ]; then
        log_info "スキップしました"
        return
    fi

    # 前回のパーティション数を記録
    local before_daily=$(mysql_exec_quiet "SELECT COUNT(*) FROM information_schema.PARTITIONS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'statistics_daily_users';")

    # イベントを1分間隔に変更
    mysql_exec "
    ALTER EVENT ${main_event}
    ON SCHEDULE EVERY 1 MINUTE
    STARTS CURRENT_TIMESTAMP;
    "

    log_success "イベント間隔を1分に変更しました"
    log_info "2分間待機してイベント実行を確認します..."

    sleep 120

    # 実行後のパーティション数を確認
    local after_daily=$(mysql_exec_quiet "SELECT COUNT(*) FROM information_schema.PARTITIONS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'statistics_daily_users';")

    local last_executed=$(mysql_exec_quiet "
        SELECT DATE_FORMAT(LAST_EXECUTED, '%Y-%m-%d %H:%i:%s')
        FROM information_schema.EVENTS
        WHERE EVENT_SCHEMA = DATABASE()
          AND EVENT_NAME = '${main_event}';
    ")

    log_info "最終実行時刻: ${last_executed}"
    log_info "パーティション数変化: ${before_daily} → ${after_daily}"

    # 元のスケジュールに戻す
    log_info "元のスケジュールに戻しています..."
    mysql_exec "
    ALTER EVENT ${main_event}
    ON SCHEDULE EVERY 1 DAY
    STARTS (TIMESTAMP(CURRENT_DATE) + INTERVAL 1 DAY + INTERVAL 3 HOUR);
    "

    log_success "元のスケジュール（毎日AM 3:00）に戻しました"
}

# ==============================================================================
# クリーンアップ
# ==============================================================================
cleanup_test() {
    log_step "テスト環境をクリーンアップ中..."

    mysql_exec "DROP EVENT IF EXISTS ${TEST_EVENT_NAME};"
    mysql_exec "DROP TABLE IF EXISTS event_scheduler_test_log;"

    log_success "テスト環境をクリーンアップしました"
}

# ==============================================================================
# ヘルプ表示
# ==============================================================================
show_help() {
    echo "使用方法: $0 [オプション]"
    echo ""
    echo "オプション:"
    echo "  --help          このヘルプを表示"
    echo "  --status        Event Scheduler の状態を表示"
    echo "  --quick         クイックテスト（30秒間隔のテストイベント）"
    echo "  --main          メインイベントの状態確認"
    echo "  --short-interval メインイベントを1分間隔に変更してテスト"
    echo "  --cleanup       テスト環境のクリーンアップ"
    echo "  (オプションなし) すべてのテストを実行"
    echo ""
    echo "環境変数:"
    echo "  MYSQL_CONTAINER  コンテナ名 (デフォルト: idp-mysql)"
    echo "  MYSQL_USER       ユーザー名 (デフォルト: idpserver)"
    echo "  MYSQL_PASSWORD   パスワード (デフォルト: idpserver)"
    echo "  MYSQL_DB         データベース名 (デフォルト: idpserver)"
}

# ==============================================================================
# メイン処理
# ==============================================================================
main() {
    echo ""
    echo "╔══════════════════════════════════════════════════════════════╗"
    echo "║     MySQL Event Scheduler 自動実行検証スクリプト              ║"
    echo "╚══════════════════════════════════════════════════════════════╝"
    echo ""

    case "${1:-}" in
        --help)
            show_help
            exit 0
            ;;
        --status)
            check_container
            check_event_scheduler
            show_current_events
            ;;
        --quick)
            check_container
            check_event_scheduler
            create_test_event
            monitor_event_execution
            cleanup_test
            ;;
        --main)
            check_container
            check_event_scheduler
            test_main_event
            ;;
        --short-interval)
            check_container
            check_event_scheduler
            test_with_short_interval
            ;;
        --cleanup)
            check_container
            cleanup_test
            ;;
        "")
            check_container
            check_event_scheduler
            show_current_events
            echo ""
            create_test_event
            monitor_event_execution
            test_main_event
            cleanup_test
            ;;
        *)
            log_error "不明なオプション: $1"
            show_help
            exit 1
            ;;
    esac

    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    log_success "テストが完了しました"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
}

main "$@"
