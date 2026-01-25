# FIDO2/WebAuthn パスキー管理

## このドキュメントの目的

**パスキーの一覧取得・削除APIの設定**を行うことが目標です。

### 学べること

- ID Tokenからのパスキー一覧取得
- パスキー削除API
- ステップアップ認証の設定
- セキュリティイベント

### 所要時間
⏱️ **約10分**

### 前提条件
- [パスキー登録](./01-registration.md)が完了していること

---

## パスキー一覧の取得

ユーザーの登録済みパスキーは、**ID TokenまたはUserinfoの`authentication_devices`クレーム**として取得できます。専用のAPIエンドポイントはありません。

```
認可リクエスト（scope=claims:authentication_devices）
       ↓
認証 → ID Token / Userinfo
       ↓
authentication_devices クレーム
```

### 事前設定

`authentication_devices`クレームを取得するには、以下の設定が必要です。

#### 1. 認可サーバー設定

`custom_claims_scope_mapping`を有効にします。

```bash
curl -X PUT "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/${TENANT_ID}/authorization-servers/${AUTH_SERVER_ID}" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{
    "custom_claims_scope_mapping": true
  }'
```

#### 2. クライアント設定

クライアントのスコープに`claims:authentication_devices`を追加します。

```json
{
  "scope": "openid profile claims:authentication_devices"
}
```

#### 3. 認可リクエスト

認可リクエストのスコープに`claims:authentication_devices`を含めます。

```
GET /authorizations?
  response_type=code&
  client_id=${CLIENT_ID}&
  scope=openid+profile+claims:authentication_devices&
  ...
```

### ID Token / Userinfoのauthentication_devicesクレーム

```json
{
  "sub": "user-123",
  "email": "user@example.com",
  "authentication_devices": [
    {
      "id": "7d181f86-8ced-4d03-8f94-71cd849c9ab5",
      "app_name": "iPhone - Safari (iOS 17.2)",
      "platform": "Mobile",
      "os": "iOS",
      "model": "Safari 17.2",
      "available_methods": ["fido2"]
    }
  ]
}
```

| フィールド | 説明 |
|:---|:---|
| `id` | デバイスID（削除時に使用） |
| `app_name` | デバイスラベル（User-Agentから自動生成） |
| `platform` | プラットフォーム（Mobile/Desktop） |
| `os` | OS名 |
| `model` | ブラウザ名+バージョン |
| `available_methods` | 利用可能な認証方式（`fido2`等） |

### クライアント実装例

```javascript
// ID Tokenをデコードしてauthentication_devicesを取得
const idTokenClaims = JSON.parse(atob(idToken.split('.')[1]));
const devices = idTokenClaims.authentication_devices || [];
```

### パスキー削除

```
DELETE {tenant-id}/v1/me/authentication-devices/{device-id}
Authorization: Bearer {access_token}
```

#### レスポンス（成功）

```
HTTP/1.1 204 No Content
```

#### レスポンス（ステップアップ認証が必要）

```json
HTTP/1.1 401 Unauthorized

{
  "status": "step_up_authentication_required",
  "message": "Additional authentication is required for this operation"
}
```

#### レスポンス（エラー）

| HTTPステータス | 説明 |
|:---|:---|
| 204 | 削除成功 |
| 401 | ステップアップ認証が必要 |
| 403 | 権限なし |
| 404 | デバイスが存在しない |

---

## ステップアップ認証

セキュリティ上重要な操作（パスキー削除等）に追加認証を要求できます。

### 設定

テナントの認証ポリシーで設定します。

```json
{
  "step_up_authentication": {
    "enabled": true,
    "required_for": [
      "device_deregistration",
      "password_change",
      "email_change"
    ],
    "max_age_seconds": 300
  }
}
```

| パラメータ | 型 | 説明 |
|:---|:---|:---|
| `enabled` | boolean | ステップアップ認証の有効化 |
| `required_for` | array | 必要な操作の種類 |
| `max_age_seconds` | integer | 認証の有効期間（秒） |

### required_forの値

| 値 | 説明 |
|:---|:---|
| `device_deregistration` | デバイス削除 |
| `password_change` | パスワード変更 |
| `email_change` | メールアドレス変更 |
| `phone_change` | 電話番号変更 |
| `mfa_change` | MFA設定変更 |

### 認証フロー

1. クライアントが削除APIを呼び出し
2. サーバーが認証時刻を確認
3. `max_age_seconds`を超過していれば`401`を返却
4. クライアントが再認証を実行
5. 新しいトークンで削除APIを再呼び出し

---

## セキュリティイベント

パスキーの登録・削除はセキュリティイベントとして記録されます。

### イベントタイプ

| イベント | 説明 |
|:---|:---|
| `device_registration` | デバイス登録 |
| `device_deregistration` | デバイス削除 |
| `authentication_success` | 認証成功 |
| `authentication_failure` | 認証失敗 |

### イベントデータ

```json
{
  "event_type": "device_deregistration",
  "timestamp": "2025-01-25T10:30:00Z",
  "tenant_id": "tenant-123",
  "user_id": "user-123",
  "device_id": "7d181f86-8ced-4d03-8f94-71cd849c9ab5",
  "credential_type": "fido2",
  "credential_id": "DQjXXvTeChkhtUuvn7rWiw",
  "ip_address": "192.168.1.100",
  "user_agent": "Mozilla/5.0..."
}
```

### Webhook連携

セキュリティイベントをWebhookで外部システムに通知できます。

```json
{
  "security_events": {
    "webhook_url": "https://example.com/webhook/security",
    "events": [
      "device_registration",
      "device_deregistration"
    ]
  }
}
```

---

## デバイス情報の自動抽出

パスキー登録時、idp-serverはHTTPリクエストの`User-Agent`ヘッダーを解析し、デバイス情報を自動設定します。

### 抽出される情報

| フィールド | 抽出元 | 例 |
|:---|:---|:---|
| `app_name` | デバイス + ブラウザ + OS | `iPhone - Safari (iOS 17.2)` |
| `platform` | デバイス種別 | `Mobile` / `Desktop` |
| `os` | OS名 | `iOS`, `macOS`, `Windows` |
| `model` | ブラウザ + バージョン | `Safari 17.2` |

### 対応プラットフォーム

| User-Agent | platform | os |
|:---|:---|:---|
| iPhone | Mobile | iOS |
| iPad | Mobile | iOS |
| Android + Mobile | Mobile | Android |
| Macintosh | Desktop | macOS |
| Windows | Desktop | Windows |
| Linux | Desktop | Linux |

---

## 関連ドキュメント

- [パスキー登録](./01-registration.md) - 登録設定
- [パスキー認証](./02-authentication.md) - 認証API
- [アテステーション検証](./04-attestation-verification.md) - 認証器の信頼性検証
- [セキュリティイベント](../../phase-4-extensions/04-security-event-hooks.md) - イベント通知
- [認証ポリシー](../../phase-1-foundation/07-authentication-policy.md) - ポリシー設定

---

**最終更新**: 2025-01-25
