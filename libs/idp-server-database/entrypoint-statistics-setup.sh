#!/bin/sh
set -eu

echo "Starting statistics aggregation setup..."

# Wait for PostgreSQL to be ready
until PGPASSWORD="$DB_PASSWORD" psql -h "$PGHOST" -p "$PGPORT" -U "$DB_USER_NAME" -d "$POSTGRES_DB" -c '\q' 2>/dev/null; do
  echo "Waiting for PostgreSQL at $PGHOST:$PGPORT..."
  sleep 2
done

echo "PostgreSQL is ready."

# Step 1: Register statistics aggregation function in idpserver database
echo "Registering statistics aggregation function..."
PGPASSWORD="$DB_PASSWORD" psql -h "$PGHOST" -p "$PGPORT" -U "$DB_USER_NAME" -d "$POSTGRES_DB" -f /scripts/statistics-aggregation-function.sql
echo "✓ Statistics aggregation function registered"

# Step 2: Register pg_cron job for daily statistics aggregation
# Note: pg_cron extension is installed in the 'postgres' database,
# so we connect to 'postgres' and use cron.schedule_in_database()
# to target 'idpserver'.
echo "Registering pg_cron job for daily statistics aggregation..."
PGPASSWORD="$DB_PASSWORD" psql -h "$PGHOST" -p "$PGPORT" -U "$DB_USER_NAME" -d "postgres" <<'EOSQL'
-- Remove existing job (idempotent)
DO $$
BEGIN
    PERFORM cron.unschedule('daily-statistics-aggregation');
    RAISE NOTICE 'Removed existing daily-statistics-aggregation job';
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'No existing daily-statistics-aggregation job to remove';
END $$;

-- Register daily statistics aggregation job
-- Schedule: Daily at 04:00 UTC (after partman 02:00 and archive 03:00)
SELECT cron.schedule_in_database(
    'daily-statistics-aggregation',
    '0 4 * * *',
    $$SELECT * FROM aggregate_daily_statistics()$$,
    'idpserver'
);

-- Verify
DO $$
DECLARE
    v_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO v_count
    FROM cron.job
    WHERE jobname = 'daily-statistics-aggregation';

    IF v_count = 1 THEN
        RAISE NOTICE 'pg_cron job "daily-statistics-aggregation" registered successfully (04:00 UTC on idpserver)';
    ELSE
        RAISE EXCEPTION 'Failed to register daily-statistics-aggregation job';
    END IF;
END $$;
EOSQL
echo "✓ pg_cron job for statistics aggregation registered"

echo "✓ Statistics aggregation setup completed"
