CREATE TABLE organization
(
    id          CHAR(36)                           NOT NULL,
    name        VARCHAR(255)                       NOT NULL,
    description TEXT,
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tenant
(
    id                     CHAR(36)     NOT NULL,
    name                   VARCHAR(255) NOT NULL,
    type                   VARCHAR(10)  NOT NULL,
    domain                 TEXT         NOT NULL,
    authorization_provider VARCHAR(255) NOT NULL,
    database_type          VARCHAR(255) NOT NULL,
    attributes             JSON,
    features               JSON,
    created_at             DATETIME     NOT NULL DEFAULT now(),
    updated_at             DATETIME     NOT NULL DEFAULT now(),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- CREATE UNIQUE INDEX unique_admin_tenant ON tenant (type) WHERE type = 'ADMIN';

CREATE TABLE tenant_invitation
(
    id          CHAR(36)     NOT NULL,
    tenant_id   CHAR(36)     NOT NULL,
    tenant_name VARCHAR(255) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    role_id     CHAR(36)     NOT NULL,
    role_name   VARCHAR(255) NOT NULL,
    url         TEXT         NOT NULL,
    status      VARCHAR(255) NOT NULL,
    expires_in  TEXT         NOT NULL,
    created_at  TEXT         NOT NULL,
    expires_at  TEXT         NOT NULL,
    updated_at  TEXT         NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE organization_tenants
(
    id              CHAR(36)               NOT NULL,
    organization_id CHAR(36)               NOT NULL,
    tenant_id       CHAR(36)               NOT NULL,
    assigned_at     DATETIME DEFAULT now() NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (organization_id, tenant_id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE,
    FOREIGN KEY (organization_id) REFERENCES organization (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE idp_user_assigned_organizations
(
    id              CHAR(36)       DEFAULT gen_random_uuid(),
    organization_id CHAR(36)                     NOT NULL,
    user_id         CHAR(36)                     NOT NULL,
    assigned_at     TIMESTAMP DEFAULT now() NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (organization_id) REFERENCES organization (id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES idp_user (id) ON DELETE CASCADE,
    UNIQUE (organization_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE authorization_server_configuration
(
    tenant_id    CHAR(36)                           NOT NULL,
    token_issuer TEXT                               NOT NULL,
    payload      JSON                               NOT NULL,
    created_at   DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at   DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (tenant_id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE permission
(
    id          CHAR(36)                           NOT NULL,
    tenant_id   CHAR(36)                           NOT NULL,
    name        VARCHAR(255)                       NOT NULL UNIQUE,
    description TEXT,
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_tenant_permission UNIQUE (tenant_id, name),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE role
(
    id          CHAR(36)                           NOT NULL,
    tenant_id   CHAR(36)                           NOT NULL,
    name        VARCHAR(255)                       NOT NULL,
    description VARCHAR(255),
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_tenant_role UNIQUE (tenant_id, name),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_role_tenant_name ON role (tenant_id, name);

CREATE TABLE role_permission
(
    id            CHAR(36)               NOT NULL,
    role_id       CHAR(36)               NOT NULL,
    permission_id CHAR(36)               NOT NULL,
    created_at    DATETIME DEFAULT now() NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (role_id, permission_id),
    FOREIGN KEY (role_id) REFERENCES role (id) ON DELETE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES permission (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE VIEW role_permission_view AS
SELECT rp.id         AS id,
       t.name        AS tenant_name,
       r.name        AS role_name,
       p.name        AS permission_name,
       p.description AS permission_description,
       rp.created_at AS assigned_at
FROM role_permission rp
         JOIN role r ON rp.role_id = r.id
         JOIN permission p ON rp.permission_id = p.id
         JOIN tenant t ON r.tenant_id = t.id;

CREATE TABLE idp_user
(
    id                             CHAR(36)                           NOT NULL,
    tenant_id                      CHAR(36)                           NOT NULL,
    provider_id                    VARCHAR(255)                       NOT NULL,
    provider_user_id               VARCHAR(255)                       NOT NULL,
    provider_user_original_payload JSON,
    name                           VARCHAR(255),
    given_name                     VARCHAR(255),
    family_name                    VARCHAR(255),
    middle_name                    VARCHAR(255),
    nickname                       VARCHAR(255),
    preferred_username             VARCHAR(255),
    profile                        VARCHAR(255),
    picture                        VARCHAR(255),
    website                        VARCHAR(255),
    email                          VARCHAR(255),
    email_verified                 TINYINT(1),
    gender                         VARCHAR(255),
    birthdate                      VARCHAR(255),
    zoneinfo                       VARCHAR(255),
    locale                         VARCHAR(255),
    phone_number                   VARCHAR(255),
    phone_number_verified          TINYINT(1),
    address                        JSON,
    custom_properties              JSON,
    credentials                    JSON,
    hashed_password                TEXT,
    multi_factor_authentication    JSON,
    authentication_devices         JSON,
    verified_claims                JSON,
    status                         VARCHAR(255)                       NOT NULL,
    created_at                     DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at                     DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE,
    CONSTRAINT uk_tenant_provider_user unique (tenant_id, provider_id, provider_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_idp_user_tenant_provider ON idp_user (tenant_id, provider_id, provider_user_id);
CREATE INDEX idx_idp_user_tenant_email ON idp_user (tenant_id, email);

CREATE TABLE idp_user_roles
(
    id          CHAR(36)               NOT NULL,
    user_id     CHAR(36)               NOT NULL,
    role_id     CHAR(36)               NOT NULL,
    assigned_at DATETIME DEFAULT now() NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES idp_user (id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES role (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_idp_user_roles_user_role ON idp_user_roles (user_id, role_id);

CREATE TABLE idp_user_permission_override
(
    id            CHAR(36)               NOT NULL,
    user_id       CHAR(36)               NOT NULL,
    permission_id CHAR(36)               NOT NULL,
    granted       TINYINT(1)                            NOT NULL,
    created_at    DATETIME DEFAULT now() NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (user_id, permission_id),
    FOREIGN KEY (user_id) REFERENCES idp_user (id) ON DELETE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES permission (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE VIEW user_effective_permissions_view AS
SELECT u.id          AS user_id,
       u.tenant_id   AS tenant_id,
       p.name        AS permission_name,
       p.description AS permission_description,
       'ROLE'        AS source,
       rp.created_at AS granted_at
FROM idp_user u
         JOIN idp_user_roles ur ON u.id = ur.user_id
         JOIN role_permission rp ON ur.role_id = rp.role_id
         JOIN permission p ON rp.permission_id = p.id
         LEFT JOIN idp_user_permission_override ovr
                   ON u.id = ovr.user_id AND ovr.permission_id = p.id AND ovr.granted = false
WHERE ovr.id IS NULL

UNION

SELECT u.id           AS user_id,
       u.tenant_id    AS tenant_id,
       p.name         AS permission_name,
       p.description  AS permission_description,
       'OVERRIDE'     AS source,
       ovr.created_at AS granted_at
FROM idp_user_permission_override ovr
         JOIN idp_user u ON ovr.user_id = u.id
         JOIN permission p ON ovr.permission_id = p.id
WHERE ovr.granted = true;

CREATE TABLE organization_members
(
    id              CHAR(36)               NOT NULL,
    idp_user_id     CHAR(36)               NOT NULL,
    organization_id VARCHAR(255)           NOT NULL,
    role            VARCHAR(100)           NOT NULL,
    joined_at       DATETIME DEFAULT now() NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (idp_user_id, organization_id),
    FOREIGN KEY (idp_user_id) REFERENCES idp_user (id) ON DELETE CASCADE,
    FOREIGN KEY (organization_id) REFERENCES organization (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE idp_user_current_organization
(
    idp_user_id     CHAR(36)                           NOT NULL,
    organization_id CHAR(36)                           NOT NULL,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (idp_user_id),
    FOREIGN KEY (idp_user_id) REFERENCES idp_user (id) ON DELETE CASCADE,
    FOREIGN KEY (organization_id) REFERENCES organization (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE client_configuration
(
    id         CHAR(36)                           NOT NULL,
    id_alias   VARCHAR(255),
    tenant_id  CHAR(36)                           NOT NULL,
    payload    JSON                               NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_client_configuration_alias unique (id_alias, tenant_id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_client_configuration_alias ON client_configuration (id_alias, tenant_id);

CREATE TABLE authorization_request
(
    id                    CHAR(36)                           NOT NULL,
    tenant_id             CHAR(36)                           NOT NULL,
    profile               VARCHAR(255)                       NOT NULL,
    scopes                TEXT                               NOT NULL,
    response_type         VARCHAR(255)                       NOT NULL,
    client_id             VARCHAR(255)                       NOT NULL,
    client_payload        JSON                               NOT NULL,
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
    code_challenge_method VARCHAR(10),
    authorization_details JSON,
    custom_params         JSON                               NOT NULL,
    created_at            DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE authorization_code_grant
(
    authorization_request_id CHAR(36)                           NOT NULL,
    tenant_id                CHAR(36)                           NOT NULL,
    authorization_code       VARCHAR(255)                       NOT NULL,
    user_id                  CHAR(36)                           NOT NULL,
    user_payload             JSON                               NOT NULL,
    authentication           JSON                               NOT NULL,
    client_id                VARCHAR(255)                       NOT NULL,
    client_payload           JSON                               NOT NULL,
    grant_type               VARCHAR(255)                       NOT NULL,
    scopes                   TEXT                               NOT NULL,
    id_token_claims          TEXT                               NOT NULL,
    userinfo_claims          TEXT                               NOT NULL,
    custom_properties        JSON,
    authorization_details    JSON,
    expired_at               TEXT                               NOT NULL,
    consent_claims           JSON,
    created_at               DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (authorization_request_id),
    FOREIGN KEY (authorization_request_id) REFERENCES authorization_request (id) ON DELETE CASCADE,
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_auth_code_grant_code ON authorization_code_grant (authorization_code);

CREATE TABLE oauth_token
(
    id                              CHAR(36)                           NOT NULL,
    tenant_id                       CHAR(36)                           NOT NULL,
    token_issuer                    TEXT                               NOT NULL,
    token_type                      VARCHAR(10)                        NOT NULL,
    encrypted_access_token          TEXT                               NOT NULL,
    hashed_access_token             VARCHAR(255)                       NOT NULL,
    user_id                         CHAR(36),
    user_payload                    JSON,
    authentication                  JSON                               NOT NULL,
    client_id                       VARCHAR(255)                       NOT NULL,
    client_payload                  JSON                               NOT NULL,
    grant_type                      VARCHAR(255)                       NOT NULL,
    scopes                          TEXT                               NOT NULL,
    id_token_claims                 TEXT                               NOT NULL,
    userinfo_claims                 TEXT                               NOT NULL,
    custom_properties               JSON,
    authorization_details           JSON,
    expires_in                      TEXT                               NOT NULL,
    access_token_expired_at         TEXT                               NOT NULL,
    access_token_created_at         TEXT                               NOT NULL,
    encrypted_refresh_token         TEXT                               NOT NULL,
    hashed_refresh_token            VARCHAR(255)                       NOT NULL,
    refresh_token_expired_at        TEXT                               NOT NULL,
    refresh_token_created_at        TEXT                               NOT NULL,
    id_token                        TEXT                               NOT NULL,
    client_certification_thumbprint TEXT                               NOT NULL,
    c_nonce                         TEXT                               NOT NULL,
    c_nonce_expires_in              TEXT                               NOT NULL,
    consent_claims                  JSON,
    created_at                      DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at                      DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_oauth_token_hashed_access_token ON oauth_token (tenant_id, hashed_access_token);

CREATE INDEX idx_oauth_token_hashed_refresh_token ON oauth_token (tenant_id, hashed_refresh_token);

CREATE TABLE backchannel_authentication_request
(
    id                        CHAR(36)                           NOT NULL,
    tenant_id                 CHAR(36)                           NOT NULL,
    profile                   VARCHAR(255)                       NOT NULL,
    delivery_mode             VARCHAR(10)                        NOT NULL,
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
    authorization_details     JSON,
    created_at                DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ciba_grant
(
    backchannel_authentication_request_id CHAR(36)                           NOT NULL,
    tenant_id                             CHAR(36)                           NOT NULL,
    auth_req_id                           VARCHAR(255)                       NOT NULL,
    expired_at                            TEXT                               NOT NULL,
    polling_interval                      TEXT                               NOT NULL,
    status                                VARCHAR(100)                       NOT NULL,
    user_id                               CHAR(36)                           NOT NULL,
    user_payload                          JSON                               NOT NULL,
    authentication                        JSON,
    client_id                             VARCHAR(255)                       NOT NULL,
    client_payload                        JSON                               NOT NULL,
    grant_type                            VARCHAR(255)                       NOT NULL,
    scopes                                TEXT                               NOT NULL,
    id_token_claims                       TEXT                               NOT NULL,
    userinfo_claims                       TEXT                               NOT NULL,
    custom_properties                     JSON,
    authorization_details                 JSON,
    consent_claims                        JSON,
    created_at                            DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (backchannel_authentication_request_id),
    FOREIGN KEY (backchannel_authentication_request_id) REFERENCES backchannel_authentication_request (id) ON DELETE CASCADE,
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_ciba_grant_auth_req ON ciba_grant (auth_req_id);

CREATE TABLE authorization_granted
(
    id                    CHAR(36)                           NOT NULL,
    tenant_id             CHAR(36)                           NOT NULL,
    user_id               CHAR(36)                           NOT NULL,
    user_payload          JSON                               NOT NULL,
    authentication        JSON                               NOT NULL,
    client_id             VARCHAR(255)                       NOT NULL,
    client_payload        JSON                               NOT NULL,
    grant_type            VARCHAR(255)                       NOT NULL,
    scopes                TEXT                               NOT NULL,
    id_token_claims       TEXT                               NOT NULL,
    userinfo_claims       TEXT                               NOT NULL,
    custom_properties     JSON,
    authorization_details JSON,
    consent_claims        JSON,
    created_at            DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at            DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    revoked_at            DATETIME,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_authorization_granted_tenant_client_user ON authorization_granted (tenant_id, client_id, user_id);

CREATE TABLE verifiable_credential_transaction
(
    id                    VARCHAR(255)                       NOT NULL,
    tenant_id             CHAR(36)                           NOT NULL,
    credential_issuer     TEXT                               NOT NULL,
    client_id             VARCHAR(255)                       NOT NULL,
    user_id               CHAR(36)                           NOT NULL,
    verifiable_credential JSON                               NOT NULL,
    status                VARCHAR(10)                        NOT NULL,
    created_at            DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at            DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE security_event
(
    id          CHAR(36)     NOT NULL,
    type        VARCHAR(255) NOT NULL,
    description VARCHAR(255) NOT NULL,
    tenant_id   CHAR(36)     NOT NULL,
    tenant_name VARCHAR(255) NOT NULL,
    client_id   VARCHAR(255) NOT NULL,
    client_name VARCHAR(255) NOT NULL,
    user_id     CHAR(36),
    user_name   VARCHAR(255),
    login_hint  VARCHAR(255),
    ip_address  VARCHAR(45),
    user_agent  TEXT,
    detail      JSON         NOT NULL,
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_events_type ON security_event (type);

CREATE INDEX idx_events_tenant ON security_event (tenant_id);

CREATE INDEX idx_events_client ON security_event (client_id);

CREATE INDEX idx_events_user ON security_event (user_id);

CREATE INDEX idx_events_created_at ON security_event (created_at);


CREATE TABLE security_event_notifications
(
    id          CHAR(36)     NOT NULL,
    event_id    CHAR(36)     NOT NULL,
    alert_type  VARCHAR(100) NOT NULL,
    channel     VARCHAR(50)  NOT NULL,
    status      VARCHAR(50)  NOT NULL,
    notified_at DATETIME     NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_security_event_notifications_event_id ON security_event_notifications (event_id);

CREATE INDEX idx_security_event_notifications_alert_type ON security_event_notifications (alert_type);

CREATE TABLE security_event_hook_configuration
(
    id              CHAR(36)               NOT NULL,
    tenant_id       CHAR(36)               NOT NULL,
    payload         JSON                   NOT NULL,
    execution_order INTEGER                NOT NULL DEFAULT 0,
    enabled         TINYINT(1)  NOT NULL DEFAULT TRUE,
    created_at      DATETIME DEFAULT now() NOT NULL,
    updated_at      DATETIME DEFAULT now() NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_security_event_hook_configuration ON security_event_hook_configuration (tenant_id);

CREATE INDEX idx_security_event_hook_configuration_order ON security_event_hook_configuration (tenant_id, execution_order);

CREATE TABLE federation_configurations
(
    id           CHAR(36)                           NOT NULL,
    tenant_id    CHAR(36)                           NOT NULL,
    type         VARCHAR(255)                       NOT NULL,
    sso_provider VARCHAR(255)                       NOt NULL,
    payload      JSON                               NOT NULL,
    created_at   DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at   DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_tenant_federation_configurations UNIQUE (tenant_id, type, sso_provider),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_federation_configurations_tenant ON federation_configurations (tenant_id);

CREATE INDEX idx_federation_configurations_type_sso_provider ON federation_configurations (tenant_id, type, sso_provider);

CREATE TABLE federation_sso_session
(
    id         CHAR(36)                           NOT NULL,
    tenant_id  CHAR(36)                           NOT NULL,
    payload    JSON                               NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE authentication_configuration
(
    id         CHAR(36)                           NOT NULL,
    tenant_id  CHAR(36)                           NOT NULL,
    type       VARCHAR(255)                       NOT NULL,
    payload    JSON                               NOT NULL,
    enabled    TINYINT(1)                 NOT NULL DEFAULT TRUE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE,
    UNIQUE (tenant_id, type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_authentication_configuration_type ON authentication_configuration (tenant_id, type);

CREATE TABLE authentication_transaction
(
    authorization_id                     CHAR(36)     NOT NULL,
    tenant_id                            CHAR(36)     NOT NULL,
    authorization_flow                   VARCHAR(255) NOT NULL,
    client_id                            VARCHAR(255) NOT NULL,
    user_id                              CHAR(36),
    user_payload                         JSON,
    authentication_device_id             CHAR(36),
    available_authentication_types       JSON         NOT NULL,
    required_any_of_authentication_types JSON,
    last_interaction_type                VARCHAR(255),
    interactions                         JSON,
    created_at                           TEXT         NOT NULL,
    expired_at                           TEXT         NOT NULL,
    PRIMARY KEY (authorization_id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE authentication_interactions
(
    authorization_id CHAR(36)                           NOT NULL,
    tenant_id        CHAR(36)                           NOT NULL,
    interaction_type VARCHAR(255)                       NOT NULL,
    payload          JSON                               NOT NULL,
    created_at       DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at       DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (authorization_id, interaction_type),
    FOREIGN KEY (authorization_id) REFERENCES authentication_transaction (authorization_id) ON DELETE CASCADE,
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE idp_user_lifecycle_event_result
(
    id             CHAR(36)         NOT NULL,
    tenant_id      CHAR(36)          NOT NULL,
    user_id        CHAR(36)          NOT NULL,
    lifecycle_type VARCHAR(255)  NOT NULL,
    executor_name  VARCHAR(255) NOT NULL,
    status         VARCHAR(16)  NOT NULL,
    payload        JSON,
    created_at     TIMESTAMP DEFAULT now(),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
