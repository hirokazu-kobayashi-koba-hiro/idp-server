-- ============================================================================
-- V0_9_21_4__idp_user_authentication_devices.sql
-- Issue #964: Authentication device search query performance improvement
--
-- Summary:
--   Extract idp_user.authentication_devices (JSONB) to a separate table
--   Enable fast search using PK (BTree index) instead of GIN index
--
-- Changes:
--   1. Create idp_user_authentication_devices table
--   2. Migrate existing data
--   3. Keep GIN index (idx_user_devices_gin_path_ops) for backward compatibility
-- ============================================================================

-- ============================================================================
-- 1. Create idp_user_authentication_devices table
-- ============================================================================
-- id = device identifier (PK)
CREATE TABLE idp_user_authentication_devices (
    id                      UUID                           NOT NULL,
    tenant_id               UUID                           NOT NULL,
    user_id                 UUID                           NOT NULL,
    os                      VARCHAR(100),
    model                   VARCHAR(255),
    platform                VARCHAR(50),
    locale                  VARCHAR(50),
    app_name                VARCHAR(255),
    priority                INTEGER DEFAULT 1,
    available_methods       JSONB DEFAULT '[]'::jsonb,
    notification_token      TEXT,
    notification_channel    VARCHAR(50),
    created_at              TIMESTAMP DEFAULT now() NOT NULL,
    updated_at              TIMESTAMP DEFAULT now() NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (user_id) REFERENCES idp_user (id) ON DELETE CASCADE
);

-- Index for tenant + user lookup
CREATE INDEX idx_user_auth_device_tenant_user
    ON idp_user_authentication_devices (tenant_id, user_id);

-- Index for user_id lookup (list devices for a user)
CREATE INDEX idx_user_auth_device_user_id
    ON idp_user_authentication_devices (user_id);

-- ============================================================================
-- 2. Row Level Security (RLS) configuration
-- ============================================================================
ALTER TABLE idp_user_authentication_devices ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy
    ON idp_user_authentication_devices
    USING (tenant_id = current_setting('app.tenant_id')::uuid);

ALTER TABLE idp_user_authentication_devices FORCE ROW LEVEL SECURITY;

-- ============================================================================
-- 3. Migrate existing data
-- ============================================================================
INSERT INTO idp_user_authentication_devices (
    id,
    tenant_id,
    user_id,
    os,
    model,
    platform,
    locale,
    app_name,
    priority,
    available_methods,
    notification_token,
    notification_channel,
    created_at,
    updated_at
)
SELECT
    (device->>'id')::uuid AS id,
    u.tenant_id,
    u.id AS user_id,
    device->>'os' AS os,
    device->>'model' AS model,
    device->>'platform' AS platform,
    device->>'locale' AS locale,
    device->>'app_name' AS app_name,
    COALESCE((device->>'priority')::integer, 1) AS priority,
    COALESCE(device->'available_methods', '[]'::jsonb) AS available_methods,
    device->>'notification_token' AS notification_token,
    device->>'notification_channel' AS notification_channel,
    u.created_at,
    u.updated_at
FROM idp_user u,
     jsonb_array_elements(u.authentication_devices) AS device
WHERE u.authentication_devices IS NOT NULL
  AND u.authentication_devices != '[]'::jsonb;

-- ============================================================================
-- 4. Add comments
-- ============================================================================
COMMENT ON TABLE idp_user_authentication_devices IS
'User authentication device information. Used for push authentication such as CIBA/FIDO UAF.
Migrated from idp_user.authentication_devices (Issue #964).
Changed from JSONB to normalized table for fast lookup by device id (PK).';

COMMENT ON COLUMN idp_user_authentication_devices.id IS
'Unique device identifier (PK). Used to identify users in CIBA flow.';

COMMENT ON COLUMN idp_user_authentication_devices.available_methods IS
'Available authentication methods for this device (e.g., ["fido-uaf", "push"])';
