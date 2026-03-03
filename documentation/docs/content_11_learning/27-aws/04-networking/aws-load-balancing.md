# ロードバランシング（ALB/NLB）

Elastic Load Balancing（ELB）は、受信トラフィックを複数のターゲット（EC2インスタンス、コンテナ、IPアドレス等）に自動的に分散するマネージドサービスです。高可用性、自動スケーリング、セキュリティを提供し、アプリケーションの信頼性を向上させます。

---

## 所要時間
約35分

## 学べること
- Elastic Load Balancingの概要と必要性
- ALB（L7）とNLB（L4）の特徴と使い分け
- ターゲットグループとヘルスチェックの設定
- SSL/TLS終端とACM証明書の利用
- スティッキーセッションとクロスゾーンロードバランシング
- IDサービスにおけるALB構成パターン

## 前提知識
- TCP/IPとHTTPプロトコルの基礎
- OSI参照モデルにおけるL4/L7の概念
- VPCとサブネットの基本（前章参照）
- SSL/TLSの基礎知識

---

## 目次
1. [Elastic Load Balancingの概要](#1-elastic-load-balancingの概要)
2. [ALB（Application Load Balancer）](#2-albapplication-load-balancer)
3. [NLB（Network Load Balancer）](#3-nlbnetwork-load-balancer)
4. [ALB vs NLB 比較](#4-alb-vs-nlb-比較)
5. [ターゲットグループ](#5-ターゲットグループ)
6. [ヘルスチェック設定](#6-ヘルスチェック設定)
7. [SSL/TLS終端](#7-ssltls終端)
8. [スティッキーセッション](#8-スティッキーセッション)
9. [クロスゾーンロードバランシング](#9-クロスゾーンロードバランシング)
10. [ALBのリスナールールとアクション](#10-albのリスナールールとアクション)
11. [IDサービスでの活用](#11-idサービスでの活用)
12. [まとめ](#12-まとめ)

---

## 1. Elastic Load Balancingの概要

### なぜロードバランサーが必要か

ロードバランサーは、単一サーバーへのトラフィック集中を防ぎ、アプリケーションの可用性とスケーラビリティを確保するために不可欠なコンポーネントです。

```
ロードバランサーなし:              ロードバランサーあり:

  Client                          Client
    │                               │
    ▼                               ▼
  ┌──────────┐               ┌──────────────┐
  │ Server 1 │               │ Load Balancer│
  │ (単一障害 │               └──────┬───────┘
  │  点)     │                ┌─────┼─────┐
  └──────────┘                ▼     ▼     ▼
                           ┌────┐┌────┐┌────┐
  問題:                     │Srv1││Srv2││Srv3│
  - 1台で全負荷を処理       └────┘└────┘└────┘
  - 障害時にサービス停止
  - スケール不可             利点:
                            - 負荷分散
                            - 障害時に自動切り替え
                            - 水平スケーリング可能
```

### ELBの種類

AWSは3種類のロードバランサーを提供しています。

| 種類 | レイヤー | リリース | 現在の推奨 |
|------|---------|---------|-----------|
| Application Load Balancer（ALB） | L7（HTTP/HTTPS） | 2016年 | Webアプリケーション向け |
| Network Load Balancer（NLB） | L4（TCP/UDP/TLS） | 2017年 | 高パフォーマンス/低レイテンシ向け |
| Classic Load Balancer（CLB） | L4/L7 | 2009年 | 非推奨、移行推奨 |

---

## 2. ALB（Application Load Balancer）

ALBはHTTP/HTTPSトラフィックに特化したL7ロードバランサーです。リクエストの内容（ホスト名、パス、ヘッダー等）に基づいた高度なルーティングが可能です。

### ALBの構成要素

```
                        ┌──────────────────────────────┐
                        │          ALB                 │
                        │                              │
  Client ──→ ┌──────────┤  リスナー (HTTPS:443)         │
             │          │    │                         │
             │          │    ├── ルール1: /api/*        │
             │          │    │   → API ターゲットグループ │
             │          │    │                         │
             │          │    ├── ルール2: /auth/*       │
             │          │    │   → Auth ターゲットグループ│
             │          │    │                         │
             │          │    └── デフォルト              │
             │          │        → Web ターゲットグループ │
             │          │                              │
             │          └──────────────────────────────┘
             │
             │  リスナー (HTTP:80)
             │    └── 全リクエスト → HTTPS:443にリダイレクト
             │
```

### ホストベースルーティング

```
  api.idp.example.com    → API ターゲットグループ
  auth.idp.example.com   → Auth ターゲットグループ
  admin.idp.example.com  → Admin ターゲットグループ
```

### パスベースルーティング

```
  /api/v1/*              → API v1 ターゲットグループ
  /api/v2/*              → API v2 ターゲットグループ
  /.well-known/*         → Discovery ターゲットグループ
  /authorize             → Auth ターゲットグループ
  /*                     → Default ターゲットグループ
```

### ALBの主な機能

| 機能 | 説明 |
|------|------|
| パスベースルーティング | URLパスに基づくトラフィック振り分け |
| ホストベースルーティング | ホスト名に基づくトラフィック振り分け |
| HTTPヘッダーベースルーティング | リクエストヘッダーに基づく振り分け |
| リダイレクト | HTTP→HTTPSリダイレクト等 |
| 固定レスポンス | メンテナンスページ等の直接レスポンス |
| 認証統合 | OIDC/Cognito認証の組み込み |
| WebSocket | WebSocketプロトコル対応 |
| HTTP/2 | HTTP/2プロトコル対応 |

---

## 3. NLB（Network Load Balancer）

NLBはTCP/UDP/TLSトラフィックに対応するL4ロードバランサーです。極めて低いレイテンシと高いスループットが特徴です。

### NLBの特徴

```
  NLBの処理フロー:

  Client (src: 203.0.113.10:54321)
    │
    ▼
  ┌──────────────────────┐
  │  NLB                 │
  │  静的IP / Elastic IP │
  │  (固定IPアドレス)     │
  │                      │
  │  コネクションを       │
  │  パケットレベルで転送  │
  └──────────┬───────────┘
             │
    ┌────────┴────────┐
    ▼                 ▼
  ┌──────┐         ┌──────┐
  │ Srv1 │         │ Srv2 │
  │      │         │      │
  │ クライアントIPが│  src IPが見える
  │ 保持される     │  (透過的)
  └──────┘         └──────┘
```

### NLBの主な特性

| 特性 | 詳細 |
|------|------|
| レイテンシ | マイクロ秒レベル（ALBはミリ秒レベル） |
| スループット | 数百万リクエスト/秒の処理能力 |
| 静的IP | AZ毎にElastic IPを割り当て可能 |
| ソースIP保持 | クライアントIPがターゲットで見える |
| プロトコル | TCP, UDP, TLS, TCP_UDP |
| PrivateLink | AWS PrivateLinkのサービスエンドポイントとして利用可能 |

---

## 4. ALB vs NLB 比較

| 項目 | ALB | NLB |
|------|-----|-----|
| OSIレイヤー | L7（アプリケーション層） | L4（トランスポート層） |
| プロトコル | HTTP, HTTPS, gRPC | TCP, UDP, TLS |
| ルーティング | パス、ホスト、ヘッダー、クエリ | ポートベース |
| レイテンシ | ミリ秒 | マイクロ秒 |
| 静的IP | 非対応（DNS名のみ） | 対応（Elastic IP） |
| ソースIP | X-Forwarded-Forヘッダー | 透過的に保持 |
| SSL終端 | 対応 | 対応（TLSリスナー） |
| WebSocket | 対応 | 対応 |
| 認証統合 | OIDC/Cognito対応 | 非対応 |
| コスト | LCU時間 + 固定時間 | NLCU時間 + 固定時間 |
| 推奨用途 | Webアプリ、API | 高スループット、IoT、ゲーム |

### 選定の指針

```
ロードバランサーの選定:

  HTTPベースのアプリケーション？
  ├── Yes → コンテンツベースのルーティングが必要？
  │   ├── Yes → ALB
  │   └── No  → 超低レイテンシが必要？
  │       ├── Yes → NLB（TLSリスナー）
  │       └── No  → ALB（推奨）
  └── No  → TCP/UDPの処理？
      ├── Yes → NLB
      └── No  → 要件を再確認
```

---

## 5. ターゲットグループ

ターゲットグループは、ロードバランサーがトラフィックを振り分ける先を定義します。

### ターゲットタイプ

| ターゲットタイプ | 説明 | ユースケース |
|---------------|------|-------------|
| instance | EC2インスタンスID | EC2ベースのデプロイ |
| ip | IPアドレス | ECS（awsvpc）、オンプレミス |
| lambda | Lambda関数 | サーバーレスバックエンド |
| alb | ALB | NLBの背後にALBを配置 |

### ターゲットグループの作成例

```bash
# ALB用ターゲットグループ作成
aws elbv2 create-target-group \
  --name idp-server-tg \
  --protocol HTTPS \
  --port 8443 \
  --vpc-id vpc-0123456789abcdef0 \
  --target-type ip \
  --health-check-protocol HTTPS \
  --health-check-path /health \
  --health-check-interval-seconds 15 \
  --healthy-threshold-count 2 \
  --unhealthy-threshold-count 3 \
  --matcher '{"HttpCode": "200"}'
```

### 負荷分散アルゴリズム

| アルゴリズム | 説明 | 適用場面 |
|------------|------|---------|
| ラウンドロビン | 順番に振り分け | 均等なリクエスト |
| 最少未処理リクエスト | 処理中リクエスト数が最少のターゲットへ | リクエスト処理時間にばらつきがある場合 |
| 加重ランダム | 重み付きランダム選択 | カナリアデプロイ |

---

## 6. ヘルスチェック設定

ヘルスチェックにより、ロードバランサーは正常なターゲットにのみトラフィックを振り分けます。

### ヘルスチェックパラメータ

| パラメータ | デフォルト | 説明 |
|-----------|-----------|------|
| プロトコル | HTTPまたはHTTPS | ヘルスチェックに使用するプロトコル |
| パス | / | ヘルスチェックのリクエストパス |
| ポート | トラフィックポート | ヘルスチェック用ポート |
| 間隔 | 30秒 | ヘルスチェックの実行間隔 |
| タイムアウト | 5秒 | レスポンスのタイムアウト |
| 正常しきい値 | 5回 | 正常と判定するまでの連続成功回数 |
| 異常しきい値 | 2回 | 異常と判定するまでの連続失敗回数 |
| マッチャー | 200 | 正常と判定するHTTPステータスコード |

### ヘルスチェックの動作

```
ヘルスチェックの状態遷移:

  ┌──────────┐   成功×N回   ┌──────────┐
  │ initial  │ ───────────→ │ healthy  │
  │ (起動中)  │              │ (正常)    │
  └──────────┘              └────┬─────┘
                                 │
                            失敗×M回
                                 │
                                 ▼
                            ┌──────────┐
                            │unhealthy │ ←─── トラフィック
                            │ (異常)    │      振り分け停止
                            └────┬─────┘
                                 │
                            成功×N回
                                 │
                                 ▼
                            ┌──────────┐
                            │ healthy  │ ←─── トラフィック
                            │ (正常)    │      振り分け再開
                            └──────────┘

  N = 正常しきい値（デフォルト5）
  M = 異常しきい値（デフォルト2）
```

### ヘルスチェックエンドポイントの推奨

```
推奨: アプリケーション固有のヘルスチェックエンドポイント

  GET /health
  ┌───────────────────────────────┐
  │ チェック項目:                   │
  │  ✓ アプリケーション起動完了      │
  │  ✓ データベース接続             │
  │  ✓ 必要な設定のロード完了        │
  │                               │
  │ レスポンス:                     │
  │  200 OK → 正常                 │
  │  503 Service Unavailable → 異常│
  └───────────────────────────────┘
```

---

## 7. SSL/TLS終端

ALBおよびNLB（TLSリスナー）は、SSL/TLS終端（ターミネーション）をサポートします。AWS Certificate Manager（ACM）と統合することで、証明書の管理を簡素化できます。

### SSL/TLS終端の構成

```
SSL/TLS終端の構成パターン:

  パターン1: ALBでSSL終端（推奨）
  ┌────────┐  HTTPS   ┌──────┐  HTTP    ┌──────────┐
  │ Client │ ───────→ │ ALB  │ ───────→ │ Backend  │
  │        │  暗号化   │(終端) │  平文    │ Server   │
  └────────┘          └──────┘          └──────────┘
                        ↑
                    ACM証明書

  パターン2: エンドツーエンドTLS
  ┌────────┐  HTTPS   ┌──────┐  HTTPS   ┌──────────┐
  │ Client │ ───────→ │ ALB  │ ───────→ │ Backend  │
  │        │  暗号化   │(終端) │  再暗号化 │ Server   │
  └────────┘          └──────┘          └──────────┘
                        ↑                    ↑
                    ACM証明書           自己署名証明書等
```

### ACM証明書の利用

```bash
# ACM証明書のリクエスト（DNS検証）
aws acm request-certificate \
  --domain-name idp.example.com \
  --subject-alternative-names "*.idp.example.com" \
  --validation-method DNS

# ALBのHTTPSリスナーにACM証明書を設定
aws elbv2 create-listener \
  --load-balancer-arn arn:aws:elasticloadbalancing:ap-northeast-1:123456789012:loadbalancer/app/idp-alb/1234567890 \
  --protocol HTTPS \
  --port 443 \
  --certificates CertificateArn=arn:aws:acm:ap-northeast-1:123456789012:certificate/abcd-1234 \
  --ssl-policy ELBSecurityPolicy-TLS13-1-2-2021-06 \
  --default-actions Type=forward,TargetGroupArn=arn:aws:elasticloadbalancing:ap-northeast-1:123456789012:targetgroup/idp-server-tg/1234567890
```

### セキュリティポリシーの選択

| ポリシー | TLSバージョン | 推奨用途 |
|---------|-------------|---------|
| ELBSecurityPolicy-TLS13-1-2-2021-06 | TLS 1.3 + 1.2 | 推奨（最新クライアント対応） |
| ELBSecurityPolicy-TLS13-1-3-2021-06 | TLS 1.3のみ | 最高セキュリティ |
| ELBSecurityPolicy-2016-08 | TLS 1.0〜1.2 | レガシー互換（非推奨） |
| ELBSecurityPolicy-FS-1-2-Res-2020-10 | TLS 1.2（FS必須） | PFS強制 |

---

## 8. スティッキーセッション

スティッキーセッション（セッションアフィニティ）は、同一クライアントからのリクエストを常に同じターゲットに振り分ける機能です。

### スティッキーセッションの種類

| 種類 | 仕組み | 持続時間 |
|------|--------|---------|
| Duration-based | ALBが発行するCookie（AWSALB） | 1秒〜7日 |
| Application-based | アプリケーション発行のCookie | アプリケーション定義 |

### 動作の違い

```
スティッキーなし:                スティッキーあり:

  Client A → Req1 → Srv1        Client A → Req1 → Srv1 (Cookie設定)
  Client A → Req2 → Srv3        Client A → Req2 → Srv1 (同じサーバー)
  Client A → Req3 → Srv2        Client A → Req3 → Srv1 (同じサーバー)

  ↑ リクエスト毎に異なるサーバー   ↑ Cookieにより同じサーバーへ
```

### idp-serverでの考慮事項

idp-serverは認可コードフローなどで一時的な状態を保持します。ただし、状態はデータベースまたはRedisに保存されるため、スティッキーセッションは必須ではありません。ステートレスな設計により、どのインスタンスでもリクエストを処理できます。

---

## 9. クロスゾーンロードバランシング

クロスゾーンロードバランシングは、全てのAZのターゲットに均等にトラフィックを分散する機能です。

### クロスゾーンの効果

```
クロスゾーンなし:                  クロスゾーンあり:

  AZ-a (2台)    AZ-c (8台)        AZ-a (2台)    AZ-c (8台)
   50%の          50%の             50%の          50%の
   トラフィック    トラフィック        トラフィック    トラフィック
  ┌────┬────┐  ┌─┬─┬─┬─┬─┬─┬─┬─┐  ┌────┬────┐  ┌─┬─┬─┬─┬─┬─┬─┬─┐
  │ 25%│ 25%│  │6│6│6│6│6│6│6│6│  │10%│10%│  │10 │10 │10 │10 │10│
  └────┴────┘  └─┴─┴─┴─┴─┴─┴─┴─┘  └────┴────┘  └──┴──┴──┴──┴──┘
  ↑ 各AZに50%ずつ              ↑ 全10台に均等（各10%）
    → AZ-aの2台に過負荷          → 均等に分散
```

### 各ロードバランサーのデフォルト設定

| ロードバランサー | クロスゾーンのデフォルト | 料金 |
|---------------|---------------------|------|
| ALB | 有効 | 無料 |
| NLB | 無効 | AZ間データ転送料金が発生 |

---

## 10. ALBのリスナールールとアクション

ALBのリスナールールは、受信リクエストに対する条件とアクションを定義します。

### アクションタイプ

| アクション | 説明 | ユースケース |
|-----------|------|-------------|
| forward | ターゲットグループへ転送 | 通常のルーティング |
| redirect | URLリダイレクト | HTTP→HTTPS、URL変更 |
| fixed-response | 固定レスポンスを返す | メンテナンスページ、ヘルスチェック |
| authenticate-oidc | OIDC認証 | 管理画面の認証 |
| authenticate-cognito | Cognito認証 | Cognito統合 |

### リスナールールの設定例

```bash
# HTTP→HTTPSリダイレクトルール
aws elbv2 create-rule \
  --listener-arn $HTTP_LISTENER_ARN \
  --conditions '[{"Field":"path-pattern","Values":["/*"]}]' \
  --actions '[{
    "Type": "redirect",
    "RedirectConfig": {
      "Protocol": "HTTPS",
      "Port": "443",
      "StatusCode": "HTTP_301"
    }
  }]' \
  --priority 1

# OIDCプロバイダーによる認証 + 転送
aws elbv2 create-rule \
  --listener-arn $HTTPS_LISTENER_ARN \
  --conditions '[{"Field":"path-pattern","Values":["/admin/*"]}]' \
  --actions '[
    {
      "Type": "authenticate-oidc",
      "Order": 1,
      "AuthenticateOidcConfig": {
        "Issuer": "https://idp.example.com",
        "AuthorizationEndpoint": "https://idp.example.com/authorize",
        "TokenEndpoint": "https://idp.example.com/token",
        "UserInfoEndpoint": "https://idp.example.com/userinfo",
        "ClientId": "admin-dashboard",
        "ClientSecret": "secret",
        "Scope": "openid profile",
        "OnUnauthenticatedRequest": "authenticate"
      }
    },
    {
      "Type": "forward",
      "Order": 2,
      "TargetGroupArn": "'$ADMIN_TG_ARN'"
    }
  ]' \
  --priority 10
```

---

## 11. IDサービスでの活用

idp-serverの前段にALBを配置し、HTTPS終端、ヘルスチェック、ルーティングを実現します。

### idp-server ALB構成図

```
┌─────────────────────────────────────────────────────────────┐
│                     AWS リージョン                            │
│                                                             │
│  ┌───────────────────────────────────────────────────────┐  │
│  │                    VPC                                │  │
│  │                                                       │  │
│  │  パブリックサブネット                                    │  │
│  │  ┌─────────────────────────────────────────────────┐  │  │
│  │  │              ALB (idp-alb)                      │  │  │
│  │  │                                                 │  │  │
│  │  │  HTTPS:443 リスナー                              │  │  │
│  │  │  ┌─────────────────────────────────────────┐    │  │  │
│  │  │  │ ルール1: /.well-known/*                  │    │  │  │
│  │  │  │   → idp-discovery-tg                    │    │  │  │
│  │  │  │                                         │    │  │  │
│  │  │  │ ルール2: /admin/* + authenticate-oidc   │    │  │  │
│  │  │  │   → admin-dashboard-tg                  │    │  │  │
│  │  │  │                                         │    │  │  │
│  │  │  │ ルール3: /api/v1/control-plane/*        │    │  │  │
│  │  │  │   → idp-control-plane-tg               │    │  │  │
│  │  │  │                                         │    │  │  │
│  │  │  │ デフォルト: /*                            │    │  │  │
│  │  │  │   → idp-server-tg                       │    │  │  │
│  │  │  └─────────────────────────────────────────┘    │  │  │
│  │  │                                                 │  │  │
│  │  │  HTTP:80 リスナー                               │  │  │
│  │  │  └── 全リクエスト → HTTPS:443 リダイレクト        │  │  │
│  │  └─────────────────────────────────────────────────┘  │  │
│  │                                                       │  │
│  │  プライベートサブネット                                  │  │
│  │  ┌──────────────────────┐ ┌──────────────────────┐   │  │
│  │  │ AZ-a                 │ │ AZ-c                 │   │  │
│  │  │ ┌──────────────────┐ │ │ ┌──────────────────┐ │   │  │
│  │  │ │  idp-server-1    │ │ │ │  idp-server-2    │ │   │  │
│  │  │ │  (ECS Task)      │ │ │ │  (ECS Task)      │ │   │  │
│  │  │ │  Port: 8443      │ │ │ │  Port: 8443      │ │   │  │
│  │  │ └──────────────────┘ │ │ └──────────────────┘ │   │  │
│  │  └──────────────────────┘ └──────────────────────┘   │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### ALB設定のポイント

| 項目 | 推奨設定 | 理由 |
|------|---------|------|
| SSL/TLS終端 | ALBで終端 | 証明書管理の一元化 |
| セキュリティポリシー | TLS 1.3+1.2 | FAPI準拠の暗号強度 |
| ヘルスチェックパス | /health | アプリ固有のヘルスチェック |
| ヘルスチェック間隔 | 15秒 | 障害の早期検出 |
| HTTP→HTTPS | リダイレクト必須 | セキュリティ要件 |
| アイドルタイムアウト | 60秒 | 認可フロー対応 |
| WAF統合 | AWS WAF連携 | L7保護（レート制限、SQLi防止等） |

### ヘルスチェック設定

```bash
# idp-server用ターゲットグループのヘルスチェック設定
aws elbv2 modify-target-group \
  --target-group-arn $TG_ARN \
  --health-check-protocol HTTPS \
  --health-check-path /health \
  --health-check-interval-seconds 15 \
  --health-check-timeout-seconds 5 \
  --healthy-threshold-count 2 \
  --unhealthy-threshold-count 3 \
  --matcher '{"HttpCode": "200"}'
```

### ALBアクセスログの活用

ALBのアクセスログをS3に保存し、認証リクエストの監査に活用できます。

```bash
# ALBアクセスログの有効化
aws elbv2 modify-load-balancer-attributes \
  --load-balancer-arn $ALB_ARN \
  --attributes \
    Key=access_logs.s3.enabled,Value=true \
    Key=access_logs.s3.bucket,Value=idp-alb-access-logs \
    Key=access_logs.s3.prefix,Value=idp-alb
```

---

## 12. まとめ

| 項目 | ALB | NLB |
|------|-----|-----|
| レイヤー | L7 | L4 |
| 主な用途 | Webアプリ、API | 高スループット |
| idp-server | 推奨（前段LB） | 特殊要件時のみ |

### 重要なポイント

- ALBはHTTP/HTTPSトラフィックに最適化されたL7ロードバランサーで、idp-serverの前段に推奨される
- パスベース/ホストベースルーティングにより、DiscoveryエンドポイントやControl Planeを適切に振り分ける
- ACM証明書によるSSL/TLS終端で、証明書管理を簡素化し更新を自動化する
- ヘルスチェックにより障害を自動検知し、正常なインスタンスにのみトラフィックを振り分ける
- ALBのOIDC認証統合を活用して、管理画面へのアクセスをidp-server自身で認証できる
- アクセスログをS3に保存し、セキュリティ監査に活用する

## 次のステップ

- [Route 53・CloudFront](./aws-route53-cloudfront.md): DNS管理とCDNによるグローバル配信

## 参考リソース

- [Elastic Load Balancing ドキュメント](https://docs.aws.amazon.com/ja_jp/elasticloadbalancing/latest/userguide/)
- [ALB ドキュメント](https://docs.aws.amazon.com/ja_jp/elasticloadbalancing/latest/application/)
- [NLB ドキュメント](https://docs.aws.amazon.com/ja_jp/elasticloadbalancing/latest/network/)
- [AWS Certificate Manager ドキュメント](https://docs.aws.amazon.com/ja_jp/acm/latest/userguide/)
