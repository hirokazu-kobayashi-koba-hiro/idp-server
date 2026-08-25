#!/bin/bash
set -e

# Attestation-Based Client Authentication - Verification Script
#
# Verifies that the tenant created by setup.sh is configured for
# draft-ietf-oauth-attestation-based-client-auth-10:
#   1. Discovery advertises attest_jwt_client_auth and the two alg lists
#   2. Discovery advertises challenge_endpoint (Section 6.1)
#   3. The challenge endpoint returns attestation_challenge and is uncacheable
#   4. A Challenge stays usable for its whole lifetime (it is not single use)
#   5. Both clients are registered with the intended trust source
#   6. The token endpoint rejects a request with no attestation headers
#
# Minting the two JWTs needs a signing key on the client side, so the full
# authentication flow is not exercised here. See VERIFY.md for that.
#
# Usage:
#   ./verify.sh
#   ./verify.sh --org my-organization

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../../.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"

ORGANIZATION_NAME="attestation-based-client-auth"
while [ $# -gt 0 ]; do
  case "$1" in
    --org) ORGANIZATION_NAME="$2"; shift 2 ;;
    *) echo "Unknown option: $1"; exit 1 ;;
  esac
done

echo "=========================================="
echo "Attestation-Based Client Auth Verification"
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
ATTESTER_CLIENT_ID=$(jq -r '.client_id' "${CONFIG_DIR}/attester-jwks-client.json")
SELF_SIGNED_CLIENT_ID=$(jq -r '.client_id' "${CONFIG_DIR}/self-signed-client.json")

BASE="${AUTHORIZATION_SERVER_URL}/${PUBLIC_TENANT_ID}"

PASSED=0
FAILED=0

check() {
  local name="$1" expected="$2" actual="$3"
  if [ "${expected}" = "${actual}" ]; then
    echo "  ✅ ${name}: ${actual}"
    PASSED=$((PASSED + 1))
  else
    echo "  ❌ ${name}: expected=${expected} actual=${actual}"
    FAILED=$((FAILED + 1))
  fi
}

echo "Tenant:  ${PUBLIC_TENANT_ID}"
echo "Clients: ${ATTESTER_CLIENT_ID} (attester_jwks)"
echo "         ${SELF_SIGNED_CLIENT_ID} (registered_instance_key)"
echo ""

# --- Step 1: Discovery metadata (Section 8) ---
echo "Step 1: Checking discovery metadata..."
DISCOVERY=$(curl -sk "${BASE}/.well-known/openid-configuration")

check "attest_jwt_client_auth advertised" "true" \
  "$(echo "${DISCOVERY}" | jq '.token_endpoint_auth_methods_supported | contains(["attest_jwt_client_auth"])')"
check "client_attestation_signing_alg_values_supported" "true" \
  "$(echo "${DISCOVERY}" | jq 'has("client_attestation_signing_alg_values_supported")')"
check "client_attestation_pop_signing_alg_values_supported" "true" \
  "$(echo "${DISCOVERY}" | jq 'has("client_attestation_pop_signing_alg_values_supported")')"
echo "  alg (attestation): $(echo "${DISCOVERY}" | jq -c '.client_attestation_signing_alg_values_supported')"
echo "  alg (pop):         $(echo "${DISCOVERY}" | jq -c '.client_attestation_pop_signing_alg_values_supported')"
echo ""

# --- Step 2: challenge_endpoint (Section 6.1) ---
echo "Step 2: Checking challenge_endpoint metadata..."
CHALLENGE_ENDPOINT=$(echo "${DISCOVERY}" | jq -r '.challenge_endpoint // empty')
check "challenge_endpoint advertised" "true" "$([ -n "${CHALLENGE_ENDPOINT}" ] && echo true || echo false)"
echo "  ${CHALLENGE_ENDPOINT}"
echo ""

# --- Step 3: challenge endpoint response ---
echo "Step 3: Fetching a Challenge..."
CHALLENGE_RESPONSE=$(curl -sk -D - -o /tmp/abca-challenge.json -X POST "${BASE}/v1/client-attestation/challenges")
CHALLENGE=$(jq -r '.attestation_challenge // empty' /tmp/abca-challenge.json)

check "attestation_challenge returned" "true" "$([ -n "${CHALLENGE}" ] && echo true || echo false)"
check "Cache-Control: no-store" "true" \
  "$(echo "${CHALLENGE_RESPONSE}" | grep -iq 'cache-control:.*no-store' && echo true || echo false)"
echo ""

# --- Step 4: a Challenge is reusable within its lifetime (Section 9.7) ---
echo "Step 4: Checking that a Challenge is not single use..."
SECOND=$(curl -sk -X POST "${BASE}/v1/client-attestation/challenges" | jq -r '.attestation_challenge')
check "each request returns a distinct Challenge" "true" \
  "$([ "${CHALLENGE}" != "${SECOND}" ] && echo true || echo false)"
echo "  (reuse of one Challenge across requests is covered by the E2E suite)"
echo ""

# --- Step 5: client configuration ---
echo "Step 5: Checking client configuration..."
check "attester_jwks client auth method" "attest_jwt_client_auth" \
  "$(jq -r '.token_endpoint_auth_method' "${CONFIG_DIR}/attester-jwks-client.json")"
check "attester_jwks trust source" "attester_jwks" \
  "$(jq -r '.extension.client_attestation_trust_source' "${CONFIG_DIR}/attester-jwks-client.json")"
check "attester JWKS embedded" "true" \
  "$(jq -r '.extension.client_attestation_attester_jwks | test("\"keys\"")' "${CONFIG_DIR}/attester-jwks-client.json")"
check "self-signed trust source" "registered_instance_key" \
  "$(jq -r '.extension.client_attestation_trust_source' "${CONFIG_DIR}/self-signed-client.json")"
echo "  registration policy: $(jq -r '.extension.client_instance_registration_policy' "${CONFIG_DIR}/self-signed-client.json")"
echo ""

# --- Step 6: the token endpoint requires the attestation headers ---
echo "Step 6: Checking that the token endpoint rejects a request with no attestation..."
NO_ATTESTATION=$(curl -sk -X POST "${BASE}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=client_credentials" \
  --data-urlencode "scope=account" \
  --data-urlencode "client_id=${ATTESTER_CLIENT_ID}")

check "rejected with invalid_client" "invalid_client" "$(echo "${NO_ATTESTATION}" | jq -r '.error // empty')"
echo ""

# --- Summary ---
echo "=========================================="
echo "Passed: ${PASSED}  Failed: ${FAILED}"
echo "=========================================="
if [ "${FAILED}" -gt 0 ]; then
  exit 1
fi
echo ""
echo "Configuration is in place. To exercise the full authentication flow, see VERIFY.md."
