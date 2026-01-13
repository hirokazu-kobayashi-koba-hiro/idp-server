# 同期プリミティブ

**所要時間**: 35分

**前提知識**: マルチスレッドプログラミングの基礎

**学べること**:
- 競合状態（Race Condition）
- mutex、セマフォ、条件変数
- デッドロックの原因と回避
- Java の synchronized との関係

---

## この章で答える疑問

```
「Race Condition って何？」
「mutex って何？」
「デッドロックはなぜ起きる？」
「Java の synchronized は何をしている？」
```

---

## 1. 競合状態（Race Condition）

### 1.1 問題の例

```
┌─────────────────────────────────────────────────────────────────────┐
│                    競合状態の例: カウンターのインクリメント          │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  共有変数: counter = 0                                              │
│                                                                      │
│  スレッドA         スレッドB         counter の値                   │
│  ─────────        ─────────        ────────────                     │
│  read counter                       0                               │
│  (値は 0)                                                           │
│                   read counter      0                               │
│                   (値は 0)                                          │
│  increment                          0 (メモリはまだ 0)              │
│  (ローカルで 1)                                                     │
│                   increment         0 (メモリはまだ 0)              │
│                   (ローカルで 1)                                    │
│  write counter                      1                               │
│                   write counter     1                               │
│                                                                      │
│  期待値: 2                                                          │
│  実際の値: 1  ← 間違い！                                            │
│                                                                      │
│  原因: counter++ は実際には3つの操作                                │
│  1. メモリから値を読む                                              │
│  2. 値をインクリメント                                              │
│  3. メモリに書き戻す                                                │
│  → この間に他のスレッドが割り込む可能性                            │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### 1.2 Java での再現

```java
// 競合状態の例
public class RaceConditionDemo {
    private static int counter = 0;

    public static void main(String[] args) throws Exception {
        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 100000; i++) {
                counter++;  // アトミックではない！
            }
        });

        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 100000; i++) {
                counter++;
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        System.out.println("Counter: " + counter);
        // 期待値: 200000
        // 実際: 100000〜200000 の間のランダムな値
    }
}
```

### 1.3 クリティカルセクション

```
┌─────────────────────────────────────────────────────────────────────┐
│                    クリティカルセクション                            │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  クリティカルセクション = 共有リソースにアクセスするコード部分       │
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │  スレッドA                     スレッドB                     │    │
│  │                                                              │    │
│  │  // 非クリティカル              // 非クリティカル            │    │
│  │  prepare_data();               prepare_data();               │    │
│  │                                                              │    │
│  │  ┌─────────────────┐          ┌─────────────────┐           │    │
│  │  │ // クリティカル │          │ // クリティカル │           │    │
│  │  │ counter++;      │ ←────── │ counter++;      │ 同時実行NG│    │
│  │  └─────────────────┘          └─────────────────┘           │    │
│  │                                                              │    │
│  │  // 非クリティカル              // 非クリティカル            │    │
│  │  process_result();             process_result();             │    │
│  │                                                              │    │
│  └─────────────────────────────────────────────────────────────┘    │
│                                                                      │
│  解決策: クリティカルセクションを相互排他（mutual exclusion）にする │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 2. Mutex（相互排他ロック）

### 2.1 Mutex の概念

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Mutex (Mutual Exclusion)                          │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Mutex = 一度に1つのスレッドだけがアクセスできるロック              │
│                                                                      │
│  操作:                                                               │
│  ├── lock(): ロックを取得（他が持っていたら待つ）                  │
│  └── unlock(): ロックを解放                                        │
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │                                                              │    │
│  │  スレッドA                     スレッドB                     │    │
│  │                                                              │    │
│  │  mutex.lock()   ←── 取得成功                                 │    │
│  │  │                              mutex.lock()                 │    │
│  │  │ counter++                    │                            │    │
│  │  │                              │ (ブロック、待機)           │    │
│  │  mutex.unlock() ─────────────→ │ ←── 取得成功               │    │
│  │                                 │ counter++                  │    │
│  │                                 mutex.unlock()               │    │
│  │                                                              │    │
│  └─────────────────────────────────────────────────────────────┘    │
│                                                                      │
│  結果: counter が正しく 2 になる                                    │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### 2.2 Java での実装

```java
// synchronized キーワード
public class Counter {
    private int count = 0;

    // メソッド全体を同期
    public synchronized void increment() {
        count++;  // これで安全
    }

    // または特定のブロックだけ
    public void incrementWithBlock() {
        // 他の処理

        synchronized (this) {
            count++;
        }

        // 他の処理
    }
}

// ReentrantLock を使う方法
import java.util.concurrent.locks.ReentrantLock;

public class Counter {
    private int count = 0;
    private final ReentrantLock lock = new ReentrantLock();

    public void increment() {
        lock.lock();
        try {
            count++;
        } finally {
            lock.unlock();  // 必ず unlock する
        }
    }
}
```

### 2.3 Java の synchronized の仕組み

```
┌─────────────────────────────────────────────────────────────────────┐
│                    synchronized の内部                               │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Java のすべてのオブジェクトは「モニター」を持つ                    │
│                                                                      │
│  synchronized (obj) {                                                │
│      // このブロック内では obj のモニターを保持                     │
│  }                                                                   │
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │  オブジェクトのヘッダー                                      │    │
│  │  ┌─────────────────────────────────────────────────────┐    │    │
│  │  │  Mark Word                                           │    │    │
│  │  │  ├── ロック状態（無効、バイアス、軽量、重量）        │    │    │
│  │  │  ├── 所有スレッドID                                  │    │    │
│  │  │  └── ハッシュコード                                  │    │    │
│  │  └─────────────────────────────────────────────────────┘    │    │
│  └─────────────────────────────────────────────────────────────┘    │
│                                                                      │
│  ロックの最適化（JVM）:                                             │
│  ├── バイアスロック: 単一スレッドなら軽量                          │
│  ├── 軽量ロック: 競合が少ない場合は CAS で                         │
│  └── 重量ロック: 競合が多い場合は OS の mutex                      │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 3. セマフォ

### 3.1 セマフォの概念

```
┌─────────────────────────────────────────────────────────────────────┐
│                    セマフォ (Semaphore)                              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  セマフォ = カウンター付きの同期プリミティブ                        │
│  └── N 個までのスレッドが同時にアクセス可能                        │
│                                                                      │
│  操作:                                                               │
│  ├── acquire() (P操作): カウンター減、0なら待つ                    │
│  └── release() (V操作): カウンター増                               │
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │  セマフォ (permits = 3)                                      │    │
│  │                                                              │    │
│  │  スレッドA: acquire() → permits = 2 → 入場                  │    │
│  │  スレッドB: acquire() → permits = 1 → 入場                  │    │
│  │  スレッドC: acquire() → permits = 0 → 入場                  │    │
│  │  スレッドD: acquire() → permits = 0 → 待機（ブロック）      │    │
│  │                                                              │    │
│  │  スレッドA: release() → permits = 1                         │    │
│  │  スレッドD: → permits = 0 → 入場可能に                      │    │
│  │                                                              │    │
│  └─────────────────────────────────────────────────────────────┘    │
│                                                                      │
│  Mutex = セマフォ (permits = 1) の特殊ケース                        │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### 3.2 使用例: コネクションプール

```java
import java.util.concurrent.Semaphore;

public class ConnectionPool {
    private final Semaphore semaphore;
    private final List<Connection> connections;

    public ConnectionPool(int maxConnections) {
        this.semaphore = new Semaphore(maxConnections);
        this.connections = new ArrayList<>();
        for (int i = 0; i < maxConnections; i++) {
            connections.add(createConnection());
        }
    }

    public Connection acquire() throws InterruptedException {
        semaphore.acquire();  // 許可を取得（なければ待つ）
        return getAvailableConnection();
    }

    public void release(Connection conn) {
        returnConnection(conn);
        semaphore.release();  // 許可を返却
    }
}
```

---

## 4. 条件変数

### 4.1 条件変数の概念

```
┌─────────────────────────────────────────────────────────────────────┐
│                    条件変数 (Condition Variable)                     │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  条件変数 = ある条件が満たされるまで待機する仕組み                  │
│                                                                      │
│  操作:                                                               │
│  ├── wait(): 条件が満たされるまで待機（ロックを一時解放）          │
│  ├── signal(): 待機中のスレッドを1つ起こす                         │
│  └── broadcast(): 待機中のスレッドを全部起こす                     │
│                                                                      │
│  例: 生産者-消費者問題                                               │
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │                                                              │    │
│  │  生産者                     キュー                 消費者    │    │
│  │  ───────                   ──────                 ───────    │    │
│  │                                                              │    │
│  │  データ作成                 [   ]                 wait()     │    │
│  │       │                    [   ]                    ↑        │    │
│  │       ▼                    [   ]                    │        │    │
│  │  enqueue()    ───────────→ [データ]                │        │    │
│  │       │                       │                     │        │    │
│  │       ▼                       │                     │        │    │
│  │  signal()     ────────────────┼─────────────────────┘        │    │
│  │                               │                              │    │
│  │                               ▼                              │    │
│  │                            dequeue()  ←─────── データ取得    │    │
│  │                                                              │    │
│  └─────────────────────────────────────────────────────────────┘    │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### 4.2 Java での実装

```java
import java.util.concurrent.locks.*;

public class BoundedBuffer<T> {
    private final Object[] items;
    private int count, putIndex, getIndex;

    private final Lock lock = new ReentrantLock();
    private final Condition notFull = lock.newCondition();
    private final Condition notEmpty = lock.newCondition();

    public BoundedBuffer(int capacity) {
        items = new Object[capacity];
    }

    public void put(T item) throws InterruptedException {
        lock.lock();
        try {
            // バッファが満杯なら待つ
            while (count == items.length) {
                notFull.await();
            }

            items[putIndex] = item;
            putIndex = (putIndex + 1) % items.length;
            count++;

            // 消費者に通知
            notEmpty.signal();

        } finally {
            lock.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    public T take() throws InterruptedException {
        lock.lock();
        try {
            // バッファが空なら待つ
            while (count == 0) {
                notEmpty.await();
            }

            T item = (T) items[getIndex];
            getIndex = (getIndex + 1) % items.length;
            count--;

            // 生産者に通知
            notFull.signal();

            return item;

        } finally {
            lock.unlock();
        }
    }
}
```

---

## 5. デッドロック

### 5.1 デッドロックとは

```
┌─────────────────────────────────────────────────────────────────────┐
│                    デッドロック (Deadlock)                           │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  複数のスレッドが互いのリソースを待ち合って永遠に止まる状態         │
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │                                                              │    │
│  │  スレッドA                           スレッドB               │    │
│  │                                                              │    │
│  │  lock(リソース1)                                             │    │
│  │  ↓                                   lock(リソース2)         │    │
│  │  リソース1 を保持                    ↓                       │    │
│  │  ↓                                   リソース2 を保持        │    │
│  │  lock(リソース2)                     lock(リソース1)         │    │
│  │  ↓                                   ↓                       │    │
│  │  待機...                             待機...                 │    │
│  │  (Bがリソース2を                     (Aがリソース1を         │    │
│  │   解放するまで)                       解放するまで)          │    │
│  │                                                              │    │
│  │         永遠に待ち続ける（デッドロック！）                   │    │
│  │                                                              │    │
│  └─────────────────────────────────────────────────────────────┘    │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### 5.2 デッドロックの4条件

```
┌─────────────────────────────────────────────────────────────────────┐
│                    デッドロック発生の4条件（Coffman条件）            │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  以下の4つが全て満たされるとデッドロックが発生する:                 │
│                                                                      │
│  1. 相互排他 (Mutual Exclusion)                                      │
│     └── リソースは一度に1つのスレッドだけが使用可能                │
│                                                                      │
│  2. 保持と待機 (Hold and Wait)                                       │
│     └── リソースを保持したまま別のリソースを待つ                   │
│                                                                      │
│  3. 横取り不可 (No Preemption)                                       │
│     └── 他スレッドからリソースを奪えない                           │
│                                                                      │
│  4. 循環待ち (Circular Wait)                                         │
│     └── A→B→C→A のように循環した待ち関係がある                    │
│                                                                      │
│  → どれか1つでも崩せばデッドロックを防げる                         │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### 5.3 デッドロックの回避

```java
// ❌ デッドロックが起きるコード
public void transfer(Account from, Account to, int amount) {
    synchronized (from) {
        synchronized (to) {
            from.withdraw(amount);
            to.deposit(amount);
        }
    }
}

// スレッドA: transfer(account1, account2, 100)  → account1, account2 の順
// スレッドB: transfer(account2, account1, 50)   → account2, account1 の順
// → デッドロック！

// ✅ 順序を固定して回避
public void transfer(Account from, Account to, int amount) {
    // 常に ID が小さい方を先にロック
    Account first = from.id < to.id ? from : to;
    Account second = from.id < to.id ? to : from;

    synchronized (first) {
        synchronized (second) {
            from.withdraw(amount);
            to.deposit(amount);
        }
    }
}

// ✅ tryLock でタイムアウト
public void transfer(Account from, Account to, int amount)
        throws InterruptedException {
    while (true) {
        if (from.lock.tryLock(1, TimeUnit.SECONDS)) {
            try {
                if (to.lock.tryLock(1, TimeUnit.SECONDS)) {
                    try {
                        from.withdraw(amount);
                        to.deposit(amount);
                        return;
                    } finally {
                        to.lock.unlock();
                    }
                }
            } finally {
                from.lock.unlock();
            }
        }
        // 取得できなかったらランダムに待ってリトライ
        Thread.sleep(ThreadLocalRandom.current().nextInt(100));
    }
}
```

### 5.4 デッドロックの検出

```bash
# Java スレッドダンプでデッドロック検出
$ jstack <pid>

Found one Java-level deadlock:
=============================
"Thread-1":
  waiting to lock monitor 0x00007f8b5c003e58 (object 0x00000000d7133848, a Account),
  which is held by "Thread-0"
"Thread-0":
  waiting to lock monitor 0x00007f8b5c003e78 (object 0x00000000d7133868, a Account),
  which is held by "Thread-1"

Java stack information for the threads listed above:
===================================================
"Thread-1":
    at transfer(Account.java:25)
    - waiting to lock <0x00000000d7133848> (a Account)
    - locked <0x00000000d7133868> (a Account)
"Thread-0":
    at transfer(Account.java:25)
    - waiting to lock <0x00000000d7133868> (a Account)
    - locked <0x00000000d7133848> (a Account)

Found 1 deadlock.
```

---

## 6. アトミック操作

### 6.1 CAS（Compare-And-Swap）

```
┌─────────────────────────────────────────────────────────────────────┐
│                    CAS (Compare-And-Swap)                            │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ロックを使わずにアトミックな更新を行う仕組み                       │
│                                                                      │
│  CAS(アドレス, 期待値, 新しい値):                                   │
│  1. アドレスの現在値が期待値と同じか確認                            │
│  2. 同じなら新しい値に更新（アトミックに）                          │
│  3. 違ったら失敗（他のスレッドが変更済み）                          │
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │                                                              │    │
│  │  スレッドA                     スレッドB                     │    │
│  │                                                              │    │
│  │  current = 0                                                 │    │
│  │  ↓                              current = 0                  │    │
│  │  CAS(addr, 0, 1)               ↓                             │    │
│  │  ↓ 成功！                       CAS(addr, 0, 1)              │    │
│  │  (値は 1 に)                    ↓ 失敗！                     │    │
│  │                                 (期待値0 ≠ 現在値1)          │    │
│  │                                 ↓                            │    │
│  │                                 リトライ                     │    │
│  │                                 current = 1                  │    │
│  │                                 CAS(addr, 1, 2)              │    │
│  │                                 ↓ 成功！                     │    │
│  │                                                              │    │
│  └─────────────────────────────────────────────────────────────┘    │
│                                                                      │
│  利点: ロックを使わないので軽量                                     │
│  欠点: 競合が多いとリトライが増える                                 │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### 6.2 Java のアトミッククラス

```java
import java.util.concurrent.atomic.*;

// AtomicInteger
AtomicInteger counter = new AtomicInteger(0);
counter.incrementAndGet();  // アトミックにインクリメント
counter.addAndGet(5);       // アトミックに加算
counter.compareAndSet(5, 10);  // CAS

// AtomicReference
AtomicReference<String> ref = new AtomicReference<>("initial");
ref.compareAndSet("initial", "updated");

// AtomicLong
AtomicLong longCounter = new AtomicLong(0);

// LongAdder（高競合時に AtomicLong より高速）
LongAdder adder = new LongAdder();
adder.increment();
adder.sum();  // 値を取得
```

---

## 7. Linux での確認

```bash
# プロセスのロック状態を確認
$ cat /proc/locks
1: FLOCK  ADVISORY  WRITE 1234 08:01:12345 0 EOF
2: POSIX  ADVISORY  WRITE 5678 08:01:67890 0 0

# futex（Fast Userspace muTEX）の統計
$ cat /proc/vmstat | grep futex

# スレッドの状態を確認
$ ps -eLf | grep myapp
UID   PID  PPID   LWP  C NLWP STIME TTY      TIME CMD
app  1234  5678  1234  0   10 10:00 ?    00:00:01 myapp
app  1234  5678  1235  0   10 10:00 ?    00:00:00 myapp
# LWP = スレッド ID

# スレッドのスタックトレース
$ cat /proc/1234/task/1235/stack
[<0000000000000000>] futex_wait_queue_me+0x...
[<0000000000000000>] futex_wait+0x...
[<0000000000000000>] do_futex+0x...
# futex_wait が見えたらロック待ち
```

---

## 8. まとめ

```
┌─────────────────────────────────────────────────────────────────────┐
│                    この章で学んだこと                                │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  1. 競合状態（Race Condition）                                      │
│     ├── 複数スレッドが同じデータにアクセス                         │
│     └── 結果が実行順序に依存してしまう                             │
│                                                                      │
│  2. Mutex                                                            │
│     ├── 一度に1スレッドだけがアクセス可能                          │
│     └── Java: synchronized, ReentrantLock                          │
│                                                                      │
│  3. セマフォ                                                         │
│     ├── N個までのスレッドが同時アクセス可能                        │
│     └── コネクションプールなどで使用                               │
│                                                                      │
│  4. 条件変数                                                         │
│     ├── 条件が満たされるまで待機                                   │
│     └── 生産者-消費者パターンで使用                                │
│                                                                      │
│  5. デッドロック                                                     │
│     ├── 4条件が揃うと発生                                          │
│     ├── ロック順序の固定で回避                                     │
│     └── jstack で検出                                              │
│                                                                      │
│  6. アトミック操作                                                   │
│     ├── CAS で ロックなしの同期                                    │
│     └── Java: AtomicInteger, AtomicReference                       │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 確認問題

1. Race Condition が起きる原因は？
2. Mutex とセマフォの違いは？
3. デッドロックの4条件は？
4. デッドロックを防ぐ方法を2つ挙げてください
5. CAS はどのような場面で使われますか？

---

## 関連リソース

- [Java Concurrency in Practice](https://jcip.net/) - 並行プログラミングのバイブル
- [The Art of Multiprocessor Programming](https://www.amazon.com/Art-Multiprocessor-Programming-Revised-Reprint/dp/0123973376) - 並行アルゴリズムの教科書
