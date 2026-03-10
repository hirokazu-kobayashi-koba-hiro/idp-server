#!/bin/bash
set -e

# Financial-Grade - CIBA Device Authentication Script
# Simulates the user's device-side authentication for CIBA flow.
#
# Usage:
#   ./ciba-device-auth.sh                    # Approve authentication (password auth)
#   ./ciba-device-auth.sh cancel             # Cancel/deny authentication
#   ORGANIZATION_NAME=my-org ./ciba-device-auth.sh

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../../.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"

# Parse action argument
ACTION="${1:-approve}"
ORGANIZATION_NAME="${ORGANIZATION_NAME:-financial-grade}"

OUTPUT_DIR="${PROJECT_ROOT}/config/generated/${ORGANIZATION_NAME}"

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
  set -a
  source "${ENV_FILE}"
  set +a
fi

# Load config from generated files
if [ ! -d "${OUTPUT_DIR}" ]; then
  echo "Error: Generated config not found at ${OUTPUT_DIR}"
  echo "Run setup.sh first."
  exit 1
fi

TENANT_ID=$(jq -r '.tenant.id' "${OUTPUT_DIR}/financial-tenant.json")
DEVICE_ID=$(jq -r '.authentication_devices[0].id' "${OUTPUT_DIR}/financial-user.json")
USERNAME=$(jq -r '.email' "${OUTPUT_DIR}/financial-user.json")
PASSWORD=$(jq -r '.raw_password' "${OUTPUT_DIR}/financial-user.json")

# Use backend URL (without mTLS) for device authentication
BASE_URL="${AUTHORIZATION_SERVER_URL:-https://api.local.test}"

echo "   Server:    ${BASE_URL}"
echo "   Tenant:    ${TENANT_ID}"
echo "   Device:    ${DEVICE_ID}"
echo "   Action:    ${ACTION}"
if [ "${ACTION}" != "cancel" ]; then
  echo "   Username:  ${USERNAME}"
fi
echo ""

# Step 1: Get authentication transaction
echo "Step 1: Getting authentication transaction..."

TRANSACTION_RESPONSE=$(curl -s -k -w "\n%{http_code}" -X GET \
  "${BASE_URL}/${TENANT_ID}/v1/authentication-devices/${DEVICE_ID}/authentications" \
  -H "Content-Type: application/json")

HTTP_CODE=$(echo "${TRANSACTION_RESPONSE}" | tail -n1)
RESPONSE_BODY=$(echo "${TRANSACTION_RESPONSE}" | sed '$d')

if [ "${HTTP_CODE}" != "200" ]; then
  echo "  Failed to get authentication transaction (HTTP ${HTTP_CODE})"
  echo "  Response: ${RESPONSE_BODY}"
  exit 1
fi

echo "  Authentication transaction retrieved"
echo "${RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "${RESPONSE_BODY}"
echo ""

# Extract transaction details
TRANSACTION_ID=$(echo "${RESPONSE_BODY}" | jq -r '.list[0].id')

if [ -z "${TRANSACTION_ID}" ] || [ "${TRANSACTION_ID}" = "null" ]; then
  echo "  No authentication transaction found"
  exit 1
fi

# Step 2: Execute action based on argument
if [ "${ACTION}" = "cancel" ]; then
  echo "Step 2: Cancelling authentication..."

  CANCEL_RESPONSE=$(curl -s -k -w "\n%{http_code}" -X POST \
    "${BASE_URL}/${TENANT_ID}/v1/authentications/${TRANSACTION_ID}/authentication-cancel" \
    -H "Content-Type: application/json")

  CANCEL_HTTP_CODE=$(echo "${CANCEL_RESPONSE}" | tail -n1)
  CANCEL_RESPONSE_BODY=$(echo "${CANCEL_RESPONSE}" | sed '$d')

  if [ "${CANCEL_HTTP_CODE}" = "200" ]; then
    echo "  Authentication cancelled successfully"
    echo "${CANCEL_RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "${CANCEL_RESPONSE_BODY}"
  else
    echo "  Cancel failed (HTTP ${CANCEL_HTTP_CODE})"
    echo "  Response: ${CANCEL_RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "${CANCEL_RESPONSE_BODY}"
    exit 1
  fi

  echo ""
  echo "=========================================="
  echo "CIBA Authentication Cancelled"
  echo "=========================================="
  echo ""
  echo "The client will receive 'access_denied' error when polling."
  echo ""

else
  echo "Step 2: Executing password authentication..."

  AUTH_RESPONSE=$(curl -s -k -w "\n%{http_code}" -X POST \
    "${BASE_URL}/${TENANT_ID}/v1/authentications/${TRANSACTION_ID}/password-authentication" \
    -H "Content-Type: application/json" \
    -d "{
      \"username\": \"${USERNAME}\",
      \"password\": \"${PASSWORD}\"
    }")

  AUTH_HTTP_CODE=$(echo "${AUTH_RESPONSE}" | tail -n1)
  AUTH_RESPONSE_BODY=$(echo "${AUTH_RESPONSE}" | sed '$d')

  if [ "${AUTH_HTTP_CODE}" = "200" ]; then
    echo "  Password authentication successful"
    echo "${AUTH_RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "${AUTH_RESPONSE_BODY}"
  else
    echo "  Password authentication failed (HTTP ${AUTH_HTTP_CODE})"
    echo "  Response: ${AUTH_RESPONSE_BODY}" | jq '.' 2>/dev/null || echo "${AUTH_RESPONSE_BODY}"
    exit 1
  fi

  echo ""
  echo "=========================================="
  echo "CIBA Device Authentication Complete"
  echo "=========================================="
  echo ""
  echo "The client can now poll the token endpoint to get the access token."
  echo ""
fi
