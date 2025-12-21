#!/bin/bash
# ============================================================================
# migrate-authentication-devices-mysql.sh
# Issue #964: Authentication device data migration script (MySQL)
#
# This script migrates data from idp_user.authentication_devices (JSON)
# to idp_user_authentication_devices table.
#
# Table creation is handled by Flyway (V0_10_1).
# This script is for:
#   - Pre-migration testing (before Flyway)
#   - Data verification
#   - Differential sync after rolling deployment
#
# Usage:
#   ./scripts/migration/migrate-authentication-devices-mysql.sh [OPTIONS]
#
# Options:
#   --dry-run       Show what would be done without making changes
#   --verify        Only verify data consistency
#   --sync          Sync missing records (for rolling deployment)
#   --help          Show this help message
#
# Environment variables:
#   DB_HOST         Database host (default: localhost)
#   DB_PORT         Database port (default: 3306)
#   DB_NAME         Database name (default: idp)
#   DB_USER         Database user (default: idp_owner)
#   DB_PASSWORD     Database password (required)
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

# Defaults (use DB_OWNER_* from .env)
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-idpserver}"
DB_USER="${DB_USER:-${DB_OWNER_USER:-idp}}"
DB_PASSWORD="${DB_PASSWORD:-${DB_OWNER_PASSWORD:-}}"

# Options
DRY_RUN=false
VERIFY_ONLY=false
SYNC_MODE=false

show_help() {
    cat << EOF
Usage: $0 [OPTIONS]

Authentication device data migration script (MySQL)
Issue #964: Migrate JSON data to normalized table

Options:
  --dry-run    Show what would be done without making changes
  --verify     Only verify data consistency
  --sync       Sync missing records (idempotent, for rolling deployment)
  --help       Show this help message

Environment variables:
  DB_HOST       Database host (default: localhost)
  DB_PORT       Database port (default: 3306)
  DB_NAME       Database name (default: idp)
  DB_USER       Database user (default: idp_owner)
  DB_PASSWORD   Database password (required)

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
    mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USER" -p"$DB_PASSWORD" -D "$DB_NAME" -N -B -e "$1"
}

# Check if table exists
table_exists() {
    local result
    result=$(run_sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$DB_NAME' AND table_name = 'idp_user_authentication_devices';")
    [[ "$result" == "1" ]]
}

# Get counts
get_json_device_count() {
    run_sql "SELECT COALESCE(SUM(JSON_LENGTH(authentication_devices)), 0) FROM idp_user WHERE authentication_devices IS NOT NULL AND JSON_LENGTH(authentication_devices) > 0;"
}

get_table_device_count() {
    run_sql "SELECT COUNT(*) FROM idp_user_authentication_devices;"
}

# Verify data consistency
verify_data() {
    log_info "Verifying data consistency..."

    local json_count=$(get_json_device_count)
    local table_count=$(get_table_device_count)
    local diff=$((json_count - table_count))

    echo ""
    echo "┌────────────────────────────────────────┐"
    echo "│       Data Consistency Report          │"
    echo "├────────────────────────────────────────┤"
    printf "│ %-24s %12s │\n" "JSON devices:" "$json_count"
    printf "│ %-24s %12s │\n" "Table devices:" "$table_count"
    printf "│ %-24s %12s │\n" "Difference:" "$diff"
    echo "└────────────────────────────────────────┘"
    echo ""

    if [[ "$diff" == "0" ]]; then
        log_success "All data is synchronized"
        return 0
    else
        log_warn "$diff devices need to be migrated"
        return 1
    fi
}

# Migrate/sync data using stored procedure
migrate_data() {
    local json_count=$(get_json_device_count)
    local table_count=$(get_table_device_count)
    local diff=$((json_count - table_count))

    if [[ "$diff" == "0" ]]; then
        log_success "No data to migrate"
        return 0
    fi

    log_info "Migrating approximately $diff devices..."

    if $DRY_RUN; then
        log_info "[DRY-RUN] Would migrate $diff devices"
        return 0
    fi

    # Create stored procedure for migration
    run_sql "DROP PROCEDURE IF EXISTS migrate_auth_devices_sync;"

    run_sql "
        CREATE PROCEDURE migrate_auth_devices_sync()
        BEGIN
            DECLARE done INT DEFAULT FALSE;
            DECLARE v_tenant_id CHAR(36);
            DECLARE v_user_id CHAR(36);
            DECLARE v_auth_devices JSON;
            DECLARE v_created_at DATETIME;
            DECLARE v_updated_at DATETIME;
            DECLARE v_device JSON;
            DECLARE v_device_count INT;
            DECLARE v_idx INT;
            DECLARE v_device_id CHAR(36);

            DECLARE cur CURSOR FOR
                SELECT tenant_id, id, authentication_devices, created_at, updated_at
                FROM idp_user
                WHERE authentication_devices IS NOT NULL
                AND JSON_LENGTH(authentication_devices) > 0;

            DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;
            DECLARE CONTINUE HANDLER FOR 1062 BEGIN END;

            OPEN cur;

            read_loop: LOOP
                FETCH cur INTO v_tenant_id, v_user_id, v_auth_devices, v_created_at, v_updated_at;
                IF done THEN
                    LEAVE read_loop;
                END IF;

                SET v_device_count = JSON_LENGTH(v_auth_devices);
                SET v_idx = 0;

                WHILE v_idx < v_device_count DO
                    SET v_device = JSON_EXTRACT(v_auth_devices, CONCAT('\$[', v_idx, ']'));
                    SET v_device_id = JSON_UNQUOTE(JSON_EXTRACT(v_device, '\$.id'));

                    INSERT IGNORE INTO idp_user_authentication_devices (
                        id, tenant_id, user_id, os, model, platform, locale, app_name,
                        priority, available_methods, notification_token, notification_channel,
                        created_at, updated_at
                    ) VALUES (
                        v_device_id,
                        v_tenant_id,
                        v_user_id,
                        JSON_UNQUOTE(JSON_EXTRACT(v_device, '\$.os')),
                        JSON_UNQUOTE(JSON_EXTRACT(v_device, '\$.model')),
                        JSON_UNQUOTE(JSON_EXTRACT(v_device, '\$.platform')),
                        JSON_UNQUOTE(JSON_EXTRACT(v_device, '\$.locale')),
                        JSON_UNQUOTE(JSON_EXTRACT(v_device, '\$.app_name')),
                        COALESCE(JSON_EXTRACT(v_device, '\$.priority'), 1),
                        COALESCE(JSON_EXTRACT(v_device, '\$.available_methods'), JSON_ARRAY()),
                        JSON_UNQUOTE(JSON_EXTRACT(v_device, '\$.notification_token')),
                        JSON_UNQUOTE(JSON_EXTRACT(v_device, '\$.notification_channel')),
                        v_created_at,
                        v_updated_at
                    );

                    SET v_idx = v_idx + 1;
                END WHILE;
            END LOOP;

            CLOSE cur;
        END;
    "

    run_sql "CALL migrate_auth_devices_sync();"
    run_sql "DROP PROCEDURE IF EXISTS migrate_auth_devices_sync;"

    local new_count=$(get_table_device_count)
    local migrated=$((new_count - table_count))
    log_success "Migrated $migrated devices"
}

# Main
main() {
    echo ""
    echo "=========================================="
    echo " Authentication Device Data Migration"
    echo " MySQL - Issue #964"
    echo "=========================================="
    echo ""

    if [[ -z "${DB_PASSWORD:-}" ]]; then
        log_error "DB_PASSWORD is required"
        exit 1
    fi

    log_info "Database: $DB_HOST:$DB_PORT/$DB_NAME"
    log_info "User: $DB_USER"
    $DRY_RUN && log_warn "DRY-RUN mode"
    echo ""

    if ! table_exists; then
        log_error "Table idp_user_authentication_devices does not exist"
        log_error "Run Flyway migration first (V0_10_1)"
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
