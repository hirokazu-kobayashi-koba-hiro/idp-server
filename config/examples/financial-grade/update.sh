#!/bin/bash
set -e

# Financial Grade Update Script
# This script updates existing tenant/client configurations

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"
ONBOARDING_REQUEST="${SCRIPT_DIR}/onboarding-request.json"
FINANCIAL_TENANT_FILE="${SCRIPT_DIR}/financial-tenant.json"
FINANCIAL_CLIENT_FILE="${SCRIPT_DIR}/financial-client.json"
AUTH_POLICY_FILE="${SCRIPT_DIR}/authentication-policy/oauth.json"

echo "=========================================="
echo "🔄 Financial Grade Update"
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

# Step 1: Get system admin access token
echo "🔐 Step 1: Getting system administrator access token..."

SYSTEM_TOKEN_RESPONSE=$(curl -s -X POST \
  "${AUTHORIZATION_SERVER_URL}/${ADMIN_TENANT_ID}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=password" \
  --data-urlencode "username=${ADMIN_USER_EMAIL}" \
  --data-urlencode "password=${ADMIN_USER_PASSWORD}" \
  --data-urlencode "client_id=${ADMIN_CLIENT_ID}" \
  --data-urlencode "client_secret=${ADMIN_CLIENT_SECRET}" \
  --data-urlencode "scope=account management")

SYSTEM_ACCESS_TOKEN=$(echo "${SYSTEM_TOKEN_RESPONSE}" | jq -r '.access_token')

if [ -z "${SYSTEM_ACCESS_TOKEN}" ] || [ "${SYSTEM_ACCESS_TOKEN}" = "null" ]; then
  echo "❌ Error: Failed to get system admin access token"
  echo "Response: ${SYSTEM_TOKEN_RESPONSE}"
  exit 1
fi

echo "✅ System admin access token obtained: ${SYSTEM_ACCESS_TOKEN:0:20}..."
echo ""

# Read IDs from configuration files
echo "📖 Reading configuration from files..."
ORG_ID=$(jq -r '.organization.id' "${ONBOARDING_REQUEST}")
ORGANIZER_TENANT_ID=$(jq -r '.tenant.id' "${ONBOARDING_REQUEST}")
ADMIN_CLIENT_ID=$(jq -r '.client.client_id' "${ONBOARDING_REQUEST}")

if [ -f "${FINANCIAL_TENANT_FILE}" ]; then
  FINANCIAL_TENANT_ID=$(jq -r '.tenant.id' "${FINANCIAL_TENANT_FILE}")
else
  FINANCIAL_TENANT_ID=""
fi

if [ -f "${FINANCIAL_CLIENT_FILE}" ]; then
  FINANCIAL_CLIENT_ID=$(jq -r '.client_id' "${FINANCIAL_CLIENT_FILE}")
else
  FINANCIAL_CLIENT_ID=""
fi

echo "✅ Configuration loaded"
echo "   Organization ID:        ${ORG_ID}"
echo "   Organizer Tenant ID:    ${ORGANIZER_TENANT_ID}"
echo "   Admin Client ID:        ${ADMIN_CLIENT_ID}"
if [ -n "${FINANCIAL_TENANT_ID}" ]; then
  echo "   Financial Tenant ID:    ${FINANCIAL_TENANT_ID}"
fi
if [ -n "${FINANCIAL_CLIENT_ID}" ]; then
  echo "   Financial Client ID:    ${FINANCIAL_CLIENT_ID}"
fi
echo ""

# Step 2: Get organization admin access token
echo "🔐 Step 2: Getting organization administrator access token..."
ORG_ADMIN_EMAIL="fapi-test@example.com"
ORG_ADMIN_PASSWORD="FapiCibaTestSecure123!"
ADMIN_CLIENT_SECRET="fapi-ciba-admin-secret-change-in-production-minimum-32-characters"

ORG_TOKEN_RESPONSE=$(curl -s -X POST \
  "${AUTHORIZATION_SERVER_URL}/${ORGANIZER_TENANT_ID}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=password" \
  --data-urlencode "username=${ORG_ADMIN_EMAIL}" \
  --data-urlencode "password=${ORG_ADMIN_PASSWORD}" \
  --data-urlencode "client_id=${ADMIN_CLIENT_ID}" \
  --data-urlencode "client_secret=${ADMIN_CLIENT_SECRET}" \
  --data-urlencode "scope=account management")

ORG_ACCESS_TOKEN=$(echo "${ORG_TOKEN_RESPONSE}" | jq -r '.access_token')

if [ -z "${ORG_ACCESS_TOKEN}" ] || [ "${ORG_ACCESS_TOKEN}" = "null" ]; then
  echo "❌ Error: Failed to get organization admin access token"
  echo "Response: ${ORG_TOKEN_RESPONSE}"
  exit 1
fi

echo "✅ Organization admin access token obtained: ${ORG_ACCESS_TOKEN:0:20}..."
echo ""

# Step 3: Update financial tenant configuration
if [ -n "${FINANCIAL_TENANT_ID}" ]; then
  echo "🔄 Step 3: Updating financial tenant configuration..."

  FINANCIAL_TENANT_UPDATE_JSON=$(jq '.tenant' "${FINANCIAL_TENANT_FILE}")

  TENANT_UPDATE_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT \
    "${AUTHORIZATION_SERVER_URL}/v1/management/organizations/${ORG_ID}/tenants/${FINANCIAL_TENANT_ID}" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${FINANCIAL_TENANT_UPDATE_JSON}")

  TENANT_UPDATE_HTTP_CODE=$(echo "${TENANT_UPDATE_RESPONSE}" | tail -n1)
  TENANT_UPDATE_BODY=$(echo "${TENANT_UPDATE_RESPONSE}" | sed '$d')

  if [ "${TENANT_UPDATE_HTTP_CODE}" = "200" ]; then
    echo "✅ Financial tenant configuration updated"
  else
    echo "❌ Financial tenant update failed (HTTP ${TENANT_UPDATE_HTTP_CODE})"
    echo "Response: ${TENANT_UPDATE_BODY}" | jq '.' || echo "${TENANT_UPDATE_BODY}"
  fi

  echo ""
fi

# Step 4: Update financial authorization server configuration
if [ -n "${FINANCIAL_TENANT_ID}" ]; then
  echo "🔄 Step 4: Updating financial authorization server configuration..."

  AUTHZ_SERVER_UPDATE_JSON=$(jq '.authorization_server' "${FINANCIAL_TENANT_FILE}")

  AUTHZ_SERVER_UPDATE_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT \
    "${AUTHORIZATION_SERVER_URL}/v1/management/organizations/${ORG_ID}/tenants/${FINANCIAL_TENANT_ID}/authorization-server" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${AUTHZ_SERVER_UPDATE_JSON}")

  AUTHZ_SERVER_UPDATE_HTTP_CODE=$(echo "${AUTHZ_SERVER_UPDATE_RESPONSE}" | tail -n1)
  AUTHZ_SERVER_UPDATE_BODY=$(echo "${AUTHZ_SERVER_UPDATE_RESPONSE}" | sed '$d')

  if [ "${AUTHZ_SERVER_UPDATE_HTTP_CODE}" = "200" ]; then
    echo "✅ Financial authorization server configuration updated"
  else
    echo "❌ Financial authorization server update failed (HTTP ${AUTHZ_SERVER_UPDATE_HTTP_CODE})"
    echo "Response: ${AUTHZ_SERVER_UPDATE_BODY}" | jq '.' || echo "${AUTHZ_SERVER_UPDATE_BODY}"
  fi

  echo ""
fi

# Step 5: Update financial client configurations
if [ -n "${FINANCIAL_TENANT_ID}" ]; then
  echo "🔄 Step 5: Updating financial client configurations..."

  # Define client files to update
  CLIENT_FILES=(
    "financial-client.json:self_signed_tls_client_auth"
    "private-key-jwt-client.json:private_key_jwt"
    "private-key-jwt-client-2.json:private_key_jwt"
    "tls-client-auth-client.json:tls_client_auth"
    "tls-client-auth-client-2.json:tls_client_auth"
  )

  for CLIENT_ENTRY in "${CLIENT_FILES[@]}"; do
    CLIENT_FILE="${CLIENT_ENTRY%%:*}"
    CLIENT_AUTH_METHOD="${CLIENT_ENTRY##*:}"
    CLIENT_FILE_PATH="${SCRIPT_DIR}/${CLIENT_FILE}"

    if [ ! -f "${CLIENT_FILE_PATH}" ]; then
      echo "   ⚠️  ${CLIENT_FILE} not found, skipping..."
      continue
    fi

    CLIENT_JSON=$(cat "${CLIENT_FILE_PATH}")
    CLIENT_ID=$(echo "${CLIENT_JSON}" | jq -r '.client_id')
    CLIENT_ALIAS=$(echo "${CLIENT_JSON}" | jq -r '.client_id_alias // .client_id')

    echo "   📝 Updating client: ${CLIENT_ALIAS} (${CLIENT_AUTH_METHOD})..."

    CLIENT_UPDATE_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT \
      "${AUTHORIZATION_SERVER_URL}/v1/management/organizations/${ORG_ID}/tenants/${FINANCIAL_TENANT_ID}/clients/${CLIENT_ID}" \
      -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
      -H "Content-Type: application/json" \
      -d "${CLIENT_JSON}")

    CLIENT_UPDATE_HTTP_CODE=$(echo "${CLIENT_UPDATE_RESPONSE}" | tail -n1)
    CLIENT_UPDATE_BODY=$(echo "${CLIENT_UPDATE_RESPONSE}" | sed '$d')

    if [ "${CLIENT_UPDATE_HTTP_CODE}" = "200" ]; then
      echo "   ✅ Updated: ${CLIENT_ID}"
    else
      echo "   ⚠️  Failed (HTTP ${CLIENT_UPDATE_HTTP_CODE})"
      echo "   Response: ${CLIENT_UPDATE_BODY}" | jq '.' 2>/dev/null || echo "   ${CLIENT_UPDATE_BODY}"
    fi
  done

  echo ""
fi

# Step 6: Update authentication policy
if [ -n "${FINANCIAL_TENANT_ID}" ] && [ -f "${AUTH_POLICY_FILE}" ]; then
  echo "🔄 Step 6: Updating authentication policy..."

  AUTH_POLICY_ID=$(jq -r '.id' "${AUTH_POLICY_FILE}")
  AUTH_POLICY_UPDATE_JSON=$(cat "${AUTH_POLICY_FILE}")

  POLICY_UPDATE_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT \
    "${AUTHORIZATION_SERVER_URL}/v1/management/organizations/${ORG_ID}/tenants/${FINANCIAL_TENANT_ID}/authentication-policies/${AUTH_POLICY_ID}" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${AUTH_POLICY_UPDATE_JSON}")

  POLICY_UPDATE_HTTP_CODE=$(echo "${POLICY_UPDATE_RESPONSE}" | tail -n1)
  POLICY_UPDATE_BODY=$(echo "${POLICY_UPDATE_RESPONSE}" | sed '$d')

  if [ "${POLICY_UPDATE_HTTP_CODE}" = "200" ]; then
    echo "✅ Authentication policy updated"
  else
    echo "❌ Authentication policy update failed (HTTP ${POLICY_UPDATE_HTTP_CODE})"
    echo "Response: ${POLICY_UPDATE_BODY}" | jq '.' || echo "${POLICY_UPDATE_BODY}"
  fi

  echo ""
fi

# Step 7: Update test user
FINANCIAL_USER_FILE="${SCRIPT_DIR}/financial-user.json"
if [ -n "${FINANCIAL_TENANT_ID}" ] && [ -f "${FINANCIAL_USER_FILE}" ]; then
  echo "🔄 Step 7: Updating test user..."

  FINANCIAL_USER_JSON=$(cat "${FINANCIAL_USER_FILE}")
  FINANCIAL_USER_ID=$(echo "${FINANCIAL_USER_JSON}" | jq -r '.sub')

  USER_UPDATE_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT \
    "${AUTHORIZATION_SERVER_URL}/v1/management/organizations/${ORG_ID}/tenants/${FINANCIAL_TENANT_ID}/users/${FINANCIAL_USER_ID}" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${FINANCIAL_USER_JSON}")

  USER_UPDATE_HTTP_CODE=$(echo "${USER_UPDATE_RESPONSE}" | tail -n1)
  USER_UPDATE_BODY=$(echo "${USER_UPDATE_RESPONSE}" | sed '$d')

  if [ "${USER_UPDATE_HTTP_CODE}" = "200" ]; then
    echo "✅ Test user updated"
  else
    echo "⚠️  Test user update failed (HTTP ${USER_UPDATE_HTTP_CODE})"
    echo "Response: ${USER_UPDATE_BODY}" | jq '.' 2>/dev/null || echo "${USER_UPDATE_BODY}"
  fi

  echo ""
fi

echo "=========================================="
echo "✅ Update Complete!"
echo "=========================================="
echo ""
echo "🔍 Run verification:"
echo "   ./verify.sh"
echo ""
echo "🧪 Test MTLS authentication:"
echo "   ./test-mtls-auth.sh"
echo ""
