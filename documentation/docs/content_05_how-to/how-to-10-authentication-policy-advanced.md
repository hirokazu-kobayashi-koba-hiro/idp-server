# 認証ポリシー設定ガイド（詳細編）

## このドキュメントの目的

**複雑な認証ポリシー**（条件式、JSONPath、failure_conditions）を設定できるようになることが目標です。

### 所要時間
⏱️ **約30分**

### 前提条件
- [認証ポリシー基礎](./how-to-07-authentication-policy-basic.md)完了
- JSONPath基礎知識（このドキュメントで学べます）
- 組織ID（organization-id）を取得済み

### Management API URL

**組織レベルAPI**（このドキュメントでの表記）:
```
POST /v1/management/organizations/{organization-id}/tenants/{tenant-id}/authentication-policies
PUT  /v1/management/organizations/{organization-id}/tenants/{tenant-id}/authentication-policies/{policy-id}
```

**注意**: システムレベルAPIとの違い
- **組織レベル**: `POST /v1/management/organizations/{organization-id}/tenants/{tenant-id}/...` ← このドキュメント
- **システムレベル**: `POST /v1/management/tenants/{tenant-id}/...` ← 管理者のみ

通常の運用では組織レベルAPIを使用してください。

---

## 認証ポリシー詳細設定の全体像

```
認証ポリシー
  ├─ available_methods: どの認証方式を許可するか
  ├─ success_conditions: 成功条件（どの認証が成功すれば完了か）
  ├─ failure_conditions: 失敗条件（何回失敗したら認証失敗か）
  ├─ lock_conditions: ロック条件（何回失敗したらアカウントロックか）
  └─ acr_mapping_rules: 認証方式 → ACRレベルのマッピング
```

---

## Level 1: JSONPath条件式の基礎（10分）

### JSONPath とは

**認証状態をJSON形式で表現し、その値をチェックする記法**です。

### 認証状態の例

```json
{
  "password": {
    "success_count": 0,
    "failure_count": 2,
    "last_attempt_at": "2025-10-13T10:00:00Z"
  },
  "sms": {
    "success_count": 1,
    "failure_count": 0
  }
}
```

### JSONPath構文

| JSONPath | 意味 | 例の値 |
|----------|------|-------|
| `$.password.success_count` | password認証の成功回数 | `0` |
| `$.password.failure_count` | password認証の失敗回数 | `2` |
| `$.sms.success_count` | SMS認証の成功回数 | `1` |

---

### 条件式の構造

```json
{
  "path": "$.password.success_count",  // 何をチェックするか
  "type": "integer",                   // 値の型
  "operation": "gte",                  // 比較演算子（>=）
  "value": 1                           // 期待値
}
```

**意味**: `password.success_count >= 1`

---

### 演算子一覧

| operation | 意味 | 例 |
|-----------|------|---|
| `eq` | `=` 等しい | `"value": 1` → 1と等しい |
| `ne` | `!=` 等しくない | `"value": 0` → 0でない |
| `gt` | `>` より大きい | `"value": 0` → 0より大きい |
| `gte` | `>=` 以上 | `"value": 1` → 1以上 |
| `lt` | `<` 未満 | `"value": 5` → 5未満 |
| `lte` | `<=` 以下 | `"value": 3` → 3以下 |

---

## Level 2: success_conditionsの詳細（10分）

### パターン1: シンプルな条件

```json
"success_conditions": {
  "any_of": [
    [
      {
        "path": "$.password.success_count",
        "type": "integer",
        "operation": "gte",
        "value": 1
      }
    ]
  ]
}
```

**意味**: パスワード認証が1回以上成功

**フロー**:
```
password成功 → password.success_count = 1
  → 1 >= 1 → 条件満たす
  → 認証完了
```

---

### パターン2: OR条件（any_of）

```json
"success_conditions": {
  "any_of": [
    [
      {
        "path": "$.password.success_count",
        "type": "integer",
        "operation": "gte",
        "value": 1
      }
    ],
    [
      {
        "path": "$.sms.success_count",
        "type": "integer",
        "operation": "gte",
        "value": 1
      }
    ]
  ]
}
```

**意味**: パスワード成功 **OR** SMS成功

**フロー**:
```
Case 1: password成功
  → password.success_count = 1
  → 条件満たす → 認証完了

Case 2: sms成功
  → sms.success_count = 1
  → 条件満たす → 認証完了
```

---

### パターン3: AND条件（配列内に複数条件）

```json
"success_conditions": {
  "any_of": [
    [
      {
        "path": "$.password.success_count",
        "type": "integer",
        "operation": "gte",
        "value": 1
      },
      {
        "path": "$.sms.success_count",
        "type": "integer",
        "operation": "gte",
        "value": 1
      }
    ]
  ]
}
```

**意味**: パスワード成功 **AND** SMS成功（MFA）

**フロー**:
```
password成功 → password.success_count = 1
sms成功 → sms.success_count = 1
  → 両方 >= 1 → 条件満たす
  → 認証完了
```

---

### パターン4: 複雑な条件（AND + OR）

```json
"success_conditions": {
  "any_of": [
    [
      {
        "path": "$.webauthn.success_count",
        "type": "integer",
        "operation": "gte",
        "value": 1
      }
    ],
    [
      {
        "path": "$.password.success_count",
        "type": "integer",
        "operation": "gte",
        "value": 1
      },
      {
        "path": "$.sms.success_count",
        "type": "integer",
        "operation": "gte",
        "value": 1
      }
    ]
  ]
}
```

**意味**: WebAuthn成功 **OR** （パスワード成功 **AND** SMS成功）

**フロー**:
```
Case 1: WebAuthnのみで認証完了
Case 2: パスワード + SMS OTPで認証完了
```

---

## Level 3: failure_conditions（失敗条件）

### 失敗回数制限

```json
{
  "flow": "oauth",
  "enabled": true,
  "policies": [
    {
      "description": "with failure limit",
      "priority": 1,
      "conditions": {},
      "available_methods": ["password"],
      "success_conditions": {
        "type": "all",
        "authentication_methods": ["password"]
      },
      "failure_conditions": {
        "any_of": [
          [
            {
              "path": "$.password.failure_count",
              "type": "integer",
              "operation": "gte",
              "value": 5
            }
          ]
        ]
      }
    }
  ]
}
```

**意味**: パスワード認証が5回失敗したら認証失敗（エラー返却）

**フロー**:
```
1回目失敗 → failure_count = 1 → 継続可能
2回目失敗 → failure_count = 2 → 継続可能
...
5回目失敗 → failure_count = 5
  → 5 >= 5 → failure_conditions満たす
  → 認証失敗エラー
```

**レスポンス**:
```json
{
  "error": "authentication_failed",
  "error_description": "Maximum authentication attempts exceeded",
  "remaining_attempts": 0
}
```

---

## Level 4: lock_conditions（アカウントロック）

### アカウントロック条件

```json
{
  "flow": "oauth",
  "enabled": true,
  "policies": [
    {
      "description": "with account lock",
      "priority": 1,
      "conditions": {},
      "available_methods": ["password"],
      "success_conditions": {
        "type": "all",
        "authentication_methods": ["password"]
      },
      "failure_conditions": {
        "any_of": [
          [
            {
              "path": "$.password.failure_count",
              "type": "integer",
              "operation": "gte",
              "value": 3
            }
          ]
        ]
      },
      "lock_conditions": {
        "any_of": [
          [
            {
              "path": "$.password.failure_count",
              "type": "integer",
              "operation": "gte",
              "value": 5
            }
          ]
        ]
      }
    }
  ]
}
```

**意味**:
- 3回失敗 → 認証失敗エラー（まだロックされていない）
- 5回失敗 → **アカウントロック**（ユーザーステータスがLOCKEDに変更）

**フロー**:
```
1-2回失敗 → 継続可能
3回目失敗 → failure_conditions満たす → 認証失敗エラー
  ↓ ユーザーが再試行
4回目失敗 → 認証失敗エラー
5回目失敗 → lock_conditions満たす
  → アカウントロック
  → UserLifecycleEvent発行（LOCK）
  → 管理者による解除が必要
```

**ロック解除**:
```bash
curl -X PUT "http://localhost:8080/v1/management/organizations/${ORGANIZATION_ID}/tenants/${TENANT_ID}/users/${USER_ID}" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d '{
    "status": "ACTIVE"
  }'
```

---

## 実例: エンタープライズ向け高度な認証ポリシー

### 複数Federation + External Token認証の設定例

**シナリオ**: 外部IdP連携とExternal Token認証を組み合わせた柔軟な認証

```json
{
  "flow": "oauth",
  "enabled": true,
  "policies": [
    {
      "description": "Enterprise Authentication Policy",
      "priority": 1,
      "conditions": {},
      "available_methods": [
        "oidc-external-idp",
        "external-token",
        "fido-uaf"
      ],
      "acr_mapping_rules": {
        "urn:mace:incommon:iap:gold": ["fido-uaf", "webauthn"],
        "urn:mace:incommon:iap:silver": ["oidc-external-idp"],
        "urn:mace:incommon:iap:bronze": ["external-token"]
      },
      "success_conditions": {
        "any_of": [
          [
            {
              "path": "$.oidc-external-idp.success_count",
              "type": "integer",
              "operation": "gte",
              "value": 1
            }
          ],
          [
            {
              "path": "$.external-token.success_count",
              "type": "integer",
              "operation": "gte",
              "value": 1
            }
          ],
          [
            {
              "path": "$.fido-uaf.success_count",
              "type": "integer",
              "operation": "gte",
              "value": 1
            }
          ]
        ]
      },
      "failure_conditions": {
        "any_of": [
          [
            {
              "path": "$.oidc-external-idp.failure_count",
              "type": "integer",
              "operation": "gte",
              "value": 5
            }
          ],
          [
            {
              "path": "$.external-token.failure_count",
              "type": "integer",
              "operation": "gte",
              "value": 5
            }
          ]
        ]
      },
      "lock_conditions": {
        "any_of": []
      }
    }
  ]
}
```

**解説**:

1. **available_methods**:
   - 外部IdP（OIDC）連携
   - External Token認証
   - FIDO-UAF生体認証

2. **success_conditions**:
   - 外部IdP成功 **OR** External Token成功 **OR** FIDO-UAF成功
   - いずれか1つで認証完了

3. **failure_conditions**:
   - 外部IdP 5回失敗 **OR** External Token 5回失敗
   - → 認証失敗エラー

4. **lock_conditions**:
   - `any_of: []` → ロック条件なし
   - アカウントはロックされない

5. **ACRマッピング**:
   - FIDO UAF/WebAuthn → gold（高）
   - Email/SMS → silver（中）
   - Password/External Token → bronze（低）

**初学者へのアドバイス**:
- まずは基礎編（how-to-14）のシンプルなポリシーから始める
- 必要に応じて段階的に複雑化

---

## 条件式の詳細構文

### any_of と all_of

| 構文 | 意味 | 使用例 |
|------|------|-------|
| `any_of` | いずれか1つ満たせばOK（OR） | 複数認証方式の選択肢 |
| `all_of` | 全て満たす必要あり（AND） | 複数条件の同時チェック |

### any_of の構造

```json
"any_of": [
  [ 条件A ],  // ← この配列内は AND
  [ 条件B ],  // ← この配列内は AND
  [ 条件C ]   // ← この配列内は AND
]
```

**意味**: 条件A **OR** 条件B **OR** 条件C

### all_of の構造

```json
"all_of": [
  [ 条件A ],
  [ 条件B ],
  [ 条件C ]
]
```

**意味**: 条件A **AND** 条件B **AND** 条件C

---

## 実践例

### 例1: パスワード成功 OR SMS成功

```json
"success_conditions": {
  "any_of": [
    [
      {
        "path": "$.password.success_count",
        "type": "integer",
        "operation": "gte",
        "value": 1
      }
    ],
    [
      {
        "path": "$.sms.success_count",
        "type": "integer",
        "operation": "gte",
        "value": 1
      }
    ]
  ]
}
```

---

### 例2: （パスワード成功 AND SMS成功）OR WebAuthn成功

```json
"success_conditions": {
  "any_of": [
    [
      {
        "path": "$.webauthn.success_count",
        "type": "integer",
        "operation": "gte",
        "value": 1
      }
    ],
    [
      {
        "path": "$.password.success_count",
        "type": "integer",
        "operation": "gte",
        "value": 1
      },
      {
        "path": "$.sms.success_count",
        "type": "integer",
        "operation": "gte",
        "value": 1
      }
    ]
  ]
}
```

**意味**:
- WebAuthnだけで認証完了（高セキュリティ）
- または、パスワード + SMS OTPで認証完了（標準MFA）

---

### 例3: 失敗回数制限（failure_conditions）

```json
"failure_conditions": {
  "any_of": [
    [
      {
        "path": "$.password.failure_count",
        "type": "integer",
        "operation": "gte",
        "value": 3
      }
    ],
    [
      {
        "path": "$.sms.failure_count",
        "type": "integer",
        "operation": "gte",
        "value": 5
      }
    ]
  ]
}
```

**意味**: パスワード3回失敗 **OR** SMS 5回失敗 → 認証失敗

---

### 例4: アカウントロック（lock_conditions）

```json
"lock_conditions": {
  "any_of": [
    [
      {
        "path": "$.password.failure_count",
        "type": "integer",
        "operation": "gte",
        "value": 5
      }
    ]
  ]
}
```

**意味**: パスワード5回失敗 → アカウントロック

---

## Level 3: ACRマッピングルール（詳細）

### ACR値の定義

**ACR（Authentication Context Class Reference）** = 認証の強度レベル

| ACR値 | レベル | 認証方式の例 | 用途 |
|-------|-------|-----------|------|
| `urn:mace:incommon:iap:bronze` | 低 | パスワードのみ | 通常操作 |
| `urn:mace:incommon:iap:silver` | 中 | パスワード + SMS OTP | 重要操作 |
| `urn:mace:incommon:iap:gold` | 高 | FIDO2生体認証 | 非常に重要な操作 |

**カスタムACR値も定義可能**:
```json
"urn:example:acr:level1"
"urn:example:acr:level2"
"urn:example:acr:level3"
```

---

### ACRマッピングの設定

```json
"acr_mapping_rules": {
  "urn:mace:incommon:iap:gold": ["webauthn", "fido-uaf"],
  "urn:mace:incommon:iap:silver": ["sms", "email", "totp"],
  "urn:mace:incommon:iap:bronze": ["password"]
}
```

**動作**:
```
WebAuthn認証成功
  → ACR: urn:mace:incommon:iap:gold
  → ID Token.acr = "urn:mace:incommon:iap:gold"

SMS OTP認証成功
  → ACR: urn:mace:incommon:iap:silver
  → ID Token.acr = "urn:mace:incommon:iap:silver"

パスワード認証成功
  → ACR: urn:mace:incommon:iap:bronze
  → ID Token.acr = "urn:mace:incommon:iap:bronze"
```

---

### ACRを使ったアクセス制御

クライアント側でACR値を確認して操作を制限：

```javascript
// クライアント側のコード
const idToken = parseJwt(response.id_token);

if (idToken.acr === "urn:mace:incommon:iap:gold") {
  // 高レベル認証 → すべての操作を許可
  allowSensitiveOperation();
} else if (idToken.acr === "urn:mace:incommon:iap:silver") {
  // 中レベル認証 → 通常操作のみ
  allowNormalOperation();
} else {
  // 低レベル認証 → 読み取りのみ
  allowReadOnlyOperation();
}
```

**または、Authorization Requestで要求**:
```bash
curl "http://localhost:8080/${TENANT_ID}/v1/authorizations?\
response_type=code&\
client_id=${CLIENT_ID}&\
acr_values=urn:mace:incommon:iap:gold"  # gold レベル必須
```

**効果**: gold レベルの認証方式（WebAuthn等）のみ許可される

---

## Level 4: 条件（conditions）によるポリシー分岐

### クライアント別ポリシー

```json
{
  "flow": "oauth",
  "enabled": true,
  "policies": [
    {
      "description": "admin app - high security",
      "priority": 1,
      "conditions": {
        "client_ids": ["admin-app", "super-admin-app"]
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
      "available_methods": ["password", "sms"],
      "success_conditions": {
        "type": "all",
        "authentication_methods": ["password", "sms"]
      }
    },
    {
      "description": "default - password only",
      "priority": 999,
      "conditions": {},
      "available_methods": ["password"],
      "success_conditions": {
        "type": "all",
        "authentication_methods": ["password"]
      }
    }
  ]
}
```

**動作**:
```
admin-app からのリクエスト
  → priority 1 にマッチ（client_ids条件）
  → パスワード + WebAuthn必須

user-app からのリクエスト
  → priority 2 にマッチ
  → パスワード + SMS OTP必須

other-app からのリクエスト
  → priority 999 にマッチ（デフォルト）
  → パスワードのみ
```

---

### スコープ別ポリシー

```json
{
  "description": "sensitive scope requires high auth",
  "priority": 1,
  "conditions": {
    "scopes": ["admin", "delete", "update_sensitive"]
  },
  "available_methods": ["password", "webauthn"],
  "success_conditions": {
    "type": "all",
    "authentication_methods": ["password", "webauthn"]
  }
}
```

**動作**:
```
scope=admin を要求
  → priority 1 にマッチ
  → パスワード + WebAuthn必須（高セキュリティ）

scope=read を要求
  → マッチせず、デフォルトポリシー
  → パスワードのみ
```

---

## よくあるエラー

### エラー1: 条件式の構文エラー

**エラー**:
```json
{
  "error": "invalid_policy",
  "error_description": "Invalid JSONPath expression"
}
```

**原因**: JSONPath構文が間違っている

**例**:
```json
// ❌ 間違い
"path": "password.success_count"  // $ がない

// ✅ 正しい
"path": "$.password.success_count"
```

---

### エラー2: any_ofの構造ミス

**エラー**:
```json
{
  "error": "invalid_policy",
  "error_description": "success_conditions must have 'any_of' or 'all_of'"
}
```

**原因**: `any_of`の二重配列を忘れている

**例**:
```json
// ❌ 間違い: 単一配列
"any_of": [
  {
    "path": "$.password.success_count",
    ...
  }
]

// ✅ 正しい: 二重配列
"any_of": [
  [
    {
      "path": "$.password.success_count",
      ...
    }
  ]
]
```

---

### エラー3: failure_countとlock_conditionsの逆転

**エラー**: ロックされる前に認証失敗エラーになってしまう

**原因**:
```json
"failure_conditions": {
  "any_of": [[
    { "path": "$.password.failure_count", "operation": "gte", "value": 5 }
  ]]
},
"lock_conditions": {
  "any_of": [[
    { "path": "$.password.failure_count", "operation": "gte", "value": 3 }
  ]]
}
```

**問題**: 3回失敗でロック、5回失敗で認証失敗 → ロックが先に発動して5回に到達しない

**解決策**: lock_conditions の値 > failure_conditions の値
```json
"failure_conditions": { "value": 3 },  // 先に発動
"lock_conditions": { "value": 5 }      // 後に発動
```

---

## 次のステップ

✅ 複雑な認証ポリシーを設定できました！

### さらに高度な設定
- [How-to: CIBA認証ポリシー](./how-to-12-ciba-flow-fido-uaf.md)
- [How-to: FIDO UAF登録ポリシー](./how-to-13-fido-uaf-registration.md)

---

## 関連ドキュメント

- [Concept: 認証ポリシー](../content_03_concepts/concept-05-authentication-policy.md) - 認証ポリシー概念
- [Developer Guide: Authentication実装](../content_06_developer-guide/03-application-plane/04-authentication.md) - 実装詳細
- [Implementation Guide: Authentication Policy](../content_06_developer-guide/04-implementation-guides/impl-05-authentication-policy.md) - 内部実装

---

**最終更新**: 2025-10-13
**難易度**: ⭐⭐⭐⭐☆（中級〜上級）
**対象**: 複雑な認証ポリシーを設定する管理者・開発者
**習得スキル**: JSONPath条件式、any_of/all_of、failure_conditions、lock_conditions、ACRマッピング詳細
