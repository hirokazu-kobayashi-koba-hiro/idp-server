#!/usr/bin/env bash
#
# pg_partman 検証環境クリーンアップスクリプト
#
# 使用方法:
#   ./scripts/pg_partman/cleanup-pg_partman.sh
#

set -e

# 設定
CONTAINER_NAME="${POSTGRES_CONTAINER:-postgres-primary}"
DB_USER="${POSTGRES_USER:-idpserver}"
DB_NAME="${POSTGRES_DB:-idpserver}"

# 色付き出力
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

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

# 検証用テーブル削除
cleanup_tables() {
    log_info "検証用テーブルを削除中..."

    psql_exec "
    -- pg_partman設定から削除
    DELETE FROM partman.part_config
    WHERE parent_table LIKE 'public.statistics_%_pm';

    DELETE FROM partman.part_config_sub
    WHERE sub_parent LIKE 'public.statistics_%_pm';

    -- テーブル削除
    DROP TABLE IF EXISTS statistics_daily_users_pm CASCADE;
    DROP TABLE IF EXISTS statistics_monthly_users_pm CASCADE;
    DROP TABLE IF EXISTS statistics_yearly_users_pm CASCADE;

    -- シミュレーション用テーブル削除
    DROP TABLE IF EXISTS partman_test CASCADE;
    " 2>/dev/null

    log_success "検証用テーブル削除完了"
}

# pg_cronジョブ削除
cleanup_cron_jobs() {
    log_info "pg_cronジョブを確認中..."

    local pg_cron_installed=$(psql_exec_quiet "SELECT count(*) FROM pg_extension WHERE extname = 'pg_cron'")

    if [ "$pg_cron_installed" = "1" ]; then
        log_info "pg_cronジョブを削除中..."
        psql_exec "
        SELECT cron.unschedule(jobid)
        FROM cron.job
        WHERE command LIKE '%partman%';
        " 2>/dev/null || true

        log_success "pg_cronジョブ削除完了"
    else
        log_info "pg_cronは未インストール"
    fi
}

# pg_partman拡張削除（オプション）
cleanup_extension() {
    log_info "pg_partman拡張を削除しますか? (y/n)"
    read -r answer

    if [ "$answer" = "y" ] || [ "$answer" = "Y" ]; then
        log_info "pg_partman拡張を削除中..."

        psql_exec "
        DROP EXTENSION IF EXISTS pg_partman CASCADE;
        DROP SCHEMA IF EXISTS partman CASCADE;
        " 2>/dev/null

        log_success "pg_partman拡張削除完了"
    else
        log_info "pg_partman拡張は保持されます"
    fi
}

# 状態確認
show_status() {
    echo ""
    echo "--- 残存テーブル確認 ---"
    psql_exec "
    SELECT tablename
    FROM pg_tables
    WHERE tablename LIKE 'statistics_%_pm%'
       OR tablename LIKE 'partman_test%'
    ORDER BY tablename;
    "

    echo ""
    echo "--- pg_partman設定確認 ---"
    psql_exec "
    SELECT count(*) as config_count
    FROM partman.part_config
    WHERE parent_table LIKE 'public.statistics_%_pm';
    " 2>/dev/null || echo "  partman.part_config: テーブルなし"
}

# ヘルプ
show_help() {
    echo "pg_partman 検証環境クリーンアップスクリプト"
    echo ""
    echo "使用方法: $0 [オプション]"
    echo ""
    echo "オプション:"
    echo "  -h, --help     このヘルプを表示"
    echo "  -a, --all      pg_partman拡張も含めて完全削除"
    echo "  -s, --status   現在の状態を表示"
    echo ""
    echo "環境変数:"
    echo "  POSTGRES_CONTAINER  コンテナ名 (デフォルト: postgres-primary)"
}

# メイン
main() {
    case "${1:-}" in
        -h|--help)
            show_help
            exit 0
            ;;
        -s|--status)
            check_container
            show_status
            exit 0
            ;;
        -a|--all)
            check_container
            cleanup_cron_jobs
            cleanup_tables
            cleanup_extension
            show_status
            log_success "完全クリーンアップ完了"
            exit 0
            ;;
    esac

    echo ""
    echo "=============================================="
    echo " pg_partman 検証環境クリーンアップ"
    echo "=============================================="
    echo " コンテナ: ${CONTAINER_NAME}"
    echo " データベース: ${DB_NAME}"
    echo "=============================================="
    echo ""

    check_container
    cleanup_cron_jobs
    cleanup_tables
    show_status

    echo ""
    log_success "クリーンアップ完了"
    echo ""
    echo "pg_partman拡張も削除する場合: $0 --all"
}

main "$@"
