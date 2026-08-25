-- ============================================================================
-- V0_13_0_5__client_attestation_challenge.mysql.sql
-- Server-provided challenge for Attestation-Based Client Authentication (refs #1521)
--
-- See the PostgreSQL migration for the design notes. MySQL has no Row Level
-- Security; tenant isolation relies on the tenant_id predicate of every query.
-- ============================================================================

CREATE TABLE client_attestation_challenge
(
    challenge  VARCHAR(255)                             NOT NULL,
    tenant_id  CHAR(36)                                 NOT NULL,
    expires_at DATETIME(6)                              NOT NULL,
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (tenant_id, challenge),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE,
    INDEX idx_client_attestation_challenge_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
