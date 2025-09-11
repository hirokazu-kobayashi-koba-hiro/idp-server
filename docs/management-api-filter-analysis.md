# ManagementApiFilter 組織レベル対応の課題分析

## 現在のManagementApiFilterの問題点

### 1. **単一AdminTenant固定**
```java
TenantIdentifier adminTenantIdentifier = AdminTenantContext.getTenantIdentifier();
// ↓ 1つのadminテナントでしか認証できない
Pairs<User, OAuthToken> result = userAuthenticationApi.authenticate(
    adminTenantIdentifier, authorization, clientCert);
```

**問題**:
- 組織ごとに異なるadminテナントがあっても、1つのadminテナントでしか認証できない
- 組織Aの管理者が組織Bのリソースにアクセスしてしまう可能性

### 2. **組織境界の認識不足**
```java
OperatorPrincipal operatorPrincipal = new OperatorPrincipal(user, oAuthToken, scopes);
// ↓ OperatorPrincipalに組織情報が含まれていない
SecurityContextHolder.getContext().setAuthentication(operatorPrincipal);
```

**問題**:
- リクエスト処理時に、どの組織の管理者として動作しているかが不明
- 後続の処理で組織境界チェックができない

### 3. **スコープ検証の不十分性**
```java
if (scopes.stream().noneMatch(authority ->
    authority.getAuthority().equals(IdpControlPlaneScope.management.name()))) {
    // managementスコープの有無のみチェック
}
```

**問題**:
- 組織レベルの権限（25個のOrganizationAdminPermission）が考慮されていない
- すべての管理者が同じ権限を持つと仮定している

## 組織レベル対応の必要な変更

### 1. **動的AdminTenant解決**

#### 現在
```java
// 固定のadminテナント
TenantIdentifier adminTenantIdentifier = AdminTenantContext.getTenantIdentifier();
```

#### 改善案
```java
// リクエストから組織を特定して対応するadminテナントを解決
OrganizationIdentifier orgId = resolveOrganizationFromRequest(request);
TenantIdentifier adminTenantIdentifier = resolveAdminTenant(orgId);

// または、複数のadminテナントでの認証を試行
List<TenantIdentifier> candidateAdminTenants = getAllAdminTenants();
Pairs<User, OAuthToken> result = authenticateWithCandidates(
    candidateAdminTenants, authorization, clientCert);
```

### 2. **組織コンテキストの追加**

#### 現在のOperatorPrincipal
```java
public class OperatorPrincipal {
  User user;
  OAuthToken oauthToken;
  // 組織情報なし
}
```

#### 改善案
```java
public class OrganizationOperatorPrincipal extends OperatorPrincipal {
  OrganizationIdentifier organizationId;
  TenantIdentifier adminTenantId;

  // 組織境界内での権限チェック
  public boolean hasOrganizationPermission(OrganizationAdminPermission permission) {
    return user.permissionsAsSet().contains(permission.value());
  }
}
```

### 3. **組織レベル権限の検証**

#### 現在
```java
// managementスコープのみチェック
if (scopes.stream().noneMatch(authority ->
    authority.getAuthority().equals(IdpControlPlaneScope.management.name()))) {
```

#### 改善案
```java
// リクエストパスから必要な組織権限を判定
String requestPath = request.getRequestURI();
Set<OrganizationAdminPermission> requiredPermissions =
    determineRequiredPermissions(requestPath);

// ユーザーが必要な組織権限を持っているかチェック
if (!hasRequiredOrganizationPermissions(user, requiredPermissions)) {
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    return;
}
```

## 具体的な実装アプローチ

### アプローチ1: フィルター内での組織解決
```java
@Override
protected void doFilterInternal(HttpServletRequest request,
                               HttpServletResponse response,
                               FilterChain filterChain) {

    // 1. リクエストから組織を特定
    OrganizationIdentifier orgId = extractOrganizationFromRequest(request);

    // 2. 組織に対応するadminテナントを解決
    TenantIdentifier adminTenantId = resolveAdminTenantForOrganization(orgId);

    // 3. 認証実行
    Pairs<User, OAuthToken> result = userAuthenticationApi.authenticate(
        adminTenantId, authorization, clientCert);

    // 4. 組織境界の検証
    validateUserBelongsToOrganization(result.getLeft(), orgId);

    // 5. 組織コンテキスト付きPrincipalを作成
    OrganizationOperatorPrincipal principal = new OrganizationOperatorPrincipal(
        result.getLeft(), result.getRight(), scopes, orgId, adminTenantId);
}
```

### アプローチ2: 複数adminテナントでの認証試行
```java
private Pairs<User, OAuthToken> authenticateWithMultipleAdminTenants(
    String authorization, String clientCert) {

    List<TenantIdentifier> adminTenants = getAllAdminTenants();

    for (TenantIdentifier adminTenant : adminTenants) {
        try {
            Pairs<User, OAuthToken> result = userAuthenticationApi.authenticate(
                adminTenant, authorization, clientCert);

            // 認証成功したらorganization情報を特定
            OrganizationIdentifier orgId = determineOrganization(result.getLeft(), adminTenant);
            return Pairs.of(result.getLeft().withOrganizationContext(orgId), result.getRight());

        } catch (UnauthorizedException e) {
            // 次のadminテナントで試行
            continue;
        }
    }

    throw new UnauthorizedException("No valid admin tenant found");
}
```

## リクエストからの組織特定方法

### Method 1: URLパス解析
```java
// /management/organizations/{orgId}/tenants のようなパス
private OrganizationIdentifier extractOrganizationFromRequest(HttpServletRequest request) {
    String path = request.getRequestURI();
    Pattern pattern = Pattern.compile("/management/organizations/([^/]+)/");
    Matcher matcher = pattern.matcher(path);

    if (matcher.find()) {
        return new OrganizationIdentifier(matcher.group(1));
    }

    return null; // 組織スコープでない操作
}
```

### Method 2: ヘッダー指定
```java
// X-Organization-Id ヘッダーからorganization特定
private OrganizationIdentifier extractOrganizationFromRequest(HttpServletRequest request) {
    String orgId = request.getHeader("X-Organization-Id");
    return orgId != null ? new OrganizationIdentifier(orgId) : null;
}
```

## 推奨する改善戦略

### Phase 1: 最小限の拡張
1. **OperatorPrincipalの拡張** - 組織情報を追加
2. **組織境界の基本チェック** - リクエスト時の組織検証
3. **既存APIとの互換性維持**

### Phase 2: 詳細権限制御
1. **組織レベル権限の詳細チェック**
2. **パス別の権限要件定義**
3. **監査ログの拡充**

### Phase 3: 統合とパフォーマンス最適化
1. **認証キャッシュの最適化**
2. **組織解決の高速化**
3. **エラーハンドリングの統一**

## 結論

ManagementApiFilterは組織レベルのテナント管理において重要な入り口となるため、以下の対応が必要：

1. **組織コンテキストの認識** - リクエストから組織を特定
2. **適切なadminテナントでの認証** - 組織に対応するadminテナントの使用
3. **組織境界の強制** - 異なる組織へのアクセス防止
4. **詳細権限制御** - 25個のOrganizationAdminPermissionの活用

これらにより、組織レベルでのセキュアな管理API提供が可能になります。