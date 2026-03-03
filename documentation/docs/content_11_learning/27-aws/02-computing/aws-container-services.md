# コンテナサービス

AWSは、コンテナ化されたアプリケーションのビルド、デプロイ、運用をサポートする複数のサービスを提供しています。コンテナレジストリ（ECR）、オーケストレーション（ECS/EKS）、サーバーレスコンピューティング（Fargate）を組み合わせることで、運用負荷の低いコンテナ環境を構築できます。

---

## 所要時間
約70分

## 学べること
- AWSのコンテナサービスの全体像と選択基準
- ECRによるコンテナイメージ管理
- ECSの概念（クラスター、タスク定義、サービス）
- Fargateによるサーバーレスコンテナの実行
- ECS vs EKSの選定基準
- OSSプロジェクトにおけるデプロイ方式の選択

## 前提知識
- Dockerの基礎（Dockerfile、イメージ、コンテナの概念）
- VPCとネットワーキングの基礎
- EC2の基本概念

---

## 目次

### ECSとEKSの共通基盤
1. [AWSにおけるコンテナの選択肢](#1-awsにおけるコンテナの選択肢)
2. [ECR（Elastic Container Registry）](#2-ecrelastic-container-registry)

### ECS（AWS独自オーケストレーション）
3. [ECS（Elastic Container Service）](#3-ecselastic-container-service)
4. [タスク定義の詳細](#4-タスク定義の詳細)
5. [ECS起動タイプ: EC2 vs Fargate](#5-ecs起動タイプ-ec2-vs-fargate)
6. [Fargate詳細](#6-fargate詳細)
7. [ECSサービス](#7-ecsサービス)

### EKS（マネージドKubernetes）
8. [EKS（Elastic Kubernetes Service）](#8-ekselastic-kubernetes-service)

### 選定と活用
9. [ECS vs EKS 選定基準](#9-ecs-vs-eks-選定基準)
10. [IDサービスでの活用](#10-idサービスでの活用)
11. [まとめ](#11-まとめ)

---

## 1. AWSにおけるコンテナの選択肢

AWSのコンテナサービスは、レジストリ、オーケストレーション、コンピューティングの3層で構成されます。

```
┌─────────────────────────────────────────────────────┐
│                   レジストリ層                        │
│                   ┌───────┐                          │
│                   │  ECR  │  コンテナイメージの保管   │
│                   └───┬───┘                          │
├───────────────────────┼─────────────────────────────┤
│               オーケストレーション層                  │
│          ┌────────────┼────────────┐                 │
│     ┌────┴────┐              ┌────┴────┐            │
│     │   ECS   │              │   EKS   │            │
│     │ (AWS独自)│              │  (K8s)  │            │
│     └────┬────┘              └────┬────┘            │
├──────────┼────────────────────────┼──────────────────┤
│                コンピューティング層                   │
│     ┌────┴────────────────────────┴────┐            │
│     │         ┌──────────┐             │            │
│     │    ┌────┴───┐  ┌───┴─────┐       │            │
│     │    │   EC2  │  │ Fargate │       │            │
│     │    │(自己管理)│  │(サーバー │       │            │
│     │    │        │  │ レス)    │       │            │
│     │    └────────┘  └─────────┘       │            │
│     └──────────────────────────────────┘            │
└─────────────────────────────────────────────────────┘
```

### 選択の組み合わせ

| 組み合わせ | オーケストレーション | コンピューティング | 運用負荷 |
|-----------|--------------------|--------------------|---------|
| ECS + Fargate | ECS | Fargate | 最も低い |
| ECS + EC2 | ECS | EC2 | 中程度 |
| EKS + Fargate | EKS | Fargate | 中程度 |
| EKS + EC2 | EKS | EC2 | 高い |

---

## 2. ECR（Elastic Container Registry）

ECRは、Dockerコンテナイメージを保存、管理、デプロイするためのフルマネージドなコンテナレジストリです。

### イメージのプッシュ

```bash
# ECRにログイン
aws ecr get-login-password --region ap-northeast-1 | \
  docker login --username AWS \
  --password-stdin 123456789012.dkr.ecr.ap-northeast-1.amazonaws.com

# イメージのビルド
docker build -t idp-server .

# タグ付け
docker tag idp-server:latest \
  123456789012.dkr.ecr.ap-northeast-1.amazonaws.com/idp-server:latest

# プッシュ
docker push \
  123456789012.dkr.ecr.ap-northeast-1.amazonaws.com/idp-server:latest
```

### イメージのプル

ECSタスクやEC2からイメージをプルする場合、IAMポリシーで`ecr:GetDownloadUrlForLayer`等の権限が必要です。VPCエンドポイント経由でプライベートにプルすることも可能です。

```
[ECS Task] → [VPC Endpoint (ecr.api)] → [ECR]
                                          │
           ← [VPC Endpoint (ecr.dkr)] ←──┘
                                          │
           ← [S3 Gateway Endpoint] ←──────┘ (イメージレイヤー)
```

### ライフサイクルポリシー

不要なイメージを自動削除するポリシーを設定できます。

```json
{
  "rules": [
    {
      "rulePriority": 1,
      "description": "最新10個のイメージを保持",
      "selection": {
        "tagStatus": "any",
        "countType": "imageCountMoreThan",
        "countNumber": 10
      },
      "action": {
        "type": "expire"
      }
    }
  ]
}
```

### ECRの主な機能

| 機能 | 説明 |
|------|------|
| イメージスキャン | 脆弱性を自動検出（Amazon Inspector連携） |
| イメージ暗号化 | KMSによる保存時暗号化 |
| クロスリージョンレプリケーション | 他リージョンへの自動複製 |
| イミュータブルタグ | タグの上書きを防止 |

---

## 3. ECS（Elastic Container Service）

ECSは、AWSが提供するフルマネージドなコンテナオーケストレーションサービスです。

### ECSの構成要素

```
┌─────────── ECS Cluster ───────────┐
│                                    │
│  ┌──── Service ──────────────┐     │
│  │                           │     │
│  │  ┌─ Task ──┐ ┌─ Task ──┐ │     │
│  │  │Container│ │Container│ │     │
│  │  │Container│ │Container│ │     │
│  │  └─────────┘ └─────────┘ │     │
│  │                           │     │
│  │  Task Definition          │     │
│  │  Desired Count: 2         │     │
│  └───────────────────────────┘     │
│                                    │
└────────────────────────────────────┘
```

### 概念の整理

| 概念 | 説明 | 例え |
|------|------|------|
| **クラスター** | タスクとサービスの論理グループ | データセンター |
| **タスク定義** | コンテナの実行仕様（設計図） | docker-compose.yml |
| **タスク** | タスク定義に基づく実行中のインスタンス | 実行中のコンテナ |
| **サービス** | タスクの維持・管理を行うコントローラー | Deployment Controller |

### クラスターの作成

ECSクラスターは論理的なグルーピングであり、Fargateを使用する場合、クラスター自体にインフラの管理は不要です。

```bash
aws ecs create-cluster --cluster-name idp-platform
```

---

## 4. タスク定義の詳細

タスク定義は、コンテナの実行方法を定義するJSON形式の設計図です。

### タスク定義のJSON例

```json
{
  "family": "idp-server",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "1024",
  "memory": "2048",
  "executionRoleArn": "arn:aws:iam::123456789012:role/ecsTaskExecutionRole",
  "taskRoleArn": "arn:aws:iam::123456789012:role/idp-server-task-role",
  "containerDefinitions": [
    {
      "name": "idp-server",
      "image": "123456789012.dkr.ecr.ap-northeast-1.amazonaws.com/idp-server:latest",
      "portMappings": [
        {
          "containerPort": 8080,
          "protocol": "tcp"
        }
      ],
      "environment": [
        {
          "name": "SPRING_PROFILES_ACTIVE",
          "value": "production"
        }
      ],
      "secrets": [
        {
          "name": "DB_PASSWORD",
          "valueFrom": "arn:aws:secretsmanager:ap-northeast-1:123456789012:secret:idp/db-password"
        }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/idp-server",
          "awslogs-region": "ap-northeast-1",
          "awslogs-stream-prefix": "ecs"
        }
      },
      "healthCheck": {
        "command": ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health || exit 1"],
        "interval": 30,
        "timeout": 5,
        "retries": 3,
        "startPeriod": 120
      },
      "essential": true
    }
  ]
}
```

### タスク定義の主要パラメータ

| パラメータ | 説明 |
|-----------|------|
| `family` | タスク定義のファミリー名（バージョン管理の単位） |
| `networkMode` | ネットワークモード（Fargateは`awsvpc`必須） |
| `cpu` / `memory` | タスクレベルのリソース割り当て |
| `executionRoleArn` | ECR Pull、ログ送信等に使用するIAMロール |
| `taskRoleArn` | タスク内アプリケーションが使用するIAMロール |
| `containerDefinitions` | コンテナの定義（複数指定可能） |

### IAMロールの使い分け

```
タスク実行ロール (executionRoleArn):
  - ECRからのイメージPull
  - CloudWatch Logsへのログ送信
  - Secrets Managerからのシークレット取得
  → ECSエージェントが使用

タスクロール (taskRoleArn):
  - S3へのアクセス
  - DynamoDBへのアクセス
  - その他AWSサービスへのアクセス
  → アプリケーション(idp-server)が使用
```

---

## 5. ECS起動タイプ: EC2 vs Fargate

### 比較表

| 項目 | EC2起動タイプ | Fargate起動タイプ |
|------|-------------|------------------|
| インフラ管理 | EC2インスタンスの管理が必要 | 不要（AWSが管理） |
| スケーリング | EC2 + タスクの二段階 | タスクのみ |
| リソース効率 | インスタンスの空きリソースを活用可能 | タスクに割り当てた分のみ |
| 料金モデル | EC2インスタンス料金 | vCPU + メモリの秒単位課金 |
| GPU対応 | あり | なし |
| コスト（大規模） | 安い場合がある | やや高い |
| 起動速度 | インスタンス起動に依存 | タスク起動のみ |
| OS管理 | パッチ適用が必要 | 不要 |

### EC2起動タイプが適する場面

- GPUが必要なワークロード
- 大規模で常時安定したワークロード（コスト最適化）
- 特定のインスタンスタイプが必要な場合
- Windows Serverコンテナ

### Fargate起動タイプが適する場面

- インフラ管理を最小化したい場合
- トラフィックの変動が大きいワークロード
- 小〜中規模のサービス
- 迅速なデプロイ・スケーリングが必要な場合

---

## 6. Fargate詳細

Fargateは、コンテナのためのサーバーレスコンピューティングエンジンです。EC2インスタンスのプロビジョニングや管理が不要で、タスク単位でリソースを指定するだけでコンテナを実行できます。

### Fargateの仕組み

```
従来のEC2起動タイプ:
  [EC2 Instance]
   ├── [タスクA] (CPU: 256, Memory: 512)
   ├── [タスクB] (CPU: 512, Memory: 1024)
   └── (空きリソース - 無駄)
  → インスタンスのキャパシティ管理が必要

Fargate:
  [タスクA] (CPU: 256, Memory: 512)   ← 独立したマイクロVM
  [タスクB] (CPU: 512, Memory: 1024)  ← 独立したマイクロVM
  → タスクごとに隔離された環境
```

### Fargateの料金モデル

| リソース | 料金（東京リージョン概算） |
|---------|------------------------|
| vCPU | 約$0.05056/vCPU/時間 |
| メモリ | 約$0.00553/GB/時間 |

秒単位で課金され、最低1分間の課金です。

### 利用可能なCPU/メモリの組み合わせ

| CPU (vCPU) | メモリ (GB) |
|-----------|------------|
| 0.25 | 0.5, 1, 2 |
| 0.5 | 1 - 4 |
| 1 | 2 - 8 |
| 2 | 4 - 16 |
| 4 | 8 - 30 |
| 8 | 16 - 60 |
| 16 | 32 - 120 |

### Fargate Spot

Fargate Spotは、割り込みの可能性がある代わりに最大70%割引で利用できるオプションです。EC2スポットインスタンスと同様の概念です。

---

## 7. ECSサービス

ECSサービスは、指定された数のタスクを維持し、ロードバランサーとの連携やデプロイ管理を行うコンポーネントです。

### サービスの主な機能

| 機能 | 説明 |
|------|------|
| タスク数の維持 | 障害時に自動的にタスクを再起動 |
| ロードバランサー連携 | ALB/NLBへのターゲット自動登録 |
| デプロイ管理 | ローリングアップデート、Blue/Green |
| Auto Scaling | タスク数の自動増減 |
| サービスディスカバリ | AWS Cloud Map連携 |

### デプロイ戦略

#### ローリングアップデート

```
Step 1: 新バージョンのタスクを起動
  [v1] [v1] [v2(new)]

Step 2: 新タスクがヘルスチェックを通過後、旧タスクを停止
  [v1] [v2] [v2]

Step 3: 全タスクが新バージョンに切り替わり完了
  [v2] [v2] [v2]

設定パラメータ:
  minimumHealthyPercent: 100  (常に最低1台は稼働)
  maximumPercent: 200          (最大で倍のタスクまで起動)
```

#### Blue/Greenデプロイ（CodeDeploy連携）

```
Step 1: Blue環境で稼働中
  [ALB] → [ターゲットグループ Blue] → [v1] [v1]

Step 2: Green環境に新バージョンをデプロイ
  [ALB] → [ターゲットグループ Blue]  → [v1] [v1]
           [ターゲットグループ Green] → [v2] [v2]  (テスト中)

Step 3: トラフィックを切り替え
  [ALB] → [ターゲットグループ Green] → [v2] [v2]  (本番)
           [ターゲットグループ Blue]  → [v1] [v1]  (ロールバック用)

Step 4: 旧環境を削除
  [ALB] → [ターゲットグループ Green] → [v2] [v2]
```

### ローリング vs Blue/Green

| 項目 | ローリング | Blue/Green |
|------|-----------|------------|
| デプロイ速度 | やや遅い | 一括切替で速い |
| リソース消費 | 少ない | 2倍のリソースが一時必要 |
| ロールバック | 再デプロイが必要 | 即座にロールバック可能 |
| テスト | デプロイ中のテストが困難 | 切替前にテスト可能 |
| 設定の複雑さ | シンプル | CodeDeploy連携が必要 |

### サービスディスカバリ

AWS Cloud Mapと連携して、ECSタスクにDNS名を付与できます。マイクロサービス間のサービス検出に利用します。

```
[サービスA] → DNS: service-b.local → [サービスBのタスクIP]
                 (Cloud Map)
```

---

## 8. EKS（Elastic Kubernetes Service）

EKSは、AWSが提供するマネージドKubernetesサービスです。Kubernetesのコントロールプレーンをフルマネージドで提供し、ワーカーノードの管理はユーザーが行います。OSSプロジェクトのデプロイ先として、ポータビリティとエコシステムの広さから特に重要なサービスです。

### EKSのアーキテクチャ

```
┌─── AWS マネージド ───────────┐    ┌─── ユーザー管理 ───────────────┐
│                              │    │                                │
│  EKS Control Plane           │    │  Worker Nodes                  │
│  ┌────────────────────────┐  │    │  ┌──────────┐ ┌──────────┐    │
│  │ API Server (Multi-AZ)  │◄─┼────┼──┤ Node (EC2)│ │ Node (EC2)│   │
│  │ etcd (暗号化・冗長化)   │  │    │  │  [Pod]   │ │  [Pod]   │    │
│  │ Controller Manager     │  │    │  │  [Pod]   │ │  [Pod]   │    │
│  │ Scheduler              │  │    │  └──────────┘ └──────────┘    │
│  │ Cloud Controller       │  │    │                                │
│  └────────────────────────┘  │    │  または Fargate Profile        │
│                              │    │  [Pod] [Pod] (サーバーレス)     │
│  自動アップグレード対応       │    │                                │
│  SLA 99.95%                  │    │  または Karpenter              │
└──────────────────────────────┘    │  (自動ノードプロビジョニング)    │
                                    └────────────────────────────────┘
```

### クラスターの作成

```bash
# eksctlを使用したクラスター作成
eksctl create cluster \
  --name idp-platform \
  --region ap-northeast-1 \
  --version 1.31 \
  --nodegroup-name idp-nodes \
  --node-type t3.large \
  --nodes 2 \
  --nodes-min 2 \
  --nodes-max 8 \
  --managed

# kubeconfigの更新
aws eks update-kubeconfig --name idp-platform --region ap-northeast-1
```

### ノードグループの種類

| 種類 | 説明 | 管理負荷 | 適する場面 |
|------|------|---------|-----------|
| **マネージドノードグループ** | EC2のプロビジョニング・更新をAWSが管理 | 中 | 標準的なワークロード |
| **Karpenter** | 需要に応じてノードを自動プロビジョニング | 低〜中 | トラフィック変動が大きい場合 |
| **Fargate Profile** | Podごとにサーバーレス実行 | 低 | バッチ処理、小規模ワークロード |
| **セルフマネージドノード** | EC2を自身で管理 | 高 | GPU、特殊なカーネル要件 |

### IRSA（IAM Roles for Service Accounts）

EKSの最も重要なAWS連携機能です。KubernetesのService AccountにIAMロールを紐づけることで、Pod単位で最小権限のAWSアクセスを実現します。

```
┌─── Kubernetes ───┐     ┌─── AWS IAM ──────────────────┐
│                   │     │                               │
│  ServiceAccount   │────▶│  IAM Role                     │
│  "idp-server-sa"  │     │  "idp-server-role"            │
│                   │     │                               │
│  Pod              │     │  Policy:                      │
│  ├── idp-server   │     │  ├── secretsmanager:Get*      │
│  └── SA: above    │     │  ├── kms:Decrypt              │
│                   │     │  └── s3:GetObject              │
└───────────────────┘     └───────────────────────────────┘

従来方式: Node全体に権限 → 過剰な権限
IRSA:     Pod単位に権限 → 最小権限の原則
```

```yaml
# ServiceAccountにIAMロールを紐づけ
apiVersion: v1
kind: ServiceAccount
metadata:
  name: idp-server-sa
  namespace: idp-system
  annotations:
    eks.amazonaws.com/role-arn: arn:aws:iam::123456789012:role/idp-server-role
```

### EKS Add-ons

EKSはクラスターの基盤機能をAdd-onとして管理します。AWSが検証済みのバージョンを提供し、自動更新が可能です。

| Add-on | 役割 | 必須 |
|--------|------|------|
| **VPC CNI** | PodにVPC内のIPアドレスを直接割り当て | はい |
| **CoreDNS** | クラスタ内DNS（Service名 → Pod IP の名前解決） | はい |
| **kube-proxy** | Service → Pod のネットワーク転送ルール管理 | はい |
| **AWS Load Balancer Controller** | Ingress/Service作成時にALB/NLBを自動プロビジョニング | 推奨 |
| **EBS CSI Driver** | PersistentVolumeとしてEBSを利用 | 必要時 |
| **EFS CSI Driver** | PersistentVolumeとしてEFSを利用（共有ストレージ） | 必要時 |

### ネットワーキング（VPC CNI）

EKSのネットワーキングは、AWS VPC CNIプラグインにより、PodがVPCのIPアドレスを直接取得するのが特徴です。

```
┌─── VPC (10.0.0.0/16) ────────────────────────────────┐
│                                                        │
│  ┌─ Subnet (10.0.1.0/24) ──────────────────────┐     │
│  │                                               │     │
│  │  Node (10.0.1.10)                             │     │
│  │  ├── Pod A (10.0.1.20)  ← VPCの実IPを取得    │     │
│  │  └── Pod B (10.0.1.21)  ← VPCの実IPを取得    │     │
│  │                                               │     │
│  └───────────────────────────────────────────────┘     │
│                                                        │
│  メリット: ALB/NLBがPodに直接ルーティング可能           │
│  メリット: SecurityGroupをPod単位で適用可能             │
│  注意: SubnetのIPアドレスを消費する                     │
└────────────────────────────────────────────────────────┘
```

### Ingress（ALB連携）

AWS Load Balancer Controllerにより、KubernetesのIngressリソースからALBを自動作成します。

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: idp-server-ingress
  namespace: idp-system
  annotations:
    kubernetes.io/ingress.class: alb
    alb.ingress.kubernetes.io/scheme: internet-facing
    alb.ingress.kubernetes.io/target-type: ip
    alb.ingress.kubernetes.io/certificate-arn: arn:aws:acm:ap-northeast-1:123456789012:certificate/xxxxx
    alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
    alb.ingress.kubernetes.io/healthcheck-path: /actuator/health
spec:
  rules:
    - host: auth.example.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: idp-server
                port:
                  number: 8080
```

```
[クライアント] → [ALB (自動作成)] → [Pod IP直接ルーティング]
                                      (VPC CNIにより可能)
```

### シークレット管理

KubernetesのSecretとAWS Secrets Managerを連携させることで、機密情報を安全に管理します。

```
方式1: External Secrets Operator（推奨）
  [AWS Secrets Manager] ←── [External Secrets Operator] ──→ [K8s Secret]
  DBパスワード等を一元管理    定期的に同期                     Podから参照

方式2: Secrets Store CSI Driver
  [AWS Secrets Manager] ←── [CSI Driver] ──→ [Pod Volume]
  シークレットをファイルとしてマウント
```

### オブザーバビリティ

EKSでは、AWSネイティブツールとOSSツールの両方を活用できます。

| ツール | 種類 | 用途 |
|--------|------|------|
| **CloudWatch Container Insights** | AWSネイティブ | クラスタ/ノード/Podのメトリクス・ログ |
| **Prometheus + Grafana** | OSS | メトリクス収集・可視化（CNCF標準） |
| **AWS Distro for OpenTelemetry** | AWS + OSS | 分散トレーシング・メトリクス収集 |
| **Fluent Bit** | OSS | ログ収集・転送（CloudWatch/S3/OpenSearch） |

```
┌─── EKS Cluster ────────────────────────────────────┐
│                                                      │
│  [Pod] ──metrics──▶ [Prometheus] ──▶ [Grafana]      │
│  [Pod] ──logs────▶ [Fluent Bit] ──▶ [CloudWatch]   │
│  [Pod] ──traces──▶ [ADOT] ──▶ [X-Ray]              │
│                                                      │
└──────────────────────────────────────────────────────┘
```

### EKSのセキュリティ

| レイヤー | 機能 | 説明 |
|---------|------|------|
| **認証** | IAM + OIDC | AWS IAMユーザー/ロールでkubectlアクセス |
| **認可** | RBAC | Namespace単位のアクセス制御 |
| **Pod セキュリティ** | Pod Security Standards | Privileged/Baseline/Restrictedの3レベル |
| **ネットワーク** | Network Policy + SG for Pods | Pod間通信の制御 |
| **暗号化** | KMS | etcd内のSecretを暗号化 |
| **監査** | CloudTrail + Audit Log | API Server操作の記録 |

### EKSの主な特徴まとめ

- Kubernetesの標準APIと完全互換。**kubectl、Helm、ArgoCD等のK8sエコシステムがそのまま利用可能**
- コントロールプレーンは自動的に複数AZに分散（SLA 99.95%）
- **IRSA**によるPod単位のIAMロール割り当てで最小権限を実現
- **VPC CNI**でPodにVPC IPを直接割り当て、ALBからの直接ルーティングが可能
- **AWS Load Balancer Controller**でIngress作成時にALB/NLBを自動プロビジョニング
- **Karpenter**による需要ベースのノード自動プロビジョニング
- GKE/AKS/オンプレK8sと同じマニフェストで運用可能（ポータビリティ）

---

## 9. ECS vs EKS 選定基準

### 比較表

| 項目 | ECS | EKS |
|------|-----|-----|
| 学習コスト | 低い（AWS独自だがシンプル） | 高い（Kubernetes知識が必要） |
| エコシステム | AWS中心 | Kubernetes エコシステム全体 |
| ポータビリティ | AWSに固有 | マルチクラウド対応 |
| マネジメントコンソール | 充実 | AWS + kubectl |
| コントロールプレーン費用 | 無料 | 約$0.10/時間（約$73/月） |
| サービスメッシュ | App Mesh | Istio, Linkerd等 |
| CI/CD | CodePipeline連携が容易 | ArgoCD, Flux等の選択肢 |
| 運用の複雑さ | 低い | 高い |

### 選定の指針

```
ECSを選ぶべき場合:
  ├── AWSに集中している
  ├── チームにKubernetes経験が少ない
  ├── シンプルなコンテナ運用が目的
  ├── 運用負荷を最小化したい
  └── コストを抑えたい

EKSを選ぶべき場合:
  ├── マルチクラウド/ハイブリッド環境
  ├── チームにKubernetes経験がある
  ├── Kubernetesエコシステムを活用したい
  ├── 複雑なワークロード管理が必要
  └── オンプレミスからの移行（EKS Anywhere）
```

---

## 10. IDサービスでの活用

### デプロイ方式の選択

idp-serverはOSSとして提供されており、利用者のインフラ環境は多様です。デプロイ方式は利用シーンに応じて選択します。

| 方式 | 推奨シーン | 備考 |
|------|-----------|------|
| **Kubernetes（EKS/GKE/AKS）** | OSS導入。既存K8sクラスタがある。マルチクラウド・ポータビリティ重視 | Helm chartで統一的にデプロイ |
| **ECS Fargate** | AWS専用環境。小規模チーム。K8s運用経験がない | AWS固有だが運用負荷が最も低い |
| **Docker Compose** | 開発・検証環境 | `docker compose up` で即座に起動 |

### OSSにおけるKubernetes推奨の理由

OSSとしてのidp-serverのデプロイには、**Kubernetes を推奨**します。

| 観点 | Kubernetes | ECS Fargate |
|------|-----------|-------------|
| **ポータビリティ** | 同じマニフェストでAWS/GCP/Azure/オンプレに対応 | AWS専用。他クラウドに移行不可 |
| **ユーザー層** | 既にK8sクラスタを持つチームがそのまま導入可能 | AWSに閉じた運用チーム向け |
| **エコシステム** | Helm chart配布、Prometheus/Grafana等の標準スタック | AWS固有のツール |
| **コミュニティ** | K8sコミュニティ全体がターゲット | AWS限定 |
| **デプロイの標準化** | `helm install` で統一的にデプロイ | AWS CLI / CloudFormation が必要 |

OSSプロジェクトでは**利用者がどのクラウドを使っているか分からない**ため、特定のクラウドサービスに依存しないデプロイ方式が望ましいです。Kubernetesは事実上の標準オーケストレーターであり、最も広いユーザー層をカバーできます。

```
開発環境:  docker compose up
本番環境:  helm install idp-server ./charts/idp-server
```

### Kubernetesデプロイ構成

```
┌─── Kubernetes Cluster ──────────────────────────────────┐
│                                                          │
│  ┌──── Namespace: idp-system ────────────────────┐      │
│  │                                                │      │
│  │  Deployment: idp-server (replicas: 2)          │      │
│  │  ┌─ Pod (AZ-a) ────────┐ ┌─ Pod (AZ-c) ────┐ │      │
│  │  │ idp-server:8080      │ │ idp-server:8080  │ │      │
│  │  │ CPU: 1000m           │ │ CPU: 1000m       │ │      │
│  │  │ Memory: 2Gi          │ │ Memory: 2Gi      │ │      │
│  │  └──────────────────────┘ └──────────────────┘ │      │
│  │                                                │      │
│  │  Service: idp-server (ClusterIP)               │      │
│  │  Ingress / LoadBalancer → Service → Pods       │      │
│  │                                                │      │
│  │  HPA: min=2, max=8, targetCPU=50%              │      │
│  └────────────────────────────────────────────────┘      │
│                                                          │
│  ConfigMap: idp-server-config                            │
│  Secret: idp-server-secrets                              │
└──────────────────────────────────────────────────────────┘
```

### Kubernetesマニフェスト例

```yaml
# Deployment
apiVersion: apps/v1
kind: Deployment
metadata:
  name: idp-server
  namespace: idp-system
spec:
  replicas: 2
  selector:
    matchLabels:
      app: idp-server
  template:
    metadata:
      labels:
        app: idp-server
    spec:
      containers:
        - name: idp-server
          image: ghcr.io/your-org/idp-server:latest
          ports:
            - containerPort: 8080
          resources:
            requests:
              cpu: "1000m"
              memory: "2Gi"
            limits:
              cpu: "2000m"
              memory: "4Gi"
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: "production"
            - name: DB_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: idp-server-secrets
                  key: db-password
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 120
            periodSeconds: 30
      topologySpreadConstraints:
        - maxSkew: 1
          topologyKey: topology.kubernetes.io/zone
          whenUnsatisfiable: DoNotSchedule
          labelSelector:
            matchLabels:
              app: idp-server
---
# HorizontalPodAutoscaler
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: idp-server
  namespace: idp-system
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: idp-server
  minReplicas: 2
  maxReplicas: 8
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 50
```

### ECS Fargateデプロイ構成（AWS専用環境向け）

K8s運用経験がなくAWS環境に閉じている場合は、ECS Fargateも有効な選択肢です。

```
┌──────────────── ECS Cluster: idp-platform ────────────────┐
│                                                            │
│  ┌──── Service: idp-server ──────────────────────┐        │
│  │  Desired Count: 2                              │        │
│  │  Launch Type: FARGATE                          │        │
│  │                                                │        │
│  │  ┌─ Task (AZ-a) ─────┐ ┌─ Task (AZ-c) ─────┐│        │
│  │  │ idp-server:8080    │ │ idp-server:8080    ││        │
│  │  │ CPU: 1024 (1vCPU)  │ │ CPU: 1024 (1vCPU)  ││        │
│  │  │ Memory: 2048 (2GB) │ │ Memory: 2048 (2GB) ││        │
│  │  └────────────────────┘ └────────────────────┘│        │
│  └────────────────────────────────────────────────┘        │
│                                                            │
│  [ALB] → ターゲットグループ → タスク群                      │
│                                                            │
│  Auto Scaling: Min=2, Max=8, ターゲット追跡: CPU 50%       │
└────────────────────────────────────────────────────────────┘
```

#### ECSサービスの設定例

```json
{
  "serviceName": "idp-server",
  "cluster": "idp-platform",
  "taskDefinition": "idp-server:latest",
  "desiredCount": 2,
  "launchType": "FARGATE",
  "networkConfiguration": {
    "awsvpcConfiguration": {
      "subnets": ["subnet-private-az-a", "subnet-private-az-c"],
      "securityGroups": ["sg-app"],
      "assignPublicIp": "DISABLED"
    }
  },
  "loadBalancers": [
    {
      "targetGroupArn": "arn:aws:elasticloadbalancing:...:targetgroup/idp-server/...",
      "containerName": "idp-server",
      "containerPort": 8080
    }
  ],
  "deploymentConfiguration": {
    "minimumHealthyPercent": 100,
    "maximumPercent": 200
  },
  "healthCheckGracePeriodSeconds": 120
}
```

#### ECS Fargateの利点

| 理由 | 説明 |
|------|------|
| 運用負荷の最小化 | インフラ管理不要。セキュリティ運用に集中できる |
| セキュリティ | タスク間が隔離され、OSレベルのパッチ管理が不要 |
| スケーラビリティ | タスク単位で迅速にスケール |
| コスト予測性 | タスク単位の従量課金 |

### デプロイパイプラインの全体像

```
Kubernetes環境:
  [Git Push] → [CI (GitHub Actions等)]
                    │
               ┌────┴────┐
               │ Build &  │
               │ Push     │ → [Container Registry]
               └────┬────┘
                    │
               [Helm upgrade / kubectl apply]
                    │
               ┌────┴──────────────┐
               │ ローリングアップデート│
               │ 新Pod起動          │
               │ Readiness通過      │
               │ 旧Pod停止          │
               └───────────────────┘

ECS環境:
  [Git Push] → [CodePipeline]
                    │
               [CodeBuild] → [ECR Push]
                    │
               [ECS Deploy]
                    │
               ┌────┴──────────────┐
               │ ローリングアップデート│
               │ 新タスク起動        │
               │ ヘルスチェック通過   │
               │ 旧タスク停止        │
               └───────────────────┘
```

---

## 11. まとめ

- **ECR**はコンテナイメージの保管・管理に使用し、ライフサイクルポリシーで不要イメージを自動削除する
- **ECS**はAWS独自のコンテナオーケストレーションサービスで、クラスター/タスク定義/サービスの3層で構成される
- **Fargate**はサーバーレスなコンピューティングエンジンで、インフラ管理なしでコンテナを実行できる
- **EKS**はマネージドKubernetesで、K8sエコシステムを活用したい場合やマルチクラウド要件がある場合に選択する
- **idp-serverのデプロイ**はOSSとしてのポータビリティを重視し**Kubernetesを推奨**。AWS専用環境ではECS Fargateも有効

## 次のステップ
- [データベースサービス（RDS）](../03-data-storage/aws-rds-database.md) - コンテナから接続するデータベースについて学ぶ

## 参考リソース
- [Amazon ECS デベロッパーガイド](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/)
- [Amazon ECR ユーザーガイド](https://docs.aws.amazon.com/AmazonECR/latest/userguide/)
- [AWS Fargate ユーザーガイド](https://docs.aws.amazon.com/AmazonECS/latest/userguide/what-is-fargate.html)
- [Amazon EKS ユーザーガイド](https://docs.aws.amazon.com/eks/latest/userguide/)
