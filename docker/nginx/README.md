# Nginx mTLS Configuration

このディレクトリには、mTLSクライアント認証用nginx設定が含まれています。

## ファイル構成

```
docker/nginx/
├── Dockerfile          # nginxイメージビルド定義
├── nginx.conf          # メイン設定（ロードバランサー + mTLS include）
├── ssl.conf            # HTTPS設定（port 443）
├── mtls.conf           # mTLS設定（port 8443）
├── server.crt          # サーバー証明書
├── server.key          # サーバー秘密鍵
├── test-client.pem     # テスト用クライアント証明書
├── test-client.key     # テスト用クライアント秘密鍵
├── test-mtls.sh        # mTLS疎通確認スクリプト
└── README.md           # このファイル
```

## ポート構成

| ポート | プロトコル | 用途 | クライアント認証 |
|-------|----------|------|----------------|
| 80 | HTTP | 通常アクセス | なし |
| 443 | HTTPS | SSL/TLS | なし |
| 8443 | HTTPS | mTLS | **必須** |

## mTLS設定内容

### クライアント証明書検証

```nginx
# CA証明書による検証
ssl_client_certificate "/etc/nginx/certs/ca.crt";
ssl_verify_client on;
ssl_verify_depth 2;
```

### バックエンドへのヘッダー転送

```nginx
# idp-serverが期待するヘッダー名（小文字）
proxy_set_header x-ssl-cert $ssl_client_cert;
proxy_set_header x-ssl-client-s-dn $ssl_client_s_dn;
proxy_set_header x-ssl-client-i-dn $ssl_client_i_dn;
proxy_set_header x-ssl-client-verify $ssl_client_verify;
```

## クイックスタート

### 1. Dockerイメージビルド

```bash
cd /path/to/idp-server
docker-compose build nginx
docker-compose up -d nginx
```

### 2. mTLS疎通確認

```bash
cd docker/nginx
./test-mtls.sh
```

**期待される出力**:
```
Testing mTLS connection to https://localhost:8443/67e7eae6-62b0-4500-9eff-87459f63fc66/health

1. Without client certificate (expected: SSL error)
curl: (35) error:14094410:SSL routines:ssl3_read_bytes:sslv3 alert handshake failure

2. With client certificate (expected: 200 OK)
{"status":"UP"}
```

## 手動curlテスト

### ✅ 成功パターン

```bash
# Health check
curl -v -k \
  --cert test-client.pem \
  --key test-client.key \
  https://localhost:8443/67e7eae6-62b0-4500-9eff-87459f63fc66/v1/health

# OpenID Configuration
curl -k \
  --cert test-client.pem \
  --key test-client.key \
  https://localhost:8443/67e7eae6-62b0-4500-9eff-87459f63fc66/.well-known/openid-configuration
```

### ❌ 失敗パターン

```bash
# クライアント証明書なし → SSL handshake failure
curl -k https://localhost:8443/67e7eae6-62b0-4500-9eff-87459f63fc66/v1/health
```

## トラブルシューティング

### 1. SSL handshake failure

**症状**:
```
curl: (35) SSL peer certificate or SSH remote key was not OK
```

**原因**: クライアント証明書が提供されていない

**解決**: `--cert` と `--key` オプションを追加

```bash
curl --cert client.pem --key client.key https://localhost:8443/...
```

### 2. Certificate verify failed

**症状**:
```
SSL certificate problem: unable to get local issuer certificate
```

**原因**: サーバー証明書が自己署名で、CA検証が必要

**解決**: `-k` オプションで証明書検証をスキップ（開発環境のみ）

```bash
curl -k --cert client.pem --key client.key https://localhost:8443/...
```

### 3. nginx起動失敗

**症状**:
```
nginx: [emerg] cannot load certificate "/etc/nginx/certs/ca.crt"
```

**原因**: CA証明書がDockerイメージに含まれていない

**解決**: Dockerイメージを再ビルド

```bash
docker-compose build nginx
docker-compose up -d nginx
```

### 4. x-ssl-cert header not received

**症状**: バックエンドでクライアント証明書が取得できない

**確認方法**:
```bash
# nginxログ確認
docker logs load-balancer

# idp-serverログ確認
docker logs idp-server-1 | grep "x-ssl-cert"
```

**解決**: mtls.confの`proxy_set_header x-ssl-cert`設定を確認

## nginx設定変更後の反映

```bash
# 設定ファイルのみ変更した場合
docker-compose restart nginx

# Dockerfileを変更した場合
docker-compose build nginx
docker-compose up -d nginx
```

## セキュリティ注意事項

### 本番環境では

1. ✅ **自己署名証明書を使用しない**: 信頼されたCAから証明書を取得
2. ✅ **証明書の定期ローテーション**: 最大1年（推奨: 3-6ヶ月）
3. ✅ **秘密鍵の厳重管理**: 0600権限、暗号化ストレージ
4. ✅ **CRL/OCSPの実装**: 証明書失効リストの運用
5. ✅ **TLS 1.3使用**: `ssl_protocols TLSv1.3;`

### 開発環境のみ

- ❌ `-k` オプション（証明書検証スキップ）
- ❌ 自己署名CA証明書
- ❌ 秘密鍵のバージョン管理への含有

## 関連ドキュメント

- [証明書生成ガイド](../../config/examples/financial-grade/certs/README.md)
- [FAPI CIBA Profile仕様](../../documentation/docs/content_04_protocols/protocol-05-fapi-ciba.md)
- [RFC 8705 - OAuth 2.0 Mutual-TLS](https://datatracker.ietf.org/doc/html/rfc8705)

---

**作成日**: 2025-11-29
**更新日**: 2025-11-29
