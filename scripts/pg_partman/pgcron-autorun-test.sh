#!/usr/bin/env bash
#
# pg_cron + pg_partman 自動実行検証スクリプト
#
# 目的:
#   - pg_cronによるpg_partmanメンテナンスの自動実行を確認
#   - AWS RDS環境と同等の構成で検証
#
# 使用方法:
#   ./scripts/pg_partman/pgcron-autorun-test.sh
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
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m'

log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_step() { echo -e "${CYAN}[STEP]${NC} $1"; }
log_test() { echo -e "${MAGENTA}[TEST]${NC} $1"; }

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

# パーティション数を取得
get_partition_count() {
    local table_name=$1
    psql_exec_quiet "
        SELECT COUNT(*)
        FROM pg_inherits i
        JOIN pg_class c ON i.inhrelid = c.oid
        JOIN pg_class p ON i.inhparent = p.oid
        WHERE p.relname = '${table_name}'
          AND c.relname NOT LIKE '%_default'
    "
}

# ==============================================================================
# pg_cron設定確認
# ==============================================================================
check_pg_cron() {
    log_step "pg_cron設定を確認中..."

    local installed=$(psql_exec_quiet "SELECT count(*) FROM pg_extension WHERE extname = 'pg_cron'")

    if [ "$installed" != "1" ]; then
        log_warn "pg_cronがインストールされていません。インストール中..."
        psql_exec "CREATE EXTENSION IF NOT EXISTS pg_cron;" 2>/dev/null || {
            log_error "pg_cronのインストールに失敗しました"
            log_info "shared_preload_libraries に pg_cron を追加してください"
            exit 1
        }
    fi

    echo ""
    echo "--- pg_cron設定 ---"
    psql_exec "
        SELECT name, setting
        FROM pg_settings
        WHERE name LIKE 'cron.%'
        ORDER BY name;
    "

    log_success "pg_cronが有効です"
}

# ==============================================================================
# テスト用テーブル作成
# ==============================================================================
setup_test_table() {
    log_step "pg_cronテスト用テーブルを作成中..."

    psql_exec "
    -- 既存テーブル削除
    DROP TABLE IF EXISTS pgcron_test_table CASCADE;

    -- テストテーブル作成（日別パーティション）
    CREATE TABLE pgcron_test_table (
        id UUID DEFAULT gen_random_uuid(),
        tenant_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001'::uuid,
        event_date DATE NOT NULL,
        data TEXT,
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (tenant_id, event_date, id)
    ) PARTITION BY RANGE (event_date);

    COMMENT ON TABLE pgcron_test_table IS 'pg_cron auto-maintenance test table';
    "

    # 既存のpg_partman設定を削除
    psql_exec "
    DELETE FROM partman.part_config WHERE parent_table = 'public.pgcron_test_table';
    " 2>/dev/null || true

    # pg_partman設定（premake=2、短い保持期間）
    psql_exec "
    SELECT partman.create_parent(
        p_parent_table => 'public.pgcron_test_table',
        p_control => 'event_date',
        p_type => 'range',
        p_interval => '1 day',
        p_premake => 2,
        p_start_partition => CURRENT_DATE::text
    );

    UPDATE partman.part_config
    SET infinite_time_partitions = true,
        retention = '3 days',
        retention_keep_table = false
    WHERE parent_table = 'public.pgcron_test_table';
    "

    log_success "テストテーブル作成完了"
}

# ==============================================================================
# pg_cronジョブ設定（1分間隔でテスト）
# ==============================================================================
setup_cron_job() {
    log_step "pg_cronジョブを設定中（1分間隔）..."

    # 既存のテストジョブを削除
    psql_exec "
    SELECT cron.unschedule(jobid)
    FROM cron.job
    WHERE jobname = 'partman-test-job';
    " 2>/dev/null || true

    # 1分間隔でメンテナンス実行（テスト用）
    psql_exec "
    SELECT cron.schedule(
        'partman-test-job',
        '* * * * *',
        \$\$CALL partman.run_maintenance_proc()\$\$
    );
    "

    echo ""
    echo "--- 登録されたジョブ ---"
    psql_exec "
    SELECT jobid, jobname, schedule, command
    FROM cron.job
    WHERE jobname = 'partman-test-job';
    "

    log_success "pg_cronジョブ設定完了（毎分実行）"
}

# ==============================================================================
# 初期状態確認
# ==============================================================================
show_initial_state() {
    log_test "初期状態確認"

    echo ""
    echo "--- 現在のパーティション ---"
    psql_exec "
    SELECT c.relname as partition
    FROM pg_inherits i
    JOIN pg_class c ON i.inhrelid = c.oid
    JOIN pg_class p ON i.inhparent = p.oid
    WHERE p.relname = 'pgcron_test_table'
    ORDER BY c.relname;
    "

    local count=$(get_partition_count "pgcron_test_table")
    log_info "初期パーティション数: ${count}"
}

# ==============================================================================
# pg_cron自動実行を監視
# ==============================================================================
monitor_cron_execution() {
    log_test "pg_cron自動実行を監視中..."

    local initial_count=$(get_partition_count "pgcron_test_table")

    echo ""
    log_info "pg_cronは毎分（:00秒）にメンテナンスを実行します"
    log_info "最大2分間待機して、パーティション変化を確認します..."
    echo ""

    # 監視ループ（最大2分間）
    local max_wait=120
    local elapsed=0
    local check_interval=10

    while [ $elapsed -lt $max_wait ]; do
        sleep $check_interval
        elapsed=$((elapsed + check_interval))

        local current_count=$(get_partition_count "pgcron_test_table")

        # パーティション数が変化したかチェック
        if [ "$current_count" != "$initial_count" ]; then
            echo ""
            log_success "パーティション数が変化しました！ ${initial_count} → ${current_count}"
            echo ""
            echo "--- 更新後のパーティション ---"
            psql_exec "
            SELECT c.relname as partition
            FROM pg_inherits i
            JOIN pg_class c ON i.inhrelid = c.oid
            JOIN pg_class p ON i.inhparent = p.oid
            WHERE p.relname = 'pgcron_test_table'
            ORDER BY c.relname;
            "
            return 0
        fi

        printf "\r  経過時間: ${elapsed}秒 / ${max_wait}秒 (パーティション数: ${current_count})"
    done

    echo ""
    log_warn "タイムアウト: パーティション数に変化がありませんでした"
    return 1
}

# ==============================================================================
# pg_cronジョブ実行履歴確認
# ==============================================================================
check_job_history() {
    log_step "pg_cronジョブ実行履歴を確認中..."

    echo ""
    echo "--- 最近のジョブ実行履歴 ---"
    psql_exec "
    SELECT
        jobid,
        runid,
        job_pid,
        status,
        return_message,
        start_time,
        end_time
    FROM cron.job_run_details
    WHERE jobid = (SELECT jobid FROM cron.job WHERE jobname = 'partman-test-job')
    ORDER BY start_time DESC
    LIMIT 5;
    " 2>/dev/null || echo "  履歴テーブルが存在しないか、まだ実行されていません"
}

# ==============================================================================
# クリーンアップ
# ==============================================================================
cleanup() {
    log_step "テストリソースをクリーンアップ中..."

    # pg_cronジョブ削除
    psql_exec "
    SELECT cron.unschedule(jobid)
    FROM cron.job
    WHERE jobname = 'partman-test-job';
    " 2>/dev/null || true

    # pg_partman設定削除
    psql_exec "
    DELETE FROM partman.part_config WHERE parent_table = 'public.pgcron_test_table';
    " 2>/dev/null || true

    # テストテーブル削除
    psql_exec "
    DROP TABLE IF EXISTS pgcron_test_table CASCADE;
    " 2>/dev/null

    log_success "クリーンアップ完了"
}

# ==============================================================================
# サマリー表示
# ==============================================================================
show_summary() {
    echo ""
    echo "=============================================="
    echo " pg_cron + pg_partman 検証サマリー"
    echo "=============================================="
    echo ""
    echo "検証項目:"
    echo "  1. ✅ pg_cronによるジョブスケジューリング"
    echo "  2. ✅ run_maintenance_proc()の定期実行"
    echo "  3. ✅ パーティションの自動作成"
    echo ""
    echo "AWS RDS での設定方法:"
    echo ""
    echo "  -- パラメータグループで設定（再起動必要）"
    echo "  shared_preload_libraries = 'pg_cron'"
    echo ""
    echo "  -- SQLで設定"
    echo "  CREATE EXTENSION pg_cron;"
    echo ""
    echo "  -- 毎時メンテナンスをスケジュール（推奨）"
    echo "  SELECT cron.schedule("
    echo "      'partman-maintenance',"
    echo "      '0 * * * *',"
    echo "      \\\$\\\$CALL partman.run_maintenance_proc()\\\$\\\$"
    echo "  );"
    echo ""
    echo "  -- または毎日AM2時に実行"
    echo "  SELECT cron.schedule("
    echo "      'partman-daily',"
    echo "      '0 2 * * *',"
    echo "      \\\$\\\$CALL partman.run_maintenance_proc()\\\$\\\$"
    echo "  );"
    echo ""
}

# ==============================================================================
# ヘルプ
# ==============================================================================
show_help() {
    echo "pg_cron + pg_partman 自動実行検証スクリプト"
    echo ""
    echo "使用方法: $0 [オプション]"
    echo ""
    echo "オプション:"
    echo "  -h, --help      このヘルプを表示"
    echo "  -s, --status    pg_cronの現在の状態を表示"
    echo "  -c, --cleanup   テストリソースのみクリーンアップ"
    echo "  -j, --jobs      登録されているジョブ一覧を表示"
    echo ""
    echo "環境変数:"
    echo "  POSTGRES_CONTAINER  コンテナ名 (デフォルト: postgres-primary)"
}

# ==============================================================================
# ジョブ一覧表示
# ==============================================================================
show_jobs() {
    log_step "登録されているpg_cronジョブ..."

    echo ""
    psql_exec "
    SELECT
        jobid,
        jobname,
        schedule,
        command,
        nodename,
        active
    FROM cron.job
    ORDER BY jobid;
    "
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
        -s|--status)
            check_container
            check_pg_cron
            exit 0
            ;;
        -c|--cleanup)
            check_container
            cleanup
            exit 0
            ;;
        -j|--jobs)
            check_container
            show_jobs
            exit 0
            ;;
    esac

    echo ""
    echo "=============================================="
    echo " pg_cron + pg_partman 自動実行検証"
    echo "=============================================="
    echo " コンテナ: ${CONTAINER_NAME}"
    echo " データベース: ${DB_NAME}"
    echo ""
    echo " ※ AWS RDS と同等の構成で検証"
    echo "=============================================="
    echo ""

    check_container
    check_pg_cron

    # pg_partman確認
    local partman_installed=$(psql_exec_quiet "SELECT count(*) FROM pg_extension WHERE extname = 'pg_partman'")
    if [ "$partman_installed" != "1" ]; then
        log_error "pg_partmanがインストールされていません"
        log_info "先に ./scripts/pg_partman/setup-pg_partman.sh を実行してください"
        exit 1
    fi

    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    setup_test_table

    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    setup_cron_job

    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    show_initial_state

    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    monitor_cron_execution
    local result=$?

    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    check_job_history

    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    cleanup

    show_summary

    if [ $result -eq 0 ]; then
        log_success "全テスト完了"
    else
        log_warn "一部テストが期待通りに動作しませんでした"
    fi
}

main "$@"
