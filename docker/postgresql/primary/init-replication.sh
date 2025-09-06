#!/bin/bash
set -e

# Create replication user for streaming replication
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE USER replicator WITH REPLICATION ENCRYPTED PASSWORD 'replicator_password';
    SELECT pg_create_physical_replication_slot('replica_slot');
EOSQL

# Configure PostgreSQL for replication
cat >> /var/lib/postgresql/data/postgresql.conf <<EOF
# Replication settings
wal_level = replica
max_wal_senders = 3
max_replication_slots = 3
hot_standby = on
EOF

# Configure client authentication for replication
echo "host replication replicator 0.0.0.0/0 md5" >> /var/lib/postgresql/data/pg_hba.conf

echo "Primary PostgreSQL replication setup completed"