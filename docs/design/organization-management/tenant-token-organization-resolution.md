# テナント発行トークンでの組織特定方法

## 現状の課題

### アクセストークン発行の制約
```
1. アクセストークンはテナント単位で発行される
2. 組織管理者も特定のテナント（adminテナント）からトークンを取得
3. トークンには発行テナントの情報のみ含まれる
4. URLパスで組織を指定しても、トークンとの整合性チェックが必要
```

### 問題となるケース
```java
// 組織A（org-123）のadminテナント（admin-tenant-456）で発行されたトークン
Authorization: Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9...
// このトークンで組織Bのリソースにアクセスしようとした場合
GET /management/organizations/org-999/tenants  // ← 不正アクセス
```

## 解決アプローチの検討

### アプローチ1: トークンベース組織解決
```java
// JWTトークンに組織情報を埋め込む
{
  "iss": "https://idp-server.example.com",
  "sub": "admin-user-123",
  "tenant_id": "admin-tenant-456",
  "organization_id": "org-123",  // ← 組織情報を含める
  "permissions": ["org-tenant:read", "org-tenant:create"],
  "scope": "management"
}

// ManagementApiFilterでの検証
public class OrganizationResolver {
  public static OrganizationIdentifier resolveFromToken(OAuthToken token) {
    // JWTペイロードから組織IDを取得
    String organizationId = token.getClaim("organization_id");
    return organizationId != null ? new OrganizationIdentifier(organizationId) : null;
  }

  public static void validateConsistency(String pathOrgId, String tokenOrgId) {
    if (pathOrgId != null && !pathOrgId.equals(tokenOrgId)) {
      throw new AccessDeniedException("Organization mismatch: path=" + pathOrgId +
                                     ", token=" + tokenOrgId);
    }
  }
}
```

### アプローチ2: テナント-組織マッピング
```java
// データベースから動的にテナントの組織を解決
public class TenantOrganizationResolver {
  private final OrganizationRepository organizationRepository;

  public OrganizationIdentifier resolveFromTenant(TenantIdentifier tenantId) {
    // データベースから該当テナントが属する組織を検索
    Organization org = organizationRepository.findByAdminTenantId(tenantId);
    return org != null ? org.organizationId() : null;
  }
}

// ManagementApiFilterでの使用
OrganizationIdentifier tokenOrg = tenantOrgResolver.resolveFromTenant(
  new TenantIdentifier(token.getTenantId()));
OrganizationIdentifier pathOrg = OrganizationResolver.parseFromUrl(request.getRequestURI());

// 整合性チェック
if (pathOrg != null && !pathOrg.equals(tokenOrg)) {
  throw new AccessDeniedException("Organization access denied");
}
```

### アプローチ3: ハイブリッド方式（推奨）
```java
public class HybridOrganizationResolver {

  public OrganizationContext resolve(HttpServletRequest request, OAuthToken token) {
    // 1. トークンから組織情報を取得（優先）
    OrganizationIdentifier tokenOrg = extractFromToken(token);

    // 2. URLパスから組織情報を取得
    OrganizationIdentifier pathOrg = extractFromPath(request.getRequestURI());

    // 3. テナントから組織をマッピング（フォールバック）
    OrganizationIdentifier mappedOrg = mapFromTenant(token.getTenantId());

    return resolveWithPriority(tokenOrg, pathOrg, mappedOrg);
  }

  private OrganizationContext resolveWithPriority(
      OrganizationIdentifier tokenOrg,
      OrganizationIdentifier pathOrg,
      OrganizationIdentifier mappedOrg) {

    // Case 1: トークンに組織情報あり（理想的）
    if (tokenOrg != null) {
      if (pathOrg != null && !pathOrg.equals(tokenOrg)) {
        throw new AccessDeniedException("Organization mismatch");
      }
      return OrganizationContext.fromToken(tokenOrg);
    }

    // Case 2: URLパスに組織情報あり（検証必要）
    if (pathOrg != null) {
      if (mappedOrg != null && !pathOrg.equals(mappedOrg)) {
        throw new AccessDeniedException("Unauthorized organization access");
      }
      return OrganizationContext.fromPath(pathOrg, mappedOrg);
    }

    // Case 3: テナントマッピングのみ（制限的）
    if (mappedOrg != null) {
      return OrganizationContext.fromMapping(mappedOrg);
    }

    // Case 4: システム管理者（組織スコープ外）
    return OrganizationContext.systemAdmin();
  }
}
```

## トークン拡張の実装

### JWT クレームの拡張
```java
// TokenCreationService の拡張
public class OrganizationAwareTokenCreationService {

  public OAuthToken createToken(User user, TenantIdentifier tenantId,
                               Set<String> scopes) {
    // 既存のトークン作成
    OAuthTokenBuilder builder = OAuthToken.builder()
        .subject(user.userIdentifier().value())
        .tenantId(tenantId.value())
        .scopes(scopes);

    // 組織情報の追加
    OrganizationIdentifier orgId = resolveUserOrganization(user, tenantId);
    if (orgId != null) {
      builder.claim("organization_id", orgId.value());

      // 組織レベル権限の追加
      Set<String> orgPermissions = extractOrganizationPermissions(user, orgId);
      if (!orgPermissions.isEmpty()) {
        builder.claim("organization_permissions", orgPermissions);
      }
    }

    return builder.build();
  }

  private OrganizationIdentifier resolveUserOrganization(User user,
                                                        TenantIdentifier tenantId) {
    // ユーザーの組織割り当てから判定
    if (user.hasAssignedOrganizations()) {
      // adminテナントから組織を特定
      return organizationRepository.findByAdminTenant(tenantId);
    }
    return null;
  }
}
```

### スコープベース組織制御
```java
// 組織スコープの導入
public enum OrganizationScope {
  ORG_MANAGEMENT("org:management"),
  ORG_TENANT_ADMIN("org:tenant:admin"),
  ORG_USER_ADMIN("org:user:admin");

  private final String value;
}

// トークン発行時の組織スコープ付与
Set<String> scopes = Set.of(
  "management",
  "org:management",
  "org:tenant:admin"
);
```

## API設計の最適化

### URLパターンの統一
```java
// 組織コンテキスト必須のAPI
GET /management/organizations/{org-id}/tenants
GET /management/organizations/{org-id}/users
GET /management/organizations/{org-id}/clients

// 組織コンテキスト不要のAPI（システム管理）
GET /management/system/organizations
GET /management/system/settings

// 自動組織解決API（トークンベース）
GET /management/my-organization/tenants    // トークンから組織を自動解決
GET /management/my-organization/users
```

### リクエスト処理フロー
```java
@RestController
@RequestMapping("/management/organizations/{orgId}")
public class OrganizationTenantController {

  @GetMapping("/tenants")
  public ResponseEntity<List<Tenant>> getTenants(
      @PathVariable String orgId,
      Authentication auth) {

    // 1. 認証済みユーザーの組織コンテキスト取得
    OrganizationAwareOperatorPrincipal principal =
        (OrganizationAwareOperatorPrincipal) auth;

    // 2. パス指定の組織とトークンの組織を検証
    OrganizationIdentifier requestedOrg = new OrganizationIdentifier(orgId);
    if (!principal.canAccessOrganization(requestedOrg)) {
      throw new AccessDeniedException("Organization access denied");
    }

    // 3. サービス呼び出し
    return tenantService.findByOrganization(requestedOrg);
  }
}
```

## 段階的実装戦略

### Phase 1: トークン拡張
```java
// 1. JWT クレームに組織情報追加
// 2. OrganizationAwareOperatorPrincipal 実装
// 3. 基本的な組織-パス整合性チェック
```

### Phase 2: API統一
```java
// 1. URLパターンの統一
// 2. 組織スコープの導入
// 3. 自動組織解決APIの追加
```

### Phase 3: 高度な制御
```java
// 1. 細かい権限制御
// 2. 監査ログの拡充
// 3. パフォーマンス最適化
```

## 実装例

### ManagementApiFilterの最終形
```java
@Override
protected void doFilterInternal(HttpServletRequest request,
                               HttpServletResponse response,
                               FilterChain filterChain) throws IOException {
  try {
    // 1. 認証
    String authorization = request.getHeader("Authorization");
    AuthenticationResult authResult = authenticator.authenticate(authorization);

    // 2. 組織コンテキスト解決
    OrganizationContext orgContext = hybridResolver.resolve(request, authResult.token());

    // 3. 権限検証
    validateOrganizationPermissions(request.getRequestURI(), orgContext, authResult.user());

    // 4. Principal設定
    OrganizationAwareOperatorPrincipal principal = new OrganizationAwareOperatorPrincipal(
        authResult.user(), authResult.token(), orgContext);

    SecurityContextHolder.getContext().setAuthentication(principal);

    filterChain.doFilter(request, response);

  } catch (Exception e) {
    handleException(response, e);
  }
}
```

## 結論

**テナント発行トークンでの組織管理には、トークンクレーム拡張 + URLパス検証のハイブリッド方式が最適**

### 主要な利点:
1. **セキュリティ** - トークンとパスの二重検証
2. **柔軟性** - 段階的な実装が可能
3. **明示性** - URLから組織スコープが明確
4. **後方互換** - 既存APIとの共存

### 実装の要点:
- JWTクレームに`organization_id`を追加
- URLパスとトークン組織の整合性検証
- 組織スコープベースの権限制御
- システム管理者と組織管理者の明確な分離