# 内部API仕様（身元確認コールバック）

このドキュメントでは、外部システム連携用の内部APIの仕様を説明します。
主に身元確認関連のコールバックAPIを含みます。

---

## 概要

内部APIは、外部の身元確認サービスからのコールバックを受け付けるためのAPIです。
審査結果の通知や検証済みクレーム（verified_claims）の登録などに使用されます。

これらのAPIは、Control Plane APIで登録されたテンプレートに基づいて動的にルーティングされます。

---

## 共通仕様

### 認証

コールバックAPIの認証方式はテンプレートで定義されます：

- **OAuth2**: `auth_type: "oauth2"` - クライアントクレデンシャルフロー
- **HMAC SHA256**: `auth_type: "hmac_sha256"` - リクエスト署名検証
- **なし**: `auth_type: "none"` - 認証なし（推奨されません）

### リクエスト形式

- **Content-Type**: `application/json`
- **文字エンコーディング**: UTF-8

### 申込み特定

コールバックAPIでは、テンプレートの `common.callback_application_id_param` に指定されたパラメータ名を使用して申込みを特定します。

```json
{
  "common": {
    "external_service": "external-kyc-service",
    "callback_application_id_param": "application_id"
  }
}
```

上記の設定の場合、リクエストボディに `application_id` パラメータが必要です。

---

## エラーレスポンス

### 共通エラー形式

```json
{
  "error": "error_code",
  "error_description": "エラーの詳細説明"
}
```

### HTTPステータスコード

| HTTPステータス | 説明 | 発生条件 |
|--------------|------|----------|
| `200` | 成功 | コールバック処理が正常に完了した |
| `400` | クライアントエラー | リクエストパラメータが不正、検証エラー |
| `401` | 認証エラー | 認証情報が無効 |
| `404` | リソース不明 | 指定された申込みが存在しない |
| `409` | 競合エラー | 申込みの状態が競合している |
| `500` | サーバーエラー | サーバー内部エラー |

### エラーコード

#### 認証エラー（401）

```json
{
  "error": "unauthorized",
  "error_description": "Invalid authentication credentials"
}
```

**発生条件:**
- OAuth2トークンが無効
- HMAC署名が一致しない
- 認証ヘッダーが欠落

#### 申込み不明（404）

```json
{
  "error": "not_found",
  "error_description": "Application not found"
}
```

**発生条件:**
- 指定された申込みIDが存在しない
- 申込みが既に削除されている

#### 検証エラー（400）

```json
{
  "error": "invalid_request",
  "error_description": "Request validation failed",
  "errors": [
    {
      "field": "status",
      "message": "must be one of: approved, rejected"
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
  "error_description": "Application status conflict",
  "current_status": "approved"
}
```

**発生条件:**
- 申込みが既に承認済み
- 申込みがキャンセル済み
- ステータス遷移が不正

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

---

## API エンドポイント

### 身元確認コールバック（プロセス指定なし）

#### エンドポイント

```
POST /{tenant-id}/internal/v1/identity-verification/callback/{verification-type}/{process}
```

#### パスパラメータ

| パラメータ | 説明 | 例 |
|----------|------|-----|
| `tenant-id` | テナントID（UUID） | `67e7eae6-62b0-4500-9eff-87459f63fc66` |
| `verification-type` | 申込み種別（テンプレートのtype） | `investment-account-opening` |
| `process` | プロセス名（テンプレートのprocessesキー） | `callback-result` |

#### 認証

テンプレートで定義された認証方式に従います。

**OAuth2の例:**
```
Authorization: Bearer {access_token}
```

**HMAC SHA256の例:**
```
X-Signature: sha256=<base64-encoded-hmac-signature>
X-Timestamp: 1640000000
```

#### リクエストボディ例

```json
{
  "application_id": "ext-12345",
  "status": "approved",
  "verified_data": {
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
  "document": {
    "type": "drivers_license",
    "number": "XXXX-XXXX-XXXX",
    "issue_date": "2020-01-15",
    "expiry_date": "2030-01-15"
  }
}
```

#### 成功レスポンス（200 OK）

```json
{
  "status": "success",
  "application_id": "550e8400-e29b-41d4-a716-446655440000",
  "verification_type": "investment-account-opening",
  "current_status": "approved"
}
```

#### エラーレスポンス（400 Bad Request）

```json
{
  "error": "invalid_request",
  "error_description": "Request validation failed",
  "errors": [
    {
      "field": "verified_data.date_of_birth",
      "message": "date format must be YYYY-MM-DD"
    }
  ]
}
```

#### エラーレスポンス（404 Not Found）

```json
{
  "error": "not_found",
  "error_description": "Application with external ID 'ext-12345' not found"
}
```

---

### 身元確認コールバック（申込みID指定）

#### エンドポイント

```
POST /{tenant-id}/internal/v1/identity-verification/callback/{verification-type}/{id}/{process}
```

#### パスパラメータ

| パラメータ | 説明 | 例 |
|----------|------|-----|
| `tenant-id` | テナントID（UUID） | `67e7eae6-62b0-4500-9eff-87459f63fc66` |
| `verification-type` | 申込み種別 | `investment-account-opening` |
| `id` | 申込みID（UUID） | `550e8400-e29b-41d4-a716-446655440000` |
| `process` | プロセス名 | `callback-examination` |

#### リクエストボディ例（審査中通知）

```json
{
  "status": "examination_processing",
  "message": "Your application is under review",
  "estimated_completion": "2024-12-31T23:59:59Z"
}
```

#### 成功レスポンス（200 OK）

```json
{
  "status": "success",
  "application_id": "550e8400-e29b-41d4-a716-446655440000",
  "current_status": "examination_processing"
}
```

---

## 認証方式詳細

### OAuth2認証

#### テンプレート設定例

```json
{
  "processes": {
    "callback-result": {
      "pre_hook": {
        "basic_auth": {
          "auth_type": "oauth2",
          "oauth_authorization": {
            "token_endpoint": "https://external-service.com/oauth/token",
            "client_id": "idp-server-client",
            "client_secret": "secret-value"
          }
        }
      }
    }
  }
}
```

#### リクエスト例

```bash
curl -X POST https://idp-server.example.com/{tenant-id}/internal/v1/identity-verification/callback/kyc/callback-result \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "Content-Type: application/json" \
  -d '{
    "application_id": "ext-12345",
    "status": "approved"
  }'
```

---

### HMAC SHA256認証

#### テンプレート設定例

```json
{
  "processes": {
    "callback-result": {
      "pre_hook": {
        "basic_auth": {
          "auth_type": "hmac_sha256",
          "hmac_authentication": {
            "secret_key": "shared-secret-key",
            "signature_header": "X-Signature",
            "timestamp_header": "X-Timestamp",
            "timestamp_tolerance": 300
          }
        }
      }
    }
  }
}
```

#### 署名生成方法

```javascript
const crypto = require('crypto');

const secret = 'shared-secret-key';
const timestamp = Math.floor(Date.now() / 1000);
const body = JSON.stringify({
  application_id: 'ext-12345',
  status: 'approved'
});

const message = `${timestamp}.${body}`;
const signature = crypto
  .createHmac('sha256', secret)
  .update(message)
  .digest('base64');

// リクエストヘッダー
// X-Signature: sha256=<signature>
// X-Timestamp: <timestamp>
```

#### リクエスト例

```bash
curl -X POST https://idp-server.example.com/{tenant-id}/internal/v1/identity-verification/callback/kyc/callback-result \
  -H "X-Signature: sha256=dGVzdHNpZ25hdHVyZQ==" \
  -H "X-Timestamp: 1640000000" \
  -H "Content-Type: application/json" \
  -d '{
    "application_id": "ext-12345",
    "status": "approved"
  }'
```

---

## データマッピング

### リクエストボディのマッピング

テンプレートの `post_hook.transition.status_mappings` で定義されたマッピングルールに基づいて、外部サービスからのレスポンスを内部ステータスに変換します。

#### 設定例

```json
{
  "post_hook": {
    "transition": {
      "status_mappings": [
        {
          "condition": {
            "field": "$.status",
            "operator": "equals",
            "value": "approved"
          },
          "target_status": "approved"
        },
        {
          "condition": {
            "field": "$.status",
            "operator": "equals",
            "value": "rejected"
          },
          "target_status": "rejected"
        }
      ]
    }
  }
}
```

### verified_claimsのマッピング

テンプレートの `result.verified_claims` で定義されたマッピングルールに基づいて、外部サービスからのデータをverified_claimsに変換します。

#### 設定例

```json
{
  "result": {
    "verified_claims": {
      "verification": {
        "trust_framework": {
          "type": "static_value",
          "value": "jp_aml"
        },
        "time": {
          "type": "json_path",
          "from": "$.verified_at"
        }
      },
      "claims": {
        "family_name": {
          "type": "json_path",
          "from": "$.verified_data.family_name"
        },
        "given_name": {
          "type": "json_path",
          "from": "$.verified_data.given_name"
        },
        "birthdate": {
          "type": "json_path",
          "from": "$.verified_data.date_of_birth"
        }
      }
    }
  }
}
```

---

## セキュリティ考慮事項

### 認証の推奨設定

- **本番環境**: OAuth2またはHMAC SHA256を使用してください
- **認証なし**: 開発環境・テスト環境のみで使用してください

### タイムスタンプ検証

HMAC認証では、タイムスタンプの有効期限（`timestamp_tolerance`）を設定してリプレイ攻撃を防止します。

### 署名検証

HMAC署名は、リクエストボディ全体とタイムスタンプから生成されます。
署名が一致しない場合、リクエストは拒否されます。

### IPアドレス制限

外部サービスのIPアドレスからのリクエストのみを許可するよう、ファイアウォールやロードバランサーで制限することを推奨します。

---

## エラーハンドリング

### リトライ戦略

外部サービスは、以下のエラー時にリトライを実施すべきです：

- **500 Internal Server Error**: 指数バックオフでリトライ
- **503 Service Unavailable**: 一定時間後にリトライ
- **504 Gateway Timeout**: タイムアウト値を増やしてリトライ

### エラー通知

コールバック処理に失敗した場合、外部サービスは代替手段（管理画面、メール通知等）でステータスを確認できるようにしてください。

---

## 関連ドキュメント

- [身元確認申込みガイド](../../content_05_how-to/how-to-16-identity-verification-application.md)
- [リソースオーナー用API仕様](./api-resource-owner-ja.md)
- [Control Plane API仕様](./control-plane-api-ja.md)
