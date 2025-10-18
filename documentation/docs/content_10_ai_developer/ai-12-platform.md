# idp-server-platform - プラットフォーム基盤

## モジュール概要

**情報源**: `libs/idp-server-platform/src/main/java/org/idp/server/platform/`
**確認日**: 2025-10-12

### 責務

idp-server全体を支える基盤機能群。マルチテナント、セッション管理、ログ、トランザクション、暗号化、JOSE(JWT)等の横断的関心事を提供。

### 依存関係

```
idp-server-core → idp-server-platform
idp-server-use-cases → idp-server-platform
idp-server-*-adapter → idp-server-platform
```

すべての上位モジュールが依存する基盤レイヤー。

## パッケージ構成

**情報源**: `find libs/idp-server-platform/src/main/java/org/idp/server/platform -type d -maxdepth 1`

### 🔷 マルチテナント (`multi_tenancy/`)

| サブパッケージ | 責務 |
|------------|------|
| `tenant/` | テナント管理・識別・属性 |
| `organization/` | 組織管理・メンバー管理 |

### 🔐 セキュリティ・暗号化

| パッケージ | 責務 |
|----------|------|
| `crypto/` | 暗号化・復号化 |
| `hash/` | ハッシュ生成（SHA-256等） |
| `jose/` | JWT/JWS/JWE/JWK処理 |
| `x509/` | X.509証明書管理 |
| `random/` | セキュアランダム生成 |
| `uuid/` | UUID生成 |
| `base64/` | Base64エンコード・デコード |

### 🔌 インフラ統合

| パッケージ | 責務 |
|----------|------|
| `datasource/` | データソース管理・トランザクション |
| `http/` | HTTP クライアント |
| `notification/` | 通知基盤（Email/Push） |
| `proxy/` | プロキシ設定 |

### 🛠️ プラットフォーム機能

| パッケージ | 責務 |
|----------|------|
| `audit/` | 監査ログ |
| `log/` | ロギング |
| `plugin/` | プラグインローダー |
| `dependency/` | DI（Dependency Injection） |
| `configuration/` | 設定管理 |
| `condition/` | 条件評価 |
| `exception/` | 例外ハンドリング |

### 📦 ユーティリティ

| パッケージ | 責務 |
|----------|------|
| `json/` | JSON シリアライズ・デシリアライズ |
| `mapper/` | オブジェクトマッピング |
| `type/` | 型ユーティリティ |
| `date/` | 日時処理 |
| `resource/` | リソース管理 |
| `oauth/` | OAuth共通型 |
| `security/` | セキュリティユーティリティ |

## マルチテナント実装

### Tenant - テナントドメインモデル

**情報源**: `libs/idp-server-platform/src/main/java/org/idp/server/platform/multi_tenancy/tenant/`

#### TenantIdentifier - テナント識別子

```java
// 値オブジェクトパターン
public class TenantIdentifier {
  String value;

  public TenantIdentifier(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  // equals/hashCode実装（値による等価性）
}
```

**重要**: `String`ではなく`TenantIdentifier`型を使用することで型安全性を担保。

#### TenantAttributes - テナント固有設定

**情報源**: [TenantAttributes.java:25](../../libs/idp-server-platform/src/main/java/org/idp/server/platform/multi_tenancy/tenant/TenantAttributes.java#L25)

```java
/**
 * テナント固有の設定値を格納
 * 確認方法: 実ファイルの25-84行目
 */
public class TenantAttributes {
  Map<String, Object> values;

  // ✅ 推奨パターン: デフォルト値を指定してOptional取得
  public String optValueAsString(String key, String defaultValue) {
    if (containsKey(key)) {
      return (String) values.get(key);
    }
    return defaultValue;
  }

  public boolean optValueAsBoolean(String key, boolean defaultValue) {
    if (values == null || values.isEmpty() || !containsKey(key)) {
      return defaultValue;
    }
    return (boolean) values.get(key);
  }

  public List<String> optValueAsStringList(String key, List<String> defaultValue) {
    if (values == null || values.isEmpty() || !containsKey(key)) {
      return defaultValue;
    }
    return (List<String>) values.get(key);
  }

  // デフォルト値なしの取得（存在確認済みの場合）
  public String getValueAsString(String key) {
    return (String) values.get(key);
  }

  public boolean containsKey(String key) {
    if (values == null || values.isEmpty()) {
      return false;
    }
    return values.containsKey(key);
  }
}
```

**使用例**:

```java
// Core層での使用
TenantAttributes attributes = tenant.attributes();

// PKCE有効化チェック
boolean enablePKCE = attributes.optValueAsBoolean("oauth.pkce.enabled", false);

// カスタムクレームキー
String customClaimKey = attributes.optValueAsString("token.custom_claim_key", "");

// リスト取得（許可スコープ等）
List<String> allowedScopes = attributes.optValueAsStringList(
    "oauth.allowed_scopes",
    List.of("openid", "profile")
);
```

**情報源**: CLAUDE.md「設定: TenantAttributes.optValueAsBoolean(key, default) パターン」

#### Tenant - テナント集約ルート

```java
public class Tenant {
  TenantIdentifier identifier;
  TenantName name;
  TenantType type;
  TenantAttributes attributes;
  TenantFeatures features;

  // ✅ 値オブジェクトアクセサー
  public TenantIdentifier identifier() { return identifier; }
  public String identifierValue() { return identifier.value(); }

  // ✅ 属性アクセス
  public TenantAttributes attributes() { return attributes; }

  // ✅ ビジネスロジック
  public boolean isOrganizer() {
    return type.isOrganizer();
  }

  public boolean hasFeature(String featureName) {
    return features.has(featureName);
  }
}
```

### Organization - 組織ドメインモデル

**情報源**: [Organization.java:22](../../libs/idp-server-platform/src/main/java/org/idp/server/platform/multi_tenancy/organization/Organization.java#L22)

```java
/**
 * 組織 = 複数テナントをグループ化する上位概念
 * 確認方法: 実ファイルの22-80行目
 */
public class Organization {
  OrganizationIdentifier identifier;
  OrganizationName name;
  OrganizationDescription description;
  AssignedTenants assignedTenants; // 組織に割り当てられたテナント一覧

  public Organization updateWithTenant(AssignedTenant assignedTenant) {
    AssignedTenants addedTenants = assignedTenants.add(assignedTenant);
    // ✅ Immutableパターン: 新しいインスタンスを返す
    return new Organization(identifier, name, description, addedTenants);
  }

  public boolean hasAssignedTenants() {
    return assignedTenants != null && assignedTenants.exists();
  }

  // ✅ toMap(): スネークケースでシリアライズ
  public HashMap<String, Object> toMap() {
    HashMap<String, Object> result = new HashMap<>();
    result.put("id", identifier.value());
    result.put("name", name.value());
    result.put("description", description.value());
    result.put("assigned_tenants", assignedTenants.toMapList());
    return result;
  }
}
```

**重要**: 組織レベルAPIでは、`OrganizationIdentifier` → `TenantIdentifier`の順で引数を渡す。

### OrganizationRepository - 組織リポジトリ

**情報源**: `libs/idp-server-platform/src/main/java/org/idp/server/platform/multi_tenancy/organization/OrganizationRepository.java`

```java
public interface OrganizationRepository {

  // ✅ 重要: OrganizationRepositoryのみTenant第一引数ではない
  void register(Organization organization);

  void update(Organization organization);

  Organization get(OrganizationIdentifier identifier);

  List<Organization> findList(OrganizationQueries queries);
}
```

**注意**: `findMember()` や `findAssignment()` などのメソッドは実装されていません。
組織メンバーや割り当てテナントの検索は、取得した `Organization` オブジェクトから行います：

```java
// 組織の取得
Organization org = organizationRepository.get(orgId);

// 割り当てテナント一覧
AssignedTenants tenants = org.assignedTenants();
List<TenantIdentifier> tenantIds = tenants.tenantIdentifiers();

// 組織タイプ（ORGANIZER）のテナント検索
AssignedTenant orgTenant = org.findOrgTenant();  // Organization.java:92
```

**例外ルール**:
- ❗ **OrganizationRepositoryのみ例外**: 組織はテナントより上位概念のため、Tenant第一引数ルールの対象外
- ✅ **その他の全Repository**: Tenant第一引数必須

**情報源**: CLAUDE.md「Repository: 全メソッドで Tenant が第一引数（マルチテナント分離）。OrganizationRepositoryは除く。」

## Repository パターン詳細

### Query / Command 分離 (CQRS)

```java
// ✅ Query: 読み取り専用
public interface TenantQueryRepository {
  Tenant get(TenantIdentifier identifier);
  Tenant find(TenantIdentifier identifier);
  List<Tenant> findList(int limit, int offset);
  long count();
}

// ✅ Command: 書き込み専用
public interface TenantCommandRepository {
  void register(Tenant tenant);
  void update(Tenant tenant);
  void delete(TenantIdentifier identifier);
}
```

### get() vs find() 命名規則

```java
// ✅ get(): 必須存在 - データがない場合は例外スロー
Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
// → データがない → TenantNotFoundException

// ✅ find(): 任意存在 - データがない場合はnull/空を返却
Tenant tenant = tenantQueryRepository.find(tenantIdentifier);
// → データがない → null

// ✅ findList(): リスト取得
List<Tenant> tenants = tenantQueryRepository.findList(10, 0);

// ✅ findTotalCount(): カウント
long total = tenantQueryRepository.findTotalCount(queries);
```

**情報源**: CLAUDE.md「命名: get()必須存在, find()任意存在, is/has/can判定メソッド」

## ログ・監査ログ

### LoggerWrapper - ロギング

```java
public class SomeHandler {
  LoggerWrapper log = LoggerWrapper.getLogger(SomeHandler.class);

  public void handle() {
    log.info("Processing authorization request");
    log.debug("Request parameters: {}", params);
    log.error("Failed to process request", exception);
  }
}
```

### AuditLog - 監査ログ

```java
// 監査ログ記録
AuditLog auditLog = AuditLog.builder()
    .tenantIdentifier(tenant.identifier())
    .action("CREATE_CLIENT")
    .subject(user.sub())
    .resource("client_configuration")
    .resourceId(clientId.value())
    .timestamp(Instant.now())
    .build();

auditLogWriter.write(auditLog);
```

## JOSE (JWT/JWS/JWE/JWK)

**情報源**: `libs/idp-server-platform/src/main/java/org/idp/server/platform/jose/`

platformモジュールは、Nimbus JOSE + JWTライブラリのラッパーを提供。JoseHandlerが統合ハンドラーとして機能。

### JoseHandler - 統合ハンドラー

**情報源**: [JoseHandler.java:23-40](../../libs/idp-server-platform/src/main/java/org/idp/server/platform/jose/JoseHandler.java#L23-L40)

```java
// Plain JWT / JWS / JWE を自動判定して処理
JoseHandler joseHandler = new JoseHandler();

JoseContext context = joseHandler.handle(
    joseString,    // JWT/JWS/JWE文字列
    publicJwks,    // 公開鍵（JWK Set）
    privateJwks,   // 秘密鍵（JWK Set）
    secret         // 共通鍵（HMAC用）
);

// コンテキストからトークン情報取得
JsonWebTokenClaims claims = context.claims();
```

### JsonWebSignature (JWS) - 署名付きJWT

**情報源**: [JsonWebSignature.java:28-100](../../libs/idp-server-platform/src/main/java/org/idp/server/platform/jose/JsonWebSignature.java#L28-L100)

```java
// JWSのパース
JsonWebSignature jws = JsonWebSignature.parse(jwsString);

// クレーム取得
JsonWebTokenClaims claims = jws.claims();
String subject = claims.subject();
String issuer = claims.issuer();

// ヘッダー情報
String keyId = jws.keyId();
String algorithm = jws.algorithm();
boolean isSymmetric = jws.isSymmetricType(); // HS256/HS384/HS512判定

// 署名検証
JsonWebSignatureVerifier verifier = new JsonWebSignatureVerifier(
    jws.header(),
    publicKey
);
verifier.verify(jws);  // 検証失敗時は JoseInvalidException
```

### JsonWebToken (Plain JWT)

**情報源**: [JsonWebToken.java:24-62](../../libs/idp-server-platform/src/main/java/org/idp/server/platform/jose/JsonWebToken.java#L24-L62)

```java
// Plain JWT（署名なし）のパース
JsonWebToken jwt = JsonWebToken.parse(jwtString);

// クレーム取得
JsonWebTokenClaims claims = jwt.claims();

// シリアライズ
String serialized = jwt.serialize();
```

## HTTP クライアント

**情報源**: [HttpRequestExecutor.java:36-165](../../libs/idp-server-platform/src/main/java/org/idp/server/platform/http/HttpRequestExecutor.java#L36-L165)

### HttpRequestExecutor - HTTP実行エンジン

platformモジュールは、Java標準の `java.net.http.HttpClient` をベースにした高機能HTTP実行エンジンを提供。

**主要機能**:
- OAuth 2.0自動認証
- リトライメカニズム（エクスポネンシャルバックオフ）
- Idempotencyキー管理
- ネットワークエラーのHTTPステータスコードマッピング
- 設定ベースのリクエスト構築

### 基本的な使用例

```java
// HttpClientとOAuthリゾルバーの初期化
HttpClient httpClient = HttpClient.newHttpClient();
OAuthAuthorizationResolvers oauthResolvers = new OAuthAuthorizationResolvers();

// HttpRequestExecutorの作成
HttpRequestExecutor executor = new HttpRequestExecutor(httpClient, oauthResolvers);

// 標準のjava.net.http.HttpRequestを使用
HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("https://api.example.com/data"))
    .header("Content-Type", "application/json")
    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
    .build();

// 実行
HttpRequestResult result = executor.execute(request);

if (result.statusCode() == 200) {
  String responseBody = result.body();
}
```

### リトライ機能付き実行

```java
// リトライ設定
HttpRetryConfiguration retryConfig = HttpRetryConfiguration.builder()
    .maxRetries(3)
    .retryableStatusCodes(List.of(502, 503, 504))
    .backoffDelays(List.of(
        Duration.ofSeconds(1),
        Duration.ofSeconds(2),
        Duration.ofSeconds(4)
    ))
    .idempotencyRequired(true)  // Idempotency-Keyヘッダー自動付与
    .build();

// リトライ実行
HttpRequestResult result = executor.executeWithRetry(request, retryConfig);
```

### OAuth認証付き実行

```java
// OAuth設定
OAuthAuthorizationConfiguration oauthConfig = // ... OAuth設定

// OAuth + リトライ
HttpRequestResult result = executor.executeWithRetry(
    request,
    oauthConfig,
    retryConfig
);
```

### 設定ベースのリクエスト実行

```java
// HttpRequestExecutionConfigを使った動的リクエスト構築
HttpRequestExecutionConfig config = HttpRequestExecutionConfig.builder()
    .url("https://api.example.com/users/{user_id}")
    .method(HttpMethod.POST)
    .authType(HttpRequestAuthType.OAUTH2)
    .oauthAuthorization(oauthConfig)
    .retryConfiguration(retryConfig)
    .build();

// リクエストパラメータ（URL埋め込み・ヘッダー・ボディに動的マッピング）
Map<String, Object> params = Map.of(
    "user_id", "12345",
    "name", "John Doe"
);

HttpRequestResult result = executor.execute(config, params);
```

## Datasource・トランザクション・Proxy

**情報源**: `libs/idp-server-platform/src/main/java/org/idp/server/platform/datasource/`, `libs/idp-server-platform/src/main/java/org/idp/server/platform/proxy/`

platformモジュールは、**アノテーション駆動のトランザクション管理**を提供。Dynamic Proxyによる自動トランザクション制御。

### アーキテクチャ概要

```
EntryService (Interface)
    ↓
TenantAwareEntryServiceProxy (Dynamic Proxy)
    ↓ @Transaction アノテーション検出
    ↓ TransactionManager 自動呼び出し
    ↓ PostgreSQL RLS 自動設定
    ↓
EntryService (実装)
    ↓
Repository → SqlExecutor → TransactionManager.getConnection()
```

### @Transaction アノテーション

**情報源**: [Transaction.java:24-28](../../libs/idp-server-platform/src/main/java/org/idp/server/platform/datasource/Transaction.java#L24-L28)

```java
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Transaction {
  boolean readOnly() default false;
}
```

**使用例**:

```java
// ✅ クラスレベル: 全メソッドにトランザクション適用
@Transaction
public class UserManagementEntryService implements UserManagementApi {
  // 全メソッドが自動的にトランザクション管理される
}

// ✅ メソッドレベル: 特定メソッドのみ
public class TenantManagementEntryService implements TenantManagementApi {

  @Transaction(readOnly = true)
  public TenantResponse get(TenantIdentifier tenantIdentifier) {
    // 読み取り専用トランザクション
  }

  @Transaction  // readOnly = false (デフォルト)
  public TenantResponse register(TenantRegistrationRequest request) {
    // 書き込みトランザクション
  }
}
```

### TenantAwareEntryServiceProxy - Dynamic Proxy

**情報源**: [TenantAwareEntryServiceProxy.java:29-181](../../libs/idp-server-platform/src/main/java/org/idp/server/platform/proxy/TenantAwareEntryServiceProxy.java#L29-L181)

Dynamic Proxyが自動的に以下を実行：

1. **アノテーション検出**: `@Transaction` の有無・readOnly判定
2. **TenantIdentifier解決**: メソッド引数から自動抽出
3. **トランザクション開始**: `TransactionManager.beginTransaction()` または `createConnection()`
4. **RLS設定**: PostgreSQLの場合、`set_config('app.tenant_id', ?, true)` 実行
5. **ログコンテキスト**: `TenantLoggingContext` 設定
6. **実行**: 実際のメソッド実行
7. **コミット/ロールバック**: 成功時commit、例外時rollback
8. **クリーンアップ**: 接続クローズ、コンテキストクリア

**Proxy作成**:

```java
// EntryServiceをProxyでラップ
UserManagementApi userManagementApi = TenantAwareEntryServiceProxy.createProxy(
    new UserManagementEntryService(...),
    UserManagementApi.class,
    applicationDatabaseTypeProvider
);

// この後のAPI呼び出しは全て自動的にトランザクション管理される
userManagementApi.register(tenantIdentifier, request);  // 自動トランザクション
```

### TransactionManager - ThreadLocal管理

**情報源**: [TransactionManager.java:25-149](../../libs/idp-server-platform/src/main/java/org/idp/server/platform/datasource/TransactionManager.java#L25-L149)

**重要**: 通常、開発者が直接TransactionManagerを呼び出すことはない。Proxyが自動的に呼び出す。

```java
// ✅ Proxyが自動的に実行（開発者は意識不要）
TransactionManager.beginTransaction(DatabaseType.POSTGRESQL, tenantIdentifier);
// → set_config('app.tenant_id', ?, true) 自動実行
// → メソッド実行
// → commit or rollback
// → closeConnection()

// ❌ 直接呼び出しは非推奨（Proxyを使用すべき）
TransactionManager.beginTransaction(...);  // 通常は使わない
```

**ThreadLocal管理の仕組み**:

```java
private static final ThreadLocal<Connection> connectionHolder = new ThreadLocal<>();

public static Connection getConnection() {
  Connection conn = connectionHolder.get();
  if (conn == null) {
    throw new SqlRuntimeException("No active transaction");
  }
  return conn;
}
```

### SqlExecutor - SQL実行ヘルパー

**情報源**: [SqlExecutor.java:23-83](../../libs/idp-server-platform/src/main/java/org/idp/server/platform/datasource/SqlExecutor.java#L23-L83)

Repository実装で使用するSQL実行ユーティリティ。

```java
public class UserRepositoryImpl implements UserQueryRepository {

  public User get(Tenant tenant, String userSub) {
    SqlExecutor executor = new SqlExecutor();  // TransactionManager.getConnection()を内部で使用

    String sql = "SELECT * FROM idp_user WHERE tenant_id = ? AND sub = ?";
    Map<String, String> row = executor.selectOne(sql, List.of(
        tenant.identifierValue(),
        userSub
    ));

    return mapToUser(row);
  }

  public List<User> findList(Tenant tenant, int limit, int offset) {
    SqlExecutor executor = new SqlExecutor();

    String sql = "SELECT * FROM idp_user WHERE tenant_id = ? LIMIT ? OFFSET ?";
    List<Map<String, String>> rows = executor.selectList(sql, List.of(
        tenant.identifierValue(),
        limit,
        offset
    ));

    return rows.stream().map(this::mapToUser).toList();
  }
}
```

**自動例外マッピング**:

```java
try {
  executor.selectOne(sql, params);
} catch (SQLException exception) {
  switch (SqlErrorClassifier.classify(exception)) {
    case UNIQUE_VIOLATION -> throw new SqlDuplicateKeyException(...);
    case NOT_NULL_VIOLATION, CHECK_VIOLATION -> throw new SqlBadRequestException(...);
    default -> throw new SqlRuntimeException(...);
  }
}
```

### Row Level Security (RLS) の自動設定

**情報源**: [TransactionManager.java:128-167](../../libs/idp-server-platform/src/main/java/org/idp/server/platform/datasource/TransactionManager.java#L128-L167)

PostgreSQL使用時、Proxyが自動的にRLSを設定：

```sql
-- Proxyが自動実行（開発者は意識不要）
SELECT set_config('app.tenant_id', ?, true);
--                                    ↑ is_local=true（トランザクション終了時に自動クリア）
```

**Issue #672対策**: プレースホルダー使用でSQLインジェクション防止

```java
// ✅ 安全な実装（Issue #672で修正済み）
private static void setTenantId(Connection conn, TenantIdentifier tenantIdentifier) {
  try (PreparedStatement stmt = conn.prepareStatement(
      "SELECT set_config('app.tenant_id', ?, true)")) {
    stmt.setString(1, tenantIdentifier.value());
    stmt.execute();
  }
}
```

### 実装パターンまとめ

**開発者がすべきこと**:

1. ✅ EntryServiceに `@Transaction` アノテーション付与
2. ✅ メソッド引数に `TenantIdentifier` を含める（第一引数推奨）
3. ✅ EntryServiceをProxyでラップして使用
4. ✅ RepositoryでSqlExecutorを使用

**開発者が意識しなくてよいこと**:

1. ❌ TransactionManagerの直接呼び出し（Proxyが自動実行）
2. ❌ Connection管理（ThreadLocalで自動管理）
3. ❌ commit/rollback（Proxyが自動実行）
4. ❌ RLS設定（Proxyが自動実行）
5. ❌ ログコンテキスト設定（Proxyが自動実行）

**情報源**: Issue #672（SQL Injection対策）

## JSON シリアライズ・デシリアライズ

### JsonConverter - JSON変換ユーティリティ

**情報源**: [JsonConverter.java:33](../../libs/idp-server-platform/src/main/java/org/idp/server/platform/json/JsonConverter.java#L33)

Jackson ObjectMapperのラッパー。スネークケース/キャメルケース変換をサポート。

#### インスタンス取得

```java
/**
 * JsonConverter - 2つのインスタンス
 * 確認方法: 実ファイルの33-44行目
 */

// ✅ デフォルトインスタンス（キャメルケース）
JsonConverter defaultConverter = JsonConverter.defaultInstance();

// ✅ スネークケースインスタンス
JsonConverter snakeCaseConverter = JsonConverter.snakeCaseInstance();
```

**重要**:
- **defaultInstance()**: フィールド名をそのまま維持（`clientId`）
- **snakeCaseInstance()**: スネークケースに変換（`client_id`）

#### シリアライズ（Java → JSON）

```java
// オブジェクトをJSON文字列に変換
ClientConfiguration config = ...;
String json = jsonConverter.write(config);

// Map → JSON
Map<String, Object> map = Map.of("client_id", "test-client");
String json = jsonConverter.write(map);
```

#### デシリアライズ（JSON → Java）

```java
// JSON文字列 → オブジェクト
String json = "{\"client_id\":\"test-client\",\"client_name\":\"Test\"}";
ClientConfiguration config = jsonConverter.read(json, ClientConfiguration.class);

// Map → オブジェクト
Map<String, Object> map = request.toMap();
ClientConfiguration config = jsonConverter.read(map, ClientConfiguration.class);
```

#### スネークケース変換の重要性

```java
// ✅ Context Creatorで使用
JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

// HTTPリクエスト: snake_case → Javaオブジェクト: camelCase
Map<String, Object> requestBody = Map.of(
    "client_id", "test",        // snake_case
    "client_name", "Test",      // snake_case
    "redirect_uris", List.of()  // snake_case
);

ClientConfiguration config = jsonConverter.read(requestBody, ClientConfiguration.class);
// config.clientId() → "test"      // camelCase
// config.clientName() → "Test"    // camelCase
// config.redirectUris() → []      // camelCase
```

**使用箇所**:
- **Context Creator**: リクエストDTO → ドメインモデル変換
- **Repository**: JSON列（JSONB）→ Javaオブジェクト変換
- **Cache**: Redis保存時のシリアライズ
- **HTTP**: 外部APIとのJSON通信

#### Jackson設定

```java
// フィールドアクセス（getterなしでもシリアライズ可能）
objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

// スネークケース変換
objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

// Java 8 Time API対応
objectMapper.registerModule(new JavaTimeModule());

// 空文字列 → null 変換
objectMapper.coercionConfigFor(LogicalType.Collection)
    .setCoercion(CoercionInputShape.EmptyString, CoercionAction.AsNull);
```

**情報源**: [JsonConverter.java:52-80](../../libs/idp-server-platform/src/main/java/org/idp/server/platform/json/JsonConverter.java#L52-L80)

## Dependency Injection - DIコンテナ

**情報源**: `libs/idp-server-platform/src/main/java/org/idp/server/platform/dependency/`

platformモジュールは、軽量なDIコンテナ機構を提供。Pluginの依存関係解決に使用。

### ApplicationComponentContainer

**情報源**: [ApplicationComponentContainer.java:22](../../libs/idp-server-platform/src/main/java/org/idp/server/platform/dependency/ApplicationComponentContainer.java#L22)

```java
/**
 * シンプルなDIコンテナ
 * 確認方法: 実ファイルの22-41行目
 */
public class ApplicationComponentContainer {

  Map<Class<?>, Object> dependencies;

  public ApplicationComponentContainer() {
    this.dependencies = new HashMap<>();
  }

  // ✅ コンポーネント登録
  public void register(Class<?> type, Object instance) {
    dependencies.put(type, instance);
  }

  // ✅ コンポーネント解決
  public <T> T resolve(Class<T> type) {
    if (!dependencies.containsKey(type)) {
      throw new ApplicationComponentMissionException(
          "Missing datasource for type: " + type.getName());
    }
    return type.cast(dependencies.get(type));
  }
}
```

### 使用例: AuthenticationDependencyContainer

```java
// DIコンテナに依存関係を登録
ApplicationComponentContainer container = new ApplicationComponentContainer();
container.register(UserQueryRepository.class, userQueryRepository);
container.register(PasswordVerificationDelegation.class, passwordVerificationDelegation);

// Pluginで依存関係を解決
public class PasswordAuthenticationInteractorFactory implements AuthenticationInteractorFactory {

  @Override
  public AuthenticationInteractor create(AuthenticationDependencyContainer container) {
    // ✅ コンテナから依存関係を解決
    PasswordVerificationDelegation delegation =
        container.resolve(PasswordVerificationDelegation.class);

    return new PasswordAuthenticationInteractor(delegation);
  }
}
```

### ProtocolContainer

**情報源**: `libs/idp-server-platform/src/main/java/org/idp/server/platform/dependency/protocol/`

Protocol（OAuth/OIDC）の依存関係管理。

```java
/**
 * Protocolの依存関係コンテナ
 */
public class ProtocolContainer {

  Map<String, ProtocolProvider> providers;

  public <T> T resolve(String key, Class<T> type) {
    ProtocolProvider provider = providers.get(key);
    return type.cast(provider.provide());
  }
}
```

**用途**:
- Plugin FactoryでのRepository解決
- AuthenticationInteractor組み立て
- FederationInteractor組み立て
- Protocol実装の依存関係管理

**重要**: idp-serverのDIは、Spring BootのDIとは**別レイヤー**:
- **Spring Boot**: Controller, Configuration, Bean管理
- **platform dependency**: Plugin, Interactor, Protocolの依存関係のみ

## Plugin System

**情報源**: [PluginLoader.java:25-91](../../libs/idp-server-platform/src/main/java/org/idp/server/platform/plugin/PluginLoader.java#L25-L91)

platformモジュールは、Java ServiceLoader機構を使用したプラグインシステムを提供。内部モジュール・外部JARの両方をサポート。

### PluginLoader - 静的メソッドAPI

**重要**: PluginLoaderはインスタンス化不要。全て静的メソッドで提供。

```java
// ✅ 内部モジュールからロード（META-INF/services）
List<AccessTokenCustomClaimsCreator> internalCreators =
    PluginLoader.loadFromInternalModule(AccessTokenCustomClaimsCreator.class);

// ✅ 外部JARからロード（plugins/ディレクトリ）
List<AccessTokenCustomClaimsCreator> externalCreators =
    PluginLoader.loadFromExternalModule(AccessTokenCustomClaimsCreator.class);

// ✅ 両方をマージして使用
List<AccessTokenCustomClaimsCreator> allCreators = new ArrayList<>();
allCreators.addAll(internalCreators);
allCreators.addAll(externalCreators);

// プラグイン適用
for (AccessTokenCustomClaimsCreator creator : allCreators) {
  Map<String, Object> claims = creator.create(context);
  customClaims.putAll(claims);
}
```

### 内部モジュールプラグイン

**idp-server内部での機能拡張**

1. プラグインインターフェース実装:
```java
package com.example.internal;

public class MyCustomClaimsCreator implements AccessTokenCustomClaimsCreator {
  @Override
  public Map<String, Object> create(AccessTokenContext context) {
    return Map.of("custom_claim", "value");
  }
}
```

2. `resources/META-INF/services/` にファイル作成:
```
ファイル名: org.idp.server.core.openid.token.plugin.AccessTokenCustomClaimsCreator
内容: com.example.internal.MyCustomClaimsCreator
```

3. ビルド後、自動的にロードされる

### 外部JARプラグイン

**外部モジュールによる機能拡張**

1. プラグインインターフェース実装（同上）

2. `resources/META-INF/services/` にファイル作成（同上）

3. JARをビルド:
```bash
./gradlew jar
```

4. `plugins/` ディレクトリに配置:
```bash
cp build/libs/my-custom-claims-1.0.0.jar /path/to/idp-server/plugins/
```

5. idp-server再起動でロード

### URLClassLoader機構

**情報源**: [PluginLoader.java:41-90](../../libs/idp-server-platform/src/main/java/org/idp/server/platform/plugin/PluginLoader.java#L41-L90)

外部JARは専用ClassLoaderで分離ロード:

```java
// plugins/ディレクトリ内の全JARを検索
File dir = new File("plugins");
File[] jars = dir.listFiles((d, name) -> name.endsWith(".jar"));

// URLClassLoaderで外部JARをロード
URL[] urls = Arrays.stream(jars).map(f -> f.toURI().toURL()).toArray(URL[]::new);
ClassLoader contextClassLoader = PluginLoader.class.getClassLoader();
URLClassLoader loader = new URLClassLoader(urls, contextClassLoader);

// ServiceLoaderで実装を検索
ServiceLoader<T> serviceLoader = ServiceLoader.load(type, loader);

// 外部JARからのみロード（内部重複を回避）
for (T impl : serviceLoader) {
  if (impl.getClass().getClassLoader() != contextClassLoader) {
    extensions.add(impl);
  }
}
```

### 専用PluginLoader

**情報源**: `libs/idp-server-platform/src/main/java/org/idp/server/platform/plugin/`

特定用途向けに専用ローダーを提供:

- `AuditLogWriterPluginLoader` - 監査ログライター拡張
- `EmailSenderPluginLoader` - メール送信プラグイン
- `SmsSenderPluginLoader` - SMS送信プラグイン
- `SecurityEventHooksPluginLoader` - セキュリティイベントフック
- `AdditionalOAuthAuthorizationResolverPluginLoader` - OAuth認可拡張

**情報源**: [intro-01-tech-overview.md:171-190](../content_01_intro/intro-01-tech-overview.md#L171-L190)

## 値オブジェクトパターン (Value Object)

platformモジュールの全識別子・属性は値オブジェクトとして実装。

### 実装例

```java
// ✅ 値オブジェクト: Immutable + 値による等価性
public class TenantIdentifier {
  private final String value;

  public TenantIdentifier(String value) {
    Objects.requireNonNull(value, "TenantIdentifier cannot be null");
    this.value = value;
  }

  public String value() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TenantIdentifier)) return false;
    TenantIdentifier that = (TenantIdentifier) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }
}
```

### メリット

1. **型安全性**: `TenantIdentifier`と`OrganizationIdentifier`を間違えるとコンパイルエラー
2. **バリデーション集約**: 値オブジェクトのコンストラクタで検証
3. **意図明確化**: `String`ではなく`TenantIdentifier`で意図を表現
4. **リファクタリング容易**: 型が違うため変更箇所が特定しやすい

## アンチパターン

### ❌ 1. String濫用

```java
// ❌ 悪い例
public Tenant findTenant(String tenantId) {
  return tenantRepository.find(tenantId);
}

// ✅ 良い例
public Tenant findTenant(TenantIdentifier tenantIdentifier) {
  return tenantRepository.find(tenantIdentifier);
}
```

### ❌ 2. Map濫用

```java
// ❌ 悪い例: 設定をMapで持ち回る
public void configure(Map<String, Object> config) {
  boolean enableFeature = (boolean) config.get("enable_feature"); // 型キャストが必要
}

// ✅ 良い例: TenantAttributesで型安全にアクセス
public void configure(TenantAttributes attributes) {
  boolean enableFeature = attributes.optValueAsBoolean("enable_feature", false); // 型安全
}
```

### ❌ 3. Platformモジュールへのドメインロジック混入

```java
// ❌ 悪い例: platformに業務ロジック
public class TenantAttributes {
  public boolean canAccessPremiumFeatures() {
    // ❌ 業務判定ロジックがplatformに
    return optValueAsBoolean("is_premium", false);
  }
}

// ✅ 良い例: Core/UseCase層で判定
public class PremiumFeatureVerifier {
  public boolean canAccess(Tenant tenant) {
    // ✅ 業務ロジックはドメイン層に
    return tenant.attributes().optValueAsBoolean("is_premium", false)
        && tenant.hasFeature("premium_access");
  }
}
```

**原則**: platformは汎用的な基盤機能のみ、業務ロジックは上位層に配置。

## まとめ

### idp-server-platform を理解するための5つのポイント

1. **マルチテナント基盤**: TenantIdentifier/OrganizationIdentifier による完全分離
2. **値オブジェクト徹底**: String/Map濫用を避け、型安全な設計
3. **TenantAttributes パターン**: `optValueAsBoolean(key, default)` で設定取得
4. **Repository命名規則**: `get()`必須存在、`find()`任意存在
5. **Tenant第一引数の原則**: OrganizationRepository以外は全てTenant第一引数

### 次のステップ

- [idp-server-use-cases（ユースケース層）](./ai-10-use-cases.md) - EntryServiceパターン
- [idp-server-core-adapter（アダプター層）](./ai-21-core-adapter.md) - Repository実装詳細
- [idp-server-control-plane（管理API契約）](./ai-13-control-plane.md) - Control Plane設計

---

## ドキュメント修正履歴

### 2025-10-12: 実装検証に基づく大規模修正

#### 修正1: OrganizationRepository の実装に合わせた修正 (238-275行目)

**問題**: 存在しないメソッドが記載されていた

**修正内容**:
```diff
- Organization find(OrganizationIdentifier identifier);     // ❌ 存在しない
- OrganizationMember findMember(...);                       // ❌ 存在しない
- AssignedTenant findAssignment(...);                       // ❌ 存在しない
+ void register(Organization organization);
+ void update(Organization organization);
+ Organization get(OrganizationIdentifier identifier);
+ List<Organization> findList(OrganizationQueries queries);
```

**追加**: 代替パターンの説明（`org.assignedTenants()`, `org.findOrgTenant()` の使用方法）

**検証**: [OrganizationRepository.java:21-29](../../libs/idp-server-platform/src/main/java/org/idp/server/platform/multi_tenancy/organization/OrganizationRepository.java#L21-L29)

#### 修正2: TenantAttributes.optValueAsInt() の削除 (153-170行目)

**問題**: 実装に存在しないメソッドを使用例に記載

**修正内容**:
```diff
- int tokenLifetime = attributes.optValueAsInt("token.access_token.lifetime_seconds", 3600);  // ❌ 存在しない
+ List<String> allowedScopes = attributes.optValueAsStringList(
+     "oauth.allowed_scopes",
+     List.of("openid", "profile")
+ );  // ✅ 実際に存在するメソッド
```

**検証**: [TenantAttributes.java:25-84](../../libs/idp-server-platform/src/main/java/org/idp/server/platform/multi_tenancy/tenant/TenantAttributes.java#L25-L84)

#### 修正3: JOSE (JWT/JWS/JWE/JWK) セクション全面改訂 (353-417行目)

**問題**: 想像で書かれた存在しないクラス・API

**修正前**:
```java
JwtCreator jwtCreator = new JwtCreator();            // ❌ 存在しない
JwtVerifier jwtVerifier = new JwtVerifier(...);      // ❌ 存在しない
Jwt jwt = jwtCreator.create(...);                    // ❌ 存在しない
Claims claims = jwt.claims();                        // ❌ 存在しない
```

**修正後**:
```java
JoseHandler joseHandler = new JoseHandler();                                    // ✅ 実装
JsonWebSignature jws = JsonWebSignature.parse(jwsString);                      // ✅ 実装
JsonWebSignatureVerifier verifier = new JsonWebSignatureVerifier(...);         // ✅ 実装
JsonWebTokenClaims claims = jws.claims();                                       // ✅ 実装
```

**検証**:
- [JoseHandler.java:23-40](../../libs/idp-server-platform/src/main/java/org/idp/server/platform/jose/JoseHandler.java#L23-L40)
- [JsonWebSignature.java:28-100](../../libs/idp-server-platform/src/main/java/org/idp/server/platform/jose/JsonWebSignature.java#L28-L100)
- [JsonWebToken.java:24-62](../../libs/idp-server-platform/src/main/java/org/idp/server/platform/jose/JsonWebToken.java#L24-L62)

#### 修正4: HTTP クライアント セクション全面改訂 (419-511行目)

**問題**: 簡略化された想像API、実装との乖離

**修正内容**:
- Java標準 `java.net.http.HttpClient` ベースの実装に修正
- OAuth 2.0自動認証、リトライメカニズム、Idempotencyキー管理の説明追加
- 設定ベースのリクエスト構築パターン追加

**検証**: [HttpRequestExecutor.java:36-165](../../libs/idp-server-platform/src/main/java/org/idp/server/platform/http/HttpRequestExecutor.java#L36-L165)

#### 修正5: Datasource・トランザクション セクション全面改訂 (513-723行目)

**問題**: 最も重要なDynamic Proxy機構の説明が欠落、TransactionManagerの直接使用を推奨する誤解

**修正前**:
```java
// ❌ 誤り: 開発者が直接TransactionManagerを呼び出す想定
TransactionManager.beginTransaction(DatabaseType.POSTGRESQL, tenantIdentifier);
try {
  clientRepository.register(tenant, clientConfiguration);
  TransactionManager.commitTransaction();
} catch (Exception e) {
  TransactionManager.rollbackTransaction();
}
```

**修正後**:
```java
// ✅ 正しい: @Transactionアノテーション + Dynamic Proxy
@Transaction
public class UserManagementEntryService implements UserManagementApi {
  // Proxyが自動的にトランザクション管理
  // 開発者はTransactionManagerを意識しない
}

// Proxy作成
UserManagementApi api = TenantAwareEntryServiceProxy.createProxy(
    new UserManagementEntryService(...),
    UserManagementApi.class,
    applicationDatabaseTypeProvider
);
```

**追加内容**:
- `@Transaction` アノテーションの詳細説明
- `TenantAwareEntryServiceProxy` の動作フロー（8ステップ）
- `SqlExecutor` の使用方法とRepository実装例
- 「開発者がすべきこと」「意識しなくてよいこと」の明確な区別
- Proxyパッケージの説明（proxy/）

**検証**:
- [Transaction.java:24-28](../../libs/idp-server-platform/src/main/java/org/idp/server/platform/datasource/Transaction.java#L24-L28)
- [TenantAwareEntryServiceProxy.java:29-181](../../libs/idp-server-platform/src/main/java/org/idp/server/platform/proxy/TenantAwareEntryServiceProxy.java#L29-L181)
- [SqlExecutor.java:23-83](../../libs/idp-server-platform/src/main/java/org/idp/server/platform/datasource/SqlExecutor.java#L23-L83)

#### 修正6: Plugin System セクション全面改訂 (725-839行目)

**問題**: インスタンス化API（存在しない）を記載、静的メソッドAPIの説明欠落

**修正前**:
```java
// ❌ 存在しないAPI: インスタンス化不可
PluginLoader<AccessTokenCustomClaimsCreator> loader =
    new PluginLoader<>(AccessTokenCustomClaimsCreator.class);
List<AccessTokenCustomClaimsCreator> creators = loader.load();  // ❌ load()メソッドは存在しない
```

**修正後**:
```java
// ✅ 実際のAPI: 静的メソッド
List<AccessTokenCustomClaimsCreator> internalCreators =
    PluginLoader.loadFromInternalModule(AccessTokenCustomClaimsCreator.class);
List<AccessTokenCustomClaimsCreator> externalCreators =
    PluginLoader.loadFromExternalModule(AccessTokenCustomClaimsCreator.class);
```

**追加内容**:
- 内部モジュール vs 外部JARの区別と使い分け
- URLClassLoader機構の詳細説明
- 専用PluginLoader一覧（AuditLogWriter, EmailSender, SmsSender等）
- META-INF/servicesファイルの具体的な作成手順

**検証**:
- [PluginLoader.java:25-91](../../libs/idp-server-platform/src/main/java/org/idp/server/platform/plugin/PluginLoader.java#L25-L91)
- 専用ローダー: `libs/idp-server-platform/src/main/java/org/idp/server/platform/plugin/`

### 修正の原則

**CLAUDE.md「想像ドキュメント作成防止」に基づく修正**:
1. **コードファーストの原則**: 必ずソースコードを先に確認
2. **情報源記録**: 参照ファイル・行番号を明記
3. **段階的確認**: クラス名→メソッド名→シグネチャの順で段階的に確認
4. **不明点明示**: 推測・仮定を明確に区別

---

**情報源**:
- `libs/idp-server-platform/src/main/java/`配下の実装コード
- CLAUDE.md「マルチテナント」「値オブジェクト」「Repository命名規則」「想像ドキュメント作成防止」
- Issue #672（SQL Injection修正）
- Issue #676（AI開発者向け知識ベースの作成・改善）

**最終更新**: 2025-10-12
**確認方法**: `find libs/idp-server-platform/src/main/java/org/idp/server/platform -type d -maxdepth 1`
**レビュー実施**: 2025-10-12 - AI開発者向けドキュメント品質改善プロジェクト
