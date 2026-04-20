# IdP Server Sample Web Application

idp-server の機能を体験できるサンプル Web アプリケーション。

## 技術スタック

- **Next.js** 15 (React 19)
- **NextAuth** v5 (OAuth/OIDC認証)
- **Material-UI** v6

## 前提条件

- Node.js 20+
- idp-server が起動済み（`docker compose up -d`）
- テナント設定が投入済み

## セットアップ

### 1. idp-server の起動

```bash
# プロジェクトルートで
docker compose up -d --build
```

### 2. テナント設定の投入

```bash
cd config/examples/subdomain-oidc-web-app
./setup.sh
```

このスクリプトが以下を自動実行:
- テナント `a1b2c3d4-5e6f-7a8b-9c0d-1e2f3a4b5c6d` の作成
- Web クライアント `8a9f5e2c-1b3d-4c6a-9f8e-7d5c3a2b1e4f` の作成
- 認証設定の登録（パスワード、FIDO2、FIDO-UAF、Email）
- 認証ポリシーの登録（OAuth、CIBA、FIDO2登録）

### 3. 環境変数の設定

```bash
cd sample-web

# テンプレートから .env を生成
./setup_env.sh

# または手動で .env を作成
cp .env.template .env
```

`.env` の設定値:

| 変数名 | 説明 | デフォルト値 |
|--------|------|-------------|
| `AUTH_SECRET` | NextAuth セッション暗号化キー | `setup_env.sh` で自動生成 |
| `NEXT_PUBLIC_IDP_SERVER_ISSUER` | idp-server の Issuer URL | `https://api.local.test/{tenant-id}` |
| `NEXT_PUBLIC_FRONTEND_URL` | sample-web のフロントエンド URL | `https://sample.local.test` |
| `NEXT_PUBLIC_IDP_CLIENT_ID` | OAuth クライアント ID | `8a9f5e2c-1b3d-4c6a-9f8e-7d5c3a2b1e4f` |
| `NEXT_IDP_CLIENT_SECRET` | OAuth クライアントシークレット | `local-dev-public-secret-32char` |

### 4. DNS 設定

`/etc/hosts` に以下を追加:

```
127.0.0.1 api.local.test
127.0.0.1 auth.local.test
127.0.0.1 sample.local.test
```

### 5. 起動

```bash
npm install
npm run dev
```

https://sample.local.test でアクセス。

## ページ一覧

### メイン機能（認証が必要）

| パス | 機能 |
|------|------|
| `/` | ログインページ（Passkey認証） |
| `/home` | ダッシュボード（ユーザー情報、トークン表示、パスキー管理、パスワード変更、アカウント削除） |

### デモページ（認証不要）

| パス | 機能 | 説明 |
|------|------|------|
| `/fido-uaf-auth-demo` | FIDO-UAF 認可コードフロー | login_hint + authentication-status + Push 通知デモ |
| `/fido-uaf-device-demo` | デバイスクレデンシャル | device_secret 発行、JWT 認証、JWT Bearer Grant |
| `/fido2-attestation-demo` | FIDO2 Attestation | Attestation フォーマットテスト |
| `/fido2-rpid-demo` | FIDO2 RP ID | RP ID 設定テスト |
| `/security-demo` | セキュリティ | AUTH_SESSION Cookie 保護シミュレーション |

## FIDO-UAF 認可コードフロー デモ

`/fido-uaf-auth-demo` で体験できる新機能:

1. **login_hint ユーザー事前解決** - 認可リクエストに `login_hint=sub:{userId}` を付与してユーザーを事前特定
2. **view-data に login_hint 返却** - SPA が login_hint の有無で UI を切り替え可能
3. **authentication-status API** - 認証進捗のポーリング（`in_progress` → `success`）
4. **Push 通知送信** - デバイスへの FCM/APNS Push 通知

デモの流れ:

```
Phase 1 (Setup):
  ユーザー登録 → FIDO-UAF デバイス登録 → 認可完了

Phase 2 (Demo):
  login_hint 付き認可 → view-data 確認 → authentication-status 確認
  → Push 通知送信 → パスワード認証(フォールバック)
  → authentication-status 確認 → 認可 → トークン発行
```

## テナント設定

テナント設定ファイルは `config/examples/subdomain-oidc-web-app/` に格納:

```
config/examples/subdomain-oidc-web-app/
├── onboarding-request.json          # テナント・認可サーバー設定
├── web-client.json                  # sample-web 用クライアント
├── authentication-config/
│   ├── fido2/webauthn4j.json        # FIDO2/WebAuthn 設定
│   ├── fido-uaf/mock-server.json    # FIDO-UAF 設定（mock-server連携）
│   ├── email/no-action.json         # Email OTP 設定
│   └── initial-registration/standard.json  # ユーザー登録設定
├── authentication-policy/
│   ├── oauth.json                   # OAuth 認証ポリシー
│   ├── ciba.json                    # CIBA 認証ポリシー
│   └── fido2-registration.json      # FIDO2 登録ポリシー
├── setup.sh                         # セットアップスクリプト
├── update.sh                        # 更新スクリプト
└── delete.sh                        # 削除スクリプト
```
