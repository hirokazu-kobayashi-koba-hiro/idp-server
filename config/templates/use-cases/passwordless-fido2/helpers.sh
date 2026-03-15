#!/bin/bash
#
# Passwordless (FIDO2/WebAuthn) - Experiment Helpers
#
# EXPERIMENTS-basics.md の各実験で使う共通関数と変数を定義する。
#
# 使い方:
#   source helpers.sh
#   source helpers.sh --org my-organization
#

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../../.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"

# Parse arguments
ORGANIZATION_NAME="passwordless-fido2"
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
AUTH_POLICY_ID=$(jq -r '.id' "${CONFIG_DIR}/authentication-policy.json")
AUTH_POLICY_JSON=$(cat "${CONFIG_DIR}/authentication-policy.json")
AUTH_POLICY_FIDO2_REG_ID=$(jq -r '.id' "${CONFIG_DIR}/authentication-policy-fido2-registration.json")
AUTH_POLICY_FIDO2_REG_JSON=$(cat "${CONFIG_DIR}/authentication-policy-fido2-registration.json")
FIDO2_AUTH_CONFIG_ID=$(jq -r '.id' "${CONFIG_DIR}/authentication-config-fido2.json")
FIDO2_AUTH_CONFIG_JSON=$(cat "${CONFIG_DIR}/authentication-config-fido2.json")
CLIENT_JSON=$(cat "${CONFIG_DIR}/public-client.json")

echo "=========================================="
echo "Experiment Helpers Loaded (Passwordless FIDO2)"
echo "=========================================="
echo "  Server:       ${AUTHORIZATION_SERVER_URL}"
echo "  Organization: ${ORGANIZATION_NAME}"
echo "  Tenant ID:    ${PUBLIC_TENANT_ID}"
echo "  Client ID:    ${CLIENT_ID}"
echo "  Redirect URI: ${REDIRECT_URI}"
echo "  FIDO2 Config: ${FIDO2_AUTH_CONFIG_ID}"
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
#   update_tenant '.identity_policy_config.authentication_device_rule.max_devices = 1'
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
#   update_auth_server '.extension.access_token_duration = 10'
#   update_auth_server '.claims_supported = ["sub", "iss"]'
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
# 認証ポリシー更新ヘルパー（OAuth フロー用）
#
# modify_auth_policy: $AUTH_POLICY_JSON をベースに jq フィルタで部分変更（推奨）
# update_auth_policy: 生の JSON を直接渡す（大きな JSON はターミナルで壊れやすいので非推奨）
#
# 使用例:
#   modify_auth_policy '.policies[0].available_methods = ["fido2", "email"]'
# ============================================================

modify_auth_policy() {
  local jq_filter="$1"
  local updated
  updated=$(echo "${AUTH_POLICY_JSON}" | jq "${jq_filter}")
  update_auth_policy "${updated}"
}

update_auth_policy() {
  local json="$1"
  local response http_code

  response=$(curl -s -w "\n%{http_code}" -X PUT \
    "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authentication-policies/${AUTH_POLICY_ID}" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${json}")

  http_code=$(echo "${response}" | tail -1)
  local body
  body=$(echo "${response}" | sed '$d')

  if [ "${http_code}" != "200" ]; then
    echo "Error: update_auth_policy failed (HTTP ${http_code})" >&2
    echo "${body}" >&2
    return 1
  fi

  echo "${body}"
}

restore_auth_policy() {
  local response http_code
  response=$(curl -s -w "\n%{http_code}" -X PUT \
    "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authentication-policies/${AUTH_POLICY_ID}" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${AUTH_POLICY_JSON}")

  http_code=$(echo "${response}" | tail -1)

  if [ "${http_code}" != "200" ]; then
    echo "Error: restore_auth_policy failed (HTTP ${http_code})" >&2
    echo "${response}" | sed '$d' >&2
    return 1
  fi

  echo "Authentication policy restored."
}

# ============================================================
# FIDO2 デバイス登録ポリシー更新ヘルパー
# ============================================================

modify_fido2_reg_policy() {
  local jq_filter="$1"
  local updated
  updated=$(echo "${AUTH_POLICY_FIDO2_REG_JSON}" | jq "${jq_filter}")
  update_fido2_reg_policy "${updated}"
}

update_fido2_reg_policy() {
  local json="$1"
  local response http_code

  response=$(curl -s -w "\n%{http_code}" -X PUT \
    "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authentication-policies/${AUTH_POLICY_FIDO2_REG_ID}" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${json}")

  http_code=$(echo "${response}" | tail -1)
  local body
  body=$(echo "${response}" | sed '$d')

  if [ "${http_code}" != "200" ]; then
    echo "Error: update_fido2_reg_policy failed (HTTP ${http_code})" >&2
    echo "${body}" >&2
    return 1
  fi

  echo "${body}"
}

restore_fido2_reg_policy() {
  local response http_code
  response=$(curl -s -w "\n%{http_code}" -X PUT \
    "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authentication-policies/${AUTH_POLICY_FIDO2_REG_ID}" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${AUTH_POLICY_FIDO2_REG_JSON}")

  http_code=$(echo "${response}" | tail -1)

  if [ "${http_code}" != "200" ]; then
    echo "Error: restore_fido2_reg_policy failed (HTTP ${http_code})" >&2
    echo "${response}" | sed '$d' >&2
    return 1
  fi

  echo "FIDO2 registration policy restored."
}

# ============================================================
# FIDO2 認証設定更新ヘルパー
#
# FIDO2 認証設定は interactions 内の 4 つの execution.details に同じパラメータを持つ。
# この関数は 4 つの details すべてに同じ jq フィルタを適用する。
#
# 使用例:
#   update_fido2_auth_config '.authenticator_attachment = "cross-platform"'
#   update_fido2_auth_config '.user_verification_required = false'
#   update_fido2_auth_config '.attestation_preference = "none"'
# ============================================================

update_fido2_auth_config() {
  local jq_filter="$1"
  local updated response http_code
  updated=$(echo "${FIDO2_AUTH_CONFIG_JSON}" | jq "
    .interactions.\"fido2-registration-challenge\".execution.details |= (${jq_filter}) |
    .interactions.\"fido2-registration\".execution.details |= (${jq_filter}) |
    .interactions.\"fido2-authentication-challenge\".execution.details |= (${jq_filter}) |
    .interactions.\"fido2-authentication\".execution.details |= (${jq_filter})
  ")

  response=$(curl -s -w "\n%{http_code}" -X PUT \
    "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authentication-configurations/${FIDO2_AUTH_CONFIG_ID}" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${updated}")

  http_code=$(echo "${response}" | tail -1)
  local body
  body=$(echo "${response}" | sed '$d')

  if [ "${http_code}" != "200" ]; then
    echo "Error: update_fido2_auth_config failed (HTTP ${http_code})" >&2
    echo "${body}" >&2
    return 1
  fi

  echo "${body}"
}

restore_fido2_auth_config() {
  local response http_code
  response=$(curl -s -w "\n%{http_code}" -X PUT \
    "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authentication-configurations/${FIDO2_AUTH_CONFIG_ID}" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${FIDO2_AUTH_CONFIG_JSON}")

  http_code=$(echo "${response}" | tail -1)

  if [ "${http_code}" != "200" ]; then
    echo "Error: restore_fido2_auth_config failed (HTTP ${http_code})" >&2
    echo "${response}" | sed '$d' >&2
    return 1
  fi

  echo "FIDO2 authentication config restored."
}

# FIDO2 認証設定の現在の details を表示（変更確認用）
show_fido2_details() {
  local response
  response=$(curl -s \
    "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authentication-configurations/${FIDO2_AUTH_CONFIG_ID}" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}")

  echo "${response}" | jq '.interactions["fido2-registration-challenge"].execution.details'
}

# ============================================================
# クライアント更新ヘルパー
#
# 重要: PUT API はフル置換。$CLIENT_JSON をベースに jq で変更して使う。
#
# 使用例:
#   update_client '.scope = "openid profile"'
#   update_client '.extension.access_token_duration = 30'
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
# 認可フロー関数
# ============================================================

# 認可リクエスト開始（新しい COOKIE_JAR で毎回クリーンに）
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

# view-data 取得（認証ポリシー・クライアント情報等）
get_view_data() {
  curl -s -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
    "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/view-data"
}

# ユーザー登録
register_user() {
  local email="$1"
  local password="$2"
  local name="${3:-Test User}"

  curl -s -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
    -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/initial-registration" \
    -H "Content-Type: application/json" \
    -d "{\"email\": \"${email}\", \"password\": \"${password}\", \"name\": \"${name}\"}"
}

# パスワード認証
password_login() {
  local username="$1"
  local password="$2"

  curl -s -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
    -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/password-authentication" \
    -H "Content-Type: application/json" \
    -d "{\"username\": \"${username}\", \"password\": \"${password}\"}"
}

# email 認証チャレンジ（verification code を取得）
email_challenge() {
  local email="$1"
  local template="${2:-authentication}"

  curl -s -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
    -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/email-authentication-challenge" \
    -H "Content-Type: application/json" \
    -d "{\"email\": \"${email}\", \"template\": \"${template}\"}"
}

# email 認証コード検証
email_verify() {
  local verification_code="$1"

  curl -s -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
    -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/email-authentication" \
    -H "Content-Type: application/json" \
    -d "{\"verification_code\": \"${verification_code}\"}"
}

# 認可 → コード取得 → トークン交換
complete_auth_flow() {
  AUTHORIZE_RESPONSE=$(curl -s \
    -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
    -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/authorize" \
    -H "Content-Type: application/json" \
    -d '{}')

  AUTHZ_REDIRECT_URI=$(echo "${AUTHORIZE_RESPONSE}" | jq -r '.redirect_uri')
  AUTHORIZATION_CODE=$(echo "${AUTHZ_REDIRECT_URI}" | sed -n 's/.*[?&]code=\([^&#]*\).*/\1/p')

  TOKEN_RESPONSE=$(curl -s \
    -X POST "${TENANT_BASE}/v1/tokens" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    --data-urlencode "grant_type=authorization_code" \
    --data-urlencode "code=${AUTHORIZATION_CODE}" \
    --data-urlencode "redirect_uri=${REDIRECT_URI}" \
    --data-urlencode "client_id=${CLIENT_ID}" \
    --data-urlencode "client_secret=${CLIENT_SECRET}")

  ACCESS_TOKEN=$(echo "${TOKEN_RESPONSE}" | jq -r '.access_token')
  REFRESH_TOKEN=$(echo "${TOKEN_RESPONSE}" | jq -r '.refresh_token')
  echo "${TOKEN_RESPONSE}" | jq '{token_type, expires_in}'
}

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
  local token="${1:-${ACCESS_TOKEN}}"
  curl -s -H "Authorization: Bearer ${token}" \
    "${TENANT_BASE}/v1/userinfo"
}

# prompt=none で認可リクエスト（既存セッションの COOKIE_JAR を使う）
# セッション有効 → redirect_uri に code 付き、セッション切れ → error=login_required
try_prompt_none() {
  local label="${1:-prompt=none}"
  local redirect_url

  redirect_url=$(curl -s -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" -o /dev/null \
    -w "%{redirect_url}" \
    "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=$(python3 -c "import urllib.parse; print(urllib.parse.quote('${REDIRECT_URI}', safe=''))")&scope=openid+profile+email&state=prompt-none-$(date +%s)&prompt=none")

  local code error
  code=$(echo "${redirect_url}" | sed -n 's/.*[?&]code=\([^&#]*\).*/\1/p')
  error=$(echo "${redirect_url}" | sed -n 's/.*[?&]error=\([^&#]*\).*/\1/p')

  echo "--- ${label} ---"
  if [ -n "${code}" ]; then
    echo "  Result: session valid (code issued)"
  elif [ -n "${error}" ]; then
    echo "  Result: ${error}"
  else
    echo "  Result: redirect to login (session expired)"
    echo "  Redirect: ${redirect_url}"
  fi
}
