-- ================================================
-- Archive Support for Statistics Tables (MySQL)
-- Issue #1122: MySQL統計テーブルのパーティションをアーカイブ方式に変更
--
-- This migration adds:
--   1. Archive tables for detached partitions
--   2. Archive procedures using EXCHANGE PARTITION
--   3. Stub function for external storage export
--   4. Update drop procedures to archive first
--
-- Strategy:
--   Retention → EXCHANGE PARTITION to archive table → Export to S3 → DROP
--
-- ================================================

-- ================================================
-- Phase 1: Create archive tables
-- ================================================

-- Archive table for statistics_daily_users
CREATE TABLE statistics_daily_users_archive (
    tenant_id CHAR(36) NOT NULL,
    stat_date DATE NOT NULL,
    user_id CHAR(36) NOT NULL,
    user_name VARCHAR(255) NOT NULL DEFAULT '',
    last_used_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    archived_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (tenant_id, stat_date, user_id),
    KEY idx_archive_daily_stat_date (stat_date),
    KEY idx_archive_daily_archived_at (archived_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Archive table for old statistics_daily_users partitions';

-- Archive table for statistics_monthly_users
CREATE TABLE statistics_monthly_users_archive (
    tenant_id CHAR(36) NOT NULL,
    stat_month DATE NOT NULL,
    user_id CHAR(36) NOT NULL,
    user_name VARCHAR(255) NOT NULL DEFAULT '',
    last_used_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    archived_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (tenant_id, stat_month, user_id),
    KEY idx_archive_monthly_stat_month (stat_month),
    KEY idx_archive_monthly_archived_at (archived_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Archive table for old statistics_monthly_users partitions';

-- Archive table for statistics_yearly_users
CREATE TABLE statistics_yearly_users_archive (
    tenant_id CHAR(36) NOT NULL,
    stat_year DATE NOT NULL,
    user_id CHAR(36) NOT NULL,
    user_name VARCHAR(255) NOT NULL DEFAULT '',
    last_used_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    archived_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (tenant_id, stat_year, user_id),
    KEY idx_archive_yearly_stat_year (stat_year),
    KEY idx_archive_yearly_archived_at (archived_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Archive table for old statistics_yearly_users partitions';

-- ================================================
-- Phase 2: Create archive processing log table
-- ================================================

CREATE TABLE archive_processing_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    table_name VARCHAR(128) NOT NULL,
    partition_name VARCHAR(64) NOT NULL,
    row_count BIGINT NOT NULL DEFAULT 0,
    archived_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    exported BOOLEAN NOT NULL DEFAULT FALSE,
    exported_at DATETIME(6) NULL,
    export_destination VARCHAR(512) NULL,
    dropped BOOLEAN NOT NULL DEFAULT FALSE,
    dropped_at DATETIME(6) NULL,

    KEY idx_archive_log_table (table_name),
    KEY idx_archive_log_archived (archived_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Log of archived partitions for tracking export status';

-- ================================================
-- Phase 3: Create archive procedures
-- ================================================

DELIMITER //

-- Archive a partition from statistics_daily_users
DROP PROCEDURE IF EXISTS archive_daily_users_partition//
CREATE PROCEDURE archive_daily_users_partition(IN p_partition_name VARCHAR(64))
BEGIN
    DECLARE v_row_count BIGINT DEFAULT 0;
    DECLARE v_temp_table VARCHAR(128);

    SET v_temp_table = CONCAT('statistics_daily_users_temp_', REPLACE(UUID(), '-', ''));

    -- Get row count before archive
    SET @count_sql = CONCAT(
        'SELECT COUNT(*) INTO @v_count FROM statistics_daily_users PARTITION (', p_partition_name, ')'
    );
    PREPARE stmt FROM @count_sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
    SET v_row_count = @v_count;

    IF v_row_count > 0 THEN
        -- Create temp table with same structure (no partitioning)
        SET @create_sql = CONCAT('CREATE TABLE ', v_temp_table, ' LIKE statistics_daily_users');
        PREPARE stmt FROM @create_sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;

        SET @alter_sql = CONCAT('ALTER TABLE ', v_temp_table, ' REMOVE PARTITIONING');
        PREPARE stmt FROM @alter_sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;

        -- Exchange partition with temp table (fast, metadata only)
        SET @exchange_sql = CONCAT(
            'ALTER TABLE statistics_daily_users EXCHANGE PARTITION ', p_partition_name,
            ' WITH TABLE ', v_temp_table
        );
        PREPARE stmt FROM @exchange_sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;

        -- Copy data to archive table with archived_at timestamp
        SET @insert_sql = CONCAT(
            'INSERT INTO statistics_daily_users_archive ',
            '(tenant_id, stat_date, user_id, user_name, last_used_at, created_at, archived_at) ',
            'SELECT tenant_id, stat_date, user_id, user_name, last_used_at, created_at, NOW(6) ',
            'FROM ', v_temp_table
        );
        PREPARE stmt FROM @insert_sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;

        -- Drop temp table
        SET @drop_sql = CONCAT('DROP TABLE ', v_temp_table);
        PREPARE stmt FROM @drop_sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;

        -- Log the archive operation
        INSERT INTO archive_processing_log (table_name, partition_name, row_count)
        VALUES ('statistics_daily_users', p_partition_name, v_row_count);
    END IF;

    -- Drop the (now empty) partition
    SET @drop_part_sql = CONCAT('ALTER TABLE statistics_daily_users DROP PARTITION ', p_partition_name);
    PREPARE stmt FROM @drop_part_sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
END//

-- Archive a partition from statistics_monthly_users
DROP PROCEDURE IF EXISTS archive_monthly_users_partition//
CREATE PROCEDURE archive_monthly_users_partition(IN p_partition_name VARCHAR(64))
BEGIN
    DECLARE v_row_count BIGINT DEFAULT 0;
    DECLARE v_temp_table VARCHAR(128);

    SET v_temp_table = CONCAT('statistics_monthly_users_temp_', REPLACE(UUID(), '-', ''));

    SET @count_sql = CONCAT(
        'SELECT COUNT(*) INTO @v_count FROM statistics_monthly_users PARTITION (', p_partition_name, ')'
    );
    PREPARE stmt FROM @count_sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
    SET v_row_count = @v_count;

    IF v_row_count > 0 THEN
        SET @create_sql = CONCAT('CREATE TABLE ', v_temp_table, ' LIKE statistics_monthly_users');
        PREPARE stmt FROM @create_sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;

        SET @alter_sql = CONCAT('ALTER TABLE ', v_temp_table, ' REMOVE PARTITIONING');
        PREPARE stmt FROM @alter_sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;

        SET @exchange_sql = CONCAT(
            'ALTER TABLE statistics_monthly_users EXCHANGE PARTITION ', p_partition_name,
            ' WITH TABLE ', v_temp_table
        );
        PREPARE stmt FROM @exchange_sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;

        SET @insert_sql = CONCAT(
            'INSERT INTO statistics_monthly_users_archive ',
            '(tenant_id, stat_month, user_id, user_name, last_used_at, created_at, archived_at) ',
            'SELECT tenant_id, stat_month, user_id, user_name, last_used_at, created_at, NOW(6) ',
            'FROM ', v_temp_table
        );
        PREPARE stmt FROM @insert_sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;

        SET @drop_sql = CONCAT('DROP TABLE ', v_temp_table);
        PREPARE stmt FROM @drop_sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;

        INSERT INTO archive_processing_log (table_name, partition_name, row_count)
        VALUES ('statistics_monthly_users', p_partition_name, v_row_count);
    END IF;

    SET @drop_part_sql = CONCAT('ALTER TABLE statistics_monthly_users DROP PARTITION ', p_partition_name);
    PREPARE stmt FROM @drop_part_sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
END//

-- Archive a partition from statistics_yearly_users
DROP PROCEDURE IF EXISTS archive_yearly_users_partition//
CREATE PROCEDURE archive_yearly_users_partition(IN p_partition_name VARCHAR(64))
BEGIN
    DECLARE v_row_count BIGINT DEFAULT 0;
    DECLARE v_temp_table VARCHAR(128);

    SET v_temp_table = CONCAT('statistics_yearly_users_temp_', REPLACE(UUID(), '-', ''));

    SET @count_sql = CONCAT(
        'SELECT COUNT(*) INTO @v_count FROM statistics_yearly_users PARTITION (', p_partition_name, ')'
    );
    PREPARE stmt FROM @count_sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
    SET v_row_count = @v_count;

    IF v_row_count > 0 THEN
        SET @create_sql = CONCAT('CREATE TABLE ', v_temp_table, ' LIKE statistics_yearly_users');
        PREPARE stmt FROM @create_sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;

        SET @alter_sql = CONCAT('ALTER TABLE ', v_temp_table, ' REMOVE PARTITIONING');
        PREPARE stmt FROM @alter_sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;

        SET @exchange_sql = CONCAT(
            'ALTER TABLE statistics_yearly_users EXCHANGE PARTITION ', p_partition_name,
            ' WITH TABLE ', v_temp_table
        );
        PREPARE stmt FROM @exchange_sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;

        SET @insert_sql = CONCAT(
            'INSERT INTO statistics_yearly_users_archive ',
            '(tenant_id, stat_year, user_id, user_name, last_used_at, created_at, archived_at) ',
            'SELECT tenant_id, stat_year, user_id, user_name, last_used_at, created_at, NOW(6) ',
            'FROM ', v_temp_table
        );
        PREPARE stmt FROM @insert_sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;

        SET @drop_sql = CONCAT('DROP TABLE ', v_temp_table);
        PREPARE stmt FROM @drop_sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;

        INSERT INTO archive_processing_log (table_name, partition_name, row_count)
        VALUES ('statistics_yearly_users', p_partition_name, v_row_count);
    END IF;

    SET @drop_part_sql = CONCAT('ALTER TABLE statistics_yearly_users DROP PARTITION ', p_partition_name);
    PREPARE stmt FROM @drop_part_sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
END//

-- ================================================
-- Phase 4: Update drop procedures to use archive
-- ================================================

-- Replace drop_old_daily_users_partitions with archive version
DROP PROCEDURE IF EXISTS drop_old_daily_users_partitions//
CREATE PROCEDURE drop_old_daily_users_partitions(IN retention_days INT)
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE p_name VARCHAR(64);
    DECLARE p_description VARCHAR(64);
    DECLARE cutoff_date DATE;
    DECLARE cur CURSOR FOR
        SELECT PARTITION_NAME, PARTITION_DESCRIPTION
        FROM information_schema.PARTITIONS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'statistics_daily_users'
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
            -- Archive instead of direct drop
            CALL archive_daily_users_partition(p_name);
        END IF;
    END LOOP;
    CLOSE cur;
END//

-- Replace drop_old_monthly_users_partitions with archive version
DROP PROCEDURE IF EXISTS drop_old_monthly_users_partitions//
CREATE PROCEDURE drop_old_monthly_users_partitions(IN retention_months INT)
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE p_name VARCHAR(64);
    DECLARE p_description VARCHAR(64);
    DECLARE cutoff_date DATE;
    DECLARE cur CURSOR FOR
        SELECT PARTITION_NAME, PARTITION_DESCRIPTION
        FROM information_schema.PARTITIONS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'statistics_monthly_users'
          AND PARTITION_NAME != 'p_future'
          AND PARTITION_DESCRIPTION != 'MAXVALUE';
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

    SET cutoff_date = DATE_SUB(DATE_FORMAT(CURDATE(), '%Y-%m-01'), INTERVAL retention_months MONTH);

    OPEN cur;
    read_loop: LOOP
        FETCH cur INTO p_name, p_description;
        IF done THEN
            LEAVE read_loop;
        END IF;

        IF STR_TO_DATE(REPLACE(p_description, '''', ''), '%Y-%m-%d') <= cutoff_date THEN
            -- Archive instead of direct drop
            CALL archive_monthly_users_partition(p_name);
        END IF;
    END LOOP;
    CLOSE cur;
END//

-- Replace drop_old_yearly_users_partitions with archive version
DROP PROCEDURE IF EXISTS drop_old_yearly_users_partitions//
CREATE PROCEDURE drop_old_yearly_users_partitions(IN retention_months INT)
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE p_name VARCHAR(64);
    DECLARE p_description VARCHAR(64);
    DECLARE cutoff_date DATE;
    DECLARE cur CURSOR FOR
        SELECT PARTITION_NAME, PARTITION_DESCRIPTION
        FROM information_schema.PARTITIONS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'statistics_yearly_users'
          AND PARTITION_NAME != 'p_future'
          AND PARTITION_DESCRIPTION != 'MAXVALUE';
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;

    SET cutoff_date = DATE_SUB(DATE_FORMAT(CURDATE(), '%Y-%m-01'), INTERVAL retention_months MONTH);

    OPEN cur;
    read_loop: LOOP
        FETCH cur INTO p_name, p_description;
        IF done THEN
            LEAVE read_loop;
        END IF;

        IF STR_TO_DATE(REPLACE(p_description, '''', ''), '%Y-%m-%d') <= cutoff_date THEN
            -- Archive instead of direct drop
            CALL archive_yearly_users_partition(p_name);
        END IF;
    END LOOP;
    CLOSE cur;
END//

-- ================================================
-- Phase 5: Create archive status and cleanup functions
-- ================================================

-- Get archive status
DROP PROCEDURE IF EXISTS get_archive_status//
CREATE PROCEDURE get_archive_status()
BEGIN
    SELECT
        'statistics_daily_users_archive' AS table_name,
        COUNT(*) AS row_count,
        MIN(stat_date) AS oldest_date,
        MAX(stat_date) AS newest_date,
        MIN(archived_at) AS first_archived,
        MAX(archived_at) AS last_archived
    FROM statistics_daily_users_archive
    UNION ALL
    SELECT
        'statistics_monthly_users_archive',
        COUNT(*),
        MIN(stat_month),
        MAX(stat_month),
        MIN(archived_at),
        MAX(archived_at)
    FROM statistics_monthly_users_archive
    UNION ALL
    SELECT
        'statistics_yearly_users_archive',
        COUNT(*),
        MIN(stat_year),
        MAX(stat_year),
        MIN(archived_at),
        MAX(archived_at)
    FROM statistics_yearly_users_archive;
END//

-- Export stub function (placeholder for external storage export)
-- Users should implement their own export logic (S3, GCS, etc.)
DROP PROCEDURE IF EXISTS export_archive_to_external_storage//
CREATE PROCEDURE export_archive_to_external_storage(
    IN p_table_name VARCHAR(128),
    IN p_before_date DATE
)
BEGIN
    -- ================================================
    -- STUB IMPLEMENTATION
    -- ================================================
    -- This procedure is a placeholder for external storage export.
    -- Replace this implementation with your cloud-specific export logic.
    --
    -- Example: Export to CSV file (requires FILE privilege and secure_file_priv)
    --   SET @sql = CONCAT(
    --       'SELECT * FROM ', p_table_name,
    --       ' WHERE archived_at < ''', p_before_date, '''',
    --       ' INTO OUTFILE ''/var/lib/mysql-files/', p_table_name, '_', p_before_date, '.csv''',
    --       ' FIELDS TERMINATED BY '','' ENCLOSED BY ''"'' LINES TERMINATED BY ''\\n'''
    --   );
    --
    -- After export, upload to S3 via aws cli or Lambda
    -- ================================================

    SELECT CONCAT(
        'STUB: Would export ', p_table_name,
        ' records archived before ', p_before_date,
        '. Implement your own export logic here.'
    ) AS message;
END//

-- Cleanup archived data after successful export
DROP PROCEDURE IF EXISTS cleanup_exported_archives//
CREATE PROCEDURE cleanup_exported_archives(
    IN p_table_name VARCHAR(128),
    IN p_before_date DATE,
    IN p_dry_run BOOLEAN
)
BEGIN
    DECLARE v_count BIGINT;

    IF p_table_name = 'statistics_daily_users_archive' THEN
        SELECT COUNT(*) INTO v_count
        FROM statistics_daily_users_archive
        WHERE archived_at < p_before_date;

        IF NOT p_dry_run AND v_count > 0 THEN
            DELETE FROM statistics_daily_users_archive WHERE archived_at < p_before_date;
        END IF;
    ELSEIF p_table_name = 'statistics_monthly_users_archive' THEN
        SELECT COUNT(*) INTO v_count
        FROM statistics_monthly_users_archive
        WHERE archived_at < p_before_date;

        IF NOT p_dry_run AND v_count > 0 THEN
            DELETE FROM statistics_monthly_users_archive WHERE archived_at < p_before_date;
        END IF;
    ELSEIF p_table_name = 'statistics_yearly_users_archive' THEN
        SELECT COUNT(*) INTO v_count
        FROM statistics_yearly_users_archive
        WHERE archived_at < p_before_date;

        IF NOT p_dry_run AND v_count > 0 THEN
            DELETE FROM statistics_yearly_users_archive WHERE archived_at < p_before_date;
        END IF;
    END IF;

    SELECT p_table_name AS table_name,
           v_count AS affected_rows,
           p_dry_run AS dry_run,
           IF(p_dry_run, 'Would delete', 'Deleted') AS action;
END//

DELIMITER ;

-- ================================================
-- Verification
-- ================================================

-- Check archive tables created
SELECT 'Archive tables created:' AS status;
SELECT TABLE_NAME, TABLE_COMMENT
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME LIKE '%_archive%'
ORDER BY TABLE_NAME;

-- ================================================
-- Usage examples (for reference):
-- ================================================
--
-- Check archive status:
--   CALL get_archive_status();
--
-- View archive processing log:
--   SELECT * FROM archive_processing_log ORDER BY archived_at DESC LIMIT 10;
--
-- Export stub (implement your own):
--   CALL export_archive_to_external_storage('statistics_daily_users_archive', '2024-01-01');
--
-- Cleanup after export (dry run):
--   CALL cleanup_exported_archives('statistics_daily_users_archive', '2024-01-01', TRUE);
--
-- Cleanup after export (actual):
--   CALL cleanup_exported_archives('statistics_daily_users_archive', '2024-01-01', FALSE);
--
