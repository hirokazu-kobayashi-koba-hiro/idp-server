#!/bin/bash
# ================================================
# Setup Local Archive Export for Testing
# ================================================
# This script configures PostgreSQL to export archived partitions
# for local testing. It sets shorter retention periods and more
# frequent pg_cron schedules.
#
# Usage:
#   ./scripts/archive/setup-local-export.sh
#
# Prerequisites:
#   - PostgreSQL running with idp-server database (Docker)
#   - V0_9_21_3__archive_support.sql migration applied
#
# Note:
#   Export directory is /var/lib/postgresql/data/archive inside
#   the Docker container. This is created by 02-init-partman.sh.
# ================================================

set -e

# Configuration
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Docker container internal path (not host path)
EXPORT_DIR="/var/lib/postgresql/data/archive"

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
echo "Export directory (container): $EXPORT_DIR"
echo "Database: $DB_NAME@$DB_HOST:$DB_PORT"
echo ""

# Apply local export SQL
echo "Applying local export configuration to PostgreSQL..."
PGPASSWORD="$DB_PASSWORD" psql \
    -h "$DB_HOST" \
    -p "$DB_PORT" \
    -U "$DB_USER" \
    -d "$DB_NAME" \
    -f "$PROJECT_ROOT/libs/idp-server-database/postgresql/operation/archive-export-local.sql"

# Verify export directory in config (should already be set correctly by migration)
echo ""
echo "Verifying export directory is set to: $EXPORT_DIR"
PGPASSWORD="$DB_PASSWORD" psql \
    -h "$DB_HOST" \
    -p "$DB_PORT" \
    -U "$DB_USER" \
    -d "$DB_NAME" \
    -c "SELECT * FROM archive.config WHERE key = 'export_directory';"

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
# Note: pg_cron is installed in postgres database (cross-database mode)
echo ""
echo "Setting pg_cron jobs to run every 5 minutes..."
PGPASSWORD="$DB_PASSWORD" psql \
    -h "$DB_HOST" \
    -p "$DB_PORT" \
    -U "$DB_USER" \
    -d "postgres" \
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
echo "pg_cron job schedule (from postgres database):"
PGPASSWORD="$DB_PASSWORD" psql \
    -h "$DB_HOST" \
    -p "$DB_PORT" \
    -U "$DB_USER" \
    -d "postgres" \
    -c "SELECT jobname, schedule, database, active FROM cron.job WHERE jobname IN ('partman-maintenance', 'archive-processing');"

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
echo "  4. Check exported files (inside Docker container):"
echo "     docker exec postgres-primary ls -la $EXPORT_DIR/"
echo ""
