/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

-- ================================================
-- Composite Indexes for security_event and audit_log tables
-- Issue #1227: Query performance improvement
--
-- Problem:
--   - Single-column indexes don't efficiently support multi-condition queries
--   - Queries with external_user_id, client_id, user_id, or type filters
--     combined with tenant_id and created_at range result in full scans
--   - Performance degrades from 294ms to 5.46s with large datasets
--
-- Solution:
--   - Add composite indexes covering: tenant_id + filter_column + created_at DESC
--   - Index order optimized for RLS (tenant_id first) and ORDER BY (created_at last)
--
-- ================================================

-- ================================================
-- security_event indexes
-- ================================================

-- External user ID search
-- Query pattern: WHERE tenant_id = ? AND external_user_id = ? AND created_at BETWEEN ? AND ? ORDER BY created_at DESC
CREATE INDEX IF NOT EXISTS idx_events_tenant_external_user_created_at
    ON security_event (tenant_id, external_user_id, created_at DESC);

-- Client ID search
-- Query pattern: WHERE tenant_id = ? AND client_id = ? AND created_at BETWEEN ? AND ? ORDER BY created_at DESC
CREATE INDEX IF NOT EXISTS idx_events_tenant_client_created_at
    ON security_event (tenant_id, client_id, created_at DESC);

-- User ID search
-- Query pattern: WHERE tenant_id = ? AND user_id = ? AND created_at BETWEEN ? AND ? ORDER BY created_at DESC
CREATE INDEX IF NOT EXISTS idx_events_tenant_user_created_at
    ON security_event (tenant_id, user_id, created_at DESC);

-- Event type search
-- Query pattern: WHERE tenant_id = ? AND type = ? AND created_at BETWEEN ? AND ? ORDER BY created_at DESC
CREATE INDEX IF NOT EXISTS idx_events_tenant_type_created_at
    ON security_event (tenant_id, type, created_at DESC);

-- ================================================
-- audit_log indexes
-- ================================================

-- External user ID search
CREATE INDEX IF NOT EXISTS idx_audit_log_tenant_external_user_created_at
    ON audit_log (tenant_id, external_user_id, created_at DESC);

-- Client ID search
CREATE INDEX IF NOT EXISTS idx_audit_log_tenant_client_created_at
    ON audit_log (tenant_id, client_id, created_at DESC);

-- User ID search
CREATE INDEX IF NOT EXISTS idx_audit_log_tenant_user_created_at
    ON audit_log (tenant_id, user_id, created_at DESC);

-- Type search (replaces idx_audit_log_type_created which lacks tenant_id)
CREATE INDEX IF NOT EXISTS idx_audit_log_tenant_type_created_at
    ON audit_log (tenant_id, type, created_at DESC);

-- Outcome result search
CREATE INDEX IF NOT EXISTS idx_audit_log_tenant_outcome_created_at
    ON audit_log (tenant_id, outcome_result, created_at DESC);

-- ================================================
-- Note: The following single-column indexes may become redundant
-- after these composite indexes are added. Consider dropping them
-- after verifying query execution plans in production:
--
-- security_event:
--   idx_events_external_user_id (external_user_id)
--   idx_events_client (client_id)
--   idx_events_user (user_id)
--   idx_events_type (type)
--
-- audit_log:
--   idx_audit_log_external_user_id (external_user_id)
--   idx_audit_log_client_id (client_id)
--   idx_audit_log_user_id (user_id)
--   idx_audit_log_type_created (type, created_at DESC) -- lacks tenant_id
--
-- Do NOT drop them immediately - verify with EXPLAIN ANALYZE first.
-- ================================================
