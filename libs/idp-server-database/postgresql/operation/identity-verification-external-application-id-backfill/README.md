# `external_application_id` Backfill Runbook

Issue #1550 follow-up: `V0_10_0_2` で追加した `external_application_id` カラムについて、本番運用での index 作成 + 既存データの埋め込み手順。

MySQL を本番で使う場合は `libs/idp-server-database/mysql/operation/identity-verification-external-application-id-backfill/` 配下の同名 SQL を使う。手順は同じ。

---

## 0. 目的と前提

- `V0_10_0_2` マイグレーションで `identity_verification_application.external_application_id VARCHAR(255)` カラムが追加される (空のまま、index なし)
- 新コードはコールバック検索でこのカラムを優先利用、ヒットしない時は旧来の JSONB 検索にフォールバックする
- 既存の行は `application_details` の中に値があるが新カラムは `NULL`。**デプロイ後にこのスクリプトで index 作成 + 既存値の埋め込みを行う**

### 前提条件

- [ ] `V0_10_0_2` Flyway マイグレーション適用済み (`ADD COLUMN external_application_id`)
- [ ] 本番運用で書込みブロックを避けるため、`V0_10_0_3` 適用前に Step 2 (`create_index.sql` で `CONCURRENTLY`) を済ませること。`V0_10_0_3` 自体は `CREATE INDEX IF NOT EXISTS` なので、index が既にあれば no-op で安全
- [ ] 新コードのデプロイ完了

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
cd libs/idp-server-database/postgresql/operation/identity-verification-external-application-id-backfill
```

### 1.3 接続確認

```bash
psql -c "SELECT current_database(), current_user, now();"
```

---

## 1.5 事前の重複チェック (UNIQUE 制約のため)

index は `UNIQUE (tenant_id, external_application_id)` で作るため、重複行があると build 失敗 (INVALID state) する。事前に確認:

```bash
psql -c "
  SELECT tenant_id, external_application_id, COUNT(*) AS dup_count
  FROM identity_verification_application
  WHERE external_application_id IS NOT NULL
    AND external_application_id <> ''
  GROUP BY tenant_id, external_application_id
  HAVING COUNT(*) > 1
  ORDER BY dup_count DESC
  LIMIT 100;
"
```

0 行で OK。返ってきたら外部 vendor 側のロジックに何か起きている可能性大なので、業務側と確認してから対応。

---

## 2. index 作成

```bash
psql -f create_index.sql
```

`CREATE INDEX CONCURRENTLY` で書き込みブロックなしの online build。100万行で 5-30 分目安。

進行状況の監視 (別 session):

```bash
psql -c "
  SELECT phase, blocks_done, blocks_total,
         round(100.0 * blocks_done / NULLIF(blocks_total, 0), 1) AS pct
  FROM pg_stat_progress_create_index;
"
```

完了後、`create_index.sql` の末尾の SELECT が空であれば OK。
INVALID な行が返ったら `DROP INDEX CONCURRENTLY idx_verification_external_application_id;` で消して再実行。

---

## 3. 事前確認 (backfill 対象の把握)

```bash
psql -c "
  SELECT COUNT(*) AS rows_to_backfill
  FROM identity_verification_application
  WHERE external_application_id IS NULL;
"
```

`rows_to_backfill = 0` なら Step 4 以降をスキップしてよい。

---

## 4. バックアップ

```bash
pg_dump -t identity_verification_application -F c \
  -f identity_verification_application_$(date +%Y%m%d_%H%M%S).dump
```

---

## 5. backfill 実行

```bash
psql -f migrate_data.sql
```

中身:

- `\set ON_ERROR_STOP on`
- 行数表示 (before)
- `UPDATE ... SET external_application_id = application_details ->> (config の callback_application_id_param)` を一括実行
- 行数表示 (after) — 残った NULL は「設定に `callback_application_id_param` がない verification_type」「`application_details` にその key がない行」

---

## 6. 検証

```bash
psql -f verify_migration.sql
```

`external_application_id` と `application_details ->> key` が **一致しない行**を返す。0 行で OK。

---

## 7. 後続: 旧フォールバック経路の整理

backfill 完了後、`IdentityVerificationApplicationQueryDataSource.get(tenant, key, identifier)` のフォールバック (JSONB 検索) はもう走らない (= `external_application_id` で全部ヒットする)。旧経路を削除する PR は別途切り出す。

---

## リカバリ

backfill をやり直したい場合:

```sql
UPDATE identity_verification_application
SET external_application_id = NULL
WHERE external_application_id IS NOT NULL;
-- もしくは特定 tenant だけ
-- WHERE tenant_id = '<uuid>';
```

その後 `psql -f migrate_data.sql` を再実行 (`IS NULL` のみ更新するので idempotent)。

---

## 付録: ローカル性能計測 (bench)

本番手順 (Step 2 `create_index.sql`) で使う `CREATE UNIQUE INDEX CONCURRENTLY` の所要時間をローカルで実測するためのスクリプト。

### ファイル

| ファイル | 役割 |
|---------|------|
| `bench_setup.sql` | bench マーカー (`verification_type = 'BENCH-EXTERNAL-APP-ID'`) で `ROW_COUNT` 行のダミーを投入。半数の `external_application_id` は `NULL`、半数は UUID テキスト |
| `bench.sql` | DROP CONCURRENTLY → `CREATE UNIQUE INDEX CONCURRENTLY` を `\timing on` 込みで計測 |
| `bench_cleanup.sql` | bench 用 index と行を削除 |

### 実行例 (1M 行)

```bash
cd libs/idp-server-database/postgresql/operation/identity-verification-external-application-id-backfill

psql -v ROW_COUNT=1000000 -f bench_setup.sql
psql -f bench.sql
psql -f bench_cleanup.sql
```

200万行で計測したいときは `-v ROW_COUNT=2000000`。

### 注意

- bench の対象テナント ID は `bench_setup.sql` 冒頭の `:TARGET_TENANT` で固定 (e2e admin tenant)。必要に応じて書き換える
- `CREATE INDEX CONCURRENTLY` はトランザクション内で実行できないため、`psql -f` で直接流す形 (`BEGIN/COMMIT` を入れない)
- ローカル / ステージング以外では実行しないこと

### 計測結果 (ローカル / Apple Silicon + NVMe SSD)

| 行数 | テーブルサイズ | `CREATE UNIQUE INDEX CONCURRENTLY` | index サイズ | 半数 NULL の内訳 |
|------|---------------|-----------------------------------|-------------|---------------|
| 1,000,000 | 336 MB | **1,060 ms** (1.06 s) | 56 MB | 500,000 値あり / 500,000 NULL |
| 2,000,000 | 676 MB | **2,171 ms** (2.17 s) | 113 MB | 1,000,000 値あり / 1,000,000 NULL |

行数に比例してほぼ線形 (約 2 倍)。

### 本番想定

本番 (AWS RDS 等) では heap scan の I/O コストが支配的で、ローカル SSD よりは遅くなる。実用上は **AWS 環境での `idp_user` の同等規模 index 作成時間と同じレンジ** で見積もるのが現実的:

- 基本想定: 数十秒〜数分のオーダー
- テーブル幅が大きい本番 (`application_details` の JSONB が太い) の場合、`idp_user` よりサイズ比に応じて伸びる
- 同時 write 負荷が高い時間帯は `CONCURRENTLY` の wait で更に伸びる可能性

予測したい場合は事前に本番でテーブルサイズを確認し、`idp_user` の実績時間と比例計算するのが安全:

```bash
# 本番でテーブルサイズを確認
psql -c "SELECT
    pg_size_pretty(pg_total_relation_size('identity_verification_application')) AS app_size,
    pg_size_pretty(pg_total_relation_size('idp_user'))                          AS user_size"
```

