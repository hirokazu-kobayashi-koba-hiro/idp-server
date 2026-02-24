#!/bin/bash
set -e

# Third Party Integration Example Setup Script
# Sets up a complete third-party integration environment with Web, Mobile, and M2M clients.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"
ONBOARDING_REQUEST="${SCRIPT_DIR}/onboarding-request.json"

echo "=========================================="
echo "Third Party Integration Example Setup"
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

# Validate required variables
if [ -z "${AUTHORIZATION_SERVER_URL}" ]; then
  echo "Error: AUTHORIZATION_SERVER_URL not set in .env"
  exit 1
fi

if [ -z "${ADMIN_TENANT_ID}" ]; then
  echo "Error: ADMIN_TENANT_ID not set in .env"
  exit 1
fi

if [ -z "${ADMIN_USER_EMAIL}" ]; then
  echo "Error: ADMIN_USER_EMAIL not set in .env"
  exit 1
fi

echo "Environment variables loaded"
echo "   Server: ${AUTHORIZATION_SERVER_URL}"
echo "   Tenant: ${ADMIN_TENANT_ID}"
echo "   Admin:  ${ADMIN_USER_EMAIL}"
echo ""

# Step 1: Get system administrator access token
echo "Step 1: Getting system administrator access token..."
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
  echo "Error: Failed to get access token"
  echo "Response: ${TOKEN_RESPONSE}"
  exit 1
fi

echo "Access token obtained: ${SYSTEM_ACCESS_TOKEN:0:20}..."
echo ""

# Step 2: Execute onboarding API (Organization + Organizer Tenant + Admin User + Client)
echo "Step 2: Executing onboarding API..."
ONBOARDING_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  "${AUTHORIZATION_SERVER_URL}/v1/management/onboarding" \
  -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d @"${ONBOARDING_REQUEST}")

HTTP_CODE=$(echo "${ONBOARDING_RESPONSE}" | tail -n1)
RESPONSE_BODY=$(echo "${ONBOARDING_RESPONSE}" | sed '$d')

if [ "${HTTP_CODE}" = "201" ]; then
  echo "Onboarding successful!"
else
  echo "Onboarding failed (HTTP ${HTTP_CODE})"
  echo "${RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "${RESPONSE_BODY}"
  exit 1
fi

# Extract IDs from onboarding-request.json
ORG_ID=$(jq -r '.organization.id' "${ONBOARDING_REQUEST}")
ORGANIZER_TENANT_ID=$(jq -r '.tenant.id' "${ONBOARDING_REQUEST}")
ADMIN_EMAIL=$(jq -r '.user.email' "${ONBOARDING_REQUEST}")
ADMIN_PASSWORD=$(jq -r '.user.raw_password' "${ONBOARDING_REQUEST}")
ORG_CLIENT_ID=$(jq -r '.client.client_id' "${ONBOARDING_REQUEST}")
ORG_CLIENT_SECRET=$(jq -r '.client.client_secret' "${ONBOARDING_REQUEST}")

echo "   Organization ID:      ${ORG_ID}"
echo "   Organizer Tenant ID:  ${ORGANIZER_TENANT_ID}"
echo ""

# Step 3: Get organizer admin access token
echo "Step 3: Getting organizer admin access token..."
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

# From here, use organization-level APIs
ORG_BASE_URL="${AUTHORIZATION_SERVER_URL}/v1/management/organizations/${ORG_ID}/tenants"

# Step 4: Create public tenant (password policy + session config)
echo "Step 4: Creating public tenant..."

PUBLIC_TENANT_FILE="${SCRIPT_DIR}/public-tenant-request.json"
PUBLIC_TENANT_ID=$(jq -r '.tenant.id' "${PUBLIC_TENANT_FILE}")

TENANT_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  "${ORG_BASE_URL}" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d @"${PUBLIC_TENANT_FILE}")

HTTP_CODE=$(echo "${TENANT_RESPONSE}" | tail -n1)
RESPONSE_BODY=$(echo "${TENANT_RESPONSE}" | sed '$d')

if [ "${HTTP_CODE}" = "200" ] || [ "${HTTP_CODE}" = "201" ]; then
  echo "Public tenant created: ${PUBLIC_TENANT_ID}"
else
  echo "Warning: Public tenant creation failed (HTTP ${HTTP_CODE})"
  echo "${RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "${RESPONSE_BODY}"
fi
echo ""

# Step 5: Create authentication configuration (initial-registration)
echo "Step 5: Creating authentication configuration (initial-registration)..."

AUTH_CONFIG_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authentication-configurations" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d @"${SCRIPT_DIR}/authentication-config-initial-registration.json")

HTTP_CODE=$(echo "${AUTH_CONFIG_RESPONSE}" | tail -n1)
RESPONSE_BODY=$(echo "${AUTH_CONFIG_RESPONSE}" | sed '$d')

if [ "${HTTP_CODE}" = "200" ] || [ "${HTTP_CODE}" = "201" ]; then
  echo "Authentication configuration created"
else
  echo "Warning: Authentication configuration creation failed (HTTP ${HTTP_CODE})"
  echo "${RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "${RESPONSE_BODY}"
fi
echo ""

# Step 6: Create authentication policy (password only)
echo "Step 6: Creating authentication policy (password only)..."

POLICY_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authentication-policies" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d @"${SCRIPT_DIR}/authentication-policy-oauth.json")

HTTP_CODE=$(echo "${POLICY_RESPONSE}" | tail -n1)
RESPONSE_BODY=$(echo "${POLICY_RESPONSE}" | sed '$d')

if [ "${HTTP_CODE}" = "200" ] || [ "${HTTP_CODE}" = "201" ]; then
  echo "Authentication policy created"
else
  echo "Warning: Authentication policy creation failed (HTTP ${HTTP_CODE})"
  echo "${RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "${RESPONSE_BODY}"
fi
echo ""

# Step 7: Create Web application client (Confidential)
echo "Step 7: Creating Web application client (Confidential)..."

WEB_CLIENT_FILE="${SCRIPT_DIR}/client-web-request.json"

WEB_CLIENT_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/clients" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d @"${WEB_CLIENT_FILE}")

HTTP_CODE=$(echo "${WEB_CLIENT_RESPONSE}" | tail -n1)
RESPONSE_BODY=$(echo "${WEB_CLIENT_RESPONSE}" | sed '$d')

if [ "${HTTP_CODE}" = "200" ] || [ "${HTTP_CODE}" = "201" ]; then
  echo "Web client created"
else
  echo "Warning: Web client creation failed (HTTP ${HTTP_CODE})"
  echo "${RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "${RESPONSE_BODY}"
fi
echo ""

# Step 8: Create Mobile application client (Public + PKCE)
echo "Step 8: Creating Mobile application client (Public + PKCE)..."

MOBILE_CLIENT_FILE="${SCRIPT_DIR}/client-mobile-request.json"

MOBILE_CLIENT_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/clients" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d @"${MOBILE_CLIENT_FILE}")

HTTP_CODE=$(echo "${MOBILE_CLIENT_RESPONSE}" | tail -n1)
RESPONSE_BODY=$(echo "${MOBILE_CLIENT_RESPONSE}" | sed '$d')

if [ "${HTTP_CODE}" = "200" ] || [ "${HTTP_CODE}" = "201" ]; then
  echo "Mobile client created"
else
  echo "Warning: Mobile client creation failed (HTTP ${HTTP_CODE})"
  echo "${RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "${RESPONSE_BODY}"
fi
echo ""

# Step 9: Create M2M client (Client Credentials)
echo "Step 9: Creating M2M client (Client Credentials)..."

M2M_CLIENT_FILE="${SCRIPT_DIR}/client-m2m-request.json"

M2M_CLIENT_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/clients" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d @"${M2M_CLIENT_FILE}")

HTTP_CODE=$(echo "${M2M_CLIENT_RESPONSE}" | tail -n1)
RESPONSE_BODY=$(echo "${M2M_CLIENT_RESPONSE}" | sed '$d')

if [ "${HTTP_CODE}" = "200" ] || [ "${HTTP_CODE}" = "201" ]; then
  echo "M2M client created"
else
  echo "Warning: M2M client creation failed (HTTP ${HTTP_CODE})"
  echo "${RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "${RESPONSE_BODY}"
fi
echo ""

# Extract client details
WEB_CLIENT_ID=$(jq -r '.client_id' "${WEB_CLIENT_FILE}")
WEB_CLIENT_SECRET=$(jq -r '.client_secret' "${WEB_CLIENT_FILE}")
WEB_REDIRECT_URI=$(jq -r '.redirect_uris[0]' "${WEB_CLIENT_FILE}")
MOBILE_CLIENT_ID=$(jq -r '.client_id' "${MOBILE_CLIENT_FILE}")
MOBILE_REDIRECT_URI=$(jq -r '.redirect_uris[0]' "${MOBILE_CLIENT_FILE}")
M2M_CLIENT_ID=$(jq -r '.client_id' "${M2M_CLIENT_FILE}")
M2M_CLIENT_SECRET=$(jq -r '.client_secret' "${M2M_CLIENT_FILE}")

echo "=========================================="
echo "Setup Complete!"
echo "=========================================="
echo ""
echo "Created Resources:"
echo "   Organization ID:      ${ORG_ID}"
echo "   Organizer Tenant ID:  ${ORGANIZER_TENANT_ID}"
echo "   Public Tenant ID:     ${PUBLIC_TENANT_ID}"
echo ""
echo "Organization Admin:"
echo "   Email:    ${ADMIN_EMAIL}"
echo "   Password: ${ADMIN_PASSWORD}"
echo ""
echo "Admin Client (Organizer Tenant):"
echo "   Client ID:     ${ORG_CLIENT_ID}"
echo "   Client Secret: ${ORG_CLIENT_SECRET}"
echo ""
echo "Web Client (Confidential):"
echo "   Client ID:      ${WEB_CLIENT_ID}"
echo "   Client Alias:   third-party-web-app"
echo "   Client Secret:  ${WEB_CLIENT_SECRET}"
echo "   Redirect URI:   ${WEB_REDIRECT_URI}"
echo "   Auth Method:    client_secret_basic"
echo ""
echo "Mobile Client (Public + PKCE):"
echo "   Client ID:      ${MOBILE_CLIENT_ID}"
echo "   Client Alias:   third-party-mobile-app"
echo "   Redirect URI:   ${MOBILE_REDIRECT_URI}"
echo "   Auth Method:    none (PKCE required)"
echo ""
echo "M2M Client (Client Credentials):"
echo "   Client ID:      ${M2M_CLIENT_ID}"
echo "   Client Alias:   third-party-m2m-service"
echo "   Client Secret:  ${M2M_CLIENT_SECRET}"
echo "   Scope:          api:read api:write"
echo "   Auth Method:    client_secret_basic"
echo ""
echo "Settings:"
echo "   Password policy:     min 8 chars, no complexity requirement"
echo "   Account lock:        5 attempts / 15 min lockout"
echo "   Session:             24 hours, SameSite=Lax"
echo "   Token strategy:      AT 30min, RT 7 days, rotation=true, strategy=FIXED"
echo "   Custom scopes:       api:read, api:write"
echo "   Auth policy:         password only"
echo ""
echo "OIDC Discovery:"
echo "   ${AUTHORIZATION_SERVER_URL}/${PUBLIC_TENANT_ID}/.well-known/openid-configuration"
echo ""
echo "Test Web Client (Authorization Code Flow):"
echo "   1. Open browser:"
echo "      ${AUTHORIZATION_SERVER_URL}/${PUBLIC_TENANT_ID}/v1/authorizations?response_type=code&client_id=${WEB_CLIENT_ID}&redirect_uri=${WEB_REDIRECT_URI}&scope=openid%20profile%20email%20api%3Aread&state=test-state"
echo ""
echo "   2. Register or login with email/password"
echo ""
echo "   3. Exchange code for token:"
echo "      curl -X POST ${AUTHORIZATION_SERVER_URL}/${PUBLIC_TENANT_ID}/v1/tokens \\"
echo "        -u \"${WEB_CLIENT_ID}:${WEB_CLIENT_SECRET}\" \\"
echo "        -d \"grant_type=authorization_code\" \\"
echo "        -d \"code=YOUR_CODE\" \\"
echo "        -d \"redirect_uri=${WEB_REDIRECT_URI}\""
echo ""
echo "Test M2M Client (Client Credentials):"
echo "   curl -X POST ${AUTHORIZATION_SERVER_URL}/${PUBLIC_TENANT_ID}/v1/tokens \\"
echo "     -u \"${M2M_CLIENT_ID}:${M2M_CLIENT_SECRET}\" \\"
echo "     -d \"grant_type=client_credentials\" \\"
echo "     -d \"scope=api:read api:write\""
echo ""
