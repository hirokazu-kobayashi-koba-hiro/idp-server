-- ============================================================================
-- V0_14_0_4__client_attestation_challenge.sql
-- Server-provided challenge for Attestation-Based Client Authentication (refs #1521)
--
-- draft-ietf-oauth-attestation-based-client-auth-10 Section 6: the server may
-- hand a Challenge to the Client Instance, which then carries it as the
-- `challenge` claim of the Client Attestation PoP JWT.
--
-- Reusable within its TTL, deliberately:
--   Nothing in the draft requires a challenge to be single-use. Section 11.1
--   lists issuing challenges without storing the seen ones as one of its three
--   approaches, and says what it buys: "This approach scales well, while only
--   guaranteeing freshness, but no replay protection within the limited
--   time-window chosen by the Authorization/Resource Server."
--
--   CIBA polling is the case that makes reuse worth having: with a single-use
--   challenge a poll-mode authentication would need one challenge per poll (up
--   to 61 with the default 300s / 5s settings). The row is therefore never
--   consumed and the TTL is the only bound.
--
--   The challenge proves freshness, not uniqueness. Replay of a single Client
--   Attestation PoP JWT within its iat window is NOT detected: the jti
--   seen-values store that would catch it is unimplemented (Section 11.1 makes
--   it a SHOULD).
--
-- Not tied to a client_id: the challenge endpoint is unauthenticated
-- (Section 6.1), so the issuing request carries no credential to bind to.
--
-- Key:
--   PRIMARY KEY (tenant_id, challenge), with no surrogate id. This departs from the
--   surrogate-plus-unique shape used elsewhere (idp_user, role, client_configuration)
--   on purpose: the challenge is the identity of the row, no table references this
--   one, the only access path is the exact (tenant_id, challenge) lookup of the
--   verifying request, and tenant-scoped uniqueness is required by the flow rather
--   than incidental. A surrogate id would add a column no query reads and would still
--   need a unique index to keep the same guarantee.
--
--   The cost is carried by InnoDB, where the clustered index is ordered by a 43
--   character random value (32 bytes base64url) and every secondary index repeats the
--   primary key. The table is bounded by its TTL, so the fragmentation is accepted.
-- ============================================================================

CREATE TABLE client_attestation_challenge
(
    challenge  VARCHAR(255)            NOT NULL,
    tenant_id  UUID                    NOT NULL,
    expires_at TIMESTAMP               NOT NULL,
    created_at TIMESTAMP DEFAULT now() NOT NULL,
    PRIMARY KEY (tenant_id, challenge),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
);

ALTER TABLE client_attestation_challenge ENABLE ROW LEVEL SECURITY;
CREATE
POLICY tenant_isolation_policy
  ON client_attestation_challenge
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
ALTER TABLE client_attestation_challenge FORCE ROW LEVEL SECURITY;

-- Expired challenges are removed by POST /v1/admin/operations/delete-expired-data
-- (ClientAttestationChallengeOperationCommandRepository#deleteExpired).
CREATE INDEX idx_client_attestation_challenge_expires_at
    ON client_attestation_challenge (expires_at);
