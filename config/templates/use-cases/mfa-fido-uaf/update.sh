#!/bin/bash
set -e

# MFA (Password + FIDO-UAF) - Use Case Update Script
# Updates public tenant, authorization server, client, and authentication settings.
#
# Usage:
#   ./update.sh
#   ORGANIZATION_NAME=my-org ./update.sh

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../../.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"

ORGANIZATION_NAME="${ORGANIZATION_NAME:-mfa-fido-uaf}"
OUTPUT_DIR="${PROJECT_ROOT}/config/generated/${ORGANIZATION_NAME}"

echo "=========================================="
echo "MFA (Password + FIDO-UAF) Use Case Update"
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
AUTH_CONFIG_ID=$(jq -r '.id' "${OUTPUT_DIR}/authentication-config-initial-registration.json")
FIDO_UAF_AUTH_CONFIG_ID=$(jq -r '.id' "${OUTPUT_DIR}/authentication-config-fido-uaf.json")
DEVICE_NOTIFICATION_CONFIG_ID=$(jq -r '.id' "${OUTPUT_DIR}/authentication-config-device-notification.json")
EMAIL_AUTH_CONFIG_ID=$(jq -r '.id' "${OUTPUT_DIR}/authentication-config-email.json")
AUTH_POLICY_ID=$(jq -r '.id' "${OUTPUT_DIR}/authentication-policy.json")

echo "Resource IDs:"
echo "   Organization ID:              ${ORG_ID}"
echo "   Organizer Tenant ID:          ${ORGANIZER_TENANT_ID}"
echo "   Public Tenant ID:             ${PUBLIC_TENANT_ID}"
echo "   Client ID:                    ${CLIENT_ID}"
echo "   Auth Config ID:               ${AUTH_CONFIG_ID}"
echo "   FIDO-UAF Config ID:           ${FIDO_UAF_AUTH_CONFIG_ID}"
echo "   Device Notification Config ID: ${DEVICE_NOTIFICATION_CONFIG_ID}"
echo "   Email Config ID:              ${EMAIL_AUTH_CONFIG_ID}"
echo "   Auth Policy ID:               ${AUTH_POLICY_ID}"
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

echo "Token obtained: ${ORG_ACCESS_TOKEN:0:20}..."
echo ""

ORG_BASE_URL="${AUTHORIZATION_SERVER_URL}/v1/management/organizations/${ORG_ID}/tenants"

# Helper: PUT or POST (create if not exists)
update_or_create_config() {
  local config_id="$1"
  local config_file="$2"
  local label="$3"

  local config_json
  config_json=$(cat "${config_file}")

  local response http_code
  response=$(curl -s -w "\n%{http_code}" -X PUT \
    "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authentication-configurations/${config_id}" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${config_json}")

  http_code=$(echo "${response}" | tail -n1)

  if [ "${http_code}" = "200" ] || [ "${http_code}" = "204" ]; then
    echo "  ${label} updated"
  elif [ "${http_code}" = "404" ]; then
    echo "  ${label} not found, creating..."
    response=$(curl -s -w "\n%{http_code}" -X POST \
      "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authentication-configurations" \
      -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
      -H "Content-Type: application/json" \
      -d "${config_json}")
    http_code=$(echo "${response}" | tail -n1)
    if [ "${http_code}" = "200" ] || [ "${http_code}" = "201" ]; then
      echo "  ${label} created"
    else
      echo "  Warning: ${label} creation failed (HTTP ${http_code})"
    fi
  else
    echo "  Warning: ${label} update failed (HTTP ${http_code})"
  fi
}

# Step 2: Update public tenant
echo "Step 2: Updating public tenant..."
TENANT_JSON=$(jq '.tenant' "${OUTPUT_DIR}/public-tenant.json")
TENANT_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT \
  "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "${TENANT_JSON}")
TENANT_HTTP=$(echo "${TENANT_RESPONSE}" | tail -n1)
[ "${TENANT_HTTP}" = "200" ] && echo "  Tenant updated" || echo "  Warning: Tenant update failed (HTTP ${TENANT_HTTP})"
echo ""

# Step 3: Update authorization server
echo "Step 3: Updating authorization server..."
AUTH_SERVER_JSON=$(jq '.authorization_server' "${OUTPUT_DIR}/public-tenant.json")
AUTH_SERVER_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT \
  "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authorization-server" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "${AUTH_SERVER_JSON}")
AUTH_SERVER_HTTP=$(echo "${AUTH_SERVER_RESPONSE}" | tail -n1)
[ "${AUTH_SERVER_HTTP}" = "200" ] && echo "  Authorization server updated" || echo "  Warning: Auth server update failed (HTTP ${AUTH_SERVER_HTTP})"
echo ""

# Step 4: Update application client
echo "Step 4: Updating application client..."
CLIENT_JSON=$(cat "${OUTPUT_DIR}/public-client.json")
CLIENT_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT \
  "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/clients/${CLIENT_ID}" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "${CLIENT_JSON}")
CLIENT_HTTP=$(echo "${CLIENT_RESPONSE}" | tail -n1)
[ "${CLIENT_HTTP}" = "200" ] && echo "  Client updated" || echo "  Warning: Client update failed (HTTP ${CLIENT_HTTP})"
echo ""

# Step 5: Update authentication configurations
echo "Step 5: Updating authentication configurations..."
update_or_create_config "${AUTH_CONFIG_ID}" "${OUTPUT_DIR}/authentication-config-initial-registration.json" "Initial Registration"
update_or_create_config "${FIDO_UAF_AUTH_CONFIG_ID}" "${OUTPUT_DIR}/authentication-config-fido-uaf.json" "FIDO-UAF"
update_or_create_config "${DEVICE_NOTIFICATION_CONFIG_ID}" "${OUTPUT_DIR}/authentication-config-device-notification.json" "Device Notification"
update_or_create_config "${EMAIL_AUTH_CONFIG_ID}" "${OUTPUT_DIR}/authentication-config-email.json" "Email"
echo ""

# Step 6: Update authentication policy
echo "Step 6: Updating authentication policy..."
AUTH_POLICY_JSON=$(cat "${OUTPUT_DIR}/authentication-policy.json")
AUTH_POLICY_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT \
  "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authentication-policies/${AUTH_POLICY_ID}" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "${AUTH_POLICY_JSON}")
AUTH_POLICY_HTTP=$(echo "${AUTH_POLICY_RESPONSE}" | tail -n1)
[ "${AUTH_POLICY_HTTP}" = "200" ] && echo "  Authentication policy updated" || echo "  Warning: Policy update failed (HTTP ${AUTH_POLICY_HTTP})"
echo ""

echo "=========================================="
echo "Update Complete!"
echo "=========================================="
echo ""
echo "Updated Resources:"
echo "   Public Tenant ID:  ${PUBLIC_TENANT_ID}"
echo "   Client ID:         ${CLIENT_ID}"
echo ""
