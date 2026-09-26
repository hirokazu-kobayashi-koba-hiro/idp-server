-- ============================================================================
-- V0_16_0_1__credential_nonce.sql
-- c_nonce of the OpenID4VCI Nonce Endpoint (OpenID for Verifiable Credential Issuance 1.0 Section 7)
--
-- The c_nonce is the Credential Issuer's defense against a key proof being
-- replayed to obtain duplicate Credentials (Section 13.8 "Proof replay"): "The c_nonce
-- parameter serves as the main defense against this ... The Credential Issuer
-- determines for how long a particular nonce can be used."
--
-- Single use, unlike client_attestation_challenge:
--   A nonce is consumed by the Credential Request that carries it, so a proof
--   cannot be replayed at all, not only after the TTL. A batch request carries
--   several proofs with the same nonce and consumes it once. The consuming
--   statement is a DELETE of the unexpired row: one row affected means the nonce
--   was valid and is now spent, and two concurrent requests cannot both spend it.
--
-- Not tied to a client or a token: the Nonce Endpoint is not a protected
-- resource (Section 7.1), so the issuing request carries nothing to bind to.
--
-- Key: PRIMARY KEY (tenant_id, nonce), for the reasons given in
-- V0_14_0_4__client_attestation_challenge.sql.
-- ============================================================================

CREATE TABLE credential_nonce
(
    nonce      VARCHAR(255)            NOT NULL,
    tenant_id  UUID                    NOT NULL,
    expires_at TIMESTAMP               NOT NULL,
    created_at TIMESTAMP DEFAULT now() NOT NULL,
    PRIMARY KEY (tenant_id, nonce),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
);

ALTER TABLE credential_nonce ENABLE ROW LEVEL SECURITY;
CREATE
POLICY tenant_isolation_policy
  ON credential_nonce
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
ALTER TABLE credential_nonce FORCE ROW LEVEL SECURITY;

-- Nonces that were issued but never used are removed by
-- POST /v1/admin/operations/delete-expired-data
-- (CredentialNonceOperationCommandRepository#deleteExpired).
CREATE INDEX idx_credential_nonce_expires_at
    ON credential_nonce (expires_at);
