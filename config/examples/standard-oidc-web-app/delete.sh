#!/bin/bash
set -e

# Standard OIDC Web Application Delete Script
# This script deletes organization and all related resources

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"

echo "=========================================="
echo "🗑️  Standard OIDC Web App Delete"
echo "=========================================="

# Load .env file
if [ ! -f "${ENV_FILE}" ]; then
  echo "❌ Error: .env file not found at ${ENV_FILE}"
  exit 1
fi

echo "📖 Loading environment variables from .env..."
set -a
source "${ENV_FILE}"
set +a

echo "✅ Environment variables loaded"
echo "   Server: ${AUTHORIZATION_SERVER_URL}"
echo ""

# Step 1: Get access token
echo "🔐 Step 1: Getting system administrator access token..."
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

echo "✅ Access token obtained: ${SYSTEM_ACCESS_TOKEN:0:20}..."
echo ""

# Read IDs from onboarding-request.json
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ONBOARDING_REQUEST="${SCRIPT_DIR}/onboarding-request.json"

if [ ! -f "${ONBOARDING_REQUEST}" ]; then
  echo "❌ Error: onboarding-request.json not found at ${ONBOARDING_REQUEST}"
  exit 1
fi

echo "📖 Reading resource IDs from onboarding-request.json..."
ORG_ID=$(jq -r '.organization.id' "${ONBOARDING_REQUEST}")
TENANT_ID=$(jq -r '.tenant.id' "${ONBOARDING_REQUEST}")
USER_ID=$(jq -r '.user.sub' "${ONBOARDING_REQUEST}")
CLIENT_ID=$(jq -r '.client.client_id' "${ONBOARDING_REQUEST}")

echo "✅ Resource IDs loaded"
echo "   Organization ID: ${ORG_ID}"
echo "   Tenant ID:       ${TENANT_ID}"
echo "   User ID:         ${USER_ID}"
echo "   Client ID:       ${CLIENT_ID}"
echo ""

# Step 2: Delete client
echo "🗑️  Step 2: Deleting client..."
CLIENT_DELETE_RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE \
  "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${TENANT_ID}/clients/${CLIENT_ID}" \
  -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}")

CLIENT_DELETE_HTTP_CODE=$(echo "${CLIENT_DELETE_RESPONSE}" | tail -n1)
CLIENT_DELETE_BODY=$(echo "${CLIENT_DELETE_RESPONSE}" | sed '$d')

if [ "${CLIENT_DELETE_HTTP_CODE}" = "204" ]; then
  echo "✅ Client deleted successfully"
elif [ "${CLIENT_DELETE_HTTP_CODE}" = "404" ]; then
  echo "⚠️  Client not found (may already be deleted)"
else
  echo "⚠️  Client deletion failed (HTTP ${CLIENT_DELETE_HTTP_CODE})"
  if [ -n "${CLIENT_DELETE_BODY}" ]; then
    echo "Response: ${CLIENT_DELETE_BODY}" | jq '.' || echo "${CLIENT_DELETE_BODY}"
  fi
fi

echo ""

# Step 3: Delete user
echo "🗑️  Step 3: Deleting user..."
USER_DELETE_RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE \
  "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${TENANT_ID}/users/${USER_ID}" \
  -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}")

USER_DELETE_HTTP_CODE=$(echo "${USER_DELETE_RESPONSE}" | tail -n1)
USER_DELETE_BODY=$(echo "${USER_DELETE_RESPONSE}" | sed '$d')

if [ "${USER_DELETE_HTTP_CODE}" = "204" ]; then
  echo "✅ User deleted successfully"
elif [ "${USER_DELETE_HTTP_CODE}" = "404" ]; then
  echo "⚠️  User not found (may already be deleted)"
else
  echo "⚠️  User deletion failed (HTTP ${USER_DELETE_HTTP_CODE})"
  if [ -n "${USER_DELETE_BODY}" ]; then
    echo "Response: ${USER_DELETE_BODY}" | jq '.' || echo "${USER_DELETE_BODY}"
  fi
fi

echo ""

# Step 4: Delete tenant
echo "🗑️  Step 4: Deleting tenant..."
TENANT_DELETE_RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE \
  "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${TENANT_ID}" \
  -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}")

TENANT_DELETE_HTTP_CODE=$(echo "${TENANT_DELETE_RESPONSE}" | tail -n1)
TENANT_DELETE_BODY=$(echo "${TENANT_DELETE_RESPONSE}" | sed '$d')

if [ "${TENANT_DELETE_HTTP_CODE}" = "204" ]; then
  echo "✅ Tenant deleted successfully"
elif [ "${TENANT_DELETE_HTTP_CODE}" = "404" ]; then
  echo "⚠️  Tenant not found (may already be deleted)"
else
  echo "❌ Tenant deletion failed (HTTP ${TENANT_DELETE_HTTP_CODE})"
  if [ -n "${TENANT_DELETE_BODY}" ]; then
    echo "Response: ${TENANT_DELETE_BODY}" | jq '.' || echo "${TENANT_DELETE_BODY}"
  fi
  echo "⚠️  Cannot proceed to organization deletion"
  exit 1
fi

echo ""

# Step 5: Delete organization
echo "🗑️  Step 5: Deleting organization..."
ORG_DELETE_RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE \
  "${AUTHORIZATION_SERVER_URL}/v1/management/orgs/${ORG_ID}" \
  -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}")

ORG_DELETE_HTTP_CODE=$(echo "${ORG_DELETE_RESPONSE}" | tail -n1)
ORG_DELETE_BODY=$(echo "${ORG_DELETE_RESPONSE}" | sed '$d')

if [ "${ORG_DELETE_HTTP_CODE}" = "204" ]; then
  echo "✅ Organization deleted successfully"
elif [ "${ORG_DELETE_HTTP_CODE}" = "404" ]; then
  echo "⚠️  Organization not found (may already be deleted)"
else
  echo "❌ Organization deletion failed (HTTP ${ORG_DELETE_HTTP_CODE})"
  if [ -n "${ORG_DELETE_BODY}" ]; then
    echo "Response: ${ORG_DELETE_BODY}" | jq '.' || echo "${ORG_DELETE_BODY}"
  fi
  exit 1
fi

echo ""
echo "=========================================="
echo "✅ Delete Complete!"
echo "=========================================="
echo ""
echo "🆔 Deleted Resources:"
echo "   Organization ID: ${ORG_ID}"
echo "   Tenant ID:       ${TENANT_ID}"
echo "   User ID:         ${USER_ID}"
echo "   Client ID:       ${CLIENT_ID}"
echo ""
