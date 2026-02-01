# Java 並行処理

Javaの並行処理の基礎を学びます。

---

## なぜ並行処理か

シングルスレッドでは、重い処理中にアプリケーションが固まってしまいます。

```java
// シングルスレッド: 重い処理中は何もできない
String data = fetchFromNetwork();  // 3秒かかる
updateUI(data);  // その間UIは固まる

// マルチスレッド: 並行して処理
executor.submit(() -> {
    String data = fetchFromNetwork();
    runOnUiThread(() -> updateUI(data));
});
// UIは応答し続ける
```

並行処理を使うと、複数のタスクを同時に実行でき、CPUコアを有効活用できます。ただし、データ競合やデッドロックなど、新たな問題にも対処する必要があります。

---

## Thread の基礎

### Thread の作成

```java
// 方法1: Runnableを渡す（推奨）
Thread thread = new Thread(() -> {
    System.out.println("Running in: " + Thread.currentThread().getName());
});
thread.start();

// 方法2: Threadを継承（非推奨）
class MyThread extends Thread {
    @Override
    public void run() {
        System.out.println("Running");
    }
}
new MyThread().start();
```

### Thread の制御

```java
Thread thread = new Thread(() -> {
    try {
        Thread.sleep(1000);  // 1秒スリープ
        System.out.println("Done");
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }
});

thread.start();
thread.join();  // スレッドの終了を待つ
thread.join(5000);  // 最大5秒待つ

// 割り込み
thread.interrupt();
boolean interrupted = Thread.interrupted();  // 割り込みフラグをクリアして取得
boolean isInterrupted = thread.isInterrupted();  // フラグを取得（クリアしない）
```

### Thread の状態

```
NEW → RUNNABLE → (BLOCKED | WAITING | TIMED_WAITING) → TERMINATED
```

```java
Thread.State state = thread.getState();
```

---

## 同期化

### synchronized

```java
public class Counter {
    private int count = 0;

    // メソッド全体を同期化
    public synchronized void increment() {
        count++;
    }

    public synchronized int getCount() {
        return count;
    }
}

// ブロック単位の同期化
public class Counter {
    private final Object lock = new Object();
    private int count = 0;

    public void increment() {
        synchronized (lock) {
            count++;
        }
    }
}
```

### volatile

可視性を保証（キャッシュを介さず直接メモリアクセス）。

```java
public class Flag {
    private volatile boolean running = true;

    public void stop() {
        running = false;  // 他のスレッドから即座に見える
    }

    public void run() {
        while (running) {
            // ...
        }
    }
}
```

**注意**: volatile は可視性のみ。原子性は保証しない（`count++` は非原子的）。

---

## Atomic クラス

原子的な操作を提供。

```java
AtomicInteger counter = new AtomicInteger(0);

counter.incrementAndGet();  // ++counter
counter.getAndIncrement();  // counter++
counter.addAndGet(5);       // counter += 5
counter.compareAndSet(expected, newValue);  // CAS操作

// 複雑な更新
counter.updateAndGet(current -> current * 2);
counter.accumulateAndGet(5, (current, delta) -> current + delta);
```

```java
AtomicReference<User> userRef = new AtomicReference<>(initialUser);
userRef.set(newUser);
User current = userRef.get();
userRef.compareAndSet(expectedUser, newUser);
```

---

## ExecutorService

スレッドプールを管理。

### 基本的な使い方

```java
// 固定サイズのスレッドプール
ExecutorService executor = Executors.newFixedThreadPool(4);

// タスクの投入
executor.execute(() -> {
    System.out.println("Task executed");
});

// 結果を取得するタスク
Future<String> future = executor.submit(() -> {
    Thread.sleep(1000);
    return "Result";
});

String result = future.get();  // ブロックして結果を待つ
String result2 = future.get(5, TimeUnit.SECONDS);  // タイムアウト付き

// シャットダウン
executor.shutdown();  // 新規タスクを受け付けない
executor.awaitTermination(60, TimeUnit.SECONDS);  // 終了を待つ
executor.shutdownNow();  // 実行中のタスクも中断
```

### スレッドプールの種類

```java
// 固定サイズ
ExecutorService fixed = Executors.newFixedThreadPool(4);

// キャッシュ（必要に応じてスレッドを作成・再利用）
ExecutorService cached = Executors.newCachedThreadPool();

// シングルスレッド
ExecutorService single = Executors.newSingleThreadExecutor();

// スケジュール実行
ScheduledExecutorService scheduled = Executors.newScheduledThreadPool(2);
scheduled.schedule(() -> System.out.println("Delayed"), 5, TimeUnit.SECONDS);
scheduled.scheduleAtFixedRate(() -> System.out.println("Periodic"), 0, 1, TimeUnit.SECONDS);

// カスタム
ThreadPoolExecutor custom = new ThreadPoolExecutor(
    2,                      // コアプールサイズ
    10,                     // 最大プールサイズ
    60, TimeUnit.SECONDS,   // アイドルスレッドの生存時間
    new LinkedBlockingQueue<>(100)  // タスクキュー
);
```

### Virtual Threads（Java 21+）

軽量な仮想スレッド。大量の並行タスクに適する。

```java
// Virtual Thread を1つ作成
Thread vThread = Thread.startVirtualThread(() -> {
    System.out.println("Running in virtual thread");
});

// Virtual Thread 用の ExecutorService
try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
    for (int i = 0; i < 10000; i++) {
        executor.submit(() -> {
            // 各タスクが独自の仮想スレッドで実行
            Thread.sleep(1000);
            return "Done";
        });
    }
}  // 自動的にシャットダウン

// Thread.Builder
Thread.ofVirtual()
    .name("my-virtual-thread")
    .start(() -> System.out.println("Running"));
```

---

## Future と CompletableFuture

### Future

```java
ExecutorService executor = Executors.newFixedThreadPool(4);

Future<Integer> future = executor.submit(() -> {
    Thread.sleep(1000);
    return 42;
});

// 完了確認
boolean isDone = future.isDone();
boolean isCancelled = future.isCancelled();

// 結果取得（ブロッキング）
Integer result = future.get();
Integer resultWithTimeout = future.get(5, TimeUnit.SECONDS);

// キャンセル
future.cancel(true);  // true = 実行中でも割り込み
```

### CompletableFuture

非同期処理を合成可能。

```java
// 非同期実行
CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
    return fetchData();
});

// 変換
CompletableFuture<Integer> lengthFuture = future
    .thenApply(String::length);

// 副作用
future.thenAccept(System.out::println);

// チェーン
CompletableFuture<String> chain = CompletableFuture
    .supplyAsync(() -> fetchUserId())
    .thenApply(id -> fetchUserName(id))
    .thenApply(name -> "Hello, " + name);

// 例外処理
CompletableFuture<String> handled = future
    .exceptionally(ex -> "Error: " + ex.getMessage());

CompletableFuture<String> handled2 = future
    .handle((result, ex) -> {
        if (ex != null) {
            return "Error: " + ex.getMessage();
        }
        return result;
    });
```

### 複数の CompletableFuture

```java
CompletableFuture<String> f1 = CompletableFuture.supplyAsync(() -> "Result1");
CompletableFuture<String> f2 = CompletableFuture.supplyAsync(() -> "Result2");
CompletableFuture<String> f3 = CompletableFuture.supplyAsync(() -> "Result3");

// 両方完了を待つ
CompletableFuture<Void> both = f1.thenAcceptBoth(f2, (r1, r2) -> {
    System.out.println(r1 + ", " + r2);
});

// 結果を結合
CompletableFuture<String> combined = f1.thenCombine(f2, (r1, r2) -> r1 + r2);

// すべて完了を待つ
CompletableFuture<Void> all = CompletableFuture.allOf(f1, f2, f3);
all.thenRun(() -> {
    String r1 = f1.join();
    String r2 = f2.join();
    String r3 = f3.join();
});

// いずれか完了
CompletableFuture<Object> any = CompletableFuture.anyOf(f1, f2, f3);
```

### 非同期メソッドチェーン

```java
CompletableFuture<String> result = CompletableFuture
    .supplyAsync(() -> fetchUserId())           // IO操作
    .thenApplyAsync(id -> fetchUserDetails(id)) // 別の非同期操作
    .thenApplyAsync(details -> formatUser(details))
    .exceptionally(ex -> {
        log.error("Failed", ex);
        return "Unknown User";
    });
```

---

## Locks

より柔軟なロック機構。

### ReentrantLock

```java
private final ReentrantLock lock = new ReentrantLock();

public void doSomething() {
    lock.lock();
    try {
        // クリティカルセクション
    } finally {
        lock.unlock();
    }
}

// tryLock（ノンブロッキング）
if (lock.tryLock()) {
    try {
        // ...
    } finally {
        lock.unlock();
    }
} else {
    // ロック取得失敗
}

// タイムアウト付き
if (lock.tryLock(5, TimeUnit.SECONDS)) {
    try {
        // ...
    } finally {
        lock.unlock();
    }
}
```

### ReadWriteLock

読み取りは並行可、書き込みは排他。

```java
private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
private final Lock readLock = rwLock.readLock();
private final Lock writeLock = rwLock.writeLock();

public String read() {
    readLock.lock();
    try {
        return data;
    } finally {
        readLock.unlock();
    }
}

public void write(String newData) {
    writeLock.lock();
    try {
        data = newData;
    } finally {
        writeLock.unlock();
    }
}
```

### StampedLock（Java 8+）

楽観的読み取りをサポート。

```java
private final StampedLock lock = new StampedLock();
private double x, y;

// 楽観的読み取り
public double distanceFromOrigin() {
    long stamp = lock.tryOptimisticRead();
    double currentX = x, currentY = y;
    if (!lock.validate(stamp)) {
        // 楽観的読み取り失敗、悲観的に再取得
        stamp = lock.readLock();
        try {
            currentX = x;
            currentY = y;
        } finally {
            lock.unlockRead(stamp);
        }
    }
    return Math.sqrt(currentX * currentX + currentY * currentY);
}

// 書き込み
public void move(double deltaX, double deltaY) {
    long stamp = lock.writeLock();
    try {
        x += deltaX;
        y += deltaY;
    } finally {
        lock.unlockWrite(stamp);
    }
}
```

---

## 同期ユーティリティ

### CountDownLatch

カウントが0になるまで待機。

```java
CountDownLatch latch = new CountDownLatch(3);

// ワーカースレッド
for (int i = 0; i < 3; i++) {
    executor.submit(() -> {
        try {
            doWork();
        } finally {
            latch.countDown();  // カウントを減らす
        }
    });
}

// メインスレッド
latch.await();  // カウントが0になるまで待機
System.out.println("All workers completed");
```

### CyclicBarrier

全スレッドが到達するまで待機（繰り返し使用可能）。

```java
CyclicBarrier barrier = new CyclicBarrier(3, () -> {
    System.out.println("All threads reached barrier");
});

for (int i = 0; i < 3; i++) {
    executor.submit(() -> {
        doPhase1();
        barrier.await();  // 全員が到達するまで待機
        doPhase2();
        barrier.await();  // 再利用可能
        doPhase3();
    });
}
```

### Semaphore

同時アクセス数を制限。

```java
Semaphore semaphore = new Semaphore(3);  // 最大3つの許可

public void accessResource() {
    try {
        semaphore.acquire();  // 許可を取得（ブロッキング）
        // リソースにアクセス
    } finally {
        semaphore.release();  // 許可を解放
    }
}

// tryAcquire（ノンブロッキング）
if (semaphore.tryAcquire(5, TimeUnit.SECONDS)) {
    try {
        // ...
    } finally {
        semaphore.release();
    }
}
```

---

## Concurrent コレクション

### ConcurrentHashMap

```java
ConcurrentMap<String, Integer> map = new ConcurrentHashMap<>();

// 原子的な操作
map.putIfAbsent("key", 1);
map.computeIfAbsent("key", k -> expensiveComputation(k));
map.compute("key", (k, v) -> v == null ? 1 : v + 1);
map.merge("key", 1, Integer::sum);

// 並行イテレーション（ConcurrentModificationExceptionにならない）
map.forEach((k, v) -> System.out.println(k + "=" + v));
```

### BlockingQueue

生産者-消費者パターン。

```java
BlockingQueue<Task> queue = new LinkedBlockingQueue<>(100);

// 生産者
executor.submit(() -> {
    while (true) {
        Task task = createTask();
        queue.put(task);  // キューが満杯ならブロック
    }
});

// 消費者
executor.submit(() -> {
    while (true) {
        Task task = queue.take();  // キューが空ならブロック
        process(task);
    }
});

// タイムアウト付き
Task task = queue.poll(5, TimeUnit.SECONDS);
boolean added = queue.offer(task, 5, TimeUnit.SECONDS);
```

### CopyOnWriteArrayList

読み取りが多く、書き込みが少ない場合に有効。

```java
List<String> list = new CopyOnWriteArrayList<>();

// 書き込み時に内部配列をコピー
list.add("item");

// 読み取りはロック不要
for (String item : list) {
    // 安全にイテレーション
}
```

---

## デッドロックの回避

### デッドロックの例

```java
// スレッド1
synchronized (lockA) {
    synchronized (lockB) { }
}

// スレッド2
synchronized (lockB) {
    synchronized (lockA) { }  // デッドロック！
}
```

### 回避策

```java
// 1. ロックの順序を統一
Object[] locks = {lockA, lockB};
Arrays.sort(locks, (a, b) -> System.identityHashCode(a) - System.identityHashCode(b));
synchronized (locks[0]) {
    synchronized (locks[1]) {
        // ...
    }
}

// 2. tryLock でタイムアウト
if (lock1.tryLock(1, TimeUnit.SECONDS)) {
    try {
        if (lock2.tryLock(1, TimeUnit.SECONDS)) {
            try {
                // ...
            } finally {
                lock2.unlock();
            }
        }
    } finally {
        lock1.unlock();
    }
}

// 3. 単一のロックを使用
```

---

## ベストプラクティス

### 1. 不変オブジェクトを使う

```java
// 不変オブジェクトはスレッドセーフ
public final class ImmutableUser {
    private final String name;
    private final int age;

    public ImmutableUser(String name, int age) {
        this.name = name;
        this.age = age;
    }
    // getter のみ、setter なし
}
```

### 2. ロックの範囲を最小化

```java
// NG: ロック範囲が広すぎる
synchronized (lock) {
    String data = fetchFromNetwork();  // IO操作
    process(data);
}

// OK: 必要な部分のみロック
String data = fetchFromNetwork();
synchronized (lock) {
    process(data);
}
```

### 3. ExecutorService を使う（Thread を直接使わない）

```java
// NG
new Thread(() -> doSomething()).start();

// OK
executor.submit(() -> doSomething());
```

### 4. try-finally でロック解放を保証

```java
lock.lock();
try {
    // ...
} finally {
    lock.unlock();
}
```

---

## まとめ

| 概念 | 用途 |
|-----|------|
| synchronized | 基本的な同期化 |
| volatile | 可視性の保証 |
| Atomic* | 原子的な操作 |
| ExecutorService | スレッドプール管理 |
| Virtual Threads | 軽量な並行処理（Java 21+） |
| CompletableFuture | 非同期処理の合成 |
| Lock | 柔軟なロック |
| Concurrent* | スレッドセーフなコレクション |

---

## 次のステップ

- [Records](java-09-records.md) - 不変データクラス
