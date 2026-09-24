-- ============================================================================
-- V0_14_0_2__client_instance.sql
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
--   - id: instance identifier, and the kid that the Client Attestation JWT carries to
--     select which registered key to verify with. Two paths assign it, and they differ
--     in who chooses the value:
--       * the registration endpoint (V0_14_0_3) issues it together with the challenge
--         and keeps it server-side, so the registration request cannot choose it
--       * the management API takes a caller supplied id, and only generates one when
--         the request omits it
--   - instance_key: CIK public key (JWK). Never contains private material.
--   - status: active / revoked. Revocation applies immediately because
--     every authentication resolves the key from this table.
--   - attestation_evidence: verification result of the platform attestation
--     (e.g. Play Integrity verdict, App Attest result) kept for audit and
--     risk decisions.
--   - device_id: optional cross reference to an authentication device, set only through
--     the management API.
--   - user_id: the user the instance is bound to. The end-user registration flow sets it
--     from the ID token that authenticates the registration; instances registered through
--     the management API have none.
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
    user_id              UUID,
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

-- Instances registered by the end-user flow are bound to a user. Registering a new one
-- revokes the user's other active instances of the client, and deleting a user revokes
-- theirs: both are lookups by user rather than by primary key.
CREATE INDEX idx_client_instance_tenant_client_user
    ON client_instance (tenant_id, client_id, user_id);

-- The management list API pages by (tenant_id, client_id) ordered by created_at.
-- Without this index the ordering has to be produced by sorting every instance of the
-- client, and once a client holds enough of the table the planner drops the index scan
-- for a sequential scan: measured at 300k instances under one client_id, a single
-- 20 row page read the whole table (27k buffers, 112ms). A mobile deployment is exactly
-- that shape, since one client_id covers the whole app and instances scale with installs.
-- With the ordering carried by the index the LIMIT stops early (5 buffers, 0.09ms).
CREATE INDEX idx_client_instance_tenant_client_created_at
    ON client_instance (tenant_id, client_id, created_at DESC);
