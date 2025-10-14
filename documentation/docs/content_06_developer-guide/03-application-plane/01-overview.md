# Application Plane - OAuth/OIDC認証フロー概要

## Application Planeとは

**エンドユーザー・アプリケーションが使用する認証・認可API**

**情報源**: `libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/`
**確認日**: 2025-10-12

### Control Planeとの違い

| 項目 | Application Plane | Control Plane |
|------|------------------|--------------|
| **目的** | 認証・認可フロー実行 | システム・リソース設定管理 |
| **ユーザー** | エンドユーザー・RPアプリケーション | システム管理者・組織管理者 |
| **URL** | `/{tenant-id}/v1/...` | `/v1/management/...` |
| **認証** | ユーザートークン or クライアント認証 | 管理者トークン（特定権限必須） |
| **実装パッケージ** | `usecases/application/` | `usecases/control_plane/` |
| **実装パターン** | シンプルな委譲（権限チェックなし） | 10フェーズ（権限・Audit Log・Dry Run） |

---

## Application Planeの種類

### 1. Relying Party API（OAuth/OIDCフロー）

**情報源**: `libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/restapi/oauth/`

#### 認可リクエスト（Authorization Request）

```
# PAR (Pushed Authorization Request)
POST /{tenant-id}/v1/authorizations/push
Content-Type: application/x-www-form-urlencoded

client_id=xxx&redirect_uri=https://...&scope=openid profile&...

# 認可リクエスト
GET /{tenant-id}/v1/authorizations?request_uri=urn:ietf:params:oauth:request_uri:xxx
GET /{tenant-id}/view/v1/authorizations?response_type=code&client_id=xxx&...
```

**実装**: [OAuthV1Api.java](../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/restapi/oauth/OAuthV1Api.java)

#### トークンエンドポイント

```
# トークン発行
POST /{tenant-id}/v1/tokens
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code&code=abc123&client_id=xxx&...

# トークンイントロスペクション
POST /{tenant-id}/v1/tokens/introspection

# トークン失効
POST /{tenant-id}/v1/tokens/revocation
```

**実装**: [TokenV1Api.java](../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/restapi/token/TokenV1Api.java)

#### UserInfo

```
GET /{tenant-id}/v1/userinfo
Authorization: Bearer eyJ...
```

**実装**: [UserinfoV1Api.java](../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/restapi/userinfo/UserinfoV1Api.java)

#### Discovery（メタデータ）

```
# OpenID Connect Discovery
GET /{tenant-id}/.well-known/openid-configuration

# Shared Signals Framework Discovery
GET /{tenant-id}/.well-known/sse-configuration

# FIDO UAF Discovery
GET /{tenant-id}/.well-known/fido-uaf-configuration
```

**実装**: `libs/idp-server-springboot-adapter/.../application/restapi/metadata/`

---

### 2. CIBA（バックチャネル認証）

**情報源**: `libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/restapi/ciba/`

```
# CIBA認証リクエスト
POST /{tenant-id}/v1/backchannel/authentications
{
  "login_hint": "user@example.com",
  "binding_message": "Code: 1234"
}

# レスポンス
{
  "auth_req_id": "xxx",
  "expires_in": 300
}

# トークン取得（ポーリング）
POST /{tenant-id}/v1/tokens
{
  "grant_type": "urn:openid:params:grant-type:ciba",
  "auth_req_id": "xxx"
}
```

**実装**: [CibaV1Api.java](../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/restapi/ciba/CibaV1Api.java)

---

### 3. エンドユーザーAPI（`/me` エンドポイント）

**情報源**: `libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/restapi/me/`

リソースオーナー（エンドユーザー）自身のリソースを操作するAPI。

#### ユーザー情報

```
# 自分のプロフィール取得
GET /{tenant-id}/v1/me
Authorization: Bearer eyJ...
```

**実装**: [UserV1Api.java](../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/restapi/me/UserV1Api.java)

#### 身元確認申込み

```
# 身元確認申込み実行
POST /{tenant-id}/v1/me/identity-verification/applications/{verification-type}/{process}
Authorization: Bearer eyJ...

# 身元確認結果取得
GET /{tenant-id}/v1/me/identity-verification/results/{result-id}
Authorization: Bearer eyJ...
```

**実装**:
- [IdentityVerificationApplicationV1Api.java](../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/restapi/me/IdentityVerificationApplicationV1Api.java)
- [IdentityVerificationV1Api.java](../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/restapi/me/IdentityVerificationV1Api.java)

---

### 4. 認証インタラクション

**情報源**: `libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/restapi/authentication/`

```
# 認証実行
POST /{tenant-id}/v1/authentications/{authorization-request-id}
{
  "type": "password",
  "username": "user@example.com",
  "password": "secret"
}

# 認証デバイス管理
GET    /{tenant-id}/v1/authentication-devices
POST   /{tenant-id}/v1/authentication-devices
DELETE /{tenant-id}/v1/authentication-devices/{device-id}
```

**実装**:
- [AuthenticationV1Api.java](../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/restapi/authentication/AuthenticationV1Api.java)
- [AuthenticationDeviceV1Api.java](../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/restapi/authentication/device/AuthenticationDeviceV1Api.java)

---

### 5. テナント招待

**情報源**: `libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/restapi/tenant/invitation/`

```
# 招待情報取得
GET /{tenant-id}/v1/invitations/{invitation-id}
```

**実装**: [TenantInvitationMetaDataV1Api.java](../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/restapi/tenant/invitation/TenantInvitationMetaDataV1Api.java)

---

### 6. 内部API（コールバック）

**情報源**: `libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/restapi/internal/`

外部サービスからのコールバック受信用。

```
# 身元確認コールバック
POST /{tenant-id}/internal/v1/identity-verification/callback/{verification-type}/{process}

# 身元確認結果取得（内部用）
GET /{tenant-id}/internal/v1/identity-verification/results/{result-id}
```

**実装**:
- [IdentityVerificationCallbackInternalV1Api.java](../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/restapi/internal/IdentityVerificationCallbackInternalV1Api.java)
- [IdentityVerificationInternalV1Api.java](../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/restapi/internal/IdentityVerificationInternalV1Api.java)

---

## 主要フロー

### Authorization Code Flow（最も一般的）

**実装**: [OAuthV1Api.java](../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/restapi/oauth/OAuthV1Api.java) + [TokenV1Api.java](../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/restapi/token/TokenV1Api.java)

```
1. [ユーザー] アプリでログインボタンをクリック
   ↓
2. [アプリ] idp-serverの認可エンドポイントにリダイレクト
   GET /{tenant-id}/v1/authorizations?response_type=code&client_id=xxx&redirect_uri=https://...&scope=openid profile

   または PAR (Pushed Authorization Request) を使用:
   POST /{tenant-id}/v1/authorizations/push
   → request_uri取得
   GET /{tenant-id}/v1/authorizations?request_uri=urn:ietf:params:oauth:request_uri:xxx
   ↓
3. [idp-server] ログイン画面表示（未認証の場合）
   - パスワード認証（POST /{tenant-id}/v1/authentications/{auth-request-id}）
   - SMS OTP（POST /{tenant-id}/v1/authentications/{auth-request-id}）
   - FIDO2/WebAuthn認証（POST /{tenant-id}/v1/authentications/{auth-request-id}）
   ↓
4. [ユーザー] 認証完了
   ↓
5. [idp-server] Authorization Code発行・リダイレクト
   → 302 Redirect: https://app.example.com/callback?code=abc123

   **重要**: Authorization Codeは5分間有効、ワンタイム使用（使用後即削除）
   ↓
6. [アプリ] codeをAccess Tokenに交換
   POST /{tenant-id}/v1/tokens
   Content-Type: application/x-www-form-urlencoded
   Authorization: Basic base64(client_id:client_secret)

   grant_type=authorization_code&code=abc123&redirect_uri=https://app.example.com/callback
   ↓
7. [idp-server] 検証・トークン発行
   ├─ Authorization Code検証（存在・期限・redirect_uri一致）
   ├─ クライアント認証（client_secret_basic/post/jwt/private_key_jwt/none）
   ├─ Access Token生成（JWT、デフォルト1時間有効）
   ├─ Refresh Token生成（設定による）
   ├─ ID Token生成（OIDCの場合、nonce/at_hash/c_hash含む）
   └─ Authorization Code削除（再使用防止）

   レスポンス:
   {
     "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
     "token_type": "Bearer",
     "expires_in": 3600,
     "refresh_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
     "id_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
     "scope": "openid profile email"
   }
   ↓
8. [アプリ] Access TokenでUserInfo取得
   GET /{tenant-id}/v1/userinfo
   Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...

   レスポンス:
   {
     "sub": "user-12345",
     "name": "John Doe",
     "email": "user@example.com",
     "email_verified": true
   }
```

**フロー詳細**:
- [02. Authorization Flow実装](./02-authorization-flow.md) - ステップ2-5の詳細
- [03. Token Flow実装](./03-token-endpoint.md) - ステップ6-7の詳細
- [04. Authentication実装](./04-authentication.md) - ステップ3の認証詳細

### Client Credentials Flow（サーバー間通信）

**実装**: [TokenV1Api.java](../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/restapi/token/TokenV1Api.java)

**用途**: マイクロサービス間通信、バッチ処理、管理用スクリプト（ユーザーコンテキストなし）

```
1. [アプリ] クライアント認証でトークン取得
   POST /{tenant-id}/v1/tokens
   Content-Type: application/x-www-form-urlencoded
   Authorization: Basic base64(client_id:client_secret)

   grant_type=client_credentials&scope=api:read api:write
   ↓
2. [idp-server] 検証・トークン発行
   ├─ クライアント認証（client_secret_basic/post/jwt/private_key_jwt）
   ├─ スコープ検証（クライアント許可scopeでフィルタリング）
   └─ Access Token生成（subject=client_id、ユーザー情報なし）

   レスポンス:
   {
     "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
     "token_type": "Bearer",
     "expires_in": 3600,
     "scope": "api:read api:write"
   }

   **注意**: Refresh TokenとID Tokenは発行されない（ユーザーコンテキストがないため）
   ↓
3. [アプリ] Access TokenでAPIにアクセス
   GET https://api.example.com/resources
   Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
```

**フロー詳細**: [03. Token Flow実装](./03-token-endpoint.md#2-client-credentials-grant)

### CIBA Flow（プッシュ通知認証）

**実装**: [CibaV1Api.java](../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/restapi/ciba/CibaV1Api.java) + [TokenV1Api.java](../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/restapi/token/TokenV1Api.java)

**用途**: スマートフォンアプリによるバックチャネル認証（ユーザーは別デバイスで承認）

```
1. [アプリ] CIBA認証リクエスト
   POST /{tenant-id}/v1/backchannel/authentications
   Content-Type: application/x-www-form-urlencoded
   Authorization: Basic base64(client_id:client_secret)

   login_hint=user@example.com&binding_message=Code: 1234&scope=openid profile
   ↓
2. [idp-server] auth_req_id返却
   レスポンス:
   {
     "auth_req_id": "8d67dc78-7faa-4d41-aabd-67707b374255",
     "expires_in": 300,
     "interval": 5
   }

   **重要**: auth_req_idは5分間有効（expires_in秒後に期限切れ）
   ↓
3. [idp-server] ユーザーにプッシュ通知送信
   - FCM（Android）
   - APNS（iOS）
   - SMS（フォールバック）

   通知内容: "Code: 1234でログインリクエストがあります。承認しますか？"
   ↓
4. [ユーザー] スマホで承認・拒否
   - スマホアプリで生体認証（FIDO2）
   - 承認 or 拒否ボタンをタップ
   ↓
5. [アプリ] トークン取得（ポーリング or Ping）

   **Poll Mode（ポーリング）**: 定期的にトークンリクエスト
   POST /{tenant-id}/v1/tokens
   Content-Type: application/x-www-form-urlencoded
   Authorization: Basic base64(client_id:client_secret)

   grant_type=urn:openid:params:grant-type:ciba&auth_req_id=8d67dc78-7faa-4d41-aabd-67707b374255

   レスポンス（承認待ち）:
   {
     "error": "authorization_pending",
     "error_description": "The authorization request is still pending"
   }

   レスポンス（承認完了）:
   {
     "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
     "token_type": "Bearer",
     "expires_in": 3600,
     "refresh_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
     "id_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."
   }

   **Ping Mode**: コールバックURLに通知（設定されている場合）
   POST https://client.example.com/ciba-callback
   {
     "auth_req_id": "8d67dc78-7faa-4d41-aabd-67707b374255"
   }
```

**ポーリング間隔**: `interval`秒ごと（デフォルト5秒、レスポンスで指定）
**タイムアウト**: `expires_in`秒後（デフォルト300秒=5分）

---

## 実装パターン

Application Plane APIの実装は**シンプルな委譲パターン**：

```
EntryService（UseCase層）
  ↓ シンプルに委譲
Protocol/Handler（Core層）
  ↓ OAuth/OIDC仕様準拠ロジック
Repository（Adapter層）
```

**Control Planeとの違い**:
- ❌ 権限チェックなし（公開API or トークン検証済み前提）
- ❌ Audit Logなし（必要な場合はCore層で記録）
- ❌ Dry Runなし
- ❌ Context Creatorなし
- ✅ トランザクション管理のみ
- ✅ Core層のProtocol/Interactorへの委譲

---

## 学習の進め方

### Step 1: 概要理解（完了）✅
このドキュメントで以下を理解：
- OAuth/OIDCエンドポイント一覧
- CIBA/UserInfo/Discovery
- エンドユーザーAPI
- 認証インタラクション

### Step 2: Authorization Flow実装（45分）
[02. Authorization Flow実装](./02-authorization-flow.md)

- Authorization Code Flowの実装
- PAR（Pushed Authorization Request）
- EntryServiceとCore層の責務分離

### Step 3: Token Endpoint実装（30分）
[03. Token Endpoint実装](./03-token-endpoint.md)

- トークン発行・検証の実装
- Grant Type別の処理
- Token Introspection/Revocation

### Step 4: 認証・ユーザー情報（50分）
- [04. Authentication実装](./04-authentication.md) - 認証インタラクション（30分）
- [05. UserInfo実装](./05-userinfo.md) - UserInfo実装（20分）

### Step 5: 高度な機能（70分）
- [06. CIBA Flow実装](./06-ciba-flow.md) - バックチャネル認証（40分）
- [07. Identity Verification実装](./07-identity-verification.md) - 身元確認申込み（30分）

### Step 6: 横断的関心事（50分）
- [08. Federation実装](./08-federation.md) - 外部IdP連携
- [09. Events実装](./09-events.md) - SecurityEvent・UserLifecycleEvent
- [10. Client Authentication実装](./10-client-authentication.md) - クライアント認証7方式

---

**最終更新**: 2025-10-13
