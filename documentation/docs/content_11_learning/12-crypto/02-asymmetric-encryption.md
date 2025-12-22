# 公開鍵暗号（非対称暗号）

## このドキュメントの目的

**公開鍵暗号（Asymmetric Encryption）** の仕組みを理解し、代表的なアルゴリズムと実際の使用例を学びます。

---

## 公開鍵暗号とは

**公開鍵暗号（非対称暗号）**:
- **2つの鍵**を使用する: 公開鍵（Public Key）と秘密鍵（Private Key）
- 公開鍵で暗号化 → 秘密鍵でのみ復号可能
- 秘密鍵で署名 → 公開鍵で検証可能
- 鍵配送問題を解決

```
鍵ペアの関係:

┌─────────────────────────────────────────────────┐
│                   鍵ペア                         │
├────────────────────┬────────────────────────────┤
│      公開鍵        │        秘密鍵               │
│   (Public Key)     │    (Private Key)           │
├────────────────────┼────────────────────────────┤
│ ・誰にでも公開可能  │ ・絶対に秘密にする         │
│ ・暗号化に使用      │ ・復号に使用              │
│ ・署名の検証に使用  │ ・署名の作成に使用         │
└────────────────────┴────────────────────────────┘

数学的な関係:
- 公開鍵から秘密鍵を導出することは計算上不可能
- 秘密鍵から公開鍵は導出可能
```

---

## 共通鍵暗号との違い

```
共通鍵暗号（対称暗号）:
[送信者] ←─── 同じ鍵 ───→ [受信者]
         どうやって安全に鍵を共有？

公開鍵暗号（非対称暗号）:
[送信者]                           [受信者]
   │                                   │
   │ ←─── 公開鍵を公開 ────────       │
   │                                   │
   │ ─── 公開鍵で暗号化 ───→          │
   │                                   │
   │                          秘密鍵で復号
   │                          （秘密鍵は自分だけが持つ）

鍵配送問題が解決！
```

| 特性 | 共通鍵暗号 | 公開鍵暗号 |
|------|-----------|-----------|
| 鍵の数 | 1つ（共通鍵） | 2つ（公開鍵・秘密鍵） |
| 処理速度 | 高速 | 低速 |
| 鍵配送 | 困難 | 容易 |
| 用途 | データ暗号化 | 鍵交換、署名 |

---

## 代表的なアルゴリズム

### RSA

**最も広く使われている公開鍵暗号**

```
特徴:
- 1977年に発明（Rivest, Shamir, Adleman）
- 素因数分解の困難性に基づく
- 鍵長: 2048ビット以上を推奨（4096ビット推奨）
- 暗号化と署名の両方に使用可能

RSAの仕組み（概念的理解）:
1. 2つの大きな素数 p, q を選ぶ
2. n = p × q を計算（公開）
3. e を選ぶ（公開指数、通常65537）
4. d を計算（秘密指数）

公開鍵: (n, e)
秘密鍵: (n, d)

暗号化: c = m^e mod n
復号:   m = c^d mod n
```

#### RSAの鍵長と安全性

| 鍵長 | 安全性 | 推奨 |
|------|--------|------|
| 1024ビット | ❌ 危険 | 使用禁止 |
| 2048ビット | △ 許容 | 2030年まで |
| 3072ビット | ⭕ 安全 | 長期使用向け |
| 4096ビット | ⭕ 最も安全 | 推奨 |

---

### 楕円曲線暗号（ECC）

**RSAより短い鍵で同等の安全性を実現**

```
特徴:
- 楕円曲線上の離散対数問題に基づく
- RSAより鍵が短く、処理が速い
- モバイル・IoTに適している

鍵長の比較（同等の安全性）:
┌─────────────┬─────────────┬─────────────┐
│ セキュリティ │     RSA     │     ECC     │
├─────────────┼─────────────┼─────────────┤
│    112bit   │   2048bit   │   224bit    │
│    128bit   │   3072bit   │   256bit    │
│    192bit   │   7680bit   │   384bit    │
│    256bit   │  15360bit   │   521bit    │
└─────────────┴─────────────┴─────────────┘

ECCはRSAの約1/10の鍵長で同等の安全性！
```

#### 主要な楕円曲線

| 曲線 | 鍵長 | 特徴 |
|------|------|------|
| P-256 (secp256r1) | 256ビット | NIST標準、最も一般的 |
| P-384 (secp384r1) | 384ビット | より高いセキュリティ |
| P-521 (secp521r1) | 521ビット | 最高レベル |
| Curve25519 | 256ビット | 高速、実装が容易 |
| secp256k1 | 256ビット | Bitcoin使用 |

---

### Ed25519

**署名専用の高速アルゴリズム**

```
特徴:
- Curve25519ベース
- 署名生成・検証が高速
- 固定サイズ（コンパクト）
- タイミング攻撃に強い

Ed25519の特性:
- 秘密鍵: 32バイト
- 公開鍵: 32バイト
- 署名:   64バイト

用途:
- SSH鍵（ssh-ed25519）
- JWT署名（EdDSA）
- TLS証明書
```

---

## 使用パターン

### パターン1: 暗号化

```
公開鍵で暗号化 → 秘密鍵で復号

[送信者]                           [受信者]
   │                                   │
   │ ←── 受信者の公開鍵を取得 ──       │
   │                                   │
   │ 平文: "秘密のメッセージ"          │
   │   ↓ 公開鍵で暗号化                │
   │ 暗号文: "X8f2kL9..."             │
   │                                   │
   │ ─── 暗号文を送信 ─────→          │
   │                                   │
   │                          暗号文: "X8f2kL9..."
   │                            ↓ 秘密鍵で復号
   │                          平文: "秘密のメッセージ"

ポイント:
- 送信者は受信者の公開鍵を使う
- 受信者だけが秘密鍵を持っている
- 受信者だけが復号できる
```

### パターン2: デジタル署名

```
秘密鍵で署名 → 公開鍵で検証

[署名者]                           [検証者]
   │                                   │
   │ ─── 公開鍵を公開 ─────→          │
   │                                   │
   │ メッセージ: "契約書"              │
   │   ↓ 秘密鍵で署名                  │
   │ 署名付きメッセージ                │
   │                                   │
   │ ─── 署名付きメッセージを送信 ──→  │
   │                                   │
   │                          署名付きメッセージ
   │                            ↓ 公開鍵で検証
   │                          ✓ 署名が正しい
   │                          ✓ 改ざんされていない
   │                          ✓ 確かに署名者からの署名

ポイント:
- 署名者は自分の秘密鍵を使う
- 誰でも公開鍵で検証できる
- 署名者しか作れない署名
```

### パターン3: 鍵交換

```
公開鍵暗号で共通鍵を安全に交換

[クライアント]                      [サーバー]
   │                                   │
   │ ←── サーバーの公開鍵 ──────      │
   │                                   │
   │ 共通鍵（AES鍵）を生成             │
   │   ↓ サーバーの公開鍵で暗号化       │
   │ 暗号化された共通鍵                 │
   │                                   │
   │ ─── 暗号化された共通鍵を送信 ──→  │
   │                                   │
   │                          秘密鍵で復号
   │                          共通鍵を取得
   │                                   │
   │ ←─── 共通鍵で暗号化した通信 ───→  │
   │                                   │

これがハイブリッド暗号の基本!
```

---

## 実装例

### Java（RSA暗号化）

```java
import java.security.*;
import javax.crypto.Cipher;
import java.util.Base64;

public class RsaExample {

    // 鍵ペア生成
    public static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(4096); // 4096ビット推奨
        return generator.generateKeyPair();
    }

    // 暗号化（公開鍵を使用）
    public static byte[] encrypt(byte[] plaintext, PublicKey publicKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        return cipher.doFinal(plaintext);
    }

    // 復号（秘密鍵を使用）
    public static byte[] decrypt(byte[] ciphertext, PrivateKey privateKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        return cipher.doFinal(ciphertext);
    }
}
```

### Java（Ed25519署名）

```java
import java.security.*;

public class Ed25519Example {

    // 鍵ペア生成
    public static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
        return generator.generateKeyPair();
    }

    // 署名（秘密鍵を使用）
    public static byte[] sign(byte[] message, PrivateKey privateKey) throws Exception {
        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(privateKey);
        signature.update(message);
        return signature.sign();
    }

    // 検証（公開鍵を使用）
    public static boolean verify(byte[] message, byte[] signatureBytes, PublicKey publicKey) throws Exception {
        Signature signature = Signature.getInstance("Ed25519");
        signature.initVerify(publicKey);
        signature.update(message);
        return signature.verify(signatureBytes);
    }
}
```

### JavaScript（Web Crypto API）

```javascript
// ECDSA鍵ペア生成
async function generateEcdsaKeyPair() {
    return await crypto.subtle.generateKey(
        { name: "ECDSA", namedCurve: "P-256" },
        true,
        ["sign", "verify"]
    );
}

// 署名
async function sign(message, privateKey) {
    const encoded = new TextEncoder().encode(message);
    return await crypto.subtle.sign(
        { name: "ECDSA", hash: "SHA-256" },
        privateKey,
        encoded
    );
}

// 検証
async function verify(message, signature, publicKey) {
    const encoded = new TextEncoder().encode(message);
    return await crypto.subtle.verify(
        { name: "ECDSA", hash: "SHA-256" },
        publicKey,
        signature,
        encoded
    );
}
```

---

## 鍵交換アルゴリズム

### Diffie-Hellman（DH）

```
2者間で共通の秘密を安全に生成

[Alice]                            [Bob]
   │                                   │
   │ 秘密値 a を生成                   │ 秘密値 b を生成
   │ A = g^a mod p を計算              │ B = g^b mod p を計算
   │                                   │
   │ ←──────── A と B を交換 ──────→   │
   │                                   │
   │ 共通鍵 = B^a mod p                │ 共通鍵 = A^b mod p
   │        = g^(ab) mod p             │        = g^(ab) mod p
   │                                   │
   │ ←─── 同じ共通鍵！ ───→            │

特徴:
- 盗聴者はA, Bを見ても共通鍵を計算できない
- 離散対数問題の困難性に基づく
```

### ECDH（楕円曲線Diffie-Hellman）

```
ECCを使ったDH（現代の推奨方式）

特徴:
- DHより短い鍵で同等の安全性
- TLS 1.3のデフォルト
- X25519（Curve25519ベース）が人気
```

---

## アイデンティティ管理での使用例

### 1. JWTの署名（JWS）

```
JWTのペイロードに署名を付ける

[認可サーバー]
   │
   │ 秘密鍵でJWTに署名
   │ eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ1c2VyMTIzIn0.署名
   │
   ↓
[クライアント/リソースサーバー]
   │
   │ JWKSエンドポイントから公開鍵を取得
   │ 公開鍵で署名を検証
   │
   ↓
 ✓ トークンは正当

署名アルゴリズム:
- RS256: RSA + SHA-256
- ES256: ECDSA + P-256 + SHA-256
- EdDSA: Ed25519
```

### 2. JWTの暗号化（JWE）

```
JWTのペイロードを暗号化

[送信者]
   │
   │ 受信者の公開鍵でコンテンツ暗号化キー(CEK)を暗号化
   │ CEKでペイロードを暗号化
   │
   ↓
[受信者]
   │
   │ 秘密鍵でCEKを復号
   │ CEKでペイロードを復号
   │
   ↓
 平文のペイロード

暗号化アルゴリズム:
- RSA-OAEP: RSA OAEP
- ECDH-ES: ECDH鍵交換
```

### 3. TLS/HTTPS

```
Webサーバーとブラウザ間の安全な通信

[ブラウザ]                          [サーバー]
   │                                   │
   │ ←─── サーバー証明書（公開鍵含む） │
   │                                   │
   │ ECDH鍵交換                        │
   │ ←────────────────────────────→    │
   │                                   │
   │ 共通鍵（セッション鍵）を導出       │
   │                                   │
   │ ←── AES-GCMで暗号化通信 ──→      │
```

### 4. SSH認証

```
公開鍵認証でSSHログイン

[クライアント]                      [サーバー]
   │                                   │
   │ ─── 公開鍵を事前に登録 ───→      │
   │                                   │
   │ ─── 接続要求 ───────────→        │
   │                                   │
   │ ←── チャレンジ ─────────         │
   │                                   │
   │ 秘密鍵でチャレンジに署名          │
   │                                   │
   │ ─── 署名を送信 ───────→          │
   │                                   │
   │                          公開鍵で署名を検証
   │                                   │
   │ ←── 認証成功 ─────────           │

鍵の種類:
- ssh-rsa（レガシー）
- ssh-ed25519（推奨）
- ecdsa-sha2-nistp256
```

---

## JWKS（JSON Web Key Set）

**公開鍵を配布する標準的な方法**

```json
{
  "keys": [
    {
      "kty": "RSA",
      "kid": "key-id-1",
      "use": "sig",
      "alg": "RS256",
      "n": "0vx7agoebGcQSuu...",
      "e": "AQAB"
    },
    {
      "kty": "EC",
      "kid": "key-id-2",
      "use": "sig",
      "alg": "ES256",
      "crv": "P-256",
      "x": "f83OJ3D2xF1Bg8v...",
      "y": "x_FEzRu9m36HLN_..."
    }
  ]
}
```

| フィールド | 説明 |
|-----------|------|
| `kty` | キータイプ（RSA, EC, OKP） |
| `kid` | キーID（識別子） |
| `use` | 用途（sig: 署名, enc: 暗号化） |
| `alg` | アルゴリズム |
| `n`, `e` | RSA公開鍵パラメータ |
| `crv`, `x`, `y` | EC公開鍵パラメータ |

---

## セキュリティの注意点

### やってはいけないこと

| ❌ 悪い例 | ⭕ 良い例 |
|----------|----------|
| RSA 1024ビット | RSA 4096ビット |
| RSA PKCS#1 v1.5パディング | RSA OAEP |
| 秘密鍵をログに出力 | 秘密鍵は絶対にログに出さない |
| 秘密鍵をGitにコミット | 秘密鍵は除外、環境変数や鍵管理システム使用 |
| 自作の暗号アルゴリズム | 標準ライブラリを使用 |

### パディングオラクル攻撃への対策

```
RSA PKCS#1 v1.5の脆弱性:

攻撃者が暗号文を改変して送信
   ↓
サーバーが「パディングエラー」か「復号成功」を返す
   ↓
この違いを利用して平文を特定

対策:
- RSA OAEP (Optimal Asymmetric Encryption Padding) を使用
- エラーメッセージを統一する
```

---

## まとめ

公開鍵暗号のポイント:

1. **2つの鍵** - 公開鍵（公開）と秘密鍵（秘密）
2. **鍵配送問題を解決** - 公開鍵は誰にでも配布可能
3. **用途** - 暗号化、署名、鍵交換
4. **アルゴリズム選択**:
   - 暗号化: RSA-OAEP または ECDH
   - 署名: Ed25519 または ECDSA
5. **秘密鍵の管理が最重要** - 漏洩したらすべて無効

次のドキュメントでは、ハッシュ関数について学びます。
