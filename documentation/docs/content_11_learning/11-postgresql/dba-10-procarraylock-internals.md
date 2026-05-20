# ProcArrayLock と LWLock の内部メカニズム

PostgreSQL を運用していると、ある接続数を境に「**CPU は余っているのに全クエリが一律に遅延する**」「**軽量なクエリすら数百ミリ秒かかる**」といった、原因の掴みにくい現象に遭遇することがあります。

特定のクエリや行へのロック競合ではない。それなのに DB が確実に詰まっている。その背景に潜むことがあるのが、PostgreSQL の **内部共有メモリ構造への並行アクセス競合** です。

本ドキュメントでは、その代表格である **ProcArrayLock** を主軸に、関連する LWLock（Lightweight Lock）群と接続数スケーラビリティの仕組みを解説します。

> 同様の症状は CPU 飽和・ディスク I/O 律速・行ロック競合などからも発生しうるため、原因の切り分けには観測が不可欠です。本ドキュメントは「**この種のボトルネックがある場合に何を観測してどう判断するか**」に焦点を当てます。診断フローは [5. 症状の見抜き方](#5-症状の見抜き方) と [9. まとめ](#9-まとめ) を参照してください。

---

## 目次

1. [PostgreSQL のプロセスモデル](#1-postgresql-のプロセスモデル)
2. [ProcArray と MVCC スナップショット](#2-procarray-と-mvcc-スナップショット)
3. [ProcArrayLock の役割](#3-procarraylock-の役割)
4. [接続数が増えると遅くなる理由](#4-接続数が増えると遅くなる理由)
5. [症状の見抜き方](#5-症状の見抜き方)
6. [PostgreSQL 14 以降の改善](#6-postgresql-14-以降の改善)
7. [関連する LWLock 群](#7-関連する-lwlock-群)
8. [対策](#8-対策)
9. [まとめ](#9-まとめ)
10. [参考リソース](#10-参考リソース)

---

## 1. PostgreSQL のプロセスモデル

### 1.1 接続 = 1 プロセス

PostgreSQL は **プロセスベースの並行モデル** を採用しています。`postmaster` と呼ばれる親プロセスがクライアント接続を受け付け、接続ごとに新しい backend process を `fork()` します。

```
┌──────────────────────────────────────────────────────────┐
│              PostgreSQL Server Process Tree              │
│                                                          │
│   postmaster (親)                                        │
│    ├── checkpointer                                      │
│    ├── background writer                                 │
│    ├── walwriter                                         │
│    ├── autovacuum launcher                               │
│    ├── stats collector                                   │
│    │                                                     │
│    ├── backend process 1  ← クライアント接続 #1          │
│    ├── backend process 2  ← クライアント接続 #2          │
│    ├── backend process 3  ← クライアント接続 #3          │
│    │       ...                                           │
│    └── backend process N  ← クライアント接続 #N          │
└──────────────────────────────────────────────────────────┘
```

各 backend は **独立した OS プロセス** で、独自のメモリ空間を持ちます。

### 1.2 マルチスレッドモデルとの違い

| 観点 | PostgreSQL（プロセス） | MySQL/MariaDB（スレッド） |
|------|----------------------|--------------------------|
| 1 接続あたりのオーバーヘッド | 大（fork コスト、独立メモリ） | 小（スレッド作成のみ） |
| プロセス間通信 | 共有メモリ経由 | スレッド間で直接参照 |
| クラッシュ時の影響 | 1 プロセスのみ | 全体に影響しうる |
| 高接続数のスケーラビリティ | 接続数増加でオーバーヘッド大 | 比較的良い |

PostgreSQL のプロセスモデルは **隔離性と堅牢性** に優れる一方、**接続数が増えるとプロセス間の調整コストが増大** します。

### 1.3 接続あたりのコスト

1 つの backend process は最低でも数 MB のメモリを使用し、扱うデータ量に応じて拡大します（ソート用 work_mem、テンポラリテーブル、キャッシュなど）。

加えて、後述の **共有メモリ構造への調整コスト** が「目に見えにくいオーバーヘッド」として効いてきます。

---

## 2. ProcArray と MVCC スナップショット

### 2.1 ProcArray とは

**ProcArray** は、現在存在する全 backend process の状態を保持する **共有メモリ上の配列** です。各エントリには以下のような情報が含まれます：

- 実行中のトランザクション ID（XID）
- サブトランザクション情報
- vacuum 関連フラグ
- 接続が見ている最古の XID（xmin）

すべての backend がこの配列を **読み書きする** ため、並行アクセスの調整が必要になります。

### 2.2 MVCC スナップショットとは

PostgreSQL の **MVCC**（Multi-Version Concurrency Control）は、トランザクション分離レベルを実現するために **スナップショット** を使います。

```
┌─────────────────────────────────────────────────────────┐
│              MVCC スナップショットの仕組み                │
│                                                         │
│   トランザクション開始時、または各 SQL 文の実行時に     │
│   「現時点で見えるべき他トランザクションの状態」を       │
│   スナップショットとして固定                            │
│                                                         │
│   スナップショット = {                                   │
│     xmin: 最小の active XID,                            │
│     xmax: 次に割り当てられる XID,                       │
│     active_xids: [現在実行中の XID リスト]              │
│   }                                                     │
└─────────────────────────────────────────────────────────┘
```

このスナップショットを構築する関数が **`GetSnapshotData()`** で、トランザクションの開始時や各文の実行時など、**極めて頻繁に呼ばれます**。

### 2.3 GetSnapshotData の計算量

`GetSnapshotData()` の本質は **「現在 active な backend を全部スキャンする」** 処理です。

```
全 backend を順に走査:
  for backend in ProcArray:
    if backend has active XID:
      active_xids.append(xid)
    track min(xmin)
```

→ **計算量は O(N)**（N = 接続数）

つまり、**接続数が増えるほどスナップショット生成が遅くなる**。これが PostgreSQL の接続スケーラビリティの根本的な制約となっています。

---

## 3. ProcArrayLock の役割

### 3.1 ProcArrayLock とは

**ProcArrayLock** は、ProcArray という共有データ構造への並行アクセスを保護する **LWLock**（Lightweight Lock）です。

LWLock は、ヒープテーブル行などに対するアプリケーションレベルのロックとは別の、**PostgreSQL 内部の共有メモリ構造を保護する低レベル同期プリミティブ** です。

### 3.2 取得モード

LWLock には 2 つのモードがあります：

| モード | 用途 | 同時保持 |
|--------|------|---------|
| **SHARED** | 読み取り（スナップショット取得など） | 複数 backend が同時保持可能 |
| **EXCLUSIVE** | 書き込み（トランザクション開始/終了） | 1 backend のみ |

### 3.3 ProcArrayLock を取得するタイミング

主なケース：

| 操作 | モード | 説明 |
|------|--------|------|
| `GetSnapshotData()` | SHARED | スナップショット取得時、ProcArray を読む |
| `ProcArrayAdd()` | EXCLUSIVE | 新規 backend の追加時 |
| `ProcArrayRemove()` | EXCLUSIVE | backend 終了時 |
| `ProcArrayEndTransaction()` | EXCLUSIVE | トランザクション終了時の状態更新 |

→ **トランザクション境界ごとに頻繁に EXCLUSIVE が取られる** ため、SHARED 取得が頻繁にブロックされる構造になります。

---

## 4. 接続数が増えると遅くなる理由

### 4.1 二つの作用が複合する

ProcArrayLock が接続数増加でボトルネックになる理由は、**2 つの作用の複合** です：

**作用 1: スナップショット計算自体が O(N)**

接続数が増えると `GetSnapshotData()` の **1 回あたりの処理時間** が伸びる。

**作用 2: ProcArrayLock 自体の競合**

多くの backend が同時に snapshot を取得しようとして **ロック取得待ち** が発生する。EXCLUSIVE（トランザクション終了など）が割り込めば SHARED 待ち全体がブロックされる。

### 4.2 ピンポン現象（cache line ping-pong）

複数の CPU コアが頻繁に同じメモリ領域を更新すると、CPU キャッシュラインの **「所有権の取り合い」** が発生します。

```
時刻 t1: CPU#1 が backend A の xmin を更新（キャッシュラインを排他取得）
時刻 t2: CPU#2 が backend B の xmin を更新（CPU#1 からキャッシュライン強奪）
時刻 t3: CPU#3 が backend C の xmin を更新（CPU#2 から強奪）
   ...
時刻 tx: スナップショット計算のため全 backend の状態を読みたい backend が
         キャッシュミスを連発しメモリから読み直し → 遅い
```

接続数 × CPU コア数の積で **キャッシュ効率が急速に悪化** することが知られています。

### 4.3 アイドル接続でも遅延を引き起こす

注意すべきは、**アイドル接続（何も処理していない接続）でも、ProcArray にエントリは存在する** 点です。

`GetSnapshotData()` は全 backend を走査するため、アイドル接続が大量にあるだけで **アクティブな接続の処理速度が低下** します。

> コミュニティのベンチマークによると、active 接続 1 つに対してアイドル接続が増えるだけで、その active 接続の CPU 時間の大半が `GetSnapshotData()` に費やされる現象が観測されています。

これは **「使ってない接続なら無害だろう」という直感を裏切る挙動** です。

---

## 5. 症状の見抜き方

### 5.1 観察される表面的な症状

| 症状 | 通常時 | ProcArrayLock 競合時 |
|------|--------|---------------------|
| `SELECT set_config(...)` 等の軽量クエリ | 1ms 未満 | 数十〜数百 ms |
| 全クエリの平均レイテンシ | ベースライン | 一律に数倍〜十数倍 |
| DB CPU 使用率 | クエリ量に比例 | クエリ量に対して不釣り合いに高い |
| トランザクション数 | 線形に増加 | 頭打ち |

### 5.2 DB Monitoring での見え方

Datadog DBM、Performance Insights、自前の `pg_stat_activity` 監視のいずれでも、**Wait Events**（待機イベント）が決定的な手がかりになります。

```sql
-- 性能テスト中に繰り返し叩く
SELECT wait_event_type, wait_event, count(*)
FROM pg_stat_activity
WHERE state = 'active'
  AND datname = '<対象 DB>'
GROUP BY wait_event_type, wait_event
ORDER BY count(*) DESC;
```

**支配的な wait event の解釈**：

| Wait Event | 強く疑われる原因 |
|------------|------------------|
| `LWLock: ProcArrayLock` 多発 | ProcArrayLock 競合（接続数律速） |
| `LWLock: WALWrite` 多発 | WAL 書き込み律速（ディスク I/O） |
| `LWLock: BufferContent` 多発 | 共有バッファ競合 |
| `Client: ClientRead` 多発 | アプリ側の遅延（idle in transaction） |
| `Lock: transactionid` 多発 | 行ロック競合（heavyweight lock） |

> Wait Event は **「backend が今何を待っているか」を PostgreSQL 自身が報告するもの** なので、最も信頼できるシグナルです。ただし観測はスナップショットなので、**継続的に多発しているか** を時系列で確認する必要があります（一瞬出ただけでは判断材料にならない）。

### 5.3 「全体が遅い vs 一部が遅い」の見分け方

ProcArrayLock 競合の典型サインは **「クエリの種別に関わらず遅い」** こと。

- 重いクエリだけ遅い → クエリ・インデックス・統計情報の問題
- 軽いクエリすら遅い、しかも一律 → **共通の通り道（LWLock）が詰まっている**

軽量クエリ（`SELECT 1`、`set_config`、軽い INSERT など）の実行時間を **canary** として観測すると分かりやすいです。

---

## 6. PostgreSQL 14 以降の改善

PostgreSQL 14 では、Andres Freund 氏らによって **スナップショット取得まわりの大規模な最適化** が入りました。

### 6.1 主要な変更

| コミット趣旨 | 効果 |
|--------------|------|
| 稼働中 XID を **密な配列** で管理 | スキャン効率向上、キャッシュ局所性改善 |
| `PGXACT->xmin` を `PGPROC` に移動 | キャッシュラインピンポンの軽減 |
| スナップショットの **キャッシュ化**（commit カウンタで無効化判定） | 同一スナップショットの再利用 |
| グローバル horizon 計算をスナップショット構築から分離 | 不要な計算の削減 |

### 6.2 改善後でも残る制約

これらの改善で「**読み取り中心の高接続ワークロードで概ね 2 倍程度のスループット向上**」が報告されていますが、以下は **完全には解消されていません**：

- スナップショット計算が完全になくなったわけではない
- キャッシュされたスナップショットの有効性チェック自体でロックが必要
- 書き込みワークロードでは効果が限定的（EXCLUSIVE 取得が依然必要）

> 「PostgreSQL 14 にしたから無制限に接続を増やしてよい」という話ではない、という点に注意。**接続数を適切に管理すること自体は変わらず重要** です。

### 6.3 RDS Aurora 等のマネージドサービスでの位置付け

クラウドのマネージド PostgreSQL（RDS, Aurora, Cloud SQL）も、提供エンジンのバージョンに応じてこれらの改善を取り込んでいます。利用しているエンジンバージョンが PostgreSQL 14 以降かは確認しておくと良いです。

---

## 7. 関連する LWLock 群

ProcArrayLock 以外にも、共有メモリ構造を保護する LWLock は数多く存在します。**「軽量クエリが遅い」症状の原因は、必ずしも ProcArrayLock とは限らない** ため、関連 LWLock を一通り把握しておくと診断が早くなります。

### 7.1 主要な LWLock

| LWLock | 保護対象 | 競合のシナリオ |
|--------|----------|---------------|
| **ProcArrayLock** | ProcArray（backend 状態） | 高接続数 + 短 tx 多発 |
| **WALInsertLock** | WAL バッファへの書き込み | 高い書き込み TPS |
| **WALWriteLock** | WAL のディスクフラッシュ | 同期コミットが多い書き込みワークロード |
| **LockManagerLock** | 行ロック・テーブルロックの管理（16 パーティション） | 同一テーブルへの大量並行アクセス |
| **BufferMappingLock** | 共有バッファのマッピング（複数パーティション） | shared_buffers 圧迫、キャッシュミス多発 |
| **BufferContent** | 個別バッファブロックの内容 | 同一ページへの並行更新 |
| **XidGenLock** | XID 採番 | 短時間に大量トランザクション |
| **CLogControlLock** | コミットログ管理 | コミット頻度が極端に高い場合 |

### 7.2 LWLock vs heavyweight lock

LWLock とアプリケーションが意識する「行ロック」「テーブルロック」（heavyweight lock）は **別物** です：

```
┌──────────────────────────────────────────────────────────┐
│                                                          │
│   Heavyweight Lock (行・テーブルロック)                  │
│     ↑                                                    │
│   pg_locks ビューで観測可能                              │
│   wait_event_type = 'Lock'                               │
│     例: 'tuple', 'transactionid', 'relation'             │
│                                                          │
│   ──────────────────────────────────────                 │
│                                                          │
│   LWLock (内部共有メモリ保護)                            │
│     ↑                                                    │
│   pg_stat_activity の wait_event で観測                  │
│   wait_event_type = 'LWLock'                             │
│     例: 'ProcArrayLock', 'WALWriteLock'                  │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

LWLock は「PostgreSQL が内部で正しく動くため」のもので、通常はアプリ開発者が直接意識する必要はありません。**ただし高負荷で見えてくると診断のためには理解が必要** です。

---

## 8. 対策

### 8.1 接続数を抑える（最優先）

ProcArrayLock 競合の根本対策は **物理 backend 数を抑える** ことです。

**接続プーラーの導入**：

| プーラー | 配置 | 特徴 |
|---------|------|------|
| **PgBouncer** | アプリと DB の間（独立プロセス） | 軽量、設定が分かりやすい |
| **Pgpool-II** | 同上 | ロードバランス機能あり |
| **AWS RDS Proxy** | マネージドサービス | 認証・フェイルオーバー込み |
| **HikariCP / その他アプリ内プール** | アプリプロセス内 | 物理接続削減には不十分 |

> **重要**: HikariCP のような **アプリ内プール** は、複数アプリインスタンスからの合計接続数を抑える効果はありません。**アプリ外のプーラー** が必要。

### 8.2 transaction mode の活用

PgBouncer や RDS Proxy には、複数のプーリングモードがあります。**transaction mode** が最も多重化効率が高い：

```
session mode:
  クライアント接続 1 つに DB backend 1 つを固定
  → アプリ側プールと同等、多重化効果なし

transaction mode:
  トランザクション単位で DB backend を借りる
  → backend 数を桁違いに削減できる
  ※ session スコープの機能（preparedstatement 等）は制限あり

statement mode:
  ステートメント単位（実用性は低い）
```

### 8.3 アプリ側での tx 数削減

アプリのコード側でできる対策：

- **1 リクエストあたりのトランザクション数を減らす**（同期化、複数操作の集約）
- **不要な短いトランザクションを統合する**
- **同期的にすべき処理と非同期で良い処理を整理する**

ProcArrayLock の取得頻度は **トランザクション境界の通過頻度** に直結するので、tx 数が減れば直接的に競合が軽減されます。

### 8.4 max_connections の適正化

`max_connections` を闇雲に上げる対策は逆効果になりえます：

- ProcArray のサイズ自体が大きくなる → スナップショット計算が遅くなる
- backend ごとのメモリ消費上限に注意
- 物理 CPU コア数とのバランス

**経験則**：
- 物理 backend 数 = CPU コア数 × 2〜4 程度を目安に
- それ以上必要なら接続プーラーで多重化
- max_connections を 500 以上に設定するのは慎重に

詳しくは [dev-06-connection-pooling.md](dev-06-connection-pooling.md) の `max_connections` セクションを参照。

### 8.5 PostgreSQL バージョンの最新化

PostgreSQL 14 以降のスナップショット改善は **コードを変えずに恩恵を受けられる** 性質のもの。マネージドサービスのエンジンバージョンが古い場合、アップグレード自体が選択肢になります。

---

## 9. まとめ

### キーポイント

- **PostgreSQL はプロセスベースモデル**で、接続数増加が様々なオーバーヘッドにつながる
- **ProcArray は全 backend の状態を共有する配列**で、MVCC スナップショット計算に使われる
- **ProcArrayLock の競合**は接続数増加で顕在化し、「全体が一律に遅くなる」典型症状を引き起こす
- 観察ポイントは **`pg_stat_activity.wait_event` で `LWLock:ProcArrayLock` が支配的か**
- **PostgreSQL 14+ で改善されたが完全解決ではない**
- **対策の中心は接続プーラー（PgBouncer / RDS Proxy）の transaction mode による物理 backend 数の削減**
- アプリ側では **1 リクエストあたりの tx 数を抑える** ことも有効

### 診断フローチャート（簡略版）

```
症状: 軽量クエリが遅い、全体が一律に遅い
   │
   ▼
pg_stat_activity の wait_event を観測
   │
   ├─ LWLock: ProcArrayLock 多発 → 接続数律速、プーラー検討
   ├─ LWLock: WALWrite 多発     → ディスク I/O 律速、ストレージ強化
   ├─ LWLock: BufferContent     → shared_buffers 不足 / バッファ圧迫
   ├─ Lock: transactionid       → 行ロック競合、設計見直し
   ├─ Client: ClientRead        → アプリ側の遅延（外部 I/O 等）
   └─ どれも目立たない          → DB CPU / クエリ自体の遅さを確認
```

---

## 10. 参考リソース

### PostgreSQL 公式

- [PostgreSQL Documentation: System Views](https://www.postgresql.org/docs/current/views.html) - `pg_stat_activity` の仕様
- [PostgreSQL Documentation: Wait Events](https://www.postgresql.org/docs/current/monitoring-stats.html#WAIT-EVENT-TABLE) - Wait event 一覧
- [The Internals of PostgreSQL](https://www.interdb.jp/pg/) - 内部構造の詳細解説

### 技術ブログ・解説

- [Improving Postgres Connection Scalability: Snapshots (Citus Data, Andres Freund)](https://www.citusdata.com/blog/2020/10/25/improving-postgres-connection-scalability-snapshots/) - PostgreSQL 14 のスナップショット改善の解説
- [Analyzing the Limits of Connection Scalability in Postgres (Citus Data)](https://www.citusdata.com/blog/2020/10/08/analyzing-connection-scalability/) - 接続スケーラビリティの限界分析
- [PostgreSQL locking, part 3: lightweight locks (Percona)](https://www.percona.com/blog/postgresql-locking-part-3-lightweight-locks/) - LWLock の体系的な解説
- [How to Manage Connections Efficiently in Postgres (brandur.org)](https://brandur.org/postgres-connections) - 接続管理のベストプラクティス

### AWS 関連

- [Amazon RDS Proxy User Guide](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/rds-proxy.html) - RDS Proxy の公式ドキュメント
- [Avoid PostgreSQL LWLock:buffer_content locks in Amazon Aurora (AWS Blog)](https://aws.amazon.com/blogs/database/avoid-postgresql-lwlockbuffer_content-locks-in-amazon-aurora-tips-and-best-practices/) - BufferContent ロックの実例

### 関連する idp-server 学習コンテンツ

- [dev-04-transactions.md](dev-04-transactions.md) - MVCC とトランザクション分離
- [dev-06-connection-pooling.md](dev-06-connection-pooling.md) - 接続プーリングの実装パターン
- [dba-04-security.md](dba-04-security.md) - Row Level Security（RLS）
- [dba-05-monitoring.md](dba-05-monitoring.md) - `pg_stat_activity` を使った監視
- [content_11_learning/26-performance-tuning/14-case-study-lock-contention.md](../26-performance-tuning/14-case-study-lock-contention.md) - 行ロック競合のケーススタディ
