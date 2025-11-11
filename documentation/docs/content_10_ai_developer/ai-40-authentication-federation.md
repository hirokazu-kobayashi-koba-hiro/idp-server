# 認証・連携層 - ユーザー認証とフェデレーション

## 概要

ユーザー認証手段とフェデレーション（外部IdP連携）を提供するモジュール群。

**3つのモジュール**:
1. **idp-server-authentication-interactors** - 認証インタラクター
2. **idp-server-webauthn4j-adapter** - WebAuthn/FIDO2実装
3. **idp-server-federation-oidc** - OIDCフェデレーション

---

## idp-server-authentication-interactors

**情報源**: `libs/idp-server-authentication-interactors/`

### 責務

多様な認証手段のインタラクター実装。

### 対応認証手段

| 認証手段 | 説明 |
|---------|------|
| **FIDO2/Passkey** | WebAuthn準拠の生体認証・セキュリティキー |
| **FIDO-UAF** | FIDO Universal Authentication Framework |
| **Password** | パスワード認証 |
| **SMS** | SMS OTP |
| **Email** | メールOTP |
| **Device** | デバイス認証（プッシュ通知） |
| **Legacy ID Service** | 既存IDサービス連携 |

### AuthenticationInteractorFactory パターン

**情報源**: [AuthenticationInteractorFactory.java:21](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/authentication/plugin/AuthenticationInteractorFactory.java#L21)

```java
/**
 * 認証インタラクターのFactory
 * 確認方法: 実ファイルの21-24行目
 */
public interface AuthenticationInteractorFactory {
  AuthenticationInteractor create(AuthenticationDependencyContainer container);
}

// Password実装例
public class PasswordAuthenticationInteractorFactory implements AuthenticationInteractorFactory {
  @Override
  public AuthenticationInteractor create(AuthenticationDependencyContainer container) {
    PasswordVerificationDelegation passwordVerificationDelegation =
        container.resolve(PasswordVerificationDelegation.class);
    return new PasswordAuthenticationInteractor(passwordVerificationDelegation);
  }
}
```

**情報源**: [PasswordAuthenticationInteractorFactory.java:24](../../../libs/idp-server-authentication-interactors/src/main/java/org/idp/server/authentication/interactors/password/PasswordAuthenticationInteractorFactory.java#L24)

### AuthenticationInteractor インターフェース

**情報源**: [AuthenticationInteractor.java:23](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/authentication/AuthenticationInteractor.java#L23)

```java
/**
 * 認証インタラクター
 * 確認方法: 実ファイルの23-40行目
 */
public interface AuthenticationInteractor {

  // ✅ インタラクションタイプ
  AuthenticationInteractionType type();

  // ✅ オペレーションタイプ（デフォルト: AUTHENTICATION）
  default OperationType operationType() {
    return OperationType.AUTHENTICATION;
  }

  // ✅ 認証方式名（AMR値）
  String method();

  // ✅ 認証インタラクション実行
  AuthenticationInteractionRequestResult interact(
      Tenant tenant,
      AuthenticationTransaction transaction,
      AuthenticationInteractionType type,
      AuthenticationInteractionRequest request,
      RequestAttributes requestAttributes,
      UserQueryRepository userQueryRepository);
}
```

### Plugin登録

```
# META-INF/services/org.idp.server.core.openid.authentication.plugin.AuthenticationInteractorFactory
org.idp.server.authentication.interactors.password.PasswordAuthenticationInteractorFactory
org.idp.server.authentication.interactors.sms.SmsAuthenticationInteractorFactory
org.idp.server.authentication.interactors.cancel.AuthenticationCancelInteractorFactory
```

### 認証フロー

```java
// 1. PluginLoaderで全Factoryをロード
List<AuthenticationInteractorFactory> factories =
    PluginLoader.loadFromInternalModule(AuthenticationInteractorFactory.class);

// 2. DependencyContainerからInteractor生成
AuthenticationDependencyContainer container = new AuthenticationDependencyContainer(...);

Map<AuthenticationInteractionType, AuthenticationInteractor> interactors = new HashMap<>();
for (AuthenticationInteractorFactory factory : factories) {
  AuthenticationInteractor interactor = factory.create(container);
  interactors.put(interactor.type(), interactor);
}

// 3. 認証実行
AuthenticationInteractionType interactionType = AuthenticationInteractionType.PASSWORD;
AuthenticationInteractor interactor = interactors.get(interactionType);

AuthenticationInteractionRequestResult result = interactor.interact(
    tenant,
    transaction,
    interactionType,
    request,
    requestAttributes,
    userQueryRepository);

if (result.isSuccess()) {
  // 認証成功
  transaction = transaction.updateWith(result);
}
```

---

## idp-server-webauthn4j-adapter

**情報源**: `libs/idp-server-webauthn4j-adapter/`

### 責務

WebAuthn/FIDO2実装（webauthn4jライブラリ統合）。

**仕様**: [Web Authentication Level 2](https://www.w3.org/TR/webauthn-2/)

### 主要機能

- **Registration**: 認証器登録（Passkey作成）
- **Authentication**: 認証器検証
- **Attestation**: 認証器証明（デバイス信頼性）
- **User Verification**: ユーザー検証（PIN/生体認証）

### WebAuthnExecutor インターフェース

**情報源**: [WebAuthnExecutor.java:23](../../../libs/idp-server-authentication-interactors/src/main/java/org/idp/server/authentication/interactors/fido2/WebAuthnExecutor.java#L23)

**重要**: `authentication-interactors` モジュールで定義（Core層ではない）

```java
/**
 * WebAuthn実行エンジン（Plugin）
 * 確認方法: 実ファイルの23-51行目
 */
public interface WebAuthnExecutor {

  // ✅ Executorタイプ
  WebAuthnExecutorType type();

  // ✅ 登録チャレンジ生成
  WebAuthnChallenge challengeRegistration(
      Tenant tenant,
      AuthenticationTransactionIdentifier authenticationTransactionIdentifier,
      AuthenticationInteractionRequest request,
      WebAuthnConfiguration configuration);

  // ✅ 登録検証
  WebAuthnVerificationResult verifyRegistration(
      Tenant tenant,
      AuthenticationTransactionIdentifier authenticationTransactionIdentifier,
      String userId,
      AuthenticationInteractionRequest request,
      WebAuthnConfiguration configuration);

  // ✅ 認証チャレンジ生成
  WebAuthnChallenge challengeAuthentication(
      Tenant tenant,
      AuthenticationTransactionIdentifier authenticationTransactionIdentifier,
      AuthenticationInteractionRequest request,
      WebAuthnConfiguration configuration);

  // ✅ 認証検証
  WebAuthnVerificationResult verifyAuthentication(
      Tenant tenant,
      AuthenticationTransactionIdentifier authenticationTransactionIdentifier,
      String userId,
      AuthenticationInteractionRequest request,
      WebAuthnConfiguration configuration);
}
```

### WebAuthn4jExecutor - webauthn4j実装

**情報源**: [WebAuthn4jExecutor.java:28](../../../libs/idp-server-webauthn4j-adapter/src/main/java/org/idp/server/authenticators/webauthn4j/WebAuthn4jExecutor.java#L28)

```java
/**
 * webauthn4jライブラリを使用したWebAuthn実装
 * 確認方法: 実ファイルの28-80行目
 */
public class WebAuthn4jExecutor implements WebAuthnExecutor {

  AuthenticationInteractionCommandRepository transactionCommandRepository;
  AuthenticationInteractionQueryRepository transactionQueryRepository;
  WebAuthn4jCredentialRepository credentialRepository;
  JsonConverter jsonConverter;

  @Override
  public WebAuthnExecutorType type() {
    return new WebAuthnExecutorType("webauthn4j");
  }

  @Override
  public WebAuthnChallenge challengeRegistration(
      Tenant tenant,
      AuthenticationTransactionIdentifier authenticationTransactionIdentifier,
      AuthenticationInteractionRequest request,
      WebAuthnConfiguration configuration) {

    // 1. チャレンジ生成
    WebAuthn4jChallenge webAuthn4jChallenge = WebAuthn4jChallenge.generate();
    WebAuthnChallenge fido2Challenge = webAuthn4jChallenge.toWebAuthnChallenge();

    // 2. チャレンジを一時保存（Redis/DB）
    transactionCommandRepository.register(
        tenant,
        authenticationTransactionIdentifier,
        type().value(),
        fido2Challenge);

    return fido2Challenge;
  }

  @Override
  public WebAuthnVerificationResult verifyRegistration(
      Tenant tenant,
      AuthenticationTransactionIdentifier authenticationTransactionIdentifier,
      String userId,
      AuthenticationInteractionRequest request,
      WebAuthnConfiguration configuration) {

    // 1. 保存されたチャレンジ取得
    WebAuthnChallenge fido2Challenge =
        transactionQueryRepository.get(
            tenant,
            authenticationTransactionIdentifier,
            type().value(),
            WebAuthnChallenge.class);

    // 2. webauthn4jで検証
    WebAuthn4jRegistrationManager registrationManager =
        new WebAuthn4jRegistrationManager(configuration);

    return registrationManager.verify(userId, request, fido2Challenge);
  }
}
```

**重要ポイント**:
- ✅ チャレンジは一時保存（AuthenticationInteractionRepository）
- ✅ Tenant第一引数パターン
- ✅ webauthn4jライブラリをラップ
- ✅ 登録と認証で別々のメソッド（`challengeRegistration` vs `challengeAuthentication`）

### Plugin登録

```
# META-INF/services/org.idp.server.authentication.interactors.fido2.Fido2ExecutorFactory
org.idp.server.authenticators.webauthn4j.WebAuthn4jExecutorFactory
```

### Attestation Format

```
- packed     : FIDO2標準形式
- fido-u2f   : FIDO U2F互換
- android-key: Android KeyStore
- android-safetynet: Android SafetyNet
- apple      : Apple Anonymous Attestation
- none       : Attestationなし
```

### User Verification

```java
// User Verification Required
UserVerificationRequirement.REQUIRED  // PIN/生体認証必須

// User Verification Preferred
UserVerificationRequirement.PREFERRED // 可能なら実施

// User Verification Discouraged
UserVerificationRequirement.DISCOURAGED // 不要
```

---

## idp-server-federation-oidc

**情報源**: `libs/idp-server-federation-oidc/`

### 責務

外部IdPとのOIDCフェデレーション（SSO連携）。

**仕様**: [OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html)

### 主要機能

- **Discovery**: `.well-known/openid-configuration`自動取得
- **Authorization Code Flow**: 外部IdPへのリダイレクト
- **Token Exchange**: 認可コード → トークン交換
- **UserInfo Fetch**: 外部IdPからユーザー情報取得
- **Account Linking**: 外部IDとローカルIDの紐付け

### FederationInteractor インターフェース

**情報源**: [FederationInteractor.java:26](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/federation/FederationInteractor.java#L26)

```java
/**
 * フェデレーションインタラクター
 * 確認方法: 実ファイルの26-40行目
 */
public interface FederationInteractor {

  // ✅ フェデレーションリクエスト（外部IdPへのリダイレクト）
  FederationRequestResponse request(
      Tenant tenant,
      AuthorizationRequestIdentifier authorizationRequestIdentifier,
      FederationType federationType,
      SsoProvider ssoProvider);

  // ✅ コールバック処理（外部IdPからの戻り）
  FederationInteractionResult callback(
      Tenant tenant,
      FederationType federationType,
      SsoProvider ssoProvider,
      FederationCallbackRequest callbackRequest,
      UserQueryRepository userQueryRepository);
}
```

### FederationInteractorFactory パターン

**情報源**: [OidcFederationInteractorFactory.java:27](../../../libs/idp-server-federation-oidc/src/main/java/org/idp/server/federation/sso/oidc/OidcFederationInteractorFactory.java#L27)

```java
/**
 * フェデレーションインタラクターのFactory
 * 確認方法: 実ファイルの27-52行目
 */
public interface FederationInteractorFactory {
  FederationType type();
  FederationInteractor create(FederationDependencyContainer container);
}

// OIDC実装
public class OidcFederationInteractorFactory implements FederationInteractorFactory {

  @Override
  public FederationType type() {
    return StandardSupportedFederationType.OIDC.toFederationType();
  }

  @Override
  public FederationInteractor create(FederationDependencyContainer container) {
    OidcSsoExecutors oidcSsoExecutors = container.resolve(OidcSsoExecutors.class);
    FederationConfigurationQueryRepository configurationQueryRepository =
        container.resolve(FederationConfigurationQueryRepository.class);
    // ... その他の依存関係を解決

    return new OidcFederationInteractor(...);
  }
}
```

### Plugin登録

```
# META-INF/services/org.idp.server.core.openid.federation.plugin.FederationInteractorFactory
org.idp.server.federation.sso.oidc.OidcFederationInteractorFactory
```

### フェデレーションフロー

```java
// 1. PluginLoaderでFactoryロード
List<FederationInteractorFactory> factories =
    PluginLoader.loadFromInternalModule(FederationInteractorFactory.class);

// 2. FederationType別にInteractor生成
FederationDependencyContainer container = new FederationDependencyContainer(...);
Map<FederationType, FederationInteractor> interactors = new HashMap<>();

for (FederationInteractorFactory factory : factories) {
  FederationInteractor interactor = factory.create(container);
  interactors.put(factory.type(), interactor);
}

// 3. フェデレーションリクエスト（外部IdPへリダイレクト）
FederationType federationType = FederationType.of("oidc");
FederationInteractor interactor = interactors.get(federationType);

FederationRequestResponse requestResponse = interactor.request(
    tenant,
    authorizationRequestIdentifier,
    federationType,
    ssoProvider);

// クライアントに返却するリダイレクトURL
String redirectUrl = requestResponse.redirectUrl();
// → https://accounts.google.com/o/oauth2/v2/auth?client_id=...&redirect_uri=...

// 4. コールバック処理（外部IdPから戻ってきた時）
FederationCallbackRequest callbackRequest = new FederationCallbackRequest(code, state);

FederationInteractionResult result = interactor.callback(
    tenant,
    federationType,
    ssoProvider,
    callbackRequest,
    userQueryRepository);

// 5. ユーザー情報取得
User externalUser = result.user();

// 6. アカウントリンキング（core.mdのIdentityドメインで説明）
```

### SsoProvider - SSO プロバイダー設定

```java
public class SsoProvider {
  String providerId;              // "google", "azure_ad", "facebook"等
  String providerName;            // "Google", "Azure AD", "Facebook"
  String issuer;                  // "https://accounts.google.com"
  String authorizationEndpoint;   // Discovery or 手動設定
  String tokenEndpoint;
  String userinfoEndpoint;
  String jwksUri;
  String clientId;
  String clientSecret;
  String redirectUri;
  List<String> scopes;            // ["openid", "profile", "email"]
}
```

---

## まとめ

### 認証・連携層を理解するための5つのポイント

1. **認証手段の多様性**: FIDO2/Password/SMS/Email/Device等を統一インターフェースで提供
2. **WebAuthn準拠**: webauthn4jによる堅牢なFIDO2実装
3. **フェデレーション**: 外部IdPとのOIDC連携・Account Linking
4. **Plugin パターン**: 認証手段を動的に追加可能
5. **User Verification**: 生体認証・PINによる強固な認証

### 次のステップ

- [通知・イベント層（Notification, Security Event）](./ai-50-notification-security-event.md)

---

## ドキュメント修正履歴

### 2025-10-12: 実装検証に基づく修正

#### 修正1: WebAuthnExecutor 定義場所の修正 (155-166行目)

**問題**: WebAuthnExecutorの定義モジュールが不正確

**修正前**:
```
**情報源**: Core層で定義（`org.idp.server.authentication.interactors.fido2.Fido2Executor`）
```

**修正後**:
```
**情報源**: [WebAuthnExecutor.java:23](...)
**重要**: `authentication-interactors` モジュールで定義（Core層ではない）
```

**理由**:
- WebAuthnExecutorは `idp-server-authentication-interactors` モジュールで定義
- Core層ではなく、認証機能層のインターフェース
- パッケージ: `org.idp.server.authentication.interactors.fido2`

**検証**: [WebAuthnExecutor.java:23-51](../../../libs/idp-server-authentication-interactors/src/main/java/org/idp/server/authentication/interactors/fido2/WebAuthnExecutor.java#L23-L51)

### 検証済み項目

#### ✅ AuthenticationInteractor インターフェース
- [AuthenticationInteractor.java:23-40](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/authentication/AuthenticationInteractor.java#L23-L40)
- ドキュメント記載と完全一致

#### ✅ WebAuthn4jExecutor 実装
- [WebAuthn4jExecutor.java:28-100](../../../libs/idp-server-webauthn4j-adapter/src/main/java/org/idp/server/authenticators/webauthn4j/WebAuthn4jExecutor.java#L28-L100)
- チャレンジ生成・検証ロジックが実装と一致

#### ✅ FederationInteractor インターフェース
- [FederationInteractor.java:26-40](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/federation/FederationInteractor.java#L26-L40)
- request(), callback() メソッドが実装と一致

### 修正の原則

**CLAUDE.md「想像ドキュメント作成防止」に基づく検証**:
1. **モジュール所属の正確性**: インターフェースがどのモジュールで定義されているかを正確に記載
2. **情報源記録**: ファイルパス・行番号を明記
3. **実装確認**: パッケージ構造を実際に確認

---

**情報源**:
- `libs/idp-server-authentication-interactors/`配下の実装コード
- `libs/idp-server-webauthn4j-adapter/`配下の実装コード
- `libs/idp-server-federation-oidc/`配下の実装コード
- `libs/idp-server-core/src/main/java/org/idp/server/core/openid/authentication/`
- `libs/idp-server-core/src/main/java/org/idp/server/core/openid/federation/`
- [intro-01-tech-overview.md](../content_01_intro/intro-01-tech-overview.md)
- [Web Authentication Level 2](https://www.w3.org/TR/webauthn-2/)

**最終更新**: 2025-10-12
**レビュー実施**: 2025-10-12 - AI開発者向けドキュメント品質改善プロジェクト
