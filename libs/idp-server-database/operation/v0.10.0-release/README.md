# v0.10.0 リリース Runbook

v0.10.0 で導入される DB スキーマ変更と性能改善のための運用手順をまとめる。

## 含まれる変更

| Flyway | 内容 | 関連 PR / Issue |
|--------|------|----------------|
| `V0_10_0_1` | `statistics_event_buckets` テーブル作成 (空) | PR #1558 / Issue #1443 |
| `V0_10_0_2` | `identity_verification_application` に `external_application_id` カラム追加 | PR #1561 / Issue #1550 |
| `V0_10_0_3` | 同テーブルに `(tenant_id, external_application_id) UNIQUE INDEX` | PR #1561 |
| `V0_10_0_4` | `idp_user (tenant_id, created_at DESC)` index | Issue #1460 |

`V0_10_0_3` / `V0_10_0_4` は **大量レコードがあるテーブルに対する `CREATE INDEX`** なので、本番では Flyway 適用前に `CONCURRENTLY` で先打ちする (詳細後述)。

---

## リリース全体フロー

```
Phase 1: V0_10_0_1 + V0_10_0_2 のみ Flyway 適用 (スキーマ追加、瞬時)
   ↓
Phase 2: アプリリリース (新コードデプロイ)
   ↓
Phase 3: 全 Pod 入れ替え確認
   ↓
Phase 4: index 作成 (CONCURRENTLY) ← V0_10_0_3 / V0_10_0_4 相当を runbook で実行
   ↓
Phase 5: 残りの Flyway 適用 (V0_10_0_3 / V0_10_0_4 は no-op で通過)
   ↓
Phase 6: データ移行 (PR #1558 / PR #1561 の各 backfill)
   ↓
Phase 7: 検証 (各 verify_migration)
```

---

## Phase 1: V0_10_0_2 までを適用

```bash
# 現状確認
./gradlew flywayInfo
# → V0_10_0_1, V0_10_0_2, V0_10_0_3, V0_10_0_4 が Pending

# target を指定して V0_10_0_2 までで止める
FLYWAY_TARGET=0.10.0.2 ./gradlew flywayMigrate

# 適用結果確認
./gradlew flywayInfo
# → V0_10_0_1, V0_10_0_2: Success / V0_10_0_3, V0_10_0_4: Pending
```

---

## Phase 2-3: アプリリリース + 入れ替え確認

```bash
kubectl apply -f deployment.yaml
kubectl rollout status deployment/idp-server -n <namespace>
kubectl get pods -l app=idp-server -o jsonpath='{.items[*].spec.containers[*].image}'
# → 全 Pod が新イメージタグになっていること
```

---

## Phase 4: index 作成 (CONCURRENTLY)

並列実行可能。順序自由。

### `statistics_event_buckets` 関連は事前作成 index なし

PR #1558 のテーブルは `V0_10_0_1` で `CREATE INDEX` 含めて適用済み (空テーブルなので書き込みブロックなし)。Phase 4 では何もしない。

### `identity_verification_application` UNIQUE INDEX (V0_10_0_3 相当)

```bash
cd libs/idp-server-database/postgresql/operation/identity-verification-external-application-id-backfill
psql -f create_index.sql
# → CREATE UNIQUE INDEX CONCURRENTLY (書き込みブロックなし)
```

### `idp_user` (tenant_id, created_at DESC) index (V0_10_0_4 相当)

```bash
cd libs/idp-server-database/postgresql/operation/idp-user-tenant-created-at-index
psql -f create_index.sql
# → CREATE INDEX CONCURRENTLY (書き込みブロックなし)
```

### 進行状況の監視 (別 session)

```bash
psql -c "
  SELECT phase, blocks_done, blocks_total,
         round(100.0 * blocks_done / NULLIF(blocks_total, 0), 1) AS pct
  FROM pg_stat_progress_create_index;
"
```

200 万行で 5-30 分目安。INVALID 検出時は各 runbook の指示に従う。

---

## Phase 5: 残りの Flyway 適用 (no-op で通過)

```bash
unset FLYWAY_TARGET
./gradlew flywayMigrate
# → V0_10_0_3, V0_10_0_4 が IF NOT EXISTS で no-op
./gradlew flywayInfo
# → 全部 Success
```

---

## Phase 6: データ移行 (backfill)

各 runbook を独立に実行可能。順序自由。

### statistics_events → statistics_event_buckets

```bash
cd libs/idp-server-database/postgresql/operation/statistics-events-bucket-migration
psql -f migrate_data.sql
```

詳細: `statistics-events-bucket-migration/README.md`

### identity_verification_application backfill

```bash
cd libs/idp-server-database/postgresql/operation/identity-verification-external-application-id-backfill
psql -f migrate_data.sql
```

詳細: `identity-verification-external-application-id-backfill/README.md`

### idp_user の backfill

不要 (既存行は変更しないため)。

---

## Phase 7: 検証

```bash
psql -f libs/idp-server-database/postgresql/operation/statistics-events-bucket-migration/verify_migration.sql
psql -f libs/idp-server-database/postgresql/operation/identity-verification-external-application-id-backfill/verify_migration.sql
```

それぞれの runbook で「期待 = 0 行」のクエリが 0 行で返ることを確認。

---

## ロールバック

各 PR の runbook にロールバック手順あり。アプリのロールバックは本リリースで導入された SQL の互換性を踏まえて段階的に判断する。

| 変更 | ロールバック可否 |
|------|---------------|
| V0_10_0_1 (新テーブル) | アプリのロールバックで旧コードに戻れば旧経路に切替、新テーブルは無視される。必要なら DROP TABLE |
| V0_10_0_2 (新カラム) | アプリのロールバックでも DROP COLUMN 不要。旧コードは新カラムを使わない |
| V0_10_0_3 (UNIQUE INDEX) | DROP INDEX CONCURRENTLY で外せる |
| V0_10_0_4 (B-tree index) | 同上 |

---

## MySQL を本番で使う場合

各 runbook の `mysql/operation/.../*.mysql.sql` を psql の代わりに `mysql` クライアントで実行する。手順は同じ。

Phase 4 の CONCURRENTLY は MySQL では `ALGORITHM=INPLACE LOCK=NONE` の online build (`create_index.mysql.sql` に明示済み)。
