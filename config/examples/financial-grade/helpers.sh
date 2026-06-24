#!/bin/bash
#
# Financial-Grade FAPI Example - Authorization Code Flow Helpers (PKCE)
#
# 認可コードフロー（PKCE 付き）の動作確認で使う共通関数と変数を定義する。
# クライアント認証は self_signed_tls_client_auth（x-ssl-cert ヘッダ）を使用する。
#
# require_pkce / FAPI のため、認可リクエストには code_challenge、トークン交換には
# code_verifier が必要になる。PKCE 生成・トランザクションID／認可コードの受け渡しを
# 関数に閉じ込めているので、関数を順番に呼ぶだけで各フローを実行できる。
#
# 使い方:
#   source helpers.sh
#
#   # 例: 既存ユーザーのパスワードログイン
#   fapi_authorize "openid profile email account"
#   fapi_password user@financial-institution.example.com SecureFinancialPass123!
#   fapi_consent
#   fapi_token
#   fapi_userinfo
#
# 関数一覧:
#   fapi_authorize <scope>                          PKCE 生成 + 署名付き JAR で認可リクエスト開始（AUTH_TX_ID / AUTHZ_URL 出力）
#   fapi_register [email] [pw] [name] [phone]       新規ユーザー登録（initial-registration）
#   fapi_password [username] [pw]                   パスワード認証
#   fapi_fido2 <assertion-json>                     FIDO2/WebAuthn 認証（assertion は認証器から取得）
#   fapi_consent                                    認可許諾（AUTH_CODE 取得）
#   fapi_token                                      認可コード -> トークン交換（code_verifier 付き）
#   fapi_refresh                                    リフレッシュトークンでアクセストークン更新
#   fapi_userinfo                                   UserInfo 取得（証明書バインドトークン）

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"

# --- .env から AUTHORIZATION_SERVER_URL を読み込む ---
if [ -f "${ENV_FILE}" ]; then
  set -a
  source "${ENV_FILE}"
  set +a
fi
: "${AUTHORIZATION_SERVER_URL:=https://api.local.test}"

# --- Example の設定ファイルから ID を読み込む（ハードコード回避）---
CLIENT_FILE="${SCRIPT_DIR}/financial-client.json"
TENANT_FILE="${SCRIPT_DIR}/financial-tenant.json"
CERT_FILE="${CERT_FILE:-${SCRIPT_DIR}/certs/client-cert.pem}"

TENANT_ID=$(jq -r '.tenant.id' "${TENANT_FILE}")
CLIENT_ID=$(jq -r '.client_id' "${CLIENT_FILE}")
REDIRECT_URI=$(jq -r '.redirect_uris[0]' "${CLIENT_FILE}")
TENANT_BASE="${AUTHORIZATION_SERVER_URL}/${TENANT_ID}"

echo "=========================================="
echo "Financial-Grade FAPI Flow Helpers Loaded"
echo "=========================================="
echo "  Server:       ${AUTHORIZATION_SERVER_URL}"
echo "  Tenant:       ${TENANT_ID}"
echo "  Client:       ${CLIENT_ID}"
echo "  Redirect URI: ${REDIRECT_URI}"
echo "  Cert (x-ssl): ${CERT_FILE}"
echo ""

# ============================================================
# 内部ヘルパー
# ============================================================

# クライアント証明書を x-ssl-cert ヘッダ用にエンコード（self_signed_tls_client_auth）
_fapi_encoded_cert() {
  if [ ! -f "${CERT_FILE}" ]; then
    echo "Error: client cert not found: ${CERT_FILE}" >&2
    echo "  生成: ./config/scripts/generate-client-certificate.sh -c financial-web-app -o ${SCRIPT_DIR}/certs" >&2
    return 1
  fi
  awk '{printf "%s%%0A", $0}' "${CERT_FILE}" | sed 's/%0A$//'
}

# URL からクエリパラメータを取り出す（$1=URL, $2=param名）
_fapi_query_param() {
  python3 -c "from urllib.parse import urlparse, parse_qs; import sys; print(parse_qs(urlparse(sys.argv[1]).query).get(sys.argv[2], [''])[0])" "$1" "$2"
}

# JWT（JARM レスポンス等）のペイロードからクレームを取り出す（$1=JWT, $2=クレーム名）
_fapi_jwt_claim() {
  python3 -c "
import sys, base64, json
p = sys.argv[1].split('.')[1]
p += '=' * (-len(p) % 4)
print(json.loads(base64.urlsafe_b64decode(p)).get(sys.argv[2], ''))
" "$1" "$2"
}

# ============================================================
# 認可コードフロー
# ============================================================

# PKCE 生成 + 認可リクエスト開始
# 使用例: fapi_authorize "openid profile email account"
fapi_authorize() {
  local scope="${1:?usage: fapi_authorize <scope>}"

  CODE_VERIFIER=$(openssl rand -hex 32)
  CODE_CHALLENGE=$(printf '%s' "${CODE_VERIFIER}" | openssl dgst -sha256 -binary | openssl base64 -e | tr '+/' '-_' | tr -d '=')
  STATE="fapi-$(date +%s)"
  NONCE=$(openssl rand -hex 16)

  # require_signed_request_object:true（FAPI Advance の transfers/write は特に必須）のため、
  # 認可リクエストは署名付き JWT リクエストオブジェクト（JAR, RFC 9101）で送る。PAR は必須で
  # ないので request= で値渡し。署名は financial-client.json の EC 秘密鍵（ES256）。
  local request_jwt
  request_jwt=$(
    FAPI_CLIENT_FILE="${CLIENT_FILE}" \
    FAPI_CLIENT_ID="${CLIENT_ID}" \
    FAPI_AUD="${TENANT_BASE}" \
    FAPI_REDIRECT_URI="${REDIRECT_URI}" \
    FAPI_SCOPE="${scope}" \
    FAPI_STATE="${STATE}" \
    FAPI_NONCE="${NONCE}" \
    FAPI_CODE_CHALLENGE="${CODE_CHALLENGE}" \
    FAPI_RESPONSE_TYPE="code" \
    node "${SCRIPT_DIR}/sign-request-object.js"
  ) || {
    echo "Error: リクエストオブジェクトの署名に失敗（node / financial-client.json の鍵を確認）" >&2
    return 1
  }

  # GET /v1/authorizations は 302 を返し、Location の id がトランザクションID。
  # JAR 値渡しなので、外側クエリは client_id / response_type / request のみ（他は JWT 内）。
  #   url_effective = 組み立てた認可リクエストURL（ブラウザで開けば画面操作で続行可能）
  #   redirect_url  = 302 Location（ログイン画面URL。id を含む）
  local out
  out=$(curl -s -k -o /dev/null -w '%{url_effective}\t%{redirect_url}' -G \
    "${TENANT_BASE}/v1/authorizations" \
    --data-urlencode "client_id=${CLIENT_ID}" \
    --data-urlencode "response_type=code" \
    --data-urlencode "request=${request_jwt}")

  AUTHZ_URL="${out%%$'\t'*}"
  local location="${out#*$'\t'}"
  AUTH_TX_ID=$(_fapi_query_param "${location}" "id")

  # ブラウザ画面操作用URL（PKCE は CODE_VERIFIER と同一なので、画面でログイン/同意 ->
  # redirect_uri の code を AUTH_CODE に入れて fapi_token、まで繋がる）
  echo "AUTHZ_URL:      ${AUTHZ_URL}"
  echo "  ↑ ブラウザで開くとログイン/同意を画面操作で実行できます"
  [ -n "${location}" ] && echo "LOGIN_URL:      ${location}"

  if [ -z "${AUTH_TX_ID}" ]; then
    echo "Note: Location に id がありません。API（fapi_password 等）で続行するには 302+id が必要です" >&2
    echo "      （素の認可リクエストが require_signed_request_object で弾かれている可能性）。" >&2
  else
    echo "AUTH_TX_ID:     ${AUTH_TX_ID}"
  fi
  echo "CODE_VERIFIER:  ${CODE_VERIFIER}"
  echo "CODE_CHALLENGE: ${CODE_CHALLENGE}"
}

# 新規ユーザー登録（initial-registration）
fapi_register() {
  local email="${1:-user@financial-institution.example.com}"
  local password="${2:-SecureFinancialPass123!}"
  local name="${3:-金融ユーザー}"
  local phone="${4:-090-1234-5678}"
  : "${AUTH_TX_ID:?先に fapi_authorize を実行してください}"

  local enc
  enc=$(_fapi_encoded_cert) || return 1

  curl -s -k -X POST \
    "${TENANT_BASE}/v1/authorizations/${AUTH_TX_ID}/initial-registration" \
    -H "Content-Type: application/json" \
    -H "x-ssl-cert: ${enc}" \
    -d "{\"email\":\"${email}\",\"name\":\"${name}\",\"phone_number\":\"${phone}\",\"password\":\"${password}\"}" | jq .
}

# パスワード認証
fapi_password() {
  local username="${1:-user@financial-institution.example.com}"
  local password="${2:-SecureFinancialPass123!}"
  : "${AUTH_TX_ID:?先に fapi_authorize を実行してください}"

  local enc
  enc=$(_fapi_encoded_cert) || return 1

  curl -s -k -X POST \
    "${TENANT_BASE}/v1/authorizations/${AUTH_TX_ID}/password-authentication" \
    -H "Content-Type: application/json" \
    -H "x-ssl-cert: ${enc}" \
    -d "{\"username\":\"${username}\",\"password\":\"${password}\"}" | jq .
}

# FIDO2/WebAuthn 認証（assertion JSON はブラウザの WebAuthn API＝認証器から取得して渡す）
fapi_fido2() {
  local credential_json="${1:?usage: fapi_fido2 '<webauthn assertion json>'}"
  : "${AUTH_TX_ID:?先に fapi_authorize を実行してください}"

  local enc
  enc=$(_fapi_encoded_cert) || return 1

  curl -s -k -X POST \
    "${TENANT_BASE}/v1/authorizations/${AUTH_TX_ID}/fido2-authentication" \
    -H "Content-Type: application/json" \
    -H "x-ssl-cert: ${enc}" \
    -d "${credential_json}" | jq .
}

# 認可許諾（authorize）。レスポンスの redirect_uri から認可コードを取り出す
fapi_consent() {
  : "${AUTH_TX_ID:?先に fapi_authorize を実行してください}"

  local enc
  enc=$(_fapi_encoded_cert) || return 1

  local resp redirect
  resp=$(curl -s -k -X POST \
    "${TENANT_BASE}/v1/authorizations/${AUTH_TX_ID}/authorize" \
    -H "Content-Type: application/json" \
    -H "x-ssl-cert: ${enc}")
  echo "${resp}" | jq . 2>/dev/null || echo "${resp}"

  redirect=$(echo "${resp}" | jq -r '.redirect_uri // empty')
  if [ -z "${redirect}" ]; then
    echo "Error: authorize レスポンスに redirect_uri がありません" >&2
    return 1
  fi

  # JARM（response_mode=jwt）では code は response=<JWT> の中。無ければ素の code= を見る。
  local jarm
  jarm=$(_fapi_query_param "${redirect}" "response")
  if [ -n "${jarm}" ]; then
    AUTH_CODE=$(_fapi_jwt_claim "${jarm}" "code")
  else
    AUTH_CODE=$(_fapi_query_param "${redirect}" "code")
  fi
  if [ -z "${AUTH_CODE}" ]; then
    echo "Error: redirect_uri から code を取得できません: ${redirect}" >&2
    return 1
  fi
  echo "AUTH_CODE: ${AUTH_CODE}"
}

# 認可コード -> トークン交換（code_verifier 付き / MTLS）
fapi_token() {
  : "${AUTH_CODE:?先に fapi_consent を実行してください}"
  : "${CODE_VERIFIER:?先に fapi_authorize を実行してください（PKCE）}"

  local enc
  enc=$(_fapi_encoded_cert) || return 1

  local resp
  resp=$(curl -s -k -X POST "${TENANT_BASE}/v1/tokens" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -H "x-ssl-cert: ${enc}" \
    --data-urlencode "grant_type=authorization_code" \
    --data-urlencode "code=${AUTH_CODE}" \
    --data-urlencode "redirect_uri=${REDIRECT_URI}" \
    --data-urlencode "client_id=${CLIENT_ID}" \
    --data-urlencode "code_verifier=${CODE_VERIFIER}")
  echo "${resp}" | jq . 2>/dev/null || echo "${resp}"

  ACCESS_TOKEN=$(echo "${resp}" | jq -r '.access_token // empty')
  ID_TOKEN=$(echo "${resp}" | jq -r '.id_token // empty')
  REFRESH_TOKEN=$(echo "${resp}" | jq -r '.refresh_token // empty')
}

# リフレッシュトークンでアクセストークン更新（MTLS）
fapi_refresh() {
  : "${REFRESH_TOKEN:?先に fapi_token を実行してください（REFRESH_TOKEN が必要）}"

  local enc
  enc=$(_fapi_encoded_cert) || return 1

  local resp
  resp=$(curl -s -k -X POST "${TENANT_BASE}/v1/tokens" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -H "x-ssl-cert: ${enc}" \
    --data-urlencode "grant_type=refresh_token" \
    --data-urlencode "refresh_token=${REFRESH_TOKEN}" \
    --data-urlencode "client_id=${CLIENT_ID}")
  echo "${resp}" | jq . 2>/dev/null || echo "${resp}"

  ACCESS_TOKEN=$(echo "${resp}" | jq -r '.access_token // empty')
  REFRESH_TOKEN=$(echo "${resp}" | jq -r '.refresh_token // empty')
}

# UserInfo 取得（証明書バインドトークンのため x-ssl-cert も付与）
fapi_userinfo() {
  : "${ACCESS_TOKEN:?先に fapi_token を実行してください}"

  local enc
  enc=$(_fapi_encoded_cert) || return 1

  curl -s -k -X GET "${TENANT_BASE}/v1/userinfo" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -H "x-ssl-cert: ${enc}" | jq .
}

# ============================================================
# クイックスタート（各フローは関数を順番に呼ぶだけ）
#
#   source helpers.sh
#
#   # パターン1: 初回ユーザー登録
#   fapi_authorize "openid profile email account"
#   fapi_register
#   fapi_consent
#   fapi_token
#   fapi_userinfo
#
#   # パターン2: 既存ユーザーのパスワードログイン
#   fapi_authorize "openid profile email account"
#   fapi_password user@financial-institution.example.com SecureFinancialPass123!
#   fapi_consent
#   fapi_token
#
#   # パターン3: 送金（WebAuthn）。assertion は認証器から取得
#   fapi_authorize "openid transfers"
#   fapi_fido2 '<webauthn assertion json>'
#   fapi_consent
#   fapi_token
#
#   # パターン4: Refresh Token
#   fapi_refresh
#
# 注意: transfers / write（FAPI Advance スコープ）は require_signed_request_object の
#       対象。PAR + 署名付きリクエストオブジェクトが必要なため、素の認可リクエストでは
#       拒否される場合がある。その検証は use-cases/financial-grade/VERIFY.md を参照。
# ============================================================
