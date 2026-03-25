/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

-- =====================================================
-- Timezone-aware daily statistics aggregation
--
-- Runs hourly via pg_cron. Each execution only processes
-- timezone groups whose "yesterday" has just completed
-- (local hour = 00:00-00:59).
--
-- Benefits over full daily batch (02):
--   - Each TZ group is processed exactly once, right after its day ends
--   - No redundant re-scans or UPSERTs
--   - Scan window is exactly 24h per TZ (vs 50h for all-at-once)
--   - Load is distributed across hours instead of spiking at one time
--   - Japanese tenants see yesterday's data by 01:00 JST (翌朝出社前)
--
-- Schedule: pg_cron every hour at :30
--   SELECT cron.schedule('hourly-tz-statistics', '30 * * * *',
--       $$SELECT * FROM aggregate_statistics_by_timezone()$$);
--
-- Idempotent: safe to re-run (absolute count overwrite).
-- =====================================================

-- =====================================================
-- Main entry point: called by pg_cron every hour
-- Finds TZ groups whose local time is 00:00-00:59
-- and aggregates their previous day's statistics.
-- =====================================================
CREATE OR REPLACE FUNCTION aggregate_statistics_by_timezone()
RETURNS TABLE (
    step TEXT,
    rows_affected BIGINT
) AS $$
DECLARE
    v_tz RECORD;
    v_target_date DATE;
    v_local_hour INT;
    r RECORD;
BEGIN
    -- Find timezone groups where local time is currently 00:xx
    -- These are the TZ groups whose "yesterday" just completed.
    FOR v_tz IN
        SELECT DISTINCT
            COALESCE(attributes->>'timezone', 'UTC') AS tz
        FROM tenant
    LOOP
        v_local_hour := EXTRACT(HOUR FROM (NOW() AT TIME ZONE v_tz.tz));

        IF v_local_hour = 0 THEN
            v_target_date := (NOW() AT TIME ZONE v_tz.tz)::date - 1;

            -- Return a marker row showing which TZ is being processed
            step := 'tz_group: ' || v_tz.tz || ' → ' || v_target_date::text;
            rows_affected := 0;
            RETURN NEXT;

            -- Delegate to the per-TZ aggregation function
            FOR r IN
                SELECT * FROM aggregate_statistics_for_timezone(v_tz.tz, v_target_date)
            LOOP
                step := v_tz.tz || '/' || r.step;
                rows_affected := r.rows_affected;
                RETURN NEXT;
            END LOOP;
        END IF;
    END LOOP;
END;
$$ LANGUAGE plpgsql;

-- =====================================================
-- Per-timezone aggregation function
--
-- Aggregates statistics for a specific timezone group
-- and target date. Scans exactly 24h of data.
--
-- Can also be called directly for manual execution:
--   SELECT * FROM aggregate_statistics_for_timezone('Asia/Tokyo', '2026-03-24');
-- =====================================================
CREATE OR REPLACE FUNCTION aggregate_statistics_for_timezone(
    p_tz TEXT,
    p_target_date DATE
)
RETURNS TABLE (
    step TEXT,
    rows_affected BIGINT
) AS $$
DECLARE
    v_count BIGINT;
    v_utc_start TIMESTAMP;
    v_utc_end TIMESTAMP;
BEGIN
    -- Compute exact 24h UTC window for this timezone's target date
    -- e.g. Asia/Tokyo, 2026-03-24 → [2026-03-23 15:00 UTC, 2026-03-24 15:00 UTC)
    v_utc_start := p_target_date::timestamp AT TIME ZONE p_tz AT TIME ZONE 'UTC';
    v_utc_end   := (p_target_date + 1)::timestamp AT TIME ZONE p_tz AT TIME ZONE 'UTC';

    -- Build tenant list for this TZ group
    DROP TABLE IF EXISTS tmp_tz_tenants;
    CREATE TEMP TABLE tmp_tz_tenants AS
    SELECT
        t.id AS tenant_id,
        COALESCE((t.attributes->>'fiscal_year_start_month')::int, 1) AS fiscal_month
    FROM tenant t
    WHERE COALESCE(t.attributes->>'timezone', 'UTC') = p_tz;

    CREATE INDEX ON tmp_tz_tenants (tenant_id);

    -- =====================================================
    -- Step 1: Aggregate event counts into statistics_events
    -- =====================================================
    INSERT INTO statistics_events (tenant_id, stat_date, event_type, count)
    SELECT
        ev.tenant_id,
        p_target_date,
        ev.type,
        COUNT(*)
    FROM security_event ev
    JOIN tmp_tz_tenants tt ON ev.tenant_id = tt.tenant_id
    WHERE ev.created_at >= v_utc_start
      AND ev.created_at < v_utc_end
    GROUP BY ev.tenant_id, ev.type
    ON CONFLICT (tenant_id, stat_date, event_type)
    DO UPDATE SET count = EXCLUDED.count,
                  updated_at = CURRENT_TIMESTAMP;

    GET DIAGNOSTICS v_count = ROW_COUNT;
    step := 'statistics_events';
    rows_affected := v_count;
    RETURN NEXT;

    -- =====================================================
    -- Step 2: Populate daily active users (DAU)
    -- =====================================================
    INSERT INTO statistics_daily_users (tenant_id, stat_date, user_id, user_name, last_used_at, created_at)
    SELECT
        ev.tenant_id,
        p_target_date,
        ev.user_id,
        COALESCE(ev.user_name, ''),
        MAX(ev.created_at),
        MIN(ev.created_at)
    FROM security_event ev
    JOIN tmp_tz_tenants tt ON ev.tenant_id = tt.tenant_id
    WHERE ev.created_at >= v_utc_start
      AND ev.created_at < v_utc_end
      AND ev.user_id IS NOT NULL
      AND ev.type IN ('login_success', 'issue_token_success', 'refresh_token_success', 'inspect_token_success')
    GROUP BY ev.tenant_id, ev.user_id, ev.user_name
    ON CONFLICT (tenant_id, stat_date, user_id)
    DO UPDATE SET last_used_at = GREATEST(statistics_daily_users.last_used_at, EXCLUDED.last_used_at);

    GET DIAGNOSTICS v_count = ROW_COUNT;
    step := 'statistics_daily_users';
    rows_affected := v_count;
    RETURN NEXT;

    -- =====================================================
    -- Step 3: Populate monthly active users (MAU)
    -- =====================================================
    INSERT INTO statistics_monthly_users (tenant_id, stat_month, user_id, user_name, last_used_at, created_at)
    SELECT
        ev.tenant_id,
        DATE_TRUNC('month', p_target_date)::date,
        ev.user_id,
        COALESCE(ev.user_name, ''),
        MAX(ev.created_at),
        MIN(ev.created_at)
    FROM security_event ev
    JOIN tmp_tz_tenants tt ON ev.tenant_id = tt.tenant_id
    WHERE ev.created_at >= v_utc_start
      AND ev.created_at < v_utc_end
      AND ev.user_id IS NOT NULL
      AND ev.type IN ('login_success', 'issue_token_success', 'refresh_token_success', 'inspect_token_success')
    GROUP BY ev.tenant_id, ev.user_id, ev.user_name
    ON CONFLICT (tenant_id, stat_month, user_id)
    DO UPDATE SET last_used_at = GREATEST(statistics_monthly_users.last_used_at, EXCLUDED.last_used_at);

    GET DIAGNOSTICS v_count = ROW_COUNT;
    step := 'statistics_monthly_users';
    rows_affected := v_count;
    RETURN NEXT;

    -- =====================================================
    -- Step 4: Populate yearly active users (YAU)
    --
    -- Fiscal year logic (same as FiscalYearCalculator.java):
    --   candidateStart = target_date's year + fiscal_month + day 1
    --   if target_date < candidateStart → candidateStart - 1 year
    -- =====================================================
    INSERT INTO statistics_yearly_users (tenant_id, stat_year, user_id, user_name, last_used_at, created_at)
    SELECT
        ev.tenant_id,
        CASE
            WHEN p_target_date < MAKE_DATE(EXTRACT(YEAR FROM p_target_date)::int, tt.fiscal_month, 1)
            THEN MAKE_DATE(EXTRACT(YEAR FROM p_target_date)::int - 1, tt.fiscal_month, 1)
            ELSE MAKE_DATE(EXTRACT(YEAR FROM p_target_date)::int, tt.fiscal_month, 1)
        END,
        ev.user_id,
        COALESCE(ev.user_name, ''),
        MAX(ev.created_at),
        MIN(ev.created_at)
    FROM security_event ev
    JOIN tmp_tz_tenants tt ON ev.tenant_id = tt.tenant_id
    WHERE ev.created_at >= v_utc_start
      AND ev.created_at < v_utc_end
      AND ev.user_id IS NOT NULL
      AND ev.type IN ('login_success', 'issue_token_success', 'refresh_token_success', 'inspect_token_success')
    GROUP BY ev.tenant_id, tt.fiscal_month, ev.user_id, ev.user_name
    ON CONFLICT (tenant_id, stat_year, user_id)
    DO UPDATE SET last_used_at = GREATEST(statistics_yearly_users.last_used_at, EXCLUDED.last_used_at);

    GET DIAGNOSTICS v_count = ROW_COUNT;
    step := 'statistics_yearly_users';
    rows_affected := v_count;
    RETURN NEXT;

    -- =====================================================
    -- Step 5: Aggregate DAU/MAU/YAU counts into statistics_events
    --
    -- Only for tenants in this TZ group
    -- =====================================================
    INSERT INTO statistics_events (tenant_id, stat_date, event_type, count)
    SELECT du.tenant_id, p_target_date, 'dau', COUNT(DISTINCT du.user_id)
    FROM statistics_daily_users du
    JOIN tmp_tz_tenants tt ON du.tenant_id = tt.tenant_id
    WHERE du.stat_date = p_target_date
    GROUP BY du.tenant_id
    ON CONFLICT (tenant_id, stat_date, event_type)
    DO UPDATE SET count = EXCLUDED.count,
                  updated_at = CURRENT_TIMESTAMP;

    INSERT INTO statistics_events (tenant_id, stat_date, event_type, count)
    SELECT mu.tenant_id, p_target_date, 'mau', COUNT(DISTINCT mu.user_id)
    FROM statistics_monthly_users mu
    JOIN tmp_tz_tenants tt ON mu.tenant_id = tt.tenant_id
    WHERE mu.stat_month = DATE_TRUNC('month', p_target_date)::date
    GROUP BY mu.tenant_id
    ON CONFLICT (tenant_id, stat_date, event_type)
    DO UPDATE SET count = EXCLUDED.count,
                  updated_at = CURRENT_TIMESTAMP;

    INSERT INTO statistics_events (tenant_id, stat_date, event_type, count)
    SELECT yu.tenant_id, p_target_date, 'yau', COUNT(DISTINCT yu.user_id)
    FROM statistics_yearly_users yu
    JOIN tmp_tz_tenants tt ON yu.tenant_id = tt.tenant_id
    WHERE yu.stat_year = CASE
            WHEN p_target_date < MAKE_DATE(EXTRACT(YEAR FROM p_target_date)::int, tt.fiscal_month, 1)
            THEN MAKE_DATE(EXTRACT(YEAR FROM p_target_date)::int - 1, tt.fiscal_month, 1)
            ELSE MAKE_DATE(EXTRACT(YEAR FROM p_target_date)::int, tt.fiscal_month, 1)
        END
    GROUP BY yu.tenant_id
    ON CONFLICT (tenant_id, stat_date, event_type)
    DO UPDATE SET count = EXCLUDED.count,
                  updated_at = CURRENT_TIMESTAMP;

    GET DIAGNOSTICS v_count = ROW_COUNT;
    step := 'statistics_events_dau_mau_yau';
    rows_affected := v_count;
    RETURN NEXT;

    -- Cleanup
    DROP TABLE tmp_tz_tenants;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION aggregate_statistics_by_timezone() IS
    'Hourly entry point: aggregates statistics for TZ groups whose local time is 00:xx. '
    'Each TZ group is processed exactly once per day, right after its day ends. '
    'Idempotent: safe to re-run.';

COMMENT ON FUNCTION aggregate_statistics_for_timezone(TEXT, DATE) IS
    'Aggregates statistics for a specific timezone group and target date. '
    'Scans exactly 24h of security_event data. '
    'Can be called directly for manual execution or re-processing.';
