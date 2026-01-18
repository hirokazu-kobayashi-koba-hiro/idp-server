-- ============================================================================
-- V0_9_27__device_credential.sql
-- Authentication Credential Support
--
-- Summary:
--   Add idp_user_authentication_device_credentials table to store various
--   credential types with unified structure.
--
-- Supported credential types:
--   - jwt_bearer_symmetric: HMAC keys for JWT Bearer Grant (RFC 7523)
--   - jwt_bearer_asymmetric: RSA/EC public keys for JWT Bearer Grant
--   - fido2: Reference to FIDO server credential (internal/external)
--   - fido_uaf: Reference to FIDO UAF server credential
--
-- Design:
--   - Common columns + type_specific_data (JSONB) for flexibility
--   - FIDO credentials store reference to FIDO server, not actual credential data
--   - FIDO server (internal/external) manages the actual credential
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

    -- Device (optional - some credentials may not have device association)
    device_id               UUID,

    -- Credential Type
    credential_type         VARCHAR(50)                    NOT NULL,
    -- Values: jwt_bearer_symmetric, jwt_bearer_asymmetric, fido2, fido_uaf

    -- Type-specific data (JSONB)
    -- JWT Bearer Symmetric:
    --   {"algorithm": "HS256", "secret_value": "base64url-encoded-secret"}
    -- JWT Bearer Asymmetric:
    --   {"algorithm": "ES256", "jwks": {"keys": [...]}}
    -- FIDO2:
    --   {"fido_server_id": "internal", "credential_id": "base64url-id", "rp_id": "example.com"}
    -- FIDO UAF:
    --   {"fido_server_id": "internal", "credential_id": "...", "app_id": "..."}
    type_specific_data      JSONB                          NOT NULL DEFAULT '{}'::jsonb,

    -- Common timestamps
    created_at              TIMESTAMP DEFAULT now() NOT NULL,
    updated_at              TIMESTAMP DEFAULT now() NOT NULL,
    expires_at              TIMESTAMP,
    revoked_at              TIMESTAMP,

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

-- Active credential lookup (exclude revoked)
CREATE INDEX idx_auth_device_cred_active
    ON idp_user_authentication_device_credentials (tenant_id, device_id, credential_type)
    WHERE revoked_at IS NULL;

-- JSONB index for type-specific queries (e.g., fido_server_id, algorithm)
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
'Unified authentication credentials table.

Credential Types:
- jwt_bearer_symmetric: HMAC keys for JWT Bearer Grant (RFC 7523). IdP manages secret.
- jwt_bearer_asymmetric: RSA/EC public keys for JWT Bearer Grant. IdP manages public key.
- fido2: Reference to FIDO2/WebAuthn credential in FIDO server (internal or external).
- fido_uaf: Reference to FIDO UAF credential in FIDO server.

For FIDO types, actual credential data (attested_credential_data, sign_count, etc.)
is managed by the FIDO server. This table only stores reference information.';

COMMENT ON COLUMN idp_user_authentication_device_credentials.id IS
'Credential identifier. Format varies by type:
- JWT Bearer: UUID
- FIDO2/UAF: Can match the credential_id in FIDO server or be a separate UUID';

COMMENT ON COLUMN idp_user_authentication_device_credentials.credential_type IS
'Credential type: jwt_bearer_symmetric, jwt_bearer_asymmetric, fido2, fido_uaf';

COMMENT ON COLUMN idp_user_authentication_device_credentials.type_specific_data IS
'Type-specific data in JSONB format.

JWT Bearer Symmetric:
  {"algorithm": "HS256", "secret_value": "base64url-encoded-secret"}

JWT Bearer Asymmetric:
  {"algorithm": "ES256", "jwks": {"keys": [...]}}

FIDO2:
  {
    "fido_server_id": "internal",       -- Reference to configured FIDO server
    "credential_id": "base64url-...",   -- Credential ID in FIDO server
    "rp_id": "example.com"              -- Relying Party ID
  }

FIDO UAF:
  {
    "fido_server_id": "internal",
    "credential_id": "...",
    "app_id": "https://example.com"
  }';

COMMENT ON COLUMN idp_user_authentication_device_credentials.device_id IS
'Associated authentication device. NULL for credentials not tied to a specific device.';

COMMENT ON COLUMN idp_user_authentication_device_credentials.expires_at IS
'Credential expiration time. NULL means no expiration.';

COMMENT ON COLUMN idp_user_authentication_device_credentials.revoked_at IS
'Credential revocation time. NULL means active.';
