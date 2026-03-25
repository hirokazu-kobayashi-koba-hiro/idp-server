-- =====================================================
-- Statistics aggregation benchmark: test data generator
-- =====================================================
--
-- Generates configurable volumes of security_event data
-- for benchmarking aggregate_daily_statistics().
--
-- Usage:
--   -- Generate 100K events (default)
--   psql -h localhost -U idpserver -d idpserver \
--     -f generate_statistics_test_data.sql
--
--   -- Generate 1M events
--   psql -h localhost -U idpserver -d idpserver \
--     -v target_count=1000000 \
--     -f generate_statistics_test_data.sql
--
--   -- Generate 5M events for a specific date
--   psql -h localhost -U idpserver -d idpserver \
--     -v target_count=5000000 \
--     -v target_date="'2026-03-20'" \
--     -f generate_statistics_test_data.sql
--
-- Cleanup:
--   DELETE FROM security_event WHERE description = 'benchmark-test-data';
--
-- =====================================================

\set ON_ERROR_STOP on

-- Defaults
SELECT COALESCE(:'target_count', '100000')::int AS target_count \gset
SELECT COALESCE(:'target_date', (CURRENT_DATE - INTERVAL '1 day')::text) AS target_date \gset

\echo ''
\echo '======================================'
\echo 'Statistics Aggregation Benchmark'
\echo '======================================'
\echo 'Target count:' :target_count
\echo 'Target date: ' :target_date
\echo ''

-- =====================================================
-- Step 1: Create tenant pool for test data
-- =====================================================
\echo 'Step 1: Preparing tenant pool...'

CREATE TEMP TABLE tmp_bench_tenants (
    idx INT,
    tenant_id UUID,
    tenant_name VARCHAR(255),
    tz TEXT,
    fiscal_month INT
);

-- Use existing tenants with different TZ configurations
-- to test timezone-aware aggregation
INSERT INTO tmp_bench_tenants (idx, tenant_id, tenant_name, tz, fiscal_month)
VALUES
    -- Asia/Tokyo tenants (UTC+9) - fiscal year April
    (0, '67e7eae6-62b0-4500-9eff-87459f63fc66', 'test-tenant', 'Asia/Tokyo', 4),
    -- UTC tenants (default) - calendar year
    (1, '952f6906-3e95-4ed3-86b2-981f90f785f9', 'organizer-tenant', 'UTC', 1),
    -- UTC tenants - fiscal year March
    (2, '94d8598e-f238-4150-85c2-c4accf515784', 'tenant1', 'UTC', 3);

SELECT COUNT(*) AS tenant_pool_size FROM tmp_bench_tenants;

-- =====================================================
-- Step 2: Create event type pool
-- =====================================================
\echo 'Step 2: Preparing event types...'

CREATE TEMP TABLE tmp_bench_event_types (
    idx INT,
    event_type VARCHAR(255),
    has_user BOOLEAN
);

INSERT INTO tmp_bench_event_types (idx, event_type, has_user) VALUES
    -- Active user events (60% of total)
    (0,  'login_success',          true),
    (1,  'login_success',          true),
    (2,  'login_success',          true),
    (3,  'issue_token_success',    true),
    (4,  'issue_token_success',    true),
    (5,  'refresh_token_success',  true),
    -- Non-active-user events (40% of total)
    (6,  'login_failure',          true),
    (7,  'login_failure',          true),
    (8,  'user_registration',     false),
    (9,  'password_change',        true);

SELECT COUNT(*) AS event_type_pool_size FROM tmp_bench_event_types;

-- =====================================================
-- Step 3: Generate events
-- =====================================================
\echo 'Step 3: Generating events...'

-- Pre-calculate the UTC time window for the target date
-- Events are spread across the full 24h of the target date
-- with timezone distribution:
--   Asia/Tokyo: [target_date 00:00 JST .. 23:59 JST] = [target_date-1 15:00 UTC .. target_date 14:59 UTC]
--   UTC:        [target_date 00:00 UTC .. 23:59 UTC]

\echo 'Inserting' :target_count 'events...'

INSERT INTO security_event (
    id, type, description,
    tenant_id, tenant_name,
    client_id, client_name,
    user_id, user_name,
    detail, created_at
)
SELECT
    gen_random_uuid(),
    et.event_type,
    'benchmark-test-data',
    t.tenant_id,
    t.tenant_name,
    'bench-client-' || (g % 5),
    'Benchmark Client ' || (g % 5),
    CASE WHEN et.has_user
        THEN ('00000000-0000-0000-0000-' || LPAD((g % 10000)::text, 12, '0'))::uuid
        ELSE NULL
    END,
    CASE WHEN et.has_user
        THEN 'bench-user-' || (g % 10000)
        ELSE NULL
    END,
    '{}'::jsonb,
    (:'target_date'::date::timestamp
        + (random() * INTERVAL '24 hours')
    ) AT TIME ZONE t.tz AT TIME ZONE 'UTC'
FROM generate_series(1, :target_count) AS g
CROSS JOIN LATERAL (
    SELECT * FROM tmp_bench_tenants WHERE idx = g % 3
) t
CROSS JOIN LATERAL (
    SELECT * FROM tmp_bench_event_types WHERE idx = g % 10
) et;

\echo 'Insert complete.'

-- =====================================================
-- Step 4: Verify
-- =====================================================
\echo ''
\echo 'Step 4: Verification...'

SELECT
    'Total benchmark events' AS metric,
    COUNT(*)::text AS value
FROM security_event
WHERE description = 'benchmark-test-data'

UNION ALL

SELECT
    'Events on target date (UTC)',
    COUNT(*)::text
FROM security_event
WHERE description = 'benchmark-test-data'
  AND created_at::date = :'target_date'::date

UNION ALL

SELECT
    'Unique users',
    COUNT(DISTINCT user_id)::text
FROM security_event
WHERE description = 'benchmark-test-data'

UNION ALL

SELECT
    'Partition sizes',
    string_agg(
        tablename || '=' || pg_size_pretty(pg_total_relation_size(schemaname || '.' || tablename)),
        ', ' ORDER BY tablename
    )
FROM pg_tables
WHERE tablename LIKE 'security_event_p%'
  AND pg_total_relation_size(schemaname || '.' || tablename) > 8192;

-- Per-tenant breakdown
SELECT
    t.tenant_name,
    t.tz,
    COUNT(*) AS events,
    COUNT(DISTINCT se.user_id) AS unique_users,
    MIN(se.created_at)::timestamp(0) AS min_utc,
    MAX(se.created_at)::timestamp(0) AS max_utc
FROM security_event se
JOIN tmp_bench_tenants t ON se.tenant_id = t.tenant_id
WHERE se.description = 'benchmark-test-data'
GROUP BY t.tenant_name, t.tz
ORDER BY events DESC;

\echo ''
\echo '======================================'
\echo 'Ready to benchmark!'
\echo '======================================'
\echo ''
\echo 'Run benchmark:'
\echo '  SELECT * FROM aggregate_daily_statistics(''' :target_date '''::date);'
\echo ''
\echo 'Cleanup:'
\echo '  DELETE FROM security_event WHERE description = ''benchmark-test-data'';'
\echo ''

-- Cleanup temp tables
DROP TABLE tmp_bench_tenants;
DROP TABLE tmp_bench_event_types;
