# Dockerfile ベストプラクティス

効率的で安全なコンテナイメージを作成するためのDockerfileのベストプラクティスを学びます。

---

## 目次

1. [Dockerfileの基本構造](#dockerfileの基本構造)
2. [ベースイメージの選択](#ベースイメージの選択)
3. [レイヤー最適化](#レイヤー最適化)
4. [マルチステージビルド](#マルチステージビルド)
5. [セキュリティ](#セキュリティ)
6. [Javaアプリケーション向け](#javaアプリケーション向け)
7. [.dockerignore](#dockerignore)
8. [ビルド引数と環境変数](#ビルド引数と環境変数)

---

## Dockerfileの基本構造

### 基本的なDockerfile

```dockerfile
# ベースイメージの指定
FROM eclipse-temurin:21-jre-alpine

# メタデータの追加
LABEL maintainer="team@example.com"
LABEL version="1.0.0"

# 作業ディレクトリの設定
WORKDIR /app

# ファイルのコピー
COPY target/*.jar app.jar

# ポートの公開宣言
EXPOSE 8080

# コンテナ起動時のコマンド
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 主要な命令

| 命令 | 説明 | 例 |
|-----|------|-----|
| `FROM` | ベースイメージの指定 | `FROM alpine:3.19` |
| `WORKDIR` | 作業ディレクトリの設定 | `WORKDIR /app` |
| `COPY` | ファイルのコピー | `COPY src/ dest/` |
| `ADD` | ファイルのコピー（展開機能付き） | `ADD archive.tar.gz /` |
| `RUN` | コマンドの実行 | `RUN apt-get update` |
| `ENV` | 環境変数の設定 | `ENV JAVA_OPTS="-Xmx512m"` |
| `ARG` | ビルド時引数 | `ARG VERSION=1.0.0` |
| `EXPOSE` | ポートの公開宣言 | `EXPOSE 8080` |
| `USER` | 実行ユーザーの変更 | `USER appuser` |
| `ENTRYPOINT` | コンテナ起動コマンド | `ENTRYPOINT ["java", "-jar"]` |
| `CMD` | デフォルト引数 | `CMD ["app.jar"]` |

---

## ベースイメージの選択

### イメージ比較

```
┌─────────────────────────────────────────────────────────────┐
│                    ベースイメージの選択                       │
├─────────────────────────────────────────────────────────────┤
│  フルイメージ                                                │
│  ubuntu:22.04, debian:bookworm                              │
│  - サイズ: 大 (70-100MB)                                    │
│  - パッケージ: 豊富                                          │
│  - デバッグ: 容易                                            │
│                                                             │
│  Slimイメージ                                                │
│  debian:bookworm-slim                                       │
│  - サイズ: 中 (50-80MB)                                     │
│  - パッケージ: 基本的なもの                                   │
│  - バランス型                                                │
│                                                             │
│  Alpineイメージ                                              │
│  alpine:3.19                                                │
│  - サイズ: 小 (5-7MB)                                       │
│  - musl libc使用（glibc互換性注意）                          │
│  - セキュリティ重視                                          │
│                                                             │
│  Distrolessイメージ                                          │
│  gcr.io/distroless/java21                                   │
│  - サイズ: 最小限                                            │
│  - シェルなし（デバッグ困難）                                 │
│  - 最高のセキュリティ                                        │
└─────────────────────────────────────────────────────────────┘
```

### Java向けベースイメージ

```dockerfile
# 推奨: Eclipse Temurin (旧AdoptOpenJDK)
FROM eclipse-temurin:21-jre-alpine

# Amazon Corretto (AWS環境向け)
FROM amazoncorretto:21-alpine

# Distroless (本番環境、最小サイズ)
FROM gcr.io/distroless/java21-debian12

# デバッグ用 (Distrolessのデバッグ版)
FROM gcr.io/distroless/java21-debian12:debug
```

### バージョン固定

```dockerfile
# 悪い例: 可変タグ
FROM eclipse-temurin:latest
FROM eclipse-temurin:21

# 良い例: 具体的なバージョン
FROM eclipse-temurin:21.0.1_12-jre-alpine

# SHA256ダイジェストで完全固定（最も安全）
FROM eclipse-temurin@sha256:abc123...
```

---

## レイヤー最適化

### レイヤーの仕組み

```
各RUN, COPY, ADD命令が1レイヤーを作成

┌─────────────────────────────────────────┐
│ Layer 4: COPY app.jar /app/             │  100MB
├─────────────────────────────────────────┤
│ Layer 3: RUN apt-get install -y curl    │  50MB
├─────────────────────────────────────────┤
│ Layer 2: RUN apt-get update             │  30MB (キャッシュ残り)
├─────────────────────────────────────────┤
│ Layer 1: FROM debian:bookworm-slim      │  74MB
└─────────────────────────────────────────┘
Total: 254MB (Layer 2のキャッシュ含む)
```

### RUN命令の最適化

```dockerfile
# 悪い例: 複数のRUN命令
RUN apt-get update
RUN apt-get install -y curl
RUN apt-get install -y wget
RUN rm -rf /var/lib/apt/lists/*

# 良い例: 1つのRUN命令にまとめる
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        curl \
        wget \
    && rm -rf /var/lib/apt/lists/*
```

### COPY命令の順序

```dockerfile
# 悪い例: 変更頻度の高いファイルを先にコピー
COPY . /app
RUN npm install

# 良い例: 変更頻度の低いものから順にコピー
# 依存関係ファイルを先にコピー（キャッシュ活用）
COPY package.json package-lock.json ./
RUN npm ci --only=production

# ソースコードは最後にコピー
COPY . .
```

### キャッシュの活用

```dockerfile
# Javaの例: 依存関係を先にダウンロード
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Gradleラッパーとビルド設定を先にコピー
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

# 依存関係のみダウンロード
RUN ./gradlew dependencies --no-daemon

# ソースコードをコピーしてビルド
COPY src src
RUN ./gradlew build -x test --no-daemon
```

---

## マルチステージビルド

### 基本パターン

```dockerfile
# ==================== ビルドステージ ====================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Gradleファイルをコピー
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

# 依存関係をダウンロード
RUN ./gradlew dependencies --no-daemon

# ソースをコピーしてビルド
COPY src src
RUN ./gradlew bootJar --no-daemon

# ==================== 実行ステージ ====================
FROM eclipse-temurin:21-jre-alpine

# 非rootユーザー作成
RUN addgroup -g 1000 app && adduser -u 1000 -G app -D app

WORKDIR /app

# ビルド成果物のみをコピー
COPY --from=builder /app/build/libs/*.jar app.jar

# 所有権の設定
RUN chown -R app:app /app

USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### サイズ比較

```
シングルステージ（JDK含む）:
┌─────────────────────────────────────────┐
│ JDK + ビルドツール + ソース + JAR       │  ~500MB
└─────────────────────────────────────────┘

マルチステージ（JREのみ）:
┌─────────────────────────────────────────┐
│ JRE + JAR                               │  ~200MB
└─────────────────────────────────────────┘
```

### 複数のビルドステージ

```dockerfile
# テスト用ステージ
FROM eclipse-temurin:21-jdk-alpine AS tester
WORKDIR /app
COPY --from=builder /app .
RUN ./gradlew test --no-daemon

# 本番用ステージ
FROM eclipse-temurin:21-jre-alpine AS production
COPY --from=builder /app/build/libs/*.jar app.jar
# ...

# 開発用ステージ（デバッグツール付き）
FROM eclipse-temurin:21-jdk-alpine AS development
RUN apk add --no-cache curl vim
COPY --from=builder /app .
# ...
```

---

## セキュリティ

### 非rootユーザーでの実行

```dockerfile
FROM eclipse-temurin:21-jre-alpine

# グループとユーザーを作成
RUN addgroup -g 1000 appgroup && \
    adduser -u 1000 -G appgroup -D -s /sbin/nologin appuser

WORKDIR /app

COPY --chown=appuser:appgroup target/*.jar app.jar

# 非rootユーザーに切り替え
USER appuser

ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 最小権限の原則

```dockerfile
FROM eclipse-temurin:21-jre-alpine

# 必要なパッケージのみインストール
RUN apk add --no-cache \
    dumb-init \
    && rm -rf /var/cache/apk/*

# 不要なファイルを削除
RUN rm -rf /tmp/* /var/tmp/*

# 読み取り専用ファイルシステムで実行可能にする
WORKDIR /app
COPY --chmod=444 target/*.jar app.jar

USER 1000:1000

# dumb-initでシグナル処理を適切に
ENTRYPOINT ["/usr/bin/dumb-init", "--"]
CMD ["java", "-jar", "app.jar"]
```

### 機密情報の取り扱い

```dockerfile
# 悪い例: 機密情報をイメージに含める
ENV DATABASE_PASSWORD=secret123
COPY secrets.properties /app/

# 良い例: 実行時に環境変数で渡す
# docker run -e DATABASE_PASSWORD=xxx my-app

# 良い例: Docker Secretsを使用
# docker run --secret id=db_password my-app

# BuildKitのシークレットマウント
RUN --mount=type=secret,id=npmrc,target=/root/.npmrc \
    npm ci
```

### 脆弱性スキャン

```bash
# Trivyでスキャン
trivy image my-app:latest

# Docker Scoutでスキャン
docker scout cves my-app:latest

# Snykでスキャン
snyk container test my-app:latest
```

---

## Javaアプリケーション向け

### Spring Boot最適化Dockerfile

```dockerfile
# ==================== ビルドステージ ====================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Gradleファイルをコピー
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY libs libs

# 依存関係のダウンロード
RUN ./gradlew dependencies --no-daemon

# ソースをコピーしてビルド
COPY src src
RUN ./gradlew bootJar --no-daemon

# JARを展開（レイヤー分割のため）
RUN java -Djarmode=layertools -jar build/libs/*.jar extract

# ==================== 実行ステージ ====================
FROM eclipse-temurin:21-jre-alpine

# タイムゾーン設定
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Tokyo /etc/localtime && \
    echo "Asia/Tokyo" > /etc/timezone && \
    apk del tzdata

# 非rootユーザー
RUN addgroup -g 1000 app && adduser -u 1000 -G app -D app

WORKDIR /app

# Spring Bootのレイヤーを順番にコピー
COPY --from=builder --chown=app:app /app/dependencies/ ./
COPY --from=builder --chown=app:app /app/spring-boot-loader/ ./
COPY --from=builder --chown=app:app /app/snapshot-dependencies/ ./
COPY --from=builder --chown=app:app /app/application/ ./

USER app

# JVMオプション
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=50.0 \
    -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
```

### JVMオプションの最適化

```dockerfile
ENV JAVA_OPTS="\
    # コンテナのリソース制限を認識
    -XX:+UseContainerSupport \
    # RAMの75%をヒープに割り当て
    -XX:MaxRAMPercentage=75.0 \
    # 初期ヒープサイズ
    -XX:InitialRAMPercentage=50.0 \
    # G1GCを使用（デフォルト）
    -XX:+UseG1GC \
    # 乱数生成の高速化
    -Djava.security.egd=file:/dev/./urandom \
    # タイムゾーン
    -Duser.timezone=Asia/Tokyo"
```

---

## .dockerignore

### 基本的な.dockerignore

```plaintext
# バージョン管理
.git
.gitignore
.gitattributes

# IDE設定
.idea
*.iml
.vscode
.settings
.project
.classpath

# ビルド成果物
build
target
out
*.class
*.jar
*.war

# 依存関係キャッシュ
.gradle
.m2
node_modules

# ログとテンポラリ
*.log
tmp
temp

# ドキュメント
*.md
!README.md
docs
documentation

# テスト
test
tests
*_test.go
*.test

# Docker関連
Dockerfile*
docker-compose*
.dockerignore

# 環境設定
.env
.env.*
*.local

# シークレット
*.pem
*.key
credentials*
secrets*
```

---

## ビルド引数と環境変数

### ARGとENVの使い分け

```dockerfile
# ARG: ビルド時のみ使用
ARG JAVA_VERSION=21
FROM eclipse-temurin:${JAVA_VERSION}-jre-alpine

# ARGはFROMの後で再定義が必要
ARG APP_VERSION=1.0.0
LABEL version="${APP_VERSION}"

# ENV: 実行時も使用
ENV SPRING_PROFILES_ACTIVE=production
ENV SERVER_PORT=8080

# ARGのデフォルト値をENVに渡す
ARG DEFAULT_JAVA_OPTS="-Xmx512m"
ENV JAVA_OPTS=${DEFAULT_JAVA_OPTS}
```

### ビルド時の引数指定

```bash
# ビルド引数を指定
docker build \
    --build-arg JAVA_VERSION=21 \
    --build-arg APP_VERSION=2.0.0 \
    -t my-app:2.0.0 .

# 環境変数として実行時に渡す
docker run \
    -e SPRING_PROFILES_ACTIVE=staging \
    -e DATABASE_URL=jdbc:postgresql://db:5432/app \
    my-app:2.0.0
```

---

## まとめ

### チェックリスト

- [ ] 適切なベースイメージを選択（Alpine/Distroless）
- [ ] バージョンを固定
- [ ] マルチステージビルドを使用
- [ ] レイヤーを最適化（キャッシュ活用）
- [ ] 非rootユーザーで実行
- [ ] 機密情報をイメージに含めない
- [ ] HEALTHCHECKを設定
- [ ] .dockerignoreを設定
- [ ] 脆弱性スキャンを実施

### 次のステップ

- [Docker Compose](docker-compose.md) - 複数コンテナの管理
- [Dockerコマンドリファレンス](docker-commands.md) - よく使うコマンド集
- [Kubernetesアーキテクチャ](kubernetes-architecture.md) - オーケストレーション

---

## 参考リソース

- [Dockerfile reference](https://docs.docker.com/engine/reference/dockerfile/)
- [Best practices for writing Dockerfiles](https://docs.docker.com/develop/develop-images/dockerfile_best-practices/)
- [Docker BuildKit](https://docs.docker.com/build/buildkit/)
- [Spring Boot Docker](https://spring.io/guides/topicals/spring-boot-docker/)
