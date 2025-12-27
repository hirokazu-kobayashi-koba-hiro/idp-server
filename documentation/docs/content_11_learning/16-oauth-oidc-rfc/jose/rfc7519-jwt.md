# RFC 7519: JSON Web Token（JWT）

RFC 7519 は、JSON 形式でクレーム（主張）を表現するためのトークン仕様です。このドキュメントでは、JWT の構造と使用方法を解説します。

---

## 第1部: 概要編

### JWT とは何か？

JWT（JSON Web Token、ジョットと読む）は、2 者間で情報を安全にやり取りするための**コンパクトで URL セーフなトークン形式**です。

```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c
└─────────────┬─────────────┘.└───────────────────────────┬───────────────────────────┘.└──────────────────┬──────────────────┘
           Header                                      Payload                                        Signature
```

### JWT の用途

| 用途 | 説明 |
|------|------|
| アクセストークン | OAuth 2.0 のアクセストークンとして使用 |
| ID トークン | OpenID Connect でユーザー情報を伝達 |
| クライアント認証 | private_key_jwt / client_secret_jwt |
| 情報交換 | サービス間で署名付きデータをやり取り |

### JWT の特徴

| 特徴 | 説明 |
|------|------|
| コンパクト | Base64URL エンコードで URL やヘッダーに含めやすい |
| 自己完結 | トークン自体に必要な情報が含まれる |
| 検証可能 | 署名により改ざんを検知できる |
| 暗号化可能 | JWE を使えば内容を秘匿できる |

---

## 第2部: 詳細編

### JWT の構造

JWT は 3 つのパートをドット（`.`）で連結した文字列です。

```
Header.Payload.Signature
```

#### Header（ヘッダー）

トークンのメタデータ。署名アルゴリズムとトークンタイプを指定。

```json
{
  "alg": "RS256",
  "typ": "JWT",
  "kid": "key-id-123"
}
```

| フィールド | 必須 | 説明 |
|-----------|------|------|
| `alg` | ✅ | 署名アルゴリズム（HS256, RS256, ES256 など） |
| `typ` | △ | トークンタイプ（通常 "JWT"） |
| `kid` | △ | 鍵 ID（複数鍵がある場合に使用） |

#### Payload（ペイロード）

クレーム（主張）のセット。ユーザー情報やトークンのメタデータを含む。

```json
{
  "iss": "https://auth.example.com",
  "sub": "user-123",
  "aud": "https://api.example.com",
  "exp": 1704070800,
  "iat": 1704067200,
  "nbf": 1704067200,
  "jti": "unique-token-id"
}
```

#### Signature（署名）

ヘッダーとペイロードを署名したもの。改ざん検知に使用。

```
RSASHA256(
  base64UrlEncode(header) + "." + base64UrlEncode(payload),
  privateKey
)
```

### 登録済みクレーム（Registered Claims）

RFC 7519 で定義されている標準クレーム。

| クレーム | 名前 | 説明 |
|---------|------|------|
| `iss` | Issuer | トークン発行者 |
| `sub` | Subject | トークンの主体（ユーザー ID など） |
| `aud` | Audience | トークンの対象者（受信者） |
| `exp` | Expiration Time | 有効期限（Unix タイムスタンプ） |
| `nbf` | Not Before | 有効開始時刻 |
| `iat` | Issued At | 発行時刻 |
| `jti` | JWT ID | トークンの一意識別子 |

**すべて任意だが、用途に応じて必須になる。**

### 公開クレーム（Public Claims）

IANA に登録されているか、衝突回避のために URI 形式で定義されるクレーム。

```json
{
  "https://example.com/claims/role": "admin",
  "email": "user@example.com",
  "name": "John Doe"
}
```

### プライベートクレーム（Private Claims）

発行者と受信者の間で合意されたカスタムクレーム。

```json
{
  "department": "engineering",
  "employee_id": "EMP-12345"
}
```

### 署名アルゴリズム

| アルゴリズム | 種類 | 鍵 | 用途 |
|-------------|------|-----|------|
| `HS256` | HMAC | 共有秘密 | シンプルな用途 |
| `HS384` | HMAC | 共有秘密 | |
| `HS512` | HMAC | 共有秘密 | |
| `RS256` | RSA | 公開鍵/秘密鍵 | 一般的な用途 |
| `RS384` | RSA | 公開鍵/秘密鍵 | |
| `RS512` | RSA | 公開鍵/秘密鍵 | |
| `ES256` | ECDSA | 公開鍵/秘密鍵 | コンパクト |
| `ES384` | ECDSA | 公開鍵/秘密鍵 | |
| `ES512` | ECDSA | 公開鍵/秘密鍵 | |
| `PS256` | RSA-PSS | 公開鍵/秘密鍵 | より安全な RSA |
| `none` | なし | - | **使用禁止** |

### JWT の生成

#### Java（Nimbus JOSE + JWT）

```java
// 1. クレームセットを作成
JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
    .issuer("https://auth.example.com")
    .subject("user-123")
    .audience("https://api.example.com")
    .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
    .issueTime(new Date())
    .jwtID(UUID.randomUUID().toString())
    .claim("email", "user@example.com")
    .build();

// 2. ヘッダーを作成
JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
    .keyID("key-id-123")
    .build();

// 3. 署名付き JWT を作成
SignedJWT signedJWT = new SignedJWT(header, claimsSet);
signedJWT.sign(new RSASSASigner(privateKey));

// 4. シリアライズ
String token = signedJWT.serialize();
```

#### JavaScript（jose）

```javascript
import * as jose from 'jose';

const privateKey = await jose.importPKCS8(privateKeyPem, 'RS256');

const token = await new jose.SignJWT({
    email: 'user@example.com'
  })
  .setProtectedHeader({ alg: 'RS256', kid: 'key-id-123' })
  .setIssuer('https://auth.example.com')
  .setSubject('user-123')
  .setAudience('https://api.example.com')
  .setExpirationTime('1h')
  .setIssuedAt()
  .setJti(crypto.randomUUID())
  .sign(privateKey);
```

### JWT の検証

検証時に確認すべき項目：

```
1. 署名検証
   └── 公開鍵または共有秘密で署名を検証

2. 構造検証
   └── 3 パートに分割できるか
   └── 各パートが有効な Base64URL か
   └── ペイロードが有効な JSON か

3. クレーム検証
   ├── exp: 現在時刻 < exp（有効期限内か）
   ├── nbf: 現在時刻 >= nbf（有効開始時刻を過ぎているか）
   ├── iat: 発行時刻が妥当か
   ├── iss: 期待する発行者か
   ├── aud: 自分が対象者に含まれているか
   └── その他のビジネスロジック
```

#### Java での検証例

```java
public class JWTValidator {
    
    private final JWKSource<SecurityContext> jwkSource;
    private final String expectedIssuer;
    private final String expectedAudience;
    
    public JWTClaimsSet validate(String token) throws Exception {
        // 1. パース
        SignedJWT signedJWT = SignedJWT.parse(token);
        
        // 2. 鍵の取得
        JWSHeader header = signedJWT.getHeader();
        JWKSelector selector = new JWKSelector(
            new JWKMatcher.Builder()
                .keyID(header.getKeyID())
                .build()
        );
        List<JWK> jwks = jwkSource.get(selector, null);
        
        if (jwks.isEmpty()) {
            throw new JWTVerificationException("Key not found");
        }
        
        // 3. 署名検証
        JWSVerifier verifier = new RSASSAVerifier(
            jwks.get(0).toRSAKey().toRSAPublicKey()
        );
        
        if (!signedJWT.verify(verifier)) {
            throw new JWTVerificationException("Invalid signature");
        }
        
        // 4. クレーム検証
        JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
        
        // 有効期限
        if (claims.getExpirationTime().before(new Date())) {
            throw new JWTVerificationException("Token expired");
        }
        
        // 発行者
        if (!expectedIssuer.equals(claims.getIssuer())) {
            throw new JWTVerificationException("Invalid issuer");
        }
        
        // 対象者
        if (!claims.getAudience().contains(expectedAudience)) {
            throw new JWTVerificationException("Invalid audience");
        }
        
        return claims;
    }
}
```

### JWT vs JWS vs JWE

| 用語 | 説明 |
|------|------|
| **JWT** | クレームを表現するトークン形式 |
| **JWS** | 署名付きデータの形式（JWT の署名に使用） |
| **JWE** | 暗号化データの形式（JWT の暗号化に使用） |

```
JWT は JWS または JWE を使って実装される

署名付き JWT:
  JWT → JWS 形式 → Header.Payload.Signature

暗号化 JWT:
  JWT → JWE 形式 → Header.EncryptedKey.IV.Ciphertext.Tag

署名 + 暗号化:
  JWT → JWS → JWE（Nested JWT）
```

### セキュリティ考慮事項

| 項目 | 推奨事項 |
|------|----------|
| `alg: none` | 絶対に受け入れない |
| 署名検証 | 必ず行う。スキップしない |
| 有効期限 | 短く設定（アクセストークンは数分〜数時間） |
| 機密情報 | ペイロードに含めない（Base64 はエンコードであり暗号化ではない） |
| 鍵管理 | 秘密鍵は安全に保管。定期的にローテーション |
| aud 検証 | 必ず行う。別サービス向けトークンの誤用を防ぐ |

### よくある脆弱性

#### 1. alg: none 攻撃

```json
// 攻撃者が送信
{
  "alg": "none",
  "typ": "JWT"
}

// 対策: alg のホワイトリストを使用
Set<String> allowedAlgorithms = Set.of("RS256", "ES256");
if (!allowedAlgorithms.contains(header.getAlgorithm())) {
    throw new Exception("Algorithm not allowed");
}
```

#### 2. 鍵混同攻撃

```
攻撃者が RS256 → HS256 に変更し、
公開鍵を HMAC の秘密鍵として使用させる

対策: 鍵タイプとアルゴリズムの整合性を確認
```

#### 3. 署名検証スキップ

```
// 危険なコード
JWTClaimsSet claims = SignedJWT.parse(token).getJWTClaimsSet();
// 署名検証していない！

// 安全なコード
SignedJWT jwt = SignedJWT.parse(token);
if (!jwt.verify(verifier)) {
    throw new Exception("Invalid signature");
}
JWTClaimsSet claims = jwt.getJWTClaimsSet();
```

---

## 参考リンク

- [RFC 7519 - JSON Web Token (JWT)](https://datatracker.ietf.org/doc/html/rfc7519)
- [RFC 7515 - JSON Web Signature (JWS)](https://datatracker.ietf.org/doc/html/rfc7515)
- [RFC 7516 - JSON Web Encryption (JWE)](https://datatracker.ietf.org/doc/html/rfc7516)
- [jwt.io](https://jwt.io/) - JWT デバッガー
