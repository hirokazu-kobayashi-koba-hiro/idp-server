# RFC 8705: OAuth 2.0 Mutual TLS

RFC 8705 は、TLS クライアント証明書を使ったクライアント認証とトークンバインディングの仕様です。このドキュメントでは、mTLS の仕組みと実装方法を解説します。

---

## 第1部: 概要編

### mTLS とは何か？

mTLS（Mutual TLS）は、TLS ハンドシェイク時に**クライアントもサーバーに対して証明書を提示**する仕組みです。

```
通常の TLS（サーバー認証のみ）:
  クライアント ─── TLS ───► サーバー
                            証明書提示
                            「私は example.com です」

Mutual TLS（相互認証）:
  クライアント ─── TLS ───► サーバー
  証明書提示 ◄───────────── 証明書提示
  「私は client-A です」    「私は example.com です」
```

### RFC 8705 の 2 つの機能

| 機能 | 説明 |
|------|------|
| **クライアント認証** | クライアント証明書でクライアントを認証 |
| **証明書バインドトークン** | アクセストークンをクライアント証明書にバインド |

### なぜ mTLS が必要なのか？

#### 従来の方式の問題

| 方式 | 問題 |
|------|------|
| client_secret_basic | シークレットがネットワークを流れる |
| client_secret_post | シークレットがネットワークを流れる |
| private_key_jwt | JWT のリプレイ攻撃リスク |
| Bearer Token | 盗まれたら誰でも使える |

#### mTLS の解決策

```
mTLS クライアント認証:
  - クライアント証明書は TLS 層で検証
  - 秘密鍵は一切ネットワークを流れない
  - PKI インフラで信頼性を担保

証明書バインドトークン:
  - トークンが特定の証明書にバインド
  - 証明書を持たない攻撃者はトークンを使用不可
```

---

## 第2部: 詳細編

### クライアント認証方式

RFC 8705 では 2 つのクライアント認証方式を定義。

#### 1. PKI Mutual TLS（tls_client_auth）

CA が発行した証明書を使用。認可サーバーは証明書の Subject を検証。

```
クライアント登録時:
{
  "client_id": "s6BhdRkqt3",
  "token_endpoint_auth_method": "tls_client_auth",
  "tls_client_auth_subject_dn": "CN=client-app,O=Example Inc,C=US"
}
```

トークンリクエスト:

```http
POST /token HTTP/1.1
Host: auth.example.com
Content-Type: application/x-www-form-urlencoded
# TLS クライアント証明書が提示されている

grant_type=authorization_code
&code=SplxlOBeZQQYbYS6WxSbIA
&redirect_uri=https%3A%2F%2Fclient.example.org%2Fcallback
&client_id=s6BhdRkqt3
```

**注意**: `client_secret` は不要。TLS 層でクライアント認証が完了している。

#### 2. Self-Signed Certificate（self_signed_tls_client_auth）

自己署名証明書を使用。認可サーバーは事前登録された証明書と照合。

```
クライアント登録時:
{
  "client_id": "s6BhdRkqt3",
  "token_endpoint_auth_method": "self_signed_tls_client_auth",
  "jwks": {
    "keys": [{
      "kty": "RSA",
      "use": "sig",
      "x5c": ["MIIDQjCCAiqgAwIBAgIGATz..."]
    }]
  }
}
```

### Subject DN の照合

PKI mTLS では、証明書の Subject DN を照合。

| 属性 | パラメータ | 例 |
|------|-----------|-----|
| Subject DN | `tls_client_auth_subject_dn` | `CN=client,O=Example,C=US` |
| SAN DNS | `tls_client_auth_san_dns` | `client.example.com` |
| SAN URI | `tls_client_auth_san_uri` | `https://client.example.com` |
| SAN IP | `tls_client_auth_san_ip` | `192.168.1.1` |
| SAN Email | `tls_client_auth_san_email` | `client@example.com` |

### 証明書バインドアクセストークン

アクセストークンをクライアント証明書にバインドする機能。

#### 仕組み

```
1. トークンリクエスト時
   クライアント ──TLS証明書提示──► 認可サーバー
   
   認可サーバーは証明書のハッシュ（cnf.x5t#S256）を
   トークンに紐づけて発行

2. リソースアクセス時
   クライアント ──TLS証明書提示──► リソースサーバー
              ──アクセストークン──►
   
   リソースサーバーは:
   a) トークンから cnf.x5t#S256 を取得
   b) TLS 証明書のハッシュを計算
   c) 両者が一致することを確認
```

#### JWT アクセストークンの場合

```json
{
  "iss": "https://auth.example.com",
  "sub": "user-123",
  "client_id": "s6BhdRkqt3",
  "exp": 1704070800,
  "cnf": {
    "x5t#S256": "bwcK0esc3ACC3DB2Y5_lESsXE8o9ltc05O89jdN-dg2"
  }
}
```

`cnf.x5t#S256` は、クライアント証明書の SHA-256 サムプリント（Base64URL エンコード）。

#### Introspection の場合

```json
{
  "active": true,
  "client_id": "s6BhdRkqt3",
  "cnf": {
    "x5t#S256": "bwcK0esc3ACC3DB2Y5_lESsXE8o9ltc05O89jdN-dg2"
  }
}
```

### 証明書ハッシュの計算

```java
public String computeCertificateThumbprint(X509Certificate cert) 
        throws Exception {
    byte[] encoded = cert.getEncoded();
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] hash = digest.digest(encoded);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
}
```

### リソースサーバーでの検証

```java
public class MtlsTokenValidator {
    
    public void validate(String accessToken, X509Certificate clientCert) {
        // 1. トークンを検証（署名、有効期限など）
        JWTClaimsSet claims = validateToken(accessToken);
        
        // 2. cnf クレームを取得
        Map<String, Object> cnf = (Map<String, Object>) claims.getClaim("cnf");
        if (cnf == null) {
            throw new SecurityException("Token is not certificate-bound");
        }
        
        String expectedThumbprint = (String) cnf.get("x5t#S256");
        if (expectedThumbprint == null) {
            throw new SecurityException("Missing x5t#S256 in cnf");
        }
        
        // 3. 証明書のサムプリントを計算
        String actualThumbprint = computeCertificateThumbprint(clientCert);
        
        // 4. 照合
        if (!expectedThumbprint.equals(actualThumbprint)) {
            throw new SecurityException("Certificate thumbprint mismatch");
        }
    }
}
```

### TLS 終端の考慮事項

多くの本番環境では、TLS がロードバランサーやリバースプロキシで終端される。

```
クライアント ──TLS──► ロードバランサー ──HTTP──► アプリケーション
           証明書提示        │
                           証明書情報をヘッダーで転送
                           X-Client-Cert: ...
```

#### 証明書の転送方法

| 方式 | ヘッダー | 形式 |
|------|---------|------|
| AWS ALB | `X-Amzn-Mtls-Clientcert` | URL エンコード PEM |
| Nginx | `X-SSL-Client-Cert` | URL エンコード PEM |
| Envoy | `X-Forwarded-Client-Cert` | 独自形式 |

```java
// AWS ALB からクライアント証明書を取得
public X509Certificate extractCertificate(HttpServletRequest request) 
        throws Exception {
    String certHeader = request.getHeader("X-Amzn-Mtls-Clientcert");
    if (certHeader == null) {
        throw new SecurityException("No client certificate");
    }
    
    String pem = URLDecoder.decode(certHeader, StandardCharsets.UTF_8);
    CertificateFactory cf = CertificateFactory.getInstance("X.509");
    return (X509Certificate) cf.generateCertificate(
        new ByteArrayInputStream(pem.getBytes())
    );
}
```

### FAPI における mTLS

| プロファイル | mTLS 要件 |
|-------------|----------|
| FAPI 1.0 Baseline | クライアント認証として使用可能 |
| FAPI 1.0 Advanced | クライアント認証または DPoP が必須 |
| FAPI 2.0 | クライアント認証または DPoP が必須 |

### mTLS vs DPoP

| 観点 | mTLS | DPoP |
|------|------|------|
| レイヤー | TLS（L4/L5） | アプリケーション（L7） |
| 証明書管理 | PKI インフラが必要 | 不要（自己生成鍵ペア） |
| TLS 終端 | プロキシでの転送が複雑 | ヘッダーで完結 |
| ブラウザサポート | 限定的 | JavaScript で実装可能 |
| 既存インフラ | 企業環境で普及 | 新しい環境向け |

### セキュリティ考慮事項

| 項目 | 推奨事項 |
|------|----------|
| 証明書の検証 | 失効確認（CRL/OCSP）を行う |
| 証明書の有効期限 | 適切な期間を設定（1〜2年） |
| 秘密鍵の保護 | HSM や TPM での保管を推奨 |
| TLS バージョン | TLS 1.2 以上必須、TLS 1.3 推奨 |
| 暗号スイート | 強力な暗号スイートのみ許可 |

### Discovery メタデータ

```json
{
  "token_endpoint_auth_methods_supported": [
    "tls_client_auth",
    "self_signed_tls_client_auth",
    "private_key_jwt"
  ],
  "tls_client_certificate_bound_access_tokens": true,
  "mtls_endpoint_aliases": {
    "token_endpoint": "https://mtls.auth.example.com/token",
    "introspection_endpoint": "https://mtls.auth.example.com/introspect"
  }
}
```

`mtls_endpoint_aliases` を使うことで、mTLS 用の専用エンドポイントを提供できる。

---

## 参考リンク

- [RFC 8705 - OAuth 2.0 Mutual-TLS Client Authentication and Certificate-Bound Access Tokens](https://datatracker.ietf.org/doc/html/rfc8705)
- [RFC 9449 - DPoP](https://datatracker.ietf.org/doc/html/rfc9449)
- [FAPI 2.0 Security Profile](https://openid.net/specs/fapi-2_0-security-profile.html)
