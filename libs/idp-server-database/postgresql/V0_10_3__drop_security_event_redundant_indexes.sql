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
-- Drop redundant indexes on security_event
--
-- 1) GIN(detail)
--    Application queries `detail` via the ->> operator, not @>, so the GIN
--    index is never used by query planner. It only pays write cost: each
--    INSERT registers many (path, value) entries.
--
-- 2) Single-column B-tree indexes that are fully covered by composite ones
--    For all of (tenant_id, type, client_id, user_id, external_user_id),
--    EXPLAIN ANALYZE shows the planner picks the composite
--    (tenant_id, X, created_at DESC) index. The single-column variants are
--    redundant prefixes.
--
-- Indexes preserved:
--   - security_event_pkey
--   - idx_events_created_at         (used by Index Only Scan in batch
--                                    statistics aggregation queries)
--   - idx_events_tenant_created_at
--   - idx_events_tenant_external_user_created_at
--   - idx_events_tenant_client_created_at
--   - idx_events_tenant_user_created_at
--   - idx_events_tenant_type_created_at
--
-- Effect: reduces per-INSERT cost by ~50% (12 index updates → 6),
-- and removes the heaviest GIN update path.
-- =====================================================

DROP INDEX IF EXISTS idx_events_detail_jsonb;
DROP INDEX IF EXISTS idx_events_tenant;
DROP INDEX IF EXISTS idx_events_type;
DROP INDEX IF EXISTS idx_events_client;
DROP INDEX IF EXISTS idx_events_user;
DROP INDEX IF EXISTS idx_events_external_user_id;
