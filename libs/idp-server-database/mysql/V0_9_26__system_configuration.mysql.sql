-- System-wide configuration table
-- This table stores application-level configuration that applies across all tenants
--
-- Note: No default data is inserted. When no configuration exists:
-- - SSRF protection is DISABLED (allows all requests)
-- - Trusted proxy handling is DISABLED
-- Administrators should explicitly configure via the management API for production use.

CREATE TABLE system_configuration
(
    id            VARCHAR(36)                                NOT NULL DEFAULT 'system',
    configuration JSON                                       NOT NULL,
    created_at    DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6)   NOT NULL,
    updated_at    DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6)   NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
