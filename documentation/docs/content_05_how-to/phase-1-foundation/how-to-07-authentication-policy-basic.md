# 認証ポリシー設定ガイド（基礎編）

## このドキュメントの目的

**認証ポリシー**の基本を理解し、シンプルな設定ができるようになることが目標です。

### 所要時間
⏱️ **約15分**

### 前提条件
- [パスワード認証設定](./how-to-05-user-registration.md)完了
- 管理者トークンを取得済み
- 組織ID（organization-id）を取得済み

### Management API URL

**組織レベルAPI**（このドキュメントでの表記）:
```
POST /v1/management/organizations/{organization-id}/tenants/{tenant-id}/authentication-policies
```

**注意**: システムレベルAPIとの違い
- **組織レベル**: `POST /v1/management/organizations/{organization-id}/tenants/{tenant-id}/authentication-policies` ← このドキュメント
- **システムレベル**: `POST /v1/management/tenants/{tenant-id}/authentication-policies` ← 管理者のみ

通常の運用では組織レベルAPIを使用してください。

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
curl -X POST "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/${TENANT_ID}/authentication-policies" \
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
          "any_of": [
            [
              {
                "path": "$.password-authentication.success_count",
                "type": "integer",
                "operation": "gte",
                "value": 1
              }
            ]
          ]
        }
      }
    ]
  }'
```

**設定内容**:
- `flow: "oauth"` - OAuth/OIDC認証フローで使用
- `available_methods: ["password"]` - UIにパスワード認証を表示
- `success_conditions.any_of` - 成功条件の配列（外側はOR、内側はAND）
- `$.password-authentication.success_count >= 1` - パスワード認証が1回以上成功すれば完了

---

## Level 2: MFA（多要素認証）ポリシー（10分）

### パスワード + SMS OTP

```bash
curl -X POST "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/${TENANT_ID}/authentication-policies" \
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
          "any_of": [
            [
              {
                "path": "$.password-authentication.success_count",
                "type": "integer",
                "operation": "gte",
                "value": 1
              },
              {
                "path": "$.sms-authentication.success_count",
                "type": "integer",
                "operation": "gte",
                "value": 1
              }
            ]
          ]
        }
      }
    ]
  }'
```

**設定内容**:
- `available_methods: ["password", "sms"]` - UIに両方の認証方式を表示
- `any_of: [[ 条件1, 条件2 ]]` - **両方成功が必要**（内側の配列はAND条件）

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
curl -X POST "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/${TENANT_ID}/authentication-policies" \
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
          "any_of": [
            [
              {
                "path": "$.password-authentication.success_count",
                "type": "integer",
                "operation": "gte",
                "value": 1
              },
              {
                "path": "$.sms-authentication.success_count",
                "type": "integer",
                "operation": "gte",
                "value": 1
              }
            ],
            [
              {
                "path": "$.password-authentication.success_count",
                "type": "integer",
                "operation": "gte",
                "value": 1
              },
              {
                "path": "$.email-authentication.success_count",
                "type": "integer",
                "operation": "gte",
                "value": 1
              }
            ]
          ]
        }
      }
    ]
  }'
```

**設定内容**:
- パスワード必須
- SMS **または** Email（ユーザーが選択）
- `any_of: [[ パスワード, SMS ], [ パスワード, Email ]]` - どちらかのグループを満たせばOK

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

### any_of構造

```json
{
  "success_conditions": {
    "any_of": [
      [ 条件グループ1 ],  // このグループ内の条件は全てAND
      [ 条件グループ2 ]   // グループ間はOR
    ]
  }
}
```

| 構造 | 意味 | 例 |
|------|------|---|
| `any_of: [[ 条件1, 条件2 ]]` | **全て**成功が必要（AND） | パスワード **かつ** SMS OTP |
| `any_of: [[ 条件1 ], [ 条件2 ]]` | **いずれか1つ**成功でOK（OR） | パスワード **または** SMS OTP |

### パターン1: AND条件（MFA）

```json
{
  "success_conditions": {
    "any_of": [
      [
        {
          "path": "$.password-authentication.success_count",
          "type": "integer",
          "operation": "gte",
          "value": 1
        },
        {
          "path": "$.sms-authentication.success_count",
          "type": "integer",
          "operation": "gte",
          "value": 1
        }
      ]
    ]
  }
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

### パターン2: OR条件

```json
{
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
          "path": "$.sms-authentication.success_count",
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
```

**意味**: パスワード **OR** SMS OTP **OR** FIDO2

**フロー**:
```
passwordで成功 → 認証完了（sms、fido2は不要）
smsで成功 → 認証完了
fido2で成功 → 認証完了
```

---

### パターン3: AND + OR（選択式MFA）

```json
{
  "success_conditions": {
    "any_of": [
      [
        { "path": "$.password-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 },
        { "path": "$.sms-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }
      ],
      [
        { "path": "$.password-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 },
        { "path": "$.email-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }
      ],
      [
        { "path": "$.password-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 },
        { "path": "$.fido2-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }
      ]
    ]
  }
}
```

**意味**: パスワード **AND** （SMS **OR** Email **OR** FIDO2）

**フロー**:
```
1. password必須
2. sms、email、fido2のいずれか1つ
```

---

### JSONPath条件の書き方

| フィールド | 説明 | 例 |
|-----------|------|---|
| `path` | JSONPath形式で認証結果を参照 | `$.password-authentication.success_count` |
| `type` | 値の型 | `integer`, `string`, `boolean` |
| `operation` | 比較演算子 | `gte`, `lte`, `eq`, `gt`, `lt` |
| `value` | 比較する値 | `1` |

**利用可能なpath例**:
- `$.password-authentication.success_count` - パスワード認証成功回数
- `$.sms-authentication.success_count` - SMS認証成功回数
- `$.email-authentication.success_count` - Email認証成功回数
- `$.fido2-authentication.success_count` - FIDO2認証成功回数
- `$.fido-uaf-authentication.success_count` - FIDO UAF認証成功回数
- `$.password-authentication.failure_count` - パスワード認証失敗回数

---

## Level 4: 複数ポリシー（priority）

### クライアント別のポリシー

```bash
curl -X POST "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/${TENANT_ID}/authentication-policies" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{
    "flow": "oauth",
    "enabled": true,
    "policies": [
      {
        "description": "admin app - high security",
        "priority": 100,
        "conditions": {
          "client_ids": ["admin-app"]
        },
        "available_methods": ["password", "fido2"],
        "success_conditions": {
          "any_of": [
            [
              { "path": "$.password-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 },
              { "path": "$.fido2-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }
            ]
          ]
        }
      },
      {
        "description": "normal app - standard security",
        "priority": 50,
        "conditions": {
          "client_ids": ["user-app"]
        },
        "available_methods": ["password"],
        "success_conditions": {
          "any_of": [
            [
              { "path": "$.password-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }
            ]
          ]
        }
      },
      {
        "description": "default - password only",
        "priority": 1,
        "conditions": {},
        "available_methods": ["password"],
        "success_conditions": {
          "any_of": [
            [
              { "path": "$.password-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }
            ]
          ]
        }
      }
    ]
  }'
```

**動作**:
```
admin-app からのリクエスト
  → priority 100 にマッチ（最優先）
  → パスワード + FIDO2（高セキュリティ）

user-app からのリクエスト
  → priority 50 にマッチ
  → パスワードのみ（標準セキュリティ）

other-app からのリクエスト
  → priority 1 にマッチ（デフォルト・最低優先度）
  → パスワードのみ
```

**重要**: priorityが**大きい**ほど優先（100 > 50 > 1）

---

## Level 5: ACRマッピング（基礎）

### ACR（Authentication Context Class Reference）とは

**認証の強度レベル**を示す標準的な値です。

```
ユーザー認証完了
  ↓
どの認証方式を使った？
  - FIDO2 → ACR: urn:mace:incommon:iap:gold（高）
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
      "available_methods": ["password", "sms", "fido2"],
      "acr_mapping_rules": {
        "urn:mace:incommon:iap:gold": ["fido2"],
        "urn:mace:incommon:iap:silver": ["sms", "email"],
        "urn:mace:incommon:iap:bronze": ["password"]
      },
      "success_conditions": {
        "any_of": [
          [{ "path": "$.password-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }],
          [{ "path": "$.sms-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }],
          [{ "path": "$.fido2-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }]
        ]
      }
    }
  ]
}
```

**効果**:
```json
// FIDO2認証した場合のID Token
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

**詳細**: [認証ポリシー詳細ガイド](./how-to-10-authentication-policy-advanced.md)

---

## 認証ポリシーのベストプラクティス

### 推奨

1. **デフォルトポリシーを必ず用意**
   ```json
   {
     "description": "default - fallback policy",
     "priority": 1,
     "conditions": {},
     ...
   }
   ```
   - デフォルトは最低優先度（priority: 1）に設定
   - 条件付きポリシーより後にマッチさせる

2. **priorityは余裕を持って設定**
   ```json
   priority: 100, 50, 1
   ```
   - 高い値ほど優先される
   - 間隔を空けておくと後から挿入しやすい

3. **descriptionを分かりやすく**
   ```json
   // 良い
   "description": "admin app - password + fido2 mfa"

   // 悪い
   "description": "policy1"
   ```

---

### 避けるべき設定

1. **デフォルトポリシーなし**
   ```json
   "policies": [
     {
       "priority": 1,
       "conditions": { "client_ids": ["specific-app"] },
       ...
     }
   ]
   // specific-app以外がエラーになる
   ```

2. **available_methodsとsuccess_conditionsの不一致**
   ```json
   // 間違い
   "available_methods": ["password"],  // UIにはパスワードのみ表示
   "success_conditions": {
     "any_of": [
       [
         { "path": "$.password-authentication.success_count", ... },
         { "path": "$.sms-authentication.success_count", ... }  // SMSも必要？
       ]
     ]
   }
   // → UIにSMS認証が表示されず、ユーザーが認証を完了できない
   ```

   **注意**: `available_methods`はUIヒントです。`success_conditions`で必要な認証方式は必ず`available_methods`にも含めてください。

3. **AND条件とOR条件の混同**
   ```json
   // MFAのつもりがMFAになっていない
   "available_methods": ["password", "sms"],
   "success_conditions": {
     "any_of": [
       [{ "path": "$.password-authentication.success_count", ... }],
       [{ "path": "$.sms-authentication.success_count", ... }]
     ]
   }
   // → パスワードのみで認証完了（MFAではない）
   // ※ 外側の配列はOR条件なので、どちらか1つでOKになる
   ```

---

## 設定の確認方法

### ポリシー一覧取得

```bash
curl "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/${TENANT_ID}/authentication-policies" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  | jq '.'
```

### 特定のポリシー取得

```bash
curl "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/${TENANT_ID}/authentication-policies/oauth" \
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
curl -X PUT "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/${TENANT_ID}/authentication-policies/oauth" \
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
        "available_methods": ["password", "sms", "fido2"],
        "success_conditions": {
          "any_of": [
            [{ "path": "$.password-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }],
            [{ "path": "$.sms-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }],
            [{ "path": "$.fido2-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }]
          ]
        }
      }
    ]
  }'
```

**注意**: 全ての`policies`配列を含める必要があります（部分更新不可）

---

### ポリシーの無効化

```bash
curl -X PUT "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/${TENANT_ID}/authentication-policies/oauth" \
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

基本的な認証ポリシーを設定できました！

### より高度な設定
- [How-to: 認証ポリシー詳細](./how-to-10-authentication-policy-advanced.md) - 条件式、JSONPath、failure_conditions

### 実際の認証設定
- [How-to: パスワード認証](./how-to-05-user-registration.md)
- [How-to: MFA設定](./how-to-08-mfa-setup.md)

### 関連概念
- [Concept: 認証ポリシー](../content_03_concepts/03-authentication-authorization/concept-01-authentication-policy.md)
- [Concept: MFA](../content_03_concepts/03-authentication-authorization/concept-02-mfa.md)

---

## 関連ドキュメント

- [Developer Guide: Authentication実装](../content_06_developer-guide/03-application-plane/04-authentication.md) - 開発者向け実装ガイド
- [API Reference](../content_07_reference/api-reference.md) - Management API仕様

---

**最終更新**: 2025-12-06
**難易度**: ⭐⭐☆☆☆（初級〜中級）
**対象**: 認証ポリシーを初めて設定する管理者・開発者
**習得スキル**: success_conditions（any_of構造）、JSONPath条件、priority、ACRマッピング基礎
