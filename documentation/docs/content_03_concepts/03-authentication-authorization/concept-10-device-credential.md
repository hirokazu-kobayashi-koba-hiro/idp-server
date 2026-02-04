# デバイスクレデンシャル管理

## 概要

**デバイスクレデンシャル**は、モバイルアプリがIdPサーバーと安全に通信するための認証情報です。

主なユースケースは**CIBAフロー**です。Webアプリからの操作承認リクエストをモバイルアプリで受信・認証する際、以下の2段階認証を実現します：

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

## ユースケース

| シナリオ | 説明 |
|---------|------|
| **モバイルバンキング** | 別デバイスからの送金をスマホアプリで承認 |
| **2要素承認** | Webでの重要操作をモバイルアプリで承認 |
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

- [CIBA（Client Initiated Backchannel Authentication）](concept-05-ciba.md)
- [パスワードレス認証](concept-07-passwordless.md)
- [認証ポリシー](concept-01-authentication-policy.md)

## 参考仕様

- [RFC 7523: JWT Profile for OAuth 2.0 Authorization Grants](https://datatracker.ietf.org/doc/html/rfc7523)
- [OpenID Connect CIBA Core](https://openid.net/specs/openid-client-initiated-backchannel-authentication-core-1_0.html)
