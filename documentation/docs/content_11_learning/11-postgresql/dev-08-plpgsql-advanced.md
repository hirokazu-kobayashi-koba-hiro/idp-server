# PostgreSQL PL/pgSQL 応用編

## 所要時間
約50分

## 学べること
- 動的SQLの安全な使い方とSQLインジェクション対策
- トリガー関数の実装（BEFORE/AFTER、NEW/OLD）
- トランザクション制御の詳細
- パフォーマンス最適化（VOLATILE/STABLE/IMMUTABLE）
- セキュリティ考慮事項（SECURITY DEFINER）

## 前提知識
- [dev-07-plpgsql-basics.md](./dev-07-plpgsql-basics.md) - PL/pgSQL基本編
- SQLの基本操作とトランザクションの理解

---

## 1. 動的SQL

### 1.1 EXECUTE文の基本

静的SQLでは対応できない、実行時に決まるテーブル名やカラム名を扱う場合に使用します。

```sql
CREATE OR REPLACE FUNCTION get_table_count(p_table_name TEXT)
RETURNS BIGINT AS $$
DECLARE
    v_count BIGINT;
BEGIN
    -- 動的にテーブル名を指定
    EXECUTE format('SELECT COUNT(*) FROM %I', p_table_name)
    INTO v_count;

    RETURN v_count;
END;
$$ LANGUAGE plpgsql;

-- 使用例
SELECT get_table_count('users');
SELECT get_table_count('orders');
```

### 1.2 format()関数による安全なクエリ構築

**絶対に守るべきルール**: SQLインジェクション対策

```sql
-- ❌ 危険: SQLインジェクションの脆弱性
CREATE OR REPLACE FUNCTION unsafe_query(p_table_name TEXT)
RETURNS BIGINT AS $$
DECLARE
    v_count BIGINT;
BEGIN
    -- これは危険！ p_table_nameに "users; DROP TABLE users; --" が渡される可能性
    EXECUTE 'SELECT COUNT(*) FROM ' || p_table_name INTO v_count;
    RETURN v_count;
END;
$$ LANGUAGE plpgsql;

-- ✅ 安全: format()関数とプレースホルダーを使用
CREATE OR REPLACE FUNCTION safe_query(p_table_name TEXT)
RETURNS BIGINT AS $$
DECLARE
    v_count BIGINT;
BEGIN
    -- %I: 識別子（テーブル名、カラム名）を安全にエスケープ
    EXECUTE format('SELECT COUNT(*) FROM %I', p_table_name)
    INTO v_count;

    RETURN v_count;
END;
$$ LANGUAGE plpgsql;
```

**format()のプレースホルダー**:

| プレースホルダー | 用途 | エスケープ |
|----------------|------|----------|
| `%I` | 識別子（テーブル名、カラム名） | ダブルクォートで囲む |
| `%L` | リテラル（文字列、数値） | シングルクォートで囲む |
| `%s` | 文字列（エスケープなし） | そのまま埋め込み（危険） |

### 1.3 実践例: 動的WHERE句

```sql
CREATE OR REPLACE FUNCTION search_users(
    p_name TEXT DEFAULT NULL,
    p_department_id INTEGER DEFAULT NULL,
    p_email_pattern TEXT DEFAULT NULL
) RETURNS TABLE (
    id INTEGER,
    name TEXT,
    email TEXT
) AS $$
DECLARE
    v_sql TEXT;
    v_where_clauses TEXT[] := ARRAY[]::TEXT[];
BEGIN
    v_sql := 'SELECT id, name, email FROM users';

    -- 動的にWHERE句を構築
    IF p_name IS NOT NULL THEN
        v_where_clauses := array_append(v_where_clauses, format('name = %L', p_name));
    END IF;

    IF p_department_id IS NOT NULL THEN
        v_where_clauses := array_append(v_where_clauses, format('department_id = %L', p_department_id));
    END IF;

    IF p_email_pattern IS NOT NULL THEN
        v_where_clauses := array_append(v_where_clauses, format('email LIKE %L', p_email_pattern));
    END IF;

    -- WHERE句を結合
    IF array_length(v_where_clauses, 1) > 0 THEN
        v_sql := v_sql || ' WHERE ' || array_to_string(v_where_clauses, ' AND ');
    END IF;

    RAISE NOTICE 'Executing SQL: %', v_sql;

    RETURN QUERY EXECUTE v_sql;
END;
$$ LANGUAGE plpgsql;

-- 使用例
SELECT * FROM search_users(p_name := 'Alice');
SELECT * FROM search_users(p_department_id := 2, p_email_pattern := '%@example.com');
```

---

## 2. トリガー

### 2.1 トリガーとは

**トリガー**は、テーブルに対する操作（INSERT、UPDATE、DELETE）が発生したときに、自動的に実行される関数です。

```
┌─────────────────────────────────────────────────────────┐
│                   トリガーの実行タイミング                 │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  BEFORE                  AFTER                          │
│  ┌───────┐              ┌───────┐                      │
│  │       │              │       │                      │
│  │ 検証  │──────────────│ ログ  │                      │
│  │ 変更  │  実際の操作  │ 通知  │                      │
│  │       │              │       │                      │
│  └───────┘              └───────┘                      │
│                                                         │
│  - データ検証            - 監査ログ記録                  │
│  - デフォルト値設定      - 関連データ更新                │
│  - データ変更            - 通知送信                      │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

**トリガーの種類**:

| タイミング | 説明 | 用途 |
|-----------|------|------|
| **BEFORE** | データ変更前に実行 | バリデーション、値の自動設定 |
| **AFTER** | データ変更後に実行 | ログ記録、関連テーブル更新 |
| **INSTEAD OF** | ビューに対する操作の代替 | ビューの更新可能化 |

**実行単位**:

| 種類 | 説明 |
|------|------|
| **FOR EACH ROW** | 影響を受ける行ごとに実行 |
| **FOR EACH STATEMENT** | SQL文ごとに1回実行 |

### 2.2 トリガー関数の作成

トリガー関数は`RETURNS TRIGGER`を指定し、特別な変数`NEW`と`OLD`を使用します。

```sql
CREATE OR REPLACE FUNCTION trigger_function_name()
RETURNS TRIGGER AS $$
BEGIN
    -- NEW: 新しい行データ (INSERT, UPDATE)
    -- OLD: 古い行データ (UPDATE, DELETE)

    -- BEFORE TRIGGERでNEWを変更可能
    -- AFTER TRIGGERでNEWを変更しても反映されない

    RETURN NEW;  -- または RETURN OLD、RETURN NULL
END;
$$ LANGUAGE plpgsql;

-- トリガーの作成
CREATE TRIGGER trigger_name
    BEFORE INSERT OR UPDATE OR DELETE ON table_name
    FOR EACH ROW
    EXECUTE FUNCTION trigger_function_name();
```

**NEW/OLDの利用可能性**:

| 操作 | NEW | OLD |
|------|-----|-----|
| INSERT | ✅ | ❌ |
| UPDATE | ✅ | ✅ |
| DELETE | ❌ | ✅ |

### 2.3 CREATE TRIGGERの文法詳細

#### 基本構文

```sql
CREATE TRIGGER trigger_name
    { BEFORE | AFTER | INSTEAD OF } { event [ OR ... ] }
    ON table_name
    [ FOR [ EACH ] { ROW | STATEMENT } ]
    [ WHEN ( condition ) ]
    EXECUTE FUNCTION function_name ( arguments )
```

#### 各要素の説明

**1. タイミング (Timing)**

```sql
BEFORE   -- データ変更の前に実行
AFTER    -- データ変更の後に実行
INSTEAD OF  -- ビューに対する操作を置き換え（ビュー専用）
```

**2. イベント (Event)**

```sql
INSERT           -- INSERT時のみ
UPDATE           -- UPDATE時のみ
DELETE           -- DELETE時のみ
INSERT OR UPDATE -- INSERT または UPDATE時
UPDATE OR DELETE -- UPDATE または DELETE時
INSERT OR UPDATE OR DELETE  -- すべての変更時
```

**特定カラムの更新時のみ（UPDATE専用）**:

```sql
CREATE TRIGGER trigger_name
    BEFORE UPDATE OF email, name ON users  -- emailまたはnameが更新された時のみ
    FOR EACH ROW
    EXECUTE FUNCTION trigger_function();
```

**3. 実行単位 (Level)**

```sql
FOR EACH ROW        -- 影響を受ける行ごとに実行（デフォルト）
FOR EACH STATEMENT  -- SQL文ごとに1回実行
```

**具体例で理解する**:

```sql
-- 以下のUPDATE文を実行した場合
UPDATE users SET status = 'active' WHERE department_id = 1;
-- 結果: 100行が更新された

┌─────────────────────────────────────────────────────────────┐
│         FOR EACH ROW vs FOR EACH STATEMENT                  │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  【FOR EACH ROW】                                           │
│  影響を受けた "各行" に対して実行                            │
│                                                             │
│  UPDATE実行 → 100行更新                                     │
│     ├─ トリガー実行 (1行目) → NEW/OLD使用可能              │
│     ├─ トリガー実行 (2行目) → NEW/OLD使用可能              │
│     ├─ ...                                                  │
│     └─ トリガー実行 (100行目) → NEW/OLD使用可能            │
│                                                             │
│  合計: 100回実行                                             │
│  用途: 各行のデータを参照・変更する必要がある処理             │
│       （監査ログ、データ検証、関連テーブル更新）              │
│                                                             │
│ ─────────────────────────────────────────────────────────── │
│                                                             │
│  【FOR EACH STATEMENT】                                     │
│  SQL文全体に対して "1回だけ" 実行                            │
│                                                             │
│  UPDATE実行 → 100行更新                                     │
│     └─ トリガー実行 (1回のみ) → NEW/OLDは使用不可          │
│                                                             │
│  合計: 1回実行                                               │
│  用途: 行の内容に関係ない処理                                │
│       （通知送信、統計更新、キャッシュクリア）                │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**コード例**:

```sql
-- FOR EACH ROW: 各行の監査ログを記録
CREATE OR REPLACE FUNCTION audit_each_row()
RETURNS TRIGGER AS $$
BEGIN
    -- 各行のデータを記録できる
    INSERT INTO audit_log (table_name, operation, user_id, changed_data)
    VALUES (TG_TABLE_NAME, TG_OP, NEW.id, row_to_json(NEW));

    RAISE NOTICE 'Audited user_id=%', NEW.id;  -- 100回表示される
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER users_audit_row
    AFTER UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION audit_each_row();

-- FOR EACH STATEMENT: 「何か更新された」という事実だけを記録
CREATE OR REPLACE FUNCTION notify_table_updated()
RETURNS TRIGGER AS $$
BEGIN
    -- NEW/OLDは使えない！
    -- 何行が更新されたかは分からない

    INSERT INTO system_notifications (message)
    VALUES (format('Table % was modified by %', TG_TABLE_NAME, TG_OP));

    RAISE NOTICE 'Users table was updated';  -- 1回だけ表示される
    RETURN NULL;  -- STATEMENTレベルでは常にNULL
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER users_notify_statement
    AFTER UPDATE ON users
    FOR EACH STATEMENT
    EXECUTE FUNCTION notify_table_updated();
```

**パフォーマンスの違い**:

```sql
-- 1,000行を更新する場合

-- FOR EACH ROW: 1,000回のトリガー実行
--   → 各行の処理が重いと遅くなる

-- FOR EACH STATEMENT: 1回のトリガー実行
--   → 行数に関係なく高速
```

**どちらを使うべきか**:

| ケース | 推奨 | 理由 |
|--------|------|------|
| 各行のデータが必要 | FOR EACH ROW | NEW/OLDでデータアクセス可能 |
| 行数が多い & 重い処理 | FOR EACH STATEMENT | パフォーマンス向上 |
| 通知・統計更新のみ | FOR EACH STATEMENT | 行の内容は不要 |
| 監査ログ（全行記録） | FOR EACH ROW | 各行の変更履歴が必要 |

**4. 条件 (WHEN句) - PostgreSQL 9.0+**

```sql
-- 特定の条件を満たす場合のみトリガー実行
CREATE TRIGGER trigger_name
    AFTER UPDATE ON users
    FOR EACH ROW
    WHEN (OLD.status IS DISTINCT FROM NEW.status)  -- statusが実際に変わった時のみ
    EXECUTE FUNCTION log_status_change();

-- 複数条件
CREATE TRIGGER trigger_name
    BEFORE INSERT OR UPDATE ON users
    FOR EACH ROW
    WHEN (NEW.email IS NOT NULL AND NEW.email != '')
    EXECUTE FUNCTION validate_email();
```

**WHEN句の制約**:
- `OLD`と`NEW`のみ参照可能（テーブルアクセス不可）
- BEFORE TRIGGERではNEWの変更前の値を参照
- サブクエリは使用不可

**5. 実行関数とパラメータ**

```sql
-- パラメータなし（最も一般的）
EXECUTE FUNCTION my_trigger_function()

-- パラメータあり（文字列のみ）
EXECUTE FUNCTION my_trigger_function('param1', 'param2')

-- トリガー関数内でのパラメータ取得
CREATE OR REPLACE FUNCTION my_trigger_function()
RETURNS TRIGGER AS $$
BEGIN
    RAISE NOTICE 'Trigger args: %, %', TG_ARGV[0], TG_ARGV[1];
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
```

#### トリガー命名規則

```sql
-- 推奨命名パターン: {table}_{event}_{timing}
CREATE TRIGGER users_audit_after
    AFTER INSERT OR UPDATE OR DELETE ON users
    FOR EACH ROW
    EXECUTE FUNCTION audit_trigger();

CREATE TRIGGER users_validate_before
    BEFORE INSERT OR UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION validate_user_data();

CREATE TRIGGER orders_email_updated
    AFTER UPDATE OF email ON users
    FOR EACH ROW
    EXECUTE FUNCTION notify_email_changed();
```

#### RETURNの意味

| トリガー種類 | RETURN値 | 動作 |
|------------|---------|------|
| **BEFORE ROW** | NEW | NEWの内容で更新が実行される |
| **BEFORE ROW** | OLD | 元の値で更新（NEWの変更を無視） |
| **BEFORE ROW** | NULL | 操作をキャンセル（行が挿入/更新/削除されない） |
| **AFTER ROW** | 任意 | 戻り値は無視される（通常はNEWを返す） |
| **STATEMENT** | NULL | 常にNULLを返す（NEW/OLDは使用不可） |

**RETURN NULLの活用例**:

```sql
-- 特定条件でINSERTをスキップ
CREATE OR REPLACE FUNCTION skip_invalid_users()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.email IS NULL OR NEW.email = '' THEN
        RAISE NOTICE 'Skipping user with invalid email';
        RETURN NULL;  -- この行は挿入されない
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER users_skip_invalid
    BEFORE INSERT ON users
    FOR EACH ROW
    EXECUTE FUNCTION skip_invalid_users();
```

### 2.4 実践例1: updated_at自動更新

最も一般的な使用例です。

```sql
-- トリガー関数: updated_atを自動更新
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- usersテーブルにトリガーを設定
CREATE TRIGGER update_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- テスト
UPDATE users SET name = 'Alice Updated' WHERE id = 1;
SELECT id, name, updated_at FROM users WHERE id = 1;
-- updated_atが自動更新される
```

### 2.5 実践例2: 監査ログの記録

データ変更履歴を自動的に記録します。

```sql
-- 監査ログテーブル
CREATE TABLE audit_log (
    id SERIAL PRIMARY KEY,
    table_name TEXT NOT NULL,
    operation TEXT NOT NULL,  -- INSERT, UPDATE, DELETE
    old_data JSONB,
    new_data JSONB,
    changed_by TEXT DEFAULT CURRENT_USER,
    changed_at TIMESTAMP DEFAULT now()
);

-- 監査ログトリガー関数
CREATE OR REPLACE FUNCTION audit_trigger()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        INSERT INTO audit_log (table_name, operation, old_data)
        VALUES (TG_TABLE_NAME, TG_OP, row_to_json(OLD));
        RETURN OLD;
    ELSIF TG_OP = 'UPDATE' THEN
        INSERT INTO audit_log (table_name, operation, old_data, new_data)
        VALUES (TG_TABLE_NAME, TG_OP, row_to_json(OLD), row_to_json(NEW));
        RETURN NEW;
    ELSIF TG_OP = 'INSERT' THEN
        INSERT INTO audit_log (table_name, operation, new_data)
        VALUES (TG_TABLE_NAME, TG_OP, row_to_json(NEW));
        RETURN NEW;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- usersテーブルに適用
CREATE TRIGGER users_audit_trigger
    AFTER INSERT OR UPDATE OR DELETE ON users
    FOR EACH ROW
    EXECUTE FUNCTION audit_trigger();

-- テスト
INSERT INTO users (name, email) VALUES ('Bob', 'bob@example.com');
UPDATE users SET email = 'bob.new@example.com' WHERE name = 'Bob';
DELETE FROM users WHERE name = 'Bob';

-- 監査ログ確認
SELECT * FROM audit_log ORDER BY changed_at DESC;
```

### 2.6 実践例3: データ検証

BEFORE TRIGGERでデータを検証し、不正なデータの挿入を防ぎます。

```sql
CREATE OR REPLACE FUNCTION validate_email_trigger()
RETURNS TRIGGER AS $$
BEGIN
    -- メールアドレスの形式チェック
    IF NEW.email IS NULL OR NEW.email = '' THEN
        RAISE EXCEPTION 'Email cannot be empty';
    END IF;

    IF NEW.email !~ '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}$' THEN
        RAISE EXCEPTION 'Invalid email format: %', NEW.email;
    END IF;

    -- メールアドレスを小文字に正規化
    NEW.email = lower(NEW.email);

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER validate_user_email
    BEFORE INSERT OR UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION validate_email_trigger();

-- テスト
INSERT INTO users (name, email) VALUES ('Test', 'INVALID_EMAIL');  -- エラー
INSERT INTO users (name, email) VALUES ('Test', 'Test@Example.COM');  -- test@example.com に正規化
```

### 2.7 トリガー特殊変数

**トリガー特殊変数**:

| 変数 | 説明 |
|------|------|
| `NEW` | 新しい行（INSERT/UPDATE） |
| `OLD` | 古い行（UPDATE/DELETE） |
| `TG_OP` | 操作種別（'INSERT', 'UPDATE', 'DELETE'） |
| `TG_TABLE_NAME` | テーブル名 |
| `TG_TABLE_SCHEMA` | スキーマ名 |
| `TG_WHEN` | 'BEFORE' or 'AFTER' |
| `TG_LEVEL` | 'ROW' or 'STATEMENT' |

### 2.8 トリガーの管理

```sql
-- トリガー一覧表示
SELECT
    trigger_name,
    event_manipulation,
    event_object_table,
    action_statement
FROM information_schema.triggers
WHERE trigger_schema = 'public'
ORDER BY event_object_table, trigger_name;

-- トリガーの削除
DROP TRIGGER IF EXISTS trigger_name ON table_name;

-- トリガーの無効化（PostgreSQL 14+）
ALTER TABLE table_name DISABLE TRIGGER trigger_name;

-- トリガーの有効化
ALTER TABLE table_name ENABLE TRIGGER trigger_name;

-- すべてのトリガーを無効化
ALTER TABLE table_name DISABLE TRIGGER ALL;
```

### 2.9 トリガーのベストプラクティス

**推奨**:
- ✅ トリガーはシンプルに保つ
- ✅ 複雑なロジックは関数に分離
- ✅ BEFORE TRIGGERでデータ検証・変更
- ✅ AFTER TRIGGERでログ・通知
- ✅ トリガー名は`{table}_{event}_{timing}`形式（例: `users_audit_after`）

**非推奨**:
- ❌ トリガー内でトリガーを連鎖させる（デバッグが困難）
- ❌ トリガー内で長時間処理（パフォーマンス低下）
- ❌ BEFORE TRIGGERでAFTERでやるべき処理（ログ記録等）
- ❌ トリガー内で例外を投げすぎる（通常の操作が失敗する）

---

## 3. トランザクション制御

### 3.1 関数内でのトランザクション動作

PL/pgSQL関数は**既存のトランザクション内で実行**されます。

```
┌────────────────────────────────────────────────────┐
│            トランザクション                         │
│  BEGIN;                                            │
│    ┌──────────────────────────────────────┐       │
│    │ PL/pgSQL関数の実行                   │       │
│    │   - 複数のSQL文                      │       │
│    │   - EXCEPTIONで部分ロールバック      │       │
│    └──────────────────────────────────────┘       │
│  COMMIT; (全体が確定)                              │
└────────────────────────────────────────────────────┘
```

**重要なポイント**:
- 関数内で`COMMIT`/`ROLLBACK`は実行できない（PostgreSQL 11未満）
- `EXCEPTION`句でキャッチされたエラーは**サブトランザクション**でロールバック
- 関数全体が失敗した場合は、呼び出し元のトランザクションもロールバック

### 3.2 サブトランザクション

```sql
CREATE OR REPLACE FUNCTION insert_with_fallback(p_email TEXT)
RETURNS INTEGER AS $$
DECLARE
    v_user_id INTEGER;
BEGIN
    -- まず通常のINSERTを試みる
    INSERT INTO users (name, email)
    VALUES ('New User', p_email)
    RETURNING id INTO v_user_id;

    RETURN v_user_id;

EXCEPTION
    WHEN unique_violation THEN
        -- 一意制約違反の場合、既存ユーザーのIDを返す
        RAISE NOTICE 'Email % already exists, returning existing user', p_email;

        SELECT id INTO v_user_id
        FROM users
        WHERE email = p_email;

        RETURN v_user_id;
END;
$$ LANGUAGE plpgsql;
```

**サブトランザクションのコスト**:
- EXCEPTION句を使用すると、自動的にサブトランザクションが作成される
- サブトランザクションはオーバーヘッドがある
- 頻繁に実行される関数では、EXCEPTIONを避けて事前チェックを行う

```sql
-- ❌ パフォーマンス低下の可能性
CREATE OR REPLACE FUNCTION slow_insert(p_email TEXT)
RETURNS INTEGER AS $$
DECLARE
    v_user_id INTEGER;
BEGIN
    INSERT INTO users (email) VALUES (p_email) RETURNING id INTO v_user_id;
    RETURN v_user_id;
EXCEPTION WHEN unique_violation THEN
    SELECT id INTO v_user_id FROM users WHERE email = p_email;
    RETURN v_user_id;
END;
$$ LANGUAGE plpgsql;

-- ✅ 改善版: 事前チェック
CREATE OR REPLACE FUNCTION fast_insert(p_email TEXT)
RETURNS INTEGER AS $$
DECLARE
    v_user_id INTEGER;
BEGIN
    -- 既存チェック
    SELECT id INTO v_user_id FROM users WHERE email = p_email;
    IF FOUND THEN
        RETURN v_user_id;
    END IF;

    -- 新規挿入
    INSERT INTO users (email) VALUES (p_email) RETURNING id INTO v_user_id;
    RETURN v_user_id;
END;
$$ LANGUAGE plpgsql;
```

### 3.3 プロシージャでのCOMMIT（PostgreSQL 11+）

プロシージャ（PROCEDURE）では、関数と異なりトランザクション制御が可能です。

```sql
CREATE OR REPLACE PROCEDURE batch_update_users()
AS $$
DECLARE
    v_user RECORD;
    v_count INTEGER := 0;
BEGIN
    FOR v_user IN SELECT * FROM users LOOP
        -- 何か更新処理
        UPDATE users SET updated_at = now() WHERE id = v_user.id;

        v_count := v_count + 1;

        -- 100件ごとにコミット（大量データ処理の場合）
        IF v_count % 100 = 0 THEN
            COMMIT;
            RAISE NOTICE 'Committed % records', v_count;
        END IF;
    END LOOP;

    -- 残りをコミット
    COMMIT;
    RAISE NOTICE 'Total % records processed', v_count;
END;
$$ LANGUAGE plpgsql;

-- 実行
CALL batch_update_users();
```

**関数 vs プロシージャ（トランザクション制御）**:

| 項目 | FUNCTION | PROCEDURE |
|------|----------|-----------|
| COMMIT/ROLLBACK | 不可 | 可能（PG11+） |
| トランザクション境界 | 呼び出し元 | 独自に制御可能 |
| 用途 | データ取得、計算 | バッチ処理、メンテナンス |

---

## 4. パフォーマンス最適化

### 4.1 VOLATILE/STABLE/IMMUTABLE属性

関数の**副作用と結果の変動性**を指定します。

```
┌──────────────────────────────────────────────────────────────┐
│                    関数の属性                                 │
├────────────┬─────────────────────────────────────────────────┤
│ VOLATILE   │ デフォルト。同じ引数でも毎回結果が変わる可能性   │
│ (揮発性)    │ 例: now(), random(), カレントシーケンス値        │
│            │ - インデックススキャンで使用不可                  │
│            │ - 毎回実行される                                  │
├────────────┼─────────────────────────────────────────────────┤
│ STABLE     │ トランザクション内で同じ引数なら同じ結果         │
│ (安定)      │ 例: current_date, テーブル参照を含む関数         │
│            │ - インデックススキャンで使用可能                  │
│            │ - 同一トランザクション内で最適化可能              │
├────────────┼─────────────────────────────────────────────────┤
│ IMMUTABLE  │ 常に同じ引数で同じ結果（副作用なし）             │
│ (不変)      │ 例: 数学関数、文字列操作                         │
│            │ - インデックス作成に使用可能                      │
│            │ - 実行計画時に事前評価可能                        │
└────────────┴─────────────────────────────────────────────────┘
```

**例**:

```sql
-- VOLATILE (デフォルト)
CREATE OR REPLACE FUNCTION get_random_user()
RETURNS users AS $$
BEGIN
    RETURN (SELECT * FROM users ORDER BY random() LIMIT 1);
END;
$$ LANGUAGE plpgsql VOLATILE;

-- STABLE
CREATE OR REPLACE FUNCTION get_user_by_email(p_email TEXT)
RETURNS users AS $$
BEGIN
    RETURN (SELECT * FROM users WHERE email = p_email);
END;
$$ LANGUAGE plpgsql STABLE;

-- IMMUTABLE
CREATE OR REPLACE FUNCTION calculate_circle_area(p_radius NUMERIC)
RETURNS NUMERIC AS $$
BEGIN
    RETURN 3.14159 * p_radius * p_radius;
END;
$$ LANGUAGE plpgsql IMMUTABLE;
```

### 4.2 SQL関数 vs PL/pgSQL関数

**SQL関数は可能な限りインライン化される**（パフォーマンス向上）。

```sql
-- SQL関数（推奨: 単純な処理の場合）
CREATE OR REPLACE FUNCTION get_tax_sql(p_amount NUMERIC)
RETURNS NUMERIC AS $$
    SELECT p_amount * 0.1;
$$ LANGUAGE sql IMMUTABLE;

-- PL/pgSQL関数（複雑な処理が必要な場合）
CREATE OR REPLACE FUNCTION get_tax_plpgsql(p_amount NUMERIC)
RETURNS NUMERIC AS $$
BEGIN
    IF p_amount < 0 THEN
        RAISE EXCEPTION 'Amount cannot be negative';
    END IF;
    RETURN p_amount * 0.1;
END;
$$ LANGUAGE plpgsql IMMUTABLE;
```

**使い分け**:
- 単純な計算・集約 → SQL関数（インライン化の恩恵）
- 制御構造・エラーハンドリングが必要 → PL/pgSQL関数

### 4.3 パフォーマンス劣化の例

```sql
-- ❌ パフォーマンス低下
CREATE OR REPLACE FUNCTION get_constant()
RETURNS INTEGER AS $$
BEGIN
    RETURN 100;
END;
$$ LANGUAGE plpgsql;  -- デフォルトでVOLATILE

-- WHERE句で使用すると、毎行ごとに関数が呼ばれる
SELECT * FROM large_table WHERE value < get_constant();

-- ✅ 対処: 適切な属性を指定
CREATE OR REPLACE FUNCTION get_constant()
RETURNS INTEGER AS $$
BEGIN
    RETURN 100;
END;
$$ LANGUAGE plpgsql IMMUTABLE;  -- 定数なのでIMMUTABLE
```

### 4.4 EXPLAIN ANALYZEでの実行計画確認

```sql
-- 関数を含むクエリの実行計画を確認
EXPLAIN ANALYZE
SELECT
    id,
    total_amount,
    calculate_tax(total_amount) AS tax
FROM orders
WHERE total_amount > 1000;
```

---

## 5. セキュリティ

### 5.1 SECURITY DEFINER vs SECURITY INVOKER

```
┌────────────────────────────────────────────────────────────┐
│              関数の実行権限                                 │
├──────────────────┬─────────────────────────────────────────┤
│ SECURITY INVOKER │ デフォルト。関数を呼び出したユーザーの   │
│                  │ 権限で実行                               │
│                  │ - 呼び出し側がテーブルへの権限が必要     │
├──────────────────┼─────────────────────────────────────────┤
│ SECURITY DEFINER │ 関数の所有者の権限で実行                 │
│                  │ - 呼び出し側は関数の実行権限のみでOK     │
│                  │ - 権限昇格のリスクあり                   │
└──────────────────┴─────────────────────────────────────────┘
```

**SECURITY DEFINERの例**:

```sql
-- 管理者のみがusersテーブルを変更できる環境で、
-- 一般ユーザーにも特定の更新を許可する

CREATE OR REPLACE FUNCTION update_user_last_login(p_user_id INTEGER)
RETURNS VOID AS $$
BEGIN
    UPDATE users
    SET last_login_at = now()
    WHERE id = p_user_id;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- この関数は所有者（管理者）の権限で実行されるため、
-- 一般ユーザーでも実行可能
```

**⚠️ SECURITY DEFINERの危険性**:

```sql
-- ❌ 危険な例: SQLインジェクションで権限昇格
CREATE OR REPLACE FUNCTION unsafe_definer(p_table_name TEXT)
RETURNS BIGINT AS $$
DECLARE
    v_count BIGINT;
BEGIN
    -- p_table_nameに任意のSQLを注入される可能性
    EXECUTE 'SELECT COUNT(*) FROM ' || p_table_name INTO v_count;
    RETURN v_count;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 攻撃例:
-- SELECT unsafe_definer('users; DROP TABLE users; --');
```

### 5.2 search_pathの固定

`SECURITY DEFINER`関数では、必ず`search_path`を固定すべきです。

```sql
CREATE OR REPLACE FUNCTION secure_function()
RETURNS INTEGER AS $$
BEGIN
    -- search_pathを固定（スキーマハイジャック対策）
    SET search_path = public, pg_temp;

    -- 処理...
    RETURN 1;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
```

**スキーマハイジャック攻撃**:

```sql
-- 攻撃者が悪意あるスキーマを作成
CREATE SCHEMA attacker;
CREATE FUNCTION attacker.now() RETURNS TIMESTAMPTZ AS $$
BEGIN
    -- 悪意ある処理
    RETURN now();
END;
$$ LANGUAGE plpgsql;

-- 脆弱な関数（search_pathが固定されていない）
CREATE OR REPLACE FUNCTION vulnerable_function()
RETURNS TIMESTAMPTZ AS $$
BEGIN
    RETURN now();  -- attackerスキーマのnow()が呼ばれる可能性
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
```

### 5.3 入力検証

```sql
CREATE OR REPLACE FUNCTION validate_and_update_email(
    p_user_id INTEGER,
    p_email TEXT
)
RETURNS VOID AS $$
BEGIN
    -- 入力検証
    IF p_user_id IS NULL OR p_user_id <= 0 THEN
        RAISE EXCEPTION 'Invalid user ID: %', p_user_id;
    END IF;

    IF p_email IS NULL OR p_email = '' THEN
        RAISE EXCEPTION 'Email cannot be empty';
    END IF;

    IF p_email !~ '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}$' THEN
        RAISE EXCEPTION 'Invalid email format: %', p_email;
    END IF;

    -- 更新処理
    UPDATE users
    SET email = lower(p_email),
        updated_at = now()
    WHERE id = p_user_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'User % not found', p_user_id;
    END IF;
END;
$$ LANGUAGE plpgsql;
```

---

## 6. 応用ベストプラクティス

### 6.1 動的SQLのセキュリティチェックリスト

- ✅ 常に`format()`関数を使用
- ✅ 識別子には`%I`、リテラルには`%L`を使用
- ✅ `%s`は絶対に使用しない
- ✅ 入力値を検証（ホワイトリスト方式）
- ✅ 動的に生成されたSQLをログ出力（デバッグ用）

### 6.2 トリガーのパフォーマンス考慮

```sql
-- ❌ 非効率: すべての更新でログを記録
CREATE TRIGGER users_audit
    AFTER UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION audit_trigger();

-- ✅ 改善: 特定カラムの変更時のみログ記録
CREATE TRIGGER users_audit
    AFTER UPDATE OF email, name ON users  -- 特定カラムのみ
    FOR EACH ROW
    WHEN (OLD.* IS DISTINCT FROM NEW.*)  -- 実際に変更があった場合のみ
    EXECUTE FUNCTION audit_trigger();
```

### 6.3 SECURITY DEFINERのベストプラクティス

```sql
CREATE OR REPLACE FUNCTION safe_definer_function(p_param TEXT)
RETURNS INTEGER AS $$
BEGIN
    -- 1. search_pathを固定
    SET search_path = public, pg_temp;

    -- 2. 入力検証
    IF p_param IS NULL OR p_param = '' THEN
        RAISE EXCEPTION 'Parameter cannot be empty';
    END IF;

    -- 3. 動的SQLは避ける（やむを得ない場合はformat()使用）

    -- 4. 最小権限の原則に従う

    RETURN 1;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER
SET search_path = public, pg_temp;  -- CREATE時にも指定可能
```

---

## 7. 参考リンク

### 公式ドキュメント
- [PostgreSQL公式ドキュメント - PL/pgSQL](https://www.postgresql.org/docs/current/plpgsql.html)
- [PostgreSQL公式ドキュメント - トリガー](https://www.postgresql.org/docs/current/triggers.html)
- [PostgreSQL公式ドキュメント - 関数の揮発性分類](https://www.postgresql.org/docs/current/xfunc-volatility.html)
- [PostgreSQL公式ドキュメント - SECURITY DEFINER](https://www.postgresql.org/docs/current/sql-createfunction.html#SQL-CREATEFUNCTION-SECURITY)

### 関連する学習コンテンツ
- [dev-07-plpgsql-basics.md](./dev-07-plpgsql-basics.md) - PL/pgSQL基本編
- [dev-04-transactions.md](./dev-04-transactions.md) - トランザクション
- [dev-05-query-optimization.md](./dev-05-query-optimization.md) - クエリ最適化
- [dba-04-security.md](./dba-04-security.md) - セキュリティ設定

---

## まとめ

PL/pgSQL応用編では、実践的な技術を学びました。

**学んだこと**:
- ✅ 動的SQLの安全な使い方（format()、SQLインジェクション対策）
- ✅ トリガーの実装（BEFORE/AFTER、NEW/OLD、監査ログ）
- ✅ トランザクション制御（サブトランザクション、プロシージャでのCOMMIT）
- ✅ パフォーマンス最適化（VOLATILE/STABLE/IMMUTABLE）
- ✅ セキュリティ（SECURITY DEFINER、search_path固定、入力検証）

**次のステップ**:
1. 実際のプロジェクトでトリガーを実装してみる
2. 既存の動的SQL関数をセキュリティレビューする
3. パフォーマンスが低い関数を最適化する
4. SECURITY DEFINERを使った権限制御を設計する
