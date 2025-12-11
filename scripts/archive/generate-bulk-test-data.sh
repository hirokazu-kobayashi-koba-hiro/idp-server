#!/bin/bash
# ================================================
# Generate Bulk Test Data for Archive Testing
# ================================================
# This script creates security event data for multiple past dates
# to simulate a realistic partition aging scenario.
#
# Usage:
#   ./scripts/archive/generate-bulk-test-data.sh
#
# This will generate:
#   - 91-95 days ago: 50 events/day (will be archived)
#   - 85-90 days ago: 50 events/day (near retention boundary)
#   - 1-7 days ago: 20 events/day (recent data)
#
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

psql_exec() {
    PGPASSWORD="$DB_PASSWORD" psql \
        -h "$DB_HOST" \
        -p "$DB_PORT" \
        -U "$DB_USER" \
        -d "$DB_NAME" \
        -c "$1"
}

echo "================================================"
echo "Generate Bulk Test Data for Archive Testing"
echo "================================================"
echo "Database: $DB_NAME@$DB_HOST:$DB_PORT"
echo ""

# Generate data for archived range (91-95 days ago)
echo "Generating data for ARCHIVED range (91-95 days ago)..."
for days in 91 92 93 94 95; do
    TARGET_DATE=$(date -v-${days}d +%Y-%m-%d 2>/dev/null || date -d "-${days} days" +%Y-%m-%d)
    echo "  - $TARGET_DATE ($days days ago): 50 events"

    psql_exec "
    DO \$\$
    DECLARE
        v_tenant_id UUID := '00000000-0000-0000-0000-000000000001';
        v_target_date DATE := '$TARGET_DATE'::DATE;
        v_event_types TEXT[] := ARRAY['login_success', 'issue_token_success', 'refresh_token_success'];
    BEGIN
        FOR i IN 1..50 LOOP
            INSERT INTO security_event (
                id, type, description, tenant_id, tenant_name,
                client_id, client_name, user_id, user_name,
                ip_address, user_agent, detail, created_at
            ) VALUES (
                gen_random_uuid(),
                v_event_types[1 + floor(random() * 3)::int],
                'Archive test event',
                v_tenant_id, 'test-tenant',
                'test-client', 'Test Client',
                ('00000000-0000-0000-0000-' || lpad((i % 10 + 1)::text, 12, '0'))::UUID,
                'user-' || (i % 10),
                ('192.168.1.' || (i % 255))::INET,
                'TestAgent/1.0',
                '{\"test\": true, \"batch\": \"archived\"}'::jsonb,
                v_target_date + (random() * interval '24 hours')
            );
        END LOOP;
    END \$\$;
    " > /dev/null
done
echo ""

# Generate data for boundary range (85-90 days ago)
echo "Generating data for BOUNDARY range (85-90 days ago)..."
for days in 85 86 87 88 89 90; do
    TARGET_DATE=$(date -v-${days}d +%Y-%m-%d 2>/dev/null || date -d "-${days} days" +%Y-%m-%d)
    echo "  - $TARGET_DATE ($days days ago): 50 events"

    psql_exec "
    DO \$\$
    DECLARE
        v_tenant_id UUID := '00000000-0000-0000-0000-000000000001';
        v_target_date DATE := '$TARGET_DATE'::DATE;
        v_event_types TEXT[] := ARRAY['login_success', 'issue_token_success', 'refresh_token_success'];
    BEGIN
        FOR i IN 1..50 LOOP
            INSERT INTO security_event (
                id, type, description, tenant_id, tenant_name,
                client_id, client_name, user_id, user_name,
                ip_address, user_agent, detail, created_at
            ) VALUES (
                gen_random_uuid(),
                v_event_types[1 + floor(random() * 3)::int],
                'Boundary test event',
                v_tenant_id, 'test-tenant',
                'test-client', 'Test Client',
                ('00000000-0000-0000-0000-' || lpad((i % 10 + 1)::text, 12, '0'))::UUID,
                'user-' || (i % 10),
                ('192.168.1.' || (i % 255))::INET,
                'TestAgent/1.0',
                '{\"test\": true, \"batch\": \"boundary\"}'::jsonb,
                v_target_date + (random() * interval '24 hours')
            );
        END LOOP;
    END \$\$;
    " > /dev/null
done
echo ""

# Generate data for recent range (1-7 days ago)
echo "Generating data for RECENT range (1-7 days ago)..."
for days in 1 2 3 4 5 6 7; do
    TARGET_DATE=$(date -v-${days}d +%Y-%m-%d 2>/dev/null || date -d "-${days} days" +%Y-%m-%d)
    echo "  - $TARGET_DATE ($days days ago): 20 events"

    psql_exec "
    DO \$\$
    DECLARE
        v_tenant_id UUID := '00000000-0000-0000-0000-000000000001';
        v_target_date DATE := '$TARGET_DATE'::DATE;
        v_event_types TEXT[] := ARRAY['login_success', 'issue_token_success', 'refresh_token_success'];
    BEGIN
        FOR i IN 1..20 LOOP
            INSERT INTO security_event (
                id, type, description, tenant_id, tenant_name,
                client_id, client_name, user_id, user_name,
                ip_address, user_agent, detail, created_at
            ) VALUES (
                gen_random_uuid(),
                v_event_types[1 + floor(random() * 3)::int],
                'Recent test event',
                v_tenant_id, 'test-tenant',
                'test-client', 'Test Client',
                ('00000000-0000-0000-0000-' || lpad((i % 10 + 1)::text, 12, '0'))::UUID,
                'user-' || (i % 10),
                ('192.168.1.' || (i % 255))::INET,
                'TestAgent/1.0',
                '{\"test\": true, \"batch\": \"recent\"}'::jsonb,
                v_target_date + (random() * interval '24 hours')
            );
        END LOOP;
    END \$\$;
    " > /dev/null
done
echo ""

# Summary
echo "================================================"
echo "Summary"
echo "================================================"
psql_exec "
SELECT
    CASE
        WHEN created_at::date < CURRENT_DATE - 90 THEN 'ARCHIVED (>90 days)'
        WHEN created_at::date >= CURRENT_DATE - 90 AND created_at::date < CURRENT_DATE - 84 THEN 'BOUNDARY (85-90 days)'
        ELSE 'RECENT (<85 days)'
    END as category,
    COUNT(*) as event_count,
    COUNT(DISTINCT created_at::date) as days,
    MIN(created_at::date) as oldest,
    MAX(created_at::date) as newest
FROM security_event
WHERE detail->>'test' = 'true'
GROUP BY 1
ORDER BY 1;
"

echo ""
echo "Partition overview:"
psql_exec "
SELECT
    c.relname as partition,
    pg_size_pretty(pg_relation_size(c.oid)) as size
FROM pg_inherits i
JOIN pg_class c ON i.inhrelid = c.oid
JOIN pg_class p ON i.inhparent = p.oid
WHERE p.relname = 'security_event'
ORDER BY c.relname DESC
LIMIT 15;
"

echo ""
echo "================================================"
echo "Test Data Generated!"
echo "================================================"
echo ""
echo "Total events created:"
echo "  - Archived range (91-95 days): 250 events"
echo "  - Boundary range (85-90 days): 300 events"
echo "  - Recent range (1-7 days): 140 events"
echo ""
echo "Next steps:"
echo "  1. Run partman maintenance:"
echo "     CALL partman.run_maintenance_proc();"
echo ""
echo "  2. Check what moved to archive:"
echo "     SELECT * FROM archive.get_archive_status();"
echo ""
echo "  3. Test archive processing:"
echo "     SELECT * FROM archive.process_archived_partitions(p_dry_run := TRUE);"
echo ""
