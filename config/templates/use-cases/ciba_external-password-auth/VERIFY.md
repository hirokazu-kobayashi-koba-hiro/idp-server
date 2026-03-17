# 動作確認ガイド - CIBA + 外部パスワード認証

setup.sh で構築した環境が正しく動作するかを、1ステップずつ手動で確認するためのガイドです。

> **自動テスト**: `source helpers.sh && get_admin_token && source verify.sh` を実行すると、Phase 1（外部パスワード認証 + デバイスマッピング）と Phase 2（CIBA フロー + binding_message 検証）の全手順を自動で検証できます。

## 前提条件

- `setup.sh` が正常に完了していること
- モックサーバーが起動していること（`node mock-server.js`）
- `curl`, `jq`, `python3` がインストール済みであること
- `config/generated/{organization-name}/` に生成された設定ファイルが存在すること

## 変数設定

```bash
cd config/templates/use-cases/ciba_external-password-auth
source helpers.sh
get_admin_token

echo "Server:       ${AUTHORIZATION_SERVER_URL}"
echo "Tenant ID:    ${PUBLIC_TENANT_ID}"
echo "Client ID:    ${CLIENT_ID}"
echo "Mock Server:  ${MOCK_LOCAL_URL}"
```

---

## Phase 1: 外部パスワード認証 + デバイスマッピング

### Step 1: Mock Server 接続確認

モックサーバーが稼働しているか確認します。

#### リクエスト

```bash
curl -s -X POST "${MOCK_LOCAL_URL}/auth/password" \
  -H "Content-Type: application/json" \
  -d '{"username":"test@example.com","password":"test"}' | jq .
```

#### 確認ポイント

- HTTP 200 が返ること
- `device.id` が UUID 形式であること
- `device.notification_channel`, `device.notification_token` が含まれていること

---

### Step 2: Discovery Endpoint

OpenID Connect の Discovery エンドポイントが正しく応答するか確認します。

#### リクエスト

```bash
curl -s "${TENANT_BASE}/.well-known/openid-configuration" | jq .
```

#### 確認ポイント

- HTTP 200 が返ること
- `issuer` が `${TENANT_BASE}` と一致すること
- `backchannel_authentication_endpoint` が含まれていること
- `grant_types_supported` に `urn:openid:params:grant-type:ciba` が含まれていること
- `backchannel_token_delivery_modes_supported` に `poll` が含まれていること

---

### Step 3: 外部パスワード認証（ユーザー作成 + デバイスマッピング）

外部パスワード認証を実行し、ユーザー作成とデバイスマッピングを同時に行います。

#### リクエスト

```bash
TEST_EMAIL="test-ciba-ext-$(date +%s)@example.com"
TEST_PASSWORD="CorrectPassword123"

start_auth_flow
password_login "${TEST_EMAIL}" "${TEST_PASSWORD}"
complete_auth_flow
```

#### 確認ポイント

- Authorization ID が取得できること
- パスワード認証が HTTP 200 を返すこと
- 認可コードが取得できること
- アクセストークンが取得できること

---

### Step 4: UserInfo で authentication_devices を確認

外部パスワード認証で作成されたユーザーに `authentication_devices` がマッピングされているか確認します。

#### リクエスト

```bash
USERINFO=$(get_userinfo)
echo "${USERINFO}"

USER_SUB=$(echo "${USERINFO}" | jq -r '.sub')
DEVICE_ID=$(echo "${USERINFO}" | jq -r '.authentication_devices[0].id')
DEVICE_COUNT=$(echo "${USERINFO}" | jq '.authentication_devices | length')
```

#### 確認ポイント

- HTTP 200 が返ること
- `authentication_devices` 配列が含まれ、1件以上のデバイスが登録されていること
- デバイスに `id`, `app_name`, `platform`, `notification_channel`, `notification_token` が含まれていること
- `id` が UUID 形式であること（ObjectCompositor 配列マッピングの検証）

---

### Step 5: 2回目のログイン（既存ユーザー解決）

同一メールアドレスで再度ログインし、既存ユーザーが正しく解決されることを確認します。

#### リクエスト

```bash
start_auth_flow
password_login "${TEST_EMAIL}" "${TEST_PASSWORD}"
complete_auth_flow

USERINFO2=$(get_userinfo)
USER_SUB2=$(echo "${USERINFO2}" | jq -r '.sub')
echo "1st sub: ${USER_SUB}, 2nd sub: ${USER_SUB2}"
```

#### 確認ポイント

- 2回目のログインでも同一の `sub` が返ること

---

## Phase 2: CIBA フロー（binding_message 検証）

### Step 6: Backchannel Authentication Request

CIBA バックチャンネル認証リクエストを送信します。`login_hint` に外部認証プロバイダー付きメールアドレスを指定し、`binding_message` を含めます。

#### リクエスト

```bash
EXTERNAL_PROVIDER_ID="${EXTERNAL_PROVIDER_ID:-external-auth}"
BINDING_MESSAGE="1234"

ciba_request "email:${TEST_EMAIL},idp:${EXTERNAL_PROVIDER_ID}" "${BINDING_MESSAGE}"
echo "Auth Request ID: ${AUTH_REQ_ID}"
```

#### 確認ポイント

- HTTP 200 が返ること
- `auth_req_id` が含まれていること
- `expires_in` が設定値（120秒）と一致すること
- `interval` が設定値（5秒）と一致すること

---

### Step 7: CIBA Token Polling（認証前）

認証が完了する前にトークンエンドポイントをポーリングし、`authorization_pending` エラーが返ることを確認します。

#### リクエスト

```bash
ciba_poll
```

#### 確認ポイント

- `error` が `authorization_pending` であること
- デバイス認証が完了するまでこのエラーが返り続けること

---

### Step 8: 認証トランザクション取得（デバイス側）

デバイス側から認証トランザクション情報を取得します。`authentication_type: none` のため、デバイスシークレット JWT なしでアクセスできます。

#### リクエスト

```bash
TX_RESPONSE=$(curl -s \
  "${TENANT_BASE}/v1/authentication-devices/${DEVICE_ID}/authentications?attributes.auth_req_id=${AUTH_REQ_ID}")

echo "${TX_RESPONSE}" | jq .

TRANSACTION_ID=$(echo "${TX_RESPONSE}" | jq -r '.list[0].id')
echo "Transaction ID: ${TRANSACTION_ID}"
```

#### 確認ポイント

- HTTP 200 が返ること
- 認証トランザクションが取得できること
- `flow` が `ciba` であること

> **注意**: `authentication_type: none` の場合、`context`（binding_message, scopes 等）はレスポンスに含まれません。セキュリティ上、認証されたデバイスにのみ詳細情報が返却されます。

---

### Step 9: Binding Message 検証（デバイス側）

認証デバイスから、CIBAリクエスト時に指定した `binding_message` を検証します。消費デバイスに表示されたコードとデバイスで入力されたコードの一致確認を行います。

#### リクエスト

```bash
curl -s -X POST \
  "${TENANT_BASE}/v1/authentications/${TRANSACTION_ID}/authentication-device-binding-message" \
  -H "Content-Type: application/json" \
  -d "{\"binding_message\": \"${BINDING_MESSAGE}\"}" | jq .
```

#### 確認ポイント

- HTTP 200 が返ること（検証成功）
- 不一致のメッセージを送信した場合は HTTP 400 `"Binding Message is unmatched"` が返ること

---

### Step 10: CIBA Token（binding_message 検証後）

binding_message 検証が成功した後、トークンエンドポイントでトークンを取得します。

#### リクエスト

```bash
ciba_poll
```

#### 確認ポイント

- HTTP 200 で `access_token` が取得できること
- `id_token` が含まれていること

---

### Step 11: UserInfo Verification（CIBA トークン）

CIBA トークンで UserInfo エンドポイントを呼び出し、Phase 1 で作成したユーザーと同一であることを確認します。

#### リクエスト

```bash
CIBA_USERINFO=$(get_userinfo "${CIBA_ACCESS_TOKEN}")
echo "${CIBA_USERINFO}"
```

#### 確認ポイント

- HTTP 200 が返ること
- `sub` が Phase 1 の `sub` と一致すること
- `email` が登録時のメールアドレスと一致すること

---

## Phase 3: CIBA フロー（device: login_hint）

Phase 2 では `email:` プレフィックスでユーザーを特定しましたが、Phase 3 では外部パスワード認証でマッピングされた `device_id` を使って CIBA フローを実行します。

### Step 12: Backchannel Authentication Request（device: login_hint）

`login_hint` に `device:{device_id},idp:{provider_id}` 形式を指定して CIBA リクエストを送信します。

#### リクエスト

```bash
BINDING_MESSAGE_D="5678"

ciba_request "device:${DEVICE_ID},idp:${EXTERNAL_PROVIDER_ID}" "${BINDING_MESSAGE_D}"
echo "Auth Request ID: ${AUTH_REQ_ID}"
```

#### 確認ポイント

- HTTP 200 が返ること
- `auth_req_id` が含まれていること
- 外部パスワード認証でマッピングされた `device_id` でユーザーが正しく特定されること

---

### Step 13: CIBA Token Polling（認証前）

#### リクエスト

```bash
ciba_poll
```

#### 確認ポイント

- `error` が `authorization_pending` であること

---

### Step 14: 認証トランザクション取得 + Binding Message 検証

#### リクエスト

```bash
# 認証トランザクション取得
TX_RESPONSE=$(curl -s \
  "${TENANT_BASE}/v1/authentication-devices/${DEVICE_ID}/authentications?attributes.auth_req_id=${AUTH_REQ_ID}")

TRANSACTION_ID=$(echo "${TX_RESPONSE}" | jq -r '.list[0].id')
echo "Transaction ID: ${TRANSACTION_ID}"

# Binding message 検証
curl -s -X POST \
  "${TENANT_BASE}/v1/authentications/${TRANSACTION_ID}/authentication-device-binding-message" \
  -H "Content-Type: application/json" \
  -d "{\"binding_message\": \"${BINDING_MESSAGE_D}\"}" | jq .
```

#### 確認ポイント

- 認証トランザクションが取得できること
- Binding message 検証が HTTP 200 を返すこと

---

### Step 15: CIBA Token + UserInfo（device hint 経由）

#### リクエスト

```bash
ciba_poll

CIBA_USERINFO=$(get_userinfo "${CIBA_ACCESS_TOKEN}")
echo "${CIBA_USERINFO}"
```

#### 確認ポイント

- HTTP 200 で `access_token` が取得できること
- `sub` が Phase 1 で作成したユーザーの `sub` と一致すること
- `email:` login_hint と `device:` login_hint で同一ユーザーが解決されること

---

## チェックリスト

### 設定前提（setup.sh 実行前に確認）

| # | 確認項目 | 結果 |
|---|---------|------|
| 1 | `custom_claims_scope_mapping: true` が `authorization_server.extension` に設定済み | |
| 2 | `claims_supported` に `authentication_devices` が含まれている | |
| 3 | `scopes_supported` に `claims:authentication_devices` が含まれている | |
| 4 | `authentication_device_rule.authentication_type` が `none` に設定済み | |
| 5 | `authentication_device_rule.issue_device_secret` が `false` に設定済み | |

> `custom_claims_scope_mapping` が未設定だと `claims:*` スコープが機能せず、UserInfo/ID Token にカスタムクレーム（`authentication_devices` 等）が含まれません。

### Phase 1: 外部パスワード認証 + デバイスマッピング

| Step | 確認項目 | 結果 |
|------|---------|------|
| 1 | Mock server が稼働しデバイス情報を返す | |
| 2 | Discovery endpoint が HTTP 200 を返す | |
| 2 | backchannel_authentication_endpoint が含まれている | |
| 2 | grant_types_supported に ciba が含まれている | |
| 3 | 外部パスワード認証でトークンが取得できる | |
| 4 | UserInfo に authentication_devices が含まれる | |
| 4 | デバイス ID が UUID 形式で正しくマッピングされている | |
| 4 | notification_token が含まれている | |
| 5 | 2回目のログインで同一ユーザーが解決される（sub 一致） | |

### Phase 2: CIBA フロー（email: login_hint + binding_message 検証）

| Step | 確認項目 | 結果 |
|------|---------|------|
| 6 | CIBA リクエスト（email: login_hint）で auth_req_id が取得できる | |
| 7 | 認証前ポーリングで authorization_pending が返る | |
| 8 | 認証トランザクションが取得できる | |
| 9 | Binding message 検証が成功する（HTTP 200） | |
| 10 | CIBA token（検証後）で access_token が取得できる | |
| 11 | UserInfo で sub が Phase 1 と一致する | |

### Phase 3: CIBA フロー（device: login_hint + binding_message 検証）

| Step | 確認項目 | 結果 |
|------|---------|------|
| 12 | CIBA リクエスト（device: login_hint）で auth_req_id が取得できる | |
| 13 | 認証前ポーリングで authorization_pending が返る | |
| 14 | 認証トランザクション取得 + Binding message 検証が成功する | |
| 15 | CIBA token で access_token が取得でき、sub が Phase 1 と一致する | |
