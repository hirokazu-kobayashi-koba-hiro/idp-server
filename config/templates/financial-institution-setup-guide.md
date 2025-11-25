# 金融機関向け設定テンプレート適用ガイド

## 概要

このガイドでは、idp-serverを金融機関向けに設定するためのテンプレート適用手順を説明します。

### テンプレートの特徴

**高セキュリティ**: FAPI準拠の厳格なセキュリティ設定
- FAPI Advanced対応（MTLS, JAR, JARM）
- 1ユーザー1デバイス制限
- 身元確認必須
- WebAuthn/FIDO-UAF認証優先

**包括的ログ記録**: すべての認証・認可イベントを記録
- 監査ログ完全保存
- ユーザー詳細情報記録
- トレーシング有効化

**厳格な認証ポリシー**: 2段階の認証ポリシー
- 高セキュリティ操作（送金・決済）: WebAuthn必須
- 標準操作（参照・口座情報）: WebAuthn/FIDO-UAF/SMS認証

## 前提条件

- idp-server v0.9.10以降がインストール済み
- PostgreSQLデータベースが稼働中
- 管理者アクセストークンを取得済み
- OpenSSLまたはJava Keytoolが利用可能

## セットアップ手順

### 1. 事前準備

#### 1.1 環境変数の設定

```bash
# 環境変数を設定
export BASE_URL="https://idp.example.com"
export TENANT_ID=$(uuidgen | tr 'A-Z' 'a-z')
export CLIENT_ID=$(uuidgen | tr 'A-Z' 'a-z')
export REDIRECT_URI="https://banking-app.example.com/callback"
export LOGO_URI="https://banking-app.example.com/logo.png"
export TOS_URI="https://banking-app.example.com/terms"
export POLICY_URI="https://banking-app.example.com/privacy"
```

#### 1.2 JWKSの生成

**テナント用JWKS（ES256キーペア）**

```bash
# EC秘密鍵生成
openssl ecparam -genkey -name prime256v1 -noout -out tenant-private-key.pem

# 公開鍵抽出
openssl ec -in tenant-private-key.pem -pubout -out tenant-public-key.pem

# JWKS形式に変換（以下はサンプル、実際にはツール使用推奨）
# JWKSコンバーターツール: https://github.com/mitreid-connect/mkjwk
```

**クライアント用JWKS（ES256キーペア）**

```bash
# クライアント秘密鍵生成
openssl ecparam -genkey -name prime256v1 -noout -out client-private-key.pem

# 公開鍵抽出
openssl ec -in client-private-key.pem -pubout -out client-public-key.pem

# JWKS形式に変換
```

**JWKS生成ツール（推奨）**

```bash
# Node.jsを使用した簡易生成スクリプト
npm install -g node-jose-tools
jose newkey -s 256 -t EC -a ES256 > tenant-jwks.json
jose newkey -s 256 -t EC -a ES256 > client-jwks.json
```

#### 1.3 環境変数にJWKSを設定

```bash
# JWKSをエスケープして環境変数に設定
export JWKS_CONTENT=$(cat tenant-jwks.json | jq -c)
export CLIENT_JWKS=$(cat client-jwks.json | jq -c)
```

### 2. テンプレートから設定ファイル生成

#### 2.1 テナント設定の生成

```bash
# テンプレートから設定ファイル生成
./config/scripts/generate-config.sh \
  -e production \
  -t financial-institution-tenant-template.json \
  -o production/financial-tenant.json
```

または、手動で変数を置換：

```bash
# envsubstを使用
envsubst < config/templates/financial-institution-tenant-template.json \
  > config/generated/production/financial-tenant.json
```

#### 2.2 クライアント設定の生成

```bash
# クライアント設定生成
./config/scripts/generate-config.sh \
  -e production \
  -t financial-institution-client-template.json \
  -o production/financial-client.json
```

### 3. 設定の適用

#### 3.1 管理者トークンの取得

```bash
# 管理者アクセストークン取得
export ADMIN_TOKEN=$(./config/scripts/get-access-token.sh)
```

#### 3.2 テナント作成

```bash
# テナント作成API実行
curl -X POST "${BASE_URL}/v1/management/tenants" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d @config/generated/production/financial-tenant.json
```

**期待レスポンス**:

```json
{
  "dry_run": false,
  "result": {
    "id": "tenant-uuid",
    "name": "financial-institution-tenant-uuid",
    "status": "active"
  }
}
```

#### 3.3 クライアント登録

```bash
# クライアント登録API実行
curl -X POST "${BASE_URL}/${TENANT_ID}/v1/management/clients" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d @config/generated/production/financial-client.json
```

### 4. 動作確認

#### 4.1 Discovery Endpoint確認

```bash
# OpenID Connect Discovery
curl "${BASE_URL}/${TENANT_ID}/.well-known/openid-configuration" | jq .
```

**確認ポイント**:
- `tls_client_certificate_bound_access_tokens`: `true`
- `token_endpoint_auth_methods_supported`: `["private_key_jwt", "tls_client_auth"]`
- `request_object_signing_alg_values_supported`: `["RS256", "ES256", "PS256"]`

#### 4.2 FAPI準拠確認

```bash
# FAPI Baseline Scopesの確認
curl "${BASE_URL}/${TENANT_ID}/.well-known/openid-configuration" \
  | jq '.extension.fapi_baseline_scopes'

# 期待値: ["read", "account"]
```

```bash
# FAPI Advance Scopesの確認
curl "${BASE_URL}/${TENANT_ID}/.well-known/openid-configuration" \
  | jq '.extension.fapi_advance_scopes'

# 期待値: ["write", "transfers", "payment_initiation"]
```

#### 4.3 認証ポリシー確認

```bash
# 認証ポリシーの確認
curl "${BASE_URL}/${TENANT_ID}/.well-known/openid-configuration" \
  | jq '.extension.authentication_policies'
```

**期待される認証ポリシー**:
1. `financial_high_security_policy`: 送金・決済操作（WebAuthn必須）
2. `financial_standard_policy`: 参照操作（WebAuthn/FIDO-UAF/SMS）

### 5. テストユーザー作成

#### 5.1 ユーザー登録

```bash
# ユーザー登録
export USER_SUB=$(uuidgen | tr 'A-Z' 'a-z')
export USER_EMAIL="test.user@example.com"
export USER_PASSWORD="SecurePassword123!"

curl -X POST "${BASE_URL}/${TENANT_ID}/v1/management/users" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "sub": "'${USER_SUB}'",
    "email": "'${USER_EMAIL}'",
    "email_verified": true,
    "password": "'${USER_PASSWORD}'",
    "given_name": "Test",
    "family_name": "User",
    "phone_number": "+81-90-1234-5678",
    "phone_number_verified": true,
    "address": {
      "country": "JP",
      "postal_code": "100-0001",
      "region": "Tokyo",
      "locality": "Chiyoda"
    }
  }'
```

#### 5.2 身元確認（Identity Verification）

金融機関向けテンプレートでは身元確認が必須です。

```bash
# 身元確認API（実装例）
curl -X POST "${BASE_URL}/${TENANT_ID}/v1/me/identity-verification/applications/kyc/submit" \
  -H "Authorization: Bearer ${USER_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "identity_document_type": "drivers_license",
    "document_number": "123456789012",
    "issued_date": "2020-01-01",
    "expiry_date": "2030-01-01"
  }'
```

#### 5.3 WebAuthn登録

```bash
# WebAuthn登録開始
curl -X POST "${BASE_URL}/${TENANT_ID}/v1/me/authentication-devices/webauthn/register/start" \
  -H "Authorization: Bearer ${USER_TOKEN}"

# WebAuthn登録完了
curl -X POST "${BASE_URL}/${TENANT_ID}/v1/me/authentication-devices/webauthn/register/finish" \
  -H "Authorization: Bearer ${USER_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "credential": {
      "id": "credential-id",
      "rawId": "...",
      "response": {
        "attestationObject": "...",
        "clientDataJSON": "..."
      },
      "type": "public-key"
    }
  }'
```

### 6. FAPI準拠フローのテスト

#### 6.1 PAR（Pushed Authorization Request）

```bash
# PAR Endpoint
curl -X POST "${BASE_URL}/${TENANT_ID}/v1/par" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --cert client-cert.pem \
  --key client-key.pem \
  -d "client_id=${CLIENT_ID}" \
  -d "request=$(cat signed-request-object.jwt)"

# レスポンス例
# {
#   "request_uri": "urn:ietf:params:oauth:request_uri:abc123",
#   "expires_in": 90
# }
```

#### 6.2 Authorization Request（JAR使用）

```bash
# 認可リクエスト（JWTリクエストオブジェクト使用）
# ブラウザでアクセス
open "${BASE_URL}/${TENANT_ID}/v1/authorizations?client_id=${CLIENT_ID}&request_uri=urn:ietf:params:oauth:request_uri:abc123"
```

#### 6.3 Token Endpoint（MTLS使用）

```bash
# トークンエンドポイント（MTLS認証）
curl -X POST "${BASE_URL}/${TENANT_ID}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --cert client-cert.pem \
  --key client-key.pem \
  -d "grant_type=authorization_code" \
  -d "code=authorization_code_value" \
  -d "redirect_uri=${REDIRECT_URI}" \
  -d "client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer" \
  -d "client_assertion=signed_jwt_assertion"
```

## トラブルシューティング

### よくあるエラー

#### 1. `invalid_client` エラー

**原因**: MTLS証明書の不一致、またはJWT Assertion署名エラー

**解決策**:
```bash
# 証明書確認
openssl x509 -in client-cert.pem -text -noout

# JWT Assertion検証
# https://jwt.io でデコードして確認
```

#### 2. `invalid_request` - JAR必須エラー

**原因**: `request` または `request_uri` パラメータ未指定

**解決策**:
```bash
# 必ずJWTリクエストオブジェクトを使用
# PARエンドポイント経由で request_uri を取得
```

#### 3. 身元確認未完了エラー

**原因**: ユーザーの身元確認（KYC）が未完了

**解決策**:
```bash
# 身元確認ステータス確認
curl "${BASE_URL}/${TENANT_ID}/v1/me/identity-verification/status" \
  -H "Authorization: Bearer ${USER_TOKEN}"

# 身元確認完了まで待機
```

#### 4. 認証デバイス重複エラー

**原因**: 1ユーザー1デバイス制限により、既存デバイスが存在

**解決策**:
```bash
# 既存デバイス削除
curl -X DELETE "${BASE_URL}/${TENANT_ID}/v1/me/authentication-devices/{device-id}" \
  -H "Authorization: Bearer ${USER_TOKEN}"

# 新規デバイス登録
```

## セキュリティベストプラクティス

### 1. 鍵管理

- **秘密鍵の保護**: HSM（Hardware Security Module）またはKMS（Key Management Service）使用推奨
- **鍵ローテーション**: 最低年1回の鍵更新
- **バックアップ**: 暗号化されたバックアップ保存

### 2. 証明書管理

- **有効期限監視**: 証明書失効前の更新通知設定
- **中間CA証明書**: 完全な証明書チェーン設定
- **失効リスト**: OCSP/CRL設定

### 3. ログ監視

- **異常検知**: 失敗認証の閾値アラート設定
- **ログ保存期間**: 最低7年間（金融機関規制準拠）
- **改ざん防止**: ログ署名・タイムスタンプ

### 4. アクセス制御

- **最小権限原則**: 必要最小限のスコープ付与
- **定期的レビュー**: 権限の定期見直し
- **緊急停止**: インシデント時の即座アクセス停止手順

## リファレンス

### ドキュメント

- [FAPI 1.0 Advanced](https://openid.net/specs/openid-financial-api-part-2-1_0.html)
- [OAuth 2.0 MTLS](https://datatracker.ietf.org/doc/html/rfc8705)
- [JWT Authorization Request (JAR)](https://datatracker.ietf.org/doc/html/rfc9101)
- [JARM](https://openid.net/specs/oauth-v2-jarm.html)

### 関連設定

- [テナント設定リファレンス](../../documentation/docs/content_06_developer-guide/05-configuration/tenant.md)
- [クライアント設定リファレンス](../../documentation/docs/content_06_developer-guide/05-configuration/client.md)
- [認証ポリシー設定](../../documentation/docs/content_05_how-to/how-to-03-tenant-setup.md)

## サポート

問題が発生した場合は、以下を確認してください：

1. [トラブルシューティングガイド](../../documentation/docs/content_08_ops/ops-01-test-strategy.md)
2. [GitHub Issues](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/issues)
3. セキュリティログ: `${BASE_URL}/${TENANT_ID}/v1/management/security-events`
