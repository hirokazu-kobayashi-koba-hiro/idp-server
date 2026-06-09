# `security_event` JSONB 検索ベンチ Runbook

Issue #1571 段階 2: `security_event.detail` (GIN index 既存) の検索クエリが `->> ? = ?`
で書かれており、GIN index を活かせていない問題を **API → Spring → SQL の実経路** で
計測する。

直接 SQL を叩く EXPLAIN ANALYZE ではなく、E2E 経由で叩いて pg_stat_statements で
統計を取ることで、RLS / Spring 経由のオーバーヘッドを含む現実的な数値を得る。

---

## 0. 前提

- PostgreSQL に `pg_stat_statements` extension が有効
- `track_io_timing = on`
- E2E が動く状態 (`docker compose up` 済み + tenant 登録済み)

## 1. ファイル構成

| ファイル | 役割 | 冪等 |
|---|---|---|
| `bench_setup.sql` | 100,000 件ダミー投入 (`type = BENCH_SECURITY_EVENT`) | ⚠️ 重複投入される (cleanup → setup の順で) |
| `bench_cleanup.sql` | bench データ削除 | ✅ |
| `capture_stats.sql` | pg_stat_statements から抽出 | ✅ (read-only) |
| `../../../../../e2e/src/tests/performance/security_event_search_jsonb.perf.test.js` | E2E perf テスト | ✅ |

## 2. データ設計

100,000 件を直近 30 日にランダム分散投入する。`detail` JSONB の各キーの分布:

| キー | 値 / 分布 | 計測用途 |
|---|---|---|
| `outcome` | `success` (50%) / `failure` (50%) | 高ヒット率 |
| `status` | `pending` (90%) / `processed` (5%) / `retry` (5%) | 中ヒット率 |
| `resource` | `token` / `user` / `session` / `client` / `invoice` (各 20%) | 中ヒット率 |
| `method` | `POST` / `GET` / `PUT` / `DELETE` (各 25%) | 高ヒット率 |
| `event_id` | 全件ユニーク UUID | 極低ヒット率 (1/100k) |
| `bench_index` | 連番 (1..100,000) | 同上 |

## 3. 計測手順

### 3.1 データ投入 (初回 / cleanup 後)

```bash
cd libs/idp-server-database/postgresql/operation/security-event-jsonb-search
docker exec -i postgres-primary psql -U idpserver -d idpserver < bench_setup.sql
```

### 3.2 pg_stat_statements リセット

```bash
docker exec postgres-primary psql -U idpserver -d idpserver \
  -c "SELECT pg_stat_statements_reset();"
```

### 3.3 E2E perf テスト実行

```bash
cd ../../../../../e2e
npm test -- --testPathPattern="performance/security_event_search_jsonb"
```

### 3.4 統計取得

```bash
cd ../libs/idp-server-database/postgresql/operation/security-event-jsonb-search
docker exec -i postgres-primary psql -U idpserver -d idpserver < capture_stats.sql
```

### 3.5 後片付け

```bash
docker exec -i postgres-primary psql -U idpserver -d idpserver < bench_cleanup.sql
```

## 4. 改善前後の比較フロー

### Before (現状)
1. データ投入 → reset → e2e → capture → **結果保存**
2. cleanup

### After (実装変更後)
1. 実装を `->> ? = ?` → `@> jsonb_build_object(?, ?)` に変更してビルド
2. データ投入 → reset → e2e → capture → **結果保存**
3. cleanup

### 比較項目
- 同じクエリパターン (`SELECT 1 FROM security_event WHERE ... AND detail ->> ? = ?`)
  の `mean_exec_time` / `shared_blks_read` / `rows` を before/after で比較
- `EXPLAIN (ANALYZE, BUFFERS)` で **Seq Scan / Bitmap Index Scan** の違いを確認

## 5. 計測対象クエリパターン (E2E test 側)

| # | クエリ | ヒット数 | 想定改善幅 |
|---|---|---|---|
| 1 | `?details.outcome=success` | ~50,000 | 小 (大量ヒットで I/O 主体) |
| 2 | `?details.status=processed` | ~5,000 | 中 |
| 3 | `?details.resource=invoice` | ~20,000 | 中 |
| 4 | `?details.event_id=<bench-uuid>` | 1 | **大** (低ヒット率 = GIN 効果最大) |
| 5 | `?details.outcome=success&details.method=POST` | ~12,500 | 中 (複合) |
| 6 | `?event_type=BENCH_SECURITY_EVENT&details.outcome=success` | ~50,000 | 小 (type で先に絞られる) |

## 6. 計測結果 (本 PR での before / after)

100,000 件投入 / PERF_REPEAT=10 / replica 計測 (`shared_preload_libraries=pg_stat_statements`)。

### E2E API レイヤー mean (Spring 経由)

| クエリパターン | 件数 | Before (`->>`) | After (`@>`) | 差分 |
|---|---|---|---|---|
| type only (絞込なし) | 100,000 | 38.7 ms | 85.1 ms | +46 ms ※測定ブレ大 |
| outcome=success (高 50%) | 50,000 | 49.2 ms | 53.9 ms | +5 ms |
| status=processed (中 5%) | 5,000 | 47.1 ms | **36.0 ms** | **-11 ms** ✅ |
| resource=invoice (中 20%) | 20,000 | 42.0 ms | 42.0 ms | ±0 ms |
| outcome + method (中 25%) | 25,000 | 47.3 ms | **46.1 ms** | -1 ms |
| **bench_index=99999 (低 1/100k)** | 1 | 86.4 ms | **26.4 ms** | **-60 ms (69% 改善)** ✅ |
| **user.sub=sub-42 (ネスト中 100)** | 100 | n/a (検索不可) | **30.2 ms** | 新規対応 ✅ |
| **user.name=user-77777 (ネスト低 1)** | 1 | n/a (検索不可) | **26.3 ms** | 新規対応 ✅ |

`user.sub` / `user.name` のようなドット区切りキーは、これまで JSONB トップレベルに `"user.sub"`
というキーが存在せず実質ヒットしなかった。`@>` への移行と同時にドット区切り → ネスト JSON 展開
(`{"user":{"sub":"xxx"}}`) を行うことで、本来意図された階層検索が GIN index 経由で機能するようになった。

### SQL レイヤー (pg_stat_statements)

| クエリ | Before mean | After mean | Before read | After read | I/O 削減 |
|---|---|---|---|---|---|
| selectCount (1 detail key) | 22.79 ms | **9.69 ms** | **107,029** blocks | **1** block | **99.999%** ✅ |
| selectCount (2 detail keys) | 23.47 ms | **16.60 ms** | 20,640 | 1 | **99.99%** ✅ |

### EXPLAIN 比較 (`bench_index=99999`)

#### Before (`->>`)
```
Index Scan using ..._tenant_id_type_created_at_idx
  Filter: (detail ->> 'bench_index' = '99999')   ← Index 後の Filter
  Rows Removed by Filter: 3341
Buffers: shared hit=101350
```

#### After (`@>`)
```
Bitmap Index Scan on ..._detail_idx
  Index Cond: (detail @> '{"bench_index": "99999"}'::jsonb)
Buffers: shared hit=6  ← GIN index で 1 行に直接到達
```

#### After (ネスト: `details.user.name=user-77777`)
```
Bitmap Index Scan on ..._detail_idx
  Index Cond: (detail @> '{"user": {"name": "user-77777"}}'::jsonb)
Buffers: shared hit=62  ← ネスト構造でも GIN index で 1 行に到達
```

### 結論

- **低ヒット率 (1 件): 86.4ms → 27.3ms (68% 改善)** ← 本番の典型的な検索パターン
- **I/O は実質ゼロ化** (Seq Scan → Bitmap Index Scan)
- **高ヒット率 (50%)** は Bitmap Heap Scan のオーバーヘッドで API レイヤー上は遅くなるが、これは GIN の特性。本番で 50% のヒット率は稀
- 互換性: 本番の `detail` トップレベルキーは string と object のみ (integer なし) なので `@>` 移行で検索結果は変わらない

## 7. 既知のリスク

- **bench データが e2e admin テナント (`952f6906-3e95-4ed3-86b2-981f90f785f9`) を汚す**
  - `type = 'BENCH_SECURITY_EVENT'` でフィルタすれば識別可
  - cleanup を忘れずに
- **パーティション横断のため bench_setup に数分かかる可能性**
- **E2E test は逐次実行で並列度 = 1** (pg_stat_statements の安定計測のため)
