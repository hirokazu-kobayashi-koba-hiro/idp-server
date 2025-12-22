# PostgreSQL コネクションプーリング

## 所要時間
約40分

## 学べること
- コネクションプーリングの必要性と仕組み
- PgBouncer の設定と運用
- アプリケーション側のコネクション管理
- 接続数の適切な設計
- トラブルシューティング

## 前提知識
- PostgreSQL の基本操作
- ネットワークの基礎知識

---

## 1. コネクションプーリングとは

### 1.1 PostgreSQL接続の課題

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    PostgreSQL接続の課題                                          │
│                                                                                 │
│  クライアント1 ──┬── 接続確立（認証、プロセス起動）                              │
│  クライアント2 ──┼── 各接続で約10MB程度のメモリ消費                             │
│  クライアント3 ──┼── max_connections の上限（デフォルト100）                    │
│      ⋮          │                                                              │
│  クライアントN ──┘                                                              │
│                                                                                 │
│  問題:                                                                          │
│  - 接続確立に50-100ms程度かかる                                                 │
│  - 多数の接続はメモリを大量消費                                                 │
│  - 接続数の上限に達すると新規接続が拒否される                                   │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 1.2 コネクションプーリングの解決策

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                  コネクションプーリングの仕組み                                   │
│                                                                                 │
│  ┌─────────────────────────┐      ┌─────────────────┐      ┌───────────────┐   │
│  │     アプリケーション     │      │   Connection    │      │  PostgreSQL   │   │
│  │                         │      │     Pool        │      │               │   │
│  │  リクエスト1 ───────────┼─────→│ ┌───┐ ┌───┐    │      │               │   │
│  │  リクエスト2 ───────────┼─────→│ │接続│ │接続│────┼─────→│  バックエンド  │   │
│  │  リクエスト3 ───────────┼─────→│ └───┘ └───┘    │      │  プロセス     │   │
│  │        ⋮               │      │  プール (5-20)  │      │  (少数)       │   │
│  │  リクエストN ──────────┼─────→│                 │      │               │   │
│  └─────────────────────────┘      └─────────────────┘      └───────────────┘   │
│                                                                                 │
│  利点:                                                                          │
│  - 接続の再利用で高速化                                                         │
│  - PostgreSQL側の接続数を最小限に                                               │
│  - 多数のクライアントを少数の接続で処理                                         │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 1.3 プーリングの種類

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                      プーリングの実装場所                                        │
├───────────────────────┬─────────────────────────────────────────────────────────┤
│ 種類                   │ 説明                                                    │
├───────────────────────┼─────────────────────────────────────────────────────────┤
│ アプリケーション側      │ HikariCP, c3p0 など                                    │
│                       │ 各アプリインスタンスがプールを持つ                       │
│                       │ スケールアウト時に接続数が増加                          │
├───────────────────────┼─────────────────────────────────────────────────────────┤
│ 外部プーラー           │ PgBouncer, Pgpool-II など                              │
│                       │ PostgreSQLとアプリの間に配置                            │
│                       │ 複数アプリから共有可能                                  │
├───────────────────────┼─────────────────────────────────────────────────────────┤
│ マネージドサービス     │ RDS Proxy, Cloud SQL Connector など                    │
│                       │ クラウド環境で統合管理                                  │
└───────────────────────┴─────────────────────────────────────────────────────────┘
```

---

## 2. PgBouncer

### 2.1 PgBouncerとは

PgBouncerは、PostgreSQL向けの軽量なコネクションプーラーです。

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                         PgBouncer の特徴                                        │
├─────────────────────────────────────────────────────────────────────────────────┤
│ ✓ 軽量（1接続あたり約2KB）                                                      │
│ ✓ シングルスレッド・イベント駆動                                                │
│ ✓ 3つのプーリングモード                                                        │
│ ✓ 認証の委譲・中継                                                             │
│ ✓ SSL/TLS サポート                                                             │
│ ✓ オンラインリロード・再起動                                                    │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 プーリングモード

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                       PgBouncer プーリングモード                                 │
├──────────────┬──────────────────────────────────────────────────────────────────┤
│ モード        │ 説明                                                             │
├──────────────┼──────────────────────────────────────────────────────────────────┤
│ session      │ セッション全体で同じサーバー接続を使用                            │
│              │ クライアントが切断するまで接続を保持                              │
│              │ プーリング効果は限定的だが、すべての機能が使える                  │
│              │                                                                  │
│              │ 用途: セッション変数、PREPARE文を多用する場合                     │
├──────────────┼──────────────────────────────────────────────────────────────────┤
│ transaction  │ トランザクション単位で接続を割り当て（推奨）                      │
│              │ トランザクション終了後に接続をプールに返却                        │
│              │ 最も効率的なプーリング                                           │
│              │                                                                  │
│              │ 制限: SET、PREPARE、一時テーブルはトランザクション内のみ         │
├──────────────┼──────────────────────────────────────────────────────────────────┤
│ statement    │ ステートメント単位で接続を割り当て                                │
│              │ 最も積極的なプーリング                                           │
│              │                                                                  │
│              │ 制限: 複数文のトランザクション不可、autocommitのみ               │
└──────────────┴──────────────────────────────────────────────────────────────────┘
```

### 2.3 基本設定

```ini
; /etc/pgbouncer/pgbouncer.ini

[databases]
; データベースごとの設定
mydb = host=localhost port=5432 dbname=mydb

; 接続文字列のカスタマイズ
mydb_readonly = host=replica.example.com port=5432 dbname=mydb

[pgbouncer]
; 基本設定
listen_addr = 0.0.0.0
listen_port = 6432
auth_type = md5
auth_file = /etc/pgbouncer/userlist.txt

; プーリングモード
pool_mode = transaction

; 接続数制限
max_client_conn = 1000          ; クライアントからの最大接続数
default_pool_size = 20          ; データベースごとのデフォルトプールサイズ
min_pool_size = 5               ; 最小プールサイズ
reserve_pool_size = 5           ; 予備の接続数

; タイムアウト設定
server_idle_timeout = 600       ; アイドル接続のタイムアウト（秒）
client_idle_timeout = 0         ; クライアントアイドルタイムアウト（0=無効）
query_timeout = 0               ; クエリタイムアウト（0=無効）

; ログ設定
log_connections = 1
log_disconnections = 1
log_pooler_errors = 1
```

### 2.4 認証設定

```
; /etc/pgbouncer/userlist.txt
; 形式: "username" "password"

"myuser" "md5abc123..."   ; MD5ハッシュ
"admin" "SCRAM-SHA-256$iterations:..."  ; SCRAM認証
```

```sql
-- PostgreSQLからパスワードハッシュを取得
SELECT usename, passwd FROM pg_shadow WHERE usename = 'myuser';
```

### 2.5 管理コマンド

```sql
-- PgBouncerに接続（管理用データベース）
psql -h localhost -p 6432 -U admin pgbouncer

-- プール状態の確認
SHOW POOLS;
/*
 database |   user   | cl_active | cl_waiting | sv_active | sv_idle | sv_used
----------+----------+-----------+------------+-----------+---------+--------
 mydb     | myuser   |        10 |          0 |         5 |      15 |       0
*/

-- クライアント接続の確認
SHOW CLIENTS;

-- サーバー接続の確認
SHOW SERVERS;

-- 統計情報
SHOW STATS;

-- 設定の再読み込み
RELOAD;

-- プールの一時停止・再開
PAUSE mydb;
RESUME mydb;
```

---

## 3. アプリケーション側のプーリング

### 3.1 HikariCP（Java）

```yaml
# application.yml (Spring Boot)
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/mydb
    username: myuser
    password: secret
    hikari:
      minimum-idle: 5           # 最小アイドル接続数
      maximum-pool-size: 20     # 最大接続数
      idle-timeout: 300000      # アイドルタイムアウト（ミリ秒）
      max-lifetime: 1800000     # 接続の最大生存期間（ミリ秒）
      connection-timeout: 30000 # 接続取得タイムアウト
      pool-name: MyPool

      # 検証クエリ（PostgreSQLの場合は不要）
      # connection-test-query: SELECT 1
```

```java
// プログラムでの設定
HikariConfig config = new HikariConfig();
config.setJdbcUrl("jdbc:postgresql://localhost:5432/mydb");
config.setUsername("myuser");
config.setPassword("secret");

config.setMinimumIdle(5);
config.setMaximumPoolSize(20);
config.setIdleTimeout(300000);
config.setMaxLifetime(1800000);
config.setConnectionTimeout(30000);

// PostgreSQL固有の最適化
config.addDataSourceProperty("cachePrepStmts", "true");
config.addDataSourceProperty("prepStmtCacheSize", "250");
config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

HikariDataSource ds = new HikariDataSource(config);
```

### 3.2 適切なプールサイズ

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                      プールサイズの計算                                          │
│                                                                                 │
│  経験則:                                                                        │
│  最適なプールサイズ ≈ (CPUコア数 × 2) + ディスクスピンドル数                    │
│                                                                                 │
│  例：4コアCPU、SSD（スピンドル=1）の場合                                        │
│  プールサイズ = (4 × 2) + 1 = 9                                                 │
│                                                                                 │
│  ┌───────────────────────────────────────────────────────────┐                 │
│  │ 注意: プールサイズは大きければ良いわけではない              │                 │
│  │                                                           │                 │
│  │ 過大なプールサイズの問題:                                  │                 │
│  │ - コンテキストスイッチのオーバーヘッド                     │                 │
│  │ - ロック競合の増加                                         │                 │
│  │ - メモリ消費の増加                                         │                 │
│  └───────────────────────────────────────────────────────────┘                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 3.3 マイクロサービス環境での考慮点

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│               マイクロサービス環境でのコネクション管理                            │
│                                                                                 │
│  問題: 各サービスがプールを持つと、合計接続数が爆発                              │
│                                                                                 │
│  サービスA (5インスタンス) × 20接続 = 100接続                                   │
│  サービスB (3インスタンス) × 20接続 =  60接続                                   │
│  サービスC (4インスタンス) × 20接続 =  80接続                                   │
│  ────────────────────────────────────────────                                  │
│  合計: 240接続 ← PostgreSQLの限界を超える可能性                                 │
│                                                                                 │
│  解決策:                                                                        │
│  1. PgBouncerを中間に配置し、サーバー接続数を制限                               │
│  2. 各サービスのプールサイズを控えめに設定                                      │
│  3. RDS Proxy などのマネージドプーラーを使用                                    │
└─────────────────────────────────────────────────────────────────────────────────┘
```

```
                アプリ層                  プーラー層              DB層
┌─────────────────────────────┐    ┌──────────────┐    ┌───────────────┐
│  サービスA                   │    │              │    │               │
│  ┌─────┐┌─────┐┌─────┐     │    │              │    │               │
│  │Pool ││Pool ││Pool │ ────┼───→│              │    │               │
│  │(10) ││(10) ││(10) │     │    │              │    │               │
│  └─────┘└─────┘└─────┘     │    │  PgBouncer   │    │  PostgreSQL   │
├─────────────────────────────┤    │              │    │               │
│  サービスB                   │    │  max_pool=50 │───→│ max_conn=100  │
│  ┌─────┐┌─────┐            │    │              │    │               │
│  │Pool ││Pool │ ───────────┼───→│              │    │               │
│  │(10) ││(10) │            │    │              │    │               │
│  └─────┘└─────┘            │    │              │    │               │
└─────────────────────────────┘    └──────────────┘    └───────────────┘
```

---

## 4. 接続数の設計

### 4.1 PostgreSQL側の設定

```sql
-- 現在の設定確認
SHOW max_connections;         -- 最大接続数（デフォルト100）
SHOW superuser_reserved_connections;  -- スーパーユーザー用予約（デフォルト3）

-- 使用可能な接続数
SELECT max_connections::int - superuser_reserved_connections::int AS available_connections
FROM pg_settings
WHERE name = 'max_connections'
CROSS JOIN pg_settings
WHERE name = 'superuser_reserved_connections';

-- 現在の接続状況
SELECT
    count(*) AS total_connections,
    count(*) FILTER (WHERE state = 'active') AS active,
    count(*) FILTER (WHERE state = 'idle') AS idle,
    count(*) FILTER (WHERE state = 'idle in transaction') AS idle_in_transaction
FROM pg_stat_activity
WHERE backend_type = 'client backend';
```

### 4.2 接続数の見積もり

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                      接続数の見積もり例                                          │
│                                                                                 │
│  前提条件:                                                                      │
│  - アプリサーバー: 10台                                                         │
│  - 各アプリのプールサイズ: 20                                                   │
│  - レプリケーション用: 5                                                        │
│  - 監視ツール: 5                                                                │
│  - 管理者接続: 5                                                                │
│                                                                                 │
│  必要な接続数:                                                                  │
│  アプリ: 10 × 20 = 200                                                         │
│  レプリケーション: 5                                                            │
│  監視: 5                                                                        │
│  管理: 5                                                                        │
│  バッファ（20%）: 43                                                            │
│  ───────────────────                                                           │
│  合計: 258                                                                      │
│                                                                                 │
│  → max_connections = 260〜300 が適切                                           │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 4.3 shared_buffers との関係

```sql
-- 接続数を増やす場合はshared_buffersも調整が必要
-- postgresql.conf

max_connections = 300
shared_buffers = 4GB          -- 接続数が多い場合は増やす

-- 各接続のワークメモリ
work_mem = 64MB               -- 接続数 × work_mem の合計に注意

-- 必要メモリの概算
-- shared_buffers + (max_connections × work_mem × 同時実行ソート数)
-- 4GB + (300 × 64MB × 2) = 4GB + 38.4GB = 約42GB
```

---

## 5. AWS RDS Proxy

### 5.1 RDS Proxyとは

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                         RDS Proxy の特徴                                        │
├─────────────────────────────────────────────────────────────────────────────────┤
│ ✓ AWSマネージドのコネクションプーラー                                            │
│ ✓ IAM認証との統合                                                               │
│ ✓ Secrets Manager との統合                                                      │
│ ✓ 自動フェイルオーバー対応                                                      │
│ ✓ マルチAZ配置                                                                  │
│ ✓ Lambda関数との相性が良い                                                      │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 5.2 アーキテクチャ

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                       RDS Proxy アーキテクチャ                                   │
│                                                                                 │
│  ┌──────────────┐                                                              │
│  │   Lambda     │                                                              │
│  │   関数群     ├──────┐                                                       │
│  └──────────────┘      │                                                       │
│                        │      ┌──────────────┐      ┌───────────────┐         │
│  ┌──────────────┐      │      │              │      │               │         │
│  │     EC2      │      ├─────→│  RDS Proxy   │─────→│   RDS         │         │
│  │  アプリ群    ├──────┤      │              │      │  (PostgreSQL) │         │
│  └──────────────┘      │      │  ・接続プール │      │               │         │
│                        │      │  ・IAM認証    │      │               │         │
│  ┌──────────────┐      │      │  ・フェイル   │      └───────────────┘         │
│  │     ECS      │      │      │    オーバー   │                                │
│  │  コンテナ群  ├──────┘      └──────────────┘                                │
│  └──────────────┘                                                              │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 5.3 IAM認証の設定

```java
// Javaでの IAM認証接続例
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.services.rds.RdsUtilities;

String authToken = RdsUtilities.builder()
    .credentialsProvider(DefaultCredentialsProvider.create())
    .region(Region.AP_NORTHEAST_1)
    .build()
    .generateAuthenticationToken(GenerateAuthenticationTokenRequest.builder()
        .hostname("proxy-endpoint.proxy-xxxx.ap-northeast-1.rds.amazonaws.com")
        .port(5432)
        .username("db_user")
        .build());

// 接続URL
String url = "jdbc:postgresql://proxy-endpoint.proxy-xxxx.ap-northeast-1.rds.amazonaws.com:5432/mydb" +
    "?ssl=true&sslmode=require";

// HikariCP設定でパスワードの代わりにトークンを使用
config.setPassword(authToken);
```

---

## 6. トラブルシューティング

### 6.1 接続枯渇

```sql
-- 症状: 新規接続ができない、"too many clients" エラー

-- 現在の接続状況を確認
SELECT
    usename,
    application_name,
    client_addr,
    state,
    count(*) as connection_count
FROM pg_stat_activity
WHERE backend_type = 'client backend'
GROUP BY usename, application_name, client_addr, state
ORDER BY connection_count DESC;

-- 長時間アイドル状態の接続を特定
SELECT
    pid,
    usename,
    application_name,
    client_addr,
    state,
    query_start,
    now() - query_start AS duration
FROM pg_stat_activity
WHERE state = 'idle'
  AND now() - query_start > interval '30 minutes'
ORDER BY duration DESC;

-- 必要に応じて強制切断
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE state = 'idle'
  AND now() - query_start > interval '1 hour';
```

### 6.2 idle in transaction

```sql
-- 症状: 接続が "idle in transaction" で放置されている

-- 問題のあるセッションを特定
SELECT
    pid,
    usename,
    application_name,
    state,
    now() - xact_start AS transaction_duration,
    query
FROM pg_stat_activity
WHERE state = 'idle in transaction'
  AND now() - xact_start > interval '5 minutes'
ORDER BY transaction_duration DESC;

-- 自動タイムアウトの設定（PostgreSQL 9.6以降）
-- postgresql.conf
-- idle_in_transaction_session_timeout = 300000  -- 5分でタイムアウト
```

### 6.3 PgBouncer の問題

```sql
-- PgBouncerに接続して状態確認
psql -h localhost -p 6432 pgbouncer

-- クライアント待機状況
SHOW POOLS;
-- cl_waiting > 0 の場合、接続待ちが発生している

-- 対処法:
-- 1. default_pool_size を増やす
-- 2. PostgreSQLの max_connections を増やす
-- 3. アプリケーション側の接続タイムアウトを設定

-- サーバー接続の状態
SHOW SERVERS;
-- state = 'active' が多すぎる場合、クエリが遅い可能性

-- 統計情報
SHOW STATS;
-- avg_query_time が増加していないか確認
```

### 6.4 接続リークの検出

```java
// Javaでの接続リーク検出（HikariCP）
HikariConfig config = new HikariConfig();
config.setLeakDetectionThreshold(60000);  // 60秒以上保持されたら警告

// ログに以下のような警告が出る
// Connection leak detection triggered for connection ...
// leaked connection was acquired from Thread ...
```

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                         接続リークの一般的な原因                                  │
├─────────────────────────────────────────────────────────────────────────────────┤
│ 1. 例外発生時にconnection.close()が呼ばれない                                   │
│    → try-with-resources を使用する                                             │
│                                                                                 │
│ 2. トランザクションのコミット/ロールバック忘れ                                  │
│    → @Transactional アノテーションを使用                                       │
│                                                                                 │
│ 3. ResultSetやStatementのクローズ忘れ                                          │
│    → try-with-resources を使用                                                 │
│                                                                                 │
│ 4. 長時間実行される処理中の接続保持                                             │
│    → 処理を分割し、必要な時だけ接続を取得                                      │
└─────────────────────────────────────────────────────────────────────────────────┘
```

```java
// 正しい接続の使い方（try-with-resources）
try (Connection conn = dataSource.getConnection();
     PreparedStatement stmt = conn.prepareStatement(sql);
     ResultSet rs = stmt.executeQuery()) {

    while (rs.next()) {
        // 処理
    }
}  // 自動的にclose()が呼ばれる
```

---

## 7. ベストプラクティス

### 7.1 設計原則

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    コネクション管理のベストプラクティス                            │
├─────────────────────────────────────────────────────────────────────────────────┤
│ ✓ 接続は可能な限り短時間で返却する                                              │
│   - トランザクションは短く保つ                                                  │
│   - ユーザー入力待ちで接続を保持しない                                          │
│                                                                                 │
│ ✓ プールサイズは控えめに設定する                                                │
│   - 必要最小限から始めて、監視しながら調整                                      │
│   - 大きすぎるプールはかえってパフォーマンスを下げる                            │
│                                                                                 │
│ ✓ 適切なタイムアウトを設定する                                                  │
│   - 接続取得タイムアウト                                                        │
│   - アイドルタイムアウト                                                        │
│   - クエリタイムアウト                                                          │
│                                                                                 │
│ ✓ 接続の健全性を確認する                                                        │
│   - HikariCPは自動でテストクエリを実行                                          │
│   - 長期間アイドルの接続は破棄して再作成                                        │
│                                                                                 │
│ ✓ マイクロサービス環境では外部プーラーを検討                                    │
│   - PgBouncer または RDS Proxy                                                  │
│   - 接続数の集中管理が可能                                                      │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 7.2 推奨設定

```yaml
# HikariCP 推奨設定（Spring Boot）
spring:
  datasource:
    hikari:
      # 基本設定
      maximum-pool-size: 10      # CPUコア数×2程度から開始
      minimum-idle: 5            # 最小アイドル接続

      # タイムアウト
      connection-timeout: 30000   # 接続取得待ち（30秒）
      idle-timeout: 600000        # アイドル接続の生存時間（10分）
      max-lifetime: 1800000       # 接続の最大生存時間（30分）

      # 検証
      validation-timeout: 5000    # 接続検証タイムアウト（5秒）

      # リーク検出（開発環境で有効化）
      leak-detection-threshold: 60000  # 60秒
```

```ini
; PgBouncer 推奨設定
[pgbouncer]
pool_mode = transaction           ; 最も効率的

; 接続数（環境に応じて調整）
default_pool_size = 20
min_pool_size = 5
reserve_pool_size = 5
max_client_conn = 1000

; タイムアウト
server_idle_timeout = 600         ; 10分
client_idle_timeout = 0           ; 無効（アプリ側で管理）
server_connect_timeout = 15
server_login_retry = 15

; パフォーマンス
tcp_keepalive = 1
tcp_keepidle = 60
```

### 7.3 監視項目

```sql
-- 定期的に監視すべき項目

-- 1. 接続使用率
SELECT
    count(*) * 100.0 / current_setting('max_connections')::int AS usage_percent
FROM pg_stat_activity
WHERE backend_type = 'client backend';

-- 2. 接続待ち時間（PgBouncer）
-- SHOW STATS; の avg_wait_time

-- 3. アイドル接続の割合
SELECT
    count(*) FILTER (WHERE state = 'idle') * 100.0 / count(*) AS idle_percent
FROM pg_stat_activity
WHERE backend_type = 'client backend';

-- 4. idle in transaction の件数
SELECT count(*)
FROM pg_stat_activity
WHERE state = 'idle in transaction';
```

---

## まとめ

1. **コネクションプーリング**はPostgreSQLの接続コストを削減し、スケーラビリティを向上させる
2. **PgBouncer**は軽量で効率的な外部プーラーとして広く使用されている
3. **transaction モード**が最も効率的だが、セッション機能の制限に注意
4. **プールサイズ**は「CPUコア数 × 2 + ディスク数」を目安に、小さく始める
5. **適切なタイムアウト**設定で接続リークや枯渇を防ぐ
6. **マイクロサービス環境**では外部プーラー（PgBouncer, RDS Proxy）を検討

## 次のステップ

- [dba-03-replication-ha.md](dba-03-replication-ha.md): レプリケーションと高可用性
- [dba-04-backup-recovery.md](dba-04-backup-recovery.md): バックアップとリカバリ
