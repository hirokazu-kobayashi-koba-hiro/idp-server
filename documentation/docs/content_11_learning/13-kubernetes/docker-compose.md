# Docker Compose

複数のコンテナを定義・管理するDocker Composeの使い方を学びます。開発環境からテスト環境まで、複数サービスの構成を効率的に管理できます。

---

## 目次

1. [Docker Composeとは](#docker-composeとは)
2. [基本構文](#基本構文)
3. [サービス定義](#サービス定義)
4. [ネットワーク](#ネットワーク)
5. [ボリューム](#ボリューム)
6. [環境変数](#環境変数)
7. [依存関係とヘルスチェック](#依存関係とヘルスチェック)
8. [プロファイル](#プロファイル)
9. [IDサービスの構成例](#idサービスの構成例)

---

## Docker Composeとは

### 概要

Docker Composeは、複数のコンテナを1つのYAMLファイルで定義し、一括で管理するツールです。

```
従来（個別にコンテナ起動）:
docker network create mynet
docker run -d --name db --network mynet postgres:16
docker run -d --name redis --network mynet redis:7
docker run -d --name app --network mynet -p 8080:8080 my-app

Docker Compose（1コマンドで起動）:
docker compose up -d
```

### バージョン

```yaml
# Docker Compose V2（推奨）
# バージョン指定は不要（最新仕様が自動適用）
services:
  app:
    image: my-app:latest
```

---

## 基本構文

### 最小構成

```yaml
# docker-compose.yml
services:
  app:
    image: eclipse-temurin:21-jre-alpine
    ports:
      - "8080:8080"
```

### 基本コマンド

```bash
# コンテナの起動
docker compose up -d

# コンテナの停止
docker compose down

# ログの確認
docker compose logs -f

# コンテナの状態確認
docker compose ps

# 特定サービスの再起動
docker compose restart app

# ビルドして起動
docker compose up -d --build
```

---

## サービス定義

### 完全なサービス定義例

```yaml
services:
  idp-server:
    # イメージ指定（ビルドしない場合）
    image: my-idp-server:latest

    # またはビルド設定
    build:
      context: .
      dockerfile: Dockerfile
      args:
        - JAVA_VERSION=21

    # コンテナ名
    container_name: idp-server

    # ホスト名
    hostname: idp-server

    # ポートマッピング
    ports:
      - "8080:8080"       # ホスト:コンテナ
      - "127.0.0.1:9090:9090"  # 特定IPにバインド

    # 環境変数
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - DATABASE_URL=jdbc:postgresql://db:5432/idp

    # 環境変数ファイル
    env_file:
      - .env
      - .env.local

    # ボリューム
    volumes:
      - ./logs:/app/logs
      - app-data:/app/data

    # ネットワーク
    networks:
      - backend
      - frontend

    # リソース制限
    deploy:
      resources:
        limits:
          cpus: '2.0'
          memory: 4G
        reservations:
          cpus: '0.5'
          memory: 1G

    # 再起動ポリシー
    restart: unless-stopped

    # ヘルスチェック
    healthcheck:
      test: ["CMD", "wget", "-q", "--spider", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s

    # 依存関係
    depends_on:
      db:
        condition: service_healthy
      redis:
        condition: service_started
```

### ビルド設定の詳細

```yaml
services:
  app:
    build:
      # ビルドコンテキスト
      context: ./app

      # Dockerfile指定
      dockerfile: Dockerfile.prod

      # ビルド引数
      args:
        - APP_VERSION=${APP_VERSION:-1.0.0}
        - BUILD_DATE=${BUILD_DATE}

      # ターゲットステージ（マルチステージビルド）
      target: production

      # キャッシュ設定
      cache_from:
        - my-app:latest

      # ラベル
      labels:
        - "com.example.version=1.0.0"

    # イメージ名とタグ
    image: my-app:${TAG:-latest}
```

---

## ネットワーク

### ネットワーク定義

```yaml
services:
  app:
    networks:
      - frontend
      - backend

  db:
    networks:
      - backend

  nginx:
    networks:
      - frontend

networks:
  frontend:
    driver: bridge

  backend:
    driver: bridge
    # 内部ネットワーク（外部からアクセス不可）
    internal: true
```

### ネットワーク構成図

```
┌─────────────────────────────────────────────────────────────┐
│                        Docker Host                           │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │                  frontend network                     │   │
│  │  ┌─────────┐         ┌─────────┐                     │   │
│  │  │  nginx  │ ◀─────▶ │   app   │                     │   │
│  │  └────┬────┘         └────┬────┘                     │   │
│  └───────│───────────────────│──────────────────────────┘   │
│          │                   │                               │
│          ▼                   │                               │
│     :80, :443                │                               │
│                              │                               │
│  ┌───────────────────────────│──────────────────────────┐   │
│  │                  backend network (internal)           │   │
│  │                   ┌───────┴───────┐                   │   │
│  │                   │      app      │                   │   │
│  │                   └───────┬───────┘                   │   │
│  │           ┌───────────────┼───────────────┐          │   │
│  │           ▼               ▼               ▼          │   │
│  │     ┌─────────┐     ┌─────────┐     ┌─────────┐     │   │
│  │     │   db    │     │  redis  │     │  kafka  │     │   │
│  │     └─────────┘     └─────────┘     └─────────┘     │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### サービスディスカバリ

```yaml
services:
  app:
    environment:
      # サービス名でアクセス可能
      - DATABASE_HOST=db
      - REDIS_HOST=redis

  db:
    # app からは "db" でアクセス可能
    image: postgres:16
```

---

## ボリューム

### ボリュームの種類

```yaml
services:
  db:
    volumes:
      # 名前付きボリューム（データ永続化）
      - postgres-data:/var/lib/postgresql/data

      # バインドマウント（ホストのパスをマウント）
      - ./init-scripts:/docker-entrypoint-initdb.d:ro

      # 匿名ボリューム
      - /var/log/postgresql

volumes:
  postgres-data:
    # ドライバー指定
    driver: local

  # 外部で作成済みのボリュームを使用
  existing-volume:
    external: true
```

### ボリュームの使い分け

```
┌─────────────────────────────────────────────────────────────┐
│                      ボリュームの種類                         │
├─────────────────────────────────────────────────────────────┤
│  名前付きボリューム (Named Volume)                            │
│  - データベースのデータ                                       │
│  - アップロードファイル                                       │
│  - Dockerが管理、ホストから独立                               │
│                                                              │
│  バインドマウント (Bind Mount)                                │
│  - 開発時のソースコード                                       │
│  - 設定ファイル                                              │
│  - ログ出力                                                  │
│  - ホストのパスを直接マウント                                  │
│                                                              │
│  tmpfs マウント                                              │
│  - 一時的なキャッシュ                                         │
│  - 機密情報（メモリ上のみ）                                    │
│  - コンテナ停止で消える                                       │
└─────────────────────────────────────────────────────────────┘
```

---

## 環境変数

### 設定方法

```yaml
services:
  app:
    # 直接指定
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - DATABASE_URL=jdbc:postgresql://db:5432/idp
      # 変数展開
      - APP_VERSION=${APP_VERSION:-1.0.0}

    # ファイルから読み込み
    env_file:
      - .env
      - .env.${ENVIRONMENT:-local}
```

### .envファイル

```bash
# .env
POSTGRES_USER=idp
POSTGRES_PASSWORD=secret
POSTGRES_DB=idp_db

# アプリケーション設定
APP_VERSION=1.0.0
SPRING_PROFILES_ACTIVE=docker
```

### 変数の優先順位

```
1. Compose ファイルの environment
2. シェル環境変数
3. .env ファイル
4. Dockerfile の ENV

例:
export APP_VERSION=2.0.0        # シェル環境変数
# .env: APP_VERSION=1.0.0       # .envファイル

→ APP_VERSION=2.0.0 が使用される
```

---

## 依存関係とヘルスチェック

### 起動順序の制御

```yaml
services:
  app:
    depends_on:
      db:
        condition: service_healthy  # ヘルスチェック完了まで待機
      redis:
        condition: service_started  # 起動開始まで待機
      kafka:
        condition: service_healthy

  db:
    image: postgres:16
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s

  redis:
    image: redis:7-alpine
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 3

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    healthcheck:
      test: ["CMD-SHELL", "kafka-broker-api-versions --bootstrap-server localhost:9092"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 60s
```

### 起動フロー

```
┌─────────────────────────────────────────────────────────────┐
│                      起動順序                                │
│                                                              │
│  1. 依存関係のないサービスを並列起動                           │
│     ┌──────┐  ┌──────┐  ┌──────┐                            │
│     │  db  │  │redis │  │kafka │                            │
│     └──┬───┘  └──┬───┘  └──┬───┘                            │
│        │         │         │                                 │
│        ▼         ▼         ▼                                 │
│   healthcheck  started  healthcheck                          │
│        │         │         │                                 │
│        └────┬────┴────┬────┘                                 │
│             │         │                                      │
│  2. 依存サービスのヘルスチェック完了を待機                     │
│             │         │                                      │
│             ▼         ▼                                      │
│         ┌───────────────┐                                    │
│         │      app      │                                    │
│         └───────────────┘                                    │
└─────────────────────────────────────────────────────────────┘
```

---

## プロファイル

### プロファイルの定義

```yaml
services:
  app:
    image: my-app:latest
    ports:
      - "8080:8080"

  db:
    image: postgres:16
    profiles:
      - dev
      - test

  # デバッグツール（devプロファイルのみ）
  adminer:
    image: adminer
    ports:
      - "8081:8080"
    profiles:
      - dev

  # 負荷テストツール（testプロファイルのみ）
  k6:
    image: grafana/k6
    profiles:
      - test
```

### プロファイルの使用

```bash
# デフォルト（プロファイルなしのサービスのみ）
docker compose up -d

# devプロファイルを有効化
docker compose --profile dev up -d

# 複数プロファイルを有効化
docker compose --profile dev --profile test up -d

# 環境変数で指定
COMPOSE_PROFILES=dev,test docker compose up -d
```

---

## IDサービスの構成例

### 開発環境（docker-compose.yml）

```yaml
services:
  # ===========================================
  # IDサーバー
  # ===========================================
  idp-server:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: idp-server
    ports:
      - "8080:8080"
      - "5005:5005"  # デバッグポート
    environment:
      - SPRING_PROFILES_ACTIVE=local
      - DATABASE_URL=jdbc:postgresql://db:5432/idp
      - REDIS_HOST=redis
      - JAVA_TOOL_OPTIONS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
    volumes:
      - ./logs:/app/logs
    networks:
      - idp-network
    depends_on:
      db:
        condition: service_healthy
      redis:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "-q", "--spider", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s

  # ===========================================
  # PostgreSQL
  # ===========================================
  db:
    image: postgres:16-alpine
    container_name: idp-db
    environment:
      POSTGRES_DB: idp
      POSTGRES_USER: idp
      POSTGRES_PASSWORD: ${DB_PASSWORD:-secret}
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data
      - ./docker/postgres/init:/docker-entrypoint-initdb.d:ro
    networks:
      - idp-network
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U idp -d idp"]
      interval: 10s
      timeout: 5s
      retries: 5

  # ===========================================
  # Redis（セッション・キャッシュ）
  # ===========================================
  redis:
    image: redis:7-alpine
    container_name: idp-redis
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    networks:
      - idp-network
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 3
    command: redis-server --appendonly yes

  # ===========================================
  # 開発ツール（devプロファイル）
  # ===========================================
  adminer:
    image: adminer
    container_name: idp-adminer
    ports:
      - "8081:8080"
    networks:
      - idp-network
    profiles:
      - dev

  mailhog:
    image: mailhog/mailhog
    container_name: idp-mailhog
    ports:
      - "1025:1025"  # SMTP
      - "8025:8025"  # Web UI
    networks:
      - idp-network
    profiles:
      - dev

networks:
  idp-network:
    driver: bridge

volumes:
  postgres-data:
  redis-data:
```

### 本番環境（docker-compose.prod.yml）

```yaml
services:
  idp-server:
    image: ${REGISTRY}/idp-server:${TAG:-latest}
    deploy:
      replicas: 3
      resources:
        limits:
          cpus: '2.0'
          memory: 4G
        reservations:
          cpus: '1.0'
          memory: 2G
      restart_policy:
        condition: on-failure
        delay: 5s
        max_attempts: 3
    environment:
      - SPRING_PROFILES_ACTIVE=production
      - DATABASE_URL=${DATABASE_URL}
      - REDIS_HOST=${REDIS_HOST}
    logging:
      driver: json-file
      options:
        max-size: "100m"
        max-file: "5"
    healthcheck:
      test: ["CMD", "wget", "-q", "--spider", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 90s
```

### 起動スクリプト

```bash
#!/bin/bash
# scripts/docker-up.sh

ENV=${1:-dev}

case $ENV in
  dev)
    docker compose --profile dev up -d
    ;;
  test)
    docker compose --profile test up -d
    ;;
  prod)
    docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
    ;;
  *)
    echo "Usage: $0 {dev|test|prod}"
    exit 1
    ;;
esac
```

---

## まとめ

### よく使うコマンド

| コマンド | 説明 |
|---------|------|
| `docker compose up -d` | 起動（デタッチモード） |
| `docker compose down` | 停止・削除 |
| `docker compose down -v` | ボリュームも削除 |
| `docker compose logs -f [service]` | ログ確認 |
| `docker compose ps` | 状態確認 |
| `docker compose exec [service] sh` | コンテナに入る |
| `docker compose build` | イメージビルド |
| `docker compose pull` | イメージ取得 |

### 次のステップ

- [Dockerコマンドリファレンス](docker-commands.md) - よく使うコマンド集
- [Kubernetesアーキテクチャ](kubernetes-architecture.md) - 本番環境のオーケストレーション

---

## 参考リソース

- [Docker Compose specification](https://docs.docker.com/compose/compose-file/)
- [Compose file version 3 reference](https://docs.docker.com/compose/compose-file/compose-file-v3/)
- [Overview of docker compose CLI](https://docs.docker.com/compose/reference/)
