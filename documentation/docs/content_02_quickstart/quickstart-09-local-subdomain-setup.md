# ローカルサブドメイン開発環境セットアップ

本番環境に近いサブドメイン構成でローカル開発を行うための手順です。

## 構成

```
┌─────────────────────────────────────────────────────────┐
│                   local.test                             │
│                                                         │
│   https://api.local.test   →  IDP Server（バックエンドAPI）│
│   https://auth.local.test  →  App View（認可画面）       │
│                                                         │
│   cookie_domain: "local.test"                           │
│   → サブドメイン間でクッキー共有                         │
│   → Safari/iOS でも動作                                 │
└─────────────────────────────────────────────────────────┘
```

## 前提条件

- macOS
- Homebrew
- Docker / Docker Compose

## セットアップ手順

### 1. dnsmasq のインストールと設定

```bash
# インストール
brew install dnsmasq

# local.test → 127.0.0.1 の設定を追加
echo 'address=/local.test/127.0.0.1' >> /opt/homebrew/etc/dnsmasq.conf

# macOS resolver 設定
sudo mkdir -p /etc/resolver
echo "nameserver 127.0.0.1" | sudo tee /etc/resolver/local.test

# dnsmasq サービス起動
sudo brew services start dnsmasq
```

### 2. DNS解決の確認

```bash
ping auth.local.test
# PING auth.local.test (127.0.0.1): 56 data bytes
```

### 3. mkcert のインストールとSSL証明書生成

```bash
# インストール
brew install mkcert

# ローカルCAをシステムに登録
mkcert -install

# SSL証明書を生成
cd docker/nginx/certs
mkcert "*.local.test" local.test
```

生成されるファイル：
- `_wildcard.local.test+1.pem` - 証明書
- `_wildcard.local.test+1-key.pem` - 秘密鍵

### 4. 環境変数の設定

```bash
cp .env.example .env
# .env ファイルを編集して必要な値を設定
```

### 5. ビルドと起動

```bash
# ビルド
docker-compose build

# 起動
docker-compose up -d
```

### 6. 動作確認

ブラウザで以下にアクセス：

- IDP Server API: https://api.local.test
- 認可画面: https://auth.local.test

## テナント設定

サブドメイン構成では、テナントの `session_config` に `cookie_domain` を設定します：

```json
{
  "session_config": {
    "cookie_domain": "local.test",
    "cookie_same_site": "Lax",
    "use_secure_cookie": true,
    "timeout_seconds": 3600
  }
}
```

設定例: `config/examples/subdomain-oidc-web-app/onboarding-request.json`

## 証明書の全体像

ローカル環境では**3つの独立したCA**が存在し、それぞれ異なる用途で証明書を発行しています。

```
┌─────────────────────────────────────────────────────────────────┐
│                  CA #1: mkcert CA（サーバー証明書用）              │
│  ファイル: docker/nginx/certs/rootCA.pem                         │
│  生成方法: mkcert -install（macOSシステム信頼ストアに自動登録）     │
│                                                                 │
│  発行する証明書:                                                  │
│    ├─ _wildcard.local.test+1.pem   … *.local.test サーバー証明書 │
│    └─ _wildcard.idp.local+1.pem    … *.idp.local サーバー証明書  │
│                                                                 │
│  用途: ブラウザ・curlがHTTPSで信頼できるようにする                  │
│  再生成: scripts/setup-local-subdomain.sh を実行                  │
├─────────────────────────────────────────────────────────────────┤
│              CA #2: Example Root CA（クライアント証明書用）         │
│  ファイル: docker/nginx/ca.crt + ca.key                          │
│  Subject: CN=Example Root CA, O=Example CA, C=JP                │
│                                                                 │
│  発行する証明書:                                                  │
│    ├─ config/examples/financial-grade/certs/tls-client-auth.pem  │
│    │    (CN=fapi-ciba-tls-client)                                │
│    ├─ config/examples/financial-grade/certs/tls-client-auth-2.pem│
│    │    (CN=fapi-ciba-tls-client-2)                              │
│    ├─ config/generated/financial-grade/certs/client-cert.pem     │
│    │    (CN=financial-app)                                       │
│    └─ e2e/src/api/cert/tlsClientAuth.pem                        │
│         (CN=fapi-client.example.com)                             │
│                                                                 │
│  用途: FAPI/mTLSのクライアント認証（tls_client_auth）             │
│  再生成: openssl で ca.crt/ca.key を使って署名                    │
├─────────────────────────────────────────────────────────────────┤
│          CA #3: 自己署名テストクライアント証明書                    │
│  ファイル: docker/nginx/test-client.pem + test-client.key        │
│  Subject: CN=test-client.example.com, O=Test, C=JP              │
│                                                                 │
│  用途: mTLS疎通確認用（self_signed_tls_client_auth）              │
│  他の証明書は発行しない（自己署名のみ）                             │
└─────────────────────────────────────────────────────────────────┘
```

### nginx での証明書の使われ方

```
ブラウザ/curl
    │
    │ HTTPS (port 443)
    ▼
nginx ─── サーバー証明書: _wildcard.local.test+1.pem（mkcert CA 発行）
    │
    │ mTLS (mtls.api.local.test)
    │ クライアント証明書の検証に使用:
    │   client-ca-bundle.pem = Example Root CA + test-client.example.com
    │
    ▼
idp-server ← X-SSL-Cert ヘッダーでクライアント証明書を受け取る
```

### 重要なポイント

- **サーバー証明書とクライアント証明書は別のCAが発行**しているため、`setup-local-subdomain.sh` でサーバー証明書を再生成しても、クライアント証明書への影響はない
- `client-ca-bundle.pem` には mkcert CA は含まれない（クライアント認証には使わない）
- 証明書生成の詳細手順は [証明書生成ガイド](../../../config/examples/financial-grade/certs/README.md) を参照

## トラブルシューティング

### DNS解決ができない

```bash
# dnsmasq の状態確認
sudo brew services list | grep dnsmasq

# 再起動
sudo brew services restart dnsmasq

# 設定確認
cat /opt/homebrew/etc/dnsmasq.conf | grep local.test
cat /etc/resolver/local.test
```

### SSL証明書エラー

```bash
# mkcert の再インストール
mkcert -uninstall
mkcert -install

# 証明書の再生成
cd docker/nginx/certs
rm -f *.pem
mkcert "*.local.test" local.test
```

### ポート80/443が使用中

```bash
# 使用中のプロセスを確認
sudo lsof -i :80
sudo lsof -i :443

# 既存のdocker-composeを停止
docker-compose down
```

## 通常の開発環境との切り替え

```bash
# サブドメイン構成（デフォルト）
docker-compose up -d

# 通常構成（localhost）
docker-compose -f docker-compose-localhost.yaml up -d
```

## 本番環境との対応

| ローカル | 本番 |
|---------|------|
| `api.local.test` | `api.example.com` |
| `auth.local.test` | `auth.example.com` |
| `cookie_domain: "local.test"` | `cookie_domain: "example.com"` |

設定値を変更するだけで、同じ構成で動作します。
