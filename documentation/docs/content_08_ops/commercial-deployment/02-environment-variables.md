# 環境変数・セキュリティパラメータ設定

idp-server の商用デプロイメントにおける環境変数、セキュリティパラメータ、および機密情報管理について説明します。

## 🎯 SRE向け設定ガイド

### 最低限設定すべき必須項目 (Priority 1)

🔒 **機密情報（厳重管理が必要）**
- `IDP_SERVER_API_KEY` - 管理API認証キー
- `IDP_SERVER_API_SECRET` - 管理API認証シークレット
- `ENCRYPTION_KEY` - データ暗号化キー (AES-256)
- `CONTROL_PLANE_DB_WRITER_PASSWORD` - Control Plane用DBパスワード
- `DB_WRITER_PASSWORD` - アプリケーション用DBパスワード

**✅ 基本環境設定**
- `DATABASE_TYPE` - データベース種類 (`POSTGRESQL`)
- `DB_WRITER_URL` - プライマリDB接続URL
- `DB_READER_URL` - レプリカDB接続URL
- `REDIS_HOST` - Redis/ElastiCache エンドポイント
- `IDP_SESSION_MODE` - セッションモード (`redis`推奨)

### 本番環境で調整すべき項目 (Priority 2)

**⚡ パフォーマンス設定**
- `DB_WRITER_MAX_POOL_SIZE` - 書き込み接続プール (負荷に応じて)
- `DB_READER_MAX_POOL_SIZE` - 読み込み接続プール (書き込みの1.5-2倍)
- `REDIS_MAX_TOTAL` - Redis接続プール (同時アクセス数に応じて)
- `CACHE_TIME_TO_LIVE_SECOND` - キャッシュTTL (業務要件に応じて)

**🔒 セキュリティ設定**
- `LOGGING_LEVEL_ROOT` - 本番は `info` 推奨（監査・トラブルシューティングに必要）
- `SESSION_TIMEOUT` - セキュリティポリシーに応じて

### 環境規模別推奨値

| 環境規模 | 想定負荷 | Writer Pool | Reader Pool | Redis Pool | Cache TTL |
|---------|---------|------------|------------|-----------|-----------|
| **小規模** | < 100 TPS | 15 | 30 | 50 | 300s |
| **中規模** | 100-500 TPS | 50 | 80 | 100 | 600s |
| **大規模** | 500+ TPS | 100 | 150 | 200 | 900s |

## 📝 application.yaml 設定詳細

idp-server の設定は `application.yaml` で定義され、環境変数で上書き可能です。以下に全設定項目の一覧表と詳細解説を記載します。

---

## 📋 設定パラメータ一覧表

### idp.configurations (コア設定)

| パラメータ | 環境変数 | 説明 | デフォルト値 | 本番推奨値 |
|-----------|----------|------|-------------|-----------|
| `adminTenantId` | `ADMIN_TENANT_ID` | 管理テナントID | `67e7eae6-62b0-4500-9eff-87459f63fc66` | 本番固有UUID |
| `apiKey` | `IDP_SERVER_API_KEY` | 管理API認証キー | なし (必須) | Secrets Manager |
| `apiSecret` | `IDP_SERVER_API_SECRET` | 管理API認証シークレット | なし (必須) | Secrets Manager |
| `encryptionKey` | `ENCRYPTION_KEY` | データ暗号化キー (AES-256) | なし (必須) | Secrets Manager |
| `databaseType` | `DATABASE_TYPE` | データベース種類 | `POSTGRESQL` | `POSTGRESQL` |

### idp.server (サーバー設定)

| パラメータ | 環境変数 | 説明 | デフォルト値 | 本番推奨値 |
|-----------|----------|------|-------------|-----------|
| `shutdown.delay` | `IDP_SERVER_SHUTDOWN_DELAY` | Graceful shutdown遅延時間 | `5s` | `5s` (負荷に応じて調整) |

**Graceful Shutdown説明:**
- アプリケーション停止時に新規リクエストを受け付けず、既存リクエストの完了を待つ
- Kubernetes等のオーケストレータでのローリングアップデート時に重要
- `delay`は、終了シグナル受信後、既存リクエスト完了を待つ時間

### idp.session (セッション管理設定)

| パラメータ | 環境変数 | 説明 | デフォルト値 | 本番推奨値 |
|-----------|----------|------|-------------|-----------|
| `mode` | `IDP_SESSION_MODE` | セッションモード (`redis`/`servlet`/`disabled`) | `redis` | `redis` |

**セッションモード説明:**
- **redis**: Spring Session with Redis (本番・マルチインスタンス環境推奨)
- **servlet**: 標準 HttpSession (ローカル開発・単一インスタンス向け)
- **disabled**: セッション無効化 (ステートレスAPI向け)

### idp.time (タイムゾーン設定)

| パラメータ | 環境変数 | 説明 | デフォルト値 | 本番推奨値 |
|-----------|----------|------|-------------|-----------|
| `zone` | `TIME_ZONE` | アプリケーションタイムゾーン | `UTC` | `UTC` |

### idp.datasource.control-plane (Control Plane用データベース)

#### Writer 設定
| パラメータ | 環境変数 | 説明 | デフォルト値 | 本番推奨値 |
|-----------|----------|------|-------------|-----------|
| `url` | `CONTROL_PLANE_DB_WRITER_URL` | 書き込み用DB接続URL | `jdbc:postgresql://localhost:5432/idpserver` | RDS Primary エンドポイント |
| `username` | `CONTROL_PLANE_DB_WRITER_USER_NAME` | Control Plane用DBユーザー | **必須（デフォルトなし）** | `idp_admin` |
| `password` | `CONTROL_PLANE_DB_WRITER_PASSWORD` | Control Plane用DBパスワード | **必須（デフォルトなし）** | Secrets Manager |
| `connection-timeout` | `CONTROL_PLANE_DB_WRITER_TIMEOUT` | 接続タイムアウト (ms) | `2000` | `3000` |
| `maximum-pool-size` | `CONTROL_PLANE_DB_WRITER_MAX_POOL_SIZE` | 最大接続プールサイズ | `10` | `15` |
| `minimum-idle` | `CONTROL_PLANE_DB_WRITER_MIN_IDLE` | 最小アイドル接続数 | `5` | `5` |

#### Reader 設定
| パラメータ | 環境変数 | 説明 | デフォルト値 | 本番推奨値 |
|-----------|----------|------|-------------|-----------|
| `url` | `CONTROL_PLANE_DB_READER_URL` | 読み込み用DB接続URL | `jdbc:postgresql://localhost:5433/idpserver` | RDS Replica エンドポイント |
| `username` | `CONTROL_PLANE_DB_READER_USER_NAME` | Control Plane用読み込み専用DBユーザー | **必須（デフォルトなし）** | `idp_admin_ro` |
| `password` | `CONTROL_PLANE_DB_READER_PASSWORD` | Control Plane用読み込み専用DBパスワード | **必須（デフォルトなし）** | Secrets Manager |
| `connection-timeout` | `CONTROL_PLANE_DB_READER_TIMEOUT` | 接続タイムアウト (ms) | `2000` | `3000` |
| `maximum-pool-size` | `CONTROL_PLANE_DB_READER_MAX_POOL_SIZE` | 最大接続プールサイズ | `10` | `20` |
| `minimum-idle` | `CONTROL_PLANE_DB_READER_MIN_IDLE` | 最小アイドル接続数 | `5` | `8` |

### idp.datasource.app (アプリケーション用データベース)

#### Writer 設定
| パラメータ | 環境変数 | 説明 | デフォルト値 | 本番推奨値 |
|-----------|----------|------|-------------|-----------|
| `url` | `DB_WRITER_URL` | 書き込み用DB接続URL | `jdbc:postgresql://localhost:5432/idpserver` | RDS Primary エンドポイント |
| `username` | `DB_WRITER_USER_NAME` | アプリ用DBユーザー | **必須（デフォルトなし）** | `idp_app_user` |
| `password` | `DB_WRITER_PASSWORD` | アプリ用DBパスワード | **必須（デフォルトなし）** | Secrets Manager |
| `connection-timeout` | `DB_WRITER_TIMEOUT` | 接続タイムアウト (ms) | `2000` | `2000` |
| `maximum-pool-size` | `DB_WRITER_MAX_POOL_SIZE` | 最大接続プールサイズ | `30` | `50` |
| `minimum-idle` | `DB_WRITER_MIN_IDLE` | 最小アイドル接続数 | `10` | `15` |

#### Reader 設定
| パラメータ | 環境変数 | 説明 | デフォルト値 | 本番推奨値 |
|-----------|----------|------|-------------|-----------|
| `url` | `DB_READER_URL` | 読み込み用DB接続URL | `jdbc:postgresql://localhost:5433/idpserver` | RDS Replica エンドポイント |
| `username` | `DB_READER_USER_NAME` | アプリ用読み込み専用DBユーザー | **必須（デフォルトなし）** | `idp_app_user_ro` |
| `password` | `DB_READER_PASSWORD` | アプリ用読み込み専用DBパスワード | **必須（デフォルトなし）** | Secrets Manager |
| `connection-timeout` | `DB_READER_TIMEOUT` | 接続タイムアウト (ms) | `2000` | `2000` |
| `maximum-pool-size` | `DB_READER_MAX_POOL_SIZE` | 最大接続プールサイズ | `30` | `80` |
| `minimum-idle` | `DB_READER_MIN_IDLE` | 最小アイドル接続数 | `10` | `25` |

**注意**: `DATABASE_TYPE`環境変数（`POSTGRESQL`/`MYSQL`）により実行時にデータベース種別が切り替わります。環境変数は同じものを使用するため、接続先URLで適切なJDBCプレフィックスを指定してください。


### idp.cache (Redis キャッシュ設定)

| パラメータ | 環境変数 | 説明 | デフォルト値 | 本番推奨値 |
|-----------|----------|------|-------------|-----------|
| `enabled` | `CACHE_ENABLE` | キャッシュ有効化 | `true` | `true` |
| `timeToLiveSecond` | `CACHE_TIME_TO_LIVE_SECOND` | キャッシュTTL (秒) | `300` | `600` |
| `host` | `REDIS_HOST` | Redis ホスト | `localhost` | ElastiCache エンドポイント |
| `port` | `REDIS_PORT` | Redis ポート | `6379` | `6379` |
| `database` | `REDIS_CACHE_DATABASE` | Redis データベース番号 | `0` | `1` (Spring Sessionと分離推奨) |
| `timeout` | `REDIS_CACHE_TIMEOUT` | 接続タイムアウト (ミリ秒) | `10000` | `5000` |
| `password` | `REDIS_CACHE_PASSWORD` | Redis パスワード | `(空)` | Secrets Manager |
| `maxTotal` | `REDIS_MAX_TOTAL` | 最大接続数 | `20` | `100` |
| `maxIdle` | `REDIS_MAX_IDLE` | 最大アイドル接続数 | `3` | `10` |
| `minIdle` | `REDIS_MIN_IDLE` | 最小アイドル接続数 | `2` | `5` |

### トークンキャッシュ設定

| パラメータ | 環境変数 | 説明 | デフォルト値 | 本番推奨値 |
|-----------|----------|------|-------------|-----------|
| - | `TOKEN_CACHE_ENABLED` | トークンイントロスペクションのRedisキャッシュ有効化 | `false` | `true`（高負荷環境） |

**注意**: `CACHE_ENABLE=true`（Redis有効）が前提条件。`TOKEN_CACHE_ENABLED=true` に設定すると、アクセストークンのイントロスペクション結果をRedisにキャッシュ（TTL 60秒）し、DB負荷を軽減します。中〜高負荷環境（100+ VU）でIntrospection p95が63-74%改善されます。

### logging.level (ログレベル設定)

| パラメータ | 環境変数 | 説明 | デフォルト値 | 本番推奨値 |
|-----------|----------|------|-------------|-----------|
| `root` | `LOGGING_LEVEL_ROOT` | ルートログレベル | `info` | `info` |
| `web` | `LOGGING_LEVEL_WEB` | Webレイヤーログレベル | `info` | `info` |
| `platform` | `LOGGING_LEVEL_IDP_SERVER_PLATFORM` | プラットフォームログレベル | `info` | `info` |
| `authentication` | `LOGGING_LEVEL_IDP_SERVER_AUTHENTICATION_INTERACTORS` | 認証処理ログレベル | `info` | `info` |
| `control_plane` | `LOGGING_LEVEL_IDP_SERVER_CONTROL_PLANE` | コントロールプレーンログレベル | `info` | `info` |
| `core.oidc` | `LOGGING_LEVEL_IDP_SERVER_CORE_OIDC` | OIDC コアログレベル | `info` | `info` |
| `core.adapters` | `LOGGING_LEVEL_IDP_SERVER_CORE_ADAPTERS` | コアアダプターログレベル | `info` | `info` |
| `core.extension` | `LOGGING_LEVEL_IDP_SERVER_CORE_EXTENSION` | コア拡張ログレベル | `info` | `info` |
| `email.aws` | `LOGGING_LEVEL_IDP_SERVER_EMAIL_AWS` | AWS Email サービスログレベル | `info` | `info` |
| `federation` | `LOGGING_LEVEL_IDP_SERVER_FEDERATION` | フェデレーションログレベル | `info` | `info` |
| `notification.push.fcm` | `LOGGING_LEVEL_IDP_SERVER_NOTIFICATION_PUSH_FCM` | FCM プッシュ通知ログレベル | `info` | `info` |
| `security.event.hook.ssf` | `LOGGING_LEVEL_IDP_SERVER_SECURITY_EVENT_HOOK_SSF` | SSF セキュリティイベントログレベル | `info` | `info` |
| `security.event.hooks` | `LOGGING_LEVEL_IDP_SERVER_SECURITY_EVENT_HOOKS` | セキュリティイベントログレベル | `info` | `info` |
| `adapters.springboot` | `LOGGING_LEVEL_IDP_SERVER_ADAPTERS_SPRING_BOOT` | Spring Boot アダプターログレベル | `info` | `info` |
| `usecases` | `LOGGING_LEVEL_IDP_SERVER_USECASES` | ユースケースログレベル | `info` | `info` |
| `authenticators.webauthn4j` | `LOGGING_LEVEL_IDP_SERVER_AUTHENTICATORS_WEBAUTHN4J` | WebAuthn4J 認証ログレベル | `info` | `info` |
| `http.request.executor` | `LOGGING_LEVEL_IDP_SERVER_HTTP_REQUEST_EXECUTOR` | HTTP リクエスト実行ログレベル | `debug` | `info` |
| `request.response.logging` | `LOGGING_LEVEL_REQUEST_RESPONSE_LOGGING_FILTER` | リクエスト/レスポンスログレベル | `info` | `info` (デバッグ時のみ `debug`) |

### idp.logging.request-response (リクエスト/レスポンスデバッグログ設定)

| パラメータ | 環境変数 | 説明 | デフォルト値 | 本番推奨値 |
|-----------|----------|------|-------------|-----------|
| `enabled` | `IDP_LOGGING_REQUEST_RESPONSE_ENABLED` | リクエスト/レスポンスログ有効化 | `false` | `false` (デバッグ時のみ `true`) |
| `mask-tokens` | `IDP_LOGGING_REQUEST_RESPONSE_MASK_TOKENS` | トークンマスキング有効化 | `true` | `true` |
| `max-body-size` | `IDP_LOGGING_REQUEST_RESPONSE_MAX_BODY_SIZE` | ログ出力する最大ボディサイズ (バイト) | `10000` | `10000` |
| `endpoints` | `IDP_LOGGING_REQUEST_RESPONSE_ENDPOINTS` | ログ対象エンドポイント (カンマ区切り) | `/v1/tokens,/v1/authorizations,/v1/backchannel/authentications,/v1/userinfo` | 必要なエンドポイントのみ指定 |

**用途:**
- OAuth/OIDCエンドポイントのHTTPリクエスト/レスポンスをDEBUGレベルで詳細ログ出力
- 結合テスト時のパラメータ認識齟齬のデバッグに有用
- `authorization_details`、カスタムスコープ等の実際の送受信内容を確認可能

**セキュリティ対策:**
- **多層防御**: Property (`enabled`) + LogLevel (`debug`) + Masking (`mask-tokens`)
- **デフォルト無効**: `enabled=false` で本番環境での誤動作を防止
- **自動マスキング**: `access_token`, `refresh_token`, `id_token`, `client_secret`, `password` を自動マスク
- **選択的ログ**: 特定エンドポイントのみログ出力可能

**設定例:**
```bash
# デバッグ時（開発・結合テスト環境）
IDP_LOGGING_REQUEST_RESPONSE_ENABLED=true
LOGGING_LEVEL_REQUEST_RESPONSE_LOGGING_FILTER=debug

# マスキング無効（結合テスト専用）
IDP_LOGGING_REQUEST_RESPONSE_MASK_TOKENS=false

# 本番環境（必ず無効化）
IDP_LOGGING_REQUEST_RESPONSE_ENABLED=false
```

### spring (Spring Boot 設定)

| パラメータ | 環境変数 | 説明 | デフォルト値 | 本番推奨値 |
|-----------|----------|------|-------------|-----------|
| `spring.data.redis.host` | `REDIS_HOST` | Spring Redis ホスト | `localhost` | ElastiCache エンドポイント |
| `spring.data.redis.port` | `REDIS_PORT` | Spring Redis ポート | `6379` | `6379` |
| `spring.data.redis.database` | `REDIS_SESSION_DATABASE` | Spring Session用 Redis データベース番号 | `0` | `0` |
| `spring.data.redis.password` | `REDIS_PASSWORD` | Spring Session用 Redis パスワード | `(空)` | Secrets Manager |
| `spring.data.redis.timeout` | `REDIS_TIMEOUT` | Redis接続タイムアウト | `2s` | `2s` |
| `spring.data.redis.jedis.pool.max-active` | `REDIS_JEDIS_POOL_MAX_ACTIVE` | Jedis 最大アクティブ接続数 | `32` | `64` |
| `spring.data.redis.jedis.pool.max-idle` | `REDIS_JEDIS_POOL_MAX_IDLE` | Jedis 最大アイドル接続数 | `16` | `32` |
| `spring.data.redis.jedis.pool.min-idle` | `REDIS_JEDIS_POOL_MIN_IDLE` | Jedis 最小アイドル接続数 | `0` | `8` |
| `spring.session.redis.configure-action` | `SPRING_SESSION_REDIS_CONFIGURE_ACTION` | Redis セッション設定アクション | `none` | `none` |
| `spring.session.timeout` | `SESSION_TIMEOUT` | セッションタイムアウト | `3600s` | `7200s` |
| `spring.lifecycle.timeout-per-shutdown-phase` | - | Shutdown phase タイムアウト | `30s` | `30s` |

### idp.async (非同期処理スレッドプール設定)

非同期処理（セキュリティイベント、ユーザーライフサイクルイベント、監査ログ）用のスレッドプール設定です。

#### Security Event スレッドプール
| パラメータ | 環境変数 | 説明 | デフォルト値 | 本番推奨値 |
|-----------|----------|------|-------------|-----------|
| `core-pool-size` | `SECURITY_EVENT_CORE_POOL_SIZE` | コアスレッド数 | `5` | `5` |
| `max-pool-size` | `SECURITY_EVENT_MAX_POOL_SIZE` | 最大スレッド数 | `20` | `20` |
| `queue-capacity` | `SECURITY_EVENT_QUEUE_CAPACITY` | キュー容量 | `100` | `100` |

#### User Lifecycle Event スレッドプール
| パラメータ | 環境変数 | 説明 | デフォルト値 | 本番推奨値 |
|-----------|----------|------|-------------|-----------|
| `core-pool-size` | `USER_LIFECYCLE_EVENT_CORE_POOL_SIZE` | コアスレッド数 | `5` | `5` |
| `max-pool-size` | `USER_LIFECYCLE_EVENT_MAX_POOL_SIZE` | 最大スレッド数 | `10` | `10` |
| `queue-capacity` | `USER_LIFECYCLE_EVENT_QUEUE_CAPACITY` | キュー容量 | `50` | `50` |

#### Audit Log スレッドプール
| パラメータ | 環境変数 | 説明 | デフォルト値 | 本番推奨値 |
|-----------|----------|------|-------------|-----------|
| `core-pool-size` | `AUDIT_LOG_CORE_POOL_SIZE` | コアスレッド数 | `5` | `5` |
| `max-pool-size` | `AUDIT_LOG_MAX_POOL_SIZE` | 最大スレッド数 | `10` | `10` |
| `queue-capacity` | `AUDIT_LOG_QUEUE_CAPACITY` | キュー容量 | `50` | `50` |

**設計思想:**
- **Security Event**: セキュリティイベント処理は高負荷になりやすいため、他より大きなプールサイズ
- **User Lifecycle / Audit Log**: 比較的低頻度のため、コンパクトなプールサイズ
- **キュー容量**: バーストトラフィックを吸収するためのバッファ

### server (Tomcat サーバー設定)

| パラメータ | 環境変数 | 説明 | デフォルト値 | 本番推奨値            |
|-----------|----------|------|-------------|------------------|
| `shutdown` | - | Shutdown モード | `graceful` | `graceful`（変更不可） |
| `tomcat.threads.max` | `SERVER_TOMCAT_THREADS_MAX` | 最大スレッド数 | `300` | `500`            |
| `tomcat.threads.min-spare` | `SERVER_TOMCAT_THREADS_MIN_SPARE` | 最小予備スレッド数 | `50` | `100`            |

**Note**: 以下の設定は application.yaml で固定値として設定されており、環境変数での変更はサポートされていません：
- Graceful shutdown: 有効（30秒）
- Kubernetes ヘルスチェック（Readiness/Liveness）: 有効
- 公開エンドポイント: `health,info` のみ（メトリクス有効化は [運用ガイダンス](./05-operational-guidance.md) 参照）

---

## 🔍 詳細解説

### 1. セキュリティパラメータ設定

#### API認証キー・シークレット設定
```bash
# API Key (UUID v4 形式推奨)
IDP_SERVER_API_KEY=30113151-4ee1-4f6a-a1ac-cc1be9eaf695

# API Secret (Base64エンコード推奨、最低32文字)
IDP_SERVER_API_SECRET=MTY5YjM3NmYtOTY0ZC00Nzg0LWIyOTMtOWQyNDhjMTkyNmIwCg==
```

**生成方法:**
```bash
# API Key生成 (UUID v4)
API_KEY=$(uuidgen | tr 'A-Z' 'a-z')
echo "IDP_SERVER_API_KEY=$API_KEY"

# API Secret生成 (Base64エンコード)
API_SECRET=$(uuidgen | tr 'A-Z' 'a-z' | base64)
echo "IDP_SERVER_API_SECRET=$API_SECRET"
```

**用途:**
- **管理API認証**: `/v1/admin/*` エンドポイントへのアクセス制御
- **Basic認証形式**: `Authorization: Basic base64(API_KEY:API_SECRET)`
- **テナント管理**: テナント作成、ユーザー管理、設定変更
- **監査ログアクセス**: セキュリティイベント・監査ログ取得

#### データ暗号化キー設定
```bash
# AES-256暗号化キー (32バイト = 256bit)
ENCRYPTION_KEY=JjN9c6N4STeA+g9d+TtBGp5MC3sbqOWs+S9qzJG42iY=
```

**生成方法:**
```bash
# AES-256キー生成 (32バイト、Base64エンコード)
ENCRYPTION_KEY=$(head -c 32 /dev/urandom | base64)
echo "ENCRYPTION_KEY=$ENCRYPTION_KEY"

# 検証: Base64デコード後が32バイトであること
echo $ENCRYPTION_KEY | base64 -d | wc -c
# 結果: 32
```

**用途:**
- **OAuthトークン暗号化**: アクセストークン・リフレッシュトークンのDB保存時の暗号化

#### 暗号化キーローテーション

> ⚠️ **重要**: 暗号化キーローテーション機能は現在未実装です。
>
> **暗号化キーを変更すると、既存の全トークンが無効化されます。**
> - 既存のアクセストークン・リフレッシュトークンが復号不可能になります
> - 全ユーザーが強制ログアウトされます
> - 全ユーザーが再ログインする必要があります
>
> **通常運用では暗号化キーの変更は推奨されません。**
> セキュリティインシデント（キー漏洩など）の緊急時のみ実施してください。
>
> 詳細な実装計画については [Issue #439: 暗号化キーローテーション機能の実装](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/issues/439) を参照してください

---

### 2. 本番環境での機密情報管理

**必須機密情報項目:**
- `IDP_SERVER_API_KEY` - 管理API認証キー
- `IDP_SERVER_API_SECRET` - 管理API認証シークレット
- `ENCRYPTION_KEY` - データ暗号化キー (AES-256)
- `CONTROL_PLANE_DB_WRITER_PASSWORD` - Control Plane用DB認証情報
- `DB_WRITER_PASSWORD` - アプリケーション用DB認証情報

**セキュリティ要件:**
- 平文での保存・ログ出力禁止
- アクセス制御・監査ログ記録
- 定期的なローテーション実施
- 暗号化キーは32バイト Base64エンコード必須

> 💡 **実装方法**: 組織のセキュリティポリシーに応じてSecrets管理ソリューション（AWS Secrets Manager、HashiCorp Vault、Kubernetes Secrets等）を選択・実装してください。

### 2. データベース接続プール設定の考え方

#### Control Plane用 vs アプリケーション用の分離理由
```yaml
# Control Plane用 - 小規模・安定
control-plane:
  writer:
    maximum-pool-size: 15  # Control Plane操作は頻度低、小さめ
  reader:
    maximum-pool-size: 20  # 管理画面の表示用

# アプリケーション用 - 大規模・高負荷
app:
  writer:
    maximum-pool-size: 50  # 認証処理の書き込み
  reader:
    maximum-pool-size: 80  # UserInfo、トークン検証等の読み込み
```

**設計思想:**
- **Control Plane用**: システム管理者が使用、低頻度・高信頼性重視
- **アプリケーション用**: エンドユーザーが使用、高頻度・スケーラビリティ重視

#### 接続プールサイズの算出方法
```
推奨最大接続プールサイズ = (CPU コア数 × 2) + 実効スピンドル数
```

**例: ECS タスク (4vCPU):**
- Writer: (4 × 2) + 1 = 9 → 本番推奨 50 (余裕を持った設定)
- Reader: 読み込み重視のため Writer の 1.5～2倍

### 2. Redis データベース分離とパスワード認証

#### Spring SessionとキャッシュでRedisデータベースを分離する理由

**設計思想:**
- **データ分離**: セッションデータとキャッシュデータを異なるRedisデータベースで管理
- **運用の柔軟性**: それぞれ異なるTTL、タイムアウト設定が可能
- **セキュリティ**: 本番環境（AWS ElastiCache、Azure Cache for Redis等）での認証対応

#### 使用例

**ローカル開発環境（認証なし）:**
```bash
# デフォルト設定で動作（設定不要）
# Spring Session: DB 0, 認証なし
# Cache: DB 0, 認証なし
```

**AWS ElastiCache（認証あり、DB分離）:**
```bash
# Spring Session用
export REDIS_HOST=my-elasticache.abc123.cache.amazonaws.com
export REDIS_SESSION_DATABASE=0
export REDIS_PASSWORD=my_secure_password
export REDIS_TIMEOUT=5s

# Cache用（同じRedis、異なるDB）
export REDIS_CACHE_DATABASE=1
export REDIS_CACHE_PASSWORD=my_secure_password
export REDIS_CACHE_TIMEOUT=3000
```

**Azure Cache for Redis（認証あり）:**
```bash
# Spring Session用
export REDIS_HOST=myapp.redis.cache.windows.net
export REDIS_SESSION_DATABASE=0
export REDIS_PASSWORD=primary_key_from_azure
export REDIS_TIMEOUT=2s

# Cache用
export REDIS_CACHE_DATABASE=2
export REDIS_CACHE_PASSWORD=primary_key_from_azure
export REDIS_CACHE_TIMEOUT=5000
```

#### セキュリティ考慮事項

**パスワード管理:**
- 環境変数で渡す（平文をapplication.yamlに記載しない）
- AWS Secrets Manager、HashiCorp Vaultなどのシークレット管理サービス使用推奨
- Kubernetes Secretsでの管理

**空文字列の扱い:**
```bash
# 空文字列 or 指定なし = 認証なし（ローカル開発環境）
export REDIS_PASSWORD=
export REDIS_CACHE_PASSWORD=

# パスワード指定 = 認証あり（本番環境）
export REDIS_PASSWORD=my_secure_password
export REDIS_CACHE_PASSWORD=cache_specific_password
```

### 3. Redis 設定の最適化

#### キャッシュTTL設定指針
```yaml
timeToLiveSecond: 600  # 10分
```

**設定根拠:**
- **OAuth トークン検証**: 頻繁にアクセス、中程度のTTL
- **ユーザー情報**: 変更頻度低、長めのTTL可
- **設定情報**: ほぼ静的、長めのTTL可

#### Redis 接続プール設定
```yaml
maxTotal: 100    # アプリケーション負荷に応じて調整
maxIdle: 10      # maxTotal の 10% 程度
minIdle: 5       # maxIdle の 50% 程度
```

### 4. ログレベル設定の戦略

#### 本番環境ログレベル方針
```yaml
# セキュリティ重要 → info 維持
LOGGING_LEVEL_IDP_SERVER_AUTHENTICATION_INTERACTORS=info
LOGGING_LEVEL_IDP_SERVER_SECURITY_EVENT_HOOKS=info

# ルートログレベル（監査・トラブルシューティングに必要）
LOGGING_LEVEL_ROOT=info

# 開発用詳細ログ → info に変更
LOGGING_LEVEL_IDP_SERVER_HTTP_REQUEST_EXECUTOR=info
```

**ログ量とパフォーマンスのバランス:**
- **監査要件**: 認証・認可ログは `info` 維持
- **パフォーマンス**: 大量ログは `warn` 以上
- **トラブルシューティング**: 必要最小限の `debug` ログ

### 5. セッション管理設定

#### セッションモード選択
```yaml
idp:
  session:
    mode: redis  # redis/servlet/disabled
```

**モード別推奨環境:**
- **redis**: 本番環境・マルチインスタンス (推奨)
- **servlet**: ローカル開発・単一インスタンス
- **disabled**: ステートレスAPI・JWT専用システム

#### セッションタイムアウト設計
```yaml
spring:
  session:
    timeout: 7200s  # 2時間
```

**考慮事項:**
- **セキュリティ**: 短いほど安全
- **ユーザビリティ**: 長いほど便利
- **業界標準**: 金融系 15-30分、一般系 1-2時間

---

## ⚙️ 本番環境設定チェックリスト

### Phase 1: 必須セキュリティ設定 🔒
```bash
# 1. 認証キー生成・設定
export IDP_SERVER_API_KEY=$(uuidgen | tr 'A-Z' 'a-z')
export IDP_SERVER_API_SECRET=$(uuidgen | tr 'A-Z' 'a-z' | base64)
export ENCRYPTION_KEY=$(head -c 32 /dev/urandom | base64)

# 2. データベース認証設定
export CONTROL_PLANE_DB_WRITER_PASSWORD="<strong-password>"
export DB_WRITER_PASSWORD="<strong-password>"

# 3. 基本環境設定
export DATABASE_TYPE="POSTGRESQL"
export DB_WRITER_URL="jdbc:postgresql://rds-primary:5432/idpserver"
export DB_READER_URL="jdbc:postgresql://rds-replica:5432/idpserver"
export REDIS_HOST="elasticache-cluster.xxxxx.cache.amazonaws.com"
export IDP_SESSION_MODE="redis"

# 4. Redis設定（認証あり環境）
export REDIS_PASSWORD="<redis-password>"
export REDIS_SESSION_DATABASE=0
export REDIS_CACHE_DATABASE=1
export REDIS_CACHE_PASSWORD="<redis-password>"
export REDIS_CACHE_TIMEOUT=5000
```

### Phase 2: パフォーマンス調整 ⚡
```bash
# 環境規模に応じて選択 (小規模/中規模/大規模)

# 中規模環境 (100-500 TPS) の例
export DB_WRITER_MAX_POOL_SIZE=50
export DB_READER_MAX_POOL_SIZE=80
export REDIS_MAX_TOTAL=100
export CACHE_TIME_TO_LIVE_SECOND=600
export SESSION_TIMEOUT=7200s
```

### Phase 3: 運用・監視設定 📊
```bash
# ログレベル設定（監査・トラブルシューティング用）
export LOGGING_LEVEL_ROOT=info
export LOGGING_LEVEL_IDP_SERVER_AUTHENTICATION_INTERACTORS=info
export LOGGING_LEVEL_IDP_SERVER_SECURITY_EVENT_HOOKS=info

# セッション管理
export SESSION_TIMEOUT=7200s
```

### ✅ 設定完了確認
- [ ] **API Key/Secret**: AWS Secrets Manager に保存完了
- [ ] **暗号化キー**: 32バイト Base64エンコード確認
- [ ] **DB接続**: Primary/Replica 接続テスト成功
- [ ] **Redis接続**: ElastiCache クラスター接続成功
- [ ] **Redis認証**: パスワード設定・認証テスト成功（本番環境）
- [ ] **RedisDB分離**: Spring Session (DB 0) とキャッシュ (DB 1) の分離確認
- [ ] **負荷テスト**: 想定TPS での接続プール動作確認

---

## 🔗 関連ドキュメント

- [デプロイ概要](./00-overview.md)
- [データベース設定](./03-database.md)
- [初期設定・ユーザー・ロール](./04-initial-configuration.md)
- [運用ガイダンス](./05-operational-guidance.md)