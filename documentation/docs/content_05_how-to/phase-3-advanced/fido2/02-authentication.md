# FIDO2/WebAuthn パスキー認証

## このドキュメントの目的

**認可コードフロー内でFIDO2パスキーを使用して認証する**ための設定を行うことが目標です。

### 学べること

- 認可コードフロー内でのFIDO2認証の仕組み
- パスワードレスログインの設定
- Discoverable / 非Discoverable Credentialの違い

### 所要時間
⏱️ **約15分**

### 前提条件
- [パスキー登録](./01-registration.md)が完了していること
- ユーザーがFIDO2クレデンシャルを登録済みであること
- [パスワード認証](../../phase-1-foundation/05-user-registration.md)が設定済みであること（パスワードからの移行時）

---

## 認可コードフロー内でのFIDO2認証

### 認証フローの全体像

FIDO2認証は、OAuth 2.0認可コードフロー内で行います。パスワード認証の代替として、または追加の認証要素として使用できます。

```
┌─────────────────────────────────────────────────────────────┐
│                    認可コードフロー                           │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  GET /v1/authorizations ────────────────────────────────┐   │
│       ↓                                                 │   │
│  ┌─────────────────────────────────────────────────┐    │   │
│  │  認証方式の選択                                   │    │   │
│  │  ├── パスワード認証                              │    │   │
│  │  └── FIDO2認証（パスキー）← このドキュメント      │    │   │
│  └─────────────────────────────────────────────────┘    │   │
│       ↓                                                 │   │
│  POST /authorizations/{id}/fido2-authentication-challenge│   │
│       ↓                                                 │   │
│  POST /authorizations/{id}/fido2-authentication         │   │
│       ↓                                                 │   │
│  POST /authorizations/{id}/authorize ───────────────────┘   │
│       ↓                                                     │
│  認可コード発行 → トークン取得                               │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### パスワードレスログイン

FIDO2認証が`success_conditions`に含まれていれば、パスワードなしでログイン可能です。

```json
{
  "success_conditions": {
    "any_of": [
      [{ "path": "$.password-authentication.success_count", "operation": "gte", "value": 1 }],
      [{ "path": "$.fido2-authentication.success_count", "operation": "gte", "value": 1 }]
    ]
  }
}
```

この設定では：
- パスワード認証のみ → ログイン成功
- FIDO2認証のみ → ログイン成功（パスワードレス）
- どちらか一方で十分

---

## 認証フローの種類

### 1. Discoverable Credential（パスキー）認証

**ユーザー名入力なし**で認証できます。認証器がクレデンシャルを保持しており、ユーザーが選択します。

```
┌────────────────────────────────────────────────────────┐
│  フロントエンド                                         │
│  ┌──────────────────────────────────────────────────┐  │
│  │  「パスキーでログイン」ボタン                      │  │
│  │         ↓                                        │  │
│  │  認証器がクレデンシャル一覧を表示                  │  │
│  │         ↓                                        │  │
│  │  ユーザーが選択 + 生体認証                        │  │
│  └──────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────┘
```

**APIフロー**:
```
POST /authorizations/{id}/fido2-authentication-challenge
Body: {}  ← usernameなし

Response:
{
  "challenge": "...",
  "allowCredentials": []  ← 空配列 = Discoverable
}
```

### 2. 非Discoverable Credential認証

**ユーザー名を入力**してから認証します。サーバーがクレデンシャルIDを指定します。

```
┌────────────────────────────────────────────────────────┐
│  フロントエンド                                         │
│  ┌──────────────────────────────────────────────────┐  │
│  │  ユーザー名入力: [user@example.com]               │  │
│  │         ↓                                        │  │
│  │  「パスキーでログイン」ボタン                      │  │
│  │         ↓                                        │  │
│  │  指定されたクレデンシャルで認証                    │  │
│  └──────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────┘
```

**APIフロー**:
```
POST /authorizations/{id}/fido2-authentication-challenge
Body: { "username": "user@example.com" }

Response:
{
  "challenge": "...",
  "allowCredentials": [
    { "type": "public-key", "id": "credential-id-1" },
    { "type": "public-key", "id": "credential-id-2" }
  ]
}
```

---

## 設定手順

### FIDO2認証設定（認証部分）

[パスキー登録](./01-registration.md)で作成した設定に、認証用のinteractionが含まれています。

```json
{
  "interactions": {
    "fido2-authentication-challenge": {
      "execution": {
        "function": "webauthn4j_authentication_challenge",
        "details": {
          "rp_id": "example.com",
          "origin": "https://example.com",
          "user_verification_required": true
        }
      },
      "response": {
        "body_mapping_rules": [
          { "from": "$.execution_webauthn4j", "to": "*" }
        ]
      }
    },
    "fido2-authentication": {
      "execution": {
        "function": "webauthn4j_authentication",
        "details": {
          "rp_id": "example.com",
          "origin": "https://example.com"
        }
      },
      "response": {
        "body_mapping_rules": [
          { "from": "$.execution_webauthn4j", "to": "*" }
        ]
      }
    }
  }
}
```

### 認証ポリシー設定

FIDO2をログイン方式として許可するには、`success_conditions`に追加します。

```json
{
  "policies": [
    {
      "available_methods": ["password", "fido2"],
      "success_conditions": {
        "any_of": [
          [
            {
              "path": "$.password-authentication.success_count",
              "type": "integer",
              "operation": "gte",
              "value": 1
            }
          ],
          [
            {
              "path": "$.fido2-authentication.success_count",
              "type": "integer",
              "operation": "gte",
              "value": 1
            }
          ]
        ]
      }
    }
  ]
}
```

---

## 認証フローAPI

### 全体フロー

```
1. GET  /{tenant}/v1/authorizations?response_type=code&...
   → 302 Redirect with authorization_id

2. POST /{tenant}/v1/authorizations/{id}/fido2-authentication-challenge
   → チャレンジ取得

3. [フロントエンド] navigator.credentials.get() で認証器を呼び出し

4. POST /{tenant}/v1/authorizations/{id}/fido2-authentication
   → 認証成功

5. POST /{tenant}/v1/authorizations/{id}/authorize
   → 認可コード発行
```

### FIDO2認証チャレンジ取得

```
POST {tenant}/v1/authorizations/{authorization-id}/fido2-authentication-challenge
Content-Type: application/json
```

**リクエスト（Discoverable Credential）**:
```json
{}
```

**リクエスト（非Discoverable Credential）**:
```json
{
  "username": "user@example.com"
}
```

**レスポンス**:
```json
{
  "challenge": "base64url-encoded-challenge",
  "rpId": "example.com",
  "timeout": 60000,
  "userVerification": "required",
  "allowCredentials": [
    {
      "type": "public-key",
      "id": "base64url-encoded-credential-id",
      "transports": ["internal", "hybrid"]
    }
  ]
}
```

| フィールド | 説明 |
|:---|:---|
| `challenge` | サーバーが生成したチャレンジ |
| `rpId` | Relying Party ID |
| `allowCredentials` | 許可するクレデンシャル（空 = Discoverable） |

### FIDO2認証完了

```
POST {tenant}/v1/authorizations/{authorization-id}/fido2-authentication
Content-Type: application/json

{
  "id": "credential-id",
  "rawId": "base64url-encoded-raw-id",
  "type": "public-key",
  "response": {
    "clientDataJSON": "base64url-encoded",
    "authenticatorData": "base64url-encoded",
    "signature": "base64url-encoded",
    "userHandle": "base64url-encoded-user-handle"
  }
}
```

**レスポンス（成功）**:
```json
{
  "status": "ok"
}
```

**レスポンス（エラー）**:

| HTTPステータス | エラーコード | 説明 |
|:---|:---|:---|
| 400 | `authentication_failed` | 認証処理に失敗 |
| 400 | `invalid_signature` | 署名検証失敗 |
| 404 | `credential_not_found` | クレデンシャルが見つからない |

---

## 検証フロー

idp-serverは認証時に以下の検証を行います。

```
認証器レスポンス
    │
    ├─1. clientDataJSON検証
    │     └── origin, challenge, type
    │
    ├─2. authenticatorData検証
    │     └── rpIdHash, UP flag, UV flag
    │
    ├─3. 署名検証
    │     └── 登録時の公開鍵で検証
    │
    ├─4. signCount検証
    │     └── リプレイ攻撃検出
    │
    └─5. ユーザー特定
          ├── userHandle（Discoverable）
          └── credentialId（非Discoverable）
```

### signCount（署名カウンター）

認証器は認証のたびにカウンターをインクリメントします。

```
前回: 10
今回: 11  → OK
今回: 10  → NG（リプレイ攻撃の可能性）
```

> **注意**: 一部のプラットフォーム認証器はsignCountを常に0で返します。

---

## セキュリティイベント

| イベントタイプ | 説明 |
|:---|:---|
| `fido2_authentication_challenge_success` | チャレンジ取得成功 |
| `fido2_authentication_success` | 認証成功 |
| `fido2_authentication_failure` | 認証失敗 |

---

## 関連ドキュメント

- [パスキー登録](./01-registration.md) - FIDO2クレデンシャルの登録
- [パスキー管理](./03-management.md) - 一覧・削除API
- [アテステーション検証](./04-attestation-verification.md) - 認証器の信頼性検証
- [認証ポリシー](../../phase-1-foundation/07-authentication-policy.md) - ポリシー設定

---

**最終更新**: 2025-01-25
