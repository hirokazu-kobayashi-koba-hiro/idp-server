# `security_event` JSONB 検索ベンチ Runbook

Issue #1571 段階 2: `security_event.detail` (GIN index 既存) の検索クエリが `->> ? = ?`
で書かれており、GIN index を活かせていなかった問題の計測手順。実装は `@>` (PostgreSQL) /
`JSON_CONTAINS` (MySQL) へ移行済み。

計測は **手動** で行う (自動テストには組み込まない)。SQL レイヤー (`EXPLAIN ANALYZE` +
`pg_stat_statements`) を主とし、必要なら API 経由 (curl ループ) も実施する。

---

## 0. 前提

- PostgreSQL に `pg_stat_statements` extension が有効
- `track_io_timing = on`
- API 経由で計測する場合のみ: アプリ起動済み (`docker compose up`) + tenant 登録済み

## 1. ファイル構成

| ファイル | 役割 | 冪等 |
|---|---|---|
| `bench_setup.sql` | 100,000 件ダミー投入 (`type = BENCH_SECURITY_EVENT`) | ⚠️ 重複投入される (cleanup → setup の順で) |
| `bench_cleanup.sql` | bench データ削除 | ✅ |
| `capture_stats.sql` | pg_stat_statements から抽出 | ✅ (read-only) |

## 2. データ設計

100,000 件を直近 30 日にランダム分散投入する。`detail` JSONB の各キーの分布:

| キー | 値 / 分布 | 計測用途 |
|---|---|---|
| `outcome` | `success` (50%) / `failure` (50%) | 高ヒット率 |
| `status` | `pending` (90%) / `processed` (5%) / `retry` (5%) | 中ヒット率 |
| `resource` | `token` / `user` / `session` / `client` / `invoice` (各 20%) | 中ヒット率 |
| `method` | `POST` / `GET` / `PUT` / `DELETE` (各 25%) | 高ヒット率 |
| `bench_index` | 連番 (1..100,000, string 値) | 極低ヒット率 (1/100k) |
| `user.sub` / `user.name` | ネスト (`sub` は約 100 件/値, `name` はユニーク) | ネスト階層検索 |

## 3. 計測手順

### 3.1 データ投入 (初回 / cleanup 後)

```bash
cd libs/idp-server-database/postgresql/operation/security-event-jsonb-search
docker exec -i postgres-primary psql -U idpserver -d idpserver < bench_setup.sql
```

### 3.2 SQL レイヤー計測 (EXPLAIN ANALYZE)

アプリが実行するクエリ形状 (`detail @> ?::jsonb`) を直接叩いて、GIN index が使われるか確認する。
RLS 配下で計測したい場合は対象 tenant のロールに `SET ROLE` してから実行する。

```bash
docker exec -i postgres-primary psql -U idpserver -d idpserver <<'SQL'
-- 低ヒット率 (1/100k): Bitmap Index Scan on ..._detail_idx になれば成功
EXPLAIN (ANALYZE, BUFFERS)
SELECT id FROM security_event
WHERE type = 'BENCH_SECURITY_EVENT'
  AND detail @> '{"bench_index":"99999"}'::jsonb;

-- ネスト (1/100k)
EXPLAIN (ANALYZE, BUFFERS)
SELECT id FROM security_event
WHERE type = 'BENCH_SECURITY_EVENT'
  AND detail @> '{"user":{"name":"user-77777"}}'::jsonb;
SQL
```

旧実装との比較は同じクエリの `detail @> ...` を `detail ->> 'bench_index' = '99999'` に置き換えて
実行し、`Index Scan + Filter (Rows Removed by Filter)` と `shared read` を見比べる。

### 3.3 (任意) API 経由計測 (curl ループ)

Spring / RLS 経由の現実的な数値が欲しい場合。control plane の READ は replica に向くため、
replica 側に `pg_stat_statements` が有効である必要がある (docker-compose 設定済み)。

```bash
# 統計リセット (replica 側)
docker exec postgres-replica psql -U idpserver -d idpserver \
  -c "SELECT pg_stat_statements_reset();"

# トークン取得 → 同一クエリを 10 回叩く (TOKEN / ORG / TENANT は環境に合わせる)
URL="https://api.local.test/v1/management/organizations/$ORG/tenants/$TENANT/security-events"
for i in $(seq 1 10); do
  curl -s -o /dev/null -w "%{time_total}\n" \
    -H "Authorization: Bearer $TOKEN" \
    "$URL?event_type=BENCH_SECURITY_EVENT&details.bench_index=99999&limit=20"
done
```

### 3.4 統計取得

```bash
docker exec -i postgres-replica psql -U idpserver -d idpserver < capture_stats.sql
```

### 3.5 後片付け

```bash
docker exec -i postgres-primary psql -U idpserver -d idpserver < bench_cleanup.sql
```

## 4. 計測結果 (SQL レイヤー / `EXPLAIN ANALYZE`)

ローカル primary / 100,000 件投入 (直近 30 日 = 約 30 パーティションに分散) / `count(*)` 形
(アプリの selectCount 相当) / 各クエリ 3 回実行のウォーム値。全データが shared_buffers に
収まる環境のため buffers はほぼ `hit` (= キャッシュヒット)。

### パターン別 before / after

| パターン | ヒット数 | Before (`->>`) time | After (`@>`) time | Before buffers | After buffers |
|---|---|---|---|---|---|
| **低: bench_index=99999** | 1 | 21.1 ms | **2.96 ms** | 6,565 | **738** |
| **ネスト低: user.name=user-77777** | 1 | 22.2 ms ※0件 (機能不全) | **3.52 ms** | 6,565 | **780** |
| **ネスト中: user.sub=sub-42** | 100 | n/a (機能不全) | **3.75 ms** | — | **948** |
| 高: outcome=success | 50,000 | 27.3 ms | 29.2 ms | 6,565 | 6,775 |

- **低ヒット率**: 実行時間 約 7 倍速 / 触る buffer 約 9 倍減 (6,565 → 738) ✅
- **ネスト検索**: Before は JSONB トップレベルに `"user.sub"` というキーが無いため `->> 'user.sub'`
  が **常に 0 件** (機能的に壊れていた)。After はドット区切りを `{"user":{"sub":"..."}}` に展開し、
  GIN index 経由で正しくヒットする (新規に機能) ✅
- **高ヒット率 (50%)**: ほぼ互角 (After が僅かに遅い)。半数ヒットでは GIN でも heap を広く読むため
  改善は出ない (GIN の特性)。本番で 50% ヒットの検索は稀。

> ウォームキャッシュのため絶対時間差は控えめだが、**恒久的な指標は触る buffer 数** (低ヒット率で
> 約 9 倍減)。コールドキャッシュ / ディスク律速の環境では buffer 削減がそのまま時間差に効く。

### EXPLAIN 比較 (`bench_index=99999`, 1 パーティション分の抜粋)

#### Before (`->>`) — type で取って detail を Filter で捨てる
```
Seq Scan on security_event_p20260608
  Filter: ((type = 'BENCH_SECURITY_EVENT') AND ((detail ->> 'bench_index') = '99999'))
  Rows Removed by Filter: 5644      ← パーティションごとに数千行を捨てる
  Buffers: shared hit=512
```

#### After (`@>`) — GIN detail index に直接ヒット
```
Bitmap Heap Scan on security_event_p20260608
  Recheck Cond: (detail @> '{"bench_index": "99999"}'::jsonb)
  Buffers: shared hit=8             ← パーティションごと 8 page のみ
  ->  Bitmap Index Scan on security_event_p20260608_detail_idx
        Index Cond: (detail @> '{"bench_index": "99999"}'::jsonb)
```

ネスト (`detail @> '{"user":{"name":"user-77777"}}'::jsonb`) も同様に `..._detail_idx` を使う。

## 5. 既知の挙動差 (containment 移行に伴う)

- **値の型一致が厳密になる**: `->>` (テキスト比較) は数値 `5` に `"5"` がヒットしたが、
  `@>` / `JSON_CONTAINS` は JSON 型まで一致が必要 (`{"a":1} @> {"a":"1"}` → false)。
  本番 `detail` の値は現状 string / object のみだが、**イベント発行側は detail の値を
  string か object に揃えること** (数値・boolean を入れると検索が静かに効かなくなる)。
- **配列値の扱いが DB 間で異なる**: 例 `detail = {"scopes": ["openid"]}` に対する
  `?details.scopes=openid` は PostgreSQL (`@>`) では false、MySQL (`JSON_CONTAINS`)
  では true (candidate scalar は target array の要素マッチで contained 扱い)。
  現状 `detail` に配列値の検索ユースケースはないが、追加する場合は要設計。

## 6. 既知のリスク

- **bench データが e2e admin テナント (`952f6906-3e95-4ed3-86b2-981f90f785f9`) を汚す**
  - `type = 'BENCH_SECURITY_EVENT'` でフィルタすれば識別可
  - cleanup を忘れずに
- **パーティション横断のため bench_setup に数分かかる可能性**
- **本番では絶対に bench_setup / cleanup を実行しないこと**
