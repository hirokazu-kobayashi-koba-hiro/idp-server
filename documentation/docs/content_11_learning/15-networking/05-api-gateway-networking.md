# リバースプロキシとAPI Gateway

## 所要時間
約35分

## 学べること
- リバースプロキシの基本概念と役割
- Nginxを使用したリバースプロキシ設定
- API Gatewayパターンの一般的な実装
- レート制限、キャッシング、認証の実装
- CORSの設定と対応

## 前提知識
- [01-dns-fundamentals.md](./01-dns-fundamentals.md) - DNS基礎
- [03-load-balancing.md](./03-load-balancing.md) - ロードバランシング
- [04-ssl-tls-certificates.md](./04-ssl-tls-certificates.md) - SSL/TLS証明書
- HTTP/RESTの基本

---

## 1. リバースプロキシとAPI Gatewayの基礎

### 1.1 リバースプロキシとは

**リバースプロキシ**は、クライアントとバックエンドサーバーの間に配置され、クライアントからのリクエストを代理で受け取り、適切なバックエンドに転送する役割を持ちます。

```
┌─────────────────────────────────────────────────────────────┐
│              リバースプロキシの役割                           │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  クライアント（ブラウザ、モバイルアプリ等）                   │
│      │                                                      │
│      │ HTTPS リクエスト                                     │
│      ▼                                                      │
│  ┌──────────────────────────────────────────────────────┐  │
│  │           リバースプロキシ (Nginx/HAProxy)           │  │
│  │  ┌────────────────────────────────────────────────┐  │  │
│  │  │ 1. 認証・認可                                  │  │  │
│  │  │ 2. リクエスト検証                              │  │  │
│  │  │ 3. レート制限（スロットリング）                 │  │  │
│  │  │ 4. キャッシング                                │  │  │
│  │  │ 5. SSL/TLS終端                                 │  │  │
│  │  │ 6. ロギング・モニタリング                      │  │  │
│  │  └────────────────────────────────────────────────┘  │  │
│  └──────────────────────────────────────────────────────┘  │
│      │                                                      │
│      ├───► Webアプリケーションサーバー                      │
│      ├───► APIサーバー                                      │
│      ├───► マイクロサービス群                               │
│      └───► 静的コンテンツサーバー                           │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 API Gatewayパターン

**API Gateway**は、マイクロサービスアーキテクチャにおいて、クライアントと複数のバックエンドサービスの間に配置される単一のエントリーポイントです。

```
┌─────────────────────────────────────────────────────────────┐
│              API Gateway パターンの利点                      │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  利点:                                                       │
│  1. 単一エントリーポイント                                   │
│     - クライアントは1つのエンドポイントのみ知る必要         │
│                                                              │
│  2. プロトコル変換                                           │
│     - 外部: HTTPS/REST → 内部: gRPC/HTTP                    │
│                                                              │
│  3. リクエスト集約                                           │
│     - 複数のバックエンド呼び出しを1つに集約                 │
│                                                              │
│  4. 認証・認可の一元化                                       │
│     - JWT検証、APIキー管理                                  │
│                                                              │
│  5. レート制限・スロットリング                               │
│     - サービス保護                                           │
│                                                              │
│  6. キャッシング                                             │
│     - バックエンド負荷軽減                                   │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. Nginxによるリバースプロキシ設定

### 2.1 基本的なリバースプロキシ設定

Nginxを使用したシンプルなリバースプロキシ設定:

```nginx
# /etc/nginx/sites-available/api-gateway

server {
    listen 443 ssl http2;
    server_name api.example.com;

    # SSL/TLS設定
    ssl_certificate /etc/letsencrypt/live/api.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/api.example.com/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers 'ECDHE-RSA-AES128-GCM-SHA256:ECDHE-RSA-AES256-GCM-SHA384';

    # ロギング
    access_log /var/log/nginx/api-access.log;
    error_log /var/log/nginx/api-error.log;

    # バックエンドサーバーへのプロキシ
    location / {
        proxy_pass http://backend-app:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # タイムアウト設定
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }
}
```

### 2.2 パスベースルーティング

複数のバックエンドサービスへのルーティング:

```nginx
server {
    listen 443 ssl http2;
    server_name api.example.com;

    # /api/v1/* → API v1サーバー
    location /api/v1/ {
        proxy_pass http://api-v1-backend:8080/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # /api/v2/* → API v2サーバー
    location /api/v2/ {
        proxy_pass http://api-v2-backend:8080/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # /auth/* → 認証サービス
    location /auth/ {
        proxy_pass http://auth-service:9000/;
        proxy_set_header Host $host;
    }

    # /static/* → 静的コンテンツ（CDN/S3等）
    location /static/ {
        proxy_pass http://cdn.example.com/;
        proxy_cache my_cache;
        proxy_cache_valid 200 1h;
    }
}
```

---

## 3. 認証とセキュリティ

### 3.1 基本認証（Basic Authentication）

最もシンプルな認証方式:

```nginx
# /etc/nginx/sites-available/api-gateway

server {
    listen 443 ssl http2;
    server_name api.example.com;

    location /api/ {
        # Basic認証
        auth_basic "API Access";
        auth_basic_user_file /etc/nginx/.htpasswd;

        proxy_pass http://backend-api:8080/;
        proxy_set_header Host $host;
    }
}
```

```bash
# .htpasswdファイル作成
sudo apt-get install apache2-utils
sudo htpasswd -c /etc/nginx/.htpasswd username
```

### 3.2 APIキー認証

カスタムヘッダーによるAPIキー検証:

```nginx
server {
    listen 443 ssl http2;
    server_name api.example.com;

    location /api/ {
        # APIキーチェック
        if ($http_x_api_key = "") {
            return 401 "API Key Required";
        }

        # APIキー検証（簡易版）
        set $valid_key 0;
        if ($http_x_api_key = "your-secret-api-key") {
            set $valid_key 1;
        }

        if ($valid_key = 0) {
            return 403 "Invalid API Key";
        }

        proxy_pass http://backend-api:8080/;
        proxy_set_header Host $host;
        proxy_set_header X-API-Key $http_x_api_key;
    }
}
```

### 3.3 JWT認証

JWT（JSON Web Token）による認証（nginx-jwt モジュール使用）:

```nginx
# JWT検証の概念（実装にはnginx-jwtモジュールまたはLuaスクリプトが必要）

server {
    listen 443 ssl http2;
    server_name api.example.com;

    location /api/ {
        # Authorizationヘッダーからトークン取得
        # JWT検証ロジック（署名検証、有効期限確認）
        # ※実装詳細はnginx-jwtモジュールまたはLuaスクリプト参照

        # 検証成功後、バックエンドにプロキシ
        proxy_pass http://backend-api:8080/;
        proxy_set_header Host $host;
        proxy_set_header X-User-ID $jwt_claim_sub;  # JWTのsubクレームを渡す
    }
}
```

**JWT検証の一般的な流れ:**

```
1. クライアントがAuthorizationヘッダーでJWTを送信
   Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...

2. リバースプロキシでJWT検証
   - 署名検証（HMAC/RSA）
   - 有効期限確認（exp claim）
   - Issuer確認（iss claim）

3. 検証成功
   - バックエンドにリクエスト転送
   - ユーザー情報をヘッダーに追加

4. 検証失敗
   - 401 Unauthorizedを返す
```

---

## 4. レート制限とスロットリング

### 4.1 Nginxによるレート制限

リクエスト数を制限してサービスを保護:

```nginx
# /etc/nginx/nginx.conf

http {
    # レート制限ゾーン定義
    # クライアントIPごとに10MB、1秒あたり10リクエストまで
    limit_req_zone $binary_remote_addr zone=api_limit:10m rate=10r/s;

    # APIキーごとのレート制限
    limit_req_zone $http_x_api_key zone=apikey_limit:10m rate=100r/s;

    server {
        listen 443 ssl http2;
        server_name api.example.com;

        location /api/ {
            # レート制限適用（burst=20で一時的な超過を許可）
            limit_req zone=api_limit burst=20 nodelay;

            # 429 Too Many Requestsのカスタムレスポンス
            limit_req_status 429;

            proxy_pass http://backend-api:8080/;
            proxy_set_header Host $host;
        }

        # プレミアムAPIエンドポイント（高レート制限）
        location /api/premium/ {
            limit_req zone=apikey_limit burst=50 nodelay;

            proxy_pass http://backend-api:8080/premium/;
            proxy_set_header Host $host;
        }
    }
}
```

### 4.2 接続数制限

同時接続数の制限:

```nginx
http {
    # 接続数制限ゾーン
    limit_conn_zone $binary_remote_addr zone=conn_limit:10m;

    server {
        listen 443 ssl http2;
        server_name api.example.com;

        location /api/ {
            # クライアントIPごとに10接続まで
            limit_conn conn_limit 10;

            proxy_pass http://backend-api:8080/;
        }
    }
}
```

---

## 4. 認証・認可

### 4.1 認証方式の種類

```
┌─────────────────────────────────────────────────────────────┐
│          API Gateway 認証方式                                │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  1. Basic認証                                                │
│     - HTTPヘッダーでユーザー名/パスワードを送信              │
│     - シンプルだが、HTTPS必須                                │
│                                                              │
│  2. APIキー認証                                              │
│     - リクエストヘッダーまたはクエリパラメータでキー送信      │
│     - シンプルなアクセス制御                                 │
│     - 使用量制限と組み合わせ                                 │
│                                                              │
│  3. JWT（JSON Web Token）認証                               │
│     - JWTトークンの検証                                      │
│     - OAuth 2.0 / OIDC統合                                  │
│     - ステートレスな認証                                     │
│                                                              │
│  4. OAuth 2.0                                               │
│     - アクセストークンベースの認証                            │
│     - 外部認証プロバイダー連携                               │
│     - スコープベースのアクセス制御                            │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```


---

## 5. キャッシングとCORS

### 5.1 Nginxキャッシング

レスポンスをキャッシュしてバックエンド負荷を軽減:

```nginx
# /etc/nginx/nginx.conf

http {
    # キャッシュパス定義
    proxy_cache_path /var/cache/nginx levels=1:2 keys_zone=api_cache:10m max_size=1g inactive=60m use_temp_path=off;

    server {
        listen 443 ssl http2;
        server_name api.example.com;

        location /api/public/ {
            # キャッシュ有効化
            proxy_cache api_cache;

            # キャッシュキー（URL + クエリパラメータ）
            proxy_cache_key "$scheme$request_method$host$request_uri";

            # ステータスコード200の場合、5分間キャッシュ
            proxy_cache_valid 200 5m;

            # キャッシュステータスをヘッダーに追加（デバッグ用）
            add_header X-Cache-Status $upstream_cache_status;

            # バックエンドへプロキシ
            proxy_pass http://backend-api:8080/public/;
            proxy_set_header Host $host;
        }

        # 認証が必要なエンドポイント（キャッシュなし）
        location /api/private/ {
            proxy_pass http://backend-api:8080/private/;
            proxy_set_header Host $host;
            proxy_set_header Authorization $http_authorization;

            # キャッシュ無効化
            proxy_no_cache 1;
            proxy_cache_bypass 1;
        }
    }
}
```

### 5.2 CORS設定

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

#### NginxでのCORS設定

```nginx
server {
    listen 443 ssl http2;
    server_name api.example.com;

    location /api/ {
        # プリフライトリクエスト（OPTIONS）への応答
        if ($request_method = 'OPTIONS') {
            add_header 'Access-Control-Allow-Origin' 'https://example.com' always;
            add_header 'Access-Control-Allow-Methods' 'GET, POST, PUT, DELETE, OPTIONS' always;
            add_header 'Access-Control-Allow-Headers' 'Content-Type, Authorization, X-API-Key' always;
            add_header 'Access-Control-Max-Age' 3600 always;
            add_header 'Content-Type' 'text/plain; charset=utf-8' always;
            add_header 'Content-Length' 0 always;
            return 204;
        }

        # 実際のリクエストへのCORSヘッダー追加
        add_header 'Access-Control-Allow-Origin' 'https://example.com' always;
        add_header 'Access-Control-Allow-Credentials' 'true' always;

        proxy_pass http://backend-api:8080/;
        proxy_set_header Host $host;
    }
}
```

#### 複数オリジンへの対応

```nginx
# 複数のオリジンを許可する場合

map $http_origin $cors_origin {
    default "";
    "~^https://example\.com$" $http_origin;
    "~^https://app\.example\.com$" $http_origin;
    "~^http://localhost:3000$" $http_origin;
}

server {
    listen 443 ssl http2;
    server_name api.example.com;

    location /api/ {
        if ($request_method = 'OPTIONS') {
            add_header 'Access-Control-Allow-Origin' $cors_origin always;
            add_header 'Access-Control-Allow-Methods' 'GET, POST, PUT, DELETE, OPTIONS' always;
            add_header 'Access-Control-Allow-Headers' 'Content-Type, Authorization' always;
            add_header 'Access-Control-Max-Age' 3600 always;
            return 204;
        }

        add_header 'Access-Control-Allow-Origin' $cors_origin always;

        proxy_pass http://backend-api:8080/;
    }
}
```

---

## 6. ログとモニタリング

### 6.1 アクセスログ

Nginxのアクセスログでリクエストを追跡:

```nginx
http {
    # カスタムログフォーマット
    log_format api_log '$remote_addr - $remote_user [$time_local] '
                       '"$request" $status $body_bytes_sent '
                       '"$http_referer" "$http_user_agent" '
                       '$request_time $upstream_response_time '
                       '"$http_x_api_key"';

    server {
        listen 443 ssl http2;
        server_name api.example.com;

        # アクセスログ
        access_log /var/log/nginx/api-access.log api_log;

        # エラーログ
        error_log /var/log/nginx/api-error.log warn;

        location /api/ {
            proxy_pass http://backend-api:8080/;
            proxy_set_header Host $host;
        }
    }
}
```

### 6.2 メトリクス収集

リクエストメトリクスの収集（Prometheusとの統合例）:

```nginx
# nginx-module-vts または nginx-prometheus-exporter を使用

server {
    listen 9113;  # メトリクスエンドポイント
    server_name localhost;

    location /metrics {
        # Prometheusメトリクスエクスポート
        # nginx-module-vtsの場合: vhost_traffic_status_display;
        # nginx-prometheus-exporterの場合: stub_status;
        stub_status;
    }
}
```

---

## まとめ

### 学んだこと

本章では、リバースプロキシとAPI Gatewayパターンを学びました:

- リバースプロキシとAPI Gatewayパターンの基本概念
- Nginxを使用したリバースプロキシ設定
- パスベースルーティングの実装
- 認証方式（Basic認証、APIキー、JWT）
- レート制限とスロットリングの実装
- キャッシングによるパフォーマンス最適化
- CORS設定
- ログとモニタリング

### 重要なポイント

```
1. リバースプロキシの役割
   - 単一エントリーポイント
   - SSL/TLS終端
   - 認証・認可の一元化
   - レート制限とキャッシング

2. パスベースルーティング
   - /api/v1/* → APIv1サーバー
   - /api/v2/* → APIv2サーバー
   - /static/* → 静的コンテンツ
   - マイクロサービス統合に有効

3. セキュリティ
   - Basic認証（シンプル）
   - APIキー認証（中間）
   - JWT認証（推奨）

4. パフォーマンス最適化
   - レート制限でサービス保護
   - キャッシングでバックエンド負荷軽減
   - 適切なTTL設定

5. CORS設定
   - プリフライトリクエスト対応
   - 複数オリジンサポート
```

### ベストプラクティス

```
□ HTTPS/TLS必須（Let's Encryptで証明書取得）
□ 適切な認証方式の選択（JWT推奨）
□ レート制限でDDoS対策
□ キャッシングでパフォーマンス向上
□ CORSを適切に設定
□ アクセスログで監視
□ メトリクス収集（Prometheus等）
```

### 次のステップ

- [06-network-troubleshooting.md](./06-network-troubleshooting.md) - ネットワークトラブルシューティング
- [03-load-balancing.md](./03-load-balancing.md) - ロードバランシングの詳細

### 参考リンク

- [Nginx Documentation](https://nginx.org/en/docs/)
- [Nginx Reverse Proxy](https://docs.nginx.com/nginx/admin-guide/web-server/reverse-proxy/)
- [HAProxy Documentation](https://www.haproxy.org/documentation.html)
- [CORS MDN Documentation](https://developer.mozilla.org/en-US/docs/Web/HTTP/CORS)
