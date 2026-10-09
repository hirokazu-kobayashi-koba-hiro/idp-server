-- ============================================================================
-- V0_15_0_1__account_linking.mysql.sql
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
--
-- Note: MySQL has no row level security. Tenant isolation relies on the
--       tenant_id predicate in every repository query.
-- ============================================================================

-- ============================================================================
-- 1. linked_external_accounts
-- ============================================================================
CREATE TABLE linked_external_accounts
(
    id                       CHAR(36)                                 NOT NULL,
    tenant_id                CHAR(36)                                 NOT NULL,
    user_id                  CHAR(36)                                 NOT NULL,
    provider                 VARCHAR(255)                             NOT NULL,
    account_alias            VARCHAR(255)                             NOT NULL,
    -- Nullable on purpose. See the PostgreSQL migration: a plain OAuth 2.0 provider need
    -- not identify the resource owner, and a link is keyed without this column.
    federated_user_id        VARCHAR(255),
    federated_username       VARCHAR(255),
    scope                    TEXT,
    encrypted_access_token   JSON                                     NOT NULL,
    encrypted_refresh_token  JSON,
    encryption_key_id        VARCHAR(64)                              NOT NULL DEFAULT 'default',
    access_token_expires_at  DATETIME(6),
    refresh_token_expires_at DATETIME(6),
    metadata                 JSON,
    created_at               DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    updated_at               DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE,
    -- No FK on user_id by design. ON DELETE CASCADE would remove the row before
    -- UserLifecycleEventExecutor runs (UserDeletionService deletes the user at
    -- step 4 and publishes the lifecycle event at step 5), leaving the token
    -- alive at the external IdP with nothing left to revoke.
    -- No UNIQUE on (tenant_id, provider, federated_user_id) by design. See the PostgreSQL
    -- migration for the reasoning: this column records whose tokens these are, not an identity.
    UNIQUE KEY uk_linked_external_accounts_alias (tenant_id, user_id, provider, account_alias)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_linked_external_accounts_user
    ON linked_external_accounts (tenant_id, user_id);

CREATE INDEX idx_linked_external_accounts_federated_user
    ON linked_external_accounts (tenant_id, provider, federated_user_id);

CREATE INDEX idx_linked_external_accounts_access_token_expires_at
    ON linked_external_accounts (tenant_id, access_token_expires_at);

-- ============================================================================
-- 2. account_linking_sessions
-- ============================================================================
CREATE TABLE account_linking_sessions
(
    state                    VARCHAR(255)                             NOT NULL,
    tenant_id                CHAR(36)                                 NOT NULL,
    user_id                  CHAR(36)                                 NOT NULL,
    client_id                VARCHAR(255)                             NOT NULL,
    provider                 VARCHAR(255)                             NOT NULL,
    account_alias            VARCHAR(255),
    redirect_uri             TEXT                                     NOT NULL,
    requested_scope          TEXT,
    code_verifier            TEXT                                     NOT NULL,
    nonce                    VARCHAR(255),
    acr                      VARCHAR(255),
    amr                      TEXT,
    authenticated_at         DATETIME(6),
    status                   VARCHAR(20)                              NOT NULL,
    browser_binding_hash     VARCHAR(255),
    federated_user_id        VARCHAR(255),
    federated_username       VARCHAR(255),
    granted_scope            TEXT,
    encrypted_access_token   JSON,
    encrypted_refresh_token  JSON,
    encryption_key_id        VARCHAR(64),
    access_token_expires_at  DATETIME(6),
    refresh_token_expires_at DATETIME(6),
    expires_at               DATETIME(6)                              NOT NULL,
    created_at               DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    updated_at               DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (state),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_account_linking_sessions_expires_at
    ON account_linking_sessions (tenant_id, expires_at);
