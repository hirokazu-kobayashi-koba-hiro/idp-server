# PostgreSQL CPU 消費パターン

## 目的

「**PostgreSQL の CPU はどこで使われるのか**」を、テーブル・index・クエリの設計判断に活かせるよう体系的に学ぶ。

CPU 基礎を理解した上で読むとより深く腹落ちする。

- CPU 基礎が未習なら先に → [21-os-fundamentals/cpu-fundamentals.md](../21-os-fundamentals/cpu-fundamentals.md)
- PostgreSQL の内部構造を先に把握したい → [00-overview.md](00-overview.md)

本ドキュメントは設計レビューや性能改善時の参照リファレンスとしても使える。

関連:
- [dev-03-indexes.md](dev-03-indexes.md): インデックス設計
- [dev-05-query-optimization.md](dev-05-query-optimization.md): クエリ最適化
- [dba-06-maintenance.md](dba-06-maintenance.md): メンテナンス（VACUUM 等）
- [dba-08-planner.md](dba-08-planner.md): プランナー詳細

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
- **Prepared Statement** を使うと Parser / Planner コストが再利用される。JDBC など多くのドライバは PreparedStatement を発行できる仕組みを持つので、動的 SQL 文字列の組み立てを避ければ自動的に活用される
- **複雑な JOIN 多数のクエリ**は Planner の探索空間が指数的に増える。`from_collapse_limit` / `join_collapse_limit` のデフォルト超え（8テーブル）になると計画時間が急増
- **大量パーティション**があると、planner が候補パーティションをチェックするだけで計画時間が支配的になることがある（数十〜数百ミリ秒オーダーになる例も）

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
- 機密データは **アプリ層で暗号化 → DB には暗号文を保存** が原則（DB の CPU を暗号処理に使わせない）
- DB レベル暗号化（TDE 等）はインフラ層で、アプリ層からは透過

### 1.4 ソート / ハッシュ join / 集計

- `ORDER BY` / `GROUP BY` / `DISTINCT` で `work_mem` を超えると **ディスクソート**になり、CPU + I/O 両方
- `HashAggregate` / `HashJoin` も `work_mem` 不足で外部化

**設計時の注意**:
- バッチ集計クエリでは **`work_mem` を一時的に増やす**（セッション単位）
- N行を渡して集計させるより、**アプリ側で N+1 を解消した SQL 1 本**にする方が CPU 効率いい

### 1.5 RLS (Row Level Security) Policy 評価

PostgreSQL の RLS は、行ごとに `USING (...)` ポリシー式を評価する仕組み。マルチテナントなどでテナント ID でフィルタするのに使われる。

典型的な実装パターン（セッション変数経由）:

```sql
-- 各テーブルにポリシーを張る
ALTER TABLE target_table ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation
  ON target_table
  USING (tenant_id = current_setting('app.tenant_id')::uuid);
```

```java
// アプリ側でトランザクション開始ごとにセッション変数を設定
try (var stmt = conn.prepareStatement("SELECT set_config('app.tenant_id', ?, true)")) {
  stmt.setString(1, tenantId);
  stmt.execute();
}
```

**CPU 影響**:
- 各クエリで RLS Policy 式が評価される（行ごと）
- `set_config(..., is_local=true)` は **トランザクション開始ごとに毎回必要**（commit で消える）
- 多数の短いトランザクションがあると `set_config` が累積で計上される

**設計時の注意**:
- read-only な API は **read-only tx** にして set_config 発行頻度を抑制
- 関連する複数操作は **1 tx に統合**することで set_config の回数を削減
- 接続プールで「直前のテナントと同じなら省略」する実装は **テナント越境のリスク**があるため、`is_local=true` を捨てるのは慎重に

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
- 高頻度書き込みテーブルに **PK + 12 個の index**（単独 + 複合 + GIN）を貼っていた例では、INSERT mean が 14.5ms（数 ms 程度が一般的なところを大幅超過）に達した観測例がある。冗長な単独 index と GIN を整理することで mean を半減できる

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
- [ ] **read-only API は read-only tx** にしたか？（書き込みフラグや operation type で制御する仕組みがある場合は活用）
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

## 7. 典型的なアンチパターン事例

設計レビュー・コードレビューで見つけたい、CPU を無駄に食う典型パターン。

### 7.1 RLS 環境で短命トランザクションが大量発生

セッション変数経由の RLS（`current_setting('app.tenant_id')`）を採用しているシステムで、API 1 リクエスト = 1 トランザクションになっていると、**毎 tx で `set_config` が発行される**。

- 高 RPS だと `set_config` の calls が pg_stat_statements の上位に来る
- mean は小さい（数十 μs）が、calls が数百万になると total は無視できない
- 改善方向:
  - read-only API は read-only tx 化（tx 自体を開かない or 軽くする）
  - 関連処理を **1 tx に統合**して set_config 回数を削減

### 7.2 GIN(JSONB) を貼ったが実アプリは `->>` だけ使ってる

JSONB カラムに GIN index を作ったものの、アプリが `WHERE col ->> 'key' = ?` のような **path 演算子**しか使っていない状態。

- GIN は `@>` `?` `?&` `?|` でしか planner に選ばれない
- → GIN は **読み取りで一切活用されない**
- 書き込みのたびに GIN entry 更新コストを払うだけ
- 検出: `pg_stat_user_indexes` で `idx_scan = 0` を確認
- 改善: 不要な GIN は DROP、もし `->>` の検索が遅いなら **式インデックス**（`((col ->> 'key'))`）を検討

### 7.3 フルスナップショット用途の JSONB

書き込みは `JSON.stringify`、読み出しは `JSON.parse` で **行全体をひとかたまり**として扱うカラム。JSON 内部のフィールドを SQL で参照していない。

- DB 側の `jsonb_in` / `jsonb_out` パースコストが純粋な無駄
- → TEXT 型に変更しても機能上の差はなく、CPU を削減できる
- 検出: コード grep で `->`、`->>`、`@>` の使用箇所を確認、該当カラムで未使用なら候補

### 7.4 JSONB カラムが多数のテーブル

1 テーブルに **6〜10 個の JSONB カラム**があり、INSERT/UPDATE のたびにそれぞれをパース。

- 1 行書き込みあたりの mean が 5-10ms に達することがある
- pg_stat_statements で TOP に来やすい
- 改善: 用途分析して TEXT 化できるものは TEXT 化、それでも残るものは payload サイズ削減

### 7.5 単独 index と複合 index の重複

`(col_A)` 単独 index と `(col_A, col_B, ...)` 複合 index が両方ある状態。

- planner は **テーブルサイズが一定以上なら複合を選ぶ**（プレフィックス検索）
- 単独 index は読み取りで使われず、書き込みコストだけ払う
- 検出: `pg_stat_user_indexes.idx_scan` で単独の使用回数を確認
- EXPLAIN ANALYZE で代表クエリのプランを見て、選ばれない単独 index は削除候補

### 7.6 ホット行への集計 UPSERT

統計情報などをリアルタイムに `INSERT ... ON CONFLICT DO UPDATE` で 1 行に集約していて、**同じ行に高頻度で書き込み**が集中するケース。

- 行ロック競合で `LockManager` 待ちが多発
- WAL 大量生成、autovacuum 追いつかず bloat
- 改善:
  - **シャーディッドカウンター**: ホット行を N 個のバケットに分散（`(tenant_id, stat_date, type, bucket_id=hash(...) % N)`）して書き込み、読み取り時に SUM で合算
  - **集計を非同期化**: user-facing path から外し、バックグラウンドで定期集計
  - **時間バケット**: 同一秒・同一分の更新をアプリ層でバッファして 1 回の UPDATE に集約

---

## 8. 参考リンク

### 学習関連

- [00-overview.md](00-overview.md): PostgreSQL 内部構造ガイド
- [dev-03-indexes.md](dev-03-indexes.md): インデックス設計
- [dev-04-transactions.md](dev-04-transactions.md): トランザクション・ロック
- [dev-05-query-optimization.md](dev-05-query-optimization.md): クエリ最適化
- [dba-06-maintenance.md](dba-06-maintenance.md): メンテナンス・autovacuum
- [dba-08-planner.md](dba-08-planner.md): プランナー深掘り
- [dba-10-procarraylock-internals.md](dba-10-procarraylock-internals.md): ProcArrayLock / LWLock
- [../21-os-fundamentals/cpu-fundamentals.md](../21-os-fundamentals/cpu-fundamentals.md): CPU 基礎

### PostgreSQL 公式

- [Performance Tips](https://www.postgresql.org/docs/current/performance-tips.html)
- [JSON Types](https://www.postgresql.org/docs/current/datatype-json.html)
- [GIN Indexes](https://www.postgresql.org/docs/current/gin.html)
- [Routine Vacuuming](https://www.postgresql.org/docs/current/routine-vacuuming.html)

### 性能改善で本ドキュメントを使うとき

- 性能課題に直面したら、まず計測してから対処（仮説先行で実装しない）
- pg_stat_statements の TOP SQL を `mean × calls` で分解して読む
- 待機イベント（`pg_stat_activity.wait_event_type`）で「CPU バウンドか IO 待ちか」を切り分ける
- 本番相当環境で再計測（ローカルだけで結論を出さない）

詳しい調査フローは性能改善プレイブック（運用ドキュメント側）を参照。
