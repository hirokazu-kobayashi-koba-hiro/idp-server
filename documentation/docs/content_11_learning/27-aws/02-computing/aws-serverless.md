# サーバーレスアーキテクチャ

サーバーの管理を意識せずにアプリケーションを構築・実行するサーバーレスアーキテクチャについて学びます。AWS Lambda、API Gateway、SQS、SNS、EventBridge、Step Functionsの各サービスの特徴と統合パターンを解説します。

---

## 所要時間
約50分

## 学べること
- サーバーレスの概念と従来型アーキテクチャとの違い
- Lambda関数の作成、設定、最適化
- API GatewayによるAPIエンドポイントの構築
- SQS/SNSによる非同期メッセージング
- EventBridgeによるイベント駆動設計
- Step Functionsによるワークフローオーケストレーション
- IDサービスにおけるサーバーレス活用パターン

## 前提知識
- AWSの基本概念（IAM、VPC等）
- REST APIの基本知識
- プログラミングの基礎（Python or Node.js）

---

## 目次
1. [サーバーレスとは何か](#1-サーバーレスとは何か)
2. [Lambda概要](#2-lambda概要)
3. [Lambda関数の作成](#3-lambda関数の作成)
4. [Lambdaのコールドスタートと対策](#4-lambdaのコールドスタートと対策)
5. [Lambdaの制限と料金モデル](#5-lambdaの制限と料金モデル)
6. [API Gateway](#6-api-gateway)
7. [API Gateway + Lambda 統合パターン](#7-api-gateway--lambda-統合パターン)
8. [SQS（Simple Queue Service）](#8-sqssimple-queue-service)
9. [SNS（Simple Notification Service）](#9-snssimple-notification-service)
10. [EventBridge](#10-eventbridge)
11. [Kinesis Data Streams](#11-kinesis-data-streams)
12. [Kinesis Data Firehose](#12-kinesis-data-firehose)
13. [Step Functions](#13-step-functions)
14. [IDサービスでの活用](#14-idサービスでの活用)
15. [まとめ](#まとめ)

---

## 1. サーバーレスとは何か

サーバーレスは、サーバーのプロビジョニングや管理をクラウドプロバイダーに任せ、開発者はアプリケーションコードに集中できるアーキテクチャモデルです。

### 従来型 vs サーバーレス

```
従来型:
+-------+   +-------+   +-------+
| EC2-1 |   | EC2-2 |   | EC2-3 |   ← 常時起動、未使用時も課金
+-------+   +-------+   +-------+
    |            |            |
    +-----+------+-----+-----+
          |            |
     +---------+  +---------+
     | OS管理   |  | パッチ   |   ← 運用負担
     +---------+  +---------+

サーバーレス:
          リクエスト
              |
    +---------v---------+
    |     Lambda        |
    | (必要時に自動起動)   |  ← 実行時間のみ課金
    | (自動スケール)      |  ← OS/ランタイム管理不要
    +-------------------+
```

| 観点 | 従来型（EC2等） | サーバーレス（Lambda等） |
|------|---------------|---------------------|
| サーバー管理 | 自己管理（OS、パッチ、スケール） | AWSが完全管理 |
| 課金モデル | 時間課金（未使用時も課金） | 実行回数・時間課金 |
| スケーリング | 手動 or Auto Scaling設定 | 自動（同時実行数に応じて） |
| 起動時間 | 分単位 | ミリ秒〜秒単位 |
| 実行時間制限 | なし | 最大15分（Lambda） |
| 適性 | 長時間処理、ステートフル | 短時間処理、イベント駆動 |
| 運用負担 | 高い | 低い |

---

## 2. Lambda概要

AWS Lambdaは、イベントに応じてコードを実行するコンピューティングサービスです。

### 主要概念

```
+----------------------------------------------------------+
|                    Lambda関数                              |
|                                                          |
|  +------------------+                                    |
|  | ハンドラー関数     | ← エントリポイント（event, context）  |
|  +------------------+                                    |
|                                                          |
|  +------------------+  +------------------+              |
|  | ランタイム         |  | レイヤー          |              |
|  | (Node.js, Python  |  | (共有ライブラリ)   |              |
|  |  Java, Go, etc.)  |  +------------------+              |
|  +------------------+                                    |
|                                                          |
|  +------------------+  +------------------+              |
|  | 環境変数          |  | IAMロール         |              |
|  +------------------+  +------------------+              |
+----------------------------------------------------------+
         |
         v
+------------------+
| 実行環境          |  ← AWSが管理するコンテナ
| (microVM)        |     再利用される場合あり（ウォームスタート）
+------------------+
```

### イベントソース

Lambdaは多様なAWSサービスからのイベントで起動できます。

| カテゴリ | サービス | ユースケース |
|---------|---------|------------|
| API | API Gateway | REST/HTTP API |
| ストレージ | S3 | ファイルアップロード処理 |
| キュー | SQS | 非同期メッセージ処理 |
| 通知 | SNS | プッシュ通知処理 |
| イベント | EventBridge | スケジュール、カスタムイベント |
| ストリーム | Kinesis, DynamoDB Streams | リアルタイムデータ処理 |
| 認証 | Cognito, ALB | カスタム認証ロジック |

---

## 3. Lambda関数の作成

### Node.js の例

```javascript
// index.mjs - JWTトークン検証Lambda
import { verify } from 'jsonwebtoken';
import { SSMClient, GetParameterCommand } from '@aws-sdk/client-ssm';

const ssmClient = new SSMClient();

// 初期化コード（コールドスタート時のみ実行）
let publicKey = null;

async function getPublicKey() {
  if (publicKey) return publicKey;
  const command = new GetParameterCommand({
    Name: '/idp-server/jwt-public-key',
    WithDecryption: true,
  });
  const response = await ssmClient.send(command);
  publicKey = response.Parameter.Value;
  return publicKey;
}

// ハンドラー関数
export const handler = async (event, context) => {
  try {
    const token = event.headers?.Authorization?.replace('Bearer ', '');
    if (!token) {
      return {
        statusCode: 401,
        body: JSON.stringify({ error: 'Missing token' }),
      };
    }

    const key = await getPublicKey();
    const decoded = verify(token, key, {
      algorithms: ['RS256'],
      issuer: process.env.EXPECTED_ISSUER,
    });

    return {
      statusCode: 200,
      body: JSON.stringify({
        sub: decoded.sub,
        scope: decoded.scope,
      }),
    };
  } catch (error) {
    console.error('Token verification failed:', error.message);
    return {
      statusCode: 401,
      body: JSON.stringify({ error: 'Invalid token' }),
    };
  }
};
```

### Python の例

```python
# security_event_handler.py - セキュリティイベント処理Lambda
import json
import os
import boto3
from datetime import datetime

dynamodb = boto3.resource('dynamodb')
table = dynamodb.Table(os.environ['EVENTS_TABLE_NAME'])

def handler(event, context):
    """SQSメッセージからセキュリティイベントを処理"""
    processed = 0
    failed = 0

    for record in event['Records']:
        try:
            body = json.loads(record['body'])
            security_event = {
                'event_id': body['jti'],
                'event_type': body['events'],
                'subject': body['sub'],
                'issuer': body['iss'],
                'timestamp': body['iat'],
                'received_at': datetime.utcnow().isoformat(),
                'raw_event': record['body'],
            }
            table.put_item(Item=security_event)
            processed += 1
        except Exception as e:
            print(f"Failed to process record: {e}")
            failed += 1

    return {
        'processed': processed,
        'failed': failed,
    }
```

### レイヤー

共有ライブラリをレイヤーとして管理することで、関数のデプロイパッケージを小さく保てます。

```
+------------------+     +------------------+
| 関数A            |     | 関数B            |
| (認証処理)        |     | (イベント処理)    |
+--------+---------+     +--------+---------+
         |                        |
         +----------+-------------+
                    |
            +-------v--------+
            | 共通レイヤー     |
            | - jsonwebtoken |
            | - aws-sdk      |
            | - 共通ユーティリティ|
            +----------------+
```

---

## 4. Lambdaのコールドスタートと対策

Lambda関数が初めて呼び出される際、実行環境の初期化に時間がかかります（コールドスタート）。

```
コールドスタート:
|←--- 初期化 (数百ms〜数秒) --->|←- 実行 ->|
+-------------------------------+---------+
| 環境作成 | ランタイム起動 | 初期化コード | 処理 |
+-------------------------------+---------+

ウォームスタート:
                               |←- 実行 ->|
                               +---------+
                               |   処理   |  ← 環境を再利用
                               +---------+
```

### コールドスタートの発生条件
- 関数の初回呼び出し
- スケールアウト時（新しい実行環境の追加）
- 長時間未使用後の呼び出し

### ランタイム別のコールドスタート時間目安

| ランタイム | コールドスタート時間 |
|-----------|------------------|
| Python | 100〜300ms |
| Node.js | 100〜300ms |
| Go | 50〜100ms |
| Java | 500ms〜数秒 |
| .NET | 300ms〜1秒 |

### 対策: Provisioned Concurrency

事前に実行環境を確保しておく機能です。コールドスタートをゼロにできます。

```yaml
# SAMテンプレートでの設定
Resources:
  AuthorizerFunction:
    Type: AWS::Serverless::Function
    Properties:
      Handler: index.handler
      ProvisionedConcurrencyConfig:
        ProvisionedConcurrentExecutions: 5  # 常時5つの環境を確保
      AutoPublishAlias: live
```

### その他の対策
- **関数パッケージの軽量化**: 不要な依存関係を排除
- **初期化コードの最適化**: グローバルスコープでの重い処理を最小化
- **適切なメモリ設定**: メモリ増加でCPUも比例して増加し初期化が高速化

---

## 5. Lambdaの制限と料金モデル

### 主要な制限

| 項目 | 制限値 |
|------|--------|
| 最大実行時間 | 15分 |
| メモリ | 128MB〜10,240MB |
| デプロイパッケージ | 50MB（zip）、250MB（展開後） |
| 同時実行数（アカウント） | 1,000（デフォルト、引き上げ可能） |
| /tmp ストレージ | 512MB〜10,240MB |
| 環境変数 | 4KB |
| レイヤー | 最大5つ |

### 料金モデル

```
料金 = リクエスト数 x 単価 + 実行時間(GB-秒) x 単価

例: 月100万回実行、平均200ms、256MBメモリの場合
  リクエスト: 1,000,000 x $0.0000002 = $0.20
  実行時間:  1,000,000 x 0.2秒 x 0.25GB x $0.0000166667 = $0.83
  合計: 約 $1.03/月

  ※ 無料枠: 月100万リクエスト + 40万GB-秒
```

---

## 6. API Gateway

API Gatewayは、APIの作成・公開・管理を行うフルマネージドサービスです。

### REST API vs HTTP API

| 観点 | REST API | HTTP API |
|------|---------|----------|
| 料金 | 高い（$3.50/100万リクエスト） | 安い（$1.00/100万リクエスト） |
| レイテンシ | 高め | 低い |
| 機能 | フル機能 | 軽量・基本機能 |
| 認証 | IAM, Cognito, Lambda Authorizer | IAM, JWT Authorizer, Lambda Authorizer |
| キャッシュ | あり | なし |
| リクエスト検証 | あり | なし |
| WAF統合 | あり | なし |
| 使用量プラン | あり | なし |
| 適性 | 高機能API、エンタープライズ | シンプルなAPI、コスト重視 |

### ステージとデプロイ

```
API Gateway
  |
  +-- /v1 (ステージ: dev)
  |     +-- GET  /users
  |     +-- POST /users
  |     +-- GET  /users/{id}
  |
  +-- /v1 (ステージ: prod)
        +-- GET  /users
        +-- POST /users
        +-- GET  /users/{id}
```

### 認証方式

| 方式 | 説明 | 適性 |
|------|------|------|
| IAM認証 | AWS SigV4署名で認証 | AWSサービス間通信 |
| Cognito Authorizer | Cognito User PoolのJWTで認証 | Cognitoユーザー向け |
| Lambda Authorizer | カスタムLambdaで認証ロジックを実装 | 外部IdP連携、カスタム認証 |
| JWT Authorizer（HTTP APIのみ） | JWTの検証を直接実行 | OIDC準拠IdP連携 |

---

## 7. API Gateway + Lambda 統合パターン

### Lambda プロキシ統合

最も一般的なパターンです。API Gatewayがリクエスト全体をLambdaに転送します。

```
クライアント                API Gateway              Lambda
    |                         |                       |
    | POST /token             |                       |
    | Content-Type: json      |                       |
    |------------------------>|                       |
    |                         | event = {             |
    |                         |   httpMethod: "POST", |
    |                         |   path: "/token",     |
    |                         |   headers: {...},     |
    |                         |   body: "..."         |
    |                         | }                     |
    |                         |---------------------->|
    |                         |                       |
    |                         |  { statusCode: 200,   |
    |                         |    headers: {...},     |
    |                         |    body: "..." }       |
    |                         |<----------------------|
    |    200 OK               |                       |
    |<------------------------|                       |
```

### Lambda Authorizer の統合

```
クライアント       API Gateway        Lambda Authorizer     バックエンドLambda
    |                 |                     |                     |
    | Authorization:  |                     |                     |
    | Bearer xxx      |                     |                     |
    |---------------->|                     |                     |
    |                 | token: "xxx"        |                     |
    |                 |-------------------->|                     |
    |                 |                     | トークン検証          |
    |                 |                     | ポリシー生成          |
    |                 | { Allow/Deny,       |                     |
    |                 |   context: {...} }  |                     |
    |                 |<--------------------|                     |
    |                 |                     |                     |
    |                 | (Allowの場合)        |                     |
    |                 |----------------------------------------->|
    |                 |                                           |
    |   200 OK        |<-----------------------------------------|
    |<----------------|                                           |
```

---

## 8. SQS（Simple Queue Service）

SQSは、マイクロサービス間の非同期メッセージングを実現するフルマネージドキューサービスです。

### 標準キュー vs FIFOキュー

| 観点 | 標準キュー | FIFOキュー |
|------|-----------|-----------|
| スループット | ほぼ無制限 | 300 msg/秒（バッチで3,000） |
| 順序保証 | ベストエフォート | 厳密な順序保証 |
| 重複配信 | 最低1回（重複あり） | 正確に1回 |
| 料金 | 安い | 標準の約1.2倍 |
| キュー名 | 任意 | `.fifo` サフィックス必須 |
| 適性 | 大量メッセージ、順序不要 | 順序重要、重複不可 |

### DLQ（Dead Letter Queue）

処理に失敗したメッセージを退避するキューです。

```
                  +------------------+
                  |   ソースキュー     |
プロデューサー ──→  |                  |
                  | 最大3回リトライ    |  ──→ コンシューマー（Lambda等）
                  +--------+---------+
                           |
                    (3回失敗)
                           |
                  +--------v---------+
                  |      DLQ         |  ← 手動調査・再処理用
                  | (失敗メッセージ)   |
                  +------------------+
```

```python
# SQSメッセージ送信例
import boto3
import json

sqs = boto3.client('sqs')

def send_security_event(event_data):
    response = sqs.send_message(
        QueueUrl=os.environ['SECURITY_EVENT_QUEUE_URL'],
        MessageBody=json.dumps(event_data),
        MessageAttributes={
            'EventType': {
                'DataType': 'String',
                'StringValue': event_data['event_type']
            }
        }
    )
    return response['MessageId']
```

---

## 9. SNS（Simple Notification Service）

SNSは、パブリッシュ/サブスクライブ型のメッセージングサービスです。1つのメッセージを複数のサブスクライバーに同時配信できます。

### トピックとサブスクリプション

```
                    +------------------+
                    |   SNSトピック      |
パブリッシャー ──→   | "security-events"|
                    +--------+---------+
                             |
              +--------------+--------------+
              |              |              |
     +--------v---+  +------v-----+  +-----v------+
     | SQSキュー   |  | Lambda     |  | Email      |
     | (処理用)    |  | (通知用)    |  | (管理者)    |
     +------------+  +------------+  +------------+
```

### ファンアウトパターン（SNS + SQS）

SNSトピックに複数のSQSキューをサブスクライブすることで、1つのイベントを複数の処理パイプラインに分配できます。

```
                         +------------------+
                         |   SNSトピック      |
  IDサービス ──→          | "user-events"    |
  (ユーザー登録イベント)    +--------+---------+
                                  |
                 +----------------+----------------+
                 |                |                |
        +--------v---+  +--------v---+  +---------v--+
        | SQS        |  | SQS        |  | SQS        |
        | 監査ログ    |  | 通知送信    |  | 分析基盤    |
        +------+-----+  +------+-----+  +------+-----+
               |                |                |
        +------v-----+  +------v-----+  +-------v----+
        | Lambda     |  | Lambda     |  | Lambda     |
        | (DynamoDB) |  | (SES送信)   |  | (S3保存)   |
        +------------+  +------------+  +------------+
```

---

## 10. EventBridge

EventBridgeは、イベント駆動型アーキテクチャの中核となるサーバーレスイベントバスです。

### 基本概念

```
イベントソース              EventBridge              ターゲット
                       +------------------+
AWSサービス     ──→     |                  | ──→  Lambda
(CloudTrail等)         |   イベントバス     |
                       |                  | ──→  SQS
カスタムアプリ   ──→     |   +----------+  |
(idp-server)           |   |  ルール    |  | ──→  Step Functions
                       |   | (パターン   |  |
SaaS           ──→     |   |  マッチング)|  | ──→  SNS
                       |   +----------+  |
                       +------------------+ ──→  API Gateway
```

### イベントルール

```json
{
  "source": ["idp-server"],
  "detail-type": ["SecurityEvent"],
  "detail": {
    "event_type": ["account.locked", "login.failed"],
    "severity": ["HIGH", "CRITICAL"]
  }
}
```

### スケジュール

```json
{
  "schedule": "rate(1 hour)",
  "target": "arn:aws:lambda:...:token-cleanup-function"
}
```

```json
{
  "schedule": "cron(0 2 * * ? *)",
  "target": "arn:aws:lambda:...:daily-report-function"
}
```

### EventBridge vs SNS

| 観点 | EventBridge | SNS |
|------|------------|-----|
| フィルタリング | イベント内容に基づく細かいルール | メッセージ属性での基本フィルタ |
| スケジュール | cron/rateルール対応 | 非対応 |
| スキーマ | スキーマレジストリで管理 | なし |
| ターゲット数 | 1ルールあたり最大5ターゲット | サブスクリプション数に応じて |
| レイテンシ | やや高い | 低い |
| 適性 | イベント駆動アーキテクチャ | シンプルなPub/Sub |

---

## 11. Kinesis Data Streams

Amazon Kinesis Data Streamsは、大量のストリーミングデータをリアルタイムに収集・処理するサービスです。SQSとは異なり、**同じデータを複数のコンシューマが並行して読み取れる**点と、**データの再読み取り（リプレイ）が可能**な点が特徴です。

### SQSとの違い

| 観点 | SQS | Kinesis Data Streams |
|------|-----|---------------------|
| データモデル | メッセージキュー（消費すると消える） | ストリーム（保持期間内は何度でも読める） |
| コンシューマ | 1つのメッセージは1つのコンシューマが処理 | 同じデータを複数のコンシューマが同時に処理 |
| 順序保証 | FIFOキューでのみ保証 | シャード内で厳密に保証 |
| データ保持 | 最大14日 | 最大365日 |
| リプレイ | 不可（処理済みメッセージは削除） | 任意の時点から再読み取り可能 |
| スループット | ほぼ無制限（標準キュー） | シャードあたり 1MB/秒 or 1000レコード/秒 |
| 課金 | リクエスト課金 | シャード時間課金（常時コスト発生） |

### いつ使うべきか

```
SQSを選ぶケース:
  - 1つのメッセージを1つのワーカーで処理する
  - 処理完了後にメッセージを消して問題ない
  - リクエスト課金でコストを抑えたい
  例: 監査ログの非同期書き込み、メール送信キュー

Kinesis Data Streamsを選ぶケース:
  - 同じデータを複数の用途で同時に処理したい
  - 過去のデータを再処理（リプレイ）する可能性がある
  - シャード内の順序保証が必要
  - 秒間数万〜数十万レコードのストリーム処理
  例: リアルタイム異常検知 + ログ保存を同時実行
```

### 基本アーキテクチャ

```
プロデューサー                    Kinesis Data Streams               コンシューマー

┌──────────┐                   ┌──────────────────────┐
│ App / SDK │ ─── PutRecord ──→│  Shard 1             │──→ Lambda (リアルタイム処理)
└──────────┘                   │  Shard 2             │──→ Lambda (リアルタイム処理)
                               │  Shard 3             │──→ Lambda (リアルタイム処理)
┌──────────┐                   │                      │
│ Agent /   │ ─── PutRecords ─→│  各シャード:          │──→ KCL App (集計処理)
│ Firehose  │                  │   1MB/秒 or           │
└──────────┘                   │   1000レコード/秒の    │──→ Kinesis Firehose (S3保存)
                               │   書き込み容量         │
                               │                      │
                               │  データ保持: 24時間〜365日│
                               └──────────────────────┘
```

### シャード設計

| 設計ポイント | 説明 |
|------------|------|
| シャード数 | 必要なスループットに基づいて決定（1シャード = 1MB/秒 or 1000レコード/秒） |
| パーティションキー | データの分散を決定。偏りがあるとホットシャードが発生 |
| オンデマンドモード | シャード数を自動管理。スループットが予測困難な場合に有効 |

### IDサービスでの活用例

```
リアルタイム異常検知パイプライン:

  idp-server                  Kinesis              Lambda              アクション
  (認証イベント)               Data Streams         (異常検知)

  login_success ─────→ ┌──────────┐         ┌──────────┐
  login_failure ─────→ │ Shard    │ ──────→ │ 集計     │ ──→ アラート通知
  account_locked ────→ │          │         │ 分析     │     (SNS → Slack)
                       └──────────┘         └──────────┘
                              │
                              └── 同時に別のコンシューマ ──→ S3 (ログ保存)
```

---

## 12. Kinesis Data Firehose

Amazon Kinesis Data Firehose（Amazon Data Firehose）は、ストリーミングデータをS3、Redshift、OpenSearch等の宛先に**自動的に配信するフルマネージドサービス**です。Kinesis Data Streamsのようなコンシューマアプリケーションの開発が不要で、データの変換・圧縮・バッチ化を自動処理します。

### Kinesis Data Streams との違い

| 観点 | Kinesis Data Streams | Kinesis Data Firehose |
|------|---------------------|----------------------|
| 目的 | リアルタイムストリーム処理 | 宛先への自動配信（ETL） |
| コンシューマ | 自分で作る（Lambda, KCL） | 不要（宛先を指定するだけ） |
| データ変換 | コンシューマで実装 | Lambda変換を組み込み可能 |
| バッチ化 | コンシューマで実装 | 自動（サイズ or 時間ベース） |
| 宛先 | 任意（コンシューマ次第） | S3, Redshift, OpenSearch, HTTP |
| 圧縮 | コンシューマで実装 | gzip, Snappy を自動適用 |
| フォーマット変換 | コンシューマで実装 | JSON → Parquet/ORC を自動変換 |

### 典型的な利用パターン

```
ログの自動集約と長期保存:

  CloudWatch Logs ──→ サブスクリプション ──→ Kinesis Firehose ──→ S3
  (アプリログ)        フィルター              ├ バッファリング      (パーティション配置)
                                             ├ gzip圧縮           year=2024/
                                             ├ Parquet変換        month=01/
                                             └ 60秒 or 1MBごと     day=15/
                                               にバッチ配信          data.parquet
                                                                      ↓
                                                                    Athena
                                                                   (SQLクエリ)
```

### 配信設定の主要パラメータ

| パラメータ | 説明 | 設定例 |
|-----------|------|--------|
| Buffer size | バッチ配信のサイズ閾値 | 1〜128 MB |
| Buffer interval | バッチ配信の時間閾値 | 60〜900 秒 |
| Compression | 圧縮形式 | GZIP, Snappy, 無圧縮 |
| Format conversion | フォーマット変換 | JSON → Parquet/ORC |
| Dynamic partitioning | S3パーティション分割 | tenant_id, year/month/day |

### いつ使うべきか

```
Firehoseが適している:
  - ログやイベントデータをS3に自動保存したい
  - Parquet変換やgzip圧縮を自動で行いたい
  - コンシューマのコードを書きたくない
  例: CloudWatch Logs → S3、認証イベント → S3 → Athena

Kinesis Data Streamsが適している:
  - リアルタイムでの処理ロジックが必要
  - 複数のコンシューマで同じデータを処理したい
  - データの再読み取り（リプレイ）が必要
  例: リアルタイム異常検知、複数の処理パイプライン
```

---

## 13. Step Functions

Step Functionsは、複数のAWSサービスを組み合わせたワークフローを視覚的に定義・実行するサービスです。

### ステートマシンの基本

```
+--------+     +-----------+     +---------+
| Start  | --> | 入力検証    | --> | 本人確認  |
+--------+     +-----------+     +----+----+
                                      |
                              +-------+-------+
                              |               |
                        +-----v-----+   +-----v-----+
                        | 書類確認   |   | 生体認証   |
                        | (Lambda)  |   | (Lambda)  |
                        +-----+-----+   +-----+-----+
                              |               |
                              +-------+-------+
                                      |
                                +-----v-----+
                                | 結果統合    |
                                | (Lambda)  |
                                +-----+-----+
                                      |
                              +-------+-------+
                              |               |
                        +-----v-----+   +-----v-----+
                        | 承認       |   | 否認       |
                        | (通知送信)  |   | (通知送信)  |
                        +-----+-----+   +-----+-----+
                              |               |
                              +-------+-------+
                                      |
                                 +----v----+
                                 |  End    |
                                 +---------+
```

### ASL（Amazon States Language）概要

```json
{
  "Comment": "身元確認ワークフロー",
  "StartAt": "ValidateInput",
  "States": {
    "ValidateInput": {
      "Type": "Task",
      "Resource": "arn:aws:lambda:...:validate-input",
      "Next": "ParallelVerification",
      "Catch": [{
        "ErrorEquals": ["ValidationError"],
        "Next": "NotifyRejection"
      }]
    },
    "ParallelVerification": {
      "Type": "Parallel",
      "Branches": [
        {
          "StartAt": "DocumentCheck",
          "States": {
            "DocumentCheck": {
              "Type": "Task",
              "Resource": "arn:aws:lambda:...:document-check",
              "End": true
            }
          }
        },
        {
          "StartAt": "BiometricAuth",
          "States": {
            "BiometricAuth": {
              "Type": "Task",
              "Resource": "arn:aws:lambda:...:biometric-auth",
              "End": true
            }
          }
        }
      ],
      "Next": "AggregateResults"
    },
    "AggregateResults": {
      "Type": "Task",
      "Resource": "arn:aws:lambda:...:aggregate-results",
      "Next": "DecisionChoice"
    },
    "DecisionChoice": {
      "Type": "Choice",
      "Choices": [
        {
          "Variable": "$.verified",
          "BooleanEquals": true,
          "Next": "NotifyApproval"
        }
      ],
      "Default": "NotifyRejection"
    },
    "NotifyApproval": {
      "Type": "Task",
      "Resource": "arn:aws:states:::sns:publish",
      "Parameters": {
        "TopicArn": "arn:aws:sns:...:verification-results",
        "Message.$": "$.message"
      },
      "End": true
    },
    "NotifyRejection": {
      "Type": "Task",
      "Resource": "arn:aws:states:::sns:publish",
      "Parameters": {
        "TopicArn": "arn:aws:sns:...:verification-results",
        "Message.$": "$.message"
      },
      "End": true
    }
  }
}
```

### ステートタイプ

| タイプ | 説明 | 用途 |
|--------|------|------|
| Task | リソースの呼び出し | Lambda実行、AWS API呼び出し |
| Choice | 条件分岐 | 値に応じた処理の振り分け |
| Parallel | 並列実行 | 複数処理の同時実行 |
| Map | 反復処理 | 配列要素ごとの処理 |
| Wait | 待機 | 指定時間の待機 |
| Pass | パススルー | データ変換、デバッグ |
| Succeed/Fail | 終了 | ワークフローの正常/異常終了 |

---

## 14. IDサービスでの活用

### Lambda Authorizer によるカスタム認証

idp-serverが発行したトークンを検証するLambda Authorizerの構成です。

```
クライアント        API Gateway       Lambda Authorizer       バックエンドAPI
    |                  |                    |                      |
    | Bearer JWT       |                    |                      |
    |----------------->|                    |                      |
    |                  | JWT token          |                      |
    |                  |------------------->|                      |
    |                  |                    |                      |
    |                  |                    | 1. JWKSエンドポイントから
    |                  |                    |    公開鍵を取得（キャッシュ）
    |                  |                    |    ↓                  |
    |                  |                    | 2. JWT署名検証         |
    |                  |                    |    ↓                  |
    |                  |                    | 3. claims検証          |
    |                  |                    |    (iss, aud, exp)    |
    |                  |                    |    ↓                  |
    |                  |                    | 4. IAMポリシー生成     |
    |                  |                    |                      |
    |                  | Allow + context   |                      |
    |                  |<-------------------|                      |
    |                  |                                           |
    |                  | リクエスト転送                              |
    |                  |----------------------------------------->|
    |  200 OK          |<-----------------------------------------|
    |<-----------------|                                           |
```

### SQS/SNSによるセキュリティイベント配信

idp-serverのセキュリティイベント（ログイン失敗、アカウントロック等）をサーバーレスで処理・配信する構成です。

```
+-------------+     +----------+     +------------------+
| idp-server  | --> | SNSトピック | --> | SQS: 監査ログ    | --> Lambda --> DynamoDB
| (イベント発行)|     | security  |     +------------------+
+-------------+     | -events  |
                    |          | --> | SQS: リアルタイム  | --> Lambda --> WebSocket
                    |          |     | 通知              |              (管理画面)
                    |          |     +------------------+
                    |          |
                    |          | --> | SQS: SIEM連携     | --> Lambda --> S3
                    +----------+     +------------------+     (ログ集約)
```

### イベント駆動の身元確認ワークフロー

Step Functionsで身元確認プロセスをオーケストレーションする構成です。

```
idp-server                EventBridge             Step Functions
(確認リクエスト)                                    (ワークフロー)
    |                          |                       |
    | PUT event               |                       |
    |------------------------->|                       |
    |                          | ルールマッチ           |
    |                          |---------------------->|
    |                          |                       |
    |                          |            +----------v----------+
    |                          |            | 1. 書類OCR (Lambda)  |
    |                          |            | 2. 顔照合 (Lambda)   |
    |                          |            | 3. 結果判定 (Lambda)  |
    |                          |            | 4. 結果通知 (SNS)     |
    |                          |            +----------+----------+
    |                          |                       |
    | Callback                 |                       |
    |<------------------------------------------------------------|
    | (確認結果を受信)           |                       |
```

---

## まとめ

- **サーバーレス**はサーバー管理不要で、イベント駆動・従量課金のアーキテクチャモデル
- **Lambda**はイベントに応じてコードを実行するコンピューティングサービス。コールドスタート対策としてProvisioned Concurrencyが利用可能
- **API Gateway**はREST/HTTP APIのフロントエンド。Lambda Authorizerで外部IdP連携が可能
- **SQS**は非同期メッセージキュー。標準キューとFIFOキューで用途を使い分け
- **SNS**はPub/Sub型の通知サービス。ファンアウトパターンで複数の処理パイプラインに分配
- **EventBridge**はイベントバス。細かいルールベースのフィルタリングとスケジュール実行が可能
- **Step Functions**はワークフローオーケストレーション。複雑な処理フローを視覚的に定義

## 次のステップ
- [Well-Architected Framework](../06-operations/aws-well-architected.md): AWSのアーキテクチャ設計原則と6つの柱を学ぶ

## 参考リソース
- [AWS Lambda ドキュメント](https://docs.aws.amazon.com/lambda/)
- [Amazon API Gateway ドキュメント](https://docs.aws.amazon.com/apigateway/)
- [Amazon SQS ドキュメント](https://docs.aws.amazon.com/sqs/)
- [Amazon SNS ドキュメント](https://docs.aws.amazon.com/sns/)
- [Amazon EventBridge ドキュメント](https://docs.aws.amazon.com/eventbridge/)
- [AWS Step Functions ドキュメント](https://docs.aws.amazon.com/step-functions/)
