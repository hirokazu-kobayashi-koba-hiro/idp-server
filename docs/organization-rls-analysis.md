# 組織レベルテナント管理におけるRLS (Row Level Security) 分析

## 現状の実装分析

### TenantAwareEntryServiceProxy の現状
- **単一テナント分離**: 1つの`TenantIdentifier`のみでRLS設定
- **PostgreSQL固有のRLS**: `SET app.tenant_id = 'tenant-value'`を実行
- **組織概念の欠如**: 組織レベルの分離が考慮されていない

### TransactionManager の現状
```java
private static void setTenantId(Connection conn, TenantIdentifier tenantIdentifier) {
  log.debug("[RLS] SET app.tenant_id = '" + tenantIdentifier.value() + "'");
  try (var stmt = conn.createStatement()) {
    stmt.execute("SET app.tenant_id = '" + tenantIdentifier.value() + "'");
  } catch (SQLException e) {
    throw new SqlRuntimeException("Failed to set tenant_id", e);
  }
}
```

## 組織レベルテナント管理での課題

### 1. アクセス制御の複雑性
- **組織管理者の権限**: 複数テナントへのアクセスが必要
- **テナント間の分離**: 同一組織内でもテナント境界は維持したい
- **管理者テナント**: 管理操作を行うテナントと対象テナントの分離

### 2. 現在のRLS制限
- **単一テナントのみ**: 組織管理者が他のテナントにアクセスできない
- **組織境界なし**: 異なる組織のテナントへの誤アクセス防止ができない
- **管理コンテキスト不足**: どのテナントが管理操作を行っているか不明

### 3. データベースレベルの制限
```sql
-- 現状: 単一テナントのみアクセス可能
CREATE POLICY tenant_access_policy ON users
FOR ALL TO PUBLIC
USING (tenant_id = current_setting('app.tenant_id'));

-- 課題: 組織管理者は他のテナントのデータにアクセスできない
```

## 改善案の検討

### アプローチ1: 既存コードの最小限拡張
```java
// TenantAwareEntryServiceProxyの修正案
private TenantIdentifier resolveTenantIdentifier(Object[] args) {
  // 既存のTenantIdentifier解決に加えて
  // OrganizationIdentifier + adminTenant の組み合わせも考慮
}

// TransactionManagerの修正案
private static void setTenantId(Connection conn, TenantIdentifier tenantIdentifier,
                               OrganizationIdentifier organizationId) {
  if (organizationId != null) {
    stmt.execute("SET app.organization_id = '" + organizationId.value() + "'");
  }
  stmt.execute("SET app.tenant_id = '" + tenantIdentifier.value() + "'");
}
```

### アプローチ2: メソッドシグネチャの拡張
```java
// サービスメソッドに組織コンテキストを追加
public void updateTenant(
    OrganizationIdentifier orgId,     // 組織境界
    TenantIdentifier targetTenantId,  // 操作対象テナント
    TenantIdentifier adminTenantId,   // 管理者テナント
    TenantUpdateRequest request
);
```

### アプローチ3: ThreadLocalコンテキスト
```java
// 現在のリクエストコンテキストで組織情報を管理
public class OrganizationContextHolder {
  private static final ThreadLocal<OrganizationContext> context = new ThreadLocal<>();

  public static void setContext(OrganizationIdentifier orgId,
                               TenantIdentifier adminTenant) {
    // コンテキスト設定
  }
}
```

## PostgreSQLのRLS設計

### 現状のRLSポリシー
```sql
-- 単純なテナント分離
CREATE POLICY tenant_policy ON users
USING (tenant_id = current_setting('app.tenant_id'));
```

### 組織対応のRLSポリシー案
```sql
-- 組織境界内でのテナントアクセス
CREATE POLICY org_admin_policy ON users
USING (
  organization_id = current_setting('app.organization_id', true) AND
  (
    -- 通常のテナントアクセス
    tenant_id = current_setting('app.tenant_id', true) OR
    -- 管理者による他テナントアクセス (admin_tenant_idが設定されている場合)
    (current_setting('app.admin_tenant_id', true) IS NOT NULL AND
     tenant_id IN (
       SELECT id FROM tenants
       WHERE organization_id = current_setting('app.organization_id', true)
     ))
  )
);
```

## 推奨する改善戦略

### Phase 1: 最小限の拡張 (推奨)
1. **既存メソッドの引数追加**: 組織IDを追加パラメータとして受け取る
2. **RLS変数の拡張**: `app.organization_id`の追加
3. **既存コードとの互換性維持**: 組織IDがnullの場合は従来通り動作

### Phase 2: 段階的な機能拡張
1. **管理者コンテキスト**: `app.admin_tenant_id`の追加
2. **組織スコープRLS**: 組織境界内での柔軟なアクセス制御
3. **監査ログの強化**: 組織レベルでの操作追跡

### Phase 3: 最適化と統合
1. **パフォーマンス最適化**: RLSポリシーの効率化
2. **エラーハンドリング**: 組織境界違反の詳細なエラーメッセージ
3. **テスト拡充**: 組織レベルでの統合テスト

## 具体的な実装プラン

### 1. TransactionManager の最小限拡張
```java
public static void setTenantContext(Connection conn,
                                   TenantIdentifier tenantIdentifier,
                                   OrganizationIdentifier organizationId) {
  // 後方互換性を保ちつつ組織IDをサポート
  if (organizationId != null) {
    stmt.execute("SET app.organization_id = '" + organizationId.value() + "'");
  }
  stmt.execute("SET app.tenant_id = '" + tenantIdentifier.value() + "'");
}
```

### 2. サービスレイヤーでの引数追加
```java
// 段階的に組織対応のメソッドを追加
public interface TenantService {
  // 既存メソッド (互換性維持)
  void updateTenant(TenantIdentifier tenantId, TenantUpdateRequest request);

  // 組織対応の新メソッド
  void updateTenant(OrganizationIdentifier orgId,
                   TenantIdentifier tenantId,
                   TenantUpdateRequest request);
}
```

### 3. PostgreSQL RLS の段階的導入
```sql
-- 既存ポリシーを維持
CREATE POLICY legacy_tenant_policy ON users
USING (
  current_setting('app.organization_id', true) IS NULL AND
  tenant_id = current_setting('app.tenant_id', true)
);

-- 組織対応の新ポリシー
CREATE POLICY org_tenant_policy ON users
USING (
  current_setting('app.organization_id', true) IS NOT NULL AND
  organization_id = current_setting('app.organization_id', true) AND
  tenant_id = current_setting('app.tenant_id', true)
);
```

## 結論

**最小限の変更で最大の効果を狙う**アプローチを推奨します：

1. **新しいクラスは作成しない** - 既存のTenantAwareEntryServiceProxyとTransactionManagerを拡張
2. **メソッドオーバーロード** - 組織IDを受け取る新しいメソッドを追加
3. **後方互換性** - 既存のコードは変更せずに動作を維持
4. **段階的導入** - 組織機能から少しずつRLS対応を進める

この方針により、既存システムへの影響を最小限に抑えつつ、組織レベルのテナント管理を実現できます。