# Token Endpoint実装ガイド

## このドキュメントの目的

**トークンエンドポイント**（トークン発行・検証・失効）の実装を理解することが目標です。

### 所要時間
⏱️ **約30分**

### 前提知識
- [02. Authorization Flow](./02-authorization-flow.md)
- OAuth 2.0基礎知識

---

## Token Endpointとは

OAuth 2.0の**トークンエンドポイント（Token Endpoint）** は、トークンに関する3つの機能を提供します：

1. **Token Request**: トークン発行（RFC 6749 Section 3.2）
2. **Token Introspection**: トークン検証（RFC 7662）
3. **Token Revocation**: トークン失効（RFC 7009）

**RFC準拠**: OAuth 2.0 (RFC 6749), Token Introspection (RFC 7662), Token Revocation (RFC 7009)

---

## エンドポイント一覧

```
# トークン発行（RFC 6749）
POST /{tenant-id}/v1/tokens

# トークンイントロスペクション（RFC 7662）
POST /{tenant-id}/v1/tokens/introspection

# トークン失効（RFC 7009）
POST /{tenant-id}/v1/tokens/revocation
```

**実装**: [TokenV1Api.java](../../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/restapi/token/TokenV1Api.java)

---

## 実装アーキテクチャ全体像

### 30秒で理解する全体像

```
HTTPリクエスト
    ↓
Controller (TokenV1Api) - HTTP処理
    ↓
EntryService (TokenEntryService) - トランザクション管理
    ↓
Core層 (TokenProtocol → TokenRequestHandler)
    ├─ Validator: 入力形式チェック
    ├─ クライアント認証（5種類）
    ├─ Grant Type別Service選択（4種類+拡張）
    └─ トークン発行
    ↓
Repository - データ永続化
```

### 主要クラスの責務

| クラス | 役割 | 主な処理 |
|--------|------|---------|
| **TokenV1Api** | HTTPエンドポイント | パラメータ受け取り、レスポンス返却 |
| **TokenEntryService** | オーケストレーション | トランザクション、イベント発行 |
| **TokenRequestHandler** | トークン発行処理 | Validator、クライアント認証、Service選択 |
| **ClientAuthenticationHandler** | クライアント認証 | 5種類の認証方式から選択・実行 |
| **OAuthTokenCreationServices** | Grant Type振り分け | 4種類+拡張からService選択 |

### 主要ドメインオブジェクト

| オブジェクト | 説明 | 保存場所 | 有効期限 |
|-------------|------|---------|---------|
| **OAuthToken** | Access Token/Refresh Token | DB | 設定による（デフォルト1時間） |
| **AuthorizationCodeGrant** | Authorization Code | Redis + DB | 5分（使用後即無効） |
| **ClientCredentials** | クライアント認証情報 | - | - |

---

## 1. Token Request（トークン発行）

トークンエンドポイントの最も重要な機能は**トークン発行**です。Authorization Codeやクライアント認証情報を使ってAccess Token/Refresh Token/ID Tokenを発行します。

### 1.1 処理フロー全体

**実装**: [TokenRequestHandler.java:82-120](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/token/handler/token/TokenRequestHandler.java#L82-L120)

```
POST /{tenant-id}/v1/tokens
    ↓
TokenRequestHandler.handle()
    ↓
┌────────────────────────────────────────┐
│ 1. Validator（入力形式チェック）          │
│    - grant_type必須                     │
│    - パラメータ形式チェック               │
└────────────────────────────────────────┘
    ↓
┌────────────────────────────────────────┐
│ 2. 設定取得                             │
│    - AuthorizationServerConfiguration  │
│    - ClientConfiguration               │
└────────────────────────────────────────┘
    ↓
┌────────────────────────────────────────┐
│ 3. クライアント認証                      │
│    → ClientAuthenticationHandler       │
│       └─ 5種類から選択・実行            │
└────────────────────────────────────────┘
    ↓
┌────────────────────────────────────────┐
│ 4. Grant Type別Service選択              │
│    → OAuthTokenCreationServices        │
│       └─ 4種類+拡張から選択             │
└────────────────────────────────────────┘
    ↓
┌────────────────────────────────────────┐
│ 5. トークン発行                         │
│    → Service.create()                  │
│       ├─ Authorization Code検証        │
│       ├─ Access Token生成              │
│       ├─ Refresh Token生成             │
│       ├─ ID Token生成（OIDC）          │
│       └─ DB保存                        │
└────────────────────────────────────────┘
    ↓
TokenRequestResponse
```

---

### 1.2 クライアント認証（Client Authentication）

**詳細**: [10. Client Authentication実装](./10-client-authentication.md) - 7つの認証方式の完全ガイド

### 標準5種類 + FAPI拡張2種類

**実装**: [ClientAuthenticators.java:32-40](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/clientauthenticator/ClientAuthenticators.java#L32-L40)

#### 標準認証方式（5種類）

| 認証方式 | 送信方法 | 用途 | セキュリティレベル |
|---------|---------|------|----------------|
| **client_secret_basic** | Basic認証ヘッダー | 最も一般的 | ⭐⭐ |
| **client_secret_post** | POSTボディ | レガシー | ⭐ |
| **client_secret_jwt** | JWT署名（共有鍵） | 高セキュリティ | ⭐⭐⭐ |
| **private_key_jwt** | JWT署名（秘密鍵） | 最高セキュリティ | ⭐⭐⭐⭐ |
| **none** | 認証なし | パブリッククライアント（SPA/Mobile+PKCE） | - |

#### FAPI拡張認証方式（2種類）

**実装**: `libs/idp-server-core-extension-fapi/src/main/java/org/idp/server/core/openid/extension/fapi/`

| 認証方式 | 送信方法 | 用途 | セキュリティレベル |
|---------|---------|------|----------------|
| **tls_client_auth** | クライアント証明書（MTLS） | FAPI準拠・金融機関 | ⭐⭐⭐⭐⭐ |
| **self_signed_tls_client_auth** | 自己署名証明書（MTLS） | FAPI準拠・開発環境 | ⭐⭐⭐⭐ |

### client_secret_basic の例

```bash
# Authorization ヘッダーにBase64エンコード
Authorization: Basic base64(client_id:client_secret)

# 実際のリクエスト
curl -X POST "http://localhost:8080/${TENANT_ID}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -H "Authorization: Basic $(echo -n 'my-client:my-secret' | base64)" \
  -d "grant_type=authorization_code&code=${CODE}&redirect_uri=${REDIRECT_URI}"
```

### client_secret_post の例

```bash
# POSTボディにclient_id/client_secretを含める
curl -X POST "http://localhost:8080/${TENANT_ID}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code&code=${CODE}&redirect_uri=${REDIRECT_URI}&client_id=my-client&client_secret=my-secret"
```

### クライアント認証の処理フロー

```
TokenRequestHandler.handle()
    ↓
TokenRequestContext作成
    ├─ clientSecretBasic (Authorizationヘッダーから抽出)
    ├─ clientCert (MTLSヘッダーから抽出)
    └─ parameters (POSTボディ)
    ↓
ClientAuthenticationHandler.authenticate(context)
    ↓
┌──────────────────────────────────────────┐
│ ClientAuthenticators.get(認証タイプ)       │
├──────────────────────────────────────────┤
│  client_secret_basic                     │
│    → ClientSecretBasicAuthenticator      │
│       └─ Base64デコード → 検証           │
│                                          │
│  client_secret_post                      │
│    → ClientSecretPostAuthenticator       │
│       └─ POSTボディから抽出 → 検証       │
│                                          │
│  client_secret_jwt                       │
│    → ClientSecretJwtAuthenticator        │
│       └─ JWT検証（HMAC署名）             │
│                                          │
│  private_key_jwt                         │
│    → PrivateKeyJwtAuthenticator          │
│       └─ JWT検証（RSA/ECDSA署名）        │
│                                          │
│  none                                    │
│    → PublicClientAuthenticator           │
│       └─ PKCE必須チェック                │
│                                          │
│  + FAPI拡張（プラグイン）                 │
│                                          │
│  tls_client_auth                         │
│    → TlsClientAuthAuthenticator          │
│       └─ クライアント証明書検証（MTLS）   │
│                                          │
│  self_signed_tls_client_auth             │
│    → SelfSignedTlsClientAuthAuthenticator│
│       └─ 自己署名証明書検証（MTLS）       │
└──────────────────────────────────────────┘
    ↓
ClientCredentials（認証済み情報）

※ FAPIモジュールがロードされている場合のみMTLS認証が有効
```

---

### 1.3 Grant Type別のService

### 4種類の標準Grant Type

**実装**: [OAuthTokenCreationServices.java:43-56](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/token/service/OAuthTokenCreationServices.java#L43-L56)

```
OAuthTokenCreationServices.get(grantType)
    ↓
┌────────────────────────────────────────┐
│ Grant Typeで振り分け                    │
├────────────────────────────────────────┤
│ authorization_code                     │
│   → AuthorizationCodeGrantService      │
│      ├─ Validator（code必須等）        │
│      ├─ AuthorizationCodeGrant取得     │
│      ├─ Verifier（used, 期限, URI）    │
│      ├─ AccessToken生成                │
│      ├─ RefreshToken生成               │
│      ├─ IdToken生成（OIDC）            │
│      └─ AuthorizationGranted保存       │
│                                        │
│ refresh_token                          │
│   → RefreshTokenGrantService           │
│      └─ 新しいAccess Token発行         │
│                                        │
│ password                               │
│   → ResourceOwnerPasswordCredentials   │
│      └─ ユーザー認証・トークン発行      │
│                                        │
│ client_credentials                     │
│   → ClientCredentialsGrantService      │
│      └─ クライアント権限でトークン発行   │
│                                        │
│ + 拡張Grant Type（プラグイン）           │
│   例: urn:openid:params:grant-type:ciba │
└────────────────────────────────────────┘
```

### AuthorizationCodeGrantService の処理詳細

**実装場所**: [AuthorizationCodeGrantService.java:127-203](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/token/service/AuthorizationCodeGrantService.java#L127-L203)

```
AuthorizationCodeGrantService.create(tokenRequestContext, clientCredentials)
    ↓
┌─────────────────────────────────────────────┐
│ 1. Validator（入力形式チェック）               │
│    実装: TokenRequestCodeGrantValidator      │
├─────────────────────────────────────────────┤
│  - code必須チェック                           │
└─────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────┐
│ 2. AuthorizationCodeGrant取得                │
├─────────────────────────────────────────────┤
│  authorizationCodeGrantRepository.find()    │
│  - 存在しない → invalid_grant エラー         │
└─────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────┐
│ 3. AuthorizationRequest取得                  │
├─────────────────────────────────────────────┤
│  authorizationRequestRepository.find()      │
│  - 元の認可リクエスト情報（scope, nonce等）   │
└─────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────┐
│ 4. Verifier（ビジネスルール検証）             │
│    実装: AuthorizationCodeGrantVerifier      │
├─────────────────────────────────────────────┤
│  Base Verifier選択（OAuth2 or OIDC）         │
│    ├─ 有効期限チェック                        │
│    ├─ used=false チェック                    │
│    ├─ redirect_uri完全一致                   │
│    ├─ クライアント一致                        │
│    └─ PKCE検証（該当時）                      │
│                                             │
│  Extension Verifiers（該当時のみ）            │
│    └─ プラグインロード拡張検証                │
└─────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────┐
│ 5. トークン生成                               │
├─────────────────────────────────────────────┤
│  AuthorizationGrant抽出                      │
│    ├─ user, scope, authentication           │
│    ↓                                         │
│  AccessTokenCreator.create()                │
│    ├─ JWT生成（RS256等）                      │
│    └─ 有効期限設定（デフォルト1時間）          │
│    ↓                                         │
│  RefreshTokenCreator.create()               │
│    └─ 有効期限設定（設定による）              │
│    ↓                                         │
│  if (OIDC)                                  │
│    IdTokenCreator.createIdToken()           │
│      ├─ nonce含める                          │
│      ├─ at_hash, c_hash計算                  │
│      └─ カスタムクレーム追加                  │
│    ↓                                         │
│  OAuthTokenBuilder.build()                  │
│    └─ 全トークンを1つのOAuthTokenに統合       │
└─────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────┐
│ 6. AuthorizationGranted登録/更新             │
├─────────────────────────────────────────────┤
│  authorizationGrantedRepository.find()      │
│    - 既存の同意情報を取得                     │
│    ↓                                         │
│  exists? → merge() : register()             │
│    - 同意情報を記録（次回の自動承認用）       │
└─────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────┐
│ 7. クリーンアップ                             │
├─────────────────────────────────────────────┤
│  oAuthTokenCommandRepository.register()     │
│    - OAuthToken保存                          │
│    ↓                                         │
│  authorizationCodeGrantRepository.delete()  │
│    - Authorization Code削除（使用済み）      │
│    ↓                                         │
│  authorizationRequestRepository.delete()    │
│    - AuthorizationRequest削除（不要）        │
└─────────────────────────────────────────────┘
    ↓
OAuthToken（Access/Refresh/ID Token）
```

**RFC 6749 Section 4.1.3準拠の実装**

---

### 1.4 各Grant Typeの詳細実装

#### 1.4.1 Authorization Code Grant（最重要）

**用途**: ユーザー認証後にトークン取得（最も一般的なフロー）

**実装**: [AuthorizationCodeGrantService.java:127-203](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/token/service/AuthorizationCodeGrantService.java#L127-L203)

**リクエスト例**:
```bash
curl -X POST "http://localhost:8080/${TENANT_ID}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -H "Authorization: Basic $(echo -n 'client-id:client-secret' | base64)" \
  -d "grant_type=authorization_code&code=${CODE}&redirect_uri=${REDIRECT_URI}"
```

**レスポンス**:
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "refresh_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "id_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "scope": "openid profile email"
}
```

**処理の詳細**: 上記「AuthorizationCodeGrantService の処理詳細」セクション参照

---

#### 1.4.2 Client Credentials Grant

**用途

**サーバー間通信**（ユーザーなし）- バックエンドサービスがAPIにアクセス

**使用場面**:
- マイクロサービス間通信
- バッチ処理
- 管理用スクリプト

### リクエスト例

**実装**: [ClientCredentialsGrantService.java:49-88](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/token/service/ClientCredentialsGrantService.java#L49-L88)

```bash
curl -X POST "http://localhost:8080/${TENANT_ID}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -H "Authorization: Basic $(echo -n 'client-id:client-secret' | base64)" \
  -d "grant_type=client_credentials&scope=api:read api:write"
```

**レスポンス**:
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "api:read api:write"
}
```

**注意**: Refresh TokenとID Tokenは発行されない（ユーザーコンテキストがないため）

### 処理フロー

```
ClientCredentialsGrantService.create()
    ↓
┌─────────────────────────────────────────────┐
│ 1. Validator                                │
│    実装: ClientCredentialsGrantValidator     │
├─────────────────────────────────────────────┤
│  - scope形式チェック                          │
└─────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────┐
│ 2. Scope検証                                 │
│    実装: ClientCredentialsGrantVerifier      │
├─────────────────────────────────────────────┤
│  - クライアント許可scopeでフィルタリング       │
│  - 空なら例外                                 │
└─────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────┐
│ 3. AuthorizationGrant作成                    │
├─────────────────────────────────────────────┤
│  - subject: なし（クライアント自身）          │
│  - scope: フィルタリング済みscope             │
│  - grant_type: client_credentials           │
└─────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────┐
│ 4. AccessToken生成                           │
├─────────────────────────────────────────────┤
│  AccessTokenCreator.create()                │
│  - subjectはclient_id                        │
│  - scopeに基づく権限設定                      │
└─────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────┐
│ 5. OAuthToken保存                            │
├─────────────────────────────────────────────┤
│  oAuthTokenCommandRepository.register()     │
└─────────────────────────────────────────────┘
```

---

#### 1.4.3 Refresh Token Grant

**用途

**Access Token更新** - 有効期限切れ前に新しいAccess Tokenを取得

**使用場面**:
- Access Token期限切れ時
- ユーザー再認証なしでトークン更新

### リクエスト例

**実装**: [RefreshTokenGrantService.java:53-90](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/token/service/RefreshTokenGrantService.java#L53-L90)

```bash
curl -X POST "http://localhost:8080/${TENANT_ID}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -H "Authorization: Basic $(echo -n 'client-id:client-secret' | base64)" \
  -d "grant_type=refresh_token&refresh_token=${REFRESH_TOKEN}"
```

**レスポンス**:
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",  // 新しいAccess Token
  "token_type": "Bearer",
  "expires_in": 3600,
  "refresh_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",  // 新しいRefresh Token
  "scope": "openid profile email"
}
```

### 処理フロー

```
RefreshTokenGrantService.create()
    ↓
┌─────────────────────────────────────────────┐
│ 1. Validator                                │
│    実装: RefreshTokenGrantValidator          │
├─────────────────────────────────────────────┤
│  - refresh_token必須チェック                  │
└─────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────┐
│ 2. 元のOAuthToken取得                        │
├─────────────────────────────────────────────┤
│  oAuthTokenQueryRepository.find()           │
│  - Refresh Tokenに紐づくOAuthToken取得       │
└─────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────┐
│ 3. Verifier検証                              │
│    実装: RefreshTokenVerifier                │
├─────────────────────────────────────────────┤
│  - Refresh Token有効期限チェック              │
│  - クライアント一致チェック                    │
└─────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────┐
│ 4. 新しいトークン生成                          │
├─────────────────────────────────────────────┤
│  AccessTokenCreator.refresh()               │
│    - 新しいAccess Token生成                  │
│    ↓                                         │
│  RefreshTokenCreator.refresh()              │
│    - 新しいRefresh Token生成                 │
│    - または既存のRefresh Tokenを再利用       │
└─────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────┐
│ 5. トークン入れ替え                            │
├─────────────────────────────────────────────┤
│  oAuthTokenCommandRepository.delete(旧)     │
│  oAuthTokenCommandRepository.register(新)   │
└─────────────────────────────────────────────┘
```

**重要**: 元のOAuthTokenは削除され、新しいOAuthTokenに置き換わる

---

## 2. Token Introspection（トークン検証）

**RFC 7662準拠**

### リクエスト

```
POST /{tenant-id}/v1/tokens/introspection
Content-Type: application/x-www-form-urlencoded
Authorization: Basic base64(client_id:client_secret)

token=eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
```

### レスポンス

```json
{
  "active": true,
  "scope": "openid profile email",
  "client_id": "test-client",
  "username": "user@example.com",
  "token_type": "Bearer",
  "exp": 1695555600,
  "iat": 1695552000,
  "sub": "user-12345"
}
```

### EntryService実装

**実装**: [TokenEntryService.java:83](../../../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/application/enduser/TokenEntryService.java#L83)

```java
@Override
public TokenIntrospectionResponse inspect(
    TenantIdentifier tenantIdentifier,
    Map<String, String[]> params,
    String authorizationHeader,
    String clientCert,
    RequestAttributes requestAttributes) {

  // 1. Tenant取得
  Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

  // 2. TokenIntrospectionRequest作成
  TokenIntrospectionRequest tokenIntrospectionRequest =
      new TokenIntrospectionRequest(tenant, authorizationHeader, params);
  tokenIntrospectionRequest.setClientCert(clientCert);

  // 3. Core層に委譲
  TokenProtocol tokenProtocol = tokenProtocols.get(tenant.authorizationProvider());
  TokenIntrospectionResponse result = tokenProtocol.inspect(tokenIntrospectionRequest);

  // 4. イベント発行
  if (result.hasOAuthToken()) {
    eventPublisher.publish(
        tenant, result.oAuthToken(), result.securityEventType(), requestAttributes);
  }

  return result;
}
```

---

## 3. Token Revocation（トークン失効）

**RFC 7009準拠**

### リクエスト

```
POST /{tenant-id}/v1/tokens/revocation
Content-Type: application/x-www-form-urlencoded
Authorization: Basic base64(client_id:client_secret)

token=eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...&
token_type_hint=access_token
```

### レスポンス

```
HTTP/1.1 200 OK
```

**注意**: RFC 7009により、成功時はボディなし（200 OKのみ）

---

## E2Eテストの書き方

### 実際のテストファイル

**参考**: `e2e/src/tests/scenario/application/scenario-02-sso-oidc.test.js`

実際のE2Eテストでは、以下のシナリオをカバーしています：
- テナント・クライアント・ユーザーのセットアップ
- Authorization Code Flow全体
- トークン発行・検証・失効

### テスト時のチェックポイント

```javascript
// ✅ 確認すべきこと
expect(response.status).toBe(200);
expect(response.data).toHaveProperty('access_token');
expect(response.data).toHaveProperty('token_type', 'Bearer');
expect(response.data).toHaveProperty('expires_in');

// OIDC時
expect(response.data).toHaveProperty('id_token');

// Refresh Token発行時
expect(response.data).toHaveProperty('refresh_token');
```

### テスト実行

```bash
cd e2e
npm test -- scenario-02-sso-oidc.test.js
```

---

## よくあるエラーと対処法

### エラー1: `invalid_client` - クライアント認証失敗

**実際のエラー**:
```json
{
  "error": "invalid_client",
  "error_description": "Client authentication failed"
}
```

**原因**:
- client_id/client_secretが不正
- Basic認証のBase64エンコードミス
- Authorizationヘッダーとボディでclient_idが異なる

**解決策**:
```bash
# ✅ 正しい（client_secret_basic）
curl -X POST "http://localhost:8080/${TENANT_ID}/v1/tokens" \
  -H "Authorization: Basic $(echo -n 'my-client:my-secret' | base64)" \
  -d "grant_type=authorization_code&code=${CODE}&redirect_uri=${REDIRECT_URI}"

# ❌ 間違い: -nオプション忘れ（改行が入る）
echo 'my-client:my-secret' | base64

# ✅ 正しい（client_secret_post）
curl -X POST "http://localhost:8080/${TENANT_ID}/v1/tokens" \
  -d "grant_type=authorization_code&code=${CODE}&client_id=my-client&client_secret=my-secret&redirect_uri=${REDIRECT_URI}"
```

---

### エラー2: `invalid_grant` - Authorization Code不正

**実際のエラー**:
```json
{
  "error": "invalid_grant",
  "error_description": "not found authorization code."
}
```

**原因**:
1. **Authorization Codeが既に使用済み** （使用後即削除される）
2. **Authorization Codeが期限切れ** （5分経過で自動削除）
3. **Authorization Codeが存在しない** （誤ったコード）
4. **redirect_uriの不一致**

**実装詳細**:
このシステムでは`used`フラグではなく、**使用後即削除する設計**を採用しています。

- トークン発行成功 → Authorization Code削除（[AuthorizationCodeGrantService.java:199](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/token/service/AuthorizationCodeGrantService.java#L199)）
- 再使用試行 → レコード不存在 → `invalid_grant`エラー
- セキュリティ: used/expired/存在しない を区別しない（攻撃者に情報を与えない）

**解決策**:
```bash
# 1. Authorization Codeの存在確認
docker exec -it postgres psql -U idp_user -d idp_db -c \
  "SELECT code, expires_at, redirect_uri FROM authorization_code_grant WHERE code='${CODE}';"

# 2. レコードが存在しない場合 → 最初からやり直し（Authorization Requestから再実行）
# 3. expires_at < now の場合 → 最初からやり直し
# 4. redirect_uri不一致 → 正しいURIを指定

# Authorization Requestで指定したredirect_uriと完全一致必須
redirect_uri=https://app.example.com/callback  # ✅
redirect_uri=https://app.example.com/callback/ # ❌ 末尾スラッシュ
```

**重要**: Authorization Codeは**ワンタイム使用**です。一度使用すると物理的に削除されるため、再使用は不可能です。

---

### エラー3: `unsupported_grant_type`

**実際のエラー**:
```json
{
  "error": "unsupported_grant_type",
  "error_description": "unsupported grant_type (password)"
}
```

**原因**: テナント設定でGrant Typeが無効化されている

**解決策**:
```bash
# サポートされているgrant_typeを確認
curl -X GET "http://localhost:8080/${TENANT_ID}/.well-known/openid-configuration" \
  | jq '.grant_types_supported'

# 出力例:
# ["authorization_code", "refresh_token", "client_credentials"]

# passwordが含まれていない → テナント設定で無効化されている
# Management APIで有効化が必要
```

---

## 次のステップ

✅ Token Flowの実装を理解した！

### 📖 次に読むべきドキュメント

1. [04. Authentication実装](./04-authentication.md) - ユーザー認証
2. [05. UserInfo実装](./05-userinfo.md) - ユーザー情報取得

### 🔗 詳細情報

- [AI開発者向け: Core - Token](../../../content_10_ai_developer/ai-11-core.md#token---トークンドメイン)
- [RFC 6749 Section 3.2](https://datatracker.ietf.org/doc/html/rfc6749#section-3.2) - Token Endpoint
- [RFC 7662](https://datatracker.ietf.org/doc/html/rfc7662) - Token Introspection

---

**情報源**: [TokenEntryService.java](../../../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/application/enduser/TokenEntryService.java)
**最終更新**: 2025-10-12
