-- ============================================================================
-- V0_13_0_5__client_attestation_challenge.sql
-- Server-provided challenge for Attestation-Based Client Authentication (refs #1521)
--
-- draft-ietf-oauth-attestation-based-client-auth-10 Section 6: the server may
-- hand a Challenge to the Client Instance, which then carries it as the
-- `challenge` claim of the Client Attestation PoP JWT.
--
-- Reusable within its TTL, deliberately:
--   Section 9.7 and Section 11.1 allow a challenge bound to a Client Instance
--   session to be validated against the single value expected for that session,
--   without a seen-values store. CIBA polling is exactly that case -- with a
--   single-use challenge a poll-mode authentication would need one challenge per
--   poll (up to 61 with the default 300s / 5s settings). The TTL therefore
--   stands in for the session lifetime and the row is never consumed.
--
--   Replay of a single Client Attestation PoP JWT is detected separately, by the
--   jti seen-values store, not here.
--
-- Not tied to a client_id: the challenge endpoint is unauthenticated
-- (Section 6.1), so the issuing request carries no credential to bind to.
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

-- Expired challenges are removed by the retention job.
CREATE INDEX idx_client_attestation_challenge_expires_at
    ON client_attestation_challenge (expires_at);
