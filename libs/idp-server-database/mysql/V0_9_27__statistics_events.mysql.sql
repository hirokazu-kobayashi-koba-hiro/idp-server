-- V0_9_27__statistics_events.mysql.sql
-- Issue #1198: Statistics data update performance improvement (MySQL version)
--
-- New table for normalized statistics events.
--
-- This table replaces JSONB updates in statistics_monthly with
-- row-based updates for better write performance.
-- Each (tenant_id, stat_date, event_type) combination is a separate row.

CREATE TABLE statistics_events (
    tenant_id CHAR(36) NOT NULL,
    stat_date DATE NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    count BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (tenant_id, stat_date, event_type),
    KEY idx_statistics_events_tenant_date (tenant_id, stat_date DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Normalized statistics events. Replaces JSONB updates for better write performance.';
