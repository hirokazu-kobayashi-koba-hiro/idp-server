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
--   - id: instance identifier (UUID, the primary key like the other entity tables), and
--     the kid that the Client Attestation JWT carries to select which registered key to
--     verify with. An operator looks an instance up by id alone, without knowing which
--     client it belongs to; authentication still matches client_id as well, so the key of
--     one client's instance never authenticates another client. Two paths assign it, and
--     they differ in who chooses the value:
--       * the registration endpoint (V0_14_0_3) issues it together with the challenge
--         and keeps it server-side, so the registration request cannot choose it
--       * the management API takes a caller supplied id, and only generates one when
--         the request omits it
--   - instance_key: CIK public key (JWK). Never contains private material.
--   - instance_key_thumbprint: RFC 7638 thumbprint of instance_key, unique within a tenant.
--     A refresh token of an instance is bound to this value, so a second instance holding the
--     same key would redeem it; and a revoked key must stay revoked rather than come back as a
--     new instance. The scope is the tenant, not the table: a constraint across tenants would let
--     one tenant learn of, or block, the keys of another.
--   - status: active / revoked. Revocation applies immediately because
--     every authentication resolves the key from this table. A revoked row is kept: it
--     holds the key taken, and records what was revoked, when and why.
--   - revocation_reason: why the instance was revoked — operator (the management API) or
--     superseded (the same user registered a newer instance of the client).
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
    id                   UUID                    NOT NULL,
    tenant_id            UUID                    NOT NULL,
    client_id            VARCHAR(255)            NOT NULL,
    instance_key         JSONB                   NOT NULL,
    instance_key_thumbprint VARCHAR(64)          NOT NULL,
    status               VARCHAR(32)             NOT NULL DEFAULT 'active',
    attestation_evidence JSONB,
    device_id            UUID,
    user_id              UUID,
    created_at           TIMESTAMP DEFAULT now() NOT NULL,
    updated_at           TIMESTAMP DEFAULT now() NOT NULL,
    expires_at           TIMESTAMP,
    revoked_at           TIMESTAMP,
    revocation_reason    VARCHAR(32),
    PRIMARY KEY (id),
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
CREATE UNIQUE INDEX uq_client_instance_tenant_key_thumbprint
    ON client_instance (tenant_id, instance_key_thumbprint);

CREATE INDEX idx_client_instance_tenant_client_user
    ON client_instance (tenant_id, client_id, user_id);

-- A user holds at most one active instance of a client. Registering a new one revokes the
-- others in the same transaction; this index is what keeps two registrations running at once
-- from both leaving an active instance, the later one failing instead.
CREATE UNIQUE INDEX uq_client_instance_active_user
    ON client_instance (tenant_id, client_id, user_id)
    WHERE status = 'active' AND user_id IS NOT NULL;

-- The management list API pages by (tenant_id, client_id) ordered by created_at.
-- Without this index the ordering has to be produced by sorting every instance of the
-- client, and once a client holds enough of the table the planner drops the index scan
-- for a sequential scan: measured at 300k instances under one client_id, a single
-- 20 row page read the whole table (27k buffers, 112ms). A mobile deployment is exactly
-- that shape, since one client_id covers the whole app and instances scale with installs.
-- With the ordering carried by the index the LIMIT stops early (5 buffers, 0.09ms).
CREATE INDEX idx_client_instance_tenant_client_created_at
    ON client_instance (tenant_id, client_id, created_at DESC);
