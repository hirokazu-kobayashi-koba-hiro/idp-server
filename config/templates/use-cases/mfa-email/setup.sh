#!/bin/bash
set -e

# MFA (Password + Email OTP) - Use Case Setup Script
#
# Prerequisites:
#   1. idp-server is running
#   2. System administrator tenant exists (initial setup completed)
#   3. .env file with admin credentials
#
# Usage:
#   ./setup.sh
#   ./setup.sh --dry-run
#
# Required .env variables:
#   AUTHORIZATION_SERVER_URL  - e.g. http://localhost:8080
#   ADMIN_TENANT_ID           - System admin tenant ID
#   ADMIN_USER_EMAIL          - Admin user email
#   ADMIN_USER_PASSWORD       - Admin user password
#   ADMIN_CLIENT_ID           - Admin client ID
#   ADMIN_CLIENT_SECRET       - Admin client secret

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../../.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"

DRY_RUN=false
[ "$1" = "--dry-run" ] && DRY_RUN=true

echo "=========================================="
echo "MFA (Password + Email OTP) Use Case Setup"
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
# Uses jq to safely replace ${PLACEHOLDER} strings, handling special characters in values
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

# --- Step 2: Onboarding (Organization + Organizer Tenant + Admin User + Client) ---
echo "Step 2: Executing onboarding..."

# Generate IDs (override via environment variables)
ORGANIZATION_ID="${ORGANIZATION_ID:-$(uuidgen | tr '[:upper:]' '[:lower:]')}"
ORGANIZER_TENANT_ID="${ORGANIZER_TENANT_ID:-$(uuidgen | tr '[:upper:]' '[:lower:]')}"
NEW_ADMIN_USER_SUB="${NEW_ADMIN_USER_SUB:-$(uuidgen | tr '[:upper:]' '[:lower:]')}"
NEW_ADMIN_CLIENT_ID="${NEW_ADMIN_CLIENT_ID:-$(uuidgen | tr '[:upper:]' '[:lower:]')}"
NEW_ADMIN_CLIENT_SECRET="${NEW_ADMIN_CLIENT_SECRET:-$(uuidgen | tr '[:upper:]' '[:lower:]')}"
ORGANIZATION_NAME="${ORGANIZATION_NAME:-mfa-email}"
COOKIE_NAME="${COOKIE_NAME:-SESSION}"
NEW_ADMIN_EMAIL="${NEW_ADMIN_EMAIL:-admin@example.com}"
NEW_ADMIN_PASSWORD="${NEW_ADMIN_PASSWORD:-ChangeMe123}"
TOKEN_SIGNING_KEY_ID="${TOKEN_SIGNING_KEY_ID:-signing_key_1}"
ID_TOKEN_SIGNING_KEY_ID="${ID_TOKEN_SIGNING_KEY_ID:-signing_key_1}"
UI_BASE_URL="${UI_BASE_URL:-${AUTHORIZATION_SERVER_URL}}"

# Customizable policy values (non-string, applied via jq overlay)
SESSION_TIMEOUT_SECONDS="${SESSION_TIMEOUT_SECONDS:-86400}"
PASSWORD_MIN_LENGTH="${PASSWORD_MIN_LENGTH:-8}"
PASSWORD_MAX_LENGTH="${PASSWORD_MAX_LENGTH:-72}"
PASSWORD_REQUIRE_UPPERCASE="${PASSWORD_REQUIRE_UPPERCASE:-false}"
PASSWORD_REQUIRE_LOWERCASE="${PASSWORD_REQUIRE_LOWERCASE:-false}"
PASSWORD_REQUIRE_NUMBER="${PASSWORD_REQUIRE_NUMBER:-false}"
PASSWORD_REQUIRE_SPECIAL_CHAR="${PASSWORD_REQUIRE_SPECIAL_CHAR:-false}"
PASSWORD_MAX_HISTORY="${PASSWORD_MAX_HISTORY:-0}"
PASSWORD_MAX_ATTEMPTS="${PASSWORD_MAX_ATTEMPTS:-5}"
PASSWORD_LOCKOUT_DURATION_SECONDS="${PASSWORD_LOCKOUT_DURATION_SECONDS:-900}"
ACCESS_TOKEN_DURATION="${ACCESS_TOKEN_DURATION:-3600}"
ID_TOKEN_DURATION="${ID_TOKEN_DURATION:-3600}"
REFRESH_TOKEN_DURATION="${REFRESH_TOKEN_DURATION:-86400}"
REGISTRATION_REQUIRED_FIELDS="${REGISTRATION_REQUIRED_FIELDS:-email,password,name}"

# Create output directory for generated JSON files
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
  echo "  Generate JWKS and save to: ${JWKS_FILE}"
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
  "ADMIN_CLIENT_SECRET" "${NEW_ADMIN_CLIENT_SECRET}" \
  "UI_BASE_URL" "${UI_BASE_URL}")

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

# From here, use organization-level APIs with ORG_ACCESS_TOKEN
ORG_BASE_URL="${AUTHORIZATION_SERVER_URL}/v1/management/organizations/${ORGANIZATION_ID}/tenants"

# --- Step 4: Create public tenant (with password policy) ---
echo "Step 4: Creating public tenant (password policy + session config)..."

PUBLIC_TENANT_ID="${PUBLIC_TENANT_ID:-$(uuidgen | tr '[:upper:]' '[:lower:]')}"

PUBLIC_TENANT_JSON=$(substitute_template "${SCRIPT_DIR}/public-tenant-template.json" \
  "PUBLIC_TENANT_ID" "${PUBLIC_TENANT_ID}" \
  "ORGANIZATION_NAME" "${ORGANIZATION_NAME}" \
  "BASE_URL" "${AUTHORIZATION_SERVER_URL}" \
  "UI_BASE_URL" "${UI_BASE_URL}" \
  "COOKIE_NAME" "${COOKIE_NAME}" \
  "JWKS_CONTENT" "${JWKS_CONTENT}" \
  "TOKEN_SIGNING_KEY_ID" "${TOKEN_SIGNING_KEY_ID}" \
  "ID_TOKEN_SIGNING_KEY_ID" "${ID_TOKEN_SIGNING_KEY_ID}")

# Overlay non-string values (integers and booleans) via jq
PUBLIC_TENANT_JSON=$(echo "${PUBLIC_TENANT_JSON}" | jq \
  --argjson session_timeout "${SESSION_TIMEOUT_SECONDS}" \
  --argjson pw_min "${PASSWORD_MIN_LENGTH}" \
  --argjson pw_max "${PASSWORD_MAX_LENGTH}" \
  --argjson pw_upper "${PASSWORD_REQUIRE_UPPERCASE}" \
  --argjson pw_lower "${PASSWORD_REQUIRE_LOWERCASE}" \
  --argjson pw_number "${PASSWORD_REQUIRE_NUMBER}" \
  --argjson pw_special "${PASSWORD_REQUIRE_SPECIAL_CHAR}" \
  --argjson pw_history "${PASSWORD_MAX_HISTORY}" \
  --argjson pw_attempts "${PASSWORD_MAX_ATTEMPTS}" \
  --argjson pw_lockout "${PASSWORD_LOCKOUT_DURATION_SECONDS}" \
  --argjson at_duration "${ACCESS_TOKEN_DURATION}" \
  --argjson idt_duration "${ID_TOKEN_DURATION}" \
  --argjson rt_duration "${REFRESH_TOKEN_DURATION}" \
  '
  .tenant.session_config.timeout_seconds = $session_timeout |
  .tenant.identity_policy_config.password_policy.min_length = $pw_min |
  .tenant.identity_policy_config.password_policy.max_length = $pw_max |
  .tenant.identity_policy_config.password_policy.require_uppercase = $pw_upper |
  .tenant.identity_policy_config.password_policy.require_lowercase = $pw_lower |
  .tenant.identity_policy_config.password_policy.require_number = $pw_number |
  .tenant.identity_policy_config.password_policy.require_special_char = $pw_special |
  .tenant.identity_policy_config.password_policy.max_history = $pw_history |
  .tenant.identity_policy_config.password_policy.max_attempts = $pw_attempts |
  .tenant.identity_policy_config.password_policy.lockout_duration_seconds = $pw_lockout |
  .authorization_server.extension.access_token_duration = $at_duration |
  .authorization_server.extension.id_token_duration = $idt_duration |
  .authorization_server.extension.refresh_token_duration = $rt_duration
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

# --- Step 5: Create authentication configuration (initial-registration) ---
echo "Step 5: Creating authentication configuration (initial-registration)..."

AUTH_CONFIG_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')

# Build required fields array from comma-separated REGISTRATION_REQUIRED_FIELDS
REQUIRED_FIELDS_JSON=$(echo "${REGISTRATION_REQUIRED_FIELDS}" | jq -R 'split(",")')

AUTH_CONFIG_JSON=$(jq \
  --arg id "${AUTH_CONFIG_ID}" \
  --argjson required "${REQUIRED_FIELDS_JSON}" \
  '. + {id: $id} | .interactions["initial-registration"].request.schema.required = $required' \
  "${SCRIPT_DIR}/authentication-config-initial-registration.json")

echo "${AUTH_CONFIG_JSON}" | jq '.' > "${OUTPUT_DIR}/authentication-config-initial-registration.json"
echo "  Saved: ${OUTPUT_DIR}/authentication-config-initial-registration.json"

AUTH_CONFIG_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authentication-configurations" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d @"${OUTPUT_DIR}/authentication-config-initial-registration.json")

HTTP_CODE=$(echo "${AUTH_CONFIG_RESPONSE}" | tail -n1)
RESPONSE_BODY=$(echo "${AUTH_CONFIG_RESPONSE}" | sed '$d')

if [ "${HTTP_CODE}" = "200" ] || [ "${HTTP_CODE}" = "201" ]; then
  echo "  Authentication configuration created: ${AUTH_CONFIG_ID}"
else
  echo "  Failed (HTTP ${HTTP_CODE})"
  echo "  ${RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "  ${RESPONSE_BODY}"
  exit 1
fi
echo ""

# --- Step 6: Create authentication configuration (email) ---
echo "Step 6: Creating authentication configuration (email)..."

EMAIL_AUTH_CONFIG_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')

EMAIL_AUTH_CONFIG_JSON=$(jq --arg id "${EMAIL_AUTH_CONFIG_ID}" '. + {id: $id}' \
  "${SCRIPT_DIR}/authentication-config-email.json")

echo "${EMAIL_AUTH_CONFIG_JSON}" | jq '.' > "${OUTPUT_DIR}/authentication-config-email.json"
echo "  Saved: ${OUTPUT_DIR}/authentication-config-email.json"

EMAIL_AUTH_CONFIG_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authentication-configurations" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d @"${OUTPUT_DIR}/authentication-config-email.json")

HTTP_CODE=$(echo "${EMAIL_AUTH_CONFIG_RESPONSE}" | tail -n1)
RESPONSE_BODY=$(echo "${EMAIL_AUTH_CONFIG_RESPONSE}" | sed '$d')

if [ "${HTTP_CODE}" = "200" ] || [ "${HTTP_CODE}" = "201" ]; then
  echo "  Email authentication configuration created: ${EMAIL_AUTH_CONFIG_ID}"
else
  echo "  Failed (HTTP ${HTTP_CODE})"
  echo "  ${RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "  ${RESPONSE_BODY}"
  exit 1
fi
echo ""

# --- Step 7: Create authentication policy (password AND email MFA) ---
echo "Step 7: Creating authentication policy (password + email MFA)..."

AUTH_POLICY_ID="${AUTH_POLICY_ID:-$(uuidgen | tr '[:upper:]' '[:lower:]')}"
jq --arg id "${AUTH_POLICY_ID}" '. + {id: $id}' "${SCRIPT_DIR}/authentication-policy.json" > "${OUTPUT_DIR}/authentication-policy.json"
echo "  Saved: ${OUTPUT_DIR}/authentication-policy.json"

POLICY_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authentication-policies" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d @"${OUTPUT_DIR}/authentication-policy.json")

HTTP_CODE=$(echo "${POLICY_RESPONSE}" | tail -n1)
RESPONSE_BODY=$(echo "${POLICY_RESPONSE}" | sed '$d')

if [ "${HTTP_CODE}" = "200" ] || [ "${HTTP_CODE}" = "201" ]; then
  echo "  Authentication policy created"
else
  echo "  Failed (HTTP ${HTTP_CODE})"
  echo "  ${RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "  ${RESPONSE_BODY}"
  exit 1
fi
echo ""

# --- Step 8: Create application client ---
echo "Step 8: Creating application client..."

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
echo "  Password policy:  min ${PASSWORD_MIN_LENGTH} / max ${PASSWORD_MAX_LENGTH} chars"
echo "    Require uppercase: ${PASSWORD_REQUIRE_UPPERCASE}, lowercase: ${PASSWORD_REQUIRE_LOWERCASE}"
echo "    Require number: ${PASSWORD_REQUIRE_NUMBER}, special char: ${PASSWORD_REQUIRE_SPECIAL_CHAR}"
echo "    History: ${PASSWORD_MAX_HISTORY}"
echo "  Account lock:     ${PASSWORD_MAX_ATTEMPTS} attempts / ${PASSWORD_LOCKOUT_DURATION_SECONDS}s lockout"
echo "  Session:          ${SESSION_TIMEOUT_SECONDS}s timeout, SameSite=Lax"
echo "  Token duration:   AT=${ACCESS_TOKEN_DURATION}s, IDT=${ID_TOKEN_DURATION}s, RT=${REFRESH_TOKEN_DURATION}s"
echo "  Registration:     ${REGISTRATION_REQUIRED_FIELDS} required"
echo "  Auth policy:      password AND email OTP (MFA)"
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
echo "  ${OUTPUT_DIR}/authentication-config-initial-registration.json"
echo "  ${OUTPUT_DIR}/authentication-config-email.json"
echo "  ${OUTPUT_DIR}/authentication-policy.json"
echo "  ${OUTPUT_DIR}/public-client.json"
echo ""
echo "Test Authorization Code Flow:"
echo "  1. Open browser:"
echo "     ${AUTHORIZATION_SERVER_URL}/${PUBLIC_TENANT_ID}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=${REDIRECT_URI}&scope=openid%20profile%20email&state=test-state"
echo ""
echo "  2. Enter email, receive OTP code (no-action mode: code logged to server), then enter password"
echo ""
echo "  3. Exchange code for token:"
echo "     curl -X POST ${AUTHORIZATION_SERVER_URL}/${PUBLIC_TENANT_ID}/v1/tokens \\"
echo "       -d \"grant_type=authorization_code\" \\"
echo "       -d \"code=YOUR_CODE\" \\"
echo "       -d \"redirect_uri=${REDIRECT_URI}\" \\"
echo "       -d \"client_id=${CLIENT_ID}\" \\"
echo "       -d \"client_secret=${CLIENT_SECRET}\""
