CREATE TABLE webauthn_credentials
(
    id                 VARBINARY(767) PRIMARY KEY,
    idp_user_id        VARCHAR(256)            NOT NULL,
    rp_id              VARCHAR(256)            NOT NULL,
    attestation_object MEDIUMBLOB              NOT NULL,
    sign_count         BIGINT                  NOT NULL,
    created_at         DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at         DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE (idp_user_id, rp_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_webauthn_user_id ON webauthn_credentials (idp_user_id);

CREATE INDEX idx_webauthn_rp_id ON webauthn_credentials (rp_id);

CREATE INDEX idx_webauthn_user_rp ON webauthn_credentials (idp_user_id, rp_id);