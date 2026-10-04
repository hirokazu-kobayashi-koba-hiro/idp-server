#!/bin/bash
set -e

# Attribute Verification Example - Verification Script
#
# Verifies that the example environment works correctly by performing:
#   1. view-data carries the step hints for both named interactions
#   2. An account that is not identity-verified is stopped right after sign-in
#   3. An identity-verified account passes the account check and the entered-value check,
#      the authorization completes, and attribute verification is not reported in amr
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
echo "Attribute Verification Example Verification"
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

PUBLIC_TENANT_ID=$(jq -r '.tenant.id' "${SCRIPT_DIR}/public-tenant-request.json")
CLIENT_ID=$(jq -r '.client_id' "${SCRIPT_DIR}/client-request.json")
CLIENT_SECRET=$(jq -r '.client_secret' "${SCRIPT_DIR}/client-request.json")
REDIRECT_URI=$(jq -r '.redirect_uris[0]' "${SCRIPT_DIR}/client-request.json")
USERS_FILE="${SCRIPT_DIR}/users.json"
VERIFIED_EMAIL=$(jq -r '.[] | select(.status_after_creation == "IDENTITY_VERIFIED") | .email' "${USERS_FILE}")
VERIFIED_PASSWORD=$(jq -r '.[] | select(.status_after_creation == "IDENTITY_VERIFIED") | .raw_password' "${USERS_FILE}")
VERIFIED_BIRTHDATE=$(jq -r '.[] | select(.status_after_creation == "IDENTITY_VERIFIED") | .birthdate' "${USERS_FILE}")
VERIFIED_PHONE=$(jq -r '.[] | select(.status_after_creation == "IDENTITY_VERIFIED") | .phone_number' "${USERS_FILE}")
UNVERIFIED_EMAIL=$(jq -r '.[] | select(.status_after_creation == "REGISTERED") | .email' "${USERS_FILE}")
UNVERIFIED_PASSWORD=$(jq -r '.[] | select(.status_after_creation == "REGISTERED") | .raw_password' "${USERS_FILE}")

TENANT_BASE="${AUTHORIZATION_SERVER_URL}/${PUBLIC_TENANT_ID}"

PASS_COUNT=0
FAIL_COUNT=0
COOKIE_JAR=$(mktemp)
trap "rm -f ${COOKIE_JAR}" EXIT

check() {
  local label="$1"
  local condition="$2"
  if [ "${condition}" = "true" ]; then
    echo "  PASS: ${label}"
    PASS_COUNT=$((PASS_COUNT + 1))
  else
    echo "  FAIL: ${label}"
    FAIL_COUNT=$((FAIL_COUNT + 1))
  fi
}

# Starts an authorization request and prints its id. The cookie jar keeps the browser binding.
start_authorization() {
  rm -f "${COOKIE_JAR}"
  local location
  location=$(curl -s -c "${COOKIE_JAR}" -o /dev/null -w "%{redirect_url}" \
    "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=${REDIRECT_URI}&scope=openid%20profile%20email&state=verify-$(date +%s)&prompt=login")
  echo "${location}" | sed -n 's/.*[?&]id=\([^&#]*\).*/\1/p'
}

# Posts to an interaction and prints "<http status> <error>".
interact() {
  local id="$1"
  local type="$2"
  local body="$3"
  local response
  response=$(curl -s -b "${COOKIE_JAR}" -w "\n%{http_code}" -X POST \
    "${TENANT_BASE}/v1/authorizations/${id}/${type}" \
    -H "Content-Type: application/json" -d "${body}")
  echo "$(echo "${response}" | tail -n1) $(echo "${response}" | sed '$d' | jq -r '.error // ""' 2>/dev/null)"
}

# ============================================================
# Step 1: view-data hints
# ============================================================
echo "Step 1: view-data carries the step hints..."
AUTH_ID=$(start_authorization)
HINTS=$(curl -s -b "${COOKIE_JAR}" "${TENANT_BASE}/v1/authorizations/${AUTH_ID}/view-data" \
  | jq -c '.authentication_step_hints["attribute-verification"].interactions')
check "identity-verified is a conditions check" \
  "$([ "$(echo "${HINTS}" | jq -r '.["identity-verified"].kind')" = "conditions" ] && echo true || echo false)"
check "kba asks for two inputs" \
  "$([ "$(echo "${HINTS}" | jq -r '.kba.inputs | length')" = "2" ] && echo true || echo false)"
echo ""

# ============================================================
# Step 2: An account that is not identity-verified
# ============================================================
echo "Step 2: Not identity-verified: stopped right after sign-in..."
AUTH_ID=$(start_authorization)
RESULT=$(interact "${AUTH_ID}" password-authentication \
  "{\"username\":\"${UNVERIFIED_EMAIL}\",\"password\":\"${UNVERIFIED_PASSWORD}\"}")
check "password accepted" "$([ "${RESULT%% *}" = "200" ] && echo true || echo false)"
RESULT=$(interact "${AUTH_ID}" attribute-verification '{"interaction":"identity-verified"}')
check "identity_verification_required" \
  "$([ "${RESULT}" = "400 identity_verification_required" ] && echo true || echo false)"
echo ""

# ============================================================
# Step 3: An identity-verified account
# ============================================================
echo "Step 3: Identity-verified: account check, entered values, authorize, token..."
AUTH_ID=$(start_authorization)
RESULT=$(interact "${AUTH_ID}" password-authentication \
  "{\"username\":\"${VERIFIED_EMAIL}\",\"password\":\"${VERIFIED_PASSWORD}\"}")
check "password accepted" "$([ "${RESULT%% *}" = "200" ] && echo true || echo false)"
RESULT=$(interact "${AUTH_ID}" attribute-verification '{"interaction":"identity-verified"}')
check "account check passed" "$([ "${RESULT%% *}" = "200" ] && echo true || echo false)"
PHONE_LAST4="${VERIFIED_PHONE: -4}"
RESULT=$(interact "${AUTH_ID}" attribute-verification \
  "{\"interaction\":\"kba\",\"birthdate\":\"${VERIFIED_BIRTHDATE}\",\"phone_last4\":\"${PHONE_LAST4}\"}")
check "entered values matched" "$([ "${RESULT%% *}" = "200" ] && echo true || echo false)"

AUTHORIZE_RESPONSE=$(curl -s -b "${COOKIE_JAR}" -X POST \
  "${TENANT_BASE}/v1/authorizations/${AUTH_ID}/authorize" -H "Content-Type: application/json" -d '{}')
CODE=$(echo "${AUTHORIZE_RESPONSE}" | jq -r '.redirect_uri // ""' | sed -n 's/.*[?&]code=\([^&#]*\).*/\1/p')
check "authorization code issued" "$([ -n "${CODE}" ] && echo true || echo false)"

TOKEN_RESPONSE=$(curl -s -X POST "${TENANT_BASE}/v1/tokens" \
  --data-urlencode "grant_type=authorization_code" \
  --data-urlencode "code=${CODE}" \
  --data-urlencode "redirect_uri=${REDIRECT_URI}" \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode "client_secret=${CLIENT_SECRET}")
ID_TOKEN=$(echo "${TOKEN_RESPONSE}" | jq -r '.id_token // ""')
check "tokens issued" "$([ -n "${ID_TOKEN}" ] && echo true || echo false)"
AMR=$(echo "${ID_TOKEN}" | cut -d. -f2 | python3 -c 'import base64,sys,json;s=sys.stdin.read().strip();print(json.dumps(json.loads(base64.urlsafe_b64decode(s+"="*(-len(s)%4))).get("amr",[])))')
check "amr does not include attribute-verification (${AMR})" \
  "$(echo "${AMR}" | jq -e 'index("attribute-verification") == null' >/dev/null && echo true || echo false)"
echo ""

TOTAL=$((PASS_COUNT + FAIL_COUNT))
echo "=========================================="
echo "Verification Summary"
echo "=========================================="
echo "  Passed: ${PASS_COUNT} / ${TOTAL}"
echo "  Failed: ${FAIL_COUNT} / ${TOTAL}"
echo ""
if [ "${FAIL_COUNT}" -eq 0 ]; then
  echo "All checks passed."
  exit 0
else
  echo "Some checks failed."
  exit 1
fi
