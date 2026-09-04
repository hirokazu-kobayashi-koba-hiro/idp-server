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

  # Step 5: Register authentication policy (acr mapping)
  # acr クレームは認証ポリシーの acr_mapping_rules からしか生成されない。ポリシーが無いと
  # AcrResolver が空文字を返し、ID Token に acr が付かない（Issue #1858）。
  echo "Step 5: Registering authentication policy..."

  AUTH_POLICY_FILE="${SCRIPT_DIR}/authentication-policy/oauth.json"
  if [ -f "${AUTH_POLICY_FILE}" ]; then
    AUTH_POLICY_JSON=$(cat "${AUTH_POLICY_FILE}")

    AUTH_POLICY_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
      "${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${TENANT_ID}/authentication-policies" \
      -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}" \
      -H "Content-Type: application/json" \
      -d "${AUTH_POLICY_JSON}")

    AUTH_POLICY_HTTP_CODE=$(echo "${AUTH_POLICY_RESPONSE}" | tail -n1)
    AUTH_POLICY_RESPONSE_BODY=$(echo "${AUTH_POLICY_RESPONSE}" | sed '$d')

    if [ "${AUTH_POLICY_HTTP_CODE}" = "200" ] || [ "${AUTH_POLICY_HTTP_CODE}" = "201" ]; then
      echo "Authentication policy created successfully"
    else
      echo "Warning: Authentication policy creation failed (HTTP ${AUTH_POLICY_HTTP_CODE})"
      echo "Response: ${AUTH_POLICY_RESPONSE_BODY}" | jq '.' || echo "${AUTH_POLICY_RESPONSE_BODY}"
    fi
  else
    echo "Warning: authentication-policy/oauth.json not found, skipping"
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
  # 表示する値は設定ファイルから読む。ハードコードすると設定を変えたときに出力だけが古くなる。
  USER_EMAIL=$(jq -r '.user.email' "${ONBOARDING_REQUEST}")
  USER_PASSWORD=$(jq -r '.user.raw_password' "${ONBOARDING_REQUEST}")
  CLIENT1_ID=$(jq -r '.client.client_id' "${ONBOARDING_REQUEST}")
  CLIENT1_ALIAS=$(jq -r '.client.client_id_alias' "${ONBOARDING_REQUEST}")
  CLIENT1_SECRET=$(jq -r '.client.client_secret' "${ONBOARDING_REQUEST}")
  CLIENT1_AUTH=$(jq -r '.client.token_endpoint_auth_method' "${ONBOARDING_REQUEST}")
  CLIENT1_REDIRECT=$(jq -r '.client.redirect_uris[0]' "${ONBOARDING_REQUEST}")
  CLIENT2_ID=$(jq -r '.client_id' "${CLIENT_POST_FILE}")
  CLIENT2_ALIAS=$(jq -r '.client_id_alias' "${CLIENT_POST_FILE}")
  CLIENT2_SECRET=$(jq -r '.client_secret' "${CLIENT_POST_FILE}")
  CLIENT2_AUTH=$(jq -r '.token_endpoint_auth_method' "${CLIENT_POST_FILE}")
  CLIENT3_ID=$(jq -r '.client_id' "${CLIENT_SECOND_FILE}")
  CLIENT3_ALIAS=$(jq -r '.client_id_alias' "${CLIENT_SECOND_FILE}")
  CLIENT3_SECRET=$(jq -r '.client_secret' "${CLIENT_SECOND_FILE}")
  CLIENT3_AUTH=$(jq -r '.token_endpoint_auth_method' "${CLIENT_SECOND_FILE}")

  echo "Test User:"
  echo "   Email:    ${USER_EMAIL}"
  echo "   Password: ${USER_PASSWORD}"
  echo ""
  echo "Client 1 (${CLIENT1_AUTH}):"
  echo "   Client ID:     ${CLIENT1_ID}"
  echo "   Client Alias:  ${CLIENT1_ALIAS}"
  echo "   Client Secret: ${CLIENT1_SECRET}"
  echo "   Auth Method:   ${CLIENT1_AUTH}"
  echo ""
  echo "Client 2 (${CLIENT2_AUTH}):"
  echo "   Client ID:     ${CLIENT2_ID}"
  echo "   Client Alias:  ${CLIENT2_ALIAS}"
  echo "   Client Secret: ${CLIENT2_SECRET}"
  echo "   Auth Method:   ${CLIENT2_AUTH}"
  echo ""
  echo "Client 3 (second client):"
  echo "   Client ID:     ${CLIENT3_ID}"
  echo "   Client Alias:  ${CLIENT3_ALIAS}"
  echo "   Client Secret: ${CLIENT3_SECRET}"
  echo "   Auth Method:   ${CLIENT3_AUTH}"
  echo ""
  echo "OIDC Discovery:"
  echo "   ${AUTHORIZATION_SERVER_URL}/${TENANT_ID}/.well-known/openid-configuration"
  echo ""
  echo "Conformance Suite Configuration:"
  echo "   Issuer:                  ${AUTHORIZATION_SERVER_URL}/${TENANT_ID}"
  echo "   Client ID (basic):       ${CLIENT1_ID}"
  echo "   Client Secret (basic):   ${CLIENT1_SECRET}"
  echo "   Client ID (post):        ${CLIENT2_ID}"
  echo "   Client Secret (post):    ${CLIENT2_SECRET}"
  echo "   Second Client ID:        ${CLIENT3_ID}"
  echo "   Second Client Secret:    ${CLIENT3_SECRET}"
  echo "   Redirect URI:            ${CLIENT1_REDIRECT}"
  echo ""
else
  echo "Onboarding failed (HTTP ${HTTP_CODE})"
  echo ""
  echo "Error Response:"
  echo "${RESPONSE_BODY}" | jq '.' || echo "${RESPONSE_BODY}"
  echo ""
  exit 1
fi
