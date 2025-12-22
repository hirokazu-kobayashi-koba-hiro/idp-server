# PostgreSQL バックアップとリカバリガイド

このドキュメントでは、PostgreSQLのバックアップ戦略とリカバリ手順を解説します。

---

## 目次

1. [バックアップ戦略の概要](#1-バックアップ戦略の概要)
2. [論理バックアップ (pg_dump/pg_dumpall)](#2-論理バックアップ-pg_dumppg_dumpall)
3. [物理バックアップ (pg_basebackup)](#3-物理バックアップ-pg_basebackup)
4. [WALアーカイブ](#4-walアーカイブ)
5. [PITR (Point-In-Time Recovery)](#5-pitr-point-in-time-recovery)
6. [バックアップの検証](#6-バックアップの検証)
7. [運用スクリプト例](#7-運用スクリプト例)
8. [クラウド環境でのバックアップ](#8-クラウド環境でのバックアップ)

---

## 1. バックアップ戦略の概要

### 1.1 バックアップ方式の比較

```
┌──────────────────────────────────────────────────────────────┐
│                    バックアップ方式の比較                     │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  【論理バックアップ】pg_dump / pg_dumpall                    │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ ✅ 特定のデータベース/テーブルのみ可能                │   │
│  │ ✅ 異なるバージョン間でリストア可能                   │   │
│  │ ✅ 可読なSQLまたはカスタム形式                        │   │
│  │ ❌ 大規模DBでは時間がかかる                           │   │
│  │ ❌ PITR不可                                           │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  【物理バックアップ】pg_basebackup / ファイルシステム        │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ ✅ 高速（ファイルコピー）                             │   │
│  │ ✅ クラスタ全体を一括バックアップ                     │   │
│  │ ✅ PITRが可能（WALアーカイブと組み合わせ）            │   │
│  │ ❌ 同一バージョンでのみリストア                       │   │
│  │ ❌ 特定テーブルのみのリストア不可                     │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  【推奨】両方を組み合わせる                                  │
│  - 日次: pg_basebackup + WALアーカイブ (PITR用)             │
│  - 週次: pg_dump (個別リストア用、長期保存用)               │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 1.2 RPOとRTO

```
┌──────────────────────────────────────────────────────────────┐
│                    RPO と RTO の概念                          │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  【RPO】Recovery Point Objective (目標復旧時点)              │
│  - どこまで古いデータを許容できるか                          │
│  - データ損失の許容量                                        │
│                                                              │
│       障害発生                                               │
│          ↓                                                   │
│  ─────────●───────────────────────────────                   │
│      ←───→                                                   │
│       RPO (例: 1時間分のデータ損失まで許容)                  │
│                                                              │
│  【RTO】Recovery Time Objective (目標復旧時間)               │
│  - 復旧までにどれだけ時間をかけられるか                      │
│  - ダウンタイムの許容量                                      │
│                                                              │
│       障害発生              復旧完了                         │
│          ↓                     ↓                             │
│  ────────●─────────────────────●────                         │
│          ←────────────────────→                              │
│            RTO (例: 4時間以内に復旧)                         │
│                                                              │
│  【バックアップ方式との関係】                                │
│  ┌────────────────────┬──────────┬──────────┐              │
│  │ 方式               │ RPO      │ RTO      │              │
│  ├────────────────────┼──────────┼──────────┤              │
│  │ 日次pg_dump        │ 最大24時間│ 大       │              │
│  │ pg_basebackup+WAL  │ ほぼゼロ │ 中       │              │
│  │ レプリケーション    │ ゼロ     │ 小       │              │
│  └────────────────────┴──────────┴──────────┘              │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## 2. 論理バックアップ (pg_dump/pg_dumpall)

### 2.1 pg_dump の基本

```bash
# 基本的な使い方
pg_dump -h localhost -U postgres -d mydb > mydb_backup.sql

# カスタム形式 (推奨: 圧縮、並列リストア可能)
pg_dump -h localhost -U postgres -Fc -d mydb -f mydb_backup.dump

# ディレクトリ形式 (並列バックアップ可能)
pg_dump -h localhost -U postgres -Fd -j 4 -d mydb -f mydb_backup_dir

# 圧縮レベル指定
pg_dump -h localhost -U postgres -Fc -Z 9 -d mydb -f mydb_backup.dump
```

### 2.2 pg_dump のオプション

```
┌──────────────────────────────────────────────────────────────┐
│                    pg_dump 主要オプション                     │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  【出力形式】                                                │
│  -F, --format=FORMAT                                         │
│      p: plain (SQL文、デフォルト)                            │
│      c: custom (カスタム形式、圧縮対応、推奨)                │
│      d: directory (ディレクトリ形式、並列対応)               │
│      t: tar (tar形式)                                        │
│                                                              │
│  【圧縮】                                                    │
│  -Z, --compress=LEVEL                                        │
│      0-9 (0=なし, 9=最大)                                    │
│                                                              │
│  【並列処理】                                                │
│  -j, --jobs=NUM                                              │
│      並列ワーカー数 (directory形式のみ)                      │
│                                                              │
│  【対象指定】                                                │
│  -n, --schema=SCHEMA      特定スキーマのみ                   │
│  -N, --exclude-schema     特定スキーマを除外                 │
│  -t, --table=TABLE        特定テーブルのみ                   │
│  -T, --exclude-table      特定テーブルを除外                 │
│  --exclude-table-data     データを除外 (スキーマのみ)        │
│                                                              │
│  【その他】                                                  │
│  -s, --schema-only        スキーマのみ (データなし)          │
│  -a, --data-only          データのみ (スキーマなし)          │
│  --no-owner               所有者情報を含めない               │
│  --no-privileges          権限情報を含めない                 │
│  -v, --verbose            詳細出力                           │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 2.3 pg_dumpall

```bash
# 全データベース + グローバルオブジェクトのバックアップ
pg_dumpall -h localhost -U postgres > full_backup.sql

# グローバルオブジェクトのみ (ロール、テーブルスペース)
pg_dumpall -h localhost -U postgres --globals-only > globals.sql

# ロールのみ
pg_dumpall -h localhost -U postgres --roles-only > roles.sql
```

### 2.4 リストア (pg_restore)

```bash
# カスタム形式からリストア
pg_restore -h localhost -U postgres -d mydb mydb_backup.dump

# 新規データベースを作成してリストア
createdb -h localhost -U postgres mydb_restored
pg_restore -h localhost -U postgres -d mydb_restored mydb_backup.dump

# 並列リストア
pg_restore -h localhost -U postgres -d mydb -j 4 mydb_backup.dump

# 特定テーブルのみリストア
pg_restore -h localhost -U postgres -d mydb -t users mydb_backup.dump

# クリーンリストア (既存オブジェクトを削除してから)
pg_restore -h localhost -U postgres -d mydb -c mydb_backup.dump

# プレーンSQL形式の場合
psql -h localhost -U postgres -d mydb < mydb_backup.sql
```

### 2.5 pg_restore のオプション

```
┌──────────────────────────────────────────────────────────────┐
│                  pg_restore 主要オプション                    │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  -d, --dbname=DBNAME      リストア先データベース             │
│  -j, --jobs=NUM           並列ワーカー数                     │
│  -c, --clean              既存オブジェクトを削除             │
│  --if-exists              DROP時に存在チェック               │
│  -C, --create             CREATE DATABASE含む                │
│  -n, --schema=SCHEMA      特定スキーマのみ                   │
│  -t, --table=TABLE        特定テーブルのみ                   │
│  -s, --schema-only        スキーマのみ                       │
│  -a, --data-only          データのみ                         │
│  --no-owner               所有者を設定しない                 │
│  --no-privileges          権限を設定しない                   │
│  -l, --list               内容一覧を表示                     │
│  -L, --use-list=FILE      リストアする項目を指定             │
│  -v, --verbose            詳細出力                           │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 2.6 選択的リストア

```bash
# バックアップの内容を確認
pg_restore -l mydb_backup.dump > backup_contents.txt

# backup_contents.txt を編集して不要な行をコメントアウト
# 例: 特定のテーブルだけを残す

# 編集したリストでリストア
pg_restore -h localhost -U postgres -d mydb -L backup_contents.txt mydb_backup.dump
```

---

## 3. 物理バックアップ (pg_basebackup)

### 3.1 pg_basebackup の基本

```bash
# 基本的な使い方
pg_basebackup -h localhost -U postgres -D /backup/base -Fp -Xs -P

# tar形式で圧縮
pg_basebackup -h localhost -U postgres -D /backup/base -Ft -z -Xs -P

# レプリケーションスロットを使用
pg_basebackup -h localhost -U postgres -D /backup/base -Fp -Xs -P -S backup_slot
```

### 3.2 pg_basebackup のオプション

```
┌──────────────────────────────────────────────────────────────┐
│                pg_basebackup 主要オプション                   │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  【接続】                                                    │
│  -h, --host=HOSTNAME      ホスト名                           │
│  -p, --port=PORT          ポート番号                         │
│  -U, --username=NAME      ユーザー名                         │
│                                                              │
│  【出力】                                                    │
│  -D, --pgdata=DIRECTORY   出力ディレクトリ                   │
│  -F, --format=FORMAT                                         │
│      p: plain (デフォルト、ファイルそのまま)                 │
│      t: tar形式                                              │
│  -z, --gzip               tar形式を圧縮                      │
│  -Z, --compress=LEVEL     圧縮レベル (0-9)                   │
│                                                              │
│  【WAL】                                                     │
│  -X, --wal-method=METHOD                                     │
│      n: none (WALを含めない)                                 │
│      f: fetch (バックアップ後に取得)                         │
│      s: stream (並行してストリーム、推奨)                    │
│                                                              │
│  【チェックポイント】                                        │
│  -c, --checkpoint=MODE                                       │
│      fast: 即座にチェックポイント (推奨)                     │
│      spread: 通常のチェックポイント                          │
│                                                              │
│  【その他】                                                  │
│  -P, --progress           進捗表示                           │
│  -v, --verbose            詳細出力                           │
│  -S, --slot=SLOT          レプリケーションスロット使用       │
│  --no-verify-checksums    チェックサム検証をスキップ         │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 3.3 前提条件の設定

```ini
# postgresql.conf
wal_level = replica                 # または logical
max_wal_senders = 10                # バックアップ用の接続を確保
max_replication_slots = 10          # スロットを使用する場合
```

```bash
# pg_hba.conf
# レプリケーション接続を許可
host    replication     backup_user     192.168.1.0/24    scram-sha-256
```

```sql
-- バックアップ用ユーザーの作成
CREATE ROLE backup_user WITH
    LOGIN
    REPLICATION
    PASSWORD 'backup_password';
```

### 3.4 物理バックアップからのリストア

```bash
# 1. PostgreSQLを停止
sudo systemctl stop postgresql-16

# 2. 既存データディレクトリを退避
sudo mv /var/lib/pgsql/16/data /var/lib/pgsql/16/data.old

# 3. バックアップをリストア
sudo cp -r /backup/base /var/lib/pgsql/16/data

# 4. 所有者を変更
sudo chown -R postgres:postgres /var/lib/pgsql/16/data

# 5. PostgreSQLを起動
sudo systemctl start postgresql-16
```

---

## 4. WALアーカイブ

### 4.1 WALアーカイブの設定

```
┌──────────────────────────────────────────────────────────────┐
│                   WALアーカイブの仕組み                       │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  PostgreSQL                       アーカイブ先               │
│  ┌─────────────────┐              ┌─────────────────┐       │
│  │    pg_wal/      │   archive    │  /archive/      │       │
│  │ ┌─────────────┐ │   command    │ ┌─────────────┐ │       │
│  │ │ 00000001... │ │ ──────────→  │ │ 00000001... │ │       │
│  │ │ 00000001... │ │              │ │ 00000001... │ │       │
│  │ │ 00000001... │ │              │ │ 00000001... │ │       │
│  │ └─────────────┘ │              │ └─────────────┘ │       │
│  └─────────────────┘              └─────────────────┘       │
│                                                              │
│  【流れ】                                                    │
│  1. WALセグメントが完了 (16MB到達または切り替え)             │
│  2. archive_commandが実行される                              │
│  3. 成功すればセグメントはアーカイブ済みとマーク             │
│  4. 失敗すればリトライ                                       │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

```ini
# postgresql.conf

# アーカイブモードを有効化
archive_mode = on

# アーカイブコマンド
# %p = WALファイルのフルパス
# %f = WALファイル名のみ
archive_command = 'cp %p /var/lib/pgsql/archive/%f'

# タイムアウト (秒、0=無効)
archive_timeout = 300    # 5分ごとに強制的にWAL切り替え
```

### 4.2 アーカイブ先のバリエーション

```bash
# ローカルディレクトリにコピー
archive_command = 'cp %p /var/lib/pgsql/archive/%f'

# rsync でリモートにコピー
archive_command = 'rsync -a %p backup_server:/archive/%f'

# S3にアップロード
archive_command = 'aws s3 cp %p s3://my-bucket/wal/%f'

# GCSにアップロード
archive_command = 'gsutil cp %p gs://my-bucket/wal/%f'

# pgBackRestを使用
archive_command = 'pgbackrest --stanza=main archive-push %p'

# 複数の場所にコピー (全て成功で成功)
archive_command = 'cp %p /archive/%f && rsync -a %p remote:/archive/%f'
```

### 4.3 アーカイブの監視

```sql
-- 最後にアーカイブされたWAL
SELECT * FROM pg_stat_archiver;

-- アーカイブ待ちのWAL数
SELECT count(*) FROM pg_ls_dir('pg_wal')
WHERE pg_ls_dir ~ '^[0-9A-F]{24}$';

-- 現在のWAL位置
SELECT pg_current_wal_lsn(), pg_walfile_name(pg_current_wal_lsn());
```

```bash
# アーカイブディレクトリの確認
ls -la /var/lib/pgsql/archive/

# アーカイブの容量確認
du -sh /var/lib/pgsql/archive/
```

---

## 5. PITR (Point-In-Time Recovery)

### 5.1 PITRの概念

```
┌──────────────────────────────────────────────────────────────┐
│                    PITRの仕組み                               │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  時間軸                                                      │
│  ─────────────────────────────────────────────────────→      │
│                                                              │
│  ベースバックアップ      誤操作        障害発生             │
│        ↓                  ↓              ↓                  │
│        ●──────────────────●──────────────●                   │
│        │                  │                                  │
│        │  WALアーカイブ   │                                  │
│        │◆◆◆◆◆◆◆◆◆◆◆◆◆◆│                                  │
│        │                  │                                  │
│        │                  ↓                                  │
│        │            ここに復旧！                             │
│        │                                                     │
│        └────────→ ベースバックアップ + WAL適用で復旧        │
│                                                              │
│  【手順】                                                    │
│  1. ベースバックアップをリストア                             │
│  2. recovery.signal ファイルを作成                           │
│  3. postgresql.conf に復旧設定を記述                         │
│  4. PostgreSQLを起動 → WALを適用して復旧                     │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 5.2 PITRの準備

```bash
# 1. ベースバックアップを取得
pg_basebackup -h localhost -U backup_user -D /backup/base_$(date +%Y%m%d) \
    -Fp -Xs -P -c fast

# 2. WALアーカイブが正常に動作していることを確認
ls -la /var/lib/pgsql/archive/

# 3. 復旧ポイントを記録しておく（任意）
psql -c "SELECT pg_create_restore_point('before_migration');"
```

### 5.3 PITRの実行

```bash
# 1. PostgreSQLを停止
sudo systemctl stop postgresql-16

# 2. 現在のデータディレクトリを退避
sudo mv /var/lib/pgsql/16/data /var/lib/pgsql/16/data_damaged

# 3. ベースバックアップをリストア
sudo cp -r /backup/base_20240101 /var/lib/pgsql/16/data

# 4. 所有者を変更
sudo chown -R postgres:postgres /var/lib/pgsql/16/data

# 5. recovery.signal ファイルを作成
sudo touch /var/lib/pgsql/16/data/recovery.signal
sudo chown postgres:postgres /var/lib/pgsql/16/data/recovery.signal

# 6. postgresql.conf に復旧設定を追加
sudo -u postgres cat >> /var/lib/pgsql/16/data/postgresql.conf << 'EOF'

# リカバリ設定
restore_command = 'cp /var/lib/pgsql/archive/%f %p'
recovery_target_time = '2024-01-15 14:30:00+09'
recovery_target_action = 'promote'
EOF

# 7. PostgreSQLを起動
sudo systemctl start postgresql-16

# 8. ログを確認
sudo tail -f /var/lib/pgsql/16/data/log/postgresql-*.log
```

### 5.4 復旧ターゲットのオプション

```ini
# postgresql.conf

# 時刻を指定
recovery_target_time = '2024-01-15 14:30:00+09'

# トランザクションIDを指定
recovery_target_xid = '12345678'

# リストアポイント名を指定
recovery_target_name = 'before_migration'

# LSN (Log Sequence Number) を指定
recovery_target_lsn = '0/1234ABCD'

# 指定ポイントを含むか
recovery_target_inclusive = true   # デフォルト

# 復旧完了後のアクション
recovery_target_action = 'pause'    # 一時停止 (確認用)
recovery_target_action = 'promote'  # 通常運用に昇格
recovery_target_action = 'shutdown' # シャットダウン
```

### 5.5 復旧の確認と完了

```sql
-- 復旧状態の確認 (pauseの場合)
SELECT pg_is_in_recovery();

-- 復旧を完了して通常運用に移行
SELECT pg_wal_replay_resume();

-- または pg_promote() で昇格
SELECT pg_promote();
```

### 5.6 タイムラインの理解

```
┌──────────────────────────────────────────────────────────────┐
│                   タイムラインの分岐                          │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  Timeline 1 (元の履歴)                                       │
│  ──────────────────────────────●────────────→ (障害)        │
│                                │                             │
│                           復旧ポイント                       │
│                                │                             │
│                                └──────────────→             │
│                           Timeline 2 (新しい履歴)            │
│                                                              │
│  【タイムライン】                                            │
│  - PITRを行うと新しいタイムラインが作成される                │
│  - WALファイル名: {タイムラインID}{LSN}                      │
│  - 例: 00000002000000000000001A (タイムライン2)              │
│                                                              │
│  【.history ファイル】                                       │
│  - タイムラインの分岐履歴を記録                              │
│  - 例: 00000002.history                                      │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## 6. バックアップの検証

### 6.1 検証の重要性

```
┌──────────────────────────────────────────────────────────────┐
│                  バックアップ検証の必要性                     │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  「バックアップは取っていたが、リストアできなかった」        │
│  という事故は実際に起きている                                │
│                                                              │
│  【よくある失敗】                                            │
│  - バックアップファイルが破損                                │
│  - 必要なWALファイルが欠落                                   │
│  - アーカイブが失敗していた                                  │
│  - リストア手順を誰も知らない                                │
│  - リストア先の容量不足                                      │
│                                                              │
│  【定期的に検証すべきこと】                                  │
│  □ バックアップからリストアできるか                         │
│  □ リストアにかかる時間 (RTO内か)                           │
│  □ データの整合性                                           │
│  □ 手順書が正確か                                           │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 6.2 論理バックアップの検証

```bash
#!/bin/bash
# backup_verify_logical.sh

BACKUP_FILE="/backup/mydb_$(date +%Y%m%d).dump"
TEST_DB="mydb_verify_$(date +%Y%m%d)"

# テスト用データベースを作成
createdb -h localhost -U postgres $TEST_DB

# リストア
pg_restore -h localhost -U postgres -d $TEST_DB $BACKUP_FILE

# 検証クエリ
psql -h localhost -U postgres -d $TEST_DB << 'EOF'
-- テーブル数の確認
SELECT count(*) AS table_count FROM information_schema.tables
WHERE table_schema NOT IN ('pg_catalog', 'information_schema');

-- 各テーブルの行数
SELECT schemaname, relname, n_live_tup
FROM pg_stat_user_tables
ORDER BY n_live_tup DESC;

-- 主要テーブルのチェックサム (例)
SELECT md5(string_agg(id::text || email, '')) FROM users ORDER BY id;
EOF

# クリーンアップ
dropdb -h localhost -U postgres $TEST_DB

echo "Verification completed successfully"
```

### 6.3 物理バックアップの検証

```bash
#!/bin/bash
# backup_verify_physical.sh

BACKUP_DIR="/backup/base_$(date +%Y%m%d)"
VERIFY_DIR="/tmp/pg_verify"
VERIFY_PORT="5433"

# 検証用ディレクトリを作成
rm -rf $VERIFY_DIR
cp -r $BACKUP_DIR $VERIFY_DIR

# 設定を調整 (ポート変更)
sed -i "s/^port = .*/port = $VERIFY_PORT/" $VERIFY_DIR/postgresql.conf

# リカバリなしで起動 (recovery.signalを作成しない)
# または、最新まで復旧
touch $VERIFY_DIR/recovery.signal
echo "restore_command = 'cp /var/lib/pgsql/archive/%f %p'" >> $VERIFY_DIR/postgresql.conf
echo "recovery_target_action = 'promote'" >> $VERIFY_DIR/postgresql.conf

# 起動
pg_ctl start -D $VERIFY_DIR -o "-p $VERIFY_PORT" -w

# 検証
psql -h localhost -p $VERIFY_PORT -U postgres -c "SELECT count(*) FROM users;"

# 停止とクリーンアップ
pg_ctl stop -D $VERIFY_DIR -m fast
rm -rf $VERIFY_DIR

echo "Physical backup verification completed"
```

### 6.4 自動検証のスケジュール

```bash
# /etc/cron.d/pg_backup_verify

# 毎週日曜日の深夜2時にバックアップ検証
0 2 * * 0 postgres /opt/scripts/backup_verify_physical.sh >> /var/log/pg_backup_verify.log 2>&1
```

---

## 7. 運用スクリプト例

### 7.1 日次論理バックアップスクリプト

```bash
#!/bin/bash
# /opt/scripts/pg_backup_logical.sh

set -e

# 設定
BACKUP_DIR="/backup/logical"
RETENTION_DAYS=7
PG_HOST="localhost"
PG_USER="backup_user"
DATABASES="myapp_prod"

# 日付
DATE=$(date +%Y%m%d_%H%M%S)
LOG_FILE="/var/log/pg_backup/logical_${DATE}.log"

# ログディレクトリ作成
mkdir -p $(dirname $LOG_FILE)
mkdir -p $BACKUP_DIR

exec > >(tee -a $LOG_FILE) 2>&1

echo "=== Logical Backup Started: $(date) ==="

for DB in $DATABASES; do
    BACKUP_FILE="${BACKUP_DIR}/${DB}_${DATE}.dump"

    echo "Backing up database: $DB"

    pg_dump -h $PG_HOST -U $PG_USER \
        -Fc -Z 6 \
        -d $DB \
        -f $BACKUP_FILE

    # サイズ確認
    ls -lh $BACKUP_FILE

    echo "Completed: $DB"
done

# 古いバックアップの削除
echo "Cleaning up old backups (older than $RETENTION_DAYS days)"
find $BACKUP_DIR -name "*.dump" -mtime +$RETENTION_DAYS -delete

echo "=== Logical Backup Completed: $(date) ==="
```

### 7.2 日次物理バックアップスクリプト

```bash
#!/bin/bash
# /opt/scripts/pg_backup_physical.sh

set -e

# 設定
BACKUP_BASE_DIR="/backup/physical"
ARCHIVE_DIR="/var/lib/pgsql/archive"
RETENTION_DAYS=3
PG_HOST="localhost"
PG_USER="backup_user"

# 日付
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="${BACKUP_BASE_DIR}/base_${DATE}"
LOG_FILE="/var/log/pg_backup/physical_${DATE}.log"

# ログディレクトリ作成
mkdir -p $(dirname $LOG_FILE)
mkdir -p $BACKUP_BASE_DIR

exec > >(tee -a $LOG_FILE) 2>&1

echo "=== Physical Backup Started: $(date) ==="

# ベースバックアップ取得
echo "Starting pg_basebackup..."
pg_basebackup -h $PG_HOST -U $PG_USER \
    -D $BACKUP_DIR \
    -Fp -Xs -P \
    -c fast \
    --checkpoint=fast \
    --label="backup_${DATE}"

# バックアップサイズ
du -sh $BACKUP_DIR

# アーカイブWALの確認
echo "Archive WAL files: $(ls $ARCHIVE_DIR | wc -l)"

# 古いバックアップの削除
echo "Cleaning up old backups (older than $RETENTION_DAYS days)"
find $BACKUP_BASE_DIR -maxdepth 1 -name "base_*" -type d -mtime +$RETENTION_DAYS -exec rm -rf {} \;

# 古いWALアーカイブの削除 (最新のバックアップより古いもの)
# pg_archivecleanupを使用
if [ -f "${BACKUP_DIR}/backup_label" ]; then
    START_WAL=$(grep "START WAL LOCATION" ${BACKUP_DIR}/backup_label | awk '{print $6}' | tr -d ')')
    echo "Cleaning WAL archives older than: $START_WAL"
    pg_archivecleanup $ARCHIVE_DIR $START_WAL
fi

echo "=== Physical Backup Completed: $(date) ==="
```

### 7.3 cronでのスケジュール設定

```bash
# /etc/cron.d/pg_backup

# 毎日午前2時に物理バックアップ
0 2 * * * postgres /opt/scripts/pg_backup_physical.sh

# 毎日午前3時に論理バックアップ
0 3 * * * postgres /opt/scripts/pg_backup_logical.sh

# 毎週日曜日の午前4時にバックアップ検証
0 4 * * 0 postgres /opt/scripts/pg_backup_verify.sh
```

---

## 8. クラウド環境でのバックアップ

### 8.1 AWS S3へのバックアップ

```bash
#!/bin/bash
# pg_backup_to_s3.sh

S3_BUCKET="s3://my-pg-backups"
DATE=$(date +%Y%m%d_%H%M%S)

# 論理バックアップをS3へ
pg_dump -h localhost -U postgres -Fc mydb | \
    aws s3 cp - ${S3_BUCKET}/logical/mydb_${DATE}.dump

# 物理バックアップをS3へ (tar形式)
pg_basebackup -h localhost -U backup_user -Ft -z -D - | \
    aws s3 cp - ${S3_BUCKET}/physical/base_${DATE}.tar.gz
```

### 8.2 pgBackRestの使用

```bash
# pgBackRestのインストール (RHEL系)
sudo dnf install pgbackrest

# /etc/pgbackrest/pgbackrest.conf
[global]
repo1-path=/var/lib/pgbackrest
repo1-retention-full=2
repo1-retention-diff=7

[main]
pg1-path=/var/lib/pgsql/16/data

# postgresql.conf
archive_command = 'pgbackrest --stanza=main archive-push %p'

# ステンザの初期化
sudo -u postgres pgbackrest --stanza=main stanza-create

# フルバックアップ
sudo -u postgres pgbackrest --stanza=main --type=full backup

# 差分バックアップ
sudo -u postgres pgbackrest --stanza=main --type=diff backup

# リストア
sudo -u postgres pgbackrest --stanza=main restore

# PITR
sudo -u postgres pgbackrest --stanza=main --type=time \
    --target="2024-01-15 14:30:00" restore
```

### 8.3 バックアップ戦略のまとめ

```
┌──────────────────────────────────────────────────────────────┐
│                  推奨バックアップ戦略                         │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  【小規模 (〜100GB)】                                        │
│  - 日次: pg_dump (カスタム形式)                              │
│  - 保持: 7日間                                               │
│  - オフサイト: S3/GCSにコピー                                │
│                                                              │
│  【中規模 (100GB〜1TB)】                                     │
│  - 日次: pg_basebackup + WALアーカイブ                       │
│  - 週次: pg_dump (長期保存用)                                │
│  - 保持: 物理3日、論理4週間                                  │
│  - PITR: 対応                                                │
│                                                              │
│  【大規模 (1TB〜)】                                          │
│  - pgBackRestまたはBarman使用                                │
│  - 増分/差分バックアップ                                     │
│  - 並列バックアップ/リストア                                 │
│  - S3/GCSへの直接バックアップ                                │
│                                                              │
│  【共通】                                                    │
│  - 定期的なリストア検証                                      │
│  - 監視とアラート                                            │
│  - 手順書の整備                                              │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

## 参考リンク

- [PostgreSQL公式ドキュメント - バックアップとリストア](https://www.postgresql.org/docs/current/backup.html)
- [pg_dump](https://www.postgresql.org/docs/current/app-pgdump.html)
- [pg_basebackup](https://www.postgresql.org/docs/current/app-pgbasebackup.html)
- [WALアーカイブ](https://www.postgresql.org/docs/current/continuous-archiving.html)
- [pgBackRest](https://pgbackrest.org/)
- [Barman](https://www.pgbarman.org/)
