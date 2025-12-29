# SSL/TLS証明書管理

## 所要時間
約40分

## 学べること
- SSL/TLS証明書の基本概念とライフサイクル
- 証明書の種類と選び方（DV、OV、EV、ワイルドカード）
- Let's Encryptによる無料証明書の取得と自動更新
- OpenSSLを使用した証明書検証
- 証明書の更新とローテーション戦略
- SSL/TLSのトラブルシューティング

## 前提知識
- [content_11_learning/12-crypto/pki-certificates.md](../12-crypto/pki-certificates.md) - PKIと証明書の基礎
- [content_11_learning/09-http-rest/https-tls-basics.md](../09-http-rest/https-tls-basics.md) - HTTPS/TLS基礎
- DNSの基本理解

---

## 1. SSL/TLS証明書の基礎

### 1.1 証明書の役割

```
┌─────────────────────────────────────────────────────────────┐
│              SSL/TLS証明書の3つの役割                        │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  1. 認証（Authentication）                                   │
│     - サーバーの身元を証明                                   │
│     - 「このサイトは本当にexample.comか?」                   │
│                                                              │
│  2. 暗号化（Encryption）                                     │
│     - 通信内容の保護                                         │
│     - 盗聴防止                                               │
│                                                              │
│  3. 完全性（Integrity）                                      │
│     - データ改ざん検知                                       │
│     - 中間者攻撃（MITM）防止                                 │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 1.2 証明書の構造

```
┌─────────────────────────────────────────────────────────────┐
│              X.509証明書の構造                               │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Certificate:                                                │
│      Data:                                                   │
│          Version: 3 (0x2)                                    │
│          Serial Number: 1234567890abcdef                     │
│          Signature Algorithm: sha256WithRSAEncryption        │
│          Issuer: CN=DigiCert SHA2 Secure Server CA           │
│          Validity                                            │
│              Not Before: Jan 1 00:00:00 2025 GMT             │
│              Not After : Jan 1 23:59:59 2026 GMT             │
│          Subject: CN=example.com                             │
│          Subject Public Key Info:                            │
│              Public Key Algorithm: rsaEncryption             │
│                  Public-Key: (2048 bit)                      │
│                  Modulus: 00:c0:ff:ee:...                    │
│                  Exponent: 65537 (0x10001)                   │
│          X509v3 extensions:                                  │
│              X509v3 Subject Alternative Name:                │
│                  DNS:example.com, DNS:www.example.com        │
│              X509v3 Key Usage:                               │
│                  Digital Signature, Key Encipherment         │
│              X509v3 Extended Key Usage:                      │
│                  TLS Web Server Authentication               │
│      Signature Algorithm: sha256WithRSAEncryption            │
│          12:34:56:78:9a:bc:de:f0:...                         │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

**重要フィールド**:

```
Subject (CN - Common Name)
  - 証明書の主体（ドメイン名）
  - 例: CN=example.com

Issuer
  - 発行者（認証局）
  - 例: CN=Let's Encrypt Authority X3

Validity (有効期間)
  - Not Before: 有効開始日時
  - Not After: 有効終了日時

Subject Alternative Name (SAN)
  - 複数のドメイン名を含められる
  - 例: example.com, www.example.com, api.example.com
```

---

## 2. 証明書の種類

### 2.1 検証レベル別の分類

```
┌─────────────────────────────────────────────────────────────┐
│              証明書の検証レベル                              │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  1. DV (Domain Validation) 証明書                           │
│     - ドメイン所有権のみ検証                                 │
│     - 数分〜数時間で発行                                     │
│     - 最も安価（無料のものも多い）                           │
│     - Let's Encrypt、ZeroSSL等                              │
│     用途: 個人サイト、開発環境、ほとんどのWebサイト          │
│                                                              │
│  2. OV (Organization Validation) 証明書                     │
│     - ドメイン所有権 + 組織の実在性を検証                    │
│     - 数日で発行                                             │
│     - 中程度の価格                                           │
│     用途: 企業サイト、Eコマース                              │
│                                                              │
│  3. EV (Extended Validation) 証明書                         │
│     - 最も厳格な検証（法人登記等の確認）                     │
│     - 数日〜数週間で発行                                     │
│     - 高価                                                   │
│     - ブラウザのアドレスバーに組織名表示（古いブラウザ）     │
│     用途: 金融機関、大規模Eコマース                          │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 スコープ別の分類

```
┌─────────────────────────────────────────────────────────────┐
│              証明書のスコープ                                │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  1. 単一ドメイン証明書                                       │
│     - 1つのFQDNのみ                                         │
│     - 例: example.com                                       │
│     - www.example.com は別証明書が必要                      │
│                                                              │
│  2. ワイルドカード証明書                                     │
│     - 1階層のサブドメインをカバー                            │
│     - 例: *.example.com                                     │
│     - カバー: www.example.com, api.example.com              │
│     - 非カバー: example.com, sub.api.example.com            │
│                                                              │
│  3. マルチドメイン証明書 (SAN証明書)                         │
│     - 複数の異なるドメインをカバー                           │
│     - 例: example.com, example.org, example.net             │
│     - SANフィールドに複数のドメインを記載                    │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 2.3 証明書選択のガイドライン

```
シナリオ別の推奨証明書:

┌────────────────────┬──────────────┬─────────────────┐
│ ユースケース       │ 推奨証明書    │ 理由             │
├────────────────────┼──────────────┼─────────────────┤
│ 個人ブログ         │ DV           │ コスト、簡便性   │
│                    │ (Let's Encrypt)│                 │
├────────────────────┼──────────────┼─────────────────┤
│ 企業Webサイト      │ DV or OV     │ 信頼性とコスト   │
│                    │              │ のバランス       │
├────────────────────┼──────────────┼─────────────────┤
│ Eコマース          │ OV or EV     │ 顧客信頼         │
├────────────────────┼──────────────┼─────────────────┤
│ 金融機関           │ EV           │ 最高レベルの信頼 │
├────────────────────┼──────────────┼─────────────────┤
│ 多数のサブドメイン │ ワイルドカード│ 管理簡略化       │
└────────────────────┴──────────────┴─────────────────┘
```

---

## 3. 証明書の取得方法

### 3.1 証明書取得の選択肢

SSL/TLS証明書を取得する一般的な方法:

```
┌─────────────────────────────────────────────────────────────┐
│              証明書取得方法の比較                             │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  1. 商用認証局（CA）                                         │
│     - DigiCert、GlobalSign、Sectigo等                      │
│     - 有料（年間数千円〜数万円）                             │
│     - OV/EV証明書が必要な場合                                │
│     - サポートが充実                                         │
│                                                              │
│  2. Let's Encrypt（無料CA）                                 │
│     - 完全無料のDV証明書                                     │
│     - 自動化対応（ACME プロトコル）                          │
│     - 有効期間90日（自動更新推奨）                           │
│     - 個人サイト、開発環境に最適                             │
│                                                              │
│  3. 自己署名証明書                                           │
│     - OpenSSLで自作                                          │
│     - 開発/テスト環境のみ                                    │
│     - ブラウザ警告が表示される                               │
│     - 本番環境では使用不可                                   │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 自己署名証明書の作成（開発/テスト用）

OpenSSLを使用して自己署名証明書を作成する方法:

```bash
# 1. 秘密鍵の生成
openssl genrsa -out server.key 2048

# 2. CSR（証明書署名要求）の生成
openssl req -new -key server.key -out server.csr
# 対話形式で以下を入力:
# Country Name (2 letter code) []:JP
# State or Province Name []:Tokyo
# Locality Name []:Minato
# Organization Name []:Example Corp
# Organizational Unit Name []:IT Department
# Common Name []:example.com
# Email Address []:admin@example.com

# 3. 自己署名証明書の生成（有効期間365日）
openssl x509 -req -days 365 -in server.csr -signkey server.key -out server.crt

# 4. ワンライナーで生成（対話なし）
openssl req -x509 -newkey rsa:2048 -keyout server.key -out server.crt -days 365 -nodes \
  -subj "/C=JP/ST=Tokyo/L=Minato/O=Example Corp/OU=IT/CN=example.com"

# 5. SANを含む証明書の生成（複数ドメイン対応）
cat > san.cnf <<EOF
[req]
distinguished_name = req_distinguished_name
req_extensions = v3_req
prompt = no

[req_distinguished_name]
C = JP
ST = Tokyo
L = Minato
O = Example Corp
OU = IT
CN = example.com

[v3_req]
subjectAltName = @alt_names

[alt_names]
DNS.1 = example.com
DNS.2 = www.example.com
DNS.3 = api.example.com
EOF

openssl req -x509 -newkey rsa:2048 -keyout server.key -out server.crt -days 365 -nodes -config san.cnf -extensions v3_req

# 注意: 自己署名証明書は開発/テスト専用。本番環境では使用しないこと
```

---

## 4. Let's Encryptによる証明書取得

### 4.1 Let's Encryptの概要

**Let's Encrypt**は、無料でDV証明書を発行する認証局（CA）です。

```
特徴:
- 完全無料
- 自動化対応（ACME プロトコル）
- 有効期間: 90日（短い → 自動更新必須）
- DV証明書のみ
- ワイルドカード証明書対応（DNS-01チャレンジ）
```

### 4.2 Certbotによる証明書取得

**Certbot**は、Let's Encryptの公式クライアントです。

#### インストール

```bash
# Ubuntu/Debian
sudo apt update
sudo apt install certbot python3-certbot-nginx

# Amazon Linux 2
sudo yum install certbot python3-certbot-nginx

# macOS
brew install certbot
```

#### HTTP-01チャレンジ（Webサーバー必要）

```bash
# Nginx使用時
sudo certbot --nginx -d example.com -d www.example.com

# 実行時の質問:
# - メールアドレス（更新通知用）
# - 利用規約への同意
# - HTTP→HTTPSリダイレクトの設定

# 証明書の保存場所:
# /etc/letsencrypt/live/example.com/fullchain.pem  # 証明書
# /etc/letsencrypt/live/example.com/privkey.pem    # 秘密鍵
# /etc/letsencrypt/live/example.com/chain.pem      # 中間証明書
# /etc/letsencrypt/live/example.com/cert.pem       # サーバー証明書のみ
```

#### DNS-01チャレンジ（ワイルドカード証明書）

```bash
# ワイルドカード証明書の取得
sudo certbot certonly \
  --manual \
  --preferred-challenges dns \
  -d *.example.com \
  -d example.com

# 実行すると、TXTレコードの追加を求められる:
# Please deploy a DNS TXT record under the name
# _acme-challenge.example.com with the following value:
#
# 1234567890abcdefghijklmnopqrstuvwxyzABCDEF
#
# Before continuing, verify the record is deployed.

# DNSプロバイダーの管理画面またはAPIでTXTレコードを追加
# 例: TXTレコード
#   名前: _acme-challenge.example.com
#   値: 1234567890abcdefghijklmnopqrstuvwxyzABCDEF
#   TTL: 60

# DNS反映確認
dig _acme-challenge.example.com TXT +short

# Enterキーで続行
```

#### DNS API プラグイン（自動化）

多くのDNSプロバイダーがCertbot用のプラグインを提供しています。

```bash
# DNSプロバイダーのプラグインをインストール（例）
# Cloudflare: sudo pip3 install certbot-dns-cloudflare
# DigitalOcean: sudo pip3 install certbot-dns-digitalocean
# その他多数のDNSプロバイダー対応プラグインあり

# DNS APIの認証情報を設定（プラグインにより異なる）
# 例: ~/.secrets/cloudflare.ini に認証トークンを保存

# 証明書取得（DNS-01自動化）
sudo certbot certonly \
  --dns-cloudflare \
  --dns-cloudflare-credentials ~/.secrets/cloudflare.ini \
  -d *.example.com \
  -d example.com

# 完全自動化（DNS検証レコードの追加/削除も自動）
```

### 4.3 証明書の自動更新

```bash
# 更新のテスト（dry-run）
sudo certbot renew --dry-run

# 自動更新の設定（cron）
sudo crontab -e

# 毎日午前2時に更新チェック
0 2 * * * certbot renew --quiet --post-hook "systemctl reload nginx"

# systemd timer使用（推奨）
sudo systemctl enable certbot-renew.timer
sudo systemctl start certbot-renew.timer

# タイマー確認
sudo systemctl list-timers | grep certbot
```

### 4.4 Nginx設定例

```nginx
# /etc/nginx/sites-available/example.com

server {
    listen 80;
    server_name example.com www.example.com;

    # Let's Encrypt HTTP-01チャレンジ用
    location /.well-known/acme-challenge/ {
        root /var/www/html;
    }

    # その他のHTTPリクエストはHTTPSにリダイレクト
    location / {
        return 301 https://$server_name$request_uri;
    }
}

server {
    listen 443 ssl http2;
    server_name example.com www.example.com;

    # Let's Encrypt証明書
    ssl_certificate /etc/letsencrypt/live/example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/example.com/privkey.pem;

    # SSL設定（強固な暗号化）
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers 'ECDHE-RSA-AES128-GCM-SHA256:ECDHE-RSA-AES256-GCM-SHA384';
    ssl_prefer_server_ciphers on;
    ssl_session_cache shared:SSL:10m;
    ssl_session_timeout 10m;

    # HSTS（HTTP Strict Transport Security）
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

---

## 5. 証明書の検証とデバッグ

### 5.1 OpenSSLによる証明書検証

```bash
# 証明書の内容確認
openssl x509 -in certificate.pem -text -noout

# 秘密鍵の確認
openssl rsa -in private-key.pem -check

# 証明書と秘密鍵の一致確認
cert_modulus=$(openssl x509 -noout -modulus -in certificate.pem | openssl md5)
key_modulus=$(openssl rsa -noout -modulus -in private-key.pem | openssl md5)

if [ "$cert_modulus" = "$key_modulus" ]; then
    echo "証明書と秘密鍵は一致しています"
else
    echo "証明書と秘密鍵が一致しません"
fi

# 証明書チェーンの検証
openssl verify -CAfile certificate-chain.pem certificate.pem
```

---

## 6. 証明書のライフサイクル管理

### 6.1 証明書更新戦略

```
┌─────────────────────────────────────────────────────────────┐
│              証明書更新のベストプラクティス                   │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  【計画的更新】                                              │
│  1. 有効期限の60日前にアラート                               │
│  2. 有効期限の30日前に更新開始                               │
│  3. 有効期限の7日前に最終確認                                │
│                                                              │
│  【Let's Encrypt（自動更新）】                               │
│  - certbot renew の自動化（cron/systemd timer）             │
│  - 有効期限30日前から更新可能                                │
│  - 更新後のサービス再起動（--post-hook）                     │
│                                                              │
│  【外部CA証明書】                                            │
│  - 手動更新プロセスの文書化                                  │
│  - EventBridgeアラート設定                                   │
│  - インポート証明書の更新手順確立                            │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 6.2 証明書有効期限の監視

#### スクリプトによる監視

```bash
#!/bin/bash
# check-certificate-expiry.sh

DOMAIN="example.com"
WARN_DAYS=30

# 証明書の有効期限取得
expiry_date=$(echo | openssl s_client -servername $DOMAIN -connect $DOMAIN:443 2>/dev/null | \
              openssl x509 -noout -enddate | cut -d= -f2)

# エポック秒に変換
expiry_epoch=$(date -d "$expiry_date" +%s)
current_epoch=$(date +%s)
days_until_expiry=$(( ($expiry_epoch - $current_epoch) / 86400 ))

echo "証明書の有効期限: $expiry_date"
echo "残り日数: $days_until_expiry 日"

if [ $days_until_expiry -lt $WARN_DAYS ]; then
    echo "警告: 証明書の有効期限が近づいています！"
    # アラート送信（Slack、メール等）
    exit 1
else
    echo "証明書は正常です"
    exit 0
fi
```

---

## 7. SSL/TLSのトラブルシューティング

### 7.1 よくあるエラーと対処法

#### エラー1: "NET::ERR_CERT_COMMON_NAME_INVALID"

```
原因: 証明書のCN/SANとアクセスURLが一致しない

例:
証明書: CN=example.com, SAN=example.com, www.example.com
アクセス: https://api.example.com  ← SANに含まれていない

対処法:
1. ワイルドカード証明書を使用（*.example.com）
2. SANにapi.example.comを追加した証明書を再発行
3. SNI（Server Name Indication）で複数証明書を設定
```

#### エラー2: "NET::ERR_CERT_DATE_INVALID"

```
原因: 証明書の有効期限切れ、または開始前

対処法:
1. 証明書の有効期限を確認
   openssl x509 -in cert.pem -noout -dates

2. サーバーの時刻を確認（NTP同期）
   date
   timedatectl status

3. 証明書を更新
```

#### エラー3: "NET::ERR_CERT_AUTHORITY_INVALID"

```
原因: 証明書チェーンが不完全、または信頼できないCA

対処法:
1. 中間証明書を含める
   cat server.crt intermediate.crt > fullchain.crt

2. ルート証明書がクライアントに信頼されているか確認

3. Let's Encryptの場合、fullchain.pemを使用
   ssl_certificate /etc/letsencrypt/live/example.com/fullchain.pem;
```

### 7.2 診断ツール

```bash
# 1. OpenSSL s_client（詳細な接続情報）
openssl s_client -connect example.com:443 -servername example.com

# 証明書チェーン表示
openssl s_client -showcerts -connect example.com:443 </dev/null

# 特定のTLSバージョンをテスト
openssl s_client -connect example.com:443 -tls1_2
openssl s_client -connect example.com:443 -tls1_3

# 2. 証明書の詳細確認
echo | openssl s_client -servername example.com -connect example.com:443 2>/dev/null | \
  openssl x509 -noout -text

# 3. 有効期限確認
echo | openssl s_client -servername example.com -connect example.com:443 2>/dev/null | \
  openssl x509 -noout -dates

# 4. SSL Labs（オンラインツール）
# https://www.ssllabs.com/ssltest/analyze.html?d=example.com

# 5. testssl.sh（包括的なテストツール）
git clone https://github.com/drwetter/testssl.sh.git
cd testssl.sh
./testssl.sh example.com
```

---

## まとめ

### 学んだこと

本章では、SSL/TLS証明書の実践的な管理方法を学びました:

- SSL/TLS証明書の役割と構造
- 証明書の種類（DV、OV、EV、ワイルドカード）と選択基準
- 商用CA、Let's Encrypt、自己署名証明書の使い分け
- Let's Encryptによる証明書取得とCertbotの使用
- OpenSSLを使用した証明書検証
- 証明書のライフサイクル管理と更新戦略
- SSL/TLSのトラブルシューティング手法

### 重要なポイント

```
1. 本番環境での証明書選択
   - 商用サイト: Let's Encrypt（無料DV証明書）
   - 企業サイト: 商用CA（OV/EV証明書）
   - 開発/テスト: 自己署名証明書

2. Let's Encryptの運用
   - 有効期限90日（短い）
   - certbot renewの自動化必須（cron/systemd timer）
   - DNS-01チャレンジでワイルドカード証明書

3. 証明書監視の徹底
   - 有効期限の30日前にアラート
   - cronによる自動監視スクリプト
   - 証明書チェーンの検証

4. セキュリティ設定
   - TLSv1.2以上のみ許可
   - 強固な暗号スイート設定
   - HSTS有効化
```

### チェックリスト

```
□ Let's Encrypt自動更新の設定（cron/systemd timer）
□ 証明書有効期限の監視スクリプト配置
□ ワイルドカード証明書の適切な利用
□ 証明書と秘密鍵の安全な保管（パーミッション600）
□ TLS設定の定期的な見直し（SSL Labs等）
□ 証明書チェーンの完全性確認
```

### 次のステップ

- [05-api-gateway-networking.md](./05-api-gateway-networking.md) - リバースプロキシとAPI Gateway
- [06-network-troubleshooting.md](./06-network-troubleshooting.md) - ネットワークトラブルシューティング
- [content_11_learning/12-crypto/tls-ssl.md](../12-crypto/tls-ssl.md) - TLS/SSLプロトコル詳細

### 参考リンク

- [Let's Encrypt Documentation](https://letsencrypt.org/docs/)
- [Certbot Documentation](https://certbot.eff.org/docs/)
- [OpenSSL Documentation](https://www.openssl.org/docs/)
- [SSL Labs](https://www.ssllabs.com/)
- [Mozilla SSL Configuration Generator](https://ssl-config.mozilla.org/)
