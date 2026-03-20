#!/bin/bash
#
# MFA (Password + FIDO-UAF) - Experiment Helpers
#
# 使い方:
#   source helpers.sh
#   source helpers.sh --org my-organization
#
# 関数一覧:
#
#   === トークン ===
#   get_admin_token              管理者トークン取得
#
#   === 認可フロー ===
#   start_auth_flow              認可リクエスト開始
#   start_auth_flow_with_login_hint  login_hint 付き認可リクエスト
#   register_user                ユーザー登録（initial-registration）
#   password_login               パスワード認証
#   email_challenge              メール OTP チャレンジ送信
#   get_email_verification_code  Management API で検証コード取得
#   email_verify                 メール OTP 検証
#   fido_uaf_reg_challenge       FIDO-UAF 登録チャレンジ
#   fido_uaf_reg                 FIDO-UAF 登録完了
#   complete_auth_flow           認可→コード取得→トークン交換
#
#   === デバイス側 FIDO-UAF 認証 ===
#   get_device_auth_transactions デバイスの認証トランザクション取得
#   fido_uaf_auth_challenge      FIDO-UAF 認証チャレンジ（デバイス側）
#   fido_uaf_auth                FIDO-UAF 認証（デバイス側）
#
#   === ユーティリティ ===
#   get_view_data                ViewData取得
#   get_auth_status              認証ステータス取得
#   get_userinfo                 UserInfo取得
#   decode_jwt_payload           JWTペイロードデコード
#   show_amr                     ID Token の amr 表示

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../../.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"

ORGANIZATION_NAME="mfa-fido-uaf"
while [ $# -gt 0 ]; do
  case "$1" in
    --org) ORGANIZATION_NAME="$2"; shift 2 ;;
    *) echo "Unknown option: $1"; return 1 2>/dev/null || exit 1 ;;
  esac
done

if [ ! -f "${ENV_FILE}" ]; then
  echo "Error: .env file not found at ${ENV_FILE}"
  return 1 2>/dev/null || exit 1
fi

set -a
source "${ENV_FILE}"
set +a

: "${AUTHORIZATION_SERVER_URL:?AUTHORIZATION_SERVER_URL is required in .env}"

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

# Device credentials (saved by Phase 3 fido_uaf_reg)
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
echo "MFA FIDO-UAF Helpers Loaded"
echo "=========================================="
echo "  Server:     ${AUTHORIZATION_SERVER_URL}"
echo "  Tenant ID:  ${PUBLIC_TENANT_ID}"
echo "  Client ID:  ${CLIENT_ID}"
if [ "${DEVICE_CREDENTIALS_LOADED}" = "true" ]; then
  echo "  Device ID:  ${DEVICE_ID}"
  echo "  User Sub:   ${USER_SUB}"
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
  echo "Admin token: acquired"
}

# ============================================================
# 認可フロー関数
# ============================================================

start_auth_flow() {
  local scope="${1:-openid+profile+email}"
  [ -n "${COOKIE_JAR:-}" ] && [ -f "${COOKIE_JAR}" ] && rm -f "${COOKIE_JAR}"
  COOKIE_JAR=$(mktemp)
  STATE="exp-state-$(date +%s)"

  AUTH_REDIRECT=$(curl -s -c "${COOKIE_JAR}" -o /dev/null \
    -w "%{redirect_url}" \
    "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=$(python3 -c "import urllib.parse; print(urllib.parse.quote('${REDIRECT_URI}', safe=''))")&scope=${scope}&state=${STATE}")

  AUTHORIZATION_ID=$(echo "${AUTH_REDIRECT}" | sed -n 's/.*[?&]id=\([^&#]*\).*/\1/p')
  echo "Authorization ID: ${AUTHORIZATION_ID}"
}

start_auth_flow_with_login_hint() {
  local login_hint="$1"
  local scope="${2:-openid+profile+email}"
  [ -n "${COOKIE_JAR:-}" ] && [ -f "${COOKIE_JAR}" ] && rm -f "${COOKIE_JAR}"
  COOKIE_JAR=$(mktemp)
  STATE="exp-state-$(date +%s)"

  AUTH_REDIRECT=$(curl -s -c "${COOKIE_JAR}" -o /dev/null \
    -w "%{redirect_url}" \
    "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=$(python3 -c "import urllib.parse; print(urllib.parse.quote('${REDIRECT_URI}', safe=''))")&scope=${scope}&state=${STATE}&login_hint=$(python3 -c "import urllib.parse; print(urllib.parse.quote('${login_hint}', safe=''))")")

  AUTHORIZATION_ID=$(echo "${AUTH_REDIRECT}" | sed -n 's/.*[?&]id=\([^&#]*\).*/\1/p')
  echo "Authorization ID: ${AUTHORIZATION_ID} (login_hint: ${login_hint})"
}

register_user() {
  local email="${1:-verify-$(date +%s)@example.com}"
  local password="${2:-VerifyPass123}"
  local name="${3:-Verify User}"

  TEST_EMAIL="${email}"
  TEST_PASSWORD="${password}"

  local response http_code body
  response=$(curl -s -w "\n%{http_code}" -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
    -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/initial-registration" \
    -H "Content-Type: application/json" \
    -d "{\"email\": \"${email}\", \"password\": \"${password}\", \"name\": \"${name}\"}")

  http_code=$(echo "${response}" | tail -1)
  body=$(echo "${response}" | sed '$d')
  USER_SUB=$(echo "${body}" | jq -r '.user.sub // empty')

  if [ "${http_code}" = "200" ]; then
    echo "← ${http_code} User registered: ${email} (sub: ${USER_SUB})" >&2
  else
    echo "← ${http_code} Registration failed" >&2
    echo "${body}" >&2
  fi
  echo "${body}"
}

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

  if [ "${http_code}" = "200" ]; then
    echo "← ${http_code} Password authenticated: ${username}" >&2
  else
    echo "← ${http_code} Password authentication failed" >&2
    echo "${body}" >&2
  fi
  echo "${body}"
}

email_challenge() {
  local email="$1"

  local response http_code body
  response=$(curl -s -w "\n%{http_code}" -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
    -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/email-authentication-challenge" \
    -H "Content-Type: application/json" \
    -d "{\"email\": \"${email}\"}")

  http_code=$(echo "${response}" | tail -1)
  body=$(echo "${response}" | sed '$d')

  if [ "${http_code}" = "200" ]; then
    echo "← ${http_code} Email OTP sent" >&2
  else
    echo "← ${http_code} Email challenge failed" >&2
    echo "${body}" >&2
  fi
  echo "${body}"
}

# Management API で検証コードを取得（http_request_store に保存された検証コード）
get_email_verification_code() {
  local transaction_response transaction_id interaction_response

  transaction_response=$(curl -s \
    "${AUTHORIZATION_SERVER_URL}/v1/management/organizations/${ORG_ID}/tenants/${PUBLIC_TENANT_ID}/authentication-transactions?authorization_id=${AUTHORIZATION_ID}" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}")

  transaction_id=$(echo "${transaction_response}" | jq -r '.list[0].id')

  if [ -z "${transaction_id}" ] || [ "${transaction_id}" = "null" ]; then
    echo "Error: Failed to get transaction ID" >&2
    return 1
  fi

  interaction_response=$(curl -s \
    "${AUTHORIZATION_SERVER_URL}/v1/management/organizations/${ORG_ID}/tenants/${PUBLIC_TENANT_ID}/authentication-interactions/${transaction_id}/email-authentication-challenge" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}")

  VERIFICATION_CODE=$(echo "${interaction_response}" | jq -r '.payload.verification_code')

  if [ -z "${VERIFICATION_CODE}" ] || [ "${VERIFICATION_CODE}" = "null" ]; then
    echo "Error: Failed to get verification code" >&2
    return 1
  fi

  echo "Verification Code: ${VERIFICATION_CODE}"
}

email_verify() {
  local code="${1:-${VERIFICATION_CODE}}"

  local response http_code body
  response=$(curl -s -w "\n%{http_code}" -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
    -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/email-authentication" \
    -H "Content-Type: application/json" \
    -d "{\"verification_code\": \"${code}\"}")

  http_code=$(echo "${response}" | tail -1)
  body=$(echo "${response}" | sed '$d')

  if [ "${http_code}" = "200" ]; then
    echo "← ${http_code} Email verified" >&2
  else
    echo "← ${http_code} Email verification failed" >&2
    echo "${body}" >&2
  fi
  echo "${body}"
}

fido_uaf_reg_challenge() {
  local response http_code body
  response=$(curl -s -w "\n%{http_code}" -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
    -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/fido-uaf-registration-challenge" \
    -H "Content-Type: application/json" \
    -d '{"app_name":"FIDO-UAF Device","platform":"Android","os":"Android15","model":"Pixel 9","notification_channel":"fcm","notification_token":"demo-token"}')

  http_code=$(echo "${response}" | tail -1)
  body=$(echo "${response}" | sed '$d')

  if [ "${http_code}" = "200" ]; then
    echo "← ${http_code} FIDO-UAF registration challenge received" >&2
  elif [ "${http_code}" = "400" ]; then
    local error=$(echo "${body}" | jq -r '.error')
    echo "← ${http_code} ${error}: $(echo "${body}" | jq -r '.error_description')" >&2
  else
    echo "← ${http_code} FIDO-UAF registration challenge failed" >&2
    echo "${body}" >&2
  fi
  echo "${body}"
}

fido_uaf_reg() {
  local response http_code body
  response=$(curl -s -w "\n%{http_code}" -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
    -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/fido-uaf-registration" \
    -H "Content-Type: application/json" \
    -d '{"uafResponse":[{"assertionScheme":"UAFV1TLV","assertion":"mock"}]}')

  http_code=$(echo "${response}" | tail -1)
  body=$(echo "${response}" | sed '$d')
  DEVICE_ID=$(echo "${body}" | jq -r '.device_id // empty')
  DEVICE_SECRET=$(echo "${body}" | jq -r '.device_secret // empty')
  DEVICE_SECRET_ALGORITHM=$(echo "${body}" | jq -r '.device_secret_algorithm // "HS256"')
  DEVICE_SECRET_JWT_ISSUER=$(echo "${body}" | jq -r '.device_secret_jwt_issuer // empty')

  if [ "${http_code}" = "200" ] && [ -n "${DEVICE_ID}" ]; then
    # Save device credentials for Phase 4
    jq -n \
      --arg device_id "${DEVICE_ID}" \
      --arg device_secret "${DEVICE_SECRET}" \
      --arg device_secret_algorithm "${DEVICE_SECRET_ALGORITHM}" \
      --arg device_secret_jwt_issuer "${DEVICE_SECRET_JWT_ISSUER}" \
      --arg user_sub "${USER_SUB}" \
      --arg user_email "${TEST_EMAIL}" \
      --arg user_password "${TEST_PASSWORD}" \
      '{device_id: $device_id, device_secret: $device_secret, device_secret_algorithm: $device_secret_algorithm, device_secret_jwt_issuer: $device_secret_jwt_issuer, user_sub: $user_sub, user_email: $user_email, user_password: $user_password}' \
      > "${DEVICE_CREDENTIALS_FILE}"
    DEVICE_CREDENTIALS_LOADED=true
    echo "← ${http_code} FIDO-UAF registered (device_id: ${DEVICE_ID})" >&2
    echo "  Device credentials saved to: ${DEVICE_CREDENTIALS_FILE}" >&2
  elif [ "${http_code}" = "200" ]; then
    echo "← ${http_code} FIDO-UAF registered (no device_id in response)" >&2
  else
    echo "← ${http_code} FIDO-UAF registration failed" >&2
    echo "${body}" >&2
  fi
  echo "${body}"
}

complete_auth_flow() {
  AUTHORIZE_RESPONSE=$(curl -s \
    -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
    -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/authorize" \
    -H "Content-Type: application/json" -d '{}')

  AUTHZ_REDIRECT_URI=$(echo "${AUTHORIZE_RESPONSE}" | jq -r '.redirect_uri')
  AUTHORIZATION_CODE=$(echo "${AUTHZ_REDIRECT_URI}" | sed -n 's/.*[?&]code=\([^&#]*\).*/\1/p')

  if [ -z "${AUTHORIZATION_CODE}" ]; then
    echo "Error: authorize failed" >&2
    echo "${AUTHORIZE_RESPONSE}" | jq '.' >&2
    return 1
  fi

  TOKEN_RESPONSE=$(curl -s \
    -X POST "${TENANT_BASE}/v1/tokens" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    --data-urlencode "grant_type=authorization_code" \
    --data-urlencode "code=${AUTHORIZATION_CODE}" \
    --data-urlencode "redirect_uri=${REDIRECT_URI}" \
    --data-urlencode "client_id=${CLIENT_ID}" \
    --data-urlencode "client_secret=${CLIENT_SECRET}")

  ACCESS_TOKEN=$(echo "${TOKEN_RESPONSE}" | jq -r '.access_token')
  ID_TOKEN=$(echo "${TOKEN_RESPONSE}" | jq -r '.id_token')
  REFRESH_TOKEN=$(echo "${TOKEN_RESPONSE}" | jq -r '.refresh_token')
  echo "${TOKEN_RESPONSE}" | jq '{token_type, expires_in, scope}'
}

# ============================================================
# JWT 生成
# ============================================================

base64url_encode() {
  openssl base64 -e -A | tr '+/' '-_' | tr -d '='
}

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

create_device_jwt() {
  if [ "${DEVICE_CREDENTIALS_LOADED}" != "true" ]; then
    echo "Error: Device credentials not loaded. Run Phase 3 (fido_uaf_reg) first." >&2
    return 1
  fi

  local now=$(date +%s)
  local exp=$((now + 300))
  local jti=$(uuidgen | tr '[:upper:]' '[:lower:]')

  local payload=$(jq -n \
    --arg iss "${DEVICE_SECRET_JWT_ISSUER}" \
    --arg sub "${DEVICE_ID}" \
    --arg aud "${TENANT_BASE}" \
    --arg jti "${jti}" \
    --argjson exp "${exp}" \
    --argjson iat "${now}" \
    '{iss: $iss, sub: $sub, aud: $aud, jti: $jti, exp: $exp, iat: $iat}')

  DEVICE_JWT=$(create_hs256_jwt "${DEVICE_SECRET}" "${payload}")
  echo "Device JWT: ${DEVICE_JWT:0:30}..." >&2
}

# ============================================================
# デバイス側 FIDO-UAF 認証
# ============================================================

# デバイスの認証トランザクション取得（デバイスAPI + device_secret_jwt）
get_device_auth_transactions() {
  create_device_jwt || return 1

  local tx_response
  tx_response=$(curl -s -X GET \
    "${TENANT_BASE}/v1/authentication-devices/${DEVICE_ID}/authentications" \
    -H "Authorization: Bearer ${DEVICE_JWT}")

  TRANSACTION_ID=$(echo "${tx_response}" | jq -r '.list[0].id')

  if [ -z "${TRANSACTION_ID}" ] || [ "${TRANSACTION_ID}" = "null" ]; then
    echo "Error: No authentication transaction found for device ${DEVICE_ID}" >&2
    echo "${tx_response}" | jq '.' >&2
    return 1
  fi

  echo "Transaction ID: ${TRANSACTION_ID}"
  echo "${tx_response}" | jq '.list[0] | {id, flow, client_id}'
}

# FIDO-UAF 認証チャレンジ（デバイス側 /authentications/ エンドポイント）
fido_uaf_auth_challenge() {
  local tx_id="${1:-${TRANSACTION_ID}}"

  local response http_code body
  response=$(curl -s -w "\n%{http_code}" \
    -X POST "${TENANT_BASE}/v1/authentications/${tx_id}/fido-uaf-authentication-challenge" \
    -H "Content-Type: application/json" -d '{}')

  http_code=$(echo "${response}" | tail -1)
  body=$(echo "${response}" | sed '$d')

  if [ "${http_code}" = "200" ]; then
    echo "← ${http_code} FIDO-UAF authentication challenge received" >&2
  else
    echo "← ${http_code} FIDO-UAF authentication challenge failed" >&2
    echo "${body}" >&2
  fi
  echo "${body}"
}

# FIDO-UAF 認証（デバイス側 /authentications/ エンドポイント）
fido_uaf_auth() {
  local tx_id="${1:-${TRANSACTION_ID}}"

  local response http_code body
  response=$(curl -s -w "\n%{http_code}" \
    -X POST "${TENANT_BASE}/v1/authentications/${tx_id}/fido-uaf-authentication" \
    -H "Content-Type: application/json" -d '{}')

  http_code=$(echo "${response}" | tail -1)
  body=$(echo "${response}" | sed '$d')

  if [ "${http_code}" = "200" ]; then
    echo "← ${http_code} FIDO-UAF authentication success" >&2
  else
    echo "← ${http_code} FIDO-UAF authentication failed" >&2
    echo "${body}" >&2
  fi
  echo "${body}"
}

# ============================================================
# ユーティリティ
# ============================================================

get_view_data() {
  curl -s -b "${COOKIE_JAR}" "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/view-data"
}

get_auth_status() {
  curl -s -b "${COOKIE_JAR}" "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/authentication-status"
}

get_userinfo() {
  local token="${1:-${ACCESS_TOKEN}}"
  curl -s -H "Authorization: Bearer ${token}" "${TENANT_BASE}/v1/userinfo"
}

decode_jwt_payload() {
  local token="$1"
  local payload
  payload=$(echo "${token}" | cut -d. -f2 | tr '_-' '/+')
  local mod=$((${#payload} % 4))
  if [ $mod -eq 2 ]; then payload="${payload}=="; elif [ $mod -eq 3 ]; then payload="${payload}="; fi
  echo "${payload}" | base64 -d 2>/dev/null
}

show_amr() {
  local token="${1:-${ID_TOKEN}}"
  decode_jwt_payload "${token}" | python3 -c "
import sys, json
data = json.load(sys.stdin)
amr = data.get('amr')
if amr:
    print('amr:', json.dumps(amr))
else:
    print('amr not found in ID Token')
print('sub:', data.get('sub', 'N/A'))
"
}

# ============================================================
# クイックスタート
#
#   source helpers.sh
#   get_admin_token
#
#   # Phase 1: ユーザー登録
#   start_auth_flow
#   register_user
#   complete_auth_flow
#
#   # Phase 2: login_hint + パスワード認証
#   start_auth_flow_with_login_hint "sub:${USER_SUB}"
#   get_view_data | jq '.login_hint'
#   get_auth_status | jq '.status'
#   password_login "${TEST_EMAIL}" "${TEST_PASSWORD}"
#   get_auth_status | jq '.status'
#   complete_auth_flow
#   show_amr
#
#   # Phase 3: デバイス登録条件の検証
#   start_auth_flow
#   password_login "${TEST_EMAIL}" "${TEST_PASSWORD}"
#   fido_uaf_reg_challenge        # → forbidden (MFA not met)
#   email_challenge "${TEST_EMAIL}"
#   get_email_verification_code
#   email_verify
#   fido_uaf_reg_challenge        # → 200 OK
#   fido_uaf_reg
#   complete_auth_flow
#
#   # Phase 4: 認可コードフロー + FIDO-UAF 認証
#   start_auth_flow_with_login_hint "sub:${USER_SUB}"
#   get_device_auth_transactions
#   fido_uaf_auth_challenge
#   fido_uaf_auth
#   get_auth_status | jq '{status, authentication_methods}'
#   complete_auth_flow
#   show_amr                      # → amr: ["fido-uaf"]
#
# ============================================================
