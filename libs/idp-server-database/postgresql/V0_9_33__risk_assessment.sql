-- Risk Assessment: user known devices and configuration
-- Phase 1: New Device signal with device fingerprinting

-- User known devices table (upsert pattern, not history accumulation)
CREATE TABLE user_known_devices (
    tenant_id          UUID NOT NULL,
    user_id            UUID NOT NULL,
    device_fingerprint VARCHAR(64) NOT NULL,
    device_os          VARCHAR(100),
    device_browser     VARCHAR(100),
    device_platform    VARCHAR(50),
    ip_address         INET,
    latitude           DOUBLE PRECISION,
    longitude          DOUBLE PRECISION,
    country            VARCHAR(10),
    city               VARCHAR(255),
    login_count        INT DEFAULT 1,
    first_seen_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, user_id, device_fingerprint)
);

CREATE INDEX idx_known_devices_last_seen
    ON user_known_devices (tenant_id, user_id, last_seen_at DESC);

COMMENT ON TABLE user_known_devices IS
'Tracks known devices per user for risk-based authentication (Adaptive MFA).
Uses upsert pattern: one row per device, updated on each login.
Device fingerprint = SHA-256(os|browser|platform).';
