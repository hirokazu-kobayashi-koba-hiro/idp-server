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
