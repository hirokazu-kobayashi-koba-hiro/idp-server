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

| API | 優先度 | ステータス | 必要権限 | APIパス | 説明 | Issue | PR |
|-----|-------|----------|---------|---------|------|-------|-----|
| **Organization Tenant Management** | 🔴 High | ✅ 実装済み | `TENANT_*` | `/v1/management/organizations/{organizationId}/tenants` | 組織内テナント管理 | #409 | #434 |
| **Organization Client Management** | 🔴 High | ✅ 実装済み | `CLIENT_*` | `/v1/management/organizations/{organizationId}/tenants/{tenantId}/clients` | 組織内クライアント管理 | #409 | #434 |
| **Organization User Management** | 🔴 High | ❌ 未実装 | `USER_*` | `/v1/management/organizations/{organizationId}/tenants/{tenantId}/users` | 組織内ユーザー管理・招待・停止 | - | - |
| **System Tenant Management** | 🟡 Medium | 🚧 部分実装 | `TENANT_*` | `/v1/management/tenants/{tenant-id}` | システムレベルテナント管理 | - | - |

### 📊 **Phase 2: Security & Monitoring APIs**

| API | 優先度 | ステータス | 必要権限 | APIパス | 説明 | Issue | PR |
|-----|-------|----------|---------|---------|------|-------|-----|
| **Organization Security Event Management** | 🔴 High | 🚧 部分実装 | `SECURITY_EVENT_READ` | `/v1/management/organizations/{organizationId}/tenants/{tenantId}/security-events` | 組織内セキュリティイベント閲覧 | #442 | #443 |
| **Organization Audit Log Management** | 🔴 High | ❌ 未実装 | `AUDIT_LOG_READ` | `/v1/management/organizations/{organizationId}/tenants/{tenantId}/audit-logs` | 組織内監査ログ管理 | - | - |
| **System Security Event Management** | 🟡 Medium | 🚧 部分実装 | `SECURITY_EVENT_READ` | `/v1/management/tenants/{tenant-id}/security-events` | システムレベルセキュリティイベント | - | - |
| **System Audit Log Management** | 🟡 Medium | ❌ 未実装 | `AUDIT_LOG_READ` | `/v1/management/tenants/{tenant-id}/audit-logs` | システムレベル監査ログ | - | - |

### 📊 **Phase 3: Configuration APIs**

| API | 優先度 | ステータス | 必要権限 | APIパス | 説明 | Issue | PR |
|-----|-------|----------|---------|---------|------|-------|-----|
| **Organization Authentication Config** | 🟡 Medium | ❌ 未実装 | `AUTHENTICATION_CONFIG_*` | `/v1/management/organizations/{organizationId}/tenants/{tenantId}/authentication-configs` | 組織内認証設定管理 | - | - |
| **Organization Authentication Policy Config** | 🟡 Medium | ❌ 未実装 | `AUTHENTICATION_POLICY_CONFIG_*` | `/v1/management/organizations/{organizationId}/tenants/{tenantId}/authentication-policy-configs` | 組織内認証ポリシー設定 | - | - |
| **Organization Identity Verification Config** | 🟡 Medium | ❌ 未実装 | `IDENTITY_VERIFICATION_CONFIG_*` | `/v1/management/organizations/{organizationId}/tenants/{tenantId}/identity-verification-configs` | 組織内身元確認設定 | - | - |
| **Organization Federation Config** | 🟡 Medium | ❌ 未実装 | `FEDERATION_CONFIG_*` | `/v1/management/organizations/{organizationId}/tenants/{tenantId}/federation-configs` | 組織内フェデレーション設定 | - | - |
| **Organization Security Event Hook Config** | 🟡 Medium | ❌ 未実装 | `SECURITY_EVENT_HOOK_CONFIG_*` | `/v1/management/organizations/{organizationId}/tenants/{tenantId}/security-event-hook-configs` | 組織内セキュリティイベントフック設定 | - | - |

### 📊 **Phase 4: Advanced Management APIs**

| API | 優先度 | ステータス | 必要権限 | APIパス | 説明 | Issue | PR |
|-----|-------|----------|---------|---------|------|-------|-----|
| **Organization Permission Management** | 🟢 Low | 🚧 部分実装 | `PERMISSION_*` | `/v1/management/organizations/{organizationId}/tenants/{tenantId}/permissions` | 組織内権限管理 | - | - |
| **Organization Role Management** | 🟢 Low | 🚧 部分実装 | `ROLE_*` | `/v1/management/organizations/{organizationId}/tenants/{tenantId}/roles` | 組織内ロール管理 | - | - |
| **System Organization Management** | 🟢 Low | 🚧 部分実装 | `ORGANIZATION_*` | `/v1/management/organizations` | システムレベル組織管理 | - | - |
| **Organization Tenant Invitation Management** | 🟢 Low | ❌ 未実装 | `TENANT_INVITATION_*` | `/v1/management/organizations/{organizationId}/tenant-invitations` | 組織内テナント招待管理 | - | - |

## ステータス定義

| アイコン | ステータス | 説明 |
|---------|----------|------|
| ✅ | 実装済み | 完全に実装され、テスト済み |
| 🚧 | 部分実装 | 基本機能は存在するが、統一フレームワーク未適用 |
| ❌ | 未実装 | 実装されていない |

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