#!/bin/bash
set -e

# Financial-Grade (FAPI Advanced + CIBA) - Verification Script
#
# Verifies that the tenant created by setup.sh has correct FAPI configuration by checking:
#   1. Discovery endpoint
#   2. FAPI compliance (mTLS, signed request, PAR, JARM)
#   3. Token endpoint auth methods (private_key_jwt, tls_client_auth)
#   4. CIBA configuration
#   5. FAPI scopes
#
# Prerequisites:
#   - setup.sh has been executed successfully
#   - Generated config exists in config/generated/{organization-name}/
#
# Usage:
#   ./verify.sh
#   ./verify.sh --org my-organization

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../../.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"

# Parse arguments
ORGANIZATION_NAME="financial-grade-2.0"
while [ $# -gt 0 ]; do
  case "$1" in
    --org) ORGANIZATION_NAME="$2"; shift 2 ;;
    *) echo "Unknown option: $1"; exit 1 ;;
  esac
done

echo "=========================================="
echo "Financial-Grade (FAPI Advanced + CIBA) Verification"
echo "=========================================="
echo ""

# --- Load .env ---
if [ ! -f "${ENV_FILE}" ]; then
  echo "Error: .env file not found at ${ENV_FILE}"
  exit 1
fi

set -a
source "${ENV_FILE}"
set +a

: "${AUTHORIZATION_SERVER_URL:?AUTHORIZATION_SERVER_URL is required in .env}"

# --- Load generated config ---
CONFIG_DIR="${PROJECT_ROOT}/config/generated/${ORGANIZATION_NAME}"

if [ ! -d "${CONFIG_DIR}" ]; then
  echo "Error: Generated config not found at ${CONFIG_DIR}"
  echo "Run setup.sh first."
  exit 1
fi

PUBLIC_TENANT_ID=$(jq -r '.tenant.id' "${CONFIG_DIR}/financial-tenant.json")

echo "Server:       ${AUTHORIZATION_SERVER_URL}"
echo "Organization: ${ORGANIZATION_NAME}"
echo "Tenant ID:    ${PUBLIC_TENANT_ID}"
echo ""

TENANT_BASE="${AUTHORIZATION_SERVER_URL}/${PUBLIC_TENANT_ID}"

PASS_COUNT=0
FAIL_COUNT=0

check_result() {
  local step="$1"
  local condition="$2"
  if [ "${condition}" = "true" ]; then
    echo "  PASS"
    PASS_COUNT=$((PASS_COUNT + 1))
  else
    echo "  FAIL"
    FAIL_COUNT=$((FAIL_COUNT + 1))
  fi
}

# ============================================================
# Step 1: Discovery Endpoint
# ============================================================
echo "Step 1: Checking discovery endpoint..."

DISCOVERY_RESPONSE=$(curl -s -w "\n%{http_code}" \
  "${TENANT_BASE}/.well-known/openid-configuration")

DISCOVERY_HTTP=$(echo "${DISCOVERY_RESPONSE}" | tail -n1)
DISCOVERY_BODY=$(echo "${DISCOVERY_RESPONSE}" | sed '$d')

if [ "${DISCOVERY_HTTP}" = "200" ]; then
  DISCOVERY_ISSUER=$(echo "${DISCOVERY_BODY}" | jq -r '.issuer')

  echo "  HTTP 200 OK"
  echo "  Issuer: ${DISCOVERY_ISSUER}"

  EXPECTED_ISSUER="${TENANT_BASE}"
  if [ "${DISCOVERY_ISSUER}" = "${EXPECTED_ISSUER}" ]; then
    check_result "issuer" "true"
  else
    echo "  Expected issuer: ${EXPECTED_ISSUER}"
    echo "  Got: ${DISCOVERY_ISSUER}"
    check_result "issuer" "false"
  fi
else
  echo "  HTTP ${DISCOVERY_HTTP}"
  echo "  ${DISCOVERY_BODY}"
  check_result "discovery" "false"
fi
echo ""

# ============================================================
# Step 2: FAPI Compliance - mTLS
# ============================================================
echo "Step 2: Checking mTLS (certificate-bound access tokens)..."

MTLS_ENABLED=$(echo "${DISCOVERY_BODY}" | jq -r '.tls_client_certificate_bound_access_tokens')
echo "  tls_client_certificate_bound_access_tokens: ${MTLS_ENABLED}"

if [ "${MTLS_ENABLED}" = "true" ]; then
  check_result "mtls" "true"
else
  echo "  Expected: true"
  check_result "mtls" "false"
fi
echo ""

# ============================================================
# Step 3: FAPI Compliance - Signed Request Object
# ============================================================
echo "Step 3: Checking signed request object requirement..."

REQUIRE_SIGNED_REQ=$(echo "${DISCOVERY_BODY}" | jq -r '.require_signed_request_object')
echo "  require_signed_request_object: ${REQUIRE_SIGNED_REQ}"

if [ "${REQUIRE_SIGNED_REQ}" = "true" ]; then
  check_result "signed_request" "true"
else
  echo "  Expected: true"
  check_result "signed_request" "false"
fi

REQ_OBJ_ALGS=$(echo "${DISCOVERY_BODY}" | jq -r '.request_object_signing_alg_values_supported | join(", ")')
echo "  Request object signing algorithms: ${REQ_OBJ_ALGS}"
echo ""

# ============================================================
# Step 4: FAPI Compliance - PAR
# ============================================================
echo "Step 4: Checking Pushed Authorization Request (PAR)..."

PAR_ENDPOINT=$(echo "${DISCOVERY_BODY}" | jq -r '.pushed_authorization_request_endpoint')
echo "  PAR endpoint: ${PAR_ENDPOINT}"

if [ -n "${PAR_ENDPOINT}" ] && [ "${PAR_ENDPOINT}" != "null" ]; then
  check_result "par" "true"
else
  echo "  Expected: PAR endpoint to be configured"
  check_result "par" "false"
fi
echo ""

# ============================================================
# Step 5: FAPI Compliance - JARM
# ============================================================
echo "Step 5: Checking JWT Secured Authorization Response Mode (JARM)..."

RESPONSE_MODES=$(echo "${DISCOVERY_BODY}" | jq -r '.response_modes_supported | join(", ")')
echo "  Response modes: ${RESPONSE_MODES}"

HAS_JWT_MODE=$(echo "${DISCOVERY_BODY}" | jq '.response_modes_supported | contains(["jwt"])')
if [ "${HAS_JWT_MODE}" = "true" ]; then
  check_result "jarm" "true"
else
  echo "  Expected: jwt response mode"
  check_result "jarm" "false"
fi

AUTH_SIGNING_ALGS=$(echo "${DISCOVERY_BODY}" | jq -r '.authorization_signing_alg_values_supported // [] | join(", ")')
echo "  Authorization signing algorithms: ${AUTH_SIGNING_ALGS}"
echo ""

# ============================================================
# Step 6: Token Endpoint Auth Methods
# ============================================================
echo "Step 6: Checking token endpoint auth methods..."

AUTH_METHODS=$(echo "${DISCOVERY_BODY}" | jq -r '.token_endpoint_auth_methods_supported | join(", ")')
echo "  Supported methods: ${AUTH_METHODS}"

HAS_PKJ=$(echo "${DISCOVERY_BODY}" | jq '.token_endpoint_auth_methods_supported | contains(["private_key_jwt"])')
echo "  private_key_jwt: $([ "${HAS_PKJ}" = "true" ] && echo "supported" || echo "NOT supported")"
if [ "${HAS_PKJ}" = "true" ]; then
  check_result "private_key_jwt" "true"
else
  check_result "private_key_jwt" "false"
fi

HAS_TLS=$(echo "${DISCOVERY_BODY}" | jq '.token_endpoint_auth_methods_supported | contains(["tls_client_auth"])')
echo "  tls_client_auth: $([ "${HAS_TLS}" = "true" ] && echo "supported" || echo "NOT supported")"
if [ "${HAS_TLS}" = "true" ]; then
  check_result "tls_client_auth" "true"
else
  check_result "tls_client_auth" "false"
fi
echo ""

# ============================================================
# Step 7: CIBA Configuration
# ============================================================
echo "Step 7: Checking CIBA configuration..."

CIBA_ENDPOINT=$(echo "${DISCOVERY_BODY}" | jq -r '.backchannel_authentication_endpoint')
echo "  CIBA endpoint: ${CIBA_ENDPOINT}"

if [ -n "${CIBA_ENDPOINT}" ] && [ "${CIBA_ENDPOINT}" != "null" ]; then
  check_result "ciba_endpoint" "true"
else
  echo "  Expected: CIBA endpoint to be configured"
  check_result "ciba_endpoint" "false"
fi

CIBA_MODES=$(echo "${DISCOVERY_BODY}" | jq -r '.backchannel_token_delivery_modes_supported // [] | join(", ")')
echo "  Delivery modes: ${CIBA_MODES}"

HAS_CIBA_GRANT=$(echo "${DISCOVERY_BODY}" | jq '.grant_types_supported | contains(["urn:openid:params:grant-type:ciba"])')
echo "  CIBA grant type: $([ "${HAS_CIBA_GRANT}" = "true" ] && echo "supported" || echo "NOT supported")"
if [ "${HAS_CIBA_GRANT}" = "true" ]; then
  check_result "ciba_grant" "true"
else
  check_result "ciba_grant" "false"
fi
echo ""

# ============================================================
# Step 8: FAPI Scopes
# ============================================================
echo "Step 8: Checking FAPI scopes..."

SCOPES=$(echo "${DISCOVERY_BODY}" | jq -r '.scopes_supported | join(", ")')
echo "  Supported scopes: ${SCOPES}"

for SCOPE in account transfers read write; do
  HAS_SCOPE=$(echo "${DISCOVERY_BODY}" | jq --arg s "${SCOPE}" '.scopes_supported | contains([$s])')
  echo "  ${SCOPE}: $([ "${HAS_SCOPE}" = "true" ] && echo "supported" || echo "NOT supported")"
  if [ "${HAS_SCOPE}" = "true" ]; then
    check_result "scope_${SCOPE}" "true"
  else
    check_result "scope_${SCOPE}" "false"
  fi
done
echo ""

# ============================================================
# Step 9: mTLS Endpoint Aliases
# ============================================================
echo "Step 9: Checking mTLS endpoint aliases..."

MTLS_TOKEN_EP=$(echo "${DISCOVERY_BODY}" | jq -r '.mtls_endpoint_aliases.token_endpoint')
echo "  mTLS token endpoint: ${MTLS_TOKEN_EP}"

if [ -n "${MTLS_TOKEN_EP}" ] && [ "${MTLS_TOKEN_EP}" != "null" ]; then
  check_result "mtls_aliases" "true"
else
  echo "  Expected: mTLS endpoint aliases to be configured"
  check_result "mtls_aliases" "false"
fi
echo ""

# ============================================================
# Summary
# ============================================================
TOTAL=$((PASS_COUNT + FAIL_COUNT))

echo "=========================================="
echo "Verification Results"
echo "=========================================="
echo ""
echo "  Passed: ${PASS_COUNT} / ${TOTAL}"
echo "  Failed: ${FAIL_COUNT} / ${TOTAL}"
echo ""

if [ "${FAIL_COUNT}" -eq 0 ]; then
  echo "All checks passed! FAPI configuration is correct."
  exit 0
else
  echo "Some checks failed. Review the output above for details."
  exit 1
fi
