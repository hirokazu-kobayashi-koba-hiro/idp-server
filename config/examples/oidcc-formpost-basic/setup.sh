#!/bin/bash
set -e

# OIDCC Form Post Basic Certification Test Setup Script
# This script sets up the environment for OIDCC certification tests

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"
ONBOARDING_REQUEST="${SCRIPT_DIR}/onboarding-request.json"

echo "=========================================="
echo "OIDCC Form Post Basic Certification Setup"
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

  # Step 3: Create second client (client_secret_post)
  echo "Step 3: Creating second client (client_secret_post)..."

  CLIENT_POST_FILE="${SCRIPT_DIR}/client-post.json"
  if [ -f "${CLIENT_POST_FILE}" ]; then
    CLIENT_POST_JSON=$(cat "${CLIENT_POST_FILE}")

    CLIENT_POST_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
      "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${TENANT_ID}/clients" \
      -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}" \
      -H "Content-Type: application/json" \
      -d "${CLIENT_POST_JSON}")

    CLIENT_POST_HTTP_CODE=$(echo "${CLIENT_POST_RESPONSE}" | tail -n1)
    CLIENT_POST_RESPONSE_BODY=$(echo "${CLIENT_POST_RESPONSE}" | sed '$d')

    if [ "${CLIENT_POST_HTTP_CODE}" = "200" ] || [ "${CLIENT_POST_HTTP_CODE}" = "201" ]; then
      echo "Second client created successfully"
    else
      echo "Warning: Second client creation failed (HTTP ${CLIENT_POST_HTTP_CODE})"
      echo "Response: ${CLIENT_POST_RESPONSE_BODY}" | jq '.' || echo "${CLIENT_POST_RESPONSE_BODY}"
    fi
  else
    echo "Warning: client-post.json not found, skipping second client creation"
  fi
  echo ""

  # Step 4: Create third client (second client for conformance suite)
  echo "Step 4: Creating third client (second client)..."

  CLIENT_SECOND_FILE="${SCRIPT_DIR}/client-second.json"
  if [ -f "${CLIENT_SECOND_FILE}" ]; then
    CLIENT_SECOND_JSON=$(cat "${CLIENT_SECOND_FILE}")

    CLIENT_SECOND_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
      "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${TENANT_ID}/clients" \
      -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}" \
      -H "Content-Type: application/json" \
      -d "${CLIENT_SECOND_JSON}")

    CLIENT_SECOND_HTTP_CODE=$(echo "${CLIENT_SECOND_RESPONSE}" | tail -n1)
    CLIENT_SECOND_RESPONSE_BODY=$(echo "${CLIENT_SECOND_RESPONSE}" | sed '$d')

    if [ "${CLIENT_SECOND_HTTP_CODE}" = "200" ] || [ "${CLIENT_SECOND_HTTP_CODE}" = "201" ]; then
      echo "Third client created successfully"
    else
      echo "Warning: Third client creation failed (HTTP ${CLIENT_SECOND_HTTP_CODE})"
      echo "Response: ${CLIENT_SECOND_RESPONSE_BODY}" | jq '.' || echo "${CLIENT_SECOND_RESPONSE_BODY}"
    fi
  else
    echo "Warning: client-second.json not found, skipping third client creation"
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
  echo "Test User:"
  echo "   Email:    oidcc-test@example.com"
  echo "   Password: OidccTestPassword123!"
  echo ""
  echo "Client 1 (client_secret_basic):"
  echo "   Client ID:     f4a5b6c7-d8e9-0123-abcd-456789012345"
  echo "   Client Alias:  oidcc-basic-client"
  echo "   Client Secret: oidcc-basic-secret-32characters!"
  echo "   Auth Method:   client_secret_basic"
  echo ""
  echo "Client 2 (client_secret_post):"
  echo "   Client ID:     a5b6c7d8-e9f0-1234-bcde-567890123456"
  echo "   Client Alias:  oidcc-post-client"
  echo "   Client Secret: oidcc-post-secret-32characters!!"
  echo "   Auth Method:   client_secret_post"
  echo ""
  echo "Client 3 (second client):"
  echo "   Client ID:     b6c7d8e9-f0a1-2345-cdef-678901234567"
  echo "   Client Alias:  oidcc-second-client"
  echo "   Client Secret: oidcc-second-secret-32characters!"
  echo "   Auth Method:   client_secret_basic"
  echo ""
  echo "OIDC Discovery:"
  echo "   ${AUTHORIZATION_SERVER_URL}/${TENANT_ID}/.well-known/openid-configuration"
  echo ""
  echo "Conformance Suite Configuration:"
  echo "   Issuer:                  ${AUTHORIZATION_SERVER_URL}/${TENANT_ID}"
  echo "   Client ID (basic):       f4a5b6c7-d8e9-0123-abcd-456789012345"
  echo "   Client Secret (basic):   oidcc-basic-secret-32characters!"
  echo "   Client ID (post):        a5b6c7d8-e9f0-1234-bcde-567890123456"
  echo "   Client Secret (post):    oidcc-post-secret-32characters!!"
  echo "   Second Client ID:        b6c7d8e9-f0a1-2345-cdef-678901234567"
  echo "   Second Client Secret:    oidcc-second-secret-32characters!"
  echo "   Redirect URI:            https://localhost.emobix.co.uk:8443/test/a/oidc-core-basic/callback"
  echo ""
else
  echo "Onboarding failed (HTTP ${HTTP_CODE})"
  echo ""
  echo "Error Response:"
  echo "${RESPONSE_BODY}" | jq '.' || echo "${RESPONSE_BODY}"
  echo ""
  exit 1
fi
