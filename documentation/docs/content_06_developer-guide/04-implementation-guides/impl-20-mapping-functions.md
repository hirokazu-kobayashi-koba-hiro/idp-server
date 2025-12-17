# Mapping Functions

> **関連ドキュメント**
> - [HTTP Request Executor](impl-16-http-request-executor.md) - HTTP通信でのMapping Functions利用
> - [外部サービス連携ガイド](impl-17-external-integration.md) - 実際の統合例

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
   - 詳細: [外部サービス連携ガイド](impl-17-external-integration.md)

2. **ID Token Claims生成** - クレーム値のフォーマット変換や動的生成
   - ユーザー情報の正規化（case, trim）
   - カスタムクレーム生成（format, if, switch）

3. **Webhook/Security Event送信** - 署名生成やタイムスタンプ付与
   - イベントID生成（uuid4）
   - タイムスタンプ（now）
   - 詳細: [HTTP Request Executor](impl-16-http-request-executor.md)

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
│  │  - 配列操作: map, filter, join, split         │  │
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

### 1. マッピング実行の全体フロー

```
[リクエスト受信]
      ↓
[JSONPath評価] → ソースデータから値を抽出
      ↓
[Function解決] → FunctionRegistry.get("function_name")
      ↓
[Function実行] → ValueFunction.apply(input, args)
      ↓
[結果配置] → ターゲットオブジェクトに値をセット
      ↓
[完成データ] → 外部API送信 or レスポンス生成
```

### 2. Function実行の詳細フロー

```java
// 設定例（MappingRule）
{
  "from": "$.request.user_id",
  "to": "subject",
  "functions": [
    {
      "name": "format",
      "args": {"template": "uid:{}"}
    }
  ]
}

// 実行フロー
Step 1: JSONPath評価
  $.request.user_id → "abc123"

Step 2: FunctionRegistry検索
  registry.get("format") → FormatFunction instance

Step 3: Function実行
  FormatFunction.apply("abc123", {"template": "uid:{}"})
    ↓
  return "uid:abc123"

Step 4: 結果配置
  result: {"subject": "uid:abc123"}
```

### 3. 複数Function連鎖（将来拡張）

```
[入力値] → Function1 → Function2 → Function3 → [最終値]
            ↓          ↓          ↓
         convert    format     validate
```

## 利用可能なFunctions一覧

現在実装されている19個のFunctionsの一覧：

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
| **配列操作** | `map` | 配列要素変換 | `function`, `args` |
| | `filter` | 配列フィルタリング | `condition` |

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
- [HTTP Request Executor](impl-16-http-request-executor.md) - マッピング設定とリトライ制御
- [外部サービス連携ガイド](impl-17-external-integration.md) - 完全な実装例とテスト

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