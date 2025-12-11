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
-- Archive Support for Event Tables
-- Issue #441: S3 archive strategy for security events
--
-- This migration adds:
--   1. Archive schema for detached partitions
--   2. Update pg_partman to use retention_schema (DETACH instead of DROP)
--   3. Stub functions for external storage export (AWS S3, GCS, etc.)
--   4. Archive processing function
--
-- Strategy:
--   pg_partman (retention) → DETACH to archive schema → Export to S3 → DROP
--
-- ================================================

-- ================================================
-- Phase 1: Create archive schema
-- ================================================

CREATE SCHEMA IF NOT EXISTS archive;

COMMENT ON SCHEMA archive IS 'Temporary storage for detached partitions awaiting export to external storage (S3, GCS, etc.)';

-- Grant permissions (assuming db_owner runs migrations)
GRANT USAGE ON SCHEMA archive TO idp_app_user;
GRANT SELECT ON ALL TABLES IN SCHEMA archive TO idp_app_user;

-- ================================================
-- Phase 2: Update pg_partman configuration
-- ================================================
-- Change from DROP to DETACH + move to archive schema

UPDATE partman.part_config
SET retention_keep_table = true,
    retention_schema = 'archive'
WHERE parent_table = 'public.security_event';

UPDATE partman.part_config
SET retention_keep_table = true,
    retention_schema = 'archive'
WHERE parent_table = 'public.security_event_hook_results';

-- ================================================
-- Phase 3: Create archive export stub function
-- ================================================
-- This is a stub function that does nothing by default.
-- Users should replace this with their own implementation
-- (AWS S3, GCP Cloud Storage, Azure Blob, local file, etc.)

CREATE OR REPLACE FUNCTION archive.export_partition_to_external_storage(
    p_schema_name TEXT,
    p_table_name TEXT,
    p_destination_path TEXT DEFAULT NULL
) RETURNS BOOLEAN AS $$
DECLARE
    v_row_count BIGINT;
BEGIN
    -- Get row count for logging
    EXECUTE format('SELECT COUNT(*) FROM %I.%I', p_schema_name, p_table_name)
    INTO v_row_count;

    -- ================================================
    -- STUB IMPLEMENTATION - Always returns FALSE
    -- ================================================
    -- This function is a placeholder for external storage export.
    -- Replace this implementation with your cloud-specific export logic.
    --
    -- Example implementations:
    --
    -- AWS S3 (requires aws_s3 extension):
    --   SELECT aws_s3.query_export_to_s3(
    --       format('SELECT * FROM %I.%I', p_schema_name, p_table_name),
    --       aws_commons.create_s3_uri(
    --           'your-bucket',
    --           format('archive/%s/%s.csv', p_schema_name, p_table_name),
    --           'ap-northeast-1'
    --       ),
    --       options := 'FORMAT CSV, HEADER'
    --   );
    --
    -- GCP Cloud Storage (via gcs_fdw or COPY to mounted storage):
    --   COPY (SELECT * FROM archive.table_name)
    --   TO '/mnt/gcs/archive/table_name.csv'
    --   WITH (FORMAT CSV, HEADER);
    --
    -- Local file system:
    --   COPY (SELECT * FROM archive.table_name)
    --   TO '/var/lib/postgresql/archive/table_name.csv'
    --   WITH (FORMAT CSV, HEADER);
    --
    -- ================================================

    RAISE NOTICE 'archive.export_partition_to_external_storage: STUB - table %.% has % rows (not exported)',
        p_schema_name, p_table_name, v_row_count;

    -- Return FALSE to indicate export was not performed (stub)
    -- When properly implemented, return TRUE on success
    RETURN FALSE;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION archive.export_partition_to_external_storage(TEXT, TEXT, TEXT) IS
'Stub function for exporting archived partitions to external storage.
Replace this with your cloud-specific implementation (AWS S3, GCS, Azure Blob, etc.).
Returns TRUE if export succeeded, FALSE otherwise.
See security-event-archive-guide.md for implementation examples.';

-- ================================================
-- Phase 4: Create archive processing function
-- ================================================
-- This function processes tables in the archive schema:
--   1. Attempts to export to external storage
--   2. Drops the table if export succeeds
--   3. Keeps the table if export fails (for retry)

CREATE OR REPLACE FUNCTION archive.process_archived_partitions(
    p_dry_run BOOLEAN DEFAULT FALSE
) RETURNS TABLE (
    table_name TEXT,
    row_count BIGINT,
    exported BOOLEAN,
    dropped BOOLEAN,
    message TEXT
) AS $$
DECLARE
    v_table RECORD;
    v_row_count BIGINT;
    v_export_result BOOLEAN;
    v_tables_processed INT := 0;
    v_tables_exported INT := 0;
    v_tables_dropped INT := 0;
BEGIN
    RAISE NOTICE 'Starting archive processing (dry_run=%)', p_dry_run;

    -- Find all tables in archive schema (excluding system tables)
    FOR v_table IN
        SELECT schemaname, tablename
        FROM pg_tables
        WHERE schemaname = 'archive'
        ORDER BY tablename
    LOOP
        v_tables_processed := v_tables_processed + 1;

        -- Get row count
        EXECUTE format('SELECT COUNT(*) FROM %I.%I', v_table.schemaname, v_table.tablename)
        INTO v_row_count;

        table_name := v_table.tablename;
        row_count := v_row_count;

        IF p_dry_run THEN
            -- Dry run: just report what would happen
            exported := FALSE;
            dropped := FALSE;
            message := format('DRY RUN: Would process table with %s rows', v_row_count);
            RETURN NEXT;
            CONTINUE;
        END IF;

        -- Attempt export to external storage
        BEGIN
            v_export_result := archive.export_partition_to_external_storage(
                v_table.schemaname,
                v_table.tablename
            );
        EXCEPTION WHEN OTHERS THEN
            v_export_result := FALSE;
            RAISE NOTICE 'Export failed for %.%: %', v_table.schemaname, v_table.tablename, SQLERRM;
        END;

        exported := v_export_result;

        IF v_export_result THEN
            -- Export succeeded, safe to drop
            v_tables_exported := v_tables_exported + 1;

            EXECUTE format('DROP TABLE %I.%I', v_table.schemaname, v_table.tablename);
            v_tables_dropped := v_tables_dropped + 1;

            dropped := TRUE;
            message := format('Exported and dropped (%s rows)', v_row_count);

            RAISE NOTICE 'Dropped archived table %.% after successful export',
                v_table.schemaname, v_table.tablename;
        ELSE
            -- Export failed or not implemented, keep table for retry
            dropped := FALSE;
            message := format('Export not performed, table retained (%s rows)', v_row_count);

            RAISE NOTICE 'Keeping archived table %.% (export returned false or not implemented)',
                v_table.schemaname, v_table.tablename;
        END IF;

        RETURN NEXT;
    END LOOP;

    RAISE NOTICE 'Archive processing complete: % tables processed, % exported, % dropped',
        v_tables_processed, v_tables_exported, v_tables_dropped;

    RETURN;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION archive.process_archived_partitions(BOOLEAN) IS
'Process archived partitions: attempt export to external storage and drop if successful.
Call with p_dry_run=TRUE to see what would be processed without making changes.
Tables are only dropped after successful export (export function returns TRUE).';

-- ================================================
-- Phase 5: Create helper functions for monitoring
-- ================================================

CREATE OR REPLACE FUNCTION archive.get_archive_status()
RETURNS TABLE (
    table_name TEXT,
    row_count BIGINT,
    size_bytes BIGINT,
    size_pretty TEXT,
    created_date DATE
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        t.tablename::TEXT,
        (xpath('/row/cnt/text()', xml_count))[1]::TEXT::BIGINT as row_count,
        pg_relation_size(format('%I.%I', t.schemaname, t.tablename)::regclass) as size_bytes,
        pg_size_pretty(pg_relation_size(format('%I.%I', t.schemaname, t.tablename)::regclass)) as size_pretty,
        -- Extract date from partition name (e.g., security_event_p20241201 -> 2024-12-01)
        CASE
            WHEN t.tablename ~ '_p\d{8}$' THEN
                to_date(substring(t.tablename from '_p(\d{8})$'), 'YYYYMMDD')
            ELSE NULL
        END as created_date
    FROM pg_tables t
    CROSS JOIN LATERAL (
        SELECT query_to_xml(format('SELECT COUNT(*) as cnt FROM %I.%I', t.schemaname, t.tablename), false, false, '') as xml_count
    ) x
    WHERE t.schemaname = 'archive'
    ORDER BY t.tablename;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION archive.get_archive_status() IS
'Get status of all tables in the archive schema including row count, size, and partition date.';

-- ================================================
-- Verification
-- ================================================

DO $$
DECLARE
    v_config_count INT;
BEGIN
    -- Verify pg_partman configuration updated
    SELECT COUNT(*) INTO v_config_count
    FROM partman.part_config
    WHERE retention_keep_table = true
      AND retention_schema = 'archive';

    IF v_config_count >= 2 THEN
        RAISE NOTICE 'Archive configuration verified: % tables configured for archive schema', v_config_count;
    ELSE
        RAISE WARNING 'Expected 2 tables configured for archive, found %', v_config_count;
    END IF;

    -- Verify functions created
    IF EXISTS (SELECT 1 FROM pg_proc WHERE proname = 'export_partition_to_external_storage') THEN
        RAISE NOTICE 'Function archive.export_partition_to_external_storage created';
    END IF;

    IF EXISTS (SELECT 1 FROM pg_proc WHERE proname = 'process_archived_partitions') THEN
        RAISE NOTICE 'Function archive.process_archived_partitions created';
    END IF;

    IF EXISTS (SELECT 1 FROM pg_proc WHERE proname = 'get_archive_status') THEN
        RAISE NOTICE 'Function archive.get_archive_status created';
    END IF;
END $$;

-- ================================================
-- Usage examples (for reference):
-- ================================================
--
-- Check archive status:
--   SELECT * FROM archive.get_archive_status();
--
-- Dry run archive processing:
--   SELECT * FROM archive.process_archived_partitions(p_dry_run := TRUE);
--
-- Process archives (export and drop):
--   SELECT * FROM archive.process_archived_partitions(p_dry_run := FALSE);
--
-- Check pg_partman configuration:
--   SELECT parent_table, retention, retention_keep_table, retention_schema
--   FROM partman.part_config;
--
