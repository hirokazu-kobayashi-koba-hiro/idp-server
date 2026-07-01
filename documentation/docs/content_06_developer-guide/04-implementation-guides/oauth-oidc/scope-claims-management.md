# Scope & Claims

## 📍 このドキュメントの位置づけ

**対象読者**: OAuth 2.0/OIDC のスコープ・クレーム管理の実装詳細を理解したい開発者

**このドキュメントで学べること**:
- スコープ管理の実装パターン
- IDトークン・アクセストークン・Userinfoでのクレーム生成
- カスタムクレームプラグインの実装方法
- `claims:`/`verified_claims:`プレフィックスの仕組み
- 標準クレームのスコープマッピング

**前提知識**:
- [basic-12: OpenID Connect詳解](../../content_11_learning/04-openid-connect/openid-connect-detail.md)の理解
- [concept-09: カスタムクレーム](../../content_03_concepts/04-tokens-claims/concept-03-custom-claims.md)の理解
- [impl-12: Plugin実装ガイド](./impl-12-plugin-implementation.md)の理解

---

## 🏗️ スコープとクレームの関係

### OAuth 2.0におけるScope

**Scope（スコープ）**は、アクセス権限の範囲を定義する文字列リストです。

```java
public class Scopes implements Iterable<String> {
  Set<String> values;  // スペース区切り文字列を Set で管理

  public Scopes(String value) {
    // "openid profile email" → Set("openid", "profile", "email")
    this.values = Arrays.stream(value.split(" ")).collect(Collectors.toSet());
  }

  public boolean contains(String scope) {
    return values.contains(scope);
  }

  public boolean hasOpenidScope() {
    return values.contains("openid");
  }

  public Scopes filterMatchedPrefix(String prefix) {
    // 特定プレフィックスにマッチするスコープをフィルタ
    Set<String> filteredValues =
        values.stream().filter(value -> value.startsWith(prefix))
              .collect(Collectors.toSet());
    return new Scopes(filteredValues);
  }
}
```

**参考実装**: [Scopes.java:47](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/type/oauth/Scopes.java#L47)

**主なメソッド**:
- `contains(String scope)` - スコープが含まれているか確認
- `hasOpenidScope()` - `openid`スコープの有無確認
- `hasScopeMatchedPrefix(String prefix)` - プレフィックスマッチング
- `filterMatchedPrefix(String prefix)` - プレフィックスでフィルタリング
- `removeScopes(DeniedScopes)` - 拒否スコープの除外

### OpenID ConnectにおけるClaim

**Claim（クレーム）**は、IDトークンやUserinfoで返されるユーザー属性情報です。

```java
public class Claims {
  Set<String> values;

  public Claims(String value) {
    // "name email phone_number" → Set("name", "email", "phone_number")
    this.values = Arrays.stream(value.split(" ")).collect(Collectors.toSet());
  }

  public boolean contains(String claim) {
    return values.contains(claim);
  }
}
```

**参考実装**: [Claims.java:26](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/type/oidc/Claims.java#L26)

---

## 📋 標準スコープとクレームのマッピング

OIDC Core仕様では、スコープに対応するクレームセットが定義されています。

### 標準マッピング

| Scope | 返されるClaims |
|-------|---------------|
| `openid` | `sub` |
| `profile` | `name`, `family_name`, `given_name`, `middle_name`, `nickname`, `preferred_username`, `profile`, `picture`, `website`, `gender`, `birthdate`, `zoneinfo`, `locale`, `updated_at` |
| `email` | `email`, `email_verified` |
| `phone` | `phone_number`, `phone_number_verified` |
| `address` | `address` |

### 実装: IndividualClaimsCreatable

標準クレームの生成は`IndividualClaimsCreatable`インターフェースで実装されています。

```java
public interface IndividualClaimsCreatable extends ClaimHashable {

  default Map<String, Object> createIndividualClaims(
      User user,
      GrantIdTokenClaims idTokenClaims,
      boolean idTokenStrictMode,
      RequestedIdTokenClaims requestedIdTokenClaims) {

    HashMap<String, Object> claims = new HashMap<>();

    // sub は常に含まれる
    claims.put("sub", user.sub());

    // profile スコープ
    if (idTokenClaims.hasName() && user.hasName()) {
      claims.put("name", user.name());
    }
    if (idTokenClaims.hasGivenName() && user.hasGivenName()) {
      claims.put("given_name", user.givenName());
    }
    if (idTokenClaims.hasFamilyName() && user.hasFamilyName()) {
      claims.put("family_name", user.familyName());
    }
    // ...その他のprofileクレーム

    // email スコープ
    if (idTokenClaims.hasEmail() && user.hasEmail()) {
      claims.put("email", user.email());
    }
    if (idTokenClaims.hasEmailVerified() && user.hasEmailVerified()) {
      claims.put("email_verified", user.emailVerified());
    }

    // phone スコープ
    if (idTokenClaims.hasPhoneNumber() && user.hasPhoneNumber()) {
      claims.put("phone_number", user.phoneNumber());
    }

    // address スコープ
    if (idTokenClaims.hasAddress() && user.hasAddress()) {
      claims.put("address", user.address().toMap());
    }

    return claims;
  }
}
```

**参考実装**: [IndividualClaimsCreatable.java:25](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/identity/id_token/IndividualClaimsCreatable.java#L25)

**重要なポイント**:
- **2段階チェック**: `idTokenClaims.hasXxx() && user.hasXxx()`
  - `idTokenClaims.hasXxx()`: スコープで要求されているか
  - `user.hasXxx()`: ユーザーがその属性を持っているか
- **データがない場合は含めない**: 両方がtrueの場合のみクレームを追加
- **sub は常に必須**: `openid`スコープがある場合、`sub`は常に含まれる

---

## 🎯 3種類のクレーム出力先

idp-serverでは、クレームが3つの場所に出力されます。

### 1. IDトークン (ID Token)

**用途**: 認証情報の証明（JWT形式）

```java
public class IdTokenCreator implements IndividualClaimsCreatable {

  public IdToken createIdToken(
      User user,
      Authentication authentication,
      AuthorizationGrant authorizationGrant,
      IdTokenCustomClaims customClaims,
      RequestedClaimsPayload requestedClaimsPayload,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {

    // 1. 標準クレーム生成
    Map<String, Object> claims = createIndividualClaims(
        user, authentication, customClaims, authorizationGrant,
        requestedClaimsPayload, ...);

    // 2. カスタムクレーム生成（プラグイン）
    Map<String, Object> customIndividualClaims =
        customIndividualClaimsCreators.createCustomIndividualClaims(
            user, authentication, authorizationGrant, ...);
    claims.putAll(customIndividualClaims);

    // 3. JWS署名
    JsonWebSignature jws = factory.createWithAsymmetricKey(claims, ...);

    // 4. JWE暗号化（クライアント設定による）
    if (clientConfiguration.hasEncryptedIdTokenMeta()) {
      String jwe = nestedJweCreator.create();
      return new IdToken(jwe);
    }

    return new IdToken(jws.serialize());
  }
}
```

**参考実装**: [IdTokenCreator.java:36](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/identity/id_token/IdTokenCreator.java#L36)

**含まれる必須クレーム**:
- `iss` - トークン発行者
- `sub` - ユーザーID
- `aud` - クライアントID
- `exp` - 有効期限
- `iat` - 発行時刻
- `auth_time` - 認証時刻（オプション）
- `nonce` - リプレイ攻撃対策（要求時）
- `c_hash`, `at_hash`, `s_hash` - ハッシュ値（Hybridフロー等）

### 2. アクセストークン (Access Token)

**用途**: API アクセス権限の証明（Opaque または JWT）

```java
public class AccessTokenCustomClaimsCreators {
  List<AccessTokenCustomClaimsCreator> creators;

  public AccessTokenCustomClaimsCreators() {
    this.creators = new ArrayList<>();
    // 1. デフォルトのScopeMappingCustomClaimsCreatorを追加
    this.creators.add(new ScopeMappingCustomClaimsCreator());
    // 2. 外部プラグインをロード
    creators.addAll(AccessTokenCustomClaimsCreationPluginLoader.load());
  }

  public Map<String, Object> create(
      AuthorizationGrant authorizationGrant,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration,
      ClientCredentials clientCredentials) {

    Map<String, Object> customClaims = new HashMap<>();

    // 各Creatorを順次実行
    creators.forEach(creator -> {
      if (creator.shouldCreate(...)) {
        Map<String, Object> claims = creator.create(...);
        customClaims.putAll(claims);
      }
    });

    return customClaims;
  }
}
```

**参考実装**: [AccessTokenCustomClaimsCreators.java:30](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/token/plugin/AccessTokenCustomClaimsCreators.java#L30)

**アクセストークンの特徴**:
- **リソースサーバー向け**: APIアクセスに必要な情報を含む
- **最小化原則**: 必要最小限のクレームのみ含める
- **JWT形式の場合**: カスタムクレームを追加可能
- **Opaque形式の場合**: クレームは含まれず、Introspectionで取得

> **`verified_claims` の出力構造**: 身元確認済みクレームは OIDC4IDA 準拠のネスト構造（`verification` + `claims`）で出力される。Access Token / UserInfo の構造仕様と、過去のフラット構造からの移行手順は [verified_claims 出力構造の変更（利用者対応）](../../../content_09_project/v0.11.0-verified-claims-impact.md) を参照。

### 3. Userinfo エンドポイント

**用途**: ユーザー情報の詳細取得

```java
public class UserinfoClaimsCreator implements IndividualClaimsCreatable {

  public Map<String, Object> createClaims() {
    Map<String, Object> claims = new HashMap<>();

    // 1. 標準クレーム生成
    Map<String, Object> individualClaims =
        createIndividualClaims(user, authorizationGrant.userinfoClaims());

    // 2. カスタムクレーム生成（プラグイン）
    Map<String, Object> customIndividualClaims =
        userinfoCustomIndividualClaimsCreators.createCustomIndividualClaims(
            user, authorizationGrant,
            authorizationServerConfiguration, clientConfiguration);

    claims.putAll(individualClaims);
    claims.putAll(customIndividualClaims);

    return claims;
  }
}
```

**参考実装**: [UserinfoClaimsCreator.java:28](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/userinfo/UserinfoClaimsCreator.java#L28)

**Userinfoの特徴**:
- **詳細な属性情報**: IDトークンより多くのクレームを含められる
- **動的取得**: アクセストークン提示で最新情報を取得
- **スコープベース**: アクセストークンのスコープに基づいて情報開示

---

## 🔌 カスタムクレームプラグイン実装

### 1. アクセストークン用プラグイン

`AccessTokenCustomClaimsCreator`インターフェースを実装します。

```java
public interface AccessTokenCustomClaimsCreator {

  /**
   * このCreatorを実行すべきか判定
   */
  boolean shouldCreate(
      AuthorizationGrant authorizationGrant,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration,
      ClientCredentials clientCredentials);

  /**
   * カスタムクレームを生成
   */
  Map<String, Object> create(
      AuthorizationGrant authorizationGrant,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration,
      ClientCredentials clientCredentials);
}
```

**参考実装**: [AccessTokenCustomClaimsCreator.java:25](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/token/plugin/AccessTokenCustomClaimsCreator.java#L25)

#### 実装例: ScopeMappingCustomClaimsCreator

```java
public class ScopeMappingCustomClaimsCreator implements AccessTokenCustomClaimsCreator {

  private static final String prefix = "claims:";

  @Override
  public boolean shouldCreate(
      AuthorizationGrant authorizationGrant,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration,
      ClientCredentials clientCredentials) {

    // custom_claims_scope_mapping が有効か確認
    if (!authorizationServerConfiguration.enabledCustomClaimsScopeMapping()) {
      return false;
    }

    // claims: プレフィックスのスコープがあるか確認
    return authorizationGrant.scopes().hasScopeMatchedPrefix(prefix);
  }

  @Override
  public Map<String, Object> create(
      AuthorizationGrant authorizationGrant,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration,
      ClientCredentials clientCredentials) {

    User user = authorizationGrant.user();
    Map<String, Object> claims = new HashMap<>();

    // claims: プレフィックスのスコープを抽出
    Scopes scopes = authorizationGrant.scopes();
    Scopes filteredClaimsScope = scopes.filterMatchedPrefix(prefix);
    CustomProperties customProperties = user.customProperties();

    for (String scope : filteredClaimsScope) {
      // "claims:roles" → "roles"
      String claimName = scope.substring(prefix.length());

      // カスタムプロパティから取得
      if (customProperties.contains(claimName)) {
        claims.put(claimName, customProperties.getValue(claimName));
      }

      // 特定のクレームを個別処理
      if (claimName.equals("status")) {
        claims.put("status", user.status().name());
      }

      if (claimName.equals("roles") && user.hasRoles()) {
        claims.put("roles", user.roleNameAsListString());
      }

      if (claimName.equals("permissions") && user.hasPermissions()) {
        claims.put("permissions", user.permissions());
      }

      if (claimName.equals("assigned_tenants") && user.hasAssignedTenants()) {
        claims.put("assigned_tenants", user.assignedTenants());
        claims.put("current_tenant_id", user.currentTenantIdentifier().value());
      }

      if (claimName.equals("authentication_devices") && user.hasAuthenticationDevices()) {
        claims.put("authentication_devices", user.authenticationDevicesListAsMap());
      }
    }

    return claims;
  }
}
```

**参考実装**: [ScopeMappingCustomClaimsCreator.java:29](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/token/plugin/ScopeMappingCustomClaimsCreator.java#L29)

**対応しているクレーム**:
- `claims:status` - ユーザーステータス
- `claims:ex_sub` - 外部ユーザーID
- `claims:provider_id` - アイデンティティプロバイダID
- `claims:roles` - ロール一覧
- `claims:permissions` - 権限一覧
- `claims:assigned_tenants` - 割り当てテナント一覧
- `claims:assigned_organizations` - 割り当て組織一覧
- `claims:authentication_devices` - 認証デバイス一覧
- `claims:{任意のカスタムプロパティ}` - ユーザーのカスタムプロパティ

### 2. IDトークン用プラグイン

`CustomIndividualClaimsCreator`インターフェースを実装します。

```java
public interface CustomIndividualClaimsCreator {

  boolean shouldCreate(
      User user,
      Authentication authentication,
      AuthorizationGrant authorizationGrant,
      IdTokenCustomClaims customClaims,
      RequestedClaimsPayload requestedClaimsPayload,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration);

  Map<String, Object> create(
      User user,
      Authentication authentication,
      AuthorizationGrant authorizationGrant,
      IdTokenCustomClaims customClaims,
      RequestedClaimsPayload requestedClaimsPayload,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration);
}
```

**参考実装**: [CustomIndividualClaimsCreator.java:28](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/identity/id_token/plugin/CustomIndividualClaimsCreator.java#L28)

#### 実装例: 組織情報の追加

```java
public class OrganizationClaimsCreator implements CustomIndividualClaimsCreator {

  @Override
  public boolean shouldCreate(
      User user,
      Authentication authentication,
      AuthorizationGrant authorizationGrant,
      IdTokenCustomClaims customClaims,
      RequestedClaimsPayload requestedClaimsPayload,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {

    // org_info スコープがある場合のみ実行
    return authorizationGrant.scopes().contains("org_info");
  }

  @Override
  public Map<String, Object> create(
      User user,
      Authentication authentication,
      AuthorizationGrant authorizationGrant,
      IdTokenCustomClaims customClaims,
      RequestedClaimsPayload requestedClaimsPayload,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {

    Map<String, Object> claims = new HashMap<>();

    if (user.hasAssignedOrganizations()) {
      claims.put("org_id", user.currentOrganizationIdentifier().value());
      claims.put("org_name", user.currentOrganizationName());
      claims.put("org_role", user.organizationRole());
    }

    return claims;
  }
}
```

### 3. プラグイン登録

`META-INF/services`にプラグインを登録します。

**ファイル名**: `META-INF/services/org.idp.server.core.openid.token.plugin.AccessTokenCustomClaimsCreator`

```
com.example.idp.plugin.OrganizationClaimsCreator
com.example.idp.plugin.CustomRolesClaimsCreator
```

**ファイル名**: `META-INF/services/org.idp.server.core.openid.identity.id_token.plugin.CustomIndividualClaimsCreator`

```
com.example.idp.plugin.OrganizationClaimsCreator
```

**プラグインローダーの仕組み**:

```java
public class AccessTokenCustomClaimsCreationPluginLoader extends PluginLoader {

  public static List<AccessTokenCustomClaimsCreator> load() {
    List<AccessTokenCustomClaimsCreator> customClaimsCreators = new ArrayList<>();

    // 1. 内部モジュールからロード
    List<AccessTokenCustomClaimsCreator> internals =
        loadFromInternalModule(AccessTokenCustomClaimsCreator.class);
    customClaimsCreators.addAll(internals);

    // 2. 外部モジュールからロード
    List<AccessTokenCustomClaimsCreator> externals =
        loadFromExternalModule(AccessTokenCustomClaimsCreator.class);
    customClaimsCreators.addAll(externals);

    return customClaimsCreators;
  }
}
```

**参考実装**: [AccessTokenCustomClaimsCreationPluginLoader.java:25](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/plugin/token/AccessTokenCustomClaimsCreationPluginLoader.java#L25)

---

## 🔧 実装フロー

### IDトークン生成フロー

```
┌─────────────────────────────────────────────────────────────┐
│ 1. トークンエンドポイント                                    │
│    - 認可コード検証                                          │
│    - AuthorizationGrant取得                                  │
└─────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────┐
│ 2. IdTokenCreator                                            │
│    - createIdToken(user, authentication, grant, ...)         │
└─────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────┐
│ 3. 標準クレーム生成 (IndividualClaimsCreatable)             │
│    - createIndividualClaims(user, idTokenClaims, ...)        │
│    - スコープに基づいたクレーム選択                          │
│    - sub, name, email, phone_number 等                       │
└─────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────┐
│ 4. カスタムクレーム生成 (CustomIndividualClaimsCreators)    │
│    - 各Pluginの shouldCreate() 判定                          │
│    - 実行すべきPluginの create() 実行                        │
│    - 全Pluginの結果をマージ                                  │
└─────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────┐
│ 5. JWT署名・暗号化                                           │
│    - JWS署名（AuthorizationServerのJWKS使用）               │
│    - JWE暗号化（ClientのJWKS使用、設定による）              │
└─────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────┐
│ 6. レスポンス返却                                            │
│    - id_token, access_token, refresh_token 等                │
└─────────────────────────────────────────────────────────────┘
```

### アクセストークン生成フロー（JWT形式）

```
┌─────────────────────────────────────────────────────────────┐
│ 1. トークンエンドポイント                                    │
│    - OAuthTokenCreationService選択（GrantType別）           │
└─────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────┐
│ 2. JwtAccessTokenCreator                                     │
│    - createAccessToken(grant, configuration, ...)            │
└─────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────┐
│ 3. カスタムクレーム生成 (AccessTokenCustomClaimsCreators)   │
│    - ScopeMappingCustomClaimsCreator (デフォルト)            │
│    - 外部プラグイン（ServiceLoader経由）                     │
└─────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────┐
│ 4. JWT生成                                                   │
│    - 標準クレーム（iss, sub, aud, exp, iat, scope）         │
│    - カスタムクレーム（上記で生成）                          │
│    - JWS署名                                                 │
└─────────────────────────────────────────────────────────────┘
```

---

## 🧪 テスト実装例

### カスタムクレームプラグインのテスト

```java
@Test
void testAccessTokenCustomClaims() {
  // 1. スコープ設定
  Scopes scopes = new Scopes("openid profile claims:roles claims:permissions");

  // 2. AuthorizationGrant作成
  AuthorizationGrant grant = AuthorizationGrant.builder()
      .scopes(scopes)
      .user(user)
      .build();

  // 3. カスタムクレーム生成
  ScopeMappingCustomClaimsCreator creator = new ScopeMappingCustomClaimsCreator();

  assertTrue(creator.shouldCreate(grant, config, client, credentials));

  Map<String, Object> claims = creator.create(grant, config, client, credentials);

  // 4. 検証
  assertThat(claims).containsKey("roles");
  assertThat(claims).containsKey("permissions");
  assertThat(claims).doesNotContainKey("email");  // claims:email がないので含まれない
}
```

### Scopeマッピングのテスト

```java
@Test
void testScopeFiltering() {
  Scopes scopes = new Scopes("openid claims:name claims:roles verified_claims:given_name");

  // claims: プレフィックスのみ抽出
  Scopes claimsScopes = scopes.filterMatchedPrefix("claims:");
  assertThat(claimsScopes.toStringSet()).containsExactlyInAnyOrder("claims:name", "claims:roles");

  // verified_claims: プレフィックスのみ抽出
  Scopes verifiedScopes = scopes.filterMatchedPrefix("verified_claims:");
  assertThat(verifiedScopes.toStringSet()).containsExactly("verified_claims:given_name");
}
```

---

## 📋 実装チェックリスト

新しいカスタムクレームプラグインを追加する際のチェックリスト:

### AccessTokenCustomClaimsCreator

- [ ] **インターフェース実装**: `AccessTokenCustomClaimsCreator`を実装
  ```java
  public class MyCustomClaimsCreator implements AccessTokenCustomClaimsCreator {
    @Override
    public boolean shouldCreate(...) { ... }

    @Override
    public Map<String, Object> create(...) { ... }
  }
  ```

- [ ] **shouldCreate判定**: 実行条件を明確に定義
  - スコープチェック
  - テナント設定チェック
  - クライアント設定チェック

- [ ] **create実装**: クレーム生成ロジック
  - nullチェック
  - データ存在チェック（`user.hasXxx()`）
  - 適切な型変換

- [ ] **プラグイン登録**: `META-INF/services`に登録
  ```
  META-INF/services/org.idp.server.core.openid.token.plugin.AccessTokenCustomClaimsCreator
  ```

- [ ] **テスト作成**:
  - `shouldCreate`のテスト
  - `create`のテスト
  - 境界値テスト

### CustomIndividualClaimsCreator (IDトークン用)

- [ ] **インターフェース実装**: `CustomIndividualClaimsCreator`を実装
- [ ] **shouldCreate判定**: IDトークン特有の条件
  - `Authentication`情報の活用
  - `RequestedClaimsPayload`の確認
- [ ] **create実装**: IDトークン用クレーム生成
  - 認証時刻（`auth_time`）関連
  - ACR/AMR関連
- [ ] **プラグイン登録**: `META-INF/services`に登録
- [ ] **テスト作成**: IDトークン生成フロー全体のテスト

---

## 🚨 よくある間違い

### 1. スコープの存在確認忘れ

```java
// ❌ 誤り: スコープなしでクレーム追加
@Override
public Map<String, Object> create(...) {
  Map<String, Object> claims = new HashMap<>();
  claims.put("roles", user.roleNameAsListString());  // 常に追加してしまう
  return claims;
}

// ✅ 正しい: shouldCreateで判定
@Override
public boolean shouldCreate(...) {
  return authorizationGrant.scopes().contains("claims:roles");
}

@Override
public Map<String, Object> create(...) {
  Map<String, Object> claims = new HashMap<>();
  if (user.hasRoles()) {  // データ存在確認も忘れずに
    claims.put("roles", user.roleNameAsListString());
  }
  return claims;
}
```

### 2. データ存在確認忘れ

```java
// ❌ 誤り: nullチェックなし
claims.put("email", user.email());  // NullPointerException のリスク

// ✅ 正しい: 存在確認
if (user.hasEmail()) {
  claims.put("email", user.email());
}
```

### 3. プレフィックス除去忘れ

```java
// ❌ 誤り: プレフィックス付きでクレーム追加
for (String scope : filteredClaimsScope) {
  claims.put(scope, ...);  // "claims:roles" というキー名になってしまう
}

// ✅ 正しい: プレフィックス除去
for (String scope : filteredClaimsScope) {
  String claimName = scope.substring("claims:".length());
  claims.put(claimName, ...);  // "roles" というキー名
}
```

### 4. カスタムクレームとスコープの混同

```java
// ❌ 誤り: "claims:" スコープを標準スコープと同列に扱う
if (scopes.contains("email")) {
  // email クレーム追加
}
if (scopes.contains("claims:email")) {
  // これは個別クレーム指定なので別処理
}

// ✅ 正しい: 明確に分離
// 標準スコープ → IndividualClaimsCreatable で処理
// カスタムスコープ → ScopeMappingCustomClaimsCreator で処理
```

---

## 🔗 関連ドキュメント

**概念・基礎**:
- [basic-12: OpenID Connect詳解](../../content_11_learning/04-openid-connect/openid-connect-detail.md) - OIDC仕様
- [basic-14: OIDCクレーム設計](../../content_11_learning/04-openid-connect/oidc-claim-design.md) - クレーム設計ガイド
- [concept-09: カスタムクレーム](../../content_03_concepts/04-tokens-claims/concept-03-custom-claims.md) - カスタムクレームの概念

**実装詳細**:
- [impl-12: Plugin実装ガイド](./impl-12-plugin-implementation.md) - プラグインシステムの詳細
- [03-application-plane/03-token-endpoint.md](../03-application-plane/03-token-endpoint.md) - トークンエンドポイント
- [03-application-plane/05-userinfo.md](../03-application-plane/05-userinfo.md) - Userinfoエンドポイント

**参考実装クラス**:
- [Scopes.java](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/type/oauth/Scopes.java)
- [Claims.java](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/type/oidc/Claims.java)
- [IndividualClaimsCreatable.java](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/identity/id_token/IndividualClaimsCreatable.java)
- [ScopeMappingCustomClaimsCreator.java](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/token/plugin/ScopeMappingCustomClaimsCreator.java)
- [AccessTokenCustomClaimsCreator.java](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/token/plugin/AccessTokenCustomClaimsCreator.java)
- [CustomIndividualClaimsCreator.java](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/identity/id_token/plugin/CustomIndividualClaimsCreator.java)

**RFC/仕様**:
- [RFC 6749 - OAuth 2.0 (Section 3.3: Access Token Scope)](https://datatracker.ietf.org/doc/html/rfc6749#section-3.3)
- [OpenID Connect Core 1.0 - 5.4: Requesting Claims using Scope Values](https://openid.net/specs/openid-connect-core-1_0.html#ScopeClaims)
- [OpenID Connect for Identity Assurance 1.0](https://openid.net/specs/openid-connect-4-identity-assurance-1_0.html) - verified_claims

---

**最終更新**: 2025-12-07
**難易度**: ⭐⭐⭐ (中級)
