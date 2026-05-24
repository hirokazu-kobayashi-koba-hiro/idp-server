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
-- Create v2 tables for OAuth/CIBA short-lived snapshot tables (MySQL parity)
--
-- JSON columns written/read as full snapshots become LONGTEXT.
-- Index set mirrors the v1 MySQL schema.
-- MySQL does not support RLS; tenant isolation is enforced in application layer.
-- =====================================================

-- ---------- authorization_request_v2 ----------
CREATE TABLE authorization_request_v2
(
    id                    CHAR(36)                           NOT NULL,
    tenant_id             CHAR(36)                           NOT NULL,
    profile               VARCHAR(255)                       NOT NULL,
    scopes                TEXT                               NOT NULL,
    response_type         VARCHAR(255)                       NOT NULL,
    client_id             VARCHAR(255)                       NOT NULL,
    client_payload        LONGTEXT                           NOT NULL,
    redirect_uri          TEXT                               NOT NULL,
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
    authorization_details LONGTEXT,
    custom_params         LONGTEXT                           NOT NULL,
    expires_in            TEXT                               NOT NULL,
    expires_at            DATETIME(6)                        NOT NULL,
    created_at            DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ---------- authorization_code_grant_v2 ----------
CREATE TABLE authorization_code_grant_v2
(
    authorization_request_id CHAR(36)                           NOT NULL,
    tenant_id                CHAR(36)                           NOT NULL,
    authorization_code       VARCHAR(255)                       NOT NULL,
    user_id                  CHAR(36)                           NOT NULL,
    user_payload             LONGTEXT                           NOT NULL,
    authentication           LONGTEXT                           NOT NULL,
    client_id                VARCHAR(255)                       NOT NULL,
    client_payload           LONGTEXT                           NOT NULL,
    grant_type               VARCHAR(255)                       NOT NULL,
    scopes                   TEXT                               NOT NULL,
    id_token_claims          TEXT                               NOT NULL,
    userinfo_claims          TEXT                               NOT NULL,
    custom_properties        LONGTEXT,
    authorization_details    LONGTEXT,
    expires_at               DATETIME(6)                        NOT NULL,
    consent_claims           LONGTEXT,
    created_at               DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (authorization_request_id),
    FOREIGN KEY (authorization_request_id) REFERENCES authorization_request_v2 (id) ON DELETE CASCADE,
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_auth_code_grant_v2_code ON authorization_code_grant_v2 (authorization_code);


-- ---------- backchannel_authentication_request_v2 ----------
CREATE TABLE backchannel_authentication_request_v2
(
    id                        CHAR(36)                           NOT NULL,
    tenant_id                 CHAR(36)                           NOT NULL,
    profile                   VARCHAR(255)                       NOT NULL,
    delivery_mode             VARCHAR(20)                        NOT NULL,
    scopes                    TEXT                               NOT NULL,
    client_id                 VARCHAR(255)                       NOT NULL,
    id_token_hint             TEXT,
    login_hint                TEXT,
    login_hint_token          TEXT,
    acr_values                TEXT,
    user_code                 TEXT,
    client_notification_token TEXT,
    binding_message           TEXT,
    requested_expiry          TEXT,
    request_object            TEXT,
    authorization_details     LONGTEXT,
    expires_in                TEXT                               NOT NULL,
    expires_at                DATETIME(6)                        NOT NULL,
    created_at                DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ---------- ciba_grant_v2 ----------
CREATE TABLE ciba_grant_v2
(
    backchannel_authentication_request_id CHAR(36)                           NOT NULL,
    tenant_id                             CHAR(36)                           NOT NULL,
    auth_req_id                           VARCHAR(255)                       NOT NULL,
    expires_at                            DATETIME(6)                        NOT NULL,
    polling_interval                      TEXT                               NOT NULL,
    status                                VARCHAR(100)                       NOT NULL,
    user_id                               CHAR(36)                           NOT NULL,
    user_payload                          LONGTEXT                           NOT NULL,
    authentication                        LONGTEXT,
    client_id                             VARCHAR(255)                       NOT NULL,
    client_payload                        LONGTEXT                           NOT NULL,
    grant_type                            VARCHAR(255)                       NOT NULL,
    scopes                                TEXT                               NOT NULL,
    id_token_claims                       TEXT                               NOT NULL,
    userinfo_claims                       TEXT                               NOT NULL,
    custom_properties                     LONGTEXT,
    authorization_details                 LONGTEXT,
    consent_claims                        LONGTEXT,
    created_at                            DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (backchannel_authentication_request_id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_ciba_grant_v2_auth_req ON ciba_grant_v2 (auth_req_id);
