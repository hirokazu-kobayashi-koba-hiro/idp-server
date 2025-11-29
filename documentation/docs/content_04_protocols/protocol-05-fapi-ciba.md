# FAPI CIBA Profile - プロトコル仕様

## 概要

**FAPI CIBA (Financial-grade API: Client Initiated Backchannel Authentication) Profile** は、金融グレードのセキュリティを備えたバックチャネル認証を実現するためのプロファイルです。

idp-serverは [OpenID FAPI CIBA Security Profile](https://openid.net/specs/openid-financial-api-ciba.html) に完全準拠しており、以下の3つの仕様を組み合わせています：

- **FAPI Part 1 (Baseline)**: 読み取り専用APIの基本セキュリティ
- **FAPI Part 2 (Advanced)**: 書き込みAPIの高度なセキュリティ
- **CIBA Core**: バックチャネル認証フロー

## ユースケース

FAPI CIBAは以下のような高セキュリティが要求されるシナリオで使用されます：

| ユースケース | 説明 | 具体例 |
|------------|------|--------|
| **オープンバンキング** | PSD2準拠の金融API | 口座情報参照、送金実行 |
| **デバイス分離認証** | 操作端末と認証端末が異なる | ATM送金、コールセンター認証 |
| **リモート承認** | 別デバイスでの認証承認 | スマホでのプッシュ通知承認 |
| **高額取引** | 金融取引の多要素認証 | 大口送金、証券取引 |

## FAPI CIBA vs 標準CIBA

### セキュリティ要件比較

| 項目 | 標準CIBA | FAPI CIBA |
|------|---------|-----------|
| **リクエストオブジェクト** | 任意 | **必須（署名付き）** |
| **署名アルゴリズム** | RS256等も可 | **PS256/ES256のみ** |
| **リクエスト有効期限** | 制限なし | **最大60分** |
| **クライアント認証** | client_secret系も可 | **private_key_jwt, mTLS のみ** |
| **binding_message** | 任意 | **authorization_details未使用時は必須** |
| **トークン配信モード** | push/poll/ping | **poll/pingのみ（push禁止）** |
| **トークンバインディング** | 任意 | **Sender-constrained必須（mTLS）** |
| **aud claim** | 任意 | **必須（Issuer URL含む）** |

## プロトコルフロー

### 1. バックチャネル認証リクエスト

```http
POST /backchannel/authentications HTTP/1.1
Host: idp-server.example.com
Content-Type: application/x-www-form-urlencoded

request=eyJhbGciOiJQUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6InNlbGZfcmVxdWVzdF9rZXlfcHMyNTYifQ...
&client_id=fapi-ciba-client
```

**リクエストオブジェクト（JWT）の内容**:
```json
{
  "alg": "PS256",
  "kid": "self_request_key_ps256"
}
.
{
  "scope": "openid payment_initiation",
  "binding_message": "TX-12345: €500 to ACME Corp",
  "user_code": "123456",
  "login_hint": "device:auth_device_id,idp:idp-server",
  "authorization_details": [
    {
      "type": "payment_initiation",
      "instructedAmount": {
        "currency": "EUR",
        "amount": "500.00"
      },
      "creditorName": "ACME Corp",
      "creditorAccount": {
        "iban": "DE02100100109307118603"
      }
    }
  ],
  "client_id": "fapi-ciba-client",
  "aud": "https://idp-server.example.com",
  "iss": "fapi-ciba-client",
  "exp": 1234567890,
  "iat": 1234564290,
  "nbf": 1234564290,
  "jti": "unique-request-id"
}
```

**レスポンス**:
```json
{
  "auth_req_id": "ea2856d7-9aab-40c6-ae71-f8db93602eab",
  "expires_in": 600,
  "interval": 5
}
```

### 2. 認証デバイスでの承認

ユーザーはスマートフォン等の認証デバイスでプッシュ通知を受け取り、以下の情報を確認：

- **binding_message**: "TX-12345: €500 to ACME Corp"
- **authorization_details**: 支払い詳細（金額、送金先等）

生体認証（FIDO-UAF）またはパスワードで承認します。

### 3. トークンリクエスト（ポーリング）

```http
POST /tokens HTTP/1.1
Host: idp-server.example.com
Content-Type: application/x-www-form-urlencoded
SSL-Client-Cert: <client-certificate>

grant_type=urn:openid:params:grant-type:ciba
&auth_req_id=ea2856d7-9aab-40c6-ae71-f8db93602eab
&client_id=fapi-ciba-client
```

**レスポンス**:
```json
{
  "access_token": "eyJhbGc...",
  "id_token": "eyJhbGc...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "refresh_token": "refresh..."
}
```

**アクセストークン（JWT）の内容**:
```json
{
  "iss": "https://idp-server.example.com",
  "sub": "user-123",
  "aud": "resource-server",
  "exp": 1234567890,
  "iat": 1234564290,
  "scope": "openid payment_initiation",
  "cnf": {
    "x5t#S256": "bwcK0esc3ACC3DB2Y5_lESsXE8o9ltc05O89jdN-dg2"
  }
}
```

**重要**: `cnf:x5t#S256` がクライアント証明書のサムプリントで、mTLSバインディングを実現します。

## FAPI要件マッピング

### 要件の階層構造

FAPI CIBAは **3つの仕様を積み重ねた構造** になっています：

```
┌─────────────────────────────────────────┐
│ FAPI CIBA 5.2.2                         │ ← CIBA固有の追加要件
│ - binding_message要件                    │
│ - Push mode禁止                          │
│ - Signed request object必須              │
├─────────────────────────────────────────┤
│ FAPI Part 2 (Advanced) 5.2.2            │ ← Part 1の一部を上書き
│ - Client authentication制限（上書き）     │
│ - Sender-constrained tokens必須          │
│ - Request object lifetime制限            │
├─────────────────────────────────────────┤
│ FAPI Part 1 (Baseline) 5.2.2            │ ← 基本セキュリティ要件
│ - Confidential client必須                │
│ - 鍵サイズ・エントロピー要件               │
│ - トークン推測不可能性                    │
├─────────────────────────────────────────┤
│ 基本OIDC/OAuth 2.0                       │ ← 標準プロトコル
└─────────────────────────────────────────┘
```

**FAPI CIBA準拠 = すべての層の要件を満たす**

### 重要な上書き関係

| 要件 | FAPI Part 1 | FAPI Part 2（上書き） | 最終適用 |
|------|------------|---------------------|---------|
| **Client Authentication** | `private_key_jwt`<br>`client_secret_jwt` ✅<br>mTLS | `private_key_jwt`<br>`client_secret_jwt` ❌<br>mTLS | **Part 2が優先**<br>secret系は禁止 |
| **PKCE** | 必須（5.2.2-7） | 免除（CIBAには不適用） | **Part 2が優先**<br>CIBA不要 |

### FAPI Part 1 (Baseline) 5.2.2 要件

| # | 要件 | CIBA適用 | 実装 | 備考 |
|---|------|---------|------|------|
| 1 | **Confidential clients必須** | ✅ 適用 | `throwExceptionIfNotConfidentialClient()` | Public client禁止 |
| 2 | **Public clients推奨** | ❌ 不適用 | - | FAPI CIBAはconfidentialのみ |
| 3 | **Client secret entropy (OIDC 16.19)** | ✅ 適用 | - | 256/512 bit minimum |
| 4 | **Client authentication methods** | ⚠️ **上書き** | `throwExceptionIfInvalidClientAuthenticationMethod()` | FAPI Part 2で変更 |
| 5 | **RSA key ≥ 2048 bits** | ✅ 適用 | JWKs検証 | Request object署名、private_key_jwt認証 |
| 6 | **EC key ≥ 160 bits** | ✅ 適用 | JWKs検証 | 実際はES256（256 bits）が最小 |
| 7 | **PKCE with S256** | ❌ 不適用 | - | CIBAにはauthorization codeなし |
| 8 | **Pre-register redirect_uri** | ❌ 不適用 | - | CIBAにはredirectなし |
| 9 | **Require redirect_uri in request** | ❌ 不適用 | - | 同上 |
| 10 | **Exact match redirect_uri** | ❌ 不適用 | - | 同上 |
| 11 | **User authentication LoA** | ✅ 適用 | Authentication Policy | 認証ポリシーで制御 |
| 12 | **Explicit user approval** | ✅ 適用 | - | binding_message/authorization_details |
| 13 | **Reject reused authorization code** | ❌ 不適用 | - | CIBAにはcodeなし |
| 14 | **Token response format (RFC6749 4.1.4)** | ✅ 適用 | - | 標準準拠 |
| 15 | **Return granted scopes (front channel)** | ❌ 不適用 | - | CIBAはbackchannel |
| 16 | **Non-guessable tokens (RFC6749 10.10)** | ✅ 適用 | - | auth_req_id, tokens |
| 17 | **Grant details disclosure (OIDC 16.18)** | ✅ 適用 | - | binding_message |
| 18 | **Token revocation (OIDC 16.18)** | ✅ 適用 | - | Revocation endpoint |
| 19 | **invalid_client for mismatched client_id** | ✅ 適用 | - | エラーハンドリング |
| 20 | **HTTPS redirect_uri** | ❌ 不適用 | - | CIBAにはredirectなし |
| 21 | **Access token lifetime < 10min** | ⚠️ 推奨 | - | Sender-constrainedなら任意 |
| 22 | **OIDD support** | ✅ 適用 | - | Discovery必須 |

### FAPI Part 2 (Advanced) 5.2.2 要件

FAPI Part 2 (Advanced) は **書き込みAPI向けの高度なセキュリティプロファイル** で、PAR (Pushed Authorization Request)、JARM (JWT Secured Authorization Response Mode)、より強力なクライアント認証を追加しています。

**CIBA適用時の注意**: FAPI Part 2の多くの要件はフロントチャネル（authorization endpoint）向けですが、以下の要件がCIBAに適用されます。

#### Authorization Server要件（CIBA適用分のみ）

| # | FAPI Part 2 要件 | CIBA適用 | 実装 | 備考 |
|---|-----------------|---------|------|------|
| - | **FAPI Part 1準拠（5.2.2-7除く）** | ⚠️ 部分適用 | - | PKCE要件(5.2.2-7)はCIBA不適用 |
| 1 | **JWS signed request object必須** | ✅ 適用 | `throwExceptionIfNotSignedRequestObject()` | CIBAでは`request`パラメータで必須 |
| 2 | **response_type制約** | ❌ 不適用 | - | CIBAにはauthorization endpointなし |
| 5 | **Sender-constrained tokens必須** | ✅ 適用 | `throwIfNotSenderConstrainedAccessToken()` | mTLSバインディング必須 |
| 6 | **MTLSサポート** | ✅ 適用 | mTLS証明書バインディング | `cnf:x5t#S256`クレーム |
| 10 | **署名済みrequest objectのみ使用** | ✅ 適用 | `throwExceptionIfNotSignedRequestObject()` | 外部パラメータ無視 |
| 11 | **PAR endpoint (任意)** | ❌ 不適用 | - | CIBAにはPAR不要 |
| 13 | **Request object lifetime ≤ 60min** | ✅ 適用 | `throwExceptionIfInvalidRequestObjectLifetime()` | `exp - nbf ≤ 60分` |
| **14** | **Client authentication (上書き)** | ✅ **適用** | `throwExceptionIfInvalidClientAuthenticationMethod()` | **重要: Part 1の5.2.2-4を上書き** |
| 15 | **aud claim必須** | ✅ 適用 | `throwExceptionIfNotContainsAud()` | Issuer URL含む |
| 16 | **Public clients禁止** | ✅ 適用 | `throwExceptionIfNotConfidentialClient()` | Confidentialのみ |
| 17 | **nbf claim検証** | ✅ 適用 | `throwExceptionIfInvalidRequestObjectLifetime()` | `nbf`は60分以内の過去 |
| 18 | **PAR + PKCE (S256)** | ❌ 不適用 | - | CIBAにはPAR不要 |

#### 5.2.2-14: Client Authentication Methods（重要な上書き）

**FAPI Part 1 (5.2.2-4)**: 以下のいずれかを許可
- `private_key_jwt`
- `client_secret_jwt` ✅
- mTLS (`tls_client_auth`, `self_signed_tls_client_auth`)

**FAPI Part 2 (5.2.2-14)**: 以下のみ許可（**client_secret_jwt禁止**）
- `private_key_jwt` ✅
- mTLS (`tls_client_auth`, `self_signed_tls_client_auth`) ✅

**実装**:
```java
void throwExceptionIfInvalidClientAuthenticationMethod(CibaRequestContext context) {
  ClientAuthenticationType authenticationType = context.clientAuthenticationType();

  boolean isValid =
      authenticationType.isPrivateKeyJwt()
          || authenticationType.isTlsClientAuth()
          || authenticationType.isSelfSignedTlsClientAuth();

  if (!isValid) {
    throw new BackchannelAuthenticationUnauthorizedException(
        "invalid_client",
        String.format(
            "FAPI CIBA Profile requires client authentication method to be one of: private_key_jwt, tls_client_auth, self_signed_tls_client_auth. Current method: %s",
            authenticationType.name()));
  }
}
```

**エラー例**:
```http
HTTP/1.1 401 Unauthorized
Content-Type: application/json

{
  "error": "invalid_client",
  "error_description": "FAPI CIBA Profile requires client authentication method to be one of: private_key_jwt, tls_client_auth, self_signed_tls_client_auth. Current method: client_secret_post"
}
```

### FAPI CIBA 5.2.2 固有要件

| # | 要件 | 実装 | HTTPステータス |
|---|------|------|---------------|
| 1 | **Signed request object必須** | `throwExceptionIfNotSignedRequestObject()` | 400 |
| 2 | **Request object lifetime ≤ 60min** | `throwExceptionIfInvalidRequestObjectLifetime()` | 400 |
| 3 | **Signing algorithm PS256/ES256** | `throwExceptionIfInvalidSigningAlgorithm()` | 400 |
| 4 | **Confidential clients** | `throwExceptionIfNotConfidentialClient()` | **401** |
| 5 | **binding_message (条件付き)** | `throwExceptionIfMissingBindingMessage()` | 400 |
| 6 | **Push mode禁止** | `throwExceptionIfPushMode()` | 400 |
| 7 | **Client authentication restrictions** | `throwExceptionIfInvalidClientAuthenticationMethod()` | **401** |
| 11 | **aud claim必須** | `throwExceptionIfNotContainsAud()` | 400 |
| - | **Sender-constrained tokens** | `throwIfNotSenderConstrainedAccessToken()` | 400 |

## エラーレスポンス

### invalid_client (HTTP 401)

**CIBA Core Section 13準拠**: クライアント認証失敗は401を返す

```json
{
  "error": "invalid_client",
  "error_description": "FAPI CIBA Profile requires client authentication method to be one of: private_key_jwt, tls_client_auth, self_signed_tls_client_auth. Current method: client_secret_post"
}
```

### invalid_request (HTTP 400)

```json
{
  "error": "invalid_request",
  "error_description": "FAPI CIBA Profile requires request object lifetime to be no longer than 60 minutes. Current lifetime: 75 minutes"
}
```

## mTLS トークンバインディング

### Sender-constrained Access Tokens

FAPI CIBAでは、アクセストークンを**クライアント証明書にバインド**することが必須です。

#### 1. トークン発行時

```
クライアント → nginx (mTLS終端) → idp-server
           (mTLS)   (HTTP + X-SSL-Cert header)

1. nginx: クライアント証明書検証
2. nginx: 証明書をPEMエンコードしてHTTPヘッダーで転送
3. idp-server: 証明書サムプリント（SHA-256）計算
4. idp-server: cnf:x5t#S256 としてアクセストークンに埋め込み
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

**重要**: トークンと証明書の両方が揃わないとAPIアクセス不可

### 証明書サムプリントの計算

```bash
# 証明書からサムプリント取得
openssl x509 -in client-cert.pem -noout -fingerprint -sha256 | \
  cut -d= -f2 | tr -d ':' | xxd -r -p | base64 | tr '+/' '-_' | tr -d '='

# 結果例
bwcK0esc3ACC3DB2Y5_lESsXE8o9ltc05O89jdN-dg2
```

## 実装チェックリスト

### サーバー設定

- [ ] `token_endpoint_auth_methods_supported` に `private_key_jwt`, `tls_client_auth`, `self_signed_tls_client_auth` のみ含む
- [ ] `grant_types_supported` に `urn:openid:params:grant-type:ciba` 含む
- [ ] `backchannel_token_delivery_modes_supported` に `poll`, `ping` のみ含む（`push` 除外）
- [ ] `backchannel_authentication_request_signing_alg_values_supported` に `PS256`, `ES256` 含む
- [ ] `tls_client_certificate_bound_access_tokens` を `true` に設定
- [ ] `authorization_details_types_supported` に対応する型を定義

### クライアント設定

- [ ] `token_endpoint_auth_method` を `private_key_jwt`, `tls_client_auth`, `self_signed_tls_client_auth` のいずれかに設定
- [ ] `grant_types` に `urn:openid:params:grant-type:ciba` 含む
- [ ] `backchannel_token_delivery_mode` を `poll` または `ping` に設定
- [ ] `backchannel_authentication_request_signing_alg` を `PS256` または `ES256` に設定
- [ ] `tls_client_certificate_bound_access_tokens` を `true` に設定
- [ ] `jwks` に PS256/ES256対応の公開鍵を登録

### インフラ設定

- [ ] nginx/AWS ALBでmTLS終端を設定
- [ ] クライアント証明書をHTTPヘッダー（`X-SSL-Cert`）で転送
- [ ] 証明書検証ロジック実装
- [ ] BFF Serverでクライアント証明書を安全に管理（ネイティブアプリの場合）

## セキュリティ考慮事項

### 1. 暗号鍵の要件

#### 鍵長の最小要件（FAPI Part 1 5.2.2-5, 5.2.2-6）

FAPI CIBAでは、すべての署名・認証に使用する鍵が以下の鍵長要件を満たす必要があります：

| アルゴリズム | 鍵タイプ | 最小鍵長 | 推奨鍵長 | 用途 |
|------------|---------|---------|---------|------|
| **RS256, RS384, RS512** | RSA | 2048 bits | 3072 bits | Request object署名、private_key_jwt、ID Token署名 |
| **PS256, PS384, PS512** | RSA-PSS | 2048 bits | 3072 bits | 同上（FAPI CIBAで推奨） |
| **ES256** | ECDSA (P-256) | 256 bits | 256 bits | Request object署名、private_key_jwt |
| **ES384** | ECDSA (P-384) | 384 bits | 384 bits | 同上 |
| **ES512** | ECDSA (P-521) | 521 bits | 521 bits | 同上 |

**適用箇所**:

1. **クライアントのJWKs**（`jwks` / `jwks_uri`登録）
   - Request object署名用の鍵
   - private_key_jwt認証用の鍵

2. **Authorization ServerのJWKs**
   - ID Token署名用の鍵
   - Access Token署名用の鍵（JWT形式の場合）

**鍵生成例**:
```bash
# RSA 2048 bits（最小）
openssl genrsa -out private-key.pem 2048

# RSA 3072 bits（推奨）
openssl genrsa -out private-key.pem 3072

# ECDSA P-256（ES256用）
openssl ecparam -genkey -name prime256v1 -out private-key.pem

# ECDSA P-384（ES384用）
openssl ecparam -genkey -name secp384r1 -out private-key.pem
```

**注意**: 仕様上は「楕円曲線160 bits以上」ですが、実際にはES256（256 bits）が最小の安全な鍵長です。

### 2. リクエストオブジェクト署名検証

- ✅ **必須**: PS256/ES256署名（RSA ≥ 2048 bits, EC ≥ 256 bits）
- ✅ **必須**: nbf/exp検証（最大60分）
- ✅ **必須**: aud claim検証（Issuer URL含む）
- ✅ **推奨**: jti (JWT ID) でリプレイ攻撃防止

### 3. クライアント認証

- ✅ **禁止**: client_secret_basic, client_secret_post, client_secret_jwt
- ✅ **必須**: private_key_jwt（鍵長要件満たすこと）または mTLS
- ✅ **推奨**: クライアント証明書のローテーション（最大1年）

### 4. トークンバインディング

- ✅ **必須**: Sender-constrained access tokens (mTLS binding)
- ✅ **必須**: cnf:x5t#S256 クレーム
- ✅ **推奨**: トークン有効期限を短く設定（10分以下）

### 5. Authorization Details

- ✅ **推奨**: 細かい権限制御にauthorization_details使用
- ✅ **推奨**: authorization_details使用時はbinding_message省略可能
- ✅ **必須**: ユーザーに承認内容を明確に表示

## パフォーマンス考慮事項

| 項目 | 推奨値 | 理由 |
|------|-------|------|
| **auth_req_id有効期限** | 10分 | ユーザー操作時間を考慮 |
| **ポーリング間隔** | 5秒 | サーバー負荷とUX のバランス |
| **アクセストークン有効期限** | 10分以下 | Sender-constrainedなら短く |
| **リフレッシュトークン** | 推奨 | 長時間セッション維持 |

## 関連ドキュメント

### 仕様書

- [OpenID FAPI CIBA Security Profile](https://openid.net/specs/openid-financial-api-ciba.html)
- [FAPI 1.0 Baseline (Part 1)](https://openid.net/specs/openid-financial-api-part-1-1_0.html)
- [FAPI 1.0 Advanced (Part 2)](https://openid.net/specs/openid-financial-api-part-2-1_0.html)
- [OpenID Connect CIBA Core](https://openid.net/specs/openid-client-initiated-backchannel-authentication-core-1_0.html)
- [RFC 8705 - OAuth 2.0 Mutual-TLS](https://datatracker.ietf.org/doc/html/rfc8705)
- [RFC 9396 - Rich Authorization Requests](https://datatracker.ietf.org/doc/html/rfc9396)

### idp-server ドキュメント

- [Concept: FAPI](../content_03_concepts/concept-22-fapi.md)
- [How-to: FAPI CIBA Profile 設定ガイド](../content_05_how-to/how-to-19-fapi-ciba-profile.md)
- [How-to: CIBA + FIDO-UAF](../content_05_how-to/how-to-12-ciba-flow-fido-uaf.md)
- [Protocol: CIBA Flow](./protocol-02-ciba-flow.md)
- [Protocol: CIBA + Rich Authorization Requests](./protocol-04-ciba-rar.md)

---

**作成日**: 2025-11-29
**対象**: システムアーキテクト、セキュリティエンジニア
**習得スキル**: FAPI CIBA Profile完全理解、金融グレードAPI実装
