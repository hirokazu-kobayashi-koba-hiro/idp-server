# デジタル署名

## このドキュメントの目的

**デジタル署名（Digital Signatures）** の仕組みを理解し、JWT署名や証明書での実践的な使い方を学びます。

---

## デジタル署名とは

**デジタル署名**:
- 電子データの**真正性**と**完全性**を証明する技術
- 紙の署名・印鑑の電子版
- 公開鍵暗号を基盤とする

```
デジタル署名が保証すること:

1. 認証（Authentication）
   → 署名者が本人であることを証明

2. 完全性（Integrity）
   → データが改ざんされていないことを証明

3. 否認防止（Non-repudiation）
   → 署名者が後から「署名していない」と言えない
```

---

## 署名と検証の流れ

```
[署名者]                           [検証者]
   │                                   │
   │ 1. メッセージを作成               │
   │ 「契約書の内容」                  │
   │                                   │
   │ 2. メッセージのハッシュを計算      │
   │ SHA-256(メッセージ) → ダイジェスト │
   │                                   │
   │ 3. ダイジェストを秘密鍵で暗号化    │
   │ 暗号化(ダイジェスト, 秘密鍵) → 署名 │
   │                                   │
   │ ─── メッセージ + 署名を送信 ───→  │
   │                                   │
   │                          4. メッセージのハッシュを計算
   │                          SHA-256(メッセージ) → ダイジェスト'
   │                                   │
   │                          5. 署名を公開鍵で復号
   │                          復号(署名, 公開鍵) → ダイジェスト
   │                                   │
   │                          6. 比較
   │                          ダイジェスト == ダイジェスト' ?
   │                                   │
   │                          ✓ 一致 → 署名は有効
   │                          ✗ 不一致 → 署名は無効
```

---

## 署名アルゴリズム

### RSA署名

```
RSA-PSS（推奨）またはRSA-PKCS#1 v1.5

特徴:
- 最も広く使われている
- 鍵長: 2048ビット以上（4096ビット推奨）
- 署名サイズ: 鍵長と同じ

アルゴリズム識別子:
- RS256: RSASSA-PKCS1-v1_5 with SHA-256
- RS384: RSASSA-PKCS1-v1_5 with SHA-384
- RS512: RSASSA-PKCS1-v1_5 with SHA-512
- PS256: RSASSA-PSS with SHA-256（推奨）
- PS384: RSASSA-PSS with SHA-384
- PS512: RSASSA-PSS with SHA-512
```

### ECDSA署名

```
楕円曲線デジタル署名アルゴリズム

特徴:
- RSAより短い鍵で同等の安全性
- 署名サイズがコンパクト
- 処理が高速

アルゴリズム識別子:
- ES256: ECDSA with P-256 and SHA-256
- ES384: ECDSA with P-384 and SHA-384
- ES512: ECDSA with P-521 and SHA-512

署名サイズの比較:
┌─────────────┬─────────────┬─────────────┐
│             │     RSA     │    ECDSA    │
├─────────────┼─────────────┼─────────────┤
│ 2048ビット相当 │  256バイト  │   64バイト   │
│ 3072ビット相当 │  384バイト  │   96バイト   │
└─────────────┴─────────────┴─────────────┘
```

### EdDSA署名

```
Ed25519 / Ed448

特徴:
- 最も新しい署名アルゴリズム
- 高速で安全
- 実装が容易（間違いにくい）
- 決定的（同じ入力は同じ署名）

アルゴリズム識別子:
- EdDSA: Ed25519 with SHA-512（推奨）

Ed25519のサイズ:
- 秘密鍵: 32バイト
- 公開鍵: 32バイト
- 署名:   64バイト

用途:
- SSH鍵（ssh-ed25519）
- JWT署名
- TLS証明書
```

### アルゴリズム選択ガイド

| 用途 | 推奨アルゴリズム | 理由 |
|------|-----------------|------|
| 新規システム | Ed25519 | 高速、安全、コンパクト |
| Web/モバイル | ES256 | 広くサポート、コンパクト |
| レガシー互換 | RS256 | 最も広くサポート |
| 高セキュリティ | PS256 or ES384 | より安全なパディング/曲線 |

---

## JWT署名（JWS）

### JWSの構造

```
JWT（JSON Web Token）は3つの部分から構成:

ヘッダー.ペイロード.署名
eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ1c2VyMTIzIn0.署名部分

┌─────────────────────────────────────────────────┐
│                    ヘッダー                       │
├─────────────────────────────────────────────────┤
│ {                                               │
│   "alg": "RS256",      ← 署名アルゴリズム        │
│   "typ": "JWT",        ← トークンタイプ          │
│   "kid": "key-id-1"    ← 鍵ID                   │
│ }                                               │
└─────────────────────────────────────────────────┘
                     ↓ Base64URL
┌─────────────────────────────────────────────────┐
│                   ペイロード                     │
├─────────────────────────────────────────────────┤
│ {                                               │
│   "iss": "https://idp.example.com",            │
│   "sub": "user123",                            │
│   "aud": "client-app",                         │
│   "exp": 1735689600,                           │
│   "iat": 1735686000                            │
│ }                                               │
└─────────────────────────────────────────────────┘
                     ↓ Base64URL
┌─────────────────────────────────────────────────┐
│                     署名                         │
├─────────────────────────────────────────────────┤
│ RSASSA-PKCS1-v1_5(                              │
│   SHA-256(                                      │
│     Base64URL(header) + "." + Base64URL(payload)│
│   ),                                            │
│   privateKey                                    │
│ )                                               │
└─────────────────────────────────────────────────┘
```

### JWT署名の検証

```
1. ヘッダーからアルゴリズムと鍵IDを取得
   alg: RS256, kid: key-id-1

2. JWKSエンドポイントから公開鍵を取得
   GET https://idp.example.com/.well-known/jwks.json
   → kid が一致する鍵を選択

3. 署名を検証
   署名対象: Base64URL(header) + "." + Base64URL(payload)
   公開鍵で署名を検証

4. クレームを検証
   - exp: 有効期限が過ぎていないか
   - iss: 期待する発行者か
   - aud: 期待する受信者か
```

### 実装例（Java）

```java
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jwt.*;
import java.security.interfaces.*;
import java.util.Date;

public class JwtSignatureExample {

    // JWT署名（RSA）
    public static String signJwt(RSAPrivateKey privateKey, String keyId)
            throws Exception {
        // ヘッダー
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
            .keyID(keyId)
            .type(JOSEObjectType.JWT)
            .build();

        // ペイロード
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .issuer("https://idp.example.com")
            .subject("user123")
            .audience("client-app")
            .expirationTime(new Date(System.currentTimeMillis() + 3600000))
            .issueTime(new Date())
            .build();

        // 署名
        SignedJWT signedJWT = new SignedJWT(header, claims);
        JWSSigner signer = new RSASSASigner(privateKey);
        signedJWT.sign(signer);

        return signedJWT.serialize();
    }

    // JWT検証（RSA）
    public static JWTClaimsSet verifyJwt(String jwt, RSAPublicKey publicKey)
            throws Exception {
        SignedJWT signedJWT = SignedJWT.parse(jwt);

        JWSVerifier verifier = new RSASSAVerifier(publicKey);
        if (!signedJWT.verify(verifier)) {
            throw new Exception("Invalid signature");
        }

        JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

        // 有効期限チェック
        if (claims.getExpirationTime().before(new Date())) {
            throw new Exception("Token expired");
        }

        return claims;
    }
}
```

### 実装例（JavaScript）

```javascript
const jose = require('jose');

// JWT署名（Ed25519）
async function signJwt(privateKey, payload) {
    const jwt = await new jose.SignJWT(payload)
        .setProtectedHeader({ alg: 'EdDSA', typ: 'JWT' })
        .setIssuedAt()
        .setExpirationTime('1h')
        .sign(privateKey);

    return jwt;
}

// JWT検証
async function verifyJwt(jwt, publicKey) {
    const { payload, protectedHeader } = await jose.jwtVerify(jwt, publicKey);
    return payload;
}

// JWKSからの公開鍵取得
async function getPublicKeyFromJwks(jwksUri, kid) {
    const JWKS = jose.createRemoteJWKSet(new URL(jwksUri));
    return JWKS;
}
```

---

## X.509証明書

### 証明書の構造

```
X.509証明書: 公開鍵 + 所有者情報 + 認証局の署名

┌─────────────────────────────────────────────────┐
│                  X.509証明書                     │
├─────────────────────────────────────────────────┤
│ バージョン: v3                                   │
│ シリアル番号: 1234567890                         │
│ 署名アルゴリズム: sha256WithRSAEncryption        │
│                                                 │
│ 発行者 (Issuer):                                │
│   CN=Example CA, O=Example Inc, C=JP            │
│                                                 │
│ 有効期間:                                        │
│   開始: 2024-01-01 00:00:00                     │
│   終了: 2025-01-01 00:00:00                     │
│                                                 │
│ 主体者 (Subject):                               │
│   CN=www.example.com, O=Example Inc, C=JP       │
│                                                 │
│ 公開鍵:                                         │
│   アルゴリズム: RSA                              │
│   鍵: (4096ビットの公開鍵)                       │
│                                                 │
│ 拡張:                                           │
│   Subject Alternative Name: www.example.com     │
│   Key Usage: Digital Signature, Key Encipherment│
│                                                 │
│ 発行者の署名:                                    │
│   (認証局の秘密鍵による署名)                     │
└─────────────────────────────────────────────────┘
```

### 証明書チェーン

```
証明書の信頼チェーン:

┌─────────────────────┐
│    ルート証明書      │  ← 自己署名、OSに事前インストール
│   (Root CA)         │
└──────────┬──────────┘
           │ 署名
           ↓
┌─────────────────────┐
│   中間証明書         │  ← ルートCAによって署名
│ (Intermediate CA)   │
└──────────┬──────────┘
           │ 署名
           ↓
┌─────────────────────┐
│   サーバー証明書     │  ← 中間CAによって署名
│ (End Entity)        │     www.example.com
└─────────────────────┘

検証の流れ:
1. サーバー証明書の署名を中間CAの公開鍵で検証
2. 中間証明書の署名をルートCAの公開鍵で検証
3. ルートCAが信頼リストにあることを確認
```

### TLSでの使用

```
TLSハンドシェイク中の証明書検証:

[ブラウザ]                          [サーバー]
   │                                   │
   │ ─── ClientHello ───────→         │
   │                                   │
   │ ←─── ServerHello ──────          │
   │ ←─── Certificate ──────          │  サーバー証明書を送信
   │ ←─── CertificateVerify ─         │  秘密鍵で署名
   │                                   │
   │ 証明書チェーンを検証              │
   │ ・有効期限                        │
   │ ・署名                            │
   │ ・ドメイン名                      │
   │ ・信頼チェーン                    │
   │                                   │
   │ ─── ClientKeyExchange ───→       │
   │                                   │
```

---

## コード署名

### アプリケーション署名

```
実行ファイルやライブラリに署名:

[開発者]
   │
   │ アプリケーションをビルド
   │   ↓
   │ コード署名証明書の秘密鍵で署名
   │   ↓
   │ 署名付きアプリケーション
   │
   ↓
[ユーザー]
   │
   │ ダウンロード
   │   ↓
   │ OSが署名を検証
   │   ↓
   │ ✓ 署名OK → インストール許可
   │ ✗ 署名NG → 警告表示

プラットフォーム別:
- Windows: Authenticode
- macOS: Apple Developer ID
- Android: APK署名
- iOS: Provisioning Profile
```

### Gitコミット署名

```
GPGまたはSSHでコミットに署名:

# GPGで署名
git commit -S -m "Signed commit"

# SSH署名（Git 2.34+）
git config gpg.format ssh
git config user.signingkey ~/.ssh/id_ed25519.pub
git commit -S -m "SSH signed commit"

GitHubでの表示:
┌───────────────────────────────────────┐
│ ✓ Verified                           │
│ This commit was signed with a        │
│ verified signature.                   │
└───────────────────────────────────────┘
```

---

## 実装上の注意点

### タイミング攻撃への対策

```java
// ❌ 悪い例: 早期リターン
public boolean verifySignature(byte[] expected, byte[] actual) {
    if (expected.length != actual.length) {
        return false;
    }
    for (int i = 0; i < expected.length; i++) {
        if (expected[i] != actual[i]) {
            return false; // タイミングが漏れる
        }
    }
    return true;
}

// ⭕ 良い例: 定数時間比較
public boolean verifySignature(byte[] expected, byte[] actual) {
    return MessageDigest.isEqual(expected, actual);
}
```

### 署名の再利用禁止

```
署名には一意性が必要:

❌ 悪い例:
- 同じデータに何度も署名（リプレイ攻撃の可能性）

⭕ 良い例:
- タイムスタンプを含める
- ノンス（一度だけ使う乱数）を含める
- 署名対象にコンテキスト情報を含める
```

### 鍵のローテーション

```
JWKSで複数の鍵を公開:

{
  "keys": [
    {
      "kid": "key-2024",     ← 現在の鍵
      "alg": "RS256",
      ...
    },
    {
      "kid": "key-2023",     ← 前の鍵（移行期間中）
      "alg": "RS256",
      ...
    }
  ]
}

ローテーション手順:
1. 新しい鍵ペアを生成
2. JWKSに新しい公開鍵を追加
3. 新しい鍵で署名を開始
4. 古い鍵で署名されたトークンが期限切れになるまで待つ
5. 古い公開鍵をJWKSから削除
```

---

## アイデンティティ管理での使用例

### 1. IDトークン署名

```
OpenID Connectのid_token:

{
  "iss": "https://idp.example.com",
  "sub": "user123",
  "aud": "client-app",
  "exp": 1735689600,
  "iat": 1735686000,
  "auth_time": 1735686000,
  "nonce": "abc123"
}

署名アルゴリズム:
- RS256（最も一般的）
- ES256（推奨）
- EdDSA（最新）

検証ポイント:
- 署名が有効か
- issが期待する発行者か
- audに自分のclient_idが含まれるか
- expが現在時刻より後か
- nonceがリクエスト時のものと一致するか
```

### 2. アクセストークン署名

```
RFC 9068 JWT Access Token:

{
  "iss": "https://idp.example.com",
  "sub": "user123",
  "client_id": "client-app",
  "scope": "read write",
  "exp": 1735689600
}

リソースサーバーでの検証:
1. JWKSエンドポイントから公開鍵を取得
2. 署名を検証
3. スコープを確認
4. 有効期限を確認
```

### 3. SAML Assertion署名

```xml
<saml:Assertion>
  <ds:Signature>
    <ds:SignedInfo>
      <ds:CanonicalizationMethod Algorithm="..."/>
      <ds:SignatureMethod Algorithm="rsa-sha256"/>
      <ds:Reference URI="#assertion">
        <ds:DigestMethod Algorithm="sha256"/>
        <ds:DigestValue>...</ds:DigestValue>
      </ds:Reference>
    </ds:SignedInfo>
    <ds:SignatureValue>...</ds:SignatureValue>
  </ds:Signature>
  <saml:Subject>...</saml:Subject>
  <saml:Conditions>...</saml:Conditions>
  <saml:AttributeStatement>...</saml:AttributeStatement>
</saml:Assertion>

XML署名の特徴:
- 正規化（Canonicalization）が必要
- 複数箇所の署名が可能
- 署名対象の参照が柔軟
```

---

## セキュリティの注意点

| ❌ 悪い例 | ⭕ 良い例 |
|----------|----------|
| HS256でクライアント間共有 | RS256/ES256で署名 |
| algヘッダーを信頼 | algをホワイトリストで検証 |
| 期限切れトークンを許容 | expを厳密に検証 |
| kid未検証 | kidが期待値か検証 |
| 署名なしJWTを許容 | alg=noneを拒否 |

### alg=none攻撃

```
攻撃手法:
1. 攻撃者がJWTのalgをnoneに変更
2. 署名部分を削除
3. サーバーが署名なしJWTを受け入れる

対策:
- alg=noneを明示的に拒否
- algをホワイトリストで検証
- 署名検証を必須に

// ⭕ 良い例
Set<String> allowedAlgorithms = Set.of("RS256", "ES256");
if (!allowedAlgorithms.contains(header.getAlgorithm())) {
    throw new Exception("Algorithm not allowed");
}
```

---

## まとめ

デジタル署名のポイント:

1. **3つの保証**: 認証、完全性、否認防止
2. **アルゴリズム選択**:
   - 新規: Ed25519 / ES256
   - レガシー互換: RS256
3. **JWT署名の検証**: alg、署名、exp、iss、audを必ず検証
4. **証明書**: 信頼チェーンを理解する
5. **鍵ローテーション**: JWKSで複数鍵をサポート

次のドキュメントでは、鍵管理と実践について学びます。
