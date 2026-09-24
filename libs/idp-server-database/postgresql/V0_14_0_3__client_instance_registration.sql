-- ============================================================================
-- V0_14_0_3__client_instance_registration.sql
-- Client Instance registration challenge (refs #1521)
--
-- Summary:
--   A registration is authenticated by two things: an ID token (who) and platform
--   attestation evidence (which device and key). The challenge ties them together.
--
--   At challenge issuance the server decides what may be registered (client_id /
--   the instance identifier to assign) and keeps that decision server-side. The
--   registration request carries the challenge, so the request body is never
--   trusted for those values.
--
--   The challenge is embedded in the platform evidence (Android Key Attestation
--   extension, App Attest client data hash), and
--   request_hash = SHA-256( challenge || canonical JWK ) is the nonce of the ID
--   token, which binds the user, the evidence and the key being registered to
--   the client_id and instance identifier of the ticket.
--
--   created_at is written by the application together with expires_at rather than
--   left to the column default, because it is compared with the iat of the ID token
--   and both have to be on the application clock.
--
-- Single use:
--   used_at is stamped on consumption. Rows are kept after use so that replays
--   are distinguishable from unknown challenges in the audit trail.
--
-- Key:
--   PRIMARY KEY (tenant_id, challenge), with no surrogate id. This departs from the
--   surrogate-plus-unique shape used elsewhere (idp_user, role, client_configuration)
--   on purpose: the challenge is the identity of the row, no table references this
--   one, the only access path is the exact (tenant_id, challenge) lookup of the
--   consuming request, and tenant-scoped uniqueness is required by the flow rather
--   than incidental. A surrogate id would add a column no query reads and would still
--   need a unique index to keep the same guarantee.
--
--   The cost is carried by InnoDB, where the clustered index is ordered by a 43
--   character random value (32 bytes base64url) and every secondary index repeats the
--   primary key. The table is bounded by its TTL, so the fragmentation is accepted.
-- ============================================================================

CREATE TABLE client_instance_registration_challenge
(
    challenge   VARCHAR(255)            NOT NULL,
    tenant_id   UUID                    NOT NULL,
    client_id   VARCHAR(255)            NOT NULL,
    instance_id VARCHAR(255)            NOT NULL,
    expires_at  TIMESTAMP               NOT NULL,
    used_at     TIMESTAMP,
    created_at  TIMESTAMP DEFAULT now() NOT NULL,
    PRIMARY KEY (tenant_id, challenge),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
);

ALTER TABLE client_instance_registration_challenge ENABLE ROW LEVEL SECURITY;
CREATE
POLICY tenant_isolation_policy
  ON client_instance_registration_challenge
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
ALTER TABLE client_instance_registration_challenge FORCE ROW LEVEL SECURITY;

-- Expired challenges are removed by POST /v1/admin/operations/delete-expired-data
-- (ClientInstanceRegistrationChallengeOperationCommandRepository#deleteExpired).
-- Expiry is the only condition: a consumed row stays while it is still valid so that a replay
-- remains distinguishable from an unknown challenge, as described above.
CREATE INDEX idx_client_instance_registration_challenge_expires_at
    ON client_instance_registration_challenge (expires_at);
