# idp-server-core - OAuth 2.0/OIDC準拠コアエンジン

## モジュール概要

**情報源**: `libs/idp-server-core/src/main/java/org/idp/server/core/`
**確認日**: 2025-10-12

### 責務

OAuth 2.0、OpenID Connect (OIDC)、およびその拡張仕様に準拠した認証・認可・トークン発行のドメインロジックを提供。

- **プロトコル準拠**: RFC 6749 (OAuth 2.0), RFC 6750 (Bearer Token), OpenID Connect Core 1.0等
- **ドメインロジック**: Handler-Service-Repositoryパターンによる層責任分離
- **拡張可能性**: Pluginインターフェースによる機能拡張

### 依存関係

```
idp-server-core
  ↓ (依存)
idp-server-platform (マルチテナント・セッション・ログ等)
```

## ディレクトリ構造

```
libs/idp-server-core/src/main/java/org/idp/server/core/
├── openid/
│   ├── authentication/       # 認証ドメイン
│   ├── discovery/           # OIDC Discovery
│   ├── federation/          # フェデレーション
│   ├── grant_management/    # グラント管理
│   ├── identity/            # Identity・身元確認
│   ├── oauth/               # OAuth 2.0コア
│   ├── plugin/              # プラグインインターフェース
│   ├── token/               # トークン処理
│   └── userinfo/            # UserInfo エンドポイント
```

**情報源**: `find libs/idp-server-core/src/main/java/org/idp/server/core -type d -maxdepth 2`

## アーキテクチャパターン

### Handler-Service-Repository パターン

idp-server-coreでは、以下の3層パターンでドメインロジックを実装：

#### 1. Handler - プロトコル処理・オーケストレーション

**命名規則**: `{'{Domain}{Action}Handler'}`

**責務**:
- プロトコルリクエストの受け取り
- Validator/Verifierによる検証
- Repositoryからのデータ取得
- Service/Creatorへの処理委譲
- プロトコルレスポンスの生成

**実装例**: [OAuthAuthorizeHandler.java:51](../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/handler/OAuthAuthorizeHandler.java#L51)

```java
/**
 * 情報源: libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/handler/OAuthAuthorizeHandler.java
 * 確認方法: 実ファイルの77-133行目
 */
public class OAuthAuthorizeHandler {

  LoggerWrapper log = LoggerWrapper.getLogger(OAuthAuthorizeHandler.class);
  AuthorizationResponseCreators creators;
  AuthorizationRequestRepository authorizationRequestRepository;
  AuthorizationCodeGrantRepository authorizationCodeGrantRepository;
  OAuthTokenCommandRepository oAuthTokenCommandRepository;
  AuthorizationServerConfigurationQueryRepository authorizationServerConfigurationQueryRepository;
  ClientConfigurationQueryRepository clientConfigurationQueryRepository;

  public AuthorizationResponse handle(
      OAuthAuthorizeRequest request, OAuthSessionDelegate delegate) {

    // 1. リクエストから必要情報を抽出
    Tenant tenant = request.tenant();
    AuthorizationRequestIdentifier authorizationRequestIdentifier = request.toIdentifier();
    User user = request.user();
    Authentication authentication = request.authentication();
    CustomProperties customProperties = request.toCustomProperties();
    DeniedScopes deniedScopes = request.toDeniedScopes();

    // 2. Validatorで入力検証
    OAuthAuthorizeRequestValidator validator =
        new OAuthAuthorizeRequestValidator(
            authorizationRequestIdentifier, user, authentication, customProperties);
    validator.validate();

    // 3. Repositoryからデータ取得（注意: Tenant第一引数）
    AuthorizationRequest authorizationRequest =
        authorizationRequestRepository.get(tenant, authorizationRequestIdentifier);
    RequestedClientId requestedClientId = authorizationRequest.requestedClientId();
    AuthorizationServerConfiguration authorizationServerConfiguration =
        authorizationServerConfigurationQueryRepository.get(tenant);
    ClientConfiguration clientConfiguration =
        clientConfigurationQueryRepository.get(tenant, requestedClientId);

    // 4. Contextを構築
    OAuthAuthorizeContext context =
        new OAuthAuthorizeContext(
            authorizationRequest,
            user,
            authentication,
            customProperties,
            deniedScopes,
            authorizationServerConfiguration,
            clientConfiguration);

    // 5. Creatorでレスポンス生成
    AuthorizationResponseCreator authorizationResponseCreator =
        creators.get(context.responseType());
    AuthorizationResponse authorizationResponse = authorizationResponseCreator.create(context);

    // 6. 認可コード/トークンの永続化
    AuthorizationGrant authorizationGrant = context.authorize();
    if (authorizationResponse.hasAuthorizationCode()) {
      AuthorizationCodeGrant authorizationCodeGrant =
          AuthorizationCodeGrantCreator.create(context, authorizationResponse);
      authorizationCodeGrantRepository.register(tenant, authorizationCodeGrant);
    }

    if (authorizationResponse.hasAccessToken()) {
      OAuthToken oAuthToken = OAuthTokenFactory.create(authorizationResponse, authorizationGrant);
      oAuthTokenCommandRepository.register(tenant, oAuthToken);
    }

    // 7. セッション登録
    OAuthSessionKey oAuthSessionKey =
        new OAuthSessionKey(tenant.identifierValue(), requestedClientId.value());
    OAuthSession session =
        OAuthSession.create(oAuthSessionKey, user, authentication, authorizationRequest.maxAge());
    delegate.registerSession(session);

    return authorizationResponse;
  }
}
```

**重要ポイント**:
- ✅ **Tenant第一引数**: 全Repository操作で`Tenant`を最初に渡す（マルチテナント分離）
- ✅ **Validator/Verifier分離**: 入力検証とビジネスルール検証を明確に分離
- ✅ **Context Pattern**: ドメインロジックをContextオブジェクトにカプセル化
- ✅ **Factory/Creator**: オブジェクト生成ロジックを専用クラスに分離

#### 2. Service - 純粋ビジネスロジック

**命名規則**: `{'{Domain}{Action}Service'}`

**責務**:
- RFC準拠のビジネスロジック実装
- 副作用のない純粋な処理（可能な限り）
- 複数のRepositoryを組み合わせたロジック

**実装例**: [AuthorizationCodeGrantService.java:96](../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/token/service/AuthorizationCodeGrantService.java#L96)

```java
/**
 * 4.1.3. Access Token Request authorization code handling
 *
 * <p>The client makes a request to the token endpoint by sending the following parameters using the
 * "application/x-www-form-urlencoded" format per Appendix B with a character encoding of UTF-8 in
 * the HTTP request entity-body:
 *
 * <p>grant_type REQUIRED. Value MUST be set to "authorization_code".
 * <p>code REQUIRED. The authorization code received from the authorization server.
 * <p>redirect_uri REQUIRED, if the "redirect_uri" parameter was included in the authorization
 *     request as described in Section 4.1.1, and their values MUST be identical.
 * <p>client_id REQUIRED, if the client is not authenticating with the authorization server as
 *     described in Section 3.2.1.
 *
 * <p>For example, the client makes the following HTTP request using TLS (with extra line breaks for
 * display purposes only):
 *
 * <p>POST /token HTTP/1.1 Host: server.example.com Authorization: Basic
 * czZCaGRSa3F0MzpnWDFmQmF0M2JW Content-Type: application/x-www-form-urlencoded
 *
 * <p>grant_type=authorization_code&code=SplxlOBeZQQYbYS6WxSbIA
 * &redirect_uri=https%3A%2F%2Fclient%2Eexample%2Ecom%2Fcb
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-4.1.3">4.1.3. Access Token Request</a>
 *
 * 情報源: libs/idp-server-core/src/main/java/org/idp/server/core/openid/token/service/AuthorizationCodeGrantService.java
 * 確認方法: 実ファイルの1-95行目（Javadocコメント）
 */
public class AuthorizationCodeGrantService
    implements OAuthTokenCreationService, RefreshTokenCreatable, CNonceCreatable {

  AuthorizationRequestRepository authorizationRequestRepository;
  OAuthTokenCommandRepository oAuthTokenCommandRepository;
  // ...
}
```

**重要ポイント**:
- ✅ **RFC引用Javadoc**: 仕様書の章番号・引用を必ず記載
- ✅ **インターフェース実装**: 機能特性をインターフェースで表現（`RefreshTokenCreatable`等）
- ✅ **ビジネスロジック集中**: プロトコル詳細はServiceに集約

#### 3. Repository - データアクセス抽象化

**命名規則**: `{'{Entity}QueryRepository'}` / `{'{Entity}CommandRepository'}`

**責務**:
- CQRS (Command Query Responsibility Segregation) パターン
- データソースへのアクセス抽象化
- **ドメインロジック禁止** - データアクセスのみ

**実装例**: [ClientConfigurationQueryRepository.java:23](../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/configuration/client/ClientConfigurationQueryRepository.java#L23)

```java
/**
 * 情報源: libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/configuration/client/ClientConfigurationQueryRepository.java
 * 確認方法: 実ファイルの23-39行目
 */
public interface ClientConfigurationQueryRepository {

  // ✅ 必須存在: get() - データが存在しない場合は例外スロー
  ClientConfiguration get(Tenant tenant, RequestedClientId requestedClientId);
  ClientConfiguration get(Tenant tenant, ClientIdentifier clientIdentifier);

  // ✅ 任意存在: find() - データが存在しない場合はnull/空を返却
  ClientConfiguration find(Tenant tenant, ClientIdentifier clientIdentifier);
  ClientConfiguration findWithDisabled(
      Tenant tenant, ClientIdentifier clientIdentifier, boolean includeDisabled);

  // ✅ リスト取得: findList()
  List<ClientConfiguration> findList(Tenant tenant, int limit, int offset);
  List<ClientConfiguration> findList(Tenant tenant, ClientQueries queries);

  // ✅ カウント: findTotalCount()
  long findTotalCount(Tenant tenant, ClientQueries queries);
}
```

**重要ポイント**:
- 🚨 **Tenant第一引数必須**: 全メソッドで`Tenant`が第一引数（マルチテナント分離の基本原則）
  - **例外**: `OrganizationRepository`のみ（組織はテナントより上位概念）
- ✅ **get() vs find()**: `get()`は必須存在、`find()`は任意存在
- ✅ **Query/Command分離**: 読み取り（Query）と書き込み（Command）でインターフェース分離
- ✅ **値オブジェクト引数**: `String`ではなく`RequestedClientId`等の型安全な値オブジェクト

### Validator vs Verifier

**Validator**: 入力形式チェック → `{'{Operation}BadRequestException'}`

```java
// 形式チェック: nullチェック、形式妥当性
public class OAuthAuthorizeRequestValidator {
  public void validate() {
    throwExceptionIfIdentifierIsInvalid();
    throwExceptionIfUserIsInvalid();
    throwExceptionIfAuthenticationIsInvalid();
  }
}
```

**Verifier**: ビジネスルール検証 → `OAuthRedirectableBadRequestException`

```java
// ビジネスルール: プロトコル仕様準拠チェック
public class AuthorizationCodeGrantVerifier {
  public void verify() {
    verifyAuthorizationCode();
    verifyClientAuthentication();
    verifyRedirectUri();
  }
}
```

### Verifierの階層パターン（Base + Extension）

**情報源**: [AuthorizationCodeGrantVerifier.java:29](../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/token/verifier/AuthorizationCodeGrantVerifier.java#L29)

idp-serverのVerifierは、**Base Verifier（OAuth2/OIDC）+ Extension Verifier（PKCE/FAPI等）**の2層構造。

#### AuthorizationCodeGrantVerifier - 統合Verifier

```java
/**
 * Authorization Code Grant統合Verifier
 * Base + Extensionの両方を実行
 * 確認方法: 実ファイルの29-77行目
 */
public class AuthorizationCodeGrantVerifier {

  Map<AuthorizationProfile, AuthorizationCodeGrantVerifierInterface> baseVerifiers;
  List<AuthorizationCodeGrantExtensionVerifierInterface> extensionVerifiers;

  public AuthorizationCodeGrantVerifier() {
    // ✅ Base Verifier登録（OAuth2/OIDC）
    this.baseVerifiers = new HashMap<>();
    baseVerifiers.put(AuthorizationProfile.OAUTH2, new AuthorizationCodeGrantOAuth2Verifier());
    baseVerifiers.put(AuthorizationProfile.OIDC, new AuthorizationCodeGrantOidcVerifier());

    // ✅ PluginからBase Verifierロード（FAPI_BASELINE, FAPI_ADVANCE等）
    Map<AuthorizationProfile, AuthorizationCodeGrantVerifierInterface> loadedBaseVerifiers =
        AuthorizationCodeGrantVerifierPluginLoader.load();
    baseVerifiers.putAll(loadedBaseVerifiers);

    // ✅ Extension Verifierロード（PKCE等）
    this.extensionVerifiers = AuthorizationCodeGrantExtensionVerifierPluginLoader.load();
  }

  public void verify(
      TokenRequestContext tokenRequestContext,
      AuthorizationRequest authorizationRequest,
      AuthorizationCodeGrant authorizationCodeGrant,
      ClientCredentials clientCredentials) {

    // 1. Base Verifier実行（プロファイル別）
    AuthorizationCodeGrantVerifierInterface baseVerifier =
        baseVerifiers.get(authorizationRequest.profile());

    if (baseVerifier == null) {
      throw new UnSupportedException(
          "Unsupported profile: " + authorizationRequest.profile().name());
    }

    baseVerifier.verify(tokenRequestContext, authorizationRequest, authorizationCodeGrant, clientCredentials);

    // 2. Extension Verifier実行（shouldVerify() でフィルタリング）
    extensionVerifiers.forEach(extensionVerifier -> {
      if (extensionVerifier.shouldVerify(...)) {
        extensionVerifier.verify(...);
      }
    });
  }
}
```

#### Base Verifier - プロファイル別検証

| AuthorizationProfile | Base Verifier | 検証内容 |
|---------------------|---------------|---------|
| `OAUTH2` | `AuthorizationCodeGrantOAuth2Verifier` | OAuth 2.0基本検証 |
| `OIDC` | `AuthorizationCodeGrantOidcVerifier` | OIDC追加検証（nonce等） |
| `FAPI_BASELINE` | `AuthorizationCodeGrantFapiBaselineVerifier` | FAPI Baseline要件 |
| `FAPI_ADVANCE` | `AuthorizationCodeGrantFapiAdvanceVerifier` | FAPI Advanced要件 |

#### Extension Verifier - 拡張仕様検証

| Extension Verifier | 検証内容 | shouldVerify条件 | 実装モジュール |
|-------------------|---------|----------------|--------------|
| `PkceVerifier` | code_verifier検証 | リクエストにcode_challengeが含まれる | [idp-server-core-extension-pkce](./ai-34-extension-pkce.md) |
| FAPI Base/Advance | JAR/JARM/MTLS要件 | プロファイルがFAPI | [idp-server-core-extension-fapi](./ai-32-extension-fapi.md) |

### Verifierパターンの利点

1. ✅ **プロファイル別検証**: OAuth2/OIDC/FAPI等を自動選択
2. ✅ **拡張可能性**: Extension VerifierをPluginで追加可能
3. ✅ **条件付き実行**: shouldVerify()で不要な検証をスキップ
4. ✅ **責務分離**: Base（仕様準拠）とExtension（追加要件）を分離

### 拡張モジュールとの統合

**Core層（本モジュール）**が提供:
- `AuthorizationCodeGrantVerifier` - 統合Verifier
- `AuthorizationCodeGrantVerifierInterface` - Base Verifierインターフェース
- `AuthorizationCodeGrantExtensionVerifierInterface` - Extension Verifierインターフェース
- Plugin読み込み機構

**拡張モジュール**が実装:
- [PKCE](./ai-34-extension-pkce.md): `PkceVerifier` (Extension Verifier)
- [FAPI](./ai-32-extension-fapi.md): `FapiBaselineVerifier`, `FapiAdvanceVerifier` (Base Verifier)
- [CIBA](./ai-31-extension-ciba.md): CIBA専用Verifier

**統合フロー**:
```
1. Core層がPlugin Loaderで拡張Verifierをロード
2. AuthorizationProfileに基づいてBase Verifier選択
3. Extension Verifierを条件付きで全実行
4. 全検証が成功した場合のみ処理継続
```

**詳細**: [拡張機能層統合ドキュメント](./ai-30-extensions.md)

**情報源**: CLAUDE.md「検証・エラーハンドリング」セクション

## 主要ドメイン

### 1. OAuth (`openid/oauth/`)

OAuth 2.0準拠の認可フロー実装。

#### 主要クラス一覧

| クラス | 責務 |
|--------|------|
| `OAuthAuthorizeHandler` | 認可リクエスト処理 |
| `OAuthHandler` | トークンエンドポイント処理 |
| `AuthorizationRequest` | 認可リクエストドメインモデル |
| `AuthorizationResponse` | 認可レスポンスドメインモデル |
| `OAuthSession` | OAuthセッション管理 |
| `OAuthAuthorizeContext` | 認可コンテキスト |
| `AuthorizationServerConfiguration` | 認可サーバー設定 |
| `ClientConfiguration` | クライアント設定 |

**情報源**: `libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/`

#### AuthorizationRequest - 認可リクエストドメインモデル

**情報源**: [AuthorizationRequest.java:34](../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/request/AuthorizationRequest.java#L34)

OAuth 2.0/OIDC認可リクエストの全パラメータを型安全に保持。

```java
/**
 * 認可リクエストドメインモデル
 * 確認方法: 実ファイルの34-150行目
 */
public class AuthorizationRequest {
  // ✅ 識別子・テナント
  AuthorizationRequestIdentifier identifier;
  TenantIdentifier tenantIdentifier;
  AuthorizationProfile profile;  // OIDC/OAuth2/FAPI等

  // ✅ OAuth 2.0必須パラメータ
  ResponseType responseType;          // code, token, id_token等
  RequestedClientId requestedClientId;
  RedirectUri redirectUri;
  Scopes scopes;
  State state;

  // ✅ OAuth 2.0オプションパラメータ
  ResponseMode responseMode;          // query, fragment, form_post

  // ✅ OIDC拡張パラメータ
  Nonce nonce;
  Display display;                    // page, popup, touch, wap
  Prompts prompts;                    // none, login, consent, select_account
  MaxAge maxAge;
  UiLocales uiLocales;
  IdTokenHint idTokenHint;
  LoginHint loginHint;
  AcrValues acrValues;
  ClaimsValue claimsValue;

  // ✅ JAR (JWT Authorization Request)
  RequestObject requestObject;
  RequestUri requestUri;
  RequestedClaimsPayload requestedClaimsPayload;

  // ✅ PKCE (Proof Key for Code Exchange)
  CodeChallenge codeChallenge;
  CodeChallengeMethod codeChallengeMethod;  // S256, plain

  // ✅ RAR (Rich Authorization Requests)
  AuthorizationDetails authorizationDetails;

  // ✅ カスタムパラメータ
  CustomParams customParams;
  ClientAttributes clientAttributes;

  // ✅ 有効期限
  ExpiresIn expiresIn;
  ExpiresAt expiresAt;

  // ✅ 判定メソッド
  public boolean hasScope() { return scopes.exists(); }
  public boolean hasNonce() { return nonce.exists(); }
  public boolean hasCodeChallenge() { return codeChallenge.exists(); }
  public boolean hasAuthorizationDetails() { return authorizationDetails.exists(); }
  // ...
}
```

**重要ポイント**:
- ✅ **全パラメータ型安全**: `String`ではなく値オブジェクト（`Scopes`, `ResponseType`等）
- ✅ **判定メソッド**: `has*()`で存在チェック
- ✅ **不変オブジェクト**: Immutable設計
- ✅ **拡張対応**: PKCE/JAR/RAR等の拡張仕様をサポート

#### AuthorizationResponse - 認可レスポンスドメインモデル

**ResponseType別のCreatorパターン**:

```java
// ResponseType: code
AuthorizationResponseCodeCreator
  → AuthorizationResponse.with(code, state)

// ResponseType: token
AuthorizationResponseTokenCreator
  → AuthorizationResponse.with(accessToken, tokenType, expiresIn, state)

// ResponseType: id_token
AuthorizationResponseIdTokenCreator
  → AuthorizationResponse.with(idToken, state)

// ResponseType: code token
AuthorizationResponseCodeTokenCreator
  → AuthorizationResponse.with(code, accessToken, tokenType, expiresIn, state)

// ResponseType: code id_token
AuthorizationResponseCodeIdTokenCreator
  → AuthorizationResponse.with(code, idToken, state)

// ResponseType: code id_token token
AuthorizationResponseCodeIdTokenTokenCreator
  → AuthorizationResponse.with(code, idToken, accessToken, tokenType, expiresIn, state)
```

**情報源**: `libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/response/`

#### OAuthSession - セッション管理

```java
public class OAuthSession {
  OAuthSessionKey key;              // (tenantId, clientId)のペア
  User user;                        // 認証済みユーザー
  Authentication authentication;    // 認証情報
  MaxAge maxAge;                    // セッション有効期限
  Instant authenticatedAt;          // 認証時刻

  // ✅ セッション作成
  public static OAuthSession create(
      OAuthSessionKey key,
      User user,
      Authentication authentication,
      MaxAge maxAge) {
    return new OAuthSession(key, user, authentication, maxAge, Instant.now());
  }

  // ✅ セッション有効性チェック
  public boolean isExpired() {
    if (!maxAge.exists()) {
      return false;
    }
    Instant expiresAt = authenticatedAt.plusSeconds(maxAge.value());
    return Instant.now().isAfter(expiresAt);
  }
}
```

**用途**: SSO（Single Sign-On）実現のためのセッション管理。

#### Configuration - 設定ドメインモデル

**AuthorizationServerConfiguration** - 認可サーバー設定:

```java
public class AuthorizationServerConfiguration {
  String issuer;
  String authorizationEndpoint;
  String tokenEndpoint;
  String userinfoEndpoint;
  String jwksUri;
  List<String> responseTypesSupported;
  List<String> grantTypesSupported;
  List<String> scopesSupported;
  List<String> tokenEndpointAuthMethodsSupported;
  // ...

  // ✅ 判定メソッド
  public boolean supportsResponseType(ResponseType responseType) { ... }
  public boolean supportsGrantType(GrantType grantType) { ... }
  public boolean supportsScope(Scope scope) { ... }
}
```

**ClientConfiguration** - クライアント設定:

```java
public class ClientConfiguration {
  ClientIdentifier identifier;
  ClientName name;
  ClientType clientType;              // public, confidential
  RedirectUris redirectUris;
  GrantTypes grantTypes;
  ResponseTypes responseTypes;
  Scopes scopes;
  ClientSecret clientSecret;          // confidentialのみ
  TokenEndpointAuthMethod tokenEndpointAuthMethod;
  // ...

  // ✅ 判定メソッド
  public boolean isConfidential() { return clientType.isConfidential(); }
  public boolean allowsGrantType(GrantType grantType) { ... }
  public boolean allowsRedirectUri(RedirectUri redirectUri) { ... }
}
```

### 2. Token (`openid/token/`)

トークン発行・検証・リフレッシュ処理。

**主要クラス**:
- `AuthorizationCodeGrantService` - 認可コードフロー
- `ClientCredentialsGrantService` - クライアントクレデンシャルフロー
- `RefreshTokenGrantService` - リフレッシュトークンフロー
- `OAuthTokenCreationService` - トークン生成インターフェース

**Grant Type拡張パターン**:

```java
// Map<GrantType, Service> パターン
Map<GrantType, OAuthTokenCreationService> services = new HashMap<>();
services.put(GrantType.AUTHORIZATION_CODE, new AuthorizationCodeGrantService(...));
services.put(GrantType.CLIENT_CREDENTIALS, new ClientCredentialsGrantService(...));
services.put(GrantType.REFRESH_TOKEN, new RefreshTokenGrantService(...));

// 実行時に動的選択
OAuthTokenCreationService service = services.get(tokenRequest.grantType());
```

**情報源**: CLAUDE.md「Extension: `Map<GrantType, Service>` + Plugin インターフェース」

### 3. Identity (`openid/identity/`)

ユーザー情報・ID Token・Verified Claims処理。

#### 主要クラス一覧

| クラス | 責務 |
|--------|------|
| `User` | ユーザードメインモデル |
| `UserIdentifier` | ユーザー識別子（sub） |
| `IdTokenCreator` | ID Token生成 |
| `IdTokenCustomClaimsBuilder` | カスタムクレーム追加 |
| `UserAuthenticationApi` | ユーザー認証API |
| `UserOperationApi` | ユーザー操作API |
| `AuthenticationDevices` | 認証デバイス管理 |

**情報源**: `libs/idp-server-core/src/main/java/org/idp/server/core/openid/identity/`

#### User - ユーザードメインモデル

**情報源**: [User.java:36](../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/identity/User.java#L36)

OpenID Connect標準クレーム + 拡張機能を含む包括的なユーザーモデル。

```java
/**
 * ユーザードメインモデル
 * 確認方法: 実ファイルの36-100行目
 */
public class User implements JsonReadable, Serializable, UuidConvertable {
  // ✅ OpenID Connect 標準クレーム
  String sub;                     // Subject Identifier（必須・一意）
  String name;                    // Full name
  String givenName;               // Given name
  String familyName;              // Family name
  String middleName;              // Middle name
  String nickname;                // Casual name
  String preferredUsername;       // Preferred username
  String profile;                 // Profile page URL
  String picture;                 // Profile picture URL
  String website;                 // Website URL
  String email;                   // Email address
  Boolean emailVerified;          // Email verified flag
  String gender;                  // Gender
  String birthdate;               // Birthdate (YYYY-MM-DD)
  String zoneinfo;                // Time zone
  String locale;                  // Locale
  String phoneNumber;             // Phone number
  Boolean phoneNumberVerified;    // Phone number verified flag
  Address address;                // Address

  // ✅ 拡張機能
  String providerId;              // Identity Provider ID
  String externalUserId;          // 外部プロバイダーのユーザーID
  HashMap<String, Object> externalProviderOriginalPayload; // 外部IdPの元データ

  // ✅ 認証関連
  String hashedPassword;          // ハッシュ化されたパスワード
  String rawPassword;             // 一時的な平文パスワード（永続化しない）
  List<AuthenticationDevice> authenticationDevices; // FIDO2/Passkey等

  // ✅ 権限・ロール
  List<UserRole> roles;           // ユーザーロール
  List<String> permissions;       // 権限リスト

  // ✅ マルチテナント・組織
  String currentTenant;           // 現在のテナント
  List<String> assignedTenants;   // 割り当てられたテナント
  String currentOrganizationId;   // 現在の組織
  List<String> assignedOrganizations; // 割り当てられた組織

  // ✅ Verified Claims (IDA)
  HashMap<String, Object> verifiedClaims; // 検証済み身元情報

  // ✅ Verifiable Credentials
  List<HashMap<String, Object>> credentials; // VCリスト

  // ✅ カスタムプロパティ
  HashMap<String, Object> customProperties; // テナント固有の追加情報

  // ✅ ライフサイクル
  LocalDateTime createdAt;
  LocalDateTime updatedAt;
  UserStatus status;              // INITIALIZED, ACTIVE, SUSPENDED, DELETED

  // ✅ ライフサイクル管理
  public boolean canTransit(UserStatus from, UserStatus to) {
    return UserLifecycleManager.canTransit(from, to);
  }

  public User transitStatus(UserStatus newStatus) {
    this.status = UserLifecycleManager.transit(this.status, newStatus);
    return this;
  }

  // ✅ 権限判定
  public Set<String> permissionsAsSet() {
    return new HashSet<>(permissions);
  }

  public String permissionsAsString() {
    return String.join(",", permissions);
  }
}
```

**重要ポイント**:
- ✅ **OIDC標準準拠**: RFC準拠の標準クレーム
- ✅ **拡張機能**: FIDO2, IDA, VC対応
- ✅ **マルチテナント**: 複数テナント・組織への所属
- ✅ **ライフサイクル管理**: ステータス遷移制御

#### UserStatus - ユーザーステータス

```java
public enum UserStatus {
  INITIALIZED,  // 初期化済み（登録直後）
  ACTIVE,       // アクティブ（通常利用可能）
  SUSPENDED,    // 一時停止
  DELETED       // 削除済み
}

// ライフサイクル遷移ルール
// INITIALIZED → ACTIVE
// ACTIVE ↔ SUSPENDED
// ACTIVE/SUSPENDED → DELETED
```

#### Address - 住所クレーム

```java
public class Address {
  String formatted;       // 完全な住所文字列
  String streetAddress;   // 番地
  String locality;        // 市区町村
  String region;          // 都道府県
  String postalCode;      // 郵便番号
  String country;         // 国コード（ISO 3166-1）
}
```

### 4. Authentication (`openid/authentication/`)

認証処理・認証トランザクション管理。

#### 主要クラス一覧

| クラス | 責務 |
|--------|------|
| `Authentication` | 認証結果ドメインモデル |
| `AuthenticationTransaction` | 認証トランザクション管理 |
| `AuthenticationContext` | 認証コンテキスト |
| `AuthenticationInteractor` | 認証インタラクター（FIDO2/Password等） |
| `AuthenticationMethod` | 認証方式 |
| `AuthenticationPolicy` | 認証ポリシー |
| `AuthenticationInteractionResult` | 認証インタラクション結果 |

**情報源**: `libs/idp-server-core/src/main/java/org/idp/server/core/openid/authentication/`

#### Authentication - 認証結果ドメインモデル

**情報源**: [Authentication.java:24](../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/authentication/Authentication.java#L24)

認証完了後の情報を保持。ID Tokenの`auth_time`, `amr`, `acr`クレームに使用。

```java
/**
 * 認証結果ドメインモデル
 * 確認方法: 実ファイルの24-84行目
 */
public class Authentication implements Serializable, JsonReadable {
  LocalDateTime time;          // 認証時刻（auth_time）
  List<String> methods;        // 認証方式リスト（amr: Authentication Methods References）
  String acr;                  // 認証コンテキストクラス参照（acr: Authentication Context Class Reference）

  // ✅ 認証時刻
  public LocalDateTime time() { return time; }
  public boolean hasAuthenticationTime() { return Objects.nonNull(time); }

  // ✅ 認証方式
  public List<String> methods() { return methods; }
  public boolean hasMethod() { return !methods.isEmpty(); }

  // ✅ ACR (Authentication Context Class Reference)
  public String acr() { return acr; }
  public boolean hasAcrValues() { return acr != null && !acr.isEmpty(); }
}
```

**AMR（Authentication Methods References）例**:
- `["pwd"]` - パスワード認証
- `["fido", "pwd"]` - FIDO2 + パスワード（MFA）
- `["otp", "sms"]` - SMS OTP
- `["fed"]` - フェデレーション

**情報源**: [RFC 8176 - Authentication Method Reference Values](https://www.rfc-editor.org/rfc/rfc8176.html)

#### AuthenticationTransaction - 認証トランザクション

**情報源**: [AuthenticationTransaction.java:35](../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/authentication/AuthenticationTransaction.java#L35)

複数ステップの認証フロー（MFA等）を管理。

```java
/**
 * 認証トランザクション管理
 * 確認方法: 実ファイルの35-120行目
 */
public class AuthenticationTransaction {
  AuthenticationTransactionIdentifier identifier;  // トランザクション識別子
  AuthorizationIdentifier authorizationIdentifier; // 認可リクエスト識別子
  AuthenticationRequest request;                   // 認証リクエスト
  AuthenticationPolicy authenticationPolicy;       // 認証ポリシー
  AuthenticationInteractionResults interactionResults; // 認証インタラクション結果集合
  AuthenticationTransactionAttributes attributes;   // カスタム属性

  // ✅ インタラクション結果の追加・更新
  public AuthenticationTransaction updateWith(
      AuthenticationInteractionRequestResult interactionRequestResult) {
    // Immutableパターン: 新しいインスタンスを返却
  }

  // ✅ 認証完了判定
  public boolean isCompleted() {
    return authenticationPolicy.isCompleted(interactionResults);
  }

  // ✅ MFA必要判定
  public boolean requiresMfa() {
    return authenticationPolicy.requiresMfa();
  }
}
```

**認証フロー例（MFA）**:

```java
// 1. パスワード認証
AuthenticationInteractionRequestResult passwordResult =
    new AuthenticationInteractionRequestResult("PASSWORD", "pwd", true, user);
transaction = transaction.updateWith(passwordResult);

// 2. MFA必要判定
if (transaction.requiresMfa()) {
  // FIDO2認証を要求
  AuthenticationInteractionRequestResult fidoResult =
      new AuthenticationInteractionRequestResult("FIDO2", "fido", true, user);
  transaction = transaction.updateWith(fidoResult);
}

// 3. 認証完了判定
if (transaction.isCompleted()) {
  Authentication authentication = transaction.createAuthentication();
  // authentication.methods() → ["pwd", "fido"]
}
```

#### AuthenticationMethod - 標準認証方式

| AMR値 | 説明 |
|------|------|
| `pwd` | Password |
| `otp` | One-Time Password |
| `sms` | SMS OTP |
| `fido` | FIDO2/WebAuthn |
| `hwk` | Hardware Key |
| `swk` | Software Key |
| `pin` | PIN |
| `face` | 顔認証 |
| `fpt` | 指紋認証 |
| `mfa` | Multiple-Factor Authentication |
| `fed` | Federation |

### 5. Grant Management (`openid/grant_management/`)

ユーザーによる認可（スコープ・クレーム同意）の管理。

#### 主要クラス一覧

| クラス | 責務 |
|--------|------|
| `AuthorizationGranted` | 認可済みグラント（永続化） |
| `AuthorizationGrant` | 認可グラント（一時） |
| `AuthorizationCodeGrant` | 認可コードグラント |
| `ConsentClaims` | 同意されたクレーム |
| `GrantIdTokenClaims` | ID Token用クレーム |
| `GrantUserinfoClaims` | UserInfo用クレーム |

**情報源**: `libs/idp-server-core/src/main/java/org/idp/server/core/openid/grant_management/`

#### AuthorizationGranted - 認可済みグラント

**情報源**: [AuthorizationGranted.java:25](../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/grant_management/AuthorizationGranted.java#L25)

ユーザーが過去に同意したスコープ・クレームを永続化。

```java
/**
 * 認可済みグラント
 * 確認方法: 実ファイルの25-80行目
 */
public class AuthorizationGranted {
  AuthorizationGrantedIdentifier identifier;  // (user, client)のペア
  AuthorizationGrant authorizationGrant;      // 認可内容

  // ✅ スコープ認可チェック
  public boolean isGrantedScopes(Scopes requestedScopes) {
    return authorizationGrant.isGrantedScopes(requestedScopes);
  }

  // ✅ 未認可スコープ取得
  public Scopes unauthorizedScopes(Scopes requestedScopes) {
    return authorizationGrant.unauthorizedScopes(requestedScopes);
  }

  // ✅ クレーム認可チェック
  public boolean isGrantedClaims(GrantIdTokenClaims requestedIdTokenClaims) {
    return authorizationGrant.isGrantedIdTokenClaims(requestedIdTokenClaims);
  }

  // ✅ グラントマージ（追加同意）
  public AuthorizationGranted merge(AuthorizationGrant newAuthorizationGrant) {
    AuthorizationGrant merged = authorizationGrant.merge(newAuthorizationGrant);
    return new AuthorizationGranted(identifier, merged);
  }
}
```

**用途**:
1. **初回認可**: ユーザーが同意画面で承認 → `AuthorizationGranted`を保存
2. **2回目以降**: `AuthorizationGranted`を確認 → 同意済みなら同意画面スキップ
3. **追加スコープ**: 未認可スコープのみ同意画面表示

#### AuthorizationGrant - 認可グラント（一時）

```java
public class AuthorizationGrant {
  Scopes scopes;                    // 認可されたスコープ
  ConsentClaims consentClaims;      // 同意されたクレーム
  GrantIdTokenClaims idTokenClaims; // ID Token用クレーム
  GrantUserinfoClaims userinfoClaims; // UserInfo用クレーム

  // ✅ スコープ判定
  public boolean isGrantedScopes(Scopes requestedScopes) {
    return scopes.containsAll(requestedScopes);
  }

  // ✅ マージ（追加認可）
  public AuthorizationGrant merge(AuthorizationGrant other) {
    Scopes mergedScopes = scopes.merge(other.scopes);
    ConsentClaims mergedClaims = consentClaims.merge(other.consentClaims);
    return new AuthorizationGrant(mergedScopes, mergedClaims, ...);
  }
}
```

### 6. Discovery (`openid/discovery/`)

OpenID Connect Discovery（`.well-known/openid-configuration`）とJWKS提供。

#### 主要クラス一覧

| クラス | 責務 |
|--------|------|
| `DiscoveryHandler` | Discoveryエンドポイント処理 |
| `ServerConfigurationResponseCreator` | メタデータ生成 |
| `OidcMetaDataApi` | Discovery API |

**情報源**: `libs/idp-server-core/src/main/java/org/idp/server/core/openid/discovery/`

#### Discovery Metadata - OpenID Provider Metadata

**エンドポイント**: `GET /.well-known/openid-configuration`

```json
{
  "issuer": "https://idp.example.com",
  "authorization_endpoint": "https://idp.example.com/authorize",
  "token_endpoint": "https://idp.example.com/token",
  "userinfo_endpoint": "https://idp.example.com/userinfo",
  "jwks_uri": "https://idp.example.com/.well-known/jwks.json",
  "registration_endpoint": "https://idp.example.com/register",
  "scopes_supported": ["openid", "profile", "email", "address", "phone"],
  "response_types_supported": ["code", "token", "id_token", "code token", "code id_token", "id_token token", "code id_token token"],
  "response_modes_supported": ["query", "fragment", "form_post"],
  "grant_types_supported": ["authorization_code", "implicit", "refresh_token", "client_credentials"],
  "subject_types_supported": ["public", "pairwise"],
  "id_token_signing_alg_values_supported": ["RS256", "RS384", "RS512", "ES256", "ES384", "ES512"],
  "token_endpoint_auth_methods_supported": ["client_secret_basic", "client_secret_post", "client_secret_jwt", "private_key_jwt"],
  "claims_supported": ["sub", "name", "given_name", "family_name", "email", "email_verified", "..."],
  "code_challenge_methods_supported": ["S256", "plain"]
}
```

#### JWKS Endpoint - JSON Web Key Set

**エンドポイント**: `GET /.well-known/jwks.json`

```json
{
  "keys": [
    {
      "kty": "RSA",
      "use": "sig",
      "kid": "2024-key-1",
      "alg": "RS256",
      "n": "xGOr...",
      "e": "AQAB"
    }
  ]
}
```

### 7. UserInfo (`openid/userinfo/`)

OpenID Connect UserInfoエンドポイント実装。

#### 主要クラス一覧

| クラス | 責務 |
|--------|------|
| `UserinfoHandler` | UserInfoエンドポイント処理 |
| `UserinfoResponse` | UserInfo レスポンス |
| `UserinfoCustomIndividualClaimsCreator` | カスタムクレーム追加（Plugin） |

**情報源**: `libs/idp-server-core/src/main/java/org/idp/server/core/openid/userinfo/`

#### UserInfo Endpoint

**エンドポイント**: `GET /userinfo`
**認証**: `Authorization: Bearer <access_token>`

**レスポンス例**:

```json
{
  "sub": "248289761001",
  "name": "Jane Doe",
  "given_name": "Jane",
  "family_name": "Doe",
  "email": "janedoe@example.com",
  "email_verified": true,
  "picture": "https://example.com/janedoe.jpg"
}
```

**スコープとクレームのマッピング**:

| スコープ | 返却されるクレーム |
|---------|------------------|
| `openid` | `sub` |
| `profile` | `name`, `given_name`, `family_name`, `middle_name`, `nickname`, `preferred_username`, `profile`, `picture`, `website`, `gender`, `birthdate`, `zoneinfo`, `locale`, `updated_at` |
| `email` | `email`, `email_verified` |
| `address` | `address` |
| `phone` | `phone_number`, `phone_number_verified` |

### 8. Federation (`openid/federation/`)

外部IdPとのフェデレーション（SSO連携）。

#### 主要クラス一覧

| クラス | 責務 |
|--------|------|
| `FederationInteractor` | フェデレーション実行（Plugin） |
| `FederationInteractionResult` | フェデレーション結果 |
| `FederationConfigurationQueryRepository` | フェデレーション設定取得 |

**情報源**: `libs/idp-server-core/src/main/java/org/idp/server/core/openid/federation/`

#### Federation Flow

```java
// 1. フェデレーション設定取得
FederationConfiguration config =
    federationConfigurationQueryRepository.get(tenant, providerId);

// 2. 外部IdPへリダイレクト
FederationInteractor interactor = federationInteractorFactory.create(config);
String authorizationUrl = interactor.createAuthorizationUrl(authorizationRequest);
// → https://external-idp.com/authorize?client_id=...&redirect_uri=...

// 3. コールバック処理
FederationInteractionResult result = interactor.handleCallback(callbackRequest);

// 4. ユーザー情報取得
User externalUser = result.user();

// 5. ローカルユーザーとのアカウントリンキング
User localUser = userRepository.findByEmail(tenant, externalUser.email());
if (localUser == null) {
  localUser = createNewUser(externalUser);
} else {
  localUser = linkFederatedIdentity(localUser, externalUser, providerId);
}
```

**対応プロトコル**:
- ✅ OIDC Federation（`idp-server-federation-oidc`モジュール）
- 🔜 SAML 2.0（将来対応予定）
- 🔜 LDAP（将来対応予定）

### 9. Plugin (`openid/plugin/`)

機能拡張用プラグインインターフェース定義。

**主要インターフェース**:
- `AuthorizationRequestExtensionVerifier` - 認可リクエスト拡張検証
- `OAuthTokenCreationServiceFactory` - トークン生成Service拡張
- `AccessTokenCustomClaimsCreator` - アクセストークンクレーム拡張
- `ClientAuthenticator` - クライアント認証拡張

**情報源**: [intro-01-tech-overview.md:93-170](../../documentation/docs/content_01_intro/intro-01-tech-overview.md#L93-L170)

## 値オブジェクトパターン

idp-server-coreでは、`String`/`Map`の濫用を避け、意味のある値オブジェクトを使用。

### 悪い例 ❌

```java
// ❌ String濫用
public void register(Tenant tenant, String clientId, String clientSecret) {
  // clientIdとclientSecretを間違えて渡してもコンパイルエラーにならない
}

// ❌ Map濫用
public Map<String, Object> getTokenResponse(Map<String, String> request) {
  // 型安全性がない、IDE補完が効かない
}
```

### 良い例 ✅

```java
// ✅ 値オブジェクト使用
public void register(Tenant tenant, RequestedClientId clientId, ClientSecret clientSecret) {
  // 型が違うため、間違えるとコンパイルエラー
}

// ✅ ドメインモデル使用
public TokenResponse createToken(TokenRequest request) {
  // 型安全、IDE補完が効く、仕様が明確
}
```

**値オブジェクト例**:
- `RequestedClientId` - クライアントID
- `AuthorizationCode` - 認可コード
- `AccessToken` - アクセストークン
- `RefreshToken` - リフレッシュトークン
- `Scope` / `Scopes` - スコープ
- `RedirectUri` - リダイレクトURI
- `GrantType` - グラントタイプ

**情報源**: CLAUDE.md「型安全性: String/Map濫用禁止、意味のある値オブジェクト優先」

## 例外ハンドリング

### 例外命名パターン

```java
// OAuth標準エラー
throw new OAuthBadRequestException("invalid_request", "Missing required parameter: code");
throw new OAuthUnauthorizedException("invalid_client", "Client authentication failed");

// OAuth Redirectableエラー（認可エンドポイント）
throw new OAuthRedirectableBadRequestException("invalid_scope", "Requested scope is invalid");

// 内部エラー
throw new ServerConfigurationNotFoundException("Authorization server configuration not found");
```

### `throwExceptionIf{Condition}() パターン`

```java
// ✅ 良い例: 条件を明示的に表現
private void throwExceptionIfCodeIsInvalid(AuthorizationCode code) {
  if (code == null || code.isEmpty()) {
    throw new TokenBadRequestException("invalid_grant", "Authorization code is invalid");
  }
}

// ✅ 良い例: 早期リターンで可読性向上
public void verify() {
  throwExceptionIfCodeIsInvalid(authorizationCode);
  throwExceptionIfClientIdMismatch(clientId);
  throwExceptionIfRedirectUriMismatch(redirectUri);
  // メインロジック
}
```

**情報源**: CLAUDE.md「例外: `throwExceptionIf{Condition}()` パターン、OAuth標準エラーコード」

## アンチパターン

### ❌ 1. Util濫用

```java
// ❌ 悪い例: 共通ロジックをUtilに逃がす
public class OAuthUtils {
  public static boolean isValidAuthorizationCode(String code) {
    // ドメインロジックがUtilに漏れている
  }
}

// ✅ 良い例: ドメインオブジェクトに配置
public class AuthorizationCode {
  public boolean isValid() {
    // ドメインロジックはドメインオブジェクトに
  }
}
```

### ❌ 2. Map濫用

```java
// ❌ 悪い例: Map<String, Object>で情報を持ち回る
public Map<String, Object> authorize(Map<String, String> request) {
  Map<String, Object> response = new HashMap<>();
  response.put("access_token", "...");
  response.put("expires_in", 3600);
  return response;
}

// ✅ 良い例: 専用クラス使用
public AuthorizationResponse authorize(AuthorizationRequest request) {
  return AuthorizationResponse.builder()
      .accessToken(new AccessToken("..."))
      .expiresIn(3600)
      .build();
}
```

### ❌ 3. 永続化層でのビジネスロジック

```java
// ❌ 悪い例: Repository実装でビジネス判定
public class ClientConfigurationRepositoryImpl implements ClientConfigurationQueryRepository {
  @Override
  public ClientConfiguration get(Tenant tenant, RequestedClientId clientId) {
    ClientConfiguration config = dao.findById(clientId);

    // ❌ ビジネスロジックがデータソース層に漏れている
    if ("ORGANIZER".equals(tenant.type())) {
      config.setSpecialPermissions(true);
    }

    return config;
  }
}

// ✅ 良い例: ドメイン層で判定
public class ClientConfiguration {
  public boolean hasSpecialPermissions(Tenant tenant) {
    // ✅ ビジネスロジックはドメインオブジェクトに
    return tenant.isOrganizer() && this.permissionLevel.isSpecial();
  }
}
```

**重要教訓**: データソース層 = SELECT/INSERT/UPDATE/DELETE、ドメイン層 = 業務ルール

**情報源**: CLAUDE.md「⚠️ レイヤー責任違反の重要教訓」

## 設定パターン

### TenantAttributes活用

```java
// テナント固有設定の取得
boolean enablePKCE = tenantAttributes.optValueAsBoolean("oauth.pkce.enabled", false);
int tokenLifetime = tenantAttributes.optValueAsInt("token.access_token.lifetime_seconds", 3600);
String customClaim = tenantAttributes.optValueAsString("token.custom_claim_key", "");
```

**情報源**: CLAUDE.md「設定: TenantAttributes.optValueAsBoolean(key, default) パターン」

## まとめ

### idp-server-core を理解するための5つのポイント

1. **Handler-Service-Repository パターン**: 層責任を明確に分離
2. **Tenant第一引数の原則**: 全Repository操作でマルチテナント分離
3. **値オブジェクト優先**: String/Map濫用を避け、型安全な設計
4. **RFC準拠Javadoc**: 仕様書引用で実装意図を明確化
5. **Plugin拡張**: `Map<GrantType, Service>`パターンで機能拡張

### 次のステップ

- [idp-server-platform（プラットフォーム基盤）](./ai-12-platform.md) - マルチテナント実装詳細
- [idp-server-use-cases（ユースケース層）](./ai-10-use-cases.md) - EntryServiceパターン
- [idp-server-core-adapter（アダプター層）](./ai-21-core-adapter.md) - Repository実装

---

**情報源**:
- `libs/idp-server-core/src/main/java/`配下の実装コード
- CLAUDE.md「4層アーキテクチャ詳細」「Handler-Service-Repository パターン」
- [intro-01-tech-overview.md](../../documentation/docs/content_01_intro/intro-01-tech-overview.md)

**最終更新**: 2025-10-12
**確認方法**: `find libs/idp-server-core -type f -name "*.java" | head -20`
