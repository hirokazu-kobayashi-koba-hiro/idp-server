CREATE TABLE server_configuration
(
    token_issuer text                    NOT NULL PRIMARY KEY,
    payload      text                    NOT NULL,
    created_at   timestamp default now() NOT NULL,
    updated_at   timestamp default now() NOT NULL
);

CREATE TABLE client_configuration
(
    token_issuer text                    NOT NULL,
    client_id    varchar(256)            NOT NULL,
    payload      text                    NOT NULL,
    created_at   timestamp default now() NOT NULL,
    updated_at   timestamp default now() NOT NULL,
    CONSTRAINT pk_client_configuration PRIMARY KEY (token_issuer, client_id)
);

CREATE TABLE user (
  id varchar(256) NOT NULL PRIMARY KEY,
  token_issuer text NOT NULL,
  name varchar(256) NOT NULL,
  given_name varchar(256) NOT NULL,
  family_name varchar(256) NOT NULL,
  middle_name varchar(256) NOT NULL,
  nickname varchar(256) NOT NULL,
  preferred_username varchar(256) NOT NULL,
  profile varchar(256) NOT NULL,
  picture varchar(256) NOT NULL,
  website varchar(256) NOT NULL,
  email varchar(256) NOT NULL,
  email_verified varchar(256) NOT NULL,
  gender varchar(256) NOT NULL,
  birthdate varchar(256) NOT NULL,
  zoneinfo varchar(256) NOT NULL,
  locale varchar(256) NOT NULL,
  phone_number varchar(256) NOT NULL,
  phone_number_verified varchar(256) NOT NULL,
  address text NOT NULL,
  custom_properties text NOT NULL,
  created_at timestamp default now() NOT NULL,
  updated_at timestamp default now() NOT NULL
);

CREATE TABLE user_credentials (
  user_id varchar(256) NOT NULL,
  created_at timestamp default now() NOT NULL,
  updated_at timestamp default now() NOT NULL
);

CREATE TABLE authorization_request
(
    id                          varchar(256)            NOT NULL PRIMARY KEY,
    token_issuer                text                    NOT NULL,
    profile                     varchar(256)            NOT NULL,
    scopes                      text                    NOT NULL,
    response_type               varchar(256)            NOT NULL,
    client_id                   varchar(256)            NOT NULL,
    redirect_uri                text                    NOT NULL,
    state                       text                    NOT NULL,
    response_mode               varchar(256)            NOT NULL,
    nonce                       text                    NOT NULL,
    display                     varchar(256)            NOT NULL,
    prompts                     varchar(256)            NOT NULL,
    max_age                     varchar(256)            NOT NULL,
    ui_locales                  text                    NOT NULL,
    id_token_hint               text                    NOT NULL,
    login_hint                  text                    NOT NULL,
    acr_values                  text                    NOT NULL,
    claims_value                text                    NOT NULL,
    request_object              text                    NOT NULL,
    request_uri                 text                    NOT NULL,
    code_challenge              text                    NOT NULL,
    code_challenge_method       varchar(10)             NOT NULL,
    authorization_details       text                    NOT NULL,
    presentation_definition     text                    NOT NULL,
    presentation_definition_uri text                    NOT NULL,
    created_at                  timestamp default now() NOT NULL
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
    presentation_definition  text                    NOT NULL,
    created_at               timestamp default now() NOT NULL,
    CONSTRAINT fk_authorization_code_grant_authorization_request_id
        FOREIGN KEY (authorization_request_id)
            REFERENCES authorization_request (id)
            ON DELETE CASCADE
);

CREATE TABLE oauth_token
(
    id                              varchar(256)            NOT NULL PRIMARY KEY,
    token_issuer                    text                    NOT NULL,
    token_type                      varchar(10)             NOT NULL,
    access_token                    text                    NOT NULL,
    user_id                         varchar(256)            NOT NULL,
    user_payload                    text                    NOT NULL,
    authentication                  text                    NOT NULL,
    client_id                       varchar(256)            NOT NULL,
    scopes                          text                    NOT NULL,
    claims                          text                    NOT NULL,
    custom_properties               text                    NOT NULL,
    authorization_details           text                    NOT NULL,
    expires_in                      text                    NOT NULL,
    access_token_expired_at         text                    NOT NULL,
    access_token_created_at         text                    NOT NULL,
    refresh_token                   text                    NOT NULL,
    refresh_token_expired_at        text                    NOT NULL,
    refresh_token_created_at        text                    NOT NULL,
    id_token                        text                    NOT NULL,
    client_certification_thumbprint text                    NOT NULL,
    c_nonce                         text                    NOT NULL,
    c_nonce_expires_in              text                    NOT NULL,
    created_at                      timestamp default now() NOT NULL,
    updated_at                      timestamp default now() NOT NULL
);

CREATE TABLE backchannel_authentication_request
(
    id                        varchar(256)            NOT NULL PRIMARY KEY,
    token_issuer              text                    NOT NULL,
    profile                   varchar(256)            NOT NULL,
    delivery_mode             varchar(10)             NOT NULL,
    scopes                    text                    NOT NULL,
    client_id                 varchar(256)            NOT NULL,
    id_token_hint             text                    NOT NULL,
    login_hint                text                    NOT NULL,
    login_hint_token          text                    NOT NULL,
    acr_values                text                    NOT NULL,
    user_code                 text                    NOT NULL,
    client_notification_token text                    NOT NULL,
    binding_message           text                    NOT NULL,
    requested_expiry          text                    NOT NULL,
    request_object            text                    NOT NULL,
    authorization_details     text                    NOT NULL,
    created_at                timestamp default now() NOT NULL
);

CREATE TABLE ciba_grant
(
    backchannel_authentication_request_id varchar(256)            NOT NULL PRIMARY KEY,
    auth_req_id                           varchar(256)            NOT NULL,
    expired_at                            text                    NOT NULL,
    interval                              text                    NOT NULL,
    status                                varchar(100)            NOT NULL,
    user_id                               varchar(256)            NOT NULL,
    user_payload                          text                    NOT NULL,
    authentication                        text                    NOT NULL,
    client_id                             varchar(256)            NOT NULL,
    scopes                                text                    NOT NULL,
    claims                                text                    NOT NULL,
    custom_properties                     text                    NOT NULL,
    authorization_details                 text                    NOT NULL,
    created_at                            timestamp default now() NOT NULL,
    CONSTRAINT fk_ciba_grant_backchannel_authentication_request_id
        FOREIGN KEY (backchannel_authentication_request_id)
            REFERENCES backchannel_authentication_request (id)
            ON DELETE CASCADE
);

CREATE TABLE verifiable_credential_transaction
(
    transaction_id                   varchar(256)            NOT NULL,
    credential_issuer                text                    NOT NULL,
    client_id                        text                    NOT NULL,
    user_id                          varchar(256)            NOT NULL,
    verifiable_credential           text                    NOT NULL,
    status                           varchar(10)             NOT NULL,
    created_at                       timestamp default now() NOT NULL,
    updated_at                       timestamp default now() NOT NULL
);