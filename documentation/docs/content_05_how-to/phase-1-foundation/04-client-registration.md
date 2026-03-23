# クライアント登録ガイド

## このドキュメントの目的

**クライアント（アプリケーション）を登録し、認可フローが動作する環境を構築する**ことが目標です。

具体的には、**3つの代表的なクライアントタイプ**（Webアプリ、モバイルアプリ、M2M）を登録し、それぞれの認証方式を理解します。

### 学べること

✅ **OAuth/OIDCクライアント登録の基礎**
- クライアントとは何か（Confidential vs Public）
- 3つの代表的なクライアントタイプ（Web/Mobile/M2M）
- 必須設定項目（client_id, redirect_uris, grant_types等）

✅ **実践的な知識**
- クライアント登録の実行手順
- 登録確認方法
- トラブルシューティング

### 所要時間
⏱️ **約15分**

### このドキュメントの位置づけ

**Phase 1**: 最小構成で動作確認（Step 3/5）

**前提ドキュメント**:
- [組織初期化](./02-organization-initialization.md) - 組織作成済み
- [テナント作成](./03-tenant-setup.md) - テナント作成済み

**次のドキュメント**:
- [ユーザー登録](./05-user-registration.md) - ユーザー作成と認証

### 前提条件
- [how-to-02](./03-tenant-setup.md)でテナント作成完了
- 組織管理者トークンを取得済み
- 組織ID・テナントIDを確認済み

---

## クライアントとは

**Client（クライアント）**は、OAuth 2.0/OIDCプロトコルを使用してリソースにアクセスする**アプリケーション**です。

クライアントには以下の2種類があります：
- **Confidential Client（機密クライアント）**: `client_secret`を安全に保管できる（例：サーバーサイドWebアプリ）
- **Public Client（公開クライアント）**: `client_secret`を保管できない（例：SPA、モバイルアプリ）

**詳細**: [📖 Concept 19: Client](../content_03_concepts/01-foundation/concept-04-client.md) - Client種別、認証方法、Tenant-Client-User関係の詳細解説

---

## このドキュメントで行うこと

3つの代表的なクライアントタイプを登録します：

### 1. 組織管理者トークンの準備

**前提**: [how-to-02](./03-tenant-setup.md)で設定した環境変数を使用します。

```bash
# 接続先サーバーURL
IDP_SERVER_URL=http://localhost:8080

# 環境変数の確認
echo "Organization ID: $ORGANIZATION_ID"
echo "Public Tenant ID: $PUBLIC_TENANT_ID"
echo "Admin Token: ${ORG_ADMIN_TOKEN:0:50}..."
```

まだ設定していない場合は、how-to-02を参照してトークンを取得してください。

---

## 動作確認：3つのクライアントタイプを登録する

### 1. Webアプリケーション（Confidential Client）

**特徴**: サーバー側でclient_secretを安全に保管できる

#### クライアントIDの準備

```bash
# クライアントID設定（UUID必須）
export WEB_CLIENT_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
export WEB_CLIENT_SECRET="$(openssl rand -base64 32)"

# 確認
echo "Web Client ID: $WEB_CLIENT_ID"
echo "Web Client Secret: $WEB_CLIENT_SECRET"
```

#### クライアント登録

```bash
curl -X POST "${IDP_SERVER_URL}/v1/management/organizations/${ORGANIZATION_ID}/tenants/${PUBLIC_TENANT_ID}/clients" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer ${ORG_ADMIN_TOKEN}" \
  -d "{
  \"client_id\": \"${WEB_CLIENT_ID}\",
  \"client_secret\": \"${WEB_CLIENT_SECRET}\",
  \"client_name\": \"My Web Application\",
  \"redirect_uris\": [
    \"https://app.example.com/callback\",
    \"http://localhost:3000/callback\"
  ],
  \"response_types\": [\"code\"],
  \"grant_types\": [\"authorization_code\", \"refresh_token\"],
  \"scope\": \"openid profile email\",
  \"token_endpoint_auth_method\": \"client_secret_basic\",
  \"application_type\": \"web\"
}" | jq .
```

**期待されるレスポンス**:
```json
{
  "dry_run": false,
  "result": {
    "client_id": "web-app-client",
    "client_name": "My Web Application",
    "redirect_uris": [
      "https://app.example.com/callback",
      "http://localhost:3000/callback"
    ],
    "response_types": ["code"],
    "grant_types": ["authorization_code", "refresh_token"],
    "token_endpoint_auth_method": "client_secret_basic",
    "application_type": "web"
  }
}
```

#### 確認ポイント
- ✅ `client_id`が正しく登録されている
- ✅ `redirect_uris`に2つのURIが含まれている
- ✅ `token_endpoint_auth_method`が`client_secret_basic`
- ✅ `grant_types`に`authorization_code`と`refresh_token`が含まれている

---

### 2. モバイルアプリ（Public Client + PKCE）

**特徴**: client_secret不要、PKCE必須

#### クライアントIDの準備

```bash
# モバイルアプリ用クライアントID（UUID必須）
export MOBILE_CLIENT_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')

# 確認
echo "Mobile Client ID: $MOBILE_CLIENT_ID"
```

#### クライアント登録

```bash
curl -X POST "${IDP_SERVER_URL}/v1/management/organizations/${ORGANIZATION_ID}/tenants/${PUBLIC_TENANT_ID}/clients" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer ${ORG_ADMIN_TOKEN}" \
  -d "{
  \"client_id\": \"${MOBILE_CLIENT_ID}\",
  \"client_name\": \"My Mobile App\",
  \"redirect_uris\": [
    \"com.example.myapp://callback\"
  ],
  \"response_types\": [\"code\"],
  \"grant_types\": [\"authorization_code\", \"refresh_token\"],
  \"scope\": \"openid profile email\",
  \"token_endpoint_auth_method\": \"none\",
  \"application_type\": \"native\"
}" | jq .
```

**期待されるレスポンス**:
```json
{
  "dry_run": false,
  "result": {
    "client_id": "mobile-app-client",
    "client_name": "My Mobile App",
    "redirect_uris": [
      "com.example.myapp://callback"
    ],
    "response_types": ["code"],
    "grant_types": ["authorization_code", "refresh_token"],
    "token_endpoint_auth_method": "none",
    "application_type": "native"
  }
}
```

#### 確認ポイント
- ✅ `token_endpoint_auth_method`が`none`（client_secretなし）
- ✅ `application_type`が`native`
- ✅ `redirect_uris`にカスタムURLスキーム（`com.example.myapp://`）
- ⚠️ **PKCE必須**: 実際の認証時にcode_challenge/code_verifierを使用

---

### 3. サーバー間通信（M2M: Machine-to-Machine）

**特徴**: ユーザー認証不要、Client Credentials Flowのみ

#### クライアントIDの準備

```bash
# M2M用クライアントID（UUID必須）
export M2M_CLIENT_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
export M2M_CLIENT_SECRET="$(openssl rand -base64 32)"

# 確認
echo "M2M Client ID: $M2M_CLIENT_ID"
echo "M2M Client Secret: $M2M_CLIENT_SECRET"
```

#### クライアント登録

```bash
curl -X POST "${IDP_SERVER_URL}/v1/management/organizations/${ORGANIZATION_ID}/tenants/${PUBLIC_TENANT_ID}/clients" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer ${ORG_ADMIN_TOKEN}" \
  -d "{
  \"client_id\": \"${M2M_CLIENT_ID}\",
  \"client_secret\": \"${M2M_CLIENT_SECRET}\",
  \"client_name\": \"Backend Service\",
  \"redirect_uris\": [],
  \"response_types\": [],
  \"grant_types\": [\"client_credentials\"],
  \"scope\": \"api:read api:write\",
  \"token_endpoint_auth_method\": \"client_secret_basic\",
  \"application_type\": \"web\"
}" | jq .
```

**期待されるレスポンス**:
```json
{
  "dry_run": false,
  "result": {
    "client_id": "backend-service",
    "client_name": "Backend Service",
    "redirect_uris": [],
    "response_types": [],
    "grant_types": ["client_credentials"],
    "token_endpoint_auth_method": "client_secret_basic",
    "application_type": "web"
  }
}
```

#### 確認ポイント
- ✅ `grant_types`が`client_credentials`のみ
- ✅ `redirect_uris`が空配列（リダイレクト不要）
- ✅ `response_types`が空配列（認可コード不要）
- ✅ `scope`がカスタムスコープ（`api:read`, `api:write`）

---

### 登録したクライアントの確認

```bash
# クライアント一覧取得
curl -X GET "${IDP_SERVER_URL}/v1/management/organizations/${ORGANIZATION_ID}/tenants/${PUBLIC_TENANT_ID}/clients" \
  -H "Authorization: Bearer ${ORG_ADMIN_TOKEN}" | jq .
```

**期待される結果**: 3つのクライアント（web-app-client, mobile-app-client, backend-service）が表示される

---

### トラブルシューティング

#### ❌ トークンが期限切れ

**症状**: `{"error": "invalid_token"}` エラー

**解決策**: トークンを再取得してください

```bash
export ORG_ADMIN_TOKEN=$(curl -sS -X POST "${IDP_SERVER_URL}/${TENANT_ID}/v1/tokens" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=password' \
  -d "username=${ADMIN_EMAIL}" \
  -d "password=${ADMIN_PASSWORD}" \
  -d "client_id=${CLIENT_ID}" \
  -d "client_secret=${CLIENT_SECRET}" \
  -d 'scope=management' | jq -r '.access_token')
```

#### ❌ client_id重複エラー

**症状**: `{"error": "invalid_request", "error_description": "client_id already exists"}`

**原因**: 同じテナント内で同じclient_idが既に登録されている

**解決策**: 別のclient_idを使用するか、既存のクライアントを削除

```bash
# 既存クライアントを削除
curl -X DELETE "${IDP_SERVER_URL}/v1/management/organizations/${ORGANIZATION_ID}/tenants/${PUBLIC_TENANT_ID}/clients/${CLIENT_ID}" \
  -H "Authorization: Bearer ${ORG_ADMIN_TOKEN}"
```
---

# API Reference

このセクションでは、クライアント登録APIの詳細仕様を説明します。

---

## クライアント登録API

### リクエスト

```http
POST /v1/management/organizations/{organization-id}/tenants/{tenant-id}/clients
Authorization: Bearer {access_token}
Content-Type: application/json

{
  "client_id": "my-app",
  "client_secret": "secret-xxx",
  "client_name": "My Application",
  "redirect_uris": ["https://app.example.com/callback"],
  "response_types": ["code"],
  "grant_types": ["authorization_code", "refresh_token"],
  "scope": "openid profile email",
  "token_endpoint_auth_method": "client_secret_basic",
  "application_type": "web"
}
```

### パラメータ説明

#### 基本情報

| 項目 | 型 | 必須 | 説明 |
|-----|---|------|------|
| `client_id` | string (UUID) | ✅ | クライアントの一意識別子（UUID形式必須） |
| `client_secret` | string | 条件付き | クライアントシークレット（Confidential Clientの場合必須） |
| `client_name` | string | ❌ | クライアント名（表示用） |
| `application_type` | string | ✅ | アプリケーションタイプ（`web` / `native`） |

**client_secret必須条件**:
- `token_endpoint_auth_method`が`client_secret_basic`または`client_secret_post`の場合
- Confidential Client（サーバーサイドアプリ）の場合

#### リダイレクトURI

| 項目 | 型 | 必須 | 説明 |
|-----|---|------|------|
| `redirect_uris` | array | ✅ | リダイレクトURI配列（完全一致必須） |

**重要**:
- 完全一致が必須（パス、ポート、プロトコル）
- 複数登録可能
- フラグメント（`#`）は禁止

#### OAuth/OIDC設定

| 項目 | 型 | 必須 | 説明 |
|-----|---|------|------|
| `response_types` | array | ✅ | サポートするレスポンスタイプ（例: `["code"]`） |
| `grant_types` | array | ✅ | サポートするグラントタイプ（例: `["authorization_code", "refresh_token"]`） |
| `scope` | string | ❌ | デフォルトスコープ（スペース区切り） |
| `token_endpoint_auth_method` | string | ❌ | トークンエンドポイント認証方式（デフォルト: `client_secret_basic`） |

**主要なgrant_type**:
- `authorization_code` - Authorization Code Flow
- `refresh_token` - Refresh Token使用
- `client_credentials` - Client Credentials Flow（M2M）
- `urn:openid:params:grant-type:ciba` - CIBA Flow

**主要なtoken_endpoint_auth_method**:
- `client_secret_basic` - Basic認証（Confidential Client）
- `client_secret_post` - POSTボディ（Confidential Client）
- `none` - Public Client（PKCE必須）
- `private_key_jwt` - JWT署名（秘密鍵）
- `tls_client_auth` - クライアント証明書（mTLS）

### レスポンス

```http
HTTP/1.1 201 Created
Content-Type: application/json

{
  "dry_run": false,
  "result": {
    "client_id": "my-app",
    "client_name": "My Application",
    "redirect_uris": ["https://app.example.com/callback"],
    "response_types": ["code"],
    "grant_types": ["authorization_code", "refresh_token"],
    "scope": "openid profile email",
    "token_endpoint_auth_method": "client_secret_basic",
    "application_type": "web"
  }
}
```

**注意**: `client_secret`はレスポンスに含まれません。登録時の値を安全に保管してください。

---

## クライアント取得API

### リクエスト

```http
GET /v1/management/organizations/{organization-id}/tenants/{tenant-id}/clients/{client-id}
Authorization: Bearer {access_token}
```

### レスポンス

```json
{
  "client_id": "my-app",
  "client_name": "My Application",
  "redirect_uris": ["https://app.example.com/callback"],
  "response_types": ["code"],
  "grant_types": ["authorization_code", "refresh_token"],
  "scope": "openid profile email",
  "token_endpoint_auth_method": "client_secret_basic",
  "application_type": "web",
  "extension": {
    "access_token_duration": 3600,
    "refresh_token_duration": 86400
  }
}
```

---

## クライアント更新API

### リクエスト

```http
PUT /v1/management/organizations/{organization-id}/tenants/{tenant-id}/clients/{client-id}
Authorization: Bearer {access_token}
Content-Type: application/json

{
  "client_name": "My Updated Application",
  "redirect_uris": [
    "https://app.example.com/callback",
    "https://app.example.com/silent-renew"
  ],
  "scope": "openid profile email address"
}
```

### Dry Run（検証のみ、更新なし）

```http
PUT /v1/management/organizations/{organization-id}/tenants/{tenant-id}/clients/{client-id}?dry_run=true
Authorization: Bearer {access_token}
Content-Type: application/json

{
  "redirect_uris": [
    "https://app.example.com/callback",
    "http://localhost:3000/callback"
  ]
}
```

**レスポンス**:
```json
{
  "dry_run": true,
  "validation_result": {
    "valid": true,
    "warnings": []
  }
}
```

---

## よくあるエラーと解決策

### エラー1: redirect_uri不一致

**エラー**:
```json
{
  "error": "invalid_request",
  "error_description": "redirect_uri does not match registered URIs"
}
```

**原因**: Authorization Requestの`redirect_uri`がクライアント登録時の`redirect_uris`に含まれていない

**解決策**: クライアント設定を確認・更新

```bash
# redirect_urisに追加
curl -X PUT "${IDP_SERVER_URL}/v1/management/organizations/${ORGANIZATION_ID}/tenants/${PUBLIC_TENANT_ID}/clients/${CLIENT_ID}" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer ${ORG_ADMIN_TOKEN}" \
  -d '{
    "redirect_uris": [
      "https://app.example.com/callback",
      "http://localhost:3000/callback"
    ]
  }'
```

### エラー2: unsupported_grant_type

**エラー**:
```json
{
  "error": "unsupported_grant_type",
  "error_description": "grant_type 'refresh_token' is not allowed for this client"
}
```

**原因**: Token Requestで使用した`grant_type`がクライアントの`grant_types`に含まれていない

**解決策**: `grant_types`に追加

```bash
curl -X PUT "${IDP_SERVER_URL}/v1/management/organizations/${ORGANIZATION_ID}/tenants/${PUBLIC_TENANT_ID}/clients/${CLIENT_ID}" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer ${ORG_ADMIN_TOKEN}" \
  -d '{
    "grant_types": ["authorization_code", "refresh_token"]
  }'
```

### エラー3: invalid_scope

**エラー**:
```json
{
  "error": "invalid_scope",
  "error_description": "Requested scope 'admin' is not allowed"
}
```

**原因**: Authorization Requestで要求したスコープがクライアントの`scope`またはテナントの`scopes_supported`に含まれていない

**解決策**:
1. クライアントのスコープを更新
2. テナントの`scopes_supported`に追加

```bash
# クライアントのスコープを更新
curl -X PUT "${IDP_SERVER_URL}/v1/management/organizations/${ORGANIZATION_ID}/tenants/${PUBLIC_TENANT_ID}/clients/${CLIENT_ID}" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer ${ORG_ADMIN_TOKEN}" \
  -d '{
    "scope": "openid profile email admin"
  }'
```

---

## ベストプラクティス

### ✅ 推奨

1. **最小権限の原則**
   ```json
   // ✅ 必要なスコープのみ
   "scope": "openid profile email"

   // ❌ 全部許可（危険）
   "scope": "openid profile email phone address update delete admin"
   ```

2. **response_types は code のみ**
   ```json
   "response_types": ["code"]
   ```

3. **grant_types は最小限**
   ```json
   // ✅ 標準的
   "grant_types": ["authorization_code", "refresh_token"]

   // ❌ 全部許可（不要なものを有効化）
   "grant_types": ["authorization_code", "refresh_token", "password", "client_credentials"]
   ```

4. **redirect_uris は厳密に**
   ```json
   // ✅ 必要なURIのみ
   "redirect_uris": ["https://app.example.com/callback"]

   // ❌ 開発用URLを本番に残す（危険）
   "redirect_uris": [
     "https://app.example.com/callback",
     "http://localhost:3000/callback"  // 本番環境では削除
   ]
   ```

5. **client_secretの安全な管理** (RFC 6749 Section 2.3.1, 10.1)
   - 環境変数で管理（コードにハードコードしない）
   - HTTPSで送信（平文送信禁止）
   - 漏洩が疑われる場合は即座に無効化

---

## 補足：高度な設定に関する注意事項

### 外部IdPフェデレーション（`extension.available_federations`）

Google、Azure AD、LINEなどの外部IdPと連携してソーシャルログインを実現する場合、クライアント設定の `extension.available_federations` に使用するフェデレーション名を配列で指定する必要があります。

```json
{
  "extension": {
    "available_federations": ["google", "azure_ad"]
  }
}
```

この設定がないと、該当クライアントの認可リクエスト時に外部IdPへのフェデレーション選択肢が表示されません。フェデレーション名はテナント側で事前に設定された `federation_configs` のキー名と一致させてください。

### `claims_supported` と `scopes_supported` の違い

クライアントが受け取るトークンに含まれるクレームを制御する際、以下の2つの設定の役割の違いに注意してください：

| 設定項目 | 設定場所 | 役割 |
|---------|---------|------|
| `claims_supported` | 認可サーバー設定（`authorization_server`） | **実際にID Token / UserInfoに含まれるクレームを制御する**。ここに含まれていないクレームは、スコープで要求しても返却されない |
| `scopes_supported` | 認可サーバー設定（`authorization_server`） | **OpenID Connect Discoveryエンドポイント（`.well-known/openid-configuration`）に表示されるスコープ一覧**。表示用であり、トークンに含まれるクレームの制御には直接影響しない |

> ⚠️ `scopes_supported` にスコープを追加しただけでは、対応するクレームがトークンに含まれるようにはなりません。`claims_supported` にクレーム名を追加することが必要です。例えば、`email` スコープを使って `email` クレームを返したい場合、`claims_supported` に `"email"` が含まれている必要があります。

---

## 次のステップ

✅ クライアント登録ができました！

### 認証を実行
- [How-to: ユーザー登録と認証](./05-user-registration.md) - ユーザー作成と初回ログイン

### 高度な設定
- [Developer Guide: Client設定](../content_06_developer-guide/05-configuration/client.md) - 詳細な設定オプション
- [How-to: CIBA Flow](../phase-3-advanced/fido-uaf/01-ciba-flow.md) - バックチャネル認証

---

## 関連ドキュメント

- [Concept: OAuth 2.0とOpenID Connect](../content_11_learning/04-openid-connect/oauth-oidc-basics.md) - プロトコル概要
- [Developer Guide: Client Authentication](../content_06_developer-guide/03-application-plane/10-client-authentication.md) - 7つの認証方式詳細
- [API Reference](../content_07_reference/api-reference.md) - Management API仕様

---

**最終更新**: 2025-10-14
