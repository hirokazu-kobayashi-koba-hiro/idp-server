#!/bin/bash
# ================================================
# Generate Test Data for Archive Testing
# ================================================
# This script creates security event data with past dates
# to simulate partitions that would be archived.
#
# Usage:
#   ./scripts/archive/generate-test-data.sh [days_ago] [event_count]
#
# Examples:
#   ./scripts/archive/generate-test-data.sh           # Default: 95 days ago, 100 events
#   ./scripts/archive/generate-test-data.sh 100 50    # 100 days ago, 50 events
#   ./scripts/archive/generate-test-data.sh 91 200    # 91 days ago, 200 events
#
# Note: Retention is 90 days, so data >= 91 days old will be archived
# ================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Load .env if exists
if [ -f "$PROJECT_ROOT/.env" ]; then
    export $(grep -v '^#' "$PROJECT_ROOT/.env" | xargs)
fi

# Parameters
DAYS_AGO="${1:-95}"
EVENT_COUNT="${2:-100}"

# Database connection
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-idpserver}"
DB_USER="${DB_USER:-idp}"
DB_PASSWORD="${DB_PASSWORD:-${DB_OWNER_PASSWORD:-password}}"

echo "================================================"
echo "Generate Test Data for Archive Testing"
echo "================================================"
echo "Days ago: $DAYS_AGO"
echo "Event count: $EVENT_COUNT"
echo "Database: $DB_NAME@$DB_HOST:$DB_PORT"
echo ""

# Calculate target date
TARGET_DATE=$(date -v-${DAYS_AGO}d +%Y-%m-%d 2>/dev/null || date -d "-${DAYS_AGO} days" +%Y-%m-%d)
echo "Target date: $TARGET_DATE"
echo ""

# Generate test data SQL
SQL=$(cat <<EOF
-- Generate test security events for archive testing
-- Target date: $TARGET_DATE ($DAYS_AGO days ago)

DO \$\$
DECLARE
    v_tenant_id UUID := '00000000-0000-0000-0000-000000000001';
    v_tenant_name TEXT := 'test-tenant';
    v_client_id TEXT := 'test-client';
    v_client_name TEXT := 'Test Client';
    v_target_date DATE := '$TARGET_DATE'::DATE;
    v_event_types TEXT[] := ARRAY['login_success', 'issue_token_success', 'refresh_token_success', 'logout_success'];
    v_event_type TEXT;
    v_user_id UUID;
    v_created_at TIMESTAMP;
    v_inserted INT := 0;
BEGIN
    RAISE NOTICE 'Generating % events for date %', $EVENT_COUNT, v_target_date;

    FOR i IN 1..$EVENT_COUNT LOOP
        -- Random event type
        v_event_type := v_event_types[1 + floor(random() * array_length(v_event_types, 1))::int];

        -- Random user ID (10 unique users)
        v_user_id := ('00000000-0000-0000-0000-' || lpad((1 + (i % 10))::text, 12, '0'))::UUID;

        -- Random time within the target date
        v_created_at := v_target_date + (random() * interval '24 hours');

        INSERT INTO security_event (
            id,
            type,
            description,
            tenant_id,
            tenant_name,
            client_id,
            client_name,
            user_id,
            user_name,
            ip_address,
            user_agent,
            detail,
            created_at
        ) VALUES (
            gen_random_uuid(),
            v_event_type,
            'Test event for archive testing',
            v_tenant_id,
            v_tenant_name,
            v_client_id,
            v_client_name,
            v_user_id,
            'test-user-' || (i % 10),
            ('192.168.1.' || (i % 255))::INET,
            'Mozilla/5.0 (Test Agent)',
            jsonb_build_object(
                'test', true,
                'event_number', i,
                'generated_for', 'archive_testing'
            ),
            v_created_at
        );

        v_inserted := v_inserted + 1;
    END LOOP;

    RAISE NOTICE 'Inserted % events for date %', v_inserted, v_target_date;
END \$\$;

-- Verify inserted data
SELECT
    created_at::date as event_date,
    type,
    COUNT(*) as count
FROM security_event
WHERE created_at::date = '$TARGET_DATE'::date
GROUP BY created_at::date, type
ORDER BY type;

-- Show partition info
SELECT
    c.relname as partition,
    pg_size_pretty(pg_relation_size(c.oid)) as size,
    (SELECT COUNT(*) FROM security_event WHERE created_at::date = '$TARGET_DATE'::date) as row_count
FROM pg_inherits i
JOIN pg_class c ON i.inhrelid = c.oid
JOIN pg_class p ON i.inhparent = p.oid
WHERE p.relname = 'security_event'
  AND c.relname LIKE '%$(echo $TARGET_DATE | tr -d '-')%'
LIMIT 1;
EOF
)

echo "Executing SQL..."
echo ""

PGPASSWORD="$DB_PASSWORD" psql \
    -h "$DB_HOST" \
    -p "$DB_PORT" \
    -U "$DB_USER" \
    -d "$DB_NAME" \
    -c "$SQL"

echo ""
echo "================================================"
echo "Test Data Generated!"
echo "================================================"
echo ""
echo "Created $EVENT_COUNT events for $TARGET_DATE ($DAYS_AGO days ago)"
echo ""
echo "Next steps:"
echo "  1. Run partman maintenance to move old partitions to archive:"
echo "     CALL partman.run_maintenance_proc();"
echo ""
echo "  2. Check archive schema:"
echo "     SELECT * FROM archive.get_archive_status();"
echo ""
echo "  3. Process archives:"
echo "     SELECT * FROM archive.process_archived_partitions();"
echo ""
