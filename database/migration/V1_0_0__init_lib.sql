CREATE TABLE server_configuration
(
    token_issuer TEXT                    NOT NULL PRIMARY KEY,
    payload      TEXT                    NOT NULL,
    created_at   TIMESTAMP DEFAULT now() NOT NULL,
    updated_at   TIMESTAMP DEFAULT now() NOT NULL
);

CREATE TABLE client_configuration
(
    token_issuer TEXT                    NOT NULL,
    client_id    VARCHAR(255)            NOT NULL,
    payload      TEXT                    NOT NULL,
    created_at   TIMESTAMP DEFAULT now() NOT NULL,
    updated_at   TIMESTAMP DEFAULT now() NOT NULL,
    CONSTRAINT pk_client_configuration PRIMARY KEY (token_issuer, client_id)
);

CREATE TABLE authorization_request
(
    id                          VARCHAR(255)            NOT NULL PRIMARY KEY,
    token_issuer                TEXT                    NOT NULL,
    profile                     VARCHAR(255)            NOT NULL,
    scopes                      TEXT                    NOT NULL,
    response_type               VARCHAR(255)            NOT NULL,
    client_id                   VARCHAR(255)            NOT NULL,
    redirect_uri                TEXT                    NOT NULL,
    state                       TEXT                    NOT NULL,
    response_mode               VARCHAR(255)            NOT NULL,
    nonce                       TEXT                    NOT NULL,
    display                     VARCHAR(255)            NOT NULL,
    prompts                     VARCHAR(255)            NOT NULL,
    max_age                     VARCHAR(255)            NOT NULL,
    ui_locales                  TEXT                    NOT NULL,
    id_token_hint               TEXT                    NOT NULL,
    login_hint                  TEXT                    NOT NULL,
    acr_values                  TEXT                    NOT NULL,
    claims_value                TEXT                    NOT NULL,
    request_object              TEXT                    NOT NULL,
    request_uri                 TEXT                    NOT NULL,
    code_challenge              TEXT                    NOT NULL,
    code_challenge_method       VARCHAR(10)             NOT NULL,
    authorization_details       TEXT                    NOT NULL,
    presentation_definition     TEXT                    NOT NULL,
    presentation_definition_uri TEXT                    NOT NULL,
    custom_params               TEXT                    NOT NULL,
    created_at                  TIMESTAMP DEFAULT now() NOT NULL
);

CREATE TABLE authorization_code_grant
(
    authorization_request_id VARCHAR(255)            NOT NULL PRIMARY KEY,
    authorization_code       VARCHAR(255)            NOT NULL,
    user_id                  VARCHAR(255)            NOT NULL,
    user_payload             TEXT                    NOT NULL,
    authentication           TEXT                    NOT NULL,
    client_id                VARCHAR(255)            NOT NULL,
    scopes                   TEXT                    NOT NULL,
    claims                   TEXT                    NOT NULL,
    custom_properties        TEXT                    NOT NULL,
    authorization_details    TEXT                    NOT NULL,
    expired_at               TEXT                    NOT NULL,
    presentation_definition  TEXT                    NOT NULL,
    created_at               TIMESTAMP DEFAULT now() NOT NULL,
    CONSTRAINT fk_authorization_code_grant_authorization_request_id
        FOREIGN KEY (authorization_request_id)
            REFERENCES authorization_request (id)
            ON DELETE CASCADE
);

CREATE TABLE oauth_token
(
    id                              VARCHAR(255)            NOT NULL PRIMARY KEY,
    token_issuer                    TEXT                    NOT NULL,
    token_type                      VARCHAR(10)             NOT NULL,
    access_token                    TEXT                    NOT NULL,
    user_id                         VARCHAR(255)            NOT NULL,
    user_payload                    TEXT                    NOT NULL,
    authentication                  TEXT                    NOT NULL,
    client_id                       VARCHAR(255)            NOT NULL,
    scopes                          TEXT                    NOT NULL,
    claims                          TEXT                    NOT NULL,
    custom_properties               TEXT                    NOT NULL,
    authorization_details           TEXT                    NOT NULL,
    expires_in                      TEXT                    NOT NULL,
    access_token_expired_at         TEXT                    NOT NULL,
    access_token_created_at         TEXT                    NOT NULL,
    refresh_token                   TEXT                    NOT NULL,
    refresh_token_expired_at        TEXT                    NOT NULL,
    refresh_token_created_at        TEXT                    NOT NULL,
    id_token                        TEXT                    NOT NULL,
    client_certification_thumbprint TEXT                    NOT NULL,
    c_nonce                         TEXT                    NOT NULL,
    c_nonce_expires_in              TEXT                    NOT NULL,
    created_at                      TIMESTAMP DEFAULT now() NOT NULL,
    updated_at                      TIMESTAMP DEFAULT now() NOT NULL
);

CREATE TABLE backchannel_authentication_request
(
    id                        VARCHAR(255)            NOT NULL PRIMARY KEY,
    token_issuer              TEXT                    NOT NULL,
    profile                   VARCHAR(255)            NOT NULL,
    delivery_mode             VARCHAR(10)             NOT NULL,
    scopes                    TEXT                    NOT NULL,
    client_id                 VARCHAR(255)            NOT NULL,
    id_token_hint             TEXT                    NOT NULL,
    login_hint                TEXT                    NOT NULL,
    login_hint_token          TEXT                    NOT NULL,
    acr_values                TEXT                    NOT NULL,
    user_code                 TEXT                    NOT NULL,
    client_notification_token TEXT                    NOT NULL,
    binding_message           TEXT                    NOT NULL,
    requested_expiry          TEXT                    NOT NULL,
    request_object            TEXT                    NOT NULL,
    authorization_details     TEXT                    NOT NULL,
    created_at                TIMESTAMP DEFAULT now() NOT NULL
);

CREATE TABLE ciba_grant
(
    backchannel_authentication_request_id VARCHAR(255)            NOT NULL PRIMARY KEY,
    auth_req_id                           VARCHAR(255)            NOT NULL,
    expired_at                            TEXT                    NOT NULL,
    interval                              TEXT                    NOT NULL,
    status                                VARCHAR(100)            NOT NULL,
    user_id                               VARCHAR(255)            NOT NULL,
    user_payload                          TEXT                    NOT NULL,
    authentication                        TEXT                    NOT NULL,
    client_id                             VARCHAR(255)            NOT NULL,
    scopes                                TEXT                    NOT NULL,
    claims                                TEXT                    NOT NULL,
    custom_properties                     TEXT                    NOT NULL,
    authorization_details                 TEXT                    NOT NULL,
    created_at                            TIMESTAMP DEFAULT now() NOT NULL,
    CONSTRAINT fk_ciba_grant_backchannel_authentication_request_id
        FOREIGN KEY (backchannel_authentication_request_id)
            REFERENCES backchannel_authentication_request (id)
            ON DELETE CASCADE
);

CREATE TABLE verifiable_credential_transaction
(
    transaction_id        VARCHAR(255)            NOT NULL,
    credential_issuer     TEXT                    NOT NULL,
    client_id             TEXT                    NOT NULL,
    user_id               VARCHAR(255)            NOT NULL,
    verifiable_credential TEXT                    NOT NULL,
    status                VARCHAR(10)             NOT NULL,
    created_at            TIMESTAMP DEFAULT now() NOT NULL,
    updated_at            TIMESTAMP DEFAULT now() NOT NULL
);

CREATE TABLE events
(
    id          char(36) PRIMARY KEY,
    type        VARCHAR(255) NOT NULL,
    description VARCHAR(255) NOT NULL,
    server_id   VARCHAR(255) NOT NULL,
    server_name VARCHAR(255) NOT NULL,
    client_id   VARCHAR(255) NOT NULL,
    client_name VARCHAR(255) NOT NULL,
    user_id     VARCHAR(255),
    user_name   VARCHAR(255),
    detail      JSONB        NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_events_type ON events (type);
CREATE INDEX idx_events_server ON events (server_id);
CREATE INDEX idx_events_client ON events (client_id);
CREATE INDEX idx_events_user ON events (user_id);
CREATE INDEX idx_events_created_at ON events (created_at);
CREATE INDEX idx_events_detail_jsonb ON events USING GIN (detail);

CREATE TABLE federatable_idp_configuration
(
    id         VARCHAR(255)            NOT NULL PRIMARY KEY,
    payload    JSONB                   NOT NULL,
    created_at TIMESTAMP DEFAULT now() NOT NULL,
    updated_at TIMESTAMP DEFAULT now() NOT NULL
);