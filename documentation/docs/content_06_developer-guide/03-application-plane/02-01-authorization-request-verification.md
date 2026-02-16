# 認可リクエスト検証フロー詳細

認可エンドポイントにおけるリクエスト検証の全体像を記述する。

## 全体フロー概要

```
HTTP Request
    ↓
[1] OAuthRequestHandler.handleRequest()
    ↓
[2] OAuthRequestParameters.analyzePattern()
    → NORMAL | REQUEST_OBJECT | REQUEST_URI | PUSHED_REQUEST_URI
    ↓
[3] ContextCreator.create()
    ├─ JOSE解析 (Request Object/URIパターンのみ)
    ├─ filterScopes() → スコープ抽出
    └─ analyze() → プロファイル決定
    ↓
[4] OAuthRequestVerifier.verify()
    ├─ [4a] baseVerifier.verify() ← プロファイル別
    └─ [4b] extensionVerifiers.forEach(verify()) ← 共通拡張
    ↓
[5] OAuthRequestErrorHandler (エラー時)
    ├─ OAuthBadRequestException → エラーページ表示
    └─ OAuthRedirectableBadRequestException → redirect_uriへリダイレクト
```

## 1. リクエストパターン決定

**ファイル**: `libs/idp-server-core/.../oauth/request/OAuthRequestParameters.java`

リクエストパラメータから以下の優先順位でパターンを決定:

| 優先度 | 条件 | パターン |
|--------|------|----------|
| 1 | `request`パラメータあり | `REQUEST_OBJECT` |
| 2 | `request_uri`がPAR形式 | `PUSHED_REQUEST_URI` |
| 3 | `request_uri`あり | `REQUEST_URI` |
| 4 | 上記以外 | `NORMAL` |

## 2. コンテキスト作成

**ファイル**: `libs/idp-server-core/.../oauth/context/OAuthRequestContextCreators.java`

パターンごとに異なるCreatorが使用される:

| パターン | Creator | JOSE処理 |
|----------|---------|----------|
| `NORMAL` | `NormalPatternContextCreator` | なし（空JoseContext） |
| `REQUEST_OBJECT` | `RequestObjectPatternContextCreator` | JWT解析・署名検証 |
| `REQUEST_URI` | `RequestUriPatternContextCreator` | URIからJWT取得・解析・署名検証 |
| `PUSHED_REQUEST_URI` | `PushedRequestUriPatternContextCreator` | リポジトリから取得（JOSE不要） |

### 2.1 Request Objectパターンの処理フロー

**ファイル**: `libs/idp-server-core/.../oauth/context/RequestObjectPatternContextCreator.java`

```
JoseHandler.handle(request_jwt, clientJwks, serverJwks, secret)
    ↓
JoseType.parse(jwt_header)
    ├─ alg: "none"  → JoseType.plain  → JwtContextCreator  → 空JsonWebSignature
    ├─ alg: JWS系   → JoseType.signature → JwsContextCreator → 署名検証可能
    └─ alg: JWE系   → JoseType.encryption → JweContextCreator → 復号処理
    ↓
joseContext.verifySignature()
    ├─ hasJsonWebSignature() == true → 署名検証実行
    └─ hasJsonWebSignature() == false → no-op (alg:none)
    ↓
RequestObjectValidator.validate(claims)
    ↓
filterScopes() → analyze() → AuthorizationProfile決定
```

**重要**: `alg: none`の場合、`verifySignature()`はno-opとなり、`isUnsignedRequestObject()`がtrueを返す。FAPI Advancedでは`FapiAdvanceVerifier`がこれを検出して拒否する。

### 2.2 スコープフィルタリング

**ファイル**: `libs/idp-server-core/.../oauth/context/OAuthRequestContextCreator.java` `filterScopes()`

```java
String targetScope;
if (pattern.isRequestParameter() || clientConfiguration.isSupportedJar()) {
    targetScope = joseScope;   // Request Object内のscope
} else {
    targetScope = queryScope;  // クエリパラメータのscope
}
return clientConfiguration.filteredScope(targetScope);
```

| パターン | スコープ取得元 |
|----------|---------------|
| `REQUEST_OBJECT` / `REQUEST_URI` | JWT claims内の`scope` |
| `NORMAL` | クエリパラメータの`scope` |
| `PUSHED_REQUEST_URI` | PAR時に保存済みのスコープ |

**注意**: Request Objectパターンで`scope`がJWT内に含まれていない場合、`joseScope`は空文字列/nullとなり、フィルタ結果も空になる。

### 2.3 プロファイル決定

**ファイル**: `libs/idp-server-core/.../oauth/context/OAuthRequestContextCreator.java` `analyze()`

フィルタ済みスコープから以下の優先順位でプロファイルを決定:

| 優先度 | 条件 | プロファイル |
|--------|------|-------------|
| 1 | テナント設定の`fapi_advance_scopes`に該当 | `FAPI_ADVANCE` |
| 2 | テナント設定の`fapi_baseline_scopes`に該当 | `FAPI_BASELINE` |
| 3 | `openid`スコープを含む | `OIDC` |
| 4 | 上記以外 | `OAUTH2` |

**重要**: スコープが空の場合、プロファイルは`OAUTH2`に解決される。これにより、Request Object内にscopeがない場合にFAPI検証が実行されない問題が発生する（GAP-006参照）。

## 3. 検証チェーン

**ファイル**: `libs/idp-server-core/.../oauth/verifier/OAuthRequestVerifier.java`

```java
public void verify(OAuthRequestContext context) {
    // [Step 1] プロファイル別base verifier
    AuthorizationRequestVerifier baseVerifier = baseVerifiers.get(context.profile());
    baseVerifier.verify(context);

    // [Step 2] 拡張verifier（条件付き）
    extensionVerifiers.forEach(verifier -> {
        if (verifier.shouldVerify(context)) {
            verifier.verify(context);
        }
    });
}
```

### 3.1 Base Verifier一覧

| プロファイル | Base Verifier | 内部呼び出し |
|-------------|---------------|-------------|
| `OAUTH2` | `OAuth2RequestVerifier` | `OAuthRequestBaseVerifier` |
| `OIDC` | `OidcRequestVerifier` | `OidcRequestBaseVerifier` → `OAuthRequestBaseVerifier` |
| `FAPI_BASELINE` | `FapiBaselineVerifier` (plugin) | `OidcRequestBaseVerifier` or `OAuthRequestBaseVerifier` |
| `FAPI_ADVANCE` | `FapiAdvanceVerifier` (plugin) | `OidcRequestBaseVerifier` or `OAuthRequestBaseVerifier` |

### 3.2 Extension Verifier一覧

| Extension Verifier | shouldVerify()条件 |
|-------|-----------|
| `RequestObjectVerifier` | `isRequestParameterPattern() && !isUnsignedRequestObject()` |
| `OAuthAuthorizationDetailsVerifier` | `hasAuthorizationDetails()` |
| `JarmVerifier` | `responseMode().isJwtMode()` |

**注意**: `RequestObjectVerifier`は`isUnsignedRequestObject()`がtrueの場合（`alg: none`）スキップされる。

## 4. Base Verifier詳細

### 4.1 OAuthRequestBaseVerifier（OAuth 2.0基本検証）

**ファイル**: `libs/idp-server-core/.../oauth/verifier/base/OAuthRequestBaseVerifier.java`

| 検証 | エラーコード | 例外型 |
|------|-------------|--------|
| response_type必須 | `invalid_request` | Redirectable |
| response_type有効値 | `invalid_request` | Redirectable |
| サーバー対応response_type | `unsupported_response_type` | Redirectable |
| クライアント対応response_type | `unauthorized_client` | Redirectable |
| スコープ存在 | `invalid_scope` | Redirectable |

### 4.2 OidcRequestBaseVerifier（OIDC検証）

**ファイル**: `libs/idp-server-core/.../oauth/verifier/base/OidcRequestBaseVerifier.java`

`OAuthRequestBaseVerifier`の検証を含み、追加で以下を検証:

| 検証 | エラーコード | 例外型 |
|------|-------------|--------|
| redirect_uri必須 | `invalid_request` | Non-redirectable |
| redirect_uri絶対URI | `invalid_request` | Non-redirectable |
| redirect_uri登録済み | `invalid_request` | Non-redirectable |
| Implicit Flowでhttp禁止 | `invalid_request` | Redirectable |
| Implicit/Hybrid Flowでnonce必須 | `invalid_request` | Redirectable |
| **OAuthRequestBaseVerifier.verify()** | (上記参照) | |
| display有効値 | `invalid_request` | Redirectable |
| prompt有効値 | `invalid_request` | Redirectable |
| prompt=none単独 | `invalid_request` | Redirectable |
| max_age有効値 | `invalid_request` | Redirectable |

### 4.3 FapiBaselineVerifier

**ファイル**: `libs/idp-server-core-extension-fapi/.../fapi/FapiBaselineVerifier.java`

| 検証 | エラーコード | 例外型 | 仕様参照 |
|------|-------------|--------|---------|
| redirect_uri登録済み | `invalid_request` | Non-redirectable | 5.2.2-8 |
| redirect_uri必須 | `invalid_request` | Non-redirectable | 5.2.2-9 |
| redirect_uri完全一致 | `invalid_request` | Non-redirectable | 5.2.2-10 |
| redirect_uri https必須 | `invalid_request` | Non-redirectable | 5.2.2-20 |
| **OidcRequestBaseVerifier.verify()** or **OAuthRequestBaseVerifier.verify()** | | |
| client_secret_post/basic禁止 | `unauthorized_client` | Redirectable | 5.2.2-4 |
| PKCE S256必須 | `invalid_request` | Redirectable | 5.2.2-7 |
| openidスコープ時nonce必須 | `invalid_request` | Redirectable | 5.2.2.2-1 |
| 非openidスコープ時state必須 | `invalid_request` | Redirectable | 5.2.2.3-1 |

### 4.4 FapiAdvanceVerifier

**ファイル**: `libs/idp-server-core-extension-fapi/.../fapi/FapiAdvanceVerifier.java`

| 検証 | エラーコード | 例外型 | 仕様参照 |
|------|-------------|--------|---------|
| JARM設定確認（JWT mode時） | `unauthorized_client` | Non-redirectable | - |
| **OidcRequestBaseVerifier.verify()** or **OAuthRequestBaseVerifier.verify()** | | |
| Request Objectパターン必須 | `invalid_request` | Redirectable | 5.2.2-1 |
| alg:none拒否 | `invalid_request_object` | Redirectable | 5.2.2-1 |
| response_type: code id_token or code+jwt | `invalid_request` | Redirectable | 5.2.2-2 |
| sender-constrained access token必須 | `invalid_request` | Redirectable | 5.2.2-5 |
| exp-nbf ≤ 60分 | `invalid_request_object` | Redirectable | 5.2.2-13 |
| aud含むissuer | `invalid_request_object` | Redirectable | 5.2.2-15 |
| client_secret_*系禁止 | `unauthorized_client` | Redirectable | 5.2.2-14 |
| publicクライアント禁止 | `unauthorized_client` | Redirectable | 5.2.2-16 |
| nbf 60分以内 | `invalid_request_object` | Redirectable | 5.2.2-17 |

## 5. Extension Verifier詳細

### 5.1 RequestObjectVerifier → RequestObjectVerifyable

**ファイル**: `libs/idp-server-core/.../oauth/verifier/extension/RequestObjectVerifier.java`
**ファイル**: `libs/idp-server-core/.../oauth/verifier/extension/RequestObjectVerifyable.java`

**実行条件**: `isRequestParameterPattern() && !isUnsignedRequestObject()`

| 検証 | エラーコード | 仕様参照 |
|------|-------------|---------|
| 対称鍵(HS*)拒否 | `invalid_request_object` | - |
| iss = client_id | `invalid_request_object` | - |
| aud含むissuer | `invalid_request_object` | - |
| jti（任意、検証なし） | - | FAPI 1.0では任意 |
| exp存在・有効期限内 | `invalid_request_object` | - |
| scope存在（require_signed_request_object時） | `invalid_request_object` | RFC 9101 §6.3, FAPI 5.2.2-13 |

### 5.2 JarmVerifier

**ファイル**: `libs/idp-server-core/.../oauth/verifier/extension/JarmVerifier.java`

**実行条件**: `responseMode().isJwtMode()`

| 検証 | エラーコード |
|------|-------------|
| form_post.jwt非対応 | `unauthorized_client` |

## 6. エラーハンドリング

**ファイル**: `libs/idp-server-core/.../oauth/handler/OAuthRequestErrorHandler.java`

### 6.1 例外型とルーティング

| 例外型 | レスポンス | 動作 |
|--------|-----------|------|
| `OAuthBadRequestException` | `BAD_REQUEST` | エラーページ表示（リダイレクト不可） |
| `OAuthRedirectableBadRequestException` | `REDIRECTABLE_BAD_REQUEST` | redirect_uriへエラーリダイレクト |
| `ClientUnAuthorizedException` | `BAD_REQUEST` | `invalid_client` |
| `ClientConfigurationNotFoundException` | `BAD_REQUEST` | `invalid_request` |
| `ServerConfigurationNotFoundException` | `BAD_REQUEST` | `invalid_request` |
| その他 | `SERVER_ERROR` | `server_error` |

### 6.2 エラーレスポンス生成

**ファイル**: `libs/idp-server-core/.../oauth/response/AuthorizationErrorResponseCreator.java`

`OAuthRedirectableBadRequestException`の場合、`AuthorizationErrorResponseCreator`がリダイレクトURLを構築:

```
redirect_uri?error=xxx&error_description=xxx&state=xxx
```

**JARMラップ条件**: `context.responseMode().isJwtMode()`のみ。プロファイルベースの自動JARM判定(`context.isJwtMode()`)はエラーレスポンスには使用しない。リクエスト自体が不正（例: response_type=code without response_mode=jwt）な場合、エラーはplain query parameterで返す必要があるため。

## 7. 既知のギャップと注意点

### 7.1 スコープ欠落時のプロファイル解決問題

Request Object内にscopeが含まれない場合:
1. `filterScopes()`で`joseScope`が空 → フィルタ結果も空
2. `analyze()`でプロファイルが`OAUTH2`に解決
3. `FapiAdvanceVerifier`が実行されない
4. `OAuthRequestBaseVerifier.throwExceptionIfNotContainsValidScope()`が`invalid_scope`をスロー
5. `RequestObjectVerifyable.throwExceptionIfMissingScopeWhenRequired()`に到達しない

**対策**: `RequestObjectPatternContextCreator`でプロファイル解決前にscope存在チェックを行う必要がある（GAP-006参照）。

### 7.2 alg:none Request Objectの処理

`alg: none`のRequest Objectは:
1. `JoseType.plain`として処理 → 空`JsonWebSignature`
2. `isUnsignedRequestObject()` = true
3. `RequestObjectVerifier`がスキップ（shouldVerify = false）
4. `FapiAdvanceVerifier`の`throwExceptionIfNotRRequestParameterPattern()`で検出・拒否

### 7.3 実行順序の重要性

```
[先] base verifier (profile-specific)
     ├─ OAuthRequestBaseVerifier.throwExceptionIfNotContainsValidScope() → invalid_scope
     ├─ FapiAdvanceVerifier.throwExceptionIfNotRRequestParameterPattern() → invalid_request_object
     └─ ...
[後] extension verifier
     └─ RequestObjectVerifier → RequestObjectVerifyable.verify() → invalid_request_object
```

base verifierが先に実行されるため、base verifierでのエラーがextension verifierのエラーを覆い隠す場合がある。

## 8. ファイル一覧

| コンポーネント | ファイルパス |
|---------------|-------------|
| パターン決定 | `libs/idp-server-core/.../oauth/request/OAuthRequestParameters.java` |
| Creator管理 | `libs/idp-server-core/.../oauth/context/OAuthRequestContextCreators.java` |
| Normalパターン | `libs/idp-server-core/.../oauth/context/NormalPatternContextCreator.java` |
| Request Objectパターン | `libs/idp-server-core/.../oauth/context/RequestObjectPatternContextCreator.java` |
| Request URIパターン | `libs/idp-server-core/.../oauth/context/RequestUriPatternContextCreator.java` |
| Pushed URIパターン | `libs/idp-server-core/.../oauth/context/PushedRequestUriPatternContextCreator.java` |
| プロファイル解析 | `libs/idp-server-core/.../oauth/context/OAuthRequestContextCreator.java` |
| 検証チェーン | `libs/idp-server-core/.../oauth/verifier/OAuthRequestVerifier.java` |
| OAuth2 base検証 | `libs/idp-server-core/.../oauth/verifier/base/OAuthRequestBaseVerifier.java` |
| OIDC base検証 | `libs/idp-server-core/.../oauth/verifier/base/OidcRequestBaseVerifier.java` |
| OAuth2 verifier | `libs/idp-server-core/.../oauth/verifier/OAuth2RequestVerifier.java` |
| OIDC verifier | `libs/idp-server-core/.../oauth/verifier/OidcRequestVerifier.java` |
| FAPI Baseline | `libs/idp-server-core-extension-fapi/.../fapi/FapiBaselineVerifier.java` |
| FAPI Advanced | `libs/idp-server-core-extension-fapi/.../fapi/FapiAdvanceVerifier.java` |
| Request Object検証 | `libs/idp-server-core/.../oauth/verifier/extension/RequestObjectVerifier.java` |
| Request Object検証IF | `libs/idp-server-core/.../oauth/verifier/extension/RequestObjectVerifyable.java` |
| JARM検証 | `libs/idp-server-core/.../oauth/verifier/extension/JarmVerifier.java` |
| AuthDetails検証 | `libs/idp-server-core/.../oauth/verifier/extension/OAuthAuthorizationDetailsVerifier.java` |
| JOSE処理 | `libs/idp-server-platform/.../jose/JoseHandler.java` |
| JOSE型判定 | `libs/idp-server-platform/.../jose/JoseType.java` |
| 未署名JWT | `libs/idp-server-platform/.../jose/JwtContextCreator.java` |
| 署名JWT | `libs/idp-server-platform/.../jose/JwsContextCreator.java` |
| エラー処理 | `libs/idp-server-core/.../oauth/handler/OAuthRequestErrorHandler.java` |
| エラーレスポンス生成 | `libs/idp-server-core/.../oauth/response/AuthorizationErrorResponseCreator.java` |
| Deny エラーレスポンス | `libs/idp-server-core/.../oauth/response/AuthorizationDenyErrorResponseCreator.java` |
| ResponseMode判定 | `libs/idp-server-core/.../oauth/response/ResponseModeDecidable.java` |
