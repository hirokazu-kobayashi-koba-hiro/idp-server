#!/bin/bash
set -e

# Login (Password Only) - Verification Script
#
# Verifies that the tenant created by setup.sh works correctly by performing:
#   1. Discovery endpoint check
#   2. User registration via initial-registration
#   3. Authorization Code Flow (authorization → token exchange)
#   4. UserInfo endpoint verification
#   5. Token Refresh
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
ORGANIZATION_NAME="login-password-only"
while [ $# -gt 0 ]; do
  case "$1" in
    --org) ORGANIZATION_NAME="$2"; shift 2 ;;
    *) echo "Unknown option: $1"; exit 1 ;;
  esac
done

echo "=========================================="
echo "Login (Password Only) Verification"
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

echo "Server:       ${AUTHORIZATION_SERVER_URL}"
echo "Organization: ${ORGANIZATION_NAME}"
echo "Tenant ID:    ${PUBLIC_TENANT_ID}"
echo "Client ID:    ${CLIENT_ID}"
echo "Redirect URI: ${REDIRECT_URI}"
echo ""

TENANT_BASE="${AUTHORIZATION_SERVER_URL}/${PUBLIC_TENANT_ID}"

# --- Helper: Extract query parameter from URL ---
extract_param() {
  local url="$1"
  local param="$2"
  echo "${url}" | sed -n "s/.*[?&]${param}=\([^&#]*\).*/\1/p"
}

# --- Helper: URL decode ---
urldecode() {
  python3 -c "import urllib.parse; print(urllib.parse.unquote('$1'))"
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
# Step 1: Discovery Endpoint
# ============================================================
echo "Step 1: Checking discovery endpoint..."

DISCOVERY_RESPONSE=$(curl -s -w "\n%{http_code}" \
  "${TENANT_BASE}/.well-known/openid-configuration")

DISCOVERY_HTTP=$(echo "${DISCOVERY_RESPONSE}" | tail -n1)
DISCOVERY_BODY=$(echo "${DISCOVERY_RESPONSE}" | sed '$d')

if [ "${DISCOVERY_HTTP}" = "200" ]; then
  DISCOVERY_ISSUER=$(echo "${DISCOVERY_BODY}" | jq -r '.issuer')
  DISCOVERY_AUTH_EP=$(echo "${DISCOVERY_BODY}" | jq -r '.authorization_endpoint')
  DISCOVERY_TOKEN_EP=$(echo "${DISCOVERY_BODY}" | jq -r '.token_endpoint')
  DISCOVERY_USERINFO_EP=$(echo "${DISCOVERY_BODY}" | jq -r '.userinfo_endpoint')

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
# Step 2: Start Authorization Request
# ============================================================
echo "Step 2: Starting authorization request..."

STATE="verify-state-$(date +%s)"
SCOPE="openid profile email"

AUTH_RESPONSE=$(curl -s -c "${COOKIE_JAR}" -o /dev/null \
  -w "%{http_code}|%{redirect_url}" \
  "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=$(python3 -c "import urllib.parse; print(urllib.parse.quote('${REDIRECT_URI}', safe=''))")&scope=$(echo "${SCOPE}" | tr ' ' '+')&state=${STATE}")

AUTH_HTTP=$(echo "${AUTH_RESPONSE}" | cut -d'|' -f1)
AUTH_REDIRECT_URL=$(echo "${AUTH_RESPONSE}" | cut -d'|' -f2)

if [ "${AUTH_HTTP}" = "302" ]; then
  AUTHORIZATION_ID=$(extract_param "${AUTH_REDIRECT_URL}" "id")

  if [ -n "${AUTHORIZATION_ID}" ]; then
    echo "  HTTP 302 Redirect"
    echo "  Authorization ID: ${AUTHORIZATION_ID}"
    check_result "authorization" "true"
  else
    echo "  HTTP 302 but no authorization ID found"
    echo "  Redirect URL: ${AUTH_REDIRECT_URL}"
    check_result "authorization" "false"
  fi
else
  echo "  Expected HTTP 302, got ${AUTH_HTTP}"
  check_result "authorization" "false"
fi
echo ""

# ============================================================
# Step 3: Register Test User (initial-registration)
# ============================================================
echo "Step 3: Registering test user (initial-registration)..."

TEST_EMAIL="verify-$(date +%s)@example.com"
TEST_PASSWORD="VerifyPass123"
TEST_NAME="Verify User"

REGISTRATION_RESPONSE=$(curl -s -w "\n%{http_code}" \
  -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/initial-registration" \
  -H "Content-Type: application/json" \
  -d "{
    \"email\": \"${TEST_EMAIL}\",
    \"password\": \"${TEST_PASSWORD}\",
    \"name\": \"${TEST_NAME}\"
  }")

REG_HTTP=$(echo "${REGISTRATION_RESPONSE}" | tail -n1)
REG_BODY=$(echo "${REGISTRATION_RESPONSE}" | sed '$d')

if [ "${REG_HTTP}" = "200" ] || [ "${REG_HTTP}" = "201" ]; then
  echo "  HTTP ${REG_HTTP} OK"
  echo "  User: ${TEST_EMAIL}"
  check_result "registration" "true"
else
  echo "  HTTP ${REG_HTTP}"
  echo "  ${REG_BODY}" | jq '.' 2>/dev/null || echo "  ${REG_BODY}"
  check_result "registration" "false"
fi
echo ""

# ============================================================
# Step 4: Authorize (consent grant)
# ============================================================
echo "Step 4: Granting authorization..."

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
  RETURNED_STATE=$(extract_param "${AUTHZ_REDIRECT_URI}" "state")

  if [ -n "${AUTHORIZATION_CODE}" ]; then
    echo "  HTTP 200 OK"
    echo "  Code: ${AUTHORIZATION_CODE:0:20}..."
    echo "  State matches: $([ "${RETURNED_STATE}" = "${STATE}" ] && echo "yes" || echo "no (expected: ${STATE}, got: ${RETURNED_STATE})")"
    check_result "authorize" "true"
  else
    echo "  HTTP 200 but no authorization code found"
    echo "  redirect_uri: ${AUTHZ_REDIRECT_URI}"
    check_result "authorize" "false"
  fi
else
  echo "  HTTP ${AUTHZ_HTTP}"
  echo "  ${AUTHZ_BODY}" | jq '.' 2>/dev/null || echo "  ${AUTHZ_BODY}"
  check_result "authorize" "false"
fi
echo ""

# ============================================================
# Step 5: Exchange Code for Tokens
# ============================================================
echo "Step 5: Exchanging authorization code for tokens..."

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
  EXPIRES_IN=$(echo "${TOKEN_BODY}" | jq -r '.expires_in')

  echo "  HTTP 200 OK"
  echo "  Token Type: ${TOKEN_TYPE}"
  echo "  Expires In: ${EXPIRES_IN}s"
  echo "  Access Token:  $([ -n "${ACCESS_TOKEN}" ] && [ "${ACCESS_TOKEN}" != "null" ] && echo "${ACCESS_TOKEN:0:20}..." || echo "missing")"
  echo "  ID Token:      $([ -n "${ID_TOKEN}" ] && [ "${ID_TOKEN}" != "null" ] && echo "${ID_TOKEN:0:20}..." || echo "missing")"
  echo "  Refresh Token: $([ -n "${REFRESH_TOKEN}" ] && [ "${REFRESH_TOKEN}" != "null" ] && echo "${REFRESH_TOKEN:0:20}..." || echo "none")"

  if [ -n "${ACCESS_TOKEN}" ] && [ "${ACCESS_TOKEN}" != "null" ]; then
    check_result "token_exchange" "true"
  else
    check_result "token_exchange" "false"
  fi
else
  echo "  HTTP ${TOKEN_HTTP}"
  echo "  ${TOKEN_BODY}" | jq '.' 2>/dev/null || echo "  ${TOKEN_BODY}"
  check_result "token_exchange" "false"
fi
echo ""

# ============================================================
# Step 6: Verify UserInfo Endpoint
# ============================================================
echo "Step 6: Verifying UserInfo endpoint..."

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

  if [ -n "${USERINFO_SUB}" ] && [ "${USERINFO_SUB}" != "null" ]; then
    echo "  Token is valid (sub present)"
    check_result "userinfo" "true"
  else
    echo "  Invalid response: sub is missing"
    check_result "userinfo" "false"
  fi
else
  echo "  HTTP ${USERINFO_HTTP}"
  echo "  ${USERINFO_BODY}" | jq '.' 2>/dev/null || echo "  ${USERINFO_BODY}"
  check_result "userinfo" "false"
fi
echo ""

# ============================================================
# Step 7: Verify Refresh Token (if available)
# ============================================================
if [ -n "${REFRESH_TOKEN}" ] && [ "${REFRESH_TOKEN}" != "null" ]; then
  echo "Step 7: Verifying refresh token..."

  REFRESH_RESPONSE=$(curl -s -w "\n%{http_code}" \
    -X POST "${TENANT_BASE}/v1/tokens" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    --data-urlencode "grant_type=refresh_token" \
    --data-urlencode "refresh_token=${REFRESH_TOKEN}" \
    --data-urlencode "client_id=${CLIENT_ID}" \
    --data-urlencode "client_secret=${CLIENT_SECRET}")

  REFRESH_HTTP=$(echo "${REFRESH_RESPONSE}" | tail -n1)
  REFRESH_BODY=$(echo "${REFRESH_RESPONSE}" | sed '$d')

  if [ "${REFRESH_HTTP}" = "200" ]; then
    NEW_ACCESS_TOKEN=$(echo "${REFRESH_BODY}" | jq -r '.access_token')
    echo "  HTTP 200 OK"
    echo "  New Access Token: ${NEW_ACCESS_TOKEN:0:20}..."
    check_result "refresh_token" "true"
  else
    echo "  HTTP ${REFRESH_HTTP}"
    echo "  ${REFRESH_BODY}" | jq '.' 2>/dev/null || echo "  ${REFRESH_BODY}"
    check_result "refresh_token" "false"
  fi
  echo ""
else
  echo "Step 7: Skipped (no refresh token issued)"
  echo ""
fi

# ============================================================
# Step 8: Password Change
# ============================================================
echo "Step 8: Changing password..."

# Use refreshed token if available, otherwise original
CURRENT_ACCESS_TOKEN="${NEW_ACCESS_TOKEN:-${ACCESS_TOKEN}}"

NEW_PASSWORD="NewVerifyPass456"
PASSWORD_CHANGE_RESPONSE=$(curl -s -w "\n%{http_code}" \
  -X POST "${TENANT_BASE}/v1/me/password/change" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${CURRENT_ACCESS_TOKEN}" \
  -d "{
    \"current_password\": \"${TEST_PASSWORD}\",
    \"new_password\": \"${NEW_PASSWORD}\"
  }")

PWCHANGE_HTTP=$(echo "${PASSWORD_CHANGE_RESPONSE}" | tail -n1)
PWCHANGE_BODY=$(echo "${PASSWORD_CHANGE_RESPONSE}" | sed '$d')

if [ "${PWCHANGE_HTTP}" = "200" ]; then
  echo "  HTTP 200 OK"
  echo "  ${PWCHANGE_BODY}" | jq '.' 2>/dev/null || echo "  ${PWCHANGE_BODY}"
  check_result "password_change" "true"
else
  echo "  HTTP ${PWCHANGE_HTTP}"
  echo "  ${PWCHANGE_BODY}" | jq '.' 2>/dev/null || echo "  ${PWCHANGE_BODY}"
  check_result "password_change" "false"
fi
echo ""

# ============================================================
# Step 9: Verify Login with New Password
# ============================================================
echo "Step 9: Verifying login with new password..."

STATE2="verify-state2-$(date +%s)"

AUTH_RESPONSE2=$(curl -s -c "${COOKIE_JAR}" -o /dev/null \
  -w "%{http_code}|%{redirect_url}" \
  "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=$(python3 -c "import urllib.parse; print(urllib.parse.quote('${REDIRECT_URI}', safe=''))")&scope=$(echo "${SCOPE}" | tr ' ' '+')&state=${STATE2}&prompt=login")

AUTH_HTTP2=$(echo "${AUTH_RESPONSE2}" | cut -d'|' -f1)
AUTH_REDIRECT_URL2=$(echo "${AUTH_RESPONSE2}" | cut -d'|' -f2)

if [ "${AUTH_HTTP2}" = "302" ]; then
  AUTHORIZATION_ID2=$(extract_param "${AUTH_REDIRECT_URL2}" "id")

  # Login with new password
  PW_RESPONSE=$(curl -s -w "\n%{http_code}" \
    -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
    -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID2}/password-authentication" \
    -H "Content-Type: application/json" \
    -d "{
      \"username\": \"${TEST_EMAIL}\",
      \"password\": \"${NEW_PASSWORD}\"
    }")

  PW_HTTP=$(echo "${PW_RESPONSE}" | tail -n1)

  if [ "${PW_HTTP}" = "200" ]; then
    echo "  HTTP 200 OK - Login with new password succeeded"
    check_result "login_new_password" "true"
  else
    PW_BODY=$(echo "${PW_RESPONSE}" | sed '$d')
    echo "  HTTP ${PW_HTTP} - Login with new password failed"
    echo "  ${PW_BODY}" | jq '.' 2>/dev/null || echo "  ${PW_BODY}"
    check_result "login_new_password" "false"
  fi
else
  echo "  Authorization request failed: HTTP ${AUTH_HTTP2}"
  check_result "login_new_password" "false"
fi
echo ""

# ============================================================
# Step 10: Admin Password Reset (Management API)
# ============================================================
echo "Step 10: Admin password reset via Management API..."

ADMIN_RESET_PASSWORD="AdminResetPass789"

# Get user sub from ID token
USER_SUB=$(echo "${ID_TOKEN}" | cut -d'.' -f2 | python3 -c "import sys,base64,json; print(json.loads(base64.urlsafe_b64decode(sys.stdin.read().strip()+'=='))['sub'])")

# Get org admin token
ORG_ID=$(jq -r '.organization.id' "${CONFIG_DIR}/onboarding.json")
ORGANIZER_TENANT_ID=$(jq -r '.tenant.id' "${CONFIG_DIR}/onboarding.json")
ADMIN_EMAIL_ADDR=$(jq -r '.user.email' "${CONFIG_DIR}/onboarding.json")
ADMIN_PASSWORD_VAL=$(jq -r '.user.raw_password' "${CONFIG_DIR}/onboarding.json")
ORG_CLIENT_ID=$(jq -r '.client.client_id' "${CONFIG_DIR}/onboarding.json")
ORG_CLIENT_SECRET=$(jq -r '.client.client_secret' "${CONFIG_DIR}/onboarding.json")

ORG_TOKEN=$(curl -s -X POST \
  "${AUTHORIZATION_SERVER_URL}/${ORGANIZER_TENANT_ID}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=password" \
  --data-urlencode "username=${ADMIN_EMAIL_ADDR}" \
  --data-urlencode "password=${ADMIN_PASSWORD_VAL}" \
  --data-urlencode "client_id=${ORG_CLIENT_ID}" \
  --data-urlencode "client_secret=${ORG_CLIENT_SECRET}" \
  --data-urlencode "scope=openid profile email management" | jq -r '.access_token')

# Reset password via Management API
ADMIN_RESET_RESPONSE=$(curl -s -w "\n%{http_code}" \
  -X PUT "${AUTHORIZATION_SERVER_URL}/v1/management/organizations/${ORG_ID}/tenants/${PUBLIC_TENANT_ID}/users/${USER_SUB}/password" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ORG_TOKEN}" \
  -d "{
    \"raw_password\": \"${ADMIN_RESET_PASSWORD}\"
  }")

ADMIN_RESET_HTTP=$(echo "${ADMIN_RESET_RESPONSE}" | tail -n1)
ADMIN_RESET_BODY=$(echo "${ADMIN_RESET_RESPONSE}" | sed '$d')

if [ "${ADMIN_RESET_HTTP}" = "200" ]; then
  echo "  HTTP 200 OK - Admin password reset succeeded"
  check_result "admin_password_reset" "true"
else
  echo "  HTTP ${ADMIN_RESET_HTTP}"
  echo "  ${ADMIN_RESET_BODY}" | jq '.' 2>/dev/null || echo "  ${ADMIN_RESET_BODY}"
  check_result "admin_password_reset" "false"
fi
echo ""

# ============================================================
# Step 11: Verify Login with Admin-Reset Password
# ============================================================
echo "Step 11: Verifying login with admin-reset password..."

STATE3="verify-state3-$(date +%s)"

AUTH_RESPONSE3=$(curl -s -c "${COOKIE_JAR}" -o /dev/null \
  -w "%{http_code}|%{redirect_url}" \
  "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=$(python3 -c "import urllib.parse; print(urllib.parse.quote('${REDIRECT_URI}', safe=''))")&scope=$(echo "${SCOPE}" | tr ' ' '+')&state=${STATE3}&prompt=login")

AUTH_HTTP3=$(echo "${AUTH_RESPONSE3}" | cut -d'|' -f1)
AUTH_REDIRECT_URL3=$(echo "${AUTH_RESPONSE3}" | cut -d'|' -f2)

if [ "${AUTH_HTTP3}" = "302" ]; then
  AUTHORIZATION_ID3=$(extract_param "${AUTH_REDIRECT_URL3}" "id")

  PW_RESPONSE3=$(curl -s -w "\n%{http_code}" \
    -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
    -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID3}/password-authentication" \
    -H "Content-Type: application/json" \
    -d "{
      \"username\": \"${TEST_EMAIL}\",
      \"password\": \"${ADMIN_RESET_PASSWORD}\"
    }")

  PW_HTTP3=$(echo "${PW_RESPONSE3}" | tail -n1)

  if [ "${PW_HTTP3}" = "200" ]; then
    echo "  HTTP 200 OK - Login with admin-reset password succeeded"
    check_result "login_admin_reset_password" "true"
  else
    PW_BODY3=$(echo "${PW_RESPONSE3}" | sed '$d')
    echo "  HTTP ${PW_HTTP3} - Login with admin-reset password failed"
    echo "  ${PW_BODY3}" | jq '.' 2>/dev/null || echo "  ${PW_BODY3}"
    check_result "login_admin_reset_password" "false"
  fi
else
  echo "  Authorization request failed: HTTP ${AUTH_HTTP3}"
  check_result "login_admin_reset_password" "false"
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
  echo "All checks passed! The tenant is working correctly."
  exit 0
else
  echo "Some checks failed. Review the output above for details."
  exit 1
fi
