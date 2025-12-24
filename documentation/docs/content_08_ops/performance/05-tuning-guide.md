# チューニングガイド

本ドキュメントでは、idp-server の性能最適化のための設定指針を提供する。

---

## JVM チューニング

### ヒープサイズ

```bash
JAVA_TOOL_OPTIONS="-Xms512m -Xmx2g"
```

| 負荷レベル | 推奨Xms | 推奨Xmx |
|----------|--------|--------|
| 小規模 | 256m | 1g |
| 中規模 | 512m | 2g |
| 大規模 | 1g | 4g |

:::tip
XmsとXmxを同じ値に設定すると、ヒープリサイズのオーバーヘッドを回避できる。
:::

### ガベージコレクション

#### G1GC（推奨）

```bash
JAVA_TOOL_OPTIONS="-XX:+UseG1GC -XX:MaxGCPauseMillis=100"
```

| パラメータ | 値 | 説明 |
|----------|---|------|
| UseG1GC | 有効 | G1GCを使用 |
| MaxGCPauseMillis | 100 | 目標GC停止時間(ms) |
| G1HeapRegionSize | 自動 | リージョンサイズ |

#### ZGC（低レイテンシ重視）

```bash
JAVA_TOOL_OPTIONS="-XX:+UseZGC -XX:+ZGenerational"
```

- JDK 21以降で推奨
- 超低レイテンシが要求される場合に使用

### GCログ

```bash
JAVA_TOOL_OPTIONS="-Xlog:gc*:file=/var/log/gc.log:time,uptime:filecount=5,filesize=100m"
```

---

## Tomcat チューニング

### スレッドプール

```yaml
server:
  tomcat:
    threads:
      max: 300
      min-spare: 50
    accept-count: 100
    connection-timeout: 20000
```

| パラメータ | 推奨値 | 説明 |
|----------|-------|------|
| max | 300 | 最大ワーカースレッド |
| min-spare | 50 | 最小待機スレッド |
| accept-count | 100 | 接続キューサイズ |
| connection-timeout | 20000 | タイムアウト(ms) |

### 負荷レベル別設定

| 負荷レベル | max | min-spare | accept-count |
|----------|-----|-----------|--------------|
| 小規模 | 100 | 20 | 50 |
| 中規模 | 300 | 50 | 100 |
| 大規模 | 500 | 100 | 200 |

---

## データベースチューニング

### PostgreSQL

#### 接続プール

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

| パラメータ | 推奨値 | 説明 |
|----------|-------|------|
| maximum-pool-size | 10 | 最大コネクション数（CPUコア × 2程度） |
| minimum-idle | 5 | 最小アイドル数 |
| connection-timeout | 30000 | 接続タイムアウト(ms) |

:::tip 接続プールサイズの指針
過剰な接続プールはDB側の競合を引き起こす。実測では30→10に削減することでp95が20%改善。
推奨値: CPUコア数 × 2 程度
:::

#### PostgreSQL設定

```conf
# postgresql.conf

# 接続
max_connections = 200

# メモリ
shared_buffers = 256MB
effective_cache_size = 768MB
work_mem = 4MB
maintenance_work_mem = 64MB

# WAL
wal_buffers = 16MB
checkpoint_completion_target = 0.9

# クエリ最適化
random_page_cost = 1.1
effective_io_concurrency = 200
```

### インデックス最適化

#### 必須インデックス

```sql
-- ユーザー認証デバイス検索
CREATE INDEX idx_user_auth_device_tenant_user
ON idp_user_authentication_devices (tenant_id, user_id);

CREATE INDEX idx_user_auth_device_user_id
ON idp_user_authentication_devices (user_id);

-- テナント別検索
CREATE INDEX idx_idp_user_tenant_id
ON idp_user (tenant_id);

-- メール検索
CREATE INDEX idx_idp_user_email
ON idp_user (tenant_id, email);
```

#### インデックス使用状況確認

```sql
SELECT
    schemaname,
    tablename,
    indexname,
    idx_scan,
    idx_tup_read,
    idx_tup_fetch
FROM pg_stat_user_indexes
WHERE schemaname = 'public'
ORDER BY idx_scan DESC;
```

---

## Redis チューニング

### 基本設定

```yaml
spring:
  redis:
    host: redis
    port: 6379
    timeout: 2000
    lettuce:
      pool:
        max-active: 50
        max-idle: 20
        min-idle: 5
```

### キャッシュTTL

```yaml
cache:
  enable: true
  ttl: 300  # 秒
```

| データ種別 | 推奨TTL | 理由 |
|----------|--------|-----|
| JWKS | 3600 | 変更頻度低 |
| クライアント設定 | 300 | 中程度の変更頻度 |
| ユーザーセッション | 1800 | セキュリティ考慮 |

### Redis設定

```conf
# redis.conf

# メモリ
maxmemory 1gb
maxmemory-policy allkeys-lru

# 永続化（必要に応じて）
appendonly no
save ""

# ネットワーク
tcp-keepalive 300
timeout 0
```

---

## ロードバランサチューニング

### Nginx設定

```nginx
upstream idp_backend {
    server idp-server-1:8080;
    server idp-server-2:8080;
    keepalive 32;
}

server {
    listen 80;

    location / {
        proxy_pass http://idp_backend;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        proxy_connect_timeout 30s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }
}
```

:::tip keepalive設定の効果
実測ではkeepalive追加によりTPS +12%、p95 -20%の改善を確認。
`proxy_http_version 1.1` と `proxy_set_header Connection ""` が必須。
:::

### 負荷分散アルゴリズム

| アルゴリズム | ユースケース |
|------------|------------|
| round_robin | デフォルト、均等分散（実測済み） |
| least_conn | 接続数ベース |
| ip_hash | セッション固定が必要な場合 |

---

## アプリケーション設定

### ログレベル

```yaml
logging:
  level:
    root: warn
    org.idp.server: info
    org.springframework.web: warn
```

:::warning
本番環境ではDEBUGログを無効化すること。I/Oオーバーヘッドが大きい。
:::

### 非同期処理

```yaml
spring:
  task:
    execution:
      pool:
        core-size: 8
        max-size: 32
        queue-capacity: 100
```

---

## パフォーマンスチェックリスト

### デプロイ前

- [ ] JVMヒープサイズ設定確認
- [ ] GC設定確認
- [ ] DBコネクションプールサイズ確認
- [ ] Redisキャッシュ有効化確認
- [ ] 必須インデックス作成確認
- [ ] ログレベルがINFO以上であること

### 運用中

- [ ] GCログの定期確認
- [ ] DBクエリ統計の確認
- [ ] Redisメモリ使用量確認
- [ ] アプリケーションヒープ使用量確認

---

## トラブルシューティング

### 高レイテンシ

1. **GCポーズ確認**
   ```bash
   grep "pause" /var/log/gc.log
   ```

2. **DBクエリ確認**
   ```sql
   SELECT query, mean_exec_time
   FROM pg_stat_statements
   ORDER BY mean_exec_time DESC
   LIMIT 10;
   ```

3. **スレッド状態確認**
   ```bash
   jstack <pid> | grep -A 2 "java.lang.Thread.State"
   ```

### メモリ不足

1. **ヒープダンプ取得**
   ```bash
   jmap -dump:format=b,file=heap.hprof <pid>
   ```

2. **ヒープ使用量確認**
   ```bash
   jstat -gc <pid> 1000
   ```

### 接続エラー

1. **DBコネクション確認**
   ```sql
   SELECT count(*) FROM pg_stat_activity;
   ```

2. **Redis接続確認**
   ```bash
   redis-cli ping
   ```

---

## 推奨設定サンプル

### 検証済み構成（2インスタンス、2,400+ TPS）

以下は実測で2,400+ TPS、p95 < 150msを達成した構成。

```yaml
# application.yaml
server:
  tomcat:
    threads:
      max: 300
      min-spare: 50
    accept-count: 100

spring:
  datasource:
    hikari:
      maximum-pool-size: 10  # CPUコア × 2
      minimum-idle: 5

  redis:
    lettuce:
      pool:
        max-active: 50

cache:
  enable: true
  ttl: 300

logging:
  level:
    root: warn
    org.idp.server: info
```

```bash
# JVM設定
JAVA_TOOL_OPTIONS="-Xms512m -Xmx2g -XX:MaxGCPauseMillis=100"
```

```nginx
# nginx設定（keepalive必須）
upstream idp_backend {
    server idp-server-1:8080;
    server idp-server-2:8080;
    keepalive 32;
}
```

:::note 実測結果
- 2インスタンス構成: 2,464 TPS、p95: 145ms（Authorization）
- 190万ユーザー環境で検証済み
:::

---

## 関連ドキュメント

- [テスト環境](./01-test-environment.md)
- [ストレステスト結果](./02-stress-test-results.md)
- [スケーラビリティ評価](./04-scalability-evaluation.md)
