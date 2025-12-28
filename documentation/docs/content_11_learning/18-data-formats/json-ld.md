---
sidebar_position: 5
---

# JSON-LD: Linked Data for JSON

JSON-LD は JSON にセマンティクス（意味）を付与するための W3C 標準です。Verifiable Credentials の W3C Data Model で使われています。

:::tip 前提知識
JSON-LD は [RDF（Resource Description Framework）](./rdf.md) の JSON 表現です。RDF の基本概念（トリプル、URI、グラフ）を理解していると、より深く理解できます。
:::

---

## JSON-LD とは

JSON-LD（JSON for Linking Data）は、**JSON データに「意味」を持たせるための仕様**です。

```
JSON-LD の位置づけ:

  ┌─────────────────────────────────────────────────────────────┐
  │                                                             │
  │  JSON                    JSON-LD                            │
  │                                                             │
  │  {"name": "John"}        {"@context": "...",                │
  │                           "name": "John"}                   │
  │                                                             │
  │  意味が不明確            意味が定義されている                │
  │  「name」って何？        「foaf:name」= 人名                 │
  │                                                             │
  └─────────────────────────────────────────────────────────────┘
```

### なぜ意味の定義が必要か

```
問題: 同じプロパティ名でも意味が違う

  システム A: { "name": "John" }      ← 人の名前
  システム B: { "name": "Acme Corp" } ← 会社名
  システム C: { "name": "Tokyo" }     ← 地名

  相互運用するとき:
    「name」をどう解釈すべき？
    人名？会社名？地名？

JSON-LD の解決策:
  各プロパティに一意の識別子（URI）を割り当てる

  { "@context": { "name": "http://schema.org/name" },
    "name": "John" }

  → schema.org/name = 「モノの名前」という明確な定義
```

---

## @context の役割

### 基本的な構文

```json
{
  "@context": "https://schema.org/",
  "name": "John",
  "jobTitle": "Professor"
}
```

```
@context が行うこと:

  1. 短い名前 → 完全な URI へのマッピング

     "name" → "https://schema.org/name"
     "jobTitle" → "https://schema.org/jobTitle"

  2. URI があることで、その意味を調べられる

     https://schema.org/name を見れば
     「モノの名前を表すプロパティ」と定義されている
```

### 複数のコンテキスト

```json
{
  "@context": [
    "https://www.w3.org/ns/credentials/v2",
    "https://example.gov/credentials/license"
  ],
  "type": ["VerifiableCredential", "DriversLicense"],
  "issuer": "did:example:gov",
  "licenseNumber": "DL-12345"
}
```

```
複数コンテキストのマージ:

  最初のコンテキスト:
    type → VC標準の定義
    issuer → VC標準の定義

  2番目のコンテキスト:
    licenseNumber → 運転免許固有の定義

  → 標準語彙 + ドメイン固有語彙の組み合わせ
```

---

## Verifiable Credentials での JSON-LD

### VC の @context

```json
{
  "@context": [
    "https://www.w3.org/ns/credentials/v2",
    "https://example.gov/credentials/identity"
  ],
  "type": ["VerifiableCredential", "IdentityCredential"],
  "issuer": "did:example:gov",
  "credentialSubject": {
    "id": "did:example:holder",
    "name": "山田太郎",
    "birthDate": "1990-01-15"
  }
}
```

```
VC での @context の意味:

  ┌─────────────────────────────────────────────────────────────┐
  │                                                             │
  │  "https://www.w3.org/ns/credentials/v2"                     │
  │    ├── "type" の意味を定義                                  │
  │    ├── "issuer" の意味を定義                                │
  │    ├── "credentialSubject" の意味を定義                     │
  │    └── "proof" の意味を定義                                 │
  │                                                             │
  │  "https://example.gov/credentials/identity"                 │
  │    ├── "name" の意味を定義                                  │
  │    └── "birthDate" の意味を定義                             │
  │                                                             │
  └─────────────────────────────────────────────────────────────┘
```

### なぜ VC は JSON-LD を採用したか

```
理由 1: 相互運用性

  異なる発行者が発行した VC でも
  同じ @context を使えば同じ意味を持つ

  発行者 A: { "credentialSubject": { "birthDate": "1990-01-15" } }
  発行者 B: { "credentialSubject": { "birthDate": "1985-05-20" } }

  → 両方とも「生年月日」として処理できる

理由 2: 拡張性

  標準にない属性を追加する場合
  独自のコンテキストを作成すればよい

  "@context": [
    "https://www.w3.org/ns/credentials/v2",
    "https://mycompany.example/vocab"  ← 独自定義
  ]

理由 3: 署名の安定性

  JSON-LD の正規化により
  同じ意味のデータは同じバイト列になる
  → 署名が安定する
```

---

## JSON-LD の主要キーワード

### @id（識別子）

```json
{
  "@context": "https://schema.org/",
  "@id": "https://example.com/person/12345",
  "name": "John"
}
```

```
@id の役割:

  このデータを一意に識別する URI

  「https://example.com/person/12345 という人物は
   name が John である」

  VC では issuer や credentialSubject.id がこれに相当:
    "issuer": "did:example:gov"
    "credentialSubject": { "id": "did:example:holder" }
```

### @type（型）

```json
{
  "@context": "https://schema.org/",
  "@type": "Person",
  "name": "John"
}
```

```
@type の役割:

  このデータが「何か」を示す

  VC では type 配列:
    "type": ["VerifiableCredential", "UniversityDegreeCredential"]

  注: JSON-LD では @type、VC では短縮形の type を使用
```

### @graph（グラフ）

```json
{
  "@context": "https://schema.org/",
  "@graph": [
    { "@id": "person:1", "@type": "Person", "name": "John" },
    { "@id": "person:2", "@type": "Person", "name": "Jane" }
  ]
}
```

```
@graph の役割:

  複数のノード（データ）を含むグラフ

  VC ではあまり使わないが
  Verifiable Presentation で複数 VC を含む場合に類似:

  "verifiableCredential": [
    { /* VC 1 */ },
    { /* VC 2 */ }
  ]
```

---

## JSON-LD の正規化

### なぜ正規化が必要か

```
問題: 同じ意味でも JSON の書き方は様々

  { "name": "John", "age": 30 }
  { "age": 30, "name": "John" }
  {"name":"John","age":30}

  → 意味は同じだがバイト列が違う
  → 署名すると異なるハッシュになる

解決: 正規化

  JSON-LD を RDF に変換し、一意の形式に正規化
  → 同じ意味 = 同じバイト列
  → 署名が安定
```

### 正規化アルゴリズム

```
主な正規化アルゴリズム:

  ┌─────────────────────────────────────────────────────────────┐
  │                                                             │
  │  URDNA2015                                                  │
  │    Universal RDF Dataset Normalization Algorithm 2015       │
  │    W3C 標準、VC で広く使用                                  │
  │                                                             │
  │  JCS (JSON Canonicalization Scheme)                         │
  │    RFC 8785                                                 │
  │    JSON-LD 以外でも使える                                   │
  │                                                             │
  └─────────────────────────────────────────────────────────────┘

VC Data Integrity で使われる cryptosuite:
  eddsa-rdfc-2022  → RDFC-1.0 正規化 + EdDSA 署名
  ecdsa-rdfc-2019  → RDFC-1.0 正規化 + ECDSA 署名
```

### 正規化の例

```
入力（順序が違う2つの JSON）:

  {"name": "John", "age": 30}
  {"age": 30, "name": "John"}

正規化後（同じ出力）:

  <_:b0> <http://schema.org/age> "30"^^<xsd:integer> .
  <_:b0> <http://schema.org/name> "John" .

→ 同じバイト列 → 同じハッシュ → 署名が一致
```

---

## JSON-LD と SD-JWT の違い

```
JSON-LD（W3C VC Data Model）:

  特徴:
    - セマンティクスを重視
    - 正規化による署名の安定性
    - 複雑だが相互運用性が高い

  VC 例:
    {
      "@context": ["https://www.w3.org/ns/credentials/v2"],
      "type": ["VerifiableCredential"],
      ...
    }

SD-JWT（IETF）:

  特徴:
    - 選択的開示を重視
    - JWS（JWT）ベースでシンプル
    - JSON-LD 不要

  VC 例:
    eyJhbGciOiJFUzI1NiJ9.eyJfc2RfYWxnIjoiU0...~WyJ...

どちらを使うか:
  - 相互運用性重視 → JSON-LD
  - シンプルさ・プライバシー重視 → SD-JWT
  - 実際は両方サポートする実装が多い
```

---

## @context のベストプラクティス

### 1. 必ず公式コンテキストを最初に

```json
// ✅ 正しい
{
  "@context": [
    "https://www.w3.org/ns/credentials/v2",
    "https://example.com/custom"
  ]
}

// ❌ 間違い
{
  "@context": [
    "https://example.com/custom",
    "https://www.w3.org/ns/credentials/v2"
  ]
}
```

### 2. コンテキストは信頼できるソースから

```
@context の URL は外部リソース

リスク:
  - @context のサーバーがダウン → 検証不能
  - @context が改ざんされる → 意味が変わる

対策:
  - 信頼できるドメインのみ使用
  - @context をキャッシュ
  - ハッシュでコンテキストを検証
```

### 3. 独自コンテキストの定義

```json
// 独自コンテキスト: https://example.com/license-context.jsonld
{
  "@context": {
    "@version": 1.1,
    "license": "https://example.com/vocab#",
    "licenseNumber": "license:number",
    "vehicleClass": "license:vehicleClass",
    "expiryDate": {
      "@id": "license:expiryDate",
      "@type": "xsd:date"
    }
  }
}
```

```
独自コンテキストを作る場合:

  1. 語彙（vocabulary）を定義
     https://example.com/vocab#

  2. 各プロパティを URI にマッピング
     licenseNumber → license:number

  3. 型情報を指定（オプション）
     expiryDate → xsd:date 型

  4. HTTPS で公開
```

---

## デバッグツール

### JSON-LD Playground

```
https://json-ld.org/playground/

機能:
  - JSON-LD の展開（Expansion）
  - 圧縮（Compaction）
  - 正規化（Normalization）
  - RDF への変換

使い方:
  1. JSON-LD を貼り付け
  2. 「Expand」で完全な URI を確認
  3. 「Normalize」で正規化形式を確認
```

### コマンドラインツール

```bash
# Node.js (jsonld ライブラリ)
npm install jsonld

# 展開
node -e "
const jsonld = require('jsonld');
const doc = {
  '@context': 'https://schema.org/',
  'name': 'John'
};
jsonld.expand(doc).then(console.log);
"

# Python (pyld ライブラリ)
pip install pyld

python -c "
from pyld import jsonld
doc = {
  '@context': 'https://schema.org/',
  'name': 'John'
}
print(jsonld.expand(doc))
"
```

---

## JSON-LD の注意点

### 1. パフォーマンス

```
コンテキストの取得:

  @context の URL を解決するために HTTP リクエストが必要
  → ネットワーク遅延

対策:
  - コンテキストをキャッシュ
  - 埋め込みコンテキストを使用

埋め込みコンテキスト:
  {
    "@context": {
      "name": "https://schema.org/name"
    },
    "name": "John"
  }
```

### 2. セキュリティ

```
リスク:

  1. コンテキスト改ざん
     悪意ある @context を参照させ、意味を変える

  2. コンテキスト依存攻撃
     同じ JSON でも @context によって意味が変わる

対策:

  1. 信頼できるコンテキストのみ許可
  2. コンテキストのハッシュを検証
  3. コンテキストをホワイトリスト管理
```

### 3. 複雑さ

```
JSON-LD の学習コスト:

  - @context の仕組み
  - RDF の概念
  - 正規化アルゴリズム
  - 展開・圧縮の動作

→ シンプルなユースケースには過剰な場合も

代替案:
  - SD-JWT（JSON-LD 不要）
  - mdoc（CBOR ベース）
```

---

## まとめ

```
JSON-LD の特徴:

  ┌─────────────────────────────────────────────────────────────┐
  │                                                             │
  │  何者か:                                                    │
  │    JSON にセマンティクス（意味）を付与する W3C 標準         │
  │    Linked Data の JSON 表現                                │
  │                                                             │
  │  使われる場所:                                              │
  │    - W3C Verifiable Credentials Data Model                 │
  │    - Schema.org（Google 構造化データ）                     │
  │    - ActivityPub（Mastodon 等）                            │
  │                                                             │
  │  主要な概念:                                                │
  │    @context - プロパティ名と URI のマッピング              │
  │    @id - リソースの識別子                                  │
  │    @type - リソースの型                                    │
  │                                                             │
  │  VC での役割:                                               │
  │    - 異なる発行者間の相互運用性確保                        │
  │    - 署名のための正規化                                    │
  │    - 拡張性のあるスキーマ定義                              │
  │                                                             │
  │  注意点:                                                    │
  │    - 学習コストが高い                                      │
  │    - パフォーマンス（コンテキスト取得）                    │
  │    - 必ずしも必要ではない（SD-JWT は不使用）               │
  │                                                             │
  └─────────────────────────────────────────────────────────────┘
```

---

## 参考リンク

- [JSON-LD 1.1 Specification](https://www.w3.org/TR/json-ld11/)
- [JSON-LD Playground](https://json-ld.org/playground/)
- [W3C VC Data Model 2.0](https://www.w3.org/TR/vc-data-model-2.0/)
- [RDF 1.1 Concepts](https://www.w3.org/TR/rdf11-concepts/)
