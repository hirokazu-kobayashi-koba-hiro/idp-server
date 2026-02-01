# Java 実践パターン

実務で使える実践的なJavaパターンを学びます。

---

## 値オブジェクト（Value Object）

プリミティブ型や String をドメインの概念で包む。

### なぜ必要か

```java
// NG: プリミティブ型をそのまま使う
public void sendEmail(String email, String userId) { }
public void createUser(String userId, String email) { }

// 引数の順序を間違えても気づかない
sendEmail(userId, email);  // コンパイルは通るがバグ
```

### 基本パターン

```java
// OK: 値オブジェクトで包む
public record Email(String value) {
    public Email {
        if (value == null || !value.contains("@")) {
            throw new IllegalArgumentException("Invalid email: " + value);
        }
    }
}

public record UserId(String value) {
    public UserId {
        Objects.requireNonNull(value, "userId must not be null");
    }
}

// 型で制約、取り違え防止
public void sendEmail(Email email, UserId userId) { }
sendEmail(userId, email);  // コンパイルエラー！
```

### 値オブジェクトの特徴

```java
public record Money(BigDecimal amount, String currency) {
    public Money {
        Objects.requireNonNull(amount);
        Objects.requireNonNull(currency);
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
    }

    // 振る舞いを持てる
    public Money add(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("Currency mismatch");
        }
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money multiply(int quantity) {
        return new Money(this.amount.multiply(BigDecimal.valueOf(quantity)), this.currency);
    }
}
```

---

## Result型

### なぜ必要か

例外を使ったエラーハンドリングは、どこで何が起きるか分かりにくい。

```java
// NG: 例外ベース
public User findUser(String id) throws UserNotFoundException {
    // 呼び出し側は例外を忘れがち
}

// 使う側
User user = findUser(id);  // 例外が飛ぶかもしれないが、コードからは分からない
```

Result型を使うと、成功/失敗が型で明示される。

### 基本実装

```java
public sealed interface Result<T> {
    record Success<T>(T value) implements Result<T> {}
    record Failure<T>(String code, String message) implements Result<T> {}

    default boolean isSuccess() {
        return this instanceof Success;
    }

    default boolean isFailure() {
        return this instanceof Failure;
    }

    default T getOrThrow() {
        return switch (this) {
            case Success(var v) -> v;
            case Failure(var code, var msg) ->
                throw new RuntimeException(code + ": " + msg);
        };
    }

    default T getOrElse(T defaultValue) {
        return switch (this) {
            case Success(var v) -> v;
            case Failure f -> defaultValue;
        };
    }

    default <U> Result<U> map(Function<T, U> mapper) {
        return switch (this) {
            case Success(var v) -> new Success<>(mapper.apply(v));
            case Failure(var code, var msg) -> new Failure<>(code, msg);
        };
    }

    default <U> Result<U> flatMap(Function<T, Result<U>> mapper) {
        return switch (this) {
            case Success(var v) -> mapper.apply(v);
            case Failure(var code, var msg) -> new Failure<>(code, msg);
        };
    }

    static <T> Result<T> success(T value) {
        return new Success<>(value);
    }

    static <T> Result<T> failure(String code, String message) {
        return new Failure<>(code, message);
    }
}
```

### 使用例

```java
// 戻り値の型で失敗の可能性が明示される
public Result<User> findUser(String id) {
    User user = repository.findById(id);
    if (user == null) {
        return Result.failure("NOT_FOUND", "User not found: " + id);
    }
    return Result.success(user);
}

// 使用側は Result を処理する必要がある
Result<User> result = findUser("123");

// パターンマッチング
String message = switch (result) {
    case Result.Success(var user) -> "Found: " + user.getName();
    case Result.Failure(var code, var msg) -> "Error: " + msg;
};

// チェーン
String name = findUser("123")
    .map(User::getName)
    .map(String::toUpperCase)
    .getOrElse("UNKNOWN");
```

---

## Builder パターン

### なぜ必要か

コンストラクタの引数が多いと、何が何だか分からなくなる。

```java
// NG: 引数が多すぎる
HttpRequest request = new HttpRequest(
    "POST",                              // method?
    "https://api.example.com/users",     // url?
    "application/json",                  // contentType?
    "Bearer token",                      // auth?
    "{\"name\": \"Alice\"}",             // body?
    30                                   // timeout?
);
```

Builder パターンなら、何を設定しているか明確。

### 基本実装

```java
public class HttpRequest {
    private final String method;
    private final String url;
    private final Map<String, String> headers;
    private final String body;
    private final Duration timeout;

    private HttpRequest(Builder builder) {
        this.method = builder.method;
        this.url = builder.url;
        this.headers = Map.copyOf(builder.headers);
        this.body = builder.body;
        this.timeout = builder.timeout;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String method = "GET";
        private String url;
        private Map<String, String> headers = new HashMap<>();
        private String body;
        private Duration timeout = Duration.ofSeconds(30);

        public Builder method(String method) {
            this.method = method;
            return this;
        }

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder header(String name, String value) {
            this.headers.put(name, value);
            return this;
        }

        public Builder body(String body) {
            this.body = body;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public HttpRequest build() {
            Objects.requireNonNull(url, "url is required");
            return new HttpRequest(this);
        }
    }

    // getters...
}

// OK: 何を設定しているか明確
HttpRequest request = HttpRequest.builder()
    .method("POST")
    .url("https://api.example.com/users")
    .header("Content-Type", "application/json")
    .header("Authorization", "Bearer token")
    .body("{\"name\": \"Alice\"}")
    .timeout(Duration.ofSeconds(10))
    .build();
```

### Record と Builder

```java
public record User(String id, String name, String email, int age) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String name;
        private String email;
        private int age;

        public Builder id(String id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder email(String email) { this.email = email; return this; }
        public Builder age(int age) { this.age = age; return this; }

        public User build() {
            return new User(id, name, email, age);
        }
    }
}
```

---

## Factory パターン

### なぜ必要か

オブジェクト生成が複雑な場合、コンストラクタだけでは表現しきれない。

```java
// NG: コンストラクタでは意図が分かりにくい
Money m1 = new Money(new BigDecimal("100.00"), Currency.getInstance("USD"));
Money m2 = new Money(BigDecimal.ZERO, Currency.getInstance("JPY"));

// OK: Factory Method で意図を明確に
Money m1 = Money.dollars(100.00);
Money m2 = Money.zero(Currency.getInstance("JPY"));
Money m3 = Money.parse("100.00 USD");  // 文字列からパース
```

### Static Factory Method

```java
public class Money {
    private final BigDecimal amount;
    private final Currency currency;

    private Money(BigDecimal amount, Currency currency) {
        this.amount = amount;
        this.currency = currency;
    }

    // Static Factory Methods
    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    public static Money dollars(double amount) {
        return new Money(BigDecimal.valueOf(amount), Currency.getInstance("USD"));
    }

    public static Money yen(long amount) {
        return new Money(BigDecimal.valueOf(amount), Currency.getInstance("JPY"));
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public static Money parse(String text) {
        // "100.00 USD" → Money
        String[] parts = text.split(" ");
        return new Money(
            new BigDecimal(parts[0]),
            Currency.getInstance(parts[1])
        );
    }
}

// 使用
Money price = Money.dollars(29.99);
Money tax = Money.yen(1000);
Money zero = Money.zero(Currency.getInstance("EUR"));
```

### Factory Interface

```java
public interface NotificationSender {
    void send(String message, String recipient);
}

public interface NotificationSenderFactory {
    NotificationSender create(String type);
}

public class DefaultNotificationSenderFactory implements NotificationSenderFactory {
    @Override
    public NotificationSender create(String type) {
        return switch (type) {
            case "email" -> new EmailSender();
            case "sms" -> new SmsSender();
            case "push" -> new PushNotificationSender();
            default -> throw new IllegalArgumentException("Unknown type: " + type);
        };
    }
}
```

---

## Strategy パターン

### なぜ必要か

同じ処理でもアルゴリズムを切り替えたい場合がある。

```java
// NG: if-else の嵐
public Money calculatePrice(String pricingType, Money basePrice, int quantity) {
    if (pricingType.equals("standard")) {
        return basePrice.multiply(quantity);
    } else if (pricingType.equals("bulk")) {
        if (quantity >= 10) {
            return basePrice.multiply(quantity).multiply(0.9);
        }
        return basePrice.multiply(quantity);
    } else if (pricingType.equals("subscription")) {
        return basePrice;
    }
    // 新しい価格体系を追加するたびにここを修正...
}
```

Strategy パターンなら、アルゴリズムを差し替え可能にできる。

### 関数型インターフェースで実装

```java
// Strategy を関数型インターフェースで表現
@FunctionalInterface
public interface PricingStrategy {
    Money calculate(Money basePrice, int quantity);
}

public class PricingService {
    private final PricingStrategy strategy;

    public PricingService(PricingStrategy strategy) {
        this.strategy = strategy;
    }

    public Money calculateTotal(Money basePrice, int quantity) {
        return strategy.calculate(basePrice, quantity);
    }
}

// 使用: ラムダで簡潔に定義
PricingStrategy standard = (price, qty) -> price.multiply(qty);
PricingStrategy bulk = (price, qty) -> {
    if (qty >= 10) {
        return price.multiply(qty).multiply(0.9);  // 10%割引
    }
    return price.multiply(qty);
};
PricingStrategy subscription = (price, qty) -> price;  // 固定価格

PricingService service = new PricingService(bulk);
Money total = service.calculateTotal(Money.dollars(100), 15);
```

### Enum で Strategy

```java
public enum SortStrategy {
    BY_NAME {
        @Override
        public Comparator<User> comparator() {
            return Comparator.comparing(User::getName);
        }
    },
    BY_AGE {
        @Override
        public Comparator<User> comparator() {
            return Comparator.comparing(User::getAge);
        }
    },
    BY_CREATED_AT {
        @Override
        public Comparator<User> comparator() {
            return Comparator.comparing(User::getCreatedAt);
        }
    };

    public abstract Comparator<User> comparator();
}

// 使用
List<User> users = getUsers();
users.sort(SortStrategy.BY_AGE.comparator());
```

---

## Repository パターン

### なぜ必要か

ビジネスロジックがデータベースの詳細に依存すると、テストやDB変更が困難になる。

```java
// NG: ビジネスロジックにSQLが混在
public User findUser(String id) {
    String sql = "SELECT * FROM users WHERE id = ?";
    // JDBC の詳細がビジネスロジックに漏れる
}
```

Repository パターンでデータアクセスを抽象化すると、ビジネスロジックがDBに依存しなくなる。

### 基本実装

```java
public interface Repository<T, ID> {
    Optional<T> findById(ID id);
    List<T> findAll();
    T save(T entity);
    void delete(T entity);
    boolean existsById(ID id);
}

public interface UserRepository extends Repository<User, UserId> {
    Optional<User> findByEmail(Email email);
    List<User> findByAgeGreaterThan(int age);
}

// 実装（テスト用のインメモリ実装）
public class InMemoryUserRepository implements UserRepository {
    private final Map<UserId, User> storage = new ConcurrentHashMap<>();

    @Override
    public Optional<User> findById(UserId id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public List<User> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public User save(User user) {
        storage.put(user.getId(), user);
        return user;
    }

    @Override
    public void delete(User user) {
        storage.remove(user.getId());
    }

    @Override
    public boolean existsById(UserId id) {
        return storage.containsKey(id);
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        return storage.values().stream()
            .filter(u -> u.getEmail().equals(email))
            .findFirst();
    }

    @Override
    public List<User> findByAgeGreaterThan(int age) {
        return storage.values().stream()
            .filter(u -> u.getAge() > age)
            .toList();
    }
}
```

---

## DTO パターン

### なぜ必要か

内部のドメインオブジェクトをそのまま外部に公開すると、以下の問題が起きる。

- 内部構造の変更が外部APIに影響
- 公開したくないフィールド（パスワードハッシュ等）が漏れる
- 外部用のフォーマット（日付形式等）とドメインの形式が異なる

```java
// NG: Entity をそのまま返す
@GetMapping("/users/{id}")
public User getUser(@PathVariable String id) {
    return userRepository.findById(id);  // passwordHash も返ってしまう
}
```

### 基本実装

```java
// Entity（ドメインオブジェクト）
public class User {
    private UserId id;
    private String name;
    private Email email;
    private String passwordHash;  // 外部に公開しない
    private LocalDateTime createdAt;
    // ...
}

// Response DTO（外部に公開する情報のみ）
public record UserResponse(
    String id,
    String name,
    String email,
    String createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
            user.getId().value(),
            user.getName(),
            user.getEmail().value(),
            user.getCreatedAt().toString()
        );
    }
}

// Request DTO（入力を受け取る）
public record CreateUserRequest(
    String name,
    String email,
    String password
) {
    public User toEntity() {
        return new User(
            UserId.generate(),
            name,
            new Email(email),
            PasswordHasher.hash(password),
            LocalDateTime.now()
        );
    }
}

// OK: DTO を返す
@GetMapping("/users/{id}")
public UserResponse getUser(@PathVariable String id) {
    User user = userRepository.findById(id);
    return UserResponse.from(user);  // 必要な情報のみ
}
```

---

## Null Object パターン

### なぜ必要か

null チェックがコード中に散らばると、可読性が下がり、NullPointerException のリスクが増える。

```java
// NG: null チェックだらけ
public void process(Logger logger) {
    if (logger != null) {
        logger.log("Starting...");
    }
    // 処理
    if (logger != null) {
        logger.log("Done");
    }
}
```

Null Object パターンでは、「何もしない」実装を用意して null を使わない。

### 基本実装

```java
public interface Logger {
    void log(String message);
}

public class ConsoleLogger implements Logger {
    @Override
    public void log(String message) {
        System.out.println(message);
    }
}

// Null Object: 何もしない実装
public class NullLogger implements Logger {
    @Override
    public void log(String message) {
        // 何もしない
    }
}

// 使用
public class Service {
    private final Logger logger;

    public Service(Logger logger) {
        // null の代わりに NullLogger を使う
        this.logger = logger != null ? logger : new NullLogger();
    }

    public void doSomething() {
        logger.log("Starting...");  // null チェック不要
        // 処理
        logger.log("Done");
    }
}
```

---

## Decorator パターン

### なぜ必要か

継承で機能を追加すると、組み合わせが爆発する。

```java
// NG: 継承で機能追加
class FileDataSource { }
class EncryptedFileDataSource extends FileDataSource { }
class CompressedFileDataSource extends FileDataSource { }
class EncryptedCompressedFileDataSource extends FileDataSource { }  // 組み合わせ爆発！
```

Decorator パターンなら、機能を動的に組み合わせられる。

### 基本実装

```java
public interface DataSource {
    String read();
    void write(String data);
}

public class FileDataSource implements DataSource {
    private final String filename;
    // 実装...
}

// Decorator の基底クラス
public abstract class DataSourceDecorator implements DataSource {
    protected final DataSource wrapped;

    public DataSourceDecorator(DataSource source) {
        this.wrapped = source;
    }
}

public class EncryptionDecorator extends DataSourceDecorator {
    public EncryptionDecorator(DataSource source) {
        super(source);
    }

    @Override
    public String read() {
        return decrypt(wrapped.read());
    }

    @Override
    public void write(String data) {
        wrapped.write(encrypt(data));
    }

    private String encrypt(String data) { /* ... */ }
    private String decrypt(String data) { /* ... */ }
}

public class CompressionDecorator extends DataSourceDecorator {
    // 圧縮/解凍の実装
}

// OK: 機能を自由に組み合わせ
DataSource source = new FileDataSource("data.txt");
source = new EncryptionDecorator(source);     // 暗号化を追加
source = new CompressionDecorator(source);    // 圧縮を追加
source.write("secret data");  // 圧縮 → 暗号化 → 書き込み
```

---

## 不変オブジェクト

### なぜ必要か

可変オブジェクトは、いつ・どこで変更されたか追跡が困難。特にマルチスレッドで問題になる。

```java
// NG: 可変オブジェクト
public class User {
    private String name;
    private List<String> roles;

    public void setName(String name) { this.name = name; }
    public List<String> getRoles() { return roles; }  // 外部から変更可能
}

User user = getUser();
user.getRoles().add("admin");  // 知らないうちに変更される
```

不変オブジェクトは一度作ったら変更できないので、安全に共有できる。

### 基本実装

```java
public final class ImmutableUser {
    private final String id;
    private final String name;
    private final List<String> roles;

    public ImmutableUser(String id, String name, List<String> roles) {
        this.id = id;
        this.name = name;
        this.roles = List.copyOf(roles);  // 防御的コピー
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public List<String> getRoles() { return roles; }  // 不変リストを返す

    // 変更は新しいインスタンスを返す
    public ImmutableUser withName(String newName) {
        return new ImmutableUser(this.id, newName, this.roles);
    }

    public ImmutableUser addRole(String role) {
        List<String> newRoles = new ArrayList<>(this.roles);
        newRoles.add(role);
        return new ImmutableUser(this.id, this.name, newRoles);
    }
}

// Record を使うと簡潔
public record User(String id, String name, List<String> roles) {
    public User {
        roles = List.copyOf(roles);  // コンパクトコンストラクタで防御的コピー
    }

    public User withName(String newName) {
        return new User(id, newName, roles);
    }
}
```

---

## まとめ

| パターン | 目的 |
|---------|------|
| 値オブジェクト | ドメイン概念を型で表現、型安全性 |
| Result型 | 例外を使わないエラーハンドリング |
| Builder | 複雑なオブジェクトの構築 |
| Factory | オブジェクト生成の隠蔽 |
| Strategy | アルゴリズムの差し替え |
| Repository | データアクセスの抽象化 |
| DTO | レイヤー間のデータ転送 |
| Null Object | null チェックの回避 |
| Decorator | 機能の動的追加 |
| 不変オブジェクト | スレッドセーフ、副作用なし |

---

## 関連ドキュメント

- [Records](java-09-records.md) - 不変データクラス
- [Sealed Classes](java-10-sealed.md) - 継承の制限
- [Pattern Matching](java-11-pattern-matching.md) - パターンマッチング
