#!/usr/bin/env bash
#
# pg_partman 拡張のセットアップスクリプト
#
# 使用方法:
#   ./scripts/pg_partman/setup-pg_partman.sh
#
# 環境変数:
#   POSTGRES_CONTAINER  コンテナ名 (デフォルト: postgres-primary)
#   POSTGRES_USER       ユーザー名 (デフォルト: idpserver)
#   POSTGRES_DB         データベース名 (デフォルト: idpserver)
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
    log_info "PostgreSQLコンテナを確認中..."
    if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
        log_error "コンテナ '$CONTAINER_NAME' が見つかりません"
        log_info "docker-compose up -d でPostgreSQLを起動してください"
        exit 1
    fi
    log_success "コンテナ '$CONTAINER_NAME' が稼働中"
}

# PostgreSQLバージョン確認
check_postgres_version() {
    log_info "PostgreSQLバージョンを確認中..."

    local version=$(psql_exec_quiet "SHOW server_version_num")
    local major_version=$((version / 10000))
    local minor_version=$(((version % 10000) / 100))

    log_info "PostgreSQL バージョン: ${major_version}.${minor_version}"

    if [ "$major_version" -lt 12 ] || ([ "$major_version" -eq 12 ] && [ "$minor_version" -lt 5 ]); then
        log_error "pg_partman には PostgreSQL 12.5 以降が必要です"
        exit 1
    fi

    log_success "バージョン要件を満たしています"
}

# pg_partman拡張の可用性確認
check_pg_partman_availability() {
    log_info "pg_partman拡張の可用性を確認中..."

    local available=$(psql_exec_quiet "SELECT count(*) FROM pg_available_extensions WHERE name = 'pg_partman'")

    if [ "$available" = "0" ]; then
        log_warn "pg_partman拡張が利用できません"
        log_info ""
        log_info "=== pg_partman のインストール方法 ==="
        log_info ""
        log_info "【Docker環境の場合】"
        log_info "  1. pg_partman同梱のイメージを使用:"
        log_info "     docker pull pgpartman/pg_partman"
        log_info ""
        log_info "  2. または、コンテナ内でビルド:"
        log_info "     docker exec -it $CONTAINER_NAME bash"
        log_info "     apt-get update && apt-get install -y git build-essential postgresql-server-dev-all"
        log_info "     git clone https://github.com/pgpartman/pg_partman.git"
        log_info "     cd pg_partman && make && make install"
        log_info ""
        log_info "【AWS RDS環境の場合】"
        log_info "  pg_partman はプリインストール済みです"
        log_info "  rds_superuser ロールで CREATE EXTENSION を実行してください"
        log_info ""

        return 1
    fi

    log_success "pg_partman拡張が利用可能です"
    return 0
}

# pg_partmanスキーマとextension作成
setup_pg_partman() {
    log_info "pg_partman拡張をセットアップ中..."

    # 既存の拡張を確認
    local installed=$(psql_exec_quiet "SELECT count(*) FROM pg_extension WHERE extname = 'pg_partman'")

    if [ "$installed" = "1" ]; then
        log_warn "pg_partman拡張は既にインストール済みです"
        return 0
    fi

    # スキーマ作成
    psql_exec "CREATE SCHEMA IF NOT EXISTS partman;"

    # 拡張インストール
    psql_exec "CREATE EXTENSION pg_partman WITH SCHEMA partman;"

    log_success "pg_partman拡張のセットアップ完了"
}

# pg_cronの確認（オプション）
check_pg_cron() {
    log_info "pg_cron拡張を確認中..."

    local available=$(psql_exec_quiet "SELECT count(*) FROM pg_available_extensions WHERE name = 'pg_cron'")

    if [ "$available" = "0" ]; then
        log_warn "pg_cron拡張が利用できません"
        log_info "自動メンテナンスには外部cronジョブを使用してください"
        return 1
    fi

    local installed=$(psql_exec_quiet "SELECT count(*) FROM pg_extension WHERE extname = 'pg_cron'")

    if [ "$installed" = "0" ]; then
        log_info "pg_cron拡張をインストール中..."
        psql_exec "CREATE EXTENSION IF NOT EXISTS pg_cron;" 2>/dev/null || {
            log_warn "pg_cronのインストールに失敗しました"
            log_info "shared_preload_libraries に pg_cron を追加してください"
            return 1
        }
    fi

    log_success "pg_cron拡張が利用可能です"
    return 0
}

# セットアップ結果の表示
show_setup_result() {
    echo ""
    echo "=============================================="
    echo " pg_partman セットアップ結果"
    echo "=============================================="
    echo ""

    psql_exec "
    SELECT
        e.extname as extension,
        e.extversion as version,
        n.nspname as schema
    FROM pg_extension e
    JOIN pg_namespace n ON e.extnamespace = n.oid
    WHERE e.extname IN ('pg_partman', 'pg_cron')
    ORDER BY e.extname;
    "

    echo ""
    log_success "セットアップ完了"
    echo ""
    echo "次のステップ:"
    echo "  ./scripts/pg_partman/statistics-users-pg_partman.sh"
}

# シミュレーションモード（pg_partmanなしでの動作確認）
run_simulation_mode() {
    log_warn "シミュレーションモードで実行します"
    log_info "pg_partmanの代わりにネイティブパーティショニングを使用"

    echo ""
    echo "=============================================="
    echo " ネイティブパーティショニング検証"
    echo "=============================================="
    echo ""

    psql_exec "
    -- テスト用パーティションテーブル作成
    DROP TABLE IF EXISTS partman_test CASCADE;

    CREATE TABLE partman_test (
        id UUID NOT NULL,
        tenant_id UUID NOT NULL,
        stat_date DATE NOT NULL,
        user_id UUID NOT NULL,
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (tenant_id, stat_date, user_id)
    ) PARTITION BY RANGE (stat_date);

    -- DEFAULTパーティション
    CREATE TABLE partman_test_default PARTITION OF partman_test DEFAULT;

    -- 月別パーティション作成
    CREATE TABLE partman_test_2024_01 PARTITION OF partman_test
        FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');
    CREATE TABLE partman_test_2024_02 PARTITION OF partman_test
        FOR VALUES FROM ('2024-02-01') TO ('2024-03-01');
    CREATE TABLE partman_test_2024_03 PARTITION OF partman_test
        FOR VALUES FROM ('2024-03-01') TO ('2024-04-01');
    "

    log_success "ネイティブパーティションテーブル作成完了"

    # データ挿入テスト
    log_info "データ挿入テスト..."
    psql_exec "
    INSERT INTO partman_test (id, tenant_id, stat_date, user_id)
    SELECT
        gen_random_uuid(),
        '00000000-0000-0000-0000-000000000001'::uuid,
        '2024-01-15'::date + (s % 60) * interval '1 day',
        gen_random_uuid()
    FROM generate_series(1, 1000) s;
    "

    # パーティション分布確認
    echo ""
    echo "--- パーティション分布 ---"
    psql_exec "
    SELECT
        tableoid::regclass as partition,
        count(*) as rows
    FROM partman_test
    GROUP BY tableoid
    ORDER BY partition;
    "

    # クリーンアップ
    psql_exec "DROP TABLE IF EXISTS partman_test CASCADE;"

    log_success "シミュレーション完了"
    echo ""
    echo "pg_partmanが利用可能になったら、再度このスクリプトを実行してください"
}

# ヘルプ表示
show_help() {
    echo "pg_partman セットアップスクリプト"
    echo ""
    echo "使用方法: $0 [オプション]"
    echo ""
    echo "オプション:"
    echo "  -h, --help        このヘルプを表示"
    echo "  -s, --simulation  pg_partmanなしでシミュレーション実行"
    echo "  -c, --check       pg_partmanの可用性のみ確認"
    echo ""
    echo "環境変数:"
    echo "  POSTGRES_CONTAINER  コンテナ名 (デフォルト: postgres-primary)"
    echo "  POSTGRES_USER       ユーザー名 (デフォルト: idpserver)"
    echo "  POSTGRES_DB         データベース名 (デフォルト: idpserver)"
}

# メイン処理
main() {
    case "${1:-}" in
        -h|--help)
            show_help
            exit 0
            ;;
        -c|--check)
            check_container
            check_postgres_version
            check_pg_partman_availability
            check_pg_cron
            exit 0
            ;;
        -s|--simulation)
            check_container
            check_postgres_version
            run_simulation_mode
            exit 0
            ;;
    esac

    echo ""
    echo "=============================================="
    echo " pg_partman セットアップ"
    echo "=============================================="
    echo " コンテナ: ${CONTAINER_NAME}"
    echo " データベース: ${DB_NAME}"
    echo "=============================================="
    echo ""

    check_container
    check_postgres_version

    if check_pg_partman_availability; then
        setup_pg_partman
        check_pg_cron
        show_setup_result
    else
        echo ""
        read -p "シミュレーションモードで続行しますか? (y/n): " answer
        if [ "$answer" = "y" ] || [ "$answer" = "Y" ]; then
            run_simulation_mode
        else
            log_info "セットアップを中止しました"
            exit 1
        fi
    fi
}

main "$@"
