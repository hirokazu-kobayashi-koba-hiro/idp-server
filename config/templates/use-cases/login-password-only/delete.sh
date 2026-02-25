#!/bin/bash
set -e

# Login (Password Only) - Use Case Delete Script
# Deletes all resources created by setup.sh.
#
# Usage:
#   ./delete.sh
#   ORGANIZATION_NAME=my-org ./delete.sh

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../../.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"

ORGANIZATION_NAME="${ORGANIZATION_NAME:-login-password-only}"
OUTPUT_DIR="${PROJECT_ROOT}/config/generated/${ORGANIZATION_NAME}"

echo "=========================================="
echo "Login (Password Only) Use Case Delete"
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

# Read IDs from generated files
echo "Reading resource IDs from generated files..."
ORG_ID=$(jq -r '.organization.id' "${OUTPUT_DIR}/onboarding.json")
ORGANIZER_TENANT_ID=$(jq -r '.tenant.id' "${OUTPUT_DIR}/onboarding.json")
USER_ID=$(jq -r '.user.sub' "${OUTPUT_DIR}/onboarding.json")
ORG_CLIENT_ID=$(jq -r '.client.client_id' "${OUTPUT_DIR}/onboarding.json")

PUBLIC_TENANT_ID=$(jq -r '.tenant.id' "${OUTPUT_DIR}/public-tenant.json")
CLIENT_ID=$(jq -r '.client_id' "${OUTPUT_DIR}/public-client.json")

echo "Resource IDs loaded"
echo "   Organization ID:      ${ORG_ID}"
echo "   Organizer Tenant ID:  ${ORGANIZER_TENANT_ID}"
echo "   Public Tenant ID:     ${PUBLIC_TENANT_ID}"
echo "   User ID:              ${USER_ID}"
echo "   Org Client ID:        ${ORG_CLIENT_ID}"
echo "   App Client ID:        ${CLIENT_ID}"
echo ""

# Step 2: Delete application client
echo "Step 2: Deleting application client..."
CLIENT_DELETE_RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE \
  "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${PUBLIC_TENANT_ID}/clients/${CLIENT_ID}" \
  -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}")

CLIENT_DELETE_HTTP_CODE=$(echo "${CLIENT_DELETE_RESPONSE}" | tail -n1)

if [ "${CLIENT_DELETE_HTTP_CODE}" = "204" ]; then
  echo "Application client deleted successfully"
elif [ "${CLIENT_DELETE_HTTP_CODE}" = "404" ]; then
  echo "Warning: Application client not found (may already be deleted)"
else
  echo "Warning: Application client deletion failed (HTTP ${CLIENT_DELETE_HTTP_CODE})"
fi
echo ""

# Step 3: Delete public tenant
echo "Step 3: Deleting public tenant..."
PUBLIC_TENANT_DELETE_RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE \
  "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${PUBLIC_TENANT_ID}" \
  -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}")

PUBLIC_TENANT_DELETE_HTTP_CODE=$(echo "${PUBLIC_TENANT_DELETE_RESPONSE}" | tail -n1)

if [ "${PUBLIC_TENANT_DELETE_HTTP_CODE}" = "204" ]; then
  echo "Public tenant deleted successfully"
elif [ "${PUBLIC_TENANT_DELETE_HTTP_CODE}" = "404" ]; then
  echo "Warning: Public tenant not found (may already be deleted)"
else
  echo "Warning: Public tenant deletion failed (HTTP ${PUBLIC_TENANT_DELETE_HTTP_CODE})"
fi
echo ""

# Step 4: Delete organizer client
echo "Step 4: Deleting organizer client..."
ORG_CLIENT_DELETE_RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE \
  "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${ORGANIZER_TENANT_ID}/clients/${ORG_CLIENT_ID}" \
  -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}")

ORG_CLIENT_DELETE_HTTP_CODE=$(echo "${ORG_CLIENT_DELETE_RESPONSE}" | tail -n1)

if [ "${ORG_CLIENT_DELETE_HTTP_CODE}" = "204" ]; then
  echo "Organizer client deleted successfully"
elif [ "${ORG_CLIENT_DELETE_HTTP_CODE}" = "404" ]; then
  echo "Warning: Organizer client not found (may already be deleted)"
else
  echo "Warning: Organizer client deletion failed (HTTP ${ORG_CLIENT_DELETE_HTTP_CODE})"
fi
echo ""

# Step 5: Delete user
echo "Step 5: Deleting user..."
USER_DELETE_RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE \
  "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${ORGANIZER_TENANT_ID}/users/${USER_ID}" \
  -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}")

USER_DELETE_HTTP_CODE=$(echo "${USER_DELETE_RESPONSE}" | tail -n1)

if [ "${USER_DELETE_HTTP_CODE}" = "204" ]; then
  echo "User deleted successfully"
elif [ "${USER_DELETE_HTTP_CODE}" = "404" ]; then
  echo "Warning: User not found (may already be deleted)"
else
  echo "Warning: User deletion failed (HTTP ${USER_DELETE_HTTP_CODE})"
fi
echo ""

# Step 6: Delete organizer tenant
echo "Step 6: Deleting organizer tenant..."
ORGANIZER_TENANT_DELETE_RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE \
  "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${ORGANIZER_TENANT_ID}" \
  -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}")

ORGANIZER_TENANT_DELETE_HTTP_CODE=$(echo "${ORGANIZER_TENANT_DELETE_RESPONSE}" | tail -n1)

if [ "${ORGANIZER_TENANT_DELETE_HTTP_CODE}" = "204" ]; then
  echo "Organizer tenant deleted successfully"
elif [ "${ORGANIZER_TENANT_DELETE_HTTP_CODE}" = "404" ]; then
  echo "Warning: Organizer tenant not found (may already be deleted)"
else
  echo "Error: Organizer tenant deletion failed (HTTP ${ORGANIZER_TENANT_DELETE_HTTP_CODE})"
  echo "Warning: Cannot proceed to organization deletion"
  exit 1
fi
echo ""

# Step 7: Delete organization
echo "Step 7: Deleting organization..."
ORG_DELETE_RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE \
  "${AUTHORIZATION_SERVER_URL}/v1/management/orgs/${ORG_ID}" \
  -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}")

ORG_DELETE_HTTP_CODE=$(echo "${ORG_DELETE_RESPONSE}" | tail -n1)

if [ "${ORG_DELETE_HTTP_CODE}" = "204" ]; then
  echo "Organization deleted successfully"
elif [ "${ORG_DELETE_HTTP_CODE}" = "404" ]; then
  echo "Warning: Organization not found (may already be deleted)"
else
  echo "Error: Organization deletion failed (HTTP ${ORG_DELETE_HTTP_CODE})"
  exit 1
fi
echo ""

echo "=========================================="
echo "Delete Complete!"
echo "=========================================="
echo ""
echo "Deleted Resources:"
echo "   Organization ID:      ${ORG_ID}"
echo "   Organizer Tenant ID:  ${ORGANIZER_TENANT_ID}"
echo "   Public Tenant ID:     ${PUBLIC_TENANT_ID}"
echo "   User ID:              ${USER_ID}"
echo "   Org Client ID:        ${ORG_CLIENT_ID}"
echo "   App Client ID:        ${CLIENT_ID}"
echo ""
