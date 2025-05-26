---
sidebar_position: 2
---

# はじめにガイド

このガイドでは、**idp-server** を初めてセットアップして実行する手順を説明します。

## 前提条件

以下のツールが事前にインストールされている必要があります：

- Java 21 以上
- PostgreSQL または MySQL
- Node.js（フロントエンドと連携する場合）
- Docker（オプション、コンテナベースでのセットアップを行う場合）

## インストール手順

### 1. リポジトリのクローン

```bash
git clone https://github.com/hirokazu-kobayashi-koba-hiro/idp-server.git
cd idp-server
```

### 初期準備

* APIキーとシークレットの生成

```shell
./init.sh
```

※ 設定を必要に応じて変更してください

```shell
export IDP_SERVER_DOMAIN=http://localhost:8080/
export IDP_SERVER_API_KEY=xxx
export IDP_SERVER_API_SECRET=xxx
export ENCRYPTION_KEY=xxx
export ENV=local または develop など

docker compose up -d
docker compose logs -f idp-server
```

* テーブル初期化

```shell
./gradlew flywayClean flywayMigrate
```

### 設定の適用

```shell
./setup.sh
```

```shell
./sample-config/test-data.sh \
-e "local" \
-u ito.ichiro@gmail.com \
-p successUserCode \
-t 67e7eae6-62b0-4500-9eff-87459f63fc66 \
-b http://localhost:8080 \
-c clientSecretPost \
-s clientSecretPostPassword1234567890123456789012345678901234567890123456789012345678901234567890 \
-d false
```

### アクセストークンの取得（デバッグ用）

```shell
./sample-config/get-access-token.sh \
-u ito.ichiro@gmail.com \
-p successUserCode \
-t 67e7eae6-62b0-4500-9eff-87459f63fc66 \
-e http://localhost:8080 \
-c clientSecretPost \
-s clientSecretPostPassword1234567890123456789012345678901234567890123456789012345678901234567890
```

### エンドツーエンドテスト（E2E）

```shell
cd e2e
npm install
npm test
```

### Dockerビルドと実行

```shell
docker build -t idp-server .
```

```shell
docker run -p 8080:8080 \
  -e IDP_SERVER_API_KEY=local-key \
  -e IDP_SERVER_API_SECRET=local-secret \
  -e ENCRYPTION_KEY=supersecret \
  -e DB_WRITE_URL=jdbc:postgresql://host.docker.internal:5432/idpserver \
  -e DB_READ_URL=jdbc:postgresql://host.docker.internal:5432/idpserver \
  -e REDIS_HOST=host.docker.internal \
  idp-server:latest -it idp-server ls /app/providers
```

---

次は管理画面のセットアップ、またはOIDCクライアント設定に進んでください。
