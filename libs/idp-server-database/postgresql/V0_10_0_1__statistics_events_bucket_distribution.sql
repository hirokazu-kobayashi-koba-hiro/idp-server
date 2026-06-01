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

-- =====================================================
-- Issue #1443: statistics_event_buckets (bucket-distributed counters)
--
-- Real-time UPSERT to statistics_events causes row lock contention on the
-- hot key (tenant_id, stat_date, event_type). This migration introduces a
-- NEW table statistics_event_buckets that scatters writes across N rows
-- per logical key via a bucket_id dimension.
--
-- Read side aggregates with SUM(count) ... GROUP BY tenant_id, stat_date,
-- event_type (handled in repository code).
--
-- The existing statistics_events table is left untouched by this migration
-- to keep the rollout side-effect free. Historical data is migrated to the
-- new table by a separate operational script after deploy completes:
--   libs/idp-server-database/postgresql/operation/
--     statistics-events-bucket-migration/migrate_data.sql
-- =====================================================

CREATE TABLE statistics_event_buckets (
    tenant_id UUID NOT NULL,
    stat_date DATE NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    bucket_id SMALLINT NOT NULL,
    count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (tenant_id, stat_date, event_type, bucket_id)
);

-- Index for efficient querying by tenant and date range (matches statistics_events shape)
CREATE INDEX idx_statistics_event_buckets_tenant_date
    ON statistics_event_buckets (tenant_id, stat_date DESC);

ALTER TABLE statistics_event_buckets ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON statistics_event_buckets
    USING (tenant_id = current_setting('app.tenant_id')::uuid);

ALTER TABLE statistics_event_buckets FORCE ROW LEVEL SECURITY;

COMMENT ON TABLE statistics_event_buckets IS
    'Bucket-distributed statistics counters. Successor of statistics_events; writes scatter across bucket_id [0..N-1] to eliminate hot-row UPSERT contention. Reads aggregate with SUM(count) GROUP BY tenant_id, stat_date, event_type.';
COMMENT ON COLUMN statistics_event_buckets.bucket_id IS
    'Write-side shard id (0..N-1) to distribute UPSERT lock contention on hot keys.';
COMMENT ON COLUMN statistics_event_buckets.count IS
    'Per-bucket partial count. SUM across bucket_id gives the logical total for (tenant_id, stat_date, event_type).';
