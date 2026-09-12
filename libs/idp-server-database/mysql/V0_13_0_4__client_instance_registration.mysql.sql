-- ============================================================================
-- V0_13_0_4__client_instance_registration.mysql.sql
-- Client Instance registration challenge (refs #1521)
--
-- See the PostgreSQL migration for the design notes. MySQL has no Row Level
-- Security; tenant isolation relies on the tenant_id predicate of every query.
-- ============================================================================

CREATE TABLE client_instance_registration_challenge
(
    challenge   VARCHAR(255)                             NOT NULL,
    tenant_id   CHAR(36)                                 NOT NULL,
    client_id   VARCHAR(255)                             NOT NULL,
    device_id   CHAR(36),
    instance_id VARCHAR(255)                             NOT NULL,
    expires_at  DATETIME(6)                              NOT NULL,
    used_at     DATETIME(6),
    created_at  DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (tenant_id, challenge),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE,
    INDEX idx_client_instance_registration_challenge_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_client_instance_tenant_client_device
    ON client_instance (tenant_id, client_id, device_id);
