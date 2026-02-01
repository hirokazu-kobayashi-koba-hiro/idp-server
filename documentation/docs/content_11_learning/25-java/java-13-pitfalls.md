# Java 落とし穴と注意事項

Javaでよくあるバグ、メモリリーク、スレッドセーフの問題をまとめます。

---

## メモリリーク

Javaにはガベージコレクションがありますが、メモリリークは発生します。GCは「参照されていないオブジェクト」を回収しますが、不要な参照が残っているとリークします。

### コレクションへの追加のみ

```java
// NG: 追加するだけで削除しない
public class EventHistory {
    private static final List<Event> events = new ArrayList<>();

    public void addEvent(Event event) {
        events.add(event);  // 永遠に増え続ける
    }
}

// OK: 上限を設けるか、古いものを削除
public class EventHistory {
    private static final int MAX_SIZE = 1000;
    private final Queue<Event> events = new LinkedList<>();

    public void addEvent(Event event) {
        events.add(event);
        if (events.size() > MAX_SIZE) {
            events.poll();  // 古いものを削除
        }
    }
}
```

### staticフィールドでの参照保持

```java
// NG: staticフィールドに大きなオブジェクトを保持
public class Cache {
    private static final Map<String, byte[]> cache = new HashMap<>();

    public void put(String key, byte[] data) {
        cache.put(key, data);  // アプリ終了までメモリに残る
    }
}

// OK: WeakHashMap、サイズ制限、有効期限を設ける
public class Cache {
    private final Map<String, byte[]> cache = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
            return size() > 100;  // 100件を超えたら古いものを削除
        }
    };
}
```

### リスナー・コールバックの登録解除忘れ

```java
// NG: 登録したまま解除しない
public class UserView {
    public UserView(EventBus eventBus) {
        eventBus.register(this);  // 登録
        // 解除を忘れると、UserViewがGCされない
    }
}

// OK: 明示的に解除
public class UserView implements AutoCloseable {
    private final EventBus eventBus;

    public UserView(EventBus eventBus) {
        this.eventBus = eventBus;
        eventBus.register(this);
    }

    @Override
    public void close() {
        eventBus.unregister(this);  // 必ず解除
    }
}
```

### リソースのclose忘れ

```java
// NG: closeを忘れるとリソースリーク
public String readFile(String path) throws IOException {
    FileInputStream fis = new FileInputStream(path);
    // 例外が発生するとcloseされない
    byte[] data = fis.readAllBytes();
    fis.close();
    return new String(data);
}

// OK: try-with-resources
public String readFile(String path) throws IOException {
    try (FileInputStream fis = new FileInputStream(path)) {
        return new String(fis.readAllBytes());
    }  // 自動的にclose
}
```

---

## スレッドセーフ

マルチスレッド環境では、データ競合やデッドロックに注意が必要です。

### 可変オブジェクトの共有

```java
// NG: 複数スレッドで可変オブジェクトを共有
public class Counter {
    private int count = 0;

    public void increment() {
        count++;  // 読み取り→加算→書き込みの3操作（原子的でない）
    }
}

// OK: AtomicIntegerを使う
public class Counter {
    private final AtomicInteger count = new AtomicInteger(0);

    public void increment() {
        count.incrementAndGet();  // 原子的操作
    }
}

// OK: synchronizedを使う
public class Counter {
    private int count = 0;

    public synchronized void increment() {
        count++;
    }
}
```

### check-then-act（競合状態）

```java
// NG: チェックと操作の間に他スレッドが割り込む可能性
public class UserCache {
    private final Map<String, User> cache = new HashMap<>();

    public User getOrCreate(String id) {
        if (!cache.containsKey(id)) {      // チェック
            cache.put(id, new User(id));   // 操作（この間に他スレッドが...）
        }
        return cache.get(id);
    }
}

// OK: ConcurrentHashMapのcomputeIfAbsent
public class UserCache {
    private final Map<String, User> cache = new ConcurrentHashMap<>();

    public User getOrCreate(String id) {
        return cache.computeIfAbsent(id, User::new);  // 原子的操作
    }
}
```

### スレッドセーフでないクラス

以下のクラスはスレッドセーフではありません。複数スレッドで共有しないでください。

```java
// NG: SimpleDateFormatを共有
private static final SimpleDateFormat FORMAT = new SimpleDateFormat("yyyy-MM-dd");

public String format(Date date) {
    return FORMAT.format(date);  // スレッドセーフでない！
}

// OK: DateTimeFormatterを使う（スレッドセーフ）
private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

public String format(LocalDate date) {
    return FORMAT.format(date);  // スレッドセーフ
}

// OK: ThreadLocalで各スレッドに固有のインスタンス
private static final ThreadLocal<SimpleDateFormat> FORMAT =
    ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd"));
```

**スレッドセーフでない主なクラス:**
- `SimpleDateFormat`
- `HashMap`, `ArrayList`, `HashSet`（→ `ConcurrentHashMap`, `CopyOnWriteArrayList`）
- `StringBuilder`（→ `StringBuffer`、ただし通常は同期不要）
- `Random`（→ `ThreadLocalRandom`）

### HashMapの無限ループ

Java 7以前では、複数スレッドでHashMapを操作すると無限ループが発生することがありました。

```java
// NG: 複数スレッドでHashMap
private final Map<String, String> map = new HashMap<>();

// スレッド1とスレッド2が同時にput → 無限ループの可能性

// OK: ConcurrentHashMapを使う
private final Map<String, String> map = new ConcurrentHashMap<>();
```

### ダブルチェックロッキングの誤り

```java
// NG: volatileがないと壊れる
public class Singleton {
    private static Singleton instance;

    public static Singleton getInstance() {
        if (instance == null) {
            synchronized (Singleton.class) {
                if (instance == null) {
                    instance = new Singleton();  // 部分的に初期化されたオブジェクトが見える可能性
                }
            }
        }
        return instance;
    }
}

// OK: volatileを付ける
public class Singleton {
    private static volatile Singleton instance;
    // ...
}

// OK: ホルダーイディオム（推奨）
public class Singleton {
    private Singleton() {}

    private static class Holder {
        static final Singleton INSTANCE = new Singleton();
    }

    public static Singleton getInstance() {
        return Holder.INSTANCE;
    }
}
```

---

## NullPointerException

Javaで最も多いランタイムエラーです。

### nullチェックの欠如

```java
// NG: nullチェックなし
public String getUpperName(User user) {
    return user.getName().toUpperCase();  // user または getName() が null なら NPE
}

// OK: nullチェック
public String getUpperName(User user) {
    if (user == null || user.getName() == null) {
        return "";
    }
    return user.getName().toUpperCase();
}

// OK: Optionalを使う
public String getUpperName(User user) {
    return Optional.ofNullable(user)
        .map(User::getName)
        .map(String::toUpperCase)
        .orElse("");
}
```

### Mapのget

```java
// NG: キーが存在しない場合nullが返る
Map<String, Integer> map = new HashMap<>();
int value = map.get("key");  // null → NullPointerException（アンボクシング時）

// OK: getOrDefault
int value = map.getOrDefault("key", 0);
```

### 配列・リストの要素

```java
// NG: 要素がnullの可能性
String[] array = new String[10];
int length = array[0].length();  // array[0] は null

// OK: nullチェック
String first = array[0];
int length = first != null ? first.length() : 0;
```

---

## equals と hashCode

### 片方だけオーバーライド

```java
// NG: equalsだけオーバーライド
public class User {
    private String id;

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof User)) return false;
        return id.equals(((User) o).id);
    }
    // hashCodeをオーバーライドしていない！
}

User u1 = new User("1");
User u2 = new User("1");
u1.equals(u2);  // true

Set<User> set = new HashSet<>();
set.add(u1);
set.contains(u2);  // false！（hashCodeが異なるため）

// OK: 両方オーバーライド
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
```

### 可変フィールドでhashCode

```java
// NG: 可変フィールドでhashCode
public class User {
    private String name;  // 可変

    public void setName(String name) { this.name = name; }

    @Override
    public int hashCode() {
        return name.hashCode();  // nameが変わるとhashCodeも変わる
    }
}

Set<User> set = new HashSet<>();
User user = new User("Alice");
set.add(user);
user.setName("Bob");  // hashCodeが変わる
set.contains(user);   // false！見つからない
```

---

## 文字列の比較

### == で比較

```java
String s1 = "hello";
String s2 = "hello";
String s3 = new String("hello");

s1 == s2   // true（文字列リテラルはプールされる）
s1 == s3   // false！（新しいオブジェクト）
s1.equals(s3)  // true（常にequalsを使う）

// OK: 常にequalsを使う
if (s1.equals(s2)) { }

// OK: null安全
if (Objects.equals(s1, s2)) { }

// OK: 定数を左側に（null安全）
if ("expected".equals(input)) { }
```

### ループ内での文字列連結

```java
// NG: ループ内で + 連結（毎回新しいStringが生成される）
String result = "";
for (int i = 0; i < 10000; i++) {
    result += i + ",";  // O(n²)の計算量
}

// OK: StringBuilderを使う
StringBuilder sb = new StringBuilder();
for (int i = 0; i < 10000; i++) {
    sb.append(i).append(",");
}
String result = sb.toString();

// OK: Stream + Collectors.joining
String result = IntStream.range(0, 10000)
    .mapToObj(String::valueOf)
    .collect(Collectors.joining(","));
```

---

## コレクション

### ConcurrentModificationException

```java
// NG: イテレーション中に変更
List<String> list = new ArrayList<>(List.of("a", "b", "c"));
for (String item : list) {
    if (item.equals("b")) {
        list.remove(item);  // ConcurrentModificationException！
    }
}

// OK: Iteratorのremoveを使う
Iterator<String> it = list.iterator();
while (it.hasNext()) {
    if (it.next().equals("b")) {
        it.remove();
    }
}

// OK: removeIfを使う
list.removeIf(item -> item.equals("b"));
```

### Arrays.asListの制限

```java
// Arrays.asListは固定サイズ
List<String> list = Arrays.asList("a", "b", "c");
list.add("d");     // UnsupportedOperationException！
list.set(0, "x");  // これはOK

// 可変リストが必要な場合
List<String> mutableList = new ArrayList<>(Arrays.asList("a", "b", "c"));
mutableList.add("d");  // OK
```

### List.ofの制限

```java
// List.ofは不変
List<String> list = List.of("a", "b", "c");
list.add("d");     // UnsupportedOperationException！
list.set(0, "x");  // UnsupportedOperationException！

// nullも入れられない
List<String> list = List.of("a", null);  // NullPointerException！
```

---

## ボクシング

### Integer同士の==比較

```java
Integer a = 127;
Integer b = 127;
a == b   // true（-128〜127はキャッシュされる）

Integer c = 128;
Integer d = 128;
c == d   // false！（キャッシュ範囲外）
c.equals(d)  // true（常にequalsを使う）
```

### オートボクシングのパフォーマンス

```java
// NG: 大量のボクシング
Long sum = 0L;
for (long i = 0; i < 1000000; i++) {
    sum += i;  // 毎回ボクシング
}

// OK: プリミティブ型を使う
long sum = 0L;
for (long i = 0; i < 1000000; i++) {
    sum += i;
}
```

### nullのアンボクシング

```java
Integer value = null;
int primitive = value;  // NullPointerException！

// OK: nullチェック
int primitive = value != null ? value : 0;

// OK: Objects.requireNonNullElse
int primitive = Objects.requireNonNullElse(value, 0);
```

---

## その他

### finallyでのreturn

```java
// NG: finallyでreturnすると例外が握りつぶされる
public int getValue() {
    try {
        throw new RuntimeException("Error!");
    } finally {
        return 0;  // 例外が消える！
    }
}

// 呼び出し側は例外を受け取れない
int value = getValue();  // 0が返る、例外は発生しない
```

### 浮動小数点の比較

```java
// NG: 浮動小数点を==で比較
double a = 0.1 + 0.2;
double b = 0.3;
a == b   // false！（0.30000000000000004 != 0.3）

// OK: 許容誤差で比較
Math.abs(a - b) < 0.0001  // true

// OK: BigDecimalを使う（金額計算など）
BigDecimal a = new BigDecimal("0.1").add(new BigDecimal("0.2"));
BigDecimal b = new BigDecimal("0.3");
a.compareTo(b) == 0  // true
```

### try-catchで例外を握りつぶす

```java
// NG: 例外を無視
try {
    doSomething();
} catch (Exception e) {
    // 何もしない（最悪）
}

// NG: ログだけ出して続行
try {
    doSomething();
} catch (Exception e) {
    e.printStackTrace();  // 本番では意味がない
}

// OK: 適切に処理するか再スロー
try {
    doSomething();
} catch (Exception e) {
    logger.error("Failed to do something", e);
    throw new RuntimeException("Operation failed", e);
}
```

---

## まとめ

| カテゴリ | 落とし穴 | 対策 |
|---------|---------|------|
| メモリリーク | コレクションに追加のみ | 上限設定、削除処理 |
| メモリリーク | リスナー解除忘れ | close()で解除 |
| メモリリーク | リソースclose忘れ | try-with-resources |
| スレッドセーフ | 可変オブジェクト共有 | AtomicXxx、synchronized |
| スレッドセーフ | check-then-act | computeIfAbsent等 |
| スレッドセーフ | HashMap共有 | ConcurrentHashMap |
| NPE | nullチェック漏れ | Optional、Objects.requireNonNull |
| equals/hashCode | 片方だけ実装 | 両方実装、IDEで生成 |
| 文字列 | ==で比較 | equals()を使う |
| 文字列 | ループ内で+連結 | StringBuilder |
| コレクション | イテレーション中の変更 | removeIf、Iterator.remove |
| ボクシング | Integer同士の== | equals()を使う |
| 浮動小数点 | ==で比較 | 許容誤差、BigDecimal |

---

## 関連ドキュメント

- [例外処理](java-05-exceptions.md) - 例外処理のベストプラクティス
- [並行処理](java-08-concurrency.md) - スレッドセーフの詳細
- [JVM学習ガイド](../20-jvm/README.md) - メモリ管理、GCの詳細
