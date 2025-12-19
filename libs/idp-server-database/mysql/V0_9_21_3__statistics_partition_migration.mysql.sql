-- V0_9_21_3__statistics_partition_migration.mysql.sql
-- Migration script: Convert existing statistics tables to partitioned tables
--
-- This migration:
--   1. Backs up existing data
--   2. Recreates tables with partition definitions
--   3. Restores data from backup
--   4. Creates partition management procedures (if not exist)
--   5. Sets up Event Scheduler for automatic maintenance
--
-- Prerequisites:
--   - MySQL 8.0+
--   - Event Scheduler enabled: SET GLOBAL event_scheduler = ON;
--
-- Note: This migration preserves all existing data.

-- =====================================================
-- Step 1: Backup existing data
-- =====================================================

CREATE TABLE IF NOT EXISTS statistics_daily_users_backup AS
SELECT * FROM statistics_daily_users;

CREATE TABLE IF NOT EXISTS statistics_monthly_users_backup AS
SELECT * FROM statistics_monthly_users;

CREATE TABLE IF NOT EXISTS statistics_yearly_users_backup AS
SELECT * FROM statistics_yearly_users;

-- =====================================================
-- Step 2: Drop old tables
-- =====================================================

DROP TABLE IF EXISTS statistics_daily_users;
DROP TABLE IF EXISTS statistics_monthly_users;
DROP TABLE IF EXISTS statistics_yearly_users;

-- =====================================================
-- Step 3: Recreate tables with partitioning
-- =====================================================

-- statistics_daily_users (PARTITIONED - daily)
CREATE TABLE statistics_daily_users (
    tenant_id CHAR(36) NOT NULL,
    stat_date DATE NOT NULL,
    user_id CHAR(36) NOT NULL,
    user_name VARCHAR(255) NOT NULL DEFAULT '',
    last_used_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (tenant_id, stat_date, user_id),
    KEY idx_statistics_daily_users_tenant_date (tenant_id, stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Daily active users tracking with daily partitioning. Retained for 90 days.'
PARTITION BY RANGE COLUMNS(stat_date) (
    PARTITION p_future VALUES LESS THAN MAXVALUE
);

-- statistics_monthly_users (PARTITIONED - monthly)
CREATE TABLE statistics_monthly_users (
    tenant_id CHAR(36) NOT NULL,
    stat_month DATE NOT NULL COMMENT 'First day of month (e.g., 2025-01-01)',
    user_id CHAR(36) NOT NULL,
    user_name VARCHAR(255) NOT NULL DEFAULT '',
    last_used_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (tenant_id, stat_month, user_id),
    KEY idx_statistics_monthly_users_tenant_month (tenant_id, stat_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Monthly active users tracking with monthly partitioning. Retained for 13 months.'
PARTITION BY RANGE COLUMNS(stat_month) (
    PARTITION p_future VALUES LESS THAN MAXVALUE
);

-- statistics_yearly_users (PARTITIONED - monthly for fiscal year)
CREATE TABLE statistics_yearly_users (
    tenant_id CHAR(36) NOT NULL,
    stat_year DATE NOT NULL COMMENT 'Fiscal year start date (e.g., 2025-04-01 for April fiscal year)',
    user_id CHAR(36) NOT NULL,
    user_name VARCHAR(255) NOT NULL DEFAULT '',
    last_used_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (tenant_id, stat_year, user_id),
    KEY idx_statistics_yearly_users_tenant_year (tenant_id, stat_year),
    KEY idx_statistics_yearly_users_last_used (tenant_id, last_used_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Yearly active users tracking with monthly partitioning for fiscal year support. Retained for 60 months.'
PARTITION BY RANGE COLUMNS(stat_year) (
    PARTITION p_future VALUES LESS THAN MAXVALUE
);

-- =====================================================
-- Step 4: Restore data from backup
-- =====================================================

INSERT INTO statistics_daily_users
SELECT * FROM statistics_daily_users_backup;

INSERT INTO statistics_monthly_users
SELECT * FROM statistics_monthly_users_backup;

INSERT INTO statistics_yearly_users
SELECT * FROM statistics_yearly_users_backup;

-- =====================================================
-- Step 5: Drop old cleanup procedures (replaced by partition management)
-- =====================================================

DROP PROCEDURE IF EXISTS cleanup_old_daily_users;
DROP PROCEDURE IF EXISTS cleanup_old_monthly_users;
DROP PROCEDURE IF EXISTS cleanup_old_yearly_users;

-- =====================================================
-- Step 6: Create partition management procedures
-- =====================================================

DELIMITER //

-- Create partition for statistics_daily_users (daily)
DROP PROCEDURE IF EXISTS create_daily_users_partition//
CREATE PROCEDURE create_daily_users_partition(IN target_date DATE)
BEGIN
    DECLARE partition_name VARCHAR(20);
    DECLARE partition_end DATE;
    DECLARE partition_exists INT DEFAULT 0;

    SET partition_name = CONCAT('p', DATE_FORMAT(target_date, '%Y%m%d'));
    SET partition_end = DATE_ADD(target_date, INTERVAL 1 DAY);

    SELECT COUNT(*) INTO partition_exists
    FROM information_schema.PARTITIONS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'statistics_daily_users'
      AND PARTITION_NAME = partition_name;

    IF partition_exists = 0 THEN
        SET @sql = CONCAT(
            'ALTER TABLE statistics_daily_users REORGANIZE PARTITION p_future INTO (',
            'PARTITION ', partition_name, ' VALUES LESS THAN (''', partition_end, '''), ',
            'PARTITION p_future VALUES LESS THAN MAXVALUE)'
        );
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//

-- Create partition for statistics_monthly_users (monthly)
DROP PROCEDURE IF EXISTS create_monthly_users_partition//
CREATE PROCEDURE create_monthly_users_partition(IN target_date DATE)
BEGIN
    DECLARE partition_name VARCHAR(20);
    DECLARE partition_start DATE;
    DECLARE partition_end DATE;
    DECLARE partition_exists INT DEFAULT 0;

    SET partition_start = DATE_FORMAT(target_date, '%Y-%m-01');
    SET partition_end = DATE_ADD(partition_start, INTERVAL 1 MONTH);
    SET partition_name = CONCAT('p', DATE_FORMAT(partition_start, '%Y%m'));

    SELECT COUNT(*) INTO partition_exists
    FROM information_schema.PARTITIONS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'statistics_monthly_users'
      AND PARTITION_NAME = partition_name;

    IF partition_exists = 0 THEN
        SET @sql = CONCAT(
            'ALTER TABLE statistics_monthly_users REORGANIZE PARTITION p_future INTO (',
            'PARTITION ', partition_name, ' VALUES LESS THAN (''', partition_end, '''), ',
            'PARTITION p_future VALUES LESS THAN MAXVALUE)'
        );
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//

-- Create partition for statistics_yearly_users (monthly)
DROP PROCEDURE IF EXISTS create_yearly_users_partition//
CREATE PROCEDURE create_yearly_users_partition(IN target_date DATE)
BEGIN
    DECLARE partition_name VARCHAR(20);
    DECLARE partition_start DATE;
    DECLARE partition_end DATE;
    DECLARE partition_exists INT DEFAULT 0;

    SET partition_start = DATE_FORMAT(target_date, '%Y-%m-01');
    SET partition_end = DATE_ADD(partition_start, INTERVAL 1 MONTH);
    SET partition_name = CONCAT('p', DATE_FORMAT(partition_start, '%Y%m'));

    SELECT COUNT(*) INTO partition_exists
    FROM information_schema.PARTITIONS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'statistics_yearly_users'
      AND PARTITION_NAME = partition_name;

    IF partition_exists = 0 THEN
        SET @sql = CONCAT(
            'ALTER TABLE statistics_yearly_users REORGANIZE PARTITION p_future INTO (',
            'PARTITION ', partition_name, ' VALUES LESS THAN (''', partition_end, '''), ',
            'PARTITION p_future VALUES LESS THAN MAXVALUE)'
        );
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//

-- Drop old partitions for statistics_daily_users
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
            SET @sql = CONCAT('ALTER TABLE statistics_daily_users DROP PARTITION ', p_name);
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
    END LOOP;
    CLOSE cur;
END//

-- Drop old partitions for statistics_monthly_users
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
            SET @sql = CONCAT('ALTER TABLE statistics_monthly_users DROP PARTITION ', p_name);
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
    END LOOP;
    CLOSE cur;
END//

-- Drop old partitions for statistics_yearly_users
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
            SET @sql = CONCAT('ALTER TABLE statistics_yearly_users DROP PARTITION ', p_name);
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
    END LOOP;
    CLOSE cur;
END//

-- Main maintenance procedure
DROP PROCEDURE IF EXISTS maintain_statistics_partitions//
CREATE PROCEDURE maintain_statistics_partitions()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE target_date DATE;

    -- statistics_daily_users: Create partitions for next 90 days
    SET i = 0;
    WHILE i < 90 DO
        SET target_date = DATE_ADD(CURDATE(), INTERVAL i DAY);
        CALL create_daily_users_partition(target_date);
        SET i = i + 1;
    END WHILE;

    -- statistics_monthly_users: Create partitions for next 3 months
    SET i = 0;
    WHILE i < 3 DO
        SET target_date = DATE_ADD(DATE_FORMAT(CURDATE(), '%Y-%m-01'), INTERVAL i MONTH);
        CALL create_monthly_users_partition(target_date);
        SET i = i + 1;
    END WHILE;

    -- statistics_yearly_users: Create partitions for next 3 months
    SET i = 0;
    WHILE i < 3 DO
        SET target_date = DATE_ADD(DATE_FORMAT(CURDATE(), '%Y-%m-01'), INTERVAL i MONTH);
        CALL create_yearly_users_partition(target_date);
        SET i = i + 1;
    END WHILE;

    -- Drop old partitions
    CALL drop_old_daily_users_partitions(90);      -- 90 days retention
    CALL drop_old_monthly_users_partitions(13);    -- 13 months retention
    CALL drop_old_yearly_users_partitions(60);     -- 60 months retention
END//

DELIMITER ;

-- =====================================================
-- Step 7: Setup Event Scheduler
-- =====================================================

DROP EVENT IF EXISTS evt_maintain_statistics_partitions;

CREATE EVENT IF NOT EXISTS evt_maintain_statistics_partitions
ON SCHEDULE EVERY 1 DAY
STARTS (TIMESTAMP(CURRENT_DATE) + INTERVAL 1 DAY + INTERVAL 3 HOUR)
ON COMPLETION PRESERVE
ENABLE
COMMENT 'Daily maintenance of statistics table partitions'
DO
    CALL maintain_statistics_partitions();

-- =====================================================
-- Step 8: Initial partition creation
-- This creates partitions for existing and future data
-- =====================================================

-- Run maintenance to create initial partitions
CALL maintain_statistics_partitions();

-- =====================================================
-- Step 9: Cleanup backup tables (optional)
-- Uncomment to remove backup tables after verification
-- =====================================================

-- DROP TABLE IF EXISTS statistics_daily_users_backup;
-- DROP TABLE IF EXISTS statistics_monthly_users_backup;
-- DROP TABLE IF EXISTS statistics_yearly_users_backup;

-- =====================================================
-- Verification queries (for manual check)
-- =====================================================

-- Check partition status:
-- SELECT TABLE_NAME, PARTITION_NAME, PARTITION_DESCRIPTION, TABLE_ROWS
-- FROM information_schema.PARTITIONS
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME LIKE 'statistics%users'
-- ORDER BY TABLE_NAME, PARTITION_NAME;

-- Check Event Scheduler status:
-- SHOW VARIABLES LIKE 'event_scheduler';

-- Check registered events:
-- SHOW EVENTS;
