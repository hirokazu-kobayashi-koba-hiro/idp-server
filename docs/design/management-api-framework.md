---
title: "Management API Design Framework"
author: "Claude Code"
created: "2025-01-15"
updated: "2025-01-15"
status: "draft"
related_issues: ["#409", "#442"]
reviewers: []
---

# Management API Design Framework

## 概要

idp-serverにおける管理系API開発のための包括的な設計フレームワーク。PR #434の組織レベルテナント管理API実装から抽出したアーキテクチャパターンをベースに、システムレベル権限（DefaultAdminPermission）のみを使用する簡素化されたアプローチを提供する。

## 設計原則

### 1. 統一権限管理アプローチ

**変更前**: 2層権限管理
- システムレベル権限 (`DefaultAdminPermission`)

**変更後**: 単一権限管理
- **システムレベル権限のみ** (`DefaultAdminPermission`)
- 権限管理の簡素化
- 権限の競合・重複の回避

### 2. アクセス制御の簡素化

**変更前**: 4層アクセス制御
1. 組織メンバーシップ検証
2. テナントアクセス検証
3. 組織-テナント関係検証
4. 権限検証

**変更後**: 2層アクセス制御
1. **テナントアクセス検証**: ユーザーが対象テナントにアクセス可能か
2. **権限検証**: ユーザーが必要なDefaultAdminPermissionを持っているか

### 3. Hexagonal Architecture準拠

```
Controller → Use-Cases → Core → Adapter
             ↑ Control-Plane (契約定義のみ)
```

## 管理APIテンプレート

### API Interface Template

```java
public interface {Resource}ManagementApi {
    // 権限マッピング - DefaultAdminPermissionのみ使用
    default AdminPermissions getRequiredPermissions(String method) {
        Map<String, AdminPermissions> map = new HashMap<>();
        map.put("create", new AdminPermissions(Set.of(DefaultAdminPermission.{RESOURCE}_CREATE)));
        map.put("findList", new AdminPermissions(Set.of(DefaultAdminPermission.{RESOURCE}_READ)));
        map.put("get", new AdminPermissions(Set.of(DefaultAdminPermission.{RESOURCE}_READ)));
        map.put("update", new AdminPermissions(Set.of(DefaultAdminPermission.{RESOURCE}_UPDATE)));
        map.put("delete", new AdminPermissions(Set.of(DefaultAdminPermission.{RESOURCE}_DELETE)));
        return map.get(method);
    }

    // 標準CRUD操作
    {Resource}ManagementResponse create(
        TenantIdentifier tenantId,
        User operator,
        {Resource}Request request,
        RequestAttributes requestAttributes,
        boolean dryRun);

    {Resource}ManagementResponse findList(
        TenantIdentifier tenantId,
        User operator,
        {Resource}Queries queries,
        RequestAttributes requestAttributes);

    {Resource}ManagementResponse get(
        TenantIdentifier tenantId,
        User operator,
        {Resource}Identifier id,
        RequestAttributes requestAttributes);

    {Resource}ManagementResponse update(
        TenantIdentifier tenantId,
        User operator,
        {Resource}Identifier id,
        {Resource}Request request,
        RequestAttributes requestAttributes,
        boolean dryRun);

    {Resource}ManagementResponse delete(
        TenantIdentifier tenantId,
        User operator,
        {Resource}Identifier id,
        RequestAttributes requestAttributes,
        boolean dryRun);
}
```

### EntryService Implementation Template

```java
@Transaction
public class {Resource}ManagementEntryService implements {Resource}ManagementApi {

    // 標準依存関係
    private final {Resource}CommandRepository commandRepository;
    private final {Resource}QueryRepository queryRepository;
    private final TenantQueryRepository tenantQueryRepository;
    private final AuditLogWriters auditLogWriters;

    @Override
    public {Resource}ManagementResponse create(
        TenantIdentifier tenantId,
        User operator,
        {Resource}Request request,
        RequestAttributes requestAttributes,
        boolean dryRun) {

        // 1. 権限解決
        AdminPermissions permissions = getRequiredPermissions("create");

        // 2. テナント存在確認
        Tenant tenant = tenantQueryRepository.get(tenantId);

        // 3. 簡素化されたアクセス制御
        AccessControlResult accessResult = verifyAccess(operator, tenantId, permissions);
        if (!accessResult.isSuccess()) {
            return accessResult.toErrorResponse();
        }

        // 4. 入力検証
        {Resource}RequestValidator validator = new {Resource}RequestValidator(request);
        {Resource}RequestValidationResult validationResult = validator.validate();
        if (!validationResult.isValid()) {
            return validationResult.toErrorResponse();
        }

        // 5. ビジネスルール検証
        {Resource}ManagementVerifier verifier = new {Resource}ManagementVerifier(commandRepository);
        {Resource}ManagementVerificationResult verificationResult = verifier.verify(context);
        if (!verificationResult.isValid()) {
            return verificationResult.toErrorResponse();
        }

        // 6. 監査ログ
        AuditLog auditLog = AuditLogCreator.create(operator, "{resource}_create", request);

        // 7. Dry-run対応
        if (dryRun) {
            return context.toResponse();
        }

        // 8. 永続化操作
        {Resource} new{Resource} = context.new{Resource}();
        commandRepository.register(new{Resource});
        auditLogWriters.write(auditLog);

        return context.toResponse();
    }

    // 簡素化されたアクセス制御メソッド
    private AccessControlResult verifyAccess(User operator, TenantIdentifier tenantId, AdminPermissions permissions) {
        // テナントアクセス検証
        if (!operator.hasAccessToTenant(tenantId)) {
            return AccessControlResult.forbidden("Tenant access denied");
        }

        // 権限検証
        if (!permissions.includesAll(operator.permissionsAsSet())) {
            return AccessControlResult.forbidden("Insufficient permissions");
        }

        return AccessControlResult.success();
    }
}
```

## Permission Enum Extensions

### DefaultAdminPermission拡張パターン

```java
// DefaultAdminPermissionに新しいリソース管理権限を追加
public enum DefaultAdminPermission {
    // 既存権限
    TENANT_CREATE("tenant:create", "Create a tenant"),
    TENANT_READ("tenant:read", "Read tenant information"),
    // ...

    // 新しいリソース管理権限
    CLIENT_CREATE("client:create", "Create a client"),
    CLIENT_READ("client:read", "Read client information"),
    CLIENT_UPDATE("client:update", "Update client"),
    CLIENT_DELETE("client:delete", "Delete client"),

    USER_CREATE("user:create", "Create a user"),
    USER_READ("user:read", "Read user information"),
    USER_UPDATE("user:update", "Update user"),
    USER_DELETE("user:delete", "Delete user"),
    USER_INVITE("user:invite", "Invite a user"),
    USER_SUSPEND("user:suspend", "Suspend user account"),

    SECURITY_EVENT_READ("security-event:read", "Read security events"),
    AUDIT_LOG_READ("audit-log:read", "Read audit logs"),

    CONFIG_READ("config:read", "Read configuration"),
    CONFIG_UPDATE("config:update", "Update configuration"),

    // ユーティリティメソッド
    public static Set<DefaultAdminPermission> findByResource(String resource) {
        return Arrays.stream(values())
            .filter(p -> p.value.startsWith(resource + ":"))
            .collect(Collectors.toSet());
    }

    public static Set<DefaultAdminPermission> findCreatePermissions() {
        return Arrays.stream(values())
            .filter(p -> p.value.endsWith(":create"))
            .collect(Collectors.toSet());
    }
}
```

## 適用可能な管理系API

### 1. Client Management API

```java
public interface ClientManagementApi {
    // DefaultAdminPermission.CLIENT_* を使用
    ClientManagementResponse create(...);
    ClientManagementResponse findList(...);
    ClientManagementResponse get(...);
    ClientManagementResponse update(...);
    ClientManagementResponse delete(...);
}

@Transaction
public class ClientManagementEntryService implements ClientManagementApi {
    // 簡素化されたアクセス制御
    // テナントアクセス + CLIENT_* 権限のみ
}
```

### 2. User Management API

```java
public interface UserManagementApi {
    // DefaultAdminPermission.USER_* を使用
    UserManagementResponse create(...);
    UserManagementResponse invite(...);  // USER_INVITE権限
    UserManagementResponse suspend(...); // USER_SUSPEND権限
    UserManagementResponse findList(...);
    UserManagementResponse get(...);
    UserManagementResponse update(...);
    UserManagementResponse delete(...);
}
```

### 3. Security Event Management API

```java
public interface SecurityEventManagementApi {
    // 読み取り専用操作
    // DefaultAdminPermission.SECURITY_EVENT_READ を使用
    SecurityEventManagementResponse findList(...);
    SecurityEventManagementResponse get(...);
}
```

### 4. Configuration Management API

```java
public interface ConfigManagementApi {
    // DefaultAdminPermission.CONFIG_* を使用
    ConfigManagementResponse get(...);
    ConfigManagementResponse update(...);
}
```

## Database Access Patterns

### Repository Pattern (簡素化版)

```java
// Command Repository - 書き込み操作
public interface {Resource}CommandRepository {
    void register({Resource} {resource});
    void update({Resource} {resource});
    void delete({Resource}Identifier {resource}Identifier);
}

// Query Repository - 読み取り操作
public interface {Resource}QueryRepository {
    {Resource} get(TenantIdentifier tenantId, {Resource}Identifier {resource}Identifier);
    {Resource} find(TenantIdentifier tenantId, {Resource}Identifier {resource}Identifier);
    List<{Resource}> findList(TenantIdentifier tenantId, {Resource}Queries queries);
}
```

### SQL Executor Pattern

```java
public interface {Resource}SqlExecutor {
    void insert({Resource} {resource});
    void update({Resource} {resource});
    void delete({Resource}Identifier {resource}Identifier);
    Map<String, String> selectOne(TenantIdentifier tenantId, {Resource}Identifier {resource}Identifier);
    List<Map<String, String>> selectList(TenantIdentifier tenantId, {Resource}Queries queries);
}

// Database-specific implementations
class PostgresqlExecutor implements {Resource}SqlExecutor { ... }
class MysqlExecutor implements {Resource}SqlExecutor { ... }
```

## Test Strategy

### E2E Test Template

```javascript
describe("{resource} management api", () => {
    describe("success pattern", () => {
        it("crud operations", async () => {
            // 1. 認証 - management権限付きトークン取得
            const tokenResponse = await requestToken({
                scope: "management account",
            });

            // 2. CRUD操作シーケンス
            const createResponse = await postWithJson({...});
            expect(createResponse.status).toBe(201);

            const listResponse = await get({...});
            expect(listResponse.status).toBe(200);
            expect(listResponse.data).toHaveProperty("list");

            const detailResponse = await get({...});
            expect(detailResponse.status).toBe(200);

            const updateResponse = await putWithJson({...});
            expect(updateResponse.status).toBe(200);

            const deleteResponse = await deletion({...});
            expect(deleteResponse.status).toBe(204);
        });

        it("dry run functionality", async () => {
            // dry-runテスト
            const dryRunResponse = await postWithJson({
                url: `${baseUrl}?dry_run=true`,
                // ...
            });
            expect(dryRunResponse.status).toBe(200);

            // 実際の変更が発生していないことを確認
            const listResponse = await get({...});
            expect(listResponse.data.list).not.toContain(testResource);
        });
    });

    describe("error cases", () => {
        it("unauthorized access", async () => {
            // 権限不足のテスト
        });

        it("invalid tenant", async () => {
            // 不正テナントアクセスのテスト
        });

        const invalidRequestCases = [
            // 不正リクエストのテストケース
        ];
        test.each(invalidRequestCases)("invalid request: %s", async (...) => {
            // バリデーションエラーのテスト
        });
    });
});
```

## Package Organization

### 簡素化されたパッケージ構造

```
control_plane/
├── base/
│   └── definition/
│       └── DefaultAdminPermission.java    # 統一権限管理
├── management/                            # 管理API定義
│   ├── tenant/
│   │   ├── TenantManagementApi.java
│   │   ├── io/
│   │   ├── validator/
│   │   └── verifier/
│   ├── client/
│   │   └── ClientManagementApi.java
│   ├── user/
│   │   └── UserManagementApi.java
│   └── security_event/
│       └── SecurityEventManagementApi.java
└── access/                                # 簡素化されたアクセス制御
    ├── AccessControlVerifier.java
    └── AccessControlResult.java

usecases/control_plane/
└── management/                            # 管理API実装
    ├── TenantManagementEntryService.java
    ├── ClientManagementEntryService.java
    ├── UserManagementEntryService.java
    └── SecurityEventManagementEntryService.java
```

## 実装優先順位

### Phase 1: 基盤整備
1. **DefaultAdminPermission拡張**: 新しいリソース管理権限の追加
2. **AccessControlVerifier簡素化**: 2層アクセス制御の実装
3. **テンプレートクラス作成**: 再利用可能なベースクラス

### Phase 2: Core APIs
1. **Client Management API**: 既存クライアント管理の拡張
2. **User Management API**: ユーザー管理機能の統合
3. **Security Event Management API**: セキュリティイベント閲覧

### Phase 3: Advanced APIs
1. **Configuration Management API**: テナント設定管理
2. **Audit Log Management API**: 監査ログ管理
3. **System Health API**: システム状態監視

## 利点

### 1. シンプルな権限管理
- 単一権限システムによる複雑性の軽減
- 権限競合・重複の回避
- 管理・理解の容易さ

### 2. 一貫したアーキテクチャ
- 全管理APIで統一されたパターン
- 予測可能な動作
- 保守性の向上

### 3. 拡張性
- 新しいリソース管理APIの容易な追加
- プラグイン可能な検証・変換システム
- 段階的な機能拡張

### 4. セキュリティ
- 適切なアクセス制御
- 包括的な監査証跡
- Dry-run による安全な変更プレビュー

## 結論

この設計フレームワークは、idp-serverプラットフォームで一貫性、セキュリティ、保守性を維持しながら管理系APIを開発するための包括的なガイドラインを提供する。システムレベル権限のみの使用により、複雑性を軽減しつつ必要な機能を実現する。