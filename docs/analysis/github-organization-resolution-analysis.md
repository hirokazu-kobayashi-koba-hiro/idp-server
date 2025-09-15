# GitHub の組織解決メカニズム分析

## GitHub の認証・認可システム

### 1. Personal Access Token (PAT)
```bash
# ユーザー単位で発行されるトークン
curl -H "Authorization: token ghp_xxxxxxxxxxxx" \
     https://api.github.com/orgs/microsoft/repos

# 特徴:
- ユーザーに紐づくトークン
- そのユーザーがアクセス可能な全組織にアクセス可能
- URLパスで組織を指定してもトークンの権限内でのみ動作
```

### 2. GitHub Apps
```bash
# Installation単位（組織単位）で発行される
curl -H "Authorization: token ghs_xxxxxxxxxxxx" \
     -H "Accept: application/vnd.github.v3+json" \
     https://api.github.com/orgs/microsoft/repos

# 特徴:
- GitHub Appが特定の組織にインストールされる
- Installation Tokenは該当組織のみアクセス可能
- URLパスの組織とトークンの組織が自動的に一致
```

### 3. OAuth Apps
```bash
# ユーザー認証後に発行される
curl -H "Authorization: token gho_xxxxxxxxxxxx" \
     https://api.github.com/orgs/microsoft/repos

# 特徴:
- ユーザー承認時にscopeとorganizationを指定
- 承認された組織のみアクセス可能
- URLパスとトークン権限の組み合わせで制御
```

## GitHub の具体的な動作メカニズム

### Personal Access Token のケース
```bash
# 1. トークン作成時にスコープ指定
Scopes: repo, read:org, read:user

# 2. API呼び出し
GET /orgs/microsoft/repos
Authorization: token ghp_xxxxxxxxxxxx

# 3. GitHub側での処理フロー
1. トークンの有効性検証
2. トークン所有者（ユーザー）の特定
3. ユーザーのmicrosoft組織へのアクセス権限チェック
   - 組織のメンバーか？
   - パブリック情報のみか？
   - 管理者権限があるか？
4. スコープ権限の確認
5. リソースへのアクセス許可
```

### GitHub Apps のケース（より興味深い）
```javascript
// GitHub App Installation の流れ

1. GitHub App作成（開発者）
   - Permissions: Repository access, Organization permissions
   - Webhook URL設定

2. 組織への App インストール（組織管理者）
   - Install App to Organization
   - Repository選択（All repositories / Selected repositories）

3. Installation Token取得（アプリケーション）
POST /app/installations/{installation_id}/access_tokens
Authorization: Bearer JWT（App private keyで署名）

Response:
{
  "token": "ghs_xxxxxxxxxxxx",
  "expires_at": "2023-12-31T23:59:59Z",
  "repositories": [...],  // アクセス可能なリポジトリ
  "account": {            // インストール先組織情報
    "login": "microsoft",
    "id": 6154722,
    "type": "Organization"
  }
}

4. API呼び出し（Installation Token使用）
GET /orgs/microsoft/repos
Authorization: token ghs_xxxxxxxxxxxx

# このトークンは microsoft 組織にのみ有効
# 他の組織（例：google）へのアクセスは自動的に拒否される
```

### OAuth Apps のケース
```javascript
// OAuth認証フロー

1. 認証URL生成
https://github.com/login/oauth/authorize
  ?client_id=your_client_id
  &scope=repo read:org
  &redirect_uri=https://yourapp.com/callback

2. ユーザー認証とスコープ承認
   - GitHubにログイン
   - アプリケーションが要求する権限を承認
   - 組織単位での権限承認（組織によっては管理者承認必要）

3. Authorization Code取得
GET https://yourapp.com/callback?code=authorization_code

4. Access Token交換
POST https://github.com/login/oauth/access_token
{
  "client_id": "your_client_id",
  "client_secret": "your_client_secret",
  "code": "authorization_code"
}

Response:
{
  "access_token": "gho_xxxxxxxxxxxx",
  "scope": "repo,read:org",
  "token_type": "bearer"
}

5. API呼び出し
GET /orgs/microsoft/repos
Authorization: token gho_xxxxxxxxxxxx

# GitHub側での処理:
- ユーザーのmicrosoft組織への権限確認
- OAuth承認時の組織権限確認
- アクセス許可/拒否
```

## GitHub の権限チェックロジック

### 実際のアルゴリズム（推測）
```javascript
function checkAccess(token, organizationLogin, resource, action) {
  // 1. トークン検証
  const tokenInfo = validateToken(token);
  if (!tokenInfo.valid) return false;

  // 2. トークンタイプ別処理
  switch (tokenInfo.type) {
    case 'personal_access_token':
      return checkPersonalTokenAccess(tokenInfo.user, organizationLogin, resource, action);

    case 'installation_token':
      return checkInstallationTokenAccess(tokenInfo.installation, organizationLogin, resource, action);

    case 'oauth_token':
      return checkOAuthTokenAccess(tokenInfo.user, tokenInfo.authorizedOrgs, organizationLogin, resource, action);
  }
}

function checkPersonalTokenAccess(user, orgLogin, resource, action) {
  // 1. ユーザーの組織メンバーシップ確認
  const membership = getUserOrgMembership(user.id, orgLogin);

  // 2. 組織の可視性確認
  const org = getOrganization(orgLogin);
  if (!org.public && !membership) return false;

  // 3. リソース別権限確認
  switch (resource) {
    case 'repositories':
      return checkRepoAccess(user, org, membership, action);
    case 'members':
      return checkMemberAccess(user, org, membership, action);
    // ...
  }
}

function checkInstallationTokenAccess(installation, orgLogin, resource, action) {
  // Installation Tokenはインストールされた組織でのみ有効
  if (installation.account.login !== orgLogin) return false;

  // インストール時に許可されたpermissionsを確認
  return installation.permissions[resource]?.includes(action);
}
```

### 重要な気づき

#### 1. **GitHub Apps は組織スコープトークン**
- Installation Tokenは特定組織にのみ有効
- URLパスの組織とトークンの組織が必然的に一致
- **これがidp-serverで実現したい理想的な状態**

#### 2. **Personal/OAuth Token は ユーザースコープ**
- ユーザーがアクセス可能な全組織にアクセス可能
- URLパスで組織を指定してもトークンの権限範囲内
- API呼び出し時にユーザー権限をチェック

#### 3. **組織権限の動的チェック**
```sql
-- GitHub内部的な権限チェック（推測）
SELECT om.role
FROM organization_memberships om
WHERE om.user_id = ? AND om.organization_id = ?;

-- 結果により権限判定
-- null: 非メンバー（パブリックリソースのみ）
-- 'member': 一般メンバー権限
-- 'admin': 管理者権限
```

## idp-server への応用

### GitHub Apps パターン（推奨）
```java
// 組織管理者向けトークン（GitHub Installation Token相当）
{
  "iss": "https://idp-server.example.com",
  "sub": "admin-user-123",
  "tenant_id": "admin-tenant-456",
  "organization_id": "org-123",        // 固定の組織ID
  "organization_role": "admin",        // 組織内での役割
  "scope": "org:management org:tenant:admin"
}

// このトークンはorg-123でのみ有効
// 他の組織へのアクセスは自動的に拒否
```

### Personal Token パターン（システム管理者）
```java
// システム管理者トークン（GitHub Personal Token相当）
{
  "iss": "https://idp-server.example.com",
  "sub": "system-admin-789",
  "tenant_id": "system-tenant-000",
  "organizations": ["org-123", "org-456", "org-789"], // アクセス可能組織一覧
  "scope": "system:management"
}

// 複数組織にアクセス可能
// API呼び出し時に組織別権限をチェック
```

### 実装アルゴリズム
```java
public class GitHubStyleAccessControl {

  public boolean checkAccess(OAuthToken token, String orgId, String resource, String action) {
    // 1. トークンタイプ判定
    if (token.hasOrganizationScope()) {
      // GitHub Apps style: 組織固定トークン
      return checkOrganizationScopedAccess(token, orgId, resource, action);
    } else {
      // Personal Token style: ユーザースコープトークン
      return checkUserScopedAccess(token, orgId, resource, action);
    }
  }

  private boolean checkOrganizationScopedAccess(OAuthToken token, String orgId,
                                               String resource, String action) {
    // 組織固定トークンは該当組織でのみ有効
    String tokenOrgId = token.getOrganizationId();
    if (!orgId.equals(tokenOrgId)) {
      return false; // 他組織へのアクセス拒否
    }

    // トークンに含まれる権限で判定
    return token.hasPermission(resource + ":" + action);
  }

  private boolean checkUserScopedAccess(OAuthToken token, String orgId,
                                       String resource, String action) {
    // ユーザーの組織メンバーシップを動的チェック
    User user = getUserFromToken(token);
    OrganizationMembership membership = organizationRepo.findMembership(user.id(), orgId);

    if (membership == null) {
      return false; // 組織メンバーでない
    }

    // 組織内での権限チェック
    return hasOrganizationPermission(membership, resource, action);
  }
}
```

## 結論

**GitHub の成功パターンをidp-serverに適用するなら:**

### 1. **組織管理者トークン**（GitHub Apps方式）
- 特定組織に固定されたトークン
- JWTクレームに`organization_id`を含める
- URLパス組織とトークン組織の不一致は自動拒否

### 2. **システム管理者トークン**（Personal Token方式）
- 複数組織アクセス可能
- API呼び出し時に動的権限チェック
- 組織メンバーシップとロールベースの制御

### 3. **権限チェックの実装**
```java
// GitHubスタイルの権限チェック
if (token.isOrganizationScoped()) {
  // 組織固定トークン: シンプル
  return token.organizationId.equals(requestedOrgId)
         && token.hasPermission(requiredPermission);
} else {
  // ユーザースコープトークン: 動的チェック
  return user.hasOrganizationMembership(requestedOrgId)
         && user.hasOrganizationPermission(requestedOrgId, requiredPermission);
}
```

**最大の学び**: GitHubはトークン発行時に組織スコープを決定し、API呼び出し時はその範囲内でのみ動作させている。idp-serverでも同様に、組織管理者には組織固定トークンを発行するのが最もシンプルで安全。