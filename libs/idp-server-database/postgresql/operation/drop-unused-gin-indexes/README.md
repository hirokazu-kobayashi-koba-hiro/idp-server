# Drop Unused GIN Indexes on security_event

`security_event.idx_events_detail_jsonb`（GIN on `detail` JSONB）を安全に削除する運用手順。

## 背景

- アプリの SQL は `detail ->> ? = ?` でアクセスしている
- GIN は `->>` 演算子を **一切サポートしない**（`@>`, `?`, `?|`, `?&` のみ）
- → planner はこの index を**選びえない**ため、`idx_scan = 0` のまま肥大化
- Issue #1550（identity_verification_application）と同じ構造的問題

## 効果

| 項目 | 期待 |
|------|------|
| ディスク解放 | GIN は JSONB エントリ展開でサイズ大、大幅解放 |
| 書き込みコスト削減 | 1 INSERT で複数 GIN entry 生成 → 削除で軽量化 |
| WAL 削減 | GIN の WAL は B-tree より重い、レプリ遅延も改善 |
| 検索性能への影響 | **なし**（元から使われていない） |

## 戦略

**「親 partitioned index を DROP するだけ」で全 child index も連動削除**される。

### なぜ「子から削除」ではないのか

partitioned index の子は、**親が生きてる限り個別 DROP できない**:

```sql
DROP INDEX security_event_p20260525_detail_idx;
-- ERROR: cannot drop index ... because index idx_events_detail_jsonb requires it
```

`ALTER INDEX ... DETACH PARTITION` で切り離す手もあるが、これも親への ACCESS EXCLUSIVE が必要 → **直接親を DROP するのと変わらない**。

### よって本手順は親 DROP 一択

```
親 DROP INDEX
   ↓
ACCESS EXCLUSIVE on security_event (短時間)
   ↓
全子 index (95 個程度) を連動削除 (メタデータ更新)
   ↓
ファイル削除は OS の vacuum で漸進的に解放
   ↓
新 partition には GIN 作られない (親定義消えたため)
```

ACCESS EXCLUSIVE は INSERT と非互換だが、`lock_timeout=200ms` + リトライで安全に取りに行く。
**lock 取れなかった場合は即時諦めて INSERT は止まらない**。

## 実行ステップ

1. **00-pre-check.sql**: 状態確認（未使用であること、長時間 tx がいないことを確認）
2. **02-drop-parent-retry.sh**: 親 partitioned index を lock_timeout + リトライで削除
3. **03-verify.sql**: 削除完了の検証
4. **99-rollback.sql**: 緊急時の再作成（基本使わない）

## 前提

- 実行ユーザー: `db_owner`（または superuser）
- PostgreSQL 15+
- pg_partman 4+
- アプリ側は `detail ->> ?` パターン使用中（GIN 未参照）であることを確認済み

## 実行手順

### 1. ローカル検証（推奨）

docker compose のローカル PG で動作確認:

```bash
cd libs/idp-server-database/postgresql/operation/drop-unused-gin-indexes

# 状態確認 (削除前)
cat 00-pre-check.sql | docker exec -i postgres-primary psql -U idp -d idpserver

# 親 index 削除 (リトライ付き)
docker exec postgres-primary bash -c \
  "PGUSER=idp PGDATABASE=idpserver psql -c \"SET lock_timeout='200ms'; DROP INDEX idx_events_detail_jsonb;\""

# 検証
cat 03-verify.sql | docker exec -i postgres-primary psql -U idp -d idpserver
```

### 2. ステージング / 本番投入

低 traffic 時間帯（推奨: 深夜 3:00 AM 等）に以下:

```bash
# 接続情報を export
export PGHOST=<host>
export PGPORT=5432
export PGDATABASE=idpserver
export PGUSER=<db_owner>
export PGPASSWORD=<secret>

# 状態確認
psql -f 00-pre-check.sql

# 親 index 削除 (リトライスクリプト)
chmod +x 02-drop-parent-retry.sh
./02-drop-parent-retry.sh

# 検証
psql -f 03-verify.sql
```

10-30 TPS の本番想定なら 1-5 トライで成功する見込み。

### 3. コード側で GIN 演算子を使っていないことを念のため確認

```bash
grep -rn "detail @>\|detail ?\|detail ?|\|detail ?&" libs/
# → 結果が空であることを確認
```

### 4. DROP 後の ANALYZE 推奨

GIN 削除により planner の cost 推定が変わるため、関連クエリのプランが意図せず変わる可能性がある (cached plan の無効化は自動だが、統計は更新されない)。

```sql
ANALYZE security_event;
```

partitioned table の場合は全 partition の統計が更新される (~数十秒〜数分)。
業務影響なし (read lock のみ、書き込みもブロックしない)。

DROP 後に必ず 1 度実行する。

## 設定変数（02-drop-parent-retry.sh）

| 変数 | デフォルト | 説明 |
|------|----------|------|
| `INDEX_NAME` | `idx_events_detail_jsonb` | 削除対象の index 名 |
| `MAX_ATTEMPTS` | `50` | リトライ回数上限 |
| `LOCK_TIMEOUT` | `200ms` | 1 トライあたりのロック待ち時間 |
| `RETRY_INTERVAL_SEC` | `2` | リトライ間の待ち秒数 |

### TPS 別の推奨設定 (目安)

INSERT 頻度に応じて `LOCK_TIMEOUT` と `MAX_ATTEMPTS` を調整する:

| 想定 TPS | `LOCK_TIMEOUT` | `MAX_ATTEMPTS` | 想定リトライ回数 |
|---------|---------------|---------------|----------------|
| 〜10 TPS (深夜) | `200ms` | `50` | 1-3 |
| 30 TPS (一般低 traffic) | `200ms` | `50` | 1-5 |
| 100 TPS | `300ms` | `100` | 5-20 |
| 200 TPS+ (ピーク時間帯) | **実行非推奨** | - | - |

200ms はかなり短い設定なので、**高 traffic 時間帯だと 50 回連続で外れる可能性**がある。
その場合は `LOCK_TIMEOUT` を上げる (300ms-1s) か、low traffic 時間帯にリトライする。

`lock_timeout` で諦める設計のため **全リトライ失敗してもアプリ無影響** だが、ピーク時間帯にやって 50 回スカ続きだと運用上の意味がない。

## トラブルシューティング

### 親 DROP が `MAX_ATTEMPTS` 超えても取れない

長時間 SELECT / INSERT が常時走っている可能性。`pg_stat_activity` で確認:

```sql
SELECT pid, state, now() - xact_start AS dur, LEFT(query, 100)
FROM pg_stat_activity
WHERE state = 'active' AND query ILIKE '%security_event%';
```

対処:
- `LOCK_TIMEOUT=500ms` 等に伸ばす
- `MAX_ATTEMPTS=200` 等に増やす
- 更に低 traffic な時間帯でリトライ

`lock_timeout` で諦める設計なので、**取れなくてもアプリ無影響**。気長に。

### 削除後に GIN を復活させたくなったら

1. まず本当に必要か再検討（Issue #1550 と同じく、SQL を `@>` に書き換える方が筋）
2. やむを得ない場合は `99-rollback.sql` を実行
3. その際は `jsonb_path_ops` opclass を推奨（サイズ・性能ともに有利）

## ローカル検証実績

ローカル PostgreSQL 15 + pg_partman 4 環境で実施済み:

- 削除前: 親 partitioned GIN 16MB / 子 95 個 / idx_scan=0
- `SET lock_timeout='200ms'; DROP INDEX idx_events_detail_jsonb;` → **1 発成功**
- 削除後: 親 GIN なし、子 GIN 0 個、B-tree 12 個健在、pg_partman 設定無変更

## 他の未使用 GIN index について

本スクリプトは `security_event.idx_events_detail_jsonb` のみを対象とする。

idp-server には他にも GIN index が複数存在し、同じパターン（`->>` でアクセス → GIN 未使用）の可能性がある:

- `idx_authentication_transaction_attributes`
- `idx_verification_application_details`（Issue #1550 で SQL を `@>` に書き換え、こちらは使われている想定）
- `idx_verification_application_attributes`
- `idx_verification_result_*`（GIN 複数）
- `idx_audit_log_attributes`
- `idx_tenant_*_config`（GIN 複数）

各 index ごとに `pg_stat_user_indexes` と実 SQL パターンを照合して同じ手順を適用可能。
ただし**テーブルごとに partition の有無・retention が違う**ので、本スクリプトはそのまま流用せず、テーブル固有の事情に合わせて修正すること。
