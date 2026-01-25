# FIDO2/WebAuthn パスキー登録

## このドキュメントの目的

**認可コードフロー内でFIDO2パスキーを安全に登録する**ための設定を行うことが目標です。

### 学べること

- 認可コードフロー内でのFIDO2登録の仕組み
- MFA完了後にのみデバイス登録を許可する設定（推奨）
- FIDO2認証設定とポリシー設定

### 所要時間
⏱️ **約20分**

### 前提条件
- [テナント設定](../../phase-1-foundation/03-tenant-setup.md)が完了していること
- [パスワード認証](../../phase-1-foundation/05-user-registration.md)が設定済みであること
- [認証ポリシー](../../phase-1-foundation/07-authentication-policy.md)の基本を理解していること
- Email認証設定（MFA用）が設定済みであること（`device_registration_conditions`を使用する場合）
  - 設定方法は[MFA設定](../../phase-2-security/01-mfa-setup.md)を参照（SMS→Emailに読み替え）

---

## 認可コードフロー内でのFIDO2登録

### なぜ認可コードフロー内で登録するのか

FIDO2パスキーの登録は、**OAuth 2.0認可コードフロー内**で行います。これにより：

- ユーザーが認証済みであることを保証
- 認証レベル（ACR）に基づくアクセス制御が可能
- セキュリティイベントの追跡が可能

```
┌─────────────────────────────────────────────────────────────┐
│                    認可コードフロー                           │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  GET /v1/authorizations ────────────────────────────────┐   │
│       ↓                                                 │   │
│  POST /authorizations/{id}/password-authentication      │   │
│       ↓                                                 │   │
│  POST /authorizations/{id}/email-authentication (MFA)   │   │
│       ↓                                                 │   │
│  ┌─────────────────────────────────────────────────┐    │   │
│  │  FIDO2登録（MFA完了後のみ許可）                   │    │   │
│  │  POST /authorizations/{id}/fido2-registration-* │    │   │
│  └─────────────────────────────────────────────────┘    │   │
│       ↓                                                 │   │
│  POST /authorizations/{id}/authorize ───────────────────┘   │
│       ↓                                                     │
│  認可コード発行 → トークン取得                               │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 推奨設定: MFA完了後のデバイス登録

### セキュリティ上の推奨事項

**パスワードのみの認証でFIDO2デバイスを登録させることは推奨しません。**

理由：
- パスワードが漏洩した場合、攻撃者が自分のデバイスを登録できてしまう
- 一度登録されると、攻撃者はパスワードなしでログイン可能になる

**推奨**: `device_registration_conditions`を設定し、MFA完了後のみデバイス登録を許可する

### フロー比較

**❌ 非推奨: パスワードのみでデバイス登録可能**
```
パスワード認証 → FIDO2登録可能 → 危険！
```

**✅ 推奨: MFA完了後のみデバイス登録可能**
```
パスワード認証 → FIDO2登録拒否（ACR不足）
       ↓
Email MFA完了 → FIDO2登録許可
```

---

# Part 1: 事前準備

## Step 1: FIDO2認証設定の作成

idp-serverは**内蔵のWebAuthn4jライブラリ**を使用してFIDO2認証を処理します。外部のFIDO2サーバーは不要です。

```
┌─────────────────────────────────────────────────────────────┐
│  idp-server                                                 │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  WebAuthn4j（内蔵ライブラリ）                          │  │
│  │  - チャレンジ生成                                      │  │
│  │  - アテステーション検証                                │  │
│  │  - アサーション検証                                    │  │
│  │  - 公開鍵・クレデンシャル管理                          │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

以下の設定で使用する`webauthn4j_*`関数は、WebAuthn4jライブラリを直接呼び出します。

```bash
FIDO2_AUTH_CONFIG_ID=$(uuidgen)

curl -X POST "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/${TENANT_ID}/authentication-configurations" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d "{
    \"id\": \"${FIDO2_AUTH_CONFIG_ID}\",
    \"type\": \"fido2\",
    \"attributes\": {},
    \"metadata\": {},
    \"interactions\": {
      \"fido2-registration-challenge\": {
        \"execution\": {
          \"function\": \"webauthn4j_registration_challenge\",
          \"details\": {
            \"rp_id\": \"example.com\",
            \"rp_name\": \"Example Service\",
            \"origin\": \"https://example.com\",
            \"attestation_preference\": \"none\",
            \"authenticator_attachment\": \"platform\",
            \"user_verification_required\": true,
            \"require_resident_key\": true
          }
        },
        \"response\": {
          \"body_mapping_rules\": [
            { \"from\": \"\$.execution_webauthn4j\", \"to\": \"*\" }
          ]
        }
      },
      \"fido2-registration\": {
        \"execution\": {
          \"function\": \"webauthn4j_registration\",
          \"details\": {
            \"rp_id\": \"example.com\",
            \"origin\": \"https://example.com\"
          }
        },
        \"response\": {
          \"body_mapping_rules\": [
            { \"from\": \"\$.execution_webauthn4j\", \"to\": \"*\" }
          ]
        }
      },
      \"fido2-authentication-challenge\": {
        \"execution\": {
          \"function\": \"webauthn4j_authentication_challenge\",
          \"details\": {
            \"rp_id\": \"example.com\",
            \"origin\": \"https://example.com\"
          }
        },
        \"response\": {
          \"body_mapping_rules\": [
            { \"from\": \"\$.execution_webauthn4j\", \"to\": \"*\" }
          ]
        }
      },
      \"fido2-authentication\": {
        \"execution\": {
          \"function\": \"webauthn4j_authentication\",
          \"details\": {
            \"rp_id\": \"example.com\",
            \"origin\": \"https://example.com\"
          }
        },
        \"response\": {
          \"body_mapping_rules\": [
            { \"from\": \"\$.execution_webauthn4j\", \"to\": \"*\" }
          ]
        }
      }
    }
  }"
```

### WebAuthn4j関数

| 関数名 | 説明 |
|:---|:---|
| `webauthn4j_registration_challenge` | 登録用チャレンジ生成 |
| `webauthn4j_registration` | アテステーション検証・クレデンシャル登録 |
| `webauthn4j_authentication_challenge` | 認証用チャレンジ生成 |
| `webauthn4j_authentication` | アサーション検証・署名検証 |

### 設定パラメータ（details）

| パラメータ | 説明 | 推奨値 |
|:---|:---|:---|
| `rp_id` | Relying Party ID（ドメイン） | サービスのドメイン |
| `rp_name` | サービス名（認証器に表示） | サービス名 |
| `origin` | 許可するオリジン | `https://example.com` |
| `attestation_preference` | アテステーション要求 | `none` / `direct` |
| `authenticator_attachment` | 認証器タイプ | `platform` / `cross-platform` |
| `user_verification_required` | ユーザー検証必須 | `true`（推奨） |
| `require_resident_key` | Discoverable Credential | `true`（パスキー用） |

---

## Step 2: 認証ポリシーの作成

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
        \"description\": \"MFA required for device registration\",
        \"priority\": 10,
        \"conditions\": {
          \"scopes\": [\"openid\"]
        },
        \"available_methods\": [\"password\", \"email\", \"fido2\"],
        \"success_conditions\": {
          \"any_of\": [
            [
              {
                \"path\": \"\$.password-authentication.success_count\",
                \"type\": \"integer\",
                \"operation\": \"gte\",
                \"value\": 1
              }
            ],
            [
              {
                \"path\": \"\$.fido2-authentication.success_count\",
                \"type\": \"integer\",
                \"operation\": \"gte\",
                \"value\": 1
              }
            ]
          ]
        },
        \"device_registration_conditions\": {
          \"any_of\": [
            [
              {
                \"path\": \"\$.email-authentication.success_count\",
                \"type\": \"integer\",
                \"operation\": \"gte\",
                \"value\": 1
              }
            ],
            [
              {
                \"path\": \"\$.fido2-authentication.success_count\",
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

### ポリシー設定の解説

| 設定項目 | 説明 |
|:---|:---|
| `success_conditions` | ログイン成功に必要な条件（パスワードまたはFIDO2） |
| `device_registration_conditions` | **デバイス登録に必要な追加条件**（Email MFAまたはFIDO2認証） |

**動作**:
- `success_conditions`を満たす → ログイン可能
- `device_registration_conditions`を満たさない → FIDO2登録拒否（`400 forbidden`）
- `device_registration_conditions`を満たす → FIDO2登録許可

---

# Part 2: 認可コードフローの実行

## 全体フロー

```
1. GET  /{tenant}/v1/authorizations?response_type=code&...
   → 302 Redirect with authorization_id

2. POST /{tenant}/v1/authorizations/{id}/password-authentication
   → ログイン成功

3. POST /{tenant}/v1/authorizations/{id}/fido2-registration-challenge
   → 400 forbidden（device_registration_conditions未達成）

4. POST /{tenant}/v1/authorizations/{id}/email-authentication-challenge
   POST /{tenant}/v1/authorizations/{id}/email-authentication
   → MFA完了

5. POST /{tenant}/v1/authorizations/{id}/fido2-registration-challenge
   → 200 OK（チャレンジ取得成功）

6. POST /{tenant}/v1/authorizations/{id}/fido2-registration
   → 200 OK（登録成功）

7. POST /{tenant}/v1/authorizations/{id}/authorize
   → 認可コード発行
```

---

## Step 1: 認可リクエスト

```bash
curl -v "http://localhost:8080/${TENANT_ID}/v1/authorizations?\
response_type=code&\
client_id=${CLIENT_ID}&\
redirect_uri=${REDIRECT_URI}&\
scope=openid+profile&\
state=random-state"
```

**レスポンス**:
```
HTTP/1.1 302 Found
Location: http://localhost:8080/authorize?id=auth-transaction-id-xxxxx
```

`id` パラメータを取得します。

---

## Step 2: パスワード認証

```bash
AUTH_ID="auth-transaction-id-xxxxx"

curl -X POST "http://localhost:8080/${TENANT_ID}/v1/authorizations/${AUTH_ID}/password-authentication" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "user@example.com",
    "password": "Test1234!"
  }'
```

**成功レスポンス**:
```json
{
  "status": "success"
}
```

---

## Step 3: FIDO2登録チャレンジ（MFA前 - 失敗例）

```bash
curl -X POST "http://localhost:8080/${TENANT_ID}/v1/authorizations/${AUTH_ID}/fido2-registration-challenge" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "user@example.com",
    "displayName": "User Name"
  }'
```

**レスポンス（device_registration_conditions未達成）**:
```json
{
  "error": "forbidden",
  "error_description": "Current authentication level does not meet device registration requirements"
}
```

→ MFAを完了する必要があります。

---

## Step 4: Email MFA

### 4.1 Email OTP送信リクエスト

```bash
curl -X POST "http://localhost:8080/${TENANT_ID}/v1/authorizations/${AUTH_ID}/email-authentication-challenge" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com"
  }'
```

**成功レスポンス**:
```json
{
  "transaction_id": "email-transaction-xxxxx",
  "expires_in": 300
}
```

### 4.2 Email OTP検証

```bash
curl -X POST "http://localhost:8080/${TENANT_ID}/v1/authorizations/${AUTH_ID}/email-authentication" \
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

---

## Step 5: FIDO2登録チャレンジ（MFA後 - 成功）

```bash
curl -X POST "http://localhost:8080/${TENANT_ID}/v1/authorizations/${AUTH_ID}/fido2-registration-challenge" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "user@example.com",
    "displayName": "User Name"
  }'
```

**レスポンス（成功）**:
```json
{
  "challenge": "base64url-encoded-challenge",
  "rp": { "id": "example.com", "name": "Example Service" },
  "user": { "id": "...", "name": "user@example.com", "displayName": "User Name" },
  "pubKeyCredParams": [{ "type": "public-key", "alg": -7 }],
  "authenticatorSelection": {
    "authenticatorAttachment": "platform",
    "userVerification": "required",
    "residentKey": "required"
  }
}
```

このレスポンスをフロントエンドの `navigator.credentials.create()` に渡します。

---

## Step 6: FIDO2登録完了

フロントエンドで `navigator.credentials.create()` を実行した結果をサーバーに送信します。

```bash
curl -X POST "http://localhost:8080/${TENANT_ID}/v1/authorizations/${AUTH_ID}/fido2-registration" \
  -H "Content-Type: application/json" \
  -d '{
    "id": "credential-id",
    "rawId": "base64url-encoded",
    "type": "public-key",
    "response": {
      "clientDataJSON": "base64url-encoded",
      "attestationObject": "base64url-encoded",
      "transports": ["internal", "hybrid"]
    }
  }'
```

**成功レスポンス**:
```json
{
  "status": "ok"
}
```

---

## Step 7: 認可完了

```bash
curl -X POST "http://localhost:8080/${TENANT_ID}/v1/authorizations/${AUTH_ID}/authorize" \
  -H "Content-Type: application/json" \
  -d '{}'
```

**成功レスポンス**:
```json
{
  "redirect_uri": "https://app.example.com/callback?code=abc123&state=random-state"
}
```

FIDO2パスキーの登録が完了しました。次回以降は[パスキー認証](./02-authentication.md)でログインできます。

---

## セキュリティイベント

FIDO2登録の成功・失敗はセキュリティイベントとして記録されます。

| イベントタイプ | 説明 |
|:---|:---|
| `fido2_registration_challenge_success` | チャレンジ取得成功 |
| `fido2_registration_challenge_failure` | チャレンジ取得失敗（ACR不足等） |
| `fido2_registration_success` | 登録成功 |
| `fido2_registration_failure` | 登録失敗 |

---

## 関連ドキュメント

- [パスキー認証](./02-authentication.md) - 登録後の認証フロー
- [パスキー管理](./03-management.md) - 一覧・削除API
- [アテステーション検証](./04-attestation-verification.md) - 認証器の信頼性検証
- [認証ポリシー](../../phase-1-foundation/07-authentication-policy.md) - ポリシー設定の詳細
- [パスワードレス認証](../../../content_03_concepts/03-authentication-authorization/concept-07-passwordless.md) - 概念説明

---

**最終更新**: 2025-01-25
