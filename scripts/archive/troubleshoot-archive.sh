#!/bin/bash
# ================================================
# Troubleshoot Archive Processing Failures
# ================================================
# This script helps diagnose issues with pg_partman
# archive processing.
#
# Usage:
#   ./scripts/archive/troubleshoot-archive.sh
#
# Common issues:
#   1. Permission denied (table ownership)
#   2. Missing partitions
#   3. pg_cron job failures
#   4. Export function errors
# ================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Load .env if exists
if [ -f "$PROJECT_ROOT/.env" ]; then
    export $(grep -v '^#' "$PROJECT_ROOT/.env" | xargs)
fi

# Database connection
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-idpserver}"
DB_USER="${DB_USER:-idp}"
DB_PASSWORD="${DB_PASSWORD:-${DB_OWNER_PASSWORD:-password}}"

psql_query() {
    PGPASSWORD="$DB_PASSWORD" psql \
        -h "$DB_HOST" \
        -p "$DB_PORT" \
        -U "$DB_USER" \
        -d "$DB_NAME" \
        -c "$1"
}

echo "================================================"
echo "Archive Processing Troubleshooting"
echo "================================================"
echo "Database: $DB_NAME@$DB_HOST:$DB_PORT"
echo "User: $DB_USER"
echo ""

# ================================================
# Step 1: Check table ownership
# ================================================
echo "Step 1: Table Ownership"
echo "------------------------"
echo "Parent tables and partitions must be owned by the same user"
echo "who runs partman.run_maintenance_proc()"
echo ""

psql_query "
SELECT
    schemaname,
    tablename,
    tableowner
FROM pg_tables
WHERE tablename LIKE 'security_event%'
   OR tablename LIKE 'security_event_hook_results%'
ORDER BY schemaname, tablename
LIMIT 20;
"

echo ""
echo "FIX: If ownership is inconsistent, run as superuser:"
echo "  ALTER TABLE security_event OWNER TO <owner>;"
echo "  ALTER TABLE security_event_p20241201 OWNER TO <owner>;"
echo ""

# ================================================
# Step 2: Check pg_partman configuration
# ================================================
echo "Step 2: pg_partman Configuration"
echo "---------------------------------"
psql_query "
SELECT
    parent_table,
    partition_interval,
    retention,
    retention_keep_table,
    retention_schema,
    premake
FROM partman.part_config;
"

echo ""
echo "Expected values for archive:"
echo "  retention_keep_table = true"
echo "  retention_schema = 'archive'"
echo ""

# ================================================
# Step 3: Check partition status
# ================================================
echo "Step 3: Partition Status"
echo "------------------------"
echo "Partitions older than retention period (90 days):"
echo ""

psql_query "
SELECT
    c.relname as partition,
    pg_get_expr(c.relpartbound, c.oid) as range,
    pg_size_pretty(pg_relation_size(c.oid)) as size,
    t.tableowner as owner
FROM pg_inherits i
JOIN pg_class c ON i.inhrelid = c.oid
JOIN pg_class p ON i.inhparent = p.oid
JOIN pg_tables t ON t.tablename = c.relname
WHERE p.relname = 'security_event'
ORDER BY c.relname
LIMIT 20;
"

# ================================================
# Step 4: Check default partition
# ================================================
echo ""
echo "Step 4: Default Partition (orphaned data)"
echo "------------------------------------------"
psql_query "SELECT * FROM partman.check_default();"

echo ""
echo "FIX: If there's data in default partition, redistribute:"
echo "  SELECT partman.partition_data_time("
echo "      p_parent_table := 'public.security_event',"
echo "      p_batch_count := 1000"
echo "  );"
echo ""

# ================================================
# Step 5: Check archive schema
# ================================================
echo "Step 5: Archive Schema Status"
echo "-----------------------------"

psql_query "
SELECT
    schemaname,
    tablename,
    tableowner
FROM pg_tables
WHERE schemaname = 'archive'
ORDER BY tablename;
"

# ================================================
# Step 6: Check pg_cron job status
# ================================================
echo ""
echo "Step 6: pg_cron Jobs"
echo "--------------------"
psql_query "
SELECT
    jobid,
    jobname,
    schedule,
    username,
    active,
    command
FROM cron.job
WHERE jobname IN ('partman-maintenance', 'archive-processing');
"

echo ""
echo "Recent job executions:"
psql_query "
SELECT
    jobid,
    runid,
    status,
    return_message,
    start_time,
    end_time
FROM cron.job_run_details
ORDER BY start_time DESC
LIMIT 10;
"

# ================================================
# Step 7: Check permissions
# ================================================
echo ""
echo "Step 7: Permission Check"
echo "------------------------"
echo "Checking if current user can manage partitions..."

psql_query "
SELECT
    has_table_privilege(current_user, 'security_event', 'SELECT') as can_select,
    has_table_privilege(current_user, 'security_event', 'INSERT') as can_insert,
    has_table_privilege(current_user, 'security_event', 'DELETE') as can_delete;
"

psql_query "
SELECT
    has_schema_privilege(current_user, 'archive', 'CREATE') as can_create_in_archive,
    has_schema_privilege(current_user, 'partman', 'USAGE') as can_use_partman;
"

# ================================================
# Summary
# ================================================
echo ""
echo "================================================"
echo "Common Fixes"
echo "================================================"
echo ""
echo "1. PERMISSION DENIED (must be owner of table):"
echo "   - Run maintenance as table owner (usually 'idp' or 'db_owner')"
echo "   - Or change table ownership:"
echo "     ALTER TABLE security_event OWNER TO idp_admin_user;"
echo ""
echo "2. PARTITIONS NOT MOVING TO ARCHIVE:"
echo "   - Verify retention_keep_table = true"
echo "   - Verify retention_schema = 'archive'"
echo "   - Check retention period (default 90 days)"
echo ""
echo "3. pg_cron JOB FAILING:"
echo "   - Check job is registered with correct user"
echo "   - Re-register job as table owner:"
echo "     SELECT cron.schedule('partman-maintenance', '0 2 * * *',"
echo "         '\$\$CALL partman.run_maintenance_proc()\$\$');"
echo ""
echo "4. DATA IN DEFAULT PARTITION:"
echo "   - Run partition_data_time() to redistribute"
echo ""
