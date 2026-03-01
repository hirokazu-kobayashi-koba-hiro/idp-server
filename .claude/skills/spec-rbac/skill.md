---
name: spec-rbac
description: idp-serverのRBAC（ロール・パーミッション）仕様を把握する際に使用。権限体系、ロール設計、ワイルドカードマッチング、アクセス検証フロー、カスタム権限の実装指針に役立つ。
---

# RBAC（ロールベースアクセス制御）仕様

## 概要

idp-serverは `idp:resource:action` 形式の細粒度権限とワイルドカード委譲を組み合わせたRBACシステムを提供する。AWS IAMスタイルのパーミッションマッチングにより、段階的な権限委譲が可能。

---

## 権限体系

### DefaultAdminPermission（73権限）

`libs/idp-server-control-plane/.../base/definition/DefaultAdminPermission.java`

#### ワイルドカード（1）

| 権限 | 説明 |
|------|------|
| `idp:*` | 全Control Plane権限 |

#### リソース管理（CRUD: 各4権限 x 14リソース = 56）

| リソース | 権限 |
|----------|------|
| `idp:organization:` | create, read, update, delete |
| `idp:tenant-invitation:` | create, read, update, delete |
| `idp:tenant:` | create, read, update, delete |
| `idp:authorization-server:` | create, read, update, delete |
| `idp:client:` | create, read, update, delete |
| `idp:permission:` | create, read, update, delete |
| `idp:role:` | create, read, update, delete |
| `idp:authentication-config:` | create, read, update, delete |
| `idp:authentication-policy-config:` | create, read, update, delete |
| `idp:identity-verification-config:` | create, read, update, delete |
| `idp:federation-config:` | create, read, update, delete |
| `idp:security-event-hook-config:` | create, read, update, delete |
| `idp:admin-user:` | create, read, update, delete |
| `idp:user:` | create, read, update, delete |

#### ユーザー追加操作（4）

| 権限 | 説明 |
|------|------|
| `idp:user:invite` | ユーザー招待 |
| `idp:user:suspend` | ユーザー停止 |
| `idp:admin-user:invite` | 管理者招待 |
| `idp:admin-user:suspend` | 管理者停止 |

#### 参照・特殊操作（12）

| 権限 | 説明 |
|------|------|
| `idp:security-event-hook:read` | セキュリティイベントフック参照 |
| `idp:security-event-hook:retry` | 失敗フックの再実行 |
| `idp:security-event:read` | セキュリティイベント参照 |
| `idp:audit-log:read` | 監査ログ参照 |
| `idp:authentication-transaction:read` | 認証トランザクション参照 |
| `idp:authentication-interaction:read` | 認証インタラクション参照 |
| `idp:session:read` | セッション参照 |
| `idp:session:delete` | セッション削除 |
| `idp:grant:read` | 認可グラント参照 |
| `idp:grant:delete` | 認可グラント削除/失効 |
| `idp:system:read` | システム設定参照 |
| `idp:system:write` | システム設定書き込み |

### 権限分類メソッド

| メソッド | 用途 |
|----------|------|
| `isSystemPermission()` | `idp:system:*` — 管理テナントのみ付与 |
| `isAdminUserPermission()` | `idp:admin-user:*` — 管理者ユーザー操作 |
| `isPublicUserPermission()` | `idp:user:*` — 一般ユーザー操作 |
| `isWildcard()` | ワイルドカード権限か |
| `toTenantPermissions()` | システム権限を除外した権限セット（テナントオンボーディング用） |

---

## ロール体系

### DefaultAdminRole

`libs/idp-server-control-plane/.../base/definition/DefaultAdminRole.java`

| ロール | 権限 | 説明 |
|--------|------|------|
| `ADMINISTRATOR` | `idp:*`（ワイルドカード） | 全Control Plane権限を持つデフォルトロール |

- テナントオンボーディング時に自動作成される
- カスタムロールを作成することで、より細粒度の権限委譲が可能

### MemberRole（定義済み・未活用）

`libs/idp-server-platform/.../organization/MemberRole.java`

| ロール | 説明 |
|--------|------|
| `OWNER` | 組織オーナー |
| `ADMINISTRATOR` | 管理者 |
| `EDITOR` | 編集者 |
| `VIEWER` | 閲覧者 |

組織階層上のロールとして定義されているが、権限体系（DefaultAdminPermission）との紐付けは未実装。

---

## ワイルドカードマッチング

### PermissionMatcher

`libs/idp-server-core/.../identity/permission/PermissionMatcher.java`

#### マッチングルール

| パターン | マッチ対象 | 例 |
|----------|-----------|-----|
| `*` | 全権限 | 全てにマッチ |
| `idp:*` | 全Control Plane権限 | `idp:user:create`, `idp:client:delete` 等 |
| `idp:user:*` | ユーザー管理全般 | `idp:user:create`, `idp:user:suspend` 等 |
| `idp:user:create` | 完全一致のみ | `idp:user:create` のみ |

#### 主要メソッド

| メソッド | 用途 |
|----------|------|
| `matches(userPerm, requiredPerm)` | 完全一致 or ワイルドカードマッチ |
| `matchesAny(userPerms, requiredPerm)` | ユーザーの権限セットのいずれかがマッチするか |
| `matchesAll(userPerms, requiredPerms)` | ユーザーが必要な全権限を持つか |
| `normalize(permission)` | レガシー形式を正規化（後方互換） |

#### 後方互換性

`normalize()` がレガシー形式を自動変換:
- `organization:create` → `idp:organization:create`（名前空間なし → `idp:` 付加）
- `admin_user:create` → `idp:admin-user:create`（アンダースコア → ハイフン）

---

## アクセス検証フロー

### ApiPermissionVerifier（システムレベル）

`libs/idp-server-control-plane/.../base/ApiPermissionVerifier.java`

```
verify(User operator, AdminPermissions required)
  └→ 権限不足 → PermissionDeniedException
```

シンプルな権限チェックのみ。組織・テナントのスコープ制限なし。

### OrganizationAccessVerifier（組織レベル）

`libs/idp-server-control-plane/.../base/OrganizationAccessVerifier.java`

4段階の検証を順次実行:

```
verify(Organization, TenantIdentifier, User, AdminPermissions)
  ├→ 1. 組織メンバーシップ確認（assignedOrganizations）
  ├→ 2. テナントアクセス確認（assignedTenants）
  ├→ 3. 組織-テナント関係確認
  └→ 4. 必要権限の確認（PermissionMatcher使用）
```

- ステップ1-3失敗 → `OrganizationAccessDeniedException`
- ステップ4失敗 → `PermissionDeniedException`

### AdminPermissions ラッパー

`libs/idp-server-control-plane/.../base/definition/AdminPermissions.java`

API操作に必要な権限セットを表現するラッパークラス。

```java
AdminPermissions required = new AdminPermissions(
    Set.of(DefaultAdminPermission.CLIENT_READ)
);
required.includesAll(user.permissionsAsSet()); // 権限判定
```

---

## データモデル

### DBテーブル

| テーブル | テナントスコープ | 用途 |
|----------|:-----------:|------|
| `permission` | Yes (RLS) | 権限定義（tenant_id, name, description） |
| `role` | Yes (RLS) | ロール定義（tenant_id, name, description） |
| `role_permission` | Yes (RLS) | ロール-権限マッピング |
| `idp_user_roles` | Yes (RLS) | ユーザー-ロールマッピング |

### ビュー

| ビュー | 用途 |
|--------|------|
| `role_permission_view` | ロール → 権限の読みやすい結合ビュー |
| `user_effective_permissions_view` | ユーザー → ロール → 権限の実効権限ビュー |

### ユニーク制約

- `uk_tenant_permission`: (tenant_id, name) — テナント内で権限名は一意
- `uk_tenant_role`: (tenant_id, name) — テナント内でロール名は一意

### PostgreSQL RLS

全RBACテーブルで `USING (tenant_id = current_setting('app.tenant_id')::uuid)` によるテナント分離を強制。

---

## Management API

### ロール管理

#### システムレベル（RoleManagementHandler）

`libs/idp-server-control-plane/.../management/role/handler/RoleManagementHandler.java`

テナント内のロールCRUD。`ApiPermissionVerifier` で権限チェック。

#### 組織レベル（OrgRoleManagementHandler）

`libs/idp-server-control-plane/.../management/role/handler/OrgRoleManagementHandler.java`

組織スコープのロールCRUD。`OrganizationAccessVerifier` で4段階検証。

### 操作一覧

| 操作 | 必要権限 |
|------|---------|
| ロール作成 | `idp:role:create` |
| ロール一覧 | `idp:role:read` |
| ロール取得 | `idp:role:read` |
| ロール更新 | `idp:role:update` |
| ロール削除 | `idp:role:delete` |
| 権限作成 | `idp:permission:create` |
| 権限一覧 | `idp:permission:read` |
| 権限更新 | `idp:permission:update` |
| 権限削除 | `idp:permission:delete` |

---

## カスタム権限

### PermissionNamespaceValidator

`libs/idp-server-core/.../identity/permission/PermissionNamespaceValidator.java`

#### 名前空間ルール

- **予約**: `idp:` — システム権限専用、カスタム権限には使用不可
- **カスタム**: `myapp:`, `custom:` 等、任意の名前空間を使用可能

```
idp:user:create          → NG（予約名前空間）
myapp:report:generate    → OK（カスタム名前空間）
billing:invoice:create   → OK（カスタム名前空間）
```

#### メソッド

| メソッド | 用途 |
|----------|------|
| `validate(name)` | 予約名前空間の場合に例外スロー |
| `usesReservedNamespace(name)` | 予約名前空間か判定 |
| `extractNamespace(name)` | 最初の `:` 前の名前空間を抽出 |

---

## ユースケース例

### クライアント設定のみ変更可能な管理者

```
ロール: client-manager
権限:
  - idp:client:create
  - idp:client:read
  - idp:client:update
  - idp:client:delete
```

### 読み取り専用の監査担当者

```
ロール: auditor
権限:
  - idp:audit-log:read
  - idp:security-event:read
  - idp:security-event-hook:read
  - idp:session:read
  - idp:grant:read
```

### ユーザー管理全般の委譲

```
ロール: user-admin
権限:
  - idp:user:*（ワイルドカードで user の全操作）
  - idp:admin-user:*（ワイルドカードで admin-user の全操作）
```

### 認証設定の管理者

```
ロール: auth-config-manager
権限:
  - idp:authentication-config:*
  - idp:authentication-policy-config:*
  - idp:federation-config:*
```

---

## 主要参照ファイル

| ファイル | 役割 |
|----------|------|
| `libs/idp-server-control-plane/.../base/definition/DefaultAdminPermission.java` | 全権限定義（73 enum値） |
| `libs/idp-server-control-plane/.../base/definition/DefaultAdminRole.java` | デフォルトロール定義 |
| `libs/idp-server-control-plane/.../base/definition/AdminPermissions.java` | 権限セットラッパー |
| `libs/idp-server-core/.../identity/permission/PermissionMatcher.java` | ワイルドカードマッチング |
| `libs/idp-server-core/.../identity/permission/PermissionNamespaceValidator.java` | カスタム権限の名前空間検証 |
| `libs/idp-server-control-plane/.../base/OrganizationAccessVerifier.java` | 組織レベル4段階検証 |
| `libs/idp-server-control-plane/.../base/ApiPermissionVerifier.java` | システムレベル権限検証 |
| `libs/idp-server-control-plane/.../management/role/handler/RoleManagementHandler.java` | システムレベルロール管理 |
| `libs/idp-server-control-plane/.../management/role/handler/OrgRoleManagementHandler.java` | 組織レベルロール管理 |
| `libs/idp-server-platform/.../organization/MemberRole.java` | 組織メンバーロール（未活用） |

$ARGUMENTS
