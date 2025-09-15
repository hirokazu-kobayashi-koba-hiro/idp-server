---
title: "Management API Development Roadmap"
author: "Claude Code"
created: "2025-01-15"
updated: "2025-01-15"
status: "draft"
related_issues: ["#409", "#442"]
reviewers: []
---

# Management API Development Roadmap

## 概要

idp-serverの組織レベルの管理系API開発ロードマップ。統一されたDefaultAdminPermissionベースのフレームワークを使用して段階的に実装する。

## 開発状況一覧

### 📊 **Phase 1: Core Infrastructure APIs**

| API | 優先度 | ステータス | 必要権限 | APIパス | 説明 | API仕様 | JsonSchema | データ整合性 | E2Eテスト | Issue | PR |
|-----|-------|----------|---------|---------|------|--------|-----------|------------|-----------|-------|-----|
| **Organization Tenant Management** | 🔴 High | ✅ 実装済み | `TENANT_*` | `/v1/management/organizations/{organizationId}/tenants` | 組織内テナント管理 | 🚧 | 🚧 | 🚧 | ✅ | #409 | #434 |
| **Organization Client Management** | 🔴 High | ✅ 実装済み | `CLIENT_*` | `/v1/management/organizations/{organizationId}/tenants/{tenantId}/clients` | 組織内クライアント管理 | 🚧 | 🚧 | 🚧 | 🚧 | #409 | #434 |
| **Organization User Management** | 🔴 High | ✅ 実装済み | `USER_*` | `/v1/management/organizations/{organizationId}/tenants/{tenantId}/users` | 組織内ユーザー管理・招待・停止 | 🚧 | 🚧 | 🚧 | 🚧 | - | #446 |
| **System Tenant Management** | 🟡 Medium | 🚧 部分実装 | `TENANT_*` | `/v1/management/tenants/{tenant-id}` | システムレベルテナント管理 | ✅ | 🚧 | 🚧 | 🚧 | - | - |

### 📊 **Phase 2: Security & Monitoring APIs**

| API | 優先度 | ステータス | 必要権限 | APIパス | 説明 | API仕様 | JsonSchema | データ整合性 | E2Eテスト | Issue | PR |
|-----|-------|----------|---------|---------|------|--------|-----------|------------|-----------|-------|-----|
| **Organization Security Event Management** | 🔴 High | ✅ 実装済み | `SECURITY_EVENT_READ` | `/v1/management/organizations/{organizationId}/tenants/{tenantId}/security-events` | 組織内セキュリティイベント閲覧 | 🚧 | 🚧 | 🚧 | 🚧 | #442 | - |
| **Organization Audit Log Management** | 🔴 High | ✅ 実装済み | `AUDIT_LOG_READ` | `/v1/management/organizations/{organizationId}/tenants/{tenantId}/audit-logs` | 組織内監査ログ管理 | 🚧 | 🚧 | 🚧 | 🚧 | - | - |
| **System Security Event Management** | 🟡 Medium | 🚧 部分実装 | `SECURITY_EVENT_READ` | `/v1/management/tenants/{tenant-id}/security-events` | システムレベルセキュリティイベント | ✅ | 🚧 | 🚧 | 🚧 | - | - |
| **System Audit Log Management** | 🟡 Medium | ✅ 実装済み | `AUDIT_LOG_READ` | `/v1/management/tenants/{tenant-id}/audit-logs` | システムレベル監査ログ | 🚧 | 🚧 | 🚧 | 🚧 | - | - |

### 📊 **Phase 3: Configuration APIs**

| API | 優先度 | ステータス | 必要権限 | APIパス | 説明 | API仕様 | JsonSchema | データ整合性 | E2Eテスト | Issue | PR |
|-----|-------|----------|---------|---------|------|--------|-----------|------------|-----------|-------|-----|
| **Organization Authentication Config** | 🟡 Medium | ✅ 実装済み | `AUTHENTICATION_CONFIG_*` | `/v1/management/organizations/{organizationId}/tenants/{tenantId}/authentication-configs` | 組織内認証設定管理 | 🚧 | 🚧 | 🚧 | 🚧 | - | - |
| **Organization Authentication Policy Config** | 🟡 Medium | ✅ 実装済み | `AUTHENTICATION_POLICY_CONFIG_*` | `/v1/management/organizations/{organizationId}/tenants/{tenantId}/authentication-policy-configs` | 組織内認証ポリシー設定 | 🚧 | 🚧 | 🚧 | 🚧 | - | - |
| **Organization Identity Verification Config** | 🟡 Medium | ✅ 実装済み | `IDENTITY_VERIFICATION_CONFIG_*` | `/v1/management/organizations/{organizationId}/tenants/{tenantId}/identity-verification-configs` | 組織内身元確認設定 | 🚧 | 🚧 | 🚧 | 🚧 | - | - |
| **Organization Federation Config** | 🟡 Medium | ❌ 未実装 | `FEDERATION_CONFIG_*` | `/v1/management/organizations/{organizationId}/tenants/{tenantId}/federation-configs` | 組織内フェデレーション設定 | ❌ | ❌ | ❌ | ❌ | - | - |
| **Organization Security Event Hook Config** | 🟡 Medium | ❌ 未実装 | `SECURITY_EVENT_HOOK_CONFIG_*` | `/v1/management/organizations/{organizationId}/tenants/{tenantId}/security-event-hook-configs` | 組織内セキュリティイベントフック設定 | ❌ | ❌ | ❌ | ❌ | - | - |
| **Organization Authentication Interaction Management** | 🟡 Medium | ❌ 未実装 | `AUTHENTICATION_INTERACTION_*` | `/v1/management/organizations/{organizationId}/tenants/{tenantId}/authentication-interactions` | 組織内認証インタラクション管理 | ❌ | ❌ | ❌ | ❌ | - | - |
| **Organization Authentication Transaction Management** | 🟡 Medium | ❌ 未実装 | `AUTHENTICATION_TRANSACTION_*` | `/v1/management/organizations/{organizationId}/tenants/{tenantId}/authentication-transactions` | 組織内認証トランザクション管理 | ❌ | ❌ | ❌ | ❌ | - | - |
| **Organization Authorization Server Management** | 🟡 Medium | ❌ 未実装 | `AUTHORIZATION_SERVER_*` | `/v1/management/organizations/{organizationId}/tenants/{tenantId}/authorization-server` | 組織内認可サーバー管理 | ❌ | ❌ | ❌ | ❌ | - | - |

### 📊 **Phase 4: Advanced Management APIs**

| API | 優先度 | ステータス | 必要権限 | APIパス | 説明 | API仕様 | JsonSchema | データ整合性 | E2Eテスト | Issue | PR |
|-----|-------|----------|---------|---------|------|--------|-----------|------------|-----------|-------|-----|
| **Organization Permission Management** | 🟢 Low | ❌ 未実装 | `PERMISSION_*` | `/v1/management/organizations/{organizationId}/tenants/{tenantId}/permissions` | 組織内権限管理 | ❌ | ❌ | ❌ | ❌ | - | - |
| **Organization Role Management** | 🟢 Low | ❌ 未実装 | `ROLE_*` | `/v1/management/organizations/{organizationId}/tenants/{tenantId}/roles` | 組織内ロール管理 | ❌ | ❌ | ❌ | ❌ | - | - |
| **Organization Tenant Invitation Management** | 🟢 Low | ❌ 未実装 | `TENANT_INVITATION_*` | `/v1/management/organizations/{organizationId}/tenants/{tenantId}/tenant-invitations` | 組織内テナント招待管理 | ❌ | ❌ | ❌ | ❌ | - | - |
| **Organization Onboarding Management** | 🟢 Low | ❌ 未実装 | `ONBOARDING_*` | `/v1/management/organizations/{organizationId}/tenants/{tenantId}/onboarding` | 組織内オンボーディング管理 | ❌ | ❌ | ❌ | ❌ | - | - |
| **System Organization Management** | 🟢 Low | 🚧 部分実装 | `ORGANIZATION_*` | `/v1/management/organizations` | システムレベル組織管理 | 🚧 | 🚧 | 🚧 | 🚧 | - | - |

## 📋 **システムベースAPI実装ギャップ分析**

### **システムレベルAPI実装状況 (参考実装ベース)**

以下のシステムレベルAPIが既に実装済みで、組織レベルAPI作成の参考実装として使用可能：

| システムAPI | 実装状況 | 組織版実装状況 | 参考ファイル |
|------------|----------|---------------|-------------|
| ✅ AuthenticationConfigurationManagement | 実装済み | ✅ 実装済み | `management/AuthenticationConfigurationManagementV1Api.java` |
| ✅ AuthenticationPolicyConfigurationManagement | 実装済み | ✅ 実装済み | `management/AuthenticationPolicyConfigurationManagementV1Api.java` |
| ✅ IdentityVerificationConfigurationManagement | 実装済み | ✅ 実装済み | `management/IdentityVerificationConfigurationManagementV1Api.java` |
| ✅ AuditLogManagement | 実装済み | ✅ 実装済み | `management/AuditLogManagementV1Api.java` |
| ✅ SecurityEventManagement | 実装済み | ✅ 実装済み | `management/SecurityEventManagementV1Api.java` |
| ✅ ClientManagement | 実装済み | ✅ 実装済み | `management/ClientManagementV1Api.java` |
| ✅ TenantManagement | 実装済み | ✅ 実装済み | `management/TenantManagementV1Api.java` |
| ✅ UserManagement | 実装済み | ✅ 実装済み | `management/UserManagementV1Api.java` |

### **🎯 組織レベル未実装API (システムベース参考実装あり)**

以下のAPIはシステムレベルで実装済みだが、組織レベルはまだ未実装：

| 未実装組織API | 優先度 | 参考実装ファイル | 推定実装工数 |
|-------------|-------|----------------|-------------|
| **Organization Federation Config** | 🟡 Medium | `management/FederationConfigurationManagementV1Api.java` | 1-2日 |
| **Organization Security Event Hook Config** | 🟡 Medium | `management/SecurityEventHookConfigurationManagementV1Api.java` | 1-2日 |
| **Organization Authentication Interaction** | 🟡 Medium | `management/AuthenticationInteractionManagementV1Api.java` | 1-2日 |
| **Organization Authentication Transaction** | 🟡 Medium | `management/AuthenticationTransactionManagementV1Api.java` | 1-2日 |
| **Organization Authorization Server** | 🟡 Medium | `management/AuthorizationServerManagementV1Api.java` | 1-2日 |
| **Organization Permission Management** | 🟢 Low | `management/PermissionManagementV1Api.java` | 1-2日 |
| **Organization Role Management** | 🟢 Low | `management/RoleManagementV1Api.java` | 1-2日 |
| **Organization Tenant Invitation** | 🟢 Low | `management/TenantInvitationManagementV1Api.java` | 1-2日 |
| **Organization Onboarding** | 🟢 Low | `management/OnboardingV1Api.java` | 1-2日 |

### **実装パターン確立済み**

組織レベルAPI実装は以下の確立されたパターンに従う：

1. **参考実装**: 対応するシステムレベルAPIファイル
2. **アクセス制御**: `OrganizationAccessVerifier` による組織レベル権限検証
3. **実装場所**:
   - API: `organization/Organization{Name}ManagementV1Api.java`
   - EntryService: `organization_manager/Org{Name}ManagementEntryService.java`
   - Interface: `control_plane/management/{domain}/Org{Name}ManagementApi.java`

## ステータス定義

| アイコン | ステータス | 説明 |
|---------|----------|------|
| ✅ | 実装済み | 完全に実装され、テスト済み |
| 🚧 | 部分実装 | 基本機能は存在するが、統一フレームワーク未適用 |
| ❌ | 未実装 | 実装されていない |

## 📊 **API完成度評価マトリックス**

各APIの品質を4つの観点で評価：

### **評価項目定義**

| 項目 | ✅ 完了 | 🚧 部分完了 | ❌ 未実装 | 説明 |
|------|--------|-----------|----------|------|
| **API仕様** | OpenAPI完全準拠 | 基本仕様のみ | 仕様なし | Swagger/OpenAPI documentation |
| **JsonSchema** | 入出力バリデーション完備 | 基本バリデーションのみ | バリデーションなし | リクエスト/レスポンス構造検証 |
| **データ整合性** | トランザクション・制約完備 | 基本整合性のみ | 整合性チェックなし | DB制約・参照整合性・楽観ロック |
| **E2Eテスト** | 全フロー自動テスト | 基本フローのみ | テストなし | CRUD・エラーケース・権限検証 |

### **品質レベル判定**

- **🟢 プロダクション品質**: 全項目 ✅
- **🟡 開発品質**: 3項目以上 ✅ または 🚧
- **🔴 実装中品質**: 2項目以下 ✅

### **重要な品質指標**

**現状**: すべての組織レベルAPIで4つの品質項目がまだ🚧（部分完了）状態

**共通課題**:
- **API仕様**: OpenAPI Specificationドキュメントが不完備
- **JsonSchema**: 入出力バリデーションルールが未定義
- **データ整合性**: DB制約・参照整合性の検証が不十分
- **E2Eテスト**: 基本動作テストのみ（権限・エラーケース未カバー）

**Organization Identity Verification Config の特記事項**:
- type フィールドUUID化で重複制約違反は解決済み
- しかし、レスポンス構造統一化等の課題が残存

## 開発優先度

### 🔴 **High Priority - 即座に必要**
システムの基本機能として必須のAPI群。ユーザー・クライアント・セキュリティ管理。

### 🟡 **Medium Priority - 運用で必要**
本格的な運用時に必要となるAPI群。設定管理・監視機能。

### 🟢 **Low Priority - 将来拡張**
エンタープライズ機能や高度な管理機能。段階的実装。

## 実装方針

### **統一フレームワーク適用**

全てのAPIは以下の統一パターンで実装：

1. **権限管理**: DefaultAdminPermissionのみ使用
2. **アクセス制御**: 2層制御（テナントアクセス + 権限検証）
3. **API構造**: 標準CRUD + dry-run対応
4. **テスト戦略**: Success/Error/Validation パターン

### **🚨 組織レベルAPI実装の重要注意事項**

#### **実装パターンの絶対ルール**

**❌ 絶対に避けるべき実装**:
```java
// TODOコメントで実装をごまかす
// TODO: Add validation logic for authentication configuration request
Map<String, Object> response = new HashMap<>();
response.put("message", "Authentication configuration created successfully");
```

**✅ 正しい実装パターン**:
```java
// 既存のContext Creatorを必ず使用
AuthenticationConfigRegistrationContextCreator contextCreator =
    new AuthenticationConfigRegistrationContextCreator(targetTenant, request, dryRun);
AuthenticationConfigRegistrationContext context = contextCreator.create();

// 適切なAudit Log作成
AuditLog auditLog = AuditLogCreator.create(
    "OrgAuthenticationConfigManagementApi.create", tenant, operator, oAuthToken, context, requestAttributes);

// 実際のRepository操作
authenticationConfigurationCommandRepository.register(tenant, context.configuration());
return context.toResponse();
```

#### **実装前必須チェック**

1. **参考実装の必須確認**:
   - `/libs/idp-server-use-cases/.../system_manager/AuthenticationConfigurationManagementEntryService.java`
   - `/libs/idp-server-use-cases/.../organization_manager/OrgUserManagementEntryService.java`

2. **組織アクセス制御パターン**:
```java
OrganizationAccessControlResult accessResult =
    organizationAccessVerifier.verifyAccess(organization, adminTenant, operator, permissions);
if (!accessResult.isSuccess()) {
    return new AuthenticationConfigManagementResponse(
        AuthenticationConfigManagementStatus.FORBIDDEN, createErrorResponse(accessResult.getReason()));
}
// この後は既存のシステムレベル実装と同じフロー
```

3. **Context Creator使用の必須性**:
   - 組織レベルAPIでも既存のContext Creatorを流用
   - 新規作成ではなく、既存パターンの踏襲が基本

#### **品質チェックリスト**

実装完了前に以下を必ず確認：
- [ ] Context Creatorパターンを使用している
- [ ] 適切なAudit Log（create/update/delete別）を実装
- [ ] TODOコメントで済ませている箇所がない
- [ ] 実際のRepository操作を実装している
- [ ] 既存の組織レベルAPIと一貫性がある

#### **🆔 重要: Authentication Configuration ID仕様**

**必須要件**: Authentication ConfigurationのIDフィールドは必ずUUIDでなければならない

- **❌ 間違い**: `"test-auth-config-123"`, `"simple-string"`
- **✅ 正しい**: `uuidv4()` → `"f47ac10b-58cc-4372-a567-0e02b2c3d479"`

```javascript
// E2Eテスト・フロントエンドでの正しい実装例
import { v4 as uuidv4 } from "uuid";

const authConfigRequest = {
  "id": uuidv4(),  // UUID必須
  "type": "password",
  "attributes": { ... },
  "metadata": { ... },
  "interactions": {}
};
```

この要件を守らないとAuthenticationConfigurationIdentifierのバリデーションでエラーになる。

#### **🏷️ 組織レベルAPIパラメータ命名規約**

**重要な修正**: 組織レベルAPIのメソッドシグネチャで適切な命名を使用

```java
// ❌ 避けるべき命名
method(OrganizationIdentifier orgId, TenantIdentifier adminTenant, ...)

// ✅ 推奨命名
method(OrganizationIdentifier organizationIdentifier, TenantIdentifier tenantIdentifier, ...)
```

**命名の意味**:
- `organizationIdentifier`: 操作対象の組織
- `tenantIdentifier`: 組織内の操作対象テナント（管理者テナントではない）
- `operator`: 操作実行者（組織管理権限を持つユーザー）

**なぜ`adminTenant`は不適切か**:
- テナント自体がadminという意味ではない
- 「組織内の対象テナント」を表すパラメータ
- 他APIとの一貫性を欠く誤解を招く命名

### **段階的実装アプローチ**

```
Phase 1 → Phase 2 → Phase 3 → Phase 4
  ↓         ↓         ↓         ↓
コア機能   監視機能   設定管理   高度機能
```

## 次の実装ターゲット

### **Phase 1 完了目標**

1. **Client Management API統一**: 既存実装のパス構造を階層的に修正
   - 現在: `/v1/management/organizations/{organizationId}/clients`
   - 目標: `/v1/management/organizations/{organizationId}/tenants/{tenantId}/clients`
2. **User Management API実装**: 招待・停止機能の統合
3. **API パス構造の統一**: 組織→テナントの階層を明示

### **Phase 2 開始**

1. **Audit Log Management API**: 監査ログ閲覧機能
2. **Security Event Management API**: Issue #442対応完了

## 関連ドキュメント

- [Management API Design Framework](./management-api-framework.md) - 設計フレームワーク
- [Organization-level Tenant Management](../analysis/organization-tenant-management-analysis.md) - 実装分析
- CLAUDE.md - プロジェクト全体コンテキスト

## 進捗追跡

### **更新履歴**

| 日付 | 更新内容 | 担当者 |
|------|---------|-------|
| 2025-01-15 | 初版作成、Phase 1-4定義 | Claude Code |

### **マイルストーン**

- [ ] **Phase 1 完了**: Core Infrastructure APIs統一 (目標: 2025-01-31)
- [ ] **Phase 2 完了**: Security & Monitoring APIs実装 (目標: 2025-02-28)
- [ ] **Phase 3 開始**: Configuration APIs着手 (目標: 2025-03-01)

---

*このロードマップは開発進捗に応じて継続的に更新されます。*