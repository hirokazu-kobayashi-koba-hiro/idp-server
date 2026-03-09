# Dockerイメージビルド

GitHubリリースの成果物を利用してDockerイメージをビルドします。

:::warning リポジトリのDockerfileは使用しない
リポジトリ直下の `Dockerfile` はローカル開発用（マルチステージビルド＋ソースからのビルド）です。
商用デプロイでは、リリースJARを使用して本ページの手順でDockerイメージをビルドしてください。
:::

---

## 📦 リリース成果物の取得

### ダウンロード

GitHubリリースページから最新版をダウンロード:

**リリースURL**: https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/releases

```bash
# バージョン指定（リリースページで最新バージョンを確認してください）
VERSION=<LATEST_VERSION>

# JARダウンロード
wget https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/releases/download/v${VERSION}/idp-server-${VERSION}.jar

# チェックサムダウンロード
wget https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/releases/download/v${VERSION}/checksums.txt

# チェックサム検証
sha256sum -c checksums.txt --ignore-missing
```

**期待結果**:
```
idp-server-<VERSION>.jar: OK
```

---

## 🔨 Dockerイメージビルド

### Dockerfile作成

リリースJARを使用する商用デプロイ用のDockerfile:

```dockerfile
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# リリース成果物をコピー
ARG JAR_FILE=idp-server-*.jar
COPY ${JAR_FILE} /app/idp-server.jar

# エントリーポイントスクリプトをコピー
COPY entrypoint.sh /app/entrypoint.sh

# セキュリティ: 非rootユーザーで実行
RUN chmod +x /app/entrypoint.sh && \
    addgroup -S idpserver && \
    adduser -S idpserver -G idpserver && \
    chown -R idpserver:idpserver /app

USER idpserver

ENTRYPOINT ["/app/entrypoint.sh"]
```

### entrypoint.sh 作成

```bash
#!/bin/sh

echo "Starting idp-server..."

# Clean up tmp directory before starting
rm -rf /tmp/tomcat.* 2>/dev/null || true

exec java $JAVA_OPTS -jar /app/idp-server.jar
```

**Note**: `JAVA_OPTS` 環境変数でJVMオプション（ヒープサイズ、GC設定等）を外部から指定できます。

### イメージビルド

```bash
# entrypoint.sh に実行権限を付与
chmod +x entrypoint.sh

# ビルド実行
docker build -t idp-server:${VERSION} .
docker tag idp-server:${VERSION} idp-server:latest
```

### イメージ確認

```bash
# イメージ一覧
docker images | grep idp-server

# 期待結果:
# idp-server   <VERSION>   <IMAGE_ID>   X seconds ago   XXX MB
# idp-server   latest      <IMAGE_ID>   X seconds ago   XXX MB
```

**Note**: 実際の起動・動作確認は [初期設定](./04-initial-configuration.md) を参照してください。

---

## 📤 コンテナレジストリへプッシュ

### 基本フロー

```bash
# 1. レジストリ認証
docker login <REGISTRY_URL>

# 2. イメージタグ付け
docker tag idp-server:${VERSION} <REGISTRY_URL>/idp-server:${VERSION}
docker tag idp-server:${VERSION} <REGISTRY_URL>/idp-server:latest

# 3. プッシュ
docker push <REGISTRY_URL>/idp-server:${VERSION}
docker push <REGISTRY_URL>/idp-server:latest
```

**対応レジストリ**: Amazon ECR, Google Container Registry, Azure Container Registry, Docker Hub等

**Note**: 各レジストリの認証方法・URL形式は、レジストリのドキュメントを参照してください。

---

## 🚨 トラブルシューティング

### イメージビルド失敗

**エラー**: `COPY failed: file not found`

**原因**: JARファイルが存在しない

**対処**:
```bash
# ファイル存在確認
ls -la idp-server-*.jar

# 再ダウンロード
wget https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/releases/download/v${VERSION}/idp-server-${VERSION}.jar
```


---

## 📋 ビルドチェックリスト

### 準備
- [ ] GitHubリリースから最新JARダウンロード
- [ ] チェックサム検証成功
- [ ] Docker環境確認（`docker version`）

### ビルド
- [ ] Dockerfile作成
- [ ] entrypoint.sh 作成
- [ ] イメージビルド成功（`docker build`）
- [ ] イメージ確認（`docker images`）

### レジストリ（任意）
- [ ] レジストリ認証成功
- [ ] バージョンタグでプッシュ
- [ ] `latest` タグでプッシュ
- [ ] レジストリでイメージ確認

---

## 🔗 関連ドキュメント

- [デプロイ概要](./00-overview.md)
- [環境変数設定](./02-environment-variables.md)
- [初期設定](./04-initial-configuration.md)
