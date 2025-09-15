# ManagementApiFilter 組織対応 実装提案

## 1. リクエストから組織を特定する仕組み

### 提案A: URLパス解析による組織特定
```java
public class OrganizationResolver {
  private static final Pattern ORG_PATH_PATTERN =
    Pattern.compile("/management/organizations/([^/]+)/");

  public static OrganizationIdentifier resolveFromPath(String requestURI) {
    Matcher matcher = ORG_PATH_PATTERN.matcher(requestURI);
    if (matcher.find()) {
      return new OrganizationIdentifier(matcher.group(1));
    }
    return null; // 組織スコープ外の操作
  }
}
```

**URLパターン例:**
```
/management/organizations/org-123/tenants          → org-123
/management/organizations/org-123/users           → org-123
/management/organizations/org-123/clients         → org-123
/management/system/settings                       → null (システム管理)
```

### 提案B: HTTPヘッダーによる組織指定
```java
public class OrganizationResolver {
  private static final String ORG_HEADER = "X-Organization-Id";

  public static OrganizationIdentifier resolveFromHeader(HttpServletRequest request) {
    String orgId = request.getHeader(ORG_HEADER);
    return orgId != null ? new OrganizationIdentifier(orgId) : null;
  }
}
```

**ヘッダー例:**
```
X-Organization-Id: org-123
```

### 推奨: ハイブリッドアプローチ
```java
public class OrganizationResolver {
  public static OrganizationIdentifier resolve(HttpServletRequest request) {
    // 1. URLパスから組織を特定 (優先)
    OrganizationIdentifier fromPath = resolveFromPath(request.getRequestURI());
    if (fromPath != null) {
      return fromPath;
    }

    // 2. ヘッダーから組織を特定 (フォールバック)
    return resolveFromHeader(request);
  }
}
```

## 2. 組織に対応するadminテナントでの動的認証

### 現在の問題
```java
// 固定のadminテナント
TenantIdentifier adminTenantIdentifier = AdminTenantContext.getTenantIdentifier();
```

### 提案A: 組織-adminテナントマッピング
```java
public class OrganizationAdminTenantResolver {
  private final Map<OrganizationIdentifier, TenantIdentifier> orgToAdminTenantMap;

  public TenantIdentifier resolveAdminTenant(OrganizationIdentifier organizationId) {
    TenantIdentifier adminTenant = orgToAdminTenantMap.get(organizationId);
    if (adminTenant == null) {
      throw new IllegalArgumentException("No admin tenant found for organization: "
                                       + organizationId.value());
    }
    return adminTenant;
  }
}
```

### 提案B: データベースからの動的解決
```java
public class OrganizationAdminTenantResolver {
  private final OrganizationRepository organizationRepository;

  public TenantIdentifier resolveAdminTenant(OrganizationIdentifier organizationId) {
    Organization org = organizationRepository.findById(organizationId);
    if (org == null || org.adminTenantId() == null) {
      throw new IllegalArgumentException("Invalid organization or missing admin tenant: "
                                       + organizationId.value());
    }
    return org.adminTenantId();
  }
}
```

### 提案C: 複数adminテナントでの認証試行
```java
public class MultiTenantAuthenticator {
  public AuthenticationResult authenticate(String authorization, String clientCert,
                                          OrganizationIdentifier targetOrg) {
    // 1. 対象組織のadminテナントでの認証を試行
    if (targetOrg != null) {
      TenantIdentifier adminTenant = resolveAdminTenant(targetOrg);
      try {
        return authenticateWithTenant(adminTenant, authorization, clientCert, targetOrg);
      } catch (UnauthorizedException e) {
        // 認証失敗時は例外をスロー
        throw new UnauthorizedException("Authentication failed for organization: "
                                      + targetOrg.value());
      }
    }

    // 2. システム管理者としての認証 (組織スコープ外)
    TenantIdentifier systemAdminTenant = AdminTenantContext.getTenantIdentifier();
    return authenticateWithTenant(systemAdminTenant, authorization, clientCert, null);
  }

  private AuthenticationResult authenticateWithTenant(TenantIdentifier tenantId,
                                                     String authorization, String clientCert,
                                                     OrganizationIdentifier targetOrg) {
    Pairs<User, OAuthToken> result = userAuthenticationApi.authenticate(
        tenantId, authorization, clientCert);

    // 組織境界の検証
    if (targetOrg != null) {
      validateUserBelongsToOrganization(result.getLeft(), targetOrg);
    }

    return new AuthenticationResult(result.getLeft(), result.getRight(), tenantId, targetOrg);
  }
}
```

## 3. 組織コンテキスト付きのOperatorPrincipal

### 拡張されたOperatorPrincipal
```java
public class OrganizationAwareOperatorPrincipal extends OperatorPrincipal {
  private final OrganizationIdentifier organizationId;
  private final TenantIdentifier adminTenantId;
  private final OperatorRole operatorRole;

  public enum OperatorRole {
    SYSTEM_ADMIN,        // システム管理者 (全組織アクセス可能)
    ORGANIZATION_ADMIN   // 組織管理者 (特定組織のみ)
  }

  public OrganizationAwareOperatorPrincipal(
      User user, OAuthToken oAuthToken, List<IdpControlPlaneAuthority> authorities,
      OrganizationIdentifier organizationId, TenantIdentifier adminTenantId) {
    super(user, oAuthToken, authorities);
    this.organizationId = organizationId;
    this.adminTenantId = adminTenantId;
    this.operatorRole = organizationId != null ? OperatorRole.ORGANIZATION_ADMIN
                                               : OperatorRole.SYSTEM_ADMIN;
  }

  // 組織境界チェック
  public boolean canAccessOrganization(OrganizationIdentifier targetOrgId) {
    if (operatorRole == OperatorRole.SYSTEM_ADMIN) {
      return true; // システム管理者は全組織アクセス可能
    }
    return this.organizationId != null && this.organizationId.equals(targetOrgId);
  }

  // 組織レベル権限チェック
  public boolean hasOrganizationPermission(OrganizationAdminPermission permission) {
    return getUser().permissionsAsSet().contains(permission.value());
  }

  // テナントアクセス権限チェック
  public boolean canAccessTenant(TenantIdentifier tenantId) {
    if (operatorRole == OperatorRole.SYSTEM_ADMIN) {
      return true; // システム管理者は全テナントアクセス可能
    }

    // 組織管理者は自分の組織内のテナントのみアクセス可能
    // (実際の検証はOrganizationAccessVerifierで行う)
    return organizationId != null;
  }

  // Getters
  public OrganizationIdentifier getOrganizationId() { return organizationId; }
  public TenantIdentifier getAdminTenantId() { return adminTenantId; }
  public OperatorRole getOperatorRole() { return operatorRole; }
  public boolean isSystemAdmin() { return operatorRole == OperatorRole.SYSTEM_ADMIN; }
  public boolean isOrganizationAdmin() { return operatorRole == OperatorRole.ORGANIZATION_ADMIN; }
}
```

### 組織権限チェック用のヘルパー
```java
public class OrganizationPermissionChecker {

  public static void requireOrganizationPermission(OperatorPrincipal principal,
                                                  OrganizationAdminPermission permission) {
    if (!(principal instanceof OrganizationAwareOperatorPrincipal orgPrincipal)) {
      throw new AccessDeniedException("Organization context required");
    }

    if (!orgPrincipal.hasOrganizationPermission(permission)) {
      throw new AccessDeniedException("Missing required permission: " + permission.value());
    }
  }

  public static void requireOrganizationAccess(OperatorPrincipal principal,
                                              OrganizationIdentifier targetOrgId) {
    if (!(principal instanceof OrganizationAwareOperatorPrincipal orgPrincipal)) {
      throw new AccessDeniedException("Organization context required");
    }

    if (!orgPrincipal.canAccessOrganization(targetOrgId)) {
      throw new AccessDeniedException("Access denied to organization: " + targetOrgId.value());
    }
  }
}
```

## 4. 統合されたManagementApiFilter実装

```java
@Component
public class OrganizationAwareManagementApiFilter extends OncePerRequestFilter {

  private final UserAuthenticationApi userAuthenticationApi;
  private final OrganizationAdminTenantResolver adminTenantResolver;
  private final MultiTenantAuthenticator authenticator;
  private final LoggerWrapper logger = LoggerWrapper.getLogger(getClass());

  @Override
  protected void doFilterInternal(HttpServletRequest request,
                                 HttpServletResponse response,
                                 FilterChain filterChain) throws IOException {
    try {
      // 1. リクエストから組織を特定
      OrganizationIdentifier organizationId = OrganizationResolver.resolve(request);

      // 2. 認証実行 (組織に応じたadminテナントを使用)
      String authorization = request.getHeader("Authorization");
      String clientCert = request.getHeader("x-ssl-cert");

      AuthenticationResult authResult = authenticator.authenticate(
          authorization, clientCert, organizationId);

      // 3. トークン検証
      if (authResult.oAuthToken().isClientCredentialsGrant()) {
        sendUnauthorized(response, "Management API does not support client credentials grant");
        return;
      }

      // 4. スコープ検証
      List<IdpControlPlaneAuthority> scopes = extractScopes(authResult.oAuthToken());
      if (!hasRequiredManagementScope(scopes)) {
        sendForbidden(response, "Missing required management scope");
        return;
      }

      // 5. 組織レベル権限の検証
      if (organizationId != null) {
        Set<OrganizationAdminPermission> requiredPermissions =
            determineRequiredOrganizationPermissions(request.getRequestURI());
        if (!hasRequiredOrganizationPermissions(authResult.user(), requiredPermissions)) {
          sendForbidden(response, "Insufficient organization permissions");
          return;
        }
      }

      // 6. 組織コンテキスト付きPrincipalを作成
      OrganizationAwareOperatorPrincipal principal = new OrganizationAwareOperatorPrincipal(
          authResult.user(), authResult.oAuthToken(), scopes,
          organizationId, authResult.adminTenantId());

      // 7. SecurityContextに設定
      SecurityContextHolder.getContext().setAuthentication(principal);

      logger.debug("Authenticated {} for organization: {}",
                  principal.getOperatorRole(), organizationId);

      filterChain.doFilter(request, response);

    } catch (UnauthorizedException e) {
      sendUnauthorized(response, e.getMessage());
    } catch (AccessDeniedException e) {
      sendForbidden(response, e.getMessage());
    } catch (Exception e) {
      logger.error("Authentication failed", e);
      sendUnauthorized(response, "Authentication failed");
    }
  }

  private Set<OrganizationAdminPermission> determineRequiredOrganizationPermissions(String requestURI) {
    // URL パターンマッチングによる権限判定
    if (requestURI.contains("/tenants")) {
      if (requestURI.contains("POST")) return Set.of(OrganizationAdminPermission.ORG_TENANT_CREATE);
      if (requestURI.contains("PUT")) return Set.of(OrganizationAdminPermission.ORG_TENANT_UPDATE);
      if (requestURI.contains("DELETE")) return Set.of(OrganizationAdminPermission.ORG_TENANT_DELETE);
      return Set.of(OrganizationAdminPermission.ORG_TENANT_READ);
    }

    if (requestURI.contains("/users")) {
      // 同様のパターン
      return Set.of(OrganizationAdminPermission.ORG_USER_READ);
    }

    return Set.of(); // デフォルト権限なし
  }

  private boolean hasRequiredOrganizationPermissions(User user, Set<OrganizationAdminPermission> required) {
    Set<String> userPermissions = user.permissionsAsSet();
    return required.stream()
        .allMatch(permission -> userPermissions.contains(permission.value()));
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().contains("/management/");
  }
}
```

## 5. 使用例とフロー

### 組織管理者によるテナント作成の例
```
1. Request: POST /management/organizations/org-123/tenants
   Header: Authorization: Bearer xxx

2. OrganizationResolver.resolve()
   → OrganizationIdentifier("org-123")

3. adminTenantResolver.resolveAdminTenant(org-123)
   → TenantIdentifier("admin-tenant-456")

4. authenticator.authenticate("Bearer xxx", null, org-123)
   → User + OAuthToken + adminTenantId

5. determineRequiredOrganizationPermissions("/organizations/org-123/tenants")
   → Set.of(ORG_TENANT_CREATE)

6. hasRequiredOrganizationPermissions(user, {ORG_TENANT_CREATE})
   → true/false

7. OrganizationAwareOperatorPrincipal 作成
   → organizationId=org-123, adminTenantId=admin-tenant-456

8. SecurityContext設定 → 後続処理へ
```

### システム管理者による全体設定の例
```
1. Request: GET /management/system/settings
   Header: Authorization: Bearer xxx

2. OrganizationResolver.resolve()
   → null (組織スコープ外)

3. authenticator.authenticate("Bearer xxx", null, null)
   → システムadminテナントで認証

4. OrganizationAwareOperatorPrincipal作成
   → organizationId=null, role=SYSTEM_ADMIN

5. SecurityContext設定 → 後続処理へ
```

## 6. 実装のポイント

### エラーハンドリング
- 組織が見つからない場合: 404 Not Found
- 認証失敗: 401 Unauthorized
- 権限不足: 403 Forbidden
- システムエラー: 500 Internal Server Error

### パフォーマンス考慮
- adminテナント解決結果のキャッシュ
- 権限チェック結果のキャッシュ
- 組織-adminテナントマッピングのメモリキャッシュ

### セキュリティ考慮
- 組織境界の厳格な検証
- ログによる監査証跡の確保
- Rate limiting による DoS攻撃対策

## 7. マイグレーション戦略

### Phase 1: 基本実装
1. OrganizationResolver の実装
2. OrganizationAwareOperatorPrincipal の実装
3. 既存ManagementApiFilter の拡張

### Phase 2: 権限制御強化
1. 詳細な組織権限チェック
2. URL パターンベースの権限判定
3. 監査ログの拡充

### Phase 3: 最適化
1. パフォーマンスチューニング
2. エラーハンドリングの改善
3. 運用性の向上

この実装により、組織レベルでのセキュアな管理API提供が実現できます。