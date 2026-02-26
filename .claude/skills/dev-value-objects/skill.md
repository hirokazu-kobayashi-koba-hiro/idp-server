---
name: dev-value-objects
description: 値オブジェクト（Value Object）カタログ。コード実装時に適切な型を素早く見つけるためのリファレンス。型安全なコードを書く際に参照。
---

# 値オブジェクト（Value Object）カタログ

## 共通パターン

すべての値オブジェクトは以下の共通パターンに従う:

- **immutable**: `final` フィールド
- **`value()`**: 内部値を返すアクセサ
- **`exists()`**: null/空でないかの判定
- **`equals()` / `hashCode()`**: 値による同一性判定
- **`UuidConvertable`**: UUID変換可能な識別子は `toUuid()` を実装

## 探索起点

```
libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/type/
libs/idp-server-platform/src/main/java/org/idp/server/platform/multi_tenancy/
```

---

## OAuth 2.0 コア

**パッケージ**: `core.openid.oauth.type.oauth`

| クラス名 | 内部型 | 用途 |
|---------|--------|------|
| `AuthorizationCode` | `String` | 認可コード |
| `Scopes` | `Set<String>` | スコープ集合（スペース区切り文字列パース） |
| `RedirectUri` | `String` | リダイレクトURI |
| `ClientSecret` | `String` | クライアントシークレット |
| `State` | `String` | CSRF防止stateパラメータ |
| `TokenIssuer` | `String` | トークン発行者URI（ドメイン抽出メソッドあり） |
| `Subject` | `String` | ユーザーサブジェクト識別子 |
| `ResponseType` | Enum | code, token, id_token 等 |
| `GrantType` | Enum | authorization_code, refresh_token, client_credentials 等 |
| `ExpiresIn` | `long` | トークン有効期限（秒） |
| `Username` | `String` | リソースオーナーユーザー名 |
| `Password` | `String` | リソースオーナーパスワード |
| `ClientAssertion` | `String` | クライアント認証用JWT（issuer抽出メソッドあり） |
| `ClientAssertionType` | - | クライアントアサーション種別 |
| `ClientAuthenticationType` | - | クライアント認証方式 |
| `RequestUri` | `String` | Request Object URI（PAR対応） |
| `CustomParams` | - | カスタムリクエスト/レスポンスパラメータ |
| `Error` | `String` | OAuthエラーコード |
| `ErrorDescription` | `String` | OAuthエラー説明 |
| `AccessTokenEntity` | `String` | アクセストークン値 |
| `RefreshTokenEntity` | `String` | リフレッシュトークン値 |
| `TokenType` | Enum | Bearer, DPoP |
| `RequestedClientId` | - | リクエストされたクライアントID |

---

## OpenID Connect

**パッケージ**: `core.openid.oauth.type.oidc`

| クラス名 | 内部型 | 用途 |
|---------|--------|------|
| `Nonce` | `String` | リプレイ攻撃防止ノンス |
| `IdToken` | `String` | 署名/暗号化済みID Token JWT |
| `Claims` | `Set<String>` | 要求クレーム名集合 |
| `Display` | Enum | page, popup, touch, wap |
| `MaxAge` | `String`/`long` | 最大認証経過時間（秒） |
| `LoginHint` | `String` | ログインヒント（device:, email:, sub: プレフィックス対応） |
| `LoginHintType` | - | ヒント種別（DEVICE, EMAIL, SUBJECT等） |
| `IdTokenHint` | `String` | 既存ID Tokenヒント |
| `Prompt` | Enum | login, consent, create 等 |
| `Prompts` | `List<Prompt>` | 複数Prompt（スペース区切り） |
| `ResponseMode` | Enum | query, fragment, form_post, jwt 等 |
| `UiLocales` | - | ローカライゼーション設定 |
| `AcrValues` | - | Authentication Context Class Reference値 |

---

## PKCE

**パッケージ**: `core.openid.oauth.type.pkce`

| クラス名 | 内部型 | 用途 |
|---------|--------|------|
| `CodeVerifier` | `String` | PKCE code verifier（長さ検証あり） |
| `CodeChallenge` | `String` | PKCE code challenge（S256/plain） |
| `CodeChallengeMethod` | - | S256, plain |

---

## CIBA

**パッケージ**: `core.openid.oauth.type.ciba`

| クラス名 | 内部型 | 用途 |
|---------|--------|------|
| `AuthReqId` | `String` | CIBA認証リクエストID（256bitセキュアランダム） |
| `UserCode` | `String` | デバイス表示用ユーザーコード |
| `ClientNotificationToken` | `String` | バックチャネル通知トークン |
| `BindingMessage` | `String` | CIBAトランザクション結合メッセージ |
| `LoginHintToken` | `String` | ユーザー特定用JWTヒント |
| `RequestedExpiry` | - | リクエスト認証期限 |
| `Interval` | `int` | ポーリング間隔（秒） |
| `BackchannelTokenDeliveryMode` | - | トークン配信モード |

---

## mTLS

**パッケージ**: `core.openid.oauth.type.mtls`

| クラス名 | 内部型 | 用途 |
|---------|--------|------|
| `ClientCert` | `String` | mTLSクライアント証明書 |

---

## 拡張/カスタム

**パッケージ**: `core.openid.oauth.type.extension`

| クラス名 | 内部型 | 用途 |
|---------|--------|------|
| `CreatedAt` | `LocalDateTime` | トークン作成日時 |
| `ExpiresAt` | `LocalDateTime` | トークン有効期限（UTC変換あり） |
| `CustomProperties` | - | クライアント固有カスタムプロパティ |
| `DeniedScopes` | `List<String>` | ユーザー拒否スコープ |
| `OAuthDenyReason` | - | 認可拒否理由 |
| `JarmPayload` | `String` | JARM (JWT Secured Authorization Response Mode) ペイロード |
| `RegisteredRedirectUris` | - | 登録済みリダイレクトURI集合 |

---

## Verifiable Credentials

**パッケージ**: `core.openid.oauth.type.verifiablecredential`

| クラス名 | 内部型 | 用途 |
|---------|--------|------|
| `TransactionId` | `String` | VC発行トランザクションID（UUID変換可能） |
| `CNonce` | `String` | Credential発行ノンス |
| `CNonceExpiresIn` | - | C_nonce有効期限 |
| `CredentialIssuer` | `String` | Credential発行者URI |
| `DocType` | `String` | Mobile Documentタイプ |
| `Format` | `String` | Credentialフォーマット（jwt_vc_json, mso_mdoc等） |
| `ProofEntity` | - | Credentialリクエスト用暗号証明 |
| `ProofType` | Enum | jwt, cwt 等 |

---

## セッション管理

**パッケージ**: `core.openid.session`

| クラス名 | 内部型 | 用途 |
|---------|--------|------|
| `OPSessionIdentifier` | `String` | OPセッションID（`ops_`プレフィックス + セキュアランダム） |
| `ClientSessionIdentifier` | `String` | クライアントセッションID（`cs_`プレフィックス + セキュアランダム） |
| `TerminationReason` | Enum | セッション終了理由 |
| `SessionStatus` | - | セッション状態 |
| `BrowserState` | - | ブラウザレベル状態追跡 |
| `IdentityCookieToken` | `String` | IDクッキートークン値 |

---

## 認証

**パッケージ**: `core.openid.authentication`

| クラス名 | 内部型 | 用途 |
|---------|--------|------|
| `AuthSessionId` | `String` | 認証セッション結合トークン（256bitセキュアランダム） |
| `AuthenticationMethod` | - | 認証方式セレクタ |
| `AuthenticationInteractionType` | Enum | 認証インタラクション種別 |
| `OperationType` | Enum | CHALLENGE, AUTHENTICATION, REGISTRATION, DENY, DE_REGISTRATION, NO_ACTION, UNKNOWN |

---

## テナント・組織

**パッケージ**: `platform.multi_tenancy`

| クラス名 | 内部型 | 用途 |
|---------|--------|------|
| `TenantIdentifier` | `String` | テナントID（UUID変換可能） |
| `TenantDomain` | `String` | テナントドメイン（TokenIssuer変換メソッドあり） |
| `TenantName` | `String` | テナント表示名 |
| `TenantType` | - | テナント種別 |
| `TenantFeature` | - | テナント機能フラグ |
| `OrganizationIdentifier` | `String` | 組織ID（UUID変換可能） |
| `OrganizationName` | `String` | 組織表示名 |
| `OrganizationDescription` | `String` | 組織説明 |
| `MemberRole` | - | 組織メンバーロール |

---

## クライアント

**パッケージ**: `core.openid.oauth.configuration.client`

| クラス名 | 内部型 | 用途 |
|---------|--------|------|
| `ClientIdentifier` | `String` | クライアントID（UUID変換可能） |
| `ClientName` | `String` | クライアント表示名 |
| `ClientAttributes` | - | クライアント設定属性コンテナ |

---

## よく使う組み合わせ

### Repository 引数パターン
```java
// 第1引数は常に Tenant
repository.find(tenant, clientIdentifier);
repository.find(tenant, subject);
repository.register(tenant, authorizationCode, ...);
```

### Handler → Service 引数パターン
```java
// Context オブジェクトが値オブジェクトを集約
AuthorizationRequestContext context;
context.clientId();      // → ClientIdentifier
context.redirectUri();   // → RedirectUri
context.scope();         // → Scopes
context.state();         // → State
```

$ARGUMENTS
