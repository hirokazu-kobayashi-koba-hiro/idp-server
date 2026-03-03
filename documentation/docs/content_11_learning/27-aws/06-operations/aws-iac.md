# Infrastructure as Code（IaC）

クラウドインフラの構築・管理を手動操作ではなくコードで定義・自動化するアプローチを学びます。AWS CloudFormation、CDK、SAMを中心に、再現可能で安全なインフラ管理手法を解説します。

---

## 所要時間
約45分

## 学べること
- IaCの概念と手動運用との違い
- CloudFormationテンプレートの構造と組み込み関数
- スタック間参照とNested Stacks
- AWS CDKによるプログラマティックなインフラ定義
- CDKコンストラクトレベル（L1/L2/L3）の使い分け
- SAMによるサーバーレスインフラ定義
- CloudFormation/CDK/Terraformの比較と選定基準

## 前提知識
- AWSの基本サービス（VPC、EC2、RDS等）の理解
- YAML/JSONの基本構文
- プログラミングの基本知識（CDKセクション向け）

---

## 目次
1. [IaCの概要と必要性](#iacの概要と必要性)
2. [AWS CloudFormation概要](#aws-cloudformation概要)
3. [CloudFormationテンプレートの構造](#cloudformationテンプレートの構造)
4. [CloudFormation組み込み関数](#cloudformation組み込み関数)
5. [スタック間の参照](#スタック間の参照)
6. [AWS CDK（Cloud Development Kit）](#aws-cdkcloud-development-kit)
7. [CDKのコンストラクトレベル](#cdkのコンストラクトレベル)
8. [CDKコード例](#cdkコード例)
9. [SAM（Serverless Application Model）](#samserverless-application-model)
10. [IaCツール比較](#iacツール比較)
11. [IDサービスでの活用](#idサービスでの活用)
12. [まとめ](#まとめ)

---

## IaCの概要と必要性

Infrastructure as Code（IaC）は、サーバー、ネットワーク、データベースなどのインフラをコード（テンプレートファイル）として定義し、バージョン管理・自動デプロイする手法です。

### 手動運用の課題

```
手動運用の世界:

開発者A ──→ AWSコンソール ──→ 本番環境
  │            (手動クリック)      │
  │                              ├─ VPC作成（設定忘れ？）
  │                              ├─ SG設定（ポート開けすぎ？）
  │                              └─ RDS作成（パラメータ違う？）
  │
開発者B ──→ AWSコンソール ──→ ステージング環境
               (手動クリック)      │
                                 ├─ VPC作成（本番と設定が違う）
                                 ├─ SG設定（本番と不整合）
                                 └─ RDS作成（バージョン違い）

→ 環境差異、再現不可能、監査困難
```

### 手動運用 vs IaC 比較

| 観点 | 手動運用 | IaC |
|------|---------|-----|
| 再現性 | 手順書依存、人的ミスが発生 | コードで完全再現可能 |
| バージョン管理 | スクリーンショットや手順書 | Gitで変更履歴を管理 |
| レビュー | 作業後の目視確認 | Pull Requestで事前レビュー |
| 環境差異 | 環境ごとに微妙な違いが蓄積 | 同一テンプレートで統一 |
| 監査 | 操作ログの後追い確認 | コード差分で変更内容を把握 |
| スケール | 環境数に比例して工数増大 | パラメータ変更で複数環境に展開 |
| 障害復旧 | 手順を再実行（時間がかかる） | テンプレートから即座に再構築 |
| ドリフト検知 | 気づきにくい | 自動検知可能 |

---

## AWS CloudFormation概要

CloudFormationはAWSネイティブのIaCサービスです。テンプレートファイルからAWSリソースを自動的にプロビジョニングします。

### 主要概念

```
+------------------+
|   テンプレート     |  ← YAML/JSONファイル（インフラの設計図）
+--------+---------+
         |
         v
+------------------+
|    スタック        |  ← テンプレートから生成されるリソース群
|  +-------------+ |
|  | VPC         | |
|  | Subnet      | |
|  | EC2         | |
|  | RDS         | |
|  +-------------+ |
+--------+---------+
         |
         v
+------------------+
|    変更セット      |  ← スタック更新前の差分プレビュー
|  - Subnet追加    |
|  - EC2タイプ変更  |
|  - RDS変更なし   |
+------------------+
```

- **テンプレート**: インフラの宣言的定義（YAML/JSON形式）
- **スタック**: テンプレートからデプロイされたリソースの集合。スタック単位で作成・更新・削除を管理
- **変更セット（Change Set）**: スタック更新前に変更内容をプレビューする仕組み。意図しない変更を防止

---

## CloudFormationテンプレートの構造

CloudFormationテンプレートは複数のセクションで構成されます。

```yaml
AWSTemplateFormatVersion: "2010-09-09"
Description: IDサービス基盤ネットワーク

# --- パラメータ: デプロイ時に値を指定 ---
Parameters:
  EnvironmentName:
    Type: String
    Default: dev
    AllowedValues:
      - dev
      - staging
      - prod
    Description: デプロイ環境名

  VpcCidr:
    Type: String
    Default: "10.0.0.0/16"
    Description: VPCのCIDRブロック

# --- マッピング: 環境ごとの設定値 ---
Mappings:
  EnvironmentConfig:
    dev:
      InstanceType: t3.small
    staging:
      InstanceType: t3.medium
    prod:
      InstanceType: t3.large

# --- 条件: 条件付きリソース作成 ---
Conditions:
  IsProduction: !Equals [!Ref EnvironmentName, prod]

# --- リソース: 作成するAWSリソース（必須セクション） ---
Resources:
  VPC:
    Type: AWS::EC2::VPC
    Properties:
      CidrBlock: !Ref VpcCidr
      EnableDnsHostnames: true
      EnableDnsSupport: true
      Tags:
        - Key: Name
          Value: !Sub "${EnvironmentName}-vpc"

  PublicSubnet1:
    Type: AWS::EC2::Subnet
    Properties:
      VpcId: !Ref VPC
      CidrBlock: "10.0.1.0/24"
      AvailabilityZone: !Select [0, !GetAZs ""]
      MapPublicIpOnLaunch: true
      Tags:
        - Key: Name
          Value: !Sub "${EnvironmentName}-public-subnet-1"

  PublicSubnet2:
    Type: AWS::EC2::Subnet
    Properties:
      VpcId: !Ref VPC
      CidrBlock: "10.0.2.0/24"
      AvailabilityZone: !Select [1, !GetAZs ""]
      MapPublicIpOnLaunch: true

  PrivateSubnet1:
    Type: AWS::EC2::Subnet
    Properties:
      VpcId: !Ref VPC
      CidrBlock: "10.0.10.0/24"
      AvailabilityZone: !Select [0, !GetAZs ""]

  PrivateSubnet2:
    Type: AWS::EC2::Subnet
    Properties:
      VpcId: !Ref VPC
      CidrBlock: "10.0.11.0/24"
      AvailabilityZone: !Select [1, !GetAZs ""]

# --- 出力: スタック外から参照可能な値 ---
Outputs:
  VpcId:
    Description: VPC ID
    Value: !Ref VPC
    Export:
      Name: !Sub "${EnvironmentName}-VpcId"

  PublicSubnetIds:
    Description: パブリックサブネットID
    Value: !Join [",", [!Ref PublicSubnet1, !Ref PublicSubnet2]]
    Export:
      Name: !Sub "${EnvironmentName}-PublicSubnetIds"
```

### セクション一覧

| セクション | 必須 | 説明 |
|-----------|------|------|
| AWSTemplateFormatVersion | - | テンプレートバージョン（現在は`2010-09-09`のみ） |
| Description | - | テンプレートの説明 |
| Parameters | - | デプロイ時に指定する入力値 |
| Mappings | - | キーと値の静的マッピング |
| Conditions | - | リソース作成の条件 |
| Resources | 必須 | 作成するAWSリソースの定義 |
| Outputs | - | スタック外へ公開する値 |

---

## CloudFormation組み込み関数

テンプレート内で動的な値を扱うための組み込み関数を利用できます。

| 関数 | 用途 | 例 |
|------|------|-----|
| `Ref` | パラメータ値またはリソースIDの参照 | `!Ref VPC` → VPCのID |
| `Fn::Sub` | 文字列内の変数置換 | `!Sub "${Env}-vpc"` |
| `Fn::GetAtt` | リソースの属性値を取得 | `!GetAtt ALB.DNSName` |
| `Fn::Join` | 文字列の結合 | `!Join [",", [a, b]]` → `"a,b"` |
| `Fn::Select` | リスト要素の選択 | `!Select [0, !GetAZs ""]` |
| `Fn::Split` | 文字列の分割 | `!Split [",", "a,b,c"]` |
| `Fn::ImportValue` | 他スタックのExport値を参照 | `!ImportValue prod-VpcId` |
| `Fn::If` | 条件分岐 | `!If [IsProduction, t3.large, t3.small]` |
| `Fn::FindInMap` | Mappingsから値を取得 | `!FindInMap [Config, prod, Type]` |

```yaml
# 組み込み関数の使用例
Resources:
  TaskDefinition:
    Type: AWS::ECS::TaskDefinition
    Properties:
      ContainerDefinitions:
        - Name: idp-server
          # Fn::Sub でアカウントIDとリージョンを動的に埋め込み
          Image: !Sub "${AWS::AccountId}.dkr.ecr.${AWS::Region}.amazonaws.com/idp-server:latest"
          # Fn::If で環境に応じたCPU割り当て
          Cpu: !If [IsProduction, 1024, 256]
          # Fn::FindInMap でメモリ設定を取得
          Memory: !FindInMap [EnvironmentConfig, !Ref EnvironmentName, Memory]
```

---

## スタック間の参照

大規模なインフラでは、1つの巨大なテンプレートではなく、役割ごとにスタックを分割して管理します。

### クロススタック参照

```
+------------------+     Export/Import     +------------------+
|  ネットワーク      | ──────────────────→  |  アプリケーション    |
|  スタック          |                      |  スタック           |
|                  |                      |                  |
|  Outputs:        |                      |  Resources:      |
|    VpcId (Export)|                      |    ECS Service   |
|    SubnetIds     |                      |    (ImportValue)  |
+------------------+                      +------------------+
```

**エクスポート側**（ネットワークスタック）:
```yaml
Outputs:
  VpcId:
    Value: !Ref VPC
    Export:
      Name: !Sub "${EnvironmentName}-VpcId"
```

**インポート側**（アプリケーションスタック）:
```yaml
Resources:
  ECSCluster:
    Type: AWS::ECS::Cluster
    Properties:
      ClusterSettings:
        - Name: containerInsights
          Value: enabled

  ECSService:
    Type: AWS::ECS::Service
    Properties:
      Cluster: !Ref ECSCluster
      NetworkConfiguration:
        AwsvpcConfiguration:
          Subnets:
            - !ImportValue prod-PrivateSubnet1Id
            - !ImportValue prod-PrivateSubnet2Id
```

### Nested Stacks

親テンプレートから子テンプレートを呼び出す構成です。

```
+---------------------------+
|  親スタック (root.yaml)     |
|                           |
|  +---------+ +---------+  |
|  | network | | compute |  |
|  | .yaml   | | .yaml   |  |
|  +---------+ +---------+  |
|       |                   |
|  +---------+              |
|  | database|              |
|  | .yaml   |              |
|  +---------+              |
+---------------------------+
```

```yaml
# 親テンプレート
Resources:
  NetworkStack:
    Type: AWS::CloudFormation::Stack
    Properties:
      TemplateURL: https://s3.amazonaws.com/mybucket/network.yaml
      Parameters:
        EnvironmentName: !Ref EnvironmentName

  ComputeStack:
    Type: AWS::CloudFormation::Stack
    DependsOn: NetworkStack
    Properties:
      TemplateURL: https://s3.amazonaws.com/mybucket/compute.yaml
      Parameters:
        VpcId: !GetAtt NetworkStack.Outputs.VpcId
```

---

## AWS CDK（Cloud Development Kit）

AWS CDKは、TypeScript、Python、Javaなどのプログラミング言語でCloudFormationテンプレートを生成するフレームワークです。

### CDKの仕組み

```
TypeScript/Python   CDK Synth    CloudFormation     AWS API
コード           ──────────→   テンプレート      ──────────→  リソース
(app.ts)            (生成)      (template.json)     (デプロイ)
```

### CDKの利点

| 観点 | CloudFormation YAML | CDK |
|------|-------------------|-----|
| 言語 | YAML/JSON | TypeScript, Python, Java等 |
| 抽象度 | 低レベル（リソース単位） | 高レベル（パターン単位） |
| IDE支援 | 限定的 | 補完、型チェック、リファクタリング |
| ループ・条件 | 制限あり | 言語の全機能を利用可能 |
| テスト | 難しい | ユニットテスト・スナップショットテスト可能 |
| 再利用 | テンプレートコピー | ライブラリとして公開・共有 |

---

## CDKのコンストラクトレベル

CDKのコンストラクト（リソース定義の構成要素）は3つのレベルに分かれています。

| レベル | 名称 | 説明 | 例 |
|--------|------|------|-----|
| L1 | CFn Resources | CloudFormationリソースの1対1マッピング。`Cfn`プレフィックス | `CfnBucket`, `CfnVPC` |
| L2 | Curated | AWSベストプラクティスを含む高レベル抽象化。デフォルト設定付き | `Bucket`, `Vpc` |
| L3 | Patterns | 複数リソースを組み合わせたアーキテクチャパターン | `ApplicationLoadBalancedFargateService` |

```
L3: ApplicationLoadBalancedFargateService
    (ALB + ECS Fargate + ターゲットグループ + リスナー)
         |
         +-- L2: ApplicationLoadBalancer (セキュリティグループ自動設定)
         +-- L2: FargateService (タスク定義、サービス)
         +-- L2: Vpc (サブネット、ルートテーブル自動構成)
              |
              +-- L1: CfnVPC, CfnSubnet, CfnRouteTable ...
```

### 使い分けの指針

- **L3**: 標準的なアーキテクチャパターンに合致する場合に最適。最も少ないコードで構築可能
- **L2**: カスタマイズが必要な場合。ベストプラクティスのデフォルト設定を活用しつつ調整
- **L1**: CloudFormationの全プロパティへのアクセスが必要な場合。L2未対応の新サービスにも使用

---

## CDKコード例

VPC + ECS Fargate でIDサービスを構成する例です。

```typescript
import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as ecs_patterns from 'aws-cdk-lib/aws-ecs-patterns';
import * as rds from 'aws-cdk-lib/aws-rds';
import { Construct } from 'constructs';

export class IdpServerStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    // --- VPC（L2コンストラクト） ---
    const vpc = new ec2.Vpc(this, 'IdpVpc', {
      maxAzs: 2,
      natGateways: 1,
      subnetConfiguration: [
        {
          cidrMask: 24,
          name: 'Public',
          subnetType: ec2.SubnetType.PUBLIC,
        },
        {
          cidrMask: 24,
          name: 'Private',
          subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS,
        },
        {
          cidrMask: 24,
          name: 'Isolated',
          subnetType: ec2.SubnetType.PRIVATE_ISOLATED,
        },
      ],
    });

    // --- RDS（L2コンストラクト） ---
    const database = new rds.DatabaseCluster(this, 'IdpDatabase', {
      engine: rds.DatabaseClusterEngine.auroraPostgres({
        version: rds.AuroraPostgresEngineVersion.VER_15_4,
      }),
      writer: rds.ClusterInstance.provisioned('Writer', {
        instanceType: ec2.InstanceType.of(
          ec2.InstanceClass.R6G, ec2.InstanceSize.LARGE
        ),
      }),
      readers: [
        rds.ClusterInstance.provisioned('Reader', {
          instanceType: ec2.InstanceType.of(
            ec2.InstanceClass.R6G, ec2.InstanceSize.LARGE
          ),
        }),
      ],
      vpc,
      vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_ISOLATED },
      defaultDatabaseName: 'idp',
    });

    // --- ECS Fargate + ALB（L3コンストラクト） ---
    const fargateService =
      new ecs_patterns.ApplicationLoadBalancedFargateService(
        this, 'IdpService', {
          vpc,
          cpu: 1024,
          memoryLimitMiB: 2048,
          desiredCount: 2,
          taskImageOptions: {
            image: ecs.ContainerImage.fromAsset('../'),
            containerPort: 8080,
            environment: {
              SPRING_PROFILES_ACTIVE: 'prod',
              DB_HOST: database.clusterEndpoint.hostname,
              DB_PORT: database.clusterEndpoint.port.toString(),
              DB_NAME: 'idp',
            },
          },
          publicLoadBalancer: true,
        },
      );

    // --- ヘルスチェック設定 ---
    fargateService.targetGroup.configureHealthCheck({
      path: '/actuator/health',
      healthyHttpCodes: '200',
    });

    // --- RDSへの接続許可 ---
    database.connections.allowDefaultPortFrom(
      fargateService.service,
      'Allow ECS to RDS'
    );

    // --- Auto Scaling ---
    const scaling = fargateService.service.autoScaleTaskCount({
      minCapacity: 2,
      maxCapacity: 10,
    });
    scaling.scaleOnCpuUtilization('CpuScaling', {
      targetUtilizationPercent: 70,
    });
  }
}
```

---

## SAM（Serverless Application Model）

AWS SAMはCloudFormationの拡張で、Lambda、API Gateway、DynamoDB等のサーバーレスリソースを簡潔に定義できます。

```yaml
AWSTemplateFormatVersion: "2010-09-09"
Transform: AWS::Serverless-2016-10-31
Description: IDサービス - カスタム認証Lambda

Globals:
  Function:
    Timeout: 30
    Runtime: nodejs20.x
    MemorySize: 256

Resources:
  # Lambda Authorizer
  TokenAuthorizerFunction:
    Type: AWS::Serverless::Function
    Properties:
      CodeUri: src/authorizer/
      Handler: index.handler
      Environment:
        Variables:
          JWKS_URI: https://idp.example.com/.well-known/jwks.json
          ISSUER: https://idp.example.com

  # Webhook通知用Lambda
  SecurityEventFunction:
    Type: AWS::Serverless::Function
    Properties:
      CodeUri: src/security-event/
      Handler: index.handler
      Events:
        SecurityEventQueue:
          Type: SQS
          Properties:
            Queue: !GetAtt SecurityEventQueue.Arn
            BatchSize: 10

  SecurityEventQueue:
    Type: AWS::SQS::Queue
    Properties:
      VisibilityTimeout: 180
      MessageRetentionPeriod: 1209600
      RedrivePolicy:
        deadLetterTargetArn: !GetAtt SecurityEventDLQ.Arn
        maxReceiveCount: 3

  SecurityEventDLQ:
    Type: AWS::SQS::Queue
```

### SAM CLI コマンド

```bash
# ローカルでLambda関数をテスト
sam local invoke TokenAuthorizerFunction -e event.json

# ローカルでAPIを起動
sam local start-api

# ビルドとデプロイ
sam build
sam deploy --guided
```

---

## IaCツール比較

| 観点 | CloudFormation | CDK | Terraform |
|------|---------------|-----|-----------|
| 提供元 | AWS | AWS | HashiCorp |
| 定義言語 | YAML/JSON | TypeScript, Python等 | HCL |
| 対応クラウド | AWSのみ | AWSのみ | マルチクラウド |
| 状態管理 | AWSが自動管理 | AWSが自動管理 | tfstateファイル |
| 学習コスト | 中 | 中〜高（言語知識必要） | 中 |
| プレビュー | Change Set | cdk diff | terraform plan |
| ドリフト検知 | あり | あり（CFn経由） | あり |
| エコシステム | AWS公式 | Construct Hub | Terraform Registry |
| 適性 | AWS限定・小〜中規模 | AWS限定・大規模・チーム開発 | マルチクラウド・大規模 |

### 選定の指針

```
AWSのみ利用?
  ├─ Yes ──→ チーム規模・複雑さは?
  │           ├─ 小規模・シンプル ──→ CloudFormation
  │           └─ 大規模・複雑     ──→ CDK
  └─ No ───→ マルチクラウド?
              ├─ Yes ──→ Terraform
              └─ No ───→ プロジェクトに応じて選択
```

---

## IDサービスでの活用

idp-serverのインフラをCDKでコード化する場合の構成例を示します。

```
idp-server-infra/
├── bin/
│   └── app.ts                  # エントリポイント
├── lib/
│   ├── network-stack.ts        # VPC、サブネット、NAT Gateway
│   ├── database-stack.ts       # Aurora PostgreSQL（マルチAZ）
│   ├── compute-stack.ts        # ECS Fargate、ALB、Auto Scaling
│   ├── security-stack.ts       # WAF、ACM証明書、セキュリティグループ
│   └── monitoring-stack.ts     # CloudWatch、アラーム
├── config/
│   ├── dev.ts                  # 開発環境パラメータ
│   ├── staging.ts              # ステージング環境パラメータ
│   └── prod.ts                 # 本番環境パラメータ
├── test/
│   └── idp-server.test.ts      # インフラテスト
├── cdk.json
└── package.json
```

### スタック分割の設計

```
+------------------+
| NetworkStack     |  VPC, Subnets, NAT GW, VPN
+--------+---------+
         |
    +----+----+
    |         |
+---v---+ +---v------+
|Database| |Security  |  Aurora PostgreSQL, WAF, ACM
|Stack   | |Stack     |
+---+---+ +---+------+
    |         |
    +----+----+
         |
+--------v---------+
| ComputeStack     |  ECS Fargate, ALB, Auto Scaling
+--------+---------+
         |
+--------v---------+
| MonitoringStack  |  CloudWatch Dashboards, Alarms
+------------------+
```

### IDサービス固有の考慮点

| 要素 | IaCでの管理ポイント |
|------|-------------------|
| マルチテナント | テナントごとのリソース分離をコードで定義 |
| 秘密鍵管理 | Secrets ManagerをIaCで構成、鍵ローテーションを自動化 |
| FAPI準拠 | ALBのTLS設定、WAFルールをコードで厳密に管理 |
| 高可用性 | マルチAZ構成をテンプレートで標準化 |
| 環境分離 | 同一テンプレート + パラメータで環境ごとにデプロイ |

---

## まとめ

- IaCはインフラの**再現性**、**バージョン管理**、**自動化**を実現する手法
- CloudFormationはAWSネイティブのIaCサービスで、YAMLテンプレートでリソースを宣言的に定義
- CDKはプログラミング言語でCloudFormationテンプレートを生成し、型安全性やテスタビリティを向上
- コンストラクトレベル（L1/L2/L3）により、必要な抽象度でインフラを定義可能
- SAMはサーバーレスに特化した簡潔なIaC記法を提供
- ツール選定はクラウド戦略、チーム規模、複雑さに基づいて判断

## 次のステップ
- [サーバーレス](../02-computing/aws-serverless.md): Lambda、API Gateway、SQS/SNSを用いたイベント駆動アーキテクチャを学ぶ

## 参考リソース
- [AWS CloudFormation ユーザーガイド](https://docs.aws.amazon.com/cloudformation/)
- [AWS CDK ドキュメント](https://docs.aws.amazon.com/cdk/)
- [AWS SAM ドキュメント](https://docs.aws.amazon.com/serverless-application-model/)
- [Construct Hub](https://constructs.dev/)
