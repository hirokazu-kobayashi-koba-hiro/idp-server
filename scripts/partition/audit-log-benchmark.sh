#!/usr/bin/env bash
#
# PostgreSQL パーティションテーブル vs 通常テーブル 性能比較スクリプト
# 対象: audit_log
#
# 使用方法:
#   ./scripts/partition/audit-log-benchmark.sh [行数]
#
# 例:
#   ./scripts/partition/audit-log-benchmark.sh          # デフォルト: 100万行
#   ./scripts/partition/audit-log-benchmark.sh 500000   # 50万行
#   ./scripts/partition/audit-log-benchmark.sh 5000000  # 500万行
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

# テーブル作成
create_tables() {
    log_info "テスト用テーブルを作成中..."

    psql_exec "
    -- 既存テーブルを削除
    DROP TABLE IF EXISTS audit_log_normal CASCADE;
    DROP TABLE IF EXISTS audit_log_partitioned CASCADE;

    -- 1. 通常テーブル（パーティションなし）
    CREATE TABLE audit_log_normal (
        id UUID PRIMARY KEY,
        event_type VARCHAR(100) NOT NULL,
        description TEXT,
        tenant_id UUID NOT NULL,
        tenant_name VARCHAR(255),
        client_id VARCHAR(255),
        client_name VARCHAR(255),
        user_id UUID,
        user_name VARCHAR(255),
        server_id VARCHAR(255),
        ip_address VARCHAR(45),
        user_agent VARCHAR(512),
        detail JSONB,
        created_at TIMESTAMP NOT NULL
    );

    -- 2. パーティションテーブル（月別）
    CREATE TABLE audit_log_partitioned (
        id UUID NOT NULL,
        event_type VARCHAR(100) NOT NULL,
        description TEXT,
        tenant_id UUID NOT NULL,
        tenant_name VARCHAR(255),
        client_id VARCHAR(255),
        client_name VARCHAR(255),
        user_id UUID,
        user_name VARCHAR(255),
        server_id VARCHAR(255),
        ip_address VARCHAR(45),
        user_agent VARCHAR(512),
        detail JSONB,
        created_at TIMESTAMP NOT NULL,
        PRIMARY KEY (id, created_at)
    ) PARTITION BY RANGE (created_at);

    -- 2024年のパーティション作成
    CREATE TABLE audit_log_partitioned_2024_01 PARTITION OF audit_log_partitioned FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');
    CREATE TABLE audit_log_partitioned_2024_02 PARTITION OF audit_log_partitioned FOR VALUES FROM ('2024-02-01') TO ('2024-03-01');
    CREATE TABLE audit_log_partitioned_2024_03 PARTITION OF audit_log_partitioned FOR VALUES FROM ('2024-03-01') TO ('2024-04-01');
    CREATE TABLE audit_log_partitioned_2024_04 PARTITION OF audit_log_partitioned FOR VALUES FROM ('2024-04-01') TO ('2024-05-01');
    CREATE TABLE audit_log_partitioned_2024_05 PARTITION OF audit_log_partitioned FOR VALUES FROM ('2024-05-01') TO ('2024-06-01');
    CREATE TABLE audit_log_partitioned_2024_06 PARTITION OF audit_log_partitioned FOR VALUES FROM ('2024-06-01') TO ('2024-07-01');
    CREATE TABLE audit_log_partitioned_2024_07 PARTITION OF audit_log_partitioned FOR VALUES FROM ('2024-07-01') TO ('2024-08-01');
    CREATE TABLE audit_log_partitioned_2024_08 PARTITION OF audit_log_partitioned FOR VALUES FROM ('2024-08-01') TO ('2024-09-01');
    CREATE TABLE audit_log_partitioned_2024_09 PARTITION OF audit_log_partitioned FOR VALUES FROM ('2024-09-01') TO ('2024-10-01');
    CREATE TABLE audit_log_partitioned_2024_10 PARTITION OF audit_log_partitioned FOR VALUES FROM ('2024-10-01') TO ('2024-11-01');
    CREATE TABLE audit_log_partitioned_2024_11 PARTITION OF audit_log_partitioned FOR VALUES FROM ('2024-11-01') TO ('2024-12-01');
    CREATE TABLE audit_log_partitioned_2024_12 PARTITION OF audit_log_partitioned FOR VALUES FROM ('2024-12-01') TO ('2025-01-01');

    -- インデックス作成（公平な比較のため同じ構成）
    CREATE INDEX idx_normal_tenant_created ON audit_log_normal(tenant_id, created_at);
    CREATE INDEX idx_normal_event_type ON audit_log_normal(event_type);
    CREATE INDEX idx_normal_user_id ON audit_log_normal(user_id);

    CREATE INDEX idx_partitioned_tenant_created ON audit_log_partitioned(tenant_id, created_at);
    CREATE INDEX idx_partitioned_event_type ON audit_log_partitioned(event_type);
    CREATE INDEX idx_partitioned_user_id ON audit_log_partitioned(user_id);
    "

    log_success "テーブル作成完了"
}

# データ挿入
insert_data() {
    local table_name=$1
    local start_time end_time duration

    log_info "${table_name} に ${ROW_COUNT} 行を挿入中..."
    start_time=$(date +%s.%N)

    psql_exec "
    WITH event_types AS (
        SELECT unnest(ARRAY['login_success', 'login_failure', 'logout', 'token_issued', 'token_revoked', 'password_changed', 'mfa_enabled', 'mfa_disabled', 'session_created', 'session_expired']) AS event_type
    ),
    tenants AS (
        SELECT gen_random_uuid() AS tenant_id, 'tenant-' || n AS tenant_name
        FROM generate_series(1, 5) n
    ),
    users AS (
        SELECT gen_random_uuid() AS user_id, 'user-' || n AS user_name
        FROM generate_series(1, 1000) n
    )
    INSERT INTO ${table_name} (
        id, event_type, description, tenant_id, tenant_name,
        client_id, client_name, user_id, user_name, server_id,
        ip_address, user_agent, detail, created_at
    )
    SELECT
        gen_random_uuid(),
        (SELECT event_type FROM event_types ORDER BY random() LIMIT 1),
        'User action performed',
        t.tenant_id, t.tenant_name,
        'client-' || (random() * 10)::int,
        'Test Client ' || (random() * 10)::int,
        u.user_id, u.user_name,
        'server-' || (random() * 3)::int,
        '192.168.1.' || (random() * 255)::int,
        'curl/8.7.1',
        jsonb_build_object(
            'user', jsonb_build_object('sub', u.user_id::text, 'email', u.user_name || '@example.com'),
            'action', 'POST',
            'resource', '/' || t.tenant_id::text || '/v1/tokens',
            'ip_address', '192.168.1.' || (random() * 255)::int
        ),
        '2024-01-01'::timestamp + (random() * 365 * 24 * 60 * 60) * interval '1 second'
    FROM generate_series(1, ${ROW_COUNT}) s
    CROSS JOIN LATERAL (SELECT * FROM tenants ORDER BY random() LIMIT 1) t
    CROSS JOIN LATERAL (SELECT * FROM users ORDER BY random() LIMIT 1) u;
    "

    end_time=$(date +%s.%N)
    duration=$(echo "$end_time - $start_time" | bc)
    log_success "${table_name}: ${duration}秒で挿入完了"
}

# 統計情報更新
analyze_tables() {
    log_info "統計情報を更新中..."
    psql_exec "ANALYZE audit_log_normal; ANALYZE audit_log_partitioned;"
    log_success "統計情報更新完了"
}

# 性能テスト実行
run_benchmark() {
    local test_name=$1
    local table_name=$2
    local query=$3
    local result

    result=$(psql_exec_quiet "EXPLAIN (ANALYZE, FORMAT JSON) ${query}" | grep -o '"Execution Time": [0-9.]*' | head -1 | grep -o '[0-9.]*')
    echo "$result"
}

# メイン性能テスト
run_all_benchmarks() {
    echo ""
    echo "=============================================="
    echo " 性能比較テスト開始"
    echo "=============================================="
    echo ""

    # テスト1: 特定月のCOUNT
    log_info "テスト1: 特定月のCOUNT"
    local test1_normal=$(run_benchmark "test1" "audit_log_normal" \
        "SELECT count(*) FROM audit_log_normal WHERE created_at >= '2024-03-01' AND created_at < '2024-04-01'")
    local test1_partitioned=$(run_benchmark "test1" "audit_log_partitioned" \
        "SELECT count(*) FROM audit_log_partitioned WHERE created_at >= '2024-03-01' AND created_at < '2024-04-01'")

    # テスト2: 全期間GROUP BY
    log_info "テスト2: 全期間GROUP BY (event_type)"
    local test2_normal=$(run_benchmark "test2" "audit_log_normal" \
        "SELECT event_type, count(*) FROM audit_log_normal GROUP BY event_type")
    local test2_partitioned=$(run_benchmark "test2" "audit_log_partitioned" \
        "SELECT event_type, count(*) FROM audit_log_partitioned GROUP BY event_type")

    # テスト3: 日付範囲+条件フィルタ
    log_info "テスト3: 日付範囲+条件フィルタ"
    local test3_normal=$(run_benchmark "test3" "audit_log_normal" \
        "SELECT * FROM audit_log_normal WHERE created_at >= '2024-06-01' AND created_at < '2024-07-01' AND event_type = 'login_success' LIMIT 100")
    local test3_partitioned=$(run_benchmark "test3" "audit_log_partitioned" \
        "SELECT * FROM audit_log_partitioned WHERE created_at >= '2024-06-01' AND created_at < '2024-07-01' AND event_type = 'login_success' LIMIT 100")

    # テスト4: 月別集計
    log_info "テスト4: 月別集計"
    local test4_normal=$(run_benchmark "test4" "audit_log_normal" \
        "SELECT date_trunc('month', created_at) as month, event_type, count(*) FROM audit_log_normal GROUP BY 1, 2 ORDER BY 1, 3 DESC")
    local test4_partitioned=$(run_benchmark "test4" "audit_log_partitioned" \
        "SELECT date_trunc('month', created_at) as month, event_type, count(*) FROM audit_log_partitioned GROUP BY 1, 2 ORDER BY 1, 3 DESC")

    # 結果表示
    echo ""
    echo "=============================================="
    echo " 性能比較結果"
    echo "=============================================="
    echo ""
    printf "%-30s %15s %15s %10s\n" "テスト" "通常(ms)" "パーティション(ms)" "勝者"
    printf "%-30s %15s %15s %10s\n" "------------------------------" "---------------" "---------------" "----------"

    # テスト1結果
    local winner1="パーティション"
    if [ -n "$test1_normal" ] && [ -n "$test1_partitioned" ]; then
        if (( $(echo "$test1_normal < $test1_partitioned" | bc -l) )); then
            winner1="通常"
        fi
    fi
    printf "%-30s %15.2f %15.2f %10s\n" "特定月のCOUNT" "${test1_normal:-0}" "${test1_partitioned:-0}" "$winner1"

    # テスト2結果
    local winner2="パーティション"
    if [ -n "$test2_normal" ] && [ -n "$test2_partitioned" ]; then
        if (( $(echo "$test2_normal < $test2_partitioned" | bc -l) )); then
            winner2="通常"
        fi
    fi
    printf "%-30s %15.2f %15.2f %10s\n" "全期間GROUP BY" "${test2_normal:-0}" "${test2_partitioned:-0}" "$winner2"

    # テスト3結果
    local winner3="パーティション"
    if [ -n "$test3_normal" ] && [ -n "$test3_partitioned" ]; then
        if (( $(echo "$test3_normal < $test3_partitioned" | bc -l) )); then
            winner3="通常"
        fi
    fi
    printf "%-30s %15.2f %15.2f %10s\n" "日付範囲+条件フィルタ" "${test3_normal:-0}" "${test3_partitioned:-0}" "$winner3"

    # テスト4結果
    local winner4="パーティション"
    if [ -n "$test4_normal" ] && [ -n "$test4_partitioned" ]; then
        if (( $(echo "$test4_normal < $test4_partitioned" | bc -l) )); then
            winner4="通常"
        fi
    fi
    printf "%-30s %15.2f %15.2f %10s\n" "月別集計" "${test4_normal:-0}" "${test4_partitioned:-0}" "$winner4"

    echo ""
}

# テーブルサイズ表示
show_table_sizes() {
    echo ""
    echo "=============================================="
    echo " テーブルサイズ"
    echo "=============================================="
    echo ""

    psql_exec "
    SELECT
        'audit_log_normal' as table_name,
        pg_size_pretty(pg_table_size('audit_log_normal')) as table_size,
        pg_size_pretty(pg_indexes_size('audit_log_normal')) as index_size,
        pg_size_pretty(pg_total_relation_size('audit_log_normal')) as total_size
    UNION ALL
    SELECT
        'audit_log_partitioned',
        pg_size_pretty(sum(pg_table_size(inhrelid::regclass))),
        pg_size_pretty(sum(pg_indexes_size(inhrelid::regclass))),
        pg_size_pretty(sum(pg_total_relation_size(inhrelid::regclass)))
    FROM pg_inherits WHERE inhparent = 'audit_log_partitioned'::regclass;
    "
}

# パーティション分布表示
show_partition_distribution() {
    echo ""
    echo "=============================================="
    echo " パーティション別データ分布"
    echo "=============================================="
    echo ""

    psql_exec "
    SELECT
        tableoid::regclass as partition_name,
        count(*) as row_count
    FROM audit_log_partitioned
    GROUP BY tableoid
    ORDER BY partition_name;
    "
}

# 削除テスト
run_delete_benchmark() {
    echo ""
    echo "=============================================="
    echo " 削除性能テスト"
    echo "=============================================="
    echo ""

    # 通常テーブルの削除
    log_info "通常テーブル: 1月分のデータを削除"
    local count_before=$(psql_exec_quiet "SELECT count(*) FROM audit_log_normal WHERE created_at < '2024-02-01'" | tr -d ' ')
    local start_time=$(date +%s.%N)
    psql_exec "DELETE FROM audit_log_normal WHERE created_at < '2024-02-01';" > /dev/null
    local end_time=$(date +%s.%N)
    local duration=$(echo "$end_time - $start_time" | bc)
    echo "  通常テーブル: ${count_before}行削除 → ${duration}秒"

    # パーティションテーブルの削除（DROP TABLE）
    log_info "パーティションテーブル: 1月のパーティションをDROP"
    count_before=$(psql_exec_quiet "SELECT count(*) FROM audit_log_partitioned_2024_01" | tr -d ' ')
    start_time=$(date +%s.%N)
    psql_exec "DROP TABLE audit_log_partitioned_2024_01;" > /dev/null
    end_time=$(date +%s.%N)
    duration=$(echo "$end_time - $start_time" | bc)
    echo "  パーティション: ${count_before}行削除(DROP) → ${duration}秒"
}

# クリーンアップ
cleanup() {
    log_info "テストテーブルを削除中..."
    psql_exec "DROP TABLE IF EXISTS audit_log_normal CASCADE; DROP TABLE IF EXISTS audit_log_partitioned CASCADE;" > /dev/null 2>&1
    log_success "クリーンアップ完了"
}

# ヘルプ表示
show_help() {
    echo "PostgreSQL パーティションテーブル性能比較スクリプト"
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

# メイン処理
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
    echo " PostgreSQL パーティション性能比較"
    echo "=============================================="
    echo " 行数: ${ROW_COUNT}"
    echo " コンテナ: ${CONTAINER_NAME}"
    echo " データベース: ${DB_NAME}"
    echo "=============================================="
    echo ""

    check_container
    create_tables

    insert_data "audit_log_normal"
    insert_data "audit_log_partitioned"

    analyze_tables
    show_table_sizes
    show_partition_distribution
    run_all_benchmarks
    run_delete_benchmark

    echo ""
    log_success "ベンチマーク完了"
    echo ""
    echo "クリーンアップするには: $0 --cleanup"
}

main "$@"
