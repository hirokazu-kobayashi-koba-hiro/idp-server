# Blue-Green デプロイ: AWS サービス別ガイド

[Blue-Green デプロイ実践](deployment-blue-green-practice) で使用する各 AWS サービスが、Blue/Green でどう振る舞うかを整理します。

---

## サービス一覧

| サービス | Blue/Green | 切り替え方法 | 切り替え時の停止 | 状態の扱い |
|:---|:---:|:---|:---:|:---|
| **ALB** | 共有 | リスナールールの重み変更 | なし | ステートレス |
| **ECS Fargate** | 分離 | ALB Target Group 切り替え | なし | ステートレス（アプリ） |
| **Aurora** | 分離 | Switchover（エンドポイント入替） | 数秒〜1分 | ステートフル（レプリケーション同期） |
| **ElastiCache** | 共有 | なし（両方が接続） | なし | ステートフル（セッション） |
| **CloudFront** | 共有 | なし（ALBがオリジン） | なし | キャッシュ |
| **Route 53** | 共有 | なし（ALBを指す） | なし | DNS |
| **S3** | 共有 | なし | なし | ステートフル（アーカイブ） |
| **CloudWatch** | 共有 | なし | なし | メトリクス・ログ |

### サービス構成図

```
┌─────────┐
│クライアント│
└────┬────┘
     │
┌────▼──────────┐
│ Route 53       │ 共有（DNS は変わらない）
└────┬──────────┘
     │
┌────▼──────────┐
│ CloudFront     │ 共有（ALB をオリジン参照）
└────┬──────────┘
     │
┌────▼──────────────────────────────────────────────────────────┐
│ ALB                                                  共有     │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ Listener Rule: Blue TG (重み X) / Green TG (重み Y)     │ │
│ │                 ↑ ここの重みを変えてトラフィック切り替え │ │
│ └─────────────────────────────────────────────────────────┘ │
└────┬──────────────────────────────┬───────────────────────────┘
     │                              │
     ▼                              ▼
┌─────────────────────┐  ┌─────────────────────┐
│ Blue ECS (v1)       │  │ Green ECS (v2)      │  分離
│ ├── Task ×4         │  │ ├── Task ×4         │
│ └── Target Group    │  │ └── Target Group    │
└────────┬────────────┘  └────────┬────────────┘
         │                        │
         ▼                        ▼
┌─────────────────────┐  ┌─────────────────────┐
│ Blue Aurora          │  │ Green Aurora         │  分離
│ ├── Writer           │  │ ├── Writer           │
│ └── Reader           │──│ └── Reader           │
│                      │  │                      │
│ (v1 スキーマ)        │  │ (v2 スキーマ)        │
└─────────────────────┘  └─────────────────────┘
         │  論理レプリ(自動)  ↑
         └───────────────────┘
         │
         ▼
┌──────────────────────────────────────────────────────────────┐
│ ElastiCache (Redis)                                  共有    │
│ ├── セッション                                               │
│ ├── トークンキャッシュ        ← Blue/Green 両方が接続        │
│ └── テナント設定キャッシュ                                    │
└──────────────────────────────────────────────────────────────┘
         │
         ▼
┌──────────────────────────────────────────────────────────────┐
│ S3                                                   共有    │
│ ├── 監査ログ（Parquet）                                      │
│ └── アーカイブ                 ← Blue/Green 両方が書き込み   │
└──────────────────────────────────────────────────────────────┘
         │
         ▼
┌──────────────────────────────────────────────────────────────┐
│ CloudWatch                                           共有    │
│ ├── Logs（/ecs/idp-server-blue, /ecs/idp-server-green）      │
│ ├── Metrics（ALB, ECS, Aurora）                              │
│ └── Alarms（5xx率, ヘルスチェック）→ SNS → Lambda(自動ロールバック)│
└──────────────────────────────────────────────────────────────┘
```

以降のセクションでは、各サービスの詳細を個別に解説します。

---

## ALB（Application Load Balancer）

```
役割: トラフィックの振り分け先を制御する「スイッチ」

通常時:
  Listener Rule → Blue Target Group (重み 100)
                  Green Target Group (重み 0)

切り替え時:
  Listener Rule → Blue Target Group (重み 0)
                  Green Target Group (重み 100)
  → API 1回で瞬時に切り替え、ダウンタイムなし
```

- ステートレスなので Blue/Green 間の整合性を気にする必要なし
- Deregistration Delay（デフォルト30秒）で処理中のリクエストを完了させてから切断
- **DNS は変わらない**: Route 53 → ALB のエンドポイントは固定。ALB 内部の Listener Rule の重みだけを変える

### 段階的切り替えの設定

```bash
# CLI で Target Group の Weight を指定
aws elbv2 modify-rule \
  --rule-arn arn:aws:elasticloadbalancing:ap-northeast-1:123456789012:listener-rule/app/idp-alb/xxx/yyy/zzz \
  --actions '[{
    "Type": "forward",
    "ForwardConfig": {
      "TargetGroups": [
        {"TargetGroupArn": "arn:aws:...targetgroup/blue-tg/xxx", "Weight": 95},
        {"TargetGroupArn": "arn:aws:...targetgroup/green-tg/yyy", "Weight": 5}
      ]
    }
  }]'
```

**コンソールの場合**: EC2 → ロードバランサー → ALB → リスナー → ルール編集 → ターゲットグループの重みを変更

### セッション振り分けの注意

```
同じユーザーが Blue と Green に交互に振られる可能性がある:

  リクエスト1 → Blue (v1)  → Redis にセッション保存
  リクエスト2 → Green (v2) → Redis からセッション読み取り

対策:
  ① ElastiCache 共有 + セッション形式の互換性確保（推奨）
     → v1/v2 どちらに振られてもセッションが読める
  ② ALB の Sticky Session（Target Group Stickiness）を有効化
     → 同じユーザーは同じ Target Group に固定される
     → ただし Canary の検証対象が偏る
```

### ルーティング条件（関係者だけ Green を使う）

ALB の Listener Rule は重み付けだけでなく、様々な条件でトラフィックを振り分けられる。**本番リリース前に関係者だけ Green を検証**するときに使う。

| 条件 | 説明 | 用途例 |
|:---|:---|:---|
| **Source IP** | 送信元IPアドレス（CIDR） | 社内IPだけ Green に |
| **Host Header** | リクエストのホスト名 | `test.api.example.com` → Green |
| **HTTP Header** | 任意のHTTPヘッダー値 | `X-Canary: true` → Green |
| **Path Pattern** | URLパス | `/v2/*` → Green |
| **Query String** | クエリパラメータ | `?canary=true` → Green |
| **重み付け** | Target Group の Weight | 全体の5%を Green に |

**パターン1: 社内IPで振り分け**

```
Rule 1 (優先度: 1):
  条件: Source IP = 203.0.113.0/24（社内IP）
  アクション: → Green Target Group

Rule 2 (優先度: 100, デフォルト):
  条件: なし
  アクション: → Blue Target Group

→ 社内からのアクセスだけ Green（v2）
→ 一般ユーザーは Blue（v1）のまま
```

**パターン2: テスト用ドメインで振り分け**

```
Rule 1 (優先度: 1):
  条件: Host Header = "test.api.example.com"
  アクション: → Green Target Group

Rule 2 (優先度: 100, デフォルト):
  条件: なし
  アクション: → Blue Target Group

→ test.api.example.com → Green（関係者のみ DNS 設定 or hosts ファイル）
→ api.example.com → Blue（一般ユーザー）
```

**パターン3: 特別なヘッダーで振り分け**

```
Rule 1 (優先度: 1):
  条件: HTTP Header "X-Green-Access" = "secret-key"
  アクション: → Green Target Group

→ curl -H "X-Green-Access: secret-key" https://api.example.com/...
→ ヘッダーを付けない通常アクセスは Blue のまま
```

### IDサービスでの推奨検証フロー

```
Step 1: テスト用ドメインで QA チームが検証
  条件: Host = test.api.example.com → Green
  → E2E テスト実行、新機能の動作確認

Step 2: 社内 IP で社内ユーザーが検証
  条件: Source IP = 社内CIDR → Green
  → 社内ユーザーが通常業務で Green を使う

Step 3: 重み付けで段階的に全体切り替え
  Blue 95 : Green 5 → 75:25 → 50:50 → 0:100
  → 一般ユーザーにも徐々に展開
```

---

## ECS Fargate

```
役割: アプリケーションコンテナの実行環境

Blue Service:
  Task Definition: idp-server:v1
  Container Image: 123456789012.dkr.ecr.../idp-server:1.0.0
  Desired Count: 4
  → Blue Target Group に登録

Green Service:
  Task Definition: idp-server:v2
  Container Image: 123456789012.dkr.ecr.../idp-server:2.0.0
  Desired Count: 4
  → Green Target Group に登録
```

- アプリ自体はステートレス（セッション・トークンは Redis/Aurora に保存）
- Graceful Shutdown（`server.shutdown=graceful`）で処理中リクエストを完了させる
- Green のヘルスチェックが全て通ってから切り替え

---

## Aurora（RDS）の詳細

RDS コンソールの「ブルー/グリーンデプロイの作成」で、**Clone + 論理レプリケーションを全自動**で実行するマネージド機能。

### 内部で何が起きるか

```
「ブルー/グリーンデプロイの作成」を実行:

  ① Aurora ストレージボリュームを Clone（Copy-on-Write）
     → 物理ページを共有、データコピーなし、瞬時に完了
     → 500GB の DB でも追加ストレージはほぼゼロ

  ② Clone 上に Green コンピュートインスタンスを作成
     → Blue と Green は別々の Writer/Reader インスタンス
     → ただしストレージの物理ページは共有（CoW）

  ③ Blue → Green の論理レプリケーションを自動設定
     → PostgreSQL: rds.logical_replication
     → MySQL: binlog
     → Blue への書き込みが Green にリアルタイム反映
     → 手動設定不要

                   ストレージ層（物理ページ共有）
  Blue Instance ──→ ┌──────────────────────────────┐
  (Writer/Reader)   │ Page 1 ──── 共有 ──── Page 1 │ ←── Green Instance
                    │ Page 2 ──── 共有 ──── Page 2 │     (Writer/Reader)
                    │ Page 3                Page 3' │ ← Green 変更分だけコピー
                    └──────────────────────────────┘
                              ↑
                    + 論理レプリケーション（Blue→Green、自動）
                    + Blue の新規書き込みも Green に反映
```

### 切り替え（Switchover）の動作

```
切り替え実行（通常1分以内）:
  1. Blue/Green 両方の書き込みを一時停止
  2. ガードレールチェック自動実行
     ・レプリカラグ = 0 か？
     ・Green のヘルスは正常か？
  3. エンドポイント名を入れ替え
  4. 書き込み再開

→ アプリ側のエンドポイント設定変更は不要！
```

### Global Database の場合

```
Blue/Green 作成時:
  プライマリリージョン (ap-northeast-1)
    Blue Cluster → Clone → Green Cluster（論理レプリ自動）
  セカンダリリージョン (us-east-1)
    Blue Secondary → Clone → Green Secondary（論理レプリ自動）

Switchover 時:
  全リージョンのクラスタが同時に切り替わる
  → アプリの設定変更不要（全リージョンのエンドポイントが自動入替）
```

### DB 共有方式との比較

| | DB 共有 | Aurora Blue/Green |
|---|---|---|
| **インスタンス** | 1つ | 2つ（別々の Writer） |
| **ストレージ** | 1つ | Clone（CoW、物理ページ共有） |
| **データ同期** | 同一DB、同期不要 | 論理レプリケーション（自動設定） |
| **スキーマ変更** | 後方互換が必須 | Green にだけ適用可能（レプリ互換の範囲） |
| **ロールバック** | マイグレーション逆適用 | 旧 Blue に戻すだけ |
| **切り替え時の停止** | なし | 数秒〜1分 |
| **追加コスト** | なし | コンピュートインスタンス分 + 変更ストレージ分 |
| **Global Database** | - | ✅ 全リージョン同時切替対応 |

### スキーマ変更の制約（論理レプリケーション互換）

論理レプリケーション中は DML（INSERT/UPDATE/DELETE）のみ同期。DDL は同期されない。

| 変更内容 | Green に適用可能？ | 説明 |
|:---|:---:|:---|
| 新テーブル追加 | ✅ | レプリ対象外なので影響なし |
| 新カラム追加（末尾） | ✅ | 既存カラムのレプリに影響なし |
| 新インデックス追加 | ✅ | レプリはインデックスに依存しない |
| カラム名の変更 | ❌ | レプリが旧カラム名で書き込もうとして失敗 |
| カラムの削除 | ❌ | レプリが削除済みカラムに書き込もうとして失敗 |
| テーブル名の変更 | ❌ | レプリが旧テーブル名を参照して失敗 |

破壊的変更は **Switchover 後**に実行するか、2リリースに分けて段階的に移行。

### 前提条件

```
Aurora PostgreSQL:
  □ rds.logical_replication = 1
  □ 全テーブルに Primary Key が必要
  □ max_replication_slots, max_logical_replication_workers の調整

Aurora MySQL:
  □ binlog_format = ROW
  □ 全テーブルに Primary Key が必要
```

---

## ElastiCache（Redis）

```
役割: セッション、トークンキャッシュ、テナント設定キャッシュ

Blue ECS (v1) ──→ ┌──────────────┐ ←── Green ECS (v2)
                   │ ElastiCache   │
                   │ (Redis)       │
                   └──────────────┘
                   ↑
              Blue/Green 両方が同じ Redis に接続
```

- **共有**: Blue/Green で分離しない（セッション継続性のため）
- ユーザーが Blue でログイン → Green に切り替わっても同じセッションが有効
- **互換性が必要**: Redis に保存するデータ形式が v1 と v2 で互換であること
- TTL で自動期限切れするデータ（セッション、トークン）は問題になりにくい

### Aurora との違い: なぜ ElastiCache は Blue/Green しないか

ElastiCache（Redis/Valkey）には **Aurora のようなマネージド Blue/Green 機能がない**。

```
Aurora:  データの本体 → 消えたら終わり → Blue/Green で安全に切り替え
Redis:   キャッシュ   → 消えても DB から再構築可能 → インプレースで十分
```

| | Aurora | ElastiCache |
|---|---|---|
| マネージド Blue/Green | ✅ あり | ❌ なし |
| データの性質 | 永続データ（消えたら終わり） | キャッシュ（消えても再構築可能） |
| アプリリリース時 | Blue/Green（スキーマ変更対応） | **共有のまま** |
| バージョンアップ時 | Blue/Green Switchover | **インプレースアップグレード** |

### ElastiCache 自体のバージョンアップ方法

アプリリリースとは**別のメンテナンスウィンドウ**で実施。

| 方式 | 説明 | 停止時間 |
|:---|:---|:---:|
| **インプレース（AWS マネージド）** | 同一クラスタ内で新ノード作成 → レプリケーション → フェイルオーバー → 旧ノード削除。エンドポイント変更なし | 数秒（フェイルオーバー） |
| **手動 Blue/Green** | 新クラスタ作成 → RedisShake でデータ同期 → DNS/エンドポイント切り替え | 切り替え時のみ |

```
インプレースアップグレード（Redis → Valkey 移行の例）:

  既存クラスタ (Redis 7.x)
  ┌──────────────────────────┐
  │ Primary (Redis) → Replica│
  │      ↓ ノード追加         │
  │ Primary (Redis) → Replica│
  │ New Replica (Valkey)      │ ← 新ノード追加、データ複製
  │      ↓ フェイルオーバー    │
  │ Primary (Valkey)          │ ← 新ノードが Primary に昇格
  │      ↓ 旧ノード削除       │
  │ Primary (Valkey) → Replica│
  └──────────────────────────┘
  → エンドポイント変更なし、アプリ設定変更不要
```

---

## CloudFront

```
役割: 静的コンテンツのキャッシュ、JWKS のキャッシュ

クライアント → CloudFront → ALB → ECS
                 ↑
          キャッシュされた JWKS は
          Blue/Green 切り替え後も有効
          （署名鍵を変更しなければ）
```

- ALB をオリジンとして設定しているだけなので、Blue/Green の影響なし
- JWKS キャッシュの TTL に注意: 署名鍵ローテーション時は Invalidation が必要

---

## S3

```
役割: 監査ログ・セキュリティイベントの長期保存

Blue/Green 両方が同じ S3 バケットに書き込み → 切り替えの影響なし
```

- 完全に共有、Blue/Green の影響を受けない
- Object Lock (WORM) で監査ログの改ざん防止

---

## 関連ドキュメント

- [Blue-Green デプロイ実践](deployment-blue-green-practice): リリース手順
- [Blue-Green デプロイ運用ガイド](deployment-blue-green-operations): 運用注意点、IaC 分担
