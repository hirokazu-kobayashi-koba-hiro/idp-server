# 外部API認証 設定ガイド

## このドキュメントの目的

**外部APIと連携した認証**を設定できるようになることが目標です。パスワード認証の外部委譲、リスク判定、OTP連携など、設定だけでさまざまな外部APIと認証フローを構築できます。

### 所要時間
⏱️ **約15分**（基本設定）/ **約30分**（Challenge-Response / MFA 含む）

### 前提条件
- 管理者トークンを取得済み
- 組織ID・テナントIDを取得済み
- 連携先の外部APIの仕様（URL、リクエスト/レスポンス形式）を把握している

---

## 外部API認証とは

**1つのエンドポイント**（`/external-api-authentication`）で、リクエストボディの `interaction` フィールドにより**複数の外部API処理を切り替える**仕組みです。

```
クライアント
  │
  ├─ { "interaction": "password_verify", "username": "...", "password": "..." }
  │    → 外部パスワード認証API
  │
  ├─ { "interaction": "risk_check", "device_fingerprint": "..." }
  │    → 外部リスク判定API
  │
  └─ { "interaction": "otp_send", "phone_number": "..." }
       → 外部OTPサービス
```

---

## Step 1: 基本設定（パスワード認証の外部委譲）

最もシンプルなパターンから始めます。外部の認証APIにパスワード検証を委譲します。

### 認証設定の登録

```bash
curl -X POST \
  "${BASE_URL}/v1/management/organizations/${ORG_ID}/tenants/${TENANT_ID}/authentication-configurations" \
  -H "Authorization: Bearer ${MGMT_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "id": "'$(uuidgen)'",
    "type": "external-api-authentication",
    "attributes": {},
    "metadata": {
      "description": "外部パスワード認証"
    },
    "interactions": {
      "password_verify": {
        "request": {
          "schema": {
            "type": "object",
            "required": ["interaction", "username", "password"],
            "properties": {
              "interaction": { "type": "string" },
              "username": { "type": "string", "minLength": 1, "maxLength": 256 },
              "password": { "type": "string", "minLength": 1, "maxLength": 128 }
            }
          }
        },
        "execution": {
          "function": "http_request",
          "http_request": {
            "url": "https://your-auth-service.com/verify",
            "method": "POST",
            "header_mapping_rules": [
              { "static_value": "application/json", "to": "Content-Type" }
            ],
            "body_mapping_rules": [
              { "from": "$.request_body.username", "to": "username" },
              { "from": "$.request_body.password", "to": "password" }
            ]
          }
        },
        "user_resolve": {
          "user_mapping_rules": [
            { "from": "$.execution_http_request.response_body.user_id", "to": "external_user_id" },
            { "from": "$.execution_http_request.response_body.email", "to": "email" },
            { "from": "$.execution_http_request.response_body.name", "to": "name" },
            { "static_value": "your-auth-provider", "to": "provider_id" }
          ]
        },
        "response": {
          "body_mapping_rules": [
            { "from": "$.execution_http_request.response_body.email", "to": "email" }
          ]
        }
      }
    }
  }'
```

### 認証の実行

```bash
# 1. 認可リクエスト
AUTH_RESPONSE=$(curl -s -o /dev/null -w "%{redirect_url}" \
  "${BASE_URL}/${TENANT_ID}/v1/authorizations?client_id=${CLIENT_ID}&response_type=code&scope=openid+profile+email&redirect_uri=${REDIRECT_URI}&state=test")

AUTH_ID=$(echo "${AUTH_RESPONSE}" | grep -oP 'id=\K[^&]+')

# 2. 外部API認証
curl -X POST \
  "${BASE_URL}/${TENANT_ID}/v1/authorizations/${AUTH_ID}/external-api-authentication" \
  -H "Content-Type: application/json" \
  -d '{
    "interaction": "password_verify",
    "username": "user@example.com",
    "password": "secret"
  }'
```

**ここまでで動作確認**: レスポンスに `user` フィールドが含まれていれば成功です。

---

## Step 2: リスク判定の追加（user_resolve なし）

同じ認証設定に、リスク判定用の interaction を追加します。

### 設定に interaction を追加

既存の認証設定を更新して `risk_check` を追加:

```json
{
  "interactions": {
    "password_verify": { "..." : "（Step 1 と同じ）" },
    "risk_check": {
      "execution": {
        "function": "http_request",
        "http_request": {
          "url": "https://risk-service.com/assess",
          "method": "POST",
          "body_mapping_rules": [
            { "from": "$.request_body.device_fingerprint", "to": "fingerprint" },
            { "from": "$.request_body.ip_address", "to": "ip" }
          ]
        }
      },
      "response": {
        "body_mapping_rules": [
          { "from": "$.execution_http_request.response_body.risk_score", "to": "risk_score" },
          { "from": "$.execution_http_request.response_body.risk_level", "to": "risk_level" }
        ]
      }
    }
  }
}
```

### クライアント側のフロー

```
1. POST /external-api-authentication { "interaction": "password_verify", ... }
   → 認証成功

2. POST /external-api-authentication { "interaction": "risk_check", "device_fingerprint": "..." }
   → { "risk_score": 0.2, "risk_level": "low" }

3. risk_level が "high" なら追加認証を要求、"low" なら authorize へ進む

4. POST /authorize
```

`risk_check` は `user_resolve` がないため、ユーザー解決は行わず外部APIの結果だけを返します。

---

## Step 3: Challenge-Response パターン（previous_interaction）

OTPや生体認証など、2ステップの外部API連携を実現します。

### 設定

```json
{
  "interactions": {
    "otp_send": {
      "execution": {
        "function": "http_request",
        "http_request": {
          "url": "https://otp-service.com/send",
          "method": "POST",
          "body_mapping_rules": [
            { "from": "$.request_body.phone_number", "to": "phone" }
          ]
        },
        "http_request_store": {
          "key": "otp_send",
          "interaction_mapping_rules": [
            { "from": "$.response_body.transaction_id", "to": "transaction_id" }
          ]
        }
      },
      "response": {
        "body_mapping_rules": [
          { "from": "$.execution_http_request.response_body.transaction_id", "to": "transaction_id" }
        ]
      }
    },
    "otp_verify": {
      "execution": {
        "function": "http_request",
        "previous_interaction": { "key": "otp_send" },
        "http_request": {
          "url": "https://otp-service.com/verify",
          "method": "POST",
          "body_mapping_rules": [
            { "from": "$.interaction.transaction_id", "to": "transaction_id" },
            { "from": "$.request_body.code", "to": "verification_code" }
          ]
        }
      },
      "user_resolve": {
        "user_mapping_rules": [
          { "from": "$.execution_http_request.response_body.user_id", "to": "external_user_id" },
          { "from": "$.execution_http_request.response_body.email", "to": "email" },
          { "static_value": "otp-provider", "to": "provider_id" }
        ]
      }
    }
  }
}
```

### クライアント側のフロー

```
1. POST /external-api-authentication
   { "interaction": "otp_send", "phone_number": "+819012345678" }
   → { "transaction_id": "abc-123" }
   （OTPが SMS で送信される）

2. POST /external-api-authentication
   { "interaction": "otp_verify", "code": "123456" }
   → { "user": { "sub": "...", "email": "..." } }
   （transaction_id は previous_interaction 経由で自動取得）

3. POST /authorize
```

**ポイント**:
- `http_request_store`: challenge のレスポンスを保存
- `previous_interaction`: verify で保存データを自動取得
- `$.interaction.*`: 保存データへのアクセスパス

---

## Step 4: MFA の2段階目として利用

パスワード認証（1段階目）+ 外部API認証（2段階目）の MFA 構成を作ります。

### 認証ポリシーの登録

```bash
curl -X POST \
  "${BASE_URL}/v1/management/organizations/${ORG_ID}/tenants/${TENANT_ID}/authentication-policies" \
  -H "Authorization: Bearer ${MGMT_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "id": "'$(uuidgen)'",
    "flow": "oauth",
    "enabled": true,
    "policies": [
      {
        "description": "Password + External API MFA",
        "priority": 1,
        "available_methods": ["password", "external-api", "initial-registration"],
        "step_definitions": [
          {
            "method": "password",
            "order": 1,
            "requires_user": false,
            "user_identity_source": "username"
          },
          {
            "method": "external-api",
            "order": 2,
            "requires_user": true
          }
        ],
        "success_conditions": {
          "any_of": [
            [
              { "path": "$.password-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 },
              { "path": "$.external-api-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }
            ],
            [
              { "path": "$.initial-registration.success_count", "type": "integer", "operation": "gte", "value": 1 }
            ]
          ]
        }
      }
    ]
  }'
```

### クライアント側のフロー

```
1. POST /password-authentication
   { "username": "user@example.com", "password": "secret" }
   → 200 OK（1段階目完了）

2. POST /external-api-authentication
   { "interaction": "verify_identity", "username": "user@example.com", "password": "biometric-data" }
   → 200 OK（2段階目完了、ユーザー一致検証済み）

3. POST /authorize
   → トークン発行
```

### セキュリティ保護

2段階目では以下が自動で検証されます:

| チェック | 失敗時のエラー |
|---------|--------------|
| 1段階目が未完了 | `400 user_not_found` |
| 外部APIのユーザーと1段階目のユーザーが不一致 | `400 user_identity_mismatch` |

**重要**: 2段階目で外部APIが別のユーザーの情報を返しても、1段階目のユーザーが入れ替わることはありません。

---

## トラブルシューティング

### 外部APIに接続できない

```json
{ "error": "service_unavailable", ... }
```
→ 外部APIのURLが正しいか、ネットワーク経路を確認してください。`auth_type: "oauth2"` の場合、トークンエンドポイントも確認してください。

### interaction が見つからない

```json
{ "error": "invalid_request", "error_description": "The interaction 'xxx' is not configured." }
```
→ 認証設定の `interactions` キーとリクエストの `interaction` フィールドが一致しているか確認してください。

### 2段階目でユーザー不一致

```json
{ "error": "user_identity_mismatch" }
```
→ 外部APIが返すユーザー情報（email / externalUserId）が、1段階目で認証したユーザーと一致しているか確認してください。`user_mapping_rules` のマッピング先を確認してください。

### JSON Schema バリデーション失敗

```json
{ "error": "invalid_request", "error_messages": ["..."] }
```
→ リクエストボディが `request.schema` で定義した JSON Schema に合っているか確認してください。

---

## 次のステップ

- 📖 [設定リファレンス](../../content_06_developer-guide/05-configuration/authn/external-api.md) — 全設定項目の詳細
- 🔒 [認証ポリシー設定](../../content_06_developer-guide/05-configuration/authentication-policy.md) — MFA・条件付き認証の詳細
- 🔗 [セキュリティイベントフック](./04-security-event-hooks.md) — 認証イベントの外部通知
