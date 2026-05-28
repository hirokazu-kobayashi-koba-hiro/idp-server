# DROP INDEX Benchmark Results

`security_event.idx_events_detail_jsonb` (GIN on `detail` jsonb) を複数スケールで
DROP した実測結果。本番 7000万レコード規模への外挿を目的とする。

## 検証環境

| 項目 | 値 |
|------|------|
| PostgreSQL | 15.18 (Debian 15.18-1.pgdg13+1) |
| 環境 | Docker (postgres-primary) |
| shared_buffers | 1 GB |
| work_mem | 64 MB |
| effective_cache_size | 2 GB |
| ストレージ | Docker volume (overlay2) |
| pg_partman | v4 |
| partition_interval | 1 day |
| premake | 90 days |
| retention | 90 days |
| index 種別 | GIN with `jsonb_path_ops` opclass |

### Partition 構造（全スケール共通）

```
security_event (partitioned table)
├── security_event_default               ← 過去 31 日範囲外データの逃げ場
├── security_event_p20260525             ← 過去 partition (今日より前)
├── security_event_p20260526
├── security_event_p20260527
├── security_event_p20260528             ← 未来 premake (今日 + 90日)
├── security_event_p20260529
...
└── security_event_p20260825             ← 未来 premake 最終日

合計: 95 partition (1 default + 3 past + 91 future)
```

`bulk_insert.sql` は `now() - random() × 31 days` で投入 → 古い日付が範囲外で **default partition に集中**。
`bulk_insert_future.sql` は `now() + random() × 90 days` で投入 → **91 個の future partition に均等分散**。

## 各スケールでの状態

| スケール | 総 rows | total parts | active parts | rows/active part |
|---------|---------|------------:|-------------:|-----------------:|
| **5M / 95 parts** | 5,019,835 | 95 | ~94 | ~53,000 |
| **10M / 95 parts** | 10,019,835 | 95 | ~94 | ~107,000 |
| **20M / 95 parts** | 20,181,785 | 95 | 94 | ~215,000 |
| **23M / 95 parts** | 23,112,985 | 95 | 94 | ~246,000 |
| **20M / 32 active** | 20,306,300 | **125** | **32** | **~625,000** |

各スケールでの GIN サイズ:

| スケール | GIN (clean) | GIN (dirty after bulk INSERT) | compact 率 |
|---------|------------:|-----------------------------:|----------:|
| 5M / 95 parts | 1286 MB | 1433 MB | -10% |
| 10M / 95 parts | 2700 MB | (測定なし) | - |
| 20M / 95 parts | 3477 MB | 4589 MB | -24% |
| 23M / 95 parts | 5542 MB | 5221 MB | +6% (例外) |
| **20M / 32 active** | **3938 MB** | **9298 MB** | **-58%** ★ |

→ 20M / 32 active 実験で **dirty / clean のサイズ差が顕著** (60% 縮小)。
これは partition あたりのデータ量が多い (625K vs ~200K) ことで GIN 内部の fragmentation が蓄積しやすかった可能性。

## DROP 時間計測結果

各スケールで `CREATE INDEX → DROP INDEX` を 5 回繰り返した結果。
**run 1 は bulk INSERT 直後の dirty state、run 2-5 は CREATE INDEX 直後の clean state**。

### 5M (5,019,835 rows)

| Run | GIN size | DROP time | 状態 |
|-----|----------|-----------|------|
| 1 | 1433 MB | **22,171.659 ms** | dirty (post-bulk) |
| 2 | 1286 MB | 214.014 ms | clean |
| 3 | 1286 MB | 175.508 ms | clean |
| 4 | 1286 MB | 273.745 ms | clean |
| 5 | 1286 MB | 212.402 ms | clean |
| **clean 平均** | - | **219 ms** | (stddev 41 ms) |

run 1 だけ突出して 22 秒。これは「**GIN サイズが shared_buffers (1GB) に近い + dirty page 多数**」の最悪条件で発生。

### 10M (10,019,835 rows)

| Run | GIN size | DROP time | 状態 |
|-----|----------|-----------|------|
| 1 | 2700 MB | 266.581 ms | dirty (post-bulk) |
| 2 | 2700 MB | 258.058 ms | clean |
| 3 | 2700 MB | 302.254 ms | clean |
| 4 | 2700 MB | 356.196 ms | clean |
| 5 | 2700 MB | 307.395 ms | clean |
| **全平均** | - | **298 ms** | (stddev 37 ms) |

run 1 も 266 ms と通常レンジ。**22 秒 outlier は再現せず**。
GIN >> shared_buffers なので大半が evict 済み、DROP 時の dirty 量が少ない。

### 20M (20,181,785 rows)

| Run | GIN size | DROP time | 状態 |
|-----|----------|-----------|------|
| 1 | 4589 MB | 205.242 ms | dirty (post-bulk) |
| 2 | 3477 MB | 303.915 ms | clean |
| 3 | 3477 MB | 351.587 ms | clean |
| 4 | 3477 MB | 293.535 ms | clean |
| 5 | 3477 MB | 282.818 ms | clean |
| **clean 平均** | - | **308 ms** | (stddev 30 ms) |

run 1 (dirty) が **205 ms と最速**。clean state より速い理由:
- GIN が shared_buffers の 4 倍以上 → 既に大半 evict 済み
- DROP が触る dirty page が少ない

### 23M (23,112,985 rows) — GIN active で INSERT、途中で kill した実 production 想定状態

| Run | GIN size | DROP time | 状態 |
|-----|----------|-----------|------|
| 1 | 5221 MB | **1,210.211 ms** | dirty (active INSERT 途中で kill) |
| 2 | 5542 MB | 347.462 ms | clean |
| 3 | 5542 MB | 402.947 ms | clean |
| 4 | 5542 MB | 378.729 ms | clean |
| 5 | 5542 MB | 310.820 ms | clean |
| **clean 平均** | - | **360 ms** | (stddev 40 ms) |

run 1 (dirty) が clean の 3 倍以上の **1.2 秒**。20M とは違って、今回は dirty 影響が再現した。

**重要な気づき**: CREATE INDEX 後の clean GIN (5542 MB) は、INSERT で構築された dirty GIN (5221 MB) より **わずかに大きい**。
- 推測: dirty の方は fastupdate の Pending List に未マージのエントリが残っており、main tree のサイズが小さい
- CREATE INDEX は全エントリを main tree に格納するので少し大きい

### 20M / 32 active partitions / 125 total — 本番想定の最終形

`bulk_insert_future31.sql` で「今日 + 未来 30 日」の **32 active partitions** に均等分散投入。
load 中に pg_partman maintenance が走り、total partition は 92→125 に増えた。
本番想定の「retention 31 days + 1 default + 90 future premake = ~123 total」に近い構造。

| Run | GIN size | DROP time | 状態 |
|-----|----------|-----------|------|
| 1 | **9298 MB** | 398.217 ms | dirty (active INSERT 直後の自然な GIN) |
| 2 | 3938 MB | 352.705 ms | clean (CREATE INDEX で full rebuild) |
| 3 | 3938 MB | 362.801 ms | clean |
| 4 | 3938 MB | 370.036 ms | clean |
| 5 | 3938 MB | 335.426 ms | clean |
| **clean 平均** | - | **355 ms** | (stddev 14 ms) |

**★ 驚きの発見: dirty GIN 9.3 GB → clean GIN 3.9 GB (60% 縮小)**

```
dirty (bulk INSERT で構築): 9298 MB
   ↓ DROP → CREATE INDEX (full rebuild)
clean (compact なツリー):    3938 MB

差: -5360 MB (60% 縮小)
```

理由 (推定):
- dirty 側: fastupdate Pending List の未マージエントリ + internal fragmentation
- clean 側: CREATE INDEX は最適構造で full rebuild → compact

→ **本番でも定期的に `REINDEX CONCURRENTLY` で GIN サイズが半減する可能性あり**。
DROP したくない場合のサイズ縮小オプションとして検討余地。

**22 秒 outlier は再現せず**（4スケール中3回目の不発生）。
5M 時の 22 秒は特殊条件下の単発と推定。

**partition 数の影響を比較**:

| 設定 | total parts | active | rows | clean DROP |
|------|----:|----:|----:|----:|
| 95 parts × 20M | 95 | 91+ | 20M | 308 ms |
| **125 parts × 20M** | **125** | **32** | **20M** | **355 ms (+15%)** |

→ active partition 数 (32 vs 91) より、**total partition 数 (catalog cascade) が支配的**。
total +32% → DROP +15%。partition あたりのデータ量 (210K vs 625K) はほぼ影響なし。

## スケーリング分析

### スケール別

```
data scale → clean DROP avg (固定 95 partitions)
─────────────────────────
 5M     → 219 ms
10M     → 298 ms   (+36% に対し scale 2x)
20M     → 308 ms   (+ 3% に対し scale 2x)  ★ 一時的に頭打ち
23M     → 360 ms   (+17% に対し scale 1.15x)  ← また増加
```

| scale × | DROP time × | 解釈 |
|---------|-------------|------|
| 5M → 10M (2x) | 1.36x | データ依存部分が効く |
| 10M → 20M (2x) | 1.03x | catalog/partition 操作が支配的 |
| 20M → 23M (1.15x) | 1.17x | 再び増加 (やや線形) |

→ **完全な頭打ちではなく、`partition 数 + catalog コスト` の底に sub-linear で乗ってくる**。

### partition 数別 (20M 固定)

```
total partition count → clean DROP avg
─────────────────────────
 95 parts (本番想定より少なめ) → 308 ms
125 parts (本番想定に近い)     → 355 ms   (+15%, partition +32%)
```

→ **active partition 数より、total partition 数 (cascade delete 対象) が DROP 時間を決める**。
本番 ~120 partition なら 355 ms 前後と予測できる。

### 結論: DROP 時間を決める 3 要素

| 要素 | 影響度 | 説明 |
|------|--------|------|
| **total partition 数** | ★★★ | catalog 操作 (pg_class, pg_index, pg_inherits) cascade |
| **shared_buffers vs GIN size** | ★★★ | dirty 状態の予測不能性 (5M で 22 秒 outlier 発生) |
| **データ量 (rows)** | ★ | sub-linear、~10% / 2x スケール |
| **active partition 数** | (ほぼ影響なし) | data per partition の違いは catalog op に影響しない |

### dirty state の振る舞い (予測不能)

| Scale | dirty time | 状況 |
|-------|-----------|------|
| 5M | **22,172 ms** | GIN ≒ shared_buffers の最悪条件 (出現は本実験で 1 回のみ) |
| 10M | 267 ms | GIN >> shared_buffers, evict 済み |
| 20M | 205 ms | 同上、安定 |
| 23M | 1,210 ms | INSERT 中断後の dirty 状態、再度遅め |
| **20M / 125 parts** | **398 ms** | bulk INSERT 直後、GIN 9.3 GB、安定 |
| 23M | **1,210 ms** | INSERT 中の状態、再び不安定 |

**dirty DROP は 0.2 - 22 秒のバラつき**。決定要因が一定しない:
- buffer 状態 (どれだけ dirty page が滞留してるか)
- WAL flush の状況
- Pending List の量
- Checkpoint の直近実行有無

→ **本番では DROP 前に `CHECKPOINT` を打って dirty 状態を平準化する**のが安全策。

## INSERT コスト比較 (with vs without GIN)

10M スケールで GIN ありと無しの INSERT 性能を比較:

| パターン | GIN なし | GIN あり | 差 |
|---------|---------|---------|---|
| **100 行/tx (バッチ)** | 33.7 TPS / 237 ms | 34.6 TPS / 231 ms | ~0% |
| **1 行/tx (OLTP)** | 3327 TPS / 2.41 ms | 3193 TPS / 2.51 ms | **-4%** |

→ **GIN による INSERT slowdown は 4% 程度**。期待よりずっと小さい。

原因:
1. `fastupdate=on` (default) で GIN は INSERT 時に Pending List 経由 (O(1))
2. B-tree 12 個の維持コストが支配的、GIN は 1/13
3. multi-row INSERT で fixed cost が分散

→ **GIN 削除のメリットは「書き込み速度」より「ディスク・運用負荷」**。

## CREATE INDEX 所要時間 (重要: rollback コスト)

`CREATE INDEX ... USING GIN (detail jsonb_path_ops)` の所要時間:

| スケール | CREATE INDEX 時間 |
|---------|------------------|
| 5M | ~80 秒 |
| 10M | ~90 秒 |
| 20M | ~157 秒 (2.6 分) |
| 23M | ~180 秒 (3 分) |

→ rollback ( `99-rollback.sql` ) の所要時間として把握。

**本番 7000万への外挿**: ~10-15 分。
DROP は < 1 秒で取り返せないが、CREATE INDEX は **数分の dead time**。

**運用上の含意**:
```
本番投入の流れ:
  T=0       DROP 成功 (<1 sec)
  T=0+ε    アプリ動作確認スタート
  T=5 min   問題なければ確定、なければ rollback 開始
  T=5-20 min  CREATE INDEX 実行中 (GIN なし状態が続く)
  T=20 min  GIN 復活
  
→ DROP したら数分以内に判断、判断遅れたら 10-15 分は GIN なし状態を耐える
```

## 本番 7000万への外挿

```
local 計測 (5 data points):
   5M / 95 parts: 219 ms
  10M / 95 parts: 298 ms (+36%)
  20M / 95 parts: 308 ms (+ 3%)
  23M / 95 parts: 360 ms (+17%)
  20M / 125 parts: 355 ms  ← total partition 数の影響を捕捉

本番想定差:
  rows:           20M → 70M (3.5x)
  total parts:    125 → ~120 (ほぼ同等)
  shared_buffers: 1GB → 16GB (16x)
  GIN サイズ:      9.3 GB → ~15-20 GB
```

### 予測

| 項目 | 予測 |
|------|------|
| **clean DROP** | **350〜500 ms** (partition 数 1.26x で線形に伸びる程度) |
| **dirty DROP (worst case)** | shared_buffers >> GIN なら clean と同程度 |
| **file unlink (background)** | 数秒 (15-20 GB の unlink) |
| **lock 保持時間** | **<1 秒** |
| **INSERT 影響** | 4% 程度 (測定値より) |

### 22 秒 outlier の本番再現可能性

```
22 秒 outlier の発生条件:
  ・GIN サイズ ≒ shared_buffers (近接)
  ・bulk INSERT 直後の dirty page 大量
  ・evict されていない buffer が DROP 中に flush 要求

本番:
  ・GIN ~15 GB << shared_buffers 16 GB ではない、むしろ ~同等
  ・ただし production の write rate なら常時 page eviction で dirty 量が緩和
  ・autovacuum も走るので fastupdate Pending List も定期 flush
  → ★ 再現確率は低いが、ゼロではない ★
```

**対策**:
1. DROP 前に `CHECKPOINT` (dirty buffer を事前 flush)
2. autovacuum を一時停止せず、待つ
3. `lock_timeout=2s` で abort retry

## 副次発見: GIN の compact 効果

20M / 32 active 実験で見えた現象:

```
dirty GIN (bulk INSERT で構築): 9298 MB
   ↓ DROP → CREATE INDEX (full rebuild)
clean GIN:                       3938 MB

実際のサイズ削減: -5360 MB (60% 縮小)
```

`REINDEX CONCURRENTLY` で同等の compact 効果が得られる可能性 = **削除しなくてもサイズ縮小オプションあり**。

ただし:
- REINDEX 中は新規 GIN を build しながら旧 GIN も維持 → **一時的にディスク使用 2x**
- 本番 GIN 15 GB なら REINDEX 中ピーク 30 GB 必要
- 完了後に旧 index を drop して圧縮確定

→ 急いで disk を解放したいケースには有効。
ただし問題の根本原因 (GIN 未使用) には対処しないので、本来は DROP 推奨。

## cache hit ratio 観察

`pg_statio_user_tables` で cache hit を計測:

| 観点 | local (shared_buffers 1GB) | 本番 (16GB 想定) |
|------|----------------------------|------------------|
| DB 全体 hit ratio | **81.6%** (低い) | 95-99% 期待 |
| Partition 単位 heap | 57-60% (CRITICAL) | 99%+ 期待 |
| GIN index hit | 88-89% (WARN) | 99%+ 期待 |
| hot data 推定 | 9.7 GB | hot data ≤ shared_buffers |

→ **ローカルは shared_buffers 不足で cache miss 多発状態**。
本番 (shared_buffers 16GB) では cache hit が向上するため、DROP 時の buffer 無効化処理も速くなる可能性。

→ **ローカル数値は本番の "悪い側" の見積**。本番は同等以下のペナルティで済む可能性。

監視クエリは [`README.md`](./README.md) に記載。

## 結論

```
本番 7000万 + GIN 削除の現実的見積:

  lock 保持時間:          ~0.5-1 秒 (lock_timeout=2s で安全)
  INSERT への影響:         数% (GIN 由来の slowdown は限定的)
  ディスク解放:           ~15-20 GB (本番想定 GIN サイズ)
                          ※ REINDEX で半減も可能 (代替案)
  CREATE INDEX 復旧時間:   10-15 分 (緊急 rollback 時)

本番投入の安全策:
  1. 事前 CHECKPOINT 推奨
  2. autovacuum 動作中は待つ (pg_stat_activity 監視)
  3. lock_timeout=2s + retry
  4. low-traffic 時間帯選択 (10-30 TPS 想定)
  5. shared_buffers vs hot data の事前確認 (cache hit ratio 95%+ 確認)

期待効果:
  ・disk: 15-20 GB 解放 (永続)
  ・WAL: 削減 (GIN の WAL は B-tree より重い)
  ・vacuum 負荷: 軽減
  ・shared_buffers 効率: 改善 (working set 縮小)
  ・INSERT 速度: 数%改善
```

## 測定環境の限界

ローカル検証の **シミュレートできなかった項目**:

| 項目 | ローカル | 本番 | 影響 |
|------|---------|------|------|
| shared_buffers | 1 GB | 16+ GB | DROP 時の buffer 無効化挙動 |
| ストレージ | Docker overlay2 | NVMe / EBS | file unlink I/O 速度 |
| WAL アーカイブ | なし | streaming replica + S3 | DROP の WAL 伝播コスト |
| 同時負荷 | pgbench のみ | OLTP + 管理画面 + 分析クエリ | lock 競合確率 |

→ **ステージングで本番同等データ量・設定で再計測**するのが理想。
ただし本ローカル検証で「致命的なロック保持はしない」「INSERT 影響は限定的」は確認できた。

## 計測スクリプト

- [01-run-bulk-load.sh](./01-run-bulk-load.sh): 過去日付 bulk load (default partition に集中)
- [bulk_insert_future.sql](./bulk_insert_future.sql): 未来日付 INSERT (91 partition に均等分散)
- [02-measure-drop.sh](./02-measure-drop.sh): DROP 時間計測 (CSV 出力)
- `drop_measure_*.csv`: 各 run の生データ
