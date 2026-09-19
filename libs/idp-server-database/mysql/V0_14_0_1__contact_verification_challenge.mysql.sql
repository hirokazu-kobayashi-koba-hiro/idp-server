-- #1416: self-service contact (email / phone) verification and change. See the PostgreSQL counterpart for why this is a
-- dedicated table rather than an authentication_transaction + authentication_interactions pair.
--
-- MySQL has no row level security, so tenant isolation here rests entirely on the tenant_id
-- predicate every query carries — the same situation as every other table on this engine.
CREATE TABLE contact_verification_challenge
(
    id                CHAR(36)                                 NOT NULL,
    tenant_id         CHAR(36)                                 NOT NULL,
    user_id           CHAR(36)                                 NOT NULL,
    operation         VARCHAR(32)                              NOT NULL,
    target_value      VARCHAR(255)                             NOT NULL,
    -- One of these two carries the secret, never both. idp-server generates the code when the
    -- tenant's authentication configuration describes a local sender; when it delegates generation
    -- and verification to an external service, the code never reaches here and what is kept is the
    -- reference that identifies the exchange.
    verification_code VARCHAR(16),
    external_reference JSON,
    attempts          INT         DEFAULT 0                    NOT NULL,
    expires_at        DATETIME(6)                              NOT NULL,
    created_at        DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    updated_at        DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_contact_verification_challenge_user (tenant_id, user_id),
    KEY idx_contact_verification_challenge_expires_at (expires_at),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES idp_user (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
