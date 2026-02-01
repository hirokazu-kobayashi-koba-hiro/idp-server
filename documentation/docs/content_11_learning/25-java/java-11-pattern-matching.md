# Java Pattern Matching

Java 21で完成したパターンマッチングを学びます。

---

## Pattern Matching とは

値の構造を検査し、構成要素を抽出する機能。

```java
// 従来
if (obj instanceof String) {
    String s = (String) obj;
    System.out.println(s.length());
}

// Pattern Matching
if (obj instanceof String s) {
    System.out.println(s.length());  // キャスト不要
}
```

---

## instanceof パターン

### 基本

```java
Object obj = "Hello";

if (obj instanceof String s) {
    // s は String として使える
    System.out.println(s.toUpperCase());
}

// 否定
if (!(obj instanceof String s)) {
    return;
}
// ここでは s が使える（フロースコーピング）
System.out.println(s.length());
```

### 条件の組み合わせ

```java
// AND
if (obj instanceof String s && s.length() > 5) {
    System.out.println("Long string: " + s);
}

// OR は使えない（スコープが曖昧になるため）
// if (obj instanceof String s || obj instanceof Integer i) { }  // コンパイルエラー
```

### null の扱い

```java
Object obj = null;

// instanceof は null に対して false
if (obj instanceof String s) {
    // null の場合はここに入らない
}
```

---

## switch 式のパターンマッチング

### 基本

```java
Object obj = ...;

String result = switch (obj) {
    case Integer i -> "Integer: " + i;
    case Long l -> "Long: " + l;
    case Double d -> "Double: " + d;
    case String s -> "String: " + s;
    case null -> "null";
    default -> "Unknown: " + obj.getClass();
};
```

### null の処理

```java
// null を明示的に処理
String result = switch (obj) {
    case null -> "It's null";
    case String s -> "String: " + s;
    default -> "Other";
};

// null と default をまとめる
String result = switch (obj) {
    case String s -> "String: " + s;
    case null, default -> "Not a string";
};
```

### ガード条件（when）

```java
String describe(Object obj) {
    return switch (obj) {
        case Integer i when i > 0 -> "Positive integer: " + i;
        case Integer i when i < 0 -> "Negative integer: " + i;
        case Integer i -> "Zero";
        case String s when s.isEmpty() -> "Empty string";
        case String s -> "String: " + s;
        default -> "Other";
    };
}
```

---

## Record Pattern

Record の構成要素を分解して抽出。

### 基本

```java
record Point(int x, int y) {}

Object obj = new Point(10, 20);

if (obj instanceof Point(int x, int y)) {
    System.out.println("x=" + x + ", y=" + y);
}

// switch での使用
String describe(Object obj) {
    return switch (obj) {
        case Point(int x, int y) -> "Point at (" + x + ", " + y + ")";
        default -> "Not a point";
    };
}
```

### var の使用

```java
if (obj instanceof Point(var x, var y)) {
    // x, y の型は推論される
}
```

### ネストした Record Pattern

```java
record Point(int x, int y) {}
record Line(Point start, Point end) {}

Object obj = new Line(new Point(0, 0), new Point(10, 10));

if (obj instanceof Line(Point(var x1, var y1), Point(var x2, var y2))) {
    double length = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    System.out.println("Length: " + length);
}

// switch での使用
String describe(Object obj) {
    return switch (obj) {
        case Line(Point(var x1, var y1), Point(var x2, var y2))
            when x1 == x2 -> "Vertical line";
        case Line(Point(var x1, var y1), Point(var x2, var y2))
            when y1 == y2 -> "Horizontal line";
        case Line l -> "Diagonal line";
        default -> "Not a line";
    };
}
```

---

## Sealed Classes との組み合わせ

Sealed Classes と Pattern Matching を組み合わせると、網羅性がコンパイル時にチェックされる。

```java
sealed interface Shape permits Circle, Rectangle, Triangle {}
record Circle(double radius) implements Shape {}
record Rectangle(double width, double height) implements Shape {}
record Triangle(double base, double height) implements Shape {}

double area(Shape shape) {
    return switch (shape) {
        case Circle(var r) -> Math.PI * r * r;
        case Rectangle(var w, var h) -> w * h;
        case Triangle(var b, var h) -> 0.5 * b * h;
        // default 不要！全ケースをカバー
    };
}
```

### 新しいサブタイプの追加

```java
// 新しい形状を追加
record Pentagon(double side) implements Shape {}

// コンパイルエラー: Pentagon のケースがない
double area(Shape shape) {
    return switch (shape) {
        case Circle(var r) -> Math.PI * r * r;
        case Rectangle(var w, var h) -> w * h;
        case Triangle(var b, var h) -> 0.5 * b * h;
        // エラー: switch は網羅的ではない
    };
}
```

---

## 実践的なパターン

### イベント処理

```java
sealed interface Event {
    record UserCreated(String userId, String email) implements Event {}
    record UserUpdated(String userId, Map<String, Object> changes) implements Event {}
    record UserDeleted(String userId) implements Event {}
}

void handle(Event event) {
    switch (event) {
        case Event.UserCreated(var id, var email) -> {
            log.info("User created: {} with email {}", id, email);
            sendWelcomeEmail(email);
        }
        case Event.UserUpdated(var id, var changes) -> {
            log.info("User updated: {} with changes {}", id, changes);
            auditChanges(id, changes);
        }
        case Event.UserDeleted(var id) -> {
            log.info("User deleted: {}", id);
            cleanupUserData(id);
        }
    }
}
```

### 結果型の処理

```java
sealed interface Result<T> {
    record Success<T>(T value) implements Result<T> {}
    record Failure<T>(String error, Exception cause) implements Result<T> {}
}

<T> T handleResult(Result<T> result) {
    return switch (result) {
        case Result.Success(var value) -> value;
        case Result.Failure(var error, var cause) -> {
            log.error("Operation failed: {}", error, cause);
            throw new RuntimeException(error, cause);
        }
    };
}
```

### 式の評価

```java
sealed interface Expr {
    record Num(int value) implements Expr {}
    record Add(Expr left, Expr right) implements Expr {}
    record Mul(Expr left, Expr right) implements Expr {}
    record Var(String name) implements Expr {}
}

int eval(Expr expr, Map<String, Integer> env) {
    return switch (expr) {
        case Expr.Num(var v) -> v;
        case Expr.Add(var l, var r) -> eval(l, env) + eval(r, env);
        case Expr.Mul(var l, var r) -> eval(l, env) * eval(r, env);
        case Expr.Var(var name) -> env.getOrDefault(name, 0);
    };
}

// 使用
Expr expr = new Expr.Add(
    new Expr.Var("x"),
    new Expr.Mul(new Expr.Num(2), new Expr.Var("y"))
);
int result = eval(expr, Map.of("x", 5, "y", 3));  // 5 + 2*3 = 11
```

### JSON パース結果の処理

```java
sealed interface JsonValue {
    record JsonNull() implements JsonValue {}
    record JsonBool(boolean value) implements JsonValue {}
    record JsonNumber(double value) implements JsonValue {}
    record JsonString(String value) implements JsonValue {}
    record JsonArray(List<JsonValue> elements) implements JsonValue {}
    record JsonObject(Map<String, JsonValue> members) implements JsonValue {}
}

String stringify(JsonValue value) {
    return switch (value) {
        case JsonValue.JsonNull() -> "null";
        case JsonValue.JsonBool(var b) -> String.valueOf(b);
        case JsonValue.JsonNumber(var n) -> String.valueOf(n);
        case JsonValue.JsonString(var s) -> "\"" + s + "\"";
        case JsonValue.JsonArray(var elements) ->
            "[" + elements.stream().map(this::stringify).collect(Collectors.joining(", ")) + "]";
        case JsonValue.JsonObject(var members) ->
            "{" + members.entrySet().stream()
                .map(e -> "\"" + e.getKey() + "\": " + stringify(e.getValue()))
                .collect(Collectors.joining(", ")) + "}";
    };
}
```

### コマンド実行

```java
sealed interface Command {
    record Help(String topic) implements Command {}
    record List(String path, boolean recursive) implements Command {}
    record Get(String key) implements Command {}
    record Set(String key, String value) implements Command {}
    record Delete(String key) implements Command {}
}

String execute(Command cmd) {
    return switch (cmd) {
        case Command.Help(var topic) when topic.isEmpty() -> showGeneralHelp();
        case Command.Help(var topic) -> showTopicHelp(topic);
        case Command.List(var path, var recursive) -> listItems(path, recursive);
        case Command.Get(var key) -> getValue(key);
        case Command.Set(var key, var value) -> setValue(key, value);
        case Command.Delete(var key) -> deleteValue(key);
    };
}
```

---

## パターンの優先順位

より具体的なパターンを先に書く。

```java
String describe(Object obj) {
    return switch (obj) {
        // 具体的なパターンを先に
        case String s when s.isEmpty() -> "Empty string";
        case String s when s.length() > 100 -> "Long string";
        case String s -> "String: " + s;

        // より一般的なパターンを後に
        case Number n -> "Number: " + n;
        case null -> "null";
        default -> "Other";
    };
}
```

### コンパイルエラーの例

```java
// NG: 到達不能なパターン
String describe(Object obj) {
    return switch (obj) {
        case Object o -> "Any object";  // 全てにマッチ
        case String s -> "String";      // 到達不能！コンパイルエラー
    };
}
```

---

## 従来の書き方との比較

### instanceof チェーン

```java
// 従来
String describe(Object obj) {
    if (obj instanceof Integer) {
        Integer i = (Integer) obj;
        return "Integer: " + i;
    } else if (obj instanceof String) {
        String s = (String) obj;
        return "String: " + s;
    } else if (obj == null) {
        return "null";
    } else {
        return "Other";
    }
}

// Pattern Matching
String describe(Object obj) {
    return switch (obj) {
        case Integer i -> "Integer: " + i;
        case String s -> "String: " + s;
        case null -> "null";
        default -> "Other";
    };
}
```

### Visitor パターン

```java
// 従来: Visitor パターン
interface ShapeVisitor<R> {
    R visit(Circle c);
    R visit(Rectangle r);
}

class AreaCalculator implements ShapeVisitor<Double> {
    public Double visit(Circle c) { return Math.PI * c.radius * c.radius; }
    public Double visit(Rectangle r) { return r.width * r.height; }
}

// Pattern Matching（より簡潔）
double area(Shape shape) {
    return switch (shape) {
        case Circle(var r) -> Math.PI * r * r;
        case Rectangle(var w, var h) -> w * h;
    };
}
```

---

## 注意点

### 変数スコープ

```java
// パターン変数はそのブランチ内でのみ有効
String result = switch (obj) {
    case String s -> s.toUpperCase();  // s はここでのみ有効
    default -> "other";
    // s はここでは使えない
};
```

### 網羅性チェック

```java
// Sealed type の場合、網羅的でないとコンパイルエラー
sealed interface Option<T> {
    record Some<T>(T value) implements Option<T> {}
    record None<T>() implements Option<T> {}
}

// OK: 網羅的
<T> T getOrDefault(Option<T> opt, T defaultValue) {
    return switch (opt) {
        case Option.Some(var v) -> v;
        case Option.None() -> defaultValue;
    };
}

// エラー: None のケースがない
<T> T getValue(Option<T> opt) {
    return switch (opt) {
        case Option.Some(var v) -> v;
        // コンパイルエラー: switch は網羅的ではない
    };
}
```

---

## まとめ

| パターン | 説明 | 例 |
|---------|------|-----|
| 型パターン | 型チェック + キャスト | `case String s` |
| Record Pattern | Record の分解 | `case Point(var x, var y)` |
| ガード条件 | 追加条件 | `case String s when s.isEmpty()` |
| null パターン | null の処理 | `case null` |

### ベストプラクティス

1. Sealed Classes と組み合わせて網羅性を保証
2. 具体的なパターンを先に書く
3. when でガード条件を追加
4. Record Pattern でネストした構造を分解

---

## 次のステップ

- [idp-serverパターン](java-12-idp-patterns.md) - 実践的なパターン
