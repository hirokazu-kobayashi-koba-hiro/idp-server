#!/bin/bash
set -e

# MFA (Password + Email OTP) Example - Update Script
# Updates public tenant, authorization server, and client settings.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"
ONBOARDING_REQUEST="${SCRIPT_DIR}/onboarding-request.json"

echo "=========================================="
echo "MFA (Password + Email OTP) Example Update"
echo "=========================================="

# Load .env file
if [ ! -f "${ENV_FILE}" ]; then
  echo "Error: .env file not found at ${ENV_FILE}"
  exit 1
fi

echo "Loading environment variables from .env..."
set -a
source "${ENV_FILE}"
set +a

echo "Environment variables loaded"
echo "   Server: ${AUTHORIZATION_SERVER_URL}"
echo ""

# Read IDs from configuration files
ORG_ID=$(jq -r '.organization.id' "${ONBOARDING_REQUEST}")
ORGANIZER_TENANT_ID=$(jq -r '.tenant.id' "${ONBOARDING_REQUEST}")
ADMIN_EMAIL=$(jq -r '.user.email' "${ONBOARDING_REQUEST}")
ADMIN_PASSWORD=$(jq -r '.user.raw_password' "${ONBOARDING_REQUEST}")
ORG_CLIENT_ID=$(jq -r '.client.client_id' "${ONBOARDING_REQUEST}")
ORG_CLIENT_SECRET=$(jq -r '.client.client_secret' "${ONBOARDING_REQUEST}")

PUBLIC_TENANT_FILE="${SCRIPT_DIR}/public-tenant-request.json"
PUBLIC_TENANT_ID=$(jq -r '.tenant.id' "${PUBLIC_TENANT_FILE}")

CLIENT_FILE="${SCRIPT_DIR}/client-request.json"
CLIENT_ID=$(jq -r '.client_id' "${CLIENT_FILE}")

echo "Resource IDs:"
echo "   Organization ID:      ${ORG_ID}"
echo "   Organizer Tenant ID:  ${ORGANIZER_TENANT_ID}"
echo "   Public Tenant ID:     ${PUBLIC_TENANT_ID}"
echo "   Client ID:            ${CLIENT_ID}"
echo ""

# Step 1: Get organizer admin access token
echo "Step 1: Getting organizer admin access token..."
ORG_TOKEN_RESPONSE=$(curl -s -X POST \
  "${AUTHORIZATION_SERVER_URL}/${ORGANIZER_TENANT_ID}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=password" \
  --data-urlencode "username=${ADMIN_EMAIL}" \
  --data-urlencode "password=${ADMIN_PASSWORD}" \
  --data-urlencode "client_id=${ORG_CLIENT_ID}" \
  --data-urlencode "client_secret=${ORG_CLIENT_SECRET}" \
  --data-urlencode "scope=openid profile email management")

ORG_ACCESS_TOKEN=$(echo "${ORG_TOKEN_RESPONSE}" | jq -r '.access_token')

if [ -z "${ORG_ACCESS_TOKEN}" ] || [ "${ORG_ACCESS_TOKEN}" = "null" ]; then
  echo "Error: Failed to get organizer admin token"
  echo "Response: ${ORG_TOKEN_RESPONSE}"
  exit 1
fi

echo "Organizer admin token obtained: ${ORG_ACCESS_TOKEN:0:20}..."
echo ""

ORG_BASE_URL="${AUTHORIZATION_SERVER_URL}/v1/management/organizations/${ORG_ID}/tenants"

# Step 2: Update public tenant
echo "Step 2: Updating public tenant..."
TENANT_JSON=$(jq '.tenant' "${PUBLIC_TENANT_FILE}")

TENANT_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT \
  "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "${TENANT_JSON}")

TENANT_HTTP_CODE=$(echo "${TENANT_RESPONSE}" | tail -n1)
TENANT_RESPONSE_BODY=$(echo "${TENANT_RESPONSE}" | sed '$d')

if [ "${TENANT_HTTP_CODE}" = "200" ] || [ "${TENANT_HTTP_CODE}" = "204" ]; then
  echo "Public tenant updated successfully"
else
  echo "Warning: Public tenant update failed (HTTP ${TENANT_HTTP_CODE})"
  echo "Response: ${TENANT_RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "${TENANT_RESPONSE_BODY}"
fi
echo ""

# Step 3: Update authorization server
echo "Step 3: Updating authorization server..."
AUTH_SERVER_JSON=$(jq '.authorization_server' "${PUBLIC_TENANT_FILE}")

AUTH_SERVER_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT \
  "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authorization-server" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "${AUTH_SERVER_JSON}")

AUTH_SERVER_HTTP_CODE=$(echo "${AUTH_SERVER_RESPONSE}" | tail -n1)
AUTH_SERVER_RESPONSE_BODY=$(echo "${AUTH_SERVER_RESPONSE}" | sed '$d')

if [ "${AUTH_SERVER_HTTP_CODE}" = "200" ] || [ "${AUTH_SERVER_HTTP_CODE}" = "204" ]; then
  echo "Authorization server updated successfully"
else
  echo "Warning: Authorization server update failed (HTTP ${AUTH_SERVER_HTTP_CODE})"
  echo "Response: ${AUTH_SERVER_RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "${AUTH_SERVER_RESPONSE_BODY}"
fi
echo ""

# Step 4: Update application client
echo "Step 4: Updating application client..."
CLIENT_JSON=$(cat "${CLIENT_FILE}")

CLIENT_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT \
  "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/clients/${CLIENT_ID}" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "${CLIENT_JSON}")

CLIENT_HTTP_CODE=$(echo "${CLIENT_RESPONSE}" | tail -n1)
CLIENT_RESPONSE_BODY=$(echo "${CLIENT_RESPONSE}" | sed '$d')

if [ "${CLIENT_HTTP_CODE}" = "200" ] || [ "${CLIENT_HTTP_CODE}" = "204" ]; then
  echo "Application client updated successfully"
else
  echo "Warning: Application client update failed (HTTP ${CLIENT_HTTP_CODE})"
  echo "Response: ${CLIENT_RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "${CLIENT_RESPONSE_BODY}"
fi
echo ""

echo "=========================================="
echo "Update Complete!"
echo "=========================================="
echo ""
echo "Updated Resources:"
echo "   Public Tenant ID:  ${PUBLIC_TENANT_ID}"
echo "   Client ID:         ${CLIENT_ID}"
echo ""
echo "Verify at:"
echo "   Discovery: ${AUTHORIZATION_SERVER_URL}/${PUBLIC_TENANT_ID}/.well-known/openid-configuration"
echo "   JWKS:      ${AUTHORIZATION_SERVER_URL}/${PUBLIC_TENANT_ID}/v1/jwks"
echo ""
