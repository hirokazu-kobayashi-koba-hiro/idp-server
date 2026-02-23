---
name: springboot-adapter
description: Spring Boot統合（idp-server-springboot-adapter）の開発・修正を行う際に使用。Controller パターン、Exception Handler、Filter、Spring Security 統合、Bean 定義実装時に役立つ。
---

# Spring Boot アダプター開発ガイド

## ドキュメント

- `documentation/docs/content_06_developer-guide/01-getting-started/02-architecture-overview.md` - アーキテクチャ概要

## 機能概要

`idp-server-springboot-adapter` は HTTP/REST API の実装レイヤー。

- **Controller**: HTTP リクエスト → EntryService/Control-Plane API 呼び出し
- **Configuration**: Spring Bean 定義（DataSource, Repository）
- **Exception Handler**: 例外 → HTTP エラーレスポンス変換
- **Filter**: 認証・CORS 等の前処理

**鉄則**: Controller にビジネスロジックは一切含まない。HTTP ↔ DTO 変換のみ。

---

## モジュール構成

**探索起点**: `libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/`

```
adapters/springboot/
  application/restapi/oauth/     # OAuth/OIDC エンドポイント（OAuthV1Api 等）
  control_plane/restapi/         # 管理 API（*ManagementV1Api）
  configuration/                 # Spring Bean 定義
  ApiExceptionHandler.java       # グローバル例外ハンドラー
  DynamicCorsFilter.java         # テナント動的 CORS
```

---

## Controller パターン

### 命名規則

- 管理 API: `{Domain}ManagementV1Api`（例: `ClientManagementV1Api`）
- OAuth API: `OAuthV1Api`, `TokenV1Api` 等

### 実装パターン

```java
@RestController
@RequestMapping("/v1/management/tenants/{tenant-id}/clients")
public class ClientManagementV1Api implements ParameterTransformable {

  ClientManagementApi clientManagementApi;  // control-plane API

  public ClientManagementV1Api(IdpServerApplication idpServerApplication) {
    this.clientManagementApi = idpServerApplication.clientManagementApi();
  }

  @PostMapping
  public ResponseEntity<?> post(
      @AuthenticationPrincipal OperatorPrincipal operatorPrincipal,
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,
      @RequestBody(required = false) Map<String, Object> body,
      HttpServletRequest httpServletRequest) {

    // Phase 1: RequestAttributes 変換
    RequestAttributes requestAttributes = transform(httpServletRequest);

    // Phase 2: API 呼び出し
    ClientManagementResponse response = clientManagementApi.create(
        tenantIdentifier, operatorPrincipal.getUser(), ...);

    // Phase 3: レスポンス生成
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(response.contents(), httpHeaders,
        HttpStatus.valueOf(response.statusCode()));
  }
}
```

### 重要ポイント

- `implements ParameterTransformable` で HttpServletRequest → RequestAttributes 変換
- `@AuthenticationPrincipal OperatorPrincipal` で認証済みオペレーター取得
- `@PathVariable("tenant-id") TenantIdentifier` で型安全なパス変数
- DI は `IdpServerApplication` 経由（Spring Bean 直接注入ではない）

---

## 例外 → HTTP ステータスマッピング

**探索起点**: `ApiExceptionHandler.java`

| 例外 | HTTP ステータス | エラーコード |
|------|--------------|-------------|
| `BadRequestException` | 400 | `invalid_request` |
| `UnauthorizedException` | 401 | `invalid_request` |
| `ForbiddenException` | 403 | `invalid_request` |
| `NotFoundException` | 404 | `invalid_request` |
| `ConflictException` | 409 | `invalid_request` |
| `SqlDuplicateKeyException` | 409 | `duplicate_key` |

---

## Filter

| Filter | 責務 |
|--------|------|
| `ManagementApiFilter` | 管理 API のアクセストークン検証・権限検証 |
| `OrgManagementFilter` | 組織レベル API の認証・認可 |
| `DynamicCorsFilter` | テナント固有の CORS 設定を動的適用 |

---

## Configuration パターン

```java
@Configuration
public class DataSourceConfiguration {

  @Bean
  public ClientConfigurationQueryRepository clientConfigurationQueryRepository(
      DataSource dataSource) {
    ClientConfigurationSqlExecutor executor = new PostgresqlExecutor(dataSource);
    return new ClientConfigurationQueryDataSource(executor);
  }
}
```

---

## Filter 詳細

### ManagementApiFilter

管理 API のアクセストークン検証・権限検証を行う。`OperatorPrincipal` を `SecurityContextHolder` にセット。

### OrgManagementFilter

組織レベル API 用。URL からの `OrganizationIdentifier` 解決 + 組織の admin テナントでのトークン検証。`OrganizationOperatorPrincipal` をセット。

| 項目 | ManagementApiFilter | OrgManagementFilter |
|------|-------------------|-------------------|
| エンドポイント | `/management/*` | `/management/organizations/{orgId}/*` |
| Principal | `OperatorPrincipal` | `OrganizationOperatorPrincipal` |
| スコープ | `management` | `org-management` or `management` |

### DynamicCorsFilter

テナント固有の CORS 設定を動的適用。リクエストパスからテナントを解決し、`CorsConfiguration` を取得。

テナント解決順序:
1. Organization API → ORGANIZER テナントの CORS 設定
2. System Management API → admin テナントの CORS 設定
3. Application API → パスから抽出したテナントの CORS 設定

---

## 例外 → HTTP ステータス 完全マッピング

```
MaliciousInputException          → 400 (invalid_request)
BadRequestException              → 400 (invalid_request)
UnauthorizedException            → 401 (invalid_request)
ForbiddenException               → 403 (invalid_request)
NotFoundException                → 404 (invalid_request)
NoResourceFoundException         → 404 (invalid_request)
ConflictException                → 409 (invalid_request)
SqlDuplicateKeyException         → 409 (duplicate_key)
HttpRequestMethodNotSupported    → 405 (invalid_request)
HttpMediaTypeNotAcceptable       → 406 (invalid_request)
HttpMediaTypeNotSupported        → 400 (invalid_request)
HttpMessageConversionException   → 400 (invalid_request)
DateTimeParseException           → 400 (invalid_request)
InvalidConfigurationException    → 500 (server_error)
Exception (catch-all)            → 500 (server_error)
```

`MaliciousInputException` は攻撃詳細を ERROR ログに記録するが、レスポンスには汎用メッセージのみ返す（セキュリティ原則）。

---

## 責務分離の原則

```
Controller (V1Api)         ← HTTP ↔ DTO 変換のみ
  ↓
Control-Plane API          ← EntryService 呼び出し（Proxy でラップ）
  ↓
EntryService (UseCase層)   ← オーケストレーション
  ↓
Core層                     ← ドメインロジック
```
