#!/bin/bash
set -e

# Standard OIDC Web Application Setup Script
# This script automates the onboarding process using .env configuration

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"
ONBOARDING_REQUEST="${SCRIPT_DIR}/onboarding-request.json"

echo "=========================================="
echo "📋 Standard OIDC Web App Setup"
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

# Validate required variables
if [ -z "${AUTHORIZATION_SERVER_URL}" ]; then
  echo "❌ Error: AUTHORIZATION_SERVER_URL not set in .env"
  exit 1
fi

if [ -z "${ADMIN_TENANT_ID}" ]; then
  echo "❌ Error: ADMIN_TENANT_ID not set in .env"
  exit 1
fi

if [ -z "${ADMIN_USER_EMAIL}" ]; then
  echo "❌ Error: ADMIN_USER_EMAIL not set in .env"
  exit 1
fi

echo "✅ Environment variables loaded"
echo "   Server: ${AUTHORIZATION_SERVER_URL}"
echo "   Tenant: ${ADMIN_TENANT_ID}"
echo "   Admin:  ${ADMIN_USER_EMAIL}"
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

# Step 2: Execute onboarding API
echo "🚀 Step 2: Executing onboarding API..."
ONBOARDING_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  "${AUTHORIZATION_SERVER_URL}/v1/management/onboarding" \
  -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d @"${ONBOARDING_REQUEST}")

HTTP_CODE=$(echo "${ONBOARDING_RESPONSE}" | tail -n1)
RESPONSE_BODY=$(echo "${ONBOARDING_RESPONSE}" | sed '$d')

if [ "${HTTP_CODE}" = "200" ]; then
  echo "✅ Onboarding successful!"
  echo ""
  echo "📊 Response:"
  echo "${RESPONSE_BODY}" | jq '.'
  echo ""

  # Extract IDs from response
  ORG_ID=$(echo "${RESPONSE_BODY}" | jq -r '.organization.id')
  TENANT_ID=$(echo "${RESPONSE_BODY}" | jq -r '.tenant.id')

  echo "=========================================="
  echo "✅ Setup Complete!"
  echo "=========================================="
  echo ""
  echo "🆔 Created Resources:"
  echo "   Organization ID: ${ORG_ID}"
  echo "   Tenant ID:       ${TENANT_ID}"
  echo "   Admin Email:     admin@localhost.local"
  echo "   Admin Password:  LocalDevPassword123"
  echo "   Client ID:       fcdfdf17-d633-448d-b2f0-af1c8ce3ff19"
  echo "   Client Secret:   local-dev-secret-32-chars-long"
  echo ""
  echo "🧪 Test Authorization Code Flow:"
  echo "   1. Open browser:"
  echo "      open \"${AUTHORIZATION_SERVER_URL}/${TENANT_ID}/v1/authorizations?response_type=code&client_id=fcdfdf17-d633-448d-b2f0-af1c8ce3ff19&redirect_uri=http://localhost:3000/callback&scope=openid%20profile%20email&state=test-state\""
  echo ""
  echo "   2. Login with:"
  echo "      Email: admin@localhost.local"
  echo "      Password: LocalDevPassword123"
  echo ""
  echo "   3. Get code from redirect URL and exchange:"
  echo "      curl -X POST ${AUTHORIZATION_SERVER_URL}/${TENANT_ID}/v1/tokens \\"
  echo "        -H \"Content-Type: application/x-www-form-urlencoded\" \\"
  echo "        -d \"grant_type=authorization_code\" \\"
  echo "        -d \"code=YOUR_CODE_HERE\" \\"
  echo "        -d \"redirect_uri=http://localhost:3000/callback\" \\"
  echo "        -d \"client_id=fcdfdf17-d633-448d-b2f0-af1c8ce3ff19\" \\"
  echo "        -d \"client_secret=local-dev-secret-32-chars-long\""
  echo ""
else
  echo "❌ Onboarding failed (HTTP ${HTTP_CODE})"
  echo ""
  echo "📊 Error Response:"
  echo "${RESPONSE_BODY}" | jq '.' || echo "${RESPONSE_BODY}"
  echo ""
  exit 1
fi
