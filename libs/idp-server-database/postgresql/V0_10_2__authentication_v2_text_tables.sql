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
--
-- Strategy: new-table swap (zero-downtime friendly)
--   - JSONB columns written/read as full snapshots are converted to TEXT
--     (no -> / ->> / @> / GIN usage on them)
--   - `attributes` stays JSONB (queried with -> ->> and GIN-indexed)
--   - Application writes new records into the *_v2 tables; legacy rows in
--     v1 tables expire naturally (short-lived data)
--
-- Removes per-INSERT jsonb_in parsing cost and the GIN-update cost that
-- otherwise dominates write throughput on the auth hot path.
-- =====================================================

CREATE TABLE authentication_transaction_v2
(
    id                            UUID                    NOT NULL,
    tenant_id                     UUID                    NOT NULL,
    tenant_payload                TEXT                    NOT NULL,
    flow                          VARCHAR(255)            NOT NULL,
    authorization_id              UUID,
    client_id                     VARCHAR(255)            NOT NULL,
    client_payload                TEXT                    NOT NULL,
    user_id                       UUID,
    user_payload                  TEXT,
    context                       TEXT,
    authentication_device_id      UUID,
    authentication_device_payload TEXT,
    authentication_policy         TEXT,
    interactions                  TEXT,
    attributes                    JSONB,
    expires_at                    TIMESTAMP               NOT NULL,
    created_at                    TIMESTAMP DEFAULT now() NOT NULL,
    updated_at                    TIMESTAMP DEFAULT now() NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
);

ALTER TABLE authentication_transaction_v2 ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_policy
  ON authentication_transaction_v2
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
ALTER TABLE authentication_transaction_v2 FORCE ROW LEVEL SECURITY;

CREATE INDEX idx_authentication_transaction_v2_device_id ON authentication_transaction_v2 (authentication_device_id);
CREATE INDEX idx_authentication_v2_client_id ON authentication_transaction_v2 (client_id);
CREATE INDEX idx_authentication_v2_tenant_id ON authentication_transaction_v2 (tenant_id);
CREATE INDEX idx_authentication_v2_authorization_id ON authentication_transaction_v2 (authorization_id);
CREATE INDEX idx_authentication_v2_flow ON authentication_transaction_v2 (flow);
CREATE INDEX idx_authentication_transaction_v2_expires_at ON authentication_transaction_v2 (tenant_id, expires_at);
CREATE INDEX idx_authentication_transaction_v2_attributes ON authentication_transaction_v2 USING GIN (attributes);


CREATE TABLE authentication_interactions_v2
(
    authentication_transaction_id UUID                    NOT NULL,
    tenant_id                     UUID                    NOT NULL,
    interaction_type              VARCHAR(255)            NOT NULL,
    payload                       TEXT                    NOT NULL,
    created_at                    TIMESTAMP DEFAULT now() NOT NULL,
    updated_at                    TIMESTAMP DEFAULT now() NOT NULL,
    PRIMARY KEY (authentication_transaction_id, interaction_type),
    FOREIGN KEY (authentication_transaction_id) REFERENCES authentication_transaction_v2 (id) ON DELETE CASCADE,
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE
);

ALTER TABLE authentication_interactions_v2 ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_policy
  ON authentication_interactions_v2
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
ALTER TABLE authentication_interactions_v2 FORCE ROW LEVEL SECURITY;
