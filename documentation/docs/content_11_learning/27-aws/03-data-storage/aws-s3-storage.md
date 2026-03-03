# S3・ストレージ

Amazon S3（Simple Storage Service）は、業界をリードするスケーラビリティ、データ可用性、セキュリティ、パフォーマンスを提供するオブジェクトストレージサービスです。ここではS3を中心に、EBS、EFSなどAWSの主要ストレージサービスの特徴と使い分けを学びます。

---

## 所要時間
約40分

## 学べること
- AWSストレージサービスの全体像と使い分け
- S3の基本概念（バケット、オブジェクト、キー）
- ストレージクラスによるコスト最適化
- バケットポリシーとアクセス制御
- S3暗号化の種類と選択基準
- EBS、EFSの特徴とユースケース
- IDサービスにおけるストレージ活用パターン

## 前提知識
- AWSの基本概念（リージョン、アベイラビリティゾーン）
- IAMの基礎知識（ポリシー、ロール）
- HTTPプロトコルの基礎

---

## 目次
1. [AWSストレージサービスの全体像](#1-awsストレージサービスの全体像)
2. [S3の基本概念](#2-s3の基本概念)
3. [S3ストレージクラス](#3-s3ストレージクラス)
4. [バケットポリシーとアクセス制御](#4-バケットポリシーとアクセス制御)
5. [S3暗号化](#5-s3暗号化)
6. [バージョニングとライフサイクルルール](#6-バージョニングとライフサイクルルール)
7. [S3 Object Lock（改ざん防止）](#7-s3-object-lock改ざん防止)
8. [S3イベント通知](#8-s3イベント通知)
9. [Athena（S3データ分析）](#9-athenas3データ分析)
10. [Glue（データカタログ・ETL）](#10-glueデータカタログetl)
11. [EBS（ブロックストレージ）](#11-ebsブロックストレージ)
12. [EFS（マネージドNFS）](#12-efsマネージドnfs)
13. [IDサービスでの活用](#13-idサービスでの活用)
14. [まとめ](#14-まとめ)

---

## 1. AWSストレージサービスの全体像

AWSは3つのカテゴリのストレージサービスを提供しています。

| カテゴリ | サービス | タイプ | アクセス方法 | ユースケース |
|---------|---------|--------|------------|-------------|
| オブジェクト | Amazon S3 | オブジェクト | HTTP API | ファイル保存、バックアップ、静的サイト |
| ブロック | Amazon EBS | ブロック | EC2にアタッチ | データベース、OS |
| ファイル | Amazon EFS | ファイル（NFS） | NFSマウント | 共有ファイルシステム |
| ファイル | Amazon FSx | ファイル | SMB/NFS/Lustre | Windows共有、HPC |

### ストレージタイプの違い

```
オブジェクトストレージ（S3）:
┌──────────────────────────────┐
│  Key: logs/2026/03/audit.json │
│  Value: {オブジェクトデータ}     │
│  Metadata: Content-Type, ...  │
│                              │
│  → フラットな名前空間           │
│  → HTTPでアクセス              │
│  → 変更は全体の上書き           │
└──────────────────────────────┘

ブロックストレージ（EBS）:
┌───┬───┬───┬───┬───┬───┬───┐
│ B1│ B2│ B3│ B4│ B5│ B6│...│
└───┴───┴───┴───┴───┴───┴───┘
  → 固定サイズのブロック単位
  → ファイルシステムを構築
  → ブロック単位で更新可能

ファイルストレージ（EFS）:
  /
  ├── config/
  │   └── app.conf
  ├── data/
  │   └── users.dat
  → 階層的なディレクトリ構造
  → 複数EC2から同時マウント可能
```

---

## 2. S3の基本概念

### バケットとオブジェクト

S3はバケット（コンテナ）にオブジェクト（ファイル）を格納するシンプルなモデルです。

```
S3の構造:

  バケット: idp-production-config
  ┌─────────────────────────────────────┐
  │                                     │
  │  オブジェクト:                        │
  │  ├── tenants/tenant-001/config.json │
  │  ├── tenants/tenant-002/config.json │
  │  ├── keys/signing-key.pem          │
  │  └── templates/email/welcome.html   │
  │                                     │
  │  バケット名: グローバルに一意          │
  │  リージョン: ap-northeast-1          │
  │  オブジェクト最大サイズ: 5TB          │
  └─────────────────────────────────────┘
```

### オブジェクトの構成要素

| 要素 | 説明 | 例 |
|------|------|-----|
| キー | オブジェクトの識別子（パス） | `tenants/001/config.json` |
| 値 | オブジェクトのデータ本体 | JSONファイルの内容 |
| バージョンID | バージョニング有効時の識別子 | `3sL4kqtJlcpXroDTDmJ+rmSpXd3dIbrHY` |
| メタデータ | オブジェクトに付与する属性 | `Content-Type: application/json` |
| アクセス制御 | 所有者、権限設定 | バケットポリシー、ACL |

### S3の操作例

```bash
# バケット作成
aws s3 mb s3://idp-production-config --region ap-northeast-1

# オブジェクトのアップロード
aws s3 cp tenant-config.json s3://idp-production-config/tenants/001/config.json

# オブジェクトの一覧
aws s3 ls s3://idp-production-config/tenants/ --recursive

# オブジェクトのダウンロード
aws s3 cp s3://idp-production-config/tenants/001/config.json ./local-config.json

# プレフィックスでの一括同期
aws s3 sync ./config/ s3://idp-production-config/tenants/ --delete
```

### S3の整合性モデル

S3は2020年12月以降、全てのオペレーションで強い整合性（strong consistency）を提供しています。

| オペレーション | 整合性 |
|-------------|--------|
| PUT（新規） | 即時の強い整合性 |
| PUT（上書き） | 即時の強い整合性 |
| DELETE | 即時の強い整合性 |
| LIST | 即時の強い整合性 |

---

## 3. S3ストレージクラス

S3はアクセス頻度とコスト要件に応じた複数のストレージクラスを提供しています。

| ストレージクラス | 可用性 | 最小保存期間 | 取り出し時間 | GB/月コスト | ユースケース |
|---------------|--------|------------|------------|-----------|-------------|
| Standard | 99.99% | なし | 即時 | $0.025 | 頻繁にアクセスするデータ |
| Intelligent-Tiering | 99.9% | なし | 即時 | $0.025〜 | アクセスパターン不明 |
| Standard-IA | 99.9% | 30日 | 即時 | $0.0138 | 低頻度アクセス |
| One Zone-IA | 99.5% | 30日 | 即時 | $0.011 | 再作成可能なデータ |
| Glacier Instant | 99.9% | 90日 | 即時 | $0.005 | 即時取り出し必要なアーカイブ |
| Glacier Flexible | 99.99% | 90日 | 分〜12時間 | $0.0045 | バックアップ、アーカイブ |
| Glacier Deep Archive | 99.99% | 180日 | 12〜48時間 | $0.002 | 長期保存、コンプライアンス |

### 最小保存期間とは

最小保存期間は「**そのストレージクラスに置いた時点で、最低この日数分の保存料金が発生する**」という課金ルールです。実際にデータを保存し続ける義務ではなく、**早期削除しても課金される**という意味です。

```
例: Standard-IA（最小保存期間 30日）に置いたファイルを5日で削除した場合

  実際の保存: ████░░░░░░░░░░░░░░░░░░░░░░░░░░  5日間
  課金される: ██████████████████████████████  30日間分
              ↑ 残り25日分は「早期削除料金」として請求

例: S3 Standard（最小保存期間なし）なら5日で削除すれば5日分の課金のみ
```

| シナリオ | 結果 |
|---------|------|
| Glacier Flexible（90日）に置いて30日で削除 | **90日分の保存料金**が発生 |
| Standard-IA（30日）に置いて60日で削除 | 60日分の保存料金（最小保存期間を超えているため通常課金） |
| Standard（なし）に置いて1日で削除 | 1日分の保存料金のみ |

**注意**: ライフサイクルルールで自動遷移する場合も同様です。Standard → Standard-IA への遷移を「作成後7日」に設定すると、Standard-IAの最小保存期間30日を満たす前に次のクラスに遷移してしまい、想定外のコストが発生します。

```
推奨: ライフサイクル遷移は最小保存期間以上の間隔で設定する

  Standard ──30日以上──▶ Standard-IA ──90日以上──▶ Glacier Flexible ──180日以上──▶ Deep Archive
```

### ストレージクラスの選定フロー

```
データのアクセス頻度は？
├── 頻繁（日次以上）
│   └── S3 Standard
├── 不明・変動する
│   └── S3 Intelligent-Tiering（自動最適化）
├── 低頻度（月次程度）
│   └── 複数AZの耐久性が必要？
│       ├── Yes → S3 Standard-IA
│       └── No  → S3 One Zone-IA
├── アーカイブ（年次以下）
│   └── 即時取り出しが必要？
│       ├── Yes → Glacier Instant Retrieval
│       └── No  → Glacier Flexible Retrieval
└── 長期保存（コンプライアンス）
    └── Glacier Deep Archive
```

---

## 4. バケットポリシーとアクセス制御

### アクセス制御の方式

S3のアクセス制御は複数のメカニズムを組み合わせて実現します。

| 方式 | 適用レベル | 制御対象 | 推奨度 |
|------|----------|---------|--------|
| バケットポリシー | バケット | 誰がアクセスできるか | 推奨 |
| IAMポリシー | ユーザー/ロール | 何にアクセスできるか | 推奨 |
| ACL | バケット/オブジェクト | レガシーな権限設定 | 非推奨 |
| パブリックアクセスブロック | アカウント/バケット | 公開アクセスの一括制御 | 必須 |

### バケットポリシーの例

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "AllowIdpServerAccess",
      "Effect": "Allow",
      "Principal": {
        "AWS": "arn:aws:iam::123456789012:role/idp-server-role"
      },
      "Action": [
        "s3:GetObject",
        "s3:ListBucket"
      ],
      "Resource": [
        "arn:aws:s3:::idp-production-config",
        "arn:aws:s3:::idp-production-config/tenants/*"
      ]
    },
    {
      "Sid": "DenyUnencryptedTransport",
      "Effect": "Deny",
      "Principal": "*",
      "Action": "s3:*",
      "Resource": [
        "arn:aws:s3:::idp-production-config",
        "arn:aws:s3:::idp-production-config/*"
      ],
      "Condition": {
        "Bool": {
          "aws:SecureTransport": "false"
        }
      }
    }
  ]
}
```

### パブリックアクセスブロック

セキュリティのベストプラクティスとして、全てのバケットでパブリックアクセスブロックを有効にします。

```bash
# アカウントレベルでパブリックアクセスをブロック
aws s3control put-public-access-block \
  --account-id 123456789012 \
  --public-access-block-configuration \
    BlockPublicAcls=true,\
    IgnorePublicAcls=true,\
    BlockPublicPolicy=true,\
    RestrictPublicBuckets=true
```

---

## 5. S3暗号化

### サーバーサイド暗号化の種類

| 暗号化方式 | 鍵の管理 | コスト | ユースケース |
|-----------|---------|--------|-------------|
| SSE-S3 | AWSが管理 | 無料 | 基本的な暗号化要件 |
| SSE-KMS | KMSで管理 | KMS料金 | 監査ログ、アクセス制御が必要 |
| SSE-C | 顧客が提供 | 無料 | 独自鍵管理が必須 |
| CSE | クライアント側 | 無料 | エンドツーエンド暗号化 |

### 暗号化の処理フロー

```
SSE-KMS の場合:

  アップロード時:
  ┌────────┐   PUT + データ   ┌──────┐   鍵要求   ┌──────┐
  │ Client │ ──────────────→ │  S3  │ ────────→ │ KMS  │
  │        │                 │      │ ←──────── │      │
  │        │                 │      │  データキー │      │
  │        │                 │      │           └──────┘
  │        │                 │      │
  │        │                 │ データキーで暗号化  │
  │        │                 │ 暗号化データ + 暗号化されたデータキーを保存
  └────────┘                 └──────┘

  ダウンロード時:
  ┌────────┐   GET           ┌──────┐   復号要求  ┌──────┐
  │ Client │ ──────────────→ │  S3  │ ────────→ │ KMS  │
  │        │                 │      │ ←──────── │      │
  │        │ ←────────────── │      │ データキー  │      │
  │        │   復号データ     │      │           └──────┘
  └────────┘                 └──────┘
```

### デフォルト暗号化の設定

```bash
# バケットにSSE-KMSデフォルト暗号化を設定
aws s3api put-bucket-encryption \
  --bucket idp-production-config \
  --server-side-encryption-configuration '{
    "Rules": [
      {
        "ApplyServerSideEncryptionByDefault": {
          "SSEAlgorithm": "aws:kms",
          "KMSMasterKeyID": "alias/idp-s3-key"
        },
        "BucketKeyEnabled": true
      }
    ]
  }'
```

`BucketKeyEnabled: true`を設定すると、バケットレベルのキーを使用してKMS APIコールを削減し、コストを最大99%削減できます。

---

## 6. バージョニングとライフサイクルルール

### バージョニング

バージョニングを有効にすると、オブジェクトの全てのバージョンが保持されます。

```
バージョニングの動作:

  PUT config.json (v1)  →  PUT config.json (v2)  →  DELETE
  ┌──────────┐          ┌──────────┐             ┌──────────────┐
  │ v1       │          │ v2 (最新) │             │ Delete Marker │
  │ (最新)    │          │ v1       │             │ v2           │
  └──────────┘          └──────────┘             │ v1           │
                                                 └──────────────┘
                                                 ↑ 論理削除のみ
                                                   v1, v2は残存
```

### ライフサイクルルール

ライフサイクルルールにより、オブジェクトのストレージクラスを自動的に移行したり、古いバージョンを削除できます。

```json
{
  "Rules": [
    {
      "ID": "AuditLogLifecycle",
      "Filter": {
        "Prefix": "audit-logs/"
      },
      "Status": "Enabled",
      "Transitions": [
        {
          "Days": 30,
          "StorageClass": "STANDARD_IA"
        },
        {
          "Days": 90,
          "StorageClass": "GLACIER_IR"
        },
        {
          "Days": 365,
          "StorageClass": "DEEP_ARCHIVE"
        }
      ],
      "NoncurrentVersionExpiration": {
        "NoncurrentDays": 30
      }
    }
  ]
}
```

```
ライフサイクル遷移の流れ:

  作成 → 30日後 → 90日後 → 365日後
  ┌──────┐  ┌──────────┐  ┌───────────┐  ┌──────────────┐
  │ Std  │→ │ Std-IA   │→ │ Glacier   │→ │ Deep Archive │
  │      │  │          │  │ Instant   │  │              │
  │$0.025│  │ $0.0138  │  │ $0.005    │  │ $0.002       │
  └──────┘  └──────────┘  └───────────┘  └──────────────┘
  /GB/月
```

---

## 7. S3 Object Lock（改ざん防止）

S3 Object Lockは、オブジェクトの削除や上書きを一定期間（または無期限に）防止する機能です。WORM（Write Once Read Many）モデルを実現し、規制やコンプライアンスで求められるデータの改ざん防止に対応します。

### 保持モードの種類

| モード | 特徴 | 解除 | ユースケース |
|--------|------|------|------------|
| Governance | 特別な権限（`s3:BypassGovernanceRetention`）で解除可能 | 権限があれば可能 | 社内ポリシー、誤削除防止 |
| Compliance | 保持期間中は**誰も**解除できない（rootアカウントでも不可） | 不可 | 法規制対応、金融監査ログ |

### 保持期間の設定

```
Object Lock の動作:

  オブジェクト作成
       │
       ▼
  ┌─────────────────────────────────────────────────┐
  │  保持期間（例: 7年）                               │
  │                                                  │
  │  この期間中:                                       │
  │  ・DELETE → 403 Forbidden                        │
  │  ・PUT（上書き） → 403 Forbidden                  │
  │  ・GET（読み取り） → 正常にアクセス可能              │
  │  ・Complianceモード: 保持期間の短縮も不可           │
  │                                                  │
  │  保持期間終了後:                                    │
  │  ・通常のS3オブジェクトと同じ操作が可能              │
  └─────────────────────────────────────────────────┘
```

### リーガルホールド

保持期間とは別に、リーガルホールド（Legal Hold）を設定できます。訴訟や調査に関連するデータを、調査完了まで無期限に保持するために使用します。

| 機能 | 保持期間（Retention） | リーガルホールド |
|------|---------------------|----------------|
| 期間 | 指定した日数/年数 | 明示的に解除するまで無期限 |
| 解除 | 期間終了で自動解除 | `s3:PutObjectLegalHold` 権限で手動解除 |
| 用途 | コンプライアンス要件 | 訴訟対応、調査対応 |

### 設定例

```bash
# Object Lock有効なバケット作成（バケット作成時にのみ有効化可能）
aws s3api create-bucket \
  --bucket idp-audit-logs \
  --region ap-northeast-1 \
  --create-bucket-configuration LocationConstraint=ap-northeast-1 \
  --object-lock-enabled-for-bucket

# デフォルトのObject Lock設定（Complianceモード、7年保持）
aws s3api put-object-lock-configuration \
  --bucket idp-audit-logs \
  --object-lock-configuration '{
    "ObjectLockEnabled": "Enabled",
    "Rule": {
      "DefaultRetention": {
        "Mode": "COMPLIANCE",
        "Years": 7
      }
    }
  }'

# 個別オブジェクトにリーガルホールドを設定
aws s3api put-object-legal-hold \
  --bucket idp-audit-logs \
  --key "2024/01/15/audit-event-001.json" \
  --legal-hold '{"Status": "ON"}'
```

### Object Lockの注意点

| 注意事項 | 説明 |
|---------|------|
| バケット作成時のみ有効化可能 | 既存バケットには後からObject Lockを追加できない |
| バージョニング必須 | Object Lockはバージョニング有効なバケットでのみ動作 |
| Complianceモードは取消不可 | 設定後は保持期間の短縮も、モードの変更も不可 |
| ライフサイクルとの連携 | ライフサイクルでGlacierに遷移してもObject Lockは維持される |
| コスト | Object Lock自体の追加料金はない（ストレージ料金のみ） |

---

## 8. S3イベント通知

S3はオブジェクトの作成・削除などのイベントをトリガーとして、他のAWSサービスに通知を送信できます。

### 通知先サービス

```
S3イベント通知:

  ┌──────────┐
  │   S3     │
  │  Bucket  │
  └────┬─────┘
       │ イベント（PUT, DELETE, COPY等）
       │
       ├──────────→ ┌──────────────┐
       │            │ AWS Lambda   │  リアルタイム処理
       │            └──────────────┘
       │
       ├──────────→ ┌──────────────┐
       │            │ Amazon SQS   │  非同期キュー処理
       │            └──────────────┘
       │
       ├──────────→ ┌──────────────┐
       │            │ Amazon SNS   │  ファンアウト通知
       │            └──────────────┘
       │
       └──────────→ ┌──────────────┐
                    │ EventBridge  │  高度なルーティング
                    └──────────────┘
```

### イベント通知の設定例

```bash
# S3イベント通知でLambdaをトリガー（テナント設定の変更検知）
aws s3api put-bucket-notification-configuration \
  --bucket idp-production-config \
  --notification-configuration '{
    "LambdaFunctionConfigurations": [
      {
        "LambdaFunctionArn": "arn:aws:lambda:ap-northeast-1:123456789012:function:idp-config-reload",
        "Events": ["s3:ObjectCreated:*", "s3:ObjectRemoved:*"],
        "Filter": {
          "Key": {
            "FilterRules": [
              {"Name": "prefix", "Value": "tenants/"},
              {"Name": "suffix", "Value": ".json"}
            ]
          }
        }
      }
    ]
  }'
```

---

## 9. Athena（S3データ分析）

Amazon Athenaは、S3に保存されたデータに対して標準SQLでクエリを実行できるサーバーレスの分析サービスです。インフラの管理が不要で、スキャンしたデータ量に対してのみ課金されます。

### 基本的な仕組み

```
Athena の動作:

  ユーザー                 Athena                    S3
    │                      │                       │
    │ SQLクエリ実行          │                       │
    │─────────────────────→│                       │
    │                      │ スキャン対象を特定        │
    │                      │─────────────────────→ │
    │                      │                       │ データ読み取り
    │                      │ ←─────────────────────│
    │                      │ クエリ実行（Prestoエンジン）│
    │ 結果                  │                       │
    │←─────────────────────│                       │

  特徴:
  ・サーバーレス（インフラ管理不要）
  ・スキャンしたデータ量で課金（$5/TB）
  ・S3のデータを直接クエリ（データ移動不要）
  ・Parquet/ORC形式で大幅にコスト削減
```

### テーブル定義とパーティション

S3上のデータにクエリするためには、テーブル定義（スキーマ）を作成します。パーティションを活用すると、スキャン対象を限定してコストとクエリ時間を大幅に削減できます。

```sql
-- 監査ログテーブルの作成（パーティション付き）
CREATE EXTERNAL TABLE audit_logs (
    event_id     STRING,
    tenant_id    STRING,
    event_type   STRING,
    user_id      STRING,
    ip_address   STRING,
    user_agent   STRING,
    payload      STRING,
    created_at   TIMESTAMP
)
PARTITIONED BY (year STRING, month STRING, day STRING)
ROW FORMAT SERDE 'org.apache.hadoop.hive.ql.io.parquet.serde.ParquetHiveSerDe'
STORED AS PARQUET
LOCATION 's3://idp-audit-logs/data/'
TBLPROPERTIES ('parquet.compression'='SNAPPY');

-- パーティションの追加
MSCK REPAIR TABLE audit_logs;
```

### コスト最適化のポイント

| 最適化手法 | 効果 | 実装方法 |
|-----------|------|---------|
| パーティション | スキャン量を10〜1000分の1に | `year/month/day` のディレクトリ構造 |
| カラムナフォーマット | 必要な列のみスキャン | Parquet / ORC 形式で保存 |
| 圧縮 | スキャン量の物理削減 | Snappy / gzip 圧縮 |
| CTAS | 結果を新テーブルとして保存 | `CREATE TABLE AS SELECT` |

```
パーティションの効果:

  パーティションなし:
    SELECT ... WHERE created_at = '2024-01-15'
    → 全データスキャン: 1TB = $5.00

  パーティションあり:
    SELECT ... WHERE year='2024' AND month='01' AND day='15'
    → 1日分のみスキャン: 3GB = $0.015

  コスト差: 約330倍
```

### クエリ例

```sql
-- 特定テナントの認証失敗イベント（直近7日間）
SELECT event_type, COUNT(*) as count, DATE(created_at) as event_date
FROM audit_logs
WHERE tenant_id = 'tenant-001'
  AND event_type = 'login_failure'
  AND year = '2024' AND month = '01'
  AND day BETWEEN '08' AND '15'
GROUP BY event_type, DATE(created_at)
ORDER BY event_date DESC;

-- テナント別のイベント件数集計（月次）
SELECT tenant_id, COUNT(*) as total_events
FROM audit_logs
WHERE year = '2024' AND month = '01'
GROUP BY tenant_id
ORDER BY total_events DESC
LIMIT 20;
```

### CloudWatch Logs / CloudTrail との使い分け

| ツール | 適したユースケース | コスト特性 |
|--------|----------------|----------|
| CloudWatch Logs Insights | 直近のアプリケーションログの検索・分析 | 取り込み量課金 |
| CloudTrail + Athena | AWS API呼び出しの監査ログ分析 | スキャン量課金 |
| S3 + Athena | 大量データの長期分析（監査ログ、アクセスログ等） | スキャン量課金（最もコスト効率が良い） |

---

## 10. Glue（データカタログ・ETL）

AWS Glueは、データの発見・変換・ロードを行うサーバーレスETLサービスです。S3上のデータに対してスキーマ情報を管理する**データカタログ**と、データ変換を行う**ETLジョブ**の2つの機能を提供します。

### Glueの2つの役割

```
┌─────────────────────────────────────────────────────────────┐
│                     AWS Glue                                │
│                                                             │
│  ┌──────────────────────┐  ┌─────────────────────────────┐  │
│  │  データカタログ        │  │  ETLジョブ                   │  │
│  │                      │  │                             │  │
│  │  S3上のデータの        │  │  データの変換・加工           │  │
│  │  「テーブル定義」を管理  │  │  （フォーマット変換、       │  │
│  │                      │  │   クレンジング、集約）       │  │
│  │  → Athenaが参照して   │  │                             │  │
│  │    SQLクエリ可能に     │  │  → S3データをParquet等に    │  │
│  │                      │  │    変換してコスト削減        │  │
│  └──────────────────────┘  └─────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### データカタログ

Glueデータカタログは、S3上のデータの**メタデータ（スキーマ、場所、フォーマット）を管理する中央リポジトリ**です。Athenaはこのカタログを参照してSQLクエリを実行します。

```
S3バケット                  Glue データカタログ          クエリエンジン
┌──────────────┐           ┌──────────────────┐       ┌──────────┐
│ /data/       │  Crawler  │ Database: idp_db │       │ Athena   │
│  year=2024/  │ ───────→  │  Table: logs     │ ←──── │          │
│   month=01/  │  自動検出  │   columns:       │ 参照  │ SELECT   │
│    *.parquet │           │    event_id STR  │       │ FROM ... │
│              │           │    tenant_id STR │       └──────────┘
└──────────────┘           └──────────────────┘
```

**Crawler**を設定すると、S3のデータ構造を自動的にスキャンし、スキーマを検出してカタログに登録します。

```bash
# Crawlerの作成
aws glue create-crawler \
  --name idp-audit-log-crawler \
  --role GlueServiceRole \
  --database-name idp_audit_db \
  --targets '{
    "S3Targets": [
      {"Path": "s3://idp-audit-logs/data/"}
    ]
  }'

# Crawlerの実行
aws glue start-crawler --name idp-audit-log-crawler
```

### ETLジョブ

ETLジョブは、S3上のデータを変換・加工するバッチ処理です。

| ユースケース | 入力 | 出力 | 効果 |
|------------|------|------|------|
| フォーマット変換 | JSON（行指向） | Parquet（列指向） | Athenaクエリコスト90%削減 |
| データクレンジング | 生ログ（不要フィールド含む） | 必要フィールドのみ | ストレージコスト削減 |
| パーティション再構成 | フラットなファイル配置 | year/month/day パーティション | クエリスキャン範囲の限定 |
| データ集約 | 個別イベントレコード | 日次/月次サマリ | 分析クエリの高速化 |

### Glue vs Lambda：ETL処理の使い分け

| 観点 | Glue ETL | Lambda |
|------|---------|--------|
| 処理データ量 | GB〜TB級 | MB〜数GB |
| 実行時間 | 数分〜数時間 | 最大15分 |
| 処理エンジン | Apache Spark | カスタムコード |
| スキーマ管理 | データカタログ統合 | なし（自前管理） |
| コスト | DPU時間課金 | リクエスト+実行時間 |
| 適したケース | 定期的な大量データ変換 | イベント駆動の小規模変換 |

**IDサービスでの判断基準**: 日次で数GB以下のログ変換ならLambda、それ以上ならGlue ETLが適切です。

### Athena + Glue の連携パターン

```
 生ログ投入                    定期ETL              分析クエリ
┌──────────┐  Firehose  ┌──────────┐  Glue ETL  ┌──────────┐  Athena  ┌──────────┐
│CloudWatch│ ────────→  │ S3       │ ────────→  │ S3       │ ──────→ │ 分析結果  │
│  Logs    │            │ (JSON)   │ Parquet変換 │ (Parquet)│ SQL実行  │          │
└──────────┘            └──────────┘            └──────────┘         └──────────┘
                          RAWゾーン    Glue Crawler   分析ゾーン
                                     でカタログ登録
```

1. **RAWゾーン**: Firehoseで配信された生ログ（JSON）を保存
2. **Glue Crawler**: RAWゾーンのスキーマを自動検出しカタログ登録
3. **Glue ETL**: JSONをParquetに変換し、パーティション付きで分析ゾーンに出力
4. **Athena**: 分析ゾーンのParquetデータに対してSQLクエリを実行

### いつGlueを使うか

| 状況 | Glue使用 | 代替手段 |
|------|---------|---------|
| S3データにAthenaでSQLを実行したい | カタログのみ使用（Crawlerでスキーマ自動検出） | AthenaのCREATE TABLEで手動定義 |
| JSON→Parquet変換が必要 | ETLジョブで定期変換 | 小規模ならLambdaで変換 |
| 複数ソースのデータを統合分析したい | カタログで統一スキーマ管理 | - |
| ログの長期保存コストを最適化したい | ETLで圧縮・フォーマット変換 | S3ライフサイクルで階層化のみ |

---

## 11. EBS（ブロックストレージ）

Amazon EBS（Elastic Block Store）は、EC2インスタンスにアタッチして使用するブロックストレージです。

### EBSボリュームタイプ

| ボリュームタイプ | カテゴリ | IOPS | スループット | サイズ | ユースケース |
|---------------|---------|------|------------|--------|-------------|
| gp3 | 汎用SSD | 3,000〜16,000 | 125〜1,000 MB/s | 1GiB〜16TiB | 汎用ワークロード |
| gp2 | 汎用SSD | サイズ比例（最大16,000） | サイズ比例 | 1GiB〜16TiB | レガシー |
| io2 Block Express | プロビジョンドSSD | 最大256,000 | 最大4,000 MB/s | 4GiB〜64TiB | ミッションクリティカルDB |
| st1 | スループット最適化HDD | 500 | 最大500 MB/s | 125GiB〜16TiB | ビッグデータ、ログ処理 |
| sc1 | コールドHDD | 250 | 最大250 MB/s | 125GiB〜16TiB | アクセス頻度の低いデータ |

### EBSの主な特徴

| 特徴 | 説明 |
|------|------|
| AZ内の冗長化 | 同一AZ内で自動的にレプリケーション |
| スナップショット | S3にインクリメンタルバックアップ |
| 暗号化 | KMSによるボリューム暗号化 |
| マルチアタッチ | io2ボリュームのみ、複数EC2にアタッチ可能 |
| エラスティック | オンラインでサイズ・タイプ変更可能 |

---

## 12. EFS（マネージドNFS）

Amazon EFS（Elastic File System）は、複数のEC2インスタンスから同時にマウントできるマネージドNFSファイルシステムです。

### EFSの特徴

```
EFSの共有ファイルシステム:

  AZ-a                AZ-c
  ┌─────────┐        ┌─────────┐
  │  EC2-1  │        │  EC2-2  │
  │  ┌────┐ │        │  ┌────┐ │
  │  │ EFS│ │        │  │ EFS│ │
  │  │Mount│ │        │  │Mount│ │
  │  └──┬─┘ │        │  └──┬─┘ │
  └─────┼───┘        └─────┼───┘
        │                  │
        └──────┬───────────┘
               │
        ┌──────▼──────┐
        │   Amazon    │
        │    EFS      │
        │  (共有FS)    │
        │             │
        │ /config/    │
        │ /shared/    │
        │ /uploads/   │
        └─────────────┘
```

### EFS vs EBS 比較

| 項目 | EFS | EBS |
|------|-----|-----|
| タイプ | ファイルストレージ（NFS） | ブロックストレージ |
| アクセス | 複数EC2から同時 | 1つのEC2（io2を除く） |
| スケーリング | 自動（ペタバイト級） | 手動でサイズ変更 |
| AZ | 複数AZにまたがる | 単一AZ |
| パフォーマンス | 汎用/最大I/Oモード | ボリュームタイプに依存 |
| コスト | EBSより高い | 比較的安い |
| ユースケース | 共有設定、コンテンツ管理 | データベース、ブートボリューム |

---

## 13. IDサービスでの活用

idp-serverのAWS環境では、S3を設定管理と監査ログの保存に活用します。

### S3の利用パターン

```
┌─────────────────────────────────────────────────────┐
│              idp-server S3活用構成                    │
│                                                     │
│  ┌────────────────────────────────────────────┐      │
│  │ s3://idp-tenant-config/                    │      │
│  │  ├── tenants/                              │      │
│  │  │   ├── tenant-001/config.json           │      │
│  │  │   ├── tenant-002/config.json           │      │
│  │  │   └── ...                              │      │
│  │  └── templates/                            │      │
│  │      ├── email/                            │      │
│  │      └── page/                             │      │
│  └────────────────────────────────────────────┘      │
│       ↑ テナント設定の一元管理                         │
│       ↑ バージョニング有効で変更履歴を保持             │
│                                                     │
│  ┌────────────────────────────────────────────┐      │
│  │ s3://idp-audit-logs/                       │      │
│  │  ├── 2026/03/03/                           │      │
│  │  │   ├── auth-events-001.json.gz          │      │
│  │  │   ├── auth-events-002.json.gz          │      │
│  │  │   └── ...                              │      │
│  │  └── lifecycle:                            │      │
│  │      Standard → IA(30d) → Glacier(1y)     │      │
│  └────────────────────────────────────────────┘      │
│       ↑ 監査ログの長期保存                            │
│       ↑ ライフサイクルルールでコスト最適化             │
│                                                     │
│  ┌────────────────────────────────────────────┐      │
│  │ s3://idp-jwks-public/                      │      │
│  │  └── .well-known/jwks.json                │      │
│  │      → CloudFront経由で公開                 │      │
│  └────────────────────────────────────────────┘      │
│       ↑ JWKS公開エンドポイントのオリジン               │
└─────────────────────────────────────────────────────┘
```

### セキュリティ設定のベストプラクティス

idp-serverの設定や監査ログには機密情報が含まれるため、以下のセキュリティ設定を適用します。

| 項目 | 設定 |
|------|------|
| パブリックアクセス | 全ブロック有効（JWKSバケットを除く） |
| 暗号化 | SSE-KMS（カスタマーマネージドキー） |
| バージョニング | 有効（設定バケット） |
| アクセスログ | S3サーバーアクセスログ有効 |
| オブジェクトロック | 監査ログバケットで有効（WORM） |
| MFA Delete | 有効（設定バケット） |
| TLS強制 | バケットポリシーでHTTPS必須 |

### 監査ログの保存ポリシー例

```bash
# オブジェクトロック（WORM）有効なバケット作成
aws s3api create-bucket \
  --bucket idp-audit-logs \
  --region ap-northeast-1 \
  --create-bucket-configuration LocationConstraint=ap-northeast-1 \
  --object-lock-enabled-for-bucket

# コンプライアンスモードで保持期間設定（改ざん防止）
aws s3api put-object-lock-configuration \
  --bucket idp-audit-logs \
  --object-lock-configuration '{
    "ObjectLockEnabled": "Enabled",
    "Rule": {
      "DefaultRetention": {
        "Mode": "COMPLIANCE",
        "Years": 7
      }
    }
  }'
```

---

## 14. まとめ

| サービス | 特徴 | idp-serverでの利用 |
|---------|------|-------------------|
| S3 | 無制限のオブジェクトストレージ | テナント設定、監査ログ、JWKS公開 |
| EBS | EC2向けブロックストレージ | EC2ベースデプロイ時のルートボリューム |
| EFS | 共有ファイルシステム | 複数インスタンスでの設定共有 |

### 重要なポイント

- S3はイレブンナイン（99.999999999%）の耐久性を持つ、事実上データを失わないストレージ
- ストレージクラスとライフサイクルルールを活用してコストを最適化する
- パブリックアクセスブロックとバケットポリシーで意図しないデータ公開を防止する
- 暗号化はデフォルトで有効にし、機密データにはSSE-KMSを使用する
- 監査ログにはオブジェクトロック（WORM）を適用してコンプライアンスを確保する

## 次のステップ

- [ロードバランシング（ALB/NLB）](../04-networking/aws-load-balancing.md): ロードバランサーの構成とSSL終端

## 参考リソース

- [Amazon S3 ドキュメント](https://docs.aws.amazon.com/ja_jp/AmazonS3/latest/userguide/)
- [Amazon EBS ドキュメント](https://docs.aws.amazon.com/ja_jp/ebs/latest/userguide/)
- [Amazon EFS ドキュメント](https://docs.aws.amazon.com/ja_jp/efs/latest/ug/)
- [S3 セキュリティベストプラクティス](https://docs.aws.amazon.com/ja_jp/AmazonS3/latest/userguide/security-best-practices.html)
