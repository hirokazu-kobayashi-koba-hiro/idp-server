---
sidebar_position: 3
title: Organizerテナント管理UI設定
description: Organizerテナントを管理ダッシュボード（UI）経由で操作するために必要な設定
---

# Organizerテナント管理UI設定

## このドキュメントの目的

[組織初期化](./02-organization-initialization.md)で作成したOrganizerテナントを、**管理ダッシュボード（UI）経由で操作できるようにする**ための設定ガイドです。

### 前提条件
- [組織初期化](./02-organization-initialization.md)が完了していること
- Organizerテナントが作成済みであること

---

## 背景

組織初期化（Onboarding API）で作成されるOrganizerテナントは、デフォルトでは**APIアクセスのみ**を想定した最小構成です。管理ダッシュボード（UI）から操作するには、以下の追加設定が必要です。

---

## 必須設定一覧

### 1. ui_config — 管理UI接続設定

管理UIのURLとサインインページのパスを指定します。

```json
{
  "tenant": {
    "ui_config": {
      "base_url": "https://admin.example.com",
      "signin_page": "/signin/",
      "signup_page": "/signup/"
    }
  }
}
```

| フィールド | 説明 |
|-----------|------|
| `base_url` | 管理UIのオリジンURL。APIサーバーURLではなく、UIのURLを指定する |
| `signin_page` | サインインページのパス。FIDO2認証を使う場合は `/signin/fido2/` |
| `signup_page` | サインアップページのパス |

:::warning
`base_url` にはAPIサーバーのURLではなく、管理UIのオリジンURLを設定してください。APIサーバーとUIが同一オリジンの場合は同じURLで問題ありません。
:::

### 2. cors_config — CORS設定

管理UIからのリクエストを許可するため、CORSを適切に設定します。

```json
{
  "tenant": {
    "cors_config": {
      "allow_origins": [
        "https://api.example.com",
        "https://admin.example.com"
      ],
      "allow_headers": "Authorization, Content-Type, Accept, x-device-id, X-SSL-Client-Cert",
      "allow_methods": "GET, POST, PUT, PATCH, DELETE, OPTIONS",
      "allow_credentials": true
    }
  }
}
```

| フィールド | 説明 |
|-----------|------|
| `allow_origins` | 許可するオリジン。APIサーバーとUIの両方のオリジンを含める |
| `allow_headers` | 許可するリクエストヘッダー |
| `allow_methods` | 許可するHTTPメソッド |
| `allow_credentials` | Cookie送信を許可（セッション管理に必須） |

:::caution
`allow_origins` のみの設定では不十分です。`allow_headers`、`allow_methods`、`allow_credentials` も必ず設定してください。
:::

### 3. claims_supported — サポートクレーム宣言

UserInfoエンドポイントやIDトークンで返却可能なクレームを宣言します。

```json
{
  "authorization_server": {
    "claims_supported": [
      "sub", "iss", "auth_time", "acr",
      "name", "given_name", "family_name",
      "email", "email_verified",
      "phone_number", "phone_number_verified",
      "address", "birthdate"
    ]
  }
}
```

:::danger 重要
**この設定がないと、UserInfo / IDトークンは `sub` のみしか返しません。** 管理UIでユーザー情報を表示するために必須です。
:::

### 4. カスタムクレーム（roles / permissions / assigned_tenants）

管理UIでロール・権限・担当テナント情報を取得するために、カスタムクレーム用のスコープと設定が必要です。

#### scopes_supported に追加

```json
{
  "authorization_server": {
    "scopes_supported": [
      "openid", "profile", "email", "management",
      "claims:roles",
      "claims:permissions",
      "claims:assigned_tenants"
    ]
  }
}
```

#### custom_claims_scope_mapping を有効化

```json
{
  "authorization_server": {
    "extension": {
      "custom_claims_scope_mapping": true
    }
  }
}
```

#### クライアントのスコープにも追加

```json
{
  "client": {
    "scope": "openid profile email management claims:roles claims:permissions claims:assigned_tenants"
  }
}
```

:::danger 重要
`custom_claims_scope_mapping: true` がないと、`claims:*` プレフィックス付きスコープが機能せず、UserInfo / IDトークンにカスタムクレーム（roles, permissions, assigned_tenants）が含まれません。
:::

### 5. statistics_enabled — テナント統計

管理ダッシュボードでテナント統計情報を表示するために有効化します。

```json
{
  "tenant": {
    "security_event_log_config": {
      "statistics_enabled": true
    }
  }
}
```

### 6. エンドポイントキー名の修正

以下のキー名が正しいことを確認してください（旧キー名は非推奨）。

| 正しいキー名 | 旧キー名（非推奨） |
|-------------|------------------|
| `introspection_endpoint` | `token_introspection_endpoint` |
| `revocation_endpoint` | `token_revocation_endpoint` |

---

## 設定テンプレート

ユースケーステンプレートの `onboarding-template.json` にはこれらの設定がすべて含まれています。

```
config/templates/use-cases/{ユースケース名}/onboarding-template.json
```

新しいOrganizerテナントを作成する場合は、これらのテンプレートをベースにしてください。

---

## チェックリスト

Organizerテナント作成後、管理UIでの動作確認前に以下を確認してください。

- [ ] `ui_config.base_url` が管理UIのオリジンURLになっている（APIサーバーURLではない）
- [ ] `cors_config` に `allow_headers`、`allow_methods`、`allow_credentials` が設定されている
- [ ] `claims_supported` が設定されている（未設定だと `sub` のみ返却）
- [ ] `scopes_supported` に `claims:roles`、`claims:permissions`、`claims:assigned_tenants` が含まれている
- [ ] `extension.custom_claims_scope_mapping` が `true` になっている
- [ ] `client.scope` にカスタムクレーム用スコープが含まれている
- [ ] `security_event_log_config.statistics_enabled` が `true` になっている
- [ ] `introspection_endpoint` / `revocation_endpoint` のキー名が正しい

---

## 関連ドキュメント

- [組織初期化ガイド](./02-organization-initialization.md) — Organizerテナントの作成手順
- [テナント設定](./03-tenant-setup.md) — ビジネステナントの作成
- [テナント設定リファレンス](../../content_06_developer-guide/05-configuration/tenant.md) — 設定フィールドの詳細
