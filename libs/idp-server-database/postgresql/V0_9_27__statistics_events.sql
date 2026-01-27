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
-- Issue #1198: Statistics data update performance improvement
--
-- New table for normalized statistics events:
--   - statistics_events (daily partitioned, 90-day retention)
--
-- This table replaces JSONB updates in statistics_monthly with
-- row-based updates for better write performance.
-- Each (tenant_id, stat_date, event_type) combination is a separate row.
-- =====================================================

-- =====================================================
-- statistics_events (PARTITIONED - daily)
-- Track event counts per tenant/date/event_type
-- =====================================================

CREATE TABLE statistics_events (
    tenant_id UUID NOT NULL,
    stat_date DATE NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (tenant_id, stat_date, event_type)
)
PARTITION BY RANGE (stat_date);

-- Index for efficient querying by tenant and date range
CREATE INDEX idx_statistics_events_tenant_date ON statistics_events (tenant_id, stat_date DESC);
CREATE INDEX idx_statistics_events_tenant_date_type ON statistics_events (tenant_id, stat_date, event_type);

ALTER TABLE statistics_events ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON statistics_events
    USING (tenant_id = current_setting('app.tenant_id')::uuid);

ALTER TABLE statistics_events FORCE ROW LEVEL SECURITY;

COMMENT ON TABLE statistics_events IS 'Normalized statistics events with daily partitioning. Replaces JSONB updates for better write performance. Retained for 90 days. Managed by pg_partman.';
COMMENT ON COLUMN statistics_events.stat_date IS 'Date of event (partition key)';
COMMENT ON COLUMN statistics_events.event_type IS 'Type of security event (e.g., login_success, issue_token_success, dau, mau)';
COMMENT ON COLUMN statistics_events.count IS 'Aggregated count for this event type on this date';

-- Configure pg_partman for statistics_events
SELECT partman.create_parent(
    p_parent_table => 'public.statistics_events',
    p_control => 'stat_date',
    p_type => 'range',
    p_interval => '1 day',
    p_premake => 90,
    p_start_partition => CURRENT_DATE::text
);

UPDATE partman.part_config
SET infinite_time_partitions = true,
    retention = '90 days',
    retention_schema = 'archive',
    retention_keep_table = true,
    retention_keep_index = true
WHERE parent_table = 'public.statistics_events';

-- =====================================================
-- Helper function for batch upsert
-- =====================================================

CREATE OR REPLACE FUNCTION upsert_statistics_events(
    p_records JSONB
)
RETURNS INTEGER AS $$
DECLARE
    inserted_count INTEGER;
    record_data JSONB;
BEGIN
    inserted_count := 0;

    FOR record_data IN SELECT * FROM jsonb_array_elements(p_records)
    LOOP
        INSERT INTO statistics_events (tenant_id, stat_date, event_type, count)
        VALUES (
            (record_data->>'tenant_id')::UUID,
            (record_data->>'stat_date')::DATE,
            record_data->>'event_type',
            (record_data->>'count')::BIGINT
        )
        ON CONFLICT (tenant_id, stat_date, event_type)
        DO UPDATE SET
            count = statistics_events.count + EXCLUDED.count,
            updated_at = now();

        inserted_count := inserted_count + 1;
    END LOOP;

    RETURN inserted_count;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION upsert_statistics_events(JSONB) IS 'Batch upsert statistics events. Input is JSONB array of {tenant_id, stat_date, event_type, count} objects.';

-- =====================================================
-- Cleanup function for statistics_events
-- Note: Partitioned tables are managed by pg_partman
-- =====================================================

CREATE OR REPLACE FUNCTION cleanup_old_statistics_events(retention_days INTEGER)
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
    cutoff_date DATE;
BEGIN
    cutoff_date := CURRENT_DATE - retention_days;

    -- This is a fallback; pg_partman handles partition cleanup
    DELETE FROM statistics_events
    WHERE stat_date < cutoff_date;

    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION cleanup_old_statistics_events(INTEGER) IS 'Fallback cleanup for statistics events. Normally handled by pg_partman retention.';

-- =====================================================
-- Query helper for aggregated statistics
-- =====================================================

CREATE OR REPLACE FUNCTION get_statistics_summary(
    p_tenant_id UUID,
    p_start_date DATE,
    p_end_date DATE
)
RETURNS TABLE (
    event_type VARCHAR(255),
    total_count BIGINT
) AS $$
BEGIN
    RETURN QUERY
    SELECT se.event_type, SUM(se.count)::BIGINT as total_count
    FROM statistics_events se
    WHERE se.tenant_id = p_tenant_id
      AND se.stat_date >= p_start_date
      AND se.stat_date <= p_end_date
    GROUP BY se.event_type
    ORDER BY se.event_type;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION get_statistics_summary(UUID, DATE, DATE) IS 'Get aggregated statistics for a tenant within a date range';
