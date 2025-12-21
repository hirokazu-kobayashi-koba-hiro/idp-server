-- ================================================
-- MySQL Performance Schema Access Setup
-- ================================================
-- This script grants SELECT permission on performance_schema
-- to the application user for query performance analysis.
--
-- By default, only root users can access performance_schema.
-- This script enables the idpserver user to run performance
-- analysis queries similar to PostgreSQL's pg_stat_statements.
--
-- Usage:
--   mysql -h 127.0.0.1 -P 3306 -u root -p < libs/idp-server-database/mysql/operation/grant-performance-schema.sql
--
-- After running this script, the idpserver user can execute:
--   SELECT * FROM performance_schema.events_statements_summary_by_digest;
-- ================================================

-- ================================================
-- 1. Grant SELECT permission on performance_schema
-- ================================================
GRANT SELECT ON performance_schema.* TO 'idpserver'@'%';

-- ================================================
-- 2. Apply permission changes
-- ================================================
FLUSH PRIVILEGES;

-- ================================================
-- 3. Verify permission granted
-- ================================================
SELECT
    GRANTEE,
    TABLE_SCHEMA,
    PRIVILEGE_TYPE
FROM information_schema.SCHEMA_PRIVILEGES
WHERE TABLE_SCHEMA = 'performance_schema'
  AND GRANTEE LIKE '%idpserver%';

-- ================================================
-- Example: Query performance analysis
-- ================================================
-- After granting permission, run the following query to analyze:
--
-- SELECT
--     DIGEST_TEXT AS query,
--     COUNT_STAR AS calls,
--     ROUND(SUM_TIMER_WAIT / 1000000000, 2) AS total_exec_time_ms,
--     ROUND(AVG_TIMER_WAIT / 1000000000, 2) AS avg_exec_time_ms,
--     SUM_ROWS_EXAMINED AS rows_examined,
--     SUM_ROWS_SENT AS rows_sent
-- FROM performance_schema.events_statements_summary_by_digest
-- WHERE SCHEMA_NAME = 'idpserver'
-- ORDER BY SUM_TIMER_WAIT DESC
-- LIMIT 20;
--
-- To reset statistics before a test:
-- TRUNCATE TABLE performance_schema.events_statements_summary_by_digest;
-- ================================================
