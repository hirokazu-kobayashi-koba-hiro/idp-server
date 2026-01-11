# パフォーマンス

## はじめに

JVMのパフォーマンス最適化は、GCチューニングだけでなく、JITコンパイラ、ウォームアップ、スレッド管理など多くの要素が関係します。本章では、これらの要素を総合的に解説します。

---

## JITコンパイラ

### 動作原理

JITコンパイラは、頻繁に実行される「ホットスポット」を検出し、ネイティブコードにコンパイルします。

```
┌─────────────────────────────────────────────────────────────────────┐
│                    JITコンパイルの流れ                               │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  実行開始                                                            │
│      │                                                               │
│      ↓                                                               │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │              インタプリタ実行                                  │  │
│  │  ・バイトコードを1命令ずつ解釈実行                            │  │
│  │  ・プロファイル情報を収集（呼び出し回数、分岐パターン等）     │  │
│  └───────────────────────────────────────────────────────────────┘  │
│      │                                                               │
│      │ 実行回数がしきい値超過（ホットスポット検出）                 │
│      ↓                                                               │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │              C1コンパイラ（クライアントコンパイラ）            │  │
│  │  ・高速コンパイル                                              │  │
│  │  ・軽度の最適化                                                │  │
│  │  ・さらにプロファイル情報を収集                                │  │
│  └───────────────────────────────────────────────────────────────┘  │
│      │                                                               │
│      │ さらに頻繁に実行される                                       │
│      ↓                                                               │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │              C2コンパイラ（サーバーコンパイラ）                │  │
│  │  ・時間をかけてコンパイル                                      │  │
│  │  ・積極的な最適化（インライン化、ループ展開等）               │  │
│  │  ・最高のパフォーマンス                                        │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### Tiered Compilation

```bash
# デフォルトで有効（Java 8以降）
-XX:+TieredCompilation

# 無効化（C2のみ使用）
-XX:-TieredCompilation
```

### コンパイルしきい値

```bash
# インタプリタからC1へのしきい値
-XX:Tier3InvocationThreshold=200

# C2への昇格しきい値
-XX:Tier4InvocationThreshold=5000
```

---

## ウォームアップ

### なぜウォームアップが必要か

```
┌─────────────────────────────────────────────────────────────────────┐
│                    ウォームアップの効果                              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  レスポンスタイム                                                    │
│        ▲                                                             │
│   200ms│  ●                                                          │
│        │    ●                                                        │
│   100ms│      ●  ●                                                   │
│        │          ●  ●  ●                                            │
│    50ms│                    ● ● ● ● ● ● ● ● ● ● ● ●                 │
│        │                                                             │
│        └──────────────────────────────────────────────────→ 時間     │
│           ↑                      ↑                                   │
│        起動直後                ウォームアップ完了                    │
│        (インタプリタ)          (JITコンパイル済み)                   │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### ウォームアップ戦略

```java
@Component
public class WarmupRunner implements ApplicationRunner {

    @Autowired
    private AuthenticationService authService;

    @Override
    public void run(ApplicationArguments args) {
        // 起動時にホットパスを事前実行
        for (int i = 0; i < 10000; i++) {
            try {
                authService.validateToken("warmup-token");
            } catch (Exception ignored) {
            }
        }
        log.info("Warmup completed");
    }
}
```

### Kubernetes での対応

```yaml
# Readiness Probeでウォームアップ完了を確認
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 60  # ウォームアップ時間
  periodSeconds: 5
```

---

## JITコンパイラの最適化

### インライン化

```java
// コンパイル前
public int calculate(int x) {
    return helper(x) + 10;
}

private int helper(int x) {
    return x * 2;
}

// インライン化後（JITによる最適化）
public int calculate(int x) {
    return (x * 2) + 10;  // helperがインライン展開
}
```

```bash
# インライン化の設定
-XX:MaxInlineSize=35           # メソッドサイズ上限（バイトコード）
-XX:FreqInlineSize=325         # ホットメソッドのサイズ上限
-XX:InlineSmallCode=2000       # コンパイル済みコードサイズ上限
```

### エスケープ解析

```java
public int process() {
    // Pointオブジェクトがメソッド外にエスケープしない
    Point p = new Point(10, 20);
    return p.x + p.y;
}

// JITによる最適化後（スカラー置換）
public int process() {
    int p_x = 10;
    int p_y = 20;
    return p_x + p_y;  // オブジェクト生成なし
}
```

---

## プロファイリング

### JFR（Java Flight Recorder）

```bash
# JFR記録開始
java -XX:StartFlightRecording=duration=60s,filename=recording.jfr -jar app.jar

# 実行中のアプリに対して記録開始
jcmd <pid> JFR.start duration=60s filename=recording.jfr

# 記録停止
jcmd <pid> JFR.stop

# 記録ダンプ
jcmd <pid> JFR.dump filename=recording.jfr
```

### JFRの分析（JDK Mission Control）

```bash
# JMC起動
jmc

# または
/path/to/jdk/bin/jmc
```

### async-profiler

```bash
# CPU プロファイリング（30秒）
./profiler.sh -d 30 -f profile.html <pid>

# メモリアロケーションプロファイリング
./profiler.sh -e alloc -d 30 -f alloc.html <pid>

# ロックプロファイリング
./profiler.sh -e lock -d 30 -f lock.html <pid>
```

---

## スレッド管理

### スレッドプールのサイズ

```
┌─────────────────────────────────────────────────────────────────────┐
│                   スレッドプールサイズの目安                         │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  CPUバウンドタスク                                                   │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │  スレッド数 = CPU コア数                                       │ │
│  │  例: 8コア → 8スレッド                                         │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                                                                      │
│  I/Oバウンドタスク                                                   │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │  スレッド数 = CPU コア数 × (1 + 待機時間/処理時間)             │ │
│  │  例: 8コア、待機80ms、処理20ms → 8 × (1 + 80/20) = 40スレッド  │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                                                                      │
│  Virtual Threads (Java 21+)                                          │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │  スレッド数を気にする必要なし                                  │ │
│  │  タスクごとにVirtual Threadを作成可能                          │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### Spring Boot でのスレッドプール設定

```yaml
# application.yml
server:
  tomcat:
    threads:
      max: 200        # 最大スレッド数
      min-spare: 10   # 最小スレッド数
    accept-count: 100 # 接続キューサイズ
```

---

## メモリ効率

### オブジェクト生成の削減

```java
// ❌ 悪い例：ループ内でオブジェクト生成
for (String item : items) {
    StringBuilder sb = new StringBuilder();
    sb.append(prefix).append(item);
    process(sb.toString());
}

// ✅ 良い例：オブジェクト再利用
StringBuilder sb = new StringBuilder();
for (String item : items) {
    sb.setLength(0);  // リセット
    sb.append(prefix).append(item);
    process(sb.toString());
}
```

### プリミティブ型の活用

```java
// ❌ 悪い例：オートボクシング
List<Integer> numbers = new ArrayList<>();
for (int i = 0; i < 1_000_000; i++) {
    numbers.add(i);  // int → Integer のボクシング
}

// ✅ 良い例：プリミティブ配列
int[] numbers = new int[1_000_000];
for (int i = 0; i < numbers.length; i++) {
    numbers[i] = i;
}

// または Eclipse Collections 等のプリミティブコレクション
IntList numbers = new IntArrayList();
```

---

## 文字列処理

### String の不変性を意識

```java
// ❌ 悪い例：文字列連結のループ
String result = "";
for (String s : strings) {
    result += s;  // 毎回新しいStringオブジェクトが生成される
}

// ✅ 良い例：StringBuilder
StringBuilder sb = new StringBuilder();
for (String s : strings) {
    sb.append(s);
}
String result = sb.toString();

// ✅ さらに良い例：String.join または Stream
String result = String.join("", strings);
```

### String Deduplication

```bash
# G1 GCで文字列重複排除を有効化
-XX:+UseStringDeduplication

# デフォルトの年齢閾値（この回数のGCを生き延びた文字列が対象）
-XX:StringDeduplicationAgeThreshold=3
```

---

## I/O最適化

### バッファリング

```java
// ❌ 悪い例：バッファなしの読み込み
try (FileInputStream fis = new FileInputStream(file)) {
    int b;
    while ((b = fis.read()) != -1) {
        process(b);
    }
}

// ✅ 良い例：バッファ付き
try (BufferedInputStream bis = new BufferedInputStream(
        new FileInputStream(file), 8192)) {
    int b;
    while ((b = bis.read()) != -1) {
        process(b);
    }
}

// ✅ さらに良い例：NIO
ByteBuffer buffer = ByteBuffer.allocateDirect(8192);
try (FileChannel channel = FileChannel.open(path)) {
    while (channel.read(buffer) > 0) {
        buffer.flip();
        processBuffer(buffer);
        buffer.clear();
    }
}
```

---

## パフォーマンス測定

### JMH（Java Microbenchmark Harness）

```java
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class StringBenchmark {

    private String[] strings;

    @Setup
    public void setup() {
        strings = new String[100];
        for (int i = 0; i < 100; i++) {
            strings[i] = "item" + i;
        }
    }

    @Benchmark
    public String concatenation() {
        String result = "";
        for (String s : strings) {
            result += s;
        }
        return result;
    }

    @Benchmark
    public String stringBuilder() {
        StringBuilder sb = new StringBuilder();
        for (String s : strings) {
            sb.append(s);
        }
        return sb.toString();
    }
}
```

```bash
# 実行
mvn clean install
java -jar target/benchmarks.jar
```

---

## 本番環境のパフォーマンス設定

```bash
java \
  # メモリ
  -Xms4g -Xmx4g \
  -XX:MaxMetaspaceSize=256m \

  # GC
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \

  # JIT
  -XX:+TieredCompilation \
  -XX:ReservedCodeCacheSize=256m \

  # 診断（本番でも有効に）
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/var/log/app/ \

  # JFR（常時記録）
  -XX:StartFlightRecording=disk=true,maxsize=500m,maxage=1d \

  # GCログ
  -Xlog:gc*:file=/var/log/app/gc.log:time,uptime,level,tags:filecount=10,filesize=100m \

  -jar app.jar
```

---

## まとめ

| 項目 | ポイント |
|-----|---------|
| JIT | ホットスポットを自動検出して最適化 |
| ウォームアップ | 起動後にホットパスを事前実行 |
| プロファイリング | JFR/async-profilerで問題箇所を特定 |
| スレッドプール | タスク特性に応じたサイズ設定 |
| メモリ効率 | オブジェクト生成の削減、プリミティブ活用 |
| 測定 | JMHでマイクロベンチマーク |

---

## 次のステップ

- [トラブルシューティング](jvm-07-troubleshooting.md) - 問題発生時の調査方法
- [Java 21新機能](jvm-08-java21.md) - Virtual Threadsによるスケーラビリティ向上
