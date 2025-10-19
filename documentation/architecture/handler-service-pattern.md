# Handler/Service Pattern - Management API Design Guide

## Overview

Management API全般に適用可能な3層アーキテクチャパターン。
各層の責務を明確化し、関心事の分離を実現する。

**適用対象**:
- User Management API ✅ 実装済み (10操作)
- Authentication Configuration API
- Client Configuration API
- その他すべてのManagement API

**設計目標**:
- 横断的関心事（権限、監査ログ、トランザクション）の一元管理
- オペレーション固有ロジックの明確な分離
- テスト容易性の向上
- 複数オペレーション対応（Strategy Pattern）

## Architecture Diagrams

### System-Level API (システムレベル)

```
┌─────────────────────────────────────────────────────────────┐
│ EntryService (例: UserManagementEntryService)               │
│ 責務: トランザクション境界、監査ログ、例外→レスポンス変換   │
├─────────────────────────────────────────────────────────────┤
│ 1. Handler呼び出し                                          │
│ 2. 監査ログ記録 (@Async別トランザクション)                 │
│ 3. 例外があれば再スロー (ロールバックトリガー)              │
│ 4. レスポンス返却                                           │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ Handler (例: UserManagementHandler)                         │
│ 責務: 横断的関心事 (Tenant取得、権限検証)                   │
├─────────────────────────────────────────────────────────────┤
│ 1. Tenant取得 (全オペレーション共通)                       │
│ 2. 権限検証 (全オペレーション共通)                         │
│ 3. Service選択 (method → Service)                          │
│ 4. Service実行                                              │
│ 5. 例外キャッチ → Result化                                 │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ Service (例: UserCreationService)                           │
│ 責務: オペレーション固有のビジネスロジック                  │
├─────────────────────────────────────────────────────────────┤
│ 1. リクエストバリデーション                                │
│ 2. Context作成 (DTO → ドメインモデル変換)                  │
│ 3. ビジネスルール検証                                       │
│ 4. Repository操作 (永続化)                                 │
│ 5. イベント発行 (SecurityEvent)                            │
│ 6. Result返却                                               │
└─────────────────────────────────────────────────────────────┘
```

### Organization-Level API (組織レベル)

```
┌─────────────────────────────────────────────────────────────┐
│ EntryService (例: OrgUserManagementEntryService)            │
│ 責務: トランザクション境界、監査ログ                        │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ OrgHandler (例: OrgUserManagementHandler)                   │
│ 責務: 組織アクセス制御 + 横断的関心事                       │
├─────────────────────────────────────────────────────────────┤
│ 1. 権限取得 (オペレーション → AdminPermissions)            │
│ 2. Organization取得                                         │
│ 3. Tenant取得                                               │
│ 4. 組織アクセス制御 (4ステップ検証)                        │
│    - 組織メンバーシップ検証                                │
│    - テナントアクセス検証                                   │
│    - 組織-テナント関係検証                                 │
│    - 権限検証                                               │
│ 5. Service選択・実行                                       │
│ 6. 例外キャッチ → Result化                                 │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ Service (システムレベルと同じServiceを再利用)              │
└─────────────────────────────────────────────────────────────┘
```

**組織レベルの特徴**:
- **Serviceは再利用**: システムレベルと同じServiceを使う
- **Handler層で組織アクセス制御**: `OrganizationAccessVerifier`で4ステップ検証
- **Organization/Tenantパラメータ**: EntryServiceがOrganizationIdentifierとTenantIdentifier両方を受け取る

---

## Layer Responsibilities

### EntryService層

**目的**: トランザクション境界と監査ログの管理

#### ✅ やること

- **トランザクション境界の定義** (`@Transaction`)
- **Handler呼び出し**
- **監査ログ記録**
  - 成功・失敗の両方を記録
  - `@Async`で別トランザクション化
  - API失敗時でもログ保存を保証
- **例外の再スロー**
  - `TenantAwareEntryServiceProxy`のロールバックトリガー
- **最終レスポンス返却**

#### ❌ やらないこと

- ビジネスロジック
- バリデーション
- Tenant取得
- 権限検証
- Repository操作

#### コード例 (システムレベル)

```java
@Override
public UserManagementResponse create(
    TenantIdentifier tenantIdentifier,
    User operator,
    OAuthToken oAuthToken,
    UserRegistrationRequest request,
    RequestAttributes requestAttributes,
    boolean dryRun) {

  // 1. Handler呼び出し
  UserManagementResult result = handler.handle(
      "create", tenantIdentifier, operator, oAuthToken, request, requestAttributes, dryRun);

  // 2. 監査ログ記録 (成功・失敗両方)
  if (result.hasException()) {
    AuditLog auditLog = AuditLogCreator.createOnError(
        "UserManagementApi.create", result.tenant(), operator, oAuthToken,
        result.getException(), requestAttributes);
    auditLogPublisher.publish(auditLog);
    throw result.getException();
  }

  AuditLog auditLog = AuditLogCreator.create(
      "UserManagementApi.create", result.tenant(), operator, oAuthToken,
      (UserRegistrationContext) result.context(), requestAttributes);
  auditLogPublisher.publish(auditLog);

  // 3. レスポンス返却
  return result.toResponse(dryRun);
}
```

#### コード例 (組織レベル)

```java
@Override
public UserManagementResponse create(
    OrganizationIdentifier organizationIdentifier,
    TenantIdentifier tenantIdentifier,  // 組織レベルは2つのIdentifierを受け取る
    User operator,
    OAuthToken oAuthToken,
    UserRegistrationRequest request,
    RequestAttributes requestAttributes,
    boolean dryRun) {

  // OrgHandlerを呼び出し
  UserManagementResult result = handler.handle(
      "create", organizationIdentifier, tenantIdentifier, operator, oAuthToken,
      request, requestAttributes, dryRun);

  // 以下、システムレベルと同じ
  // ...
}
```

---

### Handler層

#### システムレベルHandler

**目的**: 横断的関心事の処理とオペレーションルーティング

##### ✅ やること

- **横断的関心事の処理**
  - **Tenant取得**: 全オペレーション共通、監査ログ用に必須
  - **権限検証**: 全オペレーション共通
- **オペレーションルーティング**
  - methodからServiceを選択 (Strategy Pattern)
  - Serviceに必要な共通データ（Tenant）を渡す
- **例外の統一的ハンドリング**
  - `ManagementApiException`をキャッチ
  - `Result`に変換 (EntryServiceで成功/失敗判定可能にする)

##### ❌ やらないこと

- オペレーション固有のビジネスロジック
- Repository操作 (Tenant取得以外)
- レスポンス生成
- 監査ログ記録

##### コード例

```java
public UserManagementResult handle(
    String method,
    TenantIdentifier tenantIdentifier,
    User operator,
    OAuthToken oAuthToken,
    Object request,
    RequestAttributes requestAttributes,
    boolean dryRun) {

  Tenant tenant = null;
  try {
    // 1. Tenant取得 (全オペレーション共通)
    tenant = tenantQueryRepository.get(tenantIdentifier);

    // 2. 権限検証 (全オペレーション共通)
    AdminPermissions requiredPermissions = managementApi.getRequiredPermissions(method);
    permissionVerifier.verify(operator, requiredPermissions);

    // 3. Service選択
    UserManagementService<?> service = services.get(method);
    if (service == null) {
      throw new IllegalArgumentException("Unsupported operation method: " + method);
    }

    // 4. Service実行 (Genericsで型安全に)
    return executeService(service, tenant, operator, oAuthToken, request, requestAttributes, dryRun);

  } catch (ManagementApiException e) {
    // 5. 例外キャッチ → Result化
    return UserManagementResult.error(tenant, e);
  }
}

private <T> UserManagementResult executeService(
    UserManagementService<T> service,
    Tenant tenant,
    User operator,
    OAuthToken oAuthToken,
    Object request,
    RequestAttributes requestAttributes,
    boolean dryRun) {
  @SuppressWarnings("unchecked")
  T typedRequest = (T) request;
  return service.execute(tenant, operator, oAuthToken, typedRequest, requestAttributes, dryRun);
}
```

#### 組織レベルHandler

**目的**: 組織アクセス制御 + システムレベルHandlerの責務

##### ✅ やること (システムレベルに追加)

- **Organization取得**
- **組織アクセス制御 (4ステップ検証)**
  1. 組織メンバーシップ検証 (`operator`が組織メンバーか)
  2. テナントアクセス検証 (`operator`がテナントにアクセス可能か)
  3. 組織-テナント関係検証 (組織がテナントを管理しているか)
  4. 権限検証 (`operator`が必要な権限を持つか)

##### コード例

```java
public UserManagementResult handle(
    String operation,
    OrganizationIdentifier organizationIdentifier,
    TenantIdentifier tenantIdentifier,
    User operator,
    OAuthToken oAuthToken,
    Object request,
    RequestAttributes requestAttributes,
    boolean dryRun) {

  Tenant tenant = null;
  try {
    // 1. 権限取得
    AdminPermissions permissions = entryService.getRequiredPermissions(operation);

    // 2. Organization & Tenant取得
    Organization organization = organizationRepository.get(organizationIdentifier);
    tenant = tenantQueryRepository.get(tenantIdentifier);

    // 3. 組織アクセス制御 (4ステップ検証)
    OrganizationAccessControlResult accessResult =
        organizationAccessVerifier.verify(
            organization, tenant, operator, oAuthToken, permissions);

    // 4. Service選択・実行
    UserManagementService<?> service = services.get(operation);
    if (service == null) {
      throw new IllegalArgumentException("Unsupported operation: " + operation);
    }

    return executeService(service, tenant, operator, oAuthToken, request, requestAttributes, dryRun);

  } catch (ManagementApiException e) {
    return UserManagementResult.error(tenant, e);
  }
}
```

---

### Service層

**目的**: オペレーション固有のビジネスロジック実行

#### ✅ やること

- **オペレーション固有のビジネスロジック**
  - **リクエストバリデーション**
    - 形式チェック
    - 必須項目チェック
    - 失敗時: `InvalidRequestException`
  - **Context作成**
    - `ContextCreator`使用
    - DTO → ドメインモデル変換
  - **ビジネスルール検証**
    - `Verifier`使用
    - ドメインルールチェック
    - 失敗時: 各種検証例外
  - **Repository操作**
    - データ永続化 (CRUD)
  - **イベント発行**
    - `SecurityEvent`等のドメインイベント
- **Result返却**
  - `tenant`, `context`を含む成功Result

#### ❌ やらないこと

- 権限検証 (Handler層の責務)
- Tenant取得 (Handler層から渡される)
- 例外のキャッチ (上位層に伝播)
- 監査ログ記録

#### コード例

```java
public class UserCreationService implements UserManagementService<UserRegistrationRequest> {

  private final UserCommandRepository userCommandRepository;
  private final PasswordEncodeDelegation passwordEncodeDelegation;
  private final UserRegistrationVerifier verifier;
  private final ManagementEventPublisher managementEventPublisher;

  @Override
  public UserManagementResult execute(
      Tenant tenant,  // Handler層から渡される
      User operator,
      OAuthToken oAuthToken,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // 1. リクエストバリデーション
    new UserRegistrationRequestValidator(request, dryRun).validate();

    // 2. Context作成
    UserRegistrationContextCreator contextCreator =
        new UserRegistrationContextCreator(tenant, request, dryRun, passwordEncodeDelegation);
    UserRegistrationContext context = contextCreator.create();

    // 3. ビジネスルール検証
    verifier.verify(context);

    // 4. Dry-run check
    if (dryRun) {
      return UserManagementResult.success(tenant, context, context.toResponse());
    }

    // 5. Repository操作
    userCommandRepository.register(tenant, context.user());

    // 6. イベント発行
    managementEventPublisher.publish(
        tenant, operator, context.user(), oAuthToken,
        DefaultSecurityEventType.user_create.toEventType(), requestAttributes);

    return UserManagementResult.success(tenant, context, context.toResponse());
  }
}
```

---

## Context Creator Pattern

**目的**: リクエストDTO → ドメインモデル変換の責務分離

### 設計原則

1. **直接値取得パターン**: `JsonConverter.read()`を使わず、`UserRegistrationRequest`のメソッドから直接値を取得
2. **イミュータブル更新**: Userオブジェクトを段階的に更新
3. **条件付き更新**: リクエストに含まれるフィールドのみ更新

### なぜJsonConverter.read()を使わないか

**問題**: フィールド名マッピングの不一致
- JSON key: `current_tenant_id` (snake_case)
- Javaフィールド: `String currentTenant` (camelCase)
- `JsonConverter.snakeCaseInstance().read()`でもマッピングに失敗

**解決策**: `UserRegistrationRequest`のメソッドで直接値を取得

### コード例: UserTenantAssignmentsUpdateContextCreator

```java
public class UserTenantAssignmentsUpdateContextCreator {

  Tenant tenant;
  User before;
  UserRegistrationRequest request;
  boolean dryRun;

  public UserUpdateContext create() {
    User updated = before;

    // Update assigned tenants if provided
    if (request.containsKey("assigned_tenants")) {
      updated = updated.setAssignedTenants(request.assignedTenants());
    }

    // Update current tenant if provided
    if (request.containsKey("current_tenant_id") && request.currentTenant() != null) {
      updated =
          updated.setCurrentTenantId(
              new org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier(
                  request.currentTenant()));
    }

    return new UserUpdateContext(tenant, before, updated, dryRun);
  }
}
```

### 実装パターン

1. **before状態を保持**: Context内で変更前後を比較可能にする
2. **段階的更新**: `User updated = before` → 条件付き更新 → `return new Context(tenant, before, updated, dryRun)`
3. **nullチェック**: `request.containsKey()` && `request.value() != null`

---

## Key Design Decisions

### 1. Tenant取得の責務配置

**決定**: Handler層で取得

**理由**:
- 全オペレーションで必要 (共通処理)
- 監査ログ記録に必須 (失敗時でも必要)
- Service層でのTenant重複取得を防止

**実装**: ✅ Phase 2で完了

### 2. 権限検証の責務配置

**決定**: Handler層で検証

**理由**:
- 全オペレーションで必要 (共通処理)
- Service層を純粋なビジネスロジックに保つ
- 権限エラーも監査ログに記録する必要がある

### 3. 例外ハンドリング戦略

**決定**: Service層で例外スロー、Handler層でキャッチしてResult化、EntryService層で再スロー

**理由**:
- トランザクションロールバックには例外が必須
- 監査ログ記録には例外情報が必要
- Result-Exception Hybridパターンで両立

**フロー**:
```
Service: 例外スロー
    ↓
Handler: catch → Result.error(tenant, exception)
    ↓
EntryService: result.hasException() → throw exception
    ↓
TenantAwareEntryServiceProxy: catch → rollbackTransaction()
```

### 4. 監査ログの記録タイミング

**決定**: EntryService層で記録 (例外再スロー前)

**理由**:
- トランザクション分離 (`@Async`別トランザクション)
- API失敗時でもログ保存を保証
- 成功・失敗の両方を記録

**実装**: ✅ Phase 2で完了

### 5. 組織レベルとシステムレベルのService再利用

**決定**: 同じServiceを両レベルで共有

**理由**:
- ビジネスロジック（バリデーション、永続化）は同じ
- アクセス制御はHandler層で分離
- コード重複を避ける

---

## Implementation Guide

### 新しいManagement APIへの適用手順

#### Step 1: 基本クラス定義

```java
// 1. Result class
public class {Domain}ManagementResult {
  private final Tenant tenant;
  private final {Domain}Context context;
  private final ManagementApiException exception;
  private final Object response;

  public static {Domain}ManagementResult success(Tenant tenant, {Domain}Context context, Object response) { ... }
  public static {Domain}ManagementResult error(Tenant tenant, ManagementApiException exception) { ... }
}

// 2. Service interface
public interface {Domain}ManagementService<T> {
  {Domain}ManagementResult execute(
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      T request,
      RequestAttributes requestAttributes,
      boolean dryRun);
}

// 3. Management API interface
public interface {Domain}ManagementApi {
  AdminPermissions getRequiredPermissions(String method);
}
```

#### Step 2: Handler実装

```java
public class {Domain}ManagementHandler {
  private final Map<String, {Domain}ManagementService<?>> services;
  private final PermissionVerifier permissionVerifier;
  private final {Domain}ManagementApi managementApi;
  private final TenantQueryRepository tenantQueryRepository;

  public {Domain}ManagementResult handle(
      String method,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      Object request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    Tenant tenant = null;
    try {
      tenant = tenantQueryRepository.get(tenantIdentifier);
      AdminPermissions requiredPermissions = managementApi.getRequiredPermissions(method);
      permissionVerifier.verify(operator, requiredPermissions);

      {Domain}ManagementService<?> service = services.get(method);
      if (service == null) {
        throw new IllegalArgumentException("Unsupported operation method: " + method);
      }

      return executeService(service, tenant, operator, oAuthToken, request, requestAttributes, dryRun);
    } catch (ManagementApiException e) {
      return {Domain}ManagementResult.error(tenant, e);
    }
  }
}
```

#### Step 3: Service実装

各オペレーション（create, update, delete等）ごとに実装:

```java
public class {Domain}CreationService implements {Domain}ManagementService<{Domain}RegistrationRequest> {

  @Override
  public {Domain}ManagementResult execute(
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      {Domain}RegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // 1. Validation
    new {Domain}RegistrationRequestValidator(request, dryRun).validate();

    // 2. Context creation
    {Domain}RegistrationContext context =
        new {Domain}RegistrationContextCreator(tenant, request, dryRun).create();

    // 3. Business rule verification
    verifier.verify(context);

    // 4. Repository operation (if not dry-run)
    if (!dryRun) {
      commandRepository.register(tenant, context.entity());
    }

    // 5. Event publishing
    eventPublisher.publish(tenant, operator, context.entity(), oAuthToken, ...);

    return {Domain}ManagementResult.success(tenant, context, context.toResponse());
  }
}
```

#### Step 4: EntryService統合

```java
public class {Domain}ManagementEntryService implements {Domain}ManagementApi {

  AuditLogPublisher auditLogPublisher;
  private {Domain}ManagementHandler handler;

  public {Domain}ManagementEntryService(...) {
    // Handler作成
    this.handler = createHandler(...);
  }

  private {Domain}ManagementHandler createHandler(...) {
    Map<String, {Domain}ManagementService<?>> services = new HashMap<>();
    services.put("create", new {Domain}CreationService(...));
    services.put("update", new {Domain}UpdateService(...));
    // ...
    return new {Domain}ManagementHandler(services, this, tenantQueryRepository);
  }

  @Override
  public {Domain}ManagementResponse create(...) {
    {Domain}ManagementResult result = handler.handle("create", ...);

    if (result.hasException()) {
      AuditLog auditLog = AuditLogCreator.createOnError(...);
      auditLogPublisher.publish(auditLog);
      throw result.getException();
    }

    AuditLog auditLog = AuditLogCreator.create(...);
    auditLogPublisher.publish(auditLog);

    return result.toResponse(dryRun);
  }
}
```

#### Step 5: 組織レベル対応 (必要な場合)

```java
public class Org{Domain}ManagementHandler {
  private final Map<String, {Domain}ManagementService<?>> services;  // システムレベルと同じServiceを再利用
  private final OrganizationRepository organizationRepository;
  private final OrganizationAccessVerifier organizationAccessVerifier;

  public {Domain}ManagementResult handle(
      String operation,
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      Object request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    Tenant tenant = null;
    try {
      AdminPermissions permissions = entryService.getRequiredPermissions(operation);
      Organization organization = organizationRepository.get(organizationIdentifier);
      tenant = tenantQueryRepository.get(tenantIdentifier);

      // 組織アクセス制御 (4ステップ)
      OrganizationAccessControlResult accessResult =
          organizationAccessVerifier.verify(organization, tenant, operator, oAuthToken, permissions);

      // Service選択・実行 (システムレベルと同じServiceを使用)
      {Domain}ManagementService<?> service = services.get(operation);
      return executeService(service, tenant, operator, oAuthToken, request, requestAttributes, dryRun);

    } catch (ManagementApiException e) {
      return {Domain}ManagementResult.error(tenant, e);
    }
  }
}
```

---

## Implementation Status

### User Management API (完了)

**システムレベル**: ✅ 7操作
- create, get, update, patch, updatePassword, delete, find

**組織レベル**: ✅ 10操作
- システムレベル7操作 + updateRoles, updateTenantAssignments, updateOrganizationAssignments

**Phase 1**: ✅ 完了
- [x] Exception階層の定義
- [x] `UserManagementResult` (Result-Exception Hybrid)
- [x] `UserManagementHandler` (システムレベル)
- [x] `OrgUserManagementHandler` (組織レベル)
- [x] 全10操作のService実装
- [x] EntryService統合

**Phase 2**: ✅ 完了
- [x] Tenant重複取得の解消 (ServiceにTenantを渡す)
- [x] `AuditLogCreator.createOnError()` 実装
- [x] 詳細な監査ログ情報 (errorDescription, errorDetails含む)
- [x] Context Creator直接値取得パターン実装

**Phase 3**: 🚧 部分完了
- [x] Genericsで型安全性確保 (`executeService<T>`)
- [ ] Context Objectで引数集約 (現状6引数で問題なし、保留)
- [ ] テストコード作成

### 今後の展開

**次の対象API**:
- Authentication Configuration API
- Client Configuration API
- Authorization Policy API
- その他Management API

**展開方針**:
- Implementation Guideに従って段階的に実装
- Serviceのみ新規作成、Handler/EntryServiceの基本構造は流用
- 組織レベル対応が必要な場合はOrgHandlerも作成

---

## References

- **Issue #746**: Handler/Service pattern PoC for Management API refactoring
- **Issue #529**: Audit log timing problem
- **Existing pattern**: Token API (`DefaultTokenProtocol`, `DefaultTokenHandler`)
- **Implementation**: `/libs/idp-server-control-plane/src/main/java/org/idp/server/control_plane/management/identity/user/handler/`
