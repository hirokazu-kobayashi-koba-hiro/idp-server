#!/bin/bash
set -e

# Subdomain OIDC Web Application Update Script
# This script updates existing tenant/client/authentication configurations

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"
ONBOARDING_REQUEST="${SCRIPT_DIR}/onboarding-request.json"

echo "=========================================="
echo "Subdomain OIDC Web App Update"
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

# Step 1: Get system administrator access token
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

ACCESS_TOKEN=$(echo "${TOKEN_RESPONSE}" | jq -r '.access_token')

if [ -z "${ACCESS_TOKEN}" ] || [ "${ACCESS_TOKEN}" = "null" ]; then
  echo "Error: Failed to get access token"
  echo "Response: ${TOKEN_RESPONSE}"
  exit 1
fi

echo "Access token obtained: ${ACCESS_TOKEN:0:20}..."
echo ""

# Read IDs from configuration files
echo "Reading configuration from files..."
ORG_ID=$(jq -r '.organization.id' "${ONBOARDING_REQUEST}")
TENANT_ID=$(jq -r '.tenant.id' "${ONBOARDING_REQUEST}")

echo "Configuration loaded"
echo "   Organization ID: ${ORG_ID}"
echo "   Tenant ID:       ${TENANT_ID}"
echo ""

# Step 2: Update tenant configuration
echo "Step 2: Updating tenant configuration..."
TENANT_UPDATE_JSON=$(jq '.tenant' "${ONBOARDING_REQUEST}")

TENANT_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT \
  "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${TENANT_ID}" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "${TENANT_UPDATE_JSON}")

TENANT_HTTP_CODE=$(echo "${TENANT_RESPONSE}" | tail -n1)
TENANT_BODY=$(echo "${TENANT_RESPONSE}" | sed '$d')

if [ "${TENANT_HTTP_CODE}" = "200" ]; then
  echo "Tenant configuration updated"
else
  echo "Warning: Tenant update failed (HTTP ${TENANT_HTTP_CODE})"
  echo "Response: ${TENANT_BODY}" | jq '.' 2>/dev/null || echo "${TENANT_BODY}"
fi
echo ""

# Step 3: Update authorization server configuration
echo "Step 3: Updating authorization server configuration..."
AUTHZ_SERVER_JSON=$(jq '.authorization_server' "${ONBOARDING_REQUEST}")

AUTHZ_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT \
  "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${TENANT_ID}/authorization-server" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "${AUTHZ_SERVER_JSON}")

AUTHZ_HTTP_CODE=$(echo "${AUTHZ_RESPONSE}" | tail -n1)
AUTHZ_BODY=$(echo "${AUTHZ_RESPONSE}" | sed '$d')

if [ "${AUTHZ_HTTP_CODE}" = "200" ]; then
  echo "Authorization server configuration updated"
else
  echo "Warning: Authorization server update failed (HTTP ${AUTHZ_HTTP_CODE})"
  echo "Response: ${AUTHZ_BODY}" | jq '.' 2>/dev/null || echo "${AUTHZ_BODY}"
fi
echo ""

# Step 4: Update web client configuration
echo "Step 4: Updating client configurations..."

WEB_CLIENT_FILE="${SCRIPT_DIR}/web-client.json"
if [ -f "${WEB_CLIENT_FILE}" ]; then
  WEB_CLIENT_JSON=$(cat "${WEB_CLIENT_FILE}")
  WEB_CLIENT_ID=$(echo "${WEB_CLIENT_JSON}" | jq -r '.client_id')

  echo "   Updating web client: ${WEB_CLIENT_ID}..."

  CLIENT_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT \
    "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${TENANT_ID}/clients/${WEB_CLIENT_ID}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${WEB_CLIENT_JSON}")

  CLIENT_HTTP_CODE=$(echo "${CLIENT_RESPONSE}" | tail -n1)
  CLIENT_BODY=$(echo "${CLIENT_RESPONSE}" | sed '$d')

  if [ "${CLIENT_HTTP_CODE}" = "200" ]; then
    echo "   Web client updated"
  else
    echo "   Warning: Web client update failed (HTTP ${CLIENT_HTTP_CODE})"
    echo "   Response: ${CLIENT_BODY}" | jq '.' 2>/dev/null || echo "   ${CLIENT_BODY}"
  fi
else
  echo "   Skipping web client (file not found)"
fi
echo ""

# Step 5: Update authentication configurations
echo "Step 5: Updating authentication configurations..."

# FIDO2 config
FIDO2_CONFIG_FILE="${SCRIPT_DIR}/authentication-config/fido2/webauthn4j.json"
if [ -f "${FIDO2_CONFIG_FILE}" ]; then
  FIDO2_CONFIG_JSON=$(cat "${FIDO2_CONFIG_FILE}")
  FIDO2_CONFIG_ID=$(echo "${FIDO2_CONFIG_JSON}" | jq -r '.id')

  echo "   Updating FIDO2 config: ${FIDO2_CONFIG_ID}..."

  FIDO2_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT \
    "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${TENANT_ID}/authentication-configurations/${FIDO2_CONFIG_ID}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${FIDO2_CONFIG_JSON}")

  FIDO2_HTTP_CODE=$(echo "${FIDO2_RESPONSE}" | tail -n1)
  FIDO2_BODY=$(echo "${FIDO2_RESPONSE}" | sed '$d')

  if [ "${FIDO2_HTTP_CODE}" = "200" ]; then
    echo "   FIDO2 config updated"
  else
    echo "   Warning: FIDO2 config update failed (HTTP ${FIDO2_HTTP_CODE})"
    echo "   Response: ${FIDO2_BODY}" | jq '.' 2>/dev/null || echo "   ${FIDO2_BODY}"
  fi
fi

# Initial Registration config
INITIAL_REG_CONFIG_FILE="${SCRIPT_DIR}/authentication-config/initial-registration/standard.json"
if [ -f "${INITIAL_REG_CONFIG_FILE}" ]; then
  INITIAL_REG_CONFIG_JSON=$(cat "${INITIAL_REG_CONFIG_FILE}")
  INITIAL_REG_CONFIG_ID=$(echo "${INITIAL_REG_CONFIG_JSON}" | jq -r '.id')

  echo "   Updating Initial Registration config: ${INITIAL_REG_CONFIG_ID}..."

  INITIAL_REG_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT \
    "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${TENANT_ID}/authentication-configurations/${INITIAL_REG_CONFIG_ID}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${INITIAL_REG_CONFIG_JSON}")

  INITIAL_REG_HTTP_CODE=$(echo "${INITIAL_REG_RESPONSE}" | tail -n1)
  INITIAL_REG_BODY=$(echo "${INITIAL_REG_RESPONSE}" | sed '$d')

  if [ "${INITIAL_REG_HTTP_CODE}" = "200" ]; then
    echo "   Initial Registration config updated"
  else
    echo "   Warning: Initial Registration config update failed (HTTP ${INITIAL_REG_HTTP_CODE})"
    echo "   Response: ${INITIAL_REG_BODY}" | jq '.' 2>/dev/null || echo "   ${INITIAL_REG_BODY}"
  fi
fi

# Email config
EMAIL_CONFIG_FILE="${SCRIPT_DIR}/authentication-config/email/no-action.json"
if [ -f "${EMAIL_CONFIG_FILE}" ]; then
  EMAIL_CONFIG_JSON=$(cat "${EMAIL_CONFIG_FILE}")
  EMAIL_CONFIG_ID=$(echo "${EMAIL_CONFIG_JSON}" | jq -r '.id')

  echo "   Updating Email config: ${EMAIL_CONFIG_ID}..."

  EMAIL_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT \
    "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${TENANT_ID}/authentication-configurations/${EMAIL_CONFIG_ID}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${EMAIL_CONFIG_JSON}")

  EMAIL_HTTP_CODE=$(echo "${EMAIL_RESPONSE}" | tail -n1)
  EMAIL_BODY=$(echo "${EMAIL_RESPONSE}" | sed '$d')

  if [ "${EMAIL_HTTP_CODE}" = "200" ]; then
    echo "   Email config updated"
  else
    echo "   Warning: Email config update failed (HTTP ${EMAIL_HTTP_CODE})"
    echo "   Response: ${EMAIL_BODY}" | jq '.' 2>/dev/null || echo "   ${EMAIL_BODY}"
  fi
fi
echo ""

# Step 6: Update authentication policies
echo "Step 6: Updating authentication policies..."

# OAuth policy
OAUTH_POLICY_FILE="${SCRIPT_DIR}/authentication-policy/oauth.json"
if [ -f "${OAUTH_POLICY_FILE}" ]; then
  OAUTH_POLICY_JSON=$(cat "${OAUTH_POLICY_FILE}")
  OAUTH_POLICY_ID=$(echo "${OAUTH_POLICY_JSON}" | jq -r '.id')

  echo "   Updating OAuth policy: ${OAUTH_POLICY_ID}..."

  OAUTH_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT \
    "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${TENANT_ID}/authentication-policies/${OAUTH_POLICY_ID}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${OAUTH_POLICY_JSON}")

  OAUTH_HTTP_CODE=$(echo "${OAUTH_RESPONSE}" | tail -n1)
  OAUTH_BODY=$(echo "${OAUTH_RESPONSE}" | sed '$d')

  if [ "${OAUTH_HTTP_CODE}" = "200" ]; then
    echo "   OAuth policy updated"
  else
    echo "   Warning: OAuth policy update failed (HTTP ${OAUTH_HTTP_CODE})"
    echo "   Response: ${OAUTH_BODY}" | jq '.' 2>/dev/null || echo "   ${OAUTH_BODY}"
  fi
fi

# FIDO2 Registration policy
FIDO2_REG_POLICY_FILE="${SCRIPT_DIR}/authentication-policy/fido2-registration.json"
if [ -f "${FIDO2_REG_POLICY_FILE}" ]; then
  FIDO2_REG_POLICY_JSON=$(cat "${FIDO2_REG_POLICY_FILE}")
  FIDO2_REG_POLICY_ID=$(echo "${FIDO2_REG_POLICY_JSON}" | jq -r '.id')

  echo "   Updating FIDO2 Registration policy: ${FIDO2_REG_POLICY_ID}..."

  FIDO2_REG_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT \
    "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${TENANT_ID}/authentication-policies/${FIDO2_REG_POLICY_ID}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${FIDO2_REG_POLICY_JSON}")

  FIDO2_REG_HTTP_CODE=$(echo "${FIDO2_REG_RESPONSE}" | tail -n1)
  FIDO2_REG_BODY=$(echo "${FIDO2_REG_RESPONSE}" | sed '$d')

  if [ "${FIDO2_REG_HTTP_CODE}" = "200" ]; then
    echo "   FIDO2 Registration policy updated"
  else
    echo "   Warning: FIDO2 Registration policy update failed (HTTP ${FIDO2_REG_HTTP_CODE})"
    echo "   Response: ${FIDO2_REG_BODY}" | jq '.' 2>/dev/null || echo "   ${FIDO2_REG_BODY}"
  fi
fi
echo ""

echo "=========================================="
echo "Update Complete!"
echo "=========================================="
echo ""
