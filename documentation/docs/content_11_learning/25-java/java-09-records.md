# Java Records

Java 16で正式導入された不変データクラスを学びます。

---

## Records とは

不変のデータを保持するためのクラスを簡潔に定義できる機能。

```java
// 従来のクラス
public final class User {
    private final String name;
    private final int age;

    public User(String name, int age) {
        this.name = name;
        this.age = age;
    }

    public String getName() { return name; }
    public int getAge() { return age; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User user)) return false;
        return age == user.age && Objects.equals(name, user.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, age);
    }

    @Override
    public String toString() {
        return "User[name=" + name + ", age=" + age + "]";
    }
}

// Record（1行で同等の機能）
public record User(String name, int age) {}
```

---

## 基本構文

```java
// 基本形
public record Point(int x, int y) {}

// 使用
Point p = new Point(10, 20);
int x = p.x();  // getter は name()、getName() ではない
int y = p.y();

// equals, hashCode, toString は自動生成
Point p1 = new Point(10, 20);
Point p2 = new Point(10, 20);
p1.equals(p2);  // true
p1.hashCode() == p2.hashCode();  // true
p1.toString();  // "Point[x=10, y=20]"
```

---

## コンパクトコンストラクタ

バリデーションを追加するための構文。

```java
public record Email(String value) {
    // コンパクトコンストラクタ（引数なし、代入は自動）
    public Email {
        if (value == null || !value.contains("@")) {
            throw new IllegalArgumentException("Invalid email: " + value);
        }
        // this.value = value; は自動的に行われる
    }
}

// 値の正規化
public record Name(String value) {
    public Name {
        value = value.trim();  // 代入前に値を変更可能
    }
}

// 使用
Email email = new Email("test@example.com");  // OK
Email invalid = new Email("invalid");  // IllegalArgumentException
```

### 標準コンストラクタ

明示的なコンストラクタも定義可能。

```java
public record Range(int start, int end) {
    // 標準コンストラクタ
    public Range(int start, int end) {
        if (start > end) {
            throw new IllegalArgumentException("start must be <= end");
        }
        this.start = start;
        this.end = end;
    }
}
```

---

## メソッドの追加

```java
public record Rectangle(int width, int height) {
    // インスタンスメソッド
    public int area() {
        return width * height;
    }

    public int perimeter() {
        return 2 * (width + height);
    }

    public boolean isSquare() {
        return width == height;
    }

    // staticメソッド
    public static Rectangle square(int size) {
        return new Rectangle(size, size);
    }
}

// 使用
Rectangle rect = new Rectangle(10, 20);
int area = rect.area();  // 200

Rectangle square = Rectangle.square(5);
```

---

## アクセサのオーバーライド

```java
public record User(String name, int age) {
    // アクセサをオーバーライド
    @Override
    public String name() {
        return name.toUpperCase();  // 加工して返す
    }

    // 防御的コピー
    public record Container(List<String> items) {
        @Override
        public List<String> items() {
            return List.copyOf(items);  // 不変のコピーを返す
        }
    }
}
```

---

## インターフェースの実装

```java
public interface Identifiable {
    String getId();
}

public record User(String id, String name) implements Identifiable {
    @Override
    public String getId() {
        return id;
    }
}

// Comparable の実装
public record Person(String name, int age) implements Comparable<Person> {
    @Override
    public int compareTo(Person other) {
        return Integer.compare(this.age, other.age);
    }
}
```

---

## ネストしたRecord

```java
public record Address(String city, String street) {}

public record User(String name, Address address) {}

// 使用
User user = new User("Alice", new Address("Tokyo", "Shibuya"));
String city = user.address().city();  // "Tokyo"
```

---

## ローカルRecord

メソッド内でRecordを定義できる。

```java
public List<String> processData(List<User> users) {
    // ローカルRecord
    record UserWithScore(User user, int score) {}

    return users.stream()
        .map(user -> new UserWithScore(user, calculateScore(user)))
        .sorted(Comparator.comparing(UserWithScore::score).reversed())
        .map(uws -> uws.user().name())
        .toList();
}
```

---

## Record と JSON

Jackson などのライブラリとの連携。

```java
public record User(
    @JsonProperty("user_name") String name,
    @JsonProperty("user_age") int age
) {}

// シリアライズ/デシリアライズ
ObjectMapper mapper = new ObjectMapper();
String json = mapper.writeValueAsString(new User("Alice", 25));
User user = mapper.readValue(json, User.class);
```

---

## Record のコピー

Record は不変なので、変更したい場合は新しいインスタンスを作る。

```java
public record User(String name, int age) {
    // withメソッドを自分で定義
    public User withName(String newName) {
        return new User(newName, this.age);
    }

    public User withAge(int newAge) {
        return new User(this.name, newAge);
    }
}

// 使用
User user = new User("Alice", 25);
User renamed = user.withName("Bob");
User aged = user.withAge(26);
```

---

## Record の制限

```java
// NG: 継承できない（暗黙的にfinal）
// public record Child(String name) extends Parent {}

// NG: フィールドを追加できない
// public record User(String name) {
//     private int age;  // コンパイルエラー
// }

// NG: 可変フィールド（setter）を持てない
// Record のフィールドは暗黙的に final

// OK: staticフィールドは追加可能
public record User(String name) {
    private static final int MAX_NAME_LENGTH = 100;
}
```

---

## 使い分け

### Record を使うべき場合

- DTO（Data Transfer Object）
- 値オブジェクト（Value Object）
- 一時的なデータの集約
- メソッドの戻り値（複数の値を返す）
- Map のキー

```java
// DTO
public record UserResponse(String id, String name, String email) {}

// 値オブジェクト
public record Money(BigDecimal amount, Currency currency) {}

// 複数の値を返す
public record ParseResult(User user, List<String> warnings) {}

// Mapのキー
Map<Coordinate, Tile> tiles = new HashMap<>();
record Coordinate(int x, int y) {}
```

### Record を使わないべき場合

- 可変の状態が必要
- 継承が必要
- 複雑なビジネスロジックを持つエンティティ
- JPA エンティティ（一部制限あり）

---

## Pattern Matching との組み合わせ

Java 21 では Record Pattern が使える。

```java
public sealed interface Shape permits Circle, Rectangle {}
public record Circle(double radius) implements Shape {}
public record Rectangle(double width, double height) implements Shape {}

// Record Pattern
public double area(Shape shape) {
    return switch (shape) {
        case Circle(var radius) -> Math.PI * radius * radius;
        case Rectangle(var w, var h) -> w * h;
    };
}

// ネストしたRecord Pattern
public record Point(int x, int y) {}
public record Line(Point start, Point end) {}

public double length(Line line) {
    return switch (line) {
        case Line(Point(var x1, var y1), Point(var x2, var y2)) ->
            Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    };
}
```

---

## 実践的なパターン

### Builder パターンとの組み合わせ

```java
public record User(String name, int age, String email) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private int age;
        private String email;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder age(int age) {
            this.age = age;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public User build() {
            return new User(name, age, email);
        }
    }
}

// 使用
User user = User.builder()
    .name("Alice")
    .age(25)
    .email("alice@example.com")
    .build();
```

### Result 型

```java
public sealed interface Result<T> {
    record Success<T>(T value) implements Result<T> {}
    record Failure<T>(String error) implements Result<T> {}

    default T getOrThrow() {
        return switch (this) {
            case Success(var value) -> value;
            case Failure(var error) -> throw new RuntimeException(error);
        };
    }
}

// 使用
Result<User> result = findUser(id);
User user = result.getOrThrow();
```

---

## まとめ

| 特徴 | 説明 |
|-----|------|
| 不変 | フィールドは final |
| 簡潔 | ボイラープレートを削減 |
| equals/hashCode | 自動生成 |
| toString | 自動生成 |
| アクセサ | `name()` 形式（`getName()` ではない） |
| バリデーション | コンパクトコンストラクタ |
| 継承 | 不可（暗黙的に final） |

---

## 次のステップ

- [Sealed Classes](java-10-sealed.md) - 継承の制限
