# PKCE

## 📍 このドキュメントの位置づけ

**対象読者**: PKCEの実装詳細を理解したい開発者

**このドキュメントで学べること**:
- PKCE (Proof Key for Code Exchange) の仕組み
- Code Verifier / Code Challenge の生成・検証
- plain / S256 メソッドの違い
- 認可コードフローでのPKCE検証実装
- モバイルアプリ・SPAでのPKCE適用パターン

**前提知識**:
- [basic-08: 認可コードフロー](../../content_11_learning/02-oauth-fundamentals/oauth2-authorization-code-flow.md)の理解
- OAuth 2.0 の基礎知識

---

## 🏗️ PKCEとは

**PKCE (Proof Key for Code Exchange)** は、認可コードフローにおける認可コード盗難攻撃を防ぐセキュリティ拡張です。

### なぜPKCEが必要か

**通常の認可コードフローの問題点**:
```
1. 攻撃者がリダイレクトURI を傍受
   → 認可コード (code=xxx) を盗む
2. 攻撃者がトークンエンドポイントに認可コードを送信
   → アクセストークンを取得（Publicクライアントの場合）
```

**PKCEによる防御**:
```
1. クライアントが code_verifier を生成（ランダム文字列）
2. code_verifier から code_challenge を計算（SHA-256ハッシュ）
3. 認可リクエストに code_challenge を含める
4. 認可コードを取得
5. トークンリクエストに code_verifier を含める
6. サーバーが code_verifier を検証
   → SHA-256(code_verifier) == code_challenge ?
7. 一致した場合のみトークン発行
```

**攻撃者は認可コードを盗んでも、`code_verifier`がないためトークンを取得できません。**

---

## 📋 PKCE フロー

### 1. Code Verifier 生成

クライアントが**ランダムな文字列**を生成します。

```
code_verifier = BASE64URL(RANDOM(32オクテット))
              = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
```

**要件**:
- **長さ**: 43〜128文字
- **文字種**: `[A-Za-z0-9-._~]` のみ
- **ランダム性**: 暗号学的に安全な乱数生成器を使用

**参考実装**:
```java
public class CodeVerifier {
  String value;

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }

  public boolean isShorterThan43() {
    return value.length() < 43;
  }

  public boolean isLongerThan128() {
    return value.length() > 128;
  }
}
```

**参考実装**: [CodeVerifier.java:21](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/type/pkce/CodeVerifier.java#L21)

### 2. Code Challenge 生成

`code_verifier` から `code_challenge` を計算します。

#### S256 メソッド（推奨）

```
code_challenge = BASE64URL(SHA256(code_verifier))
```

**実装**:
```java
public class CodeChallengeCalculator implements MessageDigestable, Base64Codeable {

  CodeVerifier codeVerifier;

  public CodeChallenge calculateWithS256() {
    // 1. SHA-256 ハッシュ計算
    byte[] bytes = digestWithSha256(codeVerifier.value());

    // 2. Base64URL エンコード
    String encodedValue = encodeWithUrlSafe(bytes);

    return new CodeChallenge(encodedValue);
  }
}
```

**参考実装**: [CodeChallengeCalculator.java:24](../../../libs/idp-server-core-extension-pkce/src/main/java/org/idp/server/core/openid/extension/pkce/CodeChallengeCalculator.java#L24)

#### plain メソッド（非推奨）

```
code_challenge = code_verifier
```

**実装**:
```java
public CodeChallenge calculateWithPlain() {
  return new CodeChallenge(codeVerifier.value());
}
```

**⚠️ 注意**: `plain` メソッドはセキュリティが低いため、**S256の使用を強く推奨**します。FAPI Baselineでは**S256が必須**です。

### 3. 認可リクエスト

クライアントが `code_challenge` と `code_challenge_method` を認可リクエストに含めます。

```http
GET /authorize?
  response_type=code
  &client_id=s6BhdRkqt3
  &redirect_uri=https://client.example.com/cb
  &scope=openid profile
  &state=xyz
  &code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM
  &code_challenge_method=S256
```

### 4. 認可コード発行

認可サーバーは、`code_challenge` と `code_challenge_method` を認可コードと紐付けて保存します。

```java
// AuthorizationGrant に保存
AuthorizationCodeGrant grant = AuthorizationCodeGrant.builder()
    .code(authorizationCode)
    .codeChallenge(codeChallenge)
    .codeChallengeMethod(codeChallengeMethod)
    .build();
```

### 5. トークンリクエスト

クライアントが `code_verifier` をトークンリクエストに含めます。

```http
POST /token
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code
&code=SplxlOBeZQQYbYS6WxSbIA
&redirect_uri=https://client.example.com/cb
&client_id=s6BhdRkqt3
&code_verifier=dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk
```

### 6. Code Verifier 検証

認可サーバーが `code_verifier` を検証します。

```java
public class AuthorizationCodeGrantPkceVerifier
    implements AuthorizationCodeGrantExtensionVerifierInterface {

  @Override
  public void verify(
      TokenRequestContext tokenRequestContext,
      AuthorizationRequest authorizationRequest,
      AuthorizationCodeGrant authorizationCodeGrant,
      ClientCredentials clientCredentials) {

    // 1. code_verifier が含まれているか確認
    throwExceptionIfNotContainsCodeVerifier(tokenRequestContext);

    // 2. code_verifier が code_challenge と一致するか確認
    throwExceptionIfUnMatchCodeVerifier(tokenRequestContext, authorizationRequest);

    // 3. code_verifier のフォーマット検証
    throwExceptionIfInvalidCodeVerifierFormat(tokenRequestContext);
  }
}
```

**参考実装**: [AuthorizationCodeGrantPkceVerifier.java:29](../../../libs/idp-server-core-extension-pkce/src/main/java/org/idp/server/core/openid/extension/pkce/AuthorizationCodeGrantPkceVerifier.java#L29)

---

## 🔐 検証ロジック詳細

### 1. code_verifier 存在確認

```java
void throwExceptionIfNotContainsCodeVerifier(TokenRequestContext tokenRequestContext) {
  if (!tokenRequestContext.hasCodeVerifier()) {
    throw new TokenBadRequestException(
        "authorization request has code_challenge, but token request does not contain code_verifier");
  }
}
```

**エラー条件**:
- 認可リクエストに `code_challenge` があるのに、トークンリクエストに `code_verifier` がない

### 2. code_verifier 一致確認

```java
void throwExceptionIfUnMatchCodeVerifier(
    TokenRequestContext tokenRequestContext,
    AuthorizationRequest authorizationRequest) {

  // S256 メソッドの場合
  if (authorizationRequest.isPkceWithS256()) {
    CodeVerifier codeVerifier = tokenRequestContext.codeVerifier();
    CodeChallengeCalculator calculator = new CodeChallengeCalculator(codeVerifier);
    CodeChallenge calculatedChallenge = calculator.calculateWithS256();

    if (!calculatedChallenge.equals(authorizationRequest.codeChallenge())) {
      throw new TokenBadRequestException(
          "code_verifier of token request does not match code_challenge of authorization request");
    }
    return;
  }

  // plain メソッドの場合
  CodeChallengeCalculator calculator =
      new CodeChallengeCalculator(tokenRequestContext.codeVerifier());
  CodeChallenge calculatedChallenge = calculator.calculateWithPlain();

  if (!calculatedChallenge.equals(authorizationRequest.codeChallenge())) {
    throw new TokenBadRequestException(
        "code_verifier of token request does not match code_challenge of authorization request");
  }
}
```

**検証フロー**:
```
1. トークンリクエストから code_verifier 取得
2. code_challenge_method に応じて code_challenge を計算
   - S256: BASE64URL(SHA256(code_verifier))
   - plain: code_verifier
3. 計算した code_challenge と保存された code_challenge を比較
4. 一致しない場合はエラー
```

### 3. code_verifier フォーマット検証

```java
void throwExceptionIfInvalidCodeVerifierFormat(TokenRequestContext tokenRequestContext) {
  CodeVerifier codeVerifier = tokenRequestContext.codeVerifier();

  // 長さチェック: 最低43文字
  if (codeVerifier.isShorterThan43()) {
    throw new TokenBadRequestException("code_verifier must be at least 43 characters");
  }

  // 長さチェック: 最大128文字
  if (codeVerifier.isLongerThan128()) {
    throw new TokenBadRequestException("code_verifier must be at most 128 characters");
  }

  // 文字種チェック: [A-Za-z0-9-._~] のみ
  if (!codeVerifier.value().matches("^[A-Za-z0-9\\-._~]+$")) {
    throw new TokenBadRequestException("code_verifier contains invalid characters");
  }
}
```

**RFC 7636 要件**:
- **最小長**: 43文字
- **最大長**: 128文字
- **文字種**: `[A-Za-z0-9-._~]`（unreserved characters）

---

## 🔒 クライアント単位でのPKCE必須化（require_pkce）

PKCE はデフォルトでは任意です（送れば検証し、送らなければ素通し）。public client（SPA / native）では PKCE を**必須**にしたいケースが多いため、クライアント設定 `require_pkce: true` でサーバー側強制を有効化できます（デフォルト `false`＝後方互換）。

```json
{
  "client_id": "spa-client",
  "token_endpoint_auth_method": "none",
  "require_pkce": true
}
```

`require_pkce: true` の場合の挙動:

| エンドポイント | 条件 | エラー |
|---|---|---|
| `/v1/authorizations` | `code_challenge` 欠落 | `invalid_request`（"PKCE is required for this client"）|
| `/v1/authorizations` | `code_challenge_method` が S256 以外 | `invalid_request` |
| `/v1/tokens` | `code_verifier` 欠落 | `invalid_grant` |

- `false`（デフォルト）では従来どおり PKCE は任意。既存クライアントへの影響はなし。
- 認可エンドポイントのエラーは、登録済み `redirect_uri` が有効な場合 OAuth 2.0 §4.1.2.1 に従いリダイレクトで返却される。
- FAPI プロファイルはプロファイル側で PKCE(S256) を必須化するため、`require_pkce` の有無に関わらず必須。

---

## 📱 実装パターン

### パターン1: モバイルアプリ（Native App）

**特徴**:
- Publicクライアント（client_secretなし）
- PKCEが**必須**

**実装例（iOS/Swift）**:
```swift
// 1. Code Verifier 生成
func generateCodeVerifier() -> String {
    var buffer = [UInt8](repeating: 0, count: 32)
    _ = SecRandomCopyBytes(kSecRandomDefault, buffer.count, &buffer)
    return Data(buffer).base64EncodedString()
        .replacingOccurrences(of: "+", with: "-")
        .replacingOccurrences(of: "/", with: "_")
        .replacingOccurrences(of: "=", with: "")
        .trimmingCharacters(in: .whitespaces)
}

// 2. Code Challenge 生成（S256）
func generateCodeChallenge(verifier: String) -> String {
    guard let data = verifier.data(using: .utf8) else { return "" }
    var buffer = [UInt8](repeating: 0, count: Int(CC_SHA256_DIGEST_LENGTH))
    data.withUnsafeBytes {
        _ = CC_SHA256($0.baseAddress, CC_LONG(data.count), &buffer)
    }
    return Data(buffer).base64EncodedString()
        .replacingOccurrences(of: "+", with: "-")
        .replacingOccurrences(of: "/", with: "_")
        .replacingOccurrences(of: "=", with: "")
}

// 3. 認可リクエスト
let codeVerifier = generateCodeVerifier()
let codeChallenge = generateCodeChallenge(verifier: codeVerifier)

let authURL = "https://idp.example.com/authorize?" +
    "response_type=code" +
    "&client_id=mobile-app" +
    "&redirect_uri=myapp://callback" +
    "&scope=openid%20profile" +
    "&code_challenge=\(codeChallenge)" +
    "&code_challenge_method=S256"

// 4. トークンリクエスト（認可コード取得後）
let tokenParams = [
    "grant_type": "authorization_code",
    "code": authorizationCode,
    "redirect_uri": "myapp://callback",
    "client_id": "mobile-app",
    "code_verifier": codeVerifier
]
```

### パターン2: SPA（Single Page Application）

**特徴**:
- Publicクライアント
- PKCEが**必須**
- Authorization Code Flow with PKCE

**実装例（JavaScript）**:
```javascript
// 1. Code Verifier 生成
function generateCodeVerifier() {
    const array = new Uint8Array(32);
    crypto.getRandomValues(array);
    return base64UrlEncode(array);
}

// 2. Code Challenge 生成（S256）
async function generateCodeChallenge(verifier) {
    const encoder = new TextEncoder();
    const data = encoder.encode(verifier);
    const hash = await crypto.subtle.digest('SHA-256', data);
    return base64UrlEncode(new Uint8Array(hash));
}

// Base64URL エンコード
function base64UrlEncode(buffer) {
    const base64 = btoa(String.fromCharCode(...buffer));
    return base64
        .replace(/\+/g, '-')
        .replace(/\//g, '_')
        .replace(/=/g, '');
}

// 3. 認可リクエスト
const codeVerifier = generateCodeVerifier();
sessionStorage.setItem('code_verifier', codeVerifier);

const codeChallenge = await generateCodeChallenge(codeVerifier);

const authUrl = `https://idp.example.com/authorize?` +
    `response_type=code` +
    `&client_id=spa-client` +
    `&redirect_uri=${encodeURIComponent('https://app.example.com/callback')}` +
    `&scope=openid%20profile` +
    `&code_challenge=${codeChallenge}` +
    `&code_challenge_method=S256`;

window.location.href = authUrl;

// 4. トークンリクエスト（コールバックページで）
const codeVerifier = sessionStorage.getItem('code_verifier');
const params = new URLSearchParams(window.location.search);
const code = params.get('code');

const tokenResponse = await fetch('https://idp.example.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
        grant_type: 'authorization_code',
        code: code,
        redirect_uri: 'https://app.example.com/callback',
        client_id: 'spa-client',
        code_verifier: codeVerifier
    })
});
```

### パターン3: Confidential Client（オプション）

**特徴**:
- client_secretを持つクライアント
- PKCEは**オプション**（推奨）
- 追加のセキュリティ層として使用

**サーバー設定**:
```json
{
  "client_id": "web-app",
  "client_secret": "secret123",
  "token_endpoint_auth_method": "client_secret_basic",
  "pkce_required": true
}
```

---

## 🧪 テスト実装例

### PKCE検証のテスト

```java
@Test
void testPkceVerification_S256_Success() {
  // 1. Code Verifier 生成
  String codeVerifierValue = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
  CodeVerifier codeVerifier = new CodeVerifier(codeVerifierValue);

  // 2. Code Challenge 計算
  CodeChallengeCalculator calculator = new CodeChallengeCalculator(codeVerifier);
  CodeChallenge codeChallenge = calculator.calculateWithS256();

  // 3. 認可リクエスト作成
  AuthorizationRequest authRequest = AuthorizationRequest.builder()
      .codeChallenge(codeChallenge)
      .codeChallengeMethod(CodeChallengeMethod.S256)
      .build();

  // 4. トークンリクエスト作成
  TokenRequestContext tokenRequest = TokenRequestContext.builder()
      .codeVerifier(codeVerifier)
      .build();

  // 5. PKCE検証
  AuthorizationCodeGrantPkceVerifier verifier = new AuthorizationCodeGrantPkceVerifier();

  // 6. 検証成功（例外が発生しない）
  assertDoesNotThrow(() ->
      verifier.verify(tokenRequest, authRequest, grant, credentials)
  );
}

@Test
void testPkceVerification_MismatchCodeVerifier_Failure() {
  // 異なる code_verifier でエラー
  CodeVerifier correctVerifier = new CodeVerifier("correct-verifier");
  CodeVerifier wrongVerifier = new CodeVerifier("wrong-verifier");

  CodeChallengeCalculator calculator = new CodeChallengeCalculator(correctVerifier);
  CodeChallenge codeChallenge = calculator.calculateWithS256();

  AuthorizationRequest authRequest = AuthorizationRequest.builder()
      .codeChallenge(codeChallenge)
      .codeChallengeMethod(CodeChallengeMethod.S256)
      .build();

  TokenRequestContext tokenRequest = TokenRequestContext.builder()
      .codeVerifier(wrongVerifier)  // 異なる verifier
      .build();

  // 検証失敗
  assertThrows(TokenBadRequestException.class, () ->
      verifier.verify(tokenRequest, authRequest, grant, credentials)
  );
}

@Test
void testCodeVerifierFormat_Invalid() {
  // 長さ不足
  CodeVerifier shortVerifier = new CodeVerifier("short");
  assertTrue(shortVerifier.isShorterThan43());

  // 長さ超過
  String longValue = "a".repeat(129);
  CodeVerifier longVerifier = new CodeVerifier(longValue);
  assertTrue(longVerifier.isLongerThan128());
}
```

---

## 📋 実装チェックリスト

PKCE対応を実装する際のチェックリスト:

### クライアント側

- [ ] **Code Verifier 生成**:
  - [ ] 暗号学的に安全な乱数生成器を使用
  - [ ] 43〜128文字の範囲
  - [ ] `[A-Za-z0-9-._~]` のみ使用

- [ ] **Code Challenge 生成**:
  - [ ] S256メソッドを使用（SHA-256ハッシュ + Base64URL）
  - [ ] plainメソッドは使用しない

- [ ] **認可リクエスト**:
  - [ ] `code_challenge` パラメータを含める
  - [ ] `code_challenge_method=S256` を指定

- [ ] **Code Verifier 保存**:
  - [ ] セッションストレージまたはメモリに保存
  - [ ] ローカルストレージは避ける（XSSリスク）

- [ ] **トークンリクエスト**:
  - [ ] `code_verifier` パラメータを含める
  - [ ] 保存した code_verifier を使用

### サーバー側

- [ ] **認可リクエスト検証**:
  - [ ] `code_challenge` と `code_challenge_method` の存在確認
  - [ ] `code_challenge_method` が `S256` または `plain`

- [ ] **Code Challenge 保存**:
  - [ ] 認可コードと紐付けて保存
  - [ ] `code_challenge_method` も保存

- [ ] **トークンリクエスト検証**:
  - [ ] `code_verifier` の存在確認
  - [ ] `code_verifier` のフォーマット検証（長さ、文字種）
  - [ ] `code_verifier` から `code_challenge` を計算
  - [ ] 保存された `code_challenge` と一致するか確認

---

## 🚨 よくある間違い

### 1. code_verifier の長さ不足

```javascript
// ❌ 誤り: 長さが43文字未満
const codeVerifier = "short-verifier";

// ✅ 正しい: 43文字以上
const codeVerifier = generateCodeVerifier();  // 43〜128文字
```

### 2. plain メソッドの使用

```http
❌ 誤り: plain メソッド（セキュリティが低い）
code_challenge_method=plain

✅ 正しい: S256 メソッド
code_challenge_method=S256
```

### 3. code_verifier の保存場所

```javascript
// ❌ 誤り: LocalStorage（XSSリスク）
localStorage.setItem('code_verifier', codeVerifier);

// ✅ 正しい: SessionStorage（ページ遷移で消える）
sessionStorage.setItem('code_verifier', codeVerifier);

// ✅ より良い: メモリ（変数）
let codeVerifier = generateCodeVerifier();
```

### 4. Base64URL エンコードミス

```javascript
// ❌ 誤り: 通常のBase64（+, /, = を含む）
const base64 = btoa(String.fromCharCode(...buffer));

// ✅ 正しい: Base64URL（-, _, パディングなし）
const base64url = btoa(String.fromCharCode(...buffer))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=/g, '');
```

---

## 🔗 関連ドキュメント

**概念・基礎**:
- [basic-08: 認可コードフロー](../../content_11_learning/02-oauth-fundamentals/oauth2-authorization-code-flow.md) - 基本フロー
- [concept-22: FAPI](../../content_03_concepts/03-authentication-authorization/concept-06-fapi.md) - FAPI BaselineでのPKCE必須化

**実装詳細**:
- [impl-22: FAPI実装ガイド](./impl-22-fapi-implementation.md) - FAPI BaselineのPKCE要件
- [03-application-plane/02-authorization-flow.md](../03-application-plane/02-authorization-flow.md) - 認可フロー

**参考実装クラス**:
- [CodeVerifier.java](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/type/pkce/CodeVerifier.java)
- [CodeChallenge.java](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/type/pkce/CodeChallenge.java)
- [CodeChallengeMethod.java](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/type/pkce/CodeChallengeMethod.java)
- [CodeChallengeCalculator.java](../../../libs/idp-server-core-extension-pkce/src/main/java/org/idp/server/core/openid/extension/pkce/CodeChallengeCalculator.java)
- [AuthorizationCodeGrantPkceVerifier.java](../../../libs/idp-server-core-extension-pkce/src/main/java/org/idp/server/core/openid/extension/pkce/AuthorizationCodeGrantPkceVerifier.java)

**RFC/仕様**:
- [RFC 7636 - Proof Key for Code Exchange (PKCE)](https://datatracker.ietf.org/doc/html/rfc7636)
- [OAuth 2.0 for Native Apps](https://datatracker.ietf.org/doc/html/rfc8252) - PKCEの必須化

---

**最終更新**: 2025-12-07
**難易度**: ⭐⭐ (初級〜中級)
