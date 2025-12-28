---
sidebar_position: 2
---

# CBOR: Concise Binary Object Representation

CBOR は IETF で標準化されたバイナリデータフォーマットです（[RFC 8949](https://www.rfc-editor.org/rfc/rfc8949.html)）。WebAuthn、mdoc、COSE など、現代のアイデンティティ技術で広く使われています。

---

## CBOR とは

CBOR（シーボー）は、**JSON のようなデータ構造をコンパクトなバイナリで表現**するフォーマットです。

```
CBOR の位置づけ:

  JSON（テキスト）
    {"name": "John", "age": 30}
    24 バイト、人間が読める

        ↓ バイナリ化

  CBOR（バイナリ）
    A2 64 6E61 6D65 64 4A6F 686E 63 6167 65 18 1E
    17 バイト、コンパクト
```

### なぜ CBOR が必要か

| 課題 | JSON | CBOR |
|------|------|------|
| サイズ | 大きい | コンパクト |
| パース速度 | 遅い | 高速 |
| バイナリデータ | Base64 必要 | ネイティブ |
| IoT/組み込み | 不向き | 最適 |

---

## CBOR の基本構造

### Major Type（主要型）

CBOR は最初の 3 ビットでデータ型を示します。

```
CBOR バイトの構造:

  ┌─────────────────────────────────────┐
  │  1バイト目                          │
  │  ┌───┬───┬───┬───┬───┬───┬───┬───┐ │
  │  │ 7 │ 6 │ 5 │ 4 │ 3 │ 2 │ 1 │ 0 │ │
  │  └───┴───┴───┴───┴───┴───┴───┴───┘ │
  │  └─────┬─────┘└─────────┬─────────┘ │
  │    Major Type      Additional Info  │
  │     (3 bits)          (5 bits)      │
  └─────────────────────────────────────┘
```

| Major Type | 値 | データ型 |
|------------|-----|---------|
| 0 | 0x00-0x1F | 正の整数（unsigned integer） |
| 1 | 0x20-0x3F | 負の整数（negative integer） |
| 2 | 0x40-0x5F | バイト列（byte string） |
| 3 | 0x60-0x7F | テキスト（text string） |
| 4 | 0x80-0x9F | 配列（array） |
| 5 | 0xA0-0xBF | マップ（map） |
| 6 | 0xC0-0xDF | タグ（tag） |
| 7 | 0xE0-0xFF | 単純値・浮動小数点 |

---

## データ型とエンコード

### 正の整数（Major Type 0）

```
0-23:     1バイトで直接表現
24-255:   0x18 + 1バイト
256-65535: 0x19 + 2バイト
...

例:
  10  → 0A              # 1バイト
  30  → 18 1E           # 0x18 = "次の1バイトが値"
  1000 → 19 03 E8       # 0x19 = "次の2バイトが値"
```

### 負の整数（Major Type 1）

```
値は -1 - n で計算（n は Additional Info の値）

例:
  -1  → 20              # -1 - 0 = -1
  -10 → 29              # -1 - 9 = -10
  -100 → 38 63          # -1 - 99 = -100
```

### テキスト文字列（Major Type 3）

```
形式: 0x60 + 長さ + UTF-8 バイト列

例: "name"
  64                    # text(4) - 0x60 + 4
     6E 61 6D 65        # "name" の UTF-8

例: "山田"
  66                    # text(6) - UTF-8で6バイト
     E5 B1 B1 E7 94 B0  # "山田" の UTF-8
```

### バイト列（Major Type 2）

```
形式: 0x40 + 長さ + バイト列

例: [0x01, 0x02, 0x03]
  43                    # bytes(3)
     01 02 03           # バイト列
```

### 配列（Major Type 4）

```
形式: 0x80 + 要素数 + 各要素

例: [1, 2, 3]
  83                    # array(3)
     01                 # 1
     02                 # 2
     03                 # 3

例: ["a", "b"]
  82                    # array(2)
     61 61              # text(1) "a"
     61 62              # text(1) "b"
```

### マップ（Major Type 5）

```
形式: 0xA0 + ペア数 + key1 + value1 + key2 + value2 + ...

例: {"name": "John", "age": 30}
  A2                    # map(2)
     64 6E616D65        # text(4) "name"
     64 4A6F686E        # text(4) "John"
     63 616765          # text(3) "age"
     18 1E              # unsigned(30)
```

### タグ（Major Type 6）

```
タグは後続のデータに意味を付与

例: タグ 0 = 日時文字列（RFC 3339）
  C0                              # tag(0)
     78 18                        # text(24)
        323032342D30312D30315430303A30303A30305A
                                  # "2024-01-01T00:00:00Z"

よく使われるタグ:
  0:  日時文字列（RFC 3339）
  1:  Unix タイムスタンプ
  2:  正の BigNum
  3:  負の BigNum
  24: CBOR データアイテム
```

### 単純値・浮動小数点（Major Type 7）

```
単純値:
  F4 = false
  F5 = true
  F6 = null
  F7 = undefined

浮動小数点:
  F9 xxxx       = half (16-bit)
  FA xxxxxxxx   = float (32-bit)
  FB xxxxxxxxxxxxxxxx = double (64-bit)
```

---

## 具体例：WebAuthn の認証器データ

WebAuthn の attestation では CBOR が使われます。

```
AuthenticatorData の構造（一部簡略化）:

  A5                              # map(5)
     01                           # key: 1 (fmt)
     66 7061636B6564              # text(6) "packed"

     02                           # key: 2 (authData)
     58 A4                        # bytes(164)
        [164 バイトの認証器データ]

     03                           # key: 3 (attStmt)
     A2                           # map(2)
        63 616C67                 # text(3) "alg"
        26                        # -7 (ES256)
        63 736967                 # text(3) "sig"
        58 47                     # bytes(71)
           [71 バイトの署名]
```

---

## 具体例：mdoc の IssuerSignedItem

```
IssuerSignedItem:

  A4                              # map(4)
     68 6469676573744944          # text(8) "digestID"
     00                           # unsigned(0)

     66 72616E646F6D              # text(6) "random"
     50                           # bytes(16)
        [16 バイトのソルト]

     71 656C656D656E744964656E74696669657
                                  # text(17) "elementIdentifier"
     6B 66616D696C795F6E616D65    # text(11) "family_name"

     6C 656C656D656E7456616C7565  # text(12) "elementValue"
     64 4A6F686E                  # text(4) "John"
```

---

## JSON との比較

### 同じデータの比較

```json
{
  "name": "John Doe",
  "age": 30,
  "email": "john@example.com",
  "active": true,
  "scores": [85, 92, 78]
}
```

| フォーマット | サイズ |
|-------------|--------|
| JSON | 99 バイト |
| JSON (minified) | 82 バイト |
| CBOR | 67 バイト |

約 18% 削減。データが大きくなるほど差が広がります。

### バイナリデータの比較

```
256 バイトのバイナリデータを含む場合:

  JSON:  256 バイト → Base64 → 344 文字 + 引用符
  CBOR:  256 バイト → そのまま 256 バイト + 3 バイトのヘッダー

  → 約 25% 削減
```

---

## CBOR の特殊機能

### 不定長エンコーディング

長さが事前にわからない場合に使用。

```
通常:
  83 01 02 03           # array(3) [1, 2, 3]

不定長:
  9F                    # array(*)
     01 02 03           # 要素
  FF                    # break（終端）
```

### 整数キーのマップ

文字列キーより効率的。COSE で多用されます。

```
文字列キー:
  A2 63 616C67 26 63 736967 ...
     "alg"    -7  "sig"

整数キー:
  A2 01 26 02 ...
     1  -7 2

→ より コンパクト
```

---

## デコードツール

### オンライン

- **cbor.me**: https://cbor.me/
  - バイナリ ↔ 診断表記の変換
  - 最もよく使われる

### コマンドライン

```bash
# Python (cbor2)
pip install cbor2
python -c "
import cbor2
data = bytes.fromhex('a2646e616d65644a6f686e63616765181e')
print(cbor2.loads(data))
"
# 出力: {'name': 'John', 'age': 30}

# Node.js (cbor)
npm install cbor
node -e "
const cbor = require('cbor');
const data = Buffer.from('a2646e616d65644a6f686e63616765181e', 'hex');
console.log(cbor.decodeFirstSync(data));
"
```

### ライブラリ

| 言語 | ライブラリ |
|------|-----------|
| Python | cbor2 |
| JavaScript | cbor, cbor-x |
| Java | jackson-dataformat-cbor |
| Go | github.com/fxamacker/cbor |
| Rust | ciborium |

---

## CBOR 診断表記

CBOR のバイナリを人間が読める形式で表現。デバッグに便利。

```
バイナリ:
  A2 64 6E616D65 64 4A6F686E 63 616765 18 1E

診断表記:
  {"name": "John", "age": 30}

より詳細な診断表記:
  A2                    # map(2)
     64                 # text(4)
        6E616D65        # "name"
     64                 # text(4)
        4A6F686E        # "John"
     63                 # text(3)
        616765          # "age"
     18 1E              # unsigned(30)
```

---

## セキュリティ考慮事項

| 項目 | 注意点 |
|------|--------|
| **再帰深度** | 深いネストは DoS の原因になりうる |
| **サイズ制限** | 不定長エンコーディングは制限を設ける |
| **型の検証** | 期待する型かどうか確認 |
| **重複キー** | マップの重複キーは禁止（RFC 8949） |

---

## まとめ

```
CBOR の特徴:

  ┌─────────────────────────────────────────────────────────────┐
  │                                                             │
  │  何者か:                                                    │
  │    JSON のようなデータ構造をバイナリで表現                   │
  │    IETF 標準（RFC 8949）                                   │
  │                                                             │
  │  使われる場所:                                              │
  │    - WebAuthn/FIDO2（認証器データ）                        │
  │    - mdoc/mDL（モバイル運転免許証）                        │
  │    - COSE（署名・暗号化）                                  │
  │    - IoT（CoAP プロトコル）                                │
  │                                                             │
  │  メリット:                                                  │
  │    - コンパクト（JSON より 20-30% 小さい）                  │
  │    - パースが高速                                          │
  │    - バイナリデータをネイティブサポート                     │
  │                                                             │
  │  デバッグ:                                                  │
  │    - cbor.me でデコード                                    │
  │    - 診断表記で確認                                        │
  │                                                             │
  └─────────────────────────────────────────────────────────────┘
```

---

## 参考リンク

- [RFC 8949 - CBOR](https://www.rfc-editor.org/rfc/rfc8949.html)
- [CBOR Playground (cbor.me)](https://cbor.me/)
- [CBOR Tags Registry](https://www.iana.org/assignments/cbor-tags/cbor-tags.xhtml)
