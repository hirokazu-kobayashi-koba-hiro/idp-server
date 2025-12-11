-- ================================================
-- pg_cron job setup for idp-server
-- ================================================
-- This script registers scheduled jobs for:
--   - Partition maintenance (pg_partman)
--   - Archive processing (export to external storage)
--
-- It is designed to be idempotent (safe to run multiple times).

-- ================================================
-- 1. Remove existing jobs (if any) for idempotency
-- ================================================
DO $$
BEGIN
    -- Unschedule existing partman-maintenance job
    PERFORM cron.unschedule('partman-maintenance');
    RAISE NOTICE 'Removed existing partman-maintenance job';
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'No existing partman-maintenance job to remove';
END $$;

DO $$
BEGIN
    -- Unschedule existing archive-processing job
    PERFORM cron.unschedule('archive-processing');
    RAISE NOTICE 'Removed existing archive-processing job';
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'No existing archive-processing job to remove';
END $$;

-- ================================================
-- 2. Register pg_partman maintenance job
-- ================================================
-- Schedule: Daily at 02:00 UTC
-- This handles both:
--   - Creating new partitions (based on premake setting)
--   - Dropping old partitions (based on retention setting)
SELECT cron.schedule(
    'partman-maintenance',
    '0 2 * * *',
    $$CALL partman.run_maintenance_proc()$$
);

-- ================================================
-- 3. Register archive processing job
-- ================================================
-- Schedule: Daily at 03:00 UTC (1 hour after partman maintenance)
-- This ensures:
--   - Partitions are detached to archive schema by partman first
--   - Archive processing runs after partitions are ready
--   - 1-hour buffer allows partman to complete
--
-- The archive.process_archived_partitions function:
--   1. Finds all tables in archive schema
--   2. Attempts to export each to external storage
--   3. Drops tables only after successful export
--   4. Keeps tables if export fails (for retry next day)
--
-- Note: By default, the stub export function returns FALSE,
-- so tables will accumulate in archive schema until you
-- implement your cloud-specific export logic.

SELECT cron.schedule(
    'archive-processing',
    '0 3 * * *',
    $$SELECT * FROM archive.process_archived_partitions(p_dry_run := FALSE)$$
);

-- ================================================
-- 4. Verify registration
-- ================================================
DO $$
DECLARE
    v_partman_count INTEGER;
    v_archive_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO v_partman_count
    FROM cron.job
    WHERE jobname = 'partman-maintenance';

    SELECT COUNT(*) INTO v_archive_count
    FROM cron.job
    WHERE jobname = 'archive-processing';

    IF v_partman_count = 1 THEN
        RAISE NOTICE 'pg_cron job "partman-maintenance" registered successfully (02:00 UTC)';
    ELSE
        RAISE EXCEPTION 'Failed to register partman-maintenance job';
    END IF;

    IF v_archive_count = 1 THEN
        RAISE NOTICE 'pg_cron job "archive-processing" registered successfully (03:00 UTC)';
    ELSE
        RAISE EXCEPTION 'Failed to register archive-processing job';
    END IF;
END $$;

-- Show registered jobs
SELECT jobid, jobname, schedule, command, active
FROM cron.job
WHERE username = CURRENT_USER;
