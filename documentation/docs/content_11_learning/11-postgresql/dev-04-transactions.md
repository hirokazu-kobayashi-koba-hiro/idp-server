# PostgreSQL トランザクション

## 所要時間
約45分

## 学べること
- トランザクションの基本概念とACID特性
- 分離レベルと各レベルで発生する現象
- ロック機構と適切な使い方
- デッドロックの回避方法
- セーブポイントの活用

## 前提知識
- SQLの基本操作（SELECT、UPDATE等）
- テーブル設計の基礎

---

## 1. トランザクションとは

### 1.1 基本概念

トランザクションは、複数のデータベース操作を**一つの論理的な作業単位**としてまとめる仕組みです。

```
┌─────────────────────────────────────────────────────────┐
│                    トランザクション                        │
│  ┌─────────┐   ┌─────────┐   ┌─────────┐              │
│  │ 操作 1   │ → │ 操作 2   │ → │ 操作 3   │              │
│  │ UPDATE  │   │ INSERT  │   │ DELETE  │              │
│  └─────────┘   └─────────┘   └─────────┘              │
│                                                         │
│  すべて成功 → COMMIT（確定）                              │
│  一つでも失敗 → ROLLBACK（取消）                          │
└─────────────────────────────────────────────────────────┘
```

### 1.2 基本構文

```sql
-- トランザクション開始
BEGIN;

-- 複数の操作
UPDATE accounts SET balance = balance - 1000 WHERE id = 1;
UPDATE accounts SET balance = balance + 1000 WHERE id = 2;

-- 成功したら確定
COMMIT;

-- 失敗したら取消
-- ROLLBACK;
```

### 1.3 暗黙的トランザクション

PostgreSQLでは、明示的に`BEGIN`を書かない場合、各SQL文が**自動コミット**されます。

```sql
-- これらは各文が個別にコミットされる
UPDATE accounts SET balance = balance - 1000 WHERE id = 1;  -- 即座にコミット
UPDATE accounts SET balance = balance + 1000 WHERE id = 2;  -- 即座にコミット

-- 1つ目が成功して2つ目が失敗すると、不整合が発生する！
```

**重要**: 複数の関連する操作は必ず明示的なトランザクションで囲むこと。

---

## 2. ACID特性

トランザクションが保証する4つの特性です。

### 2.1 概要

```
┌─────────────────────────────────────────────────────────────────────┐
│                          ACID特性                                    │
├──────────────┬──────────────────────────────────────────────────────┤
│ Atomicity    │ 原子性：全部成功 or 全部失敗、中途半端な状態はない      │
│ (原子性)      │                                                      │
├──────────────┼──────────────────────────────────────────────────────┤
│ Consistency  │ 一貫性：制約違反があればトランザクションは失敗          │
│ (一貫性)      │                                                      │
├──────────────┼──────────────────────────────────────────────────────┤
│ Isolation    │ 分離性：同時実行されるトランザクションは互いに干渉しない │
│ (分離性)      │                                                      │
├──────────────┼──────────────────────────────────────────────────────┤
│ Durability   │ 永続性：コミットされたデータは永続的に保存される        │
│ (永続性)      │                                                      │
└──────────────┴──────────────────────────────────────────────────────┘
```

### 2.2 原子性（Atomicity）の例

```sql
-- 銀行の送金処理
BEGIN;
  -- 送金元から引き落とし
  UPDATE accounts SET balance = balance - 10000 WHERE id = 1;

  -- 送金先に入金（ここでエラーが発生したと仮定）
  UPDATE accounts SET balance = balance + 10000 WHERE id = 999;  -- 存在しないID

  -- エラーが発生すると、最初のUPDATEも取り消される
ROLLBACK;  -- または自動的にロールバック

-- 結果：どちらの口座も変更されない（原子性が保証される）
```

### 2.3 一貫性（Consistency）の例

```sql
-- 制約による一貫性の保証
CREATE TABLE accounts (
    id SERIAL PRIMARY KEY,
    balance NUMERIC(15,2) CHECK (balance >= 0)  -- 残高は0以上
);

BEGIN;
  UPDATE accounts SET balance = balance - 10000 WHERE id = 1;
  -- balance が負になる場合、CHECK制約違反でトランザクション全体が失敗
COMMIT;
```

### 2.4 分離性（Isolation）の詳細

分離性は次のセクションで詳しく説明します。

### 2.5 永続性（Durability）の保証

```
┌─────────────────────────────────────────────────────────┐
│                  永続性の実現方法                         │
│                                                         │
│  COMMIT実行                                              │
│      ↓                                                  │
│  WAL（Write-Ahead Log）に書き込み                        │
│      ↓                                                  │
│  ディスクに同期（fsync）                                  │
│      ↓                                                  │
│  クライアントに成功を返答                                 │
│                                                         │
│  ※ サーバークラッシュ時もWALから復旧可能                  │
└─────────────────────────────────────────────────────────┘
```

---

## 3. トランザクション分離レベル

### 3.1 分離レベルの概要

PostgreSQLは4つの分離レベルをサポートしています。

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                        トランザクション分離レベル                                  │
├──────────────────────┬──────────────────────────────────────────────────────────┤
│ 分離レベル            │ 説明                                                     │
├──────────────────────┼──────────────────────────────────────────────────────────┤
│ READ UNCOMMITTED     │ 他のコミットされていない変更が見える                       │
│                      │ ※ PostgreSQLでは READ COMMITTED と同等                   │
├──────────────────────┼──────────────────────────────────────────────────────────┤
│ READ COMMITTED       │ 他のコミットされた変更のみが見える（デフォルト）            │
│                      │ 各SQL文の実行時点でのスナップショット                      │
├──────────────────────┼──────────────────────────────────────────────────────────┤
│ REPEATABLE READ      │ トランザクション開始時点のスナップショットを使用            │
│                      │ 同じクエリは常に同じ結果                                   │
├──────────────────────┼──────────────────────────────────────────────────────────┤
│ SERIALIZABLE         │ 完全な直列化可能性を保証                                   │
│                      │ 最も厳格だが、競合時にエラーになる可能性                   │
└──────────────────────┴──────────────────────────────────────────────────────────┘
```

### 3.2 発生しうる現象

各分離レベルで発生しうる現象を整理します。

```
┌────────────────────────────────────────────────────────────────────────────┐
│                    分離レベルと発生する現象                                  │
├──────────────────┬──────────────┬──────────────┬──────────────┬───────────┤
│ 分離レベル        │ Dirty Read   │ Non-Repeatable│ Phantom      │ 直列化    │
│                  │ (ダーティ     │ Read(反復    │ Read(ファン  │ 異常      │
│                  │  リード)      │ 不能読み取り) │ トムリード)  │           │
├──────────────────┼──────────────┼──────────────┼──────────────┼───────────┤
│ READ UNCOMMITTED │ 発生しない※  │ 発生する      │ 発生する      │ 発生する  │
│ READ COMMITTED   │ 発生しない    │ 発生する      │ 発生する      │ 発生する  │
│ REPEATABLE READ  │ 発生しない    │ 発生しない    │ 発生しない※  │ 発生する  │
│ SERIALIZABLE     │ 発生しない    │ 発生しない    │ 発生しない    │ 発生しない│
└──────────────────┴──────────────┴──────────────┴──────────────┴───────────┘
※ PostgreSQL独自の実装により、標準SQLより厳格
```

### 3.3 各現象の具体例

#### Dirty Read（ダーティリード）

未コミットのデータを読んでしまう現象（PostgreSQLでは発生しない）。

```
時間 →
┌──────────────────────────────────────────────────────────────┐
│ トランザクションA              │ トランザクションB              │
├───────────────────────────────┼──────────────────────────────┤
│ BEGIN;                        │                              │
│ UPDATE users                  │                              │
│   SET name = '変更後'         │                              │
│   WHERE id = 1;               │                              │
│                               │ BEGIN;                       │
│                               │ SELECT name FROM users       │
│                               │   WHERE id = 1;              │
│                               │ -- '変更後' が見える？        │
│                               │ -- PostgreSQLでは見えない    │
│ ROLLBACK;                     │                              │
│                               │ -- もし見えていたら不整合    │
└───────────────────────────────┴──────────────────────────────┘
```

#### Non-Repeatable Read（反復不能読み取り）

同じクエリを2回実行すると異なる結果が返る現象。

```sql
-- トランザクションA（READ COMMITTED）
BEGIN;
SELECT balance FROM accounts WHERE id = 1;  -- 結果: 10000

-- この間にトランザクションBが更新・コミット
-- UPDATE accounts SET balance = 5000 WHERE id = 1; COMMIT;

SELECT balance FROM accounts WHERE id = 1;  -- 結果: 5000（変わった！）
COMMIT;
```

```
時間 →
┌──────────────────────────────────────────────────────────────┐
│ トランザクションA              │ トランザクションB              │
├───────────────────────────────┼──────────────────────────────┤
│ BEGIN;                        │                              │
│ SELECT balance...             │                              │
│ → 10000                       │                              │
│                               │ BEGIN;                       │
│                               │ UPDATE accounts              │
│                               │   SET balance = 5000...      │
│                               │ COMMIT;                      │
│ SELECT balance...             │                              │
│ → 5000 ← 値が変わった！        │                              │
│ COMMIT;                       │                              │
└───────────────────────────────┴──────────────────────────────┘
```

#### Phantom Read（ファントムリード）

同じ条件で検索すると、行数が変わる現象。

```sql
-- トランザクションA（READ COMMITTED）
BEGIN;
SELECT COUNT(*) FROM users WHERE status = 'active';  -- 結果: 100

-- この間にトランザクションBが新規行を追加・コミット

SELECT COUNT(*) FROM users WHERE status = 'active';  -- 結果: 101（増えた！）
COMMIT;
```

### 3.4 分離レベルの設定

```sql
-- トランザクション単位で設定
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ;
-- または
BEGIN;
SET TRANSACTION ISOLATION LEVEL REPEATABLE READ;

-- セッション単位で設定
SET SESSION CHARACTERISTICS AS TRANSACTION ISOLATION LEVEL REPEATABLE READ;

-- 現在の分離レベルを確認
SHOW transaction_isolation;
```

### 3.5 分離レベルの選択指針

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                         分離レベル選択ガイド                                      │
├──────────────────────┬──────────────────────────────────────────────────────────┤
│ ユースケース          │ 推奨分離レベル                                           │
├──────────────────────┼──────────────────────────────────────────────────────────┤
│ 一般的なWebアプリ     │ READ COMMITTED（デフォルト）                              │
│                      │ ほとんどの場合はこれで十分                                │
├──────────────────────┼──────────────────────────────────────────────────────────┤
│ レポート生成          │ REPEATABLE READ                                          │
│ 集計処理             │ トランザクション中は一貫したデータを見る必要がある        │
├──────────────────────┼──────────────────────────────────────────────────────────┤
│ 在庫管理             │ SERIALIZABLE                                             │
│ 予約システム          │ 競合を完全に防ぎたい場合                                  │
│ 金融取引             │ （ただしリトライロジックが必要）                          │
└──────────────────────┴──────────────────────────────────────────────────────────┘
```

---

## 4. ロック機構

### 4.1 ロックの種類

PostgreSQLには複数のロックレベルがあります。

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           テーブルロックモード                                    │
├────────────────────────────┬────────────────────────────────────────────────────┤
│ ロックモード               │ 用途                                                │
├────────────────────────────┼────────────────────────────────────────────────────┤
│ ACCESS SHARE               │ SELECT で取得                                       │
│                            │ ACCESS EXCLUSIVE とのみ競合                         │
├────────────────────────────┼────────────────────────────────────────────────────┤
│ ROW SHARE                  │ SELECT FOR UPDATE/SHARE で取得                      │
│                            │                                                     │
├────────────────────────────┼────────────────────────────────────────────────────┤
│ ROW EXCLUSIVE              │ UPDATE, DELETE, INSERT で取得                       │
│                            │                                                     │
├────────────────────────────┼────────────────────────────────────────────────────┤
│ SHARE                      │ CREATE INDEX（非CONCURRENT）で取得                  │
│                            │                                                     │
├────────────────────────────┼────────────────────────────────────────────────────┤
│ ACCESS EXCLUSIVE           │ ALTER TABLE, DROP, TRUNCATE, VACUUM FULL で取得     │
│                            │ 他のすべてのロックと競合                             │
└────────────────────────────┴────────────────────────────────────────────────────┘
```

### 4.2 行レベルロック

行レベルロックは、特定の行のみをロックします。

```sql
-- 排他ロック（更新用）
SELECT * FROM accounts WHERE id = 1 FOR UPDATE;
-- 他のトランザクションはこの行を更新できない

-- 共有ロック（読み取り用）
SELECT * FROM accounts WHERE id = 1 FOR SHARE;
-- 他のトランザクションもこの行を読み取り可能だが、更新は待機

-- ロック待機をスキップ
SELECT * FROM accounts WHERE id = 1 FOR UPDATE NOWAIT;
-- ロックできない場合は即座にエラー

-- ロックできた行のみ取得
SELECT * FROM accounts WHERE id IN (1, 2, 3) FOR UPDATE SKIP LOCKED;
-- ロック中の行はスキップされる
```

### 4.3 行ロックの競合マトリクス

```
┌──────────────────────────────────────────────────────────────────────────┐
│                     行レベルロックの競合                                   │
├──────────────────┬────────────────────┬───────────────────┬─────────────┤
│                  │ FOR KEY SHARE      │ FOR SHARE         │ FOR UPDATE  │
├──────────────────┼────────────────────┼───────────────────┼─────────────┤
│ FOR KEY SHARE    │ ○ 共存可能          │ ○ 共存可能         │ ○ 共存可能   │
│ FOR SHARE        │ ○ 共存可能          │ ○ 共存可能         │ × 競合      │
│ FOR UPDATE       │ ○ 共存可能          │ × 競合            │ × 競合      │
│ FOR NO KEY UPDATE│ ○ 共存可能          │ × 競合            │ × 競合      │
└──────────────────┴────────────────────┴───────────────────┴─────────────┘
```

### 4.4 ロック待機の可視化

```sql
-- 現在のロック状況を確認
SELECT
    l.pid,
    l.locktype,
    l.mode,
    l.granted,
    a.query
FROM pg_locks l
JOIN pg_stat_activity a ON l.pid = a.pid
WHERE NOT l.granted;  -- 待機中のロック

-- ブロックしているプロセスを特定
SELECT
    blocked.pid AS blocked_pid,
    blocked.query AS blocked_query,
    blocking.pid AS blocking_pid,
    blocking.query AS blocking_query
FROM pg_stat_activity blocked
JOIN pg_locks blocked_locks ON blocked.pid = blocked_locks.pid
JOIN pg_locks blocking_locks
    ON blocked_locks.locktype = blocking_locks.locktype
    AND blocked_locks.relation = blocking_locks.relation
    AND blocked_locks.pid != blocking_locks.pid
JOIN pg_stat_activity blocking ON blocking_locks.pid = blocking.pid
WHERE NOT blocked_locks.granted
  AND blocking_locks.granted;
```

---

## 5. デッドロック

### 5.1 デッドロックとは

2つ以上のトランザクションが互いのリソースを待ち合う状態です。

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           デッドロックの発生                                      │
│                                                                                 │
│    トランザクションA                     トランザクションB                        │
│    ┌─────────────────┐                  ┌─────────────────┐                    │
│    │ 行1をロック ✓    │                  │ 行2をロック ✓    │                    │
│    │                 │                  │                 │                    │
│    │ 行2をロック待ち ←─────── 待機 ─────→ 行1をロック待ち  │                    │
│    │       ↑         │                  │       ↑         │                    │
│    └───────│─────────┘                  └───────│─────────┘                    │
│            └──────────────── デッドロック ────────┘                             │
│                                                                                 │
│    → PostgreSQLが検出し、一方をROLLBACK                                         │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 5.2 デッドロックの例

```sql
-- トランザクションA
BEGIN;
UPDATE accounts SET balance = balance - 100 WHERE id = 1;  -- 行1をロック
-- ここでトランザクションBを待機

-- トランザクションB（同時に実行）
BEGIN;
UPDATE accounts SET balance = balance - 100 WHERE id = 2;  -- 行2をロック
UPDATE accounts SET balance = balance + 100 WHERE id = 1;  -- 行1を待機

-- トランザクションA（続き）
UPDATE accounts SET balance = balance + 100 WHERE id = 2;  -- 行2を待機 → デッドロック！
```

### 5.3 デッドロックの回避策

#### 策1: 一貫した順序でロック

```sql
-- 常にIDの昇順でロック
BEGIN;
SELECT * FROM accounts WHERE id IN (1, 2) ORDER BY id FOR UPDATE;
-- これにより、すべてのトランザクションが同じ順序でロックを取得
UPDATE accounts SET balance = balance - 100 WHERE id = 1;
UPDATE accounts SET balance = balance + 100 WHERE id = 2;
COMMIT;
```

#### 策2: ロックタイムアウトの設定

```sql
-- セッション単位でタイムアウト設定
SET lock_timeout = '5s';

BEGIN;
UPDATE accounts SET balance = balance - 100 WHERE id = 1;
-- 5秒以上ロックを待つとエラー
COMMIT;
```

#### 策3: NOWAIT オプション

```sql
BEGIN;
SELECT * FROM accounts WHERE id = 1 FOR UPDATE NOWAIT;
-- ロックできない場合は即座にエラー（待機しない）
COMMIT;
```

#### 策4: アプリケーション側でのリトライ

```java
// Javaでのリトライ例
int maxRetries = 3;
for (int i = 0; i < maxRetries; i++) {
    try {
        performTransaction();
        break;  // 成功したらループを抜ける
    } catch (SQLException e) {
        if (isDeadlockError(e) && i < maxRetries - 1) {
            // デッドロックエラーの場合、少し待ってリトライ
            Thread.sleep(100 * (i + 1));
        } else {
            throw e;
        }
    }
}
```

### 5.4 デッドロック検出の設定

```sql
-- デッドロック検出の間隔（デフォルト1秒）
SHOW deadlock_timeout;

-- 本番環境での推奨設定
SET deadlock_timeout = '1s';  -- デフォルトで十分
```

---

## 6. セーブポイント

### 6.1 セーブポイントとは

トランザクション内に中間地点を設定し、部分的なロールバックを可能にする機能です。

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           セーブポイントの概念                                    │
│                                                                                 │
│  BEGIN ─→ 操作1 ─→ SAVEPOINT sp1 ─→ 操作2 ─→ 操作3                             │
│                        │                         │                              │
│                        │                         ↓                              │
│                        │               ROLLBACK TO sp1                          │
│                        │                         │                              │
│                        └─────────────────────────┘                              │
│                                    ↓                                            │
│                              操作2と操作3のみ取消                                 │
│                              操作1は保持される                                   │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 6.2 基本的な使い方

```sql
BEGIN;

-- ユーザー作成
INSERT INTO users (name, email) VALUES ('田中', 'tanaka@example.com');

-- セーブポイント作成
SAVEPOINT user_created;

-- 関連データ作成（失敗する可能性あり）
INSERT INTO profiles (user_id, bio) VALUES (currval('users_id_seq'), '自己紹介...');

-- エラーが発生した場合
ROLLBACK TO user_created;

-- 別の処理を試行
INSERT INTO profiles (user_id, bio) VALUES (currval('users_id_seq'), 'デフォルトの自己紹介');

COMMIT;
-- ユーザーは作成され、プロファイルも（リトライ後に）作成される
```

### 6.3 実践的な使用例

#### バッチ処理での部分コミット

```sql
BEGIN;

SAVEPOINT batch_start;

-- 1000件のデータを処理
FOR i IN 1..1000 LOOP
    BEGIN
        -- 個別の処理
        INSERT INTO processed_items (item_id, result)
        VALUES (i, process_item(i));

        -- 100件ごとにセーブポイント更新
        IF i % 100 = 0 THEN
            RELEASE SAVEPOINT batch_start;
            SAVEPOINT batch_start;
        END IF;
    EXCEPTION WHEN OTHERS THEN
        -- エラーが発生しても続行
        ROLLBACK TO batch_start;
        INSERT INTO error_log (item_id, error) VALUES (i, SQLERRM);
    END;
END LOOP;

COMMIT;
```

#### 複数操作の条件付き実行

```sql
BEGIN;

-- メイン処理
UPDATE orders SET status = 'shipped' WHERE id = 123;
SAVEPOINT order_updated;

-- オプション処理1（失敗しても続行）
BEGIN
    INSERT INTO notifications (user_id, message)
    VALUES (456, '注文が発送されました');
EXCEPTION WHEN OTHERS THEN
    ROLLBACK TO order_updated;
END;

SAVEPOINT notification_done;

-- オプション処理2（失敗しても続行）
BEGIN
    UPDATE inventory SET quantity = quantity - 1 WHERE product_id = 789;
EXCEPTION WHEN OTHERS THEN
    ROLLBACK TO notification_done;
END;

COMMIT;
-- メインの注文更新は必ず保持される
```

### 6.4 セーブポイントの制限と注意点

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                     セーブポイントの注意点                                        │
├────────────────────────────────────────────────────────────────────────────────┤
│ 1. ROLLBACK TO sp1 の後、sp1 は再利用可能                                        │
│    （再度 ROLLBACK TO sp1 できる）                                               │
│                                                                                 │
│ 2. RELEASE sp1 でセーブポイントを解放                                            │
│    （メモリを節約、その後 ROLLBACK TO sp1 は不可）                                │
│                                                                                 │
│ 3. セーブポイントはネストできる                                                  │
│    SAVEPOINT sp1 → SAVEPOINT sp2 → ROLLBACK TO sp1                             │
│    sp2 は sp1 へのロールバックで自動的に破棄される                               │
│                                                                                 │
│ 4. トランザクション全体のロールバックではすべてのセーブポイントが無効             │
└────────────────────────────────────────────────────────────────────────────────┘
```

---

## 7. MVCCとスナップショット

### 7.1 MVCCとは

MVCC（Multi-Version Concurrency Control）は、PostgreSQLの同時実行制御の基盤です。

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              MVCCの仕組み                                        │
│                                                                                 │
│  更新前                                                                          │
│  ┌───────────────────────────────────────────────────────────┐                 │
│  │ 行データ (version 1)                                       │                 │
│  │ xmin=100, xmax=∞                                          │                 │
│  └───────────────────────────────────────────────────────────┘                 │
│                                                                                 │
│  UPDATE実行（トランザクションID=200）                                            │
│  ┌───────────────────────────────────────────────────────────┐                 │
│  │ 行データ (version 1) - 旧バージョン                         │                 │
│  │ xmin=100, xmax=200  ← 削除マーク                           │                 │
│  └───────────────────────────────────────────────────────────┘                 │
│  ┌───────────────────────────────────────────────────────────┐                 │
│  │ 行データ (version 2) - 新バージョン                         │                 │
│  │ xmin=200, xmax=∞                                          │                 │
│  └───────────────────────────────────────────────────────────┘                 │
│                                                                                 │
│  トランザクションID 150 のSELECT → version 1 を参照                              │
│  トランザクションID 250 のSELECT → version 2 を参照                              │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 7.2 可視性ルール

```sql
-- 行が可視かどうかの判断
/*
  1. xmin が現在のスナップショットより前でコミット済み → 可視
  2. xmax が設定されていない、または
     xmax が現在のスナップショットより後、または
     xmax がコミットされていない → 可視
  3. それ以外 → 不可視
*/

-- トランザクションの状態確認
SELECT txid_current();  -- 現在のトランザクションID
SELECT txid_current_snapshot();  -- 現在のスナップショット
```

### 7.3 読み取りと書き込みの分離

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                        MVCCの利点                                                │
│                                                                                 │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │ 読み取り（SELECT）は書き込み（UPDATE/DELETE）をブロックしない              │   │
│  │ 書き込み（UPDATE/DELETE）は読み取り（SELECT）をブロックしない              │   │
│  └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                 │
│  従来のロック方式:                                                               │
│  Writer ──────────[ロック]──────────                                            │
│  Reader         ↓ブロック↓                                                     │
│                                                                                 │
│  MVCC方式:                                                                      │
│  Writer ──────────[更新]──────────                                              │
│  Reader ──────────[読取]──────────  ← 旧バージョンを読める                      │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 8. 実践的なパターン

### 8.1 SELECT FOR UPDATE パターン

```sql
-- 在庫の排他的確保
BEGIN;

-- 在庫行をロック
SELECT quantity FROM inventory
WHERE product_id = 123
FOR UPDATE;

-- 在庫チェック
-- (アプリケーションで quantity >= required を確認)

-- 在庫を減らす
UPDATE inventory
SET quantity = quantity - 1
WHERE product_id = 123;

COMMIT;
```

### 8.2 Upsert（INSERT ON CONFLICT）

```sql
-- 存在しなければINSERT、存在すればUPDATE
INSERT INTO user_settings (user_id, setting_key, setting_value)
VALUES (1, 'theme', 'dark')
ON CONFLICT (user_id, setting_key)
DO UPDATE SET
    setting_value = EXCLUDED.setting_value,
    updated_at = CURRENT_TIMESTAMP;
```

### 8.3 Advisory Lock（アドバイザリーロック）

アプリケーションレベルで任意のロックを取得できます。

```sql
-- セッションレベルのロック
SELECT pg_advisory_lock(12345);  -- ロック取得（IDは任意の整数）
-- 排他的な処理
SELECT pg_advisory_unlock(12345);  -- ロック解放

-- トランザクションレベルのロック（COMMIT/ROLLBACKで自動解放）
SELECT pg_advisory_xact_lock(12345);

-- ノンブロッキング（ロックできなければfalseを返す）
SELECT pg_try_advisory_lock(12345);
```

実践例：分散システムでの排他制御

```sql
-- ユーザー単位で処理を排他制御
BEGIN;

-- ユーザーIDをキーとしてロック
SELECT pg_advisory_xact_lock(hashtext('user:' || 'user123'));

-- このユーザーに対する処理を実行
UPDATE user_balance SET amount = amount - 100 WHERE user_id = 'user123';
INSERT INTO transactions (user_id, amount, type) VALUES ('user123', -100, 'withdrawal');

COMMIT;  -- 自動的にアドバイザリーロックも解放
```

### 8.4 楽観的ロック

```sql
-- バージョン列を使用
CREATE TABLE documents (
    id SERIAL PRIMARY KEY,
    content TEXT,
    version INTEGER DEFAULT 1
);

-- 更新時にバージョンチェック
UPDATE documents
SET content = '新しい内容',
    version = version + 1
WHERE id = 1
  AND version = 5;  -- 読み取り時のバージョン

-- 影響行数が0なら、他のトランザクションが先に更新している
-- → アプリケーションでリトライまたはエラー処理
```

---

## 9. トラブルシューティング

### 9.1 長時間トランザクションの検出

```sql
-- 長時間実行中のトランザクション
SELECT
    pid,
    now() - xact_start AS duration,
    state,
    query
FROM pg_stat_activity
WHERE xact_start IS NOT NULL
  AND now() - xact_start > interval '5 minutes'
ORDER BY duration DESC;

-- アイドル状態のトランザクション（特に問題）
SELECT
    pid,
    now() - xact_start AS duration,
    query
FROM pg_stat_activity
WHERE state = 'idle in transaction'
  AND now() - xact_start > interval '1 minute';
```

### 9.2 アイドルトランザクションの自動終了

```sql
-- PostgreSQL 14以降
SET idle_in_transaction_session_timeout = '5min';

-- または postgresql.conf で設定
-- idle_in_transaction_session_timeout = 300000  -- ミリ秒
```

### 9.3 ロック競合の分析

```sql
-- 待機中のクエリとブロックしているクエリ
SELECT
    blocked.pid AS blocked_pid,
    blocked.query AS blocked_query,
    blocking.pid AS blocking_pid,
    blocking.query AS blocking_query,
    now() - blocked.query_start AS waiting_duration
FROM pg_stat_activity blocked
JOIN pg_catalog.pg_locks blocked_locks
    ON blocked.pid = blocked_locks.pid AND NOT blocked_locks.granted
JOIN pg_catalog.pg_locks blocking_locks
    ON blocking_locks.locktype = blocked_locks.locktype
    AND blocking_locks.database IS NOT DISTINCT FROM blocked_locks.database
    AND blocking_locks.relation IS NOT DISTINCT FROM blocked_locks.relation
    AND blocking_locks.page IS NOT DISTINCT FROM blocked_locks.page
    AND blocking_locks.tuple IS NOT DISTINCT FROM blocked_locks.tuple
    AND blocking_locks.virtualxid IS NOT DISTINCT FROM blocked_locks.virtualxid
    AND blocking_locks.transactionid IS NOT DISTINCT FROM blocked_locks.transactionid
    AND blocking_locks.classid IS NOT DISTINCT FROM blocked_locks.classid
    AND blocking_locks.objid IS NOT DISTINCT FROM blocked_locks.objid
    AND blocking_locks.objsubid IS NOT DISTINCT FROM blocked_locks.objsubid
    AND blocking_locks.pid != blocked_locks.pid
    AND blocking_locks.granted
JOIN pg_stat_activity blocking ON blocking_locks.pid = blocking.pid
ORDER BY waiting_duration DESC;
```

### 9.4 強制的なトランザクション終了

```sql
-- 優しく終了（SIGTERMシグナル）
SELECT pg_terminate_backend(pid);

-- クエリのみキャンセル（SIGINTシグナル）
SELECT pg_cancel_backend(pid);
```

---

## 10. ベストプラクティス

### 10.1 トランザクション設計

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                    トランザクション設計のベストプラクティス                        │
├─────────────────────────────────────────────────────────────────────────────────┤
│ ✓ トランザクションは短く保つ                                                    │
│   - 長時間トランザクションはロック競合とbloatの原因                              │
│   - ユーザー入力待ちをトランザクション内で行わない                               │
│                                                                                 │
│ ✓ 適切な分離レベルを選択する                                                    │
│   - デフォルト（READ COMMITTED）で十分な場合がほとんど                          │
│   - 必要な場合のみ高い分離レベルを使用                                          │
│                                                                                 │
│ ✓ デッドロックを防ぐ                                                            │
│   - 常に同じ順序でリソースをロック                                              │
│   - タイムアウトを設定                                                          │
│                                                                                 │
│ ✓ リトライロジックを実装する                                                    │
│   - SERIALIZABLE や競合時のエラーに備える                                       │
│   - 指数バックオフを検討                                                        │
│                                                                                 │
│ ✓ 明示的なトランザクション境界を使用                                            │
│   - 自動コミットに依存しない                                                    │
│   - BEGIN/COMMIT を明示的に記述                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 10.2 推奨設定

```sql
-- 接続プールを使用する場合の推奨設定

-- ステートメントタイムアウト（長すぎるクエリを防止）
SET statement_timeout = '30s';

-- ロックタイムアウト（ロック待ちが長すぎる場合）
SET lock_timeout = '10s';

-- アイドルトランザクションタイムアウト
SET idle_in_transaction_session_timeout = '5min';
```

---

## まとめ

1. **ACID特性**を理解し、トランザクションで一貫性を保証する
2. **分離レベル**はユースケースに応じて選択（通常はREAD COMMITTED）
3. **ロック**の仕組みを理解し、デッドロックを防ぐ
4. **MVCC**により読み取りと書き込みが互いをブロックしない
5. **セーブポイント**で部分的なロールバックが可能
6. 長時間トランザクションを避け、**適切なタイムアウト**を設定する

## 次のステップ

- [dev-05-query-optimization.md](dev-05-query-optimization.md): クエリ最適化
- [dba-03-replication-ha.md](dba-03-replication-ha.md): レプリケーションと高可用性
