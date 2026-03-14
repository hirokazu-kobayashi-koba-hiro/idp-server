# 設定値変更の動作確認ガイド - MFA (Password + Email OTP)

各設定値を変更した際の動作確認手順書です。

> **前提**: `setup.sh` で初期環境が構築済みであること。

## 共通手順

### ヘルパー読み込み + 初期準備

```bash
source config/templates/use-cases/mfa-email/helpers.sh
get_admin_token

# ユーザー登録 + トークン取得
start_auth_flow
register_user
complete_auth_flow
```

これで以下の変数・関数が使えます:

| 関数 | 用途 | 使用例 |
|------|------|--------|
| `update_tenant` | テナント設定を jq フィルタで更新 | `update_tenant '.session_config.timeout_seconds = 10'` |
| `restore_tenant` | テナント設定を初期状態に復元 | `restore_tenant` |
| `update_auth_server` | 認可サーバー設定を jq フィルタで更新 | `update_auth_server '.extension.access_token_duration = 60'` |
| `restore_auth_server` | 認可サーバー設定を初期状態に復元 | `restore_auth_server` |
| `update_client` | クライアント設定を jq フィルタで更新 | `update_client '.scope = "openid profile email transfers"'` |
| `restore_client` | クライアント設定を初期状態に復元 | `restore_client` |
| `update_registration_config` | ユーザー登録設定を jq フィルタで更新 | `update_registration_config '.interactions["initial-registration"].request.schema.required += ["phone_number"]'` |
| `restore_registration_config` | ユーザー登録設定を初期状態に復元 | `restore_registration_config` |
| `get_registration_config` | 現在のユーザー登録設定を取得 | `get_registration_config \| jq .` |
| `update_email_config` | Email OTP 設定を jq フィルタで更新 | `update_email_config '.interactions["email-authentication-challenge"].execution.details.expire_seconds = 10'` |
| `restore_email_config` | Email OTP 設定を初期状態に復元 | `restore_email_config` |
| `get_email_config` | 現在の Email OTP 設定を取得 | `get_email_config \| jq .` |
| `update_auth_policy` | 認証ポリシーを jq フィルタで更新 | `update_auth_policy '.policies[0].step_definitions[0].order = 2'` |
| `update_auth_policy_json` | 認証ポリシーを JSON 全体で更新 | `update_auth_policy_json "$(cat policy.json)"` |
| `restore_auth_policy` | 認証ポリシーを初期状態に復元 | `restore_auth_policy` |
| `get_auth_policy` | 現在の認証ポリシーを取得 | `get_auth_policy \| jq .` |
| `email_challenge` | Email OTP チャレンジ送信 | `email_challenge "${TEST_EMAIL}"` |
| `get_verification_code` | Management API で検証コード取得 | `get_verification_code` |
| `email_verify` | Email OTP 検証 | `email_verify "${VERIFICATION_CODE}"` |
| `mfa_login` | MFA ログイン一括実行 | `mfa_login "${TEST_EMAIL}" "${TEST_PASSWORD}"` |
| `get_view_data` | ViewData取得（適用ポリシー・認証状態） | `get_view_data \| jq '.authentication_policy'` |
| `show_amr` | ID Token の amr（認証方式）表示 | `show_amr` |
| `restore_all` | 全設定を初期状態に復元 | `restore_all` |

---

## パターン 1: Email OTP 有効期間の変更

**ユースケース**: セキュリティ要件に合わせた OTP ライフタイムの調整

### 1-1. 有効期間の短縮

```bash
# OTP 有効期間を 5 秒に短縮（テスト用）
update_email_config \
  '.interactions["email-authentication-challenge"].execution.details.expire_seconds = 5'

# 設定確認
get_email_config | jq '.interactions["email-authentication-challenge"].execution.details.expire_seconds'

# テスト 1: OTP 発行直後に検証 → 成功
start_auth_flow
email_challenge "${TEST_EMAIL}"
get_verification_code
email_verify
# → 200 成功

# テスト 2: OTP 発行後に6秒待って検証 → 失敗（期限切れ）
start_auth_flow
email_challenge "${TEST_EMAIL}"
get_verification_code
sleep 6
email_verify
# → 400 エラー（期限切れ）

restore_email_config
```

**確認ポイント**:

| # | テスト | 期待値 |
|---|--------|--------|
| 1 | OTP 発行直後に検証 | HTTP 200 |
| 2 | 有効期間経過後に検証 | HTTP 400（期限切れ） |

### 1-2. 有効期間の延長

```bash
# OTP 有効期間を 600 秒（10分）に延長
update_email_config \
  '.interactions["email-authentication-challenge"].execution.details.expire_seconds = 600'

# 設定確認
get_email_config | jq '.interactions["email-authentication-challenge"].execution.details.expire_seconds'
# → 600

# テスト: 延長後もフローが正常に動作する
start_auth_flow
email_challenge "${TEST_EMAIL}"
get_verification_code
email_verify
password_login "${TEST_EMAIL}" "${TEST_PASSWORD}"
complete_auth_flow
show_amr

restore_email_config
```

**確認ポイント**:

| # | 確認項目 | 期待値 |
|---|---------|--------|
| 1 | 設定が 600 に更新されている | `get_email_config` で確認 |
| 2 | MFA フロー全体が正常に動作する | `show_amr` で email と password が表示 |

---

## パターン 2: Email OTP リトライ制限の変更

**ユースケース**: ブルートフォース防止の強度調整

### 2-1. リトライ回数の引き下げ

```bash
# リトライ回数を 2 に制限
update_email_config \
  '.interactions["email-authentication-challenge"].execution.details.retry_count_limitation = 2'

# テスト: 間違ったコードで2回失敗
start_auth_flow
email_challenge "${TEST_EMAIL}"
get_verification_code

email_verify "000000"  # 1回目の失敗
email_verify "111111"  # 2回目の失敗

# テスト 1: 3回目は正しいコードでも拒否
email_verify "${VERIFICATION_CODE}"
# → 400 エラー（リトライ上限超過）

restore_email_config
```

**確認ポイント**:

| # | テスト | 期待値 |
|---|--------|--------|
| 1 | 2回失敗後に正しいコード | HTTP 400（リトライ上限超過） |

### 2-2. リトライ制限内での成功

```bash
# リトライ回数を 3 に設定
update_email_config \
  '.interactions["email-authentication-challenge"].execution.details.retry_count_limitation = 3'

# テスト: 2回間違えた後、3回目で正しいコード → 成功
start_auth_flow
email_challenge "${TEST_EMAIL}"
get_verification_code

email_verify "000000"  # 1回目の失敗
email_verify "111111"  # 2回目の失敗
email_verify "${VERIFICATION_CODE}"  # 3回目で成功
# → 200 成功

restore_email_config
```

**確認ポイント**:

| # | テスト | 期待値 |
|---|--------|--------|
| 1 | 制限内（3回目）で正しいコード | HTTP 200 |

---

## パターン 3: OTP 再送信と旧コードの無効化

**ユースケース**: ユーザーがコードを受信できなかった場合の再送フロー

### 3-1. 再チャレンジで新しいコード発行

```bash
# テスト: 再チャレンジ後に旧コードが無効になることを確認
start_auth_flow

# 1回目のチャレンジ
email_challenge "${TEST_EMAIL}"
get_verification_code
OLD_CODE="${VERIFICATION_CODE}"
echo "Old Code: ${OLD_CODE}"

# 2回目のチャレンジ（再送信）
email_challenge "${TEST_EMAIL}"
get_verification_code
NEW_CODE="${VERIFICATION_CODE}"
echo "New Code: ${NEW_CODE}"

# テスト 1: 旧コードで検証 → 失敗（無効化済み）
email_verify "${OLD_CODE}"
# → 400 エラー

# テスト 2: 新コードで検証 → 成功
# 新しいセッションで再度チャレンジから
start_auth_flow
email_challenge "${TEST_EMAIL}"
get_verification_code
email_verify
# → 200 成功
```

**確認ポイント**:

| # | テスト | 期待値 |
|---|--------|--------|
| 1 | 再チャレンジ後に旧コードで検証 | HTTP 400（旧コード無効） |
| 2 | 再チャレンジ後に新コードで検証 | HTTP 200 |

---

## パターン 4: Email テンプレートの変更

**ユースケース**: メール文面のカスタマイズ、ブランディング

### 4-1. 認証テンプレートの変更

```bash
# 認証メールのテンプレートを変更
update_email_config '
  .interactions["email-authentication-challenge"].execution.details.templates.authentication = {
    "subject": "[MyApp] Your verification code",
    "body": "Your code is: {VERIFICATION_CODE}\nExpires in {EXPIRE_SECONDS}s."
  }'

# 設定確認
get_email_config | jq '.interactions["email-authentication-challenge"].execution.details.templates.authentication'

# テスト: テンプレート変更後もフローが正常に動作する
mfa_login "${TEST_EMAIL}" "${TEST_PASSWORD}"
show_amr

restore_email_config
```

**確認ポイント**:

| # | 確認項目 | 期待値 |
|---|---------|--------|
| 1 | テンプレートが更新されている | `get_email_config` で確認 |
| 2 | テンプレート変更後も MFA フローが正常に動作する | `show_amr` で email と password が表示 |

### 4-2. 登録テンプレートの変更

```bash
# 登録メールのテンプレートを変更
update_email_config '
  .interactions["email-authentication-challenge"].execution.details.templates.registration = {
    "subject": "[MyApp] Welcome! Verify your email",
    "body": "Welcome to MyApp!\n\nVerification code: {VERIFICATION_CODE}\nValid for {EXPIRE_SECONDS} seconds."
  }'

# 設定確認
get_email_config | jq '.interactions["email-authentication-challenge"].execution.details.templates.registration'

# テスト: 登録フローが正常に動作する（登録時にも email challenge が使われる場合）
start_auth_flow
register_user
complete_auth_flow
# → 200 成功

restore_email_config
```

**確認ポイント**:

| # | 確認項目 | 期待値 |
|---|---------|--------|
| 1 | 登録テンプレートが更新されている | `get_email_config` で確認 |
| 2 | ユーザー登録フローが正常に動作する | トークン取得成功 |

### 4-3. 送信元アドレスの変更

```bash
# 送信元アドレスを変更
update_email_config \
  '.interactions["email-authentication-challenge"].execution.details.sender = "security@myapp.example.com"'

# 設定確認
get_email_config | jq '.interactions["email-authentication-challenge"].execution.details.sender'
# → "security@myapp.example.com"

# テスト: 送信元変更後もフローが正常に動作する（no-action モードなので送信自体はされない）
mfa_login "${TEST_EMAIL}" "${TEST_PASSWORD}"
show_amr

restore_email_config
```

**確認ポイント**:

| # | 確認項目 | 期待値 |
|---|---------|--------|
| 1 | sender が更新されている | `get_email_config` で確認 |
| 2 | MFA フローが正常に動作する | `show_amr` で email と password が表示 |

---

## パターン 5: 外部メール認証サービスへの切り替え

**ユースケース**: 内部 OTP 生成（no_action）から外部メール認証 API への委譲

デフォルトでは idp-server が OTP を内部で生成・検証しますが、`execution.function` を `http_request` に変更すると、OTP の生成・送信・検証をすべて外部サービスに委譲できます。

### モックサーバー起動

```bash
# 別ターミナルで起動（ポート 4003）
node config/templates/use-cases/mfa-email/mock-server.js
```

idp-server は Docker 内で動作するため、モックサーバーへの接続は `host.docker.internal` を使います。

**テストシナリオ**（`email` の値で動作を制御）:

| email | レスポンス | 用途 |
|-------|----------|------|
| 任意（例: `user@example.com`） | 200 成功 | 正常系 |
| `error@example.com` | 500 サーバーエラー | エラーハンドリング確認 |
| `timeout@example.com` | 504（10秒後） | タイムアウト確認 |

### 5-1. no_action → http_request への切り替え

チャレンジ（OTP送信）と検証（OTP確認）の両方を外部 API に委譲します。

**変更前** (`authentication-config-email.json`):

```json
"email-authentication-challenge": {
  "execution": {
    "function": "email_authentication_challenge",
    "details": { "function": "no_action", ... }
  }
}
```

**変更後**（外部API委譲）:

```json
"email-authentication-challenge": {
  "execution": {
    "function": "http_request",
    "http_request": { "url": "http://host.docker.internal:4003/email/challenge", ... },
    "http_request_store": { "key": "email-authentication-challenge", ... }
  }
}
```

**確認手順**:

```bash
# 0. モックサーバーの動作確認
curl -s http://localhost:4003/email/challenge \
  -X POST -H 'Content-Type: application/json' \
  -d '{"email":"test@example.com","template":"authentication"}' | jq .

# 1. email-authentication-challenge を http_request に変更
update_email_config '
  .interactions["email-authentication-challenge"].execution = {
    "function": "http_request",
    "http_request": {
      "url": "http://host.docker.internal:4003/email/challenge",
      "method": "POST",
      "header_mapping_rules": [
        { "static_value": "application/json", "to": "Content-Type" }
      ],
      "body_mapping_rules": [
        { "from": "$.request_body", "to": "*" }
      ]
    },
    "http_request_store": {
      "key": "email-authentication-challenge",
      "interaction_mapping_rules": [
        { "from": "$.response_body.transaction_id", "to": "transaction_id" },
        { "from": "$.response_body.verification_code", "to": "verification_code" }
      ]
    }
  } |
  .interactions["email-authentication-challenge"].response.body_mapping_rules = [
    { "from": "$.execution_http_request.response_body.status", "to": "status" },
    { "from": "$.execution_http_request.response_body.message", "to": "message", "condition": { "operation": "exists", "path": "$.execution_http_request.response_body.message" } },
    { "from": "$.execution_http_request.response_body.error", "to": "error", "condition": { "operation": "exists", "path": "$.execution_http_request.response_body.error" } },
    { "from": "$.execution_http_request.response_body.error_description", "to": "error_description", "condition": { "operation": "exists", "path": "$.execution_http_request.response_body.error_description" } }
  ]'

# 2. email-authentication を http_request に変更
update_email_config '
  .interactions["email-authentication"].execution = {
    "function": "http_request",
    "previous_interaction": {
      "key": "email-authentication-challenge"
    },
    "http_request": {
      "url": "http://host.docker.internal:4003/email/verify",
      "method": "POST",
      "header_mapping_rules": [
        { "static_value": "application/json", "to": "Content-Type" }
      ],
      "body_mapping_rules": [
        { "from": "$.request_body", "to": "*" },
        { "from": "$.interaction.transaction_id", "to": "transaction_id" }
      ]
    }
  } |
  .interactions["email-authentication"].response.body_mapping_rules = [
    { "from": "$.execution_http_request.response_body.status", "to": "status" },
    { "from": "$.execution_http_request.response_body.message", "to": "message", "condition": { "operation": "exists", "path": "$.execution_http_request.response_body.message" } },
    { "from": "$.execution_http_request.response_body.error", "to": "error", "condition": { "operation": "exists", "path": "$.execution_http_request.response_body.error" } },
    { "from": "$.execution_http_request.response_body.error_description", "to": "error_description", "condition": { "operation": "exists", "path": "$.execution_http_request.response_body.error_description" } }
  ]'

# 3. 設定確認
get_email_config | jq '.interactions["email-authentication-challenge"].execution.function'
# → "http_request"

# 4. MFA ログイン（外部API経由）
# ※ verification_code は http_request_store に内部保存されるので get_verification_code で取得可能
start_auth_flow
email_challenge "${TEST_EMAIL}"
# → {"status": "sent", "message": "Verification code sent to ..."} （verification_code は含まれない）
get_verification_code

email_verify "${VERIFICATION_CODE}"
password_login "${TEST_EMAIL}" "${TEST_PASSWORD}"
complete_auth_flow
show_amr

# 5. エラー系テスト
start_auth_flow
curl -s -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/email-authentication-challenge" \
  -H "Content-Type: application/json" \
  -d '{"email": "error@example.com", "template": "authentication"}'
# → 500 エラー（モックが 500 を返す）

# 6. 元に戻す
restore_email_config
```

> モックサーバーのターミナルにリクエストログが表示されます。ヘッダー・ボディの内容をそこで確認できます。

**確認ポイント**:

| # | 確認項目 | 期待値 |
|---|---------|--------|
| 1 | execution.function が `http_request` になっている | `get_email_config` で確認 |
| 2 | チャレンジ → モックの `/email/challenge` にリクエストが飛ぶ | モックログで確認 |
| 3 | 検証 → モックの `/email/verify` に `transaction_id` 付きでリクエストが飛ぶ | モックログで確認 |
| 4 | MFA フロー全体が成功する | `show_amr` で email と password が表示 |
| 5 | `error@example.com` でチャレンジ → エラー | HTTP 500 |

### 5-2. http_request_store / previous_interaction の動作確認

チャレンジで保存した `transaction_id` が検証時に正しく引き継がれることを確認します。

```bash
# 5-1 と同じ設定を適用（省略: 上記の update_email_config を再実行）

# テスト: transaction_id の引き継ぎ確認
start_auth_flow

# チャレンジ → transaction_id は http_request_store に内部保存（レスポンスには含まれない）
email_challenge "${TEST_EMAIL}"
# → {"status": "sent", "message": "..."} （transaction_id, verification_code はレスポンスに含まれない）

# verification_code は Management API で取得
get_verification_code

# 検証 → previous_interaction から transaction_id が自動注入される
# （リクエストボディには verification_code のみ送信、transaction_id は previous_interaction で注入）
email_verify "${VERIFICATION_CODE}"
# → モックログで transaction_id がボディに含まれていることを確認

restore_email_config
```

**確認ポイント**:

| # | 確認項目 | 期待値 |
|---|---------|--------|
| 1 | チャレンジのレスポンスに `transaction_id`/`verification_code` が含まれない | レスポンスに `status` と `message` のみ |
| 2 | `http_request_store` で `transaction_id` が内部保存される | モックログの `/email/challenge` レスポンスに含まれている |
| 3 | 検証リクエストに `transaction_id` が自動注入される | モックログで `/email/verify` のボディを確認 |

### 5-3. OAuth 認証付き外部 API 呼び出し

```bash
# OAuth 認証付き http_request に変更
update_email_config '
  .interactions["email-authentication-challenge"].execution = {
    "function": "http_request",
    "http_request": {
      "url": "http://host.docker.internal:4003/email/challenge",
      "method": "POST",
      "auth_type": "oauth2",
      "oauth_authorization": {
        "type": "password",
        "token_endpoint": "http://host.docker.internal:4003/oauth/token",
        "client_id": "email-client",
        "username": "email-user",
        "password": "email-pass",
        "scope": "email"
      },
      "header_mapping_rules": [
        { "static_value": "application/json", "to": "Content-Type" }
      ],
      "body_mapping_rules": [
        { "from": "$.request_body", "to": "*" }
      ]
    },
    "http_request_store": {
      "key": "email-authentication-challenge",
      "interaction_mapping_rules": [
        { "from": "$.response_body.transaction_id", "to": "transaction_id" },
        { "from": "$.response_body.verification_code", "to": "verification_code" }
      ]
    }
  } |
  .interactions["email-authentication-challenge"].response.body_mapping_rules = [
    { "from": "$.execution_http_request.response_body.status", "to": "status" },
    { "from": "$.execution_http_request.response_body.message", "to": "message", "condition": { "operation": "exists", "path": "$.execution_http_request.response_body.message" } },
    { "from": "$.execution_http_request.response_body.error", "to": "error", "condition": { "operation": "exists", "path": "$.execution_http_request.response_body.error" } },
    { "from": "$.execution_http_request.response_body.error_description", "to": "error_description", "condition": { "operation": "exists", "path": "$.execution_http_request.response_body.error_description" } }
  ]'

# テスト: チャレンジ → モックログで /oauth/token → /email/challenge の順にリクエスト
start_auth_flow
email_challenge "${TEST_EMAIL}"

# モックログで Authorization: Bearer {token} ヘッダーが付与されていることを確認

# OAuth 認証失敗テスト: client_id を "invalid" に設定
update_email_config \
  '.interactions["email-authentication-challenge"].execution.http_request.oauth_authorization.client_id = "invalid"'

start_auth_flow
curl -s -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/email-authentication-challenge" \
  -H "Content-Type: application/json" \
  -d "{\"email\": \"${TEST_EMAIL}\", \"template\": \"authentication\"}"
# → 500 エラー（トークン取得が 401 で失敗するため）

restore_email_config
```

**確認ポイント**:

| # | 確認項目 | 期待値 |
|---|---------|--------|
| 1 | モックの `/oauth/token` にトークン取得リクエストが飛ぶ | モックログで確認 |
| 2 | `/email/challenge` に `Authorization: Bearer {token}` が付与される | モックログで確認 |
| 3 | `client_id: "invalid"` で認証失敗 → チャレンジ失敗 | HTTP 500 |

### 5-4. ボディマッピングのカスタマイズ

外部 API の仕様に合わせてリクエストボディのフィールド名を変換します。

```bash
# body_mapping_rules を変更して外部API仕様に合わせる
update_email_config '
  .interactions["email-authentication-challenge"].execution = {
    "function": "http_request",
    "http_request": {
      "url": "http://host.docker.internal:4003/email/challenge",
      "method": "POST",
      "header_mapping_rules": [
        { "static_value": "application/json", "to": "Content-Type" }
      ],
      "body_mapping_rules": [
        { "from": "$.request_body.email", "to": "recipient_email" },
        { "from": "$.request_body.template", "to": "message_type" },
        { "static_value": "mfa-email-app", "to": "app_id" }
      ]
    },
    "http_request_store": {
      "key": "email-authentication-challenge",
      "interaction_mapping_rules": [
        { "from": "$.response_body.transaction_id", "to": "transaction_id" },
        { "from": "$.response_body.verification_code", "to": "verification_code" }
      ]
    }
  } |
  .interactions["email-authentication-challenge"].response.body_mapping_rules = [
    { "from": "$.execution_http_request.response_body.status", "to": "status" },
    { "from": "$.execution_http_request.response_body.message", "to": "message", "condition": { "operation": "exists", "path": "$.execution_http_request.response_body.message" } },
    { "from": "$.execution_http_request.response_body.error", "to": "error", "condition": { "operation": "exists", "path": "$.execution_http_request.response_body.error" } },
    { "from": "$.execution_http_request.response_body.error_description", "to": "error_description", "condition": { "operation": "exists", "path": "$.execution_http_request.response_body.error_description" } }
  ]'

# テスト: チャレンジ実行
start_auth_flow
email_challenge "${TEST_EMAIL}"

# モックサーバーのターミナルに以下のように表示されるはず:
#   body: {"recipient_email":"...@example.com","message_type":"authentication","app_id":"mfa-email-app"}

restore_email_config
```

> **注意**: このテストでは意図的にモックサーバーが期待するフィールド名（`email`）と異なるフィールド名（`recipient_email`）に変換しているため、モックサーバーは `400 {"error":"invalid_request","error_description":"email is required"}` を返します。これは body_mapping_rules によるフィールド名変換が正しく動作していることの証明です。実際の外部 API では、その API が期待するフィールド名に合わせてマッピングを設定してください。

**確認ポイント**:

| # | 確認項目 | 期待値 |
|---|---------|--------|
| 1 | `email` → `recipient_email` にフィールド名が変換される | モックログで確認 |
| 2 | `template` → `message_type` にフィールド名が変換される | モックログで確認 |
| 3 | `static_value` の固定値 `app_id` がボディに含まれる | モックログで確認 |
| 4 | モックサーバーが 400 エラーを返す | `email is required`（フィールド名不一致のため） |

---

## パターン 6: 認証ポリシーの変更

**ユースケース**: MFA のステップ順序変更、条件付き MFA、成功条件の変更

### 6-1. ステップ順序の変更（Email → Password を Password → Email に）

> **注意**: `requires_user` は「前のステップで既にユーザーが特定されていること」を前提とするフラグです。
> 順序を変更する場合、最初のステップには `requires_user: false` + `user_identity_source` を設定し、
> そのステップ自身がユーザーを特定するようにしてください。
>
> - **NG**: password (order=1, `requires_user: true`) → まだユーザーが特定されていないため `user_not_found` エラー
> - **OK**: password (order=1, `requires_user: false`, `user_identity_source: "username"`) → username でユーザーを特定

```bash
# ステップ順序を反転: password=1, email=2
# password が最初のステップになるため requires_user: false + user_identity_source が必要
# email は password でユーザーが特定済みなので requires_user: true でOK
update_auth_policy '
  .policies[0].step_definitions = [
    { "method": "password", "order": 1, "requires_user": false, "user_identity_source": "username" },
    { "method": "email", "order": 2, "requires_user": true }
  ]'

# 設定確認
get_auth_policy | jq '.policies[0].step_definitions'

# 新しいユーザーを登録（登録は順序に依存しない）
restore_auth_policy  # 登録は元のポリシーで
start_auth_flow
register_user
complete_auth_flow

# 順序変更を適用
update_auth_policy '
  .policies[0].step_definitions = [
    { "method": "password", "order": 1, "requires_user": false, "user_identity_source": "username" },
    { "method": "email", "order": 2, "requires_user": true }
  ]'

# テスト: Password → Email の順序でログイン
start_auth_flow

# Step 1: パスワード認証（1st factor） — username でユーザーを特定
password_login "${TEST_EMAIL}" "${TEST_PASSWORD}"

# Step 2: Email OTP（2nd factor） — Step 1 で特定済みのユーザーのメールアドレスを使用
email_challenge "${TEST_EMAIL}"
get_verification_code
email_verify

complete_auth_flow

# amr 確認
show_amr
# → amr に "password" と "email" の両方が含まれること

restore_auth_policy
```

**確認ポイント**:

| # | テスト | 期待値 |
|---|--------|--------|
| 1 | Password → Email の順序で MFA 成功 | トークン取得成功 |
| 2 | amr に password と email が含まれる | `show_amr` で確認 |

### 6-2. 条件付き MFA（スコープベース）

特定のスコープが要求された場合のみ MFA を要求するポリシーを設定します。

```bash
# 1. スコープに "transfers" を追加
update_auth_server '.scopes_supported += ["transfers"]'
update_client '.scope = "openid profile email transfers"'

# 2. ポリシーを条件付きに変更
# - transfers スコープ要求時: MFA（password + email）
# - それ以外: パスワードのみ
update_auth_policy_json '{
  "flow": "oauth",
  "enabled": true,
  "policies": [
    {
      "description": "mfa_for_transfers",
      "priority": 10,
      "conditions": {
        "scopes": ["transfers"]
      },
      "available_methods": ["password", "email", "initial-registration"],
      "step_definitions": [
        { "method": "password", "order": 1, "requires_user": false, "user_identity_source": "username" },
        { "method": "email", "order": 2, "requires_user": true, "user_identity_source": "email" }
      ],
      "success_conditions": {
        "any_of": [
          [
            { "path": "$.password-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 },
            { "path": "$.email-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }
          ],
          [
            { "path": "$.initial-registration.success_count", "type": "integer", "operation": "gte", "value": 1 }
          ]
        ]
      }
    },
    {
      "description": "password_only",
      "priority": 2,
      "conditions": {},
      "available_methods": ["password", "initial-registration"],
      "step_definitions": [
        { "method": "password", "order": 1, "requires_user": false, "user_identity_source": "username" }
      ],
      "success_conditions": {
        "any_of": [
          [
            { "path": "$.password-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }
          ],
          [
            { "path": "$.initial-registration.success_count", "type": "integer", "operation": "gte", "value": 1 }
          ]
        ]
      }
    }
  ]
}'

# 新しいユーザーを登録
start_auth_flow
register_user
complete_auth_flow

# テスト 1: transfers スコープなし → パスワードのみでログイン成功
start_auth_flow "openid+profile+email"
password_login "${TEST_EMAIL}" "${TEST_PASSWORD}"
complete_auth_flow
show_amr
# → amr に "password" のみ

# テスト 2: transfers スコープあり + パスワードのみ → 認可コード発行されない

# 2-a. まず scopes が正しく設定されているか確認
get_auth_policy | jq '.policies[] | {description, priority, conditions}'
# → mfa_for_transfers: conditions.scopes = ["transfers"]
# → password_only: conditions = {}

# 2-b. transfers スコープ付きで認可リクエスト
start_auth_flow "openid+profile+email+transfers"

# 2-c. 適用されたポリシーを確認（mfa_for_transfers が適用されるべき）
get_view_data | jq '{description: .authentication_policy.description, conditions: .authentication_policy.conditions}'
# → description: "mfa_for_transfers", conditions.scopes: ["transfers"]
# ⚠️ もし "password_only" が表示された場合:
#   - scopes_supported に "transfers" が追加されているか確認: update_auth_server '.scopes_supported += ["transfers"]'
#   - クライアントの scope に "transfers" が含まれるか確認: update_client '.scope = "openid profile email transfers"'
#   - priority の値を確認: mfa_for_transfers の priority > password_only の priority であること

# 2-d. パスワードだけで認可コードが発行されないことを確認
password_login "${TEST_EMAIL}" "${TEST_PASSWORD}"
complete_auth_flow
# → 認可コード発行失敗（success_conditions 未達: email-authentication が不足）

# 2-e. 認証状態を確認（email-authentication の success_count が 0）
get_view_data | jq '.authentication_interaction'

# テスト 3: transfers スコープあり + MFA → 成功
start_auth_flow "openid+profile+email+transfers"
password_login "${TEST_EMAIL}" "${TEST_PASSWORD}"
email_challenge "${TEST_EMAIL}"
get_verification_code
email_verify
complete_auth_flow
show_amr
# → amr に "password" と "email" の両方

restore_auth_policy
restore_auth_server
restore_client
```

**確認ポイント**:

| # | テスト | 期待値 |
|---|--------|--------|
| 1 | transfers スコープなし → パスワードのみ | amr: ["password"] |
| 2 | transfers スコープあり + パスワードのみ | 認可コード発行失敗（MFA 未達） |
| 3 | transfers スコープあり + MFA | amr: ["password", "email"] |

### 6-3. success_conditions の変更（AND → OR）

```bash
# success_conditions を変更: email OR password でログイン可能にする
update_auth_policy '
  .policies[0].success_conditions = {
    "any_of": [
      [
        { "path": "$.email-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }
      ],
      [
        { "path": "$.password-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }
      ],
      [
        { "path": "$.initial-registration.success_count", "type": "integer", "operation": "gte", "value": 1 }
      ]
    ]
  }'

# テスト: Email OTP のみでログイン成功（パスワード認証をスキップ）
start_auth_flow
email_challenge "${TEST_EMAIL}"
get_verification_code
email_verify
complete_auth_flow
show_amr
# → amr に "email" のみ（password なしでも成功）

restore_auth_policy
```

**確認ポイント**:

| # | テスト | 期待値 |
|---|--------|--------|
| 1 | Email OTP のみでログイン | トークン取得成功 |
| 2 | amr に email のみ含まれる | `show_amr` で確認 |

---

## パターン 7: ユーザー登録スキーマの変更

**ユースケース**: 登録時の必須項目変更、バリデーション強化

### 7-1. 必須項目の追加

```bash
# phone_number を必須に追加
update_registration_config '
  .interactions["initial-registration"].request.schema.required += ["phone_number"]'

# 設定確認
get_registration_config | jq '.interactions["initial-registration"].request.schema.required'

# テスト 1: phone_number なし → 失敗
start_auth_flow
register_user "test-nophone-$(date +%s)@example.com" "ValidPass123" "Test User"
# → 400 エラー

# テスト 2: phone_number あり → 成功
start_auth_flow
curl -s -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/initial-registration" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test-phone-'"$(date +%s)"'@example.com",
    "password": "ValidPass123",
    "name": "Test User",
    "phone_number": "09012345678"
  }'

restore_registration_config
```

**確認ポイント**:

| # | テスト | 期待値 |
|---|--------|--------|
| 1 | phone_number なしで登録 | HTTP 400 |
| 2 | phone_number ありで登録 | HTTP 200 |

### 7-2. バリデーション制約の変更

```bash
# パスワードの minLength を 12 に変更
update_registration_config '
  .interactions["initial-registration"].request.schema.properties.password.minLength = 12'

# テスト 1: パスワード 11 文字 → 失敗
start_auth_flow
register_user "test-short-$(date +%s)@example.com" "ShortPass1!" "Test User"

# テスト 2: パスワード 12 文字以上 → 成功
start_auth_flow
register_user "test-long-$(date +%s)@example.com" "LongPassword123!" "Test User"

restore_registration_config
```

**確認ポイント**:

| # | テスト | 期待値 |
|---|--------|--------|
| 1 | パスワード 11 文字 | HTTP 400 |
| 2 | パスワード 12 文字以上 | HTTP 200 |

---

## チェックリスト

### パターン 1: Email OTP 有効期間の変更

| # | 確認項目 | 結果 |
|---|---------|------|
| 1-1a | 有効期間内に OTP 検証が成功 | |
| 1-1b | 有効期間経過後に OTP 検証が 400 エラー | |
| 1-2 | 延長後も MFA フロー全体が正常動作 | |

### パターン 2: Email OTP リトライ制限の変更

| # | 確認項目 | 結果 |
|---|---------|------|
| 2-1 | retry_count_limitation 超過後に正しいコードでも 400 エラー | |
| 2-2 | 制限内（3回目）で正しいコードが成功 | |

### パターン 3: OTP 再送信と旧コードの無効化

| # | 確認項目 | 結果 |
|---|---------|------|
| 3-1a | 再チャレンジ後に旧コードで検証が 400 エラー | |
| 3-1b | 再チャレンジ後に新コードで検証が成功 | |

### パターン 4: Email テンプレートの変更

| # | 確認項目 | 結果 |
|---|---------|------|
| 4-1 | 認証テンプレート変更後も MFA フローが正常動作 | |
| 4-2 | 登録テンプレート変更後もユーザー登録フローが正常動作 | |
| 4-3 | 送信元アドレス変更後も MFA フローが正常動作 | |

### パターン 5: 外部メール認証サービスへの切り替え

| # | 確認項目 | 結果 |
|---|---------|------|
| 5-1a | execution を http_request に変更してチャレンジが外部APIに飛ぶ | |
| 5-1b | 検証が外部APIに transaction_id 付きで飛ぶ | |
| 5-1c | MFA フロー全体が外部API経由で成功する | |
| 5-1d | `error@example.com` でチャレンジ → エラー | |
| 5-2a | http_request_store で transaction_id が保存される | |
| 5-2b | previous_interaction で transaction_id が検証リクエストに注入される | |
| 5-3a | OAuth 認証で外部APIにトークンが付与される | |
| 5-3b | OAuth 認証失敗（invalid client_id）でエラーになる | |
| 5-4a | body_mapping_rules でフィールド名が変換される | |
| 5-4b | static_value の固定値がボディに含まれる | |
| 5-4c | フィールド名不一致で外部APIが 400 エラーを返す | |

### パターン 6: 認証ポリシーの変更

| # | 確認項目 | 結果 |
|---|---------|------|
| 6-1a | Password → Email の順序で MFA 成功 | |
| 6-1b | amr に password と email が含まれる | |
| 6-2a | transfers スコープなし → パスワードのみでログイン | |
| 6-2b | transfers スコープあり + パスワードのみ → 認可コード発行失敗 | |
| 6-2c | transfers スコープあり + MFA → 成功 | |
| 6-3 | Email OTP のみでログイン成功（OR 条件） | |

### パターン 7: ユーザー登録スキーマの変更

| # | 確認項目 | 結果 |
|---|---------|------|
| 7-1a | 必須追加フィールドなしで 400 エラー | |
| 7-1b | 必須追加フィールドありで成功 | |
| 7-2a | minLength 未満のパスワードで 400 エラー | |
| 7-2b | minLength 以上のパスワードで成功 | |
