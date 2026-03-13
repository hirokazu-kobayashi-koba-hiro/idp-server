#!/bin/bash
#
# eKYC (Identity Verification) - Experiment Helpers
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
#   === 身元確認設定 ===
#   update_iv_config             身元確認設定更新（jqフィルタ）
#   update_iv_config_json        身元確認設定をJSON全体で更新
#   restore_iv_config            身元確認設定を初期状態に復元
#   get_iv_config                身元確認設定取得
#
#   === 認可フロー ===
#   start_auth_flow              認可リクエスト開始
#   register_user                ユーザー登録（initial-registration）
#   password_login               パスワード認証
#   complete_auth_flow           認可→コード取得→トークン交換
#
#   === 身元確認フロー ===
#   iv_apply                     身元確認申請
#   iv_process                   プロセス実行（任意のプロセス名）
#   iv_evaluate                  審査結果判定（approved/rejected）
#   iv_list_applications         申請一覧取得
#   iv_get_results               身元確認結果取得
#
#   === ユーティリティ ===
#   get_userinfo                 UserInfo取得
#   decode_jwt_payload           JWTペイロードデコード
#   try_prompt_none              prompt=noneでセッション確認
#   restore_all                  全設定を初期状態に復元

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../../.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"

# Parse arguments
ORGANIZATION_NAME="ekyc"
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
MOCK_BASE_URL="http://host.docker.internal:4002"

# 生成済み設定をベースとして読み込む（更新APIはフル置換のため）
# *_INITIAL は restore 用の初期値、*_JSON は update が追従する現在値
TENANT_JSON_INITIAL=$(jq '.tenant' "${CONFIG_DIR}/public-tenant.json")
TENANT_JSON="${TENANT_JSON_INITIAL}"
AUTH_SERVER_JSON_INITIAL=$(jq '.authorization_server' "${CONFIG_DIR}/public-tenant.json")
AUTH_SERVER_JSON="${AUTH_SERVER_JSON_INITIAL}"
AUTH_POLICY_ID=$(jq -r '.id' "${CONFIG_DIR}/authentication-policy.json")
AUTH_CONFIG_ID=$(jq -r '.id' "${CONFIG_DIR}/authentication-config-initial-registration.json")
IV_CONFIG_ID=$(jq -r '.id' "${CONFIG_DIR}/identity-verification-config.json")
IV_CONFIG_JSON_INITIAL=$(cat "${CONFIG_DIR}/identity-verification-config.json")
IV_CONFIG_JSON="${IV_CONFIG_JSON_INITIAL}"
CLIENT_JSON_INITIAL=$(cat "${CONFIG_DIR}/public-client.json")
CLIENT_JSON="${CLIENT_JSON_INITIAL}"

echo "=========================================="
echo "eKYC Experiment Helpers Loaded"
echo "=========================================="
echo "  Server:       ${AUTHORIZATION_SERVER_URL}"
echo "  Organization: ${ORGANIZATION_NAME}"
echo "  Tenant ID:    ${PUBLIC_TENANT_ID}"
echo "  Client ID:    ${CLIENT_ID}"
echo "  Redirect URI: ${REDIRECT_URI}"
echo "  IV Config ID: ${IV_CONFIG_ID}"
echo "  Mock URL:     ${MOCK_BASE_URL}"
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

  TENANT_JSON="${updated}"
  echo "${body}"
}

restore_tenant() {
  local response http_code
  response=$(curl -s -w "\n%{http_code}" -X PUT "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${TENANT_JSON_INITIAL}")

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
#   update_auth_server '.extension.required_identity_verification_scopes = ["transfers", "account"]'
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

  AUTH_SERVER_JSON="${updated}"
  echo "${body}"
}

restore_auth_server() {
  local response http_code
  response=$(curl -s -w "\n%{http_code}" -X PUT \
    "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authorization-server" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${AUTH_SERVER_JSON_INITIAL}")

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
#   update_client '.scope = "openid profile email transfers identity_verification_application"'
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

  CLIENT_JSON="${updated}"
  echo "${body}"
}

restore_client() {
  local response http_code
  response=$(curl -s -w "\n%{http_code}" -X PUT \
    "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/clients/${CLIENT_ID}" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${CLIENT_JSON_INITIAL}")

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
# 身元確認設定更新ヘルパー
#
# 使用例:
#   # jqフィルタで部分変更
#   update_iv_config '.processes.apply.execution.type = "http_request"'
#
#   # JSON全体で更新
#   update_iv_config_json "$(cat my-config.json)"
# ============================================================

update_iv_config() {
  local jq_filter="$1"
  local updated response http_code
  updated=$(echo "${IV_CONFIG_JSON}" | jq "${jq_filter}")

  response=$(curl -s -w "\n%{http_code}" -X PUT \
    "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/identity-verification-configurations/${IV_CONFIG_ID}" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${updated}")

  http_code=$(echo "${response}" | tail -1)
  local body
  body=$(echo "${response}" | sed '$d')

  if [ "${http_code}" != "200" ]; then
    echo "Error: update_iv_config failed (HTTP ${http_code})" >&2
    echo "${body}" >&2
    return 1
  fi

  # 成功時は IV_CONFIG_JSON を更新後の状態に追従させる
  # （後続の update_iv_config が前回の変更を引き継ぐため）
  IV_CONFIG_JSON="${updated}"

  echo "${body}"
}

update_iv_config_json() {
  local json="$1"
  local response http_code

  response=$(curl -s -w "\n%{http_code}" -X PUT \
    "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/identity-verification-configurations/${IV_CONFIG_ID}" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${json}")

  http_code=$(echo "${response}" | tail -1)
  local body
  body=$(echo "${response}" | sed '$d')

  if [ "${http_code}" != "200" ]; then
    echo "Error: update_iv_config_json failed (HTTP ${http_code})" >&2
    echo "${body}" >&2
    return 1
  fi

  # 成功時は IV_CONFIG_JSON を更新後の状態に追従させる
  IV_CONFIG_JSON="${json}"

  echo "${body}"
}

restore_iv_config() {
  local response http_code
  response=$(curl -s -w "\n%{http_code}" -X PUT \
    "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/identity-verification-configurations/${IV_CONFIG_ID}" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    -d "${IV_CONFIG_JSON_INITIAL}")

  http_code=$(echo "${response}" | tail -1)

  if [ "${http_code}" != "200" ]; then
    echo "Error: restore_iv_config failed (HTTP ${http_code})" >&2
    echo "${response}" | sed '$d' >&2
    return 1
  fi

  IV_CONFIG_JSON="${IV_CONFIG_JSON_INITIAL}"
  echo "Identity verification config restored."
}

get_iv_config() {
  curl -s "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/identity-verification-configurations/${IV_CONFIG_ID}" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}"
}

# ============================================================
# 認可フロー関数
# ============================================================

# 認可リクエスト開始（新しい COOKIE_JAR で毎回クリーンに）
# 使用例:
#   start_auth_flow                                      # デフォルトスコープ
#   start_auth_flow "openid+profile+email+transfers"     # transfers スコープ付き
#   start_auth_flow "openid+profile+email+transfers+identity_verification_application"
start_auth_flow() {
  local scope="${1:-openid+profile+email+identity_verification_application}"
  [ -n "${COOKIE_JAR:-}" ] && [ -f "${COOKIE_JAR}" ] && rm -f "${COOKIE_JAR}"
  COOKIE_JAR=$(mktemp)
  STATE="exp-state-$(date +%s)"

  AUTH_REDIRECT=$(curl -s -c "${COOKIE_JAR}" -o /dev/null \
    -w "%{redirect_url}" \
    "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=$(python3 -c "import urllib.parse; print(urllib.parse.quote('${REDIRECT_URI}', safe=''))")&scope=${scope}&state=${STATE}")

  AUTHORIZATION_ID=$(echo "${AUTH_REDIRECT}" | sed -n 's/.*[?&]id=\([^&#]*\).*/\1/p')
  echo "Authorization ID: ${AUTHORIZATION_ID}"
}

# claims パラメータ付き認可リクエスト
# 使用例:
#   start_auth_flow_with_claims "openid+profile+email+transfers" '{"id_token":{"verified_claims":{"verification":{"trust_framework":"eidas"},"claims":{"given_name":null,"family_name":null}}}}'
start_auth_flow_with_claims() {
  local scope="$1"
  local claims_json="$2"
  [ -n "${COOKIE_JAR:-}" ] && [ -f "${COOKIE_JAR}" ] && rm -f "${COOKIE_JAR}"
  COOKIE_JAR=$(mktemp)
  STATE="exp-state-$(date +%s)"

  local claims_encoded
  claims_encoded=$(python3 -c "import urllib.parse; print(urllib.parse.quote('${claims_json}'))")

  AUTH_REDIRECT=$(curl -s -c "${COOKIE_JAR}" -o /dev/null \
    -w "%{redirect_url}" \
    "${TENANT_BASE}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=$(python3 -c "import urllib.parse; print(urllib.parse.quote('${REDIRECT_URI}', safe=''))")&scope=${scope}&state=${STATE}&claims=${claims_encoded}")

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

  echo "← ${http_code} POST /initial-registration" >&2
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

  echo "← ${http_code} POST /password-authentication" >&2
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

# ============================================================
# 身元確認フロー関数
# ============================================================

IV_TYPE="authentication-assurance"

# 身元確認申請
# 使用例:
#   iv_apply                                 # デフォルト値で申請
#   iv_apply "Suzuki" "Hanako" "1985-05-20"  # 名前・生年月日を指定
#   iv_apply "error"                         # モックのエラーシミュレーション
#   iv_apply "retry"                         # モックのリトライシミュレーション
iv_apply() {
  local last_name="${1:-Tanaka}"
  local first_name="${2:-Taro}"
  local birthdate="${3:-1990-01-15}"
  local email="${4:-test@example.com}"

  local response http_code body
  response=$(curl -s -w "\n%{http_code}" \
    -X POST "${TENANT_BASE}/v1/me/identity-verification/applications/${IV_TYPE}/apply" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -d "{
      \"last_name\": \"${last_name}\",
      \"first_name\": \"${first_name}\",
      \"birthdate\": \"${birthdate}\",
      \"email_address\": \"${email}\"
    }")

  http_code=$(echo "${response}" | tail -1)
  body=$(echo "${response}" | sed '$d')

  echo "← ${http_code} POST /applications/${IV_TYPE}/apply" >&2

  APPLICATION_ID=$(echo "${body}" | jq -r '.id // empty')
  if [ -n "${APPLICATION_ID}" ]; then
    echo "  Application ID: ${APPLICATION_ID}" >&2
  fi

  echo "${body}"
}

# 任意のプロセスを実行
# 使用例:
#   iv_process "request-ekyc"                           # ボディなし
#   iv_process "callback-result" '{"result":"approved"}' # ボディ付き
iv_process() {
  local process_name="$1"
  local body_json="${2:-{}}"

  local response http_code body
  response=$(curl -s -w "\n%{http_code}" \
    -X POST "${TENANT_BASE}/v1/me/identity-verification/applications/${IV_TYPE}/${APPLICATION_ID}/${process_name}" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -d "${body_json}")

  http_code=$(echo "${response}" | tail -1)
  body=$(echo "${response}" | sed '$d')

  echo "← ${http_code} POST /applications/${IV_TYPE}/${APPLICATION_ID}/${process_name}" >&2
  echo "${body}"
}

# 審査結果判定（デフォルト設定の evaluate-result 用）
# 使用例:
#   iv_evaluate approved     # 承認
#   iv_evaluate rejected     # 拒否
iv_evaluate() {
  local result="${1:-approved}"

  if [ "${result}" = "approved" ]; then
    iv_process "evaluate-result" '{"approved": true, "rejected": false}'
  elif [ "${result}" = "rejected" ]; then
    iv_process "evaluate-result" '{"approved": false, "rejected": true}'
  else
    iv_process "evaluate-result" "{\"verification_result\": \"${result}\"}"
  fi
}

# 申請一覧取得
# 使用例:
#   iv_list_applications                     # 全件
#   iv_list_applications "status=approved"   # ステータス絞り込み
iv_list_applications() {
  local query="${1:-}"
  local url="${TENANT_BASE}/v1/me/identity-verification/applications"
  [ -n "${query}" ] && url="${url}?${query}"

  curl -s -H "Authorization: Bearer ${ACCESS_TOKEN}" "${url}"
}

# 身元確認結果取得
# 使用例:
#   iv_get_results                                   # 全件
#   iv_get_results "type=${IV_TYPE}"                  # タイプ指定
iv_get_results() {
  local query="${1:-}"
  local url="${TENANT_BASE}/v1/me/identity-verification/results"
  [ -n "${query}" ] && url="${url}?${query}"

  curl -s -H "Authorization: Bearer ${ACCESS_TOKEN}" "${url}"
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

# ID Token から verified_claims を抽出
# 使用例:
#   show_verified_claims             # 現在の ID_TOKEN から
#   show_verified_claims "$MY_TOKEN" # 指定トークンから
show_verified_claims() {
  local token="${1:-${ID_TOKEN}}"
  decode_jwt_payload "${token}" | python3 -c "
import sys, json
data = json.load(sys.stdin)
vc = data.get('verified_claims')
if vc:
    print(json.dumps(vc, indent=2, ensure_ascii=False))
else:
    print('verified_claims not found in ID Token')
"
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

# 全設定を初期状態に復元
restore_all() {
  echo "Restoring all settings..."
  restore_tenant
  restore_auth_server
  restore_client
  restore_iv_config
  echo "All settings restored."
}

# ============================================================
# クイックスタート
#
# 以下を順番に実行すると、身元確認フロー全体を試せます:
#
#   source helpers.sh
#   get_admin_token
#
#   # ユーザー登録 + トークン取得
#   start_auth_flow
#   register_user
#   complete_auth_flow
#
#   # 身元確認申請 → 承認
#   iv_apply
#   iv_evaluate approved
#
#   # 結果確認
#   iv_list_applications | jq .
#   iv_get_results | jq .
#
#   # verified_claims 付きで再認可
#   start_auth_flow_with_claims "openid+profile+email+transfers" \
#     '{"id_token":{"verified_claims":{"verification":{"trust_framework":"eidas"},"claims":{"given_name":null,"family_name":null,"birthdate":null}}}}'
#   password_login "${TEST_EMAIL}" "${TEST_PASSWORD}"
#   complete_auth_flow
#   show_verified_claims
#
# ============================================================
