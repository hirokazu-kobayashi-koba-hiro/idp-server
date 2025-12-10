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
-- Daily Partitioning for Event Tables with pg_partman
-- Issue #950: Implement 90-day retention with daily partitioning
--
-- Target tables:
--   - security_event (90-day retention)
--   - security_event_hook_results (90-day retention)
--
-- Note: audit_log is NOT partitioned (permanent retention for compliance)
--
-- pg_partman advantages:
--   - Proven, well-tested partition management
--   - check_default() for monitoring DEFAULT partition
--   - partition_data_time() for data redistribution
--   - Flexible retention configuration via part_config table
--   - AWS RDS compatible (with pg_cron)
--
-- ================================================
-- PREREQUISITES (must be done by superuser before this migration):
-- ================================================
--
-- The following extensions require superuser privileges and must be
-- pre-installed during infrastructure setup:
--
--   -- In postgresql.conf or docker command:
--   -- shared_preload_libraries = 'pg_stat_statements,pg_cron'
--   -- cron.database_name = 'idpserver'
--
--   -- As superuser:
--   CREATE EXTENSION IF NOT EXISTS pg_cron;
--   CREATE SCHEMA IF NOT EXISTS partman;
--   CREATE EXTENSION IF NOT EXISTS pg_partman WITH SCHEMA partman;
--
--   -- Grant permissions to migration user (db_owner):
--   GRANT USAGE, CREATE ON SCHEMA partman TO db_owner;
--   GRANT USAGE ON SCHEMA cron TO db_owner;
--   GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA partman TO db_owner;
--   GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA partman TO db_owner;
--   GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA cron TO db_owner;
--
-- ================================================

-- ================================================
-- Phase 1: Backup existing data
-- ================================================

-- Backup security_event data
CREATE TEMP TABLE security_event_backup AS
SELECT * FROM security_event;

-- Backup security_event_hook_results data
CREATE TEMP TABLE security_event_hook_results_backup AS
SELECT * FROM security_event_hook_results;

-- ================================================
-- Phase 2: Drop and recreate security_event as partitioned table
-- ================================================

DROP TABLE security_event CASCADE;

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
PARTITION BY RANGE (created_at);

-- Enable Row Level Security
ALTER TABLE security_event ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON security_event USING (
    tenant_id = current_setting('app.tenant_id')::uuid
);

ALTER TABLE security_event FORCE ROW LEVEL SECURITY;

-- Recreate indexes
CREATE INDEX idx_events_type ON security_event (type);
CREATE INDEX idx_events_tenant ON security_event (tenant_id);
CREATE INDEX idx_events_client ON security_event (client_id);
CREATE INDEX idx_events_user ON security_event (user_id);
CREATE INDEX idx_events_external_user_id ON security_event (external_user_id);
CREATE INDEX idx_events_created_at ON security_event (created_at);
CREATE INDEX idx_events_detail_jsonb ON security_event USING GIN (detail);
CREATE INDEX idx_events_tenant_created_at ON security_event (tenant_id, created_at);

COMMENT ON TABLE security_event IS 'Security events with daily partitioning. Retained for 90 days. Managed by pg_partman.';

-- ================================================
-- Phase 3: Drop and recreate security_event_hook_results as partitioned table
-- ================================================

DROP TABLE security_event_hook_results CASCADE;

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
PARTITION BY RANGE (created_at);

-- Enable Row Level Security
ALTER TABLE security_event_hook_results ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON security_event_hook_results USING (
    tenant_id = current_setting('app.tenant_id')::uuid
);

ALTER TABLE security_event_hook_results FORCE ROW LEVEL SECURITY;

-- Recreate indexes
CREATE INDEX idx_hook_results_tenant ON security_event_hook_results (tenant_id);
CREATE INDEX idx_hook_results_security_event ON security_event_hook_results (security_event_id);
CREATE INDEX idx_hook_results_status ON security_event_hook_results (status);
CREATE INDEX idx_hook_results_created_at ON security_event_hook_results (created_at);

COMMENT ON TABLE security_event_hook_results IS 'Security event hook execution results with daily partitioning. Retained for 90 days. Managed by pg_partman.';

-- ================================================
-- Phase 4: Configure pg_partman (creates default partition)
-- ================================================

-- Configure pg_partman for security_event
SELECT partman.create_parent(
    p_parent_table => 'public.security_event',
    p_control => 'created_at',
    p_type => 'range',
    p_interval => '1 day',
    p_premake => 90,
    p_start_partition => CURRENT_DATE::text
);

UPDATE partman.part_config
SET infinite_time_partitions = true,
    retention = '90 days',
    retention_keep_table = false,
    retention_keep_index = false
WHERE parent_table = 'public.security_event';

-- Configure pg_partman for security_event_hook_results
SELECT partman.create_parent(
    p_parent_table => 'public.security_event_hook_results',
    p_control => 'created_at',
    p_type => 'range',
    p_interval => '1 day',
    p_premake => 90,
    p_start_partition => CURRENT_DATE::text
);

UPDATE partman.part_config
SET infinite_time_partitions = true,
    retention = '90 days',
    retention_keep_table = false,
    retention_keep_index = false
WHERE parent_table = 'public.security_event_hook_results';

-- ================================================
-- Phase 5: Restore data from backup
-- ================================================

-- Restore security_event data (goes to default partition initially)
INSERT INTO security_event SELECT * FROM security_event_backup;

-- Restore security_event_hook_results data
INSERT INTO security_event_hook_results SELECT * FROM security_event_hook_results_backup;

-- ================================================
-- Phase 6: Schedule pg_partman maintenance with pg_cron
-- ================================================
-- Note: pg_cron job registration is handled by pg-cron-setup container
-- (see docker-compose.yaml and setup-pg-cron-jobs.sql)
-- This separation allows:
--   - Idempotent job registration (safe to run multiple times)
--   - Environment-specific schedule customization
--   - Clear separation of DDL and operational setup

-- ================================================
-- Phase 7: Initial maintenance (run after migration)
-- ================================================
-- Note: partman.run_maintenance_proc() contains COMMIT and cannot run inside Flyway transaction.
-- Run the following commands manually after migration or via pg_cron:
--
--   CALL partman.run_maintenance_proc();
--
--   SELECT partman.partition_data_time(
--       p_parent_table := 'public.security_event',
--       p_batch_count := 10000
--   );
--
--   SELECT partman.partition_data_time(
--       p_parent_table := 'public.security_event_hook_results',
--       p_batch_count := 10000
--   );
--
-- The pg_cron job scheduled above will handle this automatically at 02:00 UTC.

-- Useful monitoring queries (for reference):
--
-- Check pg_partman configuration:
--   SELECT parent_table, partition_interval, premake, retention
--   FROM partman.part_config;
--
-- Check DEFAULT partition for orphaned data:
--   SELECT * FROM partman.check_default();
--
-- List partitions for a table:
--   SELECT c.relname as partition
--   FROM pg_inherits i
--   JOIN pg_class c ON i.inhrelid = c.oid
--   JOIN pg_class p ON i.inhparent = p.oid
--   WHERE p.relname = 'security_event'
--   ORDER BY c.relname;
--
-- Check pg_cron job status:
--   SELECT jobid, jobname, schedule, command, active FROM cron.job;
--   SELECT * FROM cron.job_run_details ORDER BY start_time DESC LIMIT 10;
