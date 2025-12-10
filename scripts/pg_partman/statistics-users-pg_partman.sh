#!/usr/bin/env bash
#
# 統計ユーザーテーブルの pg_partman パーティション設定スクリプト
#
# 対象テーブル:
#   - statistics_daily_users   (DAU: 月別パーティション)
#   - statistics_monthly_users (MAU: 年別パーティション)
#   - statistics_yearly_users  (YAU: 年別パーティション)
#
# 使用方法:
#   ./scripts/pg_partman/statistics-users-pg_partman.sh
#
# 前提条件:
#   - pg_partman拡張がインストール済み
#   - setup-pg_partman.sh を先に実行
#

set -e

# 設定
CONTAINER_NAME="${POSTGRES_CONTAINER:-postgres-primary}"
DB_USER="${POSTGRES_USER:-idpserver}"
DB_NAME="${POSTGRES_DB:-idpserver}"

# 保持期間設定
DAILY_RETENTION="${DAILY_RETENTION:-6 months}"
MONTHLY_RETENTION="${MONTHLY_RETENTION:-3 years}"
YEARLY_RETENTION="${YEARLY_RETENTION:-5 years}"

# 事前作成パーティション数
DAILY_PREMAKE="${DAILY_PREMAKE:-3}"      # 3ヶ月先まで
MONTHLY_PREMAKE="${MONTHLY_PREMAKE:-2}"  # 2年先まで
YEARLY_PREMAKE="${YEARLY_PREMAKE:-2}"    # 2年先まで

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
        exit 1
    fi
    log_success "コンテナ '$CONTAINER_NAME' が稼働中"
}

# pg_partman拡張の確認
check_pg_partman() {
    log_info "pg_partman拡張を確認中..."

    local installed=$(psql_exec_quiet "SELECT count(*) FROM pg_extension WHERE extname = 'pg_partman'")

    if [ "$installed" != "1" ]; then
        log_error "pg_partman拡張がインストールされていません"
        log_info "先に setup-pg_partman.sh を実行してください"
        exit 1
    fi

    log_success "pg_partman拡張が有効です"
}

# ==============================================================================
# テーブル作成（pg_partman用）
# ==============================================================================
create_partitioned_tables() {
    log_step "パーティションテーブルを作成中..."

    psql_exec "
    -- 既存テーブルを削除（検証用）
    DROP TABLE IF EXISTS statistics_daily_users_pm CASCADE;
    DROP TABLE IF EXISTS statistics_monthly_users_pm CASCADE;
    DROP TABLE IF EXISTS statistics_yearly_users_pm CASCADE;

    -- =====================================================
    -- statistics_daily_users_pm (月別パーティション)
    -- =====================================================
    CREATE TABLE statistics_daily_users_pm (
        tenant_id UUID NOT NULL,
        stat_date DATE NOT NULL,
        user_id UUID NOT NULL,
        last_used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (tenant_id, stat_date, user_id)
    ) PARTITION BY RANGE (stat_date);

    CREATE INDEX idx_daily_pm_tenant_date ON statistics_daily_users_pm(tenant_id, stat_date);

    COMMENT ON TABLE statistics_daily_users_pm IS 'DAU tracking - pg_partman managed (monthly partitions)';

    -- =====================================================
    -- statistics_monthly_users_pm (年別パーティション)
    -- stat_monthをDATEに変更してRANGEパーティション対応
    -- =====================================================
    CREATE TABLE statistics_monthly_users_pm (
        tenant_id UUID NOT NULL,
        stat_month DATE NOT NULL,  -- CHAR(7)からDATEに変更
        user_id UUID NOT NULL,
        last_used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (tenant_id, stat_month, user_id)
    ) PARTITION BY RANGE (stat_month);

    CREATE INDEX idx_monthly_pm_tenant_month ON statistics_monthly_users_pm(tenant_id, stat_month);

    COMMENT ON TABLE statistics_monthly_users_pm IS 'MAU tracking - pg_partman managed (yearly partitions)';

    -- =====================================================
    -- statistics_yearly_users_pm (年別パーティション)
    -- stat_yearをDATEに変更してRANGEパーティション対応
    -- =====================================================
    CREATE TABLE statistics_yearly_users_pm (
        tenant_id UUID NOT NULL,
        stat_year DATE NOT NULL,  -- CHAR(4)からDATEに変更（年の1月1日を格納）
        user_id UUID NOT NULL,
        last_used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (tenant_id, stat_year, user_id)
    ) PARTITION BY RANGE (stat_year);

    CREATE INDEX idx_yearly_pm_tenant_year ON statistics_yearly_users_pm(tenant_id, stat_year);
    CREATE INDEX idx_yearly_pm_last_used ON statistics_yearly_users_pm(tenant_id, last_used_at);

    COMMENT ON TABLE statistics_yearly_users_pm IS 'YAU tracking - pg_partman managed (yearly partitions)';
    "

    log_success "パーティションテーブル作成完了"
}

# ==============================================================================
# pg_partman設定
# ==============================================================================
setup_pg_partman_config() {
    log_step "pg_partmanパーティション設定中..."

    # statistics_daily_users_pm (月別)
    log_info "statistics_daily_users_pm: 月別パーティション設定"
    psql_exec "
    SELECT partman.create_parent(
        p_parent_table => 'public.statistics_daily_users_pm',
        p_control => 'stat_date',
        p_type => 'range',
        p_interval => '1 month',
        p_premake => ${DAILY_PREMAKE},
        p_start_partition => '2024-01-01'::text
    );
    "

    # statistics_monthly_users_pm (年別)
    log_info "statistics_monthly_users_pm: 年別パーティション設定"
    psql_exec "
    SELECT partman.create_parent(
        p_parent_table => 'public.statistics_monthly_users_pm',
        p_control => 'stat_month',
        p_type => 'range',
        p_interval => '1 year',
        p_premake => ${MONTHLY_PREMAKE},
        p_start_partition => '2024-01-01'::text
    );
    "

    # statistics_yearly_users_pm (年別)
    log_info "statistics_yearly_users_pm: 年別パーティション設定"
    psql_exec "
    SELECT partman.create_parent(
        p_parent_table => 'public.statistics_yearly_users_pm',
        p_control => 'stat_year',
        p_type => 'range',
        p_interval => '1 year',
        p_premake => ${YEARLY_PREMAKE},
        p_start_partition => '2024-01-01'::text
    );
    "

    log_success "pg_partmanパーティション設定完了"
}

# ==============================================================================
# 保持ポリシー設定
# ==============================================================================
setup_retention_policy() {
    log_step "保持ポリシーを設定中..."

    psql_exec "
    -- statistics_daily_users_pm: 6ヶ月保持
    UPDATE partman.part_config
    SET infinite_time_partitions = true,
        retention = '${DAILY_RETENTION}',
        retention_keep_table = false,
        retention_keep_index = false
    WHERE parent_table = 'public.statistics_daily_users_pm';

    -- statistics_monthly_users_pm: 3年保持
    UPDATE partman.part_config
    SET infinite_time_partitions = true,
        retention = '${MONTHLY_RETENTION}',
        retention_keep_table = false,
        retention_keep_index = false
    WHERE parent_table = 'public.statistics_monthly_users_pm';

    -- statistics_yearly_users_pm: 5年保持
    UPDATE partman.part_config
    SET infinite_time_partitions = true,
        retention = '${YEARLY_RETENTION}',
        retention_keep_table = false,
        retention_keep_index = false
    WHERE parent_table = 'public.statistics_yearly_users_pm';
    "

    log_success "保持ポリシー設定完了"
}

# ==============================================================================
# メンテナンスジョブ設定（pg_cron使用時）
# ==============================================================================
setup_maintenance_job() {
    log_step "メンテナンスジョブを確認中..."

    local pg_cron_installed=$(psql_exec_quiet "SELECT count(*) FROM pg_extension WHERE extname = 'pg_cron'")

    if [ "$pg_cron_installed" = "1" ]; then
        log_info "pg_cronでメンテナンスジョブを設定中..."

        psql_exec "
        -- 既存ジョブを削除
        SELECT cron.unschedule(jobid)
        FROM cron.job
        WHERE command LIKE '%partman.run_maintenance_proc%';

        -- 毎日AM2:00にメンテナンス実行
        SELECT cron.schedule(
            'partman-maintenance',
            '0 2 * * *',
            \$\$CALL partman.run_maintenance_proc()\$\$
        );
        "

        log_success "pg_cronメンテナンスジョブ設定完了"
    else
        log_warn "pg_cronが利用できません"
        log_info "外部cronで以下を定期実行してください:"
        echo ""
        echo "  # /etc/cron.d/partman-maintenance"
        echo "  0 2 * * * postgres psql -d ${DB_NAME} -c 'CALL partman.run_maintenance_proc()'"
        echo ""
    fi
}

# ==============================================================================
# 設定確認
# ==============================================================================
show_config() {
    echo ""
    echo "=============================================="
    echo " pg_partman 設定状況"
    echo "=============================================="
    echo ""

    echo "--- part_config ---"
    psql_exec "
    SELECT
        parent_table,
        partition_interval,
        premake,
        retention,
        infinite_time_partitions as infinite
    FROM partman.part_config
    WHERE parent_table LIKE 'public.statistics_%_pm'
    ORDER BY parent_table;
    "

    echo ""
    echo "--- 作成されたパーティション ---"
    psql_exec "
    SELECT
        parent.relname as parent_table,
        child.relname as partition,
        pg_size_pretty(pg_relation_size(child.oid)) as size
    FROM pg_inherits
    JOIN pg_class parent ON pg_inherits.inhparent = parent.oid
    JOIN pg_class child ON pg_inherits.inhrelid = child.oid
    WHERE parent.relname IN (
        'statistics_daily_users_pm',
        'statistics_monthly_users_pm',
        'statistics_yearly_users_pm'
    )
    ORDER BY parent.relname, child.relname;
    "
}

# ==============================================================================
# ヘルプ
# ==============================================================================
show_help() {
    echo "統計ユーザーテーブル pg_partman 設定スクリプト"
    echo ""
    echo "使用方法: $0 [オプション]"
    echo ""
    echo "オプション:"
    echo "  -h, --help     このヘルプを表示"
    echo "  -s, --status   現在の設定状況を表示"
    echo ""
    echo "環境変数:"
    echo "  POSTGRES_CONTAINER  コンテナ名 (デフォルト: postgres-primary)"
    echo "  DAILY_RETENTION     DAU保持期間 (デフォルト: 6 months)"
    echo "  MONTHLY_RETENTION   MAU保持期間 (デフォルト: 3 years)"
    echo "  YEARLY_RETENTION    YAU保持期間 (デフォルト: 5 years)"
    echo "  DAILY_PREMAKE       DAU事前作成数 (デフォルト: 3)"
    echo "  MONTHLY_PREMAKE     MAU事前作成数 (デフォルト: 2)"
    echo "  YEARLY_PREMAKE      YAU事前作成数 (デフォルト: 2)"
    echo ""
    echo "例:"
    echo "  $0                                  # デフォルト設定で実行"
    echo "  DAILY_RETENTION='1 year' $0        # DAU保持期間を1年に変更"
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
            check_pg_partman
            show_config
            exit 0
            ;;
    esac

    echo ""
    echo "=============================================="
    echo " 統計ユーザーテーブル pg_partman 設定"
    echo "=============================================="
    echo " コンテナ: ${CONTAINER_NAME}"
    echo " データベース: ${DB_NAME}"
    echo ""
    echo " 保持期間:"
    echo "   DAU: ${DAILY_RETENTION}"
    echo "   MAU: ${MONTHLY_RETENTION}"
    echo "   YAU: ${YEARLY_RETENTION}"
    echo "=============================================="
    echo ""

    check_container
    check_pg_partman

    create_partitioned_tables
    setup_pg_partman_config
    setup_retention_policy
    setup_maintenance_job

    show_config

    echo ""
    log_success "設定完了"
    echo ""
    echo "次のステップ:"
    echo "  ./scripts/pg_partman/verify-pg_partman.sh"
}

main "$@"
