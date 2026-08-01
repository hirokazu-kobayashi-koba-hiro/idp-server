-- ============================================================================
-- V0_13_0_3__client_instance.sql
-- Client Instance for Attestation-Based Client Authentication (refs #1521)
--
-- Summary:
--   Stores Client Instance Keys (CIK) registered per (tenant, client).
--   Used by attest_jwt_client_auth with client_attestation_trust_source =
--   "registered_instance_key": the Client Attestation JWT is self-signed by
--   the instance key, and the server resolves the trusted key by the JOSE
--   header kid (= client_instance.id).
--
-- Design:
--   - id: instance identifier. Matches the kid of the self-signed
--     Client Attestation JWT. Client-generated (e.g. UUID).
--   - instance_key: CIK public key (JWK). Never contains private material.
--   - status: active / revoked. Revocation applies immediately because
--     every authentication resolves the key from this table.
--   - attestation_evidence: verification result of the platform attestation
--     (e.g. Play Integrity verdict, App Attest result) kept for audit and
--     risk decisions.
--   - device_id: optional cross reference to an authentication device.
-- ============================================================================

CREATE TABLE client_instance
(
    id                   VARCHAR(255)            NOT NULL,
    tenant_id            UUID                    NOT NULL,
    client_id            VARCHAR(255)            NOT NULL,
    instance_key         JSONB                   NOT NULL,
    status               VARCHAR(32)             NOT NULL DEFAULT 'active',
    attestation_evidence JSONB,
    device_id            UUID,
    created_at           TIMESTAMP DEFAULT now() NOT NULL,
    updated_at           TIMESTAMP DEFAULT now() NOT NULL,
    expires_at           TIMESTAMP,
    revoked_at           TIMESTAMP,
    PRIMARY KEY (tenant_id, client_id, id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
);

ALTER TABLE client_instance ENABLE ROW LEVEL SECURITY;
CREATE
POLICY tenant_isolation_policy
  ON client_instance
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
ALTER TABLE client_instance FORCE ROW LEVEL SECURITY;
