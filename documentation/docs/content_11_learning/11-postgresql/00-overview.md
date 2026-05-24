# PostgreSQL 内部構造ガイド

PostgreSQL を「**そもそもどう動いているか**」を理解するための概要ドキュメント。

各テーマの**全体像**を示し、深掘りは個別ドキュメントへのリンクで誘導する。
PostgreSQL を初めて触る開発者・運用者が最初に読むエントリポイント。

---

## 目次

1. [PostgreSQL とは](#1-postgresql-とは)
2. [プロセスモデル](#2-プロセスモデル)
3. [メモリ構造](#3-メモリ構造)
4. [ストレージ構造](#4-ストレージ構造)
5. [MVCC の基本](#5-mvcc-の基本)
6. [クエリ処理パイプライン](#6-クエリ処理パイプライン)
7. [トランザクションと WAL](#7-トランザクションと-wal)
8. [ロックの種類](#8-ロックの種類)
9. [メンテナンス](#9-メンテナンス)
10. [レプリケーション](#10-レプリケーション)
11. [拡張機能](#11-拡張機能)
12. [ドキュメントマップ](#12-ドキュメントマップ)

---

## 1. PostgreSQL とは

PostgreSQL は **OSS のリレーショナルデータベース管理システム（ORDBMS）**。1996 年から開発が続き、エンタープライズ用途で広く採用されている。

### 主な特徴

| 特徴 | 内容 |
|---|---|
| **ACID 準拠** | トランザクションの 4 性質を厳密に守る |
| **MVCC** | 読み手は書き手を、書き手は読み手をブロックしない（後述） |
| **拡張性** | 拡張機能（extension）として機能を後付け可能。GIS、ベクトル検索、時系列なども |
| **豊富なデータ型** | JSON/JSONB、配列、範囲型、UUID、ENUM、地理空間型 |
| **強力な SQL** | window 関数、CTE、再帰クエリ、PL/pgSQL ストアド関数 |
| **レプリケーション** | 物理／論理レプリケーションを標準搭載 |
| **行レベルセキュリティ (RLS)** | テナント分離をDB層で実装可能 |

### MySQL との大きな違い

| 観点 | PostgreSQL | MySQL (InnoDB) |
|---|---|---|
| MVCC 実装 | 旧バージョンを **heap に残す**（vacuum で回収） | UNDO ログに別途保持 |
| トランザクション | DDL もトランザクション内で可 | DDL は暗黙 commit |
| データ型 | JSONB / 配列 / GIS 標準 | JSON あり、GIS は別途 |
| プロセスモデル | **1 接続 1 プロセス** | 1 接続 1 スレッド |
| 拡張機能 | 第一級概念（CREATE EXTENSION） | プラグイン仕組みあるが用途限定 |

---

## 2. プロセスモデル

PostgreSQL は **1 接続 = 1 OS プロセス** の構造。複数の補助プロセスも常駐する。

```
┌─────────────────────────────────────────────────────────────┐
│                       postmaster                            │
│           （親プロセス、接続受付・fork 管理）              │
└──────┬──────────────────────────────────────────────────────┘
       │ fork
       ├─→ backend (client 1)
       ├─→ backend (client 2)
       ├─→ backend (client N)
       │
       ├─→ autovacuum launcher
       │     └─→ autovacuum worker × 並列数
       ├─→ WAL writer
       ├─→ checkpointer
       ├─→ background writer
       ├─→ stats collector (PG14 以前) / autovacuum logger (PG15+)
       ├─→ logical replication launcher (使用時)
       └─→ walsender (レプリケーション元の場合)
```

### 主なプロセスの役割

| プロセス | 役割 |
|---|---|
| **postmaster** | クライアント接続を受け付け、backend を fork する親プロセス |
| **backend** | 1 接続を担当。クエリ実行、トランザクション管理 |
| **autovacuum launcher / worker** | dead tuple 回収、統計情報更新 |
| **WAL writer** | WAL バッファをディスクに書き出す |
| **checkpointer** | 共有バッファの dirty page を定期的にディスクへフラッシュ |
| **background writer** | 通常のクエリ実行中にバックグラウンドで dirty page を書き出す |
| **walsender** | レプリケーション先へ WAL を送信 |

### ポイント

- **接続確立コストが高い**（fork + 認証 + 初期化）→ 接続プール（HikariCP / PgBouncer 等）必須
- **接続数が CPU コア数を大幅に超えると context switch 地獄** → アプリ層の接続上限は慎重に
- 経験則: `max_connections = vCPU × 2〜4` 程度がスイートスポット、それ以上は PgBouncer で集約推奨

詳細: [dev-06-connection-pooling.md](dev-06-connection-pooling.md)

---

## 3. メモリ構造

PostgreSQL は **共有メモリ** と **プロセス別メモリ** の 2 種類を持つ。

```
┌────────────────────────────────────────────────────────────┐
│                    共有メモリ（全 backend で共有）          │
├────────────────────────────────────────────────────────────┤
│  shared_buffers      … テーブル・index の page キャッシュ  │
│  WAL buffers         … 未書き出し WAL レコード             │
│  共有 lock テーブル  … LWLock の状態                       │
└────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────┐
│             プロセス別メモリ（backend ごと）                │
├────────────────────────────────────────────────────────────┤
│  work_mem            … ソート・ハッシュ join の一時領域    │
│  maintenance_work_mem … VACUUM/CREATE INDEX で使用         │
│  temp_buffers        … 一時テーブル用                      │
│  CatalogCache        … システムカタログのキャッシュ        │
└────────────────────────────────────────────────────────────┘
```

### 主要パラメータ

| パラメータ | 用途 | 設定の目安 |
|---|---|---|
| `shared_buffers` | テーブル/index のキャッシュ | 物理メモリの 25%（〜40%）|
| `work_mem` | クエリのソート・ハッシュ用 | 4MB〜数十 MB（クエリ数 × 接続数を考慮） |
| `maintenance_work_mem` | VACUUM / CREATE INDEX | 64MB〜数 GB |
| `effective_cache_size` | planner にヒント | 物理メモリの 50-75% |
| `temp_buffers` | 一時テーブル用 | デフォルト 8MB |

### ポイント

- `work_mem` は **クエリの中間ノードごと** に確保される（ソート 1 つにつき 1 つ）
- → `work_mem` × ノード数 × 接続数 が実メモリ上限に届かないように
- `shared_buffers` が小さいと buffer hit ratio が下がり、I/O 待ちが増える（CPU は余ってるのに遅い状態）

---

## 4. ストレージ構造

データは PGDATA ディレクトリ配下に保存される。

```
PGDATA/
├── base/                    各DB のテーブル・index ファイル
│   ├── 1/                   ← OID で識別
│   ├── 16384/
│   └── ...
├── global/                  グローバルカタログ（pg_database 等）
├── pg_wal/                  WAL ファイル
├── pg_xact/                 トランザクション commit 情報
├── pg_subtrans/             サブトランザクション
├── pg_multixact/            複数 tx の lock 情報
├── pg_tblspc/               テーブルスペース
├── pg_stat/                 統計情報
└── postgresql.conf          設定ファイル
```

### テーブルとファイル

- 1 テーブル = 1 ファイル（〜1GB 単位で分割される）
- **8KB のページ**を単位として読み書き
- 1 ページに収まらない大きなカラムは **TOAST**（後述）

### TOAST（The Oversized-Attribute Storage Technique）

行のサイズが 2KB を超えそうになると、大きいカラムを別テーブル（TOAST テーブル）へ自動的に退避する仕組み。

```
本体テーブル                          pg_toast.pg_toast_xxxxx テーブル
┌─────────────────────────┐           ┌──────────────────────┐
│ id │ name │ ...        │ ←─ pointer → │ chunk_id │ chunk_data │
│                  ↑       │           │                       │
│           大きい列       │           │  圧縮された 2KB 分割  │
└─────────────────────────┘           └──────────────────────┘
```

- JSONB / TEXT / BYTEA など可変長型がデフォルトで TOAST 対象
- 圧縮（PGLZ または LZ4）もかかる
- 大きい列を SELECT すると **TOAST 経由の追加 I/O** が発生

### インデックスファイル

- B-tree、GIN、GiST、BRIN など、種類ごとに別ファイル
- これも 8KB ページ単位

詳細: [dev-03-indexes.md](dev-03-indexes.md)

---

## 5. MVCC の基本

PostgreSQL は **Multi-Version Concurrency Control (MVCC)** を実装する。

### 基本ルール

**「変更しても旧バージョンを heap に残す」**

```
時刻 t1: INSERT INTO users (id, name) VALUES (1, 'Alice');
heap:
  ┌─────────────────────┐
  │ id=1, name='Alice', xmin=100, xmax=NULL │  ← live
  └─────────────────────┘

時刻 t2: UPDATE users SET name='Bob' WHERE id=1;
heap:
  ┌─────────────────────┐
  │ id=1, name='Alice', xmin=100, xmax=200 │  ← dead (旧版)
  │ id=1, name='Bob',   xmin=200, xmax=NULL │  ← live (新版)
  └─────────────────────┘
```

各行に **`xmin`（作成 tx ID）** と **`xmax`（削除 tx ID）** が記録され、tx ID と照らし合わせて行の可視性を判定する。

### 効果

- **読み手は書き手をブロックしない**（旧バージョンを読める）
- **書き手は読み手をブロックしない**
- 同じテーブルへの並行アクセス性能が高い

### 代償: dead tuple の蓄積

- UPDATE / DELETE するたびに dead 行が増える
- **autovacuum** が定期的に dead を回収（VACUUM）する
- 回収が追いつかないと **bloat**（テーブル肥大化）が起きる

### HOT update（Heap-Only Tuple）

- インデックス対象カラムを変更しない UPDATE は、**インデックス更新不要** + 同一ページ内で済む
- 高頻度 UPDATE テーブルでは fillfactor を下げて HOT update が起きやすくする

詳細: [dba-06-maintenance.md](dba-06-maintenance.md)、[dev-04-transactions.md](dev-04-transactions.md)

---

## 6. クエリ処理パイプライン

SQL は以下のステージを経て実行される。

```
[SQL 文字列]
    │
    ▼
┌──────────┐   字句解析・構文解析、内部 AST 構築
│  Parser  │
└──────────┘
    │
    ▼
┌──────────┐   ビュー展開、ルール書き換え
│ Rewriter │
└──────────┘
    │
    ▼
┌──────────┐   統計情報を使ったコスト計算、最適プラン選択
│ Planner  │
└──────────┘
    │
    ▼
┌──────────┐   実際にデータを読み・書きする
│ Executor │
└──────────┘
    │
    ▼
[結果]
```

### Planner が選ぶプランの例

| プラン | 用途 |
|---|---|
| Seq Scan | テーブル全件スキャン（小さいテーブルや WHERE 不一致時）|
| Index Scan | index を使って効率的に取得 |
| Index Only Scan | 必要な値が index に全部あれば、heap を見ない |
| Bitmap Index Scan | 中程度の行数で複数 index 結合 |
| Hash Join / Merge Join / Nested Loop | JOIN の戦略 |

### Prepared Statement

- 同じクエリを繰り返し実行する場合、Parser / Planner コストを再利用できる
- PgJDBC では `prepareThreshold` 回（デフォルト 5）以降サーバ側プリペアに昇格

詳細: [dba-08-planner.md](dba-08-planner.md)、[dev-05-query-optimization.md](dev-05-query-optimization.md)

---

## 7. トランザクションと WAL

### WAL（Write-Ahead Log）

「**変更を heap に反映する前に、必ず WAL に記録する**」というルール。

```
[アプリ] → INSERT 実行 → backend
                            │
                            ▼
                     1. WAL バッファに記録
                            │
                            ▼
                     2. shared_buffers の page を更新（メモリ上）
                            │
                            ▼
                     3. COMMIT → WAL を fsync（ディスク同期）
                            │
                            ▼
                            commit 成功
                            │
                            ▼（後で、非同期で）
                     4. checkpointer が dirty page を heap に書き出し
```

### 効果

- **耐障害性**: WAL があれば、クラッシュしても再起動時にリプレイで復旧可能
- **書き込み効率**: コミット時に必要なのは WAL の fsync のみ（heap 全体じゃない）

### checkpoint

- WAL に貯まった変更を heap に反映する定期処理
- `checkpoint_timeout`（デフォルト 5 分）or `max_wal_size`（デフォルト 1GB）超過で発火
- 大量書き込み時に I/O スパイクが起きやすい

### トランザクション分離レベル

| レベル | 動作 |
|---|---|
| READ UNCOMMITTED | PostgreSQL では実質 READ COMMITTED と同じ |
| **READ COMMITTED** | デフォルト。各文が始まる時点のスナップショット |
| REPEATABLE READ | tx 開始時のスナップショットで一貫性保証 |
| SERIALIZABLE | 真の直列実行に等価（衝突したら abort）|

詳細: [dev-04-transactions.md](dev-04-transactions.md)

---

## 8. ロックの種類

PostgreSQL のロックは大きく 3 種類：

### ① 行レベルロック

`SELECT FOR UPDATE` / `UPDATE` / `DELETE` で取得。MVCC により読み取りはブロックしない。

| モード | 用途 |
|---|---|
| FOR UPDATE | 排他的、他の FOR UPDATE をブロック |
| FOR NO KEY UPDATE | キー以外の UPDATE 用、より弱い |
| FOR SHARE | 共有、他の SHARE は許可 |
| FOR KEY SHARE | FK 整合性チェック用 |

### ② テーブルレベルロック

DDL や VACUUM などで取得。8 つのモードがあり、互換性が定められている。

| モード | 取得タイミング |
|---|---|
| ACCESS SHARE | 単純な SELECT |
| ROW SHARE | SELECT FOR UPDATE 等 |
| ROW EXCLUSIVE | UPDATE / INSERT / DELETE |
| SHARE / SHARE ROW EXCLUSIVE | 一部 DDL |
| EXCLUSIVE | REFRESH MATERIALIZED VIEW CONCURRENTLY |
| **ACCESS EXCLUSIVE** | DROP / TRUNCATE / ALTER COLUMN TYPE 等、**全アクセス停止** |

### ③ Advisory Lock（アプリ制御）

`pg_advisory_lock(key)` で取得。PostgreSQL は中身を解釈しない、アプリが用途を決める。

### 内部ロック（LWLock）

shared_buffers のページ更新、WAL バッファ、lock manager 等で使われる軽量ロック。  
高負荷時に LWLock 競合が ボトルネックになることもある。

詳細: [dba-10-procarraylock-internals.md](dba-10-procarraylock-internals.md)

---

## 9. メンテナンス

### VACUUM

- dead tuple を回収して領域を再利用可能にする
- **テーブルサイズ自体は縮まない**（`VACUUM FULL` でないと）
- 通常は autovacuum が自動実行

### ANALYZE

- テーブルの統計情報を更新（planner が使う）
- データ分布が変わったら必要

### REINDEX

- index 再構築。bloat 解消や破損修復に
- オンラインで実行する `REINDEX CONCURRENTLY` あり

### autovacuum

- backend と独立した補助プロセス
- テーブル単位で `n_dead_tup / n_live_tup` がしきい値を超えたら発火
- `autovacuum_vacuum_scale_factor`（デフォルト 0.2）で調整

詳細: [dba-06-maintenance.md](dba-06-maintenance.md)

---

## 10. レプリケーション

PostgreSQL は **WAL を流す** ことでレプリケーションを実装する。

### 物理レプリケーション（Streaming Replication）

- WAL レコードをバイナリのまま replica へ送信
- replica は同じ WAL を再生（read-only モード）
- 同期 / 非同期を選べる

```
[Primary]   WAL生成    [Replica]
   │   ────────────────→  │
   │     walsender         │ walreceiver
   │                       │
   ├─ heap                 ├─ heap (read-only)
   └─ WAL                  └─ WAL (リプレイ)
```

### 論理レプリケーション

- WAL を「INSERT/UPDATE/DELETE のイベント」として解釈し、別 DB へ
- バージョン跨ぎ、片方向のテーブル抽出など柔軟
- publication / subscription モデル

### 用途比較

| 用途 | 推奨 |
|---|---|
| HA / 読み取り分散 | 物理レプリケーション |
| バージョンアップ時の段階移行 | 論理レプリケーション |
| 別システムへのデータ連携 | 論理レプリケーション |

詳細: [dba-03-replication-ha.md](dba-03-replication-ha.md)

---

## 11. 拡張機能

PostgreSQL は `CREATE EXTENSION` で機能を後付けできる。

```sql
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
```

### よく使う拡張

| 拡張 | 用途 |
|---|---|
| **pg_stat_statements** | SQL ごとの実行時間統計（性能調査必須）|
| **pgcrypto** | 暗号化・ハッシュ関数 |
| **pg_partman** | パーティション自動管理 |
| **pg_cron** | DB 内 cron スケジューラ |
| **PostGIS** | 地理空間データ |
| **pgvector** | ベクトル検索（RAG / 機械学習用途） |
| **pg_repack** | bloat オンライン解消 |

詳細: [dba-09-extensions.md](dba-09-extensions.md)

---

## 12. ドキュメントマップ

このディレクトリ配下のドキュメント一覧。

### DBA（運用）向け

| # | ドキュメント | 内容 |
|---|---|---|
| 00 | [dba-00-managed-vs-self-hosted.md](dba-00-managed-vs-self-hosted.md) | マネージド vs セルフホスト（AWS RDS）|
| 01 | [dba-01-installation.md](dba-01-installation.md) | インストールと初期設定 |
| 02 | [dba-02-backup-recovery.md](dba-02-backup-recovery.md) | バックアップとリカバリ |
| 03 | [dba-03-replication-ha.md](dba-03-replication-ha.md) | レプリケーションと HA |
| 04 | [dba-04-security.md](dba-04-security.md) | セキュリティ設定 |
| 05 | [dba-05-monitoring.md](dba-05-monitoring.md) | 監視とアラート |
| 06 | [dba-06-maintenance.md](dba-06-maintenance.md) | 定期メンテナンス |
| 07 | [dba-07-partitioning.md](dba-07-partitioning.md) | パーティショニング |
| 08 | [dba-08-planner.md](dba-08-planner.md) | クエリプランナー深掘り |
| 09 | [dba-09-extensions.md](dba-09-extensions.md) | 拡張機能 |
| 10 | [dba-10-procarraylock-internals.md](dba-10-procarraylock-internals.md) | ProcArrayLock / LWLock 内部 |

### 開発者向け

| # | ドキュメント | 内容 |
|---|---|---|
| 01 | [dev-01-sql-basics.md](dev-01-sql-basics.md) | SQL 基礎 |
| 02 | [dev-02-table-design.md](dev-02-table-design.md) | テーブル設計 |
| 03 | [dev-03-indexes.md](dev-03-indexes.md) | インデックス設計 |
| 04 | [dev-04-transactions.md](dev-04-transactions.md) | トランザクション・ロック |
| 05 | [dev-05-query-optimization.md](dev-05-query-optimization.md) | クエリ最適化 |
| 06 | [dev-06-connection-pooling.md](dev-06-connection-pooling.md) | 接続プーリング |
| 07 | [dev-07-plpgsql-basics.md](dev-07-plpgsql-basics.md) | PL/pgSQL 基礎 |
| 08 | [dev-08-plpgsql-advanced.md](dev-08-plpgsql-advanced.md) | PL/pgSQL 応用 |

### 性能改善関連（運用ドキュメント側）

| ドキュメント | 内容 |
|---|---|
| `content_08_ops/performance/08-postgresql-cpu-patterns.md` | PostgreSQL CPU 消費パターン（設計者向け）|
| `content_08_ops/performance/` 配下 | 性能テスト結果・スケーラビリティ評価など |

---

## 推奨学習順序

```
[初学者]
   │
   ▼
00-overview.md (本書)
   │
   ▼
dev-01-sql-basics → dev-02-table-design → dev-03-indexes
   │
   ▼
dev-04-transactions → dev-05-query-optimization
   │
   ▼
dba-01-installation → dba-04-security
   │
   ▼
dba-06-maintenance → dba-08-planner
   │
   ▼
[性能課題に遭遇したとき]
   │
   ▼
content_08_ops/performance/08-postgresql-cpu-patterns.md
content_08_ops/performance/ 配下のドキュメント
```

---

## 参考リンク

- PostgreSQL 公式: [Documentation](https://www.postgresql.org/docs/current/)
- PostgreSQL 公式: [Internals](https://www.postgresql.org/docs/current/internals.html)
- PostgreSQL Wiki: [https://wiki.postgresql.org/](https://wiki.postgresql.org/)
