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
-- Create authentication_transaction_v2 / authentication_interactions_v2
-- (MySQL parity of PostgreSQL V0_10_2)
--
-- JSON columns written/read as full snapshots become LONGTEXT.
-- `attributes` stays JSON (kept for parity with PostgreSQL JSONB behavior).
-- MySQL does not support RLS; tenant isolation is enforced in application
-- layer.
-- =====================================================

CREATE TABLE authentication_transaction_v2
(
    id                            CHAR(36)               NOT NULL,
    tenant_id                     CHAR(36)               NOT NULL,
    tenant_payload                LONGTEXT               NOT NULL,
    flow                          VARCHAR(255)           NOT NULL,
    authorization_id              CHAR(36),
    client_id                     VARCHAR(255)           NOT NULL,
    client_payload                LONGTEXT               NOT NULL,
    user_id                       CHAR(36),
    user_payload                  LONGTEXT,
    context                       LONGTEXT,
    authentication_device_id      CHAR(36),
    authentication_device_payload LONGTEXT,
    authentication_policy         LONGTEXT,
    interactions                  LONGTEXT,
    attributes                    JSON,
    expires_at                    DATETIME(6)            NOT NULL,
    created_at                    DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    updated_at                    DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_authentication_transaction_v2_device_id ON authentication_transaction_v2 (authentication_device_id);
CREATE INDEX idx_authentication_v2_client_id ON authentication_transaction_v2 (client_id);
CREATE INDEX idx_authentication_v2_tenant_id ON authentication_transaction_v2 (tenant_id);
CREATE INDEX idx_authentication_v2_authorization_id ON authentication_transaction_v2 (authorization_id);
CREATE INDEX idx_authentication_v2_flow ON authentication_transaction_v2 (flow);
CREATE INDEX idx_authentication_transaction_v2_expires_at ON authentication_transaction_v2 (tenant_id, expires_at);


CREATE TABLE authentication_interactions_v2
(
    authentication_transaction_id CHAR(36)                           NOT NULL,
    tenant_id                     CHAR(36)                           NOT NULL,
    interaction_type              VARCHAR(255)                       NOT NULL,
    payload                       LONGTEXT                           NOT NULL,
    created_at                    DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    updated_at                    DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (authentication_transaction_id, interaction_type),
    FOREIGN KEY (authentication_transaction_id) REFERENCES authentication_transaction_v2 (id) ON DELETE CASCADE,
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
