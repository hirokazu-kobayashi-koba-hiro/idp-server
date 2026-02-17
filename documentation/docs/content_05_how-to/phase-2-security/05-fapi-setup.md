# FAPI（Financial-grade API）セットアップ

## このドキュメントの目的

**FAPI 1.0 準拠の認可サーバー設定**を行い、金融グレードのセキュリティを実現することが目標です。
OIDF（OpenID Foundation）適合性テストに合格するための設定パラメータについても解説します。

### 所要時間
⏱️ **約30分**

### 前提条件
- [テナント作成](../phase-1-foundation/03-tenant-setup.md)が完了済み
- クライアント登録が完了済み
- mTLS対応の環境構築（リバースプロキシまたはロードバランサーでクライアント証明書を処理）
- 管理者トークンを取得済み

---

## FAPIとは

**FAPI（Financial-grade API）** は、OpenID Foundationが策定したOAuth 2.0 / OpenID Connectのセキュリティプロファイルです。金融APIをはじめとする高セキュリティが求められるAPIを保護するための追加要件を定義しています。

```
セキュリティレベル:

OAuth 2.0（基本）
  └─ OpenID Connect（認証追加）
       └─ FAPI 1.0 Baseline（セキュリティ強化）
            └─ FAPI 1.0 Advanced（最高レベル）
```

### FAPI 1.0 Baseline と Advanced の違い

| 要件 | Baseline | Advanced |
|:-----|:--------:|:--------:|
| PKCE S256 必須 | 常に必須 | PAR使用時に必須 |
| クライアント認証制限 | client_secret_basic/post 禁止 | + client_secret_jwt も禁止 |
| redirect_uri https必須 | 必須 | 必須 |
| nonce 必須（openid scope時） | 必須 | 必須 |
| state 必須（openid scope なし時） | 必須 | 必須 |
| Request Object (署名付きJWT) | 不要 | 必須 |
| 署名アルゴリズム制限 (PS256/ES256) | なし | 必須 |
| Request Object の nbf/exp/aud | 不要 | 必須 |
| Sender-Constrained Access Token (mTLS) | 不要 | 必須 |
| Public Client 禁止 | なし | 禁止 |
| JARM (JWT応答モード) | 不要 | code + jwt で必須 |

---

## FAPIプロファイルの決定メカニズム

idp-serverでは、**リクエストに含まれるscopeの値**に基づいてFAPIプロファイルが自動選択されます。

```
リクエストの scope 値
  ↓
scope ∩ fapi_advance_scopes ≠ ∅  → FAPI_ADVANCE プロファイル
scope ∩ fapi_baseline_scopes ≠ ∅  → FAPI_BASELINE プロファイル
scope に "openid" 含む            → OIDC プロファイル
それ以外                          → OAuth 2.0 プロファイル
```

**優先順位**: FAPI_ADVANCE > FAPI_BASELINE > OIDC > OAuth 2.0

この設計により、同じテナント内で通常のOIDCリクエストとFAPIリクエストを共存できます。scopeに `transfers`（FAPI Advancedスコープ）を含むリクエストのみがFAPI Advanced検証を受けます。

---

## Step 1: 認可サーバー設定

### 設定パラメータ一覧

FAPI認定取得に必要な認可サーバー設定パラメータを以下に示します。

#### プロファイル決定用（extension）

| パラメータ | 説明 | 設定例 |
|:---------|:-----|:------|
| `fapi_baseline_scopes` | このスコープを含むリクエストをFAPI Baselineとして検証 | `["read", "account"]` |
| `fapi_advance_scopes` | このスコープを含むリクエストをFAPI Advancedとして検証 | `["write", "transfers"]` |

**設定しない場合**: FAPIプロファイルが適用されず、通常のOIDC/OAuth 2.0として処理されます。FAPI固有のセキュリティ検証（PKCE S256強制、クライアント認証制限、mTLSバインディング等）は一切行われません。

#### セキュリティ設定

| パラメータ | 説明 | FAPI要件 | デフォルト |
|:---------|:-----|:---------|:---------|
| `tls_client_certificate_bound_access_tokens` | アクセストークンにクライアント証明書をバインド | Advanced: **必須 `true`** | `false` |
| `require_signed_request_object` | Request Object内に全パラメータ（scope含む）を必須化 | Advanced: 推奨 `true` | `false` |
| `pushed_authorization_request_endpoint` | PARエンドポイントURL | Advanced (PAR variant): 必要 | `""` |
| `mtls_endpoint_aliases` | mTLSエンドポイントのエイリアスマップ | Advanced: 必要 | `{}` |
| `authorization_response_iss_parameter_supported` | 認可レスポンスにissパラメータを含める (RFC 9207) | 推奨 `true` | `false` |

### 各パラメータの詳細

#### `tls_client_certificate_bound_access_tokens`

FAPI 1.0 Advanced Section 5.2.2 clause 5/6:
> "shall only issue sender-constrained access tokens"

```
true の場合:
  クライアント証明書 → SHA-256 thumbprint → アクセストークンの cnf クレームに埋め込み
  リソースサーバーがトークン使用時に証明書の一致を検証

false の場合:
  アクセストークンはベアラートークン（誰でも使用可能）
  ⚠️ FAPI Advancedリクエスト時にエラー:
  "shall only issue sender-constrained access tokens, but server
   tls_client_certificate_bound_access_tokens is false"
```

**重要**: この設定はサーバーレベルとクライアントレベルの**両方**で `true` が必要です。どちらか一方が `false` でもFAPI Advancedリクエストはエラーになります。

#### `require_signed_request_object`

FAPI 1.0 Advanced Section 5.2.2 clause 13 および RFC 9101 Section 6.3:
> "shall require the request object to contain all the authorization request parameters"

```
true の場合:
  Request Object JWT 内に scope クレームが必須
  → scope がJWT外（クエリパラメータ）にあってもJWT内のものだけが使用される

false の場合:
  scope は Request Object JWT の外側（クエリパラメータ）でも受け入れられる
  ⚠️ FAPI Advanced仕様では全パラメータをRO内に含めることを要求しているため、
  false だと仕様に厳密には準拠しないが、検証自体はエラーにならない
```

**注意**: `false` でもFAPI Advancedの他のチェック（署名、aud、nbf/exp等）は通過します。ただしOIDFテストスイートがscopeをRequest Object内に含めて送信するケースが多いため、`false` でもテストが通る可能性はあります。厳密な仕様準拠のためには `true` を推奨します。

#### `pushed_authorization_request_endpoint`

RFC 9126 に基づくPARエンドポイントのURL。

```
設定済みの場合:
  - PARエンドポイントが利用可能になる
  - client_assertion JWTの aud 検証でPARエンドポイントURLも受け入れる
  - Discovery応答 (.well-known) にPARエンドポイントが含まれる

未設定（空文字）の場合:
  - PARは利用不可
  - OIDFテストの pushed variant は実行不可
```

#### `mtls_endpoint_aliases`

RFC 8705 Section 5: mTLSエンドポイントのエイリアスマッピング。

```
設定済みの場合:
  - Discovery応答にmTLSエンドポイントが公開される
  - client_assertion JWTの aud 検証でmTLSエイリアスURLも受け入れる
  - クライアントはmTLSエンドポイント経由でアクセスできる

未設定の場合:
  - mTLSクライアント認証（tls_client_auth）は利用可能だが
    mTLSエンドポイント経由のアクセスは不可
  - ⚠️ OIDFテストはmTLSエンドポイントを使用するため失敗する
```

### 設定例（FAPI Advanced対応）

```bash
curl -X PUT "https://api.local.dev/v1/management/tenants/{tenant-id}/authorization-server" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "issuer": "https://api.local.dev/{tenant-id}",
    "authorization_endpoint": "https://api.local.dev/{tenant-id}/v1/authorizations",
    "token_endpoint": "https://api.local.dev/{tenant-id}/v1/tokens",
    "userinfo_endpoint": "https://api.local.dev/{tenant-id}/v1/userinfo",
    "jwks_uri": "https://api.local.dev/{tenant-id}/v1/jwks",
    "pushed_authorization_request_endpoint": "https://api.local.dev/{tenant-id}/v1/authorizations/push",
    "mtls_endpoint_aliases": {
      "token_endpoint": "https://mtls.api.local.dev/{tenant-id}/v1/tokens",
      "userinfo_endpoint": "https://mtls.api.local.dev/{tenant-id}/v1/userinfo",
      "pushed_authorization_request_endpoint": "https://mtls.api.local.dev/{tenant-id}/v1/authorizations/push",
      "introspection_endpoint": "https://mtls.api.local.dev/{tenant-id}/v1/tokens/introspection",
      "revocation_endpoint": "https://mtls.api.local.dev/{tenant-id}/v1/tokens/revocation"
    },
    "token_endpoint_auth_methods_supported": [
      "private_key_jwt",
      "tls_client_auth",
      "self_signed_tls_client_auth"
    ],
    "token_endpoint_auth_signing_alg_values_supported": ["ES256", "PS256"],
    "response_types_supported": ["code", "code id_token"],
    "response_modes_supported": ["query", "fragment", "jwt", "query.jwt", "fragment.jwt"],
    "id_token_signing_alg_values_supported": ["ES256", "PS256"],
    "request_object_signing_alg_values_supported": ["ES256", "PS256"],
    "authorization_signing_alg_values_supported": ["ES256", "PS256"],
    "code_challenge_methods_supported": ["S256"],
    "tls_client_certificate_bound_access_tokens": true,
    "require_signed_request_object": true,
    "authorization_response_iss_parameter_supported": true,
    "jwks": "{...サーバーの署名鍵（ES256 or PS256）...}",
    "extension": {
      "access_token_type": "JWT",
      "token_signed_key_id": "signing_key",
      "id_token_signed_key_id": "signing_key",
      "access_token_duration": 300,
      "id_token_duration": 300,
      "refresh_token_duration": 2592000,
      "id_token_strict_mode": true,
      "fapi_baseline_scopes": ["read", "account"],
      "fapi_advance_scopes": ["write", "transfers"],
      "pushed_authorization_request_expires_in": 60,
      "authorization_code_valid_duration": 60
    }
  }'
```

---

## Step 2: クライアント設定

FAPIクライアントに必要な設定パラメータを示します。

### 設定パラメータ一覧

| パラメータ | 説明 | FAPI Baseline | FAPI Advanced |
|:---------|:-----|:-------------|:-------------|
| `token_endpoint_auth_method` | クライアント認証方式 | mTLS / private_key_jwt / client_secret_jwt | mTLS / private_key_jwt のみ |
| `tls_client_certificate_bound_access_tokens` | mTLSバインドAT | 任意 | **必須 `true`** |
| `request_object_signing_alg` | Request Object署名アルゴリズム | 任意 | **ES256 or PS256** |
| `id_token_signed_response_alg` | ID Token署名アルゴリズム | 任意 | ES256 or PS256 推奨 |
| `authorization_signed_response_alg` | JARM署名アルゴリズム | 不要 | **ES256 or PS256**（JARMモード時） |
| `redirect_uris` | リダイレクトURI | **https必須** | **https必須** |
| `response_types` | レスポンスタイプ | `code` | `code` or `code id_token` |

### クライアント認証方式の選択

FAPI Advancedでは以下の3方式のみ許可されます:

| 認証方式 | 説明 | 追加設定 |
|:--------|:-----|:--------|
| `tls_client_auth` | CA発行クライアント証明書 | `tls_client_auth_subject_dn` |
| `self_signed_tls_client_auth` | 自己署名クライアント証明書 | `jwks`（公開鍵） |
| `private_key_jwt` | JWT Bearer認証 | `jwks`（公開鍵）, `token_endpoint_auth_signing_alg` |

```
禁止されるクライアント認証方式:
  ❌ client_secret_basic  → Baseline, Advanced 共に禁止
  ❌ client_secret_post   → Baseline, Advanced 共に禁止
  ❌ client_secret_jwt    → Advanced で追加禁止
  ❌ none (Public Client) → Advanced で禁止
```

### 設定例（tls_client_auth方式）

```bash
curl -X POST "https://api.local.dev/v1/management/tenants/{tenant-id}/clients" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "client_name": "FAPI Advanced Client",
    "token_endpoint_auth_method": "tls_client_auth",
    "tls_client_auth_subject_dn": "CN=client.example.com,O=Example,C=JP",
    "redirect_uris": ["https://client.example.com/callback"],
    "response_types": ["code"],
    "grant_types": ["authorization_code", "refresh_token"],
    "scope": "openid profile transfers",
    "tls_client_certificate_bound_access_tokens": true,
    "request_object_signing_alg": "ES256",
    "id_token_signed_response_alg": "ES256",
    "authorization_signed_response_alg": "ES256",
    "jwks": "{\"keys\":[{\"kty\":\"EC\",\"crv\":\"P-256\",\"kid\":\"client_key\",...}]}",
    "application_type": "web"
  }'
```

### 設定例（private_key_jwt方式）

```bash
curl -X POST "https://api.local.dev/v1/management/tenants/{tenant-id}/clients" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "client_name": "FAPI Advanced PKJ Client",
    "token_endpoint_auth_method": "private_key_jwt",
    "token_endpoint_auth_signing_alg": "ES256",
    "redirect_uris": ["https://client.example.com/callback"],
    "response_types": ["code"],
    "grant_types": ["authorization_code", "refresh_token"],
    "scope": "openid profile transfers",
    "tls_client_certificate_bound_access_tokens": true,
    "request_object_signing_alg": "ES256",
    "id_token_signed_response_alg": "ES256",
    "authorization_signed_response_alg": "ES256",
    "jwks": "{\"keys\":[{\"kty\":\"EC\",\"crv\":\"P-256\",\"kid\":\"client_key\",...}]}",
    "application_type": "web"
  }'
```

---

## Step 3: mTLSドメイン分離の設定

FAPI Advancedでは、mTLS（Mutual TLS）によるクライアント証明書検証が必要です。
通常のエンドポイントとmTLSエンドポイントを**異なるドメイン（サブドメイン）** で分離する構成を推奨します。

```
通常エンドポイント（認可リクエスト、Discovery等）:
  https://api.local.dev/{tenant-id}/v1/authorizations
  https://api.local.dev/{tenant-id}/.well-known/openid-configuration

mTLSエンドポイント（トークン発行、リソースアクセス等）:
  https://mtls.api.local.dev/{tenant-id}/v1/tokens
  https://mtls.api.local.dev/{tenant-id}/v1/authorizations/push
```

### なぜドメイン分離が必要か

```
リバースプロキシの構成:

api.local.dev (通常):
  → クライアント証明書を要求しない
  → ブラウザからのアクセスを許可

mtls.api.local.dev (mTLS):
  → クライアント証明書を必須で要求
  → 証明書を X-SSL-Client-Cert ヘッダーで idp-server に転送
```

認可エンドポイントはブラウザからアクセスされるため、クライアント証明書を要求できません。一方、トークンエンドポイントやPARエンドポイントはサーバー間通信であるため、mTLSを使用できます。

---

## Step 4: 署名鍵の設定

FAPI Advancedでは、以下のJWTに **PS256** または **ES256** アルゴリズムのみ許可されます:

| JWT | サーバー側設定 | クライアント側設定 |
|:----|:------------|:--------------|
| Request Object | `request_object_signing_alg_values_supported` | `request_object_signing_alg` |
| ID Token | `id_token_signing_alg_values_supported` | `id_token_signed_response_alg` |
| JARM応答 | `authorization_signing_alg_values_supported` | `authorization_signed_response_alg` |
| client_assertion | `token_endpoint_auth_signing_alg_values_supported` | `token_endpoint_auth_signing_alg` |

サーバーの `jwks` にES256またはPS256の署名鍵を含める必要があります:

```json
{
  "keys": [
    {
      "kty": "EC",
      "use": "sig",
      "crv": "P-256",
      "kid": "fapi_signing_key",
      "alg": "ES256",
      "x": "...",
      "y": "...",
      "d": "..."
    }
  ]
}
```

**RS256は禁止**: FAPI 1.0 Advanced Section 8.6 により、RSASSA-PKCS1-v1_5（RS256等）は使用不可です。

---

## 設定の依存関係と検証フロー

以下の図は、設定パラメータがどのタイミングで検証されるかを示します。

```
認可リクエスト受信
  │
  ├─ scope から プロファイル決定
  │    fapi_advance_scopes / fapi_baseline_scopes
  │
  ├─ プロファイルに応じた Verifier 選択
  │    FAPI_ADVANCE → FapiAdvanceVerifier
  │    FAPI_BASELINE → FapiBaselineVerifier
  │
  ├─ FapiAdvanceVerifier による検証:
  │    ├─ tls_client_certificate_bound_access_tokens = true ?
  │    │    サーバー: false → エラー (5.2.2-5/6)
  │    │    クライアント: false → エラー (5.2.2-5/6)
  │    │
  │    ├─ クライアント認証方式の制限
  │    │    client_secret_basic/post/jwt → エラー (5.2.2-14)
  │    │    none (public) → エラー (5.2.2-16)
  │    │
  │    ├─ Request Object 必須
  │    │    request パラメータなし → エラー (5.2.2-1)
  │    │    署名なし (alg:none) → エラー (5.2.2-1)
  │    │
  │    ├─ 署名アルゴリズム (PS256/ES256 のみ)
  │    │    RS256 等 → エラー (8.6)
  │    │
  │    ├─ Request Object クレーム検証
  │    │    nbf 必須、60分以内 → エラー (5.2.2-17)
  │    │    exp 必須、exp-nbf ≤ 60分 → エラー (5.2.2-13)
  │    │    aud = issuer identifier → エラー (5.2.2-15)
  │    │
  │    ├─ PKCE S256 (PAR経由時のみ)
  │    │    code_challenge 未指定 → エラー (5.2.2-18)
  │    │    S256以外 → エラー (5.2.2-18)
  │    │
  │    ├─ response_type 制限
  │    │    code id_token、または code + jwt → OK
  │    │    その他 → エラー (5.2.2-2)
  │    │
  │    ├─ redirect_uri https必須 → エラー (5.2.2-20)
  │    │
  │    ├─ nonce 必須 (openid scope時) → エラー (5.2.2.2)
  │    │
  │    └─ state 必須 (openid scope なし時) → エラー (5.2.2.3)
  │
  └─ Extension Verifiers:
       ├─ RequestObjectVerifier (Request Object 追加検証)
       │    require_signed_request_object = true
       │      → scope が RO内に必須
       │
       └─ JarmVerifier (JARM検証)
            authorization_signed_response_alg 設定確認
```

---

## 設定ミスによるエラーパターン

### パターン1: `tls_client_certificate_bound_access_tokens: false`

```
リクエスト: scope=openid transfers (FAPI Advanced)
結果: 400 Bad Request
エラー: "shall only issue sender-constrained access tokens,
        but server tls_client_certificate_bound_access_tokens is false"
```

**対処**: サーバーとクライアント両方の設定を `true` に変更。

### パターン2: `fapi_advance_scopes` 未設定

```
リクエスト: scope=openid transfers
結果: 通常の OIDC として処理される
問題: FAPI固有の検証が行われず、セキュリティ要件を満たさない
```

**対処**: `extension.fapi_advance_scopes` にFAPI対象のスコープを設定。

### パターン3: `pushed_authorization_request_endpoint` 未設定

```
リクエスト: PARエンドポイントへのリクエスト
結果: エンドポイントが存在しないため 404
```

**対処**: PARエンドポイントURLを設定。

### パターン4: `mtls_endpoint_aliases` 未設定

```
リクエスト: client_assertion の aud に mTLS エンドポイントURL
結果: 401 Unauthorized
エラー: "aud claim must contain valid endpoint URL"
```

**対処**: mTLSエンドポイントのエイリアスを設定。

### パターン5: クライアントの `authorization_signed_response_alg` 未設定（JARMモード時）

```
リクエスト: response_mode=jwt (JARM)
結果: 400 Bad Request
エラー: "client config must have authorization_signed_response_alg"
```

**対処**: クライアント設定に `authorization_signed_response_alg: "ES256"` を追加。

---

## OIDF認定テスト用の完全設定チェックリスト

OpenID Foundation の FAPI 1.0 Advanced Final 適合性テストに合格するために必要な設定の完全チェックリストです。

### 認可サーバー設定

- [ ] `issuer` が https URL で設定済み
- [ ] `pushed_authorization_request_endpoint` が設定済み
- [ ] `mtls_endpoint_aliases` に全エンドポイントのmTLSエイリアスを設定済み
- [ ] `tls_client_certificate_bound_access_tokens` = `true`
- [ ] `require_signed_request_object` = `true`
- [ ] `authorization_response_iss_parameter_supported` = `true`
- [ ] `token_endpoint_auth_methods_supported` に `private_key_jwt` / `tls_client_auth` を含む
- [ ] `token_endpoint_auth_signing_alg_values_supported` に `ES256` / `PS256` を含む
- [ ] `response_types_supported` に `code` と `code id_token` を含む
- [ ] `response_modes_supported` に `jwt` / `query.jwt` / `fragment.jwt` を含む
- [ ] `id_token_signing_alg_values_supported` に `ES256` / `PS256` を含む
- [ ] `request_object_signing_alg_values_supported` に `ES256` / `PS256` を含む
- [ ] `authorization_signing_alg_values_supported` に `ES256` / `PS256` を含む
- [ ] `code_challenge_methods_supported` に `S256` を含む
- [ ] `jwks` にES256またはPS256の署名鍵を含む
- [ ] `extension.fapi_advance_scopes` にFAPI対象スコープを設定済み
- [ ] `extension.access_token_type` = `JWT`
- [ ] `extension.id_token_strict_mode` = `true`

### クライアント設定（2クライアント必要）

OIDFテストでは `client` と `client2` の2つのクライアントが必要です。証明書バインドアクセストークンの交差検証（別クライアントの証明書で使用不可）をテストするためです。

**各クライアント共通:**
- [ ] `token_endpoint_auth_method` が `tls_client_auth` / `private_key_jwt` のいずれか
- [ ] `tls_client_certificate_bound_access_tokens` = `true`
- [ ] `request_object_signing_alg` = `ES256` or `PS256`
- [ ] `id_token_signed_response_alg` = `ES256` or `PS256`
- [ ] `authorization_signed_response_alg` = `ES256` or `PS256`
- [ ] `redirect_uris` が https スキーム
- [ ] `redirect_uris` にクエリパラメータ付きURLも登録（2nd client テスト用）
- [ ] `response_types` に `code` を含む
- [ ] `scope` にFAPI Advancedスコープを含む
- [ ] `jwks` にクライアントの公開鍵を含む

---

## 次のステップ

FAPI設定が完了したら:

- [OIDF適合性テストの実行と結果分析](../../content_06_developer-guide/04-implementation-guides/oauth-oidc/fapi.md)
- [トークン有効期限パターンの設定](./02-token-strategy.md)

---

## 関連ドキュメント

- [FAPIコンセプト](../../content_03_concepts/03-authentication-authorization/concept-06-fapi.md)
- [FAPI実装ガイド](../../content_06_developer-guide/04-implementation-guides/oauth-oidc/fapi.md)
- [FAPI 1.0 Gap分析](../../requirements/fapi-1.0-gap-analysis.yaml)
- [FAPI 1.0 Baseline 仕様](https://openid.net/specs/openid-financial-api-part-1-1_0.html)
- [FAPI 1.0 Advanced 仕様](https://openid.net/specs/openid-financial-api-part-2-1_0.html)
- [RFC 9126 - Pushed Authorization Requests](https://www.rfc-editor.org/rfc/rfc9126)
- [RFC 8705 - Mutual TLS](https://www.rfc-editor.org/rfc/rfc8705)
