#!/bin/bash
# ================================================
# Setup Local Archive Export for Testing
# ================================================
# This script configures PostgreSQL to export archived partitions
# to the local logs/archive directory.
#
# Usage:
#   ./scripts/archive/setup-local-export.sh
#
# Prerequisites:
#   - PostgreSQL running with idp-server database
#   - V0_9_21_3__archive_support.sql migration applied
# ================================================

set -e

# Configuration
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
EXPORT_DIR="$PROJECT_ROOT/logs/archive"

# Load .env if exists
if [ -f "$PROJECT_ROOT/.env" ]; then
    export $(grep -v '^#' "$PROJECT_ROOT/.env" | xargs)
fi

# Database connection (adjust as needed)
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-idpserver}"
DB_USER="${DB_USER:-idp}"
DB_PASSWORD="${DB_PASSWORD:-${DB_OWNER_PASSWORD:-password}}"

echo "================================================"
echo "Setting up Local Archive Export"
echo "================================================"
echo "Project root: $PROJECT_ROOT"
echo "Export directory: $EXPORT_DIR"
echo "Database: $DB_NAME@$DB_HOST:$DB_PORT"
echo ""

# Create export directory structure
echo "Creating export directory structure..."
mkdir -p "$EXPORT_DIR/security_event"
mkdir -p "$EXPORT_DIR/security_event_hook_results"

# Set permissions (PostgreSQL needs write access)
chmod -R 777 "$EXPORT_DIR"
echo "Created: $EXPORT_DIR"

# Apply local export SQL
echo ""
echo "Applying local export configuration to PostgreSQL..."
PGPASSWORD="$DB_PASSWORD" psql \
    -h "$DB_HOST" \
    -p "$DB_PORT" \
    -U "$DB_USER" \
    -d "$DB_NAME" \
    -f "$PROJECT_ROOT/libs/idp-server-database/postgresql/operation/archive-export-local.sql"

# Update export directory in config
echo ""
echo "Setting export directory to: $EXPORT_DIR"
PGPASSWORD="$DB_PASSWORD" psql \
    -h "$DB_HOST" \
    -p "$DB_PORT" \
    -U "$DB_USER" \
    -d "$DB_NAME" \
    -c "UPDATE archive.config SET value = '$EXPORT_DIR', updated_at = CURRENT_TIMESTAMP WHERE key = 'export_directory';"

# Update retention to 1 day for local testing
echo ""
echo "Setting retention to 1 day for local testing..."
PGPASSWORD="$DB_PASSWORD" psql \
    -h "$DB_HOST" \
    -p "$DB_PORT" \
    -U "$DB_USER" \
    -d "$DB_NAME" \
    -c "UPDATE partman.part_config SET retention = '1 day' WHERE parent_table IN ('public.security_event', 'public.security_event_hook_results');"

# Update pg_cron schedule to every 5 minutes for local testing
echo ""
echo "Setting pg_cron jobs to run every 5 minutes..."
PGPASSWORD="$DB_PASSWORD" psql \
    -h "$DB_HOST" \
    -p "$DB_PORT" \
    -U "$DB_USER" \
    -d "$DB_NAME" \
    -c "UPDATE cron.job SET schedule = '0,5,10,15,20,25,30,35,40,45,50,55 * * * *' WHERE jobname IN ('partman-maintenance', 'archive-processing');"

# Verify configuration
echo ""
echo "Verifying configuration..."
PGPASSWORD="$DB_PASSWORD" psql \
    -h "$DB_HOST" \
    -p "$DB_PORT" \
    -U "$DB_USER" \
    -d "$DB_NAME" \
    -c "SELECT * FROM archive.config;"

echo ""
echo "pg_partman retention settings:"
PGPASSWORD="$DB_PASSWORD" psql \
    -h "$DB_HOST" \
    -p "$DB_PORT" \
    -U "$DB_USER" \
    -d "$DB_NAME" \
    -c "SELECT parent_table, retention, retention_keep_table, retention_schema FROM partman.part_config;"

echo ""
echo "pg_cron job schedule:"
PGPASSWORD="$DB_PASSWORD" psql \
    -h "$DB_HOST" \
    -p "$DB_PORT" \
    -U "$DB_USER" \
    -d "$DB_NAME" \
    -c "SELECT jobname, schedule, active FROM cron.job WHERE jobname IN ('partman-maintenance', 'archive-processing');"

echo ""
echo "================================================"
echo "Setup Complete!"
echo "================================================"
echo ""
echo "Next steps:"
echo "  1. Check archive status:"
echo "     SELECT * FROM archive.get_archive_status();"
echo ""
echo "  2. Dry run archive processing:"
echo "     SELECT * FROM archive.process_archived_partitions(p_dry_run := TRUE);"
echo ""
echo "  3. Process archives (export and drop):"
echo "     SELECT * FROM archive.process_archived_partitions(p_dry_run := FALSE);"
echo ""
echo "  4. Check exported files:"
echo "     ls -la $EXPORT_DIR/"
echo ""
