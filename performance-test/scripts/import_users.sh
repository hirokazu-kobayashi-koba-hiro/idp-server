#!/bin/bash
# =============================================================================
# Performance Test User Data Import Script
# =============================================================================
# Imports generated user and device data into PostgreSQL.
#
# Usage:
#   ./import_users.sh <prefix>
#
# Examples:
#   ./import_users.sh single_tenant_1m      # Import 1M single tenant users
#   ./import_users.sh multi_tenant_10x100k  # Import multi-tenant users
#
# Prerequisites:
#   - Docker container 'postgres-primary' running
#   - Generated TSV files from generate_users.py
# =============================================================================

set -e

PREFIX="${1:-single_tenant_1m}"
DATA_DIR="./performance-test/data"

USERS_FILE="${DATA_DIR}/${PREFIX}_users.tsv"
DEVICES_FILE="${DATA_DIR}/${PREFIX}_devices.tsv"
TEST_USERS_FILE="${DATA_DIR}/${PREFIX}_test_users.json"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "============================================================"
echo "Performance Test User Data Import"
echo "============================================================"
echo ""

# Check files exist
if [ ! -f "$USERS_FILE" ]; then
    echo -e "${RED}Error: Users file not found: $USERS_FILE${NC}"
    echo "Run generate_users.py first to create the data files."
    exit 1
fi

if [ ! -f "$DEVICES_FILE" ]; then
    echo -e "${RED}Error: Devices file not found: $DEVICES_FILE${NC}"
    echo "Run generate_users.py first to create the data files."
    exit 1
fi

# Check Docker container
if ! docker ps --format '{{.Names}}' | grep -q 'postgres-primary'; then
    echo -e "${RED}Error: postgres-primary container not running${NC}"
    exit 1
fi

# Count lines in files
USER_COUNT=$(wc -l < "$USERS_FILE" | tr -d ' ')
DEVICE_COUNT=$(wc -l < "$DEVICES_FILE" | tr -d ' ')

echo "Files to import:"
echo "  Users:   $USERS_FILE ($USER_COUNT records)"
echo "  Devices: $DEVICES_FILE ($DEVICE_COUNT records)"
echo ""

# Confirm before import
read -p "Proceed with import? (y/N) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Import cancelled."
    exit 0
fi

echo ""
echo -e "${YELLOW}Importing users...${NC}"

docker exec -i postgres-primary psql -U idpserver -d idpserver -c "\COPY idp_user (
  id, tenant_id, provider_id, external_user_id, name, email,
  email_verified, phone_number, phone_number_verified,
  preferred_username, status, authentication_devices
) FROM STDIN WITH (FORMAT csv, HEADER false, DELIMITER E'\t')" < "$USERS_FILE"

echo -e "${GREEN}Users imported successfully!${NC}"

echo ""
echo -e "${YELLOW}Importing devices...${NC}"

docker exec -i postgres-primary psql -U idpserver -d idpserver -c "\COPY idp_user_authentication_devices (
  id, tenant_id, user_id, os, model, platform, locale,
  app_name, priority, available_methods,
  notification_token, notification_channel
) FROM STDIN WITH (FORMAT csv, HEADER false, DELIMITER E'\t')" < "$DEVICES_FILE"

echo -e "${GREEN}Devices imported successfully!${NC}"

echo ""
echo "============================================================"
echo -e "${GREEN}Import complete!${NC}"
echo "============================================================"
echo ""

# Verify counts
echo "Verifying import..."
IMPORTED_USERS=$(docker exec postgres-primary psql -U idpserver -d idpserver -t -c "SELECT COUNT(*) FROM idp_user;" | tr -d ' ')
IMPORTED_DEVICES=$(docker exec postgres-primary psql -U idpserver -d idpserver -t -c "SELECT COUNT(*) FROM idp_user_authentication_devices;" | tr -d ' ')

echo "  Total users in DB:   $IMPORTED_USERS"
echo "  Total devices in DB: $IMPORTED_DEVICES"
echo ""

# Show tenant breakdown
echo "Users per tenant:"
docker exec postgres-primary psql -U idpserver -d idpserver -c "
SELECT tenant_id, COUNT(*) as user_count
FROM idp_user
GROUP BY tenant_id
ORDER BY user_count DESC
LIMIT 10;"

echo ""
echo "Next steps:"
echo "  1. Copy test users JSON for k6:"
echo "     cp $TEST_USERS_FILE ${DATA_DIR}/performance-test-multi-tenant-users.json"
echo ""
echo "  2. Run stress tests:"
echo "     k6 run ./performance-test/stress/scenario-1-authorization-request.js"
