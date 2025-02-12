CREATE TABLE organization
(
    id          varchar(256) PRIMARY KEY,
    name        varchar(256) NOT NULL,
    description text,
    created_at  timestamp default now() NOT NULL,
    updated_at  timestamp default now() NOT NULL
);

CREATE TABLE tenant
(
    id         varchar(256) NOT NULL PRIMARY KEY,
    name       varchar(256) NOT NULL,
    type       varchar(10)  NOT NULL,
    issuer     text         NOT NULL,
    created_at timestamp    NOT NULL default now(),
    updated_at timestamp    NOT NULL default now()
);

CREATE TABLE organization_tenants
(
    id          varchar(256) PRIMARY KEY,
    organization_id     varchar(256) REFERENCES organization (id) ON DELETE CASCADE,
    tenant_id   varchar(256) REFERENCES tenant (id) ON DELETE CASCADE,
    assigned_at timestamp default now() NOT NULL,
    UNIQUE (organization_id, tenant_id)
);

CREATE TABLE idp_user
(
    id                    varchar(256)            NOT NULL PRIMARY KEY,
    tenant_id             varchar(256)            NOT NULL,
    name                  varchar(256),
    given_name            varchar(256),
    family_name           varchar(256),
    middle_name           varchar(256),
    nickname              varchar(256),
    preferred_username    varchar(256),
    profile               varchar(256),
    picture               varchar(256),
    website               varchar(256),
    email                 varchar(256),
    email_verified        boolean,
    gender                varchar(256),
    birthdate             varchar(256),
    zoneinfo              varchar(256),
    locale                varchar(256),
    phone_number          varchar(256),
    phone_number_verified boolean,
    address               text                    NOT NULL,
    custom_properties     text,
    credentials           text,
    password              text,
    created_at            timestamp default now() NOT NULL,
    updated_at            timestamp default now() NOT NULL,
    CONSTRAINT uk_tenant_id_email unique (tenant_id, email)
);

CREATE TABLE organization_members
(
    id        varchar(256) PRIMARY KEY,
    idp_user_id   varchar(256) REFERENCES idp_user (id) ON DELETE CASCADE,
    organization_id   varchar(256) REFERENCES organization (id) ON DELETE CASCADE,
    role      varchar(100) NOT NULL,
    joined_at timestamp default now() NOT NULL,
    UNIQUE (idp_user_id, organization_id)
);

