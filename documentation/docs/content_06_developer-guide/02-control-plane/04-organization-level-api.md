# 組織レベルAPI実装ガイド

## このドキュメントの目的

**組織レベル管理API**を、システムレベルAPIとの違いを理解しながら実装できるようになることが目標です。

### 所要時間
⏱️ **約60分**（実装 + テスト）

### 前提知識
- [システムレベルAPI実装ガイド](./03-system-level-api.md) - **必読**

---

## 組織レベルAPIとは

**組織単位**で管理するAPI。組織管理者が使用。

```
GET /v1/management/organizations/{orgId}/tenants/{tenantId}/roles
POST /v1/management/organizations/{orgId}/tenants/{tenantId}/roles
```

### システムレベルとの違い

| 項目 | システムレベル | 組織レベル |
|------|-------------|----------|
| **スコープ** | テナント単位 | 組織単位 |
| **URL** | `/v1/management/tenants/{tenantId}/...` | `/v1/management/organizations/{orgId}/tenants/{tenantId}/...` |
| **権限** | `client:read`, `client:write`等 | 組織専用権限 |
| **アクセス制御** | テナントアクセスのみ | **4ステップアクセス制御** |
| **複雑度** | 低 | **高（組織関係検証が追加）** |

---

## 組織レベルAPIの4ステップアクセス制御

組織レベルAPIは、**より厳格なアクセス制御**が必要です。

```
1. 組織メンバーシップ検証
   ↓
2. テナントアクセス検証
   ↓
3. 組織-テナント関係検証
   ↓
4. 権限検証
```

**実装者への注意**: この4ステップを必ず実装すること。省略すると**セキュリティ脆弱性**になります。

---

## 実装の全体フロー

```
1. API契約定義（Control Plane層）
   ├─ インターフェース定義（Org{Domain}ManagementApi）
   ├─ Request/Response DTO
   ├─ Context Creator
   └─ 権限定義（defaultメソッド）

2. EntryService実装（UseCase層）
   ├─ トランザクション管理
   ├─ **4ステップアクセス制御**  ← システムレベルとの違い
   ├─ Audit Log記録
   └─ Dry Run対応

3. Controller実装（Controller層）
   └─ HTTPエンドポイント（組織ID + テナントID）

4. E2Eテスト作成
```

---

## 実装例: 組織ロール管理API

システムレベルの「Role管理API」を組織レベルに拡張します。

---

## Step 1: API契約定義（Control Plane層）

### 1-1. インターフェース定義

**ファイル**: `libs/idp-server-control-plane/src/main/java/org/idp/server/control_plane/management/role/OrgRoleManagementApi.java`

```java
package org.idp.server.control_plane.management.role;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.idp.server.control_plane.base.definition.OrganizationAdminPermissions;
import org.idp.server.control_plane.base.definition.DefaultOrganizationAdminPermission;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;

public interface OrgRoleManagementApi {

  /**
   * 必要権限を返す（defaultメソッド - 実装不要）
   *
   * ⚠️ 注意: OrganizationAdminPermissions を使用
   *
   * @param method メソッド名
   * @return 必要な権限
   */
  default OrganizationAdminPermissions getRequiredPermissions(String method) {
    Map<String, OrganizationAdminPermissions> map = new HashMap<>();
    map.put("create", new OrganizationAdminPermissions(Set.of(DefaultOrganizationAdminPermission.ORG_ROLE_CREATE)));
    map.put("findList", new OrganizationAdminPermissions(Set.of(DefaultOrganizationAdminPermission.ORG_ROLE_READ)));
    map.put("get", new OrganizationAdminPermissions(Set.of(DefaultOrganizationAdminPermission.ORG_ROLE_READ)));
    map.put("update", new OrganizationAdminPermissions(Set.of(DefaultOrganizationAdminPermission.ORG_ROLE_UPDATE)));
    map.put("delete", new OrganizationAdminPermissions(Set.of(DefaultOrganizationAdminPermission.ORG_ROLE_DELETE)));

    OrganizationAdminPermissions permissions = map.get(method);
    if (permissions == null) {
      throw new UnSupportedException("Method " + method + " not supported");
    }
    return permissions;
  }

  /**
   * ロール作成
   *
   * ⚠️ 注意: 第一引数はOrganizationIdentifier、第二引数はTenantIdentifier
   */
  RoleManagementResponse create(
      OrganizationIdentifier organizationIdentifier,
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
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      RoleQueries queries,
      RequestAttributes requestAttributes);

  // get(), update(), delete() メソッドも同様...
}
```

**システムレベルとの違い**:
- ✅ 第一引数: `OrganizationIdentifier` **（追加）**
- ✅ 第二引数: `TenantIdentifier`
- ✅ 権限型: `OrganizationAdminPermissions`（システムの`AdminPermissions`ではない）

---

## Step 2: EntryService実装（UseCase層）

**ファイル**: `libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/control_plane/organization_manager/OrgRoleManagementEntryService.java`

```java
package org.idp.server.usecases.control_plane.organization_manager;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.base.AuditLogCreator;
import org.idp.server.control_plane.base.definition.OrganizationAdminPermissions;
import org.idp.server.control_plane.management.role.*;
import org.idp.server.control_plane.organization.access.OrganizationAccessControlResult;
import org.idp.server.control_plane.organization.access.OrganizationAccessVerifier;
import org.idp.server.core.openid.identity.Role;
import org.idp.server.core.openid.identity.RoleCommandRepository;
import org.idp.server.core.openid.identity.RoleQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogPublisher;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.organization.Organization;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.organization.OrganizationRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

@Transaction
public class OrgRoleManagementEntryService implements OrgRoleManagementApi {

  TenantQueryRepository tenantQueryRepository;
  OrganizationRepository organizationRepository;
  RoleCommandRepository roleCommandRepository;
  RoleQueryRepository roleQueryRepository;
  AuditLogPublisher auditLogPublisher;
  OrganizationAccessVerifier organizationAccessVerifier;  // ✅ 組織アクセス検証
  LoggerWrapper log = LoggerWrapper.getLogger(OrgRoleManagementEntryService.class);

  public OrgRoleManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      OrganizationRepository organizationRepository,
      RoleCommandRepository roleCommandRepository,
      RoleQueryRepository roleQueryRepository,
      AuditLogPublisher auditLogPublisher,
      OrganizationAccessVerifier organizationAccessVerifier) {
    this.tenantQueryRepository = tenantQueryRepository;
    this.organizationRepository = organizationRepository;
    this.roleCommandRepository = roleCommandRepository;
    this.roleQueryRepository = roleQueryRepository;
    this.auditLogPublisher = auditLogPublisher;
    this.organizationAccessVerifier = organizationAccessVerifier;
  }

  @Override
  public RoleManagementResponse create(
      OrganizationIdentifier organizationIdentifier,
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      RoleRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // 1. 必要権限を取得
    OrganizationAdminPermissions permissions = getRequiredPermissions("create");

    // 2. Organization/Tenant取得
    Organization organization = organizationRepository.get(organizationIdentifier);
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    // ✅ 3. **4ステップアクセス制御**（最重要）
    OrganizationAccessControlResult accessControl =
        organizationAccessVerifier.verify(
            organization,
            tenant,
            operator,
            permissions);

    if (!accessControl.isAuthorized()) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put("error_description", accessControl.reason());
      log.warn(response.toString());
      return new RoleManagementResponse("FORBIDDEN", response);
    }

    // 4. Context Creator使用
    RoleRegistrationContextCreator contextCreator =
        new RoleRegistrationContextCreator(tenant, request, dryRun);
    RoleRegistrationContext context = contextCreator.create();

    // 5. Audit Log記録
    AuditLog auditLog =
        AuditLogCreator.create(
            "OrgRoleManagementApi.create",
            organization,  // ✅ organization追加
            tenant,
            operator,
            oAuthToken,
            context,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    // 6. Dry Runチェック
    if (dryRun) {
      return context.toResponse();
    }

    // 7. Repository保存
    roleCommandRepository.register(tenant, context.role());

    // 8. レスポンス返却
    return context.toResponse();
  }

  // findList(), get(), update(), delete() メソッドも同様のパターン...
}
```

### 最重要: 4ステップアクセス制御

**`OrganizationAccessVerifier.verify()`**が自動的に以下を検証：

1. **組織メンバーシップ検証**: ユーザーが組織メンバーか？
2. **テナントアクセス検証**: ユーザーがテナントにアクセス可能か？
3. **組織-テナント関係検証**: テナントが組織に属しているか？
4. **権限検証**: ユーザーが必要な権限を持っているか？

**実装者への警告**: この検証を省略すると、**他の組織のリソースにアクセスできてしまう**セキュリティ脆弱性になります。

---

### OrganizationAccessVerifier の使用方法

```java
// ✅ 正しい使用
OrganizationAccessControlResult accessControl =
    organizationAccessVerifier.verify(
        organization,        // 組織
        tenant,             // テナント
        operator,           // ユーザー
        permissions);       // 必要権限

if (!accessControl.isAuthorized()) {
    // アクセス拒否
    return new RoleManagementResponse("FORBIDDEN", errorResponse);
}

// ❌ 間違い: 検証をスキップ
// if (!permissions.includesAll(operator.permissionsAsSet())) {
//     // 組織関係の検証が抜けている！
// }
```

---

## Step 3: Controller実装（Controller層）

**ファイル**: `libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapter/springboot/controller/management/OrgRoleManagementController.java`

```java
package org.idp.server.adapter.springboot.controller.management;

import org.idp.server.control_plane.management.role.*;
import org.idp.server.core.openid.identity.RoleIdentifier;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.organization.OrganizationIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.type.RequestAttributes;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/management/organizations/{orgId}/tenants/{tenantId}/roles")
public class OrgRoleManagementController {

  private final OrgRoleManagementApi orgRoleManagementApi;

  public OrgRoleManagementController(OrgRoleManagementApi orgRoleManagementApi) {
    this.orgRoleManagementApi = orgRoleManagementApi;
  }

  /**
   * ロール作成
   */
  @PostMapping
  public ResponseEntity<RoleManagementResponse> create(
      @PathVariable("orgId") String orgId,        // ✅ 組織ID追加
      @PathVariable("tenantId") String tenantId,
      @RequestBody RoleRegistrationRequest request,
      @RequestParam(value = "dry_run", defaultValue = "false") boolean dryRun,
      @AuthenticationPrincipal User operator,
      @RequestAttribute OAuthToken oAuthToken,
      @RequestAttribute RequestAttributes requestAttributes) {

    OrganizationIdentifier organizationIdentifier = new OrganizationIdentifier(orgId);
    TenantIdentifier tenantIdentifier = new TenantIdentifier(tenantId);

    RoleManagementResponse response =
        orgRoleManagementApi.create(
            organizationIdentifier,  // ✅ 組織ID追加
            tenantIdentifier,
            operator,
            oAuthToken,
            request,
            requestAttributes,
            dryRun);

    return ResponseEntity.ok(response);
  }

  /**
   * ロール一覧取得
   */
  @GetMapping
  public ResponseEntity<RoleManagementResponse> findList(
      @PathVariable("orgId") String orgId,
      @PathVariable("tenantId") String tenantId,
      @AuthenticationPrincipal User operator,
      @RequestAttribute OAuthToken oAuthToken,
      @RequestAttribute RequestAttributes requestAttributes) {

    OrganizationIdentifier organizationIdentifier = new OrganizationIdentifier(orgId);
    TenantIdentifier tenantIdentifier = new TenantIdentifier(tenantId);
    RoleQueries queries = new RoleQueries();

    RoleManagementResponse response =
        orgRoleManagementApi.findList(
            organizationIdentifier,
            tenantIdentifier,
            operator,
            oAuthToken,
            queries,
            requestAttributes);

    return ResponseEntity.ok(response);
  }

  // get(), update(), delete() メソッドも同様...
}
```

**システムレベルとの違い**:
- ✅ URL: `/organizations/{orgId}/tenants/{tenantId}/...`
- ✅ `@PathVariable`: `orgId`と`tenantId`の両方

---

## Step 4: E2Eテスト作成

**ファイル**: `e2e/spec/management/org-role-management.spec.js`

```javascript
const { v4: uuidv4 } = require('uuid');
const axios = require('axios');

describe('Organization Role Management API', () => {
  let orgAdminToken;
  let organizationId;
  let tenantId;
  let roleId;

  beforeAll(async () => {
    // 1. 組織管理者トークン取得
    const tokenResponse = await axios.post('http://localhost:8080/oauth/token', {
      grant_type: 'client_credentials',
      client_id: 'org-admin-client',
      client_secret: 'org-admin-secret',
      scope: 'org:role:read org:role:write'  // ⚠️ 組織専用スコープ
    });
    orgAdminToken = tokenResponse.data.access_token;

    // 2. テスト組織作成
    const orgResponse = await axios.post(
      'http://localhost:8080/v1/management/organizations',
      {
        name: 'test-organization',
        display_name: 'Test Organization'
      },
      {
        headers: {
          Authorization: `Bearer ${orgAdminToken}`
        }
      }
    );
    organizationId = orgResponse.data.organization_id;

    // 3. テスト組織配下にテナント作成
    const tenantResponse = await axios.post(
      `http://localhost:8080/v1/management/organizations/${organizationId}/tenants`,
      {
        name: 'test-tenant',
        display_name: 'Test Tenant for Org Role Management'
      },
      {
        headers: {
          Authorization: `Bearer ${orgAdminToken}`
        }
      }
    );
    tenantId = tenantResponse.data.tenant_id;

    roleId = uuidv4();
  });

  test('should create role in organization tenant', async () => {
    const response = await axios.post(
      `http://localhost:8080/v1/management/organizations/${organizationId}/tenants/${tenantId}/roles`,
      {
        role_id: roleId,
        role_name: 'Org Admin Role',
        description: 'Organization administrator role',
        permissions: ['org:tenant:read', 'org:tenant:write']
      },
      {
        headers: {
          Authorization: `Bearer ${orgAdminToken}`
        }
      }
    );

    expect(response.status).toBe(200);
    expect(response.data.status).toBe('SUCCESS');
    expect(response.data.result).toHaveProperty('role_id', roleId);
  });

  test('should return 403 when accessing different organization tenant', async () => {
    // 別の組織のテナントにアクセス試行
    const anotherTenantId = uuidv4();

    try {
      await axios.post(
        `http://localhost:8080/v1/management/organizations/${organizationId}/tenants/${anotherTenantId}/roles`,
        {
          role_id: uuidv4(),
          role_name: 'Forbidden Role',
          permissions: []
        },
        {
          headers: {
            Authorization: `Bearer ${orgAdminToken}`
          }
        }
      );
      fail('Expected 403 error');
    } catch (error) {
      expect(error.response.status).toBe(403);
    }
  });

  test('should return 403 when organization-tenant relationship is invalid', async () => {
    // 別の組織のテナントID + 異なる組織ID
    const anotherOrgId = uuidv4();

    try {
      await axios.post(
        `http://localhost:8080/v1/management/organizations/${anotherOrgId}/tenants/${tenantId}/roles`,
        {
          role_id: uuidv4(),
          role_name: 'Invalid Org Role',
          permissions: []
        },
        {
          headers: {
            Authorization: `Bearer ${orgAdminToken}`
          }
        }
      );
      fail('Expected 403 error');
    } catch (error) {
      expect(error.response.status).toBe(403);
      expect(error.response.data.error_description).toContain('organization-tenant relationship');
    }
  });
});
```

**システムレベルとの違い**:
- ✅ 組織作成 → テナント作成の順序
- ✅ 組織専用スコープ（`org:role:read`, `org:role:write`）
- ✅ 組織関係検証のテスト追加

---

## チェックリスト

組織レベルAPI実装前に以下を確認：

### API契約定義（Control Plane層）
- [ ] インターフェース定義（`Org{Domain}ManagementApi`）
- [ ] 第一引数: `OrganizationIdentifier`、第二引数: `TenantIdentifier`
- [ ] `defaultメソッド`で権限定義（`OrganizationAdminPermissions`使用）
- [ ] Request/Response DTO作成（システムレベルと同じものを再利用可能）
- [ ] Context Creator作成（システムレベルと同じものを再利用可能）

### EntryService実装（UseCase層）
- [ ] `@Transaction`アノテーション付与
- [ ] **`OrganizationAccessVerifier.verify()`実装**（最重要）
- [ ] アクセス拒否時の適切なエラーレスポンス
- [ ] Audit Log記録（組織情報含む）
- [ ] Context Creator使用
- [ ] Dry Run対応

### Controller実装（Controller層）
- [ ] URL: `/organizations/{orgId}/tenants/{tenantId}/...`
- [ ] `@PathVariable`: `orgId`と`tenantId`の両方
- [ ] 型変換のみ（ロジック禁止）

### E2Eテスト
- [ ] 正常系テスト（CREATE/READ/UPDATE/DELETE）
- [ ] **組織関係検証テスト**（別組織のテナントにアクセス試行）
- [ ] 権限エラーテスト（403）

---

## よくあるエラー

### エラー1: OrganizationAccessVerifier未使用

```java
// ❌ 間違い: システムレベルと同じ権限チェックのみ
if (!permissions.includesAll(operator.permissionsAsSet())) {
    throw new ForbiddenException("Permission denied");
}
// 組織関係の検証が抜けている！

// ✅ 正しい: OrganizationAccessVerifier使用
OrganizationAccessControlResult accessControl =
    organizationAccessVerifier.verify(organization, tenant, operator, permissions);
if (!accessControl.isAuthorized()) {
    return new RoleManagementResponse("FORBIDDEN", errorResponse);
}
```

### エラー2: AdminPermissions使用

```java
// ❌ 間違い: システムレベルの権限型
AdminPermissions permissions = getRequiredPermissions("create");

// ✅ 正しい: 組織レベルの権限型
OrganizationAdminPermissions permissions = getRequiredPermissions("create");
```

### エラー3: Audit Log に組織情報なし

```java
// ❌ 間違い: 組織情報なし
AuditLog auditLog = AuditLogCreator.create(
    "OrgRoleManagementApi.create",
    tenant,
    operator,
    oAuthToken,
    context,
    requestAttributes);

// ✅ 正しい: 組織情報含む
AuditLog auditLog = AuditLogCreator.create(
    "OrgRoleManagementApi.create",
    organization,  // ✅ 組織追加
    tenant,
    operator,
    oAuthToken,
    context,
    requestAttributes);
```

---

## 次のステップ

✅ 組織レベルAPI実装をマスターした！

### 📖 次に読むべきドキュメント

1. [Repository実装ガイド](../04-implementation-guides/impl-10-repository-implementation.md) - データアクセス層の実装
2. [Plugin実装ガイド](../04-implementation-guides/impl-12-plugin-implementation.md) - 拡張機能の実装

### 🔗 詳細情報

- [AI開発者向け: Control Plane詳細](../../content_10_ai_developer/ai-13-control-plane.md#組織レベルapi)

---

**情報源**: [OrgRoleManagementEntryService.java](../../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/control_plane/organization_manager/OrgRoleManagementEntryService.java)
**最終更新**: 2025-10-12
