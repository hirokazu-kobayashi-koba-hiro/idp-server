# Adapter層 - Repository実装とインフラ統合

## 概要

Adapter層は、Core層のRepositoryインターフェースを実装し、実際のデータソース（PostgreSQL/Redis）やHTTP/Spring Bootとの統合を提供します。

**3つのアダプターモジュール**:
1. **idp-server-core-adapter** - Repository実装（PostgreSQL/Redis）
2. **idp-server-database** - スキーマ・マイグレーション
3. **idp-server-springboot-adapter** - Spring Boot統合・HTTP/REST API

---

## idp-server-core-adapter - Repository実装

**情報源**: `libs/idp-server-core-adapter/src/main/java/org/idp/server/core/adapters/`
**確認日**: 2025-10-12

### 責務

Core層のRepositoryインターフェースの実装。データソースへのアクセスをカプセル化。

- **PostgreSQL/MySQL対応**: Dialect切り替え
- **Redis Cache**: セッション・一時データ
- **暗号化**: 機密データの暗号化・復号化
- **ハッシュ化**: トークンのHMAC-SHA256ハッシュ

### パッケージ構成

```
libs/idp-server-core-adapter/src/main/java/org/idp/server/core/adapters/
└── datasource/
    ├── token/              # トークンRepository実装
    ├── identity/           # ユーザーRepository実装
    ├── oidc/               # クライアント設定Repository実装
    ├── grant_management/   # グラント管理Repository実装
    ├── authentication/     # 認証Repository実装
    ├── federation/         # フェデレーションRepository実装
    ├── config/             # 設定Repository実装
    ├── ciba/               # CIBARepository実装
    ├── security/           # セキュリティイベントRepository実装
    ├── verifiable_credentials/ # VC Repository実装
    ├── multi_tenancy/      # テナント・組織Repository実装
    ├── audit/              # 監査ログRepository実装
    └── cache/              # Redisキャッシュ実装
```

**情報源**: `find libs/idp-server-core-adapter/src/main/java/org/idp/server/core/adapters/datasource -type d -maxdepth 2`

### DataSource - SqlExecutor パターン

#### DataSource - Repository実装

**情報源**: [OAuthTokenCommandDataSource.java:25](../../libs/idp-server-core-adapter/src/main/java/org/idp/server/core/adapters/datasource/token/command/OAuthTokenCommandDataSource.java#L25)

```java
/**
 * Repository実装 - DataSourceパターン
 * 確認方法: 実ファイルの25-47行目
 */
public class OAuthTokenCommandDataSource implements OAuthTokenCommandRepository {

  OAuthTokenSqlExecutor executor;  // ✅ SQLExecutorに委譲
  AesCipher aesCipher;             // ✅ 暗号化
  HmacHasher hmacHasher;           // ✅ ハッシュ化

  public OAuthTokenCommandDataSource(
      OAuthTokenSqlExecutor executor, AesCipher aesCipher, HmacHasher hmacHasher) {
    this.executor = executor;
    this.aesCipher = aesCipher;
    this.hmacHasher = hmacHasher;
  }

  @Override
  public void register(Tenant tenant, OAuthToken oAuthToken) {
    // ✅ SQLExecutorに委譲（暗号化・ハッシュ化を渡す）
    executor.insert(oAuthToken, aesCipher, hmacHasher);
  }

  @Override
  public void delete(Tenant tenant, OAuthToken oAuthToken) {
    executor.delete(oAuthToken, aesCipher, hmacHasher);
  }
}
```

#### SqlExecutor - SQL実行

```java
/**
 * SQL実行 - Dialect別に実装
 */
public interface OAuthTokenSqlExecutor {
  void insert(OAuthToken oAuthToken, AesCipher aesCipher, HmacHasher hmacHasher);
  void delete(OAuthToken oAuthToken, AesCipher aesCipher, HmacHasher hmacHasher);
}

// PostgreSQL実装
public class PostgresqlExecutor implements OAuthTokenSqlExecutor {
  @Override
  public void insert(OAuthToken oAuthToken, AesCipher aesCipher, HmacHasher hmacHasher) {
    // PostgreSQL固有のSQL実行
  }
}

// MySQL実装
public class MysqlExecutor implements OAuthTokenSqlExecutor {
  @Override
  public void insert(OAuthToken oAuthToken, AesCipher aesCipher, HmacHasher hmacHasher) {
    // MySQL固有のSQL実行
  }
}
```

### 重要原則

#### ❌ データソース層でのビジネスロジック禁止

```java
// ❌ 悪い例: Repository実装でビジネス判定
public class ClientConfigurationDataSource implements ClientConfigurationQueryRepository {
  @Override
  public ClientConfiguration get(Tenant tenant, RequestedClientId clientId) {
    ClientConfiguration config = executor.selectById(clientId);

    // ❌ ビジネスロジックがデータソース層に漏れている
    if ("ORGANIZER".equals(tenant.type())) {
      config.setSpecialPermissions(true);
    }

    return config;
  }
}

// ✅ 良い例: データアクセスのみ
public class ClientConfigurationDataSource implements ClientConfigurationQueryRepository {
  @Override
  public ClientConfiguration get(Tenant tenant, RequestedClientId clientId) {
    // ✅ データ取得のみ
    return executor.selectById(clientId);
  }
}
```

**原則**: データソース層 = SELECT/INSERT/UPDATE/DELETE、ドメイン層 = 業務ルール

**情報源**: CLAUDE.md「⚠️ レイヤー責任違反の重要教訓」

### 暗号化・ハッシュ化

#### AesCipher - 暗号化

```java
// 暗号化
String encrypted = aesCipher.encrypt(plaintext);

// 復号化
String plaintext = aesCipher.decrypt(encrypted);
```

#### HmacHasher - ハッシュ化

```java
// ハッシュ生成
String hash = hmacHasher.hash(data);

// ハッシュ検証
boolean valid = hmacHasher.verify(data, expectedHash);
```

**用途**:
- **AesCipher**: 機密データ（クライアントシークレット、リフレッシュトークン等）
- **HmacHasher**: トークンの検索キー（一方向ハッシュ）

### Redis Cache実装

**情報源**: [JedisCacheStore.java:28](../../libs/idp-server-core-adapter/src/main/java/org/idp/server/core/adapters/datasource/cache/JedisCacheStore.java#L28)

#### JedisCacheStore - Redis キャッシュ

```java
/**
 * Redis Cache実装（Jedis使用）
 * 確認方法: 実ファイルの28-80行目
 */
public class JedisCacheStore implements CacheStore {
  JedisPool jedisPool;
  JsonConverter jsonConverter;
  int timeToLiveSecond;

  // ✅ キャッシュ保存
  @Override
  public <T> void put(String key, T value) {
    try (Jedis resource = jedisPool.getResource()) {
      String json = jsonConverter.write(value);
      resource.setex(key, timeToLiveSecond, json);  // TTL付き保存
    }
  }

  // ✅ キャッシュ取得
  @Override
  public <T> Optional<T> find(String key, Class<T> type) {
    try (Jedis resource = jedisPool.getResource()) {
      String json = resource.get(key);
      if (json == null) {
        return Optional.empty();
      }
      return Optional.of(jsonConverter.read(json, type));
    }
  }

  // ✅ キャッシュ削除
  @Override
  public void delete(String key) {
    try (Jedis resource = jedisPool.getResource()) {
      resource.del(key);
    }
  }
}
```

**キャッシュ対象**:
- **OAuthSession**: SSOセッション
- **AuthorizationRequest**: 認可リクエスト（一時保存）
- **AuthenticationTransaction**: 認証トランザクション
- **JWKS**: 公開鍵セット（パフォーマンス向上）

**設定例**:
```properties
cache.host=localhost
cache.port=6379
cache.max.total=100
cache.max.idle=50
cache.min.idle=10
cache.ttl.seconds=3600
```

---

## idp-server-database - スキーマ・マイグレーション

**情報源**: `libs/idp-server-database/`
**確認日**: 2025-10-12

### 責務

データベーススキーマ定義とマイグレーション（Flyway）。

- **DDL**: `CREATE TABLE`文
- **マイグレーション**: Flywayによるバージョン管理
- **初期データ**: 基本設定の投入
- **RLS**: Row Level Security（PostgreSQL）

### ディレクトリ構成

```
libs/idp-server-database/
├── postgresql/
│   ├── V1_0_0__init_lib.sql          # 初期スキーマ
│   ├── V1_0_1__add_column.sql        # マイグレーション例
│   └── operation/
│       └── app_user.sql              # アプリケーションユーザー作成
├── mysql/
│   └── (PostgreSQLと同様の構成)
└── README.md                          # PostgreSQL→MySQL DDL変換ルール
```

**情報源**: `find libs/idp-server-database -name "*.sql"`

### Flyway マイグレーション

**情報源**: [README.md](../../libs/idp-server-database/README.md)

#### マイグレーション実行

```bash
# PostgreSQL
DB_TYPE=postgresql ./gradlew flywayClean flywayMigrate

# MySQL
DB_TYPE=mysql ./gradlew flywayClean flywayMigrate

# カスタムURL
DB_TYPE=postgresql DB_URL=jdbc:postgresql://localhost:5432/custom_db ./gradlew flywayMigrate
```

#### PostgreSQL → MySQL DDL変換ルール

| PostgreSQL | MySQL | 備考 |
|-----------|-------|------|
| `BOOLEAN` | `TINYINT(1)` | 1=TRUE, 0=FALSE |
| `TIMESTAMP DEFAULT now()` | `DATETIME DEFAULT CURRENT_TIMESTAMP` | タイムスタンプデフォルト |
| `JSONB` | `JSON` | MySQL 5.7+ |
| `INET` | `VARCHAR(45)` | IPv6対応IP格納 |
| `SERIAL` | `INT AUTO_INCREMENT` | 自動増分 |
| `gen_random_uuid()` | `UUID()` | UUID生成 |
| `UUID` type | `CHAR(36)` | 文字列として格納 |
| `UNIQUE (...) WHERE ...` | Not supported | トリガーで代替 |

**情報源**: [README.md:24-46](../../libs/idp-server-database/README.md#L24-L46)

#### Docker Flyway Migrator

```bash
# Docker イメージビルド
docker build -f ./Dockerfile-flyway -t idp-flyway-migrator:latest .

# コンテナ実行
docker run --rm \
  -e DB_TYPE=postgresql \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/idpserver \
  -e DB_USER=idp_app_user \
  -e DB_PASSWORD=secret \
  idp-flyway-migrator:latest migrate
```

### 主要テーブル

```sql
-- テナント
CREATE TABLE tenant (
  id UUID PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  type VARCHAR(50) NOT NULL,
  attributes JSONB
);

-- ユーザー
CREATE TABLE idp_user (
  id UUID PRIMARY KEY,
  sub VARCHAR(255) NOT NULL,
  tenant_id UUID NOT NULL REFERENCES tenant(id)
);

-- クライアント設定
CREATE TABLE client_configuration (
  id UUID PRIMARY KEY,
  client_id VARCHAR(255) NOT NULL,
  tenant_id UUID NOT NULL REFERENCES tenant(id)
);

-- 組織
CREATE TABLE organization (
  id UUID PRIMARY KEY,
  name VARCHAR(255) NOT NULL
);

-- 組織-テナント関係
CREATE TABLE organization_tenants (
  organization_id UUID REFERENCES organization(id),
  tenant_id UUID REFERENCES tenant(id),
  PRIMARY KEY (organization_id, tenant_id)
);
```

**情報源**: `libs/idp-server-database/postgresql/V1_0_0__init_lib.sql`

### Row Level Security (RLS)

PostgreSQLでマルチテナント分離を実現。

```sql
-- RLS有効化
ALTER TABLE client_configuration ENABLE ROW LEVEL SECURITY;

-- ポリシー作成
CREATE POLICY tenant_isolation ON client_configuration
  USING (tenant_id = current_setting('app.tenant_id')::UUID);
```

**動作**:
1. TransactionManagerで`SET LOCAL app.tenant_id = 'xxx'`を実行
2. RLSポリシーが自動適用
3. テナントIDが一致する行のみアクセス可能

**情報源**: Issue #672（SQL Injection修正）、[deployment.md](../../documentation/docs/content_08_ops/ops-02-deployment.md)

---

## idp-server-springboot-adapter - Spring Boot統合

**情報源**: `libs/idp-server-springboot-adapter/`
**確認日**: 2025-10-12

### 責務

Spring Boot統合・HTTP/REST API実装。

- **Controller**: HTTP → EntryService呼び出し
- **Configuration**: Spring Bean定義
- **Exception Handler**: HTTP エラーレスポンス変換
- **Security**: Spring Security統合

### Controller パターン

**情報源**: [ClientManagementV1Api.java:37-69](../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/control_plane/restapi/management/ClientManagementV1Api.java#L37-L69)

#### 命名規則: \{Domain\}ManagementV1Api

```java
/**
 * Management API Controller
 * 確認方法: 実ファイルの37-69行目
 */
@RestController
@RequestMapping("/v1/management/tenants/{tenant-id}/clients")
public class ClientManagementV1Api implements ParameterTransformable {

  ClientManagementApi clientManagementApi;  // ✅ control-plane API

  public ClientManagementV1Api(IdpServerApplication idpServerApplication) {
    this.clientManagementApi = idpServerApplication.clientManagementApi();
  }

  @PostMapping
  public ResponseEntity<?> post(
      @AuthenticationPrincipal OperatorPrincipal operatorPrincipal,  // ✅ Spring Security統合
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,  // ✅ 型安全
      @RequestBody(required = false) Map<String, Object> body,
      @RequestParam(value = "dry_run", required = false, defaultValue = "false") boolean dryRun,
      HttpServletRequest httpServletRequest) {

    // ✅ Phase 1: RequestAttributes変換
    RequestAttributes requestAttributes = transform(httpServletRequest);

    // ✅ Phase 2: Control-Plane API呼び出し
    ClientManagementResponse response =
        clientManagementApi.create(
            tenantIdentifier,
            operatorPrincipal.getUser(),
            operatorPrincipal.getOAuthToken(),
            new ClientRegistrationRequest(body),
            requestAttributes,
            dryRun);

    // ✅ Phase 3: レスポンス生成
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(),
        httpHeaders,
        HttpStatus.valueOf(response.statusCode()));
  }
}
```

**重要ポイント**:
- ✅ **命名規則**: `{'{Domain}ManagementV1Api'}` (例: `ClientManagementV1Api`, `UserManagementV1Api`)
- ✅ **implements ParameterTransformable**: HttpServletRequest → RequestAttributes変換
- ✅ **@AuthenticationPrincipal OperatorPrincipal**: Spring Securityで認証済みオペレーター取得
- ✅ **TenantIdentifier型**: `@PathVariable("tenant-id")` で型安全なパス変数
- ✅ **Control-Plane API呼び出し**: EntryServiceではなく、control-plane定義のAPIを使用
- ❌ **ロジック禁止**: HTTP → DTO変換のみ、ビジネスロジックは一切含まない

**責務の明確な分離**:
```
Controller (V1Api)
  ↓ HTTP → RequestAttributes変換
Control-Plane API
  ↓ EntryService呼び出し（Proxyでラップ）
EntryService (UseCase層)
  ↓ Handler/Service呼び出し
Core層
```

### Configuration

```java
@Configuration
public class DataSourceConfiguration {

  @Bean
  public ClientConfigurationQueryRepository clientConfigurationQueryRepository(
      DataSource dataSource) {
    ClientConfigurationSqlExecutor executor = new PostgresqlExecutor(dataSource);
    return new ClientConfigurationQueryDataSource(executor);
  }

  @Bean
  public ClientConfigurationCommandRepository clientConfigurationCommandRepository(
      DataSource dataSource) {
    ClientConfigurationSqlExecutor executor = new PostgresqlExecutor(dataSource);
    return new ClientConfigurationCommandDataSource(executor);
  }
}
```

### Exception Handler

**情報源**: [ApiExceptionHandler.java:34](../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/ApiExceptionHandler.java#L34)

```java
/**
 * グローバル例外ハンドラー
 * 確認方法: 実ファイルの34-100行目
 */
@ControllerAdvice
public class ApiExceptionHandler {

  LoggerWrapper log = LoggerWrapper.getLogger(ApiExceptionHandler.class);

  // ✅ BadRequestException → 400
  @ExceptionHandler(BadRequestException.class)
  public ResponseEntity<?> handleException(BadRequestException exception) {
    log.warn(exception.getMessage(), exception);
    Map<String, String> response =
        Map.of("error", "invalid_request", "error_description", exception.getMessage());
    return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
  }

  // ✅ UnauthorizedException → 401
  @ExceptionHandler(UnauthorizedException.class)
  public ResponseEntity<?> handleException(UnauthorizedException exception) {
    log.warn(exception.getMessage());
    Map<String, String> response =
        Map.of("error", "invalid_request", "error_description", exception.getMessage());
    return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
  }

  // ✅ ForbiddenException → 403
  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<?> handleException(ForbiddenException exception) {
    log.warn(exception.getMessage());
    Map<String, String> response =
        Map.of("error", "invalid_request", "error_description", exception.getMessage());
    return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
  }

  // ✅ NotFoundException → 404
  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<?> handleException(NotFoundException exception) {
    log.warn(exception.getMessage());
    Map<String, String> response =
        Map.of("error", "invalid_request", "error_description", exception.getMessage());
    return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
  }

  // ✅ ConflictException → 409
  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<?> handleException(ConflictException exception) {
    log.warn(exception.getMessage());
    Map<String, String> response =
        Map.of("error", "invalid_request", "error_description", exception.getMessage());
    return new ResponseEntity<>(response, HttpStatus.CONFLICT);
  }

  // ✅ SqlDuplicateKeyException → 409
  @ExceptionHandler(SqlDuplicateKeyException.class)
  public ResponseEntity<?> handleException(SqlDuplicateKeyException exception) {
    log.warn(exception.getMessage());
    Map<String, String> response =
        Map.of("error", "duplicate_key", "error_description", exception.getMessage());
    return new ResponseEntity<>(response, HttpStatus.CONFLICT);
  }

  // ✅ HttpMethodNotSupported → 405
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<?> handleException(HttpRequestMethodNotSupportedException exception) {
    log.warn(exception.getMessage(), exception);
    Map<String, String> response =
        Map.of("error", "invalid_request", "error_description", exception.getMessage());
    return new ResponseEntity<>(response, HttpStatus.METHOD_NOT_ALLOWED);
  }
}
```

**例外 → HTTPステータスマッピング**:

| 例外 | HTTPステータス | エラーコード |
|------|--------------|-------------|
| `BadRequestException` | 400 Bad Request | `invalid_request` |
| `UnauthorizedException` | 401 Unauthorized | `invalid_request` |
| `ForbiddenException` | 403 Forbidden | `invalid_request` |
| `NotFoundException` | 404 Not Found | `invalid_request` |
| `ConflictException` | 409 Conflict | `invalid_request` |
| `SqlDuplicateKeyException` | 409 Conflict | `duplicate_key` |
| `HttpRequestMethodNotSupportedException` | 405 Method Not Allowed | `invalid_request` |

### フィルター

#### ManagementApiFilter - 管理API認証

```java
@Component
public class ManagementApiFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) {

    // 1. アクセストークン検証
    String authorization = request.getHeader("Authorization");
    OAuthToken oAuthToken = validateAccessToken(authorization);

    // 2. 権限検証
    User operator = extractOperator(oAuthToken);

    // 3. リクエスト属性に設定
    request.setAttribute("operator", operator);
    request.setAttribute("oAuthToken", oAuthToken);

    filterChain.doFilter(request, response);
  }
}
```

#### OrgManagementFilter - 組織管理API認証

組織レベルAPIの認証・認可。

#### DynamicCorsFilter - CORS動的設定

テナント固有のCORS設定を動的に適用。

**情報源**: [DynamicCorsFilter.java](../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/DynamicCorsFilter.java)

---

## まとめ

### Adapter層を理解するための5つのポイント

1. **DataSource - SqlExecutor パターン**: Repository実装を2層に分離
2. **ビジネスロジック禁止**: データソース層はデータアクセスのみ
3. **暗号化・ハッシュ化**: AesCipher（暗号化）、HmacHasher（ハッシュ化）
4. **Row Level Security**: PostgreSQLでマルチテナント分離
5. **V1Api = 変換のみ**: HTTP → RequestAttributes変換、Control-Plane APIに委譲

### 次のステップ

- [拡張機能層（CIBA, FAPI, IDA, PKCE, VC）](./ai-30-extensions.md)
- [認証・連携層（Authentication, Federation, WebAuthn）](./ai-40-authentication-federation.md)
- [通知・イベント層（Notification, Security Event）](./ai-50-notification-security-event.md)

---

## ドキュメント修正履歴

### 2025-10-12: 実装検証に基づく修正

#### 修正1: Controller パターンの実装に合わせた全面改訂 (394-464行目)

**問題**: 想像で書かれたController実装パターン、実際のアーキテクチャとの重大な乖離

**修正前**:
```java
// ❌ 誤り: 存在しないクラス名・パターン
@RestController
@RequestMapping("/v1/management")
public class ClientManagementController {  // ❌ 命名規則違反
  ClientManagementEntryService entryService;  // ❌ 直接EntryService使用は誤り

  @PostMapping("/tenants/{tenantId}/clients")
  public ResponseEntity<Map<String, Object>> create(
      @PathVariable String tenantId,  // ❌ String型（型安全性なし）
      @RequestHeader("Authorization") String authorization,  // ❌ 手動パース想定
```

**修正後**:
```java
// ✅ 実装: 実際のクラス名・パターン
@RestController
@RequestMapping("/v1/management/tenants/{tenant-id}/clients")
public class ClientManagementV1Api implements ParameterTransformable {  // ✅ 命名規則: *V1Api
  ClientManagementApi clientManagementApi;  // ✅ control-plane API使用

  @PostMapping
  public ResponseEntity<?> post(
      @AuthenticationPrincipal OperatorPrincipal operatorPrincipal,  // ✅ Spring Security統合
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,  // ✅ 型安全
```

**追加内容**:
- 命名規則: `{'{Domain}ManagementV1Api'}`
- `implements ParameterTransformable`
- `@AuthenticationPrincipal OperatorPrincipal` パターン
- `TenantIdentifier` 型安全なパス変数
- Controller → Control-Plane API → EntryService の階層構造

**検証**: [ClientManagementV1Api.java:37-69](../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/control_plane/restapi/management/ClientManagementV1Api.java#L37-L69)

#### 修正2: まとめセクションの用語修正 (621-629行目)

**修正前**:
```
5. **Controller = 変換のみ**: HTTP → DTO変換、EntryServiceに委譲
```

**修正後**:
```
5. **V1Api = 変換のみ**: HTTP → RequestAttributes変換、Control-Plane APIに委譲
```

**理由**: Controllerではなく`*V1Api`が正しい命名、EntryServiceではなくControl-Plane APIへの委譲が正確

### 修正の原則

**CLAUDE.md「想像ドキュメント作成防止」に基づく修正**:
1. **実装ファースト**: 実際のControllerファイルを確認
2. **命名規則の正確性**: `*V1Api` の徹底
3. **アーキテクチャの正確性**: Controller → Control-Plane API → EntryService の階層
4. **Spring Security統合**: `@AuthenticationPrincipal` の正しい使用

---

**情報源**:
- `libs/idp-server-core-adapter/src/main/java/`配下の実装コード
- `libs/idp-server-springboot-adapter/src/main/java/`配下の実装コード
- `libs/idp-server-database/postgresql/V1_0_0__init_lib.sql`
- CLAUDE.md「⚠️ レイヤー責任違反の重要教訓」「4層アーキテクチャ」
- [OAuthTokenCommandDataSource.java](../../libs/idp-server-core-adapter/src/main/java/org/idp/server/core/adapters/datasource/token/command/OAuthTokenCommandDataSource.java)
- [ClientManagementV1Api.java](../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/control_plane/restapi/management/ClientManagementV1Api.java)
- [ApiExceptionHandler.java](../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/ApiExceptionHandler.java)

**最終更新**: 2025-10-12
**確認方法**: `find libs/idp-server-core-adapter/src/main/java/org/idp/server/core/adapters/datasource -type d -maxdepth 2`
**レビュー実施**: 2025-10-12 - AI開発者向けドキュメント品質改善プロジェクト
