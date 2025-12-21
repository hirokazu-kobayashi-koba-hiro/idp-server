#!/bin/bash
# ============================================================================
# migrate-authentication-devices.sh
# Issue #964: Authentication device data migration script (PostgreSQL)
#
# This script migrates data from idp_user.authentication_devices (JSONB)
# to idp_user_authentication_devices table.
#
# Table creation is handled by Flyway (V0_9_21_4).
# This script is for:
#   - Pre-migration testing (before Flyway)
#   - Data verification
#   - Differential sync after rolling deployment
#
# Usage:
#   ./scripts/migration/migrate-authentication-devices.sh [OPTIONS]
#
# Options:
#   --dry-run       Show what would be done without making changes
#   --verify        Only verify data consistency
#   --sync          Sync missing records (for rolling deployment)
#   --help          Show this help message
#
# Environment variables:
#   DB_HOST           Database host (default: localhost)
#   DB_PORT           Database port (default: 5432)
#   DB_NAME           Database name (default: idp)
#   DB_OWNER_USER     Database owner user (default: idp_owner)
#   DB_OWNER_PASSWORD Database owner password (required)
# ============================================================================

set -euo pipefail

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Load .env if exists
if [[ -f "$PROJECT_ROOT/.env" ]]; then
    set -a
    source "$PROJECT_ROOT/.env"
    set +a
fi

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Defaults
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-idpserver}"
DB_OWNER_USER="${DB_OWNER_USER:-idp}"

# Options
DRY_RUN=false
VERIFY_ONLY=false
SYNC_MODE=false

show_help() {
    cat << EOF
Usage: $0 [OPTIONS]

Authentication device data migration script (PostgreSQL)
Issue #964: Migrate JSONB data to normalized table

Options:
  --dry-run    Show what would be done without making changes
  --verify     Only verify data consistency
  --sync       Sync missing records (idempotent, for rolling deployment)
  --help       Show this help message

Environment variables:
  DB_HOST           Database host (default: localhost)
  DB_PORT           Database port (default: 5432)
  DB_NAME           Database name (default: idp)
  DB_OWNER_USER     Database owner user (default: idp_owner)
  DB_OWNER_PASSWORD Database owner password (required)

Examples:
  # Verify data consistency
  $0 --verify

  # Dry run migration
  $0 --dry-run

  # Migrate all data (idempotent)
  $0 --sync

  # Same as --sync
  $0
EOF
    exit 0
}

while [[ $# -gt 0 ]]; do
    case $1 in
        --dry-run)   DRY_RUN=true; shift ;;
        --verify)    VERIFY_ONLY=true; shift ;;
        --sync)      SYNC_MODE=true; shift ;;
        --help|-h)   show_help ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

log_info()    { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[OK]${NC} $1"; }
log_warn()    { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error()   { echo -e "${RED}[ERROR]${NC} $1"; }

run_sql() {
    PGPASSWORD="${DB_OWNER_PASSWORD}" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_OWNER_USER" -d "$DB_NAME" -t -A -c "$1"
}

# Check if table exists
table_exists() {
    local result
    result=$(run_sql "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'idp_user_authentication_devices');")
    [[ "$result" == "t" ]]
}

# Get counts
get_jsonb_device_count() {
    run_sql "SELECT COALESCE(SUM(jsonb_array_length(authentication_devices)), 0) FROM idp_user WHERE authentication_devices IS NOT NULL AND authentication_devices != '[]'::jsonb;"
}

get_table_device_count() {
    run_sql "SELECT COUNT(*) FROM idp_user_authentication_devices;"
}

get_missing_count() {
    run_sql "
        SELECT COUNT(*)
        FROM idp_user u,
             jsonb_array_elements(u.authentication_devices) AS device
        WHERE u.authentication_devices IS NOT NULL
          AND u.authentication_devices != '[]'::jsonb
          AND NOT EXISTS (
              SELECT 1 FROM idp_user_authentication_devices d
              WHERE d.id = (device->>'id')::uuid
          );
    "
}

# Verify data consistency
verify_data() {
    log_info "Verifying data consistency..."

    local jsonb_count=$(get_jsonb_device_count)
    local table_count=$(get_table_device_count)
    local missing_count=$(get_missing_count)

    echo ""
    echo "┌────────────────────────────────────────┐"
    echo "│       Data Consistency Report          │"
    echo "├────────────────────────────────────────┤"
    printf "│ %-24s %12s │\n" "JSONB devices:" "$jsonb_count"
    printf "│ %-24s %12s │\n" "Table devices:" "$table_count"
    printf "│ %-24s %12s │\n" "Missing in table:" "$missing_count"
    echo "└────────────────────────────────────────┘"
    echo ""

    if [[ "$missing_count" == "0" ]]; then
        log_success "All data is synchronized"
        return 0
    else
        log_warn "$missing_count devices need to be migrated"
        return 1
    fi
}

# Migrate/sync data
migrate_data() {
    local missing_count=$(get_missing_count)

    if [[ "$missing_count" == "0" ]]; then
        log_success "No data to migrate"
        return 0
    fi

    log_info "Migrating $missing_count devices..."

    if $DRY_RUN; then
        log_info "[DRY-RUN] Would migrate $missing_count devices"
        return 0
    fi

    local migrated
    migrated=$(run_sql "
        WITH inserted AS (
            INSERT INTO idp_user_authentication_devices (
                id, tenant_id, user_id, os, model, platform, locale, app_name,
                priority, available_methods, notification_token, notification_channel,
                created_at, updated_at
            )
            SELECT
                (device->>'id')::uuid,
                u.tenant_id,
                u.id,
                device->>'os',
                device->>'model',
                device->>'platform',
                device->>'locale',
                device->>'app_name',
                COALESCE((device->>'priority')::integer, 1),
                COALESCE(device->'available_methods', '[]'::jsonb),
                device->>'notification_token',
                device->>'notification_channel',
                u.created_at,
                u.updated_at
            FROM idp_user u,
                 jsonb_array_elements(u.authentication_devices) AS device
            WHERE u.authentication_devices IS NOT NULL
              AND u.authentication_devices != '[]'::jsonb
              AND NOT EXISTS (
                  SELECT 1 FROM idp_user_authentication_devices d
                  WHERE d.id = (device->>'id')::uuid
              )
            ON CONFLICT (id) DO NOTHING
            RETURNING 1
        )
        SELECT COUNT(*) FROM inserted;
    ")

    log_success "Migrated $migrated devices"
}

# Main
main() {
    echo ""
    echo "=========================================="
    echo " Authentication Device Data Migration"
    echo " PostgreSQL - Issue #964"
    echo "=========================================="
    echo ""

    if [[ -z "${DB_OWNER_PASSWORD:-}" ]]; then
        log_error "DB_OWNER_PASSWORD is required"
        exit 1
    fi

    log_info "Database: $DB_HOST:$DB_PORT/$DB_NAME"
    log_info "User: $DB_OWNER_USER"
    $DRY_RUN && log_warn "DRY-RUN mode"
    echo ""

    if ! table_exists; then
        log_error "Table idp_user_authentication_devices does not exist"
        log_error "Run Flyway migration first (V0_9_21_4)"
        exit 1
    fi

    if $VERIFY_ONLY; then
        verify_data
        exit $?
    fi

    migrate_data
    verify_data

    echo ""
    log_success "Done!"
}

main "$@"
