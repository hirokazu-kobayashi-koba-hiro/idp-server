# Mapping Functions 開発ガイド

このドキュメントでは、idp-server の Mapping Functions を開発・追加する際のルールと注意事項について説明します。

## 概要

Mapping Functions は、データマッピング処理において値の変換や生成を行う機能です。各 Function は `ValueFunction` インターフェースを実装し、`FunctionRegistry` に登録されます。

## 基本ルール

### 1. インターフェース実装

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

### 2. 命名規則

- **クラス名**: `[Function名]Function.java`（例: `RandomStringFunction.java`）
- **Function名**: snake_case を使用（例: `random_string`、`convert_type`）
- **引数名**: 分かりやすく一般的な名前を使用（例: `charset`、`length`、`pattern`）

### 3. 引数処理

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

### 4. 設置場所とファイル構造

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

## 実装手順

### 1. Function クラス作成

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

### 2. FunctionRegistry への登録

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

### 3. テストクラス作成

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

## コーディング規則

### 1. Null安全性

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

### 2. 型安全性

```java
// Good: 型チェックとキャスト
if (args.get("length") instanceof Number number) {
  int length = number.intValue();
}

// Good: getOrDefault でデフォルト値
int length = ((Number) args.getOrDefault("length", 16)).intValue();
```

### 3. エラーハンドリング

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

### 4. パフォーマンス考慮

```java
// Good: 定数の使用
private static final String DEFAULT_CHARSET = 
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

// Good: リソースの再利用
private final SecureRandom random = new SecureRandom();
```

## テスト要件

### 必須テストケース

1. **基本動作テスト**: デフォルト引数での動作
2. **カスタム引数テスト**: 各引数を指定した場合の動作
3. **エッジケーステスト**: null、空文字、境界値
4. **エラーケーステスト**: 不正な引数の処理
5. **ユニーク性テスト**: ランダム系 Function の場合

### テストの例

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

## 既存 Functions 一覧

| Function | 説明 | 主要引数 |
|----------|------|----------|
| `format` | テンプレート文字列のフォーマット | `template` |
| `random_string` | ランダム文字列生成 | `length`, `charset` |
| `now` | 現在日時の生成・フォーマット | `zone`, `pattern` |
| `exists` | 値の存在チェック | なし |
| `convert_type` | 型変換 | `type`, `trim`, `locale` |

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