# Issue Milestone Planning

## 提案マイルストーン

### 🛡️ Security & Compliance (v1.1 - 2025-Q2)
**優先度**: 最高
**目的**: セキュリティ脆弱性修正とコンプライアンス対応

#### Critical Security Issues
- #713 [Security] Upgrade Spring Boot to 3.4.5+
- #710 [Security] Fix CORS Origin validation vulnerability
- #712 [Security] Implement SSRF protection in HttpRequestExecutor
- #709 [Security] Update Docker base images to Alpine-based secure images

#### Security Features
- #715 [Security] Implement account lockout mechanism
- #714 [Security] Implement security headers in HTTP responses
- #711 [Security] Implement rate limiting for OAuth endpoints
- #638 ログアウト時にトークンリボーク(Revocation)が実行されていない

#### Compliance
- #661 GDPR Compliance Verification Checklist
- #641 脆弱性診断_20251009

---

### 📚 Documentation (v1.2 - 2025-Q2)
**優先度**: 高
**目的**: ドキュメント整備とユーザー体験向上

#### Database Documentation
- #731 Add comprehensive database schema documentation

#### Developer Guide
- #591 content_06_developer-guide/developer-guide: 欠けている開発者ガイドドキュメント（20件以上）
- #680 開発者向けドキュメント整備（content_06拡充） [CLOSED]
- #676 AI開発者向け知識ベースの作成・改善 [CLOSED]

#### Operations Documentation
- #592 content_08_ops: 運用ドキュメントが極端に不足（18件必要） [CLOSED]
- #691 サーバー初期構築ドキュメントの作成 (how-to-01) [CLOSED]

#### How-to Documentation
- #688 How-toドキュメントの整合性チェックと改善
- #588 how-toドキュメントの大幅な欠落：40以上の重要機能がドキュメント化されていない [CLOSED]

---

### 🔧 Refactoring & Architecture (v1.3 - 2025-Q3)
**優先度**: 中
**目的**: コード品質向上とアーキテクチャ改善

#### Domain Model Refactoring
- #728 Refactor authentication_device_rule from AuthenticationPolicy to Tenant-level configuration
- #635 Refactor: Extract common preferred_username auto-assignment logic
- #719 [Refactor] Replace identityPolicyConfig (TenantAttributes) with TenantIdentityPolicy [CLOSED]
- #717 [Refactor] Remove databaseType from Tenant domain model [CLOSED]
- #702 [リファクタリング] TenantAttributesの設計改善 - カテゴリ別Configuration分離 [CLOSED]

#### Plugin System
- #557 Enhance Plugin System: Adopt Keycloak-inspired unified Factory pattern for all extensions

#### Database
- #623 Refactor database configuration: Eliminate redundant PostgreSQL/MySQL dual configuration [CLOSED]
- #630 Improve PostgreSQL User Initialization for Multi-Environment Support [CLOSED]

---

### ✨ Features (v1.4 - 2025-Q3)
**優先度**: 中
**目的**: 新機能追加とユーザー価値向上

#### Grant Management
- #663 Add Authorization Grant Management APIs (Resource Owner, Organization-level, System-level)
- #660 Add concept-14-grant-management.md - Grant Management concept documentation [CLOSED]

#### Authentication
- #687 テナント作成時にデフォルト認証設定を自動生成

#### Identity Verification
- #550 [改善]身元確認申し込みのAPIに順序性を担保する仕組みを追加する

#### Security Events
- #541 [改善]セキュリティイベント検索 & セキュリティイベントフック APIの検索条件の拡張

#### HTTP Request Executor
- #716 [Enhancement] Support HTTP 200 error response pattern in HttpRequestExecutor
- #544 [仕様検討]外部APIのレスポンスのステータスコードとidp-serverが返却するステータスコードのマッピング方針を決める必要がある

---

### 🐛 Bug Fixes (v1.5 - Continuous)
**優先度**: 高（Critical bugs）/ 中（Other bugs）
**目的**: バグ修正と安定性向上

#### Critical Bugs (CLOSED)
- #672 [CRITICAL] SQL Injection in TransactionManager - RLS Bypass Risk [CLOSED]
- #674 [CRITICAL] Remove Default Database Credentials [CLOSED]
- #673 [CRITICAL] Update Nimbus JOSE + JWT to Fix CVE-2025-53864 & CVE-2024-31214 [CLOSED]

#### Data Issues
- #729 preferred_username uniqueness issue with multiple identity providers

#### Other Bugs (CLOSED)
- #725 Fix broken markdown links in documentation [CLOSED]
- #685 NullPointerException in Role.permissionsAsMap() when deleting a role [CLOSED]
- #683 Role Update API Issue: False duplicate name error on PUT request [CLOSED]
- #548 [バグ]トークン検証処理のバリデーション漏れ [CLOSED]
- #547 [バグ]ユーザーライフサイクルイベントの実行処理でエラーが発生している [CLOSED]
- #542 [バグ]セキュリティイベントフックの検索APIでidを指定する500エラーとなる [CLOSED]

---

### 🚀 Future (v2.0 - 2025-Q4)
**優先度**: 低
**目的**: 長期的な改善と新技術対応

#### Platform Support
- #596 Support to JDK25

#### DevOps
- #722 GitHub PagesへのDocusaurusドキュメントデプロイ自動化 [CLOSED]
- #618 プロジェクト直下のディレクトリー整理 [CLOSED]

---

## マイルストーン作成コマンド

```bash
# v1.1 - Security & Compliance
gh api repos/hirokazu-kobayashi-koba-hiro/idp-server/milestones \
  -X POST \
  -f title='v1.1 - Security & Compliance' \
  -f description='セキュリティ脆弱性修正とコンプライアンス対応' \
  -f due_on='2025-06-30T00:00:00Z' \
  -f state='open'

# v1.2 - Documentation
gh api repos/hirokazu-kobayashi-koba-hiro/idp-server/milestones \
  -X POST \
  -f title='v1.2 - Documentation' \
  -f description='ドキュメント整備とユーザー体験向上' \
  -f due_on='2025-06-30T00:00:00Z' \
  -f state='open'

# v1.3 - Refactoring & Architecture
gh api repos/hirokazu-kobayashi-koba-hiro/idp-server/milestones \
  -X POST \
  -f title='v1.3 - Refactoring & Architecture' \
  -f description='コード品質向上とアーキテクチャ改善' \
  -f due_on='2025-09-30T00:00:00Z' \
  -f state='open'

# v1.4 - Features
gh api repos/hirokazu-kobayashi-koba-hiro/idp-server/milestones \
  -X POST \
  -f title='v1.4 - Features' \
  -f description='新機能追加とユーザー価値向上' \
  -f due_on='2025-09-30T00:00:00Z' \
  -f state='open'

# v1.5 - Bug Fixes
gh api repos/hirokazu-kobayashi-koba-hiro/idp-server/milestones \
  -X POST \
  -f title='v1.5 - Bug Fixes' \
  -f description='バグ修正と安定性向上（継続的）' \
  -f state='open'

# v2.0 - Future
gh api repos/hirokazu-kobayashi-koba-hiro/idp-server/milestones \
  -X POST \
  -f title='v2.0 - Future' \
  -f description='長期的な改善と新技術対応' \
  -f due_on='2025-12-31T00:00:00Z' \
  -f state='open'
```

---

## Issue割り当てコマンド例

### v1.1 - Security & Compliance
```bash
gh issue edit 713 --milestone "v1.1 - Security & Compliance"
gh issue edit 710 --milestone "v1.1 - Security & Compliance"
gh issue edit 712 --milestone "v1.1 - Security & Compliance"
gh issue edit 709 --milestone "v1.1 - Security & Compliance"
gh issue edit 715 --milestone "v1.1 - Security & Compliance"
gh issue edit 714 --milestone "v1.1 - Security & Compliance"
gh issue edit 711 --milestone "v1.1 - Security & Compliance"
gh issue edit 638 --milestone "v1.1 - Security & Compliance"
gh issue edit 661 --milestone "v1.1 - Security & Compliance"
gh issue edit 641 --milestone "v1.1 - Security & Compliance"
```

### v1.2 - Documentation
```bash
gh issue edit 731 --milestone "v1.2 - Documentation"
gh issue edit 591 --milestone "v1.2 - Documentation"
gh issue edit 688 --milestone "v1.2 - Documentation"
```

### v1.3 - Refactoring & Architecture
```bash
gh issue edit 728 --milestone "v1.3 - Refactoring & Architecture"
gh issue edit 635 --milestone "v1.3 - Refactoring & Architecture"
gh issue edit 557 --milestone "v1.3 - Refactoring & Architecture"
```

### v1.4 - Features
```bash
gh issue edit 663 --milestone "v1.4 - Features"
gh issue edit 687 --milestone "v1.4 - Features"
gh issue edit 550 --milestone "v1.4 - Features"
gh issue edit 541 --milestone "v1.4 - Features"
gh issue edit 716 --milestone "v1.4 - Features"
gh issue edit 544 --milestone "v1.4 - Features"
```

### v1.5 - Bug Fixes
```bash
gh issue edit 729 --milestone "v1.5 - Bug Fixes"
```

### v2.0 - Future
```bash
gh issue edit 596 --milestone "v2.0 - Future"
```

---

## Summary

| Milestone | Open Issues | Priority | Target Date |
|-----------|-------------|----------|-------------|
| v1.1 - Security & Compliance | 10 | 最高 | 2025-Q2 |
| v1.2 - Documentation | 3 | 高 | 2025-Q2 |
| v1.3 - Refactoring & Architecture | 3 | 中 | 2025-Q3 |
| v1.4 - Features | 6 | 中 | 2025-Q3 |
| v1.5 - Bug Fixes | 1 | 高 | Continuous |
| v2.0 - Future | 1 | 低 | 2025-Q4 |
| **Total** | **24** | - | - |
