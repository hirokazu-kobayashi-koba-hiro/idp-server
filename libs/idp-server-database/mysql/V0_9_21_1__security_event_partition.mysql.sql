-- V0_9_21_1__security_event_partition.mysql.sql
-- Issue #1092: Daily Partitioning for Security Event Tables
--
-- Target tables:
--   - security_event (90-day retention)
--   - security_event_hook_results (90-day retention)
--
-- Note: audit_log is NOT partitioned (permanent retention for compliance)
--
-- Prerequisites:
--   - MySQL 8.0+
--   - Event Scheduler enabled: SET GLOBAL event_scheduler = ON;
--
-- Changes from original schema:
--   - PRIMARY KEY: (id) -> (id, created_at) to include partition key
--   - FOREIGN KEY: Removed (MySQL partitioned tables don't support foreign keys)
--   - Partitioning: RANGE COLUMNS on created_at

-- =====================================================
-- Phase 1: Backup existing data
-- =====================================================

CREATE TABLE IF NOT EXISTS security_event_backup AS
SELECT * FROM security_event;

CREATE TABLE IF NOT EXISTS security_event_hook_results_backup AS
SELECT * FROM security_event_hook_results;

-- =====================================================
-- Phase 2: Drop and recreate security_event as partitioned table
-- =====================================================

DROP TABLE IF EXISTS security_event;

CREATE TABLE security_event (
    id               CHAR(36)     NOT NULL,
    type             VARCHAR(255) NOT NULL,
    description      VARCHAR(255) NOT NULL,
    tenant_id        CHAR(36)     NOT NULL,
    tenant_name      VARCHAR(255) NOT NULL,
    client_id        VARCHAR(255) NOT NULL,
    client_name      VARCHAR(255) NOT NULL,
    user_id          CHAR(36),
    external_user_id VARCHAR(255),
    user_name        VARCHAR(255),
    login_hint       VARCHAR(255),
    ip_address       VARCHAR(45),
    user_agent       TEXT,
    detail           JSON         NOT NULL,
    created_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id, created_at),
    KEY idx_events_type (type),
    KEY idx_events_tenant (tenant_id),
    KEY idx_events_client (client_id),
    KEY idx_events_user (user_id),
    KEY idx_events_external_user_id (external_user_id),
    KEY idx_events_created_at (created_at),
    KEY idx_events_tenant_created_at (tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Security events with daily partitioning. Retained for 90 days.'
PARTITION BY RANGE COLUMNS(created_at) (
    PARTITION p_future VALUES LESS THAN MAXVALUE
);

-- =====================================================
-- Phase 3: Drop and recreate security_event_hook_results as partitioned table
-- =====================================================

DROP TABLE IF EXISTS security_event_hook_results;

CREATE TABLE security_event_hook_results (
    id                                    CHAR(36)     NOT NULL,
    tenant_id                             CHAR(36)     NOT NULL,
    security_event_id                     CHAR(36)     NOT NULL,
    security_event_type                   VARCHAR(255) NOT NULL,
    security_event_hook                   VARCHAR(255) NOT NULL,
    security_event_payload                JSON         NOT NULL,
    security_event_hook_execution_payload JSON         COMMENT 'Stores the execution result payload from security event hooks for resending and debugging purposes',
    status                                VARCHAR(255) NOT NULL,
    created_at                            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at                            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id, created_at),
    KEY idx_hook_results_tenant (tenant_id),
    KEY idx_hook_results_security_event (security_event_id),
    KEY idx_hook_results_status (status),
    KEY idx_hook_results_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Security event hook execution results with daily partitioning. Retained for 90 days.'
PARTITION BY RANGE COLUMNS(created_at) (
    PARTITION p_future VALUES LESS THAN MAXVALUE
);

-- =====================================================
-- Phase 4: Restore data from backup
-- =====================================================

INSERT INTO security_event
SELECT * FROM security_event_backup;

INSERT INTO security_event_hook_results
SELECT * FROM security_event_hook_results_backup;

-- =====================================================
-- Phase 5: Partition management stored procedures
-- =====================================================

DELIMITER //

-- -----------------------------------------------------
-- Create partition for security_event (daily)
-- -----------------------------------------------------
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

-- -----------------------------------------------------
-- Create partition for security_event_hook_results (daily)
-- -----------------------------------------------------
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

-- -----------------------------------------------------
-- Drop old partitions for security_event
-- -----------------------------------------------------
DROP PROCEDURE IF EXISTS drop_old_security_event_partitions//
CREATE PROCEDURE drop_old_security_event_partitions(IN retention_days INT)
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE p_name VARCHAR(64);
    DECLARE p_description VARCHAR(64);
    DECLARE cutoff_date DATE;
    DECLARE cur CURSOR FOR
        SELECT PARTITION_NAME, PARTITION_DESCRIPTION
        FROM information_schema.PARTITIONS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'security_event'
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

        IF STR_TO_DATE(REPLACE(p_description, '''', ''), '%Y-%m-%d %H:%i:%s') <= cutoff_date THEN
            SET @sql = CONCAT('ALTER TABLE security_event DROP PARTITION ', p_name);
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
    END LOOP;
    CLOSE cur;
END//

-- -----------------------------------------------------
-- Drop old partitions for security_event_hook_results
-- -----------------------------------------------------
DROP PROCEDURE IF EXISTS drop_old_security_event_hook_results_partitions//
CREATE PROCEDURE drop_old_security_event_hook_results_partitions(IN retention_days INT)
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE p_name VARCHAR(64);
    DECLARE p_description VARCHAR(64);
    DECLARE cutoff_date DATE;
    DECLARE cur CURSOR FOR
        SELECT PARTITION_NAME, PARTITION_DESCRIPTION
        FROM information_schema.PARTITIONS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'security_event_hook_results'
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

        IF STR_TO_DATE(REPLACE(p_description, '''', ''), '%Y-%m-%d %H:%i:%s') <= cutoff_date THEN
            SET @sql = CONCAT('ALTER TABLE security_event_hook_results DROP PARTITION ', p_name);
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
    END LOOP;
    CLOSE cur;
END//

-- -----------------------------------------------------
-- Main maintenance procedure for security events
-- -----------------------------------------------------
DROP PROCEDURE IF EXISTS maintain_security_event_partitions//
CREATE PROCEDURE maintain_security_event_partitions()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE target_date DATE;

    -- Create partitions for next 90 days
    SET i = 0;
    WHILE i < 90 DO
        SET target_date = DATE_ADD(CURDATE(), INTERVAL i DAY);
        CALL create_security_event_partition(target_date);
        CALL create_security_event_hook_results_partition(target_date);
        SET i = i + 1;
    END WHILE;

    -- Drop old partitions (90 days retention)
    CALL drop_old_security_event_partitions(90);
    CALL drop_old_security_event_hook_results_partitions(90);
END//

DELIMITER ;

-- =====================================================
-- Phase 6: Event Scheduler configuration
-- Runs daily at 02:30 AM (before statistics maintenance at 03:00)
-- =====================================================

DROP EVENT IF EXISTS evt_maintain_security_event_partitions;

CREATE EVENT IF NOT EXISTS evt_maintain_security_event_partitions
ON SCHEDULE EVERY 1 DAY
STARTS (TIMESTAMP(CURRENT_DATE) + INTERVAL 1 DAY + INTERVAL 2 HOUR + INTERVAL 30 MINUTE)
ON COMPLETION PRESERVE
ENABLE
COMMENT 'Daily maintenance of security event table partitions (90-day retention)'
DO
    CALL maintain_security_event_partitions();

-- =====================================================
-- Phase 7: Initial partition creation (run after migration)
-- =====================================================
-- Note: Similar to PostgreSQL's pg_partman, the maintenance procedure
-- should be run after the Flyway migration completes.
-- The Event Scheduler will handle this automatically at 02:30 AM.
--
-- To create partitions immediately after migration, run manually:
--   CALL maintain_security_event_partitions();
--
-- Or wait for the scheduled Event to execute.

-- =====================================================
-- Cleanup backup tables (optional - uncomment after verification)
-- =====================================================

-- DROP TABLE IF EXISTS security_event_backup;
-- DROP TABLE IF EXISTS security_event_hook_results_backup;

-- =====================================================
-- Verification queries
-- =====================================================

-- Check partition status:
-- SELECT TABLE_NAME, PARTITION_NAME, PARTITION_DESCRIPTION, TABLE_ROWS
-- FROM information_schema.PARTITIONS
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME IN ('security_event', 'security_event_hook_results')
-- ORDER BY TABLE_NAME, PARTITION_NAME;

-- Check Event Scheduler:
-- SHOW EVENTS WHERE Name LIKE '%security%';
