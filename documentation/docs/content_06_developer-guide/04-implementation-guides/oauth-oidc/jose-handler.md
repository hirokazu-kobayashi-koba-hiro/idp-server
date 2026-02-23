# JOSE (JWT/JWS/JWE)

## このドキュメントの目的

**JWT/JWS/JWE**の生成・検証処理を理解することが目標です。

### 所要時間
⏱️ **約30分**

### 前提知識
- OAuth 2.0/OIDC基礎知識
- JWT（JSON Web Token）基礎知識

---

## JOSEとは

**JOSE (JSON Object Signing and Encryption)**

OAuth/OIDCで使用される暗号化・署名技術の総称：

| 技術 | 正式名称 | 用途 | RFC |
|------|---------|------|-----|
| **JWT** | JSON Web Token | クレーム（主張）の表現 | [RFC 7519](https://datatracker.ietf.org/doc/html/rfc7519) |
| **JWS** | JSON Web Signature | JWTへの署名 | [RFC 7515](https://datatracker.ietf.org/doc/html/rfc7515) |
| **JWE** | JSON Web Encryption | JWTの暗号化 | [RFC 7516](https://datatracker.ietf.org/doc/html/rfc7516) |
| **JWK** | JSON Web Key | 鍵の表現 | [RFC 7517](https://datatracker.ietf.org/doc/html/rfc7517) |

**idp-serverでの使用例**:
- **Access Token**: JWT/JWS（署名付きトークン）
- **ID Token**: JWT/JWS（署名付きトークン）
- **Refresh Token**: JWT/JWS
- **Request Object**: JWT/JWS（クライアントが送信するリクエストパラメータ）

---

## 使用ライブラリ

**idp-serverは具体的なJOSE処理を外部ライブラリに委譲**:

| ライブラリ | バージョン | 用途 | 公式サイト |
|-----------|----------|------|----------|
| **Nimbus JOSE + JWT** | 9.x+ | JWT/JWS/JWE生成・検証・解析 | [connect2id.com/products/nimbus-jose-jwt](https://connect2id.com/products/nimbus-jose-jwt) |

**依存関係**:
```gradle
implementation 'com.nimbusds:nimbus-jose-jwt:9.x'
```

**Nimbus JOSE + JWTの役割**:
- ✅ JWT/JWS/JWE の解析（parse）
- ✅ 署名生成・検証（sign/verify）
- ✅ 暗号化・復号（encrypt/decrypt）
- ✅ JWKS解析
- ✅ 鍵生成（RSA/ECDSA等）

**idp-serverのJoseHandlerの役割**:
- ✅ Nimbus JOSE + JWTのラッパー
- ✅ 型判定（JWT/JWS/JWE）の自動化
- ✅ idp-server固有の例外処理（`JoseInvalidException`）
- ✅ 鍵選択ロジック（kid検索、アルゴリズム判定）

**なぜラッパーが必要か**:
- Nimbus JOSE + JWTは汎用ライブラリ（低レベルAPI）
- idp-serverの用途に特化した高レベルAPIを提供
- 例外処理の統一（`JOSEException` → `JoseInvalidException`）

---

## JoseHandlerアーキテクチャ

### 30秒で理解する全体像

```
JOSE文字列（JWT/JWS/JWE）
    ↓
JoseHandler.handle()
    ↓
JoseType判定（plain/signature/encryption）
    ↓
┌──────────────────────────────────┐
│ JoseContextCreatorを選択（Plugin）│
├──────────────────────────────────┤
│ plain      → JwtContextCreator   │
│ signature  → JwsContextCreator   │
│ encryption → JweContextCreator   │
└──────────────────────────────────┘
    ↓
JoseContext
    ├─ JsonWebSignature（署名情報）
    ├─ JsonWebTokenClaims（クレーム）
    ├─ JsonWebSignatureVerifier（検証器）
    └─ JsonWebKey（鍵情報）
```

**実装**: [JoseHandler.java](../../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/jose/JoseHandler.java)

---

## JoseHandler実装

### クラス構造

**実装場所**: `libs/idp-server-platform/src/main/java/org/idp/server/platform/jose/`

```java
public class JoseHandler {

  Map<JoseType, JoseContextCreator> creators;

  public JoseHandler() {
    creators = new HashMap<>();
    creators.put(JoseType.plain, new JwtContextCreator());         // 署名なしJWT
    creators.put(JoseType.signature, new JwsContextCreator());     // 署名付きJWT
    creators.put(JoseType.encryption, new JweContextCreator());    // 暗号化JWT
  }

  public JoseContext handle(String jose, String publicJwks, String privateJwks, String secret)
      throws JoseInvalidException {

    // 1. JoseType判定（JWT/JWS/JWE）
    JoseType joseType = JoseType.parse(jose);

    // 2. Creator選択（Pluginパターン）
    JoseContextCreator joseContextCreator = creators.get(joseType);

    // 3. JoseContext生成
    return joseContextCreator.create(jose, publicJwks, privateJwks, secret);
  }
}
```

**ポイント**:
- ✅ Pluginパターン（`Map<JoseType, JoseContextCreator>`）
- ✅ 3種類のCreator（JWT/JWS/JWE）
- ✅ 鍵情報を引数で受け取る（publicJwks, privateJwks, secret）

---

## JoseType判定

### 3種類のJOSE形式

**実装**: [JoseType.java](../../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/jose/JoseType.java)

```java
public enum JoseType {
  plain,       // JWT（署名なし） - 非推奨（テスト用途のみ）
  signature,   // JWS（署名付きJWT） - 推奨
  encryption;  // JWE（暗号化JWT） - 高セキュリティ

  public static JoseType parse(String jose) {
    String[] parts = jose.split("\\.");

    if (parts.length == 3) {
      return signature;  // JWS形式: header.payload.signature
    } else if (parts.length == 5) {
      return encryption; // JWE形式: header.encrypted_key.iv.ciphertext.tag
    }

    return plain;  // その他はplain JWT
  }
}
```

**判定ロジック**:
- **3パート**: JWS（署名付き） - `header.payload.signature`
- **5パート**: JWE（暗号化） - `header.encrypted_key.iv.ciphertext.tag`
- **その他**: Plain JWT（署名なし、非推奨）

---

## JoseContext

### 役割

JOSEの解析結果を保持するコンテナ。

**実装**: [JoseContext.java](../../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/jose/JoseContext.java)

```java
public class JoseContext {

  JsonWebSignature jsonWebSignature;  // 署名情報
  JsonWebTokenClaims claims;          // クレーム（ペイロード）
  JsonWebSignatureVerifier jwsVerifier;  // 署名検証器
  JsonWebKey jsonWebKey;              // 使用された鍵

  // クレーム取得
  public JsonWebTokenClaims claims() {
    return claims;
  }

  public Map<String, Object> claimsAsMap() {
    return claims.toMap();
  }

  // 署名検証
  public void verifySignature() throws JoseInvalidException {
    if (hasJsonWebSignature()) {
      jwsVerifier.verify(jsonWebSignature);
    }
  }

  // 署名の有無確認
  public boolean hasJsonWebSignature() {
    return jsonWebSignature.exists();
  }

  // 対称鍵アルゴリズム判定
  public boolean isSymmetricKey() {
    return jsonWebSignature.isSymmetricType();  // HS256/HS384/HS512
  }
}
```

**使用例**:
```java
// JOSE文字列を解析
JoseHandler handler = new JoseHandler();
JoseContext context = handler.handle(
    jwtString,
    publicJwks,   // 公開鍵JWKS（RS256等）
    privateJwks,  // 秘密鍵JWKS（復号用）
    secret);      // 共有鍵（HS256等）

// 署名検証
context.verifySignature();

// クレーム取得
Map<String, Object> claims = context.claimsAsMap();
String sub = (String) claims.get("sub");
```

---

## JWS（署名付きJWT）処理

### JwsContextCreator

**実装**: [JwsContextCreator.java](../../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/jose/JwsContextCreator.java)

```java
public class JwsContextCreator implements JoseContextCreator {

  @Override
  public JoseContext create(String jose, String publicJwks, String privateJwks, String secret)
      throws JoseInvalidException {

    // 1. JWS解析
    JsonWebSignature jsonWebSignature = JsonWebSignature.parse(jose);

    // 2. Header解析
    JsonWebSignatureHeader header = jsonWebSignature.header();
    JsonWebSignatureAlgorithm algorithm = header.algorithm();
    String keyId = header.keyId();

    // 3. 鍵選択（アルゴリズムに応じて）
    JsonWebKey jsonWebKey;
    if (algorithm.isSymmetric()) {
      // 対称鍵（HS256/HS384/HS512）
      jsonWebKey = JsonWebKey.parseFromSecret(secret);
    } else {
      // 非対称鍵（RS256/ES256等）
      JsonWebKeys jsonWebKeys = JsonWebKeys.parse(publicJwks);
      jsonWebKey = jsonWebKeys.get(keyId);  // kid で鍵を選択
    }

    // 4. Verifier作成
    JsonWebSignatureVerifier verifier =
        JsonWebSignatureVerifierFactory.create(jsonWebKey, algorithm);

    // 5. クレーム取得
    JsonWebTokenClaims claims = jsonWebSignature.claims();

    // 6. JoseContext生成
    return new JoseContext(jsonWebSignature, claims, verifier, jsonWebKey);
  }
}
```

**処理フロー**:
```
JWS文字列
    ↓
1. 解析（header.payload.signature に分割）
2. Header解析（alg, kid取得）
3. 鍵選択
   - HS256等 → secret使用
   - RS256等 → publicJwks から kid で検索
4. Verifier作成
5. クレーム取得
    ↓
JoseContext
```

---

## 署名アルゴリズム

### サポートされるアルゴリズム

**実装**: [JsonWebSignatureAlgorithm.java](../../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/jose/JsonWebSignatureAlgorithm.java)

#### 対称鍵アルゴリズム（共有鍵）

| アルゴリズム | 説明 | 鍵長 | 用途 |
|------------|------|------|------|
| **HS256** | HMAC SHA-256 | 256bit | Client Secret JWT |
| **HS384** | HMAC SHA-384 | 384bit | - |
| **HS512** | HMAC SHA-512 | 512bit | - |

**特徴**:
- 同じ鍵で署名・検証
- 速い
- 鍵共有が必要（セキュリティリスク）

#### 非対称鍵アルゴリズム（公開鍵暗号）

| アルゴリズム | 説明 | 鍵長 | 用途 |
|------------|------|------|------|
| **RS256** | RSA SHA-256 | 2048bit+ | **ID Token署名（デフォルト）** |
| **RS384** | RSA SHA-384 | 2048bit+ | - |
| **RS512** | RSA SHA-512 | 2048bit+ | - |
| **ES256** | ECDSA P-256 SHA-256 | 256bit | 高速・高セキュリティ |
| **ES384** | ECDSA P-384 SHA-384 | 384bit | - |
| **ES512** | ECDSA P-521 SHA-512 | 521bit | 最高セキュリティ |
| **PS256** | RSA-PSS SHA-256 | 2048bit+ | FAPI推奨 |
| **PS384** | RSA-PSS SHA-384 | 2048bit+ | - |
| **PS512** | RSA-PSS SHA-512 | 2048bit+ | - |

**特徴**:
- 秘密鍵で署名、公開鍵で検証
- 鍵共有不要（公開鍵は配布可能）
- OIDC標準（RS256がデフォルト）

---

## JsonWebSignature

### 署名付きJWTの処理

**実装**: [JsonWebSignature.java](../../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/jose/JsonWebSignature.java)

**ライブラリ委譲**: Nimbus JOSE + JWTの`SignedJWT`クラスをラップ

```java
public class JsonWebSignature {

  SignedJWT value;  // Nimbus JOSE + JWTのSignedJWT（実際の処理はこのライブラリが実行）

  // JWS文字列を解析（Nimbus JOSE + JWTに委譲）
  public static JsonWebSignature parse(String jose) throws JoseInvalidException {
    try {
      SignedJWT signedJWT = SignedJWT.parse(jose);  // ← Nimbus JOSE + JWTライブラリの処理
      return new JsonWebSignature(signedJWT);
    } catch (ParseException e) {
      throw new JoseInvalidException(e.getMessage(), e);
    }
  }

  // クレーム取得
  public JsonWebTokenClaims claims() {
    JWTClaimsSet jwtClaimsSet = value.getJWTClaimsSet();
    return new JsonWebTokenClaims(jwtClaimsSet);
  }

  // 署名検証（Nimbus JOSE + JWTに委譲）
  boolean verify(JWSVerifier verifier) throws JoseInvalidException {
    try {
      return value.verify(verifier);  // ← Nimbus JOSE + JWTライブラリの処理
    } catch (JOSEException e) {
      throw new JoseInvalidException(e.getMessage(), e);
    }
  }

  // 対称鍵アルゴリズム判定
  public boolean isSymmetricType() {
    JWSAlgorithm algorithm = value.getHeader().getAlgorithm();
    return algorithm.equals(JWSAlgorithm.HS256)
        || algorithm.equals(JWSAlgorithm.HS384)
        || algorithm.equals(JWSAlgorithm.HS512);
  }

  // Key ID取得
  public String keyId() {
    return value.getHeader().getKeyID();
  }

  // アルゴリズム取得
  public String algorithm() {
    return value.getHeader().getAlgorithm().getName();
  }
}
```

**JWS形式**:
```
eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6ImtleS0xIn0.
eyJzdWIiOiJ1c2VyLTEyMzQ1IiwiaWF0IjoxNjk1NTUyMDAwLCJleHAiOjE2OTU1NTU2MDB9.
signature_base64url

↓ 分解

Header (Base64URL):
{
  "alg": "RS256",  ← 署名アルゴリズム
  "typ": "JWT",
  "kid": "key-1"   ← Key ID（JWKSから鍵を検索）
}

Payload (Base64URL):
{
  "sub": "user-12345",
  "iat": 1695552000,
  "exp": 1695555600
}

Signature (Base64URL):
RS256(Header + "." + Payload, privateKey)
```

---

## JsonWebTokenClaims

### クレーム（ペイロード）の処理

**実装**: [JsonWebTokenClaims.java](../../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/jose/JsonWebTokenClaims.java)

**ライブラリ委譲**: Nimbus JOSE + JWTの`JWTClaimsSet`クラスをラップ

```java
public class JsonWebTokenClaims {

  JWTClaimsSet value;  // Nimbus JOSE + JWTのJWTClaimsSet（実際の処理はこのライブラリが実行）

  // 標準クレーム取得
  public String subject() {
    return value.getSubject();
  }

  public String issuer() {
    return value.getIssuer();
  }

  public List<String> audience() {
    return value.getAudience();
  }

  public Date expirationTime() {
    return value.getExpirationTime();
  }

  public Date issuedAt() {
    return value.getIssueTime();
  }

  // カスタムクレーム取得
  public Object getClaim(String key) {
    return value.getClaim(key);
  }

  // すべてのクレームをMapで取得
  public Map<String, Object> toMap() {
    return value.getClaims();
  }

  // クレームの存在確認
  public boolean exists() {
    return Objects.nonNull(value) && !value.getClaims().isEmpty();
  }
}
```

**標準クレーム（RFC 7519）**:

| クレーム | 説明 | 必須 | 例 |
|---------|------|------|---|
| **sub** | Subject（ユーザーID） | ✅ | `"user-12345"` |
| **iss** | Issuer（発行者） | ✅ | `"https://idp.example.com"` |
| **aud** | Audience（対象） | ✅ | `["client-app-123"]` |
| **exp** | Expiration Time（有効期限） | ✅ | `1695555600` (Unix時刻) |
| **iat** | Issued At（発行時刻） | 推奨 | `1695552000` |
| **nbf** | Not Before（有効開始時刻） | - | `1695552000` |
| **jti** | JWT ID（一意識別子） | - | `"jwt-uuid-abc"` |

**カスタムクレーム例（ID Token）**:
```json
{
  "sub": "user-12345",
  "iss": "https://idp.example.com",
  "aud": ["client-app"],
  "exp": 1695555600,
  "iat": 1695552000,
  "nonce": "random-nonce-xyz",  ← カスタム
  "at_hash": "abc123...",        ← カスタム（Access Tokenハッシュ）
  "c_hash": "def456..."          ← カスタム（Codeハッシュ）
}
```

---

## 署名検証フロー

### JwsContextCreatorの詳細処理

```
JWS文字列: "eyJhbGc...eyJzdWI...signature"
    ↓
1. JsonWebSignature.parse()
   └─ SignedJWT解析（Nimbus JOSE）
    ↓
2. Header解析
   ├─ alg: "RS256"
   └─ kid: "key-1"
    ↓
3. 鍵選択
   ├─ algorithm.isSymmetric()?
   │   YES → JsonWebKey.parseFromSecret(secret)
   │   NO  → JsonWebKeys.parse(publicJwks).get(kid)
   └─ JsonWebKey取得
    ↓
4. Verifier作成
   └─ JsonWebSignatureVerifierFactory.create(key, algorithm)
      ├─ RS256 → RSASSAVerifier
      ├─ ES256 → ECDSAVerifier
      └─ HS256 → MACVerifier
    ↓
5. クレーム取得
   └─ jsonWebSignature.claims()
    ↓
JoseContext {
  jsonWebSignature,
  claims,
  verifier,
  jsonWebKey
}
    ↓
6. 署名検証実行（使用側で）
   context.verifySignature()
```

---

## 使用例

### ID Token検証

```java
// ID Token検証
public void validateIdToken(String idTokenString, String publicJwks)
    throws JoseInvalidException {

  // 1. JoseHandler でID Token解析
  JoseHandler joseHandler = new JoseHandler();
  JoseContext context = joseHandler.handle(
      idTokenString,
      publicJwks,   // 公開鍵JWKS
      null,         // 秘密鍵不要（検証のみ）
      null);        // 共有鍵不要（RS256使用）

  // 2. 署名検証
  context.verifySignature();  // 署名が不正ならJoseInvalidException

  // 3. クレーム検証
  Map<String, Object> claims = context.claimsAsMap();

  // iss検証
  String iss = (String) claims.get("iss");
  if (!iss.equals("https://idp.example.com")) {
    throw new JoseInvalidException("Invalid issuer");
  }

  // aud検証
  List<String> aud = (List<String>) claims.get("aud");
  if (!aud.contains(expectedClientId)) {
    throw new JoseInvalidException("Invalid audience");
  }

  // exp検証
  long exp = (Long) claims.get("exp");
  if (System.currentTimeMillis() / 1000 > exp) {
    throw new JoseInvalidException("Token expired");
  }

  // nonce検証（OIDC）
  String nonce = (String) claims.get("nonce");
  if (!nonce.equals(expectedNonce)) {
    throw new JoseInvalidException("Invalid nonce");
  }
}
```

---

### Access Token生成

```java
// Access Token生成（JWS署名付き）
public String createAccessToken(
    String sub,
    List<String> scopes,
    JsonWebKey privateKey) throws JoseInvalidException {

  // 1. クレーム作成
  Map<String, Object> claims = new HashMap<>();
  claims.put("sub", sub);
  claims.put("iss", "https://idp.example.com");
  claims.put("aud", Arrays.asList("api-server"));
  claims.put("scope", String.join(" ", scopes));
  claims.put("iat", System.currentTimeMillis() / 1000);
  claims.put("exp", System.currentTimeMillis() / 1000 + 3600);  // 1時間

  // 2. JWS生成
  JsonWebSignatureFactory factory = new JsonWebSignatureFactory();
  JsonWebSignature jws = factory.create(
      claims,
      privateKey,
      JsonWebSignatureAlgorithm.RS256);

  // 3. JWS文字列化
  return jws.serialize();
}
```

---

## JWK（JSON Web Key）

### 公開鍵の表現形式

**実装**: [JsonWebKey.java](../../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/jose/JsonWebKey.java)

**ライブラリ委譲**: Nimbus JOSE + JWTの`JWK`/`JWKSet`クラスをラップ

```java
public class JsonWebKey {

  JWK value;  // Nimbus JOSE + JWTのJWK（実際の処理はこのライブラリが実行）

  // JWKS文字列から解析
  public static JsonWebKeys parse(String jwks) {
    JWKSet jwkSet = JWKSet.parse(jwks);
    return new JsonWebKeys(jwkSet);
  }

  // Key ID取得
  public String keyId() {
    return value.getKeyID();
  }

  // アルゴリズム取得
  public String algorithm() {
    return value.getAlgorithm().getName();
  }

  // 鍵タイプ判定
  public boolean isRSA() {
    return value instanceof RSAKey;
  }

  public boolean isEC() {
    return value instanceof ECKey;
  }

  public boolean isOctetSequence() {
    return value instanceof OctetSequenceKey;
  }
}
```

**JWKS形式**:
```json
{
  "keys": [
    {
      "kty": "RSA",
      "kid": "key-1",
      "use": "sig",
      "alg": "RS256",
      "n": "modulus_base64url",
      "e": "exponent_base64url"
    },
    {
      "kty": "EC",
      "kid": "key-2",
      "use": "sig",
      "alg": "ES256",
      "crv": "P-256",
      "x": "x_coordinate_base64url",
      "y": "y_coordinate_base64url"
    }
  ]
}
```

---

## よくある使用パターン

### パターン1: ID Token検証（外部IdP）

```java
// フェデレーションでの外部IdP（Google）のID Token検証
JoseHandler handler = new JoseHandler();
JoseContext context = handler.handle(
    googleIdToken,
    googlePublicJwks,  // https://www.googleapis.com/oauth2/v3/certs
    null,
    null);

// 署名検証
context.verifySignature();

// nonce検証
Map<String, Object> claims = context.claimsAsMap();
String nonce = (String) claims.get("nonce");
if (!nonce.equals(ssoSession.nonce())) {
  throw new JoseInvalidException("Nonce mismatch");
}
```

---

### パターン2: Request Object検証（OIDC）

```java
// クライアントが送信したRequest Object（JWT）の検証
JoseHandler handler = new JoseHandler();
JoseContext context = handler.handle(
    requestObject,
    clientPublicJwks,  // クライアントの公開鍵JWKS
    null,
    null);

// 署名検証
context.verifySignature();

// クレーム取得（リクエストパラメータ）
Map<String, Object> claims = context.claimsAsMap();
String clientId = (String) claims.get("client_id");
String redirectUri = (String) claims.get("redirect_uri");
String scope = (String) claims.get("scope");
```

---

### パターン3: client_secret_jwt検証

```java
// クライアント認証（client_secret_jwt）
JoseHandler handler = new JoseHandler();
JoseContext context = handler.handle(
    clientAssertion,
    null,
    null,
    clientSecret);  // Client Secretを共有鍵として使用（HS256）

// 署名検証
context.verifySignature();

// クレーム検証
Map<String, Object> claims = context.claimsAsMap();
String sub = (String) claims.get("sub");  // client_id
String aud = (String) claims.get("aud");  // token endpoint
```

---

## よくあるエラー

### エラー1: `JoseInvalidException` - 署名検証失敗

**原因**:
- 不正な署名
- 鍵の不一致（kidが見つからない）
- アルゴリズムの不一致

**解決策**:
```java
// 1. kid確認
String kid = jsonWebSignature.keyId();

// 2. JWKS に kid が存在するか確認
JsonWebKeys jwks = JsonWebKeys.parse(publicJwks);
if (!jwks.hasKey(kid)) {
  throw new JsonWebKeyNotFoundException("Key not found: " + kid);
}

// 3. アルゴリズム確認
String alg = jsonWebSignature.algorithm();  // "RS256"
```

---

### エラー2: `ParseException` - JWT解析失敗

**原因**:
- JWT形式が不正
- Base64URLデコード失敗

**解決策**:
```java
try {
  JsonWebSignature jws = JsonWebSignature.parse(jwtString);
} catch (JoseInvalidException e) {
  // JWT形式が不正
  log.error("Invalid JWT format: {}", e.getMessage());
}
```

---

### エラー3: `JsonWebKeyNotFoundException` - 鍵が見つからない

**原因**:
- kidがJWKSに存在しない
- JWKS取得失敗

**解決策**:
```java
// JWKS更新（外部IdPのJWKSを再取得）
String jwks = httpClient.get("https://idp.example.com/.well-known/jwks.json");
JsonWebKeys jsonWebKeys = JsonWebKeys.parse(jwks);
```

---

## 次のステップ

✅ JOSE（JWT/JWS/JWE）処理の実装を理解した！

### 📖 関連ドキュメント

- [実装ガイド: ID Token生成](../../content_03_concepts/04-tokens-claims/concept-01-id-token.md) - at_hash/c_hash計算

### 🔗 詳細情報

- [RFC 7519 - JWT](https://datatracker.ietf.org/doc/html/rfc7519)
- [RFC 7515 - JWS](https://datatracker.ietf.org/doc/html/rfc7515)
- [RFC 7517 - JWK](https://datatracker.ietf.org/doc/html/rfc7517)

---

**情報源**:
- [JoseHandler.java](../../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/jose/JoseHandler.java)
- [JsonWebSignature.java](../../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/jose/JsonWebSignature.java)
- [JsonWebTokenClaims.java](../../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/jose/JsonWebTokenClaims.java)

**最終更新**: 2025-10-13
