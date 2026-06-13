# Mapping Functions

> **関連ドキュメント**
> - [HTTP Request Executor](../integration/http-request-executor.md) - HTTP通信でのMapping Functions利用
> - [外部サービス連携ガイド](../integration/external-integration.md) - 実際の統合例

## 概要

### Mapping Functionsとは

Mapping Functionsは、**データマッピング処理において値の変換や生成を行う拡張可能な機能**です。外部サービス連携（HTTP Request Executor）やID Token生成時に、動的なデータ変換を宣言的に定義できます。

### 解決する課題

- **動的な値生成**: リクエストごとに異なるランダム文字列やタイムスタンプを生成
- **フォーマット変換**: テンプレートベースの文字列フォーマット、型変換
- **条件付きマッピング**: 値の存在チェックやデフォルト値設定
- **拡張性**: 新しい変換ロジックを追加せずにビジネスロジックを拡張

### 利用シーン

1. **外部サービス連携（HTTP Request Executor）** - リクエスト/レスポンスのマッピング
   - Identity Verification API連携
   - KYC（本人確認）サービス連携
   - 動的なnonce/request_id生成
   - 詳細: [外部サービス連携ガイド](../integration/external-integration.md)

2. **ID Token Claims生成** - クレーム値のフォーマット変換や動的生成
   - ユーザー情報の正規化（case, trim）
   - カスタムクレーム生成（format, if, switch）

3. **Webhook/Security Event送信** - 署名生成やタイムスタンプ付与
   - イベントID生成（uuid4）
   - タイムスタンプ（now）
   - 詳細: [HTTP Request Executor](../integration/http-request-executor.md)

4. **内部処理** - トレースIDやセッションID生成

## アーキテクチャ

### システム全体での位置づけ

```
┌─────────────────────────────────────────────────────┐
│ Application Layer (UseCase/Handler)                 │
│  - Identity Verification                            │
│  - Token Generation                                 │
│  - Webhook Transmission                             │
└──────────────────┬──────────────────────────────────┘
                   │ uses
                   ▼
┌─────────────────────────────────────────────────────┐
│ HTTP Request Executor (外部サービス連携)            │
│  ┌───────────────────────────────────────────────┐  │
│  │ HttpRequestExecutor                           │  │
│  │  - リクエスト送信                             │  │
│  │  - リトライ制御                               │  │
│  │  - OAuth/HMAC認証                             │  │
│  └──────────────────┬────────────────────────────┘  │
│                     │ uses                          │
│                     ▼                               │
│  ┌───────────────────────────────────────────────┐  │
│  │ RequestBodyMapper / ResponseBodyMapper        │  │
│  │  - リクエスト変換（DTO → 外部API形式）       │  │
│  │  - レスポンス変換（外部API → DTO）           │  │
│  └───────────────────────────────────────────────┘  │
└──────────────────┬──────────────────────────────────┘
                   │ delegates
                   ▼
┌─────────────────────────────────────────────────────┐
│ Mapping System (idp-server-platform)                │
│  ┌───────────────────────────────────────────────┐  │
│  │ FunctionRegistry                              │  │
│  │  - 全Function管理                             │  │
│  │  - Function名による動的解決                   │  │
│  └──────────────────┬────────────────────────────┘  │
│                     │ resolves                      │
│                     ▼                               │
│  ┌───────────────────────────────────────────────┐  │
│  │ ValueFunctions (19個の実装クラス)             │  │
│  │  - 文字列操作: format, case, substring...     │  │
│  │  - ID生成: random_string, uuid4, uuid5...     │  │
│  │  - 条件分岐: if, switch, exists               │  │
│  │  - 型変換: convert_type                       │  │
│  │  - 配列操作: map, filter, join, split,        │  │
│  │             append, merge, pluck, reshape     │  │
│  └───────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────┘
```

### コンポーネント関係

```
┌──────────────────────────────────────────────────────┐
│ Configuration (JSON)                                 │
│ [                                                    │
│   {                                                  │
│     "from": "$.user.id",                             │
│     "to": "subject",                                 │
│     "functions": [                                   │
│       {                                              │
│         "name": "format",                            │
│         "args": {"template": "user:{}"}              │
│       }                                              │
│     ]                                                │
│   }                                                  │
│ ]                                                    │
└──────────────────┬───────────────────────────────────┘
                   │ List<MappingRule>
                   ▼
┌──────────────────────────────────────────────────────┐
│ MappingRuleObjectMapper.execute()                    │
│  1. JSONPath評価 ($.user.id → "12345")              │
│  2. Function解決 ("format" → FormatFunction)         │
│  3. Function実行 (apply("12345", {template...}))     │
│  4. 結果配置 (result → {"subject": "user:12345"})    │
└──────────────────────────────────────────────────────┘
```

## 処理フロー

### 全体フロー

`MappingRuleObjectMapper.execute(rules, jsonPath)` が各 MappingRule を順に処理する。

```
ソースデータ (JsonPathWrapper)
    ↓
┌─ MappingRule ごとに繰り返し ─────────────────────┐
│                                                   │
│  Phase 1: Condition評価                           │
│    condition が定義されていれば評価                 │
│    false → このルールをスキップ                    │
│                                                   │
│  Phase 2: 入力値の解決 (resolveBaseValue)         │
│    static_value → そのまま使用                    │
│    from → JSONPathでソースデータから値を抽出       │
│    どちらもなし → null                            │
│                                                   │
│  Phase 3: 関数の適用 (applyFunctions)             │
│    functions 配列を順に実行                        │
│    各関数の実行前に args の値を解決                │
│    前の関数の出力が次の関数の入力になる            │
│                                                   │
│  Phase 4: 結果の書き込み (writeResult)            │
│    to が "*" → Mapとして展開                      │
│    to が "a.b.c" → ネスト構造を生成               │
│                                                   │
└───────────────────────────────────────────────────┘
    ↓
出力データ (Map<String, Object>)
```

### 値の解決ロジック（3種類）

マッピング処理では、3つの異なるタイミングで値が解決される。

#### 1. `from` の解決 — 入力値

`from` に指定した JSONPath がソースデータから評価され、関数の **入力値（input）** になる。

```json
{ "from": "$.user.verified_claims.claims.accounts", "to": "claims.accounts" }
```
```
ソースデータ:
  { "user": { "verified_claims": { "claims": { "accounts": [...] } } } }

解決:
  $.user.verified_claims.claims.accounts → [{account_no: "111", ...}, ...]
  この配列が input として関数に渡される
```

#### 2. `static_value` の解決 — 固定入力値

`from` の代わりに `static_value` を指定すると、固定値が入力になる。入力に依存しない関数（uuid4, now 等）で使う。

```json
{ "static_value": null, "to": "request_id", "functions": [{"name": "uuid4"}] }
```
```
解決:
  input = null → uuid4関数が null を受け取り、UUID を生成して返す
```

#### 3. `args` の解決 — 関数の引数

関数の `args` 内の値は、**関数実行の直前に**解決される。

**静的な値** — `$.` で始まらない値はそのまま渡される。
```json
{ "name": "pluck", "args": { "field": "account_no" } }
```
```
解決:
  args = { "field": "account_no" }  ← そのまま
```

**動的な値（JSONPath参照）** — `$.` で始まる文字列はソースデータのJSONPathとして解決される。
```json
{ "name": "merge", "args": { "source": "$.request_body.new_accounts", "key": "account_no" } }
```
```
ソースデータ:
  { "request_body": { "new_accounts": [{account_no: "333", type: "investment"}] } }

解決:
  "$.request_body.new_accounts" → [{account_no: "333", type: "investment"}]
  args = { "source": [{account_no: "333", ...}], "key": "account_no" }
                       ↑ 動的解決済み             ↑ 静的（そのまま）
```

**解決ルール:**
- `$.` で始まる**文字列値のみ**が JSONPath として解決される
- 数値、boolean、オブジェクト、配列型の args 値はそのまま
- JSONPath が存在しないパスを指す場合は `null` に解決される
- 解決は `MappingRuleObjectMapper.resolveArgs()` で行われる

### 具体例: 既存配列と新規データのマージ

```json
{
  "from": "$.user.verified_claims.claims.accounts",
  "to": "claims.accounts",
  "functions": [
    { "name": "merge", "args": { "source": "$.application.application_details.new_accounts", "key": "account_no" } },
    { "name": "pluck", "args": { "field": "account_no" } }
  ]
}
```

```
ソースデータ:
  user.verified_claims.claims.accounts = [{account_no: "111"}, {account_no: "222"}]
  application.application_details.new_accounts = [{account_no: "333"}, {account_no: "111", type: "premium"}]

Step 1: from 解決
  input = [{account_no: "111"}, {account_no: "222"}]

Step 2: merge関数の args 解決
  "$.application.application_details.new_accounts" → [{account_no: "333"}, {account_no: "111", type: "premium"}]
  args = { source: [解決済み配列], key: "account_no" }

Step 3: merge関数の実行
  MergeFunction.apply(
    [{account_no: "111"}, {account_no: "222"}],        ← input (既存)
    { source: [{account_no: "333"}, {account_no: "111", type: "premium"}], key: "account_no" }
  )
  → [{account_no: "111", type: "premium"}, {account_no: "222"}, {account_no: "333"}]
    (account_no "111" は後勝ちで更新)

Step 4: pluck関数の実行 (merge の出力が入力になる)
  PluckFunction.apply(
    [{account_no: "111", type: "premium"}, {account_no: "222"}, {account_no: "333"}],
    { field: "account_no" }
  )
  → ["111", "222", "333"]

Step 5: 結果配置
  claims.accounts = ["111", "222", "333"]
```

### 関数チェーン

`functions` 配列に複数の関数を指定すると、左から右へ順に適用される。前の関数の出力が次の関数の入力になる。

```
[from の値] → Function1 → Function2 → Function3 → [to に書き込み]
                 ↑             ↑            ↑
              args解決       args解決     args解決
```

各関数の args は**個別に**解決される。チェーン全体で同じソースデータを参照する。

## 利用可能なFunctions一覧

現在実装されている23個のFunctionsの一覧：

| カテゴリ | Function名 | 用途 | 主要引数 |
|---------|-----------|------|----------|
| **文字列操作** | `format` | テンプレートフォーマット | `template` |
| | `case` | 大文字/小文字/camelCase変換 | `mode`, `locale`, `delimiter` |
| | `substring` | 部分文字列抽出 | `start`, `end` |
| | `replace` | 文字列置換 | `search`, `replace` |
| | `regex_replace` | 正規表現置換 | `pattern`, `replacement` |
| | `trim` | 空白削除 | なし |
| | `split` | 文字列分割 | `delimiter`, `limit` |
| | `join` | 配列結合 | `delimiter` |
| **ID生成** | `random_string` | ランダム文字列生成 | `length`, `charset` |
| | `uuid4` | UUID v4生成 | なし |
| | `uuid5` | UUID v5生成（名前ベース） | `namespace`, `name` |
| | `uuid_short` | 短縮UUID生成 | なし |
| **日時操作** | `now` | 現在日時生成 | `zone`, `pattern` |
| **型変換** | `convert_type` | 型変換 | `type`, `trim`, `locale` |
| **条件分岐** | `if` | 条件分岐 | `condition`, `then`, `else` |
| | `switch` | 多条件マッピング | `cases`, `default`, `ignoreCase` |
| | `exists` | 値存在チェック | なし |
| **配列操作** | `map` | 配列要素変換 | `function`, `function_args` |
| | `filter` | 配列フィルタリング | `condition`, `field`, `negate` |
| | `append` | 配列への要素追加 | `value` |
| | `merge` | 配列同士の結合 | `source`, `distinct`, `key` |
| | `pluck` | オブジェクト配列のフィールド抽出 | `field`, `skipNull` |
| | `reshape` | オブジェクトのフィールドリネーム/再構成 | `fields` |

### 詳細説明

以下、各Functionの詳細な使用方法を説明します：

### 1. format - テンプレートフォーマット

**用途**: 入力値をテンプレート文字列に埋め込む

```json
{
  "from": "$.user.id",
  "to": "subject",
  "functions": [
    {
      "name": "format",
      "args": {"template": "user:{}"}
    }
  ]
}
```

**変換例**: `"12345"` → `"user:12345"`

**主な使用例**:
- 外部サービス向けID形式変換
- プレフィックス付与（`"uid:xxx"`, `"tenant:xxx"`）

### 2. random_string - ランダム文字列生成

**用途**: セキュアなランダム文字列を生成（nonce, state等）

```json
{
  "static_value": null,
  "to": "nonce",
  "functions": [
    {
      "name": "random_string",
      "args": {
        "length": 32,
        "charset": "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
      }
    }
  ]
}
```

**デフォルト**: 16文字、英数字（charsetも省略可）

**変換例**: `null` → `"xK9dLm2nP4qR8tY7"` (ランダム)

**主な使用例**:
- OAuth state/nonce生成
- セッションID生成
- リクエストID生成

### 3. now - 現在日時生成

**用途**: 現在のタイムスタンプを生成・フォーマット

```json
{
  "static_value": null,
  "to": "timestamp",
  "functions": [
    {
      "name": "now",
      "args": {
        "zone": "Asia/Tokyo",
        "pattern": "yyyy-MM-dd'T'HH:mm:ssXXX"
      }
    }
  ]
}
```

**デフォルト**: UTC、ISO 8601形式

**変換例**: `null` → `"2025-01-15T10:30:00+09:00"`

**主な使用例**:
- リクエストタイムスタンプ
- 署名生成時刻
- ログ記録用タイムスタンプ

### 4. convert_type - 型変換

**用途**: 値の型を変換（string, integer, boolean等）

```json
{
  "from": "$.age",
  "to": "user.age",
  "functions": [
    {
      "name": "convert_type",
      "args": {
        "type": "integer",
        "trim": true
      }
    }
  ]
}
```

**対応型**: `string`, `integer`, `long`, `double`, `boolean`, `datetime`

**変換例**: `"30"` → `30` (integer)

**主な使用例**:
- 外部API仕様に合わせた型変換
- 文字列→数値変換
- 真偽値正規化

### 5. exists - 値存在チェック

**用途**: 値が存在するかをboolean値で返す

```json
{
  "from": "$.optional_field",
  "to": "has_field",
  "functions": [
    {
      "name": "exists",
      "args": {}
    }
  ]
}
```

**変換例**: `"value"` → `true`, `null` → `false`, 空文字 → `false`

**主な使用例**:
- 条件付きマッピング判定
- オプショナルフィールド検証
- Conditionとの組み合わせ

### 6. uuid4 - UUID v4生成

**用途**: 暗号学的に安全なランダムUUID v4を生成

```json
{
  "static_value": null,
  "to": "request_id",
  "functions": [
    {
      "name": "uuid4",
      "args": {}
    }
  ]
}
```

**変換例**: `null` → `"550e8400-e29b-41d4-a716-446655440000"`

**主な使用例**:
- リクエストID生成（標準UUID形式）
- トレーシングID
- ユニークキー生成

### 7. case - 文字列ケース変換

**用途**: 文字列の大文字/小文字/camelCase/PascalCase変換

```json
{
  "from": "$.username",
  "to": "normalized_username",
  "functions": [
    {
      "name": "case",
      "args": {
        "mode": "lower",
        "locale": "en"
      }
    }
  ]
}
```

**対応モード**:
- `upper`: 大文字（`"HELLO WORLD"`）
- `lower`: 小文字（`"hello world"`）
- `title`: タイトルケース（`"Hello World"`）
- `camel`: camelCase（`"helloWorld"`）
- `pascal`: PascalCase（`"HelloWorld"`）

**主な使用例**:
- ユーザー名正規化（lowercase）
- 表示名フォーマット（title case）
- APIプロパティ名変換（camelCase, PascalCase）

### 8. if - 条件分岐

**用途**: 条件に基づいて異なる値を返す

```json
{
  "from": "$.user.role",
  "to": "claims.role_display",
  "functions": [
    {
      "name": "if",
      "args": {
        "condition": "equals:admin",
        "then": "Administrator",
        "else": "Regular User"
      }
    }
  ]
}
```

**対応条件**:
- `null` / `not_null`: null判定
- `empty` / `not_empty`: 空判定（文字列・コレクション）
- `exists`: 存在判定
- `equals:value`: 等価判定
- `not_equals:value`: 非等価判定

**変換例**:
- `"admin"` + `equals:admin` → `"Administrator"`
- `null` + `null` → `then`値
- `""` + `empty` → `then`値

**主な使用例**:
- ユーザーロールベースの変換
- 条件付きフィールド設定
- デフォルト値割り当て

### 9. switch - 多条件マッピング

**用途**: 複数の条件による値マッピング

```json
{
  "from": "$.status",
  "to": "status_message",
  "functions": [
    {
      "name": "switch",
      "args": {
        "cases": {
          "active": "User is active",
          "inactive": "User is inactive",
          "pending": "Verification pending"
        },
        "default": "Status unknown",
        "ignoreCase": true
      }
    }
  ]
}
```

**変換例**:
- `"active"` → `"User is active"`
- `"ACTIVE"` (ignoreCase: true) → `"User is active"`
- `"unknown"` → `"Status unknown"` (default)

**主な使用例**:
- ステータスコード→メッセージ変換
- ユーザーロール変換
- Enum値翻訳
- 多言語コンテンツマッピング

### 10. append - 配列への要素追加

**用途**: 既存の配列に新しい要素を追加する

```json
{
  "from": "$.user.verified_claims.claims.tags",
  "to": "claims.tags",
  "functions": [
    {
      "name": "append",
      "args": {"value": "verified"}
    }
  ]
}
```

**変換例**: `["finance", "personal"]` → `["finance", "personal", "verified"]`

**主な使用例**:
- 既存の配列に新しい要素を追加
- verified_claimsへのタグ追加
- null入力の場合は新しい単一要素リストを生成

### 11. merge - 配列同士の結合

**用途**: 2つの配列を結合する。オプションで重複除去が可能

```json
{
  "from": "$.user.verified_claims.claims.accounts",
  "to": "claims.accounts",
  "functions": [
    {
      "name": "merge",
      "args": {
        "source": "$.request_body.new_accounts",
        "key": "account_no"
      }
    }
  ]
}
```

**変換例**:
- 基本結合: `["a", "b"]` + `["c", "d"]` → `["a", "b", "c", "d"]`
- distinct: `["a", "b"]` + `["b", "c"]` → `["a", "b", "c"]`
- keyベース重複除去: 同じ`account_no`を持つオブジェクトは後勝ちで置換

**引数**:
- `source` (必須): 結合する配列。JSONPathも指定可能（後述の「動的引数」参照）
- `distinct` (任意, デフォルト: false): 値の等価性による重複除去
- `key` (任意): オブジェクト配列の重複判定キー。指定時はkeyベースで重複除去

**主な使用例**:
- MFA 2段目のuser_resolveで取得した口座情報を既存のaccountsにマージ
- 新しいタグを既存のタグリストに統合

### 12. pluck - オブジェクト配列のフィールド抽出

**用途**: 配列内の各オブジェクトから指定プロパティの値を抽出してフラット配列にする

```json
{
  "from": "$.application.application_details.accounts",
  "to": "claims.account_numbers",
  "functions": [
    {
      "name": "pluck",
      "args": {"field": "account_no"}
    }
  ]
}
```

**変換例**: `[{"account_no": "123", "type": "savings"}, {"account_no": "456", "type": "checking"}]` → `["123", "456"]`

**引数**:
- `field` (必須): 抽出するフィールド名
- `skipNull` (任意, デフォルト: false): nullの抽出結果をスキップ

**主な使用例**:
- オブジェクト配列から特定のIDフィールドをフラット配列として抽出
- filter/mapとの組み合わせでオブジェクト配列操作

### 13. reshape - オブジェクトのフィールドリネーム/再構成

**用途**: オブジェクトのフィールドを別名に変換し、必要に応じて各フィールドの値を関数で加工して新しい shape のオブジェクトを生成する

```json
{
  "name": "reshape",
  "args": {
    "fields": {
      "id": "$.entity_id",
      "name": "$.entity_name",
      "type": "$.kind"
    }
  }
}
```

**変換例**: `{"entity_id": "1", "entity_name": "Foo", "kind": "bar"}` → `{"id": "1", "name": "Foo", "type": "bar"}`

**引数**:
- `fields` (必須): 出力フィールドの定義 Map。キーが出力フィールド名、値は以下の3形式:

**fields の値の形式:**

| 形式 | 説明 | 例 |
|------|------|-----|
| JSONPath 文字列 | `$.` で始まる文字列。入力オブジェクトから値を取得 | `"$.entity_id"` |
| Map (`from` + `functions`) | 値を取得して関数チェーンで加工 | `{"from": "$.amount", "functions": [...]}` |
| Map (`static_value`) | 固定値を設定 | `{"static_value": "external"}` |
| その他 | 静的値としてそのまま使用 | `1`, `true` |

**per-field 関数チェーンの例:**

```json
{
  "name": "reshape",
  "args": {
    "fields": {
      "id": "$.entity_id",
      "amount": {
        "from": "$.amount_str",
        "functions": [
          {"name": "convert_type", "args": {"type": "integer"}}
        ]
      },
      "status": {
        "from": "$.status_code",
        "functions": [
          {"name": "switch", "args": {"cases": {"A": "active", "I": "inactive"}, "default": "unknown"}}
        ]
      },
      "name": {
        "from": "$.raw_name",
        "functions": [
          {"name": "trim"},
          {"name": "case", "args": {"mode": "upper"}}
        ]
      },
      "tag": {"static_value": "external"}
    }
  }
}
```

```
Input:  {"entity_id": "1", "amount_str": "9999", "status_code": "A", "raw_name": "  alice  "}
Output: {"id": "1", "amount": 9999, "status": "active", "name": "ALICE", "tag": "external"}
```

**主な使用例**:
- 外部 API レスポンスの schema を canonical shape に正規化
- `map(reshape)` で配列内の各オブジェクトを一括変換
- フィールドのサブセット抽出（必要なフィールドだけ残す）
- per-field の型変換・値マッピング・文字列正規化

**`map` との組み合わせ（配列の各要素を変換）**:

```json
{
  "from": "$.response_body.items",
  "to": "entities",
  "functions": [
    {
      "name": "map",
      "args": {
        "function": "reshape",
        "function_args": {
          "fields": {
            "id": "$.entity_id",
            "name": "$.entity_name",
            "type": "$.kind"
          }
        }
      }
    }
  ]
}
```

```
Input:  [{"entity_id": "1", "entity_name": "Foo", "kind": "bar"}, ...]
Output: [{"id": "1", "name": "Foo", "type": "bar"}, ...]
```

### 14. filter の field 引数 - オブジェクト配列のフィールド条件フィルタリング

**用途**: オブジェクト配列の特定フィールドの値で条件判定し、マッチした要素（オブジェクト全体）を残す

```json
{
  "name": "filter",
  "args": {
    "field": "type",
    "condition": "{{value}} == 'savings'"
  }
}
```

**変換例**:
```
Input:  [{"id": "1", "type": "savings"}, {"id": "2", "type": "checking"}, {"id": "3", "type": "savings"}]
Output: [{"id": "1", "type": "savings"}, {"id": "3", "type": "savings"}]
```

**引数**:
- `field` (任意): フィルタリング対象のフィールド名。指定時はオブジェクトの該当フィールド値で condition を評価。未指定時は既存動作（文字列配列向け）
- `condition` (必須): 既存の条件式（`{{value}} == 'xxx'`, `{{value}} contains 'xxx'` 等）
- `negate` (任意, デフォルト: false): 条件の否定

**主な使用例**:
- `map(reshape) → filter(field) → merge`: 正規化 → 条件フィルタ → 累積マージ
- 特定のステータスや種別のオブジェクトのみ抽出

## 実用パターン集

### パターン1: 外部 API レスポンスを正規化して累積マージ

外部 API のレスポンス schema が異なるケースで、canonical shape に正規化してから既存データにマージする。

```json
{
  "from": "$.response_body.bank_accounts",
  "to": "claims.accounts",
  "functions": [
    {"name": "map", "args": {"function": "reshape", "function_args": {
      "fields": {"id": "$.account_code", "name": "$.account_name", "type": "$.account_type"}
    }}},
    {"name": "merge", "args": {"source": "$.user.verified_claims.claims.accounts", "key": "id"}}
  ]
}
```

### パターン2: 条件付きフィルタ + マージ

特定の種別のみフィルタしてから累積する。

```json
{
  "from": "$.response_body.bank_accounts",
  "to": "claims.accounts",
  "functions": [
    {"name": "map", "args": {"function": "reshape", "function_args": {
      "fields": {"id": "$.account_code", "name": "$.account_name", "type": "$.account_type"}
    }}},
    {"name": "filter", "args": {"field": "type", "condition": "{{value}} == 'savings'"}},
    {"name": "merge", "args": {"source": "$.user.verified_claims.claims.accounts", "key": "id"}}
  ]
}
```

### パターン3: 単一オブジェクトを配列に包んで累積

外部 API が単一オブジェクトを返す場合、`append` の null 入力パターンで配列化してからマージする。

```json
{
  "static_value": null,
  "to": "claims.accounts",
  "functions": [
    {"name": "append", "args": {"value": "$.response_body.new_account"}},
    {"name": "merge", "args": {"source": "$.user.verified_claims.claims.accounts", "key": "id"}}
  ]
}
```

`static_value: null` → `append` で `[新オブジェクト]` → `merge` で既存配列と結合

## 動的引数（Args JSONPath 解決）

### 概要

Function の引数（args）に `$.` で始まる文字列を指定すると、マッピング実行時にソースデータのJSONPathとして解決されます。これにより、固定値だけでなくコンテキストから動的に取得した値を引数として利用できます。

### 仕組み

```
設定:
  {"name": "merge", "args": {"source": "$.request_body.new_accounts"}}

実行時:
  1. args の各値をチェック
  2. "$.request_body.new_accounts" は "$." で始まるのでJSONPathとして認識
  3. ソースデータから値を読み取り → [{account_no: "333", ...}]
  4. 解決後の args で Function を実行
```

### 使用例

#### 既存配列に動的な値をappend

```json
{
  "from": "$.user.verified_claims.claims.tags",
  "to": "claims.tags",
  "functions": [
    {
      "name": "append",
      "args": {"value": "$.request_body.new_tag"}
    }
  ]
}
```

- `from`: 既存のtagsの配列が`input`として渡る
- `args.value`: `$.request_body.new_tag` → ソースデータから動的に解決
- 結果: 既存のtagsにリクエストボディの値が追加される

#### 既存配列とリクエストの配列をマージ

```json
{
  "from": "$.user.verified_claims.claims.accounts",
  "to": "claims.accounts",
  "functions": [
    {
      "name": "merge",
      "args": {
        "source": "$.request_body.new_accounts",
        "key": "account_no"
      }
    }
  ]
}
```

- `from`: ユーザーの既存accountsの配列が`input`
- `args.source`: リクエストボディの新規accounts配列に動的解決
- `args.key`: 静的な文字列値（`$.` で始まらないのでそのまま）
- 結果: 既存のaccountsに新規accountsがaccount_noベースでマージされる

### ルール

- `$.` で始まる**文字列値のみ**がJSONPathとして解決される
- 数値、boolean、オブジェクト、配列型の値はそのまま渡される
- `$.` で始まるが存在しないパスの場合は `null` に解決される
- 既存の静的引数（`"key": "account_no"` 等）には影響しない

### 実装箇所

`MappingRuleObjectMapper.resolveArgs()` がfunction実行前にargsを走査し、`$.` で始まる文字列をJSONPathとして解決する。`ValueFunction` インターフェースの変更は不要。

### 実用例：HTTP Request Executorとの統合

外部Identity Verification API連携での実際の使用例：

#### シナリオ

内部のユーザー情報を外部KYC（本人確認）サービスのAPI形式に変換してリクエスト送信し、レスポンスを内部形式に変換して取得する。

#### リクエストマッピング（内部 → 外部API）

```json
{
  "request_mappings": [
    {
      "from": "$.user.id",
      "to": "customer_id",
      "functions": [
        {
          "name": "format",
          "args": {"template": "USR-{}"}
        }
      ]
    },
    {
      "from": "$.user.name.given",
      "to": "first_name"
    },
    {
      "from": "$.user.name.family",
      "to": "last_name"
    },
    {
      "from": "$.user.email",
      "to": "email_address",
      "functions": [
        {
          "name": "case",
          "args": {"mode": "lower"}
        }
      ]
    },
    {
      "from": "$.user.age",
      "to": "age",
      "functions": [
        {
          "name": "convert_type",
          "args": {"type": "integer"}
        }
      ]
    },
    {
      "static_value": null,
      "to": "request_id",
      "functions": [
        {
          "name": "uuid4",
          "args": {}
        }
      ]
    },
    {
      "static_value": null,
      "to": "timestamp",
      "functions": [
        {
          "name": "now",
          "args": {
            "zone": "UTC",
            "pattern": "yyyy-MM-dd'T'HH:mm:ss'Z'"
          }
        }
      ]
    }
  ]
}
```

#### レスポンスマッピング（外部API → 内部）

```json
{
  "response_mappings": [
    {
      "from": "$.verification_result.status",
      "to": "claims.verification_status",
      "functions": [
        {
          "name": "switch",
          "args": {
            "cases": {
              "APPROVED": "verified",
              "REJECTED": "not_verified",
              "PENDING": "pending_verification"
            },
            "default": "unknown",
            "ignoreCase": true
          }
        }
      ]
    },
    {
      "from": "$.verification_result.trust_framework",
      "to": "claims.trust_framework"
    },
    {
      "from": "$.verification_result.evidence",
      "to": "claims.evidence"
    },
    {
      "from": "$.user_info.verified_at",
      "to": "claims.verified_at",
      "functions": [
        {
          "name": "convert_type",
          "args": {"type": "datetime"}
        }
      ]
    }
  ]
}
```

#### 実現内容

**リクエスト変換**:
- ID正規化: `"12345"` → `"USR-12345"` (format)
- メール正規化: `"User@Example.COM"` → `"user@example.com"` (case)
- 型変換: `"30"` → `30` (convert_type)
- UUID生成: `null` → `"550e8400-e29b-41d4-a716-446655440000"` (uuid4)
- タイムスタンプ: `null` → `"2025-01-15T10:30:00Z"` (now)

**レスポンス変換**:
- ステータス変換: `"APPROVED"` → `"verified"` (switch)
- 日時変換: `"2025-01-15T10:30:00Z"` → DateTime型 (convert_type)

#### HttpRequestExecutorとの連携

詳細な実装方法は以下を参照：
- [HTTP Request Executor](../integration/http-request-executor.md) - マッピング設定とリトライ制御
- [外部サービス連携ガイド](../integration/external-integration.md) - 完全な実装例とテスト

## 実装ガイド

新しいFunctionを追加する際のルールと手順を説明します。

### 基本ルール

#### 1. インターフェース実装

全ての Function は `ValueFunction` インターフェースを実装する必要があります：

```java
public interface ValueFunction {
  /**
   * @param input 変換後の値（null の場合があります）
   * @param args 各 function 固有の引数（テンプレート、長さなど）
   * @return 変換された値（null の場合があります）
   */
  Object apply(Object input, Map<String, Object> args);

  /** function のユニークな名前を返します（例: "format", "random_string"） */
  String name();
}
```

#### 2. 命名規則

- **クラス名**: `[Function名]Function.java`（例: `RandomStringFunction.java`）
- **Function名**: snake_case を使用（例: `random_string`、`convert_type`）
- **引数名**: 分かりやすく一般的な名前を使用（例: `charset`、`length`、`pattern`）

#### 3. 引数処理

引数は `Map<String, Object> args` から取得し、適切なデフォルト値を提供：

```java
// Good: デフォルト値を提供
int length = ((Number) args.getOrDefault("length", 16)).intValue();
String charset = (String) args.getOrDefault("charset", DEFAULT_CHARSET);

// Good: オプション引数の処理
String pattern = (String) args.get("pattern");
if (pattern != null && !pattern.isEmpty()) {
    // パターンが指定された場合の処理
}
```

#### 4. 設置場所とファイル構造

```
libs/idp-server-platform/src/main/java/org/idp/server/platform/mapper/functions/
├── ValueFunction.java          # インターフェース
├── FunctionRegistry.java       # 登録管理
├── FormatFunction.java         # 各 Function 実装
├── RandomStringFunction.java
└── [新しいFunction].java

libs/idp-server-platform/src/test/java/org/idp/server/platform/mapper/functions/
├── [Function名]Test.java       # 対応するテスト
└── RandomStringFunctionTest.java
```

### 実装手順

#### 1. Function クラス作成

```java
package org.idp.server.platform.mapper.functions;

import java.util.Map;

public class ExampleFunction implements ValueFunction {

  @Override
  public Object apply(Object input, Map<String, Object> args) {
    // 引数の取得（デフォルト値付き）
    String param1 = (String) args.getOrDefault("param1", "default");
    int param2 = ((Number) args.getOrDefault("param2", 10)).intValue();
    
    // 実装ロジック
    // ...
    
    return result;
  }

  @Override
  public String name() {
    return "example";
  }
}
```

#### 2. FunctionRegistry への登録

`FunctionRegistry.java` のコンストラクタに追加：

```java
public FunctionRegistry() {
  register(new FormatFunction());
  register(new RandomStringFunction());
  register(new NowFunction());
  register(new ExistsFunction());
  register(new ConvertTypeFunction());
  register(new ExampleFunction()); // 新しい Function を追加
}
```

#### 3. テストクラス作成

```java
package org.idp.server.platform.mapper.functions;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ExampleFunctionTest {

  private final ExampleFunction function = new ExampleFunction();

  @Test
  public void testName() {
    assertEquals("example", function.name());
  }

  @Test
  public void testApplyWithDefaultParameters() {
    Map<String, Object> args = new HashMap<>();
    Object result = function.apply("input", args);
    // assertions...
  }

  @Test
  public void testApplyWithCustomParameters() {
    Map<String, Object> args = new HashMap<>();
    args.put("param1", "custom");
    args.put("param2", 20);
    
    Object result = function.apply("input", args);
    // assertions...
  }
}
```

### コーディング規則

#### 1. Null安全性

```java
// Good: null チェック
if (input == null) {
  return defaultValue;
}

// Good: String の空チェック
if (input instanceof String s && !s.isEmpty()) {
  // 処理
}
```

#### 2. 型安全性

```java
// Good: 型チェックとキャスト
if (args.get("length") instanceof Number number) {
  int length = number.intValue();
}

// Good: getOrDefault でデフォルト値
int length = ((Number) args.getOrDefault("length", 16)).intValue();
```

#### 3. エラーハンドリング

```java
// Good: 不正な引数の処理
try {
  DateTimeFormatter fmt = DateTimeFormatter.ofPattern(pattern);
  return fmt.format(dateTime);
} catch (IllegalArgumentException e) {
  // ログ出力またはデフォルト処理
  return dateTime.toString();
}
```

#### 4. パフォーマンス考慮

```java
// Good: 定数の使用
private static final String DEFAULT_CHARSET = 
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

// Good: リソースの再利用
private final SecureRandom random = new SecureRandom();
```

### テスト要件

#### 必須テストケース

1. **基本動作テスト**: デフォルト引数での動作
2. **カスタム引数テスト**: 各引数を指定した場合の動作
3. **エッジケーステスト**: null、空文字、境界値
4. **エラーケーステスト**: 不正な引数の処理
5. **ユニーク性テスト**: ランダム系 Function の場合

#### テストの例

```java
@Test
public void testBasicFunctionality() {
  // 基本動作
}

@Test
public void testCustomParameters() {
  // カスタム引数
}

@Test
public void testEdgeCases() {
  // エッジケース
}

@Test
public void testErrorHandling() {
  // エラーハンドリング
}

@Test
public void testSpecificUseCase() {
  // 特定の使用例（Issue の例など）
}
```

## 注意事項

### 1. 引数名の一貫性
- 長さ: `length`
- 文字セット: `charset`（`alphabet` ではなく）
- パターン: `pattern`
- タイムゾーン: `zone`

### 2. 後方互換性
新しい引数を追加する場合は、既存の設定が動作することを確認

### 3. ドキュメント
複雑な Function にはクラスレベルの Javadoc を追加

### 4. セキュリティ
- ランダム生成には `SecureRandom` を使用
- 入力値の検証を適切に実行
- 機密情報の漏洩防止

## Pull Request 作成時のチェックリスト

- [ ] `ValueFunction` インターフェースを実装
- [ ] `FunctionRegistry` に登録
- [ ] 包括的なテストを作成
- [ ] 全テストが通過
- [ ] 命名規則に従っている
- [ ] 適切なデフォルト値を提供
- [ ] エラーハンドリングを実装
- [ ] Javadoc（必要に応じて）
- [ ] 既存機能への影響なし

このガイドに従って Function を開発することで、一貫性があり保守しやすいコードを作成できます。