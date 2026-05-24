---
name: perf-improvement-playbook
description: 性能改善の現状調査プレイブック。性能課題に直面したとき、仮説先行ではなくデータに基づいて改善対象を絞り込むためのフロー。Phase 1〜4 の調査ステップ、3 レイヤー（アプリ/DB/インフラ）での観察、改善判断軸を提供。
---

# 性能改善 現状調査プレイブック

性能課題に直面したとき、**仮説先行で実装に走る前に**、現状を定量的に把握するためのプレイブック。

参照: [性能改善に向けた取組み (Discussion #1549)](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/discussions/1549)

---

## 目的

- **仮説先行で実装に走らない**：効果がなくて手戻りするのを防ぐ
- **ローカル限界の認識**：ローカル数値だけで結論を出さない
- **改善前ベースラインを残す**：効果検証可能に
- **判断材料はデータ**：勘ではなく実測値で動く

---

## 3 レイヤーで状況を把握する

| レイヤー | 主な観点 | 主な計測手段 |
|---------|---------|------------|
| **アプリ層** | SQL 発行頻度、CPU 消費関数、GC、スレッド競合、JSON 処理 | async-profiler / JFR、ログ、APM |
| **DB 層** | クエリ別 CPU、待機イベント、テーブル/index 状態、bloat、TOAST | pg_stat_statements、pg_stat_activity、AWS Performance Insights |
| **インフラ層** | CPU/メモリ/IO、ネットワーク、コネクション数、TLS | top / vmstat、CloudWatch、Docker stats |

複数レイヤーを並行で見ることで「**DB が原因に見えて実はアプリ側の過剰呼び出し**」のような誤判定を防ぐ。

---

## 4 フェーズで進める

```
[Phase 1: 現状把握] → [Phase 2: 仮説立案] → [Phase 3: 仮説検証] → [Phase 4: ベースライン記録]
```

### Phase 1: 現状把握

**目的**: 負荷時の各レイヤーの状態をスナップし、データを集める。**この時点では結論を出さない**。

#### Step 1-1: 環境の前提を揃える

- 計測対象環境（本番相当 staging 推奨、ローカルは補助）
- `pg_stat_statements` 拡張有効化
- 計測時刻・条件メモ

#### Step 1-2: DB 層スナップ

**CPU 食ってる SQL TOP 20**:

```sql
SELECT pg_stat_statements_reset();
-- ↑実施後、計測期間（30分〜1時間）待つ

SELECT
  substring(query, 1, 120) AS query,
  calls,
  round(total_exec_time::numeric, 0) AS total_ms,
  round(mean_exec_time::numeric, 2) AS mean_ms,
  round((100 * total_exec_time / sum(total_exec_time) OVER ())::numeric, 1) AS pct,
  rows
FROM pg_stat_statements
ORDER BY total_exec_time DESC LIMIT 20;
```

**待機イベント分布**（負荷時にリアルタイム取得）:

```sql
SELECT wait_event_type, wait_event, count(*)
FROM pg_stat_activity
WHERE state = 'active'
GROUP BY 1, 2 ORDER BY 3 DESC;
```

| wait_event_type | 意味 |
|---|---|
| NULL（=待機なし） | CPU 使用中 → **純 CPU バウンド** |
| `IO` | ディスク待ち |
| `Lock` | 行・テーブルロック待ち |
| `LWLock` | DB 内部ロック競合 |
| `Client` | アプリ送受信待ち |

**テーブル / TOAST / index サイズ + dead tuple**:

```sql
SELECT
  c.relname,
  pg_size_pretty(pg_relation_size(c.oid)) AS table_size,
  pg_size_pretty(coalesce(pg_relation_size(t.oid), 0)) AS toast_size,
  pg_size_pretty(pg_indexes_size(c.oid)) AS index_size,
  s.n_live_tup AS live, s.n_dead_tup AS dead,
  CASE WHEN s.n_live_tup > 0
       THEN round(100.0 * s.n_dead_tup / s.n_live_tup, 1)
       ELSE 0 END AS dead_pct,
  s.last_autovacuum, s.last_autoanalyze
FROM pg_class c
LEFT JOIN pg_class t ON c.reltoastrelid = t.oid
LEFT JOIN pg_stat_user_tables s ON s.relid = c.oid
WHERE c.relkind = 'r' AND s.schemaname = 'public'
ORDER BY pg_total_relation_size(c.oid) DESC
LIMIT 30;
```

判定指針:
- `dead_pct > 30%` → bloat 確定、autovacuum 不足
- `toast_size > table_size` → TOAST フェッチが支配的になりやすい

**index 使用状況**:

```sql
SELECT
  schemaname, relname, indexrelname,
  idx_scan, idx_tup_read, idx_tup_fetch,
  pg_size_pretty(pg_relation_size(indexrelid)) AS size
FROM pg_stat_user_indexes
WHERE schemaname = 'public'
ORDER BY pg_relation_size(indexrelid) DESC LIMIT 30;
```

判定指針:
- `idx_scan = 0` → 書き込みコストだけ払っている → DROP 候補
- `idx_size > table_size * 0.5` → 肥大化

#### Step 1-3: アプリ層スナップ

- **CPU プロファイル**: JFR or async-profiler
- **GC ログ**: Young GC 頻度、Full GC pause time
- **HikariCP メトリクス**: active / pending / timeout

#### Step 1-4: インフラ層スナップ

- OS 全体: CPU/メモリ/IO（`top`、`vmstat`、`iostat`）
- AWS の場合: CloudWatch、Performance Insights

---

### Phase 2: 仮説立案

**目的**: Phase 1 のデータから、ボトルネックの**位置とメカニズム**を絞り込む。

#### 観察パターンと典型仮説

**Step 1-2 の TOP 20 SQL は `mean × calls` で分解して見る**。同じ「pct 30%超」でも改善方向は真逆になる。

| 観察源 | 観察 | 想定原因 | 改善方向 |
|--------|------|---------|---------|
| Step 1-2 TOP 20 | `pct >= 30` かつ `mean` が大 | クエリ自体が重い | クエリ最適化（index、JSONB 見直し、planner ヒント） |
| Step 1-2 TOP 20 | `pct >= 30` かつ `calls` が異常に多い | アプリ側の過剰呼び出し（N+1 等） | アプリ側で呼び出し回数削減、キャッシュ導入 |
| Step 1-2 TOP 20 | `pct >= 30` で mean も calls も中程度 | 累積で支配的 | クエリ最適化 + 呼び出し削減を併用 |
| Step 1-2 待機イベント | `wait_event_type IS NULL` の比率 > 60% | 純 CPU バウンド | 暗号 / JSON パース / 計算系を疑う |
| Step 1-2 待機イベント | `IO` 多発 | ディスク待ち | shared_buffers 不足、TOAST フェッチ、index miss |
| Step 1-2 待機イベント | `LWLock` 多発 | 内部競合 | コネクション過多、WAL 書き込み詰まり |
| Step 1-2 待機イベント | `Lock` 多発 | 行ロック競合 | 同一行 UPDATE の集中、設計見直し |
| Step 1-2 テーブル状態 | `dead_pct > 30%` | autovacuum 不足、bloat | autovacuum パラメータ強化、pg_repack |
| Step 1-2 テーブル状態 | `toast_size > table_size` | TOAST 取得コスト | カラム内容削減、TOAST 圧縮 LZ4 化 |
| Step 1-2 index 使用 | `idx_scan = 0` の index | 書き込みコストだけ払っている | 不要 index の DROP |
| Step 1-3 CPU profile | Jackson 系 > 20% | JSON シリアライズ／パースコスト | payload 軽量化、ObjectMapper キャッシュ |
| Step 1-3 CPU profile | JWT / 暗号系 > 20% | 署名・検証コスト | アルゴリズム見直し、キャッシュ |
| Step 1-3 HikariCP | `active` が max 張り付き | プール枯渇 | プール調整、tx スコープ見直し |
| Step 1-3 アプリログ | 同じクエリが 1 リクエスト内で複数回 | N+1 問題 | JOIN 化、バッチ取得 |

---

### Phase 3: 仮説検証

**目的**: Phase 2 で立てた仮説を、限定された範囲の計測で検証する。

#### 検証手段

| 手段 | 用途 |
|---|---|
| `EXPLAIN (ANALYZE, BUFFERS, VERBOSE)` | 個別クエリの実プラン確認 |
| `auto_explain` | 遅いクエリの自動キャプチャ |
| `pgbench` カスタムスクリプト | 特定クエリの純粋計測 |
| index DROP の前後比較 | index の必要性検証 |
| アプリ側ピンポイントプロファイル | 1 リクエストの SQL 発行回数 |

---

### Phase 4: ベースライン記録

**目的**: 改善後の効果を検証するため、現状の数値を**残す**。

#### 残すべき項目

- 性能指標: RPS / p50 / p95 / p99 / エラー率
- DB 状態: pg_stat_statements TOP 20、テーブル状態、待機イベント分布
- 環境情報: DB バージョン・インスタンスサイズ、アプリバージョン、データ量

#### 格納先

- `documentation/docs/content_08_ops/performance/` 配下
- もしくは Discussion #1549 にコメントで追記

---

## アンチパターン

| アンチパターン | なぜダメか | 代わりに |
|--------------|----------|---------|
| 仮説先行で実装に走る | 効果が出ず手戻り | Phase 1 → 2 → 3 を順に踏む |
| ローカルだけで結論を出す | 本番とデータ量・リソース差大 | staging or 本番相当環境で計測 |
| 改善前数値を残さない | 効果検証不能、再発時に再調査 | Phase 4 でベースライン記録 |
| 1 つの指標だけで判断 | 別レイヤーの問題を見落とす | 3 レイヤー並行で見る |
| TPS だけで効果判定 | 個別クエリの改善が見えない | mean_exec_time / p95 も併用 |
| pg_stat_statements を reset せず長期累積を見る | 改善前後の差が薄まる | 計測ごとに reset |
| 「とりあえず JSONB → TEXT」「とりあえず index 追加」 | コスト超過、副作用あり | データに基づいて選ぶ |

---

## ケーススタディ（実例）

### CIBA + FIDO-UAF 負荷試験で見えた「14.5ms INSERT」

idp-server staging で、`security_event` INSERT mean が **14.5ms**（通常の 10-100 倍）と観測された。

**内訳推定**:

| 要因 | 推定コスト |
|---|---:|
| GIN(detail) 更新（JSONB の全 path × 値を index に追加） | 2-5 ms |
| B-tree index 11 個の更新（PK + 10個） | 3-6 ms |
| WAL 書き込み + fsync | 1-2 ms |
| TOAST + tx 境界 + planner | 残り |

**原因**:
- アプリは `detail ->> 'key'` を使っているが、**GIN は `@>` でしか動作しない** → GIN は書き込みコストだけ
- 単独 B-tree 5 個が複合 `(tenant_id, X, created_at)` にカバーされ冗長

**改善**:
- GIN + 単独 5 個削除で **理論上 INSERT mean ▼60〜70%**
- ローカル変動の中では効果見えにくいが、本番 1日 300万件規模では巨大効果

### 「v2 TEXT 化」で +60% 改善（idp-server perf/all-improvements）

`authentication_transaction` / `authentication_interactions` の v2 TEXT 化 + 改善コミット 9 件のフル統合で、ローカル計測で：

| 計測 | RPS | p95 | med |
|---|---:|---:|---:|
| main（改善前） | 472.8 | 656 ms | 204 ms |
| アプリ層改善 9 commits | 531.6 | 624 ms | 193 ms |
| + v2 TEXT 化 | **755.5** | **407 ms** | **122 ms** |

→ **改善幅の大半は v2 TEXT 化が稼いだ**（アプリ層 +12% / DB 構造 +42%）。

詳細: `/dev-database` の「v2 新規テーブル swap パターン」参照。

---

## このプレイブックの使い方

1. 性能課題が発生 → このプレイブックを参照しつつ、別 Issue or Discussion で具体的調査を起票
2. Phase 1〜4 を順に実施、結果を Issue/Discussion にコメントで残す
3. 改善実装後、Phase 4 のベースラインと比較
4. このプレイブック自体に学び（新しい観察パターン、新しい計測手段）を追記してアップデート

---

## 関連リソース

| リソース | 内容 |
|---|---|
| Discussion #1549 | 性能改善に向けた取組み（プレイブック原本）|
| `/test-performance` | k6 計測の実行手順・作法 |
| `/dev-database` | JSONB/TEXT 判断、index 設計、v2 swap パターン |
| `/dev-architecture` | zero-downtime な型変更戦略 |
| `documentation/docs/content_08_ops/performance/08-postgresql-cpu-patterns.md` | **PostgreSQL CPU 消費パターン（設計者向け）** |
| `documentation/docs/content_08_ops/performance/` | 性能検証ドキュメント一式 |
