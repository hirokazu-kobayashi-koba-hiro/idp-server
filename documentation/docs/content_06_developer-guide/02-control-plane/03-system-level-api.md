# システムレベルAPI実装ガイド

## このドキュメントの目的

**システムレベル管理API**を、ゼロから実装できるようになることが目標です。

### 所要時間
⏱️ **約45分**（実装 + テスト）

### 前提知識
- [01. アーキテクチャ概要](../01-getting-started/01-architecture-overview.md)
- [02. 最初のAPI実装](./03-system-level-api.md)

---

## 全体像

### システムレベルAPIとは

**テナント単位**で管理するAPI。システム管理者が使用。

```
GET /v1/management/tenants/{tenantId}/clients
POST /v1/management/tenants/{tenantId}/clients
```

**特徴**:
- ✅ テナント単位のリソース管理
- ✅ システム管理者権限が必要（`client:read`, `client:write`等）
- ✅ **全操作の監査ログ記録**（重要）
- ✅ Dry Run対応（変更前の検証）

**対比**: 組織レベルAPI = 組織単位で管理（`/organizations/{orgId}/tenants/{tenantId}/...`）

---

### アーキテクチャ全体像

```
HTTPリクエスト
    ↓
Controller (XxxManagementV1Api)
  - HTTP処理のみ
    ↓
EntryService (XxxManagementEntryService)
  - Handler呼び出し
  - Audit Log記録  ← 全操作必須
  - レスポンス返却
    ↓
Handler (XxxManagementHandler)
  - Tenant取得
  - 権限チェック
  - Service委譲
  - 例外処理
    ↓
Service (XxxCreationService, XxxUpdateService等)
  - Validation
  - ビジネスロジック
  - ContextBuilder更新
  - Repository呼び出し
    ↓
Repository
  - DB永続化
```

---

### 監査ログ（Audit Log）

**Control Plane APIの最重要機能の1つ**

#### なぜ必要か

- ✅ **セキュリティ**: 誰が、いつ、何をしたか追跡
- ✅ **コンプライアンス**: 監査要件への対応
- ✅ **トラブルシューティング**: 設定変更履歴の追跡
- ✅ **不正検知**: 異常な操作パターンの検出

#### 記録される情報

**AuditableContext**が提供：
- **操作者情報**: userId, externalUserId, ipAddress, userAgent
- **対象リソース**: targetResource, targetResourceAction
- **変更内容**: before（変更前）, after（変更後）
- **結果**: outcomeResult（success/failure）, outcomeReason
- **メタ情報**: dryRun, tenantId, clientId

#### 実装パターン

**EntryServiceで必ず実行**:
```java
// 1. Handler呼び出し
XxxManagementResult result = handler.handle(...);

// 2. Audit Log記録（成功・失敗問わず必須）
AuditLog auditLog = AuditLogCreator.create(result.context());
auditLogPublisher.publish(auditLog);

// 3. レスポンス返却
return result.toResponse(dryRun);
```

**重要**:
- ContextBuilderがHandlerで早期作成されるため、**エラー時も記録可能**
- before/after の変更履歴を自動記録

---

## 実装の全体フロー

**Handler-Serviceパターン**による3層実装：

```
1. API契約定義（Control Plane層）
   ├─ インターフェース定義（XxxManagementApi）
   ├─ Request/Response DTO（Map<String, Object>ベース）
   ├─ Handler実装（Tenant取得、権限チェック、Service委譲）
   ├─ Service実装（Validation、ビジネスロジック、永続化）
   ├─ Context Creator（リクエスト→ドメインモデル変換）
   └─ 権限定義（defaultメソッド）

2. EntryService実装（UseCase層）
   ├─ Handlerの初期化（Serviceマップ登録）
   ├─ Handlerに委譲
   ├─ Audit Log記録
   └─ レスポンス返却

3. Controller実装（Controller層）
   └─ HTTPエンドポイント（EntryService呼び出し）

4. E2Eテスト作成
   └─ API動作確認
```

**重要**: EntryServiceは複雑な処理を持たず、**Handlerに委譲するだけ**

---

## 各層の責務と主要クラス

### EntryService（UseCase層）

**責務**: トランザクション境界、Audit Log記録、レスポンス変換

**実装**: 3ステップのみ
1. Handler呼び出し
2. Audit Log記録
3. レスポンス返却

**クラス例**: `ClientManagementEntryService`, `UserManagementEntryService`

---

### Handler（Control Plane層）

**責務**: Tenant取得、権限チェック、Serviceオーケストレーション、例外処理

**実装**:
1. Service選択（メソッド名から適切なServiceを選択）
2. Context Builder作成
3. Tenant取得
4. 権限チェック（`ApiPermissionVerifier`）
5. Serviceに委譲
6. 例外ハンドリング

**クラス例**: `ClientManagementHandler`, `UserManagementHandler`

---

### Service（Control Plane層）

**責務**: 入力検証、ビジネスロジック、Context更新、永続化

**実装**:
1. Validation（Validator使用）
2. ビジネスロジック実行（ドメインモデル作成）
3. Context Builder更新（Before/After状態）
4. Dry Run判定
5. Repository呼び出し（永続化）
6. レスポンス作成

**クラス例**: `ClientCreationService`, `ClientUpdateService`, `ClientDeletionService`

---


## 実装手順

新しいシステムレベルAPIを実装する手順を説明します。

### Step 1: API契約定義（Control Plane層）

**作成するファイル**:
```
libs/idp-server-control-plane/src/main/java/org/idp/server/control_plane/management/{domain}/
├── {Domain}ManagementApi.java           # インターフェース
├── {Domain}ManagementContext.java       # 統一Context
├── {Domain}ManagementContextBuilder.java # ContextBuilder
├── io/
│   ├── {Domain}ManagementRequest.java    # Request DTO（Map<String, Object>ベース）
│   ├── {Domain}ManagementResponse.java   # Response DTO
│   └── {Domain}ManagementStatus.java     # Status列挙型
├── handler/
│   ├── {Domain}ManagementHandler.java         # Handler
│   ├── {Domain}CreationService.java           # create用Service
│   ├── {Domain}UpdateService.java             # update用Service
│   ├── {Domain}DeletionService.java           # delete用Service
│   ├── {Domain}FindService.java               # get用Service
│   └── {Domain}FindListService.java           # findList用Service
└── validator/
    └── {Domain}RegistrationRequestValidator.java # Validator
```

**実装の参考**:
- [ClientManagementApi.java](../../../../libs/idp-server-control-plane/src/main/java/org/idp/server/control_plane/management/oidc/client/ClientManagementApi.java)
- [ClientManagementHandler.java](../../../../libs/idp-server-control-plane/src/main/java/org/idp/server/control_plane/management/oidc/client/handler/ClientManagementHandler.java)
- [ClientCreationService.java](../../../../libs/idp-server-control-plane/src/main/java/org/idp/server/control_plane/management/oidc/client/handler/ClientCreationService.java)

---

### Step 2: EntryService実装（UseCase層）

**作成するファイル**:
```
libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/control_plane/system_manager/
└── {Domain}ManagementEntryService.java
```

**実装パターン**:
```java
@Transaction
public class XxxManagementEntryService implements XxxManagementApi {

  private final XxxManagementHandler handler;
  private final AuditLogPublisher auditLogPublisher;

  // コンストラクタ: Handlerを初期化（Serviceマップ登録）
  public XxxManagementEntryService(...) {
    Map<String, XxxManagementService<?>> services = new HashMap<>();
    services.put("create", new XxxCreationService(...));
    services.put("findList", new XxxFindListService(...));
    // ...

    this.handler = new XxxManagementHandler(services, this, tenantQueryRepository);
    this.auditLogPublisher = auditLogPublisher;
  }

  // 各メソッド: 3ステップパターン
  @Override
  public XxxManagementResponse create(...) {
    // 1. Handlerに委譲
    XxxManagementResult result = handler.handle("create", ...);

    // 2. Audit Log記録
    AuditLog auditLog = AuditLogCreator.create(result.context());
    auditLogPublisher.publish(auditLog);

    // 3. レスポンス返却
    return result.toResponse(dryRun);
  }
}
```

**実装の参考**:
- [ClientManagementEntryService.java](../../../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/control_plane/system_manager/ClientManagementEntryService.java)

---

### Step 3: IdpServerApplication登録

**ファイル**: `libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/IdpServerApplication.java`

**実装パターン**:
```java
// フィールド追加
XxxManagementApi xxxManagementApi;

// コンストラクタ内で初期化
this.xxxManagementApi =
    TenantAwareEntryServiceProxy.createProxy(
        new XxxManagementEntryService(...),
        XxxManagementApi.class,
        databaseTypeProvider);

// Getter追加
public XxxManagementApi xxxManagementApi() {
  return xxxManagementApi;
}
```

**Proxy選択**:
- System-level: `TenantAwareEntryServiceProxy`
- Organization-level: `ManagementTypeEntryServiceProxy`

---

### Step 4: Controller実装（Controller層）

**作成するファイル**:
```
libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/management/{domain}/
└── {Domain}ManagementV1Api.java
```

**実装パターン**:
```java
@RestController
@RequestMapping("/v1/management/tenants/{tenant-id}/{resources}")
public class XxxManagementV1Api {

  private final XxxManagementApi xxxManagementApi;

  @PostMapping
  public ResponseEntity<?> post(
      @AuthenticationPrincipal OperatorPrincipal operatorPrincipal,
      @PathVariable("tenant-id") String tenantId,
      @RequestBody Map<String, Object> requestBody) {

    XxxManagementRequest request = new XxxManagementRequest(requestBody);
    XxxManagementResponse response =
        xxxManagementApi.create(
            new TenantIdentifier(tenantId),
            operatorPrincipal.operator(),
            operatorPrincipal.oAuthToken(),
            request,
            requestAttributes,
            dryRun);

    return ResponseEntity.status(response.statusCode()).body(response.contents());
  }
}
```

**実装の参考**:
- [ClientManagementV1Api.java](../../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/management/client/ClientManagementV1Api.java)

---

### Step 5: E2Eテスト作成

**作成するファイル**:
```
e2e/src/tests/management/{domain}/
└── {domain}-management.test.js
```

**実装の参考**:
- `e2e/src/tests/management/client/`配下のテスト

---
## チェックリスト

システムレベルAPI実装前に以下を確認：

### API契約定義（Control Plane層）
- [ ] インターフェース定義（`{Domain}ManagementApi`）
- [ ] `defaultメソッド`で権限定義（実装不要）
- [ ] Request DTO作成（`Map<String, Object>`ベース、型安全なヘルパーメソッド）
- [ ] Response DTO作成
- [ ] Context Creator作成（リクエスト → ドメインモデル変換）
- [ ] Context作成（`toResponse()`メソッド実装）

### EntryService実装（UseCase層）
- [ ] `@Transaction`アノテーション付与
- [ ] 読み取り専用なら`@Transaction(readOnly = true)`
- [ ] Context Creator使用
- [ ] 権限チェック実装
- [ ] Audit Log記録（`AuditLogCreator.create()`）
- [ ] Dry Run対応（書き込み操作のみ）

### IdpServerApplication登録
- [ ] フィールド追加
- [ ] **`TenantAwareEntryServiceProxy`使用**（第一引数が`TenantIdentifier`）
- [ ] Getterメソッド追加

### Controller実装（Controller層）
- [ ] HTTPエンドポイント定義
- [ ] 型変換のみ（ロジック禁止）
- [ ] `@PathVariable`, `@RequestParam`適切使用

### E2Eテスト
- [ ] 正常系テスト（CREATE/READ/UPDATE/DELETE）
- [ ] Dry Runテスト
- [ ] 権限エラーテスト（403）

---

## よくあるエラー

### エラー1: `defaultメソッド`を実装してしまう

```java
// ❌ 間違い: defaultメソッドをオーバーライド
@Override
public AdminPermissions getRequiredPermissions(String method) {
    // 不要な実装
}

// ✅ 正しい: defaultメソッドはそのまま使用（実装不要）
public class RoleManagementEntryService implements RoleManagementApi {
    // getRequiredPermissions()は実装不要！
}
```

### エラー2: Context Creator未使用

```java
// ❌ 間違い: EntryServiceでDTO直接変換
Role role = new Role(
    new RoleIdentifier(request.getRoleId()),
    // ... 直接変換
);

// ✅ 正しい: Context Creator使用
RoleManagementContextBuilder creator =
    new RoleManagementContextBuilder(tenant, request, dryRun);
RoleManagementContext context = creator.create();
```

---

## 次のステップ

✅ システムレベルAPI実装をマスターした！

### 📖 次に読むべきドキュメント

1. [組織レベルAPI実装ガイド](./04-organization-level-api.md) - より複雑なアクセス制御
2. [Repository実装ガイド](../04-implementation-guides/impl-10-repository-implementation.md) - データアクセス層の実装

### 🔗 詳細情報

- [AI開発者向け: Control Plane詳細](../../content_10_ai_developer/ai-13-control-plane.md)
- [AI開発者向け: Use-Cases詳細](../../content_10_ai_developer/ai-10-use-cases.md)

---

**情報源**: [ClientManagementEntryService.java](../../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/control_plane/system_manager/ClientManagementEntryService.java)
**最終更新**: 2025-10-12
