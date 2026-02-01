# Java ジェネリクス

型安全なコードを書くためのジェネリクスを学びます。

---

## なぜジェネリクスが必要か

### ジェネリクスなしの問題

```java
// ジェネリクスを使わない場合
List list = new ArrayList();
list.add("hello");
list.add(123);  // 異なる型も入れられてしまう

// 取り出すときにキャストが必要
String s = (String) list.get(0);  // OK
String s2 = (String) list.get(1); // ClassCastException! （実行時エラー）
```

### ジェネリクスありの解決

```java
// ジェネリクスを使う場合
List<String> list = new ArrayList<>();
list.add("hello");
// list.add(123);  // コンパイルエラー！（コンパイル時に検出）

// キャスト不要
String s = list.get(0);  // 型安全
```

---

## 基本構文

### ジェネリッククラス

```java
// 型パラメータ T を持つクラス
public class Box<T> {
    private T content;

    public void set(T content) {
        this.content = content;
    }

    public T get() {
        return content;
    }
}

// 使用
Box<String> stringBox = new Box<>();
stringBox.set("Hello");
String value = stringBox.get();

Box<Integer> intBox = new Box<>();
intBox.set(42);
Integer number = intBox.get();
```

### 複数の型パラメータ

```java
public class Pair<K, V> {
    private final K key;
    private final V value;

    public Pair(K key, V value) {
        this.key = key;
        this.value = value;
    }

    public K getKey() { return key; }
    public V getValue() { return value; }
}

// 使用
Pair<String, Integer> pair = new Pair<>("age", 25);
String key = pair.getKey();     // "age"
Integer value = pair.getValue(); // 25
```

### ジェネリックメソッド

```java
public class Utils {
    // メソッドレベルの型パラメータ
    public static <T> T firstOrNull(List<T> list) {
        return list.isEmpty() ? null : list.get(0);
    }

    // 複数の型パラメータ
    public static <K, V> Map<K, V> singletonMap(K key, V value) {
        Map<K, V> map = new HashMap<>();
        map.put(key, value);
        return map;
    }
}

// 使用（型は推論される）
String first = Utils.firstOrNull(List.of("a", "b", "c"));
Map<String, Integer> map = Utils.singletonMap("count", 10);

// 明示的に型を指定
String first2 = Utils.<String>firstOrNull(List.of("a", "b"));
```

### ジェネリックインターフェース

```java
public interface Repository<T, ID> {
    T findById(ID id);
    List<T> findAll();
    T save(T entity);
    void delete(T entity);
}

// 実装
public class UserRepository implements Repository<User, String> {
    @Override
    public User findById(String id) {
        // ...
    }

    @Override
    public List<User> findAll() {
        // ...
    }

    @Override
    public User save(User entity) {
        // ...
    }

    @Override
    public void delete(User entity) {
        // ...
    }
}
```

---

## 型パラメータの命名規則

| 名前 | 意味 | 例 |
|-----|------|-----|
| T | Type（一般的な型） | `Box<T>` |
| E | Element（要素） | `List<E>` |
| K | Key（キー） | `Map<K, V>` |
| V | Value（値） | `Map<K, V>` |
| N | Number（数値） | `Calculator<N>` |
| R | Result（結果） | `Function<T, R>` |

---

## 境界（Bounds）

### 上限境界（extends）

```java
// T は Number またはそのサブクラスのみ
public class NumberBox<T extends Number> {
    private T value;

    public double doubleValue() {
        return value.doubleValue();  // Numberのメソッドが使える
    }
}

// 使用
NumberBox<Integer> intBox = new NumberBox<>();
NumberBox<Double> doubleBox = new NumberBox<>();
// NumberBox<String> stringBox = new NumberBox<>();  // コンパイルエラー

// 複数の境界
public class Example<T extends Comparable<T> & Serializable> {
    // TはComparableかつSerializableを実装していなければならない
}
```

### メソッドでの境界

```java
// Number以下の型のリストを受け取る
public static <T extends Number> double sum(List<T> numbers) {
    double total = 0;
    for (T number : numbers) {
        total += number.doubleValue();
    }
    return total;
}

// 使用
sum(List.of(1, 2, 3));           // Integer
sum(List.of(1.5, 2.5, 3.5));     // Double
sum(List.of(1L, 2L, 3L));        // Long
```

---

## ワイルドカード

### 非境界ワイルドカード（?）

```java
// 任意の型のListを受け取る（読み取り専用）
public static void printList(List<?> list) {
    for (Object item : list) {
        System.out.println(item);
    }
}

// 使用
printList(List.of("a", "b", "c"));
printList(List.of(1, 2, 3));
```

### 上限境界ワイルドカード（? extends T）

```java
// Numberまたはそのサブクラスのリスト（読み取り専用）
public static double sum(List<? extends Number> numbers) {
    double total = 0;
    for (Number n : numbers) {
        total += n.doubleValue();
    }
    return total;
}

// 使用
sum(List.of(1, 2, 3));           // List<Integer>
sum(List.of(1.5, 2.5));          // List<Double>
```

### 下限境界ワイルドカード（? super T）

```java
// Integerまたはその親クラスのリスト（書き込み可能）
public static void addNumbers(List<? super Integer> list) {
    list.add(1);
    list.add(2);
    list.add(3);
}

// 使用
List<Integer> intList = new ArrayList<>();
List<Number> numList = new ArrayList<>();
List<Object> objList = new ArrayList<>();

addNumbers(intList);  // OK
addNumbers(numList);  // OK
addNumbers(objList);  // OK
```

### PECS原則

**Producer Extends, Consumer Super**

```java
// Producer（値を生産する）→ extends
public static <T> void copy(
    List<? extends T> source,  // sourceから読み取る（Producer）
    List<? super T> dest       // destに書き込む（Consumer）
) {
    for (T item : source) {
        dest.add(item);
    }
}

// 使用
List<Integer> integers = List.of(1, 2, 3);
List<Number> numbers = new ArrayList<>();
copy(integers, numbers);  // Integer → Number
```

---

## 型消去（Type Erasure）

### 型消去とは

コンパイル後、ジェネリクスの型情報は消える。

```java
// コンパイル前
public class Box<T> {
    private T value;
    public T get() { return value; }
}

// コンパイル後（イメージ）
public class Box {
    private Object value;
    public Object get() { return value; }
}
```

### 型消去の影響

```java
// 実行時に型パラメータは分からない
List<String> strings = new ArrayList<>();
List<Integer> integers = new ArrayList<>();

// 両方とも同じクラス
strings.getClass() == integers.getClass()  // true

// instanceof で型パラメータは使えない
// if (list instanceof List<String>) { }  // コンパイルエラー

// 型パラメータで配列は作れない
// T[] array = new T[10];  // コンパイルエラー
```

### リフレクションでの型情報取得

```java
// フィールドの型情報は保持される
public class Example {
    private List<String> strings;
}

// リフレクションで取得可能
Field field = Example.class.getDeclaredField("strings");
ParameterizedType type = (ParameterizedType) field.getGenericType();
Type[] typeArgs = type.getActualTypeArguments();
// typeArgs[0] は String.class
```

---

## 実践的なパターン

### ジェネリックなファクトリ

```java
public interface Factory<T> {
    T create();
}

public class UserFactory implements Factory<User> {
    @Override
    public User create() {
        return new User();
    }
}

// 使用
Factory<User> factory = new UserFactory();
User user = factory.create();
```

### ジェネリックなResult型

```java
public sealed interface Result<T> {
    record Success<T>(T value) implements Result<T> {}
    record Failure<T>(String error) implements Result<T> {}

    default T getOrThrow() {
        return switch (this) {
            case Success<T> s -> s.value();
            case Failure<T> f -> throw new RuntimeException(f.error());
        };
    }

    default T getOrElse(T defaultValue) {
        return switch (this) {
            case Success<T> s -> s.value();
            case Failure<T> f -> defaultValue;
        };
    }

    static <T> Result<T> success(T value) {
        return new Success<>(value);
    }

    static <T> Result<T> failure(String error) {
        return new Failure<>(error);
    }
}

// 使用
Result<User> result = userService.findById(id);
User user = result.getOrThrow();
```

### ジェネリックなBuilder

```java
public abstract class Builder<T, B extends Builder<T, B>> {
    @SuppressWarnings("unchecked")
    protected B self() {
        return (B) this;
    }

    public abstract T build();
}

public class UserBuilder extends Builder<User, UserBuilder> {
    private String name;
    private int age;

    public UserBuilder name(String name) {
        this.name = name;
        return self();
    }

    public UserBuilder age(int age) {
        this.age = age;
        return self();
    }

    @Override
    public User build() {
        return new User(name, age);
    }
}

// 使用
User user = new UserBuilder()
    .name("Alice")
    .age(25)
    .build();
```

### 型トークン

```java
// 型情報を保持するためのパターン
public class TypeReference<T> {
    private final Type type;

    protected TypeReference() {
        Type superClass = getClass().getGenericSuperclass();
        this.type = ((ParameterizedType) superClass).getActualTypeArguments()[0];
    }

    public Type getType() {
        return type;
    }
}

// 使用（匿名クラスで具体的な型を渡す）
TypeReference<List<String>> ref = new TypeReference<>() {};
Type type = ref.getType();  // List<String> の型情報
```

---

## よくある間違い

### 間違い1: raw型の使用

```java
// NG: raw型（ジェネリクスを使わない）
List list = new ArrayList();  // 警告が出る

// OK: 型パラメータを指定
List<String> list = new ArrayList<>();
```

### 間違い2: Object との混同

```java
// List<Object> と List<?> は異なる
List<Object> objectList = new ArrayList<>();
objectList.add("string");  // OK
objectList.add(123);       // OK

List<?> wildcardList = new ArrayList<String>();
// wildcardList.add("string");  // コンパイルエラー（nullのみ追加可能）
Object item = wildcardList.get(0);  // 読み取りはOK
```

### 間違い3: 継承関係の誤解

```java
// Integer は Number のサブクラスだが
// List<Integer> は List<Number> のサブタイプではない

List<Number> numbers = new ArrayList<>();
// List<Number> numbers = new ArrayList<Integer>();  // コンパイルエラー

// ワイルドカードを使う
List<? extends Number> numbers2 = new ArrayList<Integer>();  // OK
```

---

## まとめ

| 概念 | 構文 | 用途 |
|-----|------|------|
| ジェネリッククラス | `class Box<T>` | 型安全なコンテナ |
| ジェネリックメソッド | `<T> T method(T arg)` | 型に依存しない処理 |
| 上限境界 | `<T extends Number>` | 特定の型以下に制限 |
| 下限境界 | `<? super Integer>` | 特定の型以上を受け入れ |
| ワイルドカード | `<?>` | 任意の型 |
| PECS | extends=読み取り、super=書き込み | 柔軟なAPI設計 |

---

## 次のステップ

- [コレクション](java-04-collections.md) - List、Set、Map
