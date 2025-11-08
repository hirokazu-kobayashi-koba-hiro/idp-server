CREATE TABLE webauthn_credentials
(
    id                 TEXT PRIMARY KEY,
    idp_user_id        VARCHAR(256)            NOT NULL,
    rp_id              VARCHAR(256)            NOT NULL,
    attestation_object TEXT                   NOT NULL,
    sign_count         BIGINT                  NOT NULL,
    created_at         TIMESTAMP DEFAULT now() NOT NULL,
    updated_at         TIMESTAMP DEFAULT now() NOT NULL
);

CREATE INDEX idx_webauthn_user_id ON webauthn_credentials (idp_user_id);
CREATE INDEX idx_webauthn_rp_id ON webauthn_credentials (rp_id);
CREATE INDEX idx_webauthn_user_rp ON webauthn_credentials (idp_user_id, rp_id);
