#!/bin/bash
set -e

# MFA (Password + Email OTP) Example - Verification Script
#
# Verifies that the example environment works correctly in two phases:
#
# Phase 1 (User Registration):
#   1. Discovery endpoint check
#   2. Authorization request
#   3. User registration via initial-registration
#   4. Consent grant → Token exchange → UserInfo (basic flow)
#
# Phase 2 (MFA Login):
#   5. New authorization request (same user, second login)
#   6. Email OTP challenge (no-action mode)
#   7. Email OTP verification
#   8. Password authentication
#   9. Consent grant → Token exchange → UserInfo (MFA flow)
#
# Note: Email OTP uses no-action mode for local development.
#       The verification code is returned in the challenge response.
#
# Prerequisites:
#   - setup.sh has been executed successfully
#
# Usage:
#   ./verify.sh

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"

echo "=========================================="
echo "MFA (Password + Email OTP) Example Verification"
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

# --- Load config from example JSON files ---
PUBLIC_TENANT_ID=$(jq -r '.tenant.id' "${SCRIPT_DIR}/public-tenant-request.json")
CLIENT_ID=$(jq -r '.client_id' "${SCRIPT_DIR}/client-request.json")
CLIENT_SECRET=$(jq -r '.client_secret' "${SCRIPT_DIR}/client-request.json")
REDIRECT_URI=$(jq -r '.redirect_uris[0]' "${SCRIPT_DIR}/client-request.json")

echo "Server:       ${AUTHORIZATION_SERVER_URL}"
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

# Test user credentials (shared between Phase 1 and Phase 2)
TEST_EMAIL="verify-$(date +%s)@example.com"
TEST_PASSWORD="VerifyPass123"
TEST_NAME="Verify User"

echo "=========================================="
echo "Phase 1: User Registration (initial-registration)"
echo "=========================================="
echo ""

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
# Step 2: Start Authorization Request (for registration)
# ============================================================
echo "Step 2: Starting authorization request (for registration)..."

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
    check_result "authorization_reg" "true"
  else
    echo "  HTTP 302 but no authorization ID found"
    echo "  Redirect URL: ${AUTH_REDIRECT_URL}"
    check_result "authorization_reg" "false"
  fi
else
  echo "  Expected HTTP 302, got ${AUTH_HTTP}"
  check_result "authorization_reg" "false"
fi
echo ""

# ============================================================
# Step 3: Register Test User (initial-registration)
# ============================================================
echo "Step 3: Registering test user (initial-registration)..."

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
# Step 4: Authorize + Token + UserInfo (registration flow)
# ============================================================
echo "Step 4: Completing registration flow (authorize → token → userinfo)..."

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
    echo "  Authorize: OK (code obtained)"

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
      REG_ACCESS_TOKEN=$(echo "${TOKEN_BODY}" | jq -r '.access_token')
      echo "  Token:    OK (access_token obtained)"

      USERINFO_RESPONSE=$(curl -s -w "\n%{http_code}" \
        -X GET "${TENANT_BASE}/v1/userinfo" \
        -H "Authorization: Bearer ${REG_ACCESS_TOKEN}")

      USERINFO_HTTP=$(echo "${USERINFO_RESPONSE}" | tail -n1)
      USERINFO_BODY=$(echo "${USERINFO_RESPONSE}" | sed '$d')

      if [ "${USERINFO_HTTP}" = "200" ]; then
        USERINFO_SUB=$(echo "${USERINFO_BODY}" | jq -r '.sub')
        echo "  UserInfo: OK (sub: ${USERINFO_SUB})"
        check_result "registration_flow" "true"
      else
        echo "  UserInfo: FAILED (HTTP ${USERINFO_HTTP})"
        check_result "registration_flow" "false"
      fi
    else
      echo "  Token: FAILED (HTTP ${TOKEN_HTTP})"
      check_result "registration_flow" "false"
    fi
  else
    echo "  Authorize: no code returned"
    check_result "registration_flow" "false"
  fi
else
  echo "  Authorize: FAILED (HTTP ${AUTHZ_HTTP})"
  echo "  ${AUTHZ_BODY}" | jq '.' 2>/dev/null || echo "  ${AUTHZ_BODY}"
  check_result "registration_flow" "false"
fi
echo ""

# ============================================================
# Phase 2: MFA Login (email OTP + password)
# ============================================================
echo "=========================================="
echo "Phase 2: MFA Login (email OTP + password)"
echo "=========================================="
echo ""

# Reset cookie jar for fresh session
rm -f "${COOKIE_JAR}"
COOKIE_JAR=$(mktemp)

# Load organizer admin credentials for management API access
ORG_ID=$(jq -r '.organization.id' "${SCRIPT_DIR}/onboarding-request.json")
ORGANIZER_TENANT_ID=$(jq -r '.tenant.id' "${SCRIPT_DIR}/onboarding-request.json")
ORG_ADMIN_EMAIL=$(jq -r '.user.email' "${SCRIPT_DIR}/onboarding-request.json")
ORG_ADMIN_PASSWORD=$(jq -r '.user.raw_password' "${SCRIPT_DIR}/onboarding-request.json")
ORG_CLIENT_ID=$(jq -r '.client.client_id' "${SCRIPT_DIR}/onboarding-request.json")
ORG_CLIENT_SECRET=$(jq -r '.client.client_secret' "${SCRIPT_DIR}/onboarding-request.json")

# ============================================================
# Step 5: Start New Authorization Request (for MFA login)
# ============================================================
echo "Step 5: Starting new authorization request (MFA login)..."

STATE2="verify-mfa-$(date +%s)"

AUTH_RESPONSE2=$(curl -s -c "${COOKIE_JAR}" -o /dev/null \
  -w "%{http_code}|%{redirect_url}" \
  "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=$(python3 -c "import urllib.parse; print(urllib.parse.quote('${REDIRECT_URI}', safe=''))")&scope=$(echo "${SCOPE}" | tr ' ' '+')&state=${STATE2}")

AUTH_HTTP2=$(echo "${AUTH_RESPONSE2}" | cut -d'|' -f1)
AUTH_REDIRECT_URL2=$(echo "${AUTH_RESPONSE2}" | cut -d'|' -f2)

if [ "${AUTH_HTTP2}" = "302" ]; then
  AUTHORIZATION_ID2=$(extract_param "${AUTH_REDIRECT_URL2}" "id")

  if [ -n "${AUTHORIZATION_ID2}" ]; then
    echo "  HTTP 302 Redirect"
    echo "  Authorization ID: ${AUTHORIZATION_ID2}"
    check_result "authorization_mfa" "true"
  else
    echo "  HTTP 302 but no authorization ID found"
    echo "  Redirect URL: ${AUTH_REDIRECT_URL2}"
    check_result "authorization_mfa" "false"
  fi
else
  echo "  Expected HTTP 302, got ${AUTH_HTTP2}"
  check_result "authorization_mfa" "false"
fi
echo ""

# ============================================================
# Step 6: Email OTP Challenge (1st factor)
# ============================================================
echo "Step 6: Sending email OTP challenge (no-action mode)..."

EMAIL_CHALLENGE_RESPONSE=$(curl -s -w "\n%{http_code}" \
  -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID2}/email-authentication-challenge" \
  -H "Content-Type: application/json" \
  -d "{
    \"email\": \"${TEST_EMAIL}\",
    \"template\": \"authentication\"
  }")

EMAIL_CHALLENGE_HTTP=$(echo "${EMAIL_CHALLENGE_RESPONSE}" | tail -n1)
EMAIL_CHALLENGE_BODY=$(echo "${EMAIL_CHALLENGE_RESPONSE}" | sed '$d')

if [ "${EMAIL_CHALLENGE_HTTP}" = "200" ] || [ "${EMAIL_CHALLENGE_HTTP}" = "201" ]; then
  echo "  HTTP ${EMAIL_CHALLENGE_HTTP} OK"
  echo "  Email OTP challenge sent"
  check_result "email_challenge" "true"
else
  echo "  HTTP ${EMAIL_CHALLENGE_HTTP}"
  echo "  ${EMAIL_CHALLENGE_BODY}" | jq '.' 2>/dev/null || echo "  ${EMAIL_CHALLENGE_BODY}"
  check_result "email_challenge" "false"
fi
echo ""

# ============================================================
# Step 6b: Retrieve verification code via Management API
# ============================================================
echo "  Retrieving verification code via Management API..."

# Get organizer admin access token
ORG_TOKEN_RESPONSE=$(curl -s -X POST \
  "${AUTHORIZATION_SERVER_URL}/${ORGANIZER_TENANT_ID}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=password" \
  --data-urlencode "username=${ORG_ADMIN_EMAIL}" \
  --data-urlencode "password=${ORG_ADMIN_PASSWORD}" \
  --data-urlencode "client_id=${ORG_CLIENT_ID}" \
  --data-urlencode "client_secret=${ORG_CLIENT_SECRET}" \
  --data-urlencode "scope=openid profile email management")

ORG_ACCESS_TOKEN=$(echo "${ORG_TOKEN_RESPONSE}" | jq -r '.access_token')

# Get authentication transaction ID
TRANSACTION_RESPONSE=$(curl -s \
  "${AUTHORIZATION_SERVER_URL}/v1/management/organizations/${ORG_ID}/tenants/${PUBLIC_TENANT_ID}/authentication-transactions?authorization_id=${AUTHORIZATION_ID2}" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}")

TRANSACTION_ID=$(echo "${TRANSACTION_RESPONSE}" | jq -r '.list[0].id')

# Get verification code from authentication interaction
INTERACTION_RESPONSE=$(curl -s \
  "${AUTHORIZATION_SERVER_URL}/v1/management/organizations/${ORG_ID}/tenants/${PUBLIC_TENANT_ID}/authentication-interactions/${TRANSACTION_ID}/email-authentication-challenge" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}")

VERIFICATION_CODE=$(echo "${INTERACTION_RESPONSE}" | jq -r '.payload.verification_code')

if [ -n "${VERIFICATION_CODE}" ] && [ "${VERIFICATION_CODE}" != "null" ]; then
  echo "  Verification code retrieved: ${VERIFICATION_CODE}"
else
  echo "  Warning: Could not retrieve verification code, using fallback"
  VERIFICATION_CODE="123456"
fi
echo ""

# ============================================================
# Step 7: Email OTP Verification (1st factor complete)
# ============================================================
echo "Step 7: Verifying email OTP (code: ${VERIFICATION_CODE})..."

EMAIL_VERIFY_RESPONSE=$(curl -s -w "\n%{http_code}" \
  -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID2}/email-authentication" \
  -H "Content-Type: application/json" \
  -d "{
    \"verification_code\": \"${VERIFICATION_CODE}\"
  }")

EMAIL_VERIFY_HTTP=$(echo "${EMAIL_VERIFY_RESPONSE}" | tail -n1)
EMAIL_VERIFY_BODY=$(echo "${EMAIL_VERIFY_RESPONSE}" | sed '$d')

if [ "${EMAIL_VERIFY_HTTP}" = "200" ] || [ "${EMAIL_VERIFY_HTTP}" = "201" ]; then
  echo "  HTTP ${EMAIL_VERIFY_HTTP} OK"
  echo "  Email OTP verified successfully"
  check_result "email_verification" "true"
else
  echo "  HTTP ${EMAIL_VERIFY_HTTP}"
  echo "  ${EMAIL_VERIFY_BODY}" | jq '.' 2>/dev/null || echo "  ${EMAIL_VERIFY_BODY}"
  check_result "email_verification" "false"
fi
echo ""

# ============================================================
# Step 8: Password Authentication (2nd factor)
# ============================================================
echo "Step 8: Authenticating with password (2nd factor)..."

PASSWORD_AUTH_RESPONSE=$(curl -s -w "\n%{http_code}" \
  -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID2}/password-authentication" \
  -H "Content-Type: application/json" \
  -d "{
    \"username\": \"${TEST_EMAIL}\",
    \"password\": \"${TEST_PASSWORD}\"
  }")

PASSWORD_AUTH_HTTP=$(echo "${PASSWORD_AUTH_RESPONSE}" | tail -n1)
PASSWORD_AUTH_BODY=$(echo "${PASSWORD_AUTH_RESPONSE}" | sed '$d')

if [ "${PASSWORD_AUTH_HTTP}" = "200" ] || [ "${PASSWORD_AUTH_HTTP}" = "201" ]; then
  echo "  HTTP ${PASSWORD_AUTH_HTTP} OK"
  echo "  Password authentication successful"
  check_result "password_authentication" "true"
else
  echo "  HTTP ${PASSWORD_AUTH_HTTP}"
  echo "  ${PASSWORD_AUTH_BODY}" | jq '.' 2>/dev/null || echo "  ${PASSWORD_AUTH_BODY}"
  check_result "password_authentication" "false"
fi
echo ""

# ============================================================
# Step 9: Authorize (consent grant) → Token → UserInfo
# ============================================================
echo "Step 9: Completing MFA flow (authorize → token → userinfo)..."

AUTHORIZE_RESPONSE2=$(curl -s -w "\n%{http_code}" \
  -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID2}/authorize" \
  -H "Content-Type: application/json" \
  -d '{}')

AUTHZ_HTTP2=$(echo "${AUTHORIZE_RESPONSE2}" | tail -n1)
AUTHZ_BODY2=$(echo "${AUTHORIZE_RESPONSE2}" | sed '$d')

if [ "${AUTHZ_HTTP2}" = "200" ]; then
  AUTHZ_REDIRECT_URI2=$(echo "${AUTHZ_BODY2}" | jq -r '.redirect_uri')
  AUTHORIZATION_CODE2=$(extract_param "${AUTHZ_REDIRECT_URI2}" "code")
  RETURNED_STATE2=$(extract_param "${AUTHZ_REDIRECT_URI2}" "state")

  if [ -n "${AUTHORIZATION_CODE2}" ]; then
    echo "  Authorize: OK (code obtained)"
    echo "  State matches: $([ "${RETURNED_STATE2}" = "${STATE2}" ] && echo "yes" || echo "no")"

    TOKEN_RESPONSE2=$(curl -s -w "\n%{http_code}" \
      -X POST "${TENANT_BASE}/v1/tokens" \
      -H "Content-Type: application/x-www-form-urlencoded" \
      --data-urlencode "grant_type=authorization_code" \
      --data-urlencode "code=${AUTHORIZATION_CODE2}" \
      --data-urlencode "redirect_uri=${REDIRECT_URI}" \
      --data-urlencode "client_id=${CLIENT_ID}" \
      --data-urlencode "client_secret=${CLIENT_SECRET}")

    TOKEN_HTTP2=$(echo "${TOKEN_RESPONSE2}" | tail -n1)
    TOKEN_BODY2=$(echo "${TOKEN_RESPONSE2}" | sed '$d')

    if [ "${TOKEN_HTTP2}" = "200" ]; then
      ACCESS_TOKEN2=$(echo "${TOKEN_BODY2}" | jq -r '.access_token')
      REFRESH_TOKEN2=$(echo "${TOKEN_BODY2}" | jq -r '.refresh_token')
      echo "  Token:    OK (access_token: ${ACCESS_TOKEN2:0:20}...)"

      USERINFO_RESPONSE2=$(curl -s -w "\n%{http_code}" \
        -X GET "${TENANT_BASE}/v1/userinfo" \
        -H "Authorization: Bearer ${ACCESS_TOKEN2}")

      USERINFO_HTTP2=$(echo "${USERINFO_RESPONSE2}" | tail -n1)
      USERINFO_BODY2=$(echo "${USERINFO_RESPONSE2}" | sed '$d')

      if [ "${USERINFO_HTTP2}" = "200" ]; then
        USERINFO_SUB2=$(echo "${USERINFO_BODY2}" | jq -r '.sub')
        echo "  UserInfo: OK (sub: ${USERINFO_SUB2})"
        check_result "mfa_flow" "true"
      else
        echo "  UserInfo: FAILED (HTTP ${USERINFO_HTTP2})"
        check_result "mfa_flow" "false"
      fi

      # Refresh token check
      if [ -n "${REFRESH_TOKEN2}" ] && [ "${REFRESH_TOKEN2}" != "null" ]; then
        REFRESH_RESPONSE=$(curl -s -w "\n%{http_code}" \
          -X POST "${TENANT_BASE}/v1/tokens" \
          -H "Content-Type: application/x-www-form-urlencoded" \
          --data-urlencode "grant_type=refresh_token" \
          --data-urlencode "refresh_token=${REFRESH_TOKEN2}" \
          --data-urlencode "client_id=${CLIENT_ID}" \
          --data-urlencode "client_secret=${CLIENT_SECRET}")

        REFRESH_HTTP=$(echo "${REFRESH_RESPONSE}" | tail -n1)
        if [ "${REFRESH_HTTP}" = "200" ]; then
          echo "  Refresh:  OK"
          check_result "refresh_token" "true"
        else
          echo "  Refresh:  FAILED (HTTP ${REFRESH_HTTP})"
          check_result "refresh_token" "false"
        fi
      fi
    else
      echo "  Token: FAILED (HTTP ${TOKEN_HTTP2})"
      echo "  ${TOKEN_BODY2}" | jq '.' 2>/dev/null || echo "  ${TOKEN_BODY2}"
      check_result "mfa_flow" "false"
    fi
  else
    echo "  Authorize: no code returned"
    check_result "mfa_flow" "false"
  fi
else
  echo "  Authorize: FAILED (HTTP ${AUTHZ_HTTP2})"
  echo "  ${AUTHZ_BODY2}" | jq '.' 2>/dev/null || echo "  ${AUTHZ_BODY2}"
  check_result "mfa_flow" "false"
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
  echo "All checks passed! The example environment is working correctly."
  exit 0
else
  echo "Some checks failed. Review the output above for details."
  exit 1
fi
