# PostgreSQL SQL基礎ガイド

このドキュメントでは、PostgreSQLを使用するアプリケーション開発者向けに、
SQL の基本操作から実践的なクエリ作成までを解説します。

---

## 目次

1. [SQLの基本概念](#1-sqlの基本概念)
2. [データの取得（SELECT）](#2-データの取得select)
3. [データの挿入（INSERT）](#3-データの挿入insert)
4. [データの更新（UPDATE）](#4-データの更新update)
5. [データの削除（DELETE）](#5-データの削除delete)
6. [テーブル結合（JOIN）](#6-テーブル結合join)
7. [集約関数とグループ化](#7-集約関数とグループ化)
8. [サブクエリ](#8-サブクエリ)
9. [共通テーブル式（CTE）](#9-共通テーブル式cte)
10. [ウィンドウ関数](#10-ウィンドウ関数)

---

## 1. SQLの基本概念

### 1.1 SQLとは

```
┌─────────────────────────────────────────────────────────────────┐
│                    SQL (Structured Query Language)              │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  【SQLの種類】                                                  │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ DML (Data Manipulation Language) - データ操作           │   │
│  │   SELECT, INSERT, UPDATE, DELETE                        │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ DDL (Data Definition Language) - データ定義             │   │
│  │   CREATE, ALTER, DROP, TRUNCATE                         │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ DCL (Data Control Language) - アクセス制御              │   │
│  │   GRANT, REVOKE                                         │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ TCL (Transaction Control Language) - トランザクション   │   │
│  │   BEGIN, COMMIT, ROLLBACK, SAVEPOINT                    │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  【開発者が主に使用するのはDML】                                │
│  DDL/DCLはDBAまたはマイグレーションツールで管理                 │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 1.2 サンプルテーブル

本ドキュメントで使用するサンプルテーブル:

```sql
-- ユーザーテーブル
CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    department_id INTEGER,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

-- 部署テーブル
CREATE TABLE departments (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    parent_id INTEGER REFERENCES departments(id)
);

-- 注文テーブル
CREATE TABLE orders (
    id SERIAL PRIMARY KEY,
    user_id INTEGER REFERENCES users(id),
    total_amount NUMERIC(10, 2) NOT NULL,
    status VARCHAR(20) DEFAULT 'pending',
    ordered_at TIMESTAMPTZ DEFAULT now()
);

-- サンプルデータ
INSERT INTO departments (id, name, parent_id) VALUES
    (1, 'Engineering', NULL),
    (2, 'Backend', 1),
    (3, 'Frontend', 1),
    (4, 'Sales', NULL);

INSERT INTO users (id, name, email, department_id) VALUES
    (1, 'Alice', 'alice@example.com', 2),
    (2, 'Bob', 'bob@example.com', 2),
    (3, 'Charlie', 'charlie@example.com', 3),
    (4, 'Diana', 'diana@example.com', 4),
    (5, 'Eve', 'eve@example.com', NULL);

INSERT INTO orders (user_id, total_amount, status, ordered_at) VALUES
    (1, 1500.00, 'completed', '2024-01-15'),
    (1, 2300.00, 'completed', '2024-02-20'),
    (2, 800.00, 'pending', '2024-03-01'),
    (3, 1200.00, 'completed', '2024-03-05'),
    (4, 3500.00, 'cancelled', '2024-03-10');
```

---

## 2. データの取得（SELECT）

### 2.1 基本構文

```sql
SELECT column1, column2, ...
FROM table_name
WHERE condition
ORDER BY column1 [ASC|DESC]
LIMIT n OFFSET m;
```

### 2.2 基本的なSELECT

```sql
-- 全カラム取得
SELECT * FROM users;

-- 特定カラムのみ取得
SELECT id, name, email FROM users;

-- カラムに別名（エイリアス）を付ける
SELECT
    id AS user_id,
    name AS user_name,
    email AS contact_email
FROM users;

-- 計算結果を取得
SELECT
    id,
    total_amount,
    total_amount * 0.1 AS tax,
    total_amount * 1.1 AS total_with_tax
FROM orders;
```

### 2.3 条件指定（WHERE）

```sql
-- 等価比較
SELECT * FROM users WHERE department_id = 2;

-- NULL判定（= ではなく IS を使用）
SELECT * FROM users WHERE department_id IS NULL;
SELECT * FROM users WHERE department_id IS NOT NULL;

-- 複数条件（AND, OR）
SELECT * FROM users
WHERE department_id = 2 AND name LIKE 'A%';

SELECT * FROM users
WHERE department_id = 2 OR department_id = 3;

-- IN句（複数値のいずれか）
SELECT * FROM users
WHERE department_id IN (2, 3, 4);

-- BETWEEN（範囲指定）
SELECT * FROM orders
WHERE total_amount BETWEEN 1000 AND 2000;

-- LIKE（パターンマッチ）
SELECT * FROM users WHERE name LIKE 'A%';      -- Aで始まる
SELECT * FROM users WHERE name LIKE '%e';      -- eで終わる
SELECT * FROM users WHERE name LIKE '%li%';    -- liを含む
SELECT * FROM users WHERE name ILIKE '%ALICE%'; -- 大文字小文字無視
```

### 2.4 ソートと件数制限

```sql
-- 昇順（デフォルト）
SELECT * FROM users ORDER BY name ASC;

-- 降順
SELECT * FROM users ORDER BY created_at DESC;

-- 複数カラムでソート
SELECT * FROM users
ORDER BY department_id ASC, name DESC;

-- 上位N件
SELECT * FROM orders
ORDER BY total_amount DESC
LIMIT 10;

-- ページネーション（2ページ目、1ページ10件）
SELECT * FROM orders
ORDER BY id
LIMIT 10 OFFSET 10;
```

### 2.5 DISTINCT（重複除去）

```sql
-- 重複を除いた部署IDの一覧
SELECT DISTINCT department_id FROM users;

-- 複数カラムの組み合わせで重複除去
SELECT DISTINCT department_id, status FROM users, orders;
```

---

## 3. データの挿入（INSERT）

### 3.1 基本構文

```sql
-- 単一行の挿入
INSERT INTO users (name, email, department_id)
VALUES ('Frank', 'frank@example.com', 2);

-- 複数行の挿入
INSERT INTO users (name, email, department_id)
VALUES
    ('Grace', 'grace@example.com', 3),
    ('Henry', 'henry@example.com', 4),
    ('Ivy', 'ivy@example.com', 2);

-- 挿入後のIDを取得（RETURNING）
INSERT INTO users (name, email, department_id)
VALUES ('Jack', 'jack@example.com', 2)
RETURNING id;

-- 挿入した行全体を取得
INSERT INTO users (name, email, department_id)
VALUES ('Kate', 'kate@example.com', 3)
RETURNING *;
```

### 3.2 SELECT結果からの挿入

```sql
-- 別テーブルからデータを挿入
INSERT INTO users_backup (id, name, email)
SELECT id, name, email FROM users
WHERE created_at < '2024-01-01';
```

### 3.3 UPSERT（INSERT ON CONFLICT）

```sql
-- 重複時は何もしない
INSERT INTO users (id, name, email)
VALUES (1, 'Alice Updated', 'alice@example.com')
ON CONFLICT (id) DO NOTHING;

-- 重複時は更新
INSERT INTO users (id, name, email, department_id)
VALUES (1, 'Alice Updated', 'alice@example.com', 2)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    updated_at = now();

-- ユニーク制約のカラムを指定
INSERT INTO users (name, email, department_id)
VALUES ('Alice', 'alice@example.com', 3)
ON CONFLICT (email) DO UPDATE SET
    department_id = EXCLUDED.department_id,
    updated_at = now();
```

---

## 4. データの更新（UPDATE）

### 4.1 基本構文

```sql
-- 条件に一致する行を更新
UPDATE users
SET name = 'Alice Smith', updated_at = now()
WHERE id = 1;

-- 複数カラムの更新
UPDATE orders
SET
    status = 'completed',
    updated_at = now()
WHERE id = 3;

-- 計算結果で更新
UPDATE orders
SET total_amount = total_amount * 1.1
WHERE status = 'pending';
```

### 4.2 更新結果の取得（RETURNING）

```sql
-- 更新した行を取得
UPDATE users
SET department_id = 3
WHERE id = 5
RETURNING *;

-- 更新前後の値を比較（PostgreSQL固有ではないが有用）
UPDATE users
SET name = 'Alice Johnson'
WHERE id = 1
RETURNING id, name AS new_name;
```

### 4.3 他テーブルを参照した更新

```sql
-- サブクエリを使用
UPDATE users
SET department_id = (
    SELECT id FROM departments WHERE name = 'Frontend'
)
WHERE email = 'eve@example.com';

-- FROM句を使用（PostgreSQL拡張）
UPDATE orders o
SET status = 'archived'
FROM users u
WHERE o.user_id = u.id
  AND u.department_id = 4;
```

---

## 5. データの削除（DELETE）

### 5.1 基本構文

```sql
-- 条件に一致する行を削除
DELETE FROM orders WHERE status = 'cancelled';

-- 複数条件
DELETE FROM users
WHERE department_id IS NULL
  AND created_at < '2024-01-01';

-- 削除した行を取得
DELETE FROM orders
WHERE id = 5
RETURNING *;
```

### 5.2 全件削除

```sql
-- DELETE（ログあり、遅い、ロールバック可能）
DELETE FROM temp_table;

-- TRUNCATE（ログなし、高速、ロールバック不可*）
-- * PostgreSQLではトランザクション内ならロールバック可能
TRUNCATE TABLE temp_table;

-- 関連テーブルも含めてTRUNCATE
TRUNCATE TABLE orders, users CASCADE;
```

### 5.3 他テーブルを参照した削除

```sql
-- USINGを使用（PostgreSQL拡張）
DELETE FROM orders o
USING users u
WHERE o.user_id = u.id
  AND u.email = 'alice@example.com';

-- サブクエリを使用
DELETE FROM orders
WHERE user_id IN (
    SELECT id FROM users WHERE department_id = 4
);
```

---

## 6. テーブル結合（JOIN）

### 6.1 JOINの種類

```
┌─────────────────────────────────────────────────────────────────┐
│                      JOIN の種類                                │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  【INNER JOIN】両方のテーブルに存在する行のみ                   │
│  ┌─────────┐   ┌─────────┐                                     │
│  │ users   │   │  depts  │                                     │
│  │  ┌───┐  │   │  ┌───┐  │                                     │
│  │  │ ● │←─┼───┼──│ ● │  │  ← マッチした行のみ                │
│  │  └───┘  │   │  └───┘  │                                     │
│  └─────────┘   └─────────┘                                     │
│                                                                 │
│  【LEFT JOIN】左テーブルの全行 + 右のマッチ                     │
│  ┌─────────┐   ┌─────────┐                                     │
│  │ users   │   │  depts  │                                     │
│  │  ┌───┐  │   │  ┌───┐  │                                     │
│  │  │ ● │←─┼───┼──│ ● │  │                                     │
│  │  │ ○ │  │   │  └───┘  │  ← 左のみも含む（右はNULL）        │
│  │  └───┘  │   │         │                                     │
│  └─────────┘   └─────────┘                                     │
│                                                                 │
│  【RIGHT JOIN】右テーブルの全行 + 左のマッチ                    │
│  （LEFT JOINの逆、実務ではLEFT JOINに書き換えることが多い）    │
│                                                                 │
│  【FULL OUTER JOIN】両方の全行（マッチしない場合はNULL）        │
│                                                                 │
│  【CROSS JOIN】全組み合わせ（デカルト積）                       │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 6.2 INNER JOIN

```sql
-- 部署に所属しているユーザーのみ取得
SELECT
    u.id,
    u.name,
    u.email,
    d.name AS department_name
FROM users u
INNER JOIN departments d ON u.department_id = d.id;

-- 結果: Eve（department_id = NULL）は含まれない
```

### 6.3 LEFT JOIN

```sql
-- 全ユーザー（部署がない場合もNULLで含む）
SELECT
    u.id,
    u.name,
    u.email,
    d.name AS department_name
FROM users u
LEFT JOIN departments d ON u.department_id = d.id;

-- 結果: Eveも含まれる（department_name = NULL）
```

### 6.4 複数テーブルのJOIN

```sql
-- ユーザー、部署、注文を結合
SELECT
    u.name AS user_name,
    d.name AS department_name,
    o.total_amount,
    o.status
FROM users u
LEFT JOIN departments d ON u.department_id = d.id
LEFT JOIN orders o ON u.id = o.user_id
ORDER BY u.id, o.ordered_at;
```

### 6.5 自己結合（Self Join）

```sql
-- 親部署の名前を取得
SELECT
    child.name AS department,
    parent.name AS parent_department
FROM departments child
LEFT JOIN departments parent ON child.parent_id = parent.id;
```

---

## 7. 集約関数とグループ化

### 7.1 主な集約関数

```sql
-- COUNT: 行数
SELECT COUNT(*) FROM users;                    -- 全行数
SELECT COUNT(department_id) FROM users;        -- NULLを除いた行数
SELECT COUNT(DISTINCT department_id) FROM users; -- ユニークな値の数

-- SUM: 合計
SELECT SUM(total_amount) FROM orders;

-- AVG: 平均
SELECT AVG(total_amount) FROM orders;

-- MAX / MIN: 最大・最小
SELECT MAX(total_amount), MIN(total_amount) FROM orders;

-- 複数の集約を同時に
SELECT
    COUNT(*) AS order_count,
    SUM(total_amount) AS total_sales,
    AVG(total_amount) AS avg_order_value,
    MAX(total_amount) AS max_order,
    MIN(total_amount) AS min_order
FROM orders
WHERE status = 'completed';
```

### 7.2 GROUP BY

```sql
-- 部署ごとのユーザー数
SELECT
    department_id,
    COUNT(*) AS user_count
FROM users
GROUP BY department_id;

-- 部署名も表示
SELECT
    d.name AS department,
    COUNT(u.id) AS user_count
FROM departments d
LEFT JOIN users u ON d.id = u.department_id
GROUP BY d.id, d.name;

-- ユーザーごとの注文統計
SELECT
    u.name,
    COUNT(o.id) AS order_count,
    COALESCE(SUM(o.total_amount), 0) AS total_spent
FROM users u
LEFT JOIN orders o ON u.id = o.user_id
GROUP BY u.id, u.name
ORDER BY total_spent DESC;
```

### 7.3 HAVING（グループへの条件）

```sql
-- 2件以上注文したユーザー
SELECT
    user_id,
    COUNT(*) AS order_count,
    SUM(total_amount) AS total_spent
FROM orders
GROUP BY user_id
HAVING COUNT(*) >= 2;

-- 平均注文額が1000以上の部署
SELECT
    d.name AS department,
    AVG(o.total_amount) AS avg_order
FROM departments d
JOIN users u ON d.id = u.department_id
JOIN orders o ON u.id = o.user_id
GROUP BY d.id, d.name
HAVING AVG(o.total_amount) >= 1000;
```

### 7.4 FILTER句（PostgreSQL 9.4+）

```sql
-- 条件付き集約（CASE式より読みやすい）
SELECT
    COUNT(*) AS total_orders,
    COUNT(*) FILTER (WHERE status = 'completed') AS completed_orders,
    COUNT(*) FILTER (WHERE status = 'pending') AS pending_orders,
    COUNT(*) FILTER (WHERE status = 'cancelled') AS cancelled_orders,
    SUM(total_amount) FILTER (WHERE status = 'completed') AS completed_revenue
FROM orders;
```

---

## 8. サブクエリ

### 8.1 スカラサブクエリ（単一値を返す）

```sql
-- 平均以上の注文を取得
SELECT * FROM orders
WHERE total_amount > (SELECT AVG(total_amount) FROM orders);

-- 最新の注文を取得
SELECT * FROM orders
WHERE ordered_at = (SELECT MAX(ordered_at) FROM orders);
```

### 8.2 IN句でのサブクエリ

```sql
-- 注文したことがあるユーザー
SELECT * FROM users
WHERE id IN (SELECT DISTINCT user_id FROM orders);

-- 注文したことがないユーザー
SELECT * FROM users
WHERE id NOT IN (
    SELECT user_id FROM orders WHERE user_id IS NOT NULL
);
-- 注意: NOT IN はサブクエリにNULLがあると期待通り動作しない
```

### 8.3 EXISTS（存在確認）

```sql
-- 注文したことがあるユーザー（EXISTSを使用）
SELECT * FROM users u
WHERE EXISTS (
    SELECT 1 FROM orders o WHERE o.user_id = u.id
);

-- 注文したことがないユーザー
SELECT * FROM users u
WHERE NOT EXISTS (
    SELECT 1 FROM orders o WHERE o.user_id = u.id
);
-- NOT EXISTS は NOT IN より安全（NULLの問題がない）
```

### 8.4 FROM句でのサブクエリ（導出テーブル）

```sql
-- ユーザーごとの注文統計を計算してからフィルタ
SELECT *
FROM (
    SELECT
        user_id,
        COUNT(*) AS order_count,
        SUM(total_amount) AS total_spent
    FROM orders
    GROUP BY user_id
) AS user_stats
WHERE order_count >= 2;
```

### 8.5 相関サブクエリ

```sql
-- 各ユーザーの最新注文
SELECT * FROM orders o1
WHERE ordered_at = (
    SELECT MAX(ordered_at)
    FROM orders o2
    WHERE o2.user_id = o1.user_id
);
```

---

## 9. 共通テーブル式（CTE）

### 9.1 基本構文（WITH句）

```sql
-- 読みやすいクエリ構造
WITH user_order_stats AS (
    SELECT
        user_id,
        COUNT(*) AS order_count,
        SUM(total_amount) AS total_spent
    FROM orders
    GROUP BY user_id
)
SELECT
    u.name,
    u.email,
    COALESCE(s.order_count, 0) AS order_count,
    COALESCE(s.total_spent, 0) AS total_spent
FROM users u
LEFT JOIN user_order_stats s ON u.id = s.user_id
ORDER BY total_spent DESC;
```

### 9.2 複数のCTE

```sql
WITH
department_users AS (
    SELECT
        department_id,
        COUNT(*) AS user_count
    FROM users
    GROUP BY department_id
),
department_orders AS (
    SELECT
        u.department_id,
        COUNT(o.id) AS order_count,
        SUM(o.total_amount) AS total_revenue
    FROM users u
    JOIN orders o ON u.id = o.user_id
    GROUP BY u.department_id
)
SELECT
    d.name AS department,
    COALESCE(du.user_count, 0) AS user_count,
    COALESCE(do.order_count, 0) AS order_count,
    COALESCE(do.total_revenue, 0) AS total_revenue
FROM departments d
LEFT JOIN department_users du ON d.id = du.department_id
LEFT JOIN department_orders do ON d.id = do.department_id;
```

### 9.3 再帰CTE

```sql
-- 部署の階層構造を展開
WITH RECURSIVE department_tree AS (
    -- 基底ケース: ルート部署
    SELECT id, name, parent_id, 0 AS level, name::TEXT AS path
    FROM departments
    WHERE parent_id IS NULL

    UNION ALL

    -- 再帰ケース: 子部署
    SELECT
        d.id,
        d.name,
        d.parent_id,
        dt.level + 1,
        dt.path || ' > ' || d.name
    FROM departments d
    JOIN department_tree dt ON d.parent_id = dt.id
)
SELECT * FROM department_tree
ORDER BY path;

-- 結果:
-- id | name        | level | path
-- 1  | Engineering | 0     | Engineering
-- 2  | Backend     | 1     | Engineering > Backend
-- 3  | Frontend    | 1     | Engineering > Frontend
-- 4  | Sales       | 0     | Sales
```

---

## 10. ウィンドウ関数

### 10.1 ウィンドウ関数の概念

```
┌─────────────────────────────────────────────────────────────────┐
│                    ウィンドウ関数                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  【集約関数との違い】                                           │
│                                                                 │
│  集約関数 (GROUP BY):                                          │
│  ┌───────────────┐                                             │
│  │ Row 1  ──┐    │                                             │
│  │ Row 2  ──┼──► │ 1つの結果行                                 │
│  │ Row 3  ──┘    │                                             │
│  └───────────────┘                                             │
│                                                                 │
│  ウィンドウ関数 (OVER):                                         │
│  ┌───────────────┐                                             │
│  │ Row 1  ──────►│ Row 1 + 集計結果                            │
│  │ Row 2  ──────►│ Row 2 + 集計結果                            │
│  │ Row 3  ──────►│ Row 3 + 集計結果                            │
│  └───────────────┘                                             │
│                                                                 │
│  → 各行を保持したまま、集計結果を追加できる                     │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 10.2 基本的なウィンドウ関数

```sql
-- 各注文に全体の合計と平均を追加
SELECT
    id,
    user_id,
    total_amount,
    SUM(total_amount) OVER () AS grand_total,
    AVG(total_amount) OVER () AS overall_avg,
    total_amount / SUM(total_amount) OVER () * 100 AS percentage
FROM orders;
```

### 10.3 PARTITION BY（グループ内での集計）

```sql
-- ユーザーごとの合計と、全体に対する割合
SELECT
    id,
    user_id,
    total_amount,
    SUM(total_amount) OVER (PARTITION BY user_id) AS user_total,
    SUM(total_amount) OVER () AS grand_total
FROM orders;
```

### 10.4 ORDER BY と行番号関数

```sql
-- 行番号、ランキング
SELECT
    id,
    user_id,
    total_amount,
    ROW_NUMBER() OVER (ORDER BY total_amount DESC) AS row_num,
    RANK() OVER (ORDER BY total_amount DESC) AS rank,
    DENSE_RANK() OVER (ORDER BY total_amount DESC) AS dense_rank
FROM orders;

-- ユーザーごとのランキング
SELECT
    id,
    user_id,
    total_amount,
    ROW_NUMBER() OVER (
        PARTITION BY user_id
        ORDER BY total_amount DESC
    ) AS user_order_rank
FROM orders;
```

### 10.5 LAG / LEAD（前後の行を参照）

```sql
-- 前回の注文との差額を計算
SELECT
    id,
    user_id,
    ordered_at,
    total_amount,
    LAG(total_amount) OVER (
        PARTITION BY user_id
        ORDER BY ordered_at
    ) AS prev_amount,
    total_amount - LAG(total_amount) OVER (
        PARTITION BY user_id
        ORDER BY ordered_at
    ) AS diff_from_prev
FROM orders;

-- 次の注文日を取得
SELECT
    id,
    user_id,
    ordered_at,
    LEAD(ordered_at) OVER (
        PARTITION BY user_id
        ORDER BY ordered_at
    ) AS next_order_date
FROM orders;
```

### 10.6 累積集計

```sql
-- 累積合計
SELECT
    id,
    ordered_at,
    total_amount,
    SUM(total_amount) OVER (
        ORDER BY ordered_at
        ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
    ) AS running_total
FROM orders;

-- 移動平均（直近3件）
SELECT
    id,
    ordered_at,
    total_amount,
    AVG(total_amount) OVER (
        ORDER BY ordered_at
        ROWS BETWEEN 2 PRECEDING AND CURRENT ROW
    ) AS moving_avg_3
FROM orders;
```

### 10.7 FIRST_VALUE / LAST_VALUE

```sql
-- 各ユーザーの最初と最後の注文額
SELECT
    id,
    user_id,
    ordered_at,
    total_amount,
    FIRST_VALUE(total_amount) OVER (
        PARTITION BY user_id
        ORDER BY ordered_at
    ) AS first_order_amount,
    LAST_VALUE(total_amount) OVER (
        PARTITION BY user_id
        ORDER BY ordered_at
        ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING
    ) AS last_order_amount
FROM orders;
```

---

## 参考リンク

- [PostgreSQL公式ドキュメント - SQLコマンド](https://www.postgresql.org/docs/current/sql-commands.html)
- [PostgreSQL公式ドキュメント - 関数と演算子](https://www.postgresql.org/docs/current/functions.html)
- [PostgreSQL公式ドキュメント - ウィンドウ関数](https://www.postgresql.org/docs/current/tutorial-window.html)
