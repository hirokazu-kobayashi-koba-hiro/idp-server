# mTLS Client Certificate Generation Guide

このディレクトリには、FAPI準拠のmTLSクライアント認証で使用する証明書の生成手順と管理方法をまとめています。

## 概要

FAPI (Financial-grade API) では、以下の2種類のmTLSクライアント認証方式がサポートされています：

| 認証方式 | 証明書タイプ | 用途 | 信頼モデル |
|---------|------------|------|-----------|
| **`self_signed_tls_client_auth`** | 自己署名証明書 | テスト環境、限定クライアント | 証明書そのものを事前登録 |
| **`tls_client_auth`** | CA署名証明書 | 本番環境、多数クライアント | CA証明書で信頼チェーン検証 |

## 1. Self-Signed Certificate (自己署名証明書)

### 特徴

- 証明書の発行者（Issuer）と主体（Subject）が同一
- CA（認証局）が不要でシンプル
- 各クライアント証明書を個別にサーバーに登録必須

### 生成手順

```bash
# 秘密鍵生成
openssl genrsa -out client-key.pem 2048

# 自己署名証明書生成
openssl req -new -x509 -days 365 -key client-key.pem -out client-cert.pem \
  -subj "/C=JP/ST=Tokyo/L=Tokyo/O=Example Client/CN=client.example.com"

# DER形式に変換（一部のシステムで必要）
openssl x509 -in client-cert.pem -outform DER -out client-cert.der

# 証明書情報確認
openssl x509 -in client-cert.pem -noout -text > client-cert-info.txt
```

### 証明書フィンガープリント計算（JWKs登録用）

```bash
# SHA-256フィンガープリント計算（Base64URL形式）
openssl x509 -in client-cert.pem -noout -fingerprint -sha256 | \
  cut -d= -f2 | tr -d ':' | xxd -r -p | base64 | tr '+/' '-_' | tr -d '='
```

### クライアント登録（Management API）

```json
{
  "client_id": "financial-web-app",
  "token_endpoint_auth_method": "self_signed_tls_client_auth",
  "tls_client_certificate_bound_access_tokens": true,
  "jwks": {
    "keys": [
      {
        "kty": "RSA",
        "use": "sig",
        "x5c": ["<Base64エンコードされた証明書>"],
        "x5t#S256": "<SHA-256フィンガープリント>"
      }
    ]
  }
}
```

### 検証方法

```bash
# Issuer と Subject が同一であることを確認
openssl x509 -in client-cert.pem -noout -subject -issuer

# 出力例（自己署名）:
# subject=C=JP, ST=Tokyo, L=Tokyo, O=Example Client, CN=client.example.com
# issuer=C=JP, ST=Tokyo, L=Tokyo, O=Example Client, CN=client.example.com
```

## 2. CA-Signed Certificate (CA署名証明書)

### 特徴

- 信頼されたCA（認証局）が証明書に署名
- CA証明書をサーバーに登録すれば、同じCAで発行された全クライアント証明書を検証可能
- 企業環境・本番環境で推奨
- 証明書発行フローが標準化

### 生成手順

#### Step 1: CA（認証局）の作成

```bash
# CA秘密鍵生成
openssl genrsa -out ca.key 2048

# CA証明書生成（自己署名、10年有効）
openssl req -new -x509 -days 3650 -key ca.key -out ca.crt \
  -subj "/C=JP/ST=Tokyo/L=Tokyo/O=Example CA/CN=Example Root CA"

# CA証明書情報確認
openssl x509 -in ca.crt -noout -subject -issuer
```

**重要**: `ca.key`は厳重に管理してください。この鍵が漏洩すると、攻撃者が任意のクライアント証明書を発行できてしまいます。

#### Step 2: クライアント証明書の作成

```bash
# クライアント秘密鍵生成
openssl genrsa -out tls-client.key 2048

# CSR (Certificate Signing Request) 生成
openssl req -new -key tls-client.key -out tls-client.csr \
  -subj "/C=JP/ST=Tokyo/L=Tokyo/O=Example Client/CN=fapi-client.example.com"

# CA署名によるクライアント証明書発行（1年有効）
openssl x509 -req -in tls-client.csr -CA ca.crt -CAkey ca.key \
  -CAcreateserial -out tls-client.pem -days 365 \
  -extfile <(echo "basicConstraints=CA:FALSE
keyUsage=digitalSignature,keyEncipherment
extendedKeyUsage=clientAuth")

# 証明書チェーン検証
openssl verify -CAfile ca.crt tls-client.pem
# 出力: tls-client.pem: OK
```

#### Step 3: 証明書情報確認

```bash
# Issuer（発行者）と Subject（主体）を確認
openssl x509 -in tls-client.pem -noout -subject -issuer

# 出力例（CA署名）:
# subject=C=JP, ST=Tokyo, L=Tokyo, O=Example Client, CN=fapi-client.example.com
# issuer=C=JP, ST=Tokyo, L=Tokyo, O=Example CA, CN=Example Root CA
#       ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ ← CAが署名
```

```shell
# 全情報
  openssl x509 -in tls-client-auth.pem -noout -text

  # Subject DNだけ
  openssl x509 -in tls-client-auth.pem -noout -subject

  # 有効期限だけ
  openssl x509 -in tls-client-auth.pem -noout -dates
```

### サーバー側設定（nginx例）

```nginx
http {
  server {
    listen 443 ssl;

    # サーバー証明書
    ssl_certificate /etc/nginx/ssl/server-cert.pem;
    ssl_certificate_key /etc/nginx/ssl/server-key.pem;

    # クライアント証明書検証（CA証明書で信頼チェーン検証）
    ssl_client_certificate /etc/nginx/ssl/ca.crt;
    ssl_verify_client on;
    ssl_verify_depth 2;

    location / {
      # クライアント証明書をHTTPヘッダーで転送
      proxy_set_header X-SSL-Client-Cert $ssl_client_cert;
      proxy_set_header X-SSL-Client-S-DN $ssl_client_s_dn;
      proxy_pass http://idp-server:8080;
    }
  }
}
```

### クライアント登録（Management API）

```json
{
  "client_id": "financial-web-app",
  "token_endpoint_auth_method": "tls_client_auth",
  "tls_client_certificate_bound_access_tokens": true,
  "tls_client_auth_subject_dn": "CN=fapi-client.example.com,O=Example Client,L=Tokyo,ST=Tokyo,C=JP"
}
```

**注意**: `tls_client_auth`の場合、`jwks`に証明書を含める代わりに、`tls_client_auth_subject_dn`でSubject DNを指定します。

## 3. 証明書の比較

### 検証方法の違い

| 項目 | self_signed_tls_client_auth | tls_client_auth |
|------|---------------------------|-----------------|
| **サーバー側の信頼設定** | 各クライアント証明書を個別登録（JWKs） | CA証明書のみ登録 |
| **証明書追加時** | 新しい証明書をJWKsに追加 | CAで署名すれば自動的に信頼 |
| **証明書失効** | JWKsから削除 | CRL (Certificate Revocation List) またはOCSP |
| **スケーラビリティ** | 低（クライアント数に比例） | 高（CA証明書1つで管理） |

### Issuer/Subject確認例

```bash
# Self-signed (同一)
$ openssl x509 -in client-cert.pem -noout -subject -issuer
subject=CN=client.example.com
issuer=CN=client.example.com  ← 同じ（自己署名）

# CA-signed (異なる)
$ openssl x509 -in tls-client.pem -noout -subject -issuer
subject=CN=fapi-client.example.com
issuer=CN=Example Root CA  ← 異なる（CAが署名）
```

## 4. Sender-Constrained Access Tokens (mTLS Token Binding)

### 概要

FAPI Advanced/FAPI CIBAでは、アクセストークンをクライアント証明書にバインドすることが必須です。

### フロー

#### 1. トークン発行時

```
クライアント → nginx (mTLS終端) → idp-server
           (mTLS)   (HTTP + X-SSL-Cert header)

1. nginx: クライアント証明書検証
2. nginx: 証明書をPEMエンコードしてHTTPヘッダーで転送
3. idp-server: 証明書サムプリント（SHA-256）計算
4. idp-server: cnf:x5t#S256 としてアクセストークンに埋め込み
```

**アクセストークン例（JWT形式）**:
```json
{
  "iss": "https://idp-server.example.com",
  "sub": "user-123",
  "aud": "resource-server",
  "exp": 1234567890,
  "scope": "openid account transfers",
  "cnf": {
    "x5t#S256": "bwcK0esc3ACC3DB2Y5_lESsXE8o9ltc05O89jdN-dg2"
  }
}
```

#### 2. API呼び出し時

```
クライアント → nginx (mTLS終端) → Resource Server
           (mTLS)   (HTTP + X-SSL-Cert + Authorization)

1. nginx: クライアント証明書検証
2. Resource Server: トークン内 cnf:x5t#S256 抽出
3. Resource Server: 証明書サムプリント計算
4. Resource Server: 一致確認 → アクセス許可
```

### 証明書サムプリント計算

```bash
# SHA-256フィンガープリント（Base64URL形式）
openssl x509 -in client-cert.pem -noout -fingerprint -sha256 | \
  cut -d= -f2 | tr -d ':' | xxd -r -p | base64 | tr '+/' '-_' | tr -d '='

# 結果例
bwcK0esc3ACC3DB2Y5_lESsXE8o9ltc05O89jdN-dg2
```

## 5. セキュリティベストプラクティス

### 証明書管理

- ✅ **秘密鍵の厳重管理**: 0600権限、暗号化ストレージ
- ✅ **定期ローテーション**: 最大1年（推奨: 3-6ヶ月）
- ✅ **CA秘密鍵の分離**: HSM (Hardware Security Module) 推奨
- ✅ **失効リスト運用**: CRL/OCSPの実装

### 鍵長要件（FAPI Part 1 5.2.2-5）

| アルゴリズム | 最小鍵長 | 推奨鍵長 |
|------------|---------|---------|
| RSA | 2048 bits | 3072 bits |
| ECDSA | 256 bits (P-256) | 384 bits (P-384) |

### 証明書属性

- ✅ **Subject DN**: クライアント識別に使用
- ✅ **Extended Key Usage**: `clientAuth` 必須
- ✅ **Key Usage**: `digitalSignature, keyEncipherment`
- ❌ **CA:TRUE**: クライアント証明書では禁止

## 6. トラブルシューティング

### 証明書検証エラー

```bash
# エラー: self-signed certificate
$ openssl verify client-cert.pem
error 18 at 0 depth lookup: self-signed certificate

# 解決: self-signedの場合は正常（-CAfileは不要）
```

```bash
# エラー: unable to get local issuer certificate
$ openssl verify tls-client.pem
error 20 at 0 depth lookup: unable to get local issuer certificate

# 解決: CA証明書を指定
$ openssl verify -CAfile ca.crt tls-client.pem
tls-client.pem: OK
```

### nginx mTLS接続テスト

```bash
# クライアント証明書でHTTPS接続テスト
curl -v https://localhost:8445/health \
  --cert tls-client-auth-2.pem \
  --key tls-client-auth-2.key
```

```text
# 成功時: HTTP 200
# 失敗時: SSL alert handshake failure
```

## 7. 関連ドキュメント

- [FAPI CIBA Profile仕様](../../../documentation/docs/content_04_protocols/protocol-05-fapi-ciba.md)
- [RFC 8705 - OAuth 2.0 Mutual-TLS Client Authentication](https://datatracker.ietf.org/doc/html/rfc8705)
- [Financial-grade API設定ガイド](../README.md)

---

**作成日**: 2025-11-29
**対象**: システムアーキテクト、セキュリティエンジニア
**習得スキル**: mTLSクライアント認証、証明書管理、FAPI準拠
