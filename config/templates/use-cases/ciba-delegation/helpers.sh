#!/bin/bash
#
# CIBA - Experiment Helpers
#
# EXPERIMENTS.md の各実験で使う共通関数と変数を定義する。
#
# 使い方:
#   source helpers.sh
#   source helpers.sh --org my-organization
#
# 前提条件:
#   - setup.sh が実行済み
#   - verify.sh が実行済み（CIBAフロー関数を使う場合）

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../../.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"

# Parse arguments
ORGANIZATION_NAME="ciba"
while [ $# -gt 0 ]; do
  case "$1" in
    --org) ORGANIZATION_NAME="$2"; shift 2 ;;
    *) echo "Unknown option: $1"; return 1 2>/dev/null || exit 1 ;;
  esac
done

# --- Load .env ---
if [ ! -f "${ENV_FILE}" ]; then
  echo "Error: .env file not found at ${ENV_FILE}"
  return 1 2>/dev/null || exit 1
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
  return 1 2>/dev/null || exit 1
fi

ORG_ID=$(jq -r '.organization.id' "${CONFIG_DIR}/onboarding.json")
ORGANIZER_TENANT_ID=$(jq -r '.tenant.id' "${CONFIG_DIR}/onboarding.json")
ADMIN_EMAIL=$(jq -r '.user.email' "${CONFIG_DIR}/onboarding.json")
ADMIN_PASSWORD=$(jq -r '.user.raw_password' "${CONFIG_DIR}/onboarding.json")
ORG_CLIENT_ID=$(jq -r '.client.client_id' "${CONFIG_DIR}/onboarding.json")
ORG_CLIENT_SECRET=$(jq -r '.client.client_secret' "${CONFIG_DIR}/onboarding.json")
PUBLIC_TENANT_ID=$(jq -r '.tenant.id' "${CONFIG_DIR}/public-tenant.json")
CLIENT_ID=$(jq -r '.client_id' "${CONFIG_DIR}/public-client.json")
CLIENT_SECRET=$(jq -r '.client_secret' "${CONFIG_DIR}/public-client.json")
REDIRECT_URI=$(jq -r '.redirect_uris[0]' "${CONFIG_DIR}/public-client.json")

TENANT_BASE="${AUTHORIZATION_SERVER_URL}/${PUBLIC_TENANT_ID}"
ORG_BASE_URL="${AUTHORIZATION_SERVER_URL}/v1/management/organizations/${ORG_ID}/tenants"

# 生成済み設定をベースとして読み込む（テナント更新時に使う）
# 重要: テナント更新 API（PUT）はフル置換。送らなかったフィールドは空にリセットされる。
TENANT_JSON=$(jq '.tenant' "${CONFIG_DIR}/public-tenant.json")
AUTH_SERVER_JSON=$(jq '.authorization_server' "${CONFIG_DIR}/public-tenant.json")
AUTH_POLICY_OAUTH_ID=$(jq -r '.id' "${CONFIG_DIR}/authentication-policy-oauth.json")
AUTH_POLICY_CIBA_ID=$(jq -r '.id' "${CONFIG_DIR}/authentication-policy-ciba.json")
CLIENT_JSON=$(cat "${CONFIG_DIR}/public-client.json")

# デバイス認証情報（verify.sh が生成、CIBAフロー関数の前提条件）
DEVICE_CREDENTIALS_FILE="${CONFIG_DIR}/device-credentials.json"
DEVICE_CREDENTIALS_LOADED=false

if [ -f "${DEVICE_CREDENTIALS_FILE}" ]; then
  DEVICE_ID=$(jq -r '.device_id' "${DEVICE_CREDENTIALS_FILE}")
  DEVICE_SECRET=$(jq -r '.device_secret' "${DEVICE_CREDENTIALS_FILE}")
  DEVICE_SECRET_ALGORITHM=$(jq -r '.device_secret_algorithm' "${DEVICE_CREDENTIALS_FILE}")
  DEVICE_SECRET_JWT_ISSUER=$(jq -r '.device_secret_jwt_issuer' "${DEVICE_CREDENTIALS_FILE}")
  USER_SUB=$(jq -r '.user_sub' "${DEVICE_CREDENTIALS_FILE}")
  USER_EMAIL=$(jq -r '.user_email' "${DEVICE_CREDENTIALS_FILE}")
  USER_PASSWORD=$(jq -r '.user_password' "${DEVICE_CREDENTIALS_FILE}")
  DEVICE_CREDENTIALS_LOADED=true
fi

echo "=========================================="
echo "CIBA Experiment Helpers Loaded"
echo "=========================================="
echo "  Server:       ${AUTHORIZATION_SERVER_URL}"
echo "  Organization: ${ORGANIZATION_NAME}"
echo "  Tenant ID:    ${PUBLIC_TENANT_ID}"
echo "  Client ID:    ${CLIENT_ID}"
if [ "${DEVICE_CREDENTIALS_LOADED}" = "true" ]; then
  echo "  Device ID:    ${DEVICE_ID}"
  echo "  User Sub:     ${USER_SUB}"
  echo "  User Email:   ${USER_EMAIL}"
else
  echo ""
  echo "  Warning: device-credentials.json not found."
  echo "  Run verify.sh first to enable CIBA flow functions."
fi
echo ""

# ============================================================
# 管理トークン取得
# ============================================================

get_admin_token() {
  ORG_ACCESS_TOKEN=$(curl -s -X POST \
    "${AUTHORIZATION_SERVER_URL}/${ORGANIZER_TENANT_ID}/v1/tokens" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    --data-urlencode "grant_type=password" \
    --data-urlencode "username=${ADMIN_EMAIL}" \
    --data-urlencode "password=${ADMIN_PASSWORD}" \
    --data-urlencode "client_id=${ORG_CLIENT_ID}" \
    --data-urlencode "client_secret=${ORG_CLIENT_SECRET}" \
    --data-urlencode "scope=openid profile email management" | jq -r '.access_token')

  if [ -z "${ORG_ACCESS_TOKEN}" ] || [ "${ORG_ACCESS_TOKEN}" = "null" ]; then
    echo "Error: Failed to get admin token"
    return 1
  fi

  echo "Admin token: ${ORG_ACCESS_TOKEN:0:20}..."
}

# ============================================================
# テナント更新ヘルパー
#
# 重要: PUT API はフル置換。$TENANT_JSON をベースに jq で変更して使う。
#
# 使用例:
#   update_tenant '.identity_policy_config.password_policy.min_length = 12'
# ============================================================

update_tenant() {
  local jq_filter="$1"
  local updated response http_code
  updated=$(echo "${TENANT_JSON}" | jq "${jq_filter}")

  response=$(curl -s -w "\n%{http_code}" -X PUT "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${updated}")

  http_code=$(echo "${response}" | tail -1)
  local body
  body=$(echo "${response}" | sed '$d')

  if [ "${http_code}" != "200" ]; then
    echo "Error: update_tenant failed (HTTP ${http_code})" >&2
    echo "${body}" >&2
    return 1
  fi

  echo "${body}"
}

restore_tenant() {
  local response http_code
  response=$(curl -s -w "\n%{http_code}" -X PUT "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${TENANT_JSON}")

  http_code=$(echo "${response}" | tail -1)

  if [ "${http_code}" != "200" ]; then
    echo "Error: restore_tenant failed (HTTP ${http_code})" >&2
    echo "${response}" | sed '$d' >&2
    return 1
  fi

  echo "Tenant restored."
}

# ============================================================
# 認可サーバー更新ヘルパー
#
# 使用例:
#   update_auth_server '.extension.backchannel_authentication_polling_interval = 15'
# ============================================================

update_auth_server() {
  local jq_filter="$1"
  local updated response http_code
  updated=$(echo "${AUTH_SERVER_JSON}" | jq "${jq_filter}")

  response=$(curl -s -w "\n%{http_code}" -X PUT \
    "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authorization-server" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${updated}")

  http_code=$(echo "${response}" | tail -1)
  local body
  body=$(echo "${response}" | sed '$d')

  if [ "${http_code}" != "200" ]; then
    echo "Error: update_auth_server failed (HTTP ${http_code})" >&2
    echo "${body}" >&2
    return 1
  fi

  echo "${body}"
}

restore_auth_server() {
  local response http_code
  response=$(curl -s -w "\n%{http_code}" -X PUT \
    "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authorization-server" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${AUTH_SERVER_JSON}")

  http_code=$(echo "${response}" | tail -1)

  if [ "${http_code}" != "200" ]; then
    echo "Error: restore_auth_server failed (HTTP ${http_code})" >&2
    echo "${response}" | sed '$d' >&2
    return 1
  fi

  echo "Authorization server restored."
}

# ============================================================
# クライアント更新ヘルパー
#
# 重要: PUT API はフル置換。$CLIENT_JSON をベースに jq で変更して使う。
#
# 使用例:
#   update_client '.backchannel_user_code_parameter = true'
# ============================================================

update_client() {
  local jq_filter="$1"
  local updated response http_code
  updated=$(echo "${CLIENT_JSON}" | jq "${jq_filter}")

  response=$(curl -s -w "\n%{http_code}" -X PUT \
    "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/clients/${CLIENT_ID}" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${updated}")

  http_code=$(echo "${response}" | tail -1)
  local body
  body=$(echo "${response}" | sed '$d')

  if [ "${http_code}" != "200" ]; then
    echo "Error: update_client failed (HTTP ${http_code})" >&2
    echo "${body}" >&2
    return 1
  fi

  echo "${body}"
}

restore_client() {
  local response http_code
  response=$(curl -s -w "\n%{http_code}" -X PUT \
    "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/clients/${CLIENT_ID}" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${CLIENT_JSON}")

  http_code=$(echo "${response}" | tail -1)

  if [ "${http_code}" != "200" ]; then
    echo "Error: restore_client failed (HTTP ${http_code})" >&2
    echo "${response}" | sed '$d' >&2
    return 1
  fi

  echo "Client restored."
}

# ============================================================
# JWT ヘルパー
# ============================================================

# base64url エンコード（JWT 生成用）
base64url_encode() {
  openssl base64 -e -A | tr '+/' '-_' | tr -d '='
}

# HS256 JWT 生成
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

# device_secret_jwt を生成してグローバル変数 DEVICE_JWT にセット
create_device_jwt() {
  if [ "${DEVICE_CREDENTIALS_LOADED}" != "true" ]; then
    echo "Error: Device credentials not loaded. Run verify.sh first." >&2
    return 1
  fi

  local now=$(date +%s)
  local exp=$((now + 3600))
  local jti="jti-${now}-${RANDOM}"

  local payload=$(jq -n \
    --arg iss "${DEVICE_SECRET_JWT_ISSUER}" \
    --arg sub "${USER_SUB}" \
    --arg aud "${TENANT_BASE}" \
    --arg jti "${jti}" \
    --argjson exp "${exp}" \
    --argjson iat "${now}" \
    '{iss: $iss, sub: $sub, aud: $aud, jti: $jti, exp: $exp, iat: $iat}')

  DEVICE_JWT=$(create_hs256_jwt "${DEVICE_SECRET}" "${payload}")
  echo "Device JWT: ${DEVICE_JWT:0:30}..." >&2
}

# ============================================================
# CIBA フロー関数
#
# 前提条件: verify.sh 実行済み（device-credentials.json が存在すること）
#
# 使用例:
#   ciba_request                                        # デフォルトパラメータ
#   ciba_request --scope "openid" --binding-message "Transfer 100 USD"
#   ciba_request --login-hint "sub:${USER_SUB}" --requested-expiry 30
#   ciba_request --user-code "${USER_PASSWORD}"
#
#   ciba_poll                    # AUTH_REQ_ID を使ってポーリング
#   device_auth_approve          # デバイス側で認証承認（FIDO-UAF）
#   device_auth_cancel           # デバイス側で認証拒否
# ============================================================

# CIBA バックチャネル認証リクエスト送信
# 結果: CIBA_RESPONSE, AUTH_REQ_ID, CIBA_EXPIRES_IN, CIBA_INTERVAL
ciba_request() {
  if [ "${DEVICE_CREDENTIALS_LOADED}" != "true" ]; then
    echo "Error: Device credentials not loaded. Run verify.sh first." >&2
    return 1
  fi

  local login_hint="device:${DEVICE_ID},idp:idp-server"
  local scope="openid profile email"
  local binding_message=""
  local user_code=""
  local requested_expiry=""

  while [ $# -gt 0 ]; do
    case "$1" in
      --login-hint) login_hint="$2"; shift 2 ;;
      --scope) scope="$2"; shift 2 ;;
      --binding-message) binding_message="$2"; shift 2 ;;
      --user-code) user_code="$2"; shift 2 ;;
      --requested-expiry) requested_expiry="$2"; shift 2 ;;
      *) echo "Unknown option: $1"; return 1 ;;
    esac
  done

  local args=(
    -s -X POST "${TENANT_BASE}/v1/backchannel/authentications"
    -H "Content-Type: application/x-www-form-urlencoded"
    --data-urlencode "scope=${scope}"
    --data-urlencode "login_hint=${login_hint}"
    --data-urlencode "client_id=${CLIENT_ID}"
    --data-urlencode "client_secret=${CLIENT_SECRET}"
  )

  [ -n "${binding_message}" ] && args+=(--data-urlencode "binding_message=${binding_message}")
  [ -n "${user_code}" ] && args+=(--data-urlencode "user_code=${user_code}")
  [ -n "${requested_expiry}" ] && args+=(--data-urlencode "requested_expiry=${requested_expiry}")

  CIBA_RESPONSE=$(curl "${args[@]}")
  AUTH_REQ_ID=$(echo "${CIBA_RESPONSE}" | jq -r '.auth_req_id // empty')
  CIBA_EXPIRES_IN=$(echo "${CIBA_RESPONSE}" | jq -r '.expires_in // empty')
  CIBA_INTERVAL=$(echo "${CIBA_RESPONSE}" | jq -r '.interval // empty')

  echo "${CIBA_RESPONSE}" | jq .
}

# CIBA トークンポーリング
# 結果: POLL_RESPONSE, POLL_ERROR, CIBA_ACCESS_TOKEN
ciba_poll() {
  local auth_req_id="${1:-${AUTH_REQ_ID}}"

  POLL_RESPONSE=$(curl -s -X POST "${TENANT_BASE}/v1/tokens" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    --data-urlencode "grant_type=urn:openid:params:grant-type:ciba" \
    --data-urlencode "auth_req_id=${auth_req_id}" \
    --data-urlencode "client_id=${CLIENT_ID}" \
    --data-urlencode "client_secret=${CLIENT_SECRET}")

  POLL_ERROR=$(echo "${POLL_RESPONSE}" | jq -r '.error // empty')
  CIBA_ACCESS_TOKEN=$(echo "${POLL_RESPONSE}" | jq -r '.access_token // empty')

  echo "${POLL_RESPONSE}" | jq .
}

# 認証トランザクション取得（device_secret_jwt Bearer）
# 結果: TRANSACTION_RESPONSE, TRANSACTION_ID
get_auth_transaction() {
  create_device_jwt || return 1

  TRANSACTION_RESPONSE=$(curl -s -X GET \
    "${TENANT_BASE}/v1/authentication-devices/${DEVICE_ID}/authentications" \
    -H "Authorization: Bearer ${DEVICE_JWT}")

  TRANSACTION_ID=$(echo "${TRANSACTION_RESPONSE}" | jq -r '.list[0].id')

  if [ -z "${TRANSACTION_ID}" ] || [ "${TRANSACTION_ID}" = "null" ]; then
    echo "No authentication transaction found" >&2
    echo "${TRANSACTION_RESPONSE}" | jq . >&2
    return 1
  fi

  echo "${TRANSACTION_RESPONSE}" | jq .
}

# デバイス側認証承認（FIDO-UAF challenge + authentication）
device_auth_approve() {
  create_device_jwt || return 1

  echo "--- Getting authentication transaction ---"
  local tx_response
  tx_response=$(curl -s -X GET \
    "${TENANT_BASE}/v1/authentication-devices/${DEVICE_ID}/authentications" \
    -H "Authorization: Bearer ${DEVICE_JWT}")

  TRANSACTION_ID=$(echo "${tx_response}" | jq -r '.list[0].id')

  if [ -z "${TRANSACTION_ID}" ] || [ "${TRANSACTION_ID}" = "null" ]; then
    echo "No authentication transaction found"
    echo "${tx_response}" | jq .
    return 1
  fi

  echo "Transaction ID: ${TRANSACTION_ID}"

  echo ""
  echo "--- FIDO-UAF challenge ---"
  local challenge_response
  challenge_response=$(curl -s \
    -X POST "${TENANT_BASE}/v1/authentications/${TRANSACTION_ID}/fido-uaf-authentication-challenge" \
    -H "Content-Type: application/json" \
    -d "{\"device_id\": \"${DEVICE_ID}\"}")

  echo ""
  echo "--- FIDO-UAF authentication ---"
  local uaf_request
  uaf_request=$(echo "${challenge_response}" | jq -r '.uafRequest // empty')
  local auth_body
  if [ -n "${uaf_request}" ] && [ "${uaf_request}" != "null" ]; then
    auth_body="{\"device_id\": \"${DEVICE_ID}\", \"uafResponse\": [{\"assertionScheme\": \"UAFV1TLV\", \"assertion\": \"mock_assertion_data\"}]}"
  else
    auth_body="{\"device_id\": \"${DEVICE_ID}\", \"uafResponse\": []}"
  fi

  local auth_response
  auth_response=$(curl -s \
    -X POST "${TENANT_BASE}/v1/authentications/${TRANSACTION_ID}/fido-uaf-authentication" \
    -H "Content-Type: application/json" \
    -d "${auth_body}")

  echo "${auth_response}" | jq .
  echo ""
  echo "Device authentication approved."
}

# デバイス側認証拒否
device_auth_cancel() {
  create_device_jwt || return 1

  echo "--- Getting authentication transaction ---"
  local tx_response
  tx_response=$(curl -s -X GET \
    "${TENANT_BASE}/v1/authentication-devices/${DEVICE_ID}/authentications" \
    -H "Authorization: Bearer ${DEVICE_JWT}")

  TRANSACTION_ID=$(echo "${tx_response}" | jq -r '.list[0].id')

  if [ -z "${TRANSACTION_ID}" ] || [ "${TRANSACTION_ID}" = "null" ]; then
    echo "No authentication transaction found"
    echo "${tx_response}" | jq .
    return 1
  fi

  echo "Transaction ID: ${TRANSACTION_ID}"

  echo ""
  echo "--- Cancelling authentication ---"
  local cancel_response
  cancel_response=$(curl -s \
    -X POST "${TENANT_BASE}/v1/authentications/${TRANSACTION_ID}/authentication-cancel" \
    -H "Content-Type: application/json")

  echo "${cancel_response}" | jq .
  echo ""
  echo "Device authentication cancelled."
}

# ============================================================
# ユーティリティ
# ============================================================

# JWT ペイロードのデコード（base64url → base64 変換 + パディング追加）
decode_jwt_payload() {
  local token="$1"
  local payload
  payload=$(echo "${token}" | cut -d. -f2 | tr '_-' '/+')
  local mod=$((${#payload} % 4))
  if [ $mod -eq 2 ]; then payload="${payload}=="; elif [ $mod -eq 3 ]; then payload="${payload}="; fi
  echo "${payload}" | base64 -d 2>/dev/null
}

# UserInfo 取得
get_userinfo() {
  local token="${1:-${CIBA_ACCESS_TOKEN}}"
  curl -s -H "Authorization: Bearer ${token}" \
    "${TENANT_BASE}/v1/userinfo"
}
