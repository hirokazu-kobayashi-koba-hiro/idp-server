# Java 21 新機能

## はじめに

Java 21 は LTS（Long-Term Support）リリースであり、多くの重要な新機能が含まれています。本章では、特にパフォーマンスとコードの品質に影響する機能を解説します。

---

## Virtual Threads（仮想スレッド）

### 従来のスレッドモデルの問題

```
┌─────────────────────────────────────────────────────────────────────┐
│                 Platform Threads の制約                              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Platform Thread（従来のスレッド）                                   │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │  ・OS スレッドと 1:1 で対応                                    │ │
│  │  ・スタックサイズ: 約1MB（デフォルト）                         │ │
│  │  ・コンテキストスイッチのコストが高い                          │ │
│  │  ・数千スレッドが実用的な上限                                  │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                                                                      │
│  問題: I/O待ちでスレッドがブロックされると...                       │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │  Thread-1: [処理]█████[I/O待ち]░░░░░░░░░░░░░[処理]█████        │ │
│  │  Thread-2: [処理]█████[I/O待ち]░░░░░░░░░░░░░[処理]█████        │ │
│  │  Thread-3: [処理]█████[I/O待ち]░░░░░░░░░░░░░[処理]█████        │ │
│  │                                                                 │ │
│  │  → 多くのスレッドがI/O待ちで浪費                              │ │
│  │  → 同時接続数の制限                                           │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### Virtual Threads のアーキテクチャ

```
┌─────────────────────────────────────────────────────────────────────┐
│                 Virtual Threads の構造                               │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Virtual Threads（軽量スレッド）                                     │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │  VT-1  VT-2  VT-3  VT-4  VT-5 ... VT-10000                     │ │
│  │    │     │     │     │     │           │                       │ │
│  │    └─────┼─────┼─────┴─────┘           │                       │ │
│  │          │     │                        │                       │ │
│  │    ┌─────┴─────┴────────────────────────┴────┐                 │ │
│  │    │     Carrier Threads (Platform Threads)  │                 │ │
│  │    │  PT-1    PT-2    PT-3    PT-4           │                 │ │
│  │    └─────────────────────────────────────────┘                 │ │
│  │          │                                                      │ │
│  │    ┌─────┴─────────────────────────────────────┐               │ │
│  │    │           OS Threads                       │               │ │
│  │    └────────────────────────────────────────────┘               │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                                                                      │
│  ・Virtual Thread は JVM が管理する軽量スレッド                     │
│  ・I/O待ち時に自動的に別の VT に切り替え                            │
│  ・数百万の VT を作成可能                                           │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### 基本的な使い方

```java
// Virtual Thread を直接作成
Thread vThread = Thread.ofVirtual().start(() -> {
    System.out.println("Running in virtual thread");
});

// Virtual Thread の ExecutorService
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    for (int i = 0; i < 10000; i++) {
        executor.submit(() -> {
            // 各タスクが専用の Virtual Thread で実行
            performBlockingOperation();
        });
    }
}  // try-with-resources で自動シャットダウン
```

### Spring Boot での活用

```yaml
# application.yml (Spring Boot 3.2+)
spring:
  threads:
    virtual:
      enabled: true  # Virtual Threads を有効化
```

```java
// または明示的に設定
@Bean
public TomcatProtocolHandlerCustomizer<?> protocolHandlerVirtualThreadCustomizer() {
    return protocolHandler -> {
        protocolHandler.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    };
}
```

### Virtual Threads の効果

```
┌─────────────────────────────────────────────────────────────────────┐
│                 パフォーマンス比較                                   │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  シナリオ: 10,000 同時リクエスト（各リクエストで 100ms の I/O 待ち）│
│                                                                      │
│  Platform Threads (200 threads pool)                                 │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │  スループット: ~2,000 req/sec                                  │ │
│  │  メモリ使用: ~200MB (スタック)                                 │ │
│  │  同時処理: 200 (プールサイズ)                                  │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                                                                      │
│  Virtual Threads                                                     │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │  スループット: ~10,000 req/sec (5倍)                           │ │
│  │  メモリ使用: ~10MB                                             │ │
│  │  同時処理: 10,000 (リクエスト数)                               │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### 注意点

```java
// ❌ Virtual Threads で避けるべきパターン

// 1. synchronized ブロック内での I/O（ピンニング発生）
synchronized(lock) {
    connection.read();  // Virtual Thread が Carrier Thread に固定される
}

// ✅ ReentrantLock を使用
private final ReentrantLock lock = new ReentrantLock();
lock.lock();
try {
    connection.read();  // ピンニングなし
} finally {
    lock.unlock();
}

// 2. ThreadLocal の大量使用
// Virtual Thread は大量に作成されるため、ThreadLocal のメモリ消費に注意

// 3. CPU バウンドタスク
// Virtual Threads は I/O バウンドタスクに最適
// CPU バウンドには Platform Thread プールが適切
```

---

## Record

### 従来の問題

```java
// 従来の DTO クラス（ボイラープレートが多い）
public class User {
    private final String name;
    private final int age;

    public User(String name, int age) {
        this.name = name;
        this.age = age;
    }

    public String getName() { return name; }
    public int getAge() { return age; }

    @Override
    public boolean equals(Object o) { ... }

    @Override
    public int hashCode() { ... }

    @Override
    public String toString() { ... }
}
```

### Record の使用

```java
// Record（Java 16+、Java 21 で安定）
public record User(String name, int age) { }

// 自動生成されるもの:
// - コンストラクタ
// - getter（name(), age()）
// - equals(), hashCode(), toString()
// - 全フィールドは final

// 使用例
User user = new User("John", 30);
System.out.println(user.name());  // John
System.out.println(user);         // User[name=John, age=30]
```

### Record のカスタマイズ

```java
public record User(String name, int age) {

    // コンパクトコンストラクタ（バリデーション）
    public User {
        if (age < 0) {
            throw new IllegalArgumentException("Age cannot be negative");
        }
        name = name.trim();  // 正規化
    }

    // 追加メソッド
    public boolean isAdult() {
        return age >= 18;
    }

    // static ファクトリメソッド
    public static User anonymous() {
        return new User("Anonymous", 0);
    }
}
```

---

## Pattern Matching

### instanceof の Pattern Matching

```java
// 従来
if (obj instanceof String) {
    String s = (String) obj;
    System.out.println(s.length());
}

// Pattern Matching（Java 16+）
if (obj instanceof String s) {
    System.out.println(s.length());
}

// ガード条件
if (obj instanceof String s && s.length() > 5) {
    System.out.println("Long string: " + s);
}
```

### switch の Pattern Matching

```java
// Record パターン
record Point(int x, int y) { }
record Circle(Point center, int radius) { }
record Rectangle(Point topLeft, Point bottomRight) { }

sealed interface Shape permits Circle, Rectangle { }

// Pattern Matching for switch（Java 21）
String describe(Shape shape) {
    return switch (shape) {
        case Circle(Point(int x, int y), int r) ->
            "Circle at (%d, %d) with radius %d".formatted(x, y, r);

        case Rectangle(Point(int x1, int y1), Point(int x2, int y2)) ->
            "Rectangle from (%d, %d) to (%d, %d)".formatted(x1, y1, x2, y2);
    };
}

// ガード付き
String categorize(Shape shape) {
    return switch (shape) {
        case Circle c when c.radius() > 100 -> "Large circle";
        case Circle c -> "Small circle";
        case Rectangle r -> "Rectangle";
    };
}
```

---

## Sealed Classes

### 定義

```java
// sealed クラス: 許可されたサブクラスを制限
public sealed interface Shape
    permits Circle, Rectangle, Triangle {
}

// final: これ以上継承不可
public final class Circle implements Shape {
    private final double radius;
    // ...
}

// sealed: さらにサブクラスを制限
public sealed class Rectangle implements Shape
    permits Square {
    // ...
}

// non-sealed: 制限を解除
public non-sealed class Triangle implements Shape {
    // 誰でも継承可能
}

// Square は Rectangle の唯一の許可されたサブクラス
public final class Square extends Rectangle {
    // ...
}
```

### パターンマッチングとの組み合わせ

```java
// sealed により、switch が網羅的かコンパイラが検証
double area(Shape shape) {
    return switch (shape) {
        case Circle c -> Math.PI * c.radius() * c.radius();
        case Rectangle r -> r.width() * r.height();
        case Triangle t -> 0.5 * t.base() * t.height();
        // default 不要（全てのケースが網羅されている）
    };
}
```

---

## Sequenced Collections

### 新しいインターフェース階層

```
┌─────────────────────────────────────────────────────────────────────┐
│                 Sequenced Collections (Java 21)                      │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│           SequencedCollection                                        │
│           /              \                                           │
│  SequencedSet        List                                           │
│       |                                                              │
│  SortedSet                                                          │
│       |                                                              │
│  NavigableSet                                                        │
│                                                                      │
│           SequencedMap                                               │
│               |                                                      │
│           SortedMap                                                  │
│               |                                                      │
│           NavigableMap                                               │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### 新しいメソッド

```java
// SequencedCollection のメソッド
List<String> list = new ArrayList<>(List.of("a", "b", "c"));

list.getFirst();         // "a"
list.getLast();          // "c"
list.addFirst("z");      // ["z", "a", "b", "c"]
list.addLast("d");       // ["z", "a", "b", "c", "d"]
list.removeFirst();      // "z" を削除して返す
list.removeLast();       // "d" を削除して返す
list.reversed();         // 逆順のビュー

// SequencedMap のメソッド
LinkedHashMap<String, Integer> map = new LinkedHashMap<>();
map.put("a", 1);
map.put("b", 2);

map.firstEntry();        // a=1
map.lastEntry();         // b=2
map.pollFirstEntry();    // 最初のエントリを削除して返す
map.pollLastEntry();     // 最後のエントリを削除して返す
map.sequencedKeySet();   // キーの順序付きセット
map.sequencedValues();   // 値の順序付きコレクション
map.sequencedEntrySet(); // エントリの順序付きセット
map.reversed();          // 逆順のビュー
```

---

## String Templates（Preview in Java 21）

```java
// プレビュー機能（--enable-preview が必要）
String name = "World";
int year = 2024;

// String Template（プレビュー）
String message = STR."Hello, \{name}! Welcome to \{year}.";
// → "Hello, World! Welcome to 2024."

// 式も埋め込み可能
String calc = STR."2 + 3 = \{2 + 3}";
// → "2 + 3 = 5"

// 複数行
String json = STR."""
    {
        "name": "\{name}",
        "year": \{year}
    }
    """;
```

---

## その他の重要な機能

### Scoped Values（Preview）

```java
// ThreadLocal の代替（Virtual Threads に適した設計）
private static final ScopedValue<String> USER = ScopedValue.newInstance();

void processRequest(String userId) {
    ScopedValue.where(USER, userId).run(() -> {
        // このスコープ内で USER.get() が "userId" を返す
        doSomething();
    });
}

void doSomething() {
    String userId = USER.get();  // スコープ内で有効
}
```

### Structured Concurrency（Preview）

```java
// 複数の並行タスクを構造的に管理
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    Subtask<String> user = scope.fork(() -> fetchUser());
    Subtask<List<Order>> orders = scope.fork(() -> fetchOrders());

    scope.join();           // 両方の完了を待つ
    scope.throwIfFailed();  // どちらかが失敗していれば例外

    // 両方成功した場合のみここに到達
    return new Response(user.get(), orders.get());
}  // スコープ終了時に全サブタスクが完了保証
```

---

## 移行のポイント

### Java 17 → Java 21

```
┌─────────────────────────────────────────────────────────────────────┐
│                 移行時の確認ポイント                                 │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  1. 依存ライブラリの互換性                                          │
│     ・Spring Boot 3.2+ で Virtual Threads サポート                  │
│     ・一部のライブラリは更新が必要                                  │
│                                                                      │
│  2. 廃止された機能                                                   │
│     ・Security Manager（削除予定）                                   │
│     ・Applet API（削除）                                             │
│                                                                      │
│  3. デフォルト値の変更                                               │
│     ・UTF-8 がデフォルトエンコーディング                            │
│     ・一部の GC オプションのデフォルト変更                          │
│                                                                      │
│  4. 新機能の活用                                                     │
│     ・Record でボイラープレート削減                                 │
│     ・Pattern Matching でコード簡潔化                               │
│     ・Virtual Threads でスケーラビリティ向上                        │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## idp-server での活用

| 機能 | 活用シーン |
|-----|-----------|
| Virtual Threads | HTTP リクエスト処理、外部API呼び出し |
| Record | DTO、値オブジェクト |
| Pattern Matching | レスポンス処理、エラーハンドリング |
| Sealed Classes | ドメインモデルの制約表現 |
| Sequenced Collections | 順序付きデータ処理 |

```java
// idp-server での Record 活用例
public record TokenResponse(
    String accessToken,
    String tokenType,
    int expiresIn,
    String refreshToken
) {
    public TokenResponse {
        Objects.requireNonNull(accessToken, "accessToken must not be null");
        Objects.requireNonNull(tokenType, "tokenType must not be null");
    }
}

// Pattern Matching 活用例
public String handleAuthResult(AuthResult result) {
    return switch (result) {
        case Success(var user, var token) ->
            "Authenticated: " + user.name();
        case Failure(var reason) when reason.isRetryable() ->
            "Please retry: " + reason.message();
        case Failure(var reason) ->
            "Authentication failed: " + reason.message();
    };
}
```

---

## まとめ

| 機能 | ポイント |
|-----|---------|
| Virtual Threads | I/O バウンド処理の大幅なスケーラビリティ向上 |
| Record | 不変データクラスの簡潔な定義 |
| Pattern Matching | 型安全で簡潔な分岐処理 |
| Sealed Classes | 継承階層の制御、網羅性チェック |
| Sequenced Collections | 順序付きコレクションの統一 API |

---

## 参考リソース

- [JEP 444: Virtual Threads](https://openjdk.org/jeps/444)
- [JEP 395: Records](https://openjdk.org/jeps/395)
- [JEP 441: Pattern Matching for switch](https://openjdk.org/jeps/441)
- [JEP 409: Sealed Classes](https://openjdk.org/jeps/409)
- [JEP 431: Sequenced Collections](https://openjdk.org/jeps/431)
