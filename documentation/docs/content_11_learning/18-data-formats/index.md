---
sidebar_position: 0
---

# データフォーマット学習ガイド

このディレクトリには、アイデンティティ技術で使われるデータフォーマット（シリアライゼーション形式）に関する学習ドキュメントが含まれています。

---

## なぜデータフォーマットを学ぶのか？

アイデンティティ技術では、様々なデータフォーマットが使われています。

```
使用例:

  OAuth/OIDC:
    JWT → JSON (Base64URL エンコード)

  FIDO2/WebAuthn:
    認証器との通信 → CBOR
    attestation → CBOR + COSE

  mdoc (mDL):
    データ構造 → CBOR
    署名 → COSE

  X.509 証明書:
    証明書 → ASN.1/DER
```

これらを理解することで、デバッグや実装がスムーズになります。

---

## 目次

| ドキュメント | 内容 |
|-------------|------|
| [エンコーディング基礎](./encoding-basics.md) | Base64, URLエンコーディング, UTF-8, Hex |
| [時刻とタイムゾーン](./time-and-timezone.md) | Unix時間, UTC, ISO 8601, 2038年問題 |
| [UUID](./uuid.md) | UUID v4/v7, 認証での使用, DB設計 |
| [シリアライゼーション概要](./serialization-overview.md) | JSON, CBOR, ASN.1 の比較と使い分け |
| [CBOR](./cbor.md) | CBOR の構造とエンコード方式 |
| [COSE](./cose.md) | CBOR ベースの署名・暗号化 |
| [ASN.1 と DER](./asn1-der.md) | X.509 証明書で使われるフォーマット |
| [JSON-LD](./json-ld.md) | JSON に意味を付与する Linked Data 形式 |
| [RDF](./rdf.md) | JSON-LD の基礎となるデータモデル |

---

## フォーマットの分類

```
データフォーマットの系譜:

  ┌─────────────────────────────────────────────────────────────┐
  │                                                             │
  │  テキスト系（人間が読める）                                  │
  │  ├── XML                                                    │
  │  ├── JSON ← OAuth/OIDC で広く使用                          │
  │  ├── JSON-LD ← W3C VC Data Model で使用                    │
  │  └── YAML                                                   │
  │                                                             │
  │  バイナリ系（コンパクト・高速）                              │
  │  ├── ASN.1/DER ← X.509 証明書、古い暗号系                  │
  │  ├── Protocol Buffers ← Google が開発、gRPC               │
  │  ├── MessagePack ← JSON のバイナリ版                       │
  │  └── CBOR ← IETF 標準、WebAuthn/mdoc で使用               │
  │                                                             │
  └─────────────────────────────────────────────────────────────┘
```

---

## アイデンティティ技術での使用状況

| 技術 | フォーマット | 用途 |
|------|-------------|------|
| **JWT** | JSON (Base64URL) | トークン、クレーム |
| **JWK** | JSON | 公開鍵の表現 |
| **W3C VC** | JSON-LD | Verifiable Credentials（W3C Data Model） |
| **WebAuthn** | CBOR | 認証器データ、attestation |
| **CTAP** | CBOR | 認証器との通信プロトコル |
| **mdoc (mDL)** | CBOR | モバイル運転免許証 |
| **COSE** | CBOR | 署名・暗号化（CBOR版 JOSE） |
| **X.509** | ASN.1/DER | 証明書 |
| **PKCS** | ASN.1/DER | 暗号鍵、署名 |

---

## 学習パス

### 初めての方

```
1. エンコーディング基礎
   └── Base64, URLエンコーディング, UTF-8 を理解

2. 時刻とタイムゾーン
   └── Unix時間, UTC, JWTの時刻クレーム を理解

3. UUID
   └── 識別子生成、v4 vs v7 の使い分け

4. シリアライゼーション概要
   └── なぜ複数のフォーマットがあるのか理解

5. CBOR
   └── 現代のアイデンティティ技術で多用される
```

### WebAuthn/FIDO2 を扱う方

```
1. CBOR
   └── 認証器データの構造を理解

2. COSE
   └── attestation の署名を理解
```

### mdoc/mDL を扱う方

```
1. CBOR
   └── mdoc のデータ構造を理解

2. COSE
   └── issuerAuth の署名を理解
```

### 証明書を扱う方

```
1. ASN.1 と DER
   └── X.509 証明書の構造を理解
```

### W3C Verifiable Credentials を扱う方

```
1. RDF
   └── トリプル、URI、グラフの概念を理解

2. JSON-LD
   └── @context とセマンティクスを理解

3. CBOR/COSE（オプション）
   └── mdoc 形式も扱う場合
```

---

## 参考リンク

- [RFC 8949 - CBOR](https://www.rfc-editor.org/rfc/rfc8949.html)
- [RFC 9052 - COSE](https://www.rfc-editor.org/rfc/rfc9052.html)
- [RDF 1.1 Primer](https://www.w3.org/TR/rdf11-primer/)
- [JSON-LD 1.1](https://www.w3.org/TR/json-ld11/)
- [JSON-LD Playground](https://json-ld.org/playground/)
- [CBOR デコーダー (cbor.me)](https://cbor.me/)
- [ASN.1 デコーダー](https://lapo.it/asn1js/)
