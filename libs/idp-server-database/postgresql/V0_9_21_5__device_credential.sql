-- ============================================================================
-- V0_9_21_5__device_credential.sql
-- Authentication Credential Support (Option B: Unified Table)
--
-- Summary:
--   Add idp_user_authentication_device_credentials table to store various
--   credential types. Designed to support:
--   - JWT Bearer (symmetric/asymmetric) for RFC 7523
--   - FIDO2/WebAuthn (future migration)
--   - FIDO UAF (future)
--
-- Design:
--   Common columns + type_specific_data (JSONB) for flexibility
-- ============================================================================

-- ============================================================================
-- 1. Create idp_user_authentication_device_credentials table
-- ============================================================================
CREATE TABLE idp_user_authentication_device_credentials (
    -- Credential Identifier
    id                      TEXT                           NOT NULL,

    -- Multi-Tenant & User
    tenant_id               UUID                           NOT NULL,
    user_id                 UUID                           NOT NULL,

    -- Device (optional - FIDO2 may not have device association)
    device_id               UUID,

    -- Credential Type
    credential_type         VARCHAR(50)                    NOT NULL,
    -- Values: jwt_bearer_symmetric, jwt_bearer_asymmetric, fido2, fido_uaf

    -- Type-specific data (JSONB)
    -- JWT Bearer: {"algorithm": "HS256", "secret_value": "...", "jwks": {...}}
    -- FIDO2: {"rp_id": "...", "aaguid": "...", "attested_credential_data": "...", "sign_count": 0, ...}
    type_specific_data      JSONB                          NOT NULL DEFAULT '{}'::jsonb,

    -- Common timestamps
    created_at              TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    updated_at              TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL,
    expires_at              TIMESTAMP WITH TIME ZONE,
    revoked_at              TIMESTAMP WITH TIME ZONE,

    PRIMARY KEY (id, tenant_id),
    FOREIGN KEY (user_id) REFERENCES idp_user (id) ON DELETE CASCADE,
    FOREIGN KEY (device_id) REFERENCES idp_user_authentication_devices (id) ON DELETE CASCADE
);

-- ============================================================================
-- 2. Indexes
-- ============================================================================
-- User lookup (find all credentials for a user)
CREATE INDEX idx_auth_device_cred_user
    ON idp_user_authentication_device_credentials (tenant_id, user_id);

-- Device lookup (find credentials for a device)
CREATE INDEX idx_auth_device_cred_device
    ON idp_user_authentication_device_credentials (tenant_id, device_id)
    WHERE device_id IS NOT NULL;

-- Type lookup (find credentials by type)
CREATE INDEX idx_auth_device_cred_type
    ON idp_user_authentication_device_credentials (tenant_id, credential_type);

-- Active credential lookup (exclude revoked and expired)
CREATE INDEX idx_auth_device_cred_active
    ON idp_user_authentication_device_credentials (tenant_id, device_id, credential_type)
    WHERE revoked_at IS NULL;

-- JSONB index for type-specific queries (e.g., algorithm lookup)
CREATE INDEX idx_auth_device_cred_specific
    ON idp_user_authentication_device_credentials USING GIN (type_specific_data);

-- ============================================================================
-- 3. Row Level Security (RLS) configuration
-- ============================================================================
ALTER TABLE idp_user_authentication_device_credentials ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy
    ON idp_user_authentication_device_credentials
    USING (tenant_id = current_setting('app.tenant_id')::uuid);

ALTER TABLE idp_user_authentication_device_credentials FORCE ROW LEVEL SECURITY;

-- ============================================================================
-- 4. Comments
-- ============================================================================
COMMENT ON TABLE idp_user_authentication_device_credentials IS
'Unified authentication credentials table (Option B design).
Supports multiple credential types via type_specific_data JSONB column:
- jwt_bearer_symmetric: HMAC keys for JWT Bearer Grant (RFC 7523)
- jwt_bearer_asymmetric: RSA/EC public keys for JWT Bearer Grant
- fido2: WebAuthn/Passkey credentials (future migration from webauthn_credentials)
- fido_uaf: FIDO UAF credentials (future)';

COMMENT ON COLUMN idp_user_authentication_device_credentials.id IS
'Credential identifier. Format varies by type:
- JWT Bearer: UUID
- FIDO2: Base64URL encoded credential ID';

COMMENT ON COLUMN idp_user_authentication_device_credentials.credential_type IS
'Credential type: jwt_bearer_symmetric, jwt_bearer_asymmetric, fido2, fido_uaf';

COMMENT ON COLUMN idp_user_authentication_device_credentials.type_specific_data IS
'Type-specific data in JSONB format. Schema depends on credential_type.';

COMMENT ON COLUMN idp_user_authentication_device_credentials.device_id IS
'Associated authentication device. NULL for credentials not tied to a device (e.g., some FIDO2 scenarios).';

COMMENT ON COLUMN idp_user_authentication_device_credentials.expires_at IS
'Credential expiration time. NULL means no expiration.';

COMMENT ON COLUMN idp_user_authentication_device_credentials.revoked_at IS
'Credential revocation time. NULL means active.';
