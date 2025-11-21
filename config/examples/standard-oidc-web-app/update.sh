#!/bin/bash
set -e

# Standard OIDC Web Application Update Script
# This script updates existing tenant/client/user configuration

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"
ONBOARDING_REQUEST="${SCRIPT_DIR}/onboarding-request.json"

echo "=========================================="
echo "🔄 Standard OIDC Web App Update"
echo "=========================================="

# Load .env file
if [ ! -f "${ENV_FILE}" ]; then
  echo "❌ Error: .env file not found at ${ENV_FILE}"
  exit 1
fi

# Check onboarding-request.json
if [ ! -f "${ONBOARDING_REQUEST}" ]; then
  echo "❌ Error: onboarding-request.json not found at ${ONBOARDING_REQUEST}"
  exit 1
fi

echo "📖 Loading environment variables from .env..."
set -a
source "${ENV_FILE}"
set +a

echo "✅ Environment variables loaded"
echo "   Server: ${AUTHORIZATION_SERVER_URL}"
echo ""

# Step 1: Get access token
echo "🔐 Step 1: Getting system administrator access token..."
TOKEN_RESPONSE=$(curl -s -X POST \
  "${AUTHORIZATION_SERVER_URL}/${ADMIN_TENANT_ID}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=password" \
  --data-urlencode "username=${ADMIN_USER_EMAIL}" \
  --data-urlencode "password=${ADMIN_USER_PASSWORD}" \
  --data-urlencode "client_id=${ADMIN_CLIENT_ID}" \
  --data-urlencode "client_secret=${ADMIN_CLIENT_SECRET}" \
  --data-urlencode "scope=account management")

SYSTEM_ACCESS_TOKEN=$(echo "${TOKEN_RESPONSE}" | jq -r '.access_token')

if [ -z "${SYSTEM_ACCESS_TOKEN}" ] || [ "${SYSTEM_ACCESS_TOKEN}" = "null" ]; then
  echo "❌ Error: Failed to get access token"
  echo "Response: ${TOKEN_RESPONSE}"
  exit 1
fi

echo "✅ Access token obtained: ${SYSTEM_ACCESS_TOKEN:0:20}..."
echo ""

# Read IDs from onboarding-request.json
echo "📖 Reading configuration from onboarding-request.json..."
ORG_ID=$(jq -r '.organization.id' "${ONBOARDING_REQUEST}")
TENANT_ID=$(jq -r '.tenant.id' "${ONBOARDING_REQUEST}")
USER_ID=$(jq -r '.user.sub' "${ONBOARDING_REQUEST}")
CLIENT_ID=$(jq -r '.client.client_id' "${ONBOARDING_REQUEST}")

echo "✅ Configuration loaded"
echo "   Organization ID: ${ORG_ID}"
echo "   Tenant ID:       ${TENANT_ID}"
echo "   Client ID:       ${CLIENT_ID}"
echo ""

# Step 2: Check if resources exist
echo "🔍 Step 2: Checking existing resources..."

# Check organization
#ORG_CHECK=$(curl -s -w "\n%{http_code}" -X GET \
#  "${AUTHORIZATION_SERVER_URL}/v1/management" \
#  -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}")
#
#ORG_HTTP_CODE=$(echo "${ORG_CHECK}" | tail -n1)
#ORG_EXISTS=false
#if [ "${ORG_HTTP_CODE}" = "200" ]; then
#  ORG_EXISTS=true
#  echo "   ✓ Organization exists: ${ORG_ID}"
#else
#  echo "   ✗ Organization not found: ${ORG_ID}"
#fi

# Check tenant
TENANT_CHECK=$(curl -s -w "\n%{http_code}" -X GET \
  "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${TENANT_ID}" \
  -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}")

TENANT_HTTP_CODE=$(echo "${TENANT_CHECK}" | tail -n1)
TENANT_EXISTS=false
if [ "${TENANT_HTTP_CODE}" = "200" ]; then
  TENANT_EXISTS=true
  echo "   ✓ Tenant exists: ${TENANT_ID}"
else
  echo "   ✗ Tenant not found: ${TENANT_ID}"
fi

# Check client
CLIENT_CHECK=$(curl -s -w "\n%{http_code}" -X GET \
  "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${TENANT_ID}/clients/${CLIENT_ID}" \
  -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}")

CLIENT_HTTP_CODE=$(echo "${CLIENT_CHECK}" | tail -n1)
CLIENT_EXISTS=false
if [ "${CLIENT_HTTP_CODE}" = "200" ]; then
  CLIENT_EXISTS=true
  echo "   ✓ Client exists: ${CLIENT_ID}"
else
  echo "   ✗ Client not found: ${CLIENT_ID}"
fi

echo ""

# Step 3: Determine action
if [ "${TENANT_EXISTS}" = "false" ]; then
  echo "⚠️  Resources not found. Please run ./setup.sh first."
  exit 1
fi

# Step 4: Update tenant configuration
echo "🔄 Step 3: Updating tenant configuration..."

# Extract full tenant configuration from onboarding-request.json
TENANT_UPDATE_JSON=$(jq '.tenant' "${ONBOARDING_REQUEST}")

TENANT_UPDATE_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT \
  "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${TENANT_ID}" \
  -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "${TENANT_UPDATE_JSON}")

TENANT_UPDATE_HTTP_CODE=$(echo "${TENANT_UPDATE_RESPONSE}" | tail -n1)
TENANT_UPDATE_BODY=$(echo "${TENANT_UPDATE_RESPONSE}" | sed '$d')

if [ "${TENANT_UPDATE_HTTP_CODE}" = "200" ]; then
  echo "✅ Tenant configuration updated"
else
  echo "❌ Tenant update failed (HTTP ${TENANT_UPDATE_HTTP_CODE})"
  echo "Response: ${TENANT_UPDATE_BODY}" | jq '.'
fi

echo ""

# Step 5: Update client configuration
echo "🔄 Step 4: Updating client configuration..."

# Extract full client configuration from onboarding-request.json
CLIENT_UPDATE_JSON=$(jq '.client' "${ONBOARDING_REQUEST}")

CLIENT_UPDATE_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT \
  "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${TENANT_ID}/clients/${CLIENT_ID}" \
  -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "${CLIENT_UPDATE_JSON}")

CLIENT_UPDATE_HTTP_CODE=$(echo "${CLIENT_UPDATE_RESPONSE}" | tail -n1)
CLIENT_UPDATE_BODY=$(echo "${CLIENT_UPDATE_RESPONSE}" | sed '$d')

if [ "${CLIENT_UPDATE_HTTP_CODE}" = "200" ] || [ "${CLIENT_UPDATE_HTTP_CODE}" = "201" ]; then
  echo "✅ Client configuration updated"
else
  echo "❌ Client update failed (HTTP ${CLIENT_UPDATE_HTTP_CODE})"
  echo "Response: ${CLIENT_UPDATE_BODY}" | jq '.'
fi

echo ""

echo "=========================================="
echo "✅ Update Complete!"
echo "=========================================="
echo ""
echo "🆔 Updated Resources:"
echo "   Organization ID: ${ORG_ID}"
echo "   Tenant ID:       ${TENANT_ID}"
echo "   Client ID:       ${CLIENT_ID}"
echo ""
echo "🧪 Test Authorization Code Flow:"
echo "   open \"${AUTHORIZATION_SERVER_URL}/${TENANT_ID}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=http://localhost:3000/callback/&scope=openid%20profile%20email&state=test-state\""
echo ""
