# マルチテナント

## 📍 このドキュメントの位置づけ

**対象読者**: idp-serverのマルチテナント実装を理解したい開発者

**このドキュメントで学べること**:
- マルチテナントアーキテクチャの実装詳細
- Tenant/Organization モデルの設計
- Repository層でのテナント分離パターン
- PostgreSQL RLSによるデータベースレベルの分離
- テナントコンテキスト管理の仕組み

**前提知識**:
- [concept-01: マルチテナント](../../content_03_concepts/01-foundation/concept-01-multi-tenant.md)の理解
- [Hexagonal Architecture](../../content_01_intro/tech-overview.md#アーキテクチャ)の基礎知識
- Repository パターンの理解

---

## 🏗️ マルチテナントアーキテクチャ概要

idp-serverは、**1つのアプリケーションインスタンスで複数のテナントを完全分離**するマルチテナント型IdPです。

### 設計原則

#### 1. Tenant-First Design
すべてのデータアクセスでテナントを明示的に指定します。

```java
// ✅ 正しい: Tenantを明示的に渡す
public interface UserCommandRepository {
  void register(Tenant tenant, User user);
  void update(Tenant tenant, User user);
  void delete(Tenant tenant, UserIdentifier userIdentifier);
}

// ❌ 誤り: Tenantなしでデータアクセス（テナント漏洩リスク）
public interface UserCommandRepository {
  void register(User user);  // どのテナントのユーザー?
}
```

**参考実装**:
- [UserCommandRepository.java:23](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/identity/repository/UserCommandRepository.java#L23)
- [AuthenticationConfigurationQueryRepository.java:24](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/authentication/repository/AuthenticationConfigurationQueryRepository.java#L24)

#### 2. 二重防御（Defense in Depth）
アプリケーション層とデータベース層の両方でテナント分離を強制します。

```
┌─────────────────────────────────────────┐
│  Application Layer (アプリケーション層)   │
│  - Repository第一引数でTenant強制        │
│  - TransactionManagerでRLS設定           │
└─────────────────────────────────────────┘
               ↓
┌─────────────────────────────────────────┐
│  Database Layer (データベース層)         │
│  - Row Level Security (RLS)による強制分離│
│  - FORCE ROW LEVEL SECURITY              │
└─────────────────────────────────────────┘
```

#### 3. Organization-Tenant 階層構造
組織とテナントの2層構造をサポートします。

```
Organization (組織)
├── Tenant (ORGANIZER) - 組織管理用
├── Tenant (PUBLIC)    - アプリケーション用①
└── Tenant (PUBLIC)    - アプリケーション用②
```

---

## 📦 コアモデル

### Tenant

テナントは、idp-server内での**完全に独立した認証・認可ドメイン**を表します。

**主要フィールド**:
```java
public class Tenant implements Configurable {
  TenantIdentifier identifier;           // UUID形式のテナントID
  TenantName name;                        // テナント名
  TenantType type;                        // ADMIN/ORGANIZER/PUBLIC
  TenantDomain domain;                    // テナントドメイン（トークンissuerに使用）
  AuthorizationProvider authorizationProvider;
  TenantAttributes attributes;            // カスタム属性
  TenantFeatures features;
  UIConfiguration uiConfiguration;
  CorsConfiguration corsConfiguration;
  SessionConfiguration sessionConfiguration;
  SecurityEventLogConfiguration securityEventLogConfiguration;
  SecurityEventUserAttributeConfiguration securityEventUserAttributeConfiguration;
  TenantIdentityPolicy identityPolicyConfig;
  OrganizationIdentifier mainOrganizationIdentifier;  // 所属組織
  boolean enabled;
}
```

**参考実装**: [Tenant.java:34](../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/multi_tenancy/tenant/Tenant.java#L34)

### TenantIdentifier

テナントIDを表す値オブジェクトです。

```java
public class TenantIdentifier implements UuidConvertable {
  String value;  // UUID文字列

  public UUID valueAsUuid() {
    return convertUuid(value);
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }
}
```

**参考実装**: [TenantIdentifier.java:23](../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/multi_tenancy/tenant/TenantIdentifier.java#L23)

### TenantType

テナントの種別を定義します。

```java
public enum TenantType {
  ADMIN,      // システム管理用テナント（初期化時に自動作成）
  ORGANIZER,  // 組織管理用テナント（組織作成時に自動作成）
  PUBLIC;     // アプリケーション用テナント（API経由で作成）
}
```

**参考実装**: [TenantType.java:19](../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/multi_tenancy/tenant/TenantType.java#L19)

**使い分け**:
- **ADMIN**: システム全体の初期設定・管理用（1つのみ）
- **ORGANIZER**: 組織内のテナント管理・組織メンバー管理用（組織ごとに1つ）
- **PUBLIC**: 実際のアプリケーション認証用（組織ごとに複数作成可能）

### Organization

組織は、複数のテナントをグループ化する上位概念です。

```java
public class Organization implements Configurable {
  OrganizationIdentifier identifier;    // UUID形式の組織ID
  OrganizationName name;                 // 組織名
  OrganizationDescription description;   // 組織説明
  AssignedTenants assignedTenants;       // 所属テナント一覧
  boolean enabled;

  public AssignedTenant findOrgTenant() {
    // type="ORGANIZER"のテナントを取得
    for (AssignedTenant tenant : assignedTenants()) {
      if ("ORGANIZER".equals(tenant.type())) {
        return tenant;
      }
    }
    throw new AdminTenantNotFoundException(...);
  }

  public boolean hasAssignedTenant(TenantIdentifier tenantIdentifier) {
    return assignedTenants.contains(tenantIdentifier);
  }
}
```

**参考実装**: [Organization.java:23](../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/multi_tenancy/organization/Organization.java#L23)

### OrganizationIdentifier

組織IDを表す値オブジェクトです。

```java
public class OrganizationIdentifier implements UuidConvertable {
  String value;  // UUID文字列

  public UUID valueAsUuid() {
    return convertUuid(value);
  }

  public boolean exists() {
    return value != null && !value.isEmpty();
  }
}
```

**参考実装**: [OrganizationIdentifier.java:24](../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/multi_tenancy/organization/OrganizationIdentifier.java#L24)

---

## 🛠️ Repository層の実装パターン

### Tenant第一引数パターン

すべてのRepository操作で、**第一引数に`Tenant`を渡す**ことでテナント分離を設計レベルで強制します。

#### Query Repository

```java
public interface AuthenticationConfigurationQueryRepository {
  // ✅ すべてのメソッドで第一引数がTenant
  AuthenticationConfiguration get(Tenant tenant, String key);
  AuthenticationConfiguration find(Tenant tenant, String key);
  AuthenticationConfiguration find(Tenant tenant, AuthenticationConfigurationIdentifier identifier);
  AuthenticationConfiguration findWithDisabled(Tenant tenant, AuthenticationConfigurationIdentifier identifier, boolean includeDisabled);
  long findTotalCount(Tenant tenant);
  List<AuthenticationConfiguration> findList(Tenant tenant, int limit, int offset);
}
```

**参考実装**: [AuthenticationConfigurationQueryRepository.java:24](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/authentication/repository/AuthenticationConfigurationQueryRepository.java#L24)

#### Command Repository

```java
public interface UserCommandRepository {
  // ✅ すべてのメソッドで第一引数がTenant
  void register(Tenant tenant, User user);
  void update(Tenant tenant, User user);
  void updatePassword(Tenant tenant, User user);
  void delete(Tenant tenant, UserIdentifier userIdentifier);
}
```

**参考実装**: [UserCommandRepository.java:23](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/identity/repository/UserCommandRepository.java#L23)

### 例外: OrganizationRepository

組織はテナントより上位概念のため、`Tenant`を第一引数に取りません。

```java
public interface OrganizationRepository {
  // ✅ 組織操作では、OrganizationIdentifierのみを使用
  void register(Organization organization);
  void update(Organization organization);
  void delete(OrganizationIdentifier identifier);
  Organization get(OrganizationIdentifier identifier);
  List<Organization> findList(OrganizationQueries queries);
}
```

**参考実装**: [OrganizationRepository.java:21](../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/multi_tenancy/organization/OrganizationRepository.java#L21)

---

## 🔐 データベースレベルのテナント分離

### PostgreSQL Row Level Security (RLS)

PostgreSQLを使用する場合、**Row Level Security (RLS)** によりデータベースレベルでテナント分離を強制します。

#### DDLでのRLS設定

```sql
-- テナントテーブルにRLS有効化
ALTER TABLE tenant ENABLE ROW LEVEL SECURITY;

-- ポリシー作成: app.tenant_idと一致する行のみアクセス可能
CREATE POLICY tenant_isolation_policy
  ON tenant
  USING (id = current_setting('app.tenant_id')::uuid);

-- 強制RLS: DB管理者も制限
ALTER TABLE tenant FORCE ROW LEVEL SECURITY;
```

**参考実装**: [V0_9_0__init_lib.sql](../../../libs/idp-server-database/postgresql/V0_9_0__init_lib.sql) (RLS設定箇所)

#### 全テーブルへのRLS適用

idp-serverでは、以下のテーブルにRLS が適用されています:
- `tenant` - テナント情報
- `organization_tenants` - 組織-テナント関係
- `authorization_server_configuration` - 認可サーバー設定
- `permission` - 権限
- `role` - ロール
- `idp_user` - ユーザー
- `client_configuration` - クライアント設定
- `authentication_configuration` - 認証設定
- その他すべてのテナント依存テーブル

**確認方法**:
```sql
-- RLS設定されているテーブルとポリシーを確認
SELECT schemaname, tablename, policyname, qual as policy_condition
FROM pg_policies
WHERE schemaname = 'public'
ORDER BY tablename, policyname;
```

**参考実装**: [select-rls-policy.sql](../../../libs/idp-server-database/postgresql/operation/select-rls-policy.sql)

### TransactionManagerによるテナントコンテキスト設定

`TransactionManager`は、トランザクション開始時にPostgreSQLセッション変数`app.tenant_id`を設定します。

```java
public class TransactionManager {

  public static void beginTransaction(DatabaseType databaseType, TenantIdentifier tenantIdentifier) {
    if (connectionHolder.get() != null) {
      throw new SqlRuntimeException("Transaction already started");
    }
    OperationContext.set(OperationType.WRITE);
    Connection conn = dbConnectionProvider.getConnection(
        databaseType, AdminTenantContext.isAdmin(tenantIdentifier));

    // PostgreSQLの場合、RLS用にテナントIDを設定
    if (databaseType == DatabaseType.POSTGRESQL) {
      setTenantId(conn, tenantIdentifier);
    }
    connectionHolder.set(conn);
  }

  /**
   * Sets the current tenant identifier for Row-Level Security (RLS).
   *
   * PostgreSQLのset_config()関数でapp.tenant_idを設定します。
   * is_local=trueにより、トランザクション終了時に自動クリアされます。
   */
  private static void setTenantId(Connection conn, TenantIdentifier tenantIdentifier) {
    log.trace("[RLS] SET app.tenant_id: tenant={}", tenantIdentifier.value());

    // PreparedStatementでSQLインジェクション対策
    try (var stmt = conn.prepareStatement("SELECT set_config('app.tenant_id', ?, true)")) {
      stmt.setString(1, tenantIdentifier.value());
      stmt.execute();
    } catch (SQLException e) {
      throw new SqlRuntimeException("Failed to set tenant_id", e);
    }
  }
}
```

**参考実装**: [TransactionManager.java:25](../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/datasource/TransactionManager.java#L25)

**重要なポイント**:

#### 1. `is_local = true` の重要性
```sql
SELECT set_config('app.tenant_id', 'xxx', true)
                                          ↑
                                    is_local=true
```

- **`true`**: トランザクション終了時に自動クリア（推奨）
- **`false`**: セッション全体で保持（コネクションプール使用時に危険）

**危険なシナリオ（`false`の場合）**:
```
1. Tenant A のトランザクション開始 → app.tenant_id = "A"
2. トランザクション終了 → app.tenant_id = "A" のまま残る
3. コネクションがプールに戻る
4. Tenant B がそのコネクションを取得
5. app.tenant_id = "A" のまま（Tenant B のデータアクセスがTenant A として実行される！）
```

#### 2. トランザクション開始後に設定
```java
// ❌ 誤り: トランザクション開始前に設定
setTenantId(conn, tenantIdentifier);
conn.setAutoCommit(false);  // この後だとset_configが無効化される

// ✅ 正しい: トランザクション開始後に設定
conn.setAutoCommit(false);
setTenantId(conn, tenantIdentifier);
```

#### 3. PreparedStatementでSQLインジェクション対策
```java
// ❌ 誤り: 文字列結合（SQLインジェクションリスク）
stmt.execute("SELECT set_config('app.tenant_id', '" + tenantId + "', true)");

// ✅ 正しい: PreparedStatement使用
try (var stmt = conn.prepareStatement("SELECT set_config('app.tenant_id', ?, true)")) {
  stmt.setString(1, tenantIdentifier.value());
  stmt.execute();
}
```

**PostgreSQL公式ドキュメント**:
- [Configuration Settings Functions](https://www.postgresql.org/docs/current/functions-admin.html#FUNCTIONS-ADMIN-SET)
- [Row Security Policies](https://www.postgresql.org/docs/current/ddl-rowsecurity.html)

---

## 🔄 実装フロー

### ユーザー作成フローの例

```
┌─────────────────────────────────────────────────────────────┐
│ 1. Controller Layer                                         │
│    - HTTP Request受信                                        │
│    - TenantIdentifierを抽出（URLパスまたはヘッダー）         │
└─────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────┐
│ 2. UseCase Layer (EntryService)                             │
│    - TenantQueryRepository.get(tenantIdentifier)            │
│    - Tenantオブジェクト取得                                  │
└─────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────┐
│ 3. Core Layer (Handler/Service)                             │
│    - ビジネスロジック実行                                    │
│    - Tenantオブジェクトを各Repositoryに渡す                  │
└─────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────┐
│ 4. Repository Layer                                          │
│    - TransactionManager.beginTransaction(db, tenant)         │
│    - PostgreSQLの場合: app.tenant_id設定                     │
│    - userRepository.register(tenant, user)                   │
└─────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────┐
│ 5. Database Layer                                            │
│    - RLSポリシー適用                                         │
│    - テナント分離された行のみアクセス可能                     │
└─────────────────────────────────────────────────────────────┘
```

### コード例

```java
// 1. Controller Layer
@PostMapping("/v1/management/organizations/{orgId}/tenants/{tenantId}/users")
public ResponseEntity<?> registerUser(
    @PathVariable String orgId,
    @PathVariable String tenantId,
    @RequestBody UserRequest request) {
  TenantIdentifier tenantIdentifier = new TenantIdentifier(tenantId);
  // EntryServiceに委譲
  return userManagementEntryService.register(tenantIdentifier, request);
}

// 2. UseCase Layer
public class UserManagementEntryService {
  public Response register(TenantIdentifier tenantIdentifier, UserRequest request) {
    // Tenantオブジェクト取得
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    // Handlerにテナントを渡す
    UserManagementResult result = userManagementHandler.register(tenant, request);
    return result.toResponse();
  }
}

// 3. Core Layer (Handler)
public class UserManagementHandler {
  public UserManagementResult register(Tenant tenant, UserRequest request) {
    // Serviceにテナントを渡す
    userRegistrationService.execute(tenant, request);

    // Repositoryにテナントを渡す
    userCommandRepository.register(tenant, user);
    return UserManagementResult.success();
  }
}

// 4. Repository Layer (Adapter)
public class UserCommandDataSource implements UserCommandRepository {
  @Override
  public void register(Tenant tenant, User user) {
    // トランザクション開始（PostgreSQLの場合app.tenant_id設定）
    TransactionManager.beginTransaction(databaseType, tenant.identifier());

    // SQL実行（RLSが自動適用される）
    String sql = "INSERT INTO idp_user (id, tenant_id, username, ...) VALUES (?, ?, ?, ...)";
    sqlExecutor.insert(sql, ...);

    TransactionManager.commitTransaction();
  }
}
```

---

## 🧪 テスト時の注意事項

### RLS動作確認

PostgreSQLのRLSが正しく動作しているか確認する方法:

```java
@Test
void testTenantIsolation() {
  // Tenant A でユーザー作成
  TenantIdentifier tenantA = new TenantIdentifier("tenant-a-uuid");
  Tenant tenantAObj = tenantRepository.get(tenantA);
  User userA = new User(...);
  userRepository.register(tenantAObj, userA);

  // Tenant B でユーザー検索
  TenantIdentifier tenantB = new TenantIdentifier("tenant-b-uuid");
  Tenant tenantBObj = tenantRepository.get(tenantB);

  // ✅ Tenant Bからはユーザーが見えないことを確認
  assertThrows(UserNotFoundException.class, () -> {
    userRepository.get(tenantBObj, userA.identifier());
  });
}
```

### RLSポリシー確認クエリ

```sql
-- 開発環境でRLSが正しく設定されているか確認
SELECT
    schemaname,
    tablename,
    policyname,
    qual as policy_condition
FROM pg_policies
WHERE schemaname = 'public'
  AND policyname = 'tenant_isolation_policy'
ORDER BY tablename;

-- 出力例:
-- schemaname | tablename       | policyname              | policy_condition
-- -----------|-----------------|-------------------------|----------------------------------
-- public     | tenant          | tenant_isolation_policy | (id = current_setting('app.tenant_id')::uuid)
-- public     | idp_user        | tenant_isolation_policy | (tenant_id = current_setting('app.tenant_id')::uuid)
-- public     | client_configuration | tenant_isolation_policy | (tenant_id = current_setting('app.tenant_id')::uuid)
```

---

## 📋 実装チェックリスト

新しいドメインモデルを追加する際のチェックリスト:

- [ ] **Repository Interface**: すべてのメソッドで第一引数に`Tenant`を追加
  ```java
  // ✅
  void register(Tenant tenant, Entity entity);
  Entity find(Tenant tenant, EntityIdentifier id);
  ```

- [ ] **DDL**: テーブルに`tenant_id`カラムを追加
  ```sql
  CREATE TABLE new_entity (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,  -- ← 必須
    ...
  );
  ```

- [ ] **RLS Policy**: テーブルにRLSポリシーを設定
  ```sql
  ALTER TABLE new_entity ENABLE ROW LEVEL SECURITY;
  CREATE POLICY tenant_isolation_policy ON new_entity
    USING (tenant_id = current_setting('app.tenant_id')::uuid);
  ALTER TABLE new_entity FORCE ROW LEVEL SECURITY;
  ```

- [ ] **Foreign Key**: `tenant_id`に外部キー制約を追加（オプション）
  ```sql
  ALTER TABLE new_entity
    ADD CONSTRAINT fk_new_entity_tenant
    FOREIGN KEY (tenant_id) REFERENCES tenant(id);
  ```

- [ ] **Index**: `tenant_id`にインデックスを追加（パフォーマンス向上）
  ```sql
  CREATE INDEX idx_new_entity_tenant_id ON new_entity(tenant_id);
  ```

- [ ] **Test**: テナント分離のテストケースを追加
  - 異なるテナントからのアクセスで404が返ることを確認
  - RLSポリシーが正しく機能することを確認

---

## 🚨 よくある間違い

### 1. Tenantの省略

```java
// ❌ 誤り: Repository呼び出しでTenantを渡さない
User user = userRepository.find(userId);

// ✅ 正しい: 常にTenantを渡す
Tenant tenant = tenantRepository.get(tenantIdentifier);
User user = userRepository.find(tenant, userId);
```

### 2. TenantIdentifierとTenantの混同

```java
// ❌ 誤り: TenantIdentifierをそのまま使う
userRepository.register(tenantIdentifier, user);  // コンパイルエラー

// ✅ 正しい: TenantオブジェクトをRepositoryから取得
Tenant tenant = tenantRepository.get(tenantIdentifier);
userRepository.register(tenant, user);
```

### 3. OrganizationRepositoryでのTenant渡し

```java
// ❌ 誤り: OrganizationRepositoryにTenantを渡す
organizationRepository.get(tenant, orgIdentifier);  // コンパイルエラー

// ✅ 正しい: OrganizationRepositoryはTenant不要
Organization org = organizationRepository.get(orgIdentifier);
```

### 4. RLS設定のis_local=false

```java
// ❌ 誤り: セッション全体で保持（コネクションプール使用時に危険）
stmt.execute("SELECT set_config('app.tenant_id', '" + tenantId + "', false)");

// ✅ 正しい: トランザクションローカル
stmt.execute("SELECT set_config('app.tenant_id', ?, true)");
```

---

## 🔗 関連ドキュメント

**概念・基礎**:
- [concept-01: マルチテナント](../../content_03_concepts/01-foundation/concept-01-multi-tenant.md) - マルチテナントの設計思想
- [how-to-02: 組織初期化](../../content_05_how-to/how-to-02-organization-initialization.md) - 組織作成手順
- [how-to-03: テナントセットアップ](../../content_05_how-to/how-to-03-tenant-setup.md) - テナント作成手順

**実装詳細**:
- [impl-03: トランザクション](./impl-03-transaction.md) - TransactionManagerの詳細
- [impl-02: マルチデータソース](./impl-02-multi-datasource.md) - データソース管理
- [impl-10: Repository実装](./impl-10-repository-implementation.md) - Repository層の実装パターン

**設定**:
- [05-configuration/tenant.md](../05-configuration/tenant.md) - Tenant設定ガイド
- [02-control-plane/04-organization-level-api.md](../02-control-plane/04-organization-level-api.md) - 組織レベルAPI

**参考実装クラス**:
- [Tenant.java](../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/multi_tenancy/tenant/Tenant.java)
- [Organization.java](../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/multi_tenancy/organization/Organization.java)
- [TransactionManager.java](../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/datasource/TransactionManager.java)
- [UserCommandRepository.java](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/identity/repository/UserCommandRepository.java)

---

**最終更新**: 2025-12-07
**難易度**: ⭐⭐⭐ (中級)
