-- V0_10_0__statistics.mysql.sql
-- Issue #441: Tenant statistics data collection (DAU/MAU/YAU) (MySQL version)

-- =====================================================
-- statistics_monthly
-- Monthly statistics with daily breakdown in JSON
-- =====================================================

CREATE TABLE statistics_monthly (
    id CHAR(36) PRIMARY KEY DEFAULT (UUID()),
    tenant_id CHAR(36) NOT NULL,
    stat_month CHAR(7) NOT NULL COMMENT 'YYYY-MM format (2025-01, 2025-02, ...)',

    -- Monthly summary (for quick access)
    monthly_summary JSON NOT NULL DEFAULT ('{}'),
    /* {
      "mau": 5000,
      "total_logins": 45000,
      "total_login_failures": 500,
      "new_users": 200
    } */

    -- Daily breakdown (for charts/graphs)
    daily_metrics JSON NOT NULL DEFAULT ('{}'),
    /* {
      "01": {"dau": 100, "logins": 1500, "failures": 20},
      "02": {"dau": 110, "logins": 1600, "failures": 15},
      ...
      "31": {"dau": 95, "logins": 1400, "failures": 18}
    } */

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_tenant_month (tenant_id, stat_month),
    KEY idx_statistics_monthly_tenant_month (tenant_id, stat_month DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Monthly tenant statistics with daily breakdown in JSON';

-- =====================================================
-- statistics_yearly
-- Yearly statistics summary
-- Note: Monthly breakdown is available via statistics_monthly table
-- =====================================================

CREATE TABLE statistics_yearly (
    id CHAR(36) PRIMARY KEY DEFAULT (UUID()),
    tenant_id CHAR(36) NOT NULL,
    stat_year CHAR(4) NOT NULL COMMENT 'YYYY format (2025, 2026, ...)',

    -- Yearly summary (for quick access)
    yearly_summary JSON NOT NULL DEFAULT ('{}'),
    /* {
      "yau": 15000,
      "total_logins": 500000,
      "total_login_failures": 5000,
      "new_users": 2000
    } */

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_tenant_year (tenant_id, stat_year),
    KEY idx_statistics_yearly_tenant_year (tenant_id, stat_year DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Yearly tenant statistics summary (monthly breakdown via statistics_monthly table)';

-- =====================================================
-- statistics_daily_users
-- Track unique daily active users
-- =====================================================

CREATE TABLE statistics_daily_users (
    tenant_id CHAR(36) NOT NULL,
    stat_date DATE NOT NULL,
    user_id CHAR(36) NOT NULL,
    last_used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (tenant_id, stat_date, user_id),
    KEY idx_statistics_daily_users_tenant_date (tenant_id, stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Daily active users tracking (one row per unique user per day)';

-- =====================================================
-- statistics_monthly_users
-- Track unique monthly active users
-- =====================================================

CREATE TABLE statistics_monthly_users (
    tenant_id CHAR(36) NOT NULL,
    stat_month CHAR(7) NOT NULL COMMENT 'YYYY-MM format (e.g., 2025-01)',
    user_id CHAR(36) NOT NULL,
    last_used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (tenant_id, stat_month, user_id),
    KEY idx_statistics_monthly_users_tenant_month (tenant_id, stat_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Monthly active users tracking (one row per unique user per calendar month)';

-- =====================================================
-- statistics_yearly_users
-- Track unique yearly active users
-- =====================================================

CREATE TABLE statistics_yearly_users (
    tenant_id CHAR(36) NOT NULL,
    stat_year CHAR(4) NOT NULL COMMENT 'YYYY format (e.g., 2025)',
    user_id CHAR(36) NOT NULL,
    last_used_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (tenant_id, stat_year, user_id),
    KEY idx_statistics_yearly_users_tenant_year (tenant_id, stat_year),
    KEY idx_statistics_yearly_users_last_used (tenant_id, last_used_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Yearly active users tracking (one row per unique user per calendar year)';

-- =====================================================
-- Data retention procedures
-- =====================================================

DELIMITER //

-- Delete statistics data older than specified months
CREATE PROCEDURE cleanup_old_statistics(IN retention_months INT)
BEGIN
    DECLARE cutoff_month CHAR(7);
    SET cutoff_month = DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL retention_months MONTH), '%Y-%m');

    DELETE FROM statistics_monthly
    WHERE stat_month < cutoff_month;

    SELECT ROW_COUNT() AS deleted_count;
END//

-- Delete daily user data older than specified days
CREATE PROCEDURE cleanup_old_daily_users(IN retention_days INT)
BEGIN
    DELETE FROM statistics_daily_users
    WHERE stat_date < DATE_SUB(CURDATE(), INTERVAL retention_days DAY);

    SELECT ROW_COUNT() AS deleted_count;
END//

-- Delete monthly user data older than specified months
CREATE PROCEDURE cleanup_old_monthly_users(IN retention_months INT)
BEGIN
    DECLARE cutoff_month CHAR(7);
    SET cutoff_month = DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL retention_months MONTH), '%Y-%m');

    DELETE FROM statistics_monthly_users
    WHERE stat_month < cutoff_month;

    SELECT ROW_COUNT() AS deleted_count;
END//

-- Delete yearly user data older than specified years
CREATE PROCEDURE cleanup_old_yearly_users(IN retention_years INT)
BEGIN
    DECLARE cutoff_year CHAR(4);
    SET cutoff_year = DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL retention_years YEAR), '%Y');

    DELETE FROM statistics_yearly_users
    WHERE stat_year < cutoff_year;

    SELECT ROW_COUNT() AS deleted_count;
END//

-- Delete yearly statistics data older than specified years
CREATE PROCEDURE cleanup_old_yearly_statistics(IN retention_years INT)
BEGIN
    DECLARE cutoff_year CHAR(4);
    SET cutoff_year = DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL retention_years YEAR), '%Y');

    DELETE FROM statistics_yearly
    WHERE stat_year < cutoff_year;

    SELECT ROW_COUNT() AS deleted_count;
END//

DELIMITER ;
