# ロードバランシング

## 所要時間
約45分

## 学べること
- ロードバランシングの基本概念とアルゴリズム
- L4（Transport Layer）とL7（Application Layer）ロードバランサーの違い
- AWS ELB（ALB、NLB、CLB）の実践的な使い方
- ヘルスチェックとセッション維持
- ロードバランサーとDNSの連携
- コンテナ環境でのロードバランシング

## 前提知識
- [01-dns-fundamentals.md](./01-dns-fundamentals.md) の内容
- TCP/IPの基礎知識
- HTTP/HTTPSの基本

---

## 1. ロードバランシングの基礎

### 1.1 ロードバランシングとは

**ロードバランシング**は、複数のサーバーに対してトラフィックを分散させる技術です。

```
┌─────────────────────────────────────────────────────────────┐
│              ロードバランサーなし vs あり                    │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  【ロードバランサーなし】                                    │
│  クライアント                                                │
│      │                                                      │
│      └───────► サーバー1（100%の負荷）                      │
│                                                              │
│  問題点:                                                     │
│  - 単一障害点（SPOF）                                        │
│  - スケーラビリティの限界                                    │
│  - メンテナンス時のダウンタイム                              │
│                                                              │
│  【ロードバランサーあり】                                    │
│  クライアント                                                │
│      │                                                      │
│      ▼                                                      │
│  ┌────────────────┐                                         │
│  │ ロードバランサー │                                         │
│  └────────────────┘                                         │
│      │                                                      │
│      ├───────► サーバー1（33%の負荷）                       │
│      ├───────► サーバー2（33%の負荷）                       │
│      └───────► サーバー3（33%の負荷）                       │
│                                                              │
│  利点:                                                       │
│  - 高可用性（1台故障しても継続）                             │
│  - 水平スケーリング（サーバー追加で対応）                    │
│  - ゼロダウンタイムデプロイ                                  │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 ロードバランシングの目的

```
1. 高可用性（High Availability）
   - サーバー障害時の自動フェイルオーバー
   - サービスの継続性確保

2. スケーラビリティ
   - 水平スケーリング（サーバー台数追加）
   - トラフィック増加への対応

3. パフォーマンス
   - 負荷分散による応答速度向上
   - リソースの効率的利用

4. メンテナンス性
   - ローリングアップデート
   - ゼロダウンタイムデプロイ
```

---

## 2. ロードバランシングアルゴリズム

### 2.1 主要なアルゴリズム

#### ラウンドロビン（Round Robin）

最もシンプルなアルゴリズム。順番にリクエストを振り分けます。

```
┌─────────────────────────────────────────────────────────────┐
│                  ラウンドロビン                              │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  リクエスト1 → サーバー1                                     │
│  リクエスト2 → サーバー2                                     │
│  リクエスト3 → サーバー3                                     │
│  リクエスト4 → サーバー1  ← 最初に戻る                       │
│  リクエスト5 → サーバー2                                     │
│  ...                                                         │
│                                                              │
│  利点:                                                       │
│  - 実装がシンプル                                            │
│  - 均等に分散                                                │
│                                                              │
│  欠点:                                                       │
│  - サーバーの性能差を考慮しない                              │
│  - リクエストの重さを考慮しない                              │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

#### 重み付けラウンドロビン（Weighted Round Robin）

サーバーの性能に応じて重みを設定します。

```
サーバー1: 重み 3（性能高）
サーバー2: 重み 2（性能中）
サーバー3: 重み 1（性能低）

リクエスト1 → サーバー1
リクエスト2 → サーバー1
リクエスト3 → サーバー1
リクエスト4 → サーバー2
リクエスト5 → サーバー2
リクエスト6 → サーバー3
リクエスト7 → サーバー1  ← 最初に戻る
```

#### 最小接続数（Least Connections）

現在の接続数が最も少ないサーバーにリクエストを振り分けます。

```
┌─────────────────────────────────────────────────────────────┐
│                  最小接続数                                  │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  現在の状態:                                                 │
│  サーバー1: 5接続                                            │
│  サーバー2: 3接続  ← 最小                                    │
│  サーバー3: 7接続                                            │
│                                                              │
│  新しいリクエスト → サーバー2に振り分け                      │
│                                                              │
│  利点:                                                       │
│  - 長時間接続に適している（WebSocket等）                     │
│  - 動的に負荷を均等化                                        │
│                                                              │
│  欠点:                                                       │
│  - 接続数の追跡が必要（オーバーヘッド）                      │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

#### IPハッシュ（IP Hash / Source IP Affinity）

クライアントのIPアドレスに基づいてサーバーを決定します。

```
hash(クライアントIP) % サーバー数 = サーバーインデックス

例:
hash(192.168.1.10) % 3 = 1 → サーバー2
hash(192.168.1.20) % 3 = 0 → サーバー1
hash(192.168.1.30) % 3 = 2 → サーバー3

利点:
- 同一クライアントは常に同一サーバーに接続
- セッション維持（Session Affinity / Sticky Session）

欠点:
- サーバー数変更時に再分散が発生
- 負荷が偏る可能性
```

#### 最小レスポンスタイム（Least Response Time）

応答時間が最も短いサーバーにリクエストを振り分けます。

```
サーバー1: 平均応答時間 50ms
サーバー2: 平均応答時間 30ms  ← 最速
サーバー3: 平均応答時間 80ms

新しいリクエスト → サーバー2に振り分け

利点:
- パフォーマンス最適化
- 動的に最速サーバーを選択

欠点:
- 応答時間の計測が必要（複雑）
```

---

## 3. L4 vs L7 ロードバランサー

### 3.1 OSI参照モデルとロードバランサー

```
┌─────────────────────────────────────────────────────────────┐
│              L4 vs L7 ロードバランサー                       │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  OSI参照モデル                                               │
│  ┌─────────────────────┐                                    │
│  │ 7. アプリケーション  │ ← L7ロードバランサー              │
│  ├─────────────────────┤    (HTTP/HTTPS、URLパス、ヘッダー) │
│  │ 6. プレゼンテーション│                                    │
│  ├─────────────────────┤                                    │
│  │ 5. セッション        │                                    │
│  ├─────────────────────┤                                    │
│  │ 4. トランスポート    │ ← L4ロードバランサー              │
│  ├─────────────────────┤    (TCP/UDP、IPアドレス、ポート)   │
│  │ 3. ネットワーク      │                                    │
│  ├─────────────────────┤                                    │
│  │ 2. データリンク      │                                    │
│  ├─────────────────────┤                                    │
│  │ 1. 物理              │                                    │
│  └─────────────────────┘                                    │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 L4ロードバランサー（Transport Layer）

**L4ロードバランサー**は、IPアドレスとポート番号に基づいてトラフィックを分散します。

```
┌─────────────────────────────────────────────────────────────┐
│                  L4ロードバランサーの動作                    │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  クライアント (192.168.1.10:54321)                           │
│      │                                                      │
│      │ TCP SYN → lb.example.com:443                         │
│      ▼                                                      │
│  ┌──────────────────────┐                                   │
│  │  L4ロードバランサー   │                                   │
│  │  (lb.example.com)    │                                   │
│  └──────────────────────┘                                   │
│      │                                                      │
│      │ 判断基準:                                             │
│      │ - 送信元IP: 192.168.1.10                             │
│      │ - 送信元ポート: 54321                                 │
│      │ - 宛先IP: lb.example.com                             │
│      │ - 宛先ポート: 443                                     │
│      │ - プロトコル: TCP                                     │
│      │                                                      │
│      ├───► Server1 (10.0.1.10:443)                          │
│      ├───► Server2 (10.0.1.11:443)                          │
│      └───► Server3 (10.0.1.12:443)                          │
│                                                              │
│  ※ HTTPヘッダーやペイロードは見ない                          │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

**L4の特徴**:

```
利点:
- 高速（パケット検査が少ない）
- 低レイテンシー
- あらゆるTCP/UDPトラフィックに対応
- SSL終端不要（パススルー可能）

欠点:
- URLパスベースのルーティング不可
- HTTP/HTTPSヘッダーの利用不可
- コンテンツベースのルーティング不可

用途:
- 非HTTPトラフィック（DB、SMTP、DNS等）
- 超高速処理が必要な場合
- SSL/TLSをバックエンドで処理したい場合
```

### 3.3 L7ロードバランサー（Application Layer）

**L7ロードバランサー**は、HTTPリクエストの内容に基づいてトラフィックを分散します。

```
┌─────────────────────────────────────────────────────────────┐
│                  L7ロードバランサーの動作                    │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  クライアント                                                │
│      │                                                      │
│      │ GET /api/users HTTP/1.1                              │
│      │ Host: example.com                                    │
│      │ User-Agent: Mobile                                   │
│      ▼                                                      │
│  ┌──────────────────────┐                                   │
│  │  L7ロードバランサー   │                                   │
│  │  (ALB)               │                                   │
│  └──────────────────────┘                                   │
│      │                                                      │
│      │ 判断基準:                                             │
│      │ - URLパス: /api/users                                │
│      │ - ホストヘッダー: example.com                         │
│      │ - HTTPメソッド: GET                                  │
│      │ - User-Agentヘッダー: Mobile                         │
│      │ - Cookieの有無                                       │
│      │                                                      │
│      ├───► /api/*    → API Server (10.0.1.10)              │
│      ├───► /static/* → Static Server (10.0.1.20)           │
│      └───► /*        → App Server (10.0.1.30)              │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

**L7の特徴**:

```
利点:
- URLパスベースのルーティング
- ホストヘッダーベースのルーティング
- HTTPヘッダーの読み書き
- WebSocketサポート
- SSL/TLS終端（証明書管理の一元化）
- コンテンツベースのルーティング

欠点:
- L4より低速（HTTP解析が必要）
- HTTP/HTTPS専用

用途:
- Webアプリケーション
- マイクロサービス（パスベースルーティング）
- コンテナ環境（複数サービスの統合）
```

---

## 4. AWS Elastic Load Balancing (ELB)

### 4.1 ELBの種類

AWSは3種類のロードバランサーを提供しています。

```
┌─────────────────────────────────────────────────────────────┐
│                  AWS ELB の種類                              │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  1. ALB (Application Load Balancer)                         │
│     - L7ロードバランサー                                     │
│     - HTTP/HTTPS専用                                        │
│     - パスベースルーティング                                 │
│     - 最新、最も機能豊富                                     │
│                                                              │
│  2. NLB (Network Load Balancer)                             │
│     - L4ロードバランサー                                     │
│     - TCP/UDP/TLS                                           │
│     - 超高速（数百万リクエスト/秒）                          │
│     - 固定IPアドレス対応                                     │
│                                                              │
│  3. CLB (Classic Load Balancer)                             │
│     - 旧世代（非推奨）                                       │
│     - L4/L7ハイブリッド                                      │
│     - 新規作成は推奨されない                                 │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 4.2 ALB（Application Load Balancer）

#### ALBの主要機能

```
1. パスベースルーティング
   /api/*     → API Target Group
   /images/*  → Static Target Group
   /*         → Default Target Group

2. ホストベースルーティング
   api.example.com     → API Target Group
   www.example.com     → Web Target Group

3. HTTPヘッダーベースルーティング
   User-Agent: Mobile  → Mobile Target Group
   User-Agent: Desktop → Desktop Target Group

4. HTTP/2、WebSocket、gRPC サポート

5. SSL/TLS終端
   - ACM統合（無料証明書）
   - SNI（複数証明書）サポート

6. Lambda統合
   - サーバーレスアプリケーションへのルーティング
```

#### ALB作成例（AWS CLI）

```bash
# 1. ターゲットグループ作成
aws elbv2 create-target-group \
  --name api-targets \
  --protocol HTTP \
  --port 8080 \
  --vpc-id vpc-1234567890abcdef0 \
  --health-check-path /health \
  --health-check-interval-seconds 30 \
  --health-check-timeout-seconds 5 \
  --healthy-threshold-count 2 \
  --unhealthy-threshold-count 3

# 2. ALB作成
aws elbv2 create-load-balancer \
  --name my-alb \
  --subnets subnet-12345678 subnet-87654321 \
  --security-groups sg-12345678 \
  --scheme internet-facing \
  --type application \
  --ip-address-type ipv4

# 3. リスナー作成（HTTPSリスナー）
aws elbv2 create-listener \
  --load-balancer-arn arn:aws:elasticloadbalancing:us-east-1:123456789012:loadbalancer/app/my-alb/50dc6c495c0c9188 \
  --protocol HTTPS \
  --port 443 \
  --certificates CertificateArn=arn:aws:acm:us-east-1:123456789012:certificate/12345678-1234-1234-1234-123456789012 \
  --default-actions Type=forward,TargetGroupArn=arn:aws:elasticloadbalancing:us-east-1:123456789012:targetgroup/api-targets/50dc6c495c0c9188

# 4. パスベースルーティングルール追加
aws elbv2 create-rule \
  --listener-arn arn:aws:elasticloadbalancing:us-east-1:123456789012:listener/app/my-alb/50dc6c495c0c9188/0123456789abcdef \
  --priority 10 \
  --conditions Field=path-pattern,Values='/api/*' \
  --actions Type=forward,TargetGroupArn=arn:aws:elasticloadbalancing:us-east-1:123456789012:targetgroup/api-targets/50dc6c495c0c9188

# 5. ターゲット登録
aws elbv2 register-targets \
  --target-group-arn arn:aws:elasticloadbalancing:us-east-1:123456789012:targetgroup/api-targets/50dc6c495c0c9188 \
  --targets Id=i-1234567890abcdef0 Id=i-0987654321fedcba0
```

#### ALBルーティングルール例

```
┌─────────────────────────────────────────────────────────────┐
│                  ALB ルーティング例                          │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  リスナー: HTTPS:443                                         │
│                                                              │
│  ルール1 (優先度: 10)                                        │
│    条件: Path = /api/*                                       │
│    アクション: api-target-group に転送                       │
│                                                              │
│  ルール2 (優先度: 20)                                        │
│    条件: Path = /static/*                                    │
│    アクション: s3-redirect (リダイレクト)                    │
│                                                              │
│  ルール3 (優先度: 30)                                        │
│    条件: Header[User-Agent] contains "Mobile"                │
│    アクション: mobile-target-group に転送                    │
│                                                              │
│  デフォルトルール                                            │
│    条件: なし（全てマッチ）                                  │
│    アクション: default-target-group に転送                   │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 4.3 NLB（Network Load Balancer）

#### NLBの主要機能

```
1. 超高パフォーマンス
   - 数百万リクエスト/秒
   - 超低レイテンシー

2. 固定IPアドレス
   - Elastic IP 割り当て可能
   - ファイアウォールホワイトリスト対応

3. プロトコルサポート
   - TCP、UDP、TLS

4. クロスゾーン負荷分散

5. ターゲットタイプ
   - EC2インスタンス
   - IPアドレス（オンプレミス含む）
   - ALB（NLB → ALB チェーン）
```

#### NLB作成例

```bash
# 1. ターゲットグループ作成（TCP）
aws elbv2 create-target-group \
  --name db-targets \
  --protocol TCP \
  --port 5432 \
  --vpc-id vpc-1234567890abcdef0 \
  --health-check-protocol TCP \
  --health-check-port 5432

# 2. NLB作成
aws elbv2 create-load-balancer \
  --name my-nlb \
  --subnets subnet-12345678 subnet-87654321 \
  --type network \
  --scheme internet-facing

# 3. リスナー作成（TCPリスナー）
aws elbv2 create-listener \
  --load-balancer-arn arn:aws:elasticloadbalancing:us-east-1:123456789012:loadbalancer/net/my-nlb/50dc6c495c0c9188 \
  --protocol TCP \
  --port 5432 \
  --default-actions Type=forward,TargetGroupArn=arn:aws:elasticloadbalancing:us-east-1:123456789012:targetgroup/db-targets/50dc6c495c0c9188
```

#### NLBの典型的なユースケース

```
1. データベース接続
   - PostgreSQL、MySQL、MongoDB等
   - 固定IPで接続元制限

2. 非HTTPプロトコル
   - SMTP、DNS、カスタムTCPプロトコル

3. 超高速処理が必要な場合
   - ゲームサーバー
   - IoTデータ収集

4. ハイブリッドクラウド
   - オンプレミスサーバーとの統合
   - IPアドレスターゲット使用
```

---

## 5. ヘルスチェック

### 5.1 ヘルスチェックの仕組み

```
┌─────────────────────────────────────────────────────────────┐
│                  ヘルスチェックの動作                        │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ロードバランサー                                            │
│      │                                                      │
│      ├───► Server1: GET /health → 200 OK  ✅ 正常           │
│      │                                                      │
│      ├───► Server2: GET /health → 200 OK  ✅ 正常           │
│      │                                                      │
│      └───► Server3: GET /health → 500 Error ❌ 異常         │
│                          ↓                                  │
│                    トラフィック停止                          │
│                          ↓                                  │
│              連続2回正常になるまで待機                        │
│                          ↓                                  │
│                    トラフィック再開                          │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 5.2 ヘルスチェック設定パラメータ

```
パラメータ               デフォルト  説明
────────────────────────────────────────────────────────
Protocol                HTTP        ヘルスチェックプロトコル
Port                    Traffic     ヘルスチェックポート
Path                    /           ヘルスチェックパス
Interval                30秒        チェック間隔
Timeout                 5秒         タイムアウト
Healthy Threshold       2回         正常判定までの成功回数
Unhealthy Threshold     3回         異常判定までの失敗回数
Success Codes           200         正常とみなすHTTPステータス
```

### 5.3 ヘルスチェックエンドポイントの実装

```java
// Spring Boot でのヘルスチェックエンドポイント実装例

@RestController
public class HealthCheckController {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    // シンプルなヘルスチェック
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }

    // 詳細なヘルスチェック
    @GetMapping("/health/detailed")
    public ResponseEntity<Map<String, Object>> detailedHealth() {
        Map<String, Object> health = new HashMap<>();

        try {
            // データベース接続チェック
            try (Connection conn = dataSource.getConnection()) {
                conn.createStatement().execute("SELECT 1");
                health.put("database", "UP");
            }
        } catch (Exception e) {
            health.put("database", "DOWN");
            health.put("database_error", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(health);
        }

        try {
            // Redis接続チェック
            redisTemplate.opsForValue().get("health_check");
            health.put("redis", "UP");
        } catch (Exception e) {
            health.put("redis", "DOWN");
            health.put("redis_error", e.getMessage());
            // Redisは必須でない場合、継続
        }

        health.put("status", "UP");
        return ResponseEntity.ok(health);
    }

    // 起動準備完了チェック（Readiness Probe）
    @GetMapping("/ready")
    public ResponseEntity<String> ready() {
        // アプリケーション初期化完了チェック
        if (!applicationInitialized) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("NOT READY");
        }
        return ResponseEntity.ok("READY");
    }

    // 生存確認（Liveness Probe）
    @GetMapping("/alive")
    public ResponseEntity<String> alive() {
        // 単純な応答（プロセスが生きているか）
        return ResponseEntity.ok("ALIVE");
    }
}
```

### 5.4 ヘルスチェックのベストプラクティス

```
1. 軽量なエンドポイント
   - 重い処理は避ける
   - 数秒以内に応答

2. 依存サービスのチェック
   - データベース接続
   - 外部API接続
   - キャッシュサーバー接続

3. エンドポイントの使い分け
   /health  : 基本的な生存確認
   /ready   : トラフィック受付準備完了
   /alive   : プロセス生存確認

4. ログ出力の抑制
   - ヘルスチェックのログは大量になる
   - 必要に応じてフィルタリング

5. 適切なタイムアウト設定
   - アプリケーションの起動時間を考慮
   - 初期遅延（Initial Delay）の設定
```

---

## 6. セッション維持（Sticky Session / Session Affinity）

### 6.1 セッション維持の必要性

```
┌─────────────────────────────────────────────────────────────┐
│              セッション維持なし vs あり                      │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  【セッション維持なし】                                      │
│  ユーザーA                                                   │
│    ├─ リクエスト1 → Server1 (セッション作成)                │
│    └─ リクエスト2 → Server2 (セッション不明 ❌)              │
│                                                              │
│  問題: ログイン状態が失われる                                │
│                                                              │
│  【セッション維持あり】                                      │
│  ユーザーA                                                   │
│    ├─ リクエスト1 → Server1 (セッション作成)                │
│    └─ リクエスト2 → Server1 (セッション維持 ✅)              │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 6.2 セッション維持の方式

#### Cookieベース（ALBのデフォルト）

```
┌─────────────────────────────────────────────────────────────┐
│              ALB Cookie ベースセッション維持                 │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  1. 初回リクエスト                                           │
│     クライアント → ALB → Server1                             │
│                                                              │
│  2. ALBがCookieを追加                                        │
│     ALB → クライアント                                       │
│     Set-Cookie: AWSALB=...; Path=/; Expires=...             │
│                                                              │
│  3. 2回目以降のリクエスト                                    │
│     クライアント → ALB (Cookieを送信)                        │
│     Cookie: AWSALB=...                                      │
│     ↓                                                       │
│     ALB がCookieを読み取り、Server1に転送                   │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

#### IPアドレスベース（NLB）

```
クライアントIPアドレスに基づいて同じサーバーに転送

hash(クライアントIP) % サーバー数 = サーバーインデックス

利点: Cookieなしでセッション維持
欠点: NATやプロキシ経由で同一IPに見える場合、偏りが発生
```

### 6.3 ALBでのSticky Session設定

```bash
# ターゲットグループでSticky Session有効化
aws elbv2 modify-target-group-attributes \
  --target-group-arn arn:aws:elasticloadbalancing:us-east-1:123456789012:targetgroup/my-targets/50dc6c495c0c9188 \
  --attributes \
    Key=stickiness.enabled,Value=true \
    Key=stickiness.type,Value=lb_cookie \
    Key=stickiness.lb_cookie.duration_seconds,Value=86400

# アプリケーションCookieベースのSticky Session
aws elbv2 modify-target-group-attributes \
  --target-group-arn arn:aws:elasticloadbalancing:us-east-1:123456789012:targetgroup/my-targets/50dc6c495c0c9188 \
  --attributes \
    Key=stickiness.enabled,Value=true \
    Key=stickiness.type,Value=app_cookie \
    Key=stickiness.app_cookie.cookie_name,Value=JSESSIONID \
    Key=stickiness.app_cookie.duration_seconds,Value=86400
```

### 6.4 ステートレス設計（推奨）

セッション維持に依存しない設計が推奨されます。

```java
// アンチパターン: サーバー側セッション
// HttpSession を使用（メモリに保存）
HttpSession session = request.getSession();
session.setAttribute("user", user);

// 推奨パターン1: JWTトークン
// セッション情報をトークンに含める（ステートレス）
String token = Jwts.builder()
    .setSubject(user.getId())
    .claim("email", user.getEmail())
    .setExpiration(new Date(System.currentTimeMillis() + 3600000))
    .signWith(SignatureAlgorithm.HS256, secretKey)
    .compact();

// 推奨パターン2: 外部セッションストア
// Redis、DynamoDB等にセッション保存
@Bean
public HttpSessionIdResolver httpSessionIdResolver() {
    return HeaderHttpSessionIdResolver.xAuthToken();
}

@Bean
public RedisTemplate<String, Object> sessionRedisTemplate() {
    // Spring Session with Redis
}
```

---

## 7. コンテナ環境でのロードバランシング

### 7.1 ECS + ALB

```
┌─────────────────────────────────────────────────────────────┐
│              ECS + ALB の統合                                │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ALB                                                         │
│   ├─ Listener (HTTPS:443)                                   │
│   │   ├─ Rule: /api/*  → api-target-group                   │
│   │   └─ Rule: /web/*  → web-target-group                   │
│   │                                                          │
│  ECS Cluster                                                 │
│   ├─ Service: api-service                                    │
│   │   ├─ Task1 (動的ポート: 32768) ← api-target-group       │
│   │   ├─ Task2 (動的ポート: 32769) ← api-target-group       │
│   │   └─ Task3 (動的ポート: 32770) ← api-target-group       │
│   │                                                          │
│   └─ Service: web-service                                    │
│       ├─ Task1 (動的ポート: 32771) ← web-target-group       │
│       └─ Task2 (動的ポート: 32772) ← web-target-group       │
│                                                              │
│  ※ ECSが自動的にターゲットグループにタスクを登録/解除       │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

#### ECS Service with ALB設定例

```yaml
# ECS Task Definition
{
  "family": "api-task",
  "containerDefinitions": [
    {
      "name": "api-container",
      "image": "123456789012.dkr.ecr.us-east-1.amazonaws.com/api:latest",
      "portMappings": [
        {
          "containerPort": 8080,
          "protocol": "tcp"
        }
      ],
      "healthCheck": {
        "command": ["CMD-SHELL", "curl -f http://localhost:8080/health || exit 1"],
        "interval": 30,
        "timeout": 5,
        "retries": 3,
        "startPeriod": 60
      }
    }
  ]
}
```

```bash
# ECS Service作成（ALB統合）
aws ecs create-service \
  --cluster my-cluster \
  --service-name api-service \
  --task-definition api-task:1 \
  --desired-count 3 \
  --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[subnet-12345678,subnet-87654321],securityGroups=[sg-12345678],assignPublicIp=ENABLED}" \
  --load-balancers \
    targetGroupArn=arn:aws:elasticloadbalancing:us-east-1:123456789012:targetgroup/api-targets/50dc6c495c0c9188,\
    containerName=api-container,\
    containerPort=8080
```

### 7.2 Kubernetes Service（LoadBalancer type）

```yaml
# Kubernetes Service - LoadBalancer type
apiVersion: v1
kind: Service
metadata:
  name: api-service
  annotations:
    # AWS Load Balancer Controller annotations
    service.beta.kubernetes.io/aws-load-balancer-type: "nlb"  # NLB使用
    service.beta.kubernetes.io/aws-load-balancer-scheme: "internet-facing"
    service.beta.kubernetes.io/aws-load-balancer-cross-zone-load-balancing-enabled: "true"
spec:
  type: LoadBalancer
  selector:
    app: api
  ports:
    - port: 80
      targetPort: 8080
      protocol: TCP
---
# ALB使用（Ingress経由）
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: api-ingress
  annotations:
    kubernetes.io/ingress.class: alb
    alb.ingress.kubernetes.io/scheme: internet-facing
    alb.ingress.kubernetes.io/target-type: ip
    alb.ingress.kubernetes.io/healthcheck-path: /health
    alb.ingress.kubernetes.io/listen-ports: '[{"HTTP": 80}, {"HTTPS": 443}]'
    alb.ingress.kubernetes.io/certificate-arn: arn:aws:acm:us-east-1:123456789012:certificate/...
spec:
  rules:
    - host: api.example.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: api-service
                port:
                  number: 80
```

---

## まとめ

### 学んだこと

本章では、ロードバランシングの実践的な知識を学びました:

- ロードバランシングの基本概念とアルゴリズム（ラウンドロビン、最小接続数、IPハッシュ等）
- L4とL7ロードバランサーの違いと使い分け
- AWS ELB（ALB、NLB）の機能と実装
- ヘルスチェックの仕組みとベストプラクティス
- セッション維持とステートレス設計
- コンテナ環境（ECS、Kubernetes）でのロードバランシング

### 重要なポイント

```
1. L4 vs L7 の使い分け
   - HTTP/HTTPS → ALB（L7）
   - TCP/UDP、非HTTP → NLB（L4）
   - 超高速処理 → NLB

2. ヘルスチェックの設計
   - 軽量なエンドポイント
   - 依存サービスのチェック
   - 適切なタイムアウト設定

3. ステートレス設計
   - セッション維持に依存しない
   - JWTトークン使用
   - 外部セッションストア（Redis等）

4. パスベースルーティングの活用
   - マイクロサービス統合
   - コスト削減（1つのALBで複数サービス）
```

### 次のステップ

- [04-ssl-tls-certificates.md](./04-ssl-tls-certificates.md) - SSL/TLS証明書とACM
- [05-api-gateway-networking.md](./05-api-gateway-networking.md) - API Gatewayとロードバランサー
- [06-network-troubleshooting.md](./06-network-troubleshooting.md) - ロードバランサーのトラブルシューティング

### 参考リンク

- [AWS ELB Documentation](https://docs.aws.amazon.com/elasticloadbalancing/)
- [ALB User Guide](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/)
- [NLB User Guide](https://docs.aws.amazon.com/elasticloadbalancing/latest/network/)
- [AWS Load Balancer Controller](https://kubernetes-sigs.github.io/aws-load-balancer-controller/)
