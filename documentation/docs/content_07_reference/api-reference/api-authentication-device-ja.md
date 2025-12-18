# 認証デバイス用API仕様

このドキュメントでは、認証デバイスとの連携で使用するAPIの仕様を説明します。

---

## 概要

認証デバイスAPIは、FIDO2（WebAuthn）、FIDO-UAF、パスワード、SMS、メール認証などの各種認証方式をサポートします。
これらのAPIは認証フロー中に呼び出され、ユーザーの認証操作を処理します。

---

## 共通仕様

### 認証

- **Bearer Token**: 一部のAPIではリソースオーナーのアクセストークンが必要です
- **Authorization Session**: 認証フロー中のセッションIDが必要です

### レスポンス形式

- **Content-Type**: `application/json`
- **文字エンコーディング**: UTF-8

---

## エラーレスポンス

### 共通エラー形式

全ての認証デバイスAPIは、エラー時に以下の形式でレスポンスを返します。

```json
{
  "error": "error_code",
  "error_description": "エラーの詳細説明"
}
```

### HTTPステータスコード

| HTTPステータス | 説明 | 発生条件 |
|--------------|------|----------|
| `200` | 成功 | 認証操作が正常に完了した |
| `400` | クライアントエラー | リクエストパラメータが不正、認証失敗、検証エラー |
| `500` | サーバーエラー | サーバー内部エラー、外部サービス連携エラー |

### エラーコード

#### 認証失敗（400）

```json
{
  "error": "invalid_request",
  "error_description": "user is not found or invalid password"
}
```

**発生条件:**
- パスワードが一致しない
- ユーザーが存在しない
- 認証情報が無効

#### FIDO2認証失敗（400）

```json
{
  "error": "invalid_request",
  "error_description": "FIDO2 authentication succeeded but user could not be resolved"
}
```

**発生条件:**
- FIDO2検証は成功したが、ユーザー情報を解決できない
- 登録されていない認証器からのリクエスト
- 認証器データが不正

#### 検証エラー（400）

```json
{
  "error": "invalid_request",
  "error_description": "validation error details"
}
```

**発生条件:**
- リクエストボディの形式が不正
- 必須パラメータが欠落
- パラメータの値が制約に違反

#### サーバーエラー（500）

```json
{
  "error": "server_error",
  "error_description": "internal server error"
}
```

**発生条件:**
- データベース接続エラー
- 外部認証サービスとの連携エラー
- 予期しない内部エラー

---

## API エンドポイント

### パスワード認証

#### エンドポイント

```
POST /{tenant-id}/v1/authentications/{id}/password
```

#### リクエスト

```json
{
  "username": "user@example.com",
  "password": "SecureP@ssw0rd",
  "provider_id": "idp-server"
}
```

#### 成功レスポンス（200 OK）

```json
{
  "user": {
    "user_id": "123e4567-e89b-12d3-a456-426614174000",
    "preferred_username": "user@example.com",
    ...
  }
}
```

#### エラーレスポンス（400 Bad Request）

```json
{
  "error": "invalid_request",
  "error_description": "user is not found or invalid password"
}
```

---

### FIDO2 認証チャレンジ

#### エンドポイント

```
POST /{tenant-id}/v1/authentications/{id}/fido2-authentication-challenge
```

#### リクエスト

```json
{
  "preferred_username": "user@example.com"
}
```

#### 成功レスポンス（200 OK）

```json
{
  "challenge": "base64-encoded-challenge",
  "timeout": 60000,
  "rpId": "example.com",
  "allowCredentials": [...]
}
```

#### エラーレスポンス（400 Bad Request）

```json
{
  "error": "invalid_request",
  "error_description": "user not found or no credentials registered"
}
```

---

### FIDO2 認証

#### エンドポイント

```
POST /{tenant-id}/v1/authentications/{id}/fido2-authentication
```

#### リクエスト

```json
{
  "credentialId": "base64-encoded-credential-id",
  "authenticatorData": "base64-encoded-authenticator-data",
  "clientDataJSON": "base64-encoded-client-data",
  "signature": "base64-encoded-signature",
  "userHandle": "base64-encoded-user-handle"
}
```

#### 成功レスポンス（200 OK）

```json
{
  "username": "user@example.com",
  "user": {
    "user_id": "123e4567-e89b-12d3-a456-426614174000",
    ...
  }
}
```

#### エラーレスポンス（400 Bad Request）

```json
{
  "error": "invalid_request",
  "error_description": "FIDO2 authentication succeeded but user could not be resolved"
}
```

#### エラーレスポンス（500 Internal Server Error）

```json
{
  "error": "server_error",
  "error_description": "FIDO2 verification service error"
}
```

---

### FIDO-UAF 登録チャレンジ

#### エンドポイント

```
POST /{tenant-id}/v1/authentications/{id}/fido-uaf-registration-challenge
```

#### 成功レスポンス（200 OK）

```json
{
  "challenge": "base64-encoded-uaf-challenge",
  "policy": {...}
}
```

#### エラーレスポンス（400 Bad Request）

```json
{
  "error": "invalid_request",
  "error_description": "authentication session not found or expired"
}
```

---

### FIDO-UAF 登録

#### エンドポイント

```
POST /{tenant-id}/v1/authentications/{id}/fido-uaf-registration
```

#### リクエスト

```json
{
  "uafResponse": "base64-encoded-uaf-response"
}
```

#### 成功レスポンス（200 OK）

```json
{
  "device_id": "device-uuid",
  "user": {
    "user_id": "123e4567-e89b-12d3-a456-426614174000",
    ...
  }
}
```

#### エラーレスポンス（400 Bad Request）

```json
{
  "error": "invalid_request",
  "error_description": "UAF registration verification failed"
}
```

---

### FIDO-UAF 認証チャレンジ

#### エンドポイント

```
POST /{tenant-id}/v1/authentications/{id}/fido-uaf-authentication-challenge
```

#### 成功レスポンス（200 OK）

```json
{
  "challenge": "base64-encoded-uaf-challenge",
  "policy": {...}
}
```

---

### FIDO-UAF 認証

#### エンドポイント

```
POST /{tenant-id}/v1/authentications/{id}/fido-uaf-authentication
```

#### リクエスト

```json
{
  "uafResponse": "base64-encoded-uaf-response"
}
```

#### 成功レスポンス（200 OK）

```json
{
  "user": {
    "user_id": "123e4567-e89b-12d3-a456-426614174000",
    ...
  }
}
```

#### エラーレスポンス（400 Bad Request）

```json
{
  "error": "invalid_request",
  "error_description": "UAF authentication verification failed"
}
```

---

### メール認証チャレンジ

#### エンドポイント

```
POST /{tenant-id}/v1/authentications/{id}/email-challenge
```

#### リクエスト

```json
{
  "email": "user@example.com"
}
```

#### 成功レスポンス（200 OK）

```json
{
  "challenge_id": "challenge-uuid",
  "expires_in": 300
}
```

---

### メール認証

#### エンドポイント

```
POST /{tenant-id}/v1/authentications/{id}/email-authentication
```

#### リクエスト

```json
{
  "challenge_id": "challenge-uuid",
  "code": "123456"
}
```

#### 成功レスポンス（200 OK）

```json
{
  "user": {
    "user_id": "123e4567-e89b-12d3-a456-426614174000",
    "email": "user@example.com",
    ...
  }
}
```

#### エラーレスポンス（400 Bad Request）

```json
{
  "error": "invalid_request",
  "error_description": "invalid verification code or challenge expired"
}
```

---

### SMS認証チャレンジ

#### エンドポイント

```
POST /{tenant-id}/v1/authentications/{id}/sms-challenge
```

#### リクエスト

```json
{
  "phone_number": "+81-90-1234-5678"
}
```

#### 成功レスポンス（200 OK）

```json
{
  "challenge_id": "challenge-uuid",
  "expires_in": 300
}
```

---

### SMS認証

#### エンドポイント

```
POST /{tenant-id}/v1/authentications/{id}/sms-authentication
```

#### リクエスト

```json
{
  "challenge_id": "challenge-uuid",
  "code": "123456"
}
```

#### 成功レスポンス（200 OK）

```json
{
  "user": {
    "user_id": "123e4567-e89b-12d3-a456-426614174000",
    "phone_number": "+81-90-1234-5678",
    ...
  }
}
```

#### エラーレスポンス（400 Bad Request）

```json
{
  "error": "invalid_request",
  "error_description": "invalid verification code or challenge expired"
}
```

---

## セキュリティ考慮事項

### レート制限

- 連続した認証失敗はアカウントロックやレート制限の対象となります
- チャレンジレスポンスは有効期限内に使用する必要があります

### チャレンジの有効期限

- 認証チャレンジは通常60秒〜300秒の有効期限があります
- 有効期限切れのチャレンジは再取得が必要です

### エラーレスポンスの情報漏洩対策

- エラーメッセージは攻撃者にとって有用な情報を含まないよう設計されています
- 「ユーザーが存在しない」と「パスワードが間違っている」を区別しないエラーメッセージを返します

---

## 関連ドキュメント

- [認証ポリシー設定ガイド](../../content_05_how-to/how-to-07-authentication-policy-basic.md)
- [FIDO2認証フロー](../../content_11_learning/05-fido-webauthn/fido2-authentication-flow-interface.md)
- [FIDO-UAF登録フロー](../../content_05_how-to/how-to-13-fido-uaf-registration.md)
- [CIBAフロー](../../content_04_protocols/protocol-02-ciba-flow.md)
