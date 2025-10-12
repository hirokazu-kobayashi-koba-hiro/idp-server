# 設定管理 - Overview

## このドキュメントの目的

idp-serverの設定ファイル構造と、実際のプロジェクトでの設定管理方法を理解します。

### 所要時間
⏱️ **約15分**

### 前提知識
- OAuth 2.0/OIDC基礎知識（[RFC 6749](https://datatracker.ietf.org/doc/html/rfc6749)）
- JSON形式の読み書き
- REST API基礎知識

---

## クイックスタート（5分で動作確認）

### 最小構成での設定

最もシンプルな設定で動作確認（組織管理者として実行）：

```bash
# 前提: organization-id（組織ID）を取得済み
ORGANIZATION_ID="your-organization-id"

# 1. テナント作成
curl -X POST "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants" \
  -H "Content-Type: application/json" \
  -d '{
    "tenant": {
      "id": "test-tenant",
      "name": "Test Tenant",
      "domain": "http://localhost:8080",
      "authorization_provider": "idp-server",
      "database_type": "postgresql"
    },
    "authorization_server": {
      "issuer": "http://localhost:8080/test-tenant",
      "scopes_supported": ["openid", "profile"],
      "grant_types_supported": ["authorization_code"]
    }
  }'

# 2. クライアント登録
curl -X POST "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/test-tenant/clients" \
  -H "Content-Type: application/json" \
  -d '{
    "client_id": "test-client",
    "client_secret": "secret",
    "redirect_uris": ["http://localhost:3000/callback"],
    "response_types": ["code"],
    "grant_types": ["authorization_code"],
    "scope": "openid profile",
    "application_type": "web"
  }'

# 3. 動作確認
open "http://localhost:8080/test-tenant/v1/authorizations?client_id=test-client&redirect_uri=http://localhost:3000/callback&response_type=code&scope=openid"
```

---

## 設定管理の全体フロー

```
┌─────────────────────────────────────────┐
│ 1. 設定ファイル作成                     │
│    ├─ tenant.json                       │
│    ├─ clients/web-app.json              │
│    └─ authentication-policy/oauth.json  │
└────────────┬────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────┐
│ 2. Management API経由で登録             │
│    POST /v1/management/organizations/   │
│         {org-id}/tenants                │
│    POST .../tenants/{tenant-id}/clients │
└────────────┬────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────┐
│ 3. データベースに保存                   │
│    PostgreSQL/MySQLに永続化             │
└────────────┬────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────┐
│ 4. アプリケーション起動                 │
│    設定がDBから読み込まれる             │
└────────────┬────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────┐
│ 5. OAuth/OIDCフロー実行                 │
│    Authorization Code Flow等            │
└─────────────────────────────────────────┘
```

---

## 設定ファイルの種類

推奨ディレクトリ構成：

```
config/
├── tenant.json                  # テナント・Authorization Server設定
├── clients/                     # クライアント設定
│   ├── web-app.json
│   └── mobile-app.json
├── authentication/              # 認証方式設定
│   ├── fido-uaf/
│   ├── password/
│   └── authentication-device/
├── authentication-policy/       # 認証ポリシー
│   ├── oauth.json
│   └── ciba.json
├── federation/oidc/             # 外部IdP連携
├── identity-verification/       # 身元確認
└── security-event-hook/         # イベント通知
```

---

## 主要な設定ファイル

### 1. Tenant設定（tenant.json）

テナントとAuthorization Serverの基本設定。

**主要項目**:
- テナント情報（id, name, domain）
- サポートするscopes, grant_types, response_types
- トークン有効期限（extension）

### 2. Client設定（clients/*.json）

OAuth 2.0/OIDCクライアントの設定。

**主要項目**:
- client_id, client_secret
- redirect_uris, scope
- CIBA設定（extension）

### 3. Authentication設定（authentication/）

認証方式の設定（FIDO-UAF/PIN/プッシュ通知等）。

**特徴**:
- HttpRequestExecutorで外部API連携
- Mapping Rulesでリクエスト/レスポンス変換

**詳細ガイド**:
- [FIDO-UAF](./authn/fido-uaf.md)
- [認証デバイス](./authn/authentication-device.md)
- [Password](./authn/password.md)
- [Email](./authn/email.md)
- [SMS](./authn/sms.md)

### 4. Authentication Policy設定（authentication-policy/）

フロー別の認証要件定義。

**主要項目**:
- available_methods（利用可能な認証方式）
- acr_mapping_rules（認証レベルマッピング）
- success_conditions（成功条件）

### 5. Federation設定（federation/oidc/）

外部IdP連携設定。

### 6. Identity Verification設定（identity-verification/）

eKYC/本人確認プロセスの設定。

### 7. Security Event Hook設定（security-event-hook/）

セキュリティイベントの外部通知設定。

---

## 環境変数の使用

設定ファイルでは`${VAR_NAME}`形式で環境変数を参照：

```json
{
  "url": "${STRONG_AUTH_URL}/v1/uaf/fido/keys",
  "client_secret": "${CLIENT_SECRET}"
}
```

**主要な環境変数**:
- `${TENANT_ID}` - テナントID
- `${AUTHORIZATION_SERVER_URL}` - Authorization Server URL
- `${STRONG_AUTH_URL}` - 外部認証API URL
- `${CLIENT_SECRET}` - クライアントシークレット

### 環境変数の設定方法

#### docker-compose.yml

```yaml
services:
  idp-server:
    image: idp-server:latest
    environment:
      - TENANT_ID=my-tenant
      - AUTHORIZATION_SERVER_URL=http://localhost:8080
      - CLIENT_SECRET=secret-value
      - STRONG_AUTH_URL=https://auth-api.example.com
```

#### .env ファイル

```bash
TENANT_ID=my-tenant
AUTHORIZATION_SERVER_URL=http://localhost:8080
CLIENT_SECRET=secret-value
STRONG_AUTH_URL=https://auth-api.example.com
```

#### 環境別の管理

```bash
# 開発環境
export TENANT_ID=dev-tenant
export AUTHORIZATION_SERVER_URL=https://dev-idp.example.com

# 本番環境
export TENANT_ID=prod-tenant
export AUTHORIZATION_SERVER_URL=https://idp.example.com
```

---

## 登録順序（重要）

```
1. Tenant作成
2. 認証方式設定（Authentication Configurations）
3. 認証ポリシー設定（Authentication Policies）
   ※ 認証方式を参照するため、先に登録必須
4. クライアント設定（Clients）
5. その他の設定（Federation/Identity Verification/Hooks）
```

---

## Management APIで登録

すべての設定は**組織レベルManagement API**経由で登録：

```bash
# テナント作成
POST /v1/management/organizations/{organization-id}/tenants

# 認証設定登録
POST /v1/management/organizations/{organization-id}/tenants/{tenant-id}/authentication-configurations

# 認証ポリシー登録
POST /v1/management/organizations/{organization-id}/tenants/{tenant-id}/authentication-policies

# クライアント登録
POST /v1/management/organizations/{organization-id}/tenants/{tenant-id}/clients
```

**注意**: 全てのManagement APIは組織レベル（organization-idが必要）

---

## 詳細ドキュメント構成

### 基本設定

| ドキュメント | 内容 | ステータス |
|------------|------|-----------|
| [Tenant設定](./tenant.md) | テナント・Authorization Server設定 | ✅ 完成 |
| [Client設定](./client.md) | OAuth 2.0/OIDCクライアント設定 | ✅ 完成 |

### 認証設定（authn/）

| ドキュメント | 内容 | ステータス |
|------------|------|-----------|
| [Password認証](./authn/password.md) | パスワード認証設定 | ✅ 完成 |
| [Email認証](./authn/email.md) | メールOTP設定 | ✅ 完成 |
| [SMS認証](./authn/sms.md) | SMS OTP設定 | ✅ 完成 |
| [FIDO-UAF認証](./authn/fido-uaf.md) | 生体認証設定（外部API連携） | ✅ 完成 |
| [WebAuthn認証](./authn/webauthn.md) | FIDO2/WebAuthn設定 | ✅ 完成 |
| [認証デバイス](./authn/authentication-device.md) | プッシュ通知認証設定 | ✅ 完成 |
| [初回登録](./authn/initial-registration.md) | ユーザー初回登録フロー | ✅ 完成 |
| [レガシー認証](./authn/legacy.md) | 既存システム連携 | ✅ 完成 |

### ポリシー・連携設定

| ドキュメント | 内容 | ステータス |
|------------|------|-----------|
| [Authentication Policy](./authentication-policy.md) | 認証ポリシー設定（ACRマッピング・成功条件） | ✅ 完成 |
| [Federation](./federation.md) | 外部IdP連携設定（複数API連携） | ✅ 完成 |

### 拡張機能設定

| ドキュメント | 内容 | ステータス |
|------------|------|-----------|
| [Identity Verification](./identity-verification.md) | eKYC/本人確認（7フェーズ処理） | ✅ 完成 |
| [Security Event Hook](./security-event-hook.md) | セキュリティイベント通知（SSF対応） | ✅ 完成 |

---

## 次のステップ

### まず読むべき設定ガイド

1. [Tenant設定](./tenant.md) - テナント作成に必須
2. [Client設定](./client.md) - OAuth/OIDCクライアント登録
3. [Authentication Policy](./authentication-policy.md) - 認証要件の定義

### 関連ドキュメント

- [HttpRequestExecutor実装ガイド](../04-implementation-guides/impl-16-http-request-executor.md)
- [Authentication Policy実装ガイド](../04-implementation-guides/impl-05-authentication-policy.md)

---

**最終更新**: 2025-10-13

