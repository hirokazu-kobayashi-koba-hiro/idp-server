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

PostgreSQLには**テーブルレベルロック**と**行レベルロック**の2階層があります。通常のSQL文（SELECT, UPDATE, DELETE等）を実行すると、PostgreSQLが自動的に適切なロックを取得します。

### 4.1 テーブルレベルロック

テーブルレベルロックはテーブル全体に対するロックです。通常のDML操作では軽量なロックが自動取得され、DDL操作では重いロックが取得されます。

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           テーブルロックモード                                    │
├────────────────────────────┬────────────────────────────────────────────────────┤
│ ロックモード               │ 用途・取得される場面                                 │
├────────────────────────────┼────────────────────────────────────────────────────┤
│ ACCESS SHARE               │ SELECT で自動取得                                    │
│                            │ 最も弱いロック。テーブル削除以外は何も妨げない       │
├────────────────────────────┼────────────────────────────────────────────────────┤
│ ROW SHARE                  │ SELECT FOR UPDATE / FOR SHARE で自動取得             │
│                            │ 「この後に行ロックを取るよ」という宣言               │
├────────────────────────────┼────────────────────────────────────────────────────┤
│ ROW EXCLUSIVE              │ UPDATE, DELETE, INSERT で自動取得                    │
│                            │ 「この後に行を変更するよ」という宣言                 │
├────────────────────────────┼────────────────────────────────────────────────────┤
│ SHARE UPDATE EXCLUSIVE     │ VACUUM, CREATE INDEX CONCURRENTLY で取得            │
│                            │ 同時に別のスキーマ変更を防ぐ                         │
├────────────────────────────┼────────────────────────────────────────────────────┤
│ SHARE                      │ CREATE INDEX（非CONCURRENT）で取得                   │
│                            │ テーブルへの書き込みをすべてブロック                  │
├────────────────────────────┼────────────────────────────────────────────────────┤
│ ACCESS EXCLUSIVE           │ ALTER TABLE, DROP, TRUNCATE, VACUUM FULL で取得      │
│                            │ 最も強いロック。SELECTすらブロックする               │
└────────────────────────────┴────────────────────────────────────────────────────┘
```

:::info テーブルロックは「ゲートキーパー」
テーブルレベルロックの主な役割は、DDL（スキーマ変更）とDML（データ操作）の競合を防ぐことです。通常のアプリケーションで問題になるのは、次の行レベルロックの方です。
:::

#### テーブルロックの競合マトリクス

```
要求するロック →
                 ACCESS  ROW     ROW     SHARE   SHARE   ACCESS
                 SHARE   SHARE   EXCL    UPD EX  SHARE   EXCL
保持中のロック ↓
ACCESS SHARE       ○       ○       ○       ○       ○       ×
ROW SHARE          ○       ○       ○       ○       ○       ×
ROW EXCLUSIVE      ○       ○       ○       ○       ×       ×
SHARE UPD EXCL     ○       ○       ○       ×       ×       ×
SHARE              ○       ○       ×       ×       ○       ×
ACCESS EXCLUSIVE   ×       ×       ×       ×       ×       ×

○ = 共存可能（両方同時に取得できる）
× = 競合（後から要求した方は待機）
```

**ポイント**: SELECT（ACCESS SHARE）と UPDATE（ROW EXCLUSIVE）はテーブルレベルでは競合しません。行レベルで競合が起きます。

### 4.2 行レベルロック

行レベルロックは特定の行のみをロックします。テーブルの他の行には影響しないため、高い並行性を維持できます。

#### 4つの行ロックモード

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           行レベルロックモード                                    │
├──────────────────────┬──────────────────────────────────────────────────────────┤
│ ロックモード          │ 用途                                                     │
├──────────────────────┼──────────────────────────────────────────────────────────┤
│ FOR KEY SHARE        │ 外部キー制約の検証で自動取得                              │
│                      │ 主キー以外のカラム更新は許可する                          │
│                      │ 最も弱い行ロック                                         │
├──────────────────────┼──────────────────────────────────────────────────────────┤
│ FOR SHARE            │ 読み取り保護。他のトランザクションの読み取りは許可        │
│                      │ 更新・削除はブロック                                      │
│                      │ 例: 外部キー参照先の存在確認                             │
├──────────────────────┼──────────────────────────────────────────────────────────┤
│ FOR NO KEY UPDATE    │ UPDATE で自動取得（主キー以外のカラム更新時）              │
│                      │ FOR KEY SHARE とは共存可能                                │
│                      │ → 外部キー制約の検証とデータ更新を同時に実行できる        │
├──────────────────────┼──────────────────────────────────────────────────────────┤
│ FOR UPDATE           │ 最も強い行ロック。排他的な行ロック                        │
│                      │ DELETE, 主キー更新, SELECT FOR UPDATE で取得             │
│                      │ 他の行ロック（FOR KEY SHARE 以外）をすべてブロック        │
└──────────────────────┴──────────────────────────────────────────────────────────┘
```

:::tip FOR UPDATE と FOR NO KEY UPDATE の違い
`UPDATE accounts SET balance = 100 WHERE id = 1` は主キー（id）を変更しないため `FOR NO KEY UPDATE` ロックで済みます。一方、`SELECT ... FOR UPDATE` や `DELETE` は最強の `FOR UPDATE` ロックを取ります。この違いは外部キー制約のパフォーマンスに影響します。
:::

#### 基本的な使い方

```sql
-- 排他ロック（更新用）: 読み取りと同時にロック取得
SELECT * FROM accounts WHERE id = 1 FOR UPDATE;
-- → 他のトランザクションはこの行を更新・削除できない（SELECT は可能）
-- → ロック取得と同時に最新のデータを読み取れる
-- → 「読み取ってから更新するまでの間に他の人に変更されたくない」場合に使う

-- 共有ロック（読み取り保護）
SELECT * FROM accounts WHERE id = 1 FOR SHARE;
-- → 他のトランザクションもこの行を FOR SHARE で読み取れる
-- → 更新・削除はブロック
-- → 「参照先が消されないことを保証したい」場合に使う

-- ロック待機をスキップ（NOWAIT）
SELECT * FROM accounts WHERE id = 1 FOR UPDATE NOWAIT;
-- → ロックできない場合は即座にエラー（ERROR: could not obtain lock on row）
-- → 待機したくない場合に使う

-- ロックできた行のみ取得（SKIP LOCKED）
SELECT * FROM tasks WHERE status = 'pending' LIMIT 1 FOR UPDATE SKIP LOCKED;
-- → ロック中の行はスキップして、ロック取得できた行だけ返す
-- → ジョブキューのような「誰か1人が処理すればいい」パターンに最適
```

#### よくある使用パターン

**パターン1: 読み取り後の更新（悲観ロック）**

```sql
BEGIN;
-- 最新のデータを読み取りつつロック
SELECT balance FROM accounts WHERE id = 1 FOR UPDATE;
-- → balance = 10000

-- 読み取った値を基に更新（他のトランザクションに割り込まれない）
UPDATE accounts SET balance = 10000 - 3000 WHERE id = 1;
COMMIT;
```

`FOR UPDATE` なしだと、SELECTとUPDATEの間に別トランザクションが残高を変更する可能性があります（Lost Update問題）。

**パターン2: 親行ロックによるCASCADE DELETE保護**

```sql
BEGIN;
-- 親テーブルの行をロック → 他のトランザクションは同じ行のDELETEで待機する
SELECT * FROM authentication_transaction WHERE id = 'abc' FOR UPDATE;

-- 安全に処理を実行（他のトランザクションはこの行のDELETEに到達できない）
-- ... 処理 ...

DELETE FROM authentication_transaction WHERE id = 'abc';
-- CASCADE DELETEも安全に実行される
COMMIT;
```

**パターン3: ジョブキュー（SKIP LOCKED）**

```sql
BEGIN;
-- 他のワーカーが処理中の行をスキップして、空いている行を取得
SELECT * FROM job_queue
WHERE status = 'pending'
ORDER BY created_at
LIMIT 1
FOR UPDATE SKIP LOCKED;

-- 取得した行を処理
UPDATE job_queue SET status = 'processing' WHERE id = ...;
COMMIT;
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

### 5.3 CASCADE DELETEによるデッドロック（実例）

`ON DELETE CASCADE` を持つ親子テーブルで、同一の親行に対して複数トランザクションが同時にDELETEを実行すると、子テーブルの行ロック取得順序が異なりデッドロックが発生することがあります。

#### 発生メカニズム

```
親テーブル: authentication_transaction (id, tenant_id, ...)
子テーブル: authentication_interactions (id, transaction_id REFERENCES authentication_transaction ON DELETE CASCADE)

子テーブルに3行ある場合:
  interaction_1 (transaction_id = 'abc')
  interaction_2 (transaction_id = 'abc')
  interaction_3 (transaction_id = 'abc')
```

```
時間 →
┌──────────────────────────────────────────────────────────────┐
│ トランザクションA              │ トランザクションB              │
│ （認証完了）                   │ （認証キャンセル）              │
├───────────────────────────────┼──────────────────────────────┤
│ DELETE authentication_        │                              │
│   transaction WHERE id='abc'; │                              │
│   → CASCADE: interaction_1    │                              │
│     のロック取得 ✓             │                              │
│                               │ DELETE authentication_       │
│                               │   transaction WHERE id='abc';│
│                               │   → CASCADE: interaction_3   │
│                               │     のロック取得 ✓            │
│   → CASCADE: interaction_3    │                              │
│     のロック待ち...            │   → CASCADE: interaction_1   │
│            ↑                  │     のロック待ち...            │
│            └────── デッドロック ─────────┘                    │
│                                                              │
│ → PostgreSQLが検出（deadlock_timeout後）                      │
│ → トランザクションAをROLLBACK → 500エラー                    │
└──────────────────────────────────────────────────────────────┘
```

#### 対策: 親行の事前ロック（SELECT FOR UPDATE）

親行を `SELECT FOR UPDATE` で先にロックすることで、後続のトランザクションは親行のロック待ちで直列化され、CASCADE DELETEの行ロック競合が発生しなくなります。

```sql
-- トランザクションA
BEGIN;
SELECT * FROM authentication_transaction WHERE id = 'abc' FOR UPDATE;  -- 親行をロック
-- 認証処理を実行
DELETE FROM authentication_transaction WHERE id = 'abc';  -- CASCADE DELETEも安全
COMMIT;

-- トランザクションB（同時に実行）
BEGIN;
SELECT * FROM authentication_transaction WHERE id = 'abc' FOR UPDATE;  -- Aのロック解放を待機
-- Aが完了後にロック取得 → 行が既に削除済み → 0行返却
-- アプリケーション側で「トランザクションが存在しない」として適切にハンドリング
```

```
時間 →
┌──────────────────────────────────────────────────────────────┐
│ トランザクションA              │ トランザクションB              │
├───────────────────────────────┼──────────────────────────────┤
│ SELECT ... FOR UPDATE         │                              │
│   → 親行ロック取得 ✓          │                              │
│                               │ SELECT ... FOR UPDATE        │
│                               │   → 親行ロック待ち...        │
│ （認証処理実行）               │         ↓                   │
│ DELETE → CASCADE DELETE       │       待機中                 │
│ COMMIT;                       │         ↓                   │
│   → 親行ロック解放            │   → ロック取得               │
│                               │   → 0行返却（削除済み）      │
│                               │   → 適切にハンドリング       │
└──────────────────────────────────────────────────────────────┘
デッドロックなし！
```

:::tip idp-serverでの実装
`AuthenticationTransactionQueryRepository.getForUpdate()` で `SELECT FOR UPDATE` を実行し、`CibaFlowEntryService` / `OAuthFlowEntryService` / `UserOperationEntryService` の `interact()` 等で使用しています。詳細は Issue #1454 を参照してください。
:::

---

### 5.4 デッドロックの回避策

#### 策1: 親行の事前ロック（SELECT FOR UPDATE）

CASCADE DELETEによるデッドロックに最も有効。詳細は上記5.3を参照。

#### 策2: 一貫した順序でロック

```sql
-- 常にIDの昇順でロック
BEGIN;
SELECT * FROM accounts WHERE id IN (1, 2) ORDER BY id FOR UPDATE;
-- これにより、すべてのトランザクションが同じ順序でロックを取得
UPDATE accounts SET balance = balance - 100 WHERE id = 1;
UPDATE accounts SET balance = balance + 100 WHERE id = 2;
COMMIT;
```

#### 策3: ロックタイムアウトの設定

```sql
-- セッション単位でタイムアウト設定
SET lock_timeout = '5s';

BEGIN;
UPDATE accounts SET balance = balance - 100 WHERE id = 1;
-- 5秒以上ロックを待つとエラー
COMMIT;
```

#### 策4: NOWAIT オプション

```sql
BEGIN;
SELECT * FROM accounts WHERE id = 1 FOR UPDATE NOWAIT;
-- ロックできない場合は即座にエラー（待機しない）
COMMIT;
```

#### 策5: アプリケーション側でのリトライ

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

### 5.5 デッドロック検出の設定

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

## 11. ロック実践: よくある問題パターン

基礎を理解したうえで、実際のアプリケーションで遭遇しやすいロック問題のパターンを学びます。

### 11.1 ON CONFLICT (UPSERT) のロック挙動

`INSERT ... ON CONFLICT DO UPDATE` は、対象行に**排他ロック**を取得します。

```sql
-- このUPSERTは (tenant_id, stat_date, event_type) の行に排他ロックを取得
INSERT INTO statistics_events (tenant_id, stat_date, event_type, count)
VALUES ('tenant-1', '2026-03-25', 'login_success', 1)
ON CONFLICT (tenant_id, stat_date, event_type)
DO UPDATE SET count = statistics_events.count + 1;
```

`DO NOTHING` の場合はロックを取得しません。

```sql
-- DO NOTHING は対象行にロックを取得しない
INSERT INTO statistics_daily_users (tenant_id, stat_date, user_id)
VALUES ('tenant-1', '2026-03-25', 'user-1')
ON CONFLICT DO NOTHING;
```

**ポイント**: UPSERTを多用する場合は、`DO UPDATE` と `DO NOTHING` のロック挙動の違いを意識する。

### 11.2 ホットスポット行

同一行に多数のスレッドが集中すると、直列化が発生します。

```
例: statistics_events の PK = (tenant_id, stat_date, event_type)

30スレッドが同時に:
  INSERT INTO statistics_events ('tenant-1', '2026-03-25', 'login_success', 1)
  ON CONFLICT DO UPDATE SET count = count + 1;

→ 全スレッドが同じ1行をめぐって排他ロック競合
→ 実質的にシングルスレッド実行になる
```

```
スレッド1: ████████░░░░░░░░░░░░░░░░░░░░░░  ロック取得 → 更新 → COMMIT
スレッド2: ________████████░░░░░░░░░░░░░░░  ロック待ち → 取得 → 更新 → COMMIT
スレッド3: ________________████████░░░░░░░  ロック待ち → 取得 → 更新 → COMMIT
  ...
スレッド30:                              ████████  最後にやっと実行
```

**対策パターン**:

| パターン | 概要 | トレードオフ |
|:---|:---|:---|
| バッチ集計 | 1日分をまとめて `COUNT(*)` → 1回のUPSERT | リアルタイム性を犠牲 |
| インメモリバッファ | アプリ側で集約してから書き込み | クラッシュ時にデータ消失 |
| キー分散 | ランダムなバケットIDを付与して行を分散 | 読み取り時の集約が必要 |

### 11.3 トランザクション内の外部I/O（最重要）

**最も危険なアンチパターン**: ロックを保持したまま外部I/O（HTTP通信、メール送信等）を行う。

```sql
-- ❌ 危険: ロック保持中に外部I/O
BEGIN;
  UPDATE orders SET status = 'processing' WHERE id = 123;   -- 行ロック取得
  -- ↓ この間、他のトランザクションは id=123 の更新を待つ
  SELECT http_post('https://api.example.com/notify', ...);   -- 500ms かかる
  UPDATE orders SET status = 'notified' WHERE id = 123;
COMMIT;  -- 500ms+ 後にやっとロック解放
```

問題が**カスケード**する仕組み:

```
                                   ロック保持時間
                    ├────────────────────────────────────────────┤
Thread-1: BEGIN → UPDATE(ロック) → HTTP(500ms) → UPDATE → COMMIT
Thread-2:          ↓ ロック待ち(500ms+)          → UPDATE → HTTP → COMMIT
Thread-3:                                          ↓ ロック待ち(1000ms+) → ...
Thread-4:                                                        ↓ ロック待ち(1500ms+)
```

1スレッドの500msが、30スレッドでは最悪 **15秒** の待ち時間に膨張する。
さらにコネクションプールが枯渇すると、新しいリクエストも処理できなくなる。

**解決パターン**:

```sql
-- ✅ パターンA: I/Oをトランザクション外に
BEGIN;
  UPDATE orders SET status = 'processing' WHERE id = 123;
COMMIT;  -- 即座にロック解放

-- トランザクション外でI/O
SELECT http_post('https://api.example.com/notify', ...);

BEGIN;
  UPDATE orders SET status = 'notified' WHERE id = 123;
COMMIT;
```

```sql
-- ✅ パターンB: 順序を変えてロック保持時間を最小化
BEGIN;
  INSERT INTO outbox (order_id, payload) VALUES (123, ...);  -- 新規行なので競合なし
  SELECT http_post('https://api.example.com/notify', ...);   -- I/O実行
  UPDATE orders SET status = 'notified' WHERE id = 123;      -- ロック取得は最後
COMMIT;  -- 数ms後にロック解放
```

### 11.4 ロック競合の調査方法

問題が起きたとき、何を見るか。

#### pg_stat_activity で待機中のセッションを確認

```sql
SELECT
  pid,
  state,
  wait_event_type,
  wait_event,
  query_start,
  NOW() - query_start AS duration,
  LEFT(query, 80) AS query
FROM pg_stat_activity
WHERE state = 'active'
  AND wait_event_type = 'Lock'
ORDER BY duration DESC;
```

#### pg_locks でどの行がロックされているか確認

```sql
SELECT
  blocked.pid AS blocked_pid,
  blocked.query AS blocked_query,
  blocking.pid AS blocking_pid,
  blocking.query AS blocking_query,
  NOW() - blocked_activity.query_start AS blocked_duration
FROM pg_locks blocked
JOIN pg_locks blocking
  ON blocked.transactionid = blocking.transactionid
  AND blocked.pid != blocking.pid
JOIN pg_stat_activity blocked_activity ON blocked.pid = blocked_activity.pid
JOIN pg_stat_activity blocking_activity ON blocking.pid = blocking_activity.pid
WHERE NOT blocked.granted;
```

#### 確認のポイント

| 確認項目 | 見るべきもの | 異常の目安 |
|:---|:---|:---|
| 待機セッション数 | `wait_event = 'transactionid'` のセッション数 | コネクションプールの半数以上 |
| 待機時間 | `NOW() - query_start` | 通常の処理時間の10倍以上 |
| ブロックしているクエリ | `blocking_query` | 外部I/O呼び出しを含むか |

---

## まとめ

1. **ACID特性**を理解し、トランザクションで一貫性を保証する
2. **分離レベル**はユースケースに応じて選択（通常はREAD COMMITTED）
3. **ロック**の仕組みを理解し、デッドロックを防ぐ
4. **MVCC**により読み取りと書き込みが互いをブロックしない
5. **セーブポイント**で部分的なロールバックが可能
6. 長時間トランザクションを避け、**適切なタイムアウト**を設定する
7. **トランザクション内で外部I/Oをしない** — ロック保持時間が膨張しカスケード障害を引き起こす
8. **ホットスポット行**を意識し、バッチ化やキー分散で競合を回避する

## 次のステップ

- [dev-05-query-optimization.md](dev-05-query-optimization.md): クエリ最適化
- [dba-03-replication-ha.md](dba-03-replication-ha.md): レプリケーションと高可用性
- [ケーススタディ: 統計テーブルのロック競合](../26-performance-tuning/14-case-study-lock-contention.md): 実事例による学習
