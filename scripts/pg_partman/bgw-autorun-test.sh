#!/usr/bin/env bash
#
# pg_partman_bgw 自動実行検証スクリプト
#
# 目的:
#   - pg_partman_bgw（Background Worker）の自動メンテナンス実行を確認
#   - 短い間隔（30秒）で自動実行させて動作を確認
#
# 使用方法:
#   ./scripts/pg_partman/bgw-autorun-test.sh
#
# 注意:
#   - PostgreSQLの再起動が必要
#   - テスト後は元の設定に戻すことを推奨
#

set -e

# 設定
CONTAINER_NAME="${POSTGRES_CONTAINER:-postgres-primary}"
DB_USER="${POSTGRES_USER:-idpserver}"
DB_NAME="${POSTGRES_DB:-idpserver}"

# BGW設定（テスト用：30秒間隔）
BGW_INTERVAL=30

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
# 現在のBGW設定確認
# ==============================================================================
check_current_bgw_config() {
    log_step "現在のpg_partman_bgw設定を確認中..."

    echo ""
    echo "--- PostgreSQL設定確認 ---"
    docker exec "$CONTAINER_NAME" psql -U "$DB_USER" -d "$DB_NAME" -c "
        SELECT name, setting, short_desc
        FROM pg_settings
        WHERE name LIKE 'pg_partman_bgw%'
        ORDER BY name;
    "

    # shared_preload_libraries確認
    local preload=$(docker exec "$CONTAINER_NAME" psql -U "$DB_USER" -d "$DB_NAME" -t -c \
        "SHOW shared_preload_libraries;" 2>/dev/null | tr -d ' ')

    echo ""
    log_info "shared_preload_libraries: $preload"

    if [[ "$preload" == *"pg_partman_bgw"* ]]; then
        log_success "pg_partman_bgw は有効です"
        return 0
    else
        log_warn "pg_partman_bgw は現在無効です"
        return 1
    fi
}

# ==============================================================================
# BGW設定を有効化
# ==============================================================================
enable_bgw() {
    log_step "pg_partman_bgw を有効化中..."

    log_info "postgresql.conf を更新..."

    # postgresql.confに設定を追加
    docker exec "$CONTAINER_NAME" bash -c "
        # 既存の設定を削除
        sed -i '/pg_partman_bgw/d' /var/lib/postgresql/data/postgresql.conf
        sed -i '/shared_preload_libraries.*pg_partman/d' /var/lib/postgresql/data/postgresql.conf

        # 新しい設定を追加
        cat >> /var/lib/postgresql/data/postgresql.conf << 'EOF'

# pg_partman Background Worker設定（テスト用）
shared_preload_libraries = 'pg_partman_bgw'
pg_partman_bgw.interval = ${BGW_INTERVAL}
pg_partman_bgw.role = '${DB_USER}'
pg_partman_bgw.dbname = '${DB_NAME}'
pg_partman_bgw.analyze = on
pg_partman_bgw.jobmon = off
EOF
    "

    # 実際の値を置換
    docker exec "$CONTAINER_NAME" bash -c "
        sed -i 's/\${BGW_INTERVAL}/${BGW_INTERVAL}/g' /var/lib/postgresql/data/postgresql.conf
        sed -i 's/\${DB_USER}/${DB_USER}/g' /var/lib/postgresql/data/postgresql.conf
        sed -i 's/\${DB_NAME}/${DB_NAME}/g' /var/lib/postgresql/data/postgresql.conf
    "

    log_success "設定追加完了"

    echo ""
    log_warn "PostgreSQLの再起動が必要です"
    log_info "以下のコマンドで再起動してください:"
    echo ""
    echo "  docker restart $CONTAINER_NAME"
    echo ""
    echo "再起動後、このスクリプトを再実行してください:"
    echo ""
    echo "  $0 --verify"
    echo ""
}

# ==============================================================================
# テスト用テーブル作成
# ==============================================================================
setup_test_table() {
    log_step "BGWテスト用テーブルを作成中..."

    psql_exec "
    -- 既存テーブル削除
    DROP TABLE IF EXISTS bgw_test_table CASCADE;

    -- テストテーブル作成（日別パーティション）
    CREATE TABLE bgw_test_table (
        id UUID DEFAULT gen_random_uuid(),
        tenant_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001'::uuid,
        event_date DATE NOT NULL,
        data TEXT,
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (tenant_id, event_date, id)
    ) PARTITION BY RANGE (event_date);

    COMMENT ON TABLE bgw_test_table IS 'BGW auto-maintenance test table';
    "

    # 既存のpg_partman設定を削除
    psql_exec "
    DELETE FROM partman.part_config WHERE parent_table = 'public.bgw_test_table';
    " 2>/dev/null || true

    # pg_partman設定（premake=2、短い保持期間）
    psql_exec "
    SELECT partman.create_parent(
        p_parent_table => 'public.bgw_test_table',
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
    WHERE parent_table = 'public.bgw_test_table';
    "

    log_success "テストテーブル作成完了"
}

# ==============================================================================
# BGW自動実行を監視
# ==============================================================================
monitor_bgw_execution() {
    log_step "BGW自動実行を監視中（${BGW_INTERVAL}秒間隔で確認）..."

    local initial_count=$(get_partition_count "bgw_test_table")
    log_info "初期パーティション数: ${initial_count}"

    echo ""
    echo "--- 初期状態 ---"
    psql_exec "
    SELECT c.relname as partition
    FROM pg_inherits i
    JOIN pg_class c ON i.inhrelid = c.oid
    JOIN pg_class p ON i.inhparent = p.oid
    WHERE p.relname = 'bgw_test_table'
    ORDER BY c.relname;
    "

    echo ""
    log_info "BGWの自動実行を待機中..."
    log_info "premake=2 なので、新しいパーティションが作成されるはずです"
    log_info "BGW間隔: ${BGW_INTERVAL}秒"
    echo ""

    # 監視ループ（最大3分間）
    local max_wait=180
    local elapsed=0
    local check_interval=10

    while [ $elapsed -lt $max_wait ]; do
        sleep $check_interval
        elapsed=$((elapsed + check_interval))

        local current_count=$(get_partition_count "bgw_test_table")

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
            WHERE p.relname = 'bgw_test_table'
            ORDER BY c.relname;
            "
            return 0
        fi

        printf "\r  経過時間: ${elapsed}秒 / ${max_wait}秒 (パーティション数: ${current_count})"
    done

    echo ""
    log_warn "タイムアウト: BGWによる変更が検出されませんでした"
    log_info "BGWログを確認してください: docker logs $CONTAINER_NAME"
    return 1
}

# ==============================================================================
# BGWログ確認
# ==============================================================================
check_bgw_logs() {
    log_step "BGWログを確認中..."

    echo ""
    echo "--- PostgreSQLログ（pg_partman関連）---"
    docker logs "$CONTAINER_NAME" 2>&1 | grep -i "partman\|bgw\|background worker" | tail -20 || echo "  関連ログなし"

    echo ""
    echo "--- pg_stat_activity（BGWプロセス確認）---"
    psql_exec "
    SELECT pid, usename, application_name, state, query
    FROM pg_stat_activity
    WHERE application_name LIKE '%partman%'
       OR query LIKE '%partman%'
    ORDER BY pid;
    "
}

# ==============================================================================
# クリーンアップ
# ==============================================================================
cleanup() {
    log_step "テストテーブルをクリーンアップ中..."

    psql_exec "
    DELETE FROM partman.part_config WHERE parent_table = 'public.bgw_test_table';
    DROP TABLE IF EXISTS bgw_test_table CASCADE;
    " 2>/dev/null

    log_success "クリーンアップ完了"
}

# ==============================================================================
# BGW設定を無効化
# ==============================================================================
disable_bgw() {
    log_step "pg_partman_bgw 設定を削除中..."

    docker exec "$CONTAINER_NAME" bash -c "
        sed -i '/pg_partman_bgw/d' /var/lib/postgresql/data/postgresql.conf
        sed -i '/shared_preload_libraries.*pg_partman/d' /var/lib/postgresql/data/postgresql.conf
    "

    log_success "設定削除完了"
    log_warn "変更を反映するにはPostgreSQLの再起動が必要です"
    echo ""
    echo "  docker restart $CONTAINER_NAME"
}

# ==============================================================================
# ヘルプ
# ==============================================================================
show_help() {
    echo "pg_partman_bgw 自動実行検証スクリプト"
    echo ""
    echo "使用方法: $0 [オプション]"
    echo ""
    echo "オプション:"
    echo "  -h, --help      このヘルプを表示"
    echo "  -e, --enable    pg_partman_bgwを有効化（再起動必要）"
    echo "  -v, --verify    BGW動作を検証"
    echo "  -d, --disable   pg_partman_bgwを無効化（再起動必要）"
    echo "  -l, --logs      BGW関連ログを表示"
    echo "  -c, --cleanup   テストテーブルのみクリーンアップ"
    echo "  -s, --status    現在の設定状態を表示"
    echo ""
    echo "典型的なワークフロー:"
    echo "  1. $0 --enable     # BGW有効化"
    echo "  2. docker restart $CONTAINER_NAME"
    echo "  3. $0 --verify     # 自動実行確認"
    echo "  4. $0 --disable    # BGW無効化（オプション）"
    echo ""
    echo "環境変数:"
    echo "  POSTGRES_CONTAINER  コンテナ名 (デフォルト: postgres-primary)"
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
        -e|--enable)
            check_container
            enable_bgw
            exit 0
            ;;
        -v|--verify)
            check_container
            if ! check_current_bgw_config; then
                log_error "BGWが有効になっていません"
                log_info "$0 --enable を実行してBGWを有効化してください"
                exit 1
            fi
            setup_test_table
            monitor_bgw_execution
            check_bgw_logs
            cleanup
            exit 0
            ;;
        -d|--disable)
            check_container
            disable_bgw
            exit 0
            ;;
        -l|--logs)
            check_container
            check_bgw_logs
            exit 0
            ;;
        -c|--cleanup)
            check_container
            cleanup
            exit 0
            ;;
        -s|--status)
            check_container
            check_current_bgw_config
            exit 0
            ;;
    esac

    # デフォルト: ステータス表示とガイド
    echo ""
    echo "=============================================="
    echo " pg_partman_bgw 自動実行検証"
    echo "=============================================="
    echo ""

    check_container

    if check_current_bgw_config; then
        echo ""
        log_info "BGWは有効です。検証を実行するには:"
        echo "  $0 --verify"
    else
        echo ""
        log_info "BGWを有効化するには:"
        echo "  1. $0 --enable"
        echo "  2. docker restart $CONTAINER_NAME"
        echo "  3. $0 --verify"
    fi
}

main "$@"
