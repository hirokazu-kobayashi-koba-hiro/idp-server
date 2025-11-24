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
-- Daily Partitioning for Event Tables
-- Issue #950: Implement 90-day retention with daily partitioning
-- ================================================

-- ================================================
-- Phase 1: security_event table partitioning
-- ================================================

BEGIN;

-- 1. Backup existing data to temporary table
CREATE TEMP
TABLE security_event_backup AS
SELECT *
FROM security_event;

-- 2. Drop existing table
DROP TABLE security_event CASCADE;

-- 3. Create partitioned table
CREATE TABLE security_event (
    id UUID,
    type VARCHAR(255) NOT NULL,
    description VARCHAR(255) NOT NULL,
    tenant_id UUID NOT NULL,
    tenant_name VARCHAR(255) NOT NULL,
    client_id VARCHAR(255) NOT NULL,
    client_name VARCHAR(255) NOT NULL,
    user_id UUID,
    user_name VARCHAR(255),
    external_user_id VARCHAR(255),
    ip_address INET,
    user_agent TEXT,
    detail JSONB NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (id, created_at)
)
PARTITION BY
    RANGE (created_at);

-- 4. Create default partition for existing data
CREATE TABLE security_event_default PARTITION OF security_event DEFAULT;

-- 5. Create daily partitions for next 90 days
DO $$
DECLARE
    partition_date DATE;
    partition_name TEXT;
    start_date TEXT;
    end_date TEXT;
BEGIN
    FOR i IN 0..89 LOOP
        partition_date := CURRENT_DATE + (i || ' days')::interval;
        partition_name := 'security_event_' || to_char(partition_date, 'YYYY_MM_DD');
        start_date := to_char(partition_date, 'YYYY-MM-DD');
        end_date := to_char(partition_date + interval '1 day', 'YYYY-MM-DD');

        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS %I PARTITION OF security_event FOR VALUES FROM (%L) TO (%L)',
            partition_name, start_date, end_date
        );
    END LOOP;

    RAISE NOTICE 'Created 90 daily partitions for security_event';
END $$;

-- 6. Restore data from backup
INSERT INTO security_event SELECT * FROM security_event_backup;

-- 7. Enable Row Level Security
ALTER TABLE security_event ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy
  ON security_event
  USING (tenant_id = current_setting('app.tenant_id')::uuid);

ALTER TABLE security_event FORCE ROW LEVEL SECURITY;

-- 8. Recreate indexes
CREATE INDEX idx_events_type ON security_event (type);

CREATE INDEX idx_events_tenant ON security_event (tenant_id);

CREATE INDEX idx_events_client ON security_event (client_id);

CREATE INDEX idx_events_user ON security_event (user_id);

CREATE INDEX idx_events_external_user_id ON security_event (external_user_id);

CREATE INDEX idx_events_created_at ON security_event (created_at);

CREATE INDEX idx_events_detail_jsonb ON security_event USING GIN (detail);

CREATE INDEX idx_events_tenant_created_at ON security_event (tenant_id, created_at);

-- 9. Add table comment for retention policy
COMMENT ON
TABLE security_event IS 'Security events are retained for 90 days from created_at. Daily partitions older than 90 days are automatically dropped by scheduled cleanup job.';

COMMIT;

-- ================================================
-- Phase 2: security_event_hook_results table partitioning
-- ================================================

BEGIN;

-- 1. Backup existing data
CREATE TEMP
TABLE security_event_hook_results_backup AS
SELECT *
FROM security_event_hook_results;

-- 2. Drop existing table
DROP TABLE security_event_hook_results CASCADE;

-- 3. Create partitioned table
CREATE TABLE security_event_hook_results (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    security_event_id UUID NOT NULL,
    security_event_type VARCHAR(255) NOT NULL,
    security_event_hook VARCHAR(255) NOT NULL,
    security_event_payload JSONB NOT NULL,
    security_event_hook_execution_payload JSONB,
    status VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT now() NOT NULL,
    updated_at TIMESTAMP DEFAULT now() NOT NULL,
    PRIMARY KEY (id, created_at)
)
PARTITION BY
    RANGE (created_at);

-- 4. Create default partition
CREATE TABLE security_event_hook_results_default PARTITION OF security_event_hook_results DEFAULT;

-- 5. Create daily partitions for next 90 days
DO $$
DECLARE
    partition_date DATE;
    partition_name TEXT;
    start_date TEXT;
    end_date TEXT;
BEGIN
    FOR i IN 0..89 LOOP
        partition_date := CURRENT_DATE + (i || ' days')::interval;
        partition_name := 'security_event_hook_results_' || to_char(partition_date, 'YYYY_MM_DD');
        start_date := to_char(partition_date, 'YYYY-MM-DD');
        end_date := to_char(partition_date + interval '1 day', 'YYYY-MM-DD');

        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS %I PARTITION OF security_event_hook_results FOR VALUES FROM (%L) TO (%L)',
            partition_name, start_date, end_date
        );
    END LOOP;

    RAISE NOTICE 'Created 90 daily partitions for security_event_hook_results';
END $$;

-- 6. Restore data
INSERT INTO
    security_event_hook_results
SELECT *
FROM
    security_event_hook_results_backup;

-- 7. Enable Row Level Security
ALTER TABLE security_event_hook_results ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy
  ON security_event_hook_results
  USING (tenant_id = current_setting('app.tenant_id')::uuid);

ALTER TABLE security_event_hook_results FORCE ROW LEVEL SECURITY;

-- 8. Restore column comment
COMMENT ON COLUMN security_event_hook_results.security_event_hook_execution_payload IS 'Stores the execution result payload from security event hooks for resending and debugging purposes';

-- 9. Add table comment
COMMENT ON
TABLE security_event_hook_results IS 'Security event hook results are retained for 90 days from created_at. Daily partitions older than 90 days are automatically dropped by scheduled cleanup job.';

COMMIT;