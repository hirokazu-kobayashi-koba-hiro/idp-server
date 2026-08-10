#!/bin/bash
set -e

# CIBA (Client-Initiated Backchannel Authentication) - Verification Script
#
# Verifies the full production-like flow:
#   Phase 1: User registration + FIDO-UAF device registration + authorization code flow
#   Phase 2: CIBA flow with device_secret_jwt + FIDO-UAF authentication
#
# Prerequisites:
#   - setup.sh has been executed successfully
#   - Mockoon FIDO-UAF mock server is running (docker compose up -d mockoon)
#   - Generated config exists in config/generated/{organization-name}/
#
# Usage:
#   ./verify.sh
#   ./verify.sh --org my-organization

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../../.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"

# Parse arguments
ORGANIZATION_NAME="ciba"
while [ $# -gt 0 ]; do
  case "$1" in
    --org) ORGANIZATION_NAME="$2"; shift 2 ;;
    *) echo "Unknown option: $1"; exit 1 ;;
  esac
done

echo "=========================================="
echo "CIBA Use Case Verification"
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

# --- Helper: base64url encode ---
base64url_encode() {
  openssl base64 -e -A | tr '+/' '-_' | tr -d '='
}

# --- Helper: Create HS256 JWT ---
create_hs256_jwt() {
  local secret="$1"
  local payload_json="$2"

  local header='{"alg":"HS256","typ":"JWT"}'
  local header_b64=$(echo -n "${header}" | base64url_encode)
  local payload_b64=$(echo -n "${payload_json}" | base64url_encode)
  local unsigned="${header_b64}.${payload_b64}"
  local signature=$(echo -n "${unsigned}" | openssl dgst -sha256 -hmac "${secret}" -binary | base64url_encode)

  echo "${unsigned}.${signature}"
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
# Phase 1: User Registration + FIDO-UAF Device Registration
# ============================================================
echo "============================================"
echo "Phase 1: User Registration + Device Registration"
echo "============================================"
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

  # CIBA-specific discovery checks
  echo ""
  echo "  CIBA Discovery Checks:"

  BACKCHANNEL_EP=$(echo "${DISCOVERY_BODY}" | jq -r '.backchannel_authentication_endpoint')
  if [ -n "${BACKCHANNEL_EP}" ] && [ "${BACKCHANNEL_EP}" != "null" ]; then
    echo "  backchannel_authentication_endpoint: ${BACKCHANNEL_EP}"
    check_result "backchannel_endpoint" "true"
  else
    echo "  backchannel_authentication_endpoint: missing"
    check_result "backchannel_endpoint" "false"
  fi

  CIBA_GRANT=$(echo "${DISCOVERY_BODY}" | jq -r '.grant_types_supported | index("urn:openid:params:grant-type:ciba") != null')
  if [ "${CIBA_GRANT}" = "true" ]; then
    echo "  urn:openid:params:grant-type:ciba in grant_types_supported: yes"
    check_result "ciba_grant_type" "true"
  else
    echo "  urn:openid:params:grant-type:ciba in grant_types_supported: no"
    check_result "ciba_grant_type" "false"
  fi

  JWT_BEARER_GRANT=$(echo "${DISCOVERY_BODY}" | jq -r '.grant_types_supported | index("urn:ietf:params:oauth:grant-type:jwt-bearer") != null')
  if [ "${JWT_BEARER_GRANT}" = "true" ]; then
    echo "  urn:ietf:params:oauth:grant-type:jwt-bearer in grant_types_supported: yes"
    check_result "jwt_bearer_grant_type" "true"
  else
    echo "  urn:ietf:params:oauth:grant-type:jwt-bearer in grant_types_supported: no"
    check_result "jwt_bearer_grant_type" "false"
  fi

  POLL_MODE=$(echo "${DISCOVERY_BODY}" | jq -r '.backchannel_token_delivery_modes_supported | index("poll") != null')
  if [ "${POLL_MODE}" = "true" ]; then
    echo "  backchannel_token_delivery_modes_supported includes poll: yes"
    check_result "poll_delivery_mode" "true"
  else
    echo "  backchannel_token_delivery_modes_supported includes poll: no"
    check_result "poll_delivery_mode" "false"
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
SCOPE="openid profile email claims:authentication_devices"

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
  USER_SUB=$(echo "${REG_BODY}" | jq -r '.user.sub // .sub // empty')
  echo "  HTTP ${REG_HTTP} OK"
  echo "  User: ${TEST_EMAIL}"
  echo "  Sub:  ${USER_SUB}"
  check_result "registration" "true"
else
  echo "  HTTP ${REG_HTTP}"
  echo "  ${REG_BODY}" | jq '.' 2>/dev/null || echo "  ${REG_BODY}"
  check_result "registration" "false"
fi
echo ""

# ============================================================
# Step 4: FIDO-UAF Registration Challenge
# ============================================================
echo "Step 4: FIDO-UAF registration challenge..."

FIDO_CHALLENGE_RESPONSE=$(curl -s -w "\n%{http_code}" \
  -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/fido-uaf-registration-challenge" \
  -H "Content-Type: application/json" \
  -d "{
    \"app_name\": \"CIBA Test App\",
    \"platform\": \"iOS\",
    \"os\": \"iOS 18.0\",
    \"model\": \"iPhone15\"
  }")

FIDO_CHALLENGE_HTTP=$(echo "${FIDO_CHALLENGE_RESPONSE}" | tail -n1)
FIDO_CHALLENGE_BODY=$(echo "${FIDO_CHALLENGE_RESPONSE}" | sed '$d')

if [ "${FIDO_CHALLENGE_HTTP}" = "200" ]; then
  echo "  HTTP 200 OK"
  echo "  FIDO-UAF registration challenge received"
  check_result "fido_uaf_challenge" "true"
else
  echo "  HTTP ${FIDO_CHALLENGE_HTTP}"
  echo "  ${FIDO_CHALLENGE_BODY}" | jq '.' 2>/dev/null || echo "  ${FIDO_CHALLENGE_BODY}"
  check_result "fido_uaf_challenge" "false"
fi
echo ""

# ============================================================
# Step 5: FIDO-UAF Registration Complete
# ============================================================
echo "Step 5: FIDO-UAF registration complete..."

# Build uafResponse based on challenge response
UAF_REQUEST=$(echo "${FIDO_CHALLENGE_BODY}" | jq -r '.uafRequest // empty')
if [ -n "${UAF_REQUEST}" ] && [ "${UAF_REQUEST}" != "null" ]; then
  UAF_RESPONSE_BODY='{"uafResponse": [{"assertionScheme": "UAFV1TLV", "assertion": "mock_assertion_data"}]}'
else
  UAF_RESPONSE_BODY='{"uafResponse": []}'
fi

FIDO_REG_RESPONSE=$(curl -s -w "\n%{http_code}" \
  -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/fido-uaf-registration" \
  -H "Content-Type: application/json" \
  -d "${UAF_RESPONSE_BODY}")

FIDO_REG_HTTP=$(echo "${FIDO_REG_RESPONSE}" | tail -n1)
FIDO_REG_BODY=$(echo "${FIDO_REG_RESPONSE}" | sed '$d')

if [ "${FIDO_REG_HTTP}" = "200" ]; then
  DEVICE_ID=$(echo "${FIDO_REG_BODY}" | jq -r '.device_id')
  DEVICE_SECRET=$(echo "${FIDO_REG_BODY}" | jq -r '.device_secret')
  DEVICE_SECRET_ALGORITHM=$(echo "${FIDO_REG_BODY}" | jq -r '.device_secret_algorithm')
  DEVICE_SECRET_JWT_ISSUER=$(echo "${FIDO_REG_BODY}" | jq -r '.device_secret_jwt_issuer')

  echo "  HTTP 200 OK"
  echo "  Device ID:                ${DEVICE_ID}"
  echo "  Device Secret:            ${DEVICE_SECRET:0:10}..."
  echo "  Device Secret Algorithm:  ${DEVICE_SECRET_ALGORITHM}"
  echo "  Device Secret JWT Issuer: ${DEVICE_SECRET_JWT_ISSUER}"

  if [ -n "${DEVICE_ID}" ] && [ "${DEVICE_ID}" != "null" ] && \
     [ -n "${DEVICE_SECRET}" ] && [ "${DEVICE_SECRET}" != "null" ]; then
    check_result "fido_uaf_registration" "true"
  else
    echo "  Missing device_id or device_secret"
    check_result "fido_uaf_registration" "false"
  fi
else
  echo "  HTTP ${FIDO_REG_HTTP}"
  echo "  ${FIDO_REG_BODY}" | jq '.' 2>/dev/null || echo "  ${FIDO_REG_BODY}"
  check_result "fido_uaf_registration" "false"
fi
echo ""

# Save device credentials for Phase 2 and ciba-device-auth.sh
DEVICE_CREDENTIALS_FILE="${CONFIG_DIR}/device-credentials.json"
jq -n \
  --arg device_id "${DEVICE_ID}" \
  --arg device_secret "${DEVICE_SECRET}" \
  --arg device_secret_algorithm "${DEVICE_SECRET_ALGORITHM}" \
  --arg device_secret_jwt_issuer "${DEVICE_SECRET_JWT_ISSUER}" \
  --arg user_sub "${USER_SUB}" \
  --arg user_email "${TEST_EMAIL}" \
  --arg user_password "${TEST_PASSWORD}" \
  '{
    device_id: $device_id,
    device_secret: $device_secret,
    device_secret_algorithm: $device_secret_algorithm,
    device_secret_jwt_issuer: $device_secret_jwt_issuer,
    user_sub: $user_sub,
    user_email: $user_email,
    user_password: $user_password
  }' > "${DEVICE_CREDENTIALS_FILE}"
echo "  Saved device credentials: ${DEVICE_CREDENTIALS_FILE}"
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
# Step 7: Exchange Code for Tokens
# ============================================================
echo "Step 7: Exchanging authorization code for tokens..."

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

  echo "  HTTP 200 OK"
  echo "  Access Token:  $([ -n "${ACCESS_TOKEN}" ] && [ "${ACCESS_TOKEN}" != "null" ] && echo "${ACCESS_TOKEN:0:20}..." || echo "missing")"
  echo "  ID Token:      $([ -n "${ID_TOKEN}" ] && [ "${ID_TOKEN}" != "null" ] && echo "${ID_TOKEN:0:20}..." || echo "missing")"
  echo "  User and device persisted to DB"

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
# Step 8: Verify UserInfo (authentication_devices included)
# ============================================================
echo "Step 8: Verifying UserInfo contains authentication_devices..."

USERINFO_RESPONSE=$(curl -s -w "\n%{http_code}" \
  -X GET "${TENANT_BASE}/v1/userinfo" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}")

USERINFO_HTTP=$(echo "${USERINFO_RESPONSE}" | tail -n1)
USERINFO_BODY=$(echo "${USERINFO_RESPONSE}" | sed '$d')

if [ "${USERINFO_HTTP}" = "200" ]; then
  USERINFO_SUB=$(echo "${USERINFO_BODY}" | jq -r '.sub')
  USERINFO_EMAIL=$(echo "${USERINFO_BODY}" | jq -r '.email')
  USERINFO_DEVICES=$(echo "${USERINFO_BODY}" | jq -r '.authentication_devices')
  USERINFO_DEVICE_COUNT=$(echo "${USERINFO_BODY}" | jq -r '.authentication_devices | length // 0')

  echo "  HTTP 200 OK"
  echo "  sub:   ${USERINFO_SUB}"
  echo "  email: ${USERINFO_EMAIL:-null}"

  if [ -n "${USERINFO_DEVICES}" ] && [ "${USERINFO_DEVICES}" != "null" ] && [ "${USERINFO_DEVICE_COUNT}" -gt 0 ] 2>/dev/null; then
    echo "  authentication_devices: ${USERINFO_DEVICE_COUNT} device(s) registered"

    # Verify device_id matches
    USERINFO_DEVICE_ID=$(echo "${USERINFO_BODY}" | jq -r '.authentication_devices[0].id')
    if [ "${USERINFO_DEVICE_ID}" = "${DEVICE_ID}" ]; then
      echo "  Device ID matches: ${USERINFO_DEVICE_ID}"
    else
      echo "  Device ID mismatch: expected=${DEVICE_ID}, got=${USERINFO_DEVICE_ID}"
    fi

    # Security: device_secret must NOT be exposed
    DEVICE_SECRET_EXPOSED=$(echo "${USERINFO_BODY}" | jq -r '.authentication_devices[0].credential_payload // empty')
    if [ -z "${DEVICE_SECRET_EXPOSED}" ] || [ "${DEVICE_SECRET_EXPOSED}" = "null" ]; then
      echo "  Security: credential_payload not exposed (OK)"
    else
      echo "  Security WARNING: credential_payload exposed in UserInfo!"
    fi

    check_result "userinfo_devices" "true"
  else
    echo "  authentication_devices: missing or empty"
    check_result "userinfo_devices" "false"
  fi
else
  echo "  HTTP ${USERINFO_HTTP}"
  echo "  ${USERINFO_BODY}" | jq '.' 2>/dev/null || echo "  ${USERINFO_BODY}"
  check_result "userinfo_devices" "false"
fi
echo ""

# ============================================================
# Phase 2: CIBA Flow
# ============================================================
echo "============================================"
echo "Phase 2: CIBA Flow"
echo "============================================"
echo ""

# ============================================================
# Step 9: CIBA Backchannel Authentication Request
# ============================================================
echo "Step 9: CIBA backchannel authentication request..."

CIBA_RESPONSE=$(curl -s -w "\n%{http_code}" \
  -X POST "${TENANT_BASE}/v1/backchannel/authentications" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "scope=openid profile email" \
  --data-urlencode "login_hint=device:${DEVICE_ID},idp:idp-server" \
  --data-urlencode "binding_message=CIBA-VERIFY" \
  --data-urlencode "user_code=${TEST_PASSWORD}" \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode "client_secret=${CLIENT_SECRET}")

CIBA_HTTP=$(echo "${CIBA_RESPONSE}" | tail -n1)
CIBA_BODY=$(echo "${CIBA_RESPONSE}" | sed '$d')

if [ "${CIBA_HTTP}" = "200" ]; then
  AUTH_REQ_ID=$(echo "${CIBA_BODY}" | jq -r '.auth_req_id')
  CIBA_EXPIRES_IN=$(echo "${CIBA_BODY}" | jq -r '.expires_in')
  CIBA_INTERVAL=$(echo "${CIBA_BODY}" | jq -r '.interval')

  echo "  HTTP 200 OK"
  echo "  Auth Request ID: ${AUTH_REQ_ID}"
  echo "  Expires In:      ${CIBA_EXPIRES_IN}s"
  echo "  Interval:        ${CIBA_INTERVAL}s"

  if [ -n "${AUTH_REQ_ID}" ] && [ "${AUTH_REQ_ID}" != "null" ]; then
    check_result "ciba_request" "true"
  else
    check_result "ciba_request" "false"
  fi
else
  echo "  HTTP ${CIBA_HTTP}"
  echo "  ${CIBA_BODY}" | jq '.' 2>/dev/null || echo "  ${CIBA_BODY}"
  check_result "ciba_request" "false"
fi
echo ""

# ============================================================
# Step 10: CIBA Token Polling (before authentication - expect authorization_pending)
# ============================================================
echo "Step 10: CIBA token polling (before authentication)..."

CIBA_POLL_RESPONSE=$(curl -s -w "\n%{http_code}" \
  -X POST "${TENANT_BASE}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=urn:openid:params:grant-type:ciba" \
  --data-urlencode "auth_req_id=${AUTH_REQ_ID}" \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode "client_secret=${CLIENT_SECRET}")

CIBA_POLL_HTTP=$(echo "${CIBA_POLL_RESPONSE}" | tail -n1)
CIBA_POLL_BODY=$(echo "${CIBA_POLL_RESPONSE}" | sed '$d')
CIBA_POLL_ERROR=$(echo "${CIBA_POLL_BODY}" | jq -r '.error // empty')

if [ "${CIBA_POLL_ERROR}" = "authorization_pending" ]; then
  echo "  HTTP ${CIBA_POLL_HTTP}"
  echo "  error: authorization_pending (expected)"
  echo "  Poll mode working correctly - authentication not yet completed"
  check_result "ciba_poll_pending" "true"
else
  echo "  HTTP ${CIBA_POLL_HTTP}"
  echo "  Expected error=authorization_pending, got: ${CIBA_POLL_ERROR:-none}"
  echo "  ${CIBA_POLL_BODY}" | jq '.' 2>/dev/null || echo "  ${CIBA_POLL_BODY}"
  check_result "ciba_poll_pending" "false"
fi
echo ""

# ============================================================
# Step 11: Get Authentication Transaction (device_secret_jwt Bearer)
# ============================================================
echo "Step 11: Getting authentication transaction (device_secret_jwt)..."

ISSUER="${TENANT_BASE}"
NOW=$(date +%s)
EXP=$((NOW + 3600))
JTI="jti-$(date +%s)-${RANDOM}"

JWT_PAYLOAD=$(jq -n \
  --arg iss "${DEVICE_SECRET_JWT_ISSUER}" \
  --arg sub "${USER_SUB}" \
  --arg aud "${ISSUER}" \
  --arg jti "${JTI}" \
  --argjson exp "${EXP}" \
  --argjson iat "${NOW}" \
  '{iss: $iss, sub: $sub, aud: $aud, jti: $jti, exp: $exp, iat: $iat}')

DEVICE_JWT=$(create_hs256_jwt "${DEVICE_SECRET}" "${JWT_PAYLOAD}")

TRANSACTION_RESPONSE=$(curl -s -w "\n%{http_code}" -X GET \
  "${TENANT_BASE}/v1/authentication-devices/${DEVICE_ID}/authentications?attributes.auth_req_id=${AUTH_REQ_ID}" \
  -H "Authorization: Bearer ${DEVICE_JWT}")

TX_HTTP=$(echo "${TRANSACTION_RESPONSE}" | tail -n1)
TX_BODY=$(echo "${TRANSACTION_RESPONSE}" | sed '$d')

if [ "${TX_HTTP}" = "200" ]; then
  TRANSACTION_ID=$(echo "${TX_BODY}" | jq -r '.list[0].id')
  TX_FLOW=$(echo "${TX_BODY}" | jq -r '.list[0].flow')

  echo "  HTTP 200 OK"
  echo "  Transaction ID: ${TRANSACTION_ID}"
  echo "  Flow: ${TX_FLOW}"

  if [ -n "${TRANSACTION_ID}" ] && [ "${TRANSACTION_ID}" != "null" ]; then
    check_result "device_transaction" "true"
  else
    echo "  No authentication transaction found"
    check_result "device_transaction" "false"
  fi
else
  echo "  HTTP ${TX_HTTP}"
  echo "  ${TX_BODY}" | jq '.' 2>/dev/null || echo "  ${TX_BODY}"
  check_result "device_transaction" "false"
fi
echo ""

# ============================================================
# Step 12: FIDO-UAF Authentication Challenge
# ============================================================
echo "Step 12: FIDO-UAF authentication challenge..."

FIDO_AUTH_CHALLENGE_RESPONSE=$(curl -s -w "\n%{http_code}" \
  -X POST "${TENANT_BASE}/v1/authentications/${TRANSACTION_ID}/fido-uaf-authentication-challenge" \
  -H "Content-Type: application/json" \
  -d "{
    \"device_id\": \"${DEVICE_ID}\"
  }")

FIDO_AUTH_CHALLENGE_HTTP=$(echo "${FIDO_AUTH_CHALLENGE_RESPONSE}" | tail -n1)
FIDO_AUTH_CHALLENGE_BODY=$(echo "${FIDO_AUTH_CHALLENGE_RESPONSE}" | sed '$d')

if [ "${FIDO_AUTH_CHALLENGE_HTTP}" = "200" ]; then
  echo "  HTTP 200 OK"
  echo "  FIDO-UAF authentication challenge received"
  check_result "fido_uaf_auth_challenge" "true"
else
  echo "  HTTP ${FIDO_AUTH_CHALLENGE_HTTP}"
  echo "  ${FIDO_AUTH_CHALLENGE_BODY}" | jq '.' 2>/dev/null || echo "  ${FIDO_AUTH_CHALLENGE_BODY}"
  check_result "fido_uaf_auth_challenge" "false"
fi
echo ""

# ============================================================
# Step 13: FIDO-UAF Authentication Complete
# ============================================================
echo "Step 13: FIDO-UAF authentication complete..."

AUTH_UAF_REQUEST=$(echo "${FIDO_AUTH_CHALLENGE_BODY}" | jq -r '.uafRequest // empty')
if [ -n "${AUTH_UAF_REQUEST}" ] && [ "${AUTH_UAF_REQUEST}" != "null" ]; then
  AUTH_UAF_BODY="{\"device_id\": \"${DEVICE_ID}\", \"uafResponse\": [{\"assertionScheme\": \"UAFV1TLV\", \"assertion\": \"mock_assertion_data\"}]}"
else
  AUTH_UAF_BODY="{\"device_id\": \"${DEVICE_ID}\", \"uafResponse\": []}"
fi

FIDO_AUTH_RESPONSE=$(curl -s -w "\n%{http_code}" \
  -X POST "${TENANT_BASE}/v1/authentications/${TRANSACTION_ID}/fido-uaf-authentication" \
  -H "Content-Type: application/json" \
  -d "${AUTH_UAF_BODY}")

FIDO_AUTH_HTTP=$(echo "${FIDO_AUTH_RESPONSE}" | tail -n1)
FIDO_AUTH_BODY=$(echo "${FIDO_AUTH_RESPONSE}" | sed '$d')

if [ "${FIDO_AUTH_HTTP}" = "200" ]; then
  echo "  HTTP 200 OK"
  echo "  FIDO-UAF authentication completed"
  check_result "fido_uaf_authentication" "true"
else
  echo "  HTTP ${FIDO_AUTH_HTTP}"
  echo "  ${FIDO_AUTH_BODY}" | jq '.' 2>/dev/null || echo "  ${FIDO_AUTH_BODY}"
  check_result "fido_uaf_authentication" "false"
fi
echo ""

# ============================================================
# Step 14: CIBA Token (after authentication)
# ============================================================
echo "Step 14: Getting CIBA token (after authentication)..."

CIBA_TOKEN_RESPONSE=$(curl -s -w "\n%{http_code}" \
  -X POST "${TENANT_BASE}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=urn:openid:params:grant-type:ciba" \
  --data-urlencode "auth_req_id=${AUTH_REQ_ID}" \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode "client_secret=${CLIENT_SECRET}")

CIBA_TOKEN_HTTP=$(echo "${CIBA_TOKEN_RESPONSE}" | tail -n1)
CIBA_TOKEN_BODY=$(echo "${CIBA_TOKEN_RESPONSE}" | sed '$d')

if [ "${CIBA_TOKEN_HTTP}" = "200" ]; then
  CIBA_ACCESS_TOKEN=$(echo "${CIBA_TOKEN_BODY}" | jq -r '.access_token')
  CIBA_ID_TOKEN=$(echo "${CIBA_TOKEN_BODY}" | jq -r '.id_token')

  echo "  HTTP 200 OK"
  echo "  Access Token: $([ -n "${CIBA_ACCESS_TOKEN}" ] && [ "${CIBA_ACCESS_TOKEN}" != "null" ] && echo "${CIBA_ACCESS_TOKEN:0:20}..." || echo "missing")"
  echo "  ID Token:     $([ -n "${CIBA_ID_TOKEN}" ] && [ "${CIBA_ID_TOKEN}" != "null" ] && echo "${CIBA_ID_TOKEN:0:20}..." || echo "missing")"

  if [ -n "${CIBA_ACCESS_TOKEN}" ] && [ "${CIBA_ACCESS_TOKEN}" != "null" ]; then
    check_result "ciba_token" "true"
  else
    check_result "ciba_token" "false"
  fi
else
  echo "  HTTP ${CIBA_TOKEN_HTTP}"
  echo "  ${CIBA_TOKEN_BODY}" | jq '.' 2>/dev/null || echo "  ${CIBA_TOKEN_BODY}"
  check_result "ciba_token" "false"
fi
echo ""

# ============================================================
# Step 15: Verify UserInfo
# ============================================================
echo "Step 15: Verifying UserInfo with CIBA token..."

USERINFO_RESPONSE=$(curl -s -w "\n%{http_code}" \
  -X GET "${TENANT_BASE}/v1/userinfo" \
  -H "Authorization: Bearer ${CIBA_ACCESS_TOKEN}")

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
  echo "All checks passed! The CIBA flow with FIDO-UAF is working correctly."
  echo ""
  echo "Device credentials saved to: ${DEVICE_CREDENTIALS_FILE}"
  echo "You can now run: ./ciba-device-auth.sh"
  exit 0
else
  echo "Some checks failed. Review the output above for details."
  exit 1
fi
