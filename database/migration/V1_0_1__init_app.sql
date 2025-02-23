CREATE TABLE organization
(
    id          VARCHAR(256) PRIMARY KEY,
    name        VARCHAR(256)            NOT NULL,
    description TEXT,
    created_at  TIMESTAMP DEFAULT now() NOT NULL,
    updated_at  TIMESTAMP DEFAULT now() NOT NULL
);

CREATE TABLE tenant
(
    id         VARCHAR(256) NOT NULL PRIMARY KEY,
    name       VARCHAR(256) NOT NULL,
    type       VARCHAR(10)  NOT NULL,
    issuer     TEXT         NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX unique_admin_tenant ON tenant(type) WHERE type = 'ADMIN';

CREATE TABLE organization_tenants
(
    id              CHAR(36) PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id VARCHAR(256) REFERENCES organization (id) ON DELETE CASCADE,
    tenant_id       VARCHAR(256) REFERENCES tenant (id) ON DELETE CASCADE,
    assigned_at     TIMESTAMP            DEFAULT now() NOT NULL,
    UNIQUE (organization_id, tenant_id)
);

CREATE TABLE idp_user
(
    id                    VARCHAR(256)            NOT NULL PRIMARY KEY,
    tenant_id             VARCHAR(256)            NOT NULL,
    name                  VARCHAR(256),
    given_name            VARCHAR(256),
    family_name           VARCHAR(256),
    middle_name           VARCHAR(256),
    nickname              VARCHAR(256),
    preferred_username    VARCHAR(256),
    profile               VARCHAR(256),
    picture               VARCHAR(256),
    website               VARCHAR(256),
    email                 VARCHAR(256),
    email_verified        BOOLEAN,
    gender                VARCHAR(256),
    birthdate             VARCHAR(256),
    zoneinfo              VARCHAR(256),
    locale                VARCHAR(256),
    phone_number          VARCHAR(256),
    phone_number_verified BOOLEAN,
    address               TEXT                    NOT NULL,
    custom_properties     TEXT,
    credentials           TEXT,
    hashed_password              TEXT,
    created_at            TIMESTAMP DEFAULT now() NOT NULL,
    updated_at            TIMESTAMP DEFAULT now() NOT NULL,
    CONSTRAINT uk_tenant_id_email unique (tenant_id, email)
);

CREATE TABLE organization_members
(
    id              CHAR(36) PRIMARY KEY DEFAULT gen_random_uuid(),
    idp_user_id     VARCHAR(256) REFERENCES idp_user (id) ON DELETE CASCADE,
    organization_id VARCHAR(256) REFERENCES organization (id) ON DELETE CASCADE,
    role            VARCHAR(100)                       NOT NULL,
    joined_at       TIMESTAMP            DEFAULT now() NOT NULL,
    UNIQUE (idp_user_id, organization_id)
);

CREATE TABLE idp_user_current_organization
(
    idp_user_id     VARCHAR(256) REFERENCES idp_user (id) ON DELETE CASCADE PRIMARY KEY,
    organization_id VARCHAR(256) REFERENCES organization (id) ON DELETE CASCADE,
    created_at      TIMESTAMP DEFAULT now() NOT NULL,
    updated_at            TIMESTAMP DEFAULT now() NOT NULL
)

