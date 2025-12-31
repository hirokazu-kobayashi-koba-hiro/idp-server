# JWE アドバンス - 暗号化アルゴリズムの詳細

## このドキュメントの目的

JWEの**2段階暗号化**の仕組みと、**対称鍵 vs 非対称鍵**の使い分けを理解することが目標です。

**前提知識**: [JWS/JWEの基礎](./01-jws-jwe-basics.md)を先に読んでください。

---

## JWEの2段階暗号化

### なぜ2段階なのか？

暗号化方式には2つの課題があります：

| 暗号方式 | 長所 | 短所 |
|---------|------|------|
| **非対称鍵暗号**（RSA等） | 公開鍵で暗号化できる（鍵共有が安全） | 計算コストが高い、大量データに不向き |
| **対称鍵暗号**（AES等） | 高速、大量データに向いている | 事前に鍵を安全に共有する必要がある |

**解決策**: 両方を組み合わせる「ハイブリッド暗号化」

1. ランダムな対称鍵（CEK: Content Encryption Key）を生成
2. CEKを非対称鍵暗号で暗号化して相手に送る → **対称鍵暗号の短所を回避**
3. 実データはCEKで暗号化する → **非対称鍵暗号の短所を回避**

JWEではこの仕組みを2段階で実現しています：

```
┌─────────────────────────────────────────────────────────────┐
│                    JWE 暗号化プロセス                         │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  【Stage 1: Key Management】                                │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  CEK (Content Encryption Key) を生成・暗号化         │   │
│  │                                                     │   │
│  │  - CEK: ランダムに生成される対称鍵                   │   │
│  │  - alg: CEKを保護するアルゴリズム                    │   │
│  │                                                     │   │
│  │  例: RSA-OAEP で CEK を暗号化                       │   │
│  │      → Encrypted Key (JWEの第2部)                   │   │
│  └─────────────────────────────────────────────────────┘   │
│                           ↓                                 │
│  【Stage 2: Content Encryption】                            │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Payload を CEK で暗号化                             │   │
│  │                                                     │   │
│  │  - enc: コンテンツ暗号化アルゴリズム                  │   │
│  │  - 常に対称鍵暗号（AES）を使用                       │   │
│  │                                                     │   │
│  │  例: A256GCM で Payload を暗号化                    │   │
│  │      → Ciphertext (JWEの第4部)                      │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**利点**:
- 大量のデータを効率よく暗号化（対称鍵暗号は高速）
- 鍵の安全な共有（公開鍵暗号で鍵を保護）

---

## JWE Header の2つのアルゴリズム

### alg と enc の役割

```json
{
  "alg": "RSA-OAEP",   // Key Management Algorithm
  "enc": "A256GCM"     // Content Encryption Algorithm
}
```

| パラメータ | 役割 | 対象 |
|-----------|------|------|
| `alg` | CEKを保護する方法 | Encrypted Key (第2部) |
| `enc` | Payloadを暗号化する方法 | Ciphertext (第4部) |

---

## Key Management Algorithm（alg）の分類

### 3つのカテゴリ

```
┌─────────────────────────────────────────────────────────────┐
│              Key Management Algorithm 分類                   │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  【1. 非対称鍵暗号 (Asymmetric)】                            │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  RSA系: RSA1_5, RSA-OAEP, RSA-OAEP-256              │   │
│  │  EC系:  ECDH-ES, ECDH-ES+A128KW, ECDH-ES+A256KW     │   │
│  │                                                     │   │
│  │  特徴:                                              │   │
│  │  - 受信者の公開鍵でCEKを暗号化                       │   │
│  │  - 送信者と受信者で事前に共有する秘密なし            │   │
│  │  - OPは受信者の公開鍵を知っている必要あり            │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  【2. 対称鍵ラッピング (Symmetric Key Wrap)】                │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  AES-KW: A128KW, A192KW, A256KW                     │   │
│  │  AES-GCM-KW: A128GCMKW, A192GCMKW, A256GCMKW        │   │
│  │                                                     │   │
│  │  特徴:                                              │   │
│  │  - 共有秘密鍵（KEK）でCEKをラッピング                │   │
│  │  - 事前に秘密を共有している必要あり                  │   │
│  │  - OIDC: client_secret から KEK を導出              │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  【3. 直接暗号化 (Direct Encryption)】                       │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  dir                                                │   │
│  │                                                     │   │
│  │  特徴:                                              │   │
│  │  - 共有秘密鍵を直接CEKとして使用                     │   │
│  │  - Encrypted Key は空                               │   │
│  │  - 最もシンプルだが柔軟性なし                        │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 対称 vs 非対称 の比較

| 項目 | 非対称鍵 (RSA, ECDH-ES) | 対称鍵 (A256KW, dir) |
|------|----------------------|---------------------|
| 事前共有 | 不要（公開鍵のみ） | 必要（秘密鍵） |
| 復号者 | 秘密鍵の所有者のみ | 共有秘密を知る全員 |
| OIDC用途 | クライアント向け暗号化 | OP向け暗号化 |
| 鍵の管理 | クライアントのJWKS必要 | client_secretから導出 |

---

## Content Encryption Algorithm（enc）

### 利用可能なアルゴリズム

| enc | アルゴリズム | CEK長 | 特徴 |
|-----|------------|------|------|
| A128CBC-HS256 | AES-CBC + HMAC-SHA-256 | 256ビット | 認証付き暗号化 |
| A192CBC-HS384 | AES-CBC + HMAC-SHA-384 | 384ビット | 認証付き暗号化 |
| A256CBC-HS512 | AES-CBC + HMAC-SHA-512 | 512ビット | 認証付き暗号化 |
| A128GCM | AES-GCM | 128ビット | AEAD |
| A192GCM | AES-GCM | 192ビット | AEAD |
| A256GCM | AES-GCM | 256ビット | AEAD（推奨） |

**AEAD (Authenticated Encryption with Associated Data)**:
- 暗号化と認証（改ざん検知）を同時に行う
- GCMは高速で広くサポートされている

---

## 鍵長の関係（重要）

### alg と enc の鍵長は独立

```
┌─────────────────────────────────────────────────────────────┐
│                    鍵長の関係                                │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  alg: A256KW (256ビット KEK)                                │
│       ↓                                                     │
│  KEK (Key Encryption Key) = 256ビット                       │
│       ↓ CEKをラッピング                                     │
│  CEK (Content Encryption Key)                               │
│       ↓                                                     │
│  enc: A256GCM → CEK = 256ビット                            │
│  enc: A128GCM → CEK = 128ビット                            │
│  enc: A256CBC-HS512 → CEK = 512ビット                      │
│                                                             │
│  ※ KEKとCEKは独立した鍵                                     │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### dir の特殊ケース

**dir (Direct Encryption)** は例外です：

```
┌─────────────────────────────────────────────────────────────┐
│                  dir の場合                                  │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  alg: dir                                                   │
│       ↓                                                     │
│  共有秘密 = CEK（直接使用）                                  │
│       ↓                                                     │
│  enc: A256GCM → 共有秘密 = 256ビット必要                    │
│  enc: A128GCM → 共有秘密 = 128ビット必要                    │
│  enc: A256CBC-HS512 → 共有秘密 = 512ビット必要              │
│                                                             │
│  ※ 共有秘密の長さは enc に依存する                          │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## OIDC での対称鍵暗号化

### client_secret からの鍵導出

OpenID Connect Core 1.0 Section 10.2:

> Symmetric Encryption: The client_secret value is used as the symmetric encryption key.

```
┌─────────────────────────────────────────────────────────────┐
│             OIDC 対称鍵暗号化の流れ                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Client 登録時:                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  client_id: "my-client"                             │   │
│  │  client_secret: "veryLongSecretKey123..."           │   │
│  │  id_token_encrypted_response_alg: "A256KW"          │   │
│  │  id_token_encrypted_response_enc: "A256GCM"         │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  ID Token 発行時（OP側）:                                    │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  1. client_secret から KEK を導出                   │   │
│  │     - UTF-8バイト列に変換                           │   │
│  │     - 必要な長さに切り詰め（A256KW → 32バイト）      │   │
│  │                                                     │   │
│  │  2. KEK で CEK をラッピング                         │   │
│  │                                                     │   │
│  │  3. CEK で ID Token を暗号化                        │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  ID Token 復号時（Client側）:                                │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  1. 同じ client_secret から KEK を導出              │   │
│  │                                                     │   │
│  │  2. KEK で Encrypted Key を復号 → CEK を取得        │   │
│  │                                                     │   │
│  │  3. CEK で Ciphertext を復号                        │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 鍵導出の実装

```java
// client_secret から KEK を導出
private SecretKey deriveSecretKey(JWEAlgorithm algorithm) {
    byte[] secretBytes = clientSecret.getBytes(StandardCharsets.UTF_8);
    int keyLength = getRequiredKeyLength(algorithm);

    // 必要な長さに切り詰め（足りない場合はゼロパディング）
    byte[] keyBytes = new byte[keyLength];
    System.arraycopy(secretBytes, 0, keyBytes, 0,
                     Math.min(secretBytes.length, keyLength));

    return new SecretKeySpec(keyBytes, "AES");
}

// アルゴリズムごとの必要鍵長
private int getRequiredKeyLength(JWEAlgorithm algorithm) {
    if (algorithm == A128KW || algorithm == A128GCMKW) {
        return 16;  // 128ビット
    } else if (algorithm == A192KW || algorithm == A192GCMKW) {
        return 24;  // 192ビット
    } else {  // A256KW, A256GCMKW, dir
        return 32;  // 256ビット
    }
}
```

---

## Nested JWE（Sign-then-Encrypt）

### 署名してから暗号化

ID Tokenを暗号化する場合、通常は**Nested JWE**を使用します：

```
┌─────────────────────────────────────────────────────────────┐
│                  Nested JWE の構造                           │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Step 1: JWS (署名)                                         │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Header.Payload.Signature                           │   │
│  │                                                     │   │
│  │  - OPの秘密鍵で署名                                 │   │
│  │  - Clientは署名を検証可能                           │   │
│  └─────────────────────────────────────────────────────┘   │
│                           ↓                                 │
│  Step 2: JWE (暗号化)                                       │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Header.EncKey.IV.Ciphertext.Tag                    │   │
│  │                                                     │   │
│  │  - JWS全体を暗号化                                  │   │
│  │  - Header に "cty": "JWT" を設定                    │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  最終形式:                                                   │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  {                                                  │   │
│  │    "alg": "A256KW",                                 │   │
│  │    "enc": "A256GCM",                                │   │
│  │    "cty": "JWT"  ← Nested を示す                    │   │
│  │  }                                                  │   │
│  │  .EncryptedKey                                      │   │
│  │  .IV                                                │   │
│  │  .Ciphertext ← 中にJWSが入っている                   │   │
│  │  .AuthTag                                           │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### なぜ Nested が必要か

| 方式 | 改ざん検知 | 秘匿性 | 発行者証明 |
|------|----------|-------|----------|
| JWS のみ | ✅ | ❌ | ✅ |
| JWE のみ | ✅ | ✅ | ❌ |
| Nested JWE | ✅ | ✅ | ✅ |

**Nested JWE**:
- JWSで発行者（OP）の署名を付与
- JWEで秘匿性を確保
- Clientは復号後に署名を検証

---

## RP-Initiated Logout での対称 vs 非対称

### id_token_hint の処理

```
┌─────────────────────────────────────────────────────────────┐
│           Logout 時の id_token_hint 処理                     │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  【非対称鍵暗号化 (RSA1_5, ECDH-ES)】                         │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  - ID Token は Client の公開鍵で暗号化               │   │
│  │  - OP は復号できない（Clientの秘密鍵がない）         │   │
│  │                                                     │   │
│  │  Logout時:                                          │   │
│  │  → Client が復号して JWS を取り出す                 │   │
│  │  → JWS を id_token_hint として送信                  │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  【対称鍵暗号化 (A256KW, dir)】                              │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  - ID Token は client_secret で暗号化               │   │
│  │  - OP は復号できる（client_secret を知っている）     │   │
│  │                                                     │   │
│  │  Logout時:                                          │   │
│  │  → JWE をそのまま id_token_hint として送信          │   │
│  │  → client_id パラメータ必須（秘密を特定するため）    │   │
│  │  → OP が復号して検証                                │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### OIDC RP-Initiated Logout 仕様より

> Note that symmetrically encrypted ID Tokens used as id_token_hint values
> require the Client Identifier to be specified by other means,
> so that the ID Tokens can be decrypted by the OP.

**対称鍵JWEの場合、client_id パラメータが必須**:
- OPはどのclient_secretを使うか知る必要がある
- JWE自体には client_id 情報がない（暗号化されている）

---

## 実装上の注意点

### 1. 鍵長の確認

```java
// client_secret が十分な長さか確認
if (clientSecret.length() < requiredKeyLength) {
    throw new JOSEException(
        "client_secret is too short for " + algorithm.getName());
}
```

### 2. dir での enc との整合性

```java
// dir の場合、enc に応じた鍵長が必要
if (algorithm.equals(JWEAlgorithm.DIR)) {
    int cekLength = getContentEncryptionKeyLength(encMethod);
    if (clientSecret.length() < cekLength) {
        throw new JOSEException(
            "client_secret too short for direct encryption with " + encMethod);
    }
}
```

### 3. Content Encryption Method ごとの CEK 長

| enc | CEK 長（バイト） |
|-----|-----------------|
| A128GCM | 16 |
| A192GCM | 24 |
| A256GCM | 32 |
| A128CBC-HS256 | 32 |
| A192CBC-HS384 | 48 |
| A256CBC-HS512 | 64 |

---

## まとめ

### 学んだこと

- JWEは**2段階暗号化**（Key Management + Content Encryption）
- `alg` はCEKの保護方法、`enc` はPayloadの暗号化方法
- **非対称鍵**: 公開鍵でCEKを暗号化（RSA, ECDH-ES）
- **対称鍵**: 共有秘密でCEKをラッピング（A256KW）
- **dir**: 共有秘密を直接CEKとして使用
- OIDCでは**client_secret**から対称鍵を導出
- **Nested JWE**: 署名 → 暗号化の順で処理
- 対称鍵JWEのLogoutには**client_id必須**

### アルゴリズム選択ガイドライン

| ユースケース | 推奨 alg | 推奨 enc |
|------------|---------|---------|
| Client向けID Token暗号化 | RSA-OAEP, ECDH-ES | A256GCM |
| OP向けRequest Object暗号化 | A256KW (対称) | A256GCM |
| 高セキュリティ要件 | ECDH-ES+A256KW | A256GCM |

---

## 関連ドキュメント

- [JWS/JWEの基礎](./jws-jwe-basics.md) - 基本概念
- [OIDC Core Section 10.2](https://openid.net/specs/openid-connect-core-1_0.html#Encryption) - 暗号化仕様
- [RFC 7516](https://datatracker.ietf.org/doc/html/rfc7516) - JWE仕様
- [RFC 7518](https://datatracker.ietf.org/doc/html/rfc7518) - JWAアルゴリズム

---

**最終更新**: 2025-12-29
**対象**: JWEの詳細を理解したい開発者
