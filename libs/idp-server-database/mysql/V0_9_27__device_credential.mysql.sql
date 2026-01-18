-- ============================================================================
-- V0_9_27__device_credential.mysql.sql
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
--   - Common columns + type_specific_data (JSON) for flexibility
--   - FIDO credentials store reference to FIDO server, not actual credential data
--   - FIDO server (internal/external) manages the actual credential
-- ============================================================================

-- ============================================================================
-- 1. Create idp_user_authentication_device_credentials table
-- ============================================================================
CREATE TABLE idp_user_authentication_device_credentials (
    -- Credential Identifier
    id                      VARCHAR(512)                   NOT NULL,

    -- Multi-Tenant & User
    tenant_id               VARCHAR(36)                    NOT NULL,
    user_id                 VARCHAR(36)                    NOT NULL,

    -- Device (optional - some credentials may not have device association)
    device_id               VARCHAR(36),

    -- Credential Type
    credential_type         VARCHAR(50)                    NOT NULL,
    -- Values: jwt_bearer_symmetric, jwt_bearer_asymmetric, fido2, fido_uaf

    -- Type-specific data (JSON)
    -- JWT Bearer Symmetric:
    --   {"algorithm": "HS256", "secret_value": "base64url-encoded-secret"}
    -- JWT Bearer Asymmetric:
    --   {"algorithm": "ES256", "jwks": {"keys": [...]}}
    -- FIDO2:
    --   {"fido_server_id": "internal", "credential_id": "base64url-id", "rp_id": "example.com"}
    -- FIDO UAF:
    --   {"fido_server_id": "internal", "credential_id": "...", "app_id": "..."}
    type_specific_data      JSON                           NOT NULL,

    -- Common timestamps
    created_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP NOT NULL,
    expires_at              TIMESTAMP NULL,
    revoked_at              TIMESTAMP NULL,

    PRIMARY KEY (id, tenant_id),
    CONSTRAINT fk_auth_device_cred_user
        FOREIGN KEY (user_id) REFERENCES idp_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_auth_device_cred_device
        FOREIGN KEY (device_id) REFERENCES idp_user_authentication_devices (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- 2. Indexes
-- ============================================================================
-- User lookup (find all credentials for a user)
CREATE INDEX idx_auth_device_cred_user
    ON idp_user_authentication_device_credentials (tenant_id, user_id);

-- Device lookup (find credentials for a device)
CREATE INDEX idx_auth_device_cred_device
    ON idp_user_authentication_device_credentials (tenant_id, device_id);

-- Type lookup (find credentials by type)
CREATE INDEX idx_auth_device_cred_type
    ON idp_user_authentication_device_credentials (tenant_id, credential_type);

-- Active credential lookup
CREATE INDEX idx_auth_device_cred_active
    ON idp_user_authentication_device_credentials (tenant_id, device_id, credential_type, revoked_at);
