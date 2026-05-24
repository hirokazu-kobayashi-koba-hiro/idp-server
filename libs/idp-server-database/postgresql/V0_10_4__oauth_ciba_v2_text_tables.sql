/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

-- =====================================================
-- Create v2 tables for OAuth/CIBA short-lived snapshot tables
--
-- Strategy: new-table swap (zero-downtime friendly)
--   - JSONB columns written/read as full snapshots become TEXT
--     (no -> / ->> / @> / GIN usage on any of them)
--   - Application writes new records into *_v2 tables;
--     legacy rows in v1 expire naturally (lifetime 10min ~ several hours)
--
-- Removes per-INSERT jsonb_in parsing cost on hot OAuth/CIBA write paths.
-- =====================================================

-- ---------- authorization_request_v2 ----------
CREATE TABLE authorization_request_v2
(
    id                    UUID                    NOT NULL,
    tenant_id             UUID                    NOT NULL,
    profile               VARCHAR(255)            NOT NULL,
    scopes                TEXT                    NOT NULL,
    response_type         VARCHAR(255)            NOT NULL,
    client_id             VARCHAR(255)            NOT NULL,
    client_payload        TEXT                    NOT NULL,
    redirect_uri          TEXT                    NOT NULL,
    state                 TEXT,
    response_mode         VARCHAR(255),
    nonce                 TEXT,
    display               VARCHAR(255),
    prompts               VARCHAR(255),
    max_age               VARCHAR(255),
    ui_locales            TEXT,
    id_token_hint         TEXT,
    login_hint            TEXT,
    acr_values            TEXT,
    claims_value          TEXT,
    request_object        TEXT,
    request_uri           TEXT,
    code_challenge        TEXT,
    code_challenge_method VARCHAR(20),
    authorization_details TEXT,
    custom_params         TEXT                    NOT NULL,
    expires_in            TEXT                    NOT NULL,
    expires_at            TIMESTAMP               NOT NULL,
    created_at            TIMESTAMP DEFAULT now() NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
);

ALTER TABLE authorization_request_v2 ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_policy
  ON authorization_request_v2
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
ALTER TABLE authorization_request_v2 FORCE ROW LEVEL SECURITY;

CREATE INDEX idx_authorization_request_v2_expires_at ON authorization_request_v2 (tenant_id, expires_at);


-- ---------- authorization_code_grant_v2 ----------
CREATE TABLE authorization_code_grant_v2
(
    authorization_request_id UUID                    NOT NULL,
    tenant_id                UUID                    NOT NULL,
    authorization_code       VARCHAR(255)            NOT NULL,
    user_id                  UUID                    NOT NULL,
    user_payload             TEXT                    NOT NULL,
    authentication           TEXT                    NOT NULL,
    client_id                VARCHAR(255)            NOT NULL,
    client_payload           TEXT                    NOT NULL,
    grant_type               VARCHAR(255)            NOT NULL,
    scopes                   TEXT                    NOT NULL,
    id_token_claims          TEXT                    NOT NULL,
    userinfo_claims          TEXT                    NOT NULL,
    custom_properties        TEXT,
    authorization_details    TEXT,
    expires_at               TIMESTAMP               NOT NULL,
    consent_claims           TEXT,
    created_at               TIMESTAMP DEFAULT now() NOT NULL,
    PRIMARY KEY (authorization_request_id),
    FOREIGN KEY (authorization_request_id) REFERENCES authorization_request_v2 (id) ON DELETE CASCADE,
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
);

ALTER TABLE authorization_code_grant_v2 ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_policy
  ON authorization_code_grant_v2
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
ALTER TABLE authorization_code_grant_v2 FORCE ROW LEVEL SECURITY;

CREATE INDEX idx_auth_code_grant_v2_code ON authorization_code_grant_v2 (authorization_code);
CREATE INDEX idx_auth_code_v2_expires_at ON authorization_code_grant_v2 (tenant_id, expires_at);


-- ---------- backchannel_authentication_request_v2 ----------
CREATE TABLE backchannel_authentication_request_v2
(
    id                        UUID                    NOT NULL,
    tenant_id                 UUID                    NOT NULL,
    profile                   VARCHAR(255)            NOT NULL,
    delivery_mode             VARCHAR(20)             NOT NULL,
    scopes                    TEXT                    NOT NULL,
    client_id                 VARCHAR(255)            NOT NULL,
    id_token_hint             TEXT,
    login_hint                TEXT,
    login_hint_token          TEXT,
    acr_values                TEXT,
    user_code                 TEXT,
    client_notification_token TEXT,
    binding_message           TEXT,
    requested_expiry          TEXT,
    request_object            TEXT,
    authorization_details     TEXT,
    expires_in                TEXT                    NOT NULL,
    expires_at                TIMESTAMP               NOT NULL,
    created_at                TIMESTAMP DEFAULT now() NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
);

ALTER TABLE backchannel_authentication_request_v2 ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_policy
  ON backchannel_authentication_request_v2
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
ALTER TABLE backchannel_authentication_request_v2 FORCE ROW LEVEL SECURITY;

CREATE INDEX idx_bc_auth_request_v2_expires_at ON backchannel_authentication_request_v2 (tenant_id, expires_at);


-- ---------- ciba_grant_v2 ----------
CREATE TABLE ciba_grant_v2
(
    backchannel_authentication_request_id UUID                    NOT NULL,
    tenant_id                             UUID                    NOT NULL,
    auth_req_id                           VARCHAR(255)            NOT NULL,
    expires_at                            TIMESTAMP               NOT NULL,
    polling_interval                      TEXT                    NOT NULL,
    status                                VARCHAR(32)             NOT NULL,
    user_id                               UUID                    NOT NULL,
    user_payload                          TEXT                    NOT NULL,
    authentication                        TEXT,
    client_id                             VARCHAR(255)            NOT NULL,
    client_payload                        TEXT                    NOT NULL,
    grant_type                            VARCHAR(255)            NOT NULL,
    scopes                                TEXT                    NOT NULL,
    id_token_claims                       TEXT                    NOT NULL,
    userinfo_claims                       TEXT                    NOT NULL,
    custom_properties                     TEXT,
    authorization_details                 TEXT,
    consent_claims                        TEXT,
    created_at                            TIMESTAMP DEFAULT now() NOT NULL,
    PRIMARY KEY (backchannel_authentication_request_id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
);

ALTER TABLE ciba_grant_v2 ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_policy
  ON ciba_grant_v2
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
ALTER TABLE ciba_grant_v2 FORCE ROW LEVEL SECURITY;

CREATE INDEX idx_ciba_grant_v2_auth_req ON ciba_grant_v2 (auth_req_id);
CREATE INDEX idx_ciba_grant_v2_expires_at ON ciba_grant_v2 (tenant_id, expires_at);
