# Control Plane - 管理API概要

## Control Planeとは

**システム管理者・組織管理者が使用する管理API**

### Application Planeとの違い

| 項目 | Control Plane | Application Plane |
|------|--------------|------------------|
| **目的** | システム・リソース管理 | 認証・認可フロー |
| **ユーザー** | システム管理者・組織管理者 | エンドユーザー・アプリケーション |
| **URL** | `/v1/management/...` | `/oauth/...`, `/{tenant}/v1/me/...` |
| **認証** | 管理者トークン（特定権限必須） | ユーザートークン or クライアント認証 |
| **実装層** | `control_plane/` パッケージ | `application/` パッケージ |

---

## Control Planeの種類

### 1. システムレベルAPI

**テナント単位**でリソース管理

```
GET    /v1/management/tenants/{tenantId}/clients
POST   /v1/management/tenants/{tenantId}/clients
PUT    /v1/management/tenants/{tenantId}/clients/{clientId}
DELETE /v1/management/tenants/{tenantId}/clients/{clientId}
```

**特徴**:
- ✅ システム管理者が使用
- ✅ テナント第一引数
- ✅ 権限: `client:read`, `client:write`等

### 2. 組織レベルAPI

**組織単位**でリソース管理

```
GET    /v1/management/organizations/{orgId}/tenants/{tenantId}/clients
POST   /v1/management/organizations/{orgId}/tenants/{tenantId}/clients
```

**特徴**:
- ✅ 組織管理者が使用
- ✅ 組織ID + テナントID
- ✅ 4ステップアクセス制御（組織-テナント関係検証）

---

## 管理対象リソース

| リソース | 説明 |
|---------|-----|
| **Tenant** | テナント（顧客）管理 |
| **Client** | OAuth/OIDCクライアント管理 |
| **User** | ユーザー管理 |
| **Role** | ロール管理 |
| **Permission** | 権限管理 |
| **Authentication Config** | 認証設定管理 |
| **Authentication Policy** | 認証ポリシー管理 |
| **Federation Config** | フェデレーション設定管理 |

---

## 実装パターン

Control Plane APIの実装は以下の構造：

```
1. API契約定義（Control Plane層）
   ├─ {Domain}ManagementApi インターフェース
   ├─ Request/Response DTO
   └─ Context Creator

2. EntryService実装（UseCase層）
   ├─ トランザクション管理
   ├─ 権限チェック
   ├─ Audit Log記録
   └─ Dry Run対応

3. Controller実装（Controller層）
   └─ HTTPエンドポイント
```

---

## 学習の進め方

### Step 1: 概要理解（完了）✅
このドキュメントで以下を理解：
- Control Planeとは何か
- Application Planeとの違い
- システムレベルAPI vs 組織レベルAPI
- 管理対象リソース


---

**最終更新**: 2025-10-12
