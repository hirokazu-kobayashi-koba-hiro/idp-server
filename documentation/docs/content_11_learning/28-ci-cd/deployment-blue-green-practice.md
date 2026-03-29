# 実践: Blue-Green デプロイ（IDサービス on AWS）

[デプロイ戦略](deployment-strategy) で学んだ Blue-Green デプロイを、[大量データアーキテクチャ](../aws/operations/aws-well-architected-large-scale) の構成で実践します。

---

## このドキュメントが想定するリリース

**新しいテーブルやカラムの追加を伴う機能リリース**を対象とします。

```
例: 身元確認（eKYC）機能の追加リリース

  新機能に必要な DB 変更:
    ・identity_verification テーブルの新規作成
    ・users テーブルに verification_status カラム追加

  新機能に必要なアプリ変更:
    ・eKYC API の追加
    ・ユーザー登録フローに身元確認ステップを追加
```

このようなリリースでは、**新しいスキーマがないとアプリ v2 が動かない**。しかし旧アプリ v1 はまだ本番で稼働中。

```
課題:
  DB にスキーマ変更を適用したい
  → でも v1 がまだ動いている
  → v1 が壊れないようにスキーマ変更しないといけない（後方互換性の制約）

Blue/Green で解決:
  Blue（v1 + 旧スキーマ）= 本番稼働中
  Green（v2 + 新スキーマ）= ステージング、テスト中
  → 本番に影響なく新スキーマでテストできる
  → 問題なければ切り替え、問題あれば Blue に戻すだけ
```

**スキーマ変更を伴わないリリース**（バグ修正、設定変更等）は、ローリングアップデートで十分です。Blue/Green はスキーマ変更の安全性が必要なときに使います。

| リリース内容 | デプロイ方式 | DB 操作 |
|:---|:---|:---|
| バグ修正、設定変更 | ローリングアップデート | なし |
| 新カラム追加（後方互換） | ローリングアップデート | マイグレーション先行適用 |
| **新テーブル追加、カラム型変更等** | **Blue/Green** | **Green にだけマイグレーション** |
| Aurora メジャーバージョンアップ | Blue/Green | Green でバージョンアップ |

認証サービスのダウンタイムは全テナント・全ユーザーに影響するため、**無停止での切り替えと瞬時のロールバック**が求められます。

各 AWS サービスの Blue/Green での振る舞いは [AWS サービス別ガイド](deployment-blue-green-aws-services) を、運用の注意点・IaC の分担は [運用ガイド](deployment-blue-green-operations) を参照してください。

---

## 対象アーキテクチャ

アプリケーション（ECS）もデータベース（Aurora）も Blue/Green にします。

```
┌─────────┐
│クライアント│
└────┬────┘
     │
┌────▼────┐
│CloudFront│
└────┬────┘
     │
┌────▼────┐
│   ALB   │ ← ★ アプリの切り替え
└────┬────┘
     │
     ├──→ Blue  ECS (v1) ──→ Blue  Aurora ← ★ DB も Blue/Green
     └──→ Green ECS (v2) ──→ Green Aurora
                                │
                            論理レプリケーション
                            (Blue → Green 自動同期)
```

---

## 何を分けて、何を共有するか

```
┌──────────────────────────────────────────────────────────────┐
│                   Blue 環境                                   │
│                                                              │
│  ┌─ アプリ ──────────────────┐  ┌─ DB ─────────────────┐   │
│  │ ECS Service (v1)          │  │ Aurora Cluster (Blue) │   │
│  │ ├── Task Definition       │  │ ├── Writer            │   │
│  │ ├── Container Image       │  │ ├── Reader            │   │
│  │ └── Target Group (Blue)   │  │ └── 現在のスキーマ     │   │
│  └───────────────────────────┘  └───────────────────────┘   │
│                                                              │
├──────────────────────────────────────────────────────────────┤
│                   Green 環境                                  │
│                                                              │
│  ┌─ アプリ ──────────────────┐  ┌─ DB ─────────────────┐   │
│  │ ECS Service (v2)          │  │ Aurora Cluster (Green)│   │
│  │ ├── Task Definition       │  │ ├── Writer            │   │
│  │ ├── Container Image       │  │ ├── Reader            │   │
│  │ └── Target Group (Green)  │  │ └── 新スキーマ適用済み │   │
│  └───────────────────────────┘  └───────────────────────┘   │
│                                         ↑                    │
│                              Blue から論理レプリケーション     │
│                              (データは自動同期)               │
│                                                              │
├──────────────────────────────────────────────────────────────┤
│                   共有するもの                                │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ ALB (リスナールールで切り替え)                        │   │
│  │ ElastiCache (Redis) — セッション・キャッシュ          │   │
│  │ CloudFront / Route 53 / VPC / Security Group          │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

> 各サービスの詳細は [AWS サービス別ガイド](deployment-blue-green-aws-services) を参照

---

## IDサービス特有の考慮事項

### セッション互換性（ElastiCache は共有）

```
Blue (v1) → Redis ← Green (v2)
             ↑
       同じセッションデータにアクセス

セッション形式を変更するリリースの場合:
  リリース1: 新形式の「読み取り」を追加（旧形式も読める）
  リリース2: 新形式で「書き込み」開始
  → 2リリースに分けて安全に移行
```

### トークン互換性

```
JWT 署名鍵:
  Blue Aurora → JWKS テーブルに署名鍵
  Green Aurora → Blue からレプリケーションで同じ署名鍵をコピー
  → 切り替え後も同じ鍵で検証可能 ✅

Opaque トークン:
  Blue Aurora → token テーブルにトークンデータ
  Green Aurora → Blue からレプリケーションで同期
  → Introspection は切り替え後も正常動作 ✅
```

### RLS（Row Level Security）

Aurora PostgreSQL の RLS ポリシーは Blue/Green 両方で適用される。Green で RLS ポリシーを変更しても Blue には影響しない（別クラスタ）。テナント分離は維持される。

---

## リリース手順

### 推奨フロー

```
ALB 切り替え → 監視 → Aurora switchover → 監視
          ↑                           ↑
   問題あれば瞬時ロールバック    問題あれば手動復旧
   （DB影響なし）
```

ALB と Aurora の切り替えを分けることで、ロールバックのリスクを最小化。

### Step 1: 準備

```
□ コンテナイメージをビルド・ECR にプッシュ
□ Staging で E2E テスト実行
□ Aurora PostgreSQL の論理レプリケーション有効化を確認
  (rds.logical_replication = 1)
□ リリースチェックリスト確認
```

### Step 2: Aurora Blue/Green 環境の作成

```bash
aws rds create-blue-green-deployment \
  --blue-green-deployment-name idp-release-v2 \
  --source arn:aws:rds:ap-northeast-1:123456789012:cluster:idp-cluster \
  --target-db-cluster-parameter-group-name idp-cluster-pg-v2

# Green クラスタの作成完了を待機（数分〜十数分）
aws rds describe-blue-green-deployments \
  --blue-green-deployment-identifier idp-release-v2 \
  --query 'BlueGreenDeployments[0].Status'
# → "AVAILABLE" になるまで待つ
```

> Aurora Blue/Green の仕組み（Clone + 論理レプリ）の詳細は [AWS サービス別ガイド](deployment-blue-green-aws-services#aurorardsの詳細) を参照

### Step 3: Green Aurora にマイグレーション適用

**なぜマイグレーションが必要か**: 論理レプリケーションはデータ（INSERT/UPDATE/DELETE）だけを同期し、**スキーマ変更（CREATE TABLE / ALTER TABLE 等）は同期しない**。だから Green にだけ新しいスキーマを適用できる。

```
Blue Aurora                          Green Aurora
┌────────────────────────┐          ┌────────────────────────┐
│ users (id, name, email)│──レプリ──→│ users (id, name, email)│
│                        │  データ   │                        │
│ tokens (id, value)     │──のみ──→ │ tokens (id, value)     │
└────────────────────────┘  同期    └────────────────────────┘
                                              ↓ マイグレーション適用
                                    ┌────────────────────────┐
                                    │ users (id, name, email,│
                                    │        phone) ← 新カラム│
                                    │ tokens (id, value)     │
                                    │ new_feature ← 新テーブル│
                                    └────────────────────────┘

・Blue からの INSERT (id, name, email) は Green にも反映される ✅
・Green の新カラム phone は NULL のまま（Blue は phone を知らない）
・Green の新テーブル new_feature にはレプリデータなし（Blue に存在しない）
・Switchover 後、アプリ v2 が phone カラムと new_feature テーブルを使い始める
```

```bash
GREEN_ENDPOINT=$(aws rds describe-blue-green-deployments \
  --blue-green-deployment-identifier idp-release-v2 \
  --query 'BlueGreenDeployments[0].Target' --output text)

# Green にだけマイグレーション実行（Blue には影響しない）
flyway -url="jdbc:postgresql://${GREEN_ENDPOINT}:5432/idpserver" \
  -user=idp_admin_user \
  -password=$DB_ADMIN_PASSWORD \
  migrate
```

> スキーマ変更の制約（レプリケーション互換）の詳細は [AWS サービス別ガイド](deployment-blue-green-aws-services#スキーマ変更の制約論理レプリケーション互換) を参照

### Step 4: Green ECS にアプリ v2 をデプロイ

```bash
aws ecs register-task-definition \
  --family idp-server-green \
  --container-definitions '[{
    "name": "idp-server",
    "image": "123456789012.dkr.ecr.ap-northeast-1.amazonaws.com/idp-server:v2.0.0",
    "environment": [
      {"name": "DB_WRITER_URL", "value": "jdbc:postgresql://'${GREEN_ENDPOINT}':5432/idpserver"}
    ]
  }]'

aws ecs update-service \
  --cluster idp-cluster \
  --service idp-server-green \
  --task-definition idp-server-green:LATEST \
  --desired-count 4

aws ecs wait services-stable \
  --cluster idp-cluster \
  --services idp-server-green
```

### Step 5: Green 環境の動作確認

```bash
aws elbv2 describe-target-health \
  --target-group-arn $GREEN_TARGET_GROUP_ARN

curl -k https://test.api.example.com/actuator/health
curl -k https://test.api.example.com/{tenant-id}/.well-known/openid-configuration

BASE_URL=https://test.api.example.com npm test -- --testPathPattern="smoke"
```

### Step 6: ALB トラフィック切り替え

```bash
aws elbv2 modify-rule \
  --rule-arn $LISTENER_RULE_ARN \
  --actions '[{
    "Type": "forward",
    "ForwardConfig": {
      "TargetGroups": [
        {"TargetGroupArn": "'$BLUE_TARGET_GROUP_ARN'", "Weight": 0},
        {"TargetGroupArn": "'$GREEN_TARGET_GROUP_ARN'", "Weight": 100}
      ]
    }
  }]'
```

**15分間監視。問題あれば ALB を Blue に戻すだけ（瞬時、DB影響なし）。**

### Step 7: Aurora Switchover

ALB 切り替え後、問題がなければ Aurora を切り替え。

```bash
aws rds switchover-blue-green-deployment \
  --blue-green-deployment-identifier idp-release-v2 \
  --switchover-timeout 300

# ⚠️ 数秒〜1分の書き込み停止が発生
# エンドポイント名が自動入替（アプリ設定変更不要）
```

**さらに15分間監視。**

### Step 8: ロールバック（問題発生時）

```bash
# ALB のロールバック（瞬時）
aws elbv2 modify-rule \
  --rule-arn $LISTENER_RULE_ARN \
  --actions '[{
    "Type": "forward",
    "ForwardConfig": {
      "TargetGroups": [
        {"TargetGroupArn": "'$BLUE_TARGET_GROUP_ARN'", "Weight": 100},
        {"TargetGroupArn": "'$GREEN_TARGET_GROUP_ARN'", "Weight": 0}
      ]
    }
  }]'

# ⚠️ Aurora switchover 後は、手動での DB 復旧が必要
```

### Step 9: クリーンアップ

```bash
# 24時間〜1週間後、問題がないことを確認してから

# 1. Blue/Green デプロイメント定義を削除（レプリケーション設定等を解除）
#    --delete-target は Green 環境のリソースを削除するフラグ
#    Switchover 後は Green が旧 Blue のエンドポイント名を持っているため、
#    旧 Blue（Switchover 後はスタンドアロンクラスタ）は別途削除が必要
aws rds delete-blue-green-deployment \
  --blue-green-deployment-identifier idp-release-v2

# 2. 旧 Blue クラスタを削除（Switchover 後にスタンドアロンとして残っている）
aws rds delete-db-cluster \
  --db-cluster-identifier idp-cluster-old-blue \
  --skip-final-snapshot

# 3. 旧 Blue ECS Service をスケールダウン
aws ecs update-service \
  --cluster idp-cluster \
  --service idp-server-blue \
  --desired-count 0
```

---

## 段階的切り替え（Canary 併用）

ALB の重み付きルーティングで段階的に移行。

```bash
# 5% → 25% → 50% → 100% と段階的に
aws elbv2 modify-rule --rule-arn $RULE_ARN \
  --actions '[{"Type":"forward","ForwardConfig":{"TargetGroups":[
    {"TargetGroupArn":"'$BLUE_ARN'","Weight":95},
    {"TargetGroupArn":"'$GREEN_ARN'","Weight":5}
  ]}}]'
# → 15分監視 → 問題なければ次の段階へ
```

> 段階的切り替えの注意点（セッション振り分け等）は [AWS サービス別ガイド](deployment-blue-green-aws-services#albapplication-load-balancer) を参照

---

## リリースチェックリスト

### リリース前

```
□ Aurora の論理レプリケーション有効化 (rds.logical_replication = 1)
□ コンテナイメージが ECR にプッシュ済み
□ Staging で E2E テスト合格
□ セッション形式の互換性確認（ElastiCache 共有のため）
□ リリースノート作成
□ ロールバック手順の確認
□ Aurora switchover 前のスナップショット取得計画
```

### リリース後

```
□ Green Aurora のレプリカラグ = 0
□ Green ECS の全タスクが Ready
□ ALB ヘルスチェック全 healthy
□ 5xx エラー率 < 0.1%（15分間）
□ レイテンシ p95 が前バージョンと同等（±20%以内）
□ 認証フロー手動テスト OK
□ 主要テナントの動作確認
```

---

## Graceful Shutdown との連携

切り替え時と、クリーンアップ時の旧 Blue 停止で Graceful Shutdown が関わります。

### 切り替え時（推奨フロー順: ALB → Aurora）

```
t=0s     ALB トラフィック切り替え（Blue → Green）
t=0-30s  ALB Deregistration Delay（Blue への新規リクエスト停止）
         Blue ECS は処理中リクエストを完了させる
t=30s    Blue ECS への新規トラフィックなし
         （ただし Blue ECS はまだ起動中 = ロールバック可能）
  ...15分間監視...
t=15min  Aurora Switchover 開始（書き込み一時停止）
t=16min  Aurora Switchover 完了（エンドポイント入れ替え）
  ...15分間監視...
t=30min  リリース完了
```

### クリーンアップ時（旧 Blue ECS の停止）

```
t=0s    Blue ECS の desired-count を 0 に変更
t=0-30s Graceful Shutdown（処理中リクエスト完了を待つ）
t=30s   Blue ECS タスク停止

設定:
  idp-server:  server.shutdown=graceful, timeout-per-shutdown-phase=30s
  ALB:         Deregistration Delay=30s
  Aurora:      Switchover Timeout=300s
```

---

## コスト

```
通常時:   Blue Aurora + Blue ECS = $X/月
リリース中: + Green Aurora + Green ECS（数時間のみ）
          → 月1回リリースなら追加コストは月額の約3%程度
```

---

## 関連ドキュメント

- [AWS サービス別ガイド](deployment-blue-green-aws-services): 各サービスの Blue/Green での振る舞い、Aurora 詳細
- [運用ガイド](deployment-blue-green-operations): ロールバック、自動監視、IaC 分担
- [デプロイ戦略](deployment-strategy): Blue-Green / Canary / ローリングの概念
- [大量データアーキテクチャ](../aws/operations/aws-well-architected-large-scale): IDサービスの統合アーキテクチャ
- [Aurora Blue/Green Deployments (AWS Docs)](https://docs.aws.amazon.com/AmazonRDS/latest/AuroraUserGuide/blue-green-deployments-overview.html)
