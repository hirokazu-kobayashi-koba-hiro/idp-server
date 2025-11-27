-- V0_10_0__statistics.mysql.sql
-- Issue #441: Tenant statistics data collection (MySQL version)
-- Simple version with hardcoded metrics

-- =====================================================
-- tenant_statistics
-- Daily statistics with hardcoded metrics in JSON
-- =====================================================

CREATE TABLE tenant_statistics (
    id CHAR(36) PRIMARY KEY DEFAULT (UUID()),
    tenant_id CHAR(36) NOT NULL,
    stat_date DATE NOT NULL,

    -- Calculated statistics (hardcoded metrics in application)
    metrics JSON NOT NULL DEFAULT ('{}'),
    /* Standard metrics (calculated by DailyStatisticsAggregationService):
    {
      "dau": 1250,                      // Daily Active Users
      "mau": 15000,                     // Monthly Active Users
      "login_success_count": 1560,      // Successful logins
      "login_failure_count": 40,        // Failed logins
      "login_success_rate": 97.5,       // Success rate percentage
      "tokens_issued": 800,             // Total tokens issued
      "new_users": 45,                  // New user registrations
      "total_users": 12500              // Cumulative user count
    }
    */

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- One record per tenant per date
    UNIQUE KEY uk_tenant_date (tenant_id, stat_date),
    KEY idx_stats_tenant_date (tenant_id, stat_date DESC),
    KEY idx_stats_date (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Daily tenant statistics with hardcoded metrics (DAU, MAU, login rate, tokens, etc.)';

-- =====================================================
-- daily_active_users
-- Track unique daily active users separately
-- =====================================================

CREATE TABLE daily_active_users (
    tenant_id CHAR(36) NOT NULL,
    stat_date DATE NOT NULL,
    user_id CHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- Unique constraint to prevent duplicates
    PRIMARY KEY (tenant_id, stat_date, user_id),
    KEY idx_dau_tenant_date (tenant_id, stat_date),
    KEY idx_dau_user (user_id, stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Daily active users tracking (one row per unique user per day)';

-- =====================================================
-- monthly_active_users
-- Track unique monthly active users (MAU)
-- =====================================================

CREATE TABLE monthly_active_users (
    tenant_id CHAR(36) NOT NULL,
    stat_month DATE NOT NULL COMMENT 'First day of the calendar month (e.g., 2025-01-01)',
    user_id CHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- Unique constraint to prevent duplicates
    PRIMARY KEY (tenant_id, stat_month, user_id),
    KEY idx_mau_tenant_month (tenant_id, stat_month),
    KEY idx_mau_user (user_id, stat_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Monthly active users tracking (one row per unique user per calendar month)';

-- =====================================================
-- Data retention procedures
-- =====================================================

DELIMITER //

-- Delete statistics data older than specified days
CREATE PROCEDURE cleanup_old_statistics(IN retention_days INT)
BEGIN
    DELETE FROM tenant_statistics
    WHERE stat_date < DATE_SUB(CURDATE(), INTERVAL retention_days DAY);

    SELECT ROW_COUNT() AS deleted_count;
END//

-- Delete DAU data older than specified days
CREATE PROCEDURE cleanup_old_dau(IN retention_days INT)
BEGIN
    DELETE FROM daily_active_users
    WHERE stat_date < DATE_SUB(CURDATE(), INTERVAL retention_days DAY);

    SELECT ROW_COUNT() AS deleted_count;
END//

-- Delete MAU data older than specified days
CREATE PROCEDURE cleanup_old_mau(IN retention_days INT)
BEGIN
    DELETE FROM monthly_active_users
    WHERE stat_month < DATE_SUB(DATE_FORMAT(CURDATE(), '%Y-%m-01'), INTERVAL retention_days DAY);

    SELECT ROW_COUNT() AS deleted_count;
END//

DELIMITER ;

-- =====================================================
-- Utility views
-- =====================================================

-- Latest 30 days statistics view
CREATE VIEW latest_statistics AS
SELECT
    tenant_id,
    stat_date,
    JSON_UNQUOTE(JSON_EXTRACT(metrics, '$.dau')) AS dau,
    JSON_UNQUOTE(JSON_EXTRACT(metrics, '$.mau')) AS mau,
    JSON_UNQUOTE(JSON_EXTRACT(metrics, '$.login_success_rate')) AS login_success_rate,
    JSON_UNQUOTE(JSON_EXTRACT(metrics, '$.tokens_issued')) AS tokens_issued,
    JSON_UNQUOTE(JSON_EXTRACT(metrics, '$.new_users')) AS new_users,
    created_at,
    updated_at
FROM tenant_statistics
WHERE stat_date >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
ORDER BY tenant_id, stat_date DESC;
