#!/usr/bin/env bash
#
# PostgreSQL パーティションテーブル vs 通常テーブル 性能比較スクリプト
# 対象: security_event, security_event_hook_results
#
# 使用方法:
#   ./scripts/partition/security-event-benchmark.sh [行数]
#
# 例:
#   ./scripts/partition/security-event-benchmark.sh          # デフォルト: 100万行
#   ./scripts/partition/security-event-benchmark.sh 500000   # 50万行
#   ./scripts/partition/security-event-benchmark.sh 5000000  # 500万行
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
# security_event テーブル
# ==============================================================================
create_security_event_tables() {
    log_info "security_event テスト用テーブルを作成中..."

    psql_exec "
    -- 既存テーブルを削除
    DROP TABLE IF EXISTS security_event_normal CASCADE;
    DROP TABLE IF EXISTS security_event_partitioned CASCADE;

    -- 1. 通常テーブル（パーティションなし）
    CREATE TABLE security_event_normal (
        id UUID PRIMARY KEY,
        type VARCHAR(255) NOT NULL,
        description VARCHAR(255) NOT NULL,
        tenant_id UUID NOT NULL,
        tenant_name VARCHAR(255) NOT NULL,
        client_id VARCHAR(255) NOT NULL,
        client_name VARCHAR(255) NOT NULL,
        user_id UUID,
        user_name VARCHAR(255),
        external_user_id VARCHAR(255),
        ip_address INET,
        user_agent TEXT,
        detail JSONB NOT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

    -- 2. パーティションテーブル（月別）
    CREATE TABLE security_event_partitioned (
        id UUID NOT NULL,
        type VARCHAR(255) NOT NULL,
        description VARCHAR(255) NOT NULL,
        tenant_id UUID NOT NULL,
        tenant_name VARCHAR(255) NOT NULL,
        client_id VARCHAR(255) NOT NULL,
        client_name VARCHAR(255) NOT NULL,
        user_id UUID,
        user_name VARCHAR(255),
        external_user_id VARCHAR(255),
        ip_address INET,
        user_agent TEXT,
        detail JSONB NOT NULL,
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (id, created_at)
    ) PARTITION BY RANGE (created_at);

    -- 2024年のパーティション作成
    CREATE TABLE security_event_partitioned_2024_01 PARTITION OF security_event_partitioned FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');
    CREATE TABLE security_event_partitioned_2024_02 PARTITION OF security_event_partitioned FOR VALUES FROM ('2024-02-01') TO ('2024-03-01');
    CREATE TABLE security_event_partitioned_2024_03 PARTITION OF security_event_partitioned FOR VALUES FROM ('2024-03-01') TO ('2024-04-01');
    CREATE TABLE security_event_partitioned_2024_04 PARTITION OF security_event_partitioned FOR VALUES FROM ('2024-04-01') TO ('2024-05-01');
    CREATE TABLE security_event_partitioned_2024_05 PARTITION OF security_event_partitioned FOR VALUES FROM ('2024-05-01') TO ('2024-06-01');
    CREATE TABLE security_event_partitioned_2024_06 PARTITION OF security_event_partitioned FOR VALUES FROM ('2024-06-01') TO ('2024-07-01');
    CREATE TABLE security_event_partitioned_2024_07 PARTITION OF security_event_partitioned FOR VALUES FROM ('2024-07-01') TO ('2024-08-01');
    CREATE TABLE security_event_partitioned_2024_08 PARTITION OF security_event_partitioned FOR VALUES FROM ('2024-08-01') TO ('2024-09-01');
    CREATE TABLE security_event_partitioned_2024_09 PARTITION OF security_event_partitioned FOR VALUES FROM ('2024-09-01') TO ('2024-10-01');
    CREATE TABLE security_event_partitioned_2024_10 PARTITION OF security_event_partitioned FOR VALUES FROM ('2024-10-01') TO ('2024-11-01');
    CREATE TABLE security_event_partitioned_2024_11 PARTITION OF security_event_partitioned FOR VALUES FROM ('2024-11-01') TO ('2024-12-01');
    CREATE TABLE security_event_partitioned_2024_12 PARTITION OF security_event_partitioned FOR VALUES FROM ('2024-12-01') TO ('2025-01-01');

    -- DEFAULTパーティション
    CREATE TABLE security_event_partitioned_default PARTITION OF security_event_partitioned DEFAULT;

    -- インデックス作成（公平な比較のため同じ構成）
    CREATE INDEX idx_sec_event_normal_type ON security_event_normal(type);
    CREATE INDEX idx_sec_event_normal_tenant ON security_event_normal(tenant_id);
    CREATE INDEX idx_sec_event_normal_tenant_created ON security_event_normal(tenant_id, created_at);
    CREATE INDEX idx_sec_event_normal_user ON security_event_normal(user_id);
    CREATE INDEX idx_sec_event_normal_external_user ON security_event_normal(external_user_id);

    CREATE INDEX idx_sec_event_partitioned_type ON security_event_partitioned(type);
    CREATE INDEX idx_sec_event_partitioned_tenant ON security_event_partitioned(tenant_id);
    CREATE INDEX idx_sec_event_partitioned_tenant_created ON security_event_partitioned(tenant_id, created_at);
    CREATE INDEX idx_sec_event_partitioned_user ON security_event_partitioned(user_id);
    CREATE INDEX idx_sec_event_partitioned_external_user ON security_event_partitioned(external_user_id);
    "

    log_success "security_event テーブル作成完了"
}

# ==============================================================================
# security_event_hook_results テーブル
# ==============================================================================
create_hook_results_tables() {
    log_info "security_event_hook_results テスト用テーブルを作成中..."

    psql_exec "
    -- 既存テーブルを削除
    DROP TABLE IF EXISTS security_event_hook_results_normal CASCADE;
    DROP TABLE IF EXISTS security_event_hook_results_partitioned CASCADE;

    -- 1. 通常テーブル（パーティションなし）
    CREATE TABLE security_event_hook_results_normal (
        id UUID PRIMARY KEY,
        tenant_id UUID NOT NULL,
        security_event_id UUID NOT NULL,
        security_event_type VARCHAR(255) NOT NULL,
        security_event_hook VARCHAR(255) NOT NULL,
        security_event_payload JSONB NOT NULL,
        security_event_hook_execution_payload JSONB,
        status VARCHAR(255) NOT NULL,
        created_at TIMESTAMP DEFAULT now() NOT NULL,
        updated_at TIMESTAMP DEFAULT now() NOT NULL
    );

    -- 2. パーティションテーブル（月別）
    CREATE TABLE security_event_hook_results_partitioned (
        id UUID NOT NULL,
        tenant_id UUID NOT NULL,
        security_event_id UUID NOT NULL,
        security_event_type VARCHAR(255) NOT NULL,
        security_event_hook VARCHAR(255) NOT NULL,
        security_event_payload JSONB NOT NULL,
        security_event_hook_execution_payload JSONB,
        status VARCHAR(255) NOT NULL,
        created_at TIMESTAMP NOT NULL DEFAULT now(),
        updated_at TIMESTAMP NOT NULL DEFAULT now(),
        PRIMARY KEY (id, created_at)
    ) PARTITION BY RANGE (created_at);

    -- 2024年のパーティション作成
    CREATE TABLE security_event_hook_results_partitioned_2024_01 PARTITION OF security_event_hook_results_partitioned FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');
    CREATE TABLE security_event_hook_results_partitioned_2024_02 PARTITION OF security_event_hook_results_partitioned FOR VALUES FROM ('2024-02-01') TO ('2024-03-01');
    CREATE TABLE security_event_hook_results_partitioned_2024_03 PARTITION OF security_event_hook_results_partitioned FOR VALUES FROM ('2024-03-01') TO ('2024-04-01');
    CREATE TABLE security_event_hook_results_partitioned_2024_04 PARTITION OF security_event_hook_results_partitioned FOR VALUES FROM ('2024-04-01') TO ('2024-05-01');
    CREATE TABLE security_event_hook_results_partitioned_2024_05 PARTITION OF security_event_hook_results_partitioned FOR VALUES FROM ('2024-05-01') TO ('2024-06-01');
    CREATE TABLE security_event_hook_results_partitioned_2024_06 PARTITION OF security_event_hook_results_partitioned FOR VALUES FROM ('2024-06-01') TO ('2024-07-01');
    CREATE TABLE security_event_hook_results_partitioned_2024_07 PARTITION OF security_event_hook_results_partitioned FOR VALUES FROM ('2024-07-01') TO ('2024-08-01');
    CREATE TABLE security_event_hook_results_partitioned_2024_08 PARTITION OF security_event_hook_results_partitioned FOR VALUES FROM ('2024-08-01') TO ('2024-09-01');
    CREATE TABLE security_event_hook_results_partitioned_2024_09 PARTITION OF security_event_hook_results_partitioned FOR VALUES FROM ('2024-09-01') TO ('2024-10-01');
    CREATE TABLE security_event_hook_results_partitioned_2024_10 PARTITION OF security_event_hook_results_partitioned FOR VALUES FROM ('2024-10-01') TO ('2024-11-01');
    CREATE TABLE security_event_hook_results_partitioned_2024_11 PARTITION OF security_event_hook_results_partitioned FOR VALUES FROM ('2024-11-01') TO ('2024-12-01');
    CREATE TABLE security_event_hook_results_partitioned_2024_12 PARTITION OF security_event_hook_results_partitioned FOR VALUES FROM ('2024-12-01') TO ('2025-01-01');

    -- DEFAULTパーティション
    CREATE TABLE security_event_hook_results_partitioned_default PARTITION OF security_event_hook_results_partitioned DEFAULT;

    -- インデックス作成
    CREATE INDEX idx_hook_results_normal_tenant ON security_event_hook_results_normal(tenant_id);
    CREATE INDEX idx_hook_results_normal_event ON security_event_hook_results_normal(security_event_id);
    CREATE INDEX idx_hook_results_normal_status ON security_event_hook_results_normal(status);
    CREATE INDEX idx_hook_results_normal_tenant_created ON security_event_hook_results_normal(tenant_id, created_at);

    CREATE INDEX idx_hook_results_partitioned_tenant ON security_event_hook_results_partitioned(tenant_id);
    CREATE INDEX idx_hook_results_partitioned_event ON security_event_hook_results_partitioned(security_event_id);
    CREATE INDEX idx_hook_results_partitioned_status ON security_event_hook_results_partitioned(status);
    CREATE INDEX idx_hook_results_partitioned_tenant_created ON security_event_hook_results_partitioned(tenant_id, created_at);
    "

    log_success "security_event_hook_results テーブル作成完了"
}

# ==============================================================================
# データ挿入
# ==============================================================================
insert_security_event_data() {
    local table_name=$1
    local start_time end_time duration

    log_info "${table_name} に ${ROW_COUNT} 行を挿入中..."
    start_time=$(date +%s.%N)

    # セキュリティイベントタイプ
    # https://datatracker.ietf.org/doc/html/rfc8417 SET (Security Event Token) 準拠
    psql_exec "
    INSERT INTO ${table_name} (
        id, type, description, tenant_id, tenant_name,
        client_id, client_name, user_id, user_name, external_user_id,
        ip_address, user_agent, detail, created_at
    )
    SELECT
        gen_random_uuid(),
        (ARRAY[
            'https://schemas.openid.net/secevent/risc/event-type/account-credential-change-required',
            'https://schemas.openid.net/secevent/risc/event-type/account-purged',
            'https://schemas.openid.net/secevent/risc/event-type/account-disabled',
            'https://schemas.openid.net/secevent/risc/event-type/account-enabled',
            'https://schemas.openid.net/secevent/risc/event-type/identifier-changed',
            'https://schemas.openid.net/secevent/risc/event-type/identifier-recycled',
            'https://schemas.openid.net/secevent/risc/event-type/credential-compromise',
            'https://schemas.openid.net/secevent/risc/event-type/sessions-revoked',
            'https://schemas.openid.net/secevent/caep/event-type/session-revoked',
            'https://schemas.openid.net/secevent/caep/event-type/token-claims-change'
        ])[1 + (s % 10)],
        'Security event occurred',
        ('00000000-0000-0000-0000-00000000000' || (s % 10))::uuid,
        'tenant-' || (s % 10),
        'client-' || (s % 20),
        'Test Client ' || (s % 20),
        ('00000000-0000-0000-1000-00000000' || lpad((s % 10000)::text, 4, '0'))::uuid,
        'user-' || (s % 10000),
        'external-' || (s % 5000),
        ('192.168.' || ((s % 256)) || '.' || ((s / 256) % 256))::inet,
        'Mozilla/5.0 (compatible; SecurityBot/' || (s % 10) || '.0)',
        jsonb_build_object(
            'subject', jsonb_build_object(
                'subject_type', 'iss-sub',
                'iss', 'https://idp.example.com',
                'sub', 'user-' || (s % 10000)
            ),
            'reason', 'Automated security check',
            'event_timestamp', extract(epoch from ('2024-01-01'::timestamp + (s % (365 * 24 * 60 * 60)) * interval '1 second'))
        ),
        '2024-01-01'::timestamp + ((s % (365 * 24 * 60 * 60)) * interval '1 second')
    FROM generate_series(1, ${ROW_COUNT}) s;
    "

    end_time=$(date +%s.%N)
    duration=$(echo "$end_time - $start_time" | bc)
    log_success "${table_name}: ${duration}秒で挿入完了"
}

insert_hook_results_data() {
    local table_name=$1
    local start_time end_time duration

    log_info "${table_name} に ${ROW_COUNT} 行を挿入中..."
    start_time=$(date +%s.%N)

    psql_exec "
    INSERT INTO ${table_name} (
        id, tenant_id, security_event_id, security_event_type,
        security_event_hook, security_event_payload,
        security_event_hook_execution_payload, status, created_at, updated_at
    )
    SELECT
        gen_random_uuid(),
        ('00000000-0000-0000-0000-00000000000' || (s % 10))::uuid,
        gen_random_uuid(),
        (ARRAY[
            'account-credential-change-required',
            'account-purged',
            'account-disabled',
            'account-enabled',
            'identifier-changed',
            'credential-compromise',
            'sessions-revoked',
            'session-revoked'
        ])[1 + (s % 8)],
        (ARRAY['ssf_transmitter', 'webhook', 'email_notification', 'siem_integration'])[1 + (s % 4)],
        jsonb_build_object(
            'event_id', gen_random_uuid(),
            'subject', 'user-' || (s % 10000),
            'timestamp', extract(epoch from NOW())
        ),
        CASE WHEN s % 10 < 8 THEN
            jsonb_build_object(
                'http_status', CASE WHEN s % 10 < 7 THEN 200 ELSE 500 END,
                'response_time_ms', 50 + (s % 200),
                'retry_count', s % 3
            )
        ELSE NULL END,
        (ARRAY['SUCCESS', 'FAILURE', 'PENDING', 'RETRYING'])[1 + (s % 4)],
        '2024-01-01'::timestamp + ((s % (365 * 24 * 60 * 60)) * interval '1 second'),
        '2024-01-01'::timestamp + ((s % (365 * 24 * 60 * 60)) * interval '1 second')
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
    ANALYZE security_event_normal;
    ANALYZE security_event_partitioned;
    ANALYZE security_event_hook_results_normal;
    ANALYZE security_event_hook_results_partitioned;
    "
    log_success "統計情報更新完了"
}

# ==============================================================================
# 性能テスト
# ==============================================================================
run_benchmark() {
    local query=$1
    local result

    result=$(psql_exec_quiet "EXPLAIN (ANALYZE, FORMAT JSON) ${query}" | grep -o '"Execution Time": [0-9.]*' | head -1 | grep -o '[0-9.]*')
    echo "$result"
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
    printf "%-40s %15.2f %15.2f %15s\n" "$test_name" "${normal:-0}" "${partitioned:-0}" "$winner"
}

run_security_event_benchmarks() {
    echo ""
    echo "=============================================="
    echo " security_event 性能比較テスト"
    echo "=============================================="
    echo ""

    local tenant_id=$(psql_exec_quiet "SELECT tenant_id FROM security_event_normal LIMIT 1" | tr -d ' ')

    # テスト1: 特定テナントの特定月のイベント数
    log_info "テスト1: 特定テナント・特定月のイベント数"
    local test1_normal=$(run_benchmark \
        "SELECT count(*) FROM security_event_normal WHERE tenant_id = '${tenant_id}' AND created_at >= '2024-03-01' AND created_at < '2024-04-01'")
    local test1_partitioned=$(run_benchmark \
        "SELECT count(*) FROM security_event_partitioned WHERE tenant_id = '${tenant_id}' AND created_at >= '2024-03-01' AND created_at < '2024-04-01'")

    # テスト2: イベントタイプ別集計（全期間）
    log_info "テスト2: イベントタイプ別集計（全期間）"
    local test2_normal=$(run_benchmark \
        "SELECT type, count(*) FROM security_event_normal WHERE tenant_id = '${tenant_id}' GROUP BY type ORDER BY count(*) DESC")
    local test2_partitioned=$(run_benchmark \
        "SELECT type, count(*) FROM security_event_partitioned WHERE tenant_id = '${tenant_id}' GROUP BY type ORDER BY count(*) DESC")

    # テスト3: 特定ユーザーのイベント履歴
    log_info "テスト3: 特定ユーザーのイベント履歴"
    local user_id=$(psql_exec_quiet "SELECT user_id FROM security_event_normal WHERE tenant_id = '${tenant_id}' AND user_id IS NOT NULL LIMIT 1" | tr -d ' ')
    local test3_normal=$(run_benchmark \
        "SELECT * FROM security_event_normal WHERE tenant_id = '${tenant_id}' AND user_id = '${user_id}' ORDER BY created_at DESC LIMIT 50")
    local test3_partitioned=$(run_benchmark \
        "SELECT * FROM security_event_partitioned WHERE tenant_id = '${tenant_id}' AND user_id = '${user_id}' ORDER BY created_at DESC LIMIT 50")

    # テスト4: 日付範囲+イベントタイプフィルタ
    log_info "テスト4: 日付範囲+イベントタイプフィルタ"
    local test4_normal=$(run_benchmark \
        "SELECT * FROM security_event_normal WHERE tenant_id = '${tenant_id}' AND created_at >= '2024-06-01' AND created_at < '2024-07-01' AND type LIKE '%credential%' LIMIT 100")
    local test4_partitioned=$(run_benchmark \
        "SELECT * FROM security_event_partitioned WHERE tenant_id = '${tenant_id}' AND created_at >= '2024-06-01' AND created_at < '2024-07-01' AND type LIKE '%credential%' LIMIT 100")

    # テスト5: 月別イベント推移
    log_info "テスト5: 月別イベント推移"
    local test5_normal=$(run_benchmark \
        "SELECT date_trunc('month', created_at) as month, count(*) FROM security_event_normal WHERE tenant_id = '${tenant_id}' GROUP BY 1 ORDER BY 1")
    local test5_partitioned=$(run_benchmark \
        "SELECT date_trunc('month', created_at) as month, count(*) FROM security_event_partitioned WHERE tenant_id = '${tenant_id}' GROUP BY 1 ORDER BY 1")

    # テスト6: IPアドレス別の不審アクセス検出
    log_info "テスト6: IPアドレス別アクセス集計（セキュリティ分析）"
    local test6_normal=$(run_benchmark \
        "SELECT ip_address, count(*) as event_count FROM security_event_normal WHERE tenant_id = '${tenant_id}' AND created_at >= '2024-06-01' AND created_at < '2024-07-01' GROUP BY ip_address HAVING count(*) > 5 ORDER BY event_count DESC LIMIT 20")
    local test6_partitioned=$(run_benchmark \
        "SELECT ip_address, count(*) as event_count FROM security_event_partitioned WHERE tenant_id = '${tenant_id}' AND created_at >= '2024-06-01' AND created_at < '2024-07-01' GROUP BY ip_address HAVING count(*) > 5 ORDER BY event_count DESC LIMIT 20")

    # 結果表示
    echo ""
    printf "%-40s %15s %15s %15s\n" "テスト" "通常(ms)" "パーティション(ms)" "勝者"
    printf "%-40s %15s %15s %15s\n" "----------------------------------------" "---------------" "---------------" "---------------"

    print_result "特定テナント・特定月のイベント数" "$test1_normal" "$test1_partitioned"
    print_result "イベントタイプ別集計（全期間）" "$test2_normal" "$test2_partitioned"
    print_result "特定ユーザーのイベント履歴" "$test3_normal" "$test3_partitioned"
    print_result "日付範囲+イベントタイプフィルタ" "$test4_normal" "$test4_partitioned"
    print_result "月別イベント推移" "$test5_normal" "$test5_partitioned"
    print_result "IPアドレス別アクセス集計" "$test6_normal" "$test6_partitioned"
}

run_hook_results_benchmarks() {
    echo ""
    echo "=============================================="
    echo " security_event_hook_results 性能比較テスト"
    echo "=============================================="
    echo ""

    local tenant_id=$(psql_exec_quiet "SELECT tenant_id FROM security_event_hook_results_normal LIMIT 1" | tr -d ' ')

    # テスト1: 失敗したHook結果の取得（リトライ対象）
    log_info "テスト1: 失敗したHook結果の取得"
    local test1_normal=$(run_benchmark \
        "SELECT * FROM security_event_hook_results_normal WHERE tenant_id = '${tenant_id}' AND status = 'FAILURE' AND created_at >= '2024-06-01' ORDER BY created_at DESC LIMIT 100")
    local test1_partitioned=$(run_benchmark \
        "SELECT * FROM security_event_hook_results_partitioned WHERE tenant_id = '${tenant_id}' AND status = 'FAILURE' AND created_at >= '2024-06-01' ORDER BY created_at DESC LIMIT 100")

    # テスト2: Hook種類別の成功率
    log_info "テスト2: Hook種類別の成功率"
    local test2_normal=$(run_benchmark \
        "SELECT security_event_hook, status, count(*) FROM security_event_hook_results_normal WHERE tenant_id = '${tenant_id}' GROUP BY security_event_hook, status ORDER BY security_event_hook, status")
    local test2_partitioned=$(run_benchmark \
        "SELECT security_event_hook, status, count(*) FROM security_event_hook_results_partitioned WHERE tenant_id = '${tenant_id}' GROUP BY security_event_hook, status ORDER BY security_event_hook, status")

    # テスト3: 特定期間のステータス集計
    log_info "テスト3: 特定期間のステータス集計"
    local test3_normal=$(run_benchmark \
        "SELECT status, count(*) FROM security_event_hook_results_normal WHERE tenant_id = '${tenant_id}' AND created_at >= '2024-03-01' AND created_at < '2024-04-01' GROUP BY status")
    local test3_partitioned=$(run_benchmark \
        "SELECT status, count(*) FROM security_event_hook_results_partitioned WHERE tenant_id = '${tenant_id}' AND created_at >= '2024-03-01' AND created_at < '2024-04-01' GROUP BY status")

    # テスト4: リトライ対象の取得（PENDING/RETRYING）
    log_info "テスト4: リトライ対象の取得"
    local test4_normal=$(run_benchmark \
        "SELECT * FROM security_event_hook_results_normal WHERE tenant_id = '${tenant_id}' AND status IN ('PENDING', 'RETRYING') AND created_at >= NOW() - interval '7 days' ORDER BY created_at LIMIT 100")
    local test4_partitioned=$(run_benchmark \
        "SELECT * FROM security_event_hook_results_partitioned WHERE tenant_id = '${tenant_id}' AND status IN ('PENDING', 'RETRYING') AND created_at >= NOW() - interval '7 days' ORDER BY created_at LIMIT 100")

    # 結果表示
    echo ""
    printf "%-40s %15s %15s %15s\n" "テスト" "通常(ms)" "パーティション(ms)" "勝者"
    printf "%-40s %15s %15s %15s\n" "----------------------------------------" "---------------" "---------------" "---------------"

    print_result "失敗したHook結果の取得" "$test1_normal" "$test1_partitioned"
    print_result "Hook種類別の成功率" "$test2_normal" "$test2_partitioned"
    print_result "特定期間のステータス集計" "$test3_normal" "$test3_partitioned"
    print_result "リトライ対象の取得" "$test4_normal" "$test4_partitioned"
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
        'security_event_normal' as table_name,
        pg_size_pretty(pg_table_size('security_event_normal')) as table_size,
        pg_size_pretty(pg_indexes_size('security_event_normal')) as index_size,
        pg_size_pretty(pg_total_relation_size('security_event_normal')) as total_size
    UNION ALL
    SELECT
        'security_event_partitioned',
        pg_size_pretty(COALESCE(sum(pg_table_size(inhrelid::regclass)), 0)),
        pg_size_pretty(COALESCE(sum(pg_indexes_size(inhrelid::regclass)), 0)),
        pg_size_pretty(COALESCE(sum(pg_total_relation_size(inhrelid::regclass)), 0))
    FROM pg_inherits WHERE inhparent = 'security_event_partitioned'::regclass
    UNION ALL
    SELECT
        'hook_results_normal',
        pg_size_pretty(pg_table_size('security_event_hook_results_normal')),
        pg_size_pretty(pg_indexes_size('security_event_hook_results_normal')),
        pg_size_pretty(pg_total_relation_size('security_event_hook_results_normal'))
    UNION ALL
    SELECT
        'hook_results_partitioned',
        pg_size_pretty(COALESCE(sum(pg_table_size(inhrelid::regclass)), 0)),
        pg_size_pretty(COALESCE(sum(pg_indexes_size(inhrelid::regclass)), 0)),
        pg_size_pretty(COALESCE(sum(pg_total_relation_size(inhrelid::regclass)), 0))
    FROM pg_inherits WHERE inhparent = 'security_event_hook_results_partitioned'::regclass;
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
    echo "--- security_event_partitioned ---"
    psql_exec "
    SELECT
        tableoid::regclass as partition_name,
        count(*) as row_count
    FROM security_event_partitioned
    GROUP BY tableoid
    ORDER BY partition_name;
    "

    echo ""
    echo "--- security_event_hook_results_partitioned ---"
    psql_exec "
    SELECT
        tableoid::regclass as partition_name,
        count(*) as row_count
    FROM security_event_hook_results_partitioned
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

    # security_event: 通常テーブルの削除
    log_info "security_event 通常テーブル: 1月分のデータを削除"
    local count_before=$(psql_exec_quiet "SELECT count(*) FROM security_event_normal WHERE created_at < '2024-02-01'" | tr -d ' ')
    local start_time=$(date +%s.%N)
    psql_exec "DELETE FROM security_event_normal WHERE created_at < '2024-02-01';" > /dev/null
    local end_time=$(date +%s.%N)
    local duration=$(echo "$end_time - $start_time" | bc)
    echo "  security_event 通常: ${count_before}行削除 → ${duration}秒"

    # security_event: パーティションテーブルの削除（DROP TABLE）
    log_info "security_event パーティション: 1月のパーティションをDROP"
    count_before=$(psql_exec_quiet "SELECT count(*) FROM security_event_partitioned_2024_01" | tr -d ' ')
    start_time=$(date +%s.%N)
    psql_exec "DROP TABLE security_event_partitioned_2024_01;" > /dev/null
    end_time=$(date +%s.%N)
    duration=$(echo "$end_time - $start_time" | bc)
    echo "  security_event パーティション: ${count_before}行削除(DROP) → ${duration}秒"

    # hook_results: 通常テーブルの削除
    log_info "hook_results 通常テーブル: 1月分のデータを削除"
    count_before=$(psql_exec_quiet "SELECT count(*) FROM security_event_hook_results_normal WHERE created_at < '2024-02-01'" | tr -d ' ')
    start_time=$(date +%s.%N)
    psql_exec "DELETE FROM security_event_hook_results_normal WHERE created_at < '2024-02-01';" > /dev/null
    end_time=$(date +%s.%N)
    duration=$(echo "$end_time - $start_time" | bc)
    echo "  hook_results 通常: ${count_before}行削除 → ${duration}秒"

    # hook_results: パーティションテーブルの削除（DROP TABLE）
    log_info "hook_results パーティション: 1月のパーティションをDROP"
    count_before=$(psql_exec_quiet "SELECT count(*) FROM security_event_hook_results_partitioned_2024_01" | tr -d ' ')
    start_time=$(date +%s.%N)
    psql_exec "DROP TABLE security_event_hook_results_partitioned_2024_01;" > /dev/null
    end_time=$(date +%s.%N)
    duration=$(echo "$end_time - $start_time" | bc)
    echo "  hook_results パーティション: ${count_before}行削除(DROP) → ${duration}秒"
}

# ==============================================================================
# クリーンアップ
# ==============================================================================
cleanup() {
    log_info "テストテーブルを削除中..."
    psql_exec "
    DROP TABLE IF EXISTS security_event_normal CASCADE;
    DROP TABLE IF EXISTS security_event_partitioned CASCADE;
    DROP TABLE IF EXISTS security_event_hook_results_normal CASCADE;
    DROP TABLE IF EXISTS security_event_hook_results_partitioned CASCADE;
    " > /dev/null 2>&1
    log_success "クリーンアップ完了"
}

# ==============================================================================
# ヘルプ
# ==============================================================================
show_help() {
    echo "PostgreSQL パーティションテーブル性能比較スクリプト"
    echo "対象: security_event, security_event_hook_results"
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
    echo " PostgreSQL Security Event パーティション性能比較"
    echo "=============================================="
    echo " 行数: ${ROW_COUNT}"
    echo " コンテナ: ${CONTAINER_NAME}"
    echo " データベース: ${DB_NAME}"
    echo "=============================================="
    echo ""

    check_container

    # テーブル作成
    create_security_event_tables
    create_hook_results_tables

    # データ挿入
    insert_security_event_data "security_event_normal"
    insert_security_event_data "security_event_partitioned"
    insert_hook_results_data "security_event_hook_results_normal"
    insert_hook_results_data "security_event_hook_results_partitioned"

    # 統計更新
    analyze_tables

    # サイズ表示
    show_table_sizes
    show_partition_distribution

    # ベンチマーク実行
    run_security_event_benchmarks
    run_hook_results_benchmarks

    # 削除テスト
    run_delete_benchmark

    echo ""
    log_success "ベンチマーク完了"
    echo ""
    echo "クリーンアップするには: $0 --cleanup"
}

main "$@"
