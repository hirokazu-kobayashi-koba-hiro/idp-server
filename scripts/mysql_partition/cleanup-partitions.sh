#!/usr/bin/env bash
#
# MySQL パーティション環境クリーンアップスクリプト
#
# 機能:
#   - 検証用に作成したStored ProcedureとEventの削除
#   - パーティションテーブルを通常テーブルに戻す（オプション）
#
# 使用方法:
#   ./scripts/mysql_partition/cleanup-partitions.sh           # プロシージャとイベントのみ削除
#   ./scripts/mysql_partition/cleanup-partitions.sh --full    # テーブルも通常に戻す
#

set -e

# 設定
CONTAINER_NAME="${MYSQL_CONTAINER:-idp-mysql}"
DB_USER="${MYSQL_USER:-idpserver}"
DB_PASSWORD="${MYSQL_PASSWORD:-idpserver}"
DB_NAME="${MYSQL_DB:-idpserver}"

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
log_step() { echo -e "${CYAN}[STEP]${NC} $1"; }

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
# Eventの削除
# ==============================================================================
cleanup_events() {
    log_step "Event Schedulerのイベントを削除中..."

    local events=$(mysql_exec_quiet "
    SELECT EVENT_NAME FROM information_schema.EVENTS
    WHERE EVENT_SCHEMA = DATABASE()
      AND EVENT_NAME LIKE 'evt_%statistics%';
    ")

    if [ -z "$events" ]; then
        log_info "削除対象のイベントはありません"
        return
    fi

    for event in $events; do
        log_info "削除中: EVENT ${event}"
        mysql_exec "DROP EVENT IF EXISTS ${event};"
    done

    log_success "イベントの削除が完了しました"
}

# ==============================================================================
# Stored Procedureの削除
# ==============================================================================
cleanup_procedures() {
    log_step "Stored Procedureを削除中..."

    local procedures=(
        "maintain_statistics_partitions"
        "create_daily_users_partition"
        "create_monthly_users_partition"
        "create_yearly_users_partition"
        "drop_old_daily_users_partitions"
        "drop_old_monthly_users_partitions"
        "drop_old_yearly_users_partitions"
    )

    for proc in "${procedures[@]}"; do
        local exists=$(mysql_exec_quiet "
        SELECT COUNT(*) FROM information_schema.ROUTINES
        WHERE ROUTINE_SCHEMA = DATABASE()
          AND ROUTINE_NAME = '${proc}';
        ")

        if [ "$exists" = "1" ]; then
            log_info "削除中: PROCEDURE ${proc}"
            mysql_exec "DROP PROCEDURE IF EXISTS ${proc};"
        fi
    done

    log_success "Stored Procedureの削除が完了しました"
}

# ==============================================================================
# テストデータの削除
# ==============================================================================
cleanup_test_data() {
    log_step "検証用テストデータを削除中..."

    # 将来日付のテストデータを削除
    mysql_exec "DELETE FROM statistics_daily_users WHERE stat_date > DATE_ADD(CURDATE(), INTERVAL 1 YEAR);" || true
    mysql_exec "DELETE FROM statistics_monthly_users WHERE stat_month > DATE_ADD(CURDATE(), INTERVAL 1 YEAR);" || true
    mysql_exec "DELETE FROM statistics_yearly_users WHERE stat_year > DATE_ADD(CURDATE(), INTERVAL 1 YEAR);" || true

    log_success "テストデータの削除が完了しました"
}

# ==============================================================================
# バックアップテーブルの削除
# ==============================================================================
cleanup_backup_tables() {
    log_step "バックアップテーブルを削除中..."

    local backup_tables=$(mysql_exec_quiet "
    SELECT TABLE_NAME FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME LIKE 'statistics%_backup';
    ")

    if [ -z "$backup_tables" ]; then
        log_info "バックアップテーブルはありません"
        return
    fi

    for table in $backup_tables; do
        log_info "削除中: TABLE ${table}"
        mysql_exec "DROP TABLE IF EXISTS ${table};"
    done

    log_success "バックアップテーブルの削除が完了しました"
}

# ==============================================================================
# パーティション削除（通常テーブルに戻す）
# ==============================================================================
remove_partitions() {
    log_step "パーティションを削除して通常テーブルに変換中..."
    log_warn "この操作はパーティションを削除します。データは保持されます。"

    read -p "続行しますか? (y/N): " confirm
    if [ "$confirm" != "y" ] && [ "$confirm" != "Y" ]; then
        log_info "キャンセルしました"
        return
    fi

    local tables=("statistics_daily_users" "statistics_monthly_users" "statistics_yearly_users")

    for table in "${tables[@]}"; do
        local partition_count=$(mysql_exec_quiet "
        SELECT COUNT(*) FROM information_schema.PARTITIONS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = '${table}';
        ")

        if [ "$partition_count" -gt 1 ]; then
            log_info "変換中: ${table} (${partition_count} パーティション)"
            mysql_exec "ALTER TABLE ${table} REMOVE PARTITIONING;"
            log_success "${table} を通常テーブルに変換しました"
        else
            log_info "${table} はパーティション化されていません"
        fi
    done

    log_success "パーティションの削除が完了しました"
}

# ==============================================================================
# 状態表示
# ==============================================================================
show_status() {
    echo ""
    log_step "現在の状態:"

    echo ""
    echo "--- イベント ---"
    mysql_exec "
    SELECT EVENT_NAME, STATUS, LAST_EXECUTED
    FROM information_schema.EVENTS
    WHERE EVENT_SCHEMA = DATABASE()
      AND EVENT_NAME LIKE '%statistics%';
    " 2>/dev/null || echo "イベントなし"

    echo ""
    echo "--- Stored Procedure ---"
    mysql_exec "
    SELECT ROUTINE_NAME, ROUTINE_TYPE
    FROM information_schema.ROUTINES
    WHERE ROUTINE_SCHEMA = DATABASE()
      AND ROUTINE_NAME LIKE '%partition%';
    " 2>/dev/null || echo "プロシージャなし"

    echo ""
    echo "--- パーティション数 ---"
    mysql_exec "
    SELECT TABLE_NAME, COUNT(*) AS partition_count
    FROM information_schema.PARTITIONS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME LIKE 'statistics%_users'
    GROUP BY TABLE_NAME;
    " 2>/dev/null
}

# ==============================================================================
# ヘルプ表示
# ==============================================================================
show_help() {
    echo "使用方法: $0 [オプション]"
    echo ""
    echo "オプション:"
    echo "  --help          このヘルプを表示"
    echo "  --status        現在の状態を表示"
    echo "  --events        イベントのみ削除"
    echo "  --procedures    Stored Procedureのみ削除"
    echo "  --test-data     テストデータのみ削除"
    echo "  --backups       バックアップテーブルのみ削除"
    echo "  --full          すべて削除（パーティションも削除して通常テーブルに戻す）"
    echo "  (オプションなし) イベントとStored Procedureを削除"
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
    echo "║       MySQL パーティション環境クリーンアップ                    ║"
    echo "╚══════════════════════════════════════════════════════════════╝"
    echo ""

    case "${1:-}" in
        --help)
            show_help
            exit 0
            ;;
        --status)
            check_container
            show_status
            exit 0
            ;;
        --events)
            check_container
            cleanup_events
            ;;
        --procedures)
            check_container
            cleanup_procedures
            ;;
        --test-data)
            check_container
            cleanup_test_data
            ;;
        --backups)
            check_container
            cleanup_backup_tables
            ;;
        --full)
            check_container
            cleanup_events
            cleanup_procedures
            cleanup_test_data
            cleanup_backup_tables
            remove_partitions
            ;;
        "")
            check_container
            cleanup_events
            cleanup_procedures
            ;;
        *)
            log_error "不明なオプション: $1"
            show_help
            exit 1
            ;;
    esac

    show_status
    echo ""
    log_success "クリーンアップが完了しました"
}

main "$@"
