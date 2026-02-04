-- ================================================
-- Composite Indexes for security_event and audit_log tables
-- Issue #1227: Query performance improvement
--
-- Problem:
--   - Single-column indexes don't efficiently support multi-condition queries
--   - Queries with external_user_id, client_id, user_id, or type filters
--     combined with tenant_id and created_at range result in full scans
--   - Performance degrades from 294ms to 5.46s with large datasets
--
-- Solution:
--   - Add composite indexes covering: tenant_id + filter_column + created_at DESC
--   - Index order optimized for tenant isolation and ORDER BY
--
-- ================================================

-- Helper procedure to add index if not exists
DELIMITER //

DROP PROCEDURE IF EXISTS add_index_if_not_exists//
CREATE PROCEDURE add_index_if_not_exists(
    IN p_table_name VARCHAR(64),
    IN p_index_name VARCHAR(64),
    IN p_index_columns VARCHAR(255)
)
BEGIN
    DECLARE index_exists INT DEFAULT 0;

    SELECT COUNT(*) INTO index_exists
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table_name
      AND INDEX_NAME = p_index_name;

    IF index_exists = 0 THEN
        SET @sql = CONCAT('CREATE INDEX ', p_index_name, ' ON ', p_table_name, ' (', p_index_columns, ')');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//

DELIMITER ;

-- External user ID search
-- Query pattern: WHERE tenant_id = ? AND external_user_id = ? AND created_at BETWEEN ? AND ? ORDER BY created_at DESC
CALL add_index_if_not_exists('security_event', 'idx_events_tenant_external_user_created_at', 'tenant_id, external_user_id, created_at DESC');

-- Client ID search
-- Query pattern: WHERE tenant_id = ? AND client_id = ? AND created_at BETWEEN ? AND ? ORDER BY created_at DESC
CALL add_index_if_not_exists('security_event', 'idx_events_tenant_client_created_at', 'tenant_id, client_id, created_at DESC');

-- User ID search
-- Query pattern: WHERE tenant_id = ? AND user_id = ? AND created_at BETWEEN ? AND ? ORDER BY created_at DESC
CALL add_index_if_not_exists('security_event', 'idx_events_tenant_user_created_at', 'tenant_id, user_id, created_at DESC');

-- Event type search
-- Query pattern: WHERE tenant_id = ? AND type = ? AND created_at BETWEEN ? AND ? ORDER BY created_at DESC
CALL add_index_if_not_exists('security_event', 'idx_events_tenant_type_created_at', 'tenant_id, type, created_at DESC');

-- ================================================
-- audit_log indexes
-- ================================================

-- External user ID search
CALL add_index_if_not_exists('audit_log', 'idx_audit_log_tenant_external_user_created_at', 'tenant_id, external_user_id, created_at DESC');

-- Client ID search
CALL add_index_if_not_exists('audit_log', 'idx_audit_log_tenant_client_created_at', 'tenant_id, client_id, created_at DESC');

-- User ID search
CALL add_index_if_not_exists('audit_log', 'idx_audit_log_tenant_user_created_at', 'tenant_id, user_id, created_at DESC');

-- Type search (replaces idx_audit_log_type_created which lacks tenant_id)
CALL add_index_if_not_exists('audit_log', 'idx_audit_log_tenant_type_created_at', 'tenant_id, type, created_at DESC');

-- Outcome result search
CALL add_index_if_not_exists('audit_log', 'idx_audit_log_tenant_outcome_created_at', 'tenant_id, outcome_result, created_at DESC');

-- Cleanup helper procedure
DROP PROCEDURE IF EXISTS add_index_if_not_exists;

-- ================================================
-- Note: The following single-column indexes may become redundant
-- after these composite indexes are added. Consider dropping them
-- after verifying query execution plans in production:
--
-- security_event:
--   idx_events_external_user_id (external_user_id)
--   idx_events_client (client_id)
--   idx_events_user (user_id)
--   idx_events_type (type)
--
-- audit_log:
--   idx_audit_log_external_user_id (external_user_id)
--   idx_audit_log_client_id (client_id)
--   idx_audit_log_user_id (user_id)
--   idx_audit_log_type_created (type, created_at DESC) -- lacks tenant_id
--
-- Do NOT drop them immediately - verify with EXPLAIN first.
-- ================================================
