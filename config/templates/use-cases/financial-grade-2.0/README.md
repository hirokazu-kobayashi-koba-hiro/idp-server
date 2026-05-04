# Financial-Grade API 2.0 (FAPI 2.0 SP Final) Use Case Template

FAPI 2.0 Security Profile Final 準拠の金融グレード認可サーバーを構築するテンプレートセット。
mTLS / DPoP のいずれかで sender-constrained access token を発行する構成。

> **テンプレートの位置づけ**: 既存の `financial-grade/` (FAPI 1.0 Advanced + CIBA) との違いは、FAPI 2.0
> SP の厳格化に対応した設定値。共通設定（認証ポリシー、ユーザー登録スキーマ、CIBA、eKYC 等）は
> `financial-grade/` のファイルを再利用する想定。本ディレクトリは FAPI 2.0 固有の差分のみを保持。

## FAPI 1.0 Advanced との差分

| 項目 | FAPI 1.0 Advanced | FAPI 2.0 SP |
|------|------------------|-------------|
| `response_types_supported` | `code`, `code id_token` | `code` のみ（Hybrid Flow 禁止）|
| `response_modes_supported` | `query`, `fragment`, `jwt`, `query.jwt`, `fragment.jwt` | `query`, `jwt`, `query.jwt`（fragment 系除外）|
| PAR | 任意 | **必須** (`require_pushed_authorization_requests: true`) |
| `require_signed_request_object` | `true` | `false`（PAR で代替）|
| Sender Constrained | mTLS のみ | mTLS or DPoP |
| `dpop_signing_alg_values_supported` | なし | `ES256`, `PS256`, `EdDSA` |
| `cors_config.allow_headers` | - | `DPoP` ヘッダ追加 |
| Authorization Code Binding | なし | `dpop_jkt` 必須（DPoP 使用時、RFC 9449 §10）|
| `iss` パラメータ | 任意 | 必須（`authorization_response_iss_parameter_supported: true`）|
| クライアント認証 | `tls_client_auth`, `private_key_jwt` | 同左（変更なし）|

## ファイル構成

| ファイル | 用途 | 備考 |
|---------|------|------|
| `fapi2-tenant-template.json` | FAPI 2.0 テナント設定 | `fapi20_scopes` 設定済み |
| `dpop-client-template.json` | DPoP-bound クライアント | `private_key_jwt` + DPoP |
| `mtls-client-template.json` | mTLS-bound クライアント | `tls_client_auth` |
| `README.md` | 本ファイル | - |

## 設定の要点

### `fapi20_scopes`

このスコープが Authorization Request に含まれていると `AuthorizationProfile.FAPI_2_0`
が発動し、`FapiSecurity20Verifier` / `AuthorizationCodeGrantFapi20Verifier` が動作する。

```json
"extension": {
  "fapi_2_0_scopes": [
    "write",
    "transfers"
  ]
}
```

### `require_pushed_authorization_requests`

Discovery メタデータに含めることで、クライアントに PAR 経由のリクエストを要求していることを通知。
サーバ側は `FapiSecurity20Verifier.throwExceptionIfNotPushedRequest` で実際に PAR 経由でないリクエストを
拒否する。

### DPoP 利用クライアントの注意

- `tls_client_certificate_bound_access_tokens: false` を設定（mTLS バインドを無効化）
- クライアントは Token Request 時に `DPoP` ヘッダで proof JWT を提示
- `dpop_jkt` パラメータを Authorization Request または PAR で送信することで認可コードを DPoP 鍵にバインド可能

## セットアップ

`financial-grade/` の `setup.sh` を流用しつつ、以下のテンプレートに置換することで FAPI 2.0
テナントが構築できる。

| 元 (financial-grade/) | 置換 (financial-grade-2.0/) |
|---------------------|---------------------------|
| `financial-tenant-template.json` | `fapi2-tenant-template.json` |
| `private-key-jwt-client-template.json` | `dpop-client-template.json` |
| `tls-client-auth-client-template.json` | `mtls-client-template.json` |

その他のファイル（`onboarding-template.json`、`authentication-config-*.json`、
`identity-verification-config.json`、`authentication-policy-*.json`、`financial-user-template.json`、
`jwks.json`）は `financial-grade/` のものをそのまま利用可能。

## 関連

- 仕様: <https://openid.net/specs/fapi-security-profile-2_0.html>
- ギャップ分析: [`fapi-2.0-gap-analysis.md`](../../../../documentation/requirements/fapi-2.0-gap-analysis.md)
- DPoP 実装ノート: [`oauth2-dpop-gap-analysis.md`](../../../../documentation/requirements/oauth2-dpop-gap-analysis.md)
- 既存テンプレート: [`financial-grade/`](../financial-grade/)
