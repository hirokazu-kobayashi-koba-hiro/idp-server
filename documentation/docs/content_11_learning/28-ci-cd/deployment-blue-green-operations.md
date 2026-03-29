# Blue-Green デプロイ: 運用ガイド

Blue/Green デプロイは手順通りに実行すれば動きます。しかし「動く」ことと「安全に運用できる」ことは別です。

このドキュメントでは、[実践ガイド](deployment-blue-green-practice)の手順を本番で回すときに**事故を防ぐためのガードレール**をまとめます。まず全体を通して守るべきルール、次に各ステップの具体的な注意点、最後に IaC との分担を整理します。

---

## 見落としやすい5つの落とし穴

Blue/Green デプロイは「2つの環境を切り替えるだけ」に見えますが、実際には複数のサービスが異なるタイミングで切り替わるため、**各サービスの状態の組み合わせ**を正しく把握していないと事故になります。

### 1. Green の作業が Blue に影響する

```
┌─────────────────────────────────────────────────────────────┐
│  Green を準備・テストしている間も、Blue は本番トラフィックを  │
│  処理し続けている。Green の作業が Blue に影響してはいけない。 │
│                                                             │
│  ✅ OK:                                                     │
│    ・Green Aurora にだけマイグレーション適用                  │
│    ・Green ECS に新イメージをデプロイ                        │
│    ・テスト用ドメインで Green に直接アクセス                  │
│                                                             │
│  ❌ NG:                                                     │
│    ・Blue Aurora にスキーマ変更を適用                        │
│    ・共有の ElastiCache のデータ構造を変更                   │
│    ・Blue ECS の設定を変更                                   │
└─────────────────────────────────────────────────────────────┘
```

### 2. v2 アプリが v1 スキーマの DB で動く期間がある

```
ALB 切り替え後、Aurora Switchover 前:

  Green ECS (v2) ──→ Blue Aurora (v1 スキーマ)
                      ↑ まだ Blue の DB を参照している！

  → v2 アプリが v1 スキーマの DB で動く期間がある
  → v2 アプリは v1 スキーマでも動作する必要がある
  → 新カラム・新テーブルがないと動かない機能は
    この期間中は無効化しておく（Feature Flag 等）
```

### 3. Redis のデータ構造を壊すと Blue も Green も壊れる

```
準備中:     Blue ECS → Redis ← Green ECS（テスト中）
切り替え後: Green ECS → Redis
ロールバック: Blue ECS → Redis

→ 全ステップで同じ Redis を使う
→ セッション形式の互換性は最初から最後まで必要
→ Redis のデータ構造を壊す変更は Blue/Green では対応できない
  （別リリースで事前に互換性を確保してから）
```

### 4. クリーンアップしたらロールバックできない

```
「切り替えたら終わり」ではない。
各ステップでロールバック可能な状態を維持する。

  Green デプロイ中:  → Green を削除するだけ
  ALB 切り替え後:    → ALB を Blue に戻すだけ（瞬時、DB 影響なし）
  Aurora Switchover 後: → ALB を Blue に戻す + DB 復旧（手動、要注意）
  クリーンアップ後:  → ロールバック不可（旧 Blue 削除済み）

  → クリーンアップは十分な安定確認の後で（24時間〜1週間）
```

### 5. Aurora を先に切り替えるとロールバックが複雑になる

```
正しい順序:
  ① Green デプロイ → ② Green テスト → ③ ALB 切り替え
  → ④ 監視 → ⑤ Aurora Switchover → ⑥ 監視 → ⑦ クリーンアップ

やってはいけない:
  ❌ Aurora Switchover を先にやる（ALB がまだ Blue なのに DB だけ Green に）
  ❌ ALB と Aurora を同時に切り替える（ロールバックが複雑になる）
  ❌ 監視を飛ばしてクリーンアップ（ロールバック手段を失う）
```

---

## 個別の注意点

以降は各ステップで特に注意すべき具体的なポイントです。

---

## ロールバックとデータの扱い

ロールバックの影響は、**どの段階で問題が発覚したか**によって大きく異なります。[実践ガイド](deployment-blue-green-practice#推奨フロー)では ALB と Aurora の切り替えを分けて実行するため、それぞれの段階でのロールバックを理解しておく必要があります。

### 段階別のロールバック影響

```
リリースのタイムライン:

  t=0    ALB 切り替え（Blue → Green）
         DB はまだ Blue のまま
         ├── Green ECS (v2) → Blue Aurora ← まだ Blue DB を参照
         └── Blue ECS (v1) → Blue Aurora
                          ↑
  t=15min  ALB 切り替え後の監視完了
                          ↓
  t=15min  Aurora Switchover 実行
         ├── Green ECS (v2) → Green Aurora ← DB も Green に
         └── Blue ECS (v1) → Blue Aurora（旧、スタンドアロン）
                          ↑
  t=30min  Aurora Switchover 後の監視完了
                          ↓
  t=30min  リリース完了
```

| 問題発覚のタイミング | ロールバック方法 | データへの影響 | 難易度 |
|:---|:---|:---|:---:|
| **ALB 切り替え後（t=0〜15min）** | ALB の Weight を Blue に戻す | **なし**（DB は Blue のまま） | 簡単 |
| **Aurora Switchover 後（t=15〜30min）** | ALB を Blue に戻す + DB 復旧 | **Green 期間中のデータが消える可能性** | 難しい |
| **リリース完了後（t=30min〜）** | 新規リリースで修正、または上記 | 同上 | 状況次第 |

### ALB 切り替え後のロールバック（簡単・安全）

```
ALB を Blue に戻すだけ:

  Before:  ALB → Green ECS → Blue Aurora
  After:   ALB → Blue ECS  → Blue Aurora

  ・CLI 1回で瞬時に完了
  ・DB は Blue のまま触っていない → データ影響ゼロ
  ・Green ECS が書き込んだデータも Blue Aurora に入っている
  → 最も安全なロールバック
```

**だから ALB と Aurora の切り替えを分けることが重要。**

### Aurora Switchover 後のロールバック（要注意）

```
Aurora Switchover 後にロールバックすると:

  t=15min  Aurora Switchover（Blue → Green）
  t=20min  Green Aurora にデータが書き込まれる
           ・ユーザー登録 3件
           ・トークン発行 500件
  t=25min  問題発覚 → ALB を Blue に戻す
           → しかし Blue Aurora は Switchover 前の状態
           → t=15〜25min に Green Aurora に書き込まれたデータは Blue にない

影響:
  ・ユーザー登録 → 消える（再登録が必要）
  ・トークン → 無効（再ログインが必要）
  ・セッション → ElastiCache 共有なので消えない ✅
```

### 対策

| 対策 | 説明 |
|:---|:---|
| **ALB → 監視 → Aurora の段階的切り替え** | ALB 段階でのロールバックならデータ影響ゼロ（推奨） |
| **Aurora Switchover 前にスナップショット** | 最悪の場合の復元ポイントを確保 |
| **監視を厳密に** | Aurora Switchover 後の問題を早期検出してデータ損失を最小化 |
| **低トラフィック時間帯に実行** | データ損失量を最小化 |

**認証サービスの場合**: ロールバックで失われるのは主にトークンとセッション。ユーザーは再ログインすれば復旧するため、影響は限定的。ユーザー登録データの消失が最もクリティカルだが、登録頻度は低い。

---

## Primary Key の確認

Aurora の論理レプリケーションは **Primary Key がないテーブルの UPDATE/DELETE を複製できない**。

```sql
-- PK がないテーブルを検出
SELECT table_name
FROM information_schema.tables t
WHERE t.table_schema = 'public'
  AND NOT EXISTS (
    SELECT 1 FROM information_schema.table_constraints tc
    WHERE tc.table_name = t.table_name
      AND tc.constraint_type = 'PRIMARY KEY'
  );
```

idp-server は全テーブルに PK あり（`(id, created_at)` 複合含む）。パーティションテーブル（`security_event` 等）も対応済み。

---

## ログの識別

Blue と Green のログが混在するため、問題調査で区別できる必要がある。

```
対策:
  ① ECS Service 名で区別
     Blue:  /ecs/idp-server-blue
     Green: /ecs/idp-server-green
     → ロググループを分けることで自然に分離

  ② 環境変数でバージョン情報を注入
     APP_VERSION=v2.0.0, DEPLOYMENT_COLOR=green
     → ログの JSON に含める

  ③ CloudWatch Logs Insights でフィルタ
     fields @timestamp, @message
     | filter @logStream like /green/
     | sort @timestamp desc
     | limit 100
```

---

## 切り替えのタイミング

Aurora switchover で**数秒〜1分の書き込み停止**が発生するため、タイミングの選定が重要。

```
推奨: 低トラフィック時間帯

  日本テナント中心:  深夜 2:00〜5:00 JST
  グローバルテナント: 日曜深夜

切り替え中のユーザー影響:
  ・書き込み停止中（数秒〜1分）:
    - ログインリクエスト → 一時的にエラー or リトライで成功
    - トークン発行 → 一時的にエラー
    - 読み取り専用操作 → 影響なし（Reader は継続）

テナントへの通知:
  ・計画メンテナンスとして事前通知（1週間前）
  ・ステータスページに掲示
  ・切り替え完了後に通知
```

---

## 自動ロールバック

CloudWatch Alarm + Lambda で自動ロールバックを実現。

```
┌─ 監視 → 判定 → 自動ロールバック ───────────────────────┐
│                                                         │
│  CloudWatch Alarm                                       │
│  ┌──────────────────────────────────────────────┐     │
│  │ ALB 5xx エラー率 > 1%（5分間）→ ALARM       │     │
│  │ ECS ヘルスチェック失敗 > 2台 → ALARM         │     │
│  │ Aurora 接続エラー > 0 → ALARM                │     │
│  └──────────────────────┬───────────────────────┘     │
│                          ▼                              │
│  SNS Topic → Lambda (自動ロールバック)                  │
│  ┌──────────────────────────────────────────────┐     │
│  │ 1. ALB ルールを Blue に戻す                   │     │
│  │ 2. Slack / PagerDuty に通知                   │     │
│  │ 3. ロールバック理由をログに記録               │     │
│  └──────────────────────────────────────────────┘     │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### 判断基準

| メトリクス | 閾値 | 期間 | 自動ロールバック |
|:---|:---|:---:|:---:|
| ALB 5xx エラー率 | > 1% | 5分 | ✅ |
| ALB 5xx エラー数 | > 100件 | 1分 | ✅ |
| ECS Unhealthy Task 数 | > 2 | 3分 | ✅ |
| Aurora 接続エラー | > 0 | 1分 | ✅ |
| p95 レイテンシ | > 前バージョンの3倍 | 5分 | ⚠️ 手動判断 |
| エラーログの急増 | > 通常の10倍 | 3分 | ⚠️ 手動判断 |

### 注意

- **ALB のロールバックのみ自動化**（API 1回で瞬時）
- Aurora の switchover は自動で戻せない
- Aurora switchover 前なら、ALB を Blue に戻すだけで完了（DB 影響なし）
- Aurora switchover 後は、手動での DB 復旧が必要

---

## IaC（Terraform）と手作業の分担

Blue/Green デプロイでは「インフラの構築」と「リリース時の切り替え操作」が混在します。全てを Terraform で管理しようとすると、Aurora Blue/Green が Terraform 未対応なために破綻します。逆に全て手作業にすると、インフラの変更履歴が残らない。

**「何を Terraform で管理し、何を CI/CD で自動化し、何を手作業にするか」を明確にしておかないと、オペミスや state の乖離が起きます。**

### リソース別の管理方法

| レイヤー | リソース | Terraform | CI/CD | 手作業 | 備考 |
|:---|:---|:---:|:---:|:---:|:---|
| ネットワーク | VPC / Subnet / SG | ✅ | | | |
| DNS / CDN | Route 53 / CloudFront | ✅ | | | |
| LB | ALB / Listener / Target Group | ✅ | | | |
| LB | Target Group の重み変更 | | ✅ | | リリース時に CLI |
| アプリ | ECS Cluster / Service | ✅ | | | |
| アプリ | Task Definition / Image | | ✅ | | CI/CD で自動 |
| **DB** | **Aurora Cluster (Blue)** | **✅** | | | |
| **DB** | **Aurora Blue/Green 作成** | | | **✅** | **Terraform 未対応** |
| **DB** | **Green にマイグレーション** | | | **✅** | |
| **DB** | **Aurora Switchover** | | | **✅** | **Terraform 未対応** |
| **DB** | **Aurora Green 削除** | | | **✅** | |
| キャッシュ | ElastiCache | ✅ | | | |
| 監視 | CloudWatch / SNS / Lambda | ✅ | | | |

### Aurora Blue/Green が Terraform 未対応な理由

```
Aurora は2つの Terraform リソースで構成:
  aws_rds_cluster          ← クラスタ本体
  aws_rds_cluster_instance ← コンピュートインスタンス

RDS Blue/Green の Terraform サポート:
  aws_db_instance → ✅ 対応（非 Aurora の RDS のみ）
  aws_rds_cluster → ❌ 未対応

→ Aurora Blue/Green は AWS CLI / コンソールで実行
```

### 推奨する分担

```
┌─ Terraform ──────────────────────────────────────┐
│ 「インフラの土台」を IaC で管理                   │
│ VPC / ALB / ECS / Aurora(Blue) / ElastiCache      │
│ CloudWatch / SNS / Lambda                         │
└───────────────────────────────────────────────────┘

┌─ CI/CD パイプライン ─────────────────────────────┐
│ 「リリースのたびに実行する操作」を自動化          │
│ Image Build/Push → Task Definition → ECS更新     │
│ → ALB 重み変更 → 監視 → 自動ロールバック          │
│ → スキーマ変更がなければこれだけで完結             │
└───────────────────────────────────────────────────┘

┌─ 手作業（CLI / コンソール）──────────────────────┐
│ 「DBスキーマ変更を伴うリリース」のみ              │
│ Aurora Blue/Green 作成 → マイグレーション         │
│ → Switchover → クリーンアップ                     │
│ → 実行頻度: メジャーリリース時のみ（月1回程度）   │
└───────────────────────────────────────────────────┘
```

### Switchover 後の Terraform state

```
Switchover 後、Aurora のエンドポイントが入れ替わる
→ Terraform の state が実態と乖離する

対応:
  ① terraform import で新クラスタを取り込む
  ② terraform state rm で旧クラスタの参照を削除
  ③ terraform plan で差分なしを確認

  または:
  ① terraform apply -refresh-only を実行
  ② state を実態に合わせる
```

### ECS の切り替え方式

| 方式 | Terraform 対応 | 特徴 |
|:---|:---:|:---|
| **ALB 重み手動変更** | ✅ | 最もシンプル |
| **CodeDeploy** | ✅ | 自動化。`lifecycle.ignore_changes` が必要 |
| **ECS-Native Blue/Green** | ⚠️ | CodeDeploy 不要。Terraform 対応は限定的 |

CodeDeploy を使う場合:

```hcl
resource "aws_ecs_service" "green" {
  ...
  lifecycle {
    ignore_changes = [task_definition, load_balancer]
  }
}

resource "aws_lb_listener_rule" "main" {
  ...
  lifecycle {
    ignore_changes = [action]
  }
}
```

---

## まとめ

```
Blue/Green デプロイで事故を起こさないために:

  ① 落とし穴を知る
     ・Green の作業が Blue に影響しないか？
     ・v2 アプリは v1 スキーマで動くか？
     ・Redis のデータ構造は互換か？
     ・クリーンアップのタイミングは適切か？
     ・切り替えの順序は正しいか？

  ② 段階的に切り替える
     ・ALB → 監視 → Aurora switchover → 監視
     ・ALB 段階ならロールバックは瞬時・データ影響ゼロ
     ・Aurora switchover 後のロールバックは手動・データ損失リスクあり

  ③ 自動ロールバックで守る
     ・CloudWatch Alarm で 5xx / ヘルスチェック / 接続エラーを監視
     ・ALB のロールバックだけ自動化（Aurora は手動）

  ④ IaC と手作業を分ける
     ・インフラの土台: Terraform
     ・リリース操作: CI/CD パイプライン
     ・DB スキーマ変更: 手作業（Aurora Blue/Green が Terraform 未対応）
```

---

## 関連ドキュメント

- [Blue-Green デプロイ実践](deployment-blue-green-practice): リリース手順
- [AWS サービス別ガイド](deployment-blue-green-aws-services): 各サービスの振る舞い
- [デプロイ戦略](deployment-strategy): Blue-Green / Canary / ローリングの概念
