# RFC 7517: JSON Web Key（JWK）

RFC 7517 は、暗号鍵を JSON 形式で表現するための仕様です。JWS や JWE で使用する鍵を配布・管理するために使用します。

---

## 第1部: 概要編

### JWK とは何か？

JWK（JSON Web Key）は、暗号鍵を **JSON 形式**で表現するための標準フォーマットです。

```json
{
  "kty": "RSA",
  "kid": "key-2024-01",
  "use": "sig",
  "n": "0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx4cbbfAAtVT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMstn64tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FDW2QvzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajrn1n91CbOpbISD08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINHaQ-G_xBniIqbw0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw",
  "e": "AQAB"
}
```

### なぜ JWK が必要なのか？

従来の鍵フォーマット（PEM、DER）と比較した JWK の利点：

| 観点 | PEM/DER | JWK |
|------|---------|-----|
| フォーマット | バイナリ/Base64 | JSON |
| メタデータ | 別途管理が必要 | 鍵と一緒に格納 |
| 複数鍵の管理 | ファイルを分ける | JWK Set で一括管理 |
| Web API との親和性 | エンコードが必要 | そのまま使用可能 |
| 鍵の識別 | ファイル名に依存 | `kid` で識別 |

### JWK Set

複数の JWK をまとめたもの。認可サーバーの JWKS エンドポイントで公開されます。

```json
{
  "keys": [
    {
      "kty": "RSA",
      "kid": "key-2024-01",
      "use": "sig",
      "n": "...",
      "e": "AQAB"
    },
    {
      "kty": "EC",
      "kid": "key-2024-02",
      "use": "sig",
      "crv": "P-256",
      "x": "...",
      "y": "..."
    }
  ]
}
```

---

## 第2部: 詳細編

### 共通パラメータ

すべての JWK に共通するパラメータ。

| パラメータ | 必須 | 説明 |
|-----------|------|------|
| `kty` | ✅ | 鍵タイプ（`RSA`, `EC`, `oct`, `OKP`） |
| `use` | △ | 用途（`sig` = 署名, `enc` = 暗号化） |
| `key_ops` | △ | 鍵操作（`sign`, `verify`, `encrypt`, `decrypt` など） |
| `alg` | △ | アルゴリズム（`RS256`, `ES256` など） |
| `kid` | △ | 鍵 ID |
| `x5u` | △ | X.509 証明書 URL |
| `x5c` | △ | X.509 証明書チェーン |
| `x5t` | △ | X.509 証明書サムプリント（SHA-1） |
| `x5t#S256` | △ | X.509 証明書サムプリント（SHA-256） |

#### use vs key_ops

```
use: 高レベルの用途
  "sig" → 署名・検証
  "enc" → 暗号化・復号

key_ops: 詳細な操作
  ["sign"]           → 署名のみ
  ["verify"]         → 検証のみ
  ["sign", "verify"] → 署名と検証

use と key_ops は同時に指定しないことを推奨
```

### RSA 鍵（kty: "RSA"）

#### 公開鍵

```json
{
  "kty": "RSA",
  "kid": "rsa-key-1",
  "use": "sig",
  "n": "0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx4cbbfAAtVT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMstn64tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FDW2QvzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajrn1n91CbOpbISD08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINHaQ-G_xBniIqbw0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw",
  "e": "AQAB"
}
```

| パラメータ | 説明 |
|-----------|------|
| `n` | Modulus（モジュラス） |
| `e` | Exponent（指数、通常 65537 = AQAB） |

#### 秘密鍵（追加パラメータ）

```json
{
  "kty": "RSA",
  "kid": "rsa-key-1",
  "n": "...",
  "e": "AQAB",
  "d": "...",
  "p": "...",
  "q": "...",
  "dp": "...",
  "dq": "...",
  "qi": "..."
}
```

| パラメータ | 説明 |
|-----------|------|
| `d` | Private Exponent |
| `p` | First Prime Factor |
| `q` | Second Prime Factor |
| `dp` | First Factor CRT Exponent |
| `dq` | Second Factor CRT Exponent |
| `qi` | First CRT Coefficient |

### EC 鍵（kty: "EC"）

#### 公開鍵

```json
{
  "kty": "EC",
  "kid": "ec-key-1",
  "use": "sig",
  "crv": "P-256",
  "x": "f83OJ3D2xF1Bg8vub9tLe1gHMzV76e8Tus9uPHvRVEU",
  "y": "x_FEzRu9m36HLN_tue659LNpXW6pCyStikYjKIWI5a0"
}
```

| パラメータ | 説明 |
|-----------|------|
| `crv` | 曲線（`P-256`, `P-384`, `P-521`） |
| `x` | X 座標 |
| `y` | Y 座標 |

#### 秘密鍵（追加パラメータ）

```json
{
  "kty": "EC",
  "crv": "P-256",
  "x": "...",
  "y": "...",
  "d": "jpsQnnGQmL-YBIffH1136cspYG6-0iY7X1fCE9-E9LI"
}
```

| パラメータ | 説明 |
|-----------|------|
| `d` | ECC Private Key |

### 対称鍵（kty: "oct"）

HMAC や AES で使用する対称鍵。

```json
{
  "kty": "oct",
  "kid": "hmac-key-1",
  "use": "sig",
  "alg": "HS256",
  "k": "GawgguFyGrWKav7AX4VKUg"
}
```

| パラメータ | 説明 |
|-----------|------|
| `k` | 鍵の値（Base64URL エンコード） |

**注意**: 対称鍵は秘密情報なので、公開 JWKS エンドポイントには含めない。

### OKP 鍵（kty: "OKP"）

RFC 8037 で追加。EdDSA や X25519 で使用。

```json
{
  "kty": "OKP",
  "kid": "ed25519-key-1",
  "use": "sig",
  "crv": "Ed25519",
  "x": "11qYAYKxCrfVS_7TyWQHOg7hcvPapiMlrwIaaPcHURo"
}
```

| パラメータ | 説明 |
|-----------|------|
| `crv` | 曲線（`Ed25519`, `Ed448`, `X25519`, `X448`） |
| `x` | 公開鍵 |
| `d` | 秘密鍵（秘密鍵の場合） |

### JWK Thumbprint

RFC 7638 で定義。JWK の一意な識別子を計算する方法。

```
1. 必須メンバーのみを含む JSON を構築（アルファベット順、空白なし）
2. SHA-256 ハッシュを計算
3. Base64URL エンコード

RSA の場合:
  {"e":"AQAB","kty":"RSA","n":"..."}

EC の場合:
  {"crv":"P-256","kty":"EC","x":"...","y":"..."}
```

DPoP でトークンをバインドする際に使用されます。

### 実装例

#### Java（Nimbus JOSE + JWT）

```java
// RSA 鍵ペアから JWK を生成
KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
gen.initialize(2048);
KeyPair keyPair = gen.generateKeyPair();

RSAKey jwk = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
    .privateKey((RSAPrivateKey) keyPair.getPrivate())
    .keyID("key-2024-01")
    .keyUse(KeyUse.SIGNATURE)
    .algorithm(JWSAlgorithm.RS256)
    .build();

// 公開鍵のみを取得（JWKS エンドポイント用）
RSAKey publicJwk = jwk.toPublicJWK();

// JSON 文字列に変換
String jwkJson = publicJwk.toJSONString();

// JWK Set を作成
JWKSet jwkSet = new JWKSet(publicJwk);
String jwksJson = jwkSet.toJSONObject().toString();
```

#### JavaScript（jose）

```javascript
import * as jose from 'jose';

// 鍵ペアを生成
const { publicKey, privateKey } = await jose.generateKeyPair('RS256');

// JWK にエクスポート
const publicJwk = await jose.exportJWK(publicKey);
publicJwk.kid = 'key-2024-01';
publicJwk.use = 'sig';
publicJwk.alg = 'RS256';

const privateJwk = await jose.exportJWK(privateKey);
privateJwk.kid = 'key-2024-01';
privateJwk.use = 'sig';
privateJwk.alg = 'RS256';

// JWK Set を構築
const jwks = { keys: [publicJwk] };

// JWK から鍵をインポート
const importedPublicKey = await jose.importJWK(publicJwk, 'RS256');
```

### JWKS エンドポイント

認可サーバーは公開鍵を JWKS エンドポイントで公開します。

```
GET /.well-known/jwks.json HTTP/1.1
Host: auth.example.com
```

```json
{
  "keys": [
    {
      "kty": "RSA",
      "kid": "key-2024-01",
      "use": "sig",
      "alg": "RS256",
      "n": "...",
      "e": "AQAB"
    },
    {
      "kty": "RSA",
      "kid": "key-2023-01",
      "use": "sig",
      "alg": "RS256",
      "n": "...",
      "e": "AQAB"
    }
  ]
}
```

### 鍵のローテーション

```
1. 新しい鍵を JWKS に追加
   { "keys": [new_key, old_key] }

2. 新しい鍵で署名を開始
   JWS の kid = new_key.kid

3. 古い鍵の有効期限が切れたら削除
   { "keys": [new_key] }
```

クライアントは JWT の `kid` ヘッダーを見て、JWKS から対応する鍵を取得します。

### セキュリティ考慮事項

| 項目 | 推奨事項 |
|------|----------|
| 秘密鍵の保護 | HSM、KMS で保管 |
| 公開鍵のみ公開 | 秘密鍵パラメータを含めない |
| kid の一意性 | 衝突しないように管理 |
| JWKS のキャッシュ | TTL を設定し、定期的に更新 |
| HTTPS | JWKS エンドポイントは TLS 必須 |
| 鍵のローテーション | 定期的に実施 |

---

## 参考リンク

- [RFC 7517 - JSON Web Key (JWK)](https://datatracker.ietf.org/doc/html/rfc7517)
- [RFC 7638 - JSON Web Key (JWK) Thumbprint](https://datatracker.ietf.org/doc/html/rfc7638)
- [RFC 8037 - CFRG Elliptic Curve Diffie-Hellman (ECDH) and Signatures in JOSE](https://datatracker.ietf.org/doc/html/rfc8037)
