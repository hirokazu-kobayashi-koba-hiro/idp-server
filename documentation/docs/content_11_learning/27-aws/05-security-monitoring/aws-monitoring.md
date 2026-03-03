# 監視・運用（CloudWatch/CloudTrail）

AWSの監視・運用サービスを理解し、IDサービスのメトリクス監視、ログ管理、監査証跡、分散トレーシングを学びます。

---

## 所要時間
約40分

## 学べること
- AWS監視サービスの全体像と使い分け
- CloudWatch Metrics/Alarms/Logs/Dashboardsの活用
- CloudTrailによるAPI監査証跡
- X-Rayによる分散トレーシング
- AWS Configによるリソース変更追跡
- IDサービスにおける監視設計

## 前提知識
- [AWS基礎](../01-fundamentals/aws-fundamentals.md)の知識
- [セキュリティサービス](aws-security-services.md)の基本概念
- ログ管理の基本的な考え方

---

## 目次

1. [AWS監視サービスの全体像](#aws監視サービスの全体像)
2. [CloudWatch Metrics](#cloudwatch-metrics)
3. [CloudWatch Alarms](#cloudwatch-alarms)
4. [CloudWatch Logs](#cloudwatch-logs)
5. [CloudWatch Dashboards](#cloudwatch-dashboards)
6. [CloudTrail](#cloudtrail)
7. [CloudTrail + S3 + Athena での監査ログ分析](#cloudtrail--s3--athena-での監査ログ分析)
8. [Kinesis Firehose によるログ集約](#kinesis-firehose-によるログ集約)
9. [X-Ray](#x-ray)
10. [AWS Config（リソース変更の追跡）](#aws-configリソース変更の追跡)
11. [IDサービスでの活用](#idサービスでの活用)

---

## AWS監視サービスの全体像

### サービス分類表

| サービス | 監視対象 | データの種類 | 主な用途 |
|---------|---------|------------|---------|
| CloudWatch Metrics | リソースのパフォーマンス | 数値メトリクス | CPU、メモリ、レイテンシー監視 |
| CloudWatch Alarms | メトリクスの閾値超過 | アラート | 異常検知、自動対応 |
| CloudWatch Logs | アプリケーション/システムログ | テキストログ | ログ分析、トラブルシューティング |
| CloudTrail | AWS API呼び出し | 監査ログ | セキュリティ監査、変更追跡 |
| X-Ray | リクエストの処理経路 | トレースデータ | パフォーマンス分析、ボトルネック特定 |
| AWS Config | リソースの設定変更 | 設定スナップショット | コンプライアンス、変更管理 |

```
┌─────────────────────────────────────────────────────────────┐
│              AWS 監視サービスの全体像                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  「何が起きているか」を知る:                                  │
│  ┌───────────────────┐  ┌───────────────────┐             │
│  │ CloudWatch Metrics│  │ CloudWatch Logs   │             │
│  │ (数値で把握)      │  │ (テキストで把握)  │             │
│  └─────────┬─────────┘  └─────────┬─────────┘             │
│            │                      │                        │
│            ▼                      ▼                        │
│  ┌───────────────────────────────────────────┐             │
│  │ CloudWatch Dashboards（可視化）            │             │
│  │ CloudWatch Alarms（異常検知→通知）         │             │
│  └───────────────────────────────────────────┘             │
│                                                             │
│  「誰が何をしたか」を知る:                                   │
│  ┌───────────────────┐  ┌───────────────────┐             │
│  │ CloudTrail        │  │ AWS Config        │             │
│  │ (API呼び出し記録) │  │ (設定変更記録)    │             │
│  └───────────────────┘  └───────────────────┘             │
│                                                             │
│  「リクエストがどう処理されたか」を知る:                      │
│  ┌───────────────────┐                                     │
│  │ X-Ray             │                                     │
│  │ (分散トレーシング)│                                     │
│  └───────────────────┘                                     │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## CloudWatch Metrics

CloudWatch Metricsは、AWSリソースとアプリケーションのパフォーマンスデータを収集・可視化するサービスです。

### 名前空間とメトリクス

```
メトリクスの階層構造:

  名前空間（Namespace）
  └── メトリクス名（MetricName）
      └── ディメンション（Dimensions）

  例:
  AWS/ECS
  └── CPUUtilization
      └── ClusterName=idp-cluster, ServiceName=idp-server

  AWS/ApplicationELB
  └── TargetResponseTime
      └── LoadBalancer=app/idp-alb/xxx
```

### 標準メトリクスとカスタムメトリクス

| 種類 | 説明 | 例 | 収集間隔 |
|------|------|------|---------|
| 標準メトリクス | AWSサービスが自動送信 | EC2 CPUUtilization | 5分（基本）/ 1分（詳細） |
| カスタムメトリクス | ユーザーが手動送信 | アプリ固有の指標 | 任意（最小1秒） |

### 主要サービスの標準メトリクス

```
┌─────────────────────────────────────────────────────────────┐
│           主要サービスの標準メトリクス                         │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ECS/Fargate:                                               │
│  ・CPUUtilization（CPU使用率）                              │
│  ・MemoryUtilization（メモリ使用率）                        │
│                                                             │
│  ALB:                                                       │
│  ・RequestCount（リクエスト数）                              │
│  ・TargetResponseTime（レスポンス時間）                     │
│  ・HTTPCode_Target_2XX_Count（成功レスポンス数）            │
│  ・HTTPCode_Target_5XX_Count（サーバーエラー数）            │
│  ・HealthyHostCount（正常ターゲット数）                     │
│  ・UnHealthyHostCount（異常ターゲット数）                   │
│                                                             │
│  RDS/Aurora:                                                │
│  ・DatabaseConnections（DB接続数）                          │
│  ・ReadLatency/WriteLatency（読み書きレイテンシー）         │
│  ・FreeableMemory（利用可能メモリ）                         │
│  ・CPUUtilization（CPU使用率）                              │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### カスタムメトリクスの送信例

```bash
# AWS CLIでカスタムメトリクスを送信
aws cloudwatch put-metric-data \
  --namespace "IDPServer" \
  --metric-name "TokenIssuanceCount" \
  --dimensions Tenant=tenant-001 \
  --value 1 \
  --unit Count

# 複数メトリクスの一括送信
aws cloudwatch put-metric-data \
  --namespace "IDPServer" \
  --metric-data '[
    {
      "MetricName": "AuthenticationLatency",
      "Dimensions": [{"Name": "Tenant", "Value": "tenant-001"}],
      "Value": 145.5,
      "Unit": "Milliseconds"
    },
    {
      "MetricName": "FailedAuthenticationCount",
      "Dimensions": [{"Name": "Tenant", "Value": "tenant-001"}],
      "Value": 3,
      "Unit": "Count"
    }
  ]'
```

---

## CloudWatch Alarms

### アラームの状態

```
アラームの3つの状態:

  ┌──────────┐         ┌──────────┐         ┌──────────┐
  │    OK    │ ──────→ │ ALARM    │ ──────→ │    OK    │
  │ (正常)   │  閾値超過 │ (異常)   │  閾値回復 │ (正常)   │
  └──────────┘         └──────────┘         └──────────┘
                            │
                       ┌────┴────┐
                       │ アクション│
                       │ 実行    │
                       └─────────┘

  INSUFFICIENT_DATA: データ不足で判定不能
```

### アラーム設定例

```
IDサービスの主要アラーム:

┌──────────────────────────────────────────────────────────────┐
│  アラーム名         │ メトリクス        │ 閾値    │ アクション │
├──────────────────────────────────────────────────────────────┤
│  High-CPU           │ ECS CPU使用率     │ > 80%   │ SNS通知   │
│                     │                   │ 5分連続  │ + Auto   │
│                     │                   │         │  Scaling  │
├──────────────────────────────────────────────────────────────┤
│  High-Error-Rate    │ ALB 5XX Count     │ > 10/分 │ SNS通知   │
│                     │                   │ 3分連続  │ (Urgent) │
├──────────────────────────────────────────────────────────────┤
│  High-Latency       │ TargetResponse    │ > 2秒   │ SNS通知   │
│                     │ Time              │ 5分連続  │          │
├──────────────────────────────────────────────────────────────┤
│  DB-Connections     │ DatabaseConnections│ > 100   │ SNS通知   │
│                     │                   │ 3分連続  │          │
├──────────────────────────────────────────────────────────────┤
│  Unhealthy-Targets  │ UnHealthyHost     │ > 0     │ SNS通知   │
│                     │ Count             │ 1分連続  │ (Urgent) │
└──────────────────────────────────────────────────────────────┘
```

### アラームのアクション

| アクション種類 | 内容 | 用途 |
|--------------|------|------|
| SNS通知 | メール、Slack、PagerDuty等に通知 | 運用チームへのアラート |
| Auto Scaling | スケールアウト/イン | 負荷に応じた自動調整 |
| EC2アクション | 停止、終了、再起動 | 異常インスタンスの対処 |
| Systems Manager | Runbook実行 | 自動修復 |

---

## CloudWatch Logs

### ログの構造

```
ログの階層構造:

  ロググループ（Log Group）
  └── ログストリーム（Log Stream）
      └── ログイベント（Log Event）

  例:
  /ecs/idp-server（ロググループ）
  ├── ecs/idp-server/task-id-001（ログストリーム）
  │   ├── 2024-01-15T10:00:00Z INFO  Started...
  │   ├── 2024-01-15T10:00:01Z INFO  Token issued...
  │   └── 2024-01-15T10:00:02Z ERROR Failed auth...
  │
  └── ecs/idp-server/task-id-002（ログストリーム）
      ├── 2024-01-15T10:00:00Z INFO  Started...
      └── ...
```

### ログの保持期間設定

| 保持期間 | 用途 | コスト |
|---------|------|-------|
| 1日 | 開発・デバッグ | 最安 |
| 30日 | 運用ログ | 低 |
| 90日 | セキュリティログ | 中 |
| 1年 | コンプライアンス対応 | 高 |
| 無期限 | 監査要件 | 最高 |

### フィルターパターン

```
メトリクスフィルター: ログからメトリクスを生成

  フィルターパターン例:

  1. エラーログのカウント
     パターン: "ERROR"
     メトリクス: ErrorCount

  2. 認証失敗のカウント
     パターン: "authentication failed"
     メトリクス: AuthFailureCount

  3. 特定のHTTPステータスコード
     パターン: "[..., status=5*, ...]"
     メトリクス: ServerErrorCount

  4. JSON形式のログフィルタリング
     パターン: { $.level = "ERROR" && $.component = "token" }
     メトリクス: TokenErrorCount
```

### CloudWatch Logs Insights

Logs Insightsは、ログデータに対するインタラクティブなクエリ機能です。

```sql
-- 直近1時間のエラーログ上位10件
fields @timestamp, @message
| filter @message like /ERROR/
| sort @timestamp desc
| limit 10

-- エンドポイント別のレスポンス時間統計
fields @timestamp, endpoint, responseTime
| stats avg(responseTime) as avgTime,
        max(responseTime) as maxTime,
        count(*) as requestCount
  by endpoint
| sort avgTime desc

-- 認証失敗の発生頻度（5分ごと）
fields @timestamp
| filter @message like /authentication failed/
| stats count(*) as failureCount by bin(5m)
| sort @timestamp desc

-- 特定テナントのトークン発行数
fields @timestamp, tenantId, grantType
| filter tenantId = "tenant-001"
| filter @message like /token issued/
| stats count(*) as tokenCount by grantType
```

---

## CloudWatch Dashboards

### ダッシュボード構成例

```
┌─────────────────────────────────────────────────────────────┐
│              IDサービス 運用ダッシュボード                     │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─── リクエスト概要 ─────┐  ┌─── エラー率 ──────────┐    │
│  │  リクエスト数/分        │  │  5XXエラー率            │    │
│  │  ████████████ 1,250    │  │  ░░░░░░░░░░░░ 0.1%     │    │
│  │  目標: < 5,000/分      │  │  目標: < 0.5%           │    │
│  └─────────────────────────┘  └─────────────────────────┘   │
│                                                             │
│  ┌─── レスポンス時間 ────┐  ┌─── CPU/メモリ ─────────┐    │
│  │  p50: 45ms            │  │  CPU: ████░░░░ 52%      │    │
│  │  p95: 180ms           │  │  Mem: ██████░░ 68%      │    │
│  │  p99: 450ms           │  │                         │    │
│  └─────────────────────────┘  └─────────────────────────┘   │
│                                                             │
│  ┌─── DB接続 ────────────┐  ┌─── ヘルスチェック ─────┐    │
│  │  アクティブ接続: 45    │  │  Healthy: 4/4          │    │
│  │  最大: 200            │  │  UnHealthy: 0/4        │    │
│  └─────────────────────────┘  └─────────────────────────┘   │
│                                                             │
│  ┌─── 認証メトリクス ──────────────────────────────────┐    │
│  │  認証成功率: 98.5%   トークン発行: 856/5分          │    │
│  │  認証失敗:  12/5分   CIBA: 23/5分                   │    │
│  └──────────────────────────────────────────────────────┘   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## CloudTrail

CloudTrailは、AWSアカウント内のAPI呼び出し（管理操作）を記録するサービスです。

### 記録されるイベントの種類

```
┌─────────────────────────────────────────────────────────────┐
│                  CloudTrail イベント種類                       │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1. 管理イベント（Management Events）                        │
│     AWSリソースに対する管理操作                              │
│     ・CreateBucket, DeleteTable, ModifySecurityGroup        │
│     ・IAMポリシー変更、KMSキー操作                          │
│     ※ デフォルトで記録される                                │
│                                                             │
│  2. データイベント（Data Events）                             │
│     リソース上のデータ操作                                   │
│     ・S3 GetObject/PutObject                                │
│     ・Lambda Invoke                                         │
│     ・DynamoDB GetItem/PutItem                              │
│     ※ 明示的に有効化が必要（大量のため）                    │
│                                                             │
│  3. インサイトイベント（Insights Events）                     │
│     通常と異なるAPI呼び出しパターンの検出                    │
│     ・突然のAPI呼び出し急増                                  │
│     ・エラー率の異常上昇                                     │
│     ※ 有効化が必要                                          │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### CloudTrailログの構造

```json
{
    "eventVersion": "1.08",
    "eventTime": "2024-01-15T10:30:00Z",
    "eventSource": "kms.amazonaws.com",
    "eventName": "Decrypt",
    "awsRegion": "ap-northeast-1",
    "sourceIPAddress": "10.0.1.50",
    "userAgent": "aws-sdk-java/2.20.0",
    "userIdentity": {
        "type": "AssumedRole",
        "arn": "arn:aws:sts::123456789012:assumed-role/idp-server-task-role/task-id",
        "sessionContext": {
            "sessionIssuer": {
                "type": "Role",
                "arn": "arn:aws:iam::123456789012:role/idp-server-task-role"
            }
        }
    },
    "requestParameters": {
        "keyId": "arn:aws:kms:ap-northeast-1:123456789012:key/xxx"
    },
    "responseElements": null,
    "readOnly": true
}
```

### 主な監査対象

| 操作カテゴリ | 監視すべきイベント | 理由 |
|------------|------------------|------|
| IAM | CreateUser, AttachPolicy, CreateAccessKey | 権限昇格の検出 |
| KMS | DisableKey, ScheduleKeyDeletion | 暗号鍵の不正操作 |
| SG | AuthorizeSecurityGroupIngress | ネットワーク設定変更 |
| RDS | ModifyDBInstance, DeleteDBSnapshot | データベース設定変更 |
| S3 | PutBucketPolicy, DeleteBucket | ストレージポリシー変更 |

---

## CloudTrail + S3 + Athena での監査ログ分析

### アーキテクチャ

```
┌──────────┐    ログ配信    ┌──────────┐    クエリ    ┌──────────┐
│CloudTrail│ ──────────── →│ S3バケット│ ←──────────  │  Athena  │
│          │               │ (ログ保存)│              │ (分析)   │
└──────────┘               └──────────┘              └──────────┘

  CloudTrail                S3                        Athena
  ・API呼び出しを記録      ・JSON形式で保存          ・SQLで分析
  ・リアルタイム配信       ・長期保存（低コスト）    ・サーバーレス
  ・90日間の履歴           ・ライフサイクル管理      ・スキャン課金
```

### Athenaクエリ例

```sql
-- KMS鍵へのアクセスログ（直近24時間）
SELECT
    eventTime,
    userIdentity.arn AS caller,
    eventName,
    requestParameters
FROM cloudtrail_logs
WHERE eventSource = 'kms.amazonaws.com'
  AND eventTime > date_add('hour', -24, now())
ORDER BY eventTime DESC;

-- IAM関連の変更操作
SELECT
    eventTime,
    userIdentity.arn AS caller,
    eventName,
    sourceIPAddress
FROM cloudtrail_logs
WHERE eventSource = 'iam.amazonaws.com'
  AND readOnly = false
ORDER BY eventTime DESC
LIMIT 50;

-- 特定ロールによるAPI呼び出し統計
SELECT
    eventSource,
    eventName,
    COUNT(*) AS call_count
FROM cloudtrail_logs
WHERE userIdentity.arn LIKE '%idp-server-task-role%'
  AND eventTime > date_add('day', -7, now())
GROUP BY eventSource, eventName
ORDER BY call_count DESC;
```

---

## Kinesis Firehose によるログ集約

CloudWatch LogsのデータをリアルタイムでS3に配信し、長期保存と低コスト分析を実現するには、Kinesis Data Firehose（Amazon Data Firehose）を活用します。

### CloudWatch Logs → Firehose → S3 パイプライン

CloudWatch Logsのサブスクリプションフィルターを使い、特定のログをFirehoseに流してS3に自動配信します。

```
ECS タスク                CloudWatch Logs            Kinesis Firehose
+-----------+            +------------------+       +------------------+
| アプリログ  | ──awslogs──→ | ロググループ       | ──→  | 自動バッチ化      |
+-----------+            | /ecs/idp-server  |  サブ  | ・60秒 or 1MB     |
                         +------------------+  スクリ | ・gzip圧縮        |
                                               プション| ・Parquet変換     |
                                               フィルタ +--------+---------+
                                                                 |
                                                                 ▼
                                                        +------------------+
                                                        | S3               |
                                                        | year=2024/       |
                                                        |  month=01/       |
                                                        |   day=15/        |
                                                        |    logs.parquet  |
                                                        +--------+---------+
                                                                 |
                                                                 ▼
                                                        +------------------+
                                                        | Athena           |
                                                        | (SQLクエリ分析)   |
                                                        +------------------+
```

### なぜFirehoseを使うのか

CloudWatch Logsに長期間ログを保持するとコストが高くなります。Firehose経由でS3に配信することで、ログの長期保存コストを大幅に削減できます。

| 保存方式 | 1TB/月のコスト目安 | 分析方法 |
|---------|-----------------|---------|
| CloudWatch Logs | $500〜 | Logs Insights（リアルタイム） |
| S3 Standard | $25 | Athena（SQLクエリ） |
| S3 IA | $12.5 | Athena（SQLクエリ） |
| S3 Glacier IR | $4 | Athena（復元後） |

### 設定手順の概要

```bash
# 1. Firehose配信ストリーム作成
aws firehose create-delivery-stream \
  --delivery-stream-name idp-logs-to-s3 \
  --extended-s3-destination-configuration '{
    "RoleARN": "arn:aws:iam::123456789012:role/firehose-role",
    "BucketARN": "arn:aws:s3:::idp-logs-archive",
    "Prefix": "logs/year=!{timestamp:yyyy}/month=!{timestamp:MM}/day=!{timestamp:dd}/",
    "ErrorOutputPrefix": "errors/",
    "BufferingHints": {
      "SizeInMBs": 64,
      "IntervalInSeconds": 60
    },
    "CompressionFormat": "GZIP"
  }'

# 2. CloudWatch Logsサブスクリプションフィルター作成
aws logs put-subscription-filter \
  --log-group-name /ecs/idp-server \
  --filter-name idp-to-firehose \
  --filter-pattern "" \
  --destination-arn arn:aws:firehose:ap-northeast-1:123456789012:deliverystream/idp-logs-to-s3 \
  --role-arn arn:aws:iam::123456789012:role/cwl-to-firehose-role
```

### いつ導入すべきか

| 条件 | 推奨 |
|------|------|
| CloudWatch Logsのコストが月数万円を超え始めた | 導入検討 |
| ログの長期保存（90日以上）が必要 | 導入推奨 |
| S3上のログをAthenaでSQLクエリしたい | 導入推奨 |
| ログ量が少なく、CloudWatch Logs Insightsで十分 | まだ不要 |

---

## X-Ray

X-Rayは、アプリケーションのリクエスト処理経路を可視化する分散トレーシングサービスです。

### サービスマップ

```
┌─────────────────────────────────────────────────────────────┐
│              X-Ray サービスマップ                              │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌──────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐  │
│  │Client│──→│CloudFront│──→│   ALB    │──→│ECS       │  │
│  │      │   │ 2ms      │   │ 1ms      │   │(idp)     │  │
│  └──────┘   └──────────┘   └──────────┘   │ 45ms     │  │
│                                            └────┬─────┘  │
│                                    ┌────────────┼────┐    │
│                                    │            │    │    │
│                                    ▼            ▼    ▼    │
│                              ┌─────────┐ ┌────────┐      │
│                              │  Aurora  │ │  KMS   │      │
│                              │  15ms   │ │  5ms   │      │
│                              └─────────┘ └────────┘      │
│                                                             │
│  各ノードの色:                                               │
│  緑: 正常（エラー率 < 1%）                                  │
│  黄: 警告（エラー率 1-5%）                                  │
│  赤: 異常（エラー率 > 5%）                                  │
│                                                             │
│  エッジ（矢印）の太さ: リクエスト量に比例                   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### トレースの構造

```
トレース（1つのリクエストの全体像）:

  Trace ID: 1-abc123-def456...
  │
  ├── Segment: ALB (1ms)
  │
  ├── Segment: idp-server (45ms)
  │   │
  │   ├── Subsegment: /authorize handler (5ms)
  │   │
  │   ├── Subsegment: Aurora query (15ms)
  │   │   └── SQL: SELECT * FROM clients WHERE...
  │   │
  │   ├── Subsegment: KMS decrypt (5ms)
  │   │
  │   └── Subsegment: JWT signing (3ms)
  │
  └── Total: 48ms

  トレースヘッダー:
  X-Amzn-Trace-Id: Root=1-abc123-def456;Parent=seg789;Sampled=1
```

### X-Rayの活用ポイント

```
IDサービスでの分散トレーシング:

1. ボトルネック特定
   ・レスポンス時間が長いリクエストの原因特定
   ・DB クエリ、外部API呼び出しの遅延分析

2. エラー追跡
   ・5XXエラーの発生箇所を特定
   ・例外のスタックトレース確認

3. サービス依存関係の把握
   ・サービスマップで依存関係を可視化
   ・障害の影響範囲を迅速に特定

4. パフォーマンス分析
   ・p50/p95/p99レイテンシーの分析
   ・アノテーション/メタデータによるフィルタリング
```

---

## AWS Config（リソース変更の追跡）

### CloudTrailとの違い

```
CloudTrail vs AWS Config:

  CloudTrail:
  「いつ、誰が、何のAPIを呼んだか」
  → API呼び出しの記録（イベント中心）

  AWS Config:
  「リソースの設定がどう変わったか」
  → リソース設定の変更履歴（状態中心）

  例: セキュリティグループの変更

  CloudTrail:
  「10:00にユーザーAがAuthorizeSecurityGroupIngressを呼んだ」

  AWS Config:
  「SGの設定が変わった:
   Before: インバウンド [443/tcp from 10.0.0.0/8]
   After:  インバウンド [443/tcp from 10.0.0.0/8, 22/tcp from 0.0.0.0/0]」
```

### Config Rulesによるコンプライアンス評価

```
IDサービスに適用すべきConfig Rules:

┌──────────────────────────────────────────────────────────────┐
│  ルール名                      │ チェック内容                  │
├──────────────────────────────────────────────────────────────┤
│  rds-storage-encrypted         │ RDSストレージが暗号化されているか│
│  rds-multi-az-support          │ RDSがマルチAZ構成か           │
│  alb-waf-enabled               │ ALBにWAFが設定されているか    │
│  encrypted-volumes             │ EBSボリュームが暗号化されているか│
│  restricted-ssh                │ SSHが全IPに開放されていないか │
│  s3-bucket-ssl-requests-only   │ S3がHTTPS必須か              │
│  iam-root-access-key-check     │ rootユーザーのアクセスキーがないか│
│  cloudtrail-enabled            │ CloudTrailが有効か           │
└──────────────────────────────────────────────────────────────┘
```

---

## IDサービスでの活用

### idp-serverの監視設計

```
┌─────────────────────────────────────────────────────────────────┐
│              IDサービス 監視アーキテクチャ                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─── メトリクス監視（CloudWatch Metrics） ──────────────────┐ │
│  │                                                           │ │
│  │  インフラメトリクス:                                       │ │
│  │  ・ECS CPU/メモリ使用率                                   │ │
│  │  ・ALBレスポンスタイム、リクエスト数、エラー率            │ │
│  │  ・Aurora接続数、レイテンシー、レプリカラグ              │ │
│  │                                                           │ │
│  │  アプリケーションメトリクス（カスタム）:                   │ │
│  │  ・認証成功率 / 失敗率（テナント別）                      │ │
│  │  ・トークン発行数（grant_type別）                         │ │
│  │  ・CIBA認証リクエスト数                                   │ │
│  │  ・身元確認リクエスト処理時間                             │ │
│  │                                                           │ │
│  └───────────────────────────────────────────────────────────┘ │
│                                                                 │
│  ┌─── ログ管理（CloudWatch Logs） ───────────────────────────┐ │
│  │                                                           │ │
│  │  ロググループ:                                             │ │
│  │  /ecs/idp-server        : アプリケーションログ            │ │
│  │  /ecs/idp-server/access : アクセスログ                    │ │
│  │  /aws/rds/idp-aurora    : DBスロークエリログ              │ │
│  │                                                           │ │
│  │  メトリクスフィルター:                                     │ │
│  │  ・ERROR → ErrorCount アラーム                            │ │
│  │  ・"authentication failed" → AuthFailure アラーム         │ │
│  │  ・"token issued" → TokenIssuance メトリクス              │ │
│  │                                                           │ │
│  └───────────────────────────────────────────────────────────┘ │
│                                                                 │
│  ┌─── 監査（CloudTrail） ────────────────────────────────────┐ │
│  │                                                           │ │
│  │  重点監視対象:                                             │ │
│  │  ・KMS鍵操作（Decrypt, GenerateDataKey）                  │ │
│  │  ・IAMポリシー変更                                        │ │
│  │  ・セキュリティグループ変更                                │ │
│  │  ・Secrets Managerアクセス                                │ │
│  │                                                           │ │
│  │  長期保存: S3 → Athenaで分析                              │ │
│  │                                                           │ │
│  └───────────────────────────────────────────────────────────┘ │
│                                                                 │
│  ┌─── 分散トレーシング（X-Ray） ─────────────────────────────┐ │
│  │                                                           │ │
│  │  トレース対象:                                             │ │
│  │  ・認可リクエスト（/authorize → DB → レスポンス）        │ │
│  │  ・トークン発行（/token → 検証 → DB → JWT署名）         │ │
│  │  ・CIBA（/bc-authorize → 通知 → ポーリング → 発行）     │ │
│  │                                                           │ │
│  └───────────────────────────────────────────────────────────┘ │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### アラート設計の考え方

```
アラートの優先度分類:

  P1 (Critical) - 即時対応:
  ・全ターゲットがUnhealthy
  ・5XXエラー率 > 10%
  ・DBフェイルオーバー発生
  → PagerDuty / 電話通知

  P2 (High) - 15分以内に対応:
  ・5XXエラー率 > 1%
  ・レスポンスタイム p99 > 5秒
  ・CPU使用率 > 90%（10分継続）
  → Slack #alerts-high + メール

  P3 (Medium) - 1時間以内に確認:
  ・CPU使用率 > 70%（30分継続）
  ・DB接続数 > 70%
  ・認証失敗率の急増
  → Slack #alerts-medium

  P4 (Low) - 翌営業日に確認:
  ・ディスク使用率 > 70%
  ・証明書の有効期限 < 30日
  → Slack #alerts-low
```

---

## まとめ

| サービス | 役割 | IDサービスでの用途 |
|---------|------|-----------------|
| CloudWatch Metrics | パフォーマンス監視 | CPU、レイテンシー、カスタムメトリクス |
| CloudWatch Alarms | 異常検知・通知 | 閾値ベースのアラート |
| CloudWatch Logs | ログ管理・分析 | アプリケーション/アクセスログ |
| CloudTrail | API監査証跡 | セキュリティ監査、変更追跡 |
| X-Ray | 分散トレーシング | リクエスト処理経路の可視化 |
| AWS Config | リソース設定追跡 | コンプライアンス監視 |

重要なポイント:
- CloudWatch Metricsで「今何が起きているか」をリアルタイム把握する
- CloudWatch Logsのメトリクスフィルターでログからアラームを生成する
- CloudTrailで「誰が何をしたか」の監査証跡を残す
- X-Rayでリクエストのボトルネックを特定する
- アラートは優先度を分類し、適切なエスカレーションを設計する

---

## 次のステップ

- [Infrastructure as Code](../06-operations/aws-iac.md): CloudFormation、CDKによるインフラのコード管理を学ぶ

---

## 参考リソース

- [Amazon CloudWatch ドキュメント](https://docs.aws.amazon.com/cloudwatch/)
- [AWS CloudTrail ドキュメント](https://docs.aws.amazon.com/cloudtrail/)
- [AWS X-Ray ドキュメント](https://docs.aws.amazon.com/xray/)
- [CloudWatch Logs Insights クエリ構文](https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/CWL_QuerySyntax.html)
- [AWS Config ドキュメント](https://docs.aws.amazon.com/config/)
- [AWS Observability ベストプラクティス](https://docs.aws.amazon.com/wellarchitected/latest/operational-excellence-pillar/)
