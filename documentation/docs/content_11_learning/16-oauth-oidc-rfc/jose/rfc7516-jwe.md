# RFC 7516: JSON Web Encryption（JWE）

RFC 7516 は、JSON ベースのデータを暗号化するための仕様です。ペイロードの機密性を保護する必要がある場合に使用します。

---

## 第1部: 概要編

### JWE とは何か？

JWE（JSON Web Encryption）は、任意のデータを**暗号化**するための標準フォーマットです。JWS が「改ざん防止」なら、JWE は「盗聴防止」です。

```
JWS vs JWE:

JWS（署名）:
  ペイロードは見える（Base64URL）
  改ざんを検知できる

JWE（暗号化）:
  ペイロードは見えない（暗号化済み）
  機密性を保護できる
```

### いつ JWE を使うか？

| ユースケース | 説明 |
|-------------|------|
| 機密情報の送信 | PII、医療情報、金融データ |
| Request Object | OIDC の暗号化リクエストオブジェクト |
| ID Token の暗号化 | クライアントのみが復号可能 |
| Nested JWT | 署名後に暗号化（Sign-then-Encrypt） |

### JWE の構造

JWE Compact Serialization は 5 つのパートで構成されます。

```
eyJhbGciOiJSU0EtT0FFUCIsImVuYyI6IkEyNTZHQ00ifQ.
OKOawDo13gRp2ojaHV7LFpZcgV7T6DVZKTyKOMTYUmKoTCVJRgckCL9kiMT03JGe
ipsEdY3mx_etLbbWSrFr05kLzcSr4qKAq7YN7e9jwQRb23nfa6c9d-StnImGyFDb
Sv04uVuxIp5Zms1gNxKKK2Da14B8S4rzVRltdYwam_lDp5XnZAYpQdb76FdIKLaV
mqgfwX7XWRxv2322i-vDxRfqNzo_tETKzpVLzfiwQyeyPGLBIO56YJ7eObdv0je8
1860ppamavo35UgoRdbYaBcoh9QcfylQr66oc6vFWXRcZ_ZT2LawVCWTIy3brGPi
6UklfCpIMfIjf7iGdXKHzg.
48V1_ALb6US04U3b.
5eym8TW_c8SuK0ltJ3rpYIzOeDQz7TALvtu6UG9oMo4vpzs9tX_EFShS8iB7j6ji
SdiwkIr3ajwQzaBtQD_A.
XFBoMYUZodetZdvTiFvSkQ
└────────┬────────┘.└──────────────────┬───────────────────┘.└────┬────┘.└────────────────────┬────────────────────┘.└────────┬────────┘
     Header              Encrypted Key                    IV              Ciphertext                 Tag
```

| パート | 説明 |
|--------|------|
| Header | アルゴリズム情報（JOSE Header） |
| Encrypted Key | コンテンツ暗号化鍵（CEK）の暗号化版 |
| IV | 初期化ベクトル |
| Ciphertext | 暗号化されたペイロード |
| Authentication Tag | 認証タグ（完全性保証） |

---

## 第2部: 詳細編

### 暗号化の仕組み

JWE は 2 段階の暗号化を使用します。

```
1. コンテンツ暗号化（CEK でペイロードを暗号化）
   ┌─────────────┐     ┌─────────────┐
   │  Plaintext  │ ──► │  Ciphertext │
   └─────────────┘     └─────────────┘
         │                    ▲
         │                    │
         └── CEK (Content Encryption Key)

2. 鍵暗号化（受信者の公開鍵で CEK を暗号化）
   ┌─────────────┐     ┌─────────────────┐
   │    CEK      │ ──► │  Encrypted CEK  │
   └─────────────┘     └─────────────────┘
         │                    ▲
         │                    │
         └── 受信者の公開鍵
```

この方式を「ハイブリッド暗号化」と呼びます。

### JOSE Header

```json
{
  "alg": "RSA-OAEP",
  "enc": "A256GCM",
  "kid": "key-2024-01"
}
```

| パラメータ | 必須 | 説明 |
|-----------|------|------|
| `alg` | ✅ | 鍵暗号化アルゴリズム |
| `enc` | ✅ | コンテンツ暗号化アルゴリズム |
| `zip` | △ | 圧縮アルゴリズム（`DEF` = DEFLATE） |
| `kid` | △ | 鍵 ID |
| `jku` | △ | JWK Set URL |
| `jwk` | △ | 公開鍵 |
| `typ` | △ | メディアタイプ |
| `cty` | △ | コンテンツタイプ |

### 鍵暗号化アルゴリズム（alg）

#### RSA 系

| アルゴリズム | 説明 | 推奨 |
|-------------|------|------|
| `RSA1_5` | RSAES-PKCS1-v1_5 | ❌ 非推奨（パディング攻撃） |
| `RSA-OAEP` | RSAES OAEP using SHA-1 | ⚠️ |
| `RSA-OAEP-256` | RSAES OAEP using SHA-256 | ✅ 推奨 |

#### AES Key Wrap 系

| アルゴリズム | 説明 |
|-------------|------|
| `A128KW` | AES Key Wrap with 128-bit key |
| `A192KW` | AES Key Wrap with 192-bit key |
| `A256KW` | AES Key Wrap with 256-bit key |

#### AES-GCM Key Wrap 系

| アルゴリズム | 説明 |
|-------------|------|
| `A128GCMKW` | AES GCM Key Wrap with 128-bit key |
| `A192GCMKW` | AES GCM Key Wrap with 192-bit key |
| `A256GCMKW` | AES GCM Key Wrap with 256-bit key |

#### 直接暗号化

| アルゴリズム | 説明 |
|-------------|------|
| `dir` | Direct use of shared symmetric key |

CEK を暗号化せず、共有鍵を直接使用。

#### ECDH 系

| アルゴリズム | 説明 |
|-------------|------|
| `ECDH-ES` | ECDH Ephemeral Static |
| `ECDH-ES+A128KW` | ECDH-ES with AES-128 Key Wrap |
| `ECDH-ES+A192KW` | ECDH-ES with AES-192 Key Wrap |
| `ECDH-ES+A256KW` | ECDH-ES with AES-256 Key Wrap |

#### パスワードベース

| アルゴリズム | 説明 |
|-------------|------|
| `PBES2-HS256+A128KW` | PBES2 with HMAC SHA-256 and A128KW |
| `PBES2-HS384+A192KW` | PBES2 with HMAC SHA-384 and A192KW |
| `PBES2-HS512+A256KW` | PBES2 with HMAC SHA-512 and A256KW |

### コンテンツ暗号化アルゴリズム（enc）

#### AES-CBC + HMAC

| アルゴリズム | 説明 |
|-------------|------|
| `A128CBC-HS256` | AES-128-CBC + HMAC-SHA-256 |
| `A192CBC-HS384` | AES-192-CBC + HMAC-SHA-384 |
| `A256CBC-HS512` | AES-256-CBC + HMAC-SHA-512 |

#### AES-GCM（推奨）

| アルゴリズム | 説明 | 推奨 |
|-------------|------|------|
| `A128GCM` | AES-128-GCM | ✅ |
| `A192GCM` | AES-192-GCM | ✅ |
| `A256GCM` | AES-256-GCM | ✅ 推奨 |

AES-GCM は暗号化と認証を同時に行う AEAD（Authenticated Encryption with Associated Data）です。

### 暗号化の手順

```
1. CEK（Content Encryption Key）を生成
   CEK = random(256 bits)  // enc が A256GCM の場合

2. CEK を受信者の公開鍵で暗号化
   Encrypted_CEK = RSA-OAEP(CEK, recipient_public_key)

3. IV（初期化ベクトル）を生成
   IV = random(96 bits)  // AES-GCM の場合

4. AAD（Additional Authenticated Data）を構築
   AAD = ASCII(BASE64URL(Header))

5. ペイロードを暗号化
   (Ciphertext, Tag) = AES-GCM-Encrypt(
     Plaintext,
     CEK,
     IV,
     AAD
   )

6. JWE Compact Serialization を構築
   Header.Encrypted_CEK.IV.Ciphertext.Tag
```

### 復号の手順

```
1. JWE をパース
   5 つのパートに分割

2. Header をデコードしてアルゴリズムを確認
   alg, enc を検証

3. CEK を復号
   CEK = RSA-OAEP-Decrypt(Encrypted_CEK, recipient_private_key)

4. AAD を再構築
   AAD = ASCII(BASE64URL(Header))

5. ペイロードを復号・検証
   Plaintext = AES-GCM-Decrypt(
     Ciphertext,
     CEK,
     IV,
     AAD,
     Tag
   )
```


### Nested JWT（Sign-then-Encrypt）

署名付き JWT を暗号化する場合。

```
1. JWT を作成・署名
   signed_jwt = JWS(claims, sender_private_key)

2. 署名済み JWT を暗号化
   jwe = JWE(signed_jwt, recipient_public_key)

受信者:
1. JWE を復号
   signed_jwt = Decrypt(jwe, recipient_private_key)

2. JWT の署名を検証
   claims = Verify(signed_jwt, sender_public_key)
```

Nested JWT の場合、外側の JWE ヘッダーに `cty: "JWT"` を設定します。

```json
{
  "alg": "RSA-OAEP-256",
  "enc": "A256GCM",
  "cty": "JWT"
}
```

### JSON Serialization

複数の受信者に対して暗号化する場合。

```json
{
  "protected": "eyJlbmMiOiJBMjU2R0NNIn0",
  "iv": "48V1_ALb6US04U3b",
  "ciphertext": "5eym8TW_c8SuK0ltJ3rpYIzOeDQz7TALvtu6UG9oMo4vpzs9tX...",
  "tag": "XFBoMYUZodetZdvTiFvSkQ",
  "recipients": [
    {
      "header": {
        "alg": "RSA-OAEP-256",
        "kid": "recipient-1"
      },
      "encrypted_key": "OKOawDo13gRp2ojaHV7LFpZcgV7T6DVZKTyKOMTYUmKoTCV..."
    },
    {
      "header": {
        "alg": "RSA-OAEP-256",
        "kid": "recipient-2"
      },
      "encrypted_key": "a]G9HnWIgjXJe7UpEZ_R-4q..."
    }
  ]
}
```

### セキュリティ考慮事項

| 項目 | 推奨事項 |
|------|----------|
| `RSA1_5` | 使用禁止（パディングオラクル攻撃） |
| 鍵サイズ | RSA は 2048 ビット以上、AES は 256 ビット |
| AES-GCM | 推奨（AEAD） |
| IV の再利用 | 絶対に禁止 |
| 圧縮 | CRIME 攻撃に注意（機密データでは避ける） |

---

## 参考リンク

- [RFC 7516 - JSON Web Encryption (JWE)](https://datatracker.ietf.org/doc/html/rfc7516)
- [RFC 7518 - JSON Web Algorithms (JWA)](https://datatracker.ietf.org/doc/html/rfc7518)
- [RFC 7517 - JSON Web Key (JWK)](https://datatracker.ietf.org/doc/html/rfc7517)
