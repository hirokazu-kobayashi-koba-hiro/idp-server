-- WebAuthn Credentials table
-- Based on LINE FIDO2 Server implementation and WebAuthn4j best practices
CREATE TABLE webauthn_credentials
(
    -- Credential Identifier
    id                      VARCHAR(512) PRIMARY KEY,       -- Credential ID (Base64URL encoded)

    -- User Information
    idp_user_id             VARCHAR(256) NOT NULL,          -- Internal user ID
    username                VARCHAR(64),                    -- User name for display
    user_display_name       VARCHAR(64),                    -- User display name
    user_icon               VARCHAR(128),                   -- User icon URL

    -- Relying Party
    rp_id                   VARCHAR(256) NOT NULL,          -- Relying Party ID

    -- Authenticator Information
    aaguid                  VARCHAR(36) NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000', -- Authenticator AAGUID
    attested_credential_data MEDIUMTEXT NOT NULL,           -- Attested Credential Data (Base64URL, includes public key)
    signature_algorithm     INTEGER,                        -- Signature algorithm (-7=ES256, -257=RS256, etc)
    sign_count              BIGINT NOT NULL DEFAULT 0,      -- Signature counter for clone detection

    -- Attestation
    attestation_type        VARCHAR(50),                    -- none, basic, self, attca, ecdaa

    -- WebAuthn Features
    rk                      BOOLEAN DEFAULT false,          -- Resident Key (discoverable credential / passkey)
    cred_protect            INTEGER,                        -- Credential Protection Level (1=userVerificationOptional, 2=userVerificationOptionalWithCredentialIDList, 3=userVerificationRequired)
    transports              JSON,                           -- Authenticator transports (e.g., ["usb", "nfc", "ble", "internal", "hybrid"])

    -- Timestamps
    created_at              DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at              DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP NOT NULL,
    authenticated_at        DATETIME                        -- Last authentication timestamp
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Indexes
CREATE INDEX idx_webauthn_user_id ON webauthn_credentials (idp_user_id);
CREATE INDEX idx_webauthn_rp_id ON webauthn_credentials (rp_id);
CREATE INDEX idx_webauthn_user_rp ON webauthn_credentials (idp_user_id, rp_id);
CREATE INDEX idx_webauthn_aaguid ON webauthn_credentials (aaguid);
CREATE INDEX idx_webauthn_rk ON webauthn_credentials (rk);
CREATE INDEX idx_webauthn_authenticated ON webauthn_credentials (authenticated_at);