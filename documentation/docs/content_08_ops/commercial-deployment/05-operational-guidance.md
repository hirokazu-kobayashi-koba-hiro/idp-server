# 運用ガイダンス

idp-server 固有の運用知識とトラブルシューティング手順。

---

## 📌 本ドキュメントの責任範囲

### ✅ 本ドキュメントで扱う内容

- **ヘルスチェック**: Actuator エンドポイント、テナント固有ヘルスチェック
- **ログ運用**: JSON構造化ログ、MDCフィールド、リクエストデバッグログ
- **アプリケーションライフサイクル**: Graceful Shutdown、マルチインスタンス運用
- **定期メンテナンスタスク**: pg_cron ジョブの監視
- **トラブルシューティング**: 起動失敗、Redis障害、RLS問題、パフォーマンス

### ❌ 本ドキュメントで扱わない内容（利用者の責任範囲）

- **インフラ監視**: CloudWatch、Datadog等の監視ツール設定
- **アラート設計**: 閾値設定、通知チャネル設計
- **ログ収集基盤**: Fluentd、CloudWatch Logs等のログ転送設定
- **性能チューニング**: OS・JVM・データベースレベルの最適化

---

## 1. ヘルスチェック

### 1.1 Actuator エンドポイント

idp-server は Spring Boot Actuator を使用してヘルスチェックエンドポイントを提供します。Actuator はアプリケーションと同じポート（デフォルト: 8080）で動作します。

| エンドポイント | 用途 | チェック対象 |
|---------------|------|-------------|
| `GET /actuator/health` | 総合ヘルスチェック | DB + Redis + その他すべて |
| `GET /actuator/health/readiness` | Readiness Probe | DB接続、Redis接続 |
| `GET /actuator/health/liveness` | Liveness Probe | アプリケーション生存確認（ping） |

**レスポンス例**:

```json
// 正常時
{
  "status": "UP"
}

// 異常時
{
  "status": "DOWN"
}
```

**有効な Actuator エンドポイント**:

`application.yaml` の設定により、公開されるエンドポイントは `health` と `info` のみです。

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      probes:
        enabled: true
      show-details: when-authorized
  health:
    readiness-state:
      enabled: true
    liveness-state:
      enabled: true
```

### 1.2 テナント固有ヘルスチェック

Actuator とは別に、テナント単位のヘルスチェックエンドポイントが提供されています。テナントごとのデータベース接続を検証できます。

```
GET /{tenant-id}/v1/health
```

**レスポンス例**:

```json
// 正常時 (HTTP 200)
{
  "status": "UP"
}

// 異常時 (HTTP 503)
{
  "status": "DOWN"
}
```

**用途**: 特定テナントに対するリクエストが正常に処理可能かを確認する際に使用します。テナント設定の問題やRLS接続の確認に有効です。

### 1.3 Kubernetes 設定例

```yaml
apiVersion: apps/v1
kind: Deployment
spec:
  template:
    spec:
      containers:
        - name: idp-server
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 5
            failureThreshold: 3
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 60
            periodSeconds: 10
            failureThreshold: 3
```

**ポイント**:
- `readinessProbe` はDB・Redisへの接続を検証するため、`initialDelaySeconds` をアプリケーション起動完了まで十分に設定してください
- `livenessProbe` はアプリケーションの生存のみを確認するため、`readinessProbe` より長い間隔で問題ありません

---

## 2. ログ運用

### 2.1 JSON 構造化ログ

idp-server は Logback + LogstashEncoder を使用し、すべてのログをJSON形式で出力します。プロファイルによる出力形式の切り替えはなく、常にJSON出力です。

```xml
<!-- logback-spring.xml -->
<configuration>
    <appender name="JSON_CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <includeContext>true</includeContext>
            <includeMdc>true</includeMdc>
            <timestampPattern>yyyy-MM-dd HH:mm:ss.SSS</timestampPattern>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="JSON_CONSOLE"/>
    </root>
</configuration>
```

**出力例**:

```json
{
  "@timestamp": "2025-01-15 10:30:45.123",
  "@version": "1",
  "message": "Authorization request processed",
  "logger_name": "org.idp.server.core.oidc",
  "thread_name": "http-nio-8080-exec-1",
  "level": "INFO",
  "request_id": "550e8400-e29b-41d4-a716-446655440000",
  "tenant_id": "tenant-abc-123",
  "client_id": "my-client",
  "user_id": "user-xyz-789"
}
```

### 2.2 MDC フィールド一覧

すべてのログエントリに自動付与されるMDC（Mapped Diagnostic Context）フィールドです。ログ検索・フィルタリングに活用してください。

| MDCキー | 説明 | 設定タイミング |
|---------|------|---------------|
| `request_id` | リクエスト固有のUUID | リクエスト受信時に自動生成 |
| `tenant_id` | テナント識別子 | テナント解決後 |
| `client_id` | OAuthクライアントID | クライアント認証後 |
| `user_id` | ユーザー内部ID | ユーザー認証後 |
| `user_ex_sub` | ユーザー外部サブジェクト | ユーザー認証後 |
| `user_name` | ユーザー名 | ユーザー認証後 |

セキュリティイベントログでは、`StructuredJsonLogFormatter` が独自のJSON構造に `log_type` フィールドを直接付与します（MDC経由ではありません）。

| `log_type` 値 | 出力元 | 説明 |
|---------------|--------|------|
| `security_event` | `StructuredJsonLogFormatter` | 認証成功/失敗、OAuth操作等のセキュリティイベント |

**ログ検索例（jq）**:

```bash
# 特定テナントのエラーログを抽出
cat app.log | jq 'select(.tenant_id == "tenant-abc-123" and .level == "ERROR")'

# 特定リクエストのトレース
cat app.log | jq 'select(.request_id == "550e8400-e29b-41d4-a716-446655440000")'

# セキュリティイベントのみ抽出
cat app.log | jq 'select(.log_type == "security_event")'

# 認証失敗イベントの抽出
cat app.log | jq 'select(.log_type == "security_event" and (.tags | index("failure")))'
```

### 2.3 ログレベル設定

パッケージ単位でログレベルを環境変数で制御できます。

| 環境変数 | 対象 | デフォルト |
|---------|------|-----------|
| `LOGGING_LEVEL_ROOT` | アプリケーション全体 | `info` |
| `LOGGING_LEVEL_WEB` | Spring Web | `info` |
| `LOGGING_LEVEL_IDP_SERVER_PLATFORM` | プラットフォーム基盤 | `info` |
| `LOGGING_LEVEL_IDP_SERVER_CORE_OIDC` | OIDC コア処理 | `info` |
| `LOGGING_LEVEL_IDP_SERVER_CORE_ADAPTERS` | アダプター層 | `info` |
| `LOGGING_LEVEL_IDP_SERVER_CONTROL_PLANE` | 管理API | `info` |
| `LOGGING_LEVEL_IDP_SERVER_FEDERATION` | 外部IdP連携 | `info` |
| `LOGGING_LEVEL_IDP_SERVER_SECURITY_EVENT_HOOKS` | セキュリティイベントフック | `info` |
| `LOGGING_LEVEL_IDP_SERVER_HTTP_REQUEST_EXECUTOR` | 外部HTTP通信 | `info` |
| `LOGGING_LEVEL_REQUEST_RESPONSE_LOGGING_FILTER` | リクエスト/レスポンスログ | `info` |

**本番環境推奨**: `info`（デフォルト）。`debug` への変更は一時的な調査時のみ使用し、調査完了後に戻してください。

### 2.4 リクエスト/レスポンスデバッグログ

OAuth/OIDC エンドポイントのHTTPリクエスト・レスポンスの詳細をログ出力する機能です。外部サービスとの連携問題を調査する際に使用します。

**デフォルトでは無効** です。有効化するには以下の2つの設定が必要です。

**1. プロパティ有効化**:

```bash
IDP_LOGGING_REQUEST_RESPONSE_ENABLED=true
```

**2. ログレベルを DEBUG に設定**:

```bash
LOGGING_LEVEL_REQUEST_RESPONSE_LOGGING_FILTER=debug
```

**設定パラメータ**:

| 環境変数 | 説明 | デフォルト |
|---------|------|-----------|
| `IDP_LOGGING_REQUEST_RESPONSE_ENABLED` | 有効/無効 | `false` |
| `IDP_LOGGING_REQUEST_RESPONSE_MASK_TOKENS` | トークンマスク | `true` |
| `IDP_LOGGING_REQUEST_RESPONSE_MAX_BODY_SIZE` | 最大ボディサイズ（バイト） | `10000` |
| `IDP_LOGGING_REQUEST_RESPONSE_ENDPOINTS` | 対象エンドポイント | `/v1/tokens,/v1/authorizations,/v1/backchannel/authentications,/v1/userinfo` |

**自動マスク対象パラメータ**:
- `access_token`, `refresh_token`, `id_token`, `client_secret`, `password`

> **⚠️ 注意**: この機能はリクエスト・レスポンスの全ボディをログに記録します。本番環境での常時有効化は推奨しません。問題調査時に一時的に有効化し、調査完了後に無効に戻してください。

### 2.5 監査ログ

idp-server は認証・認可操作の監査ログを非同期で記録します。

**非同期処理の仕様**:
- 監査ログは `ThreadPoolTaskExecutor` により非同期で処理されます
- キューが満杯になった場合、リトライキューに退避されます

**リトライ仕様**:
- リトライ間隔: 60秒
- 最大リトライ回数: 3回
- 最大リトライ超過時: ログ出力して破棄

**関連する環境変数**:

| 環境変数 | 説明 | デフォルト |
|---------|------|-----------|
| `AUDIT_LOG_CORE_POOL_SIZE` | コアスレッド数 | `5` |
| `AUDIT_LOG_MAX_POOL_SIZE` | 最大スレッド数 | `30` |
| `AUDIT_LOG_QUEUE_CAPACITY` | キュー容量 | `5000` |

**Graceful Shutdown 時の動作**: アプリケーション停止時、リトライキューに残っている監査ログのフラッシュを試みます。ただし、DB接続がすでに切断されている場合、すべてのログの処理は保証されません。

---

## 3. アプリケーションライフサイクル

### 3.1 Graceful Shutdown

idp-server は Kubernetes 環境での安全なシャットダウンのために、2段階のGraceful Shutdown機構を実装しています。

**シャットダウンシーケンス**:

```
SIGTERM受信
  ├── 1. Readiness Probe が DOWN に遷移
  ├── 2. シャットダウンディレイ（デフォルト: 5秒）
  │      └── Kubernetes が Service エンドポイントから Pod を削除するのを待機
  └── 3. Spring Boot Graceful Shutdown 開始
         └── 処理中のリクエスト完了を待機（タイムアウト: 30秒）
```

**設定パラメータ**:

| 設定 | 環境変数 | デフォルト | 説明 |
|------|---------|-----------|------|
| シャットダウンディレイ | `IDP_SERVER_SHUTDOWN_DELAY` | `5s` | K8sエンドポイント削除待機時間 |
| グレースフル停止タイムアウト | — | `30s` | 処理中リクエストの完了待機時間 |

**Kubernetes 設定例**:

```yaml
spec:
  terminationGracePeriodSeconds: 40  # delay(5s) + timeout(30s) + margin(5s)
  containers:
    - name: idp-server
      lifecycle:
        preStop:
          exec:
            command: ["sh", "-c", "sleep 5"]  # shutdown delay と一致させる
```

**ポイント**:
- `terminationGracePeriodSeconds` は「シャットダウンディレイ + グレースフル停止タイムアウト + マージン」以上に設定してください
- `preStop` の `sleep` 値は `IDP_SERVER_SHUTDOWN_DELAY` と一致させてください

### 3.2 マルチインスタンスデプロイ

idp-server はステートレス設計のため、複数インスタンスの並行稼働が可能です。

**アーキテクチャ**:

```
                  ┌──────────────────┐
                  │  Load Balancer   │
                  └──────┬───────────┘
               ┌─────────┼─────────┐
               ▼         ▼         ▼
          ┌─────────┐ ┌─────────┐ ┌─────────┐
          │ Pod 1   │ │ Pod 2   │ │ Pod 3   │
          │idp-server│ │idp-server│ │idp-server│
          └────┬────┘ └────┬────┘ └────┬────┘
               │           │           │
        ┌──────┴───────────┴───────────┴──────┐
        │          Redis (セッション共有)        │
        └─────────────────────────────────────┘
        ┌──────┴───────────┴───────────┴──────┐
        │       PostgreSQL (データ共有/RLS)      │
        └─────────────────────────────────────┘
```

**設計上の特徴**:
- **セッション**: Redis に格納されるため、全インスタンスで共有
- **データ**: PostgreSQL + RLS によりテナント分離を維持
- **スティッキーセッション**: 不要（ロードバランサーでのセッション固定は不要）
- **スケーリング**: HPA（Horizontal Pod Autoscaler）による自動スケール可能

---

## 4. 定期メンテナンスタスク

### 4.1 pg_cron ジョブ一覧

idp-server では PostgreSQL の pg_cron 拡張を使用して定期タスクを実行しています。

| ジョブ名 | スケジュール | 対象DB | 処理内容 |
|---------|------------|--------|---------|
| `partman-maintenance` | 毎日 02:00 UTC | idpserver | パーティション作成・削除（pg_partman） |
| `archive-processing` | 毎日 03:00 UTC | idpserver | アーカイブパーティションの外部エクスポート |

**実行順序の設計**:
- `partman-maintenance`（02:00）が先に実行され、保持期間を超えたパーティションをアーカイブスキーマに退避
- `archive-processing`（03:00）が1時間後に実行され、アーカイブスキーマのテーブルを外部ストレージにエクスポート
- エクスポート成功後にテーブルを削除、失敗時は翌日リトライ

> **📝 Note**: デフォルトではエクスポート関数はスタブ実装（常に `FALSE` を返す）のため、アーカイブスキーマにテーブルが蓄積されます。クラウド環境に応じたエクスポートロジックの実装が必要です。詳細は [データベース設定](./03-database.md) を参照してください。

### 4.2 ジョブ監視

**ジョブ登録状態の確認**:

```sql
-- pg_cron ジョブ一覧（postgres DBで実行）
SELECT jobid, jobname, schedule, command, database, username, active
FROM cron.job
WHERE username = CURRENT_USER;
```

**ジョブ実行履歴の確認**:

```sql
-- 直近の実行結果（postgres DBで実行）
SELECT jobid, runid, job_pid, database, username, command,
       status, return_message,
       start_time, end_time
FROM cron.job_run_details
ORDER BY start_time DESC
LIMIT 20;

-- 失敗したジョブのみ抽出
SELECT jobid, runid, status, return_message, start_time
FROM cron.job_run_details
WHERE status = 'failed'
ORDER BY start_time DESC
LIMIT 10;
```

**アラート用クエリ**:

idp-server にはジョブ失敗時の通知機構は含まれていません。監視ツール（Datadog、CloudWatch 等）のカスタムクエリ監視で以下のクエリを定期実行し、`failed_count > 0` でアラートを発出してください。

```sql
-- 直近24時間のジョブ失敗検出（postgres DBで実行）
SELECT COUNT(*) AS failed_count
FROM cron.job_run_details d
JOIN cron.job j ON d.jobid = j.jobid
WHERE j.jobname IN ('partman-maintenance', 'archive-processing')
  AND d.status = 'failed'
  AND d.start_time > now() - interval '24 hours';
```

**パーティション一覧の確認**:

```sql
-- 現在のパーティション一覧（idpserver DBで実行）
SELECT parent_table, partition_tablename, partition_range
FROM partman.show_partitions('public.audit_log')
ORDER BY partition_range;

-- アーカイブスキーマのテーブル蓄積確認
SELECT table_name, pg_size_pretty(pg_total_relation_size('archive.' || table_name))
FROM information_schema.tables
WHERE table_schema = 'archive'
ORDER BY table_name;
```

---

## 5. トラブルシューティング

### 5.1 起動失敗

#### データベース接続失敗

**症状**: アプリケーション起動時に HikariCP の接続プール初期化が失敗し、起動が中断される。

**エラーログ例**（認証失敗の場合）:

```json
{
  "message": "HikariPool-1 - Starting...",
  "level": "INFO"
}
{
  "message": "Error starting Tomcat context. Exception: ...Failed to initialize pool: FATAL: password authentication failed for user \"idp_admin_user\"",
  "level": "ERROR"
}
{
  "message": "Application run failed",
  "level": "ERROR",
  "stack_trace": "...Caused by: com.zaxxer.hikari.pool.HikariPool$PoolInitializationException: Failed to initialize pool: FATAL: password authentication failed for user \"idp_admin_user\"\n...Caused by: org.postgresql.util.PSQLException: FATAL: password authentication failed for user \"idp_admin_user\"..."
}
```

**キーワード**: ログ内で以下のメッセージを検索してください。
- `Failed to initialize pool` — HikariCP が接続プール作成に失敗
- `PSQLException: FATAL:` — PostgreSQL が接続を拒否（認証失敗、DB不存在等）
- `Connection refused` — ネットワーク的にDBへ到達不可

**確認ポイント**:
1. DB接続URLが正しいか（`DB_WRITER_URL`, `DB_READER_URL`）
2. ユーザー名・パスワードが正しいか（`DB_WRITER_USER_NAME`, `DB_WRITER_PASSWORD`）
3. ネットワーク的にDBへ到達可能か
4. PostgreSQL が起動しているか

```bash
# 接続テスト
psql -h <DB_HOST> -U <DB_USER> -d idpserver -c "SELECT 1;"
```

#### Redis 接続失敗

**症状**: Redis への接続に失敗する。ただし **アプリケーションの起動自体は成功する**（Redis は遅延接続のため、起動時には接続を試行しません）。実際のリクエスト処理時にエラーが発生します。

**キャッシュ操作時のエラーログ**（縮退動作 — サービスは継続）:

```json
{
  "message": "Failed to find cache",
  "logger_name": "org.idp.server.core.adapters.datasource.cache.JedisCacheStore",
  "level": "ERROR",
  "stack_trace": "redis.clients.jedis.exceptions.JedisConnectionException: Failed to create socket.\n...Caused by: java.net.UnknownHostException: <REDIS_HOST>: Name does not resolve..."
}
```

**セッション操作時のエラーログ**（認証フロー停止）:

```json
{
  "message": "Failed to set key: <session-key>",
  "logger_name": "org.idp.server.core.adapters.datasource.session.JedisSessionStore",
  "level": "ERROR",
  "stack_trace": "redis.clients.jedis.exceptions.JedisConnectionException: Failed to create socket.\n...Caused by: java.net.UnknownHostException: <REDIS_HOST>..."
}
```

セッション操作の場合は `SessionStoreException` がスローされ、認証フローが中断します。

**キーワード**: ログ内で以下のメッセージを検索してください。
- `JedisConnectionException: Failed to create socket` — Redis への接続失敗
- `UnknownHostException` — ホスト名解決失敗
- `Failed to find cache` / `Failed to put cache` — キャッシュ操作失敗（縮退動作）
- `Failed to set key` / `Failed to get key` — セッション操作失敗（サービス影響あり）

> **⚠️ 注意**: ヘルスチェック（`/actuator/health`）は Redis 障害時でも `UP` を返す場合があります。Redis の状態を正確に監視するには、Redis 自体の監視（`redis-cli ping`）が必要です。

**確認ポイント**:
1. `REDIS_HOST`, `REDIS_PORT` の設定値
2. Redis が起動しているか
3. パスワード設定（`REDIS_CACHE_PASSWORD`, `REDIS_SESSION_PASSWORD`）

```bash
# 接続テスト
redis-cli -h <REDIS_HOST> -p <REDIS_PORT> ping
```

#### Flyway マイグレーション失敗

**症状**: スキーマ変更の適用に失敗し、アプリケーションが起動しない。

**確認ポイント**:
1. Flyway 実行ユーザー（`idp`）の権限が十分か
2. `flyway_schema_history` テーブルに失敗レコードがないか
3. 手動でスキーマ変更を適用した場合、バージョン競合していないか

```sql
-- Flyway 状態確認
SELECT installed_rank, version, description, type, success
FROM flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 10;

-- 失敗したマイグレーションの修復（注意: 原因特定後に実行）
-- DELETE FROM flyway_schema_history WHERE success = false;
```

#### 必須環境変数の未設定

**症状**: 起動時に設定値エラーが発生する。

**必須環境変数一覧**（設定漏れで起動に失敗する主要項目）:

| 環境変数 | 用途 |
|---------|------|
| `IDP_SERVER_API_KEY` | 管理API認証キー |
| `IDP_SERVER_API_SECRET` | 管理API認証シークレット |
| `ENCRYPTION_KEY` | データ暗号化キー |
| `DB_WRITER_USER_NAME` / `DB_WRITER_PASSWORD` | DB接続認証 |
| `CONTROL_PLANE_DB_WRITER_USER_NAME` / `CONTROL_PLANE_DB_WRITER_PASSWORD` | Control Plane用DB接続認証 |

詳細は [環境変数設定](./02-environment-variables.md) を参照してください。

### 5.2 Redis 障害時の影響

Redis は **キャッシュ** と **セッション** の2つの用途で使用されており、障害時の影響は用途により異なります。

#### キャッシュ（JedisCacheStore）— 縮退動作

| 操作 | 障害時の動作 | 影響 |
|-----|-------------|------|
| `put` | 例外をcatch → エラーログ出力 | キャッシュ未保存（次回DBから取得） |
| `find` | 例外をcatch → `Optional.empty()` 返却 | 常にDBから取得 |
| `exists` | 例外をcatch → `false` 返却 | 存在チェックが常にfalse |
| `delete` | 例外をcatch → エラーログ出力 | キャッシュ残留（TTLで自然消滅） |
| `increment` | 例外をcatch → `0` 返却 | カウンタ未更新 |

**影響**: サービスは継続可能ですが、すべてのリクエストがDBに直接アクセスするため **DB負荷が増大** します。

#### セッション（JedisSessionStore → OPSessionDataSource / ClientSessionDataSource）— 縮退動作

`JedisSessionStore` 自体は書き込み系操作で `SessionStoreException` をスローしますが、呼び出し元の `OPSessionDataSource` / `ClientSessionDataSource` が **すべての例外を catch して graceful degradation** します。

**JedisSessionStore レベル**（低レベル）:

| 操作 | 障害時の動作 |
|-----|-------------|
| `set` | `SessionStoreException` throw |
| `get` | `Optional.empty()` 返却 |
| `delete` | `SessionStoreException` throw |
| `setAdd` / `setRemove` | `SessionStoreException` throw |
| `expire` | `SessionStoreException` throw |
| `ttl` | `-2` 返却 |

**DataSource レベル**（実際のアプリケーション動作）:

| 操作 | 障害時の動作 | ログメッセージ |
|-----|-------------|--------------|
| `register` | エラーログ出力 → セッション未保存で継続 | `Failed to save OP session (graceful degradation)` |
| `findById` | `Optional.empty()` 返却 → セッション未検出扱い | `Failed to find OP session (graceful degradation)` |
| `findByUser` | 空リスト返却 | `Failed to find OP sessions by user (graceful degradation)` |
| `delete` | エラーログ出力 → 削除失敗で継続 | `Failed to delete OP session (graceful degradation)` |
| `updateLastAccessedAt` | エラーログ出力 → 更新失敗で継続 | `Failed to update OP session lastAccessedAt (graceful degradation)` |

**影響**: 認証フロー自体は **停止しません** が、以下の機能が無効化されます。

| 機能 | Redis 正常時 | Redis 障害時 |
|------|-------------|-------------|
| 認証（ログイン） | 正常動作 | **毎回再認証が必要**（セッション未保存のため） |
| SSO（シングルサインオン） | OPセッション共有で再認証不要 | **無効**（OPセッションが保存されない） |
| RP-Initiated Logout | 関連セッション一括削除 | **不完全**（セッション検索が空を返す） |
| Back-Channel Logout | クライアントセッション通知 | **不完全**（セッション情報を取得できない） |

#### セッションベース認可の失敗

上記の通り、認証フロー自体は停止しませんが、**セッションの有効性を検証するフロー**（SSO時の `authorizeWithSession`）では明確なエラーが返却されます。

**エラー発生の流れ**:

```
1. 認証成功 → OPSession 作成試行 → Redis 保存失敗（graceful degradation でログ出力のみ）
2. セッション Cookie はブラウザに設定される（Redis の保存失敗とは無関係に設定される）
3. 次回リクエスト（SSO）→ Cookie から OPSession ID を取得 → Redis からセッション取得不可
4. OIDCSessionVerifier: opSession == null → sessionNotFound
5. クライアントに BAD_REQUEST を返却
```

セッション Cookie は設定されているにもかかわらず、Redis にセッションデータが存在しないため、セッションベースの認可（SSO）は `BAD_REQUEST` エラーで失敗します。クライアントアプリケーションは `prompt=login` を付与して完全な再認証フローにフォールバックする必要があります。

**対処方針**:
1. Redis の復旧を優先する（認証は動くがユーザー体験が大幅に低下するため）
2. ログで `graceful degradation` を検索し、Redis 障害の影響範囲を確認する
3. Redis 復旧後、既存ユーザーは再ログインが必要になる

### 5.3 AUTH_SESSION 検証エラー（セッション同一性確認）

認証ポリシーで `auth_session_binding_required`（デフォルト: `true`）が有効な場合、認可フローの各ステップで `IDP_AUTH_SESSION` Cookie と AuthenticationTransaction（DB）の `auth_session_id` の一致を検証します。この検証は認可フローハイジャック攻撃を防止するためのセキュリティ機構です。

**エラーメッセージ**:

| エラー | HTTP Status | 原因 |
|--------|-------------|------|
| `auth_session_mismatch: Missing AUTH_SESSION cookie` | 401 | Cookie がブラウザから送信されていない |
| `auth_session_mismatch: AUTH_SESSION cookie does not match` | 401 | Cookie の値が DB の値と一致しない |

**よくある原因と対処**:

| 原因 | 詳細 | 対処 |
|------|------|------|
| クロスオリジン環境で `SameSite=Lax`（デフォルト） | 認証UIとAPIが異なるドメインの場合、ブラウザが Cookie を送信しない | `IDP_AUTH_SESSION_SAME_SITE=None` + `IDP_AUTH_SESSION_SECURE=true` に設定 |
| HTTP 環境で `Secure=true`（デフォルト） | HTTP では Secure 属性の Cookie をブラウザが送信しない | 開発環境のみ `IDP_AUTH_SESSION_SECURE=false` に設定 |
| リバースプロキシによるパス変更 | Cookie path は `/{tenantId}/` にスコープされており、プロキシでパスが書き換わると一致しない | プロキシでパスプレフィクスを維持するか、`session_config.cookie_path` を設定 |
| 複数タブでの認可フロー | 各認可リクエストが新しい `auth_session_id` で Cookie を上書きするため、先に開始したタブの Cookie が無効になる | ユーザー操作上の制約（同時に複数の認可フローを開始しない） |

**確認手順**:

```bash
# ログで AUTH_SESSION 検証失敗を検索
cat app.log | jq 'select(.message | contains("AUTH_SESSION validation failed"))'
```

**設定値**:

| 環境変数 | デフォルト | 説明 |
|---------|-----------|------|
| `IDP_AUTH_SESSION_SAME_SITE` | `Lax` | Cookie SameSite 属性（`Lax` / `None`） |
| `IDP_AUTH_SESSION_SECURE` | `true` | Cookie Secure フラグ（`true` / `false`） |

> **📝 Note**: `auth_session_binding_required` を `false` に設定すると検証をスキップできますが、認可フローハイジャック攻撃のリスクが生じるため、本番環境では推奨しません。

### 5.4 エラーレスポンス形式

すべてのAPIエラーは統一されたJSON形式で返却されます。

```json
{
  "error": "invalid_request",
  "error_description": "詳細なエラーメッセージ"
}
```

**例外クラスとHTTPステータスのマッピング**:

| 例外クラス | HTTP Status | `error` 値 |
|-----------|-------------|-----------|
| `BadRequestException` | 400 Bad Request | `invalid_request` |
| `MaliciousInputException` | 400 Bad Request | `invalid_request` |
| `HttpMessageConversionException` | 400 Bad Request | `invalid_request` |
| `DateTimeParseException` | 400 Bad Request | `invalid_request` |
| `HttpMediaTypeNotSupportedException` | 400 Bad Request | `invalid_request` |
| `UnauthorizedException` | 401 Unauthorized | `invalid_request` |
| `ForbiddenException` | 403 Forbidden | `invalid_request` |
| `NotFoundException` | 404 Not Found | `invalid_request` |
| `HttpRequestMethodNotSupportedException` | 405 Method Not Allowed | `invalid_request` |
| `HttpMediaTypeNotAcceptableException` | 406 Not Acceptable | `invalid_request` |
| `ConflictException` | 409 Conflict | `invalid_request` |
| `SqlDuplicateKeyException` | 409 Conflict | `invalid_request` |
| `InvalidConfigurationException` | 500 Internal Server Error | `server_error` |
| その他 `Exception` | 500 Internal Server Error | `server_error` |

**セキュリティ上の注意**: `MaliciousInputException`（攻撃検知）の場合、クライアントには汎用的なエラーメッセージのみ返却し、攻撃の詳細はサーバーログに ERROR レベルで記録されます。

### 5.5 パフォーマンス問題

#### HikariCP 接続プール枯渇

**症状**: レスポンス遅延の増加、`Connection is not available` エラー。

**確認ポイント**:
- `DB_WRITER_MAX_POOL_SIZE`（デフォルト: `30`）が負荷に対して十分か
- `DB_READER_MAX_POOL_SIZE`（デフォルト: `30`）が負荷に対して十分か
- スロークエリがコネクションを長時間占有していないか

**関連設定**:

| 環境変数 | デフォルト | 説明 |
|---------|-----------|------|
| `DB_WRITER_MAX_POOL_SIZE` | `30` | Writer 最大接続数 |
| `DB_WRITER_TIMEOUT` | `30000` (30秒) | 接続取得タイムアウト |
| `DB_WRITER_MAX_LIFETIME` | `1800000` (30分) | 接続の最大生存時間 |
| `DB_READER_MAX_POOL_SIZE` | `30` | Reader 最大接続数 |

#### 非同期キュー飽和

idp-server は 3 つの非同期キューを持ち、キュー容量を超えるとリトライキューに退避します。

**処理フロー**:

```
リクエスト処理スレッド
  └── 非同期タスク投入
       ├── タスクキューに空きあり → 非同期スレッドで処理
       └── タスクキューが満杯 → RejectedExecutionHandler
            ├── [セキュリティイベント] discardable → ログ出力して破棄
            └── [全キュー共通] → リトライキューに退避
                 └── 60秒間隔でリトライ（最大3回）→ 超過で破棄
```

**キュー設定一覧**:

| キュー | コアプール | 最大プール | タスクキュー容量 | リトライキュー容量 |
|--------|----------|----------|---------------|-----------------|
| セキュリティイベント | `5` | `30` | `5000` | `5000`（制限あり） |
| ユーザーライフサイクルイベント | `5` | `10` | `1000` | **無制限** |
| 監査ログ | `5` | `30` | `5000` | **無制限** |

**セキュリティイベントの discardable 判定**:

タスクキューが満杯になった際、読み取り系イベント（`userinfo_success`, `inspect_token_success` 等）は低優先度として即座に破棄されます（INFO ログのみ）。認証成功・失敗等の重要イベントはリトライキューに退避されます。

**監視すべきログメッセージ**:

| ログメッセージ | レベル | 意味 |
|---------------|--------|------|
| `security event rejected, queuing for retry` | WARN | タスクキュー満杯 → リトライキュー退避 |
| `security event discarded (low priority)` | INFO | 読み取り系イベント破棄（正常動作） |
| `retry queue full, dropping security event` | ERROR | リトライキューも満杯 → **イベント消失** |
| `audit log rejected, queuing for retry` | WARN | 監査ログのタスクキュー満杯 |
| `max retries exceeded, dropping audit log` | ERROR | リトライ3回失敗 → **監査ログ消失** |
| `max retries exceeded, dropping security event` | ERROR | リトライ3回失敗 → **イベント消失** |

> **⚠️ 注意**: 監査ログとユーザーライフサイクルイベントのリトライキューには容量制限がありません。DB 障害等でリトライが継続的に失敗する場合、リトライキューがメモリ上で膨張し続けるリスクがあります。`processing audit log retry queue: N events` のログで蓄積数を監視してください。

**関連する環境変数**:

| 環境変数 | デフォルト | 説明 |
|---------|-----------|------|
| `SECURITY_EVENT_CORE_POOL_SIZE` | `5` | セキュリティイベント コアスレッド数 |
| `SECURITY_EVENT_MAX_POOL_SIZE` | `30` | セキュリティイベント 最大スレッド数 |
| `SECURITY_EVENT_QUEUE_CAPACITY` | `5000` | セキュリティイベント タスクキュー容量 |
| `SECURITY_EVENT_RETRY_QUEUE_CAPACITY` | `5000` | セキュリティイベント リトライキュー容量 |
| `AUDIT_LOG_CORE_POOL_SIZE` | `5` | 監査ログ コアスレッド数 |
| `AUDIT_LOG_MAX_POOL_SIZE` | `30` | 監査ログ 最大スレッド数 |
| `AUDIT_LOG_QUEUE_CAPACITY` | `5000` | 監査ログ タスクキュー容量 |
| `USER_LIFECYCLE_EVENT_CORE_POOL_SIZE` | `5` | ユーザーライフサイクル コアスレッド数 |
| `USER_LIFECYCLE_EVENT_MAX_POOL_SIZE` | `10` | ユーザーライフサイクル 最大スレッド数 |
| `USER_LIFECYCLE_EVENT_QUEUE_CAPACITY` | `1000` | ユーザーライフサイクル タスクキュー容量 |

#### Redis 接続プール枯渇

**症状**: セッション操作・キャッシュ操作のタイムアウト。

**関連設定**:

| 環境変数 | デフォルト | 説明 |
|---------|-----------|------|
| `REDIS_MAX_TOTAL` | `20` | キャッシュ用最大接続数 |
| `REDIS_SESSION_MAX_TOTAL` | `20` | セッション用最大接続数 |
| `REDIS_CACHE_TIMEOUT` | `10000` (10秒) | キャッシュ用接続タイムアウト |
| `REDIS_SESSION_TIMEOUT` | `10000` (10秒) | セッション用接続タイムアウト |

#### Tomcat スレッド枯渇

**症状**: 新規リクエストの受付拒否、レスポンスタイムの急増。

**関連設定**:

| 環境変数 | デフォルト | 説明 |
|---------|-----------|------|
| `SERVER_TOMCAT_THREADS_MAX` | `300` | 最大ワーカースレッド数 |
| `SERVER_TOMCAT_THREADS_MIN_SPARE` | `50` | 最小スペアスレッド数 |

---

## 6. 運用チェックリスト

### 日次確認

- [ ] ヘルスチェック（`/actuator/health`）が `UP` を返すこと
- [ ] pg_cron ジョブ（`partman-maintenance`, `archive-processing`）が成功していること
- [ ] ERROR レベルのログが異常増加していないこと
- [ ] 監査ログのリトライキューが蓄積していないこと

### 週次確認

- [ ] パーティションが適切に作成・削除されていること
- [ ] アーカイブスキーマにテーブルが蓄積していないこと（エクスポート関数実装済みの場合）
- [ ] HikariCP 接続プール使用率が上限に達していないこと
- [ ] Redis メモリ使用量が閾値以内であること

### デプロイ時確認

- [ ] Flyway マイグレーションが正常に完了していること（`flyway_schema_history`）
- [ ] Readiness Probe が `UP` を返していること
- [ ] テナント固有ヘルスチェック（`/{tenant-id}/v1/health`）が正常であること
- [ ] ログ出力が正常に行われていること（JSON形式、MDCフィールド付与）

---

## 🔗 関連ドキュメント

- [デプロイ概要](./00-overview.md)
- [環境変数設定](./02-environment-variables.md)
- [データベース設定](./03-database.md)
- [初期設定](./04-initial-configuration.md)
- [マイグレーション戦略](./06-migration-strategy.md)
