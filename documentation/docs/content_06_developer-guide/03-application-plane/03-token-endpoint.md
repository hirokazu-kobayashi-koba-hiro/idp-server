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

**RFC準拠**: OAuth 2.0 (RFC 6749), Token Introspection (RFC 7662), Token Revocation (RFC 7009), DPoP (RFC 9449)

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
│       ├─ DPoP検証（RFC 9449）          │
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
│      ├─ DPoP検証（RFC 9449）           │
│      ├─ AccessToken生成                │
│      ├─ RefreshToken生成               │
│      ├─ IdToken生成（OIDC）            │
│      └─ AuthorizationGranted保存       │
│                                        │
│ refresh_token                          │
│   → RefreshTokenGrantService           │
│      ├─ DPoP検証（RFC 9449）           │
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
│ 5. DPoP検証（RFC 9449）                       │
├─────────────────────────────────────────────┤
│  DPoPProofVerifier.verifyIfNeeded()          │
│    ├─ DPoPヘッダーなし → スキップ（Bearer）   │
│    ├─ DPoPヘッダー空 → invalid_dpop_proof    │
│    └─ DPoPヘッダーあり → JWT検証             │
│        → DPoPProofVerifiedResult             │
└─────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────┐
│ 6. トークン生成                               │
├─────────────────────────────────────────────┤
│  AuthorizationGrant抽出                      │
│    ├─ user, scope, authentication           │
│    ↓                                         │
│  AccessTokenCreator.create()                │
│    ├─ JWT生成（RS256等）                      │
│    ├─ DPoP時: jkt（JWK Thumbprint）埋め込み  │
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
│ 7. AuthorizationGranted登録/更新             │
├─────────────────────────────────────────────┤
│  authorizationGrantedRepository.find()      │
│    - 既存の同意情報を取得                     │
│    ↓                                         │
│  exists? → merge() : register()             │
│    - 同意情報を記録（次回の自動承認用）       │
└─────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────┐
│ 8. クリーンアップ                             │
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

#### DPoP（Demonstrating Proof of Possession）対応

**RFC 9449準拠**

DPoPヘッダーを送信すると、Access Tokenがクライアントの鍵ペアにバインドされます（Sender-Constrained Token）。

**DPoPリクエスト例**:
```bash
curl -X POST "http://localhost:8080/${TENANT_ID}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -H "Authorization: Basic $(echo -n 'client-id:client-secret' | base64)" \
  -H "DPoP: eyJhbGciOiJFUzI1NiIsInR5cCI6ImRwb3Arand0IiwiandrIjp7Imt0eSI6...}" \
  -d "grant_type=authorization_code&code=${CODE}&redirect_uri=${REDIRECT_URI}"
```

**DPoPレスポンス**:
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "DPoP",
  "expires_in": 3600,
  "refresh_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**注意**: `token_type`が`"DPoP"`になります（通常は`"Bearer"`）。

**DPoP検証フロー（GrantService層）**:
```
GrantService.create()
    ↓
DPoPProofVerifier.verifyIfNeeded(dpopProof, httpMethod, httpUri)
    ├─ DPoPヘッダーなし（null/空文字列） → スキップ（Bearer Token）
    ├─ DPoPヘッダー空（""） → DPoPProofInvalidException（400）
    └─ DPoPヘッダーあり → RFC 9449検証（12項目）
        → DPoPProofVerifiedResult（JWK Thumbprint含む）
    ↓
AccessTokenCreator.create(..., dpopResult)
    └─ DPoP時: Access TokenにjktクレームとしてJWK Thumbprintを埋め込み
```

**重要**: DPoP検証はAccessTokenCreatorではなくGrantService層で実行されます。AccessTokenCreatorは検証済み結果（`DPoPProofVerifiedResult`）のみを受け取ります。

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
│  - ユーザー存在チェック                        │
│  - ユーザーステータスチェック（Issue #900）     │
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

#### 1.4.4 Resource Owner Password Credentials Grant

**用途**

**ユーザー認証** - ユーザー名とパスワードで直接トークン取得（レガシーシステム移行用）

**使用場面**:
- レガシーシステムからの移行期間中
- 信頼できるファーストパーティアプリケーション

⚠️ **注意**: OAuth 2.1では非推奨。新規実装では使用を避けてください。

### リクエスト例

**実装**: [ResourceOwnerPasswordGrantService.java](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/token/service/ResourceOwnerPasswordGrantService.java)

```bash
curl -X POST "http://localhost:8080/${TENANT_ID}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -H "Authorization: Basic $(echo -n 'client-id:client-secret' | base64)" \
  -d "grant_type=password&username=${USERNAME}&password=${PASSWORD}&scope=openid"
```

**レスポンス**:
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "refresh_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "scope": "openid"
}
```

### 処理フロー

```
ResourceOwnerPasswordGrantService.create()
    ↓
┌─────────────────────────────────────────────┐
│ 1. Validator                                │
│    実装: ResourceOwnerPasswordGrantValidator │
├─────────────────────────────────────────────┤
│  - username必須チェック                       │
│  - password必須チェック                       │
└─────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────┐
│ 2. ユーザー認証                              │
├─────────────────────────────────────────────┤
│  ResourceOwnerPasswordGrantDelegate         │
│    .authenticate(username, password)        │
│  - パスワード検証                             │
│  - ユーザー取得                              │
└─────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────┐
│ 3. Verifier検証                              │
│    実装: ResourceOwnerPasswordGrantVerifier  │
├─────────────────────────────────────────────┤
│  - ユーザー存在チェック                        │
│  - ユーザーステータスチェック（Issue #900）     │
│  - スコープ検証                               │
└─────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────┐
│ 4. トークン生成                               │
├─────────────────────────────────────────────┤
│  AccessTokenCreator.create()                │
│  RefreshTokenCreator.create()               │
│  IdTokenCreator.createIdToken() (OIDC時)    │
└─────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────┐
│ 5. OAuthToken保存                            │
├─────────────────────────────────────────────┤
│  oAuthTokenCommandRepository.register()     │
└─────────────────────────────────────────────┘
```

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

## 4. トークン関連の設定

トークンエンドポイントの動作に影響を与える重要な設定項目について説明します。

### 4.1 Access Tokenタイプ（opaque vs JWT）

`authorization_server.extension.access_token_type` で、発行されるAccess Tokenの形式を制御します。

| 設定値 | 形式 | 説明 |
|--------|------|------|
| **opaque**（デフォルト） | ランダム文字列 | トークン自体に情報を含まない。リソースサーバーは**Introspectionエンドポイント**を呼び出して検証する必要がある |
| **jwt** | header.payload.signature | トークン自体にクレーム情報を含む。リソースサーバーは**JWKSを取得してローカルで署名検証**できる |

**使い分けの指針**:

- **opaque**: 即時失効が必要なケース（金融系など）。Revocationすると即座に無効化される（Introspection時にDBを参照するため）
- **jwt**: パフォーマンス重視のケース。ネットワークコールなしでトークン検証が可能。ただし、Revocation後もトークンの有効期限まで使用可能な場合がある

```json
{
  "authorization_server": {
    "extension": {
      "access_token_type": "JWT"
    }
  }
}
```

---

### 4.2 id_token_strict_mode

`authorization_server.extension.id_token_strict_mode` で、ID Tokenに含まれるクレームの制御方式を切り替えます。

| 設定値 | 動作 |
|--------|------|
| **false**（デフォルト） | scopeに基づくクレームをID Tokenに含める（例: `scope=profile` → `name`, `family_name`等） |
| **true** | `claims`パラメータで `"essential": true` を指定したクレームのみID Tokenに含める |

**`true`に設定した場合の影響**:

- ID Tokenには、`claims`パラメータの`id_token`セクションで`essential: true`と明示的にリクエストされたクレームのみが含まれる
- scopeベースのクレーム自動付与は行われない
- `claims:*` プレフィックス付きカスタムスコープによるUserInfoクレームの動作にも影響する

```json
{
  "authorization_server": {
    "extension": {
      "id_token_strict_mode": true
    }
  }
}
```

**`claims`パラメータでのリクエスト例**（strict mode時）:

```
claims={"id_token":{"email":{"essential":true},"name":{"essential":true}}}
```

この場合、ID Tokenには`email`と`name`のみが含まれます。

---

### 4.3 scopes_supported と claims_supported の違い

トークン発行に影響する2つの設定の違いを理解することが重要です。

| 設定 | 役割 | トークン発行への影響 |
|------|------|-------------------|
| **scopes_supported** | Discovery（`.well-known/openid-configuration`）での表示用 | **影響なし** - 実際のスコープ処理には関与しない |
| **claims_supported** | 認可グラント作成時のクレームフィルタリング | **直接影響あり** - `GrantIdTokenClaims`/`GrantUserinfoClaims` でフィルタされる |

**重要な注意点**:

- `scopes_supported` はDiscoveryエンドポイントで対応スコープを公開するための設定であり、実際のスコープ受付や処理には影響しません
- `claims_supported` に含まれないクレームは、認可グラント作成時に除外されるため、ID TokenやUserInfoレスポンスに含まれません
- カスタムスコープ（例: `api:read`）はリソースアクセスの権限制御に使用されるものであり、UserInfoのクレームには影響しません。UserInfoのクレームを制御するのはOIDC標準スコープ（`profile`, `email`, `address`, `phone`）です

**設定例**:

```json
{
  "authorization_server": {
    "scopes_supported": ["openid", "profile", "email", "api:read"],
    "claims_supported": [
      "sub", "name", "family_name", "given_name",
      "email", "email_verified"
    ]
  }
}
```

この設定では:
- Discovery: `openid`, `profile`, `email`, `api:read` が対応スコープとして公開される
- 実際のクレーム: `claims_supported` に `address` 関連のクレームがないため、`scope=address` を指定してもアドレス情報はID Token/UserInfoに含まれない
- `api:read` スコープ: Access Tokenの`scope`クレームに反映されるが、UserInfoのクレームには影響しない

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

### エラー4: `invalid_grant` - ユーザーが無効状態（Issue #900）

**実際のエラー**:
```json
{
  "error": "invalid_grant",
  "error_description": "user is not active (id: user-12345, status: LOCKED)"
}
```

**原因**: トークン発行時にユーザーが無効状態になっている

**対象Grant Type**:
- **Refresh Token Grant**: トークン更新時にユーザーステータスをチェック
- **Password Grant**: ユーザー認証時にユーザーステータスをチェック

**無効と判定されるステータス**:
| ステータス | 説明 | 発生ケース |
|-----------|------|-----------|
| **LOCKED** | アカウントロック | 認証失敗回数超過 |
| **DISABLED** | 無効化 | 管理者による無効化 |
| **SUSPENDED** | 一時停止 | ポリシー違反等 |
| **DEACTIVATED** | 非アクティブ化 | ユーザーによる退会申請 |
| **DELETED_PENDING** | 削除待ち | 削除処理中 |
| **DELETED** | 削除済み | 完全削除 |

**実装詳細**:
- Refresh Token Grant: [RefreshTokenVerifier.java](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/token/verifier/RefreshTokenVerifier.java) の `throwExceptionIfInactiveUser()`
- Password Grant: [ResourceOwnerPasswordGrantVerifier.java](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/token/verifier/ResourceOwnerPasswordGrantVerifier.java) の `throwExceptionIfInactiveUser()`

**セキュリティ考慮事項**:
- **Authorization Code Grant では未チェック**: 認可時点でユーザー認証済みのため、トークン交換時の再チェックは不要（Keycloakと同じアプローチ）
- **Refresh Token Grant でのチェック理由**: 長期間有効なRefresh Tokenの場合、その間にユーザーが無効化される可能性がある

**解決策**:
```bash
# ユーザーステータス確認
docker exec -it postgres psql -U idp_user -d idp_db -c \
  "SELECT id, status FROM idp_user WHERE id='${USER_ID}';"

# 有効なステータス（ACTIVE）でない場合、管理者によるステータス変更が必要
# または新しいユーザーで認証をやり直す
```

---

### エラー5: `invalid_dpop_proof` - DPoP証明が不正（RFC 9449）

**実際のエラー**:
```json
{
  "error": "invalid_dpop_proof",
  "error_description": "DPoP header is present but empty"
}
```

**原因**:
1. **DPoPヘッダーが空**: ヘッダーは存在するが値が空
2. **DPoP JWTの署名不正**: 署名検証に失敗
3. **DPoP JWTの必須クレーム欠落**: `jti`, `htm`, `htu`, `iat`が不足
4. **DPoP JWTの`htm`/`htu`不一致**: リクエストのHTTPメソッド/URIと不一致
5. **DPoP JWTの有効期限切れ**: `iat`が許容範囲外

**実装詳細**:
- DPoP検証は**GrantService層**で`DPoPProofVerifier.verifyIfNeeded()`により実行
- 空DPoPヘッダーの検出: `DPoPProof.isPresentButEmpty()`
- 例外: `DPoPProofInvalidException` → HTTP 400

**解決策**:
```bash
# DPoPヘッダーを送信する場合は、正しいDPoP JWT Proofを生成すること
# DPoPが不要な場合は、DPoPヘッダー自体を送信しない（Bearerトークンになる）

# ✅ DPoPなし（Bearer Token）
curl -X POST "http://localhost:8080/${TENANT_ID}/v1/tokens" \
  -H "Authorization: Basic $(echo -n 'client-id:client-secret' | base64)" \
  -d "grant_type=authorization_code&code=${CODE}&redirect_uri=${REDIRECT_URI}"

# ✅ DPoPあり（DPoP Token）
curl -X POST "http://localhost:8080/${TENANT_ID}/v1/tokens" \
  -H "Authorization: Basic $(echo -n 'client-id:client-secret' | base64)" \
  -H "DPoP: eyJhbGciOiJFUzI1NiIsInR5cCI6ImRwb3Arand0IiwiandrIjp7...}" \
  -d "grant_type=authorization_code&code=${CODE}&redirect_uri=${REDIRECT_URI}"

# ❌ DPoPヘッダーが空（400エラー）
curl -X POST "http://localhost:8080/${TENANT_ID}/v1/tokens" \
  -H "DPoP: " \
  -d "..."
```

---

## 次のステップ

✅ Token Flowの実装を理解した！

### 📖 次に読むべきドキュメント

1. [04. Authentication実装](./04-authentication.md) - ユーザー認証
2. [05. UserInfo実装](./05-userinfo.md) - ユーザー情報取得

### 🔗 詳細情報

- [RFC 6749 Section 3.2](https://datatracker.ietf.org/doc/html/rfc6749#section-3.2) - Token Endpoint
- [RFC 7662](https://datatracker.ietf.org/doc/html/rfc7662) - Token Introspection

---

**情報源**: [TokenEntryService.java](../../../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/application/enduser/TokenEntryService.java)
**最終更新**: 2026-03-12
