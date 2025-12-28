---
sidebar_position: 3
---

# COSE: CBOR Object Signing and Encryption

COSE は CBOR ベースの署名・暗号化フォーマットです（[RFC 9052](https://www.rfc-editor.org/rfc/rfc9052.html)）。JWT/JWS/JWE の CBOR 版として設計されています。

---

## COSE とは

COSE（コーズ）は、**CBOR データに署名・暗号化を行うための標準**です。

```
JOSE と COSE の関係:

  ┌─────────────────────────────────────────────────────────────┐
  │                                                             │
  │  JOSE（JSON ベース）          COSE（CBOR ベース）           │
  │                                                             │
  │  JWT  ─────────────────────►  CWT (CBOR Web Token)         │
  │  JWS  ─────────────────────►  COSE_Sign / COSE_Sign1       │
  │  JWE  ─────────────────────►  COSE_Encrypt / COSE_Encrypt0 │
  │  JWK  ─────────────────────►  COSE_Key                     │
  │                                                             │
  │  使用例:                      使用例:                       │
  │  - OAuth/OIDC                - WebAuthn/FIDO2              │
  │  - Web API                   - mdoc/mDL                    │
  │                              - IoT                          │
  │                                                             │
  └─────────────────────────────────────────────────────────────┘
```

---

## COSE の構造

### COSE メッセージタイプ

| タイプ | CBOR タグ | 説明 |
|--------|----------|------|
| COSE_Sign | 98 | 複数署名 |
| COSE_Sign1 | 18 | 単一署名（最も一般的） |
| COSE_Encrypt | 96 | 複数受信者への暗号化 |
| COSE_Encrypt0 | 16 | 単一受信者への暗号化 |
| COSE_Mac | 97 | 複数受信者への MAC |
| COSE_Mac0 | 17 | 単一受信者への MAC |

---

## COSE_Sign1（単一署名）

最もよく使われる形式。mdoc の issuerAuth などで使用。

### 構造

```
COSE_Sign1 = [
    protected,   # bstr: 保護ヘッダー（署名対象）
    unprotected, # map: 非保護ヘッダー（署名対象外）
    payload,     # bstr / nil: ペイロード
    signature    # bstr: 署名
]

CBOR タグ 18 が付与される:
  D2                              # tag(18) = COSE_Sign1
     84                           # array(4)
        ...
```

### 具体例

```
ES256 で署名された COSE_Sign1:

D2                                # tag(18) = COSE_Sign1
   84                             # array(4)

   # protected header (署名対象)
   43                             # bytes(3)
      A1 01 26                    # {1: -7}  → alg: ES256

   # unprotected header
   A0                             # {} 空のマップ

   # payload
   54                             # bytes(20)
      48656C6C6F2C20576F726C6421  # "Hello, World!"

   # signature
   58 40                          # bytes(64)
      [64 バイトの ECDSA 署名]
```

### ヘッダーパラメータ

COSE では整数キーを使用（JWS の文字列キーとは異なる）。

| 整数キー | 名前 | JWS 対応 | 説明 |
|---------|------|---------|------|
| 1 | alg | alg | アルゴリズム |
| 2 | crit | crit | クリティカル |
| 3 | content type | cty | コンテンツタイプ |
| 4 | kid | kid | 鍵 ID |
| 5 | IV | - | 初期化ベクトル |
| 6 | Partial IV | - | 部分 IV |
| 7 | counter signature | - | カウンター署名 |

### アルゴリズム識別子

| 値 | 名前 | 説明 |
|-----|------|------|
| -7 | ES256 | ECDSA w/ SHA-256 (P-256) |
| -35 | ES384 | ECDSA w/ SHA-384 (P-384) |
| -36 | ES512 | ECDSA w/ SHA-512 (P-521) |
| -37 | PS256 | RSASSA-PSS w/ SHA-256 |
| -257 | RS256 | RSASSA-PKCS1-v1_5 w/ SHA-256 |
| -8 | EdDSA | EdDSA (Ed25519, Ed448) |

---

## COSE_Key（鍵表現）

JWK に相当する鍵の表現形式。

### 構造

```
COSE_Key = {
    1: kty,      # 鍵タイプ
    2: kid,      # 鍵 ID（オプション）
    3: alg,      # アルゴリズム（オプション）
    4: key_ops,  # 鍵操作（オプション）
    ...          # 鍵タイプ固有のパラメータ
}
```

### 鍵タイプ（kty）

| 値 | 名前 | 説明 |
|-----|------|------|
| 1 | OKP | Octet Key Pair (Ed25519 など) |
| 2 | EC2 | Elliptic Curve (P-256 など) |
| 3 | RSA | RSA |
| 4 | Symmetric | 対称鍵 |

### EC2 鍵の例

```
P-256 公開鍵:

{
    1: 2,        # kty: EC2
    -1: 1,       # crv: P-256
    -2: h'...',  # x 座標 (32 bytes)
    -3: h'...'   # y 座標 (32 bytes)
}

CBOR:
A4                              # map(4)
   01 02                        # 1: 2 (EC2)
   20 01                        # -1: 1 (P-256)
   21 58 20 [32 bytes]          # -2: x座標
   22 58 20 [32 bytes]          # -3: y座標
```

### EC2 曲線（crv）

| 値 | 曲線 |
|-----|------|
| 1 | P-256 |
| 2 | P-384 |
| 3 | P-521 |
| 6 | Ed25519 |
| 7 | Ed448 |

---

## JOSE との対応関係

### JWS と COSE_Sign1

```
JWS:
{
    "alg": "ES256",
    "typ": "JWT"
}
.
{
    "sub": "user123",
    "name": "John"
}
.
[signature]

COSE_Sign1:
D2 84
   43 A1 01 26              # protected: {1: -7}  (alg: ES256)
   A1 03 6A 61... 2B6A7774  # unprotected: {3: "application/jwt"}
   ...                      # payload
   58 40 [signature]        # signature
```

### JWK と COSE_Key

```
JWK (P-256 公開鍵):
{
    "kty": "EC",
    "crv": "P-256",
    "x": "base64url...",
    "y": "base64url..."
}

COSE_Key:
{
    1: 2,       # kty: EC2
    -1: 1,      # crv: P-256
    -2: h'...', # x (raw bytes)
    -3: h'...'  # y (raw bytes)
}
```

---

## WebAuthn での COSE

WebAuthn の attestation/assertion で COSE が使われます。

### Credential Public Key

```
認証器から返される公開鍵（COSE_Key 形式）:

A5                              # map(5)
   01 02                        # 1: 2 (kty: EC2)
   03 26                        # 3: -7 (alg: ES256)
   20 01                        # -1: 1 (crv: P-256)
   21 58 20                     # -2: bytes(32)
      [32 bytes x座標]
   22 58 20                     # -3: bytes(32)
      [32 bytes y座標]
```

### Attestation Statement（packed 形式）

```
{
    "alg": -7,                  # ES256
    "sig": h'...'               # 署名
}

CBOR:
A2                              # map(2)
   63 616C67                    # "alg"
   26                           # -7
   63 736967                    # "sig"
   58 47                        # bytes(71)
      [71 bytes ECDSA signature]
```

---

## mdoc での COSE

mdoc の issuerAuth は COSE_Sign1 で署名されます。

### Mobile Security Object (MSO) の署名

```
issuerAuth = COSE_Sign1

D2                                # tag(18)
   84                             # array(4)

   # protected header
   59 01 23                       # bytes(291)
      A2                          # map(2)
         01 26                    # alg: ES256
         04 ...                   # kid: [発行者の鍵ID]

   # unprotected header
   A1                             # map(1)
      21                          # x5chain
      81 59 02 ...               # [証明書チェーン]

   # payload (MSO)
   59 01 00                       # bytes(256)
      [Mobile Security Object]

   # signature
   58 40                          # bytes(64)
      [64 bytes signature]
```

---

## 署名検証の流れ

```
COSE_Sign1 の検証:

  1. 構造のパース
     ┌────────────────────────────────────┐
     │ D2 84 [protected] [unprotected]    │
     │       [payload] [signature]        │
     └────────────────────────────────────┘

  2. protected ヘッダーからアルゴリズム取得
     {1: -7} → ES256

  3. Sig_structure の構築
     Sig_structure = [
         "Signature1",    # コンテキスト文字列
         protected,       # 保護ヘッダー
         h'',             # 外部 AAD（通常は空）
         payload          # ペイロード
     ]

  4. Sig_structure を CBOR エンコード
     → ToBeSigned バイト列

  5. 署名検証
     verify(公開鍵, ToBeSigned, signature)
```

---

## コード例

### Python (cose ライブラリ)

```python
from cose.messages import Sign1Message
from cose.keys import EC2Key
from cose.algorithms import Es256

# 署名作成
msg = Sign1Message(
    phdr={1: Es256},  # alg: ES256
    payload=b"Hello, World!"
)
msg.key = private_key
encoded = msg.encode()

# 署名検証
msg = Sign1Message.decode(encoded)
msg.key = public_key
verified = msg.verify_signature()
```

### JavaScript (cose-js)

```javascript
const cose = require('cose-js');

// 署名検証
const verifier = {
    key: {
        kty: 'EC',
        crv: 'P-256',
        x: Buffer.from(...),
        y: Buffer.from(...)
    }
};

const verified = await cose.sign.verify(coseMessage, verifier);
```

---

## デバッグツール

### CBOR デコード後の確認

```
cbor.me で COSE_Sign1 をデコード:

入力（16進数）:
D2 84 43 A1 01 26 A0 54 48656C6C6F2C20576F726C6421 58 40 ...

出力（診断表記）:
18([                              # COSE_Sign1
    h'A10126',                    # protected: {1: -7}
    {},                           # unprotected
    h'48656C6C6F2C20576F726C6421', # payload: "Hello, World!"
    h'...'                        # signature
])
```

---

## まとめ

```
COSE の特徴:

  ┌─────────────────────────────────────────────────────────────┐
  │                                                             │
  │  何者か:                                                    │
  │    CBOR ベースの署名・暗号化フォーマット                     │
  │    JOSE（JWT/JWS/JWE）の CBOR 版                           │
  │                                                             │
  │  使われる場所:                                              │
  │    - WebAuthn/FIDO2（attestation, assertion）             │
  │    - mdoc/mDL（issuerAuth）                               │
  │    - CWT（CBOR Web Token）                                │
  │                                                             │
  │  主要な構造:                                                │
  │    - COSE_Sign1: 単一署名（最も一般的）                    │
  │    - COSE_Key: 鍵の表現                                    │
  │                                                             │
  │  JOSE との違い:                                             │
  │    - 文字列キー → 整数キー（コンパクト）                    │
  │    - Base64URL → バイナリ（効率的）                        │
  │    - JSON → CBOR                                           │
  │                                                             │
  └─────────────────────────────────────────────────────────────┘
```

---

## 参考リンク

- [RFC 9052 - COSE](https://www.rfc-editor.org/rfc/rfc9052.html)
- [RFC 9053 - COSE Algorithms](https://www.rfc-editor.org/rfc/rfc9053.html)
- [RFC 8392 - CWT](https://www.rfc-editor.org/rfc/rfc8392.html)
- [COSE Algorithms Registry](https://www.iana.org/assignments/cose/cose.xhtml)
