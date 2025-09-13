# Dockerイメージビルド

GitHubリリースの成果物を利用してDockerイメージをビルドします。

---

## 📦 リリース成果物の取得

### ダウンロード

GitHubリリースページから最新版をダウンロード:

**リリースURL**: https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/releases

```bash
# バージョン指定
VERSION=0.8.7

# JARダウンロード
wget https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/releases/download/v${VERSION}/idp-server-${VERSION}.jar

# チェックサムダウンロード
wget https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/releases/download/v${VERSION}/checksums.txt

# チェックサム検証
sha256sum -c checksums.txt --ignore-missing
```

**期待結果**:
```
idp-server-0.8.7.jar: OK
```

---

## 🔨 Dockerイメージビルド

### Dockerfile作成

リリースJARを使用するシンプルなDockerfile:

```dockerfile
FROM openjdk:21-slim

WORKDIR /app

# リリース成果物をコピー
COPY idp-server-0.8.7.jar /app/idp-server.jar

# エントリーポイント
ENTRYPOINT ["java", "-jar", "/app/idp-server.jar"]
```

### イメージビルド

```bash
# Dockerfile作成
cat > Dockerfile << 'EOF'
FROM openjdk:21-slim
WORKDIR /app
COPY idp-server-0.8.7.jar /app/idp-server.jar
ENTRYPOINT ["java", "-jar", "/app/idp-server.jar"]
EOF

# ビルド実行
docker build -t idp-server:0.8.7 .
docker tag idp-server:0.8.7 idp-server:latest
```

### イメージ確認

```bash
# イメージ一覧
docker images | grep idp-server

# 期待結果:
# idp-server   0.8.7   <IMAGE_ID>   X seconds ago   XXX MB
# idp-server   latest  <IMAGE_ID>   X seconds ago   XXX MB
```

---

## 🧪 動作確認

### ローカル起動テスト

**Note**: 以下の環境変数が必要です。詳細は [環境変数設定](./02-environment-variables.md) を参照してください。

```bash
docker run --rm -p 8080:8080 \
  -e IDP_SERVER_API_KEY=<API_KEY> \
  -e IDP_SERVER_API_SECRET=<API_SECRET> \
  -e ENCRYPTION_KEY=<ENCRYPTION_KEY> \
  -e DB_WRITER_URL=jdbc:postgresql://host.docker.internal:5432/idpserver \
  -e DB_WRITER_USER_NAME=idp_app_user \
  -e DB_WRITER_PASSWORD=idp_app_user \
  -e DB_READER_URL=jdbc:postgresql://host.docker.internal:5433/idpserver \
  -e DB_READER_USER_NAME=idp_app_user \
  -e DB_READER_PASSWORD=idp_app_user \
  -e REDIS_HOST=host.docker.internal \
  -e REDIS_PORT=6379 \
  idp-server:0.8.7
```

**環境変数の生成方法**:
```bash
# API Key/Secret生成
export IDP_SERVER_API_KEY=$(uuidgen | tr 'A-Z' 'a-z')
export IDP_SERVER_API_SECRET=$(uuidgen | tr 'A-Z' 'a-z' | base64)

# 暗号化キー生成 (32バイト)
export ENCRYPTION_KEY=$(head -c 32 /dev/urandom | base64)
```

### ヘルスチェック

```bash
# 起動待機（約30秒）
sleep 30

# ヘルスチェック確認
curl http://localhost:8080/actuator/health
```

**期待結果**:
```json
{
  "status": "UP"
}
```

---

## 📤 コンテナレジストリへプッシュ

### 基本フロー

```bash
# 1. レジストリ認証
docker login <REGISTRY_URL>

# 2. イメージタグ付け
docker tag idp-server:0.8.7 <REGISTRY_URL>/idp-server:0.8.7
docker tag idp-server:0.8.7 <REGISTRY_URL>/idp-server:latest

# 3. プッシュ
docker push <REGISTRY_URL>/idp-server:0.8.7
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
wget https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/releases/download/v0.8.7/idp-server-0.8.7.jar
```

### コンテナ起動失敗

**エラー**: `Unable to connect to database`

**原因**: データベース接続情報が不正

**対処**:
```bash
# 環境変数確認
docker run --rm idp-server:0.8.7 env | grep DB_WRITER

# 正しい環境変数で再起動
docker run -p 8080:8080 \
  -e DB_WRITER_URL=jdbc:postgresql://正しいホスト:5432/idpserver \
  -e DB_WRITER_USER_NAME=idp_app_user \
  -e DB_WRITER_PASSWORD=<password> \
  ...
```

### ヘルスチェック失敗

**エラー**: `curl: (7) Failed to connect`

**原因**: アプリケーション起動中

**対処**:
```bash
# ログ確認
docker logs <CONTAINER_ID>

# 起動完了まで待機（通常30-60秒）
sleep 60
curl http://localhost:8080/actuator/health
```

---

## 📋 ビルドチェックリスト

### 準備
- [ ] GitHubリリースから最新JARダウンロード
- [ ] チェックサム検証成功
- [ ] Docker環境確認（`docker version`）

### ビルド
- [ ] Dockerfile作成
- [ ] イメージビルド成功（`docker build`）
- [ ] イメージ確認（`docker images`）

### 検証
- [ ] コンテナ起動成功
- [ ] ヘルスチェック成功（`/actuator/health`）
- [ ] ログにエラーなし

### レジストリ
- [ ] レジストリ認証成功
- [ ] バージョンタグでプッシュ（例: `0.8.7`）
- [ ] `latest` タグでプッシュ
- [ ] レジストリでイメージ確認

---

## 🔗 関連ドキュメント

- [デプロイ概要](./00-overview.md)
- [環境変数設定](./02-environment-variables.md)
- [初期設定](./04-initial-configuration.md)
