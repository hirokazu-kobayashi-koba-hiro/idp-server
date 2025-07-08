---
sidebar_position: 2
---

# はじめにガイド

このセクションでは、ローカル環境で `idp-server` をセットアップして起動する方法を説明します。

OpenID ConnectフローなどのE2Eテストを数ステップですぐに実行できます。

## 前提条件

以下のツールが事前にインストールされている必要があります：

- Java 21 以上
- Docker & Docker Compose
- Node 18 以上（E2E用）

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

```shell
./sample-config/test-tenant-data.sh \
-e "local" \
-u ito.ichiro \
-p successUserCode001 \
-t 67e7eae6-62b0-4500-9eff-87459f63fc66 \
-b http://localhost:8080 \
-c clientSecretPost \
-s clientSecretPostPassword1234567890123456789012345678901234567890123456789012345678901234567890 \
-n 1e68932e-ed4a-43e7-b412-460665e42df3 \
-l clientSecretPost \
-m clientSecretPostPassword1234567890123456789012345678901234567890123456789012345678901234567890 \
-d false
 ```

### エンドツーエンドテスト（E2E）

セットアップ構成が完了したら、すぐにE2Eテストを実行して、IdPサーバーが正しく動作していることを確認できます。

#### テスト構成
テストスイートは、以下の3つのカテゴリに分類されています：

* 📘 scenario/：ユーザー登録、SSOログイン、CIBAフロー、MFA登録など、実際のユーザーやシステムの振る舞いを再現したシナリオテスト。
* 📕 spec/：OpenID Connect、FAPI、JARM、Verifiable Credentials など、各種仕様準拠を検証するコンプライアンステスト。
* 🐒 monkey/：異常系・エッジケースの検証を行うモンキーテスト。意図的に不正なパラメータやシーケンスを使い、堅牢性をチェック。


#### 実行

```shell
cd e2e
npm install
npm test
```

