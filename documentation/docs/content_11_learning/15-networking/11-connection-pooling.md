# コネクションプーリング詳細

## 所要時間
約45分

## 学べること
- なぜコネクションプーリングが必要なのか
- データベース接続プール（HikariCP）の仕組みと設定
- HTTPクライアントのコネクションプール
- Redis等のコネクションプール
- 適切なプールサイズの決め方
- トラブルシューティング

## 前提知識
- [09-tcp-fundamentals.md](./09-tcp-fundamentals.md) - TCP基礎（3ウェイハンドシェイク、TIME_WAIT）
- [10-web-server-architecture.md](./10-web-server-architecture.md) - Webサーバーアーキテクチャ

---

## 1. なぜコネクションプーリングが必要か

### 1.1 接続のコスト

```
┌─────────────────────────────────────────────────────────────┐
│              TCP接続のオーバーヘッド                         │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  【接続ごとに発生するコスト】                                │
│                                                              │
│  1. TCP 3ウェイハンドシェイク                               │
│     Client ──SYN──────────► Server                          │
│     Client ◄──SYN+ACK────── Server    ← 1 RTT              │
│     Client ──ACK──────────► Server                          │
│                                                              │
│  2. TLSハンドシェイク（HTTPS/TLS接続の場合）                │
│     追加で 1〜2 RTT                                         │
│                                                              │
│  3. 認証（データベースの場合）                               │
│     ユーザー名/パスワード検証、セッション確立               │
│                                                              │
│  4. 切断時の TIME_WAIT                                      │
│     切断後 60秒〜120秒 ソケットが残存                       │
│                                                              │
│  ─────────────────────────────────────────────────────────  │
│                                                              │
│  【具体的な時間】                                            │
│                                                              │
│  同一データセンター内:                                       │
│  - TCP接続: 0.5ms〜1ms                                      │
│  - TLS追加: 1ms〜2ms                                        │
│  - PostgreSQL認証: 5ms〜10ms                                │
│  → 合計: 約10ms のオーバーヘッド                            │
│                                                              │
│  クロスリージョン（東京↔バージニア）:                       │
│  - RTT: 約150ms                                             │
│  - TCP + TLS: 300ms〜450ms                                  │
│  → 合計: 数百ms のオーバーヘッド                            │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 プールなしの問題

```
┌─────────────────────────────────────────────────────────────┐
│              プールなしの動作                                │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  リクエストごとに接続・切断                                  │
│                                                              │
│  Request 1:                                                  │
│    [接続 10ms][クエリ 2ms][切断] → 合計 12ms+               │
│                                                              │
│  Request 2:                                                  │
│    [接続 10ms][クエリ 2ms][切断] → 合計 12ms+               │
│                                                              │
│  Request 3:                                                  │
│    [接続 10ms][クエリ 2ms][切断] → 合計 12ms+               │
│                                                              │
│  問題:                                                       │
│  - 接続オーバーヘッドがクエリ時間より長い                   │
│  - TIME_WAIT でソケット枯渇のリスク                         │
│  - DBサーバーへの接続数が制御できない                       │
│  - 高負荷時にDB接続上限に達する                             │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 1.3 プールありの動作

```
┌─────────────────────────────────────────────────────────────┐
│              プールありの動作                                │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  起動時に接続を確立してプール                                │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  Connection Pool                                    │    │
│  │  ┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐               │    │
│  │  │Conn│ │Conn│ │Conn│ │Conn│ │Conn│  (idle)       │    │
│  │  │ 1  │ │ 2  │ │ 3  │ │ 4  │ │ 5  │               │    │
│  │  └────┘ └────┘ └────┘ └────┘ └────┘               │    │
│  └─────────────────────────────────────────────────────┘    │
│       ↑                                                     │
│       │ borrow                                              │
│       │                                                     │
│  Request 1:                                                  │
│    [借用 0.01ms][クエリ 2ms][返却] → 合計 2ms               │
│                                                              │
│  Request 2:                                                  │
│    [借用 0.01ms][クエリ 2ms][返却] → 合計 2ms               │
│                                                              │
│  効果:                                                       │
│  - 接続オーバーヘッドがほぼゼロ                             │
│  - TIME_WAIT 問題なし（接続を維持するため）                 │
│  - 接続数の上限を制御可能                                   │
│  - DBサーバーへの負荷を予測可能                             │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. データベース接続プール（HikariCP）

### 2.1 HikariCP の基本

```
┌─────────────────────────────────────────────────────────────┐
│              HikariCP のアーキテクチャ                       │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Spring Boot Application                                     │
│       │                                                     │
│       ▼                                                     │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  HikariDataSource                                   │    │
│  │                                                     │    │
│  │  ┌───────────────────────────────────────────────┐  │    │
│  │  │  ConcurrentBag (コネクション管理)             │  │    │
│  │  │                                               │  │    │
│  │  │  ┌────────────────────────────────────────┐   │  │    │
│  │  │  │ Connection[] (プール)                  │   │  │    │
│  │  │  │                                        │   │  │    │
│  │  │  │  [0] IDLE     ← 借用可能              │   │  │    │
│  │  │  │  [1] IN_USE   ← 使用中                │   │  │    │
│  │  │  │  [2] IDLE     ← 借用可能              │   │  │    │
│  │  │  │  [3] IN_USE   ← 使用中                │   │  │    │
│  │  │  │  [4] IDLE     ← 借用可能              │   │  │    │
│  │  │  │  ...                                   │   │  │    │
│  │  │  └────────────────────────────────────────┘   │  │    │
│  │  │                                               │  │    │
│  │  │  HouseKeeper (定期メンテナンス)               │  │    │
│  │  │  - アイドル接続の削除                         │  │    │
│  │  │  - 接続の有効性チェック                       │  │    │
│  │  └───────────────────────────────────────────────┘  │    │
│  │                                                     │    │
│  └─────────────────────────────────────────────────────┘    │
│       │                                                     │
│       │ TCP接続（維持）                                     │
│       ▼                                                     │
│  PostgreSQL / MySQL                                         │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 HikariCP 設定（Spring Boot）

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/mydb
    username: myuser
    password: ${DB_PASSWORD}

    hikari:
      # プールサイズ
      minimum-idle: 5          # 最小アイドル接続数
      maximum-pool-size: 10    # 最大接続数

      # タイムアウト
      connection-timeout: 30000      # 接続取得タイムアウト (ms)
      idle-timeout: 600000           # アイドル接続の生存時間 (ms)
      max-lifetime: 1800000          # 接続の最大生存時間 (ms)

      # 検証
      validation-timeout: 5000       # 接続検証タイムアウト (ms)

      # その他
      pool-name: MyAppPool           # プール名（ログで識別用）
      auto-commit: true              # 自動コミット
```

### 2.3 各設定の意味

```
┌─────────────────────────────────────────────────────────────┐
│              HikariCP 設定パラメータ詳細                     │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  【maximum-pool-size】                                       │
│  プールが持てる最大接続数                                    │
│                                                              │
│  - 全リクエストがこの数を超えると待機が発生                 │
│  - DBサーバーの max_connections も考慮必要                  │
│  - アプリ複数台の場合: 全アプリの合計 < DB上限              │
│                                                              │
│  例: アプリ5台 × pool-size 10 = 50接続                     │
│      → PostgreSQL max_connections は 100 以上必要           │
│                                                              │
│  ─────────────────────────────────────────────────────────  │
│                                                              │
│  【minimum-idle】                                            │
│  維持する最小アイドル接続数                                  │
│                                                              │
│  - 負荷が低い時でもこの数は維持                             │
│  - コールドスタート回避（最初のリクエストが速い）           │
│  - maximum-pool-size と同じにするのが HikariCP 推奨         │
│                                                              │
│  ─────────────────────────────────────────────────────────  │
│                                                              │
│  【connection-timeout】                                      │
│  プールから接続を取得するまでの最大待機時間                  │
│                                                              │
│  - タイムアウトすると SQLException 発生                     │
│  - 30秒がデフォルト                                         │
│  - 短すぎると高負荷時にエラー多発                           │
│  - 長すぎるとリクエストがタイムアウト                       │
│                                                              │
│  ─────────────────────────────────────────────────────────  │
│                                                              │
│  【max-lifetime】                                            │
│  接続の最大生存時間                                          │
│                                                              │
│  - この時間を過ぎた接続は破棄→再作成                       │
│  - DBの wait_timeout より短くする                           │
│  - PostgreSQLデフォルト: 無制限                             │
│  - MySQL デフォルト: 8時間 (28800秒)                        │
│                                                              │
│  推奨: DBの wait_timeout - 30秒                             │
│                                                              │
│  ─────────────────────────────────────────────────────────  │
│                                                              │
│  【idle-timeout】                                            │
│  アイドル接続が削除されるまでの時間                          │
│                                                              │
│  - minimum-idle を下回らない範囲で削除                      │
│  - minimum-idle = maximum-pool-size なら無効                │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 2.4 適切なプールサイズの決め方

```
┌─────────────────────────────────────────────────────────────┐
│              プールサイズの決め方                            │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  【公式（HikariCP作者推奨）】                                │
│                                                              │
│  connections = (core_count * 2) + effective_spindle_count   │
│                                                              │
│  - core_count: CPUコア数                                    │
│  - effective_spindle_count: HDDなら1、SSDなら0              │
│                                                              │
│  例: 4コアCPU + SSD = (4 * 2) + 0 = 8接続                   │
│                                                              │
│  ─────────────────────────────────────────────────────────  │
│                                                              │
│  【現実的な考え方】                                          │
│                                                              │
│  スレッド数とのバランス:                                     │
│                                                              │
│  Tomcat max-threads: 200                                    │
│  DB pool-size: 10                                           │
│                                                              │
│  → 200スレッドが同時にDB接続を要求すると                   │
│    190スレッドが待機状態になる                              │
│                                                              │
│  対策:                                                       │
│  1. クエリを速くする（インデックス最適化）                  │
│  2. プールサイズを適切に増やす                              │
│  3. スレッド数を減らす                                      │
│  4. 非同期処理でDBアクセスを減らす                          │
│                                                              │
│  ─────────────────────────────────────────────────────────  │
│                                                              │
│  【よくある設定例】                                          │
│                                                              │
│  小規模アプリ（1台）:                                        │
│    maximum-pool-size: 10                                    │
│                                                              │
│  中規模アプリ（5〜10台）:                                    │
│    maximum-pool-size: 10〜20                                │
│    ※ 全アプリ合計がDB上限を超えないこと                    │
│                                                              │
│  大規模（PgBouncer使用）:                                    │
│    アプリ側: 20〜50                                         │
│    PgBouncer で集約して DB へ                               │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. コネクションプロキシ（PgBouncer）

### 3.1 なぜ必要か

```
┌─────────────────────────────────────────────────────────────┐
│              PgBouncer の役割                                │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  【問題: アプリ台数が増えると接続数が爆発】                  │
│                                                              │
│  App 1 (pool=20) ──┐                                        │
│  App 2 (pool=20) ──┼──► PostgreSQL (max_connections=100)   │
│  App 3 (pool=20) ──┤                                        │
│  App 4 (pool=20) ──┤    ← 80接続で余裕なし                  │
│  App 5 (pool=20) ──┘    ← 100超えで接続エラー               │
│                                                              │
│  ─────────────────────────────────────────────────────────  │
│                                                              │
│  【解決: PgBouncer で接続を集約】                            │
│                                                              │
│  App 1 (pool=20) ──┐                                        │
│  App 2 (pool=20) ──┤                                        │
│  App 3 (pool=20) ──┼──► PgBouncer ──► PostgreSQL           │
│  App 4 (pool=20) ──┤    (100接続)     (20接続)              │
│  App 5 (pool=20) ──┘                                        │
│                                                              │
│  アプリ側: 100接続                                          │
│  DB側: 20接続 ← 大幅削減                                    │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 プーリングモード

```
┌─────────────────────────────────────────────────────────────┐
│              PgBouncer のプーリングモード                    │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  【session モード】                                          │
│  - クライアント接続中は同じDB接続を維持                     │
│  - 効果は限定的（接続を共有しない）                         │
│  - PREPARE文、一時テーブルが使える                          │
│                                                              │
│  【transaction モード】★推奨                                │
│  - トランザクション単位でDB接続を共有                       │
│  - 効果が高い（1つのDB接続を複数クライアントで再利用）      │
│  - PREPARE文、SET、一時テーブルは使えない                   │
│                                                              │
│  【statement モード】                                        │
│  - SQL文単位でDB接続を共有                                  │
│  - 最も効率的だがトランザクション不可                       │
│  - 特殊なユースケースのみ                                   │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 3.3 設定例

```ini
# /etc/pgbouncer/pgbouncer.ini

[databases]
mydb = host=127.0.0.1 port=5432 dbname=mydb

[pgbouncer]
listen_addr = 0.0.0.0
listen_port = 6432
auth_type = md5
auth_file = /etc/pgbouncer/userlist.txt

# プーリングモード
pool_mode = transaction

# 接続数設定
max_client_conn = 1000    # クライアントからの最大接続
default_pool_size = 20    # DB毎のデフォルトプールサイズ
min_pool_size = 5         # 最小プールサイズ
reserve_pool_size = 5     # 予備接続数
```

---

## 4. HTTPクライアントのコネクションプール

### 4.1 なぜHTTPでもプールが必要か

```
┌─────────────────────────────────────────────────────────────┐
│              HTTPクライアント接続の問題                      │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  【マイクロサービス間通信】                                  │
│                                                              │
│  Service A ──HTTP──► Service B                              │
│                                                              │
│  各リクエストで接続を作成すると:                            │
│  - TCP 3ウェイハンドシェイク: 0.5ms〜1ms                    │
│  - TLSハンドシェイク（HTTPS）: 1ms〜2ms                     │
│  - 切断後の TIME_WAIT 蓄積                                  │
│                                                              │
│  1000 req/sec の場合:                                        │
│  - 1秒で1000回のTCP接続確立                                 │
│  - TIME_WAIT が60秒残ると: 60,000ソケット滞留               │
│  - ポート枯渇のリスク                                       │
│                                                              │
│  ─────────────────────────────────────────────────────────  │
│                                                              │
│  【解決: HTTP Keep-Alive + コネクションプール】              │
│                                                              │
│  1つのTCP接続で複数のHTTPリクエスト/レスポンスをやり取り    │
│                                                              │
│  Connection: Keep-Alive                                      │
│                                                              │
│  Request 1 ──► Response 1                                   │
│  Request 2 ──► Response 2   同じTCP接続を再利用             │
│  Request 3 ──► Response 3                                   │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 4.2 RestTemplate（レガシー）

```java
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        // コネクションプール設定
        PoolingHttpClientConnectionManager connectionManager =
            new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(100);           // 全体の最大接続数
        connectionManager.setDefaultMaxPerRoute(20);  // ホストごとの最大接続数

        CloseableHttpClient httpClient = HttpClients.custom()
            .setConnectionManager(connectionManager)
            .setKeepAliveStrategy((response, context) -> 30_000)  // Keep-Alive 30秒
            .evictIdleConnections(60, TimeUnit.SECONDS)  // アイドル60秒で削除
            .build();

        HttpComponentsClientHttpRequestFactory factory =
            new HttpComponentsClientHttpRequestFactory(httpClient);
        factory.setConnectTimeout(5000);      // 接続タイムアウト
        factory.setReadTimeout(30000);        // 読み取りタイムアウト

        return new RestTemplate(factory);
    }
}
```

### 4.3 WebClient（推奨）

```java
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient() {
        // コネクションプール設定
        ConnectionProvider provider = ConnectionProvider.builder("myPool")
            .maxConnections(100)                    // 最大接続数
            .maxIdleTime(Duration.ofSeconds(60))    // アイドルタイムアウト
            .maxLifeTime(Duration.ofMinutes(5))     // 接続の最大生存時間
            .pendingAcquireTimeout(Duration.ofSeconds(30))  // 取得待ちタイムアウト
            .evictInBackground(Duration.ofSeconds(30))      // バックグラウンド削除
            .build();

        HttpClient httpClient = HttpClient.create(provider)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
            .responseTimeout(Duration.ofSeconds(30));

        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }
}
```

### 4.4 OkHttp

```java
@Configuration
public class OkHttpConfig {

    @Bean
    public OkHttpClient okHttpClient() {
        ConnectionPool connectionPool = new ConnectionPool(
            20,                         // 最大アイドル接続数
            5, TimeUnit.MINUTES         // Keep-Alive時間
        );

        return new OkHttpClient.Builder()
            .connectionPool(connectionPool)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    }
}
```

---

## 5. Redis コネクションプール

### 5.1 Lettuce（Spring Boot デフォルト）

```yaml
spring:
  redis:
    host: localhost
    port: 6379

    lettuce:
      pool:
        max-active: 8      # 最大接続数
        max-idle: 8        # 最大アイドル接続数
        min-idle: 0        # 最小アイドル接続数
        max-wait: -1ms     # 接続取得タイムアウト（-1は無限）
```

```java
@Configuration
public class RedisConfig {

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName("localhost");
        config.setPort(6379);

        // プール設定
        GenericObjectPoolConfig<Object> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(20);
        poolConfig.setMaxIdle(10);
        poolConfig.setMinIdle(5);
        poolConfig.setMaxWait(Duration.ofSeconds(10));

        LettucePoolingClientConfiguration clientConfig =
            LettucePoolingClientConfiguration.builder()
                .poolConfig(poolConfig)
                .commandTimeout(Duration.ofSeconds(5))
                .build();

        return new LettuceConnectionFactory(config, clientConfig);
    }
}
```

### 5.2 Jedis

```java
@Configuration
public class JedisConfig {

    @Bean
    public JedisPool jedisPool() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(20);          // 最大接続数
        poolConfig.setMaxIdle(10);           // 最大アイドル接続数
        poolConfig.setMinIdle(5);            // 最小アイドル接続数
        poolConfig.setMaxWait(Duration.ofSeconds(10));  // 取得タイムアウト

        // 接続検証（借用時にPINGを送信）
        poolConfig.setTestOnBorrow(true);

        return new JedisPool(poolConfig, "localhost", 6379);
    }
}
```

---

## 6. トラブルシューティング

### 6.1 よくある問題

```
┌─────────────────────────────────────────────────────────────┐
│              コネクションプールのトラブル                    │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  【問題1: Connection pool exhausted】                        │
│                                                              │
│  原因:                                                       │
│  - プールサイズが小さすぎる                                 │
│  - 接続が返却されていない（リーク）                         │
│  - クエリが遅すぎる                                         │
│                                                              │
│  確認方法（HikariCP）:                                       │
│  - Active connections が maximum-pool-size に張り付き       │
│  - Pending threads が増加                                   │
│                                                              │
│  対策:                                                       │
│  - leak-detection-threshold を設定                          │
│  - スロークエリを特定して修正                               │
│  - プールサイズを増やす（根本解決ではない）                 │
│                                                              │
│  ─────────────────────────────────────────────────────────  │
│                                                              │
│  【問題2: Connection leak detected】                         │
│                                                              │
│  原因:                                                       │
│  - try-with-resources を使っていない                        │
│  - 例外発生時に接続がクローズされていない                   │
│                                                              │
│  悪い例:                                                     │
│  Connection conn = dataSource.getConnection();               │
│  // 例外が発生すると conn がリークする                      │
│  Statement stmt = conn.createStatement();                    │
│  ResultSet rs = stmt.executeQuery("...");                   │
│  conn.close();  // ← ここに到達しない可能性                 │
│                                                              │
│  良い例:                                                     │
│  try (Connection conn = dataSource.getConnection();          │
│       Statement stmt = conn.createStatement();               │
│       ResultSet rs = stmt.executeQuery("...")) {            │
│      // 処理                                                 │
│  }  // ← 自動的にクローズ                                   │
│                                                              │
│  ─────────────────────────────────────────────────────────  │
│                                                              │
│  【問題3: Connection is closed / Connection reset】          │
│                                                              │
│  原因:                                                       │
│  - DBがアイドル接続を切断した                               │
│  - max-lifetime が DB の wait_timeout より長い              │
│  - ネットワーク不安定                                       │
│                                                              │
│  対策:                                                       │
│  - max-lifetime < DB wait_timeout に設定                    │
│  - connection-test-query を設定（非推奨、JDBC4では不要）    │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 6.2 HikariCP のモニタリング設定

```yaml
# application.yml
spring:
  datasource:
    hikari:
      # リーク検出（開発/テスト環境で有効に）
      leak-detection-threshold: 60000  # 60秒以上借用でログ出力

      # メトリクス
      register-mbeans: true  # JMX MBean登録

logging:
  level:
    com.zaxxer.hikari: DEBUG  # HikariCPのログを詳細に
```

### 6.3 メトリクス確認（Spring Boot Actuator）

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics
  endpoint:
    health:
      show-details: always
```

```bash
# アクティブ接続数
curl http://localhost:8080/actuator/metrics/hikaricp.connections.active

# アイドル接続数
curl http://localhost:8080/actuator/metrics/hikaricp.connections.idle

# 待機スレッド数
curl http://localhost:8080/actuator/metrics/hikaricp.connections.pending

# 接続取得時間
curl http://localhost:8080/actuator/metrics/hikaricp.connections.acquire
```

---

## 7. ベストプラクティス

### 7.1 設定のチェックリスト

```
┌─────────────────────────────────────────────────────────────┐
│              コネクションプール設定チェックリスト            │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  □ maximum-pool-size は適切か                               │
│    - アプリ全台の合計 < DB max_connections                  │
│    - HikariCP推奨: (core_count * 2) + spindle_count         │
│                                                              │
│  □ minimum-idle は maximum-pool-size と同じか               │
│    - HikariCP作者の推奨                                     │
│    - 動的サイズ変更のオーバーヘッド回避                     │
│                                                              │
│  □ max-lifetime は DB wait_timeout より短いか               │
│    - MySQL: 28800秒 (8時間)                                 │
│    - PostgreSQL: 無制限（設定が必要）                       │
│    - 推奨: wait_timeout - 30秒                              │
│                                                              │
│  □ connection-timeout は適切か                              │
│    - デフォルト 30秒は多くの場合で妥当                      │
│    - 短すぎると高負荷時にエラー多発                         │
│                                                              │
│  □ leak-detection-threshold を設定しているか               │
│    - 開発/テスト環境では必須                                │
│    - 本番でも設定推奨（60秒〜）                             │
│                                                              │
│  □ メトリクスを収集しているか                               │
│    - Actuator + Prometheus/Grafana                          │
│    - アクティブ接続数、待機数を監視                         │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 7.2 本番環境での設定例

```yaml
# production application.yml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:5432/${DB_NAME}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

    hikari:
      pool-name: ProdPool

      # サイズ（4コアサーバー想定）
      maximum-pool-size: 10
      minimum-idle: 10

      # タイムアウト
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000      # 30分（PostgreSQL wait_timeout未設定の場合）

      # リーク検出
      leak-detection-threshold: 60000

      # 検証
      validation-timeout: 5000

      # JMX
      register-mbeans: true
```

---

## まとめ

### 学んだこと

- TCP接続のオーバーヘッドとプーリングによる解決
- HikariCPの設定パラメータと適切な値の決め方
- PgBouncerによる接続集約
- HTTPクライアント、Redisのプール設定
- トラブルシューティングの方法

### 重要なポイント

```
1. プールサイズは大きければ良いわけではない
   - DBの max_connections を超えてはいけない
   - 適切なサイズは (core_count * 2) + spindle_count

2. 接続リークを防ぐ
   - try-with-resources を使う
   - leak-detection-threshold を設定

3. max-lifetime に注意
   - DBのタイムアウトより短く設定
   - そうしないと "Connection is closed" エラー

4. メトリクスで監視
   - アクティブ接続数、待機数を監視
   - 問題を早期発見
```

### 次のステップ

- [09-tcp-fundamentals.md](./09-tcp-fundamentals.md) - TCP基礎（TIME_WAIT等）
- [10-web-server-architecture.md](./10-web-server-architecture.md) - Webサーバーアーキテクチャ

### 参考リンク

- [HikariCP GitHub](https://github.com/brettwooldridge/HikariCP)
- [About Pool Sizing (HikariCP Wiki)](https://github.com/brettwooldridge/HikariCP/wiki/About-Pool-Sizing)
- [PgBouncer Documentation](https://www.pgbouncer.org/config.html)
- [Spring Boot - Connection Pooling](https://docs.spring.io/spring-boot/docs/current/reference/html/data.html#data.sql.datasource.connection-pool)
