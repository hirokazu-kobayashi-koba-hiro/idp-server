#!/bin/bash
set -e

# MFA (Password + FIDO-UAF Device Authentication) - Use Case Setup Script
#
# Prerequisites:
#   1. idp-server is running
#   2. System administrator tenant exists (initial setup completed)
#   3. .env file with admin credentials
#   4. FIDO-UAF mock-server running (port 4000)
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
echo "MFA (Password + FIDO-UAF) Use Case Setup"
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
ORGANIZATION_NAME="${ORGANIZATION_NAME:-mfa-fido-uaf}"
COOKIE_NAME="${COOKIE_NAME:-SESSION}"
NEW_ADMIN_EMAIL="${NEW_ADMIN_EMAIL:-admin@example.com}"
NEW_ADMIN_PASSWORD="${NEW_ADMIN_PASSWORD:-ChangeMe123}"
TOKEN_SIGNING_KEY_ID="${TOKEN_SIGNING_KEY_ID:-signing_key_1}"
ID_TOKEN_SIGNING_KEY_ID="${ID_TOKEN_SIGNING_KEY_ID:-signing_key_1}"

# FIDO-UAF external service URL (default: mock-server on host.docker.internal:4005)
FIDO_UAF_SERVICE_URL="${FIDO_UAF_SERVICE_URL:-http://host.docker.internal:4005}"

# Email external service URLs (default: mock-server on host.docker.internal:4005)
EMAIL_SERVICE_CHALLENGE_URL="${EMAIL_SERVICE_CHALLENGE_URL:-http://host.docker.internal:4005/email-authentication-challenge}"
EMAIL_SERVICE_VERIFY_URL="${EMAIL_SERVICE_VERIFY_URL:-http://host.docker.internal:4005/email-authentication}"

# FCM credential (default: empty JSON for local dev)
FCM_CREDENTIAL="${FCM_CREDENTIAL:-{}}"

# Customizable policy values
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

OUTPUT_DIR="${PROJECT_ROOT}/config/generated/${ORGANIZATION_NAME}"
mkdir -p "${OUTPUT_DIR}"
echo "  Output directory: ${OUTPUT_DIR}"
echo ""

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

ONBOARDING_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  "${AUTHORIZATION_SERVER_URL}/v1/management/onboarding?dry_run=${DRY_RUN}" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d @"${OUTPUT_DIR}/onboarding.json")

HTTP_CODE=$(echo "${ONBOARDING_RESPONSE}" | tail -n1)
RESPONSE_BODY=$(echo "${ONBOARDING_RESPONSE}" | sed '$d')

if [ "${HTTP_CODE}" = "201" ]; then
  echo "  Onboarding successful"
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
  exit 1
fi

echo "  Token obtained: ${ORG_ACCESS_TOKEN:0:20}..."
echo ""

ORG_BASE_URL="${AUTHORIZATION_SERVER_URL}/v1/management/organizations/${ORGANIZATION_ID}/tenants"

# --- Step 4: Create public tenant ---
echo "Step 4: Creating public tenant..."

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

TENANT_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  "${ORG_BASE_URL}" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d @"${OUTPUT_DIR}/public-tenant.json")

HTTP_CODE=$(echo "${TENANT_RESPONSE}" | tail -n1)
if [ "${HTTP_CODE}" = "200" ] || [ "${HTTP_CODE}" = "201" ]; then
  echo "  Public tenant created: ${PUBLIC_TENANT_ID}"
else
  echo "  Failed (HTTP ${HTTP_CODE})"
  exit 1
fi
echo ""

# --- Step 5: Create authentication configuration (initial-registration) ---
echo "Step 5: Creating authentication configuration (initial-registration)..."

AUTH_CONFIG_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
REQUIRED_FIELDS_JSON=$(echo "${REGISTRATION_REQUIRED_FIELDS}" | jq -R 'split(",")')

AUTH_CONFIG_JSON=$(jq \
  --arg id "${AUTH_CONFIG_ID}" \
  --argjson required "${REQUIRED_FIELDS_JSON}" \
  '. + {id: $id} | .interactions["initial-registration"].request.schema.required = $required' \
  "${SCRIPT_DIR}/authentication-config-initial-registration.json")

echo "${AUTH_CONFIG_JSON}" | jq '.' > "${OUTPUT_DIR}/authentication-config-initial-registration.json"

AUTH_CONFIG_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authentication-configurations" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d @"${OUTPUT_DIR}/authentication-config-initial-registration.json")

HTTP_CODE=$(echo "${AUTH_CONFIG_RESPONSE}" | tail -n1)
if [ "${HTTP_CODE}" = "200" ] || [ "${HTTP_CODE}" = "201" ]; then
  echo "  Initial registration config created"
else
  echo "  Failed (HTTP ${HTTP_CODE})"
  exit 1
fi
echo ""

# --- Step 6: Create authentication configuration (fido-uaf) ---
echo "Step 6: Creating authentication configuration (fido-uaf)..."

FIDO_UAF_AUTH_CONFIG_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')

FIDO_UAF_AUTH_CONFIG_JSON=$(substitute_template "${SCRIPT_DIR}/authentication-config-fido-uaf.json" \
  "FIDO_UAF_SERVICE_URL" "${FIDO_UAF_SERVICE_URL}")
FIDO_UAF_AUTH_CONFIG_JSON=$(echo "${FIDO_UAF_AUTH_CONFIG_JSON}" | jq --arg id "${FIDO_UAF_AUTH_CONFIG_ID}" '. + {id: $id}')

echo "${FIDO_UAF_AUTH_CONFIG_JSON}" | jq '.' > "${OUTPUT_DIR}/authentication-config-fido-uaf.json"

FIDO_UAF_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authentication-configurations" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d @"${OUTPUT_DIR}/authentication-config-fido-uaf.json")

HTTP_CODE=$(echo "${FIDO_UAF_RESPONSE}" | tail -n1)
if [ "${HTTP_CODE}" = "200" ] || [ "${HTTP_CODE}" = "201" ]; then
  echo "  FIDO-UAF config created"
else
  echo "  Failed (HTTP ${HTTP_CODE})"
  exit 1
fi
echo ""

# --- Step 7: Create authentication configuration (device-notification) ---
echo "Step 7: Creating authentication configuration (device-notification)..."

DEVICE_NOTIFICATION_CONFIG_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')

DEVICE_NOTIFICATION_JSON=$(substitute_template "${SCRIPT_DIR}/authentication-config-device-notification.json" \
  "BASE_URL" "${AUTHORIZATION_SERVER_URL}" \
  "PUBLIC_TENANT_ID" "${PUBLIC_TENANT_ID}" \
  "FCM_CREDENTIAL" "${FCM_CREDENTIAL}")
DEVICE_NOTIFICATION_JSON=$(echo "${DEVICE_NOTIFICATION_JSON}" | jq --arg id "${DEVICE_NOTIFICATION_CONFIG_ID}" '. + {id: $id}')

echo "${DEVICE_NOTIFICATION_JSON}" | jq '.' > "${OUTPUT_DIR}/authentication-config-device-notification.json"

DEVICE_NOTIFICATION_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authentication-configurations" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d @"${OUTPUT_DIR}/authentication-config-device-notification.json")

HTTP_CODE=$(echo "${DEVICE_NOTIFICATION_RESPONSE}" | tail -n1)
if [ "${HTTP_CODE}" = "200" ] || [ "${HTTP_CODE}" = "201" ]; then
  echo "  Device notification config created"
else
  echo "  Failed (HTTP ${HTTP_CODE})"
  exit 1
fi
echo ""

# --- Step 8: Create authentication configuration (email) ---
echo "Step 8: Creating authentication configuration (email)..."

EMAIL_AUTH_CONFIG_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')

EMAIL_AUTH_CONFIG_JSON=$(substitute_template "${SCRIPT_DIR}/authentication-config-email.json" \
  "EMAIL_SERVICE_CHALLENGE_URL" "${EMAIL_SERVICE_CHALLENGE_URL}" \
  "EMAIL_SERVICE_VERIFY_URL" "${EMAIL_SERVICE_VERIFY_URL}")
EMAIL_AUTH_CONFIG_JSON=$(echo "${EMAIL_AUTH_CONFIG_JSON}" | jq --arg id "${EMAIL_AUTH_CONFIG_ID}" '. + {id: $id}')

echo "${EMAIL_AUTH_CONFIG_JSON}" | jq '.' > "${OUTPUT_DIR}/authentication-config-email.json"

EMAIL_AUTH_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authentication-configurations" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d @"${OUTPUT_DIR}/authentication-config-email.json")

HTTP_CODE=$(echo "${EMAIL_AUTH_RESPONSE}" | tail -n1)
if [ "${HTTP_CODE}" = "200" ] || [ "${HTTP_CODE}" = "201" ]; then
  echo "  Email config created"
else
  echo "  Failed (HTTP ${HTTP_CODE})"
  exit 1
fi
echo ""

# --- Step 9: Create authentication policy ---
echo "Step 9: Creating authentication policy (FIDO-UAF + password fallback)..."

AUTH_POLICY_ID="${AUTH_POLICY_ID:-$(uuidgen | tr '[:upper:]' '[:lower:]')}"
jq --arg id "${AUTH_POLICY_ID}" '. + {id: $id}' "${SCRIPT_DIR}/authentication-policy.json" > "${OUTPUT_DIR}/authentication-policy.json"

POLICY_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authentication-policies" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d @"${OUTPUT_DIR}/authentication-policy.json")

HTTP_CODE=$(echo "${POLICY_RESPONSE}" | tail -n1)
if [ "${HTTP_CODE}" = "200" ] || [ "${HTTP_CODE}" = "201" ]; then
  echo "  Authentication policy created"
else
  echo "  Failed (HTTP ${HTTP_CODE})"
  exit 1
fi
echo ""

# --- Step 10: Create application client ---
echo "Step 10: Creating application client..."

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

CLIENT_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/clients" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d @"${OUTPUT_DIR}/public-client.json")

HTTP_CODE=$(echo "${CLIENT_RESPONSE}" | tail -n1)
if [ "${HTTP_CODE}" = "200" ] || [ "${HTTP_CODE}" = "201" ]; then
  echo "  Client created: ${CLIENT_ID}"
else
  echo "  Failed (HTTP ${HTTP_CODE})"
  exit 1
fi
echo ""

# --- Summary ---
echo "=========================================="
echo "Setup Complete!"
echo "=========================================="
echo ""
echo "Settings applied:"
echo "  Auth policy:      FIDO-UAF device auth + password fallback"
echo "  FIDO-UAF server:  ${FIDO_UAF_SERVICE_URL}"
echo "  Device secret:    HS256 (device_secret_jwt authentication)"
echo "  Token duration:   AT=${ACCESS_TOKEN_DURATION}s, IDT=${ID_TOKEN_DURATION}s, RT=${REFRESH_TOKEN_DURATION}s"
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
echo "Application Client (Public Tenant):"
echo "  Tenant ID:     ${PUBLIC_TENANT_ID}"
echo "  Client ID:     ${CLIENT_ID}"
echo "  Client Secret: ${CLIENT_SECRET}"
echo "  Redirect URI:  ${REDIRECT_URI}"
echo ""
echo "Test Authorization Code Flow with login_hint:"
echo "  ${AUTHORIZATION_SERVER_URL}/${PUBLIC_TENANT_ID}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=${REDIRECT_URI}&scope=openid%20profile%20email&state=test&login_hint=sub:{user-id}"
echo ""
