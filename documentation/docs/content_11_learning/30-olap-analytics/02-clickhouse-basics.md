# ClickHouse 入門

このドキュメントは [PostgreSQL の学習コンテンツ](../postgresql/)（特に[テーブル設計](../postgresql/dev-02-table-design)、[トランザクション](../postgresql/dev-04-transactions)、[クエリ最適化](../postgresql/dev-05-query-optimization)）を学んだ方を対象に、ClickHouse が PostgreSQL とどう違うかを対比しながら解説します。

## ClickHouse とは

オープンソースの列指向OLAPデータベース。大量データの集計・分析に特化。

```
┌──────────────────────────────────────────────────────────┐
│                  ClickHouse の位置づけ                     │
│                                                          │
│  書き込み重視          ← バランス →          分析重視    │
│  │                                                  │   │
│  │  PostgreSQL    MySQL                  ClickHouse │   │
│  │  ├ ACID保証    ├ ACID保証             ├ 列指向    │   │
│  │  ├ 行指向      ├ 行指向               ├ 高圧縮    │   │
│  │  └ OLTP        └ OLTP                 └ OLAP     │   │
│  │                                                  │   │
│  └──────────────────────────────────────────────────┘   │
│                                                          │
│  ClickHouse は「書き込みは追記のみ、分析は超高速」       │
│  PostgreSQL は「何でもできるが、大量集計は苦手」         │
│  → 組み合わせて使うのが最適解                            │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

---

## アーキテクチャ

PostgreSQL は「1つのプロセスが接続を受けて、共有バッファからデータを読む」シンプルなアーキテクチャ（[PostgreSQL 概要](../postgresql/overview) 参照）。ClickHouse は分析に特化した設計です。

```
┌─ ClickHouse Server ──────────────────────────────────────┐
│                                                          │
│  ┌──────────────────┐                                   │
│  │ SQL パーサー      │ ← 標準SQL + 拡張関数             │
│  └────────┬─────────┘                                   │
│           ▼                                              │
│  ┌──────────────────┐                                   │
│  │ クエリプランナー  │ ← ベクトル化実行計画             │
│  └────────┬─────────┘                                   │
│           ▼                                              │
│  ┌──────────────────┐                                   │
│  │ 実行エンジン      │ ← マルチコア並列 + SIMD          │
│  └────────┬─────────┘                                   │
│           ▼                                              │
│  ┌──────────────────┐                                   │
│  │ MergeTree        │ ← 列指向ストレージエンジン        │
│  │ ストレージ       │                                   │
│  │ ┌──────────────┐ │                                   │
│  │ │ パート(chunk) │ │ ← INSERT ごとにパート生成        │
│  │ │ パート(chunk) │ │   バックグラウンドでマージ        │
│  │ │ パート(chunk) │ │                                   │
│  │ └──────────────┘ │                                   │
│  └──────────────────┘                                   │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

---

## テーブルエンジン

PostgreSQL ではすべてのテーブルが heap ストレージ + B-tree インデックスで統一されています（[インデックス](../postgresql/dev-03-indexes) 参照）。ClickHouse はテーブルごとにストレージエンジンを選択する設計です。

### MergeTree（基本）

PostgreSQL と同じテーブルを ClickHouse で作るとこうなります。

```sql
-- PostgreSQL
CREATE TABLE security_event (
    id UUID PRIMARY KEY,                    -- B-tree インデックス
    type VARCHAR(255) NOT NULL,
    tenant_id UUID NOT NULL,
    user_id UUID,
    detail JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL
) PARTITION BY RANGE (created_at);          -- パーティション
CREATE INDEX idx_tenant ON security_event (tenant_id, type, created_at);

-- ClickHouse
CREATE TABLE security_event (
    id UUID,
    type String,
    tenant_id UUID,
    user_id Nullable(UUID),
    detail String,
    created_at DateTime
)
ENGINE = MergeTree()
PARTITION BY toYYYYMMDD(created_at)         -- 日次パーティション
ORDER BY (tenant_id, type, created_at)      -- ソートキー ≈ PG のインデックス
TTL created_at + INTERVAL 1 YEAR;           -- 自動削除（PGにはない）
```

**PostgreSQL との違い**:

| 概念 | PostgreSQL | ClickHouse |
|:---|:---|:---|
| PRIMARY KEY | B-tree インデックス + 一意制約 | なし（一意制約がない） |
| インデックス | B-tree, GIN, GiST 等を別途作成 | `ORDER BY` がインデックスの役割 |
| パーティション | `PARTITION BY RANGE` + pg_partman | `PARTITION BY` で組み込み |
| データ保持期間 | pg_partman の retention で管理 | `TTL` で自動削除（DDLに組み込み） |
| NULL | 通常のカラム | `Nullable()` で明示指定 |
| JSON | `JSONB`（インデックス可能） | `String`（分析時にJSON関数で抽出） |

### ReplacingMergeTree（CDC用）

```sql
-- CDC（Change Data Capture）で使用
-- 同じキーの行が複数回来ても、最新版だけ残す
CREATE TABLE security_event (
    ...
)
ENGINE = ReplacingMergeTree(created_at) -- created_at が最新の行を残す
ORDER BY (id);
```

---

## SQL: PostgreSQL との比較

### 基本は同じ

```sql
-- PostgreSQL でも ClickHouse でも同じ
SELECT tenant_id, type, COUNT(*)
FROM security_event
WHERE created_at >= '2026-01-01'
GROUP BY tenant_id, type
ORDER BY COUNT(*) DESC
LIMIT 10;
```

### ClickHouse 独自の便利関数

```sql
-- 条件付きカウント
-- PostgreSQL: COUNT(*) FILTER (WHERE type = 'login_success')
-- ClickHouse:
SELECT
    tenant_id,
    countIf(type = 'login_success') AS ok,
    countIf(type = 'login_failure') AS ng

-- ユニークカウント
-- PostgreSQL: COUNT(DISTINCT user_id)
-- ClickHouse:
SELECT uniqExact(user_id) AS dau

-- 時間丸め
-- PostgreSQL: DATE_TRUNC('month', created_at)
-- ClickHouse:
SELECT toStartOfMonth(created_at) AS month

-- パーセンタイル
-- PostgreSQL: PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY response_time)
-- ClickHouse:
SELECT quantile(0.95)(response_time) AS p95
```

### 実践クエリ例

```sql
-- テナント別の月次レポート（18億行でも数秒）
SELECT
    tenant_id,
    toStartOfMonth(created_at) AS month,
    countIf(type = 'login_success') AS login_ok,
    countIf(type = 'login_failure') AS login_ng,
    round(
        countIf(type = 'login_failure') * 100.0
        / countIf(type IN ('login_success', 'login_failure')),
        2
    ) AS failure_rate_pct,
    uniqExact(user_id) AS mau,
    uniqExactIf(user_id, type = 'login_success') AS login_users
FROM security_event
WHERE created_at >= '2025-04-01'
  AND created_at < '2026-04-01'
GROUP BY tenant_id, month
ORDER BY tenant_id, month;
```

---

## INSERT のベストプラクティス

PostgreSQL では1行ずつ INSERT しても問題ありません（WAL に追記、heap に格納）。ClickHouse は列指向のため、INSERT のたびに「パート」と呼ばれるデータチャンクが生成され、バックグラウンドでマージされます。小さなパートが大量にできるとマージが追いつかなくなるため、**バッチ INSERT が必須**です。

```
PostgreSQL: 1行 INSERT × 10万回 → 問題なし
ClickHouse: 1行 INSERT × 10万回 → パート10万個 → マージ地獄

❌ 1行ずつ INSERT（遅い）
  INSERT INTO t VALUES (1, 'a');
  INSERT INTO t VALUES (2, 'b');
  INSERT INTO t VALUES (3, 'c');
  → 毎回パートが生成される → マージ負荷大

✅ バッチ INSERT（推奨: 1万行以上をまとめて）
  INSERT INTO t VALUES (1, 'a'), (2, 'b'), (3, 'c'), ...;
  → 1パート生成 → 効率的

✅ Async Insert（クライアントがバッチできない場合）
  SET async_insert = 1;
  INSERT INTO t VALUES (1, 'a');
  → ClickHouse 側でバッファリングしてバッチ化
```

> **CDC（PeerDB等）を使う場合**: CDC ツールが自動でバッチ化してくれるため、アプリ側でバッチを意識する必要はありません。

---

## PostgreSQL との使い分け

```
┌───────────────────────────────────────────────────┐
│              適材適所の判断フロー                    │
│                                                   │
│  このクエリは...                                   │
│  ├── 1行の読み書き？ → PostgreSQL                 │
│  ├── トランザクションが必要？ → PostgreSQL         │
│  ├── 100万行以上の集計？ → ClickHouse             │
│  ├── 自由なアドホック分析？ → ClickHouse           │
│  └── ダッシュボードの定期更新？ → ClickHouse       │
│                                                   │
└───────────────────────────────────────────────────┘
```

---

## 次のステップ

- [データ投入パターン](data-ingestion): PostgreSQL → ClickHouse のデータ連携
- [OLTP + OLAP デュアル構成](dual-architecture): 組み合わせ設計
