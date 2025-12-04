-- V0_10_0__statistics.sql
-- Issue #441: Tenant statistics data collection (DAU/MAU/YAU)

-- =====================================================
-- statistics_monthly
-- Monthly statistics with daily breakdown in JSONB
-- =====================================================

CREATE TABLE statistics_monthly (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    stat_month CHAR(7) NOT NULL,  -- YYYY-MM format (2025-01, 2025-02, ...)

    -- Monthly summary (for quick access)
    monthly_summary JSONB NOT NULL DEFAULT '{}'::JSONB,
    /* {
      "mau": 5000,
      "total_logins": 45000,
      "total_login_failures": 500,
      "new_users": 200
    } */

    -- Daily breakdown (for charts/graphs)
    daily_metrics JSONB NOT NULL DEFAULT '{}'::JSONB,
    /* {
      "2025-01-01": {"dau": 100, "mau": 100, "logins": 1500, "failures": 20},
      "2025-01-02": {"dau": 110, "mau": 180, "logins": 1600, "failures": 15},
      ...
      "2025-01-31": {"dau": 95, "mau": 5000, "logins": 1400, "failures": 18}
    } */

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE(tenant_id, stat_month)
);

-- Index for time-series queries
CREATE INDEX idx_statistics_monthly_tenant_month ON statistics_monthly (tenant_id, stat_month DESC);

-- Row Level Security (RLS)
ALTER TABLE statistics_monthly ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON statistics_monthly
    USING (tenant_id = current_setting('app.tenant_id')::uuid);

ALTER TABLE statistics_monthly FORCE ROW LEVEL SECURITY;

-- Comments
COMMENT ON TABLE statistics_monthly IS 'Monthly tenant statistics with daily breakdown in JSONB';
COMMENT ON COLUMN statistics_monthly.stat_month IS 'Year and month in YYYY-MM format (e.g., 2025-01)';
COMMENT ON COLUMN statistics_monthly.monthly_summary IS 'Monthly aggregated metrics (MAU, total logins, etc.)';
COMMENT ON COLUMN statistics_monthly.daily_metrics IS 'Daily breakdown keyed by date (YYYY-MM-DD)';

-- =====================================================
-- statistics_yearly
-- Yearly statistics summary
-- Note: Monthly breakdown is available via statistics_monthly table
-- =====================================================

CREATE TABLE statistics_yearly (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    stat_year CHAR(4) NOT NULL,  -- YYYY format (2025, 2026, ...)

    -- Yearly summary (for quick access)
    yearly_summary JSONB NOT NULL DEFAULT '{}'::JSONB,
    /* {
      "yau": 15000,
      "total_logins": 500000,
      "total_login_failures": 5000,
      "new_users": 2000
    } */

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE(tenant_id, stat_year)
);

-- Index for time-series queries
CREATE INDEX idx_statistics_yearly_tenant_year ON statistics_yearly (tenant_id, stat_year DESC);

-- Row Level Security (RLS)
ALTER TABLE statistics_yearly ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON statistics_yearly
    USING (tenant_id = current_setting('app.tenant_id')::uuid);

ALTER TABLE statistics_yearly FORCE ROW LEVEL SECURITY;

-- Comments
COMMENT ON TABLE statistics_yearly IS 'Yearly tenant statistics summary (monthly breakdown via statistics_monthly table)';
COMMENT ON COLUMN statistics_yearly.stat_year IS 'Year in YYYY format (e.g., 2025)';
COMMENT ON COLUMN statistics_yearly.yearly_summary IS 'Yearly aggregated metrics (YAU, total logins, etc.)';

-- =====================================================
-- statistics_daily_users
-- Track unique daily active users
-- =====================================================

CREATE TABLE statistics_daily_users (
    tenant_id UUID NOT NULL,
    stat_date DATE NOT NULL,
    user_id UUID NOT NULL,
    last_used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (tenant_id, stat_date, user_id)
);

-- Index for DAU count queries
CREATE INDEX idx_statistics_daily_users_tenant_date ON statistics_daily_users (tenant_id, stat_date);

-- Row Level Security (RLS)
ALTER TABLE statistics_daily_users ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON statistics_daily_users
    USING (tenant_id = current_setting('app.tenant_id')::uuid);

ALTER TABLE statistics_daily_users FORCE ROW LEVEL SECURITY;

-- Comments
COMMENT ON TABLE statistics_daily_users IS 'Daily active users tracking (one row per unique user per day)';
COMMENT ON COLUMN statistics_daily_users.stat_date IS 'Date of user activity';
COMMENT ON COLUMN statistics_daily_users.user_id IS 'Active user identifier';
COMMENT ON COLUMN statistics_daily_users.last_used_at IS 'Last activity timestamp for this user on this day';

-- =====================================================
-- statistics_monthly_users
-- Track unique monthly active users
-- =====================================================

CREATE TABLE statistics_monthly_users (
    tenant_id UUID NOT NULL,
    stat_month CHAR(7) NOT NULL,  -- YYYY-MM format
    user_id UUID NOT NULL,
    last_used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (tenant_id, stat_month, user_id)
);

-- Index for MAU count queries
CREATE INDEX idx_statistics_monthly_users_tenant_month ON statistics_monthly_users (tenant_id, stat_month);

-- Row Level Security (RLS)
ALTER TABLE statistics_monthly_users ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON statistics_monthly_users
    USING (tenant_id = current_setting('app.tenant_id')::uuid);

ALTER TABLE statistics_monthly_users FORCE ROW LEVEL SECURITY;

-- Comments
COMMENT ON TABLE statistics_monthly_users IS 'Monthly active users tracking (one row per unique user per calendar month)';
COMMENT ON COLUMN statistics_monthly_users.stat_month IS 'Year and month in YYYY-MM format (e.g., 2025-01)';
COMMENT ON COLUMN statistics_monthly_users.user_id IS 'Active user identifier';
COMMENT ON COLUMN statistics_monthly_users.last_used_at IS 'Last activity timestamp for this user in this month';

-- =====================================================
-- statistics_yearly_users
-- Track unique yearly active users
-- =====================================================

CREATE TABLE statistics_yearly_users (
    tenant_id UUID NOT NULL,
    stat_year CHAR(4) NOT NULL,  -- YYYY format
    user_id UUID NOT NULL,
    last_used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (tenant_id, stat_year, user_id)
);

-- Index for YAU count queries
CREATE INDEX idx_statistics_yearly_users_tenant_year ON statistics_yearly_users (tenant_id, stat_year);

-- Index for last_used_at queries (e.g., finding inactive users)
CREATE INDEX idx_statistics_yearly_users_last_used ON statistics_yearly_users (tenant_id, last_used_at);

-- Row Level Security (RLS)
ALTER TABLE statistics_yearly_users ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON statistics_yearly_users
    USING (tenant_id = current_setting('app.tenant_id')::uuid);

ALTER TABLE statistics_yearly_users FORCE ROW LEVEL SECURITY;

-- Comments
COMMENT ON TABLE statistics_yearly_users IS 'Yearly active users tracking (one row per unique user per calendar year)';
COMMENT ON COLUMN statistics_yearly_users.stat_year IS 'Year in YYYY format (e.g., 2025)';
COMMENT ON COLUMN statistics_yearly_users.user_id IS 'Active user identifier';
COMMENT ON COLUMN statistics_yearly_users.last_used_at IS 'Last activity timestamp for this user in this year';

-- =====================================================
-- Helper functions
-- =====================================================

-- Get DAU count for a specific tenant and date
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

-- Get MAU count for a specific tenant and month
CREATE OR REPLACE FUNCTION get_mau_count(p_tenant_id UUID, p_stat_month CHAR(7))
RETURNS INTEGER AS $$
BEGIN
    RETURN (
        SELECT COUNT(*)
        FROM statistics_monthly_users
        WHERE tenant_id = p_tenant_id AND stat_month = p_stat_month
    );
END;
$$ LANGUAGE plpgsql;

-- Get YAU count for a specific tenant and year
CREATE OR REPLACE FUNCTION get_yau_count(p_tenant_id UUID, p_stat_year CHAR(4))
RETURNS INTEGER AS $$
BEGIN
    RETURN (
        SELECT COUNT(*)
        FROM statistics_yearly_users
        WHERE tenant_id = p_tenant_id AND stat_year = p_stat_year
    );
END;
$$ LANGUAGE plpgsql;

-- =====================================================
-- Data retention functions
-- =====================================================

CREATE OR REPLACE FUNCTION cleanup_old_statistics(retention_months INTEGER)
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
    cutoff_month CHAR(7);
BEGIN
    cutoff_month := TO_CHAR(CURRENT_DATE - (retention_months || ' months')::INTERVAL, 'YYYY-MM');

    DELETE FROM statistics_monthly
    WHERE stat_month < cutoff_month;

    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION cleanup_old_daily_users(retention_days INTEGER)
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM statistics_daily_users
    WHERE stat_date < CURRENT_DATE - (retention_days || ' days')::INTERVAL;

    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION cleanup_old_monthly_users(retention_months INTEGER)
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
    cutoff_month CHAR(7);
BEGIN
    cutoff_month := TO_CHAR(CURRENT_DATE - (retention_months || ' months')::INTERVAL, 'YYYY-MM');

    DELETE FROM statistics_monthly_users
    WHERE stat_month < cutoff_month;

    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION cleanup_old_yearly_users(retention_years INTEGER)
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
    cutoff_year CHAR(4);
BEGIN
    cutoff_year := TO_CHAR(CURRENT_DATE - (retention_years || ' years')::INTERVAL, 'YYYY');

    DELETE FROM statistics_yearly_users
    WHERE stat_year < cutoff_year;

    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION cleanup_old_yearly_statistics(retention_years INTEGER)
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
    cutoff_year CHAR(4);
BEGIN
    cutoff_year := TO_CHAR(CURRENT_DATE - (retention_years || ' years')::INTERVAL, 'YYYY');

    DELETE FROM statistics_yearly
    WHERE stat_year < cutoff_year;

    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION cleanup_old_statistics(INTEGER) IS 'Delete monthly statistics older than specified months';
COMMENT ON FUNCTION cleanup_old_daily_users(INTEGER) IS 'Delete daily user data older than specified days';
COMMENT ON FUNCTION cleanup_old_monthly_users(INTEGER) IS 'Delete monthly user data older than specified months';
COMMENT ON FUNCTION cleanup_old_yearly_users(INTEGER) IS 'Delete yearly user data older than specified years';
COMMENT ON FUNCTION cleanup_old_yearly_statistics(INTEGER) IS 'Delete yearly statistics older than specified years';
