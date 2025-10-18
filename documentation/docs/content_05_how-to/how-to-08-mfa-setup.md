# MFA（多要素認証）の設定

## このドキュメントの目的

**SMS OTPによるMFA（多要素認証）** を設定し、パスワード認証に追加することが目標です。

### 所要時間
⏱️ **約15分**

### 前提条件
- [パスワード認証](./how-to-05-user-registration.md)が設定済み
- 管理者トークンを取得済み
- 組織ID（organization-id）を取得済み

### Management API URL

**組織レベルAPI**（このドキュメントでの表記）:
```
POST /v1/management/organizations/{organization-id}/tenants/{tenant-id}/authentication-configurations
PUT  /v1/management/organizations/{organization-id}/tenants/{tenant-id}/authentication-policies/{policy-id}
```

**注意**: システムレベルAPIとの違い
- **組織レベル**: `POST /v1/management/organizations/{organization-id}/tenants/{tenant-id}/...` ← このドキュメント
- **システムレベル**: `POST /v1/management/tenants/{tenant-id}/...` ← 管理者のみ

通常の運用では組織レベルAPIを使用してください。

---

## MFA（多要素認証）とは

**2つ以上の認証要素**を組み合わせてセキュリティを強化する仕組みです。

```
第1要素: パスワード（知識）
  ↓ 成功
第2要素: SMS OTP（所持）
  ↓ 成功
認証完了 → Authorization Code発行
```

**メリット**:
- ✅ セキュリティ向上（パスワード漏洩だけでは突破できない）
- ✅ 不正ログイン防止

---

## Step 1: SMS OTP認証設定を作成

### Management APIで設定

```bash
curl -X POST "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/${TENANT_ID}/authentication-configurations" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{
    "type": "sms",
    "enabled": true,
    "otp_length": 6,
    "otp_expiry_seconds": 300,
    "sms_template": "Your verification code is: {otp}. Valid for 5 minutes."
  }'
```

### レスポンス

```json
{
  "dry_run": false,
  "result": {
    "id": "660e8400-e29b-41d4-a716-446655440001",
    "type": "sms",
    "enabled": true,
    "otp_length": 6,
    "otp_expiry_seconds": 300,
    "sms_template": "Your verification code is: {otp}. Valid for 5 minutes."
  }
}
```

**設定内容**:
- ✅ OTP（ワンタイムパスワード）6桁
- ✅ 有効期限5分（300秒）
- ✅ SMSテンプレート定義

---

## Step 2: 認証ポリシーをMFA対応に更新

パスワード認証**と**SMS OTP認証の**両方**を要求する設定に変更します。

```bash
curl -X PUT "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/${TENANT_ID}/authentication-policies/oauth" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{
    "flow": "oauth",
    "available_methods": ["password", "sms"],
    "success_conditions": {
      "type": "all",
      "authentication_methods": ["password", "sms"]
    }
  }'
```

**重要な変更点**:
- `available_methods`: `["password"]` → `["password", "sms"]` - 両方許可
- `type: "all"` - **両方成功**が必要（MFA）
- `type: "any"`の場合 - どちらか1つでOK（MFAではない）

---

## Step 3: ユーザーに電話番号を設定

SMS OTPを送信するには、ユーザーに電話番号が必要です。

```bash
curl -X PUT "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/${TENANT_ID}/users/${USER_ID}" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{
    "phone_number": "+81-90-1234-5678",
    "phone_number_verified": true
  }'
```

**電話番号形式**:
- ✅ E.164形式推奨: `+81-90-1234-5678`
- ✅ ハイフンあり・なし両対応: `+819012345678`

---

## Step 4: MFA動作確認

### 4.1 Authorization Request（通常通り）

```bash
curl -v "http://localhost:8080/${TENANT_ID}/v1/authorizations?\
response_type=code&\
client_id=${CLIENT_ID}&\
redirect_uri=${REDIRECT_URI}&\
scope=openid+profile&\
state=random-state"
```

### 4.2 第1要素: パスワード認証

```bash
curl -X POST "http://localhost:8080/${TENANT_ID}/v1/authentications/${AUTH_TRANSACTION_ID}" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "test-user@example.com",
    "password": "Test1234"
  }'
```

**レスポンス**:
```json
{
  "status": "ADDITIONAL_AUTHENTICATION_REQUIRED",
  "next_authentication_methods": ["sms"],
  "message": "Please complete SMS OTP authentication"
}
```

**重要**: `status: "ADDITIONAL_AUTHENTICATION_REQUIRED"` - まだ認証完了していない！

### 4.3 SMS OTP送信リクエスト

```bash
curl -X POST "http://localhost:8080/${TENANT_ID}/v1/authentications/${AUTH_TRANSACTION_ID}/sms/send" \
  -H "Content-Type: application/json" \
  -d '{
    "phone_number": "+81-90-1234-5678"
  }'
```

**レスポンス**:
```json
{
  "status": "OTP_SENT",
  "expires_in": 300
}
```

**実際の動作**: ユーザーの電話にSMSが届く
```
Your verification code is: 123456. Valid for 5 minutes.
```

### 4.4 第2要素: SMS OTP検証

```bash
curl -X POST "http://localhost:8080/${TENANT_ID}/v1/authentications/${AUTH_TRANSACTION_ID}/sms/verify" \
  -H "Content-Type: application/json" \
  -d '{
    "otp": "123456"
  }'
```

**成功レスポンス**:
```json
{
  "status": "SUCCESS",
  "redirect_uri": "https://app.example.com/callback?code=abc123&state=random-state"
}
```

**MFA完了！**

---

## MFA設定のバリエーション

### パターン1: パスワード + SMS OTP（このガイド）

```json
{
  "available_methods": ["password", "sms"],
  "success_conditions": {
    "type": "all",
    "authentication_methods": ["password", "sms"]
  }
}
```

### パターン2: パスワード + TOTP

```json
{
  "available_methods": ["password", "totp"],
  "success_conditions": {
    "type": "all",
    "authentication_methods": ["password", "totp"]
  }
}
```

### パターン3: 選択式MFA（どちらか1つでOK）

```json
{
  "available_methods": ["password", "sms", "totp"],
  "success_conditions": {
    "type": "all",
    "authentication_methods": [
      "password",
      {
        "type": "any",
        "authentication_methods": ["sms", "totp"]
      }
    ]
  }
}
```

**意味**: パスワード必須 + （SMS または TOTP）

---

## よくあるエラー

### エラー1: OTPが期限切れ

**エラー**:
```json
{
  "error": "otp_expired",
  "error_description": "OTP has expired"
}
```

**原因**: OTPの有効期限（5分）を過ぎた

**解決策**: `/sms/send`を再実行して新しいOTPを取得

---

### エラー2: OTPが間違っている

**エラー**:
```json
{
  "error": "invalid_otp",
  "error_description": "Invalid OTP"
}
```

**原因**: OTPの入力ミス

**解決策**:
- SMSを確認して正しいOTPを入力
- 3回失敗すると新しいOTP送信が必要

---

### エラー3: 電話番号が登録されていない

**エラー**:
```json
{
  "error": "phone_number_not_found",
  "error_description": "User does not have a phone number"
}
```

**原因**: ユーザーに電話番号が設定されていない

**解決策**: Step 3を実施してユーザーに電話番号を設定

---

## SMS送信サービスの設定

**注意**: SMS送信には外部サービス（Twilio、AWS SNS等）との連携が必要です。

### 開発環境（モック）

開発環境では、実際のSMSを送信せず、ログに出力：

```bash
# ログ確認
tail -f logs/idp-server.log | grep "SMS OTP"

# 出力例
SMS OTP sent to +81-90-1234-5678: 123456
```

### 本番環境（Twilio連携）

```bash
# Twilio設定を追加
curl -X POST "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/${TENANT_ID}/notification-configurations" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{
    "type": "sms",
    "provider": "twilio",
    "account_sid": "your-account-sid",
    "auth_token": "your-auth-token",
    "from_number": "+1-555-0100"
  }'
```

**詳細**: [外部サービス連携ガイド](../content_06_developer-guide/04-implementation-guides/impl-17-external-integration.md)

---

## 次のステップ

✅ MFA（SMS OTP）を設定できました！

### さらにセキュリティを強化
- [How-to: FIDO2/WebAuthn設定](./how-to-13-fido-uaf-registration.md) - 生体認証
- [How-to: TOTP設定](./how-to-XX-totp-setup.md) - Google Authenticator等

### より複雑な認証フロー
- [How-to: リスクベース認証](./how-to-XX-risk-based-authentication.md) - 状況に応じてMFA要求
- [How-to: Step-up認証](./how-to-XX-step-up-authentication.md) - 重要操作時に追加認証

---

**最終更新**: 2025-10-13
**難易度**: ⭐⭐☆☆☆（初級〜中級）
**対象**: MFAを初めて設定する管理者・開発者
