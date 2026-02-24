# Control Plane - 管理対象リソース一覧

## このドキュメントの目的

**37個の管理API**の全体像を把握し、どのAPIをいつ使うかを理解することが目標です。

### 所要時間
⏱️ **約15分**（参照用）

---

## 管理APIの分類

### 1. システムレベルAPI（System Manager）

**システム全体**のリソースを管理。システム運用者が使用。

**URL**: `/v1/management/tenants/{tenant-id}/...`

| カテゴリ | API | 管理対象 |
|---------|-----|---------|
| **Onboarding** | オンボーディング | 新規テナント自動セットアップ |
| **基本設定** | TenantManagement | テナント |
| | ClientManagement | OAuthクライアント |
| | UserManagement | ユーザー |
| | RoleManagement | ロール |
| | PermissionManagement | 権限 |
| **認証設定** | AuthorizationServerManagement | 認可サーバー設定 |
| | AuthenticationConfigurationManagement | 認証設定 |
| | AuthenticationPolicyConfigurationManagement | 認証ポリシー |
| | AuthenticationInteractionManagement | 認証インタラクション |
| | AuthenticationTransactionManagement | 認証トランザクション |
| | FederationConfigurationManagement | フェデレーション設定 |
| | IdentityVerificationConfigManagement | 身元確認設定 |
| **セキュリティ** | SecurityEventManagement | セキュリティイベント |
| | SecurityEventHookManagement | セキュリティイベントフック |
| | SecurityEventHookConfigurationManagement | フック設定 |
| | AuditLogManagement | 監査ログ |
| **招待** | TenantInvitationManagement | テナント招待 |

---

### 2. 組織レベルAPI（Organization Manager）

**組織単位**でリソースを管理。組織管理者が使用。

**URL**: `/v1/management/organizations/{org-id}/tenants/{tenant-id}/...`

| カテゴリ | API | 管理対象 |
|---------|-----|---------|
| **基本設定** | OrgTenantManagement | 組織配下のテナント |
| | OrgClientManagement | 組織配下のクライアント |
| | OrgUserManagement | 組織配下のユーザー |
| | OrgRoleManagement | 組織配下のロール |
| | OrgPermissionManagement | 組織配下の権限 |
| **認証設定** | OrgAuthorizationServerManagement | 認可サーバー設定 |
| | OrgAuthenticationConfigManagement | 認証設定 |
| | OrgAuthenticationPolicyConfigManagement | 認証ポリシー |
| | OrgAuthenticationInteractionManagement | 認証インタラクション |
| | OrgAuthenticationTransactionManagement | 認証トランザクション |
| | OrgFederationConfigManagement | フェデレーション設定 |
| | OrgIdentityVerificationConfigManagement | 身元確認設定 |
| **認可管理** | OrgGrantManagement | Grant（認可付与） |
| **セキュリティ** | OrgSecurityEventManagement | セキュリティイベント |
| | OrgSecurityEventHookManagement | セキュリティイベントフック |
| | OrgSecurityEventHookConfigManagement | フック設定 |
| | OrgAuditLogManagement | 監査ログ |

---

### 3. Admin API

**システム全体**の管理。システム管理者が使用。

**URL**: `/v1/admin/...`

| API | 管理対象 | 用途             |
|-----|---------|----------------|
| IdpServerStarter | システム初期化 | 初回セットアップ       |
| IdpServerOperation | システム運用 | データクリーンアップ     |


---

## 使い分けガイド

### いつシステムレベルAPIを使うか？

- ✅ テナント単位でリソースを管理
- ✅ システム管理者が実行
- ✅ 全テナントへのアクセス権限あり

**例**: SaaS運営者が顧客（テナント）のクライアント設定を管理

---

### いつ組織レベルAPIを使うか？

- ✅ 組織単位でリソースを管理
- ✅ 組織管理者が実行
- ✅ 組織配下のテナントのみアクセス可能

**例**: 企業グループの管理者が子会社（テナント）のユーザーを管理

---

### いつAdmin APIを使うか？

- ✅ システム全体の初期化・運用

**例**: idp-serverの初回セットアップ、データクリーンアップ

---

## CRUD操作の標準パターン

すべての管理APIは以下の標準操作をサポート：

| 操作 | HTTPメソッド | エンドポイント | 必要権限 |
|------|------------|-------------|---------|
| **Create** | POST | `/.../{resource}` | `{resource}:write` |
| **Read (List)** | GET | `/.../{resource}` | `{resource}:read` |
| **Read (Get)** | GET | `/.../{resource}/{id}` | `{resource}:read` |
| **Update** | PUT | `/.../{resource}/{id}` | `{resource}:update` |
| **Delete** | DELETE | `/.../{resource}/{id}` | `{resource}:delete` |

**例（Client Management）**:
```
POST   /v1/management/tenants/{tenant-id}/clients         # client:write
GET    /v1/management/tenants/{tenant-id}/clients         # client:read
GET    /v1/management/tenants/{tenant-id}/clients/{id}    # client:read
PUT    /v1/management/tenants/{tenant-id}/clients/{id}    # client:update
DELETE /v1/management/tenants/{tenant-id}/clients/{id}    # client:delete
```

---

## 実装ガイドへのリンク

### 汎用パターン
- [システムレベルAPI実装](./03-system-level-api.md) - CRUD実装パターン
- [組織レベルAPI実装](./04-organization-level-api.md) - 4ステップアクセス制御

### 個別機能
- [Authentication Configuration](../04-implementation-guides/impl-05-authentication-policy.md) - 認証設定
- [Security Event Hooks](../04-implementation-guides/impl-15-security-event-hooks.md) - イベントフック
- [Grant Management API](../../../../openapi/swagger-cp-grant-management-ja.yaml) - Grant管理（OpenAPI仕様）

---

## 次のステップ

✅ 管理APIの全体像を理解した！

### 📖 実装する場合

- [02. 最初の管理API実装](./03-system-level-api.md) - 実践チュートリアル
- [03. システムレベルAPI](./03-system-level-api.md) - CRUD実装

---

**情報源**: `libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/control_plane/`
**最終更新**: 2025-10-12
