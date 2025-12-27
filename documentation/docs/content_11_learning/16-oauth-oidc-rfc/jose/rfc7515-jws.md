# RFC 7515: JSON Web Signature（JWS）

RFC 7515 は、JSON ベースのデータに署名を付与するための仕様です。JWT の署名部分は JWS を使用しています。

---

## 第1部: 概要編

### JWS とは何か？

JWS（JSON Web Signature）は、任意のデータに**デジタル署名**または **MAC（メッセージ認証コード）** を付与するための標準フォーマットです。

```
JWS の役割:
  ┌─────────────┐
  │  ペイロード   │  ← 保護したいデータ
  └─────────────┘
        │
        ▼ 署名を付与
  ┌─────────────┐
  │    JWS     │  ← 署名付きデータ
  └─────────────┘
        │
        ▼ 受信者が検証
  ✅ 改ざんされていない
  ✅ 発行者が正しい
```

### JWT との関係

JWT（RFC 7519）の署名付きトークンは、JWS を使用して実装されています。

```
JWT = JWS を使って署名されたクレームセット

eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJodHRwczovL2V4YW1wbGUuY29tIn0.signature
└─────┬─────┘.└──────────────┬──────────────┘.└───┬───┘
   Header              Payload              Signature
   (JWS)               (JWT Claims)         (JWS)
```

### 2つのシリアライズ形式

JWS には 2 つのシリアライズ形式があります。

| 形式 | 用途 | 例 |
|------|------|-----|
| Compact Serialization | HTTP ヘッダー、URL に含める場合 | JWT で使用 |
| JSON Serialization | 複数署名、複雑な構造が必要な場合 | API レスポンス |

---

## 第2部: 詳細編

### Compact Serialization

最も一般的な形式。3 つのパートをドット（`.`）で連結します。

```
BASE64URL(Header).BASE64URL(Payload).BASE64URL(Signature)
```

#### Header（JOSE Header）

署名アルゴリズムやメタデータを含む JSON オブジェクト。

```json
{
  "alg": "RS256",
  "typ": "JWT",
  "kid": "key-2024-01"
}
```

| パラメータ | 必須 | 説明 |
|-----------|------|------|
| `alg` | ✅ | 署名アルゴリズム |
| `typ` | △ | メディアタイプ（例: `JWT`） |
| `kid` | △ | 鍵 ID |
| `jku` | △ | JWK Set URL |
| `jwk` | △ | 公開鍵（JWK 形式） |
| `x5u` | △ | X.509 証明書 URL |
| `x5c` | △ | X.509 証明書チェーン |
| `x5t` | △ | X.509 証明書のサムプリント（SHA-1） |
| `x5t#S256` | △ | X.509 証明書のサムプリント（SHA-256） |
| `crit` | △ | クリティカルヘッダーパラメータ |

#### Payload

署名対象のデータ。任意のバイト列。

JWT の場合はクレームセット（JSON）がペイロードになります。

```json
{
  "iss": "https://example.com",
  "sub": "user-123",
  "exp": 1704070800
}
```

#### Signature

ヘッダーとペイロードを連結したものに対する署名。

```
Signature = SIGN(
  BASE64URL(Header) + "." + BASE64URL(Payload),
  Key
)
```

### JSON Serialization

複数の署名を持つ場合や、保護されないヘッダーが必要な場合に使用します。

#### General JSON Serialization

```json
{
  "payload": "eyJpc3MiOiJodHRwczovL2V4YW1wbGUuY29tIn0",
  "signatures": [
    {
      "protected": "eyJhbGciOiJSUzI1NiJ9",
      "header": {
        "kid": "key-2024-01"
      },
      "signature": "cC4hiUPoj9..."
    },
    {
      "protected": "eyJhbGciOiJFUzI1NiJ9",
      "header": {
        "kid": "key-2024-02"
      },
      "signature": "DtEhU3..."
    }
  ]
}
```

#### Flattened JSON Serialization

単一の署名の場合に使用する簡略形式。

```json
{
  "payload": "eyJpc3MiOiJodHRwczovL2V4YW1wbGUuY29tIn0",
  "protected": "eyJhbGciOiJSUzI1NiJ9",
  "header": {
    "kid": "key-2024-01"
  },
  "signature": "cC4hiUPoj9..."
}
```

### 署名アルゴリズム

RFC 7518（JWA）で定義されている署名アルゴリズム。

#### HMAC（対称鍵）

```
HS256: HMAC using SHA-256
HS384: HMAC using SHA-384
HS512: HMAC using SHA-512
```

同じ秘密鍵で署名と検証を行う。

```java
// 署名
Mac mac = Mac.getInstance("HmacSHA256");
mac.init(new SecretKeySpec(secret, "HmacSHA256"));
byte[] signature = mac.doFinal(signingInput.getBytes());

// 検証
Mac mac = Mac.getInstance("HmacSHA256");
mac.init(new SecretKeySpec(secret, "HmacSHA256"));
byte[] expected = mac.doFinal(signingInput.getBytes());
boolean valid = MessageDigest.isEqual(signature, expected);
```

#### RSA（非対称鍵）

```
RS256: RSASSA-PKCS1-v1_5 using SHA-256
RS384: RSASSA-PKCS1-v1_5 using SHA-384
RS512: RSASSA-PKCS1-v1_5 using SHA-512

PS256: RSASSA-PSS using SHA-256 and MGF1 with SHA-256
PS384: RSASSA-PSS using SHA-384 and MGF1 with SHA-384
PS512: RSASSA-PSS using SHA-512 and MGF1 with SHA-512
```

秘密鍵で署名、公開鍵で検証。

```java
// 署名
Signature sig = Signature.getInstance("SHA256withRSA");
sig.initSign(privateKey);
sig.update(signingInput.getBytes());
byte[] signature = sig.sign();

// 検証
Signature sig = Signature.getInstance("SHA256withRSA");
sig.initVerify(publicKey);
sig.update(signingInput.getBytes());
boolean valid = sig.verify(signature);
```

#### ECDSA（楕円曲線）

```
ES256: ECDSA using P-256 and SHA-256
ES384: ECDSA using P-384 and SHA-384
ES512: ECDSA using P-521 and SHA-512
```

RSA より短い鍵で同等のセキュリティ。

```java
// 署名
Signature sig = Signature.getInstance("SHA256withECDSA");
sig.initSign(privateKey);
sig.update(signingInput.getBytes());
byte[] signature = sig.sign();
// 注意: JWS 形式に変換が必要（R || S 形式）
```

#### EdDSA

```
EdDSA: Edwards-curve Digital Signature Algorithm（Ed25519, Ed448）
```

RFC 8037 で追加。高速で安全。

#### none（署名なし）

```
none: No digital signature or MAC
```

**絶対に本番環境で使用しない**。テスト目的のみ。

### 署名の生成と検証

#### 署名の生成手順

```
1. JOSE Header を作成
   {"alg": "RS256", "typ": "JWT"}

2. Header を Base64URL エンコード
   eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9

3. Payload を Base64URL エンコード
   eyJzdWIiOiJ1c2VyLTEyMyJ9

4. 署名入力を作成
   eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyLTEyMyJ9

5. 署名を生成
   signature = SIGN(signing_input, private_key)

6. 署名を Base64URL エンコード
   dGhpcyBpcyBhIHNpZ25hdHVyZQ

7. JWS Compact Serialization を作成
   eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyLTEyMyJ9.dGhpcyBpcyBhIHNpZ25hdHVyZQ
```

#### 署名の検証手順

```
1. JWS をパース
   Header.Payload.Signature に分割

2. Header を Base64URL デコード
   alg を確認（許可されたアルゴリズムか？）

3. 署名入力を再構築
   BASE64URL(Header) + "." + BASE64URL(Payload)

4. 署名を Base64URL デコード

5. 適切な鍵を取得
   kid があれば鍵を特定

6. 署名を検証
   VERIFY(signing_input, signature, public_key)
```

### 実装例

#### Java（Nimbus JOSE + JWT）

```java
// 署名の生成
JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
    .keyID("key-2024-01")
    .build();

Payload payload = new Payload("{\"sub\":\"user-123\"}");

JWSObject jwsObject = new JWSObject(header, payload);
jwsObject.sign(new RSASSASigner(privateKey));

String jws = jwsObject.serialize();

// 署名の検証
JWSObject parsed = JWSObject.parse(jws);
JWSVerifier verifier = new RSASSAVerifier(publicKey);

if (parsed.verify(verifier)) {
    String payload = parsed.getPayload().toString();
    // 検証成功
} else {
    throw new SecurityException("Invalid signature");
}
```

#### JavaScript（jose）

```javascript
import * as jose from 'jose';

// 署名の生成
const privateKey = await jose.importPKCS8(privateKeyPem, 'RS256');

const jws = await new jose.CompactSign(
  new TextEncoder().encode(JSON.stringify({ sub: 'user-123' }))
)
  .setProtectedHeader({ alg: 'RS256', kid: 'key-2024-01' })
  .sign(privateKey);

// 署名の検証
const publicKey = await jose.importSPKI(publicKeyPem, 'RS256');

const { payload, protectedHeader } = await jose.compactVerify(jws, publicKey);
const claims = JSON.parse(new TextDecoder().decode(payload));
```

### セキュリティ考慮事項

| 項目 | 推奨事項 |
|------|----------|
| `alg: none` | 拒否する。ホワイトリストでアルゴリズムを制限 |
| 鍵混同攻撃 | アルゴリズムと鍵タイプの整合性を確認 |
| 署名検証 | 必ず行う。スキップしない |
| 鍵のローテーション | 定期的に実施。`kid` で鍵を識別 |
| 署名アルゴリズム | RS256, ES256, PS256 を推奨 |

### Detached Payload

ペイロードを JWS 自体に含めず、別途送信する方式。

```
JWS: eyJhbGciOiJSUzI1NiJ9..signature
                        ↑ ペイロード部分が空

ペイロードは別途送信され、検証時に結合
```

HTTP 署名などで使用されます。

---

## 参考リンク

- [RFC 7515 - JSON Web Signature (JWS)](https://datatracker.ietf.org/doc/html/rfc7515)
- [RFC 7518 - JSON Web Algorithms (JWA)](https://datatracker.ietf.org/doc/html/rfc7518)
- [RFC 7519 - JSON Web Token (JWT)](https://datatracker.ietf.org/doc/html/rfc7519)
- [RFC 8037 - CFRG Elliptic Curve Diffie-Hellman (ECDH) and Signatures in JOSE](https://datatracker.ietf.org/doc/html/rfc8037)
