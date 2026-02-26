---
name: dev-architecture
description: アーキテクチャ（Hexagonal Architecture + DDD）の開発・修正を行う際に使用。4層構造、Handler-Service-Repository、EntryService、マルチテナント設計実装時に役立つ。
---

# アーキテクチャ（Architecture）開発ガイド

## ドキュメント

- `documentation/docs/content_06_developer-guide/01-getting-started/02-architecture-overview.md` - アーキテクチャ概要
- `documentation/docs/content_06_developer-guide/01-getting-started/03-design-principles.md` - 設計原則
- `documentation/docs/content_06_developer-guide/06-patterns/common-patterns.md` - 共通実装パターン

## 機能概要

idp-serverは、**Hexagonal Architecture（ヘキサゴナルアーキテクチャ）+ DDD（ドメイン駆動設計）** を採用した4層構造。
- **4層アーキテクチャ**: Controller → UseCase → Core → Adapter
- **Handler-Service-Repository パターン**: Core層の3層分離
- **EntryService パターン**: UseCase層のオーケストレーション
- **マルチテナント設計**: Tenant第一引数の原則
- **型安全性**: String/Map濫用禁止、値オブジェクト優先

## 4層アーキテクチャ

```
┌─────────────────────────────────────────────┐
│         Controller層（*V1Api）               │
│   (idp-server-springboot-adapter)          │
│        HTTP ↔ DTO変換のみ                   │
│        ❌ ロジック禁止                        │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│      UseCase層（*EntryService）              │
│      (idp-server-use-cases)                │
│   トランザクション境界・オーケストレーション     │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│         Core層（Handler-Service）            │
│         (idp-server-core)                  │
│   OIDC仕様準拠・ドメインロジック              │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│       Adapter層（DataSource）                │
│    (idp-server-core-adapter)               │
│    Repository実装・永続化カプセル化            │
│    ❌ ドメインロジック禁止                    │
└─────────────────────────────────────────────┘
```

## モジュール構成

### Controller層
```
libs/
└── idp-server-springboot-adapter/
    └── .../adapters/springboot/
        ├── control_plane/restapi/management/
        │   ├── ClientManagementV1Api.java
        │   ├── UserManagementV1Api.java
        │   └── ... (管理API Controller)
        └── application/restapi/
            ├── OAuthFlowApiV1.java
            └── TokenApiV1.java
```

### UseCase層
```
libs/
└── idp-server-use-cases/
    └── .../usecases/
        ├── control_plane/              # Control Plane EntryService
        │   ├── system_manager/
        │   │   ├── ClientManagementEntryService.java
        │   │   └── TenantManagementEntryService.java
        │   └── organization_manager/
        │       ├── OrgClientManagementEntryService.java
        │       └── OrgUserManagementEntryService.java
        └── application/                # Application EntryService
            ├── enduser/
            │   ├── OAuthFlowEntryService.java
            │   └── TokenEntryService.java
            └── relying_party/
                └── OidcMetaDataEntryService.java
```

### Core層
```
libs/
└── idp-server-core/
    └── .../core/openid/
        ├── oauth/
        │   ├── handler/
        │   │   ├── OAuthAuthorizeHandler.java
        │   │   └── OAuthHandler.java
        │   ├── service/
        │   │   └── OAuthAuthorizeService.java
        │   └── repository/
        │       ├── AuthorizationRequestRepository.java
        │       └── ClientConfigurationQueryRepository.java
        ├── token/
        │   ├── service/
        │   │   ├── AuthorizationCodeGrantService.java
        │   │   ├── RefreshTokenGrantService.java
        │   │   └── ClientCredentialsGrantService.java
        │   └── repository/
        │       └── OAuthTokenCommandRepository.java
        └── grant_management/
            └── grant/
                └── AuthorizationGrant.java
```

### Adapter層
```
libs/
└── idp-server-core-adapter/
    └── .../adapters/datasource/
        ├── oidc/
        │   ├── ClientConfigurationQueryDataSource.java
        │   └── ClientConfigurationCommandDataSource.java
        ├── token/
        │   ├── query/OAuthTokenQueryDataSource.java
        │   └── command/OAuthTokenCommandDataSource.java
        └── grant_management/
            └── AuthorizationGrantDataSource.java
```

## 各層の責務

### 1. Controller層（*V1Api）

**命名規則:** `{Domain}ManagementV1Api` または `{Domain}ApiV1`

**責務:**
- HTTP → RequestAttributes変換
- Control-Plane API または Application API 呼び出し
- HTTPレスポンス生成

**実装パターン:**
```java
@RestController
@RequestMapping("/v1/management/tenants/{tenant-id}/clients")
public class ClientManagementV1Api implements ParameterTransformable {

    ClientManagementApi clientManagementApi;

    @PostMapping
    public ResponseEntity<?> post(
        @AuthenticationPrincipal OperatorPrincipal operatorPrincipal,
        @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
        @RequestBody Map<String, Object> body,
        @RequestParam(value = "dry_run", defaultValue = "false") boolean dryRun,
        HttpServletRequest httpServletRequest
    ) {
        // 1. RequestAttributes変換
        RequestAttributes requestAttributes = transform(httpServletRequest);

        // 2. Control-Plane API呼び出し
        ClientManagementResponse response = clientManagementApi.create(
            tenantIdentifier,
            operatorPrincipal.getUser(),
            operatorPrincipal.getOAuthToken(),
            new ClientRegistrationRequest(body),
            requestAttributes,
            dryRun
        );

        // 3. HTTPレスポンス生成
        return new ResponseEntity<>(
            response.contents(),
            HttpStatus.valueOf(response.statusCode())
        );
    }
}
```

**重要:**
- ✅ `implements ParameterTransformable` で HttpServletRequest → RequestAttributes 変換
- ✅ `@AuthenticationPrincipal OperatorPrincipal` で認証済みオペレーター取得
- ✅ `TenantIdentifier` 型安全なパス変数
- ❌ ビジネスロジック禁止

---

### 2. UseCase層（*EntryService）

**命名規則:** `{Domain}{Action}EntryService`

**責務:**
- トランザクション境界（`@Transaction`）
- Core層Handler/Service呼び出し
- 認可チェック（Control Planeのみ）
- Audit Log記録（Control Planeのみ）

#### Application層 EntryService（3-4フェーズ）

対象: エンドユーザー/RP向けAPI

```java
@Transaction
public class OAuthFlowEntryService implements OAuthFlowApi {

    TenantQueryRepository tenantQueryRepository;
    OAuthProtocols oAuthProtocols;

    @Override
    public OAuthRequestResponse request(
        TenantIdentifier tenantIdentifier,
        Map<String, String[]> params,
        RequestAttributes requestAttributes
    ) {
        // 1. Tenant取得
        Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

        // 2. Request構築
        OAuthRequest oAuthRequest = new OAuthRequest(tenant, params);

        // 3. Protocol実行
        OAuthProtocol protocol = oAuthProtocols.get(tenant.authorizationProvider());
        return protocol.request(oAuthRequest);
    }
}
```

#### Control Plane層 EntryService（10フェーズ）

対象: 管理者向けAPI

```java
@Transaction
public class ClientManagementEntryService implements ClientManagementApi {

    TenantQueryRepository tenantQueryRepository;
    ClientConfigurationCommandRepository commandRepository;
    AuditLogPublisher auditLogPublisher;

    @Override
    public ClientManagementResponse create(
        TenantIdentifier tenantIdentifier,
        User operator,
        OAuthToken oAuthToken,
        ClientRegistrationRequest request,
        RequestAttributes requestAttributes,
        boolean dryRun
    ) {
        // Phase 1: 権限取得
        AdminPermissions permissions = getRequiredPermissions("create");

        // Phase 2: Tenant取得
        Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

        // Phase 3: Validator - 入力検証
        ClientRegistrationRequestValidator validator =
            new ClientRegistrationRequestValidator(request, dryRun);
        validator.validate();

        // Phase 4: Context Creator - コンテキスト構築
        ClientRegistrationContextCreator contextCreator =
            new ClientRegistrationContextCreator(tenant, request, dryRun);
        ClientRegistrationContext context = contextCreator.create();

        // Phase 5: Audit Log記録
        AuditLog auditLog = AuditLogCreator.create(...);
        auditLogPublisher.publish(auditLog);

        // Phase 6: 権限チェック
        if (!permissions.includesAll(operator.permissionsAsSet())) {
            return new ClientManagementResponse(FORBIDDEN, response);
        }

        // Phase 7: バリデーション結果チェック（省略可）

        // Phase 8: Dry Run対応
        if (dryRun) {
            return context.toResponse();
        }

        // Phase 9: 永続化
        commandRepository.register(tenant, context.configuration());

        // Phase 10: レスポンス返却
        return context.toResponse();
    }
}
```

**重要:**
- ✅ Context Creator必須（TODOコメント禁止）
- ✅ Audit Log記録必須
- ✅ Dry Run対応必須
- ✅ Application層とControl Plane層のパターンを区別

---

### 3. Core層（Handler-Service-Repository）

**責務:** OAuth/OIDC仕様準拠のドメインロジック

#### Handler - プロトコル処理

**命名規則:** `{Domain}{Action}Handler`

**実装パターン:**
```java
public class OAuthAuthorizeHandler {

    AuthorizationResponseCreators creators;
    AuthorizationRequestRepository authorizationRequestRepository;
    ClientConfigurationQueryRepository clientConfigurationQueryRepository;

    public AuthorizationResponse handle(
        OAuthAuthorizeRequest request,
        OAuthSessionDelegate delegate
    ) {
        // 1. Validator - 入力検証
        OAuthAuthorizeRequestValidator validator =
            new OAuthAuthorizeRequestValidator(...);
        validator.validate();

        // 2. Repository - データ取得（Tenant第一引数）
        Tenant tenant = request.tenant();
        AuthorizationRequest authzReq =
            authorizationRequestRepository.get(tenant, request.toIdentifier());
        ClientConfiguration client =
            clientConfigurationQueryRepository.get(tenant, authzReq.requestedClientId());

        // 3. Context構築
        OAuthAuthorizeContext context = new OAuthAuthorizeContext(...);

        // 4. Creator - レスポンス生成
        AuthorizationResponseCreator creator = creators.get(context.responseType());
        AuthorizationResponse response = creator.create(context);

        // 5. 永続化（Tenant第一引数）
        if (response.hasAuthorizationCode()) {
            authorizationCodeGrantRepository.register(tenant, grant);
        }

        return response;
    }
}
```

**重要:**
- ✅ Tenant第一引数必須
- ✅ Validator/Verifier分離
- ✅ Context Pattern使用
- ✅ Factory/Creator分離

#### Service - 純粋ビジネスロジック

**命名規則:** `{Domain}{Action}Service`

**実装パターン:**
```java
public class AuthorizationCodeGrantService
    implements OAuthTokenCreationService {

    OAuthTokenCommandRepository oAuthTokenCommandRepository;
    AccessTokenCreator accessTokenCreator;

    @Override
    public OAuthToken create(
        TokenRequestContext context,
        ClientCredentials clientCredentials
    ) {
        // 1. Validator - 入力検証
        AuthorizationCodeGrantValidator validator =
            new AuthorizationCodeGrantValidator(context);
        validator.validate();

        // 2. Verifier - ビジネスルール検証
        AuthorizationCodeGrantVerifier verifier =
            new AuthorizationCodeGrantVerifier();
        verifier.verify(context, authorizationRequest, grant, clientCredentials);

        // 3. AuthorizationGrant構築
        AuthorizationGrant authorizationGrant =
            new AuthorizationGrantBuilder(...)
                .build();

        // 4. AccessToken生成
        AccessToken accessToken = accessTokenCreator.create(...);

        // 5. OAuthToken構築・永続化
        OAuthToken oAuthToken = new OAuthTokenBuilder(...)
            .add(accessToken)
            .build();

        oAuthTokenCommandRepository.register(context.tenant(), oAuthToken);
        return oAuthToken;
    }
}
```

**重要:**
- ✅ RFC準拠のJavadoc記載
- ✅ インターフェース実装で機能特性を表現
- ✅ Validator/Verifier明確に分離

#### Repository - データアクセス抽象化

**命名規則:** `{Entity}QueryRepository` / `{Entity}CommandRepository`

**実装パターン:**
```java
public interface ClientConfigurationQueryRepository {

    // ✅ 必須存在: get() - 存在しない場合は例外
    ClientConfiguration get(Tenant tenant, RequestedClientId clientId);

    // ✅ 任意存在: find() - 存在しない場合は空オブジェクト
    ClientConfiguration find(Tenant tenant, ClientIdentifier clientIdentifier);

    // ✅ リスト取得
    List<ClientConfiguration> findList(Tenant tenant, int limit, int offset);

    // ✅ カウント
    long findTotalCount(Tenant tenant);
}
```

**重要:**
- 🚨 **Tenant第一引数必須**（OrganizationRepository除く）
- ✅ get() vs find() の使い分け
- ✅ Query/Command分離（CQRS）
- ❌ Optional使用禁止（Null Object Pattern使用）

---

### 4. Adapter層（DataSource-SqlExecutor）

**責務:** Repository実装・DB/Redisアクセス

**実装パターン:**
```java
public class OAuthTokenCommandDataSource
    implements OAuthTokenCommandRepository {

    OAuthTokenSqlExecutor executor;
    AesCipher aesCipher;
    HmacHasher hmacHasher;

    @Override
    public void register(Tenant tenant, OAuthToken oAuthToken) {
        // ✅ SQLExecutorに委譲
        executor.insert(oAuthToken, aesCipher, hmacHasher);
    }
}
```

**重要:**
- ✅ DataSource-SqlExecutor 2層分離
- ✅ 暗号化・ハッシュ化の適用
- ❌ ビジネスロジック禁止

---

## 設計原則

### 1. Tenant第一引数の原則

```java
// ✅ 正しい
ClientConfiguration get(Tenant tenant, RequestedClientId clientId);

// ❌ 間違い
ClientConfiguration get(RequestedClientId clientId);
```

**例外:** `OrganizationRepository` のみ（組織はテナント上位概念）

### 2. 値オブジェクト優先

```java
// ❌ String濫用
public void register(Tenant tenant, String clientId, String clientSecret);

// ✅ 値オブジェクト
public void register(Tenant tenant, RequestedClientId clientId, ClientSecret clientSecret);
```

### 3. Validator/Verifier分離

**Validator:** 入力形式チェック → BadRequestException
**Verifier:** ビジネスルール検証 → OAuthRedirectableBadRequestException

### 4. Null Object Pattern

```java
// ✅ 正しい: find()は空オブジェクトを返す
User user = userQueryRepository.find(tenant, userId);
if (user.exists()) {  // nullチェック不要
    // 処理
}

// ❌ 間違い: Optionalを使用
Optional<User> user = userRepository.find(tenant, userId);
if (user.isPresent()) {  // アンチパターン
    // 処理
}
```

### 5. Context Creator必須

Control Plane EntryServiceでは、Context Creatorを必ず使用:

```java
// ✅ 正しい
ClientRegistrationContextCreator creator =
    new ClientRegistrationContextCreator(tenant, request, dryRun);
ClientRegistrationContext context = creator.create();

// ❌ 間違い
// TODO: Context Creator実装  ← 絶対禁止
```

---

## アンチパターン

### ❌ 1. Controller層でのロジック実行

```java
// ❌ 悪い例
@RestController
public class BadController {
    @PostMapping
    public ResponseEntity<?> register(@RequestBody Map<String, Object> request) {
        // ❌ Controllerでビジネスロジック
        if (request.get("client_type").equals("PUBLIC")) {
            // ビジネス判定禁止
        }

        // ❌ Controllerで直接Repository呼び出し
        clientRepository.save(request);
    }
}
```

### ❌ 2. Adapter層でのビジネスロジック

```java
// ❌ 悪い例
public class ClientConfigurationDataSource {
    @Override
    public ClientConfiguration get(Tenant tenant, RequestedClientId clientId) {
        ClientConfiguration config = executor.selectById(clientId);

        // ❌ ビジネスロジックがデータソース層に漏れている
        if ("ORGANIZER".equals(tenant.type())) {
            config.setSpecialPermissions(true);
        }

        return config;
    }
}
```

**原則:** データソース層 = SELECT/INSERT/UPDATE/DELETE のみ

### ❌ 3. Util/Map濫用

```java
// ❌ Util濫用
public class OAuthUtils {
    public static boolean isValidCode(String code) {
        // ドメインロジックがUtilに漏れている
    }
}

// ✅ ドメインオブジェクトに配置
public class AuthorizationCode {
    public boolean isValid() {
        // ドメインロジックはドメインオブジェクトに
    }
}
```

```java
// ❌ Map濫用
public Map<String, Object> authorize(Map<String, String> request) {
    // 型安全性がない
}

// ✅ 専用クラス
public AuthorizationResponse authorize(AuthorizationRequest request) {
    // 型安全、IDE補完が効く
}
```

### ❌ 4. Tenant第一引数忘れ

```java
// ❌ マルチテナント分離違反
ClientConfiguration client = repository.get(clientId);

// ✅ 正しい
ClientConfiguration client = repository.get(tenant, clientId);
```

---

## 実装判断フロー

新機能を実装する際の判断基準:

```
Q1: HTTPリクエストを処理する？
    YES → Controller層 (*V1Api)
    NO  → Q2へ

Q2: トランザクション境界・認可チェックが必要？
    YES → UseCase層 (*EntryService)
    NO  → Q3へ

Q3: OAuth/OIDC仕様に関わるロジック？
    YES → Core層 (Handler/Service)
    NO  → Q4へ

Q4: データベースアクセス？
    YES → Adapter層 (DataSource)
    NO  → Platform層を検討
```

---

## E2Eテスト

アーキテクチャパターンに従った実装を検証:

```
e2e/src/tests/
├── spec/                           # プロトコル仕様テスト
│   ├── oidc_core_3_1_code.test.js
│   └── rfc6749_token_endpoint_*.test.js
│
├── scenario/                       # シナリオテスト
│   ├── application/
│   │   └── scenario-02-sso-oidc.test.js
│   └── control_plane/
│       └── organization/
│           └── organization_client_management.test.js
│
└── usecase/                        # ユースケーステスト
    └── standard/
        └── standard-01-onboarding-and-audit.test.js
```

---

## コマンド

```bash
# ビルド
./gradlew :libs:idp-server-core:compileJava
./gradlew :libs:idp-server-use-cases:compileJava
./gradlew :libs:idp-server-core-adapter:compileJava
./gradlew :libs:idp-server-springboot-adapter:compileJava

# テスト
cd e2e && npm test -- spec/oidc_core_3_1_code.test.js
cd e2e && npm test -- scenario/control_plane/

# 全モジュールビルド
./gradlew build
```

---

## トラブルシューティング

### Controller層でロジックを実装してしまった
- ✅ Core層またはUseCase層に移動
- ✅ Controllerは型変換のみに限定

### Tenant第一引数を忘れた
- ✅ 全Repository操作で `Tenant` を第一引数に追加
- ✅ コンパイルエラーが発生するため、すぐに気づく

### Adapter層でビジネスロジックを実装してしまった
- ✅ Core層のドメインモデルに移動
- ✅ データソース層はSELECT/INSERT/UPDATE/DELETEのみ

### Optionalを使用してしまった
- ✅ Null Object Patternに変更
- ✅ `find()` は空オブジェクトを返す
- ✅ `exists()` メソッドで存在チェック

### Context Creatorをサボった
- ✅ TODOコメントで済ませず、必ず実装
- ✅ 既存の類似パターンを参考

---

## よくある質問

### Q1: なぜController層にロジックを書いてはいけない？

**A:** テスト容易性・ポータビリティのため。Controller層をRESTからgRPCに変更しても、UseCase層以下は変わらない。

### Q2: Application層とControl Plane層の違いは？

**A:**

| 項目 | Application層 | Control Plane層 |
|------|--------------|----------------|
| 対象 | エンドユーザー/RP | 管理者 |
| フェーズ数 | 3-4フェーズ | 10フェーズ |
| 権限チェック | ❌ なし | ✅ 必須 |
| Audit Log | ❌ なし | ✅ 必須 |
| Dry Run | ❌ なし | ✅ 必須 |
| Context Creator | ❌ なし | ✅ 必須 |

### Q3: なぜTenant第一引数なのか？

**A:** マルチテナント分離を強制するため。引数忘れでデータ漏洩リスクを防ぐ。

### Q4: get() と find() の違いは？

**A:**
- `get()`: 必須存在（存在しない場合は例外）
- `find()`: 任意存在（空オブジェクトを返す、Null Object Pattern）

---

## 拡張モジュール（Extension Modules）

Core 層の機能を Plugin アーキテクチャで拡張する仕組み。`PluginLoader` (ServiceLoader ベース) で動的にロードされる。

### 拡張モジュール一覧

| モジュール | 責務 | 主要仕様 |
|-----------|------|---------|
| `idp-server-core-extension-ciba` | バックチャネル認証 | OIDC CIBA Core 1.0 |
| `idp-server-core-extension-fapi` | 金融グレード API セキュリティ | FAPI 1.0 Baseline/Advanced |
| `idp-server-core-extension-ida` | 身元確認 | OIDC IDA |
| `idp-server-core-extension-pkce` | 認可コード横取り防止 | RFC 7636 |
| `idp-server-core-extension-vc` | Verifiable Credentials 発行 | OID4VCI |

### Plugin 登録パターン

```
libs/idp-server-core-extension-{name}/
  src/main/
    java/.../extension/{name}/    # 実装
    resources/META-INF/services/  # ServiceLoader 登録
```

拡張モジュールは `META-INF/services/` にインターフェース名のファイルを配置し、`PluginLoader.loadFromInternalModule(T.class)` で Core 層からロードされる。

### AuthorizationProfile による分岐

```java
// Core 層で Profile に応じた Verifier を切り替え
AuthorizationProfile profile = context.authorizationProfile();
// FAPI_BASELINE → FapiBaselineVerifier
// FAPI_ADVANCE  → FapiAdvanceVerifier
// DEFAULT       → 標準検証
```

---

## Validator vs Verifier - 責務分離

### Validator（入力形式チェック）

- nullチェック、形式妥当性、必須パラメータ存在、重複値チェック
- ビジネスルール検証は実施しない
- 例外: `{Operation}BadRequestException` → HTTP 400

### Verifier（ビジネスルール検証）

- プロトコル仕様準拠チェック、トークン有効期限、クライアント認証検証
- 入力形式チェックは Validator 担当
- 例外: `OAuthRedirectableBadRequestException`, `UnauthorizedException`

| 項目 | Validator | Verifier |
|------|-----------|----------|
| チェック対象 | 入力パラメータ形式 | ビジネスルール・仕様準拠 |
| 例外型 | BadRequestException | OAuth標準エラー |
| 呼び出し順序 | 最初（早期エラー検出） | Validator後 |
| 条件付き実行 | なし | `shouldVerify()`で判定 |

### throwExceptionIf パターン

条件をメソッド名で明示的に表現し、可読性を向上させるパターン。

```java
public void verify(...) {
  throwExceptionIfCodeIsInvalid(authorizationCode);
  throwExceptionIfClientIdMismatch(clientId);
  throwExceptionIfRedirectUriMismatch(redirectUri);
  throwExceptionIfExpirationTimeExpired(expiresAt);
}
```

---

## Extension Verifier パターン（shouldVerify 条件付き実行）

拡張モジュールの Verifier は `AuthorizationRequestExtensionVerifier` インターフェースを実装し、`shouldVerify()` で実行可否を判定。

```java
public class PkceVerifier implements AuthorizationRequestExtensionVerifier {
  @Override
  public boolean shouldVerify(OAuthRequestContext context) {
    return context.isPckeRequest();
  }
  @Override
  public void verify(OAuthRequestContext context) {
    // PKCE検証ロジック
  }
}
```

Core 層の統合 Verifier が全 Extension Verifier をループし、`shouldVerify()` が `true` の Verifier のみ `verify()` を実行する。

---

## PluginLoader API

PluginLoader は**静的メソッドのみ**を提供（インスタンス化不可）。

```java
public class PluginLoader {
  // 内部モジュール（idp-server-core-extension-*）からロード
  public static <T> List<T> loadFromInternalModule(Class<T> type)
  // 外部JARディレクトリ（plugins/）からロード
  public static <T> List<T> loadFromExternalModule(Class<T> type)
}
```

- `loadFromInternalModule`: Java 標準 `ServiceLoader` + `META-INF/services/`
- `loadFromExternalModule`: `URLClassLoader` + ServiceLoader（カスタム ClassLoader）、`plugins/` ディレクトリの JAR をロード

---

## IdpServerApplication - DI コンテナ

**探索起点**: `libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/IdpServerApplication.java`

idp-server 全体の依存性注入を管理する中央コンテナ。全 API・EntryService・Repository を組み立てる。

### DI 階層構造

```
IdpServerApplication (DIコンテナ)
  ↓ 組み立て
EntryService 実装
  ↓ Proxy ラップ
TenantAwareEntryServiceProxy（@Transaction 駆動）
  ↓ 公開
Management API / Application API
  ↓ 使用
Controller / Spring Boot
```

EntryService 実装を生成し、`TenantAwareEntryServiceProxy.createProxy()` でラップすることで、`@Transaction` アノテーション駆動のトランザクション管理を自動化する。
