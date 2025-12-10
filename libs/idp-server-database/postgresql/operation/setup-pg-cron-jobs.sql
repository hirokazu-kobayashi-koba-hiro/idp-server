-- ================================================
-- pg_cron job setup for idp-server
-- ================================================
-- This script registers scheduled jobs for partition maintenance.
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
-- 3. Verify registration
-- ================================================
DO $$
DECLARE
    job_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO job_count
    FROM cron.job
    WHERE jobname = 'partman-maintenance';

    IF job_count = 1 THEN
        RAISE NOTICE 'pg_cron job "partman-maintenance" registered successfully';
    ELSE
        RAISE EXCEPTION 'Failed to register pg_cron job';
    END IF;
END $$;

-- Show registered jobs
SELECT jobid, jobname, schedule, command, active
FROM cron.job
WHERE username = CURRENT_USER;
