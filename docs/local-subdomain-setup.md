# ローカルサブドメイン開発環境セットアップ

本番環境に近いサブドメイン構成でローカル開発を行うための手順です。

## 構成

```
┌─────────────────────────────────────────────────────────┐
│                   local.dev                             │
│                                                         │
│   https://api.local.dev   →  IDP Server（バックエンドAPI）│
│   https://auth.local.dev  →  App View（認可画面）       │
│                                                         │
│   cookie_domain: "local.dev"                           │
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

# local.dev → 127.0.0.1 の設定を追加
echo 'address=/local.dev/127.0.0.1' >> /opt/homebrew/etc/dnsmasq.conf

# macOS resolver 設定
sudo mkdir -p /etc/resolver
echo "nameserver 127.0.0.1" | sudo tee /etc/resolver/local.dev

# dnsmasq サービス起動
sudo brew services start dnsmasq
```

### 2. DNS解決の確認

```bash
ping auth.local.dev
# PING auth.local.dev (127.0.0.1): 56 data bytes
```

### 3. mkcert のインストールとSSL証明書生成

```bash
# インストール
brew install mkcert

# ローカルCAをシステムに登録
mkcert -install

# SSL証明書を生成
cd docker/nginx/certs
mkcert "*.local.dev" local.dev
```

生成されるファイル：
- `_wildcard.local.dev+1.pem` - 証明書
- `_wildcard.local.dev+1-key.pem` - 秘密鍵

### 4. 環境変数の設定

```bash
cp .env.example .env
# .env ファイルを編集して必要な値を設定
```

### 5. ビルドと起動

```bash
# ビルド
docker-compose -f docker-compose-subdomain.yaml build

# 起動
docker-compose -f docker-compose-subdomain.yaml up -d
```

### 6. 動作確認

ブラウザで以下にアクセス：

- IDP Server API: https://api.local.dev
- 認可画面: https://auth.local.dev

## テナント設定

サブドメイン構成では、テナントの `session_config` に `cookie_domain` を設定します：

```json
{
  "session_config": {
    "cookie_domain": "local.dev",
    "cookie_same_site": "Lax",
    "use_secure_cookie": true,
    "timeout_seconds": 3600
  }
}
```

設定例: `config/examples/subdomain-oidc-web-app/onboarding-request.json`

## トラブルシューティング

### DNS解決ができない

```bash
# dnsmasq の状態確認
sudo brew services list | grep dnsmasq

# 再起動
sudo brew services restart dnsmasq

# 設定確認
cat /opt/homebrew/etc/dnsmasq.conf | grep local.dev
cat /etc/resolver/local.dev
```

### SSL証明書エラー

```bash
# mkcert の再インストール
mkcert -uninstall
mkcert -install

# 証明書の再生成
cd docker/nginx/certs
rm -f *.pem
mkcert "*.local.dev" local.dev
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
# サブドメイン構成
docker-compose -f docker-compose-subdomain.yaml up -d

# 通常構成（localhost）
docker-compose up -d
```

## 本番環境との対応

| ローカル | 本番 |
|---------|------|
| `api.local.dev` | `api.example.com` |
| `auth.local.dev` | `auth.example.com` |
| `cookie_domain: "local.dev"` | `cookie_domain: "example.com"` |

設定値を変更するだけで、同じ構成で動作します。
