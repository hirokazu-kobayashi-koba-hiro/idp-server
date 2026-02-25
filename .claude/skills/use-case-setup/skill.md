---
name: use-case-setup
description: ユースケース別セットアップのエントリポイント。ユーザーにユースケースを選択してもらい、対応するスキル（use-case-login, use-case-mfa等）にルーティングする。共通ワークフロー、前提条件、組み合わせパターンの概要を提供。
---

# ユースケース別 設定ワークフロー

## ワークフロー概要

このスキルは以下のサイクルで、テンプレートベースのセットアップを実行する。

```
1. ユースケース選択（AskUserQuestion）
2. 対応するスキルをロード（下記ルーティング表を参照）
3. 決定事項のヒアリング（AskUserQuestion）
4. ヒアリング結果を環境変数にマッピング
5. テンプレートの setup.sh を実行
6. 次のユースケースへ（または完了）
```

## ユースケース → スキル ルーティング

| # | ユースケース | スキル名 | テンプレート | Example |
|---|------------|---------|-------------|---------|
| 1 | ログイン（パスワードのみ） | `use-case-login` | `config/templates/use-cases/login-password-only/` | `config/examples/login-password-only/` |
| 2 | ログイン（ソーシャル連携） | `use-case-login` | `config/templates/use-cases/login-social/` | `config/examples/login-social/` |
| 3 | MFA（多要素認証） | `use-case-mfa` | `config/templates/use-cases/mfa-email/` | `config/examples/mfa-email/` |
| 4 | パスワードレス認証 | `use-case-passwordless` | `config/templates/use-cases/passwordless-fido2/` | `config/examples/passwordless-fido2/` |
| 5 | 身元確認/eKYC | `use-case-ekyc` | `config/templates/use-cases/ekyc/` | `config/examples/ekyc/` |
| 6 | サードパーティ連携 | `use-case-third-party` | `config/templates/use-cases/third-party/` | `config/examples/third-party/` |

**重要**: ユースケース選択後、対応するスキルをロードしてから作業を進めること。

## 共通前提条件

- idp-serverが起動済み
- システム管理者テナントが存在（初期セットアップ済み）
- `.env` にシステム管理者の認証情報が設定済み:
  - `AUTHORIZATION_SERVER_URL`, `ADMIN_TENANT_ID`, `ADMIN_USER_EMAIL`, `ADMIN_USER_PASSWORD`, `ADMIN_CLIENT_ID`, `ADMIN_CLIENT_SECRET`

## 認可サーバー共通設定（全ユースケース必須）

### claims_supported（重要）

全テナントの `authorization_server` に `claims_supported` を必ず設定すること。
**この設定が無いと、UserInfo / ID Token が `sub` のみしか返さない。**

リファレンス: `config/templates/tenant-template.json` の `claims_supported` フィールド

```json
"claims_supported": [
  "sub", "iss", "auth_time", "acr",
  "name", "given_name", "family_name", "nickname", "preferred_username", "middle_name",
  "profile", "picture", "website",
  "email", "email_verified",
  "gender", "birthdate", "zoneinfo", "locale", "updated_at",
  "address", "phone_number", "phone_number_verified"
]
```

## setup.sh 共通ステップ

各ユースケースの `setup.sh` は以下の共通フローで実行される。

```
Step 1: アクセストークン取得（password grant）
Step 2: オンボーディング（POST /v1/management/onboarding）
  → Organization + Organizer Tenant + Admin User + Client
Step 3: ORGANIZERテナントの管理者トークン取得
Step 4+: ユースケース固有のリソース作成（組織レベルAPI）
```

## 全ユースケース共通 設定確認チェックリスト

setup.sh 実行後、ブラウザで動作確認する前に以下を確認する。

| # | 確認観点 | 設定箇所 | よくあるミス |
|---|---------|---------|------------|
| 1 | `claims_supported` が設定済み | 認可サーバー | 未設定で UserInfo/ID Token が `sub` のみ返却 |
| 2 | `ui_config.base_url` が認証UIのオリジン | テナント `ui_config` | APIサーバーURL を設定してしまう |
| 3 | `cors_config` に `allow_headers`, `allow_methods`, `allow_credentials` | テナント `cors_config` | `allow_origins` だけ設定 |
| 4 | `signin_page` がユースケースに合ったパス | テナント `ui_config` | FIDO2: `/signin/fido2/`, 標準: `/signin/` |
| 5 | `failure_conditions` / `lock_conditions` が設定済み | 認証ポリシー | 未設定だと認証失敗時にアカウントロックされない |
| 6 | setup.sh の変数定義順序 | setup.sh | 参照される変数が先に定義されていること |

### 動作確認時のprompt値

| テスト | prompt値 | 目的 |
|--------|---------|------|
| ユーザー登録 | `prompt=create` | Sign Up画面を直接表示 |
| 再認証 | `prompt=login` | 既存セッションを無視して再認証を強制 |

## 共通環境変数

| ヒアリング項目 | 環境変数 | デフォルト値 |
|--------------|---------|-------------|
| 組織名 | `ORGANIZATION_NAME` | `my-organization` |
| 管理者メール | `NEW_ADMIN_EMAIL` | `admin@example.com` |
| 管理者パスワード | `NEW_ADMIN_PASSWORD` | `ChangeMe123` |
| Cookie名 | `COOKIE_NAME` | `SESSION` |
| UI URL | `UI_BASE_URL` | `${AUTHORIZATION_SERVER_URL}` |
| クライアント名 | `CLIENT_NAME` | `Web Application` |
| リダイレクトURI | `REDIRECT_URI` | `http://localhost:3000/callback/` |
| クライアントエイリアス | `CLIENT_ALIAS` | `web-app` |

## Management API

テンプレートのsetup.shは以下の流れでAPIを使用する。

```
# Step 1-2: システム管理者トークンでオンボーディング（システムレベル）
POST /v1/management/onboarding

# Step 3: ORGANIZER テナントの管理者トークンを取得
POST /{organizer-tenant-id}/v1/tokens

# Step 4+: 組織レベルAPIでリソース作成
POST /v1/management/organizations/{org-id}/tenants                                          # テナント作成
POST /v1/management/organizations/{org-id}/tenants/{tenant-id}/authentication-configurations # 認証メソッド設定
POST /v1/management/organizations/{org-id}/tenants/{tenant-id}/authentication-policies       # 認証ポリシー
POST /v1/management/organizations/{org-id}/tenants/{tenant-id}/clients                       # クライアント作成
POST /v1/management/organizations/{org-id}/tenants/{tenant-id}/federation-configurations     # フェデレーション設定
POST /v1/management/organizations/{org-id}/tenants/{tenant-id}/identity-verification-configurations # 身元確認設定
PUT  /v1/management/organizations/{org-id}/tenants/{tenant-id}/authorization-server          # 認可サーバー更新
```

- オンボーディングのみシステム管理者トークンを使用
- それ以降はORGANIZERテナントの管理者トークンで組織レベルAPIを使用

---

## 組み合わせ設定パターン

### ECサイト

| 設定 | 内容 |
|------|------|
| 認証方式 | パスワード + Socialログイン（Google） |
| パスワードポリシー | `min_length: 8`, `require_number: true` |
| セッション | `timeout_seconds: 3600` |
| Passkey | 推奨（`authenticator_attachment: platform`, `resident_key: required`） |
| トークン | AT: 30分, RT: ローテーション+固定, 7日 |

### 金融サービス

| 設定 | 内容 |
|------|------|
| 認証方式 | パスワード + MFA（SMS必須） |
| パスワードポリシー | `min_length: 12`, `require_uppercase: true`, `require_special_char: true`, `max_history: 5` |
| セッション | `timeout_seconds: 1800`, `switch_policy: STRICT` |
| eKYC | 必須、`required_identity_verification_scopes: ["transfers", "account"]` |
| FIDO UAF + CIBA | 高額送金時、`level_of_authentication_scopes: { "transfers": ["fido-uaf"] }` |
| トークン | AT: 15分, RT: ローテーション+固定, 1時間 |

### 社内業務システム

| 設定 | 内容 |
|------|------|
| 認証方式 | Azure AD連携（エンタープライズSSO） |
| MFA | 管理者のみ必須（`conditions: { "scopes": ["admin"] }`） |
| セッション | `timeout_seconds: 28800`（8時間）, `switch_policy: SWITCH_ALLOWED` |
| トークン | AT: 1時間, RT: ローテーション+延長, 30日 |

### 決済サービス

| 設定 | 内容 |
|------|------|
| 認証方式 | パスワードレス（Passkey）推奨 + パスワードフォールバック |
| eKYC | 高額決済時必須 |
| CIBA | モバイル承認（高額決済時） |
| デバイスルール | `max_devices: 3`, `required_identity_verification: true` |
| トークン | AT: 15分, RT: ローテーション+固定, 1日 |

---

## Management API一覧

### システムレベルAPI

| 設定種類 | APIパス | メソッド |
|---------|---------|---------|
| オンボーディング | `/v1/management/onboarding` | POST |
| テナント作成 | `/v1/management/tenants` | POST |
| テナント更新 | `/v1/management/tenants/{tenant-id}` | PUT |
| 認可サーバー更新 | `/v1/management/tenants/{tenant-id}/authorization-server` | PUT |
| クライアント作成 | `/v1/management/tenants/{tenant-id}/clients` | POST |
| クライアント更新 | `/v1/management/tenants/{tenant-id}/clients/{client-id}` | PUT |
| 認証メソッド設定作成 | `/v1/management/tenants/{tenant-id}/authentication-configurations` | POST |
| 認証ポリシー作成 | `/v1/management/tenants/{tenant-id}/authentication-policies` | POST |
| フェデレーション設定作成 | `/v1/management/tenants/{tenant-id}/federation-configurations` | POST |
| 身元確認テンプレート作成 | `/v1/management/tenants/{tenant-id}/identity-verification-configurations` | POST |

### 組織レベルAPI（ORGANIZER テナント経由で使用する場合）

| 設定種類 | APIパス | メソッド |
|---------|---------|---------|
| テナント更新 | `/v1/management/organizations/{org-id}/tenants/{tenant-id}` | PUT |
| 認可サーバー更新 | `/v1/management/organizations/{org-id}/tenants/{tenant-id}/authorization-server` | PUT |
| クライアント作成 | `/v1/management/organizations/{org-id}/tenants/{tenant-id}/clients` | POST |
| クライアント更新 | `/v1/management/organizations/{org-id}/tenants/{tenant-id}/clients/{client-id}` | PUT |
| 認証メソッド設定作成 | `/v1/management/organizations/{org-id}/tenants/{tenant-id}/authentication-configurations` | POST |
| 認証メソッド設定更新 | `/v1/management/organizations/{org-id}/tenants/{tenant-id}/authentication-configurations/{config-id}` | PUT |
| 認証ポリシー作成 | `/v1/management/organizations/{org-id}/tenants/{tenant-id}/authentication-policies` | POST |
| 認証ポリシー更新 | `/v1/management/organizations/{org-id}/tenants/{tenant-id}/authentication-policies/{policy-id}` | PUT |
| フェデレーション設定作成 | `/v1/management/organizations/{org-id}/tenants/{tenant-id}/federation-configurations` | POST |
| 身元確認テンプレート作成 | `/v1/management/organizations/{org-id}/tenants/{tenant-id}/identity-verification-configurations` | POST |
| 身元確認テンプレート更新 | `/v1/management/organizations/{org-id}/tenants/{tenant-id}/identity-verification-configurations/{config-id}` | PUT |

**注意**: 組織レベルAPIを使うには、オンボーディングで作成された ORGANIZER テナントが必要。

$ARGUMENTS
