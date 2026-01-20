#!/bin/bash
set -e

# Subdomain OIDC Web Application Setup Script
# This script automates the onboarding process for subdomain-based OIDC configuration

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"
ONBOARDING_REQUEST="${SCRIPT_DIR}/onboarding-request.json"

echo "=========================================="
echo "Subdomain OIDC Web App Setup"
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

# Validate required variables
if [ -z "${AUTHORIZATION_SERVER_URL}" ]; then
  echo "Error: AUTHORIZATION_SERVER_URL not set in .env"
  exit 1
fi

if [ -z "${ADMIN_TENANT_ID}" ]; then
  echo "Error: ADMIN_TENANT_ID not set in .env"
  exit 1
fi

if [ -z "${ADMIN_USER_EMAIL}" ]; then
  echo "Error: ADMIN_USER_EMAIL not set in .env"
  exit 1
fi

echo "Environment variables loaded"
echo "   Server: ${AUTHORIZATION_SERVER_URL}"
echo "   Tenant: ${ADMIN_TENANT_ID}"
echo "   Admin:  ${ADMIN_USER_EMAIL}"
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

# Step 2: Execute onboarding API
echo "Step 2: Executing onboarding API..."
ONBOARDING_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  "${AUTHORIZATION_SERVER_URL}/v1/management/onboarding" \
  -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d @"${ONBOARDING_REQUEST}")

HTTP_CODE=$(echo "${ONBOARDING_RESPONSE}" | tail -n1)
RESPONSE_BODY=$(echo "${ONBOARDING_RESPONSE}" | sed '$d')

if [ "${HTTP_CODE}" = "201" ]; then
  echo "Onboarding successful!"
  echo ""
  echo "Response:"
  echo "${RESPONSE_BODY}" | jq '.'
  echo ""

  # Extract IDs from response
  ORG_ID=$(echo "${RESPONSE_BODY}" | jq -r '.organization.id')
  TENANT_ID=$(echo "${RESPONSE_BODY}" | jq -r '.tenant.id')

  # Step 3: Create web client for sample-web
  echo "Step 3: Creating web client for sample-web..."

  WEB_CLIENT_FILE="${SCRIPT_DIR}/web-client.json"
  if [ ! -f "${WEB_CLIENT_FILE}" ]; then
    echo "Warning: web-client.json not found at ${WEB_CLIENT_FILE}"
    echo "Skipping web client creation..."
    WEB_CLIENT_ID=""
  else
    WEB_CLIENT_JSON=$(cat "${WEB_CLIENT_FILE}")

    WEB_CLIENT_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
      "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${TENANT_ID}/clients" \
      -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}" \
      -H "Content-Type: application/json" \
      -d "${WEB_CLIENT_JSON}")

    WEB_CLIENT_HTTP_CODE=$(echo "${WEB_CLIENT_RESPONSE}" | tail -n1)
    WEB_CLIENT_RESPONSE_BODY=$(echo "${WEB_CLIENT_RESPONSE}" | sed '$d')

    if [ "${WEB_CLIENT_HTTP_CODE}" = "200" ] || [ "${WEB_CLIENT_HTTP_CODE}" = "201" ]; then
      WEB_CLIENT_ID=$(echo "${WEB_CLIENT_RESPONSE_BODY}" | jq -r '.result.client_id')
      echo "Web client created: ${WEB_CLIENT_ID}"
    else
      echo "Warning: Web client creation failed (HTTP ${WEB_CLIENT_HTTP_CODE})"
      echo "Response: ${WEB_CLIENT_RESPONSE_BODY}" | jq '.' || echo "${WEB_CLIENT_RESPONSE_BODY}"
      WEB_CLIENT_ID=""
    fi
  fi
  echo ""

  # Step 4: Create authentication configurations
  echo "Step 4: Creating authentication configurations..."

  # FIDO2 (WebAuthn4J) authentication config
  FIDO2_CONFIG_FILE="${SCRIPT_DIR}/authentication-config/fido2/webauthn4j.json"
  if [ -f "${FIDO2_CONFIG_FILE}" ]; then
    echo "   Registering FIDO2 (WebAuthn4J) config..."
    FIDO2_CONFIG_JSON=$(cat "${FIDO2_CONFIG_FILE}")

    FIDO2_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
      "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${TENANT_ID}/authentication-configurations" \
      -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}" \
      -H "Content-Type: application/json" \
      -d "${FIDO2_CONFIG_JSON}")

    FIDO2_HTTP_CODE=$(echo "${FIDO2_RESPONSE}" | tail -n1)
    FIDO2_RESPONSE_BODY=$(echo "${FIDO2_RESPONSE}" | sed '$d')

    if [ "${FIDO2_HTTP_CODE}" = "200" ] || [ "${FIDO2_HTTP_CODE}" = "201" ]; then
      FIDO2_CONFIG_ID=$(echo "${FIDO2_RESPONSE_BODY}" | jq -r '.result.id')
      echo "   FIDO2 config created: ${FIDO2_CONFIG_ID}"
    else
      echo "   Warning: FIDO2 config creation failed (HTTP ${FIDO2_HTTP_CODE})"
      echo "   Response: ${FIDO2_RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "   ${FIDO2_RESPONSE_BODY}"
    fi
  else
    echo "   Skipping FIDO2 config (file not found)"
  fi

  # Initial registration config
  INITIAL_REG_CONFIG_FILE="${SCRIPT_DIR}/authentication-config/initial-registration/standard.json"
  if [ -f "${INITIAL_REG_CONFIG_FILE}" ]; then
    echo "   Registering Initial Registration config..."
    INITIAL_REG_CONFIG_JSON=$(cat "${INITIAL_REG_CONFIG_FILE}")

    INITIAL_REG_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
      "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${TENANT_ID}/authentication-configurations" \
      -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}" \
      -H "Content-Type: application/json" \
      -d "${INITIAL_REG_CONFIG_JSON}")

    INITIAL_REG_HTTP_CODE=$(echo "${INITIAL_REG_RESPONSE}" | tail -n1)
    INITIAL_REG_RESPONSE_BODY=$(echo "${INITIAL_REG_RESPONSE}" | sed '$d')

    if [ "${INITIAL_REG_HTTP_CODE}" = "200" ] || [ "${INITIAL_REG_HTTP_CODE}" = "201" ]; then
      INITIAL_REG_CONFIG_ID=$(echo "${INITIAL_REG_RESPONSE_BODY}" | jq -r '.result.id')
      echo "   Initial Registration config created: ${INITIAL_REG_CONFIG_ID}"
    else
      echo "   Warning: Initial Registration config creation failed (HTTP ${INITIAL_REG_HTTP_CODE})"
      echo "   Response: ${INITIAL_REG_RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "   ${INITIAL_REG_RESPONSE_BODY}"
    fi
  else
    echo "   Skipping Initial Registration config (file not found)"
  fi

  # Email authentication config
  EMAIL_CONFIG_FILE="${SCRIPT_DIR}/authentication-config/email/no-action.json"
  if [ -f "${EMAIL_CONFIG_FILE}" ]; then
    echo "   Registering Email config..."
    EMAIL_CONFIG_JSON=$(cat "${EMAIL_CONFIG_FILE}")

    EMAIL_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
      "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${TENANT_ID}/authentication-configurations" \
      -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}" \
      -H "Content-Type: application/json" \
      -d "${EMAIL_CONFIG_JSON}")

    EMAIL_HTTP_CODE=$(echo "${EMAIL_RESPONSE}" | tail -n1)
    EMAIL_RESPONSE_BODY=$(echo "${EMAIL_RESPONSE}" | sed '$d')

    if [ "${EMAIL_HTTP_CODE}" = "200" ] || [ "${EMAIL_HTTP_CODE}" = "201" ]; then
      EMAIL_CONFIG_ID=$(echo "${EMAIL_RESPONSE_BODY}" | jq -r '.result.id')
      echo "   Email config created: ${EMAIL_CONFIG_ID}"
    else
      echo "   Warning: Email config creation failed (HTTP ${EMAIL_HTTP_CODE})"
      echo "   Response: ${EMAIL_RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "   ${EMAIL_RESPONSE_BODY}"
    fi
  else
    echo "   Skipping Email config (file not found)"
  fi
  echo ""

  # Step 5: Create authentication policy
  echo "Step 5: Creating authentication policy..."

  OAUTH_POLICY_FILE="${SCRIPT_DIR}/authentication-policy/oauth.json"
  if [ -f "${OAUTH_POLICY_FILE}" ]; then
    echo "   Registering OAuth authentication policy..."
    OAUTH_POLICY_JSON=$(cat "${OAUTH_POLICY_FILE}")

    POLICY_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
      "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${TENANT_ID}/authentication-policies" \
      -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}" \
      -H "Content-Type: application/json" \
      -d "${OAUTH_POLICY_JSON}")

    POLICY_HTTP_CODE=$(echo "${POLICY_RESPONSE}" | tail -n1)
    POLICY_RESPONSE_BODY=$(echo "${POLICY_RESPONSE}" | sed '$d')

    if [ "${POLICY_HTTP_CODE}" = "200" ] || [ "${POLICY_HTTP_CODE}" = "201" ]; then
      POLICY_ID=$(echo "${POLICY_RESPONSE_BODY}" | jq -r '.result.id')
      echo "   OAuth policy created: ${POLICY_ID}"
    else
      echo "   Warning: OAuth policy creation failed (HTTP ${POLICY_HTTP_CODE})"
      echo "   Response: ${POLICY_RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "   ${POLICY_RESPONSE_BODY}"
    fi
  else
    echo "   Skipping OAuth policy (file not found)"
  fi

  # FIDO2 Registration policy
  FIDO2_REG_POLICY_FILE="${SCRIPT_DIR}/authentication-policy/fido2-registration.json"
  if [ -f "${FIDO2_REG_POLICY_FILE}" ]; then
    echo "   Registering FIDO2 Registration policy..."
    FIDO2_REG_POLICY_JSON=$(cat "${FIDO2_REG_POLICY_FILE}")

    FIDO2_REG_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
      "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${TENANT_ID}/authentication-policies" \
      -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}" \
      -H "Content-Type: application/json" \
      -d "${FIDO2_REG_POLICY_JSON}")

    FIDO2_REG_HTTP_CODE=$(echo "${FIDO2_REG_RESPONSE}" | tail -n1)
    FIDO2_REG_RESPONSE_BODY=$(echo "${FIDO2_REG_RESPONSE}" | sed '$d')

    if [ "${FIDO2_REG_HTTP_CODE}" = "200" ] || [ "${FIDO2_REG_HTTP_CODE}" = "201" ]; then
      FIDO2_REG_POLICY_ID=$(echo "${FIDO2_REG_RESPONSE_BODY}" | jq -r '.result.id')
      echo "   FIDO2 Registration policy created: ${FIDO2_REG_POLICY_ID}"
    else
      echo "   Warning: FIDO2 Registration policy creation failed (HTTP ${FIDO2_REG_HTTP_CODE})"
      echo "   Response: ${FIDO2_REG_RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "   ${FIDO2_REG_RESPONSE_BODY}"
    fi
  else
    echo "   Skipping FIDO2 Registration policy (file not found)"
  fi
  echo ""

  echo "=========================================="
  echo "Setup Complete!"
  echo "=========================================="
  echo ""
  echo "Created Resources:"
  echo "   Organization ID: ${ORG_ID}"
  echo "   Tenant ID:       ${TENANT_ID}"
  echo ""
  echo "Subdomain Configuration:"
  echo "   API Domain:  https://api.local.dev"
  echo "   Auth Domain: https://auth.local.dev"
  echo ""
  echo "Admin User:"
  echo "   Email:    admin@local.dev"
  echo "   Password: LocalDevPassword123"
  echo ""
  echo "Admin Client (created by onboarding):"
  echo "   Client ID:     fd385eef-19e5-5150-93c9-6debf1a901fa"
  echo "   Client Secret: local-subdomain-secret-32chars1"
  echo "   Scopes:        openid profile email management"
  echo "   Redirect URI:  https://auth.local.dev/callback/"
  echo ""
  if [ -n "${WEB_CLIENT_ID}" ]; then
    echo "Web Client (for sample-web):"
    echo "   Client ID:     8a9f5e2c-1b3d-4c6a-9f8e-7d5c3a2b1e4f"
    echo "   Client Secret: local-dev-public-secret-32char"
    echo "   Scopes:        openid profile email"
    echo "   Redirect URI:  https://sample.local.dev/api/auth/callback/idp-server"
    echo ""
  fi
  echo "Test Authorization Code Flow:"
  echo "   1. Configure /etc/hosts (or use dnsmasq):"
  echo "      127.0.0.1 api.local.dev"
  echo "      127.0.0.1 auth.local.dev"
  echo "      127.0.0.1 sample.local.dev"
  echo ""
  echo "   2. Open browser:"
  echo "      open \"https://api.local.dev/${TENANT_ID}/v1/authorizations?response_type=code&client_id=fd385eef-19e5-5150-93c9-6debf1a901fa&redirect_uri=https://auth.local.dev/callback/&scope=openid%20profile%20email&state=test-state\""
  echo ""
  echo "   3. Login with admin credentials and get the authorization code from redirect URL"
  echo ""
  if [ -n "${WEB_CLIENT_ID}" ]; then
    echo "Test sample-web (docker-compose):"
    echo "   docker-compose up sample-web"
    echo "   open https://sample.local.dev"
    echo ""
  fi

  echo "Authentication Methods:"
  echo "   - Password authentication"
  echo "   - Email OTP (no-action mode for local dev)"
  echo "   - Passkey/FIDO2 (WebAuthn4J)"
  echo ""
  echo "FIDO2/Passkey Configuration:"
  echo "   RP ID:   local.dev"
  echo "   Origin:  https://auth.local.dev"
  echo ""
else
  echo "Onboarding failed (HTTP ${HTTP_CODE})"
  echo ""
  echo "Error Response:"
  echo "${RESPONSE_BODY}" | jq '.' || echo "${RESPONSE_BODY}"
  echo ""
  exit 1
fi
