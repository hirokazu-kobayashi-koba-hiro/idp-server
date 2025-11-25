#!/bin/bash
set -e

# Financial Grade Configuration Verification Script
# Verifies that FAPI settings are correctly applied

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"

echo "=========================================="
echo "🔍 Financial Grade Configuration Verification"
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

# Read IDs from configuration files
ONBOARDING_REQUEST="${SCRIPT_DIR}/onboarding-request.json"
FINANCIAL_TENANT_FILE="${SCRIPT_DIR}/financial-tenant.json"

if [ ! -f "${ONBOARDING_REQUEST}" ]; then
  echo "❌ Error: onboarding-request.json not found"
  exit 1
fi

ORG_ID=$(jq -r '.organization.id' "${ONBOARDING_REQUEST}")
ORGANIZER_TENANT_ID=$(jq -r '.tenant.id' "${ONBOARDING_REQUEST}")

if [ -f "${FINANCIAL_TENANT_FILE}" ]; then
  FINANCIAL_TENANT_ID=$(jq -r '.tenant.id' "${FINANCIAL_TENANT_FILE}")
else
  FINANCIAL_TENANT_ID=""
fi

echo "✅ Configuration IDs loaded"
echo "   Organization ID:        ${ORG_ID}"
echo "   Organizer Tenant ID:    ${ORGANIZER_TENANT_ID}"
if [ -n "${FINANCIAL_TENANT_ID}" ]; then
  echo "   Financial Tenant ID:    ${FINANCIAL_TENANT_ID}"
fi
echo ""

# Get system admin access token
echo "🔐 Getting system administrator access token..."
SYSTEM_ACCESS_TOKEN=$("${PROJECT_ROOT}/config/scripts/get-access-token.sh" \
  -u "${ADMIN_USER_EMAIL}" \
  -p "${ADMIN_USER_PASSWORD}" \
  -t "${ADMIN_TENANT_ID}" \
  -e "${AUTHORIZATION_SERVER_URL}" \
  -c "${ADMIN_CLIENT_ID}" \
  -s "${ADMIN_CLIENT_SECRET}")

if [ -z "${SYSTEM_ACCESS_TOKEN}" ] || [ "${SYSTEM_ACCESS_TOKEN}" = "null" ]; then
  echo "❌ Error: Failed to get access token"
  exit 1
fi

echo "✅ Access token obtained"
echo ""

# Verify Financial Tenant if exists
if [ -n "${FINANCIAL_TENANT_ID}" ]; then
  echo "=========================================="
  echo "🏦 Verifying Financial Tenant Configuration"
  echo "=========================================="
  echo ""

  # Get Discovery configuration
  echo "📡 Fetching Discovery configuration..."
  DISCOVERY_URL="${AUTHORIZATION_SERVER_URL}/${FINANCIAL_TENANT_ID}/.well-known/openid-configuration"
  DISCOVERY_RESPONSE=$(curl -s "${DISCOVERY_URL}")

  echo "✅ Discovery endpoint: ${DISCOVERY_URL}"
  echo ""

  # Check FAPI compliance
  echo "🔒 FAPI Compliance Check:"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

  # 1. MTLS
  MTLS=$(echo "${DISCOVERY_RESPONSE}" | jq -r '.tls_client_certificate_bound_access_tokens')
  if [ "$MTLS" = "true" ]; then
    echo "✅ MTLS: Enabled"
  else
    echo "❌ MTLS: Disabled (Expected: true, Actual: $MTLS)"
  fi

  # 2. Token endpoint auth methods
  AUTH_METHODS=$(echo "${DISCOVERY_RESPONSE}" | jq -r '.token_endpoint_auth_methods_supported | join(", ")')
  echo "📋 Token Endpoint Auth Methods: $AUTH_METHODS"

  if echo "${DISCOVERY_RESPONSE}" | jq -e '.token_endpoint_auth_methods_supported | contains(["private_key_jwt"])' > /dev/null; then
    echo "✅ private_key_jwt: Supported"
  else
    echo "❌ private_key_jwt: Not supported"
  fi

  if echo "${DISCOVERY_RESPONSE}" | jq -e '.token_endpoint_auth_methods_supported | contains(["tls_client_auth"])' > /dev/null; then
    echo "✅ tls_client_auth: Supported"
  else
    echo "❌ tls_client_auth: Not supported"
  fi

  # 3. Request object signing
  REQ_OBJ_ALGS=$(echo "${DISCOVERY_RESPONSE}" | jq -r '.request_object_signing_alg_values_supported | join(", ")')
  echo "📋 Request Object Signing: $REQ_OBJ_ALGS"

  # 4. Subject type
  SUBJECT_TYPES=$(echo "${DISCOVERY_RESPONSE}" | jq -r '.subject_types_supported | join(", ")')
  echo "📋 Subject Types: $SUBJECT_TYPES"

  if echo "${DISCOVERY_RESPONSE}" | jq -e '.subject_types_supported | contains(["pairwise"])' > /dev/null; then
    echo "✅ pairwise: Supported"
  else
    echo "❌ pairwise: Not supported"
  fi

  echo ""
fi

# Get tenant via Management API
if [ -n "${FINANCIAL_TENANT_ID}" ]; then
  echo "=========================================="
  echo "🔍 Management API Verification"
  echo "=========================================="
  echo ""

  echo "📡 Fetching tenant via Management API..."
  MGMT_TENANT_URL="${AUTHORIZATION_SERVER_URL}/v1/management/tenants/${FINANCIAL_TENANT_ID}/authorization-server"
  MGMT_RESPONSE=$(curl -s "${MGMT_TENANT_URL}" \
    -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}")

  if echo "${MGMT_RESPONSE}" | jq -e '.' > /dev/null 2>&1; then
    echo "✅ Tenant retrieved via Management API"
    echo ""
    echo $MGMT_RESPONSE

    echo "🔒 Authorization Server Extension (via Management API):"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    EXTENSION=$(echo "${MGMT_RESPONSE}" | jq '.extension')
    echo "${EXTENSION}"

    echo ""
    echo "🎯 FAPI Scopes (from Management API):"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    # FAPI Baseline Scopes
    FAPI_BASELINE=$(echo "${EXTENSION}" | jq -r '.fapi_baseline_scopes // [] | join(", ")')
    if [ -n "$FAPI_BASELINE" ] && [ "$FAPI_BASELINE" != "" ]; then
      echo "✅ FAPI Baseline Scopes: [$FAPI_BASELINE]"
    else
      echo "❌ FAPI Baseline Scopes: Not configured (Expected: [read, account])"
    fi

    # FAPI Advance Scopes
    FAPI_ADVANCE=$(echo "${EXTENSION}" | jq -r '.fapi_advance_scopes // [] | join(", ")')
    if [ -n "$FAPI_ADVANCE" ] && [ "$FAPI_ADVANCE" != "" ]; then
      echo "✅ FAPI Advance Scopes: [$FAPI_ADVANCE]"
    else
      echo "❌ FAPI Advance Scopes: Not configured (Expected: [write, transfers])"
    fi

    # Required Identity Verification Scopes
    REQUIRED_IV=$(echo "${EXTENSION}" | jq -r '.required_identity_verification_scopes // [] | join(", ")')
    if [ -n "$REQUIRED_IV" ] && [ "$REQUIRED_IV" != "" ]; then
      echo "✅ Required Identity Verification: [$REQUIRED_IV]"
    else
      echo "❌ Required Identity Verification: Not configured (Expected: [transfers])"
    fi

    echo ""
    echo "⚙️  Token Settings (from Management API):"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    # Access token duration
    ACCESS_TOKEN_DURATION=$(echo "${EXTENSION}" | jq -r '.access_token_duration // "not set"')
    echo "📋 Access Token Duration: ${ACCESS_TOKEN_DURATION}s"
    if [ "$ACCESS_TOKEN_DURATION" = "900" ]; then
      echo "✅ Expected: 900s (15 minutes)"
    else
      echo "⚠️  Expected: 900s, Actual: ${ACCESS_TOKEN_DURATION}s"
    fi

    # ID token duration
    ID_TOKEN_DURATION=$(echo "${EXTENSION}" | jq -r '.id_token_duration // "not set"')
    echo "📋 ID Token Duration: ${ID_TOKEN_DURATION}s"

    # ID token strict mode
    ID_TOKEN_STRICT=$(echo "${EXTENSION}" | jq -r '.id_token_strict_mode // "not set"')
    echo "📋 ID Token Strict Mode: $ID_TOKEN_STRICT"
    if [ "$ID_TOKEN_STRICT" = "true" ]; then
      echo "✅ Strict mode enabled"
    else
      echo "⚠️  Strict mode disabled (Expected: true)"
    fi

    # Refresh token duration
    REFRESH_TOKEN_DURATION=$(echo "${EXTENSION}" | jq -r '.refresh_token_duration // "not set"')
    echo "📋 Refresh Token Duration: ${REFRESH_TOKEN_DURATION}s"


  else
    echo "❌ Failed to retrieve tenant via Management API"
    echo "Response: ${MGMT_RESPONSE}" | jq '.'
  fi
fi

echo ""
echo "=========================================="
echo "✅ Verification Complete"
echo "=========================================="
