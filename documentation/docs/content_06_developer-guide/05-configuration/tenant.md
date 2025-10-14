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
- [how-to-01: 組織初期化](../../content_05_how-to/how-to-01-organization-initialization.md)完了
- [how-to-02: OAuth/OIDC認証の最小設定](../../content_05_how-to/how-to-02-tenant-setup.md)完了
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
| 1 | **Webアプリケーション（標準）** | ユーザーがWebブラウザから安全にログインし、セッション中は再認証なしでサービスを利用する | • Opaque Token<br>• Access Token: 30分<br>• Refresh Token: 1時間 | [詳細](#1-webアプリケーション向け標準) |
| 2 | **モバイルアプリ（PKCE）** | モバイルユーザーが再ログインなしで長期間（30日）アプリを利用し続ける | • PKCE必須<br>• 長期Refresh Token（30日）<br>• EXTENDS戦略 | [詳細](#2-モバイルアプリ向けpkce対応) |
| 3 | **金融グレード（FAPI）** | 銀行顧客が口座・取引情報に安全にアクセスし、厳格なセキュリティ基準を満たす | • Private Key JWT / mTLS<br>• Pairwise Subject<br>• Access Token: 10分 | [詳細](#3-金融グレードfapi準拠) |
| 4 | **SaaS型マルチテナント** | 企業ユーザーが所属組織・部署情報を含むトークンでSaaSサービスにアクセスする | • JWT Token<br>• カスタムクレーム<br>• M2M通信対応 | [詳細](#4-saas型マルチテナント) |

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
    "authorization_provider": "idp-server",
    "attributes": {
      "use_secure_cookie": true,
      "allow_origins": ["https://banking.example.com"]
    }
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
| `id_token_strict_mode` | `false` | IDトークン厳密モード（FAPI準拠時は`true`） |

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
    "attributes": {
      "cookie_name": "AUTH_SESSION",
      "use_secure_cookie": true,
      "allow_origins": [
        "https://app.example.com",
        "https://admin.example.com"
      ],
      "signin_page": "/login/",
      "security_event_log_persistence_enabled": true
    }
  }
}
```

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
  "tenant": {
    "attributes": {
      "use_secure_cookie": true  // 必須（HTTPS環境）
    }
  }
}
```

#### 2. CORS設定の適切な管理

```json
{
  "tenant": {
    "attributes": {
      "allow_origins": [
        "https://app.example.com"  // 必要最小限のオリジンのみ
      ]
    }
  }
}
```

❌ **危険**: `["*"]` は本番環境では絶対に使用しない

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
  "tenant": {
    "attributes": {
      "allow_origins": ["https://app.example.com"]
    }
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
3. [User Management](./user.md) - ユーザー管理とプロファイル設定

---

**最終更新**: 2025-01-15
