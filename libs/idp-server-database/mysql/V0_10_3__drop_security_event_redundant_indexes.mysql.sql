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
-- Drop redundant single-column B-tree indexes on security_event (MySQL)
--
-- For each of (tenant_id, type, client_id, user_id, external_user_id),
-- the composite (tenant_id, X, created_at DESC) index introduced in V0_9_31
-- provides the same lookup capability as a prefix scan. The single-column
-- indexes only add write cost.
--
-- Indexes preserved:
--   - PK
--   - idx_events_created_at
--   - idx_events_tenant_created_at
--   - idx_events_tenant_external_user_created_at
--   - idx_events_tenant_client_created_at
--   - idx_events_tenant_user_created_at
--   - idx_events_tenant_type_created_at
--
-- MySQL has no GIN index (no JSONB type either); PostgreSQL parity item
-- (drop GIN(detail)) is therefore not applicable here.
-- =====================================================

ALTER TABLE security_event
  DROP INDEX idx_events_tenant,
  DROP INDEX idx_events_type,
  DROP INDEX idx_events_client,
  DROP INDEX idx_events_user,
  DROP INDEX idx_events_external_user_id;
