# クライアント認証実装ガイド

## このドキュメントの目的

**クライアント認証**（Client Authentication）の仕組みと7つの認証方式を理解することが目標です。

### 所要時間
⏱️ **約30分**

### 前提知識
- [03. Token Flow](./03-token-endpoint.md)
- OAuth 2.0基礎知識

---

## クライアント認証とは

**クライアント（アプリケーション）の正当性を検証**する仕組み。

### 使用される場面

| エンドポイント | 用途 | 認証必須度 |
|--------------|------|----------|
| **Token Request** | トークン発行 | 必須（Confidential Client） |
| **CIBA認証リクエスト** | バックチャネル認証開始 | 必須 |
| **Token Introspection** | トークン検証 | 推奨 |
| **Token Revocation** | トークン失効 | 推奨 |

**Public Client**（SPA/Mobile）: `client_secret_none` + PKCE必須

---

## アーキテクチャ全体像

### クライアント認証の処理フロー

```
Token Request / CIBA Request等
    ↓
TokenRequestHandler / CibaRequestHandler
    ↓
┌─────────────────────────────────────────────────────┐
│ TokenRequestContext作成                              │
├─────────────────────────────────────────────────────┤
│  - clientSecretBasic: Authorizationヘッダーから抽出  │
│  - clientCert: x-ssl-certヘッダーから抽出（MTLS）    │
│  - parameters: POSTボディ                            │
└─────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────┐
│ ClientAuthenticationHandler.authenticate()          │
├─────────────────────────────────────────────────────┤
│  1. クライアント認証方式の検出                         │
│     - Authorizationヘッダー存在 → client_secret_basic│
│     - client_assertionパラメータ → JWT認証           │
│     - client_idのみ → none                           │
│     - x-ssl-cert存在 → MTLS                          │
│                                                     │
│  2. ClientAuthenticators.get(認証タイプ)             │
│     → 認証方式別のAuthenticator取得（Plugin）         │
│                                                     │
│  3. Authenticator.authenticate()                    │
│     → クライアント認証実行                            │
└─────────────────────────────────────────────────────┘
    ↓
ClientCredentials（認証済み情報）
    - clientId: 認証済みクライアントID
    - authenticationMethod: 使用した認証方式
```

**実装**: [ClientAuthenticationHandler.java](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/clientauthenticator/ClientAuthenticationHandler.java)

---

## 7つの認証方式

### 標準認証方式（5種類）

| 認証方式 | 送信方法 | セキュリティ | 用途 |
|---------|---------|------------|------|
| **client_secret_basic** | Basic認証ヘッダー | ⭐⭐ | 最も一般的（サーバーサイド） |
| **client_secret_post** | POSTボディ | ⭐ | レガシー対応 |
| **client_secret_jwt** | JWT署名（共有鍵HMAC） | ⭐⭐⭐ | 高セキュリティ |
| **private_key_jwt** | JWT署名（秘密鍵RSA/ECDSA） | ⭐⭐⭐⭐ | 最高セキュリティ |
| **none** | 認証なし | - | Public Client（SPA/Mobile+PKCE） |

### FAPI拡張認証方式（2種類）

| 認証方式 | 送信方法 | セキュリティ | 用途 |
|---------|---------|------------|------|
| **tls_client_auth** | クライアント証明書（MTLS） | ⭐⭐⭐⭐⭐ | 金融機関・FAPI準拠 |
| **self_signed_tls_client_auth** | 自己署名証明書（MTLS） | ⭐⭐⭐⭐ | FAPI準拠・開発環境 |

**拡張方式**: FAPIモジュールロード時のみ有効（Plugin）

---

## 1. client_secret_basic（最も一般的）

**実装**: [ClientSecretBasicAuthenticator.java](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/clientauthenticator/ClientSecretBasicAuthenticator.java)

### リクエスト例

```bash
curl -X POST "http://localhost:8080/${TENANT_ID}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -H "Authorization: Basic $(echo -n 'my-client:my-secret' | base64)" \
  -d "grant_type=authorization_code&code=${CODE}&redirect_uri=${REDIRECT_URI}"
```

### 処理フロー

```
1. Authorizationヘッダー取得
   Authorization: Basic Y2xpZW50LWlkOmNsaWVudC1zZWNyZXQ=

2. Base64デコード
   Y2xpZW50LWlkOmNsaWVudC1zZWNyZXQ= → "client-id:client-secret"

3. コロンで分割
   → clientId="client-id", clientSecret="client-secret"

4. ClientConfiguration取得
   clientConfigurationQueryRepository.get(tenant, clientId)

5. client_secret検証
   if (clientConfiguration.clientSecret().equals(clientSecret)) {
     認証成功
   } else {
     invalid_client エラー
   }
```

### 注意点

**Base64エンコード時の注意**:
```bash
# ✅ 正しい（-n オプション付き）
echo -n 'my-client:my-secret' | base64

# ❌ 間違い（改行が入る）
echo 'my-client:my-secret' | base64
```

---

## 2. client_secret_post

**実装**: [ClientSecretPostAuthenticator.java](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/clientauthenticator/ClientSecretPostAuthenticator.java)

### リクエスト例

```bash
curl -X POST "http://localhost:8080/${TENANT_ID}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code&code=${CODE}&redirect_uri=${REDIRECT_URI}&client_id=my-client&client_secret=my-secret"
```

### 処理フロー

```
1. POSTボディからパラメータ取得
   client_id=my-client
   client_secret=my-secret

2. ClientConfiguration取得

3. client_secret検証
   → 成功 or invalid_client
```

### 注意点

- ⚠️ **セキュリティリスク**: client_secretがHTTPボディに平文で含まれる
- ⚠️ **推奨しない**: client_secret_basicを使用すべき

---

## 3. client_secret_jwt（高セキュリティ）

**実装**: [ClientSecretJwtAuthenticator.java](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/clientauthenticator/ClientSecretJwtAuthenticator.java)

### リクエスト例

```bash
curl -X POST "http://localhost:8080/${TENANT_ID}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code&code=${CODE}&redirect_uri=${REDIRECT_URI}&client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer&client_assertion=${JWT}"
```

### JWT構造

**Header**:
```json
{
  "alg": "HS256",
  "typ": "JWT"
}
```

**Payload**:
```json
{
  "iss": "my-client",
  "sub": "my-client",
  "aud": "https://idp-server.example.com/{tenant-id}/v1/tokens",
  "jti": "unique-jwt-id-12345",
  "exp": 1697000000,
  "iat": 1696999000
}
```

**署名**: HMAC-SHA256（client_secretで署名）

### 処理フロー

```
1. client_assertionパラメータ取得（JWT文字列）

2. JWTヘッダー解析
   → alg="HS256"確認

3. JWTペイロード解析
   → iss/sub/aud/exp/iat/jti抽出

4. ClientConfiguration取得（iss=clientId）

5. JWT署名検証（HMAC-SHA256）
   client_secretを秘密鍵として署名検証

6. クレーム検証
   - iss=sub=client_id
   - aud=Token Endpoint URL
   - exp未来
   - jti一意性（リプレイ攻撃防止）
```

---

## 4. private_key_jwt（最高セキュリティ）

**実装**: [PrivateKeyJwtAuthenticator.java](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/clientauthenticator/PrivateKeyJwtAuthenticator.java)

### リクエスト例

client_secret_jwtと同じだが、署名アルゴリズムが異なる：

```bash
curl -X POST "..." \
  -d "client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer&client_assertion=${JWT}"
```

### JWT構造

**Header**:
```json
{
  "alg": "RS256",  // または ES256
  "typ": "JWT",
  "kid": "key-id-12345"  // クライアントの公開鍵識別子
}
```

**Payload**: client_secret_jwtと同じ

**署名**: RSA-SHA256 または ECDSA-SHA256（クライアントの秘密鍵で署名）

### 処理フロー

```
1. JWTヘッダー解析
   → alg="RS256" or "ES256"
   → kid抽出

2. ClientConfiguration取得

3. 公開鍵取得
   - ClientConfiguration.jwksから公開鍵取得（kidで検索）
   - またはjwks_uriから取得

4. JWT署名検証（RSA/ECDSA）
   クライアントの公開鍵で署名検証

5. クレーム検証
   → client_secret_jwtと同じ
```

### メリット

- ✅ **client_secretの共有不要**: 公開鍵のみサーバーに登録
- ✅ **秘密鍵の安全性**: クライアント側で厳重管理
- ✅ **鍵ローテーション**: 複数の公開鍵をサポート（kid切り替え）

---

## 5. none（Public Client）

**実装**: [PublicClientAuthenticator.java](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/clientauthenticator/PublicClientAuthenticator.java)

### リクエスト例

```bash
curl -X POST "http://localhost:8080/${TENANT_ID}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code&code=${CODE}&redirect_uri=${REDIRECT_URI}&client_id=my-spa-client&code_verifier=${VERIFIER}"
```

### 処理フロー

```
1. client_idパラメータのみ取得
   （client_secretなし）

2. ClientConfiguration取得

3. Public Clientチェック
   if (!clientConfiguration.isPublicClient()) {
     invalid_client エラー
   }

4. PKCE検証必須
   if (!hasPkce()) {
     invalid_request エラー（"PKCE required for public client"）
   }

5. 認証成功（PKCEが後続で検証される）
```

### 注意点

- ⚠️ **PKCE必須**: Public Clientは必ずPKCE使用
- ⚠️ **client_secretなし**: client_secretを持たない
- ✅ **SPA/Mobileで使用**: ブラウザ・モバイルアプリ

---

## 6. tls_client_auth（FAPI準拠 - MTLS）

**実装**: [TlsClientAuthAuthenticator.java](../../../../libs/idp-server-core-extension-fapi/src/main/java/org/idp/server/core/openid/extension/fapi/clientauthenticator/TlsClientAuthAuthenticator.java)

**FAPIモジュールロード時のみ有効**

### リクエスト例

```bash
curl -X POST "https://localhost:8080/${TENANT_ID}/v1/tokens" \
  --cert client.crt \
  --key client.key \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code&code=${CODE}&redirect_uri=${REDIRECT_URI}"
```

### 処理フロー

```
1. x-ssl-certヘッダー取得
   （リバースプロキシがクライアント証明書をヘッダーに変換）

2. クライアント証明書解析
   - Subject DN抽出
   - Issuer DN抽出
   - Serial Number抽出

3. ClientConfiguration取得
   - POSTボディのclient_idまたは証明書のSubject DNから

4. 証明書検証
   - ClientConfiguration.tls_client_auth_subject_dnと一致
   - 証明書の有効期限チェック
   - 証明書チェーン検証（信頼されたCA）

5. 認証成功
```

### 設定例（ClientConfiguration）

```json
{
  "client_id": "fapi-client-12345",
  "token_endpoint_auth_method": "tls_client_auth",
  "tls_client_auth_subject_dn": "CN=fapi-client,O=Example Bank,C=JP"
}
```

---

## 7. self_signed_tls_client_auth（FAPI準拠 - 自己署名MTLS）

**実装**: [SelfSignedTlsClientAuthAuthenticator.java](../../../../libs/idp-server-core-extension-fapi/src/main/java/org/idp/server/core/openid/extension/fapi/clientauthenticator/SelfSignedTlsClientAuthAuthenticator.java)

### tls_client_authとの違い

| 項目 | tls_client_auth | self_signed_tls_client_auth |
|------|----------------|---------------------------|
| **証明書発行者** | 信頼されたCA | 自己署名（クライアント自身） |
| **証明書検証** | CA証明書チェーン検証 | 公開鍵フィンガープリント検証 |
| **用途** | 本番環境（金融機関等） | 開発環境・テスト |

### 処理フロー

```
1. クライアント証明書取得

2. 証明書フィンガープリント計算
   SHA-256(証明書の公開鍵)

3. ClientConfiguration取得

4. フィンガープリント検証
   if (clientConfiguration.tls_client_auth_san_dns().equals(fingerprint)) {
     認証成功
   }
```

---

## 認証方式の選択

### ClientAuthenticationHandler の判定ロジック

**実装**: [ClientAuthenticationHandler.java](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/clientauthenticator/ClientAuthenticationHandler.java)

```java
public ClientCredentials authenticate(TokenRequestContext context) {

  // 1. Authorizationヘッダー存在？
  if (context.hasClientSecretBasic()) {
    return clientAuthenticators.get("client_secret_basic").authenticate(context);
  }

  // 2. client_assertionパラメータ存在？
  if (context.hasClientAssertion()) {
    String clientAssertionType = context.clientAssertionType();

    // JWT Bearer?
    if (clientAssertionType.equals("urn:ietf:params:oauth:client-assertion-type:jwt-bearer")) {
      // JWTのalgで判定
      String alg = extractAlg(context.clientAssertion());

      if (alg.startsWith("HS")) {
        return clientAuthenticators.get("client_secret_jwt").authenticate(context);
      } else {
        return clientAuthenticators.get("private_key_jwt").authenticate(context);
      }
    }
  }

  // 3. x-ssl-certヘッダー存在？（MTLS）
  if (context.hasClientCert()) {
    // ClientConfigurationで判定
    ClientConfiguration config = getClientConfiguration(context);

    if (config.tokenEndpointAuthMethod().equals("tls_client_auth")) {
      return clientAuthenticators.get("tls_client_auth").authenticate(context);
    } else if (config.tokenEndpointAuthMethod().equals("self_signed_tls_client_auth")) {
      return clientAuthenticators.get("self_signed_tls_client_auth").authenticate(context);
    }
  }

  // 4. client_id + client_secretパラメータ存在？
  if (context.hasClientIdAndSecret()) {
    return clientAuthenticators.get("client_secret_post").authenticate(context);
  }

  // 5. client_idのみ？
  if (context.hasClientId()) {
    return clientAuthenticators.get("none").authenticate(context);
  }

  // 6. いずれも該当しない
  throw new TokenBadRequestException("invalid_client", "Client authentication failed");
}
```

---

## よくあるエラー

### エラー1: `invalid_client` - クライアント認証失敗

**原因**:
1. client_secret不一致
2. JWT署名検証失敗
3. 証明書検証失敗

**例**:
```json
{
  "error": "invalid_client",
  "error_description": "Client authentication failed"
}
```

**デバッグ**:
```bash
# client_secretの確認
docker exec -it postgres psql -U idp_user -d idp_db -c \
  "SELECT client_id, payload->>'client_secret' FROM client_configuration WHERE client_id='my-client';"

# Base64エンコードの確認
echo -n 'my-client:my-secret' | base64
```

---

### エラー2: `invalid_request` - PKCE必須

Public Client（`client_secret_none`）でPKCEなし：

```json
{
  "error": "invalid_request",
  "error_description": "PKCE is required for public client"
}
```

**対処**: `code_verifier`パラメータを追加

---

## 次のステップ

✅ クライアント認証の仕組みを理解した！

### 📖 次に読むべきドキュメント

- [03. Token Flow](./03-token-endpoint.md) - トークン発行フロー全体
- [06. CIBA Flow](./06-ciba-flow.md) - CIBA認証リクエスト

### 🔗 詳細情報

- [RFC 6749 Section 2.3](https://datatracker.ietf.org/doc/html/rfc6749#section-2.3) - Client Authentication

---

**情報源**:
- [ClientAuthenticationHandler.java](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/clientauthenticator/ClientAuthenticationHandler.java)
- [ClientAuthenticators.java](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/clientauthenticator/ClientAuthenticators.java)

**最終更新**: 2025-10-13
