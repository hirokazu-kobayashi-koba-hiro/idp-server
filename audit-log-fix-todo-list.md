# AuditLog修正 - 完全TODOリスト

## Issue #529: Audit Log記録内容の完全化

**目的**: 全管理APIで正確なAuditLog記録を実現する

---

## 📋 修正タスク一覧

### 🔴 Phase 1: 共通基盤修正（最優先・全API影響）

#### ✅ Task 1.1: AuditLogCreator.create()のtargetTenantId修正
**ファイル**: `libs/idp-server-control-plane/src/main/java/org/idp/server/control_plane/base/AuditLogCreator.java`

**Line 59修正**:
```java
// ❌ 修正前
String targetTenantId = tenantId;

// ✅ 修正後
String targetTenantId = context.targetTenantId();
```

**影響**: 全管理API（10種類すべて）
**優先度**: 🔴 HIGH（最優先）
**工数**: 5分

---

### 🟡 Phase 2: TenantManagement完全修正（重要度高）

#### ✅ Task 2.1: TenantManagementRegistrationContextにTenantRequestフィールド追加
**ファイル**: `libs/idp-server-control-plane/src/main/java/org/idp/server/control_plane/management/tenant/TenantManagementRegistrationContext.java`

**追加内容**:
```java
import org.idp.server.control_plane.management.tenant.io.TenantRequest;

public class TenantManagementRegistrationContext implements ConfigRegistrationContext {
  Tenant adminTenant;
  Tenant newTenant;
  AuthorizationServerConfiguration authorizationServerConfiguration;
  Organization organization;
  User user;
  TenantRequest request;  // ← 追加
  boolean dryRun;

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
}
```

**優先度**: 🟡 HIGH
**工数**: 10分

---

#### ✅ Task 2.2: TenantManagementRegistrationContext.payload()修正
**ファイル**: 同上

**Line 78-81修正**:
```java
// ❌ 修正前
@Override
public Map<String, Object> payload() {
  return authorizationServerConfiguration.toMap();
}

// ✅ 修正後
@Override
public Map<String, Object> payload() {
  return newTenant.toMap();
}
```

**優先度**: 🟡 HIGH
**工数**: 5分

---

#### ✅ Task 2.3: TenantManagementRegistrationContext.requestPayload()修正
**ファイル**: 同上

**Line 84-86修正**:
```java
// ❌ 修正前
@Override
public Map<String, Object> requestPayload() {
  return payload(); // TODO: 元のリクエストデータを返す
}

// ✅ 修正後
@Override
public Map<String, Object> requestPayload() {
  return request.toMap();
}
```

**優先度**: 🟡 HIGH
**工数**: 5分

---

#### ✅ Task 2.4: TenantManagementRegistrationContext.targetTenantId()実装
**ファイル**: 同上

**Line 88の後に追加**:
```java
@Override
public String targetTenantId() {
  return newTenant.identifierValue();
}
```

**優先度**: 🟡 HIGH
**工数**: 5分

---

#### ✅ Task 2.5: TenantManagementRegistrationContextCreator.create()修正
**ファイル**: `libs/idp-server-control-plane/src/main/java/org/idp/server/control_plane/management/tenant/TenantManagementRegistrationContextCreator.java`

**Line 118-119修正**:
```java
// ❌ 修正前
return new TenantManagementRegistrationContext(
    adminTenant, tenant, authorizationServerConfiguration, assigned, user, dryRun);

// ✅ 修正後
return new TenantManagementRegistrationContext(
    adminTenant, tenant, authorizationServerConfiguration, assigned, user, request, dryRun);
```

**優先度**: 🟡 HIGH
**工数**: 5分

---

### 🟢 Phase 3: RoleManagement修正

#### ✅ Task 3.1: RoleRegistrationContext.targetTenantId()実装
**ファイル**: `libs/idp-server-control-plane/src/main/java/org/idp/server/control_plane/management/role/RoleRegistrationContext.java`

**Line 92の後に追加**:
```java
@Override
public String targetTenantId() {
  return tenant.identifierValue();
}
```

**優先度**: 🟢 MEDIUM
**工数**: 5分

---

### 🔵 Phase 4: ClientManagement修正

#### ⬜ Task 4.1: ClientRegistrationContextの実装状況確認
**ファイル**: `libs/idp-server-control-plane/src/main/java/org/idp/server/control_plane/management/oidc/client/ClientRegistrationContext.java`

**確認項目**:
- [ ] `type()`実装済み？
- [ ] `payload()`実装済み？何を返しているか？
- [ ] `requestPayload()`実装済み？
- [ ] `targetTenantId()`実装済み？
- [ ] `isDryRun()`実装済み？
- [ ] 元のリクエストオブジェクトを保持しているか？

**優先度**: 🔵 MEDIUM
**工数**: 15分（調査）

---

#### ⬜ Task 4.2: ClientRegistrationContextの不足メソッド実装
**ファイル**: 同上

**実装内容**: Task 4.1の調査結果に基づき決定

**優先度**: 🔵 MEDIUM
**工数**: 20分（実装内容により変動）

---

### 🔵 Phase 5: PermissionManagement修正

#### ⬜ Task 5.1: PermissionRegistrationContextの実装状況確認
**ファイル**: `libs/idp-server-control-plane/src/main/java/org/idp/server/control_plane/management/permission/PermissionRegistrationContext.java`

**確認項目**: Task 4.1と同様

**優先度**: 🔵 MEDIUM
**工数**: 15分（調査）

---

#### ⬜ Task 5.2: PermissionRegistrationContextの不足メソッド実装
**ファイル**: 同上

**実装内容**: Task 5.1の調査結果に基づき決定

**優先度**: 🔵 MEDIUM
**工数**: 20分（実装内容により変動）

---

### 🟣 Phase 6: IdentityVerificationConfig修正

#### ⬜ Task 6.1: IdentityVerificationConfigRegistrationContextの実装状況確認
**ファイル**: `libs/idp-server-control-plane/src/main/java/org/idp/server/control_plane/management/identity/verification/IdentityVerificationConfigRegistrationContext.java`

**確認項目**: Task 4.1と同様

**優先度**: 🟣 LOW
**工数**: 15分（調査）

---

#### ⬜ Task 6.2: IdentityVerificationConfigRegistrationContextの不足メソッド実装
**ファイル**: 同上

**実装内容**: Task 6.1の調査結果に基づき決定

**優先度**: 🟣 LOW
**工数**: 20分（実装内容により変動）

---

### 🟣 Phase 7: SecurityEventHookConfig修正

#### ⬜ Task 7.1: SecurityEventHookConfigRegistrationContextの実装状況確認
**ファイル**: `libs/idp-server-control-plane/src/main/java/org/idp/server/control_plane/management/security/hook/SecurityEventHookConfigRegistrationContext.java`

**確認項目**: Task 4.1と同様

**優先度**: 🟣 LOW
**工数**: 15分（調査）

---

#### ⬜ Task 7.2: SecurityEventHookConfigRegistrationContextの不足メソッド実装
**ファイル**: 同上

**実装内容**: Task 7.1の調査結果に基づき決定

**優先度**: 🟣 LOW
**工数**: 20分（実装内容により変動）

---

### 🟣 Phase 8: FederationConfig修正

#### ⬜ Task 8.1: FederationConfigRegistrationContextの実装状況確認
**ファイル**: `libs/idp-server-control-plane/src/main/java/org/idp/server/control_plane/management/federation/FederationConfigRegistrationContext.java`

**確認項目**: Task 4.1と同様

**優先度**: 🟣 LOW
**工数**: 15分（調査）

---

#### ⬜ Task 8.2: FederationConfigRegistrationContextの不足メソッド実装
**ファイル**: 同上

**実装内容**: Task 8.1の調査結果に基づき決定

**優先度**: 🟣 LOW
**工数**: 20分（実装内容により変動）

---

### 🟣 Phase 9: AuthenticationConfig修正

#### ⬜ Task 9.1: AuthenticationConfigRegistrationContextの実装状況確認
**ファイル**: `libs/idp-server-control-plane/src/main/java/org/idp/server/control_plane/management/authentication/configuration/AuthenticationConfigRegistrationContext.java`

**確認項目**: Task 4.1と同様

**優先度**: 🟣 LOW
**工数**: 15分（調査）

---

#### ⬜ Task 9.2: AuthenticationConfigRegistrationContextの不足メソッド実装
**ファイル**: 同上

**実装内容**: Task 9.1の調査結果に基づき決定

**優先度**: 🟣 LOW
**工数**: 20分（実装内容により変動）

---

### 🟣 Phase 10: AuthenticationPolicyConfig修正

#### ⬜ Task 10.1: AuthenticationPolicyConfigRegistrationContextの実装状況確認
**ファイル**: `libs/idp-server-control-plane/src/main/java/org/idp/server/control_plane/management/authentication/policy/AuthenticationPolicyConfigRegistrationContext.java`

**確認項目**: Task 4.1と同様

**優先度**: 🟣 LOW
**工数**: 15分（調査）

---

#### ⬜ Task 10.2: AuthenticationPolicyConfigRegistrationContextの不足メソッド実装
**ファイル**: 同上

**実装内容**: Task 10.1の調査結果に基づき決定

**優先度**: 🟣 LOW
**工数**: 20分（実装内容により変動）

---

### ✅ Phase 11: 検証

#### ⬜ Task 11.1: 全修正完了後のビルド確認
**コマンド**:
```bash
./gradlew clean build
```

**確認項目**:
- [ ] コンパイルエラーなし
- [ ] テストエラーなし
- [ ] Spotlessチェックパス

**優先度**: ✅ CRITICAL
**工数**: 10分

---

#### ⬜ Task 11.2: E2Eテストで修正内容を検証
**テスト対象**:
- [ ] TenantManagement: create操作でAuditLog確認
- [ ] UserManagement: create操作でAuditLog確認
- [ ] RoleManagement: create操作でAuditLog確認
- [ ] ClientManagement: create操作でAuditLog確認

**確認項目**:
- [ ] `request_payload`に元のリクエストボディが記録されている
- [ ] `after`に作成されたエンティティ情報が記録されている
- [ ] `target_tenant_id`が正しい操作対象テナントIDになっている
- [ ] TenantManagementで`tenant_id`≠`target_tenant_id`を確認

**優先度**: ✅ CRITICAL
**工数**: 30分

---

## 📊 進捗管理

### 全体進捗

| Phase | タスク数 | 完了数 | 進捗率 | 優先度 |
|-------|---------|--------|--------|--------|
| Phase 1: 共通基盤 | 1 | 0 | 0% | 🔴 HIGH |
| Phase 2: Tenant | 5 | 0 | 0% | 🟡 HIGH |
| Phase 3: Role | 1 | 0 | 0% | 🟢 MEDIUM |
| Phase 4: Client | 2 | 0 | 0% | 🔵 MEDIUM |
| Phase 5: Permission | 2 | 0 | 0% | 🔵 MEDIUM |
| Phase 6: IdentityVerification | 2 | 0 | 0% | 🟣 LOW |
| Phase 7: SecurityEventHook | 2 | 0 | 0% | 🟣 LOW |
| Phase 8: Federation | 2 | 0 | 0% | 🟣 LOW |
| Phase 9: AuthenticationConfig | 2 | 0 | 0% | 🟣 LOW |
| Phase 10: AuthenticationPolicy | 2 | 0 | 0% | 🟣 LOW |
| Phase 11: 検証 | 2 | 0 | 0% | ✅ CRITICAL |
| **合計** | **23** | **0** | **0%** | - |

### API別実装状況

| API | requestPayload() | targetTenantId() | その他の問題 | ステータス |
|-----|-----------------|-----------------|-------------|-----------|
| **User** | ✅ 完了 | ✅ 完了 | なし | ✅ 完璧 |
| **Tenant** | ❌ 未実装 | ❌ 未実装 | payload()誤り、request未保持 | 🔴 要修正 |
| **Role** | ✅ 完了 | ❌ 未実装 | - | 🟢 要修正 |
| **Client** | ❓ 要確認 | ❌ 未実装 | 要調査 | 🔵 要調査 |
| **Permission** | ❓ 要確認 | ❌ 未実装 | 要調査 | 🔵 要調査 |
| **IdentityVerification** | ❓ 要確認 | ❌ 未実装 | 要調査 | 🟣 要調査 |
| **SecurityEventHook** | ❓ 要確認 | ❌ 未実装 | 要調査 | 🟣 要調査 |
| **Federation** | ❓ 要確認 | ❌ 未実装 | 要調査 | 🟣 要調査 |
| **AuthenticationConfig** | ❓ 要確認 | ❌ 未実装 | 要調査 | 🟣 要調査 |
| **AuthenticationPolicy** | ❓ 要確認 | ❌ 未実装 | 要調査 | 🟣 要調査 |

---

## 🎯 推奨作業順序

### ステップ1: クイックウィン（30分）
1. ✅ Task 1.1: AuditLogCreator修正（全API即座に改善）
2. ✅ Task 3.1: RoleRegistrationContext.targetTenantId()実装

### ステップ2: 最重要API完全修正（1時間）
3. ✅ Task 2.1-2.5: TenantManagement完全修正
4. ✅ Task 11.1: ビルド確認

### ステップ3: 中優先度API（2時間）
5. ⬜ Task 4.1-4.2: ClientManagement修正
6. ⬜ Task 5.1-5.2: PermissionManagement修正
7. ✅ Task 11.1: ビルド確認

### ステップ4: 低優先度API（3時間）
8. ⬜ Task 6.1-10.2: 残り5つのAPI修正
9. ✅ Task 11.1: ビルド確認

### ステップ5: 最終検証（30分）
10. ✅ Task 11.2: E2Eテスト検証

**合計工数見積**: 約6-7時間

---

## 🔍 チェックリスト（各API共通）

各APIの修正時に以下を確認:

- [ ] 元のリクエストオブジェクトをContextフィールドとして保持
- [ ] コンストラクタでリクエストオブジェクトを受け取る
- [ ] `type()`が適切なエンティティタイプを返す
- [ ] `payload()`が作成/更新されたエンティティ情報を返す
- [ ] `requestPayload()`が元のリクエストボディを返す
- [ ] `targetTenantId()`が正しい操作対象テナントIDを返す
- [ ] `isDryRun()`が正しくドライランフラグを返す
- [ ] ContextCreatorでリクエストオブジェクトをContextに渡す
- [ ] コンパイルエラーなし
- [ ] 既存テストが通る

---

## 📝 メモ

### 重要な設計判断

1. **targetTenantIdの意味**:
   - 自テナント内操作: `tenant.identifierValue()`（操作元と同じ）
   - クロステナント操作: `newTenant.identifierValue()`（新規作成されたテナント）

2. **payloadの意味**:
   - 作成操作: 新規作成されたエンティティ情報
   - 更新操作: 更新後のエンティティ情報

3. **requestPayloadの意味**:
   - 常に元のHTTPリクエストボディ全体

### UserManagementが完璧な理由

UserManagementは既に全メソッドが正しく実装されており、他のAPIの参考実装として使える:
- `requestPayload()`: `request.toMap()`
- `targetTenantId()`: `tenant.identifierValue()`
- `payload()`: `user.toMaskedValueMap()`
