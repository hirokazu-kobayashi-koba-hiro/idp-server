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
  ORGANIZER_TENANT_ID=$(echo "${RESPONSE_BODY}" | jq -r '.tenant.id')

  echo "✅ Onboarding completed - Organization, Organizer Tenant, Admin User, and Admin Client created"
  echo ""

  # Step 3: Create public tenant
  echo "🏢 Step 3: Creating public tenant..."

  PUBLIC_TENANT_FILE="${SCRIPT_DIR}/public-tenant.json"
  if [ ! -f "${PUBLIC_TENANT_FILE}" ]; then
    echo "⚠️  public-tenant.json not found at ${PUBLIC_TENANT_FILE}"
    echo "⚠️  Skipping public tenant and client creation..."
    echo ""
    PUBLIC_TENANT_ID=""
  else
    PUBLIC_TENANT_JSON=$(cat "${PUBLIC_TENANT_FILE}")

    PUBLIC_TENANT_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
      "${AUTHORIZATION_SERVER_URL}/v1/management/tenants" \
      -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}" \
      -H "Content-Type: application/json" \
      -d "${PUBLIC_TENANT_JSON}")

    PUBLIC_TENANT_HTTP_CODE=$(echo "${PUBLIC_TENANT_RESPONSE}" | tail -n1)
    PUBLIC_TENANT_RESPONSE_BODY=$(echo "${PUBLIC_TENANT_RESPONSE}" | sed '$d')

    if [ "${PUBLIC_TENANT_HTTP_CODE}" = "200" ] || [ "${PUBLIC_TENANT_HTTP_CODE}" = "201" ]; then
      PUBLIC_TENANT_ID=$(echo "${PUBLIC_TENANT_RESPONSE_BODY}" | jq -r '.result.id')
      echo "✅ Public tenant created: ${PUBLIC_TENANT_ID}"
    else
      echo "⚠️  Public tenant creation failed (HTTP ${PUBLIC_TENANT_HTTP_CODE})"
      echo "Response: ${PUBLIC_TENANT_RESPONSE_BODY}" | jq '.' || echo "${PUBLIC_TENANT_RESPONSE_BODY}"
      echo "⚠️  Skipping public client creation..."
      PUBLIC_TENANT_ID=""
    fi
    echo ""
  fi

  # Step 4: Create public web app client in public tenant
  if [ -n "${PUBLIC_TENANT_ID}" ]; then
    echo "🔧 Step 4: Creating public web application client in public tenant..."

    PUBLIC_CLIENT_FILE="${SCRIPT_DIR}/public-client.json"
    if [ ! -f "${PUBLIC_CLIENT_FILE}" ]; then
      echo "⚠️  public-client.json not found at ${PUBLIC_CLIENT_FILE}"
      echo "⚠️  Skipping public client creation..."
      echo ""
    else
      PUBLIC_CLIENT_JSON=$(cat "${PUBLIC_CLIENT_FILE}")

      PUBLIC_CLIENT_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
        "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${PUBLIC_TENANT_ID}/clients" \
        -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}" \
        -H "Content-Type: application/json" \
        -d "${PUBLIC_CLIENT_JSON}")

      PUBLIC_HTTP_CODE=$(echo "${PUBLIC_CLIENT_RESPONSE}" | tail -n1)
      PUBLIC_RESPONSE_BODY=$(echo "${PUBLIC_CLIENT_RESPONSE}" | sed '$d')

      if [ "${PUBLIC_HTTP_CODE}" = "200" ] || [ "${PUBLIC_HTTP_CODE}" = "201" ]; then
        PUBLIC_CLIENT_ID=$(echo "${PUBLIC_RESPONSE_BODY}" | jq -r '.result.client_id')
        echo "✅ Public web app client created: ${PUBLIC_CLIENT_ID}"
      else
        echo "⚠️  Public client creation failed (HTTP ${PUBLIC_HTTP_CODE})"
        echo "Response: ${PUBLIC_RESPONSE_BODY}" | jq '.' || echo "${PUBLIC_RESPONSE_BODY}"
      fi
      echo ""
    fi
  fi

  # Step 5: Create public web app client 2 in public tenant
  if [ -n "${PUBLIC_TENANT_ID}" ]; then
    echo "🔧 Step 5: Creating public web application client 2 in public tenant..."

    PUBLIC_CLIENT2_FILE="${SCRIPT_DIR}/public-client2.json"
    if [ ! -f "${PUBLIC_CLIENT2_FILE}" ]; then
      echo "⚠️  public-client2.json not found at ${PUBLIC_CLIENT2_FILE}"
      echo "⚠️  Skipping public client 2 creation..."
      echo ""
    else
      PUBLIC_CLIENT2_JSON=$(cat "${PUBLIC_CLIENT2_FILE}")

      PUBLIC_CLIENT2_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
        "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${PUBLIC_TENANT_ID}/clients" \
        -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}" \
        -H "Content-Type: application/json" \
        -d "${PUBLIC_CLIENT2_JSON}")

      PUBLIC2_HTTP_CODE=$(echo "${PUBLIC_CLIENT2_RESPONSE}" | tail -n1)
      PUBLIC2_RESPONSE_BODY=$(echo "${PUBLIC_CLIENT2_RESPONSE}" | sed '$d')

      if [ "${PUBLIC2_HTTP_CODE}" = "200" ] || [ "${PUBLIC2_HTTP_CODE}" = "201" ]; then
        PUBLIC_CLIENT2_ID=$(echo "${PUBLIC2_RESPONSE_BODY}" | jq -r '.result.client_id')
        echo "✅ Public web app client 2 created: ${PUBLIC_CLIENT2_ID}"
      else
        echo "⚠️  Public client 2 creation failed (HTTP ${PUBLIC2_HTTP_CODE})"
        echo "Response: ${PUBLIC2_RESPONSE_BODY}" | jq '.' || echo "${PUBLIC2_RESPONSE_BODY}"
      fi
      echo ""
    fi
  fi

  echo "=========================================="
  echo "✅ Setup Complete!"
  echo "=========================================="
  echo ""
  echo "🆔 Created Resources:"
  echo "   Organization ID:      ${ORG_ID}"
  echo "   Organizer Tenant ID:  ${ORGANIZER_TENANT_ID}"
  if [ -n "${PUBLIC_TENANT_ID}" ]; then
    echo "   Public Tenant ID:     ${PUBLIC_TENANT_ID}"
  fi
  echo ""
  echo "👤 Admin User:"
  echo "   Email:    admin@localhost.local"
  echo "   Password: LocalDevPassword123"
  echo ""
  echo "🔑 Admin Client (in Organizer Tenant):"
  echo "   Tenant ID:     ${ORGANIZER_TENANT_ID}"
  echo "   Client ID:     fcdfdf17-d633-448d-b2f0-af1c8ce3ff19"
  echo "   Client Secret: local-dev-admin-secret-32chars"
  echo "   Scopes:        openid profile email management"
  echo "   Grant Types:   authorization_code, refresh_token, password"
  echo ""
  if [ -n "${PUBLIC_TENANT_ID}" ]; then
    echo "🌐 Public Web App Client (in Public Tenant):"
    echo "   Tenant ID:     ${PUBLIC_TENANT_ID}"
    echo "   Client ID:     8a9f5e2c-1b3d-4c6a-9f8e-7d5c3a2b1e4f"
    echo "   Client Secret: local-dev-public-secret-32char"
    echo "   Scopes:        openid profile email"
    echo "   Grant Types:   authorization_code, refresh_token"
    echo ""
    echo "🌐 Public Web App Client 2 (in Public Tenant):"
    echo "   Tenant ID:     ${PUBLIC_TENANT_ID}"
    echo "   Client ID:     ef274ddf-08d4-4049-82b8-5cdadf0890b9"
    echo "   Client Secret: test-secret-2"
    echo "   Scopes:        openid profile email"
    echo "   Grant Types:   authorization_code, refresh_token"
    echo ""
    echo "🧪 Test Authorization Code Flow (Public Client):"
    echo "   1. Open browser:"
    echo "      open \"${AUTHORIZATION_SERVER_URL}/${PUBLIC_TENANT_ID}/v1/authorizations?response_type=code&client_id=8a9f5e2c-1b3d-4c6a-9f8e-7d5c3a2b1e4f&redirect_uri=http://localhost:3000/callback/&scope=openid%20profile%20email&state=test-state\""
    echo ""
    echo "   2. Login with:"
    echo "      Email: admin@localhost.local"
    echo "      Password: LocalDevPassword123"
    echo ""
    echo "   3. Get code from redirect URL and exchange:"
    echo "      curl -X POST ${AUTHORIZATION_SERVER_URL}/${PUBLIC_TENANT_ID}/v1/tokens \\"
    echo "        -H \"Content-Type: application/x-www-form-urlencoded\" \\"
    echo "        -d \"grant_type=authorization_code\" \\"
    echo "        -d \"code=YOUR_CODE_HERE\" \\"
    echo "        -d \"redirect_uri=http://localhost:3000/callback/\" \\"
    echo "        -d \"client_id=8a9f5e2c-1b3d-4c6a-9f8e-7d5c3a2b1e4f\" \\"
    echo "        -d \"client_secret=local-dev-public-secret-32char\""
    echo ""
  fi
else
  echo "❌ Onboarding failed (HTTP ${HTTP_CODE})"
  echo ""
  echo "📊 Error Response:"
  echo "${RESPONSE_BODY}" | jq '.' || echo "${RESPONSE_BODY}"
  echo ""
  exit 1
fi
