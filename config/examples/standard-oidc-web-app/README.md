# Standard OIDC Web Application Configuration

## ユースケース
標準的なWebアプリケーション（サーバーサイドレンダリング）でのOIDC認証を実装するための**localhost開発環境向け**設定例です。

## 特徴
- **Authorization Code Flow**: 最もセキュアな標準フロー
- **Client Secret認証**: サーバーサイドでのシークレット保持
- **Refresh Token**: 長期セッション対応
- **JWT Access Token**: ステートレスなトークン検証
- **監査ログ**: 詳細な操作記録（Issue #913準拠）
- **localhost対応**: HTTP、セキュアCookie無効化で開発環境で即座に動作

## ファイル構成

```
standard-oidc-web-app/
├── onboarding-request.json  # localhost用オンボーディングAPIリクエスト
├── jwks.json                # EC P-256鍵ペア（開発用サンプル）
├── setup.sh                 # 初回セットアップスクリプト（.env連携）
├── update.sh                # 設定更新スクリプト（既存リソース更新）
└── README.md                # このファイル
```

**注意**: このテナントは**Authorization Code Flow**のみサポートします（Password Grantは非サポート）。

## そのまま使用できる設定値

この設定ファイルは**localhost環境でそのまま動作**するように設定されています：

| 項目 | 設定値 | 説明 |
|------|--------|------|
| **Organization ID** | `76c1b7c2-c362-42b6-a19e-f346e7967699` | 開発組織ID |
| **Tenant ID** | `d49fa8d0-00f1-4c5b-b1e8-cc4076c6b1df` | テナントID |
| **User ID (sub)** | `481b2c4c-dfa4-456a-ab1f-9bf41b692aca` | 管理者ユーザーID |
| **Client ID** | `fcdfdf17-d633-448d-b2f0-af1c8ce3ff19` | クライアントID |
| **Domain** | `http://localhost:8080` | IDP Server URL |
| **CORS Origins** | `http://localhost:3000`, `http://localhost:8080` | 許可オリジン |
| **Redirect URI** | `http://localhost:3000/callback` | コールバックURL |
| **Client Secret** | `local-dev-secret-32-chars-long` | 開発用シークレット |
| **Admin Email** | `admin@localhost.local` | 管理者メール |
| **Admin Password** | `LocalDevPassword123` | 管理者パスワード |
| **Session Cookie** | `LOCAL_DEV_SESSION` | Cookie名 |
| **Secure Cookie** | `false` | HTTP対応 |

## クイックスタート

### 🚀 初回セットアップ（setup.sh）

```bash
# このディレクトリから実行
cd /path/to/idp-server/config/examples/standard-oidc-web-app
./setup.sh
```

**`setup.sh` が自動実行すること:**
1. `.env` から管理者情報を読み込み
2. システム管理者トークンを取得
3. オンボーディングAPIを実行（新規作成）
4. 作成されたリソースのIDを表示
5. 動作確認用の手順を表示

### 🔄 設定更新（update.sh）

既にリソースが存在する場合、設定を更新できます：

```bash
cd /path/to/idp-server/config/examples/standard-oidc-web-app
./update.sh
```

**`update.sh` が自動実行すること:**
1. `.env` から管理者情報を読み込み
2. `onboarding-request.json` から設定値を読み込み
3. システム管理者トークンを取得
4. 既存リソースの存在確認
5. テナント設定を更新（`onboarding-request.json`の値を使用）
6. クライアント設定を更新（`onboarding-request.json`の値を使用）

**更新される項目（onboarding-request.jsonから自動取得）:**
- テナント名・ドメイン
- CORS設定
- セッション設定
- 監査ログ設定
- クライアント名・シークレット
- リダイレクトURI
- スコープ・認証方式

### 🗑️ リソース削除（delete.sh）

組織と関連する全リソース（テナント・ユーザー・クライアント）を削除します：

```bash
cd /path/to/idp-server/config/examples/standard-oidc-web-app
./delete.sh
```

**`delete.sh` が自動実行すること:**
1. `.env` から管理者情報を読み込み
2. `onboarding-request.json` からリソースIDを読み込み
3. システム管理者トークンを取得
4. クライアントを削除（Management API経由）
5. ユーザーを削除（Management API経由）
6. テナントを削除（Management API経由）
7. 組織を削除（Management API経由）

**削除順序の重要性:**
- データベース外部キー制約を回避するため、**子リソースから順に削除**
- クライアント → ユーザー → テナント → 組織の順序を厳守
- 各ステップでエラーが発生した場合は適切に処理

**削除される項目:**
- クライアント（Client）
- ユーザー（User）
- テナント（Tenant）
- 組織（Organization）
- 組織-テナント関係（organization_tenants - テナント削除時に自動削除）

### 📖 方法2: 手動セットアップ（3ステップ）

#### 1. システム管理者トークンを取得

```bash
# .env から管理者情報を読み込んでトークン取得
cd /path/to/idp-server/config/examples/standard-oidc-web-app

# .env ファイルを読み込み
set -a; [ -f ../../../.env ] && source ../../../.env; set +a

# トークン取得（--data-urlencodeで特殊文字を自動エスケープ）
SYSTEM_ACCESS_TOKEN=$(curl -s -X POST \
  "${AUTHORIZATION_SERVER_URL}/${ADMIN_TENANT_ID}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=password" \
  --data-urlencode "username=${ADMIN_USER_EMAIL}" \
  --data-urlencode "password=${ADMIN_USER_PASSWORD}" \
  --data-urlencode "client_id=${ADMIN_CLIENT_ID}" \
  --data-urlencode "client_secret=${ADMIN_CLIENT_SECRET}" \
  --data-urlencode "scope=account management" | jq -r '.access_token')

echo "✅ Access Token: ${SYSTEM_ACCESS_TOKEN:0:20}..."
```

または、既存の`config/token.sh`を使用：

```bash
cd /path/to/idp-server
export SYSTEM_ACCESS_TOKEN=$(./config/token.sh)
echo "✅ Access Token: ${SYSTEM_ACCESS_TOKEN:0:20}..."
```

#### 2. オンボーディングAPI実行

```bash
# このディレクトリから実行（.envから環境変数を読み込み済み）
curl -X POST ${AUTHORIZATION_SERVER_URL}/v1/management/onboarding \
  -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  -d @onboarding-request.json
```

#### 3. 動作確認（Authorization Code Flow）

ブラウザで認可エンドポイントにアクセスして、Authorization Code Flowをテストします。

```bash
# 1. ブラウザで以下のURLを開く
open "http://localhost:8080/d49fa8d0-00f1-4c5b-b1e8-cc4076c6b1df/v1/authorizations?response_type=code&client_id=fcdfdf17-d633-448d-b2f0-af1c8ce3ff19&redirect_uri=http://localhost:3000/callback&scope=openid%20profile%20email&state=test-state"

# 2. ログイン画面で入力
#    Email: admin@localhost.local
#    Password: LocalDevPassword123

# 3. リダイレクト先のURLから認可コードを取得
#    http://localhost:3000/callback/?code=XXXXX&state=test-state
#    ↑ この code=XXXXX の部分をコピー

# 4. 認可コードをトークンに交換
curl -X POST http://localhost:8080/d49fa8d0-00f1-4c5b-b1e8-cc4076c6b1df/v1/tokens \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code" \
  -d "code=RfvantsAQknPn4raHQiwsW1C4rI" \
  -d "redirect_uri=http://localhost:3000/callback/" \
  -d "client_id=fcdfdf17-d633-448d-b2f0-af1c8ce3ff19" \
  -d "client_secret=local-dev-secret-32-chars-long"
```


## 設定項目の詳細説明

### セッション設定（`session_config`）

```json
{
  "cookie_name": "LOCAL_DEV_SESSION",
  "use_secure_cookie": false
}
```

- **cookie_name**: セッションCookie名（テナントごとに一意）
- **use_secure_cookie**: `false`でHTTP環境でも動作（開発環境のみ。本番では`true`必須）

### CORS設定（`cors_config`）

```json
{
  "allow_origins": [
    "http://localhost:3000",
    "http://localhost:8080"
  ]
}
```

- SPAやクライアントサイドJavaScriptからのAPI呼び出しを許可するオリジンを指定
- localhost環境で一般的なポート（3000=フロントエンド、8080=バックエンド）を許可

### 監査ログ設定（`security_event_log_config`）

```json
{
  "format": "structured_json",
  "stage": "processed",
  "include_user_id": true,
  "include_user_ex_sub": true,
  "include_client_id": true,
  "include_ip": true,
  "persistence_enabled": true,
  "include_detail": true
}
```

- **format**: `structured_json` で構造化ログ
- **stage**: `processed` で処理後のイベント記録
- **persistence_enabled**: データベースへの永続化（Issue #913対応）
- **include_detail**: 詳細な操作情報を記録

### トークン有効期限（`extension`）

```json
{
  "access_token_duration": 3600,      // 1時間
  "id_token_duration": 3600,          // 1時間
  "refresh_token_duration": 86400     // 24時間
}
```

- **access_token**: APIアクセス用（短命推奨: 1時間）
- **id_token**: ユーザー認証情報（access_tokenと同じ有効期限推奨）
- **refresh_token**: 長期セッション用（24時間〜30日）


## トラブルシューティング

### エラー: `invalid_client`

```bash
# 確認事項
# 1. client_idが正しいか
echo "fcdfdf17-d633-448d-b2f0-af1c8ce3ff19"

# 2. client_secretが正しいか
echo "local-dev-secret-32-chars-long!!!"

# 3. token_endpoint_auth_methodが一致しているか
# onboarding-request.jsonでは "client_secret_post" を使用
```

### エラー: `invalid_redirect_uri`

```bash
# リダイレクトURIは完全一致が必要（末尾スラッシュも含む）
# 設定: http://localhost:3000/callback/
# ✅ 正しい: http://localhost:3000/callback/
# ❌ 間違い: http://localhost:3000/callback
```

### エラー: `cors_error`

```bash
# CORS許可オリジンを確認
# 設定: ["http://localhost:3000", "http://localhost:8080"]
# プロトコル・ホスト・ポート番号まで完全一致が必要
```

### オンボーディングAPIエラー: 組織/テナントが既に存在

```bash
# 同じIDで複数回実行するとエラー
# 解決策: 新しいUUIDを生成して再実行、または既存のデータを削除
```

### ログイン画面が表示されない

IDPサーバーが起動しているか確認：
```bash
curl -v http://localhost:8080/health
```

テナントが作成されているか確認：
```bash
curl -X GET http://localhost:8080/v1/management/tenants/d49fa8d0-00f1-4c5b-b1e8-cc4076c6b1df \
  -H "Authorization: Bearer ${SYSTEM_ACCESS_TOKEN}"
```

## 参考資料

- [RFC 6749: OAuth 2.0 Authorization Framework](https://datatracker.ietf.org/doc/html/rfc6749)
- [OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html)
- [RFC 7519: JSON Web Token (JWT)](https://datatracker.ietf.org/doc/html/rfc7519)
- AI開発者ガイド: `documentation/docs/content_10_ai_developer/ai-13-control-plane.md`
- E2Eテスト参考実装: `e2e/src/tests/usecase/standard/standard-01-onboarding-and-audit.test.js`

## ライセンス

このサンプル設定は学習・開発目的で自由に使用できます。
本番環境では必ずシークレット値を変更してください。
