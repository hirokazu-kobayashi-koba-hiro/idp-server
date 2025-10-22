# 全管理API - AuditLog実装状況マッピング

## 概要
全管理APIのConfigRegistrationContext実装状況と、AuditLog記録における問題点の一覧

**確認日**: 2025-10-22

---

## 1. ConfigRegistrationContext実装状況

### 実装必須メソッド（Issue #529で追加）

| メソッド | 説明 | 用途 |
|---------|------|------|
| `String type()` | エンティティタイプ | description フィールド |
| `Map<String, Object> payload()` | 作成/更新されたエンティティ情報 | after フィールド |
| `Map<String, Object> requestPayload()` | 元のリクエストボディ | request_payload フィールド |
| `String targetTenantId()` | 操作対象テナントID | target_tenant_id フィールド |
| `boolean isDryRun()` | ドライランフラグ | dry_run フィールド |

---

## 2. 各Context実装状況

### 2.1 UserRegistrationContext ✅ **完全実装済み**

**ファイル**: `org.idp.server.control_plane.management.identity.user.UserRegistrationContext`

| メソッド | 実装状況 | 実装内容 |
|---------|---------|---------|
| `type()` | ✅ 実装済み | `"user"` |
| `payload()` | ✅ 実装済み | `user.toMaskedValueMap()` |
| `requestPayload()` | ✅ 実装済み | `request.toMap()` |
| `targetTenantId()` | ✅ 実装済み | `tenant.identifierValue()` |
| `isDryRun()` | ✅ 実装済み | `dryRun` |

**特徴**:
- ユーザー作成は操作元テナント内での操作
- `targetTenantId` = `tenant`（操作元と操作対象が同じ）

---

### 2.2 TenantManagementRegistrationContext ⚠️ **部分実装・問題あり**

**ファイル**: `org.idp.server.control_plane.management.tenant.TenantManagementRegistrationContext`

| メソッド | 実装状況 | 実装内容 | 問題点 |
|---------|---------|---------|--------|
| `type()` | ✅ 実装済み | `"tenant"` | - |
| `payload()` | ❌ **問題あり** | `authorizationServerConfiguration.toMap()` | ❌ テナント情報ではなく認可サーバー設定のみ |
| `requestPayload()` | ❌ **問題あり** | `payload()` (TODO) | ❌ 元のリクエストを返していない |
| `targetTenantId()` | ❌ **未実装** | - | ❌ メソッドが存在しない |
| `isDryRun()` | ✅ 実装済み | `dryRun` | - |

**問題の詳細**:
1. **Contextが`TenantRequest`を保持していない** → `requestPayload()`実装不可
2. **`payload()`が間違ったデータを返す** → 新規テナント情報が記録されない
3. **`targetTenantId()`未実装** → 操作対象（新規テナント）が記録されない

**必要な修正**:
```java
// フィールド追加
TenantRequest request;

// コンストラクタにrequest追加

// メソッド修正
@Override
public Map<String, Object> payload() {
  return newTenant.toMap();  // ← 修正
}

@Override
public Map<String, Object> requestPayload() {
  return request.toMap();  // ← 修正
}

@Override
public String targetTenantId() {
  return newTenant.identifierValue();  // ← 追加
}
```

**特徴**:
- テナント作成は新規テナントの作成（操作元 ≠ 操作対象）
- `targetTenantId` = `newTenant`（新規作成されたテナント）
- `tenant_id` = `adminTenant`（操作元）

---

### 2.3 RoleRegistrationContext ❌ **未実装多数**

**ファイル**: `org.idp.server.control_plane.management.role.RoleRegistrationContext`

| メソッド | 実装状況 | 実装内容 | 問題点 |
|---------|---------|---------|--------|
| `type()` | ✅ 実装済み | `"role"` | - |
| `payload()` | ✅ 実装済み | `role.toMap()` | - |
| `requestPayload()` | ✅ 実装済み | `request.toMap()` | - |
| `targetTenantId()` | ❌ **未実装** | - | ❌ メソッドが存在しない |
| `isDryRun()` | ✅ 実装済み | `dryRun` | - |

**必要な修正**:
```java
@Override
public String targetTenantId() {
  return tenant.identifierValue();  // ← 追加（操作元と同じ）
}
```

---

### 2.4 ClientRegistrationContext ❌ **未実装多数**

**ファイル**: `org.idp.server.control_plane.management.oidc.client.ClientRegistrationContext`

| メソッド | 実装状況 | 推測される実装内容 | 問題点 |
|---------|---------|-----------------|--------|
| `type()` | ? | `"client"` | 要確認 |
| `payload()` | ? | `client.toMap()`? | 要確認 |
| `requestPayload()` | ❌ **未実装** | - | ❌ 実装必要 |
| `targetTenantId()` | ❌ **未実装** | - | ❌ 実装必要 |
| `isDryRun()` | ? | - | 要確認 |

**必要な修正**:
```java
// 要実装調査後に決定
```

---

### 2.5 PermissionRegistrationContext ❌ **未実装多数**

**ファイル**: `org.idp.server.control_plane.management.permission.PermissionRegistrationContext`

| メソッド | 実装状況 | 推測される実装内容 | 問題点 |
|---------|---------|-----------------|--------|
| `type()` | ? | `"permission"` | 要確認 |
| `payload()` | ? | `permission.toMap()`? | 要確認 |
| `requestPayload()` | ❌ **未実装** | - | ❌ 実装必要 |
| `targetTenantId()` | ❌ **未実装** | - | ❌ 実装必要 |
| `isDryRun()` | ? | - | 要確認 |

---

### 2.6 その他の Context（未確認）

以下のContextも存在するが、詳細実装は未確認:

1. **IdentityVerificationConfigRegistrationContext**
2. **SecurityEventHookConfigRegistrationContext**
3. **FederationConfigRegistrationContext**
4. **AuthenticationConfigRegistrationContext**
5. **AuthenticationPolicyConfigRegistrationContext**

---

## 3. AuditLogCreator.create()の問題点

### 現在の実装（Line 35-85）

```java
public static AuditLog create(
    String type,
    Tenant tenant,  // ← 常に操作元テナント
    User user,
    OAuthToken oAuthToken,
    ConfigRegistrationContext context,
    RequestAttributes requestAttributes) {

  // ...
  String tenantId = tenant.identifier().value();  // 操作元テナント
  JsonNodeWrapper request = JsonNodeWrapper.fromMap(context.requestPayload());
  JsonNodeWrapper after = JsonNodeWrapper.fromMap(context.payload());
  String targetTenantId = tenantId;  // ❌ 問題: 操作元と同じ値
  // ...
}
```

### 🚨 重大な問題

**Line 59:**
```java
String targetTenantId = tenantId;  // ❌ 間違い
```

**影響**:
- `tenant_id`と`target_tenant_id`が常に同じ値になる
- TenantManagement（新規テナント作成）で、操作対象が記録されない
- マルチテナント環境で「誰が誰に対して操作したか」が不明確

**修正が必要**:
```java
String targetTenantId = context.targetTenantId();  // ✅ 正しい
```

---

## 4. 引数`Tenant`の正体

### 確認結果

**結論**: 引数の`Tenant`は**常に操作元テナント**

#### 証拠1: TenantCreationService.execute()（Line 103, 113）
```java
return TenantManagementResult.success(adminTenant, context, context.toResponse());
```
→ `adminTenant`を渡している

#### 証拠2: UserCreationService.execute()（Line 98, 113）
```java
return UserManagementResult.success(tenant, context, context.toResponse());
```
→ `tenant`（操作元）を渡している

#### 証拠3: EntryService呼び出し（UserManagementEntryService Line 194）
```java
AuditLogCreator.create(
    "UserManagementApi.create",
    result.tenant(),  // ← これは操作元テナント
    operator,
    oAuthToken,
    (UserRegistrationContext) result.context(),
    requestAttributes);
```

### 意味

| フィールド | マッピング元 | 意味 |
|-----------|------------|------|
| `tenant_id` | `tenant.identifier().value()` | **操作元テナント**（誰が操作したか） |
| `target_tenant_id` | `context.targetTenantId()` | **操作対象テナント**（誰に対して操作したか） |

### API別の`target_tenant_id`の意味

| API | 操作内容 | tenant_id（操作元） | target_tenant_id（操作対象） | 関係 |
|-----|---------|-------------------|--------------------------|------|
| **UserManagement** | ユーザー作成 | テナントA | テナントA | 同じ（自テナント内操作） |
| **TenantManagement** | テナント作成 | adminテナント | 新規テナント | 異なる（クロステナント操作） |
| **RoleManagement** | ロール作成 | テナントA | テナントA | 同じ（自テナント内操作） |
| **ClientManagement** | クライアント作成 | テナントA | テナントA | 同じ（自テナント内操作） |

---

## 5. 修正優先度

### 🔴 HIGH - 即修正必要

1. **AuditLogCreator.create() Line 59**
   ```java
   // 修正前
   String targetTenantId = tenantId;

   // 修正後
   String targetTenantId = context.targetTenantId();
   ```

2. **TenantManagementRegistrationContext**
   - `TenantRequest`フィールド追加
   - `payload()` → `newTenant.toMap()`に修正
   - `requestPayload()` → `request.toMap()`に修正
   - `targetTenantId()` → `newTenant.identifierValue()`を実装

### 🟡 MEDIUM - 順次修正

3. **RoleRegistrationContext**
   - `targetTenantId()` → `tenant.identifierValue()`を実装

4. **ClientRegistrationContext**
   - 実装状況確認後、不足メソッドを実装

5. **PermissionRegistrationContext**
   - 実装状況確認後、不足メソッドを実装

### 🟢 LOW - 確認後対応

6. **その他5つのContext**
   - IdentityVerificationConfig
   - SecurityEventHookConfig
   - FederationConfig
   - AuthenticationConfig
   - AuthenticationPolicyConfig

---

## 6. 実装パターンの違い

### パターンA: 自テナント内操作（User, Role, Client, Permission等）

```java
@Override
public String targetTenantId() {
  return tenant.identifierValue();  // 操作元と同じ
}
```

**特徴**:
- 操作元 = 操作対象
- `tenant_id` = `target_tenant_id`（通常は同じ値）

### パターンB: クロステナント操作（Tenant作成）

```java
@Override
public String targetTenantId() {
  return newTenant.identifierValue();  // 新規作成されたテナント
}
```

**特徴**:
- 操作元 ≠ 操作対象
- `tenant_id`（adminTenant） ≠ `target_tenant_id`（newTenant）

---

## 7. まとめ

### 現状の問題

1. **AuditLogCreator.create()** が`target_tenant_id`を正しく設定していない
2. **TenantManagementRegistrationContext** の実装が不完全
3. **他の8つのContext** で`targetTenantId()`が未実装

### 影響

- マルチテナント環境での監査証跡が不完全
- 「誰が誰に対して何をしたか」が正確に記録されない
- GDPR/SOX等のコンプライアンス要件を満たせない可能性

### 次のステップ

1. AuditLogCreator.create()の修正（最優先）
2. TenantManagementRegistrationContextの完全実装
3. 他のContext実装の順次対応
4. E2Eテストでの検証

---

## 付録: 確認コマンド

```bash
# 全RegistrationContextファイルの一覧
find libs/idp-server-control-plane/src/main/java -name "*RegistrationContext.java" -type f

# targetTenantId()実装確認
for file in libs/idp-server-control-plane/src/main/java/org/idp/server/control_plane/management/*/*RegistrationContext.java; do
  echo "=== $(basename "$file") ==="
  grep -A 3 "public String targetTenantId()" "$file" || echo "NOT FOUND"
done

# requestPayload()実装確認
for file in libs/idp-server-control-plane/src/main/java/org/idp/server/control_plane/management/*/*RegistrationContext.java; do
  echo "=== $(basename "$file") ==="
  grep -A 3 "public Map<String, Object> requestPayload()" "$file" || echo "NOT FOUND"
done
```
