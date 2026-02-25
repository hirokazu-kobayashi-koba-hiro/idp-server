#!/bin/bash
set -e

# Third Party Integration - Verification Script
#
# Verifies that the tenant created by setup.sh works correctly by performing:
#   1. Discovery endpoint check
#   2. M2M client_credentials grant
#   3. Token introspection
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
ORGANIZATION_NAME="third-party"
while [ $# -gt 0 ]; do
  case "$1" in
    --org) ORGANIZATION_NAME="$2"; shift 2 ;;
    *) echo "Unknown option: $1"; exit 1 ;;
  esac
done

echo "=========================================="
echo "Third Party Integration Verification"
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

PUBLIC_TENANT_ID=$(jq -r '.tenant.id' "${CONFIG_DIR}/public-tenant.json")
WEB_CLIENT_ID=$(jq -r '.client_id' "${CONFIG_DIR}/web-client.json")
WEB_CLIENT_SECRET=$(jq -r '.client_secret' "${CONFIG_DIR}/web-client.json")
WEB_REDIRECT_URI=$(jq -r '.redirect_uris[0]' "${CONFIG_DIR}/web-client.json")
M2M_CLIENT_ID=$(jq -r '.client_id' "${CONFIG_DIR}/m2m-client.json")
M2M_CLIENT_SECRET=$(jq -r '.client_secret' "${CONFIG_DIR}/m2m-client.json")
M2M_SCOPE=$(jq -r '.scope' "${CONFIG_DIR}/m2m-client.json")

echo "Server:       ${AUTHORIZATION_SERVER_URL}"
echo "Organization: ${ORGANIZATION_NAME}"
echo "Tenant ID:    ${PUBLIC_TENANT_ID}"
echo "Web Client:   ${WEB_CLIENT_ID}"
echo "M2M Client:   ${M2M_CLIENT_ID}"
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
  DISCOVERY_GRANT_TYPES=$(echo "${DISCOVERY_BODY}" | jq -r '.grant_types_supported | join(", ")')
  DISCOVERY_SCOPES=$(echo "${DISCOVERY_BODY}" | jq -r '.scopes_supported | join(", ")')

  echo "  HTTP 200 OK"
  echo "  Issuer: ${DISCOVERY_ISSUER}"
  echo "  Grant Types: ${DISCOVERY_GRANT_TYPES}"
  echo "  Scopes: ${DISCOVERY_SCOPES}"

  EXPECTED_ISSUER="${TENANT_BASE}"
  if [ "${DISCOVERY_ISSUER}" = "${EXPECTED_ISSUER}" ]; then
    check_result "issuer" "true"
  else
    echo "  Expected issuer: ${EXPECTED_ISSUER}"
    echo "  Got: ${DISCOVERY_ISSUER}"
    check_result "issuer" "false"
  fi

  # Verify client_credentials is in grant_types_supported
  HAS_CC=$(echo "${DISCOVERY_BODY}" | jq '[.grant_types_supported[] | select(. == "client_credentials")] | length')
  if [ "${HAS_CC}" -gt 0 ]; then
    echo "  client_credentials grant type: present"
    check_result "client_credentials_grant_type" "true"
  else
    echo "  client_credentials grant type: missing"
    check_result "client_credentials_grant_type" "false"
  fi

  # Verify custom scopes
  HAS_API_READ=$(echo "${DISCOVERY_BODY}" | jq '[.scopes_supported[] | select(. == "api:read")] | length')
  HAS_API_WRITE=$(echo "${DISCOVERY_BODY}" | jq '[.scopes_supported[] | select(. == "api:write")] | length')
  if [ "${HAS_API_READ}" -gt 0 ] && [ "${HAS_API_WRITE}" -gt 0 ]; then
    echo "  Custom API scopes: present (api:read, api:write)"
    check_result "custom_scopes" "true"
  else
    echo "  Custom API scopes: missing"
    check_result "custom_scopes" "false"
  fi
else
  echo "  HTTP ${DISCOVERY_HTTP}"
  echo "  ${DISCOVERY_BODY}"
  check_result "discovery" "false"
fi
echo ""

# ============================================================
# Step 2: M2M Client Credentials Grant
# ============================================================
echo "Step 2: Testing M2M client_credentials grant..."

M2M_TOKEN_RESPONSE=$(curl -s -w "\n%{http_code}" \
  -X POST "${TENANT_BASE}/v1/tokens" \
  -u "${M2M_CLIENT_ID}:${M2M_CLIENT_SECRET}" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=client_credentials" \
  --data-urlencode "scope=${M2M_SCOPE}")

M2M_TOKEN_HTTP=$(echo "${M2M_TOKEN_RESPONSE}" | tail -n1)
M2M_TOKEN_BODY=$(echo "${M2M_TOKEN_RESPONSE}" | sed '$d')

if [ "${M2M_TOKEN_HTTP}" = "200" ]; then
  M2M_ACCESS_TOKEN=$(echo "${M2M_TOKEN_BODY}" | jq -r '.access_token')
  M2M_TOKEN_TYPE=$(echo "${M2M_TOKEN_BODY}" | jq -r '.token_type')
  M2M_EXPIRES_IN=$(echo "${M2M_TOKEN_BODY}" | jq -r '.expires_in')

  echo "  HTTP 200 OK"
  echo "  Token Type: ${M2M_TOKEN_TYPE}"
  echo "  Expires In: ${M2M_EXPIRES_IN}s"
  echo "  Access Token: $([ -n "${M2M_ACCESS_TOKEN}" ] && [ "${M2M_ACCESS_TOKEN}" != "null" ] && echo "${M2M_ACCESS_TOKEN:0:20}..." || echo "missing")"

  if [ -n "${M2M_ACCESS_TOKEN}" ] && [ "${M2M_ACCESS_TOKEN}" != "null" ]; then
    check_result "m2m_token" "true"
  else
    check_result "m2m_token" "false"
  fi
else
  echo "  HTTP ${M2M_TOKEN_HTTP}"
  echo "  ${M2M_TOKEN_BODY}" | jq '.' 2>/dev/null || echo "  ${M2M_TOKEN_BODY}"
  check_result "m2m_token" "false"
fi
echo ""

# ============================================================
# Step 3: Token Introspection (M2M token)
# ============================================================
if [ -n "${M2M_ACCESS_TOKEN}" ] && [ "${M2M_ACCESS_TOKEN}" != "null" ]; then
  echo "Step 3: Verifying M2M token via introspection..."

  INTROSPECT_RESPONSE=$(curl -s -w "\n%{http_code}" \
    -X POST "${TENANT_BASE}/v1/tokens/introspection" \
    -u "${M2M_CLIENT_ID}:${M2M_CLIENT_SECRET}" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    --data-urlencode "token=${M2M_ACCESS_TOKEN}")

  INTROSPECT_HTTP=$(echo "${INTROSPECT_RESPONSE}" | tail -n1)
  INTROSPECT_BODY=$(echo "${INTROSPECT_RESPONSE}" | sed '$d')

  if [ "${INTROSPECT_HTTP}" = "200" ]; then
    INTROSPECT_ACTIVE=$(echo "${INTROSPECT_BODY}" | jq -r '.active')
    INTROSPECT_SCOPE=$(echo "${INTROSPECT_BODY}" | jq -r '.scope // "N/A"')
    INTROSPECT_CLIENT=$(echo "${INTROSPECT_BODY}" | jq -r '.client_id // "N/A"')

    echo "  HTTP 200 OK"
    echo "  Active: ${INTROSPECT_ACTIVE}"
    echo "  Scope:  ${INTROSPECT_SCOPE}"
    echo "  Client: ${INTROSPECT_CLIENT}"

    if [ "${INTROSPECT_ACTIVE}" = "true" ]; then
      check_result "introspection" "true"
    else
      echo "  Token is not active"
      check_result "introspection" "false"
    fi
  else
    echo "  HTTP ${INTROSPECT_HTTP}"
    echo "  ${INTROSPECT_BODY}" | jq '.' 2>/dev/null || echo "  ${INTROSPECT_BODY}"
    check_result "introspection" "false"
  fi
  echo ""
else
  echo "Step 3: Skipped (no M2M access token)"
  echo ""
fi

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
  echo "All checks passed! The tenant is working correctly."
  exit 0
else
  echo "Some checks failed. Review the output above for details."
  exit 1
fi
