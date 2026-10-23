-- ============================================================================
-- V0_15_0_2__tenant_data_keys.sql
-- Two-tier encryption keys for stored external tokens (Issue #1531)
--
-- Summary:
--   Tokens are encrypted with a data key (DEK) held per tenant and purpose.
--   Each DEK is stored only wrapped by a key encryption key (KEK) from the
--   deployment's keyring (ENCRYPTION_KEYS, or a KMS later). Rotating the KEK
--   re-wraps the rows of this table and leaves the encrypted tokens untouched.
--
-- Design:
--   - One active DEK per (tenant, purpose); older generations stay passive so
--     what they encrypted can still be opened.
--   - linked_external_accounts.encryption_key_id and
--     account_linking_sessions.encryption_key_id hold the DEK id.
-- ============================================================================

CREATE TABLE tenant_data_keys
(
    id          UUID         NOT NULL,
    tenant_id   UUID         NOT NULL,
    purpose     VARCHAR(64)  NOT NULL,
    version     INTEGER      NOT NULL,
    status      VARCHAR(16)  NOT NULL,
    wrapped_key JSONB        NOT NULL,
    kek_id      VARCHAR(64)  NOT NULL,
    created_at  TIMESTAMP DEFAULT now() NOT NULL,
    updated_at  TIMESTAMP DEFAULT now() NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE,
    UNIQUE (tenant_id, purpose, version)
);

ALTER TABLE tenant_data_keys ENABLE ROW LEVEL SECURITY;
CREATE
POLICY tenant_isolation_policy
  ON tenant_data_keys
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
ALTER TABLE tenant_data_keys FORCE ROW LEVEL SECURITY;

-- At most one active key per tenant and purpose. Concurrent first encryptions race to create it;
-- the loser's insert is a no-op and it reads the winner's key.
CREATE UNIQUE INDEX uk_tenant_data_keys_active
    ON tenant_data_keys (tenant_id, purpose)
    WHERE status = 'active';

-- How many data keys each KEK still wraps: zero means the KEK can be removed.
CREATE INDEX idx_tenant_data_keys_kek_id
    ON tenant_data_keys (kek_id);

-- Whether any token is still under a data key: none means the key can be destroyed.
CREATE INDEX idx_linked_external_accounts_encryption_key_id
    ON linked_external_accounts (encryption_key_id);

ALTER TABLE linked_external_accounts ALTER COLUMN encryption_key_id DROP DEFAULT;

COMMENT ON TABLE tenant_data_keys IS
'Data keys (DEKs) per tenant and purpose, each stored wrapped by a key encryption key (KEK).

The plain DEK exists only in application memory. wrapped_key is authenticated with
tenant_id, purpose and id, so a wrapped key copied to another row does not open.';

COMMENT ON COLUMN tenant_data_keys.status IS
'active: encrypts new data. passive: only decrypts what it encrypted earlier.
destroyed: no longer opens anything; wrapped_key is cleared.';

COMMENT ON COLUMN tenant_data_keys.kek_id IS
'Key id of the KEK that wrapped this DEK. Rewritten when the KEK is rotated; the DEK itself,
and every token it encrypted, stays as it is.';

COMMENT ON COLUMN linked_external_accounts.encryption_key_id IS
'Id of the tenant data key (tenant_data_keys.id) the token columns are encrypted under.';
