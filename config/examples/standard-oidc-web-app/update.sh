#!/bin/bash
set -e

# Standard OIDC Web Application Update Script
# This script updates existing tenant/client configurations

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"
ONBOARDING_REQUEST="${SCRIPT_DIR}/onboarding-request.json"
PUBLIC_TENANT_FILE="${SCRIPT_DIR}/public-tenant.json"
PUBLIC_CLIENT_FILE="${SCRIPT_DIR}/public-client.json"
PUBLIC_CLIENT2_FILE="${SCRIPT_DIR}/public-client2.json"

echo "=========================================="
echo "🔄 Standard OIDC Web App Update"
echo "=========================================="

# Load .env file
if [ ! -f "${ENV_FILE}" ]; then
  echo "❌ Error: .env file not found at ${ENV_FILE}"
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

# Read IDs from configuration files
echo "📖 Reading configuration from files..."
ORG_ID=$(jq -r '.organization.id' "${ONBOARDING_REQUEST}")
ORGANIZER_TENANT_ID=$(jq -r '.tenant.id' "${ONBOARDING_REQUEST}")
ADMIN_CLIENT_ID=$(jq -r '.client.client_id' "${ONBOARDING_REQUEST}")

if [ -f "${PUBLIC_TENANT_FILE}" ]; then
  PUBLIC_TENANT_ID=$(jq -r '.tenant.id' "${PUBLIC_TENANT_FILE}")
else
  PUBLIC_TENANT_ID=""
fi

if [ -f "${PUBLIC_CLIENT_FILE}" ]; then
  PUBLIC_CLIENT_ID=$(jq -r '.client_id' "${PUBLIC_CLIENT_FILE}")
else
  PUBLIC_CLIENT_ID=""
fi

if [ -f "${PUBLIC_CLIENT2_FILE}" ]; then
  PUBLIC_CLIENT2_ID=$(jq -r '.client_id' "${PUBLIC_CLIENT2_FILE}")
else
  PUBLIC_CLIENT2_ID=""
fi

echo "✅ Configuration loaded"
echo "   Organization ID:      ${ORG_ID}"
echo "   Organizer Tenant ID:  ${ORGANIZER_TENANT_ID}"
echo "   Admin Client ID:      ${ADMIN_CLIENT_ID}"
if [ -n "${PUBLIC_TENANT_ID}" ]; then
  echo "   Public Tenant ID:     ${PUBLIC_TENANT_ID}"
fi
if [ -n "${PUBLIC_CLIENT_ID}" ]; then
  echo "   Public Client ID:     ${PUBLIC_CLIENT_ID}"
fi
if [ -n "${PUBLIC_CLIENT2_ID}" ]; then
  echo "   Public Client 2 ID:   ${PUBLIC_CLIENT2_ID}"
fi
echo ""

# Step 2: Check if resources exist
echo "🔍 Step 2: Checking existing resources..."

# Check organizer tenant
ORGANIZER_TENANT_CHECK=$(curl -s -w "\n%{http_code}" -X GET \
  "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${ORGANIZER_TENANT_ID}" \
  -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}")

ORGANIZER_TENANT_HTTP_CODE=$(echo "${ORGANIZER_TENANT_CHECK}" | tail -n1)
ORGANIZER_TENANT_EXISTS=false
if [ "${ORGANIZER_TENANT_HTTP_CODE}" = "200" ]; then
  ORGANIZER_TENANT_EXISTS=true
  echo "   ✓ Organizer Tenant exists: ${ORGANIZER_TENANT_ID}"
else
  echo "   ✗ Organizer Tenant not found: ${ORGANIZER_TENANT_ID}"
fi

# Check admin client
ADMIN_CLIENT_CHECK=$(curl -s -w "\n%{http_code}" -X GET \
  "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${ORGANIZER_TENANT_ID}/clients/${ADMIN_CLIENT_ID}" \
  -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}")

ADMIN_CLIENT_HTTP_CODE=$(echo "${ADMIN_CLIENT_CHECK}" | tail -n1)
ADMIN_CLIENT_EXISTS=false
if [ "${ADMIN_CLIENT_HTTP_CODE}" = "200" ]; then
  ADMIN_CLIENT_EXISTS=true
  echo "   ✓ Admin Client exists: ${ADMIN_CLIENT_ID}"
else
  echo "   ✗ Admin Client not found: ${ADMIN_CLIENT_ID}"
fi

# Check public tenant
if [ -n "${PUBLIC_TENANT_ID}" ]; then
  PUBLIC_TENANT_CHECK=$(curl -s -w "\n%{http_code}" -X GET \
    "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${PUBLIC_TENANT_ID}" \
    -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}")

  PUBLIC_TENANT_HTTP_CODE=$(echo "${PUBLIC_TENANT_CHECK}" | tail -n1)
  PUBLIC_TENANT_EXISTS=false
  if [ "${PUBLIC_TENANT_HTTP_CODE}" = "200" ]; then
    PUBLIC_TENANT_EXISTS=true
    echo "   ✓ Public Tenant exists: ${PUBLIC_TENANT_ID}"
  else
    echo "   ✗ Public Tenant not found: ${PUBLIC_TENANT_ID}"
  fi
fi

# Check public client
if [ -n "${PUBLIC_CLIENT_ID}" ] && [ -n "${PUBLIC_TENANT_ID}" ]; then
  PUBLIC_CLIENT_CHECK=$(curl -s -w "\n%{http_code}" -X GET \
    "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${PUBLIC_TENANT_ID}/clients/${PUBLIC_CLIENT_ID}" \
    -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}")

  PUBLIC_CLIENT_HTTP_CODE=$(echo "${PUBLIC_CLIENT_CHECK}" | tail -n1)
  PUBLIC_CLIENT_EXISTS=false
  if [ "${PUBLIC_CLIENT_HTTP_CODE}" = "200" ]; then
    PUBLIC_CLIENT_EXISTS=true
    echo "   ✓ Public Client exists: ${PUBLIC_CLIENT_ID}"
  else
    echo "   ✗ Public Client not found: ${PUBLIC_CLIENT_ID}"
  fi
fi

# Check public client 2
if [ -n "${PUBLIC_CLIENT2_ID}" ] && [ -n "${PUBLIC_TENANT_ID}" ]; then
  PUBLIC_CLIENT2_CHECK=$(curl -s -w "\n%{http_code}" -X GET \
    "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${PUBLIC_TENANT_ID}/clients/${PUBLIC_CLIENT2_ID}" \
    -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}")

  PUBLIC_CLIENT2_HTTP_CODE=$(echo "${PUBLIC_CLIENT2_CHECK}" | tail -n1)
  PUBLIC_CLIENT2_EXISTS=false
  if [ "${PUBLIC_CLIENT2_HTTP_CODE}" = "200" ]; then
    PUBLIC_CLIENT2_EXISTS=true
    echo "   ✓ Public Client 2 exists: ${PUBLIC_CLIENT2_ID}"
  else
    echo "   ✗ Public Client 2 not found: ${PUBLIC_CLIENT2_ID}"
  fi
fi

echo ""

# Step 3: Determine action
if [ "${ORGANIZER_TENANT_EXISTS}" = "false" ]; then
  echo "⚠️  Resources not found. Please run ./setup.sh first."
  exit 1
fi

# Step 3: Update organizer tenant configuration
echo "🔄 Step 3: Updating organizer tenant configuration..."

ORGANIZER_TENANT_UPDATE_JSON=$(jq '.tenant' "${ONBOARDING_REQUEST}")

ORGANIZER_TENANT_UPDATE_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT \
  "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${ORGANIZER_TENANT_ID}" \
  -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "${ORGANIZER_TENANT_UPDATE_JSON}")

ORGANIZER_TENANT_UPDATE_HTTP_CODE=$(echo "${ORGANIZER_TENANT_UPDATE_RESPONSE}" | tail -n1)
ORGANIZER_TENANT_UPDATE_BODY=$(echo "${ORGANIZER_TENANT_UPDATE_RESPONSE}" | sed '$d')

if [ "${ORGANIZER_TENANT_UPDATE_HTTP_CODE}" = "200" ]; then
  echo "✅ Organizer tenant configuration updated"
else
  echo "❌ Organizer tenant update failed (HTTP ${ORGANIZER_TENANT_UPDATE_HTTP_CODE})"
  echo "Response: ${ORGANIZER_TENANT_UPDATE_BODY}" | jq '.' || echo "${ORGANIZER_TENANT_UPDATE_BODY}"
fi

echo ""

# Step 4: Update organizer authorization server configuration
echo "🔄 Step 4: Updating organizer authorization server configuration..."

ORGANIZER_AUTHZ_SERVER_UPDATE_JSON=$(jq '.authorization_server' "${ONBOARDING_REQUEST}")

ORGANIZER_AUTHZ_SERVER_UPDATE_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT \
  "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${ORGANIZER_TENANT_ID}/authorization-server" \
  -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "${ORGANIZER_AUTHZ_SERVER_UPDATE_JSON}")

ORGANIZER_AUTHZ_SERVER_UPDATE_HTTP_CODE=$(echo "${ORGANIZER_AUTHZ_SERVER_UPDATE_RESPONSE}" | tail -n1)
ORGANIZER_AUTHZ_SERVER_UPDATE_BODY=$(echo "${ORGANIZER_AUTHZ_SERVER_UPDATE_RESPONSE}" | sed '$d')

if [ "${ORGANIZER_AUTHZ_SERVER_UPDATE_HTTP_CODE}" = "200" ] || [ "${ORGANIZER_AUTHZ_SERVER_UPDATE_HTTP_CODE}" = "201" ]; then
  echo "✅ Organizer authorization server configuration updated"
else
  echo "❌ Organizer authorization server update failed (HTTP ${ORGANIZER_AUTHZ_SERVER_UPDATE_HTTP_CODE})"
  echo "Response: ${ORGANIZER_AUTHZ_SERVER_UPDATE_BODY}" | jq '.' || echo "${ORGANIZER_AUTHZ_SERVER_UPDATE_BODY}"
fi

echo ""

# Step 5: Update admin client configuration
echo "🔄 Step 5: Updating admin client configuration..."

ADMIN_CLIENT_UPDATE_JSON=$(jq '.client' "${ONBOARDING_REQUEST}")

ADMIN_CLIENT_UPDATE_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT \
  "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${ORGANIZER_TENANT_ID}/clients/${ADMIN_CLIENT_ID}" \
  -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "${ADMIN_CLIENT_UPDATE_JSON}")

ADMIN_CLIENT_UPDATE_HTTP_CODE=$(echo "${ADMIN_CLIENT_UPDATE_RESPONSE}" | tail -n1)
ADMIN_CLIENT_UPDATE_BODY=$(echo "${ADMIN_CLIENT_UPDATE_RESPONSE}" | sed '$d')

if [ "${ADMIN_CLIENT_UPDATE_HTTP_CODE}" = "200" ] || [ "${ADMIN_CLIENT_UPDATE_HTTP_CODE}" = "201" ]; then
  echo "✅ Admin client configuration updated"
else
  echo "❌ Admin client update failed (HTTP ${ADMIN_CLIENT_UPDATE_HTTP_CODE})"
  echo "Response: ${ADMIN_CLIENT_UPDATE_BODY}" | jq '.' || echo "${ADMIN_CLIENT_UPDATE_BODY}"
fi

echo ""

# Step 6: Update public tenant configuration
if [ -n "${PUBLIC_TENANT_ID}" ] && [ "${PUBLIC_TENANT_EXISTS}" = "true" ]; then
  echo "🔄 Step 6: Updating public tenant configuration..."

  # Extract tenant configuration only
  PUBLIC_TENANT_UPDATE_JSON=$(jq '.tenant' "${PUBLIC_TENANT_FILE}")

  PUBLIC_TENANT_UPDATE_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT \
    "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${PUBLIC_TENANT_ID}" \
    -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${PUBLIC_TENANT_UPDATE_JSON}")

  PUBLIC_TENANT_UPDATE_HTTP_CODE=$(echo "${PUBLIC_TENANT_UPDATE_RESPONSE}" | tail -n1)
  PUBLIC_TENANT_UPDATE_BODY=$(echo "${PUBLIC_TENANT_UPDATE_RESPONSE}" | sed '$d')

  if [ "${PUBLIC_TENANT_UPDATE_HTTP_CODE}" = "200" ]; then
    echo "✅ Public tenant configuration updated"
  else
    echo "❌ Public tenant update failed (HTTP ${PUBLIC_TENANT_UPDATE_HTTP_CODE})"
    echo "Response: ${PUBLIC_TENANT_UPDATE_BODY}" | jq '.' || echo "${PUBLIC_TENANT_UPDATE_BODY}"
  fi

  echo ""

  # Step 7: Update public authorization server configuration
  echo "🔄 Step 7: Updating public authorization server configuration..."

  PUBLIC_AUTHZ_SERVER_UPDATE_JSON=$(jq '.authorization_server' "${PUBLIC_TENANT_FILE}")

  PUBLIC_AUTHZ_SERVER_UPDATE_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT \
    "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${PUBLIC_TENANT_ID}/authorization-server" \
    -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${PUBLIC_AUTHZ_SERVER_UPDATE_JSON}")

  PUBLIC_AUTHZ_SERVER_UPDATE_HTTP_CODE=$(echo "${PUBLIC_AUTHZ_SERVER_UPDATE_RESPONSE}" | tail -n1)
  PUBLIC_AUTHZ_SERVER_UPDATE_BODY=$(echo "${PUBLIC_AUTHZ_SERVER_UPDATE_RESPONSE}" | sed '$d')

  if [ "${PUBLIC_AUTHZ_SERVER_UPDATE_HTTP_CODE}" = "200" ] || [ "${PUBLIC_AUTHZ_SERVER_UPDATE_HTTP_CODE}" = "201" ]; then
    echo "✅ Public authorization server configuration updated"
  else
    echo "❌ Public authorization server update failed (HTTP ${PUBLIC_AUTHZ_SERVER_UPDATE_HTTP_CODE})"
    echo "Response: ${PUBLIC_AUTHZ_SERVER_UPDATE_BODY}" | jq '.' || echo "${PUBLIC_AUTHZ_SERVER_UPDATE_BODY}"
  fi

  echo ""
else
  echo "⏭️  Step 6-7: Skipping public tenant update (not configured or not exists)"
  echo ""
fi

# Step 8: Update public client configuration
if [ -n "${PUBLIC_CLIENT_ID}" ] && [ -n "${PUBLIC_TENANT_ID}" ] && [ "${PUBLIC_CLIENT_EXISTS}" = "true" ]; then
  echo "🔄 Step 8: Updating public client configuration..."

  PUBLIC_CLIENT_UPDATE_JSON=$(cat "${PUBLIC_CLIENT_FILE}")

  PUBLIC_CLIENT_UPDATE_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT \
    "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${PUBLIC_TENANT_ID}/clients/${PUBLIC_CLIENT_ID}" \
    -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${PUBLIC_CLIENT_UPDATE_JSON}")

  PUBLIC_CLIENT_UPDATE_HTTP_CODE=$(echo "${PUBLIC_CLIENT_UPDATE_RESPONSE}" | tail -n1)
  PUBLIC_CLIENT_UPDATE_BODY=$(echo "${PUBLIC_CLIENT_UPDATE_RESPONSE}" | sed '$d')

  if [ "${PUBLIC_CLIENT_UPDATE_HTTP_CODE}" = "200" ] || [ "${PUBLIC_CLIENT_UPDATE_HTTP_CODE}" = "201" ]; then
    echo "✅ Public client configuration updated"
  else
    echo "❌ Public client update failed (HTTP ${PUBLIC_CLIENT_UPDATE_HTTP_CODE})"
    echo "Response: ${PUBLIC_CLIENT_UPDATE_BODY}" | jq '.' || echo "${PUBLIC_CLIENT_UPDATE_BODY}"
  fi

  echo ""
else
  echo "⏭️  Step 8: Skipping public client update (not configured or not exists)"
  echo ""
fi

# Step 9: Update public client 2 configuration
if [ -n "${PUBLIC_CLIENT2_ID}" ] && [ -n "${PUBLIC_TENANT_ID}" ] && [ "${PUBLIC_CLIENT2_EXISTS}" = "true" ]; then
  echo "🔄 Step 9: Updating public client 2 configuration..."

  PUBLIC_CLIENT2_UPDATE_JSON=$(cat "${PUBLIC_CLIENT2_FILE}")

  PUBLIC_CLIENT2_UPDATE_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT \
    "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${PUBLIC_TENANT_ID}/clients/${PUBLIC_CLIENT2_ID}" \
    -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${PUBLIC_CLIENT2_UPDATE_JSON}")

  PUBLIC_CLIENT2_UPDATE_HTTP_CODE=$(echo "${PUBLIC_CLIENT2_UPDATE_RESPONSE}" | tail -n1)
  PUBLIC_CLIENT2_UPDATE_BODY=$(echo "${PUBLIC_CLIENT2_UPDATE_RESPONSE}" | sed '$d')

  if [ "${PUBLIC_CLIENT2_UPDATE_HTTP_CODE}" = "200" ] || [ "${PUBLIC_CLIENT2_UPDATE_HTTP_CODE}" = "201" ]; then
    echo "✅ Public client 2 configuration updated"
  else
    echo "❌ Public client 2 update failed (HTTP ${PUBLIC_CLIENT2_UPDATE_HTTP_CODE})"
    echo "Response: ${PUBLIC_CLIENT2_UPDATE_BODY}" | jq '.' || echo "${PUBLIC_CLIENT2_UPDATE_BODY}"
  fi

  echo ""
else
  echo "⏭️  Step 9: Skipping public client 2 update (not configured or not exists)"
  echo ""
fi

echo "=========================================="
echo "✅ Update Complete!"
echo "=========================================="
echo ""
echo "🆔 Updated Resources:"
echo "   Organization ID:      ${ORG_ID}"
echo "   Organizer Tenant ID:  ${ORGANIZER_TENANT_ID}"
if [ -n "${PUBLIC_TENANT_ID}" ]; then
  echo "   Public Tenant ID:     ${PUBLIC_TENANT_ID}"
fi
echo "   Admin Client ID:      ${ADMIN_CLIENT_ID}"
if [ -n "${PUBLIC_CLIENT_ID}" ]; then
  echo "   Public Client ID:     ${PUBLIC_CLIENT_ID}"
fi
if [ -n "${PUBLIC_CLIENT2_ID}" ]; then
  echo "   Public Client 2 ID:   ${PUBLIC_CLIENT2_ID}"
fi
echo ""
echo "🧪 Test Authorization Code Flow (Public Client):"
if [ -n "${PUBLIC_TENANT_ID}" ] && [ -n "${PUBLIC_CLIENT_ID}" ]; then
  echo "   open \"${AUTHORIZATION_SERVER_URL}/${PUBLIC_TENANT_ID}/v1/authorizations?response_type=code&client_id=${PUBLIC_CLIENT_ID}&redirect_uri=http://localhost:3000/callback/&scope=openid%20profile%20email&state=test-state\""
else
  echo "   (Public client not configured)"
fi
echo ""
