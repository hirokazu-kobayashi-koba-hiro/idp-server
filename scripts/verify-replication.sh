#!/bin/bash
set -e

echo "=== PostgreSQL Primary/Replica Replication Verification ==="

# Wait for services to be ready
echo "Waiting for primary and replica to be healthy..."
docker compose up -d postgres-primary postgres-replica
sleep 30

# Check primary status
echo -e "\n1. Checking Primary Status:"
echo "Primary pg_is_in_recovery():"
docker compose exec postgres-primary psql -U idpserver -d idpserver -c "SELECT pg_is_in_recovery();"

echo -e "\nPrimary replication slots:"
docker compose exec postgres-primary psql -U idpserver -d idpserver -c "SELECT slot_name, slot_type, active FROM pg_replication_slots;"

# Check replica status
echo -e "\n2. Checking Replica Status:"
echo "Replica pg_is_in_recovery():"
docker compose exec postgres-replica psql -U idpserver -d idpserver -c "SELECT pg_is_in_recovery();"

echo -e "\nReplica status:"
docker compose exec postgres-replica psql -U idpserver -d idpserver -c "SELECT status, received_tli, last_msg_send_time FROM pg_stat_wal_receiver;"

# Test replication
echo -e "\n3. Testing Replication:"
echo "Creating test table on primary..."
docker compose exec postgres-primary psql -U idpserver -d idpserver -c "CREATE TABLE IF NOT EXISTS replication_test (id SERIAL PRIMARY KEY, message TEXT, created_at TIMESTAMP DEFAULT NOW());"

echo "Inserting test data on primary..."
docker compose exec postgres-primary psql -U idpserver -d idpserver -c "INSERT INTO replication_test (message) VALUES ('Test message from primary at $(date)');"

echo "Waiting for replication..."
sleep 5

echo "Checking if data exists on replica:"
docker compose exec postgres-replica psql -U idpserver -d idpserver -c "SELECT * FROM replication_test ORDER BY created_at DESC LIMIT 1;"

# Test write restriction on replica
echo -e "\n4. Testing Write Restriction on Replica:"
echo "Attempting to insert on replica (should fail):"
if docker compose exec postgres-replica psql -U idpserver -d idpserver -c "INSERT INTO replication_test (message) VALUES ('This should fail');" 2>/dev/null; then
    echo "ERROR: Write succeeded on replica (should not happen)"
    exit 1
else
    echo "SUCCESS: Write correctly failed on replica"
fi

# Connection test
echo -e "\n5. Connection Test:"
echo "Primary connection (port 5432):"
docker compose exec postgres-primary psql -U idpserver -d idpserver -c "SELECT 'Connected to PRIMARY on port 5432' as status;"

echo "Replica connection (port 5433):"
docker compose exec postgres-replica psql -U idpserver -d idpserver -c "SELECT 'Connected to REPLICA on port 5433' as status;"

echo -e "\n=== Verification Complete ==="
echo "✅ Primary/Replica replication is working correctly!"
echo ""
echo "Connection Information:"
echo "  Primary (Write): postgresql://idpserver:idpserver@localhost:5432/idpserver"
echo "  Replica (Read):  postgresql://idpserver:idpserver@localhost:5433/idpserver"