# データベース（RDS/Aurora/DynamoDB）

AWSは用途に応じた多様なデータベースサービスを提供しています。リレーショナルデータベースのRDS/Aurora、NoSQLのDynamoDB、インメモリキャッシュのElastiCacheなど、アプリケーションの要件に最適なデータベースを選択できます。ここでは各サービスの特徴と使い分けを学びます。

---

## 所要時間
約60分

## 学べること
- AWSデータベースサービスの全体像と分類
- RDSの基本概念、インスタンスクラス、マルチAZ構成
- Amazon Auroraのアーキテクチャと特徴
- DynamoDBの基本概念とキャパシティモード
- ElastiCacheによるキャッシュ戦略
- IDサービスにおけるデータベース活用パターン

## 前提知識
- リレーショナルデータベースの基礎（テーブル、SQL、インデックス）
- AWSの基本概念（リージョン、アベイラビリティゾーン）
- VPCとサブネットの基本（前章参照）

---

## 目次
1. [AWSデータベースサービスの全体像](#1-awsデータベースサービスの全体像)
2. [RDS概要](#2-rds概要)
3. [RDSインスタンスクラスとストレージ](#3-rdsインスタンスクラスとストレージ)
4. [マルチAZ配置とリードレプリカ](#4-マルチaz配置とリードレプリカ)
5. [RDSバックアップ](#5-rdsバックアップ)
6. [Amazon Aurora](#6-amazon-aurora)
7. [Aurora Serverless v2](#7-aurora-serverless-v2)
8. [DynamoDB概要](#8-dynamodb概要)
9. [ElastiCache](#9-elasticache)
10. [RDS Proxy](#10-rds-proxy)
11. [IDサービスでの活用](#11-idサービスでの活用)
12. [まとめ](#12-まとめ)

---

## 1. AWSデータベースサービスの全体像

AWSは「Purpose-built databases」の方針のもと、ユースケースごとに最適化されたデータベースサービスを提供しています。

| カテゴリ | サービス | タイプ | 主なユースケース |
|---------|---------|--------|----------------|
| リレーショナル | Amazon RDS | マネージドRDB | 汎用的なトランザクション処理 |
| リレーショナル | Amazon Aurora | 高性能RDB | 高可用性が求められるワークロード |
| キーバリュー | Amazon DynamoDB | NoSQL | 低レイテンシの大規模データアクセス |
| インメモリ | Amazon ElastiCache | キャッシュ | セッション管理、リアルタイムランキング |
| ドキュメント | Amazon DocumentDB | MongoDB互換 | コンテンツ管理、カタログ |
| グラフ | Amazon Neptune | グラフDB | ソーシャルネットワーク、不正検知 |
| 時系列 | Amazon Timestream | 時系列DB | IoTデータ、運用メトリクス |
| 台帳 | Amazon QLDB | 台帳DB | 変更履歴の完全な追跡 |

### データベース選定のフローチャート

```
データベース選定
├── リレーショナルモデルが必要？
│   ├── Yes → 高可用性・高性能が必須？
│   │   ├── Yes → Amazon Aurora
│   │   └── No  → Amazon RDS
│   └── No  → データアクセスパターンは？
│       ├── キーベースの高速アクセス → DynamoDB
│       ├── キャッシュ/セッション    → ElastiCache
│       ├── ドキュメント検索         → DocumentDB
│       └── 関係性の探索             → Neptune
```

---

## 2. RDS概要

Amazon RDS（Relational Database Service）は、リレーショナルデータベースのセットアップ、運用、スケーリングをマネージドで提供するサービスです。

### RDSがマネージドする領域

従来のオンプレミス運用と比較して、RDSが自動化する範囲を理解することが重要です。

```
┌─────────────────────────────────────────────────┐
│              データベース運用タスク                │
├──────────────────────┬──────────────────────────┤
│   ユーザー責任        │   RDSがマネージド         │
├──────────────────────┼──────────────────────────┤
│ アプリケーション最適化 │ OSインストール・パッチ     │
│ クエリチューニング     │ DBエンジンインストール     │
│ スキーマ設計          │ 自動バックアップ           │
│ インデックス管理      │ 高可用性（マルチAZ）       │
│ アクセス制御設定      │ スケーリング               │
│ パラメータ調整        │ モニタリング基盤           │
└──────────────────────┴──────────────────────────┘
```

### 対応データベースエンジン

| エンジン | バージョン例 | 特徴 |
|---------|------------|------|
| PostgreSQL | 13, 14, 15, 16 | 高機能、拡張性、JSON対応 |
| MySQL | 8.0, 8.4 | 広い普及率、軽量 |
| MariaDB | 10.6, 10.11 | MySQL互換、オープンソース |
| Oracle | 19c, 21c | エンタープライズ向け |
| SQL Server | 2019, 2022 | Windows/.NET統合 |

### RDSインスタンスの作成例（AWS CLI）

```bash
aws rds create-db-instance \
  --db-instance-identifier idp-production-db \
  --db-instance-class db.r6g.xlarge \
  --engine postgres \
  --engine-version 16.4 \
  --master-username admin \
  --master-user-password <secure-password> \
  --allocated-storage 100 \
  --storage-type gp3 \
  --vpc-security-group-ids sg-0123456789abcdef0 \
  --db-subnet-group-name idp-db-subnet-group \
  --multi-az \
  --storage-encrypted \
  --kms-key-id alias/idp-rds-key \
  --backup-retention-period 7 \
  --preferred-backup-window "03:00-04:00" \
  --preferred-maintenance-window "Mon:04:00-Mon:05:00"
```

---

## 3. RDSインスタンスクラスとストレージ

### インスタンスクラスの分類

RDSインスタンスクラスは用途に応じて複数のファミリーに分類されます。

| ファミリー | クラス例 | vCPU | メモリ | ユースケース |
|-----------|---------|------|--------|-------------|
| 汎用（M系） | db.m7g.xlarge | 4 | 16 GiB | 一般的なワークロード |
| メモリ最適化（R系） | db.r7g.xlarge | 4 | 32 GiB | メモリ集約型クエリ |
| バースト（T系） | db.t4g.medium | 2 | 4 GiB | 開発/テスト、低負荷 |
| メモリ最適化（X系） | db.x2g.xlarge | 4 | 64 GiB | 超大規模データベース |

### RDSの料金体系

RDSの料金は**インスタンス時間**＋**ストレージ**＋**データ転送**の3要素で構成されます。

| 料金要素 | 課金単位 | 例（東京リージョン概算） |
|---------|---------|----------------------|
| インスタンス | 時間単位 | db.r6g.xlarge: 約$0.50/時間（マルチAZ: 約$1.00/時間） |
| ストレージ | GB/月 | gp3: $0.138/GB/月、io2: $0.138/GB/月 + IOPS課金 |
| バックアップ | GB/月 | DB容量分まで無料、超過分: $0.095/GB/月 |
| データ転送 | GB | 同リージョン内: 無料、リージョン外: $0.09/GB〜 |

```
月額コスト例（本番環境の最小構成）:

  db.r6g.xlarge マルチAZ + gp3 100GB:
    インスタンス: $1.00/時間 × 730時間 = $730
    ストレージ:   $0.138 × 100GB         = $13.8
    バックアップ: 100GBまで無料             = $0
    ─────────────────────────────────
    合計: 約 $744/月

  リードレプリカを追加する場合:
    + db.r6g.xlarge シングルAZ: $0.50/時間 × 730時間 = +$365/月
```

**コスト削減オプション**:
- **リザーブドインスタンス**: 1年/3年の予約で最大40〜60%割引
- **Gravitonインスタンス**: ARM版で同等性能を約20%安く

### Gravitonインスタンス

`g`サフィックスの付いたインスタンスクラス（db.m7g, db.r7gなど）はAWS Gravitonプロセッサ（ARM）を使用しており、同等のx86インスタンスと比較して最大20%のコスト削減と高い性能を実現します。

### ストレージタイプ

| ストレージ | IOPS | スループット | コスト | ユースケース |
|-----------|------|------------|--------|-------------|
| gp3 | 3,000（基本）〜16,000 | 125〜1,000 MB/s | 低 | 汎用的なワークロード |
| gp2 | サイズに比例（最大16,000） | サイズに比例 | 低 | レガシー、gp3移行推奨 |
| io2 | 最大256,000 | 最大4,000 MB/s | 高 | 高IOPSが必要な処理 |
| magnetic | 制限あり | 制限あり | 最低 | 非推奨、下位互換のみ |

### ストレージの自動スケーリング

```
設定例:
  初期サイズ:      100 GiB
  最大サイズ:      500 GiB
  閾値:           残り10%で自動拡張
  拡張単位:        現在のサイズの10%または10GiBの大きい方

┌─────────┐     残容量 < 10%    ┌─────────┐
│ 100 GiB │ ──────────────────→ │ 110 GiB │ ──→ ... ──→ 最大500 GiB
└─────────┘    自動拡張発動      └─────────┘
```

---

## 4. マルチAZ配置とリードレプリカ

### マルチAZ配置（高可用性）

マルチAZ配置では、プライマリインスタンスとは異なるAZにスタンバイインスタンスが自動作成されます。プライマリに障害が発生すると、自動フェイルオーバーによりスタンバイが新しいプライマリに昇格します。

```
                    リージョン（ap-northeast-1）
┌──────────────────────────────────────────────────────┐
│                                                      │
│   AZ-a                          AZ-c                 │
│  ┌──────────────────┐  同期     ┌──────────────────┐  │
│  │  ┌────────────┐  │  レプリ   │  ┌────────────┐  │  │
│  │  │  Primary   │──│──ケーション│──│  Standby   │  │  │
│  │  │  (R/W)     │  │  ─────→  │  │  (待機)     │  │  │
│  │  └────────────┘  │          │  └────────────┘  │  │
│  │  ┌────────────┐  │          │  ┌────────────┐  │  │
│  │  │  EBS Vol   │  │          │  │  EBS Vol   │  │  │
│  │  └────────────┘  │          │  └────────────┘  │  │
│  └──────────────────┘          └──────────────────┘  │
│                                                      │
│         ↑ 障害発生時、DNSエンドポイントが             │
│           自動的にStandbyに切り替わる                 │
│           （フェイルオーバー: 通常60〜120秒）          │
└──────────────────────────────────────────────────────┘
```

### リードレプリカ（読み取りスケーリング）

リードレプリカは読み取り負荷を分散するための非同期レプリカです。マルチAZとは目的が異なります。

```
                 アプリケーション
                 ┌─────────┐
                 │  App    │
                 └────┬────┘
            ┌─────────┴─────────┐
       Write│                   │Read
            ▼                   ▼
   ┌──────────────┐    ┌──────────────┐
   │   Primary    │    │ Read Replica │  ← 最大15個（Aurora）
   │   (R/W)      │───→│   (R only)   │     最大5個（RDS）
   │              │ 非同│              │
   └──────────────┘ 期  └──────────────┘
                        ┌──────────────┐
                   ───→ │ Read Replica │  ← クロスリージョンも可能
                        │   (R only)   │
                        └──────────────┘
```

### マルチAZ vs リードレプリカ

| 項目 | マルチAZ | リードレプリカ |
|------|---------|---------------|
| 目的 | 高可用性（HA） | 読み取りスケーリング |
| レプリケーション | 同期 | 非同期 |
| フェイルオーバー | 自動 | 手動昇格が必要 |
| 読み取りトラフィック | 不可（スタンバイ） | 可能 |
| クロスリージョン | 不可 | 可能 |
| 追加コスト | インスタンス料金 | インスタンス料金 |

---

## 5. RDSバックアップ

### 自動バックアップ

RDSは自動バックアップ機能を標準で提供します。

```
自動バックアップの仕組み:

  Day 1     Day 2     Day 3     Day 4     Day 5
  ┌───┐    ┌───┐    ┌───┐    ┌───┐    ┌───┐
  │ S │    │ S │    │ S │    │ S │    │ S │   S: スナップショット
  └─┬─┘    └─┬─┘    └─┬─┘    └─┬─┘    └─┬─┘
    │        │        │        │        │
  ──┴────────┴────────┴────────┴────────┴──→ 時間
    ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓
    └──────── トランザクションログ ────────┘

  ポイントインタイムリカバリ（PITR）:
  スナップショット + トランザクションログにより、
  保持期間内の任意の時点（5分前まで）に復元可能
```

### バックアップの種類

| 種類 | 自動バックアップ | 手動スナップショット |
|------|----------------|-------------------|
| 実行 | 自動（バックアップウィンドウ） | 手動で任意のタイミング |
| 保持期間 | 1〜35日（デフォルト7日） | 明示的に削除するまで無期限 |
| PITR | 対応 | 非対応（スナップショット時点のみ） |
| 削除時 | DBインスタンス削除で消える | 残る |
| コスト | DBサイズ分まで無料 | ストレージ料金が発生 |

### バックアップのベストプラクティス

```bash
# 手動スナップショットの作成
aws rds create-db-snapshot \
  --db-instance-identifier idp-production-db \
  --db-snapshot-identifier idp-db-before-migration-20260303

# クロスリージョンスナップショットコピー（DR対策）
aws rds copy-db-snapshot \
  --source-db-snapshot-identifier arn:aws:rds:ap-northeast-1:123456789012:snapshot:idp-db-snapshot \
  --target-db-snapshot-identifier idp-db-snapshot-dr \
  --region ap-southeast-1 \
  --kms-key-id alias/idp-rds-key-dr
```

---

## 6. Amazon Aurora

Amazon Auroraは、AWSが独自に開発した高性能リレーショナルデータベースエンジンです。MySQL/PostgreSQL互換でありながら、独自の分散ストレージアーキテクチャにより標準的なRDSの最大5倍（MySQL）/3倍（PostgreSQL）のスループットを実現します。

### Auroraのアーキテクチャ

```
            ┌─────────────────────────────────────────┐
            │            Aurora クラスター              │
            │                                         │
            │  ┌───────────┐    ┌───────────┐         │
            │  │  Writer   │    │  Reader   │ ×最大15  │
            │  │ Instance  │    │ Instance  │         │
            │  └─────┬─────┘    └─────┬─────┘         │
            │        │                │               │
            │        └────────┬───────┘               │
            │                 │                       │
            │  ┌──────────────▼──────────────────┐    │
            │  │     共有分散ストレージボリューム    │    │
            │  │                                 │    │
            │  │   AZ-a      AZ-b      AZ-c     │    │
            │  │  ┌─────┐  ┌─────┐  ┌─────┐    │    │
            │  │  │Copy1│  │Copy3│  │Copy5│    │    │
            │  │  │Copy2│  │Copy4│  │Copy6│    │    │
            │  │  └─────┘  └─────┘  └─────┘    │    │
            │  │                                 │    │
            │  │  6コピー × 3AZ = 高耐久性        │    │
            │  │  4/6書き込みクォーラム            │    │
            │  │  3/6読み取りクォーラム            │    │
            │  └─────────────────────────────────┘    │
            │                                         │
            │  ストレージ: 自動拡張（10GB〜128TB）      │
            └─────────────────────────────────────────┘
```

### Auroraの料金体系

Auroraは標準RDSと異なり、ストレージとI/Oが分離課金される点が特徴です。

| 料金要素 | 課金単位 | 例（東京リージョン概算） |
|---------|---------|----------------------|
| インスタンス | 時間単位 | db.r6g.xlarge: 約$0.58/時間（RDSより約20%高い） |
| ストレージ | GB/月 | $0.12/GB/月（自動拡張、使用分のみ） |
| I/O | 100万リクエスト | Standard: $0.24/100万I/O、I/O-Optimized: I/O無料だがインスタンス30%増 |
| バックアップ | GB/月 | DB容量分まで無料、超過分: $0.023/GB/月 |

```
Auroraの2つの課金モデル:

  Aurora Standard:
    インスタンス料金（低い） + ストレージ + I/O課金
    → I/O量が少ない場合にコスト有利

  Aurora I/O-Optimized:
    インスタンス料金（30%増） + ストレージ + I/O無料
    → I/O量が多い場合にコスト有利（I/O費用がインスタンスの25%を超える場合が目安）
```

**idp-serverの場合**: 認証リクエストによる書き込み（認可コード、トークン発行）が頻繁に発生するため、I/O量が多くなる傾向があります。本番環境ではAurora I/O-Optimizedを検討してください。

### Aurora vs 標準RDS

| 項目 | Amazon Aurora | 標準RDS |
|------|-------------|---------|
| ストレージ | 共有分散（自動拡張） | EBSボリューム（手動管理） |
| レプリカ数 | 最大15 | 最大5 |
| フェイルオーバー時間 | 通常30秒以下 | 通常60〜120秒 |
| レプリケーション遅延 | 通常10ms以下 | 数秒〜数分 |
| バックトラック | 対応（MySQL互換のみ） | 非対応 |
| ストレージ耐久性 | 99.999999999%（11ナイン） | EBS依存 |
| コスト | インスタンス約20%高いがストレージ効率がよい | 標準的 |
| Global Database | 対応 | 非対応 |

### Auroraエンドポイント

Auroraはクラスターレベルで複数のエンドポイントを提供します。

| エンドポイント | 用途 | 接続先 |
|-------------|------|--------|
| クラスターエンドポイント | 書き込み | Writerインスタンス |
| リーダーエンドポイント | 読み取り負荷分散 | Readerインスタンス（ラウンドロビン） |
| インスタンスエンドポイント | 特定インスタンス | 指定したインスタンス |
| カスタムエンドポイント | カスタム分散 | 指定したインスタンスグループ |

---

## 7. Aurora Serverless v2

Aurora Serverless v2は、ワークロードに応じてコンピューティング容量を自動的にスケーリングするAuroraの構成オプションです。

### ACU（Aurora Capacity Units）

Aurora Serverless v2はACUという単位でスケーリングします。1ACUは約2GiBのメモリに対応します。

```
容量の自動スケーリング:

ACU
 16 ┤                        ┌──────┐
    │                       ┌┘      └┐
 12 ┤                      ┌┘        └┐
    │                     ┌┘          └┐
  8 ┤        ┌────┐      ┌┘            └───┐
    │       ┌┘    └┐    ┌┘                 └┐
  4 ┤      ┌┘      └┐  ┌┘                   └──┐
    │     ┌┘        └──┘                       └──
  1 ┤─────┘  最小ACU: 1
    │
  0 ┼──────────────────────────────────────────────→ 時間
        夜間    朝    昼    ピーク    夕方    夜間

  スケーリング: 0.5 ACU単位で段階的に調整
  最小ACU: 0.5〜  最大ACU: 〜256
```

### Aurora Serverless v2の料金

ACU時間単位で課金されます。最小ACUの設定がそのまま最低コストになります。

| 料金要素 | 課金単位 | 例（東京リージョン概算） |
|---------|---------|----------------------|
| ACU | ACU時間 | 約$0.20/ACU/時間 |
| ストレージ | GB/月 | $0.12/GB/月（プロビジョンドと同じ） |
| I/O | 100万リクエスト | プロビジョンドと同じ料金体系 |

```
月額コスト例:

  最小ACU=0.5, 平均ACU=4 の場合（1日の変動想定）:
    深夜帯（8時間）:   0.5 ACU × 8h  × 30日 × $0.20 = $24
    日中（12時間）:    4 ACU   × 12h × 30日 × $0.20 = $288
    ピーク（4時間）:   8 ACU   × 4h  × 30日 × $0.20 = $192
    ストレージ（50GB）:                                 = $6
    ─────────────────────────────────────────────────
    合計: 約 $510/月

  比較: プロビジョンド db.r6g.xlarge（常時8ACU相当）
    $0.58/時間 × 730時間 = $423/月
    → 常時高負荷ならプロビジョンドの方が安い
    → 変動が大きいならServerless v2の方が安い
```

### プロビジョンド vs Serverless v2

| 項目 | プロビジョンド | Serverless v2 |
|------|-------------|---------------|
| スケーリング | 手動（インスタンスクラス変更） | 自動（ACU単位） |
| 課金 | インスタンス時間 | ACU秒単位 |
| 最小コスト | インスタンス分固定 | 最小ACU分のみ |
| 適用場面 | 安定したワークロード | 変動するワークロード |
| 混在利用 | - | プロビジョンドReaderと混在可能 |

---

## 8. DynamoDB概要

Amazon DynamoDBは、フルマネージドのNoSQLデータベースサービスです。キーバリュー型とドキュメント型の両方のデータモデルをサポートし、ミリ秒単位の一貫したレイテンシを提供します。

### DynamoDBの基本構造

```
テーブル: Users
┌───────────────────────────────────────────────────┐
│  Partition Key  │  Sort Key     │  Attributes     │
│  (Hash Key)     │  (Range Key)  │                 │
├─────────────────┼───────────────┼─────────────────┤
│  user-001       │  PROFILE      │  name, email    │
│  user-001       │  SESSION#001  │  created, ttl   │
│  user-001       │  SESSION#002  │  created, ttl   │
│  user-002       │  PROFILE      │  name, email    │
│  user-002       │  SESSION#001  │  created, ttl   │
└─────────────────┴───────────────┴─────────────────┘

  パーティションキー: データの分散を決定（必須）
  ソートキー: パーティション内のデータ順序を定義（オプション）
```

### キャパシティモードと料金

| モード | 特徴 | 課金 | 適用場面 |
|-------|------|------|---------|
| オンデマンド | 自動スケーリング | リクエスト単位 | トラフィック予測困難 |
| プロビジョンド | RCU/WCUを事前指定 | 予約容量 | トラフィック予測可能 |

| 料金要素 | オンデマンド（東京概算） | プロビジョンド（東京概算） |
|---------|----------------------|-----------------------|
| 書き込み | $1.4269/100万書き込み | $0.000742/WCU/時間 |
| 読み取り | $0.285/100万読み取り | $0.000148/RCU/時間 |
| ストレージ | $0.285/GB/月 | $0.285/GB/月 |

```
月額コスト例:

  オンデマンド（1日10万書き込み、50万読み取りの場合）:
    書き込み: 300万/月 × $1.4269/100万  = $4.3
    読み取り: 1500万/月 × $0.285/100万  = $4.3
    ストレージ（10GB）:                    = $2.9
    ───────────────────────────────────
    合計: 約 $12/月（小規模なら非常に安い）

  プロビジョンド（50 WCU, 200 RCU を予約）:
    書き込み: 50 × $0.000742 × 730時間  = $27
    読み取り: 200 × $0.000148 × 730時間 = $21.6
    ストレージ（10GB）:                    = $2.9
    ───────────────────────────────────
    合計: 約 $52/月（予約リザーブドで更に割引可能）
```

DynamoDBはRDS/Auroraと異なりインスタンス管理が不要なため、**小規模では非常に安く、大規模ではリクエスト量に比例**してコストが増加します。

```
プロビジョンドキャパシティの単位:

  RCU (Read Capacity Unit):
    1 RCU = 4KBまでの強い整合性読み込み 1回/秒
    1 RCU = 4KBまでの結果整合性読み込み 2回/秒

  WCU (Write Capacity Unit):
    1 WCU = 1KBまでの書き込み 1回/秒
```

### グローバルセカンダリインデックス（GSI）

プライマリキー以外のアクセスパターンをサポートするためにGSIを活用します。

```bash
# テーブル作成例
aws dynamodb create-table \
  --table-name IdpSessions \
  --attribute-definitions \
    AttributeName=SessionId,AttributeType=S \
    AttributeName=UserId,AttributeType=S \
    AttributeName=ExpiresAt,AttributeType=N \
  --key-schema \
    AttributeName=SessionId,KeyType=HASH \
  --global-secondary-indexes \
    "IndexName=UserIdIndex,KeySchema=[{AttributeName=UserId,KeyType=HASH},{AttributeName=ExpiresAt,KeyType=RANGE}],Projection={ProjectionType=ALL}" \
  --billing-mode PAY_PER_REQUEST
```

### DynamoDB Streams

テーブルへの変更をリアルタイムにキャプチャし、Lambda等で処理できます。

```
┌─────────────┐     変更イベント     ┌──────────────┐     ┌──────────┐
│  DynamoDB    │ ──────────────────→ │  DynamoDB    │ ──→ │  Lambda  │
│  Table       │   INSERT/MODIFY/    │  Streams     │     │ Function │
│              │   REMOVE            │ (24時間保持)  │     │          │
└─────────────┘                     └──────────────┘     └──────────┘
```

---

## 9. ElastiCache

Amazon ElastiCacheは、インメモリデータストアのマネージドサービスです。RedisまたはMemcachedエンジンを選択でき、マイクロ秒レベルのレイテンシでデータにアクセスできます。

### ElastiCacheの料金体系

ElastiCacheはノード時間単位で課金されます。RDS同様、インスタンスタイプで料金が決まります。

| 料金要素 | 課金単位 | 例（東京リージョン概算） |
|---------|---------|----------------------|
| ノード | 時間単位 | cache.r7g.large: 約$0.252/時間 |
| バックアップ | GB/月 | 1スナップショット分は無料、超過分: $0.085/GB/月 |
| データ転送 | GB | 同リージョン内: 無料 |

```
月額コスト例（idp-server向け構成）:

  cache.r7g.large × 2ノード（Primary + Replica, マルチAZ）:
    ノード: $0.252/時間 × 2 × 730時間 = $368/月

  cache.t4g.medium × 2ノード（開発/小規模環境向け）:
    ノード: $0.078/時間 × 2 × 730時間 = $114/月
```

**Serverlessオプション**: ElastiCache Serverlessも提供されており、ECPU（ElastiCache Processing Unit）とストレージのGB/時間で課金されます。トラフィック変動が大きい場合に有効です。

### Redis vs Memcached

| 機能 | Redis（ElastiCache for Redis） | Memcached |
|------|-------------------------------|-----------|
| データ構造 | String, List, Set, Hash, Sorted Set等 | String のみ |
| 永続化 | 対応（RDB/AOF） | 非対応 |
| レプリケーション | 対応 | 非対応 |
| マルチAZ | 対応（自動フェイルオーバー） | 非対応 |
| Pub/Sub | 対応 | 非対応 |
| Lua スクリプト | 対応 | 非対応 |
| 推奨用途 | セッション管理、キャッシュ全般 | 単純なキャッシュ |

### キャッシュ戦略

```
キャッシュアサイドパターン（Lazy Loading）:

  ┌──────┐   1. GET    ┌──────────────┐
  │ App  │ ──────────→ │ ElastiCache  │
  │      │ ←────────── │   (Redis)    │
  │      │   2. Hit    └──────────────┘
  │      │      or
  │      │   2. Miss
  │      │ ──────────→ ┌──────────────┐
  │      │   3. Query  │     RDS      │
  │      │ ←────────── │  (Primary)   │
  │      │   4. Result └──────────────┘
  │      │
  │      │ ──────────→ ┌──────────────┐
  │      │   5. SET    │ ElastiCache  │
  └──────┘  (with TTL) │   (Redis)    │
                       └──────────────┘
```

### Redisクラスター構成

```
ElastiCache for Redis クラスターモード:

  ┌──────────────────────────────────────────┐
  │          Redis Cluster                    │
  │                                          │
  │  Shard 1          Shard 2          ...   │
  │  ┌─────────┐     ┌─────────┐            │
  │  │ Primary │     │ Primary │            │
  │  │(slot 0- │     │(slot    │            │
  │  │  5460)  │     │5461-    │            │
  │  └────┬────┘     │10922)   │            │
  │       │          └────┬────┘            │
  │  ┌────▼────┐     ┌────▼────┐            │
  │  │Replica 1│     │Replica 1│            │
  │  └─────────┘     └─────────┘            │
  │  ┌─────────┐     ┌─────────┐            │
  │  │Replica 2│     │Replica 2│            │
  │  └─────────┘     └─────────┘            │
  └──────────────────────────────────────────┘
```

---

## 10. RDS Proxy

Amazon RDS Proxyは、RDSおよびAuroraのデータベース接続をマネージドで管理するフルマネージドなプロキシサービスです。アプリケーションとデータベースの間に配置し、コネクションプーリングやフェイルオーバーの高速化を提供します。

### RDS Proxyが解決する問題

アプリケーションのインスタンス数が多い環境や、Auto Scalingでインスタンスが増減する環境では、データベースのコネクション管理が課題になります。

```
RDS Proxyなし:

ECS タスク1 ──→ [10接続] ──→
ECS タスク2 ──→ [10接続] ──→  Aurora (最大接続数: 1000)
...                           ↑
ECS タスク100──→ [10接続] ──→  1000接続で上限到達！

問題:
  - Auto Scalingでタスクが増えるとコネクション枯渇
  - タスク終了時にコネクションが残留
  - フェイルオーバー時に全アプリが再接続を試みて輻輳

RDS Proxyあり:

ECS タスク1 ──→ [10接続] ──→
ECS タスク2 ──→ [10接続] ──→  RDS Proxy ──→ [100接続] ──→ Aurora
...                          多重化＆再利用
ECS タスク100──→ [10接続] ──→  1000→100に圧縮
```

### 主な機能

| 機能 | 説明 |
|------|------|
| コネクションプーリング | アプリからの多数の接続をDB側では少数の接続に多重化 |
| コネクション再利用 | アイドル接続を他のリクエストに再利用（ピン留め回避） |
| フェイルオーバー高速化 | DB切り替わり時にアプリの再接続を自動処理（フェイルオーバー時間を最大66%短縮） |
| IAMデータベース認証 | IAMロールでDB認証。パスワード管理が不要 |
| Secrets Manager統合 | DBクレデンシャルをSecrets Managerで自動ローテーション |

### RDS Proxy の構成例

```bash
# RDS Proxy作成
aws rds create-db-proxy \
  --db-proxy-name idp-aurora-proxy \
  --engine-family POSTGRESQL \
  --auth '[{
    "AuthScheme": "SECRETS",
    "SecretArn": "arn:aws:secretsmanager:ap-northeast-1:123456789012:secret:idp-db-credentials",
    "IAMAuth": "REQUIRED"
  }]' \
  --role-arn arn:aws:iam::123456789012:role/idp-rds-proxy-role \
  --vpc-subnet-ids subnet-aaa subnet-bbb \
  --vpc-security-group-ids sg-proxy-xxx
```

### いつ導入すべきか

| 条件 | 推奨 |
|------|------|
| アプリのインスタンス数 x プールサイズ > DBの最大接続数の50% | 導入推奨 |
| Auto Scalingでインスタンスが頻繁に増減する | 導入推奨 |
| Lambda関数からRDS/Auroraに接続する | 強く推奨（Lambda の同時実行で接続が爆発する） |
| 固定3台のアプリで接続数に余裕がある | 不要（アプリ側のプール設定で十分） |

### コストの考え方

RDS ProxyはプロキシインスタンスのvCPU時間で課金されます。コネクション枯渇によるサービス停止のリスクと比較して、コストが妥当かを判断します。目安として、DBインスタンス料金の約30%程度の追加コストになります。

---

## 11. IDサービスでの活用

idp-serverでは、PostgreSQLとMySQLの両データベースエンジンをサポートしています。AWS環境では、これらをRDSまたはAurora上で運用します。

### データベース構成の推奨

```
┌─────────────────────────────────────────────────────┐
│                  idp-server 構成                      │
│                                                     │
│  ┌───────────┐   ┌───────────┐                      │
│  │ idp-srv-1 │   │ idp-srv-2 │   ← ECS/EKS上       │
│  └─────┬─────┘   └─────┬─────┘                      │
│        │               │                            │
│   ┌────┴───────────────┴────┐                       │
│   │                         │                       │
│   ▼                         ▼                       │
│  ┌──────────────┐  ┌──────────────────┐             │
│  │ Aurora       │  │ ElastiCache      │             │
│  │ PostgreSQL   │  │ (Redis)          │             │
│  │              │  │                  │             │
│  │ Writer (AZ-a)│  │ セッションストア    │             │
│  │ Reader (AZ-c)│  │ トークンキャッシュ  │             │
│  │              │  │ レート制限カウンタ  │             │
│  └──────────────┘  └──────────────────┘             │
│                                                     │
│  コアデータ:                キャッシュデータ:           │
│  - テナント設定             - 認証セッション           │
│  - クライアント情報          - アクセストークン         │
│  - 認可コード               - JWKS キャッシュ          │
│  - ユーザー情報             - レートリミット状態        │
└─────────────────────────────────────────────────────┘
```

### RDS/Auroraのセキュリティ設定

idp-serverは認証・認可の基盤サービスであるため、データベースには特に厳格なセキュリティが求められます。

| 項目 | 推奨設定 |
|------|---------|
| ネットワーク | プライベートサブネットのみに配置 |
| 暗号化（保存時） | SSE-KMS有効（カスタマーマネージドキー） |
| 暗号化（転送時） | SSL/TLS接続を強制 |
| 認証 | IAMデータベース認証の活用 |
| 監査 | Database Activity Streams有効化 |
| パラメータ | `log_statement = 'ddl'`、`log_connections = on` |

### Aurora PostgreSQL の接続設定例

idp-serverは独自のデータソース設定（`idp.datasource`）を使用し、**control-plane**（管理API）と**app**（OAuth/OIDC）の2系統をそれぞれwriter/readerで分離しています。

```yaml
# application.yaml - Aurora PostgreSQL 接続設定
idp:
  datasource:
    # 管理API用データソース（テナント設定、クライアント管理等）
    control-plane:
      writer:
        url: jdbc:postgresql://idp-aurora-cluster.cluster-xxxxx.ap-northeast-1.rds.amazonaws.com:5432/idpserver
        username: ${CONTROL_PLANE_DB_WRITER_USER_NAME}
        password: ${CONTROL_PLANE_DB_WRITER_PASSWORD}
        hikari:
          connection-timeout: ${CONTROL_PLANE_DB_WRITER_TIMEOUT:30000}
          maximum-pool-size: ${CONTROL_PLANE_DB_WRITER_MAX_POOL_SIZE:10}
          minimum-idle: ${CONTROL_PLANE_DB_WRITER_MIN_IDLE:5}
          idle-timeout: ${CONTROL_PLANE_DB_WRITER_IDLE_TIMEOUT:600000}
          max-lifetime: ${CONTROL_PLANE_DB_WRITER_MAX_LIFETIME:1800000}
          keepalive-time: ${CONTROL_PLANE_DB_WRITER_KEEPALIVE_TIME:180000}
      reader:
        url: jdbc:postgresql://idp-aurora-cluster.cluster-ro-xxxxx.ap-northeast-1.rds.amazonaws.com:5432/idpserver
        username: ${CONTROL_PLANE_DB_READER_USER_NAME}
        password: ${CONTROL_PLANE_DB_READER_PASSWORD}
        hikari:
          maximum-pool-size: ${CONTROL_PLANE_DB_READER_MAX_POOL_SIZE:10}
          minimum-idle: ${CONTROL_PLANE_DB_READER_MIN_IDLE:5}

    # OAuth/OIDC用データソース（認可コード、トークン、ユーザー情報等）
    app:
      writer:
        url: jdbc:postgresql://idp-aurora-cluster.cluster-xxxxx.ap-northeast-1.rds.amazonaws.com:5432/idpserver
        username: ${DB_WRITER_USER_NAME}
        password: ${DB_WRITER_PASSWORD}
        hikari:
          connection-timeout: ${DB_WRITER_TIMEOUT:30000}
          maximum-pool-size: ${DB_WRITER_MAX_POOL_SIZE:30}
          minimum-idle: ${DB_WRITER_MIN_IDLE:10}
          idle-timeout: ${DB_WRITER_IDLE_TIMEOUT:600000}
          max-lifetime: ${DB_WRITER_MAX_LIFETIME:1800000}
          keepalive-time: ${DB_WRITER_KEEPALIVE_TIME:180000}
      reader:
        url: jdbc:postgresql://idp-aurora-cluster.cluster-ro-xxxxx.ap-northeast-1.rds.amazonaws.com:5432/idpserver
        username: ${DB_READER_USER_NAME}
        password: ${DB_READER_PASSWORD}
        hikari:
          maximum-pool-size: ${DB_READER_MAX_POOL_SIZE:30}
          minimum-idle: ${DB_READER_MIN_IDLE:10}
```

```
接続先の使い分け:
  Writer Endpoint (.cluster-xxxxx...):  INSERT/UPDATE/DELETE
  Reader Endpoint (.cluster-ro-xxxxx...):  SELECT（参照系クエリ）

データソースの使い分け:
  control-plane: テナント設定、クライアント管理等の管理API
    → pool-size小（管理操作は頻度が低い）
  app: 認可コード、トークン、ユーザー情報等のOAuth/OIDC処理
    → pool-size大（認証リクエストは高頻度）
```

### Redis接続設定例

idp-serverはRedisをキャッシュとセッションストアに使用します。それぞれ独立した設定を持ちます。

```yaml
idp:
  # キャッシュ（テナント設定、JWKSキャッシュ等）
  cache:
    enabled: ${CACHE_ENABLE:true}
    timeToLiveSecond: ${CACHE_TIME_TO_LIVE_SECOND:300}
    redis:
      host: idp-redis.xxxxx.apne1.cache.amazonaws.com
      port: ${REDIS_PORT:6379}
      database: ${REDIS_CACHE_DATABASE:0}
      password: ${REDIS_CACHE_PASSWORD:}
      maxTotal: ${REDIS_MAX_TOTAL:20}
      maxIdle: ${REDIS_MAX_IDLE:3}
      minIdle: ${REDIS_MIN_IDLE:2}

  # セッションストア（認証セッション）
  session:
    enabled: ${SESSION_REDIS_ENABLE:true}
    redis:
      host: idp-redis.xxxxx.apne1.cache.amazonaws.com
      port: ${REDIS_PORT:6379}
      database: ${REDIS_SESSION_DATABASE:0}
      password: ${REDIS_SESSION_PASSWORD:}
      maxTotal: ${REDIS_SESSION_MAX_TOTAL:20}
```

---

## 12. まとめ

| サービス | 用途 | idp-serverでの利用 |
|---------|------|-------------------|
| RDS | マネージドRDB（開発/小規模） | 開発・検証環境のPostgreSQL/MySQL |
| Aurora | 高性能RDB（本番） | 本番環境のコアデータストア |
| DynamoDB | NoSQL（キーバリュー） | セッション管理（代替オプション） |
| ElastiCache | インメモリキャッシュ | セッション、トークンキャッシュ |

### コスト比較（東京リージョン、本番最小構成の目安）

| 構成 | 月額概算 | 備考 |
|------|---------|------|
| RDS db.r6g.xlarge マルチAZ + gp3 100GB | 約$744 | 開発/小規模向け |
| Aurora db.r6g.xlarge（Writer+Reader） + 100GB | 約$960 | 本番推奨。I/Oコスト別途 |
| Aurora Serverless v2（0.5〜8 ACU） + 50GB | 約$510 | トラフィック変動が大きい場合 |
| ElastiCache cache.r7g.large × 2ノード | 約$368 | セッション+キャッシュ用 |
| RDS Proxy | 約DBの30% | コネクション管理が必要な場合 |

```
idp-server 本番環境のデータ層コスト目安:

  Aurora PostgreSQL（Writer + Reader）:  約 $960
  ElastiCache Redis（Primary + Replica）: 約 $368
  RDS Proxy（オプション）:                 約 $290
  ──────────────────────────────────────────────
  合計: 約 $1,330〜$1,620/月

  ※ リザーブドインスタンス（1年）適用で 30〜40% 削減可能
```

### 重要なポイント

- RDSはデータベース運用の多くをマネージドするが、スキーマ設計やクエリ最適化はユーザー責任
- Auroraは共有分散ストレージにより高い耐久性とフェイルオーバー性能を実現する
- マルチAZは高可用性、リードレプリカは読み取りスケーリングと目的が異なる
- ElastiCacheを活用してデータベース負荷を軽減し、レスポンスタイムを改善する
- idp-serverのようなセキュリティ基盤では、暗号化・ネットワーク分離・監査ログが必須
- **コスト最適化**: リザーブドインスタンス、Graviton、Aurora I/O-Optimizedを組み合わせて最適化する

## 次のステップ

- [S3・ストレージ](./aws-s3-storage.md): オブジェクトストレージ、設定ファイル管理、監査ログ保存

## 参考リソース

- [Amazon RDS ドキュメント](https://docs.aws.amazon.com/ja_jp/AmazonRDS/latest/UserGuide/)
- [Amazon Aurora ドキュメント](https://docs.aws.amazon.com/ja_jp/AmazonRDS/latest/AuroraUserGuide/)
- [Amazon DynamoDB ドキュメント](https://docs.aws.amazon.com/ja_jp/amazondynamodb/latest/developerguide/)
- [Amazon ElastiCache ドキュメント](https://docs.aws.amazon.com/ja_jp/elasticache/latest/red-ug/)
