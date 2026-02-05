#!/bin/bash
set -e

# OIDCC Form Post Basic - Update Script
# This script updates tenant, authorization server, and clients

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"
ONBOARDING_REQUEST="${SCRIPT_DIR}/onboarding-request.json"

echo "=========================================="
echo "OIDCC Form Post Basic Update"
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

echo "Environment variables loaded"
echo "   Server: ${AUTHORIZATION_SERVER_URL}"
echo ""

# Read IDs from configuration files
TENANT_ID=$(jq -r '.tenant.id' "${ONBOARDING_REQUEST}")
CLIENT_BASIC_ID=$(jq -r '.client.client_id' "${ONBOARDING_REQUEST}")

CLIENT_POST_FILE="${SCRIPT_DIR}/client-post.json"
if [ -f "${CLIENT_POST_FILE}" ]; then
  CLIENT_POST_ID=$(jq -r '.client_id' "${CLIENT_POST_FILE}")
else
  CLIENT_POST_ID=""
fi

CLIENT_SECOND_FILE="${SCRIPT_DIR}/client-second.json"
if [ -f "${CLIENT_SECOND_FILE}" ]; then
  CLIENT_SECOND_ID=$(jq -r '.client_id' "${CLIENT_SECOND_FILE}")
else
  CLIENT_SECOND_ID=""
fi

echo "Resource IDs:"
echo "   Tenant ID:        ${TENANT_ID}"
echo "   Client Basic ID:  ${CLIENT_BASIC_ID}"
if [ -n "${CLIENT_POST_ID}" ]; then
  echo "   Client Post ID:   ${CLIENT_POST_ID}"
fi
if [ -n "${CLIENT_SECOND_ID}" ]; then
  echo "   Client Second ID: ${CLIENT_SECOND_ID}"
fi
echo ""

# Step 1: Get access token
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

# Step 2: Update tenant
echo "Step 2: Updating tenant..."
TENANT_JSON=$(jq '.tenant' "${ONBOARDING_REQUEST}")

TENANT_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT \
  "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${TENANT_ID}" \
  -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "${TENANT_JSON}")

TENANT_HTTP_CODE=$(echo "${TENANT_RESPONSE}" | tail -n1)
TENANT_RESPONSE_BODY=$(echo "${TENANT_RESPONSE}" | sed '$d')

if [ "${TENANT_HTTP_CODE}" = "200" ] || [ "${TENANT_HTTP_CODE}" = "204" ]; then
  echo "Tenant updated successfully"
else
  echo "Warning: Tenant update failed (HTTP ${TENANT_HTTP_CODE})"
  echo "Response: ${TENANT_RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "${TENANT_RESPONSE_BODY}"
fi
echo ""

# Step 3: Update authorization server
echo "Step 3: Updating authorization server..."
AUTH_SERVER_JSON=$(jq '.authorization_server' "${ONBOARDING_REQUEST}")

AUTH_SERVER_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT \
  "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${TENANT_ID}/authorization-server" \
  -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}" \
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

# Step 4: Update client (client_secret_basic)
echo "Step 4: Updating client (client_secret_basic)..."
CLIENT_BASIC_JSON=$(jq '.client' "${ONBOARDING_REQUEST}")

CLIENT_BASIC_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT \
  "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${TENANT_ID}/clients/${CLIENT_BASIC_ID}" \
  -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "${CLIENT_BASIC_JSON}")

CLIENT_BASIC_HTTP_CODE=$(echo "${CLIENT_BASIC_RESPONSE}" | tail -n1)
CLIENT_BASIC_RESPONSE_BODY=$(echo "${CLIENT_BASIC_RESPONSE}" | sed '$d')

if [ "${CLIENT_BASIC_HTTP_CODE}" = "200" ] || [ "${CLIENT_BASIC_HTTP_CODE}" = "204" ]; then
  echo "Client (basic) updated successfully"
else
  echo "Warning: Client (basic) update failed (HTTP ${CLIENT_BASIC_HTTP_CODE})"
  echo "Response: ${CLIENT_BASIC_RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "${CLIENT_BASIC_RESPONSE_BODY}"
fi
echo ""

# Step 5: Update client (client_secret_post)
if [ -n "${CLIENT_POST_ID}" ] && [ -f "${CLIENT_POST_FILE}" ]; then
  echo "Step 5: Updating client (client_secret_post)..."
  CLIENT_POST_JSON=$(cat "${CLIENT_POST_FILE}")

  CLIENT_POST_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT \
    "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${TENANT_ID}/clients/${CLIENT_POST_ID}" \
    -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${CLIENT_POST_JSON}")

  CLIENT_POST_HTTP_CODE=$(echo "${CLIENT_POST_RESPONSE}" | tail -n1)
  CLIENT_POST_RESPONSE_BODY=$(echo "${CLIENT_POST_RESPONSE}" | sed '$d')

  if [ "${CLIENT_POST_HTTP_CODE}" = "200" ] || [ "${CLIENT_POST_HTTP_CODE}" = "204" ]; then
    echo "Client (post) updated successfully"
  else
    echo "Warning: Client (post) update failed (HTTP ${CLIENT_POST_HTTP_CODE})"
    echo "Response: ${CLIENT_POST_RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "${CLIENT_POST_RESPONSE_BODY}"
  fi
  echo ""
else
  echo "Step 5: Skipping client (post) update (not configured)"
  echo ""
fi

# Step 6: Update client (second client)
if [ -n "${CLIENT_SECOND_ID}" ] && [ -f "${CLIENT_SECOND_FILE}" ]; then
  echo "Step 6: Updating client (second client)..."
  CLIENT_SECOND_JSON=$(cat "${CLIENT_SECOND_FILE}")

  CLIENT_SECOND_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT \
    "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${TENANT_ID}/clients/${CLIENT_SECOND_ID}" \
    -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${CLIENT_SECOND_JSON}")

  CLIENT_SECOND_HTTP_CODE=$(echo "${CLIENT_SECOND_RESPONSE}" | tail -n1)
  CLIENT_SECOND_RESPONSE_BODY=$(echo "${CLIENT_SECOND_RESPONSE}" | sed '$d')

  if [ "${CLIENT_SECOND_HTTP_CODE}" = "200" ] || [ "${CLIENT_SECOND_HTTP_CODE}" = "204" ]; then
    echo "Client (second) updated successfully"
  else
    echo "Warning: Client (second) update failed (HTTP ${CLIENT_SECOND_HTTP_CODE})"
    echo "Response: ${CLIENT_SECOND_RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "${CLIENT_SECOND_RESPONSE_BODY}"
  fi
  echo ""
else
  echo "Step 6: Skipping client (second) update (not configured)"
  echo ""
fi

echo "=========================================="
echo "Update Complete!"
echo "=========================================="
echo ""
echo "Updated Resources:"
echo "   Tenant ID:        ${TENANT_ID}"
echo "   Client Basic ID:  ${CLIENT_BASIC_ID}"
if [ -n "${CLIENT_POST_ID}" ]; then
  echo "   Client Post ID:   ${CLIENT_POST_ID}"
fi
if [ -n "${CLIENT_SECOND_ID}" ]; then
  echo "   Client Second ID: ${CLIENT_SECOND_ID}"
fi
echo ""
echo "Verify at:"
echo "   Discovery: ${AUTHORIZATION_SERVER_URL}/${TENANT_ID}/.well-known/openid-configuration"
echo "   JWKS:      ${AUTHORIZATION_SERVER_URL}/${TENANT_ID}/v1/jwks"
echo ""
