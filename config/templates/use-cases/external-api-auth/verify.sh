#!/bin/bash
set -e

# External API Auth - Verification Script
#
# Verifies that the tenant created by setup.sh works correctly:
#   1. Mock server connectivity
#   2. Discovery endpoint
#   3. User registration (initial-registration)
#   4. Authorization request
#   5. External API authentication (password_verify interaction)
#   6. Authorization (consent grant)
#   7. Token exchange + ID Token amr verification
#   8. UserInfo endpoint
#   9. Token refresh
#
# Usage:
#   ./verify.sh
#   ./verify.sh --org my-organization

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../../.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"

ORGANIZATION_NAME="external-api-auth"
while [ $# -gt 0 ]; do
  case "$1" in
    --org) ORGANIZATION_NAME="$2"; shift 2 ;;
    *) echo "Unknown option: $1"; exit 1 ;;
  esac
done

echo "=========================================="
echo "External API Auth Verification"
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
CLIENT_ID=$(jq -r '.client_id' "${CONFIG_DIR}/public-client.json")
CLIENT_SECRET=$(jq -r '.client_secret' "${CONFIG_DIR}/public-client.json")
REDIRECT_URI=$(jq -r '.redirect_uris[0]' "${CONFIG_DIR}/public-client.json")

EXTERNAL_API_URL=$(jq -r '.interactions.password_verify.execution.http_request.url' "${CONFIG_DIR}/authentication-config-external-api.json")

echo "Server:           ${AUTHORIZATION_SERVER_URL}"
echo "Organization:     ${ORGANIZATION_NAME}"
echo "Tenant ID:        ${PUBLIC_TENANT_ID}"
echo "Client ID:        ${CLIENT_ID}"
echo "External API:     ${EXTERNAL_API_URL}"
echo ""

TENANT_BASE="${AUTHORIZATION_SERVER_URL}/${PUBLIC_TENANT_ID}"

# --- Helpers ---
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
# Step 1: Mock Server Connectivity
# ============================================================
echo "Step 1: Checking mock server..."

EXTERNAL_API_URL_LOCAL=$(echo "${EXTERNAL_API_URL}" | sed 's|host\.docker\.internal|localhost|')

EXT_RESPONSE=$(curl -s -w "\n%{http_code}" --connect-timeout 5 \
  -X POST "${EXTERNAL_API_URL_LOCAL}" \
  -H "Content-Type: application/json" \
  -d '{"username": "test@example.com", "password": "test"}')

EXT_HTTP=$(echo "${EXT_RESPONSE}" | tail -n1)

if [ -n "${EXT_HTTP}" ] && [ "${EXT_HTTP}" != "000" ]; then
  echo "  Mock server reachable (HTTP ${EXT_HTTP})"
  check_result "mock_server" "true"
else
  echo "  FAIL: Cannot connect to ${EXTERNAL_API_URL_LOCAL}"
  echo "  Ensure Mockoon is running (docker compose up mockoon)"
  check_result "mock_server" "false"
fi
echo ""

# ============================================================
# Step 2: Discovery Endpoint
# ============================================================
echo "Step 2: Checking discovery endpoint..."

DISCOVERY_RESPONSE=$(curl -s -w "\n%{http_code}" \
  "${TENANT_BASE}/.well-known/openid-configuration")

DISCOVERY_HTTP=$(echo "${DISCOVERY_RESPONSE}" | tail -n1)
DISCOVERY_BODY=$(echo "${DISCOVERY_RESPONSE}" | sed '$d')

if [ "${DISCOVERY_HTTP}" = "200" ]; then
  DISCOVERY_ISSUER=$(echo "${DISCOVERY_BODY}" | jq -r '.issuer')
  echo "  HTTP 200 OK"
  echo "  Issuer: ${DISCOVERY_ISSUER}"
  check_result "discovery" "true"
else
  echo "  HTTP ${DISCOVERY_HTTP}"
  check_result "discovery" "false"
fi
echo ""

# ============================================================
# Step 3: Register Test User
# ============================================================
echo "Step 3: Registering test user via initial-registration..."

TEST_EMAIL="${VERIFY_USERNAME:-extapi-user@example.com}"
TEST_PASSWORD="${VERIFY_PASSWORD:-ExternalPass123!}"
TEST_NAME="External API Test User"

STATE="verify-reg-$(date +%s)"

REG_AUTH_RESPONSE=$(curl -s -c "${COOKIE_JAR}" -o /dev/null \
  -w "%{http_code}|%{redirect_url}" \
  "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=$(python3 -c "import urllib.parse; print(urllib.parse.quote('${REDIRECT_URI}', safe=''))")&scope=openid+profile+email&state=${STATE}")

REG_AUTH_HTTP=$(echo "${REG_AUTH_RESPONSE}" | cut -d'|' -f1)
REG_REDIRECT_URL=$(echo "${REG_AUTH_RESPONSE}" | cut -d'|' -f2)

if [ "${REG_AUTH_HTTP}" = "302" ]; then
  REG_AUTH_ID=$(extract_param "${REG_REDIRECT_URL}" "id")

  REG_RESPONSE=$(curl -s -w "\n%{http_code}" \
    -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
    -X POST "${TENANT_BASE}/v1/authorizations/${REG_AUTH_ID}/initial-registration" \
    -H "Content-Type: application/json" \
    -d "{\"email\": \"${TEST_EMAIL}\", \"password\": \"${TEST_PASSWORD}\", \"name\": \"${TEST_NAME}\"}")

  REG_HTTP=$(echo "${REG_RESPONSE}" | tail -n1)
  REG_BODY=$(echo "${REG_RESPONSE}" | sed '$d')

  if [ "${REG_HTTP}" = "200" ]; then
    TEST_USER_SUB=$(echo "${REG_BODY}" | jq -r '.user.sub')
    echo "  User registered: ${TEST_EMAIL}"
    echo "  Sub: ${TEST_USER_SUB}"

    # Complete registration flow
    curl -s -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
      -X POST "${TENANT_BASE}/v1/authorizations/${REG_AUTH_ID}/authorize" \
      -H "Content-Type: application/json" -d '{}' > /dev/null

    check_result "registration" "true"
  elif [ "${REG_HTTP}" = "400" ]; then
    REG_ERROR=$(echo "${REG_BODY}" | jq -r '.error // empty')
    if echo "${REG_BODY}" | grep -q "conflict"; then
      echo "  User already exists: ${TEST_EMAIL} (OK, skipping registration)"
      check_result "registration" "true"
    else
      echo "  HTTP ${REG_HTTP}: ${REG_ERROR}"
      check_result "registration" "false"
    fi
  else
    echo "  HTTP ${REG_HTTP}"
    echo "  ${REG_BODY}" | jq '.' 2>/dev/null || echo "  ${REG_BODY}"
    check_result "registration" "false"
  fi
else
  echo "  Auth request failed (HTTP ${REG_AUTH_HTTP})"
  check_result "registration" "false"
fi
echo ""

# ============================================================
# Step 4: Start Authorization Request
# ============================================================
echo "Step 4: Starting authorization request..."

STATE="verify-state-$(date +%s)"

AUTH_RESPONSE=$(curl -s -c "${COOKIE_JAR}" -o /dev/null \
  -w "%{http_code}|%{redirect_url}" \
  "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=$(python3 -c "import urllib.parse; print(urllib.parse.quote('${REDIRECT_URI}', safe=''))")&scope=openid+profile+email&state=${STATE}&prompt=login")

AUTH_HTTP=$(echo "${AUTH_RESPONSE}" | cut -d'|' -f1)
AUTH_REDIRECT_URL=$(echo "${AUTH_RESPONSE}" | cut -d'|' -f2)

if [ "${AUTH_HTTP}" = "302" ]; then
  AUTHORIZATION_ID=$(extract_param "${AUTH_REDIRECT_URL}" "id")

  if [ -n "${AUTHORIZATION_ID}" ]; then
    echo "  Authorization ID: ${AUTHORIZATION_ID}"
    check_result "authorization" "true"
  else
    echo "  No authorization ID found"
    check_result "authorization" "false"
  fi
else
  echo "  Expected HTTP 302, got ${AUTH_HTTP}"
  check_result "authorization" "false"
fi
echo ""

# ============================================================
# Step 5: External API Authentication (password_verify interaction)
# ============================================================
echo "Step 5: Authenticating via external API (interaction=password_verify)..."

EXT_AUTH_RESPONSE=$(curl -s -w "\n%{http_code}" \
  -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/external-api-authentication" \
  -H "Content-Type: application/json" \
  -d "{\"interaction\": \"password_verify\", \"username\": \"${TEST_EMAIL}\", \"password\": \"${TEST_PASSWORD}\"}")

EXT_AUTH_HTTP=$(echo "${EXT_AUTH_RESPONSE}" | tail -n1)
EXT_AUTH_BODY=$(echo "${EXT_AUTH_RESPONSE}" | sed '$d')

if [ "${EXT_AUTH_HTTP}" = "200" ]; then
  echo "  HTTP 200 OK"
  echo "  Interaction: password_verify"
  echo "  Response: $(echo "${EXT_AUTH_BODY}" | jq -c '{interaction, email, user_id}' 2>/dev/null)"
  check_result "external_api_auth" "true"
else
  echo "  HTTP ${EXT_AUTH_HTTP}"
  echo "  ${EXT_AUTH_BODY}" | jq '.' 2>/dev/null || echo "  ${EXT_AUTH_BODY}"
  check_result "external_api_auth" "false"
fi
echo ""

# ============================================================
# Step 6: Authorize (consent grant)
# ============================================================
echo "Step 6: Granting authorization..."

AUTHORIZE_RESPONSE=$(curl -s -w "\n%{http_code}" \
  -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/authorize" \
  -H "Content-Type: application/json" \
  -d '{}')

AUTHZ_HTTP=$(echo "${AUTHORIZE_RESPONSE}" | tail -n1)
AUTHZ_BODY=$(echo "${AUTHORIZE_RESPONSE}" | sed '$d')

if [ "${AUTHZ_HTTP}" = "200" ]; then
  AUTHZ_REDIRECT_URI=$(echo "${AUTHZ_BODY}" | jq -r '.redirect_uri')
  AUTHORIZATION_CODE=$(extract_param "${AUTHZ_REDIRECT_URI}" "code")

  if [ -n "${AUTHORIZATION_CODE}" ]; then
    echo "  Code: ${AUTHORIZATION_CODE:0:20}..."
    check_result "authorize" "true"
  else
    echo "  No authorization code found"
    check_result "authorize" "false"
  fi
else
  echo "  HTTP ${AUTHZ_HTTP}"
  echo "  ${AUTHZ_BODY}" | jq '.' 2>/dev/null || echo "  ${AUTHZ_BODY}"
  check_result "authorize" "false"
fi
echo ""

# ============================================================
# Step 7: Exchange Code for Tokens
# ============================================================
echo "Step 7: Exchanging code for tokens..."

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
  ID_TOKEN=$(echo "${TOKEN_BODY}" | jq -r '.id_token')
  REFRESH_TOKEN=$(echo "${TOKEN_BODY}" | jq -r '.refresh_token')
  TOKEN_TYPE=$(echo "${TOKEN_BODY}" | jq -r '.token_type')

  echo "  HTTP 200 OK"
  echo "  Token Type: ${TOKEN_TYPE}"
  echo "  Access Token:  ${ACCESS_TOKEN:0:20}..."
  echo "  ID Token:      ${ID_TOKEN:0:20}..."

  # Decode ID Token payload and check amr (add padding for base64url)
  ID_TOKEN_B64=$(echo "${ID_TOKEN}" | cut -d'.' -f2 | tr '_-' '/+')
  PAD=$((4 - ${#ID_TOKEN_B64} % 4))
  [ "${PAD}" -lt 4 ] && ID_TOKEN_B64="${ID_TOKEN_B64}$(printf '%0.s=' $(seq 1 ${PAD}))"
  ID_TOKEN_PAYLOAD=$(echo "${ID_TOKEN_B64}" | base64 -d 2>/dev/null || echo "${ID_TOKEN_B64}" | base64 -D 2>/dev/null)
  AMR=$(echo "${ID_TOKEN_PAYLOAD}" | jq -r '.amr // empty' 2>/dev/null)

  if [ -n "${AMR}" ]; then
    echo "  ID Token amr: ${AMR}"
    # Check if external-api is in amr
    HAS_EXT_API=$(echo "${ID_TOKEN_PAYLOAD}" | jq '.amr | index("external-api") != null' 2>/dev/null)
    if [ "${HAS_EXT_API}" = "true" ]; then
      echo "  amr contains 'external-api': yes"
    else
      echo "  amr does NOT contain 'external-api'"
    fi
  fi

  check_result "token_exchange" "true"
else
  echo "  HTTP ${TOKEN_HTTP}"
  echo "  ${TOKEN_BODY}" | jq '.' 2>/dev/null || echo "  ${TOKEN_BODY}"
  check_result "token_exchange" "false"
fi
echo ""

# ============================================================
# Step 8: Verify UserInfo
# ============================================================
echo "Step 8: Verifying UserInfo endpoint..."

USERINFO_RESPONSE=$(curl -s -w "\n%{http_code}" \
  -X GET "${TENANT_BASE}/v1/userinfo" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}")

USERINFO_HTTP=$(echo "${USERINFO_RESPONSE}" | tail -n1)
USERINFO_BODY=$(echo "${USERINFO_RESPONSE}" | sed '$d')

if [ "${USERINFO_HTTP}" = "200" ]; then
  USERINFO_SUB=$(echo "${USERINFO_BODY}" | jq -r '.sub')
  USERINFO_EMAIL=$(echo "${USERINFO_BODY}" | jq -r '.email')
  USERINFO_NAME=$(echo "${USERINFO_BODY}" | jq -r '.name')

  echo "  HTTP 200 OK"
  echo "  sub:   ${USERINFO_SUB}"
  echo "  email: ${USERINFO_EMAIL:-null}"
  echo "  name:  ${USERINFO_NAME:-null}"
  check_result "userinfo" "true"
else
  echo "  HTTP ${USERINFO_HTTP}"
  check_result "userinfo" "false"
fi
echo ""

# ============================================================
# Step 9: Verify Refresh Token
# ============================================================
if [ -n "${REFRESH_TOKEN}" ] && [ "${REFRESH_TOKEN}" != "null" ]; then
  echo "Step 9: Verifying refresh token..."

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
    check_result "refresh_token" "true"
  else
    echo "  HTTP ${REFRESH_HTTP}"
    check_result "refresh_token" "false"
  fi
  echo ""
else
  echo "Step 9: Skipped (no refresh token)"
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
  echo "All checks passed!"
  exit 0
else
  echo "Some checks failed. Review the output above."
  exit 1
fi
