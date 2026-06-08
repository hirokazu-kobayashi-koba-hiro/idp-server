# `idp_user` ページネーション index 追加 Runbook

Issue #1460: `idp_user (tenant_id, created_at DESC)` の B-tree index を本番運用に追加する手順。

MySQL を本番で使う場合は `libs/idp-server-database/mysql/operation/idp-user-tenant-created-at-index/` 配下の同名 SQL を使う。手順は同じ。

---

## 0. 目的と前提

- ユーザー検索 API の `ORDER BY created_at DESC` ページネーションで、対応 index がないため大規模テナントで Parallel Seq Scan になる
- 200 万行規模で **185ms → 2.5ms (75 倍)** の改善 (Issue #1460 計測)

### 前提条件

- [ ] `V0_10_0_4` Flyway ファイルを **本番に deploy する前** に Step 2 (`create_index.sql` で `CONCURRENTLY`) を済ませる。`CREATE INDEX IF NOT EXISTS` なので、index が既にあれば V0_10_0_4 は no-op で安全

---

## 1. 接続情報の準備

### 1.1 環境変数

```bash
export PGHOST=your-db-host.example.com
export PGPORT=5432
export PGDATABASE=idpserver
export PGUSER=idpserver
export PGPASSWORD=yourpassword
```

### 1.2 作業ディレクトリ

```bash
cd libs/idp-server-database/postgresql/operation/idp-user-tenant-created-at-index
```

### 1.3 接続確認

```bash
psql -c "SELECT current_database(), current_user, now();"
```

---

## 2. index 作成

```bash
psql -f create_index.sql
```

`CREATE INDEX CONCURRENTLY` で書き込みブロックなしの online build。200 万行で 5-30 分目安。

進行状況の監視 (別 session):

```bash
psql -c "
  SELECT phase, blocks_done, blocks_total,
         round(100.0 * blocks_done / NULLIF(blocks_total, 0), 1) AS pct
  FROM pg_stat_progress_create_index;
"
```

完了後、`create_index.sql` の末尾の SELECT が空であれば OK。
INVALID な行が返ったら `DROP INDEX CONCURRENTLY idx_idp_user_tenant_created_at;` で消して再実行。

---

## 3. 動作確認

```bash
psql -c "
EXPLAIN (ANALYZE, BUFFERS)
SELECT id FROM idp_user
WHERE tenant_id = '<sample-tenant-uuid>'::uuid
ORDER BY created_at DESC
LIMIT 20;
"
```

`Index Scan using idx_idp_user_tenant_created_at` が出れば成功。

---

## 設計メモ

### `(tenant_id)` 単独 index を追加しなくてよい理由

`(tenant_id, created_at DESC)` の **leftmost prefix** で `WHERE tenant_id = ?` 単独検索もカバーできる (B-tree の標準動作)。追加の `(tenant_id)` 単独 index は冗長になるので不要。

---

## 付録: ベンチマーク再現

ローカル / ステージング限定で再現可能なスクリプトを同梱。データ投入と計測を分離して、データ投入は 1 回 / 計測は何度でも繰り返せる構成にしている。

```bash
# 1. データ投入 (200 万行ダミー + role/permission + idp_user_roles + index + ANALYZE)
#    冪等 (ON CONFLICT DO NOTHING) なので再実行 OK
psql -f bench_setup.sql

# 2. 計測 (EXPLAIN ANALYZE のみ。状態を変えないので何度でも実行可)
psql -f bench.sql

# 3. 完了後の後片付け (ダミーデータ DELETE + 検証用 index DROP)
psql -f bench_cleanup.sql
```

| ファイル | 役割 | 冪等 |
|---------|------|------|
| `bench_setup.sql` | データ投入 (1 回) | ✅ |
| `bench.sql` | 計測のみ (何度でも) | ✅ |
| `bench_cleanup.sql` | 後片付け | ✅ |

### 計測結果 (ローカル / 同一テナントに 200 万行追加)

#### `selectList` (Issue #1460): `ORDER BY created_at DESC LIMIT 20`

| 状態 | Execution Time | Plan |
|------|---------------|------|
| index なし | 340.568 ms | Parallel Seq Scan + top-N heapsort |
| index あり |   1.280 ms | Index Scan using idx_idp_user_tenant_created_at |

約 266 倍の改善。Issue #1460 で報告された 75 倍 (185ms → 2.5ms) よりも幅が大きいのは、テナント分散の差 (Issue は 200 万 = 1 テナント想定、ローカルは 200 万 + 既存 4070 行 + 1500 テナント混在) によるものと推測。

#### `selectCount` (Issue #1565): role/permission 絞り込み無しの総件数取得

bench.sql Step 5 (現状: 常に 4-way JOIN + COUNT(DISTINCT)) と Step 6 (改善後: 単表 COUNT(*)) を `(tenant_id, created_at DESC)` index あり状態で比較する。テーブル状態は Step 4 と同じ (200 万行 + index あり)。実行は `psql -f bench.sql` で連続計測可能。
