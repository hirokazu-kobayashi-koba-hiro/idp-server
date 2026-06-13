# 設定値変更の動作確認ガイド - External API Auth

各設定値を変更した際の動作確認手順書です。

> **前提**: `setup.sh` で初期環境が構築済みであること。

## 共通手順

### ヘルパー読み込み + 初期準備

```bash
source config/templates/use-cases/external-api-auth/helpers.sh
get_admin_token
```

これで以下の関数が使えます:

| 関数 | 用途 |
|------|------|
| `update_auth_config` | 認証設定を jq フィルタで更新 |
| `restore_auth_config` | 認証設定を初期状態に復元 |
| `update_auth_policy` | 認証ポリシーを JSON で更新 |
| `restore_auth_policy` | 認証ポリシーを初期状態に復元 |
| `update_tenant` | テナント設定を jq フィルタで更新 |
| `restore_tenant` | テナント設定を初期状態に復元 |
| `update_auth_server` | 認可サーバー設定を jq フィルタで更新 |
| `restore_auth_server` | 認可サーバー設定を初期状態に復元 |
| `start_auth_flow` | 認可リクエスト開始 |
| `external_api_login` | External API 認証（interaction 指定） |
| `complete_auth_flow` | 認可→コード取得→トークン交換 |
| `show_amr` | ID Token の amr 表示 |
| `restore_all` | 全設定を初期状態に復元 |

---

## パターン 1: 外部API URL の変更

**ユースケース**: 外部認証サービスの切り替え（本番→ステージング、サービスA→サービスB）

### 1-1. 到達不能な URL に変更

```bash
# 到達不能な URL に変更
update_auth_config \
  '.interactions.password_verify.execution.http_request.url = "http://host.docker.internal:9999/nonexistent"'

# テスト: 認証 → 外部 API 到達不能でエラー
start_auth_flow
external_api_login "password_verify" "extapi-user@example.com" "ExternalPass123!"
# → 503 Service Unavailable（外部 API に接続できない）

restore_auth_config
```

**確認ポイント**:

| # | テスト | 期待値 |
|---|--------|--------|
| 1 | 到達不能 URL で認証 | HTTP 503 |

### 1-2. URL 復元後に正常動作

```bash
restore_auth_config

# テスト: 復元後に正常認証
start_auth_flow
external_api_login "password_verify" "extapi-user@example.com" "ExternalPass123!"
# → 200 成功

complete_auth_flow
show_amr
```

---

## パターン 2: interaction の追加

**ユースケース**: 既存設定にリスク判定 interaction を追加

### 2-1. risk_check interaction を追加

```bash
# 既存設定に risk_check を追加
update_auth_config '
  .interactions.risk_check = {
    "execution": {
      "function": "http_request",
      "http_request": {
        "url": "http://host.docker.internal:4000/e2e/error-responses",
        "method": "POST",
        "header_mapping_rules": [
          { "static_value": "application/json", "to": "Content-Type" }
        ],
        "body_mapping_rules": [
          { "from": "$.request_body", "to": "*" }
        ]
      }
    },
    "response": {
      "body_mapping_rules": [
        { "from": "$.execution_http_request.response_body", "to": "*" }
      ]
    }
  }'

# テスト 1: password_verify は引き続き動作
start_auth_flow
external_api_login "password_verify" "extapi-user@example.com" "ExternalPass123!"
# → 200 成功

# テスト 2: risk_check が新たに使える
external_api_login "risk_check" "" ""
# → 200 {"status": "success", ...}

complete_auth_flow
show_amr

restore_auth_config
```

**確認ポイント**:

| # | テスト | 期待値 |
|---|--------|--------|
| 1 | 既存 password_verify が動作 | HTTP 200 |
| 2 | 新規 risk_check が動作 | HTTP 200 |

---

## パターン 3: JSON Schema バリデーションの変更

**ユースケース**: リクエストの必須フィールド変更、バリデーション強化

### 3-1. 必須フィールドの削除

```bash
# username を必須から外す
update_auth_config '
  .interactions.password_verify.request.schema.required = ["interaction", "password"]'

# テスト: username なしで認証 → スキーマ通過（外部 API の挙動次第）
start_auth_flow
curl -s -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/external-api-authentication" \
  -H "Content-Type: application/json" \
  -d '{"interaction": "password_verify", "password": "ExternalPass123!"}'
# → 外部 API に username=null で送信される

restore_auth_config
```

### 3-2. カスタムフィールドの追加

```bash
# device_id を必須に追加
update_auth_config '
  .interactions.password_verify.request.schema.required += ["device_id"] |
  .interactions.password_verify.request.schema.properties.device_id = { "type": "string", "minLength": 1 }'

# テスト 1: device_id なし → 400 バリデーションエラー
start_auth_flow
external_api_login "password_verify" "extapi-user@example.com" "ExternalPass123!"
# → 400 invalid_request

# テスト 2: device_id あり → 成功
start_auth_flow
curl -s -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/external-api-authentication" \
  -H "Content-Type: application/json" \
  -d '{"interaction": "password_verify", "username": "extapi-user@example.com", "password": "ExternalPass123!", "device_id": "device-001"}'
# → 200 成功

restore_auth_config
```

**確認ポイント**:

| # | テスト | 期待値 |
|---|--------|--------|
| 1 | 必須フィールドなしで認証 | HTTP 400 |
| 2 | 必須フィールドありで認証 | HTTP 200 |

---

## パターン 4: user_mapping_rules の変更

**ユースケース**: 外部 API のレスポンス構造変更への対応

### 4-1. provider_id の変更

```bash
# provider_id を変更
update_auth_config '
  .interactions.password_verify.user_resolve.user_mapping_rules = [
    { "from": "$.execution_http_request.response_body.user_id", "to": "external_user_id" },
    { "from": "$.execution_http_request.response_body.email", "to": "email" },
    { "from": "$.execution_http_request.response_body.name", "to": "name" },
    { "static_value": "new-provider", "to": "provider_id" }
  ]'

# テスト: 新しい provider_id で認証 → 新しいユーザーとして解決される
start_auth_flow
external_api_login "password_verify" "extapi-user@example.com" "ExternalPass123!"
# → 200 成功（新しい sub が発行される）

complete_auth_flow
show_amr

restore_auth_config
```

**確認ポイント**:

| # | テスト | 期待値 |
|---|--------|--------|
| 1 | provider_id 変更後に認証 | 新しい sub で成功 |

---

## パターン 5: セッション有効期限の変更

**ユースケース**: セキュリティ要件に合わせたセッション時間の調整

### 5-1. セッション有効期限の短縮

```bash
# セッション有効期限を 10 秒に短縮
update_tenant '.session_config.timeout_seconds = 10'

# テスト 1: ログイン直後にセッション確認 → 有効
start_auth_flow
external_api_login "password_verify" "extapi-user@example.com" "ExternalPass123!"
complete_auth_flow
try_prompt_none "直後"
# → session valid

# テスト 2: 11 秒待ってセッション確認 → 期限切れ
sleep 11
try_prompt_none "11秒後"
# → login_required

restore_tenant
```

**確認ポイント**:

| # | テスト | 期待値 |
|---|--------|--------|
| 1 | ログイン直後 | session valid |
| 2 | 11秒後 | login_required |

---

## パターン 6: アクセストークン有効期限の変更

**ユースケース**: 短命トークンへの変更

### 6-1. AT 有効期限の短縮

```bash
# AT 有効期限を 10 秒に短縮
update_auth_server '.extension.access_token_duration = 10'

# テスト 1: ログイン → トークン取得 → UserInfo 成功
start_auth_flow
external_api_login "password_verify" "extapi-user@example.com" "ExternalPass123!"
complete_auth_flow
get_userinfo | jq '{sub, email}'
# → 200 成功

# テスト 2: 11 秒待って UserInfo → 失敗（トークン期限切れ）
sleep 11
get_userinfo
# → 401 エラー

restore_auth_server
```

**確認ポイント**:

| # | テスト | 期待値 |
|---|--------|--------|
| 1 | AT 取得直後に UserInfo | HTTP 200 |
| 2 | AT 期限切れ後に UserInfo | HTTP 401 |

---

## パターン 7: previous_interaction（Challenge-Response）

**ユースケース**: 2ステップの外部API連携（OTP送信→検証など）

### 7-1. challenge → verify のデータ受け渡し

```bash
# challenge と verify の2つの interaction を追加
update_auth_config '
  .interactions.challenge = {
    "execution": {
      "function": "http_request",
      "http_request": {
        "url": "http://host.docker.internal:4000/e2e/error-responses",
        "method": "POST",
        "header_mapping_rules": [{"static_value": "application/json", "to": "Content-Type"}],
        "body_mapping_rules": [{"from": "$.request_body", "to": "*"}]
      },
      "http_request_store": {
        "key": "challenge",
        "interaction_mapping_rules": [
          {"from": "$.response_body.application_id", "to": "transaction_id"}
        ]
      }
    },
    "response": {
      "body_mapping_rules": [
        {"from": "$.execution_http_request.response_body.application_id", "to": "transaction_id"}
      ]
    }
  } |
  .interactions.verify = {
    "execution": {
      "function": "http_request",
      "previous_interaction": {"key": "challenge"},
      "http_request": {
        "url": "http://host.docker.internal:4000/e2e/error-responses",
        "method": "POST",
        "header_mapping_rules": [{"static_value": "application/json", "to": "Content-Type"}],
        "body_mapping_rules": [
          {"from": "$.interaction.transaction_id", "to": "transaction_id"},
          {"from": "$.request_body.code", "to": "verification_code"}
        ]
      }
    },
    "response": {
      "body_mapping_rules": [
        {"from": "$.execution_http_request.response_body", "to": "*"}
      ]
    }
  }'

# テスト: challenge → verify の流れ
start_auth_flow
external_api_login "password_verify" "extapi-user@example.com" "ExternalPass123!" > /dev/null 2>&1

# Step 1: challenge → transaction_id を取得
echo "--- challenge ---"
curl -s -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/external-api-authentication" \
  -H "Content-Type: application/json" \
  -d '{"interaction": "challenge"}' | jq '{interaction, transaction_id}'

# Step 2: verify → previous_interaction で transaction_id が自動注入される
echo "--- verify ---"
curl -s -b "${COOKIE_JAR}" -c "${COOKIE_JAR}" \
  -X POST "${TENANT_BASE}/v1/authorizations/${AUTHORIZATION_ID}/external-api-authentication" \
  -H "Content-Type: application/json" \
  -d '{"interaction": "verify", "code": "123456"}' | jq '{interaction, status}'

restore_auth_config
```

**確認ポイント**:

| # | テスト | 期待値 |
|---|--------|--------|
| 1 | challenge で transaction_id を取得 | JSON に transaction_id が含まれる |
| 2 | verify で previous_interaction 経由でデータ注入 | HTTP 200（transaction_id が自動注入） |

---

## パターン 8: identity_match_field（MFA 2段階目）

**ユースケース**: MFA 2段階目で外部APIのユーザーと1段階目のユーザーの一致検証

### 8-1. identity_match_field の効果を確認

この検証には認証ポリシーの MFA 構成（password + external-api）が必要です。

```bash
# 1. パスワード認証設定を追加（initial-registration 含む）
INITIAL_REG_ID=$(jq -r '.id' "${CONFIG_DIR}/authentication-config-initial-registration.json")
PASSWORD_CONFIG_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')

curl -s -w "\n%{http_code}" -X POST \
  "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}/authentication-configurations" \
  -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{
    \"id\": \"${PASSWORD_CONFIG_ID}\",
    \"type\": \"password\",
    \"attributes\": {},
    \"metadata\": {},
    \"interactions\": {
      \"password-authentication\": {
        \"request\": {
          \"schema\": {
            \"type\": \"object\",
            \"required\": [\"username\", \"password\"],
            \"properties\": {
              \"username\": {\"type\": \"string\"},
              \"password\": {\"type\": \"string\"}
            }
          }
        },
        \"execution\": {\"function\": \"password_verification\"},
        \"response\": {\"body_mapping_rules\": []}
      }
    }
  }" | tail -1
# → 201

# 2. 認証ポリシーを MFA に変更（password=1st, external-api=2nd）
update_auth_policy '{
  "flow": "oauth",
  "enabled": true,
  "policies": [{
    "description": "mfa_password_external_api",
    "priority": 1,
    "available_methods": ["password", "external-api", "initial-registration"],
    "step_definitions": [
      {"method": "password", "order": 1, "requires_user": false, "user_identity_source": "username"},
      {"method": "external-api", "order": 2, "requires_user": true}
    ],
    "success_conditions": {
      "any_of": [
        [
          {"path": "$.password-authentication.success_count", "type": "integer", "operation": "gte", "value": 1},
          {"path": "$.external-api-authentication.success_count", "type": "integer", "operation": "gte", "value": 1}
        ],
        [{"path": "$.initial-registration.success_count", "type": "integer", "operation": "gte", "value": 1}]
      ]
    }
  }]
}'

# 3. テスト: 同じユーザーで 2nd factor → 成功
start_auth_flow
password_login "extapi-user@example.com" "ExternalPass123!" > /dev/null 2>&1
echo -n "8-1a 同じユーザー: "
external_api_login "password_verify" "extapi-user@example.com" "ExternalPass123!" 2>/dev/null | jq -r '.interaction // .error'

# 4. テスト: 別ユーザーで 2nd factor → user_identity_mismatch
start_auth_flow
password_login "extapi-user@example.com" "ExternalPass123!" > /dev/null 2>&1
echo -n "8-1b 別ユーザー: "
external_api_login "password_verify" "attacker@evil.com" "ExternalPass123!" 2>/dev/null | jq -r '.error'

# 5. identity_match_field を削除して比較スキップ確認
update_auth_config '.interactions.password_verify.user_resolve.identity_match_field = null' > /dev/null

start_auth_flow
password_login "extapi-user@example.com" "ExternalPass123!" > /dev/null 2>&1
echo -n "8-1c identity_match_field なし + 別ユーザー: "
external_api_login "password_verify" "attacker@evil.com" "ExternalPass123!" 2>/dev/null | jq -r '.interaction // .error'
# → password_verify（比較スキップで成功、ただし sub はユーザーA のまま）

# 6. 復元
restore_auth_config
restore_auth_policy
```

**確認ポイント**:

| # | テスト | 期待値 |
|---|--------|--------|
| 1 | 同じユーザーで 2nd factor | 成功（`password_verify`） |
| 2 | 別ユーザーで 2nd factor | `user_identity_mismatch` |
| 3 | identity_match_field なし + 別ユーザー | 成功（比較スキップ、sub はユーザーA） |

---

## パターン 9: response.body_mapping_rules の変更

**ユースケース**: クライアントに返すレスポンスのカスタマイズ

### 9-1. レスポンスフィールドの追加・変更

```bash
# phone_number をレスポンスに追加
update_auth_config '
  .interactions.password_verify.response.body_mapping_rules += [
    {"from": "$.execution_http_request.response_body.phone_number", "to": "phone"},
    {"from": "$.execution_http_request.response_body.member_id", "to": "member_id"}
  ]'

# テスト: 追加フィールドがレスポンスに含まれる
start_auth_flow
external_api_login "password_verify" "extapi-user@example.com" "ExternalPass123!" 2>/dev/null | jq '{interaction, email, phone, member_id}'

restore_auth_config
```

### 9-2. レスポンスを最小限に絞る

```bash
# レスポンスを interaction のみに絞る
update_auth_config '
  .interactions.password_verify.response.body_mapping_rules = []'

# テスト: マッピングなし → 実行結果がそのまま返る
start_auth_flow
external_api_login "password_verify" "extapi-user@example.com" "ExternalPass123!" 2>/dev/null | jq 'keys'

restore_auth_config
```

**確認ポイント**:

| # | テスト | 期待値 |
|---|--------|--------|
| 1 | phone, member_id がレスポンスに追加される | JSON に含まれる |
| 2 | body_mapping_rules を空にする | 実行結果がそのまま返る |

---

## パターン 10: 外部 API が HTTP 200 でエラーを返すケース

**ユースケース**: 外部APIが HTTP ステータスコードではなくボディでエラーを通知するパターン

mock の `/auth/password` は `password=locked` で HTTP 200 + エラーボディを返します:

```json
{
  "error": "account_locked",
  "error_description": "Account is temporarily locked due to too many failed attempts",
  "locked_until": "2026-12-31T23:59:59Z"
}
```

### 10-1. エラーボディの透過確認

```bash
# テスト: password=locked → HTTP 200 だがボディにエラー
start_auth_flow
external_api_login "password_verify" "extapi-user@example.com" "locked" 2>/dev/null | jq .
# → HTTP 200 だが {error: "account_locked", ...} が含まれる
# → user_resolve は失敗する（user_id がないため）
```

### 10-2. response.body_mapping_rules で error を返す

`response.body_mapping_rules` に `condition` を使って、成功/エラーで異なるフィールドを返すことを確認します。

```bash
# 現在の設定を確認（condition 付き mapping が既に設定済み）
# 認証設定テンプレートでは、error フィールドは condition: exists で条件付きマッピング

# テスト 1: 正常パスワード → user_id, email が返る（error は含まれない）
start_auth_flow
external_api_login "password_verify" "extapi-user@example.com" "ExternalPass123!" 2>/dev/null | jq '{email, error}'
# → {"email": "...", "error": null}

# テスト 2: locked パスワード → error が返る（user_id は含まれない）
start_auth_flow
external_api_login "password_verify" "extapi-user@example.com" "locked" 2>/dev/null | jq '{email, error, error_description}'
# → {"email": null, "error": "account_locked", "error_description": "..."}
```

**確認ポイント**:

| # | テスト | 期待値 |
|---|--------|--------|
| 1 | 正常パスワード | email あり、error なし |
| 2 | locked パスワード | error: account_locked、email なし |

---

## チェックリスト

### パターン 1: 外部API URL の変更

| # | 確認項目 | 結果 |
|---|---------|------|
| 1-1 | 到達不能 URL で 503 エラー | |
| 1-2 | URL 復元後に正常認証 | |

### パターン 2: interaction の追加

| # | 確認項目 | 結果 |
|---|---------|------|
| 2-1a | 既存 password_verify が動作 | |
| 2-1b | 新規 risk_check が動作 | |

### パターン 3: JSON Schema バリデーション

| # | 確認項目 | 結果 |
|---|---------|------|
| 3-2a | カスタム必須フィールドなしで 400 | |
| 3-2b | カスタム必須フィールドありで 200 | |

### パターン 4: user_mapping_rules

| # | 確認項目 | 結果 |
|---|---------|------|
| 4-1 | provider_id 変更で新しい sub が発行 | |

### パターン 5: セッション有効期限

| # | 確認項目 | 結果 |
|---|---------|------|
| 5-1a | ログイン直後にセッション有効 | |
| 5-1b | タイムアウト後にセッション期限切れ | |

### パターン 6: AT 有効期限

| # | 確認項目 | 結果 |
|---|---------|------|
| 6-1a | AT 取得直後に UserInfo 成功 | |
| 6-1b | AT 期限切れ後に UserInfo 401 | |

### パターン 7: previous_interaction

| # | 確認項目 | 結果 |
|---|---------|------|
| 7-1a | challenge で transaction_id 取得 | |
| 7-1b | verify で previous_interaction 経由でデータ注入成功 | |

### パターン 8: identity_match_field（MFA）

| # | 確認項目 | 結果 |
|---|---------|------|
| 8-1a | 同じユーザーで 2nd factor 成功 | |
| 8-1b | 別ユーザーで user_identity_mismatch | |
| 8-1c | identity_match_field なしで比較スキップ（成功、sub は変わらない） | |

### パターン 9: response.body_mapping_rules

| # | 確認項目 | 結果 |
|---|---------|------|
| 9-1 | phone, member_id がレスポンスに追加 | |
| 9-2 | body_mapping_rules 空で実行結果がそのまま返る | |

### パターン 10: HTTP 200 + エラーボディ

| # | 確認項目 | 結果 |
|---|---------|------|
| 10-1 | 正常パスワード: email あり、error なし | |
| 10-2 | locked パスワード: error: account_locked、email なし | |
