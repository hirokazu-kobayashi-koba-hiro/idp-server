-- ============================================================================
-- V0_9_27_1__device_credential.sql
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
    ADD COLUMN credential_payload  JSONB DEFAULT '{}'::jsonb,
    ADD COLUMN credential_metadata JSONB DEFAULT '{}'::jsonb;

-- ============================================================================
-- 2. Indexes
-- ============================================================================
-- Credential ID lookup for FIDO2 authentication (find user by credentialId)
CREATE INDEX idx_auth_device_credential_id
    ON idp_user_authentication_devices (tenant_id, credential_id)
    WHERE credential_id IS NOT NULL;

-- Credential type lookup
CREATE INDEX idx_auth_device_credential_type
    ON idp_user_authentication_devices (tenant_id, credential_type)
    WHERE credential_type IS NOT NULL;

-- ============================================================================
-- 3. Comments
-- ============================================================================
COMMENT ON COLUMN idp_user_authentication_devices.credential_type IS
'Credential type: fido2, fido_uaf. NULL if device has no credential.';

COMMENT ON COLUMN idp_user_authentication_devices.credential_id IS
'FIDO credential ID (Base64URL encoded). Max 1400 chars for WebAuthn spec (1023 bytes).
Used for indexed lookup during authentication.';

COMMENT ON COLUMN idp_user_authentication_devices.credential_payload IS
'Credential payload in JSONB format.

FIDO2 example:
  {
    "public_key": "base64url-encoded-public-key",
    "counter": 42,
    "attestation": "base64url-encoded-attestation"
  }';

COMMENT ON COLUMN idp_user_authentication_devices.credential_metadata IS
'Credential metadata in JSONB format.

FIDO2 example:
  {
    "rp_id": "example.com",
    "fido_server_id": "internal",
    "aaguid": "00000000-0000-0000-0000-000000000000",
    "transports": ["usb", "internal"],
    "created_at": "2026-01-20T12:00:00Z"
  }';
