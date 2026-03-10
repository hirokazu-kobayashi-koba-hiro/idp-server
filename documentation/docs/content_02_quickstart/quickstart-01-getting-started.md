# Getting-Started

このガイドでは、**idp-server** を初めてセットアップして実行する手順を説明します。

## 前提条件

| ツール | 必須バージョン | 推奨 | 備考 |
|-------|-------------|------|------|
| **Java** | 21+ | Java 21 | |
| **データベース** | PostgreSQL 14+ または MySQL 8.0+ | **PostgreSQL 14+** | Primary/Replica 構成対応 |
| **Node.js** | 18.0+ | 20.x LTS | E2E テスト実行に必要 |
| **Docker** | 20.10+ | Docker Desktop 最新版 | Compose V2 対応 |

### データベース選択ガイド
- **PostgreSQL**: ✅ 推奨（Primary/Replica 対応、本番環境向け）
- **MySQL**: ⚠️ 基本機能のみ（開発・検証環境向け）

## ローカル環境構成

この図は、docker-compose を使って構築される idp-server のローカル開発環境の全体構成を示しています。

```mermaid
flowchart TD
subgraph Frontend["Frontend (*.local.test)"]
app_view[🖥️ app-view<br>auth.local.test<br>認可UI]
sample_web[🌐 sample-web<br>sample.local.test<br>サンプルRP]
end

subgraph Nginx["Nginx Reverse Proxy"]
nginx[🔀 nginx<br>api.local.test:443<br>mtls.api.local.test:443]
end

subgraph App["App Cluster"]
idp1[🔥 idp-server-1<br>:8081]
idp2[🔥 idp-server-2<br>:8082]
end

subgraph Data["Data Layer"]
subgraph PostgreSQL["PostgreSQL Cluster"]
pg_primary[🧠 Primary<br>:5432]
pg_replica[📖 Replica<br>:5433]
pg_primary -.->|WAL| pg_replica
end
redis[⚡ Redis<br>:6379]
end

subgraph External["External Services"]
mockoon[🧪 Mockoon<br>:3010]
end

app_view --> nginx
sample_web --> nginx
nginx --> idp1
nginx --> idp2

idp1 --> pg_primary
idp1 -.->|Read| pg_replica
idp1 --> redis
idp1 --> mockoon

idp2 --> pg_primary
idp2 -.->|Read| pg_replica
idp2 --> redis
idp2 --> mockoon
```

### サブドメイン構成

| サブドメイン | サービス | 説明 |
|------------|---------|------|
| `api.local.test` | nginx → idp-server | IDP Server API エンドポイント |
| `mtls.api.local.test` | nginx → idp-server | mTLSエンドポイント（クライアント証明書検証、sender-constrainedトークン） |
| `auth.local.test` | app-view | 認可UI（ログイン画面等） |
| `sample.local.test` | sample-web | サンプルRPアプリケーション |

### 各コンポーネントの役割

| コンポーネント | 説明 |
|---------------|------|
| 🔀 **nginx** | リバースプロキシ。`api.local.test` / `mtls.api.local.test` へのリクエストを idp-server クラスタにルーティング。mTLSドメインではクライアント証明書を検証・転送 |
| 🔥 **idp-server-1/2** | idp-server 本体。クラスタ構成でスケーラビリティ・冗長性を確認（ポート 8081/8082） |
| 🖥️ **app-view** | Next.js製の認可UI。ログイン・同意画面などを提供 |
| 🌐 **sample-web** | サンプルRPアプリ。OIDC連携のデモ・テスト用 |
| 🧠 **PostgreSQL Primary** | メインDB（プライマリ）。書き込み・読み込み操作を処理（ポート: 5432） |
| 📖 **PostgreSQL Replica** | 読み取り専用レプリカDB。ストリーミングレプリケーションで同期（ポート: 5433） |
| ⚡ **Redis** | セッション情報・キャッシュストア（ポート: 6379） |
| 🧪 **Mockoon** | 外部サービス連携を模擬するモックサーバー（eKYC/通知サービス等） |

### 特徴
- **サブドメイン構成**: 本番環境に近い `*.local.test` サブドメイン構成
- **複数台構成（HAテスト可）**: 2台の idp-server をクラスタで起動し、nginx 経由でルーティング
- **PostgreSQL Primary/Replica**: ストリーミングレプリケーションによる読み書き分離
- **Redis セッション/キャッシュ**: 高速なセッション管理とキャッシュ
- **フロントエンド統合**: 認可UI（app-view）とサンプルRP（sample-web）を含む
- **モック環境完備**: Mockoon による外部連携模擬でE2E試験も可能

> **Note**: MySQL を使用する場合は `docker-compose-mysql.yaml` を参照してください。

## インストール手順

### 1. リポジトリのクローン

```bash
git clone https://github.com/hirokazu-kobayashi-koba-hiro/idp-server.git
cd idp-server
```

### 2. サブドメイン設定（初回のみ）

ローカル開発環境で `*.local.test` サブドメインを使用するため、以下のスクリプトを実行します：

```shell
./scripts/setup-local-subdomain.sh
```

このスクリプトは以下を設定します：
- dnsmasq による `*.local.test` のローカルDNS解決
- mkcert によるローカルSSL証明書の生成

> **Note**: macOS を前提としています。他のOSでは手動設定が必要です（下記参照）。

#### 設定の確認

スクリプト実行後、以下のコマンドでDNS解決が正常に動作しているか確認します：

```shell
# DNS解決の確認
ping -c 1 api.local.test
ping -c 1 auth.local.test

# または nslookup で確認
nslookup api.local.test
```

`127.0.0.1` が返されれば正常です。

#### 手動設定（他のOS / dnsmasqが使えない場合）

dnsmasqを使用できない環境では、`/etc/hosts` に直接追記することで代替できます：

```shell
# Linux / macOS
sudo sh -c 'echo "127.0.0.1 api.local.test mtls.api.local.test auth.local.test sample.local.test" >> /etc/hosts'

# Windows (管理者権限のPowerShell)
Add-Content -Path C:\Windows\System32\drivers\etc\hosts -Value "127.0.0.1 api.local.test mtls.api.local.test auth.local.test sample.local.test"
```

#### トラブルシューティング

| 問題 | 原因 | 解決策 |
|------|------|--------|
| `ping api.local.test` が失敗 | dnsmasq が動作していない | `brew services restart dnsmasq` を実行、または `/etc/hosts` で手動設定 |
| DNS解決が `127.0.0.1` 以外を返す | 他のDNSリゾルバが優先されている | `/etc/resolver/local.test` ファイルが存在するか確認 |
| SSL証明書エラー | mkcert のルートCAが未インストール | `mkcert -install` を実行 |

### 3. 環境変数の設定

#### シンプルセットアップ（固定ID使用・推奨）

`config/examples/` 配下のサンプル設定と互換性のある固定IDを使用する場合：

```shell
# サンプル環境変数ファイルをコピー
cp ./.env.example .env

# 必要な値を設定（以下は例）
# IDP_SERVER_API_KEY, IDP_SERVER_API_SECRET, ENCRYPTION_KEY,
# POSTGRES_PASSWORD, DB_OWNER_PASSWORD, IDP_DB_ADMIN_PASSWORD, IDP_DB_APP_PASSWORD
# などを設定してください
```

#### カスタムセットアップ（新しいIDを生成）

新しいランダムIDを生成する場合：

```shell
./init-generate-env.sh postgresql
```

このスクリプトは以下を生成します：
- `.env` ファイル（全ての環境変数）
- `config/secrets/local/` 配下のシークレットファイル

> **注意**: 新しいIDを生成すると、`config/examples/` 配下のサンプル設定との互換性が失われます。

### 4. Docker起動

```shell
# 全サービスをビルド
docker compose build

# 全サービスを起動（データベース初期化も自動実行）
docker compose up -d
```

セットアップには以下が含まれます：
- PostgreSQL Primary/Replicaレプリケーション構成
- データベースマイグレーション実行
- 全サービスのヘルスチェック

### 5. セットアップ確認

サービスの健全性をチェック：

```shell
curl -v https://api.local.test/actuator/health
```

PostgreSQLレプリケーションの確認：

```shell
./scripts/verify-replication.sh
```

このスクリプトは以下のテストを実行します：
- プライマリとレプリカの状態確認
- レプリケーションスロットの確認
- データ同期テスト（プライマリに書き込み、レプリカから読み取り）
- レプリカへの書き込み制限確認
- 接続テスト（ポート 5432: プライマリ、ポート 5433: レプリカ）

### ステップバイステップセットアップ（デバッグ用）

トラブルシューティングが必要な場合は、サービスを個別に開始：

```shell
# 1. まずデータベースを起動
docker compose up -d postgres-primary postgres-replica redis

# 2. データベースマイグレーション実行
docker compose up flyway-migrator

# 3. アプリケーションサービスを起動
docker compose up -d idp-server-1 idp-server-2 nginx app-view sample-web
```

### 6. 設定の適用

#### admin-tenant の初期化

admin-tenant の設定ファイルを生成：

```shell
./init-admin-tenant-config.sh
```

admin-tenant を初期化：

```shell
./setup.sh
```

#### E2Eテスト用データの設定

E2Eテストを実行するためのテストデータを設定：

```shell
./config/scripts/e2e-test-data.sh
```

### 7. エンドツーエンドテスト（E2E）

設定の適用が完了したら、E2Eテストを実行してIdPサーバーが正常に動作しているかを確認できます。

#### テスト構成

テストスイートは3つのカテゴリーに分かれています：

| カテゴリー | 説明 |
|-----------|------|
| 📘 **scenario/** | 現実的なユーザーとシステムの動作（ユーザー登録、SSOログイン、CIBAフロー、MFA登録など） |
| 📕 **spec/** | OpenID Connect、FAPI、JARM、Verifiable Credentialsに基づく仕様準拠テスト |
| 🐒 **monkey/** | 障害注入とエッジケースの検証（意図的に無効なシーケンス、パラメータ、プロトコル違反） |

#### 実行

```shell
cd e2e
npm install
npm test
```

約800ケースのテストが実行され、正常に動作することが確認できます。

## ローカル環境のリソースチューニング

ローカル環境で負荷テストやパフォーマンス検証を行う場合、デフォルト設定では十分なスループットが得られないことがあります。
以下の設定を調整することで、ローカル環境でもより高い負荷に対応できます。

### Docker リソース割り当て

`docker-compose.yaml` で各コンテナのリソース制限を設定できます。

```yaml
services:
  idp-server-1:
    deploy:
      resources:
        limits:
          cpus: '2.0'    # CPU制限（コア数）
          memory: 2048M  # メモリ制限
```

**推奨設定（負荷テスト用）**:
- idp-server: CPU 2.0〜3.0コア、メモリ 2GB
- PostgreSQL: CPU 2.0コア、メモリ 2GB
- nginx: CPU 1.0コア、メモリ 512MB

> **Note**: Docker Desktop の設定で十分なリソースが割り当てられていることを確認してください（推奨: 6コア以上、8GB以上のメモリ）。

### PostgreSQL チューニング

`docker-compose.yaml` の PostgreSQL コマンドでパフォーマンスパラメータを調整できます。

```yaml
postgres-primary:
  command:
    - "postgres"
    - "-c"
    - "shared_buffers=1GB"           # 共有バッファ（デフォルト: 128MB）
    - "-c"
    - "work_mem=64MB"                # 作業メモリ（デフォルト: 4MB）
    - "-c"
    - "effective_cache_size=2GB"     # 実効キャッシュサイズ
    - "-c"
    - "max_parallel_workers_per_gather=4"  # 並列ワーカー数
```

| パラメータ | デフォルト | 推奨値 | 説明 |
|-----------|----------|--------|------|
| `shared_buffers` | 128MB | 1GB | PostgreSQLが使用する共有メモリ |
| `work_mem` | 4MB | 64MB | ソート・ハッシュ操作用メモリ |
| `effective_cache_size` | - | 2GB | OSキャッシュ見積もり（プランナー用） |
| `max_parallel_workers_per_gather` | 2 | 4 | 並列クエリワーカー数 |

> **Note**: 設定変更後は `docker compose up -d postgres-primary` でコンテナを再作成する必要があります（`restart` では反映されません）。

### HikariCP（コネクションプール）設定

`application.yaml` で HikariCP のコネクションプール設定を環境変数で調整できます。

```yaml
idp:
  datasource:
    app:
      writer:
        hikari:
          connection-timeout: ${DB_WRITER_TIMEOUT:30000}      # 接続取得タイムアウト（ms）
          maximum-pool-size: ${DB_WRITER_MAX_POOL_SIZE:30}    # 最大プール数
          minimum-idle: ${DB_WRITER_MIN_IDLE:10}              # 最小アイドル接続数
          idle-timeout: ${DB_WRITER_IDLE_TIMEOUT:600000}      # アイドルタイムアウト（ms）
          max-lifetime: ${DB_WRITER_MAX_LIFETIME:1800000}     # 接続最大生存時間（ms）
          keepalive-time: ${DB_WRITER_KEEPALIVE_TIME:180000}  # キープアライブ間隔（ms）
          validation-timeout: ${DB_WRITER_VALIDATION_TIMEOUT:5000}  # 検証タイムアウト（ms）
```

| パラメータ | デフォルト | 説明 |
|-----------|----------|------|
| `connection-timeout` | 30000ms | プールから接続を取得する最大待機時間 |
| `maximum-pool-size` | 30 | 最大コネクション数 |
| `minimum-idle` | 10 | アイドル時に維持する最小コネクション数 |
| `keepalive-time` | 180000ms | DB接続のキープアライブ間隔（Aurora等のtcp_keepalive_idle未満に設定） |
| `validation-timeout` | 5000ms | 接続検証のタイムアウト |

### nginx チューニング

`docker/nginx/nginx.conf` でnginxのパフォーマンスを調整できます。

```nginx
worker_processes auto;

events {
    worker_connections 4096;  # ワーカーあたりの最大接続数
    multi_accept on;          # 複数接続の同時受付
    use epoll;                # イベント駆動モデル（Linux）
}

http {
    sendfile on;
    tcp_nopush on;
    tcp_nodelay on;
    keepalive_timeout 65;
    keepalive_requests 10000;  # キープアライブあたりのリクエスト数

    upstream idp_backend {
        keepalive 100;  # バックエンドへのキープアライブ接続数
        server idp-server-1:8080;
        server idp-server-2:8080;
    }
}
```

### リソースモニタリング

負荷テスト中は `docker stats` でリソース使用状況を監視し、ボトルネックを特定します。

```bash
docker stats --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}"
```

**典型的なボトルネックパターン**:

| 症状 | 原因 | 対策 |
|------|------|------|
| PostgreSQL CPU > 100% | DBがボトルネック | shared_buffers/work_mem増加、インデックス確認 |
| idp-server CPU > 100% | JVM処理限界 | CPU割り当て増加、インスタンス追加 |
| nginx CPU高負荷 | 接続処理限界 | worker_connections増加 |
| メモリ使用率高 | ヒープ不足 | コンテナメモリ制限増加 |

> **Tip**: 詳細なパフォーマンスチューニングガイドは [パフォーマンスチューニング](../content_11_learning/26-performance-tuning/) を参照してください。

## 次のステップ

クイックスタートはこれで完了です。次のステップとして以下を参照してください：

- [コンセプト](../content_03_concepts/) - idp-server の設計思想と主要概念
- [チュートリアル](../content_04_tutorials/) - 具体的なユースケース別の実装ガイド
- [開発者ガイド](../content_06_developer-guide/) - 詳細な実装リファレンス
