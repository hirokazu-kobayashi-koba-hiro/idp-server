#!/bin/bash
# CIBA + External Password Auth - Helper Functions
#
# Usage:
#   cd config/templates/use-cases/ciba_external-password-auth
#   source helpers.sh
#   get_admin_token

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../../.." && pwd)"

# Load .env
if [ -f "${PROJECT_ROOT}/.env" ]; then
  set -a
  source "${PROJECT_ROOT}/.env"
  set +a
fi

ORGANIZATION_NAME="${ORGANIZATION_NAME:-ciba-ext-pw}"

# Load generated config
CONFIG_DIR="${PROJECT_ROOT}/config/generated/${ORGANIZATION_NAME}"
if [ -d "${CONFIG_DIR}" ]; then
  ORGANIZATION_ID=$(jq -r '.organization.id' "${CONFIG_DIR}/onboarding.json" 2>/dev/null)
  ORGANIZER_TENANT_ID=$(jq -r '.tenant.id' "${CONFIG_DIR}/onboarding.json" 2>/dev/null)
  PUBLIC_TENANT_ID=$(jq -r '.tenant.id' "${CONFIG_DIR}/public-tenant.json" 2>/dev/null)
  NEW_ADMIN_EMAIL=$(jq -r '.user.email' "${CONFIG_DIR}/onboarding.json" 2>/dev/null)
  NEW_ADMIN_PASSWORD=$(jq -r '.user.raw_password' "${CONFIG_DIR}/onboarding.json" 2>/dev/null)
  NEW_ADMIN_CLIENT_ID=$(jq -r '.client.client_id' "${CONFIG_DIR}/onboarding.json" 2>/dev/null)
  NEW_ADMIN_CLIENT_SECRET=$(jq -r '.client.client_secret' "${CONFIG_DIR}/onboarding.json" 2>/dev/null)
  CLIENT_ID=$(jq -r '.client_id' "${CONFIG_DIR}/public-client.json" 2>/dev/null)
  CLIENT_SECRET=$(jq -r '.client_secret' "${CONFIG_DIR}/public-client.json" 2>/dev/null)
  REDIRECT_URI=$(jq -r '.redirect_uris[0]' "${CONFIG_DIR}/public-client.json" 2>/dev/null)

  ORG_BASE_URL="${AUTHORIZATION_SERVER_URL}/v1/management/organizations/${ORGANIZATION_ID}/tenants"
  TENANT_BASE="${AUTHORIZATION_SERVER_URL}/${PUBLIC_TENANT_ID}"

  echo "Loaded config for: ${ORGANIZATION_NAME}"
  echo "  Public Tenant: ${PUBLIC_TENANT_ID}"
  echo "  Client ID: ${CLIENT_ID}"
else
  echo "Warning: Config directory not found: ${CONFIG_DIR}"
  echo "Run setup.sh first."
fi

MOCK_LOCAL_URL="${MOCK_LOCAL_URL:-http://localhost:4002}"

# --- Admin token ---
get_admin_token() {
  ORG_ACCESS_TOKEN=$(curl -s -X POST \
    "${AUTHORIZATION_SERVER_URL}/${ORGANIZER_TENANT_ID}/v1/tokens" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    --data-urlencode "grant_type=password" \
    --data-urlencode "username=${NEW_ADMIN_EMAIL}" \
    --data-urlencode "password=${NEW_ADMIN_PASSWORD}" \
    --data-urlencode "client_id=${NEW_ADMIN_CLIENT_ID}" \
    --data-urlencode "client_secret=${NEW_ADMIN_CLIENT_SECRET}" \
    --data-urlencode "scope=openid profile email management" | jq -r '.access_token')

  if [ -z "${ORG_ACCESS_TOKEN}" ] || [ "${ORG_ACCESS_TOKEN}" = "null" ]; then
    echo "Failed to get admin token"
    return 1
  fi
  echo "Admin token obtained: ${ORG_ACCESS_TOKEN:0:20}..."
}

# --- OAuth flow helpers ---

# 認可リクエスト開始（cookie jar で毎回クリーン）
start_auth_flow() {
  local scope="${1:-openid+profile+email+claims:authentication_devices}"
  [ -n "${COOKIE_JAR:-}" ] && [ -f "${COOKIE_JAR}" ] && rm -f "${COOKIE_JAR}"
  COOKIE_JAR=$(mktemp)
  STATE="ciba-ext-state-$(date +%s)"

  AUTH_REDIRECT=$(curl -s -c "${COOKIE_JAR}" -o /dev/null \
    -w "%{redirect_url}" \
    "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=$(python3 -c "import urllib.parse; print(urllib.parse.quote('${REDIRECT_URI}', safe=''))")&scope=${scope}&state=${STATE}")

  AUTHORIZATION_ID=$(echo "${AUTH_REDIRECT}" | sed -n 's/.*[?&]id=\([^&#]*\).*/\1/p')
  echo "Authorization ID: ${AUTHORIZATION_ID}"
}

# パスワード認証（外部サービス経由）
password_login() {
  local username="$1"
  local password="$2"

  local response http_code body
  response=$(curl -s -w "\n%{http_code}" -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
    -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/password-authentication" \
    -H "Content-Type: application/json" \
    -d "{\"username\": \"${username}\", \"password\": \"${password}\"}")

  http_code=$(echo "${response}" | tail -1)
  body=$(echo "${response}" | sed '$d')

  echo "  Password auth: HTTP ${http_code}"
  if [ "${http_code}" != "200" ] && [ "${http_code}" != "302" ]; then
    echo "  ${body}" | jq '.' 2>/dev/null || echo "  ${body}"
  fi
}

# 認可 → コード取得 → トークン交換
complete_auth_flow() {
  AUTHORIZE_RESPONSE=$(curl -s \
    -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
    -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/authorize" \
    -H "Content-Type: application/json" \
    -d '{}')

  AUTHZ_REDIRECT_URI=$(echo "${AUTHORIZE_RESPONSE}" | jq -r '.redirect_uri')
  AUTH_CODE=$(echo "${AUTHZ_REDIRECT_URI}" | sed -n 's/.*[?&]code=\([^&#]*\).*/\1/p')

  if [ -n "${AUTH_CODE}" ] && [ "${AUTH_CODE}" != "null" ] && [ "${AUTH_CODE}" != "" ]; then
    echo "  Authorization code: ${AUTH_CODE:0:20}..."

    TOKEN_RESPONSE=$(curl -s \
      -X POST "${TENANT_BASE}/v1/tokens" \
      -H "Content-Type: application/x-www-form-urlencoded" \
      --data-urlencode "grant_type=authorization_code" \
      --data-urlencode "code=${AUTH_CODE}" \
      --data-urlencode "redirect_uri=${REDIRECT_URI}" \
      --data-urlencode "client_id=${CLIENT_ID}" \
      --data-urlencode "client_secret=${CLIENT_SECRET}")

    USER_ACCESS_TOKEN=$(echo "${TOKEN_RESPONSE}" | jq -r '.access_token')
    USER_ID_TOKEN=$(echo "${TOKEN_RESPONSE}" | jq -r '.id_token')

    if [ -n "${USER_ACCESS_TOKEN}" ] && [ "${USER_ACCESS_TOKEN}" != "null" ]; then
      echo "  Token obtained: ${USER_ACCESS_TOKEN:0:20}..."
    else
      echo "  Token exchange failed"
      echo "  ${TOKEN_RESPONSE}" | jq '.' 2>/dev/null
    fi
  else
    echo "  No authorization code returned"
    echo "  ${AUTHORIZE_RESPONSE}" | jq '.' 2>/dev/null || echo "  ${AUTHORIZE_RESPONSE}"
  fi
}

# --- CIBA helpers ---
ciba_request() {
  local LOGIN_HINT="$1"
  local BINDING_MSG="${2:-}"

  local CURL_ARGS=()
  CURL_ARGS+=(-s -w "\n%{http_code}" -X POST)
  CURL_ARGS+=("${TENANT_BASE}/v1/backchannel/authentications")
  CURL_ARGS+=(-H "Content-Type: application/x-www-form-urlencoded")
  CURL_ARGS+=(--data-urlencode "scope=openid profile email")
  CURL_ARGS+=(--data-urlencode "login_hint=${LOGIN_HINT}")
  if [ -n "${BINDING_MSG}" ]; then
    CURL_ARGS+=(--data-urlencode "binding_message=${BINDING_MSG}")
  fi
  CURL_ARGS+=(--data-urlencode "client_id=${CLIENT_ID}")
  CURL_ARGS+=(--data-urlencode "client_secret=${CLIENT_SECRET}")

  local RESPONSE
  RESPONSE=$(curl "${CURL_ARGS[@]}")

  local HTTP_CODE
  HTTP_CODE=$(echo "${RESPONSE}" | tail -n1)
  local BODY
  BODY=$(echo "${RESPONSE}" | sed '$d')

  AUTH_REQ_ID=$(echo "${BODY}" | jq -r '.auth_req_id // empty')
  CIBA_EXPIRES_IN=$(echo "${BODY}" | jq -r '.expires_in // empty')
  CIBA_INTERVAL=$(echo "${BODY}" | jq -r '.interval // empty')

  echo "  CIBA request: HTTP ${HTTP_CODE}"
  if [ -n "${AUTH_REQ_ID}" ] && [ "${AUTH_REQ_ID}" != "null" ]; then
    echo "  auth_req_id: ${AUTH_REQ_ID}"
    echo "  expires_in: ${CIBA_EXPIRES_IN}, interval: ${CIBA_INTERVAL}"
  else
    echo "  ${BODY}" | jq '.' 2>/dev/null || echo "  ${BODY}"
  fi
}

ciba_poll() {
  local RESPONSE
  RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
    "${TENANT_BASE}/v1/tokens" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    --data-urlencode "grant_type=urn:openid:params:grant-type:ciba" \
    --data-urlencode "auth_req_id=${AUTH_REQ_ID}" \
    --data-urlencode "client_id=${CLIENT_ID}" \
    --data-urlencode "client_secret=${CLIENT_SECRET}")

  local HTTP_CODE
  HTTP_CODE=$(echo "${RESPONSE}" | tail -n1)
  local BODY
  BODY=$(echo "${RESPONSE}" | sed '$d')

  CIBA_ACCESS_TOKEN=$(echo "${BODY}" | jq -r '.access_token // empty')
  CIBA_ERROR=$(echo "${BODY}" | jq -r '.error // empty')

  echo "  CIBA poll: HTTP ${HTTP_CODE}"
  if [ -n "${CIBA_ACCESS_TOKEN}" ] && [ "${CIBA_ACCESS_TOKEN}" != "null" ] && [ "${CIBA_ACCESS_TOKEN}" != "" ]; then
    echo "  Access token: ${CIBA_ACCESS_TOKEN:0:20}..."
  elif [ -n "${CIBA_ERROR}" ]; then
    echo "  Status: ${CIBA_ERROR}"
  fi
}

# --- UserInfo ---
get_userinfo() {
  local TOKEN="${1:-${USER_ACCESS_TOKEN}}"
  curl -s "${TENANT_BASE}/v1/userinfo" \
    -H "Authorization: Bearer ${TOKEN}" | jq '.'
}

# --- JWT decode ---
decode_jwt_payload() {
  local token="$1"
  local payload
  payload=$(echo "${token}" | cut -d. -f2 | tr '_-' '/+')
  local mod=$((${#payload} % 4))
  if [ $mod -eq 2 ]; then payload="${payload}=="; elif [ $mod -eq 3 ]; then payload="${payload}="; fi
  echo "${payload}" | base64 -d 2>/dev/null
}
