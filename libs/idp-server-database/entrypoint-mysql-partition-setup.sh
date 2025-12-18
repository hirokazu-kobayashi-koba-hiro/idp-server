#!/bin/sh
set -eu

echo "Starting MySQL partition maintenance setup..."

# Wait for MySQL to be ready
until mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" -p"$MYSQL_PASSWORD" -e '\q' 2>/dev/null; do
  echo "Waiting for MySQL at $MYSQL_HOST:$MYSQL_PORT..."
  sleep 2
done

echo "MySQL is ready."

# Step 1: Fix stored procedures (Flyway cannot handle DELIMITER properly)
echo "Fixing stored procedures..."
mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" < /scripts/fix-stored-procedures.sql

if [ $? -ne 0 ]; then
  echo "✗ Failed to fix stored procedures"
  exit 1
fi

# Step 2: Run partition maintenance
echo "Running partition maintenance script..."
mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" < /scripts/setup-partition-maintenance.sql

if [ $? -eq 0 ]; then
  echo "✓ MySQL partition maintenance completed successfully"
else
  echo "✗ MySQL partition maintenance failed"
  exit 1
fi
