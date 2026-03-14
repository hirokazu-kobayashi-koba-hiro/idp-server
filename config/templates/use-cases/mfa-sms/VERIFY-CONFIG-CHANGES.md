# 設定値変更の動作確認ガイド - MFA (Password + SMS OTP)

各設定値を変更した際の動作確認手順書です。

> **前提**: `setup.sh` で初期環境が構築済みであること。

## 共通手順

### ヘルパー読み込み + 初期準備

```bash
source config/templates/use-cases/mfa-sms/helpers.sh
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
| `update_sms_config` | SMS OTP 設定を jq フィルタで更新 | `update_sms_config '.interactions["sms-authentication-challenge"].execution.http_request.url = "http://host.docker.internal:4004/sms/challenge"'` |
| `restore_sms_config` | SMS OTP 設定を初期状態に復元 | `restore_sms_config` |
| `get_sms_config` | 現在の SMS OTP 設定を取得 | `get_sms_config \| jq .` |
| `update_auth_policy` | 認証ポリシーを jq フィルタで更新 | `update_auth_policy '.policies[0].step_definitions[0].order = 2'` |
| `update_auth_policy_json` | 認証ポリシーを JSON 全体で更新 | `update_auth_policy_json "$(cat policy.json)"` |
| `restore_auth_policy` | 認証ポリシーを初期状態に復元 | `restore_auth_policy` |
| `get_auth_policy` | 現在の認証ポリシーを取得 | `get_auth_policy \| jq .` |
| `sms_challenge` | SMS OTP チャレンジ送信 | `sms_challenge "${TEST_PHONE}"` |
| `get_verification_code` | Management API で検証コード取得 | `get_verification_code` |
| `sms_verify` | SMS OTP 検証 | `sms_verify "${VERIFICATION_CODE}"` |
| `mfa_login` | MFA ログイン一括実行 | `mfa_login "${TEST_PHONE}" "${TEST_PASSWORD}"` |
| `get_view_data` | ViewData取得（適用ポリシー・認証状態） | `get_view_data \| jq '.authentication_policy'` |
| `show_amr` | ID Token の amr（認証方式）表示 | `show_amr` |
| `restore_all` | 全設定を初期状態に復元 | `restore_all` |

---

## パターン 1: OTP 再送信と旧コードの無効化

**ユースケース**: ユーザーがコードを受信できなかった場合の再送フロー

### 1-1. 再チャレンジで新しいコード発行

```bash
# テスト: 再チャレンジ後に旧コードが無効になることを確認
start_auth_flow

# Step 1: パスワード認証（1st factor）
password_login "${TEST_PHONE}" "${TEST_PASSWORD}"

# 1回目のチャレンジ
sms_challenge "${TEST_PHONE}"
get_verification_code
OLD_CODE="${VERIFICATION_CODE}"
echo "Old Code: ${OLD_CODE}"

# 2回目のチャレンジ（再送信）
sms_challenge "${TEST_PHONE}"
get_verification_code
NEW_CODE="${VERIFICATION_CODE}"
echo "New Code: ${NEW_CODE}"

# テスト 1: 旧コードで検証 → 失敗（無効化済み）
sms_verify "${OLD_CODE}"
# → 400 エラー

# テスト 2: 新コードで検証 → 成功
# 新しいセッションで再度チャレンジから
start_auth_flow
password_login "${TEST_PHONE}" "${TEST_PASSWORD}"
sms_challenge "${TEST_PHONE}"
get_verification_code
sms_verify
# → 200 成功
```

**確認ポイント**:

| # | テスト | 期待値 |
|---|--------|--------|
| 1 | 再チャレンジ後に旧コードで検証 | HTTP 400（旧コード無効） |
| 2 | 再チャレンジ後に新コードで検証 | HTTP 200 |

---

## パターン 2: 外部SMS認証サービスの設定変更

**ユースケース**: デフォルトのモックサーバーから別の外部サービスへの切り替え、エラーハンドリング確認

デフォルトでは外部API委譲（`http_request`）モードで、付属のモックサーバー（ポート4004）を使用します。

### モックサーバー起動

```bash
# 別ターミナルで起動（ポート 4004）
node config/templates/use-cases/mfa-sms/mock-server.js
```

idp-server は Docker 内で動作するため、モックサーバーへの接続は `host.docker.internal` を使います。

> SMS が 2nd factor（`requires_user: true`）の場合、リクエストの `phone_number` は無視され、
> パスワード認証で特定済みのユーザーの電話番号が使用されます。

### 2-1. 正常系: モックサーバー経由のMFAフロー

デフォルト設定（http_request + モックサーバー）での正常動作を確認します。

**確認手順**:

```bash
# 1. 設定確認（デフォルトで http_request）
get_sms_config | jq '.interactions["sms-authentication-challenge"].execution.function'
# → "http_request"

# 2. MFA ログイン（外部API経由）
# ※ verification_code は http_request_store に内部保存されるので get_verification_code で取得可能
start_auth_flow
password_login "${TEST_PHONE}" "${TEST_PASSWORD}"
sms_challenge "${TEST_PHONE}"
# → {"status": "sent", "message": "Verification code sent to ..."} （verification_code は含まれない）
get_verification_code

sms_verify "${VERIFICATION_CODE}"
complete_auth_flow
show_amr
```

> モックサーバーのターミナルにリクエストログが表示されます。ヘッダー・ボディの内容をそこで確認できます。

**確認ポイント**:

| # | 確認項目 | 期待値 |
|---|---------|--------|
| 1 | execution.function が `http_request` になっている | `get_sms_config` で確認 |
| 2 | チャレンジ → モックの `/sms/challenge` にリクエストが飛ぶ | モックログで確認 |
| 3 | 検証 → モックの `/sms/verify` に `transaction_id` 付きでリクエストが飛ぶ | モックログで確認 |
| 4 | MFA フロー全体が成功する | `show_amr` で sms と password が表示 |

### 2-2. エラー系: 外部APIの接続エラー

外部SMSサービスが利用不可の場合のエラーハンドリングを確認します。

> **注意**: SMS が 2nd factor（`requires_user: true`）の場合、リクエストの `phone_number` は無視され、
> パスワード認証で特定済みのユーザーの電話番号が使用されます。
> そのため、エラーテストではモックサーバーの phone_number トリガーではなく、接続先 URL を変更して検証します。

```bash
# SMS設定の URL を無効なポートに変更
update_sms_config \
  '.interactions["sms-authentication-challenge"].execution.http_request.url = "http://host.docker.internal:9999/sms/challenge"'

start_auth_flow
password_login "${TEST_PHONE}" "${TEST_PASSWORD}"
sms_challenge "${TEST_PHONE}"
# → 500 エラー（接続先が存在しないため）

restore_sms_config
```

**確認ポイント**:

| # | 確認項目 | 期待値 |
|---|---------|--------|
| 1 | 無効な URL でチャレンジ | HTTP 500 |

### 2-3. http_request_store / previous_interaction の動作確認

チャレンジで保存した `transaction_id` が検証時に正しく引き継がれることを確認します。

```bash
# テスト: transaction_id の引き継ぎ確認
start_auth_flow
password_login "${TEST_PHONE}" "${TEST_PASSWORD}"

# チャレンジ → transaction_id は http_request_store に内部保存（レスポンスには含まれない）
sms_challenge "${TEST_PHONE}"
# → {"status": "sent", "message": "..."} （transaction_id, verification_code はレスポンスに含まれない）

# verification_code は Management API で取得
get_verification_code

# 検証 → previous_interaction から transaction_id が自動注入される
# （リクエストボディには verification_code のみ送信、transaction_id は previous_interaction で注入）
sms_verify "${VERIFICATION_CODE}"
# → モックログで transaction_id がボディに含まれていることを確認
```

**確認ポイント**:

| # | 確認項目 | 期待値 |
|---|---------|--------|
| 1 | チャレンジのレスポンスに `transaction_id`/`verification_code` が含まれない | レスポンスに `status` と `message` のみ |
| 2 | `http_request_store` で `transaction_id` が内部保存される | モックログの `/sms/challenge` レスポンスに含まれている |
| 3 | 検証リクエストに `transaction_id` が自動注入される | モックログで `/sms/verify` のボディを確認 |

### 2-4. OAuth 認証付き外部 API 呼び出し

```bash
# OAuth 認証付き http_request に変更
update_sms_config '
  .interactions["sms-authentication-challenge"].execution = {
    "function": "http_request",
    "http_request": {
      "url": "http://host.docker.internal:4004/sms/challenge",
      "method": "POST",
      "auth_type": "oauth2",
      "oauth_authorization": {
        "type": "password",
        "token_endpoint": "http://host.docker.internal:4004/oauth/token",
        "client_id": "sms-client",
        "username": "sms-user",
        "password": "sms-pass",
        "scope": "sms"
      },
      "header_mapping_rules": [
        { "static_value": "application/json", "to": "Content-Type" }
      ],
      "body_mapping_rules": [
        { "from": "$.request_body", "to": "*" }
      ]
    },
    "http_request_store": {
      "key": "sms-authentication-challenge",
      "interaction_mapping_rules": [
        { "from": "$.response_body.transaction_id", "to": "transaction_id" },
        { "from": "$.response_body.verification_code", "to": "verification_code" }
      ]
    }
  } |
  .interactions["sms-authentication-challenge"].response.body_mapping_rules = [
    { "from": "$.execution_http_request.response_body.status", "to": "status" },
    { "from": "$.execution_http_request.response_body.message", "to": "message", "condition": { "operation": "exists", "path": "$.execution_http_request.response_body.message" } },
    { "from": "$.execution_http_request.response_body.error", "to": "error", "condition": { "operation": "exists", "path": "$.execution_http_request.response_body.error" } },
    { "from": "$.execution_http_request.response_body.error_description", "to": "error_description", "condition": { "operation": "exists", "path": "$.execution_http_request.response_body.error_description" } }
  ]'

# テスト: チャレンジ → モックログで /oauth/token → /sms/challenge の順にリクエスト
start_auth_flow
password_login "${TEST_PHONE}" "${TEST_PASSWORD}"
sms_challenge "${TEST_PHONE}"

# モックログで Authorization: Bearer {token} ヘッダーが付与されていることを確認

# OAuth 認証失敗テスト: client_id を "invalid" に設定
update_sms_config \
  '.interactions["sms-authentication-challenge"].execution.http_request.oauth_authorization.client_id = "invalid"'

start_auth_flow
password_login "${TEST_PHONE}" "${TEST_PASSWORD}"
sms_challenge "${TEST_PHONE}"
# → 500 エラー（トークン取得が 401 で失敗するため）

restore_sms_config
```

**確認ポイント**:

| # | 確認項目 | 期待値 |
|---|---------|--------|
| 1 | モックの `/oauth/token` にトークン取得リクエストが飛ぶ | モックログで確認 |
| 2 | `/sms/challenge` に `Authorization: Bearer {token}` が付与される | モックログで確認 |
| 3 | `client_id: "invalid"` で認証失敗 → チャレンジ失敗 | HTTP 500 |

### 2-5. ボディマッピングルールの動作確認

`body_mapping_rules` によるリクエストボディのフィールド名変換が正しく動作することを確認します。
意図的にモックサーバーが期待しないフィールド名に変換し、変換が効いていることをエラーレスポンスで検証します。

```bash
# body_mapping_rules を変更して外部API仕様に合わせる
update_sms_config '
  .interactions["sms-authentication-challenge"].execution = {
    "function": "http_request",
    "http_request": {
      "url": "http://host.docker.internal:4004/sms/challenge",
      "method": "POST",
      "header_mapping_rules": [
        { "static_value": "application/json", "to": "Content-Type" }
      ],
      "body_mapping_rules": [
        { "from": "$.request_body.phone_number", "to": "recipient_phone" },
        { "from": "$.request_body.template", "to": "message_type" },
        { "static_value": "mfa-sms-app", "to": "app_id" }
      ]
    },
    "http_request_store": {
      "key": "sms-authentication-challenge",
      "interaction_mapping_rules": [
        { "from": "$.response_body.transaction_id", "to": "transaction_id" },
        { "from": "$.response_body.verification_code", "to": "verification_code" }
      ]
    }
  } |
  .interactions["sms-authentication-challenge"].response.body_mapping_rules = [
    { "from": "$.execution_http_request.response_body.status", "to": "status" },
    { "from": "$.execution_http_request.response_body.message", "to": "message", "condition": { "operation": "exists", "path": "$.execution_http_request.response_body.message" } },
    { "from": "$.execution_http_request.response_body.error", "to": "error", "condition": { "operation": "exists", "path": "$.execution_http_request.response_body.error" } },
    { "from": "$.execution_http_request.response_body.error_description", "to": "error_description", "condition": { "operation": "exists", "path": "$.execution_http_request.response_body.error_description" } }
  ]'

# テスト: チャレンジ実行
start_auth_flow
password_login "${TEST_PHONE}" "${TEST_PASSWORD}"
sms_challenge "${TEST_PHONE}"
# → 400 エラー（モックサーバーが phone_number フィールドを期待するが、recipient_phone に変換されているため）
#
# モックサーバーのターミナルに以下のように表示されるはず:
#   body: {"recipient_phone":"09012345678","message_type":"authentication","app_id":"mfa-sms-app"}
# → フィールド名変換が正しく動作していることの証明

restore_sms_config
```

> **注意**: このテストでは意図的にモックサーバーが期待するフィールド名（`phone_number`）と異なるフィールド名（`recipient_phone`）に変換しているため、モックサーバーは `400 {"error":"invalid_request","error_description":"phone_number is required"}` を返します。これは body_mapping_rules によるフィールド名変換が正しく動作していることの証明です。実際の外部 API では、その API が期待するフィールド名に合わせてマッピングを設定してください。

**確認ポイント**:

| # | 確認項目 | 期待値 |
|---|---------|--------|
| 1 | `phone_number` → `recipient_phone` にフィールド名が変換される | モックログで確認 |
| 2 | `template` → `message_type` にフィールド名が変換される | モックログで確認 |
| 3 | `static_value` の固定値 `app_id` がボディに含まれる | モックログで確認 |
| 4 | モックサーバーが 400 エラーを返す | `phone_number is required`（フィールド名不一致のため） |

---

## パターン 3: 認証ポリシーの変更

**ユースケース**: MFA のステップ順序変更、条件付き MFA、成功条件の変更

### 3-1. ステップ順序の変更（Password → SMS を SMS → Password に）

> **注意**: `requires_user` は「前のステップで既にユーザーが特定されていること」を前提とするフラグです。
> 順序を変更する場合、最初のステップには `requires_user: false` + `user_identity_source` を設定し、
> そのステップ自身がユーザーを特定するようにしてください。
>
> - **NG**: sms (order=1, `requires_user: true`) → まだユーザーが特定されていないため `user_not_found` エラー
> - **OK**: sms (order=1, `requires_user: false`, `user_identity_source: "phone_number"`) → phone_number でユーザーを特定

```bash
# === NG パターン: requires_user: true のまま1st factorにすると失敗 ===

update_auth_policy '
  .policies[0].step_definitions = [
    { "method": "sms", "order": 1, "requires_user": true },
    { "method": "password", "order": 2, "requires_user": false, "user_identity_source": "username" }
  ]'

start_auth_flow
sms_challenge "${TEST_PHONE}"
# → エラー（まだユーザーが特定されていないため）

restore_auth_policy

# === OK パターン: requires_user: false + user_identity_source で1st factorにする ===

# sms が最初のステップになるため requires_user: false + user_identity_source が必要
# password は sms でユーザーが特定済みなので requires_user: true でOK
update_auth_policy '
  .policies[0].step_definitions = [
    { "method": "sms", "order": 1, "requires_user": false, "user_identity_source": "phone_number" },
    { "method": "password", "order": 2, "requires_user": true }
  ]'

# 設定確認
get_auth_policy | jq '.policies[0].step_definitions'

# テスト: SMS → Password の順序でログイン
start_auth_flow

# Step 1: SMS OTP（1st factor） — phone_number でユーザーを特定
sms_challenge "${TEST_PHONE}"
get_verification_code
sms_verify

# Step 2: パスワード認証（2nd factor） — Step 1 で特定済みのユーザーを使用
# ⚠️ requires_user: true の場合、PasswordAuthenticationExecutor は
#    username ではなくトランザクションのユーザーを使うべきだが、
#    現実装では findByPreferredUsername で検索するため preferred_username を渡す必要がある
#    → Issue #1396 参照
password_login "${TEST_PHONE}" "${TEST_PASSWORD}"

complete_auth_flow

# amr 確認
show_amr
# → amr に "password" と "sms" の両方が含まれること

restore_auth_policy
```

**確認ポイント**:

| # | テスト | 期待値 |
|---|--------|--------|
| 1 | NG: `requires_user: true` で1st factorに設定 → チャレンジ失敗 | エラー（ユーザー未特定） |
| 2 | OK: SMS → Password の順序で MFA 成功 | トークン取得成功 |
| 3 | amr に password と sms が含まれる | `show_amr` で確認 |

### 3-2. 条件付き MFA（スコープベース）

特定のスコープが要求された場合のみ MFA を要求するポリシーを設定します。

```bash
# 1. スコープに "transfers" を追加
update_auth_server '.scopes_supported += ["transfers"]'
update_client '.scope = "openid profile email transfers"'

# 2. ポリシーを条件付きに変更
# - transfers スコープ要求時: MFA（password + sms）
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
      "available_methods": ["password", "sms", "initial-registration"],
      "step_definitions": [
        { "method": "password", "order": 1, "requires_user": false, "user_identity_source": "username" },
        { "method": "sms", "order": 2, "requires_user": true, "user_identity_source": "phone_number" }
      ],
      "success_conditions": {
        "any_of": [
          [
            { "path": "$.password-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 },
            { "path": "$.sms-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }
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

# 共通手順で登録済みのユーザーを使用

# テスト 1: transfers スコープなし → パスワードのみでログイン成功
start_auth_flow "openid+profile+email"
password_login "${TEST_PHONE}" "${TEST_PASSWORD}"
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
password_login "${TEST_PHONE}" "${TEST_PASSWORD}"
complete_auth_flow
# → 認可コード発行失敗（success_conditions 未達: sms-authentication が不足）

# テスト 3: transfers スコープあり + MFA → 成功
start_auth_flow "openid+profile+email+transfers"
password_login "${TEST_PHONE}" "${TEST_PASSWORD}"
sms_challenge "${TEST_PHONE}"
get_verification_code
sms_verify
complete_auth_flow
show_amr
# → amr に "password" と "sms" の両方

restore_auth_policy
restore_auth_server
restore_client
```

**確認ポイント**:

| # | テスト | 期待値 |
|---|--------|--------|
| 1 | transfers スコープなし → パスワードのみ | amr: ["password"] |
| 2 | transfers スコープあり + パスワードのみ | 認可コード発行失敗（MFA 未達） |
| 3 | transfers スコープあり + MFA | amr: ["password", "sms"] |

### 3-3. success_conditions の変更（AND → OR）

```bash
# success_conditions を変更: sms OR password でログイン可能にする
# sms のみでもログインできるように requires_user: false + user_identity_source を設定
update_auth_policy '
  .policies[0].step_definitions = [
    { "method": "password", "order": 1, "requires_user": false, "user_identity_source": "username" },
    { "method": "sms", "order": 2, "requires_user": false, "user_identity_source": "phone_number" }
  ] |
  .policies[0].success_conditions = {
    "any_of": [
      [
        { "path": "$.sms-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }
      ],
      [
        { "path": "$.password-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }
      ],
      [
        { "path": "$.initial-registration.success_count", "type": "integer", "operation": "gte", "value": 1 }
      ]
    ]
  }'

# テスト: SMS OTP のみでログイン成功（パスワード認証をスキップ）
start_auth_flow
sms_challenge "${TEST_PHONE}"
get_verification_code
sms_verify
complete_auth_flow
show_amr
# → amr に "sms" のみ（password なしでも成功）

restore_auth_policy
```

**確認ポイント**:

| # | テスト | 期待値 |
|---|--------|--------|
| 1 | SMS OTP のみでログイン | トークン取得成功 |
| 2 | amr に sms のみ含まれる | `show_amr` で確認 |

---

## パターン 4: ユーザー登録スキーマの変更

**ユースケース**: 登録時の必須項目変更、バリデーション強化

### 4-1. 必須項目の追加

```bash
# given_name を必須に追加
update_registration_config '
  .interactions["initial-registration"].request.schema.required += ["given_name"]'

# 設定確認
get_registration_config | jq '.interactions["initial-registration"].request.schema.required'

# テスト 1: given_name なし → 失敗
start_auth_flow
register_user "test-noname-$(date +%s)@example.com" "ValidPass123" "Test User"
# → 400 エラー

# テスト 2: given_name あり → 成功
start_auth_flow
curl -s -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/initial-registration" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test-given-'"$(date +%s)"'@example.com",
    "password": "ValidPass123",
    "name": "Test User",
    "given_name": "Taro",
    "phone_number": "09012345678"
  }'

restore_registration_config
```

**確認ポイント**:

| # | テスト | 期待値 |
|---|--------|--------|
| 1 | given_name なしで登録 | HTTP 400 |
| 2 | given_name ありで登録 | HTTP 200 |

### 4-2. バリデーション制約の変更

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

### パターン 1: OTP 再送信と旧コードの無効化

| # | 確認項目 | 結果 |
|---|---------|------|
| 1-1a | 再チャレンジ後に旧コードで検証が 400 エラー | |
| 1-1b | 再チャレンジ後に新コードで検証が成功 | |

### パターン 2: 外部SMS認証サービスの設定変更

| # | 確認項目 | 結果 |
|---|---------|------|
| 2-1a | デフォルト設定でチャレンジが外部APIに飛ぶ | |
| 2-1b | 検証が外部APIに transaction_id 付きで飛ぶ | |
| 2-1c | MFA フロー全体が外部API経由で成功する | |
| 2-2 | 無効な URL でチャレンジ → エラー | |
| 2-3a | http_request_store で transaction_id が保存される | |
| 2-3b | previous_interaction で transaction_id が検証リクエストに注入される | |
| 2-4a | OAuth 認証で外部APIにトークンが付与される | |
| 2-4b | OAuth 認証失敗（invalid client_id）でエラーになる | |
| 2-5a | body_mapping_rules でフィールド名が変換される | |
| 2-5b | static_value の固定値がボディに含まれる | |
| 2-5c | フィールド名不一致で外部APIが 400 エラーを返す | |

### パターン 3: 認証ポリシーの変更

| # | 確認項目 | 結果 |
|---|---------|------|
| 3-1a | NG: `requires_user: true` で1st factor → チャレンジ失敗 | |
| 3-1b | OK: SMS → Password の順序で MFA 成功 | |
| 3-1c | amr に password と sms が含まれる | |
| 3-2a | transfers スコープなし → パスワードのみでログイン | |
| 3-2b | transfers スコープあり + パスワードのみ → 認可コード発行失敗 | |
| 3-2c | transfers スコープあり + MFA → 成功 | |
| 3-3 | SMS OTP のみでログイン成功（OR 条件） | |

### パターン 4: ユーザー登録スキーマの変更

| # | 確認項目 | 結果 |
|---|---------|------|
| 4-1a | 必須追加フィールドなしで 400 エラー | |
| 4-1b | 必須追加フィールドありで成功 | |
| 4-2a | minLength 未満のパスワードで 400 エラー | |
| 4-2b | minLength 以上のパスワードで成功 | |
