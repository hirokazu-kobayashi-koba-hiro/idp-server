#!/bin/bash
#
# MFA (Password + Email OTP) - Experiment Helpers
#
# 各動作確認で使う共通関数と変数を定義する。
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
#   === テナント・認可サーバー・クライアント更新 ===
#   update_tenant                テナント設定更新（jqフィルタ）
#   restore_tenant               テナント設定を初期状態に復元
#   update_auth_server           認可サーバー設定更新（jqフィルタ）
#   restore_auth_server          認可サーバー設定を初期状態に復元
#   update_client                クライアント設定更新（jqフィルタ）
#   restore_client               クライアント設定を初期状態に復元
#
#   === 認証設定 ===
#   update_registration_config   ユーザー登録設定更新（jqフィルタ）
#   restore_registration_config  ユーザー登録設定を初期状態に復元
#   get_registration_config      ユーザー登録設定取得
#   update_email_config          Email OTP設定更新（jqフィルタ）
#   restore_email_config         Email OTP設定を初期状態に復元
#   get_email_config             Email OTP設定取得
#   update_auth_policy           認証ポリシー更新（jqフィルタ）
#   update_auth_policy_json      認証ポリシーをJSON全体で更新
#   restore_auth_policy          認証ポリシーを初期状態に復元
#   get_auth_policy              認証ポリシー取得
#
#   === 認可フロー ===
#   start_auth_flow              認可リクエスト開始
#   register_user                ユーザー登録（initial-registration）
#   email_challenge              Email OTP チャレンジ送信
#   get_verification_code        Management API で検証コード取得
#   email_verify                 Email OTP 検証
#   password_login               パスワード認証
#   complete_auth_flow           認可→コード取得→トークン交換
#   mfa_login                    MFA ログイン一括実行（email challenge → verify → password → authorize → token）
#
#   === ユーティリティ ===
#   get_view_data                ViewData取得（適用ポリシー・認証状態）
#   get_userinfo                 UserInfo取得
#   decode_jwt_payload           JWTペイロードデコード
#   show_amr                     ID Token の amr（認証方式）表示
#   try_prompt_none              prompt=noneでセッション確認
#   get_discovery                Discoveryエンドポイント取得
#   restore_all                  全設定を初期状態に復元

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../../.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"

# Parse arguments
ORGANIZATION_NAME="mfa-email"
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

# 生成済み設定をベースとして読み込む（更新APIはフル置換のため）
# *_INITIAL は restore 用の初期値、*_JSON は update が追従する現在値
TENANT_JSON_INITIAL=$(jq '.tenant' "${CONFIG_DIR}/public-tenant.json")
TENANT_JSON="${TENANT_JSON_INITIAL}"
AUTH_SERVER_JSON_INITIAL=$(jq '.authorization_server' "${CONFIG_DIR}/public-tenant.json")
AUTH_SERVER_JSON="${AUTH_SERVER_JSON_INITIAL}"
CLIENT_JSON_INITIAL=$(cat "${CONFIG_DIR}/public-client.json")
CLIENT_JSON="${CLIENT_JSON_INITIAL}"

AUTH_CONFIG_ID=$(jq -r '.id' "${CONFIG_DIR}/authentication-config-initial-registration.json")
AUTH_CONFIG_JSON_INITIAL=$(cat "${CONFIG_DIR}/authentication-config-initial-registration.json")
AUTH_CONFIG_JSON="${AUTH_CONFIG_JSON_INITIAL}"

EMAIL_AUTH_CONFIG_ID=$(jq -r '.id' "${CONFIG_DIR}/authentication-config-email.json")
EMAIL_AUTH_CONFIG_JSON_INITIAL=$(cat "${CONFIG_DIR}/authentication-config-email.json")
EMAIL_AUTH_CONFIG_JSON="${EMAIL_AUTH_CONFIG_JSON_INITIAL}"

AUTH_POLICY_ID=$(jq -r '.id' "${CONFIG_DIR}/authentication-policy.json")
AUTH_POLICY_JSON_INITIAL=$(cat "${CONFIG_DIR}/authentication-policy.json")
AUTH_POLICY_JSON="${AUTH_POLICY_JSON_INITIAL}"

echo "=========================================="
echo "MFA Email Experiment Helpers Loaded"
echo "=========================================="
echo "  Server:              ${AUTHORIZATION_SERVER_URL}"
echo "  Organization:        ${ORGANIZATION_NAME}"
echo "  Tenant ID:           ${PUBLIC_TENANT_ID}"
echo "  Client ID:           ${CLIENT_ID}"
echo "  Redirect URI:        ${REDIRECT_URI}"
echo "  Auth Config ID:      ${AUTH_CONFIG_ID}"
echo "  Email Config ID:     ${EMAIL_AUTH_CONFIG_ID}"
echo "  Auth Policy ID:      ${AUTH_POLICY_ID}"
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
#   update_tenant '.session_config.timeout_seconds = 15'
# ============================================================

update_tenant() {
  local jq_filter="$1"
  local updated response http_code
  local tmpfile
  tmpfile=$(mktemp)
  printf '%s\n' "${TENANT_JSON}" | jq "${jq_filter}" > "${tmpfile}"
  updated=$(cat "${tmpfile}")

  response=$(curl -s -w "\n%{http_code}" -X PUT "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d @"${tmpfile}")

  rm -f "${tmpfile}"

  http_code=$(echo "${response}" | tail -1)
  local body
  body=$(echo "${response}" | sed '$d')

  if [ "${http_code}" != "200" ]; then
    echo "Error: update_tenant failed (HTTP ${http_code})" >&2
    echo "${body}" >&2
    return 1
  fi

  TENANT_JSON="${updated}"
  echo "${body}"
}

restore_tenant() {
  local response http_code
  local tmpfile
  tmpfile=$(mktemp)
  printf '%s\n' "${TENANT_JSON_INITIAL}" > "${tmpfile}"

  response=$(curl -s -w "\n%{http_code}" -X PUT "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d @"${tmpfile}")

  rm -f "${tmpfile}"

  http_code=$(echo "${response}" | tail -1)

  if [ "${http_code}" != "200" ]; then
    echo "Error: restore_tenant failed (HTTP ${http_code})" >&2
    echo "${response}" | sed '$d' >&2
    return 1
  fi

  TENANT_JSON="${TENANT_JSON_INITIAL}"
  echo "Tenant restored."
}

# ============================================================
# 認可サーバー更新ヘルパー
#
# 使用例:
#   update_auth_server '.extension.access_token_duration = 10'
# ============================================================

update_auth_server() {
  local jq_filter="$1"
  local updated response http_code
  local tmpfile
  tmpfile=$(mktemp)
  printf '%s\n' "${AUTH_SERVER_JSON}" | jq "${jq_filter}" > "${tmpfile}"
  updated=$(cat "${tmpfile}")

  response=$(curl -s -w "\n%{http_code}" -X PUT \
    "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authorization-server" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d @"${tmpfile}")

  rm -f "${tmpfile}"

  http_code=$(echo "${response}" | tail -1)
  local body
  body=$(echo "${response}" | sed '$d')

  if [ "${http_code}" != "200" ]; then
    echo "Error: update_auth_server failed (HTTP ${http_code})" >&2
    echo "${body}" >&2
    return 1
  fi

  AUTH_SERVER_JSON="${updated}"
  echo "${body}"
}

restore_auth_server() {
  local response http_code
  local tmpfile
  tmpfile=$(mktemp)
  printf '%s\n' "${AUTH_SERVER_JSON_INITIAL}" > "${tmpfile}"

  response=$(curl -s -w "\n%{http_code}" -X PUT \
    "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authorization-server" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d @"${tmpfile}")

  rm -f "${tmpfile}"

  http_code=$(echo "${response}" | tail -1)

  if [ "${http_code}" != "200" ]; then
    echo "Error: restore_auth_server failed (HTTP ${http_code})" >&2
    echo "${response}" | sed '$d' >&2
    return 1
  fi

  AUTH_SERVER_JSON="${AUTH_SERVER_JSON_INITIAL}"
  echo "Authorization server restored."
}

# ============================================================
# クライアント更新ヘルパー
#
# 使用例:
#   update_client '.scope = "openid profile email"'
# ============================================================

update_client() {
  local jq_filter="$1"
  local updated response http_code
  local tmpfile
  tmpfile=$(mktemp)
  printf '%s\n' "${CLIENT_JSON}" | jq "${jq_filter}" > "${tmpfile}"
  updated=$(cat "${tmpfile}")

  response=$(curl -s -w "\n%{http_code}" -X PUT \
    "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/clients/${CLIENT_ID}" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d @"${tmpfile}")

  rm -f "${tmpfile}"

  http_code=$(echo "${response}" | tail -1)
  local body
  body=$(echo "${response}" | sed '$d')

  if [ "${http_code}" != "200" ]; then
    echo "Error: update_client failed (HTTP ${http_code})" >&2
    echo "${body}" >&2
    return 1
  fi

  CLIENT_JSON="${updated}"
  echo "${body}"
}

restore_client() {
  local response http_code
  local tmpfile
  tmpfile=$(mktemp)
  printf '%s\n' "${CLIENT_JSON_INITIAL}" > "${tmpfile}"

  response=$(curl -s -w "\n%{http_code}" -X PUT \
    "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/clients/${CLIENT_ID}" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d @"${tmpfile}")

  rm -f "${tmpfile}"

  http_code=$(echo "${response}" | tail -1)

  if [ "${http_code}" != "200" ]; then
    echo "Error: restore_client failed (HTTP ${http_code})" >&2
    echo "${response}" | sed '$d' >&2
    return 1
  fi

  CLIENT_JSON="${CLIENT_JSON_INITIAL}"
  echo "Client restored."
}

# ============================================================
# ユーザー登録設定更新ヘルパー
#
# 使用例:
#   update_registration_config '.interactions["initial-registration"].request.schema.required += ["phone_number"]'
# ============================================================

update_registration_config() {
  local jq_filter="$1"
  local updated response http_code
  local tmpfile
  tmpfile=$(mktemp)
  printf '%s\n' "${AUTH_CONFIG_JSON}" | jq "${jq_filter}" > "${tmpfile}"
  updated=$(cat "${tmpfile}")

  response=$(curl -s -w "\n%{http_code}" -X PUT \
    "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authentication-configurations/${AUTH_CONFIG_ID}" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d @"${tmpfile}")

  rm -f "${tmpfile}"

  http_code=$(echo "${response}" | tail -1)
  local body
  body=$(echo "${response}" | sed '$d')

  if [ "${http_code}" != "200" ]; then
    echo "Error: update_registration_config failed (HTTP ${http_code})" >&2
    echo "${body}" >&2
    return 1
  fi

  AUTH_CONFIG_JSON="${updated}"
  echo "${body}"
}

restore_registration_config() {
  local response http_code
  local tmpfile
  tmpfile=$(mktemp)
  printf '%s\n' "${AUTH_CONFIG_JSON_INITIAL}" > "${tmpfile}"

  response=$(curl -s -w "\n%{http_code}" -X PUT \
    "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authentication-configurations/${AUTH_CONFIG_ID}" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d @"${tmpfile}")

  rm -f "${tmpfile}"

  http_code=$(echo "${response}" | tail -1)

  if [ "${http_code}" != "200" ]; then
    echo "Error: restore_registration_config failed (HTTP ${http_code})" >&2
    echo "${response}" | sed '$d' >&2
    return 1
  fi

  AUTH_CONFIG_JSON="${AUTH_CONFIG_JSON_INITIAL}"
  echo "Registration config restored."
}

get_registration_config() {
  curl -s "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authentication-configurations/${AUTH_CONFIG_ID}" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}"
}

# ============================================================
# Email OTP 設定更新ヘルパー
#
# 使用例:
#   update_email_config '.interactions["email-authentication-challenge"].execution.details.expire_seconds = 10'
# ============================================================

update_email_config() {
  local jq_filter="$1"
  local updated response http_code
  local tmpfile
  tmpfile=$(mktemp)
  printf '%s\n' "${EMAIL_AUTH_CONFIG_JSON}" | jq "${jq_filter}" > "${tmpfile}"
  updated=$(cat "${tmpfile}")

  response=$(curl -s -w "\n%{http_code}" -X PUT \
    "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authentication-configurations/${EMAIL_AUTH_CONFIG_ID}" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d @"${tmpfile}")

  rm -f "${tmpfile}"

  http_code=$(echo "${response}" | tail -1)
  local body
  body=$(echo "${response}" | sed '$d')

  if [ "${http_code}" != "200" ]; then
    echo "Error: update_email_config failed (HTTP ${http_code})" >&2
    echo "${body}" >&2
    return 1
  fi

  EMAIL_AUTH_CONFIG_JSON="${updated}"
  echo "${body}"
}

restore_email_config() {
  local response http_code
  local tmpfile
  tmpfile=$(mktemp)
  printf '%s\n' "${EMAIL_AUTH_CONFIG_JSON_INITIAL}" > "${tmpfile}"

  response=$(curl -s -w "\n%{http_code}" -X PUT \
    "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authentication-configurations/${EMAIL_AUTH_CONFIG_ID}" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d @"${tmpfile}")

  rm -f "${tmpfile}"

  http_code=$(echo "${response}" | tail -1)

  if [ "${http_code}" != "200" ]; then
    echo "Error: restore_email_config failed (HTTP ${http_code})" >&2
    echo "${response}" | sed '$d' >&2
    return 1
  fi

  EMAIL_AUTH_CONFIG_JSON="${EMAIL_AUTH_CONFIG_JSON_INITIAL}"
  echo "Email config restored."
}

get_email_config() {
  curl -s "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authentication-configurations/${EMAIL_AUTH_CONFIG_ID}" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}"
}

# ============================================================
# 認証ポリシー更新ヘルパー
#
# 使用例:
#   update_auth_policy '.policies[0].step_definitions[0].order = 2'
# ============================================================

update_auth_policy() {
  local jq_filter="$1"
  local updated response http_code
  local tmpfile
  tmpfile=$(mktemp)
  printf '%s\n' "${AUTH_POLICY_JSON}" | jq "${jq_filter}" > "${tmpfile}"
  updated=$(cat "${tmpfile}")

  response=$(curl -s -w "\n%{http_code}" -X PUT \
    "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authentication-policies/${AUTH_POLICY_ID}" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d @"${tmpfile}")

  rm -f "${tmpfile}"

  http_code=$(echo "${response}" | tail -1)
  local body
  body=$(echo "${response}" | sed '$d')

  if [ "${http_code}" != "200" ]; then
    echo "Error: update_auth_policy failed (HTTP ${http_code})" >&2
    echo "${body}" >&2
    return 1
  fi

  AUTH_POLICY_JSON="${updated}"
  echo "${body}"
}

update_auth_policy_json() {
  local json="$1"
  local response http_code
  local tmpfile
  tmpfile=$(mktemp)
  printf '%s\n' "${json}" > "${tmpfile}"

  response=$(curl -s -w "\n%{http_code}" -X PUT \
    "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authentication-policies/${AUTH_POLICY_ID}" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d @"${tmpfile}")

  rm -f "${tmpfile}"

  http_code=$(echo "${response}" | tail -1)
  local body
  body=$(echo "${response}" | sed '$d')

  if [ "${http_code}" != "200" ]; then
    echo "Error: update_auth_policy_json failed (HTTP ${http_code})" >&2
    echo "${body}" >&2
    return 1
  fi

  AUTH_POLICY_JSON="${json}"
  echo "${body}"
}

restore_auth_policy() {
  local response http_code
  local tmpfile
  tmpfile=$(mktemp)
  printf '%s\n' "${AUTH_POLICY_JSON_INITIAL}" > "${tmpfile}"

  response=$(curl -s -w "\n%{http_code}" -X PUT \
    "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authentication-policies/${AUTH_POLICY_ID}" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d @"${tmpfile}")

  rm -f "${tmpfile}"

  http_code=$(echo "${response}" | tail -1)

  if [ "${http_code}" != "200" ]; then
    echo "Error: restore_auth_policy failed (HTTP ${http_code})" >&2
    echo "${response}" | sed '$d' >&2
    return 1
  fi

  AUTH_POLICY_JSON="${AUTH_POLICY_JSON_INITIAL}"
  echo "Authentication policy restored."
}

get_auth_policy() {
  curl -s "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authentication-policies/${AUTH_POLICY_ID}" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}"
}

# ============================================================
# 認可フロー関数
# ============================================================

# 認可リクエスト開始（新しい COOKIE_JAR で毎回クリーンに）
# 使用例:
#   start_auth_flow                          # デフォルトスコープ
#   start_auth_flow "openid+profile+email"   # スコープ指定
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

# ユーザー登録
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

  if [ "${http_code}" = "200" ]; then
    echo "← ${http_code} User registered: ${email}" >&2
  else
    echo "← ${http_code} Registration failed" >&2
    echo "${body}" >&2
  fi
  echo "${body}"
}

# Email OTP チャレンジ送信
# 使用例:
#   email_challenge "${TEST_EMAIL}"
email_challenge() {
  local email="$1"

  local response http_code body
  response=$(curl -s -w "\n%{http_code}" -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
    -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/email-authentication-challenge" \
    -H "Content-Type: application/json" \
    -d "{\"email\": \"${email}\", \"template\": \"authentication\"}")

  http_code=$(echo "${response}" | tail -1)
  body=$(echo "${response}" | sed '$d')

  if [ "${http_code}" = "200" ]; then
    echo "← ${http_code} Challenge sent to ${email}" >&2
  else
    echo "← ${http_code} Challenge failed" >&2
    echo "${body}" >&2
  fi
  echo "${body}"
}

# Management API で検証コードを取得（no-action モード用）
get_verification_code() {
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

# Email OTP 検証
# 使用例:
#   email_verify "${VERIFICATION_CODE}"
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
    echo "← ${http_code} Email verified successfully" >&2
  elif [ "${http_code}" = "400" ]; then
    echo "← ${http_code} Verification failed (code expired or invalid)" >&2
  else
    echo "← ${http_code} Email verification error" >&2
  fi
  echo "${body}"
}

# パスワード認証
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
  ID_TOKEN=$(echo "${TOKEN_RESPONSE}" | jq -r '.id_token')
  REFRESH_TOKEN=$(echo "${TOKEN_RESPONSE}" | jq -r '.refresh_token')
  echo "${TOKEN_RESPONSE}" | jq '{token_type, expires_in, scope}'
}

# MFA ログイン一括実行
# 使用例:
#   mfa_login "${TEST_EMAIL}" "${TEST_PASSWORD}"
mfa_login() {
  local email="$1"
  local password="$2"

  start_auth_flow
  email_challenge "${email}"
  get_verification_code
  email_verify
  password_login "${email}" "${password}"
  complete_auth_flow
}

# ============================================================
# ユーティリティ
# ============================================================

# JWT ペイロードのデコード
decode_jwt_payload() {
  local token="$1"
  local payload
  payload=$(echo "${token}" | cut -d. -f2 | tr '_-' '/+')
  local mod=$((${#payload} % 4))
  if [ $mod -eq 2 ]; then payload="${payload}=="; elif [ $mod -eq 3 ]; then payload="${payload}="; fi
  echo "${payload}" | base64 -d 2>/dev/null
}

# ID Token から amr（Authentication Methods References）を表示
# 使用例:
#   show_amr                 # 現在の ID_TOKEN から
#   show_amr "$MY_TOKEN"    # 指定トークンから
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

# ViewData 取得（認可リクエストの表示用データ: 適用ポリシー、認証状態等）
get_view_data() {
  curl -s -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
    "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/view-data"
}

# UserInfo 取得
get_userinfo() {
  local token="${1:-${ACCESS_TOKEN}}"
  curl -s -H "Authorization: Bearer ${token}" \
    "${TENANT_BASE}/v1/userinfo"
}

# Discovery エンドポイント確認
get_discovery() {
  curl -s "${TENANT_BASE}/.well-known/openid-configuration"
}

# prompt=none で認可リクエスト（セッション有効性チェック）
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

# Refresh Token でトークン更新
refresh_tokens() {
  local refresh_token="${1:-${REFRESH_TOKEN}}"

  local response
  response=$(curl -s \
    -X POST "${TENANT_BASE}/v1/tokens" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    --data-urlencode "grant_type=refresh_token" \
    --data-urlencode "refresh_token=${refresh_token}" \
    --data-urlencode "client_id=${CLIENT_ID}" \
    --data-urlencode "client_secret=${CLIENT_SECRET}")

  ACCESS_TOKEN=$(echo "${response}" | jq -r '.access_token')
  ID_TOKEN=$(echo "${response}" | jq -r '.id_token')
  REFRESH_TOKEN=$(echo "${response}" | jq -r '.refresh_token // empty')
  echo "${response}" | jq .
}

# 全設定を初期状態に復元
restore_all() {
  echo "Restoring all settings..."
  restore_tenant
  restore_auth_server
  restore_client
  restore_registration_config
  restore_email_config
  restore_auth_policy
  echo "All settings restored."
}

# ============================================================
# クイックスタート
#
# 以下を順番に実行すると、MFA フロー全体を試せます:
#
#   source helpers.sh
#   get_admin_token
#
#   # ユーザー登録 + トークン取得（Phase 1）
#   start_auth_flow
#   register_user
#   complete_auth_flow
#
#   # MFA ログイン（Phase 2）
#   mfa_login "${TEST_EMAIL}" "${TEST_PASSWORD}"
#
#   # 結果確認
#   show_amr
#   get_userinfo | jq .
#
# ============================================================
