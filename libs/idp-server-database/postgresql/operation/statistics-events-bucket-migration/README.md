# `statistics_events` → `statistics_event_buckets` 移行 Runbook

Issue #1443 のバケット分散導入にともなう、本番データ移行手順。

このページは **上から順にコピペで実行** すれば移行が完了するように構成している。

MySQL を本番で使う場合は、同名の SQL ファイル群が `libs/idp-server-database/mysql/operation/statistics-events-bucket-migration/` にある。手順 (Step 0〜6) は同じで、`migrate_data.sql` / `verify_migration.sql` の代わりに `migrate_data.mysql.sql` / `verify_migration.mysql.sql` を使い、`psql` の代わりに `mysql` クライアントを使う。

---

## 0. 移行の目的と前提

- `V0_10_0_1` マイグレーションで新テーブル `statistics_event_buckets` が作成される (CREATE のみ、データは空)
- 新コードはデプロイ後、リアルタイム書込みを **新テーブル** に行う
- 旧テーブル `statistics_events` の歴史データは、デプロイ完了後に本スクリプトで新テーブルへ移送する

### 前提条件

- [ ] `V0_10_0_1` Flyway マイグレーションが適用済み
- [ ] 新コードのデプロイが完了し、**全 Pod が新コードに置き換わっている**
- [ ] 旧コードを実行する Pod がもう存在しない (= 旧テーブルへの新規書込みが止まっている)
- [ ] 本リポジトリを手元に clone してある (`migrate_data.sql` / `verify_migration.sql` を実行するため)

ローリング完了の確認:

```bash
kubectl rollout status deployment/idp-server -n <namespace>
# または
kubectl get pods -l app=idp-server -o jsonpath='{.items[*].spec.containers[*].image}'
# → すべて新イメージタグで揃っていること
```

### 推奨実行タイミング (SLA)

新コードのローリングデプロイ完了から **30 分以内** に本スクリプトを実行することを推奨。

- 過渡期 (新テーブル単体で歴史データが薄い状態) を最小化するため
- 管理 API の数値が一時的に薄く見えるが、本スクリプト実行で即座に揃う
- 30 分はあくまで目安。データ規模が大きい場合は前倒し、小さい場合は数時間以内でも可

---

## 1. 接続情報の準備

以降のすべての `psql` コマンドは、ここで設定した環境変数と `~/.pgpass` を参照する。

### 1.1 環境変数の設定

接続情報を環境に合わせて書き換えてからコピペする。

```bash
export PGHOST=localhost
export PGPORT=5432
export PGDATABASE=idpserver
export PGUSER=idpserver
```

### 1.2 パスワードの設定（環境変数）

パスワードも環境変数で渡す。`yourpassword` の部分を実際のパスワードに書き換える:

```bash
export PGPASSWORD=yourpassword
```

**注意**:

- 先頭にスペースを 1 つ入れて実行すると、`HISTCONTROL=ignorespace` が有効な shell では shell history に残らない (推奨)。それでも history に残ったら `history -d <line>` で削除する
- `PGPASSWORD` は他プロセスから `ps eauxww` で読み取られうる。マルチユーザー環境では `~/.pgpass` を使う方が安全 (下記の補足参照)
- 作業が終わったら `unset PGPASSWORD` で確実に消す

<details>
<summary>補足: より安全に運用したい場合は ~/.pgpass に永続化する</summary>

上で `PGPASSWORD` を設定済みなら、そのままその値を流用して `~/.pgpass` に書ける（パスワードを 2 回書かなくて済む）:

```bash
echo "${PGHOST}:${PGPORT}:${PGDATABASE}:${PGUSER}:${PGPASSWORD}" >> ~/.pgpass
chmod 600 ~/.pgpass
unset PGPASSWORD  # env からは消しておく
```

以降 `PGPASSWORD` を毎回 export せずとも `psql` が `~/.pgpass` を読んでくれる。

既存の `~/.pgpass` に同じ接続先の行があると重複追記になる。心配なら `grep -F "${PGHOST}:${PGPORT}:${PGDATABASE}:${PGUSER}:" ~/.pgpass` で確認してから追記。

</details>

### 1.3 作業ディレクトリへ移動

```bash
cd libs/idp-server-database/postgresql/operation/statistics-events-bucket-migration
```

### 1.4 接続確認

```bash
psql -c "SELECT current_database(), current_user, now();"
```

`idpserver` / `idpserver` / 現在時刻 が表示されれば OK。パスワード入力を求められた場合は `~/.pgpass` のパーミッション (`chmod 600`) と書式 (`host:port:db:user:password`) を見直す。

---

## 2. 事前確認（移行対象の把握）

```bash
psql -c "
  SELECT
    (SELECT COUNT(*) FROM statistics_events)        AS legacy_rows,
    (SELECT COUNT(*) FROM statistics_event_buckets) AS new_rows;
"
```

- `legacy_rows`: 旧テーブルの行数。**これが移行対象**
- `new_rows`: 新テーブルの行数。デプロイ完了後の新 Pod がリアルタイム書込みした分

`legacy_rows = 0` の場合は移行対象がないので Step 4 (移行実行) と Step 5 (検証) をスキップしてよい。

---

## 3. バックアップ

万が一の切り戻しに備えて、両テーブルをバックアップする。

```bash
pg_dump -t statistics_events -t statistics_event_buckets -F c \
  -f statistics_backup_$(date +%Y%m%d_%H%M%S).dump
```

---

## 4. 移行実行

```bash
psql -f migrate_data.sql
```

スクリプトの中身:

| Step | 内容 |
|------|------|
| ① `\set ON_ERROR_STOP on` | エラー時に psql を即終了させる。ガードを実効化するために必須 |
| ② 行数表示 (before) | 移行前の `legacy_rows`, `new_rows_before` を出力 |
| ③ 事前ガード | 新テーブルに過去日付かつ `bucket_id = 0` の行が既にあれば `RAISE EXCEPTION` で中断 (= 前回 migrate 済みの二重実行防止) |
| ④ `INSERT ... SELECT` | 旧テーブルの全行を `bucket_id = 0` で新テーブルへ挿入 |
| ⑤ `ON CONFLICT DO UPDATE` | 既存の `bucket_id = 0` 行があれば加算マージ (通常は新規 INSERT) |
| ⑥ 行数表示 (after) | 移行後の `legacy_rows`, `new_rows_after` を出力 |

期待される出力例:

```
 legacy_rows | new_rows_before
-------------+-----------------
       N     |       M
DO
BEGIN
INSERT 0 N
COMMIT
 legacy_rows | new_rows_after
-------------+----------------
       N     |       M + N
Migration complete. Run verify_migration.sql to spot-check totals.
```

`new_rows_after = new_rows_before + legacy_rows` になる (リアルタイム書込みは `bucket_id ∈ [1, BUCKET_COUNT]` なので `bucket_id = 0` の INSERT と衝突しない)。

---

## 5. 検証

```bash
psql -f verify_migration.sql
```

`legacy.count` と `bucket_id = 0` の `count` が全期間で完全一致するかを見る。

| 結果 | 意味 |
|------|------|
| **0 行 (空)** | ✅ 全期間で `legacy.count` と `bucket_0.count` が完全一致 |
| 1 行以上 | ❌ 不一致。各行が「legacy にある値が `bucket_0` に無い/値が違う」組み合わせ |

`bucket_id = 0` のみ比較する根拠は末尾「設計メモ」を参照。

---

## 6. ダッシュボード確認

- 管理 API でテナント別の統計値が期待通りに見えるか確認
- 既存ダッシュボードに極端な落差が出ていないか確認

ここまで問題なければ移行完了。

---

## 二重実行が必要になったとき（リカバリ）

事前ガードに引っかかった場合、または意図的に再実行が必要な場合 (例: 一部のテナントだけリトライ、移行ロジック修正後のやり直し) は、**移行済み行のみ**をクリアする。

ガードは「過去日付 かつ `bucket_id = 0`」を見るので、消すのも同じ条件に絞る (リアルタイム書込みの `bucket_id ∈ [1, BUCKET_COUNT]` には触らない):

```sql
-- 全テナントの移行行をリセット
DELETE FROM statistics_event_buckets WHERE bucket_id = 0;

-- もしくは特定テナントだけ
DELETE FROM statistics_event_buckets
WHERE tenant_id = '<uuid>' AND bucket_id = 0;
```

その後 `psql -f migrate_data.sql` を再実行。

**注意**: `TRUNCATE TABLE statistics_event_buckets` は `bucket_id ∈ [1, BUCKET_COUNT]` のリアルタイム書込み分まで消してしまう。完全クリーンを意図しない限り上の `DELETE` を使う。

---

## ロールバック

### 移行スクリプトのみリトライしたい場合

上の「二重実行が必要になったとき」を参照。

### PR 全体をロールバックしたい場合 (アプリ含む)

新テーブルを削除して原状回帰。既存 `statistics_events` は無変更なので、旧アプリにロールバックすれば従来通り動作する:

```sql
DROP TABLE statistics_event_buckets;
```

アプリのロールバック時、新コードが書き込んだぶんは新テーブル消滅で失われる (それ以降の新規書込みは旧コードが旧テーブルに行う)。raw `security_event` は無傷なので、必要なら `aggregate_daily_statistics()` で旧テーブルを rebuild できる。

---

## 設計メモ

### bucket_id = 0 の予約

`migrate_data.sql` が挿入する行はすべて `bucket_id = 0` で固定する。これは設計上の予約で、リアルタイム書込み (`StatisticsEventBuckets.pickBucketId`) は `bucket_id ∈ [1, BUCKET_COUNT]` のみを返す。

この分離により:

- **移行された過去分を後から識別可能**: `SELECT count FROM statistics_event_buckets WHERE bucket_id = 0` で、デプロイ前の集計値を論理キーごとに完全に再現できる。
- **旧テーブル DROP 後も値を失わない**: `statistics_events` を将来 DROP しても、移行値そのものは新テーブルの `bucket_id = 0` 行に永続化される。
- **読み出し透過**: 読み出しは全 bucket_id を `SUM(count)` するので、消費者からは合算後の総和しか見えない。

→ 「移行後にリアルタイム書込みが既存の bucket_0 行に加算されて過去分が分からなくなる」問題は発生しない。

### 旧テーブルの扱い

このスクリプト後も `statistics_events` は残る (バックアップ扱い)。
将来の別 PR で:

- バッチ集計関数 `aggregate_daily_statistics()` の出力先を新テーブルに切替
- 必要なら旧テーブルを `DROP TABLE statistics_events;`

DROP しても、移行値そのものは新テーブルの `bucket_id = 0` 行に保持される (上述の予約)。
