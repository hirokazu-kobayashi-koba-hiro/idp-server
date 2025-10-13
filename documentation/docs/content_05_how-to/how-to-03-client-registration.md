# クライアント登録ガイド

## このドキュメントの目的

**OAuth/OIDCクライアント**を正しく登録し、適切な設定を選択できるようになることが目標です。

### 所要時間
⏱️ **約20分**

### 前提条件
- 管理者トークンを取得済み
- テナントが作成済み
- 組織ID（organization-id）を取得済み
- OAuth 2.0/OIDC基礎知識（オプション）

### Management API URL

**実際のAPI**: 組織レベルAPI
```
POST /v1/management/organizations/{organization-id}/tenants/{tenant-id}/clients
```

**このドキュメントでの表記**: 簡潔性のため、以下のように省略
```
POST /v1/management/tenants/${TENANT_ID}/clients
```

**注意**: 実際のAPI呼び出し時は`organizations/{organization-id}/`を含める必要があります。

---

## クライアントとは

**あなたのアプリケーション**（Webアプリ、モバイルアプリ、サーバー等）をOAuth/OIDCクライアントとして登録する必要があります。

```
アプリケーション（クライアント）
  ↓ OAuth認証リクエスト
idp-server
  ↓ ユーザー認証
  ↓ Authorization Code発行
アプリケーション
  ↓ Authorization Code → Access Token交換
idp-server
  ↓ Access Token発行
```

---

## Level 1: 最小限のクライアント（5分）

### モバイルアプリ（Public Client）

**特徴**: クライアントシークレットを安全に保存できない

```bash
curl -X POST "http://localhost:8080/v1/management/tenants/${TENANT_ID}/clients" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{
    "client_id": "my-mobile-app",
    "client_name": "My Mobile App",
    "redirect_uris": ["myapp://callback"],
    "response_types": ["code"],
    "grant_types": ["authorization_code", "refresh_token"],
    "scope": "openid profile email",
    "token_endpoint_auth_method": "none",
    "application_type": "native"
  }'
```

**ポイント**:
- ✅ `token_endpoint_auth_method: "none"` - クライアントシークレット不要
- ✅ `application_type: "native"` - モバイルアプリ
- ✅ **PKCE必須**（自動的に要求される）

---

### Webアプリ（Confidential Client）

**特徴**: サーバー側でクライアントシークレットを安全に保存できる

```bash
curl -X POST "http://localhost:8080/v1/management/tenants/${TENANT_ID}/clients" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{
    "client_id": "my-web-app",
    "client_secret": "your-secret-here",
    "client_name": "My Web Application",
    "redirect_uris": ["https://app.example.com/callback"],
    "response_types": ["code"],
    "grant_types": ["authorization_code", "refresh_token"],
    "scope": "openid profile email",
    "token_endpoint_auth_method": "client_secret_basic",
    "application_type": "web"
  }'
```

**ポイント**:
- ✅ `client_secret` あり
- ✅ `token_endpoint_auth_method: "client_secret_basic"` - Basic認証
- ✅ `application_type: "web"` - Webアプリ

---

### サーバー間通信（Machine-to-Machine）

**特徴**: ユーザーなし、クライアント自身の権限で実行

```bash
curl -X POST "http://localhost:8080/v1/management/tenants/${TENANT_ID}/clients" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{
    "client_id": "my-backend-service",
    "client_secret": "your-secret-here",
    "client_name": "My Backend Service",
    "redirect_uris": [],
    "response_types": [],
    "grant_types": ["client_credentials"],
    "scope": "api:read api:write",
    "token_endpoint_auth_method": "client_secret_basic",
    "application_type": "web"
  }'
```

**ポイント**:
- ✅ `grant_types: ["client_credentials"]` のみ
- ✅ `redirect_uris: []` - リダイレクト不要
- ✅ カスタムスコープ（`api:read`, `api:write`等）

---

## Level 2: response_typesの選択（10分）

### response_typeとは

**Authorization Requestで何を返すか**を指定します。

| response_type | 返却物 | フロー | セキュリティ | 推奨 |
|--------------|--------|------|------------|-----|
| `code` | Authorization Code | Authorization Code Flow | ⭐⭐⭐⭐⭐ | ✅ 推奨 |
| `token` | Access Token | Implicit Flow | ⭐⭐ | ❌ 非推奨 |
| `id_token` | ID Token | Implicit Flow | ⭐⭐ | ❌ 非推奨 |
| `code id_token` | Code + ID Token | Hybrid Flow | ⭐⭐⭐⭐ | △ 特殊用途 |
| `code token` | Code + Access Token | Hybrid Flow | ⭐⭐⭐ | ❌ 非推奨 |

### 推奨設定

**ほとんどのアプリ**:
```json
"response_types": ["code"]
```

**理由**:
- ✅ 最もセキュア（Authorization CodeはワンタイムのみTOKEN交換可能）
- ✅ シンプル
- ✅ PKCE対応

**Implicit Flow（非推奨）**:
```json
// ❌ 使わない
"response_types": ["token", "id_token"]
```

**理由**:
- ❌ トークンがURLフラグメントに露出
- ❌ リフレッシュトークン非対応
- ❌ OAuth 2.1で廃止予定

---

## Level 3: grant_typesの選択（10分）

### grant_typeとは

**Token Endpointでどの方式でトークンを取得するか**を指定します。

| grant_type | 用途 | 必須？ | セキュリティ | 推奨 |
|-----------|------|-------|------------|-----|
| `authorization_code` | ユーザー認証後のトークン取得 | ✅ 必須 | ⭐⭐⭐⭐⭐ | ✅ |
| `refresh_token` | Access Token更新 | ✅ 推奨 | ⭐⭐⭐⭐⭐ | ✅ |
| `password` | ユーザー名・パスワード直接送信 | - | ⭐⭐ | ❌ 非推奨 |
| `client_credentials` | サーバー間通信 | - | ⭐⭐⭐⭐ | △ M2Mのみ |
| `urn:openid:params:grant-type:ciba` | バックチャネル認証 | - | ⭐⭐⭐⭐⭐ | △ 特殊用途 |

### アプリケーション種類別の推奨設定

#### モバイルアプリ / Webアプリ（標準）
```json
"grant_types": ["authorization_code", "refresh_token"]
```

**理由**:
- ✅ Authorization Code Flow（最もセキュア）
- ✅ Refresh Tokenで長期間ログイン維持

#### サーバー間通信（M2M）
```json
"grant_types": ["client_credentials"]
```

**理由**:
- ✅ ユーザーコンテキスト不要
- ✅ クライアント自身の権限で実行

#### レガシー対応（非推奨）
```json
"grant_types": ["password", "refresh_token"]
```

**理由**:
- ⚠️ パスワードを直接送信（中間者攻撃リスク）
- ⚠️ 既存システムとの互換性のみ使用
- ⚠️ 新規実装では使用しない

#### 高度な認証（CIBA対応）
```json
"grant_types": ["authorization_code", "refresh_token", "urn:openid:params:grant-type:ciba"]
```

**用途**:
- プッシュ通知による認証
- ユーザーがデバイスで承認

**詳細**: [CIBA Flow実装](./how-to-04-ciba-flow-fido-uaf.md)

---

## Level 4: scopeの選択（10分）

### 標準スコープ

| スコープ | 取得できる情報 | 必須？ |
|---------|-------------|-------|
| `openid` | `sub`（ユーザーID） | ✅ 必須（OIDC） |
| `profile` | `name`, `given_name`, `family_name`, `picture`等 | - |
| `email` | `email`, `email_verified` | - |
| `phone` | `phone_number`, `phone_number_verified` | - |
| `address` | `address`（郵送先住所） | - |

### 基本設定

```json
"scope": "openid profile email"
```

**理由**:
- ✅ ユーザー識別（sub）
- ✅ 基本情報（name）
- ✅ メールアドレス

---

### カスタムスコープ（claims:xxx）

idp-serverは`claims:`プレフィックスで**カスタム属性をID Tokenに含める**ことができます。

#### 使用例

```json
"scope": "openid profile email claims:roles claims:permissions"
```

**効果**:
```json
// ID Token
{
  "sub": "user-12345",
  "name": "John Doe",
  "email": "john@example.com",
  "roles": ["admin", "user"],        // ← claims:roles
  "permissions": ["read", "write"]   // ← claims:permissions
}
```

#### よくあるカスタムスコープ

| スコープ | ID Tokenに含まれる情報 | 用途 |
|---------|---------------------|------|
| `claims:roles` | ユーザーのロール一覧 | 権限チェック |
| `claims:permissions` | ユーザーの権限一覧 | 細かい権限制御 |
| `claims:department` | 部署情報 | 組織内制御 |
| `claims:assigned_tenants` | 割り当てテナント一覧 | マルチテナント対応 |

**設定**:
```bash
# テナント設定でカスタムクレームマッピングを有効化
curl -X PUT "http://localhost:8080/v1/management/tenants/${TENANT_ID}" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{
    "attributes": {
      "enabledCustomClaimsScopeMapping": "true",
      "id_token_strict_mode": "false"
    }
  }'
```

**詳細**: [Concept: カスタムクレーム](../content_03_concepts/concept-09-custom-claims.md)

---

### アプリケーション種類別の推奨スコープ

#### ソーシャルログイン
```json
"scope": "openid profile email"
```

#### エンタープライズアプリ
```json
"scope": "openid profile email claims:roles claims:department"
```

#### マルチテナントSaaS
```json
"scope": "openid profile email claims:assigned_tenants claims:current_tenant"
```

---

## Level 5: token_endpoint_auth_methodの選択（5分）

### クライアント認証方式

トークンエンドポイントで**クライアントをどう認証するか**を指定します。

| 認証方式 | セキュリティ | 用途 | 推奨 |
|---------|------------|------|-----|
| `none` | - | Public Client（モバイル/SPA） | ✅ Public Client |
| `client_secret_basic` | ⭐⭐ | Basic認証ヘッダー | ✅ Confidential Client |
| `client_secret_post` | ⭐ | POSTボディ | △ レガシー |
| `client_secret_jwt` | ⭐⭐⭐ | JWT署名（共有鍵） | ✅ 高セキュリティ |
| `private_key_jwt` | ⭐⭐⭐⭐ | JWT署名（秘密鍵） | ✅ 最高セキュリティ |
| `tls_client_auth` | ⭐⭐⭐⭐⭐ | クライアント証明書（MTLS） | ✅ FAPI準拠 |

### 推奨設定

**モバイルアプリ / SPA**:
```json
"token_endpoint_auth_method": "none"
```
- PKCE必須で代替

**Webアプリ（バックエンドあり）**:
```json
"token_endpoint_auth_method": "client_secret_basic"
```
- 最も一般的

**金融機関等（高セキュリティ）**:
```json
"token_endpoint_auth_method": "private_key_jwt"
```
- クライアント秘密鍵で署名

**詳細**: [Client Authentication実装](../content_06_developer-guide/03-application-plane/10-client-authentication.md)

---

## Level 6: トークン有効期限の設定（5分）

### デフォルト設定

```json
{
  "client_id": "my-app",
  ...
  "extension": {
    "access_token_duration": 3600,      // 1時間（秒）
    "refresh_token_duration": 2592000   // 30日（秒）
  }
}
```

### アプリケーション種類別の推奨値

| アプリ種類 | Access Token | Refresh Token | 理由 |
|----------|-------------|--------------|------|
| **モバイルアプリ** | 1時間（3600秒） | 30日（2592000秒） | バランス型 |
| **SPA** | 15分（900秒） | 7日（604800秒） | 短命推奨 |
| **Webアプリ** | 1時間（3600秒） | 30日（2592000秒） | 標準的 |
| **管理画面** | 15分（900秒） | 1日（86400秒） | 高セキュリティ |
| **API（M2M）** | 1時間（3600秒） | - | Refresh不要 |

**セキュリティトレードオフ**:
- 短い有効期限 → セキュア、頻繁な再認証
- 長い有効期限 → 便利、トークン漏洩リスク

---

## Level 7: redirect_urisの設定（重要）

### redirect_uriとは

**認証成功後にユーザーをリダイレクトするURL**

```
ユーザー認証成功
  ↓
idp-server: リダイレクト
  ↓
https://app.example.com/callback?code=abc123
  ↑ この URL が redirect_uri
```

### 設定例

#### Webアプリ
```json
"redirect_uris": [
  "https://app.example.com/callback",
  "https://app.example.com/auth/callback"
]
```

#### モバイルアプリ
```json
"redirect_uris": [
  "myapp://callback",
  "com.example.myapp://oauth/callback"
]
```

#### 開発環境 + 本番環境
```json
"redirect_uris": [
  "http://localhost:3000/callback",      // 開発環境
  "https://app.example.com/callback"     // 本番環境
]
```

### ⚠️ セキュリティ重要事項

**完全一致**が必須:
```bash
# ✅ OK
登録: https://app.example.com/callback
使用: https://app.example.com/callback

# ❌ NG: 末尾スラッシュ違い
登録: https://app.example.com/callback
使用: https://app.example.com/callback/  # エラー！

# ❌ NG: サブドメイン違い
登録: https://app.example.com/callback
使用: https://sub.app.example.com/callback  # エラー！
```

**ワイルドカード非対応**:
```json
// ❌ 使えない
"redirect_uris": ["https://*.example.com/callback"]

// ✅ 個別に登録
"redirect_uris": [
  "https://app1.example.com/callback",
  "https://app2.example.com/callback"
]
```

---

## Level 8: 高度な設定（オプション）

### CIBA対応クライアント

```json
{
  "client_id": "my-app",
  "grant_types": ["authorization_code", "refresh_token", "urn:openid:params:grant-type:ciba"],
  "extension": {
    "default_ciba_authentication_interaction_type": "authentication-device-notification"
  }
}
```

**CIBA認証インタラクションタイプ**:

| タイプ | 意味 | ユーザー操作 |
|-------|------|-----------|
| `authentication-device-notification` | プッシュ通知 | デバイスで承認 |
| `authentication-device-notification-no-action` | プッシュ通知（自動承認） | 操作不要 |
| `poll` | ポーリング | 別途ログイン |

**詳細**: [CIBA Flow](./how-to-04-ciba-flow-fido-uaf.md)

---

### Federationを有効化

```json
{
  "client_id": "my-app",
  "extension": {
    "available_federations": [
      {
        "id": "external-idp-a",
        "type": "oidc",
        "sso_provider": "external-idp-a"
      },
      {
        "id": "external-idp-b",
        "type": "oidc",
        "sso_provider": "external-idp-b"
      }
    ]
  }
}
```

**効果**: このクライアントで外部IdP連携が利用可能になる

**詳細**: [Federation設定](./how-to-12-federation-setup.md)

---

## 実例: エンタープライズモバイルアプリの設定

### 高度な機能を使用する完全な設定例

**シナリオ**: CIBA対応、本人確認、カスタムクレーム使用のエンタープライズアプリ

```json
{
  "client_id": "${ENTERPRISE_APP_CLIENT_ID}",
  "client_id_alias": "enterprise-mobile-app",
  "client_secret": "${ENTERPRISE_APP_CLIENT_SECRET}",
  "redirect_uris": [
    "com.example.enterprise://callback"
  ],
  "response_types": ["code"],
  "grant_types": [
    "authorization_code",
    "refresh_token",
    "urn:openid:params:grant-type:ciba"
  ],
  "scope": "openid profile email address phone update identity_verification_application claims:roles claims:department claims:authentication_devices",
  "client_name": "Enterprise Mobile App",
  "token_endpoint_auth_method": "client_secret_basic",
  "application_type": "native",
  "extension": {
    "default_ciba_authentication_interaction_type": "authentication-device-notification-no-action",
    "access_token_duration": 3600,
    "refresh_token_duration": 2592000,
    "supported_jar": true
  }
}
```

**特徴**:
- ✅ CIBA Grant対応（プッシュ通知認証）
- ✅ カスタムスコープ多数（組織固有情報）
- ✅ 本人確認申請スコープ（`identity_verification_application`）
- ✅ 認証デバイス情報（`claims:authentication_devices`）
- ✅ JWT Authorization Request対応（`supported_jar`）

**初学者へのアドバイス**:
- まずはシンプルなクライアント（Level 1）から始める
- 必要に応じて段階的に機能追加
- この例は参考として、自社要件に合わせてカスタマイズ

---

## よくあるエラー

### エラー1: redirect_uri不一致

**エラー**:
```json
{
  "error": "invalid_request",
  "error_description": "redirect_uri does not match registered URIs"
}
```

**原因**: Authorization Requestの`redirect_uri`がクライアント登録時の`redirect_uris`に含まれていない

**解決策**:
```bash
# クライアント設定を確認
curl "http://localhost:8080/v1/management/tenants/${TENANT_ID}/clients/${CLIENT_ID}" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  | jq '.redirect_uris'

# redirect_urisに追加
curl -X PUT "http://localhost:8080/v1/management/tenants/${TENANT_ID}/clients/${CLIENT_ID}" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{
    "redirect_uris": [
      "https://app.example.com/callback",
      "http://localhost:3000/callback"  // 追加
    ]
  }'
```

---

### エラー2: unsupported_grant_type

**エラー**:
```json
{
  "error": "unsupported_grant_type",
  "error_description": "grant_type 'password' is not allowed for this client"
}
```

**原因**: Token Requestで使用した`grant_type`がクライアントの`grant_types`に含まれていない

**解決策**:
```bash
# grant_typesに追加（ただしpasswordは非推奨）
curl -X PUT "http://localhost:8080/v1/management/tenants/${TENANT_ID}/clients/${CLIENT_ID}" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{
    "grant_types": ["authorization_code", "refresh_token"]  // passwordは追加しない
  }'
```

---

### エラー3: invalid_scope

**エラー**:
```json
{
  "error": "invalid_scope",
  "error_description": "Requested scope 'admin' is not allowed"
}
```

**原因**: Authorization Requestで要求したスコープがクライアントの`scope`に含まれていない

**解決策**:
```bash
# クライアントのスコープを確認・更新
curl -X PUT "http://localhost:8080/v1/management/tenants/${TENANT_ID}/clients/${CLIENT_ID}" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{
    "scope": "openid profile email admin"  // adminスコープ追加
  }'
```

---

## クライアント登録のベストプラクティス

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
   "grant_types": ["authorization_code", "refresh_token", "password", "client_credentials", "ciba"]
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

---

## 次のステップ

✅ クライアント登録ができました！

### 認証を設定
- [How-to: パスワード認証](./how-to-03-password-authentication.md)
- [How-to: MFA設定](./how-to-09-mfa-setup.md)
- [How-to: Federation設定](./how-to-12-federation-setup.md)

### 高度な機能
- [How-to: CIBA Flow](./how-to-04-ciba-flow-fido-uaf.md) - バックチャネル認証
- [How-to: Identity Verification](./how-to-07-identity-verification-application.md) - 身元確認

---

## 関連ドキュメント

- [Concept: ID管理](../content_03_concepts/concept-02-id-management.md) - クライアント概念
- [Concept: トークン管理](../content_03_concepts/concept-06-token-management.md) - トークン有効期限
- [Developer Guide: Client Authentication](../content_06_developer-guide/03-application-plane/10-client-authentication.md) - 7つの認証方式詳細
- [API Reference](../content_07_reference/api-reference.md) - Management API仕様

---

**最終更新**: 2025-10-13
**難易度**: ⭐⭐☆☆☆（初級〜中級）
**対象**: 初めてクライアントを登録する管理者・開発者
**習得スキル**: response_types、grant_types、scope、認証方式の選択
