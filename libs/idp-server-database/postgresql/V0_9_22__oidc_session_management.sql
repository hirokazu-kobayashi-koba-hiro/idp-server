-- OIDC Session Management Tables
-- For RP-Initiated Logout, Back-Channel Logout, Front-Channel Logout support

-- Logout Events audit log
CREATE TABLE IF NOT EXISTS logout_events (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    op_session_id VARCHAR(64),
    sub VARCHAR(255),
    initiator_client_id VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Indexes for logout_events
CREATE INDEX IF NOT EXISTS idx_logout_events_tenant_id ON logout_events(tenant_id);
CREATE INDEX IF NOT EXISTS idx_logout_events_created_at ON logout_events(created_at);
CREATE INDEX IF NOT EXISTS idx_logout_events_op_session_id ON logout_events(op_session_id);

-- Logout Notifications record
CREATE TABLE IF NOT EXISTS logout_notifications (
    id BIGSERIAL PRIMARY KEY,
    logout_event_id BIGINT NOT NULL REFERENCES logout_events(id),
    client_id VARCHAR(255) NOT NULL,
    sid VARCHAR(64),
    notification_type VARCHAR(20) NOT NULL,
    logout_token_jti VARCHAR(64),
    status VARCHAR(20) NOT NULL,
    http_status_code INTEGER,
    error_message TEXT,
    attempted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    completed_at TIMESTAMP WITH TIME ZONE
);

-- Indexes for logout_notifications
CREATE INDEX IF NOT EXISTS idx_logout_notifications_event_id ON logout_notifications(logout_event_id);
CREATE INDEX IF NOT EXISTS idx_logout_notifications_status ON logout_notifications(status)
    WHERE status IN ('pending', 'failed');
CREATE INDEX IF NOT EXISTS idx_logout_notifications_client_id ON logout_notifications(client_id);

-- Comments
COMMENT ON TABLE logout_events IS 'Audit log for logout events (RP-Initiated, Back-Channel, Admin, etc.)';
COMMENT ON TABLE logout_notifications IS 'Record of logout notifications sent to RPs';

COMMENT ON COLUMN logout_events.event_type IS 'Event type: rp_initiated, op_initiated, backchannel_received, timeout, admin_forced';
COMMENT ON COLUMN logout_notifications.notification_type IS 'Notification type: back_channel, front_channel';
COMMENT ON COLUMN logout_notifications.status IS 'Status: pending, success, failed, timeout';
