# Tenant設定ガイド（開発者向け）

## 📍 このドキュメントの位置づけ

**対象読者**: Phase 1（how-to 01-05）完了済みの開発者

**このドキュメントで学べること**:
- 本番運用に向けた詳細なTenant設定
- ユースケース別の設定パターン
- 高度な機能（Extension、カスタムスコープ、カスタムクレーム）
- セキュリティとパフォーマンスのベストプラクティス

**How-toガイドとの違い**:

| ドキュメント | 目的 | 内容 |
|------------|------|------|
| **How-to** | 最小構成で動かす | 実践的な手順（動作確認重視） |
| **Developer Guide** | 本番設定を理解する | 詳細仕様と設計パターン |

**前提知識**:
- [how-to-01: 組織初期化](../../content_05_how-to/how-to-02-organization-initialization.md)完了
- [how-to-02: OAuth/OIDC認証の最小設定](../../content_05_how-to/how-to-03-tenant-setup.md)完了
- OAuth 2.0/OpenID Connectの基礎理解

---

## 🧭 Tenantアーキテクチャの理解

### Tenantとは

**Tenant（テナント）**は、マルチテナント環境における**完全に独立した認証・認可ドメイン**です。

### Organization vs Tenant

```
┌─────────────────────────────────────────────────────────┐
│ Organization (企業A)                                     │
│                                                         │
│  ┌──────────────────┐  ┌──────────────────┐           │
│  │ Organizer Tenant │  │ Public Tenant    │           │
│  │ (組織管理用)      │  │ (アプリ用)       │           │
│  ├──────────────────┤  ├──────────────────┤           │
│  │ - 組織管理者     │  │ - Client 1       │           │
│  │ - テナント管理   │  │ - Client 2       │           │
│  │                  │  │ - Users          │           │
│  └──────────────────┘  └──────────────────┘           │
└─────────────────────────────────────────────────────────┘
```

### データ分離の仕組み

各Tenantで完全に分離されるもの：
- **ユーザーデータ**: 認証情報、プロファイル
- **クライアント設定**: OAuth/OIDCクライアント
- **認証ポリシー**: パスワードポリシー、MFA設定
- **トークン設定**: 有効期限、署名鍵
- **セキュリティイベントログ**: 監査ログ

### テナント種別

| 種別 | 説明 | 作成方法 | 用途 |
|------|------|---------|------|
| `ADMIN` | システム管理用テナント | システム初期化時に自動作成 | システム管理・初期設定用 |
| `ORGANIZER` | 組織管理用テナント | 組織作成（Onboarding API）時に自動作成 | 組織管理者の管理操作用 |
| `PUBLIC` | アプリケーション用テナント | 組織レベルAPIで作成 | 通常のアプリケーション用 |

**実装リファレンス**:
- `ADMIN`: [IdpServerStarterContextCreator.java:78](../../../libs/idp-server-control-plane/src/main/java/org/idp/server/control_plane/admin/starter/IdpServerStarterContextCreator.java)
- `ORGANIZER`: [OnboardingContextCreator.java:82](../../../libs/idp-server-control-plane/src/main/java/org/idp/server/control_plane/management/onboarding/OnboardingContextCreator.java)
- `PUBLIC`: [TenantManagementRegistrationContextCreator.java:68](../../../libs/idp-server-control-plane/src/main/java/org/idp/server/control_plane/management/tenant/TenantManagementRegistrationContextCreator.java)

**重要**: 組織レベルAPI（`POST /v1/management/organizations/{org-id}/tenants`）で作成されるテナントは常に`type: "PUBLIC"`です。`ADMIN`と`ORGANIZER`は手動で作成できません。

---

## 📖 API仕様リファレンス

テナント作成・更新のAPI詳細仕様（リクエスト/レスポンススキーマ、全パラメータ説明）は、OpenAPI仕様書を参照してください。

**📖 OpenAPI仕様書**:
- [swagger-control-plane-ja.yaml](../../openapi/swagger-control-plane-ja.yaml) - 日本語版
- [swagger-control-plane-en.yaml](../../openapi/swagger-control-plane-en.yaml) - 英語版

---

## 🎯 シナリオ別設定例

実際のユースケースに応じた認可サーバー設定例を紹介します。

| # | シナリオ | ユースケース | 主なポイント | 詳細 |
|---|---------|------------|------------|------|
| 1 | **Webアプリケーション（標準）** | ユーザーがWebブラウザから安全にログインし、セッション中は再認証なしでサービスを利用する | • Opaque Token<br/>• Access Token: 30分<br/>• Refresh Token: 1時間 | [詳細](#1-webアプリケーション向け標準) |
| 2 | **モバイルアプリ（PKCE）** | モバイルユーザーが再ログインなしで長期間（30日）アプリを利用し続ける | • PKCE必須<br/>• 長期Refresh Token（30日）<br/>• EXTENDS戦略 | [詳細](#2-モバイルアプリ向けpkce対応) |
| 3 | **金融グレード（FAPI）** | 銀行顧客が口座・取引情報に安全にアクセスし、厳格なセキュリティ基準を満たす | • Private Key JWT / mTLS<br/>• Pairwise Subject<br/>• Access Token: 10分 | [詳細](#3-金融グレードfapi準拠) |
| 4 | **SaaS型マルチテナント** | 企業ユーザーが所属組織・部署情報を含むトークンでSaaSサービスにアクセスする | • JWT Token<br/>• カスタムクレーム<br/>• M2M通信対応 | [詳細](#4-saas型マルチテナント) |

---

## 📋 シナリオ詳細設定

### 1. Webアプリケーション向け（標準）

**要件**:
- Authorization Code Flow
- Refresh Token使用
- Access Token: 30分
- Refresh Token: 1時間

**ユースケース**: 一般的なWebアプリケーション、SPA

<details>
<summary>設定JSON例を表示</summary>

```json
{
  "tenant": {
    "id": "web-app-tenant",
    "name": "Web Application Tenant",
    "domain": "https://app.example.com",
    "authorization_provider": "idp-server"
  },
  "authorization_server": {
    "issuer": "https://app.example.com/web-app-tenant",
    "authorization_endpoint": "https://app.example.com/web-app-tenant/v1/authorizations",
    "token_endpoint": "https://app.example.com/web-app-tenant/v1/tokens",
    "userinfo_endpoint": "https://app.example.com/web-app-tenant/v1/userinfo",
    "jwks_uri": "https://app.example.com/web-app-tenant/v1/jwks",
    "scopes_supported": ["openid", "profile", "email"],
    "grant_types_supported": ["authorization_code", "refresh_token"],
    "response_types_supported": ["code"],
    "token_endpoint_auth_methods_supported": ["client_secret_post", "client_secret_basic"],
    "extension": {
      "access_token_type": "opaque",
      "access_token_duration": 1800,
      "refresh_token_duration": 3600,
      "rotate_refresh_token": true
    }
  }
}
```

</details>

**設定ポイント**:
- `access_token_type: "opaque"`: 高速な不透明トークン
- `rotate_refresh_token: true`: セキュリティ向上のためリフレッシュトークンをローテーション

---

### 2. モバイルアプリ向け（PKCE対応）

**要件**:
- Authorization Code Flow + PKCE
- 長期間のRefresh Token（30日）
- カスタムスコープ（プッシュ通知、オフラインアクセス）

**ユースケース**: iOS/Androidアプリ、ネイティブアプリ

<details>
<summary>設定JSON例を表示</summary>

```json
{
  "tenant": {
    "id": "mobile-app-tenant",
    "name": "Mobile Application Tenant",
    "domain": "https://mobile.example.com",
    "authorization_provider": "idp-server"
  },
  "authorization_server": {
    "issuer": "https://mobile.example.com/mobile-app-tenant",
    "authorization_endpoint": "https://mobile.example.com/mobile-app-tenant/v1/authorizations",
    "token_endpoint": "https://mobile.example.com/mobile-app-tenant/v1/tokens",
    "userinfo_endpoint": "https://mobile.example.com/mobile-app-tenant/v1/userinfo",
    "jwks_uri": "https://mobile.example.com/mobile-app-tenant/v1/jwks",
    "scopes_supported": [
      "openid",
      "profile",
      "email",
      "offline_access",
      "notifications:push"
    ],
    "grant_types_supported": ["authorization_code", "refresh_token"],
    "response_types_supported": ["code"],
    "token_endpoint_auth_methods_supported": ["none"],
    "extension": {
      "access_token_type": "opaque",
      "access_token_duration": 3600,
      "refresh_token_duration": 2592000,
      "rotate_refresh_token": true,
      "refresh_token_strategy": "EXTENDS"
    }
  }
}
```

</details>

**設定ポイント**:
- `token_endpoint_auth_methods_supported: ["none"]`: PKCE専用（Client Secretなし）
- `refresh_token_duration: 2592000`: 30日間の長期トークン
- `refresh_token_strategy: "EXTENDS"`: リフレッシュの度にトークン有効期限を延長（ユーザー体験向上）

---

### 3. 金融グレード（FAPI準拠）

**要件**:
- FAPI 1.0 Advanced Profile準拠
- 強力なクライアント認証（Private Key JWT, mTLS）
- Pairwise Subject（プライバシー保護）
- カスタムスコープ（OpenBanking）

**ユースケース**: オンラインバンキング、金融API、機密データアクセス

<details>
<summary>設定JSON例を表示</summary>

```json
{
  "tenant": {
    "id": "banking-tenant",
    "name": "Online Banking Platform",
    "domain": "https://banking.example.com",
    "authorization_provider": "idp-server"
  },
  "session_config": {
    "use_secure_cookie": true,
    "cookie_same_site": "Strict"
  },
  "cors_config": {
    "allow_origins": ["https://banking.example.com"]
  },
  "authorization_server": {
    "issuer": "https://banking.example.com/banking-tenant",
    "authorization_endpoint": "https://banking.example.com/banking-tenant/v1/authorizations",
    "token_endpoint": "https://banking.example.com/banking-tenant/v1/tokens",
    "userinfo_endpoint": "https://banking.example.com/banking-tenant/v1/userinfo",
    "jwks_uri": "https://banking.example.com/banking-tenant/v1/jwks",
    "scopes_supported": [
      "openid",
      "profile",
      "email",
      "openbanking:accounts",
      "openbanking:transactions",
      "openbanking:payments"
    ],
    "grant_types_supported": ["authorization_code", "refresh_token"],
    "response_types_supported": ["code"],
    "response_modes_supported": ["query", "jwt"],
    "token_endpoint_auth_methods_supported": [
      "private_key_jwt",
      "tls_client_auth"
    ],
    "subject_types_supported": ["pairwise"],
    "extension": {
      "access_token_type": "jwt",
      "access_token_duration": 600,
      "refresh_token_duration": 3600,
      "authorization_code_valid_duration": 300,
      "fapi_baseline_scopes": ["openbanking:accounts", "openbanking:transactions"],
      "fapi_advance_scopes": ["openbanking:payments"],
      "id_token_strict_mode": true
    }
  }
}
```

</details>

**設定ポイント**:
- `token_endpoint_auth_methods_supported`: `private_key_jwt`, `tls_client_auth`のみ許可
- `subject_types_supported: ["pairwise"]`: ユーザー識別子を分離（プライバシー保護）
- `access_token_type: "jwt"`: JWT形式で署名検証可能
- `access_token_duration: 600`: 10分の短い有効期限（セキュリティ向上）
- `fapi_baseline_scopes` / `fapi_advance_scopes`: FAPI検証スコープ

**FAPI準拠の利点**:
- 金融機関レベルのセキュリティ
- 国際標準への準拠
- 監査対応の容易さ

---

### 4. SaaS型マルチテナント

**要件**:
- 複数企業の従業員が利用
- カスタムクレーム（企業ID、部署、権限）
- JWT形式のAccess Token
- M2M通信（Client Credentials Grant）

**ユースケース**: B2B SaaS、企業向けプラットフォーム

<details>
<summary>設定JSON例を表示</summary>

```json
{
  "tenant": {
    "id": "saas-tenant",
    "name": "SaaS Platform Tenant",
    "domain": "https://saas.example.com",
    "authorization_provider": "idp-server"
  },
  "authorization_server": {
    "issuer": "https://saas.example.com/saas-tenant",
    "authorization_endpoint": "https://saas.example.com/saas-tenant/v1/authorizations",
    "token_endpoint": "https://saas.example.com/saas-tenant/v1/tokens",
    "userinfo_endpoint": "https://saas.example.com/saas-tenant/v1/userinfo",
    "jwks_uri": "https://saas.example.com/saas-tenant/v1/jwks",
    "scopes_supported": [
      "openid",
      "profile",
      "email",
      "claims:organization_id",
      "claims:department",
      "claims:role"
    ],
    "grant_types_supported": ["authorization_code", "refresh_token", "client_credentials"],
    "response_types_supported": ["code"],
    "extension": {
      "access_token_type": "jwt",
      "access_token_duration": 3600,
      "custom_claims_scope_mapping": true
    }
  }
}
```

</details>

**設定ポイント**:
- `custom_claims_scope_mapping: true`: カスタムクレームをスコープでマッピング
- `claims:organization_id`, `claims:department`: 企業・部署情報をトークンに含める
- `grant_types_supported`: `client_credentials`を追加（M2M通信）
- `access_token_type: "jwt"`: クレーム情報をトークン内に含める

---

## ⚙️ 高度な設定

### Extension設定の詳細

`extension`オブジェクトには、idp-server固有の拡張設定を含めます。

#### トークン設定

| フィールド | デフォルト値 | 説明 | 推奨値 |
|-----------|------------|------|--------|
| `access_token_type` | `opaque` | トークン形式（`opaque` / `jwt`） | Web: `opaque`, SaaS: `jwt` |
| `access_token_duration` | 1800秒 (30分) | アクセストークン有効期限 | 30分～1時間 |
| `id_token_duration` | 3600秒 (60分) | IDトークン有効期限 | 1時間 |
| `refresh_token_duration` | 3600秒 (60分) | リフレッシュトークン有効期限 | Web: 1時間、Mobile: 30日 |
| `rotate_refresh_token` | `true` | リフレッシュトークンローテーション | `true` 推奨 |
| `refresh_token_strategy` | `FIXED` | 期限戦略（`FIXED` / `EXTENDS`） | Mobile: `EXTENDS` |

#### 認可フロー設定

| フィールド | デフォルト値 | 説明 | 推奨値 |
|-----------|------------|------|--------|
| `authorization_code_valid_duration` | 600秒 (10分) | 認可コード有効期限 | 5～10分（RFC 6749推奨） |
| `oauth_authorization_request_expires_in` | 1800秒 (30分) | 認可リクエスト有効期限 | 30分 |
| `authorization_response_duration` | 60秒 (1分) | 認可レスポンス有効期限 | 1分 |
| `default_max_age` | 86400秒 (24時間) | デフォルト最大認証有効期間 | 24時間 |

#### セキュリティ設定

| フィールド | デフォルト値 | 説明 |
|-----------|------------|------|
| `id_token_strict_mode` | `false` | IDトークン厳密モード（OIDC仕様準拠、詳細は下記参照） |

##### id_token_strict_mode - IDトークンクレーム制御

**目的**: IDトークンに含めるクレームの判定ロジックをOIDC仕様に厳密準拠させます。

**デフォルト値**: `false`

**動作の違い**:

| モード | `scope=profile`のみ指定 | `claims`パラメータで明示的要求 | 用途 |
|--------|----------------------|----------------------------|------|
| `false`（デフォルト） | `name`, `given_name`等を**全て含める** | 明示的に要求されたクレームのみ | 後方互換性・利便性優先 |
| `true`（厳密モード） | クレームを**含めない** | 明示的に要求されたクレームのみ | OIDC仕様準拠・FAPI準拠 |

**OIDC仕様の解釈**:
- [OpenID Connect Core 1.0 Section 5.4](https://openid.net/specs/openid-connect-core-1_0.html#ScopeClaims): "`profile` scopeは**UserInfoエンドポイントで**クレームへのアクセスを要求する"
- IDトークンへの包含は、`claims`パラメータでの明示的要求が推奨される

**実装における挙動**:

```java
// GrantIdTokenClaims.java:218-221
if (idTokenStrictMode) {
  return idTokenClaims.hasName();  // claimsパラメータでの明示的要求のみ
}
return scopes.contains("profile");  // scopeだけで含める（非厳密モード）
```

**使用例**:

**非厳密モード（`id_token_strict_mode: false`、デフォルト）**:
```http
GET /authorize?scope=openid profile&...
```
→ IDトークンに `name`, `given_name`, `family_name` 等が含まれる

**厳密モード（`id_token_strict_mode: true`）**:
```http
GET /authorize?scope=openid profile&claims={"id_token":{"name":null}}&...
```
→ `name`のみIDトークンに含まれる（`claims`で明示的要求）

**推奨設定**:
- **一般的なアプリケーション**: `false`（利便性優先）
- **金融グレード（FAPI）**: `true`（仕様準拠・最小限のデータ公開）
- **OIDC4IDA**: `true`（検証済みクレームの厳密制御）

**判断基準**:

| 設定値 | 選択条件 | 理由 |
|-------|---------|------|
| **`true`** | FAPI/OIDC仕様への厳密準拠が必要 | FAPI、OIDC4IDAではIDトークンのクレームを明示的に要求することが求められる |
| **`true`** | クライアントが`claims`パラメータに対応済み | OIDC仕様に準拠した実装が可能 |
| **`false`** | クライアントが`claims`パラメータに対応困難 | レガシーシステム、既存実装の改修コストが高い |
| **`false`** | 開発・テスト環境での利便性を優先 | クレーム取得を簡素化して開発効率を向上 |

**移行戦略**:

新規プロジェクトの場合:
1. **最初から`true`で設計**: 将来的な規制対応を見据える
2. クライアント実装時に`claims`パラメータを考慮

既存プロジェクトの場合:
1. **段階的移行**:
   - Phase 1: `false`のまま、クライアントに`claims`パラメータ実装
   - Phase 2: 検証環境で`true`に変更してテスト
   - Phase 3: 本番環境で`true`に変更
2. **互換性確認**: UserInfoエンドポイントで同じクレームが取得できることを確認

**関連設定**:
- `custom_claims_scope_mapping`: カスタムクレームの`claims:`スコープマッピング
- `claims_supported`: サポートするクレームの宣言（Discovery）

**実装リファレンス**:
- [GrantIdTokenClaims.java:218-221](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/grant_management/grant/GrantIdTokenClaims.java#L218-L221)
- [AuthorizationServerExtensionConfiguration.java:40](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/configuration/AuthorizationServerExtensionConfiguration.java#L40)

#### FAPI設定

| フィールド | デフォルト値 | 説明 |
|-----------|------------|------|
| `fapi_baseline_scopes` | `[]` | FAPI Baseline検証スコープ |
| `fapi_advance_scopes` | `[]` | FAPI Advanced検証スコープ |

**実装リファレンス**: [AuthorizationServerExtensionConfiguration.java](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/configuration/AuthorizationServerExtensionConfiguration.java)

---

### カスタムスコープ

標準スコープ（`openid`, `profile`, `email`）に加えて、独自のスコープを定義できます。

#### 定義方法

`scopes_supported`にカスタムスコープを追加：

```json
{
  "scopes_supported": [
    "openid",
    "profile",
    "email",
    "identity_verification_application",
    "notifications:push",
    "api:read",
    "api:write"
  ]
}
```

#### スコープ命名規則

| パターン | 例 | 用途 |
|---------|---|------|
| `domain:action` | `api:read`, `notifications:push` | 機能別アクセス制御 |
| `claims:field` | `claims:vip_status` | カスタムクレームアクセス |
| 単独名 | `offline_access` | 標準的な追加スコープ |

---

### Claims（クレーム）設定

OpenID Connectでは、**クレーム（claim）**とはユーザーに関する情報項目（名前、メール、電話番号等）を指します。

#### claims_supported - サポートするクレームの宣言

**目的**: 認可サーバーが返却可能なクレーム（ユーザー情報項目）を宣言します。

**OpenID Connect Discovery仕様**: [OpenID Connect Discovery 1.0 Section 3](https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata)

**設定例**:
```json
{
  "claims_supported": [
    "sub",
    "name",
    "email",
    "email_verified",
    "preferred_username",
    "given_name",
    "family_name",
    "picture",
    "phone_number",
    "phone_number_verified"
  ]
}
```

**標準クレーム（OIDC Core仕様）**:

| クレーム | 説明 | 例 |
|---------|------|---|
| `sub` | Subject（ユーザー識別子） | `248289761001` |
| `name` | フルネーム | `Jane Doe` |
| `given_name` | 名 | `Jane` |
| `family_name` | 姓 | `Doe` |
| `email` | メールアドレス | `janedoe@example.com` |
| `email_verified` | メール検証済みフラグ | `true` |
| `preferred_username` | 優先ユーザー名 | `jane.doe` |
| `phone_number` | 電話番号 | `+1 (555) 123-4567` |
| `phone_number_verified` | 電話番号検証済みフラグ | `true` |
| `picture` | プロフィール画像URL | `https://example.com/jane.jpg` |
| `profile` | プロフィールページURL | `https://example.com/jane` |
| `website` | ウェブサイトURL | `https://janedoe.com` |
| `gender` | 性別 | `female` |
| `birthdate` | 生年月日 | `1990-01-01` |
| `zoneinfo` | タイムゾーン | `Asia/Tokyo` |
| `locale` | ロケール | `ja-JP` |
| `address` | 住所（JSON構造） | `{"formatted": "..."}` |

**参照**: [OpenID Connect Core 1.0 Section 5.1 - Standard Claims](https://openid.net/specs/openid-connect-core-1_0.html#StandardClaims)

**実装における重要な注意点**:
- `claims_supported`は**宣言のみ**であり、実際に返却されるかはUserInfoエンドポイントやIDトークンの実装に依存します
- クライアントは`scope`や`claims`リクエストパラメータでクレームを要求します
- 未実装のクレームを宣言すると、クライアントが誤動作する可能性があります

#### claim_types_supported - クレームタイプの宣言

**目的**: クレームの配布方式の種別を宣言します。

**OpenID Connect Discovery仕様**: [OpenID Connect Discovery 1.0 Section 3](https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata)

**現在の実装状況**: **`normal`のみサポート**

**設定例**:
```json
{
  "claim_types_supported": ["normal"]
}
```

**OIDC仕様で定義されているクレームタイプ**:

| タイプ | 説明 | idp-server対応状況 |
|-------|------|--------------------|
| `normal` | クレームを直接UserInfo/IDトークンに含める | ✅ **サポート済み** |
| `aggregated` | クレームを外部ソースから集約して返却 | ❌ 未サポート |
| `distributed` | クレームを外部エンドポイントへのリファレンスとして返却 | ❌ 未サポート |

**参照**: [OpenID Connect Core 1.0 Section 5.6 - Claim Types](https://openid.net/specs/openid-connect-core-1_0.html#ClaimTypes)

**実装リファレンス**: [AuthorizationServerConfiguration.java:58](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/configuration/AuthorizationServerConfiguration.java#L58)

**将来の拡張**: `aggregated`/`distributed`は高度なユースケース（複数IDプロバイダー統合、プライバシー保護）で有用ですが、現状では実装されていません。

---

### カスタムクレーム

#### claims:パターン

`claims:` プレフィックスで、特定のClaimにアクセス：

```json
{
  "scopes_supported": [
    "openid",
    "profile",
    "claims:vip_status",
    "claims:verified_at",
    "claims:account_type"
  ],
  "extension": {
    "custom_claims_scope_mapping": true
  }
}
```

**設定**: `custom_claims_scope_mapping: true` で有効化

#### ユースケース例

**SaaS型マルチテナント**:
```json
"claims:organization_id"  // 所属企業ID
"claims:department"        // 部署
"claims:role"              // 権限ロール
```

**サブスクリプションサービス**:
```json
"claims:subscription_plan"  // サブスクリプションプラン
"claims:billing_status"     // 請求ステータス
```

---

### Tenant Attributes

`attributes`オブジェクトで、テナント固有の動作をカスタマイズできます。

#### 設定可能な属性

| フィールド | デフォルト値 | 説明 |
|-----------|------------|------|
| `cookie_name` | `IDP_SESSION` | セッションCookie名 |
| `use_secure_cookie` | `true` | Secure属性を付与（HTTPS必須） |
| `allow_origins` | `[]` | CORS許可オリジン |
| `signin_page` | `/signin/` | サインインページパス |
| `security_event_log_persistence_enabled` | `true` | イベントログ保存 |

#### 設定例

```json
{
  "tenant": {
    "id": "example-tenant",
    "name": "Example Tenant",
    "domain": "https://auth.example.com"
  },
  "session_config": {
    "cookie_name": "AUTH_SESSION",
    "use_secure_cookie": true
  },
  "cors_config": {
    "allow_origins": [
      "https://app.example.com",
      "https://admin.example.com"
    ]
  },
  "ui_config": {
    "signin_page": "/login/"
  },
  "security_event_log_config": {
    "persistence_enabled": true
  }
}
```

---

## 🔧 Type-Safe Configuration Classes

idp-serverでは、Tenant設定を型安全な6つのConfigurationクラスに分離しています。

### UI Configuration

**目的**: カスタムサインイン/サインアップページの設定

**フィールド**:
```json
{
  "ui_config": {
    "signup_page": "/auth-views/signup/index.html",
    "signin_page": "/auth-views/signin/index.html"
  }
}
```

| フィールド | 型 | デフォルト | 説明 |
|-----------|---|----------|------|
| `signup_page` | string | `/auth-views/signup/index.html` | カスタムサインアップページのパス |
| `signin_page` | string | `/auth-views/signin/index.html` | カスタムサインインページのパス |

**実装**: [UIConfiguration.java](../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/multi_tenancy/tenant/config/UIConfiguration.java)

---

### CORS Configuration

**目的**: クロスオリジンリソース共有の設定

**フィールド**:
```json
{
  "cors_config": {
    "allow_origins": ["https://app.example.com"],
    "allow_headers": "Authorization, Content-Type, Accept, x-device-id",
    "allow_methods": "GET, POST, PUT, PATCH, DELETE, OPTIONS",
    "allow_credentials": true
  }
}
```

| フィールド | 型 | デフォルト | 説明 |
|-----------|---|----------|------|
| `allow_origins` | array[string] | `[]` | 許可するオリジンのリスト |
| `allow_headers` | string | `Authorization, Content-Type, Accept, x-device-id` | 許可するヘッダー |
| `allow_methods` | string | `GET, POST, PUT, PATCH, DELETE, OPTIONS` | 許可するHTTPメソッド |
| `allow_credentials` | boolean | `true` | クレデンシャル送信を許可 |

**実装**: [CorsConfiguration.java](../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/multi_tenancy/tenant/config/CorsConfiguration.java)

---

### Session Configuration

**目的**: セッション管理とCookie設定

**フィールド**:
```json
{
  "session_config": {
    "cookie_name": null,
    "cookie_same_site": "None",
    "use_secure_cookie": true,
    "use_http_only_cookie": true,
    "cookie_path": "/",
    "timeout_seconds": 3600
  }
}
```

| フィールド | 型 | デフォルト | 説明 |
|-----------|---|----------|------|
| `cookie_name` | string \| null | `null` (自動生成) | セッションCookie名 |
| `cookie_same_site` | string | `None` | SameSite属性 (`None`, `Lax`, `Strict`) |
| `use_secure_cookie` | boolean | `true` | Secure属性を使用 |
| `use_http_only_cookie` | boolean | `true` | HttpOnly属性を使用 |
| `cookie_path` | string | `/` | Cookieのパス |
| `timeout_seconds` | number | `3600` | セッションタイムアウト（秒） |

**重要**: `cookie_name`が`null`の場合、`IDP_SERVER_SESSION_{tenant-id-prefix}`形式で自動生成されます。

**実装**: [SessionConfiguration.java](../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/multi_tenancy/tenant/config/SessionConfiguration.java)

---

### Security Event Log Configuration

**目的**: セキュリティイベントログの詳細設定

**フィールド**:
```json
{
  "security_event_log_config": {
    "format": "structured_json",
    "debug_logging": false,
    "stage": "processed",
    "include_user_id": true,
    "include_user_name": true,
    "include_user_ex_sub": true,
    "include_client_id": true,
    "include_ip_address": true,
    "include_user_agent": true,
    "include_event_detail": false,
    "include_user_detail": false,
    "include_user_pii": false,
    "allowed_user_pii_keys": "",
    "include_trace_context": false,
    "service_name": "idp-server",
    "custom_tags": "",
    "tracing_enabled": false,
    "persistence_enabled": false,
    "detail_scrub_keys": ""
  }
}
```

| フィールド | 型 | デフォルト | 説明 |
|-----------|---|----------|------|
| `format` | string | `structured_json` | ログフォーマット (`structured_json`, `plain_text`) |
| `debug_logging` | boolean | `false` | デバッグログ出力を有効化 |
| `stage` | string | `processed` | ログ出力タイミング |
| `include_user_id` | boolean | `true` | ユーザーIDを含める |
| `include_user_name` | boolean | `true` | ユーザー名を含める |
| `include_user_ex_sub` | boolean | `true` | 外部ユーザーIDを含める |
| `include_client_id` | boolean | `true` | クライアントIDを含める |
| `include_ip_address` | boolean | `true` | IPアドレスを含める |
| `include_user_agent` | boolean | `true` | User-Agentを含める |
| `include_event_detail` | boolean | `false` | イベント詳細を含める |
| `include_user_detail` | boolean | `false` | ユーザー詳細を含める |
| `include_user_pii` | boolean | `false` | 個人情報を含める（⚠️ 注意） |
| `allowed_user_pii_keys` | string | `""` | 許可するPIIキー（カンマ区切り） |
| `include_trace_context` | boolean | `false` | トレーシング情報を含める |
| `service_name` | string | `idp-server` | サービス名 |
| `custom_tags` | string | `""` | カスタムタグ（カンマ区切り） |
| `tracing_enabled` | boolean | `false` | 分散トレーシングを有効化 |
| `persistence_enabled` | boolean | `false` | データベース永続化を有効化 |
| `detail_scrub_keys` | string | (必須キー) | スクラブするキー（カンマ区切り） |

**デフォルトでスクラブされるキー**: `authorization`, `cookie`, `password`, `secret`, `token`, `access_token`, `refresh_token`, `api_key`, `api_secret`

**プライバシー推奨設定**:
- 本番環境: `include_user_pii: false`, `include_user_detail: false`
- デバッグ: `debug_logging: true`, `include_event_detail: true`（一時的のみ）

**実装**: [SecurityEventLogConfiguration.java](../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/security/log/SecurityEventLogConfiguration.java)

---

### Security Event User Attribute Configuration

**目的**: セキュリティイベントに含めるユーザー属性の制御

**フィールド**:
```json
{
  "security_event_user_config": {
    "include_id": true,
    "include_name": true,
    "include_external_user_id": true,
    "include_email": false,
    "include_phone_number": false,
    "include_given_name": false,
    "include_family_name": false,
    "include_preferred_username": false,
    "include_profile": false,
    "include_picture": false,
    "include_website": false,
    "include_gender": false,
    "include_birthdate": false,
    "include_zoneinfo": false,
    "include_locale": false,
    "include_address": false,
    "include_roles": false,
    "include_permissions": false,
    "include_current_tenant": false,
    "include_assigned_tenants": false,
    "include_verified_claims": false
  }
}
```

**主要フィールドの説明**:

| フィールド | 記録される値 | 説明 |
|-----------|-------------|------|
| `include_id` | `sub` | ユーザーの内部識別子 |
| `include_name` | `preferred_username` | 管理者が識別しやすい名前（IDポリシーにより決定） |
| `include_external_user_id` | `ex_sub` | 外部システム連携用の識別子 |

> **注意**: `include_name`に記録される値は、テナントのIDポリシー設定により決定されます。
> - `EMAIL_OR_EXTERNAL_USER_ID`（デフォルト）: メールアドレスが記録される
> - `USERNAME_OR_EXTERNAL_USER_ID`: ユーザー名が記録される

**デフォルト**: `include_id`、`include_name`、`include_external_user_id`が`true`（管理者による識別のため`include_name`を追加）

**プライバシーレベル別設定**:

| レベル | 設定 | 用途 |
|--------|------|------|
| **最小** | `include_id`, `include_name`, `include_external_user_id`のみ | 本番環境（推奨） |
| **標準** | + `include_email`, `include_roles` | 監査要件がある場合 |
| **詳細** | + `include_phone_number` | デバッグ・調査時（一時的） |
| **フル** | 全て`true` | ❌ 非推奨（GDPR/個人情報保護法違反リスク） |

**実装**: [SecurityEventUserAttributeConfiguration.java](../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/security/event/SecurityEventUserAttributeConfiguration.java)

---

### Identity Policy Configuration

**目的**: ユーザー識別キーとパスワードポリシーの設定

**フィールド**:
```json
{
  "identity_policy_config": {
    "identity_unique_key_type": "EMAIL_OR_EXTERNAL_USER_ID",
    "password_policy": {
      "min_length": 8,
      "max_length": 72,
      "require_uppercase": false,
      "require_lowercase": false,
      "require_number": false,
      "require_special_char": false,
      "max_history": 0
    }
  }
}
```

| フィールド | 型 | デフォルト | 説明 |
|-----------|---|----------|------|
| `identity_unique_key_type` | string | `EMAIL_OR_EXTERNAL_USER_ID` | ユニークキー種別 |
| `password_policy` | object | デフォルトポリシー | パスワードポリシー設定 |

#### identity_unique_key_type - ユーザー識別キー戦略

**許可される値**:

| 値 | 説明 | 用途 |
|---|------|------|
| `USERNAME` | ユーザー名を一意キーとして使用 | ユーザー名ベース認証 |
| `USERNAME_OR_EXTERNAL_USER_ID` | ユーザー名、なければ外部ユーザーID | 外部IdP連携（ユーザー名優先） |
| `EMAIL` | メールアドレスを一意キーとして使用 | メールベース認証（厳密） |
| `EMAIL_OR_EXTERNAL_USER_ID` | メールアドレス、なければ外部ユーザーID | **推奨**：外部IdP連携（メール優先） |
| `PHONE` | 電話番号を一意キーとして使用 | 電話番号ベース認証 |
| `PHONE_OR_EXTERNAL_USER_ID` | 電話番号、なければ外部ユーザーID | 外部IdP連携（電話番号優先） |
| `EXTERNAL_USER_ID` | 外部ユーザーIDを一意キーとして使用 | 外部システム完全連携 |

**デフォルト値**: `EMAIL_OR_EXTERNAL_USER_ID` (Issue #729対応)

**フォールバック動作**:

フォールバックが発生した場合（例: GitHubでメール非公開）、`preferred_username`は以下の形式で設定されます：
- **外部IdP**: `{provider_id}.{external_user_id}` (例: `google.123456`, `github.987654`)
- **ローカル(idp-server)**: `{external_user_id}` (例: `550e8400-e29b-41d4-a716-446655440000`)

**重要**: フォールバックが発生しない場合（メール等が存在する場合）、`preferred_username`は通常の値（メールアドレス等）が設定されます。

**推奨設定**:
- **外部IdP統合**: `EMAIL_OR_EXTERNAL_USER_ID` - GitHub等でメール非公開の場合に対応
- **独自ユーザーDB**: `EMAIL` - メールアドレスを厳密に使用
- **電話番号認証**: `PHONE_OR_EXTERNAL_USER_ID` - SMS認証等

#### password_policy - パスワードポリシー設定

**OWASP/NIST準拠**: [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html), [NIST SP 800-63B](https://pages.nist.gov/800-63-3/sp800-63b.html)

| フィールド | 型 | デフォルト | 説明 |
|-----------|---|----------|------|
| `min_length` | number | `8` | 最小文字数 |
| `max_length` | number | `72` | 最大文字数（BCrypt制限） |
| `require_uppercase` | boolean | `false` | 大文字必須 |
| `require_lowercase` | boolean | `false` | 小文字必須 |
| `require_number` | boolean | `false` | 数字必須 |
| `require_special_char` | boolean | `false` | 特殊文字必須 |
| `max_history` | number | `0` | パスワード履歴保持数（将来対応 Issue #741） |

**NIST推奨**: 最小8文字、複雑性要件なし（ユーザビリティ優先）

**使用例**:

**パターン1: NIST推奨（デフォルト）**
```json
{
  "identity_policy_config": {
    "identity_unique_key_type": "EMAIL_OR_EXTERNAL_USER_ID",
    "password_policy": {
      "min_length": 8,
      "require_uppercase": false,
      "require_lowercase": false,
      "require_number": false,
      "require_special_char": false
    }
  }
}
```

**パターン2: 金融グレード（高セキュリティ）**
```json
{
  "identity_policy_config": {
    "identity_unique_key_type": "EMAIL",
    "password_policy": {
      "min_length": 12,
      "require_uppercase": true,
      "require_lowercase": true,
      "require_number": true,
      "require_special_char": true
    }
  }
}
```

**パターン3: 外部IdP統合（メールフォールバック）**
```json
{
  "identity_policy_config": {
    "identity_unique_key_type": "EMAIL_OR_EXTERNAL_USER_ID"
  }
}
```
→ GitHub等でメールを非公開にしているユーザーでも`external_user_id`で識別可能

**パターン4: 電話番号認証**
```json
{
  "identity_policy_config": {
    "identity_unique_key_type": "PHONE_OR_EXTERNAL_USER_ID",
    "password_policy": {
      "min_length": 6
    }
  }
}
```

**実装リファレンス**:
- [TenantIdentityPolicy.java](../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/multi_tenancy/tenant/policy/TenantIdentityPolicy.java)
- [PasswordPolicyConfig.java](../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/multi_tenancy/tenant/policy/PasswordPolicyConfig.java)

---

## 🛠️ 運用ノウハウ

### パフォーマンスチューニング

#### トークン有効期限とパフォーマンス

| 設定 | パフォーマンス影響 | セキュリティ影響 |
|------|------------------|----------------|
| **短い有効期限** | 頻繁なトークン更新→負荷増 | セキュリティ向上 |
| **長い有効期限** | トークン更新頻度減→負荷減 | セキュリティ低下 |

**推奨バランス**:
- Access Token: 30分～1時間
- Refresh Token: 1時間～30日（用途による）

#### Refresh Tokenの活用

```json
{
  "extension": {
    "access_token_duration": 1800,      // 30分（短め）
    "refresh_token_duration": 2592000,  // 30日（長め）
    "rotate_refresh_token": true,       // セキュリティ維持
    "refresh_token_strategy": "EXTENDS" // UX向上（リフレッシュ毎に期限延長）
  }
}
```

**メリット**:
- Access Tokenは短くしてセキュリティ確保
- Refresh Tokenで頻繁な再認証を回避
- ユーザー体験とセキュリティの両立

---

### セキュリティベストプラクティス

#### 1. Secure Cookie必須

```json
{
  "session_config": {
    "use_secure_cookie": true,  // 必須（HTTPS環境）
    "use_http_only_cookie": true,  // XSS対策
    "cookie_same_site": "Strict"  // CSRF対策（本番環境推奨）
  }
}
```

#### 2. CORS設定の適切な管理

```json
{
  "cors_config": {
    "allow_origins": [
      "https://app.example.com"  // 必要最小限のオリジンのみ
    ],
    "allow_credentials": true
  }
}
```

❌ **危険**: `allow_origins: ["*"]` は本番環境では絶対に使用しない

#### 3. トークン有効期限の適切な設定

| 用途 | Access Token | Refresh Token |
|------|-------------|---------------|
| **金融グレード** | 5～10分 | 1時間 |
| **標準Webアプリ** | 30分～1時間 | 1時間～1日 |
| **モバイルアプリ** | 1時間 | 7～30日 |

#### 4. 強力なクライアント認証

金融グレード・機密データアクセス:
```json
{
  "token_endpoint_auth_methods_supported": [
    "private_key_jwt",
    "tls_client_auth"
  ]
}
```

通常のアプリケーション:
```json
{
  "token_endpoint_auth_methods_supported": [
    "client_secret_post",
    "client_secret_basic"
  ]
}
```

#### 5. Pairwise Subject（プライバシー保護）

```json
{
  "subject_types_supported": ["pairwise"]
}
```

**効果**: クライアント間でユーザー識別子を分離（追跡防止）

---

### トラブルシューティング

#### 問題1: issuerの不一致

**エラー**:
```json
{
  "error": "invalid_issuer",
  "error_description": "issuer does not match token issuer"
}
```

**原因**: `issuer`が実際のURLと一致しない

**解決策**:
```json
{
  "issuer": "https://app.example.com/{tenant-id}"  // 実際のアクセスURLと一致させる
}
```

#### 問題2: カスタムスコープ未定義

**エラー**:
```json
{
  "error": "invalid_scope",
  "error_description": "scope 'claims:custom_field' is not supported"
}
```

**原因**: `scopes_supported`に未定義

**解決策**:
```json
{
  "scopes_supported": [
    "openid",
    "profile",
    "email",
    "claims:custom_field"  // 追加
  ]
}
```

#### 問題3: 認可コードが期限切れ

**エラー**:
```json
{
  "error": "invalid_grant",
  "error_description": "authorization code has expired"
}
```

**原因**: `authorization_code_valid_duration`が短すぎる、またはリダイレクトに時間がかかりすぎる

**解決策**:
```json
{
  "extension": {
    "authorization_code_valid_duration": 600  // 10分（推奨）
  }
}
```

#### 問題4: CORS エラー

**エラー**:
```
Access to XMLHttpRequest at 'https://idp.example.com/...' from origin 'https://app.example.com' has been blocked by CORS policy
```

**原因**: `allow_origins`にオリジンが含まれていない

**解決策**:
```json
{
  "cors_config": {
    "allow_origins": ["https://app.example.com"]
  }
}
```

---

## 📚 リファレンス（付録）

### 全フィールド一覧表

#### Tenantセクション

| フィールド | 必須 | 型 | 説明 | 例 |
|-----------|------|---|------|---|
| `id` | ✅ | string (UUID) | テナントID | `18ffff8d-...` |
| `name` | ✅ | string | テナント名（最大255文字） | `Example Tenant` |
| `domain` | ✅ | string (URI) | 認証画面のドメイン | `https://auth.example.com` |
| `description` | ❌ | string | テナント説明 | `説明文` |
| `authorization_provider` | ✅ | string | 認可プロバイダー（固定値） | `idp-server` |
| `attributes` | ❌ | object | テナント固有属性 | オブジェクト |

**OpenAPI仕様**: [swagger-control-plane-ja.yaml:4627-4665](../../openapi/swagger-control-plane-ja.yaml#L4627-L4665)

#### Authorization Serverセクション

##### 必須フィールド

| フィールド | 説明 |
|-----------|------|
| `issuer` | Issuer識別子（HTTPS URL、クエリ/フラグメント不可） |
| `authorization_endpoint` | 認可エンドポイント（HTTPS URL） |
| `token_endpoint` | トークンエンドポイント |
| `jwks_uri` | JWKS URI（HTTPS URL） |
| `scopes_supported` | サポートするスコープ（`openid`必須） |
| `response_types_supported` | サポートするResponse Type（`code`必須） |
| `response_modes_supported` | サポートするResponse Mode |
| `subject_types_supported` | Subject識別子タイプ（`public`/`pairwise`） |

**OpenAPI仕様**: [swagger-control-plane-ja.yaml:3616-3627](../../openapi/swagger-control-plane-ja.yaml#L3616-L3627)

##### 推奨フィールド

| フィールド | 説明 |
|-----------|------|
| `userinfo_endpoint` | UserInfoエンドポイント（HTTPS URL） |
| `registration_endpoint` | 動的クライアント登録エンドポイント |
| `claims_supported` | サポートするクレーム（ユーザー情報項目）のリスト |
| `claim_types_supported` | サポートするクレームタイプ（現状は`normal`のみ） |

##### オプショナルフィールド

| フィールド | デフォルト値 |
|-----------|------------|
| `grant_types_supported` | `["authorization_code", "implicit"]` |
| `acr_values_supported` | `[]` |
| `token_endpoint_auth_methods_supported` | `["client_secret_basic"]` |
| `id_token_signing_alg_values_supported` | `["RS256"]` |
| `extension` | - |

---

### デフォルト値一覧

#### Extension設定デフォルト値

| フィールド | デフォルト値 | 単位 |
|-----------|------------|------|
| `access_token_duration` | 1800 | 秒 (30分) |
| `refresh_token_duration` | 3600 | 秒 (60分) |
| `id_token_duration` | 3600 | 秒 (60分) |
| `authorization_code_valid_duration` | 600 | 秒 (10分) |
| `oauth_authorization_request_expires_in` | 1800 | 秒 (30分) |
| `authorization_response_duration` | 60 | 秒 (1分) |
| `default_max_age` | 86400 | 秒 (24時間) |
| `access_token_type` | `opaque` | - |
| `rotate_refresh_token` | `true` | - |
| `refresh_token_strategy` | `FIXED` | - |
| `custom_claims_scope_mapping` | `false` | - |
| `id_token_strict_mode` | `false` | - |

#### Tenant Attributesデフォルト値

| フィールド | デフォルト値 |
|-----------|------------|
| `cookie_name` | `IDP_SESSION` |
| `use_secure_cookie` | `true` |
| `allow_origins` | `[]` |
| `signin_page` | `/signin/` |
| `security_event_log_persistence_enabled` | `true` |

---

### 実装クラスへのリンク

**Core**:
- [AuthorizationServerConfiguration.java](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/configuration/AuthorizationServerConfiguration.java)
- [AuthorizationServerExtensionConfiguration.java](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/configuration/AuthorizationServerExtensionConfiguration.java)
- [Tenant.java](../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/multi_tenancy/tenant/Tenant.java)

**Control Plane**:
- [TenantManagementRegistrationContextCreator.java](../../../libs/idp-server-control-plane/src/main/java/org/idp/server/control_plane/management/tenant/TenantManagementRegistrationContextCreator.java)

---

## 次のステップ

✅ Tenant設定を理解した！

### 次に読むべきドキュメント

1. [Client設定](./client.md) - クライアント登録とOAuth 2.0クライアント設定
2. [Authentication Policy](./authentication-policy.md) - 認証ポリシーとMFA設定

---

**最終更新**: 2025-01-15
