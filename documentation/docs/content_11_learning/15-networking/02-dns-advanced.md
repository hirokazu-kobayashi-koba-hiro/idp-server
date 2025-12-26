# DNS詳細: CNAME、DNSSECとクラウドDNS

## 所要時間
約50分

## 学べること
- CNAMEレコードの詳細仕様と制約
- CNAMEに関連する実践的な問題と対策（AWS Advanced JDBC Wrapper含む）
- DNSSEC（DNS Security Extensions）の基礎
- AWS Route 53の実践的な使い方
- クラウド環境でのDNSベストプラクティス
- プライベートDNSとサービスディスカバリー

## 前提知識
- [01-dns-fundamentals.md](./01-dns-fundamentals.md) の内容
- 基本的なクラウドサービスの理解
- データベース接続の基礎知識

---

## 1. CNAMEレコードの詳細

### 1.1 CNAMEの基本仕様

**CNAME（Canonical Name）レコード**は、一般的なDNS仕様として RFC 1034/1035 で定義されています。AWSやクラウド固有の機能ではなく、**標準的なDNSレコードタイプ**です。

```
┌─────────────────────────────────────────────────────────────┐
│                  CNAME の基本動作                            │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  【設定例】                                                  │
│  www.example.com.    IN  CNAME  example.com.                │
│  example.com.        IN  A      93.184.216.34               │
│                                                              │
│  【名前解決の流れ】                                          │
│  クライアント: www.example.com のIPは?                       │
│      │                                                      │
│      ├─► (1) www.example.com を問い合わせ                   │
│      │   応答: CNAME → example.com                          │
│      │                                                      │
│      ├─► (2) example.com を問い合わせ                       │
│      │   応答: A → 93.184.216.34                            │
│      │                                                      │
│      └─► 最終結果: 93.184.216.34                            │
│                                                              │
│  ※ 通常、DNSリゾルバが自動的に(2)まで実行してくれる          │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 CNAMEの重要な制約

CNAMEには、RFC 1034 で定義された**重要な制約**があります。

#### 制約1: 他のレコードとの共存禁止

```
┌─────────────────────────────────────────────────────────────┐
│            CNAME と他のレコードの共存禁止                    │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ❌ 禁止例1: CNAMEとAレコードの共存                          │
│  www.example.com.    IN  CNAME  example.com.                │
│  www.example.com.    IN  A      93.184.216.34               │
│  → 設定できない（DNSサーバーがエラー）                       │
│                                                              │
│  ❌ 禁止例2: CNAMEとMXレコードの共存                         │
│  example.com.        IN  CNAME  other.com.                  │
│  example.com.        IN  MX     10 mail.example.com.        │
│  → 設定できない                                              │
│                                                              │
│  ❌ 禁止例3: ゾーンAPEXにCNAME（後述）                       │
│  example.com.        IN  CNAME  other.com.                  │
│  → 技術的に問題あり（NS, SOAレコードと競合）                 │
│                                                              │
│  ✅ 正しい例: サブドメインにCNAME                            │
│  www.example.com.    IN  CNAME  example.com.                │
│  example.com.        IN  A      93.184.216.34               │
│  example.com.        IN  MX     10 mail.example.com.        │
│  → サブドメイン(www)にCNAME、APEXに他のレコード              │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

#### 制約2: CNAMEチェーンの問題

CNAMEが別のCNAMEを指すことは可能ですが、推奨されません。

```bash
# CNAMEチェーンの例
www.example.com.     IN  CNAME  www-prod.example.com.
www-prod.example.com. IN CNAME  lb-12345.us-east-1.elb.amazonaws.com.
lb-12345.us-east-1.elb.amazonaws.com. IN A 52.1.2.3

# 問題点:
# 1. DNS問い合わせが複数回発生（パフォーマンス低下）
# 2. TTL管理が複雑になる
# 3. デバッグが困難
# 4. RFC 1034では推奨されていない
```

### 1.3 ゾーンAPEXとCNAMEの問題

**ゾーンAPEX**（zone apex / naked domain / root domain）は、サブドメインを含まないドメイン名（例：example.com）のことです。

```
┌─────────────────────────────────────────────────────────────┐
│              ゾーンAPEXにCNAMEを設定できない理由              │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  【問題となるシナリオ】                                      │
│  example.com.    IN  CNAME  other.com.                      │
│  example.com.    IN  NS     ns1.example.com.  ← 必須         │
│  example.com.    IN  SOA    ...               ← 必須         │
│                                                              │
│  → CNAMEが存在する名前には、他のレコードを共存させられない   │
│  → しかしNS/SOAレコードはゾーンAPEXに必須                    │
│  → 矛盾が発生！                                              │
│                                                              │
│  【よくあるニーズ】                                          │
│  example.com にアクセスしたユーザーを                        │
│  ロードバランサー（lb.cloudprovider.com）に向けたい          │
│                                                              │
│  ❌ 解決策1: CNAMEを使う（RFC違反、動作不定）                │
│  example.com.    IN  CNAME  lb.cloudprovider.com.           │
│                                                              │
│  ✅ 解決策2: Aレコードを使う（推奨）                         │
│  example.com.    IN  A      52.1.2.3                        │
│                                                              │
│  ✅ 解決策3: ALIASレコード（Route 53等の拡張機能）           │
│  example.com.    IN  ALIAS  lb.cloudprovider.com.           │
│  → 次節で詳述                                                │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 1.4 CNAMEの実践的な使い方

```bash
# ユースケース1: CDNへのマッピング
www.example.com.     IN  CNAME  d111111abcdef8.cloudfront.net.

# ユースケース2: サブドメインの統合
blog.example.com.    IN  CNAME  example.com.
shop.example.com.    IN  CNAME  example.com.

# ユースケース3: 複数環境の管理
app-dev.example.com.  IN  CNAME  dev-lb.example.com.
app-stg.example.com.  IN  CNAME  stg-lb.example.com.
app-prod.example.com. IN  CNAME  prod-lb.example.com.

# ユースケース4: サービス移行
# 旧システムから新システムへの段階的移行
legacy.example.com.   IN  CNAME  old-server.example.com.
# 移行後
legacy.example.com.   IN  CNAME  new-server.example.com.

# 確認コマンド
dig www.example.com

# 出力例:
# ;; ANSWER SECTION:
# www.example.com.      300  IN  CNAME  d111111abcdef8.cloudfront.net.
# d111111abcdef8.cloudfront.net. 60 IN A 52.84.123.45
```

---

## 2. CNAMEに関連する実践的な問題

### 2.1 AWS Advanced JDBC WrapperとCNAMEの問題

**AWS Advanced JDBC Wrapper**は、RDS Proxy、Aurora、フェイルオーバー機能を提供するJDBCドライバーのラッパーです。このラッパーには、**CNAMEレコードに対する特殊な動作**があります。

```
┌─────────────────────────────────────────────────────────────┐
│      AWS Advanced JDBC Wrapper の CNAME処理動作              │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  【問題のシナリオ】                                          │
│                                                              │
│  DNS設定:                                                    │
│  db.example.com.  IN  CNAME  my-db.cluster-xxx.us-east-1.   │
│                               rds.amazonaws.com.             │
│                                                              │
│  アプリケーション設定:                                       │
│  jdbc:aws-wrapper:postgresql://db.example.com:5432/mydb     │
│                                                              │
│  【何が起こるか】                                            │
│  1. DNS解決: db.example.com → CNAME検出                     │
│  2. CNAME先を取得: my-db.cluster-xxx.us-east-1.rds.amazonaws.com │
│  3. ⚠️ ラッパーがCNAME先のホスト名を使用                     │
│  4. SSL証明書検証で、元のホスト名(db.example.com)ではなく   │
│     CNAME先(*.rds.amazonaws.com)で検証される                │
│  5. トポロジー検出（クラスタ構成の把握）にも影響             │
│                                                              │
│  【問題点】                                                  │
│  - CNAME先のホスト名がアプリケーションに露出                 │
│  - フェイルオーバー検知に影響する可能性                      │
│  - 意図しない動作につながる                                  │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

#### 実際のエラー例

```java
// Java アプリケーションログ
Caused by: javax.net.ssl.SSLHandshakeException:
  No subject alternative DNS name matching db.example.com found.
  Certificate is valid for *.rds.amazonaws.com

// または
FATAL: SSL connection failed:
  server certificate for "*.rds.amazonaws.com" does not match host name "db.example.com"
```

#### 対策方法

```
対策1: CNAMEを使わず、Aレコードを使用
  ❌ db.example.com.  IN  CNAME  my-db.cluster-xxx.us-east-1.rds.amazonaws.com.
  ✅ db.example.com.  IN  A      10.0.1.10

  利点: DNS標準に準拠、問題が起きにくい
  欠点: IPアドレスが変わった場合に手動更新が必要

対策2: Route 53 ALIASレコードを使用（AWSの場合）
  ✅ db.example.com.  IN  ALIAS  my-db.cluster-xxx.us-east-1.rds.amazonaws.com.

  利点: 自動的にIPアドレス追従、CNAMEの制約なし
  欠点: Route 53専用機能（他のDNSサービスでは使えない）

対策3: RDS Proxyを使用
  RDS Proxy経由で接続することで、エンドポイントが安定
  ✅ jdbc:aws-wrapper:postgresql://my-rds-proxy.proxy-xxx.us-east-1.rds.amazonaws.com:5432/mydb

対策4: JDBC接続パラメータでSSL検証を調整（非推奨）
  ⚠️ jdbc:aws-wrapper:postgresql://db.example.com:5432/mydb?ssl=true&sslmode=require&sslrootcert=...

  欠点: セキュリティリスク、本質的な解決ではない
```

### 2.2 その他のCNAME問題例

#### 問題1: メール配送とCNAME

```
# ❌ 間違った設定
example.com.    IN  CNAME  mail.example.com.
example.com.    IN  MX     10 mail.example.com.
→ CNAMEとMXが共存できないため、メールが届かない

# ✅ 正しい設定
example.com.    IN  A      93.184.216.34
example.com.    IN  MX     10 mail.example.com.
mail.example.com. IN A    93.184.216.35
```

#### 問題2: HTTPリダイレクトとCNAME

```
# シナリオ: example.com → www.example.com にリダイレクトしたい

# ❌ CNAMEだけでは不可能
example.com.    IN  CNAME  www.example.com.
→ DNSレベルではリダイレクトできない
→ HTTPレベルの処理が必要

# ✅ 正しいアプローチ
# 1. DNSで両方のホストを設定
example.com.     IN  A      93.184.216.34
www.example.com. IN  A      93.184.216.34

# 2. Webサーバーでリダイレクト設定
# Nginx例:
server {
    server_name example.com;
    return 301 https://www.example.com$request_uri;
}

# Apache例:
<VirtualHost *:80>
    ServerName example.com
    Redirect permanent / https://www.example.com/
</VirtualHost>
```

#### 問題3: CDNとCNAME設定ミス

```bash
# ❌ よくある間違い
www.example.com.  IN  CNAME  example.com.
example.com.      IN  CNAME  d111111abcdef8.cloudfront.net.
→ CNAMEチェーンが発生、パフォーマンス低下

# ✅ 正しい設定
www.example.com.  IN  CNAME  d111111abcdef8.cloudfront.net.
example.com.      IN  A      52.84.123.45  # CloudFrontのIPまたはALIAS
```

---

## 3. AWS Route 53

### 3.1 Route 53の概要

**Amazon Route 53**は、AWSが提供する高可用性でスケーラブルなDNSサービスです。

```
┌─────────────────────────────────────────────────────────────┐
│                  Route 53 の主要機能                         │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  1. 権威DNSサービス                                          │
│     - ドメインのホストゾーン管理                             │
│     - 高可用性（複数のAZに分散）                             │
│     - SLA 100%                                              │
│                                                              │
│  2. ドメイン登録                                             │
│     - gTLD/ccTLDの登録・管理                                │
│                                                              │
│  3. ヘルスチェック                                           │
│     - エンドポイントの監視                                   │
│     - 障害検知とフェイルオーバー                             │
│                                                              │
│  4. トラフィックポリシー                                     │
│     - ルーティングポリシー（後述）                           │
│     - 地理的ルーティング                                     │
│     - レイテンシーベースルーティング                         │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 ALIASレコード（Route 53拡張機能）

**ALIASレコード**は、Route 53独自の機能で、CNAMEの制約を回避しつつ、AWSリソースへのマッピングを提供します。

```
┌─────────────────────────────────────────────────────────────┐
│              ALIAS vs CNAME vs A レコード                    │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  項目              │ A      │ CNAME  │ ALIAS                 │
│  ─────────────────┼────────┼────────┼──────────────────    │
│  ゾーンAPEXで使用  │ ✅     │ ❌     │ ✅                    │
│  他レコードと共存  │ ✅     │ ❌     │ ✅（一部）            │
│  IPアドレス指定    │ ✅     │ ❌     │ 自動解決              │
│  DNS問い合わせ回数 │ 1回    │ 2回以上│ 1回                   │
│  Route 53課金     │ 有料   │ 有料   │ AWSリソース宛は無料   │
│  標準DNS仕様      │ ✅     │ ✅     │ ❌（Route 53専用）    │
│                                                              │
│  【ALIAS使用例】                                             │
│  example.com.  ALIAS  my-alb-123.us-east-1.elb.amazonaws.com │
│                                                              │
│  内部動作:                                                   │
│  1. クライアント: example.com のIPは?                        │
│  2. Route 53: ALIASを検出                                   │
│  3. Route 53: ELBのIPアドレスを自動取得                     │
│  4. Route 53: Aレコードとして応答（例: 52.1.2.3）           │
│  → クライアントからは通常のAレコードに見える                 │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

#### ALIASレコード対応AWSサービス

```
- CloudFront ディストリビューション
- Elastic Load Balancing (ALB, NLB, CLB)
- API Gateway
- VPC エンドポイント
- S3 Webサイトエンドポイント
- 同一ホストゾーン内の他のレコード
- Global Accelerator
```

#### ALIAS設定例（AWS CLI）

```bash
# ALIASレコードを作成（JSON形式）
cat > alias-record.json <<EOF
{
  "Changes": [{
    "Action": "CREATE",
    "ResourceRecordSet": {
      "Name": "example.com",
      "Type": "A",
      "AliasTarget": {
        "HostedZoneId": "Z35SXDOTRQ7X7K",
        "DNSName": "my-alb-123.us-east-1.elb.amazonaws.com",
        "EvaluateTargetHealth": true
      }
    }
  }]
}
EOF

aws route53 change-resource-record-sets \
  --hosted-zone-id Z1234567890ABC \
  --change-batch file://alias-record.json

# 確認
dig example.com +short
# 52.1.2.3  ← ALBのIPアドレスが直接返される
```

### 3.3 ルーティングポリシー

Route 53は、複数の高度なルーティングポリシーをサポートしています。

#### シンプルルーティング

```
最も基本的なルーティング。1つのレコードに複数の値を返す。

example.com.  A  192.0.2.1
example.com.  A  192.0.2.2
example.com.  A  192.0.2.3

→ 全てのIPアドレスがランダムな順序でクライアントに返される
```

#### 加重ルーティング（Weighted Routing）

```
┌─────────────────────────────────────────────────────────────┐
│                  加重ルーティング                            │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  トラフィックを重み付けして分散                              │
│                                                              │
│  レコード1: example.com  A  192.0.2.1  (重み: 70)           │
│  レコード2: example.com  A  192.0.2.2  (重み: 20)           │
│  レコード3: example.com  A  192.0.2.3  (重み: 10)           │
│                                                              │
│  結果:                                                       │
│  70%のトラフィック → 192.0.2.1                               │
│  20%のトラフィック → 192.0.2.2                               │
│  10%のトラフィック → 192.0.2.3                               │
│                                                              │
│  ユースケース:                                               │
│  - カナリアリリース（新バージョンに10%のトラフィック）       │
│  - A/Bテスト                                                 │
│  - サーバー能力に応じた負荷分散                              │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

#### レイテンシーベースルーティング

```
┌─────────────────────────────────────────────────────────────┐
│              レイテンシーベースルーティング                   │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  クライアントから最も近い（低レイテンシー）リージョンに      │
│  トラフィックをルーティング                                  │
│                                                              │
│  設定例:                                                     │
│  example.com  A  52.1.2.3   (us-east-1)                     │
│  example.com  A  13.2.3.4   (ap-northeast-1)                │
│  example.com  A  18.3.4.5   (eu-west-1)                     │
│                                                              │
│  クライアント              応答                              │
│  東京          →  13.2.3.4  (ap-northeast-1)                │
│  ニューヨーク  →  52.1.2.3  (us-east-1)                     │
│  ロンドン      →  18.3.4.5  (eu-west-1)                     │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

#### フェイルオーバールーティング

```bash
# プライマリ/セカンダリ構成
# ヘルスチェックでプライマリが異常の場合、セカンダリを返す

# プライマリレコード
aws route53 change-resource-record-sets \
  --hosted-zone-id Z1234567890ABC \
  --change-batch '{
    "Changes": [{
      "Action": "CREATE",
      "ResourceRecordSet": {
        "Name": "example.com",
        "Type": "A",
        "SetIdentifier": "Primary",
        "Failover": "PRIMARY",
        "TTL": 60,
        "ResourceRecords": [{"Value": "192.0.2.1"}],
        "HealthCheckId": "abc-123-def"
      }
    }]
  }'

# セカンダリレコード
# (同様にFailover: "SECONDARY"で設定)

# ヘルスチェック作成
aws route53 create-health-check \
  --health-check-config \
    IPAddress=192.0.2.1,Port=443,Type=HTTPS,ResourcePath=/health
```

#### 地理的ルーティング（Geolocation）

```
┌─────────────────────────────────────────────────────────────┐
│                  地理的ルーティング                          │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  クライアントの地理的位置に基づいてルーティング              │
│                                                              │
│  設定例:                                                     │
│  - 日本からのアクセス → jp-server.example.com               │
│  - 米国からのアクセス → us-server.example.com               │
│  - その他の地域       → default-server.example.com          │
│                                                              │
│  ユースケース:                                               │
│  - コンテンツのローカライゼーション                          │
│  - 規制対応（特定国からのアクセス制御）                      │
│  - パフォーマンス最適化                                      │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 3.4 Route 53プライベートホストゾーン

VPC内でのみ解決可能なプライベートDNSゾーンを作成できます。

```bash
# プライベートホストゾーン作成
aws route53 create-hosted-zone \
  --name internal.example.com \
  --vpc VPCRegion=us-east-1,VPCId=vpc-1234567890abcdef0 \
  --caller-reference "$(date +%s)" \
  --hosted-zone-config PrivateZone=true

# レコード作成
aws route53 change-resource-record-sets \
  --hosted-zone-id Z9876543210ZYX \
  --change-batch '{
    "Changes": [{
      "Action": "CREATE",
      "ResourceRecordSet": {
        "Name": "db.internal.example.com",
        "Type": "A",
        "TTL": 300,
        "ResourceRecords": [{"Value": "10.0.1.10"}]
      }
    }]
  }'

# VPC内のEC2インスタンスから解決可能
dig db.internal.example.com +short
# 10.0.1.10
```

**プライベートホストゾーンのユースケース**:
```
- RDSエンドポイントのカスタムドメイン
- 内部APIエンドポイント
- 内部マイクロサービス間通信
- 開発/ステージング環境の分離
```

---

## 4. DNSSEC（DNS Security Extensions）

### 4.1 DNSSECの基本概念

**DNSSEC**は、DNSレスポンスの真正性と完全性を保証するための拡張機能です。

```
┌─────────────────────────────────────────────────────────────┐
│                  DNSSEC の仕組み                             │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  【DNSSECなし】                                              │
│  クライアント ← [応答] ← DNSサーバー                         │
│  → 応答が本物か偽物か判断できない                            │
│                                                              │
│  【DNSSECあり】                                              │
│  クライアント ← [応答 + デジタル署名] ← DNSサーバー          │
│  → デジタル署名を検証することで、応答の真正性を確認          │
│                                                              │
│  【信頼の連鎖（Chain of Trust）】                            │
│  . (ルート)  ← ルートキーで署名                             │
│    │                                                         │
│    ├─→ .com  ← ルートが署名                                 │
│         │                                                    │
│         ├─→ example.com  ← .comが署名                       │
│              │                                               │
│              ├─→ www.example.com  ← example.comが署名        │
│                                                              │
│  各階層が下位階層の公開鍵に署名することで、                  │
│  ルートから葉まで信頼を伝搬                                  │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 4.2 DNSSECのレコードタイプ

```
RRSIG (Resource Record Signature)
  - DNSレコードのデジタル署名

DNSKEY (DNS Public Key)
  - ゾーンの公開鍵
  - ZSK (Zone Signing Key): レコードに署名
  - KSK (Key Signing Key): DNSKEYに署名

DS (Delegation Signer)
  - 子ゾーンの公開鍵のハッシュ
  - 親ゾーンに設定して信頼の連鎖を構築

NSEC / NSEC3 (Next Secure)
  - 存在しないドメイン名の証明（NXDOMAIN認証）
```

### 4.3 DNSSEC検証

```bash
# DNSSECが有効か確認
dig example.com +dnssec

# 出力例（DNSSEC有効）:
# ;; ANSWER SECTION:
# example.com.    3600  IN  A     93.184.216.34
# example.com.    3600  IN  RRSIG A 13 2 3600 ...
#                           ↑ デジタル署名が付いている

# DNSSEC検証を有効にしてクエリ
dig @8.8.8.8 example.com +dnssec +multiline

# DNSSECキーの確認
dig example.com DNSKEY +short

# DSレコードの確認（親ゾーンに設定されている）
dig example.com DS +short
```

### 4.4 Route 53でのDNSSEC設定

```bash
# 1. ホストゾーンでDNSSECを有効化
aws route53 enable-hosted-zone-dnssec \
  --hosted-zone-id Z1234567890ABC

# 2. KSK（キー署名鍵）を作成
aws route53 create-key-signing-key \
  --hosted-zone-id Z1234567890ABC \
  --name example-ksk \
  --status ACTIVE \
  --key-management-service-arn arn:aws:kms:us-east-1:123456789012:key/...

# 3. 親ゾーン（レジストラ）にDSレコードを設定
# Route 53コンソールでDSレコード情報を取得し、ドメインレジストラに設定

# 4. DNSSEC検証
dig @8.8.8.8 example.com +dnssec +multiline
```

---

## 5. サービスディスカバリーとプライベートDNS

### 5.1 AWS Cloud Map

**AWS Cloud Map**は、クラウドリソースのサービスディスカバリーを提供します。

```
┌─────────────────────────────────────────────────────────────┐
│                  AWS Cloud Map の動作                        │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  【サービス登録】                                            │
│  ECS Task/K8s Pod起動                                        │
│      ↓                                                      │
│  Cloud Map にサービスインスタンス登録                        │
│      ↓                                                      │
│  DNSレコード自動作成                                         │
│  api.service.local  →  10.0.1.10 (Task IP)                  │
│                                                              │
│  【サービス発見】                                            │
│  クライアント: api.service.local のIPは?                     │
│      ↓                                                      │
│  Route 53 / Cloud Map が応答                                │
│      ↓                                                      │
│  10.0.1.10 に接続                                            │
│                                                              │
│  【ヘルスチェック統合】                                      │
│  不健全なインスタンスは自動的にDNSから除外                   │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

#### Cloud Map設定例

```bash
# 1. ネームスペース作成（プライベートDNS）
aws servicediscovery create-private-dns-namespace \
  --name service.local \
  --vpc vpc-1234567890abcdef0

# 2. サービス作成
aws servicediscovery create-service \
  --name api \
  --namespace-id ns-abc123 \
  --dns-config \
    'NamespaceId=ns-abc123,DnsRecords=[{Type=A,TTL=60}]' \
  --health-check-custom-config FailureThreshold=1

# 3. サービスインスタンス登録（通常はECS/EKSが自動実行）
aws servicediscovery register-instance \
  --service-id srv-xyz789 \
  --instance-id i-1234567890abcdef0 \
  --attributes \
    'AWS_INSTANCE_IPV4=10.0.1.10,AWS_INSTANCE_PORT=8080'

# 4. DNSクエリ
dig api.service.local +short
# 10.0.1.10
```

### 5.2 Kubernetesサービスディスカバリー

Kubernetes（EKS含む）は、内部DNSベースのサービスディスカバリーを提供します。

```
┌─────────────────────────────────────────────────────────────┐
│          Kubernetes Service Discovery                        │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  【DNS名の形式】                                             │
│  <service-name>.<namespace>.svc.cluster.local                │
│                                                              │
│  例:                                                         │
│  api-service.default.svc.cluster.local                       │
│  db-service.production.svc.cluster.local                     │
│                                                              │
│  【省略形】                                                  │
│  同一Namespace内:  <service-name>                            │
│  異なるNamespace:  <service-name>.<namespace>                │
│                                                              │
│  【SRVレコード】                                             │
│  _<port-name>._<protocol>.<service>.<namespace>.svc.cluster.local │
│                                                              │
│  例:                                                         │
│  _http._tcp.api-service.default.svc.cluster.local            │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

```yaml
# Kubernetes Service定義
apiVersion: v1
kind: Service
metadata:
  name: api-service
  namespace: production
spec:
  selector:
    app: api
  ports:
    - name: http
      port: 80
      targetPort: 8080
---
# Podから接続
apiVersion: v1
kind: Pod
metadata:
  name: client
  namespace: production
spec:
  containers:
  - name: app
    image: myapp:latest
    env:
    - name: API_URL
      value: "http://api-service:80"  # 同一Namespace内なので省略形
```

```bash
# Pod内からDNS解決テスト
kubectl exec -it client -- nslookup api-service

# Name:      api-service.production.svc.cluster.local
# Address 1: 10.100.200.50 api-service.production.svc.cluster.local

# SRVレコード確認
kubectl exec -it client -- nslookup -type=SRV _http._tcp.api-service.production.svc.cluster.local
```

---

## 6. DNS最適化とベストプラクティス

### 6.1 TTL最適化

```
シナリオ別のTTL推奨値:

┌────────────────────┬──────┬─────────────────────────┐
│ 用途               │ TTL  │ 理由                     │
├────────────────────┼──────┼─────────────────────────┤
│ 本番Webサイト      │ 3600-│ キャッシュ効率、コスト削減│
│ (APEXドメイン)     │86400 │                         │
├────────────────────┼──────┼─────────────────────────┤
│ 本番API            │ 300- │ 柔軟な変更対応           │
│                    │ 3600 │                         │
├────────────────────┼──────┼─────────────────────────┤
│ ロードバランサー   │ 60-  │ ヘルスチェック連動       │
│ (ELB等)            │ 300  │                         │
├────────────────────┼──────┼─────────────────────────┤
│ 開発/検証環境      │ 60-  │ 頻繁な変更対応           │
│                    │ 300  │                         │
├────────────────────┼──────┼─────────────────────────┤
│ フェイルオーバー   │ 60   │ 高速切り替え             │
│ レコード           │      │                         │
└────────────────────┴──────┴─────────────────────────┘
```

### 6.2 DNS問い合わせの削減

```java
// アプリケーション側のDNSキャッシュ設定例

// Java - JVM DNSキャッシュ設定（システムプロパティ）
// 成功時のキャッシュTTL（秒、-1=無期限、0=キャッシュなし）
-Dsun.net.inetaddr.ttl=60

// 失敗時のキャッシュTTL（秒）
-Dsun.net.inetaddr.negative.ttl=10

// セキュリティマネージャ有効時のデフォルトTTL
// デフォルト: 30秒（セキュリティ対策でキャッシュを長くしない）

// プログラムから設定（Security.setProperty）
import java.security.Security;

Security.setProperty("networkaddress.cache.ttl", "60");
Security.setProperty("networkaddress.cache.negative.ttl", "10");
```

```python
# Python - DNSキャッシュ（aiodns使用例）
import asyncio
import aiodns

# DNSリゾルバをアプリケーション起動時に作成（キャッシュ共有）
resolver = aiodns.DNSResolver()

async def resolve_hostname(hostname):
    try:
        result = await resolver.gethostbyname(hostname, socket.AF_INET)
        return result.addresses[0]
    except aiodns.error.DNSError as e:
        print(f"DNS resolution failed: {e}")
        return None
```

### 6.3 マルチリージョン構成のDNS設計

```
┌─────────────────────────────────────────────────────────────┐
│          マルチリージョンDNS設計パターン                     │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  【パターン1: レイテンシーベースルーティング】               │
│  example.com                                                 │
│    ├─► us-east-1  (52.1.2.3)   ← 北米ユーザー               │
│    ├─► eu-west-1  (18.2.3.4)   ← ヨーロッパユーザー         │
│    └─► ap-northeast-1 (13.3.4.5) ← アジアユーザー           │
│                                                              │
│  【パターン2: フェイルオーバー + レイテンシー】              │
│  プライマリ: us-east-1 (ヘルスチェックあり)                  │
│  セカンダリ: us-west-2 (プライマリ障害時)                    │
│                                                              │
│  【パターン3: 加重 + フェイルオーバー（カナリア）】          │
│  旧バージョン: 90% (ヘルスチェックあり)                      │
│  新バージョン: 10% (ヘルスチェックあり)                      │
│  → 新バージョンに問題があれば自動的に旧バージョンへ          │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 6.4 DNS監視とアラート

```bash
# CloudWatchでRoute 53メトリクスを監視

# DNSクエリ数
aws cloudwatch get-metric-statistics \
  --namespace AWS/Route53 \
  --metric-name Queries \
  --dimensions Name=HostedZoneId,Value=Z1234567890ABC \
  --start-time 2025-12-26T00:00:00Z \
  --end-time 2025-12-26T23:59:59Z \
  --period 3600 \
  --statistics Sum

# ヘルスチェック失敗アラート
aws cloudwatch put-metric-alarm \
  --alarm-name route53-health-check-failed \
  --alarm-description "Route 53 health check failed" \
  --metric-name HealthCheckStatus \
  --namespace AWS/Route53 \
  --statistic Minimum \
  --period 60 \
  --threshold 1 \
  --comparison-operator LessThanThreshold \
  --datapoints-to-alarm 2 \
  --evaluation-periods 2 \
  --alarm-actions arn:aws:sns:us-east-1:123456789012:alerts
```

---

## まとめ

### 学んだこと

本章では、DNSの高度なトピックを学びました:

- **CNAMEレコードの詳細**
  - CNAMEは標準的なDNS仕様（RFC 1034/1035）
  - 他のレコードとの共存禁止、ゾーンAPEXでの制約
  - AWS Advanced JDBC WrapperでのCNAME問題と対策

- **AWS Route 53**
  - ALIASレコード（Route 53独自機能でCNAME制約を回避）
  - 高度なルーティングポリシー（加重、レイテンシー、フェイルオーバー等）
  - プライベートホストゾーンとVPC統合

- **DNSSEC**
  - DNS応答の真正性・完全性の保証
  - 信頼の連鎖（Chain of Trust）
  - Route 53でのDNSSEC有効化

- **サービスディスカバリー**
  - AWS Cloud Mapによる動的サービス登録
  - KubernetesのDNSベースサービスディスカバリー

- **DNS最適化**
  - TTL最適化、アプリケーション側キャッシュ
  - マルチリージョン構成のDNS設計パターン

### 重要なポイント

```
1. CNAMEの制約を理解する
   - ゾーンAPEXでは使えない
   - 他のレコードと共存できない
   - AWS Advanced JDBC Wrapperでは注意が必要

2. Route 53 ALIASを活用する
   - CNAMEの制約を回避
   - AWSリソースへの動的マッピング
   - DNS問い合わせが1回で済む

3. 適切なルーティングポリシーを選択
   - レイテンシーベース: グローバルパフォーマンス
   - フェイルオーバー: 高可用性
   - 加重: カナリアリリース

4. セキュリティとパフォーマンスのバランス
   - DNSSEC: セキュリティ向上
   - TTL最適化: パフォーマンスとコスト削減
```

### 次のステップ

- [03-load-balancing.md](./03-load-balancing.md) - ロードバランシングとDNSの連携
- [04-ssl-tls-certificates.md](./04-ssl-tls-certificates.md) - SSL/TLS証明書管理
- [06-network-troubleshooting.md](./06-network-troubleshooting.md) - DNSトラブルシューティング実践

### 参考リンク

- [RFC 1034 - Domain Names - Concepts](https://tools.ietf.org/html/rfc1034)
- [RFC 4033-4035 - DNSSEC](https://tools.ietf.org/html/rfc4033)
- [AWS Route 53 Documentation](https://docs.aws.amazon.com/route53/)
- [AWS Advanced JDBC Wrapper](https://github.com/awslabs/aws-advanced-jdbc-wrapper)
- [Kubernetes DNS Specification](https://kubernetes.io/docs/concepts/services-networking/dns-pod-service/)
