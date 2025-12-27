# クライアント認証

---

## 前提知識

このドキュメントを理解するには、以下の基礎知識が役立ちます：

- [OAuth 2.0の基本](../content_11_learning/02-oauth-fundamentals/oauth2-authorization.md) - OAuth 2.0の認可の仕組み
- [トークンエンドポイント](../content_11_learning/02-oauth-fundamentals/oauth2-token-endpoint.md) - トークン取得の流れ

---

## 概要

クライアント認証は、OAuth 2.0/OpenID Connectにおいてクライアント（アプリケーション）が認可サーバーに対して自身を証明するための仕組みです。

`idp-server`は以下のクライアント認証方式をサポートしています：

| 認証方式 | 説明 | セキュリティレベル |
|---------|------|------------------|
| `client_secret_basic` | HTTP Basic認証 | 標準 |
| `client_secret_post` | POSTボディにシークレット | 標準 |
| `client_secret_jwt` | HMAC署名JWT | 高 |
| `private_key_jwt` | 公開鍵署名JWT | 最高 |
| `tls_client_auth` | mTLS証明書認証 | 最高 |
| `self_signed_tls_client_auth` | 自己署名mTLS | 最高 |
| `none` | 認証なし（Public Client） | - |

---

## クライアント認証方式の詳細

### client_secret_basic

HTTP Basic認証を使用してクライアントを認証します。

```http
POST /tokens HTTP/1.1
Host: idp.example.com
Authorization: Basic Y2xpZW50X2lkOmNsaWVudF9zZWNyZXQ=
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code&code=xxx&redirect_uri=https://client.example.com/callback
```

**Base64エンコード**: `client_id:client_secret`

---

### client_secret_post

リクエストボディにクライアントIDとシークレットを含めます。

```http
POST /tokens HTTP/1.1
Host: idp.example.com
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code
&code=xxx
&redirect_uri=https://client.example.com/callback
&client_id=my_client
&client_secret=my_secret
```

---

### client_secret_jwt

クライアントシークレットを使用してHMAC署名されたJWTで認証します。

```http
POST /tokens HTTP/1.1
Host: idp.example.com
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code
&code=xxx
&redirect_uri=https://client.example.com/callback
&client_id=my_client
&client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer
&client_assertion=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**JWTペイロードの例**:
```json
{
  "iss": "my_client",
  "sub": "my_client",
  "aud": "https://idp.example.com",
  "jti": "unique-token-id",
  "exp": 1735300000,
  "iat": 1735296400
}
```

---

### private_key_jwt

クライアントの秘密鍵で署名されたJWTで認証します。最も安全な方式の一つです。

```http
POST /tokens HTTP/1.1
Host: idp.example.com
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code
&code=xxx
&redirect_uri=https://client.example.com/callback
&client_id=my_client
&client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer
&client_assertion=eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6ImtleTEifQ...
```

**JWTペイロード**は`client_secret_jwt`と同様ですが、RSAまたはECDSA秘密鍵で署名されます。

---

## RFC 7523: JWT Bearer Client Authentication

`client_secret_jwt`および`private_key_jwt`は、RFC 7523「JSON Web Token (JWT) Profile for OAuth 2.0 Client Authentication and Authorization Grants」に準拠しています。

### Section 3: JWT Format and Processing Requirements

認可サーバーがJWTを受け入れるために、以下の要件を満たす必要があります：

#### 署名要件

> "The JWT MUST be digitally signed or have a Message Authentication Code (MAC) applied by the issuer."
>
> — RFC 7523 Section 3

**重要**: `alg: none`（署名なし）のJWTは拒否されます。これはセキュリティ上の脆弱性を防ぐために必須です。

#### 必須クレーム

| クレーム | 説明 | RFC 7523の要件 |
|---------|------|---------------|
| `iss` | 発行者（= client_id） | **MUST** - "The JWT MUST contain an 'iss' (issuer) claim that contains a unique identifier for the entity that issued the JWT." |
| `sub` | 主体（= client_id） | **MUST** - "The JWT MUST contain a 'sub' (subject) claim identifying the principal that is the subject of the JWT." |
| `aud` | 対象者（= 認可サーバーURL） | **MUST** - "The JWT MUST contain an 'aud' (audience) claim containing a value that identifies the authorization server as an intended audience." |
| `exp` | 有効期限 | **MUST** - "The JWT MUST contain an 'exp' (expiration time) claim that limits the time window during which the JWT can be used." |
| `jti` | JWT ID | **MAY** - "The JWT MAY contain a 'jti' (JWT ID) claim that provides a unique identifier for the token." |
| `iat` | 発行時刻 | **MAY** - 推奨 |

#### aud（audience）クレームの値

`aud`クレームには以下のいずれかを指定できます：

1. **認可サーバーのIssuer URL** - 例: `https://idp.example.com`
2. **トークンエンドポイントURL** - 例: `https://idp.example.com/tokens`

> "The token endpoint URL of the authorization server MAY be used as a value for the 'aud' element to identify the authorization server as an intended audience of the JWT."
>
> — RFC 7523 Section 3

#### idp-serverの実装詳細

`idp-server`では、セキュリティ強化のため以下の追加要件があります：

| クレーム | idp-server要件 | 理由 |
|---------|---------------|------|
| `jti` | **REQUIRED** | リプレイ攻撃防止 |

---

## mTLS Client Authentication

### tls_client_auth

PKI（Public Key Infrastructure）で発行された証明書を使用したmTLS認証です。

クライアント証明書のDNまたはSANが、事前に登録された値と一致する必要があります。

### self_signed_tls_client_auth

自己署名証明書を使用したmTLS認証です。証明書のフィンガープリントまたは公開鍵が、事前に登録された値と一致する必要があります。

---

## セキュリティ考慮事項

### 推奨事項

1. **Confidential Clientには`private_key_jwt`または`tls_client_auth`を推奨**
   - クライアントシークレットの漏洩リスクがない
   - FAPI準拠に必要

2. **`client_secret_basic`/`client_secret_post`はFAPI非準拠**
   - FAPI準拠が必要な場合は使用不可
   - 一般的なユースケースでは問題なし

3. **JWTの有効期限は短く設定**
   - 推奨: 5分以内
   - 長すぎるとリプレイ攻撃のリスク増加

---

## 関連仕様

- [RFC 6749](https://www.rfc-editor.org/rfc/rfc6749) - OAuth 2.0 Authorization Framework
- [RFC 7523](https://www.rfc-editor.org/rfc/rfc7523) - JWT Profile for OAuth 2.0 Client Authentication
- [RFC 8705](https://www.rfc-editor.org/rfc/rfc8705) - OAuth 2.0 Mutual-TLS Client Authentication
- [OpenID Connect Core 1.0 Section 9](https://openid.net/specs/openid-connect-core-1_0.html#ClientAuthentication) - Client Authentication

---

## 参考

- [認可コードフロー](./protocol-01-authorization-code-flow.md) - クライアント認証を使用するフロー
- [CIBAフロー](./protocol-02-ciba-flow.md) - バックチャネル認証でのクライアント認証
