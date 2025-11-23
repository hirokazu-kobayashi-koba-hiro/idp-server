# Issue #441 Phase 1: 現状把握 - 詳細調査レポート

**Issue**: [#441 テナント統計データ収集・分析機能](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/issues/441)
**調査日**: 2025-11-23
**調査者**: AI Assistant (Claude Code)
**ステータス**: Phase 1完了

---

## 📋 調査目的

統計・分析機能の実装に向けて、既存のイベントログ取得機能、ユーザー管理機能、データ構造を完全に棚卸しし、統計APIの実装可能性を評価する。

---

## 🎯 Executive Summary

### 主要発見

✅ **既存データの充実度**: 統計機能に必要なデータは既に完全に記録されている
✅ **優れたインデックス設計**: 時系列集計に最適な複合インデックスが整備済み
✅ **Repository パターン整備**: `findTotalCount()` 等の集計用メソッドが既に実装済み
✅ **マルチテナント分離**: RLS + テナント第一引数パターンで完全分離

### 統計API実装の難易度

🟢 **基本統計（DAU/MAU/成功率等）**: **簡単** - 既存Repositoryパターンで即実装可能
🟡 **リアルタイム統計**: **中程度** - Materialized View または専用集計テーブル推奨
🔴 **予測分析・異常検知**: **高度** - ML基盤との統合が必要

---

## 1. 既存イベントログ取得機能

### 1.1 Audit Log API（管理操作の監査ログ）

#### システムレベルAPI

**EntryService**: `AuditLogManagementEntryService`
**ファイルパス**: `/Users/hirokazu.kobayashi/work/idp-server/libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/control_plane/system_manager/AuditLogManagementEntryService.java`

**REST Endpoint**:
```
GET /v1/management/tenants/{tenant-id}/audit-logs
GET /v1/management/tenants/{tenant-id}/audit-logs/{id}
```

**主要メソッド**:
```java
findList(AdminAuthenticationContext, TenantIdentifier, AuditLogQueries, RequestAttributes)
get(AdminAuthenticationContext, TenantIdentifier, AuditLogIdentifier, RequestAttributes)
```

#### 組織レベルAPI

**EntryService**: `OrgAuditLogManagementEntryService`
**ファイルパス**: `/Users/hirokazu.kobayashi/work/idp-server/libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/control_plane/organization_manager/OrgAuditLogManagementEntryService.java`

**REST Endpoint**:
```
GET /v1/management/organizations/{organizationId}/tenants/{tenantId}/audit-logs
GET /v1/management/organizations/{organizationId}/tenants/{tenantId}/audit-logs/{auditLogId}
```

#### クエリパラメータ（AuditLogQueries）

| パラメータ | 型 | 説明 |
|-----------|---|------|
| `from` | LocalDateTime | 開始日時 |
| `to` | LocalDateTime | 終了日時 |
| `id` | UUID | Audit Log ID |
| `type` | String (カンマ区切り) | イベント種別 |
| `description` | String | 説明フィルタ |
| `target_resource` | String | 対象リソース |
| `target_resource_action` | String | アクション (create/update/delete) |
| `client_id` | String | クライアントID |
| `user_id` | UUID | ユーザーID |
| `external_user_id` | String | 外部ユーザーID |
| `outcome_result` | String | 結果 (success/failure) |
| `target_tenant_id` | String | 対象テナントID |
| `dry_run` | Boolean | ドライラン実行フラグ |
| `attributes.*` | String | カスタム属性フィルタ |
| `limit` | Integer | ページサイズ (デフォルト: 20) |
| `offset` | Integer | オフセット (デフォルト: 0) |

---

### 1.2 Security Event API（セキュリティイベントログ）

#### システムレベルAPI

**EntryService**: `SecurityEventManagementEntryService`
**ファイルパス**: `/Users/hirokazu.kobayashi/work/idp-server/libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/control_plane/system_manager/SecurityEventManagementEntryService.java`

**REST Endpoint**:
```
GET /v1/management/tenants/{tenant-id}/security-events
GET /v1/management/tenants/{tenant-id}/security-events/{event-id}
```

#### 組織レベルAPI

**EntryService**: `OrgSecurityEventManagementEntryService`
**ファイルパス**: `/Users/hirokazu.kobayashi/work/idp-server/libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/control_plane/organization_manager/OrgSecurityEventManagementEntryService.java`

**REST Endpoint**:
```
GET /v1/management/organizations/{organizationId}/tenants/{tenantId}/security-events
GET /v1/management/organizations/{organizationId}/tenants/{tenantId}/security-events/{eventId}
```

#### クエリパラメータ（SecurityEventQueries）

| パラメータ | 型 | 説明 |
|-----------|---|------|
| `from` | LocalDateTime | 開始日時 |
| `to` | LocalDateTime | 終了日時 |
| `id` | UUID | イベントID |
| `event_type` | String (カンマ区切り) | イベント種別 (TOKEN_ISSUED, USER_AUTHENTICATED等) |
| `client_id` | String | クライアントID |
| `user_id` | UUID | ユーザーID |
| `external_user_id` | String | 外部ユーザーID |
| `details.*` | String | JSONB詳細フィルタ |
| `limit` | Integer | ページサイズ (デフォルト: 20) |
| `offset` | Integer | オフセット (デフォルト: 0) |

---

### 1.3 User Management API（ユーザー一覧取得）

#### システムレベルAPI

**EntryService**: `UserManagementEntryService`
**ファイルパス**: `/Users/hirokazu.kobayashi/work/idp-server/libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/control_plane/system_manager/UserManagementEntryService.java`

**REST Endpoint**:
```
GET /v1/management/tenants/{tenant-id}/users
GET /v1/management/tenants/{tenant-id}/users/{user-id}
```

#### 組織レベルAPI

**EntryService**: `OrgUserManagementEntryService`
**ファイルパス**: `/Users/hirokazu.kobayashi/work/idp-server/libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/control_plane/organization_manager/OrgUserManagementEntryService.java`

**REST Endpoint**:
```
GET /v1/management/organizations/{organizationId}/tenants/{tenantId}/users
GET /v1/management/organizations/{organizationId}/tenants/{tenantId}/users/{userId}
```

#### クエリパラメータ（UserQueries）

| パラメータ | 型 | 説明 |
|-----------|---|------|
| `from` | LocalDateTime | 開始日時 |
| `to` | LocalDateTime | 終了日時 |
| `id` | UUID | ユーザーID |
| `user_id` | UUID | ユーザーIDフィルタ |
| `external_user_id` | String | 外部ユーザーID |
| `provider_id` | String | プロバイダーID |
| `name` | String | 氏名 |
| `given_name` | String | 名 |
| `family_name` | String | 姓 |
| `middle_name` | String | ミドルネーム |
| `nickname` | String | ニックネーム |
| `preferred_username` | String | ユーザー名 |
| `email` | String | メールアドレス |
| `status` | String | ステータス (ACTIVE/INACTIVE/DELETED) |
| `phone_number` | String | 電話番号 |
| `tenant_id` | UUID | テナントID |
| `role` | String | ロール |
| `permission` | String | 権限 |
| `details.*` | String | カスタム詳細フィルタ |
| `limit` | Integer | ページサイズ (デフォルト: 20) |
| `offset` | Integer | オフセット (デフォルト: 0) |

---

## 2. データモデル詳細

### 2.1 AuditLog データモデル

**ファイルパス**: `/Users/hirokazu.kobayashi/work/idp-server/libs/idp-server-platform/src/main/java/org/idp/server/platform/audit/AuditLog.java`

**主要フィールド**:
```java
UUID id                      // 監査ログID
String type                  // イベント種別
String description           // 説明
UUID tenantId                // テナントID
String clientId              // クライアントID
UUID userId                  // ユーザーID
String externalUserId        // 外部ユーザーID
JSONB userPayload            // ユーザー情報スナップショット
String targetResource        // 対象リソース
String targetResourceAction  // アクション (create/update/delete)
JSONB request                // リクエスト詳細
JSONB before                 // 変更前状態
JSONB after                  // 変更後状態
String outcomeResult         // 結果 (success/failure)
String outcomeReason         // 失敗理由
String targetTenantId        // 対象テナントID
String ipAddress             // IPアドレス
String userAgent             // ユーザーエージェント
JSONB attributes             // カスタム属性
Boolean dryRun               // ドライランフラグ
Timestamp createdAt          // 作成日時 ★統計用
```

**統計に使えるフィールド**:
- ✅ `type` - イベント種別ごとの集計
- ✅ `outcomeResult` - 成功/失敗率
- ✅ `createdAt` - 時系列集計
- ✅ `targetResourceAction` - アクション種別集計
- ✅ `userId` - ユーザーごとのアクティビティ
- ✅ `clientId` - クライアントごとの使用状況
- ✅ `dryRun` - テスト実行 vs 本番実行の比率

---

### 2.2 SecurityEvent データモデル

**ファイルパス**: `/Users/hirokazu.kobayashi/work/idp-server/libs/idp-server-platform/src/main/java/org/idp/server/platform/security/SecurityEvent.java`

**主要フィールド**:
```java
UUID identifier              // イベントID
SecurityEventType type       // イベント種別 (TOKEN_ISSUED, USER_AUTHENTICATED等)
String description           // 説明
SecurityEventTenant tenant   // テナント情報 (id, issuer)
SecurityEventClient client   // クライアント情報 (id, clientId)
SecurityEventUser user       // ユーザー情報 (sub, externalUserId)
IpAddress ipAddress          // IPアドレス
UserAgent userAgent          // ユーザーエージェント
JSONB detail                 // イベント詳細
Timestamp createdAt          // 作成日時 ★統計用
```

**統計に使えるフィールド**:
- ✅ `type` - イベント種別集計 (認証成功/失敗、トークン発行等)
- ✅ `createdAt` - 時系列集計
- ✅ `user.sub` - ユニークユーザー数 (DAU/MAU)
- ✅ `client.clientId` - クライアント別使用状況
- ✅ `ipAddress` - 地理的分布分析
- ✅ `userAgent` - デバイス/ブラウザ分析
- ✅ `detail` (JSONB) - 柔軟なカテゴリ分析

---

### 2.3 User データモデル

**ファイルパス**: `/Users/hirokazu.kobayashi/work/idp-server/libs/idp-server-core/src/main/java/org/idp/server/core/openid/identity/User.java`

**主要フィールド**:
```java
UUID sub                     // ユーザーID
String providerId            // 認証プロバイダー
String externalUserId        // 外部ユーザーID
String preferredUsername     // ユーザー名
String email                 // メールアドレス
Boolean emailVerified        // メール検証済み
String phoneNumber           // 電話番号
Boolean phoneNumberVerified  // 電話番号検証済み
JSONB authenticationDevices  // 認証デバイス (WebAuthn/FIDO2)
JSONB verifiedClaims         // 検証済みクレーム
UserStatus status            // ステータス (ACTIVE/INACTIVE/DELETED)
Timestamp createdAt          // 作成日時 ★統計用
Timestamp updatedAt          // 更新日時 ★統計用
```

**統計に使えるフィールド**:
- ✅ `status` - アクティブユーザー数
- ✅ `createdAt` - ユーザー成長トレンド
- ✅ `providerId` - 認証方法の分布
- ✅ `emailVerified` / `phoneNumberVerified` - 検証率
- ✅ `authenticationDevices` - FIDO2/WebAuthn採用率

---

## 3. データベーステーブル構造

### 3.1 audit_log テーブル

**DDLファイル**: `/Users/hirokazu.kobayashi/work/idp-server/libs/idp-server-database/postgresql/V0_9_0__init_lib.sql:956-998`

```sql
CREATE TABLE audit_log (
    id UUID PRIMARY KEY,
    type VARCHAR(255) NOT NULL,
    description VARCHAR(255) NOT NULL,
    tenant_id UUID NOT NULL,
    client_id VARCHAR(255) NOT NULL,
    user_id UUID NOT NULL,
    external_user_id VARCHAR(255),
    user_payload JSONB NOT NULL,
    target_tenant_id VARCHAR(255),
    target_resource TEXT NOT NULL,
    target_resource_action TEXT NOT NULL,
    request_payload JSONB,
    before_payload JSONB,
    after_payload JSONB,
    outcome_result VARCHAR(20) DEFAULT 'unknown',
    outcome_reason VARCHAR(255),
    ip_address TEXT,
    user_agent TEXT,
    dry_run BOOLEAN,
    attributes JSONB,
    created_at TIMESTAMP DEFAULT now()
);
```

**インデックス（10個）**:
```sql
CREATE INDEX idx_audit_log_tenant_id ON audit_log (tenant_id);
CREATE INDEX idx_audit_log_client_id ON audit_log (client_id);
CREATE INDEX idx_audit_log_user_id ON audit_log (user_id);
CREATE INDEX idx_audit_log_external_user_id ON audit_log (external_user_id);
CREATE INDEX idx_audit_log_created_at ON audit_log (created_at);
CREATE INDEX idx_audit_log_tenant_created_at ON audit_log (tenant_id, created_at); -- 複合 ★重要
CREATE INDEX idx_audit_log_attributes ON audit_log USING GIN (attributes);
CREATE INDEX idx_audit_log_outcome ON audit_log (outcome_result);
CREATE INDEX idx_audit_log_type_created ON audit_log (type, created_at DESC); -- 複合 ★重要
CREATE INDEX idx_audit_log_target_tenant ON audit_log (target_tenant_id, created_at DESC);
```

**RLS ポリシー**:
```sql
ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_policy ON audit_log
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
```

---

### 3.2 security_event テーブル

**DDLファイル**: `/Users/hirokazu.kobayashi/work/idp-server/libs/idp-server-database/postgresql/V0_9_0__init_lib.sql:595-629`

```sql
CREATE TABLE security_event (
    id UUID PRIMARY KEY,
    type VARCHAR(255) NOT NULL,
    description VARCHAR(255) NOT NULL,
    tenant_id UUID NOT NULL,
    tenant_name VARCHAR(255) NOT NULL,
    client_id VARCHAR(255) NOT NULL,
    client_name VARCHAR(255) NOT NULL,
    user_id UUID,
    user_name VARCHAR(255),
    external_user_id VARCHAR(255),
    ip_address INET,
    user_agent TEXT,
    detail JSONB NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**インデックス（8個）**:
```sql
CREATE INDEX idx_events_type ON security_event (type);
CREATE INDEX idx_events_tenant ON security_event (tenant_id);
CREATE INDEX idx_events_client ON security_event (client_id);
CREATE INDEX idx_events_user ON security_event (user_id);
CREATE INDEX idx_events_external_user_id ON security_event (external_user_id);
CREATE INDEX idx_events_created_at ON security_event (created_at);
CREATE INDEX idx_events_detail_jsonb ON security_event USING GIN (detail); -- JSONB ★柔軟
CREATE INDEX idx_events_tenant_created_at ON security_event (tenant_id, created_at); -- 複合 ★重要
```

**RLS ポリシー**: 同様に適用

**⭐ 統計に最適な理由**:
1. **複合インデックス**: `(tenant_id, created_at)` で時系列集計が高速
2. **JSONB GIN インデックス**: 詳細フィルタリングが可能
3. **型種別インデックス**: イベント種別ごとの集計が高速

---

### 3.3 idp_user テーブル

**DDLファイル**: `/Users/hirokazu.kobayashi/work/idp-server/libs/idp-server-database/postgresql/V0_9_0__init_lib.sql:175-230`

```sql
CREATE TABLE idp_user (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    external_user_id VARCHAR(255),
    external_user_original_payload JSONB,
    name VARCHAR(255),
    given_name VARCHAR(255),
    family_name VARCHAR(255),
    middle_name VARCHAR(255),
    nickname VARCHAR(255),
    preferred_username VARCHAR(255) NOT NULL,
    profile TEXT,
    picture TEXT,
    website TEXT,
    email VARCHAR(255),
    email_verified BOOLEAN,
    gender VARCHAR(255),
    birthdate VARCHAR(255),
    zoneinfo VARCHAR(255),
    locale VARCHAR(255),
    phone_number VARCHAR(255),
    phone_number_verified BOOLEAN,
    address JSONB,
    custom_properties JSONB,
    credentials JSONB,
    hashed_password TEXT,
    authentication_devices JSONB,
    verified_claims JSONB,
    status VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now()
);
```

**インデックス**:
```sql
CREATE INDEX idx_idp_external_user_id ON idp_user (tenant_id, provider_id, external_user_id);
CREATE INDEX idx_idp_user_tenant_email ON idp_user (tenant_id, email);
CREATE INDEX idx_idp_user_tenant_phone ON idp_user (tenant_id, phone_number);
CREATE INDEX idx_user_devices_gin_path_ops ON idp_user USING GIN (authentication_devices);
```

---

### 3.4 その他の統計関連テーブル

#### oauth_token テーブル

**DDLファイル**: `/Users/hirokazu.kobayashi/work/idp-server/libs/idp-server-database/postgresql/V0_9_0__init_lib.sql:442-493`

**統計用フィールド**:
```sql
grant_type VARCHAR(255)           -- authorization_code, refresh_token, client_credentials
token_type VARCHAR(20)            -- Bearer, DPoP
user_id UUID                      -- トークン所有者
client_id VARCHAR(255)            -- クライアントID
scopes TEXT                       -- スコープ
created_at TIMESTAMP              -- 発行日時 ★統計用
access_token_expires_at TIMESTAMP -- アクセストークン有効期限
refresh_token_expires_at TIMESTAMP -- リフレッシュトークン有効期限
```

**統計例**:
- トークン発行数（日次/grant_type別）
- アクティブトークン数
- トークンリフレッシュ頻度

#### authentication_transaction テーブル

**DDLファイル**: `/Users/hirokazu.kobayashi/work/idp-server/libs/idp-server-database/postgresql/V0_9_0__init_lib.sql:789-826`

**統計用フィールド**:
```sql
flow VARCHAR(255)                 -- 認証フロー種別 (password, mfa, biometric)
user_id UUID                      -- ユーザーID
client_id VARCHAR(255)            -- クライアントID
created_at TIMESTAMP              -- 作成日時 ★統計用
expires_at TIMESTAMP              -- 有効期限
```

**統計例**:
- 認証フロー別使用状況
- 認証セッション継続時間

---

## 4. Repository インターフェース

### 4.1 SecurityEventQueryRepository

**ファイルパス**: `/Users/hirokazu.kobayashi/work/idp-server/libs/idp-server-platform/src/main/java/org/idp/server/platform/security/repository/SecurityEventQueryRepository.java`

```java
public interface SecurityEventQueryRepository {
    long findTotalCount(Tenant tenant, SecurityEventQueries queries);
    List<SecurityEvent> findList(Tenant tenant, SecurityEventQueries queries);
    SecurityEvent find(Tenant tenant, SecurityEventIdentifier identifier);
}
```

**実装クラス**: `PostgresqlExecutor`
**ファイルパス**: `/Users/hirokazu.kobayashi/work/idp-server/libs/idp-server-core-adapter/src/main/java/org/idp/server/core/adapters/datasource/security/event/query/PostgresqlExecutor.java`

**サポートされるクエリ**:
- ✅ 日時範囲フィルタ (`hasFrom()` / `hasTo()`)
- ✅ イベント種別フィルタ (`hasEventType()` - IN句対応)
- ✅ クライアントフィルタ (`hasClientId()`)
- ✅ ユーザーフィルタ (`hasUserId()` / `hasExternalUserId()`)
- ✅ JSONB詳細フィルタ (`hasDetails()`)
- ✅ 件数取得 (`findTotalCount()`)
- ✅ ページネーション (`limit` / `offset`)

---

### 4.2 AuditLogQueryRepository

**ファイルパス**: `/Users/hirokazu.kobayashi/work/idp-server/libs/idp-server-platform/src/main/java/org/idp/server/platform/audit/AuditLogQueryRepository.java`

```java
public interface AuditLogQueryRepository {
    long findTotalCount(Tenant tenant, AuditLogQueries queries);
    List<AuditLog> findList(Tenant tenant, AuditLogQueries queries);
    AuditLog find(Tenant tenant, AuditLogIdentifier identifier);
}
```

**実装クラス**: `PostgresqlExecutor`
**ファイルパス**: `/Users/hirokazu.kobayashi/work/idp-server/libs/idp-server-core-adapter/src/main/java/org/idp/server/core/adapters/datasource/audit/query/PostgresqlExecutor.java`

**サポートされるクエリ**:
- ✅ 日時範囲フィルタ
- ✅ 結果フィルタ (`outcomeResult`)
- ✅ リソース種別フィルタ (`targetResource` / `targetResourceAction`)
- ✅ JSONB属性フィルタ (`attributes`)

---

### 4.3 UserQueryRepository

**ファイルパス**: `/Users/hirokazu.kobayashi/work/idp-server/libs/idp-server-core/src/main/java/org/idp/server/core/openid/identity/repository/UserQueryRepository.java`

```java
public interface UserQueryRepository {
    User get(Tenant tenant, UserIdentifier userIdentifier);
    long findTotalCount(Tenant tenant, UserQueries queries);
    List<User> findList(Tenant tenant, UserQueries queries);
    User findByEmail(Tenant tenant, String hint, String providerId);
    User findByPhone(Tenant tenant, String hint, String providerId);
    User findByPreferredUsername(Tenant tenant, String providerId, String preferredUsername);
}
```

**サポートされるクエリ**:
- ✅ ユーザーステータスフィルタ (`status`)
- ✅ プロバイダーフィルタ (`providerId`)
- ✅ 日時範囲フィルタ
- ✅ 件数取得 (`findTotalCount()`)

---

## 5. 統計メトリクスの実装可能性評価

### 5.1 DAU (Daily Active Users)

**データソース**: `security_event` テーブル

**SQL例**:
```sql
SELECT DATE(created_at) as date, COUNT(DISTINCT user_id) as dau
FROM security_event
WHERE tenant_id = ?
  AND type = 'login_success'
  AND created_at >= ?
  AND created_at < ?
GROUP BY DATE(created_at)
ORDER BY date DESC;
```

**実装難易度**: 🟢 **簡単**
**使用インデックス**: `idx_events_tenant_created_at`, `idx_events_type`

---

### 5.2 MAU (Monthly Active Users)

**データソース**: `security_event` テーブル

**SQL例**:
```sql
SELECT COUNT(DISTINCT user_id) as mau
FROM security_event
WHERE tenant_id = ?
  AND type = 'login_success'
  AND created_at >= DATE_TRUNC('month', CURRENT_DATE)
  AND created_at < DATE_TRUNC('month', CURRENT_DATE) + INTERVAL '1 month';
```

**実装難易度**: 🟢 **簡単**

---

### 5.3 認証成功率

**データソース**: `security_event` テーブル

**SQL例**:
```sql
SELECT
  DATE(created_at) as date,
  COUNT(*) FILTER (WHERE type = 'login_success') as success_count,
  COUNT(*) FILTER (WHERE type = 'login_failure') as failure_count,
  ROUND(
    100.0 * COUNT(*) FILTER (WHERE type = 'login_success') / COUNT(*),
    2
  ) as success_rate_percent
FROM security_event
WHERE tenant_id = ?
  AND type IN ('login_success', 'login_failure')
  AND created_at >= ?
  AND created_at < ?
GROUP BY DATE(created_at)
ORDER BY date DESC;
```

**実装難易度**: 🟢 **簡単**

---

### 5.4 トークン発行数（grant_type別）

**データソース**: `oauth_token` テーブル

**SQL例**:
```sql
SELECT
  grant_type,
  DATE(created_at) as date,
  COUNT(*) as count
FROM oauth_token
WHERE tenant_id = ?
  AND created_at >= ?
  AND created_at < ?
GROUP BY grant_type, DATE(created_at)
ORDER BY date DESC, grant_type;
```

**実装難易度**: 🟢 **簡単**
**使用インデックス**: `idx_oauth_token_expires_at` (tenant_id, expires_at)

---

### 5.5 ユーザー成長トレンド

**データソース**: `idp_user` テーブル

**SQL例**:
```sql
SELECT
  DATE(created_at) as date,
  COUNT(*) as new_users,
  SUM(COUNT(*)) OVER (ORDER BY DATE(created_at)) as cumulative_users
FROM idp_user
WHERE tenant_id = ?
  AND created_at >= ?
  AND created_at < ?
GROUP BY DATE(created_at)
ORDER BY date DESC;
```

**実装難易度**: 🟢 **簡単**

---

### 5.6 管理操作統計

**データソース**: `audit_log` テーブル

**SQL例**:
```sql
SELECT
  type,
  target_resource_action,
  outcome_result,
  COUNT(*) as count
FROM audit_log
WHERE tenant_id = ?
  AND created_at >= ?
  AND created_at < ?
GROUP BY type, target_resource_action, outcome_result
ORDER BY count DESC;
```

**実装難易度**: 🟢 **簡単**
**使用インデックス**: `idx_audit_log_type_created`, `idx_audit_log_outcome`

---

## 6. パフォーマンス最適化の推奨事項

### 6.1 Materialized View の活用

大量データ（億単位）での集計高速化のため、日次集計用Materialized Viewを推奨：

```sql
CREATE MATERIALIZED VIEW security_event_daily_summary AS
SELECT
  tenant_id,
  DATE(created_at) as event_date,
  type,
  COUNT(*) as event_count,
  COUNT(DISTINCT user_id) as unique_users,
  COUNT(DISTINCT client_id) as unique_clients
FROM security_event
GROUP BY tenant_id, DATE(created_at), type;

CREATE INDEX idx_summary_tenant_date ON security_event_daily_summary (tenant_id, event_date);

-- 日次更新（Cron Job）
REFRESH MATERIALIZED VIEW CONCURRENTLY security_event_daily_summary;
```

---

### 6.2 追加インデックスの検討

時系列集計の更なる高速化：

```sql
-- oauth_token テーブル
CREATE INDEX idx_oauth_token_created_grant
  ON oauth_token (tenant_id, created_at, grant_type);

-- idp_user テーブル
CREATE INDEX idx_idp_user_created_status
  ON idp_user (tenant_id, created_at, status);

-- security_event テーブル
CREATE INDEX idx_security_event_type_created
  ON security_event (tenant_id, type, created_at);
```

---

### 6.3 パーティショニング（将来的検討）

テーブルサイズが10GB超の場合、月次パーティショニングを検討：

```sql
CREATE TABLE security_event_partitioned (
    LIKE security_event
) PARTITION BY RANGE (created_at);

CREATE TABLE security_event_2025_01 PARTITION OF security_event_partitioned
  FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');

CREATE TABLE security_event_2025_02 PARTITION OF security_event_partitioned
  FOR VALUES FROM ('2025-02-01') TO ('2025-03-01');
```

---

## 7. 既存パターンの活用方針

### 7.1 Handler-Service-Repository パターンの継承

統計APIも既存パターンに準拠：

```java
// Handler層
public class TenantStatisticsHandler {
    public TenantStatisticsResult handle(Tenant tenant, StatisticsQueries queries) {
        // サービス呼び出し
    }
}

// Service層
public class TenantStatisticsFindService {
    public TenantStatistics execute(Tenant tenant, StatisticsQueries queries) {
        long dau = securityEventQueryRepository.findDAU(tenant, queries);
        long mau = securityEventQueryRepository.findMAU(tenant, queries);
        // ...
    }
}

// Repository層
public interface TenantStatisticsQueryRepository {
    long findDAU(Tenant tenant, StatisticsQueries queries);
    long findMAU(Tenant tenant, StatisticsQueries queries);
    Map<String, Long> findEventCountByType(Tenant tenant, StatisticsQueries queries);
}
```

---

### 7.2 EntryService 10フェーズパターンの適用

```java
public class TenantStatisticsManagementEntryService {

    public TenantStatisticsManagementResponse findDashboard(
        AdminAuthenticationContext context,
        TenantIdentifier tenantIdentifier,
        StatisticsQueries queries,
        RequestAttributes attributes
    ) {
        // Phase 1: 認証・認可確認
        // Phase 2: Tenant取得
        Tenant tenant = tenantRepository.get(tenantIdentifier);

        // Phase 3: 権限検証
        // Phase 4: Handler実行
        TenantStatisticsResult result = handler.handle(tenant, queries);

        // Phase 5: AuditLog記録
        // Phase 6: Response変換
        return result.toResponse();
    }
}
```

---

## 8. 次のステップの推奨事項

### Phase 2: 外部調査（Keycloak/Auth0）

既存の設計ドキュメント `analytics-statistics-design.md` で既にカバー済み：
- Keycloakの統計機能（Prometheus連携）
- Auth0のAnalytics API
- 業界ベストプラクティス

### Phase 3: API仕様策定

基本統計APIのエンドポイント設計：

```
GET /v1/management/tenants/{tenant-id}/statistics/dashboard
GET /v1/management/tenants/{tenant-id}/statistics/summary
GET /v1/management/tenants/{tenant-id}/statistics/time-series
GET /v1/management/tenants/{tenant-id}/statistics/users
GET /v1/management/tenants/{tenant-id}/statistics/tokens
GET /v1/management/tenants/{tenant-id}/statistics/authentication
```

### Phase 4: PoC実装

最小限の統計API（1週間で実装可能）：
1. **DAU/MAU API**: `security_event` テーブルから集計
2. **認証成功率 API**: `security_event` テーブルから集計
3. **ユーザー成長 API**: `idp_user` テーブルから集計

---

## 9. 結論

### ✅ 既存データの充実度

| 項目 | 評価 | 詳細 |
|-----|------|------|
| **タイムスタンプ** | ⭐⭐⭐⭐⭐ | 全テーブルに `created_at` 完備 |
| **インデックス** | ⭐⭐⭐⭐⭐ | 複合インデックスで時系列集計最適化済み |
| **テナント分離** | ⭐⭐⭐⭐⭐ | RLS + インデックスで完全分離 |
| **柔軟性** | ⭐⭐⭐⭐⭐ | JSONB GINインデックスで詳細フィルタ対応 |
| **Repository** | ⭐⭐⭐⭐⭐ | `findTotalCount()` 等の集計メソッド実装済み |

### 🚀 実装の容易性

| メトリクス | 難易度 | 期間見積 |
|----------|-------|---------|
| **DAU/MAU** | 🟢 簡単 | 1-2日 |
| **認証成功率** | 🟢 簡単 | 1日 |
| **トークン発行数** | 🟢 簡単 | 1日 |
| **ユーザー成長** | 🟢 簡単 | 1日 |
| **管理操作統計** | 🟢 簡単 | 1日 |
| **リアルタイム統計** | 🟡 中程度 | 1週間 |
| **異常検知** | 🔴 高度 | 2-3週間 |

### 📊 最重要テーブル

1. **`security_event`** - 認証/セキュリティイベント（最優先）
2. **`audit_log`** - 管理操作監査
3. **`oauth_token`** - トークン発行履歴
4. **`idp_user`** - ユーザーベース成長
5. **`authentication_transaction`** - 認証フロー分析

---

## 付録A: クエリパフォーマンステスト例

### テストケース: DAU計算

```sql
EXPLAIN ANALYZE
SELECT DATE(created_at) as date, COUNT(DISTINCT user_id) as dau
FROM security_event
WHERE tenant_id = '67e7eae6-62b0-4500-9eff-87459f63fc66'
  AND type = 'login_success'
  AND created_at >= '2025-01-01'
  AND created_at < '2025-02-01'
GROUP BY DATE(created_at);
```

**期待される実行計画**:
- ✅ Index Scan on `idx_events_tenant_created_at`
- ✅ Filter on `type` using `idx_events_type`

---

## 付録B: 参考実装ファイル一覧

### EntryService
- `AuditLogManagementEntryService.java:45`
- `SecurityEventManagementEntryService.java:38`
- `UserManagementEntryService.java:52`

### Repository
- `SecurityEventQueryRepository.java:12`
- `AuditLogQueryRepository.java:10`
- `UserQueryRepository.java:15`

### Handler
- `AuditLogFindListService.java:23`

### DDL
- `V0_9_0__init_lib.sql:595-629` (security_event)
- `V0_9_0__init_lib.sql:956-998` (audit_log)
- `V0_9_0__init_lib.sql:175-230` (idp_user)

---

**次のアクション**: Phase 2（外部調査）またはPoC実装の選択を推奨
