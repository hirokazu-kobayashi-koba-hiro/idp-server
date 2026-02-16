#!/bin/bash
set -e

# Financial Grade Setup Script
# This script automates the FAPI-compliant financial institution setup

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"
ONBOARDING_REQUEST="${SCRIPT_DIR}/onboarding-request.json"

echo "=========================================="
echo "🏦 Financial Grade FAPI Setup"
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
echo "🚀 Step 2: Executing Financial Grade onboarding..."
ONBOARDING_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  "${AUTHORIZATION_SERVER_URL}/v1/management/onboarding" \
  -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d @"${ONBOARDING_REQUEST}")

HTTP_CODE=$(echo "${ONBOARDING_RESPONSE}" | tail -n1)
RESPONSE_BODY=$(echo "${ONBOARDING_RESPONSE}" | sed '$d')

if [ "${HTTP_CODE}" = "201" ]; then
  echo "✅ Onboarding successful!"
  echo ""
  echo "📊 Response:"
  echo "${RESPONSE_BODY}" | jq '.'
  echo ""

  # Extract IDs from response
  ORG_ID=$(echo "${RESPONSE_BODY}" | jq -r '.organization.id')
  ORGANIZER_TENANT_ID=$(echo "${RESPONSE_BODY}" | jq -r '.tenant.id')
  ADMIN_USER_ID=$(echo "${RESPONSE_BODY}" | jq -r '.user.sub')
  ADMIN_CLIENT_ID=$(echo "${RESPONSE_BODY}" | jq -r '.client.client_id')

  echo "✅ Onboarding completed - Organization, Organizer Tenant, Admin User, and Admin Client created"
  echo ""

  # Step 3: Get organization admin access token
  echo "🔐 Step 3: Getting organization administrator access token..."
  ORG_ADMIN_EMAIL=$(echo "${RESPONSE_BODY}" | jq -r '.user.email')
  ORG_ADMIN_PASSWORD="FapiCibaTestSecure123!"
  ADMIN_CLIENT_SECRET=$(echo "${RESPONSE_BODY}" | jq -r '.client.client_secret')

  ORG_TOKEN_RESPONSE=$(curl -s -X POST \
    "${AUTHORIZATION_SERVER_URL}/${ORGANIZER_TENANT_ID}/v1/tokens" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    --data-urlencode "grant_type=password" \
    --data-urlencode "username=${ORG_ADMIN_EMAIL}" \
    --data-urlencode "password=${ORG_ADMIN_PASSWORD}" \
    --data-urlencode "client_id=${ADMIN_CLIENT_ID}" \
    --data-urlencode "client_secret=${ADMIN_CLIENT_SECRET}" \
    --data-urlencode "scope=account management")

  ORG_ACCESS_TOKEN=$(echo "${ORG_TOKEN_RESPONSE}" | jq -r '.access_token')

  if [ -z "${ORG_ACCESS_TOKEN}" ] || [ "${ORG_ACCESS_TOKEN}" = "null" ]; then
    echo "❌ Error: Failed to get organization admin access token"
    echo "Response: ${ORG_TOKEN_RESPONSE}"
    exit 1
  fi

  echo "✅ Organization admin access token obtained: ${ORG_ACCESS_TOKEN:0:20}..."
  echo ""

  # Step 4: Create financial business tenant
  echo "🏢 Step 4: Creating financial grade business tenant..."

  FINANCIAL_TENANT_FILE="${SCRIPT_DIR}/financial-tenant.json"
  if [ ! -f "${FINANCIAL_TENANT_FILE}" ]; then
    echo "⚠️  financial-tenant.json not found at ${FINANCIAL_TENANT_FILE}"
    echo "⚠️  Skipping tenant and client creation..."
    echo ""
    FINANCIAL_TENANT_ID=""
  else
    FINANCIAL_TENANT_JSON=$(cat "${FINANCIAL_TENANT_FILE}")

    TENANT_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
      "${AUTHORIZATION_SERVER_URL}/v1/management/organizations/${ORG_ID}/tenants" \
      -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
      -H "Content-Type: application/json" \
      -d "${FINANCIAL_TENANT_JSON}")

    TENANT_HTTP_CODE=$(echo "${TENANT_RESPONSE}" | tail -n1)
    TENANT_RESPONSE_BODY=$(echo "${TENANT_RESPONSE}" | sed '$d')

    if [ "${TENANT_HTTP_CODE}" = "200" ] || [ "${TENANT_HTTP_CODE}" = "201" ]; then
      FINANCIAL_TENANT_ID=$(echo "${TENANT_RESPONSE_BODY}" | jq -r '.result.id')
      echo "✅ Financial grade business tenant created: ${FINANCIAL_TENANT_ID}"
    else
      echo "⚠️  Financial tenant creation failed (HTTP ${TENANT_HTTP_CODE})"
      echo "Response: ${TENANT_RESPONSE_BODY}" | jq '.' || echo "${TENANT_RESPONSE_BODY}"
      echo "⚠️  Skipping client and authentication policy creation..."
      FINANCIAL_TENANT_ID=""
    fi
    echo ""
  fi

  # Step 5: Create financial web app clients
  if [ -n "${FINANCIAL_TENANT_ID}" ]; then
    echo "🔧 Step 5: Creating financial grade web application clients..."

    # Define client files to register
    CLIENT_FILES=(
      "financial-client.json:self_signed_tls_client_auth"
      "private-key-jwt-client.json:private_key_jwt"
      "private-key-jwt-client-2.json:private_key_jwt"
      "tls-client-auth-client.json:tls_client_auth"
      "tls-client-auth-client-2.json:tls_client_auth"
    )

    for CLIENT_ENTRY in "${CLIENT_FILES[@]}"; do
      CLIENT_FILE="${CLIENT_ENTRY%%:*}"
      CLIENT_AUTH_METHOD="${CLIENT_ENTRY##*:}"
      CLIENT_FILE_PATH="${SCRIPT_DIR}/${CLIENT_FILE}"

      if [ ! -f "${CLIENT_FILE_PATH}" ]; then
        echo "⚠️  ${CLIENT_FILE} not found, skipping..."
        continue
      fi

      CLIENT_JSON=$(cat "${CLIENT_FILE_PATH}")
      CLIENT_ALIAS=$(echo "${CLIENT_JSON}" | jq -r '.client_id_alias // .client_id')

      echo "   📝 Registering client: ${CLIENT_ALIAS} (${CLIENT_AUTH_METHOD})..."

      CLIENT_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
        "${AUTHORIZATION_SERVER_URL}/v1/management/organizations/${ORG_ID}/tenants/${FINANCIAL_TENANT_ID}/clients" \
        -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
        -H "Content-Type: application/json" \
        -d "${CLIENT_JSON}")

      CLIENT_HTTP_CODE=$(echo "${CLIENT_RESPONSE}" | tail -n1)
      CLIENT_RESPONSE_BODY=$(echo "${CLIENT_RESPONSE}" | sed '$d')

      if [ "${CLIENT_HTTP_CODE}" = "200" ] || [ "${CLIENT_HTTP_CODE}" = "201" ]; then
        CREATED_CLIENT_ID=$(echo "${CLIENT_RESPONSE_BODY}" | jq -r '.result.client_id')
        echo "   ✅ Created: ${CREATED_CLIENT_ID}"
      else
        echo "   ⚠️  Failed (HTTP ${CLIENT_HTTP_CODE})"
        echo "   Response: ${CLIENT_RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "   ${CLIENT_RESPONSE_BODY}"
      fi
    done
    echo ""
  fi

  # Step 6: Create authentication configurations
  if [ -n "${FINANCIAL_TENANT_ID}" ]; then
    echo "🔧 Step 6: Creating authentication configurations..."

    # FIDO2 (WebAuthn4J) authentication config
    FIDO2_CONFIG_FILE="${SCRIPT_DIR}/authentication-config/fido2/webauthn4j.json"
    if [ -f "${FIDO2_CONFIG_FILE}" ]; then
      echo "   📝 Registering FIDO2 (WebAuthn4J) authentication config..."
      FIDO2_CONFIG_JSON=$(cat "${FIDO2_CONFIG_FILE}")

      FIDO2_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
        "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${FINANCIAL_TENANT_ID}/authentication-configurations" \
        -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}" \
        -H "Content-Type: application/json" \
        -d "${FIDO2_CONFIG_JSON}")

      FIDO2_HTTP_CODE=$(echo "${FIDO2_RESPONSE}" | tail -n1)
      FIDO2_RESPONSE_BODY=$(echo "${FIDO2_RESPONSE}" | sed '$d')

      if [ "${FIDO2_HTTP_CODE}" = "200" ] || [ "${FIDO2_HTTP_CODE}" = "201" ]; then
        FIDO2_CONFIG_ID=$(echo "${FIDO2_RESPONSE_BODY}" | jq -r '.result.id')
        echo "   ✅ FIDO2 config created: ${FIDO2_CONFIG_ID}"
      else
        echo "   ⚠️  FIDO2 config creation failed (HTTP ${FIDO2_HTTP_CODE})"
        echo "   Response: ${FIDO2_RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "   ${FIDO2_RESPONSE_BODY}"
      fi
    else
      echo "   ⚠️  FIDO2 config file not found, skipping..."
    fi

    # Email authentication config
    EMAIL_CONFIG_FILE="${SCRIPT_DIR}/authentication-config/email/no-action.json"
    if [ -f "${EMAIL_CONFIG_FILE}" ]; then
      echo "   📝 Registering Email authentication config..."
      EMAIL_CONFIG_JSON=$(cat "${EMAIL_CONFIG_FILE}")

      EMAIL_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
        "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${FINANCIAL_TENANT_ID}/authentication-configurations" \
        -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}" \
        -H "Content-Type: application/json" \
        -d "${EMAIL_CONFIG_JSON}")

      EMAIL_HTTP_CODE=$(echo "${EMAIL_RESPONSE}" | tail -n1)
      EMAIL_RESPONSE_BODY=$(echo "${EMAIL_RESPONSE}" | sed '$d')

      if [ "${EMAIL_HTTP_CODE}" = "200" ] || [ "${EMAIL_HTTP_CODE}" = "201" ]; then
        EMAIL_CONFIG_ID=$(echo "${EMAIL_RESPONSE_BODY}" | jq -r '.result.id')
        echo "   ✅ Email config created: ${EMAIL_CONFIG_ID}"
      else
        echo "   ⚠️  Email config creation failed (HTTP ${EMAIL_HTTP_CODE})"
        echo "   Response: ${EMAIL_RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "   ${EMAIL_RESPONSE_BODY}"
      fi
    else
      echo "   ⚠️  Email config file not found, skipping..."
    fi
    echo ""
  fi

  # Step 7: Create authentication policies
  if [ -n "${FINANCIAL_TENANT_ID}" ]; then
    echo "🔒 Step 7: Creating financial grade authentication policies..."

    # Define authentication policy files to register
    AUTH_POLICY_FILES=(
      "oauth.json:OAuth"
      "ciba.json:CIBA"
    )

    for POLICY_ENTRY in "${AUTH_POLICY_FILES[@]}"; do
      POLICY_FILE="${POLICY_ENTRY%%:*}"
      POLICY_NAME="${POLICY_ENTRY##*:}"
      AUTH_POLICY_FILE="${SCRIPT_DIR}/authentication-policy/${POLICY_FILE}"

      if [ ! -f "${AUTH_POLICY_FILE}" ]; then
        echo "   ⚠️  ${POLICY_FILE} not found, skipping..."
        continue
      fi

      echo "   📝 Registering ${POLICY_NAME} authentication policy..."
      AUTH_POLICY_JSON=$(cat "${AUTH_POLICY_FILE}")

      POLICY_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
        "${AUTHORIZATION_SERVER_URL}/v1/management/organizations/${ORG_ID}/tenants/${FINANCIAL_TENANT_ID}/authentication-policies" \
        -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
        -H "Content-Type: application/json" \
        -d "${AUTH_POLICY_JSON}")

      POLICY_HTTP_CODE=$(echo "${POLICY_RESPONSE}" | tail -n1)
      POLICY_RESPONSE_BODY=$(echo "${POLICY_RESPONSE}" | sed '$d')

      if [ "${POLICY_HTTP_CODE}" = "200" ] || [ "${POLICY_HTTP_CODE}" = "201" ]; then
        CREATED_POLICY_ID=$(echo "${POLICY_RESPONSE_BODY}" | jq -r '.result.id')
        echo "   ✅ ${POLICY_NAME} authentication policy created: ${CREATED_POLICY_ID}"
      else
        echo "   ⚠️  ${POLICY_NAME} policy creation failed (HTTP ${POLICY_HTTP_CODE})"
        echo "   Response: ${POLICY_RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "   ${POLICY_RESPONSE_BODY}"
      fi
    done
    echo ""
  fi

  # Step 8: Create test user for CIBA
  if [ -n "${FINANCIAL_TENANT_ID}" ]; then
    echo "👤 Step 8: Creating test user for CIBA..."

    FINANCIAL_USER_FILE="${SCRIPT_DIR}/financial-user.json"
    if [ ! -f "${FINANCIAL_USER_FILE}" ]; then
      echo "⚠️  financial-user.json not found at ${FINANCIAL_USER_FILE}"
      echo "⚠️  Skipping test user creation..."
      echo ""
    else
      FINANCIAL_USER_JSON=$(cat "${FINANCIAL_USER_FILE}")
      FINANCIAL_USER_ID=$(echo "${FINANCIAL_USER_JSON}" | jq -r '.sub')

      USER_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
        "${AUTHORIZATION_SERVER_URL}/v1/management/organizations/${ORG_ID}/tenants/${FINANCIAL_TENANT_ID}/users" \
        -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
        -H "Content-Type: application/json" \
        -d "${FINANCIAL_USER_JSON}")

      USER_HTTP_CODE=$(echo "${USER_RESPONSE}" | tail -n1)
      USER_RESPONSE_BODY=$(echo "${USER_RESPONSE}" | sed '$d')

      if [ "${USER_HTTP_CODE}" = "200" ] || [ "${USER_HTTP_CODE}" = "201" ]; then
        CREATED_USER_ID=$(echo "${USER_RESPONSE_BODY}" | jq -r '.result.sub // .result.id')
        CREATED_USER_EMAIL=$(echo "${USER_RESPONSE_BODY}" | jq -r '.result.email')
        echo "✅ Test user created: ${CREATED_USER_EMAIL} (${CREATED_USER_ID})"
      else
        echo "⚠️  Test user creation failed (HTTP ${USER_HTTP_CODE})"
        echo "Response: ${USER_RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "${USER_RESPONSE_BODY}"
      fi
      echo ""
    fi
  fi

  echo "=========================================="
  echo "✅ Financial Grade Setup Complete!"
  echo "=========================================="
  echo ""
  echo "🆔 Created Resources:"
  echo "   Organization ID:        ${ORG_ID}"
  echo "   Organizer Tenant ID:    ${ORGANIZER_TENANT_ID}"
  echo "   Admin User ID:          ${ADMIN_USER_ID}"
  echo "   Admin Client ID:        ${ADMIN_CLIENT_ID}"
  if [ -n "${FINANCIAL_TENANT_ID}" ]; then
    echo "   Financial Tenant ID:    ${FINANCIAL_TENANT_ID}"
  fi
  if [ -n "${FINANCIAL_CLIENT_ID}" ]; then
    echo "   Financial Client ID:    ${FINANCIAL_CLIENT_ID}"
  fi
  if [ -n "${AUTH_POLICY_ID}" ]; then
    echo "   Auth Policy ID:         ${AUTH_POLICY_ID}"
  fi
  if [ -n "${CREATED_USER_EMAIL}" ]; then
    echo "   Test User Email:        ${CREATED_USER_EMAIL}"
  fi
  echo ""
  echo "🔍 Next Steps:"
  echo "   1. Verify FAPI compliance:"
  echo "      curl ${AUTHORIZATION_SERVER_URL}/${FINANCIAL_TENANT_ID}/.well-known/openid-configuration | jq '.tls_client_certificate_bound_access_tokens'"
  echo ""
  echo "   2. Check FAPI scopes:"
  echo "      curl ${AUTHORIZATION_SERVER_URL}/${FINANCIAL_TENANT_ID}/.well-known/openid-configuration | jq '.extension.fapi_advance_scopes'"
  echo ""
  echo "   3. Test authentication policy:"
  echo "      curl ${AUTHORIZATION_SERVER_URL}/${FINANCIAL_TENANT_ID}/.well-known/openid-configuration | jq '.extension.authentication_policies'"
  echo ""

else
  echo "❌ Onboarding failed (HTTP ${HTTP_CODE})"
  echo "Response: ${RESPONSE_BODY}" | jq '.' || echo "${RESPONSE_BODY}"
  exit 1
fi
