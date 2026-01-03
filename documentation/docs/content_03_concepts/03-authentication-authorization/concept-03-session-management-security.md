# セッション管理セキュリティ分析

このドキュメントでは、idp-serverのセッション管理におけるセキュリティ対策の現状を記載します。

## セッションライフサイクル概要

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          セッションライフサイクル                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  【フェーズ1: 認可リクエスト開始】                                          │
│  ┌──────────────────┐                                                      │
│  │ GET /authorizations │                                                    │
│  └──────────────────┘                                                      │
│         │                                                                   │
│         ▼                                                                   │
│  ┌──────────────────┐     ┌──────────────────┐                             │
│  │ AuthSession       │     │ Authentication    │                             │
│  │ Cookie生成        │     │ Transaction作成   │                             │
│  │ (CSRF対策)        │     │ (認可リクエスト   │                             │
│  └──────────────────┘     │  に紐付く)        │                             │
│                            └──────────────────┘                             │
│                                                                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  【フェーズ2: 認証完了】                                                    │
│  ┌──────────────────────────────────────────────────────────────┐          │
│  │ POST /password-authentication (+ TOTP等)                     │          │
│  └──────────────────────────────────────────────────────────────┘          │
│         │                                                                   │
│         ▼                                                                   │
│  ┌──────────────────┐                                                      │
│  │ validateAuthSession() │  ← AUTH_SESSION Cookie検証                       │
│  └──────────────────┘                                                      │
│         │                                                                   │
│         ▼                                                                   │
│  ┌──────────────────┐                                                      │
│  │ Authentication    │  認証情報を更新                                      │
│  │ Transaction更新   │  - user, acr, amr, authTime                         │
│  └──────────────────┘                                                      │
│                                                                             │
│  ※ この時点ではOPSessionは作成されない                                      │
│  ※ MFA途中離脱の場合、ここで止まる                                          │
│                                                                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  【フェーズ3: 認可完了】                                                    │
│  ┌──────────────────┐                                                      │
│  │ POST /authorize   │                                                      │
│  └──────────────────┘                                                      │
│         │                                                                   │
│         ▼                                                                   │
│  ┌──────────────────┐                                                      │
│  │ validateAuthSession() │  ← AUTH_SESSION Cookie検証                       │
│  └──────────────────┘                                                      │
│         │                                                                   │
│         ▼                                                                   │
│  ┌──────────────────┐     ┌──────────────────┐                             │
│  │ OPSession作成     │     │ ClientSession作成 │                             │
│  │ (SSO用)          │     │ (per-client)     │                             │
│  │ acr, amr, authTime│     │ sid for ID Token │                             │
│  └──────────────────┘     └──────────────────┘                             │
│         │                                                                   │
│         ▼                                                                   │
│  ┌──────────────────┐                                                      │
│  │ IDP_IDENTITY      │  OPSession ID (HttpOnly)                            │
│  │ IDP_SESSION       │  SHA256(OPSession ID) (for iframe)                  │
│  │ Cookie設定        │                                                      │
│  └──────────────────┘                                                      │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                          SSO時のライフサイクル                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  【前提: OPSession が既に存在する】                                          │
│  ┌──────────────────┐                                                      │
│  │ IDP_IDENTITY      │  既存のOPSession ID                                  │
│  │ Cookie あり       │                                                      │
│  └──────────────────┘                                                      │
│                                                                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  【SSO方式1: prompt=none】                                                  │
│  ┌──────────────────┐                                                      │
│  │ GET /authorizations │  prompt=none                                       │
│  │                    │  + IDP_IDENTITY Cookie                             │
│  └──────────────────┘                                                      │
│         │                                                                   │
│         ▼                                                                   │
│  ┌──────────────────┐                                                      │
│  │ OPSession検証     │  - 有効期限チェック                                  │
│  │                   │  - max_ageチェック                                  │
│  │                   │  - AuthorizationGrantedチェック                     │
│  └──────────────────┘                                                      │
│         │                                                                   │
│         ▼                                                                   │
│  ┌──────────────────┐                                                      │
│  │ 302 Redirect      │  認可コード直接発行                                  │
│  │ + 認可コード      │  （認証・認可画面スキップ）                          │
│  └──────────────────┘                                                      │
│                                                                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  【SSO方式2: authorize-with-session】                                       │
│  ┌──────────────────┐                                                      │
│  │ GET /authorizations │  prompt指定なし                                    │
│  └──────────────────┘                                                      │
│         │                                                                   │
│         ▼                                                                   │
│  ┌──────────────────┐     ┌──────────────────┐                             │
│  │ AuthSession       │     │ Authentication    │                             │
│  │ Cookie生成        │     │ Transaction作成   │                             │
│  └──────────────────┘     └──────────────────┘                             │
│         │                                                                   │
│         ▼                                                                   │
│  ┌──────────────────┐                                                      │
│  │ GET /view-data    │  session_enabled: true を返す                        │
│  └──────────────────┘                                                      │
│         │                                                                   │
│         ▼                                                                   │
│  ┌─────────────────────────────────────┐                                   │
│  │ POST /authorize-with-session         │                                   │
│  │ + AUTH_SESSION Cookie               │                                   │
│  │ + IDP_IDENTITY Cookie               │                                   │
│  └─────────────────────────────────────┘                                   │
│         │                                                                   │
│         ▼                                                                   │
│  ┌──────────────────┐     ┌──────────────────┐                             │
│  │ validateAuthSession() │  │ OPSession検証     │                             │
│  │ AUTH_SESSION検証  │     │ IDP_IDENTITY検証  │                             │
│  └──────────────────┘     └──────────────────┘                             │
│         │                                                                   │
│         ▼                                                                   │
│  ┌──────────────────┐                                                      │
│  │ OPSessionの認証   │  既存OPSessionのacr/amr/authTimeで認可               │
│  │ 情報で認可        │                                                      │
│  └──────────────────┘                                                      │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

## セッションライフサイクル詳細

```mermaid
sequenceDiagram
    participant Browser
    participant IdP
    participant Session Store

    Note over Browser,Session Store: 認可リクエスト開始
    Browser->>IdP: GET /authorizations
    IdP->>Session Store: AuthenticationTransaction作成
    IdP->>Browser: 302 Redirect + AUTH_SESSION Cookie

    Note over Browser,Session Store: 認証フロー
    Browser->>IdP: POST /password-authentication
    IdP->>IdP: validateAuthSession()
    IdP->>Session Store: AuthenticationTransaction更新

    Note over Browser,Session Store: 認可完了
    Browser->>IdP: POST /authorize
    IdP->>IdP: validateAuthSession()
    IdP->>Session Store: OPSession作成
    IdP->>Session Store: ClientSession作成
    IdP->>Browser: 認可コード + IDP_IDENTITY Cookie
```

## Cookie一覧

| Cookie名 | 用途 | HttpOnly | Secure | SameSite |
|---------|------|----------|--------|----------|
| AUTH_SESSION | 認可フロー固定攻撃対策 | Yes | Yes | Lax |
| IDP_IDENTITY | OPSession識別（SSO用） | Yes | Yes | Lax |
| IDP_SESSION | OIDC Session Management iframe用 | No | Yes | Lax |

## 攻撃と対策マトリクス

| 攻撃 | 対策 | 備考 |
|------|------|------|
| セッション固定攻撃 (Session Fixation) | AUTH_SESSION Cookie検証 | `validateAuthSession()` |
| 認可フローハイジャック (authorize-with-session経由) | AUTH_SESSION Cookie検証 | `authorizeWithSession()`に追加 |
| CSRF攻撃 | SameSite=Lax + POST only | Cookie設定 |
| XSS経由のセッション窃取 | HttpOnly | IDP_IDENTITY Cookie |

## 対策：認可フローハイジャック

### 攻撃シナリオ

```
1. 攻撃者が認可リクエストを開始 → id=abc123, AUTH_SESSION=xyz789 を取得
2. 攻撃者がURL (id=abc123) を被害者に送信
3. 被害者が認証を完了
4. 攻撃者が authorize-with-session を呼ぶ
```

### 対策

`authorizeWithSession()` で `validateAuthSession()` を実行し、AUTH_SESSION Cookieの一致を検証。

```java
public OAuthAuthorizeResponse authorizeWithSession(...) {
    // Validate AUTH_SESSION cookie to prevent authorization flow hijacking attacks
    AuthenticationTransaction authenticationTransaction =
        authenticationTransactionQueryRepository.get(
            tenant, authorizationRequestIdentifier.toAuthorizationIdentifier());
    validateAuthSession(authenticationTransaction);
    // ...
}
```

### 結果

- AUTH_SESSION Cookie不一致 → 401 `auth_session_mismatch`
- AUTH_SESSION Cookie欠落 → 401 `auth_session_mismatch`

## E2Eテスト

| テスト | ファイル |
|-------|---------|
| AUTH_SESSION Cookie missing | `scenario-13-sso-session-management.test.js` |
| AUTH_SESSION Cookie mismatch | `scenario-13-sso-session-management.test.js` |

## 参考

- [OIDC Session Management](https://openid.net/specs/openid-connect-session-1_0.html)
- [Keycloak Session Management](https://www.keycloak.org/docs/latest/server_admin/#_user-session-management)
