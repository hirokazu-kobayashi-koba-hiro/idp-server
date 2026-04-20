# MFA (Password + FIDO-UAF Device Authentication) Use Case Template

パスワード認証 + FIDO-UAF デバイス認証（Push 通知 → 生体認証）を設定するテンプレートセット。

> Claude Code を使用している場合は `/use-case-setup` でヒアリング付きの対話型セットアップが利用できます。

> **templates vs examples**: このテンプレート（`config/templates/`）はゼロからの完全セットアップ用です。Organization・テナント・クライアントを一括作成します。

## 設定内容

| 項目 | 設定値 |
|------|--------|
| 認証方式 | FIDO-UAF デバイス認証 + パスワードフォールバック |
| デバイス認証 | device_secret_jwt（HS256）認証 |
| Push 通知 | FCM（Firebase Cloud Messaging） |
| パスワードポリシー | 8文字以上、文字種制約なし |
| アカウントロック | 5回失敗でロック |
| セッション有効期限 | 24時間 |
| ユーザー登録必須項目 | email, password, name |

## 認証フロー

### login_hint 付き（デバイス認証）
```
認可リクエスト（login_hint=sub:{userId}）
  → User 事前解決
  → SPA: デバイス通知送信
  → Device: Push 通知受信 → FIDO-UAF 生体認証
  → SPA: authentication-status ポーリング → success
  → authorize → トークン発行
```

### login_hint なし（パスワードフォールバック）
```
認可リクエスト
  → SPA: パスワード認証
  → authorize → トークン発行
```

## ファイル構成

| ファイル | 用途 | API |
|---------|------|-----|
| `onboarding-template.json` | Organization + Organizer Tenant + Admin User + Client | `POST /v1/management/onboarding` |
| `public-tenant-template.json` | Public Tenant（パスワードポリシー + デバイスクレデンシャル設定） | `POST /v1/management/tenants` |
| `authentication-config-initial-registration.json` | ユーザー登録スキーマ | `POST .../authentication-configurations` |
| `authentication-config-fido-uaf.json` | FIDO-UAF 認証設定（外部API委譲モード） | `POST .../authentication-configurations` |
| `authentication-config-device-notification.json` | デバイス通知設定（FCM） | `POST .../authentication-configurations` |
| `authentication-policy.json` | FIDO-UAF + パスワードフォールバック認証ポリシー | `POST .../authentication-policies` |
| `public-client-template.json` | アプリケーションクライアント | `POST .../clients` |
| `setup.sh` | 上記を順番に実行するスクリプト | - |
| `verify.sh` | セットアップの検証スクリプト | - |

## セットアップ手順

### 前提条件

- idp-server が起動済み
- システム管理者テナントが存在（初期セットアップ済み）
- `.env` に管理者認証情報を設定済み
- FIDO-UAF mock-server 起動済み（ポート4000）

### 自動セットアップ

```bash
# セットアップ実行
./setup.sh

# ドライラン
./setup.sh --dry-run
```

### 環境変数でカスタマイズ

```bash
# FIDO-UAF サーバー URL
FIDO_UAF_SERVICE_URL="http://host.docker.internal:4000" ./setup.sh

# FCM クレデンシャル（本番環境）
FCM_CREDENTIAL='{"type":"service_account",...}' ./setup.sh

# 組織名を指定
ORGANIZATION_NAME="my-company" ./setup.sh
```

### カスタマイズ可能な環境変数

#### FIDO-UAF / デバイス通知

| 環境変数 | デフォルト | 説明 |
|---------|-----------|------|
| `FIDO_UAF_SERVICE_URL` | `http://host.docker.internal:4000` | FIDO-UAF サーバー URL |
| `FCM_CREDENTIAL` | `{}` | FCM サービスアカウント JSON |

#### トークン有効期限

| 環境変数 | デフォルト | 説明 |
|---------|-----------|------|
| `ACCESS_TOKEN_DURATION` | `3600` | アクセストークン有効期限（秒） |
| `ID_TOKEN_DURATION` | `3600` | IDトークン有効期限（秒） |
| `REFRESH_TOKEN_DURATION` | `86400` | リフレッシュトークン有効期限（秒） |

## セットアップフロー

```
Step 1: システム管理者トークン取得
Step 2: オンボーディング（Organization + Organizer Tenant + Admin）
Step 3: ORGANIZER管理者トークン取得
Step 4: パブリックテナント作成（デバイスクレデンシャル設定付き）
Step 5: 認証設定 - 初期登録
Step 6: 認証設定 - FIDO-UAF（mock-server連携）
Step 7: 認証設定 - デバイス通知（FCM）
Step 8: 認証ポリシー（FIDO-UAF + パスワードフォールバック）
Step 9: クライアント作成
```

## 検証

```bash
# セットアップ後に検証
./verify.sh

# 組織名を指定して検証
./verify.sh --org my-company
```

## 関連ドキュメント

- [認可コードフロー + FIDO-UAF](../../../../documentation/docs/content_05_how-to/phase-3-advanced/fido-uaf/04-authorization-code-flow.md)
- [CIBA + FIDO-UAF](../../../../documentation/docs/content_05_how-to/phase-3-advanced/fido-uaf/01-ciba-flow.md)
- [FIDO-UAF 登録](../../../../documentation/docs/content_05_how-to/phase-3-advanced/fido-uaf/02-registration.md)
