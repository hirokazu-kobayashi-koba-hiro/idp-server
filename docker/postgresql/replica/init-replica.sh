#!/bin/bash
set -e

echo "Starting replica initialization..."

# Wait for primary to be ready
until pg_isready -h postgres-primary -p 5432 -U replicator; do
    echo "Waiting for primary database to be ready..."
    sleep 5
done

# Remove any existing data directory
rm -rf /var/lib/postgresql/data/*

# Perform base backup from primary
echo "Performing base backup from primary..."
PGPASSWORD=replicator_password pg_basebackup -h postgres-primary -D /var/lib/postgresql/data -U replicator -v -P -W -x

# Create recovery configuration
cat > /var/lib/postgresql/data/postgresql.conf <<EOF
# Replica configuration
hot_standby = on
primary_conninfo = 'host=postgres-primary port=5432 user=replicator password=replicator_password'
primary_slot_name = 'replica_slot'
EOF

# Create standby.signal to enable standby mode
touch /var/lib/postgresql/data/standby.signal

# Set proper permissions
chown -R postgres:postgres /var/lib/postgresql/data
chmod 700 /var/lib/postgresql/data

echo "Replica initialization completed"