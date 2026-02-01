# Java Sealed Classes

Java 17で正式導入された継承を制限するクラスを学びます。

---

## Sealed Classes とは

継承できるクラス/インターフェースを明示的に指定する機能。

```java
// 従来: 誰でも継承可能
public abstract class Shape { }
// どこからでも extends Shape できてしまう

// Sealed: 継承を制限
public sealed class Shape permits Circle, Rectangle, Triangle { }
// Circle, Rectangle, Triangle のみが継承可能
```

---

## 基本構文

```java
// sealed で宣言、permits で許可するサブクラスを列挙
public sealed class Shape permits Circle, Rectangle {
    // 共通の実装
}

// サブクラスは final, sealed, non-sealed のいずれかが必須
public final class Circle extends Shape {
    private final double radius;
    public Circle(double radius) { this.radius = radius; }
}

public final class Rectangle extends Shape {
    private final double width, height;
    public Rectangle(double width, double height) {
        this.width = width;
        this.height = height;
    }
}
```

---

## サブクラスの修飾子

### final

これ以上の継承を禁止。

```java
public sealed class Animal permits Dog, Cat {}

public final class Dog extends Animal {}
// Dog を継承することはできない
```

### sealed

さらに継承を制限付きで許可。

```java
public sealed class Animal permits Mammal, Bird {}

public sealed class Mammal extends Animal permits Dog, Cat {}

public final class Dog extends Mammal {}
public final class Cat extends Mammal {}

public final class Bird extends Animal {}
```

### non-sealed

継承の制限を解除（オープンにする）。

```java
public sealed class Vehicle permits Car, Motorcycle, CustomVehicle {}

public final class Car extends Vehicle {}
public final class Motorcycle extends Vehicle {}

// non-sealed: 誰でも継承可能
public non-sealed class CustomVehicle extends Vehicle {}

// CustomVehicle は誰でも継承できる
public class ElectricScooter extends CustomVehicle {}
```

---

## Sealed Interface

```java
public sealed interface Result<T> permits Success, Failure {
    T getOrThrow();
}

public record Success<T>(T value) implements Result<T> {
    @Override
    public T getOrThrow() {
        return value;
    }
}

public record Failure<T>(String error) implements Result<T> {
    @Override
    public T getOrThrow() {
        throw new RuntimeException(error);
    }
}
```

---

## permits の省略

サブクラスが同じファイル内にある場合、`permits` を省略できる。

```java
// 同一ファイル内
public sealed interface Shape {
    // permits は暗黙的
}

final class Circle implements Shape {
    private final double radius;
    Circle(double radius) { this.radius = radius; }
}

final class Rectangle implements Shape {
    private final double width, height;
    Rectangle(double width, double height) {
        this.width = width;
        this.height = height;
    }
}
```

---

## Pattern Matching との組み合わせ

Sealed Classes の真価は Pattern Matching との組み合わせで発揮される。

### switch 式での網羅性チェック

```java
public sealed interface Shape permits Circle, Rectangle, Triangle {}
public record Circle(double radius) implements Shape {}
public record Rectangle(double width, double height) implements Shape {}
public record Triangle(double base, double height) implements Shape {}

// switch で全ケースをカバー → default 不要
public double area(Shape shape) {
    return switch (shape) {
        case Circle c -> Math.PI * c.radius() * c.radius();
        case Rectangle r -> r.width() * r.height();
        case Triangle t -> 0.5 * t.base() * t.height();
        // default 不要！コンパイラが網羅性を保証
    };
}

// 新しいサブクラスを追加すると、switch がコンパイルエラーになる
// → 漏れを防げる
```

### Record Pattern との組み合わせ

```java
public double area(Shape shape) {
    return switch (shape) {
        case Circle(var r) -> Math.PI * r * r;
        case Rectangle(var w, var h) -> w * h;
        case Triangle(var b, var h) -> 0.5 * b * h;
    };
}
```

---

## 実践的なパターン

### 代数的データ型（ADT）

```java
// 結果型
public sealed interface Result<T> {
    record Success<T>(T value) implements Result<T> {}
    record Failure<T>(Exception error) implements Result<T> {}
}

// 使用
public Result<User> findUser(String id) {
    try {
        User user = repository.findById(id);
        return new Result.Success<>(user);
    } catch (Exception e) {
        return new Result.Failure<>(e);
    }
}

// パターンマッチング
Result<User> result = findUser("123");
String message = switch (result) {
    case Result.Success(var user) -> "Found: " + user.name();
    case Result.Failure(var error) -> "Error: " + error.getMessage();
};
```

### 状態マシン

```java
public sealed interface OrderState {
    record Pending() implements OrderState {}
    record Confirmed(String confirmationCode) implements OrderState {}
    record Shipped(String trackingNumber) implements OrderState {}
    record Delivered(LocalDateTime deliveredAt) implements OrderState {}
    record Cancelled(String reason) implements OrderState {}
}

public class Order {
    private OrderState state = new OrderState.Pending();

    public void confirm(String code) {
        if (!(state instanceof OrderState.Pending)) {
            throw new IllegalStateException("Cannot confirm order in state: " + state);
        }
        state = new OrderState.Confirmed(code);
    }

    public String getStatusDescription() {
        return switch (state) {
            case OrderState.Pending() -> "注文は処理待ちです";
            case OrderState.Confirmed(var code) -> "注文が確定しました: " + code;
            case OrderState.Shipped(var tracking) -> "発送済み: " + tracking;
            case OrderState.Delivered(var at) -> "配達完了: " + at;
            case OrderState.Cancelled(var reason) -> "キャンセル: " + reason;
        };
    }
}
```

### イベント

```java
public sealed interface DomainEvent {
    LocalDateTime occurredAt();

    record UserCreated(String userId, String email, LocalDateTime occurredAt)
        implements DomainEvent {}

    record UserEmailChanged(String userId, String oldEmail, String newEmail, LocalDateTime occurredAt)
        implements DomainEvent {}

    record UserDeleted(String userId, LocalDateTime occurredAt)
        implements DomainEvent {}
}

// イベントハンドラ
public void handle(DomainEvent event) {
    switch (event) {
        case DomainEvent.UserCreated e -> sendWelcomeEmail(e.email());
        case DomainEvent.UserEmailChanged e -> sendEmailChangeNotification(e.oldEmail(), e.newEmail());
        case DomainEvent.UserDeleted e -> cleanupUserData(e.userId());
    }
}
```

### コマンド

```java
public sealed interface Command {
    record CreateUser(String name, String email) implements Command {}
    record UpdateUser(String id, String name) implements Command {}
    record DeleteUser(String id) implements Command {}
}

public Result<User> execute(Command command) {
    return switch (command) {
        case Command.CreateUser(var name, var email) -> createUser(name, email);
        case Command.UpdateUser(var id, var name) -> updateUser(id, name);
        case Command.DeleteUser(var id) -> deleteUser(id);
    };
}
```

### 式ツリー

```java
public sealed interface Expr {
    record Num(int value) implements Expr {}
    record Add(Expr left, Expr right) implements Expr {}
    record Mul(Expr left, Expr right) implements Expr {}
    record Neg(Expr operand) implements Expr {}
}

public int evaluate(Expr expr) {
    return switch (expr) {
        case Expr.Num(var v) -> v;
        case Expr.Add(var l, var r) -> evaluate(l) + evaluate(r);
        case Expr.Mul(var l, var r) -> evaluate(l) * evaluate(r);
        case Expr.Neg(var e) -> -evaluate(e);
    };
}

// 使用
Expr expr = new Expr.Add(
    new Expr.Num(5),
    new Expr.Mul(new Expr.Num(3), new Expr.Num(4))
);
int result = evaluate(expr);  // 5 + (3 * 4) = 17
```

---

## メリット

### 1. 網羅性のコンパイル時チェック

```java
// 新しいサブクラスを追加すると...
public sealed interface Shape permits Circle, Rectangle, Triangle, Pentagon {}
public record Pentagon(double side) implements Shape {}

// 既存の switch がコンパイルエラーになる
public double area(Shape shape) {
    return switch (shape) {
        case Circle c -> Math.PI * c.radius() * c.radius();
        case Rectangle r -> r.width() * r.height();
        case Triangle t -> 0.5 * t.base() * t.height();
        // エラー: Pentagon のケースがない
    };
}
```

### 2. ドメインモデルの明確化

```java
// 支払い方法は3種類のみであることが型で保証される
public sealed interface PaymentMethod
    permits CreditCard, BankTransfer, DigitalWallet {}
```

### 3. 安全なダウンキャスト

```java
// instanceof + パターンマッチングで安全にダウンキャスト
if (shape instanceof Circle c) {
    // c は Circle として使える
}
```

---

## 従来の設計との比較

### enum との違い

```java
// enum: 値が固定（シングルトン）
enum Status { PENDING, ACTIVE, INACTIVE }

// Sealed: 各インスタンスがデータを持てる
sealed interface Status {
    record Pending() implements Status {}
    record Active(LocalDateTime activatedAt) implements Status {}
    record Inactive(String reason) implements Status {}
}
```

### Visitor パターンとの比較

```java
// 従来: Visitor パターン
interface ShapeVisitor<R> {
    R visitCircle(Circle c);
    R visitRectangle(Rectangle r);
}

// Sealed + Pattern Matching: より簡潔
double area = switch (shape) {
    case Circle c -> Math.PI * c.radius() * c.radius();
    case Rectangle r -> r.width() * r.height();
};
```

---

## 制限事項

```java
// permits に列挙されたクラスは同じモジュールまたはパッケージ内にある必要がある

// NG: 異なるパッケージのクラスを permits に指定できない（同一モジュール内なら可）
public sealed class Shape permits com.other.Circle {}  // 別モジュールはNG
```

---

## まとめ

| 概念 | 説明 |
|-----|------|
| sealed | 継承を制限 |
| permits | 許可するサブクラスを列挙 |
| final | これ以上の継承を禁止 |
| non-sealed | 継承制限を解除 |
| 網羅性チェック | switch で全ケースをカバーしないとコンパイルエラー |

---

## 次のステップ

- [Pattern Matching](java-11-pattern-matching.md) - instanceof、switch式
