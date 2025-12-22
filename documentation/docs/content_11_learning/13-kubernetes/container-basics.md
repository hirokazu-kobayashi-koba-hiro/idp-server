# コンテナ技術の基礎

コンテナ技術の基本概念と、なぜコンテナが現代のアプリケーション開発・デプロイに不可欠なのかを学びます。

---

## 目次

1. [コンテナとは](#コンテナとは)
2. [仮想マシンとの比較](#仮想マシンとの比較)
3. [Dockerの基本概念](#dockerの基本概念)
4. [コンテナイメージ](#コンテナイメージ)
5. [コンテナレジストリ](#コンテナレジストリ)
6. [コンテナのライフサイクル](#コンテナのライフサイクル)
7. [IDサービスでの活用](#idサービスでの活用)

---

## コンテナとは

### 定義

コンテナは、アプリケーションとその依存関係を1つのパッケージにまとめ、どの環境でも一貫して実行できるようにする技術です。

```
┌─────────────────────────────────────────────────┐
│                   コンテナ                        │
│  ┌─────────────────────────────────────────┐   │
│  │           アプリケーション                  │   │
│  ├─────────────────────────────────────────┤   │
│  │    ライブラリ・依存関係（JDK, Node.js等）   │   │
│  ├─────────────────────────────────────────┤   │
│  │         設定ファイル・環境変数              │   │
│  └─────────────────────────────────────────┘   │
└─────────────────────────────────────────────────┘
```

### なぜコンテナが必要か

#### 1. 環境の一貫性

```
開発環境で動くのに本番で動かない...

従来の問題:
┌──────────┐    ┌──────────┐    ┌──────────┐
│  開発環境  │ ≠ │ ステージ  │ ≠ │   本番    │
│ Java 11   │    │ Java 17  │    │ Java 21  │
│ Ubuntu    │    │ CentOS   │    │ Amazon   │
│           │    │          │    │ Linux    │
└──────────┘    └──────────┘    └──────────┘

コンテナによる解決:
┌──────────────────────────────────────────────┐
│              同一コンテナイメージ                │
│  Java 21 + アプリ + 設定 = 常に同じ動作         │
└──────────────────────────────────────────────┘
        ↓               ↓               ↓
     開発環境        ステージ          本番
```

#### 2. 分離（Isolation）

```
┌─────────────────────────────────────────────────────┐
│                    ホストOS                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐│
│  │  コンテナA    │  │  コンテナB    │  │  コンテナC    ││
│  │  Java 11     │  │  Java 21     │  │  Node.js     ││
│  │  ポート8080   │  │  ポート8080   │  │  ポート3000   ││
│  └──────────────┘  └──────────────┘  └──────────────┘│
│         ↓                ↓                ↓          │
│    異なるJavaバージョンが同一ホストで共存可能          │
└─────────────────────────────────────────────────────┘
```

#### 3. 高速な起動

```
仮想マシン起動: 数分
  OS起動 → サービス起動 → アプリ起動

コンテナ起動: 数秒
  プロセス起動のみ
```

---

## 仮想マシンとの比較

### アーキテクチャの違い

```
仮想マシン（VM）                    コンテナ
┌─────────────────────────┐    ┌─────────────────────────┐
│    App A    │    App B   │    │   App A  │  App B │ App C │
├─────────────┼────────────┤    ├──────────┼────────┼───────┤
│   Guest OS  │  Guest OS  │    │   Bins   │  Bins  │ Bins  │
│   (Ubuntu)  │  (CentOS)  │    │   Libs   │  Libs  │ Libs  │
├─────────────┴────────────┤    ├──────────┴────────┴───────┤
│       Hypervisor         │    │      Container Engine     │
├──────────────────────────┤    │         (Docker)          │
│        Host OS           │    ├───────────────────────────┤
├──────────────────────────┤    │         Host OS           │
│       Hardware           │    ├───────────────────────────┤
└──────────────────────────┘    │        Hardware           │
                                └───────────────────────────┘
```

### 比較表

| 項目 | 仮想マシン | コンテナ |
|------|----------|---------|
| 起動時間 | 分単位 | 秒単位 |
| サイズ | GB単位 | MB単位 |
| リソース効率 | 低い（OS分のオーバーヘッド） | 高い |
| 分離レベル | 完全（ハードウェアレベル） | プロセスレベル |
| 密度 | 1ホストに数十VM | 1ホストに数百コンテナ |
| セキュリティ | 高い | 中程度（カーネル共有） |
| 用途 | 異なるOSが必要な場合 | マイクロサービス |

### 使い分け

```
VMが適している場合:
- 異なるOSカーネルが必要（Linux上でWindows）
- 完全な分離が必要（マルチテナント基盤）
- レガシーアプリケーション

コンテナが適している場合:
- マイクロサービスアーキテクチャ
- CI/CDパイプライン
- 開発環境の標準化
- スケーラブルなWebサービス
```

---

## Dockerの基本概念

### Docker Engine

```
┌─────────────────────────────────────────────────┐
│              Docker Client (CLI)                 │
│              docker run, build, push             │
└────────────────────┬────────────────────────────┘
                     │ REST API
┌────────────────────▼────────────────────────────┐
│              Docker Daemon (dockerd)             │
│  ┌─────────────┐ ┌─────────────┐ ┌──────────┐   │
│  │   Images    │ │  Containers │ │ Networks │   │
│  └─────────────┘ └─────────────┘ └──────────┘   │
│  ┌─────────────┐ ┌─────────────┐                │
│  │   Volumes   │ │   Plugins   │                │
│  └─────────────┘ └─────────────┘                │
└────────────────────┬────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────┐
│              Container Runtime                   │
│              (containerd + runc)                 │
└─────────────────────────────────────────────────┘
```

### 主要コンポーネント

#### 1. イメージ（Image）

コンテナの設計図（読み取り専用テンプレート）

```bash
# イメージの取得
docker pull eclipse-temurin:21-jre

# イメージの一覧
docker images

# 出力例
REPOSITORY          TAG       IMAGE ID       SIZE
eclipse-temurin     21-jre    abc123def456   267MB
postgres            16        def789ghi012   412MB
```

#### 2. コンテナ（Container）

イメージから作成された実行インスタンス

```bash
# コンテナの作成と起動
docker run -d --name my-app eclipse-temurin:21-jre

# コンテナの一覧
docker ps

# 出力例
CONTAINER ID   IMAGE                  STATUS         NAMES
a1b2c3d4e5f6   eclipse-temurin:21-jre Up 5 minutes   my-app
```

#### 3. ボリューム（Volume）

永続データの保存場所

```bash
# ボリュームの作成
docker volume create my-data

# ボリュームをマウントしてコンテナ起動
docker run -d \
  -v my-data:/var/lib/postgresql/data \
  postgres:16
```

#### 4. ネットワーク（Network）

コンテナ間の通信を管理

```bash
# ネットワークの作成
docker network create my-network

# ネットワークに接続してコンテナ起動
docker run -d --network my-network --name db postgres:16
docker run -d --network my-network --name app my-app:latest
```

---

## コンテナイメージ

### レイヤー構造

```
┌─────────────────────────────────────────┐
│  Layer 4: アプリケーション (JAR)          │  ← 変更頻度: 高
├─────────────────────────────────────────┤
│  Layer 3: 依存ライブラリ                  │  ← 変更頻度: 中
├─────────────────────────────────────────┤
│  Layer 2: JDK 21                        │  ← 変更頻度: 低
├─────────────────────────────────────────┤
│  Layer 1: ベースOS (Alpine/Debian)       │  ← 変更頻度: 低
└─────────────────────────────────────────┘

各レイヤーは読み取り専用
キャッシュにより、変更のないレイヤーは再利用される
```

### イメージタグ

```bash
# タグの形式
レジストリ/リポジトリ:タグ

# 例
docker.io/library/postgres:16          # Docker Hub公式
ghcr.io/myorg/my-app:v1.0.0           # GitHub Container Registry
123456789.dkr.ecr.ap-northeast-1.amazonaws.com/my-app:latest  # AWS ECR

# タグのベストプラクティス
my-app:latest           # 開発用（本番では避ける）
my-app:v1.2.3           # セマンティックバージョン（推奨）
my-app:abc123def        # Gitコミットハッシュ
my-app:20240115-1       # 日付ベース
```

### イメージサイズの最適化

```
サイズ比較:
┌────────────────────────────────────────────┐
│ ubuntu:22.04           77MB               │
├────────────────────────────────────────────┤
│ debian:bookworm-slim   74MB               │
├────────────────────────────────────────────┤
│ alpine:3.19            7MB                │
├────────────────────────────────────────────┤
│ distroless/java21      ~200MB             │
│ (Googleの最小イメージ)                      │
└────────────────────────────────────────────┘
```

---

## コンテナレジストリ

### 主要なレジストリ

```
┌─────────────────────────────────────────────────────────┐
│                    コンテナレジストリ                     │
├─────────────────────────────────────────────────────────┤
│  パブリック                                              │
│  ├── Docker Hub (docker.io)     最大のパブリックレジストリ │
│  ├── GitHub Container Registry  GitHubとの連携が容易      │
│  └── Quay.io                    Red Hat運営             │
│                                                         │
│  クラウドプロバイダ                                       │
│  ├── Amazon ECR                 AWSとの統合             │
│  ├── Google Container Registry  GCPとの統合             │
│  └── Azure Container Registry   Azureとの統合           │
│                                                         │
│  プライベート                                            │
│  ├── Harbor                     CNCF認定、エンタープライズ │
│  └── JFrog Artifactory          汎用アーティファクト管理   │
└─────────────────────────────────────────────────────────┘
```

### 基本操作

```bash
# Docker Hubへのログイン
docker login

# プライベートレジストリへのログイン
docker login ghcr.io
docker login 123456789.dkr.ecr.ap-northeast-1.amazonaws.com

# イメージのプッシュ
docker tag my-app:v1.0.0 ghcr.io/myorg/my-app:v1.0.0
docker push ghcr.io/myorg/my-app:v1.0.0

# イメージのプル
docker pull ghcr.io/myorg/my-app:v1.0.0
```

---

## コンテナのライフサイクル

### 状態遷移

```
                    docker create
        ┌──────────────────────────────┐
        │                              ▼
┌───────┴───────┐              ┌──────────────┐
│    Image      │              │   Created    │
│  (イメージ)    │              │   (作成済)   │
└───────────────┘              └──────┬───────┘
                                      │ docker start
                                      ▼
                               ┌──────────────┐
            docker restart ───▶│   Running    │◀─── docker unpause
                               │   (実行中)   │
                               └──────┬───────┘
                                      │
                    ┌─────────────────┼─────────────────┐
                    │                 │                 │
              docker stop       docker pause      プロセス終了
                    │                 │                 │
                    ▼                 ▼                 ▼
             ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
             │   Stopped    │  │   Paused     │  │   Exited     │
             │  (停止済)    │  │  (一時停止)   │  │  (終了)      │
             └──────────────┘  └──────────────┘  └──────┬───────┘
                    │                                    │
                    └──────────────┬─────────────────────┘
                                   │ docker rm
                                   ▼
                            ┌──────────────┐
                            │   Deleted    │
                            │   (削除済)   │
                            └──────────────┘
```

### 基本操作

```bash
# コンテナの作成と起動
docker run -d --name idp-server \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=docker \
  my-idp-server:latest

# コンテナの停止
docker stop idp-server

# コンテナの再起動
docker restart idp-server

# コンテナの削除
docker rm idp-server

# 実行中のコンテナ内でコマンド実行
docker exec -it idp-server /bin/sh

# ログの確認
docker logs -f idp-server
```

### リソース制限

```bash
# CPUとメモリの制限
docker run -d \
  --name idp-server \
  --cpus="2.0" \
  --memory="4g" \
  --memory-swap="4g" \
  my-idp-server:latest

# 制限の確認
docker stats idp-server

# 出力例
CONTAINER ID   NAME         CPU %   MEM USAGE / LIMIT   MEM %
a1b2c3d4e5f6   idp-server   25.5%   2.1GiB / 4GiB       52.50%
```

---

## IDサービスでの活用

### 開発環境の標準化

```yaml
# docker-compose.yml（開発環境）
version: '3.8'
services:
  idp-server:
    build: .
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=local
      - DATABASE_URL=jdbc:postgresql://db:5432/idp
    depends_on:
      - db
      - redis

  db:
    image: postgres:16
    environment:
      POSTGRES_DB: idp
      POSTGRES_USER: idp
      POSTGRES_PASSWORD: secret
    volumes:
      - postgres-data:/var/lib/postgresql/data

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

volumes:
  postgres-data:
```

### 本番デプロイパターン

```
┌─────────────────────────────────────────────────────────────┐
│                    Kubernetes Cluster                        │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                    Deployment                        │   │
│  │  ┌───────────────┐ ┌───────────────┐ ┌───────────────┐│   │
│  │  │   Pod         │ │   Pod         │ │   Pod         ││   │
│  │  │ ┌───────────┐ │ │ ┌───────────┐ │ │ ┌───────────┐ ││   │
│  │  │ │idp-server │ │ │ │idp-server │ │ │ │idp-server │ ││   │
│  │  │ │ :8080     │ │ │ │ :8080     │ │ │ │ :8080     │ ││   │
│  │  │ └───────────┘ │ │ └───────────┘ │ │ └───────────┘ ││   │
│  │  └───────────────┘ └───────────────┘ └───────────────┘│   │
│  └─────────────────────────────────────────────────────┘   │
│                            ▲                                │
│                            │                                │
│  ┌─────────────────────────┴───────────────────────────┐   │
│  │                      Service                         │   │
│  │              (LoadBalancer / Ingress)                │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### セキュリティ考慮事項

```dockerfile
# セキュアなDockerfile例
FROM eclipse-temurin:21-jre-alpine

# 非rootユーザーの作成
RUN addgroup -g 1000 appgroup && \
    adduser -u 1000 -G appgroup -D appuser

# アプリケーションのコピー
WORKDIR /app
COPY --chown=appuser:appgroup target/*.jar app.jar

# 非rootユーザーで実行
USER appuser

# ヘルスチェック
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s \
  CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

# 実行
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

## まとめ

### コンテナの主なメリット

| メリット | 説明 |
|---------|------|
| 環境の一貫性 | 開発・テスト・本番で同一イメージ |
| 高速なデプロイ | 秒単位での起動・停止 |
| リソース効率 | VMより軽量で高密度 |
| スケーラビリティ | 水平スケーリングが容易 |
| 分離 | アプリケーション間の独立性 |

### 次のステップ

- [Dockerfile ベストプラクティス](dockerfile-best-practices.md) - 効率的なイメージ作成
- [Docker Compose](docker-compose.md) - 複数コンテナの管理
- [Dockerコマンドリファレンス](docker-commands.md) - よく使うコマンド集

---

## 参考リソース

- [Docker Documentation](https://docs.docker.com/)
- [Docker Hub](https://hub.docker.com/)
- [OCI (Open Container Initiative)](https://opencontainers.org/)
- [CNCF (Cloud Native Computing Foundation)](https://www.cncf.io/)
