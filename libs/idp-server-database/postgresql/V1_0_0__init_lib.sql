CREATE TABLE organization
(
    id          UUID                    NOT NULL,
    name        VARCHAR(255)            NOT NULL,
    description TEXT,
    created_at  TIMESTAMP DEFAULT now() NOT NULL,
    updated_at  TIMESTAMP DEFAULT now() NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE tenant
(
    id                     UUID         NOT NULL,
    name                   VARCHAR(255) NOT NULL,
    type                   VARCHAR(10)  NOT NULL,
    domain                 TEXT         NOT NULL,
    authorization_provider VARCHAR(255) NOT NULL,
    database_type          VARCHAR(255) NOT NULL,
    attributes             JSONB,
    features               JSONB,
    created_at             TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP    NOT NULL DEFAULT now(),
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX unique_admin_tenant ON tenant (type) WHERE type = 'ADMIN';

CREATE TABLE tenant_invitation
(
    id          UUID         NOT NULL,
    tenant_id   UUID         NOT NULL,
    tenant_name VARCHAR(255) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    role_id     UUID         NOT NULL,
    role_name   VARCHAR(255) NOT NULL,
    url         TEXT         NOT NULL,
    status      VARCHAR(255) NOT NULL,
    expires_in  TEXT         NOT NULL,
    created_at  TIMESTAMP    NOT NULL,
    expires_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
);

ALTER TABLE tenant_invitation ENABLE ROW LEVEL SECURITY;
CREATE
POLICY rls_tenant_invitation
  ON tenant_invitation
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
ALTER TABLE tenant_invitation FORCE ROW LEVEL SECURITY;

CREATE TABLE organization_tenants
(
    id              UUID      DEFAULT gen_random_uuid() NOT NULL,
    organization_id UUID                                NOT NULL,
    tenant_id       UUID                                NOT NULL,
    assigned_at     TIMESTAMP DEFAULT now()             NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (organization_id) REFERENCES organization (id) ON DELETE CASCADE,
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE,
    UNIQUE (organization_id, tenant_id)
);

ALTER TABLE organization_tenants ENABLE ROW LEVEL SECURITY;
CREATE
POLICY rls_organization_tenants
  ON organization_tenants
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
ALTER TABLE organization_tenants FORCE ROW LEVEL SECURITY;

CREATE TABLE authorization_server_configuration
(
    tenant_id    UUID                    NOT NULL,
    token_issuer TEXT                    NOT NULL,
    payload      JSONB                   NOT NULL,
    created_at   TIMESTAMP DEFAULT now() NOT NULL,
    updated_at   TIMESTAMP DEFAULT now() NOT NULL,
    PRIMARY KEY (tenant_id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
);

ALTER TABLE authorization_server_configuration ENABLE ROW LEVEL SECURITY;
CREATE
POLICY rls_authorization_server_configuration
  ON authorization_server_configuration
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
ALTER TABLE authorization_server_configuration FORCE ROW LEVEL SECURITY;

CREATE TABLE permission
(
    id          UUID                    NOT NULL,
    tenant_id   UUID                    NOT NULL,
    name        VARCHAR(255)            NOT NULL UNIQUE,
    description TEXT,
    created_at  TIMESTAMP DEFAULT now() NOT NULL,
    updated_at  TIMESTAMP DEFAULT now() NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE,
    CONSTRAINT uk_tenant_permission UNIQUE (tenant_id, name)
);

ALTER TABLE permission ENABLE ROW LEVEL SECURITY;
CREATE
POLICY rls_permission
  ON permission
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
ALTER TABLE permission FORCE ROW LEVEL SECURITY;


CREATE TABLE role
(
    id          UUID                    NOT NULL,
    tenant_id   UUID                    NOT NULL,
    name        VARCHAR(255)            NOT NULL,
    description VARCHAR(255),
    created_at  TIMESTAMP DEFAULT now() NOT NULL,
    updated_at  TIMESTAMP DEFAULT now() NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE,
    CONSTRAINT uk_tenant_role UNIQUE (tenant_id, name)
);

ALTER TABLE role ENABLE ROW LEVEL SECURITY;
CREATE
POLICY rls_role
  ON role
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
ALTER TABLE role FORCE ROW LEVEL SECURITY;

CREATE INDEX idx_role_tenant_name ON role (tenant_id, name);

CREATE TABLE role_permission
(
    id            UUID      DEFAULT gen_random_uuid(),
    tenant_id     UUID                    NOT NULL,
    role_id       UUID                    NOT NULL,
    permission_id UUID                    NOT NULL,
    created_at    TIMESTAMP DEFAULT now() NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES role (id) ON DELETE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES permission (id) ON DELETE CASCADE,
    UNIQUE (role_id, permission_id)
);

ALTER TABLE role_permission ENABLE ROW LEVEL SECURITY;
CREATE
POLICY rls_role_permission
  ON role_permission
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
ALTER TABLE role_permission FORCE ROW LEVEL SECURITY;

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
    id                             UUID                    NOT NULL,
    tenant_id                      UUID                    NOT NULL,
    provider_id                    VARCHAR(255)            NOT NULL,
    external_user_id               VARCHAR(255),
    external_user_original_payload JSONB,
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
    email_verified                 BOOLEAN,
    gender                         VARCHAR(255),
    birthdate                      VARCHAR(255),
    zoneinfo                       VARCHAR(255),
    locale                         VARCHAR(255),
    phone_number                   VARCHAR(255),
    phone_number_verified          BOOLEAN,
    address                        JSONB,
    custom_properties              JSONB,
    credentials                    JSONB,
    hashed_password                TEXT,
    multi_factor_authentication    JSONB,
    authentication_devices         JSONB,
    verified_claims                JSONB,
    status                         VARCHAR(255)            NOT NULL,
    created_at                     TIMESTAMP DEFAULT now() NOT NULL,
    updated_at                     TIMESTAMP DEFAULT now() NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE,
    CONSTRAINT uk_external_user unique (tenant_id, provider_id, external_user_id)
);

ALTER TABLE idp_user ENABLE ROW LEVEL SECURITY;
CREATE
POLICY rls_idp_user
  ON idp_user
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
ALTER TABLE idp_user FORCE ROW LEVEL SECURITY;

CREATE INDEX idx_idp_external_user_id ON idp_user (tenant_id, provider_id, external_user_id);
CREATE INDEX idx_idp_user_tenant_email ON idp_user (tenant_id, email);
CREATE INDEX idx_idp_user_tenant_phone ON idp_user (tenant_id, phone_number);
CREATE INDEX idx_user_devices_gin_path_ops
    ON idp_user USING GIN (authentication_devices jsonb_path_ops);

-- no rls
CREATE TABLE idp_user_assigned_tenants
(
    id          UUID      DEFAULT gen_random_uuid(),
    tenant_id   UUID                    NOT NULL,
    user_id     UUID                    NOT NULL,
    assigned_at TIMESTAMP DEFAULT now() NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES idp_user (id) ON DELETE CASCADE,
    UNIQUE (tenant_id, user_id)
);

-- no rls
CREATE TABLE idp_user_current_tenant
(
    user_id    UUID                                NOT NULL,
    tenant_id  UUID                                NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (user_id),
    FOREIGN KEY (user_id) REFERENCES idp_user (id) ON DELETE CASCADE,
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
);

-- no rls
CREATE TABLE idp_user_assigned_organizations
(
    id              UUID      DEFAULT gen_random_uuid(),
    user_id         UUID                    NOT NULL,
    organization_id UUID                    NOT NULL,
    assigned_at     TIMESTAMP DEFAULT now() NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (user_id, organization_id),
    FOREIGN KEY (user_id) REFERENCES idp_user (id) ON DELETE CASCADE,
    FOREIGN KEY (organization_id) REFERENCES organization (id) ON DELETE CASCADE
);

-- no rls
CREATE TABLE idp_user_current_organization
(
    user_id         UUID                                NOT NULL,
    organization_id UUID                                NOT NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (user_id),
    FOREIGN KEY (user_id) REFERENCES idp_user (id) ON DELETE CASCADE,
    FOREIGN KEY (organization_id) REFERENCES organization (id) ON DELETE CASCADE
);

CREATE INDEX idx_idp_user_assigned_tenants_user_id_tenant_id
    ON idp_user_assigned_tenants (user_id, tenant_id);

CREATE INDEX idx_idp_user_assigned_organizations_user_id_organization_id
    ON idp_user_assigned_organizations (user_id, organization_id);

CREATE TABLE idp_user_roles
(
    id          UUID      DEFAULT gen_random_uuid(),
    tenant_id   UUID                    NOT NULL,
    user_id     UUID                    NOT NULL,
    role_id     UUID                    NOT NULL,
    assigned_at TIMESTAMP DEFAULT now() NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES idp_user (id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES role (id) ON DELETE CASCADE,
    UNIQUE (user_id, role_id)
);

ALTER TABLE idp_user_roles ENABLE ROW LEVEL SECURITY;
CREATE
POLICY rls_idp_user_roles
  ON idp_user_roles
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
ALTER TABLE idp_user_roles FORCE ROW LEVEL SECURITY;

CREATE INDEX idx_idp_user_roles_user_role ON idp_user_roles (user_id, role_id);

CREATE TABLE idp_user_permission_override
(
    id            UUID      DEFAULT gen_random_uuid() NOT NULL,
    tenant_id     UUID                                NOT NULL,
    user_id       UUID                                NOT NULL,
    permission_id UUID                                NOT NULL,
    granted       BOOLEAN                             NOT NULL,
    created_at    TIMESTAMP DEFAULT now()             NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE,
    UNIQUE (user_id, permission_id),
    FOREIGN KEY (user_id) REFERENCES idp_user (id) ON DELETE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES permission (id) ON DELETE CASCADE
);

ALTER TABLE idp_user_permission_override ENABLE ROW LEVEL SECURITY;
CREATE
POLICY rls_idp_user_permission_override
  ON idp_user_permission_override
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
ALTER TABLE idp_user_permission_override FORCE ROW LEVEL SECURITY;

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

CREATE TABLE client_configuration
(
    id         UUID                    NOT NULL,
    id_alias   VARCHAR(255),
    tenant_id  UUID                    NOT NULL,
    payload    JSONB                   NOT NULL,
    created_at TIMESTAMP DEFAULT now() NOT NULL,
    updated_at TIMESTAMP DEFAULT now() NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE,
    CONSTRAINT uk_client_configuration_alias unique (id_alias, tenant_id)
);

ALTER TABLE client_configuration ENABLE ROW LEVEL SECURITY;
CREATE
POLICY rls_client_configuration
  ON client_configuration
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
ALTER TABLE client_configuration FORCE ROW LEVEL SECURITY;

CREATE INDEX idx_client_configuration_alias ON client_configuration (id_alias, tenant_id);

CREATE TABLE authorization_request
(
    id                    UUID                    NOT NULL,
    tenant_id             UUID                    NOT NULL,
    profile               VARCHAR(255)            NOT NULL,
    scopes                TEXT                    NOT NULL,
    response_type         VARCHAR(255)            NOT NULL,
    client_id             VARCHAR(255)            NOT NULL,
    client_payload        JSONB                   NOT NULL,
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
    code_challenge_method VARCHAR(10),
    authorization_details JSONB,
    custom_params         JSONB                   NOT NULL,
    expires_in            TEXT                    NOT NULL,
    expires_at            TIMESTAMP               NOT NULL,
    created_at            TIMESTAMP DEFAULT now() NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
);

ALTER TABLE authorization_request ENABLE ROW LEVEL SECURITY;
CREATE
POLICY rls_authorization_request
  ON authorization_request
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
ALTER TABLE authorization_request FORCE ROW LEVEL SECURITY;

CREATE INDEX idx_authorization_request_expires_at ON authorization_request (tenant_id, expires_at);

CREATE TABLE authorization_code_grant
(
    authorization_request_id UUID                    NOT NULL,
    tenant_id                UUID                    NOT NULL,
    authorization_code       VARCHAR(255)            NOT NULL,
    user_id                  UUID                    NOT NULL,
    user_payload             JSONB                   NOT NULL,
    authentication           JSONB                   NOT NULL,
    client_id                VARCHAR(255)            NOT NULL,
    client_payload           JSONB                   NOT NULL,
    grant_type               VARCHAR(255)            NOT NULL,
    scopes                   TEXT                    NOT NULL,
    id_token_claims          TEXT                    NOT NULL,
    userinfo_claims          TEXT                    NOT NULL,
    custom_properties        JSONB,
    authorization_details    JSONB,
    expires_at               TIMESTAMP               NOT NULL,
    consent_claims           JSONB,
    created_at               TIMESTAMP DEFAULT now() NOT NULL,
    PRIMARY KEY (authorization_request_id),
    FOREIGN KEY (authorization_request_id) REFERENCES authorization_request (id) ON DELETE CASCADE,
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
);

ALTER TABLE authorization_code_grant ENABLE ROW LEVEL SECURITY;
CREATE
POLICY rls_authorization_code_grant
  ON authorization_code_grant
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
ALTER TABLE authorization_code_grant FORCE ROW LEVEL SECURITY;

CREATE INDEX idx_auth_code_grant_code ON authorization_code_grant (authorization_code);
CREATE INDEX idx_auth_code_expires_at ON authorization_code_grant (tenant_id, expires_at);

CREATE TABLE oauth_token
(
    id                              UUID                    NOT NULL,
    tenant_id                       UUID                    NOT NULL,
    token_issuer                    TEXT                    NOT NULL,
    token_type                      VARCHAR(10)             NOT NULL,
    encrypted_access_token          TEXT                    NOT NULL,
    hashed_access_token             TEXT                    NOT NULL,
    user_id                         UUID,
    user_payload                    JSONB,
    authentication                  JSONB                   NOT NULL,
    client_id                       VARCHAR(255)            NOT NULL,
    client_payload                  JSONB                   NOT NULL,
    grant_type                      VARCHAR(255)            NOT NULL,
    scopes                          TEXT                    NOT NULL,
    id_token_claims                 TEXT                    NOT NULL,
    userinfo_claims                 TEXT                    NOT NULL,
    custom_properties               JSONB,
    authorization_details           JSONB,
    expires_in                      TEXT                    NOT NULL,
    access_token_expires_at         TIMESTAMP               NOT NULL,
    access_token_created_at         TIMESTAMP               NOT NULL,
    encrypted_refresh_token         TEXT,
    hashed_refresh_token            TEXT,
    refresh_token_expires_at        TIMESTAMP,
    refresh_token_created_at        TIMESTAMP,
    id_token                        TEXT                    NOT NULL,
    client_certification_thumbprint TEXT                    NOT NULL,
    c_nonce                         TEXT                    NOT NULL,
    c_nonce_expires_in              TEXT                    NOT NULL,
    consent_claims                  JSONB,
    expires_at                      TIMESTAMP               NOT NULL,
    created_at                      TIMESTAMP DEFAULT now() NOT NULL,
    updated_at                      TIMESTAMP DEFAULT now() NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
);

ALTER TABLE oauth_token ENABLE ROW LEVEL SECURITY;
CREATE
POLICY rls_oauth_token
  ON oauth_token
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
ALTER TABLE oauth_token FORCE ROW LEVEL SECURITY;

CREATE INDEX idx_oauth_token_hashed_access_token ON oauth_token (tenant_id, hashed_access_token);
CREATE INDEX idx_oauth_token_hashed_refresh_token ON oauth_token (tenant_id, hashed_refresh_token);
CREATE INDEX idx_oauth_token_expires_at ON oauth_token (tenant_id, expires_at);

CREATE TABLE backchannel_authentication_request
(
    id                        UUID                    NOT NULL,
    tenant_id                 UUID                    NOT NULL,
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
    expires_in                TEXT                    NOT NULL,
    expires_at                TIMESTAMP               NOT NULL,
    created_at                TIMESTAMP DEFAULT now() NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
);

ALTER TABLE backchannel_authentication_request ENABLE ROW LEVEL SECURITY;
CREATE
POLICY rls_backchannel_authentication_request
  ON backchannel_authentication_request
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
ALTER TABLE backchannel_authentication_request FORCE ROW LEVEL SECURITY;

CREATE INDEX idx_bc_auth_request_expires_at ON backchannel_authentication_request (tenant_id, expires_at);

CREATE TABLE ciba_grant
(
    backchannel_authentication_request_id UUID                    NOT NULL,
    tenant_id                             UUID                    NOT NULL,
    auth_req_id                           VARCHAR(255)            NOT NULL,
    expires_at                            TIMESTAMP               NOT NULL,
    polling_interval                      TEXT                    NOT NULL,
    status                                VARCHAR(100)            NOT NULL,
    user_id                               UUID                    NOT NULL,
    user_payload                          JSONB                   NOT NULL,
    authentication                        JSONB,
    client_id                             VARCHAR(255)            NOT NULL,
    client_payload                        JSONB                   NOT NULL,
    grant_type                            VARCHAR(255)            NOT NULL,
    scopes                                TEXT                    NOT NULL,
    id_token_claims                       TEXT                    NOT NULL,
    userinfo_claims                       TEXT                    NOT NULL,
    custom_properties                     JSONB,
    authorization_details                 JSONB,
    consent_claims                        JSONB,
    created_at                            TIMESTAMP DEFAULT now() NOT NULL,
    PRIMARY KEY (backchannel_authentication_request_id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
);

ALTER TABLE ciba_grant ENABLE ROW LEVEL SECURITY;
CREATE
POLICY rls_ciba_grant
  ON ciba_grant
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
ALTER TABLE ciba_grant FORCE ROW LEVEL SECURITY;

CREATE INDEX idx_ciba_grant_auth_req ON ciba_grant (auth_req_id);
CREATE INDEX idx_ciba_grant_expires_at ON ciba_grant (tenant_id, expires_at);

CREATE TABLE authorization_granted
(
    id                    UUID                    NOT NULL,
    tenant_id             UUID                    NOT NULL,
    user_id               UUID                    NOT NULL,
    user_payload          JSONB                   NOT NULL,
    authentication        JSONB                   NOT NULL,
    client_id             VARCHAR(255)            NOT NULL,
    client_payload        JSONB                   NOT NULL,
    grant_type            VARCHAR(255)            NOT NULL,
    scopes                TEXT                    NOT NULL,
    id_token_claims       TEXT                    NOT NULL,
    userinfo_claims       TEXT                    NOT NULL,
    custom_properties     JSONB,
    authorization_details JSONB,
    consent_claims        JSONB,
    created_at            TIMESTAMP DEFAULT now() NOT NULL,
    updated_at            TIMESTAMP DEFAULT now() NOT NULL,
    revoked_at            TIMESTAMP,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
);

ALTER TABLE authorization_granted ENABLE ROW LEVEL SECURITY;
CREATE
POLICY rls_authorization_granted
  ON authorization_granted
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
ALTER TABLE authorization_granted FORCE ROW LEVEL SECURITY;

CREATE INDEX idx_authorization_granted_tenant_client_user ON authorization_granted (tenant_id, client_id, user_id);

CREATE TABLE security_event
(
    id               UUID,
    type             VARCHAR(255) NOT NULL,
    description      VARCHAR(255) NOT NULL,
    tenant_id        UUID         NOT NULL,
    tenant_name      VARCHAR(255) NOT NULL,
    client_id        VARCHAR(255) NOT NULL,
    client_name      VARCHAR(255) NOT NULL,
    user_id          UUID,
    user_name        VARCHAR(255),
    external_user_id VARCHAR(255),
    ip_address       INET,
    user_agent       TEXT,
    detail           JSONB        NOT NULL,
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

ALTER TABLE security_event ENABLE ROW LEVEL SECURITY;
CREATE
POLICY rls_security_event
  ON security_event
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
ALTER TABLE security_event FORCE ROW LEVEL SECURITY;

CREATE INDEX idx_events_type ON security_event (type);
CREATE INDEX idx_events_tenant ON security_event (tenant_id);
CREATE INDEX idx_events_client ON security_event (client_id);
CREATE INDEX idx_events_user ON security_event (user_id);
CREATE INDEX idx_events_external_user_id ON security_event (external_user_id);
CREATE INDEX idx_events_created_at ON security_event (created_at);
CREATE INDEX idx_events_detail_jsonb ON security_event USING GIN (detail);
CREATE INDEX idx_events_tenant_created_at ON security_event (tenant_id, created_at);

CREATE TABLE security_event_hook_configurations
(
    id              UUID         NOT NULL,
    tenant_id       UUID         NOT NULL,
    type            VARCHAR(255) NOT NULL,
    payload         JSONB        NOT NULL,
    execution_order INTEGER      NOT NULL DEFAULT 0,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP             DEFAULT now() NOT NULL,
    updated_at      TIMESTAMP             DEFAULT now() NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
);

ALTER TABLE security_event_hook_configurations ENABLE ROW LEVEL SECURITY;
CREATE
POLICY rls_security_event_hook_configurations
  ON security_event_hook_configurations
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
ALTER TABLE security_event_hook_configurations FORCE ROW LEVEL SECURITY;

CREATE INDEX idx_security_event_hook_configurations ON security_event_hook_configurations (tenant_id);
CREATE INDEX idx_security_event_hook_configurations_order ON security_event_hook_configurations (tenant_id, execution_order);

CREATE TABLE security_event_hook_results
(
    id                     UUID                    NOT NULL,
    tenant_id              UUID                    NOT NULL,
    security_event_id      UUID                    NOT NULL,
    security_event_type    VARCHAR(255)            NOT NULL,
    security_event_hook    VARCHAR(255)            NOT NULL,
    security_event_payload JSONB                   NOT NULL,
    status                 VARCHAR(255)            NOT NULL,
    created_at             TIMESTAMP DEFAULT now() NOT NULL,
    updated_at             TIMESTAMP DEFAULT now() NOT NULL,
    PRIMARY KEY (id)
);

ALTER TABLE security_event_hook_results ENABLE ROW LEVEL SECURITY;
CREATE
POLICY rls_security_event_hook_results
  ON security_event_hook_results
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
ALTER TABLE security_event_hook_results FORCE ROW LEVEL SECURITY;

CREATE TABLE federation_configurations
(
    id           UUID                    NOT NULL,
    tenant_id    UUID                    NOT NULL,
    type         VARCHAR(255)            NOT NULL,
    sso_provider VARCHAR(255)            NOt NULL,
    payload      JSONB                   NOT NULL,
    created_at   TIMESTAMP DEFAULT now() NOT NULL,
    updated_at   TIMESTAMP DEFAULT now() NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_tenant_federation_configurations UNIQUE (tenant_id, type, sso_provider),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
);

ALTER TABLE federation_configurations ENABLE ROW LEVEL SECURITY;
CREATE
POLICY rls_federation_configurations
  ON federation_configurations
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
ALTER TABLE federation_configurations FORCE ROW LEVEL SECURITY;

CREATE INDEX idx_federation_configurations_tenant ON federation_configurations (tenant_id);
CREATE INDEX idx_federation_configurations_type_sso_provider ON federation_configurations (tenant_id, type, sso_provider);

CREATE TABLE federation_sso_session
(
    id         UUID                    NOT NULL,
    tenant_id  UUID                    NOT NULL,
    payload    JSONB                   NOT NULL,
    created_at TIMESTAMP DEFAULT now() NOT NULL,
    updated_at TIMESTAMP DEFAULT now() NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
);

ALTER TABLE federation_sso_session ENABLE ROW LEVEL SECURITY;
CREATE
POLICY rls_federation_sso_session
  ON federation_sso_session
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
ALTER TABLE federation_sso_session FORCE ROW LEVEL SECURITY;

CREATE TABLE authentication_configuration
(
    id         UUID                    NOT NULL,
    tenant_id  UUID                    NOT NULL,
    type       VARCHAR(255)            NOT NULL,
    payload    JSONB                   NOT NULL,
    enabled    BOOLEAN                 NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT now() NOT NULL,
    updated_at TIMESTAMP DEFAULT now() NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE,
    UNIQUE (tenant_id, type)
);

ALTER TABLE authentication_configuration ENABLE ROW LEVEL SECURITY;
CREATE
POLICY rls_authentication_configuration
  ON authentication_configuration
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
ALTER TABLE authentication_configuration FORCE ROW LEVEL SECURITY;

CREATE INDEX idx_authentication_configuration_type ON authentication_configuration (tenant_id, type);

CREATE TABLE authentication_transaction
(
    id                            UUID                    NOT NULL,
    tenant_id                     UUID                    NOT NULL,
    tenant_payload                JSONB                   NOT NULL,
    flow                          VARCHAR(255)            NOT NULL,
    authorization_id              UUID,
    client_id                     VARCHAR(255)            NOT NULL,
    client_payload                JSONB                   NOT NULL,
    user_id                       UUID,
    user_payload                  JSONB,
    context                       JSONB,
    authentication_device_id      UUID,
    authentication_device_payload JSONB,
    authentication_policy         JSONB,
    interactions                  JSONB,
    attributes                    JSONB,
    expires_at                    TIMESTAMP               NOT NULL,
    created_at                    TIMESTAMP DEFAULT now() NOT NULL,
    updated_at                    TIMESTAMP DEFAULT now() NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
);

ALTER TABLE authentication_transaction ENABLE ROW LEVEL SECURITY;
CREATE
POLICY rls_authentication_transaction
  ON authentication_transaction
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
ALTER TABLE authentication_transaction FORCE ROW LEVEL SECURITY;

CREATE INDEX idx_authentication_transaction_device_id ON authentication_transaction (authentication_device_id);
CREATE INDEX idx_authentication_client_id ON authentication_transaction (client_id);
CREATE INDEX idx_authentication_tenant_id ON authentication_transaction (tenant_id);
CREATE INDEX idx_authentication_authorization_id ON authentication_transaction (authorization_id);
CREATE INDEX idx_authentication_flow ON authentication_transaction (flow);
CREATE INDEX idx_authentication_transaction_expires_at ON authentication_transaction (tenant_id, expires_at);
CREATE INDEX idx_authentication_transaction_attributes ON authentication_transaction USING GIN (attributes);

CREATE TABLE authentication_interactions
(
    authentication_transaction_id UUID                    NOT NULL,
    tenant_id                     UUID                    NOT NULL,
    interaction_type              VARCHAR(255)            NOT NULL,
    payload                       JSONB                   NOT NULL,
    created_at                    TIMESTAMP DEFAULT now() NOT NULL,
    updated_at                    TIMESTAMP DEFAULT now() NOT NULL,
    PRIMARY KEY (authentication_transaction_id, interaction_type),
    FOREIGN KEY (authentication_transaction_id) REFERENCES authentication_transaction (id) ON DELETE CASCADE,
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
);

ALTER TABLE authentication_interactions ENABLE ROW LEVEL SECURITY;
CREATE
POLICY rls_authentication_interactions
  ON authentication_interactions
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
ALTER TABLE authentication_interactions FORCE ROW LEVEL SECURITY;

CREATE TABLE identity_verification_configuration
(
    id         UUID                    NOT NULL,
    tenant_id  UUID                    NOT NULL,
    type       VARCHAR(255)            NOT NULL,
    payload    JSONB                   NOT NULL,
    enabled    BOOLEAN                 NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT now() NOT NULL,
    updated_at TIMESTAMP DEFAULT now() NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE,
    UNIQUE (tenant_id, type)
);

ALTER TABLE identity_verification_configuration ENABLE ROW LEVEL SECURITY;
CREATE
POLICY rls_identity_verification_configuration
  ON identity_verification_configuration
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
ALTER TABLE identity_verification_configuration FORCE ROW LEVEL SECURITY;

CREATE INDEX idx_identity_verification_configuration_type ON identity_verification_configuration (tenant_id, type);

CREATE TABLE identity_verification_application
(
    id                           UUID                    NOT NULL,
    tenant_id                    UUID                    NOT NULL,
    client_id                    VARCHAR(255)            NOT NULL,
    user_id                      UUID                    NOT NULL,
    verification_type            VARCHAR(255)            NOT NULL,
    application_details          JSONB                   NOT NULL,
    processes                    JSONB                   NOT NULL,
    status                       VARCHAR(255)            NOT NULL,
    requested_at                 TIMESTAMP               NOT NULL,
    comment                      TEXT,
    created_at                   TIMESTAMP DEFAULT now() NOT NULL,
    updated_at                   TIMESTAMP DEFAULT now() NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
);

ALTER TABLE identity_verification_application ENABLE ROW LEVEL SECURITY;
CREATE
POLICY rls_identity_verification_application
  ON identity_verification_application
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
ALTER TABLE identity_verification_application FORCE ROW LEVEL SECURITY;

CREATE INDEX idx_verification_user ON identity_verification_application (user_id);
CREATE INDEX idx_verification_tenant_client ON identity_verification_application (tenant_id, client_id);
CREATE INDEX idx_verification_status ON identity_verification_application (status);
CREATE INDEX idx_verification_application_details ON identity_verification_application USING GIN (application_details);

CREATE TABLE identity_verification_result
(
    id                      UUID         NOT NULL,
    tenant_id               UUID         NOT NULL,
    user_id                 UUID         NOT NULL,
    application_id          UUID,
    verification_type       VARCHAR(255) NOT NULL,
    verified_claims         JSONB        NOT NULL,
    verified_at             TIMESTAMP    NOT NULL,
    valid_until             TIMESTAMP,
    source                  VARCHAR(255) NOT NULL DEFAULT 'application',
    source_details JSONB,
    created_at              TIMESTAMP             DEFAULT now() NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (application_id) REFERENCES identity_verification_application (id) ON DELETE CASCADE,
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
);

CREATE INDEX idx_verification_result_user_id ON identity_verification_result (user_id);
CREATE INDEX idx_verification_result_application_id ON identity_verification_result (application_id);
CREATE INDEX idx_verification_result_verification_type ON identity_verification_result (verification_type);
CREATE INDEX idx_verification_result_verified_at ON identity_verification_result (verified_at);
CREATE INDEX idx_verification_result_verified_claims ON identity_verification_result USING GIN (verified_claims);
CREATE INDEX idx_verification_result_source_details ON identity_verification_result USING GIN (source_details);

ALTER TABLE identity_verification_result ENABLE ROW LEVEL SECURITY;
CREATE
POLICY rls_identity_verification_result
  ON identity_verification_result
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
ALTER TABLE identity_verification_result FORCE ROW LEVEL SECURITY;

CREATE TABLE idp_user_lifecycle_event_result
(
    id             UUID         NOT NULL,
    tenant_id      UUID         NOT NULL,
    user_id        UUID         NOT NULL,
    lifecycle_type VARCHAR(255) NOT NULL,
    executor_name  VARCHAR(255) NOT NULL,
    status         VARCHAR(16)  NOT NULL,
    payload        JSONB,
    created_at     TIMESTAMP DEFAULT now(),
    PRIMARY KEY (id)
);

ALTER TABLE idp_user_lifecycle_event_result ENABLE ROW LEVEL SECURITY;
CREATE
POLICY rls_idp_user_lifecycle_event_result
  ON idp_user_lifecycle_event_result
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
ALTER TABLE idp_user_lifecycle_event_result FORCE ROW LEVEL SECURITY;

CREATE TABLE audit_log
(
    id                     UUID                    NOT NULL,
    type                   VARCHAR(255)            NOT NULL,
    description            VARCHAR(255)            NOT NULL,
    tenant_id              UUID                    NOT NULL,
    client_id              VARCHAR(255)            NOT NULL,
    user_id                UUID                    NOT NULL,
    external_user_id       VARCHAR(255),
    user_payload           JSONB                   NOT NULL,
    target_resource        TEXT                    NOT NULL,
    target_resource_action TEXT                    NOT NULL,
    before_payload         JSONB,
    after_payload          JSONB,
    ip_address             TEXT,
    user_agent             TEXT,
    dry_run                BOOLEAN,
    attributes             JSONB,
    created_at             TIMESTAMP DEFAULT now() NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_audit_log_tenant_id ON audit_log (tenant_id);
CREATE INDEX idx_audit_log_client_id ON audit_log (client_id);
CREATE INDEX idx_audit_log_user_id ON audit_log (user_id);
CREATE INDEX idx_audit_log_external_user_id ON audit_log (external_user_id);
CREATE INDEX idx_audit_log_created_at ON audit_log (created_at);
CREATE INDEX idx_audit_log_tenant_created_at ON audit_log (tenant_id, created_at);
CREATE INDEX idx_audit_log_attributes ON audit_log USING GIN (attributes);