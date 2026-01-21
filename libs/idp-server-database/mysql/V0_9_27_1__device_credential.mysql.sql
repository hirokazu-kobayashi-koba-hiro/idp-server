-- ============================================================================
-- V0_9_27_1__device_credential.mysql.sql
-- Authentication Device Credential Support
--
-- Summary:
--   Add credential columns to idp_user_authentication_devices table.
--   Credentials are stored directly in the device record (1 device = 1 credential).
--
-- Supported credential types:
--   - fido2: FIDO2/WebAuthn credential
--   - fido_uaf: FIDO UAF credential
--
-- Design:
--   - credential_id: Indexed for fast user lookup during authentication
--   - credential_payload: Actual credential data (public key, counter, etc.)
--   - credential_metadata: Meta info (rp_id, fido_server_id, aaguid, etc.)
-- ============================================================================

-- ============================================================================
-- 1. Add credential columns to idp_user_authentication_devices
-- ============================================================================
ALTER TABLE idp_user_authentication_devices
    ADD COLUMN credential_type     VARCHAR(50),
    ADD COLUMN credential_id       VARCHAR(1400),
    ADD COLUMN credential_payload  JSON,
    ADD COLUMN credential_metadata JSON;

-- ============================================================================
-- 2. Indexes
-- ============================================================================
-- Credential ID lookup for FIDO2 authentication (find user by credentialId)
-- Note: MySQL requires prefix length for VARCHAR > 767 bytes with utf8mb4
CREATE INDEX idx_auth_device_credential_id
    ON idp_user_authentication_devices (tenant_id, credential_id(255));

-- Credential type lookup
CREATE INDEX idx_auth_device_credential_type
    ON idp_user_authentication_devices (tenant_id, credential_type);
