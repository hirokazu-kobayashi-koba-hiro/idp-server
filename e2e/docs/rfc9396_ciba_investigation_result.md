# RFC 9396 CIBA Authorization Details 検証問題 - 調査結果

## 問題の原因

CIBAのVerifierに`authorization_details`の検証が含まれていないため、無効なtypeがそのまま通過してトークンが発行されてしまう。

## コード分析

### 1. OAuthリクエスト検証（正常動作）

**ファイル**: `libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/verifier/OAuthRequestVerifier.java`

```java
public class OAuthRequestVerifier {
  Map<AuthorizationProfile, AuthorizationRequestVerifier> baseVerifiers = new HashMap<>();
  List<AuthorizationRequestExtensionVerifier> extensionVerifiers = new ArrayList<>();

  public OAuthRequestVerifier() {
    // 基本検証
    baseVerifiers.put(AuthorizationProfile.OAUTH2, new OAuth2RequestVerifier());
    baseVerifiers.put(AuthorizationProfile.OIDC, new OidcRequestVerifier());

    // 拡張検証
    extensionVerifiers.add(new RequestObjectVerifier());
    extensionVerifiers.add(new OAuthAuthorizationDetailsVerifier()); // ← 48行目: RAR検証あり
    extensionVerifiers.add(new JarmVerifier());
  }

  public void verify(OAuthRequestContext context) {
    // 基本検証実行
    baseRequestVerifier.verify(context);

    // 拡張検証実行
    extensionVerifiers.forEach(
        verifier -> {
          if (verifier.shouldVerify(context)) {
            verifier.verify(context); // ← authorization_details検証が実行される
          }
        });
  }
}
```

### 2. Authorization Details Verifier（実装済み）

**ファイル**: `libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/verifier/extension/AuthorizationDetailsVerifier.java`

```java
public class AuthorizationDetailsVerifier {
  public void verify() {
    throwExceptionIfNotContainsType();       // typeフィールド必須チェック
    throwExceptionIfUnauthorizedType();      // クライアント認可チェック
    throwExceptionIfUnSupportedType();       // サーバーサポートチェック
    throwExceptionIfUnauthorizedType();      // 重複（typo?）
  }

  void throwExceptionIfUnSupportedType() {
    authorizationDetails.forEach(
        authorizationDetail -> {
          if (!authorizationServerConfiguration.isSupportedAuthorizationDetailsType(
              authorizationDetail.type())) {
            throw new AuthorizationDetailsInvalidException(
                "invalid_authorization_details",
                String.format(
                    "unsupported authorization details type (%s)", authorizationDetail.type()));
          }
        });
  }

  void throwExceptionIfUnauthorizedType() {
    authorizationDetails.forEach(
        authorizationDetail -> {
          if (!clientConfiguration.isAuthorizedAuthorizationDetailsType(
              authorizationDetail.type())) {
            throw new AuthorizationDetailsInvalidException(
                "invalid_authorization_details",
                String.format(
                    "unauthorized authorization details type (%s)", authorizationDetail.type()));
          }
        });
  }
}
```

**重要**: この検証ロジックは完璧に実装されている！

### 3. OAuth Authorization Details Verifier（ラッパー）

**ファイル**: `libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/verifier/extension/OAuthAuthorizationDetailsVerifier.java`

```java
public class OAuthAuthorizationDetailsVerifier implements AuthorizationRequestExtensionVerifier {

  public boolean shouldVerify(OAuthRequestContext oAuthRequestContext) {
    return oAuthRequestContext.hasAuthorizationDetails(); // authorization_detailsがあれば検証
  }

  @Override
  public void verify(OAuthRequestContext context) {
    try {
      AuthorizationDetailsVerifier authorizationDetailsVerifier =
          new AuthorizationDetailsVerifier(
              context.authorizationRequest().authorizationDetails(),
              context.serverConfiguration(),
              context.clientConfiguration());
      authorizationDetailsVerifier.verify(); // ← 検証実行
    } catch (AuthorizationDetailsInvalidException exception) {
      throw new OAuthRedirectableBadRequestException(
          exception.error(), exception.errorDescription(), context);
    }
  }
}
```

### 4. CIBAリクエスト検証（**問題箇所**）

#### 4-1. Normal Profile Verifier

**ファイル**: `libs/idp-server-core-extension-ciba/src/main/java/org/idp/server/core/extension/ciba/verifier/CibaRequestNormalProfileVerifier.java`

```java
public class CibaRequestNormalProfileVerifier implements CibaVerifier {

  CibaRequestBaseVerifier baseVerifier;
  List<CibaExtensionVerifier> extensionVerifiers;

  public CibaRequestNormalProfileVerifier() {
    this.baseVerifier = new CibaRequestBaseVerifier();
    this.extensionVerifiers = new ArrayList<>();
    extensionVerifiers.add(new CibaRequestObjectVerifier()); // ← RequestObjectのみ
    // ❌ authorization_details検証がない！
  }

  @Override
  public void verify(CibaRequestContext context) {
    baseVerifier.verify(context); // 基本検証（grant type, scope, hint, user_code）
    extensionVerifiers.forEach(
        cibaExtensionVerifier -> {
          if (cibaExtensionVerifier.shouldNotVerify(context)) {
            return;
          }
          cibaExtensionVerifier.verify(context);
        });
  }
}
```

#### 4-2. FAPI Profile Verifier（**より深刻**）

**ファイル**: `libs/idp-server-core-extension-ciba/src/main/java/org/idp/server/core/extension/ciba/verifier/CibaRequestFapiProfileVerifier.java`

```java
public class CibaRequestFapiProfileVerifier implements CibaVerifier {

  CibaRequestBaseVerifier baseVerifier;
  // ❌❌ extensionVerifiersフィールドすらない！

  public CibaRequestFapiProfileVerifier() {
    this.baseVerifier = new CibaRequestBaseVerifier();
    // ❌❌ 拡張検証が一切ない！RequestObjectVerifierすらない！
  }

  @Override
  public void verify(CibaRequestContext context) {
    baseVerifier.verify(context); // 基本検証のみ
    // ❌❌ 拡張検証が実行されない
  }
}
```

**重大な問題**: FAPIプロファイルは`RequestObjectVerifier`すら持っていない！

**ファイル**: `libs/idp-server-core-extension-ciba/src/main/java/org/idp/server/core/extension/ciba/verifier/CibaRequestBaseVerifier.java`

```java
public class CibaRequestBaseVerifier {

  public void verify(CibaRequestContext context) {
    throwExceptionIfUnSupportedGrantType(context);
    throwExceptionIfNotContainsOpenidScope(context);
    throwExceptionIfNotContainsAnyHint(context);
    throwExceptionIfNotContainsUserCode(context);
    // ❌ authorization_details検証がない！
  }
}
```

## 修正方法

### Option 1: CibaAuthorizationDetailsVerifierを作成（推奨）

**新規ファイル**: `libs/idp-server-core-extension-ciba/src/main/java/org/idp/server/core/extension/ciba/verifier/CibaAuthorizationDetailsVerifier.java`

```java
package org.idp.server.core.extension.ciba.verifier;

import org.idp.server.core.extension.ciba.CibaRequestContext;
import org.idp.server.core.extension.ciba.exception.BackchannelAuthenticationBadRequestException;
import org.idp.server.core.openid.oauth.rar.AuthorizationDetailsInvalidException;
import org.idp.server.core.openid.oauth.verifier.extension.AuthorizationDetailsVerifier;

public class CibaAuthorizationDetailsVerifier implements CibaExtensionVerifier {

  @Override
  public boolean shouldNotVerify(CibaRequestContext context) {
    return !context.hasAuthorizationDetails();
  }

  @Override
  public void verify(CibaRequestContext context) {
    try {
      AuthorizationDetailsVerifier authorizationDetailsVerifier =
          new AuthorizationDetailsVerifier(
              context.authorizationDetails(),
              context.authorizationServerConfiguration(),
              context.clientConfiguration());
      authorizationDetailsVerifier.verify();
    } catch (AuthorizationDetailsInvalidException exception) {
      throw new BackchannelAuthenticationBadRequestException(
          exception.error(), exception.errorDescription());
    }
  }
}
```

**修正**: `CibaRequestNormalProfileVerifier.java`

```java
public CibaRequestNormalProfileVerifier() {
  this.baseVerifier = new CibaRequestBaseVerifier();
  this.extensionVerifiers = new ArrayList<>();
  extensionVerifiers.add(new CibaRequestObjectVerifier());
  extensionVerifiers.add(new CibaAuthorizationDetailsVerifier()); // ← 追加
}
```

**修正**: `CibaRequestFapiProfileVerifier.java`にも同様の追加が必要

```java
public CibaRequestFapiProfileVerifier() {
  this.baseVerifier = new CibaRequestBaseVerifier();
  this.extensionVerifiers = new ArrayList<>();
  extensionVerifiers.add(new CibaRequestObjectVerifier());
  extensionVerifiers.add(new CibaAuthorizationDetailsVerifier()); // ← 追加
}
```

### Option 2: CibaRequestBaseVerifierに追加

**修正**: `CibaRequestBaseVerifier.java`

```java
public void verify(CibaRequestContext context) {
  throwExceptionIfUnSupportedGrantType(context);
  throwExceptionIfNotContainsOpenidScope(context);
  throwExceptionIfNotContainsAnyHint(context);
  throwExceptionIfNotContainsUserCode(context);
  verifyAuthorizationDetails(context); // ← 追加
}

void verifyAuthorizationDetails(CibaRequestContext context) {
  if (!context.hasAuthorizationDetails()) {
    return;
  }

  try {
    AuthorizationDetailsVerifier authorizationDetailsVerifier =
        new AuthorizationDetailsVerifier(
            context.authorizationDetails(),
            context.authorizationServerConfiguration(),
            context.clientConfiguration());
    authorizationDetailsVerifier.verify();
  } catch (AuthorizationDetailsInvalidException exception) {
    throw new BackchannelAuthenticationBadRequestException(
        exception.error(), exception.errorDescription());
  }
}
```

## 推奨アプローチ

**Option 1を推奨**する理由：
1. **アーキテクチャ一貫性**: OAuthと同じ拡張検証パターン
2. **関心の分離**: Base検証とExtension検証の明確な分離
3. **拡張性**: 他のプロファイル（FAPI）でも再利用可能
4. **テスタビリティ**: 単体テストが容易

## 必要な実装ステップ

1. **新規クラス作成**: `CibaAuthorizationDetailsVerifier.java`
2. **Normal Profile修正**: `CibaRequestNormalProfileVerifier.java`
3. **FAPI Profile修正**: `CibaRequestFapiProfileVerifier.java`
4. **単体テスト作成**: `CibaAuthorizationDetailsVerifierTest.java`
5. **E2Eテスト確認**: `rfc9396_rar_ciba.test.js`が成功することを確認

## テスト期待結果

### Before（現在）
```
CIBA Response Status: 200
CIBA Response Data: {
  "auth_req_id": "...",  ← エラーではなく成功
  "interval": 5,
  "expires_in": 60
}

Token Response Status: 200  ← SPEC VIOLATION
Token Response Data: {
  "authorization_details": [
    {
      "type": "invalid_type",  ← 無効なtypeがそのまま
      ...
    }
  ]
}
```

### After（修正後）
```
CIBA Response Status: 400
CIBA Response Data: {
  "error": "invalid_authorization_details",
  "error_description": "unsupported authorization details type (invalid_type)"
}
```

## RFC 9396準拠確認

### Section 4 - Authorization Request

> If the authorization server does not support authorization details in general or the specific authorization details type... the authorization server returns the error code invalid_authorization_details.

✅ Backchannel Authentication Endpointで即座にエラーを返す

### Section 7 - Token Response

> The AS MUST refuse to process any unknown authorization details type.

✅ Token Endpointに到達する前にエラーで拒否される

## 関連ファイル

### 実装ファイル
- `libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/verifier/extension/AuthorizationDetailsVerifier.java` ✅ 実装済み
- `libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/verifier/extension/OAuthAuthorizationDetailsVerifier.java` ✅ 参考実装
- `libs/idp-server-core-extension-ciba/src/main/java/org/idp/server/core/extension/ciba/verifier/CibaRequestNormalProfileVerifier.java` ❌ 要修正
- `libs/idp-server-core-extension-ciba/src/main/java/org/idp/server/core/extension/ciba/verifier/CibaRequestFapiProfileVerifier.java` ❌ 要修正

### テストファイル
- `e2e/src/tests/spec/rfc9396_rar_ciba.test.js` - E2Eテスト
- `e2e/docs/rfc9396_test_investigation.md` - 調査手順
- `e2e/docs/rfc9396_ciba_investigation_result.md` - 本ドキュメント
