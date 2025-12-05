#!/usr/bin/env bash
#
# PostgreSQL 統計ユーザーテーブル パーティションライフサイクルシミュレーション
# 対象: statistics_daily_users, statistics_monthly_users, statistics_yearly_users
#
# 実運用を想定した以下のサイクルをシミュレート:
# 1. 新規パーティション作成（月初のcron job想定）
# 2. データ挿入（日次のアプリケーション動作想定）
# 3. 古いパーティション削除（月次のcron job想定）
#
# 使用方法:
#   ./scripts/partition/statistics-users-lifecycle.sh [サイクル数]
#
# 例:
#   ./scripts/partition/statistics-users-lifecycle.sh       # デフォルト: 12サイクル（1年分）
#   ./scripts/partition/statistics-users-lifecycle.sh 24    # 24サイクル（2年分）
#

set -e

# 設定
CONTAINER_NAME="${POSTGRES_CONTAINER:-postgres-primary}"
DB_USER="${POSTGRES_USER:-idpserver}"
DB_NAME="${POSTGRES_DB:-idpserver}"
CYCLE_COUNT="${1:-12}"
ROWS_PER_DAY="${ROWS_PER_DAY:-10000}"        # 1日あたりの挿入行数
RETENTION_MONTHS="${RETENTION_MONTHS:-6}"     # 保持期間（月）

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
log_cron() { echo -e "${CYAN}[CRON]${NC} $1"; }

# PostgreSQLコマンド実行
psql_exec() {
    docker exec "$CONTAINER_NAME" psql -U "$DB_USER" -d "$DB_NAME" -c "$1"
}

psql_exec_quiet() {
    docker exec "$CONTAINER_NAME" psql -U "$DB_USER" -d "$DB_NAME" -t -c "$1" 2>/dev/null | tr -d ' '
}

# エラーを返すPostgreSQLコマンド実行（エラーメッセージを取得）
psql_exec_with_error() {
    local result
    local exit_code
    result=$(docker exec "$CONTAINER_NAME" psql -U "$DB_USER" -d "$DB_NAME" -c "$1" 2>&1)
    exit_code=$?
    echo "$result"
    return $exit_code
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

# ==============================================================================
# 初期セットアップ
# ==============================================================================
setup_tables() {
    log_info "パーティションテーブルを初期化中..."

    psql_exec "
    -- 既存テーブルを削除
    DROP TABLE IF EXISTS statistics_daily_users_sim CASCADE;
    DROP TABLE IF EXISTS statistics_monthly_users_sim CASCADE;
    DROP TABLE IF EXISTS statistics_yearly_users_sim CASCADE;

    -- =====================================================
    -- statistics_daily_users_sim（月別パーティション）
    -- =====================================================
    CREATE TABLE statistics_daily_users_sim (
        tenant_id UUID NOT NULL,
        stat_date DATE NOT NULL,
        user_id UUID NOT NULL,
        last_used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (tenant_id, stat_date, user_id)
    ) PARTITION BY RANGE (stat_date);

    -- DEFAULTパーティション: 対応するパーティションがない場合の安全ネット
    CREATE TABLE statistics_daily_users_sim_default PARTITION OF statistics_daily_users_sim DEFAULT;

    CREATE INDEX idx_daily_sim_tenant_date ON statistics_daily_users_sim(tenant_id, stat_date);

    -- =====================================================
    -- statistics_monthly_users_sim（年別パーティション）
    -- =====================================================
    CREATE TABLE statistics_monthly_users_sim (
        tenant_id UUID NOT NULL,
        stat_month CHAR(7) NOT NULL,
        user_id UUID NOT NULL,
        last_used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (tenant_id, stat_month, user_id)
    ) PARTITION BY RANGE (stat_month);

    -- DEFAULTパーティション
    CREATE TABLE statistics_monthly_users_sim_default PARTITION OF statistics_monthly_users_sim DEFAULT;

    CREATE INDEX idx_monthly_sim_tenant_month ON statistics_monthly_users_sim(tenant_id, stat_month);

    -- =====================================================
    -- statistics_yearly_users_sim（年別パーティション）
    -- =====================================================
    CREATE TABLE statistics_yearly_users_sim (
        tenant_id UUID NOT NULL,
        stat_year CHAR(4) NOT NULL,
        user_id UUID NOT NULL,
        last_used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (tenant_id, stat_year, user_id)
    ) PARTITION BY LIST (stat_year);

    -- DEFAULTパーティション
    CREATE TABLE statistics_yearly_users_sim_default PARTITION OF statistics_yearly_users_sim DEFAULT;

    CREATE INDEX idx_yearly_sim_tenant_year ON statistics_yearly_users_sim(tenant_id, stat_year);
    CREATE INDEX idx_yearly_sim_last_used ON statistics_yearly_users_sim(tenant_id, last_used_at);
    "

    log_success "パーティションテーブル初期化完了（DEFAULTパーティション付き）"
}

# ==============================================================================
# パーティション作成（月初のcron job想定）
# ==============================================================================
create_daily_partition() {
    local year_month=$1  # YYYY-MM形式
    local year=${year_month:0:4}
    local month=${year_month:5:2}
    local partition_name="statistics_daily_users_sim_${year}_${month}"

    # 月の開始日と終了日を計算（PostgreSQLで計算して互換性問題を回避）
    local start_date="${year}-${month}-01"
    local next_month=$(psql_exec_quiet "SELECT to_char('${start_date}'::date + interval '1 month', 'YYYY-MM-DD')")

    # パーティションが存在するかチェック
    local exists=$(psql_exec_quiet "SELECT 1 FROM pg_tables WHERE tablename = '${partition_name}'")

    if [ "$exists" = "1" ]; then
        log_warn "パーティション ${partition_name} は既に存在します"
        return 0
    fi

    psql_exec "
    CREATE TABLE ${partition_name} PARTITION OF statistics_daily_users_sim
    FOR VALUES FROM ('${start_date}') TO ('${next_month}');
    " > /dev/null 2>&1

    log_cron "作成: ${partition_name} (${start_date} ~ ${next_month})"
}

create_monthly_partition() {
    local year=$1  # YYYY形式
    local partition_name="statistics_monthly_users_sim_${year}"

    local exists=$(psql_exec_quiet "SELECT 1 FROM pg_tables WHERE tablename = '${partition_name}'")

    if [ "$exists" = "1" ]; then
        log_warn "パーティション ${partition_name} は既に存在します"
        return 0
    fi

    local next_year=$((year + 1))
    psql_exec "
    CREATE TABLE ${partition_name} PARTITION OF statistics_monthly_users_sim
    FOR VALUES FROM ('${year}-01') TO ('${next_year}-01');
    " > /dev/null 2>&1

    log_cron "作成: ${partition_name} (${year}-01 ~ ${next_year}-01)"
}

create_yearly_partition() {
    local year=$1  # YYYY形式
    local partition_name="statistics_yearly_users_sim_${year}"

    local exists=$(psql_exec_quiet "SELECT 1 FROM pg_tables WHERE tablename = '${partition_name}'")

    if [ "$exists" = "1" ]; then
        log_warn "パーティション ${partition_name} は既に存在します"
        return 0
    fi

    psql_exec "
    CREATE TABLE ${partition_name} PARTITION OF statistics_yearly_users_sim
    FOR VALUES IN ('${year}');
    " > /dev/null 2>&1

    log_cron "作成: ${partition_name}"
}

# ==============================================================================
# データ挿入（日次のアプリケーション動作想定）
# ==============================================================================
insert_daily_data_for_month() {
    local year_month=$1  # YYYY-MM形式
    local result
    local exit_code

    # 月の全日数分のデータを一括挿入（PostgreSQLで日数を計算）
    result=$(psql_exec_with_error "
    INSERT INTO statistics_daily_users_sim (tenant_id, stat_date, user_id, last_used_at, created_at)
    SELECT
        ('00000000-0000-0000-0000-00000000000' || (s % 10))::uuid,
        d.day_date,
        gen_random_uuid(),
        d.day_date::timestamp + (random() * 24 * 60 * 60) * interval '1 second',
        NOW()
    FROM (
        SELECT generate_series(
            '${year_month}-01'::date,
            ('${year_month}-01'::date + interval '1 month' - interval '1 day')::date,
            '1 day'::interval
        )::date as day_date
    ) d
    CROSS JOIN generate_series(1, ${ROWS_PER_DAY} / 30) s;
    ")
    exit_code=$?

    if [ $exit_code -ne 0 ] || echo "$result" | grep -qi "error"; then
        log_error "daily_users挿入失敗 (${year_month})"
        echo "$result" | grep -i "error" | head -3
        return 1
    fi

    # DEFAULTパーティションに入ったかチェック（パーティション作成スキップ時）
    if [ "${SKIP_PARTITION_CREATION:-0}" -eq 1 ]; then
        local default_count=$(psql_exec_quiet "SELECT count(*) FROM statistics_daily_users_sim_default WHERE stat_date >= '${year_month}-01' AND stat_date < '${year_month}-01'::date + interval '1 month'")
        if [ "${default_count:-0}" -gt 0 ]; then
            log_warn "daily_users: ${default_count}行がDEFAULTパーティションに格納 (${year_month})"
            return 2  # 警告（エラーではないが注意が必要）
        fi
    fi

    return 0
}

insert_monthly_data() {
    local year_month=$1  # YYYY-MM形式
    local year=${year_month:0:4}
    local result
    local exit_code

    result=$(psql_exec_with_error "
    INSERT INTO statistics_monthly_users_sim (tenant_id, stat_month, user_id, last_used_at, created_at)
    SELECT
        ('00000000-0000-0000-0000-00000000000' || (s % 10))::uuid,
        '${year_month}',
        gen_random_uuid(),
        NOW(),
        NOW()
    FROM generate_series(1, ${ROWS_PER_DAY}) s;
    ")
    exit_code=$?

    if [ $exit_code -ne 0 ] || echo "$result" | grep -qi "error"; then
        log_error "monthly_users挿入失敗 (${year_month})"
        echo "$result" | grep -i "error" | head -3
        return 1
    fi

    # DEFAULTパーティションに入ったかチェック
    if [ "${SKIP_PARTITION_CREATION:-0}" -eq 1 ]; then
        local default_count=$(psql_exec_quiet "SELECT count(*) FROM statistics_monthly_users_sim_default WHERE stat_month = '${year_month}'")
        if [ "${default_count:-0}" -gt 0 ]; then
            log_warn "monthly_users: ${default_count}行がDEFAULTパーティションに格納 (${year_month})"
            return 2
        fi
    fi

    return 0
}

insert_yearly_data() {
    local year=$1  # YYYY形式
    local result
    local exit_code

    result=$(psql_exec_with_error "
    INSERT INTO statistics_yearly_users_sim (tenant_id, stat_year, user_id, last_used_at, created_at)
    SELECT
        ('00000000-0000-0000-0000-00000000000' || (s % 10))::uuid,
        '${year}',
        gen_random_uuid(),
        NOW(),
        NOW()
    FROM generate_series(1, ${ROWS_PER_DAY}) s;
    ")
    exit_code=$?

    if [ $exit_code -ne 0 ] || echo "$result" | grep -qi "error"; then
        log_error "yearly_users挿入失敗 (${year})"
        echo "$result" | grep -i "error" | head -3
        return 1
    fi

    # DEFAULTパーティションに入ったかチェック
    if [ "${SKIP_PARTITION_CREATION:-0}" -eq 1 ]; then
        local default_count=$(psql_exec_quiet "SELECT count(*) FROM statistics_yearly_users_sim_default WHERE stat_year = '${year}'")
        if [ "${default_count:-0}" -gt 0 ]; then
            log_warn "yearly_users: ${default_count}行がDEFAULTパーティションに格納 (${year})"
            return 2
        fi
    fi

    return 0
}

# ==============================================================================
# パーティション削除（月次のcron job想定）
# ==============================================================================
drop_daily_partition() {
    local year_month=$1  # YYYY-MM形式
    local year=${year_month:0:4}
    local month=${year_month:5:2}
    local partition_name="statistics_daily_users_sim_${year}_${month}"

    local exists=$(psql_exec_quiet "SELECT 1 FROM pg_tables WHERE tablename = '${partition_name}'")

    if [ "$exists" != "1" ]; then
        return 0
    fi

    local row_count=$(psql_exec_quiet "SELECT count(*) FROM ${partition_name}")

    psql_exec "DROP TABLE ${partition_name};" > /dev/null 2>&1

    log_cron "削除: ${partition_name} (${row_count}行)"
}

drop_monthly_partition() {
    local year=$1
    local partition_name="statistics_monthly_users_sim_${year}"

    local exists=$(psql_exec_quiet "SELECT 1 FROM pg_tables WHERE tablename = '${partition_name}'")

    if [ "$exists" != "1" ]; then
        return 0
    fi

    local row_count=$(psql_exec_quiet "SELECT count(*) FROM ${partition_name}")

    psql_exec "DROP TABLE ${partition_name};" > /dev/null 2>&1

    log_cron "削除: ${partition_name} (${row_count}行)"
}

drop_yearly_partition() {
    local year=$1
    local partition_name="statistics_yearly_users_sim_${year}"

    local exists=$(psql_exec_quiet "SELECT 1 FROM pg_tables WHERE tablename = '${partition_name}'")

    if [ "$exists" != "1" ]; then
        return 0
    fi

    local row_count=$(psql_exec_quiet "SELECT count(*) FROM ${partition_name}")

    psql_exec "DROP TABLE ${partition_name};" > /dev/null 2>&1

    log_cron "削除: ${partition_name} (${row_count}行)"
}

# ==============================================================================
# 状態表示
# ==============================================================================
show_partition_status() {
    echo ""
    echo "--- パーティション状態 ---"
    psql_exec "
    SELECT
        parent.relname as parent_table,
        child.relname as partition,
        pg_size_pretty(pg_relation_size(child.oid)) as size,
        (SELECT count(*) FROM pg_class WHERE relname = child.relname) as exists
    FROM pg_inherits
    JOIN pg_class parent ON pg_inherits.inhparent = parent.oid
    JOIN pg_class child ON pg_inherits.inhrelid = child.oid
    WHERE parent.relname IN ('statistics_daily_users_sim', 'statistics_monthly_users_sim', 'statistics_yearly_users_sim')
    ORDER BY parent.relname, child.relname;
    "
}

show_summary() {
    echo ""
    echo "--- サマリー ---"
    psql_exec "
    SELECT
        'daily_users_sim' as table_name,
        count(*) as total_rows,
        pg_size_pretty(pg_total_relation_size('statistics_daily_users_sim')) as total_size
    FROM statistics_daily_users_sim
    UNION ALL
    SELECT
        'monthly_users_sim',
        count(*),
        pg_size_pretty(pg_total_relation_size('statistics_monthly_users_sim'))
    FROM statistics_monthly_users_sim
    UNION ALL
    SELECT
        'yearly_users_sim',
        count(*),
        pg_size_pretty(pg_total_relation_size('statistics_yearly_users_sim'))
    FROM statistics_yearly_users_sim;
    "
}

# DEFAULTパーティションの状態確認
show_default_partition_status() {
    echo ""
    echo "--- DEFAULTパーティション状態 ---"

    local daily_default=$(psql_exec_quiet "SELECT count(*) FROM statistics_daily_users_sim_default")
    local monthly_default=$(psql_exec_quiet "SELECT count(*) FROM statistics_monthly_users_sim_default")
    local yearly_default=$(psql_exec_quiet "SELECT count(*) FROM statistics_yearly_users_sim_default")

    echo "  daily_users_sim_default:   ${daily_default:-0} 行"
    echo "  monthly_users_sim_default: ${monthly_default:-0} 行"
    echo "  yearly_users_sim_default:  ${yearly_default:-0} 行"

    local total_default=$((${daily_default:-0} + ${monthly_default:-0} + ${yearly_default:-0}))

    if [ "$total_default" -gt 0 ]; then
        echo ""
        log_warn "DEFAULTパーティションにデータが存在します（${total_default}行）"
        echo ""
        echo "💡 DEFAULTパーティションのデータは以下の理由で発生:"
        echo "   - 対応する期間のパーティションが作成されていない"
        echo "   - cronジョブが失敗してパーティション作成が漏れた"
        echo ""
        echo "🔧 対処方法:"
        echo "   1. 適切なパーティションを作成"
        echo "   2. DEFAULTパーティションからデータを移動:"
        echo "      -- 例: 2024-01のデータを移動"
        echo "      INSERT INTO statistics_daily_users_sim_2024_01"
        echo "      SELECT * FROM statistics_daily_users_sim_default"
        echo "      WHERE stat_date >= '2024-01-01' AND stat_date < '2024-02-01';"
        echo "      DELETE FROM statistics_daily_users_sim_default"
        echo "      WHERE stat_date >= '2024-01-01' AND stat_date < '2024-02-01';"

        # DEFAULTにあるデータの日付範囲を表示
        if [ "${daily_default:-0}" -gt 0 ]; then
            echo ""
            echo "📊 daily_users_sim_default の日付範囲:"
            psql_exec "SELECT min(stat_date) as min_date, max(stat_date) as max_date FROM statistics_daily_users_sim_default;"
        fi
    else
        log_success "DEFAULTパーティションにデータなし（正常）"
    fi
}

# ==============================================================================
# ライフサイクルシミュレーション
# ==============================================================================

# グローバルエラーカウンター
TOTAL_ERRORS=0

simulate_lifecycle() {
    local start_year=2024
    local start_month=1
    local skip_partition_creation=${SKIP_PARTITION_CREATION:-0}

    echo ""
    echo "=============================================="
    echo " パーティションライフサイクルシミュレーション"
    echo "=============================================="
    echo " サイクル数: ${CYCLE_COUNT}ヶ月"
    echo " 1日あたり挿入行数: ${ROWS_PER_DAY}"
    echo " 保持期間: ${RETENTION_MONTHS}ヶ月"
    if [ "$skip_partition_creation" -eq 1 ]; then
        echo -e " ${RED}⚠️  パーティション作成スキップモード（エラーテスト）${NC}"
    fi
    echo "=============================================="
    echo ""

    for ((cycle=0; cycle<CYCLE_COUNT; cycle++)); do
        # 現在の年月を計算
        local total_months=$((start_year * 12 + start_month - 1 + cycle))
        local current_year=$((total_months / 12))
        local current_month=$((total_months % 12 + 1))
        local current_ym=$(printf "%04d-%02d" $current_year $current_month)

        echo ""
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo " サイクル $((cycle + 1))/${CYCLE_COUNT}: ${current_ym}"
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

        # ========================================
        # 1. 月初: 新規パーティション作成
        # ========================================
        echo ""
        if [ "$skip_partition_creation" -eq 1 ]; then
            echo "📅 月初cron: 新規パーティション作成 ${RED}[スキップ]${NC}"
            log_warn "パーティション作成をスキップ（エラーテストモード）"
        else
            echo "📅 月初cron: 新規パーティション作成"

            # daily: 当月分のパーティション作成
            create_daily_partition "${current_ym}"

            # monthly: 年が変わったら新年分のパーティション作成
            if [ "$current_month" -eq 1 ]; then
                create_monthly_partition "${current_year}"
            fi

            # yearly: 年が変わったら新年分のパーティション作成
            if [ "$current_month" -eq 1 ]; then
                create_yearly_partition "${current_year}"
            fi
        fi

        # ========================================
        # 2. 月中: データ挿入（月単位で一括挿入）
        # ========================================
        echo ""
        echo "📊 アプリケーション: データ挿入中..."

        local insert_start=$(date +%s.%N)
        local daily_status=0
        local monthly_status=0
        local yearly_status=0

        # daily_users: 当月の全日分を一括挿入
        insert_daily_data_for_month "${current_ym}"
        daily_status=$?
        if [ $daily_status -eq 1 ]; then
            ((TOTAL_ERRORS++)) || true
        fi

        # monthly_users: 月別データ（月1回まとめて）
        insert_monthly_data "${current_ym}"
        monthly_status=$?
        if [ $monthly_status -eq 1 ]; then
            ((TOTAL_ERRORS++)) || true
        fi

        # yearly_users: 年別データ（月1回まとめて）
        insert_yearly_data "${current_year}"
        yearly_status=$?
        if [ $yearly_status -eq 1 ]; then
            ((TOTAL_ERRORS++)) || true
        fi

        local insert_end=$(date +%s.%N)
        local insert_duration=$(echo "$insert_end - $insert_start" | bc)

        # 挿入された行数を確認
        local daily_count=$(psql_exec_quiet "SELECT count(*) FROM statistics_daily_users_sim WHERE stat_date >= '${current_ym}-01' AND stat_date < '${current_ym}-01'::date + interval '1 month'")

        # status: 0=成功, 1=エラー, 2=警告（DEFAULTに格納）
        if [ $daily_status -eq 0 ] && [ $monthly_status -eq 0 ] && [ $yearly_status -eq 0 ]; then
            log_success "挿入完了: daily=${daily_count}行, monthly=1月分, yearly=1月分 (${insert_duration}秒)"
        elif [ $daily_status -eq 1 ] || [ $monthly_status -eq 1 ] || [ $yearly_status -eq 1 ]; then
            log_error "挿入失敗あり: daily=${daily_count}行 (${insert_duration}秒)"
        else
            log_warn "挿入完了（DEFAULTパーティション使用）: daily=${daily_count}行 (${insert_duration}秒)"
        fi

        # ========================================
        # 3. 月末: 古いパーティション削除
        # ========================================
        echo ""
        echo "🗑️  月末cron: 古いパーティション削除 (保持期間: ${RETENTION_MONTHS}ヶ月)"

        # 削除対象の年月を計算
        local delete_months=$((total_months - RETENTION_MONTHS))
        if [ $delete_months -ge 0 ]; then
            local delete_year=$((delete_months / 12))
            local delete_month=$((delete_months % 12 + 1))
            local delete_ym=$(printf "%04d-%02d" $delete_year $delete_month)

            # daily: 保持期間を超えたパーティションを削除
            drop_daily_partition "${delete_ym}"

            # monthly: 保持年を超えた年のパーティションを削除（年単位）
            local delete_year_for_monthly=$((current_year - 2))  # 2年前
            if [ $delete_year_for_monthly -ge 2020 ]; then
                drop_monthly_partition "${delete_year_for_monthly}"
            fi

            # yearly: 保持年を超えた年のパーティションを削除（3年保持）
            local delete_year_for_yearly=$((current_year - 3))  # 3年前
            if [ $delete_year_for_yearly -ge 2020 ]; then
                drop_yearly_partition "${delete_year_for_yearly}"
            fi
        fi

        # ========================================
        # 4. 状態確認（3ヶ月ごと）
        # ========================================
        if [ $(((cycle + 1) % 3)) -eq 0 ]; then
            show_partition_status
        fi
    done
}

# ==============================================================================
# クリーンアップ
# ==============================================================================
cleanup() {
    log_info "シミュレーション用テーブルを削除中..."
    psql_exec "
    DROP TABLE IF EXISTS statistics_daily_users_sim CASCADE;
    DROP TABLE IF EXISTS statistics_monthly_users_sim CASCADE;
    DROP TABLE IF EXISTS statistics_yearly_users_sim CASCADE;
    " > /dev/null 2>&1
    log_success "クリーンアップ完了"
}

# ==============================================================================
# 実運用向けcronスクリプト生成
# ==============================================================================
generate_cron_scripts() {
    echo ""
    echo "=============================================="
    echo " 実運用向けcronスクリプト例"
    echo "=============================================="

    cat << 'CRON_SCRIPT'

# ===========================================
# /etc/cron.d/partition-maintenance
# ===========================================

# 毎月1日 AM 2:00: 新規パーティション作成
0 2 1 * * postgres /opt/scripts/create-partitions.sh >> /var/log/partition-maintenance.log 2>&1

# 毎月1日 AM 3:00: 古いパーティション削除
0 3 1 * * postgres /opt/scripts/drop-old-partitions.sh >> /var/log/partition-maintenance.log 2>&1

# ===========================================
# /opt/scripts/create-partitions.sh
# ===========================================
#!/bin/bash
set -e

PGPASSWORD="${DB_PASSWORD}" psql -h localhost -U idpserver -d idpserver << 'SQL'
DO $$
DECLARE
    next_month DATE := date_trunc('month', CURRENT_DATE + interval '1 month');
    partition_name TEXT;
BEGIN
    -- daily_users: 来月分のパーティション作成
    partition_name := 'statistics_daily_users_' || to_char(next_month, 'YYYY_MM');
    IF NOT EXISTS (SELECT 1 FROM pg_tables WHERE tablename = partition_name) THEN
        EXECUTE format(
            'CREATE TABLE %I PARTITION OF statistics_daily_users FOR VALUES FROM (%L) TO (%L)',
            partition_name,
            next_month,
            next_month + interval '1 month'
        );
        RAISE NOTICE 'Created partition: %', partition_name;
    END IF;

    -- monthly_users: 来年分のパーティション作成（12月に実行）
    IF EXTRACT(MONTH FROM CURRENT_DATE) = 12 THEN
        partition_name := 'statistics_monthly_users_' || to_char(next_month, 'YYYY');
        IF NOT EXISTS (SELECT 1 FROM pg_tables WHERE tablename = partition_name) THEN
            EXECUTE format(
                'CREATE TABLE %I PARTITION OF statistics_monthly_users FOR VALUES FROM (%L) TO (%L)',
                partition_name,
                to_char(next_month, 'YYYY') || '-01',
                to_char(next_month + interval '1 year', 'YYYY') || '-01'
            );
            RAISE NOTICE 'Created partition: %', partition_name;
        END IF;
    END IF;

    -- yearly_users: 来年分のパーティション作成（12月に実行）
    IF EXTRACT(MONTH FROM CURRENT_DATE) = 12 THEN
        partition_name := 'statistics_yearly_users_' || to_char(next_month, 'YYYY');
        IF NOT EXISTS (SELECT 1 FROM pg_tables WHERE tablename = partition_name) THEN
            EXECUTE format(
                'CREATE TABLE %I PARTITION OF statistics_yearly_users FOR VALUES IN (%L)',
                partition_name,
                to_char(next_month, 'YYYY')
            );
            RAISE NOTICE 'Created partition: %', partition_name;
        END IF;
    END IF;
END $$;
SQL

# ===========================================
# /opt/scripts/drop-old-partitions.sh
# ===========================================
#!/bin/bash
set -e

RETENTION_MONTHS=6  # 保持期間

PGPASSWORD="${DB_PASSWORD}" psql -h localhost -U idpserver -d idpserver << SQL
DO \$\$
DECLARE
    cutoff_date DATE := date_trunc('month', CURRENT_DATE - interval '${RETENTION_MONTHS} months');
    partition_name TEXT;
    r RECORD;
BEGIN
    -- daily_users: 保持期間を超えたパーティションを削除
    FOR r IN
        SELECT child.relname
        FROM pg_inherits
        JOIN pg_class parent ON pg_inherits.inhparent = parent.oid
        JOIN pg_class child ON pg_inherits.inhrelid = child.oid
        WHERE parent.relname = 'statistics_daily_users'
        AND child.relname < 'statistics_daily_users_' || to_char(cutoff_date, 'YYYY_MM')
    LOOP
        EXECUTE format('DROP TABLE %I', r.relname);
        RAISE NOTICE 'Dropped partition: %', r.relname;
    END LOOP;
END \$\$;
SQL

CRON_SCRIPT

    echo ""
    log_info "上記のスクリプトを参考に実運用環境を構築してください"
}

# ==============================================================================
# ヘルプ
# ==============================================================================
show_help() {
    echo "PostgreSQL パーティションライフサイクルシミュレーション"
    echo ""
    echo "使用方法: $0 [オプション] [サイクル数]"
    echo ""
    echo "オプション:"
    echo "  -h, --help       このヘルプを表示"
    echo "  -c, --cleanup    シミュレーション用テーブルを削除"
    echo "  -g, --generate   実運用向けcronスクリプトを生成"
    echo "  -e, --error-test パーティション未作成エラーテスト"
    echo ""
    echo "環境変数:"
    echo "  POSTGRES_CONTAINER       コンテナ名 (デフォルト: postgres-primary)"
    echo "  ROWS_PER_DAY             1日あたりの挿入行数 (デフォルト: 10000)"
    echo "  RETENTION_MONTHS         保持期間（月） (デフォルト: 6)"
    echo "  SKIP_PARTITION_CREATION  1に設定するとパーティション作成をスキップ"
    echo ""
    echo "例:"
    echo "  $0                           # 12サイクル（1年分）シミュレーション"
    echo "  $0 24                        # 24サイクル（2年分）シミュレーション"
    echo "  ROWS_PER_DAY=1000 $0 6       # 少ないデータで6サイクル"
    echo "  $0 --generate                # cronスクリプト例を表示"
    echo "  $0 --error-test 3            # パーティション未作成エラーを3サイクルでテスト"
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
        -g|--generate)
            generate_cron_scripts
            exit 0
            ;;
        -e|--error-test)
            # パーティション未作成エラーテストモード
            export SKIP_PARTITION_CREATION=1
            CYCLE_COUNT="${2:-3}"
            ROWS_PER_DAY="${ROWS_PER_DAY:-1000}"
            shift
            ;;
    esac

    check_container
    setup_tables
    simulate_lifecycle

    echo ""
    echo "=============================================="
    echo " 最終状態"
    echo "=============================================="
    show_partition_status
    show_summary
    show_default_partition_status

    # エラーサマリー表示
    echo ""
    echo "=============================================="
    echo " エラーサマリー"
    echo "=============================================="
    if [ "$TOTAL_ERRORS" -eq 0 ]; then
        log_success "エラーなし - すべての操作が正常に完了しました"
    else
        log_error "合計 ${TOTAL_ERRORS} 件のエラーが発生しました"
        echo ""
        echo "💡 DEFAULTパーティションがあるためエラーは発生しませんでしたが、"
        echo "   データがDEFAULTパーティションに格納されています。"
        echo "   上記の「DEFAULTパーティション状態」を確認してください。"
    fi

    echo ""
    if [ "$TOTAL_ERRORS" -eq 0 ]; then
        log_success "シミュレーション完了"
    else
        log_warn "シミュレーション完了（エラーあり）"
    fi
    echo ""
    echo "クリーンアップ: $0 --cleanup"
    echo "cronスクリプト例: $0 --generate"
    echo "エラーテスト: $0 --error-test 3"
}

main "$@"
