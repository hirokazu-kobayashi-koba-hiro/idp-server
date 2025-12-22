# PostgreSQL メンテナンスガイド

このドキュメントでは、PostgreSQLの定期メンテナンスとテーブル肥大化対策を解説します。

---

## 目次

1. [メンテナンスの概要](#1-メンテナンスの概要)
2. [VACUUM](#2-vacuum)
3. [ANALYZE](#3-analyze)
4. [REINDEX](#4-reindex)
5. [テーブル肥大化対策](#5-テーブル肥大化対策)
6. [Autovacuumの最適化](#6-autovacuumの最適化)
7. [メンテナンススケジュール](#7-メンテナンススケジュール)

---

## 1. メンテナンスの概要

### 1.1 なぜメンテナンスが必要か

```
┌──────────────────────────────────────────────────────────────┐
│                  メンテナンスが必要な理由                     │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  【MVCCによる死んだ行の蓄積】                                │
│                                                              │
│  UPDATE文の動作:                                             │
│  1. 既存行を「削除済み」としてマーク (xmaxを設定)            │
│  2. 新しい行を挿入                                           │
│  → 古い行が「死んだ行 (dead tuple)」として残る              │
│                                                              │
│  DELETE文の動作:                                             │
│  1. 行を「削除済み」としてマーク                             │
│  → 実際には削除されず、死んだ行として残る                   │
│                                                              │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  テーブル                                              │  │
│  │  ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐           │  │
│  │  │Live │ │Dead │ │Live │ │Dead │ │Dead │ ...       │  │
│  │  │Tuple│ │Tuple│ │Tuple│ │Tuple│ │Tuple│           │  │
│  │  └─────┘ └─────┘ └─────┘ └─────┘ └─────┘           │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                              │
│  【問題】                                                    │
│  - ディスク使用量の増加                                      │
│  - テーブルスキャンの効率低下                                │
│  - インデックスの肥大化                                      │
│  - トランザクションIDの周回問題                              │
│                                                              │
│  【解決策】                                                  │
│  - VACUUM: 死んだ行の領域を再利用可能にする                  │
│  - ANALYZE: 統計情報を更新してクエリプランを最適化           │
│  - REINDEX: 肥大化したインデックスを再構築                   │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 1.2 メンテナンス作業の一覧

```
┌──────────────────────────────────────────────────────────────┐
│                    メンテナンス作業一覧                       │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌────────────────┬────────────────────────────────────────┐│
│  │ 作業           │ 目的                                   ││
│  ├────────────────┼────────────────────────────────────────┤│
│  │ VACUUM         │ 死んだ行の領域を再利用可能にする       ││
│  │ VACUUM FULL    │ テーブルを完全に再構築 (排他ロック)    ││
│  │ ANALYZE        │ 統計情報を更新                         ││
│  │ REINDEX        │ インデックスを再構築                   ││
│  │ CLUSTER        │ インデックス順にテーブルを再配置       ││
│  │ pg_repack      │ オンラインでテーブル/インデックス再構築││
│  └────────────────┴────────────────────────────────────────┘│
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## 2. VACUUM

### 2.1 VACUUMの種類

```
┌──────────────────────────────────────────────────────────────┐
│                      VACUUMの種類                             │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  【VACUUM (通常)】                                           │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ - 死んだ行を「再利用可能」としてマーク                │   │
│  │ - ディスク容量はOSに返さない                          │   │
│  │ - テーブルをロックしない (同時に読み書き可能)         │   │
│  │ - 比較的高速                                          │   │
│  │ - 日常的に使用 (Autovacuumで自動実行)                 │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  【VACUUM FULL】                                             │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ - テーブルを完全に再構築                              │   │
│  │ - ディスク容量をOSに返す                              │   │
│  │ - 排他ロック (ACCESS EXCLUSIVE) が必要               │   │
│  │ - 作業中はアクセス不可                                │   │
│  │ - 時間がかかる                                        │   │
│  │ - 本当に必要な場合のみ使用                            │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  【VACUUM ANALYZE】                                          │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ - VACUUM + ANALYZE を同時実行                         │   │
│  │ - 日常的に推奨                                        │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 2.2 VACUUMの実行

```sql
-- 全テーブルをVACUUM
VACUUM;

-- 特定テーブルをVACUUM
VACUUM users;

-- VACUUM + ANALYZE
VACUUM ANALYZE users;

-- 詳細出力付き
VACUUM VERBOSE users;

-- VACUUM FULL (排他ロック、注意が必要)
VACUUM FULL users;

-- 並列VACUUM (PostgreSQL 13+)
VACUUM (PARALLEL 4) users;
```

### 2.3 VACUUMのオプション

```sql
-- PostgreSQL 12+ の構文
VACUUM (
    FULL false,           -- 通常VACUUM
    FREEZE false,         -- 強制的にFreeze
    VERBOSE true,         -- 詳細出力
    ANALYZE true,         -- 統計情報も更新
    DISABLE_PAGE_SKIPPING false,  -- 全ページをスキャン
    SKIP_LOCKED false,    -- ロックされたテーブルをスキップ
    INDEX_CLEANUP auto,   -- インデックスのクリーンアップ
    PROCESS_TOAST true,   -- TOASTテーブルも処理
    TRUNCATE true,        -- 末尾の空ページを切り詰め
    PARALLEL 4            -- 並列ワーカー数
) users;
```

### 2.4 VACUUMの進捗確認

```sql
-- VACUUM の進捗状況
SELECT
    p.pid,
    p.datname,
    p.relid::regclass AS table_name,
    p.phase,
    p.heap_blks_total,
    p.heap_blks_scanned,
    p.heap_blks_vacuumed,
    ROUND(100.0 * p.heap_blks_vacuumed / NULLIF(p.heap_blks_total, 0), 2) AS progress_pct,
    p.index_vacuum_count,
    p.max_dead_tuples,
    p.num_dead_tuples
FROM pg_stat_progress_vacuum p;

-- Autovacuumの実行状況
SELECT
    pid,
    datname,
    relid::regclass AS table_name,
    phase,
    heap_blks_total,
    heap_blks_scanned
FROM pg_stat_progress_vacuum
WHERE datname = current_database();
```

### 2.5 Freezeとトランザクション周回問題

```
┌──────────────────────────────────────────────────────────────┐
│               トランザクションID周回問題                      │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  【問題】                                                    │
│  - トランザクションID (XID) は32ビット (約42億)             │
│  - 使い切ると周回する                                        │
│  - 古いXIDと新しいXIDの区別がつかなくなる                   │
│  → データが「未来」のデータとして見えなくなる               │
│                                                              │
│  【対策: Freeze処理】                                        │
│  - VACUUMが古いXIDを「FrozenXID」に置き換え                 │
│  - FrozenXIDは「全トランザクションから見える」特別なID       │
│                                                              │
│  【モニタリング】                                            │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ SELECT datname, age(datfrozenxid) FROM pg_database;  │   │
│  │                                                      │   │
│  │ age() の値が大きいほど危険                           │   │
│  │ - 10億未満: 正常                                     │   │
│  │ - 10億-15億: 注意                                    │   │
│  │ - 15億-20億: 警告、VACUUM FREEZEを検討              │   │
│  │ - 20億に近い: 緊急対応が必要                         │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

```sql
-- データベースのフリーズ年齢を確認
SELECT
    datname,
    age(datfrozenxid) AS age,
    datfrozenxid
FROM pg_database
ORDER BY age DESC;

-- テーブルのフリーズ年齢を確認
SELECT
    schemaname,
    relname,
    age(relfrozenxid) AS age,
    relfrozenxid
FROM pg_stat_user_tables
ORDER BY age DESC
LIMIT 20;

-- 強制的にFreeze
VACUUM FREEZE users;
VACUUM (FREEZE) users;
```

---

## 3. ANALYZE

### 3.1 ANALYZEの役割

```
┌──────────────────────────────────────────────────────────────┐
│                     ANALYZEの役割                             │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  【目的】                                                    │
│  テーブルの統計情報を収集し、                                │
│  プランナーが最適な実行計画を立てられるようにする            │
│                                                              │
│  【収集される情報】                                          │
│  - 行数 (reltuples)                                         │
│  - ページ数 (relpages)                                      │
│  - NULL値の割合 (null_frac)                                 │
│  - ユニーク値の数 (n_distinct)                              │
│  - 最頻値 (most_common_vals)                                │
│  - ヒストグラム (histogram_bounds)                          │
│  - 相関 (correlation)                                       │
│                                                              │
│  【いつ実行すべきか】                                        │
│  - 大量のINSERT/UPDATE/DELETE後                             │
│  - テーブル作成後                                            │
│  - インデックス作成後                                        │
│  - クエリの実行計画が最適でない場合                          │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 3.2 ANALYZEの実行

```sql
-- 全テーブルをANALYZE
ANALYZE;

-- 特定テーブルをANALYZE
ANALYZE users;

-- 特定カラムのみ
ANALYZE users (email, status);

-- 詳細出力付き
ANALYZE VERBOSE users;

-- VACUUM ANALYZE (推奨)
VACUUM ANALYZE users;
```

### 3.3 統計情報の確認と調整

```sql
-- テーブルの基本統計
SELECT
    relname,
    reltuples::bigint AS estimated_rows,
    relpages
FROM pg_class
WHERE relname = 'users';

-- カラムの統計情報
SELECT
    attname,
    null_frac,
    n_distinct,
    most_common_vals,
    most_common_freqs,
    histogram_bounds
FROM pg_stats
WHERE tablename = 'users' AND attname = 'status';

-- 統計サンプル数の変更 (より正確な統計が必要な場合)
ALTER TABLE users ALTER COLUMN status SET STATISTICS 500;
-- デフォルト: 100, 最大: 10000

ANALYZE users;
```

### 3.4 ANALYZEの進捗確認

```sql
-- ANALYZE の進捗状況 (PostgreSQL 13+)
SELECT
    pid,
    datname,
    relid::regclass AS table_name,
    phase,
    sample_blks_total,
    sample_blks_scanned,
    ROUND(100.0 * sample_blks_scanned / NULLIF(sample_blks_total, 0), 2) AS progress_pct
FROM pg_stat_progress_analyze;
```

---

## 4. REINDEX

### 4.1 REINDEXが必要な場合

```
┌──────────────────────────────────────────────────────────────┐
│                  REINDEXが必要な場合                          │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  【インデックスの肥大化】                                    │
│  - 大量のUPDATE/DELETEでインデックスが肥大化                │
│  - インデックスのサイズがテーブルより大きくなることも        │
│                                                              │
│  【インデックスの破損】                                      │
│  - ハードウェア障害                                          │
│  - ソフトウェアバグ                                          │
│  - amcheck で検出可能                                        │
│                                                              │
│  【パフォーマンス劣化】                                      │
│  - インデックススキャンが遅くなった                          │
│  - explain で高コストを示している                            │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 4.2 REINDEXの実行

```sql
-- 特定のインデックスを再構築
REINDEX INDEX idx_users_email;

-- テーブルの全インデックスを再構築
REINDEX TABLE users;

-- スキーマの全インデックスを再構築
REINDEX SCHEMA public;

-- データベースの全インデックスを再構築
REINDEX DATABASE mydb;

-- システムカタログのインデックスも再構築
REINDEX SYSTEM mydb;
```

### 4.3 REINDEX CONCURRENTLY

```sql
-- オンラインでインデックス再構築 (PostgreSQL 12+)
-- 書き込みをブロックしない
REINDEX INDEX CONCURRENTLY idx_users_email;

REINDEX TABLE CONCURRENTLY users;

-- 注意点:
-- - 通常のREINDEXより時間がかかる
-- - 一時的にディスク容量が2倍必要
-- - 失敗すると無効なインデックスが残る可能性
```

### 4.4 インデックス肥大化の確認

```sql
-- インデックスサイズとテーブルサイズの比較
SELECT
    schemaname,
    relname AS table_name,
    indexrelname AS index_name,
    pg_size_pretty(pg_relation_size(indexrelid)) AS index_size,
    pg_size_pretty(pg_relation_size(relid)) AS table_size,
    ROUND(100.0 * pg_relation_size(indexrelid) / NULLIF(pg_relation_size(relid), 0), 2) AS idx_to_tbl_ratio
FROM pg_stat_user_indexes
ORDER BY pg_relation_size(indexrelid) DESC
LIMIT 20;

-- pgstattupleでインデックスの肥大化を確認
CREATE EXTENSION IF NOT EXISTS pgstattuple;

SELECT * FROM pgstatindex('idx_users_email');
-- leaf_fragmentation が高い場合は肥大化
```

---

## 5. テーブル肥大化対策

### 5.1 肥大化の検出

```sql
-- 死んだ行が多いテーブル
SELECT
    schemaname,
    relname,
    n_live_tup,
    n_dead_tup,
    ROUND(100.0 * n_dead_tup / NULLIF(n_live_tup + n_dead_tup, 0), 2) AS dead_ratio,
    pg_size_pretty(pg_total_relation_size(schemaname || '.' || relname)) AS total_size,
    last_vacuum,
    last_autovacuum
FROM pg_stat_user_tables
WHERE n_dead_tup > 10000
ORDER BY n_dead_tup DESC;

-- pgstattupleで詳細確認 (時間がかかる)
SELECT * FROM pgstattuple('users');

-- 結果の見方:
-- dead_tuple_percent: 死んだ行の割合
-- free_percent: 空き領域の割合
-- 両方が高い場合は肥大化
```

### 5.2 VACUUM FULL vs pg_repack

```
┌──────────────────────────────────────────────────────────────┐
│              VACUUM FULL vs pg_repack                        │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  【VACUUM FULL】                                             │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ ✅ 標準機能                                           │   │
│  │ ✅ 確実にディスク領域を解放                           │   │
│  │ ❌ 排他ロック (テーブルにアクセス不可)               │   │
│  │ ❌ 長時間かかる                                       │   │
│  │ ❌ 作業中は追加のディスク容量が必要                   │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  【pg_repack】                                               │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ ✅ オンラインで実行可能 (読み書き可能)               │   │
│  │ ✅ 最小限のロック時間                                 │   │
│  │ ✅ インデックスも同時に再構築                         │   │
│  │ ❌ 拡張機能のインストールが必要                       │   │
│  │ ❌ 一時的にディスク容量が2倍必要                      │   │
│  │ ❌ 主キーまたはユニーク制約が必要                     │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  【推奨】                                                    │
│  - 本番環境: pg_repack                                      │
│  - メンテナンスウィンドウあり: VACUUM FULL                  │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 5.3 pg_repackの使用

```bash
# インストール (RHEL系)
sudo dnf install pg_repack_16

# 基本的な使用法
pg_repack -d mydb -t users

# 全テーブル
pg_repack -d mydb

# インデックスのみ
pg_repack -d mydb -t users --only-indexes

# 並列実行
pg_repack -d mydb -t users -j 4

# ドライラン
pg_repack -d mydb -t users --dry-run
```

```sql
-- 拡張の作成
CREATE EXTENSION pg_repack;
```

### 5.4 CLUSTERコマンド

```sql
-- インデックス順にテーブルを物理的に再配置
-- 排他ロックが必要
CLUSTER users USING idx_users_created_at;

-- 以前にCLUSTERしたテーブルを再CLUSTER
CLUSTER users;

-- 全テーブル
CLUSTER;

-- 注意:
-- - INSERT後は順序が崩れる
-- - 定期的な再CLUSTERが必要
-- - pg_repack --order-by で代替可能
```

---

## 6. Autovacuumの最適化

### 6.1 Autovacuumの仕組み

```
┌──────────────────────────────────────────────────────────────┐
│                   Autovacuumの仕組み                          │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌───────────────────┐                                       │
│  │ Autovacuum        │ ← autovacuum_naptime (60秒) ごとに   │
│  │ Launcher          │   テーブルをチェック                  │
│  └─────────┬─────────┘                                       │
│            │ 条件を満たすテーブルに対して                    │
│            ▼                                                 │
│  ┌───────────────────┐ ┌───────────────────┐                │
│  │ Autovacuum        │ │ Autovacuum        │ ...            │
│  │ Worker 1          │ │ Worker 2          │                 │
│  └───────────────────┘ └───────────────────┘                │
│                                                              │
│  【VACUUM起動条件】                                          │
│  dead tuples > vacuum_threshold + vacuum_scale_factor × rows │
│  デフォルト: 50 + 0.2 × テーブル行数                         │
│                                                              │
│  例: 10,000行のテーブル                                      │
│      50 + 0.2 × 10,000 = 2,050                              │
│      → 2,050件の死んだ行でVACUUM起動                        │
│                                                              │
│  【ANALYZE起動条件】                                         │
│  modified rows > analyze_threshold + analyze_scale_factor × rows│
│  デフォルト: 50 + 0.1 × テーブル行数                         │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 6.2 Autovacuum設定

```ini
# postgresql.conf

# Autovacuumの有効化
autovacuum = on

# ワーカー数
autovacuum_max_workers = 3      # 同時実行数

# チェック間隔
autovacuum_naptime = 60         # 秒

# VACUUM起動閾値
autovacuum_vacuum_threshold = 50
autovacuum_vacuum_scale_factor = 0.2

# ANALYZE起動閾値
autovacuum_analyze_threshold = 50
autovacuum_analyze_scale_factor = 0.1

# コスト制限 (I/O負荷制御)
autovacuum_vacuum_cost_delay = 2ms
autovacuum_vacuum_cost_limit = 200

# Freeze設定
autovacuum_freeze_max_age = 200000000
vacuum_freeze_table_age = 150000000
vacuum_freeze_min_age = 50000000
```

### 6.3 テーブル単位の設定

```sql
-- 大量更新があるテーブル: より頻繁にVACUUM
ALTER TABLE high_update_table SET (
    autovacuum_vacuum_scale_factor = 0.05,  -- 5%で起動
    autovacuum_analyze_scale_factor = 0.02,
    autovacuum_vacuum_cost_limit = 1000     -- より積極的に
);

-- 大きなテーブル: 閾値を絶対値で設定
ALTER TABLE huge_table SET (
    autovacuum_vacuum_threshold = 10000,     -- 固定閾値
    autovacuum_vacuum_scale_factor = 0,      -- 割合を無効化
    autovacuum_vacuum_cost_delay = 0         -- 制限なし
);

-- ほとんど更新されないテーブル: VACUUM頻度を下げる
ALTER TABLE static_table SET (
    autovacuum_vacuum_scale_factor = 0.5,
    autovacuum_analyze_scale_factor = 0.5
);

-- 設定の確認
SELECT relname, reloptions
FROM pg_class
WHERE relname = 'high_update_table';
```

### 6.4 Autovacuumのモニタリング

```sql
-- Autovacuumの動作状況
SELECT
    schemaname,
    relname,
    last_vacuum,
    last_autovacuum,
    vacuum_count,
    autovacuum_count,
    n_dead_tup
FROM pg_stat_user_tables
WHERE last_autovacuum IS NOT NULL
ORDER BY last_autovacuum DESC;

-- 現在実行中のAutovacuum
SELECT
    pid,
    datname,
    relid::regclass AS table_name,
    phase,
    heap_blks_total,
    heap_blks_vacuumed,
    index_vacuum_count
FROM pg_stat_progress_vacuum;

-- Autovacuumがブロックされているか
SELECT
    a.pid,
    a.query,
    b.pid AS blocking_pid,
    b.query AS blocking_query
FROM pg_stat_activity a
JOIN pg_stat_activity b ON b.pid = ANY(pg_blocking_pids(a.pid))
WHERE a.query LIKE 'autovacuum%';
```

### 6.5 Autovacuumのトラブルシューティング

```
┌──────────────────────────────────────────────────────────────┐
│            Autovacuumのトラブルシューティング                 │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  【問題1】Autovacuumが追いつかない                           │
│  症状: dead tuples が増え続ける                              │
│  対策:                                                       │
│  - autovacuum_max_workers を増やす                          │
│  - autovacuum_vacuum_cost_limit を増やす                    │
│  - autovacuum_vacuum_cost_delay を減らす                    │
│                                                              │
│  【問題2】Autovacuumが起動しない                             │
│  症状: 大量の dead tuples があるのにVACUUMされない          │
│  確認:                                                       │
│  - autovacuum = on か確認                                   │
│  - テーブル単位の設定を確認                                  │
│  - ロングトランザクションがないか確認                        │
│                                                              │
│  【問題3】Autovacuumが遅い                                   │
│  症状: 長時間実行されている                                  │
│  確認:                                                       │
│  - maintenance_work_mem が十分か                            │
│  - I/O負荷が高くないか                                      │
│  - インデックスが多すぎないか                                │
│                                                              │
│  【問題4】ロングトランザクションによるVACUUM阻害             │
│  症状: VACUUMしても dead tuples が減らない                  │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ SELECT pid, now() - xact_start AS duration, query   │   │
│  │ FROM pg_stat_activity                               │   │
│  │ WHERE xact_start < now() - interval '1 hour';      │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## 7. メンテナンススケジュール

### 7.1 日次メンテナンス

```bash
#!/bin/bash
# daily_maintenance.sh

PGHOST="${PGHOST:-localhost}"
PGUSER="${PGUSER:-postgres}"
LOG_FILE="/var/log/pg_maintenance/daily_$(date +%Y%m%d).log"

exec > >(tee -a $LOG_FILE) 2>&1

echo "=== Daily Maintenance Started: $(date) ==="

# 統計情報の確認
psql -h $PGHOST -U $PGUSER -d mydb << 'EOF'
-- 死んだ行が多いテーブル
SELECT schemaname, relname, n_dead_tup, last_autovacuum
FROM pg_stat_user_tables
WHERE n_dead_tup > 10000
ORDER BY n_dead_tup DESC
LIMIT 10;

-- フリーズが必要なテーブル
SELECT schemaname, relname, age(relfrozenxid)
FROM pg_stat_user_tables
WHERE age(relfrozenxid) > 1000000000
ORDER BY age(relfrozenxid) DESC;
EOF

echo "=== Daily Maintenance Completed: $(date) ==="
```

### 7.2 週次メンテナンス

```bash
#!/bin/bash
# weekly_maintenance.sh

PGHOST="${PGHOST:-localhost}"
PGUSER="${PGUSER:-postgres}"
LOG_FILE="/var/log/pg_maintenance/weekly_$(date +%Y%m%d).log"

exec > >(tee -a $LOG_FILE) 2>&1

echo "=== Weekly Maintenance Started: $(date) ==="

# 全テーブルの VACUUM ANALYZE
psql -h $PGHOST -U $PGUSER -d mydb -c "VACUUM ANALYZE;"

# 使用されていないインデックスのレポート
psql -h $PGHOST -U $PGUSER -d mydb << 'EOF'
SELECT schemaname, relname, indexrelname, idx_scan,
       pg_size_pretty(pg_relation_size(indexrelid)) AS size
FROM pg_stat_user_indexes
WHERE idx_scan = 0
  AND indexrelname NOT LIKE '%_pkey'
ORDER BY pg_relation_size(indexrelid) DESC;
EOF

echo "=== Weekly Maintenance Completed: $(date) ==="
```

### 7.3 月次メンテナンス

```bash
#!/bin/bash
# monthly_maintenance.sh

PGHOST="${PGHOST:-localhost}"
PGUSER="${PGUSER:-postgres}"
LOG_FILE="/var/log/pg_maintenance/monthly_$(date +%Y%m%d).log"

exec > >(tee -a $LOG_FILE) 2>&1

echo "=== Monthly Maintenance Started: $(date) ==="

# インデックスの再構築 (大きく肥大化したもののみ)
psql -h $PGHOST -U $PGUSER -d mydb << 'EOF'
-- 肥大化率が高いインデックスを特定
SELECT indexrelname, pg_size_pretty(pg_relation_size(indexrelid))
FROM pg_stat_user_indexes ui
JOIN pg_index i ON ui.indexrelid = i.indexrelid
WHERE pg_relation_size(ui.indexrelid) > 100 * 1024 * 1024  -- 100MB以上
ORDER BY pg_relation_size(ui.indexrelid) DESC;
EOF

# pg_repackで肥大化テーブルを再構築
for table in $(psql -h $PGHOST -U $PGUSER -d mydb -t -c "
    SELECT schemaname || '.' || relname
    FROM pg_stat_user_tables
    WHERE ROUND(100.0 * n_dead_tup / NULLIF(n_live_tup + n_dead_tup, 0), 2) > 20
    AND pg_total_relation_size(schemaname || '.' || relname) > 1073741824  -- 1GB以上
"); do
    echo "Repacking: $table"
    pg_repack -h $PGHOST -U $PGUSER -d mydb -t "$table"
done

echo "=== Monthly Maintenance Completed: $(date) ==="
```

### 7.4 cronでのスケジュール

```bash
# /etc/cron.d/pg_maintenance

# 日次メンテナンス (毎日午前3時)
0 3 * * * postgres /opt/scripts/daily_maintenance.sh

# 週次メンテナンス (毎週日曜午前4時)
0 4 * * 0 postgres /opt/scripts/weekly_maintenance.sh

# 月次メンテナンス (毎月1日午前5時)
0 5 1 * * postgres /opt/scripts/monthly_maintenance.sh
```

---

## 参考リンク

- [PostgreSQL公式ドキュメント - ルーチンメンテナンス](https://www.postgresql.org/docs/current/maintenance.html)
- [PostgreSQL公式ドキュメント - VACUUM](https://www.postgresql.org/docs/current/sql-vacuum.html)
- [PostgreSQL公式ドキュメント - Autovacuum](https://www.postgresql.org/docs/current/routine-vacuuming.html#AUTOVACUUM)
- [pg_repack](https://reorg.github.io/pg_repack/)
- [pgstattuple](https://www.postgresql.org/docs/current/pgstattuple.html)
