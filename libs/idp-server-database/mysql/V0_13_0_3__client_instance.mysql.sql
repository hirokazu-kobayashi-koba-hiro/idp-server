-- ============================================================================
-- V0_13_0_3__client_instance.mysql.sql
-- Client Instance for Attestation-Based Client Authentication (refs #1521)
--
-- Summary:
--   Stores Client Instance Keys (CIK) registered per (tenant, client).
--   Used by attest_jwt_client_auth with client_attestation_trust_source =
--   "registered_instance_key": the Client Attestation JWT is self-signed by
--   the instance key, and the server resolves the trusted key by the JOSE
--   header kid (= client_instance.id).
--
-- Note: MySQL has no Row Level Security; tenant isolation relies on the
--   tenant_id predicate of every query (repository convention).
-- ============================================================================

CREATE TABLE client_instance
(
    id                   VARCHAR(255)                             NOT NULL,
    tenant_id            CHAR(36)                                 NOT NULL,
    client_id            VARCHAR(255)                             NOT NULL,
    instance_key         JSON                                     NOT NULL,
    status               VARCHAR(32)  DEFAULT 'active'            NOT NULL,
    attestation_evidence JSON,
    device_id            CHAR(36),
    created_at           DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    updated_at           DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    expires_at           DATETIME(6),
    revoked_at           DATETIME(6),
    PRIMARY KEY (tenant_id, client_id, id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
