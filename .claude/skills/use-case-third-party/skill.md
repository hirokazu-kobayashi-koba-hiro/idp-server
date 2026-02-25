---
name: use-case-third-party
description: サードパーティ連携ユースケースの設定ガイド。外部アプリがIdPに接続するためのクライアント登録（Web/モバイル/M2M）、スコープ設計、トークン戦略のヒアリングと設定JSONを提供。
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

### 3. クレーム設定（認可サーバー更新）

> **重要**: この設定が無いと UserInfo / ID Token が `sub` のみしか返さない。
> 詳細は `use-case-login` スキルの「クレーム設定」セクションを参照。

認可サーバーの `claims_supported` に返したいクレーム一覧を設定する。
標準的な設定は `config/templates/tenant-template.json` を参照。

## 設定確認チェックリスト

| # | 確認観点 | 設定箇所 | よくあるミス |
|---|---------|---------|------------|
| 1 | `claims_supported` が設定済み | 認可サーバー | 未設定で UserInfo/ID Token が `sub` のみ |
| 2 | `ui_config.base_url` が認証UIのオリジン | テナント `ui_config` | APIサーバーURLを設定してしまう |
| 3 | `cors_config` に全フィールド設定 | テナント `cors_config` | `allow_origins` だけで `allow_headers`, `allow_methods`, `allow_credentials` が抜ける |
| 4 | クライアントの `grant_types` がユースケースに合致 | クライアント設定 | Webアプリに `client_credentials` を設定、M2Mに `authorization_code` を設定 |
| 5 | クライアントの `token_endpoint_auth_method` が種別に合致 | クライアント設定 | Public Client に `client_secret_basic` を設定してしまう |
| 6 | `scopes_supported` にクライアントが要求するスコープが含まれる | 認可サーバー | カスタムスコープの追加漏れ |
| 7 | トークン戦略（rotate + strategy）が要件に合致 | 認可サーバー拡張設定 | `rotate_refresh_token: false` でセキュリティリスク |

### 動作確認時のprompt値

| テスト | prompt値 | 目的 |
|--------|---------|------|
| ユーザー登録 | `prompt=create` | Sign Up画面を直接表示 |
| 再認証 | `prompt=login` | 既存セッションを無視して再認証を強制 |

## 設定例ファイル参照

- テンプレート: `config/templates/use-cases/third-party/`
- クライアント: `config/examples/e2e/.../clients/publicClient.json`, `clientSecretBasic.json`
- 認可サーバー: `config/examples/e2e/.../authorization-server/idp-server.json`

## 関連ドキュメント

- `documentation/docs/content_05_how-to/phase-1-foundation/04-client-registration.md`
- `documentation/docs/content_05_how-to/phase-2-security/02-token-strategy.md`
- `documentation/docs/content_02_quickstart/quickstart-08-third-party-integration.md`

$ARGUMENTS
