# `statistics_events` → `statistics_event_buckets` 移行 Runbook

Issue #1443 のバケット分散導入にともなう、本番データ移行手順。

## 全体像

- `V0_10_0_1` マイグレーションで新テーブル `statistics_event_buckets` が作成される (CREATE のみ、データは空)
- 新コードはデプロイ後、リアルタイム書込みを **新テーブル** に行う
- 旧テーブル `statistics_events` の歴史データは、デプロイ完了後にこのスクリプトで新テーブルへ移送する

## 前提

- `V0_10_0_1` Flyway マイグレーションが適用済み
- 新コードのデプロイが完了し、**全 Pod が新コードに置き換わっている** こと
- 旧コードを実行する Pod がもう存在しないこと (= 旧テーブルへの新規書込みが止まっている)

## 推奨実行タイミング (SLA)

新コードのローリングデプロイ完了から **30 分以内** に本スクリプトを実行することを推奨。

- 過渡期 (新テーブル単体で歴史データが薄い状態) を最小化するため
- 管理 API の数値が一時的に薄く見えるが、本スクリプト実行で即座に揃う
- 30 分はあくまで目安。データ規模が大きい場合は前倒し、小さい場合は数時間以内でも可

ローリング完了の確認:
```bash
kubectl rollout status deployment/idp-server -n <namespace>
# または
kubectl get pods -l app=idp-server -o jsonpath='{.items[*].spec.containers[*].image}'
# → すべて新イメージタグで揃っていること
```

## 手順

### 1. 事前確認

```bash
psql -h <host> -U idpserver -d idpserver -c "
  SELECT
    (SELECT COUNT(*) FROM statistics_events) AS legacy_rows,
    (SELECT COUNT(*) FROM statistics_event_buckets) AS new_rows;
"
```

`new_rows` はデプロイ後の新Pod書込み分のみ。`legacy_rows` は移行対象。

### 2. データ移行スクリプト実行

```bash
psql -h <host> -U idpserver -d idpserver \
  -f migrate_data.sql
```

このスクリプトは:
- **事前ガード**: 新テーブルに過去日付 (`stat_date < CURRENT_DATE`) の行が既にあれば `RAISE EXCEPTION` で中断 (= 二重実行防止)
- 旧テーブルの全行を `bucket_id = 0` で新テーブルに `INSERT`
- 既存行 (新Pod がリアルタイム書込みで作った行) に当たれば **加算マージ** (`count + EXCLUDED.count`)

INSERT 部分はトランザクションで包まれている。

#### 二重実行ガードに引っかかったとき

意図的に再実行が必要な場合 (例: 一部のテナントだけリトライしたい、移行ロジックを修正してやり直し) は、対象行を先にクリアする:

```sql
-- 全テナントをリセットしてやり直し
TRUNCATE TABLE statistics_event_buckets;

-- もしくは特定テナントだけ
DELETE FROM statistics_event_buckets WHERE tenant_id = '<uuid>';
```

その後 `migrate_data.sql` を再実行。アプリは新テーブルに書き続けているので、削除後の空白期間中もリアルタイム書込み自体は継続する (削除以降の新規イベントは消えない)。

### 3. 検証

```bash
psql -h <host> -U idpserver -d idpserver \
  -f verify_migration.sql
```

以下を検査:

| クエリ | 期待 |
|--------|------|
| 1. 過去日付の整合性 (`legacy.count` vs `SUM(new.count)`) | **0 行** (差分なし) |
| 2. 当日の整合性 (`new` >= `legacy`) | **0 行** (新側が下回らない) |
| 3. サンプル上位レコード | 目視で違和感がないこと |

クエリ 1 で行が返る場合 → 過去日付に差分あり (移行に問題)。
クエリ 2 で行が返る場合 → 当日新側が小さい (新Pod 書込みが反映されてない)。

### 4. ダッシュボード確認

管理 API でテナント別の統計値が期待通りに見えることを確認。

## ロールバック

### 移行スクリプトのみリトライしたい場合

「二重実行ガードに引っかかったとき」の手順を参照。

### PR1 全体をロールバックしたい場合 (アプリ含む)

新テーブルを削除して原状回帰。既存 `statistics_events` は無変更なので、旧アプリにロールバックすれば従来通り動作する:

```sql
DROP TABLE statistics_event_buckets;
```

アプリのロールバック時、新コードが書き込んだぶんは新テーブル消滅で失われる (それ以降の新規書込みは旧コードが旧テーブルに行う)。raw `security_event` は無傷なので、必要なら `aggregate_daily_statistics()` で旧テーブルを rebuild できる。

## 旧テーブルの扱い

このスクリプト後も `statistics_events` は残る (バックアップ扱い)。
将来の別 PR で:
- バッチ集計関数 `aggregate_daily_statistics()` の出力先を新テーブルに切替
- 必要なら旧テーブルを `DROP TABLE statistics_events;`
