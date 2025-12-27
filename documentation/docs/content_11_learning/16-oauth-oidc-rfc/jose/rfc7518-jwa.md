# RFC 7518: JSON Web Algorithms（JWA）

RFC 7518 は、JWS、JWE、JWK で使用する暗号アルゴリズムを定義した仕様です。

---

## 第1部: 概要編

### JWA とは何か？

JWA（JSON Web Algorithms）は、JOSE（JSON Object Signing and Encryption）で使用できる**暗号アルゴリズムのカタログ**です。

```
JOSE ファミリー:
  ┌─────────────────────────────────────────────┐
  │                    JWA                       │
  │        （アルゴリズムの定義）                  │
  └─────────────────────────────────────────────┘
           ▲              ▲              ▲
           │              │              │
  ┌────────┴────────┐ ┌───┴───┐ ┌───────┴───────┐
  │      JWS        │ │  JWE  │ │      JWK      │
  │   （署名）       │ │（暗号化）│ │   （鍵表現）   │
  └─────────────────┘ └───────┘ └───────────────┘
```

### アルゴリズムの分類

| 分類 | 用途 | 例 |
|------|------|-----|
| 署名アルゴリズム | JWS の署名 | RS256, ES256, HS256 |
| 鍵暗号化アルゴリズム | JWE の CEK 暗号化 | RSA-OAEP, ECDH-ES |
| コンテンツ暗号化アルゴリズム | JWE のペイロード暗号化 | A256GCM |
| 鍵管理アルゴリズム | 鍵のラップ | A256KW |

---

## 第2部: 署名アルゴリズム

### HMAC（対称鍵署名）

| アルゴリズム | 説明 | 鍵長 |
|-------------|------|------|
| `HS256` | HMAC using SHA-256 | 256 ビット以上推奨 |
| `HS384` | HMAC using SHA-384 | 384 ビット以上推奨 |
| `HS512` | HMAC using SHA-512 | 512 ビット以上推奨 |

```
特徴:
  ✅ 高速
  ✅ 実装がシンプル
  ❌ 鍵の共有が必要（署名者と検証者が同じ鍵を持つ）
  ❌ 否認防止ができない

用途:
  - 同一システム内での署名（例: セッショントークン）
  - client_secret_jwt
```

### RSA（非対称鍵署名）

#### RSASSA-PKCS1-v1_5

| アルゴリズム | 説明 | 推奨 |
|-------------|------|------|
| `RS256` | RSASSA-PKCS1-v1_5 using SHA-256 | ✅ 広く使用 |
| `RS384` | RSASSA-PKCS1-v1_5 using SHA-384 | ✅ |
| `RS512` | RSASSA-PKCS1-v1_5 using SHA-512 | ✅ |

```
特徴:
  ✅ 広くサポートされている
  ✅ 秘密鍵で署名、公開鍵で検証
  ⚠️ PS256 より古い方式

鍵サイズ: 2048 ビット以上必須、4096 ビット推奨
```

#### RSASSA-PSS

| アルゴリズム | 説明 | 推奨 |
|-------------|------|------|
| `PS256` | RSASSA-PSS using SHA-256 | ✅ より安全 |
| `PS384` | RSASSA-PSS using SHA-384 | ✅ |
| `PS512` | RSASSA-PSS using SHA-512 | ✅ |

```
特徴:
  ✅ PKCS#1 v1.5 より安全（確率的署名）
  ✅ 証明可能なセキュリティ
  ⚠️ 古いライブラリでサポートされていない場合がある

FAPI 2.0 では PS256 または ES256 を推奨
```

### ECDSA（楕円曲線署名）

| アルゴリズム | 曲線 | 説明 | 推奨 |
|-------------|------|------|------|
| `ES256` | P-256 | ECDSA using P-256 and SHA-256 | ✅ 推奨 |
| `ES384` | P-384 | ECDSA using P-384 and SHA-384 | ✅ |
| `ES512` | P-521 | ECDSA using P-521 and SHA-512 | ✅ |

```
特徴:
  ✅ RSA より短い鍵で同等のセキュリティ
  ✅ 署名サイズが小さい
  ✅ 署名生成が高速
  ⚠️ 署名検証は RSA より遅い

比較（同等のセキュリティ）:
  RSA-2048 ≈ EC P-256
  RSA-3072 ≈ EC P-384
  RSA-15360 ≈ EC P-521
```

### EdDSA（Edwards 曲線署名）

| アルゴリズム | 曲線 | 説明 | 推奨 |
|-------------|------|------|------|
| `EdDSA` | Ed25519 | Edwards-curve Digital Signature | ✅ 最新・推奨 |
| `EdDSA` | Ed448 | Edwards-curve Digital Signature | ✅ |

```
特徴:
  ✅ 非常に高速
  ✅ 決定論的署名（同じ入力から同じ署名）
  ✅ サイドチャネル攻撃に強い
  ⚠️ 比較的新しい（RFC 8037 で追加）
```

### none（署名なし）

```
アルゴリズム: none
署名: なし

⚠️ 警告:
  - 本番環境では絶対に使用しない
  - テスト目的のみ
  - 検証側で必ず拒否する設定にする
```

### 署名アルゴリズムの選択ガイド

```
用途別の推奨:

┌─────────────────────────────────────────────────┐
│  新規実装                                        │
│  → ES256 または EdDSA                           │
├─────────────────────────────────────────────────┤
│  既存システムとの互換性                           │
│  → RS256                                        │
├─────────────────────────────────────────────────┤
│  FAPI 2.0 準拠                                   │
│  → PS256 または ES256                           │
├─────────────────────────────────────────────────┤
│  同一システム内（鍵共有可能）                     │
│  → HS256（ただし client_secret_jwt 程度）        │
└─────────────────────────────────────────────────┘
```

---

## 第3部: 鍵暗号化アルゴリズム

JWE で CEK（Content Encryption Key）を暗号化するアルゴリズム。

### RSA 系

| アルゴリズム | 説明 | 推奨 |
|-------------|------|------|
| `RSA1_5` | RSAES-PKCS1-v1_5 | ❌ 非推奨 |
| `RSA-OAEP` | RSAES OAEP using SHA-1 | ⚠️ |
| `RSA-OAEP-256` | RSAES OAEP using SHA-256 | ✅ 推奨 |

```
RSA1_5 を使用しない理由:
  - Bleichenbacher のパディングオラクル攻撃
  - RFC 8017 で新規使用は非推奨

RSA-OAEP-256 を推奨:
  - SHA-256 を使用
  - パディングオラクル攻撃に耐性
```

### AES Key Wrap

| アルゴリズム | 説明 |
|-------------|------|
| `A128KW` | AES Key Wrap with 128-bit key |
| `A192KW` | AES Key Wrap with 192-bit key |
| `A256KW` | AES Key Wrap with 256-bit key |

```
特徴:
  - 対称鍵で CEK をラップ
  - 事前共有鍵が必要
```

### AES-GCM Key Wrap

| アルゴリズム | 説明 |
|-------------|------|
| `A128GCMKW` | AES GCM Key Wrap with 128-bit key |
| `A192GCMKW` | AES GCM Key Wrap with 192-bit key |
| `A256GCMKW` | AES GCM Key Wrap with 256-bit key |

```
特徴:
  - AEAD（認証付き暗号化）
  - iv と tag が追加で必要
```

### ECDH 系

| アルゴリズム | 説明 |
|-------------|------|
| `ECDH-ES` | ECDH Ephemeral Static |
| `ECDH-ES+A128KW` | ECDH-ES with AES-128 Key Wrap |
| `ECDH-ES+A192KW` | ECDH-ES with AES-192 Key Wrap |
| `ECDH-ES+A256KW` | ECDH-ES with AES-256 Key Wrap |

```
ECDH-ES:
  - 一時的な EC 鍵ペアを生成
  - 受信者の公開鍵と ECDH で共有秘密を導出
  - 共有秘密から CEK を導出

ECDH-ES+A256KW:
  - 共有秘密から KEK（Key Encryption Key）を導出
  - KEK で CEK をラップ
```

### Direct

| アルゴリズム | 説明 |
|-------------|------|
| `dir` | Direct use of shared symmetric key |

```
特徴:
  - 事前共有鍵を CEK として直接使用
  - Encrypted Key が空になる
  - 事前に鍵を共有する必要がある
```

### パスワードベース

| アルゴリズム | 説明 |
|-------------|------|
| `PBES2-HS256+A128KW` | PBES2 with HMAC SHA-256 and A128KW |
| `PBES2-HS384+A192KW` | PBES2 with HMAC SHA-384 and A192KW |
| `PBES2-HS512+A256KW` | PBES2 with HMAC SHA-512 and A256KW |

```
特徴:
  - パスワードから鍵を導出
  - p2s（salt）と p2c（iteration count）が必要
  - ユーザー向け暗号化に使用
```

---

## 第4部: コンテンツ暗号化アルゴリズム

JWE でペイロードを暗号化するアルゴリズム。

### AES-CBC + HMAC

| アルゴリズム | 説明 | 鍵長 |
|-------------|------|------|
| `A128CBC-HS256` | AES-128-CBC + HMAC-SHA-256 | 256 ビット |
| `A192CBC-HS384` | AES-192-CBC + HMAC-SHA-384 | 384 ビット |
| `A256CBC-HS512` | AES-256-CBC + HMAC-SHA-512 | 512 ビット |

```
構成:
  - AES-CBC で暗号化
  - HMAC で認証タグを生成
  - Encrypt-then-MAC 方式

鍵の分割:
  CEK の前半 → MAC 鍵
  CEK の後半 → 暗号化鍵
```

### AES-GCM（推奨）

| アルゴリズム | 説明 | 鍵長 |
|-------------|------|------|
| `A128GCM` | AES-128-GCM | 128 ビット |
| `A192GCM` | AES-192-GCM | 192 ビット |
| `A256GCM` | AES-256-GCM | 256 ビット |

```
特徴:
  ✅ AEAD（暗号化と認証を同時に）
  ✅ 高速（ハードウェアアクセラレーション）
  ✅ IV は 96 ビット
  ⚠️ IV の再利用は厳禁
```

---

## 第5部: アルゴリズムの選択

### 署名アルゴリズム

| シナリオ | 推奨 |
|----------|------|
| 一般的な用途 | ES256 |
| 最新のセキュリティ | EdDSA (Ed25519) |
| 既存互換性重視 | RS256 |
| FAPI 準拠 | PS256 または ES256 |
| 高速性重視 | HS256（鍵共有可能な場合のみ） |

### 暗号化アルゴリズム

| シナリオ | alg | enc |
|----------|-----|-----|
| 一般的な用途 | RSA-OAEP-256 | A256GCM |
| EC ベース | ECDH-ES+A256KW | A256GCM |
| パスワード暗号化 | PBES2-HS512+A256KW | A256GCM |
| 事前共有鍵 | dir | A256GCM |

### 非推奨・禁止

| アルゴリズム | 理由 |
|-------------|------|
| `none` | 署名なし。本番禁止 |
| `RSA1_5` | パディングオラクル攻撃 |
| `HS256` + 短い鍵 | ブルートフォース可能 |

---

## 参考リンク

- [RFC 7518 - JSON Web Algorithms (JWA)](https://datatracker.ietf.org/doc/html/rfc7518)
- [RFC 8037 - CFRG Elliptic Curve Diffie-Hellman (ECDH) and Signatures in JOSE](https://datatracker.ietf.org/doc/html/rfc8037)
- [RFC 7515 - JSON Web Signature (JWS)](https://datatracker.ietf.org/doc/html/rfc7515)
- [RFC 7516 - JSON Web Encryption (JWE)](https://datatracker.ietf.org/doc/html/rfc7516)
