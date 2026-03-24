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
-- Daily statistics aggregation function (Optimized)
--
-- Performance optimization: pre-computes UTC time ranges
-- per timezone group instead of per-row AT TIME ZONE conversion.
--
-- Before (V0_9_33):
--   20M rows × AT TIME ZONE per row → ~200s
--
-- After (V0_9_34):
--   N timezone groups × UTC range calc (instant)
--   20M rows × simple timestamp comparison (index-friendly) → much faster
--
-- Replaces aggregate_daily_statistics from V0_9_33.
-- =====================================================

CREATE OR REPLACE FUNCTION aggregate_daily_statistics(p_target_date DATE DEFAULT CURRENT_DATE - INTERVAL '1 day')
RETURNS TABLE (
    step TEXT,
    rows_affected BIGINT
) AS $$
DECLARE
    v_count BIGINT;
    v_prune_start TIMESTAMP;
    v_prune_end TIMESTAMP;
BEGIN
    -- Constant window for partition pruning (UTC-12 to UTC+14 coverage)
    -- These are constants known at plan time, enabling PostgreSQL to skip irrelevant partitions.
    v_prune_start := p_target_date::timestamp - INTERVAL '14 hours';
    v_prune_end   := p_target_date::timestamp + INTERVAL '36 hours';
    -- =====================================================
    -- Prepare: Build timezone-to-UTC-range mapping
    --
    -- Instead of converting every event row with AT TIME ZONE,
    -- we pre-compute the UTC window for each distinct timezone.
    --
    -- Example for p_target_date = 2026-03-24:
    --   Asia/Tokyo → [2026-03-23 15:00 UTC, 2026-03-24 15:00 UTC)
    --   UTC        → [2026-03-24 00:00 UTC, 2026-03-25 00:00 UTC)
    --   US/Pacific → [2026-03-24 07:00 UTC, 2026-03-25 07:00 UTC)
    -- =====================================================
    DROP TABLE IF EXISTS tmp_tz_ranges;
    DROP TABLE IF EXISTS tmp_tenant_tz;
    CREATE TEMP TABLE tmp_tz_ranges AS
    SELECT
        tz,
        (p_target_date::timestamp AT TIME ZONE tz AT TIME ZONE 'UTC') AS utc_start,
        ((p_target_date + 1)::timestamp AT TIME ZONE tz AT TIME ZONE 'UTC') AS utc_end
    FROM (
        SELECT DISTINCT COALESCE(attributes->>'timezone', 'UTC') AS tz
        FROM tenant
    ) tzs;

    CREATE TEMP TABLE tmp_tenant_tz AS
    SELECT
        t.id AS tenant_id,
        COALESCE(t.attributes->>'timezone', 'UTC') AS tz,
        COALESCE((t.attributes->>'fiscal_year_start_month')::int, 1) AS fiscal_month
    FROM tenant t;

    CREATE INDEX ON tmp_tenant_tz (tenant_id);

    -- =====================================================
    -- Step 1: Aggregate event counts into statistics_events
    --
    -- All event types are counted (no filtering).
    -- The app-layer (SecurityEventHandler) excluded inspect_token_success
    -- to reduce real-time UPSERT lock contention, but in batch mode
    -- there is no cost difference since we COUNT(*) the entire day at once.
    -- =====================================================
    INSERT INTO statistics_events (tenant_id, stat_date, event_type, count)
    SELECT
        ev.tenant_id,
        p_target_date,
        ev.type,
        COUNT(*)
    FROM security_event ev
    JOIN tmp_tenant_tz tt ON ev.tenant_id = tt.tenant_id
    JOIN tmp_tz_ranges tr ON tt.tz = tr.tz
    WHERE ev.created_at >= v_prune_start AND ev.created_at < v_prune_end
      AND ev.created_at >= tr.utc_start
      AND ev.created_at < tr.utc_end
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
    JOIN tmp_tenant_tz tt ON ev.tenant_id = tt.tenant_id
    JOIN tmp_tz_ranges tr ON tt.tz = tr.tz
    WHERE ev.created_at >= v_prune_start AND ev.created_at < v_prune_end
      AND ev.created_at >= tr.utc_start
      AND ev.created_at < tr.utc_end
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
    JOIN tmp_tenant_tz tt ON ev.tenant_id = tt.tenant_id
    JOIN tmp_tz_ranges tr ON tt.tz = tr.tz
    WHERE ev.created_at >= v_prune_start AND ev.created_at < v_prune_end
      AND ev.created_at >= tr.utc_start
      AND ev.created_at < tr.utc_end
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
    JOIN tmp_tenant_tz tt ON ev.tenant_id = tt.tenant_id
    JOIN tmp_tz_ranges tr ON tt.tz = tr.tz
    WHERE ev.created_at >= v_prune_start AND ev.created_at < v_prune_end
      AND ev.created_at >= tr.utc_start
      AND ev.created_at < tr.utc_end
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
    -- =====================================================
    INSERT INTO statistics_events (tenant_id, stat_date, event_type, count)
    SELECT tenant_id, p_target_date, 'dau', COUNT(DISTINCT user_id)
    FROM statistics_daily_users
    WHERE stat_date = p_target_date
    GROUP BY tenant_id
    ON CONFLICT (tenant_id, stat_date, event_type)
    DO UPDATE SET count = EXCLUDED.count,
                  updated_at = CURRENT_TIMESTAMP;

    INSERT INTO statistics_events (tenant_id, stat_date, event_type, count)
    SELECT tenant_id, p_target_date, 'mau', COUNT(DISTINCT user_id)
    FROM statistics_monthly_users
    WHERE stat_month = DATE_TRUNC('month', p_target_date)::date
    GROUP BY tenant_id
    ON CONFLICT (tenant_id, stat_date, event_type)
    DO UPDATE SET count = EXCLUDED.count,
                  updated_at = CURRENT_TIMESTAMP;

    INSERT INTO statistics_events (tenant_id, stat_date, event_type, count)
    SELECT yu.tenant_id, p_target_date, 'yau', COUNT(DISTINCT yu.user_id)
    FROM statistics_yearly_users yu
    JOIN tmp_tenant_tz tt ON yu.tenant_id = tt.tenant_id
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

    -- Cleanup temp tables
    DROP TABLE tmp_tz_ranges;
    DROP TABLE tmp_tenant_tz;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION aggregate_daily_statistics(DATE) IS
    'Aggregates security_event data for a given date into statistics tables. '
    'Optimized: pre-computes UTC ranges per timezone group instead of per-row conversion. '
    'Supports tenant-specific fiscal year (fiscal_year_start_month). '
    'Idempotent: safe to re-run. Default: previous day.';
