-- =====================================================
-- Historical Statistics Aggregation Script
--
-- This script aggregates historical security_event data
-- into the statistics tables (statistics_monthly, statistics_yearly,
-- statistics_daily_users, statistics_monthly_users, statistics_yearly_users)
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

INSERT INTO statistics_daily_users (tenant_id, stat_date, user_id, last_used_at, created_at)
SELECT DISTINCT
    tenant_id,
    DATE(created_at) as stat_date,
    user_id,
    MAX(created_at) as last_used_at,
    MIN(created_at) as created_at
FROM security_event
WHERE user_id IS NOT NULL
  AND type IN ('login_success', 'issue_token_success', 'refresh_token_success', 'inspect_token_success')
  AND created_at >= :'start_date'::DATE
  AND created_at < :'end_date'::DATE + INTERVAL '1 day'
GROUP BY tenant_id, DATE(created_at), user_id
ON CONFLICT (tenant_id, stat_date, user_id) DO UPDATE
SET last_used_at = GREATEST(statistics_daily_users.last_used_at, EXCLUDED.last_used_at);

\echo 'Step 1 complete.'

\echo '========================================'
\echo 'Step 2: Populating statistics_monthly_users (MAU tracking)...'
\echo '========================================'

INSERT INTO statistics_monthly_users (tenant_id, stat_month, user_id, last_used_at, created_at)
SELECT DISTINCT
    tenant_id,
    TO_CHAR(created_at, 'YYYY-MM') as stat_month,
    user_id,
    MAX(created_at) as last_used_at,
    MIN(created_at) as created_at
FROM security_event
WHERE user_id IS NOT NULL
  AND type IN ('login_success', 'issue_token_success', 'refresh_token_success', 'inspect_token_success')
  AND created_at >= :'start_date'::DATE
  AND created_at < :'end_date'::DATE + INTERVAL '1 day'
GROUP BY tenant_id, TO_CHAR(created_at, 'YYYY-MM'), user_id
ON CONFLICT (tenant_id, stat_month, user_id) DO UPDATE
SET last_used_at = GREATEST(statistics_monthly_users.last_used_at, EXCLUDED.last_used_at);

\echo 'Step 2 complete.'

\echo '========================================'
\echo 'Step 3: Populating statistics_yearly_users (YAU tracking)...'
\echo '========================================'

INSERT INTO statistics_yearly_users (tenant_id, stat_year, user_id, last_used_at, created_at)
SELECT DISTINCT
    tenant_id,
    TO_CHAR(created_at, 'YYYY') as stat_year,
    user_id,
    MAX(created_at) as last_used_at,
    MIN(created_at) as created_at
FROM security_event
WHERE user_id IS NOT NULL
  AND type IN ('login_success', 'issue_token_success', 'refresh_token_success', 'inspect_token_success')
  AND created_at >= :'start_date'::DATE
  AND created_at < :'end_date'::DATE + INTERVAL '1 day'
GROUP BY tenant_id, TO_CHAR(created_at, 'YYYY'), user_id
ON CONFLICT (tenant_id, stat_year, user_id) DO UPDATE
SET last_used_at = GREATEST(statistics_yearly_users.last_used_at, EXCLUDED.last_used_at);

\echo 'Step 3 complete.'

\echo '========================================'
\echo 'Step 4: Creating temporary aggregation table...'
\echo '========================================'

-- Create temporary table for aggregation
DROP TABLE IF EXISTS temp_event_aggregation;

CREATE TEMP TABLE temp_event_aggregation AS
WITH daily_events AS (
    SELECT
        tenant_id,
        TO_CHAR(created_at, 'YYYY') as stat_year,
        TO_CHAR(created_at, 'YYYY-MM') as stat_month,
        TO_CHAR(created_at, 'YYYY-MM-DD') as stat_date,
        type as event_type,
        COUNT(*) as event_count
    FROM security_event
    WHERE created_at >= :'start_date'::DATE
      AND created_at < :'end_date'::DATE + INTERVAL '1 day'
    GROUP BY tenant_id, TO_CHAR(created_at, 'YYYY'), TO_CHAR(created_at, 'YYYY-MM'), TO_CHAR(created_at, 'YYYY-MM-DD'), type
),
daily_dau AS (
    SELECT
        tenant_id,
        TO_CHAR(stat_date, 'YYYY-MM') as stat_month,
        TO_CHAR(stat_date, 'YYYY-MM-DD') as stat_date,
        COUNT(DISTINCT user_id) as dau
    FROM statistics_daily_users
    WHERE stat_date >= :'start_date'::DATE
      AND stat_date < :'end_date'::DATE + INTERVAL '1 day'
    GROUP BY tenant_id, TO_CHAR(stat_date, 'YYYY-MM'), TO_CHAR(stat_date, 'YYYY-MM-DD')
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
),
yearly_yau AS (
    SELECT
        tenant_id,
        stat_year,
        COUNT(DISTINCT user_id) as yau
    FROM statistics_yearly_users
    WHERE stat_year >= TO_CHAR(:'start_date'::DATE, 'YYYY')
      AND stat_year <= TO_CHAR(:'end_date'::DATE, 'YYYY')
    GROUP BY tenant_id, stat_year
)
SELECT
    de.tenant_id,
    de.stat_year,
    de.stat_month,
    de.stat_date,
    de.event_type,
    de.event_count,
    COALESCE(dd.dau, 0) as dau,
    COALESCE(mm.mau, 0) as mau,
    COALESCE(yy.yau, 0) as yau
FROM daily_events de
LEFT JOIN daily_dau dd ON de.tenant_id = dd.tenant_id
    AND de.stat_month = dd.stat_month
    AND de.stat_date = dd.stat_date
LEFT JOIN monthly_mau mm ON de.tenant_id = mm.tenant_id
    AND de.stat_month = mm.stat_month
LEFT JOIN yearly_yau yy ON de.tenant_id = yy.tenant_id
    AND de.stat_year = yy.stat_year;

-- Calculate cumulative MAU per day
-- For each day, count all unique users who were first active on or before that day
DROP TABLE IF EXISTS temp_cumulative_mau;

CREATE TEMP TABLE temp_cumulative_mau AS
WITH all_days AS (
    -- Get all unique (tenant, month, date) combinations from events
    SELECT DISTINCT tenant_id, stat_month, stat_date
    FROM temp_event_aggregation
),
first_activity AS (
    -- Get the first date each user was active in each month
    SELECT
        tenant_id,
        stat_month,
        user_id,
        MIN(TO_CHAR(created_at, 'YYYY-MM-DD')) as first_date
    FROM statistics_monthly_users
    WHERE stat_month >= TO_CHAR(:'start_date'::DATE, 'YYYY-MM')
      AND stat_month <= TO_CHAR(:'end_date'::DATE, 'YYYY-MM')
    GROUP BY tenant_id, stat_month, user_id
)
SELECT
    ad.tenant_id,
    ad.stat_month,
    ad.stat_date,
    COUNT(fa.user_id) as cumulative_mau
FROM all_days ad
LEFT JOIN first_activity fa
    ON ad.tenant_id = fa.tenant_id
    AND ad.stat_month = fa.stat_month
    AND fa.first_date <= ad.stat_date
GROUP BY ad.tenant_id, ad.stat_month, ad.stat_date;

\echo 'Step 4 complete.'

\echo '========================================'
\echo 'Step 5: Building statistics_monthly records...'
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
        SELECT jsonb_object_agg(days.stat_date, days.day_data)
        FROM (
            SELECT
                tea.stat_date,
                (
                    jsonb_build_object('dau', MAX(tea.dau), 'mau', COALESCE(MAX(cm.cumulative_mau), 0)) ||
                    jsonb_object_agg(tea.event_type, tea.event_count)
                ) as day_data
            FROM temp_event_aggregation tea
            LEFT JOIN temp_cumulative_mau cm
                ON tea.tenant_id = cm.tenant_id
                AND tea.stat_month = cm.stat_month
                AND tea.stat_date = cm.stat_date
            WHERE tea.tenant_id = t.tenant_id AND tea.stat_month = t.stat_month
            GROUP BY tea.stat_date
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

\echo 'Step 5 complete.'

\echo '========================================'
\echo 'Step 6: Building statistics_yearly records...'
\echo '========================================'

-- Build yearly_summary JSONB (monthly breakdown is in statistics_monthly table)
INSERT INTO statistics_yearly (tenant_id, stat_year, yearly_summary, created_at, updated_at)
SELECT
    t.tenant_id,
    t.stat_year,
    -- yearly_summary: yau + all event totals
    (
        SELECT jsonb_build_object('yau', MAX(t2.yau)) ||
               jsonb_object_agg(t2.event_type, t2.total_count)
        FROM (
            SELECT event_type, SUM(event_count) as total_count, MAX(yau) as yau
            FROM temp_event_aggregation
            WHERE tenant_id = t.tenant_id AND stat_year = t.stat_year
            GROUP BY event_type
        ) t2
    ) as yearly_summary,
    NOW() as created_at,
    NOW() as updated_at
FROM (
    SELECT DISTINCT tenant_id, stat_year
    FROM temp_event_aggregation
) t
ON CONFLICT (tenant_id, stat_year)
DO UPDATE SET
    yearly_summary = EXCLUDED.yearly_summary,
    updated_at = NOW();

\echo 'Step 6 complete.'

-- Cleanup
DROP TABLE IF EXISTS temp_event_aggregation;
DROP TABLE IF EXISTS temp_cumulative_mau;

\echo '========================================'
\echo 'Aggregation Complete!'
\echo '========================================'

-- Show summary
SELECT 'statistics_monthly' as table_name, COUNT(*) as row_count FROM statistics_monthly
UNION ALL
SELECT 'statistics_yearly', COUNT(*) FROM statistics_yearly
UNION ALL
SELECT 'statistics_daily_users', COUNT(*) FROM statistics_daily_users
UNION ALL
SELECT 'statistics_monthly_users', COUNT(*) FROM statistics_monthly_users
UNION ALL
SELECT 'statistics_yearly_users', COUNT(*) FROM statistics_yearly_users;

\echo '========================================'
