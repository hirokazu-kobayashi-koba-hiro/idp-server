-- V0_9_33__daily_statistics_aggregation.mysql.sql
--
-- Daily statistics aggregation stored procedure + Event Scheduler job.
-- (Tenant Timezone-aware)
--
-- Aggregates security_event data into statistics tables,
-- converting UTC timestamps to each tenant's local timezone
-- before determining the statistics date.
--
-- This replaces real-time upsert from the application layer,
-- eliminating UPDATE lock contention on statistics tables.
--
-- Idempotent: uses absolute counts so re-running produces the same result.
--
-- Note: MySQL CONVERT_TZ requires timezone tables to be loaded.
--   mysql_tzinfo_to_sql /usr/share/zoneinfo | mysql -u root mysql

DELIMITER //

CREATE PROCEDURE aggregate_daily_statistics(IN p_target_date DATE)
BEGIN
    DECLARE v_window_start DATETIME(6);
    DECLARE v_window_end DATETIME(6);

    -- Default to previous day if NULL
    IF p_target_date IS NULL THEN
        SET p_target_date = DATE_SUB(CURDATE(), INTERVAL 1 DAY);
    END IF;

    -- UTC time window: [target_date - 14h, target_date + 36h)
    -- Covers all possible timezones (UTC-12 to UTC+14)
    SET v_window_start = DATE_SUB(p_target_date, INTERVAL 14 HOUR);
    SET v_window_end   = DATE_ADD(p_target_date, INTERVAL 36 HOUR);

    -- Step 1: Aggregate event counts into statistics_events
    -- All event types are counted (no filtering).
    -- The app-layer (SecurityEventHandler) excluded inspect_token_success
    -- to reduce real-time UPSERT lock contention, but in batch mode
    -- there is no cost difference since we COUNT(*) the entire day at once.
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
      AND DATE(CONVERT_TZ(ev.created_at, 'UTC', COALESCE(JSON_UNQUOTE(JSON_EXTRACT(t.attributes, '$.timezone')), 'UTC'))) = p_target_date
    GROUP BY ev.tenant_id, ev.type
    ON DUPLICATE KEY UPDATE
        count = VALUES(count),
        updated_at = CURRENT_TIMESTAMP(6);

    -- Step 2: Populate daily active users (DAU)
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
      AND DATE(CONVERT_TZ(ev.created_at, 'UTC', COALESCE(JSON_UNQUOTE(JSON_EXTRACT(t.attributes, '$.timezone')), 'UTC'))) = p_target_date
      AND ev.user_id IS NOT NULL
      AND ev.type IN ('login_success', 'issue_token_success', 'refresh_token_success', 'inspect_token_success')
    GROUP BY ev.tenant_id, ev.user_id, ev.user_name
    ON DUPLICATE KEY UPDATE
        last_used_at = GREATEST(statistics_daily_users.last_used_at, VALUES(last_used_at));

    -- Step 3: Populate monthly active users (MAU)
    INSERT INTO statistics_monthly_users (tenant_id, stat_month, user_id, user_name, last_used_at, created_at)
    SELECT
        ev.tenant_id,
        DATE_FORMAT(p_target_date, '%Y-%m-01'),
        ev.user_id,
        COALESCE(ev.user_name, ''),
        MAX(ev.created_at),
        MIN(ev.created_at)
    FROM security_event ev
    JOIN tenant t ON ev.tenant_id = t.id
    WHERE ev.created_at >= v_window_start
      AND ev.created_at < v_window_end
      AND DATE(CONVERT_TZ(ev.created_at, 'UTC', COALESCE(JSON_UNQUOTE(JSON_EXTRACT(t.attributes, '$.timezone')), 'UTC'))) = p_target_date
      AND ev.user_id IS NOT NULL
      AND ev.type IN ('login_success', 'issue_token_success', 'refresh_token_success', 'inspect_token_success')
    GROUP BY ev.tenant_id, ev.user_id, ev.user_name
    ON DUPLICATE KEY UPDATE
        last_used_at = GREATEST(statistics_monthly_users.last_used_at, VALUES(last_used_at));

    -- Step 4: Populate yearly active users (YAU)
    -- Uses fiscal year start month from tenant.attributes.fiscal_year_start_month (default: 1)
    -- Fiscal year logic (same as FiscalYearCalculator.java):
    --   candidateStart = target_date's year + startMonth + day 1
    --   if target_date < candidateStart → candidateStart - 1 year
    INSERT INTO statistics_yearly_users (tenant_id, stat_year, user_id, user_name, last_used_at, created_at)
    SELECT
        ev.tenant_id,
        CASE
            WHEN p_target_date < STR_TO_DATE(
                    CONCAT(YEAR(p_target_date), '-',
                           LPAD(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(t.attributes, '$.fiscal_year_start_month')), '1'), 2, '0'),
                           '-01'),
                    '%Y-%m-%d')
            THEN STR_TO_DATE(
                    CONCAT(YEAR(p_target_date) - 1, '-',
                           LPAD(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(t.attributes, '$.fiscal_year_start_month')), '1'), 2, '0'),
                           '-01'),
                    '%Y-%m-%d')
            ELSE STR_TO_DATE(
                    CONCAT(YEAR(p_target_date), '-',
                           LPAD(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(t.attributes, '$.fiscal_year_start_month')), '1'), 2, '0'),
                           '-01'),
                    '%Y-%m-%d')
        END,
        ev.user_id,
        COALESCE(ev.user_name, ''),
        MAX(ev.created_at),
        MIN(ev.created_at)
    FROM security_event ev
    JOIN tenant t ON ev.tenant_id = t.id
    WHERE ev.created_at >= v_window_start
      AND ev.created_at < v_window_end
      AND DATE(CONVERT_TZ(ev.created_at, 'UTC', COALESCE(JSON_UNQUOTE(JSON_EXTRACT(t.attributes, '$.timezone')), 'UTC'))) = p_target_date
      AND ev.user_id IS NOT NULL
      AND ev.type IN ('login_success', 'issue_token_success', 'refresh_token_success', 'inspect_token_success')
    GROUP BY ev.tenant_id, JSON_UNQUOTE(JSON_EXTRACT(t.attributes, '$.fiscal_year_start_month')), ev.user_id, ev.user_name
    ON DUPLICATE KEY UPDATE
        last_used_at = GREATEST(statistics_yearly_users.last_used_at, VALUES(last_used_at));

    -- Step 5: Aggregate DAU/MAU/YAU counts into statistics_events
    INSERT INTO statistics_events (tenant_id, stat_date, event_type, count)
    SELECT tenant_id, p_target_date, 'dau', COUNT(DISTINCT user_id)
    FROM statistics_daily_users
    WHERE stat_date = p_target_date
    GROUP BY tenant_id
    ON DUPLICATE KEY UPDATE
        count = VALUES(count),
        updated_at = CURRENT_TIMESTAMP(6);

    INSERT INTO statistics_events (tenant_id, stat_date, event_type, count)
    SELECT tenant_id, p_target_date, 'mau', COUNT(DISTINCT user_id)
    FROM statistics_monthly_users
    WHERE stat_month = DATE_FORMAT(p_target_date, '%Y-%m-01')
    GROUP BY tenant_id
    ON DUPLICATE KEY UPDATE
        count = VALUES(count),
        updated_at = CURRENT_TIMESTAMP(6);

    INSERT INTO statistics_events (tenant_id, stat_date, event_type, count)
    SELECT yu.tenant_id, p_target_date, 'yau', COUNT(DISTINCT yu.user_id)
    FROM statistics_yearly_users yu
    JOIN tenant t ON yu.tenant_id = t.id
    WHERE yu.stat_year = CASE
            WHEN p_target_date < STR_TO_DATE(
                    CONCAT(YEAR(p_target_date), '-',
                           LPAD(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(t.attributes, '$.fiscal_year_start_month')), '1'), 2, '0'),
                           '-01'),
                    '%Y-%m-%d')
            THEN STR_TO_DATE(
                    CONCAT(YEAR(p_target_date) - 1, '-',
                           LPAD(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(t.attributes, '$.fiscal_year_start_month')), '1'), 2, '0'),
                           '-01'),
                    '%Y-%m-%d')
            ELSE STR_TO_DATE(
                    CONCAT(YEAR(p_target_date), '-',
                           LPAD(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(t.attributes, '$.fiscal_year_start_month')), '1'), 2, '0'),
                           '-01'),
                    '%Y-%m-%d')
        END
    GROUP BY yu.tenant_id
    ON DUPLICATE KEY UPDATE
        count = VALUES(count),
        updated_at = CURRENT_TIMESTAMP(6);
END //

DELIMITER ;

-- Register Event Scheduler job: Daily at 04:00
DROP EVENT IF EXISTS evt_daily_statistics_aggregation;

CREATE EVENT evt_daily_statistics_aggregation
    ON SCHEDULE EVERY 1 DAY
    STARTS (TIMESTAMP(CURDATE(), '04:00:00') + INTERVAL IF(CURTIME() >= '04:00:00', 1, 0) DAY)
    ON COMPLETION PRESERVE
    ENABLE
    COMMENT 'Daily statistics aggregation from security_event (tenant TZ-aware). Idempotent.'
    DO CALL aggregate_daily_statistics(NULL);
