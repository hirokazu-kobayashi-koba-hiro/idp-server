# PostgreSQL インストールと初期設定ガイド

このドキュメントでは、PostgreSQLのインストールから本番環境向けの初期設定までを解説します。

---

## 目次

1. [インストール方法](#1-インストール方法)
2. [データベースクラスタの初期化](#2-データベースクラスタの初期化)
3. [postgresql.conf の設定](#3-postgresqlconf-の設定)
4. [pg_hba.conf の設定](#4-pg_hbaconf-の設定)
5. [サービスの起動と管理](#5-サービスの起動と管理)
6. [初期セットアップ作業](#6-初期セットアップ作業)
7. [本番環境向けチェックリスト](#7-本番環境向けチェックリスト)

---

## 1. インストール方法

### 1.1 インストール方法の比較

```
┌──────────────────────────────────────────────────────────────┐
│                  インストール方法の選択                       │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  ① パッケージマネージャー (推奨)                             │
│     - OS標準またはPGDGリポジトリ                             │
│     - 自動更新、依存関係管理が容易                           │
│     - 本番環境向け                                           │
│                                                              │
│  ② ソースからビルド                                         │
│     - 最新バージョン、カスタムビルド                         │
│     - 開発・検証環境向け                                     │
│                                                              │
│  ③ Docker                                                   │
│     - 環境の再現性                                           │
│     - 開発・CI/CD向け                                        │
│                                                              │
│  ④ クラウドマネージドサービス                               │
│     - RDS, Cloud SQL, Azure Database等                       │
│     - 運用負荷軽減                                           │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 1.2 Linux (RHEL/CentOS/Rocky Linux)

```bash
# PGDG (PostgreSQL Global Development Group) リポジトリの追加
sudo dnf install -y https://download.postgresql.org/pub/repos/yum/reporpms/EL-9-x86_64/pgdg-redhat-repo-latest.noarch.rpm

# デフォルトのPostgreSQLモジュールを無効化
sudo dnf -qy module disable postgresql

# PostgreSQL 16 のインストール
sudo dnf install -y postgresql16-server postgresql16-contrib

# バージョン確認
/usr/pgsql-16/bin/postgres --version
```

### 1.3 Linux (Ubuntu/Debian)

```bash
# PGDGリポジトリの追加
sudo sh -c 'echo "deb https://apt.postgresql.org/pub/repos/apt $(lsb_release -cs)-pgdg main" > /etc/apt/sources.list.d/pgdg.list'

# リポジトリの署名キーをインポート
wget --quiet -O - https://www.postgresql.org/media/keys/ACCC4CF8.asc | sudo apt-key add -

# パッケージリストの更新
sudo apt-get update

# PostgreSQL 16 のインストール
sudo apt-get install -y postgresql-16 postgresql-contrib-16

# バージョン確認
psql --version
```

### 1.4 macOS

```bash
# Homebrewでインストール
brew install postgresql@16

# パスを通す
echo 'export PATH="/opt/homebrew/opt/postgresql@16/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc

# サービスの開始
brew services start postgresql@16
```

### 1.5 Docker

```bash
# 基本的な起動
docker run -d \
  --name postgres \
  -e POSTGRES_PASSWORD=mysecretpassword \
  -p 5432:5432 \
  -v pgdata:/var/lib/postgresql/data \
  postgres:16

# カスタム設定付き
docker run -d \
  --name postgres \
  -e POSTGRES_PASSWORD=mysecretpassword \
  -e POSTGRES_USER=myuser \
  -e POSTGRES_DB=mydb \
  -p 5432:5432 \
  -v pgdata:/var/lib/postgresql/data \
  -v ./postgresql.conf:/etc/postgresql/postgresql.conf \
  postgres:16 -c 'config_file=/etc/postgresql/postgresql.conf'
```

---

## 2. データベースクラスタの初期化

### 2.1 initdb の実行

```bash
# RHEL系の場合
sudo /usr/pgsql-16/bin/postgresql-16-setup initdb

# または手動で実行
sudo -u postgres /usr/pgsql-16/bin/initdb -D /var/lib/pgsql/16/data

# Ubuntu/Debianの場合（自動で実行される）
# 手動で行う場合
sudo -u postgres /usr/lib/postgresql/16/bin/initdb -D /var/lib/postgresql/16/main
```

### 2.2 initdb のオプション

```
┌──────────────────────────────────────────────────────────────┐
│                    initdb の主要オプション                    │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  -D, --pgdata=DIRECTORY                                      │
│      データディレクトリを指定                                 │
│                                                              │
│  -E, --encoding=ENCODING                                     │
│      デフォルトのエンコーディング (推奨: UTF8)               │
│                                                              │
│  --locale=LOCALE                                             │
│      ロケール設定 (推奨: C.UTF-8 または en_US.UTF-8)         │
│                                                              │
│  --lc-collate=LOCALE                                         │
│      照合順序のロケール                                       │
│                                                              │
│  --data-checksums                                            │
│      データページのチェックサムを有効化 (推奨)               │
│                                                              │
│  -k, --data-encryption                                       │
│      TDE (Transparent Data Encryption) - PostgreSQL 16+     │
│                                                              │
│  --wal-segsize=SIZE                                          │
│      WALセグメントサイズ (デフォルト: 16MB)                  │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 2.3 本番環境向け initdb 例

```bash
sudo -u postgres /usr/pgsql-16/bin/initdb \
  -D /var/lib/pgsql/16/data \
  -E UTF8 \
  --locale=C.UTF-8 \
  --data-checksums \
  --wal-segsize=64
```

### 2.4 データディレクトリの構造

```
/var/lib/pgsql/16/data/
├── base/                    # データベースファイル
│   ├── 1/                   # template1
│   ├── 4/                   # template0
│   └── 5/                   # postgres
├── global/                  # クラスタ全体の共有テーブル
├── pg_commit_ts/            # トランザクションコミットタイムスタンプ
├── pg_dynshmem/             # 動的共有メモリ
├── pg_logical/              # 論理レプリケーション
├── pg_multixact/            # マルチトランザクション
├── pg_notify/               # LISTEN/NOTIFY
├── pg_replslot/             # レプリケーションスロット
├── pg_serial/               # シリアライザブルトランザクション
├── pg_snapshots/            # エクスポートされたスナップショット
├── pg_stat/                 # 統計情報（永続）
├── pg_stat_tmp/             # 統計情報（一時）
├── pg_subtrans/             # サブトランザクション
├── pg_tblspc/               # テーブルスペースへのシンボリックリンク
├── pg_twophase/             # 2フェーズコミット
├── pg_wal/                  # WALファイル
├── pg_xact/                 # トランザクションコミット状態
├── postgresql.auto.conf     # ALTER SYSTEMによる設定
├── postgresql.conf          # メイン設定ファイル
├── pg_hba.conf              # クライアント認証設定
├── pg_ident.conf            # ユーザーマッピング
├── PG_VERSION               # PostgreSQLバージョン
├── postmaster.opts          # 最後の起動オプション
└── postmaster.pid           # プロセスID（起動中のみ）
```

---

## 3. postgresql.conf の設定

### 3.1 設定ファイルの概要

```
┌──────────────────────────────────────────────────────────────┐
│                    設定ファイルの優先順位                     │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  優先度: 低 ────────────────────────────────────────→ 高    │
│                                                              │
│  postgresql.conf                                             │
│       ↓ 上書き                                               │
│  postgresql.auto.conf (ALTER SYSTEM)                         │
│       ↓ 上書き                                               │
│  起動時オプション (-c parameter=value)                       │
│       ↓ 上書き                                               │
│  セッション単位 (SET parameter = value)                      │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 3.2 接続設定

```ini
#------------------------------------------------------------------------------
# 接続設定
#------------------------------------------------------------------------------

# リッスンするIPアドレス
# '*' = 全てのインターフェース、'localhost' = ローカルのみ
listen_addresses = '*'

# ポート番号
port = 5432

# 最大同時接続数
# 注意: 各接続はメモリを消費する (work_mem × 接続数)
max_connections = 200

# スーパーユーザー用の予約接続数
superuser_reserved_connections = 3

# Unix ソケットディレクトリ
unix_socket_directories = '/var/run/postgresql'

# 認証タイムアウト (秒)
authentication_timeout = 60

# TCP keepalive設定
tcp_keepalives_idle = 60
tcp_keepalives_interval = 10
tcp_keepalives_count = 6
```

### 3.3 メモリ設定

```
┌──────────────────────────────────────────────────────────────┐
│                    メモリ設定の考え方                         │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  【サーバー総メモリ: 64GB の場合の目安】                     │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  shared_buffers: 16GB (25%)                          │    │
│  │  - データベースキャッシュ                            │    │
│  │  - 総メモリの 25% が目安                             │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  effective_cache_size: 48GB (75%)                    │    │
│  │  - OSキャッシュを含めた見積もり                      │    │
│  │  - プランナーの判断に使用                            │    │
│  │  - 実際にはメモリ確保しない                          │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  work_mem: 64MB                                      │    │
│  │  - ソート/ハッシュ用 (接続ごと・操作ごと)            │    │
│  │  - max_connections × 並列度 を考慮                   │    │
│  │  - 大きすぎるとメモリ不足の原因に                    │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  maintenance_work_mem: 2GB                           │    │
│  │  - VACUUM, CREATE INDEX用                            │    │
│  │  - 大きいほどメンテナンスが高速                      │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

```ini
#------------------------------------------------------------------------------
# メモリ設定
#------------------------------------------------------------------------------

# 共有バッファ - 総メモリの25%程度
shared_buffers = 16GB

# OSキャッシュを含めたキャッシュ見積もり - 総メモリの75%程度
effective_cache_size = 48GB

# ソート/ハッシュ用メモリ (接続ごと)
work_mem = 64MB

# メンテナンス用メモリ
maintenance_work_mem = 2GB

# 一時バッファ (一時テーブル用)
temp_buffers = 32MB

# Huge Pages (Linux)
# 事前にOSで設定が必要
huge_pages = try

# 動的共有メモリ
dynamic_shared_memory_type = posix
```

### 3.4 WAL設定

```ini
#------------------------------------------------------------------------------
# WAL設定
#------------------------------------------------------------------------------

# WALレベル
# minimal: 最小限 (レプリケーション不可)
# replica: レプリケーション対応 (デフォルト)
# logical: 論理レプリケーション対応
wal_level = replica

# 同期コミット
# on: 完全同期 (データ損失なし、遅い)
# off: 非同期 (高速だがクラッシュ時に最大3×wal_writer_delayのデータ損失)
# local: ローカルのみ同期
# remote_write: スタンバイの書き込みまで待機
# remote_apply: スタンバイの適用まで待機
synchronous_commit = on

# WALバッファサイズ
wal_buffers = 64MB

# チェックポイント関連
checkpoint_timeout = 15min           # チェックポイント間隔
checkpoint_completion_target = 0.9   # 完了目標 (0.9 = 次のCPまでの90%の時間で完了)
max_wal_size = 4GB                   # WALの最大サイズ
min_wal_size = 1GB                   # WALの最小サイズ

# WAL圧縮
wal_compression = on

# フルページ書き込み (データ保護のため基本的にon)
full_page_writes = on
```

### 3.5 レプリケーション設定

```ini
#------------------------------------------------------------------------------
# レプリケーション設定
#------------------------------------------------------------------------------

# WAL送信プロセス数
max_wal_senders = 10

# レプリケーションスロット数
max_replication_slots = 10

# スタンバイでの問い合わせ中のWAL保持
hot_standby_feedback = on

# アーカイブモード
archive_mode = on
archive_command = 'cp %p /var/lib/pgsql/archive/%f'

# WAL保持 (レプリケーションスロット未使用時)
wal_keep_size = 1GB
```

### 3.6 並列クエリ設定

```ini
#------------------------------------------------------------------------------
# 並列クエリ設定
#------------------------------------------------------------------------------

# 並列ワーカーの最大数
max_parallel_workers = 8

# 1クエリあたりの最大並列ワーカー数
max_parallel_workers_per_gather = 4

# 並列メンテナンスワーカー数
max_parallel_maintenance_workers = 4

# 並列処理のコスト設定
parallel_tuple_cost = 0.1
parallel_setup_cost = 1000

# 並列処理を開始するテーブルサイズ
min_parallel_table_scan_size = 8MB
min_parallel_index_scan_size = 512kB
```

### 3.7 VACUUM / Autovacuum設定

```ini
#------------------------------------------------------------------------------
# Autovacuum設定
#------------------------------------------------------------------------------

# Autovacuumの有効化
autovacuum = on

# Autovacuumワーカー数
autovacuum_max_workers = 3

# チェック間隔 (秒)
autovacuum_naptime = 60

# VACUUM起動閾値
autovacuum_vacuum_threshold = 50           # 最小行数
autovacuum_vacuum_scale_factor = 0.1       # テーブルサイズの割合

# ANALYZE起動閾値
autovacuum_analyze_threshold = 50
autovacuum_analyze_scale_factor = 0.05

# コスト制限 (I/O負荷制御)
autovacuum_vacuum_cost_delay = 2ms
autovacuum_vacuum_cost_limit = 1000

# Freeze設定
autovacuum_freeze_max_age = 200000000
autovacuum_multixact_freeze_max_age = 400000000
```

### 3.8 ログ設定

```ini
#------------------------------------------------------------------------------
# ログ設定
#------------------------------------------------------------------------------

# ログ出力先
logging_collector = on
log_directory = 'log'
log_filename = 'postgresql-%Y-%m-%d.log'
log_rotation_age = 1d
log_rotation_size = 0

# ログレベル
log_min_messages = warning
log_min_error_statement = error

# スロークエリログ
log_min_duration_statement = 1000    # 1秒以上のクエリをログ

# ログ内容
log_checkpoints = on
log_connections = on
log_disconnections = on
log_lock_waits = on
log_temp_files = 0                   # 一時ファイルを全てログ
log_autovacuum_min_duration = 0      # Autovacuumを全てログ

# ログフォーマット
log_line_prefix = '%t [%p]: user=%u,db=%d,app=%a,client=%h '
log_statement = 'ddl'                # none, ddl, mod, all
```

### 3.9 統計情報設定

```ini
#------------------------------------------------------------------------------
# 統計情報設定
#------------------------------------------------------------------------------

# 統計情報コレクター
track_activities = on
track_counts = on
track_io_timing = on                 # I/O時間の計測 (若干のオーバーヘッド)
track_wal_io_timing = on
track_functions = all                # 関数の統計

# 統計情報の詳細度
default_statistics_target = 100      # ヒストグラムのサンプル数
```

### 3.10 SSD向け設定

```ini
#------------------------------------------------------------------------------
# SSD向け設定
#------------------------------------------------------------------------------

# ランダムアクセスのコストを下げる
random_page_cost = 1.1               # HDD: 4.0, SSD: 1.1-1.5

# シーケンシャルアクセスとの差を小さく
seq_page_cost = 1.0

# 実効I/O並列度
effective_io_concurrency = 200       # HDD: 2, SSD: 200
maintenance_io_concurrency = 200
```

---

## 4. pg_hba.conf の設定

### 4.1 pg_hba.conf の構造

```
┌──────────────────────────────────────────────────────────────┐
│                    pg_hba.conf の形式                         │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  TYPE    DATABASE    USER    ADDRESS         METHOD          │
│  ────    ────────    ────    ───────         ──────          │
│                                                              │
│  local   all         all                     peer            │
│  host    all         all     127.0.0.1/32    scram-sha-256   │
│  host    all         all     ::1/128         scram-sha-256   │
│  host    mydb        myuser  192.168.1.0/24  scram-sha-256   │
│                                                              │
│  【TYPE】                                                    │
│  local  : Unixドメインソケット接続                           │
│  host   : TCP/IP接続 (SSL/非SSL両方)                         │
│  hostssl: SSL接続のみ                                        │
│  hostnossl: 非SSL接続のみ                                    │
│                                                              │
│  【METHOD】                                                  │
│  trust       : 無条件で許可 (危険)                           │
│  reject      : 無条件で拒否                                  │
│  scram-sha-256: パスワード認証 (推奨)                        │
│  md5         : MD5パスワード認証 (非推奨)                    │
│  peer        : OSユーザー名で認証 (local接続用)              │
│  ident       : Identサーバーで認証                           │
│  cert        : SSL証明書認証                                 │
│  ldap        : LDAP認証                                      │
│  gss         : GSSAPI認証                                    │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 4.2 本番環境向け pg_hba.conf 例

```bash
# TYPE  DATABASE        USER            ADDRESS                 METHOD

# ローカル接続 (postgres ユーザー)
local   all             postgres                                peer

# ローカル接続 (一般ユーザー)
local   all             all                                     scram-sha-256

# ローカルホストからのTCP/IP接続
host    all             all             127.0.0.1/32            scram-sha-256
host    all             all             ::1/128                 scram-sha-256

# アプリケーションサーバーからの接続 (SSL必須)
hostssl myapp_prod      myapp_user      10.0.1.0/24             scram-sha-256

# レプリケーション用接続
hostssl replication     repl_user       10.0.2.0/24             scram-sha-256

# 監視サーバーからの接続
hostssl all             monitor_user    10.0.3.10/32            scram-sha-256

# 管理者からの接続 (特定IP)
hostssl all             admin_user      192.168.100.0/24        scram-sha-256

# その他は全て拒否 (明示的に記載)
host    all             all             0.0.0.0/0               reject
```

### 4.3 設定変更の反映

```bash
# 設定をリロード (再起動不要)
sudo -u postgres psql -c "SELECT pg_reload_conf();"

# または
sudo systemctl reload postgresql-16

# 現在の設定を確認
sudo -u postgres psql -c "SELECT * FROM pg_hba_file_rules;"
```

---

## 5. サービスの起動と管理

### 5.1 systemd での管理

```bash
# サービスの起動
sudo systemctl start postgresql-16

# サービスの停止
sudo systemctl stop postgresql-16

# サービスの再起動
sudo systemctl restart postgresql-16

# 設定リロード (接続を切断しない)
sudo systemctl reload postgresql-16

# 自動起動の有効化
sudo systemctl enable postgresql-16

# ステータス確認
sudo systemctl status postgresql-16
```

### 5.2 pg_ctl での管理

```bash
# 起動
sudo -u postgres /usr/pgsql-16/bin/pg_ctl start -D /var/lib/pgsql/16/data

# 停止モード
sudo -u postgres /usr/pgsql-16/bin/pg_ctl stop -D /var/lib/pgsql/16/data -m smart   # 全接続終了待ち
sudo -u postgres /usr/pgsql-16/bin/pg_ctl stop -D /var/lib/pgsql/16/data -m fast    # 即座に切断
sudo -u postgres /usr/pgsql-16/bin/pg_ctl stop -D /var/lib/pgsql/16/data -m immediate # 強制終了

# 再起動
sudo -u postgres /usr/pgsql-16/bin/pg_ctl restart -D /var/lib/pgsql/16/data -m fast

# リロード
sudo -u postgres /usr/pgsql-16/bin/pg_ctl reload -D /var/lib/pgsql/16/data

# ステータス
sudo -u postgres /usr/pgsql-16/bin/pg_ctl status -D /var/lib/pgsql/16/data
```

### 5.3 停止モードの違い

```
┌──────────────────────────────────────────────────────────────┐
│                      停止モードの比較                         │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  【smart】                                                   │
│  - 新規接続を拒否                                            │
│  - 既存接続の終了を待機                                      │
│  - 全セッション終了後に停止                                  │
│  - 時間がかかる可能性あり                                    │
│                                                              │
│  【fast】(推奨)                                              │
│  - 新規接続を拒否                                            │
│  - 既存接続を切断                                            │
│  - トランザクションをロールバック                            │
│  - 正常なシャットダウン                                      │
│                                                              │
│  【immediate】                                               │
│  - 即座にプロセスを終了                                      │
│  - 次回起動時にリカバリが必要                                │
│  - 緊急時のみ使用                                            │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## 6. 初期セットアップ作業

### 6.1 初期ユーザーとデータベースの作成

```sql
-- スーパーユーザー(postgres)でログイン
sudo -u postgres psql

-- 管理者ユーザーの作成
CREATE ROLE admin_user WITH
    LOGIN
    PASSWORD 'secure_password_here'
    CREATEDB
    CREATEROLE
    REPLICATION;

-- アプリケーションユーザーの作成
CREATE ROLE myapp_user WITH
    LOGIN
    PASSWORD 'app_password_here';

-- 読み取り専用ユーザーの作成
CREATE ROLE readonly_user WITH
    LOGIN
    PASSWORD 'readonly_password_here';

-- データベースの作成
CREATE DATABASE myapp_prod
    OWNER myapp_user
    ENCODING 'UTF8'
    LC_COLLATE 'C.UTF-8'
    LC_CTYPE 'C.UTF-8'
    TEMPLATE template0;

-- データベースへの接続権限
GRANT CONNECT ON DATABASE myapp_prod TO readonly_user;

-- スキーマの作成
\c myapp_prod
CREATE SCHEMA app AUTHORIZATION myapp_user;

-- 読み取り専用ユーザーへの権限
GRANT USAGE ON SCHEMA app TO readonly_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA app GRANT SELECT ON TABLES TO readonly_user;
```

### 6.2 拡張機能のインストール

```sql
-- よく使う拡張機能
\c myapp_prod

-- UUID生成
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 暗号化関数
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 統計情報
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

-- トリグラム (あいまい検索)
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- テーブル肥大化確認
CREATE EXTENSION IF NOT EXISTS pgstattuple;

-- インストール済み拡張の確認
\dx
```

### 6.3 pg_stat_statements の設定

```ini
# postgresql.conf に追加
shared_preload_libraries = 'pg_stat_statements'

# pg_stat_statements の設定
pg_stat_statements.max = 10000
pg_stat_statements.track = all
pg_stat_statements.track_utility = on
pg_stat_statements.track_planning = on
```

```bash
# 設定変更後は再起動が必要
sudo systemctl restart postgresql-16
```

---

## 7. 本番環境向けチェックリスト

### 7.1 セキュリティチェック

```
┌──────────────────────────────────────────────────────────────┐
│                  セキュリティチェックリスト                   │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  □ postgres ユーザーのパスワード設定                         │
│  □ trust 認証を使用していない                                │
│  □ listen_addresses を必要最小限に制限                       │
│  □ pg_hba.conf で接続元を制限                                │
│  □ SSL/TLS を有効化                                          │
│  □ scram-sha-256 認証を使用                                  │
│  □ アプリケーションユーザーに最小権限                        │
│  □ デフォルトの public スキーマ権限を制限                    │
│  □ ログに接続情報を記録                                      │
│  □ ファイアウォールでポートを制限                            │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 7.2 パフォーマンスチェック

```
┌──────────────────────────────────────────────────────────────┐
│                パフォーマンスチェックリスト                   │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  □ shared_buffers を適切に設定 (総メモリの25%)               │
│  □ effective_cache_size を設定 (総メモリの75%)               │
│  □ work_mem を設定 (負荷テストで調整)                        │
│  □ maintenance_work_mem を設定                               │
│  □ SSDの場合 random_page_cost を調整                         │
│  □ checkpoint_timeout, max_wal_size を調整                   │
│  □ 並列クエリを有効化                                        │
│  □ pg_stat_statements を有効化                               │
│  □ スロークエリログを有効化                                  │
│  □ Autovacuum が有効                                         │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 7.3 可用性チェック

```
┌──────────────────────────────────────────────────────────────┐
│                  可用性チェックリスト                         │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  □ data_checksums が有効                                     │
│  □ アーカイブモードが有効                                    │
│  □ レプリケーションが設定済み                                │
│  □ バックアップスクリプトが設定済み                          │
│  □ バックアップのリストアテスト実施                          │
│  □ 監視が設定済み                                            │
│  □ アラートが設定済み                                        │
│  □ 障害時の手順書作成                                        │
│  □ 自動起動が有効 (systemctl enable)                         │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 7.4 現在の設定確認クエリ

```sql
-- 主要な設定値の確認
SELECT name, setting, unit, context, short_desc
FROM pg_settings
WHERE name IN (
    'max_connections',
    'shared_buffers',
    'effective_cache_size',
    'work_mem',
    'maintenance_work_mem',
    'wal_level',
    'max_wal_senders',
    'max_replication_slots',
    'checkpoint_timeout',
    'max_wal_size',
    'random_page_cost',
    'effective_io_concurrency',
    'autovacuum',
    'log_min_duration_statement'
)
ORDER BY name;

-- data_checksums の確認
SHOW data_checksums;

-- 現在の接続数
SELECT count(*) FROM pg_stat_activity;

-- データベースサイズ
SELECT pg_database.datname, pg_size_pretty(pg_database_size(pg_database.datname))
FROM pg_database
ORDER BY pg_database_size(pg_database.datname) DESC;
```

---

## 参考リンク

- [PostgreSQL公式ドキュメント - サーバー設定](https://www.postgresql.org/docs/current/runtime-config.html)
- [PostgreSQL公式ドキュメント - クライアント認証](https://www.postgresql.org/docs/current/client-authentication.html)
- [PGTune - 設定値計算ツール](https://pgtune.leopard.in.ua/)
- [PostgreSQL Wiki - Tuning Your PostgreSQL Server](https://wiki.postgresql.org/wiki/Tuning_Your_PostgreSQL_Server)
