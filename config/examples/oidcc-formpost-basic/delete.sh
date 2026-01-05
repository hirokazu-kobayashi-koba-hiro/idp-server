#!/bin/bash
set -e

# OIDCC Form Post Basic Certification Test Delete Script
# This script deletes organization and all related resources

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"

echo "=========================================="
echo "OIDCC Form Post Basic Delete"
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

# Read IDs from configuration files
ONBOARDING_REQUEST="${SCRIPT_DIR}/onboarding-request.json"

if [ ! -f "${ONBOARDING_REQUEST}" ]; then
  echo "Error: onboarding-request.json not found at ${ONBOARDING_REQUEST}"
  exit 1
fi

echo "Reading resource IDs from configuration files..."
ORG_ID=$(jq -r '.organization.id' "${ONBOARDING_REQUEST}")
TENANT_ID=$(jq -r '.tenant.id' "${ONBOARDING_REQUEST}")
USER_ID=$(jq -r '.user.sub' "${ONBOARDING_REQUEST}")
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

echo "Resource IDs loaded"
echo "   Organization ID:  ${ORG_ID}"
echo "   Tenant ID:        ${TENANT_ID}"
echo "   User ID:          ${USER_ID}"
echo "   Client Basic ID:  ${CLIENT_BASIC_ID}"
if [ -n "${CLIENT_POST_ID}" ]; then
  echo "   Client Post ID:   ${CLIENT_POST_ID}"
fi
if [ -n "${CLIENT_SECOND_ID}" ]; then
  echo "   Client Second ID: ${CLIENT_SECOND_ID}"
fi
echo ""

# Step 2: Delete client (client_secret_basic)
echo "Step 2: Deleting client (client_secret_basic)..."
CLIENT_BASIC_DELETE_RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE \
  "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${TENANT_ID}/clients/${CLIENT_BASIC_ID}" \
  -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}")

CLIENT_BASIC_DELETE_HTTP_CODE=$(echo "${CLIENT_BASIC_DELETE_RESPONSE}" | tail -n1)
CLIENT_BASIC_DELETE_BODY=$(echo "${CLIENT_BASIC_DELETE_RESPONSE}" | sed '$d')

if [ "${CLIENT_BASIC_DELETE_HTTP_CODE}" = "204" ]; then
  echo "Client (basic) deleted successfully"
elif [ "${CLIENT_BASIC_DELETE_HTTP_CODE}" = "404" ]; then
  echo "Warning: Client (basic) not found (may already be deleted)"
else
  echo "Warning: Client (basic) deletion failed (HTTP ${CLIENT_BASIC_DELETE_HTTP_CODE})"
  if [ -n "${CLIENT_BASIC_DELETE_BODY}" ]; then
    echo "Response: ${CLIENT_BASIC_DELETE_BODY}" | jq '.' || echo "${CLIENT_BASIC_DELETE_BODY}"
  fi
fi

echo ""

# Step 3: Delete client (client_secret_post)
if [ -n "${CLIENT_POST_ID}" ]; then
  echo "Step 3: Deleting client (client_secret_post)..."
  CLIENT_POST_DELETE_RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE \
    "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${TENANT_ID}/clients/${CLIENT_POST_ID}" \
    -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}")

  CLIENT_POST_DELETE_HTTP_CODE=$(echo "${CLIENT_POST_DELETE_RESPONSE}" | tail -n1)
  CLIENT_POST_DELETE_BODY=$(echo "${CLIENT_POST_DELETE_RESPONSE}" | sed '$d')

  if [ "${CLIENT_POST_DELETE_HTTP_CODE}" = "204" ]; then
    echo "Client (post) deleted successfully"
  elif [ "${CLIENT_POST_DELETE_HTTP_CODE}" = "404" ]; then
    echo "Warning: Client (post) not found (may already be deleted)"
  else
    echo "Warning: Client (post) deletion failed (HTTP ${CLIENT_POST_DELETE_HTTP_CODE})"
    if [ -n "${CLIENT_POST_DELETE_BODY}" ]; then
      echo "Response: ${CLIENT_POST_DELETE_BODY}" | jq '.' || echo "${CLIENT_POST_DELETE_BODY}"
    fi
  fi

  echo ""
else
  echo "Step 3: Skipping client (post) deletion (not configured)"
  echo ""
fi

# Step 4: Delete client (second client)
if [ -n "${CLIENT_SECOND_ID}" ]; then
  echo "Step 4: Deleting client (second client)..."
  CLIENT_SECOND_DELETE_RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE \
    "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${TENANT_ID}/clients/${CLIENT_SECOND_ID}" \
    -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}")

  CLIENT_SECOND_DELETE_HTTP_CODE=$(echo "${CLIENT_SECOND_DELETE_RESPONSE}" | tail -n1)
  CLIENT_SECOND_DELETE_BODY=$(echo "${CLIENT_SECOND_DELETE_RESPONSE}" | sed '$d')

  if [ "${CLIENT_SECOND_DELETE_HTTP_CODE}" = "204" ]; then
    echo "Client (second) deleted successfully"
  elif [ "${CLIENT_SECOND_DELETE_HTTP_CODE}" = "404" ]; then
    echo "Warning: Client (second) not found (may already be deleted)"
  else
    echo "Warning: Client (second) deletion failed (HTTP ${CLIENT_SECOND_DELETE_HTTP_CODE})"
    if [ -n "${CLIENT_SECOND_DELETE_BODY}" ]; then
      echo "Response: ${CLIENT_SECOND_DELETE_BODY}" | jq '.' || echo "${CLIENT_SECOND_DELETE_BODY}"
    fi
  fi

  echo ""
else
  echo "Step 4: Skipping client (second) deletion (not configured)"
  echo ""
fi

# Step 5: Delete user
echo "Step 5: Deleting user..."
USER_DELETE_RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE \
  "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${TENANT_ID}/users/${USER_ID}" \
  -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}")

USER_DELETE_HTTP_CODE=$(echo "${USER_DELETE_RESPONSE}" | tail -n1)
USER_DELETE_BODY=$(echo "${USER_DELETE_RESPONSE}" | sed '$d')

if [ "${USER_DELETE_HTTP_CODE}" = "204" ]; then
  echo "User deleted successfully"
elif [ "${USER_DELETE_HTTP_CODE}" = "404" ]; then
  echo "Warning: User not found (may already be deleted)"
else
  echo "Warning: User deletion failed (HTTP ${USER_DELETE_HTTP_CODE})"
  if [ -n "${USER_DELETE_BODY}" ]; then
    echo "Response: ${USER_DELETE_BODY}" | jq '.' || echo "${USER_DELETE_BODY}"
  fi
fi

echo ""

# Step 6: Delete tenant
echo "Step 6: Deleting tenant..."
TENANT_DELETE_RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE \
  "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${TENANT_ID}" \
  -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}")

TENANT_DELETE_HTTP_CODE=$(echo "${TENANT_DELETE_RESPONSE}" | tail -n1)
TENANT_DELETE_BODY=$(echo "${TENANT_DELETE_RESPONSE}" | sed '$d')

if [ "${TENANT_DELETE_HTTP_CODE}" = "204" ]; then
  echo "Tenant deleted successfully"
elif [ "${TENANT_DELETE_HTTP_CODE}" = "404" ]; then
  echo "Warning: Tenant not found (may already be deleted)"
else
  echo "Error: Tenant deletion failed (HTTP ${TENANT_DELETE_HTTP_CODE})"
  if [ -n "${TENANT_DELETE_BODY}" ]; then
    echo "Response: ${TENANT_DELETE_BODY}" | jq '.' || echo "${TENANT_DELETE_BODY}"
  fi
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
ORG_DELETE_BODY=$(echo "${ORG_DELETE_RESPONSE}" | sed '$d')

if [ "${ORG_DELETE_HTTP_CODE}" = "204" ]; then
  echo "Organization deleted successfully"
elif [ "${ORG_DELETE_HTTP_CODE}" = "404" ]; then
  echo "Warning: Organization not found (may already be deleted)"
else
  echo "Error: Organization deletion failed (HTTP ${ORG_DELETE_HTTP_CODE})"
  if [ -n "${ORG_DELETE_BODY}" ]; then
    echo "Response: ${ORG_DELETE_BODY}" | jq '.' || echo "${ORG_DELETE_BODY}"
  fi
  exit 1
fi

echo ""
echo "=========================================="
echo "Delete Complete!"
echo "=========================================="
echo ""
echo "Deleted Resources:"
echo "   Organization ID:  ${ORG_ID}"
echo "   Tenant ID:        ${TENANT_ID}"
echo "   User ID:          ${USER_ID}"
echo "   Client Basic ID:  ${CLIENT_BASIC_ID}"
if [ -n "${CLIENT_POST_ID}" ]; then
  echo "   Client Post ID:   ${CLIENT_POST_ID}"
fi
if [ -n "${CLIENT_SECOND_ID}" ]; then
  echo "   Client Second ID: ${CLIENT_SECOND_ID}"
fi
echo ""
