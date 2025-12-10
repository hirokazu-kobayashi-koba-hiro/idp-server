#!/usr/bin/env bash
#
# DEFAULTパーティション問題の検証スクリプト
#
# 目的:
#   - DEFAULTパーティションにデータが蓄積される状況を再現
#   - run_maintenance_proc()実行時のエラーを確認
#   - partition_data_time()による対処法を検証
#   - check_default()による監視方法を確認
#
# 使用方法:
#   ./scripts/pg_partman/default-partition-problem-test.sh
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

# ==============================================================================
# Phase 1: テストテーブル作成（premake=1で意図的に少なく設定）
# ==============================================================================
setup_test_table() {
    log_step "Phase 1: テストテーブル作成（premake=1 で少なく設定）"

    psql_exec "
    -- 既存テーブル削除
    DROP TABLE IF EXISTS default_test_table CASCADE;

    -- テストテーブル作成（日別パーティション）
    CREATE TABLE default_test_table (
        id UUID DEFAULT gen_random_uuid(),
        tenant_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001'::uuid,
        event_date DATE NOT NULL,
        data TEXT,
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (tenant_id, event_date, id)
    ) PARTITION BY RANGE (event_date);

    COMMENT ON TABLE default_test_table IS 'DEFAULT partition problem test table';
    "

    # 既存のpg_partman設定を削除
    psql_exec "
    DELETE FROM partman.part_config WHERE parent_table = 'public.default_test_table';
    " 2>/dev/null || true

    # pg_partman設定（premake=1 で意図的に少なく設定）
    psql_exec "
    SELECT partman.create_parent(
        p_parent_table => 'public.default_test_table',
        p_control => 'event_date',
        p_type => 'range',
        p_interval => '1 day',
        p_premake => 1,
        p_start_partition => CURRENT_DATE::text
    );
    "

    log_success "テストテーブル作成完了（premake=1）"

    echo ""
    echo "--- 初期パーティション状態 ---"
    psql_exec "
    SELECT c.relname as partition
    FROM pg_inherits i
    JOIN pg_class c ON i.inhrelid = c.oid
    JOIN pg_class p ON i.inhparent = p.oid
    WHERE p.relname = 'default_test_table'
    ORDER BY c.relname;
    "
}

# ==============================================================================
# Phase 2: 将来日付のデータを挿入（DEFAULTに格納される）
# ==============================================================================
insert_future_data() {
    log_step "Phase 2: 将来日付のデータを挿入（DEFAULTに格納される）"

    # premake=1なので、今日+1日後のパーティションしかない
    # 今日+5日後のデータはDEFAULTパーティションに格納される
    psql_exec "
    -- 5日後のデータを挿入（パーティションが存在しないためDEFAULTへ）
    INSERT INTO default_test_table (event_date, data)
    VALUES
        (CURRENT_DATE + INTERVAL '5 days', 'future data 1'),
        (CURRENT_DATE + INTERVAL '5 days', 'future data 2'),
        (CURRENT_DATE + INTERVAL '6 days', 'future data 3'),
        (CURRENT_DATE + INTERVAL '7 days', 'future data 4');
    "

    log_success "将来日付のデータを挿入完了"

    echo ""
    echo "--- DEFAULTパーティションの確認 ---"
    psql_exec "
    SELECT event_date, COUNT(*) as count
    FROM default_test_table_default
    GROUP BY event_date
    ORDER BY event_date;
    "

    local default_count=$(psql_exec_quiet "SELECT COUNT(*) FROM default_test_table_default")
    log_warn "DEFAULTパーティションに ${default_count} 件のデータが存在"
}

# ==============================================================================
# Phase 3: check_default()でDEFAULTパーティションを監視
# ==============================================================================
check_default_partition() {
    log_step "Phase 3: check_default()でDEFAULTパーティションを監視"

    echo ""
    echo "--- partman.check_default() の実行結果 ---"
    echo "(全pg_partman管理テーブルのDEFAULTパーティションをチェック)"
    psql_exec "
    SELECT * FROM partman.check_default();
    "

    log_info "check_default()はDEFAULTパーティションにデータがある全テーブルを返します"

    echo ""
    echo "--- DEFAULTパーティションの直接確認 ---"
    psql_exec "
    SELECT
        'default_test_table' as parent_table,
        COUNT(*) as default_count
    FROM default_test_table_default;
    "
}

# ==============================================================================
# Phase 4: premakeを増やしてメンテナンス実行（エラー発生を確認）
# ==============================================================================
demonstrate_error() {
    log_step "Phase 4: premakeを増やしてメンテナンス実行（エラー発生の可能性）"

    # premakeを増やす
    psql_exec "
    UPDATE partman.part_config
    SET premake = 10
    WHERE parent_table = 'public.default_test_table';
    "

    log_info "premakeを1から10に変更しました"
    log_warn "run_maintenance_proc()を実行すると、DEFAULTパーティションのデータと競合する可能性があります"

    echo ""
    echo "--- メンテナンス実行（エラーが発生する可能性）---"

    # エラーを捕捉するためにset +eで一時的にエラー停止を無効化
    set +e
    psql_exec "CALL partman.run_maintenance_proc();" 2>&1
    local result=$?
    set -e

    if [ $result -ne 0 ]; then
        log_error "予想通りエラーが発生しました！"
        log_info "DEFAULTパーティションにデータが存在する状態で新パーティションを作成しようとしたためです"
    else
        log_info "エラーが発生しませんでした（pg_partmanのバージョンによる動作の違いの可能性）"
    fi

    echo ""
    echo "--- 現在のパーティション状態 ---"
    psql_exec "
    SELECT c.relname as partition
    FROM pg_inherits i
    JOIN pg_class c ON i.inhrelid = c.oid
    JOIN pg_class p ON i.inhparent = p.oid
    WHERE p.relname = 'default_test_table'
    ORDER BY c.relname;
    "
}

# ==============================================================================
# Phase 5: partition_data_time()でデータを再配置
# ==============================================================================
fix_with_partition_data_time() {
    log_step "Phase 5: partition_data_time()でデータを再配置"

    echo ""
    echo "--- 再配置前のDEFAULTパーティション ---"
    psql_exec "
    SELECT event_date, COUNT(*) as count
    FROM default_test_table_default
    GROUP BY event_date
    ORDER BY event_date;
    "

    log_info "partition_data_time()でDEFAULTパーティションのデータを適切なパーティションに移動します"

    # partition_data_time()でデータを再配置
    psql_exec "
    SELECT partman.partition_data_time(
        p_parent_table := 'public.default_test_table',
        p_batch_count := 100
    );
    "

    echo ""
    echo "--- 再配置後のDEFAULTパーティション ---"
    local default_count=$(psql_exec_quiet "SELECT COUNT(*) FROM default_test_table_default")

    if [ "$default_count" == "0" ]; then
        log_success "DEFAULTパーティションが空になりました！"
    else
        log_warn "DEFAULTパーティションにまだ ${default_count} 件残っています"
    fi

    echo ""
    echo "--- 再配置後のパーティション状態 ---"
    psql_exec "
    SELECT c.relname as partition
    FROM pg_inherits i
    JOIN pg_class c ON i.inhrelid = c.oid
    JOIN pg_class p ON i.inhparent = p.oid
    WHERE p.relname = 'default_test_table'
    ORDER BY c.relname;
    "

    echo ""
    echo "--- 各パーティションのデータ件数 ---"
    psql_exec "
    SELECT
        (SELECT COUNT(*) FROM default_test_table_default) as default_count,
        (SELECT COUNT(*) FROM default_test_table) as total_count;
    "
}

# ==============================================================================
# Phase 6: 再度メンテナンス実行（正常動作を確認）
# ==============================================================================
verify_maintenance_works() {
    log_step "Phase 6: 再度メンテナンス実行（正常動作を確認）"

    psql_exec "CALL partman.run_maintenance_proc();"

    log_success "メンテナンスが正常に完了しました"

    echo ""
    echo "--- 最終パーティション状態 ---"
    psql_exec "
    SELECT c.relname as partition
    FROM pg_inherits i
    JOIN pg_class c ON i.inhrelid = c.oid
    JOIN pg_class p ON i.inhparent = p.oid
    WHERE p.relname = 'default_test_table'
    ORDER BY c.relname;
    "
}

# ==============================================================================
# クリーンアップ
# ==============================================================================
cleanup() {
    log_step "クリーンアップ"

    psql_exec "
    DELETE FROM partman.part_config WHERE parent_table = 'public.default_test_table';
    DROP TABLE IF EXISTS default_test_table CASCADE;
    " 2>/dev/null

    log_success "クリーンアップ完了"
}

# ==============================================================================
# サマリー
# ==============================================================================
show_summary() {
    echo ""
    echo "=============================================="
    echo " DEFAULTパーティション問題 検証サマリー"
    echo "=============================================="
    echo ""
    echo "検証項目:"
    echo "  1. ✅ DEFAULTパーティションへのデータ蓄積"
    echo "  2. ✅ check_default()による監視"
    echo "  3. ✅ メンテナンス実行時の競合確認"
    echo "  4. ✅ partition_data_time()によるデータ再配置"
    echo "  5. ✅ 再配置後のメンテナンス正常動作"
    echo ""
    echo "重要な教訓:"
    echo ""
    echo "  ⚠️  DEFAULTパーティションにデータが蓄積されると、"
    echo "      新パーティション作成時にエラーが発生する可能性がある"
    echo ""
    echo "  📋 予防策:"
    echo "      - premakeを十分に設定（日別なら7〜14）"
    echo "      - check_default()で定期監視"
    echo "      - アラート設定でDEFAULTパーティションのデータを検知"
    echo ""
    echo "  🔧 対処法:"
    echo "      - partition_data_time()でデータを適切なパーティションに移動"
    echo "      - 大量データの場合はバッチサイズを指定"
    echo "      - ⚠️ 大量データの再配置は長時間ロックが発生する可能性"
    echo ""
}

# ==============================================================================
# ヘルプ
# ==============================================================================
show_help() {
    echo "DEFAULTパーティション問題 検証スクリプト"
    echo ""
    echo "使用方法: $0 [オプション]"
    echo ""
    echo "オプション:"
    echo "  -h, --help      このヘルプを表示"
    echo "  -c, --cleanup   テストリソースのみクリーンアップ"
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
        -c|--cleanup)
            check_container
            cleanup
            exit 0
            ;;
    esac

    echo ""
    echo "=============================================="
    echo " DEFAULTパーティション問題 検証"
    echo "=============================================="
    echo " コンテナ: ${CONTAINER_NAME}"
    echo " データベース: ${DB_NAME}"
    echo ""
    echo " この検証では以下を確認します:"
    echo "   1. DEFAULTパーティションへのデータ蓄積"
    echo "   2. check_default()による監視"
    echo "   3. partition_data_time()による対処"
    echo "=============================================="
    echo ""

    check_container

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
    insert_future_data

    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    check_default_partition

    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    demonstrate_error

    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    fix_with_partition_data_time

    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    verify_maintenance_works

    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    cleanup

    show_summary

    log_success "全検証完了"
}

main "$@"
