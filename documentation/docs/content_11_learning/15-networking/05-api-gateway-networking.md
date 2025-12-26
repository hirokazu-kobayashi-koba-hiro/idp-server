# API Gatewayとネットワーキング

## 所要時間
約45分

## 学べること
- API Gatewayの基本概念とネットワーク統合
- REST API、HTTP API、WebSocket APIの違い
- VPC統合とプライベートAPI
- カスタムドメインとSSL/TLS設定
- スロットリング、キャッシング、認証
- CloudFrontとの連携パターン

## 前提知識
- [01-dns-fundamentals.md](./01-dns-fundamentals.md) - DNS基礎
- [03-load-balancing.md](./03-load-balancing.md) - ロードバランシング
- [04-ssl-tls-certificates.md](./04-ssl-tls-certificates.md) - SSL/TLS証明書
- HTTP/RESTの基本

---

## 1. API Gatewayの基礎

### 1.1 API Gatewayとは

**Amazon API Gateway**は、あらゆる規模のREST、HTTP、WebSocket APIを作成、公開、保守、監視、保護するためのフルマネージド型サービスです。

```
┌─────────────────────────────────────────────────────────────┐
│              API Gateway の役割                              │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  クライアント（モバイル、Web、IoT等）                         │
│      │                                                      │
│      │ HTTPS リクエスト                                     │
│      ▼                                                      │
│  ┌──────────────────────────────────────────────────────┐  │
│  │           API Gateway                                │  │
│  │  ┌────────────────────────────────────────────────┐  │  │
│  │  │ 1. 認証・認可（IAM、Cognito、Lambda）          │  │  │
│  │  │ 2. リクエスト検証                              │  │  │
│  │  │ 3. レート制限（スロットリング）                 │  │  │
│  │  │ 4. キャッシング                                │  │  │
│  │  │ 5. リクエスト/レスポンス変換                   │  │  │
│  │  │ 6. ロギング・モニタリング                      │  │  │
│  │  └────────────────────────────────────────────────┘  │  │
│  └──────────────────────────────────────────────────────┘  │
│      │                                                      │
│      ├───► Lambda関数                                      │
│      ├───► EC2/ECS（VPC統合）                              │
│      ├───► DynamoDB、S3                                    │
│      └───► 外部HTTP エンドポイント                          │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 API Gatewayの種類

```
┌─────────────────────────────────────────────────────────────┐
│          REST API vs HTTP API vs WebSocket API              │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  REST API                                                    │
│  - 最も機能豊富                                              │
│  - API キー、使用量プラン、リクエスト検証                    │
│  - モックレスポンス、SDKジェネレーション                     │
│  - 価格: 高め                                                │
│  用途: エンタープライズアプリケーション                      │
│                                                              │
│  HTTP API                                                    │
│  - シンプル、低コスト（REST APIの70%安）                     │
│  - JWT認証ネイティブサポート                                 │
│  - OIDC/OAuth 2.0統合                                       │
│  - 価格: 低い                                                │
│  用途: マイクロサービス、OAuth/OIDC認証API                   │
│                                                              │
│  WebSocket API                                               │
│  - 双方向通信                                                │
│  - リアルタイムアプリケーション                              │
│  - 接続管理（@connections）                                 │
│  用途: チャット、ゲーム、リアルタイムダッシュボード          │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. API Gatewayのネットワーク統合

### 2.1 統合タイプ

```
┌─────────────────────────────────────────────────────────────┐
│              API Gateway 統合タイプ                          │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  1. Lambda統合（LAMBDA / LAMBDA_PROXY）                     │
│     API Gateway → Lambda関数                                │
│                                                              │
│  2. HTTP統合（HTTP / HTTP_PROXY）                           │
│     API Gateway → 外部HTTPエンドポイント                    │
│                                                              │
│  3. AWS統合（AWS）                                          │
│     API Gateway → AWSサービス（DynamoDB、S3等）             │
│                                                              │
│  4. VPC Link統合                                             │
│     API Gateway → プライベートリソース（NLB経由）           │
│                                                              │
│  5. モック統合（MOCK）                                      │
│     API Gateway → 固定レスポンス（バックエンドなし）        │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 VPC統合（VPC Link）

**VPC Link**を使用すると、API GatewayからVPC内のプライベートリソースにアクセスできます。

```
┌─────────────────────────────────────────────────────────────┐
│              VPC Link の仕組み                               │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  インターネット                                              │
│      │                                                      │
│      │ HTTPS                                                │
│      ▼                                                      │
│  ┌──────────────────┐                                       │
│  │  API Gateway     │ (パブリック)                          │
│  │  (REST API)      │                                       │
│  └──────────────────┘                                       │
│      │                                                      │
│      │ VPC Link (REST API用)                                │
│      │ または                                                │
│      │ VPC Link (HTTP API用)                                │
│      ▼                                                      │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  VPC                                                  │  │
│  │  ┌────────────────────────────────────────────────┐  │  │
│  │  │  Network Load Balancer (NLB)                   │  │  │
│  │  └────────────────────────────────────────────────┘  │  │
│  │      │                                                │  │
│  │      ├───► EC2 インスタンス (10.0.1.10)               │  │
│  │      ├───► ECS タスク (10.0.1.20)                     │  │
│  │      └───► Lambda (VPC内)                             │  │
│  │                                                        │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

#### REST API用 VPC Link作成

```bash
# 1. NLB作成（VPC内）
aws elbv2 create-load-balancer \
  --name internal-nlb \
  --subnets subnet-12345678 subnet-87654321 \
  --scheme internal \
  --type network

# 2. ターゲットグループ作成
aws elbv2 create-target-group \
  --name api-targets \
  --protocol TCP \
  --port 8080 \
  --vpc-id vpc-1234567890abcdef0

# 3. リスナー作成
aws elbv2 create-listener \
  --load-balancer-arn arn:aws:elasticloadbalancing:us-east-1:123456789012:loadbalancer/net/internal-nlb/50dc6c495c0c9188 \
  --protocol TCP \
  --port 80 \
  --default-actions Type=forward,TargetGroupArn=arn:aws:elasticloadbalancing:us-east-1:123456789012:targetgroup/api-targets/50dc6c495c0c9188

# 4. VPC Link作成（REST API用）
aws apigateway create-vpc-link \
  --name my-vpc-link \
  --target-arns arn:aws:elasticloadbalancing:us-east-1:123456789012:loadbalancer/net/internal-nlb/50dc6c495c0c9188

# 5. REST APIにVPC Link統合を設定
# API Gateway Console または AWS CLI/CloudFormationで設定
```

#### HTTP API用 VPC Link作成

```bash
# HTTP API用のVPC Linkは異なるコマンド
aws apigatewayv2 create-vpc-link \
  --name my-http-vpc-link \
  --subnet-ids subnet-12345678 subnet-87654321 \
  --security-group-ids sg-12345678
```

### 2.3 プライベートAPI

**プライベートAPI**は、VPCエンドポイント経由でのみアクセス可能なAPIです。

```
┌─────────────────────────────────────────────────────────────┐
│              プライベートAPI の構成                          │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │  VPC                                                    │ │
│  │  ┌──────────────────────────────────────────────────┐  │ │
│  │  │  EC2インスタンス                                  │  │ │
│  │  │  (10.0.1.10)                                      │  │ │
│  │  └──────────────────────────────────────────────────┘  │ │
│  │      │                                                  │ │
│  │      │ HTTPS                                            │ │
│  │      ▼                                                  │ │
│  │  ┌──────────────────────────────────────────────────┐  │ │
│  │  │  VPCエンドポイント (execute-api)                  │  │ │
│  │  │  vpce-1234567890abcdef0                           │  │ │
│  │  └──────────────────────────────────────────────────┘  │ │
│  │      │                                                  │ │
│  └──────┼──────────────────────────────────────────────────┘ │
│         │                                                    │
│         │ AWS PrivateLink                                   │
│         ▼                                                    │
│  ┌──────────────────┐                                       │
│  │  API Gateway     │                                       │
│  │  (プライベートAPI)│                                       │
│  └──────────────────┘                                       │
│                                                              │
│  特徴:                                                       │
│  - インターネットからアクセス不可                            │
│  - VPC内からのみアクセス可能                                 │
│  - リソースポリシーで更に制限可能                            │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

#### プライベートAPI作成例

```bash
# 1. VPCエンドポイント作成
aws ec2 create-vpc-endpoint \
  --vpc-id vpc-1234567890abcdef0 \
  --vpc-endpoint-type Interface \
  --service-name com.amazonaws.us-east-1.execute-api \
  --subnet-ids subnet-12345678 subnet-87654321 \
  --security-group-ids sg-12345678

# 2. プライベートREST API作成
aws apigateway create-rest-api \
  --name private-api \
  --endpoint-configuration types=PRIVATE

# 3. リソースポリシー設定
cat > resource-policy.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": "*",
      "Action": "execute-api:Invoke",
      "Resource": "arn:aws:execute-api:us-east-1:123456789012:api-id/*",
      "Condition": {
        "StringEquals": {
          "aws:SourceVpce": "vpce-1234567890abcdef0"
        }
      }
    }
  ]
}
EOF

aws apigateway update-rest-api \
  --rest-api-id api-id \
  --patch-operations op=replace,path=/policy,value=file://resource-policy.json
```

---

## 3. カスタムドメインとSSL/TLS

### 3.1 カスタムドメインの設定

デフォルトのAPI Gatewayエンドポイント（例：`https://api-id.execute-api.us-east-1.amazonaws.com`）を、カスタムドメイン（例：`https://api.example.com`）に変更できます。

```
┌─────────────────────────────────────────────────────────────┐
│          カスタムドメインの構成                              │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  クライアント                                                │
│      │                                                      │
│      │ https://api.example.com/prod/users                   │
│      ▼                                                      │
│  ┌──────────────────┐                                       │
│  │  Route 53        │                                       │
│  │  api.example.com │                                       │
│  │  ↓ ALIAS         │                                       │
│  │  d-xxxx.execute-api.us-east-1.amazonaws.com             │
│  └──────────────────┘                                       │
│      │                                                      │
│      ▼                                                      │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  CloudFront ディストリビューション                    │  │
│  │  (API Gateway カスタムドメインが自動作成)             │  │
│  │  証明書: ACM (us-east-1)                              │  │
│  └──────────────────────────────────────────────────────┘  │
│      │                                                      │
│      ▼                                                      │
│  ┌──────────────────┐                                       │
│  │  API Gateway     │                                       │
│  │  ステージ: prod   │                                       │
│  │  パス: /users     │                                       │
│  └──────────────────┘                                       │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

#### カスタムドメイン作成手順

```bash
# 1. ACM証明書作成（us-east-1リージョン必須 - エッジ最適化の場合）
aws acm request-certificate \
  --domain-name api.example.com \
  --validation-method DNS \
  --region us-east-1

# 2. DNS検証完了後、カスタムドメイン作成
aws apigateway create-domain-name \
  --domain-name api.example.com \
  --certificate-arn arn:aws:acm:us-east-1:123456789012:certificate/12345678-1234-1234-1234-123456789012 \
  --endpoint-configuration types=EDGE \
  --security-policy TLS_1_2

# リージョナルエンドポイントの場合（推奨）
aws apigateway create-domain-name \
  --domain-name api.example.com \
  --regional-certificate-arn arn:aws:acm:us-east-1:123456789012:certificate/12345678-1234-1234-1234-123456789012 \
  --endpoint-configuration types=REGIONAL \
  --security-policy TLS_1_2

# 3. ベースパスマッピング作成（API ステージとカスタムドメインを紐付け）
aws apigateway create-base-path-mapping \
  --domain-name api.example.com \
  --rest-api-id api123456 \
  --stage prod \
  --base-path v1

# 複数バージョンのAPI
aws apigateway create-base-path-mapping \
  --domain-name api.example.com \
  --rest-api-id api123456 \
  --stage prod \
  --base-path v1

aws apigateway create-base-path-mapping \
  --domain-name api.example.com \
  --rest-api-id api789012 \
  --stage prod \
  --base-path v2

# 結果:
# https://api.example.com/v1/* → api123456 (prod ステージ)
# https://api.example.com/v2/* → api789012 (prod ステージ)

# 4. Route 53でDNSレコード作成
aws route53 change-resource-record-sets \
  --hosted-zone-id Z1234567890ABC \
  --change-batch '{
    "Changes": [{
      "Action": "CREATE",
      "ResourceRecordSet": {
        "Name": "api.example.com",
        "Type": "A",
        "AliasTarget": {
          "HostedZoneId": "Z2FDTNDATAQYW2",
          "DNSName": "d-xxxx.execute-api.us-east-1.amazonaws.com",
          "EvaluateTargetHealth": false
        }
      }
    }]
  }'
```

### 3.2 エッジ最適化 vs リージョナル

```
┌─────────────────────────────────────────────────────────────┐
│          エッジ最適化 vs リージョナルエンドポイント          │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  エッジ最適化エンドポイント                                  │
│  - CloudFront経由でグローバル配信                            │
│  - 低レイテンシー（エッジロケーション活用）                  │
│  - ACM証明書はus-east-1リージョン必須                        │
│  - 追加コストなし（CloudFront組み込み）                      │
│  用途: グローバルアプリケーション                            │
│                                                              │
│  リージョナルエンドポイント                                  │
│  - 特定リージョンにデプロイ                                  │
│  - CloudFront統合は任意（手動設定）                          │
│  - ACM証明書は同一リージョンで取得                           │
│  - VPC Link、プライベートAPIで必須                           │
│  用途: 特定リージョンのみ、VPC統合、独自CloudFront設定       │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## 4. 認証・認可

### 4.1 認証方式の種類

```
┌─────────────────────────────────────────────────────────────┐
│          API Gateway 認証方式                                │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  1. IAM認証                                                  │
│     - AWS署名バージョン4（SigV4）                            │
│     - AWSサービス間通信に最適                                │
│                                                              │
│  2. Cognito オーソライザー                                   │
│     - Cognito User Poolsで認証                               │
│     - OAuth 2.0 / OIDC                                      │
│                                                              │
│  3. Lambda オーソライザー（カスタムオーソライザー）          │
│     - カスタム認証ロジック                                   │
│     - トークンベース or リクエストパラメータベース           │
│                                                              │
│  4. API キー                                                 │
│     - シンプルなアクセス制御                                 │
│     - 使用量プランと組み合わせ                               │
│                                                              │
│  5. JWT オーソライザー（HTTP API専用）                       │
│     - JWTトークンのネイティブ検証                            │
│     - OIDC/OAuth 2.0統合                                    │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 4.2 Lambda オーソライザー実装例

```javascript
// Lambda オーソライザー関数（Node.js）
exports.handler = async (event) => {
    console.log('Event:', JSON.stringify(event, null, 2));

    // トークン取得（Authorizationヘッダーから）
    const token = event.authorizationToken;

    try {
        // トークン検証ロジック（例: JWTトークン検証）
        const decoded = verifyJwtToken(token);

        // IAMポリシー生成（Allow）
        return generatePolicy(decoded.sub, 'Allow', event.methodArn);
    } catch (error) {
        console.error('Authentication failed:', error);

        // IAMポリシー生成（Deny）
        return generatePolicy('user', 'Deny', event.methodArn);
    }
};

function generatePolicy(principalId, effect, resource) {
    const authResponse = {
        principalId: principalId
    };

    if (effect && resource) {
        const policyDocument = {
            Version: '2012-10-17',
            Statement: [{
                Action: 'execute-api:Invoke',
                Effect: effect,
                Resource: resource
            }]
        };
        authResponse.policyDocument = policyDocument;
    }

    // コンテキスト（バックエンドに渡される追加情報）
    authResponse.context = {
        userId: principalId,
        email: 'user@example.com',
        role: 'admin'
    };

    return authResponse;
}

function verifyJwtToken(token) {
    // JWT検証ロジック（jsonwebtokenライブラリ使用）
    const jwt = require('jsonwebtoken');
    const publicKey = process.env.JWT_PUBLIC_KEY;

    return jwt.verify(token, publicKey, {
        algorithms: ['RS256']
    });
}
```

#### Lambdaオーソライザー設定（AWS CLI）

```bash
# Lambda関数作成（省略）

# REST APIにオーソライザー追加
aws apigateway create-authorizer \
  --rest-api-id api123456 \
  --name my-lambda-authorizer \
  --type TOKEN \
  --authorizer-uri arn:aws:apigateway:us-east-1:lambda:path/2015-03-31/functions/arn:aws:lambda:us-east-1:123456789012:function:my-authorizer/invocations \
  --identity-source method.request.header.Authorization \
  --authorizer-result-ttl-in-seconds 300

# メソッドにオーソライザーを適用
aws apigateway update-method \
  --rest-api-id api123456 \
  --resource-id abc123 \
  --http-method GET \
  --patch-operations op=replace,path=/authorizationType,value=CUSTOM \
                      op=replace,path=/authorizerId,value=authorizer-id
```

### 4.3 HTTP API JWTオーソライザー

HTTP APIでは、JWTオーソライザーをネイティブにサポートしています。

```bash
# HTTP API作成
aws apigatewayv2 create-api \
  --name my-http-api \
  --protocol-type HTTP \
  --target arn:aws:lambda:us-east-1:123456789012:function:my-function

# JWTオーソライザー作成
aws apigatewayv2 create-authorizer \
  --api-id api-id \
  --name jwt-authorizer \
  --authorizer-type JWT \
  --identity-source '$request.header.Authorization' \
  --jwt-configuration Audience=https://example.com,Issuer=https://cognito-idp.us-east-1.amazonaws.com/us-east-1_XXXXXXXXX

# ルートにオーソライザーを適用
aws apigatewayv2 update-route \
  --api-id api-id \
  --route-id route-id \
  --authorization-type JWT \
  --authorizer-id authorizer-id
```

---

## 5. スロットリング、キャッシング、CORS

### 5.1 スロットリング（レート制限）

```
┌─────────────────────────────────────────────────────────────┐
│          API Gateway スロットリング                          │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  【デフォルト制限】                                          │
│  - リージョンごとのアカウント制限:                           │
│    - 定常レート: 10,000 リクエスト/秒                        │
│    - バースト: 5,000 リクエスト                              │
│                                                              │
│  【制限の階層】                                              │
│  1. アカウントレベル（リージョンごと）                       │
│  2. ステージレベル                                           │
│  3. メソッドレベル                                           │
│                                                              │
│  【使用量プラン】                                            │
│  - APIキーごとのレート制限                                   │
│  - クォータ（日次/週次/月次）                                │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

#### スロットリング設定

```bash
# ステージレベルのスロットリング設定
aws apigateway update-stage \
  --rest-api-id api123456 \
  --stage-name prod \
  --patch-operations \
    op=replace,path=/throttle/rateLimit,value=1000 \
    op=replace,path=/throttle/burstLimit,value=2000

# メソッドレベルのスロットリング設定
aws apigateway update-stage \
  --rest-api-id api123456 \
  --stage-name prod \
  --patch-operations \
    op=replace,path=/*/GET/users/throttle/rateLimit,value=500 \
    op=replace,path=/*/GET/users/throttle/burstLimit,value=1000

# 使用量プラン作成
aws apigateway create-usage-plan \
  --name basic-plan \
  --throttle rateLimit=100,burstLimit=200 \
  --quota limit=10000,period=MONTH \
  --api-stages apiId=api123456,stage=prod

# APIキー作成
aws apigateway create-api-key \
  --name customer-api-key \
  --enabled

# APIキーを使用量プランに関連付け
aws apigateway create-usage-plan-key \
  --usage-plan-id plan-id \
  --key-id api-key-id \
  --key-type API_KEY
```

### 5.2 キャッシング

```
┌─────────────────────────────────────────────────────────────┐
│          API Gateway キャッシング                            │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  リクエスト1 → API Gateway                                   │
│               ↓ キャッシュミス                               │
│               Lambda実行 (100ms)                             │
│               ↓                                             │
│               キャッシュに保存 (TTL: 300秒)                  │
│               ↓                                             │
│               レスポンス                                     │
│                                                              │
│  リクエスト2 (同じパラメータ) → API Gateway                  │
│                                 ↓ キャッシュヒット           │
│                                 キャッシュから返却 (1ms)     │
│                                                              │
│  【キャッシュキー】                                          │
│  - パス                                                      │
│  - クエリパラメータ                                          │
│  - ヘッダー（設定可能）                                      │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

#### キャッシング設定

```bash
# ステージでキャッシング有効化
aws apigateway update-stage \
  --rest-api-id api123456 \
  --stage-name prod \
  --patch-operations \
    op=replace,path=/cacheClusterEnabled,value=true \
    op=replace,path=/cacheClusterSize,value=0.5

# キャッシュサイズ:
# 0.5GB, 1.6GB, 6.1GB, 13.5GB, 28.4GB, 58.2GB, 118GB, 237GB

# TTL設定（秒）
aws apigateway update-stage \
  --rest-api-id api123456 \
  --stage-name prod \
  --patch-operations \
    op=replace,path=/methodSettings/*/GET~1users/cacheTtlInSeconds,value=300

# クエリパラメータをキャッシュキーに含める
aws apigateway update-method \
  --rest-api-id api123456 \
  --resource-id abc123 \
  --http-method GET \
  --patch-operations \
    op=replace,path=/requestParameters/method.request.querystring.userId,value=true \
    op=replace,path=/cacheKeyParameters,value=method.request.querystring.userId
```

### 5.3 CORS設定

```
┌─────────────────────────────────────────────────────────────┐
│              CORS (Cross-Origin Resource Sharing)            │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ブラウザ (https://example.com)                              │
│      │                                                      │
│      │ OPTIONS /users (プリフライトリクエスト)              │
│      ▼                                                      │
│  API Gateway (https://api.example.com)                      │
│      │                                                      │
│      │ レスポンスヘッダー:                                  │
│      │ Access-Control-Allow-Origin: https://example.com    │
│      │ Access-Control-Allow-Methods: GET,POST,PUT,DELETE   │
│      │ Access-Control-Allow-Headers: Content-Type,Authorization │
│      │ Access-Control-Max-Age: 3600                        │
│      │                                                      │
│      │ 200 OK                                               │
│      ▼                                                      │
│  ブラウザ（CORS許可確認）                                    │
│      │                                                      │
│      │ 実際のリクエスト: GET /users                         │
│      ▼                                                      │
│  API Gateway                                                 │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

#### CORS設定（REST API）

```bash
# OPTIONSメソッド作成（モック統合）
aws apigateway put-method \
  --rest-api-id api123456 \
  --resource-id abc123 \
  --http-method OPTIONS \
  --authorization-type NONE

aws apigateway put-integration \
  --rest-api-id api123456 \
  --resource-id abc123 \
  --http-method OPTIONS \
  --type MOCK \
  --request-templates '{"application/json": "{\"statusCode\": 200}"}'

aws apigateway put-method-response \
  --rest-api-id api123456 \
  --resource-id abc123 \
  --http-method OPTIONS \
  --status-code 200 \
  --response-parameters \
    method.response.header.Access-Control-Allow-Headers=true \
    method.response.header.Access-Control-Allow-Methods=true \
    method.response.header.Access-Control-Allow-Origin=true

aws apigateway put-integration-response \
  --rest-api-id api123456 \
  --resource-id abc123 \
  --http-method OPTIONS \
  --status-code 200 \
  --response-parameters \
    method.response.header.Access-Control-Allow-Headers="'Content-Type,Authorization'" \
    method.response.header.Access-Control-Allow-Methods="'GET,POST,PUT,DELETE'" \
    method.response.header.Access-Control-Allow-Origin="'https://example.com'"
```

#### CORS設定（HTTP API - 簡単）

```bash
# HTTP APIはCORSが簡単に設定可能
aws apigatewayv2 update-api \
  --api-id api-id \
  --cors-configuration \
    AllowOrigins=https://example.com,AllowMethods=GET,POST,PUT,DELETE,AllowHeaders=Content-Type,Authorization,MaxAge=3600
```

---

## 6. CloudFrontとの連携

### 6.1 CloudFront + API Gateway パターン

```
┌─────────────────────────────────────────────────────────────┐
│          CloudFront + API Gateway 構成                       │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  クライアント                                                │
│      │                                                      │
│      │ https://example.com                                  │
│      ▼                                                      │
│  ┌──────────────────────────────────────────────────────┐  │
│  │  CloudFront                                           │  │
│  │  ┌────────────────────────────────────────────────┐  │  │
│  │  │ ビヘイビア1: /api/*                             │  │  │
│  │  │ → API Gateway (オリジン)                        │  │  │
│  │  │ キャッシュ: 無効 or 短時間                       │  │  │
│  │  └────────────────────────────────────────────────┘  │  │
│  │  ┌────────────────────────────────────────────────┐  │  │
│  │  │ ビヘイビア2: /static/*                          │  │  │
│  │  │ → S3 (オリジン)                                 │  │  │
│  │  │ キャッシュ: 長時間                               │  │  │
│  │  └────────────────────────────────────────────────┘  │  │
│  │  ┌────────────────────────────────────────────────┐  │  │
│  │  │ デフォルトビヘイビア: /*                        │  │  │
│  │  │ → S3 (SPA)                                      │  │  │
│  │  └────────────────────────────────────────────────┘  │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                              │
│  利点:                                                       │
│  - 単一ドメインでAPI + 静的コンテンツ配信                    │
│  - WAF統合（DDoS対策、レート制限）                           │
│  - グローバルエッジキャッシング                              │
│  - カスタムSSL証明書                                         │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

#### CloudFrontディストリビューション作成例

```json
// cloudfront-config.json
{
  "CallerReference": "2025-12-26-api-cf",
  "Aliases": {
    "Quantity": 1,
    "Items": ["example.com"]
  },
  "DefaultRootObject": "index.html",
  "Origins": {
    "Quantity": 2,
    "Items": [
      {
        "Id": "api-gateway",
        "DomainName": "api123456.execute-api.us-east-1.amazonaws.com",
        "OriginPath": "/prod",
        "CustomOriginConfig": {
          "HTTPPort": 80,
          "HTTPSPort": 443,
          "OriginProtocolPolicy": "https-only",
          "OriginSslProtocols": {
            "Quantity": 1,
            "Items": ["TLSv1.2"]
          }
        }
      },
      {
        "Id": "s3-static",
        "DomainName": "my-bucket.s3.amazonaws.com",
        "S3OriginConfig": {
          "OriginAccessIdentity": "origin-access-identity/cloudfront/ABCDEFG1234567"
        }
      }
    ]
  },
  "DefaultCacheBehavior": {
    "TargetOriginId": "s3-static",
    "ViewerProtocolPolicy": "redirect-to-https",
    "AllowedMethods": {
      "Quantity": 2,
      "Items": ["GET", "HEAD"]
    },
    "ForwardedValues": {
      "QueryString": false,
      "Cookies": {"Forward": "none"}
    },
    "MinTTL": 0,
    "DefaultTTL": 86400,
    "MaxTTL": 31536000
  },
  "CacheBehaviors": {
    "Quantity": 1,
    "Items": [
      {
        "PathPattern": "/api/*",
        "TargetOriginId": "api-gateway",
        "ViewerProtocolPolicy": "https-only",
        "AllowedMethods": {
          "Quantity": 7,
          "Items": ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
        },
        "ForwardedValues": {
          "QueryString": true,
          "Cookies": {"Forward": "all"},
          "Headers": {
            "Quantity": 3,
            "Items": ["Authorization", "Content-Type", "Accept"]
          }
        },
        "MinTTL": 0,
        "DefaultTTL": 0,
        "MaxTTL": 0
      }
    ]
  },
  "ViewerCertificate": {
    "ACMCertificateArn": "arn:aws:acm:us-east-1:123456789012:certificate/12345678-1234-1234-1234-123456789012",
    "SSLSupportMethod": "sni-only",
    "MinimumProtocolVersion": "TLSv1.2_2021"
  },
  "Comment": "API Gateway + S3 distribution",
  "Enabled": true
}
```

```bash
aws cloudfront create-distribution \
  --distribution-config file://cloudfront-config.json
```

---

## まとめ

### 学んだこと

本章では、API Gatewayのネットワーキング機能を学びました:

- API Gatewayの基本概念と統合タイプ（Lambda、HTTP、VPC Link）
- VPC統合とプライベートAPIの構成
- カスタムドメインとSSL/TLS設定
- 認証・認可（IAM、Cognito、Lambda オーソライザー、JWT）
- スロットリング、キャッシング、CORS設定
- CloudFrontとの連携パターン

### 重要なポイント

```
1. HTTP API vs REST API の選択
   - シンプル、低コスト → HTTP API
   - 豊富な機能 → REST API

2. VPC統合
   - プライベートリソースへのアクセス
   - NLB経由のVPC Link

3. カスタムドメイン
   - エッジ最適化（グローバル）
   - リージョナル（VPC統合、独自CloudFront）

4. 認証の選択
   - AWS内部 → IAM
   - ユーザー認証 → Cognito / JWT
   - カスタムロジック → Lambda オーソライザー

5. パフォーマンス最適化
   - キャッシング（適切なTTL設定）
   - スロットリング（レート制限）
   - CloudFront統合（グローバル配信）
```

### ベストプラクティス

```
□ HTTP APIを優先検討（コスト削減）
□ VPC内リソースはVPC Link経由
□ カスタムドメインでブランディング
□ 適切な認証方式の選択
□ スロットリングで保護
□ CloudWatch Logsで監視
□ X-Rayでトレーシング
□ WAF統合（CloudFront経由）
```

### 次のステップ

- [06-network-troubleshooting.md](./06-network-troubleshooting.md) - API Gatewayのトラブルシューティング
- [content_06_developer-guide/03-application-plane/](../../content_06_developer-guide/03-application-plane/) - idp-serverでのAPI設計

### 参考リンク

- [AWS API Gateway Documentation](https://docs.aws.amazon.com/apigateway/)
- [API Gateway REST API](https://docs.aws.amazon.com/apigateway/latest/developerguide/apigateway-rest-api.html)
- [API Gateway HTTP API](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api.html)
- [VPC Link](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-vpc-links.html)
