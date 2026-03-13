# External Password Auth Use Case Template

パスワード認証を外部サービス（HTTP API）に委譲するテンプレートセット。

idp-server 内蔵のパスワード検証を使わず、`authentication-configurations` の `execution.function = "http_request"` を使って外部 API にユーザー名/パスワードを転送し、レスポンスからユーザー情報をマッピングします。

**ユースケース**: 既存の認証基盤（LDAP ラッパー、社内認証 API 等）を持つ組織が、idp-server を OIDC レイヤーとして導入するケース。

> **templates vs examples**: このテンプレート（`config/templates/`）はゼロからの完全セットアップ用です。Organization・テナント・クライアントを一括作成します。

## 設定内容

| 項目 | 設定値 |
|------|--------|
| 認証方式 | パスワード（外部サービス委譲） |
| パスワードポリシー | なし（外部サービスが管理） |
| ユーザー登録 | なし（外部サービスが管理） |
| アカウントロック | 5回失敗でロック |
| セッション有効期限 | 24時間 |
| ユーザー識別キー | `EMAIL_OR_EXTERNAL_USER_ID` |

## ファイル構成

| ファイル | 用途 | API |
|---------|------|-----|
| `onboarding-template.json` | Organization + Organizer Tenant + Admin User + Client | `POST /v1/management/onboarding` |
| `public-tenant-template.json` | Public Tenant（パスワードポリシーなし） | `POST /v1/management/tenants` |
| `authentication-config-password-template.json` | 外部サービスパスワード認証（http_request） | `POST /v1/management/tenants/{id}/authentication-configurations` |
| `authentication-policy.json` | パスワードのみ認証ポリシー（登録なし） | `POST /v1/management/tenants/{id}/authentication-policies` |
| `public-client-template.json` | アプリケーションクライアント | `POST /v1/management/tenants/{id}/clients` |
| `mock-server.js` | ローカル検証用モックサーバー | `node mock-server.js` |
| `setup.sh` | 上記を順番に実行するスクリプト | - |
| `verify.sh` | 自動検証スクリプト | - |
| `delete.sh` | リソース削除スクリプト | - |
| `update.sh` | リソース更新スクリプト | - |

## 前提条件

- idp-server が起動済み
- システム管理者テナントが存在（初期セットアップ済み）
- `.env` に管理者認証情報を設定済み
- 外部認証サービスが起動済み（またはモックサーバー）

## 外部認証 API の契約

idp-server が外部サービスに送信するリクエストと、期待するレスポンスの仕様です。

### リクエスト

```
POST /auth/password
Content-Type: application/json

{
  "username": "user@example.com",
  "password": "UserPassword123"
}
```

### 成功レスポンス (HTTP 200)

```json
{
  "user_id": "ext-user-123",
  "email": "user@example.com",
  "name": "Test User"
}
```

レスポンスのフィールドは `user_mapping_rules` によって idp-server のユーザー属性にマッピングされます：

| 外部レスポンス | idp-server 属性 | 説明 |
|---------------|----------------|------|
| `user_id` | `external_user_id` | 外部サービスのユーザーID |
| `email` | `email` | メールアドレス |
| `name` | `name` | 表示名 |
| (static) | `provider_id` | 外部サービス識別子 |

### 失敗レスポンス (HTTP 401)

```json
{
  "error": "invalid_credentials",
  "error_description": "Invalid username or password"
}
```

## セットアップ手順

### モックサーバーの起動（ローカルテスト用）

```bash
node mock-server.js
# → Mock auth server running on http://localhost:4001
```

`mock-server.js` は Node.js 標準ライブラリのみで動作し、依存パッケージのインストールは不要です。

### 自動セットアップ

```bash
# .env を設定（プロジェクトルートに配置）
# AUTHORIZATION_SERVER_URL=http://localhost:8080
# ADMIN_TENANT_ID=<system-admin-tenant-id>
# ADMIN_USER_EMAIL=<admin-email>
# ADMIN_USER_PASSWORD=<admin-password>
# ADMIN_CLIENT_ID=<admin-client-id>
# ADMIN_CLIENT_SECRET=<admin-client-secret>

# セットアップ実行
./setup.sh

# ドライラン（実際には作成しない）
./setup.sh --dry-run
```

### 環境変数でカスタマイズ

```bash
# 組織名を指定
ORGANIZATION_NAME="my-company" ./setup.sh

# 外部認証サービスURLを指定
EXTERNAL_AUTH_URL="https://auth.internal.example.com/api/authenticate" ./setup.sh

# 外部プロバイダーIDを指定
EXTERNAL_PROVIDER_ID="ldap-wrapper" ./setup.sh

# トークン有効期限をカスタマイズ
ACCESS_TOKEN_DURATION=1800 \
REFRESH_TOKEN_DURATION=604800 \
./setup.sh
```

### カスタマイズ可能な環境変数

#### 外部認証サービス

| 環境変数 | デフォルト | 説明 |
|---------|-----------|------|
| `EXTERNAL_AUTH_URL` | `http://host.docker.internal:4001/auth/password` | 外部認証サービスのURL |
| `EXTERNAL_PROVIDER_ID` | `external-auth` | 外部プロバイダー識別子 |

#### セッション設定

| 環境変数 | デフォルト | 説明 |
|---------|-----------|------|
| `SESSION_TIMEOUT_SECONDS` | `86400` | セッション有効期限（秒） |

#### トークン有効期限

| 環境変数 | デフォルト | 説明 |
|---------|-----------|------|
| `ACCESS_TOKEN_DURATION` | `3600` | アクセストークン有効期限（秒） |
| `ID_TOKEN_DURATION` | `3600` | IDトークン有効期限（秒） |
| `REFRESH_TOKEN_DURATION` | `86400` | リフレッシュトークン有効期限（秒） |

## セットアップフロー

```
Step 1: システム管理者トークン取得
  └─ POST /{admin-tenant}/v1/tokens (password grant)

Step 2: オンボーディング（システムレベルAPI）
  └─ POST /v1/management/onboarding
     → Organization + Organizer Tenant + Admin User + Client 作成

Step 3: ORGANIZER管理者トークン取得
  └─ POST /{organizer-tenant}/v1/tokens (password grant)
     → 以降は組織レベルAPIを使用

Step 4: パブリックテナント作成（組織レベルAPI）
  └─ POST /v1/management/organizations/{org-id}/tenants
     → パスワードポリシーなし（外部サービスが管理）

Step 5: 外部パスワード認証設定作成（組織レベルAPI）
  └─ POST /v1/management/organizations/{org-id}/tenants/{id}/authentication-configurations
     → http_request による外部サービス委譲 + ユーザーマッピング

Step 6: 認証ポリシー作成（組織レベルAPI）
  └─ POST /v1/management/organizations/{org-id}/tenants/{id}/authentication-policies
     → パスワードのみで認証成功とするポリシー（self-registration なし）

Step 7: クライアント作成（組織レベルAPI）
  └─ POST /v1/management/organizations/{org-id}/tenants/{id}/clients
     → アプリケーション用OAuthクライアント
```

## 次のステップ

セットアップ完了後、以下のガイドで動作確認・設定カスタマイズができます。

| ガイド | 内容 |
|--------|------|
| [VERIFY.md](./VERIFY.md) | 基本動作確認（外部認証フロー → ログイン → トークン取得） |
| [EXPERIMENTS.md](./EXPERIMENTS.md) | 設定変更の実験（認証ポリシー、ブルートフォース防止、ユーザー識別方式等） |
| [EXPERIMENTS-http-requests.md](./EXPERIMENTS-http-requests.md) | HTTP Request の高度な設定（複数APIチェーン、マッピング関数等） |

## login-password-only との差分

| 項目 | login-password-only | external-password-auth |
|------|--------------------|-----------------------|
| パスワード検証 | idp-server 内蔵 | 外部サービス（http_request） |
| パスワードポリシー | テナントに設定 | なし（外部サービスが管理） |
| ユーザー登録 | initial-registration | なし（外部サービスが管理） |
| ユーザー識別 | EMAIL | EMAIL_OR_EXTERNAL_USER_ID |
| 認証ポリシー | password + initial-registration | password のみ |
| ブルートフォース防止 | なし | failure_conditions + lock_conditions |
