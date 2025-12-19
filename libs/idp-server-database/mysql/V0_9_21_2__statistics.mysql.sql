-- V0_9_21_2__statistics.mysql.sql
-- Issue #441: Tenant statistics data collection (DAU/MAU/YAU) (MySQL version)
--
-- Partitioned tables:
--   - statistics_daily_users (daily partitions, 90-day retention)
--   - statistics_monthly_users (monthly partitions, 13-month retention)
--   - statistics_yearly_users (monthly partitions, 60-month retention, fiscal year support)
--
-- Non-partitioned tables (summary):
--   - statistics_monthly
--   - statistics_yearly
--
-- Prerequisites:
--   - MySQL 8.0+
--   - Event Scheduler enabled: SET GLOBAL event_scheduler = ON;

-- =====================================================
-- statistics_monthly
-- Monthly statistics with daily breakdown in JSON
-- =====================================================

CREATE TABLE statistics_monthly (
    id CHAR(36) PRIMARY KEY DEFAULT (UUID()),
    tenant_id CHAR(36) NOT NULL,
    stat_month DATE NOT NULL COMMENT 'First day of month (e.g., 2025-01-01, 2025-02-01, ...)',

    -- Monthly summary (for quick access)
    monthly_summary JSON NOT NULL DEFAULT ('{}'),
    /* {
      "mau": 5000,
      "total_logins": 45000,
      "total_login_failures": 500,
      "new_users": 200
    } */

    -- Daily breakdown (for charts/graphs)
    daily_metrics JSON NOT NULL DEFAULT ('{}'),
    /* {
      "01": {"dau": 100, "logins": 1500, "failures": 20},
      "02": {"dau": 110, "logins": 1600, "failures": 15},
      ...
      "31": {"dau": 95, "logins": 1400, "failures": 18}
    } */

    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    UNIQUE KEY uk_tenant_month (tenant_id, stat_month),
    KEY idx_statistics_monthly_tenant_month (tenant_id, stat_month DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Monthly tenant statistics with daily breakdown in JSON';

-- =====================================================
-- statistics_yearly
-- Yearly statistics summary with tenant-specific fiscal year support
-- Note: Monthly breakdown is available via statistics_monthly table
--
-- Fiscal year examples:
--   - Japan: April (stat_year = 2025-04-01 for FY2025)
--   - US (some): October (stat_year = 2025-10-01 for FY2025)
--   - Calendar year: January (stat_year = 2025-01-01 for FY2025)
-- =====================================================

CREATE TABLE statistics_yearly (
    id CHAR(36) PRIMARY KEY DEFAULT (UUID()),
    tenant_id CHAR(36) NOT NULL,
    stat_year DATE NOT NULL COMMENT 'Fiscal year start date (e.g., 2025-04-01 for April fiscal year)',

    -- Yearly summary (for quick access)
    yearly_summary JSON NOT NULL DEFAULT ('{}'),
    /* {
      "yau": 15000,
      "total_logins": 500000,
      "total_login_failures": 5000,
      "new_users": 2000
    } */

    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    UNIQUE KEY uk_tenant_year (tenant_id, stat_year),
    KEY idx_statistics_yearly_tenant_year (tenant_id, stat_year DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Yearly tenant statistics summary with fiscal year support';

-- =====================================================
-- statistics_daily_users (PARTITIONED - daily)
-- Track unique daily active users
-- =====================================================

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

-- =====================================================
-- statistics_monthly_users (PARTITIONED - monthly)
-- Track unique monthly active users
-- =====================================================

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

-- =====================================================
-- statistics_yearly_users (PARTITIONED - monthly for fiscal year)
-- Track unique yearly active users with tenant-specific fiscal year
--
-- Fiscal year examples:
--   - Japan: April (stat_year = 2025-04-01 for FY2025)
--   - US (some): October (stat_year = 2025-10-01 for FY2025)
--   - Calendar year: January (stat_year = 2025-01-01 for FY2025)
-- =====================================================

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
-- Partition management stored procedures
-- =====================================================

DELIMITER //

-- -----------------------------------------------------
-- Create partition for statistics_daily_users (daily)
-- -----------------------------------------------------
DROP PROCEDURE IF EXISTS create_daily_users_partition//
CREATE PROCEDURE create_daily_users_partition(IN target_date DATE)
BEGIN
    DECLARE partition_exists INT DEFAULT 0;

    SET @p_name = CONCAT('p', DATE_FORMAT(target_date, '%Y%m%d'));
    SET @p_end = DATE_ADD(target_date, INTERVAL 1 DAY);

    SELECT COUNT(*) INTO partition_exists
    FROM information_schema.PARTITIONS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'statistics_daily_users'
      AND PARTITION_NAME = @p_name;

    IF partition_exists = 0 THEN
        SET @sql = CONCAT(
            'ALTER TABLE statistics_daily_users REORGANIZE PARTITION p_future INTO (',
            'PARTITION ', @p_name, ' VALUES LESS THAN (''', @p_end, '''), ',
            'PARTITION p_future VALUES LESS THAN MAXVALUE)'
        );
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//

-- -----------------------------------------------------
-- Create partition for statistics_monthly_users (monthly)
-- -----------------------------------------------------
DROP PROCEDURE IF EXISTS create_monthly_users_partition//
CREATE PROCEDURE create_monthly_users_partition(IN target_date DATE)
BEGIN
    DECLARE partition_exists INT DEFAULT 0;

    SET @p_start = DATE_FORMAT(target_date, '%Y-%m-01');
    SET @p_end = DATE_ADD(@p_start, INTERVAL 1 MONTH);
    SET @p_name = CONCAT('p', DATE_FORMAT(@p_start, '%Y%m'));

    SELECT COUNT(*) INTO partition_exists
    FROM information_schema.PARTITIONS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'statistics_monthly_users'
      AND PARTITION_NAME = @p_name;

    IF partition_exists = 0 THEN
        SET @sql = CONCAT(
            'ALTER TABLE statistics_monthly_users REORGANIZE PARTITION p_future INTO (',
            'PARTITION ', @p_name, ' VALUES LESS THAN (''', @p_end, '''), ',
            'PARTITION p_future VALUES LESS THAN MAXVALUE)'
        );
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//

-- -----------------------------------------------------
-- Create partition for statistics_yearly_users (monthly)
-- -----------------------------------------------------
DROP PROCEDURE IF EXISTS create_yearly_users_partition//
CREATE PROCEDURE create_yearly_users_partition(IN target_date DATE)
BEGIN
    DECLARE partition_exists INT DEFAULT 0;

    SET @p_start = DATE_FORMAT(target_date, '%Y-%m-01');
    SET @p_end = DATE_ADD(@p_start, INTERVAL 1 MONTH);
    SET @p_name = CONCAT('p', DATE_FORMAT(@p_start, '%Y%m'));

    SELECT COUNT(*) INTO partition_exists
    FROM information_schema.PARTITIONS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'statistics_yearly_users'
      AND PARTITION_NAME = @p_name;

    IF partition_exists = 0 THEN
        SET @sql = CONCAT(
            'ALTER TABLE statistics_yearly_users REORGANIZE PARTITION p_future INTO (',
            'PARTITION ', @p_name, ' VALUES LESS THAN (''', @p_end, '''), ',
            'PARTITION p_future VALUES LESS THAN MAXVALUE)'
        );
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//

-- -----------------------------------------------------
-- Drop old partitions for statistics_daily_users
-- -----------------------------------------------------
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

-- -----------------------------------------------------
-- Drop old partitions for statistics_monthly_users
-- -----------------------------------------------------
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

-- -----------------------------------------------------
-- Drop old partitions for statistics_yearly_users
-- -----------------------------------------------------
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

-- -----------------------------------------------------
-- Main maintenance procedure (called by Event Scheduler)
-- -----------------------------------------------------
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

-- =====================================================
-- Data retention procedures (for non-partitioned tables)
-- =====================================================

-- Delete statistics_monthly data older than specified months
DROP PROCEDURE IF EXISTS cleanup_old_statistics//
CREATE PROCEDURE cleanup_old_statistics(IN retention_months INT)
BEGIN
    DECLARE cutoff_date DATE;
    SET cutoff_date = DATE_SUB(DATE_FORMAT(CURDATE(), '%Y-%m-01'), INTERVAL retention_months MONTH);

    DELETE FROM statistics_monthly
    WHERE stat_month < cutoff_date;

    SELECT ROW_COUNT() AS deleted_count;
END//

-- Delete statistics_yearly data older than specified months
CREATE PROCEDURE cleanup_old_yearly_statistics(IN retention_months INT)
BEGIN
    DECLARE cutoff_date DATE;
    SET cutoff_date = DATE_SUB(CURDATE(), INTERVAL retention_months MONTH);

    DELETE FROM statistics_yearly
    WHERE stat_year < cutoff_date;

    SELECT ROW_COUNT() AS deleted_count;
END//

DELIMITER ;

-- =====================================================
-- Event Scheduler configuration
-- Runs daily at 03:00 AM
-- =====================================================

-- Note: Event Scheduler must be enabled globally
-- SET GLOBAL event_scheduler = ON;

CREATE EVENT IF NOT EXISTS evt_maintain_statistics_partitions
ON SCHEDULE EVERY 1 DAY
STARTS (TIMESTAMP(CURRENT_DATE) + INTERVAL 1 DAY + INTERVAL 3 HOUR)
ON COMPLETION PRESERVE
ENABLE
COMMENT 'Daily maintenance of statistics table partitions'
DO
    CALL maintain_statistics_partitions();

-- =====================================================
-- Initial partition setup
-- Create partitions for current and upcoming periods
-- =====================================================

-- Create initial partitions by calling maintenance procedure
-- This will be executed once during migration
-- Note: Manual execution if Event Scheduler is not yet enabled
-- CALL maintain_statistics_partitions();
