#!/bin/bash
set -e

# If data directory is empty or doesn't exist, initialize as replica
if [ ! -s "/var/lib/postgresql/data/PG_VERSION" ]; then
    echo "Initializing PostgreSQL replica..."
    
    # Wait for primary to be ready
    until pg_isready -h postgres-primary -p 5432 -U replicator -d postgres; do
        echo "Waiting for primary database to be ready..."
        sleep 5
    done
    
    # Perform base backup from primary
    echo "Performing base backup from primary..."
    PGPASSWORD=replicator_password pg_basebackup -h postgres-primary -D /var/lib/postgresql/data -U replicator -v -P -R
    
    # Create standby.signal to enable standby mode
    touch /var/lib/postgresql/data/standby.signal
    
    echo "Replica initialization completed"
fi

# Start PostgreSQL with original entrypoint
exec docker-entrypoint.sh "$@"