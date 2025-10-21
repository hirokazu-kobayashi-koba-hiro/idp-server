# スレッドセーフ性チェックリスト

## 🚨 アンチパターン一覧

### Pattern 1: Mutable Static Fields（可変static フィールド）
```java
// ❌ 危険: 複数スレッドから同時アクセス可能
public static List<String> values = new ArrayList<>();
public static Map<String, Object> cache = new HashMap<>();
public static int counter = 0;

// ✅ 安全: immutable or thread-safe
public static final List<String> VALUES = List.of("a", "b");
public static final Map<String, Object> CACHE = new ConcurrentHashMap<>();
private static final AtomicInteger counter = new AtomicInteger(0);
```

**検出方法**:
```bash
grep -rn "static.*\(List\|Map\|Set\).*=.*new.*\(ArrayList\|HashMap\|HashSet\)" --include="*.java" libs/
grep -rn "static.*\(int\|long\|boolean\).*=" --include="*.java" libs/ | grep -v final
```

---

### Pattern 2: Non-Thread-Safe Collections（非スレッドセーフコレクション）
```java
// ❌ 危険: HashMapは同期化されない
private Map<String, User> userCache = new HashMap<>();

// ❌ 危険: ArrayListは同期化されない
private List<Event> events = new ArrayList<>();

// ✅ 安全: ConcurrentHashMap使用
private Map<String, User> userCache = new ConcurrentHashMap<>();

// ✅ 安全: CopyOnWriteArrayList使用（読み取り多・書き込み少）
private List<Event> events = new CopyOnWriteArrayList<>();

// ✅ 安全: Collections.synchronizedMap使用
private Map<String, User> userCache = Collections.synchronizedMap(new HashMap<>());
```

**検出方法**:
```bash
grep -rn "new HashMap<>" --include="*.java" libs/ | grep -v "local variable"
grep -rn "new ArrayList<>" --include="*.java" libs/ | grep -v "local variable"
```

---

### Pattern 3: SimpleDateFormat（非スレッドセーフな日付フォーマッタ）
```java
// ❌ 危険: SimpleDateFormatはスレッドセーフでない
private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

// ✅ 安全: DateTimeFormatterはスレッドセーフ
private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

// ✅ 安全: ThreadLocal使用（レガシーコード互換）
private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT =
    ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd"));
```

**検出方法**:
```bash
grep -rn "SimpleDateFormat" --include="*.java" libs/
```

---

### Pattern 4: Lazy Initialization without Synchronization（同期化なし遅延初期化）
```java
// ❌ 危険: ダブルチェックロッキングなし
private static Instance instance;
public static Instance getInstance() {
    if (instance == null) {
        instance = new Instance();  // 複数回実行される可能性
    }
    return instance;
}

// ✅ 安全: volatile + ダブルチェックロッキング
private static volatile Instance instance;
public static Instance getInstance() {
    if (instance == null) {
        synchronized (Instance.class) {
            if (instance == null) {
                instance = new Instance();
            }
        }
    }
    return instance;
}

// ✅ 安全: Initialization-on-demand holder idiom
private static class Holder {
    static final Instance INSTANCE = new Instance();
}
public static Instance getInstance() {
    return Holder.INSTANCE;
}
```

**検出方法**:
```bash
grep -rn "private.*static.*instance.*=.*null" --include="*.java" libs/
grep -A5 "getInstance()" libs/**/*.java | grep -B3 "if.*==.*null"
```

---

### Pattern 5: Shared Mutable State in Services（サービス内共有可変状態）
```java
// ❌ 危険: インスタンスフィールドでリクエスト状態保持
@Service
public class UserService {
    private User currentUser;  // 複数リクエストで競合

    public void processUser(User user) {
        this.currentUser = user;
        // ...
    }
}

// ✅ 安全: ローカル変数のみ使用
@Service
public class UserService {
    public void processUser(User user) {
        // ローカル変数はスレッドセーフ
        String userId = user.id();
    }
}

// ✅ 安全: ThreadLocal使用（必要な場合のみ）
@Service
public class UserService {
    private ThreadLocal<User> currentUser = new ThreadLocal<>();
}
```

**検出方法**:
```bash
grep -rn "@Service\|@Component" --include="*.java" -A20 libs/ | grep "private.*[^final]"
```

---

### Pattern 6: Race Conditions in Check-Then-Act（チェック後実行の競合）
```java
// ❌ 危険: チェックと実行の間に他スレッドが介入可能
if (!cache.containsKey(key)) {
    cache.put(key, value);  // 複数スレッドが同時実行
}

// ✅ 安全: Atomic操作使用
cache.putIfAbsent(key, value);

// ✅ 安全: 同期化
synchronized (cache) {
    if (!cache.containsKey(key)) {
        cache.put(key, value);
    }
}
```

**検出方法**:
```bash
grep -rn "if.*containsKey" -A2 --include="*.java" libs/ | grep "put("
grep -rn "if.*isEmpty()" -A2 --include="*.java" libs/ | grep "add("
```

---

### Pattern 7: Mutable Static Collections in Static Initializer（静的初期化ブロックの可変コレクション）
```java
// ❌ 危険: 後から変更可能
public static Map<String, String> CONFIG = new HashMap<>();
static {
    CONFIG.put("key1", "value1");
}

// ✅ 安全: Immutable化
public static final Map<String, String> CONFIG = Map.of(
    "key1", "value1",
    "key2", "value2"
);

// ✅ 安全: Collections.unmodifiableMap
public static final Map<String, String> CONFIG;
static {
    Map<String, String> temp = new HashMap<>();
    temp.put("key1", "value1");
    CONFIG = Collections.unmodifiableMap(temp);
}
```

**検出方法**:
```bash
grep -rn "static.*Map.*=.*new.*HashMap" --include="*.java" libs/ | grep -v "final.*unmodifiable"
```

---

## 🔍 検出コマンド一覧

### 1. Mutable Static Fields
```bash
find libs -name "*.java" -type f -exec grep -Hn "static.*\(List\|Map\|Set\).*=.*new" {} \; | grep -v final | grep -v test
```

### 2. Non-Thread-Safe Collections (instance fields)
```bash
find libs -name "*.java" -type f -exec grep -Hn "private.*Map<.*>.*=.*new HashMap" {} \; | grep -v test
```

### 3. SimpleDateFormat
```bash
find libs -name "*.java" -type f -exec grep -Hn "SimpleDateFormat" {} \; | grep -v test
```

### 4. Lazy Initialization
```bash
find libs -name "*.java" -type f -exec grep -Hn "private static.*instance.*=" {} \; | grep -v test
```

### 5. Check-Then-Act Pattern
```bash
find libs -name "*.java" -type f -exec grep -B1 -A1 "containsKey\|isEmpty" {} \; | grep -A1 "if (" | grep "put\|add" | grep -v test
```

---

## 📊 チェック実行順序

1. **Pattern 1**: Mutable Static Fields（最優先・最も危険）
2. **Pattern 3**: SimpleDateFormat（確実に問題）
3. **Pattern 4**: Lazy Initialization（シングルトン系）
4. **Pattern 2**: Non-Thread-Safe Collections（範囲が広い）
5. **Pattern 6**: Race Conditions（検出難度高）
6. **Pattern 5**: Shared Mutable State（設計レビュー必要）

---

## ✅ 次のステップ

1. Pattern 1-4を自動検出（高優先度）
2. 検出された箇所をマニュアルレビュー
3. 修正PRを作成（パターン別）
4. Pattern 5-6を設計レビュー（低頻度だが影響大）
