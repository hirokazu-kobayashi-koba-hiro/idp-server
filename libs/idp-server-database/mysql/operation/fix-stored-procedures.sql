-- ================================================
-- Fix Stored Procedures for MySQL Partition Management
-- ================================================
-- This script recreates stored procedures with correct variable scoping.
-- Flyway cannot properly handle MySQL DELIMITER statements, so this script
-- is executed separately by the partition-setup container.
--
-- Issue: MySQL stored procedures use session variables (@var) instead of
-- local variables (DECLARE var) because PREPARE/EXECUTE runs in a separate
-- scope and cannot access local variables.
-- ================================================

DELIMITER //

-- =====================================================
-- Statistics partition procedures
-- =====================================================

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

-- =====================================================
-- Historical partition creation for yearly users
-- This procedure splits an existing partition to create
-- historical partitions for past fiscal years.
-- =====================================================
DROP PROCEDURE IF EXISTS create_historical_yearly_partitions//
CREATE PROCEDURE create_historical_yearly_partitions(IN months_back INT)
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE target_month DATE;
    DECLARE first_partition_name VARCHAR(20);
    DECLARE first_partition_boundary DATE;
    DECLARE partition_list TEXT DEFAULT '';
    DECLARE partition_exists INT DEFAULT 0;

    -- Find the first non-p_future partition
    SELECT PARTITION_NAME, STR_TO_DATE(REPLACE(REPLACE(PARTITION_DESCRIPTION, '''', ''), ' ', ''), '%Y-%m-%d')
    INTO first_partition_name, first_partition_boundary
    FROM information_schema.PARTITIONS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'statistics_yearly_users'
      AND PARTITION_NAME != 'p_future'
    ORDER BY PARTITION_ORDINAL_POSITION
    LIMIT 1;

    -- If no partition found, exit
    IF first_partition_name IS NULL THEN
        SELECT 'No partition found to reorganize' AS result;
    ELSE
        -- Build partition list for all months from (current - months_back) to first_partition_boundary
        SET i = months_back;
        WHILE i > 0 DO
            SET target_month = DATE_SUB(DATE_FORMAT(CURDATE(), '%Y-%m-01'), INTERVAL i MONTH);

            -- Only create if before the first partition boundary
            IF target_month < first_partition_boundary THEN
                SET @p_name = CONCAT('p', DATE_FORMAT(target_month, '%Y%m'));
                SET @p_end = DATE_ADD(target_month, INTERVAL 1 MONTH);

                -- Check if partition already exists
                SELECT COUNT(*) INTO partition_exists
                FROM information_schema.PARTITIONS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'statistics_yearly_users'
                  AND PARTITION_NAME = @p_name;

                IF partition_exists = 0 THEN
                    IF partition_list != '' THEN
                        SET partition_list = CONCAT(partition_list, ', ');
                    END IF;
                    SET partition_list = CONCAT(partition_list,
                        'PARTITION ', @p_name, ' VALUES LESS THAN (''', @p_end, ''')');
                END IF;
            END IF;

            SET i = i - 1;
        END WHILE;

        -- Add the original first partition back
        IF partition_list != '' THEN
            SET partition_list = CONCAT(partition_list, ', ',
                'PARTITION ', first_partition_name, ' VALUES LESS THAN (''', first_partition_boundary, ''')');

            SET @sql = CONCAT(
                'ALTER TABLE statistics_yearly_users REORGANIZE PARTITION ',
                first_partition_name, ' INTO (', partition_list, ')'
            );

            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;

            SELECT CONCAT('Created historical partitions: ', partition_list) AS result;
        ELSE
            SELECT 'No historical partitions needed' AS result;
        END IF;
    END IF;
END//

-- =====================================================
-- Updated maintain_statistics_partitions with historical support
-- =====================================================
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

    -- statistics_yearly_users: Create historical partitions (60 months back for fiscal year support)
    CALL create_historical_yearly_partitions(60);

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
-- Security event partition procedures
-- =====================================================

DROP PROCEDURE IF EXISTS create_security_event_partition//
CREATE PROCEDURE create_security_event_partition(IN target_date DATE)
BEGIN
    DECLARE partition_exists INT DEFAULT 0;

    SET @p_name = CONCAT('p', DATE_FORMAT(target_date, '%Y%m%d'));
    SET @p_end = DATE_ADD(target_date, INTERVAL 1 DAY);

    SELECT COUNT(*) INTO partition_exists
    FROM information_schema.PARTITIONS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'security_event'
      AND PARTITION_NAME = @p_name;

    IF partition_exists = 0 THEN
        SET @sql = CONCAT(
            'ALTER TABLE security_event REORGANIZE PARTITION p_future INTO (',
            'PARTITION ', @p_name, ' VALUES LESS THAN (''', @p_end, '''), ',
            'PARTITION p_future VALUES LESS THAN MAXVALUE)'
        );
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//

DROP PROCEDURE IF EXISTS create_security_event_hook_results_partition//
CREATE PROCEDURE create_security_event_hook_results_partition(IN target_date DATE)
BEGIN
    DECLARE partition_exists INT DEFAULT 0;

    SET @p_name = CONCAT('p', DATE_FORMAT(target_date, '%Y%m%d'));
    SET @p_end = DATE_ADD(target_date, INTERVAL 1 DAY);

    SELECT COUNT(*) INTO partition_exists
    FROM information_schema.PARTITIONS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'security_event_hook_results'
      AND PARTITION_NAME = @p_name;

    IF partition_exists = 0 THEN
        SET @sql = CONCAT(
            'ALTER TABLE security_event_hook_results REORGANIZE PARTITION p_future INTO (',
            'PARTITION ', @p_name, ' VALUES LESS THAN (''', @p_end, '''), ',
            'PARTITION p_future VALUES LESS THAN MAXVALUE)'
        );
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//

DELIMITER ;

SELECT 'Stored procedures fixed successfully' AS result;
