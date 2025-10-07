#!/bin/sh
set -eu

echo "Starting PostgreSQL user creation..."

# Wait for PostgreSQL to be ready (using superuser credentials)
until PGPASSWORD="$POSTGRES_PASSWORD" psql -h "$PGHOST" -p "$PGPORT" -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c '\q' 2>/dev/null; do
  echo "Waiting for PostgreSQL at $PGHOST:$PGPORT (user: $POSTGRES_USER)..."
  sleep 2
done

echo "PostgreSQL is ready. Running user creation script..."

# Execute user creation script
sh /scripts/01-create-users.sh

if [ $? -eq 0 ]; then
  echo "✓ User creation completed successfully"
else
  echo "✗ User creation failed"
  exit 1
fi
