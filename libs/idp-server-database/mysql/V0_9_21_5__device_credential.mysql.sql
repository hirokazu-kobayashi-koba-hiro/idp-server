-- ============================================================================
-- V0_9_21_5__device_credential.mysql.sql
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
--   Common columns + type_specific_data (JSON) for flexibility
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

    -- Device (optional - FIDO2 may not have device association)
    device_id               VARCHAR(36),

    -- Credential Type
    credential_type         VARCHAR(50)                    NOT NULL,
    -- Values: jwt_bearer_symmetric, jwt_bearer_asymmetric, fido2, fido_uaf

    -- Type-specific data (JSON)
    -- JWT Bearer: {"algorithm": "HS256", "secret_value": "...", "jwks": {...}}
    -- FIDO2: {"rp_id": "...", "aaguid": "...", "attested_credential_data": "...", "sign_count": 0, ...}
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
