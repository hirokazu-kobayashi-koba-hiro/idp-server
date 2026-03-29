# AWS での DR 構成

ここまで学んだ DR の概念（[RPO/RTO](rpo-rto-strategies)、[マルチリージョン](multi-region)、[バックアップ](backup-restore)、[フェイルオーバー](failover-procedures)）を、AWS のサービスを使って具体的にどう構築するかを学びます。

[大量データアーキテクチャ](../aws/operations/aws-well-architected-large-scale)の統合アーキテクチャを前提に、DR リージョンの構成を設計します。

---

## Primary と DR の構成図

```
┌─ Primary Region (ap-northeast-1) ────────────────────────────────┐
│                                                                    │
│  Route 53 (api.example.com)                                       │
│    ├── Primary: ap-northeast-1 (重み 100)                         │
│    └── DR:      us-west-2      (重み 0)                           │
│                                                                    │
│  ┌─ CloudFront ─────────────────────────────────────────────────┐│
│  │  Origin: ALB (Primary)                                        ││
│  └───────────────────────────────────────────────────────────────┘│
│                                                                    │
│  ┌─ ALB ─────────────────────────────────────────────────────────┐│
│  │  Target Group → ECS Fargate (4 tasks)                         ││
│  └───────────────────────────────────────────────────────────────┘│
│                                                                    │
│  ┌─ ECS Fargate ─────────────────────────────────────────────────┐│
│  │  idp-server × 4                                               ││
│  └───────────────────────────────────────────────────────────────┘│
│                                                                    │
│  ┌─ Aurora Global Database ──────────────────────────────────────┐│
│  │  Primary Cluster                                              ││
│  │  ├── Writer (db.r6g.xlarge)                                   ││
│  │  └── Reader (db.r6g.xlarge)                                   ││
│  └───────────────────────┬───────────────────────────────────────┘│
│                           │ ストレージレベルレプリケーション        │
│  ┌─ ElastiCache ─────────┼───────────────────────────────────────┐│
│  │  Redis (session/cache) │                                      ││
│  └────────────────────────│──────────────────────────────────────┘│
│                           │                                        │
└───────────────────────────│────────────────────────────────────────┘
                            │
                            │ < 1秒のラグ（通常）
                            │
┌─ DR Region (us-west-2) ──│────────────────────────────────────────┐
│                           │                                        │
│  ┌─ ALB ─────────────────│───────────────────────────────────────┐│
│  │  Target Group → ECS Fargate (0〜1 tasks)                      ││
│  └───────────────────────│───────────────────────────────────────┘│
│                           │                                        │
│  ┌─ ECS Fargate ─────────│───────────────────────────────────────┐│
│  │  idp-server × 0 (Pilot Light) or × 1 (Warm Standby)          ││
│  └───────────────────────│───────────────────────────────────────┘│
│                           │                                        │
│  ┌─ Aurora Global Database│──────────────────────────────────────┐│
│  │  Secondary Cluster    ▼                                       ││
│  │  ├── Reader (db.r6g.xlarge) ← 昇格可能                       ││
│  │  └── (Writer なし。昇格するまで読み取り専用)                   ││
│  └───────────────────────────────────────────────────────────────┘│
│                                                                    │
│  ┌─ ElastiCache ─────────────────────────────────────────────────┐│
│  │  Redis (空 or Global Datastore)                               ││
│  └───────────────────────────────────────────────────────────────┘│
│                                                                    │
│  ┌─ S3 (クロスリージョンレプリケーション) ────────────────────────┐│
│  │  バックアップ、監査ログのコピー                               ││
│  └───────────────────────────────────────────────────────────────┘│
│                                                                    │
└────────────────────────────────────────────────────────────────────┘
```

---

## AWS サービス別の DR 設計

### Route 53（DNS）

```
フェイルオーバールーティング:

  api.example.com
  ├── Primary: ALB (ap-northeast-1)
  │   ヘルスチェック: /actuator/health → 3回連続失敗で Unhealthy
  └── Secondary: ALB (us-west-2)
      ヘルスチェック: /actuator/health

  Primary が Unhealthy → 自動で Secondary に切り替わる

設定のポイント:
  ・TTL: 60秒（短く。DR 切り替え後の伝搬を速く）
  ・ヘルスチェック間隔: 10秒
  ・失敗閾値: 3回
  → 最短30秒で切り替え検知
```

### Aurora Global Database

```
構成:
  Primary Cluster (ap-northeast-1)
    ├── Writer
    └── Reader
         │
         │ ストレージレベルレプリケーション
         │ ラグ: 通常 < 1秒
         ▼
  Secondary Cluster (us-west-2)
    └── Reader（昇格可能）

特徴:
  ・ストレージレベルでレプリケーション（論理レプリケーションより高速）
  ・通常のラグ: 1秒未満
  ・RPO: 通常 < 1秒（リージョン障害時はラグ分のデータ損失）
  ・昇格時間: 1〜2分

フェイルオーバー手順:
  ① Managed Planned Failover（計画的）
     ・Primary と Secondary を入れ替え
     ・ダウンタイム: 数秒〜1分
     ・データ損失: なし（同期を待ってから切り替え）

  ② Unplanned Failover（障害時）
     ・Secondary を独立した Primary に昇格
     ・旧 Primary との接続を切断
     ・ダウンタイム: 1〜2分
     ・データ損失: レプリカラグ分（通常 < 1秒）
```

### ECS Fargate

```
Pilot Light:
  DR リージョンにタスク定義とサービスを作成済み
  desired-count = 0（タスクは起動していない）
  → フェイルオーバー時に desired-count を増やすだけ

Warm Standby:
  DR リージョンに最小構成で稼働（desired-count = 1）
  → フェイルオーバー時にスケールアウト（1 → 4）

共通の注意点:
  ・コンテナイメージは両リージョンの ECR に Push しておく
  ・環境変数（DB エンドポイント等）は DR リージョン用に設定
  ・シークレット（Secrets Manager）も DR リージョンにレプリケーション
```

### ElastiCache

```
選択肢:

  ① フェイルオーバー時にコールドスタート（推奨）
     DR リージョンの Redis は空の状態で起動
     → セッションは消える → ユーザーは再ログイン
     → キャッシュは自然にウォームアップ
     → 最もシンプル、コスト低

  ② Global Datastore（リアルタイム同期）
     Primary → Secondary にリアルタイムレプリケーション
     → セッションも DR リージョンに同期
     → コスト増、ラグあり（数百ミリ秒）

  認証サービスの場合:
     → ① が推奨。DR はめったに起きない。再ログインで復旧可能。
```

### S3（バックアップ / 監査ログ）

```
クロスリージョンレプリケーション (CRR):
  Primary S3 (ap-northeast-1) ──自動コピー──→ DR S3 (us-west-2)

対象:
  ・DB スナップショット（Aurora のクロスリージョンコピーで自動）
  ・監査ログ（S3 CRR で自動）
  ・Terraform state（S3 + DynamoDB lock）

設定:
  ・レプリケーションルール: 全オブジェクトを DR リージョンにコピー
  ・ストレージクラス: DR 側は S3 Standard-IA（コスト最適化）
```

### Secrets Manager

```
シークレットのマルチリージョン:
  DB パスワード、API キー、暗号化キー等
  → Secrets Manager のレプリカを DR リージョンに作成
  → 自動同期（Primary で変更 → DR にも反映）

注意:
  DR リージョンのアプリが参照するシークレットが
  Primary と同じ値であることを確認
```

---

## DR 戦略別のコスト

### Pilot Light

```
Primary Region:
  ECS (4 tasks):           $200/月
  Aurora (Writer + Reader): $400/月
  ElastiCache:              $100/月
  ALB:                      $50/月
  合計:                     $750/月

DR Region（追加分）:
  ECS (0 tasks):            $0/月     ← 停止中
  Aurora Secondary:         $200/月   ← Reader 1台
  ElastiCache:              $0/月     ← なし or 最小
  ALB:                      $50/月    ← ヘルスチェック用
  S3 CRR:                   $10/月
  合計:                     $260/月

→ DR 追加コスト: 約35%増
```

### Warm Standby

```
DR Region（追加分）:
  ECS (1 task):             $50/月    ← 最小構成で稼働
  Aurora Secondary:         $200/月
  ElastiCache (最小):       $50/月
  ALB:                      $50/月
  S3 CRR:                   $10/月
  合計:                     $360/月

→ DR 追加コスト: 約48%増
```

### Active-Active

```
DR Region（追加分）:
  ECS (4 tasks):            $200/月   ← フル稼働
  Aurora Secondary:         $400/月   ← Writer + Reader
  ElastiCache:              $100/月
  ALB:                      $50/月
  S3 CRR:                   $10/月
  合計:                     $760/月

→ DR 追加コスト: 約100%増（2倍）
```

---

## フェイルオーバー手順（AWS 固有）

### 自動フェイルオーバー（Route 53 ヘルスチェック）

```
Route 53 が自動で実行:
  1. Primary のヘルスチェック失敗を検知（30秒）
  2. DNS を Secondary に切り替え
  3. ユーザーのリクエストが DR リージョンに向く

ただし:
  ・DB の昇格は自動ではない → 手動 or Lambda で自動化
  ・アプリの起動（Pilot Light）も手動 → 事前に Lambda / Step Functions で自動化
```

### Aurora Global Database のフェイルオーバー

```
Unplanned Failover（障害時）:

  ① Secondary Cluster を独立した Primary に昇格
     aws rds failover-global-cluster \
       --global-cluster-identifier idp-global \
       --target-db-cluster-identifier arn:aws:rds:us-west-2:...:cluster:idp-dr

  ② 旧 Primary との接続が自動切断

  ③ 昇格完了（1〜2分）

  ④ DR リージョンの Writer エンドポイントが有効に
```

### フェイルオーバー自動化

```
EventBridge + Step Functions で自動化:

  Route 53 ヘルスチェック失敗
    ↓
  EventBridge Rule
    ↓
  Step Functions:
    1. Aurora Global Database フェイルオーバー
    2. ECS Service の desired-count を増加（Pilot Light の場合）
    3. 動作確認（ヘルスチェック URL にリクエスト）
    4. Slack / PagerDuty に通知
    5. 失敗時: ロールバック + エスカレーション
```

---

## IaC（Terraform）での DR 管理

```
DR リージョンのインフラも Terraform で管理:

  terraform/
  ├── modules/
  │   ├── networking/     ← VPC, Subnet, SG
  │   ├── compute/        ← ECS, ALB
  │   ├── database/       ← Aurora
  │   └── monitoring/     ← CloudWatch, SNS
  │
  ├── environments/
  │   ├── primary/        ← ap-northeast-1
  │   │   └── main.tf     ← module 呼び出し（フル構成）
  │   └── dr/             ← us-west-2
  │       └── main.tf     ← module 呼び出し（最小構成）
  │
  └── global/
      └── route53.tf      ← DNS（リージョンまたぎ）

ポイント:
  ・同じ module を使い、パラメータで Primary / DR を切り替え
  ・DR は desired_count = 0 や instance_count = 1 で最小化
  ・Aurora Global Database の Secondary は Terraform で管理可能
```

---

## まとめ

```
AWS での DR 設計:

  1. Aurora Global Database がデータ復旧の核
     ・ストレージレベルレプリケーション（< 1秒ラグ）
     ・Unplanned Failover で 1〜2分で昇格

  2. Route 53 ヘルスチェックで自動検知
     ・30秒で検知 → DNS 自動切替

  3. ECS は Pilot Light or Warm Standby
     ・Pilot Light: コスト最小、起動に数分
     ・Warm Standby: 最小稼働、スケールアウトで数分

  4. ElastiCache は再ログインで対応
     ・セッション同期のコストより、再ログインの方が安い

  5. Terraform で Primary / DR を統一管理
     ・同じ module、パラメータで切り替え

  6. 自動化は段階的に
     ・まず手動手順を整備 → Lambda / Step Functions で自動化
```

---

## 関連ドキュメント

- [RPO/RTO と DR 戦略](rpo-rto-strategies): コストとトレードオフの判断
- [フェイルオーバー手順](failover-procedures): 汎用的な切り替え手順
- [DR テスト（Game Day）](game-day): 定期的な検証
- [大量データアーキテクチャ](../aws/operations/aws-well-architected-large-scale): Primary リージョンの構成
- [Blue-Green デプロイ AWS サービス別ガイド](../ci-cd/deployment-blue-green-aws-services): デプロイ時の各サービスの振る舞い
