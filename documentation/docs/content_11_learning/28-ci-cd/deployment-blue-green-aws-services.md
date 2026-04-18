# Blue-Green デプロイ: AWS サービス別ガイド

[Blue-Green デプロイ実践](deployment-blue-green-practice) で使用する各 AWS サービスが、Blue/Green でどう振る舞うかを整理します。

---

## サービス一覧

| サービス | Blue/Green | 切り替え方法 | 切り替え時の停止 | 状態の扱い |
|:---|:---:|:---|:---:|:---|
| **ALB** | 共有 | リスナールールの重み変更 | なし | ステートレス |
| **ALB mTLS** | 共有 | なし（リスナーレベルで処理） | なし | Trust Store（共有） |
| **ECS Fargate** | 分離 | ALB Target Group 切り替え | なし | ステートレス（アプリ） |
| **Aurora** | 分離 | Switchover（エンドポイント入替） | 数秒〜1分 | ステートフル（レプリケーション同期） |
| **ElastiCache** | 共有 | なし（両方が接続） | なし | ステートフル（セッション） |
| **CloudFront** | 共有 | なし（ALBがオリジン） | なし | キャッシュ |
| **Route 53** | 共有 | なし（ALBを指す） | なし | DNS |
| **S3** | 共有 | なし | なし | ステートフル（アーカイブ） |
| **CloudWatch** | 共有 | なし | なし | メトリクス・ログ |
| **PrivateLink** | 共有 | なし（ALB TG 切り替えで対応） | なし | NLB → ALB 経路 |
| **NAT Gateway** | 共有 | なし | なし | 送信元 IP（EIP） |
| **Network Firewall** | 共有 | なし（mTLS は TLS 検査除外） | なし | ルールグループ |

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

### mTLS（相互TLS認証）との組み合わせ

FAPI（Financial-grade API）などで mTLS（`tls_client_auth`）を使う場合、Blue-Green デプロイとの関係を整理する。

#### mTLS は ALB リスナーレベルで処理される

```
クライアント ──[mTLS ハンドシェイク]──▶ ALB ──[HTTP]──▶ Blue TG (重み X)
                                                  ──▶ Green TG (重み Y)

ポイント:
  ・mTLS の検証は ALB で完結する（リスナーレベル）
  ・TLS 終端後に Target Group へのルーティングが行われる
  ・トラフィック切り替え（重み変更）は HTTP レベルの操作
  → mTLS に影響しない
```

#### ALB の mTLS モード

| モード | 動作 | Blue-Green への影響 |
|:---|:---|:---|
| **Verify** | ALB が Trust Store に基づきクライアント証明書を検証 | なし（ALB で完結） |
| **Passthrough** | ALB は検証せず `X-Amzn-Mtls-Clientcert` ヘッダーで転送 | Blue/Green 両方でヘッダー処理の互換性が必要 |

IDサービスでは **Verify モード推奨**。ALB で検証が完結するため、Blue/Green の切り替えに影響しない。

#### 設定の共有範囲

```
┌────────────────────────────────────────────────────────────┐
│ ALB Listener                                       共有    │
│ ├── mTLS 設定（Verify / Passthrough）                      │
│ ├── Trust Store（CA 証明書バンドル）                        │
│ └── サーバー証明書（ACM）                                  │
│                                                            │
│ → これらは Blue/Green 共通。環境ごとに分けられない         │
│ → Trust Store 変更は Blue/Green 両方に即時反映             │
└────────────────────────────────────────────────────────────┘
```

#### 証明書バインドトークン（cnf クレーム）

```
FAPI の tls_client_auth では、アクセストークンにクライアント証明書の
thumbprint（x5t#S256）がバインドされる。

  ① クライアント ──[mTLS]──▶ ALB ──▶ Blue (v1)
     → トークン発行、cnf.x5t#S256 を Aurora に保存

  ② クライアント ──[mTLS]──▶ ALB ──▶ Green (v2)
     → トークン検証、Aurora（論理レプリ）から cnf を読み取り
     → 同じ ALB なので同じ thumbprint が抽出される → 検証成功

  → ALB が mTLS を終端するため、Blue/Green どちらでも
    同じ証明書情報が取得でき、トークンバインディングは一貫する
```

#### パフォーマンスの注意点

ALB で mTLS を有効化すると **TLS セッション再開（Session Resumption）が無効になる**。すべての接続でフル TLS ハンドシェイクが必要になる。

```
mTLS なし:
  初回: Full Handshake（数十ms〜）
  2回目以降: Session Resumption（数ms〜）
  ※ レイテンシは環境・ネットワーク条件により異なる

mTLS あり:
  毎回: Full Handshake（数十ms〜）
  → Canary フェーズ（5%:95%）でもレイテンシ特性は同じ
  → p95/p99 レイテンシを通常より注意深く監視
```

#### やってはいけないこと

```
❌ Blue-Green アプリデプロイと CA ローテーションを同時に実行
   → 障害時にアプリの問題か CA の問題か切り分けできない
   → CA ローテーションは独立した作業として実施

❌ NLB + TCP パススルーで mTLS を実現しようとする
   → 各 ECS タスクで TLS 終端が必要になり、証明書管理が複雑化
   → ALB Verify モードを使うべき
   → PrivateLink が必要な場合は NLB → ALB 構成にする
     （→ ネットワーク構成 > PrivateLink セクション参照）

❌ Green テスト時に Trust Store を変更する
   → Trust Store は ALB リスナー（共有）に紐づく
   → 変更すると Blue にも即座に影響する
   → 異なる Trust Store でテストしたい場合は別の ALB/リスナーを使う
```

#### チェックリスト

```
□ ALB の mTLS モードが Verify に設定されている
□ Trust Store に必要な CA 証明書が登録されている
□ Passthrough モードの場合、Blue/Green 両バージョンで
  X-Amzn-Mtls-* ヘッダーの処理が互換性を持つ
□ CA ローテーションはアプリデプロイと別タイミングで実施する
□ Canary フェーズ中の p95/p99 レイテンシを監視する
□ CRL（証明書失効リスト）が最新であることを確認する
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

### CloudFront オリジン mTLS

CloudFront はオリジン（ALB）への接続時にクライアント証明書を提示する**オリジン mTLS** をサポートしている。

```
クライアント ──[TLS]──▶ CloudFront ──[mTLS]──▶ ALB ──▶ ECS
                                       ↑
                              CloudFront がクライアント証明書を
                              提示してオリジンに接続

Blue-Green での考慮:
  ・オリジン mTLS 証明書は CloudFront のオリジン設定に紐づく
  ・ALB の Trust Store が CloudFront の証明書 CA を信頼していれば、
    Target Group の切り替えに影響しない
  ・ALB 側の Trust Store 変更はオリジン mTLS にも影響するので注意
```

### キャッシュと Blue-Green

```
キャッシュ Invalidation が必要なケース:
  ・JWKS エンドポイント（署名鍵ローテーション時のみ）
  ・.well-known/openid-configuration（エンドポイント URL 変更時のみ）

不要なケース:
  ・トークンエンドポイント、認可エンドポイント、UserInfo
    → Cache-Control: no-store で設定されているべき
  ・Blue-Green の切り替え自体ではキャッシュ Invalidation は不要
    （オリジン ALB のエンドポイントは変わらない）
```

---

## S3

```
役割: 監査ログ・セキュリティイベントの長期保存

Blue/Green 両方が同じ S3 バケットに書き込み → 切り替えの影響なし
```

- 完全に共有、Blue/Green の影響を受けない
- Object Lock (WORM) で監査ログの改ざん防止

---

## ネットワーク構成

Blue-Green デプロイに影響するネットワークコンポーネントを整理する。

外部クライアント（RP、金融機関等）は**別 AWS アカウント**にいることが一般的。IDサービス側の Blue-Green 切り替えが、クライアント側の接続にどう影響するかが重要なポイントになる。

### 全体構成図

```
┌─────────────────────────────────────────────────────────────────────┐
│ クライアント AWS アカウント（金融機関 A）                              │
│                                                                     │
│  ┌─────────────┐                                                    │
│  │ アプリ (RP)   │                                                   │
│  └──────┬──────┘                                                    │
│         │                                                           │
│         ▼                                                           │
│  接続方式を選択:                                                     │
│  ├── ① インターネット経由（CloudFront → ALB）                       │
│  ├── ② PrivateLink（VPC Endpoint → NLB → ALB）                     │
│  └── ③ Transit Gateway（TGW → IDサービス VPC → ALB）               │
└─────────────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────────────┐
│ IDサービス AWS アカウント                                             │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │ ALB (mTLS Verify)                                    共有   │  │
│  │ ├── Trust Store（CA 証明書）                                  │  │
│  │ └── Blue TG / Green TG の重み切り替え ← ここだけ変わる       │  │
│  └─────┬───────────────────────────────┬────────────────────────┘  │
│        │                               │                           │
│  ┌─────▼─────────────┐  ┌──────────────▼──────────┐               │
│  │ Blue ECS (v1)      │  │ Green ECS (v2)          │               │
│  └─────┬─────────────┘  └──────────────┬──────────┘               │
│        │                               │                           │
│  ┌─────▼───────────────────────────────▼──────────────────────┐   │
│  │ NAT Gateway (共有, EIP 固定)                                │   │
│  │ → 外部 IdP、Webhook、OCSP/CRL チェック                     │   │
│  └────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌────────────────────────────────────────────────────────────┐   │
│  │ VPC Endpoints: ECR, CloudWatch, S3, STS                    │   │
│  └────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘

重要:
  どの接続方式でも、Blue-Green 切り替えは ALB Target Group の
  重み変更のみ。クライアント側の設定変更は一切不要。
```

### 接続方式と Blue-Green への影響

| 接続方式 | クロスアカウント | Blue-Green 時のクライアント側変更 | mTLS |
|:---|:---:|:---|:---:|
| **① インターネット経由** | 無関係 | なし（DNS/ALB 変わらない） | ALB で処理 |
| **② PrivateLink** | あり | なし（VPC Endpoint 変わらない） | NLB→ALB で処理 |
| **③ Transit Gateway** | あり | なし（同一 VPC なら） | TGW は透過 |

**結論: どの方式でもクライアント側の変更は不要。** ただし接続方式によって注意点が異なる。

### PrivateLink（クロスアカウント接続）

金融機関等の外部クライアントが**別 AWS アカウントの VPC** から PrivateLink でIDサービスに接続するケース。FAPI で最も一般的な構成。

```
クライアント AWS アカウント              IDサービス AWS アカウント
┌─────────────────────────┐        ┌────────────────────────────────┐
│ クライアント VPC          │        │ IDサービス VPC                  │
│                         │        │                                │
│  ┌──────────────────┐   │        │  ┌────────┐    ┌────────────┐ │
│  │ アプリ (RP)        │  │        │  │  NLB   │───▶│ ALB        │ │
│  │   │               │  │        │  │ (TCP)  │    │ (mTLS      │ │
│  │   ▼               │  │        │  └────────┘    │  Verify)   │ │
│  │ VPC Endpoint      │──────────────▶              │    │       │ │
│  │ (ENI, Private IP) │  │ Private │  Endpoint     │    ▼       │ │
│  │                   │  │  Link   │  Service      │ Blue TG    │ │
│  │ DNS:              │  │         │               │ Green TG   │ │
│  │ vpce-xxx.svc.     │  │         │               └────────────┘ │
│  │  local.test       │  │         │                              │
│  └──────────────────┘   │        └────────────────────────────────┘
└─────────────────────────┘

Blue-Green 切り替え時に何が変わるか:

  変わらないもの（クライアント側）:
    ・VPC Endpoint の ENI / プライベート IP
    ・VPC Endpoint の DNS 名
    ・クライアントの Security Group / Route Table
    ・クライアントのアプリケーション設定

  変わるもの（IDサービス側のみ）:
    ・ALB の Target Group 重み（Blue 100→0, Green 0→100）

  → クライアントへの事前通知や設定変更依頼は不要
```

#### クロスアカウントでの Endpoint Service 設定

```
IDサービス側（サービスプロバイダー）:
  ① NLB を作成（TCP:443 リスナー）
  ② ALB タイプのターゲットグループで NLB → ALB を接続
  ③ VPC Endpoint Service を作成（NLB を指定）
  ④ クライアントの AWS アカウント ID を許可リストに追加

クライアント側（サービスコンシューマー）:
  ① VPC Endpoint（Interface 型）を作成
  ② Endpoint Service のサービス名を指定
  ③ ENI が作成され、プライベート IP が割り当てられる
  ④ プライベート DNS を有効化（推奨）

Blue-Green デプロイ:
  ・上記の設定は一度行えば、以降の Blue-Green で変更不要
  ・NLB → ALB → ECS の経路の中で、ALB TG の重みだけを変える
  ・NLB / Endpoint Service / VPC Endpoint は一切触らない
```

提供側・利用側の詳細な設定手順は [VPC・ネットワーキング > PrivateLink によるクロスアカウントサービス公開](../27-aws/01-fundamentals/aws-vpc-networking.md#privatelink-によるクロスアカウントサービス公開) を参照。

#### NLB → ALB 構成での mTLS

```
NLB (TCP:443) → ALB (HTTPS:443, mTLS Verify) → ECS

NLB の設定:
  ・リスナー: TCP 443（TLS 終端しない）
  ・ターゲット: ALB の IP アドレス（ALB タイプのターゲットグループ）
  ・ヘルスチェック: TCP or HTTP

ALB の設定:
  ・リスナー: HTTPS 443, mTLS Verify モード
  ・Trust Store: クライアント CA 証明書
  ・ターゲット: Blue TG / Green TG（重み切り替え）

重要:
  ・NLB は TCP リスナー必須（TLS リスナーでは ALB タイプ TG に転送不可）
  ・クライアント証明書は NLB を透過して ALB に到達する
  ・ALB が mTLS 検証を行い、Blue/Green TG にルーティング
  → PrivateLink + mTLS + Blue-Green がすべて共存可能
```

### Transit Gateway（クロスアカウント接続）

外部クライアントが **Transit Gateway 経由**でIDサービスに接続するケース。同一組織内の別アカウントや、RAM（Resource Access Manager）で共有された TGW を使うパターン。

```
クライアント                  共有              IDサービス
AWS アカウント A             TGW               AWS アカウント B
┌───────────────┐    ┌──────────────┐    ┌──────────────────┐
│ クライアント VPC │    │ Transit      │    │ IDサービス VPC    │
│               │    │ Gateway      │    │                  │
│ アプリ ────────────▶│ Route Table  │───▶│ ALB ──▶ Blue TG  │
│               │    │              │    │     ──▶ Green TG │
│               │    │ (RAM で共有) │    │                  │
└───────────────┘    └──────────────┘    └──────────────────┘

Blue-Green 切り替え時に何が変わるか:

  IDサービスの Blue/Green が同一 VPC 内の場合:
    ・TGW のルートテーブル: 変更なし
    ・TGW の Attachment: 変更なし
    ・クライアント VPC のルートテーブル: 変更なし
    ・ALB の Target Group 重みのみ変更

  → PrivateLink と同様、クライアント側の変更は不要
```

#### パターン1: Blue/Green 同一 VPC（推奨）

```
Blue/Green が同じ IDサービス VPC 内にある場合:

  クライアント VPC ──[TGW]──▶ IDサービス VPC ──▶ ALB ──▶ Blue/Green TG

  ・TGW の設定変更は一切不要
  ・ALB Target Group の重み変更だけでトラフィック切り替え
  ・クライアント VPC（別アカウント）はデプロイを認識しない
  ・ロールバックも ALB の重み変更だけで瞬時
```

#### パターン2: 別 VPC（非推奨）

やむを得ず Blue-VPC と Green-VPC を分離する場合の手順。

```
切り替え前:
  TGW Route Table:
    10.100.0.0/16 → Blue-VPC Attachment（静的ルート）

切り替え:
  aws ec2 replace-transit-gateway-route \
    --transit-gateway-route-table-id tgw-rtb-xxx \
    --destination-cidr-block 10.100.0.0/16 \
    --transit-gateway-attachment-id tgw-attach-green-yyy

ロールバック:
  aws ec2 replace-transit-gateway-route \
    --transit-gateway-route-table-id tgw-rtb-xxx \
    --destination-cidr-block 10.100.0.0/16 \
    --transit-gateway-attachment-id tgw-attach-blue-xxx
```

**戻り経路の設定漏れに注意**:

```
往路（コンシューマ → IdP）:
  コンシューマ VPC → TGW → Green-VPC  ← TGW ルートで切り替え

復路（IdP → コンシューマ）:
  Green-VPC → TGW → コンシューマ VPC  ← Green-VPC のルートテーブルに
                                         コンシューマ CIDR → TGW の
                                         エントリが必要

  → 復路ルートが未設定だとレスポンスが返らない
  → 別 VPC パターンの障害原因 No.1
```

#### ルート伝搬の遅延

```
ルート変更の反映タイミング（AWS は SLA を公表していない）:

  静的ルート変更（replace-transit-gateway-route）:
    → 数秒〜十数秒で反映（結果整合性）
    → API は同期的に返るが、データプレーンの反映にラグがある

  VPC Attachment 経由のルート伝搬:
    → 30秒〜数分で反映

  VPN/Direct Connect（BGP ベース）:
    → 60〜90秒以上（BGP タイマー依存）

影響:
  ・伝搬中は新規接続がルーティングされない、または旧先に飛ぶ
  ・TGW にはコネクションドレインがない（L3 ルーター）
  ・確立済み TCP 接続は維持されるが、旧先が停止すると切断

IDサービスへの影響:
  ・OAuth/OIDC の認可コードフローは複数ステップ
  ・認可リクエスト → トークン交換の間にルートが切り替わると
    セッション不整合が発生しうる
  → これが別 VPC パターンを推奨しない最大の理由
```

#### TGW と mTLS の関係

```
TGW は L3（IP ルーティング）で動作する:
  ・パケットのペイロードを検査しない
  ・TLS / mTLS トラフィックに干渉しない
  ・暗号化・復号を行わない
  → mTLS は TGW を透過的に通過する

MTU の注意:
  ・TGW の MTU: 8500 バイト（VPC 間）
  ・VPN Attachment の MTU: 1500 バイト
  ・mTLS のクライアント証明書チェーンは数 KB になることがある
  ・TLS ハンドシェイクの Certificate メッセージが大きくなり、
    IP フラグメンテーションが発生する可能性がある

  対策:
    ・Security Group / NACL で ICMP Type 3 Code 4
      （Fragmentation Needed）を許可する
    ・PMTUD（Path MTU Discovery）が正常に動作することを確認
    ・VPN 経由の場合は MSS Clamping を設定

  → ICMP をブロックしていると、mTLS ハンドシェイクが
    原因不明で失敗する「MTU ブラックホール」が発生する
```

#### TGW + PrivateLink の使い分け（クロスアカウント）

```
外部クライアント（別 AWS アカウント）からの接続方式の比較:

  TGW 経由（RAM で共有）:
    クライアント VPC → TGW → IDサービス VPC → ALB
    ・同一組織内のアカウント間で使いやすい
    ・TGW を RAM で共有する必要がある
    ・Blue-Green: 同一 VPC なら影響なし
    ・コスト: Attachment $0.05/hr + Data $0.02/GB（参考価格、リージョンにより異なる）

  PrivateLink 経由:
    クライアント VPC → VPC Endpoint (ENI) → NLB → ALB
    ・組織外のアカウントでも利用可能（サービス名を共有するだけ）
    ・TGW を共有する必要がない
    ・Blue-Green: ALB TG 切り替えのみ、一切影響なし
    ・コスト: Endpoint $0.01/hr/AZ + Data $0.01/GB（参考価格、リージョンにより異なる）

  使い分けの指針:
    ・FAPI / 金融機関連携 → PrivateLink 推奨
      → 組織外アカウントとの接続が簡単
      → クライアント側は VPC Endpoint を作るだけ
      → mTLS は NLB→ALB 構成で対応

    ・同一組織内の内部サービス → TGW で十分
      → 既に TGW があれば追加コストが少ない
      → Blue-Green は同一 VPC 内で ALB 切り替え

    ・併用パターン（よくある構成）:
      → 組織内通信は TGW
      → 外部金融機関への公開は PrivateLink
      → IDサービスは ALB レベルの Blue-Green だけ管理すればよい
```

#### 事前検証ツール

```
切り替え前の確認:

① TGW Route Analyzer（Network Manager）:
   ・送信元 → 宛先の経路をシミュレーション（実パケット送信なし）
   ・復路の検証も可能
   ・TGW ルートテーブルのみ検証（VPC ルートテーブル/SG は対象外）

② VPC Reachability Analyzer:
   ・エンドツーエンドの到達性を検証
   ・VPC ルートテーブル、SG、NACL も含めて検証
   ・ただし TGW 経由のパスは一部制限あり

③ CloudWatch メトリクス（切り替え中の監視）:
   ・PacketDropCountBlackhole: ルートなしによるドロップ
   ・PacketDropCountNoRoute: 宛先不明によるドロップ
   → 切り替え直後にこれらが急増したらルート設定ミス

④ ルートテーブルのエクスポート:
   aws ec2 search-transit-gateway-routes \
     --transit-gateway-route-table-id tgw-rtb-xxx \
     --filters "Name=state,Values=active"
   → 切り替え前後で diff を取り、意図通りか確認
```

#### コスト

```
※ 以下は参考価格。リージョンにより異なるため、公式料金ページを確認すること。

同一 VPC パターン:
  ・TGW Attachment: 1 × $0.05/hr ≒ $36/月
  ・ALB: 1 台（共有）

別 VPC パターン:
  ・TGW Attachment: 2 × $0.05/hr ≒ $72/月（移行期間中）
  ・ALB: 2 台（移行期間中）
  ・デプロイ完了後に旧 VPC の Attachment を削除すれば約 $36/月に戻る

データ処理:
  ・$0.02/GB（双方向）
  ・OAuth/OIDC トラフィックは小さい（JSON, JWT）ため影響軽微
  ・eKYC 等で画像データを扱う場合は注意
```

### Security Group

```
ALB Security Group:
  ├── Inbound:  443 from 0.0.0.0/0（または CloudFront プレフィックスリスト）
  └── Outbound: アプリポート to Blue Task SG, Green Task SG

Blue/Green Task Security Group（共通にするのが推奨）:
  ├── Inbound:  アプリポート from ALB SG
  ├── Outbound: 443 to VPC Endpoints SG
  ├── Outbound: 443 to NAT Gateway（外部通信）
  └── Outbound: Aurora ポート to Aurora SG

注意:
  ・Blue と Green で別の Security Group を使う場合、
    ALB の Outbound に両方の SG を追加しておく必要がある
  ・Green のタスクを起動する前に SG の設定を完了させること
  ・推奨: Blue/Green 共通の Task Security Group を使う
```

### NACL

```
サブネット NACL:
  ・Blue と Green が同じサブネットなら追加設定不要
  ・異なるサブネットの場合、Green サブネットの NACL に以下を設定:
    - Inbound: ALB サブネットからのアプリポート許可
    - Outbound: エフェメラルポート (1024-65535) 許可
    - Aurora/Redis サブネットへの通信許可

  ・NACL はステートレスなので Inbound/Outbound 両方の設定が必須
  ・Green 環境を新しいサブネットに構築する場合、
    NACL の設定漏れが最もよくある障害原因
```

### NAT Gateway と送信元 IP

```
外部サービスへのアウトバウンド通信:
  ├── 外部 IdP 呼び出し（Federation）
  ├── Webhook 配信（セキュリティイベント）
  ├── OCSP / CRL チェック（証明書検証）
  └── 外部 eKYC サービス呼び出し

Blue ECS ──▶ NAT Gateway (EIP: 203.0.113.10) ──▶ 外部サービス
Green ECS ──▶ NAT Gateway (EIP: 203.0.113.10) ──▶ 外部サービス
                    ↑
             同じ NAT Gateway を共有

重要:
  ・FAPI / 金融連携では、送信元 IP が外部サービスの許可リストに
    登録されていることが多い
  ・Blue/Green で異なる NAT Gateway（= 異なる EIP）を使うと、
    Green からの通信が外部サービスに拒否される
  ・対策: Blue/Green が同じサブネット → 同じ NAT Gateway を使う
  ・AZ をまたぐ場合: 各 AZ の NAT Gateway に同じ EIP を割り当て
    られないため、外部サービスに全 AZ の EIP を事前登録しておく
```

### AWS Network Firewall

```
TLS インスペクションと mTLS の関係:

  Network Firewall の TLS インスペクション:
    ① クライアントとの TLS を終端
    ② トラフィックを検査
    ③ サーバーへの新しい TLS 接続を確立

  → mTLS の場合、①でクライアント証明書が破棄される
  → サーバー（ALB）にクライアント証明書が届かない
  → mTLS が機能しなくなる

対策:
  ・mTLS トラフィックは TLS インスペクションから除外する
  ・スコープ設定で IDサービス宛の通信をバイパス
  ・IP/ポートベースのルール、SNI ベースのルールは mTLS でも使用可能

Blue-Green との関係:
  ・Blue/Green が同じ VPC・サブネットなら FW ルール変更不要
  ・異なる IP レンジを使う場合、FW ルールに Green の IP を事前追加
```

### VPN（管理者アクセス）

```
管理 API へのアクセス経路:

  管理者 ──[Client VPN]──▶ VPC ──▶ Internal ALB ──▶ ECS
                                        ↑
                               Blue/Green 切り替えは
                               ALB Target Group レベル

  ・VPN トンネル自体は Blue-Green に影響されない
  ・Internal ALB の DNS 名は変わらない
  ・管理者は切り替えを意識せずに操作できる
```

### Route 53 プライベートホストゾーン

```
内部サービスディスカバリ:

  推奨（ALB ベース）:
    idp.internal → Internal ALB → Blue/Green TG
    ・DNS 変更不要、ALB レベルで切り替え
    ・TTL 伝搬の遅延なし

  非推奨（DNS ベース切り替え）:
    idp.internal → Blue ALB の IP → Green ALB の IP に変更
    ・TTL キャッシュにより古い IP にアクセスされる
    ・FAPI では認可フロー途中の切り替えでセッション不整合が起きる

→ 内部通信でも ALB エイリアスレコードを使い、
   切り替えは ALB Target Group の重み変更で行う
```

### ネットワーク構成チェックリスト

```
Blue-Green デプロイ前の確認:

□ Blue/Green が同一 VPC・同一サブネットにある（推奨）
□ Green サブネットの NACL が正しく設定されている
□ ALB の Security Group が Green Task SG へのアウトバウンドを許可
□ Green Task SG が ALB SG からのインバウンドを許可
□ Green サブネットから NAT Gateway へのルートが設定されている
□ NAT Gateway の EIP が外部サービスの許可リストに登録済み
□ VPC Endpoints が Green サブネットからアクセス可能
□ Network Firewall で mTLS トラフィックが TLS インスペクション除外
□ PrivateLink 構成の場合、NLB → ALB の経路が正常
□ Transit Gateway を使う場合、ルートテーブル変更不要であることを確認
```

---

## 関連ドキュメント

- [Blue-Green デプロイ実践](deployment-blue-green-practice): リリース手順
- [Blue-Green デプロイ運用ガイド](deployment-blue-green-operations): 運用注意点、IaC 分担
