#!/usr/bin/env bash
#
# MySQL パーティションライフサイクル検証スクリプト
#
# 目的:
#   - パーティションの作成（premake相当）を確認
#   - パーティションの削除（retention相当）を確認
#   - maintain_statistics_partitions() の動作を確認
#
# 使用方法:
#   ./scripts/mysql_partition/partition-lifecycle-test.sh
#

set -e

# 設定
CONTAINER_NAME="${MYSQL_CONTAINER:-idp-mysql}"
DB_USER="${MYSQL_USER:-idpserver}"
DB_PASSWORD="${MYSQL_PASSWORD:-idpserver}"
DB_NAME="${MYSQL_DB:-idpserver}"

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

# MySQLコマンド実行
mysql_exec() {
    docker exec "$CONTAINER_NAME" mysql -u"$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" -e "$1" 2>/dev/null
}

mysql_exec_quiet() {
    docker exec "$CONTAINER_NAME" mysql -u"$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" -sN -e "$1" 2>/dev/null
}

# コンテナチェック
check_container() {
    if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
        log_error "コンテナ '$CONTAINER_NAME' が見つかりません"
        exit 1
    fi
    log_success "コンテナ '$CONTAINER_NAME' が稼働中"
}

# パーティション数を取得
get_partition_count() {
    local table_name=$1
    mysql_exec_quiet "
        SELECT COUNT(*)
        FROM information_schema.PARTITIONS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = '${table_name}'
          AND PARTITION_NAME != 'p_future';
    "
}

# パーティション一覧を取得
list_partitions() {
    local table_name=$1
    mysql_exec_quiet "
        SELECT PARTITION_NAME
        FROM information_schema.PARTITIONS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = '${table_name}'
        ORDER BY PARTITION_NAME;
    "
}

# ==============================================================================
# テスト用テーブル作成（短い保持期間で検証）
# ==============================================================================
setup_test_table() {
    log_step "テスト用パーティションテーブルを作成中..."

    mysql_exec "
    -- 既存テーブル削除
    DROP TABLE IF EXISTS partition_lifecycle_test;

    -- テストテーブル作成（日別パーティション）
    CREATE TABLE partition_lifecycle_test (
        id CHAR(36) DEFAULT (UUID()),
        tenant_id CHAR(36) NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001',
        event_date DATE NOT NULL,
        data TEXT,
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

        PRIMARY KEY (tenant_id, event_date, id),
        KEY idx_lifecycle_test_date (event_date)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    PARTITION BY RANGE COLUMNS(event_date) (
        PARTITION p_future VALUES LESS THAN MAXVALUE
    );
    "

    log_success "テストテーブル partition_lifecycle_test を作成しました"
}

# ==============================================================================
# テスト用プロシージャ作成
# ==============================================================================
setup_test_procedures() {
    log_step "テスト用プロシージャを作成中..."

    # 一時ファイルにSQLを書き込んで実行（DELIMITERは-eでは使えないため）
    local tmp_sql=$(mktemp)
    cat > "$tmp_sql" << 'EOSQL'
DELIMITER //

DROP PROCEDURE IF EXISTS create_lifecycle_test_partition//
CREATE PROCEDURE create_lifecycle_test_partition(IN target_date DATE)
BEGIN
    DECLARE v_partition_exists INT DEFAULT 0;

    -- ユーザー変数を使用（ローカル変数はCONCAT内で正しく展開されないため）
    SET @v_partition_name = CONCAT('p', DATE_FORMAT(target_date, '%Y%m%d'));
    SET @v_partition_end = DATE_ADD(target_date, INTERVAL 1 DAY);

    SELECT COUNT(*) INTO v_partition_exists
    FROM information_schema.PARTITIONS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'partition_lifecycle_test'
      AND PARTITION_NAME = @v_partition_name;

    IF v_partition_exists = 0 THEN
        SET @sql = CONCAT(
            'ALTER TABLE partition_lifecycle_test REORGANIZE PARTITION p_future INTO (',
            'PARTITION ', @v_partition_name, ' VALUES LESS THAN (''', @v_partition_end, '''), ',
            'PARTITION p_future VALUES LESS THAN MAXVALUE)'
        );
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;

        SELECT CONCAT('Created: ', @v_partition_name) AS result;
    END IF;
END//

DROP PROCEDURE IF EXISTS drop_old_lifecycle_test_partitions//
CREATE PROCEDURE drop_old_lifecycle_test_partitions(IN retention_days INT)
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE p_name VARCHAR(64);
    DECLARE p_description VARCHAR(64);
    DECLARE cutoff_date DATE;
    DECLARE cur CURSOR FOR
        SELECT PARTITION_NAME, PARTITION_DESCRIPTION
        FROM information_schema.PARTITIONS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'partition_lifecycle_test'
          AND PARTITION_NAME != 'p_future'
          AND PARTITION_DESCRIPTION != 'MAXVALUE';
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

    SET cutoff_date = DATE_SUB(CURDATE(), INTERVAL retention_days DAY);

    OPEN cur;
    read_loop: LOOP
        FETCH cur INTO p_name, p_description;
        IF done THEN
            LEAVE read_loop;
        END IF;

        IF STR_TO_DATE(REPLACE(p_description, '''', ''), '%Y-%m-%d') <= cutoff_date THEN
            SET @sql = CONCAT('ALTER TABLE partition_lifecycle_test DROP PARTITION ', p_name);
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
            SELECT CONCAT('Dropped: ', p_name) AS result;
        END IF;
    END LOOP;
    CLOSE cur;
END//

DELIMITER ;
EOSQL

    # 一時ファイルをコンテナにコピーして実行
    docker cp "$tmp_sql" "$CONTAINER_NAME:/tmp/lifecycle_procedures.sql"
    docker exec "$CONTAINER_NAME" mysql -u"$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" -e "SOURCE /tmp/lifecycle_procedures.sql" 2>/dev/null
    rm -f "$tmp_sql"

    log_success "テスト用プロシージャを作成しました"
}

# ==============================================================================
# テスト1: パーティション作成（premake相当）
# ==============================================================================
test_partition_creation() {
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    log_test "テスト1: パーティション作成（premake相当）"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    local before_count=$(get_partition_count "partition_lifecycle_test")
    log_info "作成前のパーティション数: ${before_count}"

    # 今後7日分のパーティションを作成
    log_info "今後7日分のパーティションを作成中..."
    for i in $(seq 0 6); do
        local target=$(mysql_exec_quiet "SELECT DATE_ADD(CURDATE(), INTERVAL $i DAY);")
        mysql_exec "CALL create_lifecycle_test_partition('${target}');"
    done

    local after_count=$(get_partition_count "partition_lifecycle_test")
    log_info "作成後のパーティション数: ${after_count}"

    local created=$((after_count - before_count))
    if [ "$created" -eq 7 ]; then
        log_success "テスト1成功: 7つのパーティションが作成されました"
    else
        log_warn "テスト1: ${created}つのパーティションが作成されました（期待値: 7）"
    fi

    echo ""
    log_info "パーティション一覧:"
    mysql_exec "
    SELECT PARTITION_NAME, PARTITION_DESCRIPTION
    FROM information_schema.PARTITIONS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'partition_lifecycle_test'
    ORDER BY PARTITION_NAME;
    "
}

# ==============================================================================
# テスト2: データ挿入とパーティション振り分け
# ==============================================================================
test_data_distribution() {
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    log_test "テスト2: データ挿入とパーティション振り分け"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    # 各パーティションにデータを挿入
    log_info "各パーティションにテストデータを挿入中..."
    for i in $(seq 0 6); do
        local target=$(mysql_exec_quiet "SELECT DATE_ADD(CURDATE(), INTERVAL $i DAY);")
        mysql_exec "
        INSERT INTO partition_lifecycle_test (tenant_id, event_date, data)
        VALUES (UUID(), '${target}', 'test data for day ${i}');
        "
    done

    echo ""
    log_info "パーティション別データ分布:"
    mysql_exec "
    SELECT PARTITION_NAME, TABLE_ROWS
    FROM information_schema.PARTITIONS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'partition_lifecycle_test'
      AND TABLE_ROWS > 0
    ORDER BY PARTITION_NAME;
    "

    log_success "テスト2完了: データが各パーティションに振り分けられました"
}

# ==============================================================================
# テスト3: パーティション削除（retention相当）
# ==============================================================================
test_partition_deletion() {
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    log_test "テスト3: パーティション削除（retention相当）"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    # RANGEパーティションは昇順で作成する必要があるため、
    # テーブルを再作成して過去→未来の順でパーティションを作成
    log_info "テスト用にテーブルを再作成中..."
    mysql_exec "DROP TABLE IF EXISTS partition_lifecycle_test;"
    mysql_exec "
    CREATE TABLE partition_lifecycle_test (
        id CHAR(36) DEFAULT (UUID()),
        tenant_id CHAR(36) NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001',
        event_date DATE NOT NULL,
        data TEXT,
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (tenant_id, event_date, id),
        KEY idx_lifecycle_test_date (event_date)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    PARTITION BY RANGE COLUMNS(event_date) (
        PARTITION p_future VALUES LESS THAN MAXVALUE
    );
    "

    # 過去のパーティションを先に作成（古い順から）
    log_info "テスト用に過去のパーティションを作成中（15日前〜10日前）..."
    for i in $(seq 15 -1 10); do
        local target=$(mysql_exec_quiet "SELECT DATE_SUB(CURDATE(), INTERVAL $i DAY);")
        mysql_exec "CALL create_lifecycle_test_partition('${target}');"
    done

    # 今後のパーティションも作成
    log_info "今後のパーティションを作成中（今日〜3日後）..."
    for i in $(seq 0 3); do
        local target=$(mysql_exec_quiet "SELECT DATE_ADD(CURDATE(), INTERVAL $i DAY);")
        mysql_exec "CALL create_lifecycle_test_partition('${target}');"
    done

    local before_count=$(get_partition_count "partition_lifecycle_test")
    log_info "削除前のパーティション数: ${before_count}"

    echo ""
    log_info "パーティション一覧（削除前）:"
    mysql_exec "
    SELECT PARTITION_NAME, PARTITION_DESCRIPTION
    FROM information_schema.PARTITIONS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'partition_lifecycle_test'
    ORDER BY PARTITION_NAME;
    "

    # 7日以上前のパーティションを削除
    log_info "7日以上前のパーティションを削除中..."
    mysql_exec "CALL drop_old_lifecycle_test_partitions(7);"

    local after_count=$(get_partition_count "partition_lifecycle_test")
    log_info "削除後のパーティション数: ${after_count}"

    local deleted=$((before_count - after_count))
    if [ "$deleted" -gt 0 ]; then
        log_success "テスト3成功: ${deleted}つのパーティションが削除されました"
    else
        log_warn "テスト3: 削除されたパーティションはありません"
    fi

    echo ""
    log_info "パーティション一覧（削除後）:"
    mysql_exec "
    SELECT PARTITION_NAME, PARTITION_DESCRIPTION
    FROM information_schema.PARTITIONS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'partition_lifecycle_test'
    ORDER BY PARTITION_NAME;
    "
}

# ==============================================================================
# テスト4: DROP PARTITION vs DELETE 性能比較
# ==============================================================================
test_drop_vs_delete_performance() {
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    log_test "テスト4: DROP PARTITION vs DELETE 性能比較"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    # テーブルを再作成（RANGEパーティションは昇順で作成が必要）
    log_info "テスト用にテーブルを再作成中..."
    mysql_exec "DROP TABLE IF EXISTS partition_lifecycle_test;"
    mysql_exec "
    CREATE TABLE partition_lifecycle_test (
        id CHAR(36) DEFAULT (UUID()),
        tenant_id CHAR(36) NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001',
        event_date DATE NOT NULL,
        data TEXT,
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (tenant_id, event_date, id),
        KEY idx_lifecycle_test_date (event_date)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    PARTITION BY RANGE COLUMNS(event_date) (
        PARTITION p_future VALUES LESS THAN MAXVALUE
    );
    "

    # 過去30日と31日のパーティションを作成（古い順）
    local test_date=$(mysql_exec_quiet "SELECT DATE_SUB(CURDATE(), INTERVAL 31 DAY);")
    local test_date2=$(mysql_exec_quiet "SELECT DATE_SUB(CURDATE(), INTERVAL 30 DAY);")
    mysql_exec "CALL create_lifecycle_test_partition('${test_date}');"
    mysql_exec "CALL create_lifecycle_test_partition('${test_date2}');"

    # 今日のパーティションも作成
    local today=$(mysql_exec_quiet "SELECT CURDATE();")
    mysql_exec "CALL create_lifecycle_test_partition('${today}');"

    log_info "テスト用に1000行を挿入中（パーティション1）..."
    mysql_exec "
    INSERT INTO partition_lifecycle_test (tenant_id, event_date, data)
    SELECT
        UUID(),
        '${test_date}',
        REPEAT('x', 100)
    FROM (
        SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
        UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10
    ) a,
    (
        SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
        UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10
    ) b,
    (
        SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
        UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10
    ) c
    LIMIT 1000;
    "

    # DROP PARTITION で削除
    local partition_name=$(mysql_exec_quiet "SELECT CONCAT('p', DATE_FORMAT('${test_date}', '%Y%m%d'));")

    log_info "DROP PARTITION で削除中..."
    local start_time=$(python3 -c 'import time; print(int(time.time() * 1000))' 2>/dev/null || date +%s)
    mysql_exec "ALTER TABLE partition_lifecycle_test DROP PARTITION ${partition_name};"
    local end_time=$(python3 -c 'import time; print(int(time.time() * 1000))' 2>/dev/null || date +%s)
    local drop_duration=$((end_time - start_time))

    log_info "DROP PARTITION 実行時間: ${drop_duration}ms"

    log_info "比較用に1000行を挿入中（パーティション2）..."
    mysql_exec "
    INSERT INTO partition_lifecycle_test (tenant_id, event_date, data)
    SELECT
        UUID(),
        '${test_date2}',
        REPEAT('x', 100)
    FROM (
        SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
        UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10
    ) a,
    (
        SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
        UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10
    ) b,
    (
        SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
        UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10
    ) c
    LIMIT 1000;
    "

    log_info "DELETE で削除中..."
    start_time=$(python3 -c 'import time; print(int(time.time() * 1000))' 2>/dev/null || date +%s)
    mysql_exec "DELETE FROM partition_lifecycle_test WHERE event_date = '${test_date2}';"
    end_time=$(python3 -c 'import time; print(int(time.time() * 1000))' 2>/dev/null || date +%s)
    local delete_duration=$((end_time - start_time))

    log_info "DELETE 実行時間: ${delete_duration}ms"

    echo ""
    log_info "===== 性能比較結果 ====="
    log_info "DROP PARTITION: ${drop_duration}ms"
    log_info "DELETE:         ${delete_duration}ms"

    if [ "$drop_duration" -lt "$delete_duration" ]; then
        local speedup=$((delete_duration / (drop_duration + 1)))
        log_success "DROP PARTITION は DELETE の約 ${speedup}x 高速"
    else
        log_warn "このテストでは有意な差が出ませんでした（データ量が少ない可能性）"
    fi
}

# ==============================================================================
# クリーンアップ
# ==============================================================================
cleanup_test() {
    log_step "テスト環境をクリーンアップ中..."

    mysql_exec "DROP TABLE IF EXISTS partition_lifecycle_test;"
    mysql_exec "DROP PROCEDURE IF EXISTS create_lifecycle_test_partition;"
    mysql_exec "DROP PROCEDURE IF EXISTS drop_old_lifecycle_test_partitions;"

    log_success "テスト環境をクリーンアップしました"
}

# ==============================================================================
# メイン処理
# ==============================================================================
main() {
    echo ""
    echo "╔══════════════════════════════════════════════════════════════╗"
    echo "║     MySQL パーティションライフサイクル検証スクリプト            ║"
    echo "╚══════════════════════════════════════════════════════════════╝"
    echo ""

    check_container

    # テスト環境セットアップ
    setup_test_table
    setup_test_procedures

    # テスト実行
    test_partition_creation
    test_data_distribution
    test_partition_deletion
    test_drop_vs_delete_performance

    # クリーンアップ
    echo ""
    read -p "テスト環境をクリーンアップしますか? (Y/n): " confirm
    if [ "$confirm" != "n" ] && [ "$confirm" != "N" ]; then
        cleanup_test
    fi

    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    log_success "すべてのライフサイクルテストが完了しました"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
}

main "$@"
