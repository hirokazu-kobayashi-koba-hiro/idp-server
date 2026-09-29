#!/bin/bash
set -e

# Registers (or updates) the client that runs the authorization flow in cross-site view mode.
#
# sample-web signs in with two clients side by side:
#   - public-client.json             : same-site view (auth.local.test), cookie binding
#   - public-client-cross-site.json  : cross-site view (auth.idp.local), auth_proof + /complete
#
# The tenant routes the second one to auth.idp.local through the "cross-site" view variant in
# public-tenant.json, so run ./update.sh (or ./setup.sh) first to apply that.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"
PUBLIC_TENANT_FILE="${SCRIPT_DIR}/public-tenant.json"
CLIENT_FILE="${SCRIPT_DIR}/public-client-cross-site.json"

if [ ! -f "${ENV_FILE}" ]; then
  echo "❌ Error: .env file not found at ${ENV_FILE}"
  exit 1
fi

set -a
source "${ENV_FILE}"
set +a

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
  echo "❌ Error: Failed to get access token"
  echo "Response: ${TOKEN_RESPONSE}"
  exit 1
fi

PUBLIC_TENANT_ID=$(jq -r '.tenant.id' "${PUBLIC_TENANT_FILE}")
CLIENT_ID=$(jq -r '.client_id' "${CLIENT_FILE}")
CLIENTS_URL="${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${PUBLIC_TENANT_ID}/clients"

EXISTS_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
  "${CLIENTS_URL}/${CLIENT_ID}" \
  -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}")

if [ "${EXISTS_CODE}" = "200" ]; then
  METHOD=PUT
  URL="${CLIENTS_URL}/${CLIENT_ID}"
else
  METHOD=POST
  URL="${CLIENTS_URL}"
fi

RESPONSE=$(curl -s -w "\n%{http_code}" -X "${METHOD}" "${URL}" \
  -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d @"${CLIENT_FILE}")
HTTP_CODE=$(echo "${RESPONSE}" | tail -n1)
BODY=$(echo "${RESPONSE}" | sed '$d')

if [ "${HTTP_CODE}" = "200" ] || [ "${HTTP_CODE}" = "201" ]; then
  echo "✅ Cross-site client ${METHOD}: ${CLIENT_ID}"
else
  echo "❌ Cross-site client ${METHOD} failed (HTTP ${HTTP_CODE})"
  echo "${BODY}" | jq '.' 2>/dev/null || echo "${BODY}"
  exit 1
fi
