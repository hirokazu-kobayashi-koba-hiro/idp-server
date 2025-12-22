# PostgreSQL 監視ガイド

このドキュメントでは、PostgreSQLの監視方法とアラート設計を解説します。

---

## 目次

1. [監視の概要](#1-監視の概要)
2. [統計情報ビュー (pg_stat_*)](#2-統計情報ビュー-pg_stat_)
3. [パフォーマンス監視](#3-パフォーマンス監視)
4. [リソース監視](#4-リソース監視)
5. [pg_stat_statements](#5-pg_stat_statements)
6. [アラート設計](#6-アラート設計)
7. [監視ツールとの連携](#7-監視ツールとの連携)

---

## 1. 監視の概要

### 1.1 監視の目的

```
┌──────────────────────────────────────────────────────────────┐
│                       監視の目的                              │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  【可用性監視】                                              │
│  - PostgreSQLプロセスが稼働しているか                        │
│  - 接続を受け付けているか                                    │
│  - レプリケーションが正常か                                  │
│                                                              │
│  【パフォーマンス監視】                                      │
│  - クエリの応答時間                                          │
│  - スロークエリの発生                                        │
│  - ロック競合                                                │
│                                                              │
│  【リソース監視】                                            │
│  - ディスク使用量                                            │
│  - 接続数                                                    │
│  - メモリ使用状況                                            │
│                                                              │
│  【キャパシティプランニング】                                │
│  - データ増加傾向                                            │
│  - トランザクション数の推移                                  │
│  - リソース使用率のトレンド                                  │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 1.2 監視の設定

```ini
# postgresql.conf

# 統計情報収集を有効化
track_activities = on           # pg_stat_activity
track_counts = on               # pg_stat_*_tables, pg_stat_*_indexes
track_io_timing = on            # I/O時間計測 (若干のオーバーヘッド)
track_wal_io_timing = on        # WAL I/O時間計測
track_functions = all           # 関数の統計 (none, pl, all)

# 統計情報の更新頻度
stats_fetch_consistency = cache # none, cache, snapshot
```

---

## 2. 統計情報ビュー (pg_stat_*)

### 2.1 主要な統計情報ビュー

```
┌──────────────────────────────────────────────────────────────┐
│                    主要な統計情報ビュー                       │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  【アクティビティ】                                          │
│  pg_stat_activity      : 現在の接続とクエリ                  │
│  pg_stat_progress_*    : 長時間操作の進捗                    │
│                                                              │
│  【データベース】                                            │
│  pg_stat_database      : データベース全体の統計              │
│  pg_stat_database_conflicts : スタンバイでの競合             │
│                                                              │
│  【テーブル/インデックス】                                   │
│  pg_stat_user_tables   : テーブルの統計                      │
│  pg_stat_user_indexes  : インデックスの統計                  │
│  pg_statio_user_tables : テーブルのI/O統計                   │
│  pg_statio_user_indexes: インデックスのI/O統計               │
│                                                              │
│  【バックグラウンドプロセス】                                │
│  pg_stat_bgwriter      : バックグラウンドライター            │
│  pg_stat_wal           : WAL統計                             │
│  pg_stat_archiver      : アーカイバ                          │
│                                                              │
│  【レプリケーション】                                        │
│  pg_stat_replication   : レプリケーション状態                │
│  pg_stat_wal_receiver  : WALレシーバー (スタンバイ)          │
│  pg_replication_slots  : レプリケーションスロット            │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 2.2 pg_stat_activity

```sql
-- 現在のアクティブセッション
SELECT
    pid,
    usename,
    datname,
    client_addr,
    state,
    wait_event_type,
    wait_event,
    query_start,
    now() - query_start AS query_duration,
    LEFT(query, 100) AS query
FROM pg_stat_activity
WHERE state != 'idle'
ORDER BY query_start;

-- 状態別の接続数
SELECT
    state,
    count(*) AS count
FROM pg_stat_activity
GROUP BY state;

-- 長時間実行中のクエリ
SELECT
    pid,
    usename,
    datname,
    now() - query_start AS duration,
    state,
    query
FROM pg_stat_activity
WHERE state = 'active'
  AND now() - query_start > interval '1 minute'
ORDER BY query_start;

-- ロック待ちのセッション
SELECT
    pid,
    usename,
    wait_event_type,
    wait_event,
    query
FROM pg_stat_activity
WHERE wait_event_type = 'Lock';
```

### 2.3 pg_stat_database

```sql
-- データベース統計
SELECT
    datname,
    numbackends AS connections,
    xact_commit AS commits,
    xact_rollback AS rollbacks,
    blks_read,
    blks_hit,
    ROUND(100.0 * blks_hit / NULLIF(blks_hit + blks_read, 0), 2) AS cache_hit_ratio,
    tup_returned,
    tup_fetched,
    tup_inserted,
    tup_updated,
    tup_deleted,
    conflicts,
    deadlocks,
    temp_files,
    pg_size_pretty(temp_bytes) AS temp_bytes,
    stats_reset
FROM pg_stat_database
WHERE datname NOT LIKE 'template%';

-- トランザクション数 (コミット + ロールバック)
SELECT
    datname,
    xact_commit + xact_rollback AS total_transactions,
    xact_commit,
    xact_rollback,
    ROUND(100.0 * xact_rollback / NULLIF(xact_commit + xact_rollback, 0), 2) AS rollback_ratio
FROM pg_stat_database
WHERE datname = current_database();
```

### 2.4 pg_stat_user_tables

```sql
-- テーブル統計
SELECT
    schemaname,
    relname,
    seq_scan,
    seq_tup_read,
    idx_scan,
    idx_tup_fetch,
    n_tup_ins,
    n_tup_upd,
    n_tup_del,
    n_live_tup,
    n_dead_tup,
    ROUND(100.0 * n_dead_tup / NULLIF(n_live_tup + n_dead_tup, 0), 2) AS dead_tuple_ratio,
    last_vacuum,
    last_autovacuum,
    last_analyze,
    last_autoanalyze
FROM pg_stat_user_tables
ORDER BY n_dead_tup DESC
LIMIT 20;

-- シーケンシャルスキャンが多いテーブル (インデックス追加の候補)
SELECT
    schemaname,
    relname,
    seq_scan,
    idx_scan,
    CASE WHEN idx_scan > 0 THEN
        ROUND(seq_scan::numeric / idx_scan, 2)
    ELSE
        seq_scan
    END AS seq_to_idx_ratio,
    pg_size_pretty(pg_relation_size(schemaname || '.' || relname)) AS size
FROM pg_stat_user_tables
WHERE seq_scan > 100
ORDER BY seq_scan DESC
LIMIT 20;

-- 最近VACUUMされていないテーブル
SELECT
    schemaname,
    relname,
    n_dead_tup,
    last_vacuum,
    last_autovacuum,
    GREATEST(last_vacuum, last_autovacuum) AS last_vacuumed
FROM pg_stat_user_tables
WHERE n_dead_tup > 1000
ORDER BY GREATEST(last_vacuum, last_autovacuum) NULLS FIRST
LIMIT 20;
```

### 2.5 pg_stat_user_indexes

```sql
-- インデックス統計
SELECT
    schemaname,
    relname,
    indexrelname,
    idx_scan,
    idx_tup_read,
    idx_tup_fetch,
    pg_size_pretty(pg_relation_size(indexrelid)) AS index_size
FROM pg_stat_user_indexes
ORDER BY idx_scan DESC
LIMIT 20;

-- 使用されていないインデックス (削除候補)
SELECT
    schemaname,
    relname,
    indexrelname,
    idx_scan,
    pg_size_pretty(pg_relation_size(indexrelid)) AS index_size
FROM pg_stat_user_indexes
WHERE idx_scan = 0
  AND indexrelname NOT LIKE '%_pkey'  -- 主キーは除外
ORDER BY pg_relation_size(indexrelid) DESC;
```

### 2.6 pg_statio_user_tables

```sql
-- テーブルのI/O統計
SELECT
    schemaname,
    relname,
    heap_blks_read,
    heap_blks_hit,
    ROUND(100.0 * heap_blks_hit / NULLIF(heap_blks_hit + heap_blks_read, 0), 2) AS heap_hit_ratio,
    idx_blks_read,
    idx_blks_hit,
    ROUND(100.0 * idx_blks_hit / NULLIF(idx_blks_hit + idx_blks_read, 0), 2) AS idx_hit_ratio,
    toast_blks_read,
    toast_blks_hit
FROM pg_statio_user_tables
ORDER BY heap_blks_read DESC
LIMIT 20;
```

---

## 3. パフォーマンス監視

### 3.1 キャッシュヒット率

```sql
-- データベース全体のキャッシュヒット率
SELECT
    datname,
    blks_read,
    blks_hit,
    ROUND(100.0 * blks_hit / NULLIF(blks_hit + blks_read, 0), 2) AS cache_hit_ratio
FROM pg_stat_database
WHERE datname = current_database();

-- 目安:
-- 99% 以上: 良好
-- 95-99%: 許容範囲
-- 95% 未満: shared_buffers の増加を検討

-- テーブル別のキャッシュヒット率
SELECT
    schemaname,
    relname,
    heap_blks_read + idx_blks_read AS total_reads,
    heap_blks_hit + idx_blks_hit AS total_hits,
    ROUND(100.0 * (heap_blks_hit + idx_blks_hit) /
        NULLIF(heap_blks_hit + idx_blks_hit + heap_blks_read + idx_blks_read, 0), 2) AS hit_ratio
FROM pg_statio_user_tables
WHERE heap_blks_read + idx_blks_read > 0
ORDER BY total_reads DESC
LIMIT 20;
```

### 3.2 ロック監視

```sql
-- 現在のロック状況
SELECT
    pg_locks.pid,
    pg_stat_activity.usename,
    pg_locks.locktype,
    pg_locks.mode,
    pg_locks.granted,
    pg_class.relname,
    pg_stat_activity.query
FROM pg_locks
LEFT JOIN pg_class ON pg_locks.relation = pg_class.oid
LEFT JOIN pg_stat_activity ON pg_locks.pid = pg_stat_activity.pid
WHERE NOT pg_locks.granted
ORDER BY pg_locks.pid;

-- ロックの競合 (誰が誰を待たせているか)
SELECT
    blocked.pid AS blocked_pid,
    blocked.usename AS blocked_user,
    blocking.pid AS blocking_pid,
    blocking.usename AS blocking_user,
    blocked.query AS blocked_query,
    blocking.query AS blocking_query,
    blocked.wait_event
FROM pg_stat_activity AS blocked
JOIN pg_stat_activity AS blocking
    ON blocking.pid = ANY(pg_blocking_pids(blocked.pid))
WHERE blocked.wait_event_type = 'Lock';

-- デッドロック数
SELECT deadlocks FROM pg_stat_database WHERE datname = current_database();
```

### 3.3 チェックポイント監視

```sql
-- チェックポイント統計
SELECT
    checkpoints_timed,      -- 時間経過によるチェックポイント
    checkpoints_req,        -- 要求によるチェックポイント (WAL満杯など)
    checkpoint_write_time,  -- 書き込み時間 (ms)
    checkpoint_sync_time,   -- 同期時間 (ms)
    buffers_checkpoint,     -- チェックポイントで書き込まれたバッファ
    buffers_clean,          -- バックグラウンドライターで書き込まれたバッファ
    buffers_backend,        -- バックエンドで直接書き込まれたバッファ
    buffers_alloc,          -- 割り当てられたバッファ
    stats_reset
FROM pg_stat_bgwriter;

-- buffers_backend が多い場合:
-- → shared_buffers が不足している可能性
-- → checkpoint_completion_target を調整
```

### 3.4 WAL統計

```sql
-- WAL統計 (PostgreSQL 14+)
SELECT
    wal_records,
    wal_fpi,            -- フルページイメージ数
    wal_bytes,
    pg_size_pretty(wal_bytes) AS wal_size,
    wal_buffers_full,
    wal_write,
    wal_sync,
    wal_write_time,     -- 書き込み時間 (ms)
    wal_sync_time,      -- 同期時間 (ms)
    stats_reset
FROM pg_stat_wal;
```

---

## 4. リソース監視

### 4.1 接続数監視

```sql
-- 現在の接続数
SELECT count(*) AS total_connections FROM pg_stat_activity;

-- 最大接続数
SHOW max_connections;

-- 接続数の割合
SELECT
    count(*) AS current_connections,
    current_setting('max_connections')::int AS max_connections,
    ROUND(100.0 * count(*) / current_setting('max_connections')::int, 2) AS usage_percent
FROM pg_stat_activity;

-- ユーザー別接続数
SELECT
    usename,
    count(*) AS connections
FROM pg_stat_activity
GROUP BY usename
ORDER BY connections DESC;

-- データベース別接続数
SELECT
    datname,
    count(*) AS connections
FROM pg_stat_activity
GROUP BY datname
ORDER BY connections DESC;

-- クライアントIP別接続数
SELECT
    client_addr,
    count(*) AS connections
FROM pg_stat_activity
WHERE client_addr IS NOT NULL
GROUP BY client_addr
ORDER BY connections DESC;
```

### 4.2 ディスク使用量監視

```sql
-- データベースサイズ
SELECT
    datname,
    pg_size_pretty(pg_database_size(datname)) AS size
FROM pg_database
ORDER BY pg_database_size(datname) DESC;

-- テーブルサイズ (TOP 20)
SELECT
    schemaname,
    relname,
    pg_size_pretty(pg_total_relation_size(schemaname || '.' || relname)) AS total_size,
    pg_size_pretty(pg_relation_size(schemaname || '.' || relname)) AS table_size,
    pg_size_pretty(pg_indexes_size(schemaname || '.' || relname)) AS indexes_size
FROM pg_stat_user_tables
ORDER BY pg_total_relation_size(schemaname || '.' || relname) DESC
LIMIT 20;

-- テーブルスペース使用量
SELECT
    spcname,
    pg_size_pretty(pg_tablespace_size(spcname)) AS size
FROM pg_tablespace;

-- WALディレクトリのサイズ
SELECT pg_size_pretty(sum(size)) AS wal_size
FROM pg_ls_waldir();
```

### 4.3 テーブル肥大化監視

```sql
-- 肥大化したテーブルの検出 (pgstattuple拡張が必要)
CREATE EXTENSION IF NOT EXISTS pgstattuple;

SELECT
    schemaname || '.' || relname AS table_name,
    pg_size_pretty(pg_relation_size(schemaname || '.' || relname)) AS size,
    n_live_tup,
    n_dead_tup,
    ROUND(100.0 * n_dead_tup / NULLIF(n_live_tup + n_dead_tup, 0), 2) AS dead_tuple_percent
FROM pg_stat_user_tables
WHERE n_dead_tup > 10000
ORDER BY n_dead_tup DESC;

-- 詳細な肥大化情報 (大きなテーブルでは時間がかかる)
SELECT * FROM pgstattuple('your_table_name');
```

### 4.4 レプリケーション監視

```sql
-- レプリケーション状態 (プライマリで実行)
SELECT
    client_addr,
    state,
    sent_lsn,
    write_lsn,
    flush_lsn,
    replay_lsn,
    pg_wal_lsn_diff(sent_lsn, replay_lsn) AS replay_lag_bytes,
    pg_size_pretty(pg_wal_lsn_diff(sent_lsn, replay_lsn)) AS replay_lag,
    sync_state,
    reply_time
FROM pg_stat_replication;

-- レプリケーションスロット
SELECT
    slot_name,
    slot_type,
    active,
    pg_size_pretty(pg_wal_lsn_diff(pg_current_wal_lsn(), restart_lsn)) AS retained_wal
FROM pg_replication_slots;

-- WALレシーバー状態 (スタンバイで実行)
SELECT
    status,
    received_lsn,
    latest_end_lsn,
    last_msg_send_time,
    last_msg_receipt_time,
    conninfo
FROM pg_stat_wal_receiver;

-- レプリケーションラグ (秒、スタンバイで実行)
SELECT
    CASE
        WHEN pg_last_wal_receive_lsn() = pg_last_wal_replay_lsn() THEN 0
        ELSE EXTRACT(EPOCH FROM (now() - pg_last_xact_replay_timestamp()))
    END AS lag_seconds;
```

---

## 5. pg_stat_statements

### 5.1 セットアップ

```ini
# postgresql.conf
shared_preload_libraries = 'pg_stat_statements'

pg_stat_statements.max = 10000
pg_stat_statements.track = all
pg_stat_statements.track_utility = on
pg_stat_statements.track_planning = on
```

```sql
-- 拡張の作成
CREATE EXTENSION pg_stat_statements;
```

### 5.2 スロークエリの分析

```sql
-- 総実行時間が長いクエリ TOP 20
SELECT
    queryid,
    calls,
    ROUND(total_exec_time::numeric, 2) AS total_time_ms,
    ROUND(mean_exec_time::numeric, 2) AS mean_time_ms,
    ROUND(stddev_exec_time::numeric, 2) AS stddev_time_ms,
    rows,
    ROUND(100.0 * shared_blks_hit / NULLIF(shared_blks_hit + shared_blks_read, 0), 2) AS hit_ratio,
    LEFT(query, 100) AS query
FROM pg_stat_statements
ORDER BY total_exec_time DESC
LIMIT 20;

-- 平均実行時間が長いクエリ TOP 20
SELECT
    queryid,
    calls,
    ROUND(mean_exec_time::numeric, 2) AS mean_time_ms,
    ROUND(max_exec_time::numeric, 2) AS max_time_ms,
    rows / NULLIF(calls, 0) AS avg_rows,
    LEFT(query, 100) AS query
FROM pg_stat_statements
WHERE calls > 100  -- ある程度呼ばれているクエリ
ORDER BY mean_exec_time DESC
LIMIT 20;

-- 呼び出し回数が多いクエリ TOP 20
SELECT
    queryid,
    calls,
    ROUND(total_exec_time::numeric, 2) AS total_time_ms,
    ROUND(mean_exec_time::numeric, 2) AS mean_time_ms,
    LEFT(query, 100) AS query
FROM pg_stat_statements
ORDER BY calls DESC
LIMIT 20;
```

### 5.3 I/O集約的なクエリ

```sql
-- I/Oが多いクエリ
SELECT
    queryid,
    calls,
    shared_blks_read,
    shared_blks_hit,
    shared_blks_dirtied,
    shared_blks_written,
    ROUND(100.0 * shared_blks_hit / NULLIF(shared_blks_hit + shared_blks_read, 0), 2) AS hit_ratio,
    LEFT(query, 100) AS query
FROM pg_stat_statements
WHERE shared_blks_read > 1000
ORDER BY shared_blks_read DESC
LIMIT 20;

-- 一時ファイルを使用しているクエリ
SELECT
    queryid,
    calls,
    temp_blks_read,
    temp_blks_written,
    LEFT(query, 100) AS query
FROM pg_stat_statements
WHERE temp_blks_written > 0
ORDER BY temp_blks_written DESC
LIMIT 20;
```

### 5.4 統計情報のリセット

```sql
-- 全統計をリセット
SELECT pg_stat_statements_reset();

-- 特定のクエリの統計をリセット (PostgreSQL 14+)
SELECT pg_stat_statements_reset(userid, dbid, queryid);
```

---

## 6. アラート設計

### 6.1 アラートの優先度

```
┌──────────────────────────────────────────────────────────────┐
│                    アラート優先度の設計                       │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  【Critical - 即時対応必要】                                 │
│  - PostgreSQLダウン                                          │
│  - レプリケーション停止                                      │
│  - ディスク使用率 > 95%                                      │
│  - 接続数 > 最大接続数の95%                                  │
│  - デッドロック発生                                          │
│                                                              │
│  【Warning - 数時間以内に対応】                              │
│  - ディスク使用率 > 80%                                      │
│  - 接続数 > 最大接続数の80%                                  │
│  - レプリケーションラグ > 30秒                               │
│  - キャッシュヒット率 < 95%                                  │
│  - 長時間クエリ > 10分                                       │
│  - Autovacuum が24時間以上未実行                             │
│                                                              │
│  【Info - 定期確認】                                         │
│  - ディスク使用率 > 70%                                      │
│  - 接続数 > 最大接続数の60%                                  │
│  - 未使用インデックスの存在                                  │
│  - 肥大化テーブルの存在                                      │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 6.2 監視クエリ集

```sql
-- [Critical] 接続可否チェック
SELECT 1;

-- [Critical] 接続数の確認
SELECT
    CASE
        WHEN count(*) > current_setting('max_connections')::int * 0.95 THEN 'CRITICAL'
        WHEN count(*) > current_setting('max_connections')::int * 0.80 THEN 'WARNING'
        ELSE 'OK'
    END AS status,
    count(*) AS current_connections,
    current_setting('max_connections') AS max_connections
FROM pg_stat_activity;

-- [Critical] レプリケーション状態
SELECT
    CASE
        WHEN count(*) = 0 THEN 'CRITICAL'  -- レプリカなし
        WHEN max(pg_wal_lsn_diff(sent_lsn, replay_lsn)) > 1073741824 THEN 'CRITICAL'  -- 1GB以上遅延
        WHEN max(pg_wal_lsn_diff(sent_lsn, replay_lsn)) > 104857600 THEN 'WARNING'  -- 100MB以上遅延
        ELSE 'OK'
    END AS status,
    count(*) AS replica_count,
    max(pg_wal_lsn_diff(sent_lsn, replay_lsn)) AS max_lag_bytes
FROM pg_stat_replication;

-- [Warning] キャッシュヒット率
SELECT
    CASE
        WHEN ROUND(100.0 * blks_hit / NULLIF(blks_hit + blks_read, 0), 2) < 95 THEN 'WARNING'
        ELSE 'OK'
    END AS status,
    ROUND(100.0 * blks_hit / NULLIF(blks_hit + blks_read, 0), 2) AS cache_hit_ratio
FROM pg_stat_database
WHERE datname = current_database();

-- [Warning] 長時間実行クエリ
SELECT
    count(*) AS long_running_queries,
    CASE
        WHEN count(*) > 0 THEN 'WARNING'
        ELSE 'OK'
    END AS status
FROM pg_stat_activity
WHERE state = 'active'
  AND now() - query_start > interval '10 minutes'
  AND query NOT LIKE '%pg_stat_activity%';

-- [Warning] デッドロック検出
SELECT
    CASE
        WHEN deadlocks > 0 THEN 'WARNING'
        ELSE 'OK'
    END AS status,
    deadlocks
FROM pg_stat_database
WHERE datname = current_database();

-- [Warning] Autovacuum 確認
SELECT
    schemaname || '.' || relname AS table_name,
    n_dead_tup,
    last_autovacuum,
    CASE
        WHEN last_autovacuum IS NULL THEN 'NEVER'
        WHEN now() - last_autovacuum > interval '24 hours' THEN 'WARNING'
        ELSE 'OK'
    END AS status
FROM pg_stat_user_tables
WHERE n_dead_tup > 10000
ORDER BY n_dead_tup DESC
LIMIT 10;
```

### 6.3 アラート設定例 (Prometheus形式)

```yaml
groups:
  - name: postgresql
    rules:
      - alert: PostgreSQLDown
        expr: pg_up == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "PostgreSQL is down"

      - alert: PostgreSQLTooManyConnections
        expr: pg_stat_activity_count / pg_settings_max_connections > 0.8
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "PostgreSQL connections above 80%"

      - alert: PostgreSQLReplicationLag
        expr: pg_replication_lag_seconds > 30
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "PostgreSQL replication lag is high"

      - alert: PostgreSQLDiskSpaceLow
        expr: pg_database_size_bytes / node_filesystem_size_bytes > 0.8
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "PostgreSQL disk space usage above 80%"

      - alert: PostgreSQLCacheHitRatioLow
        expr: pg_stat_database_blks_hit / (pg_stat_database_blks_hit + pg_stat_database_blks_read) < 0.95
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "PostgreSQL cache hit ratio below 95%"
```

---

## 7. 監視ツールとの連携

### 7.1 postgres_exporter (Prometheus)

```bash
# インストール
wget https://github.com/prometheus-community/postgres_exporter/releases/download/v0.15.0/postgres_exporter-0.15.0.linux-amd64.tar.gz
tar xvf postgres_exporter-0.15.0.linux-amd64.tar.gz

# 環境変数設定
export DATA_SOURCE_NAME="postgresql://postgres:password@localhost:5432/postgres?sslmode=disable"

# 起動
./postgres_exporter
```

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'postgresql'
    static_configs:
      - targets: ['localhost:9187']
```

### 7.2 カスタムメトリクス

```yaml
# queries.yaml (postgres_exporter用)
pg_replication:
  query: |
    SELECT
      client_addr,
      pg_wal_lsn_diff(sent_lsn, replay_lsn) as replication_lag_bytes
    FROM pg_stat_replication
  metrics:
    - client_addr:
        usage: "LABEL"
        description: "Client address"
    - replication_lag_bytes:
        usage: "GAUGE"
        description: "Replication lag in bytes"

pg_long_running_queries:
  query: |
    SELECT
      count(*) as count
    FROM pg_stat_activity
    WHERE state = 'active'
      AND now() - query_start > interval '5 minutes'
  metrics:
    - count:
        usage: "GAUGE"
        description: "Number of long running queries"
```

### 7.3 Grafanaダッシュボード

```
┌──────────────────────────────────────────────────────────────┐
│                 推奨Grafanaダッシュボード                     │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  【概要パネル】                                              │
│  - PostgreSQLバージョン                                      │
│  - 稼働時間                                                  │
│  - データベースサイズ                                        │
│  - 接続数/最大接続数                                         │
│                                                              │
│  【パフォーマンスパネル】                                    │
│  - トランザクション/秒                                       │
│  - クエリ/秒                                                 │
│  - キャッシュヒット率                                        │
│  - 平均クエリ時間                                            │
│                                                              │
│  【リソースパネル】                                          │
│  - ディスク使用量                                            │
│  - WALサイズ                                                 │
│  - 一時ファイル使用量                                        │
│  - 接続数推移                                                │
│                                                              │
│  【レプリケーションパネル】                                  │
│  - レプリケーションラグ                                      │
│  - WAL送信/受信量                                            │
│  - スロット使用状況                                          │
│                                                              │
│  【テーブルパネル】                                          │
│  - TOP 10 大きいテーブル                                     │
│  - TOP 10 アクセス数                                         │
│  - 肥大化テーブル                                            │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 7.4 監視スクリプト例

```bash
#!/bin/bash
# pg_health_check.sh

PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
PGUSER="${PGUSER:-postgres}"
PGDATABASE="${PGDATABASE:-postgres}"

export PGPASSWORD="${PGPASSWORD}"

# 接続チェック
if ! psql -h $PGHOST -p $PGPORT -U $PGUSER -d $PGDATABASE -c "SELECT 1" > /dev/null 2>&1; then
    echo "CRITICAL: Cannot connect to PostgreSQL"
    exit 2
fi

# 接続数チェック
CONN_RESULT=$(psql -h $PGHOST -p $PGPORT -U $PGUSER -d $PGDATABASE -t -c "
SELECT
    count(*) AS current,
    current_setting('max_connections')::int AS max,
    ROUND(100.0 * count(*) / current_setting('max_connections')::int) AS pct
FROM pg_stat_activity;
")

CONN_PCT=$(echo $CONN_RESULT | awk '{print $3}')
if [ "$CONN_PCT" -gt 95 ]; then
    echo "CRITICAL: Connection usage at ${CONN_PCT}%"
    exit 2
elif [ "$CONN_PCT" -gt 80 ]; then
    echo "WARNING: Connection usage at ${CONN_PCT}%"
    exit 1
fi

# レプリケーションチェック
REPL_LAG=$(psql -h $PGHOST -p $PGPORT -U $PGUSER -d $PGDATABASE -t -c "
SELECT COALESCE(max(pg_wal_lsn_diff(sent_lsn, replay_lsn)), 0)
FROM pg_stat_replication;
")

if [ "$REPL_LAG" -gt 1073741824 ]; then  # 1GB
    echo "CRITICAL: Replication lag is ${REPL_LAG} bytes"
    exit 2
elif [ "$REPL_LAG" -gt 104857600 ]; then  # 100MB
    echo "WARNING: Replication lag is ${REPL_LAG} bytes"
    exit 1
fi

echo "OK: PostgreSQL is healthy"
exit 0
```

---

## 参考リンク

- [PostgreSQL公式ドキュメント - 統計情報コレクター](https://www.postgresql.org/docs/current/monitoring-stats.html)
- [PostgreSQL公式ドキュメント - pg_stat_statements](https://www.postgresql.org/docs/current/pgstatstatements.html)
- [postgres_exporter](https://github.com/prometheus-community/postgres_exporter)
- [pgMonitor](https://github.com/CrunchyData/pgmonitor)
- [pg_activity](https://github.com/dalibo/pg_activity)
