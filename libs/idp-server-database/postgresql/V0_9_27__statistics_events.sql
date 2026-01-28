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
-- New table for normalized statistics events.
--
-- This table replaces JSONB updates in statistics_monthly with
-- row-based updates for better write performance.
-- Each (tenant_id, stat_date, event_type) combination is a separate row.
-- =====================================================

CREATE TABLE statistics_events (
    tenant_id UUID NOT NULL,
    stat_date DATE NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (tenant_id, stat_date, event_type)
);

-- Index for efficient querying by tenant and date range
CREATE INDEX idx_statistics_events_tenant_date ON statistics_events (tenant_id, stat_date DESC);

ALTER TABLE statistics_events ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON statistics_events
    USING (tenant_id = current_setting('app.tenant_id')::uuid);

ALTER TABLE statistics_events FORCE ROW LEVEL SECURITY;

COMMENT ON TABLE statistics_events IS 'Normalized statistics events. Replaces JSONB updates for better write performance.';
COMMENT ON COLUMN statistics_events.stat_date IS 'Date of event';
COMMENT ON COLUMN statistics_events.event_type IS 'Type of security event (e.g., login_success, issue_token_success, dau, mau)';
COMMENT ON COLUMN statistics_events.count IS 'Aggregated count for this event type on this date';
