#!/bin/bash
set -e

# Financial Grade Delete Script
# This script deletes organization and all related resources

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"

echo "=========================================="
echo "🗑️  Financial Grade Delete"
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

# Read IDs from configuration files
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

FINANCIAL_TENANT_FILE="${SCRIPT_DIR}/financial-tenant.json"
if [ -f "${FINANCIAL_TENANT_FILE}" ]; then
  FINANCIAL_TENANT_ID=$(jq -r '.tenant.id' "${FINANCIAL_TENANT_FILE}")
else
  FINANCIAL_TENANT_ID=""
fi

FINANCIAL_CLIENT_FILE="${SCRIPT_DIR}/financial-client.json"
if [ -f "${FINANCIAL_CLIENT_FILE}" ]; then
  FINANCIAL_CLIENT_ID=$(jq -r '.client_id' "${FINANCIAL_CLIENT_FILE}")
else
  FINANCIAL_CLIENT_ID=""
fi

echo "✅ Resource IDs loaded"
echo "   Organization ID:        ${ORG_ID}"
echo "   Organizer Tenant ID:    ${ORGANIZER_TENANT_ID}"
if [ -n "${FINANCIAL_TENANT_ID}" ]; then
  echo "   Financial Tenant ID:    ${FINANCIAL_TENANT_ID}"
fi
echo "   User ID:                ${USER_ID}"
echo "   Admin Client ID:        ${ADMIN_CLIENT_ID}"
if [ -n "${FINANCIAL_CLIENT_ID}" ]; then
  echo "   Financial Client ID:    ${FINANCIAL_CLIENT_ID}"
fi
echo ""

# Step 2: Delete test user (in Financial Tenant) - Using System API
if [ -n "${FINANCIAL_TENANT_ID}" ]; then
  echo "🗑️  Step 2: Deleting test user (in Financial Tenant)..."

  FINANCIAL_USER_FILE="${SCRIPT_DIR}/financial-user.json"
  if [ -f "${FINANCIAL_USER_FILE}" ]; then
    FINANCIAL_USER_ID=$(jq -r '.sub' "${FINANCIAL_USER_FILE}")

    FINANCIAL_USER_DELETE_RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE \
      "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${FINANCIAL_TENANT_ID}/users/${FINANCIAL_USER_ID}" \
      -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}")

    FINANCIAL_USER_DELETE_HTTP_CODE=$(echo "${FINANCIAL_USER_DELETE_RESPONSE}" | tail -n1)
    FINANCIAL_USER_DELETE_BODY=$(echo "${FINANCIAL_USER_DELETE_RESPONSE}" | sed '$d')

    if [ "${FINANCIAL_USER_DELETE_HTTP_CODE}" = "200" ] || [ "${FINANCIAL_USER_DELETE_HTTP_CODE}" = "204" ]; then
      echo "✅ Test user deleted successfully"
    elif [ "${FINANCIAL_USER_DELETE_HTTP_CODE}" = "404" ]; then
      echo "⚠️  Test user not found (may already be deleted)"
    else
      echo "⚠️  Test user deletion failed (HTTP ${FINANCIAL_USER_DELETE_HTTP_CODE})"
      if [ -n "${FINANCIAL_USER_DELETE_BODY}" ]; then
        echo "Response: ${FINANCIAL_USER_DELETE_BODY}" | jq '.' || echo "${FINANCIAL_USER_DELETE_BODY}"
      fi
    fi
  else
    echo "⏭️  Financial user file not found, skipping"
  fi

  echo ""
else
  echo "⏭️  Step 2: Skipping test user deletion (Financial Tenant not configured)"
  echo ""
fi

# Step 3: Delete authentication policy (in Financial Tenant) - Using System API
if [ -n "${FINANCIAL_TENANT_ID}" ]; then
  echo "🗑️  Step 3: Deleting authentication policy (in Financial Tenant)..."

  AUTH_POLICY_FILE="${SCRIPT_DIR}/authentication-policy/oauth.json"
  if [ -f "${AUTH_POLICY_FILE}" ]; then
    AUTH_POLICY_ID=$(jq -r '.id' "${AUTH_POLICY_FILE}")

    AUTH_POLICY_DELETE_RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE \
      "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${FINANCIAL_TENANT_ID}/authentication-policies/${AUTH_POLICY_ID}" \
      -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}")

    AUTH_POLICY_DELETE_HTTP_CODE=$(echo "${AUTH_POLICY_DELETE_RESPONSE}" | tail -n1)
    AUTH_POLICY_DELETE_BODY=$(echo "${AUTH_POLICY_DELETE_RESPONSE}" | sed '$d')

    if [ "${AUTH_POLICY_DELETE_HTTP_CODE}" = "200" ] || [ "${AUTH_POLICY_DELETE_HTTP_CODE}" = "204" ]; then
      echo "✅ Authentication policy deleted successfully"
    elif [ "${AUTH_POLICY_DELETE_HTTP_CODE}" = "404" ]; then
      echo "⚠️  Authentication policy not found (may already be deleted)"
    else
      echo "⚠️  Authentication policy deletion failed (HTTP ${AUTH_POLICY_DELETE_HTTP_CODE})"
      if [ -n "${AUTH_POLICY_DELETE_BODY}" ]; then
        echo "Response: ${AUTH_POLICY_DELETE_BODY}" | jq '.' || echo "${AUTH_POLICY_DELETE_BODY}"
      fi
    fi
  else
    echo "⏭️  Authentication policy file not found, skipping"
  fi

  echo ""
else
  echo "⏭️  Step 3: Skipping authentication policy deletion (Financial Tenant not configured)"
  echo ""
fi

# Step 4: Delete financial clients (in Financial Tenant) - Using System API
if [ -n "${FINANCIAL_TENANT_ID}" ]; then
  echo "🗑️  Step 4: Deleting financial web app clients (in Financial Tenant)..."

  # Define client files to delete
  CLIENT_FILES=(
    "financial-client.json"
    "private-key-jwt-client.json"
    "private-key-jwt-client-2.json"
    "tls-client-auth-client.json"
    "tls-client-auth-client-2.json"
  )

  for CLIENT_FILE in "${CLIENT_FILES[@]}"; do
    CLIENT_FILE_PATH="${SCRIPT_DIR}/${CLIENT_FILE}"

    if [ ! -f "${CLIENT_FILE_PATH}" ]; then
      continue
    fi

    CLIENT_ID=$(jq -r '.client_id' "${CLIENT_FILE_PATH}")
    CLIENT_ALIAS=$(jq -r '.client_id_alias // .client_id' "${CLIENT_FILE_PATH}")

    echo "   🗑️  Deleting client: ${CLIENT_ALIAS}..."

    CLIENT_DELETE_RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE \
      "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${FINANCIAL_TENANT_ID}/clients/${CLIENT_ID}" \
      -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}")

    CLIENT_DELETE_HTTP_CODE=$(echo "${CLIENT_DELETE_RESPONSE}" | tail -n1)
    CLIENT_DELETE_BODY=$(echo "${CLIENT_DELETE_RESPONSE}" | sed '$d')

    if [ "${CLIENT_DELETE_HTTP_CODE}" = "200" ] || [ "${CLIENT_DELETE_HTTP_CODE}" = "204" ]; then
      echo "   ✅ Deleted: ${CLIENT_ID}"
    elif [ "${CLIENT_DELETE_HTTP_CODE}" = "404" ]; then
      echo "   ⚠️  Not found (may already be deleted)"
    else
      echo "   ⚠️  Failed (HTTP ${CLIENT_DELETE_HTTP_CODE})"
      if [ -n "${CLIENT_DELETE_BODY}" ]; then
        echo "   Response: ${CLIENT_DELETE_BODY}" | jq '.' 2>/dev/null || echo "   ${CLIENT_DELETE_BODY}"
      fi
    fi
  done

  echo ""
else
  echo "⏭️  Step 4: Skipping financial client deletion (not configured)"
  echo ""
fi

# Step 5: Delete financial tenant - Using System API
if [ -n "${FINANCIAL_TENANT_ID}" ]; then
  echo "🗑️  Step 5: Deleting financial tenant..."
  FINANCIAL_TENANT_DELETE_RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE \
    "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${FINANCIAL_TENANT_ID}" \
    -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}")

  FINANCIAL_TENANT_DELETE_HTTP_CODE=$(echo "${FINANCIAL_TENANT_DELETE_RESPONSE}" | tail -n1)
  FINANCIAL_TENANT_DELETE_BODY=$(echo "${FINANCIAL_TENANT_DELETE_RESPONSE}" | sed '$d')

  if [ "${FINANCIAL_TENANT_DELETE_HTTP_CODE}" = "200" ] || [ "${FINANCIAL_TENANT_DELETE_HTTP_CODE}" = "204" ]; then
    echo "✅ Financial tenant deleted successfully"
  elif [ "${FINANCIAL_TENANT_DELETE_HTTP_CODE}" = "404" ]; then
    echo "⚠️  Financial tenant not found (may already be deleted)"
  else
    echo "⚠️  Financial tenant deletion failed (HTTP ${FINANCIAL_TENANT_DELETE_HTTP_CODE})"
    if [ -n "${FINANCIAL_TENANT_DELETE_BODY}" ]; then
      echo "Response: ${FINANCIAL_TENANT_DELETE_BODY}" | jq '.' || echo "${FINANCIAL_TENANT_DELETE_BODY}"
    fi
  fi

  echo ""
else
  echo "⏭️  Step 5: Skipping financial tenant deletion (not configured)"
  echo ""
fi

# Step 6: Delete admin client (in Organizer Tenant)
echo "🗑️  Step 6: Deleting admin client (in Organizer Tenant)..."
ADMIN_CLIENT_DELETE_RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE \
  "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${ORGANIZER_TENANT_ID}/clients/${ADMIN_CLIENT_ID}" \
  -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}")

ADMIN_CLIENT_DELETE_HTTP_CODE=$(echo "${ADMIN_CLIENT_DELETE_RESPONSE}" | tail -n1)
ADMIN_CLIENT_DELETE_BODY=$(echo "${ADMIN_CLIENT_DELETE_RESPONSE}" | sed '$d')

if [ "${ADMIN_CLIENT_DELETE_HTTP_CODE}" = "200" ] || [ "${ADMIN_CLIENT_DELETE_HTTP_CODE}" = "204" ]; then
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

# Step 7: Delete user (in Organizer Tenant)
echo "🗑️  Step 7: Deleting user (in Organizer Tenant)..."
USER_DELETE_RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE \
  "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${ORGANIZER_TENANT_ID}/users/${USER_ID}" \
  -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}")

USER_DELETE_HTTP_CODE=$(echo "${USER_DELETE_RESPONSE}" | tail -n1)
USER_DELETE_BODY=$(echo "${USER_DELETE_RESPONSE}" | sed '$d')

if [ "${USER_DELETE_HTTP_CODE}" = "200" ] || [ "${USER_DELETE_HTTP_CODE}" = "204" ]; then
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

# Step 8: Delete organizer tenant
echo "🗑️  Step 8: Deleting organizer tenant..."
ORGANIZER_TENANT_DELETE_RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE \
  "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${ORGANIZER_TENANT_ID}" \
  -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}")

ORGANIZER_TENANT_DELETE_HTTP_CODE=$(echo "${ORGANIZER_TENANT_DELETE_RESPONSE}" | tail -n1)
ORGANIZER_TENANT_DELETE_BODY=$(echo "${ORGANIZER_TENANT_DELETE_RESPONSE}" | sed '$d')

if [ "${ORGANIZER_TENANT_DELETE_HTTP_CODE}" = "200" ] || [ "${ORGANIZER_TENANT_DELETE_HTTP_CODE}" = "204" ]; then
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

# Step 9: Delete organization
echo "🗑️  Step 9: Deleting organization..."
ORG_DELETE_RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE \
  "${AUTHORIZATION_SERVER_URL}/v1/management/orgs/${ORG_ID}" \
  -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}")

ORG_DELETE_HTTP_CODE=$(echo "${ORG_DELETE_RESPONSE}" | tail -n1)
ORG_DELETE_BODY=$(echo "${ORG_DELETE_RESPONSE}" | sed '$d')

if [ "${ORG_DELETE_HTTP_CODE}" = "200" ] || [ "${ORG_DELETE_HTTP_CODE}" = "204" ]; then
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
echo "   Organization ID:        ${ORG_ID}"
echo "   Organizer Tenant ID:    ${ORGANIZER_TENANT_ID}"
if [ -n "${FINANCIAL_TENANT_ID}" ]; then
  echo "   Financial Tenant ID:    ${FINANCIAL_TENANT_ID}"
fi
echo "   User ID:                ${USER_ID}"
echo "   Admin Client ID:        ${ADMIN_CLIENT_ID}"
if [ -n "${FINANCIAL_CLIENT_ID}" ]; then
  echo "   Financial Client ID:    ${FINANCIAL_CLIENT_ID}"
fi
echo ""
