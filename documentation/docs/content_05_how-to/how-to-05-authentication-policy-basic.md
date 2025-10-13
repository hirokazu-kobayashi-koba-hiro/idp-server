# 認証ポリシー設定ガイド（基礎編）

## このドキュメントの目的

**認証ポリシー**の基本を理解し、シンプルな設定ができるようになることが目標です。

### 所要時間
⏱️ **約15分**

### 前提条件
- [パスワード認証設定](./how-to-04-password-authentication.md)完了
- 管理者トークンを取得済み
- 組織ID（organization-id）を取得済み

### Management API URL

**実際のAPI**: 組織レベルAPI
```
POST /v1/management/organizations/{organization-id}/tenants/{tenant-id}/authentication-policies
```

**このドキュメントでの表記**: 簡潔性のため、以下のように省略
```
POST /v1/management/tenants/${TENANT_ID}/authentication-policies
```

**注意**: 実際のAPI呼び出し時は`organizations/{organization-id}/`を含める必要があります。

**詳細**: [how-to-03 クライアント登録](./how-to-03-client-registration.md#management-api-url)参照

---

## 認証ポリシーとは

**どの認証方式を使って、どのような条件で認証成功とするか**を定義する設定です。

```
Authorization Request
  ↓
認証ポリシー確認
  ├─ 利用可能な認証方式は？ → available_methods
  ├─ 成功条件は？ → success_conditions
  └─ 失敗/ロック条件は？ → failure_conditions, lock_conditions
  ↓
認証実行
```

---

## Level 1: 最もシンプルなポリシー（5分）

### パスワードのみの認証

```bash
curl -X POST "http://localhost:8080/v1/management/tenants/${TENANT_ID}/authentication-policies" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{
    "flow": "oauth",
    "enabled": true,
    "policies": [
      {
        "description": "password only",
        "priority": 1,
        "conditions": {},
        "available_methods": ["password"],
        "success_conditions": {
          "type": "all",
          "authentication_methods": ["password"]
        }
      }
    ]
  }'
```

**設定内容**:
- `flow: "oauth"` - OAuth/OIDC認証フローで使用
- `available_methods: ["password"]` - パスワード認証のみ許可
- `success_conditions.type: "all"` - 指定した全ての認証方式が必要
- `authentication_methods: ["password"]` - パスワード認証が成功すれば完了

---

## Level 2: MFA（多要素認証）ポリシー（10分）

### パスワード + SMS OTP

```bash
curl -X POST "http://localhost:8080/v1/management/tenants/${TENANT_ID}/authentication-policies" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{
    "flow": "oauth",
    "enabled": true,
    "policies": [
      {
        "description": "password + sms mfa",
        "priority": 1,
        "conditions": {},
        "available_methods": ["password", "sms"],
        "success_conditions": {
          "type": "all",
          "authentication_methods": ["password", "sms"]
        }
      }
    ]
  }'
```

**設定内容**:
- `available_methods: ["password", "sms"]` - 両方許可
- `type: "all"` - **両方成功が必要**（MFA）

**認証フロー**:
```
1. ユーザーがパスワード入力
   → 成功
2. SMS OTP送信
   → ユーザーがOTP入力
   → 成功
3. 認証完了
```

---

## Level 3: 選択式MFA（15分）

### パスワード + （SMS または Email）

```bash
curl -X POST "http://localhost:8080/v1/management/tenants/${TENANT_ID}/authentication-policies" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{
    "flow": "oauth",
    "enabled": true,
    "policies": [
      {
        "description": "password + (sms or email)",
        "priority": 1,
        "conditions": {},
        "available_methods": ["password", "sms", "email"],
        "success_conditions": {
          "type": "all",
          "authentication_methods": [
            "password",
            {
              "type": "any",
              "authentication_methods": ["sms", "email"]
            }
          ]
        }
      }
    ]
  }'
```

**設定内容**:
- パスワード必須
- SMS **または** Email（ユーザーが選択）

**認証フロー**:
```
1. ユーザーがパスワード入力
   → 成功
2. ユーザーが選択
   - SMSを選択 → SMS OTP認証
   - Emailを選択 → Email OTP認証
3. 認証完了
```

---

## success_conditionsの基本

### type: "all" vs "any"

| type | 意味 | 例 |
|------|------|---|
| `all` | **全て**成功が必要 | パスワード **かつ** SMS OTP |
| `any` | **いずれか1つ**成功でOK | パスワード **または** SMS OTP |

### パターン1: type: "all"（AND条件）

```json
{
  "type": "all",
  "authentication_methods": ["password", "sms"]
}
```

**意味**: パスワード **AND** SMS OTP

**フロー**:
```
password成功 → sms成功 → 認証完了
password失敗 → 認証失敗
sms失敗 → 認証失敗
```

---

### パターン2: type: "any"（OR条件）

```json
{
  "type": "any",
  "authentication_methods": ["password", "sms", "webauthn"]
}
```

**意味**: パスワード **OR** SMS OTP **OR** WebAuthn

**フロー**:
```
passwordで成功 → 認証完了（sms、webauthnは不要）
smsで成功 → 認証完了
webauthnで成功 → 認証完了
```

---

### パターン3: ネスト（AND + OR）

```json
{
  "type": "all",
  "authentication_methods": [
    "password",
    {
      "type": "any",
      "authentication_methods": ["sms", "email", "webauthn"]
    }
  ]
}
```

**意味**: パスワード **AND** （SMS **OR** Email **OR** WebAuthn）

**フロー**:
```
1. password必須
2. sms、email、webauthnのいずれか1つ
```

---

## Level 4: 複数ポリシー（priority）

### クライアント別のポリシー

```bash
curl -X POST "http://localhost:8080/v1/management/tenants/${TENANT_ID}/authentication-policies" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{
    "flow": "oauth",
    "enabled": true,
    "policies": [
      {
        "description": "admin app - high security",
        "priority": 1,
        "conditions": {
          "client_ids": ["admin-app"]
        },
        "available_methods": ["password", "webauthn"],
        "success_conditions": {
          "type": "all",
          "authentication_methods": ["password", "webauthn"]
        }
      },
      {
        "description": "normal app - standard security",
        "priority": 2,
        "conditions": {
          "client_ids": ["user-app"]
        },
        "available_methods": ["password"],
        "success_conditions": {
          "type": "all",
          "authentication_methods": ["password"]
        }
      },
      {
        "description": "default - password only",
        "priority": 3,
        "conditions": {},
        "available_methods": ["password"],
        "success_conditions": {
          "type": "all",
          "authentication_methods": ["password"]
        }
      }
    ]
  }'
```

**動作**:
```
admin-app からのリクエスト
  → priority 1 にマッチ
  → パスワード + WebAuthn（高セキュリティ）

user-app からのリクエスト
  → priority 2 にマッチ
  → パスワードのみ（標準セキュリティ）

other-app からのリクエスト
  → priority 3 にマッチ（デフォルト）
  → パスワードのみ
```

**重要**: priorityが**小さい**ほど優先（1 > 2 > 3）

---

## Level 5: ACRマッピング（基礎）

### ACR（Authentication Context Class Reference）とは

**認証の強度レベル**を示す標準的な値です。

```
ユーザー認証完了
  ↓
どの認証方式を使った？
  - WebAuthn → ACR: urn:mace:incommon:iap:gold（高）
  - SMS OTP → ACR: urn:mace:incommon:iap:silver（中）
  - パスワード → ACR: urn:mace:incommon:iap:bronze（低）
  ↓
ID Tokenに acr クレームとして含める
```

### ACRマッピング設定

```json
{
  "flow": "oauth",
  "enabled": true,
  "policies": [
    {
      "description": "with acr mapping",
      "priority": 1,
      "conditions": {},
      "available_methods": ["password", "sms", "webauthn"],
      "acr_mapping_rules": {
        "urn:mace:incommon:iap:gold": ["webauthn"],
        "urn:mace:incommon:iap:silver": ["sms", "email"],
        "urn:mace:incommon:iap:bronze": ["password"]
      },
      "success_conditions": {
        "type": "any",
        "authentication_methods": ["password", "sms", "webauthn"]
      }
    }
  ]
}
```

**効果**:
```json
// WebAuthn認証した場合のID Token
{
  "sub": "user-12345",
  "acr": "urn:mace:incommon:iap:gold",  // 高レベル
  ...
}

// パスワード認証した場合のID Token
{
  "sub": "user-12345",
  "acr": "urn:mace:incommon:iap:bronze",  // 低レベル
  ...
}
```

**用途**:
- クライアントがACR値を確認してアクセス制御
- 重要操作は`gold`レベル必須等

**詳細**: [認証ポリシー詳細ガイド](./how-to-15-authentication-policy-advanced.md)

---

## よくあるエラー

### エラー1: 認証方式が許可されていない

**エラー**:
```json
{
  "error": "authentication_method_not_allowed",
  "error_description": "password authentication is not allowed"
}
```

**原因**: `available_methods`に`password`が含まれていない

**解決策**:
```bash
# 認証ポリシーを確認
curl "http://localhost:8080/v1/management/tenants/${TENANT_ID}/authentication-policies/oauth" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}"

# available_methodsに追加
curl -X PUT "http://localhost:8080/v1/management/tenants/${TENANT_ID}/authentication-policies/oauth" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{
    "flow": "oauth",
    "enabled": true,
    "policies": [
      {
        "description": "allow password",
        "priority": 1,
        "conditions": {},
        "available_methods": ["password"],
        "success_conditions": {
          "type": "all",
          "authentication_methods": ["password"]
        }
      }
    ]
  }'
```

---

### エラー2: 成功条件を満たせない

**エラー**:
```json
{
  "error": "authentication_incomplete",
  "error_description": "Required authentication methods not completed"
}
```

**原因**: `success_conditions`で指定した認証方式が完了していない

**例**:
```json
// 設定
"success_conditions": {
  "type": "all",
  "authentication_methods": ["password", "sms"]
}

// 実行
ユーザーがパスワード認証のみ実行
→ SMS OTP認証が未完了
→ エラー
```

**解決策**: 全ての必須認証方式を完了する

---

### エラー3: ポリシーが見つからない

**エラー**:
```json
{
  "error": "policy_not_found",
  "error_description": "No matching authentication policy found"
}
```

**原因**:
- 認証ポリシーが作成されていない
- `conditions`にマッチするポリシーがない

**解決策**:
```bash
# デフォルトポリシーを作成（conditions: {}）
curl -X POST "http://localhost:8080/v1/management/tenants/${TENANT_ID}/authentication-policies" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{
    "flow": "oauth",
    "enabled": true,
    "policies": [
      {
        "description": "default policy",
        "priority": 999,
        "conditions": {},  // すべてにマッチ
        "available_methods": ["password"],
        "success_conditions": {
          "type": "all",
          "authentication_methods": ["password"]
        }
      }
    ]
  }'
```

---

## 認証ポリシーのベストプラクティス

### ✅ 推奨

1. **デフォルトポリシーを必ず用意**
   ```json
   {
     "description": "default - fallback policy",
     "priority": 999,
     "conditions": {},  // すべてにマッチ
     ...
   }
   ```

2. **priorityは10刻みで設定**
   ```json
   priority: 10, 20, 30, ...
   ```
   - 後から挿入しやすい

3. **descriptionを分かりやすく**
   ```json
   // ✅ 良い
   "description": "admin app - password + webauthn mfa"

   // ❌ 悪い
   "description": "policy1"
   ```

---

### ❌ 避けるべき設定

1. **デフォルトポリシーなし**
   ```json
   "policies": [
     {
       "priority": 1,
       "conditions": { "client_ids": ["specific-app"] },
       ...
     }
   ]
   // ❌ specific-app以外がエラーになる
   ```

2. **available_methodsと success_conditionsの不一致**
   ```json
   // ❌ 間違い
   "available_methods": ["password"],  // パスワードのみ許可
   "success_conditions": {
     "type": "all",
     "authentication_methods": ["password", "sms"]  // SMSも必要？
   }
   // → SMS認証ができない（available_methodsにない）
   ```

3. **type: "all"とtype: "any"の混同**
   ```json
   // ❌ MFAのつもりがMFAになっていない
   "available_methods": ["password", "sms"],
   "success_conditions": {
     "type": "any",  // どちらか1つでOK
     "authentication_methods": ["password", "sms"]
   }
   // → パスワードのみで認証完了（MFAではない）
   ```

---

## 設定の確認方法

### ポリシー一覧取得

```bash
curl "http://localhost:8080/v1/management/tenants/${TENANT_ID}/authentication-policies" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  | jq '.'
```

### 特定のポリシー取得

```bash
curl "http://localhost:8080/v1/management/tenants/${TENANT_ID}/authentication-policies/oauth" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  | jq '.policies'
```

### テナントのDiscoveryで確認

```bash
curl "http://localhost:8080/${TENANT_ID}/.well-known/openid-configuration" \
  | jq '.acr_values_supported'
```

**レスポンス例**:
```json
[
  "urn:mace:incommon:iap:bronze",
  "urn:mace:incommon:iap:silver",
  "urn:mace:incommon:iap:gold"
]
```

---

## ポリシーの更新

### 既存ポリシーの更新

```bash
curl -X PUT "http://localhost:8080/v1/management/tenants/${TENANT_ID}/authentication-policies/oauth" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{
    "flow": "oauth",
    "enabled": true,
    "policies": [
      {
        "description": "updated policy",
        "priority": 1,
        "conditions": {},
        "available_methods": ["password", "sms", "webauthn"],
        "success_conditions": {
          "type": "any",
          "authentication_methods": ["password", "sms", "webauthn"]
        }
      }
    ]
  }'
```

**注意**: 全ての`policies`配列を含める必要があります（部分更新不可）

---

### ポリシーの無効化

```bash
curl -X PUT "http://localhost:8080/v1/management/tenants/${TENANT_ID}/authentication-policies/oauth" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{
    "flow": "oauth",
    "enabled": false,
    "policies": [...]
  }'
```

**効果**: このフローの認証ポリシーが無効化される

---

## 次のステップ

✅ 基本的な認証ポリシーを設定できました！

### より高度な設定
- [How-to: 認証ポリシー詳細](./how-to-15-authentication-policy-advanced.md) - 条件式、JSONPath、failure_conditions

### 実際の認証設定
- [How-to: パスワード認証](./how-to-03-password-authentication.md)
- [How-to: MFA設定](./how-to-09-mfa-setup.md)

### 関連概念
- [Concept: 認証ポリシー](../content_03_concepts/concept-05-authentication-policy.md)
- [Concept: MFA](../content_03_concepts/concept-08-mfa.md)

---

## 関連ドキュメント

- [Developer Guide: Authentication実装](../content_06_developer-guide/03-application-plane/04-authentication.md) - 開発者向け実装ガイド
- [API Reference](../content_07_reference/api-reference.md) - Management API仕様

---

**最終更新**: 2025-10-13
**難易度**: ⭐⭐☆☆☆（初級〜中級）
**対象**: 認証ポリシーを初めて設定する管理者・開発者
**習得スキル**: success_conditions、type: "all" vs "any"、priority、ACRマッピング基礎
