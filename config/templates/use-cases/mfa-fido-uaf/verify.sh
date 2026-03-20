#!/bin/bash
set -e

# MFA (Password + FIDO-UAF) - Verification Script
#
# Verifies that the tenant created by setup.sh works correctly by performing:
#   1. Discovery endpoint check
#   2. User registration via initial-registration
#   3. authentication-status API check
#   4. Authorization Code Flow (authorization -> token exchange)
#   5. UserInfo endpoint verification
#   6. Token Refresh
#
# Note: FIDO-UAF device authentication requires device-side interaction.
# This script verifies the initial-registration path (password fallback) to confirm
# tenant configuration is correct.
#
# Usage:
#   ./verify.sh
#   ./verify.sh --org my-organization

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../../.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"

ORGANIZATION_NAME="mfa-fido-uaf"
while [ $# -gt 0 ]; do
  case "$1" in
    --org) ORGANIZATION_NAME="$2"; shift 2 ;;
    *) echo "Unknown option: $1"; exit 1 ;;
  esac
done

echo "=========================================="
echo "MFA (Password + FIDO-UAF) Verification"
echo "=========================================="
echo ""

if [ ! -f "${ENV_FILE}" ]; then
  echo "Error: .env file not found at ${ENV_FILE}"
  exit 1
fi

set -a
source "${ENV_FILE}"
set +a

: "${AUTHORIZATION_SERVER_URL:?AUTHORIZATION_SERVER_URL is required in .env}"

CONFIG_DIR="${PROJECT_ROOT}/config/generated/${ORGANIZATION_NAME}"

if [ ! -d "${CONFIG_DIR}" ]; then
  echo "Error: Generated config not found at ${CONFIG_DIR}"
  echo "Run setup.sh first."
  exit 1
fi

PUBLIC_TENANT_ID=$(jq -r '.tenant.id' "${CONFIG_DIR}/public-tenant.json")
CLIENT_ID=$(jq -r '.client_id' "${CONFIG_DIR}/public-client.json")
CLIENT_SECRET=$(jq -r '.client_secret' "${CONFIG_DIR}/public-client.json")
REDIRECT_URI=$(jq -r '.redirect_uris[0]' "${CONFIG_DIR}/public-client.json")

echo "Server:       ${AUTHORIZATION_SERVER_URL}"
echo "Organization: ${ORGANIZATION_NAME}"
echo "Tenant ID:    ${PUBLIC_TENANT_ID}"
echo "Client ID:    ${CLIENT_ID}"
echo ""

TENANT_BASE="${AUTHORIZATION_SERVER_URL}/${PUBLIC_TENANT_ID}"

extract_param() {
  local url="$1"
  local param="$2"
  echo "${url}" | sed -n "s/.*[?&]${param}=\([^&#]*\).*/\1/p"
}

PASS_COUNT=0
FAIL_COUNT=0
COOKIE_JAR=$(mktemp)
trap "rm -f ${COOKIE_JAR}" EXIT

check_result() {
  local condition="$1"
  if [ "${condition}" = "true" ]; then
    echo "  PASS"
    PASS_COUNT=$((PASS_COUNT + 1))
  else
    echo "  FAIL"
    FAIL_COUNT=$((FAIL_COUNT + 1))
  fi
}

# Step 1: Discovery
echo "Step 1: Checking discovery endpoint..."

DISCOVERY_RESPONSE=$(curl -s -w "\n%{http_code}" "${TENANT_BASE}/.well-known/openid-configuration")
DISCOVERY_HTTP=$(echo "${DISCOVERY_RESPONSE}" | tail -n1)
DISCOVERY_BODY=$(echo "${DISCOVERY_RESPONSE}" | sed '$d')

if [ "${DISCOVERY_HTTP}" = "200" ]; then
  DISCOVERY_ISSUER=$(echo "${DISCOVERY_BODY}" | jq -r '.issuer')
  echo "  HTTP 200 OK - Issuer: ${DISCOVERY_ISSUER}"
  [ "${DISCOVERY_ISSUER}" = "${TENANT_BASE}" ] && check_result "true" || check_result "false"
else
  echo "  HTTP ${DISCOVERY_HTTP}"
  check_result "false"
fi
echo ""

# Step 2: Authorization Request
echo "Step 2: Starting authorization request..."

STATE="verify-state-$(date +%s)"

AUTH_RESPONSE=$(curl -s -c "${COOKIE_JAR}" -o /dev/null \
  -w "%{http_code}|%{redirect_url}" \
  "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=$(python3 -c "import urllib.parse; print(urllib.parse.quote('${REDIRECT_URI}', safe=''))")&scope=openid+profile+email&state=${STATE}")

AUTH_HTTP=$(echo "${AUTH_RESPONSE}" | cut -d'|' -f1)
AUTH_REDIRECT_URL=$(echo "${AUTH_RESPONSE}" | cut -d'|' -f2)

if [ "${AUTH_HTTP}" = "302" ]; then
  AUTHORIZATION_ID=$(extract_param "${AUTH_REDIRECT_URL}" "id")
  if [ -n "${AUTHORIZATION_ID}" ]; then
    echo "  HTTP 302 - Authorization ID: ${AUTHORIZATION_ID}"
    check_result "true"
  else
    echo "  No authorization ID found"
    check_result "false"
  fi
else
  echo "  Expected 302, got ${AUTH_HTTP}"
  check_result "false"
fi
echo ""

# Step 3: Register Test User
echo "Step 3: Registering test user..."

TEST_EMAIL="verify-$(date +%s)@example.com"
TEST_PASSWORD="VerifyPass123"

REGISTRATION_RESPONSE=$(curl -s -w "\n%{http_code}" \
  -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/initial-registration" \
  -H "Content-Type: application/json" \
  -d "{\"email\": \"${TEST_EMAIL}\", \"password\": \"${TEST_PASSWORD}\", \"name\": \"Verify User\"}")

REG_HTTP=$(echo "${REGISTRATION_RESPONSE}" | tail -n1)

if [ "${REG_HTTP}" = "200" ] || [ "${REG_HTTP}" = "201" ]; then
  echo "  HTTP ${REG_HTTP} OK - User: ${TEST_EMAIL}"
  check_result "true"
else
  echo "  HTTP ${REG_HTTP}"
  check_result "false"
fi
echo ""

# Step 4: Check authentication-status API
echo "Step 4: Checking authentication-status API..."

STATUS_RESPONSE=$(curl -s -w "\n%{http_code}" \
  -b "${COOKIE_JAR}" \
  "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/authentication-status")

STATUS_HTTP=$(echo "${STATUS_RESPONSE}" | tail -n1)
STATUS_BODY=$(echo "${STATUS_RESPONSE}" | sed '$d')

if [ "${STATUS_HTTP}" = "200" ]; then
  AUTH_STATUS=$(echo "${STATUS_BODY}" | jq -r '.status')
  echo "  HTTP 200 OK - Status: ${AUTH_STATUS}"
  check_result "true"
else
  echo "  HTTP ${STATUS_HTTP}"
  check_result "false"
fi
echo ""

# Step 5: Authorize
echo "Step 5: Granting authorization..."

AUTHORIZE_RESPONSE=$(curl -s -w "\n%{http_code}" \
  -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/authorize" \
  -H "Content-Type: application/json" -d '{}')

AUTHZ_HTTP=$(echo "${AUTHORIZE_RESPONSE}" | tail -n1)
AUTHZ_BODY=$(echo "${AUTHORIZE_RESPONSE}" | sed '$d')

if [ "${AUTHZ_HTTP}" = "200" ]; then
  AUTHZ_REDIRECT_URI=$(echo "${AUTHZ_BODY}" | jq -r '.redirect_uri')
  AUTHORIZATION_CODE=$(extract_param "${AUTHZ_REDIRECT_URI}" "code")

  if [ -n "${AUTHORIZATION_CODE}" ]; then
    echo "  HTTP 200 OK - Code: ${AUTHORIZATION_CODE:0:20}..."
    check_result "true"
  else
    echo "  No authorization code found"
    check_result "false"
  fi
else
  echo "  HTTP ${AUTHZ_HTTP}"
  echo "  ${AUTHZ_BODY}" | jq '.' 2>/dev/null || echo "  ${AUTHZ_BODY}"
  check_result "false"
fi
echo ""

# Step 6: Token Exchange
echo "Step 6: Exchanging code for tokens..."

TOKEN_RESPONSE=$(curl -s -w "\n%{http_code}" \
  -X POST "${TENANT_BASE}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=authorization_code" \
  --data-urlencode "code=${AUTHORIZATION_CODE}" \
  --data-urlencode "redirect_uri=${REDIRECT_URI}" \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode "client_secret=${CLIENT_SECRET}")

TOKEN_HTTP=$(echo "${TOKEN_RESPONSE}" | tail -n1)
TOKEN_BODY=$(echo "${TOKEN_RESPONSE}" | sed '$d')

if [ "${TOKEN_HTTP}" = "200" ]; then
  ACCESS_TOKEN=$(echo "${TOKEN_BODY}" | jq -r '.access_token')
  REFRESH_TOKEN=$(echo "${TOKEN_BODY}" | jq -r '.refresh_token')
  echo "  HTTP 200 OK - Access Token: ${ACCESS_TOKEN:0:20}..."
  [ -n "${ACCESS_TOKEN}" ] && [ "${ACCESS_TOKEN}" != "null" ] && check_result "true" || check_result "false"
else
  echo "  HTTP ${TOKEN_HTTP}"
  check_result "false"
fi
echo ""

# Step 7: UserInfo
echo "Step 7: Verifying UserInfo endpoint..."

USERINFO_RESPONSE=$(curl -s -w "\n%{http_code}" \
  "${TENANT_BASE}/v1/userinfo" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}")

USERINFO_HTTP=$(echo "${USERINFO_RESPONSE}" | tail -n1)
USERINFO_BODY=$(echo "${USERINFO_RESPONSE}" | sed '$d')

if [ "${USERINFO_HTTP}" = "200" ]; then
  USERINFO_SUB=$(echo "${USERINFO_BODY}" | jq -r '.sub')
  echo "  HTTP 200 OK - sub: ${USERINFO_SUB}"
  check_result "true"
else
  echo "  HTTP ${USERINFO_HTTP}"
  check_result "false"
fi
echo ""

# Step 8: Refresh Token
if [ -n "${REFRESH_TOKEN}" ] && [ "${REFRESH_TOKEN}" != "null" ]; then
  echo "Step 8: Verifying refresh token..."

  REFRESH_RESPONSE=$(curl -s -w "\n%{http_code}" \
    -X POST "${TENANT_BASE}/v1/tokens" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    --data-urlencode "grant_type=refresh_token" \
    --data-urlencode "refresh_token=${REFRESH_TOKEN}" \
    --data-urlencode "client_id=${CLIENT_ID}" \
    --data-urlencode "client_secret=${CLIENT_SECRET}")

  REFRESH_HTTP=$(echo "${REFRESH_RESPONSE}" | tail -n1)
  if [ "${REFRESH_HTTP}" = "200" ]; then
    echo "  HTTP 200 OK"
    check_result "true"
  else
    echo "  HTTP ${REFRESH_HTTP}"
    check_result "false"
  fi
  echo ""
else
  echo "Step 8: Skipped (no refresh token)"
  echo ""
fi

# Summary
TOTAL=$((PASS_COUNT + FAIL_COUNT))
echo "=========================================="
echo "Verification Results: ${PASS_COUNT}/${TOTAL} passed"
echo "=========================================="
[ "${FAIL_COUNT}" -eq 0 ] && exit 0 || exit 1
