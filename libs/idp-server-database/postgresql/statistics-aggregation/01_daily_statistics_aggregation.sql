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
-- Daily statistics aggregation function (Tenant Timezone-aware)
--
-- Aggregates security_event data into statistics tables,
-- converting UTC timestamps to each tenant's local timezone
-- before determining the statistics date.
--
-- This replaces real-time upsert from the application layer,
-- eliminating UPDATE lock contention on statistics tables.
--
-- Idempotent: uses absolute counts (ON CONFLICT ... SET count = EXCLUDED.count)
-- so re-running produces the same result.
--
-- Schedule: pg_cron daily at 04:00 UTC
--   SELECT * FROM aggregate_daily_statistics();
--
-- Manual re-run for a specific date:
--   SELECT * FROM aggregate_daily_statistics('2026-03-24'::date);
-- =====================================================

CREATE OR REPLACE FUNCTION aggregate_daily_statistics(p_target_date DATE DEFAULT CURRENT_DATE - INTERVAL '1 day')
RETURNS TABLE (
    step TEXT,
    rows_affected BIGINT
) AS $$
DECLARE
    v_count BIGINT;
    v_window_start TIMESTAMP;
    v_window_end TIMESTAMP;
BEGIN
    -- =====================================================
    -- Determine the UTC time window to scan.
    --
    -- Since tenants can be in any timezone (UTC-12 to UTC+14),
    -- a single calendar date in the furthest-ahead timezone
    -- may start as early as target_date - 14 hours in UTC,
    -- and a single calendar date in the furthest-behind timezone
    -- may end as late as target_date + 1 day + 12 hours in UTC.
    --
    -- Example: p_target_date = 2026-03-24
    --   UTC+14 (Line Islands): 2026-03-24 00:00 local = 2026-03-23 10:00 UTC
    --   UTC-12 (Baker Island): 2026-03-24 23:59 local = 2026-03-25 11:59 UTC
    --
    -- So we scan: [target_date - 14h, target_date + 36h)
    -- =====================================================
    v_window_start := p_target_date::timestamp - INTERVAL '14 hours';
    v_window_end   := p_target_date::timestamp + INTERVAL '36 hours';

    -- =====================================================
    -- Step 1: Aggregate event counts into statistics_events
    --
    -- For each event, convert created_at to the tenant's
    -- local timezone, then check if the local date matches
    -- p_target_date.
    -- =====================================================
    INSERT INTO statistics_events (tenant_id, stat_date, event_type, count)
    SELECT
        ev.tenant_id,
        p_target_date,
        ev.type,
        COUNT(*)
    FROM security_event ev
    JOIN tenant t ON ev.tenant_id = t.id
    WHERE ev.created_at >= v_window_start
      AND ev.created_at < v_window_end
      AND (ev.created_at AT TIME ZONE 'UTC' AT TIME ZONE COALESCE(t.attributes->>'timezone', 'UTC'))::date = p_target_date
      AND ev.type != 'inspect_token_success'
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
    JOIN tenant t ON ev.tenant_id = t.id
    WHERE ev.created_at >= v_window_start
      AND ev.created_at < v_window_end
      AND (ev.created_at AT TIME ZONE 'UTC' AT TIME ZONE COALESCE(t.attributes->>'timezone', 'UTC'))::date = p_target_date
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
    JOIN tenant t ON ev.tenant_id = t.id
    WHERE ev.created_at >= v_window_start
      AND ev.created_at < v_window_end
      AND (ev.created_at AT TIME ZONE 'UTC' AT TIME ZONE COALESCE(t.attributes->>'timezone', 'UTC'))::date = p_target_date
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
    -- Uses fiscal year start month from tenant.attributes->>'fiscal_year_start_month'.
    -- Default: 1 (calendar year).
    --
    -- Fiscal year logic (same as FiscalYearCalculator.java):
    --   candidateStart = p_target_date's year + startMonth + day 1
    --   if p_target_date < candidateStart → candidateStart - 1 year
    --
    -- Example (fiscal_year_start_month = 4):
    --   2026-02-15 → candidate=2026-04-01 → Feb < Apr → 2025-04-01
    --   2026-05-10 → candidate=2026-04-01 → May >= Apr → 2026-04-01
    -- =====================================================
    INSERT INTO statistics_yearly_users (tenant_id, stat_year, user_id, user_name, last_used_at, created_at)
    SELECT
        ev.tenant_id,
        -- Calculate fiscal year start date per tenant
        CASE
            WHEN p_target_date < MAKE_DATE(EXTRACT(YEAR FROM p_target_date)::int,
                                           COALESCE((t.attributes->>'fiscal_year_start_month')::int, 1),
                                           1)
            THEN MAKE_DATE(EXTRACT(YEAR FROM p_target_date)::int - 1,
                           COALESCE((t.attributes->>'fiscal_year_start_month')::int, 1),
                           1)
            ELSE MAKE_DATE(EXTRACT(YEAR FROM p_target_date)::int,
                           COALESCE((t.attributes->>'fiscal_year_start_month')::int, 1),
                           1)
        END,
        ev.user_id,
        COALESCE(ev.user_name, ''),
        MAX(ev.created_at),
        MIN(ev.created_at)
    FROM security_event ev
    JOIN tenant t ON ev.tenant_id = t.id
    WHERE ev.created_at >= v_window_start
      AND ev.created_at < v_window_end
      AND (ev.created_at AT TIME ZONE 'UTC' AT TIME ZONE COALESCE(t.attributes->>'timezone', 'UTC'))::date = p_target_date
      AND ev.user_id IS NOT NULL
      AND ev.type IN ('login_success', 'issue_token_success', 'refresh_token_success', 'inspect_token_success')
    GROUP BY ev.tenant_id, t.attributes->>'fiscal_year_start_month', ev.user_id, ev.user_name
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
    JOIN tenant t ON yu.tenant_id = t.id
    WHERE yu.stat_year = CASE
            WHEN p_target_date < MAKE_DATE(EXTRACT(YEAR FROM p_target_date)::int,
                                           COALESCE((t.attributes->>'fiscal_year_start_month')::int, 1),
                                           1)
            THEN MAKE_DATE(EXTRACT(YEAR FROM p_target_date)::int - 1,
                           COALESCE((t.attributes->>'fiscal_year_start_month')::int, 1),
                           1)
            ELSE MAKE_DATE(EXTRACT(YEAR FROM p_target_date)::int,
                           COALESCE((t.attributes->>'fiscal_year_start_month')::int, 1),
                           1)
        END
    GROUP BY yu.tenant_id
    ON CONFLICT (tenant_id, stat_date, event_type)
    DO UPDATE SET count = EXCLUDED.count,
                  updated_at = CURRENT_TIMESTAMP;

    GET DIAGNOSTICS v_count = ROW_COUNT;
    step := 'statistics_events_dau_mau_yau';
    rows_affected := v_count;
    RETURN NEXT;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION aggregate_daily_statistics(DATE) IS
    'Aggregates security_event data for a given date into statistics tables. '
    'Converts UTC timestamps to each tenant''s local timezone (from tenant.attributes->>timezone). '
    'Idempotent: safe to re-run. Default: previous day.';
