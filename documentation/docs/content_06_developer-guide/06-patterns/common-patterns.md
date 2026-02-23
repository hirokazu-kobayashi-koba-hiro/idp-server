# 03. 共通実装パターン

## このドキュメントの目的

idp-serverで頻繁に使用する実装パターンを理解し、**迷わず実装できる**ようになることが目標です。

### 所要時間
⏱️ **約20分**

### 前提知識
- [01. アーキテクチャ概要](./01-architecture-overview.md)
- [02. 最初のAPI実装](./02-first-api-implementation.md)

---

## パターン一覧

| パターン | 使用タイミング | 層 |
|---------|-------------|---|
| [Repository パターン](#1-repository-パターン) | データアクセス | Adapter層 |
| [Context Creator パターン](#2-context-creator-パターン) | リクエスト変換 | Control Plane層 |
| [Handler-Service パターン](#3-handler-service-パターン) | ドメインロジック | Core層 |
| [Plugin パターン](#4-plugin-パターン) | 拡張機能 | Core層・Extension層 |
| [JsonConverter パターン](#5-jsonconverter-パターン) | JSON変換 | 全層 |

---

## 1. Repository パターン

### 基本ルール

#### ✅ 必須: Tenant第一引数

```java
public interface ClientConfigurationQueryRepository {

    // ✅ 正しい: Tenant第一引数
    ClientConfiguration get(Tenant tenant, RequestedClientId clientId);

    ClientConfiguration find(Tenant tenant, ClientIdentifier clientIdentifier);

    List<ClientConfiguration> findList(Tenant tenant, int limit, int offset);

    long findTotalCount(Tenant tenant);
}

// ❌ 例外: OrganizationRepositoryのみ（組織はテナントより上位）
public interface OrganizationQueryRepository {
    Organization get(OrganizationIdentifier organizationIdentifier);
}
```

**理由**: マルチテナント分離を強制。テナント指定忘れでデータ漏洩を防ぐ。

**重要**: `Optional`は使用しない。`find()`は**Null Object Pattern**を採用（`SomeModel.notFound()`を返す）。

---

### Query/Command分離

```java
// Query Repository - 読み取り専用
public interface ClientConfigurationQueryRepository {
    ClientConfiguration get(Tenant tenant, RequestedClientId clientId);
    ClientConfiguration find(Tenant tenant, ClientIdentifier clientIdentifier);  // Null Object Pattern
    List<ClientConfiguration> findList(Tenant tenant, int limit, int offset);
    long findTotalCount(Tenant tenant);
}

// Command Repository - 書き込み専用
public interface ClientConfigurationCommandRepository {
    void register(Tenant tenant, ClientConfiguration configuration);
    void update(Tenant tenant, ClientConfiguration configuration);
    void delete(Tenant tenant, RequestedClientId clientId);
}
```

**用途**:
- **Query**: 読み取りトランザクション最適化（`@Transaction(readOnly = true)`）
- **Command**: 書き込みトランザクション（`@Transaction`）

**Null Object Pattern**:
```java
// find()はnullを返さない、空オブジェクトを返す
ClientConfiguration client = repository.find(tenant, clientIdentifier);
if (client.exists()) {  // ドメインモデルのexists()メソッドで存在確認
    // 処理
}
```

---

### 命名規則

| メソッド名 | 戻り値型 | 意味 |
|-----------|---------|-----|
| `get()` | `T` | **必須存在**（存在しない場合は例外スロー） |
| `find()` | `T` | **任意存在**（Null Object Patternで空オブジェクト返却） |
| `findList()` | `List<T>` | 複数件取得（limit/offset付き） |
| `findTotalCount()` | `long` | 件数取得 |

**重要**: `Optional`、`exists()`、`is/has/can()`はRepositoryに定義**しない**。
- これらのメソッドは**ドメインモデルクラス**に実装する

**実装例**:

```java
// get() - 必須存在
ClientConfiguration client = repository.get(tenant, clientId);
// 存在しない場合 → ClientNotFoundException

// find() - 任意存在（Null Object Pattern）
ClientConfiguration client = repository.find(tenant, clientIdentifier);
if (client.exists()) {  // ドメインモデルのメソッド
    // 処理
}
// nullチェック不要！

// findList() - 複数件取得
List<ClientConfiguration> clients = repository.findList(tenant, 10, 0);

// ドメインモデルの実装例
public class ClientConfiguration {
    public static ClientConfiguration notFound() {
        return new ClientConfiguration();  // 空オブジェクト
    }

    public boolean exists() {
        return Objects.nonNull(clientId) && !clientId.isEmpty();
    }
}
```

---

### DataSource-SqlExecutor パターン

**Adapter層の実装パターン**。

```java
public class ClientConfigurationDataSource implements ClientConfigurationQueryRepository {

    private final SqlExecutor sqlExecutor;

    public ClientConfigurationDataSource(SqlExecutor sqlExecutor) {
        this.sqlExecutor = sqlExecutor;
    }

    @Override
    public ClientConfiguration get(Tenant tenant, RequestedClientId clientId) {
        // 1. SQL定義
        String sql = """
            SELECT client_id, client_name, client_type, redirect_uris
            FROM client_configuration
            WHERE tenant_id = ? AND client_id = ?
            """;

        // 2. SQL実行（⚠️ ビジネスロジック禁止）
        Map<String, Object> row = sqlExecutor.selectOne(
            sql,
            tenant.identifier().value(),  // UUID
            clientId.value()              // String
        );

        // 3. ドメインモデルに変換
        return ClientConfigurationMapper.map(row);
    }
}
```

**禁止事項**:
```java
// ❌ Adapter層でビジネスロジック禁止
if ("ORGANIZER".equals(tenant.type())) {
    // このような判定はCore層の仕事
}
```

---

## 2. Context Creator パターン

### 役割

リクエストDTO → ドメインモデルへの変換。

**定義場所**: `idp-server-control-plane` モジュール
**使用場所**: `idp-server-use-cases` モジュール（EntryService）

---

### 実装パターン

```java
package org.idp.server.control_plane.management.oidc.client;

/**
 * ClientRegistrationContext Creator
 * リクエストDTO → ClientRegistrationContext変換
 */
public class ClientRegistrationContextCreator {

    private final Tenant tenant;
    private final ClientRegistrationRequest request;
    private final boolean dryRun;

    public ClientRegistrationContextCreator(
            Tenant tenant,
            ClientRegistrationRequest request,
            boolean dryRun) {
        this.tenant = tenant;
        this.request = request;
        this.dryRun = dryRun;
    }

    public ClientRegistrationContext create() {
        // 1. リクエストからドメインモデル生成
        RequestedClientId clientId = new RequestedClientId(request.getClientId());
        ClientName clientName = new ClientName(request.getClientName());
        ClientType clientType = ClientType.of(request.getClientType());

        // 2. Contextオブジェクト生成
        ClientConfiguration configuration = new ClientConfiguration(
            clientId,
            clientName,
            clientType,
            // ... その他のフィールド
        );

        return new ClientRegistrationContext(tenant, configuration, dryRun);
    }
}
```

---

### EntryServiceでの使用例

```java
@Override
public ClientManagementResponse create(
        TenantIdentifier tenantIdentifier,
        User operator,
        OAuthToken oAuthToken,
        ClientRegistrationRequest request,
        RequestAttributes requestAttributes,
        boolean dryRun) {

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    // ✅ Context Creator使用
    ClientRegistrationContextCreator contextCreator =
        new ClientRegistrationContextCreator(tenant, request, dryRun);
    ClientRegistrationContext context = contextCreator.create();

    // Dry Runチェック
    if (dryRun) {
        return context.toResponse();
    }

    // Repository保存
    clientConfigurationCommandRepository.register(tenant, context.configuration());

    return context.toResponse();
}
```

**重要**: Context Creatorを使わずに直接変換するのは**アンチパターン**。

---

## 3. Handler-Service パターン

### 構造

```
Handler (プロトコル処理・オーケストレーション)
   ↓ 委譲
Service (純粋ビジネスロジック)
```

---

### Handler - プロトコル処理

```java
public class OAuthAuthorizeHandler {

    private final AuthorizationRequestRepository authorizationRequestRepository;
    private final ClientConfigurationQueryRepository clientConfigurationQueryRepository;
    private final OAuthAuthorizeService service;

    public AuthorizationResponse handle(
            OAuthAuthorizeRequest request,
            OAuthSessionDelegate delegate) {

        // 1. Validatorで入力検証
        OAuthAuthorizeRequestValidator validator =
            new OAuthAuthorizeRequestValidator(request);
        validator.validate();

        // 2. Repository呼び出し（⚠️ Tenant第一引数）
        Tenant tenant = request.tenant();
        AuthorizationRequest authorizationRequest =
            authorizationRequestRepository.get(tenant, request.toIdentifier());
        ClientConfiguration clientConfiguration =
            clientConfigurationQueryRepository.get(tenant, authorizationRequest.requestedClientId());

        // 3. Serviceに委譲（純粋ビジネスロジック）
        AuthorizationCode authorizationCode = service.createAuthorizationCode(
            request.user(),
            request.authentication(),
            authorizationRequest,
            clientConfiguration
        );

        // 4. レスポンス生成
        return AuthorizationResponse.success(authorizationCode);
    }
}
```

**Handler責務**:
- ✅ プロトコル処理（リクエスト/レスポンス）
- ✅ Validator/Verifier呼び出し
- ✅ Repository呼び出し
- ✅ Service呼び出し
- ❌ ビジネスロジック（それはServiceの仕事）

---

### Service - 純粋ビジネスロジック

```java
public class OAuthAuthorizeService {

    /**
     * 認可コード生成
     * RFC 6749 Section 4.1.2 準拠
     */
    public AuthorizationCode createAuthorizationCode(
            User user,
            Authentication authentication,
            AuthorizationRequest authorizationRequest,
            ClientConfiguration clientConfiguration) {

        // ✅ 純粋関数的ロジック（外部依存なし）
        if (clientConfiguration.isConfidential()) {
            return AuthorizationCode.generate();
        }

        if (authorizationRequest.requiresPKCE()) {
            return AuthorizationCode.generateWithPKCE();
        }

        return AuthorizationCode.empty();
    }
}
```

**Service責務**:
- ✅ 純粋ビジネスロジック
- ✅ RFC仕様準拠の計算・判定
- ❌ Repository呼び出し（HandlerがServiceに渡す）
- ❌ プロトコル処理（それはHandlerの仕事）

---

## 4. Plugin パターン

### PluginLoader - 静的メソッドAPI

```java
// ✅ 正しい: 静的メソッド使用
Map<GrantType, OAuthTokenCreationService> services =
    PluginLoader.loadFromInternalModule(OAuthTokenCreationService.class);

OAuthTokenCreationService service = services.get(grantType);
OAuthToken token = service.create(request);

// ❌ 間違い: インスタンス化不可
PluginLoader<OAuthTokenCreationService> loader =
    new PluginLoader<>(OAuthTokenCreationService.class);  // コンパイルエラー
```

---

### Plugin実装例

```java
/**
 * 認可コードグラント用トークン生成
 *
 * @Plugin(type = "AUTHORIZATION_CODE")
 */
public class AuthorizationCodeTokenCreationService implements OAuthTokenCreationService {

    @Override
    public GrantType supportedGrantType() {
        return GrantType.AUTHORIZATION_CODE;
    }

    @Override
    public OAuthToken create(OAuthTokenRequest request) {
        // 認可コード検証・トークン発行
        return OAuthToken.issue(...);
    }
}
```

---

## 5. JsonConverter パターン

### defaultInstance() vs snakeCaseInstance()

```java
import org.idp.server.platform.converter.JsonConverter;

// ✅ defaultInstance() - キャメルケース維持
JsonConverter converter = JsonConverter.defaultInstance();
String json = converter.write(clientConfiguration);
// {"clientId": "abc", "clientName": "Example"}

// ✅ snakeCaseInstance() - スネークケース変換
JsonConverter converter = JsonConverter.snakeCaseInstance();
String json = converter.write(clientConfiguration);
// {"client_id": "abc", "client_name": "Example"}
```

---

### 用途

| 用途 | 使用するインスタンス |
|------|-------------------|
| Context Creator（DTO変換） | `snakeCaseInstance()` |
| Repository（JSONB列） | `snakeCaseInstance()` |
| Cache（Redis） | `defaultInstance()` |
| HTTP通信（外部API） | `snakeCaseInstance()` |

---

### 実装例

```java
// Context Creator
public class ClientRegistrationContextCreator {

    private static final JsonConverter converter = JsonConverter.snakeCaseInstance();

    public ClientRegistrationContext create() {
        // JSON文字列 → Map変換
        Map<String, Object> metadata = converter.read(request.getMetadata());

        // Map → JSON文字列変換
        String json = converter.write(clientConfiguration.toMap());

        return new ClientRegistrationContext(...);
    }
}
```

---

## よくある間違い

### ❌ 間違い1: Repository第一引数にTenantなし

```java
// ❌ 間違い
ClientConfiguration client = repository.get(clientId);

// ✅ 正しい
ClientConfiguration client = repository.get(tenant, clientId);
```

---

### ❌ 間違い2: Adapter層でビジネスロジック

```java
// ❌ 間違い: Adapter層でビジネス判定
public ClientConfiguration get(Tenant tenant, RequestedClientId clientId) {
    String sql = "SELECT * FROM client_configuration WHERE tenant_id = ? AND client_id = ?";
    Map<String, Object> row = sqlExecutor.selectOne(sql, tenant.value(), clientId.value());

    // ❌ ビジネスロジック禁止
    if ("ORGANIZER".equals(tenant.type())) {
        // ...
    }

    return ClientConfigurationMapper.map(row);
}

// ✅ 正しい: Adapter層はSQLのみ
public ClientConfiguration get(Tenant tenant, RequestedClientId clientId) {
    String sql = "SELECT * FROM client_configuration WHERE tenant_id = ? AND client_id = ?";
    Map<String, Object> row = sqlExecutor.selectOne(sql, tenant.value(), clientId.value());
    return ClientConfigurationMapper.map(row);
}
```

---

### ❌ 間違い3: Context Creator未使用

```java
// ❌ 間違い: EntryServiceでDTO直接変換
public ClientManagementResponse create(ClientRegistrationRequest request) {
    ClientConfiguration configuration = new ClientConfiguration(
        new RequestedClientId(request.getClientId()),
        new ClientName(request.getClientName()),
        // ... 直接変換
    );
    // ...
}

// ✅ 正しい: Context Creator使用
public ClientManagementResponse create(ClientRegistrationRequest request) {
    ClientRegistrationContextCreator creator =
        new ClientRegistrationContextCreator(tenant, request, dryRun);
    ClientRegistrationContext context = creator.create();
    // ...
}
```

---

### ❌ 間違い4: PluginLoaderインスタンス化

```java
// ❌ 間違い: インスタンス化不可
PluginLoader<OAuthTokenCreationService> loader =
    new PluginLoader<>(OAuthTokenCreationService.class);

// ✅ 正しい: 静的メソッド使用
Map<GrantType, OAuthTokenCreationService> services =
    PluginLoader.loadFromInternalModule(OAuthTokenCreationService.class);
```

---

## 実装判断チャート

```
Q1: データベースアクセスが必要？
    YES → Repository パターン
    NO  → Q2へ

Q2: リクエストDTO → ドメインモデル変換？
    YES → Context Creator パターン
    NO  → Q3へ

Q3: OAuth仕様準拠のロジック？
    YES → Handler-Service パターン
    NO  → Q4へ

Q4: 拡張機能（複数実装の切り替え）？
    YES → Plugin パターン
    NO  → Q5へ

Q5: JSON変換が必要？
    YES → JsonConverter パターン
    NO  → 他のパターンを検討
```

---

## チェックリスト

実装前に以下を確認してください。

- [ ] Repository第一引数はTenant（OrganizationRepositoryは除く）
- [ ] Query/Command Repositoryを適切に分離
- [ ] Context Creator使用（DTO → ドメインモデル変換）
- [ ] Handler-Service分離（Handler=プロトコル、Service=ロジック）
- [ ] PluginLoaderは静的メソッド使用
- [ ] JsonConverterは適切なインスタンス使用（default vs snakeCase）
- [ ] Adapter層にビジネスロジックなし

---

## 次のステップ

✅ idp-serverの主要パターンを理解した！

### 📖 次に読むべきドキュメント

1. [04. トラブルシューティング](./04-troubleshooting.md) - よくあるエラーと解決策
2. [05. コードレビューチェックリスト](./05-code-review-checklist.md) - PR前の確認項目

---

**情報源**: 共通実装パターン
**最終更新**: 2025-10-12
