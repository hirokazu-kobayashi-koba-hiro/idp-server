-- ============================================================================
-- V0_15_0_2__tenant_data_keys.mysql.sql
-- Two-tier encryption keys for stored external tokens (Issue #1531)
--
-- See the PostgreSQL migration for the design.
--
-- Note: MySQL has no row level security. Tenant isolation relies on the
--       tenant_id predicate in every repository query. MySQL has no partial
--       index either: the one-active-key rule is a unique index over a
--       generated column that is NULL for every non-active key.
-- ============================================================================

CREATE TABLE tenant_data_keys
(
    id           CHAR(36)    NOT NULL,
    tenant_id    CHAR(36)    NOT NULL,
    purpose      VARCHAR(64) NOT NULL,
    version      INT         NOT NULL,
    status       VARCHAR(16) NOT NULL,
    wrapped_key  JSON        NOT NULL,
    kek_id       VARCHAR(64) NOT NULL,
    active_purpose VARCHAR(64) GENERATED ALWAYS AS (IF(status = 'active', purpose, NULL)) STORED,
    created_at   DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    updated_at   DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE,
    UNIQUE KEY uk_tenant_data_keys_version (tenant_id, purpose, version),
    UNIQUE KEY uk_tenant_data_keys_active (tenant_id, active_purpose),
    INDEX idx_tenant_data_keys_kek_id (kek_id)
);

CREATE INDEX idx_linked_external_accounts_encryption_key_id
    ON linked_external_accounts (encryption_key_id);

ALTER TABLE linked_external_accounts ALTER COLUMN encryption_key_id DROP DEFAULT;
