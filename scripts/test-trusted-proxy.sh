#!/bin/bash

# Trusted Proxy IP Resolution Test Script
# Usage: ./scripts/test-trusted-proxy.sh
#
# Tests:
# 1. Request without X-Forwarded-For → uses remoteAddr
# 2. Request with X-Forwarded-For from untrusted proxy → uses remoteAddr
# 3. Request with X-Forwarded-For from trusted proxy → uses X-Forwarded-For
#
# Prerequisites:
# - idp-server running locally
# - Admin credentials in .env

set -e

# Load .env
set -a; [ -f .env ] && source .env; set +a

# Configuration
BASE_URL="${AUTHORIZATION_SERVER_URL:-http://localhost:8080}"
ADMIN_TENANT_ID="${ADMIN_TENANT_ID}"
ADMIN_CLIENT_ID="${ADMIN_CLIENT_ID}"
ADMIN_CLIENT_SECRET="${ADMIN_CLIENT_SECRET}"
ADMIN_USER_EMAIL="${ADMIN_USER_EMAIL}"
ADMIN_USER_PASSWORD="${ADMIN_USER_PASSWORD}"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Trusted Proxy IP Resolution Test${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Check required env vars
if [ -z "$ADMIN_TENANT_ID" ] || [ -z "$ADMIN_USER_EMAIL" ]; then
  echo -e "${RED}Error: Required environment variables not set${NC}"
  echo "  ADMIN_TENANT_ID: ${ADMIN_TENANT_ID:-<not set>}"
  echo "  ADMIN_USER_EMAIL: ${ADMIN_USER_EMAIL:-<not set>}"
  exit 1
fi

# Get access token
echo -e "${YELLOW}Step 1: Getting admin access token...${NC}"
ACCESS_TOKEN=$(curl -s -X POST "${BASE_URL}/${ADMIN_TENANT_ID}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=password" \
  --data-urlencode "client_id=${ADMIN_CLIENT_ID}" \
  --data-urlencode "client_secret=${ADMIN_CLIENT_SECRET}" \
  --data-urlencode "username=${ADMIN_USER_EMAIL}" \
  --data-urlencode "password=${ADMIN_USER_PASSWORD}" \
  --data-urlencode "scope=openid management" | jq -r .access_token)

if [ "$ACCESS_TOKEN" == "null" ] || [ -z "$ACCESS_TOKEN" ]; then
  echo -e "${RED}Failed to get access token${NC}"
  exit 1
fi
echo -e "${GREEN}✓ Access token obtained${NC}"
echo ""

# Get current system configuration
echo -e "${YELLOW}Step 2: Getting current system configuration...${NC}"
CURRENT_CONFIG=$(curl -s -X GET "${BASE_URL}/v1/management/system-configurations" \
  -H "Authorization: Bearer $ACCESS_TOKEN")
echo "$CURRENT_CONFIG" | jq .
echo ""

# Enable trusted proxy with 127.0.0.1 (localhost)
echo -e "${YELLOW}Step 3: Enabling trusted proxy for 127.0.0.1...${NC}"
UPDATE_RESULT=$(curl -s -X PUT "${BASE_URL}/v1/management/system-configurations" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "trusted_proxies": {
      "enabled": true,
      "addresses": ["127.0.0.1", "::1"]
    }
  }')
echo "$UPDATE_RESULT" | jq .
echo ""

# Wait for cache invalidation
echo -e "${YELLOW}Waiting 2 seconds for cache invalidation...${NC}"
sleep 2
echo ""

# Test 1: Request without X-Forwarded-For
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Test 1: Request WITHOUT X-Forwarded-For${NC}"
echo -e "${BLUE}========================================${NC}"
echo "Expected: IP should be 127.0.0.1 (remoteAddr)"
echo ""
echo "Making request to discovery endpoint..."
curl -s -v "${BASE_URL}/${ADMIN_TENANT_ID}/.well-known/openid-configuration" 2>&1 | grep -E "(< HTTP|ip_address|resolvedClientIp)" || true
echo ""
echo -e "${GREEN}✓ Test 1 completed (check server logs for resolved IP)${NC}"
echo ""

# Test 2: Request WITH X-Forwarded-For from trusted proxy (127.0.0.1)
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Test 2: Request WITH X-Forwarded-For${NC}"
echo -e "${BLUE}        from TRUSTED proxy (127.0.0.1)${NC}"
echo -e "${BLUE}========================================${NC}"
echo "Expected: IP should be 203.0.113.50 (from X-Forwarded-For)"
echo ""
echo "Making request with X-Forwarded-For: 203.0.113.50..."
curl -s "${BASE_URL}/${ADMIN_TENANT_ID}/.well-known/openid-configuration" \
  -H "X-Forwarded-For: 203.0.113.50" > /dev/null
echo -e "${GREEN}✓ Test 2 completed (check server logs for resolved IP)${NC}"
echo ""

# Test 3: Check audit log with IP
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Test 3: Verify IP in authenticated request${NC}"
echo -e "${BLUE}========================================${NC}"
echo "Making authenticated request with X-Forwarded-For: 198.51.100.25..."
curl -s "${BASE_URL}/v1/management/system-configurations" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "X-Forwarded-For: 198.51.100.25" > /dev/null
echo -e "${GREEN}✓ Test 3 completed (check audit logs for IP: 198.51.100.25)${NC}"
echo ""

# Test 4: Disable trusted proxy and verify X-Forwarded-For is ignored
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Test 4: Disable trusted proxy${NC}"
echo -e "${BLUE}========================================${NC}"
echo -e "${YELLOW}Disabling trusted proxy...${NC}"
curl -s -X PUT "${BASE_URL}/v1/management/system-configurations" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "trusted_proxies": {
      "enabled": false,
      "addresses": []
    }
  }' | jq .
echo ""

sleep 2

echo "Making request with X-Forwarded-For: 192.0.2.100 (should be IGNORED)..."
curl -s "${BASE_URL}/${ADMIN_TENANT_ID}/.well-known/openid-configuration" \
  -H "X-Forwarded-For: 192.0.2.100" > /dev/null
echo "Expected: IP should be 127.0.0.1 (X-Forwarded-For ignored because proxy not trusted)"
echo -e "${GREEN}✓ Test 4 completed${NC}"
echo ""

# Test 5: CIDR range test
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Test 5: CIDR range (10.0.0.0/8)${NC}"
echo -e "${BLUE}========================================${NC}"
echo -e "${YELLOW}Enabling trusted proxy for 10.0.0.0/8...${NC}"
curl -s -X PUT "${BASE_URL}/v1/management/system-configurations" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "trusted_proxies": {
      "enabled": true,
      "addresses": ["10.0.0.0/8"]
    }
  }' | jq .
echo ""
echo "Note: To test CIDR, you would need to send request from 10.x.x.x network"
echo "      Local testing from 127.0.0.1 will NOT match 10.0.0.0/8"
echo ""

# Restore original configuration
echo -e "${YELLOW}Restoring original configuration...${NC}"
if [ -n "$CURRENT_CONFIG" ]; then
  TRUSTED_PROXIES=$(echo "$CURRENT_CONFIG" | jq -c '.trusted_proxies // {"enabled": false, "addresses": []}')
  curl -s -X PUT "${BASE_URL}/v1/management/system-configurations" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"trusted_proxies\": $TRUSTED_PROXIES}" | jq .
fi
echo ""

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  All Tests Completed!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "To verify the resolved IPs, check the server logs:"
echo ""
echo "  grep 'Resolved client IP' logs/app.log"
echo "  grep 'resolvedClientIp' logs/app.log"
echo ""
echo "Or check audit logs in the database:"
echo ""
echo "  SELECT payload->'ip_address' FROM audit_logs ORDER BY created_at DESC LIMIT 5;"
echo ""
