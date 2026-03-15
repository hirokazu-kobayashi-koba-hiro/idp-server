# 動作確認ガイド - Enterprise (Security Events)

setup.sh で構築した環境が正しく動作するかを、1ステップずつ手動で確認するためのガイドです。
セキュリティイベントの永続化、フック連携（Webhook / SSF）、フック実行結果、テナント統計を検証します。

> **自動テスト**: `./verify.sh` を実行すると、以下の手順をすべて自動で検証できます。

## 前提条件

- `setup.sh` が正常に完了していること
- `curl`, `jq`, `python3` がインストール済みであること
- `config/generated/{organization-name}/` に生成された設定ファイルが存在すること
- mock-server が起動していること

> **注意**: `jwks.json` および `security-event-hook-ssf.json` に含まれる秘密鍵はテスト専用です。本番環境では必ず別の鍵を生成して使用してください。

## 事前準備

### Mock Server 起動

```bash
node config/templates/use-cases/enterprise/mock-server.js
# → http://localhost:4005 で待ち受け
```

### 変数設定

```bash
ORGANIZATION_NAME="${ORGANIZATION_NAME:-enterprise}"
cd config/templates/use-cases/enterprise

source helpers.sh
get_admin_token
```

---

# Phase 1: 基本フロー確認

まず基本的な認証フローが動作することを確認します。

## Step 1: Discovery Endpoint

### リクエスト

```bash
curl -s "${TENANT_BASE}/.well-known/openid-configuration" | jq .
```

### 確認ポイント

- HTTP 200 が返ること
- `issuer` が `${TENANT_BASE}` と一致すること

---

## Step 2: ユーザー登録

### リクエスト

```bash
TEST_EMAIL="verify-$(date +%s)@example.com"
TEST_PASSWORD="VerifyPass123"

start_auth_flow
register_user "${TEST_EMAIL}" "${TEST_PASSWORD}" "Enterprise Test User"
complete_auth_flow
```

### 確認ポイント

- ユーザー登録が成功すること
- トークンが取得できること

---

# Phase 2: Webhook フック

## Step 3: Webhook フック登録

### リクエスト

```bash
WEBHOOK_HOOK_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')

curl -s -w "\n%{http_code}" -X POST "${HOOK_API}" \
  -H "${AUTH_HEADER}" \
  -H "Content-Type: application/json" \
  -d "{
    \"id\": \"${WEBHOOK_HOOK_ID}\",
    \"type\": \"WEBHOOK\",
    \"triggers\": [\"password_success\", \"password_failure\", \"login_success\"],
    \"events\": {
      \"default\": {
        \"execution\": {
          \"function\": \"http_request\",
          \"http_request\": {
            \"url\": \"http://host.docker.internal:4005/webhook/security-events\",
            \"method\": \"POST\",
            \"auth_type\": \"none\",
            \"body_mapping_rules\": [
              { \"from\": \"$.type\", \"to\": \"event_type\" },
              { \"from\": \"$.user.sub\", \"to\": \"user_id\" }
            ]
          }
        }
      }
    },
    \"execution_order\": 100,
    \"enabled\": true,
    \"store_execution_payload\": true
  }"
```

### 確認ポイント

- HTTP 201 が返ること

---

## Step 4: フック一覧確認

### リクエスト

```bash
curl -s "${HOOK_API}" -H "${AUTH_HEADER}" | jq '.list[] | {id, type, triggers, enabled}'
```

### 確認ポイント

- 登録したフックが一覧に含まれていること
- `type` が `WEBHOOK` であること
- `triggers` に指定したイベントタイプが含まれていること

---

## Step 5: フック詳細取得

### リクエスト

```bash
curl -s "${HOOK_API}/${WEBHOOK_HOOK_ID}" -H "${AUTH_HEADER}" | jq .
```

### 確認ポイント

- 登録した設定が正しく返ること
- `events.default.execution.function` が `"http_request"` であること

---

## Step 6: 認証フローでイベント発火

### リクエスト

```bash
# パスワード認証成功（password_success + login_success イベント）
start_auth_flow
password_login "${TEST_EMAIL}" "${TEST_PASSWORD}"
complete_auth_flow

# パスワード認証失敗（password_failure イベント）
start_auth_flow
password_login "${TEST_EMAIL}" "WrongPassword999"
```

### 確認ポイント

- パスワード認証が成功/失敗すること

---

## Step 7: Webhook イベント確認

### リクエスト

```bash
sleep 3
curl -s http://localhost:4005/webhook/security-events | jq .
```

### 確認ポイント

- `total` が 0 より大きいこと
- `events[].event_type` に `password_success`, `login_success`, `password_failure` が含まれていること
- `events[].user_id` にユーザーの sub が含まれていること

### レスポンス例

```json
{
  "events": [
    {
      "received_at": "2025-01-01T00:00:00.000Z",
      "event_type": "password_success",
      "user_id": "user-uuid"
    },
    {
      "received_at": "2025-01-01T00:00:01.000Z",
      "event_type": "login_success",
      "user_id": "user-uuid"
    },
    {
      "received_at": "2025-01-01T00:00:02.000Z",
      "event_type": "password_failure",
      "user_id": "user-uuid"
    }
  ],
  "total": 3
}
```

---

## Step 8: Webhook フック削除

### リクエスト

```bash
curl -s -w "\n%{http_code}" -X DELETE "${HOOK_API}/${WEBHOOK_HOOK_ID}" -H "${AUTH_HEADER}"
```

### 確認ポイント

- HTTP 204 が返ること

---

# Phase 3: SSF フック

## Step 9: SSF フック登録

### リクエスト

```bash
SSF_HOOK_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')

register_ssf_hook "${SSF_HOOK_ID}"
```

または手動で JSON を送信：

```bash
SSF_BODY=$(cat security-event-hook-ssf.json \
  | jq --arg id "${SSF_HOOK_ID}" \
       --arg url "http://host.docker.internal:4005/ssf/events" \
       --arg base_url "${AUTHORIZATION_SERVER_URL}" \
       --arg tenant_id "${PUBLIC_TENANT_ID}" \
       '.id = $id
        | (.events[].execution.details.url) = $url
        | .metadata.issuer = ($base_url + "/" + $tenant_id)
        | .metadata.jwks_uri = ($base_url + "/" + $tenant_id + "/v1/ssf/jwks")')

curl -s -w "\n%{http_code}" -X POST "${HOOK_API}" \
  -H "${AUTH_HEADER}" \
  -H "Content-Type: application/json" \
  -d "${SSF_BODY}"
```

### 確認ポイント

- HTTP 201 が返ること

---

## Step 10: SSF イベント発火 + 確認

### リクエスト

```bash
# 認証フローを実行（password_success が SSF トリガーに含まれている場合）
start_auth_flow
password_login "${TEST_EMAIL}" "${TEST_PASSWORD}"
complete_auth_flow

sleep 3
curl -s http://localhost:4005/ssf/events | jq .
```

### 確認ポイント

- `total` が 0 より大きいこと
- `events[].content_type` が `application/secevent+jwt` であること
- mock-server のコンソールに JWT ペイロードの `iss` と `events` が表示されること

---

## Step 11: SSF フック削除

### リクエスト

```bash
curl -s -w "\n%{http_code}" -X DELETE "${HOOK_API}/${SSF_HOOK_ID}" -H "${AUTH_HEADER}"
```

### 確認ポイント

- HTTP 204 が返ること

---

# Phase 4: セキュリティイベント永続化

認証フローで発生したセキュリティイベントが DB に永続化されているかを Management API で確認します。

## Step 12: セキュリティイベント一覧取得

### リクエスト

```bash
EVENT_API="${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/security-events"

curl -s "${EVENT_API}?limit=10" -H "${AUTH_HEADER}" | jq .
```

### 確認ポイント

- HTTP 200 が返ること
- `total_count` が 0 より大きいこと
- `list[]` に認証フローで発生したイベントが含まれていること

---

## Step 13: イベントタイプでフィルタ

### リクエスト

```bash
# password_success イベントのみ取得
curl -s "${EVENT_API}?event_type=password_success&limit=5" -H "${AUTH_HEADER}" | jq .

# password_failure イベントのみ取得
curl -s "${EVENT_API}?event_type=password_failure&limit=5" -H "${AUTH_HEADER}" | jq .
```

### 確認ポイント

- フィルタが正しく動作すること
- 各イベントに `id`, `type`, `created_at` が含まれていること

---

## Step 14: セキュリティイベント詳細取得

### リクエスト

```bash
# 一覧から取得した ID を使用
EVENT_ID=$(curl -s "${EVENT_API}?limit=1" -H "${AUTH_HEADER}" | jq -r '.list[0].id')
curl -s "${EVENT_API}/${EVENT_ID}" -H "${AUTH_HEADER}" | jq .
```

### 確認ポイント

- 個別のイベント詳細が返ること
- ユーザー情報、クライアント情報が含まれていること

---

# Phase 5: フック実行結果

フックが実行された結果（成功/失敗）を Management API で確認します。

## Step 15: フック実行結果一覧

### リクエスト

```bash
HOOK_RESULT_API="${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/security-event-hooks"

curl -s "${HOOK_RESULT_API}?limit=10" -H "${AUTH_HEADER}" | jq .
```

### 確認ポイント

- HTTP 200 が返ること
- `list[]` にフック実行結果が含まれていること
- `status` が `SUCCESS` であること（Mock Server が正常応答している場合）

---

## Step 16: フック実行結果のフィルタ

### リクエスト

```bash
# Webhook の実行結果のみ
curl -s "${HOOK_RESULT_API}?hook_type=WEBHOOK&limit=5" -H "${AUTH_HEADER}" | jq .

# SSF の実行結果のみ
curl -s "${HOOK_RESULT_API}?hook_type=SSF&limit=5" -H "${AUTH_HEADER}" | jq .

# 失敗したフックのみ
curl -s "${HOOK_RESULT_API}?status=FAILURE&limit=5" -H "${AUTH_HEADER}" | jq .
```

### 確認ポイント

- フィルタが正しく動作すること
- 各結果に `security_event.type`, `type`（フックタイプ）, `status`, `created_at` が含まれていること

---

# Phase 6: テナント統計

セキュリティイベントがテナント統計に反映されているかを確認します。

## Step 17: テナント統計取得

### リクエスト

```bash
STATS_API="${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/statistics"
CURRENT_MONTH=$(date +%Y-%m)

curl -s "${STATS_API}?from=${CURRENT_MONTH}&to=${CURRENT_MONTH}" -H "${AUTH_HEADER}" | jq .
```

### 確認ポイント

- HTTP 200 が返ること
- `list[]` に当月の統計データが含まれていること
- イベント数が認証フローの実行回数と整合すること

---

## Step 18: 年次レポート取得

### リクエスト

```bash
CURRENT_YEAR=$(date +%Y)

curl -s "${STATS_API}/yearly/${CURRENT_YEAR}" -H "${AUTH_HEADER}" | jq .
```

### 確認ポイント

- HTTP 200 が返ること
- `summary` にイベント集計が含まれていること
- `monthly[]` に月別データが含まれていること

---

## チェックリスト

### Phase 1: 基本フロー

| Step | 確認項目 | 結果 |
|------|---------|------|
| 1 | Discovery endpoint が HTTP 200 を返す | |
| 2 | ユーザー登録が成功する | |
| 2 | トークンが取得できる | |

### Phase 2: Webhook フック

| Step | 確認項目 | 結果 |
|------|---------|------|
| 3 | Webhook フック登録が HTTP 201 を返す | |
| 4 | フック一覧に登録したフックが含まれる | |
| 5 | フック詳細が正しく返る | |
| 6 | パスワード認証が成功/失敗する | |
| 7 | Mock Server に Webhook イベントが到達する | |
| 7 | event_type, user_id が正しい | |
| 8 | フック削除が HTTP 204 を返す | |

### Phase 3: SSF フック

| Step | 確認項目 | 結果 |
|------|---------|------|
| 9 | SSF フック登録が HTTP 201 を返す | |
| 10 | Mock Server に SSF イベントが到達する | |
| 10 | content_type が application/secevent+jwt | |
| 11 | フック削除が HTTP 204 を返す | |

### Phase 4: セキュリティイベント永続化

| Step | 確認項目 | 結果 |
|------|---------|------|
| 12 | セキュリティイベント一覧が取得できる | |
| 12 | total_count が 0 より大きい | |
| 13 | event_type フィルタが動作する | |
| 14 | イベント詳細が取得できる | |

### Phase 5: フック実行結果

| Step | 確認項目 | 結果 |
|------|---------|------|
| 15 | フック実行結果一覧が取得できる | |
| 15 | status が SUCCESS | |
| 16 | hook_type フィルタが動作する | |

### Phase 6: テナント統計

| Step | 確認項目 | 結果 |
|------|---------|------|
| 17 | 当月の統計データが取得できる | |
| 18 | 年次レポートが取得できる | |
