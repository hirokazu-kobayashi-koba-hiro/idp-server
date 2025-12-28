---
sidebar_position: 4
---

# ASN.1 と DER

ASN.1 は X.509 証明書や PKCS などの暗号分野で使われるデータ記述言語です。このドキュメントでは、ASN.1 と DER エンコーディングの基礎を解説します。

---

## ASN.1 とは

ASN.1（Abstract Syntax Notation One）は、**データ構造を定義するための言語**です。

```
ASN.1 の役割:

  ┌─────────────────────────────────────────────────────────────┐
  │                                                             │
  │  ASN.1 = スキーマ定義言語（データ構造の「設計図」）          │
  │                                                             │
  │  例: X.509 証明書の定義                                     │
  │                                                             │
  │  Certificate ::= SEQUENCE {                                 │
  │      tbsCertificate       TBSCertificate,                   │
  │      signatureAlgorithm   AlgorithmIdentifier,              │
  │      signatureValue       BIT STRING                        │
  │  }                                                          │
  │                                                             │
  │  この定義に基づいて、実際のバイナリ（DER）が生成される       │
  │                                                             │
  └─────────────────────────────────────────────────────────────┘
```

### ASN.1 と JSON Schema の比較

| 観点 | ASN.1 | JSON Schema |
|------|-------|-------------|
| データ形式 | バイナリ（DER/BER） | テキスト（JSON） |
| 標準化 | ITU-T X.680-683 | IETF Draft |
| 用途 | 暗号、通信プロトコル | Web API |
| 歴史 | 1984年〜 | 2010年〜 |

---

## エンコーディング規則

ASN.1 で定義されたデータは、複数の方法でバイナリ化できます。

| 規則 | 名前 | 特徴 |
|------|------|------|
| BER | Basic Encoding Rules | 柔軟だが非一意 |
| DER | Distinguished Encoding Rules | 一意（暗号用） |
| CER | Canonical Encoding Rules | ストリーミング向け |
| PER | Packed Encoding Rules | 最もコンパクト |
| XER | XML Encoding Rules | XML 形式 |

**暗号分野では DER が標準**です（署名の一意性が必要なため）。

---

## DER の基本構造

### TLV（Tag-Length-Value）

DER は TLV 形式でエンコードされます。

```
TLV 構造:

  ┌─────────────────────────────────────────────────────────────┐
  │                                                             │
  │  Tag (1-n bytes)  │  Length (1-n bytes)  │  Value (n bytes) │
  │                                                             │
  │  何のデータか       データの長さ           実際のデータ       │
  │                                                             │
  └─────────────────────────────────────────────────────────────┘

例: INTEGER 30

  02                    # Tag: INTEGER
  01                    # Length: 1 byte
  1E                    # Value: 30
```

### タグの構造

```
タグバイトの構造:

  ┌───┬───┬───┬───┬───┬───┬───┬───┐
  │ 7 │ 6 │ 5 │ 4 │ 3 │ 2 │ 1 │ 0 │
  └───┴───┴───┴───┴───┴───┴───┴───┘
  └───┬───┘└─┬─┘└───────┬─────────┘
    Class  P/C    Tag Number

  Class:
    00 = Universal（標準型）
    01 = Application
    10 = Context-specific
    11 = Private

  P/C:
    0 = Primitive（プリミティブ）
    1 = Constructed（構造体）
```

### 主要な Universal タグ

| タグ | 16進 | 型 |
|-----|------|-----|
| 1 | 01 | BOOLEAN |
| 2 | 02 | INTEGER |
| 3 | 03 | BIT STRING |
| 4 | 04 | OCTET STRING |
| 5 | 05 | NULL |
| 6 | 06 | OBJECT IDENTIFIER |
| 12 | 0C | UTF8String |
| 16 | 30 | SEQUENCE |
| 17 | 31 | SET |
| 19 | 13 | PrintableString |
| 22 | 16 | IA5String |
| 23 | 17 | UTCTime |
| 24 | 18 | GeneralizedTime |

### Length のエンコード

```
Short Form（0-127 バイト）:
  長さをそのまま 1 バイトで表現
  例: 長さ 30 → 1E

Long Form（128 バイト以上）:
  最初のバイト = 0x80 + 長さバイト数
  続くバイト = 長さ
  例: 長さ 256 → 82 01 00

Indefinite Length（BER のみ、DER では禁止）:
  80 ... 00 00
```

---

## 具体例：INTEGER

```
正の整数 30:
  02 01 1E
  │  │  └── Value: 30
  │  └───── Length: 1
  └──────── Tag: INTEGER

正の整数 256:
  02 02 01 00
  │  │  └──── Value: 256 (0x0100)
  │  └─────── Length: 2
  └────────── Tag: INTEGER

正の整数 127:
  02 01 7F

正の整数 128（注意！）:
  02 02 00 80
  │  │  │  └── 128
  │  │  └───── 先頭に 00 を付ける（正の数を示す）
  │  └──────── Length: 2
  └─────────── Tag: INTEGER

  ※ 0x80 は負の数と解釈されるため、00 を付ける
```

---

## 具体例：SEQUENCE

```
SEQUENCE { name: "John", age: 30 }

ASN.1 定義:
  Person ::= SEQUENCE {
      name  UTF8String,
      age   INTEGER
  }

DER:
  30 0B                 # SEQUENCE, length 11
     0C 04              # UTF8String, length 4
        4A6F686E        # "John"
     02 01              # INTEGER, length 1
        1E              # 30
```

---

## 具体例：OBJECT IDENTIFIER (OID)

OID は階層的な識別子です。

```
例: sha256WithRSAEncryption = 1.2.840.113549.1.1.11

エンコード規則:
  - 最初の2つ: 40 * first + second
  - 残り: 7ビット可変長エンコード

1.2.840.113549.1.1.11:
  06 09                         # OID, length 9
     2A                         # 1*40 + 2 = 42
     86 48                      # 840
     86 F7 0D                   # 113549
     01                         # 1
     01                         # 1
     0B                         # 11
```

よく使われる OID:

| OID | 意味 |
|-----|------|
| 1.2.840.113549.1.1.1 | rsaEncryption |
| 1.2.840.113549.1.1.11 | sha256WithRSAEncryption |
| 1.2.840.10045.2.1 | ecPublicKey |
| 1.2.840.10045.3.1.7 | secp256r1 (P-256) |
| 2.5.4.3 | commonName (CN) |
| 2.5.4.6 | countryName (C) |
| 2.5.4.10 | organizationName (O) |

---

## X.509 証明書の構造

```
X.509 証明書の ASN.1 定義（簡略版）:

Certificate ::= SEQUENCE {
    tbsCertificate       TBSCertificate,
    signatureAlgorithm   AlgorithmIdentifier,
    signatureValue       BIT STRING
}

TBSCertificate ::= SEQUENCE {
    version         [0] EXPLICIT Version DEFAULT v1,
    serialNumber        CertificateSerialNumber,
    signature           AlgorithmIdentifier,
    issuer              Name,
    validity            Validity,
    subject             Name,
    subjectPublicKeyInfo SubjectPublicKeyInfo,
    ...
}
```

### 実際の証明書（DER）

```
30 82 03 A3                   # SEQUENCE (certificate)
   30 82 02 8B                # SEQUENCE (tbsCertificate)
      A0 03                   # [0] EXPLICIT (version)
         02 01 02             # INTEGER 2 (v3)
      02 10                   # INTEGER (serialNumber)
         ...
      30 0D                   # SEQUENCE (signature algorithm)
         06 09                # OID
            2A 86 48 86 F7 0D 01 01 0B  # sha256WithRSA
         05 00                # NULL
      30 ...                  # SEQUENCE (issuer)
      30 1E                   # SEQUENCE (validity)
         17 0D                # UTCTime
            ...
      30 ...                  # SEQUENCE (subject)
      30 ...                  # SEQUENCE (subjectPublicKeyInfo)
   30 0D                      # SEQUENCE (signatureAlgorithm)
      ...
   03 82 01 01                # BIT STRING (signature)
      00 ...
```

---

## PEM 形式

DER バイナリを Base64 でテキスト化した形式。

```
-----BEGIN CERTIFICATE-----
MIIDazCCAlOgAwIBAgIUZ...（Base64エンコードされたDER）
...
-----END CERTIFICATE-----

BEGIN/END の種類:
  - CERTIFICATE: 証明書
  - PRIVATE KEY: 秘密鍵（PKCS#8）
  - RSA PRIVATE KEY: RSA秘密鍵（PKCS#1）
  - PUBLIC KEY: 公開鍵
  - CERTIFICATE REQUEST: CSR
```

### PEM ↔ DER 変換

```bash
# PEM → DER
openssl x509 -in cert.pem -outform DER -out cert.der

# DER → PEM
openssl x509 -in cert.der -inform DER -outform PEM -out cert.pem
```

---

## デバッグツール

### OpenSSL

```bash
# 証明書の内容を表示
openssl x509 -in cert.pem -text -noout

# ASN.1 構造を表示
openssl asn1parse -in cert.pem

# DER ファイルを解析
openssl asn1parse -in cert.der -inform DER

# 特定オフセットから解析
openssl asn1parse -in cert.pem -strparse 19
```

### オンラインツール

- **ASN.1 JavaScript decoder**: https://lapo.it/asn1js/
  - DER/PEM をビジュアルに解析
  - 構造をツリー表示

### dumpasn1

```bash
# インストール (macOS)
brew install dumpasn1

# 使用
dumpasn1 cert.der
```

---

## よくあるエンコーディング

### RSA 公開鍵 (PKCS#1)

```
RSAPublicKey ::= SEQUENCE {
    modulus           INTEGER,  -- n
    publicExponent    INTEGER   -- e
}

30 ...                        # SEQUENCE
   02 ...                     # INTEGER (n)
      00 ...                  # modulus
   02 03                      # INTEGER (e)
      01 00 01                # 65537
```

### RSA 公開鍵 (X.509/SPKI)

```
SubjectPublicKeyInfo ::= SEQUENCE {
    algorithm         AlgorithmIdentifier,
    subjectPublicKey  BIT STRING
}

30 ...                        # SEQUENCE
   30 0D                      # SEQUENCE (algorithm)
      06 09                   # OID: rsaEncryption
         2A 86 48 86 F7 0D 01 01 01
      05 00                   # NULL
   03 ...                     # BIT STRING
      00                      # unused bits
      30 ...                  # SEQUENCE (RSAPublicKey)
         ...
```

### EC 公開鍵

```
SubjectPublicKeyInfo ::= SEQUENCE {
    algorithm         AlgorithmIdentifier,
    subjectPublicKey  BIT STRING
}

30 59                         # SEQUENCE
   30 13                      # SEQUENCE (algorithm)
      06 07                   # OID: ecPublicKey
         2A 86 48 CE 3D 02 01
      06 08                   # OID: secp256r1
         2A 86 48 CE 3D 03 01 07
   03 42                      # BIT STRING
      00                      # unused bits
      04                      # uncompressed point
      [32 bytes X]
      [32 bytes Y]
```

---

## CBOR/COSE との比較

| 観点 | ASN.1/DER | CBOR |
|------|-----------|------|
| 設計年代 | 1984年 | 2013年 |
| 複雑さ | 高い | 低い |
| スキーマ | 必須 | オプション |
| 用途 | 暗号、通信 | IoT、Web |
| デバッグ | 難しい | 比較的容易 |
| 採用領域 | X.509、PKCS | WebAuthn、mdoc |

```
なぜ両方存在するか:

  ASN.1/DER:
    - PKI（証明書）の標準
    - 歴史的に確立されたエコシステム
    - 変更が困難

  CBOR:
    - 新しい技術（WebAuthn、mdoc）で採用
    - よりシンプル
    - JSON との親和性

  実務では両方を理解する必要がある
```

---

## まとめ

```
ASN.1/DER の特徴:

  ┌─────────────────────────────────────────────────────────────┐
  │                                                             │
  │  何者か:                                                    │
  │    データ構造を定義する言語（ASN.1）と                       │
  │    そのバイナリ表現（DER）                                  │
  │                                                             │
  │  使われる場所:                                              │
  │    - X.509 証明書                                          │
  │    - PKCS（#1, #7, #8, #12）                               │
  │    - CRL、OCSP                                             │
  │                                                             │
  │  構造:                                                      │
  │    TLV（Tag-Length-Value）形式                             │
  │                                                             │
  │  デバッグ:                                                  │
  │    - openssl asn1parse                                     │
  │    - lapo.it/asn1js                                        │
  │                                                             │
  │  注意点:                                                    │
  │    - 複雑で学習コストが高い                                 │
  │    - 新規設計では CBOR が推奨されることが多い               │
  │    - ただし証明書関連では必須の知識                         │
  │                                                             │
  └─────────────────────────────────────────────────────────────┘
```

---

## 参考リンク

- [ITU-T X.680 - ASN.1](https://www.itu.int/rec/T-REC-X.680)
- [RFC 5280 - X.509 PKI](https://www.rfc-editor.org/rfc/rfc5280.html)
- [ASN.1 JavaScript decoder](https://lapo.it/asn1js/)
- [A Layman's Guide to a Subset of ASN.1, BER, and DER](https://luca.ntop.org/Teaching/Appunti/asn1.html)
