---
name: use-case-financial-grade
description: 金融グレード（FAPI Advanced + CIBA）ユースケースの設定ガイド。mTLS、署名リクエスト、PAR、JARM、CIBA、多段認証ポリシーのヒアリングと設定JSONを提供。
---

# 金融グレード（FAPI Advanced + CIBA）

## ヒアリング項目

| # | 決めること | 選択肢 | 影響する設定 |
|---|-----------|--------|-------------|
| 1 | クライアント認証方式 | `tls_client_auth`（CA証明書）/ `self_signed_tls_client_auth`（自己署名）/ `private_key_jwt` | クライアント `token_endpoint_auth_method` |
| 2 | FAPI Scopes | baseline: read,account / advance: write,transfers | 認可サーバー拡張 `fapi_baseline_scopes`, `fapi_advance_scopes` |
| 3 | eKYC 要否 | `true` / `false`（デフォルト） | `required_identity_verification_scopes` |
| 4 | CIBA Delivery Mode | `poll`（推奨）/ `ping` | 認可サーバー `backchannel_token_delivery_modes_supported` |
| 5 | 署名アルゴリズム | `ES256`（推奨）/ `PS256` | 各 `*_signing_alg` フィールド |
| 6 | 証明書 Subject DN | 組織情報 | `tls_client_auth_subject_dn` |
| 7 | mTLS エンドポイント URL | mTLS用ドメイン | `mtls_endpoint_aliases` |

---

## 環境変数マッピング

### FAPI 固有設定

| ヒアリング項目 | 環境変数 | デフォルト値 |
|--------------|---------|-------------|
| 署名アルゴリズム | `SIGNING_ALGORITHM` | `ES256` |
| PAR 有効期限 | `PAR_EXPIRES_IN` | `60` |
| mTLS ベースURL | `MTLS_BASE_URL` | `https://mtls.api.local.test` |
| AT 有効期限 | `ACCESS_TOKEN_DURATION` | `300` |
| IDT 有効期限 | `ID_TOKEN_DURATION` | `300` |
| RT 有効期限 | `REFRESH_TOKEN_DURATION` | `2592000` |

### 証明書設定

| ヒアリング項目 | 環境変数 | デフォルト値 |
|--------------|---------|-------------|
| Subject DN | `CERT_SUBJECT_DN` | `CN=financial-app,O=Financial Institution,C=JP` |
| SAN DNS | `CERT_SAN_DNS` | `financial-app.example.com` |

### CIBA 設定

| ヒアリング項目 | 環境変数 | デフォルト値 |
|--------------|---------|-------------|
| Delivery Mode | `CIBA_DELIVERY_MODE` | `poll` |
| リクエスト有効期限 | `CIBA_REQUEST_EXPIRES_IN` | `120` |
| Polling Interval | `CIBA_POLLING_INTERVAL` | `5` |
| User Code 要否 | `CIBA_USER_CODE_REQUIRED` | `false` |

### eKYC 設定（オプション）

| ヒアリング項目 | 環境変数 | デフォルト値 |
|--------------|---------|-------------|
| eKYC 有効化 | `ENABLE_EKYC` | `false` |
| 必須スコープ | `REQUIRED_IV_SCOPES` | `transfers` |

### クライアント設定

| 項目 | 環境変数 | デフォルト値 |
|------|---------|-------------|
| TLS Client ID | `TLS_CLIENT_ID` | `(uuid)` |
| TLS Client エイリアス | `TLS_CLIENT_ALIAS` | `financial-tls-client` |
| TLS Client 名 | `TLS_CLIENT_NAME` | `Financial TLS Client` |
| PKJ Client ID | `PKJ_CLIENT_ID` | `(uuid)` |
| PKJ Client エイリアス | `PKJ_CLIENT_ALIAS` | `financial-pkj-client` |
| PKJ Client 名 | `PKJ_CLIENT_NAME` | `Financial Private Key JWT Client` |
| リダイレクトURI | `REDIRECT_URI` | `https://localhost:8443/callback` |

---

## 設定対象と手順

### 1. 証明書生成

`generate-certs.sh` で自己署名クライアント証明書を生成:

```bash
./generate-certs.sh
```

- EC P-256 鍵ペア生成
- 自己署名証明書（365日）
- DER 変換（JWKS 登録用）
- SHA-256 フィンガープリント表示

### 2. テナント作成（FAPI 設定付き）

認可サーバーに以下の FAPI 設定が含まれること:

```json
{
  "tls_client_certificate_bound_access_tokens": true,
  "require_signed_request_object": true,
  "authorization_response_iss_parameter_supported": true,
  "code_challenge_methods_supported": ["S256"],
  "mtls_endpoint_aliases": {
    "token_endpoint": "${MTLS_BASE_URL}/${PUBLIC_TENANT_ID}/v1/tokens",
    "pushed_authorization_request_endpoint": "..."
  },
  "extension": {
    "fapi_baseline_scopes": ["read", "account"],
    "fapi_advance_scopes": ["write", "transfers"],
    "pushed_authorization_request_expires_in": 60,
    "id_token_strict_mode": true
  }
}
```

### 3. クライアント作成（2種類）

#### tls_client_auth クライアント
- mTLS による証明書ベース認証
- `tls_client_auth_subject_dn` で証明書 DN を指定
- `tls_client_certificate_bound_access_tokens: true`

#### private_key_jwt クライアント
- JWT 署名によるクライアント認証
- JWKS にクライアント公開鍵を登録

### 4. 認証ポリシー（多段ポリシー）

#### OAuth フロー（3段階ポリシー）
| Priority | 条件 | 認証方式 | 用途 |
|----------|------|---------|------|
| 1 | transfers, write | FIDO2, FIDO-UAF, Email | 高額送金 |
| 2 | openid, read, account | FIDO2, FIDO-UAF, SMS, Email | 参照系 |
| 10 | ACR Gold | FIDO2, FIDO-UAF | 最高レベル認証 |

#### CIBA フロー
- パスワード認証のみ
- デバイス側で実行

### 5. テストユーザー

`authentication_devices` 付きユーザーを作成。CIBA と FIDO2 の両方をテスト可能。

---

## 証明書生成ガイド

### 自己署名証明書（self_signed_tls_client_auth）

```bash
# EC P-256 鍵生成
openssl ecparam -genkey -name prime256v1 -noout -out client-key.pem

# 自己署名証明書
openssl req -new -x509 -days 365 \
  -key client-key.pem \
  -out client-cert.pem \
  -subj "/CN=financial-app,O=Financial Institution,C=JP" \
  -addext "subjectAltName=DNS:financial-app.example.com"

# DER 変換（JWKS 登録用）
openssl x509 -in client-cert.pem -outform DER -out client-cert.der

# フィンガープリント確認
openssl x509 -in client-cert.pem -fingerprint -sha256 -noout
```

### CA 署名証明書（tls_client_auth）

1. CA 作成（自己署名認証局）
2. クライアント CSR 作成
3. CA で署名
4. 信頼チェーン検証

詳細: `config/examples/financial-grade/certs/README.md`

---

## Sender-Constrained Token

FAPI では mTLS 証明書バインディングにより、トークン盗用を防止:

```json
{
  "cnf": {
    "x5t#S256": "<certificate-fingerprint>"
  }
}
```

リソースサーバーは、提示された証明書のフィンガープリントとトークン内の `cnf` クレームを照合する。

---

## 設定確認チェックリスト

| # | 確認観点 | 設定箇所 | よくあるミス |
|---|---------|---------|------------|
| 1 | `claims_supported` が設定済み | 認可サーバー | 未設定で UserInfo/ID Token が `sub` のみ |
| 2 | `tls_client_certificate_bound_access_tokens` = `true` | 認可サーバー | 未設定で mTLS バインディング無効 |
| 3 | `require_signed_request_object` = `true` | 認可サーバー | 未設定で JAR 必須化されない |
| 4 | `mtls_endpoint_aliases` が全エンドポイント設定済み | 認可サーバー | token_endpoint だけで PAR, revocation 等が抜ける |
| 5 | `pushed_authorization_request_endpoint` が設定済み | 認可サーバー | PAR エンドポイント未設定 |
| 6 | `fapi_baseline_scopes` / `fapi_advance_scopes` が設定済み | 認可サーバー拡張 | 未設定だと FAPI スコープ分類が無効 |
| 7 | クライアントの `tls_client_auth_subject_dn` が証明書 DN と一致 | クライアント | DN 不一致で `invalid_client` エラー |
| 8 | `backchannel_authentication_endpoint` が設定済み | 認可サーバー | CIBA エンドポイント未設定 |
| 9 | CIBA 認証ポリシー（`flow: "ciba"`）が作成済み | 認証ポリシー | OAuth ポリシーのみで CIBA ポリシー未作成 |
| 10 | テストユーザーに `authentication_devices` が設定済み | ユーザー | デバイス未登録だと CIBA 通知不可 |
| 11 | `authorization_response_iss_parameter_supported` = `true` | 認可サーバー | FAPI 2.0 iss パラメータ未対応 |
| 12 | `code_challenge_methods_supported` に `S256` が含まれる | 認可サーバー | PKCE S256 未対応 |

---

## テンプレートファイル参照

- テンプレート: `config/templates/use-cases/financial-grade/`
- 動作する Example: `config/examples/financial-grade/`
- 証明書 README: `config/examples/financial-grade/certs/README.md`

## 関連ドキュメント

- `content_06_developer-guide/03-application-plane/06-ciba-flow.md`
- `config/examples/financial-grade/README.md`

$ARGUMENTS
