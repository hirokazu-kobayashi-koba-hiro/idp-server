-- Risk Assessment: user known devices and configuration
-- Phase 1: New Device signal with device fingerprinting

-- User known devices table (upsert pattern, not history accumulation)
CREATE TABLE user_known_devices (
    tenant_id          CHAR(36) NOT NULL,
    user_id            CHAR(36) NOT NULL,
    device_fingerprint VARCHAR(64) NOT NULL,
    device_os          VARCHAR(100),
    device_browser     VARCHAR(100),
    device_platform    VARCHAR(50),
    ip_address         VARCHAR(45),
    latitude           DOUBLE,
    longitude          DOUBLE,
    country            VARCHAR(10),
    city               VARCHAR(255),
    login_count        INT DEFAULT 1,
    first_seen_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_seen_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (tenant_id, user_id, device_fingerprint),
    KEY idx_known_devices_last_seen (tenant_id, user_id, last_seen_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Risk assessment configuration per tenant (JSON payload)
CREATE TABLE risk_assessment_configuration (
    id          CHAR(36) NOT NULL,
    tenant_id   CHAR(36) NOT NULL,
    payload     JSON NOT NULL,
    enabled     TINYINT(1) NOT NULL DEFAULT 1,
    created_at  DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    updated_at  DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_risk_config_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
