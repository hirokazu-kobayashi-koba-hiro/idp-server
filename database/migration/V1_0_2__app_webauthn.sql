CREATE TABLE webauthn_rp_configuration
(
    tenant_id                  VARCHAR(256) REFERENCES tenant (id) ON DELETE CASCADE PRIMARY KEY,
    rp_id                      VARCHAR(4096) NOT NULL UNIQUE,
    rp_name                    VARCHAR(256)  NOT NULL,
    origin                     VARCHAR(4096) NOT NULL,
    attestation_preference     VARCHAR(50)   NOT NULL CHECK (attestation_preference IN ('none', 'indirect', 'direct')),
    authenticator_attachment   VARCHAR(50) CHECK (authenticator_attachment IN ('platform', 'cross-platform')),
    require_resident_key       BOOLEAN       NOT NULL DEFAULT FALSE,
    user_verification_required BOOLEAN       NOT NULL DEFAULT TRUE,
    user_presence_required     BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at                 TIMESTAMP              default now() NOT NULL,
    updated_at                 TIMESTAMP              default now() NOT NULL
);

--  REFERENCES idp_user (id) ON DELETE CASCADE

CREATE TABLE webauthn_credentials
(
    id                 BYTEA PRIMARY KEY,
    idp_user_id        VARCHAR(256)            NOT NULL,
    rp_id              VARCHAR(256)            NOT NULL REFERENCES webauthn_rp_configuration (rp_id) ON DELETE CASCADE,
--     public_key         BYTEA                   NOT NULL,
    attestation_object BYTEA                   NOT NULL,
    sign_count         BIGINT                  NOT NULL,
    created_at         TIMESTAMP DEFAULT now() NOT NULL,
    updated_at         TIMESTAMP DEFAULT now() NOT NULL,
    UNIQUE (idp_user_id, rp_id)
);

-- Index for fast lookups by user_id
CREATE INDEX idx_webauthn_user_id ON webauthn_credentials (idp_user_id);

-- Index for fast lookups by rp_id (useful in multi-tenant setups)
CREATE INDEX idx_webauthn_rp_id ON webauthn_credentials (rp_id);

-- Composite index for faster lookups in multi-tenant scenarios
CREATE INDEX idx_webauthn_user_rp ON webauthn_credentials (idp_user_id, rp_id);

