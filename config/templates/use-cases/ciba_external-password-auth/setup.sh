#!/bin/bash
set -e

# CIBA + External Password Auth - Use Case Setup Script
#
# Combines external password authentication (user resolution with device mapping)
# with CIBA backchannel authentication (no-action device notification mode).
#
# Flow:
#   1. External password auth creates user with authentication_devices via mapping rules
#   2. CIBA uses the mapped device for backchannel authentication notification
#   3. No device credentials (no FIDO-UAF, no device_secret_jwt)
#
# Prerequisites:
#   1. idp-server is running
#   2. System administrator tenant exists (initial setup completed)
#   3. .env file with admin credentials
#   4. Mock server is running (node mock-server.js)
#
# Usage:
#   ./setup.sh
#   ./setup.sh --dry-run

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../../.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"

DRY_RUN=false
[ "$1" = "--dry-run" ] && DRY_RUN=true

echo "=========================================="
echo "CIBA + External Password Auth Setup"
echo "=========================================="
echo ""

# --- Load .env ---
if [ ! -f "${ENV_FILE}" ]; then
  echo "Error: .env file not found at ${ENV_FILE}"
  exit 1
fi

set -a
source "${ENV_FILE}"
set +a

: "${AUTHORIZATION_SERVER_URL:?AUTHORIZATION_SERVER_URL is required in .env}"
: "${ADMIN_TENANT_ID:?ADMIN_TENANT_ID is required in .env}"
: "${ADMIN_USER_EMAIL:?ADMIN_USER_EMAIL is required in .env}"
: "${ADMIN_USER_PASSWORD:?ADMIN_USER_PASSWORD is required in .env}"
: "${ADMIN_CLIENT_ID:?ADMIN_CLIENT_ID is required in .env}"
: "${ADMIN_CLIENT_SECRET:?ADMIN_CLIENT_SECRET is required in .env}"

echo "Server:  ${AUTHORIZATION_SERVER_URL}"
echo "Admin:   ${ADMIN_USER_EMAIL}"
echo ""

# --- Helper: jq-based template substitution ---
substitute_template() {
  local template_file="$1"
  shift
  local result
  result=$(cat "${template_file}")
  while [ $# -gt 0 ]; do
    local key="$1"
    local value="$2"
    shift 2
    result=$(echo "${result}" | jq --arg k "\${${key}}" --arg v "${value}" '
      walk(if type == "string" then split($k) | join($v) else . end)
    ')
  done
  echo "${result}"
}

# --- Step 1: Get access token ---
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

ACCESS_TOKEN=$(echo "${TOKEN_RESPONSE}" | jq -r '.access_token')

if [ -z "${ACCESS_TOKEN}" ] || [ "${ACCESS_TOKEN}" = "null" ]; then
  echo "  Failed to get access token"
  echo "  ${TOKEN_RESPONSE}"
  exit 1
fi

echo "  Access token obtained: ${ACCESS_TOKEN:0:20}..."
echo ""

# --- Step 2: Onboarding ---
echo "Step 2: Executing onboarding..."

ORGANIZATION_ID="${ORGANIZATION_ID:-$(uuidgen | tr '[:upper:]' '[:lower:]')}"
ORGANIZER_TENANT_ID="${ORGANIZER_TENANT_ID:-$(uuidgen | tr '[:upper:]' '[:lower:]')}"
NEW_ADMIN_USER_SUB="${NEW_ADMIN_USER_SUB:-$(uuidgen | tr '[:upper:]' '[:lower:]')}"
NEW_ADMIN_CLIENT_ID="${NEW_ADMIN_CLIENT_ID:-$(uuidgen | tr '[:upper:]' '[:lower:]')}"
NEW_ADMIN_CLIENT_SECRET="${NEW_ADMIN_CLIENT_SECRET:-$(uuidgen | tr '[:upper:]' '[:lower:]')}"
ORGANIZATION_NAME="${ORGANIZATION_NAME:-ciba-ext-pw}"
COOKIE_NAME="${COOKIE_NAME:-SESSION}"
NEW_ADMIN_EMAIL="${NEW_ADMIN_EMAIL:-admin@example.com}"
NEW_ADMIN_PASSWORD="${NEW_ADMIN_PASSWORD:-ChangeMe123}"
TOKEN_SIGNING_KEY_ID="${TOKEN_SIGNING_KEY_ID:-signing_key_1}"
ID_TOKEN_SIGNING_KEY_ID="${ID_TOKEN_SIGNING_KEY_ID:-signing_key_1}"

# External authentication service settings
EXTERNAL_AUTH_URL="${EXTERNAL_AUTH_URL:-http://host.docker.internal:4002/auth/password}"
EXTERNAL_PROVIDER_ID="${EXTERNAL_PROVIDER_ID:-external-auth}"

# Customizable policy values
SESSION_TIMEOUT_SECONDS="${SESSION_TIMEOUT_SECONDS:-86400}"
ACCESS_TOKEN_DURATION="${ACCESS_TOKEN_DURATION:-3600}"
ID_TOKEN_DURATION="${ID_TOKEN_DURATION:-3600}"
REFRESH_TOKEN_DURATION="${REFRESH_TOKEN_DURATION:-86400}"

# CIBA configuration
CIBA_REQUEST_EXPIRES_IN="${CIBA_REQUEST_EXPIRES_IN:-120}"
CIBA_POLLING_INTERVAL="${CIBA_POLLING_INTERVAL:-5}"

# Device configuration
MAX_DEVICES="${MAX_DEVICES:-5}"

OUTPUT_DIR="${PROJECT_ROOT}/config/generated/${ORGANIZATION_NAME}"
mkdir -p "${OUTPUT_DIR}"
echo "  Output directory: ${OUTPUT_DIR}"
echo ""

# Read JWKS
JWKS_FILE="${SCRIPT_DIR}/jwks.json"
if [ -f "${JWKS_FILE}" ]; then
  JWKS_CONTENT=$(jq -c '.' "${JWKS_FILE}")
else
  echo "  Warning: jwks.json not found at ${JWKS_FILE}"
  JWKS_CONTENT='{"keys":[]}'
fi

ONBOARDING_JSON=$(substitute_template "${SCRIPT_DIR}/onboarding-template.json" \
  "ORGANIZATION_ID" "${ORGANIZATION_ID}" \
  "ORGANIZATION_NAME" "${ORGANIZATION_NAME}" \
  "ORGANIZER_TENANT_ID" "${ORGANIZER_TENANT_ID}" \
  "BASE_URL" "${AUTHORIZATION_SERVER_URL}" \
  "COOKIE_NAME" "${COOKIE_NAME}" \
  "JWKS_CONTENT" "${JWKS_CONTENT}" \
  "TOKEN_SIGNING_KEY_ID" "${TOKEN_SIGNING_KEY_ID}" \
  "ID_TOKEN_SIGNING_KEY_ID" "${ID_TOKEN_SIGNING_KEY_ID}" \
  "ADMIN_USER_SUB" "${NEW_ADMIN_USER_SUB}" \
  "ADMIN_EMAIL" "${NEW_ADMIN_EMAIL}" \
  "ADMIN_PASSWORD" "${NEW_ADMIN_PASSWORD}" \
  "ADMIN_CLIENT_ID" "${NEW_ADMIN_CLIENT_ID}" \
  "ADMIN_CLIENT_SECRET" "${NEW_ADMIN_CLIENT_SECRET}")

echo "${ONBOARDING_JSON}" | jq '.' > "${OUTPUT_DIR}/onboarding.json"
echo "  Saved: ${OUTPUT_DIR}/onboarding.json"

ONBOARDING_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  "${AUTHORIZATION_SERVER_URL}/v1/management/onboarding?dry_run=${DRY_RUN}" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d @"${OUTPUT_DIR}/onboarding.json")

HTTP_CODE=$(echo "${ONBOARDING_RESPONSE}" | tail -n1)
RESPONSE_BODY=$(echo "${ONBOARDING_RESPONSE}" | sed '$d')

if [ "${HTTP_CODE}" = "201" ]; then
  echo "  Onboarding successful"
  echo "  Organization: ${ORGANIZATION_ID}"
  echo "  Organizer Tenant: ${ORGANIZER_TENANT_ID}"
else
  echo "  Failed (HTTP ${HTTP_CODE})"
  echo "  ${RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "  ${RESPONSE_BODY}"
  exit 1
fi
echo ""

# --- Step 3: Get organizer admin token ---
echo "Step 3: Getting organizer admin access token..."

ORG_TOKEN_RESPONSE=$(curl -s -X POST \
  "${AUTHORIZATION_SERVER_URL}/${ORGANIZER_TENANT_ID}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=password" \
  --data-urlencode "username=${NEW_ADMIN_EMAIL}" \
  --data-urlencode "password=${NEW_ADMIN_PASSWORD}" \
  --data-urlencode "client_id=${NEW_ADMIN_CLIENT_ID}" \
  --data-urlencode "client_secret=${NEW_ADMIN_CLIENT_SECRET}" \
  --data-urlencode "scope=openid profile email management")

ORG_ACCESS_TOKEN=$(echo "${ORG_TOKEN_RESPONSE}" | jq -r '.access_token')

if [ -z "${ORG_ACCESS_TOKEN}" ] || [ "${ORG_ACCESS_TOKEN}" = "null" ]; then
  echo "  Failed to get organizer admin token"
  echo "  ${ORG_TOKEN_RESPONSE}"
  exit 1
fi

echo "  Organizer admin token obtained: ${ORG_ACCESS_TOKEN:0:20}..."
echo ""

ORG_BASE_URL="${AUTHORIZATION_SERVER_URL}/v1/management/organizations/${ORGANIZATION_ID}/tenants"

# --- Step 4: Create public tenant ---
echo "Step 4: Creating public tenant (CIBA + external password auth)..."

PUBLIC_TENANT_ID="${PUBLIC_TENANT_ID:-$(uuidgen | tr '[:upper:]' '[:lower:]')}"
UI_BASE_URL="${UI_BASE_URL:-${AUTHORIZATION_SERVER_URL}}"

PUBLIC_TENANT_JSON=$(substitute_template "${SCRIPT_DIR}/public-tenant-template.json" \
  "PUBLIC_TENANT_ID" "${PUBLIC_TENANT_ID}" \
  "ORGANIZATION_NAME" "${ORGANIZATION_NAME}" \
  "BASE_URL" "${AUTHORIZATION_SERVER_URL}" \
  "UI_BASE_URL" "${UI_BASE_URL}" \
  "COOKIE_NAME" "${COOKIE_NAME}" \
  "JWKS_CONTENT" "${JWKS_CONTENT}" \
  "TOKEN_SIGNING_KEY_ID" "${TOKEN_SIGNING_KEY_ID}" \
  "ID_TOKEN_SIGNING_KEY_ID" "${ID_TOKEN_SIGNING_KEY_ID}")

PUBLIC_TENANT_JSON=$(echo "${PUBLIC_TENANT_JSON}" | jq \
  --argjson session_timeout "${SESSION_TIMEOUT_SECONDS}" \
  --argjson at_duration "${ACCESS_TOKEN_DURATION}" \
  --argjson idt_duration "${ID_TOKEN_DURATION}" \
  --argjson rt_duration "${REFRESH_TOKEN_DURATION}" \
  --argjson ciba_expires "${CIBA_REQUEST_EXPIRES_IN}" \
  --argjson ciba_interval "${CIBA_POLLING_INTERVAL}" \
  --argjson max_devices "${MAX_DEVICES}" \
  '
  .tenant.session_config.timeout_seconds = $session_timeout |
  .authorization_server.extension.access_token_duration = $at_duration |
  .authorization_server.extension.id_token_duration = $idt_duration |
  .authorization_server.extension.refresh_token_duration = $rt_duration |
  .authorization_server.extension.backchannel_authentication_request_expires_in = $ciba_expires |
  .authorization_server.extension.backchannel_authentication_polling_interval = $ciba_interval |
  .tenant.identity_policy_config.authentication_device_rule.max_devices = $max_devices
  ')

echo "${PUBLIC_TENANT_JSON}" | jq '.' > "${OUTPUT_DIR}/public-tenant.json"
echo "  Saved: ${OUTPUT_DIR}/public-tenant.json"

TENANT_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  "${ORG_BASE_URL}" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d @"${OUTPUT_DIR}/public-tenant.json")

HTTP_CODE=$(echo "${TENANT_RESPONSE}" | tail -n1)
RESPONSE_BODY=$(echo "${TENANT_RESPONSE}" | sed '$d')

if [ "${HTTP_CODE}" = "200" ] || [ "${HTTP_CODE}" = "201" ]; then
  echo "  Public tenant created: ${PUBLIC_TENANT_ID}"
else
  echo "  Failed (HTTP ${HTTP_CODE})"
  echo "  ${RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "  ${RESPONSE_BODY}"
  exit 1
fi
echo ""

# --- Step 5: Create authentication configuration (external password with device mapping) ---
echo "Step 5: Creating authentication configuration (external password + device mapping)..."

AUTH_CONFIG_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')

AUTH_CONFIG_JSON=$(substitute_template "${SCRIPT_DIR}/authentication-config-password-template.json" \
  "EXTERNAL_AUTH_URL" "${EXTERNAL_AUTH_URL}" \
  "EXTERNAL_PROVIDER_ID" "${EXTERNAL_PROVIDER_ID}")

AUTH_CONFIG_JSON=$(echo "${AUTH_CONFIG_JSON}" | jq --arg id "${AUTH_CONFIG_ID}" '. + {id: $id}')

echo "${AUTH_CONFIG_JSON}" | jq '.' > "${OUTPUT_DIR}/authentication-config-password.json"
echo "  Saved: ${OUTPUT_DIR}/authentication-config-password.json"

AUTH_CONFIG_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authentication-configurations" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d @"${OUTPUT_DIR}/authentication-config-password.json")

HTTP_CODE=$(echo "${AUTH_CONFIG_RESPONSE}" | tail -n1)
RESPONSE_BODY=$(echo "${AUTH_CONFIG_RESPONSE}" | sed '$d')

if [ "${HTTP_CODE}" = "200" ] || [ "${HTTP_CODE}" = "201" ]; then
  echo "  Authentication configuration created: ${AUTH_CONFIG_ID}"
  echo "  External URL: ${EXTERNAL_AUTH_URL}"
  echo "  Provider ID:  ${EXTERNAL_PROVIDER_ID}"
else
  echo "  Failed (HTTP ${HTTP_CODE})"
  echo "  ${RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "  ${RESPONSE_BODY}"
  exit 1
fi
echo ""

# --- Step 6a: Create authentication policy (OAuth - external password) ---
echo "Step 6a: Creating authentication policy (OAuth - external password)..."

AUTH_POLICY_OAUTH_ID="${AUTH_POLICY_OAUTH_ID:-$(uuidgen | tr '[:upper:]' '[:lower:]')}"
jq --arg id "${AUTH_POLICY_OAUTH_ID}" '. + {id: $id}' "${SCRIPT_DIR}/authentication-policy-oauth.json" > "${OUTPUT_DIR}/authentication-policy-oauth.json"
echo "  Saved: ${OUTPUT_DIR}/authentication-policy-oauth.json"

POLICY_OAUTH_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authentication-policies" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d @"${OUTPUT_DIR}/authentication-policy-oauth.json")

HTTP_CODE=$(echo "${POLICY_OAUTH_RESPONSE}" | tail -n1)
RESPONSE_BODY=$(echo "${POLICY_OAUTH_RESPONSE}" | sed '$d')

if [ "${HTTP_CODE}" = "200" ] || [ "${HTTP_CODE}" = "201" ]; then
  echo "  OAuth authentication policy created"
else
  echo "  Failed (HTTP ${HTTP_CODE})"
  echo "  ${RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "  ${RESPONSE_BODY}"
  exit 1
fi
echo ""

# --- Step 6b: Create authentication policy (CIBA - no-action notification) ---
echo "Step 6b: Creating authentication policy (CIBA - no-action notification)..."

AUTH_POLICY_CIBA_ID="${AUTH_POLICY_CIBA_ID:-$(uuidgen | tr '[:upper:]' '[:lower:]')}"
jq --arg id "${AUTH_POLICY_CIBA_ID}" '. + {id: $id}' "${SCRIPT_DIR}/authentication-policy-ciba.json" > "${OUTPUT_DIR}/authentication-policy-ciba.json"
echo "  Saved: ${OUTPUT_DIR}/authentication-policy-ciba.json"

POLICY_CIBA_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authentication-policies" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d @"${OUTPUT_DIR}/authentication-policy-ciba.json")

HTTP_CODE=$(echo "${POLICY_CIBA_RESPONSE}" | tail -n1)
RESPONSE_BODY=$(echo "${POLICY_CIBA_RESPONSE}" | sed '$d')

if [ "${HTTP_CODE}" = "200" ] || [ "${HTTP_CODE}" = "201" ]; then
  echo "  CIBA authentication policy created"
else
  echo "  Failed (HTTP ${HTTP_CODE})"
  echo "  ${RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "  ${RESPONSE_BODY}"
  exit 1
fi
echo ""

# --- Step 7: Create application client ---
echo "Step 7: Creating application client..."

CLIENT_ID="${CLIENT_ID:-$(uuidgen | tr '[:upper:]' '[:lower:]')}"
CLIENT_ALIAS="${CLIENT_ALIAS:-web-app}"
CLIENT_SECRET="${CLIENT_SECRET_VALUE:-$(uuidgen | tr '[:upper:]' '[:lower:]')}"
CLIENT_NAME="${CLIENT_NAME:-Web Application}"
REDIRECT_URI="${REDIRECT_URI:-http://localhost:3000/callback/}"

CLIENT_JSON=$(substitute_template "${SCRIPT_DIR}/public-client-template.json" \
  "CLIENT_ID" "${CLIENT_ID}" \
  "CLIENT_ALIAS" "${CLIENT_ALIAS}" \
  "CLIENT_SECRET" "${CLIENT_SECRET}" \
  "CLIENT_NAME" "${CLIENT_NAME}" \
  "REDIRECT_URI" "${REDIRECT_URI}")

echo "${CLIENT_JSON}" | jq '.' > "${OUTPUT_DIR}/public-client.json"
echo "  Saved: ${OUTPUT_DIR}/public-client.json"

CLIENT_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/clients" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d @"${OUTPUT_DIR}/public-client.json")

HTTP_CODE=$(echo "${CLIENT_RESPONSE}" | tail -n1)
RESPONSE_BODY=$(echo "${CLIENT_RESPONSE}" | sed '$d')

if [ "${HTTP_CODE}" = "200" ] || [ "${HTTP_CODE}" = "201" ]; then
  echo "  Client created: ${CLIENT_ID}"
else
  echo "  Failed (HTTP ${HTTP_CODE})"
  echo "  ${RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "  ${RESPONSE_BODY}"
  exit 1
fi
echo ""

# --- Summary ---
echo "=========================================="
echo "Setup Complete!"
echo "=========================================="
echo ""
echo "Settings applied:"
echo "  Authentication:   External password (${EXTERNAL_AUTH_URL})"
echo "  Provider ID:      ${EXTERNAL_PROVIDER_ID}"
echo "  Device mapping:   authentication_devices via user_mapping_rules"
echo "  CIBA mode:        no-action (notification only, no device auth)"
echo "  Session:          ${SESSION_TIMEOUT_SECONDS}s timeout"
echo "  Token duration:   AT=${ACCESS_TOKEN_DURATION}s, IDT=${ID_TOKEN_DURATION}s, RT=${REFRESH_TOKEN_DURATION}s"
echo "  CIBA config:      expires=${CIBA_REQUEST_EXPIRES_IN}s, interval=${CIBA_POLLING_INTERVAL}s"
echo ""
echo "Created Resources:"
echo "  Organization ID:      ${ORGANIZATION_ID}"
echo "  Organizer Tenant ID:  ${ORGANIZER_TENANT_ID}"
echo "  Public Tenant ID:     ${PUBLIC_TENANT_ID}"
echo ""
echo "Admin User:"
echo "  Email:    ${NEW_ADMIN_EMAIL}"
echo "  Password: ${NEW_ADMIN_PASSWORD}"
echo ""
echo "Admin Client (Organizer Tenant):"
echo "  Tenant ID:     ${ORGANIZER_TENANT_ID}"
echo "  Client ID:     ${NEW_ADMIN_CLIENT_ID}"
echo "  Client Secret: ${NEW_ADMIN_CLIENT_SECRET}"
echo ""
echo "Application Client (Public Tenant):"
echo "  Tenant ID:     ${PUBLIC_TENANT_ID}"
echo "  Client ID:     ${CLIENT_ID}"
echo "  Client Secret: ${CLIENT_SECRET}"
echo "  Redirect URI:  ${REDIRECT_URI}"
echo ""
echo "Generated JSON files:"
echo "  ${OUTPUT_DIR}/onboarding.json"
echo "  ${OUTPUT_DIR}/public-tenant.json"
echo "  ${OUTPUT_DIR}/authentication-config-password.json"
echo "  ${OUTPUT_DIR}/authentication-policy-oauth.json"
echo "  ${OUTPUT_DIR}/authentication-policy-ciba.json"
echo "  ${OUTPUT_DIR}/public-client.json"
echo ""
echo "Verification:"
echo "  1. Ensure mock server is running: node mock-server.js"
echo "  2. Run: cd config/templates/use-cases/ciba_external-password-auth && source helpers.sh && get_admin_token"
echo "  3. Run: source verify.sh"
