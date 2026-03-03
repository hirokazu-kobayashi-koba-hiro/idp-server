# AWS

AWS（Amazon Web Services）は、世界で最も広く利用されているクラウドプラットフォームです。このセクションでは、AWSの基礎概念からセキュリティ、運用まで体系的に学びます。

---

## クラウドを学ぶ意義

| | オンプレミス | クラウド（AWS） |
|---|---|---|
| 調達 | サーバー購入（数週間〜数ヶ月） | 必要な分だけ数分で起動 |
| コスト | 初期投資が大きい | 従量課金、初期費用ゼロ |
| 拡張 | ハードウェア追加が必要 | ボタンひとつでスケール |

クラウドを理解することで:

- インフラをコードで管理し、再現性を確保できる
- 需要に応じてスケールイン/アウトできる
- 世界中のリージョンに展開できる
- マネージドサービスで運用負荷を軽減できる

---

## 学習ロードマップ

| Step | テーマ | トピック |
|------|--------|---------|
| Step 1 | 基礎を理解する | 01 AWS基礎（リージョン/AZ）、02 IAM（認証・認可）、03 VPC（ネットワーク基盤） |
| Step 2 | コンピューティング | 04 EC2（仮想サーバー）、05 コンテナ（ECS/EKS/Fargate）、06 サーバーレス（Lambda/API GW） |
| Step 3 | データ・ストレージ | 07 データベース（RDS/Aurora/DynamoDB）、08 S3/ストレージ（Object Lock/Athena） |
| Step 4 | ネットワーク・配信 | 09 ロードバランサ（ALB/NLB）、10 Route53/CloudFront（DNS/CDN） |
| Step 5 | セキュリティ・監視 | 11 セキュリティ（KMS/WAF/Shield）、12 モニタリング（CloudWatch/X-Ray） |
| Step 6 | 運用・設計 | 13 IaC（CFn/CDK）、14 Well-Architected、15 大量データの設計パターン |

---

## コンテンツ一覧

### 基礎

| # | ドキュメント | 内容 | 所要時間 |
|---|-------------|------|---------|
| 01 | [AWS基礎](01-fundamentals/aws-fundamentals.md) | リージョン、AZ、グローバルインフラ、料金モデル、AWS CLI | 30分 |
| 02 | [IAM](01-fundamentals/aws-iam.md) | ユーザー、グループ、ロール、ポリシー、MFA、STS | 45分 |
| 03 | [VPC・ネットワーキング](01-fundamentals/aws-vpc-networking.md) | VPC、サブネット、SG、NACL、NAT Gateway、VPN、PrivateLink | 50分 |

### コンピューティング

| # | ドキュメント | 内容 | 所要時間 |
|---|-------------|------|---------|
| 04 | [EC2](02-computing/aws-ec2.md) | インスタンスタイプ、AMI、EBS、Auto Scaling、キーペア | 45分 |
| 05 | [コンテナサービス](02-computing/aws-container-services.md) | ECS、Fargate、EKS（IRSA/VPC CNI/Add-ons）、ECR、OSSデプロイ戦略 | 70分 |
| 06 | [サーバーレス](02-computing/aws-serverless.md) | Lambda、API Gateway、SQS、SNS、EventBridge、Kinesis、Step Functions | 55分 |

### データ・ストレージ

| # | ドキュメント | 内容 | 所要時間 |
|---|-------------|------|---------|
| 07 | [RDS・データベース](03-data-storage/aws-rds-database.md) | RDS、Aurora、DynamoDB、ElastiCache、RDS Proxy、料金体系 | 60分 |
| 08 | [S3・ストレージ](03-data-storage/aws-s3-storage.md) | S3、バケットポリシー、ライフサイクル、暗号化、Object Lock、Athena、Glue、EBS、EFS | 50分 |

### ネットワーク・配信

| # | ドキュメント | 内容 | 所要時間 |
|---|-------------|------|---------|
| 09 | [ロードバランシング](04-networking/aws-load-balancing.md) | ALB、NLB、Target Group、ヘルスチェック、SSL終端 | 35分 |
| 10 | [Route 53・CloudFront](04-networking/aws-route53-cloudfront.md) | DNS、ルーティングポリシー、CDN、ACM、SSL証明書 | 40分 |

### セキュリティ・監視

| # | ドキュメント | 内容 | 所要時間 |
|---|-------------|------|---------|
| 11 | [セキュリティサービス](05-security-monitoring/aws-security-services.md) | KMS、Secrets Manager、WAF、Shield、GuardDuty | 45分 |
| 12 | [モニタリング](05-security-monitoring/aws-monitoring.md) | CloudWatch、CloudTrail、Kinesis Firehose、X-Ray、AWS Config | 45分 |

### 運用・設計

| # | ドキュメント | 内容 | 所要時間 |
|---|-------------|------|---------|
| 13 | [Infrastructure as Code](06-operations/aws-iac.md) | CloudFormation、CDK、SAM、テンプレート構文 | 45分 |
| 14 | [Well-Architected Framework](06-operations/aws-well-architected.md) | 6つの柱、設計原則、レビュープロセス、本番コスト試算 | 45分 |
| 15 | [Well-Architected: 大量データの設計パターン](06-operations/aws-well-architected-large-scale.md) | データ分類、パフォーマンス、信頼性、コスト最適化、Phase別コスト試算 | 70分 |


### コラム

| # | ドキュメント | 内容 | 所要時間 |
|---|-------------|------|---------|
| C1 | [監査ログ、全部DBに入れとけばよくない？](aws-audit-log-design.md) | 記録・検索・保存・コストの4つの矛盾と3層設計 | 15分 |

---

## 学習パス

### 初心者向け（まずはここから）

IDサービスを理解するための最低限の知識を身につけるパスです。

```
01 AWS基礎 → 02 IAM → 03 VPC → 07 データベース → 09 ロードバランシング
```

### 運用者向け（本番環境を管理する人へ）

本番環境の構築・運用に必要な知識を重点的に学びます。

```
03 VPC → 05 コンテナ → 07 データベース → 09 LB → 12 モニタリング → 13 IaC
```

### セキュリティ重視（認証基盤に必須）

IDサービスにとって特に重要なセキュリティ関連の知識を深めます。

```
02 IAM → 03 VPC → 11 セキュリティ → 10 Route53/CF → 14 Well-Architected
```

### 大規模運用（スケーラビリティ重視）

大量データ・大量トラフィックに対応するための知識を学びます。

```
07 データベース → 08 S3/ストレージ → 06 サーバーレス → 12 モニタリング → 15 大量データの設計パターン
```

---

## 関連する学習コンテンツ

AWSをより深く理解するために、以下のセクションも合わせて学習することを推奨します。

| セクション | 関連内容 |
|-----------|---------|
| [ネットワーキング](../15-networking/README.md) | TCP/IP、DNS、TLS等のプロトコル基礎 |
| [Linux](../14-linux/README.md) | EC2インスタンスのOS操作、パフォーマンス監視 |
| [Kubernetes](../13-kubernetes/README.md) | EKSで利用するK8sの基礎知識 |
| [パフォーマンスチューニング](../26-performance-tuning/README.md) | AWS環境でのパフォーマンス最適化 |
| [PostgreSQL](../11-postgresql/README.md) | RDS/Auroraで利用するDB知識 |
| [セキュリティ](../06-security/README.md) | 暗号化、認証・認可の基礎概念 |

---

## 学習チェックリスト

### 基礎レベル

- [ ] AWSのリージョンとAZの違いを説明できる
- [ ] IAMユーザー、グループ、ロールの違いを説明できる
- [ ] VPCの基本構成（パブリック/プライベートサブネット）を理解している
- [ ] セキュリティグループとNACLの違いを説明できる
- [ ] S3のバケットポリシーを設定できる

### 中級レベル

- [ ] マルチAZ構成のメリットとデザインパターンを理解している
- [ ] IAMポリシーの評価ロジック（明示的Deny優先）を理解している
- [ ] ALBのターゲットグループとヘルスチェックを設定できる
- [ ] RDS/Auroraの高可用性構成を説明できる
- [ ] CloudWatchでアラームとダッシュボードを作成できる

### 上級レベル

- [ ] Well-Architected Frameworkの6つの柱を説明できる
- [ ] CloudFormation/CDKでインフラをコード管理できる
- [ ] セキュリティインシデント発生時の対応フローを理解している
- [ ] コスト最適化のための施策を提案できる
- [ ] IDサービスに適したAWSアーキテクチャを設計できる
