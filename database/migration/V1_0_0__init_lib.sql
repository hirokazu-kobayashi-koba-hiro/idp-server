CREATE TABLE organization
(
    id          CHAR(36)                NOT NULL PRIMARY KEY,
    name        VARCHAR(255)            NOT NULL,
    description TEXT,
    created_at  TIMESTAMP DEFAULT now() NOT NULL,
    updated_at  TIMESTAMP DEFAULT now() NOT NULL
);

CREATE TABLE tenant
(
    id         CHAR(36)     NOT NULL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    type       VARCHAR(10)  NOT NULL,
    domain     TEXT         NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX unique_admin_tenant ON tenant (type) WHERE type = 'ADMIN';

CREATE TABLE organization_tenants
(
    id              CHAR(36) PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id CHAR(36) REFERENCES organization (id) ON DELETE CASCADE,
    tenant_id       CHAR(36) REFERENCES tenant (id) ON DELETE CASCADE,
    assigned_at     TIMESTAMP            DEFAULT now() NOT NULL,
    UNIQUE (organization_id, tenant_id)
);


CREATE TABLE server_configuration
(
    tenant_id    CHAR(36)                NOT NULL PRIMARY KEY REFERENCES tenant (id) ON DELETE CASCADE,
    token_issuer TEXT                    NOT NULL,
    payload      JSONB                   NOT NULL,
    created_at   TIMESTAMP DEFAULT now() NOT NULL,
    updated_at   TIMESTAMP DEFAULT now() NOT NULL
);

CREATE TABLE idp_user
(
    id                    CHAR(36)                NOT NULL PRIMARY KEY,
    tenant_id             CHAR(36)                NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    provider_id           VARCHAR(255)            NOT NULL,
    provider_user_id      VARCHAR(255)            NOT NULL,
    name                  VARCHAR(255),
    given_name            VARCHAR(255),
    family_name           VARCHAR(255),
    middle_name           VARCHAR(255),
    nickname              VARCHAR(255),
    preferred_username    VARCHAR(255),
    profile               VARCHAR(255),
    picture               VARCHAR(255),
    website               VARCHAR(255),
    email                 VARCHAR(255),
    email_verified        BOOLEAN,
    gender                VARCHAR(255),
    birthdate             VARCHAR(255),
    zoneinfo              VARCHAR(255),
    locale                VARCHAR(255),
    phone_number          VARCHAR(255),
    phone_number_verified BOOLEAN,
    address               JSONB,
    custom_properties     JSONB,
    credentials           JSONB,
    hashed_password       TEXT,
    created_at            TIMESTAMP DEFAULT now() NOT NULL,
    updated_at            TIMESTAMP DEFAULT now() NOT NULL,
    CONSTRAINT uk_tenant_provider_user unique (tenant_id, provider_user_id)
);

CREATE TABLE organization_members
(
    id              CHAR(36) PRIMARY KEY DEFAULT gen_random_uuid(),
    idp_user_id     VARCHAR(255) REFERENCES idp_user (id) ON DELETE CASCADE,
    organization_id VARCHAR(255) REFERENCES organization (id) ON DELETE CASCADE,
    role            VARCHAR(100)                       NOT NULL,
    joined_at       TIMESTAMP            DEFAULT now() NOT NULL,
    UNIQUE (idp_user_id, organization_id)
);

CREATE TABLE idp_user_current_organization
(
    idp_user_id     CHAR(36) REFERENCES idp_user (id) ON DELETE CASCADE PRIMARY KEY,
    organization_id CHAR(36) REFERENCES organization (id) ON DELETE CASCADE,
    created_at      TIMESTAMP DEFAULT now() NOT NULL,
    updated_at      TIMESTAMP DEFAULT now() NOT NULL
);

CREATE TABLE client_configuration
(
    id         CHAR(36)                NOT NULL PRIMARY KEY,
    id_alias   VARCHAR(255),
    tenant_id  CHAR(36)                NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    payload    JSONB                   NOT NULL,
    created_at TIMESTAMP DEFAULT now() NOT NULL,
    updated_at TIMESTAMP DEFAULT now() NOT NULL,
    CONSTRAINT uk_client_configuration_alias unique (id_alias, tenant_id)
);

CREATE INDEX idx_client_configuration_alias ON client_configuration (id_alias, tenant_id);

CREATE TABLE authorization_request
(
    id                          CHAR(36)                NOT NULL PRIMARY KEY,
    tenant_id                   CHAR(36)                NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    profile                     VARCHAR(255)            NOT NULL,
    scopes                      TEXT                    NOT NULL,
    response_type               VARCHAR(255)            NOT NULL,
    client_id                   VARCHAR(255)               NOT NULL,
    redirect_uri                TEXT                    NOT NULL,
    state                       TEXT,
    response_mode               VARCHAR(255),
    nonce                       TEXT,
    display                     VARCHAR(255),
    prompts                     VARCHAR(255),
    max_age                     VARCHAR(255),
    ui_locales                  TEXT,
    id_token_hint               TEXT,
    login_hint                  TEXT,
    acr_values                  TEXT,
    claims_value                TEXT,
    request_object              TEXT,
    request_uri                 TEXT,
    code_challenge              TEXT,
    code_challenge_method       VARCHAR(10),
    authorization_details       JSONB,
    presentation_definition     JSONB,
    presentation_definition_uri TEXT,
    custom_params               JSONB                   NOT NULL,
    created_at                  TIMESTAMP DEFAULT now() NOT NULL
);

CREATE TABLE authorization_code_grant
(
    authorization_request_id CHAR(36)                NOT NULL PRIMARY KEY,
    authorization_code       VARCHAR(255)            NOT NULL,
    user_id                  CHAR(36)                NOT NULL,
    user_payload             TEXT                    NOT NULL,
    authentication           TEXT                    NOT NULL,
    client_id                VARCHAR(255)                NOT NULL,
    scopes                   TEXT                    NOT NULL,
    claims                   TEXT                    NOT NULL,
    custom_properties        JSONB,
    authorization_details    JSONB,
    expired_at               TEXT                    NOT NULL,
    presentation_definition  JSONB                   NOT NULL,
    created_at               TIMESTAMP DEFAULT now() NOT NULL,
    CONSTRAINT fk_authorization_code_grant_authorization_request_id
        FOREIGN KEY (authorization_request_id)
            REFERENCES authorization_request (id)
            ON DELETE CASCADE
);

CREATE TABLE oauth_token
(
    id                              CHAR(36)                NOT NULL PRIMARY KEY,
    tenant_id                       CHAR(36)                NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    token_issuer                    TEXT                    NOT NULL,
    token_type                      VARCHAR(10)             NOT NULL,
    access_token                    TEXT                    NOT NULL,
    user_id                         VARCHAR(255),
    user_payload                    JSONB,
    authentication                  JSONB                   NOT NULL,
    client_id                       VARCHAR(255)            NOT NULL,
    scopes                          TEXT                    NOT NULL,
    claims                          TEXT                    NOT NULL,
    custom_properties               JSONB,
    authorization_details           JSONB,
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
    id                        CHAR(36)                NOT NULL PRIMARY KEY,
    tenant_id                 CHAR(36)                NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    profile                   VARCHAR(255)            NOT NULL,
    delivery_mode             VARCHAR(10)             NOT NULL,
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
    authorization_details     JSONB,
    created_at                TIMESTAMP DEFAULT now() NOT NULL
);

CREATE TABLE ciba_grant
(
    backchannel_authentication_request_id CHAR(36)                NOT NULL PRIMARY KEY,
    auth_req_id                           VARCHAR(255)            NOT NULL,
    expired_at                            TEXT                    NOT NULL,
    interval                              TEXT                    NOT NULL,
    status                                VARCHAR(100)            NOT NULL,
    user_id                               CHAR(36)                NOT NULL,
    user_payload                          JSONB                   NOT NULL,
    authentication                        JSONB,
    client_id                             VARCHAR(255)                NOT NULL,
    scopes                                TEXT                    NOT NULL,
    claims                                JSONB                   NOT NULL,
    custom_properties                     JSONB,
    authorization_details                 JSONB,
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
    client_id             VARCHAR(255)                NOT NULL,
    user_id               CHAR(36)                NOT NULL,
    verifiable_credential JSONB                   NOT NULL,
    status                VARCHAR(10)             NOT NULL,
    created_at            TIMESTAMP DEFAULT now() NOT NULL,
    updated_at            TIMESTAMP DEFAULT now() NOT NULL
);

CREATE TABLE shared_signal_framework_configuration
(
    id         CHAR(36)                NOT NULL PRIMARY KEY,
    payload    JSONB                   NOT NULL,
    created_at TIMESTAMP DEFAULT now() NOT NULL,
    updated_at TIMESTAMP DEFAULT now() NOT NULL
);

CREATE TABLE events
(
    id          CHAR(36) PRIMARY KEY,
    type        VARCHAR(255) NOT NULL,
    description VARCHAR(255) NOT NULL,
    tenant_id   CHAR(36)     NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    tenant_name VARCHAR(255) NOT NULL,
    client_id   VARCHAR(255)     NOT NULL,
    client_name VARCHAR(255) NOT NULL,
    user_id     CHAR(36),
    user_name   VARCHAR(255),
    detail      JSONB        NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_events_type ON events (type);
CREATE INDEX idx_events_tenant ON events (tenant_id);
CREATE INDEX idx_events_client ON events (client_id);
CREATE INDEX idx_events_user ON events (user_id);
CREATE INDEX idx_events_created_at ON events (created_at);
CREATE INDEX idx_events_detail_jsonb ON events USING GIN (detail);

CREATE TABLE federatable_idp_configuration
(
    id         CHAR(36)                NOT NULL PRIMARY KEY,
    payload    JSONB                   NOT NULL,
    created_at TIMESTAMP DEFAULT now() NOT NULL,
    updated_at TIMESTAMP DEFAULT now() NOT NULL
);
