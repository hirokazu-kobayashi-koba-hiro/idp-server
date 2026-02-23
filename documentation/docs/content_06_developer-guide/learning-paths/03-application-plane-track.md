# Application Plane Track（認証フロー実装者向け）

## 🎯 このトラックの目標

**認証・認可フロー（Application Plane）の実装**ができるようになる。

- Authorization Flow実装
- Token Endpoint実装（Grant Type追加）
- 認証インタラクター実装（新しい認証方式追加）
- Federation実装（外部IdP連携）

**前提**: [初級ラーニングパス](./01-beginner.md)完了

---

## 📚 学習内容

### OAuth/OIDCフロー理解

#### 読むべきドキュメント
- [ ] [Application Plane概要](../03-application-plane/01-overview.md)
- [ ] [Authorization Flow実装](../03-application-plane/02-authorization-flow.md)
- [ ] [Token Endpoint実装](../03-application-plane/03-token-endpoint.md)
- [ ] [UserInfo実装](../03-application-plane/05-userinfo.md)

#### 実装の参考
実際のコードを読んで理解：
- [AuthorizationCodeGrantService.java](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/token/service/AuthorizationCodeGrantService.java)
- [ClientCredentialsGrantService.java](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/token/service/ClientCredentialsGrantService.java)
- [RefreshTokenGrantService.java](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/token/service/RefreshTokenGrantService.java)

#### チェックリスト
- [ ] Authorization Code Flowの8ステップを説明できる
- [ ] 4種類のGrant Typeの違いを説明できる
- [ ] クライアント認証7方式を説明できる
- [ ] PKCE（Proof Key for Code Exchange）の役割を説明できる
- [ ] Token Introspection/Revocationの用途を理解

---

### 認証インタラクター実装

#### 読むべきドキュメント
- [ ] [Authentication実装ガイド](../03-application-plane/04-authentication.md)
- [ ] [AuthenticationInteractor実装ガイド](../04-implementation-guides/impl-06-authentication-interactor.md)
- [ ] [Plugin実装ガイド](../04-implementation-guides/impl-12-plugin-implementation.md)

#### 実装の参考
既存の認証インタラクターを読んで理解：
- [PasswordAuthenticationInteractor.java](../../../libs/idp-server-authentication-interactors/src/main/java/org/idp/server/authentication/interactors/password/PasswordAuthenticationInteractor.java)
- [SmsAuthenticationInteractor.java](../../../libs/idp-server-authentication-interactors/src/main/java/org/idp/server/authentication/interactors/sms/SmsAuthenticationInteractor.java)
- [Fido2AuthenticationInteractor.java](../../../libs/idp-server-authentication-interactors/src/main/java/org/idp/server/authentication/interactors/fido2/Fido2AuthenticationInteractor.java)
- [FidoUafAuthenticationInteractor.java](../../../libs/idp-server-authentication-interactors/src/main/java/org/idp/server/authentication/interactors/fidouaf/FidoUafAuthenticationInteractor.java)

#### チェックリスト
- [ ] AuthenticationInteractorインターフェースを説明できる
- [ ] `type()`メソッドの役割を理解
- [ ] Plugin機構での動的選択を理解
- [ ] 新しい認証インタラクターを実装できる
- [ ] Plugin自動ロード確認
- [ ] E2Eテスト作成

---

### Grant Type実装

#### 読むべきドキュメント
- [ ] [Token Endpoint実装](../03-application-plane/03-token-endpoint.md)
- [ ] [CIBA Flow実装ガイド](../03-application-plane/06-ciba-flow.md)

#### 実装の参考
- [AuthorizationCodeGrantService.java](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/token/service/AuthorizationCodeGrantService.java)
- [ClientCredentialsGrantService.java](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/token/service/ClientCredentialsGrantService.java)
- [CibaGrantService.java](../../../libs/idp-server-core-extension-ciba/src/main/java/org/idp/server/core/extension/ciba/token/CibaGrantService.java)

#### チェックリスト
- [ ] `OAuthTokenCreationService`インターフェースを理解
- [ ] `supportedGrantType()`で一意なGrant Type返却
- [ ] Validator-Verifier-Serviceパターンを理解
- [ ] 新しいGrant Typeを実装できる
- [ ] Plugin自動ロード確認
- [ ] E2Eテスト作成

---

### Federation実装

#### 読むべきドキュメント
- [ ] [Federation実装](../03-application-plane/08-federation.md)
- [ ] [Federation Provider実装ガイド](../04-implementation-guides/impl-08-federation-provider.md)

#### 実装の参考
- [OidcSsoExecutor.java](../../../libs/idp-server-federation-oidc/src/main/java/org/idp/server/federation/sso/oidc/OidcSsoExecutor.java)

#### チェックリスト
- [ ] SsoExecutorインターフェースを理解
- [ ] `supportedProvider()`で一意なプロバイダー返却
- [ ] state/nonce検証実装
- [ ] 外部IdPへのToken Request実装
- [ ] ID Token検証実装
- [ ] 属性マッピング実装
- [ ] 新しい外部IdP連携を追加できる
- [ ] OpenID Connect Discovery対応を実装できる
- [ ] E2Eテスト作成

---

## ✅ 完了判定基準

以下をすべて達成したらApplication Plane Trackクリア：

### 知識面
- [ ] Authorization Code Flowの8ステップを説明できる
- [ ] 4種類のGrant Typeの違いを説明できる
- [ ] クライアント認証7方式を説明できる
- [ ] PKCE（Proof Key for Code Exchange）の仕組みを説明できる
- [ ] state/nonceの役割を説明できる
- [ ] Delegateパターンの役割を説明できる

### 実践面
- [ ] 新しいGrant Typeを実装できる
- [ ] 新しい認証インタラクターを実装できる
- [ ] 外部IdP連携（SsoExecutor）を実装できる
- [ ] E2Eテストを作成できる
- [ ] PRを出してレビューを受けられる

### OAuth/OIDC仕様理解
- [ ] RFC 6749（OAuth 2.0）の主要セクションを理解
- [ ] OpenID Connect Core 1.0の主要フローを理解
- [ ] JWT（RFC 7519）の構造を理解

### コード品質
- [ ] Plugin自動ロード（`type()`/`supportedGrantType()`/`supportedProvider()`）を実装できる
- [ ] Delegateパターンを使える（Core層からUseCase層へのコールバック）
- [ ] Validator/Verifierは void + throw

---

## 🚀 次のステップ

Application Plane Track完了後の選択肢：

### Control Planeも学ぶ
管理API実装も習得したい場合：
- [Control Plane Track](./02-control-plane-track.md)

### Full Stack開発者へ
両方を統合した高度な実装を学ぶ：
- [Full Stack Track](./04-full-stack-track.md)

### 専門性を深める
Application Plane専門家として：
- 新しいOAuth拡張仕様対応（RAR、DPoP等）
- セキュリティ強化（FAPI対応）
- 新しい認証方式設計

---

## 💡 Application Plane実装のヒント

### よくあるミス

#### 1. Delegate未実装

```java
// ❌ 間違い: UseCase層でRepositoryを直接呼ぶ
public class BadEntryService implements UserinfoApi {
    @Autowired
    private UserQueryRepository userQueryRepository;  // NG

    @Override
    public UserinfoResponse get(String token) {
        // UseCase層で直接Repository呼び出し
        User user = userQueryRepository.get(...);
    }
}

// ✅ 正しい: Core層でDelegateインターフェース定義、UseCase層で実装
// Core層
public interface UserinfoDelegate {
    User findUser(Tenant tenant, Subject subject);
}

// UseCase層が実装
public class UserinfoEntryService implements UserinfoApi, UserinfoDelegate {
    @Override
    public User findUser(Tenant tenant, Subject subject) {
        return userQueryRepository.get(tenant, new UserIdentifier(subject.value()));
    }
}
```

**理由**: Core層はRepositoryに直接依存しない（Hexagonal Architecture原則）

#### 2. Plugin識別子の実装忘れ

```java
// ❌ 間違い: type()未実装またはnull返却
public class BadAuthenticationInteractor implements AuthenticationInteractor {
    @Override
    public String type() {
        return null;  // NG: Plugin自動ロードされない
    }
}

// ✅ 正しい: 一意な識別子を返却
public class PasswordAuthenticationInteractor implements AuthenticationInteractor {
    @Override
    public String type() {
        return "password";  // 一意な識別子
    }
}
```

**理由**: Plugin自動ロード時に`type()`/`supportedGrantType()`/`supportedProvider()`で識別

#### 3. Authorization Code再利用

```bash
# ❌ 間違い: Authorization Codeは1回しか使えない
curl -X POST ".../v1/tokens" -d "grant_type=authorization_code&code=${CODE}..."
# → 成功

curl -X POST ".../v1/tokens" -d "grant_type=authorization_code&code=${CODE}..."
# → invalid_grant エラー（Authorization Codeは使用後即削除）
```

**理由**: セキュリティ（リプレイ攻撃防止）

#### 4. redirect_uri不一致

```bash
# ❌ 間違い: Authorization RequestとToken Requestでredirect_uriが異なる

# Authorization Request
redirect_uri=https://app.example.com/callback

# Token Request
redirect_uri=https://app.example.com/callback/  # 末尾スラッシュ → エラー
```

**理由**: RFC 6749で完全一致が必須

---

### Application Plane特有のパターン

#### Delegateパターン

Core層からUseCase層へのコールバック：

```java
// Core層がDelegateインターフェース定義
public interface UserinfoDelegate {
    User findUser(Tenant tenant, Subject subject);
}

// UseCase層が実装
public class UserinfoEntryService implements UserinfoApi, UserinfoDelegate {
    @Override
    public User findUser(Tenant tenant, Subject subject) {
        return userQueryRepository.get(tenant, new UserIdentifier(subject.value()));
    }
}
```

#### Plugin自動ロード

AuthenticationInteractor/OAuthTokenCreationService/SsoExecutorの自動ロード：
- `type()` / `supportedGrantType()` / `supportedProvider()` で一意な識別子を返す
- Spring Boot起動時に自動検出
- 実装例: [impl-12-plugin-implementation.md](../04-implementation-guides/impl-12-plugin-implementation.md)

---

## 🔧 新しいOAuth/OIDC仕様の実装手順

Application Plane Trackを完了した後、新しいOAuth/OIDC仕様（DPoP、RAR、FAPI等）を実装する手順を示します。

### 実装手順（例: DPoP対応）

DPoP (Demonstrating Proof-of-Possession) RFC 9449 を実装する場合：

#### Step 1: 仕様理解
- [ ] RFC 9449を読む
- [ ] DPoPの目的を理解（トークン盗用防止）
- [ ] DPoP Proof JWTの構造を理解
- [ ] 既存実装（PKCE、FAPI等）を参考にする

#### Step 2: 影響範囲の特定
DPoPは以下に影響：
- **Token Endpoint**: DPoP Proof検証、DPoP bound Access Token発行
- **Resource Server**: DPoP Proof検証（Access Token使用時）
- **Discovery**: メタデータに`dpop_signing_alg_values_supported`追加

#### Step 3: Control Plane実装（設定API）
DPoP設定を管理する場合：

```
1. AuthorizationServerConfiguration拡張
   - dpopRequired: boolean
   - dpopSigningAlgValuesSupported: List<String>

2. Client設定拡張
   - dpopBoundAccessTokens: boolean
```

参考実装: `libs/idp-server-control-plane/.../oidc/authorization/`

#### Step 4: Application Plane実装（認証フロー）

##### 4-1. DPoP Proof検証（Core層）
```
1. DPoPProof値オブジェクト作成
   libs/idp-server-core/src/main/java/org/idp/server/core/openid/token/dpop/
   ├─ DPoPProof.java（値オブジェクト）
   ├─ DPoPProofValidator.java（形式検証）
   └─ DPoPProofVerifier.java（署名検証）

2. TokenRequestContextにDPoP情報追加
   - DPoPヘッダーを読み込み
   - DPoPProofをパース
```

参考: `libs/idp-server-core/.../token/`

##### 4-2. Grant Service修正
```
各Grant ServiceでDPoP対応:
- AuthorizationCodeGrantService
- RefreshTokenGrantService
- ClientCredentialsGrantService

実装パターン:
1. DPoPProofValidator.validate()
2. DPoPProofVerifier.verify()
3. Access Token生成時にDPoP bound設定
```

参考: `libs/idp-server-core/.../token/service/`

##### 4-3. Access Token拡張
```
1. AccessToken値オブジェクト拡張
   - dpopJkt: String（DPoP公開鍵のJWK Thumbprint）

2. AccessTokenCreatorでDPoP bound Token生成
   - cnf.jkt クレームを追加
```

##### 4-4. Resource Server検証
```
UserInfo Endpointで検証:
1. DPoPヘッダー取得
2. DPoPProof検証
3. Access TokenのcnfクレームとDPoP公開鍵を照合
```

参考: `libs/idp-server-core/.../userinfo/`

#### Step 5: Discovery対応
```
OpenID Connect Discoveryにメタデータ追加:
- dpop_signing_alg_values_supported: ["RS256", "ES256"]
```

参考: `libs/idp-server-core/.../metadata/`

#### Step 6: E2Eテスト作成

E2EテストはRFC仕様の要件を直接検証します。RFCの用語をそのまま使い、MUST要件をテストします。

##### E2Eテストの考え方

**基本原則**:
1. **RFCの用語を使う**: テストケース名にRFC番号とMUST/SHOULD/MAYを明記
2. **正常系はMUST要件を検証**: RFCで定義された正常な動作を確認
3. **異常系はMUST要件の違反を検証**: 不正なリクエストを正しく拒否するか確認
4. **メタデータ・インターフェースの確認**: Discovery、エラーレスポンス形式、HTTPヘッダーの検証

##### E2Eテストケース設計（DPoP RFC 9449の例）

**正常系（RFC MUST要件の検証）**:
- [ ] RFC 9449: Token endpoint MUST accept DPoP header
  - DPoP Proof付きToken Requestが成功する
  - レスポンスに`token_type=DPoP`が含まれる
  - Access Tokenに`cnf.jkt`クレームが含まれる

- [ ] RFC 9449: Resource server MUST verify DPoP proof
  - DPoP-bound tokenをDPoP Proof付きで使用できる
  - Resource serverがDPoP Proofを検証する（`ath`クレーム含む）

**異常系（RFC MUST要件違反の検証）**:
- [ ] RFC 9449: DPoP proof MUST contain htm, htu, jti, iat
  - 必須クレーム欠落時に`invalid_dpop_proof`エラーを返す
  - エラーメッセージに欠落したクレーム名が含まれる

- [ ] RFC 9449: Resource server MUST verify signature
  - 署名改ざん時に401 `invalid_token`を返す

- [ ] RFC 9449: cnf.jkt MUST match DPoP proof public key
  - 異なる鍵でのDPoP Proof使用時に401 `invalid_token`を返す
  - エラーメッセージに"Public key mismatch"が含まれる

- [ ] RFC 9449: Server MUST reject reused jti
  - 同一jtiの再利用時に`invalid_dpop_proof`エラーを返す
  - リプレイ攻撃を検知する

- [ ] RFC 9449: htm/htu MUST match actual request
  - HTTP method不一致時に`invalid_dpop_proof`エラーを返す
  - URL不一致時に`invalid_dpop_proof`エラーを返す

**メタデータ・インターフェース確認**:
- [ ] RFC 9449: Discovery MUST include dpop_signing_alg_values_supported
  - `.well-known/openid-configuration`にメタデータが含まれる
  - サポートされるアルゴリズム（RS256、ES256等）が列挙される

- [ ] RFC 6749: Error responses MUST follow standard format
  - エラーレスポンスに`error`フィールドが含まれる
  - `error_description`フィールドが含まれる（SHOULD）

- [ ] RFC 6750: Resource server MUST return WWW-Authenticate on 401
  - 401レスポンスに`WWW-Authenticate`ヘッダーが含まれる
  - ヘッダーに`error="invalid_token"`が含まれる

- [ ] RFC 6749: token_type comparison MUST be case-insensitive
  - `DPoP`と`dpop`の両方を受け入れる

**セキュリティテスト**:
- [ ] Replay attack detection
  - 時間窓内のjti再利用を拒否する
  - 時間窓外のjti再利用は許可される（実装による）

- [ ] Token binding enforcement
  - 異なる鍵での使用を拒否する
  - トークン盗用を防止する

- [ ] Token theft mitigation
  - DPoP Proofなしでの使用を拒否する
  - Bearer tokenとして使用できないことを確認

#### Step 7: ドキュメント作成
```
documentation/docs/content_06_developer-guide/03-application-plane/
└─ 11-dpop.md（新規作成）
   - DPoPとは
   - 実装概要
   - API使用例
   - セキュリティ考慮事項
```

---

### 実装チェックリスト（汎用）

新しいOAuth/OIDC仕様を実装する際の汎用チェックリスト：

#### 設計フェーズ
- [ ] RFC/仕様を完全に理解している
- [ ] 影響範囲を特定している（Token Endpoint/Authorization Endpoint/UserInfo等）
- [ ] 既存実装との整合性を確認している
- [ ] セキュリティリスクを理解している

#### 実装フェーズ（Core層）
- [ ] 値オブジェクト作成（不変オブジェクト）
- [ ] Validator作成（void + throw、形式検証）
- [ ] Verifier作成（void + throw、ビジネスロジック検証）
- [ ] Handler/Service修正
- [ ] Repository修正（必要な場合）

#### 実装フェーズ（Control Plane）
- [ ] 設定API実装（必要な場合）
- [ ] ContextBuilder実装
- [ ] Audit Log記録

#### テストフェーズ
- [ ] ユニットテスト作成
- [ ] E2Eテスト作成（正常系・異常系）
- [ ] セキュリティテスト（攻撃シナリオ）

#### ドキュメントフェーズ
- [ ] 実装ガイド作成
- [ ] API使用例作成
- [ ] セキュリティ考慮事項記載

---

### 参考: 実装済みの拡張仕様

以下の実装を参考にできます：

| 仕様 | 実装場所 | 参考ドキュメント |
|------|---------|----------------|
| PKCE | `libs/idp-server-core/.../pkce/` | [impl-23-pkce-implementation.md](../04-implementation-guides/impl-23-pkce-implementation.md) |
| CIBA | `libs/idp-server-core-extension-ciba/` | [06-ciba-flow.md](../03-application-plane/06-ciba-flow.md) |
| FAPI | `libs/idp-server-core/.../fapi/` | [impl-22-fapi-implementation.md](../04-implementation-guides/impl-22-fapi-implementation.md) |
| PAR | `libs/idp-server-core/.../par/` | [02-authorization-flow.md](../03-application-plane/02-authorization-flow.md) |

---

## 🔗 関連リソース

- [開発者ガイドTOC](../DEVELOPER_GUIDE_TOC.md)

---

**最終更新**: 2025-12-18
**対象**: Application Plane実装者
**習得スキル**: Authorization Flow、Token Endpoint、認証インタラクター、Grant Type、Federation
