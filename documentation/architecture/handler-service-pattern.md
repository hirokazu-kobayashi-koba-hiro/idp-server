# Handler/Service Pattern - Layer Responsibilities

## Overview

Management APIリファクタリングのための3層アーキテクチャパターン定義。
各層の責務を明確化し、関心事の分離を実現する。

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│ EntryService (UserManagementEntryService)                   │
│ 責務: トランザクション境界、監査ログ、例外→レスポンス変換   │
├─────────────────────────────────────────────────────────────┤
│ 1. Handler呼び出し                                          │
│ 2. 監査ログ記録 (@Async別トランザクション)                 │
│ 3. 例外があれば再スロー (ロールバックトリガー)              │
│ 4. レスポンス返却                                           │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ Handler (UserManagementHandler)                             │
│ 責務: 横断的関心事 (権限、マルチオペレーション対応)         │
├─────────────────────────────────────────────────────────────┤
│ 1. Tenant取得 (全オペレーション共通)                       │
│ 2. 権限検証 (全オペレーション共通)                         │
│ 3. Service選択 (method → Service)                          │
│ 4. Service実行                                              │
│ 5. 例外キャッチ → Result化                                 │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ Service (UserCreationService, UserUpdateService, etc.)     │
│ 責務: オペレーション固有のビジネスロジック                  │
├─────────────────────────────────────────────────────────────┤
│ 1. リクエストバリデーション (InvalidRequestException)      │
│ 2. Context作成 (DTO → ドメインモデル変換)                  │
│ 3. ビジネスルール検証 (VerificationException)              │
│ 4. Repository操作 (永続化)                                 │
│ 5. イベント発行 (SecurityEvent)                            │
│ 6. Result返却                                               │
└─────────────────────────────────────────────────────────────┘
```

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

#### コード例

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
  AuditLog auditLog = createAuditLog(result, operator, oAuthToken, requestAttributes);
  auditLogPublisher.publish(auditLog);

  // 3. 例外があれば再スロー
  if (result.hasException()) {
    throw result.getException();
  }

  // 4. レスポンス返却
  return result.toResponse(dryRun);
}
```

---

### Handler層

**目的**: 横断的関心事の処理とオペレーションルーティング

#### ✅ やること

- **横断的関心事の処理**
  - **Tenant取得**: 全オペレーション共通、監査ログ用に必須
  - **権限検証**: 全オペレーション共通
- **オペレーションルーティング**
  - methodからServiceを選択
  - Serviceに必要な共通データを渡す
- **例外の統一的ハンドリング**
  - `ManagementApiException`をキャッチ
  - `Result`に変換 (EntryServiceで成功/失敗判定可能にする)

#### ❌ やらないこと

- オペレーション固有のビジネスロジック
- Repository操作 (Tenant取得以外)
- レスポンス生成
- 監査ログ記録

#### コード例

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
    UserManagementService service = services.get(method);
    if (service == null) {
      throw new IllegalArgumentException("Unsupported operation method: " + method);
    }

    // 4. Service実行
    return service.execute(tenant, operator, oAuthToken, request, requestAttributes, dryRun);

  } catch (ManagementApiException e) {
    // 5. 例外キャッチ → Result化
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
public UserManagementResult execute(
    Tenant tenant,  // Handler層から渡される
    User operator,
    OAuthToken oAuthToken,
    Object request,
    RequestAttributes requestAttributes,
    boolean dryRun) {

  UserRegistrationRequest userRequest = (UserRegistrationRequest) request;

  // 1. リクエストバリデーション
  UserRegistrationRequestValidator validator =
      new UserRegistrationRequestValidator(userRequest, dryRun);
  validator.validateWithException();  // throws InvalidRequestException

  // 2. Context作成
  UserRegistrationContextCreator contextCreator =
      new UserRegistrationContextCreator(tenant, userRequest, dryRun, passwordEncodeDelegation);
  UserRegistrationContext context = contextCreator.create();

  // 3. ビジネスルール検証
  verifier.verify(context);  // throws exceptions

  // 4. Repository操作 (dry-runでなければ)
  if (!dryRun) {
    userCommandRepository.register(tenant, context.user());
  }

  // 5. イベント発行
  managementEventPublisher.publish(tenant, operator, context.user(), oAuthToken, ...);

  // 6. Result返却
  return UserManagementResult.success(tenant, context, context.toResponse());
}
```

---

## Key Design Decisions

### 1. Tenant取得の責務配置

**決定**: Handler層で取得

**理由**:
- 全オペレーションで必要 (共通処理)
- 監査ログ記録に必須 (失敗時でも必要)
- Service層でのTenant重複取得を防止

**実装**:
```java
// Handler層
Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

// Service層
public UserManagementResult execute(
    Tenant tenant,  // Handler層から渡される
    ...
) {
  // Tenant取得不要、渡されたtenantを使用
}
```

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

---

## Implementation Checklist

### Phase 1: 基本構造
- [x] Exception階層の定義 (`ManagementApiException`, `InvalidRequestException`, `PermissionDeniedException`)
- [x] `UserManagementResult` (Result-Exception Hybrid)
- [x] `UserManagementHandler` (横断的関心事)
- [x] `UserCreationService` (ビジネスロジック)
- [x] EntryService統合

### Phase 2: 改善項目
- [ ] Tenant重複取得の解消 (ServiceにTenantを渡す)
- [ ] `AuditLogCreator.createOnError()` 実装
- [ ] 詳細な監査ログ情報 (errorDescription, errorDetails含む)
- [ ] テストコード作成

### Phase 3: 拡張性改善
- [ ] Genericsで型安全性確保 (`UserManagementResult<T>`)
- [ ] Context Objectで引数集約
- [ ] 他のManagement API (update, delete等) への適用

---

## Open Questions

### 1. Handlerレイヤーの必要性

**YES派**: 横断的関心事を集約、Service層を純粋に保つ
**NO派**: EntryServiceでswitchすれば十分、レイヤー過多

**現状の判断**: YES - 以下の理由で必要
- 複数オペレーション (create/update/delete/find) の共通処理集約
- Service層を純粋なビジネスロジックに保つ
- 権限検証・Tenant取得の一元管理

### 2. Serviceの引数形式

**現状**: 6個の引数
```java
execute(Tenant tenant, User operator, OAuthToken oAuthToken,
        Object request, RequestAttributes requestAttributes, boolean dryRun)
```

**代替案**: Context Object
```java
class ServiceExecutionContext {
  Tenant tenant;
  User operator;
  OAuthToken oAuthToken;
  Object request;
  RequestAttributes requestAttributes;
  boolean dryRun;
}

execute(ServiceExecutionContext context)
```

**判断保留**: まずは現状の形式で動作確認後、必要に応じて改善

---

## References

- Issue #746: Handler/Service pattern PoC for Management API refactoring
- Issue #529: Audit log timing problem
- Existing pattern: Token API (`DefaultTokenProtocol`, `DefaultTokenHandler`)
