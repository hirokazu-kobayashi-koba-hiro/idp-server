# サーバー初期設定ガイド

## このドキュメントの目的

**idp-serverを実運用可能な状態で構築する**ことが目標です。

このドキュメントは[クイックスタート](../../content_02_quickstart/quickstart-01-getting-started.md)を補足し、本格的なセットアップに必要な詳細を提供します。

### このドキュメントの位置づけ

**クイックスタート（quickstart-01）との関係**:
- **quickstart-01**: 5分で動作確認（Docker Composeで即座に起動）
- **このドキュメント**: 詳細な理解と実運用準備（環境別の構築手順）

### 学べること

✅ **idp-serverの初期化の詳細**


### 所要時間
⏱️ **約30分**（環境構築含む）

### 次のドキュメント
- [組織初期化](./02-organization-initialization.md) - 組織・テナント・ユーザーの作成

---

## 前提条件

- [quickstartの完了](../../content_02_quickstart/quickstart-01-getting-started.md)

---

## 初期設定とは

初期設定とは、**idp-serverの運用を開始するための最初のステップ**です。

Adminテナントを構築します。

1つのAPIリクエストで以下をまとめて作成できます：

1. **Organization** - Admin用の組織
2. **Tenant** -Adminテナント（Adminタイプ）
3. **Authorization Server** - 認可サーバー設定
4. **User** - 管理者ユーザー
5. **Client** - 管理用クライアント

##

## 事前準備


以下の環境変数を生成します：

| 環境変数名 | 説明 | 生成コマンド例 |
|-----------|------|--------------|
| `IDP_SERVER_API_KEY` | 管理API認証キー | `uuidgen \| tr 'A-Z' 'a-z'` |
| `IDP_SERVER_API_SECRET` | 管理API認証シークレット | `uuidgen \| tr 'A-Z' 'a-z' \| base64` |
| `ENCRYPTION_KEY` | データ暗号化キー (AES-256) | `head -c 32 /dev/urandom \| base64` |
| `POSTGRES_PASSWORD` | PostgreSQLのマスターユーザー用のパスワード | `head -c 24 /dev/urandom \| base64` |
| `DB_OWNER_PASSWORD` | DB管理ユーザー用のパスワード | `head -c 24 /dev/urandom \| base64` |
| `IDP_DB_ADMIN_PASSWORD` | IDP管理ユーザー用のパスワード | `head -c 24 /dev/urandom \| base64` |
| `IDP_DB_APP_PASSWORD` | IDPアプリユーザー用のパスワード | `head -c 24 /dev/urandom \| base64` |
| `ADMIN_USERNAME` | 管理者ユーザー名 | `"administrator_$(date +%s)"` |
| `ADMIN_EMAIL` | 管理者メールアドレス | `"$ADMIN_USERNAME@mail.com"` |
| `ADMIN_PASSWORD` | 管理者パスワード | `head -c 12 /dev/urandom \| base64` |
| `ADMIN_TENANT_ID` | 管理テナントID | `uuidgen \| tr A-Z a-z` |
| `ADMIN_CLIENT_ID` | 管理クライアントID | `uuidgen \| tr A-Z a-z` |
| `ADMIN_CLIENT_ID_ALIAS` | 管理クライアントエイリアス | `"client_$(head -c 4 /dev/urandom \| base64 \| tr -dc 'a-zA-Z0-9' \| head -c 8)"` |
| `ADMIN_CLIENT_SECRET` | 管理クライアントシークレット | `head -c 48 /dev/urandom \| base64` |

**一括生成スクリプト**:

.envファイルを自動で作成します。

```bash
./init.sh
```

## Adminテナント初期設定API

`POST /v1/admin/initialization`に対してAPIを実行します。

**setupスクリプト**:

```txt
#!/bin/zsh
# read .env
set -a; [ -f .env ] && source .env; set +a

echo "env: $ENV"
echo "url: $BASE_URL"

#admin
curl -X POST "${IDP_SERVER_DOMAIN}v1/admin/initialization" \
-u "${IDP_SERVER_API_KEY}:${IDP_SERVER_API_SECRET}" \
-H "Content-Type:application/json" \
--data @./config/examples/"${ENV}"/admin-tenant/initial.json | jq
```

### 正常性確認

**Adminテナントのトークン取得**

```bash
# ⚠️ パスワードに特殊文字(!,$,\等)が含まれる可能性があるため --data-urlencode
curl -X POST "http://localhost:8080/${ADMIN_TENANT_ID}/v1/tokens" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'grant_type=password' \
  --data-urlencode "username=${ADMIN_USER_EMAIL}" \
  --data-urlencode "password=${ADMIN_USER_PASSWORD}" \
  --data-urlencode "client_id=${ADMIN_CLIENT_ID}" \
  --data-urlencode "client_secret=${ADMIN_CLIENT_SECRET}" \
  --data-urlencode 'scope=management' | jq .
```

---

## 次のステップ

✅ サーバーの初期構築が完了しました！

次に、idp-serverの初期設定を行います：

1. **[組織初期化](./02-organization-initialization.md)**
   - 組織・管理者テナント・ユーザーの作成
   - 管理者権限の設定

2. **[テナント設定](./03-tenant-setup.md)**
   - アプリケーション用テナントの作成
   - OAuth/OIDC認証設定

3. **[クライアント登録](./04-client-registration.md)**
   - クライアントアプリケーションの登録
   - 認証フローの設定

---


## 関連ドキュメント

- [クイックスタート](../../content_02_quickstart/quickstart-01-getting-started.md) - 5分で試す
- [組織初期化](./02-organization-initialization.md) - 組織・テナント作成
- [Developer Guide: デプロイメント](../../content_08_ops/commercial-deployment/00-overview.md) - 詳細なデプロイ手順

---

**最終更新**: 2025-10-14
**難易度**: ⭐⭐☆☆☆（初級〜中級）
**対象**: idp-serverを初めてセットアップする管理者・開発者
