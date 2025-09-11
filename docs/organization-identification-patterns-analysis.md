# 他サービスの組織特定パターン分析

## 主要SaaSサービスの組織特定方法

### 1. GitHub
```
# URLパス方式 (Organization スコープ)
GET /orgs/{org}/repos
GET /orgs/{org}/members
GET /orgs/{org}/teams

# URLパス方式 (Repository スコープ)
GET /repos/{owner}/{repo}/issues

# 特徴:
- パスパラメータで組織を明示
- RESTful設計に準拠
- 階層的なリソース構造
```

### 2. Slack
```
# Workspace ベース (サブドメイン)
https://{workspace}.slack.com/api/conversations.list

# OAuth scope での制御
scope: team:read, channels:read

# 特徴:
- サブドメインで組織(Workspace)を分離
- OAuth scopeで権限制御
- 1つのAPIキーは1つのWorkspaceに紐づく
```

### 3. Microsoft Graph API (Teams/Office 365)
```
# テナントID指定
GET https://graph.microsoft.com/v1.0/organization/{tenant-id}/...

# または現在のテナントコンテキスト
GET https://graph.microsoft.com/v1.0/me/...

# 特徴:
- Azure ADのテナントIDで分離
- Bearer tokenにテナント情報が含まれる
- Multi-tenant application の場合はテナントID必須
```

### 4. Google Workspace Admin API
```
# Customer ID (組織ID) 指定
GET /admin/directory/v1/customer/{customerId}/orgunits

# または 'my_customer' エイリアス
GET /admin/directory/v1/customer/my_customer/users

# 特徴:
- Customer IDで組織を特定
- Service Accountは特定の組織に紐づく
- Domain-wide delegation による権限委譲
```

### 5. AWS Organizations API
```
# Account context (暗黙的)
GET /organizations/accounts

# Cross-account access
arn:aws:iam::{account-id}:role/{role-name}

# 特徴:
- STSトークンに組織・アカウント情報が含まれる
- AssumeRole による組織間アクセス
- IAM Policy での細かい権限制御
```

### 6. Stripe
```
# Account context (暗黙的)
GET /v1/customers

# Connect platform (複数アカウント)
Stripe-Account: acct_1234567890

# 特徴:
- APIキーに組織(Account)情報が含まれる
- Stripe Connectでマルチテナント対応
- HTTPヘッダーでアカウント切り替え
```

### 7. Auth0
```
# Domain ベース
https://{tenant}.auth0.com/api/v2/users

# Management API (複数テナント管理)
Authorization: Bearer {management_token}
Content-Type: application/json

# 特徴:
- サブドメインでテナント分離
- Management APIでクロステナント操作
- JWTトークンにテナント情報含む
```

### 8. Okta
```
# Domain ベース
https://{yourOktaDomain}/api/v1/users

# 特徴:
- 各組織が独自のドメインを持つ
- APIトークンは組織固有
- Single tenancy アーキテクチャ
```

## パターン分類

### A. URLパス方式
```
優点:
- RESTful設計に合致
- 組織が明示的で分かりやすい
- キャッシュしやすい

欠点:
- URLが長くなる
- 組織IDが推測されやすい

採用サービス: GitHub, Google Admin API
```

### B. サブドメイン方式
```
優点:
- 組織ごとの完全分離
- データ漏洩リスクが低い
- カスタムドメイン対応可能

欠点:
- DNS設定が必要
- クロステナント操作が困難

採用サービス: Slack, Auth0, Okta
```

### C. HTTPヘッダー方式
```
優点:
- URLが簡潔
- 動的な組織切り替えが可能
- プロキシでの制御が容易

欠点:
- ヘッダーの設定忘れリスク
- デバッグが困難
- キャッシュの複雑化

採用サービス: Stripe (Stripe-Account)
```

### D. トークンコンテキスト方式
```
優点:
- 認証と認可が統合
- 組織情報の改ざん防止
- セキュリティが高い

欠点:
- トークン管理が複雑
- 組織切り替えにトークン再取得必要

採用サービス: Microsoft Graph, AWS
```

## idp-server での最適解

### 現状の制約
- 既存のManagement API構造
- テナント分離アーキテクチャ
- RESTful API設計

### 推奨パターン: URLパス + フォールバック

```java
// Pattern 1: URLパス優先 (推奨)
GET /management/organizations/{org-id}/tenants
GET /management/organizations/{org-id}/users
GET /management/organizations/{org-id}/clients

// Pattern 2: ヘッダーフォールバック
GET /management/tenants
Header: X-Organization-Id: org-123
```

### 実装理由

#### 1. **GitHub パターンを参考**
```java
// GitHub風の階層構造
/management/organizations/{org-id}/tenants/{tenant-id}
/management/organizations/{org-id}/tenants/{tenant-id}/users
/management/organizations/{org-id}/tenants/{tenant-id}/clients
```

#### 2. **既存APIとの共存**
```java
// システム管理者用 (既存)
GET /management/tenants          // 全テナント管理

// 組織管理者用 (新規)
GET /management/organizations/org-123/tenants  // 組織内テナント管理
```

#### 3. **段階的移行対応**
```java
public class OrganizationResolver {
  public static OrganizationIdentifier resolve(HttpServletRequest request) {
    // 1. URL パスから解決 (v2 API)
    OrganizationIdentifier fromPath = parseFromUrl(request.getRequestURI());
    if (fromPath != null) return fromPath;

    // 2. ヘッダーから解決 (v1 API との互換性)
    return parseFromHeader(request, "X-Organization-Id");
  }
}
```

## セキュリティ考慮事項

### 1. **組織ID の推測攻撃対策**
```java
// UUIDベースの組織ID使用
org-550e8400-e29b-41d4-a716-446655440000

// または暗号化された組織ID
org-encrypted-3x7k9m2n8p4q
```

### 2. **権限検証の多層化**
```java
// 1. URLパスの組織ID
// 2. JWTトークンの組織クレーム
// 3. データベースでの組織所属確認
```

### 3. **監査ログの強化**
```java
log.info("Organization access: user={}, org={}, action={}, resource={}",
         userId, organizationId, action, resourceId);
```

## 結論

**idp-server では GitHub スタイルのURLパス方式を主軸とし、HTTPヘッダーをフォールバックとするハイブリッドアプローチを推奨**

### 理由:
1. **RESTful設計との整合性** - 既存のAPI設計思想に合致
2. **明示性** - 組織スコープが明確
3. **段階的導入** - 既存APIとの共存が可能
4. **デバッグ性** - URLから組織コンテキストが分かる
5. **業界標準** - GitHub等の主要サービスが採用

### 実装優先度:
1. **Phase 1**: URLパス解析の実装
2. **Phase 2**: HTTPヘッダーフォールバックの追加
3. **Phase 3**: セキュリティ強化と監査ログ拡充