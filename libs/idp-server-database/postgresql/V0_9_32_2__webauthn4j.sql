-- WebAuthn Credentials table (Multi-Tenant)
-- Based on LINE FIDO2 Server implementation and WebAuthn4j best practices
-- Design: Core columns + JSONB for extensibility (WebAuthn Level 3+ ready)
CREATE TABLE webauthn_credentials
(
    -- Credential Identifier
    id                       TEXT PRIMARY KEY,                -- Credential ID (Base64URL encoded)

    -- Multi-Tenant
    tenant_id                UUID NOT NULL,                   -- Tenant ID for multi-tenancy

    -- User Information
    user_id                  VARCHAR(256) NOT NULL,           -- FIDO2 User ID (WebAuthn user.id)
    username                 VARCHAR(64),                     -- User name for display
    user_display_name        VARCHAR(64),                     -- User display name

    -- Relying Party
    rp_id                    VARCHAR(256) NOT NULL,           -- Relying Party ID

    -- Core Authenticator Information (frequently searched)
    aaguid                   VARCHAR(36) NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000', -- Authenticator AAGUID (for policy & vulnerability tracking)
    attested_credential_data TEXT NOT NULL,                   -- Attested Credential Data (Base64URL, includes public key)
    signature_algorithm      INTEGER,                         -- Signature algorithm (-7=ES256, -257=RS256, etc)
    sign_count               BIGINT NOT NULL DEFAULT 0,       -- Signature counter for clone detection

    -- Core WebAuthn Features
    rk                       BOOLEAN DEFAULT false,           -- Resident Key (discoverable credential / passkey)

    -- WebAuthn Level 3: Backup Flags
    backup_eligible          BOOLEAN,                         -- BE flag: Can be backed up (e.g., iCloud Keychain, Google Password Manager)
    backup_state             BOOLEAN,                         -- BS flag: Currently backed up (synced passkey)

    -- JSONB: Authenticator metadata (transports, attachment, etc.)
    -- Example: {"transports": ["usb", "nfc"], "attachment": "cross-platform"}
    authenticator            JSONB NOT NULL DEFAULT '{}',

    -- JSONB: Attestation information
    -- Example: {"type": "basic", "format": "packed", "trust_path": [...]}
    attestation              JSONB NOT NULL DEFAULT '{}',

    -- JSONB: WebAuthn extensions (credProtect, prf, largeBlob, etc.)
    -- Example: {"cred_protect": 2, "prf": {"enabled": true}, "large_blob": {"supported": true}}
    extensions               JSONB NOT NULL DEFAULT '{}',

    -- JSONB: Device/registration context (for audit)
    -- Example: {"name": "YubiKey 5 NFC", "registered_ip": "192.168.1.1", "registered_ua": "..."}
    device                   JSONB NOT NULL DEFAULT '{}',

    -- JSONB: Future extensions
    metadata                 JSONB NOT NULL DEFAULT '{}',

    -- Timestamps
    created_at               TIMESTAMP DEFAULT now() NOT NULL,
    updated_at               TIMESTAMP DEFAULT now() NOT NULL,
    authenticated_at         TIMESTAMP,                       -- Last authentication timestamp

    -- Foreign Keys
    CONSTRAINT fk_webauthn_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
);

-- Indexes: Core columns
CREATE INDEX idx_webauthn_tenant_id ON webauthn_credentials (tenant_id);
CREATE INDEX idx_webauthn_user_id ON webauthn_credentials (user_id);
CREATE INDEX idx_webauthn_tenant_user ON webauthn_credentials (tenant_id, user_id);
CREATE INDEX idx_webauthn_tenant_rp ON webauthn_credentials (tenant_id, rp_id);
CREATE INDEX idx_webauthn_user_rp ON webauthn_credentials (user_id, rp_id);
CREATE INDEX idx_webauthn_rp_id ON webauthn_credentials (rp_id);
CREATE INDEX idx_webauthn_aaguid ON webauthn_credentials (aaguid);
CREATE INDEX idx_webauthn_rk ON webauthn_credentials (rk) WHERE rk = true;
CREATE INDEX idx_webauthn_authenticated ON webauthn_credentials (authenticated_at);

-- Indexes: Backup flags (for synced passkey policy)
CREATE INDEX idx_webauthn_backup_eligible ON webauthn_credentials (backup_eligible) WHERE backup_eligible = true;
CREATE INDEX idx_webauthn_backup_state ON webauthn_credentials (backup_state) WHERE backup_state = true;

-- Indexes: JSONB (GIN for flexible queries)
CREATE INDEX idx_webauthn_authenticator ON webauthn_credentials USING GIN (authenticator);
CREATE INDEX idx_webauthn_extensions ON webauthn_credentials USING GIN (extensions);

-- Row Level Security
ALTER TABLE webauthn_credentials ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_policy
    ON webauthn_credentials
    USING (tenant_id = current_setting('app.tenant_id')::uuid);
ALTER TABLE webauthn_credentials FORCE ROW LEVEL SECURITY;
