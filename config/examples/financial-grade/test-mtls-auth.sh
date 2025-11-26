#!/bin/bash
set -e

# Test MTLS authentication with financial-grade client
# This script tests self_signed_tls_client_auth using generated certificates

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"

echo "=========================================="
echo "🔐 MTLS Authentication Test"
echo "=========================================="
echo ""

# Load configuration
FINANCIAL_CLIENT_FILE="${SCRIPT_DIR}/financial-client.json"
FINANCIAL_TENANT_FILE="${SCRIPT_DIR}/financial-tenant.json"
CERT_FILE="${SCRIPT_DIR}/certs/client-cert.pem"
KEY_FILE="${SCRIPT_DIR}/certs/client-key.pem"

if [ ! -f "${FINANCIAL_CLIENT_FILE}" ]; then
  echo "❌ Error: financial-client.json not found"
  exit 1
fi

if [ ! -f "${FINANCIAL_TENANT_FILE}" ]; then
  echo "❌ Error: financial-tenant.json not found"
  exit 1
fi

if [ ! -f "${CERT_FILE}" ] || [ ! -f "${KEY_FILE}" ]; then
  echo "❌ Error: Client certificate not found. Please run:"
  echo "   ./config/scripts/generate-client-certificate.sh -c financial-web-app -o ./config/examples/financial-grade/certs"
  exit 1
fi

# Extract IDs
CLIENT_ID=$(jq -r '.client_id' "${FINANCIAL_CLIENT_FILE}")
TENANT_ID=$(jq -r '.tenant.id' "${FINANCIAL_TENANT_FILE}")
BASE_URL="http://localhost:8080"
TOKEN_ENDPOINT="${BASE_URL}/${TENANT_ID}/v1/tokens"

echo "📋 Configuration:"
echo "   Tenant ID:       ${TENANT_ID}"
echo "   Client ID:       ${CLIENT_ID}"
echo "   Token Endpoint:  ${TOKEN_ENDPOINT}"
echo "   Certificate:     ${CERT_FILE}"
echo "   Private Key:     ${KEY_FILE}"
echo ""

# Test 1: Invalid authorization code (should fail with invalid_grant)
echo "🧪 Test 1: Token request with invalid authorization code"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Expected: HTTP 400 with error=invalid_grant (code is invalid)"
echo ""

INVALID_CODE="invalid_authorization_code_12345"
REDIRECT_URI="http://localhost:3000/callback/"

# Encode client certificate for HTTP header (replace newlines with %0A)
ENCODED_CLIENT_CERT=$(cat "${CERT_FILE}" | awk '{printf "%s%%0A", $0}' | sed 's/%0A$//')

echo "📤 Sending request with MTLS certificate (via x-ssl-cert header)..."
RESPONSE=$(curl -s -w "\nHTTP_CODE:%{http_code}" \
  -X POST "${TOKEN_ENDPOINT}" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -H "x-ssl-cert: ${ENCODED_CLIENT_CERT}" \
  --data-urlencode "grant_type=authorization_code" \
  --data-urlencode "code=${INVALID_CODE}" \
  --data-urlencode "redirect_uri=${REDIRECT_URI}" \
  --data-urlencode "client_id=${CLIENT_ID}")

BODY=$(echo "$RESPONSE" | sed -n '1,/HTTP_CODE:/p' | sed '$d')
HTTP_CODE=$(echo "$RESPONSE" | grep HTTP_CODE | cut -d: -f2)

echo "📥 Response (HTTP ${HTTP_CODE}):"
echo "${BODY}" | jq '.' 2>/dev/null || echo "${BODY}"
echo ""

if [ "${HTTP_CODE}" = "400" ]; then
  ERROR=$(echo "${BODY}" | jq -r '.error // "unknown"')
  if [ "${ERROR}" = "invalid_grant" ]; then
    echo "✅ MTLS client authentication successful!"
    echo "   (Authorization code is invalid as expected, but client was authenticated)"
  elif [ "${ERROR}" = "invalid_client" ]; then
    echo "❌ Client authentication failed"
    echo "   Error: ${ERROR}"
  else
    echo "⚠️  Unexpected error: ${ERROR}"
  fi
else
  echo "⚠️  Unexpected HTTP code: ${HTTP_CODE}"
fi

echo ""

# Test 2: Without MTLS certificate (should fail with invalid_client or connection error)
echo "🧪 Test 2: Token request WITHOUT MTLS certificate"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Expected: Client authentication error (no certificate provided)"
echo ""

echo "📤 Sending request WITHOUT certificate..."
RESPONSE_NO_CERT=$(curl -s -w "\nHTTP_CODE:%{http_code}" \
  -X POST "${TOKEN_ENDPOINT}" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=authorization_code" \
  --data-urlencode "code=${INVALID_CODE}" \
  --data-urlencode "redirect_uri=${REDIRECT_URI}" \
  --data-urlencode "client_id=${CLIENT_ID}")

BODY_NO_CERT=$(echo "$RESPONSE_NO_CERT" | sed -n '1,/HTTP_CODE:/p' | sed '$d')
HTTP_CODE_NO_CERT=$(echo "$RESPONSE_NO_CERT" | grep HTTP_CODE | cut -d: -f2)

echo "📥 Response (HTTP ${HTTP_CODE_NO_CERT}):"
echo "${BODY_NO_CERT}" | jq '.' 2>/dev/null || echo "${BODY_NO_CERT}"
echo ""

if [ "${HTTP_CODE_NO_CERT}" = "401" ] || [ "${HTTP_CODE_NO_CERT}" = "400" ]; then
  ERROR_NO_CERT=$(echo "${BODY_NO_CERT}" | jq -r '.error // "unknown"')
  if [ "${ERROR_NO_CERT}" = "invalid_client" ]; then
    echo "✅ Correctly rejected request without MTLS certificate"
  else
    echo "⚠️  Authentication failed with error: ${ERROR_NO_CERT}"
  fi
else
  echo "⚠️  Unexpected behavior: HTTP ${HTTP_CODE_NO_CERT}"
fi

echo ""
echo "=========================================="
echo "✅ MTLS Authentication Test Complete"
echo "=========================================="
echo ""
echo "📊 Summary:"
echo "   Test 1 (with cert):    HTTP ${HTTP_CODE} - ${ERROR}"
echo "   Test 2 (without cert): HTTP ${HTTP_CODE_NO_CERT} - ${ERROR_NO_CERT}"
echo ""
echo "🔍 Analysis:"
if [ "${HTTP_CODE}" = "400" ] && [ "${ERROR}" = "invalid_grant" ]; then
  echo "   ✅ MTLS client authentication is working correctly"
  echo "   ✅ self_signed_tls_client_auth successfully authenticated the client"
else
  echo "   ⚠️  Check client registration and certificate configuration"
fi
