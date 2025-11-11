-- WebAuthn Credentials table (Multi-Tenant)
-- Based on LINE FIDO2 Server implementation and WebAuthn4j best practices
CREATE TABLE webauthn_credentials
(
    -- Credential Identifier
    id                      TEXT PRIMARY KEY,                -- Credential ID (Base64URL encoded)

    -- Multi-Tenant
    tenant_id               UUID NOT NULL,                   -- Tenant ID for multi-tenancy

    -- User Information
    user_id                 VARCHAR(256) NOT NULL,          -- FIDO2 User ID (WebAuthn user.id)
    username                VARCHAR(64),                     -- User name for display
    user_display_name       VARCHAR(64),                     -- User display name

    -- Relying Party
    rp_id                   VARCHAR(256) NOT NULL,          -- Relying Party ID

    -- Authenticator Information
    aaguid                  VARCHAR(36) NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000', -- Authenticator AAGUID
    attested_credential_data TEXT NOT NULL,                 -- Attested Credential Data (Base64URL, includes public key)
    signature_algorithm     INTEGER,                         -- Signature algorithm (-7=ES256, -257=RS256, etc)
    sign_count              BIGINT NOT NULL DEFAULT 0,      -- Signature counter for clone detection

    -- Attestation
    attestation_type        VARCHAR(50),                    -- none, basic, self, attca, ecdaa

    -- WebAuthn Features
    rk                      BOOLEAN DEFAULT false,           -- Resident Key (discoverable credential / passkey)
    cred_protect            INTEGER,                         -- Credential Protection Level (1=userVerificationOptional, 2=userVerificationOptionalWithCredentialIDList, 3=userVerificationRequired)
    transports              JSONB,                           -- Authenticator transports (e.g., ["usb", "nfc", "ble", "internal", "hybrid"])

    -- Timestamps
    created_at              TIMESTAMP DEFAULT now() NOT NULL,
    updated_at              TIMESTAMP DEFAULT now() NOT NULL,
    authenticated_at        TIMESTAMP,                       -- Last authentication timestamp

    -- Foreign Keys
    CONSTRAINT fk_webauthn_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
);

-- Indexes
CREATE INDEX idx_webauthn_tenant_id ON webauthn_credentials (tenant_id);
CREATE INDEX idx_webauthn_user_id ON webauthn_credentials (user_id);
CREATE INDEX idx_webauthn_tenant_user ON webauthn_credentials (tenant_id, user_id);
CREATE INDEX idx_webauthn_tenant_rp ON webauthn_credentials (tenant_id, rp_id);
CREATE INDEX idx_webauthn_user_rp ON webauthn_credentials (user_id, rp_id);
CREATE INDEX idx_webauthn_rp_id ON webauthn_credentials (rp_id);
CREATE INDEX idx_webauthn_aaguid ON webauthn_credentials (aaguid);
CREATE INDEX idx_webauthn_rk ON webauthn_credentials (rk) WHERE rk = true;
CREATE INDEX idx_webauthn_authenticated ON webauthn_credentials (authenticated_at);
CREATE INDEX idx_webauthn_transports ON webauthn_credentials USING GIN (transports);

-- Row Level Security
ALTER TABLE webauthn_credentials ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_policy
    ON webauthn_credentials
    USING (tenant_id = current_setting('app.tenant_id')::uuid);
ALTER TABLE webauthn_credentials FORCE ROW LEVEL SECURITY;
