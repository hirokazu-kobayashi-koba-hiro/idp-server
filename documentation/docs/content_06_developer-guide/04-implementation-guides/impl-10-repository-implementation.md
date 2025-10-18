# Repository実装ガイド

## このドキュメントの目的

**Repository（Query/Command）**を、DataSource-SqlExecutorパターンで実装できるようになることが目標です。

### 所要時間
⏱️ **約30分**（実装 + テスト）

### 前提知識
- [03. 共通実装パターン](../03-common-patterns.md#1-repository-パターン)

---

## Repositoryとは

データアクセスを抽象化するインターフェース。

```
Core層: Repositoryインターフェース定義
  ↓ (実装)
Adapter層: DataSource実装（SQL実行）
```

**原則**: **Adapter層はSQLのみ、ビジネスロジック禁止**

---

## Query/Command分離

```java
// Query Repository - 読み取り専用
public interface RoleQueryRepository {
    Role get(Tenant tenant, RoleIdentifier roleIdentifier);
    Optional<Role> find(Tenant tenant, RoleIdentifier roleIdentifier);
    boolean exists(Tenant tenant, RoleIdentifier roleIdentifier);
    List<Role> findAll(Tenant tenant);
}

// Command Repository - 書き込み専用
public interface RoleCommandRepository {
    void register(Tenant tenant, Role role);
    void update(Tenant tenant, Role role);
    void delete(Tenant tenant, RoleIdentifier roleIdentifier);
}
```

---

## Step 1: Repositoryインターフェース定義（Core層）

**ファイル**: `libs/idp-server-core/src/main/java/org/idp/server/core/openid/identity/role/RoleQueryRepository.java`

```java
package org.idp.server.core.openid.identity.role;

import java.util.List;
import java.util.Optional;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface RoleQueryRepository {

  /**
   * ロール取得（必須存在）
   *
   * @param tenant テナント（必ず第一引数）
   * @param roleIdentifier ロールID
   * @return ロール
   * @throws RoleNotFoundException ロールが存在しない場合
   */
  Role get(Tenant tenant, RoleIdentifier roleIdentifier);

  /**
   * ロール検索（任意存在）
   *
   * @param tenant テナント
   * @param roleIdentifier ロールID
   * @return ロール（Optional）
   */
  Optional<Role> find(Tenant tenant, RoleIdentifier roleIdentifier);

  /**
   * ロール存在チェック
   *
   * @param tenant テナント
   * @param roleIdentifier ロールID
   * @return 存在する場合true
   */
  boolean exists(Tenant tenant, RoleIdentifier roleIdentifier);

  /**
   * ロール一覧取得
   *
   * @param tenant テナント
   * @return ロールリスト
   */
  List<Role> findAll(Tenant tenant);
}
```

**ファイル**: `libs/idp-server-core/src/main/java/org/idp/server/core/openid/identity/role/RoleCommandRepository.java`

```java
package org.idp.server.core.openid.identity.role;

import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface RoleCommandRepository {

  /**
   * ロール登録
   *
   * @param tenant テナント（必ず第一引数）
   * @param role ロール
   */
  void register(Tenant tenant, Role role);

  /**
   * ロール更新
   *
   * @param tenant テナント
   * @param role ロール
   */
  void update(Tenant tenant, Role role);

  /**
   * ロール削除
   *
   * @param tenant テナント
   * @param roleIdentifier ロールID
   */
  void delete(Tenant tenant, RoleIdentifier roleIdentifier);
}
```

---

## Step 2: DataSource実装（Adapter層）

**ファイル**: `libs/idp-server-core-adapter/src/main/java/org/idp/server/adapter/datasource/role/RoleDataSource.java`

```java
package org.idp.server.adapter.datasource.role;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.idp.server.core.openid.identity.role.*;
import org.idp.server.platform.datasource.SqlExecutor;
import org.idp.server.platform.datasource.TransactionManager;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Role DataSource implementation
 *
 * ⚠️ 原則: SQL実行のみ、ビジネスロジック禁止
 */
public class RoleDataSource implements RoleQueryRepository, RoleCommandRepository {

  private final SqlExecutor sqlExecutor;

  public RoleDataSource(SqlExecutor sqlExecutor) {
    this.sqlExecutor = sqlExecutor;
  }

  // ========== Query Repository実装 ==========

  @Override
  public Role get(Tenant tenant, RoleIdentifier roleIdentifier) {
    // ⚠️ Tenant第一引数必須
    TransactionManager.setTenantId(tenant.identifier().value());

    String sql = """
        SELECT role_id, role_name, description, permissions
        FROM role
        WHERE tenant_id = ? AND role_id = ?
        """;

    Map<String, Object> row = sqlExecutor.selectOne(
        sql,
        tenant.identifier().value(),
        roleIdentifier.value()
    );

    if (row == null) {
      throw new RoleNotFoundException(roleIdentifier);
    }

    return RoleMapper.map(row);
  }

  @Override
  public Optional<Role> find(Tenant tenant, RoleIdentifier roleIdentifier) {
    TransactionManager.setTenantId(tenant.identifier().value());

    String sql = """
        SELECT role_id, role_name, description, permissions
        FROM role
        WHERE tenant_id = ? AND role_id = ?
        """;

    Map<String, Object> row = sqlExecutor.selectOne(
        sql,
        tenant.identifier().value(),
        roleIdentifier.value()
    );

    return row == null ? Optional.empty() : Optional.of(RoleMapper.map(row));
  }

  @Override
  public boolean exists(Tenant tenant, RoleIdentifier roleIdentifier) {
    TransactionManager.setTenantId(tenant.identifier().value());

    String sql = """
        SELECT COUNT(*) as count
        FROM role
        WHERE tenant_id = ? AND role_id = ?
        """;

    Map<String, Object> row = sqlExecutor.selectOne(
        sql,
        tenant.identifier().value(),
        roleIdentifier.value()
    );

    return ((Number) row.get("count")).intValue() > 0;
  }

  @Override
  public List<Role> findAll(Tenant tenant) {
    TransactionManager.setTenantId(tenant.identifier().value());

    String sql = """
        SELECT role_id, role_name, description, permissions
        FROM role
        WHERE tenant_id = ?
        ORDER BY created_at DESC
        """;

    List<Map<String, Object>> rows = sqlExecutor.selectList(
        sql,
        tenant.identifier().value()
    );

    return rows.stream()
        .map(RoleMapper::map)
        .collect(Collectors.toList());
  }

  // ========== Command Repository実装 ==========

  @Override
  public void register(Tenant tenant, Role role) {
    TransactionManager.setTenantId(tenant.identifier().value());

    String sql = """
        INSERT INTO role (tenant_id, role_id, role_name, description, permissions, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?::jsonb, NOW(), NOW())
        """;

    sqlExecutor.execute(
        sql,
        tenant.identifier().value(),
        role.identifier().value(),
        role.name().value(),
        role.description(),
        role.permissions().toJson()  // JSONB列
    );
  }

  @Override
  public void update(Tenant tenant, Role role) {
    TransactionManager.setTenantId(tenant.identifier().value());

    String sql = """
        UPDATE role
        SET role_name = ?, description = ?, permissions = ?::jsonb, updated_at = NOW()
        WHERE tenant_id = ? AND role_id = ?
        """;

    sqlExecutor.execute(
        sql,
        role.name().value(),
        role.description(),
        role.permissions().toJson(),
        tenant.identifier().value(),
        role.identifier().value()
    );
  }

  @Override
  public void delete(Tenant tenant, RoleIdentifier roleIdentifier) {
    TransactionManager.setTenantId(tenant.identifier().value());

    String sql = """
        DELETE FROM role
        WHERE tenant_id = ? AND role_id = ?
        """;

    sqlExecutor.execute(
        sql,
        tenant.identifier().value(),
        roleIdentifier.value()
    );
  }
}
```

---

## Step 3: Mapper作成

**ファイル**: `libs/idp-server-core-adapter/src/main/java/org/idp/server/adapter/datasource/role/RoleMapper.java`

```java
package org.idp.server.adapter.datasource.role;

import java.util.Map;
import java.util.Set;
import org.idp.server.core.openid.identity.role.*;
import org.idp.server.platform.converter.JsonConverter;

public class RoleMapper {

  private static final JsonConverter converter = JsonConverter.snakeCaseInstance();

  /**
   * データベース行 → Roleドメインモデル変換
   */
  public static Role map(Map<String, Object> row) {
    RoleIdentifier roleIdentifier = new RoleIdentifier((String) row.get("role_id"));
    RoleName roleName = new RoleName((String) row.get("role_name"));
    String description = (String) row.get("description");

    // JSONB → Set<String>変換
    String permissionsJson = (String) row.get("permissions");
    Set<String> permissionsSet = converter.read(permissionsJson, Set.class);
    Permissions permissions = new Permissions(permissionsSet);

    return new Role(roleIdentifier, roleName, description, permissions);
  }
}
```

---

## チェックリスト

Repository実装前に以下を確認：

### Repositoryインターフェース（Core層）
- [ ] **Tenant第一引数**（全メソッド）
- [ ] Query/Command分離
- [ ] 命名規則: `get()`必須存在, `find()`任意存在, `exists()`判定

### DataSource実装（Adapter層）
- [ ] `TransactionManager.setTenantId()`実行（RLS対応）
- [ ] **SQLのみ、ビジネスロジック禁止**
- [ ] PreparedStatement使用（SQLインジェクション対策）
- [ ] Mapper使用（データベース行 → ドメインモデル変換）

### Mapper実装
- [ ] `JsonConverter.snakeCaseInstance()`使用（JSONB列）
- [ ] 値オブジェクト生成（`RoleIdentifier`, `RoleName`等）

---

## よくあるエラー

### エラー1: Adapter層でビジネスロジック

```java
// ❌ 間違い: Adapter層でビジネス判定
public Role get(Tenant tenant, RoleIdentifier roleIdentifier) {
    String sql = "SELECT * FROM role WHERE tenant_id = ? AND role_id = ?";
    Map<String, Object> row = sqlExecutor.selectOne(sql, tenant.value(), roleIdentifier.value());

    // ❌ ビジネスロジック禁止
    if ("ORGANIZER".equals(tenant.type())) {
        // ...
    }

    return RoleMapper.map(row);
}

// ✅ 正しい: SQLのみ
public Role get(Tenant tenant, RoleIdentifier roleIdentifier) {
    TransactionManager.setTenantId(tenant.identifier().value());
    String sql = "SELECT * FROM role WHERE tenant_id = ? AND role_id = ?";
    Map<String, Object> row = sqlExecutor.selectOne(sql, tenant.value(), roleIdentifier.value());
    return RoleMapper.map(row);
}
```

### エラー2: TransactionManager未使用

```java
// ❌ 間違い: RLS設定なし
public void register(Tenant tenant, Role role) {
    String sql = "INSERT INTO role (...) VALUES (...)";
    sqlExecutor.execute(sql, ...);  // RLSエラー
}

// ✅ 正しい: TransactionManager使用
public void register(Tenant tenant, Role role) {
    TransactionManager.setTenantId(tenant.identifier().value());  // ✅ RLS設定
    String sql = "INSERT INTO role (...) VALUES (...)";
    sqlExecutor.execute(sql, ...);
}
```

---

## 次のステップ

✅ Repository実装をマスターした！

### 📖 次に読むべきドキュメント

1. [Plugin実装ガイド](./impl-12-plugin-implementation.md) - 拡張機能の実装
2. [外部サービス連携ガイド](./impl-17-external-integration.md) - HttpRequestExecutor使用

### 🔗 詳細情報

- [AI開発者向け: Adapters詳細](../content_10_ai_developer/ai-20-adapters.md#datasource---sqlexecutor-パターン)

---

**情報源**: [ClientConfigurationDataSource.java](../../../libs/idp-server-core-adapter/src/main/java/org/idp/server/adapter/datasource/oidc/client/ClientConfigurationDataSource.java)
**最終更新**: 2025-10-12
