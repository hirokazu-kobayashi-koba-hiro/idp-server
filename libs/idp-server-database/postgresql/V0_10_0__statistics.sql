-- V0_10_0__statistics.sql
-- Issue #441: Tenant statistics data collection
-- Simple version with hardcoded metrics

-- =====================================================
-- tenant_statistics
-- Daily statistics with hardcoded metrics in JSONB
-- =====================================================

CREATE TABLE tenant_statistics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    stat_date DATE NOT NULL,

    -- Calculated statistics (hardcoded metrics in application)
    metrics JSONB NOT NULL DEFAULT '{}'::JSONB,
    /* Standard metrics (calculated by DailyStatisticsAggregationService):
    {
      "dau": 1250,                      // Daily Active Users
      "login_success_count": 1560,      // Successful logins
      "login_failure_count": 40,        // Failed logins
      "login_success_rate": 97.5,       // Success rate percentage
      "tokens_issued": 800,             // Total tokens issued
      "new_users": 45,                  // New user registrations
      "total_users": 12500              // Cumulative user count
    }
    */

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- One record per tenant per date
    UNIQUE(tenant_id, stat_date)
);

-- Index for time-series queries
CREATE INDEX idx_stats_tenant_date ON tenant_statistics (tenant_id, stat_date DESC);

-- Index for date-only queries (cross-tenant analytics)
CREATE INDEX idx_stats_date ON tenant_statistics (stat_date);

-- JSONB GIN index for flexible queries
CREATE INDEX idx_stats_metrics_gin ON tenant_statistics USING GIN (metrics);

-- Row Level Security (RLS)
ALTER TABLE tenant_statistics ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON tenant_statistics
    USING (tenant_id = current_setting('app.tenant_id')::uuid);

ALTER TABLE tenant_statistics FORCE ROW LEVEL SECURITY;

-- Comments
COMMENT ON TABLE tenant_statistics IS 'Daily tenant statistics with hardcoded metrics (DAU, login rate, tokens, etc.)';
COMMENT ON COLUMN tenant_statistics.stat_date IS 'Date of statistics (daily granularity)';
COMMENT ON COLUMN tenant_statistics.metrics IS 'Calculated statistics in JSONB format (hardcoded metrics in application code)';
COMMENT ON COLUMN tenant_statistics.updated_at IS 'Timestamp of last update (for recalculation tracking)';

-- =====================================================
-- Data retention function (optional)
-- =====================================================

-- Delete statistics data older than specified days
CREATE OR REPLACE FUNCTION cleanup_old_statistics(retention_days INTEGER)
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM tenant_statistics
    WHERE stat_date < CURRENT_DATE - (retention_days || ' days')::INTERVAL;

    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION cleanup_old_statistics(INTEGER) IS 'Delete statistics data older than specified days (e.g., SELECT cleanup_old_statistics(365))';

-- =====================================================
-- Utility view (optional)
-- =====================================================

-- Latest 30 days statistics view
CREATE VIEW latest_statistics AS
SELECT
    tenant_id,
    stat_date,
    metrics->>'dau' AS dau,
    metrics->>'login_success_rate' AS login_success_rate,
    metrics->>'tokens_issued' AS tokens_issued,
    metrics->>'new_users' AS new_users,
    created_at,
    updated_at
FROM tenant_statistics
WHERE stat_date >= CURRENT_DATE - INTERVAL '30 days'
ORDER BY tenant_id, stat_date DESC;

COMMENT ON VIEW latest_statistics IS 'Latest 30 days statistics with extracted common metrics';

-- =====================================================
-- daily_active_users
-- Track unique daily active users separately
-- =====================================================

CREATE TABLE daily_active_users (
    tenant_id UUID NOT NULL,
    stat_date DATE NOT NULL,
    user_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Unique constraint to prevent duplicates
    PRIMARY KEY (tenant_id, stat_date, user_id)
);

-- Index for DAU count queries
CREATE INDEX idx_dau_tenant_date ON daily_active_users (tenant_id, stat_date);

-- Index for user-specific queries
CREATE INDEX idx_dau_user ON daily_active_users (user_id, stat_date);

-- Row Level Security (RLS)
ALTER TABLE daily_active_users ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON daily_active_users
    USING (tenant_id = current_setting('app.tenant_id')::uuid);

ALTER TABLE daily_active_users FORCE ROW LEVEL SECURITY;

-- Comments
COMMENT ON TABLE daily_active_users IS 'Daily active users tracking (one row per unique user per day)';
COMMENT ON COLUMN daily_active_users.stat_date IS 'Date of user activity';
COMMENT ON COLUMN daily_active_users.user_id IS 'Active user identifier';

-- =====================================================
-- DAU calculation function
-- =====================================================

-- Get DAU count for a specific tenant and date
CREATE OR REPLACE FUNCTION get_dau_count(p_tenant_id UUID, p_stat_date DATE)
RETURNS INTEGER AS $$
DECLARE
    dau_count INTEGER;
BEGIN
    SELECT COUNT(DISTINCT user_id) INTO dau_count
    FROM daily_active_users
    WHERE tenant_id = p_tenant_id AND stat_date = p_stat_date;

    RETURN COALESCE(dau_count, 0);
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION get_dau_count(UUID, DATE) IS 'Get DAU count for a specific tenant and date';

-- =====================================================
-- Data retention for DAU
-- =====================================================

CREATE OR REPLACE FUNCTION cleanup_old_dau(retention_days INTEGER)
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM daily_active_users
    WHERE stat_date < CURRENT_DATE - (retention_days || ' days')::INTERVAL;

    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION cleanup_old_dau(INTEGER) IS 'Delete DAU data older than specified days';

-- =====================================================
-- monthly_active_users
-- Track unique monthly active users (MAU)
-- =====================================================

CREATE TABLE monthly_active_users (
    tenant_id UUID NOT NULL,
    stat_month DATE NOT NULL,
    user_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Unique constraint to prevent duplicates
    PRIMARY KEY (tenant_id, stat_month, user_id)
);

-- Index for MAU count queries
CREATE INDEX idx_mau_tenant_month ON monthly_active_users (tenant_id, stat_month);

-- Index for user-specific queries
CREATE INDEX idx_mau_user ON monthly_active_users (user_id, stat_month);

-- Row Level Security (RLS)
ALTER TABLE monthly_active_users ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON monthly_active_users
    USING (tenant_id = current_setting('app.tenant_id')::uuid);

ALTER TABLE monthly_active_users FORCE ROW LEVEL SECURITY;

-- Comments
COMMENT ON TABLE monthly_active_users IS 'Monthly active users tracking (one row per unique user per calendar month)';
COMMENT ON COLUMN monthly_active_users.stat_month IS 'First day of the calendar month (e.g., 2025-01-01)';
COMMENT ON COLUMN monthly_active_users.user_id IS 'Active user identifier';

-- =====================================================
-- MAU calculation function
-- =====================================================

-- Get MAU count for a specific tenant and month
CREATE OR REPLACE FUNCTION get_mau_count(p_tenant_id UUID, p_stat_month DATE)
RETURNS INTEGER AS $$
DECLARE
    mau_count INTEGER;
BEGIN
    SELECT COUNT(DISTINCT user_id) INTO mau_count
    FROM monthly_active_users
    WHERE tenant_id = p_tenant_id AND stat_month = p_stat_month;

    RETURN COALESCE(mau_count, 0);
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION get_mau_count(UUID, DATE) IS 'Get MAU count for a specific tenant and calendar month (stat_month should be first day of month)';

-- =====================================================
-- Data retention for MAU
-- =====================================================

CREATE OR REPLACE FUNCTION cleanup_old_mau(retention_days INTEGER)
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM monthly_active_users
    WHERE stat_month < DATE_TRUNC('month', CURRENT_DATE) - (retention_days || ' days')::INTERVAL;

    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION cleanup_old_mau(INTEGER) IS 'Delete MAU data older than specified days (based on stat_month)';
