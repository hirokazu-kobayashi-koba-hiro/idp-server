-- V0_9_27__statistics_events.mysql.sql
-- Issue #1198: Statistics data update performance improvement (MySQL version)
--
-- New table for normalized statistics events:
--   - statistics_events (daily partitions, 90-day retention)
--
-- This table replaces JSONB updates in statistics_monthly with
-- row-based updates for better write performance.
-- Each (tenant_id, stat_date, event_type) combination is a separate row.
--
-- Prerequisites:
--   - MySQL 8.0+
--   - Event Scheduler enabled: SET GLOBAL event_scheduler = ON;

-- =====================================================
-- statistics_events (PARTITIONED - daily)
-- Track event counts per tenant/date/event_type
-- =====================================================

CREATE TABLE statistics_events (
    tenant_id CHAR(36) NOT NULL,
    stat_date DATE NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    count BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (tenant_id, stat_date, event_type),
    KEY idx_statistics_events_tenant_date (tenant_id, stat_date DESC),
    KEY idx_statistics_events_tenant_date_type (tenant_id, stat_date, event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Normalized statistics events with daily partitioning. Replaces JSONB updates for better write performance. Retained for 90 days.'
PARTITION BY RANGE COLUMNS(stat_date) (
    PARTITION p_future VALUES LESS THAN MAXVALUE
);

-- =====================================================
-- Partition management stored procedures
-- =====================================================

DELIMITER //

-- -----------------------------------------------------
-- Create partition for statistics_events (daily)
-- -----------------------------------------------------
DROP PROCEDURE IF EXISTS create_statistics_events_partition//
CREATE PROCEDURE create_statistics_events_partition(IN target_date DATE)
BEGIN
    DECLARE partition_exists INT DEFAULT 0;

    SET @p_name = CONCAT('p', DATE_FORMAT(target_date, '%Y%m%d'));
    SET @p_end = DATE_ADD(target_date, INTERVAL 1 DAY);

    SELECT COUNT(*) INTO partition_exists
    FROM information_schema.PARTITIONS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'statistics_events'
      AND PARTITION_NAME = @p_name;

    IF partition_exists = 0 THEN
        SET @sql = CONCAT(
            'ALTER TABLE statistics_events REORGANIZE PARTITION p_future INTO (',
            'PARTITION ', @p_name, ' VALUES LESS THAN (''', @p_end, '''), ',
            'PARTITION p_future VALUES LESS THAN MAXVALUE)'
        );
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//

-- -----------------------------------------------------
-- Drop old partitions for statistics_events
-- -----------------------------------------------------
DROP PROCEDURE IF EXISTS drop_old_statistics_events_partitions//
CREATE PROCEDURE drop_old_statistics_events_partitions(IN retention_days INT)
BEGIN
    DECLARE done INT DEFAULT FALSE;
    DECLARE p_name VARCHAR(64);
    DECLARE p_description VARCHAR(64);
    DECLARE cutoff_date DATE;
    DECLARE cur CURSOR FOR
        SELECT PARTITION_NAME, PARTITION_DESCRIPTION
        FROM information_schema.PARTITIONS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'statistics_events'
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
            SET @sql = CONCAT('ALTER TABLE statistics_events DROP PARTITION ', p_name);
            PREPARE stmt FROM @sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
    END LOOP;
    CLOSE cur;
END//

-- -----------------------------------------------------
-- Maintenance procedure for statistics_events
-- -----------------------------------------------------
DROP PROCEDURE IF EXISTS maintain_statistics_events_partitions//
CREATE PROCEDURE maintain_statistics_events_partitions()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE target_date DATE;

    -- Create partitions for next 90 days
    SET i = 0;
    WHILE i < 90 DO
        SET target_date = DATE_ADD(CURDATE(), INTERVAL i DAY);
        CALL create_statistics_events_partition(target_date);
        SET i = i + 1;
    END WHILE;

    -- Drop old partitions (90 days retention)
    CALL drop_old_statistics_events_partitions(90);
END//

DELIMITER ;

-- =====================================================
-- Event Scheduler configuration
-- Add maintenance for statistics_events to the daily schedule
-- =====================================================

-- Note: Event Scheduler must be enabled globally
-- SET GLOBAL event_scheduler = ON;

CREATE EVENT IF NOT EXISTS evt_maintain_statistics_events_partitions
ON SCHEDULE EVERY 1 DAY
STARTS (TIMESTAMP(CURRENT_DATE) + INTERVAL 1 DAY + INTERVAL 3 HOUR + INTERVAL 10 MINUTE)
ON COMPLETION PRESERVE
ENABLE
COMMENT 'Daily maintenance of statistics_events table partitions'
DO
    CALL maintain_statistics_events_partitions();
