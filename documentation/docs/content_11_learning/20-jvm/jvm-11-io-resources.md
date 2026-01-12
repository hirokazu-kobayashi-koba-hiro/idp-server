# I/O とリソース管理

## はじめに

JVMアプリケーションでは、HTTPコネクションやJDBCコネクションなど、OSレベルのリソースを扱います。これらは**GCの管理対象外**であり、適切な管理が必要です。

---

## ヒープとヒープ外リソース

```
┌─────────────────────────────────────────────────────────────────────┐
│                    JVMリソースの分類                                 │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  JVMヒープ（GC管理）                 ヒープ外（GC管理外）            │
│  ┌─────────────────────┐            ┌─────────────────────┐         │
│  │ ・Javaオブジェクト  │            │ ・TCPソケット       │         │
│  │ ・配列              │            │ ・ファイルハンドル  │         │
│  │ ・文字列            │            │ ・Direct Buffer     │         │
│  │ ・コレクション      │            │ ・ネイティブメモリ  │         │
│  │                     │            │ ・DBコネクション    │         │
│  │  参照がなくなると   │            │                     │         │
│  │  GCが自動回収       │            │  明示的に close()   │         │
│  │                     │            │  しないと解放されない│         │
│  └─────────────────────┘            └─────────────────────┘         │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### リソースの種類

| リソース | Java での表現 | OS リソース |
|---------|--------------|-------------|
| ファイル | FileInputStream, FileChannel | ファイルディスクリプタ |
| ネットワーク | Socket, ServerSocket | TCPソケット |
| HTTP | HttpURLConnection, HttpClient | TCPソケット + SSL/TLS |
| データベース | Connection, Statement | TCP + DBセッション |
| プロセス | Process | プロセスID, パイプ |

---

## なぜ GC では回収されないのか

```
┌─────────────────────────────────────────────────────────────────────┐
│                    リソース管理の問題                                │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Connection conn = dataSource.getConnection();                       │
│                                                                      │
│  JVMヒープ                          OS / ネイティブ                  │
│  ┌─────────────────┐               ┌─────────────────┐              │
│  │ Connection obj  │ ───参照───→  │ TCP Socket      │              │
│  │ (HikariProxyConn│               │ File Descriptor │              │
│  │  ection)        │               │ DB Session      │              │
│  └─────────────────┘               └─────────────────┘              │
│          │                                  │                        │
│          ↓                                  ↓                        │
│    GCで回収可能                      GCでは回収できない              │
│    （参照がなくなれば）              （OSに返却が必要）              │
│                                                                      │
│  問題: Connection オブジェクトがGCされても、                         │
│        OSのソケットは解放されない → リソースリーク                   │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### Finalizer の問題

```java
// 昔のアプローチ（非推奨）
public class OldConnection {
    private int socketFd;

    @Override
    protected void finalize() throws Throwable {
        // GC時に呼ばれることを期待
        closeNativeSocket(socketFd);
    }
}
```

**Finalizer が危険な理由：**

| 問題 | 説明 |
|-----|------|
| 実行タイミング不定 | GCがいつ走るかわからない |
| 実行保証なし | finalize が呼ばれないこともある |
| パフォーマンス劣化 | Finalizer キューの処理オーバーヘッド |
| リソース枯渇 | GC前にOSリソースが枯渇する可能性 |

:::warning
**Java 9+ で非推奨**: `finalize()` は Java 9 で非推奨、Java 18 で削除予定です。代わりに `try-with-resources` や `Cleaner` を使用してください。
:::

---

## try-with-resources

### 基本的な使い方

```java
// 推奨：try-with-resources
try (Connection conn = dataSource.getConnection();
     PreparedStatement stmt = conn.prepareStatement(sql);
     ResultSet rs = stmt.executeQuery()) {

    while (rs.next()) {
        // データ処理
    }
}  // ← 自動的に rs.close(), stmt.close(), conn.close() が呼ばれる
   //   例外が発生しても確実にクローズされる
```

### 複数リソースの順序

```java
try (
    Connection conn = getConnection();      // 1番目に開く
    PreparedStatement stmt = prepare(conn); // 2番目に開く
    ResultSet rs = stmt.executeQuery()      // 3番目に開く
) {
    // 処理
}
// クローズは逆順: rs → stmt → conn
```

```
┌─────────────────────────────────────────────────────────────────────┐
│              try-with-resources のクローズ順序                       │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  開く順序                         閉じる順序                         │
│  ─────────                        ─────────                          │
│  1. Connection                    3. Connection.close()              │
│  2. PreparedStatement             2. PreparedStatement.close()       │
│  3. ResultSet                     1. ResultSet.close()               │
│                                                                      │
│  → 依存関係を考慮した正しい順序で自動クローズ                        │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### Suppressed Exceptions

```java
try (Resource r = new Resource()) {
    throw new RuntimeException("メイン処理で例外");
}  // close() でも例外が発生した場合

// 結果:
// RuntimeException: メイン処理で例外
//   Suppressed: IOException: クローズ中の例外
```

```java
// Suppressed Exception の取得
try {
    // ...
} catch (Exception e) {
    for (Throwable suppressed : e.getSuppressed()) {
        System.out.println("Suppressed: " + suppressed);
    }
}
```

---

## コネクションプーリング

### なぜプーリングが必要か

```
┌─────────────────────────────────────────────────────────────────────┐
│              コネクション作成のコスト                                │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  毎回新規作成の場合:                                                 │
│                                                                      │
│  アプリケーション              データベース                          │
│  ┌─────────────┐              ┌─────────────┐                       │
│  │ Request 1   │──TCP接続────→│             │  50-100ms             │
│  │             │←─認証完了────│             │                       │
│  │             │──クエリ─────→│             │  1-10ms               │
│  │             │←─結果───────│             │                       │
│  │             │──切断───────→│             │  10ms                 │
│  └─────────────┘              └─────────────┘                       │
│                                                                      │
│  プール使用の場合:                                                   │
│                                                                      │
│  ┌─────────────┐  ┌────────┐  ┌─────────────┐                       │
│  │ Request 1   │──│  Pool  │──│             │                       │
│  │             │  │ ┌────┐ │  │             │  プールから取得: 0.1ms│
│  │             │  │ │conn│ │  │             │  クエリ実行: 1-10ms   │
│  │             │  │ └────┘ │  │             │  プールに返却: 0.1ms  │
│  └─────────────┘  └────────┘  └─────────────┘                       │
│                                                                      │
│  → 接続確立のオーバーヘッドを削減                                    │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### HikariCP（JDBC コネクションプール）

```java
// HikariCP 設定
HikariConfig config = new HikariConfig();
config.setJdbcUrl("jdbc:postgresql://localhost:5432/mydb");
config.setUsername("user");
config.setPassword("password");

// プールサイズ設定
config.setMaximumPoolSize(10);      // 最大コネクション数
config.setMinimumIdle(5);            // 最小アイドルコネクション数
config.setIdleTimeout(300000);       // アイドルタイムアウト（5分）
config.setConnectionTimeout(30000);  // 接続タイムアウト（30秒）
config.setMaxLifetime(1800000);      // コネクション最大生存時間（30分）

// リーク検出
config.setLeakDetectionThreshold(60000);  // 60秒以上使用でリーク警告

HikariDataSource dataSource = new HikariDataSource(config);
```

### 適切なプールサイズ

```
┌─────────────────────────────────────────────────────────────────────┐
│              プールサイズの考え方                                    │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  公式: connections = (core_count * 2) + effective_spindle_count     │
│                                                                      │
│  例: 4コアCPU、SSD（spindle=1）の場合                               │
│      connections = (4 * 2) + 1 = 9                                  │
│                                                                      │
│  ポイント:                                                           │
│  ・大きすぎるプール = コンテキストスイッチ増加、性能低下             │
│  ・小さすぎるプール = コネクション待ち、スループット低下             │
│  ・I/O待ちが多い場合は若干大きめに                                   │
│                                                                      │
│  推奨: 10-20 程度から始めて、モニタリングしながら調整                │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## HTTP コネクション管理

### HttpClient（Java 11+）

```java
// HttpClient はコネクションプールを内蔵
HttpClient client = HttpClient.newBuilder()
    .version(HttpClient.Version.HTTP_2)
    .connectTimeout(Duration.ofSeconds(10))
    .executor(Executors.newVirtualThreadPerTaskExecutor())  // Virtual Threads
    .build();

// 同じ HttpClient インスタンスを再利用 → コネクション再利用
HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("https://api.example.com/data"))
    .timeout(Duration.ofSeconds(30))
    .GET()
    .build();

HttpResponse<String> response = client.send(request,
    HttpResponse.BodyHandlers.ofString());
```

### コネクション再利用の重要性

```
┌─────────────────────────────────────────────────────────────────────┐
│              HTTP コネクション再利用                                 │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Keep-Alive なし:                                                    │
│  ┌────────┐                              ┌────────┐                 │
│  │Request1│──TCP接続+TLS────────────────→│        │  200ms          │
│  │        │←─Response──────────────────│        │                 │
│  │        │──切断─────────────────────→│        │                 │
│  │Request2│──TCP接続+TLS────────────────→│ Server │  200ms          │
│  │        │←─Response──────────────────│        │                 │
│  └────────┘                              └────────┘                 │
│                                                                      │
│  Keep-Alive あり（HTTP/1.1）/ HTTP/2:                               │
│  ┌────────┐                              ┌────────┐                 │
│  │Request1│──TCP接続+TLS────────────────→│        │  200ms（初回）  │
│  │        │←─Response──────────────────│        │                 │
│  │Request2│──────────────────────────────→│ Server │  10ms（再利用） │
│  │        │←─Response──────────────────│        │                 │
│  │Request3│──────────────────────────────→│        │  10ms（再利用） │
│  │        │←─Response──────────────────│        │                 │
│  └────────┘                              └────────┘                 │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### Apache HttpClient（外部ライブラリ）

```java
// 明示的なコネクションプール管理
PoolingHttpClientConnectionManager connectionManager =
    new PoolingHttpClientConnectionManager();
connectionManager.setMaxTotal(100);           // 全体の最大コネクション数
connectionManager.setDefaultMaxPerRoute(20);  // ホストごとの最大コネクション数

CloseableHttpClient httpClient = HttpClients.custom()
    .setConnectionManager(connectionManager)
    .setKeepAliveStrategy((response, context) -> 30 * 1000)  // 30秒
    .build();

// アプリケーション終了時にクローズ
// httpClient.close();
```

### HttpClient を再利用しないとどうなるか

```java
// 危険：毎回新しい HttpClient を作成
public String fetchData(String url) {
    HttpClient client = HttpClient.newHttpClient();  // 毎回作成
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .build();
    return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
}

// 1000回呼び出すと...
for (int i = 0; i < 1000; i++) {
    fetchData("https://api.example.com/data");  // 1000個の HttpClient 作成
}
```

**発生する問題：**

```
┌─────────────────────────────────────────────────────────────────────┐
│              HttpClient 再利用しない場合の問題                       │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  1. パフォーマンス低下                                               │
│     ┌─────────────────────────────────────────────────────────────┐ │
│     │  毎回の処理:                                                 │ │
│     │  ・TCP 3-way handshake: 50-100ms                            │ │
│     │  ・TLS handshake: 100-200ms                                 │ │
│     │  ・実際のリクエスト: 10-50ms                                 │ │
│     │  ─────────────────────────────────                           │ │
│     │  合計: 160-350ms/リクエスト                                  │ │
│     │                                                              │ │
│     │  再利用した場合:                                             │ │
│     │  ・実際のリクエスト: 10-50ms                                 │ │
│     │  ─────────────────────────────────                           │ │
│     │  合計: 10-50ms/リクエスト（3-10倍高速）                      │ │
│     └─────────────────────────────────────────────────────────────┘ │
│                                                                      │
│  2. ソケット枯渇（TIME_WAIT 蓄積）                                   │
│     ┌─────────────────────────────────────────────────────────────┐ │
│     │  切断されたソケットは TIME_WAIT 状態で 60-120秒 残留         │ │
│     │                                                              │ │
│     │  $ netstat -an | grep TIME_WAIT | wc -l                     │ │
│     │  28547  ← 大量の TIME_WAIT ソケット                         │ │
│     │                                                              │ │
│     │  エラー: java.net.BindException:                            │ │
│     │          Address already in use (Bind failed)               │ │
│     │  または: Cannot assign requested address                    │ │
│     └─────────────────────────────────────────────────────────────┘ │
│                                                                      │
│  3. スレッドプール・メモリリーク                                     │
│     ┌─────────────────────────────────────────────────────────────┐ │
│     │  HttpClient は内部でスレッドプールを持つ                     │ │
│     │                                                              │ │
│     │  HttpClient 1 ─→ Executor (スレッド数個)                    │ │
│     │  HttpClient 2 ─→ Executor (スレッド数個)                    │ │
│     │  HttpClient 3 ─→ Executor (スレッド数個)                    │ │
│     │  ...                                                         │ │
│     │  HttpClient 1000 ─→ Executor (スレッド数個)                 │ │
│     │                                                              │ │
│     │  → 数千のスレッドが作成される                                │ │
│     │  → OutOfMemoryError: unable to create native thread         │ │
│     └─────────────────────────────────────────────────────────────┘ │
│                                                                      │
│  4. ファイルディスクリプタ枯渇                                       │
│     ┌─────────────────────────────────────────────────────────────┐ │
│     │  $ ulimit -n                                                 │ │
│     │  1024  ← デフォルトの上限                                   │ │
│     │                                                              │ │
│     │  1000個のソケット + 内部リソース → 上限超過                  │ │
│     │                                                              │ │
│     │  エラー: java.io.IOException: Too many open files           │ │
│     └─────────────────────────────────────────────────────────────┘ │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

**正しい使い方：**

```java
// アプリケーション全体で1つのインスタンスを共有
public class HttpClientProvider {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_2)
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    public static HttpClient getClient() {
        return CLIENT;
    }
}

// 使用側
public String fetchData(String url) {
    HttpClient client = HttpClientProvider.getClient();  // 再利用
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .build();
    return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
}
```

**Spring Boot での設定：**

```java
@Configuration
public class HttpClientConfig {

    @Bean
    public HttpClient httpClient() {
        return HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }
}

@Service
public class ExternalApiService {
    private final HttpClient httpClient;

    public ExternalApiService(HttpClient httpClient) {
        this.httpClient = httpClient;  // DI で注入、再利用される
    }
}
```

---

## Virtual Threads とコネクションプール

### 問題：プールサイズがボトルネック

```
┌─────────────────────────────────────────────────────────────────────┐
│              Virtual Threads とプールの問題                          │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  従来（Platform Threads）:                                           │
│  ・スレッド数: 200                                                   │
│  ・コネクションプール: 20                                            │
│  → 200スレッド中、最大20スレッドがDBアクセス可能                     │
│  → 残り180スレッドは他の処理 or 待機                                │
│                                                                      │
│  Virtual Threads 導入後:                                             │
│  ・Virtual Threads 数: 100,000+                                      │
│  ・コネクションプール: 20（変更なし）                                │
│  → 100,000スレッドが同時にDB接続を要求                              │
│  → 99,980スレッドがコネクション待ち → タイムアウト続出              │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  Virtual Thread 1  ─┐                                         │   │
│  │  Virtual Thread 2  ─┤                                         │   │
│  │  Virtual Thread 3  ─┤     ┌─────────────┐                     │   │
│  │  ...               ─┼────→│ Pool (20)   │────→ Database       │   │
│  │  Virtual Thread N  ─┤     └─────────────┘                     │   │
│  │                     │            ↑                             │   │
│  │  (100,000スレッド)  │      ボトルネック！                      │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### 解決策

#### 1. Semaphore で同時実行数を制限

```java
public class DatabaseService {
    private final DataSource dataSource;
    private final Semaphore semaphore;

    public DatabaseService(DataSource dataSource, int maxConcurrent) {
        this.dataSource = dataSource;
        this.semaphore = new Semaphore(maxConcurrent);  // 同時実行数を制限
    }

    public Result query(String sql) throws Exception {
        semaphore.acquire();  // 許可を取得（待機する可能性あり）
        try (Connection conn = dataSource.getConnection()) {
            // クエリ実行
            return executeQuery(conn, sql);
        } finally {
            semaphore.release();  // 許可を返却
        }
    }
}
```

#### 2. Bulkhead パターン

```java
// Resilience4j の Bulkhead
BulkheadConfig config = BulkheadConfig.custom()
    .maxConcurrentCalls(20)           // 最大同時実行数
    .maxWaitDuration(Duration.ofMillis(500))  // 待機時間
    .build();

Bulkhead bulkhead = Bulkhead.of("database", config);

Supplier<Result> decoratedSupplier = Bulkhead.decorateSupplier(
    bulkhead,
    () -> databaseService.query(sql)
);
```

#### 3. 接続タイムアウトの適切な設定

```java
// HikariCP のタイムアウト設定
HikariConfig config = new HikariConfig();
config.setConnectionTimeout(5000);   // 5秒でタイムアウト（長すぎない）
config.setMaximumPoolSize(20);

// タイムアウト時のフォールバック
try (Connection conn = dataSource.getConnection()) {
    // ...
} catch (SQLTransientConnectionException e) {
    // コネクション取得タイムアウト
    return fallbackResult();
}
```

---

## リソースリークの検出

### HikariCP のリーク検出

```java
// リーク検出を有効化
config.setLeakDetectionThreshold(60000);  // 60秒以上借りていたら警告

// ログ出力例:
// WARN  - Connection leak detection triggered for conn0:
//         ProxyConnection@12345
//         java.lang.Exception: Apparent connection leak detected
//         at com.example.UserService.findUser(UserService.java:42)
```

### ファイルディスクリプタの監視

```bash
# Linux: プロセスのファイルディスクリプタ数を確認
ls -la /proc/<pid>/fd | wc -l

# または lsof
lsof -p <pid> | wc -l

# macOS
lsof -p <pid> | wc -l
```

```java
// Java から OS のリソース状況を確認
OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
if (os instanceof UnixOperatingSystemMXBean unixOs) {
    long openFds = unixOs.getOpenFileDescriptorCount();
    long maxFds = unixOs.getMaxFileDescriptorCount();
    System.out.println("Open FDs: " + openFds + " / " + maxFds);
}
```

### JMX によるモニタリング

```java
// HikariCP のメトリクス
HikariPoolMXBean poolMXBean = dataSource.getHikariPoolMXBean();

System.out.println("Active: " + poolMXBean.getActiveConnections());
System.out.println("Idle: " + poolMXBean.getIdleConnections());
System.out.println("Total: " + poolMXBean.getTotalConnections());
System.out.println("Waiting: " + poolMXBean.getThreadsAwaitingConnection());
```

---

## OS リソース制限

### ファイルディスクリプタの上限

```bash
# 現在の制限を確認
ulimit -n        # soft limit
ulimit -Hn       # hard limit

# 制限を変更（一時的）
ulimit -n 65535

# 永続的な変更（/etc/security/limits.conf）
# appuser soft nofile 65535
# appuser hard nofile 65535
```

### Docker での設定

```yaml
# docker-compose.yml
services:
  app:
    image: myapp
    ulimits:
      nofile:
        soft: 65535
        hard: 65535
```

### よくあるエラー

```
┌─────────────────────────────────────────────────────────────────────┐
│              リソース枯渇のエラー例                                  │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ファイルディスクリプタ枯渇:                                         │
│  java.io.IOException: Too many open files                            │
│                                                                      │
│  コネクションプール枯渇:                                             │
│  java.sql.SQLTransientConnectionException:                           │
│    HikariPool-1 - Connection is not available,                       │
│    request timed out after 30000ms                                   │
│                                                                      │
│  ソケット枯渇:                                                       │
│  java.net.BindException: Address already in use                      │
│  java.net.ConnectException: Cannot assign requested address          │
│                                                                      │
│  対処:                                                               │
│  1. リソースリークを修正（try-with-resources の徹底）               │
│  2. プールサイズの適正化                                             │
│  3. OS の制限値を上げる（ulimit）                                    │
│  4. TIME_WAIT のチューニング（net.ipv4.tcp_tw_reuse）               │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Direct Buffer のメモリ管理

### NIO と Direct Buffer

```java
// Heap Buffer（GC対象）
ByteBuffer heapBuffer = ByteBuffer.allocate(1024);

// Direct Buffer（GC対象外）
ByteBuffer directBuffer = ByteBuffer.allocateDirect(1024);
```

```
┌─────────────────────────────────────────────────────────────────────┐
│              Heap Buffer vs Direct Buffer                            │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Heap Buffer:                                                        │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │  JVM Heap                              OS                    │    │
│  │  ┌─────────────┐                       ┌─────────────┐       │    │
│  │  │ ByteBuffer  │ ──コピー──→ 一時領域 → │ ソケット    │       │    │
│  │  │ (データ)    │                       │             │       │    │
│  │  └─────────────┘                       └─────────────┘       │    │
│  │                                                               │    │
│  │  → I/O時にヒープからネイティブ領域へのコピーが発生            │    │
│  └─────────────────────────────────────────────────────────────┘    │
│                                                                      │
│  Direct Buffer:                                                      │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │  Native Memory                         OS                    │    │
│  │  ┌─────────────┐                       ┌─────────────┐       │    │
│  │  │ ByteBuffer  │ ──直接参照──────────→ │ ソケット    │       │    │
│  │  │ (データ)    │                       │             │       │    │
│  │  └─────────────┘                       └─────────────┘       │    │
│  │                                                               │    │
│  │  → ゼロコピー（コピーなしで I/O）、高速                       │    │
│  └─────────────────────────────────────────────────────────────┘    │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### Direct Memory の制限

```bash
# Direct Memory の最大サイズ
java -XX:MaxDirectMemorySize=512m -jar app.jar

# デフォルトは -Xmx と同じ値
```

### Direct Buffer のリーク

```java
// 危険：Direct Buffer を大量に作成
public void processData(List<byte[]> dataList) {
    for (byte[] data : dataList) {
        // 毎回新しい Direct Buffer を作成 → メモリリーク
        ByteBuffer buffer = ByteBuffer.allocateDirect(data.length);
        buffer.put(data);
        // buffer は GC されにくい！
    }
}

// 改善：プールを使用 or 再利用
public class BufferPool {
    private final Queue<ByteBuffer> pool = new ConcurrentLinkedQueue<>();
    private final int bufferSize;

    public ByteBuffer acquire() {
        ByteBuffer buffer = pool.poll();
        if (buffer == null) {
            buffer = ByteBuffer.allocateDirect(bufferSize);
        }
        return buffer;
    }

    public void release(ByteBuffer buffer) {
        buffer.clear();
        pool.offer(buffer);
    }
}
```

---

## ベストプラクティス

### 1. 常に try-with-resources を使用

```java
// Good
try (Connection conn = dataSource.getConnection();
     PreparedStatement stmt = conn.prepareStatement(sql)) {
    // ...
}

// Bad
Connection conn = dataSource.getConnection();
try {
    // ...
} finally {
    conn.close();  // 例外で close されない可能性
}
```

### 2. コネクションプールを適切に設定

```java
HikariConfig config = new HikariConfig();
config.setMaximumPoolSize(10);              // 少なめに開始
config.setMinimumIdle(5);                   // アイドル維持
config.setConnectionTimeout(5000);          // 5秒タイムアウト
config.setLeakDetectionThreshold(30000);    // リーク検出
```

### 3. HttpClient を再利用

```java
// アプリケーション全体で1つのインスタンス
@Bean
public HttpClient httpClient() {
    return HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
}
```

### 4. Virtual Threads 使用時は同時実行数を制限

```java
private final Semaphore dbSemaphore = new Semaphore(20);

public Result executeQuery(String sql) throws Exception {
    dbSemaphore.acquire();
    try {
        return doQuery(sql);
    } finally {
        dbSemaphore.release();
    }
}
```

### 5. モニタリングを設定

```java
// JMX でプール状態を監視
@Scheduled(fixedRate = 60000)
public void logPoolStats() {
    HikariPoolMXBean pool = dataSource.getHikariPoolMXBean();
    log.info("DB Pool - Active: {}, Idle: {}, Waiting: {}",
        pool.getActiveConnections(),
        pool.getIdleConnections(),
        pool.getThreadsAwaitingConnection());
}
```

---

## まとめ

| 項目 | ポイント |
|-----|---------|
| ヒープ外リソース | GC で回収されない、明示的な close が必要 |
| try-with-resources | リソース管理の基本、必ず使用する |
| コネクションプール | 作成コスト削減、サイズは控えめに |
| Virtual Threads | プールがボトルネック、Semaphore で制限 |
| Direct Buffer | 高速だがリーク注意、プールで再利用 |
| モニタリング | JMX、リーク検出を有効化 |

---

## 次のステップ

- [メモリ管理](jvm-03-memory.md) - Direct Memory の詳細
- [トラブルシューティング](jvm-07-troubleshooting.md) - リソースリークの調査方法
- [GCチューニング](jvm-05-gc-tuning.md) - ヒープ外メモリとの関係
