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

---

## システムレベルとの差分

### 再利用される部分（二重開発不要）

以下はシステムレベルAPIと**完全に共通**です：

- ✅ **Service**: ClientCreationService、ClientUpdateService等
- ✅ **Context/ContextBuilder**: ClientManagementContext、ClientManagementContextBuilder
- ✅ **Request/Response DTO**: すべて共通
- ✅ **Validator**: すべて共通
- ✅ **Repository**: すべて共通

**重要**: これらは一度システムレベルで実装すれば、組織レベルで**そのまま再利用**できます。

---

### 組織レベル固有の実装（新規作成が必要）

#### 1. OrgXxxManagementHandler（最重要）

**システムレベルとの違い**:
- `XxxManagementHandler` → `OrgXxxManagementHandler`
- **OrganizationAccessVerifier追加** ← 4ステップアクセス制御

**実装例**: [OrgClientManagementHandler.java](../../../../libs/idp-server-control-plane/src/main/java/org/idp/server/control_plane/management/oidc/client/handler/OrgClientManagementHandler.java)

**差分**（システムレベルのHandlerとの違い）:
```java
// System-level
public ClientManagementResult handle(
    AdminAuthenticationContext authenticationContext,
    TenantIdentifier tenantIdentifier, ...) {

  // Tenant取得
  Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

  // 権限チェック
  apiPermissionVerifier.verify(operator, requiredPermissions);

  // Serviceに委譲
  ...
}

// Organization-level（追加部分のみ）
public ClientManagementResult handle(
    OrganizationAuthenticationContext authenticationContext, // ← 引数変更
    TenantIdentifier tenantIdentifier, ...) {

  // Organization取得（追加）
  Organization organization = authenticationContext.organization();

  // Tenant取得
  Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

  // 4ステップアクセス制御（追加） ← これが最重要
  OrganizationAccessControlResult accessControl =
      organizationAccessVerifier.verify(organization, tenant, operator, requiredPermissions);

  if (!accessControl.isAuthorized()) {
    // アクセス拒否
  }

  // Serviceに委譲（システムレベルと同じServiceを再利用）
  ...
}
```

**ポイント**:
- ✅ **Serviceは再利用**: System-levelと同じServiceをそのまま使う
- ✅ **追加処理は4ステップアクセス制御のみ**

---

#### 2. OrgXxxManagementApi

**システムレベルとの違い**:
- 第一引数に`OrganizationIdentifier`を追加

```java
// System-level
RoleManagementResponse create(
    TenantIdentifier tenantIdentifier, ...);

// Organization-level
RoleManagementResponse create(
    OrganizationIdentifier organizationIdentifier, // ← 追加
    TenantIdentifier tenantIdentifier, ...);
```

---

#### 3. OrgXxxManagementEntryService

**システムレベルとの違い**:
- `OrgXxxManagementHandler`を使用
- `ManagementTypeEntryServiceProxy`を使用（Proxy選択のみ）

**実装パターン**（システムレベルとほぼ同じ）:
```java
@Transaction
public class OrgXxxManagementEntryService implements OrgXxxManagementApi {

  private final OrgXxxManagementHandler handler; // ← Org用Handler使用

  public OrgXxxManagementEntryService(...) {
    // Serviceマップ登録（システムレベルと同じServiceを再利用）
    Map<String, XxxManagementService<?>> services = new HashMap<>();
    services.put("create", new XxxCreationService(...)); // ← 再利用
    services.put("update", new XxxUpdateService(...));   // ← 再利用

    // Org用Handler（OrganizationAccessVerifier追加）
    this.handler = new OrgXxxManagementHandler(
        services, this, tenantQueryRepository, new OrganizationAccessVerifier());
  }

  // メソッドはシステムレベルと同じ3ステップパターン
}
```

---

## 実装手順（システムレベルとの差分のみ）

### Step 1: OrgXxxManagementHandler作成

**新規作成するファイル**:
```
libs/idp-server-control-plane/src/main/java/org/idp/server/control_plane/management/{domain}/handler/
└── Org{Domain}ManagementHandler.java  ← これだけ
```

**実装内容**:
- システムレベルのHandlerをコピー
- `OrganizationAccessVerifier`の呼び出しを追加
- Organization取得処理を追加

**実装の参考**: [OrgClientManagementHandler.java](../../../../libs/idp-server-control-plane/src/main/java/org/idp/server/control_plane/management/oidc/client/handler/OrgClientManagementHandler.java)

---

### Step 2: OrgXxxManagementApi作成

**新規作成するファイル**:
```
libs/idp-server-control-plane/src/main/java/org/idp/server/control_plane/management/{domain}/
└── Org{Domain}ManagementApi.java
```

**実装内容**:
- システムレベルのAPIをコピー
- 各メソッドの第一引数に`OrganizationIdentifier`を追加

---

### Step 3: OrgXxxManagementEntryService作成

**新規作成するファイル**:
```
libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/control_plane/organization_manager/
└── Org{Domain}ManagementEntryService.java
```

**実装内容**:
- システムレベルのEntryServiceをコピー
- `OrgXxxManagementHandler`を使用
- `ManagementTypeEntryServiceProxy`を使用（Proxy選択のみ変更）

**実装の参考**: [OrgClientManagementEntryService.java](../../../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/control_plane/organization_manager/OrgClientManagementEntryService.java)

---

### Step 4: Controller作成

システムレベルと同様。URL pathに`organizations/{orgId}`が追加されるだけ。

---

### Step 5: E2Eテスト作成

組織作成 → テナント作成 → リソース作成のフローでテスト。

---

## 重要なポイント

### ✅ やること（最小限）

1. **OrgXxxManagementHandler作成** - 4ステップアクセス制御追加
2. **OrgXxxManagementApi作成** - organizationId引数追加
3. **OrgXxxManagementEntryService作成** - Org用Handler使用

### ❌ やらないこと

1. Service再実装 - システムレベルのServiceを再利用
2. Context再実装 - 同じContextBuilderを再利用
3. Request/Response DTO再実装 - すべて再利用
4. Validator再実装 - すべて再利用

**実装量**: システムレベルの**約20%**のみ（Handlerとラッパーのみ）

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

### IdpServerApplication登録
- [ ] フィールド追加
- [ ] **`ManagementTypeEntryServiceProxy`使用**（Organization-level Control Plane）
- [ ] Getterメソッド追加

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

### エラー4: Proxy選択ミス

```java
// ❌ 間違い: Organization-level Control PlaneにTenantAwareEntryServiceProxy
this.orgRoleManagementApi =
    TenantAwareEntryServiceProxy.createProxy(  // 間違い
        new OrgRoleManagementEntryService(...),
        OrgRoleManagementApi.class,
        databaseTypeProvider);

// ✅ 正しい: Organization-level Control PlaneはManagementTypeEntryServiceProxy
this.orgRoleManagementApi =
    ManagementTypeEntryServiceProxy.createProxy(  // 正しい
        new OrgRoleManagementEntryService(...),
        OrgRoleManagementApi.class,
        databaseTypeProvider);
```

**判断基準**: レイヤーで決まる
- Application Plane / System-level Control Plane → `TenantAwareEntryServiceProxy`
- Organization-level Control Plane → `ManagementTypeEntryServiceProxy`

---

## 次のステップ

✅ 組織レベルAPI実装をマスターした！

### 📖 次に読むべきドキュメント

1. [Repository実装ガイド](../04-implementation-guides/impl-10-repository-implementation.md) - データアクセス層の実装
2. [Plugin実装ガイド](../04-implementation-guides/impl-12-plugin-implementation.md) - 拡張機能の実装

---

**情報源**: [OrgRoleManagementEntryService.java](../../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/control_plane/organization_manager/OrgRoleManagementEntryService.java)
**最終更新**: 2025-10-12
