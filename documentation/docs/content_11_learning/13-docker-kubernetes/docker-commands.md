# Docker コマンドリファレンス

日常的に使用するDockerコマンドのクイックリファレンスです。

---

## 目次

1. [コンテナ操作](#コンテナ操作)
2. [イメージ操作](#イメージ操作)
3. [ボリューム操作](#ボリューム操作)
4. [ネットワーク操作](#ネットワーク操作)
5. [Docker Compose](#docker-compose)
6. [ログとデバッグ](#ログとデバッグ)
7. [クリーンアップ](#クリーンアップ)
8. [便利なワンライナー](#便利なワンライナー)

---

## コンテナ操作

### 基本操作

```bash
# コンテナ一覧（実行中）
docker ps

# コンテナ一覧（全て）
docker ps -a

# コンテナの作成と起動
docker run -d --name my-app -p 8080:8080 my-image:latest

# コンテナの停止
docker stop my-app

# コンテナの開始
docker start my-app

# コンテナの再起動
docker restart my-app

# コンテナの削除
docker rm my-app

# 強制削除（実行中でも）
docker rm -f my-app
```

### docker run オプション

```bash
# 基本形
docker run [オプション] イメージ名 [コマンド]

# よく使うオプション
docker run -d \                        # デタッチモード（バックグラウンド）
  --name my-app \                      # コンテナ名
  -p 8080:8080 \                       # ポートマッピング
  -e SPRING_PROFILES_ACTIVE=docker \   # 環境変数
  -v ./data:/app/data \                # ボリュームマウント
  --network my-network \               # ネットワーク指定
  --restart unless-stopped \           # 再起動ポリシー
  --memory 2g \                        # メモリ制限
  --cpus 1.5 \                         # CPU制限
  my-image:latest

# インタラクティブモード（シェル接続）
docker run -it --rm alpine:latest /bin/sh

# 一時的なコンテナ（終了後自動削除）
docker run --rm my-image:latest
```

### コンテナ内操作

```bash
# 実行中コンテナでコマンド実行
docker exec my-app ls -la

# コンテナ内でシェルを起動
docker exec -it my-app /bin/sh
docker exec -it my-app /bin/bash

# 環境変数を指定して実行
docker exec -e MY_VAR=value my-app printenv

# rootユーザーで実行
docker exec -u root my-app whoami

# 作業ディレクトリを指定
docker exec -w /app my-app pwd
```

### コンテナ情報

```bash
# コンテナの詳細情報
docker inspect my-app

# 特定の情報を抽出（IPアドレス）
docker inspect -f '{{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}' my-app

# コンテナのリソース使用状況
docker stats
docker stats my-app

# コンテナのプロセス一覧
docker top my-app

# コンテナの変更差分
docker diff my-app
```

---

## イメージ操作

### 基本操作

```bash
# イメージ一覧
docker images

# イメージの取得
docker pull eclipse-temurin:21-jre-alpine

# イメージの削除
docker rmi my-image:latest

# 未使用イメージの削除
docker image prune

# 全未使用イメージの削除
docker image prune -a
```

### イメージビルド

```bash
# 基本ビルド
docker build -t my-app:latest .

# Dockerfile指定
docker build -f Dockerfile.prod -t my-app:prod .

# ビルド引数
docker build --build-arg VERSION=1.0.0 -t my-app:1.0.0 .

# キャッシュなしでビルド
docker build --no-cache -t my-app:latest .

# 特定ステージまでビルド
docker build --target builder -t my-app:builder .

# マルチプラットフォームビルド
docker buildx build --platform linux/amd64,linux/arm64 -t my-app:latest .
```

### イメージ管理

```bash
# イメージのタグ付け
docker tag my-app:latest ghcr.io/myorg/my-app:v1.0.0

# イメージのプッシュ
docker push ghcr.io/myorg/my-app:v1.0.0

# イメージの保存（tarファイル）
docker save -o my-app.tar my-app:latest

# イメージの読み込み
docker load -i my-app.tar

# イメージの履歴
docker history my-app:latest

# イメージの詳細
docker inspect my-app:latest
```

---

## ボリューム操作

```bash
# ボリューム一覧
docker volume ls

# ボリューム作成
docker volume create my-data

# ボリューム詳細
docker volume inspect my-data

# ボリューム削除
docker volume rm my-data

# 未使用ボリューム削除
docker volume prune

# ボリュームをマウントして起動
docker run -v my-data:/app/data my-app:latest

# バインドマウント（ホストパス）
docker run -v $(pwd)/data:/app/data my-app:latest

# 読み取り専用マウント
docker run -v $(pwd)/config:/app/config:ro my-app:latest
```

---

## ネットワーク操作

```bash
# ネットワーク一覧
docker network ls

# ネットワーク作成
docker network create my-network

# ブリッジネットワーク作成（サブネット指定）
docker network create --driver bridge --subnet 172.20.0.0/16 my-network

# ネットワーク詳細
docker network inspect my-network

# ネットワーク削除
docker network rm my-network

# コンテナをネットワークに接続
docker network connect my-network my-app

# コンテナをネットワークから切断
docker network disconnect my-network my-app

# ネットワークを指定して起動
docker run --network my-network my-app:latest
```

---

## Docker Compose

### 基本操作

```bash
# 起動（バックグラウンド）
docker compose up -d

# 停止と削除
docker compose down

# ボリュームも削除
docker compose down -v

# ビルドして起動
docker compose up -d --build

# 特定サービスのみ起動
docker compose up -d db redis

# 状態確認
docker compose ps

# ログ確認
docker compose logs
docker compose logs -f my-app
docker compose logs --tail=100 my-app
```

### スケーリングと更新

```bash
# サービスのスケール
docker compose up -d --scale app=3

# サービスの再起動
docker compose restart my-app

# サービスの停止（削除せず）
docker compose stop my-app

# サービスの開始
docker compose start my-app

# 設定の再読み込み（rolling update）
docker compose up -d --no-deps my-app
```

### 実行とデバッグ

```bash
# サービス内でコマンド実行
docker compose exec my-app /bin/sh

# ワンショットコマンド
docker compose run --rm my-app npm test

# サービスのビルドのみ
docker compose build
docker compose build --no-cache my-app

# 設定の検証
docker compose config

# 環境変数の確認
docker compose config | grep -A5 environment
```

### 複数Composeファイル

```bash
# 複数ファイルの指定
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d

# プロファイル指定
docker compose --profile dev up -d

# 環境ファイル指定
docker compose --env-file .env.prod up -d
```

---

## ログとデバッグ

### ログ確認

```bash
# コンテナログ
docker logs my-app

# フォロー（リアルタイム）
docker logs -f my-app

# 末尾N行
docker logs --tail 100 my-app

# タイムスタンプ付き
docker logs -t my-app

# 時間範囲指定
docker logs --since 1h my-app
docker logs --since 2024-01-01T00:00:00 my-app
docker logs --until 10m my-app
```

### デバッグ

```bash
# コンテナの詳細情報
docker inspect my-app

# ヘルスチェック状態
docker inspect --format='{{.State.Health.Status}}' my-app

# コンテナのイベント
docker events --filter container=my-app

# システム情報
docker system info

# ディスク使用量
docker system df
docker system df -v
```

---

## クリーンアップ

### 個別クリーンアップ

```bash
# 停止中コンテナの削除
docker container prune

# 未使用イメージの削除
docker image prune

# 未タグイメージ（dangling）の削除
docker image prune

# 全未使用イメージの削除
docker image prune -a

# 未使用ボリュームの削除
docker volume prune

# 未使用ネットワークの削除
docker network prune
```

### 一括クリーンアップ

```bash
# 全未使用リソースの削除
docker system prune

# ボリュームも含めて削除
docker system prune -a --volumes

# 確認なしで実行
docker system prune -af --volumes
```

### 選択的削除

```bash
# 特定イメージを持つコンテナを削除
docker rm $(docker ps -aq --filter ancestor=my-image:latest)

# 1週間以上前のコンテナを削除
docker container prune --filter "until=168h"

# 特定ラベルのイメージを削除
docker image prune --filter "label=stage=build"
```

---

## 便利なワンライナー

### コンテナ操作

```bash
# 全コンテナ停止
docker stop $(docker ps -q)

# 全コンテナ削除
docker rm $(docker ps -aq)

# 全コンテナ強制停止・削除
docker rm -f $(docker ps -aq)

# 終了コードが0以外のコンテナを表示
docker ps -a --filter "exited!=0"

# コンテナのIPアドレス一覧
docker ps -q | xargs -I {} docker inspect -f '{{.Name}}: {{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}' {}
```

### イメージ操作

```bash
# 全イメージ削除
docker rmi $(docker images -q)

# danglingイメージ削除
docker rmi $(docker images -f "dangling=true" -q)

# 特定タグのイメージを削除
docker rmi $(docker images | grep 'my-app' | awk '{print $3}')

# イメージサイズでソート
docker images --format "{{.Size}}\t{{.Repository}}:{{.Tag}}" | sort -h
```

### リソース監視

```bash
# 全コンテナのリソース使用状況
docker stats --no-stream

# 特定コンテナのメモリ使用量
docker stats --no-stream --format "{{.Name}}: {{.MemUsage}}"

# コンテナごとのログサイズ
docker ps -q | xargs -I {} sh -c 'echo $(docker inspect --format="{{.LogPath}}" {}) $(du -h $(docker inspect --format="{{.LogPath}}" {}) 2>/dev/null | cut -f1)'
```

### トラブルシューティング

```bash
# コンテナが起動しない原因を調査
docker logs my-app --tail 50
docker inspect my-app | grep -A 10 "State"

# ネットワーク接続確認
docker exec my-app ping -c 3 db
docker exec my-app wget -qO- http://other-service:8080/health

# ファイルシステムの確認
docker exec my-app df -h
docker exec my-app ls -la /app

# 環境変数の確認
docker exec my-app printenv | sort
```

---

## クイックリファレンス表

### コンテナ状態

| コマンド | 説明 |
|---------|------|
| `docker ps` | 実行中コンテナ一覧 |
| `docker ps -a` | 全コンテナ一覧 |
| `docker stats` | リソース使用状況 |
| `docker logs -f [name]` | ログをフォロー |
| `docker inspect [name]` | 詳細情報 |

### ライフサイクル

| コマンド | 説明 |
|---------|------|
| `docker run -d [image]` | 起動 |
| `docker stop [name]` | 停止 |
| `docker start [name]` | 開始 |
| `docker restart [name]` | 再起動 |
| `docker rm [name]` | 削除 |

### イメージ

| コマンド | 説明 |
|---------|------|
| `docker images` | イメージ一覧 |
| `docker pull [image]` | 取得 |
| `docker build -t [tag] .` | ビルド |
| `docker push [image]` | プッシュ |
| `docker rmi [image]` | 削除 |

### Docker Compose

| コマンド | 説明 |
|---------|------|
| `docker compose up -d` | 起動 |
| `docker compose down` | 停止・削除 |
| `docker compose ps` | 状態確認 |
| `docker compose logs -f` | ログ確認 |
| `docker compose exec [svc] sh` | シェル接続 |

---

## 参考リソース

- [Docker CLI reference](https://docs.docker.com/engine/reference/commandline/cli/)
- [Docker Compose CLI reference](https://docs.docker.com/compose/reference/)
- [Docker run reference](https://docs.docker.com/engine/reference/run/)
