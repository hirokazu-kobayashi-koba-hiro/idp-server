# Dockerイメージビルド

GitHubリリースの成果物を利用してDockerイメージをビルドします。

---

## 📦 リリース成果物の取得

### ダウンロード

GitHubリリースページから最新版をダウンロード:

**リリースURL**: https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/releases

```bash
# バージョン指定
VERSION=0.9.20

# JARダウンロード
wget https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/releases/download/v${VERSION}/idp-server-${VERSION}.jar

# チェックサムダウンロード
wget https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/releases/download/v${VERSION}/checksums.txt

# チェックサム検証
sha256sum -c checksums.txt --ignore-missing
```

**期待結果**:
```
idp-server-0.9.20.jar: OK
```

---

## 🔨 Dockerイメージビルド

### Dockerfile作成

リリースJARを使用するシンプルなDockerfile:

```dockerfile
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# リリース成果物をコピー
COPY idp-server-0.9.20.jar /app/idp-server.jar

# エントリーポイント
ENTRYPOINT ["java", "-jar", "/app/idp-server.jar"]
```

### イメージビルド

```bash
# Dockerfile作成
cat > Dockerfile << 'EOF'
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY idp-server-0.9.20.jar /app/idp-server.jar
ENTRYPOINT ["java", "-jar", "/app/idp-server.jar"]
EOF

# ビルド実行
docker build -t idp-server:0.9.20 .
docker tag idp-server:0.9.20 idp-server:latest
```

### イメージ確認

```bash
# イメージ一覧
docker images | grep idp-server

# 期待結果:
# idp-server   0.9.20   <IMAGE_ID>   X seconds ago   XXX MB
# idp-server   latest  <IMAGE_ID>   X seconds ago   XXX MB
```

**Note**: 実際の起動・動作確認は [初期設定](./04-initial-configuration.md) を参照してください。

---

## 📤 コンテナレジストリへプッシュ

### 基本フロー

```bash
# 1. レジストリ認証
docker login <REGISTRY_URL>

# 2. イメージタグ付け
docker tag idp-server:0.9.20 <REGISTRY_URL>/idp-server:0.9.20
docker tag idp-server:0.9.20 <REGISTRY_URL>/idp-server:latest

# 3. プッシュ
docker push <REGISTRY_URL>/idp-server:0.9.20
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
wget https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/releases/download/v0.9.20/idp-server-0.9.20.jar
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

### レジストリ（任意）
- [ ] レジストリ認証成功
- [ ] バージョンタグでプッシュ（例: `0.9.20`）
- [ ] `latest` タグでプッシュ
- [ ] レジストリでイメージ確認

---

## 🔗 関連ドキュメント

- [デプロイ概要](./00-overview.md)
- [環境変数設定](./02-environment-variables.md)
- [初期設定](./04-initial-configuration.md)
