# Financial Grade FAPI Configuration

## 概要

このディレクトリには、**FAPI (Financial-grade API)** 準拠の金融機関向け設定テンプレートが含まれています。

### 特徴

- **FAPI Advanced 準拠**: MTLS、JAR、JARM対応
- **高セキュリティ**: pairwise subject type、strict session設定
- **厳格な認証ポリシー**:
  - 送金・決済操作: WebAuthn/FIDO-UAF必須
  - 参照操作: WebAuthn/FIDO-UAF/SMS/Email認証
- **包括的ログ記録**: すべての認証・認可イベントを記録
- **トレーシング有効**: 監査証跡の完全性

## ファイル構成

```
financial-grade/
├── README.md                           # このファイル
├── setup.sh                            # セットアップスクリプト
├── onboarding-request.json             # 組織・管理テナント初期化設定
├── financial-tenant.json               # 金融業務テナント設定
├── financial-client.json               # 金融アプリケーションクライアント設定
└── authentication-policy/
    └── oauth.json                      # 認証ポリシー設定
```

## 前提条件

- idp-server v0.9.10以降が稼働中
- PostgreSQLデータベース設定済み
- `.env` ファイルに以下の環境変数が設定済み:
  ```bash
  AUTHORIZATION_SERVER_URL=https://localhost:8443
  ADMIN_TENANT_ID=<admin-tenant-id>
  ADMIN_USER_EMAIL=<admin-email>
  ADMIN_USER_PASSWORD=<admin-password>
  ADMIN_CLIENT_ID=<admin-client-id>
  ADMIN_CLIENT_SECRET=<admin-client-secret>
  ```

## セットアップ手順

### 1. クライアント証明書の生成（MTLS用）

金融アプリケーションクライアントは`self_signed_tls_client_auth`認証を使用するため、事前にクライアント証明書を生成します。

```bash
# クライアント証明書生成
./config/scripts/generate-client-certificate.sh \
  -c financial-web-app \
  -o ./config/examples/financial-grade/certs

# 生成されるファイル:
#   - certs/client-cert.pem (証明書)
#   - certs/client-key.pem (秘密鍵)
#   - certs/client-cert.der (DER形式)
#   - certs/client-cert-info.txt (証明書情報)
```

**重要**: 生成された証明書のSHA256フィンガープリントは、クライアント登録時のJWKSと照合されます。

### 2. 自動セットアップ（推奨）

```bash
cd config/examples/financial-grade
./setup.sh
```

このスクリプトは以下を実行します:

1. システム管理者アクセストークン取得
2. 組織＋管理テナント作成（Onboarding API）
3. 組織管理者アクセストークン取得
4. 金融業務テナント作成
5. 金融アプリケーションクライアント作成
6. 認証ポリシー設定

**作成されるリソース:**
```
Organization (f1a2b3c4-d5e6-f7a8-b9c0-d1e2f3a4b5c6)
├── Organizer Tenant (e7f8a9b0-c1d2-e3f4-a5b6-c7d8e9f0a1b2) ← 管理用
│   ├── Admin User (financial-admin@example.com)
│   └── Admin Client (b2c3d4e5-f6a7-b8c9-d0e1-f2a3b4c5d6e7)
│       - Auth: client_secret_post
│       - Scopes: openid profile email management
└── Financial Tenant (c3d4e5f6-a7b8-c9d0-e1f2-a3b4c5d6e7f8) ← 金融アプリ用
    └── Financial Client (d4e5f6a7-b8c9-d0e1-f2a3-b4c5d6e7f8a9)
        - Auth: self_signed_tls_client_auth
        - Scopes: openid profile email account transfers read write
        - FAPI Advanced準拠
```

### 2. 設定更新（update.sh）

既にリソースが存在する場合、設定を更新できます：

```bash
cd config/examples/financial-grade
./update.sh
```

**update.shが自動実行すること:**
1. システム管理者・組織管理者トークン取得
2. Financial Tenant設定更新
3. Authorization Server設定更新（extension含む）
4. Financial Client設定更新（MTLS証明書のJWKS更新）
5. Authentication Policy更新

### 3. リソース削除（delete.sh）

```bash
cd config/examples/financial-grade
./delete.sh
```

**削除順序:**
1. Authentication Policy（Financial Tenant内）
2. Financial Client（Financial Tenant内）
3. Financial Tenant
4. Admin Client（Organizer Tenant内）
5. User（Organizer Tenant内）
6. Organizer Tenant
7. Organization

### 4. 手動セットアップ

#### 2.1 Onboarding API実行

```bash
# システム管理者トークン取得
SYSTEM_TOKEN=$(./config/scripts/get-access-token.sh \
  -u "${ADMIN_USER_EMAIL}" \
  -p "${ADMIN_USER_PASSWORD}" \
  -t "${ADMIN_TENANT_ID}" \
  -e "${AUTHORIZATION_SERVER_URL}" \
  -c "${ADMIN_CLIENT_ID}" \
  -s "${ADMIN_CLIENT_SECRET}")

# Onboarding実行
curl -X POST "${AUTHORIZATION_SERVER_URL}/v1/management/onboarding" \
  -H "Authorization: Bearer ${SYSTEM_TOKEN}" \
  -H "Content-Type: application/json" \
  -d @config/examples/financial-grade/onboarding-request.json
```

#### 2.2 組織管理者トークン取得

```bash
# レスポンスから取得した組織テナントIDを使用
ORG_TENANT_ID="e7f8a9b0-c1d2-e3f4-a5b6-c7d8e9f0a1b2"
ORG_ADMIN_EMAIL="financial-admin@example.com"
ORG_ADMIN_PASSWORD="FinancialAdminSecure123!"
ORG_ADMIN_CLIENT_ID="b2c3d4e5-f6a7-b8c9-d0e1-f2a3b4c5d6e7"

ORG_TOKEN=$(./config/scripts/get-access-token.sh \
  -u "${ORG_ADMIN_EMAIL}" \
  -p "${ORG_ADMIN_PASSWORD}" \
  -t "${ORG_TENANT_ID}" \
  -e "${AUTHORIZATION_SERVER_URL}" \
  -c "${ORG_ADMIN_CLIENT_ID}")
```

#### 2.3 金融業務テナント作成

```bash
ORG_ID="f1a2b3c4-d5e6-f7a8-b9c0-d1e2f3a4b5c6"

./config/scripts/upsert-tenant.sh \
  -f config/examples/financial-grade/financial-tenant.json \
  -o "${ORG_ID}" \
  -b "${AUTHORIZATION_SERVER_URL}" \
  -a "${ORG_TOKEN}"
```

#### 2.4 金融アプリケーションクライアント作成

```bash
FINANCIAL_TENANT_ID="c3d4e5f6-a7b8-c9d0-e1f2-a3b4c5d6e7f8"

./config/scripts/upsert-client.sh \
  -t "${FINANCIAL_TENANT_ID}" \
  -o "${ORG_ID}" \
  -f config/examples/financial-grade/financial-client.json \
  -b "${AUTHORIZATION_SERVER_URL}" \
  -a "${ORG_TOKEN}"
```

#### 2.5 認証ポリシー設定

```bash
./config/scripts/upsert-authentication-policy.sh \
  -t "${FINANCIAL_TENANT_ID}" \
  -o "${ORG_ID}" \
  -f config/examples/financial-grade/authentication-policy/oauth.json \
  -b "${AUTHORIZATION_SERVER_URL}" \
  -a "${ORG_TOKEN}"
```

## 設定内容詳細

### FAPI準拠設定

#### 1. MTLS（相互TLS認証）

```json
{
  "tls_client_certificate_bound_access_tokens": true,
  "token_endpoint_auth_methods_supported": [
    "private_key_jwt",
    "tls_client_auth",
    "self_signed_tls_client_auth"
  ]
}
```

- `client_secret_post`/`client_secret_basic` は使用不可
- アクセストークンはクライアント証明書にバインド

#### 2. JAR（JWT Authorization Request）

```json
{
  "request_object_signing_alg_values_supported": [
    "RS256",
    "ES256",
    "PS256"
  ]
}
```

- 認可リクエストは署名済みJWT必須

#### 3. JARM（JWT Authorization Response Mode）

```json
{
  "response_modes_supported": [
    "query",
    "fragment",
    "jwt"
  ]
}
```

- 認可レスポンスをJWTで署名可能

#### 4. Pairwise Subject Type

```json
{
  "subject_types_supported": [
    "pairwise"
  ]
}
```

- ユーザー識別子をクライアントごとに異なる値に

### FAPI Scopes

```json
{
  "extension": {
    "fapi_baseline_scopes": ["read", "account"],
    "fapi_advance_scopes": ["write", "transfers"],
    "required_identity_verification_scopes": ["transfers"]
  }
}
```

- **FAPI Baseline**: `read`（参照）、`account`（口座情報）
- **FAPI Advance**: `write`（更新）、`transfers`（送金）
- **身元確認必須**: `transfers` スコープは本人確認完了が必要

### 認証ポリシー

#### ポリシー1: 高セキュリティ（送金・決済）

```json
{
  "priority": 1,
  "conditions": {
    "scopes": ["transfers", "write"]
  },
  "available_methods": ["fido2", "fido-uaf"],
  "failure_conditions": {
    "any_of": [
      {
        "path": "$.fido2-authentication.failure_count",
        "operation": "gte",
        "value": 3
      }
    ]
  }
}
```

- **対象スコープ**: `transfers`, `write`
- **認証方法**: WebAuthn/FIDO-UAF **のみ**
- **失敗上限**: 3回で認証失敗
- **ロック**: 5回で アカウントロック

#### ポリシー2: 標準セキュリティ（参照操作）

```json
{
  "priority": 2,
  "conditions": {
    "scopes": ["openid", "read", "account"]
  },
  "available_methods": ["fido2", "fido-uaf", "sms", "email"],
  "failure_conditions": {
    "any_of": [
      {
        "path": "$.fido2-authentication.failure_count",
        "operation": "gte",
        "value": 5
      }
    ]
  }
}
```

- **対象スコープ**: `read`, `account`
- **認証方法**: WebAuthn/FIDO-UAF/SMS/Email
- **失敗上限**: 5回で認証失敗

### セキュリティログ設定

```json
{
  "security_event_log_config": {
    "format": "structured_json",
    "stage": "production",
    "include_user_detail": true,
    "include_trace_context": true,
    "tracing_enabled": true,
    "persistence_enabled": true
  },
  "security_event_user_config": {
    "include_verified_claims": true,
    "include_roles": true,
    "include_permissions": true
  }
}
```

- **ログ形式**: 構造化JSON
- **トレーシング**: 有効（監査証跡追跡可能）
- **ユーザー詳細**: 本人確認情報、ロール、権限を記録
- **永続化**: 有効（長期保存）

## 設定の検証

### verify.shスクリプト

設定が正しく反映されているか確認するスクリプトを実行：

```bash
cd config/examples/financial-grade
./verify.sh
```

このスクリプトは以下を確認します：
- FAPI準拠設定（Auth methods, Request object signing, Subject types）
- FAPI Scopes（Baseline, Advance, Required verification）
- Token設定（Duration, Strict mode）
- Extension設定の完全性

## 動作確認

### 1. FAPI準拠確認

```bash
curl https://localhost:8443/c3d4e5f6-a7b8-c9d0-e1f2-a3b4c5d6e7f8/.well-known/openid-configuration | jq '{
  mtls: .tls_client_certificate_bound_access_tokens,
  auth_methods: .token_endpoint_auth_methods_supported,
  request_object: .request_object_signing_alg_values_supported,
  subject_types: .subject_types_supported
}'
```

**期待される出力**:
```json
{
  "mtls": true,
  "auth_methods": [
    "private_key_jwt",
    "tls_client_auth",
    "self_signed_tls_client_auth"
  ],
  "request_object": [
    "RS256",
    "ES256",
    "PS256"
  ],
  "subject_types": [
    "pairwise"
  ]
}
```

### 2. FAPI Scopes確認

```bash
curl https://localhost:8443/c3d4e5f6-a7b8-c9d0-e1f2-a3b4c5d6e7f8/.well-known/openid-configuration | jq '.extension | {
  fapi_baseline: .fapi_baseline_scopes,
  fapi_advance: .fapi_advance_scopes,
  required_verification: .required_identity_verification_scopes
}'
```

**期待される出力**:
```json
{
  "fapi_baseline": ["read", "account"],
  "fapi_advance": ["write", "transfers"],
  "required_verification": ["transfers"]
}
```

### 3. 認証ポリシー確認

```bash
curl https://localhost:8443/c3d4e5f6-a7b8-c9d0-e1f2-a3b4c5d6e7f8/.well-known/openid-configuration | jq '.extension.authentication_policies[] | {
  description: .description,
  priority: .priority,
  scopes: .conditions.scopes,
  methods: .available_methods
}'
```

## MTLS認証の使用方法

### クライアント証明書での認証

金融アプリケーションクライアント（`financial-client.json`）は`self_signed_tls_client_auth`認証を使用します。

#### 1. 証明書生成

```bash
./config/scripts/generate-client-certificate.sh \
  -c financial-web-app \
  -o ./config/examples/financial-grade/certs
```

生成されるファイル：
- `certs/client-cert.pem` - クライアント証明書
- `certs/client-key.pem` - 秘密鍵
- `certs/client-cert.der` - DER形式証明書
- `certs/client-cert-info.txt` - 証明書情報

#### 2. Token Endpointへのリクエスト（MTLS）

```bash
curl --cert ./config/examples/financial-grade/certs/client-cert.pem \
     --key ./config/examples/financial-grade/certs/client-key.pem \
     -X POST "https://localhost:8443/${TENANT_ID}/v1/tokens" \
     -H "Content-Type: application/x-www-form-urlencoded" \
     --data-urlencode "grant_type=authorization_code" \
     --data-urlencode "code=${AUTHORIZATION_CODE}" \
     --data-urlencode "redirect_uri=http://localhost:3000/callback/" \
     --data-urlencode "client_id=d4e5f6a7-b8c9-d0e1-f2a3-b4c5d6e7f8a9"
```

**重要**: `self_signed_tls_client_auth`では、クライアント証明書の公開鍵がJWKSに登録された公開鍵と一致する必要があります。

#### 3. 証明書バインドされたアクセストークン

`tls_client_certificate_bound_access_tokens: true`により、発行されたアクセストークンはクライアント証明書にバインドされます。

リソースサーバーへのリクエスト時も、**同じクライアント証明書**を使用する必要があります：

```bash
curl --cert ./config/examples/financial-grade/certs/client-cert.pem \
     --key ./config/examples/financial-grade/certs/client-key.pem \
     -H "Authorization: Bearer ${ACCESS_TOKEN}" \
     "https://localhost:8443/${TENANT_ID}/v1/userinfo"
```

証明書が一致しない場合、`401 Unauthorized`エラーが返されます。

## 認証フロー動作確認

### パターン1: 初回ユーザー登録フロー（initial-registration）

新規ユーザーを登録してトークンを取得するフローです。

```bash
TENANT_ID="c3d4e5f6-a7b8-c9d0-e1f2-a3b4c5d6e7f8"
CLIENT_ID="d4e5f6a7-b8c9-d0e1-f2a3-b4c5d6e7f8a9"
REDIRECT_URI="http://localhost:3000/callback/"
CERT_FILE="./certs/client-cert.pem"

# Step 1: 認可リクエストを開始
curl -v -X GET "https://localhost:8443/${TENANT_ID}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=${REDIRECT_URI}&scope=openid%20profile%20email%20account&state=test-state"

# URLから認証トランザクションIDを取得
export AUTH_TX_ID="取得したID"

# Step 2: 証明書エンコード（x-ssl-certヘッダー用）
ENCODED_CERT=$(cat ${CERT_FILE} | awk '{printf "%s%%0A", $0}' | sed 's/%0A$//')

# Step 3: 新規ユーザー登録（initial-registration API）
curl -X POST "https://localhost:8443/${TENANT_ID}/v1/authorizations/${AUTH_TX_ID}/initial-registration" \
  -H "Content-Type: application/json" \
  -H "x-ssl-cert: ${ENCODED_CERT}" \
  -d '{
    "email": "user@financial-institution.example.com",
    "name": "金融ユーザー",
    "phone_number": "090-1234-5678",
    "password": "SecureFinancialPass123!"
  }' | jq

# Step 4: 認可許諾
curl -X POST "https://localhost:8443/${TENANT_ID}/v1/authorizations/${AUTH_TX_ID}/authorize" \
  -H "Content-Type: application/json" \
  -H "x-ssl-cert: ${ENCODED_CERT}" | jq

# レスポンス例
# {
#   "redirect_uri": "http://localhost:3000/callback/?code=AUTHORIZATION_CODE&state=test-state"
# }

# Step 5: 認可コードをトークンに交換（MTLS認証）
AUTH_CODE="取得した認可コード"

curl -X POST "https://localhost:8443/${TENANT_ID}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -H "x-ssl-cert: ${ENCODED_CERT}" \
  --data-urlencode "grant_type=authorization_code" \
  --data-urlencode "code=${AUTH_CODE}" \
  --data-urlencode "redirect_uri=${REDIRECT_URI}" \
  --data-urlencode "client_id=${CLIENT_ID}" | jq

# レスポンス例（証明書バインドされたトークン）
# {
#   "access_token": "eyJhbGc...",
#   "token_type": "Bearer",
#   "expires_in": 900,
#   "refresh_token": "...",
#   "id_token": "eyJraWQ...",
#   "scope": "openid profile email account"
# }
```

### パターン2: 既存ユーザーログインフロー（password-authentication）

既に登録済みのユーザーでログインしてトークンを取得するフローです。

```bash
TENANT_ID="c3d4e5f6-a7b8-c9d0-e1f2-a3b4c5d6e7f8"
CLIENT_ID="d4e5f6a7-b8c9-d0e1-f2a3-b4c5d6e7f8a9"
REDIRECT_URI="http://localhost:3000/callback/"
CERT_FILE="./certs/client-cert.pem"

# Step 1: 認可リクエストを開始
curl -v -X GET "https://localhost:8443/${TENANT_ID}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=${REDIRECT_URI}&scope=openid%20profile%20email%20account%20transfers&state=test-state"

# 認証トランザクションIDを取得
export AUTH_TX_ID="取得したID"

# 証明書エンコード
ENCODED_CERT=$(cat ${CERT_FILE} | awk '{printf "%s%%0A", $0}' | sed 's/%0A$//')

# Step 2: パスワード認証
curl -X POST "https://localhost:8443/${TENANT_ID}/v1/authorizations/${AUTH_TX_ID}/password-authentication" \
  -H "Content-Type: application/json" \
  -H "x-ssl-cert: ${ENCODED_CERT}" \
  -d '{
    "username": "user@financial-institution.example.com",
    "password": "SecureFinancialPass123!"
  }' | jq

# Step 3: 認可許諾
curl -X POST "https://localhost:8443/${TENANT_ID}/v1/authorizations/${AUTH_TX_ID}/authorize" \
  -H "Content-Type: application/json" \
  -H "x-ssl-cert: ${ENCODED_CERT}" | jq

# Step 4: トークン取得（MTLS認証）
AUTH_CODE="取得した認可コード"

curl -X POST "https://localhost:8443/${TENANT_ID}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -H "x-ssl-cert: ${ENCODED_CERT}" \
  --data-urlencode "grant_type=authorization_code" \
  --data-urlencode "code=${AUTH_CODE}" \
  --data-urlencode "redirect_uri=${REDIRECT_URI}" \
  --data-urlencode "client_id=${CLIENT_ID}" | jq
```

### パターン3: 高セキュリティ操作（送金・決済）- WebAuthn必須

`transfers`スコープを要求する場合、WebAuthn/FIDO-UAF認証が必須です（認証ポリシーにより強制）。

```bash
TENANT_ID="c3d4e5f6-a7b8-c9d0-e1f2-a3b4c5d6e7f8"
CLIENT_ID="d4e5f6a7-b8c9-d0e1-f2a3-b4c5d6e7f8a9"
REDIRECT_URI="http://localhost:3000/callback/"
CERT_FILE="./certs/client-cert.pem"

# Step 1: 認可リクエスト（transfersスコープ含む）
curl -v -X GET "https://localhost:8443/${TENANT_ID}/v1/authorizations?response_type=code&client_id=${CLIENT_ID}&redirect_uri=${REDIRECT_URI}&scope=openid%20transfers&state=test-state"

# 認証トランザクションIDを取得
export AUTH_TX_ID="取得したID"

# 証明書エンコード
ENCODED_CERT=$(cat ${CERT_FILE} | awk '{printf "%s%%0A", $0}' | sed 's/%0A$//')

# Step 2: WebAuthn認証が要求される
# 認証ポリシー "financial_high_security_policy_for_transfers" により
# password-authenticationは拒否され、WebAuthn/FIDO-UAFのみ許可

# WebAuthn登録（事前準備）
# curl -X POST "https://localhost:8443/${TENANT_ID}/v1/me/authentication-devices/webauthn/register/start" ...

# WebAuthn認証
curl -X POST "https://localhost:8443/${TENANT_ID}/v1/authorizations/${AUTH_TX_ID}/fido2-authentication" \
  -H "Content-Type: application/json" \
  -H "x-ssl-cert: ${ENCODED_CERT}" \
  -d '{
    "credential": {
      "id": "credential-id",
      "rawId": "...",
      "response": {
        "authenticatorData": "...",
        "clientDataJSON": "...",
        "signature": "..."
      },
      "type": "public-key"
    }
  }' | jq

# Step 3: 認可許諾
curl -X POST "https://localhost:8443/${TENANT_ID}/v1/authorizations/${AUTH_TX_ID}/authorize" \
  -H "Content-Type: application/json" \
  -H "x-ssl-cert: ${ENCODED_CERT}" | jq

# Step 4: トークン取得
AUTH_CODE="取得した認可コード"

curl -X POST "https://localhost:8443/${TENANT_ID}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -H "x-ssl-cert: ${ENCODED_CERT}" \
  --data-urlencode "grant_type=authorization_code" \
  --data-urlencode "code=${AUTH_CODE}" \
  --data-urlencode "redirect_uri=${REDIRECT_URI}" \
  --data-urlencode "client_id=${CLIENT_ID}" | jq
```

### パターン4: Refresh Tokenの使用

```bash
TENANT_ID="c3d4e5f6-a7b8-c9d0-e1f2-a3b4c5d6e7f8"
CLIENT_ID="d4e5f6a7-b8c9-d0e1-f2a3-b4c5d6e7f8a9"
CERT_FILE="./certs/client-cert.pem"

# 証明書エンコード
ENCODED_CERT=$(cat ${CERT_FILE} | awk '{printf "%s%%0A", $0}' | sed 's/%0A$//')

# Refresh Tokenでアクセストークンを更新（MTLS認証）
REFRESH_TOKEN="前回取得したリフレッシュトークン"

curl -X POST "https://localhost:8443/${TENANT_ID}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -H "x-ssl-cert: ${ENCODED_CERT}" \
  --data-urlencode "grant_type=refresh_token" \
  --data-urlencode "refresh_token=${REFRESH_TOKEN}" \
  --data-urlencode "client_id=${CLIENT_ID}" | jq

# レスポンス例（新しいaccess_tokenとrefresh_token）
# {
#   "access_token": "eyJhbGc...",
#   "token_type": "Bearer",
#   "expires_in": 900,
#   "refresh_token": "...",
#   "scope": "openid profile email account transfers"
# }
```

**重要**: Refresh Token使用時も、**同じクライアント証明書**が必要です（`tls_client_certificate_bound_access_tokens: true`のため）。

## リファレンス

### ドキュメント

- [FAPI 1.0 Advanced](https://openid.net/specs/openid-financial-api-part-2-1_0.html)
- [OAuth 2.0 MTLS](https://datatracker.ietf.org/doc/html/rfc8705)
- [JWT Authorization Request (JAR)](https://datatracker.ietf.org/doc/html/rfc9101)
- [JARM](https://openid.net/specs/oauth-v2-jarm.html)

### テンプレート

- [テンプレート一覧](../../templates/)
- [標準OIDC Webアプリ設定例](../standard-oidc-web-app/)

### 関連How-to

- [テナント設定](../../../documentation/docs/content_05_how-to/how-to-03-tenant-setup.md)
- [認証ポリシー設定](../../../documentation/docs/content_06_developer-guide/05-configuration/tenant.md)

## トラブルシューティング

### よくあるエラー

#### 1. `invalid_client` - MTLS認証エラー

**原因**: クライアント証明書の不一致、またはJWT Assertion署名エラー

**解決策**:
```bash
# 証明書確認
openssl x509 -in client-cert.pem -text -noout

# JWT Assertion検証（https://jwt.io でデコード）
```

#### 2. 認証ポリシー未適用

**原因**: 認証ポリシーAPIの実行失敗

**解決策**:
```bash
# 認証ポリシー一覧確認
curl https://localhost:8443/v1/management/organizations/${ORG_ID}/tenants/${TENANT_ID}/authentication-policies \
  -H "Authorization: Bearer ${ORG_TOKEN}"
```

#### 3. アクセストークン取得失敗

**原因**: 組織管理者の認証情報が正しくない

**解決策**:
```bash
# onboarding-request.jsonの user.email と user.raw_password を確認
jq '.user | {email, raw_password}' config/examples/financial-grade/onboarding-request.json
```

## セキュリティ注意事項

### 本番環境での使用時

1. **JWKS鍵の変更**: onboarding-request.json内のJWKSは **必ず** 本番用に生成
2. **パスワードの変更**: デフォルトパスワードは **絶対に使用しない**
3. **HTTPS必須**: `AUTHORIZATION_SERVER_URL` は必ずHTTPSを使用
4. **証明書管理**: MTLSクライアント証明書は信頼されたCAから発行
5. **ログ監視**: セキュリティイベントログを定期的に監査

### 推奨事項

- **鍵ローテーション**: 最低年1回のJWKS鍵更新
- **証明書有効期限**: 90日以内に更新通知設定
- **ログ保存期間**: 最低7年間（金融機関規制準拠）
- **異常検知**: 失敗認証の閾値アラート設定
