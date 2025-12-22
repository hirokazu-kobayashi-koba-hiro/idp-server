# PostgreSQL クエリ最適化

## 所要時間
約50分

## 学べること
- EXPLAIN/EXPLAIN ANALYZEの読み方
- 実行計画の最適化手法
- 統計情報とプランナーの動作
- よくあるパフォーマンス問題と解決策
- クエリチューニングの実践テクニック

## 前提知識
- SQLの基本操作
- インデックスの基礎知識

---

## 1. 実行計画の基礎

### 1.1 EXPLAINとは

EXPLAINは、PostgreSQLがクエリをどのように実行するかを表示するコマンドです。

```sql
-- 基本的な使い方
EXPLAIN SELECT * FROM users WHERE id = 1;

-- 実際に実行して時間も計測
EXPLAIN ANALYZE SELECT * FROM users WHERE id = 1;

-- より詳細な情報
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT * FROM users WHERE id = 1;
```

### 1.2 実行計画の読み方

```
                                    QUERY PLAN
------------------------------------------------------------------------------------
 Index Scan using users_pkey on users  (cost=0.29..8.31 rows=1 width=100)
   Index Cond: (id = 1)
 Planning Time: 0.080 ms
 Execution Time: 0.025 ms
```

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                        実行計画の各要素                                          │
├──────────────────┬──────────────────────────────────────────────────────────────┤
│ 要素             │ 説明                                                         │
├──────────────────┼──────────────────────────────────────────────────────────────┤
│ Index Scan       │ 実行方法（ノードタイプ）                                      │
│                  │ インデックスを使った検索                                      │
├──────────────────┼──────────────────────────────────────────────────────────────┤
│ cost=0.29..8.31  │ コスト見積もり                                               │
│                  │ 0.29: 最初の行を返すまでのコスト（スタートアップコスト）      │
│                  │ 8.31: すべての行を返すまでの総コスト                          │
├──────────────────┼──────────────────────────────────────────────────────────────┤
│ rows=1           │ 返される行数の見積もり                                        │
├──────────────────┼──────────────────────────────────────────────────────────────┤
│ width=100        │ 1行あたりの平均バイト数                                       │
├──────────────────┼──────────────────────────────────────────────────────────────┤
│ Index Cond       │ インデックス条件                                             │
└──────────────────┴──────────────────────────────────────────────────────────────┘
```

### 1.3 EXPLAIN ANALYZEの詳細

```sql
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM orders WHERE user_id = 123;
```

```
                                         QUERY PLAN
--------------------------------------------------------------------------------------------
 Index Scan using orders_user_id_idx on orders  (cost=0.43..12.50 rows=5 width=200)
                                                 (actual time=0.020..0.035 rows=7 loops=1)
   Index Cond: (user_id = 123)
   Buffers: shared hit=4
 Planning Time: 0.100 ms
 Execution Time: 0.055 ms
```

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    EXPLAIN ANALYZEの追加情報                                     │
├──────────────────────────┬──────────────────────────────────────────────────────┤
│ 要素                      │ 説明                                                │
├──────────────────────────┼──────────────────────────────────────────────────────┤
│ actual time=0.020..0.035 │ 実際の実行時間（ミリ秒）                             │
│                          │ 最初の行まで..最後の行まで                           │
├──────────────────────────┼──────────────────────────────────────────────────────┤
│ rows=7                   │ 実際に返された行数                                   │
│                          │ ※ 見積もり(rows=5)との差に注目                      │
├──────────────────────────┼──────────────────────────────────────────────────────┤
│ loops=1                  │ このノードが実行された回数                           │
│                          │ ネストループ結合では複数回実行される                 │
├──────────────────────────┼──────────────────────────────────────────────────────┤
│ Buffers: shared hit=4    │ バッファ情報                                         │
│                          │ hit: キャッシュから読み取ったブロック数              │
│                          │ read: ディスクから読み取ったブロック数               │
└──────────────────────────┴──────────────────────────────────────────────────────┘
```

---

## 2. スキャン方法

### 2.1 主なスキャンタイプ

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                         スキャン方法の比較                                       │
├──────────────────────┬──────────────────────────────────────────────────────────┤
│ スキャンタイプ        │ 説明                                                     │
├──────────────────────┼──────────────────────────────────────────────────────────┤
│ Seq Scan             │ テーブル全体を順次読み取り                                │
│                      │ フィルタ条件に合致する行を返す                           │
│                      │ 大部分の行を読む場合に効率的                             │
├──────────────────────┼──────────────────────────────────────────────────────────┤
│ Index Scan           │ インデックスを使って行を特定                             │
│                      │ インデックスとテーブルの両方にアクセス                   │
│                      │ 少数の行を取得する場合に効率的                           │
├──────────────────────┼──────────────────────────────────────────────────────────┤
│ Index Only Scan      │ インデックスのみで完結                                   │
│                      │ テーブルアクセス不要（可視性確認を除く）                 │
│                      │ カバリングインデックスで実現                             │
├──────────────────────┼──────────────────────────────────────────────────────────┤
│ Bitmap Index Scan    │ 複数のインデックスを組み合わせ                           │
│ + Bitmap Heap Scan   │ まずビットマップを作成、その後テーブルにアクセス         │
│                      │ 中程度の行数を取得する場合に効率的                       │
└──────────────────────┴──────────────────────────────────────────────────────────┘
```

### 2.2 スキャン選択の図解

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    スキャン方法の選択基準                                        │
│                                                                                 │
│  取得行数 / 総行数 の割合                                                       │
│                                                                                 │
│  0%        5%       20%                                   100%                  │
│  ├─────────┼─────────┼─────────────────────────────────────┤                   │
│  │         │         │                                     │                   │
│  │ Index   │ Bitmap  │         Seq Scan                    │                   │
│  │ Scan    │ Scan    │         (全件スキャンが効率的)       │                   │
│  │         │         │                                     │                   │
│  └─────────┴─────────┴─────────────────────────────────────┘                   │
│                                                                                 │
│  ※ 実際の閾値はテーブルサイズ、ランダムI/Oコストなどで変動                       │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 2.3 各スキャンの実例

```sql
-- Seq Scan（インデックスなし、または大部分を取得）
EXPLAIN SELECT * FROM users WHERE created_at > '2024-01-01';
-- → Seq Scan on users  (cost=0.00..1520.00 rows=50000 width=100)
--     Filter: (created_at > '2024-01-01'::date)

-- Index Scan（少数行を取得）
EXPLAIN SELECT * FROM users WHERE id = 123;
-- → Index Scan using users_pkey on users  (cost=0.29..8.31 rows=1 width=100)
--     Index Cond: (id = 123)

-- Index Only Scan（インデックスに含まれる列のみ取得）
EXPLAIN SELECT id, email FROM users WHERE email = 'test@example.com';
-- → Index Only Scan using users_email_idx on users  (cost=0.29..4.31 rows=1 width=50)
--     Index Cond: (email = 'test@example.com'::text)

-- Bitmap Scan（中程度の行数、または複数条件）
EXPLAIN SELECT * FROM orders WHERE status = 'pending' AND created_at > '2024-01-01';
-- → Bitmap Heap Scan on orders  (cost=10.50..520.50 rows=200 width=150)
--     Recheck Cond: ((status = 'pending') AND (created_at > '2024-01-01'))
--     → BitmapAnd
--           → Bitmap Index Scan on orders_status_idx
--           → Bitmap Index Scan on orders_created_at_idx
```

---

## 3. 結合方法

### 3.1 主な結合アルゴリズム

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                          結合アルゴリズム                                        │
├──────────────────────┬──────────────────────────────────────────────────────────┤
│ アルゴリズム          │ 特徴                                                     │
├──────────────────────┼──────────────────────────────────────────────────────────┤
│ Nested Loop Join     │ 外側テーブルの各行に対して、内側テーブルを検索           │
│                      │ 小さいテーブル同士、またはインデックスがある場合に効率的 │
│                      │ O(N × M) または O(N × log M) インデックス使用時          │
├──────────────────────┼──────────────────────────────────────────────────────────┤
│ Hash Join            │ 小さい方のテーブルでハッシュテーブルを構築               │
│                      │ 大きい方をスキャンしながらハッシュで検索                 │
│                      │ 等価結合で効率的、メモリを使用                           │
│                      │ O(N + M)                                                 │
├──────────────────────┼──────────────────────────────────────────────────────────┤
│ Merge Join           │ 両テーブルをソートして順次マージ                         │
│                      │ 大きなテーブル同士、ソート済みデータで効率的             │
│                      │ O(N log N + M log M + N + M)                             │
└──────────────────────┴──────────────────────────────────────────────────────────┘
```

### 3.2 Nested Loop Join

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                       Nested Loop Join                                          │
│                                                                                 │
│  外側テーブル (users)         内側テーブル (orders)                              │
│  ┌─────────────────┐         ┌─────────────────┐                              │
│  │ user_id = 1    ├─────────→│ user_id = 1 の  │                              │
│  │                │          │ 注文を検索      │                              │
│  ├─────────────────┤         └─────────────────┘                              │
│  │ user_id = 2    ├─────────→│ user_id = 2 の  │                              │
│  │                │          │ 注文を検索      │                              │
│  ├─────────────────┤         └─────────────────┘                              │
│  │ user_id = 3    ├─────────→│ user_id = 3 の  │                              │
│  │                │          │ 注文を検索      │                              │
│  └─────────────────┘         └─────────────────┘                              │
│                                                                                 │
│  → 外側の各行に対して内側を検索                                                 │
│  → 内側にインデックスがあると高速                                               │
└─────────────────────────────────────────────────────────────────────────────────┘
```

```sql
EXPLAIN ANALYZE
SELECT u.name, o.total
FROM users u
JOIN orders o ON u.id = o.user_id
WHERE u.id = 123;

-- → Nested Loop  (cost=0.72..16.77 rows=5 width=50) (actual time=0.030..0.045 rows=3)
--     → Index Scan using users_pkey on users u  (cost=0.29..8.31 rows=1) (actual rows=1)
--           Index Cond: (id = 123)
--     → Index Scan using orders_user_id_idx on orders o  (cost=0.43..8.41 rows=5) (actual rows=3)
--           Index Cond: (user_id = 123)
```

### 3.3 Hash Join

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           Hash Join                                             │
│                                                                                 │
│  1. ビルドフェーズ                     2. プローブフェーズ                       │
│  ┌─────────────────────────┐          ┌─────────────────────────┐              │
│  │ 小さいテーブル          │          │ 大きいテーブル          │              │
│  │ (categories)           │          │ (products)             │              │
│  │                        │          │                        │              │
│  │ ┌────────────────────┐ │          │ 各行をスキャン          │              │
│  │ │ ハッシュテーブル    │ │ ←───────│ ハッシュで検索          │              │
│  │ │ category_id → 行   │ │          │                        │              │
│  │ └────────────────────┘ │          │                        │              │
│  └─────────────────────────┘          └─────────────────────────┘              │
│                                                                                 │
│  → メモリ上にハッシュテーブルを構築                                             │
│  → work_mem が重要                                                             │
└─────────────────────────────────────────────────────────────────────────────────┘
```

```sql
EXPLAIN ANALYZE
SELECT p.name, c.name as category
FROM products p
JOIN categories c ON p.category_id = c.id;

-- → Hash Join  (cost=25.00..500.00 rows=10000 width=60) (actual time=2.5..25.0 rows=10000)
--     Hash Cond: (p.category_id = c.id)
--     → Seq Scan on products p  (cost=0.00..250.00 rows=10000 width=40)
--     → Hash  (cost=15.00..15.00 rows=100 width=25) (actual time=0.5..0.5 rows=100)
--           Buckets: 128  Batches: 1  Memory Usage: 12kB
--           → Seq Scan on categories c  (cost=0.00..15.00 rows=100 width=25)
```

### 3.4 Merge Join

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                          Merge Join                                             │
│                                                                                 │
│  ソート済みテーブルA              ソート済みテーブルB                             │
│  ┌─────────────────┐             ┌─────────────────┐                           │
│  │ id: 1          ├─────────────→│ id: 1          │ マッチ                     │
│  │ id: 2          ├─────────────→│ id: 2          │ マッチ                     │
│  │ id: 3          │             │ id: 4          │                            │
│  │ id: 5          ├─────────────→│ id: 5          │ マッチ                     │
│  │ id: 6          │             │ ...            │                            │
│  └─────────────────┘             └─────────────────┘                           │
│                                                                                 │
│  → 両テーブルを同時に走査                                                       │
│  → ソート済みであることが前提（インデックスまたは明示的ソート）                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. 統計情報

### 4.1 統計情報とは

プランナーが最適な実行計画を選ぶための情報です。

```sql
-- テーブルの統計情報
SELECT
    relname,
    reltuples,      -- 推定行数
    relpages,       -- ページ数
    relallvisible   -- 全可視ページ数
FROM pg_class
WHERE relname = 'users';

-- 列の統計情報
SELECT
    attname,
    n_distinct,     -- ユニーク値の推定数（負の値は割合）
    null_frac,      -- NULL の割合
    avg_width,      -- 平均バイト幅
    most_common_vals,   -- 最頻値
    most_common_freqs   -- 最頻値の出現頻度
FROM pg_stats
WHERE tablename = 'users' AND attname = 'status';
```

### 4.2 統計情報の更新

```sql
-- 特定テーブルの統計情報を更新
ANALYZE users;

-- 全テーブル
ANALYZE;

-- 特定列のみ
ANALYZE users (email, status);

-- 詳細な統計情報を取得（デフォルト100）
ALTER TABLE users ALTER COLUMN status SET STATISTICS 1000;
ANALYZE users;
```

### 4.3 統計情報が古い場合の問題

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    統計情報が古い場合の影響                                      │
│                                                                                 │
│  統計情報: 100万行                  実際: 1000万行                              │
│  ┌───────────────────┐              ┌───────────────────┐                      │
│  │ 推定行数: 1000     │              │ 実際行数: 10000   │                      │
│  │                   │              │                   │                      │
│  │ → Index Scan を   │              │ → 実際には        │                      │
│  │   選択            │              │   Seq Scan の方が │                      │
│  │                   │              │   効率的だった    │                      │
│  └───────────────────┘              └───────────────────┘                      │
│                                                                                 │
│  結果: 非効率な実行計画 → パフォーマンス低下                                     │
└─────────────────────────────────────────────────────────────────────────────────┘
```

```sql
-- 見積もりと実際の差を確認
EXPLAIN ANALYZE SELECT * FROM users WHERE status = 'active';

-- rows=1000 が見積もり、actual rows=15000 が実際
-- 大きな差がある場合は ANALYZE を実行
```

### 4.4 autovacuum と統計情報

```sql
-- autovacuum の設定確認
SHOW autovacuum;

-- テーブルごとの最終分析日時
SELECT
    schemaname,
    relname,
    last_analyze,
    last_autoanalyze,
    n_live_tup,
    n_dead_tup
FROM pg_stat_user_tables
ORDER BY last_autoanalyze DESC NULLS LAST;
```

---

## 5. よくあるパフォーマンス問題

### 5.1 インデックスが使われない

#### 原因1: 関数やキャスト

```sql
-- ✗ インデックスが使われない
SELECT * FROM users WHERE LOWER(email) = 'test@example.com';
SELECT * FROM users WHERE created_at::date = '2024-01-01';

-- ✓ 式インデックスを作成
CREATE INDEX users_email_lower_idx ON users (LOWER(email));
CREATE INDEX users_created_date_idx ON users ((created_at::date));
```

#### 原因2: 型の不一致

```sql
-- user_id が INTEGER 型の場合
-- ✗ 型変換が発生してインデックスが使われない
SELECT * FROM users WHERE user_id = '123';

-- ✓ 正しい型を使用
SELECT * FROM users WHERE user_id = 123;
```

#### 原因3: LIKE パターン

```sql
-- ✗ 前方一致以外はインデックスが使われない
SELECT * FROM users WHERE name LIKE '%田中%';

-- ✓ 前方一致はインデックスが使用される
SELECT * FROM users WHERE name LIKE '田中%';

-- ✓ 全文検索には GIN インデックス + pg_trgm
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX users_name_trgm_idx ON users USING gin (name gin_trgm_ops);
SELECT * FROM users WHERE name LIKE '%田中%';  -- インデックス使用
```

#### 原因4: OR 条件

```sql
-- ✗ OR条件はインデックスが使われにくい
SELECT * FROM users WHERE email = 'a@example.com' OR name = '田中';

-- ✓ UNION で分割
SELECT * FROM users WHERE email = 'a@example.com'
UNION
SELECT * FROM users WHERE name = '田中';
```

### 5.2 N+1 問題

```sql
-- ✗ N+1 問題（アプリケーションで発生）
-- 1回目: SELECT * FROM users WHERE status = 'active';
-- N回目: SELECT * FROM orders WHERE user_id = ? (ユーザーごと)

-- ✓ JOIN で1クエリに
SELECT u.*, o.*
FROM users u
LEFT JOIN orders o ON u.id = o.user_id
WHERE u.status = 'active';

-- ✓ または IN 句でまとめて取得
SELECT * FROM orders WHERE user_id IN (1, 2, 3, ...);
```

### 5.3 大量データの OFFSET

```sql
-- ✗ 深いページネーション（OFFSET が大きいと遅い）
SELECT * FROM logs ORDER BY created_at DESC LIMIT 20 OFFSET 100000;
-- → 100000行をスキップするために全部読む必要がある

-- ✓ カーソルベースのページネーション
SELECT * FROM logs
WHERE created_at < '2024-01-15 10:30:00'  -- 前回の最後のcreated_at
ORDER BY created_at DESC
LIMIT 20;

-- ✓ または ROW_NUMBER() を使用（ただしインデックスが必要）
WITH numbered AS (
    SELECT *, ROW_NUMBER() OVER (ORDER BY created_at DESC) as rn
    FROM logs
)
SELECT * FROM numbered WHERE rn BETWEEN 100001 AND 100020;
```

### 5.4 不要な列の取得

```sql
-- ✗ 全列を取得
SELECT * FROM large_table WHERE id = 123;

-- ✓ 必要な列のみ取得
SELECT id, name, status FROM large_table WHERE id = 123;
-- → Index Only Scan が可能になる場合もある
```

### 5.5 ソートのボトルネック

```sql
-- 大量データのソートはメモリとディスクを消費
EXPLAIN ANALYZE SELECT * FROM logs ORDER BY created_at DESC;

-- → Sort Method: external merge Disk: xxxKB
-- これはwork_memを超えてディスクソートが発生している

-- 対策1: インデックスを活用
CREATE INDEX logs_created_at_desc_idx ON logs (created_at DESC);

-- 対策2: work_mem を増やす（セッション単位で）
SET work_mem = '256MB';
```

---

## 6. クエリチューニング実践

### 6.1 EXPLAINで問題を特定する手順

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                   クエリチューニングの手順                                        │
│                                                                                 │
│  1. EXPLAIN ANALYZE でベースラインを取得                                         │
│     ↓                                                                           │
│  2. 最もコストが高いノードを特定                                                 │
│     ↓                                                                           │
│  3. 見積もりと実際の行数の差を確認                                               │
│     ↓                                                                           │
│  4. Seq Scan が適切かどうか確認                                                  │
│     ↓                                                                           │
│  5. 改善策を適用                                                                 │
│     - インデックス追加                                                           │
│     - クエリ書き換え                                                             │
│     - 統計情報更新                                                               │
│     ↓                                                                           │
│  6. EXPLAIN ANALYZE で効果を確認                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 6.2 問題のあるクエリの例

```sql
-- 問題のあるクエリ
EXPLAIN (ANALYZE, BUFFERS)
SELECT o.*, u.name, u.email
FROM orders o
JOIN users u ON o.user_id = u.id
WHERE o.status = 'pending'
  AND o.created_at > NOW() - INTERVAL '7 days'
ORDER BY o.created_at DESC
LIMIT 100;
```

```
                                        QUERY PLAN
------------------------------------------------------------------------------------------
 Limit  (cost=15000.00..15000.25 rows=100 width=250)
        (actual time=850.123..850.145 rows=100 loops=1)
   Buffers: shared hit=50000 read=10000
   →  Sort  (cost=15000.00..15050.00 rows=20000 width=250)
            (actual time=850.120..850.130 rows=100 loops=1)
         Sort Key: o.created_at DESC
         Sort Method: top-N heapsort  Memory: 50kB
         Buffers: shared hit=50000 read=10000
         →  Hash Join  (cost=500.00..14000.00 rows=20000 width=250)
                       (actual time=5.000..800.000 rows=18500 loops=1)
               Hash Cond: (o.user_id = u.id)
               Buffers: shared hit=50000 read=10000
               →  Seq Scan on orders o  (cost=0.00..12000.00 rows=20000 width=200)
                                         (actual time=0.050..700.000 rows=18500 loops=1)
                     Filter: ((status = 'pending') AND (created_at > ...))
                     Rows Removed by Filter: 981500
                     Buffers: shared hit=49000 read=10000
               →  Hash  (cost=300.00..300.00 rows=10000 width=50)
                         (actual time=4.500..4.500 rows=10000 loops=1)
                     Buckets: 16384  Batches: 1  Memory Usage: 600kB
                     →  Seq Scan on users u  (cost=0.00..300.00 rows=10000 width=50)
 Planning Time: 0.500 ms
 Execution Time: 850.500 ms
```

### 6.3 問題の分析

```
問題点:
1. orders テーブルで Seq Scan が発生
   - 100万行から18500行をフィルタ
   - Rows Removed by Filter: 981500 ← 大量の行が捨てられている

2. ディスクI/O が多い
   - Buffers: read=10000 ← ディスクからの読み取り

解決策:
1. 複合インデックスを作成
2. 部分インデックスを検討
```

### 6.4 改善後

```sql
-- 複合インデックスを作成
CREATE INDEX orders_status_created_at_idx ON orders (status, created_at DESC);

-- 改善後のクエリを確認
EXPLAIN (ANALYZE, BUFFERS)
SELECT o.*, u.name, u.email
FROM orders o
JOIN users u ON o.user_id = u.id
WHERE o.status = 'pending'
  AND o.created_at > NOW() - INTERVAL '7 days'
ORDER BY o.created_at DESC
LIMIT 100;
```

```
                                        QUERY PLAN
------------------------------------------------------------------------------------------
 Limit  (cost=0.86..250.00 rows=100 width=250)
        (actual time=0.050..5.500 rows=100 loops=1)
   Buffers: shared hit=450
   →  Nested Loop  (cost=0.86..5000.00 rows=20000 width=250)
                   (actual time=0.048..5.480 rows=100 loops=1)
         Buffers: shared hit=450
         →  Index Scan using orders_status_created_at_idx on orders o
              (cost=0.43..2500.00 rows=20000 width=200)
              (actual time=0.025..0.200 rows=100 loops=1)
               Index Cond: ((status = 'pending') AND (created_at > ...))
               Buffers: shared hit=50
         →  Index Scan using users_pkey on users u
              (cost=0.43..0.50 rows=1 width=50)
              (actual time=0.040..0.040 rows=1 loops=100)
               Index Cond: (id = o.user_id)
               Buffers: shared hit=400
 Planning Time: 0.300 ms
 Execution Time: 5.600 ms
```

```
改善結果:
- 実行時間: 850ms → 5.6ms（約150倍高速化）
- バッファ読み取り: 60000 → 450（99%削減）
- Seq Scan → Index Scan に変更
```

---

## 7. プランナーの制御

### 7.1 プランナー設定

```sql
-- 特定のプラン選択を無効化（デバッグ用）
SET enable_seqscan = off;      -- Seq Scan を無効化
SET enable_indexscan = off;    -- Index Scan を無効化
SET enable_hashjoin = off;     -- Hash Join を無効化
SET enable_nestloop = off;     -- Nested Loop を無効化
SET enable_mergejoin = off;    -- Merge Join を無効化

-- コスト見積もりパラメータ
SET random_page_cost = 1.1;    -- SSD の場合（デフォルト4.0）
SET seq_page_cost = 1.0;       -- シーケンシャルI/Oのコスト
SET effective_cache_size = '4GB';  -- 利用可能なキャッシュサイズ見積もり
```

### 7.2 ヒントによるプラン制御（pg_hint_plan）

pg_hint_plan 拡張を使うと、特定のプランを強制できます。

```sql
-- pg_hint_plan のインストール（拡張が必要）
CREATE EXTENSION pg_hint_plan;

-- インデックス使用を強制
/*+ IndexScan(users users_email_idx) */
SELECT * FROM users WHERE email = 'test@example.com';

-- 結合順序と結合方法を強制
/*+ Leading(orders users) NestLoop(orders users) */
SELECT * FROM orders o JOIN users u ON o.user_id = u.id;
```

> **注意**: pg_hint_plan は本番環境での使用には注意が必要です。
> プランナーの判断が間違っている根本原因を解決する方が望ましいです。

---

## 8. 監視とログ

### 8.1 遅いクエリのログ

```sql
-- postgresql.conf の設定
-- log_min_duration_statement = 1000  -- 1秒以上のクエリをログ

-- 現在の設定確認
SHOW log_min_duration_statement;

-- セッションで一時的に変更
SET log_min_duration_statement = '500ms';
```

### 8.2 pg_stat_statements

```sql
-- 拡張機能を有効化
CREATE EXTENSION pg_stat_statements;

-- 最も時間がかかっているクエリTOP10
SELECT
    round(total_exec_time::numeric, 2) as total_time_ms,
    calls,
    round(mean_exec_time::numeric, 2) as mean_time_ms,
    round((100 * total_exec_time / sum(total_exec_time) OVER ())::numeric, 2) as percent,
    query
FROM pg_stat_statements
ORDER BY total_exec_time DESC
LIMIT 10;

-- 統計をリセット
SELECT pg_stat_statements_reset();
```

### 8.3 auto_explain

クエリの実行計画を自動的にログに出力する拡張です。

```sql
-- postgresql.conf での設定
-- shared_preload_libraries = 'auto_explain'
-- auto_explain.log_min_duration = '1s'
-- auto_explain.log_analyze = true
-- auto_explain.log_buffers = true

-- セッションで有効化（要スーパーユーザー）
LOAD 'auto_explain';
SET auto_explain.log_min_duration = '500ms';
SET auto_explain.log_analyze = true;
```

---

## 9. ベストプラクティス

### 9.1 クエリ設計

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                      クエリ最適化のチェックリスト                                 │
├─────────────────────────────────────────────────────────────────────────────────┤
│ □ 必要な列のみを SELECT する（SELECT * を避ける）                                │
│ □ WHERE 句の条件にインデックスが効くか確認                                       │
│ □ JOIN の結合条件にインデックスがあるか確認                                      │
│ □ ORDER BY にインデックスが使えるか確認                                         │
│ □ LIMIT と組み合わせる場合、インデックスで効率化できるか確認                    │
│ □ 関数やキャストで条件列を加工していないか確認                                  │
│ □ EXPLAIN ANALYZE で実行計画を確認                                              │
│ □ 見積もりと実際の行数に大きな差がないか確認                                    │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 9.2 インデックス設計

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                        インデックス設計の原則                                    │
├─────────────────────────────────────────────────────────────────────────────────┤
│ □ 頻繁にフィルタ/ソートに使われる列にインデックス                               │
│ □ 複合インデックスは選択性の高い列を先頭に                                      │
│ □ 更新頻度が高いテーブルはインデックスを最小限に                                │
│ □ 使われていないインデックスは削除                                              │
│ □ 部分インデックスで必要なデータのみカバー                                      │
│ □ カバリングインデックスで Index Only Scan を狙う                               │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 9.3 定期メンテナンス

```sql
-- 統計情報を最新に保つ
ANALYZE;

-- 不要なデッドタプルを回収
VACUUM ANALYZE;

-- インデックスの肥大化を解消
REINDEX CONCURRENTLY INDEX idx_name;

-- 使われていないインデックスを確認
SELECT
    schemaname,
    relname,
    indexrelname,
    idx_scan,
    idx_tup_read,
    idx_tup_fetch
FROM pg_stat_user_indexes
WHERE idx_scan = 0
ORDER BY pg_relation_size(indexrelid) DESC;
```

---

## まとめ

1. **EXPLAIN ANALYZE** でクエリの実行計画を確認する
2. **スキャン方法**（Seq Scan, Index Scan, Bitmap Scan）の選択を理解する
3. **結合方法**（Nested Loop, Hash Join, Merge Join）の特性を理解する
4. **統計情報**を最新に保ち、見積もりの精度を維持する
5. よくある**パフォーマンス問題**のパターンを覚える
6. **インデックス**を適切に設計・活用する
7. **pg_stat_statements** で遅いクエリを監視する

## 次のステップ

- [dev-06-connection-pooling.md](dev-06-connection-pooling.md): コネクションプーリング
- [dba-08-planner.md](dba-08-planner.md): プランナーの詳細設定
