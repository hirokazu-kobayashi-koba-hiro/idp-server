# Java 例外処理

Javaの例外処理の基礎とベストプラクティスを学びます。

---

## なぜ例外処理か

エラーを戻り値で返すと、チェックを忘れがちです。

```java
// 戻り値でエラーを返す（C言語スタイル）
int result = readFile(path);
if (result == -1) {
    // エラー処理...でも忘れがち
}

// 例外: 処理しないと先に進めない
try {
    String content = readFile(path);
} catch (IOException e) {
    // エラー処理が強制される
}
```

Javaの例外処理は、正常系と異常系を明確に分離し、エラーハンドリングを強制する仕組みです。

---

## 例外の階層

```
Throwable
├── Error（回復不能、通常catchしない）
│   ├── OutOfMemoryError
│   ├── StackOverflowError
│   └── ...
│
└── Exception
    ├── RuntimeException（非検査例外）
    │   ├── NullPointerException
    │   ├── IllegalArgumentException
    │   ├── IllegalStateException
    │   ├── IndexOutOfBoundsException
    │   └── ...
    │
    └── 検査例外（catchまたはthrows必須）
        ├── IOException
        ├── SQLException
        └── ...
```

---

## 検査例外と非検査例外

### 検査例外（Checked Exception）

コンパイラがチェックする。catch または throws が必須。

```java
// 検査例外を投げるメソッド
public void readFile(String path) throws IOException {
    BufferedReader reader = new BufferedReader(new FileReader(path));
    // ...
}

// 呼び出し側は処理が必要
public void process() {
    try {
        readFile("data.txt");
    } catch (IOException e) {
        // エラー処理
    }
}

// または throws で伝播
public void process() throws IOException {
    readFile("data.txt");
}
```

### 非検査例外（Unchecked Exception）

RuntimeException のサブクラス。catch/throws は任意。

```java
public void setAge(int age) {
    if (age < 0) {
        throw new IllegalArgumentException("Age cannot be negative: " + age);
    }
    this.age = age;
}

// 呼び出し側でcatchは任意
user.setAge(-1);  // IllegalArgumentException がスローされる
```

---

## try-catch-finally

### 基本構文

```java
try {
    // 例外が発生する可能性のある処理
    int result = 10 / divisor;
    System.out.println(result);
} catch (ArithmeticException e) {
    // 例外処理
    System.err.println("Division by zero: " + e.getMessage());
} finally {
    // 必ず実行される（例外の有無に関わらず）
    System.out.println("Cleanup");
}
```

### 複数の例外をキャッチ

```java
try {
    // ...
} catch (FileNotFoundException e) {
    System.err.println("File not found: " + e.getMessage());
} catch (IOException e) {
    System.err.println("IO error: " + e.getMessage());
} catch (Exception e) {
    // 最後に一般的な例外
    System.err.println("Unexpected error: " + e.getMessage());
}

// マルチキャッチ（Java 7以降）
try {
    // ...
} catch (FileNotFoundException | SocketException e) {
    System.err.println("Connection error: " + e.getMessage());
}
```

---

## try-with-resources

リソース（ファイル、接続など）を自動でクローズする。

### 基本

```java
// 従来の方法
BufferedReader reader = null;
try {
    reader = new BufferedReader(new FileReader("file.txt"));
    String line = reader.readLine();
} finally {
    if (reader != null) {
        try {
            reader.close();
        } catch (IOException e) {
            // ignore
        }
    }
}

// try-with-resources（推奨）
try (BufferedReader reader = new BufferedReader(new FileReader("file.txt"))) {
    String line = reader.readLine();
}  // 自動的にcloseされる
```

### 複数のリソース

```java
try (
    FileInputStream fis = new FileInputStream("input.txt");
    FileOutputStream fos = new FileOutputStream("output.txt")
) {
    // 両方とも自動的にクローズされる
    // 宣言の逆順でクローズ（fosが先、fisが後）
}
```

### AutoCloseable の実装

```java
public class DatabaseConnection implements AutoCloseable {
    private Connection connection;

    public DatabaseConnection(String url) throws SQLException {
        this.connection = DriverManager.getConnection(url);
    }

    @Override
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
}

// 使用
try (DatabaseConnection db = new DatabaseConnection(url)) {
    // ...
}  // 自動的にclose
```

---

## 例外のスロー

### throw文

```java
public void withdraw(int amount) {
    if (amount <= 0) {
        throw new IllegalArgumentException("Amount must be positive: " + amount);
    }
    if (amount > balance) {
        throw new InsufficientFundsException("Insufficient balance");
    }
    balance -= amount;
}
```

### throws宣言

```java
// メソッドが例外をスローする可能性を宣言
public void processFile(String path) throws IOException, ParseException {
    // ...
}
```

---

## カスタム例外

### 非検査例外（RuntimeException）

```java
public class UserNotFoundException extends RuntimeException {
    private final String userId;

    public UserNotFoundException(String userId) {
        super("User not found: " + userId);
        this.userId = userId;
    }

    public UserNotFoundException(String userId, Throwable cause) {
        super("User not found: " + userId, cause);
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }
}

// 使用
public User findById(String id) {
    return userRepository.findById(id)
        .orElseThrow(() -> new UserNotFoundException(id));
}
```

### 検査例外

```java
public class ValidationException extends Exception {
    private final List<String> errors;

    public ValidationException(List<String> errors) {
        super("Validation failed: " + errors);
        this.errors = errors;
    }

    public List<String> getErrors() {
        return errors;
    }
}
```

---

## 例外の再スロー

### ラップして再スロー

```java
public User loadUser(String id) {
    try {
        return repository.findById(id);
    } catch (SQLException e) {
        // 検査例外を非検査例外にラップ
        throw new DataAccessException("Failed to load user: " + id, e);
    }
}
```

### そのまま再スロー

```java
public void process() throws IOException {
    try {
        doSomething();
    } catch (IOException e) {
        logger.error("Processing failed", e);
        throw e;  // そのまま再スロー
    }
}
```

---

## 標準例外の使い分け

### IllegalArgumentException

不正な引数に対して使用。

```java
public void setAge(int age) {
    if (age < 0 || age > 150) {
        throw new IllegalArgumentException("Invalid age: " + age);
    }
}

public void setEmail(String email) {
    if (email == null || !email.contains("@")) {
        throw new IllegalArgumentException("Invalid email: " + email);
    }
}
```

### IllegalStateException

オブジェクトの状態が不正な場合に使用。

```java
public void start() {
    if (isRunning) {
        throw new IllegalStateException("Already started");
    }
    isRunning = true;
}

public void stop() {
    if (!isRunning) {
        throw new IllegalStateException("Not running");
    }
    isRunning = false;
}
```

### NullPointerException

null が許可されない場所で null が渡された場合。

```java
public User(String name) {
    this.name = Objects.requireNonNull(name, "name must not be null");
}
```

### UnsupportedOperationException

サポートされない操作に対して使用。

```java
@Override
public void remove() {
    throw new UnsupportedOperationException("Remove not supported");
}
```

---

## ベストプラクティス

### 1. 具体的な例外をキャッチする

```java
// NG: 範囲が広すぎる
try {
    // ...
} catch (Exception e) {
    // 全ての例外をキャッチ
}

// OK: 具体的な例外
try {
    // ...
} catch (FileNotFoundException e) {
    // ファイルが見つからない場合の処理
} catch (IOException e) {
    // その他のIO例外
}
```

### 2. 例外を握りつぶさない

```java
// NG: 例外を無視
try {
    // ...
} catch (Exception e) {
    // 何もしない
}

// OK: 少なくともログを残す
try {
    // ...
} catch (Exception e) {
    logger.error("Unexpected error", e);
}
```

### 3. 例外メッセージに文脈を含める

```java
// NG: 情報不足
throw new RuntimeException("Failed");

// OK: 何が失敗したかが分かる
throw new RuntimeException("Failed to load user with id: " + userId);
```

### 4. 原因（cause）を保持する

```java
// NG: 原因を失う
try {
    // ...
} catch (SQLException e) {
    throw new RuntimeException("Database error");
}

// OK: 原因を保持
try {
    // ...
} catch (SQLException e) {
    throw new RuntimeException("Database error", e);  // cause を渡す
}
```

### 5. finally でリソースを解放しない

```java
// NG: finally でリソース解放（煩雑）
FileInputStream fis = null;
try {
    fis = new FileInputStream("file.txt");
    // ...
} finally {
    if (fis != null) {
        try { fis.close(); } catch (IOException e) { }
    }
}

// OK: try-with-resources を使う
try (FileInputStream fis = new FileInputStream("file.txt")) {
    // ...
}
```

### 6. 制御フローに例外を使わない

```java
// NG: 例外を制御フローに使用
try {
    int value = Integer.parseInt(input);
    // ...
} catch (NumberFormatException e) {
    // 数値でない場合のデフォルト処理
    value = 0;
}

// OK: 事前チェック
if (isNumeric(input)) {
    int value = Integer.parseInt(input);
} else {
    value = 0;
}
```

---

## Optional による null 回避

例外の代わりに Optional を使うパターン。

```java
// 例外をスロー
public User findByIdOrThrow(String id) {
    User user = repository.findById(id);
    if (user == null) {
        throw new UserNotFoundException(id);
    }
    return user;
}

// Optional を返す（推奨）
public Optional<User> findById(String id) {
    return Optional.ofNullable(repository.findById(id));
}

// 呼び出し側
User user = userService.findById(id)
    .orElseThrow(() -> new UserNotFoundException(id));

User userOrDefault = userService.findById(id)
    .orElse(User.anonymous());

userService.findById(id)
    .ifPresent(user -> sendNotification(user));
```

---

## 例外とログ

### ログレベルの選択

```java
try {
    // ...
} catch (UserNotFoundException e) {
    // 想定内のエラー → WARN
    logger.warn("User not found: {}", e.getUserId());
} catch (Exception e) {
    // 想定外のエラー → ERROR
    logger.error("Unexpected error", e);
}
```

### スタックトレースの出力

```java
// メッセージのみ
logger.error("Error: {}", e.getMessage());

// スタックトレース付き（例外を第2引数に）
logger.error("Error occurred", e);
```

---

## まとめ

| 概念 | ポイント |
|-----|---------|
| 検査例外 | catch/throws 必須、回復可能なエラー |
| 非検査例外 | catch 任意、プログラムのバグ |
| try-with-resources | リソースの自動クローズ |
| カスタム例外 | 文脈情報を含めて設計 |
| cause | 原因例外を保持する |
| Optional | null を例外で表現しない |

---

## 次のステップ

- [Stream API](java-06-streams.md) - コレクション操作
