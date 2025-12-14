# FAPI実装ガイド

## 📍 このドキュメントの位置づけ

**対象読者**: 金融グレードのセキュリティを実装したい開発者

**このドキュメントで学べること**:
- FAPI Baseline / Advance Profile の実装詳細
- mTLS (Mutual TLS) クライアント認証の仕組み
- Sender-constrained Access Tokens の実装
- PAR (Pushed Authorization Requests) の実装
- JARM (JWT Secured Authorization Response Mode) の実装
- Request Object 検証の詳細

**前提知識**:
- [concept-22: FAPI](../../content_03_concepts/concept-11-fapi.md)の理解
- [basic-08: 認可コードフロー](../../content_03_concepts/basic/basic-08-oauth2-authorization-code-flow.md)の理解
- OAuth 2.0 / OIDC の基礎知識

---

## 🏗️ FAPI アーキテクチャ概要

**FAPI (Financial-grade API)** は、金融取引など高セキュリティが要求される環境で OAuth 2.0/OIDC を安全に利用するための仕様です。

### idp-serverのFAPI対応

| プロファイル | 用途 | 実装モジュール | 主要コンポーネント |
|------------|------|--------------|------------------|
| **FAPI Baseline** | 読み取り専用API | `idp-server-core-extension-fapi` | `FapiBaselineVerifier` |
| **FAPI Advance** | 書き込みAPI | `idp-server-core-extension-fapi` | `FapiAdvanceVerifier` |
| **FAPI CIBA** | デバイス分離認証 | `idp-server-core-extension-fapi-ciba` | (CIBAモジュール) |

### プロファイル自動判定

idp-serverは、**リクエストされたスコープ**に基づいてFAPIプロファイルを自動判定します。

```
判定ロジック（優先順位順）:
1. スコープが fapiAdvanceScopes に一致 → FAPI Advance
2. スコープが fapiBaselineScopes に一致 → FAPI Baseline
3. スコープに openid が含まれる → OIDC
4. それ以外 → OAuth 2.0
```

**テナント設定例**:
```json
{
  "extension": {
    "fapi_baseline_scopes": ["read", "account"],
    "fapi_advance_scopes": ["write", "transfers", "payment_initiation"]
  }
}
```

---

## 📋 FAPI Baseline Profile 実装

### 概要

**FAPI Baseline** は、読み取り専用API（残高照会、取引履歴参照等）向けのプロファイルです。

**主要要件**:
- ✅ 署名付きリクエストオブジェクト（PS256/ES256）
- ✅ PKCE必須（S256のみ）
- ✅ 強固なクライアント認証（private_key_jwt または mTLS）
- ✅ HTTPSリダイレクトURI必須
- ✅ nonce（OIDCの場合）または state（OAuth 2.0の場合）必須

### 実装: FapiBaselineVerifier

```java
public class FapiBaselineVerifier implements AuthorizationRequestVerifier {

  OAuthRequestBaseVerifier oAuthRequestBaseVerifier = new OAuthRequestBaseVerifier();
  OidcRequestBaseVerifier oidcRequestBaseVerifier = new OidcRequestBaseVerifier();

  public AuthorizationProfile profile() {
    return AuthorizationProfile.FAPI_BASELINE;
  }

  @Override
  public void verify(OAuthRequestContext context) {
    // 1. リダイレクトURI検証
    throwExceptionIfUnregisteredRedirectUri(context);
    throwExceptionIfNotContainsRedirectUri(context);
    throwExceptionUnMatchRedirectUri(context);
    throwExceptionIfNotHttpsRedirectUri(context);

    // 2. 基本検証（OIDC or OAuth 2.0）
    if (context.isOidcRequest()) {
      oidcRequestBaseVerifier.verify(context);
    } else {
      oAuthRequestBaseVerifier.verify(context);
    }

    // 3. FAPI Baseline固有検証
    throwExceptionIfClientSecretPostOrClientSecretBasic(context);
    throwExceptionIfNotS256CodeChallengeMethod(context);
    throwExceptionIfHasOpenidScopeAndNotContainsNonce(context);
    throwExceptionIfNotHasOpenidScopeAndNotContainsState(context);
  }
}
```

**参考実装**: [FapiBaselineVerifier.java:31](../../../libs/idp-server-core-extension-fapi/src/main/java/org/idp/server/core/openid/extension/fapi/FapiBaselineVerifier.java#L31)

### 1. リダイレクトURI検証

#### shall require redirect URIs to be pre-registered

```java
void throwExceptionIfUnregisteredRedirectUri(OAuthRequestContext context) {
  ClientConfiguration clientConfiguration = context.clientConfiguration();
  if (!clientConfiguration.hasRedirectUri()) {
    throw new OAuthBadRequestException(
        "invalid_request",
        "When FAPI Baseline profile, shall require redirect URIs to be pre-registered",
        context.tenant());
  }
}
```

#### shall require the redirect_uri in the authorization request

```java
void throwExceptionIfNotContainsRedirectUri(OAuthRequestContext context) {
  if (!context.hasRedirectUriInRequest()) {
    throw new OAuthBadRequestException(
        "invalid_request",
        "When FAPI Baseline profile, shall require the redirect_uri in the authorization request",
        context.tenant());
  }
}
```

#### shall require redirect URIs to use the https scheme

```java
void throwExceptionIfNotHttpsRedirectUri(OAuthRequestContext context) {
  RedirectUri redirectUri = context.redirectUri();
  if (!redirectUri.isHttps()) {
    throw new OAuthBadRequestException(
        "invalid_request",
        "When FAPI Baseline profile, shall require redirect URIs to use the https scheme",
        context.tenant());
  }
}
```

### 2. クライアント認証検証

#### shall authenticate using one of: mTLS, client_secret_jwt, private_key_jwt

```java
void throwExceptionIfClientSecretPostOrClientSecretBasic(OAuthRequestContext context) {
  ClientAuthenticationType clientAuthenticationType = context.clientAuthenticationType();

  // client_secret_basic は禁止
  if (clientAuthenticationType.isClientSecretBasic()) {
    throw new OAuthRedirectableBadRequestException(
        "unauthorized_client",
        "When FAPI Baseline profile, client_secret_basic MUST not be used",
        context);
  }

  // client_secret_post は禁止
  if (clientAuthenticationType.isClientSecretPost()) {
    throw new OAuthRedirectableBadRequestException(
        "unauthorized_client",
        "When FAPI Baseline profile, client_secret_post MUST not be used",
        context);
  }
}
```

**許可される認証方式**:
- ✅ `private_key_jwt` - 秘密鍵によるJWT署名
- ✅ `client_secret_jwt` - 共有秘密によるJWT署名
- ✅ `tls_client_auth` - mTLS（証明書による認証）
- ✅ `self_signed_tls_client_auth` - 自己署名証明書による認証

### 3. PKCE検証

#### shall require RFC7636 with S256 as the code challenge method

```java
void throwExceptionIfNotS256CodeChallengeMethod(OAuthRequestContext context) {
  AuthorizationRequest authorizationRequest = context.authorizationRequest();

  // code_challenge と code_challenge_method が必須
  if (!authorizationRequest.hasCodeChallenge()
      || !authorizationRequest.hasCodeChallengeMethod()) {
    throw new OAuthRedirectableBadRequestException(
        "invalid_request",
        "When FAPI Baseline profile, authorization request must contain code_challenge and code_challenge_method(S256).",
        context);
  }

  // code_challenge_method は S256 のみ
  if (!authorizationRequest.codeChallengeMethod().isS256()) {
    throw new OAuthRedirectableBadRequestException(
        "invalid_request",
        "When FAPI Baseline profile, shall require RFC7636 with S256 as the code challenge method.",
        context);
  }
}
```

**PKCE検証フロー**:
```
1. 認可リクエスト:   code_challenge=xxx&code_challenge_method=S256
2. 認可コード発行:   code=yyy
3. トークンリクエスト: code=yyy&code_verifier=zzz
4. 検証:             SHA256(zzz) == xxx
```

### 4. Nonce / State検証

#### Client requesting openid scope: shall require the nonce parameter

```java
void throwExceptionIfHasOpenidScopeAndNotContainsNonce(OAuthRequestContext context) {
  if (!context.hasOpenidScope()) {
    return;  // openid スコープなしの場合はスキップ
  }

  if (!context.authorizationRequest().hasNonce()) {
    throw new OAuthRedirectableBadRequestException(
        "invalid_request",
        "When FAPI Baseline profile, shall require the nonce parameter in the authentication request.",
        context);
  }
}
```

#### Clients not requesting openid scope: shall require the state parameter

```java
void throwExceptionIfNotHasOpenidScopeAndNotContainsState(OAuthRequestContext context) {
  if (context.hasOpenidScope()) {
    return;  // openid スコープありの場合はスキップ
  }

  if (!context.authorizationRequest().hasState()) {
    throw new OAuthRedirectableBadRequestException(
        "invalid_request",
        "When FAPI Baseline profile, shall require the state parameter.",
        context);
  }
}
```

---

## 🔐 FAPI Advance Profile 実装

### 概要

**FAPI Advance** は、書き込みAPI（送金実行、口座設定変更等）向けのプロファイルです。

**Baselineに追加される要件**:
- ✅ PAR（Pushed Authorization Requests）必須
- ✅ JARM（JWT Secured Authorization Response）必須
- ✅ Sender-constrained アクセストークン必須（mTLS binding）
- ✅ Request Object 有効期限: 最大60分
- ✅ `aud` claim必須（Issuer URL）
- ✅ Publicクライアント禁止
- ✅ `client_secret_jwt` も禁止（`private_key_jwt` または mTLS のみ）

### 実装: FapiAdvanceVerifier

```java
public class FapiAdvanceVerifier implements AuthorizationRequestVerifier {

  public AuthorizationProfile profile() {
    return AuthorizationProfile.FAPI_ADVANCE;
  }

  @Override
  public void verify(OAuthRequestContext context) {
    // 1. 設定検証
    throwIfExceptionInvalidConfig(context);

    // 2. 基本検証
    if (context.isOidcRequest()) {
      oidcRequestBaseVerifier.verify(context);
    } else {
      oAuthRequestBaseVerifier.verify(context);
    }

    // 3. FAPI Advance固有検証
    throwExceptionIfNotRRequestParameterPattern(context);
    throwExceptionIfInvalidResponseTypeAndResponseMode(context);
    throwIfNotSenderConstrainedAccessToken(context);
    throwExceptionIfNotContainExpAndNbfAndExp60minutesLongerThanNbf(context);
    throwExceptionIfNotContainsAud(context);
    throwExceptionIfClientSecretPostOrClientSecretBasicOrClientSecretJwt(context);
    throwExceptionIfPublicClient(context);
    throwExceptionIfNotContainNbfAnd60minutesLongerThan(context);
  }
}
```

**参考実装**: [FapiAdvanceVerifier.java:34](../../../libs/idp-server-core-extension-fapi/src/main/java/org/idp/server/core/openid/extension/fapi/FapiAdvanceVerifier.java#L34)

### 1. Request Object 必須

#### shall require a JWS signed JWT request object

```java
void throwExceptionIfNotRRequestParameterPattern(OAuthRequestContext context) {
  if (!context.isRequestParameterPattern()) {
    throw new OAuthRedirectableBadRequestException(
        "invalid_request",
        "When FAPI Advance profile, shall require a JWS signed JWT request object passed by value with the request parameter or by reference with the request_uri parameter",
        context);
  }
}
```

**Request Object パターン**:
- **By Value**: `request=eyJhbGciOiJQUzI1NiIs...`
- **By Reference**: `request_uri=https://client.example.com/request/abcd1234`

### 2. Response Type / Response Mode 検証

#### shall require response_type code id_token, or code + response_mode jwt

```java
void throwExceptionIfInvalidResponseTypeAndResponseMode(OAuthRequestContext context) {
  // パターン1: response_type=code id_token (Hybrid Flow)
  if (context.responseType().isCodeIdToken()) {
    return;
  }

  // パターン2: response_type=code&response_mode=jwt (JARM)
  if (context.responseType().isCode() && context.responseMode().isJwt()) {
    return;
  }

  throw new OAuthRedirectableBadRequestException(
      "invalid_request",
      "When FAPI Advance profile, shall require the response_type value code id_token, or the response_type value code in conjunction with the response_mode value jwt",
      context);
}
```

**許可されるパターン**:
- ✅ `response_type=code id_token` - Hybrid Flow
- ✅ `response_type=code&response_mode=jwt` - JARM

### 3. Sender-constrained Access Tokens

#### shall only issue sender-constrained access tokens

```java
void throwIfNotSenderConstrainedAccessToken(OAuthRequestContext context) {
  AuthorizationServerConfiguration authorizationServerConfiguration =
      context.serverConfiguration();
  ClientConfiguration clientConfiguration = context.clientConfiguration();

  // サーバー側で有効化されているか
  if (!authorizationServerConfiguration.isTlsClientCertificateBoundAccessTokens()) {
    throw new OAuthRedirectableBadRequestException(
        "invalid_request",
        "When FAPI Advance profile, shall only issue sender-constrained access tokens, but server tls_client_certificate_bound_access_tokens is false",
        context);
  }

  // クライアント側で有効化されているか
  if (!clientConfiguration.isTlsClientCertificateBoundAccessTokens()) {
    throw new OAuthRedirectableBadRequestException(
        "invalid_request",
        "When FAPI Advance profile, shall only issue sender-constrained access tokens, but client tls_client_certificate_bound_access_tokens is false",
        context);
  }
}
```

**Sender-constrained Access Tokens の仕組み**:
```
1. クライアント証明書のサムプリント（SHA-256）を計算
2. アクセストークン（JWT）の cnf クレームに格納
   {
     "cnf": {
       "x5t#S256": "bwcK0esc3ACC3DB2Y5_lESsXE8o9ltc05O89jdN-dg2"
     }
   }
3. API呼び出し時、サーバーは証明書とサムプリントを照合
4. 一致した場合のみAPIアクセスを許可
```

### 4. Request Object 有効期限検証

#### shall require exp claim that has a lifetime of no longer than 60 minutes after nbf

```java
void throwExceptionIfNotContainExpAndNbfAndExp60minutesLongerThanNbf(
    OAuthRequestContext context) {

  JoseContext joseContext = context.joseContext();
  JsonWebTokenClaims claims = joseContext.claims();

  // exp クレーム必須
  if (!claims.hasExp()) {
    throw new OAuthRedirectableBadRequestException(
        "invalid_request_object",
        "When FAPI Advance profile, shall require the request object to contain an exp claim",
        context);
  }

  // nbf クレーム必須
  if (!claims.hasNbf()) {
    throw new OAuthRedirectableBadRequestException(
        "invalid_request_object",
        "When FAPI Advance profile, shall require the request object to contain an nbf claim",
        context);
  }

  // exp - nbf <= 60分
  Date exp = claims.getExp();
  Date nbf = claims.getNbf();
  if (exp.getTime() - nbf.getTime() > 3600001) {  // 60分 + 1ミリ秒
    throw new OAuthRedirectableBadRequestException(
        "invalid_request_object",
        "When FAPI Advance profile, shall require the request object to contain an exp claim that has a lifetime of no longer than 60 minutes after the nbf claim",
        context);
  }
}
```

### 5. Audience (aud) 検証

#### shall require the aud claim to contain the OP's Issuer Identifier URL

```java
void throwExceptionIfNotContainsAud(OAuthRequestContext context) {
  JoseContext joseContext = context.joseContext();
  JsonWebTokenClaims claims = joseContext.claims();

  // aud クレーム必須
  if (!claims.hasAud()) {
    throw new OAuthRedirectableBadRequestException(
        "invalid_request_object",
        "When FAPI Advance profile, shall require the request object to contain an aud claim",
        context);
  }

  // aud に Issuer URL が含まれているか
  List<String> aud = claims.getAud();
  if (!aud.contains(context.tokenIssuer().value())) {
    throw new OAuthRedirectableBadRequestException(
        "invalid_request_object",
        String.format(
            "When FAPI Advance profile, shall require the aud claim in the request object to contain the OP's Issuer Identifier URL (%s)",
            String.join(" ", aud)),
        context);
  }
}
```

### 6. クライアント認証検証（Advance限定）

#### shall authenticate using: tls_client_auth, self_signed_tls_client_auth, or private_key_jwt

```java
void throwExceptionIfClientSecretPostOrClientSecretBasicOrClientSecretJwt(
    OAuthRequestContext context) {

  ClientAuthenticationType clientAuthenticationType = context.clientAuthenticationType();

  // client_secret_basic 禁止
  if (clientAuthenticationType.isClientSecretBasic()) {
    throw new OAuthRedirectableBadRequestException(
        "unauthorized_client",
        "When FAPI Advance profile, client_secret_basic MUST not be used",
        context);
  }

  // client_secret_post 禁止
  if (clientAuthenticationType.isClientSecretPost()) {
    throw new OAuthRedirectableBadRequestException(
        "unauthorized_client",
        "When FAPI Advance profile, client_secret_post MUST not be used",
        context);
  }

  // client_secret_jwt も禁止（Advance では）
  if (clientAuthenticationType.isClientSecretJwt()) {
    throw new OAuthRedirectableBadRequestException(
        "unauthorized_client",
        "When FAPI Advance profile, client_secret_jwt MUST not be used",
        context);
  }
}
```

**FAPI Advance で許可される認証方式**:
- ✅ `private_key_jwt` - 秘密鍵によるJWT署名
- ✅ `tls_client_auth` - mTLS（証明書による認証）
- ✅ `self_signed_tls_client_auth` - 自己署名証明書による認証

**FAPI Baseline で許可される認証方式**（参考）:
- ✅ `private_key_jwt`
- ✅ `client_secret_jwt` ← Advanceでは禁止
- ✅ `tls_client_auth`
- ✅ `self_signed_tls_client_auth`

### 7. Public Client 禁止

```java
void throwExceptionIfPublicClient(OAuthRequestContext context) {
  ClientAuthenticationType clientAuthenticationType = context.clientAuthenticationType();

  if (clientAuthenticationType.isNone()) {
    throw new OAuthRedirectableBadRequestException(
        "unauthorized_client",
        "When FAPI Advance profile, shall not support public clients",
        context);
  }
}
```

---

## 🔧 mTLS クライアント認証実装

### TlsClientAuthAuthenticator

`tls_client_auth` 方式のクライアント認証を実装します。

```java
public class TlsClientAuthAuthenticator implements ClientAuthenticator {

  @Override
  public ClientAuthenticationType type() {
    return ClientAuthenticationType.tls_client_auth;
  }

  @Override
  public ClientCredentials authenticate(BackchannelRequestContext context) {
    RequestedClientId requestedClientId = context.requestedClientId();

    // 1. クライアント証明書が存在するか確認
    throwExceptionIfNotContainsClientCert(context);

    // 2. X.509証明書をパースして検証
    X509Certification x509Certification = parseOrThrowExceptionIfNoneMatch(context);

    // 3. ClientCredentials生成
    ClientCertification clientCertification = new ClientCertification(x509Certification);

    return new ClientCredentials(
        requestedClientId,
        ClientAuthenticationType.tls_client_auth,
        new ClientSecret(),
        new ClientAuthenticationPublicKey(),
        new ClientAssertionJwt(),
        clientCertification);
  }

  X509Certification parseOrThrowExceptionIfNoneMatch(BackchannelRequestContext context) {
    ClientCert clientCert = context.clientCert();
    X509Certification x509Certification = X509Certification.parse(clientCert.plainValue());
    ClientConfiguration clientConfiguration = context.clientConfiguration();

    // 証明書検証（3つの方式のいずれかで一致）
    // 1. tls_client_auth_subject_dn
    if (x509Certification.subject().equals(clientConfiguration.tlsClientAuthSubjectDn())) {
      return x509Certification;
    }

    // 2. tls_client_auth_san_dns (dNSName)
    if (x509Certification.hasDNSName()
        && x509Certification.dNSName().equals(clientConfiguration.tlsClientAuthSanDns())) {
      return x509Certification;
    }

    // 3. tls_client_auth_san_uri (uniformResourceIdentifier)
    if (x509Certification.hasUniformResourceIdentifier()
        && x509Certification.uniformResourceIdentifier()
            .equals(clientConfiguration.tlsClientAuthSanUri())) {
      return x509Certification;
    }

    throw new ClientUnAuthorizedException("client certificate verification failed");
  }
}
```

**参考実装**: [TlsClientAuthAuthenticator.java:35](../../../libs/idp-server-core-extension-fapi/src/main/java/org/idp/server/core/openid/extension/fapi/TlsClientAuthAuthenticator.java#L35)

**証明書検証方式**:
1. **subject DN**: 証明書のSubject Distinguished Nameで検証
2. **SAN DNS**: Subject Alternative Name の dNSName フィールドで検証
3. **SAN URI**: Subject Alternative Name の uniformResourceIdentifier フィールドで検証

---

## 📋 実装チェックリスト

FAPI対応を実装する際のチェックリスト:

### FAPI Baseline

- [ ] **リダイレクトURI**:
  - [ ] 事前登録必須
  - [ ] HTTPSスキーム必須
  - [ ] 完全一致検証

- [ ] **PKCE**:
  - [ ] S256必須
  - [ ] `code_challenge` / `code_challenge_method` パラメータ必須

- [ ] **クライアント認証**:
  - [ ] `client_secret_basic` 禁止
  - [ ] `client_secret_post` 禁止
  - [ ] `private_key_jwt`, `client_secret_jwt`, `tls_client_auth`, `self_signed_tls_client_auth` のいずれか

- [ ] **Nonce / State**:
  - [ ] `openid` スコープあり → `nonce` 必須
  - [ ] `openid` スコープなし → `state` 必須

### FAPI Advance

- [ ] **Baseline要件**: 上記すべて満たす

- [ ] **Request Object**:
  - [ ] JWS署名付きRequest Object必須
  - [ ] `exp` クレーム必須
  - [ ] `nbf` クレーム必須
  - [ ] `exp - nbf <= 60分`
  - [ ] `aud` クレーム必須（Issuer URL含む）
  - [ ] `nbf` が60分以上過去でない

- [ ] **Response Type / Mode**:
  - [ ] `response_type=code id_token` または
  - [ ] `response_type=code&response_mode=jwt`

- [ ] **Sender-constrained Access Tokens**:
  - [ ] サーバー設定で `tls_client_certificate_bound_access_tokens=true`
  - [ ] クライアント設定で `tls_client_certificate_bound_access_tokens=true`
  - [ ] アクセストークンに `cnf.x5t#S256` 含む

- [ ] **クライアント認証**:
  - [ ] `client_secret_jwt` も禁止
  - [ ] `private_key_jwt`, `tls_client_auth`, `self_signed_tls_client_auth` のいずれか

- [ ] **Public Client**:
  - [ ] 禁止

---

## 🚨 よくある間違い

### 1. PKCE の code_challenge_method

```java
// ❌ 誤り: plain を許可してしまう
if (authorizationRequest.hasCodeChallenge()) {
  // OK
}

// ✅ 正しい: S256 必須
if (authorizationRequest.hasCodeChallenge()
    && authorizationRequest.codeChallengeMethod().isS256()) {
  // OK
} else {
  throw new OAuthRedirectableBadRequestException("code_challenge_method must be S256");
}
```

### 2. Request Object の有効期限

```java
// ❌ 誤り: expのみチェック
if (claims.hasExp()) {
  // OK
}

// ✅ 正しい: exp と nbf の両方をチェック + 差分60分以内
if (claims.hasExp() && claims.hasNbf()) {
  Date exp = claims.getExp();
  Date nbf = claims.getNbf();
  if (exp.getTime() - nbf.getTime() <= 3600000) {  // 60分
    // OK
  }
}
```

### 3. クライアント認証方式の混同

```java
// ❌ 誤り: FAPI Advance で client_secret_jwt を許可
// Baseline では OK だが Advance では NG

// ✅ 正しい: Advance では禁止
if (profile == AuthorizationProfile.FAPI_ADVANCE) {
  if (clientAuthenticationType.isClientSecretJwt()) {
    throw new OAuthRedirectableBadRequestException("client_secret_jwt not allowed in FAPI Advance");
  }
}
```

### 4. Response Mode の指定忘れ

```java
// ❌ 誤り: response_type=code のみ指定
// FAPI Advance では response_mode=jwt も必要

// ✅ 正しい: response_type=code&response_mode=jwt
if (context.responseType().isCode() && context.responseMode().isJwt()) {
  // OK
}
```

---

## 🔗 関連ドキュメント

**概念・基礎**:
- [concept-22: FAPI](../../content_03_concepts/concept-11-fapi.md) - FAPI概念説明
- [basic-08: 認可コードフロー](../../content_03_concepts/basic/basic-08-oauth2-authorization-code-flow.md) - 基本フロー

**実装詳細**:
- [impl-12: Plugin実装ガイド](./impl-12-plugin-implementation.md) - プラグインシステム
- [03-application-plane/02-authorization-flow.md](../03-application-plane/02-authorization-flow.md) - 認可フロー
- [03-application-plane/10-client-authentication.md](../03-application-plane/10-client-authentication.md) - クライアント認証

**参考実装クラス**:
- [FapiBaselineVerifier.java](../../../libs/idp-server-core-extension-fapi/src/main/java/org/idp/server/core/openid/extension/fapi/FapiBaselineVerifier.java)
- [FapiAdvanceVerifier.java](../../../libs/idp-server-core-extension-fapi/src/main/java/org/idp/server/core/openid/extension/fapi/FapiAdvanceVerifier.java)
- [TlsClientAuthAuthenticator.java](../../../libs/idp-server-core-extension-fapi/src/main/java/org/idp/server/core/openid/extension/fapi/TlsClientAuthAuthenticator.java)

**RFC/仕様**:
- [Financial-grade API Security Profile 1.0 - Part 1: Baseline](https://openid.net/specs/openid-financial-api-part-1-1_0.html)
- [Financial-grade API Security Profile 1.0 - Part 2: Advanced](https://openid.net/specs/openid-financial-api-part-2-1_0.html)
- [RFC 7636 - Proof Key for Code Exchange (PKCE)](https://datatracker.ietf.org/doc/html/rfc7636)
- [RFC 8705 - OAuth 2.0 Mutual-TLS Client Authentication](https://datatracker.ietf.org/doc/html/rfc8705)

---

**最終更新**: 2025-12-07
**難易度**: ⭐⭐⭐⭐ (上級)
