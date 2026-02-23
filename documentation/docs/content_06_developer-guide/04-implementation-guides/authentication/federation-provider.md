# Federation

## このドキュメントの目的

**新しいSsoProvider**（外部IdP連携）を追加できるようになることが目標です。

### 所要時間
⏱️ **約60分**（実装 + テスト）

### 前提知識
- [impl-12-plugin-implementation.md](./impl-12-plugin-implementation.md) - Plugin実装パターン
- [Application Plane: 08-federation.md](../03-application-plane/08-federation.md) - フェデレーション概要
- OpenID Connect基礎知識

---

## 新しいSsoProviderの追加

新しい外部IdP（例: GitHub、LINE、Apple等）を追加する手順。

---

## Step 1: SsoProviderの定義

**ファイル**: `libs/idp-server-core/src/main/java/org/idp/server/core/openid/federation/sso/SsoProvider.java`

```java
public enum SsoProvider {
  GOOGLE("google"),
  AZURE_AD("azure_ad"),
  GENERIC_OIDC("generic_oidc"),
  GITHUB("github");  // ← 新規追加

  private final String value;

  SsoProvider(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
```

---

## Step 2: OidcSsoExecutor実装

**ファイル**: `libs/idp-server-federation-oidc/src/main/java/org/idp/server/federation/sso/oidc/github/GitHubOidcSsoExecutor.java`

```java
package org.idp.server.federation.sso.oidc.github;

import org.idp.server.core.openid.federation.sso.oidc.*;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import java.util.Map;

public class GitHubOidcSsoExecutor implements OidcSsoExecutor {

  @Override
  public SsoProvider ssoProvider() {
    return SsoProvider.GITHUB;  // ← Plugin識別キー
  }

  @Override
  public OidcSsoSession createOidcSession(
      Tenant tenant,
      AuthorizationRequestIdentifier authorizationRequestIdentifier,
      OidcSsoConfiguration configuration,
      FederationType federationType,
      SsoProvider ssoProvider) {

    // 1. state/nonce/code_verifier生成
    String state = UUID.randomUUID().toString();
    String nonce = UUID.randomUUID().toString();
    String codeVerifier = PkceGenerator.generateCodeVerifier();
    String codeChallenge = PkceGenerator.generateCodeChallenge(codeVerifier);

    // 2. Authorization URL生成（GitHub固有）
    String authorizationUrl = buildAuthorizationUrl(configuration, state, nonce, codeChallenge);

    // 3. OidcSsoSession作成
    return new OidcSsoSession(
        new SsoSessionIdentifier(state),
        authorizationRequestIdentifier,
        federationType,
        ssoProvider,
        state,
        nonce,
        codeVerifier,
        authorizationUrl);
  }

  private String buildAuthorizationUrl(
      OidcSsoConfiguration configuration,
      String state,
      String nonce,
      String codeChallenge) {

    // GitHub固有のパラメータ構築
    return configuration.authorizationEndpoint() +
        "?client_id=" + configuration.clientId() +
        "&redirect_uri=" + configuration.redirectUri() +
        "&response_type=code" +
        "&scope=" + String.join(" ", configuration.scopes()) +
        "&state=" + state +
        "&nonce=" + nonce +
        "&code_challenge=" + codeChallenge +
        "&code_challenge_method=S256";
  }

  @Override
  public OidcSsoTokenResponse requestToken(
      Tenant tenant,
      OidcSsoSession session,
      OidcSsoConfiguration configuration,
      FederationCallbackRequest request) {

    // 1. Token Request（GitHub Token Endpoint）
    HttpRequest tokenRequest = HttpRequest.post(configuration.tokenEndpoint())
        .header("Content-Type", "application/x-www-form-urlencoded")
        .body("grant_type=authorization_code" +
            "&code=" + request.code() +
            "&client_id=" + configuration.clientId() +
            "&client_secret=" + configuration.clientSecret() +
            "&redirect_uri=" + configuration.redirectUri() +
            "&code_verifier=" + session.codeVerifier());

    HttpResponse response = httpClient.execute(tokenRequest);

    // 2. レスポンスパース
    Map<String, Object> tokenData = JsonConverter.defaultInstance()
        .readAsMap(response.body());

    return new OidcSsoTokenResponse(
        new AccessToken(tokenData.get("access_token").toString()),
        new RefreshToken(tokenData.get("refresh_token").toString()),
        IdToken.parse(tokenData.get("id_token").toString()));
  }

  @Override
  public void verifyIdToken(
      IdToken idToken,
      OidcSsoSession session,
      OidcSsoConfiguration configuration) {

    // 1. JWKSから公開鍵取得
    JwkSet jwkSet = fetchJwkSet(configuration.jwksUri());

    // 2. 署名検証
    boolean signatureValid = JwtVerifier.verify(idToken.value(), jwkSet);
    if (!signatureValid) {
      throw new FederationException("ID token signature verification failed");
    }

    // 3. iss検証（GitHub固有のissuer）
    if (!idToken.iss().equals(configuration.issuer())) {
      throw new FederationException("Invalid issuer: " + idToken.iss());
    }

    // 4. aud検証
    if (!idToken.aud().contains(configuration.clientId())) {
      throw new FederationException("Invalid audience");
    }

    // 5. exp検証
    if (idToken.isExpired()) {
      throw new FederationException("ID token has expired");
    }

    // 6. nonce検証
    if (!idToken.nonce().equals(session.nonce())) {
      throw new FederationException("Nonce mismatch");
    }
  }

  @Override
  public Map<String, Object> requestUserInfo(
      Tenant tenant,
      AccessToken accessToken,
      OidcSsoConfiguration configuration) {

    // 1. UserInfo Request（GitHub UserInfo Endpoint）
    HttpRequest request = HttpRequest.get(configuration.userinfoEndpoint())
        .header("Authorization", "Bearer " + accessToken.value());

    HttpResponse response = httpClient.execute(request);

    // 2. レスポンスパース（GitHub固有のフィールド構造対応）
    Map<String, Object> userInfo = JsonConverter.defaultInstance()
        .readAsMap(response.body());

    return userInfo;
  }
}
```

---

## Step 3: Plugin登録（META-INF/services）

**ファイル**: `libs/idp-server-federation-oidc/src/main/resources/META-INF/services/org.idp.server.core.openid.federation.sso.oidc.OidcSsoExecutor`

```
org.idp.server.federation.sso.oidc.google.GoogleOidcSsoExecutor
org.idp.server.federation.sso.oidc.azuread.AzureAdOidcSsoExecutor
org.idp.server.federation.sso.oidc.generic.GenericOidcSsoExecutor
org.idp.server.federation.sso.oidc.github.GitHubOidcSsoExecutor
```

**確認**:
```bash
./gradlew build
# → ServiceLoaderでGitHubOidcSsoExecutorが自動ロードされる
```

---

## Step 4: プロバイダー別の注意事項

### Google固有

- **issuer**: `https://accounts.google.com`
- **UserInfoフィールド**: 標準OIDC準拠
- **JWKSキャッシュ**: 必須（頻繁に公開鍵が変わらない）

### Azure AD固有

- **issuer**: `https://login.microsoftonline.com/{tenant-id}/v2.0`
- **UserInfoフィールド**: Microsoft Graph API形式（`userPrincipalName`等）
- **multi-tenant対応**: `organizations` または `common` エンドポイント

### GitHub固有（例）

- **issuer**: `https://token.actions.githubusercontent.com`
- **UserInfoフィールド**: `login`（GitHubユーザー名）
- **email取得**: UserInfo APIで`email`が返らない場合、別途Email API呼び出し

---

## テスト

### E2Eテスト例

```javascript
describe('GitHub Federation', () => {
  test('should authenticate with GitHub', async () => {
    // 1. Federation Request
    const response = await axios.post(
      `http://localhost:8080/${tenantId}/v1/authentications/${authReqId}/federations/oidc/github`,
      {},
      { maxRedirects: 0, validateStatus: status => status === 302 }
    );

    expect(response.status).toBe(302);
    expect(response.headers.location).toContain('github.com');

    // 2. GitHub認証（実際はブラウザで実行）
    // 3. Callbackシミュレーション
    const callbackResponse = await axios.get(
      `http://localhost:8080/${tenantId}/v1/federations/callback/oidc/github`,
      {
        params: { code: 'github-code-123', state: extractedState },
        maxRedirects: 0
      }
    );

    expect(callbackResponse.status).toBe(302);
    // 認証完了 → Authorization Request画面へリダイレクト
  });
});
```

---

## チェックリスト

新しいSsoProvider実装時の確認項目：

### OidcSsoExecutor実装
- [ ] `ssoProvider()`メソッド実装（Plugin識別）
- [ ] `createOidcSession()`実装（Authorization URL生成）
- [ ] `requestToken()`実装（Token Request）
- [ ] `verifyIdToken()`実装（ID Token検証）
- [ ] `requestUserInfo()`実装（UserInfo取得）

### プロバイダー固有対応
- [ ] issuer検証（プロバイダー固有のissuer）
- [ ] UserInfoフィールドマッピング
- [ ] emailアドレス取得戦略
- [ ] JWKSキャッシュ戦略

### Plugin登録
- [ ] META-INF/services に追加
- [ ] PluginLoader動作確認

### テスト
- [ ] Authorization URL生成テスト
- [ ] ID Token検証テスト
- [ ] UserInfo取得テスト
- [ ] E2Eテスト（実際の外部IdP）

---

## 次のステップ

✅ 新しいSsoProviderの追加方法を理解した！

### 🔗 詳細情報

- [Application Plane: フェデレーション](../03-application-plane/08-federation.md)
- [OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html)

---

**情報源**: [OidcFederationInteractor.java](../../../../libs/idp-server-federation-oidc/src/main/java/org/idp/server/federation/sso/oidc/OidcFederationInteractor.java)
**最終更新**: 2025-10-13
