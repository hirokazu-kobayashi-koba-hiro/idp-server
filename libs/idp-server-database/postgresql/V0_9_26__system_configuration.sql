-- System-wide configuration table
-- This table stores application-level configuration that applies across all tenants
-- Unlike tenant-specific tables, this does NOT have Row Level Security
--
-- Note: No default data is inserted. When no configuration exists:
-- - SSRF protection is DISABLED (allows all requests)
-- - Trusted proxy handling is DISABLED
-- Administrators should explicitly configure via the management API for production use.

CREATE TABLE system_configuration
(
    id            VARCHAR(36)             NOT NULL DEFAULT 'system',
    configuration JSONB                   NOT NULL DEFAULT '{}',
    created_at    TIMESTAMP               NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP               NOT NULL DEFAULT now(),
    PRIMARY KEY (id)
);

-- Create index for JSONB querying
CREATE INDEX idx_system_configuration_config ON system_configuration USING GIN (configuration);

COMMENT ON TABLE system_configuration IS 'System-wide configuration that applies across all tenants';
COMMENT ON COLUMN system_configuration.id IS 'Fixed identifier (always "system")';
COMMENT ON COLUMN system_configuration.configuration IS 'JSON configuration containing ssrf_protection, trusted_proxies settings';
