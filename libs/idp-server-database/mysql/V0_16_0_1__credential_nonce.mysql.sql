-- ============================================================================
-- V0_16_0_1__credential_nonce.mysql.sql
-- c_nonce of the OpenID4VCI Nonce Endpoint (OpenID for Verifiable Credential Issuance 1.0 Section 7)
--
-- See the PostgreSQL migration for the design notes. MySQL has no Row Level
-- Security; tenant isolation relies on the tenant_id predicate of every query.
-- ============================================================================

CREATE TABLE credential_nonce
(
    nonce      VARCHAR(255)                             NOT NULL,
    tenant_id  CHAR(36)                                 NOT NULL,
    expires_at DATETIME(6)                              NOT NULL,
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (tenant_id, nonce),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE,
    INDEX idx_credential_nonce_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
