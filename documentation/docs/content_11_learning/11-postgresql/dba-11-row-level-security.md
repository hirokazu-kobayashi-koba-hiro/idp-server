# PostgreSQL Row Level Security 完全ガイド

PostgreSQL の **Row Level Security (RLS)** の仕組みと、適用した時のクエリ挙動を深掘りするガイド。
基本的な設定方法は [dba-04-security.md](dba-04-security.md) の RLS 章を参照。本ドキュメントは仕組み・挙動・落とし穴に焦点を当てる。

対象読者：
- PostgreSQL の RLS を運用で使う／使い始める DBA・バックエンドエンジニア
- マルチテナント分離を RLS で実装している（あるいは検討中の）開発者
- 「なぜか index が使われない」現象を調査している人

---

## 目次

1. [RLS とは何か](#1-rls-とは何か)
2. [RLS の仕組み](#2-rls-の仕組み)
3. [RLS の有効化方法](#3-rls-の有効化方法)
4. [ポリシーの書き方](#4-ポリシーの書き方)
5. [RLS 適用時の挙動](#5-rls-適用時の挙動)
6. [session 変数との連携（マルチテナントパターン）](#6-session-変数との連携マルチテナントパターン)
7. [他の機能との相互作用](#7-他の機能との相互作用)
8. [落とし穴と回避策（重要）](#8-落とし穴と回避策重要)
9. [デバッグ・可視化](#9-デバッグ可視化)
10. [実装パターン集](#10-実装パターン集)
11. [パフォーマンスチューニング](#11-パフォーマンスチューニング)
12. [まとめと参考資料](#12-まとめと参考資料)

---

## 1. RLS とは何か

### 1.1 概念

**Row Level Security (RLS)** は、テーブルの行単位でアクセス制御を行う PostgreSQL の機能。
クエリの WHERE 句に依存せず、**DB エンジンが自動的に「見える行」を絞り込む**。

```sql
-- RLS なし: アプリが WHERE で絞る (漏れたら全行見える)
SELECT * FROM orders WHERE tenant_id = 'A';

-- RLS あり: アプリは WHERE なしで叩いても DB 側で自動絞り込み
SELECT * FROM orders;  -- 自動で「tenant_id = 'A' のもの」だけ返る
```

### 1.2 なぜ必要か

| ユースケース | RLS のメリット |
|------------|--------------|
| **マルチテナント SaaS** | アプリ層で WHERE 漏れがあっても、DB 層で他テナントの行を物理的に見せない（**Defense in Depth**） |
| **ユーザー別データ分離** | 同じテーブルに複数ユーザーのデータを混在させても、ユーザー間で漏洩しない |
| **管理者と一般ユーザーの区別** | ロールごとに見える行を変える |
| **監査要件への対応** | 「アプリのバグで他テナントが見えた」事故の最終防衛線 |

### 1.3 登場した背景

- PostgreSQL **9.5** (2016 年リリース) で正式導入
- それ以前は views や triggers で代替実装していたが、性能・保守性に難があった
- 商用 DB（Oracle VPD など）では先行していた機能を PostgreSQL も実装

---

## 2. RLS の仕組み

### 2.1 動作レイヤー

RLS は **planner レベル** で動作する：

```
┌───────────────────────────────────┐
│ Application (アプリの SQL クエリ)  │
└────────────────┬──────────────────┘
                 │
                 ▼
┌───────────────────────────────────┐
│ Parser (構文解析)                  │
└────────────────┬──────────────────┘
                 │
                 ▼
┌───────────────────────────────────┐
│ Rewriter (RLS 適用 ★ここ)          │
│  - policy の USING/WITH CHECK を   │
│    クエリの WHERE に自動追加        │
└────────────────┬──────────────────┘
                 │
                 ▼
┌───────────────────────────────────┐
│ Planner (実行プラン作成)            │
│  - LEAKPROOF 属性を考慮            │
└────────────────┬──────────────────┘
                 │
                 ▼
┌───────────────────────────────────┐
│ Executor (実行)                    │
└───────────────────────────────────┘
```

ポイント：
- アプリのクエリは **書き換えられた状態**で planner に渡される
- アプリ側のコードは RLS の存在を意識する必要がない（透過的）

### 2.2 クエリ書き換えの例

ポリシー定義：
```sql
CREATE POLICY tenant_isolation ON orders
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
```

アプリのクエリ：
```sql
SELECT * FROM orders WHERE status = 'paid';
```

PostgreSQL 内部での書き換え後：
```sql
SELECT * FROM orders
WHERE status = 'paid'
  AND tenant_id = current_setting('app.tenant_id')::uuid;  -- ← RLS が追加
```

EXPLAIN で確認すると、Filter 句に RLS 条件が現れる。

### 2.3 Security Barrier としての性質

RLS は **security barrier** として動作する。意味：

> 悪意あるユーザーが任意の関数を WHERE 句に混ぜても、その関数は **RLS チェックの後でしか実行されない**（保証）

これは攻撃防止のため。詳細は [8章 落とし穴](#8-落とし穴と回避策重要) で扱う。

---

## 3. RLS の有効化方法

### 3.1 基本 (ENABLE)

```sql
-- テーブルで RLS を有効化
ALTER TABLE orders ENABLE ROW LEVEL SECURITY;

-- ポリシーを定義
CREATE POLICY tenant_isolation ON orders
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
```

### 3.2 強制 (FORCE)

通常、**テーブル所有者は RLS をバイパスする**。これを止めるのが `FORCE`：

```sql
ALTER TABLE orders FORCE ROW LEVEL SECURITY;
```

| 設定 | 一般ユーザー | テーブル所有者 | SUPERUSER |
|------|:----------:|:------------:|:---------:|
| `ENABLE` のみ | RLS 適用 | **バイパス** | バイパス |
| `ENABLE` + `FORCE` | RLS 適用 | **RLS 適用** | バイパス |

マルチテナント SaaS のような厳格な環境では `FORCE` を推奨。

### 3.3 確認

```sql
SELECT relname, relrowsecurity, relforcerowsecurity
FROM pg_class
WHERE relname = 'orders';
--   relname | relrowsecurity | relforcerowsecurity
--  ---------+----------------+---------------------
--   orders  | t              | t
```

---

## 4. ポリシーの書き方

### 4.1 基本構文

```sql
CREATE POLICY policy_name ON table_name
  [ AS { PERMISSIVE | RESTRICTIVE } ]
  [ FOR { ALL | SELECT | INSERT | UPDATE | DELETE } ]
  [ TO role_name [, ...] ]
  [ USING (expression) ]
  [ WITH CHECK (expression) ];
```

| 句 | 意味 |
|----|------|
| `USING` | **SELECT / UPDATE / DELETE で「どの行が見えるか」**を定義 |
| `WITH CHECK` | **INSERT / UPDATE で「どの行を書けるか」**を定義 |
| `FOR ALL` | 全コマンドに適用 (デフォルト) |
| `TO role` | 特定ロールにのみ適用 |
| `PERMISSIVE` | 「OR で組み合わせ」(デフォルト) |
| `RESTRICTIVE` | 「AND で組み合わせ」(複数ポリシーがある時の厳格化) |

### 4.2 例

```sql
-- 例 1: テナント分離 (読み書き両方)
CREATE POLICY tenant_isolation ON orders
  USING (tenant_id = current_setting('app.tenant_id')::uuid)
  WITH CHECK (tenant_id = current_setting('app.tenant_id')::uuid);

-- 例 2: ロール別のポリシー
CREATE POLICY admin_full_access ON orders
  FOR ALL
  TO admin_role
  USING (true);

CREATE POLICY user_self_only ON orders
  FOR ALL
  TO end_user_role
  USING (user_id = current_setting('app.user_id')::uuid);

-- 例 3: 書き込み制限 (削除を禁じる)
CREATE POLICY no_delete ON orders
  AS RESTRICTIVE
  FOR DELETE
  USING (false);
```

### 4.3 PERMISSIVE と RESTRICTIVE の合成

複数のポリシーがある場合：

- **PERMISSIVE 同士**: いずれかが true なら見える (OR)
- **RESTRICTIVE 同士**: すべて true なら見える (AND)
- **両方ある場合**: `(PERMISSIVE の OR) AND (RESTRICTIVE の AND)`

つまり PERMISSIVE は「許可リスト」、RESTRICTIVE は「禁止条件」として機能する。

---

## 5. RLS 適用時の挙動

### 5.1 EXPLAIN で見える挙動

RLS が効いている時、EXPLAIN プランは独特の形になる：

```
EXPLAIN SELECT * FROM orders WHERE status = 'paid';
```

```
Result  (cost=0.01..4743.46 rows=1000)
  One-Time Filter: ((current_setting('app.tenant_id'::text))::uuid = '67e7eae6-...'::uuid)
  ->  Seq Scan on orders
        Filter: (status = 'paid' AND tenant_id = '67e7eae6-...'::uuid)
```

**読み方**：
- `One-Time Filter` ... RLS の `current_setting` を 1 回だけ評価 (定数化)
- `Filter` ... RLS 条件と元のクエリ条件が AND で結合されている
- `Seq Scan` ... LEAKPROOF 制約等により index が使えない場合に発生（[8章](#8-落とし穴と回避策重要)）

### 5.2 SET / unset 時の動作

```sql
-- session 変数を設定
SET app.tenant_id = '67e7eae6-...';
SELECT * FROM orders;
-- → tenant_id = '67e7eae6-...' の行のみ返る

-- 未設定で呼ぶと？
RESET app.tenant_id;
SELECT * FROM orders;
-- → ERROR: unrecognized configuration parameter "app.tenant_id"
-- (RLS policy 内で current_setting() がエラー)

-- 第二引数 missing_ok=true なら NULL を返す
-- CREATE POLICY ... USING (tenant_id = current_setting('app.tenant_id', true)::uuid)
-- → NULL = uuid は NULL → 全行除外
```

設計上、`app.tenant_id` を必ずセットする運用にするか、`missing_ok=true` で 0 件返すか、選ぶ必要がある。

### 5.3 ロール別の挙動

```sql
-- 一般ユーザー (RLS 適用)
SET ROLE app_user;
SELECT count(*) FROM orders;  -- 自分のテナント分のみ

-- テーブル所有者
SET ROLE orders_owner;
SELECT count(*) FROM orders;
-- ENABLE のみ: 全行見える (バイパス)
-- ENABLE + FORCE: テナント分のみ

-- SUPERUSER は常にバイパス
SET ROLE postgres;
SELECT count(*) FROM orders;  -- 全行見える
```

---

## 6. session 変数との連携（マルチテナントパターン）

### 6.1 典型的な実装

idp-server 等のマルチテナント SaaS で使う典型パターン：

```sql
-- 1. ポリシー定義
CREATE POLICY tenant_isolation ON orders
  USING (tenant_id = current_setting('app.tenant_id')::uuid);

ALTER TABLE orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE orders FORCE ROW LEVEL SECURITY;
```

```java
// 2. アプリ層: リクエスト処理時に SET
String tenantId = resolveTenantFromRequest(request);
connection.createStatement().execute(
    "SET app.tenant_id = '" + tenantId + "'"
);

// 3. 通常のクエリ (テナント絞り込み不要)
PreparedStatement stmt = connection.prepareStatement(
    "SELECT * FROM orders WHERE status = ?"
);
stmt.setString(1, "paid");
ResultSet rs = stmt.executeQuery();
// → DB が自動的に tenant_id で絞り込む
```

### 6.2 SET LOCAL の活用

トランザクション内で限定したい時：

```sql
BEGIN;
SET LOCAL app.tenant_id = '67e7eae6-...';
-- このトランザクション内のクエリのみ RLS が適用
COMMIT;
-- COMMIT 後は設定が消える (安全)
```

connection pool 環境では `SET LOCAL` が安全。なぜなら：
- `SET`（global）だと、connection を返却した後も設定が残る
- 別のリクエストが同じ connection を再利用すると、前のテナントの設定が引き継がれる

### 6.3 注意：session 変数の漏洩リスク

```sql
-- 一般ユーザーが現在の設定を覗ける
SELECT current_setting('app.tenant_id');
```

`app.tenant_id` 自体は秘密情報じゃないので問題ないが、機密値を session 変数に入れるのは避ける。

---

## 7. 他の機能との相互作用

### 7.1 SUPERUSER / BYPASSRLS / FORCE RLS の優先関係

| 接続ロールの属性 | テーブル設定 | RLS 適用？ |
|----------------|------------|:---------:|
| SUPERUSER | 任意 | バイパス (RLS 適用されない) |
| BYPASSRLS = t | 任意 | バイパス |
| 通常ロール | ENABLE のみ | 適用 (ただし所有者はバイパス) |
| 通常ロール | ENABLE + FORCE | 適用 (所有者も適用) |

```sql
-- ロールに BYPASSRLS 属性を付与
ALTER ROLE batch_user BYPASSRLS;

-- 確認
SELECT rolname, rolsuper, rolbypassrls FROM pg_roles;
```

### 7.2 View との関係

view 経由でテーブルにアクセスする場合：

```sql
CREATE VIEW v_orders AS SELECT * FROM orders;

-- view を SELECT する時
SELECT * FROM v_orders;
-- → view を作成したユーザーの権限で RLS が評価される (SECURITY DEFINER 的)
```

`SECURITY INVOKER` 属性をつけると、view を呼び出すユーザーの権限で評価される：

```sql
CREATE VIEW v_orders WITH (security_invoker = true) AS SELECT * FROM orders;
```

### 7.3 Index との関係

RLS 有効テーブルでも、**ほとんどの index は通常通り使える**。`tenant_id = ?::uuid` のような equality 検索（`uuid_eq` / `texteq` 等の leakproof な関数を使う条件）では、btree index が普通に選ばれる。

例外は、**leakproof でない関数を含む条件式**（JSONB の `@>` / `->>` など）。この場合 planner は対応する index を選べず Seq Scan に倒れる。詳細は [8章 落とし穴と回避策](#8-落とし穴と回避策重要) で扱う。

| 条件式の例 | 使われる関数 | leakproof | RLS 下で index 使用 |
|----------|------------|:---------:|:------------------:|
| `tenant_id = '...'::uuid` | uuid_eq | ✅ | ✅ 普通に使える |
| `email = '...'` | texteq | ✅ | ✅ 普通に使える |
| `age > 18` | int4gt | ✅ | ✅ 普通に使える |
| `payload @> '...'::jsonb` | jsonb_contains | ❌ | ❌ index 不使用、Seq Scan |
| `payload ->> 'key' = '...'` | jsonb_object_field_text | ❌ | ❌ index 不使用、Seq Scan |

---

## 8. 落とし穴と回避策（重要）

### 8.1 LEAKPROOF 制約とは

PostgreSQL の関数には `LEAKPROOF` 属性がある：

| 属性 | 意味 |
|------|------|
| `LEAKPROOF` | 引数の値を side channel（エラーメッセージ・実行時間・例外内容）から漏らさない |
| `NOT LEAKPROOF`（デフォルト） | 上記の保証なし。安全側に倒すと想定 |

RLS 有効テーブルでは、**planner は leakproof でない関数を RLS チェックより前に評価しない**（攻撃防止のため）。

結果：
- 条件式に leakproof でない関数を含むと、その条件を **index で先に評価できない**（push down できない）
- 全行 Seq Scan された後に上位レイヤーで filter する羽目に

### 8.2 実例：jsonb 関数の罠

PostgreSQL の **jsonb 関数群はデフォルトで全て non-leakproof**：

```sql
SELECT proname, proleakproof FROM pg_proc
WHERE proname IN ('jsonb_contains', 'jsonb_object_field_text', 'texteq', 'uuid_eq');
--   proname                 | proleakproof
--  -------------------------+--------------
--   jsonb_contains          | f      ← @> 演算子の実装
--   jsonb_object_field_text | f      ← ->> 演算子の実装
--   texteq                  | t      ← text = text
--   uuid_eq                 | t      ← uuid = uuid
```

つまり：

| クエリ | RLS 下での挙動 |
|--------|--------------|
| `WHERE tenant_id = ?::uuid` | uuid_eq は leakproof → index 使える |
| `WHERE payload @> '...'::jsonb` | jsonb_contains は non-leakproof → **GIN index 使えない** |
| `WHERE payload ->> 'key' = '...'` | jsonb_object_field_text は non-leakproof → **expression index 使えない** |

### 8.3 確認方法

```sql
-- 1. EXPLAIN で確認
EXPLAIN (ANALYZE, BUFFERS) SELECT id FROM orders
WHERE payload @> '{"key": "value"}'::jsonb
  AND tenant_id = ?::uuid;

-- 期待: Bitmap Index Scan on idx_payload (GIN)
-- 実際 (RLS下): Seq Scan + One-Time Filter
```

```sql
-- 2. pg_stat_user_indexes で「使われているかどうか」確認
SELECT indexrelname, idx_scan FROM pg_stat_user_indexes
WHERE relname = 'orders';

-- idx_scan = 0 が長期間続いている index は要注意 → RLS で殺されている可能性
```

### 8.4 回避策

| 案 | 内容 | リスク |
|----|------|------|
| (A) `ALTER FUNCTION ... LEAKPROOF` | 組み込み関数を leakproof マーク | 関数のエラー経路等で情報漏洩しないかセキュリティレビュー要 |
| (B) `ALTER ROLE app BYPASSRLS` | アプリロールを RLS バイパス | テナント分離の防御層を 1 段失う |
| (C) RLS policy 削除 | RLS そのものを使わない | アプリの WHERE 句のみが防御 |
| (D) Expression index | `((payload ->> 'key'))` 形式の btree index | jsonb_object_field_text が leakproof でないため、これ単独では効かない（(A) との併用が前提） |
| (E) 現状維持 | 何もしない | データ量に応じて線形に遅くなる |

実例については [プロジェクトの実証分析](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/issues/1550) も参照。

### 8.5 LEAKPROOF マークの安全性評価ポイント

`ALTER FUNCTION ... LEAKPROOF` を導入する前に：

- 該当関数が **データ依存でエラーを投げないか**（投げると side channel になる）
- 該当関数の **実行時間がデータ依存しないか**（タイミング攻撃の余地）
- アプリ側で **エラーメッセージを sanitize しているか**（生エラーをユーザーに返していないか）
- アプリ用 DB ロールに **CREATE FUNCTION 権限がないか**（悪意ある leakproof 関数を作れないように）

`jsonb_contains` / `jsonb_object_field_text` は構造的にデータ依存エラーを投げない設計のため、アプリ経由アクセス限定の環境では LEAKPROOF マーク可能と判断できる。

### 8.6 マネージド DB 環境での設計指針

AWS RDS / Aurora / Google Cloud SQL / Azure Database for PostgreSQL などの **マネージドサービスでは、`jsonb_contains` 等の組み込み関数の所有者が `rdsadmin` 等のシステムロールに固定**されており、ユーザーは `ALTER FUNCTION ... LEAKPROOF` を実行できない（実行すると `ERROR: must be owner of function jsonb_contains`）。

このため、マネージド DB を採用する場合は **(A) LEAKPROOF マーク案が使えない**ことを前提に、**index 設計を最初から適切に行う**必要がある。

#### 設計時の判断軸

1. **動的キー検索は本当に必要か** — 多くのケースは固定キーで済む
   - 固定なら **expression index** で対処可能
   - 動的なら GIN index に頼ることになるが、RLS との相性問題が残る

2. **JSONB を「検索対象」にするか「構造化データ」にするか**
   - 検索対象なら → 固定キーに絞った設計 + expression index
   - 構造化データなら → JSONB のまま保持し検索しない（取得は id 経由）

3. **テナント分離の防御層をどう設計するか**
   - 「RLS + アプリ層 WHERE」二重防御を維持 → BYPASSRLS 付与 + expression index（要 SUPERUSER）
   - 「アプリ層のみで十分」と判断 → RLS policy 削除 or BYPASSRLS 付与

#### マネージド DB で取りうる戦略

| 戦略 | 内容 | 適用条件 |
|-----|------|---------|
| **Expression index + 検索キー限定** | `((payload ->> 'fixed_key'))` の btree index | 検索キーが事前に特定できる |
| **BYPASSRLS 付与 + index 追加** | アプリロールに BYPASSRLS（マネージド DB でも `rds_superuser` 等で実行可能なケースが多い） | テナント分離をアプリ層で完全保証できる |
| **RLS policy 削除** | RLS そのものを使わない | テナント分離の二重防御を諦める判断 |
| **Column 化 / 正規化** | JSONB を諦めて通常 column に | スキーマが安定している項目 |
| **検索エンジン外出し** | Elasticsearch 等で別途検索 | 全文検索や複雑なクエリが必要な場合 |

#### 設計アンチパターン

- ❌ **マネージド DB 採用前提で `jsonb_contains @>` を多用する設計** → LEAKPROOF マークが効かないため index が使えない
- ❌ **動的キー検索を想定した JSONB スキーマ + RLS 強制** → どの index 戦略も限定的
- ❌ **「とりあえず JSONB に入れておけば後で検索できる」** → 検索パフォーマンスが線形に悪化

#### 設計時の推奨フロー

```
[新規 JSONB column 導入時]
        ↓
   検索対象にする？ ──── No ──→ JSONB のまま保持、取得は id 経由
        ↓ Yes
   検索キーは固定？ ──── No ──→ 検索エンジン外出し or GIN + BYPASSRLS 検討
        ↓ Yes
   expression index を migration に含める
        ↓
   RLS の防御層を維持するか？ ── No ──→ RLS policy 削除
        ↓ Yes
   BYPASSRLS をアプリロールに付与（マネージド DB 制約を事前検証）
```

---

## 9. デバッグ・可視化

### 9.1 ポリシー一覧

```sql
SELECT schemaname, tablename, policyname, permissive, roles, cmd, qual, with_check
FROM pg_policies
WHERE tablename = 'orders';
```

### 9.2 RLS 一時無効化（デバッグ用）

```sql
-- session レベルで RLS を無効化（要 SUPERUSER または BYPASSRLS）
SET row_security = off;
SELECT * FROM orders;  -- 全行見える
RESET row_security;
```

`FORCE ROW LEVEL SECURITY` でも、SUPERUSER と BYPASSRLS ロールは `SET row_security = off` で無効化できる。

### 9.3 EXPLAIN の読み方

| プラン要素 | 意味 |
|----------|------|
| `One-Time Filter: current_setting(...)` | RLS 条件を 1 回だけ評価して定数化（成功） |
| `Filter: (... AND tenant_id = ...)` | RLS 条件が他の条件と AND 結合された状態 |
| `Subquery Scan` | RLS が subquery として展開された（複雑な policy の場合） |

### 9.4 関数の LEAKPROOF 状態確認

```sql
-- 全 leakproof 関数を一覧
SELECT proname, pronamespace::regnamespace AS schema
FROM pg_proc
WHERE proleakproof = true
ORDER BY proname;
```

---

## 10. 実装パターン集

### 10.1 マルチテナント分離（基本）

```sql
ALTER TABLE orders ENABLE ROW LEVEL SECURITY;
ALTER TABLE orders FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON orders
  USING (tenant_id = current_setting('app.tenant_id')::uuid)
  WITH CHECK (tenant_id = current_setting('app.tenant_id')::uuid);
```

### 10.2 テナント + ユーザー二重分離

```sql
CREATE POLICY tenant_and_user_isolation ON user_profiles
  USING (
    tenant_id = current_setting('app.tenant_id')::uuid
    AND user_id = current_setting('app.user_id')::uuid
  );
```

### 10.3 管理者バイパスパターン

```sql
-- 一般ユーザー: 自テナントのみ
CREATE POLICY user_tenant_access ON orders
  FOR ALL
  TO end_user_role
  USING (tenant_id = current_setting('app.tenant_id')::uuid);

-- 管理者: 全テナント
CREATE POLICY admin_full_access ON orders
  FOR ALL
  TO admin_role
  USING (true);
```

### 10.4 削除のみ禁止

```sql
CREATE POLICY allow_read_write ON orders
  USING (tenant_id = current_setting('app.tenant_id')::uuid);

CREATE POLICY no_delete ON orders
  AS RESTRICTIVE
  FOR DELETE
  USING (false);
```

---

## 11. パフォーマンスチューニング

### 11.1 RLS のオーバーヘッド

RLS が有効でも、適切に設計されていれば overhead は最小限：
- ポリシーの条件が **leakproof な関数（uuid_eq, texteq 等）** のみで構成されている
- 適切な index が貼られている
- `current_setting` が `One-Time Filter` で定数化される

### 11.2 index 設計のコツ

| 用途 | 推奨 index |
|------|----------|
| tenant_id での絞り込み | btree on `tenant_id` (uuid_eq は leakproof で問題なし) |
| `payload @> '{...}'` JSONB containment | GIN on `payload` + 必要なら `jsonb_contains` を LEAKPROOF マーク |
| `payload ->> 'key' = '...'` JSON 値 | btree on `((payload ->> 'key'))` + `jsonb_object_field_text` LEAKPROOF マーク |

### 11.3 連結 index による高速化

```sql
-- tenant_id を先頭にした複合 index
CREATE INDEX idx_orders_tenant_status
  ON orders (tenant_id, status);

-- RLS の tenant_id 条件と他の WHERE 条件が同時に効く
```

### 11.4 監視

```sql
-- 一度も使われていない index を抽出
SELECT schemaname, relname, indexrelname, pg_size_pretty(pg_relation_size(indexrelid))
FROM pg_stat_user_indexes
WHERE idx_scan = 0
  AND schemaname = 'public'
ORDER BY pg_relation_size(indexrelid) DESC;
```

`idx_scan = 0` が長期間続いている GIN / expression index は、RLS + LEAKPROOF 制約に殺されている可能性が高い。

---

## 12. まとめと参考資料

### 12.1 RLS 導入時のチェックリスト

- [ ] `ENABLE ROW LEVEL SECURITY` + `FORCE ROW LEVEL SECURITY` を設定したか
- [ ] policy の条件式に **leakproof でない関数**を含んでいないか確認
- [ ] アプリ層で `SET app.tenant_id`（または同等）を確実に発行しているか
- [ ] connection pool 環境では `SET LOCAL` の使用を検討
- [ ] アプリのテストで RLS バイパスがないか確認
- [ ] EXPLAIN で意図した index が選ばれているか確認
- [ ] `pg_stat_user_indexes.idx_scan` の継続監視

### 12.2 落とし穴サマリ

| 落とし穴 | 対処 |
|---------|------|
| jsonb の GIN/expression index が一切使われない | `jsonb_contains` / `jsonb_object_field_text` を LEAKPROOF マーク |
| `current_setting('app.tenant_id')` で ERROR | `SET` を確実に発行 or `current_setting(name, true)` で missing OK |
| connection pool で他テナントの設定が引き継がれる | `SET LOCAL` を使う or 接続返却前に `RESET` |
| view 経由で意図せず RLS バイパス | `WITH (security_invoker = true)` を使う |
| RLS のテストが甘い | 複数テナントのデータを混在させたテストを必ず書く |

### 12.3 参考資料

- [PostgreSQL 公式ドキュメント: 5.9. Row Security Policies](https://www.postgresql.org/docs/current/ddl-rowsecurity.html)
- [PostgreSQL 公式: CREATE POLICY](https://www.postgresql.org/docs/current/sql-createpolicy.html)
- [PostgreSQL 公式: CREATE FUNCTION (LEAKPROOF)](https://www.postgresql.org/docs/current/sql-createfunction.html)
- [PostgreSQL mailing list: Row Level Security − leakproof-ness and performance](https://www.postgresql.org/message-id/2811772.0XtDgEdalL@peanuts2)
- [Postgres Row-Level Security Footguns - Bytebase](https://www.bytebase.com/blog/postgres-row-level-security-footguns/)
- [5mins of Postgres: RLS と LEAKPROOF - pganalyze](https://pganalyze.com/blog/5mins-postgres-row-level-security-bypassrls-security-invoker-views-leakproof-functions)

### 12.4 関連ドキュメント（idp-server 内）

- [dba-04-security.md](dba-04-security.md) — PostgreSQL セキュリティ全般、RLS 基本設定
- [dba-08-planner.md](dba-08-planner.md) — planner / 実行計画の詳細
- [dev-03-indexes.md](dev-03-indexes.md) — index 設計
- [dev-05-query-optimization.md](dev-05-query-optimization.md) — クエリ最適化
