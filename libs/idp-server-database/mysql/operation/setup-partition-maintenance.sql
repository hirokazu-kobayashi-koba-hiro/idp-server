-- ================================================
-- MySQL Partition Maintenance Setup
-- ================================================
-- This script runs initial partition maintenance after Flyway migration.
-- It creates partitions for the next 90 days for each partitioned table.
--
-- Similar to PostgreSQL's pg_partman maintenance, this ensures
-- partitions are ready immediately after deployment.
--
-- Tables managed:
--   - security_event (90-day retention)
--   - security_event_hook_results (90-day retention)
--   - statistics_daily_users (90-day retention)
--   - statistics_monthly_users (13-month retention)
--   - statistics_yearly_users (60-month retention)
--
-- Usage:
--   mysql -h localhost -u user -p database < setup-partition-maintenance.sql
-- ================================================

-- ================================================
-- 1. Check Event Scheduler status
-- ================================================
SELECT
    CASE @@event_scheduler
        WHEN 'ON' THEN 'Event Scheduler is ON'
        ELSE CONCAT('WARNING: Event Scheduler is ', @@event_scheduler)
    END AS event_scheduler_status;

-- ================================================
-- 2. Run security event partition maintenance
-- ================================================
SELECT '=== Security Event Partition Maintenance ===' AS step;

-- Check if procedure exists
SELECT
    CASE
        WHEN COUNT(*) > 0 THEN 'Procedure maintain_security_event_partitions exists'
        ELSE 'WARNING: Procedure maintain_security_event_partitions NOT FOUND'
    END AS procedure_status
FROM information_schema.ROUTINES
WHERE ROUTINE_SCHEMA = DATABASE()
  AND ROUTINE_NAME = 'maintain_security_event_partitions';

-- Execute maintenance (ignore errors if procedure doesn't exist)
CALL maintain_security_event_partitions();

-- Show partition count after maintenance
SELECT
    TABLE_NAME,
    COUNT(*) AS partition_count
FROM information_schema.PARTITIONS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('security_event', 'security_event_hook_results')
GROUP BY TABLE_NAME;

-- ================================================
-- 3. Run statistics partition maintenance
-- ================================================
SELECT '=== Statistics Partition Maintenance ===' AS step;

-- Check if procedure exists
SELECT
    CASE
        WHEN COUNT(*) > 0 THEN 'Procedure maintain_statistics_partitions exists'
        ELSE 'WARNING: Procedure maintain_statistics_partitions NOT FOUND'
    END AS procedure_status
FROM information_schema.ROUTINES
WHERE ROUTINE_SCHEMA = DATABASE()
  AND ROUTINE_NAME = 'maintain_statistics_partitions';

-- Execute maintenance (ignore errors if procedure doesn't exist)
CALL maintain_statistics_partitions();

-- Show partition count after maintenance
SELECT
    TABLE_NAME,
    COUNT(*) AS partition_count
FROM information_schema.PARTITIONS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('statistics_daily_users', 'statistics_monthly_users', 'statistics_yearly_users')
GROUP BY TABLE_NAME;

-- ================================================
-- 4. Verify Event Scheduler jobs
-- ================================================
SELECT '=== Event Scheduler Jobs ===' AS step;

SELECT
    EVENT_NAME,
    STATUS,
    INTERVAL_VALUE,
    INTERVAL_FIELD,
    STARTS,
    LAST_EXECUTED
FROM information_schema.EVENTS
WHERE EVENT_SCHEMA = DATABASE()
  AND EVENT_NAME LIKE 'evt_maintain%'
ORDER BY EVENT_NAME;

-- ================================================
-- 5. Summary
-- ================================================
SELECT '=== Partition Setup Summary ===' AS step;

SELECT
    TABLE_NAME,
    COUNT(*) AS partition_count,
    SUM(TABLE_ROWS) AS total_rows,
    MIN(CASE WHEN PARTITION_NAME != 'p_future' THEN PARTITION_NAME END) AS oldest_partition,
    MAX(CASE WHEN PARTITION_NAME != 'p_future' THEN PARTITION_NAME END) AS newest_partition
FROM information_schema.PARTITIONS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN (
      'security_event',
      'security_event_hook_results',
      'statistics_daily_users',
      'statistics_monthly_users',
      'statistics_yearly_users'
  )
GROUP BY TABLE_NAME
ORDER BY TABLE_NAME;

SELECT 'Partition maintenance completed successfully' AS result;
