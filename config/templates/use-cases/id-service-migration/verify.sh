#!/bin/bash
# CIBA + External Password Auth - Verification Script
#
# Tests:
#   Phase 1: External password auth with device mapping
#   Phase 2: CIBA backchannel authentication using mapped device
#
# Prerequisites:
#   - setup.sh completed
#   - mock-server.js running (port 4002)
#   - source helpers.sh && get_admin_token

PASS=0
FAIL=0

pass() { echo "  ✓ $1"; PASS=$((PASS + 1)); }
fail() { echo "  ✗ $1"; FAIL=$((FAIL + 1)); }

echo "=========================================="
echo "CIBA + External Password Auth Verification"
echo "=========================================="
echo ""

TEST_EMAIL="${TEST_EMAIL:-test-ciba-ext-$(date +%s)@example.com}"
TEST_PASSWORD="${TEST_PASSWORD:-CorrectPassword123}"

# ============================================================
# Phase 1: External Password Auth + Device Mapping
# ============================================================
echo "--- Phase 1: External Password Auth + Device Mapping ---"
echo ""

# --- Step 1: Mock server connectivity ---
echo "Step 1: Mock server connectivity check..."
MOCK_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "${MOCK_LOCAL_URL}/auth/password" \
  -H "Content-Type: application/json" \
  -d '{"username":"test@example.com","password":"test"}')

if [ "${MOCK_STATUS}" = "200" ]; then
  pass "Mock server reachable (HTTP ${MOCK_STATUS})"
else
  fail "Mock server unreachable (HTTP ${MOCK_STATUS})"
  echo "  Start mock server: node mock-server.js"
  echo ""
  echo "Results: PASS=${PASS} FAIL=${FAIL}"
  exit 1
fi

# Verify mock returns device info
MOCK_RESPONSE=$(curl -s -X POST "${MOCK_LOCAL_URL}/auth/password" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"${TEST_EMAIL}\",\"password\":\"${TEST_PASSWORD}\"}")

MOCK_DEVICE_ID=$(echo "${MOCK_RESPONSE}" | jq -r '.device.id // empty')
if [ -n "${MOCK_DEVICE_ID}" ]; then
  pass "Mock returns device info (device_id: ${MOCK_DEVICE_ID})"
else
  fail "Mock does not return device info"
fi
echo ""

# --- Step 2: Discovery endpoint ---
echo "Step 2: Discovery endpoint..."
DISCO_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "${TENANT_BASE}/.well-known/openid-configuration")
if [ "${DISCO_STATUS}" = "200" ]; then
  pass "Discovery endpoint (HTTP 200)"
else
  fail "Discovery endpoint (HTTP ${DISCO_STATUS})"
fi

# Check CIBA endpoint
CIBA_ENDPOINT=$(curl -s "${TENANT_BASE}/.well-known/openid-configuration" | jq -r '.backchannel_authentication_endpoint // empty')
if [ -n "${CIBA_ENDPOINT}" ]; then
  pass "CIBA endpoint advertised"
else
  fail "CIBA endpoint not in discovery"
fi
echo ""

# --- Step 3: External password login (creates user with device) ---
echo "Step 3: External password login (user creation with device mapping)..."
start_auth_flow
password_login "${TEST_EMAIL}" "${TEST_PASSWORD}"
complete_auth_flow

if [ -n "${USER_ACCESS_TOKEN}" ] && [ "${USER_ACCESS_TOKEN}" != "null" ]; then
  pass "External password auth + token obtained"
else
  fail "External password auth failed"
fi
echo ""

# --- Step 4: Check UserInfo for authentication_devices ---
echo "Step 4: Verify authentication_devices in UserInfo..."
USERINFO=$(get_userinfo)
echo "${USERINFO}"

USER_SUB=$(echo "${USERINFO}" | jq -r '.sub // empty')
DEVICE_COUNT=$(echo "${USERINFO}" | jq '.authentication_devices | length // 0' 2>/dev/null)

if [ -n "${USER_SUB}" ] && [ "${USER_SUB}" != "null" ]; then
  pass "UserInfo returned (sub: ${USER_SUB})"
else
  fail "UserInfo missing sub"
fi

if [ "${DEVICE_COUNT}" -gt 0 ] 2>/dev/null; then
  DEVICE_ID=$(echo "${USERINFO}" | jq -r '.authentication_devices[0].id // empty')
  DEVICE_NOTIFICATION=$(echo "${USERINFO}" | jq -r '.authentication_devices[0].notification_token // empty')
  pass "authentication_devices mapped (count: ${DEVICE_COUNT}, id: ${DEVICE_ID})"
  if [ -n "${DEVICE_NOTIFICATION}" ]; then
    pass "notification_token present"
  else
    fail "notification_token missing"
  fi
else
  fail "authentication_devices not mapped (count: ${DEVICE_COUNT:-0})"
  echo "  This is the key test: ObjectCompositor array mapping for authentication_devices"
fi
echo ""

# --- Step 5: Second login (verify existing user resolution) ---
echo "Step 5: Second login (existing user resolution)..."
start_auth_flow
password_login "${TEST_EMAIL}" "${TEST_PASSWORD}"
complete_auth_flow

USERINFO2=$(get_userinfo)
USER_SUB2=$(echo "${USERINFO2}" | jq -r '.sub // empty')

if [ "${USER_SUB}" = "${USER_SUB2}" ]; then
  pass "Same user resolved on second login (sub matches)"
else
  fail "Different user on second login (${USER_SUB} vs ${USER_SUB2})"
fi
echo ""

# ============================================================
# Phase 2: CIBA Backchannel Authentication (binding_message)
# ============================================================
echo "--- Phase 2: CIBA Backchannel Authentication ---"
echo ""

EXTERNAL_PROVIDER_ID="${EXTERNAL_PROVIDER_ID:-external-auth}"
BINDING_MESSAGE="CIBA-$(date +%s | tail -c 5)"

# --- Step 6: CIBA request with binding_message ---
echo "Step 6: CIBA backchannel authentication request (binding_message: ${BINDING_MESSAGE})..."
ciba_request "email:${TEST_EMAIL},idp:${EXTERNAL_PROVIDER_ID}" "${BINDING_MESSAGE}"

if [ -n "${AUTH_REQ_ID}" ] && [ "${AUTH_REQ_ID}" != "null" ] && [ "${AUTH_REQ_ID}" != "" ]; then
  pass "CIBA request accepted (auth_req_id: ${AUTH_REQ_ID:0:20}...)"
else
  fail "CIBA request failed"
fi
echo ""

# --- Step 7: CIBA poll (before binding_message verification - expect authorization_pending) ---
echo "Step 7: CIBA token poll (before verification, expect authorization_pending)..."
ciba_poll

if [ "${CIBA_ERROR}" = "authorization_pending" ]; then
  pass "CIBA poll returns authorization_pending (expected)"
else
  fail "CIBA poll unexpected result (expected authorization_pending, got: ${CIBA_ERROR:-token})"
fi
echo ""

# --- Step 8: Get authentication transaction (device side) ---
echo "Step 8: Get authentication transaction for device..."
TX_RESPONSE=$(curl -s -w "\n%{http_code}" \
  "${TENANT_BASE}/v1/authentication-devices/${DEVICE_ID}/authentications?attributes.auth_req_id=${AUTH_REQ_ID}")

TX_HTTP=$(echo "${TX_RESPONSE}" | tail -n1)
TX_BODY=$(echo "${TX_RESPONSE}" | sed '$d')

TRANSACTION_ID=$(echo "${TX_BODY}" | jq -r '.list[0].id // empty')
TX_BINDING_MSG=$(echo "${TX_BODY}" | jq -r '.list[0].context.binding_message // empty')

if [ -n "${TRANSACTION_ID}" ] && [ "${TRANSACTION_ID}" != "null" ]; then
  pass "Authentication transaction found (id: ${TRANSACTION_ID:0:20}...)"
  if [ -n "${TX_BINDING_MSG}" ]; then
    echo "    binding_message in context: ${TX_BINDING_MSG}"
  fi
else
  fail "Authentication transaction not found"
  echo "  HTTP ${TX_HTTP}"
  echo "  ${TX_BODY}" | jq '.' 2>/dev/null || echo "  ${TX_BODY}"
fi
echo ""

# --- Step 9: Binding message verification (device side) ---
echo "Step 9: Binding message verification..."
BM_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  "${TENANT_BASE}/v1/authentications/${TRANSACTION_ID}/authentication-device-binding-message" \
  -H "Content-Type: application/json" \
  -d "{\"binding_message\": \"${BINDING_MESSAGE}\"}")

BM_HTTP=$(echo "${BM_RESPONSE}" | tail -n1)
BM_BODY=$(echo "${BM_RESPONSE}" | sed '$d')

if [ "${BM_HTTP}" = "200" ]; then
  pass "Binding message verified successfully"
else
  fail "Binding message verification failed (HTTP ${BM_HTTP})"
  echo "  ${BM_BODY}" | jq '.' 2>/dev/null || echo "  ${BM_BODY}"
fi
echo ""

# --- Step 10: CIBA poll (after binding_message verification - expect token) ---
echo "Step 10: CIBA token poll (after verification)..."
ciba_poll

if [ -n "${CIBA_ACCESS_TOKEN}" ] && [ "${CIBA_ACCESS_TOKEN}" != "null" ] && [ "${CIBA_ACCESS_TOKEN}" != "" ]; then
  pass "CIBA token obtained"

  # Verify CIBA token UserInfo
  CIBA_USERINFO=$(get_userinfo "${CIBA_ACCESS_TOKEN}")
  CIBA_SUB=$(echo "${CIBA_USERINFO}" | jq -r '.sub // empty')
  if [ "${CIBA_SUB}" = "${USER_SUB}" ]; then
    pass "CIBA token UserInfo matches original user (sub: ${CIBA_SUB})"
  else
    fail "CIBA UserInfo sub mismatch (${CIBA_SUB} vs ${USER_SUB})"
  fi
elif [ "${CIBA_ERROR}" = "authorization_pending" ]; then
  fail "CIBA still authorization_pending after binding_message verification"
else
  fail "CIBA poll failed"
fi
echo ""

# ============================================================
# Phase 3: CIBA with device: login_hint
# ============================================================
echo "--- Phase 3: CIBA with device: login_hint ---"
echo ""

BINDING_MESSAGE_D="DEV-$(date +%s | tail -c 5)"

# --- Step 11: CIBA request with device: login_hint ---
echo "Step 11: CIBA request with device: login_hint (binding_message: ${BINDING_MESSAGE_D})..."
ciba_request "device:${DEVICE_ID},idp:${EXTERNAL_PROVIDER_ID}" "${BINDING_MESSAGE_D}"

if [ -n "${AUTH_REQ_ID}" ] && [ "${AUTH_REQ_ID}" != "null" ] && [ "${AUTH_REQ_ID}" != "" ]; then
  pass "CIBA request accepted with device hint (auth_req_id: ${AUTH_REQ_ID:0:20}...)"
else
  fail "CIBA request with device hint failed"
fi
echo ""

# --- Step 12: CIBA poll (before verification) ---
echo "Step 12: CIBA token poll (before verification, expect authorization_pending)..."
ciba_poll

if [ "${CIBA_ERROR}" = "authorization_pending" ]; then
  pass "CIBA poll returns authorization_pending (expected)"
else
  fail "CIBA poll unexpected result (expected authorization_pending, got: ${CIBA_ERROR:-token})"
fi
echo ""

# --- Step 13: Get authentication transaction ---
echo "Step 13: Get authentication transaction for device..."
TX_RESPONSE_D=$(curl -s -w "\n%{http_code}" \
  "${TENANT_BASE}/v1/authentication-devices/${DEVICE_ID}/authentications?attributes.auth_req_id=${AUTH_REQ_ID}")

TX_HTTP_D=$(echo "${TX_RESPONSE_D}" | tail -n1)
TX_BODY_D=$(echo "${TX_RESPONSE_D}" | sed '$d')

TRANSACTION_ID_D=$(echo "${TX_BODY_D}" | jq -r '.list[0].id // empty')

if [ -n "${TRANSACTION_ID_D}" ] && [ "${TRANSACTION_ID_D}" != "null" ]; then
  pass "Authentication transaction found (id: ${TRANSACTION_ID_D:0:20}...)"
else
  fail "Authentication transaction not found"
  echo "  HTTP ${TX_HTTP_D}"
  echo "  ${TX_BODY_D}" | jq '.' 2>/dev/null || echo "  ${TX_BODY_D}"
fi
echo ""

# --- Step 14: Binding message verification ---
echo "Step 14: Binding message verification..."
BM_RESPONSE_D=$(curl -s -w "\n%{http_code}" -X POST \
  "${TENANT_BASE}/v1/authentications/${TRANSACTION_ID_D}/authentication-device-binding-message" \
  -H "Content-Type: application/json" \
  -d "{\"binding_message\": \"${BINDING_MESSAGE_D}\"}")

BM_HTTP_D=$(echo "${BM_RESPONSE_D}" | tail -n1)
BM_BODY_D=$(echo "${BM_RESPONSE_D}" | sed '$d')

if [ "${BM_HTTP_D}" = "200" ]; then
  pass "Binding message verified successfully"
else
  fail "Binding message verification failed (HTTP ${BM_HTTP_D})"
  echo "  ${BM_BODY_D}" | jq '.' 2>/dev/null || echo "  ${BM_BODY_D}"
fi
echo ""

# --- Step 15: CIBA poll (after verification) ---
echo "Step 15: CIBA token poll (after verification)..."
ciba_poll

if [ -n "${CIBA_ACCESS_TOKEN}" ] && [ "${CIBA_ACCESS_TOKEN}" != "null" ] && [ "${CIBA_ACCESS_TOKEN}" != "" ]; then
  pass "CIBA token obtained via device hint"

  CIBA_USERINFO_D=$(get_userinfo "${CIBA_ACCESS_TOKEN}")
  CIBA_SUB_D=$(echo "${CIBA_USERINFO_D}" | jq -r '.sub // empty')
  if [ "${CIBA_SUB_D}" = "${USER_SUB}" ]; then
    pass "CIBA token UserInfo matches original user (sub: ${CIBA_SUB_D})"
  else
    fail "CIBA UserInfo sub mismatch (${CIBA_SUB_D} vs ${USER_SUB})"
  fi
elif [ "${CIBA_ERROR}" = "authorization_pending" ]; then
  fail "CIBA still authorization_pending after binding_message verification"
else
  fail "CIBA poll failed"
fi
echo ""

# ============================================================
# Phase 4: CIBA with FIDO-UAF Authentication
# ============================================================
echo "--- Phase 4: CIBA with FIDO-UAF Authentication ---"
echo ""

BINDING_MESSAGE_F="UAF-$(date +%s | tail -c 5)"

# --- Step 16: CIBA request for FIDO-UAF flow ---
echo "Step 16: CIBA request for FIDO-UAF flow..."
ciba_request "email:${TEST_EMAIL},idp:${EXTERNAL_PROVIDER_ID}" "${BINDING_MESSAGE_F}"

if [ -n "${AUTH_REQ_ID}" ] && [ "${AUTH_REQ_ID}" != "null" ] && [ "${AUTH_REQ_ID}" != "" ]; then
  pass "CIBA request accepted (auth_req_id: ${AUTH_REQ_ID:0:20}...)"
else
  fail "CIBA request failed"
fi
echo ""

# --- Step 17: CIBA poll (before FIDO-UAF - expect authorization_pending) ---
echo "Step 17: CIBA token poll (before FIDO-UAF, expect authorization_pending)..."
ciba_poll

if [ "${CIBA_ERROR}" = "authorization_pending" ]; then
  pass "CIBA poll returns authorization_pending (expected)"
else
  fail "CIBA poll unexpected result (expected authorization_pending, got: ${CIBA_ERROR:-token})"
fi
echo ""

# --- Step 18: Get authentication transaction ---
echo "Step 18: Get authentication transaction for FIDO-UAF..."
TX_RESPONSE_F=$(curl -s -w "\n%{http_code}" \
  "${TENANT_BASE}/v1/authentication-devices/${DEVICE_ID}/authentications?attributes.auth_req_id=${AUTH_REQ_ID}")

TX_HTTP_F=$(echo "${TX_RESPONSE_F}" | tail -n1)
TX_BODY_F=$(echo "${TX_RESPONSE_F}" | sed '$d')

TRANSACTION_ID_F=$(echo "${TX_BODY_F}" | jq -r '.list[0].id // empty')

if [ -n "${TRANSACTION_ID_F}" ] && [ "${TRANSACTION_ID_F}" != "null" ]; then
  pass "Authentication transaction found (id: ${TRANSACTION_ID_F:0:20}...)"
else
  fail "Authentication transaction not found"
  echo "  HTTP ${TX_HTTP_F}"
  echo "  ${TX_BODY_F}" | jq '.' 2>/dev/null || echo "  ${TX_BODY_F}"
fi
echo ""

# --- Step 19: FIDO-UAF authentication challenge ---
echo "Step 19: FIDO-UAF authentication challenge..."
fido_uaf_authentication_challenge "${TRANSACTION_ID_F}"

if [ -n "${FIDO_CHALLENGE}" ] && [ "${FIDO_CHALLENGE}" != "null" ]; then
  pass "FIDO-UAF authentication challenge obtained"
else
  fail "FIDO-UAF authentication challenge failed"
fi
echo ""

# --- Step 20: FIDO-UAF authentication ---
echo "Step 20: FIDO-UAF authentication..."
fido_uaf_authentication "${TRANSACTION_ID_F}" "${FIDO_CHALLENGE}"

echo ""

# --- Step 21: CIBA poll (after FIDO-UAF - expect token) ---
echo "Step 21: CIBA token poll (after FIDO-UAF authentication)..."
ciba_poll

if [ -n "${CIBA_ACCESS_TOKEN}" ] && [ "${CIBA_ACCESS_TOKEN}" != "null" ] && [ "${CIBA_ACCESS_TOKEN}" != "" ]; then
  pass "CIBA token obtained via FIDO-UAF"

  CIBA_USERINFO_F=$(get_userinfo "${CIBA_ACCESS_TOKEN}")
  CIBA_SUB_F=$(echo "${CIBA_USERINFO_F}" | jq -r '.sub // empty')
  if [ "${CIBA_SUB_F}" = "${USER_SUB}" ]; then
    pass "CIBA token UserInfo matches original user (sub: ${CIBA_SUB_F})"
  else
    fail "CIBA UserInfo sub mismatch (${CIBA_SUB_F} vs ${USER_SUB})"
  fi
elif [ "${CIBA_ERROR}" = "authorization_pending" ]; then
  fail "CIBA still authorization_pending after FIDO-UAF authentication"
else
  fail "CIBA poll failed"
fi
echo ""

# ============================================================
# Summary
# ============================================================
echo "=========================================="
echo "Verification Complete"
echo "=========================================="
echo ""
echo "Results: PASS=${PASS} FAIL=${FAIL}"
echo ""

if [ ${FAIL} -gt 0 ]; then
  echo "Some tests failed. Key areas to check:"
  echo "  - authentication_devices mapping via ObjectCompositor"
  echo "  - CIBA binding_message verification flow"
  echo "  - CIBA FIDO-UAF authentication flow"
  echo "  - authentication_device_rule.authentication_type setting"
  echo "  - login_hint format (email: or device: with ,idp: suffix)"
  exit 1
else
  echo "All tests passed!"
fi
