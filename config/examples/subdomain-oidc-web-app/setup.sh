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
else
  echo "Onboarding failed (HTTP ${HTTP_CODE})"
  echo ""
  echo "Error Response:"
  echo "${RESPONSE_BODY}" | jq '.' || echo "${RESPONSE_BODY}"
  echo ""
  exit 1
fi
