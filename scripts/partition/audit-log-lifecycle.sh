#!/usr/bin/env bash
#
# PostgreSQL 監査ログ パーティションライフサイクルシミュレーション
# 対象: audit_log
#
# 実運用を想定した以下のサイクルをシミュレート:
# 1. 新規パーティション作成（月初のcron job想定）
# 2. データ挿入（日次のアプリケーション動作想定）
# 3. 古いパーティション削除（月次のcron job想定）
#
# 使用方法:
#   ./scripts/partition/audit-log-lifecycle.sh [サイクル数]
#
# 例:
#   ./scripts/partition/audit-log-lifecycle.sh       # デフォルト: 12サイクル（1年分）
#   ./scripts/partition/audit-log-lifecycle.sh 24    # 24サイクル（2年分）
#

# set -e を使わない（return 2 = 警告でスクリプトが終了してしまうため）

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
    log_info "監査ログ パーティションテーブルを初期化中..."

    psql_exec "
    -- 既存テーブルを削除
    DROP TABLE IF EXISTS audit_log_sim CASCADE;

    -- =====================================================
    -- audit_log_sim（月別パーティション）
    -- =====================================================
    CREATE TABLE audit_log_sim (
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

    -- DEFAULTパーティション: 対応するパーティションがない場合の安全ネット
    CREATE TABLE audit_log_sim_default PARTITION OF audit_log_sim DEFAULT;

    -- インデックス
    CREATE INDEX idx_audit_log_sim_tenant_created ON audit_log_sim(tenant_id, created_at);
    CREATE INDEX idx_audit_log_sim_event_type ON audit_log_sim(event_type);
    CREATE INDEX idx_audit_log_sim_user_id ON audit_log_sim(user_id);
    "

    log_success "監査ログ テーブル初期化完了（DEFAULTパーティション付き）"
}

# ==============================================================================
# パーティション作成（月初のcron job想定）
# ==============================================================================
create_partition() {
    local year_month=$1  # YYYY-MM形式
    local year=${year_month:0:4}
    local month=${year_month:5:2}
    local partition_name="audit_log_sim_${year}_${month}"

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
    CREATE TABLE ${partition_name} PARTITION OF audit_log_sim
    FOR VALUES FROM ('${start_date}') TO ('${next_month}');
    " > /dev/null 2>&1

    log_cron "作成: ${partition_name} (${start_date} ~ ${next_month})"
}

# ==============================================================================
# データ挿入（日次のアプリケーション動作想定）
# ==============================================================================

# 監査ログイベントタイプ
EVENT_TYPES=(
    "login_success"
    "login_failure"
    "logout"
    "token_issued"
    "token_revoked"
    "password_changed"
    "mfa_enabled"
    "mfa_disabled"
    "session_created"
    "session_expired"
    "consent_granted"
    "consent_revoked"
    "user_created"
    "user_updated"
    "user_deleted"
)

insert_audit_logs_for_month() {
    local year_month=$1  # YYYY-MM形式
    local result
    local exit_code

    # 月の全日数分のデータを一括挿入
    result=$(psql_exec_with_error "
    INSERT INTO audit_log_sim (
        id, event_type, description, tenant_id, tenant_name,
        client_id, client_name, user_id, user_name, server_id,
        ip_address, user_agent, detail, created_at
    )
    SELECT
        gen_random_uuid(),
        (ARRAY['login_success', 'login_failure', 'logout', 'token_issued',
               'token_revoked', 'password_changed', 'mfa_enabled', 'mfa_disabled',
               'session_created', 'session_expired', 'consent_granted', 'consent_revoked',
               'user_created', 'user_updated', 'user_deleted'])[1 + (s % 15)],
        'User action performed',
        ('00000000-0000-0000-0000-00000000000' || (s % 10))::uuid,
        'tenant-' || (s % 10),
        'client-' || (s % 100),
        'Test Client ' || (s % 100),
        gen_random_uuid(),
        'user-' || (s % 1000),
        'server-' || (s % 5),
        '192.168.' || (s % 256) || '.' || ((s * 7) % 256),
        'Mozilla/5.0 (compatible; AuditBot/' || (s % 10) || '.0)',
        jsonb_build_object(
            'action', (ARRAY['POST', 'GET', 'PUT', 'DELETE'])[1 + (s % 4)],
            'resource', '/api/v1/resource-' || (s % 50),
            'request_id', gen_random_uuid()::text,
            'duration_ms', (random() * 1000)::int
        ),
        d.day_date + (random() * interval '24 hours')
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
        log_error "audit_log挿入失敗 (${year_month})"
        echo "$result" | grep -i "error" | head -3
        return 1
    fi

    # DEFAULTパーティションに入ったかチェック
    if [ "${SKIP_PARTITION_CREATION:-0}" -eq 1 ]; then
        local default_count=$(psql_exec_quiet "SELECT count(*) FROM audit_log_sim_default WHERE created_at >= '${year_month}-01' AND created_at < '${year_month}-01'::date + interval '1 month'")
        if [ "${default_count:-0}" -gt 0 ]; then
            log_warn "audit_log: ${default_count}行がDEFAULTパーティションに格納 (${year_month})"
            return 2
        fi
    fi

    return 0
}

# ==============================================================================
# パーティション削除（月次のcron job想定）
# ==============================================================================
drop_partition() {
    local year_month=$1  # YYYY-MM形式
    local year=${year_month:0:4}
    local month=${year_month:5:2}
    local partition_name="audit_log_sim_${year}_${month}"

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
    WHERE parent.relname = 'audit_log_sim'
    ORDER BY child.relname;
    "
}

show_summary() {
    echo ""
    echo "--- サマリー ---"
    psql_exec "
    SELECT
        'audit_log_sim' as table_name,
        count(*) as total_rows,
        pg_size_pretty(pg_total_relation_size('audit_log_sim')) as total_size
    FROM audit_log_sim;
    "
}

# DEFAULTパーティションの状態確認
show_default_partition_status() {
    echo ""
    echo "--- DEFAULTパーティション状態 ---"

    local default_count=$(psql_exec_quiet "SELECT count(*) FROM audit_log_sim_default")

    echo "  audit_log_sim_default: ${default_count:-0} 行"

    if [ "${default_count:-0}" -gt 0 ]; then
        echo ""
        log_warn "DEFAULTパーティションにデータが存在します（${default_count}行）"
        echo ""
        echo "💡 DEFAULTパーティションのデータは以下の理由で発生:"
        echo "   - 対応する期間のパーティションが作成されていない"
        echo "   - cronジョブが失敗してパーティション作成が漏れた"
        echo ""
        echo "🔧 対処方法:"
        echo "   1. 適切なパーティションを作成"
        echo "   2. DEFAULTパーティションからデータを移動:"
        echo "      -- 例: 2024-01のデータを移動"
        echo "      INSERT INTO audit_log_sim_2024_01"
        echo "      SELECT * FROM audit_log_sim_default"
        echo "      WHERE created_at >= '2024-01-01' AND created_at < '2024-02-01';"
        echo "      DELETE FROM audit_log_sim_default"
        echo "      WHERE created_at >= '2024-01-01' AND created_at < '2024-02-01';"

        echo ""
        echo "📊 audit_log_sim_default のタイムスタンプ範囲:"
        psql_exec "SELECT min(created_at) as min_ts, max(created_at) as max_ts FROM audit_log_sim_default;"
    else
        log_success "DEFAULTパーティションにデータなし（正常）"
    fi
}

# イベントタイプ別統計
show_event_type_stats() {
    echo ""
    echo "--- イベントタイプ別統計 ---"
    psql_exec "
    SELECT
        event_type,
        count(*) as count,
        round(100.0 * count(*) / sum(count(*)) over(), 2) as percentage
    FROM audit_log_sim
    GROUP BY event_type
    ORDER BY count DESC
    LIMIT 10;
    "
}

# テナント別統計
show_tenant_stats() {
    echo ""
    echo "--- テナント別統計 ---"
    psql_exec "
    SELECT
        tenant_name,
        count(*) as count,
        round(100.0 * count(*) / sum(count(*)) over(), 2) as percentage
    FROM audit_log_sim
    GROUP BY tenant_name
    ORDER BY count DESC
    LIMIT 10;
    "
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
    echo " 監査ログ パーティションライフサイクル"
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
            create_partition "${current_ym}"
        fi

        # ========================================
        # 2. 月中: データ挿入（月単位で一括挿入）
        # ========================================
        echo ""
        echo "📊 アプリケーション: 監査ログ挿入中..."

        local insert_start=$(date +%s.%N)
        local insert_status=0

        insert_audit_logs_for_month "${current_ym}"
        insert_status=$?
        if [ $insert_status -eq 1 ]; then
            ((TOTAL_ERRORS++)) || true
        fi

        local insert_end=$(date +%s.%N)
        local insert_duration=$(echo "$insert_end - $insert_start" | bc)

        # 挿入された行数を確認
        local row_count=$(psql_exec_quiet "SELECT count(*) FROM audit_log_sim WHERE created_at >= '${current_ym}-01' AND created_at < '${current_ym}-01'::date + interval '1 month'")

        # status: 0=成功, 1=エラー, 2=警告（DEFAULTに格納）
        if [ $insert_status -eq 0 ]; then
            log_success "挿入完了: ${row_count}行 (${insert_duration}秒)"
        elif [ $insert_status -eq 1 ]; then
            log_error "挿入失敗: ${row_count}行 (${insert_duration}秒)"
        else
            log_warn "挿入完了（DEFAULTパーティション使用）: ${row_count}行 (${insert_duration}秒)"
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

            drop_partition "${delete_ym}"
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
    DROP TABLE IF EXISTS audit_log_sim CASCADE;
    " > /dev/null 2>&1
    log_success "クリーンアップ完了"
}

# ==============================================================================
# 実運用向けcronスクリプト生成
# ==============================================================================
generate_cron_scripts() {
    echo ""
    echo "=============================================="
    echo " 監査ログ用 cronスクリプト例"
    echo "=============================================="

    cat << 'CRON_SCRIPT'

# ===========================================
# /etc/cron.d/audit-log-partition
# ===========================================

# 毎月1日 AM 2:00: 新規パーティション作成
0 2 1 * * postgres /opt/scripts/create-audit-log-partitions.sh >> /var/log/audit-log-partition.log 2>&1

# 毎月1日 AM 3:00: 古いパーティション削除
0 3 1 * * postgres /opt/scripts/drop-old-audit-log-partitions.sh >> /var/log/audit-log-partition.log 2>&1

# ===========================================
# /opt/scripts/create-audit-log-partitions.sh
# ===========================================
#!/bin/bash
set -e

PGPASSWORD="${DB_PASSWORD}" psql -h localhost -U idpserver -d idpserver << 'SQL'
DO $$
DECLARE
    next_month DATE := date_trunc('month', CURRENT_DATE + interval '1 month');
    partition_name TEXT;
BEGIN
    -- audit_log: 来月分のパーティション作成
    partition_name := 'audit_log_' || to_char(next_month, 'YYYY_MM');
    IF NOT EXISTS (SELECT 1 FROM pg_tables WHERE tablename = partition_name) THEN
        EXECUTE format(
            'CREATE TABLE %I PARTITION OF audit_log FOR VALUES FROM (%L) TO (%L)',
            partition_name,
            next_month,
            next_month + interval '1 month'
        );
        RAISE NOTICE 'Created partition: %', partition_name;
    END IF;
END $$;
SQL

# ===========================================
# /opt/scripts/drop-old-audit-log-partitions.sh
# ===========================================
#!/bin/bash
set -e

RETENTION_MONTHS=12  # 監査ログ保持期間（コンプライアンス要件に応じて調整）

PGPASSWORD="${DB_PASSWORD}" psql -h localhost -U idpserver -d idpserver << SQL
DO \$\$
DECLARE
    cutoff_date DATE := date_trunc('month', CURRENT_DATE - interval '${RETENTION_MONTHS} months');
    r RECORD;
BEGIN
    -- audit_log: 保持期間を超えたパーティションを削除
    FOR r IN
        SELECT child.relname
        FROM pg_inherits
        JOIN pg_class parent ON pg_inherits.inhparent = parent.oid
        JOIN pg_class child ON pg_inherits.inhrelid = child.oid
        WHERE parent.relname = 'audit_log'
        AND child.relname ~ '^audit_log_[0-9]{4}_[0-9]{2}$'
        AND child.relname < 'audit_log_' || to_char(cutoff_date, 'YYYY_MM')
    LOOP
        EXECUTE format('DROP TABLE %I', r.relname);
        RAISE NOTICE 'Dropped partition: %', r.relname;
    END LOOP;
END \$\$;
SQL

# ===========================================
# 監視スクリプト: DEFAULTパーティション監視
# ===========================================
#!/bin/bash
# /opt/scripts/monitor-audit-log-default-partition.sh
# cronで1時間ごとに実行してアラート

ALERT_THRESHOLD=100  # DEFAULTパーティションにこれ以上のレコードがあればアラート

result=$(PGPASSWORD="${DB_PASSWORD}" psql -h localhost -U idpserver -d idpserver -t -c "
SELECT COALESCE((SELECT count(*) FROM audit_log_default), 0) as total_default;
")

if [ "$result" -gt "$ALERT_THRESHOLD" ]; then
    echo "ALERT: audit_log DEFAULTパーティションに ${result} レコードが存在します"
    # ここにSlack/PagerDuty等の通知処理を追加
fi

CRON_SCRIPT

    echo ""
    log_info "上記のスクリプトを参考に実運用環境を構築してください"
    echo ""
    echo "⚠️  監査ログの保持期間はコンプライアンス要件に応じて設定:"
    echo "   - 一般的な企業ポリシー: 1年以上"
    echo "   - PCI DSS: 1年以上（オンラインアクセス: 3ヶ月）"
    echo "   - SOX法: 7年以上"
    echo "   - GDPR: 必要最小限（目的達成後は削除）"
    echo "   - 金融機関: 7〜10年"
}

# ==============================================================================
# ヘルプ
# ==============================================================================
show_help() {
    echo "PostgreSQL 監査ログ パーティションライフサイクルシミュレーション"
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
    show_event_type_stats
    show_tenant_stats
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
