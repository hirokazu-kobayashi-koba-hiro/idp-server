#!/bin/bash
set -e

# CIBA + External Password Auth - Cleanup Script

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../../.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"

echo "=========================================="
echo "CIBA + External Password Auth Cleanup"
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

ORGANIZATION_NAME="${ORGANIZATION_NAME:-ciba-ext-pw}"
CONFIG_DIR="${PROJECT_ROOT}/config/generated/${ORGANIZATION_NAME}"

if [ ! -d "${CONFIG_DIR}" ]; then
  echo "Config directory not found: ${CONFIG_DIR}"
  echo "Nothing to clean up."
  exit 0
fi

# Read IDs from generated config
ORGANIZATION_ID=$(jq -r '.organization.id' "${CONFIG_DIR}/onboarding.json" 2>/dev/null)

echo "Organization: ${ORGANIZATION_ID}"
echo ""

# --- Get system admin token ---
echo "Getting system administrator access token..."

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
  echo "Failed to get access token. Cleaning up local files only."
  rm -rf "${CONFIG_DIR}"
  echo "Removed: ${CONFIG_DIR}"
  exit 0
fi

# --- Delete organization ---
echo "Deleting organization ${ORGANIZATION_ID}..."

DELETE_RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE \
  "${AUTHORIZATION_SERVER_URL}/v1/management/organizations/${ORGANIZATION_ID}" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}")

HTTP_CODE=$(echo "${DELETE_RESPONSE}" | tail -n1)

if [ "${HTTP_CODE}" = "204" ] || [ "${HTTP_CODE}" = "200" ]; then
  echo "  Organization deleted"
else
  echo "  Delete returned HTTP ${HTTP_CODE} (may already be deleted)"
fi

# --- Clean up local files ---
echo ""
echo "Cleaning up generated files..."
rm -rf "${CONFIG_DIR}"
echo "  Removed: ${CONFIG_DIR}"

echo ""
echo "Cleanup complete."
