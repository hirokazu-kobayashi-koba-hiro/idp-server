# idp-server-core-extension-pkce - PKCE拡張

## モジュール概要

**情報源**: `libs/idp-server-core-extension-pkce/`
**確認日**: 2025-10-12

### 責務

PKCE (Proof Key for Code Exchange) 実装。

**RFC**: [RFC 7636](https://www.rfc-editor.org/rfc/rfc7636.html)

### 主要機能

- **code_challenge**: SHA-256ハッシュ生成
- **code_verifier**: ランダム文字列検証
- **code_challenge_method**: `S256` / `plain`

## PKCE フロー

### 1. code_verifier 生成

```
code_verifier = 43-128文字のランダム文字列
例: dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk
```

### 2. code_challenge 生成

```
code_challenge = BASE64URL(SHA256(code_verifier))
例: E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM
```

### 3. Authorization Request

```
GET /authorize?
  response_type=code&
  client_id=s6BhdRkqt3&
  redirect_uri=https://client.example.org/cb&
  scope=openid&
  state=af0ifjsldkj&
  code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&
  code_challenge_method=S256
```

### 4. Token Request

```
POST /token
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code&
code=SplxlOBeZQQYbYS6WxSbIA&
redirect_uri=https://client.example.org/cb&
client_id=s6BhdRkqt3&
code_verifier=dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk
```

### 5. Verification

```
BASE64URL(SHA256(code_verifier)) == code_challenge
BASE64URL(SHA256(dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk)) == E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM
```

## code_challenge_method

### S256 (推奨)

```
code_challenge = BASE64URL(SHA256(ASCII(code_verifier)))
```

### plain

```
code_challenge = code_verifier
```

**注意**: `plain`は非推奨。セキュリティ上`S256`を使用すべき。

## Core層との統合

### PkceVerifier - Extension Verifier実装

**情報源**: [PkceVerifier.java:23](../../../libs/idp-server-core-extension-pkce/src/main/java/org/idp/server/core/openid/extension/pkce/PkceVerifier.java#L23)

```java
/**
 * PKCE Extension Verifier
 * Core層のAuthorizationCodeGrantVerifierから呼び出される
 */
public class PkceVerifier implements AuthorizationCodeGrantExtensionVerifierInterface {

  @Override
  public boolean shouldVerify(
      TokenRequestContext context,
      AuthorizationRequest authorizationRequest,
      AuthorizationCodeGrant authorizationCodeGrant,
      ClientCredentials clientCredentials) {
    // ✅ code_challengeがある場合のみ検証
    return context.isPkceRequest();
  }

  @Override
  public void verify(
      TokenRequestContext context,
      AuthorizationRequest authorizationRequest,
      AuthorizationCodeGrant authorizationCodeGrant,
      ClientCredentials clientCredentials) {
    // code_verifier検証
    // BASE64URL(SHA256(code_verifier)) == code_challenge
  }
}
```

**統合の仕組み**:
```
1. Core層のAuthorizationCodeGrantVerifierがPluginLoaderでPkceVerifierをロード
2. shouldVerify()でPKCEリクエストか判定
3. trueの場合、verify()でcode_verifier検証実行
4. 検証失敗時はOAuthRedirectableBadRequestException
```

**詳細**: [idp-server-core - Verifierパターン](./ai-11-core.md#verifierの階層パターンbase--extension)

## Plugin登録

```
# META-INF/services/org.idp.server.core.openid.oauth.plugin.AuthorizationRequestExtensionVerifier
org.idp.server.core.extension.pkce.PkceVerifier
```

## セキュリティ上の利点

1. **認可コード傍受対策**: code_verifierがないとトークン取得不可
2. **Public Client保護**: クライアントシークレット不要でも安全
3. **CSRF対策**: stateパラメータと併用で強固

---

## 次のステップ

- [拡張機能層トップに戻る](./ai-30-extensions.md)
- [他の拡張モジュール](./ai-30-extensions.md#概要)

---

**情報源**:
- `libs/idp-server-core-extension-pkce/`
- [RFC 7636 - PKCE](https://www.rfc-editor.org/rfc/rfc7636.html)

**最終更新**: 2025-10-12
