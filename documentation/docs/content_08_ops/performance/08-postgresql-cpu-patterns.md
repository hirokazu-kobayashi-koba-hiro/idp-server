# PostgreSQL CPU 消費パターン（設計者向けリファレンス）

## 目的

DB の物理リソースで先に枯渇するのは多くの場合 **CPU**。
本ドキュメントは、idp-server の設計・実装時に「**この設計は将来 DB CPU を食いそう**」を早期に判断するためのリファレンス。

- 一般論は PostgreSQL 公式ドキュメントの記述に基づく
- idp-server 固有の実例は実コード・migration ファイルに基づく
- 設計レビューでも参照することで「予防的な性能設計」を促す

関連:
- `/dev-database` （DB アダプタの実装ガイド）
- `/perf-improvement-playbook`（性能改善時の調査フロー）
- `/test-performance`（計測手法）

---

## 1. クエリ実行で CPU を食う要素

### 1.1 Parser / Planner / Executor

PostgreSQL のクエリ処理は以下の流れ。それぞれ CPU を使う。

```
[SQL文字列] → Parser → Planner → Executor → [結果]
              構文解析   実行計画   実際の処理
```

| ステージ | 主な CPU 消費 |
|---|---|
| Parser | SQL 文字列の構文解析、AST 構築 |
| Planner | 統計情報を使ってコスト計算、複数候補プランを比較 |
| Executor | 計画に沿ってデータを読み・書き、関数評価 |

**設計時の注意**:
- **Prepared Statement** を使うと Parser / Planner コストが再利用される。idp-server は JDBC + PreparedStatement で発行しているため、 動的 SQL 文字列の組み立てを避ければ自動的に活用される
- **複雑な JOIN 多数のクエリ**は Planner の探索空間が指数的に増える。`from_collapse_limit` / `join_collapse_limit` のデフォルト超え（8テーブル）になると計画時間が急増
- 大量パーティションがあると **計画時間が支配的になる**ことがある（idp-server の `security_event` の Planning Time が ~30-80ms と実測）

### 1.2 JSONB のパース/出力 (`jsonb_in` / `jsonb_out`)

公式ドキュメント:

> "the `jsonb` data is stored in a decomposed binary format that makes it slightly slower to input due to added conversion overhead, but significantly faster to process"

つまり:
- **INSERT 時の `jsonb_in`**: テキスト → 内部バイナリへの変換コスト（CPU）
- **SELECT 時の `jsonb_out`**: バイナリ → テキストへの再構築（クライアント送信時）
- **検索演算子**: `@>` / `?` / `?&` / `?|` は GIN を使えるが、`->>` は GIN を使わない

**設計時の注意**:
- 大きい JSONB（数 KB 以上）は **INSERT/UPDATE のたびにパースコスト**を払う
- JSONB の中身を **検索しないなら TEXT で十分** → JSON.parse はアプリ層で
- **GIN(jsonb) は `@>` 演算子用**。アプリが `->>` で検索しているなら GIN は完全に無駄
- `jsonb_path_ops` は `jsonb_ops` より index が小さく・速いが、`@>` 専用

詳細: `/dev-database` の「JSONB vs TEXT 設計判断」

### 1.3 暗号 / TLS

- **接続 TLS 終端**: DB が TLS で接続を受ける場合、暗号化/復号で CPU 消費
- **`pgcrypto`** や式での暗号化: 列の暗号化を DB で行うと、毎クエリで暗号 CPU が走る

**設計時の注意**:
- アプリ層で暗号化 → DB に暗号文を保存（idp-server の `encrypted_access_token` パターン）が原則
- DB レベル暗号化（TDE 等）はインフラ層で、アプリ層からは透過

### 1.4 ソート / ハッシュ join / 集計

- `ORDER BY` / `GROUP BY` / `DISTINCT` で `work_mem` を超えると **ディスクソート**になり、CPU + I/O 両方
- `HashAggregate` / `HashJoin` も `work_mem` 不足で外部化

**設計時の注意**:
- バッチ集計クエリでは **`work_mem` を一時的に増やす**（セッション単位）
- N行を渡して集計させるより、**アプリ側で N+1 を解消した SQL 1 本**にする方が CPU 効率いい

### 1.5 RLS (Row Level Security) Policy 評価

idp-server は **PostgreSQL の RLS でテナント分離**を実装している。

`libs/idp-server-platform/src/main/java/org/idp/server/platform/datasource/TransactionManager.java`:

```java
try (var stmt = conn.prepareStatement("SELECT set_config('app.tenant_id', ?, true)")) {
  stmt.setString(1, tenantIdentifier.value());
  stmt.execute();
}
```

各テーブルに `USING (tenant_id = current_setting('app.tenant_id')::uuid)` のポリシーを張る。

**CPU 影響**:
- 各クエリで RLS Policy 式が評価される（行ごと）
- `set_config('app.tenant_id', ?, true)` が **トランザクション開始ごとに 1 回発行**される
  - これが pg_stat_statements で高頻度 calls として現れる
  - 多数の短いトランザクションがあると累積で計上される

**設計時の注意**:
- read-only API は **read-only tx** 化でこの set_config の頻度を抑制可能
- 複数の管理 API 操作を **1 tx に統合**することでも削減できる

---

## 2. 書き込み系の CPU 消費

### 2.1 INSERT / UPDATE / DELETE の共通オーバーヘッド

1 行の書き込みでも、以下が積み重なる:

| 要素 | 内容 |
|---|---|
| Parse / Plan | クエリの解釈・計画 |
| 行作成 | カラムごとの型変換（JSONB なら `jsonb_in`）|
| **全 index の更新** | テーブルに index が N 個あれば N 回の index ページ更新 |
| WAL 書き込み | 全変更を WAL に追記 |
| 必要に応じて TOAST | 大きい列を別ストレージに |
| トリガー実行 | あれば |
| RLS / 制約評価 | 整合性チェック |

**設計時の注意**:
- index が増えるたびに INSERT/UPDATE は線形に重くなる
- idp-server の `security_event` には **当初 13 個の index**（PK + 12 個）があり、INSERT mean 14.5ms（staging 実測）。冗長な単独 5 個と GIN を削除して 7 個に減らした（migration `V0_10_3__drop_security_event_redundant_indexes.sql`）

### 2.2 Index の更新コスト（B-tree / GIN / GiST）

| Index 種類 | 書き込み時のコスト | 検索能力 |
|---|---|---|
| **B-tree** | 中（log N の挿入）| =, <, >, BETWEEN, LIKE 'prefix%' |
| **Hash** | 小 | = のみ |
| **GIN** | **大**（複数 entry 挿入、posting list 更新） | 配列・JSONB の含包、全文検索 |
| **GiST** | 大 | 範囲検索、地理空間 |
| **BRIN** | 小 | 範囲（大きいテーブル向け） |

**GIN の特徴**:
- 1 INSERT で「key × 値」分の entry を index に追加する
- 「pending list」に貯めて遅延マージするオプション（`fastupdate=on`、デフォルト）
- → 普段は INSERT 軽いが、pending list が満杯になると **一気にマージで遅い瞬間**が来る
- `jsonb_path_ops` で entry 数を減らせる

**設計時の注意**:
- 高頻度 INSERT テーブルに **GIN を作るかは慎重に**
- アプリの検索クエリが GIN を使う演算子（`@>` 等）か確認してから作る
- 単独カラム index と複合 index の **プレフィックス重複** に注意（複合があれば単独は冗長）

### 2.3 MVCC と HOT update

- PostgreSQL の UPDATE は **新しい行バージョンを別の場所に書く**（MVCC）
- 旧 row は dead tuple として残る → autovacuum で回収
- **HOT (Heap-Only Tuple) update**: index 対象カラムを変更しない UPDATE は index 更新不要 = 軽い

**設計時の注意**:
- 同じ行を高頻度 UPDATE するテーブルは **dead tuple が積もりやすい**
- fillfactor を下げる（例: `fillfactor=80`）と HOT update が成立しやすくなる
- 大きい JSONB を UPDATE すると **TOAST 側も MVCC** で旧版が dead に

### 2.4 WAL 生成

- 全変更が WAL に追記される。書き込み量に比例
- `wal_compression = on` だと CPU で圧縮（容量↓、CPU↑）
- JSONB の大量 UPDATE は WAL も大量

**設計時の注意**:
- payload を最小化（不要なフィールド削減）すれば WAL も減る
- バッチ INSERT （`INSERT ... VALUES (...), (...), ...`）は WAL/commit を分散できる

---

## 3. メンテナンス系の CPU 消費

### 3.1 autovacuum / autoanalyze

- dead tuple を回収（VACUUM）、統計情報を更新（ANALYZE）
- 高更新テーブルでは頻繁に走る
- 走行中は **対象テーブルの読み込み + 統計計算で CPU 使用**

**設計時の注意**:
- 高更新テーブルは `autovacuum_vacuum_scale_factor` を小さく設定（例: 0.05）
- 大きいテーブルでは ANALYZE の sample size を上げる必要あり
- TOAST テーブルも個別に vacuum 対象

### 3.2 checkpoint / WAL writer

- checkpoint: dirty page をディスクへフラッシュ
- WAL writer: WAL バッファをディスクへ
- 高 TPS だと両プロセスが CPU 食う

**設計時の注意**:
- `checkpoint_timeout` / `max_wal_size` のチューニングはインフラ層の責務
- アプリ側では「**不要な UPDATE を発行しない**」が一番効く

---

## 4. 接続管理

### 4.1 接続確立コスト

- PostgreSQL は **1 接続 = 1 プロセス**（fork）
- 認証 + 接続初期化で数十 ms オーダー
- → **接続プール必須**（HikariCP 等）

### 4.2 接続数と context switch

- 接続数が CPU コア数を大幅に超えると **context switch 地獄**
- 経験則: `max_connections = vCPU × 2〜4` 程度が上限ライン
- 超える場合は **PgBouncer / RDS Proxy** で集約

**設計時の注意**:
- アプリ Pod 数 × HikariCP `maximumPoolSize` が **DB 側の vCPU × 4 を超えないか**事前計算
- 例: 3 Pod × pool 50 = 150 接続 → DB 4 vCPU だと厳しい

### 4.3 Prepared Statement のキャッシュ

- PgJDBC は `prepareThreshold`（デフォルト 5 回）でサーバ側プリペアに昇格
- サーバ側プリペアになると Parser / Planner コストが再利用される

**設計時の注意**:
- 同じクエリを高頻度発行する API では効果大
- ただし「**プラン固化**」のリスクあり（パラメータ次第で最適プランが変わるケース）

---

## 5. 設計時のチェックリスト

新しいテーブル・カラム・index を追加する前に、以下を自問する：

### テーブル設計
- [ ] **データ寿命**は？（短命なら expire 待ちでテーブル swap が選べる）
- [ ] 1 行あたりの **書き込み頻度**は？
- [ ] **JSONB カラム**は本当に検索が必要か？（不要なら TEXT）
- [ ] **大きい payload** が含まれるか？（TOAST 化されるか）

### Index 設計
- [ ] **読み取りユースケースが具体的に存在する**か？（推測ではなく実装上の参照）
- [ ] **複合 index にプレフィックスとしてカバー**されないか？
- [ ] **GIN/GiST 等の重い index** は本当に必要か？
- [ ] **書き込みコスト増加を許容**できるか？

### クエリ設計
- [ ] **N+1 になっていない**か？
- [ ] **長期トランザクション**を作らないか？（lock 持ち続けで他を阻害）
- [ ] **集計はリアルタイム or バッチ**？（リアルタイムは CPU 重い）
- [ ] **SELECT で `*` 使わない**か？（不要列で I/O 増、TOAST 取得増）

### 接続・トランザクション
- [ ] **read-only API は read-only tx** にしたか？（idp-server では `OperationContext` で制御）
- [ ] **複数の関連 API を 1 tx に統合**できないか？
- [ ] **prepared statement** が効く形になっているか？

---

## 6. 計測・診断（クイックリファレンス）

| 目的 | クエリ |
|---|---|
| TOP SQL（累積実行時間順）| `SELECT ... FROM pg_stat_statements ORDER BY total_exec_time DESC LIMIT 20` |
| 1 回が遅いクエリ | `... ORDER BY mean_exec_time DESC ...` |
| 待機イベント分布 | `SELECT wait_event_type, wait_event, count(*) FROM pg_stat_activity WHERE state='active' GROUP BY 1,2` |
| dead tuple / bloat | `pg_stat_user_tables.n_dead_tup` |
| index 使用状況 | `pg_stat_user_indexes.idx_scan` |
| EXPLAIN | `EXPLAIN (ANALYZE, BUFFERS, VERBOSE) <query>` |

詳細フロー: `/perf-improvement-playbook`

---

## 7. idp-server での実例（事実ベース）

### 7.1 RLS による set_config の高頻度発行

`TransactionManager.setTenantId()` が **トランザクション開始ごとに `set_config('app.tenant_id', ?, true)` を発行**する。

- staging 観測例: 計測期間中 1.68M calls（mean 41.5μs）
- 大半は短命トランザクションで `is_local=true` のため毎 tx で必要
- 改善方向: read-only tx 化、1 tx での複数 API 統合（部分実施済: `bb3022fa8` refactor）

### 7.2 GIN(security_event.detail) の無駄

- アプリは `WHERE detail ->> 'key' = ?` で検索（query Executor 内）
- GIN は `@>` 演算子でしか使われない → **読み取りで一切活用されない**
- それでも書き込みごとに entry 更新コストを払う
- → `V0_10_3__drop_security_event_redundant_indexes.sql` で削除

### 7.3 authentication_transaction の v2 TEXT 化

- 7 個の JSONB カラム (`tenant_payload`, `client_payload`, `user_payload`, `context`, `authentication_device_payload`, `authentication_policy`, `interactions`)
- アプリは `JSON.parse` / `JSON.stringify` のフルスナップショット用途
- → `V0_10_2__authentication_v2_text_tables.sql` で TEXT 化
- 個別 SQL の mean は **5.59ms → 3.86ms に減少**（リセット後ローカル実測）

### 7.4 oauth_token の 9 JSONB カラム

- `user_payload`, `encrypted_access_token`, `client_payload`, `authentication`, `access_token_custom_claims`, `custom_properties`, `authorization_details`, `consent_claims`, `encrypted_refresh_token`
- ローカル実測で INSERT mean 7.98ms、全体の 19.2% を占有
- → 次の v2 化候補（未実施）

### 7.5 security_event の冗長 index

- 初期定義は PK + 12 個（6 個の単独 B-tree + 5 個の複合 + GIN(detail)）
- EXPLAIN ANALYZE で確認: 単独 5 個は対応する複合 `(tenant_id, X, created_at DESC)` でカバーされ planner は複合を選ぶ
- → 6 個削除（GIN + 単独 5 個）、`idx_events_created_at` のみ統計集計用に維持

### 7.6 statistics_event_buckets の支配性

- ローカル CIBA フロー計測で `INSERT statistics_event_buckets` が **総実行時間の 34.8% を占める**
- シャーディッドカウンター方式（PR #1541、`statistics_event_buckets`）でロック競合は解消したが、CPU 消費は依然 TOP
- → user-facing path から非同期化することで RPS への影響を軽減できる可能性

---

## 8. 参考リンク

- PostgreSQL 公式: [Performance Tips](https://www.postgresql.org/docs/current/performance-tips.html)
- PostgreSQL 公式: [JSON Types](https://www.postgresql.org/docs/current/datatype-json.html)
- PostgreSQL 公式: [GIN Indexes](https://www.postgresql.org/docs/current/gin.html)
- PostgreSQL 公式: [Routine Vacuuming](https://www.postgresql.org/docs/current/routine-vacuuming.html)
- Discussion #1549: [性能改善に向けた取組み](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/discussions/1549)
- 関連 migration: `V0_10_2`, `V0_10_3`, `V0_10_4`
- 関連スキル: `/dev-database`、`/perf-improvement-playbook`、`/test-performance`
