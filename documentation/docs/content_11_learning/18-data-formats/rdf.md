---
sidebar_position: 6
---

# RDF: Resource Description Framework

RDF は「モノとモノの関係」を記述するための W3C 標準です。JSON-LD の基礎となる概念であり、Verifiable Credentials の正規化にも使われています。

---

## RDF とは

RDF（Resource Description Framework）は、**「何が」「どうである」「何と」という関係を記述するためのデータモデル**です。

```
RDF の基本アイデア:

  ┌─────────────────────────────────────────────────────────────┐
  │                                                             │
  │  世界のすべてを「トリプル」で表現する                        │
  │                                                             │
  │  トリプル = 主語 + 述語 + 目的語                            │
  │           Subject + Predicate + Object                      │
  │                                                             │
  │  例:                                                        │
  │    「山田太郎」は「名前を持つ」「太郎」                      │
  │    「山田太郎」は「年齢である」「30」                        │
  │    「山田太郎」は「勤務先である」「株式会社Example」          │
  │                                                             │
  └─────────────────────────────────────────────────────────────┘
```

### なぜ RDF が必要か

```
問題: データベースごとに構造が違う

  システム A:
    users テーブル
    ├── name: "山田太郎"
    └── company_id: 123

  システム B:
    persons テーブル
    ├── full_name: "山田太郎"
    └── employer: "Example Inc."

  → 統合するには変換が必要
  → スキーマが違うと相互運用が困難

RDF の解決策:

  すべてを「トリプル」という共通形式で表現

  <person:yamada> <schema:name> "山田太郎" .
  <person:yamada> <schema:worksFor> <company:example> .

  → どのシステムでも同じ形式
  → URI で意味が明確
```

---

## トリプルとグラフ

### トリプルの構造

```
トリプル（Triple）:

  ┌──────────┐    ┌──────────┐    ┌──────────┐
  │  主語    │───►│  述語    │───►│  目的語  │
  │ Subject  │    │ Predicate│    │  Object  │
  └──────────┘    └──────────┘    └──────────┘
       │               │               │
       ▼               ▼               ▼
     URI             URI           URI または
   (リソース)      (プロパティ)     リテラル値


例: 「山田太郎の年齢は30歳」

  主語:    <https://example.com/person/yamada>
  述語:    <http://schema.org/age>
  目的語:  "30"^^<xsd:integer>

  N-Triples 表記:
  <https://example.com/person/yamada> <http://schema.org/age> "30"^^<xsd:integer> .
```

### グラフの形成

```
複数のトリプルでグラフを形成:

  トリプル 1: 山田太郎 → 名前 → "太郎"
  トリプル 2: 山田太郎 → 年齢 → 30
  トリプル 3: 山田太郎 → 勤務先 → Example社
  トリプル 4: Example社 → 名前 → "株式会社Example"
  トリプル 5: Example社 → 所在地 → 東京

  グラフ表現:

                    "太郎"
                       ▲
                       │ name
                       │
  "株式会社Example" ◄──┼── Example社 ◄─── 山田太郎 ──► 30
         │             │                      │        age
         │ name        │ location             │
         ▼             ▼                      │
                    "東京"                    ▼
                                          勤務先
```

---

## URI の役割

### なぜ URI を使うか

```
問題: 「name」の意味が曖昧

  { "name": "John" }

  → 人の名前？会社名？製品名？

解決: URI で一意に識別

  <http://schema.org/name>        → schema.org で定義された「名前」
  <http://xmlns.com/foaf/0.1/name> → FOAF で定義された「名前」
  <http://example.com/vocab#name>  → 独自定義の「名前」

  URI を見れば定義を確認できる
```

### 名前空間（Namespace）

```
URI は長いので、プレフィックスで短縮:

  完全な URI:
    <http://schema.org/name>
    <http://schema.org/age>
    <http://schema.org/worksFor>

  プレフィックス定義:
    @prefix schema: <http://schema.org/> .

  短縮形:
    schema:name
    schema:age
    schema:worksFor

よく使われる名前空間:

  rdf:   → http://www.w3.org/1999/02/22-rdf-syntax-ns#
  rdfs:  → http://www.w3.org/2000/01/rdf-schema#
  xsd:   → http://www.w3.org/2001/XMLSchema#
  schema: → http://schema.org/
  foaf:  → http://xmlns.com/foaf/0.1/
```

---

## RDF のシリアライゼーション形式

RDF は抽象的なデータモデルであり、複数の形式で表現できます。

### 主な形式

| 形式 | 特徴 | 用途 |
|------|------|------|
| N-Triples | 最もシンプル、1行1トリプル | デバッグ、処理 |
| Turtle | 人間が読みやすい | 手書き、ドキュメント |
| RDF/XML | XML 形式 | 歴史的、レガシー |
| JSON-LD | JSON 形式 | Web API、VC |
| N-Quads | N-Triples + グラフ名 | 複数グラフ |

### N-Triples

```
最もシンプルな形式（1行1トリプル）:

<https://example.com/person/yamada> <http://schema.org/name> "山田太郎" .
<https://example.com/person/yamada> <http://schema.org/age> "30"^^<http://www.w3.org/2001/XMLSchema#integer> .
<https://example.com/person/yamada> <http://schema.org/worksFor> <https://example.com/company/abc> .

特徴:
  - 完全な URI を使用
  - 1行に1つのトリプル
  - ピリオドで終了
  - 正規化に適している
```

### Turtle

```
人間が読みやすい形式:

@prefix schema: <http://schema.org/> .
@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
@prefix ex: <https://example.com/> .

ex:person/yamada
    schema:name "山田太郎" ;
    schema:age "30"^^xsd:integer ;
    schema:worksFor ex:company/abc .

特徴:
  - プレフィックスで URI を短縮
  - セミコロンで同じ主語を継続
  - より読みやすい
```

### JSON-LD

```json
{
  "@context": {
    "schema": "http://schema.org/",
    "name": "schema:name",
    "age": "schema:age",
    "worksFor": { "@id": "schema:worksFor", "@type": "@id" }
  },
  "@id": "https://example.com/person/yamada",
  "name": "山田太郎",
  "age": 30,
  "worksFor": "https://example.com/company/abc"
}
```

```
JSON-LD の特徴:
  - JSON 形式で RDF を表現
  - Web 開発者に馴染みやすい
  - @context で URI マッピングを定義
  - Verifiable Credentials で採用
```

---

## RDF と JSON-LD の関係

```
JSON-LD は RDF のシリアライゼーション形式:

  ┌─────────────────────────────────────────────────────────────┐
  │                                                             │
  │  RDF（抽象モデル）                                          │
  │    │                                                        │
  │    ├── N-Triples（テキスト形式）                            │
  │    ├── Turtle（テキスト形式）                               │
  │    ├── RDF/XML（XML 形式）                                  │
  │    └── JSON-LD（JSON 形式）  ← Web で使いやすい            │
  │                                                             │
  └─────────────────────────────────────────────────────────────┘

JSON-LD の利点:
  - JSON なので Web API と親和性が高い
  - 既存の JSON データに @context を追加するだけで RDF 化
  - JavaScript で直接処理可能
```

### JSON-LD から RDF への変換

```
JSON-LD:
{
  "@context": "http://schema.org/",
  "@id": "https://example.com/person/1",
  "name": "John"
}

    ↓ 展開（Expansion）

RDF トリプル:
<https://example.com/person/1> <http://schema.org/name> "John" .
```

---

## Verifiable Credentials での RDF

### なぜ VC は RDF/JSON-LD を使うか

```
理由 1: 相互運用性

  異なる発行者が異なるスキーマを使っても
  RDF に変換すれば統一的に処理できる

  発行者 A: { "fullName": "山田太郎" }
  発行者 B: { "name": "山田太郎" }

  両方が schema:name にマッピングされていれば
  → 同じ意味として処理可能

理由 2: 署名の正規化

  JSON の書き方は様々（プロパティ順序など）
  RDF に変換して正規化すれば一意の形式になる
  → 署名が安定する

理由 3: 拡張性

  新しい属性を追加する場合
  独自の名前空間を定義すればよい
  既存のシステムとの互換性を保てる
```

### VC の正規化プロセス

```
VC Data Integrity での署名:

  1. VC（JSON-LD）
     ↓
  2. JSON-LD を RDF トリプルに変換
     ↓
  3. RDFC-1.0 で正規化
     ↓
  4. 正規化されたトリプルをハッシュ
     ↓
  5. ハッシュに署名

正規化により:
  { "name": "John", "age": 30 }
  { "age": 30, "name": "John" }

  → 両方とも同じ正規化結果
  → 同じハッシュ
  → 同じ署名で検証可能
```

---

## 正規化アルゴリズム

### RDFC-1.0（RDF Dataset Canonicalization）

```
正規化の目的:

  同じ意味の RDF グラフを
  同じバイト列に変換する

手順（概要）:

  1. すべてのトリプルを N-Quads 形式に変換

  2. 空白ノード（無名ノード）に一意の識別子を付与
     _:b0, _:b1, _:b2, ...

  3. トリプルを辞書順でソート

  4. 結果を連結

例:

  入力（順序バラバラ）:
    <ex:b> <schema:name> "Bob" .
    <ex:a> <schema:name> "Alice" .

  正規化後（ソート済み）:
    <ex:a> <schema:name> "Alice" .
    <ex:b> <schema:name> "Bob" .
```

### 空白ノードの処理

```
空白ノード（Blank Node）とは:

  URI を持たない匿名のノード

  JSON-LD:
  {
    "name": "John",
    "address": {           ← この address オブジェクトは URI がない
      "city": "Tokyo",
      "country": "Japan"
    }
  }

  RDF:
  <person:john> <schema:name> "John" .
  <person:john> <schema:address> _:b0 .   ← _:b0 = 空白ノード
  _:b0 <schema:city> "Tokyo" .
  _:b0 <schema:country> "Japan" .

正規化での問題:

  空白ノードの識別子（_:b0）は一時的なもの
  異なる処理系で異なる識別子になる可能性

  → RDFC-1.0 は空白ノードに一意の識別子を付与するアルゴリズムを含む
```

---

## RDF の語彙（Vocabulary）

### よく使われる語彙

| 語彙 | 用途 | 例 |
|------|------|-----|
| Schema.org | 汎用（Google 推奨） | schema:name, schema:Person |
| FOAF | 人物関係 | foaf:name, foaf:knows |
| Dublin Core | メタデータ | dc:title, dc:creator |
| SKOS | 分類・シソーラス | skos:Concept, skos:broader |
| VC | Verifiable Credentials | cred:issuer, cred:credentialSubject |

### Schema.org の例

```
Schema.org は Google が推進する汎用語彙:

{
  "@context": "https://schema.org/",
  "@type": "Person",
  "name": "山田太郎",
  "jobTitle": "エンジニア",
  "worksFor": {
    "@type": "Organization",
    "name": "株式会社Example"
  }
}

よく使われるプロパティ:
  schema:name        → 名前
  schema:description → 説明
  schema:url         → URL
  schema:image       → 画像
  schema:Person      → 人物（型）
  schema:Organization → 組織（型）
```

---

## RDF のクエリ言語: SPARQL

```
SPARQL は RDF データを検索するクエリ言語:

例: 30歳以上の人を検索

  SELECT ?person ?name
  WHERE {
    ?person schema:name ?name .
    ?person schema:age ?age .
    FILTER (?age >= 30)
  }

結果:
  | person         | name     |
  |----------------|----------|
  | ex:person/yamada | 山田太郎 |
  | ex:person/tanaka | 田中花子 |

※ VC では SPARQL は直接使わないが
   RDF の概念を理解する上で有用
```

---

## デバッグツール

### オンラインツール

```
RDF Playground:
  https://rdfplayground.dcc.uchile.cl/

  機能:
    - 各形式間の変換
    - SPARQL クエリ実行
    - グラフ可視化

JSON-LD Playground:
  https://json-ld.org/playground/

  機能:
    - JSON-LD の展開（Expansion）
    - JSON-LD の圧縮（Compaction）
    - RDF への変換
    - 正規化（Normalization）
```

### コマンドラインツール

```bash
# Python (rdflib)
pip install rdflib

python -c "
from rdflib import Graph

# JSON-LD を読み込み
g = Graph()
g.parse(data='''
{
  \"@context\": \"http://schema.org/\",
  \"@id\": \"http://example.com/person/1\",
  \"name\": \"John\"
}
''', format='json-ld')

# N-Triples で出力
print(g.serialize(format='nt'))
"

# 出力:
# <http://example.com/person/1> <http://schema.org/name> "John" .
```

---

## まとめ

```
RDF の特徴:

  ┌─────────────────────────────────────────────────────────────┐
  │                                                             │
  │  何者か:                                                    │
  │    「モノとモノの関係」を記述するデータモデル                │
  │    トリプル（主語-述語-目的語）の集合                       │
  │                                                             │
  │  核心的な概念:                                              │
  │    - トリプル: 関係を表す最小単位                          │
  │    - URI: リソースと関係を一意に識別                       │
  │    - グラフ: トリプルの集合                                │
  │                                                             │
  │  シリアライゼーション形式:                                  │
  │    - N-Triples: シンプル、正規化向き                       │
  │    - Turtle: 人間が読みやすい                              │
  │    - JSON-LD: Web 開発者向け、VC で採用                    │
  │                                                             │
  │  VC での役割:                                               │
  │    - JSON-LD の基礎となるモデル                            │
  │    - 相互運用性の確保                                      │
  │    - 署名のための正規化（RDFC-1.0）                        │
  │                                                             │
  │  学習のポイント:                                            │
  │    - まずトリプルの概念を理解                              │
  │    - URI の役割を理解                                      │
  │    - JSON-LD が RDF の JSON 表現であることを理解           │
  │                                                             │
  └─────────────────────────────────────────────────────────────┘
```

---

## 参考リンク

- [RDF 1.1 Concepts](https://www.w3.org/TR/rdf11-concepts/)
- [RDF 1.1 Primer](https://www.w3.org/TR/rdf11-primer/)
- [RDFC-1.0 (RDF Dataset Canonicalization)](https://www.w3.org/TR/rdf-canon/)
- [JSON-LD 1.1](https://www.w3.org/TR/json-ld11/)
- [Schema.org](https://schema.org/)
- [RDF Playground](https://rdfplayground.dcc.uchile.cl/)
