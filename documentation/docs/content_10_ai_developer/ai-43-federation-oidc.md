# idp-server-federation-oidc

## モジュール概要

**情報源**: `libs/idp-server-federation-oidc/`
**確認日**: 2025-10-12

### 責務

外部IdPとのOIDCフェデレーション（SSO連携）。

**仕様**: [OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html)

### 主要機能

- **Discovery**: `.well-known/openid-configuration`自動取得
- **Authorization Code Flow**: 外部IdPへのリダイレクト
- **Token Exchange**: 認可コード → トークン交換
- **UserInfo Fetch**: 外部IdPからユーザー情報取得
- **Account Linking**: 外部IDとローカルIDの紐付け

## FederationInteractor インターフェース

**情報源**: [FederationInteractor.java:26](../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/federation/FederationInteractor.java#L26)

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

## FederationInteractorFactory パターン

**情報源**: [OidcFederationInteractorFactory.java:27](../../libs/idp-server-federation-oidc/src/main/java/org/idp/server/federation/sso/oidc/OidcFederationInteractorFactory.java#L27)

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

## Plugin登録

```
# META-INF/services/org.idp.server.core.openid.federation.plugin.FederationInteractorFactory
org.idp.server.federation.sso.oidc.OidcFederationInteractorFactory
```

## フェデレーションフロー

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

## SsoProvider - SSO プロバイダー設定

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

## 関連ドキュメント

- [認証・連携層統合ドキュメント](./ai-40-authentication-federation.md) - フェデレーションを含む全認証モジュール
- [idp-server-authentication-interactors](./authentication-federation.md#idp-server-authentication-interactors) - 認証インタラクター
- [idp-server-core](./ai-11-core.md) - OAuth/OIDCコアエンジン（Identityドメイン）

---

**情報源**:
- `libs/idp-server-federation-oidc/`配下の実装コード
- `libs/idp-server-core/src/main/java/org/idp/server/core/openid/federation/`
- [OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html)

**最終更新**: 2025-10-12
