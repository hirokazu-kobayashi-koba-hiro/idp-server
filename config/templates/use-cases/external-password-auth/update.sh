#!/bin/bash
set -e

# External Password Auth - Use Case Update Script
# Updates public tenant, authorization server, authentication config, and client settings.
#
# Usage:
#   ./update.sh
#   ORGANIZATION_NAME=my-org ./update.sh

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../../.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"

ORGANIZATION_NAME="${ORGANIZATION_NAME:-external-password-auth}"
OUTPUT_DIR="${PROJECT_ROOT}/config/generated/${ORGANIZATION_NAME}"

echo "=========================================="
echo "External Password Auth Use Case Update"
echo "=========================================="

# --- Load .env ---
if [ ! -f "${ENV_FILE}" ]; then
  echo "Error: .env file not found at ${ENV_FILE}"
  exit 1
fi

set -a
source "${ENV_FILE}"
set +a

echo "Server:  ${AUTHORIZATION_SERVER_URL}"
echo ""

# --- Verify generated files exist ---
if [ ! -d "${OUTPUT_DIR}" ]; then
  echo "Error: Generated directory not found at ${OUTPUT_DIR}"
  echo "  Run setup.sh first, or set ORGANIZATION_NAME to match your setup."
  exit 1
fi

# Read IDs from generated files
ORG_ID=$(jq -r '.organization.id' "${OUTPUT_DIR}/onboarding.json")
ORGANIZER_TENANT_ID=$(jq -r '.tenant.id' "${OUTPUT_DIR}/onboarding.json")
ADMIN_EMAIL=$(jq -r '.user.email' "${OUTPUT_DIR}/onboarding.json")
ADMIN_PASSWORD=$(jq -r '.user.raw_password' "${OUTPUT_DIR}/onboarding.json")
ORG_CLIENT_ID=$(jq -r '.client.client_id' "${OUTPUT_DIR}/onboarding.json")
ORG_CLIENT_SECRET=$(jq -r '.client.client_secret' "${OUTPUT_DIR}/onboarding.json")

PUBLIC_TENANT_ID=$(jq -r '.tenant.id' "${OUTPUT_DIR}/public-tenant.json")
CLIENT_ID=$(jq -r '.client_id' "${OUTPUT_DIR}/public-client.json")
AUTH_CONFIG_ID=$(jq -r '.id' "${OUTPUT_DIR}/authentication-config-password.json")
AUTH_POLICY_ID=$(jq -r '.id' "${OUTPUT_DIR}/authentication-policy.json")

echo "Resource IDs:"
echo "   Organization ID:      ${ORG_ID}"
echo "   Organizer Tenant ID:  ${ORGANIZER_TENANT_ID}"
echo "   Public Tenant ID:     ${PUBLIC_TENANT_ID}"
echo "   Client ID:            ${CLIENT_ID}"
echo "   Auth Config ID:       ${AUTH_CONFIG_ID}"
echo "   Auth Policy ID:       ${AUTH_POLICY_ID}"
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
TENANT_JSON=$(jq '.tenant' "${OUTPUT_DIR}/public-tenant.json")

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
AUTH_SERVER_JSON=$(jq '.authorization_server' "${OUTPUT_DIR}/public-tenant.json")

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
CLIENT_JSON=$(cat "${OUTPUT_DIR}/public-client.json")

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

# Step 5: Update authentication configuration (external password)
echo "Step 5: Updating authentication configuration..."
AUTH_CONFIG_JSON=$(cat "${OUTPUT_DIR}/authentication-config-password.json")

AUTH_CONFIG_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT \
  "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authentication-configurations/${AUTH_CONFIG_ID}" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "${AUTH_CONFIG_JSON}")

AUTH_CONFIG_HTTP_CODE=$(echo "${AUTH_CONFIG_RESPONSE}" | tail -n1)
AUTH_CONFIG_RESPONSE_BODY=$(echo "${AUTH_CONFIG_RESPONSE}" | sed '$d')

if [ "${AUTH_CONFIG_HTTP_CODE}" = "200" ] || [ "${AUTH_CONFIG_HTTP_CODE}" = "204" ]; then
  echo "Authentication configuration updated successfully"
else
  echo "Warning: Authentication configuration update failed (HTTP ${AUTH_CONFIG_HTTP_CODE})"
  echo "Response: ${AUTH_CONFIG_RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "${AUTH_CONFIG_RESPONSE_BODY}"
fi
echo ""

# Step 6: Update authentication policy
echo "Step 6: Updating authentication policy..."
AUTH_POLICY_JSON=$(cat "${OUTPUT_DIR}/authentication-policy.json")

AUTH_POLICY_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT \
  "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authentication-policies/${AUTH_POLICY_ID}" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "${AUTH_POLICY_JSON}")

AUTH_POLICY_HTTP_CODE=$(echo "${AUTH_POLICY_RESPONSE}" | tail -n1)
AUTH_POLICY_RESPONSE_BODY=$(echo "${AUTH_POLICY_RESPONSE}" | sed '$d')

if [ "${AUTH_POLICY_HTTP_CODE}" = "200" ] || [ "${AUTH_POLICY_HTTP_CODE}" = "204" ]; then
  echo "Authentication policy updated successfully"
else
  echo "Warning: Authentication policy update failed (HTTP ${AUTH_POLICY_HTTP_CODE})"
  echo "Response: ${AUTH_POLICY_RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "${AUTH_POLICY_RESPONSE_BODY}"
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
