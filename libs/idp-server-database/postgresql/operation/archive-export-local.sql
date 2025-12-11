-- ================================================
-- Local File Export for Archive Testing
-- ================================================
-- This script replaces the stub export function with a local file export
-- implementation for testing purposes.
--
-- Usage:
--   1. Run this script to install the local export function
--   2. Ensure PostgreSQL has write access to the export directory
--   3. Run archive processing: SELECT * FROM archive.process_archived_partitions();
--
-- Note: This is for LOCAL TESTING ONLY.
-- In production, use cloud-specific implementations (AWS S3, GCS, etc.)
-- ================================================

-- ================================================
-- Configuration: Set export directory
-- ================================================
-- Default: /var/lib/postgresql/archive
-- For Docker: mount a volume to this path
-- For local dev: use a path PostgreSQL can write to

-- Create a configuration table for archive settings
CREATE TABLE IF NOT EXISTS archive.config (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL,
    description TEXT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Insert default configuration (upsert)
INSERT INTO archive.config (key, value, description)
VALUES ('export_directory', '/var/lib/postgresql/archive', 'Directory for local file exports')
ON CONFLICT (key) DO NOTHING;

COMMENT ON TABLE archive.config IS 'Configuration settings for archive functionality';

-- ================================================
-- Helper function to get config value
-- ================================================
CREATE OR REPLACE FUNCTION archive.get_config(p_key TEXT, p_default TEXT DEFAULT NULL)
RETURNS TEXT AS $$
DECLARE
    v_value TEXT;
BEGIN
    SELECT value INTO v_value FROM archive.config WHERE key = p_key;
    RETURN COALESCE(v_value, p_default);
END;
$$ LANGUAGE plpgsql;

-- ================================================
-- Local file export implementation
-- ================================================
-- Replaces the stub function with actual file export

CREATE OR REPLACE FUNCTION archive.export_partition_to_external_storage(
    p_schema_name TEXT,
    p_table_name TEXT,
    p_destination_path TEXT DEFAULT NULL
) RETURNS BOOLEAN AS $$
DECLARE
    v_row_count BIGINT;
    v_export_dir TEXT;
    v_file_path TEXT;
BEGIN
    -- Get row count
    EXECUTE format('SELECT COUNT(*) FROM %I.%I', p_schema_name, p_table_name)
    INTO v_row_count;

    IF v_row_count = 0 THEN
        RAISE NOTICE 'Table %.% is empty, skipping export', p_schema_name, p_table_name;
        RETURN TRUE;  -- Consider empty tables as successfully exported
    END IF;

    -- Skip non-partition tables (e.g., config table)
    IF p_table_name !~ '_p\d{8}$' THEN
        RAISE NOTICE 'Table %.% is not a partition table, skipping', p_schema_name, p_table_name;
        RETURN FALSE;  -- Not exported, but not an error
    END IF;

    -- Get export directory from config
    v_export_dir := archive.get_config('export_directory', '/var/lib/postgresql/archive');

    -- Build file path: export_dir/table_name.csv (flat structure for local testing)
    -- PostgreSQL COPY TO cannot create directories, so use flat structure
    v_file_path := v_export_dir || '/' || p_table_name || '.csv';

    -- Use custom destination if provided
    IF p_destination_path IS NOT NULL THEN
        v_file_path := p_destination_path;
    END IF;

    RAISE NOTICE 'Exporting %.% (% rows) to %',
        p_schema_name, p_table_name, v_row_count, v_file_path;

    -- Export to CSV file
    BEGIN
        EXECUTE format(
            'COPY (SELECT * FROM %I.%I) TO %L WITH (FORMAT CSV, HEADER)',
            p_schema_name, p_table_name, v_file_path
        );
    EXCEPTION WHEN OTHERS THEN
        RAISE WARNING 'Failed to export %.% to %: %',
            p_schema_name, p_table_name, v_file_path, SQLERRM;
        RETURN FALSE;
    END;

    RAISE NOTICE 'Successfully exported %.% to %', p_schema_name, p_table_name, v_file_path;
    RETURN TRUE;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION archive.export_partition_to_external_storage(TEXT, TEXT, TEXT) IS
'Local file export implementation for testing.
Exports archived partition to CSV file in the configured export directory.
Directory structure: {export_dir}/{table_type}/{year}/{month}/{day}/{table_name}.csv
Configure export directory: UPDATE archive.config SET value = ''/path/to/dir'' WHERE key = ''export_directory'';';

-- ================================================
-- Verification
-- ================================================
DO $$
DECLARE
    v_export_dir TEXT;
BEGIN
    v_export_dir := archive.get_config('export_directory');
    RAISE NOTICE 'Local archive export configured. Export directory: %', v_export_dir;
    RAISE NOTICE 'To change: UPDATE archive.config SET value = ''/your/path'' WHERE key = ''export_directory'';';
END $$;

-- ================================================
-- Usage examples:
-- ================================================
--
-- Set export directory (for local development):
--   UPDATE archive.config
--   SET value = '/Users/yourname/work/idp-server/logs/archive'
--   WHERE key = 'export_directory';
--
-- For Docker, mount a volume:
--   volumes:
--     - ./logs/archive:/var/lib/postgresql/archive
--
-- Test export manually:
--   SELECT archive.export_partition_to_external_storage('archive', 'security_event_p20241201');
--
-- Process all archived partitions:
--   SELECT * FROM archive.process_archived_partitions(p_dry_run := FALSE);
--
-- Check configuration:
--   SELECT * FROM archive.config;
--
