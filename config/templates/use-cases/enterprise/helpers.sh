#!/bin/bash
#
# Enterprise (Security Event Hooks) - Helpers
#
# 使い方:
#   source helpers.sh
#   source helpers.sh --org my-organization
#

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../../.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"

# Parse arguments
ORGANIZATION_NAME="enterprise"
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

# Mock server URLs
MOCK_API_URL="${MOCK_API_URL:-http://host.docker.internal:4005}"
MOCK_LOCAL_URL="${MOCK_LOCAL_URL:-http://localhost:4005}"

# Hook API base
HOOK_API="${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/security-event-hook-configurations"

echo "=========================================="
echo "Enterprise Helpers Loaded"
echo "=========================================="
echo "  Server:       ${AUTHORIZATION_SERVER_URL}"
echo "  Organization: ${ORGANIZATION_NAME}"
echo "  Tenant ID:    ${PUBLIC_TENANT_ID}"
echo "  Client ID:    ${CLIENT_ID}"
echo "  Mock Server:  ${MOCK_LOCAL_URL}"
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

  AUTH_HEADER="Authorization: Bearer ${ORG_ACCESS_TOKEN}"
  echo "Admin token: ${ORG_ACCESS_TOKEN:0:20}..."
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

register_user() {
  local email="$1"
  local password="$2"
  local name="${3:-Test User}"

  curl -s -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
    -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/initial-registration" \
    -H "Content-Type: application/json" \
    -d "{\"email\": \"${email}\", \"password\": \"${password}\", \"name\": \"${name}\"}"
}

password_login() {
  local username="$1"
  local password="$2"

  curl -s -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
    -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/password-authentication" \
    -H "Content-Type: application/json" \
    -d "{\"username\": \"${username}\", \"password\": \"${password}\"}"
}

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
  echo "${TOKEN_RESPONSE}" | jq '{token_type, expires_in}'
}

# ============================================================
# Security Event Hook 管理関数
# ============================================================

register_webhook_hook() {
  local hook_id="${1:-$(uuidgen | tr '[:upper:]' '[:lower:]')}"
  local triggers="${2:-password_success,password_failure,login_success}"
  local webhook_url="${3:-${MOCK_API_URL}/webhook/security-events}"

  # triggers をJSON配列に変換
  local triggers_json
  triggers_json=$(echo "${triggers}" | jq -R 'split(",")')

  local response http_code body
  response=$(curl -s -w "\n%{http_code}" -X POST "${HOOK_API}" \
    -H "${AUTH_HEADER}" \
    -H "Content-Type: application/json" \
    -d "$(jq -n \
      --arg id "${hook_id}" \
      --argjson triggers "${triggers_json}" \
      --arg url "${webhook_url}" \
      '{
        id: $id,
        type: "WEBHOOK",
        triggers: $triggers,
        events: {
          default: {
            execution: {
              function: "http_request",
              http_request: {
                url: $url,
                method: "POST",
                auth_type: "none",
                body_mapping_rules: [
                  { from: "$.type", to: "event_type" },
                  { from: "$.user.sub", to: "user_id" },
                  { from: "$.user.preferred_username", to: "username" }
                ]
              }
            }
          }
        },
        execution_order: 100,
        enabled: true,
        store_execution_payload: true
      }')")

  http_code=$(echo "${response}" | tail -1)
  body=$(echo "${response}" | sed '$d')

  if [ "${http_code}" = "201" ]; then
    echo "Webhook hook registered: ${hook_id}"
    LAST_HOOK_ID="${hook_id}"
  else
    echo "Failed (HTTP ${http_code})"
    echo "${body}" | jq '.' 2>/dev/null || echo "${body}"
    return 1
  fi
}

register_ssf_hook() {
  local hook_id="${1:-$(uuidgen | tr '[:upper:]' '[:lower:]')}"
  local template_file="${2:-${SCRIPT_DIR}/security-event-hook-ssf.json}"
  local ssf_url="${3:-${MOCK_API_URL}/ssf/events}"

  local body
  body=$(cat "${template_file}" \
    | jq --arg id "${hook_id}" \
         --arg url "${ssf_url}" \
         --arg base_url "${AUTHORIZATION_SERVER_URL}" \
         --arg tenant_id "${PUBLIC_TENANT_ID}" \
         '.id = $id
          | (.events[].execution.details.url) = $url
          | .metadata.issuer = ($base_url + "/" + $tenant_id)
          | .metadata.jwks_uri = ($base_url + "/" + $tenant_id + "/v1/ssf/jwks")')

  local response http_code resp_body
  response=$(curl -s -w "\n%{http_code}" -X POST "${HOOK_API}" \
    -H "${AUTH_HEADER}" \
    -H "Content-Type: application/json" \
    -d "${body}")

  http_code=$(echo "${response}" | tail -1)
  resp_body=$(echo "${response}" | sed '$d')

  if [ "${http_code}" = "201" ]; then
    echo "SSF hook registered: ${hook_id}"
    LAST_HOOK_ID="${hook_id}"
  else
    echo "Failed (HTTP ${http_code})"
    echo "${resp_body}" | jq '.' 2>/dev/null || echo "${resp_body}"
    return 1
  fi
}

list_hook_configs() {
  curl -s "${HOOK_API}" -H "${AUTH_HEADER}" | jq '.'
}

get_hook_config() {
  local hook_id="${1:?hook_id is required}"
  curl -s "${HOOK_API}/${hook_id}" -H "${AUTH_HEADER}" | jq '.'
}

delete_hook_config() {
  local hook_id="${1:?hook_id is required}"
  local response http_code
  response=$(curl -s -w "\n%{http_code}" -X DELETE "${HOOK_API}/${hook_id}" \
    -H "${AUTH_HEADER}")
  http_code=$(echo "${response}" | tail -1)

  if [ "${http_code}" = "204" ]; then
    echo "Hook deleted: ${hook_id}"
  else
    echo "Failed (HTTP ${http_code})"
    return 1
  fi
}

# ============================================================
# セキュリティイベント照会（Management API）
# ============================================================

list_security_events() {
  local query="${1:-limit=10}"
  curl -s "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/security-events?${query}" -H "${AUTH_HEADER}" | jq '.'
}

get_security_event() {
  local event_id="${1:?event_id is required}"
  curl -s "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/security-events/${event_id}" -H "${AUTH_HEADER}" | jq '.'
}

# ============================================================
# フック実行結果照会（Management API）
# ============================================================

list_hook_results() {
  local query="${1:-limit=10}"
  curl -s "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/security-event-hooks?${query}" -H "${AUTH_HEADER}" | jq '.'
}

# ============================================================
# テナント統計（Management API）
# ============================================================

get_statistics() {
  local from="${1:-$(date +%Y-%m)}"
  local to="${2:-${from}}"
  curl -s "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/statistics?from=${from}&to=${to}" -H "${AUTH_HEADER}" | jq '.'
}

get_yearly_report() {
  local year="${1:-$(date +%Y)}"
  curl -s "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/statistics/yearly/${year}" -H "${AUTH_HEADER}" | jq '.'
}

# ============================================================
# Mock Server イベント確認
# ============================================================

get_webhook_events() {
  curl -s "${MOCK_LOCAL_URL}/webhook/security-events" | jq '.'
}

get_ssf_events() {
  curl -s "${MOCK_LOCAL_URL}/ssf/events" | jq '.'
}
