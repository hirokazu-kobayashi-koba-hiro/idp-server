-- ============================================================================
-- V0_15_0_1__account_linking.sql
-- External IdP Account Linking (Issue #1531)
--
-- Summary:
--   Store external IdP access/refresh tokens obtained by an already
--   authenticated user, so that they can be replayed against external APIs.
--   Independent of the existing federation (login) path; idp_user_sso_credentials
--   is left untouched.
--
-- Design:
--   - park-and-claim: the unauthenticated callback writes only into
--     account_linking_sessions. linked_external_accounts is written by the
--     Bearer-authenticated complete phase.
--   - user_id is bound at link time and never re-derived from the callback.
--   - Tokens are encrypted at the application layer (AES-GCM-256, AesCipher)
--     and stored in the same {cipherText, iv} JSON shape as oauth_token.
-- ============================================================================

-- ============================================================================
-- 1. linked_external_accounts
-- ============================================================================
CREATE TABLE linked_external_accounts
(
    id                       UUID                    NOT NULL,
    tenant_id                UUID                    NOT NULL,
    user_id                  UUID                    NOT NULL,
    provider                 VARCHAR(255)            NOT NULL,
    account_alias            VARCHAR(255)            NOT NULL,
    federated_user_id        VARCHAR(255)            NOT NULL,
    federated_username       VARCHAR(255),
    scope                    TEXT,
    encrypted_access_token   JSONB                   NOT NULL,
    encrypted_refresh_token  JSONB,
    encryption_key_id        VARCHAR(64)             NOT NULL DEFAULT 'default',
    access_token_expires_at  TIMESTAMP,
    refresh_token_expires_at TIMESTAMP,
    metadata                 JSONB,
    created_at               TIMESTAMP DEFAULT now() NOT NULL,
    updated_at               TIMESTAMP DEFAULT now() NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE,
    -- No FK on user_id by design. ON DELETE CASCADE would remove the row before
    -- UserLifecycleEventExecutor runs (UserDeletionService deletes the user at
    -- step 4 and publishes the lifecycle event at step 5), leaving the token
    -- alive at the external IdP with nothing left to revoke.
    UNIQUE (tenant_id, user_id, provider, account_alias),
    UNIQUE (tenant_id, provider, federated_user_id)
);

ALTER TABLE linked_external_accounts ENABLE ROW LEVEL SECURITY;
CREATE
POLICY tenant_isolation_policy
  ON linked_external_accounts
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
ALTER TABLE linked_external_accounts FORCE ROW LEVEL SECURITY;

CREATE INDEX idx_linked_external_accounts_user
    ON linked_external_accounts (tenant_id, user_id);

CREATE INDEX idx_linked_external_accounts_access_token_expires_at
    ON linked_external_accounts (tenant_id, access_token_expires_at);

-- ============================================================================
-- 2. account_linking_sessions
-- ============================================================================
CREATE TABLE account_linking_sessions
(
    state                    VARCHAR(255)            NOT NULL,
    tenant_id                UUID                    NOT NULL,
    user_id                  UUID                    NOT NULL,
    client_id                VARCHAR(255)            NOT NULL,
    provider                 VARCHAR(255)            NOT NULL,
    account_alias            VARCHAR(255),
    redirect_uri             TEXT                    NOT NULL,
    requested_scope          TEXT,
    code_verifier            TEXT                    NOT NULL,
    nonce                    VARCHAR(255),
    acr                      VARCHAR(255),
    amr                      TEXT,
    authenticated_at         TIMESTAMP,
    status                   VARCHAR(20)             NOT NULL,
    federated_user_id        VARCHAR(255),
    federated_username       VARCHAR(255),
    granted_scope            TEXT,
    encrypted_access_token   JSONB,
    encrypted_refresh_token  JSONB,
    encryption_key_id        VARCHAR(64),
    access_token_expires_at  TIMESTAMP,
    refresh_token_expires_at TIMESTAMP,
    expires_at               TIMESTAMP               NOT NULL,
    created_at               TIMESTAMP DEFAULT now() NOT NULL,
    updated_at               TIMESTAMP DEFAULT now() NOT NULL,
    PRIMARY KEY (state),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
);

ALTER TABLE account_linking_sessions ENABLE ROW LEVEL SECURITY;
CREATE
POLICY tenant_isolation_policy
  ON account_linking_sessions
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
ALTER TABLE account_linking_sessions FORCE ROW LEVEL SECURITY;

CREATE INDEX idx_account_linking_sessions_expires_at
    ON account_linking_sessions (tenant_id, expires_at);

-- ============================================================================
-- 3. Comments
-- ============================================================================
COMMENT ON COLUMN linked_external_accounts.account_alias IS
'Server-assigned identifier, {provider}-{seq} (google-1, google-2). Appears in URLs.
Never derived from federated_username, which the external IdP can change.';

COMMENT ON COLUMN linked_external_accounts.encryption_key_id IS
'Key generation used to encrypt the token columns. Currently always ''default'':
the application resolves a single AesCipher from ENCRYPTION_KEY. The column exists
so a key ring can be introduced without a table rewrite.';

COMMENT ON COLUMN account_linking_sessions.user_id IS
'Bound from the Bearer token at link time. MUST NOT be re-derived at the callback.
This binding plus the check at /linking/start and complete is what prevents
linking CSRF in both directions.';

COMMENT ON COLUMN account_linking_sessions.status IS
'pending -> authorized -> parked -> consumed.
Each transition is a single-shot conditional UPDATE; 0 rows updated means the
caller lost the race and MUST NOT touch the authorization code.';
