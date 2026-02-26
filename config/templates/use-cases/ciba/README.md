# CIBA (Client-Initiated Backchannel Authentication) Use Case Template

FIDO-UAF デバイス登録 + CIBA フローを設定するテンプレートセット。
CIBA はブラウザリダイレクトではなく、別デバイス（モバイル端末など）での認証承認を行うフローです。
デバイス登録は FIDO-UAF 登録フローで自動的に行われ、device_secret が発行されます。

> Claude Code を使用している場合は `/use-case-setup` でヒアリング付きの対話型セットアップが利用できます。

> **templates vs examples**: このテンプレート（`config/templates/`）はゼロからの完全セットアップ用です。Organization・テナント・クライアントを一括作成します。既存テナントへの追加設定には `config/examples/` を使用してください。

## 設定内容

| 項目 | 設定値 |
|------|--------|
| 認証方式（OAuth） | パスワード + FIDO-UAF + 初期登録 |
| 認証方式（CIBA） | FIDO-UAF認証（デバイス側） |
| デバイス登録 | FIDO-UAF登録フローで自動登録 |
| デバイスシークレット | HS256, 有効期限1年 |
| CIBA配信モード | poll |
| CIBA認証リクエスト有効期限 | 120秒 |
| CIBAポーリング間隔 | 5秒 |
| パスワードポリシー | 8文字以上、文字種制約なし |
| アカウントロック | 5回失敗で15分ロック |
| セッション有効期限 | 24時間 |
| ユーザー登録必須項目 | email, password, name |

## ファイル構成

| ファイル | 用途 | API |
|---------|------|-----|
| `onboarding-template.json` | Organization + Organizer Tenant + Admin User + Client | `POST /v1/management/onboarding` |
| `public-tenant-template.json` | Public Tenant（CIBA + FIDO-UAF + authentication_device_rule付き） | `POST /v1/management/tenants` |
| `authentication-config-initial-registration.json` | ユーザー登録スキーマ | `POST .../authentication-configurations` |
| `authentication-config-fido-uaf.json` | FIDO-UAF認証設定（登録 + 認証） | `POST .../authentication-configurations` |
| `authentication-policy-oauth.json` | OAuthフロー認証ポリシー（password + fido-uaf + 初期登録） | `POST .../authentication-policies` |
| `authentication-policy-ciba.json` | CIBAフロー認証ポリシー（fido-uaf認証） | `POST .../authentication-policies` |
| `public-client-template.json` | アプリケーションクライアント（CIBA + JWT Bearer grant + device federation） | `POST .../clients` |
| `jwks.json` | トークン署名用鍵 | - |
| `setup.sh` | インフラ構築スクリプト | - |
| `verify.sh` | FIDO-UAF登録 + CIBAフロー検証スクリプト | - |
| `update.sh` | リソース更新スクリプト | - |
| `delete.sh` | リソース削除スクリプト | - |
| `ciba-device-auth.sh` | CIBAデバイス側認証スクリプト（device_secret_jwt + FIDO-UAF） | - |

## セットアップ手順

### 前提条件

- idp-serverが起動済み
- Mockoon FIDO-UAFモックサーバーが起動済み（`docker compose up -d mockoon`）
- システム管理者テナントが存在（初期セットアップ済み）
- `.env` に管理者認証情報を設定済み

### 自動セットアップ

```bash
# .env を設定（プロジェクトルートに配置）
# AUTHORIZATION_SERVER_URL=http://localhost:8080
# ADMIN_TENANT_ID=<system-admin-tenant-id>
# ADMIN_USER_EMAIL=<admin-email>
# ADMIN_USER_PASSWORD=<admin-password>
# ADMIN_CLIENT_ID=<admin-client-id>
# ADMIN_CLIENT_SECRET=<admin-client-secret>

# JWKS を生成して配置（初回のみ）
# cp path/to/your/jwks.json ./jwks.json

# セットアップ実行
./setup.sh

# ドライラン（実際には作成しない）
./setup.sh --dry-run
```

### 環境変数でカスタマイズ

```bash
# FIDO-UAFサーバーURL（デフォルト: http://host.docker.internal:4000）
FIDO_UAF_SERVER_URL="http://host.docker.internal:4000" ./setup.sh

# デバイスシークレット設定
DEVICE_SECRET_ALGORITHM=HS256 \
DEVICE_SECRET_EXPIRES_IN=31536000 \
MAX_DEVICES=5 \
./setup.sh

# CIBA設定をカスタマイズ
CIBA_REQUEST_EXPIRES_IN=300 \
CIBA_POLLING_INTERVAL=10 \
./setup.sh
```

### カスタマイズ可能な環境変数

#### FIDO-UAF / デバイス設定

| 環境変数 | デフォルト | 説明 |
|---------|-----------|------|
| `FIDO_UAF_SERVER_URL` | `http://host.docker.internal:4000` | FIDO-UAFモックサーバーURL |
| `DEVICE_SECRET_ALGORITHM` | `HS256` | デバイスシークレットのアルゴリズム |
| `DEVICE_SECRET_EXPIRES_IN` | `31536000` | デバイスシークレットの有効期限（秒） |
| `MAX_DEVICES` | `5` | ユーザーあたりの最大デバイス数 |

#### CIBA設定

| 環境変数 | デフォルト | 説明 |
|---------|-----------|------|
| `CIBA_DELIVERY_MODE` | `poll` | CIBA配信モード |
| `CIBA_REQUEST_EXPIRES_IN` | `120` | CIBA認証リクエスト有効期限（秒） |
| `CIBA_POLLING_INTERVAL` | `5` | CIBAポーリング間隔（秒） |
| `CIBA_USER_CODE_REQUIRED` | `false` | ユーザーコード必須 |

#### セッション設定

| 環境変数 | デフォルト | 説明 |
|---------|-----------|------|
| `SESSION_TIMEOUT_SECONDS` | `86400` | セッション有効期限（秒） |

#### パスワードポリシー

| 環境変数 | デフォルト | 説明 |
|---------|-----------|------|
| `PASSWORD_MIN_LENGTH` | `8` | 最小文字数 |
| `PASSWORD_MAX_LENGTH` | `72` | 最大文字数 |
| `PASSWORD_REQUIRE_UPPERCASE` | `false` | 大文字必須 |
| `PASSWORD_REQUIRE_LOWERCASE` | `false` | 小文字必須 |
| `PASSWORD_REQUIRE_NUMBER` | `false` | 数字必須 |
| `PASSWORD_REQUIRE_SPECIAL_CHAR` | `false` | 特殊文字必須 |
| `PASSWORD_MAX_HISTORY` | `0` | パスワード履歴保存数 |
| `PASSWORD_MAX_ATTEMPTS` | `5` | ロックまでの失敗回数 |
| `PASSWORD_LOCKOUT_DURATION_SECONDS` | `900` | ロック期間（秒） |

#### トークン有効期限

| 環境変数 | デフォルト | 説明 |
|---------|-----------|------|
| `ACCESS_TOKEN_DURATION` | `3600` | アクセストークン有効期限（秒） |
| `ID_TOKEN_DURATION` | `3600` | IDトークン有効期限（秒） |
| `REFRESH_TOKEN_DURATION` | `86400` | リフレッシュトークン有効期限（秒） |

#### ユーザー登録

| 環境変数 | デフォルト | 説明 |
|---------|-----------|------|
| `REGISTRATION_REQUIRED_FIELDS` | `email,password,name` | 登録時の必須項目（カンマ区切り） |

## セットアップフロー

```
Step 1: システム管理者トークン取得
  +-- POST /{admin-tenant}/v1/tokens (password grant)

Step 2: オンボーディング（システムレベルAPI）
  +-- POST /v1/management/onboarding
     -> Organization + Organizer Tenant + Admin User + Client 作成

Step 3: ORGANIZER管理者トークン取得
  +-- POST /{organizer-tenant}/v1/tokens (password grant)
     -> 以降は組織レベルAPIを使用

Step 4: パブリックテナント作成（組織レベルAPI）
  +-- POST /v1/management/organizations/{org-id}/tenants
     -> CIBA設定 + FIDO-UAF + authentication_device_rule付きテナント作成

Step 5a: 認証設定作成 - initial-registration
  +-- POST .../tenants/{id}/authentication-configurations
     -> ユーザー登録スキーマ

Step 5b: 認証設定作成 - FIDO-UAF
  +-- POST .../tenants/{id}/authentication-configurations
     -> FIDO-UAF登録・認証設定

Step 6a: OAuth認証ポリシー作成
  +-- POST .../tenants/{id}/authentication-policies
     -> password + fido-uaf + initial-registration

Step 6b: CIBA認証ポリシー作成
  +-- POST .../tenants/{id}/authentication-policies
     -> fido-uaf認証

Step 7: クライアント作成
  +-- POST .../tenants/{id}/clients
     -> CIBA + JWT Bearer grant + device federation付きクライアント
```

## プロダクションフロー

```
1. ユーザーがWebでアカウント登録（initial-registration）
2. ユーザーがモバイルアプリをインストール
3. アプリがFIDO-UAF登録 -> デバイス自動登録 + device_secret発行
4. サービスがCIBAリクエスト -> ユーザーのデバイスに通知
5. アプリがFIDO-UAF認証で承認 -> トークン発行
```

## 検証フロー

```
1. setup.sh でインフラ構築（テナント、認証設定、ポリシー、クライアント）
2. verify.sh でユーザー登録 + FIDO-UAF デバイス登録 + CIBA フロー検証
3. ciba-device-auth.sh で個別のCIBAデバイス認証を実行
```
