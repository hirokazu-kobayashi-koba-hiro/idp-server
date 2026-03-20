#!/bin/bash
set -e

# MFA (Password + Email OTP) - Verification Script
#
# Verifies that the tenant created by setup.sh works correctly by performing:
#   1. Discovery endpoint check
#   2. User registration via initial-registration
#   3. Authorization Code Flow (authorization -> token exchange)
#   4. UserInfo endpoint verification
#   5. Token Refresh
#
# Note: MFA email + password multi-step authentication cannot be fully automated.
# This script verifies the initial-registration path (bypasses MFA) to confirm
# tenant configuration is correct.
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
ORGANIZATION_NAME="mfa-email"
while [ $# -gt 0 ]; do
  case "$1" in
    --org) ORGANIZATION_NAME="$2"; shift 2 ;;
    *) echo "Unknown option: $1"; exit 1 ;;
  esac
done

echo "=========================================="
echo "MFA (Password + Email OTP) Verification"
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
# Step 9: Password Reset Flow (email authentication → reset)
# ============================================================
echo "Step 9: Password reset flow..."

RESET_PASSWORD="ResetVerifyPass789"

# 9a: Start authorization with password:reset scope
RESET_STATE="verify-reset-$(date +%s)"
RESET_SCOPE="openid password:reset"

COOKIE_JAR_RESET=$(mktemp)
trap "rm -f ${COOKIE_JAR} ${COOKIE_JAR_RESET}" EXIT

RESET_AUTH_RESPONSE=$(curl -s -c "${COOKIE_JAR_RESET}" -o /dev/null \
  -w "%{http_code}|%{redirect_url}" \
  "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=$(python3 -c "import urllib.parse; print(urllib.parse.quote('${REDIRECT_URI}', safe=''))")&scope=$(echo "${RESET_SCOPE}" | tr ' ' '+')&state=${RESET_STATE}&prompt=login")

RESET_AUTH_HTTP=$(echo "${RESET_AUTH_RESPONSE}" | cut -d'|' -f1)
RESET_AUTH_REDIRECT=$(echo "${RESET_AUTH_RESPONSE}" | cut -d'|' -f2)

if [ "${RESET_AUTH_HTTP}" != "302" ]; then
  echo "  Authorization request failed: HTTP ${RESET_AUTH_HTTP}"
  check_result "password_reset" "false"
else
  RESET_AUTH_ID=$(extract_param "${RESET_AUTH_REDIRECT}" "id")
  echo "  9a: Authorization started (id: ${RESET_AUTH_ID})"

  # 9b: Email authentication challenge
  EMAIL_CHALLENGE_RESPONSE=$(curl -s -w "\n%{http_code}" \
    -b "${COOKIE_JAR_RESET}" -c "${COOKIE_JAR_RESET}" \
    -X POST "${TENANT_BASE}/v1/authorizations/${RESET_AUTH_ID}/email-authentication-challenge" \
    -H "Content-Type: application/json" \
    -d "{
      \"email\": \"${TEST_EMAIL}\",
      \"template\": \"authentication\"
    }")

  EMAIL_CHALLENGE_HTTP=$(echo "${EMAIL_CHALLENGE_RESPONSE}" | tail -n1)

  if [ "${EMAIL_CHALLENGE_HTTP}" != "200" ] && [ "${EMAIL_CHALLENGE_HTTP}" != "201" ]; then
    echo "  9b: Email challenge failed: HTTP ${EMAIL_CHALLENGE_HTTP}"
    echo "${EMAIL_CHALLENGE_RESPONSE}" | sed '$d' | jq '.' 2>/dev/null
    check_result "password_reset" "false"
  else
    echo "  9b: Email challenge sent"

    # 9c: Get verification code via Management API
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

    TRANSACTION_ID=$(curl -s \
      "${AUTHORIZATION_SERVER_URL}/v1/management/organizations/${ORG_ID}/tenants/${PUBLIC_TENANT_ID}/authentication-transactions?authorization_id=${RESET_AUTH_ID}" \
      -H "Authorization: Bearer ${ORG_TOKEN}" | jq -r '.list[0].id')

    VERIFICATION_CODE=$(curl -s \
      "${AUTHORIZATION_SERVER_URL}/v1/management/organizations/${ORG_ID}/tenants/${PUBLIC_TENANT_ID}/authentication-interactions/${TRANSACTION_ID}/email-authentication-challenge" \
      -H "Authorization: Bearer ${ORG_TOKEN}" | jq -r '.payload.verification_code')

    echo "  9c: Verification code obtained: ${VERIFICATION_CODE}"

    # 9d: Email authentication verification
    EMAIL_VERIFY_RESPONSE=$(curl -s -w "\n%{http_code}" \
      -b "${COOKIE_JAR_RESET}" -c "${COOKIE_JAR_RESET}" \
      -X POST "${TENANT_BASE}/v1/authorizations/${RESET_AUTH_ID}/email-authentication" \
      -H "Content-Type: application/json" \
      -d "{
        \"verification_code\": \"${VERIFICATION_CODE}\"
      }")

    EMAIL_VERIFY_HTTP=$(echo "${EMAIL_VERIFY_RESPONSE}" | tail -n1)

    if [ "${EMAIL_VERIFY_HTTP}" != "200" ]; then
      echo "  9d: Email verification failed: HTTP ${EMAIL_VERIFY_HTTP}"
      check_result "password_reset" "false"
    else
      echo "  9d: Email verified"

      # 9e: Authorize
      RESET_AUTHORIZE_RESPONSE=$(curl -s -w "\n%{http_code}" \
        -b "${COOKIE_JAR_RESET}" -c "${COOKIE_JAR_RESET}" \
        -X POST "${TENANT_BASE}/v1/authorizations/${RESET_AUTH_ID}/authorize" \
        -H "Content-Type: application/json" \
        -d '{}')

      RESET_AUTHZ_HTTP=$(echo "${RESET_AUTHORIZE_RESPONSE}" | tail -n1)
      RESET_AUTHZ_BODY=$(echo "${RESET_AUTHORIZE_RESPONSE}" | sed '$d')

      if [ "${RESET_AUTHZ_HTTP}" != "200" ]; then
        echo "  9e: Authorize failed: HTTP ${RESET_AUTHZ_HTTP}"
        echo "  ${RESET_AUTHZ_BODY}" | jq '.' 2>/dev/null
        check_result "password_reset" "false"
      else
        RESET_CODE=$(extract_param "$(echo "${RESET_AUTHZ_BODY}" | jq -r '.redirect_uri')" "code")
        echo "  9e: Authorization code obtained"

        # 9f: Exchange code for token with password:reset scope
        RESET_TOKEN_RESPONSE=$(curl -s -w "\n%{http_code}" \
          -X POST "${TENANT_BASE}/v1/tokens" \
          -H "Content-Type: application/x-www-form-urlencoded" \
          --data-urlencode "grant_type=authorization_code" \
          --data-urlencode "code=${RESET_CODE}" \
          --data-urlencode "redirect_uri=${REDIRECT_URI}" \
          --data-urlencode "client_id=${CLIENT_ID}" \
          --data-urlencode "client_secret=${CLIENT_SECRET}")

        RESET_TOKEN_HTTP=$(echo "${RESET_TOKEN_RESPONSE}" | tail -n1)
        RESET_TOKEN_BODY=$(echo "${RESET_TOKEN_RESPONSE}" | sed '$d')

        if [ "${RESET_TOKEN_HTTP}" != "200" ]; then
          echo "  9f: Token exchange failed: HTTP ${RESET_TOKEN_HTTP}"
          check_result "password_reset" "false"
        else
          RESET_ACCESS_TOKEN=$(echo "${RESET_TOKEN_BODY}" | jq -r '.access_token')
          RESET_SCOPE_RESULT=$(echo "${RESET_TOKEN_BODY}" | jq -r '.scope')
          echo "  9f: Token obtained (scope: ${RESET_SCOPE_RESULT})"

          if echo "${RESET_SCOPE_RESULT}" | grep -q "password:reset"; then
            echo "  password:reset scope confirmed"
          else
            echo "  WARNING: password:reset scope not in token"
          fi

          # 9g: Reset password
          RESET_RESPONSE=$(curl -s -w "\n%{http_code}" \
            -X POST "${TENANT_BASE}/v1/me/password/reset" \
            -H "Content-Type: application/json" \
            -H "Authorization: Bearer ${RESET_ACCESS_TOKEN}" \
            -d "{
              \"new_password\": \"${RESET_PASSWORD}\"
            }")

          RESET_HTTP=$(echo "${RESET_RESPONSE}" | tail -n1)
          RESET_BODY=$(echo "${RESET_RESPONSE}" | sed '$d')

          if [ "${RESET_HTTP}" = "200" ]; then
            echo "  9g: Password reset succeeded"
            echo "  ${RESET_BODY}" | jq '.' 2>/dev/null || echo "  ${RESET_BODY}"
            check_result "password_reset" "true"
          else
            echo "  9g: Password reset failed: HTTP ${RESET_HTTP}"
            echo "  ${RESET_BODY}" | jq '.' 2>/dev/null || echo "  ${RESET_BODY}"
            check_result "password_reset" "false"
          fi
        fi
      fi
    fi
  fi
fi
echo ""

# ============================================================
# Step 10: Verify Login with Reset Password
# ============================================================
echo "Step 10: Verifying login with reset password..."

LOGIN_STATE="verify-login-$(date +%s)"

COOKIE_JAR_LOGIN=$(mktemp)
trap "rm -f ${COOKIE_JAR} ${COOKIE_JAR_RESET} ${COOKIE_JAR_LOGIN}" EXIT

LOGIN_AUTH_RESPONSE=$(curl -s -c "${COOKIE_JAR_LOGIN}" -o /dev/null \
  -w "%{http_code}|%{redirect_url}" \
  "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=$(python3 -c "import urllib.parse; print(urllib.parse.quote('${REDIRECT_URI}', safe=''))")&scope=openid+profile+email&state=${LOGIN_STATE}&prompt=login")

LOGIN_AUTH_HTTP=$(echo "${LOGIN_AUTH_RESPONSE}" | cut -d'|' -f1)
LOGIN_AUTH_REDIRECT=$(echo "${LOGIN_AUTH_RESPONSE}" | cut -d'|' -f2)

if [ "${LOGIN_AUTH_HTTP}" = "302" ]; then
  LOGIN_AUTH_ID=$(extract_param "${LOGIN_AUTH_REDIRECT}" "id")

  PW_LOGIN_RESPONSE=$(curl -s -w "\n%{http_code}" \
    -b "${COOKIE_JAR_LOGIN}" -c "${COOKIE_JAR_LOGIN}" \
    -X POST "${TENANT_BASE}/v1/authorizations/${LOGIN_AUTH_ID}/password-authentication" \
    -H "Content-Type: application/json" \
    -d "{
      \"username\": \"${TEST_EMAIL}\",
      \"password\": \"${RESET_PASSWORD}\"
    }")

  PW_LOGIN_HTTP=$(echo "${PW_LOGIN_RESPONSE}" | tail -n1)

  if [ "${PW_LOGIN_HTTP}" = "200" ]; then
    echo "  HTTP 200 OK - Login with reset password succeeded"
    check_result "login_reset_password" "true"
  else
    PW_LOGIN_BODY=$(echo "${PW_LOGIN_RESPONSE}" | sed '$d')
    echo "  HTTP ${PW_LOGIN_HTTP} - Login with reset password failed"
    echo "  ${PW_LOGIN_BODY}" | jq '.' 2>/dev/null || echo "  ${PW_LOGIN_BODY}"
    check_result "login_reset_password" "false"
  fi
else
  echo "  Authorization request failed: HTTP ${LOGIN_AUTH_HTTP}"
  check_result "login_reset_password" "false"
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
