# OIDCC Form Post Basic Certification Test Configuration

OpenID Connect Core Form Post Basic 認定テスト用の設定ファイルです。

## ファイル構成

```
oidcc-formpost-basic/
├── README.md                 # このファイル
├── onboarding-request.json   # Onboarding API 用設定（組織・テナント・ユーザー・クライアント1）
├── client-post.json          # 2つ目のクライアント（client_secret_post）
├── client-second.json        # 3つ目のクライアント（second client）
├── setup.sh                  # セットアップスクリプト
└── delete.sh                 # 削除スクリプト
```

## テストプラン

| 項目 | 値 |
|------|-----|
| テストプラン名 | `oidcc-formpost-basic-certification-test-plan` |
| 認定プロファイル | Form Post OP |
| Response Type | `code` |
| Response Mode | `form_post` |
| Client Auth | `client_secret_basic`, `client_secret_post` |

## セットアップ

### 1. 前提条件

- idp-server が起動していること
- `.env` ファイルに管理者認証情報が設定されていること

### 2. セットアップ実行

```bash
cd config/examples/oidcc-formpost-basic
chmod +x setup.sh
./setup.sh
```

### 3. セットアップ結果

| リソース | 値 |
|---------|-----|
| Tenant ID | `d2e3f4a5-b6c7-8901-def0-234567890123` |
| Issuer | `https://api.local.dev/d2e3f4a5-b6c7-8901-def0-234567890123` |

## クライアント設定

### Client 1: client_secret_basic

| 項目 | 値 |
|------|-----|
| Client ID | `f4a5b6c7-d8e9-0123-abcd-456789012345` |
| Client ID Alias | `oidcc-basic-client` |
| Client Secret | `oidcc-basic-secret-32characters!` |
| Auth Method | `client_secret_basic` |
| Redirect URI | `https://localhost.emobix.co.uk:8443/test/a/oidc-core-basic/callback` |

### Client 2: client_secret_post

| 項目 | 値 |
|------|-----|
| Client ID | `a5b6c7d8-e9f0-1234-bcde-567890123456` |
| Client ID Alias | `oidcc-post-client` |
| Client Secret | `oidcc-post-secret-32characters!!` |
| Auth Method | `client_secret_post` |
| Redirect URI | `https://localhost.emobix.co.uk:8443/test/a/oidc-core-basic/callback` |

### Client 3: Second Client

| 項目 | 値 |
|------|-----|
| Client ID | `b6c7d8e9-f0a1-2345-cdef-678901234567` |
| Client ID Alias | `oidcc-second-client` |
| Client Secret | `oidcc-second-secret-32characters!` |
| Auth Method | `client_secret_basic` |
| Redirect URI | `https://localhost.emobix.co.uk:8443/test/a/oidc-core-basic/callback` |

## テストユーザー

| 項目 | 値 |
|------|-----|
| Email | `oidcc-test@example.com` |
| Password | `OidccTestPassword123!` |
| Name | `OIDCC Test User` |
| Phone | `+81-90-1234-5678` |
| Address | `123 Test Street, Tokyo, Japan 100-0001` |

## Conformance Suite 設定

OpenID Conformance Suite で以下の設定を使用:

```
Server:
  Issuer: https://api.local.dev/d2e3f4a5-b6c7-8901-def0-234567890123

Client (client_secret_basic):
  Client ID: f4a5b6c7-d8e9-0123-abcd-456789012345
  Client Secret: oidcc-basic-secret-32characters!

Client (client_secret_post):
  Client ID: a5b6c7d8-e9f0-1234-bcde-567890123456
  Client Secret: oidcc-post-secret-32characters!!

Second Client:
  Client ID: b6c7d8e9-f0a1-2345-cdef-678901234567
  Client Secret: oidcc-second-secret-32characters!

User:
  Username: oidcc-test@example.com
  Password: OidccTestPassword123!
```

## サポートされる機能

| 機能 | サポート |
|------|---------|
| response_mode=form_post | Yes |
| response_type=code | Yes |
| prompt=none | Yes |
| prompt=login | Yes |
| max_age | Yes |
| acr_values | Yes |
| login_hint | Yes |
| id_token_hint | Yes |
| claims parameter | Yes |
| request parameter | Yes |
| request_uri parameter | Yes |
| PKCE | Yes |
| Refresh Token | Yes |

## テストモジュール (38個)

1. oidcc-server - 基本フロー
2. oidcc-response-type-missing - response_type省略エラー
3. oidcc-idtoken-signature - ID Token署名検証
4. oidcc-userinfo-get - UserInfo GET
5. oidcc-userinfo-post-header - UserInfo POST (header)
6. oidcc-userinfo-post-body - UserInfo POST (body)
7. oidcc-scope-profile/email/address/phone/all - スコープテスト
8. oidcc-prompt-login/none - prompt パラメータ
9. oidcc-max-age-1/10000 - max_age パラメータ
10. oidcc-id-token-hint - id_token_hint
11. oidcc-login-hint - login_hint
12. oidcc-codereuse - 認可コード再利用防止
13. oidcc-refresh-token - リフレッシュトークン
14. oidcc-ensure-request-with-valid-pkce-succeeds - PKCE
... など

詳細は OpenID Conformance Suite のドキュメントを参照。
