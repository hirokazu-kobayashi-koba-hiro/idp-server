# テナント作成API - AuditLogマッピング表

## 概要
テナント作成API (`TenantManagementApi.create`) が受け取るパラメータと、AuditLogテーブルに記録される内容のマッピング表

**情報源**:
- `/libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/control_plane/system_manager/TenantManagementEntryService.java`
- `/libs/idp-server-control-plane/src/main/java/org/idp/server/control_plane/management/tenant/TenantManagementRegistrationContext.java`
- `/libs/idp-server-control-plane/src/main/java/org/idp/server/control_plane/base/AuditLogCreator.java`

**確認日**: 2025-10-22

---

## 1. 入力パラメータ

### TenantManagementEntryService.create() メソッドパラメータ

| パラメータ名 | 型 | 説明 | 用途 |
|------------|---|------|------|
| `adminTenantIdentifier` | `TenantIdentifier` | 操作元テナントID | システム管理テナント識別子 |
| `operator` | `User` | 操作者情報 | 操作実行ユーザー |
| `oAuthToken` | `OAuthToken` | アクセストークン | 認可情報・クライアントID |
| `request` | `TenantRequest` | テナント作成リクエスト | リクエストボディ全体（下記参照） |
| `requestAttributes` | `RequestAttributes` | HTTPリクエスト属性 | IPアドレス・UserAgent等 |
| `dryRun` | `boolean` | ドライラン実行フラグ | true=実際には保存しない |

### TenantRequest 構造（リクエストボディ）

```json
{
  "tenant": {
    "tenant_identifier": "uuid",
    "tenant_name": "テナント名",
    "tenant_domain": "example.com",
    "authorization_provider": "internal",
    "attributes": { /* 任意属性 */ },
    "ui_config": { /* UI設定 */ },
    "cors_config": { /* CORS設定 */ },
    "session_config": { /* セッション設定 */ },
    "security_event_log_config": { /* セキュリティイベントログ設定 */ },
    "security_event_user_config": { /* セキュリティイベントユーザー設定 */ },
    "identity_policy_config": { /* アイデンティティポリシー設定 */ }
  },
  "authorization_server": {
    /* AuthorizationServerConfiguration全フィールド */
  }
}
```

---

## 2. AuditLogテーブル マッピング

### 現状のマッピング（❌ 問題あり）

| AuditLogフィールド | 現在のマッピング元 | 実際の値 | ステータス |
|------------------|-----------------|---------|-----------|
| `id` | UUID.randomUUID() | ランダムUUID | ✅ 正常 |
| `type` | 固定値 | `"TenantManagementApi.create"` | ✅ 正常 |
| `description` | `context.type()` | `"tenant"` | ✅ 正常 |
| `tenant_id` | `tenant.identifier().value()` | **adminTenantのID** | ⚠️ 操作元テナント（正しい） |
| `client_id` | `oAuthToken.requestedClientId()` | クライアントID | ✅ 正常 |
| `user_id` | `user.sub()` | ユーザーID（sub） | ✅ 正常 |
| `external_user_id` | `user.externalUserId()` | 外部ユーザーID | ✅ 正常 |
| `user_payload` | `user.toMap()` | ユーザー情報JSON | ✅ 正常 |
| `target_resource` | `requestAttributes.resource()` | リソース名 | ✅ 正常 |
| `target_resource_action` | `requestAttributes.action()` | アクション名 | ✅ 正常 |
| `ip_address` | `requestAttributes.getIpAddress()` | IPアドレス | ✅ 正常 |
| `user_agent` | `requestAttributes.getUserAgent()` | UserAgent | ✅ 正常 |
| **`request_payload`** | `context.requestPayload()` | **`context.payload()`と同じ** | ❌ **問題**: 元のリクエストが記録されていない |
| `before` | `JsonNodeWrapper.empty()` | 空 | ✅ 正常（新規作成のため） |
| **`after`** | `context.payload()` | **`authorizationServerConfiguration.toMap()`** | ❌ **問題**: テナント情報がない |
| `outcome_result` | 固定値 | `"success"` | ✅ 正常 |
| `outcome_reason` | `null` | null | ✅ 正常 |
| **`target_tenant_id`** | `tenantId`（=`tenant_id`と同じ） | **adminTenantのID** | ❌ **問題**: 新規テナントIDであるべき |
| `attributes` | `JsonNodeWrapper.empty()` | 空 | ✅ 正常 |
| `dry_run` | `context.isDryRun()` | true/false | ✅ 正常 |
| `created_at` | `SystemDateTime.now()` | 現在時刻 | ✅ 正常 |

---

## 3. 問題点の詳細

### 問題1: `request_payload` が元のリクエストを記録していない

**現状**:
```java
// TenantManagementRegistrationContext.java (Line 84-86)
@Override
public Map<String, Object> requestPayload() {
  return payload(); // TODO: 元のリクエストデータを返す
}
```

**影響**:
- 元のリクエストボディ（`tenant` + `authorization_server`）が失われる
- `request_payload`フィールドに`authorizationServerConfiguration`だけが記録される
- 監査ログとして「何が要求されたか」が不完全

**根本原因**:
- `TenantManagementRegistrationContext`が`TenantRequest`を保持していない
- `TenantManagementRegistrationContextCreator.create()`でrequestを渡していない

---

### 問題2: `after` (payload) がテナント情報を含んでいない

**現状**:
```java
// TenantManagementRegistrationContext.java (Line 78-81)
@Override
public Map<String, Object> payload() {
  return authorizationServerConfiguration.toMap();
}
```

**影響**:
- 作成された新規テナントの情報（ID、name、domain等）が記録されない
- `after`フィールドに認可サーバー設定のみが記録される
- 「何が作成されたか」の主要情報が欠落

**期待される動作**:
```java
@Override
public Map<String, Object> payload() {
  return newTenant.toMap(); // 新規作成されたテナント情報を返すべき
}
```

---

### 問題3: `target_tenant_id` が操作元テナントIDになっている

**現状**:
```java
// AuditLogCreator.java (Line 59)
String targetTenantId = tenantId; // tenantIdはadminTenantのID
```

**影響**:
- 操作対象（新規作成テナント）と操作元（adminTenant）が区別できない
- マルチテナント環境で「誰が誰に対して操作したか」が不明確

**期待される動作**:
```java
// TenantManagementRegistrationContext.java (追加すべきメソッド)
@Override
public String targetTenantId() {
  return newTenant.identifierValue(); // 新規テナントのIDを返すべき
}
```

---

## 4. 修正後の期待されるマッピング

### 修正方針

| AuditLogフィールド | 修正後のマッピング元 | 期待される値 | 変更内容 |
|------------------|------------------|-------------|---------|
| `request_payload` | `context.requestPayload()` → `request.toMap()` | **元のリクエストボディ全体** | ✅ 修正: TenantRequestを記録 |
| `after` | `context.payload()` → `newTenant.toMap()` | **新規作成されたテナント情報** | ✅ 修正: newTenantを記録 |
| `target_tenant_id` | `context.targetTenantId()` → `newTenant.identifierValue()` | **新規テナントのID** | ✅ 修正: 操作対象を記録 |

### 修正後の記録内容イメージ

```json
{
  "type": "TenantManagementApi.create",
  "description": "tenant",
  "tenant_id": "admin-tenant-id",  // 操作元
  "target_tenant_id": "new-tenant-id",  // 操作対象（新規作成）

  "request_payload": {  // 元のリクエスト全体
    "tenant": {
      "tenant_identifier": "new-tenant-id",
      "tenant_name": "New Tenant",
      ...
    },
    "authorization_server": { ... }
  },

  "before": {},  // 新規作成なので空

  "after": {  // 作成されたテナント情報
    "identifier": "new-tenant-id",
    "name": "New Tenant",
    "domain": "example.com",
    "type": "PUBLIC",
    ...
  },

  "outcome_result": "success",
  "dry_run": false
}
```

---

## 5. 実装修正箇所

### 5.1 TenantManagementRegistrationContext.java

**追加フィールド**:
```java
TenantRequest request; // 元のリクエストを保持
```

**コンストラクタ修正**:
```java
public TenantManagementRegistrationContext(
    Tenant adminTenant,
    Tenant newTenant,
    AuthorizationServerConfiguration authorizationServerConfiguration,
    Organization organization,
    User user,
    TenantRequest request,  // ← 追加
    boolean dryRun) {
  this.adminTenant = adminTenant;
  this.newTenant = newTenant;
  this.authorizationServerConfiguration = authorizationServerConfiguration;
  this.organization = organization;
  this.user = user;
  this.request = request;  // ← 追加
  this.dryRun = dryRun;
}
```

**メソッド修正**:
```java
@Override
public Map<String, Object> payload() {
  return newTenant.toMap();  // 修正: authorizationServerConfiguration → newTenant
}

@Override
public Map<String, Object> requestPayload() {
  return request.toMap();  // 修正: payload() → request.toMap()
}

@Override
public String targetTenantId() {
  return newTenant.identifierValue();  // 追加: 新規テナントIDを返す
}
```

### 5.2 TenantManagementRegistrationContextCreator.java

**create()メソッド修正（Line 118-119）**:
```java
return new TenantManagementRegistrationContext(
    adminTenant,
    tenant,
    authorizationServerConfiguration,
    assigned,
    user,
    request,  // ← 追加: 元のリクエストを渡す
    dryRun);
```

---

## 6. 他のContext実装への影響

同様の問題は他の8つのConfigRegistrationContext実装にも存在する可能性が高い:

1. ✅ **UserRegistrationContext** - 既に修正済み
2. ✅ **RoleRegistrationContext** - 既に修正済み
3. ❌ **TenantManagementRegistrationContext** - 本ドキュメント対象
4. ❌ **ClientRegistrationContext**
5. ❌ **PermissionRegistrationContext**
6. ❌ **AuthenticationConfigRegistrationContext**
7. ❌ **AuthenticationPolicyConfigRegistrationContext**
8. ❌ **FederationConfigRegistrationContext**
9. ❌ **IdentityVerificationConfigRegistrationContext**
10. ❌ **SecurityEventHookConfigRegistrationContext**

各Context実装で以下を確認・修正する必要がある:
- 元のリクエストオブジェクトを保持しているか
- `payload()`が適切なエンティティ情報を返しているか
- `requestPayload()`が元のリクエストを返しているか
- `targetTenantId()`が正しい操作対象を返しているか

---

## 7. まとめ

### 重要度: 🔴 HIGH

テナント作成APIのAuditLog記録には以下の重大な欠陥がある:

1. **元のリクエストが記録されていない** → 監査証跡として不完全
2. **作成されたテナント情報が記録されていない** → 「何が作成されたか」が不明
3. **操作対象テナントIDが誤っている** → マルチテナント環境で混乱の原因

これらは**Issue #529の本質的な問題**であり、早急な修正が必要。

### 次のステップ

1. TenantManagementRegistrationContext の修正実装
2. TenantManagementRegistrationContextCreator の修正実装
3. 他の8つのContext実装の同様の問題を修正
4. E2Eテストで修正内容を検証
