-- ============================================================================
-- V0_13_0_4__client_instance_registration.sql
-- Client Instance registration challenge (refs #1521)
--
-- Summary:
--   The registration endpoint is unauthenticated: platform attestation evidence
--   takes the role of the credential. The challenge is therefore issued as an
--   authorization ticket rather than a bare nonce.
--
--   At challenge issuance the server decides what may be registered
--   (client_id / device_id / the instance identifier to assign) and keeps that
--   decision server-side. The registration request carries only the challenge,
--   so the request body is never trusted for those values.
--
--   The challenge is also embedded in the platform evidence itself (Android Key
--   Attestation extension, and request_hash / client_data_hash computed over
--   challenge || canonical JWK), which is what binds the evidence to the
--   client_id, device_id and instance identifier of the ticket.
--
-- Single use:
--   used_at is stamped on consumption. Rows are kept after use so that replays
--   are distinguishable from unknown challenges in the audit trail.
-- ============================================================================

CREATE TABLE client_instance_registration_challenge
(
    challenge   VARCHAR(255)            NOT NULL,
    tenant_id   UUID                    NOT NULL,
    client_id   VARCHAR(255)            NOT NULL,
    device_id   UUID,
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

-- Expired challenges are removed by the retention job.
CREATE INDEX idx_client_instance_registration_challenge_expires_at
    ON client_instance_registration_challenge (expires_at);

-- Registration rejects a device that already holds an active instance, which is
-- a lookup by device rather than by primary key.
CREATE INDEX idx_client_instance_tenant_client_device
    ON client_instance (tenant_id, client_id, device_id);
