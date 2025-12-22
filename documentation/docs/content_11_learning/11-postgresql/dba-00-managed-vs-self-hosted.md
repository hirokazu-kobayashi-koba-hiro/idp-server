# PostgreSQL: マネージドサービス vs セルフホスト比較ガイド

## 概要

本ドキュメントでは、AWS RDS for PostgreSQLなどのマネージドサービスを利用する場合と、
EC2やオンプレミスでセルフホストする場合の設定・運用の違いを明確にします。

```
┌─────────────────────────────────────────────────────────────────┐
│                    PostgreSQL 運用形態比較                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────────────┐    ┌──────────────────────┐          │
│  │    セルフホスト      │    │   マネージドサービス  │          │
│  │  (EC2/オンプレミス)  │    │    (RDS/Aurora)      │          │
│  ├──────────────────────┤    ├──────────────────────┤          │
│  │ ✓ 完全な制御権       │    │ ✓ 運用負荷軽減       │          │
│  │ ✓ カスタマイズ自由   │    │ ✓ 自動バックアップ   │          │
│  │ ✓ コスト最適化可能   │    │ ✓ 自動フェイルオーバ │          │
│  │ ✗ 運用負荷大         │    │ ✗ 制約あり          │          │
│  │ ✗ 専門知識必要       │    │ ✗ コスト高め        │          │
│  └──────────────────────┘    └──────────────────────┘          │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 1. 設定・運用タスク比較一覧

### 凡例
- ✅ **必要**: ユーザーが設定・実施する必要あり
- 🔧 **一部必要**: 部分的に設定が必要
- ❌ **不要/不可**: AWS側で管理、またはアクセス不可
- 📋 **推奨**: 必須ではないが設定を推奨

### 1.1 インストール・初期設定 (dba-01)

| タスク | セルフホスト | RDS | Aurora | 備考 |
|--------|:------------:|:---:|:------:|------|
| OSインストール | ✅ | ❌ | ❌ | RDS: AWSが管理 |
| PostgreSQLインストール | ✅ | ❌ | ❌ | RDS: インスタンス作成時に自動 |
| initdb実行 | ✅ | ❌ | ❌ | RDS: 自動実行 |
| postgresql.conf設定 | ✅ | 🔧 | 🔧 | RDS: Parameter Groupで設定 |
| pg_hba.conf設定 | ✅ | ❌ | ❌ | RDS: Security Groupで制御 |
| データディレクトリ設計 | ✅ | ❌ | ❌ | RDS: EBSストレージ自動管理 |
| WALディレクトリ分離 | ✅ | ❌ | ❌ | RDS: 自動最適化 |
| サービス登録 | ✅ | ❌ | ❌ | RDS: 自動起動管理 |

### 1.2 バックアップ・リカバリ (dba-02)

| タスク | セルフホスト | RDS | Aurora | 備考 |
|--------|:------------:|:---:|:------:|------|
| pg_dump実行 | ✅ | ✅ | ✅ | 論理バックアップは両方で利用可能 |
| pg_basebackup | ✅ | ❌ | ❌ | RDS: スナップショットを使用 |
| WALアーカイブ設定 | ✅ | ❌ | ❌ | RDS: 自動でS3にアーカイブ |
| 自動バックアップ設定 | ✅ | 🔧 | 🔧 | RDS: 保持期間のみ設定 |
| PITR設定 | ✅ | 🔧 | 🔧 | RDS: 有効化と保持期間のみ |
| リストア手順 | ✅ | 🔧 | 🔧 | RDS: コンソール/CLIで実行 |
| バックアップ検証 | ✅ | 📋 | 📋 | RDS: スナップショットから復元テスト推奨 |

### 1.3 レプリケーション・HA (dba-03)

| タスク | セルフホスト | RDS | Aurora | 備考 |
|--------|:------------:|:---:|:------:|------|
| ストリーミングレプリケーション | ✅ | ❌ | ❌ | RDS: リードレプリカで代替 |
| レプリケーションスロット | ✅ | ❌ | ❌ | RDS: 自動管理 |
| 同期レプリケーション設定 | ✅ | ❌ | ❌ | Aurora: 自動同期 |
| リードレプリカ作成 | ✅ | 🔧 | 🔧 | RDS: コンソールから作成 |
| フェイルオーバー設定 | ✅ | ❌ | ❌ | RDS: Multi-AZで自動 |
| Patroni/Pacemaker | ✅ | ❌ | ❌ | RDS: 不要 |
| HAProxy/PgBouncer | ✅ | 🔧 | 🔧 | RDS: RDS Proxyを使用可能 |
| VIP管理 | ✅ | ❌ | ❌ | RDS: エンドポイントで自動切替 |

### 1.4 セキュリティ (dba-04)

| タスク | セルフホスト | RDS | Aurora | 備考 |
|--------|:------------:|:---:|:------:|------|
| pg_hba.conf設定 | ✅ | ❌ | ❌ | RDS: Security Groupで制御 |
| SSL/TLS証明書設定 | ✅ | 🔧 | 🔧 | RDS: AWS提供証明書を使用 |
| ロール・権限設定 | ✅ | ✅ | ✅ | 両方で必要（masterユーザー経由） |
| Row Level Security | ✅ | ✅ | ✅ | 両方で設定可能 |
| pgAudit設定 | ✅ | 🔧 | 🔧 | RDS: Parameter Groupで有効化 |
| ファイアウォール設定 | ✅ | 🔧 | 🔧 | RDS: Security Group/NACL |
| 暗号化設定 | ✅ | 🔧 | 🔧 | RDS: KMSで暗号化（作成時のみ） |
| IAM認証 | ❌ | 🔧 | 🔧 | RDS固有機能 |

### 1.5 監視 (dba-05)

| タスク | セルフホスト | RDS | Aurora | 備考 |
|--------|:------------:|:---:|:------:|------|
| pg_stat_*ビュー参照 | ✅ | ✅ | ✅ | 両方で利用可能 |
| pg_stat_statements設定 | ✅ | 🔧 | 🔧 | RDS: Parameter Groupで有効化 |
| Prometheus/Grafana構築 | ✅ | 📋 | 📋 | RDS: CloudWatchが標準 |
| CloudWatch連携 | ❌ | ✅ | ✅ | RDS: 自動でメトリクス送信 |
| Performance Insights | ❌ | 🔧 | 🔧 | RDS固有機能（有効化推奨） |
| Enhanced Monitoring | ❌ | 🔧 | 🔧 | RDS固有（OS メトリクス） |
| ログ設定 | ✅ | 🔧 | 🔧 | RDS: Parameter Groupで設定 |
| アラート設定 | ✅ | 🔧 | 🔧 | RDS: CloudWatch Alarms使用 |

### 1.6 メンテナンス (dba-06)

| タスク | セルフホスト | RDS | Aurora | 備考 |
|--------|:------------:|:---:|:------:|------|
| VACUUM実行 | ✅ | ✅ | ✅ | autovacuumは両方で動作 |
| VACUUM FULL | ✅ | ✅ | ✅ | 必要に応じて手動実行 |
| ANALYZE | ✅ | ✅ | ✅ | 両方で実行可能 |
| REINDEX | ✅ | ✅ | ✅ | 両方で実行可能 |
| autovacuum調整 | ✅ | 🔧 | 🔧 | RDS: Parameter Groupで設定 |
| pg_repack | ✅ | 🔧 | 🔧 | RDS: 拡張機能として利用可能 |
| PostgreSQLアップグレード | ✅ | 🔧 | 🔧 | RDS: マネージドアップグレード |
| OSパッチ適用 | ✅ | ❌ | ❌ | RDS: メンテナンスウィンドウで自動 |
| ストレージ拡張 | ✅ | 🔧 | ❌ | Aurora: 自動拡張 |

### 1.7 パーティショニング (dba-07)

| タスク | セルフホスト | RDS | Aurora | 備考 |
|--------|:------------:|:---:|:------:|------|
| パーティションテーブル作成 | ✅ | ✅ | ✅ | 両方で同じSQL |
| パーティション追加・削除 | ✅ | ✅ | ✅ | 両方で同じ操作 |
| pg_partman利用 | ✅ | 🔧 | 🔧 | RDS: 拡張機能として利用可能 |
| パーティション監視 | ✅ | ✅ | ✅ | 同じクエリで監視可能 |

---

## 2. AWS RDS 固有の設定ガイド

### 2.1 Parameter Group設定

セルフホストでの `postgresql.conf` に相当する設定は、Parameter Groupで行います。

```
┌─────────────────────────────────────────────────────────────────┐
│                    Parameter Group 構成                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │              Default Parameter Group                     │   │
│  │              (変更不可・参照用)                          │   │
│  └─────────────────────────────────────────────────────────┘   │
│                           │                                     │
│                           ▼ コピーして作成                      │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │           Custom Parameter Group                         │   │
│  │   ┌─────────────────────────────────────────────────┐   │   │
│  │   │ 本番環境用: prod-postgres15-params              │   │   │
│  │   │ ・shared_buffers = {DBInstanceClassMemory/4}    │   │   │
│  │   │ ・max_connections = 200                         │   │   │
│  │   │ ・log_statement = 'ddl'                         │   │   │
│  │   │ ・shared_preload_libraries = 'pg_stat_statements'│   │   │
│  │   └─────────────────────────────────────────────────┘   │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

#### 推奨Parameter Group設定

```bash
# AWS CLIでParameter Group作成
aws rds create-db-parameter-group \
    --db-parameter-group-name prod-postgres15-params \
    --db-parameter-group-family postgres15 \
    --description "Production PostgreSQL 15 parameters"

# パラメータ設定
aws rds modify-db-parameter-group \
    --db-parameter-group-name prod-postgres15-params \
    --parameters \
        "ParameterName=shared_preload_libraries,ParameterValue=pg_stat_statements,ApplyMethod=pending-reboot" \
        "ParameterName=pg_stat_statements.track,ParameterValue=all,ApplyMethod=pending-reboot" \
        "ParameterName=log_statement,ParameterValue=ddl,ApplyMethod=immediate" \
        "ParameterName=log_min_duration_statement,ParameterValue=1000,ApplyMethod=immediate" \
        "ParameterName=log_connections,ParameterValue=1,ApplyMethod=immediate" \
        "ParameterName=log_disconnections,ParameterValue=1,ApplyMethod=immediate"
```

#### 重要パラメータ対応表

| セルフホスト設定 | RDS Parameter Group | 備考 |
|------------------|---------------------|------|
| shared_buffers | shared_buffers | `{DBInstanceClassMemory/N}` 形式で設定 |
| effective_cache_size | effective_cache_size | 同上 |
| work_mem | work_mem | 数値で設定（KB単位） |
| maintenance_work_mem | maintenance_work_mem | 同上 |
| max_connections | max_connections | インスタンスクラスに応じて調整 |
| log_statement | log_statement | none/ddl/mod/all |
| shared_preload_libraries | shared_preload_libraries | 再起動必要 |

### 2.2 Security Group設定

セルフホストでの `pg_hba.conf` に相当するアクセス制御は、Security Groupで行います。

```
┌─────────────────────────────────────────────────────────────────┐
│                    Security Group 構成例                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ Security Group: rds-postgres-prod-sg                     │   │
│  ├─────────────────────────────────────────────────────────┤   │
│  │ Inbound Rules:                                           │   │
│  │ ┌───────────────────────────────────────────────────┐   │   │
│  │ │ Type       │ Port │ Source                       │   │   │
│  │ ├───────────────────────────────────────────────────┤   │   │
│  │ │ PostgreSQL │ 5432 │ app-server-sg (アプリ用)     │   │   │
│  │ │ PostgreSQL │ 5432 │ bastion-sg (踏み台用)        │   │   │
│  │ │ PostgreSQL │ 5432 │ 10.0.0.0/16 (VPC内部)        │   │   │
│  │ └───────────────────────────────────────────────────┘   │   │
│  │                                                          │   │
│  │ Outbound Rules:                                          │   │
│  │ ┌───────────────────────────────────────────────────┐   │   │
│  │ │ Type       │ Port │ Destination                  │   │   │
│  │ ├───────────────────────────────────────────────────┤   │   │
│  │ │ All        │ All  │ 0.0.0.0/0                    │   │   │
│  │ └───────────────────────────────────────────────────┘   │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

```bash
# Security Group作成
aws ec2 create-security-group \
    --group-name rds-postgres-prod-sg \
    --description "Security group for production RDS PostgreSQL" \
    --vpc-id vpc-xxxxxxxx

# インバウンドルール追加（アプリサーバーからのアクセス許可）
aws ec2 authorize-security-group-ingress \
    --group-id sg-xxxxxxxx \
    --protocol tcp \
    --port 5432 \
    --source-group sg-app-server
```

### 2.3 バックアップ設定

```
┌─────────────────────────────────────────────────────────────────┐
│                    RDS バックアップ戦略                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ 自動バックアップ (Automated Backups)                      │  │
│  │ ・保持期間: 1-35日 (本番: 7日以上推奨)                   │  │
│  │ ・バックアップウィンドウ: 業務時間外に設定               │  │
│  │ ・PITR: 5分前まで復元可能                                │  │
│  └──────────────────────────────────────────────────────────┘  │
│                           │                                     │
│                           ▼                                     │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ 手動スナップショット (Manual Snapshots)                   │  │
│  │ ・保持期間: 無制限                                        │  │
│  │ ・メジャーアップグレード前に取得推奨                      │  │
│  │ ・クロスリージョンコピー可能                              │  │
│  └──────────────────────────────────────────────────────────┘  │
│                           │                                     │
│                           ▼                                     │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ 論理バックアップ (pg_dump)                                │  │
│  │ ・特定テーブル/スキーマのみ必要な場合                    │  │
│  │ ・クロスエンジン移行時                                    │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

```bash
# 自動バックアップ設定変更
aws rds modify-db-instance \
    --db-instance-identifier prod-postgres \
    --backup-retention-period 14 \
    --preferred-backup-window "03:00-04:00" \
    --apply-immediately

# 手動スナップショット作成
aws rds create-db-snapshot \
    --db-instance-identifier prod-postgres \
    --db-snapshot-identifier prod-postgres-before-upgrade-20240115

# PITRによる復元
aws rds restore-db-instance-to-point-in-time \
    --source-db-instance-identifier prod-postgres \
    --target-db-instance-identifier prod-postgres-restored \
    --restore-time "2024-01-15T10:30:00Z"
```

### 2.4 監視設定

```
┌─────────────────────────────────────────────────────────────────┐
│                    RDS 監視アーキテクチャ                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐        │
│  │    RDS      │───▶│ CloudWatch  │───▶│   Alarms    │        │
│  │  Instance   │    │  Metrics    │    │   (SNS)     │        │
│  └─────────────┘    └─────────────┘    └─────────────┘        │
│         │                  │                   │               │
│         ▼                  ▼                   ▼               │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐        │
│  │ Performance │    │ CloudWatch  │    │   Lambda    │        │
│  │  Insights   │    │    Logs     │    │  (自動対応) │        │
│  └─────────────┘    └─────────────┘    └─────────────┘        │
│         │                                                      │
│         ▼                                                      │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │              pg_stat_statements (手動クエリ分析)         │  │
│  └─────────────────────────────────────────────────────────┘  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

#### CloudWatch Alarms推奨設定

```bash
# CPU使用率アラーム
aws cloudwatch put-metric-alarm \
    --alarm-name "RDS-Postgres-HighCPU" \
    --alarm-description "CPU utilization exceeds 80%" \
    --metric-name CPUUtilization \
    --namespace AWS/RDS \
    --statistic Average \
    --period 300 \
    --threshold 80 \
    --comparison-operator GreaterThanThreshold \
    --dimensions Name=DBInstanceIdentifier,Value=prod-postgres \
    --evaluation-periods 3 \
    --alarm-actions arn:aws:sns:ap-northeast-1:123456789:db-alerts

# ストレージ空き容量アラーム
aws cloudwatch put-metric-alarm \
    --alarm-name "RDS-Postgres-LowStorage" \
    --alarm-description "Free storage space below 10GB" \
    --metric-name FreeStorageSpace \
    --namespace AWS/RDS \
    --statistic Average \
    --period 300 \
    --threshold 10737418240 \
    --comparison-operator LessThanThreshold \
    --dimensions Name=DBInstanceIdentifier,Value=prod-postgres \
    --evaluation-periods 1 \
    --alarm-actions arn:aws:sns:ap-northeast-1:123456789:db-alerts

# レプリケーション遅延アラーム
aws cloudwatch put-metric-alarm \
    --alarm-name "RDS-Postgres-ReplicaLag" \
    --alarm-description "Replica lag exceeds 60 seconds" \
    --metric-name ReplicaLag \
    --namespace AWS/RDS \
    --statistic Average \
    --period 60 \
    --threshold 60 \
    --comparison-operator GreaterThanThreshold \
    --dimensions Name=DBInstanceIdentifier,Value=prod-postgres-replica \
    --evaluation-periods 3 \
    --alarm-actions arn:aws:sns:ap-northeast-1:123456789:db-alerts

# コネクション数アラーム
aws cloudwatch put-metric-alarm \
    --alarm-name "RDS-Postgres-HighConnections" \
    --alarm-description "Database connections exceed 150" \
    --metric-name DatabaseConnections \
    --namespace AWS/RDS \
    --statistic Average \
    --period 60 \
    --threshold 150 \
    --comparison-operator GreaterThanThreshold \
    --dimensions Name=DBInstanceIdentifier,Value=prod-postgres \
    --evaluation-periods 3 \
    --alarm-actions arn:aws:sns:ap-northeast-1:123456789:db-alerts
```

#### Performance Insights有効化

```bash
# Performance Insights有効化
aws rds modify-db-instance \
    --db-instance-identifier prod-postgres \
    --enable-performance-insights \
    --performance-insights-retention-period 7 \
    --apply-immediately
```

### 2.5 RDS拡張機能の有効化

RDSで利用可能な主要な拡張機能:

```sql
-- 利用可能な拡張機能一覧
SELECT * FROM pg_available_extensions ORDER BY name;

-- 推奨拡張機能のインストール
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;  -- クエリ統計
CREATE EXTENSION IF NOT EXISTS pgcrypto;            -- 暗号化関数
CREATE EXTENSION IF NOT EXISTS pg_trgm;             -- 類似検索
CREATE EXTENSION IF NOT EXISTS btree_gin;           -- GINインデックス拡張
CREATE EXTENSION IF NOT EXISTS pg_repack;           -- オンラインREINDEX
CREATE EXTENSION IF NOT EXISTS pgaudit;             -- 監査ログ

-- pg_partman（パーティション管理）
CREATE SCHEMA partman;
CREATE EXTENSION pg_partman WITH SCHEMA partman;
```

---

## 3. RDSでのメンテナンス実施ガイド

### 3.1 VACUUM/ANALYZE

RDSでもautovacuumは動作しますが、手動実行が必要な場合があります。

```sql
-- テーブル別のVACUUM状態確認
SELECT
    schemaname,
    relname,
    n_live_tup,
    n_dead_tup,
    round(n_dead_tup::numeric / nullif(n_live_tup + n_dead_tup, 0) * 100, 2) as dead_ratio,
    last_vacuum,
    last_autovacuum,
    last_analyze,
    last_autoanalyze
FROM pg_stat_user_tables
WHERE n_dead_tup > 10000
ORDER BY n_dead_tup DESC;

-- 手動VACUUM（大量削除後などに実行）
VACUUM VERBOSE your_table;

-- VACUUM ANALYZE（統計情報も更新）
VACUUM ANALYZE your_table;

-- VACUUM FULL（テーブルサイズを縮小、排他ロックに注意）
-- メンテナンスウィンドウ中に実行推奨
VACUUM FULL your_table;
```

### 3.2 REINDEX

```sql
-- インデックスの肥大化確認
SELECT
    schemaname,
    tablename,
    indexname,
    pg_size_pretty(pg_relation_size(indexrelid)) as index_size
FROM pg_stat_user_indexes
ORDER BY pg_relation_size(indexrelid) DESC
LIMIT 20;

-- 通常のREINDEX
REINDEX INDEX your_index;
REINDEX TABLE your_table;

-- pg_repackを使用したオンラインREINDEX（RDSで利用可能）
-- 事前にpg_repack拡張のインストールが必要
-- psqlから実行
-- pg_repack -d your_database -t your_table
```

### 3.3 PostgreSQLアップグレード

```
┌─────────────────────────────────────────────────────────────────┐
│                    RDSアップグレード手順                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. 事前準備                                                    │
│     ┌─────────────────────────────────────────────────────┐    │
│     │ ・Parameter Groupの互換性確認                       │    │
│     │ ・拡張機能の互換性確認                              │    │
│     │ ・手動スナップショット取得                          │    │
│     │ ・メンテナンスウィンドウ調整                        │    │
│     └─────────────────────────────────────────────────────┘    │
│                           │                                     │
│                           ▼                                     │
│  2. マイナーバージョンアップグレード                            │
│     ┌─────────────────────────────────────────────────────┐    │
│     │ aws rds modify-db-instance \                        │    │
│     │     --db-instance-identifier prod-postgres \        │    │
│     │     --engine-version 15.4 \                         │    │
│     │     --apply-immediately                             │    │
│     └─────────────────────────────────────────────────────┘    │
│                           │                                     │
│                           ▼                                     │
│  3. メジャーバージョンアップグレード                            │
│     ┌─────────────────────────────────────────────────────┐    │
│     │ ・新Parameter Group作成（新バージョン用）           │    │
│     │ ・Blue/Greenデプロイメント推奨                      │    │
│     │ ・ダウンタイムあり（インスタンスサイズによる）      │    │
│     └─────────────────────────────────────────────────────┘    │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

```bash
# アップグレードパスの確認
aws rds describe-db-engine-versions \
    --engine postgres \
    --engine-version 14.9 \
    --query 'DBEngineVersions[].ValidUpgradeTarget[].EngineVersion'

# Blue/Greenデプロイメント作成
aws rds create-blue-green-deployment \
    --blue-green-deployment-name postgres-upgrade-15 \
    --source arn:aws:rds:ap-northeast-1:123456789:db:prod-postgres \
    --target-engine-version 15.4 \
    --target-db-parameter-group-name prod-postgres15-params
```

---

## 4. コスト最適化のポイント

### 4.1 インスタンスサイジング

```
┌─────────────────────────────────────────────────────────────────┐
│              RDSインスタンスサイジングガイド                    │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ワークロード特性に応じた選択:                                  │
│                                                                 │
│  ┌────────────────┬─────────────────────────────────────────┐  │
│  │ パターン       │ 推奨インスタンス                        │  │
│  ├────────────────┼─────────────────────────────────────────┤  │
│  │ 開発/テスト    │ db.t3.micro - db.t3.medium             │  │
│  │ 小規模本番     │ db.t3.large - db.r6g.large             │  │
│  │ 中規模本番     │ db.r6g.xlarge - db.r6g.2xlarge         │  │
│  │ 大規模本番     │ db.r6g.4xlarge以上                     │  │
│  │ メモリ集約型   │ db.r6g系 (メモリ最適化)                │  │
│  │ CPU集約型      │ db.m6g系 (汎用)                        │  │
│  └────────────────┴─────────────────────────────────────────┘  │
│                                                                 │
│  コスト削減オプション:                                          │
│  ・Reserved Instances: 1-3年契約で最大60%割引                  │
│  ・Aurora Serverless v2: 可変ワークロード向け                  │
│  ・リードレプリカ: 読み取り負荷分散                            │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 4.2 ストレージ最適化

```bash
# ストレージ使用状況確認
aws cloudwatch get-metric-statistics \
    --namespace AWS/RDS \
    --metric-name FreeStorageSpace \
    --dimensions Name=DBInstanceIdentifier,Value=prod-postgres \
    --start-time $(date -u -v-7d +%Y-%m-%dT%H:%M:%SZ) \
    --end-time $(date -u +%Y-%m-%dT%H:%M:%SZ) \
    --period 86400 \
    --statistics Average

# gp3ストレージへの移行（コスト削減）
aws rds modify-db-instance \
    --db-instance-identifier prod-postgres \
    --storage-type gp3 \
    --allocated-storage 100 \
    --iops 3000 \
    --storage-throughput 125 \
    --apply-immediately
```

---

## 5. セルフホストとRDSの判断基準

### 5.1 RDSを選ぶべきケース

| 条件 | 理由 |
|------|------|
| DBA専任者がいない | 運用負荷を大幅軽減 |
| 高可用性が必須 | Multi-AZ/Aurora で簡単に実現 |
| スケールが予測しにくい | Aurora Serverless で自動スケール |
| セキュリティ監査対応 | AWS認証取得済み基盤 |
| 素早く立ち上げたい | 数クリックでProduction Ready |

### 5.2 セルフホストを選ぶべきケース

| 条件 | 理由 |
|------|------|
| コスト最優先 | EC2+EBS は RDS より安価 |
| 特殊な拡張機能が必要 | RDS非対応の拡張機能利用 |
| OSレベルのカスタマイズ | カーネルパラメータ調整等 |
| PostgreSQLの最新機能利用 | RDS対応前の新バージョン |
| データ主権要件 | 特定リージョン外への配置禁止 |

---

## 6. 移行ガイド

### 6.1 セルフホスト → RDS移行

```
┌─────────────────────────────────────────────────────────────────┐
│              セルフホスト → RDS 移行手順                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Step 1: 事前準備                                               │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ ・RDSインスタンス作成（同バージョン）                    │   │
│  │ ・Parameter Group設定（現行設定を移植）                  │   │
│  │ ・Security Group設定                                      │   │
│  │ ・拡張機能の互換性確認                                    │   │
│  └─────────────────────────────────────────────────────────┘   │
│                           │                                     │
│                           ▼                                     │
│  Step 2: データ移行                                             │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ 方式A: pg_dump/pg_restore (小〜中規模)                   │   │
│  │   pg_dump -Fc source_db | pg_restore -d target_db       │   │
│  │                                                          │   │
│  │ 方式B: AWS DMS (大規模・継続レプリケーション)            │   │
│  │   ・CDC（Change Data Capture）で同期                     │   │
│  │   ・ダウンタイム最小化                                   │   │
│  └─────────────────────────────────────────────────────────┘   │
│                           │                                     │
│                           ▼                                     │
│  Step 3: 検証・切替                                             │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │ ・データ整合性検証                                        │   │
│  │ ・アプリケーション接続テスト                              │   │
│  │ ・パフォーマンステスト                                    │   │
│  │ ・DNS/接続文字列切替                                      │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 6.2 RDS → セルフホスト移行

```bash
# スナップショットからエクスポート
aws rds start-export-task \
    --export-task-identifier my-snapshot-export \
    --source-arn arn:aws:rds:ap-northeast-1:123456789:snapshot:prod-snapshot \
    --s3-bucket-name my-export-bucket \
    --iam-role-arn arn:aws:iam::123456789:role/rds-s3-export-role \
    --kms-key-id arn:aws:kms:ap-northeast-1:123456789:key/xxx

# または pg_dump で論理バックアップ
pg_dump -h prod-postgres.xxxxx.ap-northeast-1.rds.amazonaws.com \
    -U masteruser \
    -Fc \
    -f backup.dump \
    production_db
```

---

## 7. チェックリスト

### 7.1 RDS初期構築チェックリスト

- [ ] VPC/サブネットグループ設計完了
- [ ] Security Group作成・設定完了
- [ ] Parameter Group作成・設定完了
- [ ] Multi-AZ有効化（本番環境）
- [ ] 自動バックアップ設定（保持期間7日以上）
- [ ] 暗号化有効化
- [ ] Performance Insights有効化
- [ ] Enhanced Monitoring有効化
- [ ] CloudWatch Alarms設定完了
- [ ] pg_stat_statements有効化
- [ ] メンテナンスウィンドウ設定

### 7.2 RDS運用チェックリスト（週次）

- [ ] CloudWatchメトリクス確認
- [ ] Performance Insightsでスロークエリ確認
- [ ] ストレージ使用量確認
- [ ] レプリケーション遅延確認（リードレプリカ使用時）
- [ ] バックアップ成功確認

### 7.3 RDS運用チェックリスト（月次）

- [ ] 手動スナップショット取得
- [ ] パラメータ設定見直し
- [ ] 未使用リソース確認（コスト最適化）
- [ ] セキュリティパッチ確認
- [ ] Reserved Instance更新検討

---

## まとめ

AWS RDSを利用することで、DBAの運用負荷を大幅に軽減できます。
ただし、以下の点は引き続きユーザーの責任となります：

1. **アプリケーションレベルの最適化**: クエリチューニング、インデックス設計
2. **論理的なセキュリティ**: ロール設計、RLS、アプリケーション認証
3. **データ管理**: パーティショニング、アーカイブ戦略
4. **メンテナンス作業**: VACUUM/ANALYZE、REINDEX（autovacuum補助）
5. **監視とアラート対応**: CloudWatch Alarmsへの対応

本ドキュメントを参考に、マネージドサービスの恩恵を最大限活用しながら、
効率的なPostgreSQL運用を実現してください。
