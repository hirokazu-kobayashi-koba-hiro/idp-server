-- #1416: self-service email verification / change.
--
-- Deliberately NOT built on authentication_transaction + authentication_interactions. That machinery
-- exists to drive *authentication* — a negotiation with a party that holds no credentials yet — so
-- its interaction endpoints (/v1/authorizations/{id}/{type}, /v1/authentications/{id}/{type}) are
-- unauthenticated by design. Mounting an already-authenticated self-service profile mutation on it
-- exposed the mutation through those public doors. This table is the small piece actually needed:
-- server-side state for "a one-time code was sent to this address, for this user".
--
-- user_id is part of the lookup predicate, not a value checked after loading, so a challenge that
-- does not belong to the caller is simply not found.
--
-- operation is stored on the row rather than derived from the request path, so the authorization
-- decision (which scope was required) and the behaviour (what gets committed) cannot disagree.
CREATE TABLE email_verification_challenge
(
    id                UUID                    NOT NULL,
    tenant_id         UUID                    NOT NULL,
    user_id           UUID                    NOT NULL,
    operation         VARCHAR(32)             NOT NULL,
    target_email      VARCHAR(255)            NOT NULL,
    verification_code VARCHAR(16)             NOT NULL,
    attempts          INTEGER   DEFAULT 0     NOT NULL,
    expires_at        TIMESTAMP               NOT NULL,
    created_at        TIMESTAMP DEFAULT now() NOT NULL,
    updated_at        TIMESTAMP DEFAULT now() NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES idp_user (id) ON DELETE CASCADE
);

-- Supports the only query shape used at commit time, and the expiry sweep.
CREATE INDEX idx_email_verification_challenge_user
    ON email_verification_challenge (tenant_id, user_id);
CREATE INDEX idx_email_verification_challenge_expires_at
    ON email_verification_challenge (expires_at);

ALTER TABLE email_verification_challenge ENABLE ROW LEVEL SECURITY;
CREATE
POLICY tenant_isolation_policy
  ON email_verification_challenge
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
ALTER TABLE email_verification_challenge FORCE ROW LEVEL SECURITY;
