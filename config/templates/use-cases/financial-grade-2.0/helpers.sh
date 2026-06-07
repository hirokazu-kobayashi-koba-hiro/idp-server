#!/bin/bash
#
# Financial-Grade (FAPI Advanced + CIBA) - Experiment Helpers
#
# 管理ダッシュボードやコントロールプレーンAPIの動作確認で使う共通関数と変数を定義する。
#
# 使い方:
#   source helpers.sh
#   source helpers.sh --org financial-grade
#
# 関数一覧:
#
#   === トークン ===
#   get_admin_token              管理者トークン取得（Organizerテナント）
#
#   === コントロールプレーン（組織レベルAPI）===
#   list_tenants                 テナント一覧取得
#   get_tenant                   テナント詳細取得
#   list_users                   ユーザー一覧取得
#   list_clients                 クライアント一覧取得
#   list_security_events         セキュリティイベント一覧取得
#   get_tenant_statistics        テナント統計情報取得
#   list_auth_configs            認証設定一覧取得
#   list_auth_policies           認証ポリシー一覧取得
#
#   === テナント・認可サーバー更新 ===
#   update_tenant                テナント設定更新（jqフィルタ）
#   update_auth_server           認可サーバー設定更新（jqフィルタ）
#
#   === ユーティリティ ===
#   decode_jwt_payload           JWTペイロードデコード
#   get_discovery                Discoveryエンドポイント確認

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../../.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"

# Parse arguments
ORGANIZATION_NAME="financial-grade"
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
PUBLIC_TENANT_ID=$(jq -r '.tenant.id' "${CONFIG_DIR}/financial-tenant.json")
TLS_CLIENT_ID=$(jq -r '.client_id' "${CONFIG_DIR}/tls-client-auth-client.json")
PKJ_CLIENT_ID=$(jq -r '.client_id' "${CONFIG_DIR}/private-key-jwt-client.json")

ORGANIZER_BASE="${AUTHORIZATION_SERVER_URL}/${ORGANIZER_TENANT_ID}"
TENANT_BASE="${AUTHORIZATION_SERVER_URL}/${PUBLIC_TENANT_ID}"
ORG_BASE_URL="${AUTHORIZATION_SERVER_URL}/v1/management/organizations/${ORG_ID}/tenants"

# 生成済み設定をベースとして読み込む（PUT APIはフル置換のため）
TENANT_JSON_INITIAL=$(jq '.tenant' "${CONFIG_DIR}/financial-tenant.json")
TENANT_JSON="${TENANT_JSON_INITIAL}"
AUTH_SERVER_JSON_INITIAL=$(jq '.authorization_server' "${CONFIG_DIR}/financial-tenant.json")
AUTH_SERVER_JSON="${AUTH_SERVER_JSON_INITIAL}"

echo "=========================================="
echo "Financial-Grade Helpers Loaded"
echo "=========================================="
echo "  Server:            ${AUTHORIZATION_SERVER_URL}"
echo "  Organization:      ${ORGANIZATION_NAME} (${ORG_ID})"
echo "  Organizer Tenant:  ${ORGANIZER_TENANT_ID}"
echo "  Financial Tenant:  ${PUBLIC_TENANT_ID}"
echo "  Admin Email:       ${ADMIN_EMAIL}"
echo "  TLS Client:        ${TLS_CLIENT_ID}"
echo "  PKJ Client:        ${PKJ_CLIENT_ID}"
echo ""

# ============================================================
# 管理トークン取得
# ============================================================

get_admin_token() {
  local response
  response=$(curl -s -X POST \
    "${ORGANIZER_BASE}/v1/tokens" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    --data-urlencode "grant_type=password" \
    --data-urlencode "username=${ADMIN_EMAIL}" \
    --data-urlencode "password=${ADMIN_PASSWORD}" \
    --data-urlencode "client_id=${ORG_CLIENT_ID}" \
    --data-urlencode "client_secret=${ORG_CLIENT_SECRET}" \
    --data-urlencode "scope=openid profile email management")

  ORG_ACCESS_TOKEN=$(echo "${response}" | jq -r '.access_token')

  if [ -z "${ORG_ACCESS_TOKEN}" ] || [ "${ORG_ACCESS_TOKEN}" = "null" ]; then
    echo "Error: Failed to get admin token"
    echo "${response}" | jq '.' 2>/dev/null || echo "${response}"
    return 1
  fi

  echo "Admin token: ${ORG_ACCESS_TOKEN:0:20}..."
}

# ============================================================
# コントロールプレーン（組織レベルAPI）
# ============================================================

# テナント一覧
list_tenants() {
  curl -s "${ORG_BASE_URL}" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" | jq '.'
}

# テナント詳細（デフォルト: Financial Tenant）
# 使用例:
#   get_tenant                           # Financial Tenant
#   get_tenant "${ORGANIZER_TENANT_ID}"  # Organizer Tenant
get_tenant() {
  local tenant_id="${1:-${PUBLIC_TENANT_ID}}"
  curl -s "${ORG_BASE_URL}/${tenant_id}" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" | jq '.'
}

# ユーザー一覧（デフォルト: Financial Tenant）
# 使用例:
#   list_users                           # Financial Tenant
#   list_users "${ORGANIZER_TENANT_ID}"  # Organizer Tenant
#   list_users "" "limit=5&offset=0"     # ページネーション
list_users() {
  local tenant_id="${1:-${PUBLIC_TENANT_ID}}"
  local query="${2:-}"
  local url="${ORG_BASE_URL}/${tenant_id}/users"
  [ -n "${query}" ] && url="${url}?${query}"

  curl -s "${url}" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" | jq '.'
}

# クライアント一覧（デフォルト: Financial Tenant）
list_clients() {
  local tenant_id="${1:-${PUBLIC_TENANT_ID}}"
  curl -s "${ORG_BASE_URL}/${tenant_id}/clients" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" | jq '.'
}

# セキュリティイベント一覧（デフォルト: Financial Tenant）
# 使用例:
#   list_security_events                          # デフォルト
#   list_security_events "${PUBLIC_TENANT_ID}" 20 # 件数指定
list_security_events() {
  local tenant_id="${1:-${PUBLIC_TENANT_ID}}"
  local limit="${2:-10}"

  curl -s "${ORG_BASE_URL}/${tenant_id}/security-events?limit=${limit}" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" | jq '.'
}

# テナント統計情報取得（デフォルト: Financial Tenant、当月）
# 使用例:
#   get_tenant_statistics                                    # デフォルト（当月）
#   get_tenant_statistics "" "2026-01" "2026-04"             # 期間指定（YYYY-MM）
#   get_tenant_statistics "${ORGANIZER_TENANT_ID}"           # Organizerテナント
get_tenant_statistics() {
  local tenant_id="${1:-${PUBLIC_TENANT_ID}}"
  local from="${2:-$(date +%Y-%m)}"
  local to="${3:-$(date +%Y-%m)}"

  curl -s "${ORG_BASE_URL}/${tenant_id}/statistics?from=${from}&to=${to}" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" | jq '.'
}

# 認証設定一覧（デフォルト: Financial Tenant）
list_auth_configs() {
  local tenant_id="${1:-${PUBLIC_TENANT_ID}}"

  curl -s "${ORG_BASE_URL}/${tenant_id}/authentication-configurations" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" | jq '.'
}

# 認証ポリシー一覧（デフォルト: Financial Tenant）
list_auth_policies() {
  local tenant_id="${1:-${PUBLIC_TENANT_ID}}"

  curl -s "${ORG_BASE_URL}/${tenant_id}/authentication-policies" \
    -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" | jq '.'
}

# ============================================================
# テナント・認可サーバー更新ヘルパー
#
# 重要: PUT API はフル置換。$TENANT_JSON をベースに jq で変更して使う。
#
# 使用例:
#   update_tenant '.session_config.timeout_seconds = 15'
#   update_auth_server '.extension.access_token_duration = 60'
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

# Discovery エンドポイント確認
get_discovery() {
  local tenant_id="${1:-${PUBLIC_TENANT_ID}}"
  curl -s "${AUTHORIZATION_SERVER_URL}/${tenant_id}/.well-known/openid-configuration" | jq '.'
}

# ============================================================
# クイックスタート
#
# 以下を順番に実行すると、管理機能を試せます:
#
#   source helpers.sh
#   get_admin_token
#
#   # コントロールプレーン確認
#   list_tenants
#   get_tenant
#   list_users
#   list_clients
#   list_security_events
#   get_tenant_statistics
#   list_auth_configs
#   list_auth_policies
#
#   # Organizerテナントの情報
#   get_tenant "${ORGANIZER_TENANT_ID}"
#   list_users "${ORGANIZER_TENANT_ID}"
#
#   # Discovery
#   get_discovery
#   get_discovery "${ORGANIZER_TENANT_ID}"
#
# ============================================================
