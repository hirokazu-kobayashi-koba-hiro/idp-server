# PostgreSQL 拡張機能（Extensions）ガイド

PostgreSQLは拡張機能（Extension）によって機能を追加できます。
本ドキュメントでは、実運用でよく使われる拡張機能を解説します。

---

## 目次

1. [拡張機能の基本操作](#1-拡張機能の基本操作)
2. [パフォーマンス・監視系](#2-パフォーマンス監視系)
3. [メンテナンス系](#3-メンテナンス系)
4. [スケジューリング系](#4-スケジューリング系)
5. [セキュリティ・暗号化系](#5-セキュリティ暗号化系)
6. [データ型・検索拡張系](#6-データ型検索拡張系)
7. [地理情報系](#7-地理情報系)
8. [外部データ連携系](#8-外部データ連携系)
   - 8.1 postgres_fdw
   - 8.2 file_fdw
   - 8.3 aws_commons（RDS専用）
   - 8.4 aws_s3（RDS専用）
   - 8.5 aws_lambda（RDS専用）
9. [拡張機能の管理ベストプラクティス](#9-拡張機能の管理ベストプラクティス)

---

## 1. 拡張機能の基本操作

### 1.1 拡張機能の概念

```
┌─────────────────────────────────────────────────────────────────┐
│                    PostgreSQL 拡張機能                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  【拡張機能とは】                                               │
│  PostgreSQLの機能を拡張するパッケージ                           │
│  ・新しいデータ型                                               │
│  ・新しい関数/演算子                                            │
│  ・新しいインデックスタイプ                                     │
│  ・バックグラウンドワーカー                                     │
│                                                                 │
│  【提供形態】                                                   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ contrib: PostgreSQL本体に同梱                            │   │
│  │   例: pg_stat_statements, pgcrypto, uuid-ossp           │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ 外部: 別途インストールが必要                             │   │
│  │   例: PostGIS, pg_partman, pg_cron, pgaudit             │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 1.2 基本コマンド

```sql
-- 利用可能な拡張機能の一覧
SELECT * FROM pg_available_extensions ORDER BY name;

-- インストール済み拡張機能の一覧
SELECT extname, extversion FROM pg_extension ORDER BY extname;

-- 拡張機能のインストール
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

-- 特定スキーマにインストール
CREATE EXTENSION pg_stat_statements WITH SCHEMA public;

-- 拡張機能のアップグレード
ALTER EXTENSION pg_stat_statements UPDATE;

-- 特定バージョンへアップグレード
ALTER EXTENSION pg_stat_statements UPDATE TO '1.10';

-- 拡張機能の削除
DROP EXTENSION pg_stat_statements;

-- 依存関係も含めて削除
DROP EXTENSION pg_stat_statements CASCADE;
```

### 1.3 shared_preload_libraries

一部の拡張機能はサーバー起動時にロードが必要です。

```ini
# postgresql.conf
# カンマ区切りで複数指定可能
shared_preload_libraries = 'pg_stat_statements, pg_cron, pgaudit'
```

```
┌─────────────────────────────────────────────────────────────────┐
│            shared_preload_libraries が必要な拡張機能            │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  【必須】                                                       │
│  ・pg_stat_statements  - クエリ統計収集                        │
│  ・pg_cron            - ジョブスケジューラ                     │
│  ・pgaudit            - 監査ログ                               │
│  ・auto_explain       - 自動EXPLAIN出力                        │
│  ・pg_prewarm         - バッファキャッシュウォームアップ       │
│                                                                 │
│  【不要】                                                       │
│  ・pgcrypto           - 暗号化関数                             │
│  ・uuid-ossp          - UUID生成                               │
│  ・pg_trgm            - 類似検索                               │
│  ・hstore             - キーバリュー型                         │
│  ・PostGIS            - 地理情報                               │
│                                                                 │
│  【注意】                                                       │
│  shared_preload_librariesの変更後は再起動が必要                │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

#### shared_preload_libraries が必要になる技術的基準

なぜ一部の拡張機能だけがサーバー起動時のロードを必要とするのか、
その技術的な理由は以下の3つの条件に集約されます。

```
┌─────────────────────────────────────────────────────────────────┐
│        shared_preload_libraries が必要な技術的理由              │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  【条件1】共有メモリを確保する必要がある                        │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ PostgreSQLの共有メモリはサーバー起動時に確保される       │   │
│  │ → 後から動的に追加できない                               │   │
│  │                                                          │   │
│  │ 例: pg_stat_statements                                   │   │
│  │     クエリ統計を保存する領域を共有メモリに確保           │   │
│  │     pg_stat_statements.max = 10000 → その分のメモリ確保  │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  【条件2】フック（Hook）を登録する必要がある                    │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ PostgreSQLは各処理ポイントにフック機構を提供             │   │
│  │ フックはサーバー起動時に登録する必要がある               │   │
│  │                                                          │   │
│  │ 主なフックポイント:                                      │   │
│  │ ・ExecutorStart_hook  - クエリ実行開始時                 │   │
│  │ ・ExecutorEnd_hook    - クエリ実行完了時                 │   │
│  │ ・ProcessUtility_hook - DDL/ユーティリティ実行時         │   │
│  │ ・planner_hook        - クエリプラン作成時               │   │
│  │                                                          │   │
│  │ 例: pg_stat_statements                                   │   │
│  │     ExecutorEnd_hook → クエリ完了時に統計記録           │   │
│  │                                                          │   │
│  │ 例: auto_explain                                         │   │
│  │     ExecutorEnd_hook → クエリ完了時に実行計画出力       │   │
│  │                                                          │   │
│  │ 例: pgaudit                                              │   │
│  │     ProcessUtility_hook → DDL実行時に監査ログ出力       │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  【条件3】バックグラウンドワーカーを起動する必要がある          │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ 常駐プロセスとして動作する機能                           │   │
│  │ → サーバー起動時にワーカーを登録・起動                   │   │
│  │                                                          │   │
│  │ 例: pg_cron                                              │   │
│  │     スケジューラとして常駐し、定期的にジョブを実行       │   │
│  │                                                          │   │
│  │ 例: pg_prewarm (autoprewarm)                             │   │
│  │     定期的にバッファ状態を保存するワーカー               │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

#### shared_preload_libraries が不要な拡張機能の特徴

```
┌─────────────────────────────────────────────────────────────────┐
│        shared_preload_libraries が不要な拡張機能                │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  【特徴】セッション単位で動作し、グローバルな状態を持たない     │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ ・新しい関数を提供するだけ                               │   │
│  │   pgcrypto: crypt(), encrypt() など                      │   │
│  │   uuid-ossp: uuid_generate_v4() など                     │   │
│  │                                                          │   │
│  │ ・新しいデータ型を提供するだけ                           │   │
│  │   hstore: キーバリュー型                                 │   │
│  │   ltree: 階層パス型                                      │   │
│  │   PostGIS: geometry, geography型                         │   │
│  │                                                          │   │
│  │ ・新しい演算子/インデックスを提供するだけ                │   │
│  │   pg_trgm: %, <-> 演算子、GINインデックス               │   │
│  │                                                          │   │
│  │ これらは CREATE EXTENSION 時に                           │   │
│  │ システムカタログ（pg_proc, pg_type等）に登録されるだけ   │   │
│  │ → 実行時にオンデマンドでロードされる                     │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

#### 判断フローチャート

```
                    拡張機能の機能は？
                          │
          ┌───────────────┼───────────────┐
          ▼               ▼               ▼
    共有メモリが      フック登録が    バックグラウンド
      必要？           必要？         ワーカーが必要？
          │               │               │
     ┌────┴────┐     ┌────┴────┐     ┌────┴────┐
     Yes      No     Yes      No     Yes      No
     │         │      │        │      │        │
     ▼         │      ▼        │      ▼        │
  必要 ────────┴───→ 必要 ─────┴───→ 必要      │
                                               │
                                               ▼
                           ┌───────────────────┴───────────────────┐
                           │  すべてNoの場合                        │
                           │  → shared_preload_libraries 不要      │
                           │  → CREATE EXTENSION だけでOK          │
                           └───────────────────────────────────────┘
```

#### 拡張機能別の分類一覧

| 拡張機能 | 共有メモリ | フック | BGワーカー | 必要？ |
|----------|:----------:|:------:|:----------:|:------:|
| pg_stat_statements | ✅ | ✅ | - | **必要** |
| pgaudit | - | ✅ | - | **必要** |
| auto_explain | - | ✅ | - | **必要** |
| pg_cron | ✅ | - | ✅ | **必要** |
| pg_prewarm (auto) | ✅ | - | ✅ | **必要** |
| pgcrypto | - | - | - | 不要 |
| uuid-ossp | - | - | - | 不要 |
| pg_trgm | - | - | - | 不要 |
| hstore | - | - | - | 不要 |
| PostGIS | - | - | - | 不要 |
| pg_repack | - | - | - | 不要 |
| pg_partman | - | - | ※ | ※条件付き |

※ pg_partman: `pg_partman_bgw`をバックグラウンドワーカーとして使う場合のみ必要。
  `pg_cron`と連携して定期実行する場合は不要。

---

## 2. パフォーマンス・監視系

### 2.1 pg_stat_statements

**最も重要な拡張機能の一つ。クエリの実行統計を収集します。**

```
┌─────────────────────────────────────────────────────────────────┐
│                     pg_stat_statements                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  【機能】                                                       │
│  ・実行されたSQLの統計情報を収集                               │
│  ・実行回数、合計時間、平均時間、行数などを記録                │
│  ・クエリを正規化（パラメータを$1, $2に置換）                  │
│                                                                 │
│  【ユースケース】                                               │
│  ・スロークエリの特定                                          │
│  ・最も頻繁に実行されるクエリの特定                            │
│  ・リソース消費の大きいクエリの特定                            │
│  ・クエリパフォーマンスの経時変化監視                          │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

#### 設定

```ini
# postgresql.conf
shared_preload_libraries = 'pg_stat_statements'

# 追跡するクエリ数（デフォルト: 5000）
pg_stat_statements.max = 10000

# 追跡対象（all/top/none）
pg_stat_statements.track = all

# ネストした関数呼び出しも追跡
pg_stat_statements.track_utility = on

# WAL使用量も追跡（PostgreSQL 13+）
pg_stat_statements.track_wal = on
```

#### 使用例

```sql
-- インストール
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

-- 総実行時間が長いクエリTOP10
SELECT
    substring(query, 1, 80) AS query_preview,
    calls,
    round(total_exec_time::numeric, 2) AS total_time_ms,
    round(mean_exec_time::numeric, 2) AS avg_time_ms,
    round((100 * total_exec_time / sum(total_exec_time) OVER ())::numeric, 2) AS percentage,
    rows
FROM pg_stat_statements
ORDER BY total_exec_time DESC
LIMIT 10;

-- 平均実行時間が長いクエリTOP10
SELECT
    substring(query, 1, 80) AS query_preview,
    calls,
    round(mean_exec_time::numeric, 2) AS avg_time_ms,
    round(stddev_exec_time::numeric, 2) AS stddev_ms,
    rows / nullif(calls, 0) AS avg_rows
FROM pg_stat_statements
WHERE calls > 100  -- 十分な実行回数があるもの
ORDER BY mean_exec_time DESC
LIMIT 10;

-- 実行回数が多いクエリTOP10
SELECT
    substring(query, 1, 80) AS query_preview,
    calls,
    round(total_exec_time::numeric, 2) AS total_time_ms,
    round(mean_exec_time::numeric, 2) AS avg_time_ms
FROM pg_stat_statements
ORDER BY calls DESC
LIMIT 10;

-- I/O時間が長いクエリ（PostgreSQL 13+）
SELECT
    substring(query, 1, 80) AS query_preview,
    calls,
    round(blk_read_time::numeric, 2) AS read_time_ms,
    round(blk_write_time::numeric, 2) AS write_time_ms,
    shared_blks_read,
    shared_blks_hit
FROM pg_stat_statements
WHERE blk_read_time > 0
ORDER BY blk_read_time DESC
LIMIT 10;

-- 統計のリセット
SELECT pg_stat_statements_reset();

-- 特定ユーザーの統計のみリセット
SELECT pg_stat_statements_reset(userid := (SELECT oid FROM pg_roles WHERE rolname = 'app_user'));
```

### 2.2 pg_buffercache

**共有バッファの内容を可視化します。**

```sql
-- インストール
CREATE EXTENSION IF NOT EXISTS pg_buffercache;

-- テーブル別のバッファ使用量
SELECT
    c.relname AS table_name,
    count(*) AS buffers,
    pg_size_pretty(count(*) * 8192) AS buffer_size,
    round(100.0 * count(*) / (SELECT count(*) FROM pg_buffercache), 2) AS percentage
FROM pg_buffercache b
JOIN pg_class c ON b.relfilenode = pg_relation_filenode(c.oid)
WHERE c.relkind IN ('r', 'i')  -- テーブルとインデックス
GROUP BY c.relname
ORDER BY buffers DESC
LIMIT 20;

-- バッファのダーティ率
SELECT
    count(*) FILTER (WHERE isdirty) AS dirty_buffers,
    count(*) AS total_buffers,
    round(100.0 * count(*) FILTER (WHERE isdirty) / count(*), 2) AS dirty_percentage
FROM pg_buffercache
WHERE relfilenode IS NOT NULL;
```

### 2.3 pg_prewarm

**テーブル/インデックスを共有バッファにプリロードします。**

```
┌─────────────────────────────────────────────────────────────────┐
│                        pg_prewarm                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  【ユースケース】                                               │
│  ・サーバー再起動後のコールドスタート対策                      │
│  ・頻繁にアクセスされるテーブルの事前ロード                    │
│                                                                 │
│  【モード】                                                     │
│  ・buffer: 共有バッファに読み込み（推奨）                      │
│  ・prefetch: OSのファイルキャッシュにプリフェッチ             │
│  ・read: 単純な読み込み（キャッシュ効果なし）                  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

```ini
# postgresql.conf（自動保存/復元機能）
shared_preload_libraries = 'pg_prewarm'
pg_prewarm.autoprewarm = on
pg_prewarm.autoprewarm_interval = 300  # 5分ごとに状態保存
```

```sql
-- インストール
CREATE EXTENSION IF NOT EXISTS pg_prewarm;

-- テーブルをバッファにロード
SELECT pg_prewarm('users');

-- インデックスをバッファにロード
SELECT pg_prewarm('users_pkey');

-- モードを指定
SELECT pg_prewarm('users', 'buffer');
SELECT pg_prewarm('users', 'prefetch');

-- 手動でバッファ状態を保存
SELECT autoprewarm_dump_now();
```

### 2.4 auto_explain

**スロークエリの実行計画を自動的にログ出力します。**

```ini
# postgresql.conf
shared_preload_libraries = 'auto_explain'

# 閾値（ms）を超えたクエリの実行計画を出力
auto_explain.log_min_duration = 1000  # 1秒

# ANALYZE情報を含める（実際の行数など）
auto_explain.log_analyze = on

# バッファ使用状況を含める
auto_explain.log_buffers = on

# 出力形式
auto_explain.log_format = 'text'  # text/xml/json/yaml

# ネストした関数も対象
auto_explain.log_nested_statements = on
```

```sql
-- セッション単位で有効化（テスト用）
LOAD 'auto_explain';
SET auto_explain.log_min_duration = 0;  -- 全クエリ
SET auto_explain.log_analyze = on;

-- テストクエリ実行
SELECT * FROM large_table WHERE condition;
-- → サーバーログに実行計画が出力される
```

---

## 3. メンテナンス系

### 3.1 pg_repack

**テーブルをオンラインで再編成（VACUUM FULLの代替）**

```
┌─────────────────────────────────────────────────────────────────┐
│                        pg_repack                                │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  【VACUUM FULL との比較】                                       │
│                                                                 │
│  VACUUM FULL:                                                   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ ✅ 標準機能                                              │   │
│  │ ❌ 排他ロック（テーブル全体をロック）                   │   │
│  │ ❌ 実行中は読み書き不可                                 │   │
│  │ ❌ 大きなテーブルでは長時間ダウンタイム                 │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  pg_repack:                                                     │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ ✅ オンラインで実行可能                                  │   │
│  │ ✅ 実行中も読み書き可能                                 │   │
│  │ ✅ 最後の切り替え時のみ短時間ロック                     │   │
│  │ ❌ 一時的に2倍のディスク容量が必要                      │   │
│  │ ❌ レプリカアイデンティティ（PK等）が必要              │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

#### インストール（Debian/Ubuntu）

```bash
sudo apt install postgresql-15-repack
```

#### 使用例

```sql
-- 拡張機能のインストール
CREATE EXTENSION IF NOT EXISTS pg_repack;
```

```bash
# テーブルの再編成
pg_repack -d mydb -t large_table

# 特定スキーマの全テーブル
pg_repack -d mydb -s public

# データベース全体
pg_repack -d mydb

# インデックスのみ再編成
pg_repack -d mydb -t large_table --only-indexes

# 並列実行（PostgreSQL 14+）
pg_repack -d mydb -t large_table -j 4

# ドライラン（実行せずに確認）
pg_repack -d mydb -t large_table --dry-run
```

### 3.2 pg_squeeze

**pg_repackの代替。異なるアプローチでテーブル再編成**

```
┌─────────────────────────────────────────────────────────────────┐
│               pg_repack vs pg_squeeze                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  【pg_repack】                                                  │
│  ・トリガーで変更をキャプチャ                                  │
│  ・外部コマンドで実行                                          │
│  ・一般的に高速                                                 │
│                                                                 │
│  【pg_squeeze】                                                 │
│  ・論理デコードで変更をキャプチャ                              │
│  ・バックグラウンドワーカーで実行                              │
│  ・スケジュール実行が可能                                      │
│  ・論理レプリケーション環境でも使用可能                        │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 4. スケジューリング系

### 4.1 pg_cron

**PostgreSQL内でcronジョブを実行します。**

```
┌─────────────────────────────────────────────────────────────────┐
│                         pg_cron                                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  【特徴】                                                       │
│  ・PostgreSQL内部でジョブスケジューリング                      │
│  ・cron構文で時間指定                                          │
│  ・SQL/関数を定期実行                                          │
│  ・実行履歴の記録                                              │
│                                                                 │
│  【ユースケース】                                               │
│  ・古いデータの定期削除                                        │
│  ・統計情報の定期収集                                          │
│  ・パーティション管理                                          │
│  ・定期的なVACUUM/ANALYZE                                      │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

#### 設定

```ini
# postgresql.conf
shared_preload_libraries = 'pg_cron'

# pg_cronを実行するデータベース
cron.database_name = 'postgres'

# 同時実行ジョブ数
cron.max_running_jobs = 32

# バックグラウンドワーカー使用（推奨）
cron.use_background_workers = on
```

#### 使用例

```sql
-- インストール
CREATE EXTENSION IF NOT EXISTS pg_cron;

-- ジョブの登録（毎日AM3時に古いログを削除）
SELECT cron.schedule(
    'cleanup-old-logs',           -- ジョブ名
    '0 3 * * *',                  -- cron式（毎日3:00）
    $$DELETE FROM logs WHERE created_at < now() - interval '30 days'$$
);

-- 毎時0分にVACUUM ANALYZE
SELECT cron.schedule(
    'hourly-vacuum',
    '0 * * * *',
    'VACUUM ANALYZE active_sessions'
);

-- 毎月1日にパーティション作成
SELECT cron.schedule(
    'create-monthly-partition',
    '0 0 1 * *',
    $$SELECT create_next_partition('events')$$
);

-- 5分ごとに統計更新
SELECT cron.schedule(
    'update-stats',
    '*/5 * * * *',
    'SELECT refresh_materialized_view_concurrently()'
);

-- ジョブ一覧
SELECT * FROM cron.job;

-- ジョブの削除
SELECT cron.unschedule('cleanup-old-logs');

-- ジョブIDで削除
SELECT cron.unschedule(1);

-- 実行履歴
SELECT * FROM cron.job_run_details
ORDER BY start_time DESC
LIMIT 20;

-- 失敗したジョブの確認
SELECT * FROM cron.job_run_details
WHERE status = 'failed'
ORDER BY start_time DESC;

-- 別データベースでジョブ実行（PostgreSQL 14+）
SELECT cron.schedule_in_database(
    'cleanup-app-logs',
    '0 4 * * *',
    $$DELETE FROM logs WHERE age > 90$$,
    'app_database'
);
```

#### cron式の書式

```
┌─────────────────────────────────────────────────────────────────┐
│                      cron式の書式                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌───────────── 分 (0-59)                                      │
│  │ ┌─────────── 時 (0-23)                                      │
│  │ │ ┌───────── 日 (1-31)                                      │
│  │ │ │ ┌─────── 月 (1-12)                                      │
│  │ │ │ │ ┌───── 曜日 (0-6, 0=日曜)                             │
│  │ │ │ │ │                                                     │
│  * * * * *                                                      │
│                                                                 │
│  【例】                                                         │
│  '0 3 * * *'      毎日3:00                                     │
│  '*/5 * * * *'    5分ごと                                      │
│  '0 0 * * 0'      毎週日曜0:00                                 │
│  '0 0 1 * *'      毎月1日0:00                                  │
│  '30 4 1,15 * *'  毎月1日と15日の4:30                          │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 4.2 pg_partman

**パーティション管理を自動化します。**

```
┌─────────────────────────────────────────────────────────────────┐
│                       pg_partman                                │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  【機能】                                                       │
│  ・パーティションの自動作成                                    │
│  ・古いパーティションの自動削除/アーカイブ                    │
│  ・時間ベース/シリアルベースのパーティション対応              │
│                                                                 │
│  【対応パーティションタイプ】                                  │
│  ・RANGE（時間、数値）                                         │
│  ・LIST                                                         │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

#### 設定・使用例

```sql
-- インストール
CREATE SCHEMA IF NOT EXISTS partman;
CREATE EXTENSION IF NOT EXISTS pg_partman SCHEMA partman;

-- パーティションテーブルの作成
CREATE TABLE events (
    id BIGSERIAL,
    event_time TIMESTAMPTZ NOT NULL,
    event_type TEXT,
    payload JSONB,
    PRIMARY KEY (id, event_time)
) PARTITION BY RANGE (event_time);

-- pg_partmanでパーティション管理を設定
SELECT partman.create_parent(
    p_parent_table := 'public.events',
    p_control := 'event_time',
    p_type := 'native',
    p_interval := 'daily',
    p_premake := 7,           -- 7日先まで事前作成
    p_start_partition := (now() - interval '30 days')::text
);

-- 設定の確認
SELECT * FROM partman.part_config;

-- 手動でパーティションメンテナンス実行
CALL partman.run_maintenance_proc();

-- 古いパーティションの保持設定（30日より古いものを削除）
UPDATE partman.part_config
SET retention = '30 days',
    retention_keep_table = false,  -- テーブル削除
    retention_keep_index = false
WHERE parent_table = 'public.events';

-- pg_cronと連携（毎時メンテナンス）
SELECT cron.schedule(
    'partman-maintenance',
    '0 * * * *',
    'CALL partman.run_maintenance_proc()'
);
```

---

## 5. セキュリティ・暗号化系

### 5.1 pgcrypto

**暗号化関数を提供します。**

```sql
-- インストール
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ハッシュ関数
SELECT encode(digest('password', 'sha256'), 'hex');
SELECT encode(digest('password', 'sha512'), 'hex');

-- パスワードハッシュ（bcrypt）
SELECT crypt('user_password', gen_salt('bf', 10));

-- パスワード検証
SELECT crypt('input_password', stored_hash) = stored_hash;

-- ランダムバイト生成
SELECT encode(gen_random_bytes(32), 'hex');

-- UUID生成（PostgreSQL 13+はgen_random_uuid()が標準）
SELECT gen_random_uuid();

-- 対称鍵暗号（AES-256）
-- 暗号化
SELECT encode(
    encrypt('機密データ'::bytea, 'encryption_key'::bytea, 'aes'),
    'base64'
);

-- 復号
SELECT convert_from(
    decrypt(
        decode('暗号化されたデータ', 'base64'),
        'encryption_key'::bytea,
        'aes'
    ),
    'UTF8'
);

-- PGP暗号化
SELECT armor(pgp_sym_encrypt('機密データ', 'passphrase'));

-- PGP復号
SELECT pgp_sym_decrypt(
    dearmor('-----BEGIN PGP MESSAGE----- ...'),
    'passphrase'
);
```

### 5.2 pgaudit

**詳細な監査ログを出力します。**

```
┌─────────────────────────────────────────────────────────────────┐
│                        pgaudit                                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  【機能】                                                       │
│  ・セッション監査ログ（全クエリ）                              │
│  ・オブジェクト監査ログ（特定テーブルへのアクセス）            │
│  ・ロール別の監査設定                                          │
│                                                                 │
│  【ログ対象】                                                   │
│  ・READ: SELECT, COPY FROM                                     │
│  ・WRITE: INSERT, UPDATE, DELETE, TRUNCATE, COPY TO            │
│  ・FUNCTION: 関数呼び出し                                      │
│  ・ROLE: ロール操作                                            │
│  ・DDL: CREATE, ALTER, DROP                                    │
│  ・MISC: その他                                                │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

#### 設定

```ini
# postgresql.conf
shared_preload_libraries = 'pgaudit'

# セッション監査（全ユーザー）
pgaudit.log = 'ddl, role'

# ログにオブジェクト名を含める
pgaudit.log_relation = on

# ログにステートメントを含める
pgaudit.log_statement_once = off

# パラメータも記録
pgaudit.log_parameter = on
```

```sql
-- インストール
CREATE EXTENSION IF NOT EXISTS pgaudit;

-- 特定ロールの監査設定
ALTER ROLE audited_user SET pgaudit.log = 'read, write';

-- オブジェクト監査用ロールの作成
CREATE ROLE auditor NOLOGIN;
GRANT SELECT ON sensitive_table TO auditor;

-- オブジェクト監査の有効化
SET pgaudit.role = 'auditor';
```

#### ログ出力例

```
AUDIT: SESSION,1,1,DDL,CREATE TABLE,TABLE,public.users,
       "CREATE TABLE users (id SERIAL PRIMARY KEY, name TEXT)"

AUDIT: SESSION,2,1,READ,SELECT,TABLE,public.users,
       "SELECT * FROM users WHERE id = $1",[123]
```

---

## 6. データ型・検索拡張系

### 6.1 uuid-ossp

**UUID生成関数を提供します。**

```sql
-- インストール
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- バージョン1 UUID（MACアドレス+タイムスタンプ）
SELECT uuid_generate_v1();

-- バージョン4 UUID（ランダム）※最も一般的
SELECT uuid_generate_v4();

-- PostgreSQL 13+は標準関数で代替可能
SELECT gen_random_uuid();

-- テーブルでの使用例
CREATE TABLE orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMPTZ DEFAULT now()
);
```

### 6.2 pg_trgm

**類似文字列検索（あいまい検索）を提供します。**

```
┌─────────────────────────────────────────────────────────────────┐
│                        pg_trgm                                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  【トライグラム (trigram)】                                     │
│  文字列を3文字ずつに分割して類似度を計算                       │
│                                                                 │
│  "hello" → {"  h", " he", "hel", "ell", "llo", "lo ", "o  "}   │
│                                                                 │
│  【ユースケース】                                               │
│  ・タイプミス許容検索                                          │
│  ・氏名のあいまい検索                                          │
│  ・商品名の類似検索                                            │
│  ・LIKE検索の高速化                                            │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

```sql
-- インストール
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 類似度の計算
SELECT similarity('hello', 'hallo');  -- 0.5
SELECT similarity('PostgreSQL', 'PostgrsQL');  -- 0.6

-- 類似度による検索
SELECT name, similarity(name, 'Jhon') AS sim
FROM users
WHERE similarity(name, 'Jhon') > 0.3
ORDER BY sim DESC;

-- %演算子（閾値: pg_trgm.similarity_threshold）
SELECT * FROM users WHERE name % 'Jhon';

-- GINインデックスの作成（高速化）
CREATE INDEX idx_users_name_trgm ON users USING gin (name gin_trgm_ops);

-- GiSTインデックス（ORDER BY similarityに最適）
CREATE INDEX idx_users_name_gist ON users USING gist (name gist_trgm_ops);

-- LIKE/ILIKEの高速化
-- GINインデックスがあれば、LIKEも高速
SELECT * FROM users WHERE name LIKE '%smith%';
SELECT * FROM users WHERE name ILIKE '%SMITH%';

-- 正規表現も高速化
SELECT * FROM users WHERE name ~ 'john|jane';
```

### 6.3 hstore

**キーバリュー型のデータを格納します。**

```sql
-- インストール
CREATE EXTENSION IF NOT EXISTS hstore;

-- hstoreカラムの作成
CREATE TABLE products (
    id SERIAL PRIMARY KEY,
    name TEXT,
    attributes hstore
);

-- データの挿入
INSERT INTO products (name, attributes)
VALUES ('Laptop', 'brand => Dell, cpu => "Intel i7", ram => "16GB"');

-- 値の取得
SELECT attributes -> 'brand' FROM products;

-- キーの存在確認
SELECT * FROM products WHERE attributes ? 'gpu';

-- 複数キーの存在確認
SELECT * FROM products WHERE attributes ?& ARRAY['brand', 'cpu'];

-- 値での検索
SELECT * FROM products WHERE attributes @> 'brand => Dell';

-- キーの追加/更新
UPDATE products
SET attributes = attributes || 'ssd => "512GB"'
WHERE id = 1;

-- GINインデックス
CREATE INDEX idx_products_attrs ON products USING gin (attributes);
```

### 6.4 citext

**大文字小文字を区別しないテキスト型を提供します。**

```sql
-- インストール
CREATE EXTENSION IF NOT EXISTS citext;

-- citextカラムの作成
CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    email citext UNIQUE,
    username citext
);

-- 大文字小文字を無視して一意制約
INSERT INTO users (email) VALUES ('User@Example.com');
INSERT INTO users (email) VALUES ('user@example.com');  -- 重複エラー

-- 検索も大文字小文字を無視
SELECT * FROM users WHERE email = 'USER@EXAMPLE.COM';  -- マッチする
```

### 6.5 ltree

**階層構造データを効率的に扱います。**

```sql
-- インストール
CREATE EXTENSION IF NOT EXISTS ltree;

-- 階層データの格納
CREATE TABLE categories (
    id SERIAL PRIMARY KEY,
    name TEXT,
    path ltree
);

-- データの挿入
INSERT INTO categories (name, path) VALUES
    ('Electronics', 'electronics'),
    ('Computers', 'electronics.computers'),
    ('Laptops', 'electronics.computers.laptops'),
    ('Desktops', 'electronics.computers.desktops'),
    ('Phones', 'electronics.phones');

-- 子孫の取得（@>）
SELECT * FROM categories WHERE path <@ 'electronics.computers';

-- 祖先の取得（<@）
SELECT * FROM categories WHERE path @> 'electronics.computers.laptops';

-- パターンマッチ
SELECT * FROM categories WHERE path ~ 'electronics.*{1}';  -- 直接の子

-- GiSTインデックス
CREATE INDEX idx_categories_path ON categories USING gist (path);
```

---

## 7. 地理情報系

### 7.1 PostGIS

**地理空間データを扱う最も強力な拡張機能です。**

```
┌─────────────────────────────────────────────────────────────────┐
│                        PostGIS                                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  【機能】                                                       │
│  ・geometry/geography データ型                                 │
│  ・空間インデックス（GiST）                                    │
│  ・空間関数（距離、交差、包含など）                            │
│  ・座標系変換                                                  │
│  ・ラスターデータ対応                                          │
│                                                                 │
│  【ユースケース】                                               │
│  ・位置情報サービス                                            │
│  ・配送ルート最適化                                            │
│  ・地図アプリケーション                                        │
│  ・不動産検索                                                  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

```sql
-- インストール
CREATE EXTENSION IF NOT EXISTS postgis;

-- 位置情報テーブル
CREATE TABLE locations (
    id SERIAL PRIMARY KEY,
    name TEXT,
    location geography(Point, 4326)  -- WGS84座標系
);

-- データの挿入
INSERT INTO locations (name, location)
VALUES ('Tokyo Station', ST_GeogFromText('POINT(139.7671 35.6812)'));

-- 特定地点からの距離で検索（半径5km以内）
SELECT name, ST_Distance(location, ST_GeogFromText('POINT(139.7671 35.6812)')) AS distance_m
FROM locations
WHERE ST_DWithin(location, ST_GeogFromText('POINT(139.7671 35.6812)'), 5000);

-- 空間インデックス
CREATE INDEX idx_locations_geog ON locations USING gist (location);

-- 最寄りの場所を検索
SELECT name, ST_Distance(location, ST_GeogFromText('POINT(139.75 35.68)')) AS distance_m
FROM locations
ORDER BY location <-> ST_GeogFromText('POINT(139.75 35.68)')
LIMIT 5;
```

---

## 8. 外部データ連携系

### 8.1 postgres_fdw

**他のPostgreSQLサーバーにアクセスします。**

```sql
-- インストール
CREATE EXTENSION IF NOT EXISTS postgres_fdw;

-- 外部サーバーの定義
CREATE SERVER remote_server
FOREIGN DATA WRAPPER postgres_fdw
OPTIONS (host 'remote-host', port '5432', dbname 'remote_db');

-- ユーザーマッピング
CREATE USER MAPPING FOR local_user
SERVER remote_server
OPTIONS (user 'remote_user', password 'remote_password');

-- 外部テーブルのインポート
IMPORT FOREIGN SCHEMA public
FROM SERVER remote_server
INTO local_schema;

-- 個別の外部テーブル定義
CREATE FOREIGN TABLE remote_users (
    id INTEGER,
    name TEXT,
    email TEXT
)
SERVER remote_server
OPTIONS (schema_name 'public', table_name 'users');

-- 使用（通常のテーブルと同様）
SELECT * FROM remote_users WHERE id = 1;
```

### 8.2 file_fdw

**CSVファイルなどをテーブルとして読み込みます。**

```sql
-- インストール
CREATE EXTENSION IF NOT EXISTS file_fdw;

-- サーバー定義
CREATE SERVER file_server FOREIGN DATA WRAPPER file_fdw;

-- 外部テーブル定義
CREATE FOREIGN TABLE import_data (
    id INTEGER,
    name TEXT,
    value NUMERIC
)
SERVER file_server
OPTIONS (
    filename '/var/lib/postgresql/data/import.csv',
    format 'csv',
    header 'true'
);

-- 使用
SELECT * FROM import_data;
COPY real_table FROM (SELECT * FROM import_data WHERE value > 100);
```

### 8.3 aws_commons（RDS専用）

**AWS連携拡張機能の共通基盤です。**

> **Note:** `shared_preload_libraries` への追加は**不要**です。
> 通常は `aws_s3` や `aws_lambda` のインストール時に `CASCADE` で自動インストールされます。

```
┌─────────────────────────────────────────────────────────────────┐
│                       aws_commons                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  【役割】                                                       │
│  aws_s3, aws_lambda などAWS連携拡張の共通ヘルパー関数を提供    │
│                                                                 │
│  【依存関係】                                                   │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                                                          │   │
│  │              aws_commons（共通基盤）                     │   │
│  │                    ▲                                     │   │
│  │         ┌──────────┴──────────┐                         │   │
│  │         │                     │                          │   │
│  │     aws_s3               aws_lambda                      │   │
│  │   (S3連携)             (Lambda連携)                      │   │
│  │                                                          │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  【提供する主な関数】                                           │
│  ・create_s3_uri()              - S3オブジェクトのURI作成      │
│  ・create_lambda_function_arn() - Lambda関数のARN作成          │
│  ・create_aws_credentials()     - 認証情報の作成（非推奨）     │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

#### 主な関数

```sql
-- インストール（通常は依存関係で自動インストール）
CREATE EXTENSION IF NOT EXISTS aws_commons;

-- S3 URIの作成
SELECT aws_commons.create_s3_uri(
    'bucket-name',           -- バケット名
    'path/to/object.csv',    -- オブジェクトキー
    'ap-northeast-1'         -- リージョン
);

-- Lambda関数ARNの作成
SELECT aws_commons.create_lambda_function_arn(
    'function-name',         -- 関数名
    'ap-northeast-1'         -- リージョン
);

-- 認証情報の作成（IAMロール使用時は不要、非推奨）
SELECT aws_commons.create_aws_credentials(
    'AKIAIOSFODNN7EXAMPLE',  -- アクセスキーID
    'wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY',  -- シークレットキー
    ''                       -- セッショントークン（オプション）
);
```

#### 認証方式の比較

| 方式 | 設定方法 | 推奨度 |
|------|----------|:------:|
| **IAMロール** | RDSインスタンスにロールをアタッチ | ✅ 推奨 |
| アクセスキー | `create_aws_credentials()`で指定 | ⚠️ 非推奨 |

IAMロール使用時は認証情報の指定が不要で、セキュリティ面でも優れています。

---

### 8.4 aws_s3（RDS専用）

**Amazon S3との間でデータをインポート/エクスポートします。**

> **Note:** `shared_preload_libraries` への追加は**不要**です。
> `CREATE EXTENSION` のみで使用可能で、サーバー再起動は必要ありません。
> （新しい関数を提供するだけで、共有メモリ・フック・BGワーカーを使用しないため）

```
┌─────────────────────────────────────────────────────────────────┐
│                         aws_s3                                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  【提供元】Amazon RDS for PostgreSQL 専用                       │
│  【前提】aws_commons 拡張機能が必要（CASCADE で自動インストール）│
│                                                                 │
│  【アーキテクチャ】                                             │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                                                          │   │
│  │   RDS PostgreSQL  ◄─────────────────►  Amazon S3        │   │
│  │        │                                    │            │   │
│  │        │  table_import_from_s3()           │            │   │
│  │        │  ◄─────────────────────────────   │            │   │
│  │        │                                    │            │   │
│  │        │  query_export_to_s3()             │            │   │
│  │        │  ─────────────────────────────►   │            │   │
│  │        │                                    │            │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  【機能】                                                       │
│  ・S3からテーブルへデータインポート                            │
│  ・クエリ結果をS3へエクスポート                                │
│  ・CSV/テキスト形式対応                                        │
│  ・gzip圧縮対応                                                │
│                                                                 │
│  【認証方式】                                                   │
│  ・IAMロールをRDSインスタンスにアタッチ（推奨）                │
│  ・アクセスキー/シークレットキー（非推奨）                     │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

#### 前提条件（IAMロール設定）

```
┌─────────────────────────────────────────────────────────────────┐
│                    IAMロール設定手順                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. IAMロールの作成                                             │
│     ┌─────────────────────────────────────────────────────┐    │
│     │ {                                                    │    │
│     │   "Version": "2012-10-17",                          │    │
│     │   "Statement": [                                     │    │
│     │     {                                                │    │
│     │       "Effect": "Allow",                            │    │
│     │       "Action": [                                    │    │
│     │         "s3:GetObject",                             │    │
│     │         "s3:PutObject",                             │    │
│     │         "s3:ListBucket"                             │    │
│     │       ],                                             │    │
│     │       "Resource": [                                  │    │
│     │         "arn:aws:s3:::your-bucket",                 │    │
│     │         "arn:aws:s3:::your-bucket/*"                │    │
│     │       ]                                              │    │
│     │     }                                                │    │
│     │   ]                                                  │    │
│     │ }                                                    │    │
│     └─────────────────────────────────────────────────────┘    │
│                                                                 │
│  2. RDSインスタンスにIAMロールをアタッチ                        │
│     aws rds add-role-to-db-instance \                          │
│       --db-instance-identifier your-rds-instance \             │
│       --role-arn arn:aws:iam::123456789:role/rds-s3-role \    │
│       --feature-name s3Import                                  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

#### インストール

```sql
-- aws_commonsも自動的にインストールされる
CREATE EXTENSION IF NOT EXISTS aws_s3 CASCADE;

-- インストール確認
SELECT * FROM pg_extension WHERE extname IN ('aws_s3', 'aws_commons');
```

#### S3からのインポート

```sql
-- 基本的なインポート
SELECT aws_s3.table_import_from_s3(
    'target_table',                    -- インポート先テーブル
    '',                                -- カラム指定（空=全カラム）
    '(format csv, header true)',       -- COPYオプション
    aws_commons.create_s3_uri(
        'my-bucket',                   -- バケット名
        'data/import.csv',             -- オブジェクトキー
        'ap-northeast-1'               -- リージョン
    )
);

-- 特定カラムのみインポート
SELECT aws_s3.table_import_from_s3(
    'users',
    'id, name, email',                 -- カラム指定
    '(format csv, header true, null ''NULL'')',
    aws_commons.create_s3_uri(
        'my-bucket',
        'imports/users.csv',
        'ap-northeast-1'
    )
);

-- gzip圧縮ファイルのインポート
SELECT aws_s3.table_import_from_s3(
    'large_table',
    '',
    '(format csv, header true)',
    aws_commons.create_s3_uri(
        'my-bucket',
        'data/large_data.csv.gz',
        'ap-northeast-1'
    )
);
```

#### S3へのエクスポート

```sql
-- クエリ結果をS3にエクスポート
SELECT aws_s3.query_export_to_s3(
    'SELECT * FROM orders WHERE created_at >= ''2024-01-01''',
    aws_commons.create_s3_uri(
        'my-bucket',
        'exports/orders_2024.csv',
        'ap-northeast-1'
    ),
    options := 'format csv, header true'
);

-- 複雑なクエリのエクスポート
SELECT aws_s3.query_export_to_s3(
    $$
    SELECT
        u.id,
        u.name,
        u.email,
        count(o.id) as order_count,
        sum(o.total) as total_amount
    FROM users u
    LEFT JOIN orders o ON u.id = o.user_id
    GROUP BY u.id, u.name, u.email
    $$,
    aws_commons.create_s3_uri(
        'my-bucket',
        'reports/user_summary.csv',
        'ap-northeast-1'
    ),
    options := 'format csv, header true'
);

-- 戻り値（エクスポートされた行数とファイル情報）
-- rows_uploaded | files_uploaded | bytes_uploaded
-- 10000         | 1              | 524288
```

#### ユースケース

```
┌─────────────────────────────────────────────────────────────────┐
│                    aws_s3 ユースケース                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  【データレイク連携】                                           │
│  RDS → S3 → Athena/Redshift/EMR                                │
│  定期的にクエリ結果をS3に出力し、分析基盤で活用                │
│                                                                 │
│  【バルクインポート】                                           │
│  大量CSVファイルをS3経由で高速にロード                         │
│  COPYコマンドより高速（並列処理）                              │
│                                                                 │
│  【バックアップ補完】                                           │
│  特定テーブルの論理バックアップをS3に直接出力                  │
│  日次でマスターデータをエクスポート                            │
│                                                                 │
│  【ETL処理】                                                    │
│  S3 → RDS: Lambda/Glueで加工したデータをインポート             │
│  RDS → S3: 加工結果を他システムへ連携                          │
│                                                                 │
│  【pg_cronとの連携例】                                          │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ SELECT cron.schedule(                                    │   │
│  │     'daily-export',                                      │   │
│  │     '0 2 * * *',                                        │   │
│  │     $$SELECT aws_s3.query_export_to_s3(                 │   │
│  │         'SELECT * FROM daily_metrics',                   │   │
│  │         aws_commons.create_s3_uri(                       │   │
│  │             'analytics-bucket',                          │   │
│  │             'metrics/' || to_char(now(), 'YYYYMMDD'),   │   │
│  │             'ap-northeast-1'                             │   │
│  │         ),                                               │   │
│  │         options := 'format csv'                          │   │
│  │     )$$                                                  │   │
│  │ );                                                       │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 8.5 aws_lambda（RDS専用）

**PostgreSQLからAWS Lambda関数を呼び出します。**

> **Note:** `shared_preload_libraries` への追加は**不要**です。
> `CREATE EXTENSION` のみで使用可能で、サーバー再起動は必要ありません。

```sql
-- インストール
CREATE EXTENSION IF NOT EXISTS aws_lambda CASCADE;

-- Lambda関数の同期呼び出し
SELECT aws_lambda.invoke(
    aws_commons.create_lambda_function_arn(
        'my-function',
        'ap-northeast-1'
    ),
    '{"key": "value"}'::json
);

-- 非同期呼び出し
SELECT aws_lambda.invoke(
    aws_commons.create_lambda_function_arn(
        'my-async-function',
        'ap-northeast-1'
    ),
    '{"event": "data"}'::json,
    'Event'  -- 非同期
);
```

```
┌─────────────────────────────────────────────────────────────────┐
│                    aws_lambda ユースケース                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  【トリガーからの呼び出し】                                     │
│  INSERT/UPDATE時にLambdaで外部システムへ通知                   │
│                                                                 │
│  【データ変換】                                                 │
│  複雑な変換ロジックをLambdaに委譲                              │
│                                                                 │
│  【外部API連携】                                                │
│  LambdaをプロキシとしてREST API呼び出し                        │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 9. 拡張機能の管理ベストプラクティス

### 9.1 バージョン管理

```sql
-- 拡張機能のバージョン確認
SELECT
    extname,
    extversion,
    (SELECT version FROM pg_available_extension_versions
     WHERE name = e.extname
     ORDER BY version DESC LIMIT 1) AS latest_version
FROM pg_extension e
ORDER BY extname;

-- アップグレード可能な拡張機能
SELECT
    name,
    installed_version,
    default_version
FROM pg_available_extensions
WHERE installed_version IS NOT NULL
  AND installed_version <> default_version;
```

### 9.2 依存関係の確認

```sql
-- 拡張機能の依存関係
SELECT
    e.extname,
    d.refobjid::regclass AS depends_on
FROM pg_depend d
JOIN pg_extension e ON d.objid = e.oid
WHERE d.deptype = 'e'
  AND d.classid = 'pg_extension'::regclass;
```

### 9.3 RDS/Auroraでの対応状況

```
┌─────────────────────────────────────────────────────────────────┐
│                RDS/Aurora での拡張機能対応                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  【利用可能（主要なもの）】                                     │
│  ✅ pg_stat_statements                                          │
│  ✅ pgcrypto                                                    │
│  ✅ uuid-ossp                                                   │
│  ✅ pg_trgm                                                     │
│  ✅ hstore                                                      │
│  ✅ PostGIS                                                     │
│  ✅ pg_partman                                                  │
│  ✅ pgaudit                                                     │
│  ✅ pg_repack                                                   │
│  ✅ postgres_fdw                                                │
│                                                                 │
│  【RDS固有】                                                    │
│  ✅ pg_cron（RDS PostgreSQL 12.5+）                            │
│     → cron.database_name はParameter Groupで設定               │
│                                                                 │
│  【利用不可（セキュリティ上の理由）】                           │
│  ❌ adminpack                                                   │
│  ❌ file_fdw（セキュリティ上の制約あり）                       │
│                                                                 │
│  【確認方法】                                                   │
│  SHOW rds.extensions;                                           │
│  SELECT * FROM pg_available_extensions;                        │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 9.4 本番環境での推奨拡張機能セット

```sql
-- 監視・パフォーマンス（必須）
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

-- セキュリティ
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pgaudit;

-- メンテナンス
CREATE EXTENSION IF NOT EXISTS pg_repack;

-- スケジューリング（パーティション管理用）
-- shared_preload_libraries に追加後
CREATE EXTENSION IF NOT EXISTS pg_cron;
CREATE SCHEMA IF NOT EXISTS partman;
CREATE EXTENSION IF NOT EXISTS pg_partman SCHEMA partman;

-- 検索機能強化
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- UUID
-- PostgreSQL 13+は不要（gen_random_uuid()が標準）
-- CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
```

```ini
# postgresql.conf
shared_preload_libraries = 'pg_stat_statements, pg_cron, pgaudit'
```

---

## 参考リンク

- [PostgreSQL Extensions](https://www.postgresql.org/docs/current/contrib.html)
- [pg_stat_statements](https://www.postgresql.org/docs/current/pgstatstatements.html)
- [pg_cron](https://github.com/citusdata/pg_cron)
- [pg_partman](https://github.com/pgpartman/pg_partman)
- [pg_repack](https://github.com/reorg/pg_repack)
- [PostGIS](https://postgis.net/)
- [pgAudit](https://www.pgaudit.org/)
