# リソースオーナー用API仕様

このドキュメントでは、リソースオーナー（エンドユーザー）が使用するAPIの仕様を説明します。
身元確認申込みAPIを含みます。

---

## 概要

リソースオーナーAPIは、ユーザー自身のプロフィール情報の取得・更新、認証デバイスの管理、身元確認の申込みなどをサポートします。
全てのAPIはリソースオーナーのアクセストークンによる認証が必要です。

---

## 共通仕様

### 認証

- **Bearer Token**: 全てのAPIでリソースオーナーのアクセストークンが必要です
- **Authorization ヘッダー**: `Authorization: Bearer {access_token}`

### レスポンス形式

- **Content-Type**: `application/json`
- **文字エンコーディング**: UTF-8

---

## エラーレスポンス

### 共通エラー形式

全てのリソースオーナーAPIは、エラー時に以下の形式でレスポンスを返します。

```json
{
  "error": "error_code",
  "error_description": "エラーの詳細説明",
  "errors": [
    {
      "field": "field_name",
      "message": "field specific error message"
    }
  ]
}
```

**注:** `errors` フィールドは検証エラー時のみ含まれます。

### HTTPステータスコード

| HTTPステータス | 説明 | 発生条件 |
|--------------|------|----------|
| `200` | 成功 | リクエストが正常に処理された |
| `400` | クライアントエラー | リクエストパラメータが不正、検証エラー |
| `401` | 認証エラー | アクセストークンが無効または期限切れ |
| `403` | 権限エラー | リソースへのアクセス権限がない |
| `404` | リソース不明 | 指定されたリソースが存在しない |
| `409` | 競合エラー | リソースの状態が競合している（例：重複申込み） |
| `429` | レート制限 | リクエスト数が上限を超えた |
| `500` | サーバーエラー | サーバー内部エラー |
| `503` | サービス利用不可 | サービスが一時的に利用できない |
| `504` | ゲートウェイタイムアウト | 外部サービスからのレスポンスがタイムアウトした |

### エラーコード

#### 認証エラー（401）

```json
{
  "error": "invalid_token",
  "error_description": "The access token is invalid or expired"
}
```

**発生条件:**
- アクセストークンが無効
- アクセストークンの期限が切れている
- トークンの署名検証に失敗

#### 権限エラー（403）

```json
{
  "error": "insufficient_scope",
  "error_description": "The request requires higher privileges than provided by the access token"
}
```

**発生条件:**
- 必要なスコープがトークンに含まれていない
- リソースへのアクセス権限がない

#### リソース不明（404）

```json
{
  "error": "not_found",
  "error_description": "The requested resource was not found"
}
```

**発生条件:**
- 指定された申込みIDが存在しない
- ユーザーに紐づくリソースが見つからない

#### 検証エラー（400）

```json
{
  "error": "invalid_request",
  "error_description": "Request validation failed",
  "errors": [
    {
      "field": "date_of_birth",
      "message": "date format must be YYYY-MM-DD"
    },
    {
      "field": "email",
      "message": "invalid email format"
    }
  ]
}
```

**発生条件:**
- リクエストボディの形式が不正
- 必須パラメータが欠落
- パラメータの値が制約に違反

#### 競合エラー（409）

```json
{
  "error": "conflict",
  "error_description": "A verification application is already in progress"
}
```

**発生条件:**
- 同じ種別の申込みが既に進行中
- リソースの状態が要求と矛盾している

#### レート制限（429）

```json
{
  "error": "too_many_requests",
  "error_description": "Too many requests. Please try again later.",
  "retry_after": 60
}
```

**発生条件:**
- 短時間に過剰なリクエストを送信
- レート制限の上限に到達

#### サーバーエラー（500）

```json
{
  "error": "server_error",
  "error_description": "An internal server error occurred"
}
```

**発生条件:**
- データベース接続エラー
- 予期しない内部エラー

#### サービス利用不可（503）

```json
{
  "error": "service_unavailable",
  "error_description": "The service is temporarily unavailable. Please try again later."
}
```

**発生条件:**
- システムメンテナンス中
- 外部サービスが利用不可
- サーバー過負荷

#### ゲートウェイタイムアウト（504）

```json
{
  "error": "gateway_timeout",
  "error_description": "The external service request timed out"
}
```

**発生条件:**
- 外部身元確認サービスからのレスポンスがタイムアウト
- 外部APIの応答遅延

---

## 身元確認申込みAPI

### 概要

身元確認申込みAPIは、ユーザーが身元確認プロセスを開始・管理するためのAPIです。
申込みテンプレートに基づいて動的にエンドポイントが生成されます。

### 初回申込み

#### エンドポイント

```
POST /{tenant-id}/v1/me/identity-verification/applications/{verification-type}/{process}
```

#### パスパラメータ

| パラメータ | 説明 | 例 |
|----------|------|-----|
| `tenant-id` | テナントID（UUID） | `67e7eae6-62b0-4500-9eff-87459f63fc66` |
| `verification-type` | 申込み種別（テンプレートのtype） | `investment-account-opening` |
| `process` | プロセス名（テンプレートのprocessesキー） | `apply` |

#### リクエストヘッダー

```
Authorization: Bearer {access_token}
Content-Type: application/json
```

#### リクエストボディ例

```json
{
  "personal_info": {
    "family_name": "山田",
    "given_name": "太郎",
    "date_of_birth": "1990-01-01",
    "address": {
      "country": "JP",
      "postal_code": "100-0001",
      "region": "東京都",
      "locality": "千代田区"
    }
  },
  "purpose": "investment"
}
```

#### 成功レスポンス（200 OK）

```json
{
  "application_id": "550e8400-e29b-41d4-a716-446655440000",
  "verification_type": "investment-account-opening",
  "status": "requested",
  "external_application_id": "ext-12345",
  "next_process": "ekyc",
  "expires_at": "2024-12-31T23:59:59Z"
}
```

#### エラーレスポンス（400 Bad Request）

```json
{
  "error": "invalid_request",
  "error_description": "Request validation failed",
  "errors": [
    {
      "field": "personal_info.date_of_birth",
      "message": "date format must be YYYY-MM-DD"
    }
  ]
}
```

#### エラーレスポンス（409 Conflict）

```json
{
  "error": "conflict",
  "error_description": "A verification application of this type is already in progress",
  "existing_application_id": "550e8400-e29b-41d4-a716-446655440000"
}
```

#### エラーレスポンス（503 Service Unavailable）

```json
{
  "error": "service_unavailable",
  "error_description": "External verification service is temporarily unavailable"
}
```

---

### 後続処理

#### エンドポイント

```
POST /{tenant-id}/v1/me/identity-verification/applications/{verification-type}/{id}/{process}
```

#### パスパラメータ

| パラメータ | 説明 | 例 |
|----------|------|-----|
| `tenant-id` | テナントID（UUID） | `67e7eae6-62b0-4500-9eff-87459f63fc66` |
| `verification-type` | 申込み種別 | `investment-account-opening` |
| `id` | 申込みID（UUID） | `550e8400-e29b-41d4-a716-446655440000` |
| `process` | プロセス名 | `ekyc` |

#### リクエストボディ例（eKYCプロセス）

```json
{
  "identity_document": {
    "type": "drivers_license",
    "front_image": "base64-encoded-image-data",
    "back_image": "base64-encoded-image-data"
  },
  "selfie_image": "base64-encoded-selfie-data"
}
```

#### 成功レスポンス（200 OK）

```json
{
  "application_id": "550e8400-e29b-41d4-a716-446655440000",
  "verification_type": "investment-account-opening",
  "status": "applying",
  "current_process": "ekyc",
  "next_process": null,
  "message": "eKYC data submitted successfully"
}
```

#### エラーレスポンス（404 Not Found）

```json
{
  "error": "not_found",
  "error_description": "Application not found or does not belong to this user"
}
```

#### エラーレスポンス（400 Bad Request - プロセスシーケンス違反）

```json
{
  "error": "invalid_request",
  "error_description": "Process sequence violation",
  "error_messages": [
    "process 'complete-verification' is not available at current state"
  ]
}
```

---

### 申込み一覧取得

#### エンドポイント

```
GET /{tenant-id}/v1/me/identity-verification/applications
```

#### クエリパラメータ

| パラメータ | 説明 | デフォルト | 必須 |
|----------|------|-----------|------|
| `type` | 申込み種別でフィルタ | なし | ✗ |
| `status` | ステータスでフィルタ | なし | ✗ |
| `limit` | 取得件数制限 | 20 | ✗ |
| `offset` | 取得開始位置 | 0 | ✗ |

#### 成功レスポンス（200 OK）

```json
{
  "applications": [
    {
      "application_id": "550e8400-e29b-41d4-a716-446655440000",
      "verification_type": "investment-account-opening",
      "status": "examination_processing",
      "created_at": "2024-01-15T10:30:00Z",
      "updated_at": "2024-01-16T14:20:00Z"
    },
    {
      "application_id": "660e9511-f39c-52e5-b827-557766551111",
      "verification_type": "kyc-basic",
      "status": "approved",
      "created_at": "2023-12-01T09:00:00Z",
      "updated_at": "2023-12-05T16:45:00Z"
    }
  ],
  "total": 2,
  "limit": 20,
  "offset": 0
}
```

---

### 申込み削除

#### エンドポイント

```
DELETE /{tenant-id}/v1/me/identity-verification/applications/{verification-type}/{id}
```

#### 成功レスポンス（200 OK）

```json
{
  "application_id": "550e8400-e29b-41d4-a716-446655440000",
  "verification_type": "investment-account-opening",
  "status": "cancelled",
  "message": "Application cancelled successfully"
}
```

#### エラーレスポンス（404 Not Found）

```json
{
  "error": "not_found",
  "error_description": "Application not found"
}
```

#### エラーレスポンス（400 Bad Request）

```json
{
  "error": "invalid_request",
  "error_description": "Cannot cancel application in current status",
  "current_status": "approved"
}
```

---

### 検証結果取得

#### エンドポイント

```
GET /{tenant-id}/v1/me/identity-verification/results
```

#### 成功レスポンス（200 OK）

```json
{
  "verified_claims": [
    {
      "verification": {
        "trust_framework": "jp_aml",
        "time": "2024-01-16T10:30:00Z",
        "verification_process": "investment-account-opening",
        "evidence": [
          {
            "type": "id_document",
            "method": "pipp",
            "document": {
              "type": "drivers_license",
              "issuer": {
                "name": "Tokyo Metropolitan Police",
                "country": "JP"
              },
              "number": "XXXX-XXXX-XXXX",
              "date_of_issuance": "2020-01-15",
              "date_of_expiry": "2030-01-15"
            }
          }
        ]
      },
      "claims": {
        "family_name": "山田",
        "given_name": "太郎",
        "birthdate": "1990-01-01",
        "address": {
          "country": "JP",
          "postal_code": "100-0001",
          "region": "東京都",
          "locality": "千代田区"
        }
      }
    }
  ]
}
```

#### エラーレスポンス（404 Not Found）

```json
{
  "error": "not_found",
  "error_description": "No verified claims found for this user"
}
```

---

## MFA（多要素認証）管理API

### FIDO2登録開始

#### エンドポイント

```
POST /{tenant-id}/v1/me/mfa/fido2-registration
```

#### リクエストボディ

```json
{
  "device_name": "My iPhone",
  "platform": "iOS"
}
```

#### 成功レスポンス（200 OK）

```json
{
  "id": "registration-session-id",
  "expires_at": "2024-01-15T10:35:00Z"
}
```

---

### FIDO-UAF登録開始

#### エンドポイント

```
POST /{tenant-id}/v1/me/mfa/fido-uaf-registration
```

#### リクエストボディ

```json
{
  "app_name": "MyApp",
  "platform": "Android",
  "os": "Android 14",
  "model": "Pixel 8",
  "locale": "ja"
}
```

#### 成功レスポンス（200 OK）

```json
{
  "id": "registration-session-id",
  "expires_at": "2024-01-15T10:35:00Z"
}
```

---

## UserInfo API

### ユーザー情報取得

#### エンドポイント

```
GET /{tenant-id}/userinfo
```

#### 成功レスポンス（200 OK）

```json
{
  "sub": "123e4567-e89b-12d3-a456-426614174000",
  "preferred_username": "user@example.com",
  "email": "user@example.com",
  "email_verified": true,
  "name": "山田 太郎",
  "family_name": "山田",
  "given_name": "太郎",
  "authentication_devices": [
    {
      "device_id": "device-uuid",
      "device_name": "My iPhone",
      "device_type": "fido2",
      "registered_at": "2024-01-10T09:00:00Z"
    }
  ],
  "verified_claims": [...]
}
```

---

## セキュリティ考慮事項

### アクセストークンの管理

- アクセストークンは安全に保管し、第三者に漏洩しないよう注意してください
- トークンの有効期限が切れた場合は、リフレッシュトークンで更新してください

### レート制限

- 過剰なリクエストはレート制限の対象となります
- 429エラー時は `retry_after` ヘッダーの値（秒）だけ待機してから再試行してください

### データプライバシー

- 個人情報は暗号化されて保存されます
- 身元確認データは最小限の期間のみ保持されます

---

## 関連ドキュメント

- [身元確認申込みガイド](../../content_05_how-to/how-to-16-identity-verification-application.md)
- [FIDO2登録フロー](../../content_05_how-to/how-to-13-fido-uaf-registration.md)
- [認証ポリシー](../../content_03_concepts/03-authentication-authorization/concept-06-authentication-policy.md)
