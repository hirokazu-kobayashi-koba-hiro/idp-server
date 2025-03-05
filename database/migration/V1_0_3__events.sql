CREATE TABLE events
(
    id          char(36) PRIMARY KEY,
    type        VARCHAR(256) NOT NULL,
    description VARCHAR(256) NOT NULL,
    server_id   VARCHAR(256) NOT NULL,
    server_name VARCHAR(256) NOT NULL,
    client_id   VARCHAR(256) NOT NULL,
    client_name   VARCHAR(256) NOT NULL,
    user_id     VARCHAR(256),
    user_name   VARCHAR(256),
    detail      JSONB        NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_events_type ON events (type);
CREATE INDEX idx_events_server ON events (server_id);
CREATE INDEX idx_events_client ON events (client_id);
CREATE INDEX idx_events_user ON events (user_id);
CREATE INDEX idx_events_created_at ON events (created_at);
CREATE INDEX idx_events_detail_jsonb ON events USING GIN (detail);

