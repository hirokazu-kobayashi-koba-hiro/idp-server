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
-- Issue #441: Tenant statistics data collection (DAU/MAU/YAU)
--
-- Partitioned tables (managed by pg_partman):
--   - statistics_daily_users (daily, 90-day retention)
--   - statistics_monthly_users (monthly, 13-month retention)
--   - statistics_yearly_users (yearly, 5-year retention)
--
-- Non-partitioned tables (summary):
--   - statistics_monthly
--   - statistics_yearly
-- =====================================================

-- =====================================================
-- statistics_daily_users (PARTITIONED - daily)
-- Track unique daily active users
-- =====================================================

CREATE TABLE statistics_daily_users (
    tenant_id UUID NOT NULL,
    stat_date DATE NOT NULL,
    user_id UUID NOT NULL,
    user_name VARCHAR(255) NOT NULL DEFAULT '',
    last_used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (tenant_id, stat_date, user_id)
)
PARTITION BY RANGE (stat_date);

-- Note: pg_partman.create_parent() will create the default partition

CREATE INDEX idx_statistics_daily_users_tenant_date ON statistics_daily_users (tenant_id, stat_date);

ALTER TABLE statistics_daily_users ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON statistics_daily_users
    USING (tenant_id = current_setting('app.tenant_id')::uuid);

ALTER TABLE statistics_daily_users FORCE ROW LEVEL SECURITY;

COMMENT ON TABLE statistics_daily_users IS 'Daily active users tracking with daily partitioning. Retained for 90 days. Managed by pg_partman.';
COMMENT ON COLUMN statistics_daily_users.stat_date IS 'Date of user activity (partition key)';

-- Configure pg_partman for statistics_daily_users
SELECT partman.create_parent(
    p_parent_table => 'public.statistics_daily_users',
    p_control => 'stat_date',
    p_type => 'range',
    p_interval => '1 day',
    p_premake => 90,
    p_start_partition => CURRENT_DATE::text
);

UPDATE partman.part_config
SET infinite_time_partitions = true,
    retention = '90 days',
    retention_keep_table = false,
    retention_keep_index = false
WHERE parent_table = 'public.statistics_daily_users';

-- =====================================================
-- statistics_monthly_users (PARTITIONED - monthly)
-- Track unique monthly active users
-- Note: stat_month is DATE type (first day of month) for pg_partman compatibility
-- =====================================================

CREATE TABLE statistics_monthly_users (
    tenant_id UUID NOT NULL,
    stat_month DATE NOT NULL,  -- First day of month (e.g., 2025-01-01)
    user_id UUID NOT NULL,
    user_name VARCHAR(255) NOT NULL DEFAULT '',
    last_used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (tenant_id, stat_month, user_id)
)
PARTITION BY RANGE (stat_month);

-- Note: pg_partman.create_parent() will create the default partition

CREATE INDEX idx_statistics_monthly_users_tenant_month ON statistics_monthly_users (tenant_id, stat_month);

ALTER TABLE statistics_monthly_users ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON statistics_monthly_users
    USING (tenant_id = current_setting('app.tenant_id')::uuid);

ALTER TABLE statistics_monthly_users FORCE ROW LEVEL SECURITY;

COMMENT ON TABLE statistics_monthly_users IS 'Monthly active users tracking with monthly partitioning. Retained for 13 months. Managed by pg_partman.';
COMMENT ON COLUMN statistics_monthly_users.stat_month IS 'First day of month (e.g., 2025-01-01) for partition key';

-- Configure pg_partman for statistics_monthly_users
SELECT partman.create_parent(
    p_parent_table => 'public.statistics_monthly_users',
    p_control => 'stat_month',
    p_type => 'range',
    p_interval => '1 month',
    p_premake => 13,
    p_start_partition => DATE_TRUNC('month', CURRENT_DATE)::text
);

UPDATE partman.part_config
SET infinite_time_partitions = true,
    retention = '13 months',
    retention_keep_table = false,
    retention_keep_index = false
WHERE parent_table = 'public.statistics_monthly_users';

-- =====================================================
-- statistics_yearly_users (PARTITIONED - yearly)
-- Track unique yearly active users
-- Note: stat_year is DATE type (first day of year) for pg_partman compatibility
-- =====================================================

CREATE TABLE statistics_yearly_users (
    tenant_id UUID NOT NULL,
    stat_year DATE NOT NULL,  -- First day of year (e.g., 2025-01-01)
    user_id UUID NOT NULL,
    user_name VARCHAR(255) NOT NULL DEFAULT '',
    last_used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (tenant_id, stat_year, user_id)
)
PARTITION BY RANGE (stat_year);

-- Note: pg_partman.create_parent() will create the default partition

CREATE INDEX idx_statistics_yearly_users_tenant_year ON statistics_yearly_users (tenant_id, stat_year);
CREATE INDEX idx_statistics_yearly_users_last_used ON statistics_yearly_users (tenant_id, last_used_at);

ALTER TABLE statistics_yearly_users ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON statistics_yearly_users
    USING (tenant_id = current_setting('app.tenant_id')::uuid);

ALTER TABLE statistics_yearly_users FORCE ROW LEVEL SECURITY;

COMMENT ON TABLE statistics_yearly_users IS 'Yearly active users tracking with yearly partitioning. Retained for 5 years. Managed by pg_partman.';
COMMENT ON COLUMN statistics_yearly_users.stat_year IS 'First day of year (e.g., 2025-01-01) for partition key';

-- Configure pg_partman for statistics_yearly_users
SELECT partman.create_parent(
    p_parent_table => 'public.statistics_yearly_users',
    p_control => 'stat_year',
    p_type => 'range',
    p_interval => '1 year',
    p_premake => 5,
    p_start_partition => DATE_TRUNC('year', CURRENT_DATE)::text
);

UPDATE partman.part_config
SET infinite_time_partitions = true,
    retention = '5 years',
    retention_keep_table = false,
    retention_keep_index = false
WHERE parent_table = 'public.statistics_yearly_users';

-- =====================================================
-- Initial maintenance (run after migration)
-- =====================================================
-- Note: partman.run_maintenance_proc() contains COMMIT and cannot run inside Flyway transaction.
-- Run the following commands manually after migration or via pg_cron:
--
--   CALL partman.run_maintenance_proc();
--
--   SELECT partman.partition_data_time(p_parent_table := 'public.statistics_daily_users', p_batch_count := 10000);
--   SELECT partman.partition_data_time(p_parent_table := 'public.statistics_monthly_users', p_batch_count := 10000);
--   SELECT partman.partition_data_time(p_parent_table := 'public.statistics_yearly_users', p_batch_count := 10000);
--
-- The pg_cron job scheduled in V0_9_21_1 will handle this automatically at 02:00 UTC.

-- =====================================================
-- statistics_monthly (NON-PARTITIONED - summary)
-- Monthly statistics with daily breakdown in JSONB
-- =====================================================

CREATE TABLE statistics_monthly (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    stat_month DATE NOT NULL,  -- First day of month (e.g., 2025-01-01)

    monthly_summary JSONB NOT NULL DEFAULT '{}'::JSONB,
    daily_metrics JSONB NOT NULL DEFAULT '{}'::JSONB,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE(tenant_id, stat_month)
);

CREATE INDEX idx_statistics_monthly_tenant_month ON statistics_monthly (tenant_id, stat_month DESC);

ALTER TABLE statistics_monthly ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON statistics_monthly
    USING (tenant_id = current_setting('app.tenant_id')::uuid);

ALTER TABLE statistics_monthly FORCE ROW LEVEL SECURITY;

COMMENT ON TABLE statistics_monthly IS 'Monthly tenant statistics with daily breakdown in JSONB';
COMMENT ON COLUMN statistics_monthly.stat_month IS 'First day of month (e.g., 2025-01-01)';
COMMENT ON COLUMN statistics_monthly.monthly_summary IS 'Monthly aggregated metrics (MAU, total logins, etc.)';
COMMENT ON COLUMN statistics_monthly.daily_metrics IS 'Daily breakdown keyed by date (YYYY-MM-DD)';

-- =====================================================
-- statistics_yearly (NON-PARTITIONED - summary)
-- Yearly statistics summary
-- =====================================================

CREATE TABLE statistics_yearly (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    stat_year DATE NOT NULL,  -- First day of year (e.g., 2025-01-01)

    yearly_summary JSONB NOT NULL DEFAULT '{}'::JSONB,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE(tenant_id, stat_year)
);

CREATE INDEX idx_statistics_yearly_tenant_year ON statistics_yearly (tenant_id, stat_year DESC);

ALTER TABLE statistics_yearly ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON statistics_yearly
    USING (tenant_id = current_setting('app.tenant_id')::uuid);

ALTER TABLE statistics_yearly FORCE ROW LEVEL SECURITY;

COMMENT ON TABLE statistics_yearly IS 'Yearly tenant statistics summary';
COMMENT ON COLUMN statistics_yearly.stat_year IS 'First day of year (e.g., 2025-01-01)';
COMMENT ON COLUMN statistics_yearly.yearly_summary IS 'Yearly aggregated metrics (YAU, total logins, etc.)';

-- =====================================================
-- Helper functions
-- =====================================================

CREATE OR REPLACE FUNCTION get_dau_count(p_tenant_id UUID, p_stat_date DATE)
RETURNS INTEGER AS $$
BEGIN
    RETURN (
        SELECT COUNT(*)
        FROM statistics_daily_users
        WHERE tenant_id = p_tenant_id AND stat_date = p_stat_date
    );
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION get_mau_count(p_tenant_id UUID, p_stat_month DATE)
RETURNS INTEGER AS $$
BEGIN
    RETURN (
        SELECT COUNT(*)
        FROM statistics_monthly_users
        WHERE tenant_id = p_tenant_id AND stat_month = p_stat_month
    );
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION get_yau_count(p_tenant_id UUID, p_stat_year DATE)
RETURNS INTEGER AS $$
BEGIN
    RETURN (
        SELECT COUNT(*)
        FROM statistics_yearly_users
        WHERE tenant_id = p_tenant_id AND stat_year = p_stat_year
    );
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION get_dau_count(UUID, DATE) IS 'Get Daily Active User count for a tenant and date';
COMMENT ON FUNCTION get_mau_count(UUID, DATE) IS 'Get Monthly Active User count for a tenant and month (pass first day of month)';
COMMENT ON FUNCTION get_yau_count(UUID, DATE) IS 'Get Yearly Active User count for a tenant and year (pass first day of year)';

-- =====================================================
-- Data retention functions (for non-partitioned tables)
-- Note: Partitioned tables are managed by pg_partman
-- =====================================================

CREATE OR REPLACE FUNCTION cleanup_old_statistics_monthly(retention_months INTEGER)
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
    cutoff_date DATE;
BEGIN
    cutoff_date := DATE_TRUNC('month', CURRENT_DATE - (retention_months || ' months')::INTERVAL);

    DELETE FROM statistics_monthly
    WHERE stat_month < cutoff_date;

    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION cleanup_old_statistics_yearly(retention_years INTEGER)
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
    cutoff_date DATE;
BEGIN
    cutoff_date := DATE_TRUNC('year', CURRENT_DATE - (retention_years || ' years')::INTERVAL);

    DELETE FROM statistics_yearly
    WHERE stat_year < cutoff_date;

    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION cleanup_old_statistics_monthly(INTEGER) IS 'Delete monthly statistics summary older than specified months';
COMMENT ON FUNCTION cleanup_old_statistics_yearly(INTEGER) IS 'Delete yearly statistics summary older than specified years';
