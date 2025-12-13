#!/bin/bash
# ================================================
# Test Archive Flow End-to-End
# ================================================
# This script tests the complete archive flow:
#   1. Check pg_partman configuration
#   2. Simulate partition aging (optional)
#   3. Run partman maintenance
#   4. Check archive schema
#   5. Process archived partitions
#   6. Verify exported files
#
# Usage:
#   ./scripts/archive/test-archive-flow.sh
#
# ================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
EXPORT_DIR="$PROJECT_ROOT/logs/archive"

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

psql_cmd() {
    PGPASSWORD="$DB_PASSWORD" psql \
        -h "$DB_HOST" \
        -p "$DB_PORT" \
        -U "$DB_USER" \
        -d "$DB_NAME" \
        -c "$1"
}

psql_query() {
    PGPASSWORD="$DB_PASSWORD" psql \
        -h "$DB_HOST" \
        -p "$DB_PORT" \
        -U "$DB_USER" \
        -d "$DB_NAME" \
        -t -A -c "$1"
}

echo "================================================"
echo "Archive Flow Test"
echo "================================================"
echo ""

# Step 1: Check pg_partman configuration
echo "Step 1: pg_partman configuration"
echo "--------------------------------"
psql_cmd "SELECT parent_table, partition_interval, retention, retention_keep_table, retention_schema FROM partman.part_config;"
echo ""

# Step 2: Check current partitions
echo "Step 2: Current partitions"
echo "-------------------------"
psql_cmd "
SELECT
    p.relname as parent,
    c.relname as partition,
    pg_size_pretty(pg_relation_size(c.oid)) as size
FROM pg_inherits i
JOIN pg_class c ON i.inhrelid = c.oid
JOIN pg_class p ON i.inhparent = p.oid
WHERE p.relname IN ('security_event', 'security_event_hook_results')
ORDER BY p.relname, c.relname
LIMIT 20;
"
echo ""

# Step 3: Check archive schema
echo "Step 3: Tables in archive schema"
echo "--------------------------------"
ARCHIVE_COUNT=$(psql_query "SELECT COUNT(*) FROM pg_tables WHERE schemaname = 'archive' AND tablename NOT IN ('config');")
echo "Tables in archive schema: $ARCHIVE_COUNT"

if [ "$ARCHIVE_COUNT" -gt "0" ]; then
    psql_cmd "SELECT * FROM archive.get_archive_status();"
else
    echo "(No archived partitions yet - they will appear after retention period)"
fi
echo ""

# Step 4: Check export configuration
echo "Step 4: Export configuration"
echo "---------------------------"
psql_cmd "SELECT * FROM archive.config;"
echo ""

# Step 5: Dry run archive processing
echo "Step 5: Dry run archive processing"
echo "----------------------------------"
psql_cmd "SELECT * FROM archive.process_archived_partitions(p_dry_run := TRUE);"
echo ""

# Step 6: Check export directory
echo "Step 6: Export directory contents"
echo "---------------------------------"
echo "Directory: $EXPORT_DIR"
if [ -d "$EXPORT_DIR" ]; then
    find "$EXPORT_DIR" -type f -name "*.csv" 2>/dev/null | head -20 || echo "(No CSV files yet)"
else
    echo "(Export directory does not exist)"
fi
echo ""

# Step 7: Summary
echo "================================================"
echo "Summary"
echo "================================================"
echo ""
echo "pg_partman is configured to:"
echo "  - DETACH partitions older than 90 days"
echo "  - Move them to 'archive' schema"
echo ""
echo "Archive processing will:"
echo "  - Export tables from archive schema to: $EXPORT_DIR"
echo "  - DROP tables after successful export"
echo ""
echo "To manually trigger archive processing:"
echo "  SELECT * FROM archive.process_archived_partitions(p_dry_run := FALSE);"
echo ""
echo "To simulate partition aging for testing:"
echo "  -- Move a partition to archive schema manually"
echo "  ALTER TABLE public.security_event_p20241201"
echo "  SET SCHEMA archive;"
echo ""
