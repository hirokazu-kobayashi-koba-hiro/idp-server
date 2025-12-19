CREATE TABLE organization
(
    id          CHAR(36)                           NOT NULL,
    name        VARCHAR(255)                       NOT NULL,
    description TEXT,
    created_at  DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    updated_at  DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    enabled     BOOLEAN DEFAULT TRUE               NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tenant
(
    id                         CHAR(36)     NOT NULL,
    name                       VARCHAR(255) NOT NULL,
    type                       VARCHAR(20)  NOT NULL,
    domain                     TEXT         NOT NULL,
    authorization_provider     VARCHAR(255) NOT NULL,

    -- Configuration columns (category-based JSON)
    security_event_log_config  JSON, -- Security event logging configuration
    security_event_user_config JSON, -- Security event user attribute configuration
    identity_policy_config     JSON, -- Identity policy configuration
    ui_config                  JSON, -- UI/authorization page configuration
    cors_config                JSON, -- CORS configuration
    session_config             JSON, -- Cookie/session configuration

    attributes                 JSON,
    features                   JSON,
    main_organization_id CHAR(36)                    NOT NULL,

    created_at                 DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at                 DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    enabled                    BOOLEAN      NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    FOREIGN KEY (main_organization_id) REFERENCES organization (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- CREATE UNIQUE INDEX unique_admin_tenant ON tenant (type) WHERE type = 'ADMIN';

CREATE INDEX idx_tenant_enabled ON tenant (enabled);
CREATE INDEX idx_organization_enabled ON organization (enabled);

CREATE TABLE organization_tenants
(
    id              CHAR(36)                           NOT NULL,
    organization_id CHAR(36)                           NOT NULL,
    tenant_id       CHAR(36)                           NOT NULL,
    assigned_at     DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (organization_id, tenant_id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE,
    FOREIGN KEY (organization_id) REFERENCES organization (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE authorization_server_configuration
(
    tenant_id    CHAR(36)                           NOT NULL,
    token_issuer TEXT                               NOT NULL,
    payload      JSON                               NOT NULL,
    created_at   DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    updated_at   DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    enabled      BOOLEAN                            NOT NULL DEFAULT TRUE,
    PRIMARY KEY (tenant_id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_authorization_server_configuration_enabled ON authorization_server_configuration (tenant_id, enabled);

CREATE TABLE permission
(
    id          CHAR(36)                           NOT NULL,
    tenant_id   CHAR(36)                           NOT NULL,
    name        VARCHAR(255)                       NOT NULL,
    description TEXT,
    created_at  DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    updated_at  DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
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
    created_at  DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    updated_at  DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_tenant_role UNIQUE (tenant_id, name),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_role_tenant_name ON role (tenant_id, name);

CREATE TABLE role_permission
(
    id            CHAR(36)                           NOT NULL,
    tenant_id     CHAR(36)                           NOT NULL,
    role_id       CHAR(36)                           NOT NULL,
    permission_id CHAR(36)                           NOT NULL,
    created_at    DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (role_id, permission_id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE,
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
    external_user_id               VARCHAR(255),
    external_user_original_payload JSON,
    name                           VARCHAR(255),
    given_name                     VARCHAR(255),
    family_name                    VARCHAR(255),
    middle_name                    VARCHAR(255),
    nickname                       VARCHAR(255),
    preferred_username             VARCHAR(255)                       NOT NULL,
    profile                        TEXT,
    picture                        TEXT,
    website                        TEXT,
    email                          VARCHAR(255),
    email_verified                 TINYINT,
    gender                         VARCHAR(255),
    birthdate                      VARCHAR(255),
    zoneinfo                       VARCHAR(255),
    locale                         VARCHAR(255),
    phone_number                   VARCHAR(255),
    phone_number_verified          TINYINT,
    address                        JSON,
    custom_properties              JSON,
    credentials                    JSON,
    hashed_password                TEXT,
    authentication_devices         JSON,
    verified_claims                JSON,
    status                         VARCHAR(255)                       NOT NULL,
    created_at                     DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    updated_at                     DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    -- FK constraint removed for performance with billion-scale records (Issue #832)
    -- Application layer handles referential integrity via TenantDataCleanupService
    -- FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE,
    CONSTRAINT uk_tenant_provider_user UNIQUE (tenant_id, provider_id, external_user_id),
    -- Issue #729: Ensure uniqueness of preferred_username within tenant and provider
    -- Allow same preferred_username (e.g., user@example.com) across different IdPs
    CONSTRAINT uk_preferred_username UNIQUE (tenant_id, provider_id, preferred_username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_idp_user_tenant_provider ON idp_user (tenant_id, provider_id, external_user_id);
CREATE INDEX idx_idp_user_tenant_email ON idp_user (tenant_id, email);

ALTER TABLE idp_user MODIFY COLUMN preferred_username VARCHAR (255) NOT NULL
    COMMENT 'Tenant and provider-scoped unique user identifier. Stores normalized username/email/phone/external_user_id based on tenant unique key policy. Multiple IdPs can use the same preferred_username (e.g., user@example.com from Google and GitHub).';

CREATE TABLE idp_user_assigned_tenants
(
    id          CHAR(36)                           NOT NULL,
    tenant_id   CHAR(36)                           NOT NULL,
    user_id     CHAR(36)                           NOT NULL,
    assigned_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (tenant_id, user_id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES idp_user (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE idp_user_current_tenant
(
    user_id    CHAR(36)                           NOT NULL,
    tenant_id  CHAR(36)                           NOT NULL,
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (user_id),
    FOREIGN KEY (user_id) REFERENCES idp_user (id) ON DELETE CASCADE,
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE idp_user_assigned_organizations
(
    id              CHAR(36)                            NOT NULL,
    organization_id CHAR(36)                            NOT NULL,
    user_id         CHAR(36)                            NOT NULL,
    assigned_at     DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (organization_id) REFERENCES organization (id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES idp_user (id) ON DELETE CASCADE,
    UNIQUE (organization_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE idp_user_roles
(
    id          CHAR(36)                           NOT NULL,
    tenant_id   CHAR(36)                           NOT NULL,
    user_id     CHAR(36)                           NOT NULL,
    role_id     CHAR(36)                           NOT NULL,
    assigned_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (user_id, role_id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES idp_user (id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES role (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_idp_user_roles_user_role ON idp_user_roles (user_id, role_id);

CREATE VIEW user_effective_permissions_view AS
SELECT u.id          AS user_id,
       u.tenant_id   AS tenant_id,
       p.name        AS permission_name,
       p.description AS permission_description,
       rp.created_at AS granted_at
FROM idp_user u
         JOIN idp_user_roles ur ON u.id = ur.user_id
         JOIN role_permission rp ON ur.role_id = rp.role_id
         JOIN permission p ON rp.permission_id = p.id;

CREATE TABLE idp_user_current_organization
(
    user_id         CHAR(36)                           NOT NULL,
    organization_id CHAR(36)                           NOT NULL,
    created_at      DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    updated_at      DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (user_id),
    FOREIGN KEY (user_id) REFERENCES idp_user (id) ON DELETE CASCADE,
    FOREIGN KEY (organization_id) REFERENCES organization (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE client_configuration
(
    id         CHAR(36)                           NOT NULL,
    id_alias   VARCHAR(255),
    tenant_id  CHAR(36)                           NOT NULL,
    payload    JSON                               NOT NULL,
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    enabled    BOOLEAN                            NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    CONSTRAINT uk_client_configuration_alias unique (id_alias, tenant_id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_client_configuration_alias ON client_configuration (id_alias, tenant_id);
CREATE INDEX idx_client_configuration_enabled ON client_configuration (tenant_id, enabled);

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
    code_challenge_method VARCHAR(20),
    authorization_details JSON,
    custom_params         JSON                               NOT NULL,
    expires_in            TEXT                               NOT NULL,
    expires_at            DATETIME(6)                        NOT NULL,
    created_at            DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
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
    expires_at               DATETIME(6)                        NOT NULL,
    consent_claims           JSON,
    created_at               DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
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
    token_type                      VARCHAR(20)                        NOT NULL,
    encrypted_access_token          TEXT                               NOT NULL,
    hashed_access_token             VARCHAR(255)                       NOT NULL,
    access_token_custom_claims      JSON,
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
    access_token_expires_at         TEXT                               NOT NULL,
    access_token_created_at         TEXT                               NOT NULL,
    encrypted_refresh_token         TEXT,
    hashed_refresh_token            VARCHAR(255),
    refresh_token_expires_at        TEXT,
    refresh_token_created_at        TEXT,
    id_token                        TEXT                               NOT NULL,
    client_certification_thumbprint TEXT                               NOT NULL,
    c_nonce                         TEXT                               NOT NULL,
    c_nonce_expires_in              TEXT                               NOT NULL,
    consent_claims                  JSON,
    expires_at                      DATETIME(6)                        NOT NULL,
    created_at                      DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    updated_at                      DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id)
    -- FK constraint removed for performance with billion-scale records (Issue #832)
    -- Application layer handles referential integrity via TenantDataCleanupService
    -- FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_oauth_token_hashed_access_token ON oauth_token (tenant_id, hashed_access_token);
CREATE INDEX idx_oauth_token_hashed_refresh_token ON oauth_token (tenant_id, hashed_refresh_token);
CREATE INDEX idx_oauth_token_expires_at ON oauth_token (tenant_id, expires_at);

CREATE TABLE backchannel_authentication_request
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
    authorization_details     JSON,
    expires_in                TEXT                               NOT NULL,
    expires_at                DATETIME(6)                        NOT NULL,
    created_at                DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ciba_grant
(
    backchannel_authentication_request_id CHAR(36)                           NOT NULL,
    tenant_id                             CHAR(36)                           NOT NULL,
    auth_req_id                           VARCHAR(255)                       NOT NULL,
    expires_at                            DATETIME(6)                        NOT NULL,
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
    created_at                            DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (backchannel_authentication_request_id),
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
    created_at            DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    updated_at            DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    revoked_at            DATETIME(6),
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_authorization_granted_tenant_client_user ON authorization_granted (tenant_id, client_id, user_id);

CREATE TABLE security_event
(
    id               CHAR(36)     NOT NULL,
    type             VARCHAR(255) NOT NULL,
    description      VARCHAR(255) NOT NULL,
    tenant_id        CHAR(36)     NOT NULL,
    tenant_name      VARCHAR(255) NOT NULL,
    client_id        VARCHAR(255) NOT NULL,
    client_name      VARCHAR(255) NOT NULL,
    user_id          CHAR(36),
    external_user_id VARCHAR(255),
    user_name        VARCHAR(255),
    login_hint       VARCHAR(255),
    ip_address       VARCHAR(45),
    user_agent       TEXT,
    detail           JSON         NOT NULL,
    created_at       DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_events_type ON security_event (type);
CREATE INDEX idx_events_tenant ON security_event (tenant_id);
CREATE INDEX idx_events_client ON security_event (client_id);
CREATE INDEX idx_events_user ON security_event (user_id);
CREATE INDEX idx_events_external_user_id ON security_event (external_user_id);
CREATE INDEX idx_events_created_at ON security_event (created_at);
CREATE INDEX idx_events_tenant_created_at ON security_event (tenant_id, created_at);

CREATE TABLE security_event_hook_configurations
(
    id              CHAR(36)     NOT NULL,
    tenant_id       CHAR(36)     NOT NULL,
    type            VARCHAR(255) NOT NULL,
    payload         JSON         NOT NULL,
    execution_order INTEGER      NOT NULL DEFAULT 0,
    enabled         TINYINT      NOT NULL DEFAULT TRUE,
    created_at      DATETIME(6)           DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    updated_at      DATETIME(6)           DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_security_event_hook_configuration ON security_event_hook_configurations (tenant_id);

CREATE INDEX idx_security_event_hook_configuration_order ON security_event_hook_configurations (tenant_id, execution_order);

CREATE TABLE security_event_hook_results
(
    id                                    CHAR(36)                           NOT NULL,
    tenant_id                             CHAR(36)                           NOT NULL,
    security_event_id                     CHAR(36)                           NOT NULL,
    security_event_type                   VARCHAR(255)                       NOT NULL,
    security_event_hook                   VARCHAR(255)                       NOT NULL,
    security_event_payload                JSON                               NOT NULL,
    security_event_hook_execution_payload JSON,
    status                                VARCHAR(255)                       NOT NULL,
    created_at                            DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    updated_at                            DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE security_event_hook_results
    MODIFY COLUMN security_event_hook_execution_payload JSON COMMENT
    'Stores the execution result payload from security event hooks for resending and debugging purposes';

CREATE TABLE federation_configurations
(
    id           CHAR(36)                           NOT NULL,
    tenant_id    CHAR(36)                           NOT NULL,
    type         VARCHAR(255)                       NOT NULL,
    sso_provider VARCHAR(255)                       NOt NULL,
    payload      JSON                               NOT NULL,
    created_at   DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    updated_at   DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    enabled      BOOLEAN                            NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    CONSTRAINT uk_tenant_federation_configurations UNIQUE (tenant_id, type, sso_provider),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_federation_configurations_tenant ON federation_configurations (tenant_id);
CREATE INDEX idx_federation_configurations_enabled ON federation_configurations (tenant_id, enabled);
CREATE INDEX idx_federation_configurations_type_sso_provider ON federation_configurations (tenant_id, type, sso_provider);

CREATE TABLE federation_sso_session
(
    id         CHAR(36)                           NOT NULL,
    tenant_id  CHAR(36)                           NOT NULL,
    payload    JSON                               NOT NULL,
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE idp_user_sso_credentials
(
    user_id         CHAR(36)                NOT NULL,
    tenant_id       CHAR(36)                NOT NULL,
    sso_provider    VARCHAR(255)            NOT NULL,
    sso_credentials JSON                    NOT NULL,
    created_at      DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    updated_at      DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (user_id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE authentication_configuration
(
    id         CHAR(36)                           NOT NULL,
    tenant_id  CHAR(36)                           NOT NULL,
    type       VARCHAR(255)                       NOT NULL,
    payload    JSON                               NOT NULL,
    enabled    TINYINT                            NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE,
    UNIQUE (tenant_id, type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_authentication_configuration_type ON authentication_configuration (tenant_id, type);

CREATE TABLE authentication_policy
(
    id         CHAR(36)                           NOT NULL,
    tenant_id  CHAR(36)                           NOT NULL,
    flow       VARCHAR(255)                       NOT NULL,
    payload    JSON                               NOT NULL,
    enabled    TINYINT                            NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE,
    UNIQUE (tenant_id, flow)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE authentication_transaction
(
    id                            CHAR(36)               NOT NULL,
    tenant_id                     CHAR(36)               NOT NULL,
    tenant_payload                JSON                   NOT NULL,
    flow                          VARCHAR(255)           NOT NULL,
    authorization_id              CHAR(36),
    client_id                     VARCHAR(255)           NOT NULL,
    client_payload                JSON                   NOT NULL,
    user_id                       CHAR(36),
    user_payload                  JSON,
    context                       JSON,
    authentication_device_id      CHAR(36),
    authentication_device_payload JSON,
    authentication_policy         JSON,
    interactions                  JSON,
    attributes                    JSON,
    expires_at                    DATETIME(6)            NOT NULL,
    created_at                    DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    updated_at                    DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_authentication_transaction_device_id ON authentication_transaction (authentication_device_id);
CREATE INDEX idx_authentication_client_id ON authentication_transaction (client_id);
CREATE INDEX idx_authentication_tenant_id ON authentication_transaction (tenant_id);
CREATE INDEX idx_authentication_authorization_id ON authentication_transaction (authorization_id);
CREATE INDEX idx_authentication_flow ON authentication_transaction (flow);
CREATE INDEX idx_authentication_transaction_expires_at ON authentication_transaction (tenant_id, expires_at);

CREATE TABLE authentication_interactions
(
    authentication_transaction_id CHAR(36)                           NOT NULL,
    tenant_id                     CHAR(36)                           NOT NULL,
    interaction_type              VARCHAR(255)                       NOT NULL,
    payload                       JSON                               NOT NULL,
    created_at                    DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    updated_at                    DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (authentication_transaction_id, interaction_type),
    FOREIGN KEY (authentication_transaction_id) REFERENCES authentication_transaction (id) ON DELETE CASCADE,
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE identity_verification_configuration
(
    id         CHAR(36)                           NOT NULL,
    tenant_id  CHAR(36)                           NOT NULL,
    type       VARCHAR(255)                       NOT NULL,
    payload    JSON                               NOT NULL,
    enabled    TINYINT                            NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    updated_at DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_identity_verification_configuration_type
    ON identity_verification_configuration (tenant_id, type);

CREATE TABLE identity_verification_application
(
    id                  CHAR(36)                           NOT NULL,
    tenant_id           CHAR(36)                           NOT NULL,
    client_id           VARCHAR(255)                       NOT NULL,
    user_id             CHAR(36)                           NOT NULL,
    verification_type   VARCHAR(255)                       NOT NULL,
    application_details JSON                               NOT NULL,
    processes           JSON                               NOT NULL,
    status              VARCHAR(255)                       NOT NULL,
    requested_at        DATETIME(6)                        NOT NULL,
    attributes          JSON,
    created_at          DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    updated_at          DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_verification_user ON identity_verification_application (user_id);
CREATE INDEX idx_verification_tenant_client ON identity_verification_application (tenant_id, client_id);
CREATE INDEX idx_verification_status ON identity_verification_application (status);

CREATE TABLE identity_verification_result
(
    id                CHAR(36)     NOT NULL,
    tenant_id         CHAR(36)     NOT NULL,
    user_id           CHAR(36)     NOT NULL,
    application_id    CHAR(36),
    verification_type VARCHAR(255),
    verified_claims   JSON         NOT NULL,
    verified_at       DATETIME(6)  NOT NULL,
    valid_until       DATETIME(6),
    source            VARCHAR(255) NOT NULL DEFAULT 'application',
    source_details    JSON,
    attributes        JSON,
    created_at        DATETIME(6)           DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (application_id) REFERENCES identity_verification_application (id) ON DELETE CASCADE,
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_verification_result_user_id ON identity_verification_result (user_id);
CREATE INDEX idx_verification_result_application_id ON identity_verification_result (application_id);
CREATE INDEX idx_verification_result_verification_type ON identity_verification_result (verification_type);
CREATE INDEX idx_verification_result_verified_at ON identity_verification_result (verified_at);


CREATE TABLE idp_user_lifecycle_event_result
(
    id             CHAR(36)     NOT NULL,
    tenant_id      CHAR(36)     NOT NULL,
    user_id        CHAR(36)     NOT NULL,
    lifecycle_type VARCHAR(255) NOT NULL,
    executor_name  VARCHAR(255) NOT NULL,
    status         VARCHAR(32)  NOT NULL,
    payload        JSON,
    created_at     DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE audit_log
(
    id                     CHAR(36)     NOT NULL,
    type                   VARCHAR(255) NOT NULL,
    description            VARCHAR(255) NOT NULL,
    tenant_id              CHAR(36)     NOT NULL,
    client_id              VARCHAR(255) NOT NULL,
    user_id                CHAR(36)     NOT NULL,
    external_user_id       VARCHAR(255),
    user_payload           JSON         NOT NULL,
    target_tenant_id       VARCHAR(255),
    target_resource        TEXT         NOT NULL,
    target_resource_action TEXT         NOT NULL,
    request_payload        JSON,
    before_payload         JSON,
    after_payload          JSON,
    outcome_result         VARCHAR(20)  NOT NULL DEFAULT 'unknown',
    outcome_reason         VARCHAR(255),
    ip_address             TEXT,
    user_agent             TEXT,
    dry_run                BOOLEAN,
    attributes             JSON,
    created_at             DATETIME(6)           DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_audit_log_tenant_id ON audit_log (tenant_id);
CREATE INDEX idx_audit_log_client_id ON audit_log (client_id);
CREATE INDEX idx_audit_log_user_id ON audit_log (user_id);
CREATE INDEX idx_audit_log_external_user_id ON audit_log (external_user_id);
CREATE INDEX idx_audit_log_created_at ON audit_log (created_at);
CREATE INDEX idx_audit_log_tenant_created_at ON audit_log (tenant_id, created_at);
CREATE INDEX idx_audit_log_outcome ON audit_log (outcome_result);
CREATE INDEX idx_audit_log_type_created ON audit_log (type, created_at);
CREATE INDEX idx_audit_log_target_tenant ON audit_log (target_tenant_id, created_at);