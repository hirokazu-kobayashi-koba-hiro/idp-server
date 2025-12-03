-- =====================================================
-- Historical Statistics Aggregation Script
--
-- This script aggregates historical security_event data
-- into the statistics tables (statistics_monthly,
-- statistics_daily_users, statistics_monthly_users)
--
-- Usage:
--   psql -h <host> -U <user> -d <database> -f aggregate_historical_statistics.sql
--
-- Or with date range:
--   psql -h <host> -U <user> -d <database> \
--     -v start_date="'2024-01-01'" \
--     -v end_date="'2024-12-31'" \
--     -f aggregate_historical_statistics.sql
-- =====================================================

\set ON_ERROR_STOP on

-- Set default date range if not provided (last 12 months)
SELECT COALESCE(:'start_date', (CURRENT_DATE - INTERVAL '12 months')::TEXT) AS start_date \gset
SELECT COALESCE(:'end_date', CURRENT_DATE::TEXT) AS end_date \gset

\echo '========================================'
\echo 'Historical Statistics Aggregation'
\echo '========================================'
\echo 'Date range:' :start_date 'to' :end_date

-- Show affected data count
SELECT
    COUNT(DISTINCT tenant_id) as tenant_count,
    COUNT(*) as event_count
FROM security_event
WHERE created_at >= :'start_date'::DATE
  AND created_at < :'end_date'::DATE + INTERVAL '1 day';

\echo '========================================'
\echo 'Step 1: Populating statistics_daily_users (DAU tracking)...'
\echo '========================================'

INSERT INTO statistics_daily_users (tenant_id, stat_date, user_id, created_at)
SELECT DISTINCT
    tenant_id,
    DATE(created_at) as stat_date,
    user_id,
    MIN(created_at) as created_at
FROM security_event
WHERE user_id IS NOT NULL
  AND type IN ('login_success', 'issue_token_success', 'refresh_token_success', 'inspect_token_success')
  AND created_at >= :'start_date'::DATE
  AND created_at < :'end_date'::DATE + INTERVAL '1 day'
GROUP BY tenant_id, DATE(created_at), user_id
ON CONFLICT (tenant_id, stat_date, user_id) DO NOTHING;

\echo 'Step 1 complete.'

\echo '========================================'
\echo 'Step 2: Populating statistics_monthly_users (MAU tracking)...'
\echo '========================================'

INSERT INTO statistics_monthly_users (tenant_id, stat_month, user_id, created_at)
SELECT DISTINCT
    tenant_id,
    TO_CHAR(created_at, 'YYYY-MM') as stat_month,
    user_id,
    MIN(created_at) as created_at
FROM security_event
WHERE user_id IS NOT NULL
  AND type IN ('login_success', 'issue_token_success', 'refresh_token_success', 'inspect_token_success')
  AND created_at >= :'start_date'::DATE
  AND created_at < :'end_date'::DATE + INTERVAL '1 day'
GROUP BY tenant_id, TO_CHAR(created_at, 'YYYY-MM'), user_id
ON CONFLICT (tenant_id, stat_month, user_id) DO NOTHING;

\echo 'Step 2 complete.'

\echo '========================================'
\echo 'Step 3: Creating temporary aggregation table...'
\echo '========================================'

-- Create temporary table for aggregation
DROP TABLE IF EXISTS temp_event_aggregation;

CREATE TEMP TABLE temp_event_aggregation AS
WITH daily_events AS (
    SELECT
        tenant_id,
        TO_CHAR(created_at, 'YYYY-MM') as stat_month,
        EXTRACT(DAY FROM created_at)::INT as day_num,
        type as event_type,
        COUNT(*) as event_count
    FROM security_event
    WHERE created_at >= :'start_date'::DATE
      AND created_at < :'end_date'::DATE + INTERVAL '1 day'
    GROUP BY tenant_id, TO_CHAR(created_at, 'YYYY-MM'), EXTRACT(DAY FROM created_at), type
),
daily_dau AS (
    SELECT
        tenant_id,
        TO_CHAR(stat_date, 'YYYY-MM') as stat_month,
        EXTRACT(DAY FROM stat_date)::INT as day_num,
        COUNT(DISTINCT user_id) as dau
    FROM statistics_daily_users
    WHERE stat_date >= :'start_date'::DATE
      AND stat_date < :'end_date'::DATE + INTERVAL '1 day'
    GROUP BY tenant_id, TO_CHAR(stat_date, 'YYYY-MM'), EXTRACT(DAY FROM stat_date)
),
monthly_mau AS (
    SELECT
        tenant_id,
        stat_month,
        COUNT(DISTINCT user_id) as mau
    FROM statistics_monthly_users
    WHERE stat_month >= TO_CHAR(:'start_date'::DATE, 'YYYY-MM')
      AND stat_month <= TO_CHAR(:'end_date'::DATE, 'YYYY-MM')
    GROUP BY tenant_id, stat_month
)
SELECT
    de.tenant_id,
    de.stat_month,
    de.day_num,
    de.event_type,
    de.event_count,
    COALESCE(dd.dau, 0) as dau,
    COALESCE(mm.mau, 0) as mau
FROM daily_events de
LEFT JOIN daily_dau dd ON de.tenant_id = dd.tenant_id
    AND de.stat_month = dd.stat_month
    AND de.day_num = dd.day_num
LEFT JOIN monthly_mau mm ON de.tenant_id = mm.tenant_id
    AND de.stat_month = mm.stat_month;

-- Calculate cumulative MAU per day
-- For each day, count all unique users who were first active on or before that day
DROP TABLE IF EXISTS temp_cumulative_mau;

CREATE TEMP TABLE temp_cumulative_mau AS
WITH all_days AS (
    -- Get all unique (tenant, month, day) combinations from events
    SELECT DISTINCT tenant_id, stat_month, day_num
    FROM temp_event_aggregation
),
first_activity AS (
    -- Get the first day each user was active in each month
    SELECT
        tenant_id,
        stat_month,
        user_id,
        MIN(EXTRACT(DAY FROM created_at)::INT) as first_day
    FROM statistics_monthly_users
    WHERE stat_month >= TO_CHAR(:'start_date'::DATE, 'YYYY-MM')
      AND stat_month <= TO_CHAR(:'end_date'::DATE, 'YYYY-MM')
    GROUP BY tenant_id, stat_month, user_id
)
SELECT
    ad.tenant_id,
    ad.stat_month,
    ad.day_num,
    COUNT(fa.user_id) as cumulative_mau
FROM all_days ad
LEFT JOIN first_activity fa
    ON ad.tenant_id = fa.tenant_id
    AND ad.stat_month = fa.stat_month
    AND fa.first_day <= ad.day_num
GROUP BY ad.tenant_id, ad.stat_month, ad.day_num;

\echo 'Step 3 complete.'

\echo '========================================'
\echo 'Step 4: Building statistics_monthly records...'
\echo '========================================'

-- Build monthly_summary and daily_metrics JSONB
INSERT INTO statistics_monthly (tenant_id, stat_month, monthly_summary, daily_metrics, created_at, updated_at)
SELECT
    t.tenant_id,
    t.stat_month,
    -- monthly_summary: mau + all event totals
    (
        SELECT jsonb_build_object('mau', MAX(t2.mau)) ||
               jsonb_object_agg(t2.event_type, t2.total_count)
        FROM (
            SELECT event_type, SUM(event_count) as total_count, MAX(mau) as mau
            FROM temp_event_aggregation
            WHERE tenant_id = t.tenant_id AND stat_month = t.stat_month
            GROUP BY event_type
        ) t2
    ) as monthly_summary,
    -- daily_metrics: per-day breakdown
    (
        SELECT jsonb_object_agg(days.day_num::TEXT, days.day_data)
        FROM (
            SELECT
                tea.day_num,
                (
                    jsonb_build_object('dau', MAX(tea.dau), 'mau', COALESCE(MAX(cm.cumulative_mau), 0)) ||
                    jsonb_object_agg(tea.event_type, tea.event_count)
                ) as day_data
            FROM temp_event_aggregation tea
            LEFT JOIN temp_cumulative_mau cm
                ON tea.tenant_id = cm.tenant_id
                AND tea.stat_month = cm.stat_month
                AND tea.day_num = cm.day_num
            WHERE tea.tenant_id = t.tenant_id AND tea.stat_month = t.stat_month
            GROUP BY tea.day_num
        ) days
    ) as daily_metrics,
    NOW() as created_at,
    NOW() as updated_at
FROM (
    SELECT DISTINCT tenant_id, stat_month
    FROM temp_event_aggregation
) t
ON CONFLICT (tenant_id, stat_month)
DO UPDATE SET
    monthly_summary = EXCLUDED.monthly_summary,
    daily_metrics = EXCLUDED.daily_metrics,
    updated_at = NOW();

\echo 'Step 4 complete.'

-- Cleanup
DROP TABLE IF EXISTS temp_event_aggregation;
DROP TABLE IF EXISTS temp_cumulative_mau;

\echo '========================================'
\echo 'Aggregation Complete!'
\echo '========================================'

-- Show summary
SELECT 'statistics_monthly' as table_name, COUNT(*) as row_count FROM statistics_monthly
UNION ALL
SELECT 'statistics_daily_users', COUNT(*) FROM statistics_daily_users
UNION ALL
SELECT 'statistics_monthly_users', COUNT(*) FROM statistics_monthly_users;

\echo '========================================'
