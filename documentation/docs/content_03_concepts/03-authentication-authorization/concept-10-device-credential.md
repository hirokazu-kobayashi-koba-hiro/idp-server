# デバイスクレデンシャル管理

## 概要

**デバイスクレデンシャル**は、モバイルアプリがIdPサーバーと安全に通信するための認証情報です。

### 利用パターン

デバイスシークレットには2つの利用パターンがあります：

| パターン | 説明 | ユーザー操作 |
|---------|------|-------------|
| **CIBAフロー** | デバイスエンドポイント認証 + FIDO-UAF本人確認 | 必要（生体認証） |
| **JWT Bearer Grant** | アクセストークンを直接取得（RFC 7523） | 不要 |

### CIBAフロー（推奨）

Webアプリからの操作承認リクエストをモバイルアプリで受信・認証する際、以下の2段階認証を実現します：

1. **デバイス認証**: デバイスシークレット（JWT）で「正規のデバイス」であることを証明
2. **本人認証**: FIDO-UAF（生体認証）で「正規のユーザー」であることを証明

```
┌─────────────────────────────────────────────────────────────────────┐
│ モバイルアプリの初回セットアップ                                      │
│                                                                     │
│   FIDO-UAF登録 → デバイスシークレット自動発行                        │
│                  (issue_device_secret: true)                       │
└─────────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────────┐
│ CIBAフロー（Webからの操作承認）                                       │
│                                                                     │
│   1. Webアプリ → IdP: 承認リクエスト                                 │
│   2. IdP → モバイルアプリ: Push通知                                  │
│   3. モバイルアプリ → IdP: デバイスシークレットJWTで認証              │
│   4. モバイルアプリ → IdP: FIDO-UAF（生体認証）で本人確認            │
│   5. Webアプリ: トークン取得                                         │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Step 1: デバイス登録（FIDO-UAF + シークレット自動発行）

モバイルアプリの初回セットアップで、FIDO-UAF登録と同時にデバイスシークレットを発行します。

### テナントポリシー設定

`identity_policy_config.authentication_device_rule` で設定します。

```json
{
  "identity_policy_config": {
    "authentication_device_rule": {
      "max_devices": 5,
      "required_identity_verification": false,
      "authentication_type": "device_secret_jwt",
      "issue_device_secret": true,
      "device_secret_algorithm": "HS256",
      "device_secret_expires_in_seconds": 31536000
    }
  }
}
```

| パラメータ | 説明 | デフォルト |
|-----------|------|-----------|
| `authentication_type` | `device_secret_jwt`: デバイスエンドポイントアクセスにJWT認証を要求<br/>`none`: 認証不要 | `none` |
| `issue_device_secret` | FIDO-UAF登録時にシークレットを自動発行 | `false` |
| `device_secret_algorithm` | 署名アルゴリズム（HS256/HS384/HS512） | `HS256` |
| `device_secret_expires_in_seconds` | 有効期限（秒）、null=無期限 | `null` |

### 登録シーケンス

```mermaid
sequenceDiagram
    participant User as ユーザー
    participant App as モバイルアプリ
    participant IdP as IdP Server
    participant FIDO as FIDOサーバー

    User->>App: 1. 初回セットアップ開始
    App->>IdP: 2. Authorization Request
    IdP->>User: 3. 認証画面
    User->>IdP: 4. 認証（パスワード等）
    IdP->>App: 5. Authorization Code

    Note over App, IdP: FIDO-UAF登録 + デバイスシークレット自動発行
    App->>IdP: 6. POST /authorizations/{id}/fido-uaf-registration-challenge
    IdP->>FIDO: 7. Challenge生成
    FIDO->>IdP: 8. Challenge
    IdP->>App: 9. Challenge
    App->>User: 10. 生体認証要求
    User->>App: 11. 生体認証
    App->>IdP: 12. POST /authorizations/{id}/fido-uaf-registration
    IdP->>FIDO: 13. 署名検証
    FIDO->>IdP: 14. 登録完了
    IdP->>IdP: 15. デバイスシークレット生成<br/>(issue_device_secret: true)
    IdP->>App: 16. 登録完了 + device_secret

    App->>App: 17. device_secretをSecure Storageに保存
```

### 登録レスポンス

```json
{
  "status": "success",
  "device_id": "device_abc123",
  "device_secret": "base64url-encoded-random-secret",
  "device_secret_algorithm": "HS256",
  "device_secret_jwt_issuer": "device:device_abc123"
}
```

| フィールド | 説明 |
|-----------|------|
| `device_id` | デバイス識別子 |
| `device_secret` | 署名用シークレット（Secure Storageに保存） |
| `device_secret_algorithm` | JWT署名アルゴリズム |
| `device_secret_jwt_issuer` | JWT生成時の`iss`クレームに使用 |

---

## Step 2: CIBAフローでの認証

Webアプリからの操作承認リクエストをモバイルアプリで処理します。

### 認証シーケンス

```mermaid
sequenceDiagram
    participant Web as Webアプリ
    participant IdP as IdP Server
    participant Device as モバイルアプリ
    participant FIDO as FIDOサーバー

    Note over Web, IdP: 1. CIBAリクエスト
    Web->>IdP: POST /backchannel/authentications<br/>login_hint=device:{deviceId}
    IdP->>IdP: デバイス・ユーザー特定
    IdP->>Web: auth_req_id
    IdP-->>Device: Push通知（FCM等）

    Note over Device, IdP: 2. デバイス認証（device_secret_jwt）
    Device->>Device: device_secret_jwtを生成
    Device->>IdP: GET /authentication-devices/{deviceId}/authentications<br/>Authorization: Bearer {device_secret_jwt}
    IdP->>IdP: JWT署名検証（デバイスの正当性確認）
    IdP->>Device: 認証トランザクション情報

    Note over Device, FIDO: 3. 本人認証（FIDO-UAF）
    Device->>IdP: POST /authentications/{id}/fido-uaf-authentication-challenge
    IdP->>FIDO: Challenge生成
    FIDO->>Device: Challenge
    Device->>Device: 生体認証（指紋/顔認証）
    Device->>FIDO: 署名済みレスポンス
    FIDO->>IdP: 認証成功
    IdP->>Device: 認証完了

    Note over Web, IdP: 4. トークン取得
    Web->>IdP: POST /tokens<br/>grant_type=urn:openid:params:grant-type:ciba
    IdP->>Web: Access Token, ID Token
```

### デバイス認証用JWT

デバイスエンドポイントにアクセスする際、`device_secret`で署名したJWTを送信します。

```json
{
  "iss": "device:device_abc123",
  "sub": "user_123",
  "aud": "https://idp.example.com",
  "jti": "unique-request-id",
  "iat": 1704067200,
  "exp": 1704067500
}
```

| クレーム | 説明 |
|---------|------|
| `iss` | 登録時に返却された`device_secret_jwt_issuer`の値 |
| `sub` | ユーザーID |
| `aud` | IdPのissuer URL |
| `jti` | リクエストごとにユニークなID（リプレイ攻撃防止） |
| `exp` | 有効期限（5分以内を推奨） |

### デバイスエンドポイントの認証要否

**`authentication_type: "device_secret_jwt"`の場合：**

```http
# JWTなし → 401エラー
GET /v1/authentication-devices/{deviceId}/authentications

HTTP/1.1 401 Unauthorized
{"error": "unauthorized", "error_description": "Device authentication required"}
```

```http
# JWTあり → 成功
GET /v1/authentication-devices/{deviceId}/authentications
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...

HTTP/1.1 200 OK
{"list": [{"id": "auth_123", "flow": "ciba", "binding_message": "承認コード: 1234"}]}
```

---

## （参考）JWT Bearer Grantによるトークン取得

CIBAフローとは別に、デバイスシークレットを使ってアクセストークンを直接取得することも可能です（RFC 7523）。

> **詳細**: [JWT認可グラント（JWT Bearer Grant）](concept-09-jwt-bearer-grant.md)を参照してください。クライアント設定、Subject Claim Mapping、外部IdP連携などの詳細が記載されています。

```mermaid
sequenceDiagram
    participant App as モバイルアプリ
    participant IdP as IdP Server

    App->>App: 1. device_secretでJWT生成
    Note over App: iss: device:{deviceId}<br/>sub: {userId}<br/>aud: {issuer}
    App->>IdP: 2. POST /oauth/token<br/>grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer<br/>assertion={signed_jwt}
    IdP->>IdP: 3. JWT署名検証
    IdP->>App: 4. Access Token
```

### リクエスト例

```http
POST /oauth/token
Content-Type: application/x-www-form-urlencoded

grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer
&assertion=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
&scope=openid profile
```

> **Note**: このパターンはCIBAフローのデバイスエンドポイント認証とは異なり、ユーザー操作なしでアクセストークンを取得します。セキュリティ要件に応じて使い分けてください。

---

## 認証トランザクションのレスポンス制御

デバイスエンドポイントから取得する認証トランザクションのレスポンス内容は、**デバイス認証の有無**によって制御されます。

### レスポンス内容の違い

| 認証設定 | `context`フィールド | 説明 |
|---------|-------------------|------|
| `authentication_type: "none"` | **含まれない** | 認証なしでアクセス可能。機密情報は除外 |
| `authentication_type: "access_token"` | **含まれる** | アクセストークン認証成功後のみ詳細情報を返却 |
| `authentication_type: "device_secret_jwt"` | **含まれる** | 対称鍵JWT（HMAC）認証成功後のみ詳細情報を返却 |
| `authentication_type: "private_key_jwt"` | **含まれる** | 非対称鍵JWT（RSA/EC）認証成功後のみ詳細情報を返却 |

### contextに含まれる情報

`context`フィールドには、認証リクエストの詳細情報が含まれます：

| フィールド | 説明 |
|-----------|------|
| `scopes` | 要求されたスコープ |
| `acr_values` | 要求された認証コンテキストクラス |
| `binding_message` | CIBAバインディングメッセージ |
| `authorization_details` | Rich Authorization Requests（RAR）の詳細 |

### レスポンス例

**デバイス認証あり（`authentication_type: "device_secret_jwt"`）：**

```json
{
  "list": [
    {
      "id": "auth_123",
      "flow": "ciba",
      "tenant_id": "tenant_abc",
      "client_id": "client_xyz",
      "context": {
        "scopes": "openid profile payment",
        "binding_message": "送金承認: ¥10,000",
        "acr_values": "urn:example:acr:mfa"
      },
      "created_at": "2024-01-01T00:00:00",
      "expires_at": "2024-01-01T00:05:00"
    }
  ],
  "total_count": 1
}
```

**デバイス認証なし（`authentication_type: "none"`）：**

```json
{
  "list": [
    {
      "id": "auth_123",
      "flow": "ciba",
      "tenant_id": "tenant_abc",
      "client_id": "client_xyz",
      "created_at": "2024-01-01T00:00:00",
      "expires_at": "2024-01-01T00:05:00"
    }
  ],
  "total_count": 1
}
```

> **セキュリティ**: `context`フィールドには、スコープやバインディングメッセージなど、認証リクエストの詳細情報が含まれます。デバイス認証なしでこれらの情報が漏洩すると、リクエストの内容を推測される可能性があるため、認証されたデバイスにのみ返却します。

---

## ユースケース

| シナリオ | 説明 |
|---------|------|
| **モバイルバンキング** | 別デバイスからの送金をスマホアプリで承認（CIBAフロー） |
| **2要素承認** | Webでの重要操作をモバイルアプリで承認（CIBAフロー） |
| **バックグラウンド処理** | アプリがバックグラウンドでAPIアクセス（JWT Bearer Grant） |
| **IoTデバイス連携** | スマートデバイスの操作承認 |

---

## セキュリティ考慮事項

### シークレット保存

| プラットフォーム | 推奨保存先 |
|-----------------|-----------|
| iOS | Keychain Services |
| Android | Android Keystore |

### JWT生成

- **有効期限**: 5分以内を推奨
- **jti**: リクエストごとにユニークな値を生成（リプレイ攻撃防止）

### シークレット管理

| 項目 | 推奨 |
|------|------|
| **有効期限** | 1年以内を推奨 |
| **ローテーション** | デバイス再登録でシークレットを更新 |
| **漏洩時対応** | デバイス削除 + 再登録で即座に無効化 |

---

## 関連ドキュメント

- [JWT認可グラント（JWT Bearer Grant）](concept-09-jwt-bearer-grant.md) - JWT Bearer Grantの詳細（クライアント設定、Subject Claim Mapping等）
- [CIBA（Client Initiated Backchannel Authentication）](concept-05-ciba.md)
- [パスワードレス認証](concept-07-passwordless.md)
- [認証ポリシー](concept-01-authentication-policy.md)
- [CIBA + FIDO-UAF How-to](../../content_05_how-to/phase-3-advanced/fido-uaf/01-ciba-flow.md) - CIBAフローでのFIDO-UAF認証の実装手順
- [Authentication Device API（OpenAPI）](../../../openapi/swagger-authentication-device-ja.yaml) - 認証デバイスAPIのOpenAPI仕様

## 参考仕様

- [RFC 7523: JWT Profile for OAuth 2.0 Authorization Grants](https://datatracker.ietf.org/doc/html/rfc7523)
- [OpenID Connect CIBA Core](https://openid.net/specs/openid-client-initiated-backchannel-authentication-core-1_0.html)
