# インデックスのライフサイクル（本番運用視点）

本番運用中の PostgreSQL でインデックスを操作する場面は、想像以上に多く発生します。
未使用インデックスの削除、肥大化した GIN の再構築、`CREATE INDEX CONCURRENTLY` の途中失敗の片付け、不要 partition の整理…

本ドキュメントは「**すでにデータが入っているテーブルに対して、安全にインデックス操作するための知見**」を、実測データつきでまとめたものです。

> 本ドキュメントの実測値は、idp-server プロジェクトの `libs/idp-server-database/postgresql/operation/drop-unused-gin-indexes/benchmark/RESULTS.md` で取得した数値を基にしています。
> 検証環境: PostgreSQL 15 / shared_buffers 1 GB / partitioned table 95-125 個 / `security_event` テーブル (B-tree 12 個 + GIN 1 個)。

---

## 目次

**基礎編** — 用語と仕組みを揃える

1. [インデックスのライフサイクル](#1-インデックスのライフサイクル)
2. [ロックの基礎](#2-ロックの基礎)

**応用編** — 本番運用での実践

3. [CREATE INDEX](#3-create-index)
4. [DROP INDEX](#4-drop-index)
5. [REINDEX – 削除しない選択肢](#5-reindex--削除しない選択肢)
6. [安全な戦略: lock_timeout + retry パターン](#6-安全な戦略-lock_timeout--retry-パターン)
7. [スケーリング特性 (実測)](#7-スケーリング特性-実測)
8. [DROP 時間の変動要因](#8-drop-時間の変動要因)
9. [周辺ファクター](#9-周辺ファクター)
10. [本番運用チェックリスト](#10-本番運用チェックリスト)

**まとめ編** — 押さえても陥る落とし穴と実例

11. [それでも陥る落とし穴](#11-それでも陥る落とし穴)
12. [ケーススタディ: security_event の未使用 GIN 削除](#12-ケーススタディ-security_event-の未使用-gin-削除)
13. [参考リソース](#13-参考リソース)

---

## 1. インデックスのライフサイクル

インデックスは静的な存在ではなく、テーブル同様に**ライフサイクル**を持ちます。

```
┌─────────────────────────────────────────────────────────────────┐
│                インデックスのライフサイクル                       │
│                                                                 │
│  ┌──────────┐                                                   │
│  │  CREATE  │ ← テーブル作成時 / マイグレーション              │
│  └────┬─────┘                                                   │
│       │                                                         │
│       ▼                                                         │
│  ┌──────────────────────────┐                                   │
│  │   使用 (planner が選択)   │                                   │
│  │   - SELECT で参照される   │                                   │
│  │   - INSERT/UPDATE で維持  │                                   │
│  │   - VACUUM で整理         │                                   │
│  └────┬─────────────────────┘                                   │
│       │                                                         │
│       │ サイズ肥大化 / 断片化 / 統計の劣化                       │
│       │                                                         │
│       ▼                                                         │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐                       │
│  │  REINDEX │  │  DROP    │  │  ALTER   │                       │
│  │ (再構築) │  │ (削除)   │  │ (移行)   │                       │
│  └──────────┘  └──────────┘  └──────────┘                       │
│       │             │             │                             │
│       └─────────────┴─────────────┘                             │
│              本番運用中に発生する操作                            │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 1.1 各操作の特徴

| 操作 | いつ実行する | 主な目的 |
|------|------------|---------|
| `CREATE INDEX` | 設計時、性能改善時 | 新規追加 |
| `CREATE INDEX CONCURRENTLY` | 本番稼働中の追加 | 書き込みを止めずに追加 |
| `REINDEX` | 肥大化・断片化対応 | サイズ縮小、断片化解消 |
| `REINDEX CONCURRENTLY` | 本番稼働中の再構築 | 書き込みを止めずに再構築 |
| `DROP INDEX` | 未使用・不要 | 削除 |
| `DROP INDEX CONCURRENTLY` | 本番稼働中の削除 | 書き込みを止めずに削除 |
| `ALTER INDEX` | リネーム、tablespace 移動 | メタ情報変更 |

### 1.2 ライフサイクルで注意すべき本質

**「テーブルがあるのにインデックスがない状態」は性能上不利**:
- 通常時は `CREATE INDEX` してから運用に投入する
- 本番運用後に追加するなら **`CONCURRENTLY` でロックを最小化**する
- 削除や再構築も同様

**「インデックスのサイズはデータと比例して増えない」**:
- B-tree は概ね線形に増える
- GIN は **データの構造（JSONB のキー数等）**にも依存
- 肥大化したらサイズ縮小手段は **`REINDEX`** または **`DROP` + `CREATE`**

---

## 2. ロックの基礎

CREATE/DROP/REINDEX で発生するロックの理解は必須です。

> ロックの一般論は [dev-04-transactions.md §4 ロック機構](./dev-04-transactions.md#4-ロック機構) を参照。
> 内部の LWLock (ProcArrayLock 等) については [dba-10-procarraylock-internals.md](./dba-10-procarraylock-internals.md) を参照。
> 本ドキュメントでは **インデックス操作で発生するロック** に絞って解説します。

### 2.1 PostgreSQL のテーブルロック階層

PostgreSQL のテーブルレベルロックは **8 段階**あります:

```
弱 ────────────────────────────────────────────────── 強
                                                       
ACCESS  ROW    ROW       SHARE   SHARE   SHARE    EXCLUSIVE  ACCESS
SHARE   SHARE  EXCLUSIVE UPDATE          ROW                 EXCLUSIVE
                          EXCL            EXCL                
  ↑     ↑       ↑          ↑       ↑       ↑         ↑          ↑
SELECT  SELECT  INSERT     VACUUM  CREATE  CREATE   REFRESH    DROP
        FOR     UPDATE     ANALYZE INDEX   TRIGGER  MATVIEW    TABLE
        UPDATE  DELETE     CONC.           一部     CONC.      DROP IDX
                                           ALTER              ALTER
```

#### conflict matrix

主要なロックの互換性:

```
              AS   RS   RE   SUE  SH   SRE  EX   AE
ACCESS SHARE   o    o    o    o    o    o    o   ✗
ROW SHARE      o    o    o    o    o    o    ✗   ✗
ROW EXCLUSIVE  o    o    o    o    ✗    ✗    ✗   ✗
SHARE UPDATE   o    o    o    ✗    ✗    ✗    ✗   ✗
   EXCLUSIVE
SHARE          o    o    ✗    ✗    o    ✗    ✗   ✗
SHARE ROW      o    o    ✗    ✗    ✗    ✗    ✗   ✗
   EXCLUSIVE
EXCLUSIVE      o    ✗    ✗    ✗    ✗    ✗    ✗   ✗
ACCESS         ✗    ✗    ✗    ✗    ✗    ✗    ✗   ✗
   EXCLUSIVE
```

### 2.2 重要なロックモード

#### ACCESS EXCLUSIVE — 最強の排他ロック

- **取る操作**: `DROP TABLE`, `DROP INDEX` (非 CONCURRENTLY), `TRUNCATE`, `VACUUM FULL`, `REINDEX` (非 CONCURRENTLY), `ALTER TABLE` (一部)
- **特徴**: 他の **すべて**のロックと非互換 (`SELECT` すらブロック)
- **本番リスク**: これが取得待ち状態に入ると、後続全クエリが queue で待たされる

#### ROW EXCLUSIVE — INSERT/UPDATE/DELETE が取る

- **取る操作**: `INSERT`, `UPDATE`, `DELETE`
- **特徴**: 自分同士は互換 (複数 INSERT が並行可能)
- **重要**: `ACCESS EXCLUSIVE` と非互換 → DROP/CREATE 系と詰まる

#### SHARE UPDATE EXCLUSIVE — CONCURRENTLY のキー

- **取る操作**: `CREATE INDEX CONCURRENTLY`, `DROP INDEX CONCURRENTLY`, `REINDEX CONCURRENTLY`, `VACUUM` (非 FULL), `ANALYZE`
- **特徴**: `INSERT/UPDATE/DELETE` (ROW EXCLUSIVE) と **互換** ★
- **これが CONCURRENTLY の魔法**: 書き込みを止めずに DDL ができる

### 2.3 ロック取得待ちのメカニズム

```
時系列:
  T=0   SELECT A 実行中 (ACCESS SHARE 取得)
  T=1   DROP INDEX 投入
        → ACCESS EXCLUSIVE 要求
        → A と非互換 → 待機キューに入る
  T=2   SELECT B 到着
        → ACCESS SHARE 要求
        → ★ DROP INDEX が前に並んでる ★ → 後ろに並ばされる
  T=3   INSERT C 到着 → 同様
  T=N   A 完了 → DROP INDEX 即実行 (一瞬で完了)
        → B, C, ... が順次解放
```

**ポイント**: `DROP INDEX` 自体は瞬時でも、**前にいる長時間トランザクションを待つ間、後ろも詰まる**。

これが「`DROP INDEX` は短いがリスクが大きい」と言われる理由です。

---

## 3. CREATE INDEX

### 3.1 通常の CREATE INDEX

```sql
CREATE INDEX idx_users_email ON users (email);
```

**ロック**: `SHARE` (テーブル全体)
- `SELECT` は通す (互換)
- `INSERT/UPDATE/DELETE` は **ブロック** (非互換)
- ★ つまり書き込みが止まる ★

**所要時間**: テーブルサイズに比例
- データを全件スキャンしてインデックスを構築
- 大きいテーブルだと数分〜数十分

**用途**:
- マイグレーション時、初回構築時
- メンテナンスウィンドウが取れるとき

### 3.2 CREATE INDEX CONCURRENTLY

```sql
CREATE INDEX CONCURRENTLY idx_users_email ON users (email);
```

**ロック**: `SHARE UPDATE EXCLUSIVE`
- `INSERT/UPDATE/DELETE` と **互換** ★
- 書き込みを止めない

**所要時間**: 通常 CREATE の **約 2 倍**
- 2 回テーブルをスキャンする (concurrent な変更を取り込むため)
- ただしロック保持時間は短いので**実害は小さい**

**制約**:
- トランザクション内で実行不可 (自動 commit が必要)
- 失敗すると `INVALID` 状態のインデックスが残る → 手動 cleanup 必要
- `EXCLUSIVE` ロックを最終フェーズで一瞬取る (ms 級)
- **partitioned index の親には使えない** (各子に対しては可能)

#### 失敗時のリカバリ

```sql
-- INVALID なインデックスを発見
SELECT indexrelid::regclass, indisvalid, indisready
FROM pg_index
WHERE NOT indisvalid;

-- 削除
DROP INDEX CONCURRENTLY idx_users_email;
-- 再作成
CREATE INDEX CONCURRENTLY idx_users_email ON users (email);
```

### 3.3 partitioned table の CREATE INDEX

```sql
-- partitioned table の親に index を作る
CREATE INDEX idx_events_tenant ON security_event (tenant_id);
   ↓
全 child partition に **同じ index が自動作成**される
親 index は "definition" のみ保持
```

**子だけに作りたい場合**:
```sql
CREATE INDEX ONLY idx_events_tenant ON security_event (tenant_id);
-- ↑ 親のみ定義、子は自分で ATTACH 必要

-- 子ごとに作って ATTACH
CREATE INDEX idx_events_tenant_p20260527
    ON security_event_p20260527 (tenant_id);
ALTER INDEX idx_events_tenant ATTACH PARTITION idx_events_tenant_p20260527;
```

これは **partition ごとに段階的に CONCURRENTLY で作る** ときに使えるテクニック。

### 3.4 CREATE INDEX 所要時間 (実測)

`security_event` (`detail jsonb_path_ops` GIN) で計測:

| データ量 | partition 数 | CREATE INDEX 時間 |
|---------|-------------|------------------|
| 5M | 95 | ~80 秒 |
| 10M | 95 | ~90 秒 |
| 20M | 95 | ~157 秒 (2.6 分) |
| 23M | 95 | ~180 秒 (3 分) |
| 20M / 125 part | 125 | ~3-4 分 |

**特性**:
- データ量に概ね線形
- partition 数は CREATE 時間に大きく影響しない
- GIN は B-tree より遅い

**本番 7000万 への外挿**: 10〜15 分。
`DROP` した後の rollback 時間として記憶すべき数字。

### 3.5 CREATE INDEX を加速する手段

#### maintenance_work_mem

```sql
SET maintenance_work_mem = '2GB';  -- default 64MB
CREATE INDEX idx_events_detail_jsonb ON security_event USING GIN (detail);
```

- index ビルド中のソート/構築バッファ
- **default 64MB は本番には小さすぎる**
- 1-2 GB に上げると **数倍速くなる**ことが多い
- ただし複数の `CREATE INDEX` を並行で打つと N 倍メモリ食うので注意

#### max_parallel_maintenance_workers

```sql
SET max_parallel_maintenance_workers = 4;  -- default 2
CREATE INDEX idx_events_tenant ON security_event (tenant_id);
```

- B-tree index の並列ビルド対応 (PG 11+)
- GIN は **並列ビルド非対応** (PG 17 時点)
- CPU コア数に応じて調整 (CPU 数の半分くらいが目安)

#### work_mem (補助)

- index ビルド時の sort 領域
- maintenance_work_mem の方が直接効くが、関連あり

### 3.6 CREATE INDEX の進捗監視 (PG 12+)

長時間 CREATE INDEX の進捗を確認できる:

```sql
SELECT
    pid,
    datname,
    relid::regclass AS table_name,
    index_relid::regclass AS index_name,
    phase,
    blocks_done,
    blocks_total,
    round(blocks_done::numeric / NULLIF(blocks_total, 0) * 100, 2) AS progress_pct,
    tuples_done,
    tuples_total
FROM pg_stat_progress_create_index;
```

**phases (B-tree)**:
1. `initializing`
2. `waiting for writers before build` (CONCURRENTLY のみ)
3. `building index: scanning table`
4. `building index: sorting tuples`
5. `building index: loading tuples in tree`
6. `waiting for readers before marking dead` (CONCURRENTLY のみ)
7. `waiting for writers before marking ready` (CONCURRENTLY のみ)
8. `waiting for old snapshots`
9. `waiting for readers before drop`

**GIN の場合**: 専用 phase 名 (`scanning table`, `building index`)。

→ 30 分以上かかる CREATE INDEX を打ったときは、これで進捗確認する。

### 3.7 CONCURRENTLY 失敗時のクリーンアップ

`CREATE INDEX CONCURRENTLY` が途中で失敗すると **INVALID** な index が残る:

```sql
-- INVALID 検出
SELECT
    n.nspname AS schema,
    c.relname AS index_name,
    pg_get_indexdef(c.oid) AS def,
    i.indisvalid,
    i.indisready
FROM pg_index i
JOIN pg_class c ON c.oid = i.indexrelid
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE NOT i.indisvalid OR NOT i.indisready;
```

**INVALID な index の特徴**:
- planner には選ばれない (役立たず)
- でも書き込み時には維持される (コストだけ払う)
- ディスクを占有する

→ **発見したら即削除**:
```sql
DROP INDEX CONCURRENTLY idx_xxx;
-- 再作成
CREATE INDEX CONCURRENTLY idx_xxx ON ...;
```

INVALID は **完全に害悪**なので、本番監視に組み込むべき:

```sql
-- 監視クエリ (定期実行)
SELECT count(*) FROM pg_index WHERE NOT indisvalid;
-- 0 でない → アラート
```

### 3.8 CREATE INDEX 中の I/O 増加への配慮

```
CREATE INDEX 中:
  ・テーブル全件 scan
  ・WAL を大量生成
  ・disk write が急増
   ↓
本番 RDS の場合:
  ・burst IOPS を消費 → IOPS credit 枯渇すると性能急落
  ・WAL バックアップ転送量も増える
  ・replica の lag が一時的に拡大
```

→ 大規模テーブルでの CREATE INDEX は **低 traffic 時間帯に実施**。
監視: IOPS, WAL lag, replica lag。

### 3.9 rollback 用の CREATE INDEX 戦略

`DROP INDEX` 後に rollback が必要になった場合、状況に応じた戦略選択が必要。

#### 単純な非 partitioned index の rollback

```sql
CREATE INDEX CONCURRENTLY idx_users_email ON users (email);
```

- 書き込み継続可能 (SHARE UPDATE EXCLUSIVE)
- 通常 CREATE の約 2 倍時間
- → これで OK、選択の余地なし

#### partitioned index の rollback (制約あり)

partitioned table の親に対する `CREATE INDEX CONCURRENTLY` は **不可**:

```sql
CREATE INDEX CONCURRENTLY idx_events_tenant ON security_event (tenant_id);
-- ERROR: cannot create index on partitioned table "security_event" concurrently
```

選択肢:

**選択肢 A**: 通常の `CREATE INDEX` (書き込み停止)

```sql
CREATE INDEX idx_events_tenant ON security_event (tenant_id);
```

- SHARE ロックで **全 INSERT/UPDATE/DELETE が完了まで停止**
- 本番 7000万 GIN なら 10-15 分
- → **運用上 NG** (rollback でさらに障害悪化)

**選択肢 B**: 子 partition ループで CONCURRENTLY + ATTACH (推奨)

```sql
-- Step 1: 親を ONLY で定義 (INVALID 状態、書き込みブロックなし)
CREATE INDEX idx_events_tenant ON ONLY security_event (tenant_id);
```

```bash
# Step 2: 各子 partition で CONCURRENTLY 作って ATTACH (shell から 1 子ずつ実行)
for partition in $(psql -tAc "SELECT inhrelid::regclass FROM pg_inherits WHERE inhparent='security_event'::regclass"); do
  child_idx="${partition}_tenant_idx"
  psql -c "CREATE INDEX CONCURRENTLY ${child_idx} ON ${partition} (tenant_id);"
  psql -c "ALTER INDEX idx_events_tenant ATTACH PARTITION ${child_idx};"
done
```

- 書き込み継続可能
- 約 30-60 分 (95-125 partitions × CONCURRENTLY 個別)
- 全 partition の ATTACH 完了で親 index が自動 valid 化

#### 所要時間の見積もり

| 戦略 | 書き込み影響 | 所要時間 (7000万 GIN) |
|------|------------|---------------------|
| 通常 CREATE INDEX (非 partitioned) | 全停止 | 10-15 分 |
| CONCURRENTLY (非 partitioned) | なし | 20-30 分 |
| **partitioned 親 通常 CREATE** | **全停止** | **10-15 分 (危険)** |
| **partitioned 子ループ CONCURRENTLY** | **なし** | **30-60 分** |

→ **rollback も「速くない」のが現実**。
だから DROP 前の検証 (24-48h `idx_scan=0`) で確証を持つことが、最強の予防策。

---

## 4. DROP INDEX

### 4.1 DROP INDEX の内部動作

```sql
DROP INDEX idx_events_detail_jsonb;
```

内部で何が起きるか:

```
1. ACCESS EXCLUSIVE ロック取得 (テーブル + index)
   ↓
2. カタログ更新
   - pg_class から index 行を削除
   - pg_index から index 行を削除
   - pg_inherits, pg_depend, pg_statistic 等を整理
   ↓
3. relfilenode を「削除予約」にマーク
   ↓
4. buffer cache 内の関連 page を invalidate
   ↓
5. WAL レコード書き込み
   ↓
6. ロック解放
   ↓
(非同期) checkpointer / background writer がファイル unlink
```

**ロック保持時間 = 1-5** (合計 ms〜数百 ms オーダー)

**ファイル unlink** は **6 の後**で非同期に実行されるので、**ロック保持時間には含まれない**。

### 4.2 ロック保持時間に効く要素

| 要素 | 影響 | 解説 |
|------|------|------|
| **partition 数** | ★★★ | カタログ操作が partition × 行数で増える |
| **shared_buffers サイズ** | ★★★ | 大きいほど buffer invalidation の walk が長い |
| **segment ファイル数** | ★★ | 1 GB ごとに 1 segment、unlink マーキング |
| **データ量 (rows)** | ★ | sub-linear (catalog 操作が支配的) |

### 4.3 partitioned index の特殊事情

partitioned index の子は **親が生きてる限り個別 DROP 不可**:

```sql
DROP INDEX security_event_p20260525_detail_idx;
-- ERROR: cannot drop index ... because index idx_events_detail_jsonb requires it
-- HINT: You can drop index idx_events_detail_jsonb instead.
```

`ALTER INDEX ... DETACH PARTITION` 構文は **PostgreSQL では未サポート** (CREATE 時の ATTACH のみ存在)。

→ **親 partitioned index を DROP する** = 全 child index がカスケード削除される、というのが正攻法。

```sql
DROP INDEX idx_events_detail_jsonb;
-- 全 partition の子 index が一緒に消える
-- メタデータ更新 + ファイル削除マーキング
```

### 4.4 DROP INDEX CONCURRENTLY

通常の index には使えるが、**partitioned index の親には不可**:

```sql
DROP INDEX CONCURRENTLY idx_events_detail_jsonb;
-- ERROR: cannot drop partitioned index "idx_events_detail_jsonb" concurrently
-- SQLSTATE: 0A000  (feature_not_supported)
```

`0A000` は **「PG が機能として未実装」** のエラー。将来サポートされる可能性はあるが、PG 17 時点で入っていない。

#### 通常 index に対する DROP INDEX CONCURRENTLY

```sql
DROP INDEX CONCURRENTLY idx_users_email;
```

**ロック**: `SHARE UPDATE EXCLUSIVE`
- `INSERT/UPDATE/DELETE` と互換
- 書き込みを止めない

**フェーズ**:
1. Mark index as `invalid` (planner が以降この index を使わない)
2. 既存トランザクションが index を参照しなくなるまで待機
3. 実際に削除

**制約**:
- トランザクション内不可
- UNIQUE 制約や PRIMARY KEY の裏 index には不可

### 4.5 DROP INDEX 所要時間 (実測)

clean state (`CREATE INDEX` 直後) での DROP INDEX:

| データ量 | total partition 数 | GIN サイズ | DROP avg |
|---------|------------------|-----------|----------|
| 5M | 95 | 1.3 GB | 219 ms |
| 10M | 95 | 2.7 GB | 298 ms |
| 20M | 95 | 3.5 GB | 308 ms |
| 23M | 95 | 5.5 GB | 360 ms |
| **20M / 32 active** | **125** | **3.9 GB** | **355 ms** |

**特性**:
- **partition 数が支配的**: 95 → 125 (+32%) で DROP +15%
- データ量は sub-linear: 5M → 20M (4倍) で DROP +41%
- shared_buffers と GIN サイズの関係でも変動 ([§8 DROP 時間の変動要因](#8-drop-時間の変動要因))

**本番 7000万への外挿**: clean DROP で 350〜500 ms 程度。

---

## 5. REINDEX – 削除しない選択肢

「DROP は怖いがサイズは縮小したい」場合の選択肢。

### 5.1 REINDEX の動作

```sql
REINDEX INDEX idx_events_detail_jsonb;
```

内部で起きること:
1. 新しい index を構築 (元のテーブルから全件再構築)
2. 古い index と置き換え
3. 古い index を削除

**効果**:
- **断片化解消**: 削除済み tuple や Pending List の残骸が消える
- **サイズ縮小**: 実測では GIN が dirty 9.3 GB → clean 3.9 GB と **60% 縮小**

### 5.2 REINDEX と REINDEX CONCURRENTLY の違い

| 操作 | ロック | INSERT 影響 | 所要時間 |
|------|--------|------------|---------|
| `REINDEX INDEX` | `ACCESS EXCLUSIVE` | ★ ブロック | CREATE と同程度 |
| `REINDEX INDEX CONCURRENTLY` | `SHARE UPDATE EXCLUSIVE` | 並行可 | CREATE の 2 倍 |

**本番では基本 `REINDEX CONCURRENTLY` 一択**。

### 5.3 REINDEX のディスク要件

```
REINDEX CONCURRENTLY 中:
  ・新 index を構築中 (旧 index も維持)
  ・★ 一時的にディスク使用量 2x ★

本番 GIN 15 GB の場合:
  ・REINDEX 中ピーク: 30 GB 必要
  ・完了後に旧 index を drop して圧縮確定
```

→ **本番でディスク余裕がない場合は実行不可**。
事前に空き容量を確認し、必要なら dummy WAL アーカイブクリーンアップなどで空けてから。

### 5.4 GIN の compact 効果 (実測)

idp-server の検証で観測した数値:

| 状態 | GIN サイズ |
|------|-----------|
| **dirty** (bulk INSERT で構築) | 9.3 GB |
| **clean** (REINDEX で full rebuild) | 3.9 GB |
| **差分** | **5.4 GB / -58%** |

原因 (推定):
- dirty 側: fastupdate Pending List の未マージエントリ + internal fragmentation
- clean 側: full rebuild で最適構造に再構築

→ **「DROP したくないが disk 解放したい」場合は REINDEX が有効**。
ただし不要 index 本体は残るので、書き込み維持コストは継続する点に注意。

### 5.5 REINDEX vs DROP の判断軸

```
不要 index?
   ↓
YES → DROP INDEX (運用負荷 + サイズ両方解決)
NO ↓

必要だが肥大化してる?
   ↓
YES → REINDEX CONCURRENTLY (サイズ縮小、書き込み継続可能)
NO  → 何もしない
```

---

## 6. 安全な戦略: lock_timeout + retry パターン

partitioned index の親 DROP に CONCURRENTLY が使えない以上、本番投入には **`lock_timeout` + リトライ**が現実解。

### 6.1 基本パターン

```sql
SET lock_timeout = '200ms';
DROP INDEX idx_events_detail_jsonb;
```

**挙動**:
- ロック取得に 200ms かかったら **諦めてエラー終了**
- 後ろに並んでた INSERT/SELECT がすぐ流れる
- 失敗してもアプリ影響なし、リトライ可能

### 6.2 リトライスクリプト例

```bash
#!/usr/bin/env bash
MAX_ATTEMPTS=50
LOCK_TIMEOUT=200ms

for ((i=1; i<=MAX_ATTEMPTS; i++)); do
  OUT=$(psql -v ON_ERROR_STOP=1 \
    -c "SET lock_timeout='${LOCK_TIMEOUT}'; DROP INDEX idx_events_detail_jsonb;" 2>&1)
  if [[ $? -eq 0 ]]; then
    echo "SUCCESS on attempt $i"
    exit 0
  fi
  if echo "$OUT" | grep -q "lock timeout"; then
    echo "[$i] lock timeout (retry)"
  else
    echo "[$i] UNEXPECTED: $OUT"
    exit 1
  fi
  sleep 1
done
```

### 6.3 lock_timeout の値の選び方

| `lock_timeout` | INSERT 影響 | 成功確率 | 用途 |
|---------------|------------|---------|------|
| 100ms | 最小 | 低 (long tx で失敗) | 高 TPS 環境 |
| **200ms** | **小** | **中** | **デフォルト推奨** |
| 1s | 中 | 高 | 中 TPS 環境 |
| 2-5s | 大 | 非常に高 | low traffic 時間帯 |

実測 (10-30 TPS 想定): **`200ms` で 1-5 トライ内に成功**。

### 6.4 CONCURRENTLY との等価性

```
DROP INDEX CONCURRENTLY (通常 index):
  Phase 1: 短いロック (invalid マーク)
  Phase 2: wait (INSERT 並行進行)
  Phase 3: 短いロック (実削除)

lock_timeout + retry (partitioned index):
  Try 1:  200ms 試行 → 取れなければ諦め (INSERT 止めない)
  wait:   INSERT 並行進行
  Try 2:  200ms 試行 → ...
  Try N:  取れた → DROP 完了
```

**挙動は等価** (INSERT を止めない、最終的に成功)。
唯一の差: CONCURRENTLY は「絶対に取れる」、retry は「取れないかも」。

ただし本番運用では、`MAX_ATTEMPTS=50` 程度で実用上必ず成功する。

---

## 7. スケーリング特性 (実測)

idp-server の検証で取得したスケーリング曲線。

### 7.1 データ量 vs DROP 時間

```
clean DROP avg (95 partitions 固定):

scale → DROP time
─────────────────
 5M     → 219 ms
10M     → 298 ms   (+36% / scale 2x)
20M     → 308 ms   (+ 3% / scale 2x)  ← 頭打ち気味
23M     → 360 ms   (+17% / scale 1.15x)
```

→ **データ量に sub-linear**。10M → 20M はほぼ flat、23M でやや増加。

### 7.2 partition 数 vs DROP 時間

```
20M 固定 / partition 数のみ変えて比較:

partitions → DROP time
─────────────────────
 95         → 308 ms
125         → 355 ms   (+15% / partitions +32%)
```

→ **partition 数の影響のほうが支配的**。

### 7.3 DROP 時間を決める 3 要素

| 要素 | 影響度 | 解説 |
|------|--------|------|
| **total partition 数** | ★★★ | カタログ操作 (pg_class, pg_index, pg_inherits) cascade |
| **shared_buffers サイズ** | ★★★ | buffer 無効化 walk が O(N) (PG 13 以前)、PG 14+ で改善 |
| **データ量 (rows)** | ★ | sub-linear (~10% / 2x スケール) |
| **active partition 数** | (ほぼ影響なし) | data per partition の違いは catalog op に影響しない |

---

## 8. DROP 時間の変動要因

DROP INDEX の所要時間は **clean な状態でほぼ一定**ですが、状況によって秒オーダーまでブレることがあります。実測でも 5 回中 1 回 22 秒の outlier を観測しました。

### 8.1 実測データ (dirty state ≒ bulk INSERT 直後)

| Scale | dirty time | clean avg | 状況 |
|-------|-----------|----------:|------|
| 5M | **22,172 ms** | 219 ms | GIN ≒ shared_buffers (1.4 vs 1 GB) の状況、1 回のみ観測 |
| 10M | 267 ms | 298 ms | dirty の方が clean より速いケース |
| 20M | 205 ms | 308 ms | 同上 |
| 23M | 1,210 ms | 360 ms | clean の ~3.4 倍 |
| 20M / 125 parts | 398 ms | 355 ms | clean と同程度 |

→ **5 ケース中 1 ケースで 22 秒の outlier**。残り 4 ケースは 200-1200 ms に収まる。
傾向としては「dirty の方が遅め」と言えるが、**5M ケースの 22 秒だけが突出**しており、再現性は確認できていない。

### 8.2 documented 要因: shared_buffers 線形 walk

`DROP INDEX` (および `DROP TABLE`) は内部で `DropRelFileNodeBuffers()` を呼び出し、
**shared_buffers 全体を線形に walk** して該当 relation の buffer を invalidate する。

```
DropRelFileNodeBuffers の計算量:
  PG 13 以前: O(N)   ※ N = shared_buffers / 8KB
  PG 14 以降: O(1)   ※ 小規模 relation で最適化、大規模では依然線形寄り
```

Cybertec の検証では:
- shared_buffers 512 MB → DROP TABLE (300 shards) **5.4 秒**
- shared_buffers   8 GB → **7.2 秒**
- shared_buffers  16 GB → **12.3 秒**

→ **shared_buffers が大きいほど DROP の buffer 無効化に時間がかかる** のは documented な現象。

### 8.3 22 秒 outlier について (推測)

22 秒の outlier は、上記の linear walk だけでは完全に説明できない (5M で GIN 1.4 GB, shared_buffers 1 GB は中程度のサイズ)。

**可能性のある要因** (どれが該当するかは未確定):

| 要因 | 仕組み |
|------|--------|
| 長時間トランザクションのロック待ち | DROP の ACCESS EXCLUSIVE が他の ACCESS SHARE/EXCLUSIVE と競合 |
| autovacuum との競合 | 該当 partition への autovacuum が SHARE UPDATE EXCLUSIVE 保持中 |
| pg_partman maintenance | 同時に partition 操作が走っていた可能性 |
| WAL fsync の遅延 | I/O 競合や OS-level の遅延 |
| checkpoint の同時発火 | 大量 dirty page flush と DROP の I/O が重なった |

公式 docs や Postgres 関連の信頼できる文献で **「dirty page flush が DROP INDEX を直接遅らせる」** という記述は確認できなかった。
当初の説明 (「ロック保持中に dirty page flush が走る」) は **推測ベース**であり、メカニズム的には根拠が弱い。

→ **`22 秒は再現性が低い outlier`** であり、本番でも稀に起きる可能性はあるが頻発は想定しない。

### 8.4 対策: CHECKPOINT + lock_timeout

メカニズム不明でも対策は明確:

```sql
CHECKPOINT;                          -- dirty buffer を事前 flush (checkpoint 経路の I/O 競合を回避)
SELECT pg_sleep(2);                  -- autovacuum 等の追従待ち
SET lock_timeout = '5s';             -- 万一の長時間 tx 競合で諦め可能に
DROP INDEX idx_events_detail_jsonb;
```

- `CHECKPOINT`: 直前の WAL を flush して、DROP 時の I/O 競合を減らす
- `lock_timeout`: 長時間 tx と競合した場合の最悪ケースを抑える

両方やっておけば 22 秒級の outlier はほぼ回避できる、というのが現状の最善策。

---

## 9. 周辺ファクター

### 9.1 autovacuum との競合

`autovacuum` は `SHARE UPDATE EXCLUSIVE` を取る。
これは `ACCESS EXCLUSIVE` と非互換 → DROP と競合する。

**観察方法**:
```sql
SELECT pid, query_start, LEFT(query, 100)
FROM pg_stat_activity
WHERE query LIKE 'autovacuum:%security_event%';
```

走ってたら:
- 待つ (通常 partition 1個あたり数分〜十数分)
- もしくは `pg_cancel_backend(pid)` で中断 (vacuum は idempotent、安全)

### 9.2 CHECKPOINT との関係

- DROP は WAL を生成、checkpoint で WAL 削除
- DROP 自体は checkpoint をブロックしない
- ただし大量の WAL 生成 → 直後 checkpoint が走ると I/O spike

**監視**:
```sql
SELECT * FROM pg_stat_bgwriter;
-- checkpoints_timed, checkpoints_req, buffers_checkpoint 等
```

### 9.3 streaming replication との関係

DROP INDEX は WAL 経由で replica に伝播:

```
primary で DROP INDEX
   ↓ WAL (数 KB)
replica が apply
   ↓ replica でも ACCESS EXCLUSIVE 短時間
   ↓ replica の read query 並走中なら、その完了待ち
```

**注意点**:
- `max_standby_streaming_delay` の設定次第で replica の read 遅延 or 強制中断
- 長時間 read が走ってる replica で DROP すると、replica で warning ログ

### 9.4 cache hit ratio との関係

`shared_buffers` と hot data の関係で DROP の挙動が変わる:

```sql
-- DB 全体 hit ratio
SELECT round(blks_hit::numeric / NULLIF(blks_hit + blks_read, 0) * 100, 2) AS hit_pct
FROM pg_stat_database WHERE datname = 'idpserver';

-- パーティション単位 hit
SELECT relname,
       round(heap_blks_hit::numeric / NULLIF(heap_blks_hit + heap_blks_read, 0) * 100, 2) AS heap_hit_pct
FROM pg_statio_user_tables
WHERE heap_blks_read + heap_blks_hit > 1000
ORDER BY heap_blks_read DESC LIMIT 10;
```

**目安**:
- 99%+ : excellent
- 95-99%: good
- 90-95%: shared_buffers 増強検討
- < 90% : 明らかに不足

ローカル検証で 81% を観測 → shared_buffers 1 GB が hot data (9.7 GB) に対して不足。
本番 16 GB なら 99%+ 期待できる → DROP も buffer 無効化が速く完了。

---

## 10. 本番運用チェックリスト

### 10.1 DROP INDEX 投入前

```
□ pg_stat_user_indexes で idx_scan = 0 を 24-48h 観察
□ コード grep で index が暗黙に使われてないこと確認 (例: detail @> '...')
□ pg_stat_activity で長時間トランザクションがないこと確認
□ pg_stat_activity で autovacuum が当該テーブルで走ってないこと確認
□ shared_buffers vs index サイズの関係を把握
□ replica の状態 (replication lag, max_standby_streaming_delay)
□ disk 空き容量 (rollback で CREATE INDEX するなら 2x 必要)
□ low traffic 時間帯選定 (深夜帯推奨)
```

### 10.2 DROP INDEX 投入時

```
□ CHECKPOINT 実行 (dirty buffer を事前 flush)
□ lock_timeout = '200ms' (or 2s) 設定
□ DROP INDEX + リトライスクリプト
□ DROP 直後の pg_stat_activity 監視 (lock 待ち発生してないか)
□ disk usage が階段状に減ることを確認 (background unlink)
□ replica lag を監視 (1-3 秒以内で正常)
```

### 10.3 DROP INDEX 投入後

```
□ アプリ動作確認 (主要 API のレスポンス)
□ error rate 監視 (5xx の急増がないか)
□ pg_partman maintenance が継続動作するか (新 partition に index 作られないこと確認)
□ DB cache hit ratio 確認 (改善方向に動いてるか)
□ 数日後、本当に問題なければ runbook をマージ確定
```

### 10.4 緊急時 (やっぱり必要だった)

```
□ CREATE INDEX 実行を準備 (CONCURRENTLY 推奨)
□ rollback 中も検索が遅くなる前提 (lock_timeout で SELECT が長くなる可能性)
□ 本番 7000万 → ~10-15 分の dead time を覚悟
□ rollback 完了まで他の DDL は打たない
```

---

## 11. それでも陥る落とし穴

基礎・応用・チェックリストを押さえても踏みやすい罠。各項目は要約のみで、詳細は本文の該当セクションを参照。

### DROP 系

#### 落とし穴 1: 「DROP INDEX は一瞬」

`DROP INDEX` 自体は数 ms だが、`ACCESS EXCLUSIVE` 取得待ちで queue が詰まると **後続クエリ全部が止まる**。
→ [§6 安全な戦略](#6-安全な戦略-lock_timeout--retry-パターン)

#### 落とし穴 2: 「CONCURRENTLY なら安全」

`DROP INDEX CONCURRENTLY` は **partitioned index の親には不可** (SQLSTATE 0A000)。
子は親が生きてる限り個別 DROP できない。
→ [§4.3 partitioned index の特殊事情](#43-partitioned-index-の特殊事情)

#### 落とし穴 3: 「DROP したら disk が即解放される」

ファイル削除は **背景 (background unlink)**。ロック解放後 数秒〜数十秒の I/O が続く。
→ 監視: DROP 直後の iowait spike を許容する設計

#### 落とし穴 4: 「DROP 時間は常に一定」

`shared_buffers` の線形 walk (PG 13 以前) や autovacuum・長時間 tx との競合で、**秒オーダーまでブレる**ことがある (ローカル 5M で 22 秒 outlier 1 回観測、メカニズム未確定)。
→ [§8 DROP 時間の変動要因](#8-drop-時間の変動要因)、対策は事前 `CHECKPOINT` + `lock_timeout`

#### 落とし穴 5: 「rollback はすぐできる」

CREATE INDEX は GIN なら 10-15 分、partitioned 子ループで 30-60 分。
判断が遅いほど「インデックスなし状態」が長引く。
→ [§3.9 rollback 用の CREATE INDEX 戦略](#39-rollback-用の-create-index-戦略)

### CREATE 系

#### 落とし穴 6: 「`CREATE INDEX` で書き込みが止まった」

通常 `CREATE INDEX` は `SHARE` ロック → 書き込み全停止。本番稼働中は **必ず `CONCURRENTLY`**。
→ [§3.2 CREATE INDEX CONCURRENTLY](#32-create-index-concurrently)

#### 落とし穴 7: 「`CONCURRENTLY` 失敗したからもう一回打てばいい」

失敗すると `INVALID` index が残る。同名で再実行は エラー。**先に DROP CONCURRENTLY** が必要。
→ [§3.7 CONCURRENTLY 失敗時のクリーンアップ](#37-concurrently-失敗時のクリーンアップ)

#### 落とし穴 8: 「partitioned 親に `CONCURRENTLY` 使える」

不可。子で CONCURRENTLY 作って ATTACH する戦略が必要。
→ [§3.3 partitioned table の CREATE INDEX](#33-partitioned-table-の-create-index)

#### 落とし穴 9: 「`maintenance_work_mem` は default で十分」

default 64 MB は本番大規模には小さすぎ。**2 GB 等に上げると 2-5 倍速い**。
→ [§3.5 CREATE INDEX を加速する手段](#35-create-index-を加速する手段)

#### 落とし穴 10: 「`CREATE INDEX` 中の I/O は気にしない」

大規模 index ビルドは数 GB〜数十 GB の I/O。**burst IOPS 枯渇**で DB 全体性能急落。
→ [§3.8 CREATE INDEX 中の I/O 増加への配慮](#38-create-index-中の-io-増加への配慮)

---

## 12. ケーススタディ: security_event の未使用 GIN 削除

### 12.1 問題の発見

```sql
SELECT indexrelname, idx_scan, pg_size_pretty(pg_relation_size(indexrelid))
FROM pg_stat_user_indexes
WHERE relname = 'security_event';
-- idx_events_detail_jsonb: idx_scan = 0 / size = 数GB ★
```

`idx_scan = 0` (24h 観察) かつアプリの SQL を確認:
```bash
grep -rn "detail @>\|detail ?\|detail ->\>" libs/
# → "detail ->> ? = ?" しか使われていない
# → GIN は `->>` を一切サポートしないので planner に選ばれない
```

→ **未使用確定**。

### 12.2 partitioned index への対応

`security_event` は pg_partman で日次 partition (premake=90, retention=90)。
→ 95-125 partition の親 partitioned index を持つ。

```sql
DROP INDEX idx_events_detail_jsonb;
-- 全 95 child index がカスケード削除
```

CONCURRENTLY は親には使えない → `lock_timeout` + retry。

### 12.3 ローカル検証で確認したこと

```
1. 30 TPS pgbench 並走 → 1 attempt success
2. 100 TPS pgbench 並走 → 1 attempt success
3. 実 API 負荷 k6 (~225 TPS) → 1 attempt success
4. 長時間 tx (5秒) と競合 → 4 attempts でロック取れず → tx 終了直後成功
```

→ 通常負荷下で安全に DROP できることが実証された。

### 12.4 本番投入計画

```
1. 平日深夜帯 (3:00 AM)
2. pg_stat_activity チェック (長時間 tx 不在)
3. CHECKPOINT
4. lock_timeout=2s + retry スクリプト実行
5. 完了 → 03-verify.sql で確認
6. アプリへの影響を 1 時間監視
7. 翌日 disk 解放と error rate を確認
```

### 12.5 期待効果

| 項目 | 期待値 |
|------|--------|
| ディスク解放 | 15〜20 GB |
| INSERT 高速化 | 4% (実測値) |
| WAL 量削減 | GIN の WAL は B-tree より重い |
| autovacuum 負荷削減 | GIN の VACUUM コストが消える |
| shared_buffers 効率改善 | working set が小さくなる |

### 12.6 詳細

実際のスクリプトと実測値は以下を参照:
- `libs/idp-server-database/postgresql/operation/drop-unused-gin-indexes/README.md`
- `libs/idp-server-database/postgresql/operation/drop-unused-gin-indexes/benchmark/RESULTS.md`

---

## 13. 参考リソース

### 公式ドキュメント

- [PostgreSQL: CREATE INDEX](https://www.postgresql.org/docs/current/sql-createindex.html)
- [PostgreSQL: DROP INDEX](https://www.postgresql.org/docs/current/sql-dropindex.html)
- [PostgreSQL: REINDEX](https://www.postgresql.org/docs/current/sql-reindex.html)
- [PostgreSQL: Explicit Locking](https://www.postgresql.org/docs/current/explicit-locking.html)

### 関連ドキュメント (本リポジトリ)

- [dev-03-indexes.md](./dev-03-indexes.md): インデックス基礎
- [dev-04-transactions.md](./dev-04-transactions.md): ロックとトランザクション
- [dba-06-maintenance.md](./dba-06-maintenance.md): 定期メンテナンス
- [dba-07-partitioning.md](./dba-07-partitioning.md): パーティショニング詳細

### 関連実装

- `libs/idp-server-database/postgresql/operation/drop-unused-gin-indexes/`: 実運用 runbook と実測ベンチマーク (本リポジトリ)
- PR #1555: idp-server での DROP INDEX 運用 runbook 追加

---

## まとめ

```
本番運用中の index 操作で本当に大事なこと:

  1. DROP INDEX 自体は速い、でも巻き込み事故は致命的
     → lock_timeout + retry で「諦め可能」にする

  2. partitioned index には CONCURRENTLY が使えない
     → 親 DROP のカスケード + lock_timeout が正攻法

  3. DROP 時間は shared_buffers サイズで線形に伸びる (PG 13 以前)
     → 事前 CHECKPOINT + lock_timeout で最悪ケースを抑える

  4. rollback は遅い (CREATE INDEX 数分〜数十分)
     → DROP 前に「数日観察」で確証を持つ

  5. REINDEX で代替できることもある
     → サイズ縮小だけが目的なら DROP は不要

  6. shared_buffers vs working set の関係を意識
     → cache hit ratio が低いと DROP も I/O bound になる
```

「**インデックス操作は短いが慎重に**」が運用の本質。
本ドキュメントの数値は本番投入前の指針として活用してください。
