-- =====================================================
-- oauth_token に (tenant_id, client_instance_id) index を追加 (MySQL, Issue #1521)
--
-- See postgresql/V0_14_0_6__oauth_token_client_instance_index.sql for context.
-- MySQL は部分 index を持たないため、通常の複合 index にする。
--
-- MySQL 8.0+ では CREATE INDEX のデフォルトが ALGORITHM=INPLACE で
-- 書き込みブロックなしの online build となるため、Flyway 適用でもほぼ
-- 影響なし。確実性を求める場合は事前に runbook の create_index.mysql.sql
-- (ALGORITHM=INPLACE LOCK=NONE 明示) を実行しておくこと。
--
-- MySQL は CREATE INDEX の IF NOT EXISTS をサポートしないため、
-- information_schema を見るプロシージャで冪等にする。
-- runbook で事前作成済みの場合は no-op となる。
-- =====================================================

DELIMITER //

DROP PROCEDURE IF EXISTS add_index_if_not_exists//
CREATE PROCEDURE add_index_if_not_exists(
    IN p_table_name VARCHAR(64),
    IN p_index_name VARCHAR(64),
    IN p_index_columns VARCHAR(255)
)
BEGIN
    DECLARE index_exists INT DEFAULT 0;

    SELECT COUNT(*) INTO index_exists
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table_name
      AND INDEX_NAME = p_index_name;

    IF index_exists = 0 THEN
        SET @sql = CONCAT('CREATE INDEX ', p_index_name, ' ON ', p_table_name, ' (', p_index_columns, ')');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//

DELIMITER ;

CALL add_index_if_not_exists('oauth_token', 'idx_oauth_token_client_instance', 'tenant_id, client_instance_id');
