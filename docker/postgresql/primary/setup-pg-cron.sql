-- pg_cron extension setup and statistics cleanup jobs
-- Run: docker exec -i postgres-primary psql -U idpserver -d idpserver < setup-pg-cron.sql

-- 1. Create pg_cron extension
CREATE EXTENSION IF NOT EXISTS pg_cron;

-- 2. Verify installation
SELECT extname, extversion FROM pg_extension WHERE extname = 'pg_cron';

-- 3. Register cleanup jobs (see statistics-maintenance.md for details)

-- Daily cleanup: Delete daily_users older than 2 days (AM 3:00 JST = 18:00 UTC)
SELECT cron.schedule(
    'cleanup-daily-users',
    '0 18 * * *',
    $$SELECT cleanup_old_daily_users(2)$$
);

-- Monthly cleanup: Delete monthly_users older than 2 months (1st of month, AM 4:00 JST)
SELECT cron.schedule(
    'cleanup-monthly-users',
    '0 19 1 * *',
    $$SELECT cleanup_old_monthly_users(2)$$
);

-- Annual cleanup: Delete statistics older than 24 months (1st of month, AM 4:30 JST)
SELECT cron.schedule(
    'cleanup-old-statistics',
    '30 19 1 * *',
    $$SELECT cleanup_old_statistics(24)$$
);

-- 4. Verify registered jobs
SELECT jobid, jobname, schedule, command, active FROM cron.job;
