# OpenID Connect Discovery 1.0

OpenID Connect Discovery は、OpenID Provider（OP）の設定情報を自動的に発見するための仕様です。

---

## 第1部: 概要編

### Discovery とは？

Discovery は、クライアントが OpenID Provider の設定情報を**自動的に取得**するための仕組みです。

```
手動設定（従来）:
  クライアント設定ファイル:
    authorization_endpoint: https://auth.example.com/authorize
    token_endpoint: https://auth.example.com/token
    jwks_uri: https://auth.example.com/.well-known/jwks.json
    ...

自動発見（Discovery）:
  GET https://auth.example.com/.well-known/openid-configuration
      ↓
  すべての設定情報を JSON で取得
```

### なぜ Discovery が必要なのか？

| 課題 | Discovery による解決 |
|------|---------------------|
| 設定の手動管理 | 自動取得 |
| エンドポイントの変更 | メタデータを更新するだけ |
| サポート機能の確認 | メタデータで宣言 |
| マルチ OP 対応 | issuer から自動発見 |

### Well-Known URI

```
OpenID Provider のメタデータ:
  https://{issuer}/.well-known/openid-configuration

例:
  https://accounts.google.com/.well-known/openid-configuration
  https://login.microsoftonline.com/{tenant}/v2.0/.well-known/openid-configuration
  https://auth.example.com/.well-known/openid-configuration
```

---

## 第2部: 詳細編

### メタデータの取得

```http
GET /.well-known/openid-configuration HTTP/1.1
Host: auth.example.com
```

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "issuer": "https://auth.example.com",
  "authorization_endpoint": "https://auth.example.com/authorize",
  "token_endpoint": "https://auth.example.com/token",
  "userinfo_endpoint": "https://auth.example.com/userinfo",
  "jwks_uri": "https://auth.example.com/.well-known/jwks.json",
  ...
}
```

### 必須メタデータ

| フィールド | 説明 |
|-----------|------|
| `issuer` | OP の識別子（URL） |
| `authorization_endpoint` | 認可エンドポイント |
| `token_endpoint` | トークンエンドポイント（暗黙的フローのみの場合は不要） |
| `jwks_uri` | JWK Set の URL |
| `response_types_supported` | サポートする response_type |
| `subject_types_supported` | サポートする subject タイプ |
| `id_token_signing_alg_values_supported` | ID トークンの署名アルゴリズム |

### 推奨メタデータ

| フィールド | 説明 |
|-----------|------|
| `userinfo_endpoint` | UserInfo エンドポイント |
| `registration_endpoint` | 動的登録エンドポイント |
| `scopes_supported` | サポートするスコープ |
| `claims_supported` | サポートするクレーム |
| `grant_types_supported` | サポートするグラントタイプ |
| `acr_values_supported` | サポートする認証コンテキストクラス |

### 完全なメタデータ例

```json
{
  "issuer": "https://auth.example.com",

  "authorization_endpoint": "https://auth.example.com/authorize",
  "token_endpoint": "https://auth.example.com/token",
  "userinfo_endpoint": "https://auth.example.com/userinfo",
  "jwks_uri": "https://auth.example.com/.well-known/jwks.json",
  "registration_endpoint": "https://auth.example.com/register",
  "revocation_endpoint": "https://auth.example.com/revoke",
  "introspection_endpoint": "https://auth.example.com/introspect",
  "end_session_endpoint": "https://auth.example.com/logout",
  "pushed_authorization_request_endpoint": "https://auth.example.com/par",

  "scopes_supported": [
    "openid", "profile", "email", "address", "phone", "offline_access"
  ],

  "response_types_supported": [
    "code",
    "token",
    "id_token",
    "code token",
    "code id_token",
    "token id_token",
    "code token id_token"
  ],

  "response_modes_supported": [
    "query", "fragment", "form_post", "jwt", "query.jwt", "fragment.jwt", "form_post.jwt"
  ],

  "grant_types_supported": [
    "authorization_code",
    "implicit",
    "refresh_token",
    "client_credentials",
    "urn:ietf:params:oauth:grant-type:jwt-bearer",
    "urn:ietf:params:oauth:grant-type:token-exchange",
    "urn:openid:params:grant-type:ciba"
  ],

  "subject_types_supported": [
    "public", "pairwise"
  ],

  "id_token_signing_alg_values_supported": [
    "RS256", "RS384", "RS512", "ES256", "ES384", "ES512", "PS256", "PS384", "PS512"
  ],

  "id_token_encryption_alg_values_supported": [
    "RSA-OAEP", "RSA-OAEP-256", "A256KW"
  ],

  "id_token_encryption_enc_values_supported": [
    "A128CBC-HS256", "A256CBC-HS512", "A128GCM", "A256GCM"
  ],

  "userinfo_signing_alg_values_supported": [
    "RS256", "ES256"
  ],

  "request_object_signing_alg_values_supported": [
    "none", "RS256", "ES256"
  ],

  "token_endpoint_auth_methods_supported": [
    "client_secret_basic",
    "client_secret_post",
    "client_secret_jwt",
    "private_key_jwt",
    "tls_client_auth",
    "self_signed_tls_client_auth"
  ],

  "token_endpoint_auth_signing_alg_values_supported": [
    "RS256", "ES256", "PS256"
  ],

  "claims_supported": [
    "sub", "iss", "aud", "exp", "iat", "auth_time", "nonce", "acr", "amr",
    "name", "given_name", "family_name", "nickname", "preferred_username",
    "profile", "picture", "website", "email", "email_verified",
    "gender", "birthdate", "zoneinfo", "locale", "phone_number",
    "phone_number_verified", "address", "updated_at"
  ],

  "acr_values_supported": [
    "urn:mace:incommon:iap:silver",
    "urn:mace:incommon:iap:bronze"
  ],

  "claims_parameter_supported": true,
  "request_parameter_supported": true,
  "request_uri_parameter_supported": true,
  "require_request_uri_registration": true,

  "code_challenge_methods_supported": [
    "plain", "S256"
  ],

  "tls_client_certificate_bound_access_tokens": true,
  "dpop_signing_alg_values_supported": ["RS256", "ES256"],

  "authorization_response_iss_parameter_supported": true,

  "backchannel_logout_supported": true,
  "backchannel_logout_session_supported": true,
  "frontchannel_logout_supported": true,
  "frontchannel_logout_session_supported": true,

  "service_documentation": "https://auth.example.com/docs",
  "ui_locales_supported": ["en", "ja", "de", "fr"]
}
```

### OAuth 2.0 拡張メタデータ（RFC 8414）

| フィールド | 説明 |
|-----------|------|
| `revocation_endpoint` | トークン取消エンドポイント |
| `introspection_endpoint` | トークンイントロスペクションエンドポイント |
| `code_challenge_methods_supported` | PKCE のサポート |
| `pushed_authorization_request_endpoint` | PAR エンドポイント |
| `require_pushed_authorization_requests` | PAR が必須か |

### セキュリティ拡張メタデータ

| フィールド | 説明 |
|-----------|------|
| `tls_client_certificate_bound_access_tokens` | mTLS トークンバインディング |
| `dpop_signing_alg_values_supported` | DPoP サポート |
| `authorization_response_iss_parameter_supported` | RFC 9207 サポート |

### ログアウト関連メタデータ

| フィールド | 説明 |
|-----------|------|
| `end_session_endpoint` | RP 起点ログアウト |
| `frontchannel_logout_supported` | フロントチャネルログアウト |
| `frontchannel_logout_session_supported` | セッション付きフロントチャネルログアウト |
| `backchannel_logout_supported` | バックチャネルログアウト |
| `backchannel_logout_session_supported` | セッション付きバックチャネルログアウト |

### WebFinger による Issuer 発見

ユーザーのメールアドレスなどから Issuer を発見する場合。

```http
GET /.well-known/webfinger?
  resource=acct:user@example.com
  &rel=http://openid.net/specs/connect/1.0/issuer
Host: example.com
```

```json
{
  "subject": "acct:user@example.com",
  "links": [
    {
      "rel": "http://openid.net/specs/connect/1.0/issuer",
      "href": "https://auth.example.com"
    }
  ]
}
```


### キャッシュ戦略

```
メタデータのキャッシュ:

1. HTTP キャッシュヘッダーに従う
   Cache-Control: max-age=3600

2. 適度な間隔で更新
   - 一般的には 1-24 時間ごと
   - エラー発生時に再取得

3. JWKS のキャッシュ
   - 鍵ローテーションを考慮
   - kid が見つからない場合は再取得

注意:
  - 本番環境では必ずキャッシュを使用
  - 起動時に 1 回取得し、定期的に更新
```

### セキュリティ考慮事項

| 項目 | 推奨事項 |
|------|----------|
| HTTPS | Discovery エンドポイントは HTTPS 必須 |
| issuer 検証 | レスポンスの issuer と期待値を比較 |
| キャッシュ | 適切な TTL でキャッシュ |
| TLS 証明書 | 有効な証明書を検証 |
| エンドポイント検証 | 取得したエンドポイントが issuer と同一オリジンか確認 |

---

## 参考リンク

- [OpenID Connect Discovery 1.0](https://openid.net/specs/openid-connect-discovery-1_0.html)
- [RFC 8414 - OAuth 2.0 Authorization Server Metadata](https://datatracker.ietf.org/doc/html/rfc8414)
- [WebFinger (RFC 7033)](https://datatracker.ietf.org/doc/html/rfc7033)
