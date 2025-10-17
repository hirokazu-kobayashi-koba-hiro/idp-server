# idp-server-use-cases - ユースケース層（EntryService実装）

## モジュール概要

**情報源**: `libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/`
**確認日**: 2025-10-12

### 責務

アプリケーション層のオーケストレーション。Control Plane APIの契約を実装し、Core層のHandlerを呼び出す。

- **EntryService実装**: `{'{Domain}{Action}EntryService'}` パターン
- **トランザクション境界**: `@Transaction` によるトランザクション管理
- **認可チェック**: 権限検証・アクセス制御
- **Audit Log記録**: 全操作の監査ログ出力
- **Dry Run対応**: 検証のみの実行モード

### 依存関係

```
idp-server-use-cases
  ↓ (依存)
idp-server-core (ドメインロジック)
idp-server-platform (基盤機能)
idp-server-control-plane (API契約定義)
```

## パッケージ構成

**情報源**: `find libs/idp-server-use-cases/src/main/java/org/idp/server/usecases -type d -maxdepth 2`

### 🔷 Application層 (`application/`)

エンドユーザー・RP（Relying Party）・システム向けのアプリケーションロジック。

| サブパッケージ | 責務 |
|------------|------|
| `relying_party/` | OAuth/OIDC認証・認可フロー |
| `enduser/` | エンドユーザー向けAPI |
| `identity_verification_service/` | 身元確認サービス |
| `system/` | システム内部API |
| `tenant_invitator/` | テナント招待処理 |

**特徴**: Control Plane層と異なり、**シンプルな委譲パターン**を採用。

- ❌ 権限チェックなし（公開API or トークン検証済み前提）
- ❌ Audit Logなし（必要な場合はCore層で記録）
- ❌ Dry Runなし
- ❌ Context Creatorなし
- ✅ Core層のProtocol/Interactorへの委譲
- ✅ Transaction管理のみ

**詳細**: [Application層 EntryServiceパターン](#application層-entryservice-パターン)

### 🎛️ Control Plane層 (`control_plane/`)

管理API実装。システム管理者・組織管理者向け。

| サブパッケージ | 責務 |
|------------|------|
| `system_manager/` | システムレベル管理API |
| `system_administrator/` | システム管理者API |
| `organization_manager/` | 組織レベル管理API |

## EntryService パターン

### 命名規則

```
{Domain}{Action}EntryService
```

**例**:
- `ClientManagementEntryService` - クライアント管理
- `UserManagementEntryService` - ユーザー管理
- `AuthorizationServerManagementEntryService` - 認可サーバー管理
- `TenantManagementEntryService` - テナント管理

### 実装パターン

**情報源**: [ClientManagementEntryService.java:47](../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/control_plane/system_manager/ClientManagementEntryService.java#L47)

```java
/**
 * EntryServiceの標準実装パターン
 * 確認方法: 実ファイルの47-114行目
 */
@Transaction // ✅ トランザクション境界
public class ClientManagementEntryService implements ClientManagementApi {

  // ✅ Repository依存性注入
  TenantQueryRepository tenantQueryRepository;
  ClientConfigurationCommandRepository clientConfigurationCommandRepository;
  ClientConfigurationQueryRepository clientConfigurationQueryRepository;
  AuditLogPublisher auditLogPublisher;
  LoggerWrapper log = LoggerWrapper.getLogger(ClientManagementEntryService.class);

  // ✅ コンストラクタインジェクション
  public ClientManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      ClientConfigurationCommandRepository clientConfigurationCommandRepository,
      ClientConfigurationQueryRepository clientConfigurationQueryRepository,
      AuditLogPublisher auditLogPublisher) {
    this.tenantQueryRepository = tenantQueryRepository;
    this.clientConfigurationCommandRepository = clientConfigurationCommandRepository;
    this.clientConfigurationQueryRepository = clientConfigurationQueryRepository;
    this.auditLogPublisher = auditLogPublisher;
  }

  @Override
  public ClientManagementResponse create(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      ClientRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    // ✅ Phase 1: 権限取得
    AdminPermissions permissions = getRequiredPermissions("create");

    // ✅ Phase 2: Tenant取得
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    // ✅ Phase 3: Validator - 入力検証
    ClientRegistrationRequestValidator validator =
        new ClientRegistrationRequestValidator(request, dryRun);
    ClientRegistrationRequestValidationResult validate = validator.validate();

    // ✅ Phase 4: Context Creator - コンテキスト構築
    ClientRegistrationContextCreator contextCreator =
        new ClientRegistrationContextCreator(tenant, request, dryRun);
    ClientRegistrationContext context = contextCreator.create();

    // ✅ Phase 5: Audit Log記録
    AuditLog auditLog =
        AuditLogCreator.create(
            "ClientManagementApi.create",
            tenant,
            operator,
            oAuthToken,
            context,
            requestAttributes);
    auditLogPublisher.publish(auditLog);

    // ✅ Phase 6: 権限チェック
    if (!permissions.includesAll(operator.permissionsAsSet())) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put(
          "error_description",
          String.format(
              "permission denied required permission %s, but %s",
              permissions.valuesAsString(), operator.permissionsAsString()));
      log.warn(response.toString());
      return new ClientManagementResponse(ClientManagementStatus.FORBIDDEN, response);
    }

    // ✅ Phase 7: バリデーションエラーチェック
    if (!validate.isValid()) {
      return validate.errorResponse();
    }

    // ✅ Phase 8: Dry Run対応
    if (dryRun) {
      return context.toResponse();
    }

    // ✅ Phase 9: 永続化
    clientConfigurationCommandRepository.register(tenant, context.configuration());

    // ✅ Phase 10: レスポンス返却
    return context.toResponse();
  }
}
```

## EntryServiceの10フェーズ

### Phase 1: 権限取得

```java
AdminPermissions permissions = getRequiredPermissions("create");
```

**重要**: Control Plane APIインターフェースの`default`メソッドで自動計算されるため、通常は実装不要。

### Phase 2: Tenant取得

```java
Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
```

**原則**: システムレベルAPIは必ずTenantを最初に取得。

### Phase 3: Validator - 入力検証

```java
ClientRegistrationRequestValidator validator =
    new ClientRegistrationRequestValidator(request, dryRun);
ClientRegistrationRequestValidationResult validate = validator.validate();
```

**責務**: リクエストパラメータの形式・必須チェック。

### Phase 4: Context Creator - コンテキスト構築

```java
ClientRegistrationContextCreator contextCreator =
    new ClientRegistrationContextCreator(tenant, request, dryRun);
ClientRegistrationContext context = contextCreator.create();
```

**重要**: Context Creatorは**絶対必須**。TODOコメントで済ませない。

### Phase 5: Audit Log記録

```java
AuditLog auditLog =
    AuditLogCreator.create(
        "ClientManagementApi.create",
        tenant,
        operator,
        oAuthToken,
        context,
        requestAttributes);
auditLogPublisher.publish(auditLog);
```

**原則**: 全操作の監査ログを記録（create/update/delete別）。

### Phase 6: 権限チェック

```java
if (!permissions.includesAll(operator.permissionsAsSet())) {
  return new ClientManagementResponse(ClientManagementStatus.FORBIDDEN, response);
}
```

**ステータスコード**: `403 Forbidden`

### Phase 7: バリデーションエラーチェック

```java
if (!validate.isValid()) {
  return validate.errorResponse();
}
```

**ステータスコード**: `400 Bad Request`

### Phase 8: Dry Run対応

```java
if (dryRun) {
  return context.toResponse();
}
```

**目的**: 実行せずに検証結果のみ返却。

### Phase 9: 永続化

```java
clientConfigurationCommandRepository.register(tenant, context.configuration());
```

**原則**: Tenant第一引数必須。

### Phase 10: レスポンス返却

```java
return context.toResponse();
```

## Application層 EntryService パターン

**情報源**: [OAuthFlowEntryService.java](../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/application/enduser/OAuthFlowEntryService.java), [OidcMetaDataEntryService.java](../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/application/relying_party/OidcMetaDataEntryService.java)

### 設計思想

Application層は**エンドユーザー・RP（Relying Party）向けAPI**であり、Control Plane層とは異なる設計原則を採用。

**Control Plane層との違い**:
- **対象**: 管理者 → エンドユーザー/RP
- **複雑度**: 10フェーズ → 3-4フェーズ
- **責務**: 検証・認可・Context構築・永続化 → Protocol委譲・Transaction管理
- **依存**: Repository直接 → Protocol/Interactor経由

### Application層の特徴

#### ✅ 採用する機能
- **Transaction管理**: `@Transaction` / `@Transaction(readOnly = true)`
- **Tenant取得**: 全操作の最初にTenant取得
- **Protocol委譲**: Core層の`Protocol`/`Interactor`にビジネスロジック委譲

#### ❌ 採用しない機能
- **権限チェック**: Controller層で完了済み or 公開API
- **Audit Log**: Core層でイベント駆動記録
- **Dry Run**: 管理操作ではないため不要
- **Context Creator**: Protocolが内部で処理
- **Validator**: Protocol内で実装

### 実装パターン1: OAuth認可フロー

**情報源**: [OAuthFlowEntryService.java:117-138](../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/application/enduser/OAuthFlowEntryService.java#L117-L138)

```java
@Transaction
public class OAuthFlowEntryService implements OAuthFlowApi {

  OAuthProtocols oAuthProtocols;
  TenantQueryRepository tenantQueryRepository;
  AuthenticationTransactionCommandRepository authenticationTransactionCommandRepository;
  // ...

  @Override
  public OAuthRequestResponse request(
      TenantIdentifier tenantIdentifier,
      Map<String, String[]> params,
      RequestAttributes requestAttributes) {

    // ✅ フェーズ 1: Tenant取得
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    // ✅ フェーズ 2: Request構築
    OAuthRequest oAuthRequest = new OAuthRequest(tenant, params);

    // ✅ フェーズ 3: Core層Protocol取得・実行
    OAuthProtocol oAuthProtocol = oAuthProtocols.get(tenant.authorizationProvider());
    OAuthRequestResponse response = oAuthProtocol.request(oAuthRequest);

    // ✅ フェーズ 4: Transaction管理（成功時のみ）
    if (response.isOK()) {
      AuthenticationPolicyConfiguration policyConfig =
          authenticationPolicyConfigurationQueryRepository.find(tenant, StandardAuthFlow.OAUTH.toAuthFlow());
      AuthenticationTransaction transaction =
          OAuthAuthenticationTransactionCreator.create(tenant, response, policyConfig);
      authenticationTransactionCommandRepository.register(tenant, transaction);
    }

    return response;
  }
}
```

**重要ポイント**:
- **Protocol中心**: `OAuthProtocol`がOAuth 2.0仕様準拠の処理を実装
- **Transaction管理**: 認証トランザクションの永続化のみ担当
- **エラーハンドリング**: Protocolがエラーレスポンス構築

### 実装パターン2: メタデータAPI（読み取り専用）

**情報源**: [OidcMetaDataEntryService.java:29-56](../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/application/relying_party/OidcMetaDataEntryService.java#L29-L56)

```java
@Transaction(readOnly = true)
public class OidcMetaDataEntryService implements OidcMetaDataApi {

  TenantQueryRepository tenantQueryRepository;
  DiscoveryProtocols discoveryProtocols;

  public OidcMetaDataEntryService(
      TenantQueryRepository tenantQueryRepository,
      DiscoveryProtocols discoveryProtocols) {
    this.tenantQueryRepository = tenantQueryRepository;
    this.discoveryProtocols = discoveryProtocols;
  }

  @Override
  public ServerConfigurationRequestResponse getConfiguration(
      TenantIdentifier tenantIdentifier) {

    // ✅ フェーズ 1: Tenant取得
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    // ✅ フェーズ 2: Core層Protocol取得
    DiscoveryProtocol protocol = discoveryProtocols.get(tenant.authorizationProvider());

    // ✅ フェーズ 3: 委譲実行
    return protocol.getConfiguration(tenant);
  }

  @Override
  public JwksRequestResponse getJwks(TenantIdentifier tenantIdentifier) {
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    DiscoveryProtocol protocol = discoveryProtocols.get(tenant.authorizationProvider());
    return protocol.getJwks(tenant);
  }
}
```

**重要ポイント**:
- **読み取り専用**: `@Transaction(readOnly = true)`でパフォーマンス最適化
- **公開API**: 認証不要のOIDC Discovery仕様準拠
- **単純委譲**: ビジネスロジックなし、Protocol呼び出しのみ

### Application層の3-4フェーズ

#### 標準フロー（読み書き）
1. **Tenant取得**: `tenantQueryRepository.get(tenantIdentifier)`
2. **Request構築**: ドメインオブジェクト生成
3. **Protocol実行**: Core層への委譲
4. **Transaction管理**: 成功時のみ永続化

#### 簡易フロー（読み取り専用）
1. **Tenant取得**: `tenantQueryRepository.get(tenantIdentifier)`
2. **Protocol取得**: `protocols.get(tenant.authorizationProvider())`
3. **委譲実行**: `protocol.method(tenant)`

### Application層 vs Control Plane層 比較

| 項目 | Application層 | Control Plane層 |
|------|--------------|----------------|
| **対象ユーザー** | エンドユーザー/RP | 管理者（システム/組織） |
| **フェーズ数** | 3-4フェーズ | 10フェーズ |
| **権限チェック** | ❌ なし | ✅ AdminPermissions |
| **Audit Log** | ❌ なし | ✅ 全操作記録 |
| **Dry Run** | ❌ なし | ✅ 必須 |
| **Context Creator** | ❌ なし | ✅ 必須 |
| **Validator** | △ Protocol内 | ✅ 専用Validator |
| **依存層** | Protocol/Interactor | Repository |
| **トランザクション** | `@Transaction` | `@Transaction` |
| **設計思想** | OIDC仕様準拠処理 | 管理操作の完全制御 |

### Application層で避けるべきアンチパターン

#### ❌ 1. Repository直接操作

```java
// ❌ 悪い例: Repositoryを直接操作
public OAuthRequestResponse request(...) {
  Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
  ClientConfiguration client = clientConfigurationQueryRepository.get(tenant, clientId);
  // ビジネスロジックをEntryServiceに実装
  if (client.isConfidential()) {
    // ...
  }
}

// ✅ 良い例: Protocolに委譲
public OAuthRequestResponse request(...) {
  Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
  OAuthRequest oAuthRequest = new OAuthRequest(tenant, params);
  OAuthProtocol protocol = oAuthProtocols.get(tenant.authorizationProvider());
  return protocol.request(oAuthRequest); // Protocol内で完結
}
```

#### ❌ 2. 管理API機能の混入

```java
// ❌ 悪い例: 権限チェック・Audit Logを追加
public OAuthRequestResponse request(...) {
  // Application層に権限チェックは不要
  if (!operator.hasPermission("OAUTH_REQUEST")) {
    return error();
  }

  // Application層にAudit Logは不要
  auditLogPublisher.publish(auditLog);

  // ...
}

// ✅ 良い例: シンプルな委譲のみ
public OAuthRequestResponse request(...) {
  Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
  OAuthProtocol protocol = oAuthProtocols.get(tenant.authorizationProvider());
  return protocol.request(new OAuthRequest(tenant, params));
}
```

### Application層 EntryService一覧

**情報源**: `find libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/application -name "*EntryService.java"`

#### enduser（エンドユーザー向け）
- `OAuthFlowEntryService` - OAuth/OIDC認証・認可フロー
- `TokenEntryService` - トークンエンドポイント
- `UserinfoEntryService` - UserInfo API
- `CibaFlowEntryService` - CIBA（Client Initiated Backchannel Authentication）
- `IdentityVerificationApplicationEntryService` - 身元確認申込み
- `UserOperationEntryService` - ユーザー操作API
- `AuthenticationTransactionEntryService` - 認証トランザクション管理
- `AuthenticationMetaDataEntryService` - 認証メタデータ

#### relying_party（RP向け）
- `OidcMetaDataEntryService` - OIDC Discovery（.well-known）
- `SharedSignalsFrameworkMetaDataEntryService` - SSF Discovery

#### system（システム内部）
- `UserAuthenticationEntryService` - ユーザー認証処理
- `OrganizationUserAuthenticationEntryService` - 組織ユーザー認証
- `SecurityEventEntryService` - セキュリティイベント処理
- `UserLifecycleEventEntryService` - ユーザーライフサイクルイベント
- `AuditLogEntryService` - 監査ログ処理
- `TenantMetaDataEntryService` - テナントメタデータ

#### identity_verification_service（身元確認サービス）
- `IdentityVerificationEntryService` - 身元確認実行
- `IdentityVerificationCallbackEntryService` - 身元確認コールバック

#### tenant_invitator（テナント招待）
- `TenantInvitationMetaDataEntryService` - テナント招待メタデータ

## Control Plane層 EntryService パターン

**注意**: 以下は**Control Plane層（管理API）**の実装パターンです。Application層とは異なります。

## システムレベル vs 組織レベルAPI

### システムレベルAPI

**メソッドシグネチャ**:
```java
public Response method(
    TenantIdentifier tenantIdentifier,
    User operator,
    OAuthToken oAuthToken,
    Request request,
    RequestAttributes requestAttributes,
    boolean dryRun)
```

**特徴**:
- テナント単位の操作
- `tenantIdentifier`が第一引数

### 組織レベルAPI

**メソッドシグネチャ**:
```java
public Response method(
    OrganizationIdentifier organizationIdentifier,
    TenantIdentifier tenantIdentifier,
    User operator,
    OAuthToken oAuthToken,
    Request request,
    RequestAttributes requestAttributes,
    boolean dryRun)
```

**特徴**:
- 組織単位の操作（複数テナントを管理）
- `organizationIdentifier` → `tenantIdentifier`の順
- **追加検証**: 組織メンバーシップ・組織-テナント関係・権限検証

**情報源**: CLAUDE.md「組織レベルAPI設計」

### 組織アクセス制御フロー

**情報源**: [OrgUserManagementEntryService.java:150-187](../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/control_plane/organization_manager/OrgUserManagementEntryService.java#L150-L187)

```java
// ✅ 実際の実装パターン

// Phase 1: 権限取得
AdminPermissions permissions = getRequiredPermissions("create");

// Phase 2: Organization と Tenant 取得
Organization organization = organizationRepository.get(organizationIdentifier);
Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);

// Phase 3: OrganizationAccessVerifier で統合検証
OrganizationAccessVerifier organizationAccessVerifier = new OrganizationAccessVerifier();
OrganizationAccessControlResult accessResult =
    organizationAccessVerifier.verifyAccess(
        organization,
        tenantIdentifier,
        operator,
        permissions);

// Phase 4: アクセス制御結果チェック
if (!accessResult.isSuccess()) {
  Map<String, Object> response = new HashMap<>();
  response.put("error", "access_denied");
  response.put("error_description", accessResult.getReason());
  return new UserManagementResponse(UserManagementStatus.FORBIDDEN, response);
}

// Phase 5: 以降のビジネスロジック実行
```

**OrganizationAccessVerifier の検証内容**:
1. 組織メンバーシップ検証（operatorが組織メンバーか）
2. テナントアクセス検証（tenantが組織に割り当てられているか）
3. 組織-テナント関係検証（organization.assignedTenants()からチェック）
4. 権限検証（必要な権限を持っているか）

**情報源**:
- CLAUDE.md「組織アクセス制御フロー」
- [OrganizationAccessVerifier.java](../../libs/idp-server-control-plane/src/main/java/org/idp/server/control_plane/organization/access/OrganizationAccessVerifier.java)

## Context Creator パターン

### 役割

リクエストをドメインオブジェクトに変換し、Core層で使用可能なContextを構築。

### 実装例

```java
public class ClientRegistrationContextCreator {
  Tenant tenant;
  ClientRegistrationRequest request;
  boolean dryRun;

  public ClientRegistrationContextCreator(
      Tenant tenant,
      ClientRegistrationRequest request,
      boolean dryRun) {
    this.tenant = tenant;
    this.request = request;
    this.dryRun = dryRun;
  }

  public ClientRegistrationContext create() {
    // ✅ リクエストから値オブジェクトを構築
    ClientIdentifier clientIdentifier = new ClientIdentifier(request.clientId());
    ClientName clientName = new ClientName(request.clientName());
    RedirectUris redirectUris = new RedirectUris(request.redirectUris());
    // ...

    // ✅ ClientConfigurationドメインオブジェクト構築
    ClientConfiguration configuration = ClientConfiguration.builder()
        .identifier(clientIdentifier)
        .name(clientName)
        .redirectUris(redirectUris)
        // ...
        .build();

    // ✅ Contextを返却
    return new ClientRegistrationContext(
        tenant,
        configuration,
        dryRun);
  }
}
```

**重要ポイント**:
- ✅ リクエストDTO → 値オブジェクト変換
- ✅ ドメインオブジェクト構築
- ✅ Contextカプセル化

## Audit Log 記録パターン

### AuditLogCreator

```java
AuditLog auditLog = AuditLogCreator.create(
    "ClientManagementApi.create",  // アクション名
    tenant,                         // テナント
    operator,                       // 操作者
    oAuthToken,                     // アクセストークン
    context,                        // コンテキスト（操作内容）
    requestAttributes);             // リクエスト属性（IP等）

auditLogPublisher.publish(auditLog);
```

### 操作別Audit Log

```java
// Create操作
AuditLogCreator.create("ClientManagementApi.create", ...)

// Update操作
AuditLogCreator.create("ClientManagementApi.update", ...)

// Delete操作
AuditLogCreator.create("ClientManagementApi.delete", ...)

// Read操作（必要に応じて）
AuditLogCreator.createOnRead("ClientManagementApi.get", ...)
```

**注意**: `createOnRead()`で統一してエラー回避するのは**アンチパターン**。操作に応じて適切なメソッドを使用。

**情報源**: CLAUDE.md「🚨 組織レベルAPI実装の重要注意事項」

## トランザクション管理

### @Transaction アノテーション

```java
// ✅ デフォルト: 読み書きトランザクション
@Transaction
public class ClientManagementEntryService implements ClientManagementApi {

  @Override
  public ClientManagementResponse create(...) {
    // トランザクション内で実行
  }

  // ✅ 読み取り専用: パフォーマンス最適化
  @Override
  @Transaction(readOnly = true)
  public ClientManagementResponse findList(...) {
    // 読み取り専用トランザクション
  }
}
```

**効果**:
- 自動コミット/ロールバック
- PostgreSQL Row Level Securityのための`app.tenant_id`設定
- 読み取り専用トランザクションでのパフォーマンス最適化

## Dry Run パターン

### 目的

実際の永続化を行わずに、検証結果のみを返却。

### 実装

```java
public Response create(..., boolean dryRun) {
  // Phase 1-7: 検証・Context構築・権限チェック
  // ...

  // ✅ Dry Runの場合はここで返却
  if (dryRun) {
    return context.toResponse();
  }

  // ✅ Dry Runでない場合のみ永続化
  repository.register(tenant, context.configuration());

  return context.toResponse();
}
```

### レスポンス形式

```json
{
  "dry_run": true,
  "result": {
    "id": "generated-uuid",
    "client_id": "test-client",
    "client_secret": "generated-secret",
    ...
  }
}
```

**重要**: Dry Runでも`result`フィールド内に完全な結果を含める。

**情報源**: CLAUDE.md「📝 レスポンス構造」

## アンチパターン

### ❌ 1. Context Creator軽視

```java
// ❌ 悪い例: TODOコメントで済ませる
public Response create(...) {
  // TODO: Context Creator実装
  return new Response(Status.OK, "適当なメッセージ");
}

// ✅ 良い例: 必ずContext Creator使用
public Response create(...) {
  ClientRegistrationContextCreator contextCreator =
      new ClientRegistrationContextCreator(tenant, request, dryRun);
  ClientRegistrationContext context = contextCreator.create();
  // ...
}
```

### ❌ 2. Audit Log手抜き

```java
// ❌ 悪い例: createOnRead()で統一してエラー回避
AuditLog auditLog = AuditLogCreator.createOnRead("ClientManagementApi.create", ...);

// ✅ 良い例: 操作に応じた適切なメソッド
AuditLog auditLog = AuditLogCreator.create("ClientManagementApi.create", ...); // Create
AuditLog auditLog = AuditLogCreator.createOnUpdate("ClientManagementApi.update", ...); // Update
AuditLog auditLog = AuditLogCreator.createOnDelete("ClientManagementApi.delete", ...); // Delete
```

### ❌ 3. システムレベル実装の理解不足

```java
// ❌ 悪い例: システムレベルを理解せずに組織レベル実装
public Response orgMethod(...) {
  // システムレベルのパターンを理解せずに実装
  // → Context Creator未使用、Audit Log不適切、権限チェック漏れ
}

// ✅ 良い例: システムレベル実装を完全理解してから組織レベル実装
public Response orgMethod(...) {
  // 1. 組織メンバーシップ検証
  // 2. テナントアクセス検証
  // 3. 組織-テナント関係検証
  // 4. 権限検証
  // 5. システムレベルと同じパターンで実装
}
```

**重要教訓**: 「組織レベル = システムレベル + 組織アクセス制御」であり、簡易版ではない。

**情報源**: CLAUDE.md「❌ 致命的誤解（絶対回避）」

### ❌ 4. 「たぶん」「適当に」実装

```java
// ❌ 悪い例: 推測実装
public Response create(...) {
  // たぶんこれでいけるだろう（エラー依存判断）
  repository.register(configuration); // Tenant引数忘れ
}

// ✅ 良い例: 既存実装パターンを参考
public Response create(...) {
  // 既存のClientManagementEntryServiceと同じパターン
  repository.register(tenant, configuration); // Tenant第一引数
}
```

**原則**: 「不確実な実装より確実な設計確認を優先」

**情報源**: CLAUDE.md「❌ 実装継続危険シグナル」

## IdpServerApplication - DIコンテナ

**情報源**: [IdpServerApplication.java](../../libs/idp-server-use-cases/src/main/java/org/idp/server/IdpServerApplication.java)

### 責務

idp-server全体の依存性注入（DI）を管理する中央コンテナ。全てのAPI、Protocol、EntryService、Repositoryを組み立てる。

**規模**: 1,288行、40以上のpublic APIメソッド

### 主要な役割

#### 1. Application API公開

```java
public class IdpServerApplication {

  // ✅ Application層API（エンドユーザー・RP向け）
  public OAuthFlowApi oAuthFlowApi() { ... }
  public TokenApi tokenAPi() { ... }
  public UserinfoApi userinfoApi() { ... }
  public CibaFlowApi cibaFlowApi() { ... }
  public AuthenticationTransactionApi authenticationApi() { ... }
  public IdentityVerificationApplicationApi identityVerificationApplicationApi() { ... }

  // ✅ システムレベルManagement API
  public TenantManagementApi tenantManagementApi() { ... }
  public ClientManagementApi clientManagementApi() { ... }
  public UserManagementApi userManagementAPi() { ... }
  public AuthorizationServerManagementApi authorizationServerManagementApi() { ... }
  // ... 約20個のManagement API

  // ✅ 組織レベルManagement API
  public OrgTenantManagementApi orgTenantManagementApi() { ... }
  public OrgClientManagementApi orgClientManagementApi() { ... }
  public OrgUserManagementApi orgUserManagementApi() { ... }
  // ... 約15個のOrg Management API

  // ✅ Admin API（システム管理）
  public IdpServerStarterApi idpServerStarterApi() { ... }
  public IdpServerOperationApi idpServerOperationApi() { ... }
}
```

#### 2. EntryService組み立て + Proxyラップ

```java
// EntryService実装を生成
ClientManagementEntryService entryService = new ClientManagementEntryService(
    tenantQueryRepository,
    clientConfigurationCommandRepository,
    clientConfigurationQueryRepository,
    auditLogPublisher
);

// ✅ TenantAwareEntryServiceProxyでラップ（トランザクション自動管理）
ClientManagementApi api = TenantAwareEntryServiceProxy.createProxy(
    entryService,
    ClientManagementApi.class,
    applicationDatabaseTypeProvider
);

return api;  // @Transactionアノテーション駆動で自動トランザクション管理
```

#### 3. Repository・Executor・Plugin組み立て

```java
// Repository組み立て
TenantQueryRepository tenantQueryRepository = ...
ClientConfigurationCommandRepository clientConfigurationCommandRepository = ...

// Plugin読み込み
AuthenticationInteractors authenticationInteractors =
    AuthenticationInteractorPluginLoader.load(authenticationDependencyContainer);

FederationInteractors federationInteractors =
    FederationInteractorPluginLoader.load(federationDependencyContainer);

// HttpRequestExecutor組み立て
HttpClient httpClient = HttpClientFactory.create();
OAuthAuthorizationResolvers oauthResolvers = ...
HttpRequestExecutor httpRequestExecutor = new HttpRequestExecutor(httpClient, oauthResolvers);
```

### DI階層構造

```
IdpServerApplication (DIコンテナ)
  ↓ 組み立て
EntryService実装
  ↓ Proxyラップ
TenantAwareEntryServiceProxy（@Transaction駆動）
  ↓ 公開
Management API / Application API
  ↓ 使用
Controller / Spring Boot
```

### 主要なDI対象

| カテゴリ | 内容 |
|---------|------|
| **Application API** | OAuth/Token/UserInfo/CIBA等（約10個） |
| **Management API** | System Level（約20個） + Organization Level（約15個） |
| **Admin API** | Starter/Operation（2個） |
| **Repository** | Query/Command（約50個） |
| **Protocol/Interactor** | Plugin読み込み・組み立て |
| **Executor** | HttpRequestExecutor, AuthenticationExecutors等 |

### 使用例（Spring Bootから）

```java
@Configuration
public class IdpServerConfiguration {

  @Bean
  public IdpServerApplication idpServerApplication(
      DataSource dataSource,
      CacheStore cacheStore,
      // ... その他の依存関係
  ) {
    return new IdpServerApplication(dataSource, cacheStore, ...);
  }

  @Bean
  public ClientManagementApi clientManagementApi(IdpServerApplication app) {
    return app.clientManagementApi();  // ✅ Proxyラップ済みのAPI取得
  }
}
```

**重要**: Spring BootのControllerは、`IdpServerApplication`から取得したAPI（Proxyラップ済み）を使用する。

## まとめ

### idp-server-use-cases を理解するための8つのポイント

1. **IdpServerApplication**: 全体のDIコンテナ（1,288行、40以上のAPI）、Proxy統合の中心
2. **2つのEntryService層**: Application層（3-4フェーズ、Protocol委譲） vs Control Plane層（10フェーズ、完全制御）
3. **Application層パターン**: Tenant取得 → Request構築 → Protocol実行 → Transaction管理
4. **Control Plane層10フェーズ**: 権限取得 → Tenant取得 → Validator → Context Creator → Audit Log → 権限チェック → バリデーション → Dry Run → 永続化 → レスポンス
5. **Context Creator必須**（Control Planeのみ）: TODOコメントで済ませず、必ず実装
6. **Audit Log適切化**（Control Planeのみ）: 操作に応じたメソッド使用（create/update/delete別）
7. **システムレベル理解優先**: 組織レベル実装前にシステムレベルを完全理解
8. **Tenant第一引数**: Repository操作は必ずTenant第一引数（OrganizationRepository除く）

### Application層 vs Control Plane層 選択基準

| 質問 | Application層 | Control Plane層 |
|------|--------------|----------------|
| 対象ユーザーは？ | エンドユーザー/RP | 管理者 |
| ビジネスロジックはどこ？ | Core層Protocol | EntryService内 |
| 権限チェックは必要？ | ❌ 不要 | ✅ 必須 |
| Audit Logは必要？ | ❌ 不要 | ✅ 必須 |
| Dry Runは必要？ | ❌ 不要 | ✅ 必須 |
| OIDC仕様準拠？ | ✅ 厳密準拠 | △ 管理操作 |

### 次のステップ

- [idp-server-control-plane（管理API契約）](./ai-13-control-plane.md) - API契約定義
- [idp-server-core-adapter（アダプター層）](./ai-21-core-adapter.md) - Repository実装
- [idp-server-database（データベース層）](./ai-22-database.md) - スキーマ・マイグレーション

---

## ドキュメント修正履歴

### 2025-10-12: 実装検証に基づく修正

#### 修正1: 組織アクセス制御フローの実装に合わせた修正 (551-593行目)

**問題**: 存在しないRepositoryメソッドを使用した想像フロー

**修正前**:
```java
// ❌ 存在しないメソッド
OrganizationMember member = organizationMemberRepository.find(...);  // ❌ 存在しない
AssignedTenant assignment = organizationRepository.findAssignment(...);  // ❌ 存在しない

// ❌ 個別の検証ロジック（実際は統合されている）
if (member == null) { ... }
if (assignment == null) { ... }
if (!permissions.includesAll(...)) { ... }
```

**修正後**:
```java
// ✅ 実際の実装
Organization organization = organizationRepository.get(organizationIdentifier);
Tenant targetTenant = tenantQueryRepository.get(tenantIdentifier);

// ✅ OrganizationAccessVerifier で統合検証
OrganizationAccessVerifier organizationAccessVerifier = new OrganizationAccessVerifier();
OrganizationAccessControlResult accessResult =
    organizationAccessVerifier.verifyAccess(
        organization,
        tenantIdentifier,
        operator,
        permissions);

if (!accessResult.isSuccess()) {
  return new UserManagementResponse(UserManagementStatus.FORBIDDEN, response);
}
```

**重要な変更**:
- 個別検証 → **OrganizationAccessVerifier** による統合検証
- 存在しないメソッド削除（findMember, findAssignment）
- `OrganizationAccessControlResult` によるアクセス制御結果管理
- 4ステップの検証内容を説明として明記

**検証**:
- [OrgUserManagementEntryService.java:150-187](../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/control_plane/organization_manager/OrgUserManagementEntryService.java#L150-L187)
- [OrganizationAccessVerifier.java](../../libs/idp-server-control-plane/src/main/java/org/idp/server/control_plane/organization/access/OrganizationAccessVerifier.java)

### 検証済み項目

#### ✅ EntryService 10フェーズパターン
- [ClientManagementEntryService.java:47-114](../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/control_plane/system_manager/ClientManagementEntryService.java#L47-L114)
- 全10フェーズが実装と完全一致

#### ✅ Application層パターン
- [OAuthFlowEntryService.java:117-138](../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/application/enduser/OAuthFlowEntryService.java#L117-L138)
- シンプルな委譲パターンが正確

#### ✅ @Transaction アノテーション
- クラスレベル: `@Transaction`
- メソッドレベル: `@Transaction(readOnly = true)`
- 実装と完全一致

#### ✅ システムレベル vs 組織レベル
- メソッドシグネチャの違いが正確
- `OrganizationIdentifier` → `TenantIdentifier` の順序が正確

### 修正の原則

**CLAUDE.md「想像ドキュメント作成防止」に基づく修正**:
1. **実装ファースト**: 実際の組織レベルEntryServiceを確認
2. **統合検証パターン**: OrganizationAccessVerifierの使用を正確に記載
3. **存在しないメソッド削除**: findMember(), findAssignment()の削除
4. **情報源記録**: 実装ファイルパス・行番号を明記

---

**情報源**:
- `libs/idp-server-use-cases/src/main/java/`配下の実装コード
- CLAUDE.md「4層アーキテクチャ詳細」「組織レベルAPI設計」「🚨 組織レベルAPI実装の重要注意事項」
- [ClientManagementEntryService.java](../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/control_plane/system_manager/ClientManagementEntryService.java)
- [OrgUserManagementEntryService.java](../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/control_plane/organization_manager/OrgUserManagementEntryService.java)
- [OrganizationAccessVerifier.java](../../libs/idp-server-control-plane/src/main/java/org/idp/server/control_plane/organization/access/OrganizationAccessVerifier.java)

**最終更新**: 2025-10-12
**確認方法**: `find libs/idp-server-use-cases -type f -name "*EntryService.java" | head -15`
**レビュー実施**: 2025-10-12 - AI開発者向けドキュメント品質改善プロジェクト
