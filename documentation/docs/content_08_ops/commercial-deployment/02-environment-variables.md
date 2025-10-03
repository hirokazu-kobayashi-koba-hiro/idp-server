# 環境変数・セキュリティパラメータ設定

idp-server の商用デプロイメントにおける環境変数、セキュリティパラメータ、および機密情報管理について説明します。

## 🎯 SRE向け設定ガイド

### 最低限設定すべき必須項目 (Priority 1)

**✅ セキュリティクリティカル**
- `IDP_SERVER_API_KEY` - 管理API認証キー
- `IDP_SERVER_API_SECRET` - 管理API認証シークレット
- `ENCRYPTION_KEY` - データ暗号化キー (AES-256)
- `ADMIN_DB_WRITER_PASSWORD` - DB管理者パスワード
- `DB_WRITER_PASSWORD` - DBアプリケーションパスワード

**✅ 基本環境設定**
- `DATABASE_TYPE` - データベース種類 (`POSTGRESQL`)
- `DB_WRITER_URL` - プライマリDB接続URL
- `DB_READER_URL` - レプリカDB接続URL
- `REDIS_HOST` - Redis/ElastiCache エンドポイント

### 本番環境で調整すべき項目 (Priority 2)

**⚡ パフォーマンス設定**
- `DB_WRITER_MAX_POOL_SIZE` - 書き込み接続プール (負荷に応じて)
- `DB_READER_MAX_POOL_SIZE` - 読み込み接続プール (書き込みの1.5-2倍)
- `REDIS_MAX_TOTAL` - Redis接続プール (同時アクセス数に応じて)
- `CACHE_TIME_TO_LIVE_SECOND` - キャッシュTTL (業務要件に応じて)

**🔒 セキュリティ設定**
- `LOGGING_LEVEL_ROOT` - 本番は `warn` 推奨
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
| `adminDashboardUrl` | `ADMIN_DASHBOARD_URL` | 管理ダッシュボードURL | `http://localhost:3000` | `https://admin.your-domain.com` |
| `apiKey` | `IDP_SERVER_API_KEY` | 管理API認証キー | なし (必須) | Secrets Manager |
| `apiSecret` | `IDP_SERVER_API_SECRET` | 管理API認証シークレット | なし (必須) | Secrets Manager |
| `encryptionKey` | `ENCRYPTION_KEY` | データ暗号化キー (AES-256) | なし (必須) | Secrets Manager |
| `databaseType` | `DATABASE_TYPE` | データベース種類 | `POSTGRESQL` | `POSTGRESQL` |

### idp.time (タイムゾーン設定)

| パラメータ | 環境変数 | 説明 | デフォルト値 | 本番推奨値 |
|-----------|----------|------|-------------|-----------|
| `zone` | `TIME_ZONE` | アプリケーションタイムゾーン | `UTC` | `UTC` |

### idp.datasource.admin.postgresql (管理用PostgreSQL)

#### Writer 設定
| パラメータ | 環境変数 | 説明 | デフォルト値 | 本番推奨値 |
|-----------|----------|------|-------------|-----------|
| `url` | `DB_WRITER_URL` | 書き込み用DB接続URL | `jdbc:postgresql://localhost:5432/idpserver` | RDS Primary エンドポイント |
| `username` | `ADMIN_DB_WRITER_USER_NAME` | 管理用DBユーザー | `idpserver` | `idp_admin` |
| `password` | `ADMIN_DB_WRITER_PASSWORD` | 管理用DBパスワード | `idpserver` | Secrets Manager |
| `connection-timeout` | `ADMIN_DB_WRITER_TIMEOUT` | 接続タイムアウト (ms) | `2000` | `3000` |
| `maximum-pool-size` | `ADMIN_DB_WRITER_MAX_POOL_SIZE` | 最大接続プールサイズ | `10` | `15` |
| `minimum-idle` | `ADMIN_DB_WRITER_MIN_IDLE` | 最小アイドル接続数 | `5` | `5` |

#### Reader 設定
| パラメータ | 環境変数 | 説明 | デフォルト値 | 本番推奨値 |
|-----------|----------|------|-------------|-----------|
| `url` | `DB_READER_URL` | 読み込み用DB接続URL | `jdbc:postgresql://localhost:5433/idpserver` | RDS Replica エンドポイント |
| `username` | `ADMIN_DB_READER_USER_NAME` | 管理用読み込み専用DBユーザー | `idpserver` | `idp_admin_ro` |
| `password` | `ADMIN_DB_READER_PASSWORD` | 管理用読み込み専用DBパスワード | `idpserver` | Secrets Manager |
| `connection-timeout` | `ADMIN_DB_READER_TIMEOUT` | 接続タイムアウト (ms) | `2000` | `3000` |
| `maximum-pool-size` | `ADMIN_DB_READER_MAX_POOL_SIZE` | 最大接続プールサイズ | `10` | `20` |
| `minimum-idle` | `ADMIN_DB_READER_MIN_IDLE` | 最小アイドル接続数 | `5` | `8` |

### idp.datasource.app.postgresql (アプリケーション用PostgreSQL)

#### Writer 設定
| パラメータ | 環境変数 | 説明 | デフォルト値 | 本番推奨値 |
|-----------|----------|------|-------------|-----------|
| `url` | `DB_WRITER_URL` | 書き込み用DB接続URL | `jdbc:postgresql://localhost:5432/idpserver` | RDS Primary エンドポイント |
| `username` | `DB_WRITER_USER_NAME` | アプリ用DBユーザー | `idp_app_user` | `idp_app_user` |
| `password` | `DB_WRITER_PASSWORD` | アプリ用DBパスワード | `idp_app_user` | Secrets Manager |
| `connection-timeout` | `DB_WRITER_TIMEOUT` | 接続タイムアウト (ms) | `2000` | `2000` |
| `maximum-pool-size` | `DB_WRITER_MAX_POOL_SIZE` | 最大接続プールサイズ | `30` | `50` |
| `minimum-idle` | `DB_WRITER_MIN_IDLE` | 最小アイドル接続数 | `10` | `15` |

#### Reader 設定
| パラメータ | 環境変数 | 説明 | デフォルト値 | 本番推奨値 |
|-----------|----------|------|-------------|-----------|
| `url` | `DB_READER_URL` | 読み込み用DB接続URL | `jdbc:postgresql://localhost:5433/idpserver` | RDS Replica エンドポイント |
| `username` | `DB_READER_USER_NAME` | アプリ用読み込み専用DBユーザー | `idp_app_user` | `idp_app_user_ro` |
| `password` | `DB_READER_PASSWORD` | アプリ用読み込み専用DBパスワード | `idp_app_user` | Secrets Manager |
| `connection-timeout` | `DB_READER_TIMEOUT` | 接続タイムアウト (ms) | `2000` | `2000` |
| `maximum-pool-size` | `DB_READER_MAX_POOL_SIZE` | 最大接続プールサイズ | `30` | `80` |
| `minimum-idle` | `DB_READER_MIN_IDLE` | 最小アイドル接続数 | `10` | `25` |

### idp.cache (Redis キャッシュ設定)

| パラメータ | 環境変数 | 説明 | デフォルト値 | 本番推奨値 |
|-----------|----------|------|-------------|-----------|
| `enabled` | `CACHE_ENABLE` | キャッシュ有効化 | `true` | `true` |
| `timeToLiveSecond` | `CACHE_TIME_TO_LIVE_SECOND` | キャッシュTTL (秒) | `300` | `600` |
| `host` | `REDIS_HOST` | Redis ホスト | `localhost` | ElastiCache エンドポイント |
| `port` | `REDIS_PORT` | Redis ポート | `6379` | `6379` |
| `maxTotal` | `REDIS_MAX_TOTAL` | 最大接続数 | `20` | `100` |
| `maxIdle` | `REDIS_MAX_IDLE` | 最大アイドル接続数 | `3` | `10` |
| `minIdle` | `REDIS_MIN_IDLE` | 最小アイドル接続数 | `2` | `5` |

### logging.level (ログレベル設定)

| パラメータ | 環境変数 | 説明 | デフォルト値 | 本番推奨値 |
|-----------|----------|------|-------------|-----------|
| `root` | `LOGGING_LEVEL_ROOT` | ルートログレベル | `info` | `warn` |
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

### spring (Spring Boot 設定)

| パラメータ | 環境変数 | 説明 | デフォルト値 | 本番推奨値 |
|-----------|----------|------|-------------|-----------|
| `spring.data.redis.host` | `REDIS_HOST` | Spring Redis ホスト | `localhost` | ElastiCache エンドポイント |
| `spring.data.redis.port` | `REDIS_PORT` | Spring Redis ポート | `6379` | `6379` |
| `spring.session.redis.configure-action` | - | Redis セッション設定アクション | `none` | `none` |
| `spring.session.timeout` | `SESSION_TIMEOUT` | セッションタイムアウト | `3600s` | `7200s` |

### server (Tomcat サーバー設定)

| パラメータ | 設定ファイル | 説明 | デフォルト値 | 本番推奨値 |
|-----------|------------|------|-------------|-----------|
| `server.tomcat.threads.max` | application-prod.yaml | 最大スレッド数 | `300` | `500` |
| `server.tomcat.threads.min-spare` | application-prod.yaml | 最小予備スレッド数 | `50` | `100` |

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
- **PII データ暗号化**: 個人識別情報のフィールドレベル暗号化
- **機密属性暗号化**: email、phone_number、address等
- **Verified Claims暗号化**: eKYC結果等の機密データ
- **セッション暗号化**: Redis保存時の暗号化

**重要事項:**
- **キーローテーション**: 定期的な暗号化キー更新が必要
- **過去キー保持**: 過去の暗号化データ復号のため全履歴保持必要
- **ゼロダウンタイム更新**: アプリケーション停止なしでのキー更新対応

#### 暗号化キーローテーション戦略

> ⚠️ **重要**: 暗号化キーローテーション機能は現在未実装です
> 詳細な実装計画と仕様については [Issue #439: 暗号化キーローテーション機能の実装](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/issues/439) を参照してください

**現在の制限事項:**
- 暗号化キーは手動更新のみ対応
- 過去キーでの復号化は未サポート
- 自動キーローテーション機能なし

**暫定的な運用指針:**
1. **キー更新頻度**: 年1回以上の手動更新
2. **緊急時対応**: セキュリティインシデント時の即座更新
3. **バックアップ**: 暗号化キーの安全な複製保存
4. **アクセス制御**: Secrets Manager での厳格な権限管理

**将来実装予定の機能 (Issue #439):**
- マルチキーバージョン管理
- 過去キーでの段階的復号化
- 自動データ再暗号化
- キーローテーション API
- 監査ログ・アクセス追跡

#### 本番環境での機密情報管理

**必須機密情報項目:**
- `IDP_SERVER_API_KEY` - 管理API認証キー
- `IDP_SERVER_API_SECRET` - 管理API認証シークレット
- `ENCRYPTION_KEY` - データ暗号化キー (AES-256)
- `ADMIN_DB_WRITER_PASSWORD` - 管理DB認証情報
- `DB_WRITER_PASSWORD` - アプリケーションDB認証情報

**セキュリティ要件:**
- 平文での保存・ログ出力禁止
- アクセス制御・監査ログ記録
- 定期的なローテーション実施
- 暗号化キーは32バイト Base64エンコード必須

> 💡 **実装方法**: 組織のセキュリティポリシーに応じてSecrets管理ソリューション（AWS Secrets Manager、HashiCorp Vault、Kubernetes Secrets等）を選択・実装してください。

### 2. データベース接続プール設定の考え方

#### 管理用 vs アプリケーション用の分離理由
```yaml
# 管理用 (admin) - 小規模・安定
admin:
  postgresql:
    writer:
      maximum-pool-size: 15  # 管理操作は頻度低、小さめ
    reader:
      maximum-pool-size: 20  # 管理画面の表示用

# アプリケーション用 (app) - 大規模・高負荷
app:
  postgresql:
    writer:
      maximum-pool-size: 50  # 認証処理の書き込み
    reader:
      maximum-pool-size: 80  # UserInfo、トークン検証等の読み込み
```

**設計思想:**
- **管理用**: システム管理者が使用、低頻度・高信頼性重視
- **アプリケーション用**: エンドユーザーが使用、高頻度・スケーラビリティ重視

#### 接続プールサイズの算出方法
```
推奨最大接続プールサイズ = (CPU コア数 × 2) + 実効スピンドル数
```

**例: ECS タスク (4vCPU):**
- Writer: (4 × 2) + 1 = 9 → 本番推奨 50 (余裕を持った設定)
- Reader: 読み込み重視のため Writer の 1.5～2倍

### 2. Redis 設定の最適化

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

### 3. ログレベル設定の戦略

#### 本番環境ログレベル方針
```yaml
# セキュリティ重要 → info 維持
LOGGING_LEVEL_IDP_SERVER_AUTHENTICATION_INTERACTORS=info
LOGGING_LEVEL_IDP_SERVER_SECURITY_EVENT_HOOKS=info

# パフォーマンス影響大 → warn に変更
LOGGING_LEVEL_ROOT=warn

# 開発用詳細ログ → info に変更
LOGGING_LEVEL_IDP_SERVER_HTTP_REQUEST_EXECUTOR=info
```

**ログ量とパフォーマンスのバランス:**
- **監査要件**: 認証・認可ログは `info` 維持
- **パフォーマンス**: 大量ログは `warn` 以上
- **トラブルシューティング**: 必要最小限の `debug` ログ

### 4. セッション管理設定

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

### 5. 本番環境向け application-prod.yaml

#### パフォーマンス最適化
```yaml
server:
  tomcat:
    threads:
      max: 500           # 高負荷対応
      min-spare: 100     # 常時待機スレッド
    connection-timeout: 5000  # ネットワーク遅延考慮
    max-connections: 10000    # 同時接続上限

  compression:
    enabled: true        # レスポンス圧縮
    mime-types:
      - application/json # API レスポンス圧縮
```

#### 監視・メトリクス有効化
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized  # セキュリティ考慮
      probes:
        enabled: true    # Kubernetes ヘルスチェック対応
```

---

## ⚙️ 本番環境設定チェックリスト

### Phase 1: 必須セキュリティ設定 🔒
```bash
# 1. 認証キー生成・設定
export IDP_SERVER_API_KEY=$(uuidgen | tr 'A-Z' 'a-z')
export IDP_SERVER_API_SECRET=$(uuidgen | tr 'A-Z' 'a-z' | base64)
export ENCRYPTION_KEY=$(head -c 32 /dev/urandom | base64)

# 2. データベース認証設定
export ADMIN_DB_WRITER_PASSWORD="<strong-password>"
export DB_WRITER_PASSWORD="<strong-password>"

# 3. 基本環境設定
export DATABASE_TYPE="POSTGRESQL"
export DB_WRITER_URL="jdbc:postgresql://rds-primary:5432/idpserver"
export DB_READER_URL="jdbc:postgresql://rds-replica:5432/idpserver"
export REDIS_HOST="elasticache-cluster.xxxxx.cache.amazonaws.com"
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
# ログレベル最適化
export LOGGING_LEVEL_ROOT=warn
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
- [ ] **負荷テスト**: 想定TPS での接続プール動作確認

---

## 🔗 関連ドキュメント

- [デプロイ概要](./00-overview.md)
- [データベース・キャッシュ設定](./03-database-cache-configuration.md)
- [初期設定・ユーザー・ロール](./04-initial-configuration.md)
- [検証・テストチェックリスト](./05-verification-checklist.md)
- [運用ガイダンス](./06-operational-guidance.md)