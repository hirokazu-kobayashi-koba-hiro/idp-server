#!/bin/bash

# System Configuration Upsert Script
# Usage: ./config/scripts/upsert-system-configuration.sh [options]
#
# Options:
#   -f, --file     Configuration JSON file path
#   -d, --dry-run  Dry run mode (validate only, don't save)
#   -h, --help     Show this help message
#
# Environment variables (from .env):
#   AUTHORIZATION_SERVER_URL - IdP server URL (default: http://localhost:8080)
#   ADMIN_USER_EMAIL         - Admin user email
#   ADMIN_USER_PASSWORD      - Admin user password
#   ADMIN_TENANT_ID          - Admin tenant ID
#   ADMIN_CLIENT_ID          - Admin client ID
#   ADMIN_CLIENT_SECRET      - Admin client secret

set -e

# Load .env
set -a; [ -f .env ] && source .env; set +a

# Default values
CONFIG_FILE="./config/examples/system-configuration-local.json"
BASE_URL="${AUTHORIZATION_SERVER_URL:-http://localhost:8080}"
DRY_RUN="${DRY_RUN:-false}"

# Parse arguments
while [[ $# -gt 0 ]]; do
  case $1 in
    -f|--file)
      CONFIG_FILE="$2"
      shift 2
      ;;
    -d|--dry-run)
      DRY_RUN="true"
      shift
      ;;
    -h|--help)
      head -17 "$0" | tail -15
      exit 0
      ;;
    *)
      echo "Unknown option: $1"
      exit 1
      ;;
  esac
done

# Check if config file exists
if [ ! -f "$CONFIG_FILE" ]; then
  echo "Error: Configuration file not found: $CONFIG_FILE"
  exit 1
fi

echo "==================================================="
echo "System Configuration Upsert"
echo "==================================================="
echo "Config File: $CONFIG_FILE"
echo "Base URL: $BASE_URL"
echo "Dry Run: $DRY_RUN"
echo ""

# Validate required environment variables
if [ -z "$ADMIN_USER_EMAIL" ] || [ -z "$ADMIN_USER_PASSWORD" ] || [ -z "$ADMIN_TENANT_ID" ]; then
  echo "Error: Required environment variables not set"
  echo "  ADMIN_USER_EMAIL: ${ADMIN_USER_EMAIL:-<not set>}"
  echo "  ADMIN_USER_PASSWORD: ${ADMIN_USER_PASSWORD:+<set>}${ADMIN_USER_PASSWORD:-<not set>}"
  echo "  ADMIN_TENANT_ID: ${ADMIN_TENANT_ID:-<not set>}"
  exit 1
fi

# Get access token
echo "Fetching admin access token..."

ACCESS_TOKEN=$(curl -s -X POST "${BASE_URL}/${ADMIN_TENANT_ID}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=password" \
  --data-urlencode "client_id=${ADMIN_CLIENT_ID}" \
  --data-urlencode "client_secret=${ADMIN_CLIENT_SECRET}" \
  --data-urlencode "username=${ADMIN_USER_EMAIL}" \
  --data-urlencode "password=${ADMIN_USER_PASSWORD}" \
  --data-urlencode "scope=openid management" | jq -r .access_token)

if [ "$ACCESS_TOKEN" == "null" ] || [ -z "$ACCESS_TOKEN" ]; then
  echo "❌ Failed to get access token"
  echo ""
  echo "Debug: Retrying with verbose output..."
  curl -X POST "${BASE_URL}/${ADMIN_TENANT_ID}/v1/tokens" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    --data-urlencode "grant_type=password" \
    --data-urlencode "client_id=${ADMIN_CLIENT_ID}" \
    --data-urlencode "client_secret=${ADMIN_CLIENT_SECRET}" \
    --data-urlencode "username=${ADMIN_USER_EMAIL}" \
    --data-urlencode "password=${ADMIN_USER_PASSWORD}" \
    --data-urlencode "scope=openid management" | jq .
  exit 1
fi

echo "✅ Access token obtained"

# Read configuration file
CONFIG_JSON=$(cat "$CONFIG_FILE")

echo ""
echo "Configuration to apply:"
echo "$CONFIG_JSON" | jq .
echo ""

# Build URL with dry-run parameter
API_URL="${BASE_URL}/v1/management/system-configurations"
if [ "$DRY_RUN" = "true" ]; then
  API_URL="${API_URL}?dry_run=true"
fi

# Call API
echo "Calling API: PUT $API_URL"
echo ""

RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT "$API_URL" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d "$CONFIG_JSON")

# Extract response body and status code
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
RESPONSE_BODY=$(echo "$RESPONSE" | sed '$d')

echo "HTTP Status: $HTTP_CODE"
echo ""
echo "Response:"
echo "$RESPONSE_BODY" | jq . 2>/dev/null || echo "$RESPONSE_BODY"

# Check result
if [ "$HTTP_CODE" -ge 200 ] && [ "$HTTP_CODE" -lt 300 ]; then
  if [ "$DRY_RUN" = "true" ]; then
    echo ""
    echo "✅ Dry run successful - validation passed"
  else
    echo ""
    echo "✅ System configuration updated successfully"
  fi
else
  echo ""
  echo "❌ Failed to update system configuration"
  exit 1
fi
