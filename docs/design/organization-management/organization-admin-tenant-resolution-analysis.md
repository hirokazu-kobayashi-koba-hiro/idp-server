# idp-server 組織管理テナント解決の仕組み分析

## 現在のデータベース構造

### テーブル構成
```sql
-- 組織テーブル
CREATE TABLE organization (
    id          UUID                    NOT NULL,
    name        VARCHAR(255)            NOT NULL,
    description TEXT,
    created_at  TIMESTAMP DEFAULT now() NOT NULL,
    updated_at  TIMESTAMP DEFAULT now() NOT NULL,
    PRIMARY KEY (id)
);

-- テナントテーブル
CREATE TABLE tenant (
    id                     UUID         NOT NULL,
    name                   VARCHAR(255) NOT NULL,
    type                   VARCHAR(10)  NOT NULL,  -- "BUSINESS", "ORGANIZER" など
    domain                 TEXT         NOT NULL,
    authorization_provider VARCHAR(255) NOT NULL,
    database_type          VARCHAR(255) NOT NULL,
    attributes             JSONB,
    features               JSONB,
    created_at             TIMESTAMP    NOT NULL DEFAULT now(),
    PRIMARY KEY (id)
);

-- 組織-テナント関連付けテーブル
CREATE TABLE organization_tenants (
    id              UUID      DEFAULT gen_random_uuid() NOT NULL,
    organization_id UUID                                NOT NULL,
    tenant_id       UUID                                NOT NULL,
    assigned_at     TIMESTAMP DEFAULT now()             NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (organization_id) REFERENCES organization (id) ON DELETE CASCADE,
    FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE CASCADE,
    UNIQUE (organization_id, tenant_id)
);
```

## 現在の実装の問題点

### 1. **管理テナントの明示的な識別不足**
```sql
-- 現在: どのテナントが組織の管理テナントか不明
SELECT t.* FROM tenant t
JOIN organization_tenants ot ON t.id = ot.tenant_id
WHERE ot.organization_id = 'org-123';

-- 結果: 組織に属する全テナントが返される（管理テナントの区別なし）
```

### 2. **OrganizationRepositoryでの管理テナント解決メソッド不足**
```java
public interface OrganizationRepository {
  void register(Tenant tenant, Organization organization);
  void update(Tenant tenant, Organization organization);
  Organization get(Tenant tenant, OrganizationIdentifier identifier);
  List<Organization> findList(Tenant tenant, OrganizationQueries queries);
  AssignedTenant findAssignment(Tenant adminTenant, OrganizationIdentifier organizationId, TenantIdentifier tenantId);

  // ❌ 不足: 組織IDから管理テナントを解決するメソッド
  // TenantIdentifier findAdminTenantByOrganization(OrganizationIdentifier organizationId);
}
```

### 3. **テナントタイプによる管理テナント判定の曖昧性**
```java
// 現在の AssignedTenant
public class AssignedTenant {
  String id;
  String name;
  String type;  // "BUSINESS", "ORGANIZER"

  // ❌ type="ORGANIZER" が管理テナントを意味するのか不明確
}
```

## 解決アプローチの検討

### アプローチ1: テナントタイプによる管理テナント判定
```java
public class OrganizationAdminTenantResolver {
  private final OrganizationRepository organizationRepository;

  public TenantIdentifier resolveAdminTenant(OrganizationIdentifier organizationId) {
    // 1. 組織に属するテナント一覧を取得
    Organization organization = organizationRepository.get(systemTenant, organizationId);

    // 2. type="ORGANIZER" のテナントを管理テナントとして判定
    return organization.assignedTenants().stream()
        .filter(tenant -> "ORGANIZER".equals(tenant.type()))
        .map(tenant -> new TenantIdentifier(tenant.id()))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException(
            "No admin tenant found for organization: " + organizationId.value()));
  }
}
```

### アプローチ2: organization テーブルに admin_tenant_id カラム追加
```sql
-- スキーマ拡張
ALTER TABLE organization ADD COLUMN admin_tenant_id UUID;
ALTER TABLE organization ADD FOREIGN KEY (admin_tenant_id) REFERENCES tenant (id);

-- 使用例
SELECT t.* FROM organization o
JOIN tenant t ON o.admin_tenant_id = t.id
WHERE o.id = 'org-123';
```

```java
// Organizationエンティティの拡張
public class Organization {
  OrganizationIdentifier identifier;
  OrganizationName name;
  OrganizationDescription description;
  TenantIdentifier adminTenantId;  // 追加
  AssignedTenants assignedTenants;

  public TenantIdentifier adminTenantId() {
    return adminTenantId;
  }
}
```

### アプローチ3: organization_tenants テーブルに role カラム追加
```sql
-- スキーマ拡張
ALTER TABLE organization_tenants ADD COLUMN role VARCHAR(50) DEFAULT 'member';

-- データ例
INSERT INTO organization_tenants (organization_id, tenant_id, role) VALUES
  ('org-123', 'admin-tenant-456', 'admin'),
  ('org-123', 'business-tenant-789', 'member');

-- 管理テナント取得クエリ
SELECT t.* FROM organization_tenants ot
JOIN tenant t ON ot.tenant_id = t.id
WHERE ot.organization_id = 'org-123' AND ot.role = 'admin';
```

## 推奨実装: アプローチ1（最小限の変更）

### 理由
1. **既存スキーマとの互換性**: データベース変更不要
2. **実装の簡易性**: ビジネスロジックのみの変更
3. **段階的導入**: 既存コードへの影響最小限

### 具体的実装

#### 1. OrganizationRepository インターフェース拡張
```java
public interface OrganizationRepository {
  // 既存メソッド...

  /**
   * 組織の管理テナントを解決する
   * @param organizationId 組織ID
   * @return 管理テナントID
   * @throws IllegalStateException 管理テナントが見つからない場合
   */
  TenantIdentifier findAdminTenantByOrganization(OrganizationIdentifier organizationId);
}
```

#### 2. OrganizationDataSource実装
```java
@Override
public TenantIdentifier findAdminTenantByOrganization(OrganizationIdentifier organizationId) {
  // 1. 組織取得
  Organization organization = get(null, organizationId); // null = system context

  // 2. ORGANIZER タイプのテナントを管理テナントとして解決
  return organization.assignedTenants().stream()
      .filter(tenant -> "ORGANIZER".equals(tenant.type()))
      .map(tenant -> new TenantIdentifier(tenant.id()))
      .findFirst()
      .orElseThrow(() -> new IllegalStateException(
          "No admin tenant (type=ORGANIZER) found for organization: " + organizationId.value()));
}
```

#### 3. SQL Executor 拡張
```java
public interface OrganizationSqlExecutor {
  // 既存メソッド...

  /**
   * 組織の管理テナント取得
   * @param organizationId 組織ID
   * @return 管理テナント情報
   */
  Map<String, String> selectAdminTenant(OrganizationIdentifier organizationId);
}
```

```java
// PostgreSQL実装
@Override
public Map<String, String> selectAdminTenant(OrganizationIdentifier organizationId) {
  String sql = """
      SELECT t.id, t.name, t.type
      FROM organization_tenants ot
      JOIN tenant t ON ot.tenant_id = t.id
      WHERE ot.organization_id = ? AND t.type = 'ORGANIZER'
      LIMIT 1
      """;

  return selectOne(sql, organizationId.value());
}
```

#### 4. ManagementApiFilter での使用
```java
public class OrganizationAwareManagementApiFilter {
  private final OrganizationRepository organizationRepository;

  private TenantIdentifier resolveAdminTenant(OrganizationIdentifier organizationId) {
    try {
      return organizationRepository.findAdminTenantByOrganization(organizationId);
    } catch (IllegalStateException e) {
      throw new UnauthorizedException("No admin tenant found for organization: "
                                    + organizationId.value());
    }
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request,
                                 HttpServletResponse response,
                                 FilterChain filterChain) {
    // 1. 組織ID取得
    OrganizationIdentifier orgId = OrganizationResolver.resolve(request);

    // 2. 管理テナント解決
    TenantIdentifier adminTenant = resolveAdminTenant(orgId);

    // 3. 認証実行
    AuthenticationResult result = userAuthenticationApi.authenticate(
        adminTenant, authorization, clientCert);

    // 4. 後続処理...
  }
}
```

### 実装の流れ

#### Phase 1: 基本実装
1. `OrganizationRepository.findAdminTenantByOrganization()` メソッド追加
2. `OrganizationDataSource` での実装
3. SQL Executor での実装

#### Phase 2: 統合
1. `ManagementApiFilter` での組織管理テナント解決
2. エラーハンドリングの追加
3. ログとモニタリング

#### Phase 3: 最適化
1. 管理テナント解決結果のキャッシュ
2. パフォーマンス測定と改善
3. 運用ドキュメントの整備

## まとめ

**現状**: 組織IDから管理テナントを解決する仕組みが存在しない

**推奨解決策**: テナントタイプ（ORGANIZER）による管理テナント判定

**実装のポイント**:
1. 既存スキーマを活用（database schema 変更不要）
2. `type='ORGANIZER'` のテナントを管理テナントとして扱う
3. `OrganizationRepository` にメソッド追加
4. エラーハンドリング（管理テナント未設定の組織対応）

この実装により、GitHubスタイルの組織固定トークン発行が可能になり、ManagementApiFilter でのセキュアな認証が実現できます。