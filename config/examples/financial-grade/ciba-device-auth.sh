#!/bin/bash
set -e

# CIBA Device Authentication Script
# This script simulates the user's device-side authentication for CIBA flow

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"

# Default values (can be overridden by command line arguments)
TENANT_ID="${TENANT_ID:-c3d4e5f6-a7b8-c9d0-e1f2-a3b4c5d6e7f8}"
DEVICE_ID="${DEVICE_ID:-b2c3d4e5-f6a7-8901-bcde-f23456789012}"
AUTH_REQ_ID="${1:-}"
CLIENT_ID="${2:-}"
USERNAME="${USERNAME:-fapi-test@example.com}"
PASSWORD="${PASSWORD:-FapiCibaTestSecure123!}"

echo "=========================================="
echo "📱 CIBA Device Authentication"
echo "=========================================="

# Load .env file
if [ -f "${ENV_FILE}" ]; then
  echo "📖 Loading environment variables from .env..."
  set -a
  source "${ENV_FILE}"
  set +a
fi

# Use backend URL (without mTLS) for device authentication
BASE_URL="${AUTHORIZATION_SERVER_URL:-https://host.docker.internal:8445}"

echo "   Server:    ${BASE_URL}"
echo "   Tenant:    ${TENANT_ID}"
echo "   Device:    ${DEVICE_ID}"
echo "   Username:  ${USERNAME}"
echo ""

# Step 1: Get authentication transaction
echo "🔍 Step 1: Getting authentication transaction..."

QUERY_PARAMS="attributes.auth_req_id=${AUTH_REQ_ID}"
if [ -n "${CLIENT_ID}" ]; then
  QUERY_PARAMS="${QUERY_PARAMS}&client_id=${CLIENT_ID}"
fi

TRANSACTION_RESPONSE=$(curl -s -k -w "\n%{http_code}" -X GET \
  "${BASE_URL}/${TENANT_ID}/v1/authentication-devices/${DEVICE_ID}/authentications" \
  -H "Content-Type: application/json")

HTTP_CODE=$(echo "${TRANSACTION_RESPONSE}" | tail -n1)
RESPONSE_BODY=$(echo "${TRANSACTION_RESPONSE}" | sed '$d')

if [ "${HTTP_CODE}" != "200" ]; then
  echo "❌ Failed to get authentication transaction (HTTP ${HTTP_CODE})"
  echo "Response: ${RESPONSE_BODY}"
  exit 1
fi

echo "✅ Authentication transaction retrieved"
echo "${RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "${RESPONSE_BODY}"
echo ""

# Extract transaction details
TRANSACTION_ID=$(echo "${RESPONSE_BODY}" | jq -r '.list[0].id')

if [ -z "${TRANSACTION_ID}" ] || [ "${TRANSACTION_ID}" = "null" ]; then
  echo "❌ No authentication transaction found for auth_req_id: ${AUTH_REQ_ID}"
  exit 1
fi


# Step 2: Execute password authentication
echo "🔐 Step 2: Executing password authentication..."

AUTH_RESPONSE=$(curl -s -k -w "\n%{http_code}" -X POST \
  "${BASE_URL}/${TENANT_ID}/v1/authentications/${TRANSACTION_ID}/password-authentication" \
  -H "Content-Type: application/json" \
  -d "{
    \"username\": \"${USERNAME}\",
    \"password\": \"${PASSWORD}\"
  }")

AUTH_HTTP_CODE=$(echo "${AUTH_RESPONSE}" | tail -n1)
AUTH_RESPONSE_BODY=$(echo "${AUTH_RESPONSE}" | sed '$d')

if [ "${AUTH_HTTP_CODE}" = "200" ]; then
  echo "✅ Password authentication successful!"
  echo "${AUTH_RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "${AUTH_RESPONSE_BODY}"
else
  echo "❌ Password authentication failed (HTTP ${AUTH_HTTP_CODE})"
  echo "Response: ${AUTH_RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "${AUTH_RESPONSE_BODY}"
  exit 1
fi

echo ""
echo "=========================================="
echo "✅ CIBA Device Authentication Complete!"
echo "=========================================="
echo ""
echo "The client can now poll the token endpoint to get the access token."
echo ""
