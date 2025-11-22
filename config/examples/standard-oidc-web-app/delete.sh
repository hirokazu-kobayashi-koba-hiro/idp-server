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

echo "📖 Reading resource IDs from configuration files..."
ORG_ID=$(jq -r '.organization.id' "${ONBOARDING_REQUEST}")
ORGANIZER_TENANT_ID=$(jq -r '.tenant.id' "${ONBOARDING_REQUEST}")
USER_ID=$(jq -r '.user.sub' "${ONBOARDING_REQUEST}")
ADMIN_CLIENT_ID=$(jq -r '.client.client_id' "${ONBOARDING_REQUEST}")

PUBLIC_TENANT_FILE="${SCRIPT_DIR}/public-tenant.json"
if [ -f "${PUBLIC_TENANT_FILE}" ]; then
  PUBLIC_TENANT_ID=$(jq -r '.tenant.id' "${PUBLIC_TENANT_FILE}")
else
  PUBLIC_TENANT_ID=""
fi

PUBLIC_CLIENT_FILE="${SCRIPT_DIR}/public-client.json"
if [ -f "${PUBLIC_CLIENT_FILE}" ]; then
  PUBLIC_CLIENT_ID=$(jq -r '.client_id' "${PUBLIC_CLIENT_FILE}")
else
  PUBLIC_CLIENT_ID=""
fi

PUBLIC_CLIENT2_FILE="${SCRIPT_DIR}/public-client2.json"
if [ -f "${PUBLIC_CLIENT2_FILE}" ]; then
  PUBLIC_CLIENT2_ID=$(jq -r '.client_id' "${PUBLIC_CLIENT2_FILE}")
else
  PUBLIC_CLIENT2_ID=""
fi

echo "✅ Resource IDs loaded"
echo "   Organization ID:       ${ORG_ID}"
echo "   Organizer Tenant ID:   ${ORGANIZER_TENANT_ID}"
if [ -n "${PUBLIC_TENANT_ID}" ]; then
  echo "   Public Tenant ID:      ${PUBLIC_TENANT_ID}"
fi
echo "   User ID:               ${USER_ID}"
echo "   Admin Client ID:       ${ADMIN_CLIENT_ID}"
if [ -n "${PUBLIC_CLIENT_ID}" ]; then
  echo "   Public Client ID:      ${PUBLIC_CLIENT_ID}"
fi
if [ -n "${PUBLIC_CLIENT2_ID}" ]; then
  echo "   Public Client 2 ID:    ${PUBLIC_CLIENT2_ID}"
fi
echo ""

# Step 2: Delete admin client (in Organizer Tenant)
echo "🗑️  Step 2: Deleting admin client (in Organizer Tenant)..."
ADMIN_CLIENT_DELETE_RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE \
  "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${ORGANIZER_TENANT_ID}/clients/${ADMIN_CLIENT_ID}" \
  -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}")

ADMIN_CLIENT_DELETE_HTTP_CODE=$(echo "${ADMIN_CLIENT_DELETE_RESPONSE}" | tail -n1)
ADMIN_CLIENT_DELETE_BODY=$(echo "${ADMIN_CLIENT_DELETE_RESPONSE}" | sed '$d')

if [ "${ADMIN_CLIENT_DELETE_HTTP_CODE}" = "204" ]; then
  echo "✅ Admin client deleted successfully"
elif [ "${ADMIN_CLIENT_DELETE_HTTP_CODE}" = "404" ]; then
  echo "⚠️  Admin client not found (may already be deleted)"
else
  echo "⚠️  Admin client deletion failed (HTTP ${ADMIN_CLIENT_DELETE_HTTP_CODE})"
  if [ -n "${ADMIN_CLIENT_DELETE_BODY}" ]; then
    echo "Response: ${ADMIN_CLIENT_DELETE_BODY}" | jq '.' || echo "${ADMIN_CLIENT_DELETE_BODY}"
  fi
fi

echo ""

# Step 3: Delete public client (in Public Tenant)
if [ -n "${PUBLIC_CLIENT_ID}" ] && [ -n "${PUBLIC_TENANT_ID}" ]; then
  echo "🗑️  Step 3: Deleting public web app client (in Public Tenant)..."
  PUBLIC_CLIENT_DELETE_RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE \
    "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${PUBLIC_TENANT_ID}/clients/${PUBLIC_CLIENT_ID}" \
    -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}")

  PUBLIC_CLIENT_DELETE_HTTP_CODE=$(echo "${PUBLIC_CLIENT_DELETE_RESPONSE}" | tail -n1)
  PUBLIC_CLIENT_DELETE_BODY=$(echo "${PUBLIC_CLIENT_DELETE_RESPONSE}" | sed '$d')

  if [ "${PUBLIC_CLIENT_DELETE_HTTP_CODE}" = "204" ]; then
    echo "✅ Public client deleted successfully"
  elif [ "${PUBLIC_CLIENT_DELETE_HTTP_CODE}" = "404" ]; then
    echo "⚠️  Public client not found (may already be deleted)"
  else
    echo "⚠️  Public client deletion failed (HTTP ${PUBLIC_CLIENT_DELETE_HTTP_CODE})"
    if [ -n "${PUBLIC_CLIENT_DELETE_BODY}" ]; then
      echo "Response: ${PUBLIC_CLIENT_DELETE_BODY}" | jq '.' || echo "${PUBLIC_CLIENT_DELETE_BODY}"
    fi
  fi

  echo ""
else
  echo "⏭️  Step 3: Skipping public client deletion (not configured)"
  echo ""
fi

# Step 3-2: Delete public client 2 (in Public Tenant)
if [ -n "${PUBLIC_CLIENT2_ID}" ] && [ -n "${PUBLIC_TENANT_ID}" ]; then
  echo "🗑️  Step 3-2: Deleting public web app client 2 (in Public Tenant)..."
  PUBLIC_CLIENT2_DELETE_RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE \
    "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${PUBLIC_TENANT_ID}/clients/${PUBLIC_CLIENT2_ID}" \
    -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}")

  PUBLIC_CLIENT2_DELETE_HTTP_CODE=$(echo "${PUBLIC_CLIENT2_DELETE_RESPONSE}" | tail -n1)
  PUBLIC_CLIENT2_DELETE_BODY=$(echo "${PUBLIC_CLIENT2_DELETE_RESPONSE}" | sed '$d')

  if [ "${PUBLIC_CLIENT2_DELETE_HTTP_CODE}" = "204" ]; then
    echo "✅ Public client 2 deleted successfully"
  elif [ "${PUBLIC_CLIENT2_DELETE_HTTP_CODE}" = "404" ]; then
    echo "⚠️  Public client 2 not found (may already be deleted)"
  else
    echo "⚠️  Public client 2 deletion failed (HTTP ${PUBLIC_CLIENT2_DELETE_HTTP_CODE})"
    if [ -n "${PUBLIC_CLIENT2_DELETE_BODY}" ]; then
      echo "Response: ${PUBLIC_CLIENT2_DELETE_BODY}" | jq '.' || echo "${PUBLIC_CLIENT2_DELETE_BODY}"
    fi
  fi

  echo ""
else
  echo "⏭️  Step 3-2: Skipping public client 2 deletion (not configured)"
  echo ""
fi

# Step 4: Delete user (in Organizer Tenant)
echo "🗑️  Step 4: Deleting user (in Organizer Tenant)..."
USER_DELETE_RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE \
  "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${ORGANIZER_TENANT_ID}/users/${USER_ID}" \
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

# Step 5: Delete public tenant
if [ -n "${PUBLIC_TENANT_ID}" ]; then
  echo "🗑️  Step 5: Deleting public tenant..."
  PUBLIC_TENANT_DELETE_RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE \
    "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${PUBLIC_TENANT_ID}" \
    -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}")

  PUBLIC_TENANT_DELETE_HTTP_CODE=$(echo "${PUBLIC_TENANT_DELETE_RESPONSE}" | tail -n1)
  PUBLIC_TENANT_DELETE_BODY=$(echo "${PUBLIC_TENANT_DELETE_RESPONSE}" | sed '$d')

  if [ "${PUBLIC_TENANT_DELETE_HTTP_CODE}" = "204" ]; then
    echo "✅ Public tenant deleted successfully"
  elif [ "${PUBLIC_TENANT_DELETE_HTTP_CODE}" = "404" ]; then
    echo "⚠️  Public tenant not found (may already be deleted)"
  else
    echo "⚠️  Public tenant deletion failed (HTTP ${PUBLIC_TENANT_DELETE_HTTP_CODE})"
    if [ -n "${PUBLIC_TENANT_DELETE_BODY}" ]; then
      echo "Response: ${PUBLIC_TENANT_DELETE_BODY}" | jq '.' || echo "${PUBLIC_TENANT_DELETE_BODY}"
    fi
  fi

  echo ""
else
  echo "⏭️  Step 5: Skipping public tenant deletion (not configured)"
  echo ""
fi

# Step 6: Delete organizer tenant
echo "🗑️  Step 6: Deleting organizer tenant..."
ORGANIZER_TENANT_DELETE_RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE \
  "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${ORGANIZER_TENANT_ID}" \
  -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}")

ORGANIZER_TENANT_DELETE_HTTP_CODE=$(echo "${ORGANIZER_TENANT_DELETE_RESPONSE}" | tail -n1)
ORGANIZER_TENANT_DELETE_BODY=$(echo "${ORGANIZER_TENANT_DELETE_RESPONSE}" | sed '$d')

if [ "${ORGANIZER_TENANT_DELETE_HTTP_CODE}" = "204" ]; then
  echo "✅ Organizer tenant deleted successfully"
elif [ "${ORGANIZER_TENANT_DELETE_HTTP_CODE}" = "404" ]; then
  echo "⚠️  Organizer tenant not found (may already be deleted)"
else
  echo "❌ Organizer tenant deletion failed (HTTP ${ORGANIZER_TENANT_DELETE_HTTP_CODE})"
  if [ -n "${ORGANIZER_TENANT_DELETE_BODY}" ]; then
    echo "Response: ${ORGANIZER_TENANT_DELETE_BODY}" | jq '.' || echo "${ORGANIZER_TENANT_DELETE_BODY}"
  fi
  echo "⚠️  Cannot proceed to organization deletion"
  exit 1
fi

echo ""

# Step 7: Delete organization
echo "🗑️  Step 7: Deleting organization..."
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
echo "   Organization ID:       ${ORG_ID}"
echo "   Organizer Tenant ID:   ${ORGANIZER_TENANT_ID}"
if [ -n "${PUBLIC_TENANT_ID}" ]; then
  echo "   Public Tenant ID:      ${PUBLIC_TENANT_ID}"
fi
echo "   User ID:               ${USER_ID}"
echo "   Admin Client ID:       ${ADMIN_CLIENT_ID}"
if [ -n "${PUBLIC_CLIENT_ID}" ]; then
  echo "   Public Client ID:      ${PUBLIC_CLIENT_ID}"
fi
if [ -n "${PUBLIC_CLIENT2_ID}" ]; then
  echo "   Public Client 2 ID:    ${PUBLIC_CLIENT2_ID}"
fi
echo ""
