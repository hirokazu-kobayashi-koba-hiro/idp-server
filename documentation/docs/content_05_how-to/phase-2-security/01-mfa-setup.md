# MFA（多要素認証）の設定

## このドキュメントの目的

**SMS OTPによるMFA（多要素認証）** を設定し、パスワード認証に追加することが目標です。

### 所要時間
⏱️ **約20分**

### 前提条件
- [パスワード認証](../phase-1-foundation/05-user-registration.md)が設定済み
- 管理者トークンを取得済み
- 組織ID（organization-id）を取得済み
- 外部SMS送信サービス（モックまたは実サービス）が稼働中

### Management API URL

**組織レベルAPI**（このドキュメントでの表記）:
```
POST /v1/management/organizations/{organization-id}/tenants/{tenant-id}/authentication-configurations
POST /v1/management/organizations/{organization-id}/tenants/{tenant-id}/authentication-policies
```

**注意**: システムレベルAPIとの違い
- **組織レベル**: `POST /v1/management/organizations/{organization-id}/tenants/{tenant-id}/...` ← このドキュメント
- **システムレベル**: `POST /v1/management/tenants/{tenant-id}/...` ← 管理者のみ

通常の運用では組織レベルAPIを使用してください。

---

## MFA（多要素認証）とは

**2つ以上の認証要素**を組み合わせてセキュリティを強化する仕組みです。

```
第1要素: パスワード（知識）
  ↓ 成功
第2要素: SMS OTP（所持）
  ↓ 成功
認証完了 → Authorization Code発行
```

**メリット**:
- ✅ セキュリティ向上（パスワード漏洩だけでは突破できない）
- ✅ 不正ログイン防止

---

## Step 1: SMS OTP認証設定を作成

### 外部SMS送信サービスとの連携設定

SMS認証では、外部APIを呼び出してOTPを送信します。このステップでは、外部サービスとの連携を設定します。

```bash
SMS_AUTH_CONFIG_ID=$(uuidgen)

curl -X POST "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/${TENANT_ID}/authentication-configurations" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d "{
    \"id\": \"${SMS_AUTH_CONFIG_ID}\",
    \"type\": \"sms\",
    \"attributes\": {},
    \"metadata\": {
      \"type\": \"external\",
      \"description\": \"SMS authentication for MFA\",
      \"transaction_id_param\": \"transaction_id\",
      \"verification_code_param\": \"verification_code\",
      \"retry_count_limitation\": 5,
      \"expire_seconds\": 300
    },
    \"interactions\": {
      \"sms-authentication-challenge\": {
        \"request\": {
          \"schema\": {
            \"type\": \"object\",
            \"properties\": {
              \"phone_number\": { \"type\": \"string\" },
              \"template\": { \"type\": \"string\" }
            }
          }
        },
        \"pre_hook\": {},
        \"execution\": {
          \"function\": \"http_request\",
          \"http_request\": {
            \"url\": \"http://host.docker.internal:4000/sms-authentication-challenge\",
            \"method\": \"POST\",
            \"oauth_authorization\": {
              \"type\": \"password\",
              \"token_endpoint\": \"http://host.docker.internal:4000/token\",
              \"client_id\": \"your-client-id\",
              \"username\": \"username\",
              \"password\": \"password\",
              \"scope\": \"application\"
            },
            \"header_mapping_rules\": [
              { \"static_value\": \"application/json\", \"to\": \"Content-Type\" }
            ],
            \"body_mapping_rules\": [
              { \"from\": \"$.request_body\", \"to\": \"*\" }
            ]
          },
          \"http_request_store\": {
            \"key\": \"sms-authentication-challenge\",
            \"interaction_mapping_rules\": [
              { \"from\": \"$.response_body.transaction_id\", \"to\": \"transaction_id\" }
            ]
          }
        },
        \"post_hook\": {},
        \"response\": {
          \"body_mapping_rules\": [
            { \"from\": \"$.execution_http_request.response_body\", \"to\": \"*\" }
          ]
        }
      },
      \"sms-authentication\": {
        \"request\": {
          \"schema\": {
            \"type\": \"object\",
            \"properties\": {
              \"verification_code\": { \"type\": \"string\" }
            }
          }
        },
        \"pre_hook\": {},
        \"execution\": {
          \"function\": \"http_request\",
          \"previous_interaction\": {
            \"key\": \"sms-authentication-challenge\"
          },
          \"http_request\": {
            \"url\": \"http://host.docker.internal:4000/sms-authentication\",
            \"method\": \"POST\",
            \"oauth_authorization\": {
              \"type\": \"password\",
              \"token_endpoint\": \"http://host.docker.internal:4000/token\",
              \"client_id\": \"your-client-id\",
              \"username\": \"username\",
              \"password\": \"password\",
              \"scope\": \"application\"
            },
            \"header_mapping_rules\": [
              { \"static_value\": \"application/json\", \"to\": \"Content-Type\" }
            ],
            \"body_mapping_rules\": [
              { \"from\": \"$.request_body\", \"to\": \"*\" }
            ]
          }
        },
        \"post_hook\": {},
        \"response\": {
          \"body_mapping_rules\": [
            { \"from\": \"$.execution_http_request.response_body\", \"to\": \"*\" }
          ]
        }
      }
    }
  }"
```

### レスポンス

```json
{
  "dry_run": false,
  "result": {
    "id": "sms-auth-config-id",
    "type": "sms",
    "enabled": true,
    "attributes": {},
    "metadata": {
      "type": "external",
      "description": "SMS authentication for MFA",
      "expire_seconds": 300,
      "retry_count_limitation": 5
    },
    "interactions": {
      "sms-authentication-challenge": { ... },
      "sms-authentication": { ... }
    }
  }
}
```

**設定内容**:
- ✅ 外部SMS送信API（`http://host.docker.internal:4000`）との連携
- ✅ OTP有効期限5分（300秒）
- ✅ リトライ回数上限5回
- ✅ チャレンジ（SMS送信）と認証（OTP検証）の2ステップ

**重要ポイント**:
- `interactions`: 認証フローの各ステップを定義
  - `sms-authentication-challenge`: SMS送信（チャレンジ）
  - `sms-authentication`: OTP検証
- `http_request`: 外部APIへのHTTPリクエスト設定
- `http_request_store`: 外部APIレスポンスの保存（transaction_id等）

---

## Step 2: 認証ポリシーをMFA対応に更新

新規登録とログインの両方で、MFA（SMS OTP）を要求する設定を作成します。

```bash
AUTH_POLICY_ID=$(uuidgen)

curl -X POST "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/${TENANT_ID}/authentication-policies" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d "{
    \"id\": \"${AUTH_POLICY_ID}\",
    \"flow\": \"oauth\",
    \"enabled\": true,
    \"policies\": [
      {
        \"description\": \"mfa_required_for_registration_and_login\",
        \"priority\": 10,
        \"conditions\": {
          \"scopes\": [\"openid\"]
        },
        \"available_methods\": [\"password\", \"initial-registration\", \"sms\"],
        \"success_conditions\": {
          \"any_of\": [
            [
              {
                \"path\": \"$.password-authentication.success_count\",
                \"type\": \"integer\",
                \"operation\": \"gte\",
                \"value\": 1
              },
              {
                \"path\": \"$.sms-authentication.success_count\",
                \"type\": \"integer\",
                \"operation\": \"gte\",
                \"value\": 1
              }
            ],
            [
              {
                \"path\": \"$.initial-registration.success_count\",
                \"type\": \"integer\",
                \"operation\": \"gte\",
                \"value\": 1
              },
              {
                \"path\": \"$.sms-authentication.success_count\",
                \"type\": \"integer\",
                \"operation\": \"gte\",
                \"value\": 1
              }
            ]
          ]
        }
      }
    ]
  }"
```

**重要な構造**:
- `policies`: ポリシーの配列（複数のポリシーを優先度順に定義可能）
- `priority`: ポリシーの優先度（数値が大きいほど優先）
- `conditions`: ポリシー適用条件（スコープ、クライアントID、ACR値等）
- `available_methods`: 利用可能な認証方式
  - `password`: パスワード認証（ログイン時）
  - `initial-registration`: 新規ユーザー登録
  - `sms`: SMS OTP認証（2要素目）
- `success_conditions.any_of`: 認証成功条件
  - **内側配列の複数条件 = AND条件**（すべて満たす必要）
  - **外側配列の複数グループ = OR条件**（いずれか1つでOK）

**このポリシーの意味**:
```
[(password-authentication AND sms-authentication) >= 1]
  OR
[(initial-registration AND sms-authentication) >= 1]

→ ログイン時: パスワード認証 + SMS認証（MFA）
→ 登録時: ユーザー登録 + SMS認証（MFA）
```

**重要なポイント**:
- ✅ ログインでも登録でも、SMS認証（2要素目）が必須
- ✅ `any_of` の外側配列で2パターンを定義（ログイン OR 登録）
- ✅ 各パターン内でAND条件（1要素目 AND 2要素目）

---

## Step 3: 新規ユーザー登録時のMFAフロー

新規ユーザー登録時も、MFAポリシーが適用されます。登録フローでは、`initial-registration`（1要素目）の後に、SMS OTP認証（2要素目）が必要です。

### 3.1 Authorization Request（認可開始）

```bash
curl -v "http://localhost:8080/${TENANT_ID}/v1/authorizations?\
response_type=code&\
client_id=${CLIENT_ID}&\
redirect_uri=${REDIRECT_URI}&\
scope=openid+profile&\
state=random-state-registration"
```

**レスポンス**:
```
HTTP/1.1 302 Found
Location: http://localhost:8080/authorize?id=auth-transaction-id-xxxxx
```

`id` パラメータを取得します。

### 3.2 第1要素: Initial Registration（電話番号含む）

```bash
AUTH_ID="auth-transaction-id-xxxxx"

curl -X POST "http://localhost:8080/${TENANT_ID}/v1/authorizations/${AUTH_ID}/initial-registration" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "new-user@example.com",
    "password": "Test1234!",
    "name": "New Test User",
    "phone_number": "+81-90-1234-5678"
  }'
```

**電話番号形式**:
- 外部SMS送信サービスの仕様に依存します
- 一般的にはE.164形式（`+81-90-1234-5678`）が推奨されます
- 利用するサービスのドキュメントを確認してください

**成功レスポンス**:
```json
{
  "status": "success"
}
```

**重要**: この時点ではまだ登録完了していません。SMS OTP認証が必要です。

### 3.3 第2要素: SMS OTP送信リクエスト（登録用）

```bash
curl -X POST "http://localhost:8080/${TENANT_ID}/v1/authorizations/${AUTH_ID}/sms-authentication-challenge" \
  -H "Content-Type: application/json" \
  -d '{
    "phone_number": "+81-90-1234-5678",
    "template": "registration"
  }'
```

**`template` パラメータ**:
- `"registration"`: ユーザー登録時のSMS（新規ユーザー向けメッセージ）
- `"authentication"`: ログイン時のSMS（既存ユーザー向けメッセージ）

**成功レスポンス**:
```json
{
  "transaction_id": "sms-transaction-xxxxx",
  "verification_code": "123456",
  "expires_in": 300
}
```

**実際の動作**:
- 新規ユーザーの電話にSMSが届く（登録用テンプレート）
- 開発環境ではレスポンスに `verification_code` が含まれる（テスト用）

### 3.4 第2要素: SMS OTP検証

```bash
curl -X POST "http://localhost:8080/${TENANT_ID}/v1/authorizations/${AUTH_ID}/sms-authentication" \
  -H "Content-Type: application/json" \
  -d '{
    "verification_code": "123456"
  }'
```

**成功レスポンス**:
```json
{
  "status": "success"
}
```

### 3.5 認可完了（ユーザー登録完了）

```bash
curl -X POST "http://localhost:8080/${TENANT_ID}/v1/authorizations/${AUTH_ID}/authorize" \
  -H "Content-Type: application/json" \
  -d '{}'
```

**成功レスポンス**:
```json
{
  "redirect_uri": "https://app.example.com/callback?code=abc123&state=random-state-registration"
}
```

**ユーザー登録完了！** MFAを完了した状態でauthorization codeが発行されます。

---

## Step 4: 既存ユーザーログイン時のMFAフロー

既存ユーザーがログインする場合、パスワード認証（1要素目）の後に、SMS OTP認証（2要素目）が必要です。

### 4.1 Authorization Request（認可開始）

```bash
curl -v "http://localhost:8080/${TENANT_ID}/v1/authorizations?\
response_type=code&\
client_id=${CLIENT_ID}&\
redirect_uri=${REDIRECT_URI}&\
scope=openid+profile&\
state=random-state-login"
```

**レスポンス**:
```
HTTP/1.1 302 Found
Location: http://localhost:8080/authorize?id=auth-transaction-id-yyyyy
```

### 4.2 第1要素: パスワード認証

```bash
AUTH_ID="auth-transaction-id-yyyyy"

curl -X POST "http://localhost:8080/${TENANT_ID}/v1/authorizations/${AUTH_ID}/password-authentication" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "new-user@example.com",
    "password": "Test1234!"
  }'
```

**成功レスポンス**:
```json
{
  "status": "success"
}
```

**重要**: この時点ではまだ認証完了していません。SMS OTP認証が必要です。

### 4.3 第2要素: SMS OTP送信リクエスト（ログイン用）

```bash
curl -X POST "http://localhost:8080/${TENANT_ID}/v1/authorizations/${AUTH_ID}/sms-authentication-challenge" \
  -H "Content-Type: application/json" \
  -d '{
    "phone_number": "+81-90-1234-5678",
    "template": "authentication"
  }'
```

**成功レスポンス**:
```json
{
  "transaction_id": "sms-transaction-yyyyy",
  "verification_code": "654321",
  "expires_in": 300
}
```

**実際の動作**:
- ユーザーの電話にSMSが届く（ログイン用テンプレート）
- 本番環境では `verification_code` は含まれず、SMSでのみ確認可能

### 4.4 第2要素: SMS OTP検証

```bash
curl -X POST "http://localhost:8080/${TENANT_ID}/v1/authorizations/${AUTH_ID}/sms-authentication" \
  -H "Content-Type: application/json" \
  -d '{
    "verification_code": "654321"
  }'
```

**成功レスポンス**:
```json
{
  "status": "success"
}
```

### 4.5 認可完了

```bash
curl -X POST "http://localhost:8080/${TENANT_ID}/v1/authorizations/${AUTH_ID}/authorize" \
  -H "Content-Type: application/json" \
  -d '{}'
```

**成功レスポンス**:
```json
{
  "redirect_uri": "https://app.example.com/callback?code=def456&state=random-state-login"
}
```

**MFA完了！** リダイレクトURIにauthorization codeが含まれています。

---

## MFA設定のバリエーション

### パターン1: 登録＆ログイン両方でMFA（このガイド）

```json
{
  "policies": [
    {
      "description": "mfa_required_for_registration_and_login",
      "priority": 10,
      "conditions": { "scopes": ["openid"] },
      "available_methods": ["password", "initial-registration", "sms"],
      "success_conditions": {
        "any_of": [
          [
            { "path": "$.password-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 },
            { "path": "$.sms-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }
          ],
          [
            { "path": "$.initial-registration.success_count", "type": "integer", "operation": "gte", "value": 1 },
            { "path": "$.sms-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }
          ]
        ]
      }
    }
  ]
}
```

**意味**: （パスワード + SMS）**OR**（登録 + SMS）

### パターン2: Email認証でMFA

```json
{
  "policies": [
    {
      "description": "mfa_with_email",
      "priority": 10,
      "conditions": { "scopes": ["openid"] },
      "available_methods": ["password", "initial-registration", "email"],
      "success_conditions": {
        "any_of": [
          [
            { "path": "$.password-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 },
            { "path": "$.email-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }
          ],
          [
            { "path": "$.initial-registration.success_count", "type": "integer", "operation": "gte", "value": 1 },
            { "path": "$.email-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }
          ]
        ]
      }
    }
  ]
}
```

**意味**: （パスワード + Email）**OR**（登録 + Email）

### パターン3: 選択式MFA（SMS または Email）

```json
{
  "policies": [
    {
      "description": "mfa_with_sms_or_email",
      "priority": 10,
      "conditions": { "scopes": ["openid"] },
      "available_methods": ["password", "initial-registration", "sms", "email"],
      "success_conditions": {
        "any_of": [
          [
            { "path": "$.password-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 },
            { "path": "$.sms-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }
          ],
          [
            { "path": "$.password-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 },
            { "path": "$.email-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }
          ],
          [
            { "path": "$.initial-registration.success_count", "type": "integer", "operation": "gte", "value": 1 },
            { "path": "$.sms-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }
          ],
          [
            { "path": "$.initial-registration.success_count", "type": "integer", "operation": "gte", "value": 1 },
            { "path": "$.email-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }
          ]
        ]
      }
    }
  ]
}
```

**意味**: （パスワード + SMS）**OR**（パスワード + Email）**OR**（登録 + SMS）**OR**（登録 + Email）

---

## よくあるエラー

### エラー1: 認証設定が見つからない

**エラー**:
```json
{
  "error": "invalid_request",
  "error_description": "Authentication configuration not found"
}
```

**原因**: Step 1のSMS認証設定が作成されていない

**解決策**: Step 1を実施してSMS認証設定を作成

---

### エラー2: 外部APIへの接続エラー

**エラー**:
```json
{
  "error": "server_error",
  "error_description": "Failed to connect to external authentication service"
}
```

**原因**: 外部SMS送信サービス（`http://host.docker.internal:4000`）が起動していない

**解決策**:
- 開発環境: モックサービスを起動
- 本番環境: 外部サービスのURLと認証情報を確認

---

### エラー3: OTP検証失敗

**エラー**:
```json
{
  "error": "invalid_grant",
  "error_description": "Verification code is invalid or expired"
}
```

**原因**: OTPの入力ミス、または有効期限切れ（5分）

**解決策**:
- SMSを確認して正しいOTPを入力
- 期限切れの場合は `/sms-authentication-challenge` を再実行

---

### エラー4: 電話番号が登録されていない

**エラー**:
```json
{
  "error": "invalid_request",
  "error_description": "User does not have a phone number"
}
```

**原因**: ユーザーに電話番号が設定されていない

**解決策**: Step 3を実施してユーザー登録時に電話番号を設定

---

## SMS送信サービスの設定

### 開発環境（モックサービス）

開発環境では、モックサービスを使用してSMS送信をシミュレートします。

```bash
# モックサービスのレスポンス例
POST http://host.docker.internal:4000/sms-authentication-challenge
→ { "transaction_id": "xxx", "verification_code": "123456" }

POST http://host.docker.internal:4000/sms-authentication
→ { "status": "success" }
```

### 本番環境（実際のSMS送信サービス）

本番環境では、Twilio、AWS SNS、または独自のSMS送信サービスと連携します。

**Step 1の `http_request.url` を変更**:
```json
{
  "http_request": {
    "url": "https://your-sms-service.example.com/send",
    "method": "POST",
    "oauth_authorization": {
      "type": "password",
      "token_endpoint": "https://your-sms-service.example.com/token",
      "client_id": "your-production-client-id",
      "username": "your-username",
      "password": "your-password",
      "scope": "sms:send"
    },
    ...
  }
}
```

---

## 次のステップ

✅ MFA（SMS OTP）を設定できました！

### さらにセキュリティを強化
- [How-to: FIDO2設定](../phase-3-advanced/fido-uaf/02-registration.md) - 生体認証
- [How-to: デバイス登録ポリシー](./02-device-registration-policy.md) - ACRベースのアクセス制御

### セキュリティイベントの確認
```bash
# SMS認証の成功/失敗イベントを確認
curl "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/${TENANT_ID}/security-events?event_type=sms_verification_success" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}"
```

---

**最終更新**: 2025-01-19
**難易度**: ⭐⭐⭐☆☆（中級）
**対象**: MFAを初めて設定する管理者・開発者
