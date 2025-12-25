# PostgreSQL PL/pgSQL 基本編

## 所要時間
約40分

## 学べること
- PL/pgSQL（PostgreSQL手続き型言語）の基礎
- 関数とプロシージャの作成方法
- 基本的なエラーハンドリング
- デバッグ手法
- 基本的なベストプラクティス

## 前提知識
- SQLの基本操作（SELECT、INSERT、UPDATE、DELETE）
- トランザクションの基礎

---

## 1. PL/pgSQLとは

### 1.1 基本概念

**PL/pgSQL**（Procedural Language/PostgreSQL）は、PostgreSQL専用の手続き型プログラミング言語です。

```
┌─────────────────────────────────────────────────────────────┐
│               SQL vs PL/pgSQL                               │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  【SQL】                                                    │
│  - 宣言的（What: 何を取得するか）                           │
│  - 単一の問い合わせや更新                                    │
│  - 制御構造なし                                              │
│                                                             │
│  【PL/pgSQL】                                               │
│  - 手続き的（How: どのように処理するか）                     │
│  - 複数の操作を組み合わせ                                    │
│  - IF/LOOP等の制御構造あり                                  │
│  - 変数、エラーハンドリング                                  │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 なぜPL/pgSQLを使うのか

**メリット**:
- **パフォーマンス**: 複数のSQL文をまとめて実行（ネットワークラウンドトリップ削減）
- **カプセル化**: 複雑なビジネスロジックをDB側に集約
- **再利用性**: 共通処理を関数化して複数箇所で利用
- **トランザクション制御**: エラー時の自動ロールバック

**デメリット**:
- **移植性**: PostgreSQL固有の機能（他のDBMSに移行しにくい）
- **デバッグ**: アプリケーションコードよりデバッグが難しい
- **テスト**: 単体テストの作成が複雑

**使い分けの指針**:
```
複雑なビジネスロジック → アプリケーション層
データ整合性の保証     → PL/pgSQL（トリガー、関数）
メンテナンス処理       → PL/pgSQL（クリーンアップ、集計）
```

### 1.3 SQL関数との違い

| 項目 | SQL関数 | PL/pgSQL関数 |
|------|---------|-------------|
| 構文 | シンプル | 複雑（変数、制御構造） |
| パフォーマンス | インライン化可能 | 関数呼び出しオーバーヘッド |
| 制御構造 | なし | IF/LOOP/CASE等 |
| 変数 | なし | 宣言可能 |
| 用途 | 単純な計算 | 複雑なロジック |

---

## 2. 基本構文

### 2.1 関数の作成

```sql
CREATE OR REPLACE FUNCTION function_name(param1 type, param2 type)
RETURNS return_type AS $$
DECLARE
    -- 変数宣言
    variable_name type;
BEGIN
    -- 処理内容
    RETURN result;
END;
$$ LANGUAGE plpgsql;
```

**最も単純な例**:

```sql
-- "Hello, World!" を返す関数
CREATE OR REPLACE FUNCTION hello_world()
RETURNS TEXT AS $$
BEGIN
    RETURN 'Hello, World!';
END;
$$ LANGUAGE plpgsql;

-- 実行
SELECT hello_world();
-- 結果: "Hello, World!"
```

### 2.2 変数宣言

```sql
CREATE OR REPLACE FUNCTION variable_example()
RETURNS TEXT AS $$
DECLARE
    -- 基本的な変数宣言
    v_name TEXT := 'Alice';
    v_count INTEGER := 0;
    v_price NUMERIC(10, 2);
    v_created_at TIMESTAMP := now();

    -- テーブルのカラム型を使用
    v_user_id users.id%TYPE;

    -- レコード型
    v_user_record users%ROWTYPE;
BEGIN
    -- 変数に値を代入
    v_price := 1000.50;
    v_count := v_count + 1;

    -- SELECT結果を変数に代入
    SELECT id, name INTO v_user_id, v_name
    FROM users
    WHERE email = 'alice@example.com';

    -- レコード全体を取得
    SELECT * INTO v_user_record
    FROM users
    WHERE id = 1;

    RETURN v_user_record.name;
END;
$$ LANGUAGE plpgsql;
```

**変数命名規則のベストプラクティス**:
- 変数には`v_`プレフィックスを付ける（例: `v_user_id`）
- パラメータには`p_`プレフィックスを付ける（例: `p_user_id`）
- カラム名との衝突を避ける

### 2.3 制御構造

#### IF文

```sql
CREATE OR REPLACE FUNCTION check_age_category(p_age INTEGER)
RETURNS TEXT AS $$
BEGIN
    IF p_age < 0 THEN
        RAISE EXCEPTION 'Age cannot be negative';
    ELSIF p_age < 18 THEN
        RETURN 'Minor';
    ELSIF p_age < 65 THEN
        RETURN 'Adult';
    ELSE
        RETURN 'Senior';
    END IF;
END;
$$ LANGUAGE plpgsql;
```

#### CASE文

```sql
CREATE OR REPLACE FUNCTION get_order_status_label(p_status TEXT)
RETURNS TEXT AS $$
BEGIN
    RETURN CASE p_status
        WHEN 'pending' THEN '処理中'
        WHEN 'completed' THEN '完了'
        WHEN 'cancelled' THEN 'キャンセル'
        ELSE '不明'
    END;
END;
$$ LANGUAGE plpgsql;
```

#### LOOP

```sql
CREATE OR REPLACE FUNCTION sum_to_n(p_n INTEGER)
RETURNS INTEGER AS $$
DECLARE
    v_sum INTEGER := 0;
    v_i INTEGER := 1;
BEGIN
    LOOP
        v_sum := v_sum + v_i;
        v_i := v_i + 1;

        EXIT WHEN v_i > p_n;  -- ループ終了条件
    END LOOP;

    RETURN v_sum;
END;
$$ LANGUAGE plpgsql;
```

#### WHILE LOOP

```sql
CREATE OR REPLACE FUNCTION sum_to_n_while(p_n INTEGER)
RETURNS INTEGER AS $$
DECLARE
    v_sum INTEGER := 0;
    v_i INTEGER := 1;
BEGIN
    WHILE v_i <= p_n LOOP
        v_sum := v_sum + v_i;
        v_i := v_i + 1;
    END LOOP;

    RETURN v_sum;
END;
$$ LANGUAGE plpgsql;
```

#### FOR LOOP

```sql
-- 範囲指定のFORループ
CREATE OR REPLACE FUNCTION sum_to_n_for(p_n INTEGER)
RETURNS INTEGER AS $$
DECLARE
    v_sum INTEGER := 0;
BEGIN
    FOR i IN 1..p_n LOOP
        v_sum := v_sum + i;
    END LOOP;

    RETURN v_sum;
END;
$$ LANGUAGE plpgsql;

-- クエリ結果をループ
CREATE OR REPLACE FUNCTION process_all_users()
RETURNS VOID AS $$
DECLARE
    v_user RECORD;
BEGIN
    FOR v_user IN SELECT * FROM users LOOP
        RAISE NOTICE 'Processing user: %', v_user.name;
        -- 何か処理をする
    END LOOP;
END;
$$ LANGUAGE plpgsql;
```

---

## 3. 関数の種類

### 3.1 スカラー関数（単一値を返す）

```sql
CREATE OR REPLACE FUNCTION calculate_tax(p_amount NUMERIC)
RETURNS NUMERIC AS $$
BEGIN
    RETURN p_amount * 0.1;
END;
$$ LANGUAGE plpgsql;

-- 使用例
SELECT
    id,
    total_amount,
    calculate_tax(total_amount) AS tax
FROM orders;
```

### 3.2 テーブル返却関数（複数行を返す）

```sql
-- RETURNS TABLE構文
CREATE OR REPLACE FUNCTION get_high_value_orders(p_threshold NUMERIC)
RETURNS TABLE (
    order_id INTEGER,
    user_name TEXT,
    amount NUMERIC
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        o.id,
        u.name,
        o.total_amount
    FROM orders o
    JOIN users u ON o.user_id = u.id
    WHERE o.total_amount >= p_threshold
    ORDER BY o.total_amount DESC;
END;
$$ LANGUAGE plpgsql;

-- 使用例
SELECT * FROM get_high_value_orders(2000);
```

### 3.3 OUT パラメータ

```sql
CREATE OR REPLACE FUNCTION get_user_stats(
    p_user_id INTEGER,
    OUT total_orders INTEGER,
    OUT total_spent NUMERIC,
    OUT avg_order_value NUMERIC
) AS $$
BEGIN
    SELECT
        COUNT(*),
        COALESCE(SUM(total_amount), 0),
        COALESCE(AVG(total_amount), 0)
    INTO total_orders, total_spent, avg_order_value
    FROM orders
    WHERE user_id = p_user_id;
END;
$$ LANGUAGE plpgsql;

-- 使用例
SELECT * FROM get_user_stats(1);
-- または
SELECT total_orders, total_spent FROM get_user_stats(1);
```

### 3.4 プロシージャ（PROCEDURE）

PostgreSQL 11以降で使用可能。関数と異なり、戻り値を持たず、トランザクション制御が可能。

```sql
CREATE OR REPLACE PROCEDURE update_user_email(
    p_user_id INTEGER,
    p_new_email TEXT
) AS $$
BEGIN
    UPDATE users
    SET email = p_new_email,
        updated_at = now()
    WHERE id = p_user_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'User % not found', p_user_id;
    END IF;

    -- PostgreSQL 11+ではプロシージャ内でCOMMIT可能
    COMMIT;
END;
$$ LANGUAGE plpgsql;

-- 実行（CALLを使用）
CALL update_user_email(1, 'newemail@example.com');
```

**関数 vs プロシージャ**:

| 項目 | FUNCTION | PROCEDURE |
|------|----------|-----------|
| 戻り値 | 必須 | なし |
| 呼び出し | SELECT文で実行 | CALL文で実行 |
| トランザクション | 制御不可 | COMMIT/ROLLBACK可能（PG11+） |
| 用途 | 計算、データ取得 | データ更新、メンテナンス |

---

## 4. エラーハンドリング

### 4.1 RAISE文

```sql
-- 各種ログレベル
CREATE OR REPLACE FUNCTION raise_examples()
RETURNS VOID AS $$
BEGIN
    -- DEBUG: デバッグ情報（デフォルトでは表示されない）
    RAISE DEBUG 'This is a debug message';

    -- NOTICE: 通知（ログに記録される）
    RAISE NOTICE 'Processing started at %', now();

    -- WARNING: 警告（ログに記録、処理は継続）
    RAISE WARNING 'This operation may take a long time';

    -- EXCEPTION: エラー（処理を中断、ロールバック）
    RAISE EXCEPTION 'Critical error occurred';

    -- この行は実行されない
    RAISE NOTICE 'This will not be executed';
END;
$$ LANGUAGE plpgsql;
```

**パラメータの埋め込み**:

```sql
CREATE OR REPLACE FUNCTION delete_user(p_user_id INTEGER)
RETURNS VOID AS $$
DECLARE
    v_row_count INTEGER;
BEGIN
    DELETE FROM users WHERE id = p_user_id;
    GET DIAGNOSTICS v_row_count = ROW_COUNT;

    IF v_row_count = 0 THEN
        -- %は値を埋め込むプレースホルダー
        RAISE EXCEPTION 'User with ID % not found', p_user_id;
    END IF;

    RAISE NOTICE 'Deleted % user(s)', v_row_count;
END;
$$ LANGUAGE plpgsql;
```

### 4.2 EXCEPTION句

```sql
CREATE OR REPLACE FUNCTION safe_divide(p_a NUMERIC, p_b NUMERIC)
RETURNS NUMERIC AS $$
BEGIN
    RETURN p_a / p_b;
EXCEPTION
    WHEN division_by_zero THEN
        RAISE WARNING 'Division by zero detected, returning NULL';
        RETURN NULL;
    WHEN OTHERS THEN
        -- すべてのエラーをキャッチ
        RAISE WARNING 'Unexpected error: %', SQLERRM;
        RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- 使用例
SELECT safe_divide(10, 2);  -- 5
SELECT safe_divide(10, 0);  -- NULL (警告が出る)
```

### 4.3 標準エラーコード

主要なエラーコード（全リストは[公式ドキュメント](https://www.postgresql.org/docs/current/errcodes-appendix.html)参照）:

```sql
CREATE OR REPLACE FUNCTION comprehensive_error_handling()
RETURNS VOID AS $$
BEGIN
    -- 何か処理

EXCEPTION
    WHEN unique_violation THEN
        RAISE EXCEPTION 'Duplicate key violation';
    WHEN foreign_key_violation THEN
        RAISE EXCEPTION 'Foreign key constraint violation';
    WHEN not_null_violation THEN
        RAISE EXCEPTION 'NOT NULL constraint violation';
    WHEN check_violation THEN
        RAISE EXCEPTION 'CHECK constraint violation';
    WHEN no_data_found THEN
        RAISE EXCEPTION 'No data found';
    WHEN too_many_rows THEN
        RAISE EXCEPTION 'Query returned more than one row';
    WHEN OTHERS THEN
        -- SQLERRM: エラーメッセージ
        -- SQLSTATE: エラーコード
        RAISE EXCEPTION 'Unexpected error: % (SQLSTATE: %)', SQLERRM, SQLSTATE;
END;
$$ LANGUAGE plpgsql;
```

---

## 5. デバッグとテスト

### 5.1 RAISE NOTICEでのデバッグ

```sql
CREATE OR REPLACE FUNCTION debug_example(p_user_id INTEGER)
RETURNS TEXT AS $$
DECLARE
    v_user_record users%ROWTYPE;
    v_order_count INTEGER;
BEGIN
    RAISE NOTICE 'Starting debug_example with user_id=%', p_user_id;

    -- ユーザー取得
    SELECT * INTO v_user_record FROM users WHERE id = p_user_id;
    RAISE NOTICE 'Found user: name=%, email=%', v_user_record.name, v_user_record.email;

    -- 注文数取得
    SELECT COUNT(*) INTO v_order_count FROM orders WHERE user_id = p_user_id;
    RAISE NOTICE 'User has % orders', v_order_count;

    RETURN format('User %s has %s orders', v_user_record.name, v_order_count);
END;
$$ LANGUAGE plpgsql;

-- 実行するとNOTICEメッセージが表示される
SELECT debug_example(1);
```

**psqlでのログレベル設定**:

```sql
-- デバッグメッセージを表示
SET client_min_messages = DEBUG;

-- 通常に戻す
SET client_min_messages = NOTICE;
```

### 5.2 DO $$ ブロックでのアドホック実行

関数を作成せずに、その場でPL/pgSQLコードを実行できます。

```sql
-- 単発のメンテナンス処理等に便利
DO $$
DECLARE
    v_count INTEGER;
BEGIN
    -- 古いレコードを削除
    DELETE FROM orders
    WHERE status = 'cancelled'
      AND ordered_at < now() - INTERVAL '1 year';

    GET DIAGNOSTICS v_count = ROW_COUNT;

    RAISE NOTICE 'Deleted % cancelled orders', v_count;
END $$;
```

### 5.3 pg_stat_statementsでのパフォーマンス分析

```sql
-- pg_stat_statements拡張を有効化
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

-- 関数の実行統計を確認
SELECT
    query,
    calls,
    total_exec_time,
    mean_exec_time,
    max_exec_time
FROM pg_stat_statements
WHERE query LIKE '%function_name%'
ORDER BY mean_exec_time DESC;
```

---

## 6. ベストプラクティス

### 6.1 関数名の命名規則

```sql
-- ✅ 推奨
get_user_by_id()         -- 取得
create_user()            -- 作成
update_user_email()      -- 更新
delete_old_orders()      -- 削除
calculate_tax()          -- 計算
validate_email()         -- 検証
is_admin()               -- 真偽値チェック

-- ❌ 非推奨
userById()               -- キャメルケース（PostgreSQLでは小文字に変換される）
DoSomething()            -- 動作が不明確
```

### 6.2 COMMENTの記述

```sql
CREATE OR REPLACE FUNCTION calculate_order_total(p_order_id INTEGER)
RETURNS NUMERIC AS $$
DECLARE
    v_total NUMERIC;
BEGIN
    SELECT SUM(quantity * unit_price)
    INTO v_total
    FROM order_items
    WHERE order_id = p_order_id;

    RETURN COALESCE(v_total, 0);
END;
$$ LANGUAGE plpgsql STABLE;

-- 関数の説明を記述
COMMENT ON FUNCTION calculate_order_total(INTEGER) IS
'Calculate the total amount for a given order by summing up all order items.
Returns 0 if the order has no items.';
```

### 6.3 CREATE OR REPLACEの活用

```sql
-- 常に CREATE OR REPLACE を使用
-- （関数が存在しない場合は作成、存在する場合は置換）
CREATE OR REPLACE FUNCTION my_function()
RETURNS INTEGER AS $$
BEGIN
    RETURN 1;
END;
$$ LANGUAGE plpgsql;
```

### 6.4 戻り値の設計

```sql
-- パターン1: BOOLEAN（成功/失敗）
CREATE OR REPLACE FUNCTION try_lock_user(p_user_id INTEGER)
RETURNS BOOLEAN AS $$
BEGIN
    UPDATE users SET is_locked = TRUE WHERE id = p_user_id;
    RETURN FOUND;  -- 更新できたらTRUE
END;
$$ LANGUAGE plpgsql;

-- パターン2: VOID（副作用のみ）
CREATE OR REPLACE FUNCTION log_event(p_event_type TEXT, p_message TEXT)
RETURNS VOID AS $$
BEGIN
    INSERT INTO event_logs (event_type, message) VALUES (p_event_type, p_message);
END;
$$ LANGUAGE plpgsql;

-- パターン3: EXCEPTION（エラー時は例外を投げる）
CREATE OR REPLACE FUNCTION create_user(p_email TEXT)
RETURNS INTEGER AS $$
DECLARE
    v_user_id INTEGER;
BEGIN
    INSERT INTO users (email) VALUES (p_email) RETURNING id INTO v_user_id;
    RETURN v_user_id;
    -- エラー時は自動的に例外が投げられる
END;
$$ LANGUAGE plpgsql;
```

---

## 7. よくあるエラーと対処法

### 7.1 "function does not exist"

```sql
-- ❌ エラー
SELECT my_function(1);
-- ERROR: function my_function(integer) does not exist

-- 原因1: 関数が存在しない → 作成する
-- 原因2: 引数の型が一致しない
CREATE FUNCTION my_function(p_id TEXT) RETURNS INTEGER ...;
SELECT my_function(1);  -- INTEGER を渡しているがTEXT を期待

-- 対処: 明示的にキャスト
SELECT my_function(1::TEXT);

-- 原因3: スキーマが異なる
SELECT public.my_function(1);  -- スキーマを明示
```

### 7.2 "type mismatch"

```sql
-- ❌ エラー
CREATE OR REPLACE FUNCTION return_type_mismatch()
RETURNS INTEGER AS $$
BEGIN
    RETURN 'not a number';  -- TEXT を返しているが、戻り値はINTEGER
END;
$$ LANGUAGE plpgsql;
-- ERROR: return type mismatch in function declared to return integer

-- 対処: 戻り値の型を一致させる
RETURN 123;
```

### 7.3 "control reached end of function without RETURN"

```sql
-- ❌ エラー
CREATE OR REPLACE FUNCTION missing_return(p_value INTEGER)
RETURNS TEXT AS $$
BEGIN
    IF p_value > 0 THEN
        RETURN 'positive';
    END IF;
    -- p_value <= 0 の場合、RETURNがない
END;
$$ LANGUAGE plpgsql;

SELECT missing_return(0);
-- ERROR: control reached end of function without RETURN

-- ✅ 対処: すべてのパスでRETURNを保証
CREATE OR REPLACE FUNCTION fixed_return(p_value INTEGER)
RETURNS TEXT AS $$
BEGIN
    IF p_value > 0 THEN
        RETURN 'positive';
    ELSE
        RETURN 'non-positive';
    END IF;
END;
$$ LANGUAGE plpgsql;
```

---

## 8. 参考リンク

### 公式ドキュメント
- [PostgreSQL公式ドキュメント - PL/pgSQL](https://www.postgresql.org/docs/current/plpgsql.html)
- [PostgreSQL公式ドキュメント - 関数とプロシージャ](https://www.postgresql.org/docs/current/xfunc.html)
- [PostgreSQL公式ドキュメント - エラーコード](https://www.postgresql.org/docs/current/errcodes-appendix.html)

### 関連する学習コンテンツ
- [dev-01-sql-basics.md](./dev-01-sql-basics.md) - SQL基礎
- [dev-04-transactions.md](./dev-04-transactions.md) - トランザクション
- [dev-08-plpgsql-advanced.md](./dev-08-plpgsql-advanced.md) - PL/pgSQL応用編

---

## まとめ

PL/pgSQL基本編では、PostgreSQLの手続き型言語の基礎を学びました。

**学んだこと**:
- ✅ PL/pgSQLの基本概念とSQLとの違い
- ✅ 変数宣言と制御構造（IF/LOOP/FOR/WHILE/CASE）
- ✅ 関数とプロシージャの作成方法
- ✅ エラーハンドリング（RAISE、EXCEPTION）
- ✅ デバッグ手法（RAISE NOTICE、DO $$ブロック）
- ✅ 基本的なベストプラクティス

**次のステップ**:
1. 実際のプロジェクトで簡単な関数を作成してみる
2. 既存のマイグレーションファイルのPL/pgSQL関数を読んで理解する
3. [dev-08-plpgsql-advanced.md](./dev-08-plpgsql-advanced.md) で応用技術を学ぶ
   - 動的SQL
   - トリガー
   - パフォーマンス最適化
   - セキュリティ
