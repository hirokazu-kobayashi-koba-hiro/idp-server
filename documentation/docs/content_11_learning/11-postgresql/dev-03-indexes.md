# PostgreSQL インデックス設計ガイド

このドキュメントでは、PostgreSQLにおけるインデックスの仕組みと
効果的な設計方法について解説します。

---

## 目次

1. [インデックスの基本概念](#1-インデックスの基本概念)
2. [B-treeインデックス](#2-b-treeインデックス)
3. [GINインデックス](#3-ginインデックス)
4. [GiSTインデックス](#4-gistインデックス)
5. [その他のインデックス](#5-その他のインデックス)
6. [複合インデックス](#6-複合インデックス)
7. [部分インデックス](#7-部分インデックス)
8. [カバリングインデックス](#8-カバリングインデックス)
9. [インデックスの運用](#9-インデックスの運用)
10. [設計のベストプラクティス](#10-設計のベストプラクティス)

---

## 1. インデックスの基本概念

### 1.1 インデックスとは

```
┌─────────────────────────────────────────────────────────────────┐
│                    インデックスの役割                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  【本の索引のようなもの】                                       │
│                                                                 │
│  本で「PostgreSQL」という単語を探す場合:                        │
│  ❌ 全ページを順番に読む → 遅い                                │
│  ✅ 索引で「PostgreSQL → p.123」を見つける → 速い              │
│                                                                 │
│  データベースでも同様:                                          │
│  ❌ テーブル全体をスキャン（Seq Scan）→ 遅い                   │
│  ✅ インデックスで位置を特定（Index Scan）→ 速い              │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ Table                         Index (email)             │   │
│  │ ┌────┬──────────────────┐     ┌────────────────────┐   │   │
│  │ │ id │ email            │     │ alice@... → row 1 │   │   │
│  │ ├────┼──────────────────┤     │ bob@...   → row 2 │   │   │
│  │ │ 1  │ alice@example... │     │ carol@... → row 3 │   │   │
│  │ │ 2  │ bob@example...   │     │ ...               │   │   │
│  │ │ 3  │ carol@example... │     └────────────────────┘   │   │
│  │ │ ...│ ...              │     ↑ ソートされている        │   │
│  │ └────┴──────────────────┘                               │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 1.2 インデックスの種類

```
┌─────────────────────────────────────────────────────────────────┐
│                   インデックスの種類                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────┬──────────────────────────────────────────┐   │
│  │ 種類         │ 用途                                     │   │
│  ├──────────────┼──────────────────────────────────────────┤   │
│  │ B-tree       │ 等価・範囲検索（デフォルト、最も一般的）│   │
│  │ Hash         │ 等価検索のみ（PostgreSQL 10+で改善）    │   │
│  │ GIN          │ 配列、JSONB、全文検索                   │   │
│  │ GiST         │ 地理情報、範囲型、全文検索              │   │
│  │ SP-GiST      │ 不均一な分布のデータ                    │   │
│  │ BRIN         │ 大きなテーブル、時系列データ            │   │
│  └──────────────┴──────────────────────────────────────────┘   │
│                                                                 │
│  【選択の目安】                                                 │
│  ・通常の検索 → B-tree                                         │
│  ・配列/JSONB → GIN                                            │
│  ・地理情報/範囲 → GiST                                        │
│  ・時系列の大きなテーブル → BRIN                               │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 1.3 インデックスのトレードオフ

```
┌─────────────────────────────────────────────────────────────────┐
│                インデックスのトレードオフ                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  【メリット】                                                   │
│  ✅ SELECT が高速化                                            │
│  ✅ ORDER BY が高速化                                          │
│  ✅ JOIN が高速化                                              │
│                                                                 │
│  【デメリット】                                                 │
│  ❌ INSERT が遅くなる（インデックス更新が必要）                │
│  ❌ UPDATE が遅くなる（インデックス対象カラムの場合）          │
│  ❌ DELETE が遅くなる（インデックス更新が必要）                │
│  ❌ ディスク容量を消費                                         │
│                                                                 │
│  【判断基準】                                                   │
│  ・読み取りが多い → インデックス有効                           │
│  ・書き込みが多い → インデックス最小限                         │
│  ・カーディナリティが高い → インデックス有効                   │
│  ・カーディナリティが低い → インデックス効果薄い               │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. B-treeインデックス

### 2.1 B-treeの仕組み

```
┌─────────────────────────────────────────────────────────────────┐
│                    B-tree 構造                                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│                        ┌───────────────┐                        │
│                        │   50 | 100    │  ← ルートノード       │
│                        └───────┬───────┘                        │
│                 ┌──────────────┼──────────────┐                 │
│                 ▼              ▼              ▼                 │
│         ┌───────────┐  ┌───────────┐  ┌───────────┐            │
│         │ 20 | 35   │  │ 70 | 85   │  │ 120| 150  │ ← 中間     │
│         └─────┬─────┘  └─────┬─────┘  └─────┬─────┘            │
│               │              │              │                   │
│               ▼              ▼              ▼                   │
│           ┌───────┐      ┌───────┐      ┌───────┐              │
│           │ Leaf  │      │ Leaf  │      │ Leaf  │ ← リーフ    │
│           │ nodes │      │ nodes │      │ nodes │              │
│           └───────┘      └───────┘      └───────┘              │
│                                                                 │
│  【特徴】                                                       │
│  ・ソート順を維持                                               │
│  ・等価検索: O(log n)                                          │
│  ・範囲検索: O(log n + k) ※k = 結果件数                       │
│  ・ORDER BY にも使える                                          │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 B-treeが有効な操作

```sql
-- B-treeインデックスの作成
CREATE INDEX idx_users_email ON users (email);

-- 等価検索 ✅
SELECT * FROM users WHERE email = 'alice@example.com';

-- 範囲検索 ✅
SELECT * FROM users WHERE created_at >= '2024-01-01';
SELECT * FROM users WHERE created_at BETWEEN '2024-01-01' AND '2024-12-31';

-- 前方一致 ✅
SELECT * FROM users WHERE email LIKE 'alice%';

-- ソート ✅
SELECT * FROM users ORDER BY email;

-- 中間・後方一致 ❌（インデックス使用不可）
SELECT * FROM users WHERE email LIKE '%alice%';
SELECT * FROM users WHERE email LIKE '%@example.com';

-- 関数適用 ❌（通常はインデックス使用不可）
SELECT * FROM users WHERE LOWER(email) = 'alice@example.com';
-- → 式インデックスで対応可能
CREATE INDEX idx_users_email_lower ON users (LOWER(email));
```

### 2.3 NULLとB-tree

```sql
-- B-treeはNULLも格納できる
CREATE INDEX idx_users_deleted_at ON users (deleted_at);

-- IS NULL検索にも使える
SELECT * FROM users WHERE deleted_at IS NULL;

-- NULLの位置を制御
CREATE INDEX idx_users_deleted_at_nulls_first
    ON users (deleted_at NULLS FIRST);

CREATE INDEX idx_users_deleted_at_nulls_last
    ON users (deleted_at NULLS LAST);
```

---

## 3. GINインデックス

### 3.1 GINの仕組み

```
┌─────────────────────────────────────────────────────────────────┐
│              GIN (Generalized Inverted Index)                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  【転置インデックス】                                           │
│                                                                 │
│  元データ:                                                      │
│  ┌─────┬─────────────────────────────┐                         │
│  │ id  │ tags                        │                         │
│  ├─────┼─────────────────────────────┤                         │
│  │ 1   │ ['java', 'spring', 'api']  │                         │
│  │ 2   │ ['python', 'django', 'api']│                         │
│  │ 3   │ ['java', 'android']        │                         │
│  └─────┴─────────────────────────────┘                         │
│                                                                 │
│  GINインデックス:                                               │
│  ┌──────────────┬─────────────────┐                            │
│  │ キー         │ 行リスト        │                            │
│  ├──────────────┼─────────────────┤                            │
│  │ 'android'    │ [3]             │                            │
│  │ 'api'        │ [1, 2]          │                            │
│  │ 'django'     │ [2]             │                            │
│  │ 'java'       │ [1, 3]          │                            │
│  │ 'python'     │ [2]             │                            │
│  │ 'spring'     │ [1]             │                            │
│  └──────────────┴─────────────────┘                            │
│                                                                 │
│  → 'java'を含む記事を高速に検索可能                            │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 GINの用途

```sql
-- 配列検索
CREATE TABLE articles (
    id BIGINT PRIMARY KEY,
    title TEXT,
    tags TEXT[]
);

CREATE INDEX idx_articles_tags ON articles USING gin (tags);

-- 配列に特定の値を含む
SELECT * FROM articles WHERE tags @> ARRAY['java'];

-- 配列のいずれかを含む
SELECT * FROM articles WHERE tags && ARRAY['java', 'python'];

-- JSONB検索
CREATE TABLE products (
    id BIGINT PRIMARY KEY,
    name TEXT,
    attributes JSONB
);

CREATE INDEX idx_products_attributes ON products USING gin (attributes);

-- JSONBに特定のキーと値を含む
SELECT * FROM products
WHERE attributes @> '{"color": "red"}';

-- JSONBの特定のキーが存在
SELECT * FROM products
WHERE attributes ? 'size';

-- 全文検索
CREATE INDEX idx_articles_body_fts
    ON articles USING gin (to_tsvector('english', body));

SELECT * FROM articles
WHERE to_tsvector('english', body) @@ to_tsquery('english', 'postgresql & index');
```

### 3.3 GINのオプション

```sql
-- fastupdate（デフォルト: on）
-- 更新を遅延させてバッチ処理することで書き込み性能向上
CREATE INDEX idx_articles_tags ON articles
USING gin (tags) WITH (fastupdate = on);

-- gin_pending_list_limit
-- 遅延リストの最大サイズ（デフォルト: 4MB）
CREATE INDEX idx_articles_tags ON articles
USING gin (tags) WITH (gin_pending_list_limit = 8192);  -- 8MB
```

---

## 4. GiSTインデックス

### 4.1 GiSTの仕組み

```
┌─────────────────────────────────────────────────────────────────┐
│              GiST (Generalized Search Tree)                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  【用途】                                                       │
│  ・地理空間データ（PostGIS）                                   │
│  ・範囲型（Range Types）                                        │
│  ・全文検索（ts_vector）                                        │
│  ・類似検索（pg_trgm）                                          │
│                                                                 │
│  【特徴】                                                       │
│  ・「含む」「重なる」「近い」などの演算に対応                   │
│  ・B-treeより柔軟だが、やや遅い場合もある                      │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 4.2 GiSTの用途

```sql
-- 範囲型（Range）での排他制約
CREATE TABLE reservations (
    id BIGINT PRIMARY KEY,
    room_id BIGINT NOT NULL,
    reserved_period TSTZRANGE NOT NULL,
    EXCLUDE USING GIST (room_id WITH =, reserved_period WITH &&)
);

-- 挿入時に重複期間があればエラー
INSERT INTO reservations (room_id, reserved_period)
VALUES (1, '[2024-03-01 10:00, 2024-03-01 12:00)');

-- 同じ部屋で重なる期間は挿入不可
INSERT INTO reservations (room_id, reserved_period)
VALUES (1, '[2024-03-01 11:00, 2024-03-01 13:00)');
-- ERROR: conflicting key value violates exclusion constraint

-- 地理空間検索（PostGIS）
CREATE INDEX idx_locations_point ON locations USING gist (location);

SELECT * FROM locations
WHERE ST_DWithin(location, ST_MakePoint(139.7, 35.7)::geography, 1000);

-- 類似検索（pg_trgm）
CREATE INDEX idx_users_name_gist ON users USING gist (name gist_trgm_ops);

SELECT * FROM users
WHERE name % 'John'
ORDER BY name <-> 'John'
LIMIT 10;
```

---

## 5. その他のインデックス

### 5.1 Hashインデックス

```sql
-- 等価検索のみに使用
CREATE INDEX idx_users_email_hash ON users USING hash (email);

-- 有効: 等価検索
SELECT * FROM users WHERE email = 'alice@example.com';

-- 無効: 範囲検索
SELECT * FROM users WHERE email > 'a';  -- 使われない

-- PostgreSQL 10以降でWAL対応となり、実用的になった
-- ただし、B-treeでほとんどのケースはカバー可能
```

### 5.2 BRINインデックス

```
┌─────────────────────────────────────────────────────────────────┐
│              BRIN (Block Range Index)                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  【特徴】                                                       │
│  ・非常に小さいサイズ（B-treeの数%程度）                       │
│  ・連続したブロック範囲の最小/最大値を保持                     │
│  ・時系列データに最適                                          │
│                                                                 │
│  【仕組み】                                                     │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ Block 1-128    │ min: 2024-01-01, max: 2024-01-31      │   │
│  │ Block 129-256  │ min: 2024-02-01, max: 2024-02-28      │   │
│  │ Block 257-384  │ min: 2024-03-01, max: 2024-03-31      │   │
│  │ ...            │ ...                                    │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  → 2024-02-15を検索 → Block 129-256のみスキャン               │
│                                                                 │
│  【適用条件】                                                   │
│  ・物理的な順序とカラム値の順序に相関がある                    │
│  ・テーブルが非常に大きい（数GB以上）                          │
│  ・時系列データ、ログテーブルなど                              │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

```sql
-- ログテーブル（時系列順に挿入される）
CREATE TABLE access_logs (
    id BIGINT GENERATED ALWAYS AS IDENTITY,
    accessed_at TIMESTAMPTZ NOT NULL,
    user_id BIGINT,
    path TEXT
);

-- BRINインデックス（pages_per_range でブロック範囲を指定）
CREATE INDEX idx_logs_accessed_at ON access_logs
USING brin (accessed_at) WITH (pages_per_range = 128);

-- サイズ比較
-- B-tree: 数GB
-- BRIN: 数MB
```

### 5.3 式インデックス（関数インデックス）

```sql
-- 関数を適用した結果にインデックス
CREATE INDEX idx_users_email_lower ON users (LOWER(email));

-- 使用例
SELECT * FROM users WHERE LOWER(email) = 'alice@example.com';

-- JSONBの特定のキー
CREATE INDEX idx_products_color
    ON products ((attributes->>'color'));

SELECT * FROM products WHERE attributes->>'color' = 'red';

-- 計算結果
CREATE INDEX idx_orders_year
    ON orders (EXTRACT(YEAR FROM ordered_at));

SELECT * FROM orders WHERE EXTRACT(YEAR FROM ordered_at) = 2024;
```

---

## 6. 複合インデックス

### 6.1 複合インデックスの仕組み

```
┌─────────────────────────────────────────────────────────────────┐
│                   複合インデックス                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  CREATE INDEX idx ON orders (user_id, status, ordered_at);     │
│                                                                 │
│  【インデックスの構造】                                         │
│  ┌───────────────────────────────────────────────────────┐     │
│  │ user_id │ status    │ ordered_at │ → row pointer     │     │
│  ├─────────┼───────────┼────────────┼────────────────────┤     │
│  │ 1       │ completed │ 2024-01-01 │ → ...             │     │
│  │ 1       │ completed │ 2024-02-01 │ → ...             │     │
│  │ 1       │ pending   │ 2024-03-01 │ → ...             │     │
│  │ 2       │ completed │ 2024-01-15 │ → ...             │     │
│  │ 2       │ pending   │ 2024-02-15 │ → ...             │     │
│  │ ...     │ ...       │ ...        │                    │     │
│  └───────────────────────────────────────────────────────┘     │
│                                                                 │
│  → 左から順にソートされている                                  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 6.2 複合インデックスの効果

```sql
CREATE INDEX idx_orders_user_status_date
    ON orders (user_id, status, ordered_at);

-- ✅ 効果あり（左端から使用）
SELECT * FROM orders WHERE user_id = 1;
SELECT * FROM orders WHERE user_id = 1 AND status = 'completed';
SELECT * FROM orders WHERE user_id = 1 AND status = 'completed'
                       AND ordered_at >= '2024-01-01';

-- ⚠️ 部分的に効果あり
SELECT * FROM orders WHERE user_id = 1 AND ordered_at >= '2024-01-01';
-- → user_id での絞り込みには使用、ordered_at は要検討

-- ❌ 効果なし（左端のカラムがない）
SELECT * FROM orders WHERE status = 'completed';
SELECT * FROM orders WHERE ordered_at >= '2024-01-01';

-- ソートでも効果あり
SELECT * FROM orders WHERE user_id = 1
ORDER BY status, ordered_at;  -- ✅

SELECT * FROM orders WHERE user_id = 1
ORDER BY ordered_at;  -- ⚠️ status を飛ばしている
```

### 6.3 カラム順序の設計

```
┌─────────────────────────────────────────────────────────────────┐
│              複合インデックスのカラム順序                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  【原則】                                                       │
│  1. 等価条件のカラムを先に                                      │
│  2. 範囲条件のカラムを後に                                      │
│  3. カーディナリティが高いカラムを先に                          │
│                                                                 │
│  【例】                                                         │
│  WHERE user_id = 1 AND status = 'completed'                    │
│        AND ordered_at >= '2024-01-01'                          │
│                                                                 │
│  ✅ 良い順序: (user_id, status, ordered_at)                    │
│     user_id = 等価                                              │
│     status = 等価                                               │
│     ordered_at = 範囲 (最後)                                   │
│                                                                 │
│  ❌ 悪い順序: (ordered_at, user_id, status)                    │
│     範囲条件が先頭だと、後続カラムの絞り込み効率が下がる       │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 7. 部分インデックス

### 7.1 部分インデックスとは

```
┌─────────────────────────────────────────────────────────────────┐
│                    部分インデックス                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  【概念】                                                       │
│  テーブルの一部の行のみをインデックス化                        │
│                                                                 │
│  【メリット】                                                   │
│  ・インデックスサイズの削減                                    │
│  ・更新オーバーヘッドの削減                                    │
│  ・特定条件の検索が高速化                                      │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 7.2 部分インデックスの例

```sql
-- アクティブユーザーのみインデックス
CREATE INDEX idx_users_email_active
    ON users (email)
    WHERE is_active = true;

-- 使用される
SELECT * FROM users WHERE email = 'alice@example.com' AND is_active = true;

-- 使用されない（条件が一致しない）
SELECT * FROM users WHERE email = 'alice@example.com';
SELECT * FROM users WHERE email = 'alice@example.com' AND is_active = false;

-- 未処理の注文のみインデックス
CREATE INDEX idx_orders_pending
    ON orders (user_id, ordered_at)
    WHERE status = 'pending';

-- 使用される
SELECT * FROM orders WHERE user_id = 1 AND status = 'pending';

-- ソフトデリート対応
CREATE UNIQUE INDEX idx_users_email_not_deleted
    ON users (email)
    WHERE deleted_at IS NULL;

-- deleted_atがNULLの行でのみemailがユニーク
```

### 7.3 よくある部分インデックスのパターン

```sql
-- NULLでない行のみ
CREATE INDEX idx_orders_shipped_at
    ON orders (shipped_at)
    WHERE shipped_at IS NOT NULL;

-- 最近のデータのみ
CREATE INDEX idx_logs_recent
    ON access_logs (user_id, accessed_at)
    WHERE accessed_at >= '2024-01-01';

-- 特定のステータスのみ
CREATE INDEX idx_tasks_incomplete
    ON tasks (assigned_to, due_date)
    WHERE status NOT IN ('completed', 'cancelled');
```

---

## 8. カバリングインデックス

### 8.1 カバリングインデックスとは

```
┌─────────────────────────────────────────────────────────────────┐
│                 カバリングインデックス                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  【通常のIndex Scan】                                           │
│  1. インデックスで行の位置を特定                               │
│  2. テーブル（Heap）にアクセスしてデータ取得                   │
│                                                                 │
│  【Index Only Scan（カバリングインデックス使用時）】            │
│  1. インデックスのみでデータ取得完了                           │
│  2. テーブルへのアクセス不要 → 高速                            │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ Query: SELECT id, email FROM users WHERE email = '...'  │   │
│  │                                                          │   │
│  │ Index (email) INCLUDE (id):                             │   │
│  │ ┌──────────────────────┬─────┐                          │   │
│  │ │ email                │ id  │ ← インデックスに含まれる │   │
│  │ ├──────────────────────┼─────┤                          │   │
│  │ │ alice@example.com    │ 1   │                          │   │
│  │ │ bob@example.com      │ 2   │                          │   │
│  │ └──────────────────────┴─────┘                          │   │
│  │                                                          │   │
│  │ → テーブルにアクセスせず回答可能                        │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 8.2 INCLUDEを使用したカバリングインデックス

```sql
-- PostgreSQL 11+
CREATE INDEX idx_users_email_covering
    ON users (email) INCLUDE (id, name);

-- このクエリはIndex Only Scanになる可能性が高い
SELECT id, name FROM users WHERE email = 'alice@example.com';

-- 複合インデックス + INCLUDE
CREATE INDEX idx_orders_user_covering
    ON orders (user_id, status)
    INCLUDE (total_amount, ordered_at);

-- Index Only Scan
SELECT total_amount, ordered_at
FROM orders
WHERE user_id = 1 AND status = 'completed';
```

### 8.3 Index Only Scanの条件

```sql
-- Index Only Scanが使われるための条件
-- 1. SELECTするカラムがすべてインデックスに含まれている
-- 2. Visibility Map で可視性が確認できる（VACUUMが実行されている）

-- 確認方法
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, email FROM users WHERE email = 'alice@example.com';

-- "Index Only Scan" と表示されればOK
-- "Heap Fetches: 0" なら最高効率
```

---

## 9. インデックスの運用

### 9.1 インデックスの確認

```sql
-- テーブルのインデックス一覧
SELECT
    indexname,
    indexdef
FROM pg_indexes
WHERE tablename = 'users';

-- インデックスのサイズ
SELECT
    indexrelname AS index_name,
    pg_size_pretty(pg_relation_size(indexrelid)) AS index_size
FROM pg_stat_user_indexes
WHERE relname = 'users'
ORDER BY pg_relation_size(indexrelid) DESC;

-- インデックスの使用状況
SELECT
    indexrelname AS index_name,
    idx_scan AS times_used,
    idx_tup_read AS tuples_read,
    idx_tup_fetch AS tuples_fetched
FROM pg_stat_user_indexes
WHERE relname = 'users';

-- 未使用のインデックスを検出
SELECT
    schemaname,
    relname AS table_name,
    indexrelname AS index_name,
    idx_scan AS times_used,
    pg_size_pretty(pg_relation_size(indexrelid)) AS index_size
FROM pg_stat_user_indexes
WHERE idx_scan = 0
  AND indexrelname NOT LIKE '%_pkey'
ORDER BY pg_relation_size(indexrelid) DESC;
```

### 9.2 インデックスの作成オプション

```sql
-- 通常の作成（テーブルロック発生）
CREATE INDEX idx_users_email ON users (email);

-- CONCURRENTLY: ロックなしで作成（時間はかかる）
CREATE INDEX CONCURRENTLY idx_users_email ON users (email);

-- 失敗した場合の対処
-- INVALIDなインデックスが残ることがある
DROP INDEX CONCURRENTLY idx_users_email;
-- 再作成
CREATE INDEX CONCURRENTLY idx_users_email ON users (email);

-- インデックスの再構築
REINDEX INDEX idx_users_email;

-- テーブル全体のインデックス再構築
REINDEX TABLE users;

-- CONCURRENTLY で再構築（PostgreSQL 12+）
REINDEX INDEX CONCURRENTLY idx_users_email;
```

### 9.3 インデックスの膨張対策

```sql
-- インデックスの膨張確認
SELECT
    nspname AS schema_name,
    relname AS index_name,
    pg_size_pretty(pg_relation_size(indexrelid)) AS index_size,
    idx_scan,
    idx_tup_read
FROM pg_stat_user_indexes
JOIN pg_class ON pg_class.oid = indexrelid
JOIN pg_namespace ON pg_namespace.oid = pg_class.relnamespace
ORDER BY pg_relation_size(indexrelid) DESC
LIMIT 20;

-- 膨張が疑われる場合はREINDEX
REINDEX INDEX CONCURRENTLY idx_users_email;

-- または pg_repack を使用
-- pg_repack -d mydb --only-indexes -t users
```

---

## 10. 設計のベストプラクティス

### 10.1 インデックス設計のチェックリスト

```
┌─────────────────────────────────────────────────────────────────┐
│              インデックス設計チェックリスト                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ✅ 主キーにはインデックスが自動作成される                      │
│  ✅ UNIQUE制約にもインデックスが自動作成される                  │
│  ✅ 外部キーには明示的にインデックスを作成する                  │
│  ✅ WHERE句で頻繁に使われるカラムにインデックス                 │
│  ✅ JOIN条件のカラムにインデックス                              │
│  ✅ ORDER BYで使われるカラムにインデックス                      │
│                                                                 │
│  ⚠️ 低カーディナリティ（値の種類が少ない）はインデックス効果薄 │
│  ⚠️ 書き込みが多いテーブルはインデックス最小限に               │
│  ⚠️ 未使用のインデックスは削除を検討                           │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 10.2 よくあるパターン

```sql
-- 外部キーにはインデックスを作成
CREATE TABLE orders (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    ...
);
CREATE INDEX idx_orders_user_id ON orders (user_id);

-- ステータス検索が多い場合
CREATE INDEX idx_orders_status ON orders (status)
    WHERE status IN ('pending', 'processing');

-- 日時範囲検索
CREATE INDEX idx_orders_ordered_at ON orders (ordered_at DESC);

-- 複合条件（よく一緒に使われるカラム）
CREATE INDEX idx_orders_user_status_date
    ON orders (user_id, status, ordered_at DESC);

-- 全文検索
CREATE INDEX idx_articles_body_fts
    ON articles USING gin (to_tsvector('english', body));

-- JSONB
CREATE INDEX idx_users_preferences
    ON users USING gin (preferences jsonb_path_ops);
```

### 10.3 アンチパターン

```sql
-- ❌ 全カラムにインデックス
-- 書き込み性能が大幅に低下

-- ❌ 低カーディナリティカラムへのインデックス
CREATE INDEX idx_users_gender ON users (gender);  -- 効果薄い

-- ❌ 重複するインデックス
CREATE INDEX idx_a ON orders (user_id);
CREATE INDEX idx_b ON orders (user_id, status);
-- idx_a は idx_b でカバーできる

-- ❌ 使われないインデックス
-- 定期的に pg_stat_user_indexes を確認して削除
```

---

## 参考リンク

- [PostgreSQL公式ドキュメント - インデックス](https://www.postgresql.org/docs/current/indexes.html)
- [PostgreSQL公式ドキュメント - インデックスの種類](https://www.postgresql.org/docs/current/indexes-types.html)
- [PostgreSQL公式ドキュメント - 式インデックス](https://www.postgresql.org/docs/current/indexes-expressional.html)
- [PostgreSQL公式ドキュメント - 部分インデックス](https://www.postgresql.org/docs/current/indexes-partial.html)
