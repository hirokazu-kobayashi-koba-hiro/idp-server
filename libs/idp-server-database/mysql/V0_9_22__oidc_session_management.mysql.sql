-- OIDC Session Management Tables
-- For RP-Initiated Logout, Back-Channel Logout, Front-Channel Logout support

-- Logout Events audit log
CREATE TABLE IF NOT EXISTS logout_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    op_session_id VARCHAR(64),
    sub VARCHAR(255),
    initiator_client_id VARCHAR(255),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_logout_events_tenant_id (tenant_id),
    INDEX idx_logout_events_created_at (created_at),
    INDEX idx_logout_events_op_session_id (op_session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Logout Notifications record
CREATE TABLE IF NOT EXISTS logout_notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    logout_event_id BIGINT NOT NULL,
    client_id VARCHAR(255) NOT NULL,
    sid VARCHAR(64),
    notification_type VARCHAR(20) NOT NULL,
    logout_token_jti VARCHAR(64),
    status VARCHAR(20) NOT NULL,
    http_status_code INT,
    error_message TEXT,
    attempted_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at TIMESTAMP(6) NULL,
    INDEX idx_logout_notifications_event_id (logout_event_id),
    INDEX idx_logout_notifications_status (status),
    INDEX idx_logout_notifications_client_id (client_id),
    CONSTRAINT fk_logout_notifications_event_id FOREIGN KEY (logout_event_id) REFERENCES logout_events(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
