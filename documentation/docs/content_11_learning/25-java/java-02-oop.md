# Java オブジェクト指向

Javaのオブジェクト指向プログラミングの基礎を学びます。

---

## なぜオブジェクト指向か

オブジェクト指向の本質は「変更に強い設計」を実現することです。

```java
// 手続き型: データと処理がバラバラ
String userName = "Alice";
int userAge = 25;
boolean isAdult = userAge >= 18;
// userAgeの判定ロジックがあちこちに散らばる...

// オブジェクト指向: データと振る舞いをまとめる
User user = new User("Alice", 25);
boolean isAdult = user.isAdult();
// 判定ロジックの変更はUser内だけで済む
```

**オブジェクト指向の価値:**
- **カプセル化**: 内部の詳細を隠し、変更の影響を局所化
- **ポリモーフィズム**: 同じインターフェースで異なる実装を扱う（依存関係の逆転）
- **責務の分離**: 各クラスが明確な役割を持つ

> **よくある誤解**: 「現実世界のモノをそのままモデル化する」という説明がありますが、これは正確ではありません。現実世界を忠実に模倣すると、むしろ悪い設計になることが多いです。オブジェクト指向は「ソフトウェアの変更容易性」を高めるための技術です。

---

## クラスとオブジェクト

### クラスの構成要素

```java
public class User {
    // ─────────────────────────────────────
    // フィールド（状態）
    // ─────────────────────────────────────
    private final String id;
    private String name;
    private int age;

    // ─────────────────────────────────────
    // コンストラクタ（初期化）
    // ─────────────────────────────────────
    public User(String id, String name, int age) {
        this.id = id;
        this.name = name;
        this.age = age;
    }

    // ─────────────────────────────────────
    // メソッド（振る舞い）
    // ─────────────────────────────────────
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public boolean isAdult() {
        return age >= 18;
    }
}
```

### コンストラクタ

```java
public class Product {
    private final String id;
    private String name;
    private int price;

    // プライマリコンストラクタ
    public Product(String id, String name, int price) {
        this.id = id;
        this.name = name;
        this.price = price;
    }

    // オーバーロード（別のコンストラクタを呼び出す）
    public Product(String id, String name) {
        this(id, name, 0);  // this()で他のコンストラクタを呼ぶ
    }

    // デフォルトコンストラクタ相当
    public Product(String id) {
        this(id, "Unknown", 0);
    }
}
```

### thisキーワード

```java
public class Example {
    private String value;

    public Example(String value) {
        this.value = value;  // this.フィールド = 引数
    }

    public Example withValue(String value) {
        this.value = value;
        return this;  // 自分自身を返す（メソッドチェーン用）
    }

    public void process() {
        helper(this);  // 自分自身を引数として渡す
    }
}
```

---

## カプセル化

### 情報隠蔽

```java
// 悪い例：フィールドが公開されている
public class BadUser {
    public String name;  // 外部から自由に変更できてしまう
    public int age;
}

// 良い例：カプセル化されている
public class GoodUser {
    private String name;
    private int age;

    public GoodUser(String name, int age) {
        setName(name);   // バリデーションを通す
        setAge(age);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name cannot be empty");
        }
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        if (age < 0 || age > 150) {
            throw new IllegalArgumentException("Invalid age: " + age);
        }
        this.age = age;
    }
}
```

### 不変オブジェクト（Immutable）

```java
// 不変クラス
public final class Email {
    private final String value;

    public Email(String value) {
        if (value == null || !value.contains("@")) {
            throw new IllegalArgumentException("Invalid email: " + value);
        }
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    // setterがない → 状態を変更できない

    // 新しい値が必要な場合は新しいインスタンスを返す
    public Email withDomain(String newDomain) {
        String localPart = value.substring(0, value.indexOf('@'));
        return new Email(localPart + "@" + newDomain);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Email email)) return false;
        return value.equals(email.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
```

---

## 継承

### 基本的な継承

```java
// 親クラス（スーパークラス）
public class Animal {
    protected String name;

    public Animal(String name) {
        this.name = name;
    }

    public void eat() {
        System.out.println(name + " is eating");
    }

    public void sleep() {
        System.out.println(name + " is sleeping");
    }
}

// 子クラス（サブクラス）
public class Dog extends Animal {
    private String breed;

    public Dog(String name, String breed) {
        super(name);  // 親のコンストラクタを呼ぶ
        this.breed = breed;
    }

    // メソッドの追加
    public void bark() {
        System.out.println(name + " says: Woof!");
    }

    // メソッドのオーバーライド
    @Override
    public void eat() {
        System.out.println(name + " is eating dog food");
    }
}
```

### super キーワード

```java
public class Child extends Parent {
    private String extra;

    public Child(String name, String extra) {
        super(name);  // 親のコンストラクタ呼び出し（必ず最初）
        this.extra = extra;
    }

    @Override
    public void doSomething() {
        super.doSomething();  // 親のメソッドを呼ぶ
        // 追加の処理
        System.out.println("Child's additional processing");
    }
}
```

### 継承の制限

```java
// finalクラスは継承できない
public final class ImmutableValue {
    // ...
}

// public class ExtendedValue extends ImmutableValue { }  // コンパイルエラー

// finalメソッドはオーバーライドできない
public class Parent {
    public final void criticalMethod() {
        // サブクラスで変更されては困る処理
    }
}
```

---

## 抽象クラス

```java
// 抽象クラス（インスタンス化不可）
public abstract class Shape {
    protected String color;

    public Shape(String color) {
        this.color = color;
    }

    // 具象メソッド（実装あり）
    public String getColor() {
        return color;
    }

    // 抽象メソッド（実装なし、サブクラスで必ず実装）
    public abstract double area();
    public abstract double perimeter();
}

// 具象クラス
public class Circle extends Shape {
    private double radius;

    public Circle(String color, double radius) {
        super(color);
        this.radius = radius;
    }

    @Override
    public double area() {
        return Math.PI * radius * radius;
    }

    @Override
    public double perimeter() {
        return 2 * Math.PI * radius;
    }
}

public class Rectangle extends Shape {
    private double width;
    private double height;

    public Rectangle(String color, double width, double height) {
        super(color);
        this.width = width;
        this.height = height;
    }

    @Override
    public double area() {
        return width * height;
    }

    @Override
    public double perimeter() {
        return 2 * (width + height);
    }
}
```

---

## インターフェース

### 基本的なインターフェース

```java
// インターフェースの定義
public interface Drawable {
    void draw();  // 暗黙的に public abstract
}

public interface Resizable {
    void resize(double factor);
}

// 複数のインターフェースを実装
public class Circle implements Drawable, Resizable {
    private double radius;

    @Override
    public void draw() {
        System.out.println("Drawing circle with radius: " + radius);
    }

    @Override
    public void resize(double factor) {
        radius *= factor;
    }
}
```

### デフォルトメソッド（Java 8以降）

```java
public interface Collection<E> {
    int size();
    boolean isEmpty();

    // デフォルト実装
    default boolean isNotEmpty() {
        return !isEmpty();
    }
}

// 実装クラスはデフォルトメソッドをオーバーライドしなくてもよい
public class MyList<E> implements Collection<E> {
    private List<E> items = new ArrayList<>();

    @Override
    public int size() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        return items.isEmpty();
    }

    // isNotEmpty() はデフォルト実装が使われる
}
```

### staticメソッド

```java
public interface StringUtils {
    // staticメソッド
    static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    static boolean isNotBlank(String s) {
        return !isBlank(s);
    }
}

// 使用
if (StringUtils.isBlank(input)) {
    // ...
}
```

### privateメソッド（Java 9以降）

```java
public interface Validator {
    boolean validate(String input);

    default boolean validateAndLog(String input) {
        boolean result = validate(input);
        log(input, result);  // privateメソッドを呼ぶ
        return result;
    }

    // インターフェース内部でのみ使用
    private void log(String input, boolean result) {
        System.out.println("Validated: " + input + " -> " + result);
    }
}
```

---

## 抽象クラス vs インターフェース

| 特徴 | 抽象クラス | インターフェース |
|-----|----------|---------------|
| 多重継承 | 不可（単一継承のみ） | 可能（複数実装） |
| コンストラクタ | 持てる | 持てない |
| フィールド | インスタンス変数を持てる | 定数（static final）のみ |
| アクセス修飾子 | 任意 | public（暗黙的） |
| 用途 | is-a関係、共通実装の提供 | can-do関係、契約の定義 |

```java
// 抽象クラス：共通の状態と振る舞いを持つ
public abstract class HttpHandler {
    protected final Logger logger;

    protected HttpHandler() {
        this.logger = LoggerFactory.getLogger(getClass());
    }

    public final void handle(Request request, Response response) {
        logger.info("Handling request: {}", request.getPath());
        doHandle(request, response);
    }

    protected abstract void doHandle(Request request, Response response);
}

// インターフェース：能力を定義
public interface Serializable { }
public interface Comparable<T> {
    int compareTo(T other);
}
public interface AutoCloseable {
    void close() throws Exception;
}
```

---

## ポリモーフィズム

### 基本

```java
// 親クラスの型で子クラスのインスタンスを扱う
Animal animal = new Dog("Buddy", "Labrador");
animal.eat();   // Dog#eat() が呼ばれる
// animal.bark();  // コンパイルエラー（Animalにbarkはない）

// インターフェースの型で実装クラスを扱う
List<String> list = new ArrayList<>();  // ArrayListの詳細を隠す
list.add("A");
list.add("B");
```

### 使用例

```java
public class ShapeProcessor {
    public void processAll(List<Shape> shapes) {
        for (Shape shape : shapes) {
            // 実際の型に応じたarea()が呼ばれる
            System.out.println("Area: " + shape.area());
        }
    }
}

// 使用
List<Shape> shapes = List.of(
    new Circle("red", 5.0),
    new Rectangle("blue", 3.0, 4.0)
);

processor.processAll(shapes);
// Area: 78.54...（円）
// Area: 12.0（長方形）
```

### instanceof と型キャスト

```java
// 従来の方法
if (animal instanceof Dog) {
    Dog dog = (Dog) animal;
    dog.bark();
}

// パターンマッチング（Java 16以降、推奨）
if (animal instanceof Dog dog) {
    dog.bark();  // キャスト不要
}

// switch式でのパターンマッチング（Java 21）
String describe(Shape shape) {
    return switch (shape) {
        case Circle c -> "Circle with radius " + c.getRadius();
        case Rectangle r -> "Rectangle " + r.getWidth() + "x" + r.getHeight();
        default -> "Unknown shape";
    };
}
```

---

## 内部クラス

### 非staticな内部クラス

```java
public class Outer {
    private String outerField = "outer";

    public class Inner {
        public void printOuter() {
            // 外部クラスのフィールドにアクセス可能
            System.out.println(outerField);
        }
    }
}

// 使用
Outer outer = new Outer();
Outer.Inner inner = outer.new Inner();
inner.printOuter();
```

### staticな内部クラス

```java
public class Outer {
    private static String staticField = "static";
    private String instanceField = "instance";

    public static class StaticInner {
        public void print() {
            System.out.println(staticField);  // staticフィールドのみアクセス可能
            // System.out.println(instanceField);  // コンパイルエラー
        }
    }
}

// 使用（外部クラスのインスタンス不要）
Outer.StaticInner inner = new Outer.StaticInner();
inner.print();
```

### ローカルクラスと匿名クラス

```java
public class Example {
    public void method() {
        // ローカルクラス（メソッド内で定義）
        class LocalClass {
            void doSomething() { }
        }

        // 匿名クラス（インターフェースや抽象クラスをその場で実装）
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                System.out.println("Running!");
            }
        };

        // ラムダ式で置き換え可能（関数型インターフェースの場合）
        Runnable lambda = () -> System.out.println("Running!");
    }
}
```

---

## Object クラスのメソッド

すべてのクラスは `Object` を継承している。

### equals と hashCode

```java
public class User {
    private String id;
    private String name;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User user)) return false;
        return Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
```

**重要なルール**:
- `equals()` をオーバーライドしたら `hashCode()` も必ずオーバーライドする
- `a.equals(b)` が true なら `a.hashCode() == b.hashCode()` でなければならない

### toString

```java
public class User {
    private String id;
    private String name;

    @Override
    public String toString() {
        return "User{id='" + id + "', name='" + name + "'}";
    }
}
```

### clone

```java
public class User implements Cloneable {
    private String name;
    private List<String> roles;

    @Override
    public User clone() {
        try {
            User cloned = (User) super.clone();
            // 深いコピー（参照型のフィールド）
            cloned.roles = new ArrayList<>(this.roles);
            return cloned;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();  // 到達しない
        }
    }
}
```

---

## 列挙型（enum）

### 基本

```java
public enum Status {
    PENDING,
    ACTIVE,
    INACTIVE,
    DELETED
}

// 使用
Status status = Status.ACTIVE;

// 比較（==でOK）
if (status == Status.ACTIVE) {
    // ...
}

// switch
switch (status) {
    case PENDING -> handlePending();
    case ACTIVE -> handleActive();
    case INACTIVE -> handleInactive();
    case DELETED -> handleDeleted();
}

// 全値の取得
Status[] allStatuses = Status.values();

// 文字列から変換
Status parsed = Status.valueOf("ACTIVE");
```

### フィールドとメソッドを持つenum

```java
public enum HttpStatus {
    OK(200, "OK"),
    CREATED(201, "Created"),
    BAD_REQUEST(400, "Bad Request"),
    UNAUTHORIZED(401, "Unauthorized"),
    NOT_FOUND(404, "Not Found"),
    INTERNAL_SERVER_ERROR(500, "Internal Server Error");

    private final int code;
    private final String message;

    HttpStatus(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public boolean isSuccess() {
        return code >= 200 && code < 300;
    }

    public boolean isError() {
        return code >= 400;
    }

    // コードから検索
    public static HttpStatus fromCode(int code) {
        for (HttpStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown status code: " + code);
    }
}
```

---

## まとめ

| 概念 | ポイント |
|-----|---------|
| カプセル化 | フィールドはprivate、メソッドでアクセス |
| 継承 | `extends`、単一継承のみ |
| 抽象クラス | 共通実装を提供、インスタンス化不可 |
| インターフェース | 契約を定義、複数実装可能 |
| ポリモーフィズム | 親の型で子を扱う、実行時に適切なメソッドが呼ばれる |
| equals/hashCode | セットでオーバーライド |
| enum | 固定の選択肢、型安全 |

---

## 次のステップ

- [ジェネリクス](java-03-generics.md) - 型パラメータ
