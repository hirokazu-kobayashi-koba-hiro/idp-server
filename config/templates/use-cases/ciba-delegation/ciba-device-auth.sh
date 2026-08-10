#!/bin/bash
set -e

# CIBA Device Authentication Script
# This script simulates device-side authentication for CIBA flow using
# device_secret_jwt + FIDO-UAF authentication.
#
# Prerequisites:
#   - verify.sh has been run (creates device-credentials.json with device_id, device_secret, etc.)
#   - Mockoon FIDO-UAF mock server is running
#
# Usage:
#   ./ciba-device-auth.sh                    # Approve authentication (FIDO-UAF)
#   ./ciba-device-auth.sh cancel             # Cancel/deny authentication

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../../.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"

# Parse action argument
ACTION="${1:-approve}"

ORGANIZATION_NAME="${ORGANIZATION_NAME:-ciba}"
CONFIG_DIR="${PROJECT_ROOT}/config/generated/${ORGANIZATION_NAME}"

if [ "${ACTION}" = "cancel" ]; then
  echo "=========================================="
  echo "CIBA Device Authentication - CANCEL"
  echo "=========================================="
else
  echo "=========================================="
  echo "CIBA Device Authentication - APPROVE"
  echo "=========================================="
fi

# Load .env file
if [ -f "${ENV_FILE}" ]; then
  echo "Loading environment variables from .env..."
  set -a
  source "${ENV_FILE}"
  set +a
fi

: "${AUTHORIZATION_SERVER_URL:?AUTHORIZATION_SERVER_URL is required in .env}"

# Load generated config
if [ ! -d "${CONFIG_DIR}" ]; then
  echo "Error: Generated config not found at ${CONFIG_DIR}"
  echo "Run setup.sh first."
  exit 1
fi

# Load device credentials (created by verify.sh)
DEVICE_CREDENTIALS_FILE="${CONFIG_DIR}/device-credentials.json"
if [ ! -f "${DEVICE_CREDENTIALS_FILE}" ]; then
  echo "Error: Device credentials not found at ${DEVICE_CREDENTIALS_FILE}"
  echo "Run verify.sh first to register a device via FIDO-UAF."
  exit 1
fi

TENANT_ID=$(jq -r '.tenant.id' "${CONFIG_DIR}/public-tenant.json")
DEVICE_ID=$(jq -r '.device_id' "${DEVICE_CREDENTIALS_FILE}")
DEVICE_SECRET=$(jq -r '.device_secret' "${DEVICE_CREDENTIALS_FILE}")
DEVICE_SECRET_JWT_ISSUER=$(jq -r '.device_secret_jwt_issuer' "${DEVICE_CREDENTIALS_FILE}")
USER_SUB=$(jq -r '.user_sub' "${DEVICE_CREDENTIALS_FILE}")

TENANT_BASE="${AUTHORIZATION_SERVER_URL}/${TENANT_ID}"

echo "   Server:    ${AUTHORIZATION_SERVER_URL}"
echo "   Tenant:    ${TENANT_ID}"
echo "   Device:    ${DEVICE_ID}"
echo "   User Sub:  ${USER_SUB}"
echo "   Action:    ${ACTION}"
echo ""

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

# Step 1: Create device_secret_jwt
echo "Step 1: Creating device_secret_jwt..."

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
echo "  JWT created: ${DEVICE_JWT:0:30}..."
echo ""

# Step 2: Get authentication transaction
echo "Step 2: Getting authentication transaction..."

TRANSACTION_RESPONSE=$(curl -s -w "\n%{http_code}" -X GET \
  "${TENANT_BASE}/v1/authentication-devices/${DEVICE_ID}/authentications" \
  -H "Authorization: Bearer ${DEVICE_JWT}")

HTTP_CODE=$(echo "${TRANSACTION_RESPONSE}" | tail -n1)
RESPONSE_BODY=$(echo "${TRANSACTION_RESPONSE}" | sed '$d')

if [ "${HTTP_CODE}" != "200" ]; then
  echo "Failed to get authentication transaction (HTTP ${HTTP_CODE})"
  echo "Response: ${RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "${RESPONSE_BODY}"
  exit 1
fi

echo "Authentication transaction retrieved"
echo "${RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "${RESPONSE_BODY}"
echo ""

# Extract transaction details
TRANSACTION_ID=$(echo "${RESPONSE_BODY}" | jq -r '.list[0].id')

if [ -z "${TRANSACTION_ID}" ] || [ "${TRANSACTION_ID}" = "null" ]; then
  echo "No authentication transaction found"
  exit 1
fi

echo "  Transaction ID: ${TRANSACTION_ID}"
echo ""

# Step 3: Execute action based on argument
if [ "${ACTION}" = "cancel" ]; then
  echo "Step 3: Cancelling authentication..."

  CANCEL_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
    "${TENANT_BASE}/v1/authentications/${TRANSACTION_ID}/authentication-cancel" \
    -H "Content-Type: application/json")

  CANCEL_HTTP_CODE=$(echo "${CANCEL_RESPONSE}" | tail -n1)
  CANCEL_RESPONSE_BODY=$(echo "${CANCEL_RESPONSE}" | sed '$d')

  if [ "${CANCEL_HTTP_CODE}" = "200" ]; then
    echo "Authentication cancelled successfully!"
    echo "${CANCEL_RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "${CANCEL_RESPONSE_BODY}"
  else
    echo "Cancel failed (HTTP ${CANCEL_HTTP_CODE})"
    echo "Response: ${CANCEL_RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "${CANCEL_RESPONSE_BODY}"
    exit 1
  fi

  echo ""
  echo "=========================================="
  echo "CIBA Authentication Cancelled!"
  echo "=========================================="
  echo ""
  echo "The client will receive 'access_denied' error when polling."
  echo ""

else
  # Step 3a: FIDO-UAF authentication challenge
  echo "Step 3a: FIDO-UAF authentication challenge..."

  FIDO_CHALLENGE_RESPONSE=$(curl -s -w "\n%{http_code}" \
    -X POST "${TENANT_BASE}/v1/authentications/${TRANSACTION_ID}/fido-uaf-authentication-challenge" \
    -H "Content-Type: application/json" \
    -d "{
      \"device_id\": \"${DEVICE_ID}\"
    }")

  FIDO_CHALLENGE_HTTP=$(echo "${FIDO_CHALLENGE_RESPONSE}" | tail -n1)
  FIDO_CHALLENGE_BODY=$(echo "${FIDO_CHALLENGE_RESPONSE}" | sed '$d')

  if [ "${FIDO_CHALLENGE_HTTP}" != "200" ]; then
    echo "FIDO-UAF authentication challenge failed (HTTP ${FIDO_CHALLENGE_HTTP})"
    echo "Response: ${FIDO_CHALLENGE_BODY}" | jq '.' 2>/dev/null || echo "${FIDO_CHALLENGE_BODY}"
    exit 1
  fi

  echo "  FIDO-UAF authentication challenge received"
  echo ""

  # Step 3b: FIDO-UAF authentication complete
  echo "Step 3b: FIDO-UAF authentication complete..."

  UAF_REQUEST=$(echo "${FIDO_CHALLENGE_BODY}" | jq -r '.uafRequest // empty')
  if [ -n "${UAF_REQUEST}" ] && [ "${UAF_REQUEST}" != "null" ]; then
    AUTH_BODY="{\"device_id\": \"${DEVICE_ID}\", \"uafResponse\": [{\"assertionScheme\": \"UAFV1TLV\", \"assertion\": \"mock_assertion_data\"}]}"
  else
    AUTH_BODY="{\"device_id\": \"${DEVICE_ID}\", \"uafResponse\": []}"
  fi

  AUTH_RESPONSE=$(curl -s -w "\n%{http_code}" \
    -X POST "${TENANT_BASE}/v1/authentications/${TRANSACTION_ID}/fido-uaf-authentication" \
    -H "Content-Type: application/json" \
    -d "${AUTH_BODY}")

  AUTH_HTTP_CODE=$(echo "${AUTH_RESPONSE}" | tail -n1)
  AUTH_RESPONSE_BODY=$(echo "${AUTH_RESPONSE}" | sed '$d')

  if [ "${AUTH_HTTP_CODE}" = "200" ]; then
    echo "FIDO-UAF authentication successful!"
    echo "${AUTH_RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "${AUTH_RESPONSE_BODY}"
  else
    echo "FIDO-UAF authentication failed (HTTP ${AUTH_HTTP_CODE})"
    echo "Response: ${AUTH_RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "${AUTH_RESPONSE_BODY}"
    exit 1
  fi

  echo ""
  echo "=========================================="
  echo "CIBA Device Authentication Complete!"
  echo "=========================================="
  echo ""
  echo "The client can now poll the token endpoint to get the access token."
  echo ""
fi
