#!/bin/bash
set -e

# Third Party Integration Example - Verification Script
#
# Verifies that the example environment works correctly by performing:
#   1. Discovery endpoint check (client_credentials, api:read, api:write)
#   2. M2M client_credentials grant
#   3. Token introspection (M2M token)
#   4. Web Client authorization request
#   5. User registration via initial-registration
#   6. Consent grant (authorize)
#   7. Token exchange (authorization code → tokens)
#   8. UserInfo endpoint verification
#   9. Token Refresh
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
echo "Third Party Integration Example Verification"
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
WEB_CLIENT_ID=$(jq -r '.client_id' "${SCRIPT_DIR}/client-web-request.json")
WEB_CLIENT_SECRET=$(jq -r '.client_secret' "${SCRIPT_DIR}/client-web-request.json")
WEB_REDIRECT_URI=$(jq -r '.redirect_uris[0]' "${SCRIPT_DIR}/client-web-request.json")
M2M_CLIENT_ID=$(jq -r '.client_id' "${SCRIPT_DIR}/client-m2m-request.json")
M2M_CLIENT_SECRET=$(jq -r '.client_secret' "${SCRIPT_DIR}/client-m2m-request.json")
M2M_SCOPE=$(jq -r '.scope' "${SCRIPT_DIR}/client-m2m-request.json")

echo "Server:       ${AUTHORIZATION_SERVER_URL}"
echo "Tenant ID:    ${PUBLIC_TENANT_ID}"
echo "Web Client:   ${WEB_CLIENT_ID}"
echo "M2M Client:   ${M2M_CLIENT_ID}"
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
# Step 4: Web Client Authorization Request
# ============================================================
echo "Step 4: Starting Web Client authorization request..."

STATE="verify-state-$(date +%s)"
SCOPE="openid profile email api:read"

AUTH_RESPONSE=$(curl -s -c "${COOKIE_JAR}" -o /dev/null \
  -w "%{http_code}|%{redirect_url}" \
  "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${WEB_CLIENT_ID}&redirect_uri=$(python3 -c "import urllib.parse; print(urllib.parse.quote('${WEB_REDIRECT_URI}', safe=''))")&scope=$(echo "${SCOPE}" | tr ' ' '+')&state=${STATE}")

AUTH_HTTP=$(echo "${AUTH_RESPONSE}" | cut -d'|' -f1)
AUTH_REDIRECT_URL=$(echo "${AUTH_RESPONSE}" | cut -d'|' -f2)

if [ "${AUTH_HTTP}" = "302" ]; then
  AUTHORIZATION_ID=$(extract_param "${AUTH_REDIRECT_URL}" "id")

  if [ -n "${AUTHORIZATION_ID}" ]; then
    echo "  HTTP 302 Redirect"
    echo "  Authorization ID: ${AUTHORIZATION_ID}"
    check_result "web_authorization" "true"
  else
    echo "  HTTP 302 but no authorization ID found"
    echo "  Redirect URL: ${AUTH_REDIRECT_URL}"
    check_result "web_authorization" "false"
  fi
else
  echo "  Expected HTTP 302, got ${AUTH_HTTP}"
  check_result "web_authorization" "false"
fi
echo ""

# ============================================================
# Step 5: Register Test User (initial-registration)
# ============================================================
echo "Step 5: Registering test user (initial-registration)..."

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
# Step 7: Exchange Code for Tokens (Web Client uses client_secret_basic)
# ============================================================
echo "Step 7: Exchanging authorization code for tokens..."

TOKEN_RESPONSE=$(curl -s -w "\n%{http_code}" \
  -X POST "${TENANT_BASE}/v1/tokens" \
  -u "${WEB_CLIENT_ID}:${WEB_CLIENT_SECRET}" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=authorization_code" \
  --data-urlencode "code=${AUTHORIZATION_CODE}" \
  --data-urlencode "redirect_uri=${WEB_REDIRECT_URI}")

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
# Step 8: Verify UserInfo Endpoint
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
# Step 9: Verify Refresh Token (if available)
# ============================================================
if [ -n "${REFRESH_TOKEN}" ] && [ "${REFRESH_TOKEN}" != "null" ]; then
  echo "Step 9: Verifying refresh token..."

  REFRESH_RESPONSE=$(curl -s -w "\n%{http_code}" \
    -X POST "${TENANT_BASE}/v1/tokens" \
    -u "${WEB_CLIENT_ID}:${WEB_CLIENT_SECRET}" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    --data-urlencode "grant_type=refresh_token" \
    --data-urlencode "refresh_token=${REFRESH_TOKEN}")

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
  echo "Step 9: Skipped (no refresh token issued)"
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
  echo "All checks passed! The example environment is working correctly."
  exit 0
else
  echo "Some checks failed. Review the output above for details."
  exit 1
fi
