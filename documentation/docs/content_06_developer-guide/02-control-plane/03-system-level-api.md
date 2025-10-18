# システムレベルAPI実装ガイド

## このドキュメントの目的

**システムレベル管理API**を、ゼロから実装できるようになることが目標です。

### 所要時間
⏱️ **約45分**（実装 + テスト）

### 前提知識
- [01. アーキテクチャ概要](../01-architecture-overview.md)
- [02. 最初のAPI実装](../02-first-api-implementation.md)
- [03. 共通実装パターン](../03-common-patterns.md)

---

## システムレベルAPIとは

**テナント単位**で管理するAPI。システム管理者が使用。

```
GET /v1/management/tenants/{tenantId}/clients
POST /v1/management/tenants/{tenantId}/clients
```

**特徴**:
- ✅ テナント単位のリソース管理
- ✅ システム管理者権限が必要（`client:read`, `client:write`等）
- ✅ Audit Log記録
- ✅ Dry Run対応

**対比**: 組織レベルAPI = 組織単位で管理（`/organizations/{orgId}/tenants/{tenantId}/...`）

---

## 実装の全体フロー

```
1. API契約定義（Control Plane層）
   ├─ インターフェース定義
   ├─ Request/Response DTO
   ├─ Context Creator
   └─ 権限定義（defaultメソッド）

2. EntryService実装（UseCase層）
   ├─ トランザクション管理
   ├─ 権限チェック
   ├─ Audit Log記録
   └─ Dry Run対応

3. Controller実装（Controller層）
   └─ HTTPエンドポイント

4. E2Eテスト作成
```

---

## 実装例: Role管理API

新しい「Role管理API」を実装します。

### 要件
- ロール作成（CREATE）
- ロール一覧取得（READ）
- ロール取得（READ）
- ロール更新（UPDATE）
- ロール削除（DELETE）

---

## Step 1: API契約定義（Control Plane層）

### 1-1. インターフェース定義

**ファイル**: `libs/idp-server-control-plane/src/main/java/org/idp/server/control_plane/management/role/RoleManagementApi.java`

```java
package org.idp.server.control_plane.management.role;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.base.definition.DefaultAdminPermission;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;

public interface RoleManagementApi {

  /**
   * 必要権限を返す（defaultメソッド - 実装不要）
   *
   * @param method メソッド名
   * @return 必要な権限
   */
  default AdminPermissions getRequiredPermissions(String method) {
    Map<String, AdminPermissions> map = new HashMap<>();
    map.put("create", new AdminPermissions(Set.of(DefaultAdminPermission.ROLE_CREATE)));
    map.put("findList", new AdminPermissions(Set.of(DefaultAdminPermission.ROLE_READ)));
    map.put("get", new AdminPermissions(Set.of(DefaultAdminPermission.ROLE_READ)));
    map.put("update", new AdminPermissions(Set.of(DefaultAdminPermission.ROLE_UPDATE)));
    map.put("delete", new AdminPermissions(Set.of(DefaultAdminPermission.ROLE_DELETE)));

    AdminPermissions adminPermissions = map.get(method);
    if (adminPermissions == null) {
      throw new UnSupportedException("Method " + method + " not supported");
    }
    return adminPermissions;
  }

  /**
   * ロール作成
   */
  RoleManagementResponse create(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      RoleRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  /**
   * ロール一覧取得
   */
  RoleManagementResponse findList(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      RoleQueries queries,
      RequestAttributes requestAttributes);

  /**
   * ロール取得
   */
  RoleManagementResponse get(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      RoleIdentifier roleIdentifier,
      RequestAttributes requestAttributes);

  /**
   * ロール更新
   */
  RoleManagementResponse update(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      RoleIdentifier roleIdentifier,
      RoleUpdateRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun);

  /**
   * ロール削除
   */
  RoleManagementResponse delete(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      RoleIdentifier roleIdentifier,
      RequestAttributes requestAttributes,
      boolean dryRun);
}
```

**ポイント**:
- ✅ `defaultメソッド`: 権限自動計算（**実装不要**）
- ✅ 全メソッド第一引数: `TenantIdentifier`
- ✅ 共通引数: `User operator`, `OAuthToken oAuthToken`, `RequestAttributes requestAttributes`
- ✅ 書き込み操作: `boolean dryRun`

---

### 1-2. Request DTO作成

**ファイル**: `libs/idp-server-control-plane/src/main/java/org/idp/server/control_plane/management/role/RoleRegistrationRequest.java`

```java
package org.idp.server.control_plane.management.role;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;

public class RoleRegistrationRequest {

  @JsonProperty("role_id")
  private String roleId;

  @JsonProperty("role_name")
  private String roleName;

  @JsonProperty("description")
  private String description;

  @JsonProperty("permissions")
  private Set<String> permissions;

  // Getters/Setters
  public String getRoleId() {
    return roleId;
  }

  public void setRoleId(String roleId) {
    this.roleId = roleId;
  }

  public String getRoleName() {
    return roleName;
  }

  public void setRoleName(String roleName) {
    this.roleName = roleName;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Set<String> getPermissions() {
    return permissions;
  }

  public void setPermissions(Set<String> permissions) {
    this.permissions = permissions;
  }
}
```

**ポイント**:
- ✅ `@JsonProperty`: スネークケース対応
- ✅ プリミティブ型回避: `String`, `Set<String>`使用

---

### 1-3. Response DTO作成

**ファイル**: `libs/idp-server-control-plane/src/main/java/org/idp/server/control_plane/management/role/RoleManagementResponse.java`

```java
package org.idp.server.control_plane.management.role;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public class RoleManagementResponse {

  @JsonProperty("status")
  private String status;

  @JsonProperty("dry_run")
  private boolean dryRun;

  @JsonProperty("result")
  private Map<String, Object> result;

  public RoleManagementResponse(String status, Map<String, Object> result) {
    this.status = status;
    this.dryRun = false;
    this.result = result;
  }

  public RoleManagementResponse(String status, boolean dryRun, Map<String, Object> result) {
    this.status = status;
    this.dryRun = dryRun;
    this.result = result;
  }

  // Getters
  public String getStatus() {
    return status;
  }

  public boolean isDryRun() {
    return dryRun;
  }

  public Map<String, Object> getResult() {
    return result;
  }
}
```

---

### 1-4. Context Creator作成

**ファイル**: `libs/idp-server-control-plane/src/main/java/org/idp/server/control_plane/management/role/RoleRegistrationContextCreator.java`

```java
package org.idp.server.control_plane.management.role;

import org.idp.server.core.openid.identity.Role;
import org.idp.server.core.openid.identity.RoleIdentifier;
import org.idp.server.core.openid.identity.RoleName;
import org.idp.server.core.openid.identity.Permissions;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class RoleRegistrationContextCreator {

  private final Tenant tenant;
  private final RoleRegistrationRequest request;
  private final boolean dryRun;

  public RoleRegistrationContextCreator(
      Tenant tenant,
      RoleRegistrationRequest request,
      boolean dryRun) {
    this.tenant = tenant;
    this.request = request;
    this.dryRun = dryRun;
  }

  public RoleRegistrationContext create() {
    // 1. リクエスト → ドメインモデル変換
    RoleIdentifier roleIdentifier = new RoleIdentifier(request.getRoleId());
    RoleName roleName = new RoleName(request.getRoleName());
    Permissions permissions = new Permissions(request.getPermissions());

    // 2. Roleドメインモデル生成
    Role role = new Role(
        roleIdentifier,
        roleName,
        request.getDescription(),
        permissions
    );

    // 3. Context生成
    return new RoleRegistrationContext(tenant, role, dryRun);
  }
}
```

**ポイント**:
- ✅ リクエストDTO → ドメインモデル変換
- ✅ 値オブジェクト使用（`RoleIdentifier`, `RoleName`, `Permissions`）

---

### 1-5. Context作成

**ファイル**: `libs/idp-server-control-plane/src/main/java/org/idp/server/control_plane/management/role/RoleRegistrationContext.java`

```java
package org.idp.server.control_plane.management.role;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.identity.Role;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class RoleRegistrationContext {

  private final Tenant tenant;
  private final Role role;
  private final boolean dryRun;

  public RoleRegistrationContext(Tenant tenant, Role role, boolean dryRun) {
    this.tenant = tenant;
    this.role = role;
    this.dryRun = dryRun;
  }

  public Tenant tenant() {
    return tenant;
  }

  public Role role() {
    return role;
  }

  public boolean isDryRun() {
    return dryRun;
  }

  /**
   * レスポンス変換
   */
  public RoleManagementResponse toResponse() {
    Map<String, Object> result = new HashMap<>();
    result.put("role_id", role.identifier().value());
    result.put("role_name", role.name().value());
    result.put("description", role.description());
    result.put("permissions", role.permissions().values());

    return new RoleManagementResponse("SUCCESS", dryRun, result);
  }
}
```

---

## Step 2: EntryService実装（UseCase層）

**ファイル**: `libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/control_plane/system_manager/RoleManagementEntryService.java`

```java
package org.idp.server.usecases.control_plane.system_manager;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.base.AuditLogCreator;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.management.role.*;
import org.idp.server.core.openid.identity.Role;
import org.idp.server.core.openid.identity.RoleCommandRepository;
import org.idp.server.core.openid.identity.RoleQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class RoleManagementEntryService implements RoleManagementApi {

  TenantQueryRepository tenantQueryRepository;
  RoleCommandRepository roleCommandRepository;
  RoleQueryRepository roleQueryRepository;
  AuditLogPublisher auditLogPublisher;
  LoggerWrapper log = LoggerWrapper.getLogger(RoleManagementEntryService.class);

  public RoleManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      RoleCommandRepository roleCommandRepository,
      RoleQueryRepository roleQueryRepository,
      AuditLogPublisher auditLogPublisher) {
    this.tenantQueryRepository = tenantQueryRepository;
    this.roleCommandRepository = roleCommandRepository;
    this.roleQueryRepository = roleQueryRepository;
    this.auditLogPublisher = auditLogPublisher;
  }

  @Override
  public RoleManagementResponse create(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      RoleRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // 1. 必要権限を取得
    AdminPermissions permissions = getRequiredPermissions("create");

    // 2. Tenantを取得
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    // 3. Context Creator使用
    RoleRegistrationContextCreator contextCreator =
        new RoleRegistrationContextCreator(tenant, request, dryRun);
    RoleRegistrationContext context = contextCreator.create();

    // 4. Audit Log記録
    AuditLog auditLog =
        AuditLogCreator.create(
            "RoleManagementApi.create",
            tenant,
            operator,
            oAuthToken,
            context,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    // 5. 権限チェック
    if (!permissions.includesAll(operator.permissionsAsSet())) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put(
          "error_description",
          String.format(
              "permission denied required permission %s, but %s",
              permissions.valuesAsString(), operator.permissionsAsString()));
      log.warn(response.toString());
      return new RoleManagementResponse("FORBIDDEN", response);
    }

    // 6. Dry Runチェック
    if (dryRun) {
      return context.toResponse();
    }

    // 7. Repository保存
    roleCommandRepository.register(tenant, context.role());

    // 8. レスポンス返却
    return context.toResponse();
  }

  @Override
  @Transaction(readOnly = true)  // ⚠️ 読み取り専用トランザクション
  public RoleManagementResponse findList(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      RoleQueries queries,
      RequestAttributes requestAttributes) {

    AdminPermissions permissions = getRequiredPermissions("findList");

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    // 読み取り操作のAudit Log
    AuditLog auditLog =
        AuditLogCreator.createOnRead(
            "RoleManagementApi.findList",
            "findList",
            tenant,
            operator,
            oAuthToken,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    if (!permissions.includesAll(operator.permissionsAsSet())) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      log.warn(response.toString());
      return new RoleManagementResponse("FORBIDDEN", response);
    }

    // Repository呼び出し
    List<Role> roles = roleQueryRepository.findAll(tenant);

    // レスポンス生成
    Map<String, Object> result = new HashMap<>();
    result.put("roles", roles.stream().map(Role::toMap).collect(Collectors.toList()));

    return new RoleManagementResponse("SUCCESS", result);
  }

  // get(), update(), delete() メソッドも同様のパターンで実装...
}
```

**ポイント**:
- ✅ `@Transaction`: トランザクション境界
- ✅ 読み取り専用: `@Transaction(readOnly = true)`
- ✅ Context Creator使用
- ✅ 権限チェック
- ✅ Audit Log記録（`create` vs `createOnRead`）
- ✅ Dry Run対応

---

## Step 3: Controller実装（Controller層）

**ファイル**: `libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapter/springboot/controller/management/RoleManagementController.java`

```java
package org.idp.server.adapter.springboot.controller.management;

import org.idp.server.control_plane.management.role.*;
import org.idp.server.core.openid.identity.RoleIdentifier;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/management/tenants/{tenantId}/roles")
public class RoleManagementController {

  private final RoleManagementApi roleManagementApi;

  public RoleManagementController(RoleManagementApi roleManagementApi) {
    this.roleManagementApi = roleManagementApi;
  }

  /**
   * ロール作成
   */
  @PostMapping
  public ResponseEntity<RoleManagementResponse> create(
      @PathVariable("tenantId") String tenantId,
      @RequestBody RoleRegistrationRequest request,
      @RequestParam(value = "dry_run", defaultValue = "false") boolean dryRun,
      @AuthenticationPrincipal User operator,
      @RequestAttribute OAuthToken oAuthToken,
      @RequestAttribute RequestAttributes requestAttributes) {

    TenantIdentifier tenantIdentifier = new TenantIdentifier(tenantId);

    RoleManagementResponse response =
        roleManagementApi.create(
            tenantIdentifier, operator, oAuthToken, request, requestAttributes, dryRun);

    return ResponseEntity.ok(response);
  }

  /**
   * ロール一覧取得
   */
  @GetMapping
  public ResponseEntity<RoleManagementResponse> findList(
      @PathVariable("tenantId") String tenantId,
      @AuthenticationPrincipal User operator,
      @RequestAttribute OAuthToken oAuthToken,
      @RequestAttribute RequestAttributes requestAttributes) {

    TenantIdentifier tenantIdentifier = new TenantIdentifier(tenantId);
    RoleQueries queries = new RoleQueries();  // クエリパラメータがある場合は設定

    RoleManagementResponse response =
        roleManagementApi.findList(
            tenantIdentifier, operator, oAuthToken, queries, requestAttributes);

    return ResponseEntity.ok(response);
  }

  /**
   * ロール取得
   */
  @GetMapping("/{roleId}")
  public ResponseEntity<RoleManagementResponse> get(
      @PathVariable("tenantId") String tenantId,
      @PathVariable("roleId") String roleId,
      @AuthenticationPrincipal User operator,
      @RequestAttribute OAuthToken oAuthToken,
      @RequestAttribute RequestAttributes requestAttributes) {

    TenantIdentifier tenantIdentifier = new TenantIdentifier(tenantId);
    RoleIdentifier roleIdentifier = new RoleIdentifier(roleId);

    RoleManagementResponse response =
        roleManagementApi.get(tenantIdentifier, operator, oAuthToken, roleIdentifier, requestAttributes);

    return ResponseEntity.ok(response);
  }

  // update(), delete() メソッドも同様...
}
```

**ポイント**:
- ✅ Controller = 型変換のみ
- ✅ `@PathVariable`: URLパラメータ
- ✅ `@RequestParam`: クエリパラメータ（`dry_run`）
- ✅ `@AuthenticationPrincipal`: 認証済みユーザー
- ✅ `@RequestAttribute`: リクエスト属性

---

## Step 4: E2Eテスト作成

**ファイル**: `e2e/spec/management/role-management.spec.js`

```javascript
const { v4: uuidv4 } = require('uuid');
const axios = require('axios');

describe('Role Management API', () => {
  let adminToken;
  let tenantId;
  let roleId;

  beforeAll(async () => {
    // 1. 管理者トークン取得
    const tokenResponse = await axios.post('http://localhost:8080/oauth/token', {
      grant_type: 'client_credentials',
      client_id: 'admin-client',
      client_secret: 'admin-secret',
      scope: 'role:read role:write'
    });
    adminToken = tokenResponse.data.access_token;

    // 2. テストテナント作成
    const tenantResponse = await axios.post(
      'http://localhost:8080/v1/management/tenants',
      {
        name: 'test-tenant',
        display_name: 'Test Tenant for Role Management'
      },
      {
        headers: {
          Authorization: `Bearer ${adminToken}`
        }
      }
    );
    tenantId = tenantResponse.data.tenant_id;

    roleId = uuidv4();
  });

  test('should create role successfully', async () => {
    const response = await axios.post(
      `http://localhost:8080/v1/management/tenants/${tenantId}/roles`,
      {
        role_id: roleId,
        role_name: 'Admin Role',
        description: 'Administrator role',
        permissions: ['tenant:read', 'tenant:write', 'client:read', 'client:write']
      },
      {
        headers: {
          Authorization: `Bearer ${adminToken}`
        }
      }
    );

    expect(response.status).toBe(200);
    expect(response.data.status).toBe('SUCCESS');
    expect(response.data.dry_run).toBe(false);
    expect(response.data.result).toHaveProperty('role_id', roleId);
    expect(response.data.result).toHaveProperty('role_name', 'Admin Role');
  });

  test('should support dry run mode', async () => {
    const response = await axios.post(
      `http://localhost:8080/v1/management/tenants/${tenantId}/roles?dry_run=true`,
      {
        role_id: uuidv4(),
        role_name: 'Dry Run Role',
        description: 'Test dry run',
        permissions: ['tenant:read']
      },
      {
        headers: {
          Authorization: `Bearer ${adminToken}`
        }
      }
    );

    expect(response.status).toBe(200);
    expect(response.data.dry_run).toBe(true);
  });

  test('should get role list', async () => {
    const response = await axios.get(
      `http://localhost:8080/v1/management/tenants/${tenantId}/roles`,
      {
        headers: {
          Authorization: `Bearer ${adminToken}`
        }
      }
    );

    expect(response.status).toBe(200);
    expect(response.data.result.roles).toBeInstanceOf(Array);
    expect(response.data.result.roles.length).toBeGreaterThan(0);
  });

  test('should return 403 when permission denied', async () => {
    // 権限のないトークンで実行
    const noPermTokenResponse = await axios.post('http://localhost:8080/oauth/token', {
      grant_type: 'client_credentials',
      client_id: 'public-client',
      client_secret: 'public-secret',
      scope: 'openid'  // role:write権限なし
    });

    try {
      await axios.post(
        `http://localhost:8080/v1/management/tenants/${tenantId}/roles`,
        {
          role_id: uuidv4(),
          role_name: 'Forbidden Role',
          permissions: []
        },
        {
          headers: {
            Authorization: `Bearer ${noPermTokenResponse.data.access_token}`
          }
        }
      );
      fail('Expected 403 error');
    } catch (error) {
      expect(error.response.status).toBe(403);
      expect(error.response.data.error).toBe('access_denied');
    }
  });
});
```

---

## チェックリスト

システムレベルAPI実装前に以下を確認：

### API契約定義（Control Plane層）
- [ ] インターフェース定義（`{Domain}ManagementApi`）
- [ ] `defaultメソッド`で権限定義（実装不要）
- [ ] Request DTO作成（`@JsonProperty`でスネークケース対応）
- [ ] Response DTO作成
- [ ] Context Creator作成（リクエスト → ドメインモデル変換）
- [ ] Context作成（`toResponse()`メソッド実装）

### EntryService実装（UseCase層）
- [ ] `@Transaction`アノテーション付与
- [ ] 読み取り専用なら`@Transaction(readOnly = true)`
- [ ] Context Creator使用
- [ ] 権限チェック実装
- [ ] Audit Log記録（`create` vs `createOnRead`）
- [ ] Dry Run対応（書き込み操作のみ）

### Controller実装（Controller層）
- [ ] HTTPエンドポイント定義
- [ ] 型変換のみ（ロジック禁止）
- [ ] `@PathVariable`, `@RequestParam`適切使用

### E2Eテスト
- [ ] 正常系テスト（CREATE/READ/UPDATE/DELETE）
- [ ] Dry Runテスト
- [ ] 権限エラーテスト（403）

---

## よくあるエラー

### エラー1: `defaultメソッド`を実装してしまう

```java
// ❌ 間違い: defaultメソッドをオーバーライド
@Override
public AdminPermissions getRequiredPermissions(String method) {
    // 不要な実装
}

// ✅ 正しい: defaultメソッドはそのまま使用（実装不要）
public class RoleManagementEntryService implements RoleManagementApi {
    // getRequiredPermissions()は実装不要！
}
```

### エラー2: Context Creator未使用

```java
// ❌ 間違い: EntryServiceでDTO直接変換
Role role = new Role(
    new RoleIdentifier(request.getRoleId()),
    // ... 直接変換
);

// ✅ 正しい: Context Creator使用
RoleRegistrationContextCreator creator =
    new RoleRegistrationContextCreator(tenant, request, dryRun);
RoleRegistrationContext context = creator.create();
```

---

## 次のステップ

✅ システムレベルAPI実装をマスターした！

### 📖 次に読むべきドキュメント

1. [組織レベルAPI実装ガイド](./04-organization-level-api.md) - より複雑なアクセス制御
2. [Repository実装ガイド](../04-implementation-guides/impl-10-repository-implementation.md) - データアクセス層の実装

### 🔗 詳細情報

- [AI開発者向け: Control Plane詳細](../../content_10_ai_developer/ai-13-control-plane.md)
- [AI開発者向け: Use-Cases詳細](../../content_10_ai_developer/ai-10-use-cases.md)

---

**情報源**: [ClientManagementEntryService.java](../../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/control_plane/system_manager/ClientManagementEntryService.java)
**最終更新**: 2025-10-12
