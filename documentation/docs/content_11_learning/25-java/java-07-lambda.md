# Java ラムダ式

関数型インターフェースとラムダ式を学びます。

---

## ラムダ式とは

匿名関数を簡潔に書くための構文。

```java
// 匿名クラス（従来）
Comparator<String> comparator = new Comparator<String>() {
    @Override
    public int compare(String s1, String s2) {
        return s1.length() - s2.length();
    }
};

// ラムダ式
Comparator<String> comparator = (s1, s2) -> s1.length() - s2.length();
```

---

## 基本構文

```java
// 完全な形
(Type param1, Type param2) -> { statements; return value; }

// 型推論（型は省略可能）
(param1, param2) -> { statements; return value; }

// 単一式（波括弧とreturnを省略）
(param1, param2) -> expression

// 引数が1つ（括弧を省略）
param -> expression

// 引数なし
() -> expression
```

### 例

```java
// 引数なし
Runnable r = () -> System.out.println("Hello");

// 引数1つ
Consumer<String> c = s -> System.out.println(s);

// 引数2つ
BiFunction<Integer, Integer, Integer> add = (a, b) -> a + b;

// 複数文
Function<String, Integer> parse = s -> {
    System.out.println("Parsing: " + s);
    return Integer.parseInt(s);
};
```

---

## 関数型インターフェース

抽象メソッドを1つだけ持つインターフェース。`@FunctionalInterface` で明示。

### 標準の関数型インターフェース

| インターフェース | メソッド | 用途 |
|---------------|---------|------|
| `Function<T, R>` | `R apply(T t)` | 変換 |
| `Consumer<T>` | `void accept(T t)` | 消費（副作用） |
| `Supplier<T>` | `T get()` | 生成 |
| `Predicate<T>` | `boolean test(T t)` | 判定 |
| `BiFunction<T, U, R>` | `R apply(T t, U u)` | 2引数の変換 |
| `BiConsumer<T, U>` | `void accept(T t, U u)` | 2引数の消費 |
| `BiPredicate<T, U>` | `boolean test(T t, U u)` | 2引数の判定 |
| `UnaryOperator<T>` | `T apply(T t)` | 同じ型への変換 |
| `BinaryOperator<T>` | `T apply(T t1, T t2)` | 2引数の同じ型への変換 |

### Function

```java
Function<String, Integer> length = s -> s.length();
int len = length.apply("Hello");  // 5

// 合成
Function<String, String> upper = String::toUpperCase;
Function<String, Integer> upperThenLength = upper.andThen(length);
int result = upperThenLength.apply("hello");  // 5

// compose（逆順）
Function<Integer, String> intToString = Object::toString;
Function<Integer, Integer> stringLength = intToString.andThen(length);
```

### Consumer

```java
Consumer<String> print = s -> System.out.println(s);
print.accept("Hello");

// チェーン
Consumer<String> printUpper = s -> System.out.println(s.toUpperCase());
Consumer<String> printBoth = print.andThen(printUpper);
printBoth.accept("hello");
// hello
// HELLO

// Stream での使用
names.forEach(print);
```

### Supplier

```java
Supplier<String> supplier = () -> "Hello";
String value = supplier.get();  // "Hello"

// 遅延評価
Supplier<ExpensiveObject> lazy = () -> new ExpensiveObject();
// この時点ではインスタンス化されない
ExpensiveObject obj = lazy.get();  // ここで初めてインスタンス化
```

### Predicate

```java
Predicate<String> isEmpty = s -> s.isEmpty();
boolean result = isEmpty.test("");  // true

// 合成
Predicate<String> isNotEmpty = isEmpty.negate();
Predicate<String> isShort = s -> s.length() < 5;
Predicate<String> isShortAndNotEmpty = isNotEmpty.and(isShort);

// Stream での使用
List<String> nonEmpty = names.stream()
    .filter(isNotEmpty)
    .toList();
```

### プリミティブ特化型

ボクシングを避けるための特化型。

```java
IntFunction<String> intToString = i -> "Number: " + i;
IntConsumer printInt = System.out::println;
IntSupplier randomInt = () -> (int) (Math.random() * 100);
IntPredicate isEven = n -> n % 2 == 0;
IntUnaryOperator doubleIt = n -> n * 2;
IntBinaryOperator add = (a, b) -> a + b;

// ToIntFunction: 任意の型からintへ
ToIntFunction<String> length = String::length;
```

---

## メソッド参照

ラムダ式をさらに簡潔に書く方法。

### 静的メソッド参照

```java
// ClassName::staticMethod
Function<String, Integer> parse = Integer::parseInt;
// 等価なラムダ: s -> Integer.parseInt(s)

List<Integer> numbers = strings.stream()
    .map(Integer::parseInt)
    .toList();
```

### インスタンスメソッド参照（特定のオブジェクト）

```java
// instance::method
String prefix = "Hello, ";
Function<String, String> greeter = prefix::concat;
// 等価なラムダ: s -> prefix.concat(s)

System.out::println  // System.outのprintlnメソッド
```

### インスタンスメソッド参照（任意のオブジェクト）

```java
// ClassName::instanceMethod
Function<String, Integer> length = String::length;
// 等価なラムダ: s -> s.length()

Function<String, String> upper = String::toUpperCase;
// 等価なラムダ: s -> s.toUpperCase()

BiFunction<String, String, Boolean> startsWith = String::startsWith;
// 等価なラムダ: (s, prefix) -> s.startsWith(prefix)
```

### コンストラクタ参照

```java
// ClassName::new
Supplier<List<String>> listFactory = ArrayList::new;
List<String> list = listFactory.get();

Function<String, User> userFactory = User::new;
User user = userFactory.apply("Alice");

// 配列
IntFunction<int[]> arrayFactory = int[]::new;
int[] array = arrayFactory.apply(10);  // new int[10]
```

---

## カスタム関数型インターフェース

```java
@FunctionalInterface
public interface Validator<T> {
    boolean validate(T value);

    // デフォルトメソッド（合成用）
    default Validator<T> and(Validator<T> other) {
        return value -> this.validate(value) && other.validate(value);
    }

    default Validator<T> or(Validator<T> other) {
        return value -> this.validate(value) || other.validate(value);
    }

    default Validator<T> negate() {
        return value -> !this.validate(value);
    }
}

// 使用
Validator<String> notEmpty = s -> !s.isEmpty();
Validator<String> notTooLong = s -> s.length() <= 100;
Validator<String> valid = notEmpty.and(notTooLong);

boolean isValid = valid.validate("Hello");  // true
```

### 例外を投げる関数型インターフェース

```java
@FunctionalInterface
public interface ThrowingFunction<T, R, E extends Exception> {
    R apply(T t) throws E;

    static <T, R, E extends Exception> Function<T, R> unchecked(
            ThrowingFunction<T, R, E> f) {
        return t -> {
            try {
                return f.apply(t);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }
}

// 使用
List<String> lines = paths.stream()
    .map(ThrowingFunction.unchecked(Files::readString))
    .toList();
```

---

## クロージャ

ラムダ式は外部の変数をキャプチャできる。

```java
String prefix = "Hello, ";

// prefixをキャプチャ
Function<String, String> greeter = name -> prefix + name;
greeter.apply("Alice");  // "Hello, Alice"
```

### 実質的にfinal

キャプチャする変数は final か実質的に final（一度も変更されない）でなければならない。

```java
String message = "Hello";
// message = "Hi";  // これがあるとコンパイルエラー

Runnable r = () -> System.out.println(message);

// NG: 変数を変更しようとする
int count = 0;
// names.forEach(name -> count++);  // コンパイルエラー

// OK: 配列やコンテナを使う
int[] counter = {0};
names.forEach(name -> counter[0]++);

// OK: AtomicInteger を使う
AtomicInteger atomicCount = new AtomicInteger(0);
names.forEach(name -> atomicCount.incrementAndGet());
```

---

## 実践的なパターン

### 遅延初期化

```java
public class LazyValue<T> {
    private Supplier<T> supplier;
    private T value;
    private boolean initialized = false;

    public LazyValue(Supplier<T> supplier) {
        this.supplier = supplier;
    }

    public T get() {
        if (!initialized) {
            value = supplier.get();
            initialized = true;
            supplier = null;  // GC対象に
        }
        return value;
    }
}

// 使用
LazyValue<ExpensiveObject> lazy = new LazyValue<>(ExpensiveObject::new);
// 最初のget()呼び出し時に初期化される
```

### コールバック

```java
public void processAsync(String data, Consumer<String> onSuccess, Consumer<Exception> onError) {
    executor.submit(() -> {
        try {
            String result = process(data);
            onSuccess.accept(result);
        } catch (Exception e) {
            onError.accept(e);
        }
    });
}

// 使用
processAsync(
    "data",
    result -> System.out.println("Success: " + result),
    error -> System.err.println("Error: " + error.getMessage())
);
```

### ファクトリ

```java
public class UserFactory {
    private final Map<String, Supplier<User>> factories = new HashMap<>();

    public void register(String type, Supplier<User> factory) {
        factories.put(type, factory);
    }

    public User create(String type) {
        Supplier<User> factory = factories.get(type);
        if (factory == null) {
            throw new IllegalArgumentException("Unknown type: " + type);
        }
        return factory.get();
    }
}

// 使用
UserFactory factory = new UserFactory();
factory.register("admin", AdminUser::new);
factory.register("guest", GuestUser::new);

User admin = factory.create("admin");
```

### 戦略パターン

```java
public class Processor {
    private Function<String, String> strategy;

    public Processor(Function<String, String> strategy) {
        this.strategy = strategy;
    }

    public String process(String input) {
        return strategy.apply(input);
    }
}

// 使用
Processor upperProcessor = new Processor(String::toUpperCase);
Processor reverseProcessor = new Processor(s -> new StringBuilder(s).reverse().toString());

upperProcessor.process("hello");   // "HELLO"
reverseProcessor.process("hello"); // "olleh"
```

### 条件付き実行

```java
public void executeIf(boolean condition, Runnable action) {
    if (condition) {
        action.run();
    }
}

public <T> T computeIf(boolean condition, Supplier<T> supplier, T defaultValue) {
    return condition ? supplier.get() : defaultValue;
}

// 使用
executeIf(user.isAdmin(), () -> log("Admin access"));
String name = computeIf(user != null, user::getName, "Anonymous");
```

---

## パフォーマンスの考慮

### ラムダ vs メソッド参照

```java
// 同等のパフォーマンス
names.forEach(s -> System.out.println(s));
names.forEach(System.out::println);  // より簡潔
```

### ラムダのキャプチャ

```java
// キャプチャなし（同じインスタンスが再利用される）
Function<String, Integer> length = String::length;

// キャプチャあり（毎回新しいインスタンスが作られる可能性）
String prefix = "Hello, ";
Function<String, String> greeter = name -> prefix + name;
```

### プリミティブ特化型を使う

```java
// NG: ボクシングが発生
Function<Integer, Integer> doubleIt = n -> n * 2;

// OK: プリミティブ特化型
IntUnaryOperator doubleIt = n -> n * 2;
```

---

## まとめ

| 概念 | 説明 |
|-----|------|
| ラムダ式 | `(params) -> expression` |
| 関数型インターフェース | 抽象メソッド1つのインターフェース |
| メソッド参照 | `Class::method` |
| クロージャ | 外部変数のキャプチャ（実質final） |
| 標準インターフェース | Function, Consumer, Supplier, Predicate |

---

## 次のステップ

- [並行処理](java-08-concurrency.md) - Thread, ExecutorService
