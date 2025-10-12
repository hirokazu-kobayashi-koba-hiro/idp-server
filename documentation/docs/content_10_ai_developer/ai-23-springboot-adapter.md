# idp-server-springboot-adapter

## モジュール概要

**情報源**: `libs/idp-server-springboot-adapter/`
**確認日**: 2025-10-12

### 責務

Spring Boot統合・HTTP/REST API実装。

- **Controller**: HTTP → EntryService呼び出し
- **Configuration**: Spring Bean定義
- **Exception Handler**: HTTP エラーレスポンス変換
- **Security**: Spring Security統合

## Controller パターン

**情報源**: [ClientManagementV1Api.java:37-69](../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/control_plane/restapi/management/ClientManagementV1Api.java#L37-L69)

### 命名規則: {Domain}ManagementV1Api

```java
/**
 * Management API Controller
 * 確認方法: 実ファイルの37-69行目
 */
@RestController
@RequestMapping("/v1/management/tenants/{tenant-id}/clients")
public class ClientManagementV1Api implements ParameterTransformable {

  ClientManagementApi clientManagementApi;  // ✅ control-plane API

  public ClientManagementV1Api(IdpServerApplication idpServerApplication) {
    this.clientManagementApi = idpServerApplication.clientManagementApi();
  }

  @PostMapping
  public ResponseEntity<?> post(
      @AuthenticationPrincipal OperatorPrincipal operatorPrincipal,  // ✅ Spring Security統合
      @PathVariable("tenant-id") TenantIdentifier tenantIdentifier,  // ✅ 型安全
      @RequestBody(required = false) Map<String, Object> body,
      @RequestParam(value = "dry_run", required = false, defaultValue = "false") boolean dryRun,
      HttpServletRequest httpServletRequest) {

    // ✅ Phase 1: RequestAttributes変換
    RequestAttributes requestAttributes = transform(httpServletRequest);

    // ✅ Phase 2: Control-Plane API呼び出し
    ClientManagementResponse response =
        clientManagementApi.create(
            tenantIdentifier,
            operatorPrincipal.getUser(),
            operatorPrincipal.getOAuthToken(),
            new ClientRegistrationRequest(body),
            requestAttributes,
            dryRun);

    // ✅ Phase 3: レスポンス生成
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("content-type", "application/json");
    return new ResponseEntity<>(
        response.contents(),
        httpHeaders,
        HttpStatus.valueOf(response.statusCode()));
  }
}
```

**重要ポイント**:
- ✅ **命名規則**: `{Domain}ManagementV1Api` (例: `ClientManagementV1Api`, `UserManagementV1Api`)
- ✅ **implements ParameterTransformable**: HttpServletRequest → RequestAttributes変換
- ✅ **@AuthenticationPrincipal OperatorPrincipal**: Spring Securityで認証済みオペレーター取得
- ✅ **TenantIdentifier型**: `@PathVariable("tenant-id")` で型安全なパス変数
- ✅ **Control-Plane API呼び出し**: EntryServiceではなく、control-plane定義のAPIを使用
- ❌ **ロジック禁止**: HTTP → DTO変換のみ、ビジネスロジックは一切含まない

**責務の明確な分離**:
```
Controller (V1Api)
  ↓ HTTP → RequestAttributes変換
Control-Plane API
  ↓ EntryService呼び出し（Proxyでラップ）
EntryService (UseCase層)
  ↓ Handler/Service呼び出し
Core層
```

## Configuration

```java
@Configuration
public class DataSourceConfiguration {

  @Bean
  public ClientConfigurationQueryRepository clientConfigurationQueryRepository(
      DataSource dataSource) {
    ClientConfigurationSqlExecutor executor = new PostgresqlExecutor(dataSource);
    return new ClientConfigurationQueryDataSource(executor);
  }

  @Bean
  public ClientConfigurationCommandRepository clientConfigurationCommandRepository(
      DataSource dataSource) {
    ClientConfigurationSqlExecutor executor = new PostgresqlExecutor(dataSource);
    return new ClientConfigurationCommandDataSource(executor);
  }
}
```

## Exception Handler

**情報源**: [ApiExceptionHandler.java:34](../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/ApiExceptionHandler.java#L34)

```java
/**
 * グローバル例外ハンドラー
 * 確認方法: 実ファイルの34-100行目
 */
@ControllerAdvice
public class ApiExceptionHandler {

  LoggerWrapper log = LoggerWrapper.getLogger(ApiExceptionHandler.class);

  // ✅ BadRequestException → 400
  @ExceptionHandler(BadRequestException.class)
  public ResponseEntity<?> handleException(BadRequestException exception) {
    log.warn(exception.getMessage(), exception);
    Map<String, String> response =
        Map.of("error", "invalid_request", "error_description", exception.getMessage());
    return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
  }

  // ✅ UnauthorizedException → 401
  @ExceptionHandler(UnauthorizedException.class)
  public ResponseEntity<?> handleException(UnauthorizedException exception) {
    log.warn(exception.getMessage());
    Map<String, String> response =
        Map.of("error", "invalid_request", "error_description", exception.getMessage());
    return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
  }

  // ✅ ForbiddenException → 403
  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<?> handleException(ForbiddenException exception) {
    log.warn(exception.getMessage());
    Map<String, String> response =
        Map.of("error", "invalid_request", "error_description", exception.getMessage());
    return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
  }

  // ✅ NotFoundException → 404
  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<?> handleException(NotFoundException exception) {
    log.warn(exception.getMessage());
    Map<String, String> response =
        Map.of("error", "invalid_request", "error_description", exception.getMessage());
    return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
  }

  // ✅ ConflictException → 409
  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<?> handleException(ConflictException exception) {
    log.warn(exception.getMessage());
    Map<String, String> response =
        Map.of("error", "invalid_request", "error_description", exception.getMessage());
    return new ResponseEntity<>(response, HttpStatus.CONFLICT);
  }

  // ✅ SqlDuplicateKeyException → 409
  @ExceptionHandler(SqlDuplicateKeyException.class)
  public ResponseEntity<?> handleException(SqlDuplicateKeyException exception) {
    log.warn(exception.getMessage());
    Map<String, String> response =
        Map.of("error", "duplicate_key", "error_description", exception.getMessage());
    return new ResponseEntity<>(response, HttpStatus.CONFLICT);
  }

  // ✅ HttpMethodNotSupported → 405
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<?> handleException(HttpRequestMethodNotSupportedException exception) {
    log.warn(exception.getMessage(), exception);
    Map<String, String> response =
        Map.of("error", "invalid_request", "error_description", exception.getMessage());
    return new ResponseEntity<>(response, HttpStatus.METHOD_NOT_ALLOWED);
  }
}
```

**例外 → HTTPステータスマッピング**:

| 例外 | HTTPステータス | エラーコード |
|------|--------------|-------------|
| `BadRequestException` | 400 Bad Request | `invalid_request` |
| `UnauthorizedException` | 401 Unauthorized | `invalid_request` |
| `ForbiddenException` | 403 Forbidden | `invalid_request` |
| `NotFoundException` | 404 Not Found | `invalid_request` |
| `ConflictException` | 409 Conflict | `invalid_request` |
| `SqlDuplicateKeyException` | 409 Conflict | `duplicate_key` |
| `HttpRequestMethodNotSupportedException` | 405 Method Not Allowed | `invalid_request` |

## フィルター

### ManagementApiFilter - 管理API認証

```java
@Component
public class ManagementApiFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) {

    // 1. アクセストークン検証
    String authorization = request.getHeader("Authorization");
    OAuthToken oAuthToken = validateAccessToken(authorization);

    // 2. 権限検証
    User operator = extractOperator(oAuthToken);

    // 3. リクエスト属性に設定
    request.setAttribute("operator", operator);
    request.setAttribute("oAuthToken", oAuthToken);

    filterChain.doFilter(request, response);
  }
}
```

### OrgManagementFilter - 組織管理API認証

組織レベルAPIの認証・認可。

### DynamicCorsFilter - CORS動的設定

テナント固有のCORS設定を動的に適用。

**情報源**: [DynamicCorsFilter.java](../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/DynamicCorsFilter.java)

## 関連ドキュメント

- [Adapter層統合ドキュメント](./ai-20-adapters.md) - springboot-adapterを含む全アダプターモジュール
- [idp-server-core-adapter](./ai-21-core-adapter.md) - Repository実装
- [idp-server-database](./ai-22-database.md) - データベーススキーマ

---

**情報源**:
- `libs/idp-server-springboot-adapter/src/main/java/`配下の実装コード
- [ClientManagementV1Api.java](../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/control_plane/restapi/management/ClientManagementV1Api.java)
- [ApiExceptionHandler.java](../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/ApiExceptionHandler.java)

**最終更新**: 2025-10-12
