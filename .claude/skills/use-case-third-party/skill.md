---
name: use-case-third-party
description: サードパーティ連携ユースケースの設定ガイド。クライアント登録（Web/モバイル/M2M）、トークン戦略、フェデレーション設定（Google/Azure AD）のヒアリングと設定JSONを提供。
---

# サードパーティ連携

## ヒアリング項目

| # | 決めること | 選択肢 | 影響する設定 |
|---|-----------|--------|-------------|
| 1 | クライアント種別 | Webアプリ / モバイル / M2M | クライアント設定 |
| 2 | スコープ設計 | 公開データ / 個人データ読取 / 個人データ書込 / 管理 | 認可サーバー `scopes_supported` |
| 3 | トークン有効期限 | AT: 1時間〜1日 / RT: 1日〜30日 | 認可サーバー拡張設定 or クライアント拡張設定 |
| 4 | RTローテーション | する / しない | 認可サーバー拡張設定 or クライアント拡張設定 |
| 5 | RT有効期限戦略 | 固定 / 延長（使用ごと） | 認可サーバー拡張設定 or クライアント拡張設定 |
| 6 | 外部IdP連携 | Google / Azure AD / カスタムOIDC / なし | フェデレーション設定 |

---

## 設定対象と手順

### 1. クライアント登録

**API**: `POST /v1/management/organizations/{org-id}/tenants/{tenant-id}/clients`

**Webアプリ（Confidential Client）**:
```json
{
  "client_id": "{uuid}",
  "client_secret": "{ランダム文字列}",
  "client_name": "{アプリ名}",
  "redirect_uris": ["{コールバックURL}"],
  "response_types": ["code"],
  "grant_types": ["authorization_code", "refresh_token"],
  "scope": "openid profile email",
  "token_endpoint_auth_method": "client_secret_basic",
  "application_type": "web"
}
```

**モバイルアプリ（Public Client + PKCE）**:
```json
{
  "client_id": "{uuid}",
  "client_name": "{アプリ名}",
  "redirect_uris": ["{カスタムスキーム}://callback"],
  "response_types": ["code"],
  "grant_types": ["authorization_code", "refresh_token"],
  "scope": "openid profile email",
  "token_endpoint_auth_method": "none",
  "application_type": "native"
}
```

**M2M（Machine-to-Machine）**:
```json
{
  "client_id": "{uuid}",
  "client_secret": "{ランダム文字列}",
  "client_name": "{サービス名}",
  "redirect_uris": [],
  "response_types": [],
  "grant_types": ["client_credentials"],
  "scope": "api:read api:write",
  "token_endpoint_auth_method": "client_secret_basic",
  "application_type": "web"
}
```

### 2. トークン戦略設定

**サーバーレベル（認可サーバー拡張設定更新）**:

**API**: `PUT /v1/management/organizations/{org-id}/tenants/{tenant-id}/authorization-server`

```json
{
  "extension": {
    "access_token_duration": 1800,
    "refresh_token_duration": 3600,
    "refresh_token_strategy": "FIXED",
    "rotate_refresh_token": true,
    "id_token_duration": 3600
  }
}
```

**クライアントレベル（個別オーバーライド）**:

**API**: `PUT /v1/management/organizations/{org-id}/tenants/{tenant-id}/clients/{client-id}`

```json
{
  "extension": {
    "access_token_duration": 3600,
    "refresh_token_duration": 2592000,
    "refresh_token_strategy": "EXTENDS",
    "rotate_refresh_token": true
  }
}
```

**ヒアリング結果の反映先**:

| ヒアリング項目 | JSONキー | 値の例 |
|--------------|---------|-------|
| AT有効期限（秒） | `access_token_duration` | `1800`（30分）, `3600`（1時間） |
| RT有効期限（秒） | `refresh_token_duration` | `3600`（1時間）, `2592000`（30日） |
| RTローテーション | `rotate_refresh_token` | `true`（推奨）/ `false` |
| RT戦略 | `refresh_token_strategy` | `FIXED`（推奨）/ `EXTENDS` |

#### トークン戦略の推奨パターン

| パターン | rotate | strategy | セキュリティ | 推奨度 |
|---------|--------|----------|----------|-------|
| ローテーション+固定 | `true` | `FIXED` | 最高 | 推奨 |
| ローテーション+延長 | `true` | `EXTENDS` | 高 | 選択可 |
| 非ローテーション+固定 | `false` | `FIXED` | 中 | 注意 |
| 非ローテーション+延長 | `false` | `EXTENDS` | 低 | 注意 |

### 3. フェデレーション設定（外部IdP連携）

**API**: `POST /v1/management/organizations/{org-id}/tenants/{tenant-id}/federation-configurations`

**Google連携例**:
```json
{
  "type": "oidc",
  "sso_provider": "google",
  "enabled": true,
  "payload": {
    "type": "standard",
    "provider": "standard",
    "issuer": "https://accounts.google.com",
    "issuer_name": "google",
    "authorization_endpoint": "https://accounts.google.com/o/oauth2/v2/auth",
    "token_endpoint": "https://oauth2.googleapis.com/token",
    "userinfo_endpoint": "https://openidconnect.googleapis.com/v1/userinfo",
    "jwks_uri": "https://www.googleapis.com/oauth2/v3/certs",
    "client_id": "{Google Client ID}",
    "client_secret": "{Google Client Secret}",
    "redirect_uri": "https://{domain}/{tenant-id}/v1/authorizations/federations/oidc/callback",
    "scopes_supported": ["openid", "profile", "email"],
    "userinfo_mapping_rules": [
      { "from": "$.http_request.response_body.sub", "to": "external_user_id" },
      { "from": "$.http_request.response_body.email", "to": "email" },
      { "from": "$.http_request.response_body.name", "to": "name" },
      { "from": "$.http_request.response_body.picture", "to": "picture" }
    ]
  }
}
```

**Azure AD連携例**:
```json
{
  "type": "oidc",
  "sso_provider": "azure_ad",
  "enabled": true,
  "payload": {
    "type": "standard",
    "provider": "standard",
    "issuer": "https://login.microsoftonline.com/{azure-tenant-id}/v2.0",
    "issuer_name": "azure_ad",
    "authorization_endpoint": "https://login.microsoftonline.com/{azure-tenant-id}/oauth2/v2.0/authorize",
    "token_endpoint": "https://login.microsoftonline.com/{azure-tenant-id}/oauth2/v2.0/token",
    "userinfo_endpoint": "https://graph.microsoft.com/oidc/userinfo",
    "client_id": "{Azure Client ID}",
    "client_secret": "{Azure Client Secret}",
    "redirect_uri": "https://{domain}/{tenant-id}/v1/authorizations/federations/oidc/callback",
    "scopes_supported": ["openid", "profile", "email"],
    "userinfo_mapping_rules": [
      { "from": "$.http_request.response_body.sub", "to": "external_user_id" },
      { "from": "$.http_request.response_body.email", "to": "email" },
      { "from": "$.http_request.response_body.name", "to": "name" }
    ]
  }
}
```

### 4. クライアントでフェデレーション有効化

**API**: `PUT /v1/management/organizations/{org-id}/tenants/{tenant-id}/clients/{client-id}`

```json
{
  "extension": {
    "available_federations": [
      {
        "id": "{federation-config-id}",
        "type": "oidc",
        "sso_provider": "google"
      }
    ]
  }
}
```

## 設定例ファイル参照

- クライアント: `config/examples/e2e/.../clients/publicClient.json`, `clientSecretBasic.json`
- フェデレーション: `config/examples/e2e/.../federation/oidc/google.json`
- 認可サーバー: `config/examples/e2e/.../authorization-server/idp-server.json`

## 関連ドキュメント

- `documentation/docs/content_05_how-to/phase-1-foundation/04-client-registration.md`
- `documentation/docs/content_05_how-to/phase-2-security/02-token-strategy.md`
- `documentation/docs/content_05_how-to/phase-3-advanced/01-federation-setup.md`
- `documentation/docs/content_02_quickstart/quickstart-08-third-party-integration.md`

$ARGUMENTS
