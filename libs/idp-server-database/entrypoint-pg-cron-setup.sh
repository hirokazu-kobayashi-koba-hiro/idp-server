#!/bin/sh
set -eu

echo "Starting pg_cron job setup..."

# Wait for PostgreSQL to be ready
until PGPASSWORD="$DB_PASSWORD" psql -h "$PGHOST" -p "$PGPORT" -U "$DB_USER_NAME" -d "$POSTGRES_DB" -c '\q' 2>/dev/null; do
  echo "Waiting for PostgreSQL at $PGHOST:$PGPORT..."
  sleep 2
done

echo "PostgreSQL is ready. Running pg_cron setup script..."

# Execute setup script
# Note: pg_cron is installed in 'postgres' database (cross-database mode)
# The script uses cron.schedule_in_database() to target 'idpserver' database
PGPASSWORD="$DB_PASSWORD" psql -h "$PGHOST" -p "$PGPORT" -U "$DB_USER_NAME" -d "postgres" -f /scripts/setup-pg-cron-jobs.sql

if [ $? -eq 0 ]; then
  echo "✓ pg_cron job setup completed successfully"
else
  echo "✗ pg_cron job setup failed"
  exit 1
fi
