# クラスローディング

## はじめに

クラスローディングは、JVMがクラスファイル（`.class`）をメモリに読み込み、使用可能な状態にするプロセスです。この仕組みを理解することで、Spring BootのDI（依存性注入）やプラグインシステムの動作原理を把握できます。

---

## クラスローディングの3つのフェーズ

```
┌─────────────────────────────────────────────────────────────┐
│              クラスローディングの流れ                        │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  1. Loading（ロード）                                        │
│     ┌────────────────────────────────────────────────────┐  │
│     │ .classファイルをバイナリデータとして読み込む        │  │
│     │ → java.lang.Class オブジェクトを生成               │  │
│     └────────────────────────────────────────────────────┘  │
│                           ↓                                  │
│  2. Linking（リンク）                                        │
│     ┌────────────────────────────────────────────────────┐  │
│     │ a. Verification（検証）                             │  │
│     │    → バイトコードの正当性を検証                     │  │
│     │                                                     │  │
│     │ b. Preparation（準備）                              │  │
│     │    → static フィールドにデフォルト値を設定          │  │
│     │                                                     │  │
│     │ c. Resolution（解決）                               │  │
│     │    → シンボリック参照を実際の参照に変換             │  │
│     └────────────────────────────────────────────────────┘  │
│                           ↓                                  │
│  3. Initialization（初期化）                                 │
│     ┌────────────────────────────────────────────────────┐  │
│     │ static イニシャライザとstatic フィールドの初期化     │  │
│     └────────────────────────────────────────────────────┘  │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 各フェーズの詳細

```java
public class Example {
    // Preparation: count = 0 (デフォルト値)
    // Initialization: count = 42
    private static int count = 42;

    // Initialization で実行される
    static {
        System.out.println("Static initializer called");
    }
}
```

| フェーズ | 処理内容 | 例 |
|---------|---------|-----|
| Loading | クラスファイルを読み込み | `.class` → バイト配列 |
| Verification | バイトコード検証 | 不正なジャンプ命令の検出 |
| Preparation | staticフィールドにデフォルト値 | `count = 0` |
| Resolution | 参照の解決 | `String` → 実際のクラス |
| Initialization | staticブロック実行 | `count = 42` |

---

## クラスローダー階層

### 親委譲モデル（Parent Delegation Model）

```
┌─────────────────────────────────────────────────────────────┐
│                  クラスローダー階層                          │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │            Bootstrap ClassLoader                     │    │
│  │  ・JVMに組み込み（ネイティブコード）                  │    │
│  │  ・java.lang.*, java.util.* などコアクラス           │    │
│  │  ・$JAVA_HOME/lib/modules                            │    │
│  └─────────────────────────────────────────────────────┘    │
│                          ↑ 委譲                              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │            Platform ClassLoader                      │    │
│  │  (旧: Extension ClassLoader)                         │    │
│  │  ・プラットフォーム固有のクラス                       │    │
│  │  ・java.sql.*, javax.* など                          │    │
│  └─────────────────────────────────────────────────────┘    │
│                          ↑ 委譲                              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │            Application ClassLoader                   │    │
│  │  (System ClassLoader)                                │    │
│  │  ・アプリケーションのクラス                          │    │
│  │  ・classpath上のクラス                               │    │
│  └─────────────────────────────────────────────────────┘    │
│                          ↑ 委譲                              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │            Custom ClassLoader                        │    │
│  │  ・アプリケーション独自のクラスローダー              │    │
│  │  ・例: Spring Boot, Tomcat, OSGi                     │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 親委譲の動作

```
クラス "com.example.MyClass" のロード要求

  Application ClassLoader
      │
      ├─ 1. 親(Platform)に委譲
      │       │
      │       ├─ 2. 親(Bootstrap)に委譲
      │       │       │
      │       │       └─ 3. Bootstrapで探す → 見つからない
      │       │
      │       └─ 4. Platformで探す → 見つからない
      │
      └─ 5. Applicationで探す → 見つかった！ロード完了
```

### なぜ親委譲モデルか？

| 理由 | 説明 |
|-----|------|
| セキュリティ | 悪意のある`java.lang.String`クラスの差し替えを防止 |
| 一意性 | 同じクラスが複数回ロードされることを防止 |
| 可視性 | 親がロードしたクラスは子から参照可能 |

---

## クラスローダーの確認

### コードで確認

```java
public class ClassLoaderDemo {
    public static void main(String[] args) {
        // Stringクラス（コアライブラリ）
        ClassLoader stringLoader = String.class.getClassLoader();
        System.out.println("String: " + stringLoader);  // null (Bootstrap)

        // 自作クラス
        ClassLoader myLoader = ClassLoaderDemo.class.getClassLoader();
        System.out.println("MyClass: " + myLoader);

        // クラスローダー階層を表示
        ClassLoader loader = myLoader;
        while (loader != null) {
            System.out.println("  -> " + loader);
            loader = loader.getParent();
        }
    }
}
```

出力例:
```
String: null
MyClass: jdk.internal.loader.ClassLoaders$AppClassLoader@5c647e05
  -> jdk.internal.loader.ClassLoaders$AppClassLoader@5c647e05
  -> jdk.internal.loader.ClassLoaders$PlatformClassLoader@7a81197d
```

---

## クラスの一意性

### クラスの同一性

JVMでは、クラスは「完全修飾名 + クラスローダー」で一意に識別されます。

```
┌─────────────────────────────────────────────────────────────┐
│              クラスの同一性                                  │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  異なるクラスローダーでロード                                │
│                                                              │
│  ClassLoader A                 ClassLoader B                 │
│       │                              │                       │
│       ↓                              ↓                       │
│  com.example.User              com.example.User              │
│  (Class@1a2b)                  (Class@3c4d)                  │
│                                                              │
│  同じ完全修飾名でも、異なるClassオブジェクト                 │
│  → 相互にキャストできない！                                  │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

```java
// これはClassCastExceptionになる可能性がある
Object obj = loaderA.loadClass("com.example.User").newInstance();
User user = (User) obj;  // loaderBでロードしたUserクラスの場合、例外
```

### Spring Bootでの注意点

Spring Bootの開発モード（DevTools）では、アプリケーションクラスが別のクラスローダーでリロードされます。

```
通常のクラスローダー
    └── DevTools ClassLoader（リスタート時に新規作成）
            └── アプリケーションのクラス
```

これにより、シリアライズされたセッションデータなどで問題が発生することがあります。

---

## カスタムクラスローダー

### 実装例

```java
public class CustomClassLoader extends ClassLoader {

    private final String classPath;

    public CustomClassLoader(String classPath, ClassLoader parent) {
        super(parent);
        this.classPath = classPath;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        try {
            // クラス名をファイルパスに変換
            String fileName = classPath + "/" +
                name.replace('.', '/') + ".class";

            // バイトコードを読み込み
            byte[] classData = Files.readAllBytes(Path.of(fileName));

            // クラスを定義
            return defineClass(name, classData, 0, classData.length);

        } catch (IOException e) {
            throw new ClassNotFoundException(name, e);
        }
    }
}
```

### 使用例

```java
// カスタムクラスローダーを使用
CustomClassLoader loader = new CustomClassLoader(
    "/path/to/classes",
    ClassLoader.getSystemClassLoader()
);

// クラスをロード
Class<?> clazz = loader.loadClass("com.example.Plugin");

// インスタンス生成
Object instance = clazz.getDeclaredConstructor().newInstance();
```

---

## ServiceLoader（SPI）

### サービスプロバイダインターフェース（SPI）とは

ServiceLoaderは、Javaの標準的なプラグイン機構です。インターフェースの実装を実行時に動的に発見・ロードできます。

```
┌─────────────────────────────────────────────────────────────┐
│                    ServiceLoader の仕組み                    │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  1. サービスインターフェース定義                             │
│     ┌────────────────────────────────────────────────────┐  │
│     │ public interface PaymentProvider {                 │  │
│     │     void processPayment(Payment payment);          │  │
│     │ }                                                  │  │
│     └────────────────────────────────────────────────────┘  │
│                                                              │
│  2. 実装クラス（プロバイダ）                                 │
│     ┌────────────────────────────────────────────────────┐  │
│     │ public class StripeProvider implements PaymentProvider│
│     │ public class PayPalProvider implements PaymentProvider│
│     └────────────────────────────────────────────────────┘  │
│                                                              │
│  3. META-INF/services で登録                                 │
│     ┌────────────────────────────────────────────────────┐  │
│     │ META-INF/services/com.example.PaymentProvider      │  │
│     │ ─────────────────────────────────────────────────  │  │
│     │ com.example.StripeProvider                         │  │
│     │ com.example.PayPalProvider                         │  │
│     └────────────────────────────────────────────────────┘  │
│                                                              │
│  4. ServiceLoader で発見・ロード                             │
│     ┌────────────────────────────────────────────────────┐  │
│     │ ServiceLoader.load(PaymentProvider.class)          │  │
│     │ → 全ての実装を自動的に発見                         │  │
│     └────────────────────────────────────────────────────┘  │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 基本的な使い方

```java
// 1. サービスインターフェース
public interface MessageFormatter {
    String format(String message);
}

// 2. 実装クラス
public class JsonFormatter implements MessageFormatter {
    @Override
    public String format(String message) {
        return "{\"message\": \"" + message + "\"}";
    }
}

public class XmlFormatter implements MessageFormatter {
    @Override
    public String format(String message) {
        return "<message>" + message + "</message>";
    }
}
```

```
// 3. META-INF/services/com.example.MessageFormatter
com.example.JsonFormatter
com.example.XmlFormatter
```

```java
// 4. ServiceLoader で使用
ServiceLoader<MessageFormatter> loader = ServiceLoader.load(MessageFormatter.class);

for (MessageFormatter formatter : loader) {
    System.out.println(formatter.format("Hello"));
}
// 出力:
// {"message": "Hello"}
// <message>Hello</message>
```

### 代表的な使用例

| ライブラリ/フレームワーク | SPI の用途 |
|-------------------------|-----------|
| JDBC | `java.sql.Driver` の自動登録 |
| SLF4J | ロギング実装の発見 |
| Jackson | モジュールの自動登録 |
| Spring Boot | `AutoConfiguration` の発見 |
| Servlet | `ServletContainerInitializer` |

### JDBC での例

```java
// Java 6以降、DriverManager は ServiceLoader で自動的にドライバを発見
// META-INF/services/java.sql.Driver に登録されているドライバが自動ロード

// 明示的な Class.forName() は不要
// Class.forName("com.mysql.cj.jdbc.Driver");  // 昔は必要だった

Connection conn = DriverManager.getConnection(
    "jdbc:mysql://localhost/db", "user", "password"
);
```

### Java 9+ モジュールシステムとの統合

Java 9以降は `module-info.java` で宣言的にサービスを定義できます。

```java
// サービスを提供するモジュール
module payment.stripe {
    requires payment.api;
    provides com.example.PaymentProvider
        with com.stripe.StripeProvider;
}

// サービスを使用するモジュール
module payment.app {
    requires payment.api;
    uses com.example.PaymentProvider;
}
```

### ServiceLoader のベストプラクティス

```java
// シングルトンでキャッシュ（毎回ロードしない）
public class FormatterRegistry {
    private static final ServiceLoader<MessageFormatter> loader =
        ServiceLoader.load(MessageFormatter.class);

    public static List<MessageFormatter> getFormatters() {
        List<MessageFormatter> formatters = new ArrayList<>();
        loader.forEach(formatters::add);
        return formatters;
    }

    // リロードが必要な場合
    public static void reload() {
        loader.reload();
    }
}
```

### 注意点

| 注意点 | 説明 |
|-------|------|
| インスタンス生成 | 毎回新しいインスタンスが生成される |
| 引数なしコンストラクタ | 実装クラスには引数なしコンストラクタが必要 |
| クラスパス依存 | クラスパス上のJARのみが対象 |
| 順序不定 | 実装の発見順序は保証されない |

---

## 遅延ロード（Lazy Loading）

JVMはクラスを必要になるまでロードしません。

```java
public class LazyLoadingDemo {
    public static void main(String[] args) {
        System.out.println("main started");

        // この時点ではHeavyClassはロードされない
        if (false) {
            HeavyClass heavy = new HeavyClass();
        }

        System.out.println("main ended");
    }
}

class HeavyClass {
    static {
        System.out.println("HeavyClass loaded!");
    }
}
```

出力:
```
main started
main ended
```

### ロードのトリガー

| トリガー | 例 |
|---------|-----|
| new によるインスタンス生成 | `new MyClass()` |
| static メンバへのアクセス | `MyClass.staticField` |
| リフレクション | `Class.forName("MyClass")` |
| サブクラスの初期化 | 親クラスが先に初期化 |
| mainメソッドを持つクラス | エントリポイント |

---

## クラスのアンロード

### アンロードの条件

クラスがアンロードされる条件:

1. そのクラスのインスタンスが全て到達不能
2. そのClassオブジェクトが到達不能
3. そのクラスをロードしたClassLoaderが到達不能

```
┌─────────────────────────────────────────────────────────────┐
│              クラスのアンロード条件                          │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  通常のクラス（AppClassLoader）                              │
│  → アンロードされない（ClassLoaderがJVM終了まで生存）       │
│                                                              │
│  カスタムクラスローダーでロードしたクラス                    │
│  → ClassLoaderへの参照がなくなればアンロード可能            │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  // ClassLoaderを破棄                                │   │
│  │  customLoader = null;                                │   │
│  │  System.gc();  // GCでアンロード                     │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### Metaspace とクラスアンロード

Java 8以降、クラスメタデータはMetaspaceに格納されます。

```bash
# Metaspaceサイズ制限
java -XX:MaxMetaspaceSize=256m -jar app.jar

# クラスアンロードを許可（デフォルトで有効）
java -XX:+ClassUnloading -jar app.jar
```

---

## Spring Bootとクラスローディング

### Fat JAR のクラスローダー

Spring Boot の実行可能JAR（Fat JAR）は独自のクラスローダーを使用します。

```
BOOT-INF/
├── classes/          ← アプリケーションクラス
├── lib/              ← 依存ライブラリ（JAR）
│   ├── spring-core-6.1.0.jar
│   └── ...
└── classpath.idx     ← クラスパス順序

org/springframework/boot/loader/
├── JarLauncher.class
├── LaunchedURLClassLoader.class  ← Spring Boot独自
└── ...
```

### LaunchedURLClassLoader

```
JVM Bootstrap
    └── Platform ClassLoader
        └── Application ClassLoader
            └── LaunchedURLClassLoader  ← Spring Boot
                    ├── BOOT-INF/classes/
                    └── BOOT-INF/lib/*.jar
```

### クラスローダーの確認

```java
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        ConfigurableApplicationContext ctx =
            SpringApplication.run(Application.class, args);

        // クラスローダー確認
        ClassLoader cl = Application.class.getClassLoader();
        System.out.println("ClassLoader: " + cl.getClass().getName());
        // 出力: org.springframework.boot.loader.LaunchedURLClassLoader
    }
}
```

---

## トラブルシューティング

### ClassNotFoundException vs NoClassDefFoundError

| 例外 | 原因 | 対処 |
|-----|------|------|
| ClassNotFoundException | `Class.forName()` でクラスが見つからない | クラスパスを確認 |
| NoClassDefFoundError | コンパイル時にあったクラスが実行時にない | 依存関係を確認 |

```java
// ClassNotFoundException
try {
    Class.forName("com.example.Missing");
} catch (ClassNotFoundException e) {
    // 動的ロードの失敗
}

// NoClassDefFoundError
public class Main {
    public static void main(String[] args) {
        Helper.help();  // コンパイル時にはHelperがあった
    }
}
// → 実行時にHelper.classがないとNoClassDefFoundError
```

### クラスパスの確認

```bash
# クラスパスを表示
java -XshowSettings:all -version 2>&1 | grep -A20 "java.class.path"

# 特定のクラスがどこからロードされるか
java -verbose:class -jar app.jar 2>&1 | grep "com.example.MyClass"
```

### JAR内のクラス確認

```bash
# JARの内容を確認
jar tf mylib.jar | grep -i "ClassName"

# 複数のJARで同じクラスを検索
for jar in lib/*.jar; do
    if jar tf "$jar" | grep -q "com/example/Target.class"; then
        echo "$jar contains Target.class"
    fi
done
```

---

## まとめ

| 項目 | ポイント |
|-----|---------|
| 3フェーズ | Loading → Linking → Initialization |
| 親委譲モデル | 親クラスローダーに先に委譲してセキュリティ確保 |
| クラスの一意性 | 完全修飾名 + ClassLoader で識別 |
| ServiceLoader | META-INF/services でプラグイン機構を実現 |
| 遅延ロード | 実際に必要になるまでロードしない |
| Spring Boot | LaunchedURLClassLoaderで Fat JAR をサポート |

---

## 次のステップ

- [メモリ管理](jvm-03-memory.md) - ロードされたクラスがどこに格納されるか
- [ガベージコレクション](jvm-04-gc.md) - クラスのアンロードとGCの関係
