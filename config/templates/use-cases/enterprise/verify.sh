#!/bin/bash
set -e

# Enterprise (Security Event Hooks) - Verification Script
#
# セキュリティイベントフック（Webhook / SSF）の動作確認を行う。
#
# 前提:
#   1. setup.sh 済み
#   2. source helpers.sh && get_admin_token 済み
#   3. mock-server 起動済み: node config/templates/use-cases/enterprise/mock-server.js
#
# 使い方:
#   source helpers.sh
#   get_admin_token
#   bash verify.sh

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MOCK_API_URL="${MOCK_API_URL:-http://host.docker.internal:4005}"
MOCK_LOCAL_URL="${MOCK_LOCAL_URL:-http://localhost:4005}"

# --- 必須変数チェック ---
: "${ORG_ACCESS_TOKEN:?ORG_ACCESS_TOKEN is required. Run get_admin_token first.}"
: "${ORG_BASE_URL:?ORG_BASE_URL is required. Source helpers.sh first.}"
: "${PUBLIC_TENANT_ID:?PUBLIC_TENANT_ID is required. Source helpers.sh first.}"
: "${HOOK_API:?HOOK_API is required. Source helpers.sh first.}"

AUTH_HEADER="Authorization: Bearer ${ORG_ACCESS_TOKEN}"
TEST_EMAIL="${TEST_EMAIL:-test-enterprise@example.com}"
TEST_PASSWORD="${TEST_PASSWORD:-ChangeMe123}"

echo "=========================================="
echo "Enterprise - Security Event Hook 動作確認"
echo "=========================================="
echo "  Tenant:       ${PUBLIC_TENANT_ID}"
echo "  Mock Server:  ${MOCK_LOCAL_URL}"
echo ""

# --- 0. Mock Server 疎通確認 ---
echo "--- 0. Mock Server 疎通確認 ---"
MOCK_CHECK=$(curl -s -o /dev/null -w "%{http_code}" "${MOCK_LOCAL_URL}/webhook/security-events" 2>/dev/null || echo "000")
if [ "${MOCK_CHECK}" != "200" ]; then
  echo "  Error: Mock server is not running at ${MOCK_LOCAL_URL}"
  echo "  Start it with: node ${SCRIPT_DIR}/mock-server.js"
  exit 1
fi
echo "  Mock server is running"
# Clear previous events
curl -s -X DELETE "${MOCK_LOCAL_URL}/webhook/security-events" > /dev/null 2>&1
curl -s -X DELETE "${MOCK_LOCAL_URL}/ssf/events" > /dev/null 2>&1
echo "  Previous events cleared"
echo ""

# --- 1. Webhook フック登録 ---
echo "--- 1. Webhook フック登録 ---"
WEBHOOK_HOOK_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')

WEBHOOK_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${HOOK_API}" \
  -H "${AUTH_HEADER}" \
  -H "Content-Type: application/json" \
  -d "{
    \"id\": \"${WEBHOOK_HOOK_ID}\",
    \"type\": \"WEBHOOK\",
    \"triggers\": [\"password_success\", \"password_failure\", \"login_success\", \"user_signup\"],
    \"events\": {
      \"default\": {
        \"execution\": {
          \"function\": \"http_request\",
          \"http_request\": {
            \"url\": \"${MOCK_API_URL}/webhook/security-events\",
            \"method\": \"POST\",
            \"auth_type\": \"none\",
            \"body_mapping_rules\": [
              { \"from\": \"$.type\", \"to\": \"event_type\" },
              { \"from\": \"$.user.sub\", \"to\": \"user_id\" },
              { \"from\": \"$.user.preferred_username\", \"to\": \"username\" }
            ]
          }
        }
      }
    },
    \"execution_order\": 100,
    \"enabled\": true,
    \"store_execution_payload\": true
  }")

HTTP_CODE=$(echo "${WEBHOOK_RESPONSE}" | tail -1)
if [ "${HTTP_CODE}" = "201" ]; then
  echo "  Webhook hook registered: ${WEBHOOK_HOOK_ID}"
else
  echo "  Failed (HTTP ${HTTP_CODE})"
  echo "${WEBHOOK_RESPONSE}" | sed '$d'
  exit 1
fi
echo ""

# --- 2. SSF フック登録 ---
echo "--- 2. SSF フック登録 ---"
SSF_HOOK_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')

SSF_BODY=$(cat "${SCRIPT_DIR}/security-event-hook-ssf.json" \
  | jq --arg id "${SSF_HOOK_ID}" \
       --arg url "${MOCK_API_URL}/ssf/events" \
       --arg base_url "${AUTHORIZATION_SERVER_URL}" \
       --arg tenant_id "${PUBLIC_TENANT_ID}" \
       '.id = $id
        | (.events[].execution.details.url) = $url
        | .metadata.issuer = ($base_url + "/" + $tenant_id)
        | .metadata.jwks_uri = ($base_url + "/" + $tenant_id + "/v1/ssf/jwks")')

SSF_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${HOOK_API}" \
  -H "${AUTH_HEADER}" \
  -H "Content-Type: application/json" \
  -d "${SSF_BODY}")

HTTP_CODE=$(echo "${SSF_RESPONSE}" | tail -1)
if [ "${HTTP_CODE}" = "201" ]; then
  echo "  SSF hook registered: ${SSF_HOOK_ID}"
else
  echo "  Failed (HTTP ${HTTP_CODE})"
  echo "${SSF_RESPONSE}" | sed '$d'
  echo "  (SSF hook registration failure is non-fatal, continuing...)"
fi
echo ""

# --- 3. フック一覧確認 ---
echo "--- 3. フック一覧確認 ---"
HOOKS_LIST=$(curl -s "${HOOK_API}" -H "${AUTH_HEADER}")
HOOKS_COUNT=$(echo "${HOOKS_LIST}" | jq '.list | length')
echo "  Total hooks: ${HOOKS_COUNT}"
echo "${HOOKS_LIST}" | jq '.list[] | {id: .id, type: .type, triggers: .triggers, enabled: .enabled}'
echo ""

# --- 4. 認証フローでイベント発火 ---
echo "--- 4. 認証フローでイベント発火 ---"

# 4a. ユーザー登録 + 認可完了（user_initial_registration_success イベント発火）
echo "  4a. ユーザー登録..."
start_auth_flow
REGISTER_RESULT=$(register_user "${TEST_EMAIL}" "${TEST_PASSWORD}" "Enterprise Test User")
REGISTER_STATUS=$(echo "${REGISTER_RESULT}" | jq -r '.status // .error // "unknown"' 2>/dev/null)
echo "  Registration: ${REGISTER_STATUS}"
echo "  4a-2. 認可完了（ユーザー永続化）..."
complete_auth_flow

# 4b. パスワード認証 + 認可完了（password_success, login_success イベント発火）
echo "  4b. パスワード認証..."
start_auth_flow
LOGIN_RESULT=$(password_login "${TEST_EMAIL}" "${TEST_PASSWORD}")
LOGIN_STATUS=$(echo "${LOGIN_RESULT}" | jq -r '.status // .error // "unknown"' 2>/dev/null)
echo "  Login: ${LOGIN_STATUS}"
echo "  4c. 認可完了..."
complete_auth_flow

# 4d. パスワード認証失敗（password_failure イベント発火）
echo "  4d. パスワード認証失敗..."
start_auth_flow
FAIL_RESULT=$(password_login "${TEST_EMAIL}" "WrongPassword999")
FAIL_STATUS=$(echo "${FAIL_RESULT}" | jq -r '.status // .error // "unknown"' 2>/dev/null)
echo "  Login failure: ${FAIL_STATUS}"
echo ""

# --- 5. イベント確認 ---
echo "--- 5. イベント確認（3秒待機後）---"
sleep 3

echo "  Webhook events:"
WEBHOOK_EVENTS=$(curl -s "${MOCK_LOCAL_URL}/webhook/security-events")
WEBHOOK_EVENT_COUNT=$(echo "${WEBHOOK_EVENTS}" | jq '.total')
echo "  Total: ${WEBHOOK_EVENT_COUNT}"
echo "${WEBHOOK_EVENTS}" | jq '.events[] | {event_type, user_id, received_at}'
echo ""

echo "  SSF events:"
SSF_EVENTS=$(curl -s "${MOCK_LOCAL_URL}/ssf/events")
SSF_EVENT_COUNT=$(echo "${SSF_EVENTS}" | jq '.total')
echo "  Total: ${SSF_EVENT_COUNT}"
if [ "${SSF_EVENT_COUNT}" -gt 0 ] 2>/dev/null; then
  echo "${SSF_EVENTS}" | jq '.events[] | {content_type, received_at}'
fi
echo ""

# --- 6. セキュリティイベント永続化確認 ---
echo "--- 6. セキュリティイベント永続化確認 ---"
EVENT_API="${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/security-events"

EVENTS_RESPONSE=$(curl -s "${EVENT_API}?limit=10" -H "${AUTH_HEADER}")
EVENTS_TOTAL=$(echo "${EVENTS_RESPONSE}" | jq '.total_count // 0')
echo "  永続化イベント数: ${EVENTS_TOTAL}"

if [ "${EVENTS_TOTAL}" -gt 0 ] 2>/dev/null; then
  echo "  イベントタイプ別:"
  echo "${EVENTS_RESPONSE}" | jq -r '.list[].type' | sort | uniq -c | sed 's/^/    /'

  # イベントタイプフィルタ確認
  FILTERED=$(curl -s "${EVENT_API}?event_type=password_success&limit=1" -H "${AUTH_HEADER}")
  FILTERED_COUNT=$(echo "${FILTERED}" | jq '.total_count // 0')
  echo "  password_success フィルタ: ${FILTERED_COUNT} 件"
fi
echo ""

# --- 7. フック実行結果確認 ---
echo "--- 7. フック実行結果確認 ---"
HOOK_RESULT_API="${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/security-event-hooks"

HOOK_RESULTS=$(curl -s "${HOOK_RESULT_API}?limit=10" -H "${AUTH_HEADER}")
HOOK_RESULTS_TOTAL=$(echo "${HOOK_RESULTS}" | jq '.list | length')
echo "  フック実行結果数: ${HOOK_RESULTS_TOTAL}"

if [ "${HOOK_RESULTS_TOTAL}" -gt 0 ] 2>/dev/null; then
  echo "  実行結果:"
  echo "${HOOK_RESULTS}" | jq '.list[] | {event_type: .security_event.type, hook_type: .type, status, created_at}'

  # ステータス別集計
  echo "  ステータス別:"
  echo "${HOOK_RESULTS}" | jq -r '.list[].status' | sort | uniq -c | sed 's/^/    /'

  # タイプ別集計
  echo "  フックタイプ別:"
  echo "${HOOK_RESULTS}" | jq -r '.list[] | "\(.type) \(.status)"' | sort | uniq -c | sed 's/^/    /'
fi
echo ""

# --- 8. テナント統計確認 ---
echo "--- 8. テナント統計確認 ---"
STATS_API="${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/statistics"
CURRENT_MONTH=$(date +%Y-%m)
CURRENT_YEAR=$(date +%Y)

STATS_RESPONSE=$(curl -s "${STATS_API}?from=${CURRENT_MONTH}&to=${CURRENT_MONTH}" -H "${AUTH_HEADER}")
STATS_COUNT=$(echo "${STATS_RESPONSE}" | jq '.list | length')
echo "  当月統計データ: ${STATS_COUNT} 件"

if [ "${STATS_COUNT}" -gt 0 ] 2>/dev/null; then
  echo "${STATS_RESPONSE}" | jq '.list[0].monthly_summary // .list[0] | keys'
fi

YEARLY_RESPONSE=$(curl -s "${STATS_API}/yearly/${CURRENT_YEAR}" -H "${AUTH_HEADER}")
YEARLY_STATUS=$(echo "${YEARLY_RESPONSE}" | jq -r '.period.year // "N/A"')
echo "  年次レポート: ${YEARLY_STATUS}"
echo ""

# --- 9. フッククリーンアップ ---
echo "--- 9. フッククリーンアップ ---"

curl -s -o /dev/null -w "  Webhook hook delete: HTTP %{http_code}\n" \
  -X DELETE "${HOOK_API}/${WEBHOOK_HOOK_ID}" -H "${AUTH_HEADER}"

if [ "${SSF_HOOK_ID:-}" ]; then
  curl -s -o /dev/null -w "  SSF hook delete: HTTP %{http_code}\n" \
    -X DELETE "${HOOK_API}/${SSF_HOOK_ID}" -H "${AUTH_HEADER}"
fi
echo ""

# --- 結果サマリ ---
echo "=========================================="
echo "検証結果サマリ"
echo "=========================================="
echo ""
echo "  [フック連携]"
echo "  Webhook フック登録:    OK"
echo "  SSF フック登録:        $([ "${SSF_EVENT_COUNT}" -gt 0 ] 2>/dev/null && echo "OK" || echo "要確認")"
echo "  Webhook イベント受信:  ${WEBHOOK_EVENT_COUNT} 件"
echo "  SSF イベント受信:      ${SSF_EVENT_COUNT} 件"
echo ""
echo "  [イベント永続化]"
echo "  DB 永続化イベント数:   ${EVENTS_TOTAL} 件"
echo ""
echo "  [フック実行結果]"
echo "  フック実行結果数:      ${HOOK_RESULTS_TOTAL} 件"
echo ""
echo "  [テナント統計]"
echo "  当月統計:              ${STATS_COUNT} 件"
echo "  年次レポート:          ${YEARLY_STATUS}"

if [ "${WEBHOOK_EVENT_COUNT}" -gt 0 ] 2>/dev/null; then
  echo ""
  echo "  Webhook に記録されたイベントタイプ:"
  echo "${WEBHOOK_EVENTS}" | jq -r '.events[].event_type' | sort | uniq -c | sed 's/^/    /'
fi
echo ""
