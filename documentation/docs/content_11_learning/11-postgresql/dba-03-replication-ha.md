# PostgreSQL レプリケーションとHA構成ガイド

このドキュメントでは、PostgreSQLのレプリケーションと高可用性（HA）構成を解説します。

---

## 目次

1. [レプリケーションの概要](#1-レプリケーションの概要)
2. [ストリーミングレプリケーション](#2-ストリーミングレプリケーション)
3. [同期レプリケーション](#3-同期レプリケーション)
4. [論理レプリケーション](#4-論理レプリケーション)
5. [レプリケーションスロット](#5-レプリケーションスロット)
6. [フェイルオーバー](#6-フェイルオーバー)
7. [Patroniによる自動フェイルオーバー](#7-patroniによる自動フェイルオーバー)
8. [読み取り負荷分散](#8-読み取り負荷分散)

---

## 1. レプリケーションの概要

### 1.1 レプリケーションの目的

```
┌──────────────────────────────────────────────────────────────┐
│                   レプリケーションの目的                      │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  【高可用性 (High Availability)】                            │
│  - プライマリ障害時にスタンバイに切り替え                    │
│  - ダウンタイムの最小化                                      │
│  - RPO (データ損失) の最小化                                 │
│                                                              │
│  【読み取りスケーリング】                                    │
│  - 読み取りクエリをスタンバイに分散                          │
│  - プライマリの負荷軽減                                      │
│  - レポート/分析クエリの分離                                 │
│                                                              │
│  【災害対策 (Disaster Recovery)】                            │
│  - 地理的に離れた場所にレプリカを配置                        │
│  - データセンター障害への対策                                │
│                                                              │
│  【メンテナンス】                                            │
│  - ローリングアップグレード                                  │
│  - スタンバイでのバックアップ取得                            │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 1.2 レプリケーション方式の比較

```
┌──────────────────────────────────────────────────────────────┐
│                 レプリケーション方式の比較                    │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  【ストリーミングレプリケーション】(物理)                    │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ ✅ WALをそのまま送信 → 完全なレプリカ                │   │
│  │ ✅ 設定が比較的シンプル                               │   │
│  │ ✅ PITRと組み合わせ可能                               │   │
│  │ ❌ クラスタ全体の複製のみ (テーブル単位不可)         │   │
│  │ ❌ 同一メジャーバージョン間のみ                       │   │
│  │ ❌ 同一アーキテクチャ間のみ                           │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  【論理レプリケーション】                                    │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ ✅ テーブル/データベース単位で選択可能               │   │
│  │ ✅ 異なるメジャーバージョン間で可能                   │   │
│  │ ✅ サブスクライバー側で追加のインデックス作成可能    │   │
│  │ ❌ DDLは複製されない                                  │   │
│  │ ❌ シーケンス、ラージオブジェクトは複製されない      │   │
│  │ ❌ 設定が複雑                                         │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  【選択の指針】                                              │
│  - HA/DR目的 → ストリーミングレプリケーション               │
│  - 部分的な複製、異バージョン間 → 論理レプリケーション      │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## 2. ストリーミングレプリケーション

### 2.1 アーキテクチャ

```
┌──────────────────────────────────────────────────────────────┐
│              ストリーミングレプリケーション                   │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│   Primary                              Standby               │
│  ┌─────────────────────┐            ┌─────────────────────┐ │
│  │                     │            │                     │ │
│  │  ┌───────────────┐  │            │  ┌───────────────┐  │ │
│  │  │   Backend     │  │            │  │  WAL Receiver │  │ │
│  │  └───────┬───────┘  │            │  └───────┬───────┘  │ │
│  │          │ write    │            │          │ apply    │ │
│  │          ▼          │            │          ▼          │ │
│  │  ┌───────────────┐  │   WAL      │  ┌───────────────┐  │ │
│  │  │    WAL        │  │  Stream    │  │    WAL        │  │ │
│  │  │   Buffer      │──┼──────────→ │  │   Buffer      │  │ │
│  │  └───────┬───────┘  │            │  └───────┬───────┘  │ │
│  │          │          │            │          │          │ │
│  │          ▼          │            │          ▼          │ │
│  │  ┌───────────────┐  │            │  ┌───────────────┐  │ │
│  │  │   pg_wal/     │  │            │  │   pg_wal/     │  │ │
│  │  └───────────────┘  │            │  └───────────────┘  │ │
│  │          │          │            │          │          │ │
│  │          ▼          │            │          ▼          │ │
│  │  ┌───────────────┐  │            │  ┌───────────────┐  │ │
│  │  │  Data Files   │  │            │  │  Data Files   │  │ │
│  │  └───────────────┘  │            │  └───────────────┘  │ │
│  │                     │            │                     │ │
│  │  Read/Write         │            │  Read Only          │ │
│  └─────────────────────┘            └─────────────────────┘ │
│                                                              │
│  【プロセス】                                                │
│  Primary: WAL Sender プロセスがWALを送信                    │
│  Standby: WAL Receiver プロセスがWALを受信・適用            │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 2.2 プライマリの設定

```ini
# postgresql.conf (Primary)

# WALレベル (replica以上が必要)
wal_level = replica

# WAL送信プロセスの最大数
max_wal_senders = 10

# レプリケーションスロット数
max_replication_slots = 10

# スタンバイへのWAL保持 (スロット未使用時)
wal_keep_size = 1GB

# アーカイブ (推奨)
archive_mode = on
archive_command = 'cp %p /var/lib/pgsql/archive/%f'

# ホットスタンバイからのフィードバック
hot_standby_feedback = on
```

```bash
# pg_hba.conf (Primary)
# レプリケーション接続を許可
host    replication     repl_user       192.168.1.0/24      scram-sha-256
```

```sql
-- レプリケーション用ユーザーの作成 (Primary)
CREATE ROLE repl_user WITH
    LOGIN
    REPLICATION
    PASSWORD 'replication_password';
```

### 2.3 スタンバイの構築

```bash
# 1. プライマリからベースバックアップを取得
pg_basebackup -h primary_host -U repl_user -D /var/lib/pgsql/16/data \
    -Fp -Xs -P -R

# -R オプションで以下が自動生成される:
# - standby.signal ファイル
# - postgresql.auto.conf に primary_conninfo が追加される
```

### 2.4 スタンバイの設定

```ini
# postgresql.conf (Standby)

# ホットスタンバイ (読み取り可能)
hot_standby = on

# リカバリ中のクエリ競合時の待機時間
max_standby_streaming_delay = 30s
max_standby_archive_delay = 30s

# WAL受信タイムアウト
wal_receiver_timeout = 60s

# 昇格時のトリガーファイル (旧方式、現在は pg_promote() 推奨)
# promote_trigger_file = '/tmp/promote_trigger'
```

```ini
# postgresql.auto.conf (自動生成または手動設定)
primary_conninfo = 'host=primary_host port=5432 user=repl_user password=replication_password'
primary_slot_name = 'standby1_slot'
```

```bash
# standby.signal ファイルの存在でスタンバイモードになる
touch /var/lib/pgsql/16/data/standby.signal
```

### 2.5 レプリケーション状態の確認

```sql
-- プライマリで確認: 接続中のスタンバイ
SELECT
    client_addr,
    state,
    sent_lsn,
    write_lsn,
    flush_lsn,
    replay_lsn,
    pg_wal_lsn_diff(sent_lsn, replay_lsn) AS replay_lag_bytes,
    sync_state
FROM pg_stat_replication;

-- スタンバイで確認: レプリケーション状態
SELECT
    status,
    received_lsn,
    latest_end_lsn,
    last_msg_send_time,
    last_msg_receipt_time
FROM pg_stat_wal_receiver;

-- スタンバイで確認: リカバリ状態
SELECT pg_is_in_recovery();

-- レプリケーションラグ (秒)
SELECT
    CASE WHEN pg_is_in_recovery() THEN
        EXTRACT(EPOCH FROM (now() - pg_last_xact_replay_timestamp()))
    ELSE
        0
    END AS lag_seconds;
```

### 2.6 カスケードレプリケーション

```
┌──────────────────────────────────────────────────────────────┐
│                 カスケードレプリケーション                    │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│   Primary ──────→ Standby1 ──────→ Standby2                 │
│                       │                                      │
│                       └──────→ Standby3                     │
│                                                              │
│  【利点】                                                    │
│  - Primaryの負荷軽減                                         │
│  - 地理的に離れた場所への効率的な複製                        │
│                                                              │
│  【設定】                                                    │
│  Standby1 で wal_level = replica を設定                      │
│  Standby2, Standby3 は Standby1 を primary_conninfo で指定   │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## 3. 同期レプリケーション

### 3.1 同期レプリケーションの概念

```
┌──────────────────────────────────────────────────────────────┐
│                    同期レプリケーション                       │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  【非同期 (デフォルト)】                                     │
│                                                              │
│  Client ──→ Primary: COMMIT                                 │
│             Primary: WAL書き込み                             │
│             Primary ──→ Client: OK ← ここで応答             │
│             Primary ──→ Standby: WAL送信 (後から)           │
│                                                              │
│  → Primary障害時、送信前のWALが失われる可能性                │
│                                                              │
│  ──────────────────────────────────────────────             │
│                                                              │
│  【同期】                                                    │
│                                                              │
│  Client ──→ Primary: COMMIT                                 │
│             Primary: WAL書き込み                             │
│             Primary ──→ Standby: WAL送信                    │
│             Standby: WAL受信/書き込み                        │
│             Standby ──→ Primary: 確認応答                   │
│             Primary ──→ Client: OK ← ここで応答             │
│                                                              │
│  → データ損失なし、ただし遅延増加                           │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 3.2 同期レプリケーションの設定

```ini
# postgresql.conf (Primary)

# 同期スタンバイの指定
# FIRST 1: 最初の1台が応答すればOK
synchronous_standby_names = 'FIRST 1 (standby1, standby2)'

# または ANY: N台中いずれかN台が応答すればOK
synchronous_standby_names = 'ANY 2 (standby1, standby2, standby3)'

# 特定のスタンバイを指定
synchronous_standby_names = 'standby1'

# 同期コミットレベル
synchronous_commit = on
```

### 3.3 synchronous_commit のレベル

```
┌──────────────────────────────────────────────────────────────┐
│                synchronous_commit のレベル                    │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  off                                                         │
│  └─ WALバッファに書き込んだ時点で応答                        │
│     最速だが、クラッシュ時にデータ損失の可能性               │
│                                                              │
│  local                                                       │
│  └─ ローカルのディスクに書き込んだ時点で応答                 │
│     スタンバイへの送信は待たない                             │
│                                                              │
│  remote_write                                                │
│  └─ スタンバイがOSバッファに書き込んだ時点で応答             │
│     スタンバイのディスク書き込みは待たない                   │
│                                                              │
│  on (デフォルト)                                             │
│  └─ スタンバイがディスクに書き込んだ時点で応答               │
│     スタンバイでのWAL適用は待たない                          │
│                                                              │
│  remote_apply                                                │
│  └─ スタンバイがWALを適用した時点で応答                      │
│     スタンバイで読み取り可能になってから応答                 │
│     最も厳格だが最も遅い                                     │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 3.4 同期レプリケーションの状態確認

```sql
-- 同期スタンバイの確認
SELECT
    application_name,
    client_addr,
    state,
    sync_state,      -- sync, async, quorum, potential
    sync_priority
FROM pg_stat_replication;

-- sync_state の意味:
-- sync: 現在の同期スタンバイ
-- potential: 候補 (syncが落ちたら昇格)
-- async: 非同期
-- quorum: quorum対象
```

---

## 4. 論理レプリケーション

### 4.1 論理レプリケーションの概念

```
┌──────────────────────────────────────────────────────────────┐
│                   論理レプリケーション                        │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│   Publisher                        Subscriber                │
│  ┌─────────────────────┐         ┌─────────────────────┐    │
│  │                     │         │                     │    │
│  │  CREATE PUBLICATION │         │ CREATE SUBSCRIPTION │    │
│  │  FOR TABLE t1, t2   │         │ CONNECTION '...'    │    │
│  │                     │         │ PUBLICATION pub1    │    │
│  │  ┌───────────────┐  │ logical │  ┌───────────────┐  │    │
│  │  │ WAL Decoder   │──┼─────────┼─→│ Apply Worker  │  │    │
│  │  └───────────────┘  │ changes │  └───────────────┘  │    │
│  │                     │         │                     │    │
│  │  t1, t2 テーブル   │         │  t1, t2 テーブル   │    │
│  │                     │         │                     │    │
│  └─────────────────────┘         └─────────────────────┘    │
│                                                              │
│  【特徴】                                                    │
│  - テーブル単位で選択可能                                    │
│  - 異なるバージョン間で可能                                  │
│  - Subscriber側も書き込み可能 (競合に注意)                  │
│  - DDLは複製されない                                         │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 4.2 Publisher (送信側) の設定

```ini
# postgresql.conf (Publisher)
wal_level = logical
max_replication_slots = 10
max_wal_senders = 10
```

```sql
-- パブリケーションの作成
-- 特定のテーブル
CREATE PUBLICATION my_pub FOR TABLE users, orders;

-- 全テーブル
CREATE PUBLICATION my_pub FOR ALL TABLES;

-- 特定スキーマの全テーブル
CREATE PUBLICATION my_pub FOR TABLES IN SCHEMA public;

-- INSERT/UPDATE/DELETEの選択
CREATE PUBLICATION my_pub FOR TABLE users
    WITH (publish = 'insert, update, delete');

-- パブリケーションにテーブルを追加
ALTER PUBLICATION my_pub ADD TABLE products;

-- パブリケーションの確認
SELECT * FROM pg_publication;
SELECT * FROM pg_publication_tables;
```

### 4.3 Subscriber (受信側) の設定

```sql
-- テーブルを事前に作成 (スキーマは複製されない)
CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100),
    email VARCHAR(255)
);

CREATE TABLE orders (
    id SERIAL PRIMARY KEY,
    user_id INTEGER,
    total NUMERIC
);

-- サブスクリプションの作成
CREATE SUBSCRIPTION my_sub
    CONNECTION 'host=publisher_host port=5432 dbname=mydb user=repl_user password=xxx'
    PUBLICATION my_pub;

-- 初期データコピーをスキップ (既にデータがある場合)
CREATE SUBSCRIPTION my_sub
    CONNECTION '...'
    PUBLICATION my_pub
    WITH (copy_data = false);

-- サブスクリプションの確認
SELECT * FROM pg_subscription;
SELECT * FROM pg_stat_subscription;
```

### 4.4 論理レプリケーションの管理

```sql
-- サブスクリプションの一時停止
ALTER SUBSCRIPTION my_sub DISABLE;

-- サブスクリプションの再開
ALTER SUBSCRIPTION my_sub ENABLE;

-- パブリケーションのリフレッシュ (新しいテーブルを同期)
ALTER SUBSCRIPTION my_sub REFRESH PUBLICATION;

-- サブスクリプションの削除
DROP SUBSCRIPTION my_sub;

-- レプリケーションの状態確認 (Subscriber側)
SELECT
    subname,
    received_lsn,
    latest_end_lsn,
    last_msg_send_time,
    last_msg_receipt_time
FROM pg_stat_subscription;

-- 初期同期の状態 (Subscriber側)
SELECT * FROM pg_subscription_rel;
```

### 4.5 論理レプリケーションの制限事項

```
┌──────────────────────────────────────────────────────────────┐
│               論理レプリケーションの制限事項                  │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  【複製されないもの】                                        │
│  - DDL (CREATE, ALTER, DROP)                                 │
│  - シーケンスの値                                            │
│  - ラージオブジェクト                                        │
│  - TRUNCATE (PostgreSQL 11以降は可能)                       │
│  - マテリアライズドビュー                                    │
│                                                              │
│  【要件】                                                    │
│  - レプリカアイデンティティが必要                            │
│    - PRIMARY KEY、または                                     │
│    - REPLICA IDENTITY FULL                                  │
│  - テーブル構造が一致している必要                            │
│                                                              │
│  【競合】                                                    │
│  - Subscriberで同じ行を更新すると競合                        │
│  - 競合時はレプリケーションが停止                            │
│  - 手動での解決が必要                                        │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

```sql
-- レプリカアイデンティティの設定 (PKがない場合)
ALTER TABLE my_table REPLICA IDENTITY FULL;

-- または特定のインデックスを使用
ALTER TABLE my_table REPLICA IDENTITY USING INDEX my_unique_idx;
```

---

## 5. レプリケーションスロット

### 5.1 レプリケーションスロットの役割

```
┌──────────────────────────────────────────────────────────────┐
│                  レプリケーションスロット                     │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  【問題】スロットなしの場合                                  │
│                                                              │
│  Standbyが一時的に停止                                       │
│      ↓                                                       │
│  Primaryは通常通りWALを生成・削除                            │
│      ↓                                                       │
│  Standbyが必要とするWALが削除される                          │
│      ↓                                                       │
│  Standbyが再接続できない！                                   │
│                                                              │
│  ──────────────────────────────────────────────             │
│                                                              │
│  【解決】スロットありの場合                                  │
│                                                              │
│  スロットが「このLSNまで必要」を記録                         │
│      ↓                                                       │
│  Primaryはスロットが参照するWALを保持                        │
│      ↓                                                       │
│  Standbyが再接続可能                                         │
│                                                              │
│  【注意】                                                    │
│  Standbyが長期間停止するとWALが蓄積してディスクを圧迫       │
│  → max_slot_wal_keep_size で制限可能                        │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 5.2 物理レプリケーションスロット

```sql
-- スロットの作成 (Primary)
SELECT pg_create_physical_replication_slot('standby1_slot');

-- Standbyの設定
-- postgresql.auto.conf
primary_slot_name = 'standby1_slot'

-- スロットの確認
SELECT
    slot_name,
    slot_type,
    active,
    restart_lsn,
    pg_wal_lsn_diff(pg_current_wal_lsn(), restart_lsn) AS lag_bytes
FROM pg_replication_slots;

-- スロットの削除
SELECT pg_drop_replication_slot('standby1_slot');
```

### 5.3 論理レプリケーションスロット

```sql
-- 論理スロットは CREATE SUBSCRIPTION 時に自動作成される

-- 手動作成 (デコードプラグイン指定)
SELECT pg_create_logical_replication_slot('my_slot', 'pgoutput');

-- 論理スロットからの変更取得 (デバッグ用)
SELECT * FROM pg_logical_slot_peek_changes('my_slot', NULL, NULL);

-- 変更を消費 (確認後に削除)
SELECT * FROM pg_logical_slot_get_changes('my_slot', NULL, NULL);
```

### 5.4 スロットの監視

```sql
-- スロットの状態とWAL保持量
SELECT
    slot_name,
    slot_type,
    active,
    pg_size_pretty(pg_wal_lsn_diff(pg_current_wal_lsn(), restart_lsn)) AS wal_retained
FROM pg_replication_slots;

-- 非アクティブスロットの検出 (WAL蓄積リスク)
SELECT slot_name, active, restart_lsn
FROM pg_replication_slots
WHERE NOT active;
```

```ini
# WAL蓄積の制限 (PostgreSQL 13+)
max_slot_wal_keep_size = 10GB  # この量を超えると無効スロットを無視
```

---

## 6. フェイルオーバー

### 6.1 手動フェイルオーバー

```
┌──────────────────────────────────────────────────────────────┐
│                    手動フェイルオーバー                       │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  【状況】Primary障害発生                                     │
│                                                              │
│  Step 1: Primary停止の確認                                   │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ # Primaryに接続できないことを確認                     │   │
│  │ psql -h primary_host -c "SELECT 1"                   │   │
│  │                                                      │   │
│  │ # 可能なら正常停止                                    │   │
│  │ pg_ctl stop -D /var/lib/pgsql/16/data -m fast       │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  Step 2: Standbyを昇格 (Promote)                            │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ # 方法1: pg_ctl                                      │   │
│  │ pg_ctl promote -D /var/lib/pgsql/16/data            │   │
│  │                                                      │   │
│  │ # 方法2: SQL関数                                     │   │
│  │ SELECT pg_promote();                                 │   │
│  │                                                      │   │
│  │ # 確認                                               │   │
│  │ SELECT pg_is_in_recovery(); -- falseになる          │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  Step 3: アプリケーションの接続先変更                        │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ - DNS更新                                            │   │
│  │ - ロードバランサー設定変更                           │   │
│  │ - アプリケーション設定変更                           │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  Step 4: 旧Primaryをスタンバイとして再構築 (後で)           │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 6.2 pg_rewind による再構築

```bash
# 旧Primaryを新Standbyとして再構築
# (データを再コピーせずに同期)

# 前提条件:
# - 旧Primaryがクリーンシャットダウンされている
# - wal_log_hints = on または data_checksums が有効

# 1. 旧Primaryを停止
pg_ctl stop -D /var/lib/pgsql/16/data -m fast

# 2. pg_rewindを実行
pg_rewind --target-pgdata=/var/lib/pgsql/16/data \
    --source-server="host=new_primary port=5432 user=postgres"

# 3. standby.signal を作成
touch /var/lib/pgsql/16/data/standby.signal

# 4. primary_conninfo を設定
cat >> /var/lib/pgsql/16/data/postgresql.auto.conf << EOF
primary_conninfo = 'host=new_primary port=5432 user=repl_user password=xxx'
EOF

# 5. 起動
pg_ctl start -D /var/lib/pgsql/16/data
```

---

## 7. Patroniによる自動フェイルオーバー

### 7.1 Patroniの概要

```
┌──────────────────────────────────────────────────────────────┐
│                      Patroni アーキテクチャ                   │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌─────────────────────────────────────────────────────────┐│
│  │                      etcd / Consul / ZooKeeper          ││
│  │                   (分散キーバリューストア)               ││
│  └────────────┬──────────────┬──────────────┬──────────────┘│
│               │              │              │               │
│  ┌────────────▼───────┐ ┌────▼────────────┐ ┌▼────────────┐ │
│  │    Node 1          │ │    Node 2       │ │   Node 3    │ │
│  │ ┌────────────────┐ │ │ ┌──────────────┐│ │┌───────────┐│ │
│  │ │   Patroni      │ │ │ │  Patroni     ││ ││  Patroni  ││ │
│  │ │   (Agent)      │ │ │ │  (Agent)     ││ ││  (Agent)  ││ │
│  │ └───────┬────────┘ │ │ └──────┬───────┘│ │└─────┬─────┘│ │
│  │         │          │ │        │        │ │      │      │ │
│  │ ┌───────▼────────┐ │ │ ┌──────▼───────┐│ │┌─────▼─────┐│ │
│  │ │  PostgreSQL    │ │ │ │ PostgreSQL   ││ ││PostgreSQL ││ │
│  │ │  (Leader)      │ │ │ │ (Replica)    ││ ││(Replica)  ││ │
│  │ └────────────────┘ │ │ └──────────────┘│ │└───────────┘│ │
│  └────────────────────┘ └─────────────────┘ └─────────────┘ │
│                                                              │
│  【動作】                                                    │
│  1. Patroniがリーダー選出を行い、etcdに登録                  │
│  2. リーダーが障害 → etcdでリーダー不在を検知               │
│  3. 新リーダー選出 → レプリカの1つが昇格                    │
│  4. 旧リーダーが復帰 → 自動的にレプリカとして参加           │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 7.2 Patroniのインストール

```bash
# Pythonパッケージとしてインストール
pip install patroni[etcd]

# または RPM/DEB パッケージ
# RHEL系
sudo dnf install patroni patroni-etcd

# Debian系
sudo apt install patroni
```

### 7.3 Patroni設定ファイル

```yaml
# /etc/patroni/patroni.yml

scope: pg-cluster
name: node1

restapi:
  listen: 0.0.0.0:8008
  connect_address: 192.168.1.11:8008

etcd3:
  hosts:
    - 192.168.1.21:2379
    - 192.168.1.22:2379
    - 192.168.1.23:2379

bootstrap:
  dcs:
    ttl: 30
    loop_wait: 10
    retry_timeout: 10
    maximum_lag_on_failover: 1048576  # 1MB
    postgresql:
      use_pg_rewind: true
      use_slots: true
      parameters:
        wal_level: replica
        hot_standby: "on"
        max_connections: 200
        max_wal_senders: 10
        max_replication_slots: 10
        wal_keep_size: 1GB

  initdb:
    - encoding: UTF8
    - data-checksums

  pg_hba:
    - host replication replicator 192.168.1.0/24 scram-sha-256
    - host all all 192.168.1.0/24 scram-sha-256

  users:
    admin:
      password: admin_password
      options:
        - createrole
        - createdb
    replicator:
      password: repl_password
      options:
        - replication

postgresql:
  listen: 0.0.0.0:5432
  connect_address: 192.168.1.11:5432
  data_dir: /var/lib/pgsql/16/data
  bin_dir: /usr/pgsql-16/bin
  pgpass: /tmp/pgpass
  authentication:
    replication:
      username: replicator
      password: repl_password
    superuser:
      username: postgres
      password: postgres_password
  parameters:
    unix_socket_directories: '/var/run/postgresql'
    shared_buffers: 4GB
    effective_cache_size: 12GB
    work_mem: 64MB

tags:
  nofailover: false
  noloadbalance: false
  clonefrom: false
  nosync: false
```

### 7.4 Patroniの操作

```bash
# サービス起動
sudo systemctl start patroni

# クラスタ状態の確認
patronictl -c /etc/patroni/patroni.yml list

# 出力例:
# + Cluster: pg-cluster (7123456789012345678) ----+----+-----------+
# | Member | Host          | Role    | State     | TL | Lag in MB |
# +--------+---------------+---------+-----------+----+-----------+
# | node1  | 192.168.1.11  | Leader  | running   |  1 |           |
# | node2  | 192.168.1.12  | Replica | streaming |  1 |         0 |
# | node3  | 192.168.1.13  | Replica | streaming |  1 |         0 |
# +--------+---------------+---------+-----------+----+-----------+

# 手動フェイルオーバー
patronictl -c /etc/patroni/patroni.yml failover

# スイッチオーバー (計画的な切り替え)
patronictl -c /etc/patroni/patroni.yml switchover

# ノードの再起動
patronictl -c /etc/patroni/patroni.yml restart pg-cluster node1

# 設定のリロード
patronictl -c /etc/patroni/patroni.yml reload pg-cluster

# クラスタ設定の編集
patronictl -c /etc/patroni/patroni.yml edit-config
```

### 7.5 HAProxy との連携

```
┌──────────────────────────────────────────────────────────────┐
│                  HAProxy + Patroni 構成                      │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│                      ┌──────────────┐                        │
│                      │   HAProxy    │                        │
│                      │  (VIP)       │                        │
│                      └──────┬───────┘                        │
│                 ┌───────────┼───────────┐                    │
│                 │           │           │                    │
│            :5432│      :5433│      :8008│                    │
│          (write)│    (read) │   (health)│                    │
│                 ▼           ▼           ▼                    │
│  ┌──────────────────────────────────────────────────────┐   │
│  │                   PostgreSQL Nodes                    │   │
│  │   Node1 (Leader)  Node2 (Replica)  Node3 (Replica)   │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  【ポート】                                                  │
│  5432: 書き込み用 (Leaderのみ)                              │
│  5433: 読み取り用 (全Replica)                               │
│  8008: Patroni REST API (ヘルスチェック)                    │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

```haproxy
# /etc/haproxy/haproxy.cfg

global
    maxconn 1000

defaults
    mode tcp
    timeout connect 10s
    timeout client 30m
    timeout server 30m

# 書き込み用 (Leaderのみ)
frontend pg_write
    bind *:5432
    default_backend pg_write_backend

backend pg_write_backend
    option httpchk GET /primary
    http-check expect status 200
    default-server inter 3s fall 3 rise 2 on-marked-down shutdown-sessions
    server node1 192.168.1.11:5432 check port 8008
    server node2 192.168.1.12:5432 check port 8008
    server node3 192.168.1.13:5432 check port 8008

# 読み取り用 (全Replica)
frontend pg_read
    bind *:5433
    default_backend pg_read_backend

backend pg_read_backend
    balance roundrobin
    option httpchk GET /replica
    http-check expect status 200
    default-server inter 3s fall 3 rise 2 on-marked-down shutdown-sessions
    server node1 192.168.1.11:5432 check port 8008
    server node2 192.168.1.12:5432 check port 8008
    server node3 192.168.1.13:5432 check port 8008

# 統計情報
frontend stats
    bind *:7000
    mode http
    stats enable
    stats uri /stats
```

---

## 8. 読み取り負荷分散

### 8.1 アプリケーション側での分散

```
┌──────────────────────────────────────────────────────────────┐
│                   読み取り負荷分散の方法                      │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  【方法1】アプリケーションで明示的に分ける                   │
│                                                              │
│  write_conn = connect("primary_host")                        │
│  read_conn  = connect("replica_host")                        │
│                                                              │
│  # 書き込み                                                  │
│  write_conn.execute("INSERT INTO ...")                       │
│                                                              │
│  # 読み取り                                                  │
│  read_conn.execute("SELECT ...")                             │
│                                                              │
│  ──────────────────────────────────────────────             │
│                                                              │
│  【方法2】コネクションプールで分ける                         │
│                                                              │
│  PgBouncer や Pgpool-II で読み取りをレプリカに振り分け      │
│                                                              │
│  ──────────────────────────────────────────────             │
│                                                              │
│  【方法3】libpq の target_session_attrs                      │
│                                                              │
│  # 複数ホスト指定、primary のみに接続                        │
│  host=node1,node2,node3 target_session_attrs=primary        │
│                                                              │
│  # 複数ホスト指定、どれでもOK (読み取り用)                  │
│  host=node1,node2,node3 target_session_attrs=any            │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 8.2 PgBouncerによる負荷分散

```ini
# /etc/pgbouncer/pgbouncer.ini

[databases]
# 書き込み用
mydb_write = host=primary_host port=5432 dbname=mydb

# 読み取り用 (ラウンドロビン)
mydb_read = host=replica1,replica2 port=5432 dbname=mydb

[pgbouncer]
listen_addr = 0.0.0.0
listen_port = 6432
auth_type = scram-sha-256
auth_file = /etc/pgbouncer/userlist.txt
pool_mode = transaction
max_client_conn = 1000
default_pool_size = 20
```

### 8.3 レプリケーションラグの考慮

```sql
-- スタンバイでのラグ確認
SELECT
    CASE
        WHEN pg_last_wal_receive_lsn() = pg_last_wal_replay_lsn() THEN 0
        ELSE EXTRACT(EPOCH FROM now() - pg_last_xact_replay_timestamp())
    END AS lag_seconds;

-- アプリケーション側でラグを許容する例 (擬似コード)
if (query.can_tolerate_lag(seconds=5)) {
    use_replica_connection()
} else {
    use_primary_connection()
}
```

---

## 参考リンク

- [PostgreSQL公式ドキュメント - 高可用性](https://www.postgresql.org/docs/current/high-availability.html)
- [PostgreSQL公式ドキュメント - ストリーミングレプリケーション](https://www.postgresql.org/docs/current/warm-standby.html)
- [PostgreSQL公式ドキュメント - 論理レプリケーション](https://www.postgresql.org/docs/current/logical-replication.html)
- [Patroni Documentation](https://patroni.readthedocs.io/)
- [PgBouncer](https://www.pgbouncer.org/)
- [HAProxy Documentation](https://www.haproxy.org/documentation/)
