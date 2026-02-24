# Examples - ユースケース別サンプル設定

idp-server のユースケース別サンプル設定集です。各ディレクトリに設定ファイル（JSON）とライフサイクルスクリプト（setup/verify/update/delete）が含まれており、固定値でそのまま実行できます。

> **examples vs templates**: `examples/` は固定値のサンプル構成です。環境変数でカスタマイズしたい場合は `config/templates/use-cases/` を使用してください。

## ユースケース一覧

| ディレクトリ | 概要 | 認証方式 |
|---|---|---|
| [login-password-only](./login-password-only/) | パスワードのみのログイン | パスワード |
| [login-social](./login-social/) | ソーシャルログイン（Google連携） | パスワード + Google Federation |
| [mfa-email](./mfa-email/) | 多要素認証（パスワード + Email OTP） | パスワード AND Email OTP |
| [passwordless-fido2](./passwordless-fido2/) | パスワードレス認証（FIDO2/WebAuthn） | FIDO2 OR パスワード |
| [ekyc](./ekyc/) | 身元確認（Identity Verification） | パスワード + verified_claims |
| [third-party](./third-party/) | サードパーティ連携（Web/Mobile/M2M） | パスワード + Client Credentials |

## クイックスタート

### 前提条件

- idp-server が起動していること（`docker compose up -d`）
- プロジェクトルートに `.env` ファイルが存在すること
- `jq`, `curl`, `python3` がインストールされていること

### 基本操作

```bash
cd config/examples

# 1. セットアップ（リソース作成）
bash login-password-only/setup.sh

# 2. 動作確認（E2E検証）
bash login-password-only/verify.sh

# 3. 設定更新（JSONファイル編集後）
bash login-password-only/update.sh

# 4. クリーンアップ（全リソース削除）
bash login-password-only/delete.sh
```

### 一括テスト

全ユースケースの `setup → verify → delete` を一括実行できます。

```bash
# 全ユースケース実行
bash run-all-tests.sh

# 特定ユースケースのみ
bash run-all-tests.sh login-password-only mfa-email

# 利用可能なユースケース
#   login-password-only, login-social, mfa-email,
#   passwordless-fido2, ekyc, third-party
```

## ファイル構成

各ユースケースは以下の共通構成を持ちます。

```
{use-case}/
├── README.md                                      # ユースケース固有の説明
├── onboarding-request.json                        # Organization + Organizer Tenant + Admin User + Client
├── public-tenant-request.json                     # Public Tenant + 認可サーバー設定
├── authentication-config-*.json                   # 認証設定（登録スキーマ、MFA等）
├── authentication-policy-*.json                   # 認証ポリシー
├── client-request.json                            # アプリケーションクライアント
├── setup.sh                                       # セットアップ（リソース作成）
├── verify.sh                                      # 動作確認（E2E検証）
├── update.sh                                      # 設定更新
└── delete.sh                                      # クリーンアップ
```

### スクリプト

| スクリプト | 役割 | 冪等性 |
|---|---|---|
| `setup.sh` | 全リソースを作成（Organization → Tenant → 認証設定 → Client） | 再実行時エラー（先にdelete必要） |
| `verify.sh` | セットアップ済み環境に対してOIDCフローを実行し、正常動作を確認 | 何度でも実行可能 |
| `update.sh` | JSONファイルの変更内容をサーバーに反映（PUT） | 何度でも実行可能 |
| `delete.sh` | 作成した全リソースを削除 | 何度でも実行可能 |

### 設定ファイル（JSON）

| ファイル | 管理API | 説明 |
|---|---|---|
| `onboarding-request.json` | `POST /v1/management/onboarding` | 組織・管理テナント・管理者ユーザー・管理クライアントを一括作成 |
| `public-tenant-request.json` | `POST /v1/management/organizations/{id}/tenants` | パブリックテナント（パスワードポリシー、セッション設定、認可サーバー設定） |
| `authentication-config-*.json` | `POST .../authentication-configurations` | 認証設定（初期登録スキーマ、Email OTP、FIDO2等） |
| `authentication-policy-*.json` | `POST .../authentication-policies` | 認証ポリシー（どの認証方式をどの条件で要求するか） |
| `client-*.json` | `POST .../clients` | OAuthクライアント設定 |

## セットアップフロー

全ユースケースで共通の基本フローです。

```
Step 1: システム管理者トークン取得
  └─ POST /{admin-tenant}/v1/tokens (password grant)

Step 2: オンボーディング（システムレベルAPI）
  └─ POST /v1/management/onboarding
     → Organization + Organizer Tenant + Admin User + Client 作成

Step 3: Organizer管理者トークン取得
  └─ POST /{organizer-tenant}/v1/tokens (password grant)
     → 以降は組織レベルAPIを使用

Step 4: パブリックテナント作成（組織レベルAPI）
  └─ POST /v1/management/organizations/{org-id}/tenants

Step 5+: 認証設定・ポリシー・クライアント作成
  └─ ユースケースごとに異なる（各README参照）
```

## verify.sh の検証内容

`verify.sh` は curl でOIDCフローを実行し、各ステップの成否を判定します。

### 共通検証ステップ（全ユースケース）

1. **Discovery** - `/.well-known/openid-configuration` の疎通と issuer 一致確認
2. **Authorization Request** - 認可リクエスト（HTTP 302 → authorization_id 取得）
3. **User Registration** - `initial-registration` によるテストユーザー作成
4. **Consent Grant** - 同意付与（authorization_code 取得）
5. **Token Exchange** - 認可コード → access_token, id_token, refresh_token
6. **UserInfo** - access_token でユーザー情報取得
7. **Refresh Token** - refresh_token で新しい access_token 取得

### ユースケース固有の検証

| ユースケース | 追加検証 |
|---|---|
| login-social | Federation設定ファイル存在確認 |
| mfa-email | Phase 2: Email OTPチャレンジ → Management API経由でverification code取得 → OTP検証 → パスワード認証 |
| passwordless-fido2 | パスワードフォールバック検証（FIDO2はブラウザ必要） |
| ekyc | Discovery の `verified_claims_supported: true` 確認 |
| third-party | M2M client_credentials grant → Token Introspection → Web Client認可コードフロー |

## その他のサンプル

ユースケース別セットアップ以外のサンプルも含まれています。

| ディレクトリ | 概要 |
|---|---|
| `e2e/` | E2Eテスト用テナント設定 |
| `standard-oidc-web-app/` | 標準的なOIDC Webアプリ構成 |
| `subdomain-oidc-web-app/` | サブドメインベースのOIDC構成 |
| `financial-grade/` | FAPI 1.0 Advanced / CIBA 準拠構成 |
| `oidcc-cross-site/` | OIDF適合性テスト（Cross-Site） |
| `oidcc-cross-site-context-path/` | OIDF適合性テスト（Context Path） |
| `oidcc-formpost-basic/` | OIDF適合性テスト（Form Post Basic） |

## トラブルシューティング

### setup.sh が「Tenant already exists」で失敗する

前回のリソースが残っています。先に削除してください。

```bash
bash {use-case}/delete.sh && bash {use-case}/setup.sh
```

### verify.sh が Step 2 で失敗する（認可リクエスト）

- `setup.sh` が正常完了しているか確認
- idp-server が起動しているか確認（`docker compose ps`）
- `.env` の `AUTHORIZATION_SERVER_URL` が正しいか確認

### verify.sh が Token Exchange で失敗する

- `client_secret` が `client-request.json` の値と一致しているか確認
- `redirect_uri` がクライアント設定と一致しているか確認

### run-all-tests.sh で一部のユースケースだけ失敗する

前回のリソースが残っている可能性があります。該当ユースケースの `delete.sh` を実行してからリトライしてください。
