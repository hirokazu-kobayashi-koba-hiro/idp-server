---
name: spec-client-auth
description: クライアント認証（Client Authentication）機能の開発・修正を行う際に使用。7種のクライアント認証方式、private_key_jwt、tls_client_auth実装時に役立つ。
---

# クライアント認証（Client Authentication）開発ガイド

## ドキュメント

- `documentation/docs/content_06_developer-guide/03-application-plane/10-client-authentication.md` - クライアント認証実装ガイド
- `documentation/docs/content_03_concepts/01-foundation/concept-03-client.md` - クライアント概念

## 機能概要

クライアント認証は、クライアントの正当性を検証する層。
- **7種の認証方式**: client_secret_basic, client_secret_post, client_secret_jwt, private_key_jwt, tls_client_auth, self_signed_tls_client_auth, none
- **JWT Assertion検証**: JWS署名検証
- **mTLS認証**: クライアント証明書検証

## モジュール構成

```
libs/
├── idp-server-core/                         # クライアント認証コア
│   └── .../oauth/clientauthenticator/
│       ├── ClientAuthenticationHandler.java
│       ├── ClientSecretBasicAuthenticator.java
│       ├── ClientSecretPostAuthenticator.java
│       ├── ClientSecretJwtAuthenticator.java
│       ├── PrivateKeyJwtAuthenticator.java
│       └── clientcredentials/
│           └── ClientCredentials.java
│
├── idp-server-core-extension-fapi/          # mTLS認証（FAPI）
│   └── .../extension/fapi/
│       ├── TlsClientAuthAuthenticator.java
│       └── SelfSignedTlsClientAuthAuthenticator.java
│
└── idp-server-control-plane/               # 管理API
    └── .../management/client/
        └── ClientManagementApi.java
```

## ClientAuthenticationHandler

`idp-server-core/oauth/clientauthenticator/` 内:

ClientAuthenticationHandlerが、token_endpoint_auth_methodに応じた適切なAuthenticatorを選択します。

## 認証方式別実装

### 1. client_secret_basic

```java
public class ClientSecretBasicAuthenticator {
    // HTTP Basic認証
    // Authorization: Basic base64(client_id:client_secret)
}
```

### 2. client_secret_post

```java
public class ClientSecretPostAuthenticator {
    // リクエストボディでclient_id, client_secretを送信
}
```

### 3. client_secret_jwt

```java
public class ClientSecretJwtAuthenticator {
    // JWT Assertionをclient_secretで署名（HMAC）
    // client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer
}
```

### 4. private_key_jwt

```java
public class PrivateKeyJwtAuthenticator {
    // JWT Assertionをクライアント秘密鍵で署名（RSA/ECDSA）
    // JWKSエンドポイントから公開鍵を取得して検証
    // client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer
}
```

### 5. tls_client_auth (mTLS)

`idp-server-core-extension-fapi/` 内:

```java
public class TlsClientAuthAuthenticator {
    // クライアント証明書のSubject DNを検証
    // Client.tlsClientAuthSubjectDn と一致確認
}
```

### 6. self_signed_tls_client_auth (mTLS)

```java
public class SelfSignedTlsClientAuthAuthenticator {
    // クライアント証明書全体を検証
    // Client.tlsClientAuthCertificate と一致確認
}
```

### 7. none

```java
// Public Client用（認証なし）
// PKCE必須
```

## JWT Assertion形式

```json
{
  "iss": "client_id",
  "sub": "client_id",
  "aud": "https://idp.example.com/token",
  "jti": "unique-id",
  "exp": 1234567890,
  "iat": 1234567890
}
```

## クライアント設定による認可制御

クライアント認証方式だけでなく、クライアント設定で認可動作を制御できる。

### スコープ制限

クライアントの `scope` フィールドで、利用可能なスコープを制限する。

```json
{
  "scope": "openid profile"  // emailスコープを除外 → UserInfoからemail消失
}
```

サーバーの `scopes_supported` は Discovery 表示専用。実際のフィルタリングはクライアント設定で行う。

### grant_types制限

```json
{
  "grant_types": ["authorization_code"]  // refresh_tokenを除外 → RT発行不可
}
```

### redirect_uri検証

```json
{
  "redirect_uris": ["https://app.example.com/callback"]
  // 登録されていないURIへの認可リクエスト → error=invalid_request
}
```

RFC 6749 Section 3.1.2.4 に準拠した完全一致検証。

### response_types制限

```json
{
  "response_types": ["code"]  // token, id_token等を除外
}
```

### トークン有効期限のクライアント別オーバーライド

認可サーバー設定のデフォルト値をクライアント単位で上書きできる。

```json
{
  "access_token_duration": 1800,   // サーバーデフォルトより短く
  "id_token_duration": 600,
  "refresh_token_duration": 86400
}
```

### リフレッシュトークン戦略のクライアント別オーバーライド

```json
{
  "refresh_token_strategy": "FIXED",    // FIXED: 元のRT期限維持, EXTENDS: リフレッシュ時に延長
  "rotate_refresh_token": true           // true: リフレッシュ時に新RT発行（旧RT無効化）
}
```

### tos_uri / policy_uri による再同意トリガー

```json
{
  "tos_uri": "https://example.com/tos-v2",     // 変更すると既存同意を無効化
  "policy_uri": "https://example.com/policy-v2" // 次回認可時に再同意画面が表示される
}
```

スコープ拡張時も同様に再同意がトリガーされる。

### サーバーとクライアントの認証方式整合性

クライアントの `token_endpoint_auth_method` は、サーバーの `token_endpoint_auth_methods_supported` に含まれている必要がある。

```json
// サーバー: "token_endpoint_auth_methods_supported": ["client_secret_basic", "client_secret_post"]
// クライアント: "token_endpoint_auth_method": "none"
// → トークンリクエスト時にエラー
```

`none` を使う場合はサーバー側にも `none` を追加する必要がある。

## E2Eテスト

```
e2e/src/tests/
└── spec/
    ├── oidc_core_9_client_authenticartion.test.js  # クライアント認証仕様
    ├── rfc6749_4_1_code_secret_basic.test.js       # client_secret_basic
    └── rfc7523_jwt_bearer_assertion.test.js        # JWT Assertion
```

## コマンド

```bash
# ビルド
./gradlew :libs:idp-server-core:compileJava
./gradlew :libs:idp-server-core-extension-fapi:compileJava

# テスト
cd e2e && npm test -- spec/oidc_core_9_client_authenticartion.test.js
cd e2e && npm test -- spec/rfc7523_jwt_bearer_assertion.test.js
```

## トラブルシューティング

### client_secret認証失敗
- client_secretが正しいか確認
- client_secret_basic: Base64エンコーディングを確認
- client_secret_post: パラメータ名を確認

### private_key_jwt検証失敗
- JWKSエンドポイントが正しいか確認
- JWT署名アルゴリズム（RS256, ES256）を確認
- JWT Assertionのaud, iss, subを確認

### mTLS認証失敗
- クライアント証明書が正しく送信されているか確認
- tls_client_auth: Subject DNが一致するか確認
- self_signed_tls_client_auth: 証明書全体が一致するか確認
