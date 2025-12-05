#!/usr/bin/env bash
#
# PostgreSQL パーティションテーブル vs 通常テーブル 性能比較スクリプト
# 対象: statistics_yearly_users, statistics_monthly_users, statistics_daily_users
#
# 使用方法:
#   ./scripts/partition/statistics-users-benchmark.sh [行数]
#
# 例:
#   ./scripts/partition/statistics-users-benchmark.sh          # デフォルト: 100万行
#   ./scripts/partition/statistics-users-benchmark.sh 500000   # 50万行
#   ./scripts/partition/statistics-users-benchmark.sh 5000000  # 500万行
#

set -e

# 設定
CONTAINER_NAME="${POSTGRES_CONTAINER:-postgres-primary}"
DB_USER="${POSTGRES_USER:-idpserver}"
DB_NAME="${POSTGRES_DB:-idpserver}"
ROW_COUNT="${1:-1000000}"

# 色付き出力
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# PostgreSQLコマンド実行
psql_exec() {
    docker exec "$CONTAINER_NAME" psql -U "$DB_USER" -d "$DB_NAME" -c "$1"
}

psql_exec_quiet() {
    docker exec "$CONTAINER_NAME" psql -U "$DB_USER" -d "$DB_NAME" -t -c "$1" 2>/dev/null
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

# ==============================================================================
# DAILY USERS テーブル
# ==============================================================================
create_daily_users_tables() {
    log_info "statistics_daily_users テスト用テーブルを作成中..."

    psql_exec "
    -- 既存テーブルを削除
    DROP TABLE IF EXISTS statistics_daily_users_normal CASCADE;
    DROP TABLE IF EXISTS statistics_daily_users_partitioned CASCADE;

    -- 1. 通常テーブル（パーティションなし）
    CREATE TABLE statistics_daily_users_normal (
        tenant_id UUID NOT NULL,
        stat_date DATE NOT NULL,
        user_id UUID NOT NULL,
        last_used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (tenant_id, stat_date, user_id)
    );

    -- 2. パーティションテーブル（月別）
    CREATE TABLE statistics_daily_users_partitioned (
        tenant_id UUID NOT NULL,
        stat_date DATE NOT NULL,
        user_id UUID NOT NULL,
        last_used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (tenant_id, stat_date, user_id)
    ) PARTITION BY RANGE (stat_date);

    -- 2024年のパーティション作成
    CREATE TABLE statistics_daily_users_partitioned_2024_01 PARTITION OF statistics_daily_users_partitioned FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');
    CREATE TABLE statistics_daily_users_partitioned_2024_02 PARTITION OF statistics_daily_users_partitioned FOR VALUES FROM ('2024-02-01') TO ('2024-03-01');
    CREATE TABLE statistics_daily_users_partitioned_2024_03 PARTITION OF statistics_daily_users_partitioned FOR VALUES FROM ('2024-03-01') TO ('2024-04-01');
    CREATE TABLE statistics_daily_users_partitioned_2024_04 PARTITION OF statistics_daily_users_partitioned FOR VALUES FROM ('2024-04-01') TO ('2024-05-01');
    CREATE TABLE statistics_daily_users_partitioned_2024_05 PARTITION OF statistics_daily_users_partitioned FOR VALUES FROM ('2024-05-01') TO ('2024-06-01');
    CREATE TABLE statistics_daily_users_partitioned_2024_06 PARTITION OF statistics_daily_users_partitioned FOR VALUES FROM ('2024-06-01') TO ('2024-07-01');
    CREATE TABLE statistics_daily_users_partitioned_2024_07 PARTITION OF statistics_daily_users_partitioned FOR VALUES FROM ('2024-07-01') TO ('2024-08-01');
    CREATE TABLE statistics_daily_users_partitioned_2024_08 PARTITION OF statistics_daily_users_partitioned FOR VALUES FROM ('2024-08-01') TO ('2024-09-01');
    CREATE TABLE statistics_daily_users_partitioned_2024_09 PARTITION OF statistics_daily_users_partitioned FOR VALUES FROM ('2024-09-01') TO ('2024-10-01');
    CREATE TABLE statistics_daily_users_partitioned_2024_10 PARTITION OF statistics_daily_users_partitioned FOR VALUES FROM ('2024-10-01') TO ('2024-11-01');
    CREATE TABLE statistics_daily_users_partitioned_2024_11 PARTITION OF statistics_daily_users_partitioned FOR VALUES FROM ('2024-11-01') TO ('2024-12-01');
    CREATE TABLE statistics_daily_users_partitioned_2024_12 PARTITION OF statistics_daily_users_partitioned FOR VALUES FROM ('2024-12-01') TO ('2025-01-01');

    -- インデックス作成（公平な比較のため同じ構成）
    CREATE INDEX idx_daily_users_normal_tenant_date ON statistics_daily_users_normal(tenant_id, stat_date);
    CREATE INDEX idx_daily_users_partitioned_tenant_date ON statistics_daily_users_partitioned(tenant_id, stat_date);
    "

    log_success "statistics_daily_users テーブル作成完了"
}

insert_daily_users_data() {
    local table_name=$1
    local start_time end_time duration

    log_info "${table_name} に ${ROW_COUNT} 行を挿入中..."
    start_time=$(date +%s.%N)

    # 10テナント × 100,000ユーザー × 365日 = 最大365M行可能
    # ROW_COUNT行をテナント・ユーザー・日付の組み合わせで生成
    psql_exec "
    INSERT INTO ${table_name} (tenant_id, stat_date, user_id, last_used_at, created_at)
    SELECT
        -- 10テナントに分散
        ('00000000-0000-0000-0000-00000000000' || (s % 10))::uuid as tenant_id,
        -- 365日に分散
        ('2024-01-01'::date + ((s / 10) % 365))::date as stat_date,
        -- ユニークなユーザーID
        gen_random_uuid() as user_id,
        '2024-01-01'::timestamp + (random() * 365 * 24 * 60 * 60) * interval '1 second',
        '2024-01-01'::timestamp + (random() * 365 * 24 * 60 * 60) * interval '1 second'
    FROM generate_series(1, ${ROW_COUNT}) s;
    "

    end_time=$(date +%s.%N)
    duration=$(echo "$end_time - $start_time" | bc)
    log_success "${table_name}: ${duration}秒で挿入完了"
}

# ==============================================================================
# MONTHLY USERS テーブル
# ==============================================================================
create_monthly_users_tables() {
    log_info "statistics_monthly_users テスト用テーブルを作成中..."

    psql_exec "
    -- 既存テーブルを削除
    DROP TABLE IF EXISTS statistics_monthly_users_normal CASCADE;
    DROP TABLE IF EXISTS statistics_monthly_users_partitioned CASCADE;

    -- 1. 通常テーブル（パーティションなし）
    CREATE TABLE statistics_monthly_users_normal (
        tenant_id UUID NOT NULL,
        stat_month CHAR(7) NOT NULL,
        user_id UUID NOT NULL,
        last_used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (tenant_id, stat_month, user_id)
    );

    -- 2. パーティションテーブル（RANGE by stat_month - 年別）
    -- Note: CHAR(7)のRANGEパーティションで辞書順比較を利用
    CREATE TABLE statistics_monthly_users_partitioned (
        tenant_id UUID NOT NULL,
        stat_month CHAR(7) NOT NULL,
        user_id UUID NOT NULL,
        last_used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (tenant_id, stat_month, user_id)
    ) PARTITION BY RANGE (stat_month);

    -- 年別パーティション作成（2022-2026年）- RANGE by stat_month
    CREATE TABLE statistics_monthly_users_partitioned_2022 PARTITION OF statistics_monthly_users_partitioned FOR VALUES FROM ('2022-01') TO ('2023-01');
    CREATE TABLE statistics_monthly_users_partitioned_2023 PARTITION OF statistics_monthly_users_partitioned FOR VALUES FROM ('2023-01') TO ('2024-01');
    CREATE TABLE statistics_monthly_users_partitioned_2024 PARTITION OF statistics_monthly_users_partitioned FOR VALUES FROM ('2024-01') TO ('2025-01');
    CREATE TABLE statistics_monthly_users_partitioned_2025 PARTITION OF statistics_monthly_users_partitioned FOR VALUES FROM ('2025-01') TO ('2026-01');
    CREATE TABLE statistics_monthly_users_partitioned_2026 PARTITION OF statistics_monthly_users_partitioned FOR VALUES FROM ('2026-01') TO ('2027-01');

    -- インデックス作成
    CREATE INDEX idx_monthly_users_normal_tenant_month ON statistics_monthly_users_normal(tenant_id, stat_month);
    CREATE INDEX idx_monthly_users_partitioned_tenant_month ON statistics_monthly_users_partitioned(tenant_id, stat_month);
    "

    log_success "statistics_monthly_users テーブル作成完了"
}

insert_monthly_users_data() {
    local table_name=$1
    local start_time end_time duration

    log_info "${table_name} に ${ROW_COUNT} 行を挿入中..."
    start_time=$(date +%s.%N)

    # 10テナント × 60ヶ月（5年） × ユニークユーザー
    # 月の配列: 2022-01 ~ 2026-12
    psql_exec "
    INSERT INTO ${table_name} (tenant_id, stat_month, user_id, last_used_at, created_at)
    SELECT
        -- 10テナントに分散
        ('00000000-0000-0000-0000-00000000000' || (s % 10))::uuid as tenant_id,
        -- 60ヶ月に分散 (2022-01 ~ 2026-12)
        to_char(('2022-01-01'::date + (((s / 10) % 60) || ' months')::interval), 'YYYY-MM') as stat_month,
        -- ユニークなユーザーID
        gen_random_uuid() as user_id,
        NOW() - (random() * 365 * 24 * 60 * 60) * interval '1 second',
        NOW() - (random() * 365 * 24 * 60 * 60) * interval '1 second'
    FROM generate_series(1, ${ROW_COUNT}) s;
    "

    end_time=$(date +%s.%N)
    duration=$(echo "$end_time - $start_time" | bc)
    log_success "${table_name}: ${duration}秒で挿入完了"
}

# ==============================================================================
# YEARLY USERS テーブル
# ==============================================================================
create_yearly_users_tables() {
    log_info "statistics_yearly_users テスト用テーブルを作成中..."

    psql_exec "
    -- 既存テーブルを削除
    DROP TABLE IF EXISTS statistics_yearly_users_normal CASCADE;
    DROP TABLE IF EXISTS statistics_yearly_users_partitioned CASCADE;

    -- 1. 通常テーブル（パーティションなし）
    CREATE TABLE statistics_yearly_users_normal (
        tenant_id UUID NOT NULL,
        stat_year CHAR(4) NOT NULL,
        user_id UUID NOT NULL,
        last_used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (tenant_id, stat_year, user_id)
    );

    -- 2. パーティションテーブル（年別）
    CREATE TABLE statistics_yearly_users_partitioned (
        tenant_id UUID NOT NULL,
        stat_year CHAR(4) NOT NULL,
        user_id UUID NOT NULL,
        last_used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (tenant_id, stat_year, user_id)
    ) PARTITION BY LIST (stat_year);

    -- 年別パーティション作成（2020-2030年）
    CREATE TABLE statistics_yearly_users_partitioned_2020 PARTITION OF statistics_yearly_users_partitioned FOR VALUES IN ('2020');
    CREATE TABLE statistics_yearly_users_partitioned_2021 PARTITION OF statistics_yearly_users_partitioned FOR VALUES IN ('2021');
    CREATE TABLE statistics_yearly_users_partitioned_2022 PARTITION OF statistics_yearly_users_partitioned FOR VALUES IN ('2022');
    CREATE TABLE statistics_yearly_users_partitioned_2023 PARTITION OF statistics_yearly_users_partitioned FOR VALUES IN ('2023');
    CREATE TABLE statistics_yearly_users_partitioned_2024 PARTITION OF statistics_yearly_users_partitioned FOR VALUES IN ('2024');
    CREATE TABLE statistics_yearly_users_partitioned_2025 PARTITION OF statistics_yearly_users_partitioned FOR VALUES IN ('2025');
    CREATE TABLE statistics_yearly_users_partitioned_2026 PARTITION OF statistics_yearly_users_partitioned FOR VALUES IN ('2026');
    CREATE TABLE statistics_yearly_users_partitioned_2027 PARTITION OF statistics_yearly_users_partitioned FOR VALUES IN ('2027');
    CREATE TABLE statistics_yearly_users_partitioned_2028 PARTITION OF statistics_yearly_users_partitioned FOR VALUES IN ('2028');
    CREATE TABLE statistics_yearly_users_partitioned_2029 PARTITION OF statistics_yearly_users_partitioned FOR VALUES IN ('2029');
    CREATE TABLE statistics_yearly_users_partitioned_2030 PARTITION OF statistics_yearly_users_partitioned FOR VALUES IN ('2030');

    -- インデックス作成
    CREATE INDEX idx_yearly_users_normal_tenant_year ON statistics_yearly_users_normal(tenant_id, stat_year);
    CREATE INDEX idx_yearly_users_normal_last_used ON statistics_yearly_users_normal(tenant_id, last_used_at);
    CREATE INDEX idx_yearly_users_partitioned_tenant_year ON statistics_yearly_users_partitioned(tenant_id, stat_year);
    CREATE INDEX idx_yearly_users_partitioned_last_used ON statistics_yearly_users_partitioned(tenant_id, last_used_at);
    "

    log_success "statistics_yearly_users テーブル作成完了"
}

insert_yearly_users_data() {
    local table_name=$1
    local start_time end_time duration

    log_info "${table_name} に ${ROW_COUNT} 行を挿入中..."
    start_time=$(date +%s.%N)

    # 10テナント × 11年（2020-2030） × ユニークユーザー
    psql_exec "
    INSERT INTO ${table_name} (tenant_id, stat_year, user_id, last_used_at, created_at)
    SELECT
        -- 10テナントに分散
        ('00000000-0000-0000-0000-00000000000' || (s % 10))::uuid as tenant_id,
        -- 11年に分散 (2020 ~ 2030)
        to_char(('2020-01-01'::date + (((s / 10) % 11) || ' years')::interval), 'YYYY') as stat_year,
        -- ユニークなユーザーID
        gen_random_uuid() as user_id,
        NOW() - (random() * 365 * 24 * 60 * 60) * interval '1 second',
        NOW() - (random() * 365 * 24 * 60 * 60) * interval '1 second'
    FROM generate_series(1, ${ROW_COUNT}) s;
    "

    end_time=$(date +%s.%N)
    duration=$(echo "$end_time - $start_time" | bc)
    log_success "${table_name}: ${duration}秒で挿入完了"
}

# ==============================================================================
# 統計情報更新
# ==============================================================================
analyze_tables() {
    log_info "統計情報を更新中..."
    psql_exec "
    ANALYZE statistics_daily_users_normal;
    ANALYZE statistics_daily_users_partitioned;
    ANALYZE statistics_monthly_users_normal;
    ANALYZE statistics_monthly_users_partitioned;
    ANALYZE statistics_yearly_users_normal;
    ANALYZE statistics_yearly_users_partitioned;
    "
    log_success "統計情報更新完了"
}

# ==============================================================================
# 性能テスト
# ==============================================================================
run_benchmark() {
    local test_name=$1
    local query=$2
    local result

    result=$(psql_exec_quiet "EXPLAIN (ANALYZE, FORMAT JSON) ${query}" | grep -o '"Execution Time": [0-9.]*' | head -1 | grep -o '[0-9.]*')
    echo "$result"
}

run_daily_users_benchmarks() {
    echo ""
    echo "=============================================="
    echo " statistics_daily_users 性能比較テスト"
    echo "=============================================="
    echo ""

    # テスト1: 特定日のDAU COUNT
    log_info "テスト1: 特定日のDAU COUNT (tenant_id指定)"
    local tenant_id=$(psql_exec_quiet "SELECT tenant_id FROM statistics_daily_users_normal LIMIT 1" | tr -d ' ')
    local test1_normal=$(run_benchmark "test1" \
        "SELECT count(DISTINCT user_id) FROM statistics_daily_users_normal WHERE tenant_id = '${tenant_id}' AND stat_date = '2024-03-15'")
    local test1_partitioned=$(run_benchmark "test1" \
        "SELECT count(DISTINCT user_id) FROM statistics_daily_users_partitioned WHERE tenant_id = '${tenant_id}' AND stat_date = '2024-03-15'")

    # テスト2: 月間範囲のDAU推移
    log_info "テスト2: 1ヶ月間のDAU推移"
    local test2_normal=$(run_benchmark "test2" \
        "SELECT stat_date, count(DISTINCT user_id) as dau FROM statistics_daily_users_normal WHERE tenant_id = '${tenant_id}' AND stat_date >= '2024-03-01' AND stat_date < '2024-04-01' GROUP BY stat_date ORDER BY stat_date")
    local test2_partitioned=$(run_benchmark "test2" \
        "SELECT stat_date, count(DISTINCT user_id) as dau FROM statistics_daily_users_partitioned WHERE tenant_id = '${tenant_id}' AND stat_date >= '2024-03-01' AND stat_date < '2024-04-01' GROUP BY stat_date ORDER BY stat_date")

    # テスト3: 全期間の総ユニークユーザー
    log_info "テスト3: 全期間の総ユニークユーザー"
    local test3_normal=$(run_benchmark "test3" \
        "SELECT count(DISTINCT user_id) FROM statistics_daily_users_normal WHERE tenant_id = '${tenant_id}'")
    local test3_partitioned=$(run_benchmark "test3" \
        "SELECT count(DISTINCT user_id) FROM statistics_daily_users_partitioned WHERE tenant_id = '${tenant_id}'")

    # テスト4: 特定ユーザーの活動履歴
    log_info "テスト4: 特定ユーザーの活動履歴"
    local user_id=$(psql_exec_quiet "SELECT user_id FROM statistics_daily_users_normal WHERE tenant_id = '${tenant_id}' LIMIT 1" | tr -d ' ')
    local test4_normal=$(run_benchmark "test4" \
        "SELECT stat_date, last_used_at FROM statistics_daily_users_normal WHERE tenant_id = '${tenant_id}' AND user_id = '${user_id}' ORDER BY stat_date DESC LIMIT 30")
    local test4_partitioned=$(run_benchmark "test4" \
        "SELECT stat_date, last_used_at FROM statistics_daily_users_partitioned WHERE tenant_id = '${tenant_id}' AND user_id = '${user_id}' ORDER BY stat_date DESC LIMIT 30")

    # 結果表示
    echo ""
    printf "%-35s %15s %15s %15s\n" "テスト" "通常(ms)" "パーティション(ms)" "勝者"
    printf "%-35s %15s %15s %15s\n" "-----------------------------------" "---------------" "---------------" "---------------"

    print_result "特定日のDAU COUNT" "$test1_normal" "$test1_partitioned"
    print_result "1ヶ月間のDAU推移" "$test2_normal" "$test2_partitioned"
    print_result "全期間ユニークユーザー" "$test3_normal" "$test3_partitioned"
    print_result "特定ユーザー活動履歴" "$test4_normal" "$test4_partitioned"
}

run_monthly_users_benchmarks() {
    echo ""
    echo "=============================================="
    echo " statistics_monthly_users 性能比較テスト"
    echo "=============================================="
    echo ""

    local tenant_id=$(psql_exec_quiet "SELECT tenant_id FROM statistics_monthly_users_normal LIMIT 1" | tr -d ' ')

    # テスト1: 特定月のMAU COUNT
    log_info "テスト1: 特定月のMAU COUNT"
    local test1_normal=$(run_benchmark "test1" \
        "SELECT count(DISTINCT user_id) FROM statistics_monthly_users_normal WHERE tenant_id = '${tenant_id}' AND stat_month = '2024-03'")
    local test1_partitioned=$(run_benchmark "test1" \
        "SELECT count(DISTINCT user_id) FROM statistics_monthly_users_partitioned WHERE tenant_id = '${tenant_id}' AND stat_month = '2024-03'")

    # テスト2: 年間MAU推移
    log_info "テスト2: 年間MAU推移"
    local test2_normal=$(run_benchmark "test2" \
        "SELECT stat_month, count(DISTINCT user_id) as mau FROM statistics_monthly_users_normal WHERE tenant_id = '${tenant_id}' AND stat_month LIKE '2024-%' GROUP BY stat_month ORDER BY stat_month")
    local test2_partitioned=$(run_benchmark "test2" \
        "SELECT stat_month, count(DISTINCT user_id) as mau FROM statistics_monthly_users_partitioned WHERE tenant_id = '${tenant_id}' AND stat_month LIKE '2024-%' GROUP BY stat_month ORDER BY stat_month")

    # テスト3: 複数年のMAU比較
    log_info "テスト3: 複数年のMAU比較"
    local test3_normal=$(run_benchmark "test3" \
        "SELECT LEFT(stat_month, 4) as year, count(DISTINCT user_id) as unique_users FROM statistics_monthly_users_normal WHERE tenant_id = '${tenant_id}' GROUP BY LEFT(stat_month, 4) ORDER BY year")
    local test3_partitioned=$(run_benchmark "test3" \
        "SELECT LEFT(stat_month, 4) as year, count(DISTINCT user_id) as unique_users FROM statistics_monthly_users_partitioned WHERE tenant_id = '${tenant_id}' GROUP BY LEFT(stat_month, 4) ORDER BY year")

    # テスト4: 特定月範囲でのユーザー検索
    log_info "テスト4: 6ヶ月間のユニークユーザー"
    local test4_normal=$(run_benchmark "test4" \
        "SELECT count(DISTINCT user_id) FROM statistics_monthly_users_normal WHERE tenant_id = '${tenant_id}' AND stat_month >= '2024-01' AND stat_month <= '2024-06'")
    local test4_partitioned=$(run_benchmark "test4" \
        "SELECT count(DISTINCT user_id) FROM statistics_monthly_users_partitioned WHERE tenant_id = '${tenant_id}' AND stat_month >= '2024-01' AND stat_month <= '2024-06'")

    # 結果表示
    echo ""
    printf "%-35s %15s %15s %15s\n" "テスト" "通常(ms)" "パーティション(ms)" "勝者"
    printf "%-35s %15s %15s %15s\n" "-----------------------------------" "---------------" "---------------" "---------------"

    print_result "特定月のMAU COUNT" "$test1_normal" "$test1_partitioned"
    print_result "年間MAU推移" "$test2_normal" "$test2_partitioned"
    print_result "複数年のMAU比較" "$test3_normal" "$test3_partitioned"
    print_result "6ヶ月間ユニークユーザー" "$test4_normal" "$test4_partitioned"
}

run_yearly_users_benchmarks() {
    echo ""
    echo "=============================================="
    echo " statistics_yearly_users 性能比較テスト"
    echo "=============================================="
    echo ""

    local tenant_id=$(psql_exec_quiet "SELECT tenant_id FROM statistics_yearly_users_normal LIMIT 1" | tr -d ' ')

    # テスト1: 特定年のYAU COUNT
    log_info "テスト1: 特定年のYAU COUNT"
    local test1_normal=$(run_benchmark "test1" \
        "SELECT count(DISTINCT user_id) FROM statistics_yearly_users_normal WHERE tenant_id = '${tenant_id}' AND stat_year = '2024'")
    local test1_partitioned=$(run_benchmark "test1" \
        "SELECT count(DISTINCT user_id) FROM statistics_yearly_users_partitioned WHERE tenant_id = '${tenant_id}' AND stat_year = '2024'")

    # テスト2: 複数年のYAU推移
    log_info "テスト2: 複数年のYAU推移"
    local test2_normal=$(run_benchmark "test2" \
        "SELECT stat_year, count(DISTINCT user_id) as yau FROM statistics_yearly_users_normal WHERE tenant_id = '${tenant_id}' GROUP BY stat_year ORDER BY stat_year")
    local test2_partitioned=$(run_benchmark "test2" \
        "SELECT stat_year, count(DISTINCT user_id) as yau FROM statistics_yearly_users_partitioned WHERE tenant_id = '${tenant_id}' GROUP BY stat_year ORDER BY stat_year")

    # テスト3: 直近アクティブユーザー検索
    log_info "テスト3: 直近アクティブユーザー検索 (last_used_at > 30日前)"
    local test3_normal=$(run_benchmark "test3" \
        "SELECT count(*) FROM statistics_yearly_users_normal WHERE tenant_id = '${tenant_id}' AND stat_year = '2024' AND last_used_at > NOW() - interval '30 days'")
    local test3_partitioned=$(run_benchmark "test3" \
        "SELECT count(*) FROM statistics_yearly_users_partitioned WHERE tenant_id = '${tenant_id}' AND stat_year = '2024' AND last_used_at > NOW() - interval '30 days'")

    # テスト4: 非アクティブユーザー検索（休眠ユーザー）
    log_info "テスト4: 非アクティブユーザー検索 (last_used_at < 180日前)"
    local test4_normal=$(run_benchmark "test4" \
        "SELECT count(*) FROM statistics_yearly_users_normal WHERE tenant_id = '${tenant_id}' AND stat_year = '2024' AND last_used_at < NOW() - interval '180 days'")
    local test4_partitioned=$(run_benchmark "test4" \
        "SELECT count(*) FROM statistics_yearly_users_partitioned WHERE tenant_id = '${tenant_id}' AND stat_year = '2024' AND last_used_at < NOW() - interval '180 days'")

    # 結果表示
    echo ""
    printf "%-35s %15s %15s %15s\n" "テスト" "通常(ms)" "パーティション(ms)" "勝者"
    printf "%-35s %15s %15s %15s\n" "-----------------------------------" "---------------" "---------------" "---------------"

    print_result "特定年のYAU COUNT" "$test1_normal" "$test1_partitioned"
    print_result "複数年のYAU推移" "$test2_normal" "$test2_partitioned"
    print_result "直近アクティブユーザー" "$test3_normal" "$test3_partitioned"
    print_result "非アクティブユーザー" "$test4_normal" "$test4_partitioned"
}

print_result() {
    local test_name=$1
    local normal=$2
    local partitioned=$3
    local winner="パーティション"

    if [ -n "$normal" ] && [ -n "$partitioned" ]; then
        if (( $(echo "$normal < $partitioned" | bc -l) )); then
            winner="通常"
        fi
    fi
    printf "%-35s %15.2f %15.2f %15s\n" "$test_name" "${normal:-0}" "${partitioned:-0}" "$winner"
}

# ==============================================================================
# テーブルサイズ表示
# ==============================================================================
show_table_sizes() {
    echo ""
    echo "=============================================="
    echo " テーブルサイズ"
    echo "=============================================="
    echo ""

    psql_exec "
    SELECT
        'daily_users_normal' as table_name,
        pg_size_pretty(pg_table_size('statistics_daily_users_normal')) as table_size,
        pg_size_pretty(pg_indexes_size('statistics_daily_users_normal')) as index_size,
        pg_size_pretty(pg_total_relation_size('statistics_daily_users_normal')) as total_size
    UNION ALL
    SELECT
        'daily_users_partitioned',
        pg_size_pretty(COALESCE(sum(pg_table_size(inhrelid::regclass)), 0)),
        pg_size_pretty(COALESCE(sum(pg_indexes_size(inhrelid::regclass)), 0)),
        pg_size_pretty(COALESCE(sum(pg_total_relation_size(inhrelid::regclass)), 0))
    FROM pg_inherits WHERE inhparent = 'statistics_daily_users_partitioned'::regclass
    UNION ALL
    SELECT
        'monthly_users_normal',
        pg_size_pretty(pg_table_size('statistics_monthly_users_normal')),
        pg_size_pretty(pg_indexes_size('statistics_monthly_users_normal')),
        pg_size_pretty(pg_total_relation_size('statistics_monthly_users_normal'))
    UNION ALL
    SELECT
        'monthly_users_partitioned',
        pg_size_pretty(COALESCE(sum(pg_table_size(inhrelid::regclass)), 0)),
        pg_size_pretty(COALESCE(sum(pg_indexes_size(inhrelid::regclass)), 0)),
        pg_size_pretty(COALESCE(sum(pg_total_relation_size(inhrelid::regclass)), 0))
    FROM pg_inherits WHERE inhparent = 'statistics_monthly_users_partitioned'::regclass
    UNION ALL
    SELECT
        'yearly_users_normal',
        pg_size_pretty(pg_table_size('statistics_yearly_users_normal')),
        pg_size_pretty(pg_indexes_size('statistics_yearly_users_normal')),
        pg_size_pretty(pg_total_relation_size('statistics_yearly_users_normal'))
    UNION ALL
    SELECT
        'yearly_users_partitioned',
        pg_size_pretty(COALESCE(sum(pg_table_size(inhrelid::regclass)), 0)),
        pg_size_pretty(COALESCE(sum(pg_indexes_size(inhrelid::regclass)), 0)),
        pg_size_pretty(COALESCE(sum(pg_total_relation_size(inhrelid::regclass)), 0))
    FROM pg_inherits WHERE inhparent = 'statistics_yearly_users_partitioned'::regclass;
    "
}

# ==============================================================================
# パーティション分布表示
# ==============================================================================
show_partition_distribution() {
    echo ""
    echo "=============================================="
    echo " パーティション別データ分布"
    echo "=============================================="

    echo ""
    echo "--- daily_users_partitioned ---"
    psql_exec "
    SELECT
        tableoid::regclass as partition_name,
        count(*) as row_count
    FROM statistics_daily_users_partitioned
    GROUP BY tableoid
    ORDER BY partition_name;
    "

    echo ""
    echo "--- monthly_users_partitioned ---"
    psql_exec "
    SELECT
        tableoid::regclass as partition_name,
        count(*) as row_count
    FROM statistics_monthly_users_partitioned
    GROUP BY tableoid
    ORDER BY partition_name;
    "

    echo ""
    echo "--- yearly_users_partitioned ---"
    psql_exec "
    SELECT
        tableoid::regclass as partition_name,
        count(*) as row_count
    FROM statistics_yearly_users_partitioned
    GROUP BY tableoid
    ORDER BY partition_name;
    "
}

# ==============================================================================
# 削除テスト
# ==============================================================================
run_delete_benchmark() {
    echo ""
    echo "=============================================="
    echo " 削除性能テスト"
    echo "=============================================="
    echo ""

    # Daily: 通常テーブルの削除
    log_info "daily_users 通常テーブル: 1月分のデータを削除"
    local count_before=$(psql_exec_quiet "SELECT count(*) FROM statistics_daily_users_normal WHERE stat_date < '2024-02-01'" | tr -d ' ')
    local start_time=$(date +%s.%N)
    psql_exec "DELETE FROM statistics_daily_users_normal WHERE stat_date < '2024-02-01';" > /dev/null
    local end_time=$(date +%s.%N)
    local duration=$(echo "$end_time - $start_time" | bc)
    echo "  daily_users 通常: ${count_before}行削除 → ${duration}秒"

    # Daily: パーティションテーブルの削除（DROP TABLE）
    log_info "daily_users パーティション: 1月のパーティションをDROP"
    count_before=$(psql_exec_quiet "SELECT count(*) FROM statistics_daily_users_partitioned_2024_01" | tr -d ' ')
    start_time=$(date +%s.%N)
    psql_exec "DROP TABLE statistics_daily_users_partitioned_2024_01;" > /dev/null
    end_time=$(date +%s.%N)
    duration=$(echo "$end_time - $start_time" | bc)
    echo "  daily_users パーティション: ${count_before}行削除(DROP) → ${duration}秒"

    # Monthly: 通常テーブルの削除
    log_info "monthly_users 通常テーブル: 2022年分のデータを削除"
    count_before=$(psql_exec_quiet "SELECT count(*) FROM statistics_monthly_users_normal WHERE stat_month LIKE '2022-%'" | tr -d ' ')
    start_time=$(date +%s.%N)
    psql_exec "DELETE FROM statistics_monthly_users_normal WHERE stat_month LIKE '2022-%';" > /dev/null
    end_time=$(date +%s.%N)
    duration=$(echo "$end_time - $start_time" | bc)
    echo "  monthly_users 通常: ${count_before}行削除 → ${duration}秒"

    # Monthly: パーティションテーブルの削除（DROP TABLE）
    log_info "monthly_users パーティション: 2022年のパーティションをDROP"
    count_before=$(psql_exec_quiet "SELECT count(*) FROM statistics_monthly_users_partitioned_2022" | tr -d ' ')
    start_time=$(date +%s.%N)
    psql_exec "DROP TABLE statistics_monthly_users_partitioned_2022;" > /dev/null
    end_time=$(date +%s.%N)
    duration=$(echo "$end_time - $start_time" | bc)
    echo "  monthly_users パーティション: ${count_before}行削除(DROP) → ${duration}秒"

    # Yearly: 通常テーブルの削除
    log_info "yearly_users 通常テーブル: 2020年分のデータを削除"
    count_before=$(psql_exec_quiet "SELECT count(*) FROM statistics_yearly_users_normal WHERE stat_year = '2020'" | tr -d ' ')
    start_time=$(date +%s.%N)
    psql_exec "DELETE FROM statistics_yearly_users_normal WHERE stat_year = '2020';" > /dev/null
    end_time=$(date +%s.%N)
    duration=$(echo "$end_time - $start_time" | bc)
    echo "  yearly_users 通常: ${count_before}行削除 → ${duration}秒"

    # Yearly: パーティションテーブルの削除（DROP TABLE）
    log_info "yearly_users パーティション: 2020年のパーティションをDROP"
    count_before=$(psql_exec_quiet "SELECT count(*) FROM statistics_yearly_users_partitioned_2020" | tr -d ' ')
    start_time=$(date +%s.%N)
    psql_exec "DROP TABLE statistics_yearly_users_partitioned_2020;" > /dev/null
    end_time=$(date +%s.%N)
    duration=$(echo "$end_time - $start_time" | bc)
    echo "  yearly_users パーティション: ${count_before}行削除(DROP) → ${duration}秒"
}

# ==============================================================================
# クリーンアップ
# ==============================================================================
cleanup() {
    log_info "テストテーブルを削除中..."
    psql_exec "
    DROP TABLE IF EXISTS statistics_daily_users_normal CASCADE;
    DROP TABLE IF EXISTS statistics_daily_users_partitioned CASCADE;
    DROP TABLE IF EXISTS statistics_monthly_users_normal CASCADE;
    DROP TABLE IF EXISTS statistics_monthly_users_partitioned CASCADE;
    DROP TABLE IF EXISTS statistics_yearly_users_normal CASCADE;
    DROP TABLE IF EXISTS statistics_yearly_users_partitioned CASCADE;
    " > /dev/null 2>&1
    log_success "クリーンアップ完了"
}

# ==============================================================================
# ヘルプ表示
# ==============================================================================
show_help() {
    echo "PostgreSQL パーティションテーブル性能比較スクリプト"
    echo "対象: statistics_yearly_users, statistics_monthly_users, statistics_daily_users"
    echo ""
    echo "使用方法: $0 [オプション] [行数]"
    echo ""
    echo "オプション:"
    echo "  -h, --help     このヘルプを表示"
    echo "  -c, --cleanup  テストテーブルを削除して終了"
    echo ""
    echo "環境変数:"
    echo "  POSTGRES_CONTAINER  コンテナ名 (デフォルト: postgres-primary)"
    echo "  POSTGRES_USER       ユーザー名 (デフォルト: idpserver)"
    echo "  POSTGRES_DB         データベース名 (デフォルト: idpserver)"
    echo ""
    echo "例:"
    echo "  $0                    # 100万行でテスト"
    echo "  $0 500000             # 50万行でテスト"
    echo "  $0 --cleanup          # テストテーブルを削除"
}

# ==============================================================================
# メイン処理
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
    echo " PostgreSQL Statistics Users パーティション性能比較"
    echo "=============================================="
    echo " 行数: ${ROW_COUNT}"
    echo " コンテナ: ${CONTAINER_NAME}"
    echo " データベース: ${DB_NAME}"
    echo "=============================================="
    echo ""

    check_container

    # テーブル作成
    create_daily_users_tables
    create_monthly_users_tables
    create_yearly_users_tables

    # データ挿入
    insert_daily_users_data "statistics_daily_users_normal"
    insert_daily_users_data "statistics_daily_users_partitioned"
    insert_monthly_users_data "statistics_monthly_users_normal"
    insert_monthly_users_data "statistics_monthly_users_partitioned"
    insert_yearly_users_data "statistics_yearly_users_normal"
    insert_yearly_users_data "statistics_yearly_users_partitioned"

    # 統計更新
    analyze_tables

    # サイズ表示
    show_table_sizes
    show_partition_distribution

    # ベンチマーク実行
    run_daily_users_benchmarks
    run_monthly_users_benchmarks
    run_yearly_users_benchmarks

    # 削除テスト
    run_delete_benchmark

    echo ""
    log_success "ベンチマーク完了"
    echo ""
    echo "クリーンアップするには: $0 --cleanup"
}

main "$@"
