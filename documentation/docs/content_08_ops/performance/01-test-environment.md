# テスト環境

本ドキュメントでは、性能検証に使用するテスト環境の構成と設定について説明する。

---

## ハードウェア構成

### 検証用マシン

| 項目 | スペック |
|-----|---------|
| モデル | MacBook Pro 14インチ (2023) |
| チップ | Apple M2 Max |
| メモリ | 64 GB |
| OS | macOS 15.0.1 (24A348) |

### 本番想定構成

中規模（〜1,000 req/s）を想定した推奨構成：

| コンポーネント | スペック | インスタンス数 |
|--------------|---------|--------------|
| idp-server | 2 vCPU, 4GB RAM | 2-4 |
| PostgreSQL | 4 vCPU, 8GB RAM | 1 (Primary) |
| Redis | 2 vCPU, 4GB RAM | 1 |
| ロードバランサ | - | 1 |

---

## ソフトウェア構成

### アプリケーション設定

#### JVM設定

```yaml
JAVA_TOOL_OPTIONS: >-
  -Xms512m
  -Xmx2g
  -XX:+UseG1GC
  -XX:MaxGCPauseMillis=100
```

| パラメータ | 値 | 説明 |
|----------|---|------|
| -Xms | 512m | 初期ヒープサイズ |
| -Xmx | 2g | 最大ヒープサイズ |
| -XX:+UseG1GC | 有効 | G1ガベージコレクタ使用 |
| -XX:MaxGCPauseMillis | 100 | 最大GC停止時間 |

:::note
G1GCをOFFにした場合、レスポンス時間の短縮が確認されている。本番環境では要検討。
:::

#### Tomcat設定

```yaml
server:
  tomcat:
    threads:
      max: 300
      min-spare: 50
    connection-timeout: 20000
    accept-count: 100
```

| パラメータ | 値 | 説明 |
|----------|---|------|
| maxThreads | 300 | 最大ワーカースレッド数 |
| minSpareThreads | 50 | 最小待機スレッド数 |
| connectionTimeout | 20000 | コネクションタイムアウト(ms) |
| acceptCount | 100 | 接続キューサイズ |

### データベース設定

#### PostgreSQL

```yaml
services:
  postgresql:
    image: postgres:16
    command:
      - "postgres"
      - "-c"
      - "shared_preload_libraries=pg_stat_statements"
      - "-c"
      - "max_connections=200"
      - "-c"
      - "shared_buffers=256MB"
```

| パラメータ | 値 | 説明 |
|----------|---|------|
| max_connections | 200 | 最大接続数 |
| shared_buffers | 256MB | 共有バッファサイズ |
| pg_stat_statements | 有効 | クエリ統計収集 |

#### MySQL

```yaml
services:
  mysql:
    image: mysql:8.0
    command:
      - "--max_connections=200"
      - "--innodb_buffer_pool_size=256M"
```

### キャッシュ設定

#### Redis

```yaml
environment:
  CACHE_ENABLE: true
  CACHE_TTL: 300
  REDIS_HOST: redis
  REDIS_PORT: 6379
```

| パラメータ | 値 | 説明 |
|----------|---|------|
| CACHE_ENABLE | true | キャッシュ有効化 |
| CACHE_TTL | 300 | キャッシュTTL(秒) |

---

## Docker Compose構成

### 基本構成

```yaml
services:
  nginx:
    image: nginx:alpine
    ports:
      - "8080:80"
    depends_on:
      - idp-server-1
      - idp-server-2

  idp-server-1:
    image: idp-server:latest
    deploy:
      resources:
        limits:
          cpus: '2'
          memory: 2G

  idp-server-2:
    image: idp-server:latest
    deploy:
      resources:
        limits:
          cpus: '2'
          memory: 2G

  postgresql:
    image: postgres:16
    command: ["postgres", "-c", "shared_preload_libraries=pg_stat_statements"]

  redis:
    image: redis:7-alpine
```

### リソース制限

| コンテナ | CPU | メモリ |
|---------|-----|-------|
| idp-server | 2コア | 2GB |
| postgresql | - | - |
| redis | - | - |
| nginx | - | - |

---

## テストデータ

### ユーザーデータ

```bash
# 10万ユーザーデータ生成
python3 ./performance-test/data/generate_users_100k.py

# テストユーザーJSON生成
./performance-test/data/test-user.sh all
```

生成されるファイル：

| ファイル | 内容 | 件数 |
|---------|------|------|
| generated_users_100k.tsv | ユーザーマスタ | 100,000 |
| generated_user_devices_100k.tsv | 認証デバイス | 100,000 |
| performance-test-user.json | テスト用ユーザーリスト | 500 |

### データ投入

```bash
# ユーザーデータ投入
psql -U idpserver -d idpserver -h localhost -p 5432 -c "\COPY idp_user (
  id, tenant_id, provider_id, external_user_id, name, email,
  email_verified, phone_number, phone_number_verified,
  preferred_username, status, authentication_devices
) FROM './performance-test/data/generated_users_100k.tsv'
WITH (FORMAT csv, HEADER false, DELIMITER E'\t')"

# 認証デバイスデータ投入
psql -U idpserver -d idpserver -h localhost -p 5432 -c "\COPY idp_user_authentication_devices (
  id, tenant_id, user_id, os, model, platform, locale,
  app_name, priority, available_methods,
  notification_token, notification_channel
) FROM './performance-test/data/generated_user_devices_100k.tsv'
WITH (FORMAT csv, HEADER false, DELIMITER E'\t')"
```

### テナントデータ

オンボーディングAPIを使用して、テスト用テナントを登録する。認証情報は`.env`ファイルから自動的に読み込まれる。

```bash
# 5テナントを登録（.envから認証情報を読み込み）
./performance-test/data/register-tenants.sh -n 5

# ドライラン
./performance-test/data/register-tenants.sh -n 5 -d true
```

生成されるファイル：
- `performance-test/data/performance-test-tenant.json` - テナント情報（k6スクリプトで使用）

---

## 負荷テストツール

### k6

```bash
# インストール
brew install k6

# バージョン確認
k6 version
```

### 環境変数

ロードテストスクリプトは`performance-test-tenant.json`から設定を自動読み込みするため、環境変数設定は最小限で済む。

```bash
# BASE_URLのみ必要（オプション、デフォルト: http://localhost:8080）
export BASE_URL=http://localhost:8080
```

:::note
ストレステストは引き続き環境変数が必要です。詳細は[テスト実行ガイド](./06-test-execution-guide.md)を参照してください。
:::

---

## モニタリング

### リソース監視

```bash
# Docker Stats
docker stats $(docker compose ps -q idp-server)
```

### PostgreSQL クエリ統計

```sql
-- pg_stat_statements有効化
CREATE EXTENSION pg_stat_statements;

-- 統計リセット
SELECT pg_stat_statements_reset();

-- 上位クエリ取得
SELECT query, calls, total_exec_time, mean_exec_time, rows
FROM pg_stat_statements
ORDER BY total_exec_time DESC
LIMIT 20;
```

### アプリケーションログ

```yaml
logging:
  level:
    root: info
    web: info
  file:
    name: logs/idp-server.log
    pattern:
      file: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
```

---

## 関連ドキュメント

- [performance-test/README.md](../../../../performance-test/README.md) - k6テスト実行ガイド
- [performance-test/README-mysql.md](../../../../performance-test/README-mysql.md) - MySQL環境向けガイド
