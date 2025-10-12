# Control Plane Track（管理API実装者向け）

## 🎯 このトラックの目標

**管理API（Control Plane）の実装**ができるようになる。

- システムレベルAPI実装（CRUD操作）
- Repository実装（Query/Command分離）
- Context Creator作成
- 組織レベルAPI実装（4ステップアクセス制御）

**所要期間**: 2-4週間

**前提**: [初級ラーニングパス](./01-beginner.md)完了

---

## 📅 学習スケジュール

### Week 1: システムレベルAPI実装

#### Day 1-2: 最初のAPI実装チュートリアル
- [ ] **所要時間**: 4時間
- [ ] [02. 最初のAPI実装](../02-control-plane/02-first-api.md)を実施
- [ ] テナント名取得APIを実装

**実践課題**:
```java
// 以下のAPIを実装する
GET /v1/management/tenants/{tenantId}/name

レスポンス:
{
  "tenant_id": "...",
  "name": "...",
  "display_name": "..."
}
```

**チェックポイント**:
- [ ] API契約定義（インターフェース）を作成できる
- [ ] EntryServiceを実装できる
- [ ] Controllerを実装できる
- [ ] E2Eテストを作成できる

---

#### Day 3-5: システムレベルAPI（CRUD）実装
- [ ] **所要時間**: 10時間
- [ ] [システムレベルAPI実装ガイド](../02-control-plane/03-system-level-api.md)を実施
- [ ] Role管理API（CRUD）を実装

**実践課題**:
```java
// 以下のAPIを実装する
POST   /v1/management/tenants/{tenantId}/roles         // 作成
GET    /v1/management/tenants/{tenantId}/roles         // 一覧
GET    /v1/management/tenants/{tenantId}/roles/{id}    // 取得
PUT    /v1/management/tenants/{tenantId}/roles/{id}    // 更新
DELETE /v1/management/tenants/{tenantId}/roles/{id}    // 削除
```

**チェックリスト**:
- [ ] API契約定義（Control Plane層）
- [ ] Request/Response DTO作成
- [ ] Context Creator作成
- [ ] EntryService実装（権限チェック・Audit Log・Dry Run）
- [ ] Controller実装
- [ ] E2Eテスト作成（正常系・異常系）

**チェックポイント**:
- [ ] CRUD全操作を実装できる
- [ ] `defaultメソッド`を正しく使える（不要なオーバーライド回避）
- [ ] Dry Run対応を実装できる

---

### Week 2: Repository実装

#### Day 6-8: Repository実装
- [ ] **所要時間**: 8時間
- [ ] [Repository実装ガイド](../04-implementation-guides/impl-10-repository-implementation.md)を実施
- [ ] RoleQueryRepository/RoleCommandRepository実装

**実践課題**:
```java
// 以下のRepositoryを実装する

// Query Repository
public interface RoleQueryRepository {
    Role get(Tenant tenant, RoleIdentifier roleIdentifier);
    Role find(Tenant tenant, RoleIdentifier roleIdentifier);  // Null Object Pattern
    List<Role> findList(Tenant tenant, int limit, int offset);
    long findTotalCount(Tenant tenant);
}

// Command Repository
public interface RoleCommandRepository {
    void register(Tenant tenant, Role role);
    void update(Tenant tenant, Role role);
    void delete(Tenant tenant, RoleIdentifier roleIdentifier);
}

// DataSource実装
public class RoleDataSource implements RoleQueryRepository, RoleCommandRepository {
    private final SqlExecutor sqlExecutor;

    @Override
    public Role get(Tenant tenant, RoleIdentifier roleIdentifier) {
        String sql = "SELECT * FROM role WHERE tenant_id = ? AND role_id = ?";
        Map<String, Object> row = sqlExecutor.selectOne(sql, tenant.value(), roleIdentifier.value());
        return RoleMapper.map(row);
    }

    // ... 他のメソッド実装
}
```

**チェックリスト**:
- [ ] **Tenant第一引数**（全メソッド）
- [ ] Query/Command分離
- [ ] `TransactionManager.setTenantId()`実行（RLS対応）
- [ ] Mapper作成（データベース行 → ドメインモデル）
- [ ] **ビジネスロジック禁止**（SQLのみ）

**チェックポイント**:
- [ ] Repository命名規則を遵守できる
- [ ] DataSource-SqlExecutorパターンを実装できる
- [ ] RLS（Row Level Security）を理解している

---

#### Day 9-10: Context Creator実装
- [ ] **所要時間**: 6時間
- [ ] Context Creatorを0から実装

**実践課題**:
```java
// RoleRegistrationContextCreator実装
public class RoleRegistrationContextCreator {

    public RoleRegistrationContext create(RoleRegistrationRequest request) {
        // 1. DTO → ドメインモデル変換
        RoleIdentifier roleIdentifier = new RoleIdentifier(request.getRoleId());
        RoleName roleName = new RoleName(request.getName());
        RoleDescription description = new RoleDescription(request.getDescription());

        // 2. Permissions変換
        List<Permission> permissions = request.getPermissions().stream()
            .map(p -> new Permission(new PermissionIdentifier(p)))
            .collect(Collectors.toList());

        // 3. Context構築
        return new RoleRegistrationContext(
            roleIdentifier,
            roleName,
            description,
            new Permissions(permissions)
        );
    }
}
```

**チェックポイント**:
- [ ] Context Creatorの責務を理解している
- [ ] DTO → ドメインモデル変換を実装できる

---

### Week 3-4: 組織レベルAPI実装

#### Day 11-15: 組織レベルAPI実装
- [ ] **所要時間**: 20時間
- [ ] [組織レベルAPI実装ガイド](../02-control-plane/04-organization-level-api.md)を実施
- [ ] 組織Role管理API実装

**実践課題**:
```java
// 以下の組織レベルAPIを実装する
POST   /v1/management/organizations/{orgId}/tenants/{tenantId}/roles
GET    /v1/management/organizations/{orgId}/tenants/{tenantId}/roles
GET    /v1/management/organizations/{orgId}/tenants/{tenantId}/roles/{id}
PUT    /v1/management/organizations/{orgId}/tenants/{tenantId}/roles/{id}
DELETE /v1/management/organizations/{orgId}/tenants/{tenantId}/roles/{id}
```

**最重要**: 4ステップアクセス制御
```java
// OrganizationAccessVerifier必須使用
OrganizationAccessControlResult accessControl =
    organizationAccessVerifier.verify(
        organizationIdentifier,
        tenantIdentifier,
        operator,
        permissions);

if (!accessControl.isAuthorized()) {
    return new RoleManagementResponse("FORBIDDEN", errorResponse);
}
```

**チェックリスト**:
- [ ] `OrganizationAccessVerifier.verify()`実装
- [ ] 組織情報をAudit Logに含む
- [ ] E2Eテスト（組織関係検証テスト含む）

**チェックポイント**:
- [ ] 4ステップアクセス制御を説明できる
- [ ] 組織-テナント関係検証の重要性を理解している
- [ ] システムレベルとの違いを説明できる

---

#### Day 16-20: 複雑なアクセス制御実装
- [ ] **所要時間**: 20時間
- [ ] 組織Permission管理API実装
- [ ] 組織User管理API実装

**実践課題**:
```
以下の複雑なアクセス制御を実装：
1. 組織管理者のみが実行可能なAPI
2. テナント所有者のみが実行可能なAPI
3. 組織-テナント関係が存在しない場合のエラー処理
```

**セキュリティチェック**:
```java
// ❌ 危険: 組織関係検証なし
if (!permissions.includesAll(operator.permissionsAsSet())) {
    throw new ForbiddenException("Permission denied");
}
// 他の組織のリソースにアクセスできてしまう！

// ✅ 安全: OrganizationAccessVerifier使用
OrganizationAccessControlResult accessControl =
    organizationAccessVerifier.verify(organizationIdentifier, tenantIdentifier, operator, permissions);
if (!accessControl.isAuthorized()) {
    return errorResponse;
}
```

**チェックポイント**:
- [ ] 複雑なアクセス制御を実装できる
- [ ] セキュリティ脆弱性を回避できる

---

## 📚 必読ドキュメント

| 優先度 | ドキュメント | 所要時間 |
|-------|------------|---------|
| 🔴 必須 | [02. 最初のAPI実装](../02-control-plane/02-first-api.md) | 30分 |
| 🔴 必須 | [03. システムレベルAPI](../02-control-plane/03-system-level-api.md) | 45分 |
| 🔴 必須 | [04. 組織レベルAPI](../02-control-plane/04-organization-level-api.md) | 60分 |
| 🔴 必須 | [Repository実装ガイド](../04-implementation-guides/impl-10-repository-implementation.md) | 30分 |
| 🟡 推奨 | [AI開発者向け: Use-Cases詳細](../../content_10_ai_developer/ai-10-use-cases.md) | 60分 |
| 🟡 推奨 | [AI開発者向け: Control Plane詳細](../../content_10_ai_developer/ai-13-control-plane.md) | 60分 |

---

## ✅ 完了判定基準

以下をすべて達成したらControl Plane Trackクリア：

### 知識面
- [ ] EntryServiceの10フェーズを説明できる
- [ ] Context Creatorの役割を説明できる
- [ ] Repository第一引数がTenantである理由を説明できる
- [ ] RLS（Row Level Security）の仕組みを説明できる
- [ ] 4ステップアクセス制御を説明できる

### 実践面
- [ ] システムレベルAPI（CRUD）を実装・マージした
- [ ] Repository（Query/Command）を実装・マージした
- [ ] 組織レベルAPIを実装・マージした
- [ ] E2Eテストを作成し、全件パスした
- [ ] レビューコメントが10件以下

### コード品質
- [ ] `./gradlew spotlessApply`を習慣化
- [ ] [コードレビューチェックリスト](../08-reference/code-review-checklist.md)を完全遵守
- [ ] Codex AIのレビューで指摘0件

---

## 🚀 次のステップ

Control Plane Track完了後の選択肢：

### Application Planeも学ぶ
認証フロー実装も習得したい場合：
- [Application Plane Track](./03-application-plane-track.md)

### Full Stack開発者へ
両方を統合した高度な実装を学ぶ：
- [Full Stack Track](./04-full-stack-track.md)

### 専門性を深める
Control Plane専門家として：
- アーキテクチャ設計
- 新規管理API設計
- チームメンバーのメンタリング

---

## 💡 Control Plane実装のヒント

### よくあるミス

#### 1. defaultメソッドをオーバーライド
```java
// ❌ 間違い: defaultメソッドを実装してしまう
@Override
public AdminPermissions getRequiredPermissions(String method) {
    // 不要な実装
}

// ✅ 正しい: defaultメソッドは実装不要
public class RoleManagementEntryService implements RoleManagementApi {
    // getRequiredPermissions()は実装不要！
}
```

#### 2. Context Creator未使用
```java
// ❌ 間違い: EntryServiceでDTO直接変換
Role role = new Role(new RoleIdentifier(request.getRoleId()), ...);

// ✅ 正しい: Context Creator使用
RoleRegistrationContextCreator creator = new RoleRegistrationContextCreator(...);
RoleRegistrationContext context = creator.create(request);
```

#### 3. Adapter層でビジネスロジック
```java
// ❌ 間違い: Adapter層でビジネス判定
if ("ORGANIZER".equals(tenant.type())) { ... }

// ✅ 正しい: SQLのみ
String sql = "SELECT * FROM role WHERE tenant_id = ? AND role_id = ?";
return RoleMapper.map(row);
```

---

### デバッグテクニック

#### ログ出力
```properties
# application.properties
logging.level.org.idp.server=DEBUG
logging.level.org.springframework.jdbc=DEBUG
```

#### SQL実行確認
```properties
logging.level.org.idp.server.platform.datasource.SqlExecutor=DEBUG
```

#### トランザクション確認
```properties
logging.level.org.springframework.transaction=DEBUG
```

---

### パフォーマンス最適化

#### 読み取り専用トランザクション
```java
// ✅ 最適化: 読み取り専用
@Override
@Transaction(readOnly = true)
public RoleManagementResponse findList(...) {
    // 読み取りのみ → パフォーマンス向上
}
```

#### N+1問題回避
```java
// ❌ N+1問題
List<Role> roles = roleRepository.findList(tenant, limit, offset);
for (Role role : roles) {
    List<Permission> permissions = permissionRepository.findByRole(tenant, role.identifier());
    // N+1回クエリ実行
}

// ✅ 一括取得
List<Role> roles = roleRepository.findListWithPermissions(tenant, limit, offset);
```

---

## 🔗 関連リソース

- [AI開発者向け: Use-Cases詳細](../../content_10_ai_developer/ai-10-use-cases.md)
- [AI開発者向け: Control Plane詳細](../../content_10_ai_developer/ai-13-control-plane.md)
- [AI開発者向け: Adapters詳細](../../content_10_ai_developer/ai-20-adapters.md)
- [開発者ガイドTOC](../DEVELOPER_GUIDE_TOC.md)

---

**最終更新**: 2025-10-13
**対象**: 管理API実装者（2-4週間）
**習得スキル**: システムレベルAPI、組織レベルAPI、Repository実装、Context Creator
