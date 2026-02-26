#!/bin/bash
set -e

# Financial-Grade (FAPI Advanced + CIBA) - Use Case Update Script
# Updates financial tenant, authorization server, clients, auth configs, and policies.
#
# Usage:
#   ./update.sh
#   ORGANIZATION_NAME=my-org ./update.sh

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../../.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"

ORGANIZATION_NAME="${ORGANIZATION_NAME:-financial-grade}"
OUTPUT_DIR="${PROJECT_ROOT}/config/generated/${ORGANIZATION_NAME}"

echo "=========================================="
echo "Financial-Grade (FAPI Advanced + CIBA) Use Case Update"
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

PUBLIC_TENANT_ID=$(jq -r '.tenant.id' "${OUTPUT_DIR}/financial-tenant.json")
TLS_CLIENT_ID=$(jq -r '.client_id' "${OUTPUT_DIR}/tls-client-auth-client.json")
PKJ_CLIENT_ID=$(jq -r '.client_id' "${OUTPUT_DIR}/private-key-jwt-client.json")
AUTH_CONFIG_IR_ID=$(jq -r '.id' "${OUTPUT_DIR}/authentication-config-initial-registration.json")
AUTH_CONFIG_FIDO2_ID=$(jq -r '.id' "${OUTPUT_DIR}/authentication-config-fido2.json")
OAUTH_POLICY_ID=$(jq -r '.id' "${OUTPUT_DIR}/authentication-policy-oauth.json")
CIBA_POLICY_ID=$(jq -r '.id' "${OUTPUT_DIR}/authentication-policy-ciba.json")
FINANCIAL_USER_ID=$(jq -r '.sub' "${OUTPUT_DIR}/financial-user.json")

echo "Resource IDs:"
echo "   Organization ID:      ${ORG_ID}"
echo "   Organizer Tenant ID:  ${ORGANIZER_TENANT_ID}"
echo "   Financial Tenant ID:  ${PUBLIC_TENANT_ID}"
echo "   TLS Client ID:        ${TLS_CLIENT_ID}"
echo "   PKJ Client ID:        ${PKJ_CLIENT_ID}"
echo "   Auth Config IR ID:    ${AUTH_CONFIG_IR_ID}"
echo "   Auth Config FIDO2 ID: ${AUTH_CONFIG_FIDO2_ID}"
echo "   OAuth Policy ID:      ${OAUTH_POLICY_ID}"
echo "   CIBA Policy ID:       ${CIBA_POLICY_ID}"
echo "   User ID:              ${FINANCIAL_USER_ID}"
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

# Step 2: Update financial tenant
echo "Step 2: Updating financial tenant..."
TENANT_JSON=$(jq '.tenant' "${OUTPUT_DIR}/financial-tenant.json")

TENANT_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT \
  "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "${TENANT_JSON}")

TENANT_HTTP_CODE=$(echo "${TENANT_RESPONSE}" | tail -n1)
TENANT_RESPONSE_BODY=$(echo "${TENANT_RESPONSE}" | sed '$d')

if [ "${TENANT_HTTP_CODE}" = "200" ] || [ "${TENANT_HTTP_CODE}" = "204" ]; then
  echo "Financial tenant updated successfully"
else
  echo "Warning: Financial tenant update failed (HTTP ${TENANT_HTTP_CODE})"
  echo "Response: ${TENANT_RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "${TENANT_RESPONSE_BODY}"
fi
echo ""

# Step 3: Update authorization server
echo "Step 3: Updating authorization server..."
AUTH_SERVER_JSON=$(jq '.authorization_server' "${OUTPUT_DIR}/financial-tenant.json")

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

# Step 4: Update tls_client_auth client
echo "Step 4: Updating tls_client_auth client..."
TLS_CLIENT_JSON=$(cat "${OUTPUT_DIR}/tls-client-auth-client.json")

TLS_CLIENT_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT \
  "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/clients/${TLS_CLIENT_ID}" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "${TLS_CLIENT_JSON}")

TLS_CLIENT_HTTP_CODE=$(echo "${TLS_CLIENT_RESPONSE}" | tail -n1)
TLS_CLIENT_RESPONSE_BODY=$(echo "${TLS_CLIENT_RESPONSE}" | sed '$d')

if [ "${TLS_CLIENT_HTTP_CODE}" = "200" ] || [ "${TLS_CLIENT_HTTP_CODE}" = "204" ]; then
  echo "tls_client_auth client updated successfully"
else
  echo "Warning: tls_client_auth client update failed (HTTP ${TLS_CLIENT_HTTP_CODE})"
  echo "Response: ${TLS_CLIENT_RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "${TLS_CLIENT_RESPONSE_BODY}"
fi
echo ""

# Step 5: Update private_key_jwt client
echo "Step 5: Updating private_key_jwt client..."
PKJ_CLIENT_JSON=$(cat "${OUTPUT_DIR}/private-key-jwt-client.json")

PKJ_CLIENT_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT \
  "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/clients/${PKJ_CLIENT_ID}" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "${PKJ_CLIENT_JSON}")

PKJ_CLIENT_HTTP_CODE=$(echo "${PKJ_CLIENT_RESPONSE}" | tail -n1)
PKJ_CLIENT_RESPONSE_BODY=$(echo "${PKJ_CLIENT_RESPONSE}" | sed '$d')

if [ "${PKJ_CLIENT_HTTP_CODE}" = "200" ] || [ "${PKJ_CLIENT_HTTP_CODE}" = "204" ]; then
  echo "private_key_jwt client updated successfully"
else
  echo "Warning: private_key_jwt client update failed (HTTP ${PKJ_CLIENT_HTTP_CODE})"
  echo "Response: ${PKJ_CLIENT_RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "${PKJ_CLIENT_RESPONSE_BODY}"
fi
echo ""

# Step 6: Update authentication configuration (initial-registration)
echo "Step 6: Updating authentication configuration (initial-registration)..."
AUTH_CONFIG_IR_JSON=$(cat "${OUTPUT_DIR}/authentication-config-initial-registration.json")

AUTH_CONFIG_IR_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT \
  "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authentication-configurations/${AUTH_CONFIG_IR_ID}" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "${AUTH_CONFIG_IR_JSON}")

AUTH_CONFIG_IR_HTTP_CODE=$(echo "${AUTH_CONFIG_IR_RESPONSE}" | tail -n1)
AUTH_CONFIG_IR_RESPONSE_BODY=$(echo "${AUTH_CONFIG_IR_RESPONSE}" | sed '$d')

if [ "${AUTH_CONFIG_IR_HTTP_CODE}" = "200" ] || [ "${AUTH_CONFIG_IR_HTTP_CODE}" = "204" ]; then
  echo "Authentication configuration (initial-registration) updated successfully"
else
  echo "Warning: Authentication configuration (initial-registration) update failed (HTTP ${AUTH_CONFIG_IR_HTTP_CODE})"
  echo "Response: ${AUTH_CONFIG_IR_RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "${AUTH_CONFIG_IR_RESPONSE_BODY}"
fi
echo ""

# Step 7: Update authentication configuration (FIDO2)
echo "Step 7: Updating authentication configuration (FIDO2)..."
AUTH_CONFIG_FIDO2_JSON=$(cat "${OUTPUT_DIR}/authentication-config-fido2.json")

AUTH_CONFIG_FIDO2_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT \
  "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authentication-configurations/${AUTH_CONFIG_FIDO2_ID}" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "${AUTH_CONFIG_FIDO2_JSON}")

AUTH_CONFIG_FIDO2_HTTP_CODE=$(echo "${AUTH_CONFIG_FIDO2_RESPONSE}" | tail -n1)
AUTH_CONFIG_FIDO2_RESPONSE_BODY=$(echo "${AUTH_CONFIG_FIDO2_RESPONSE}" | sed '$d')

if [ "${AUTH_CONFIG_FIDO2_HTTP_CODE}" = "200" ] || [ "${AUTH_CONFIG_FIDO2_HTTP_CODE}" = "204" ]; then
  echo "Authentication configuration (FIDO2) updated successfully"
else
  echo "Warning: Authentication configuration (FIDO2) update failed (HTTP ${AUTH_CONFIG_FIDO2_HTTP_CODE})"
  echo "Response: ${AUTH_CONFIG_FIDO2_RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "${AUTH_CONFIG_FIDO2_RESPONSE_BODY}"
fi
echo ""

# Step 8: Update OAuth authentication policy
echo "Step 8: Updating OAuth authentication policy..."
OAUTH_POLICY_JSON=$(cat "${OUTPUT_DIR}/authentication-policy-oauth.json")

OAUTH_POLICY_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT \
  "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authentication-policies/${OAUTH_POLICY_ID}" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "${OAUTH_POLICY_JSON}")

OAUTH_POLICY_HTTP_CODE=$(echo "${OAUTH_POLICY_RESPONSE}" | tail -n1)
OAUTH_POLICY_RESPONSE_BODY=$(echo "${OAUTH_POLICY_RESPONSE}" | sed '$d')

if [ "${OAUTH_POLICY_HTTP_CODE}" = "200" ] || [ "${OAUTH_POLICY_HTTP_CODE}" = "204" ]; then
  echo "OAuth authentication policy updated successfully"
else
  echo "Warning: OAuth authentication policy update failed (HTTP ${OAUTH_POLICY_HTTP_CODE})"
  echo "Response: ${OAUTH_POLICY_RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "${OAUTH_POLICY_RESPONSE_BODY}"
fi
echo ""

# Step 9: Update CIBA authentication policy
echo "Step 9: Updating CIBA authentication policy..."
CIBA_POLICY_JSON=$(cat "${OUTPUT_DIR}/authentication-policy-ciba.json")

CIBA_POLICY_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT \
  "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authentication-policies/${CIBA_POLICY_ID}" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "${CIBA_POLICY_JSON}")

CIBA_POLICY_HTTP_CODE=$(echo "${CIBA_POLICY_RESPONSE}" | tail -n1)
CIBA_POLICY_RESPONSE_BODY=$(echo "${CIBA_POLICY_RESPONSE}" | sed '$d')

if [ "${CIBA_POLICY_HTTP_CODE}" = "200" ] || [ "${CIBA_POLICY_HTTP_CODE}" = "204" ]; then
  echo "CIBA authentication policy updated successfully"
else
  echo "Warning: CIBA authentication policy update failed (HTTP ${CIBA_POLICY_HTTP_CODE})"
  echo "Response: ${CIBA_POLICY_RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "${CIBA_POLICY_RESPONSE_BODY}"
fi
echo ""

# Step 10: Update test user
echo "Step 10: Updating test user..."
FINANCIAL_USER_JSON=$(cat "${OUTPUT_DIR}/financial-user.json")

USER_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT \
  "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/users/${FINANCIAL_USER_ID}" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "${FINANCIAL_USER_JSON}")

USER_HTTP_CODE=$(echo "${USER_RESPONSE}" | tail -n1)
USER_RESPONSE_BODY=$(echo "${USER_RESPONSE}" | sed '$d')

if [ "${USER_HTTP_CODE}" = "200" ] || [ "${USER_HTTP_CODE}" = "204" ]; then
  echo "Test user updated successfully"
else
  echo "Warning: Test user update failed (HTTP ${USER_HTTP_CODE})"
  echo "Response: ${USER_RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "${USER_RESPONSE_BODY}"
fi
echo ""

echo "=========================================="
echo "Update Complete!"
echo "=========================================="
echo ""
echo "Updated Resources:"
echo "   Financial Tenant ID:  ${PUBLIC_TENANT_ID}"
echo "   TLS Client ID:        ${TLS_CLIENT_ID}"
echo "   PKJ Client ID:        ${PKJ_CLIENT_ID}"
echo ""
echo "Verify at:"
echo "   Discovery: ${AUTHORIZATION_SERVER_URL}/${PUBLIC_TENANT_ID}/.well-known/openid-configuration"
echo "   JWKS:      ${AUTHORIZATION_SERVER_URL}/${PUBLIC_TENANT_ID}/v1/jwks"
echo ""
