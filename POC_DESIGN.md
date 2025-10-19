# PoC: Handler/Service Pattern for Management API

## 目的

UserManagementEntryService (702行) をHandler/Serviceパターンにリファクタリングし、以下を実現：

1. **重複コード削除**: 権限チェック・監査ログ発行の共通化
2. **責務分離**: Handler（フロー制御）とService（ビジネスロジック）の分離
3. **テスタビリティ向上**: Service単体テストが容易
4. **監査ログ改善**: トランザクション後の発行（Issue #529）

## 現状分析

### UserManagementEntryServiceの構造

```
総行数: 702行
メソッド数: 11個（create, findList, get, update, patch, updatePassword, delete, updateRoles, updateTenantAssignments, updateOrganizationAssignments）
平均: 60-70行/メソッド
```

### 各メソッドの共通パターン

```java
public UserManagementResponse method(...) {
    // 1. Permission check (全メソッドで重複)
    AdminPermissions permissions = getRequiredPermissions("method");
    if (!permissions.includesAll(operator.permissionsAsSet())) {
        // エラーレスポンス作成（重複コード）
    }

    // 2. Validation
    Validator validator = new Validator(...);
    ValidationResult result = validator.validate();

    // 3. Verification
    VerificationResult verResult = verifier.verify(...);

    // 4. Audit Log (❌ トランザクション前！)
    AuditLog auditLog = AuditLogCreator.create(...);
    auditLogPublisher.publish(auditLog);

    // 5. Dry-run check
    if (dryRun) {
        return context.toResponse();
    }

    // 6. Repository operation
    repository.operation(...);

    // 7. SecurityEvent publish
    managementEventPublisher.publish(...);

    return context.toResponse();
}
```

## 提案アーキテクチャ

### OAuthパターンの適用

TokenRequestHandlerの設計を参考：

```java
// Handler: 薄い層（フロー制御のみ）
public TokenRequestResponse handle(TokenRequest request, Delegate delegate) {
    // 1. Validation
    TokenRequestValidator validator = new TokenRequestValidator(parameters);
    validator.validate();

    // 2. Context作成
    TokenRequestContext context = new TokenRequestContext(...);

    // 3. 認証
    ClientCredentials credentials = clientAuthenticationHandler.authenticate(context);

    // 4. Service選択・実行
    OAuthTokenCreationService service = services.get(context.grantType());
    OAuthToken token = service.create(context, credentials);

    return new TokenRequestResponse(TokenRequestStatus.OK, token);
}
```

### 新しい構造

```
UserManagementHandler (100-150行)
  ├─ PermissionVerifier (共通)
  ├─ AuditLogHandler (共通・AFTER_COMMIT対応)
  └─ Map<Operation, UserManagementService>
      ├─ UserCreationService (50-80行)
      ├─ UserUpdateService (50-80行)
      ├─ UserDeletionService (50-80行)
      ├─ UserPasswordUpdateService (50-80行)
      └─ ... (その他のサービス)
```

## 実装計画

### Phase 1: 基盤コンポーネント

#### 1. PermissionVerifier (共通化)

```java
public class PermissionVerifier {
    public PermissionCheckResult verify(User operator, AdminPermissions required) {
        if (!required.includesAll(operator.permissionsAsSet())) {
            return PermissionCheckResult.denied(required, operator.permissionsAsSet());
        }
        return PermissionCheckResult.allowed();
    }
}

public class PermissionCheckResult {
    boolean allowed;
    Map<String, Object> errorResponse; // error, error_descriptionを含む

    public static PermissionCheckResult denied(AdminPermissions required, Set<String> actual) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", "access_denied");
        response.put("error_description",
            String.format("permission denied required permission %s, but %s",
                required.valuesAsString(), String.join(",", actual)));
        return new PermissionCheckResult(false, response);
    }
}
```

#### 2. AuditLogHandler (AFTER_COMMIT対応)

```java
public class AuditLogHandler {
    AuditLogPublisher auditLogPublisher;

    public void logAfterCommit(
        String operation,
        Tenant tenant,
        User operator,
        OAuthToken token,
        Object context,
        RequestAttributes attributes) {

        AuditLog auditLog = AuditLogCreator.create(
            operation, tenant, operator, token, context, attributes);

        // ❌ 現状: トランザクション中に発行
        // ✅ 改善: トランザクション完了後に発行
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronizationAdapter() {
                @Override
                public void afterCommit() {
                    auditLogPublisher.publish(auditLog);
                }
            });
    }
}
```

### Phase 2: UserManagementHandler

```java
@Transaction
public class UserManagementHandler {
    Map<String, UserManagementService> services;
    PermissionVerifier permissionVerifier;
    AuditLogHandler auditLogHandler;

    public UserManagementHandler(
        UserCreationService userCreationService,
        UserUpdateService userUpdateService,
        UserDeletionService userDeletionService,
        // ... その他のサービス
        PermissionVerifier permissionVerifier,
        AuditLogHandler auditLogHandler) {

        this.services = Map.of(
            "create", userCreationService,
            "update", userUpdateService,
            "delete", userDeletionService
            // ...
        );
        this.permissionVerifier = permissionVerifier;
        this.auditLogHandler = auditLogHandler;
    }

    public UserManagementResponse handle(
        String operation,
        TenantIdentifier tenantIdentifier,
        User operator,
        OAuthToken oAuthToken,
        Object request,
        RequestAttributes requestAttributes,
        boolean dryRun) {

        // 1. Permission check (共通化)
        AdminPermissions permissions = getRequiredPermissions(operation);
        PermissionCheckResult permResult = permissionVerifier.verify(operator, permissions);
        if (!permResult.isAllowed()) {
            return new UserManagementResponse(UserManagementStatus.FORBIDDEN, permResult.errorResponse());
        }

        // 2. Service選択・実行
        UserManagementService service = services.get(operation);
        UserManagementResult result = service.execute(
            tenantIdentifier, operator, oAuthToken, request, requestAttributes, dryRun);

        // 3. Audit log (AFTER_COMMIT)
        auditLogHandler.logAfterCommit(
            "UserManagementApi." + operation,
            result.tenant(),
            operator,
            oAuthToken,
            result.context(),
            requestAttributes);

        return result.toResponse();
    }
}
```

### Phase 3: Service実装例

```java
public class UserCreationService implements UserManagementService {
    UserQueryRepository userQueryRepository;
    UserCommandRepository userCommandRepository;
    RoleQueryRepository roleQueryRepository;
    OrganizationRepository organizationRepository;
    PasswordEncodeDelegation passwordEncodeDelegation;
    UserRegistrationVerifier verifier;
    ManagementEventPublisher managementEventPublisher;

    @Override
    public UserManagementResult execute(
        TenantIdentifier tenantIdentifier,
        User operator,
        OAuthToken oAuthToken,
        Object requestObj,
        RequestAttributes requestAttributes,
        boolean dryRun) {

        UserRegistrationRequest request = (UserRegistrationRequest) requestObj;
        Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

        // 1. Validation
        UserRegistrationRequestValidator validator =
            new UserRegistrationRequestValidator(request, dryRun);
        UserRequestValidationResult validate = validator.validate();
        if (!validate.isValid()) {
            return UserManagementResult.error(validate.errorResponse());
        }

        // 2. Context作成
        UserRegistrationContextCreator creator =
            new UserRegistrationContextCreator(tenant, request, dryRun, passwordEncodeDelegation);
        UserRegistrationContext context = creator.create();

        // 3. Verification
        UserRegistrationVerificationResult verificationResult = verifier.verify(context);
        if (!verificationResult.isValid()) {
            return UserManagementResult.error(verificationResult.errorResponse());
        }

        // 4. Dry-run check
        if (dryRun) {
            return UserManagementResult.success(tenant, context, context.toResponse());
        }

        // 5. Repository operation
        userCommandRepository.register(tenant, context.user());

        // 6. SecurityEvent publish
        managementEventPublisher.publish(
            tenant, operator, context.user(), oAuthToken,
            DefaultSecurityEventType.user_create.toEventType(),
            requestAttributes);

        return UserManagementResult.success(tenant, context, context.toResponse());
    }
}
```

### Phase 4: EntryService統合

```java
@Transaction
public class UserManagementEntryService implements UserManagementApi {
    UserManagementHandler handler;

    public UserManagementEntryService(
        // ... 既存の依存関係
        UserManagementHandler handler) {
        this.handler = handler;
    }

    @Override
    public UserManagementResponse create(
        TenantIdentifier tenantIdentifier,
        User operator,
        OAuthToken oAuthToken,
        UserRegistrationRequest request,
        RequestAttributes requestAttributes,
        boolean dryRun) {

        return handler.handle(
            "create", tenantIdentifier, operator, oAuthToken,
            request, requestAttributes, dryRun);
    }

    // 他のメソッドも同様にhandlerに委譲
}
```

## 期待される効果

### Before (現状)
- UserManagementEntryService: 702行
- 重複コード: 権限チェック・監査ログ発行が各メソッドに
- 監査ログ問題: トランザクション前の発行
- テスト困難: すべてのロジックが1クラスに集約

### After (改善後)
- UserManagementHandler: ~150行
- UserCreationService: ~70行
- UserUpdateService: ~70行
- ... (各Service 50-80行)
- PermissionVerifier: ~30行 (共通)
- AuditLogHandler: ~40行 (共通・AFTER_COMMIT対応)

### 改善点
1. ✅ 重複コード削除: 権限チェック・監査ログが共通化
2. ✅ 責務分離: Handler（制御）とService（ロジック）の明確な分離
3. ✅ テスタビリティ: Service単体テストが容易
4. ✅ 監査ログ整合性: AFTER_COMMITで確実に記録
5. ✅ 拡張性: 新しいoperationの追加が容易

## 次のステップ

1. [ ] PermissionVerifier実装
2. [ ] UserManagementHandler基盤実装
3. [ ] UserCreationService実装（1つ目のPoC）
4. [ ] E2Eテスト確認
5. [ ] パフォーマンステスト
6. [ ] パターン有効性評価
7. [ ] 他のServiceへの展開判断

## リスク軽減策

- 既存EntryServiceは残したまま並行実装
- 問題があれば既存実装に戻せる
- Phase 1で1つのServiceのみ実装して検証
