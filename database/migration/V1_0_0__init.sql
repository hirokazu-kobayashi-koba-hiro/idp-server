CREATE TABLE authorization_request
(
    id                    varchar(256)            NOT NULL PRIMARY KEY,
    token_issuer          text                    NOT NULL,
    profile               varchar(256)            NOT NULL,
    scopes                text                    NOT NULL,
    response_type         varchar(256)            NOT NULL,
    client_id             varchar(256)            NOT NULL,
    redirect_uri          text                    NOT NULL,
    state                 text                    NOT NULL,
    response_mode         varchar(256)            NOT NULL,
    nonce                 text                    NOT NULL,
    display               varchar(256)            NOT NULL,
    prompts               varchar(256)            NOT NULL,
    max_age               varchar(256)            NOT NULL,
    ui_locales            text                    NOT NULL,
    id_token_hint         text                    NOT NULL,
    login_hint            text                    NOT NULL,
    acr_values            text                    NOT NULL,
    claims_value          text                    NOT NULL,
    request_object        text                    NOT NULL,
    request_uri           text                    NOT NULL,
    code_challenge        text                    NOT NULL,
    code_challenge_method varchar(10)             NOT NULL,
    authorization_details text                    NOT NULL,
    created_at            timestamp default now() NOT NULL
);

CREATE TABLE authorization_code_grant
(
    authorization_request_id varchar(256)            NOT NULL PRIMARY KEY,
    authorization_code       varchar(256)            NOT NULL,
    user_id                  varchar(256)            NOT NULL,
    user_payload             text                    NOT NULL,
    authentication           text                    NOT NULL,
    client_id                varchar(256)            NOT NULL,
    scopes                   text                    NOT NULL,
    claims                   text                    NOT NULL,
    custom_properties        text                    NOT NULL,
    authorization_details    text                    NOT NULL,
    expired_at               text                    NOT NULL,
    created_at               timestamp default now() NOT NULL,
    CONSTRAINT fk_authorization_code_grant_authorization_request_id
        FOREIGN KEY (authorization_request_id)
            REFERENCES authorization_request (id)
            ON DELETE CASCADE
);

CREATE TABLE oauth_token
(
    id                       varchar(256)            NOT NULL PRIMARY KEY,
    token_issuer             text                    NOT NULL,
    token_type               varchar(10)             NOT NULL,
    access_token             text                    NOT NULL,
    user_id                  varchar(256)            NOT NULL,
    user_payload             text                    NOT NULL,
    authentication           text                    NOT NULL,
    client_id                varchar(256)            NOT NULL,
    scopes                   text                    NOT NULL,
    claims                   text                    NOT NULL,
    custom_properties        text                    NOT NULL,
    authorization_details    text                    NOT NULL,
    expires_in               text                    NOT NULL,
    access_token_expired_at  text                    NOT NULL,
    access_token_created_at  text                    NOT NULL,
    refresh_token            text                    NOT NULL,
    refresh_token_expired_at text                    NOT NULL,
    refresh_token_created_at text                    NOT NULL,
    id_token                 text                    NOT NULL,
    created_at               timestamp default now() NOT NULL,
    updated_at               timestamp default now() NOT NULL
);