# 認証ポリシー設定ガイド（詳細編）

## このドキュメントの目的

**複雑な認証ポリシー**（条件式、JSONPath、failure_conditions）を設定できるようになることが目標です。

### 所要時間
⏱️ **約30分**

### 前提条件
- [認証ポリシー基礎](../phase-1-foundation/07-authentication-policy.md)完了
- JSONPath基礎知識（このドキュメントで学べます）
- 組織ID（organization-id）を取得済み

### Management API URL

**組織レベルAPI**（このドキュメントでの表記）:
```
POST /v1/management/organizations/{organization-id}/tenants/{tenant-id}/authentication-policies
```

**注意**: システムレベルAPIとの違い
- **組織レベル**: `POST /v1/management/organizations/{organization-id}/tenants/{tenant-id}/...` ← このドキュメント
- **システムレベル**: `POST /v1/management/tenants/{tenant-id}/...` ← 管理者のみ

通常の運用では組織レベルAPIを使用してください。

---

## 認証ポリシー詳細設定の全体像

```
認証ポリシー（policies配列内の各ポリシー）
  ├─ priority: 優先度（高い値ほど優先）
  ├─ conditions: ポリシー適用条件（スコープ、クライアントID、ACR値）
  ├─ available_methods: どの認証方式を許可するか
  ├─ success_conditions: 成功条件（どの認証が成功すれば完了か）
  ├─ failure_conditions: 失敗条件（何回失敗したら認証失敗か）
  ├─ lock_conditions: ロック条件（何回失敗したらアカウントロックか）
  ├─ step_definitions: マルチステップ認証フロー制御（1st/2nd factor）
  └─ acr_mapping_rules: 認証方式 → ACRレベルのマッピング
```

**このドキュメントで学べること**:
- Level 1: JSONPath条件式の基礎
- Level 2: success_conditions（成功条件）の詳細
- Level 3: failure_conditions と lock_conditions（失敗・ロック条件）
- Level 4: step_definitions（マルチステップ認証フロー制御）
- Level 5: ACRマッピングルール（詳細）
- Level 6: 条件によるポリシー分岐（クライアント別・スコープ別）

---

## Level 1: JSONPath条件式の基礎

### JSONPath とは

**認証状態をJSON形式で表現し、その値をチェックする記法**です。

### 認証状態の例

```json
{
  "password-authentication": {
    "success_count": 0,
    "failure_count": 2,
    "last_attempt_at": "2025-10-13T10:00:00Z"
  },
  "sms-authentication": {
    "success_count": 1,
    "failure_count": 0
  }
}
```

### JSONPath構文

| JSONPath | 意味 | 例の値 |
|----------|------|-------|
| `$.password-authentication.success_count` | password認証の成功回数 | `0` |
| `$.password-authentication.failure_count` | password認証の失敗回数 | `2` |
| `$.sms-authentication.success_count` | SMS認証の成功回数 | `1` |

---

### 条件式の構造

```json
{
  "path": "$.password-authentication.success_count",  // 何をチェックするか
  "type": "integer",                                  // 値の型
  "operation": "gte",                                 // 比較演算子（>=）
  "value": 1                                          // 期待値
}
```

**意味**: `password-authentication.success_count >= 1`

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

## Level 2: success_conditions（成功条件）の詳細

### パターン1: シンプルな条件

```json
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
```

**意味**: パスワード認証が1回以上成功

**フロー**:
```
password成功 → password-authentication.success_count = 1
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
    ]
  ]
}
```

**意味**: パスワード成功 **OR** SMS成功

**フロー**:
```
Case 1: password成功
  → password-authentication.success_count = 1
  → 条件満たす → 認証完了

Case 2: sms成功
  → sms-authentication.success_count = 1
  → 条件満たす → 認証完了
```

---

### パターン3: AND条件（配列内に複数条件）

```json
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
```

**意味**: パスワード成功 **AND** SMS成功（MFA）

**フロー**:
```
password成功 → password-authentication.success_count = 1
sms成功 → sms-authentication.success_count = 1
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
        "path": "$.fido2-authentication.success_count",
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
        "path": "$.sms-authentication.success_count",
        "type": "integer",
        "operation": "gte",
        "value": 1
      }
    ]
  ]
}
```

**意味**: FIDO2成功 **OR** （パスワード成功 **AND** SMS成功）

**フロー**:
```
Case 1: FIDO2のみで認証完了
Case 2: パスワード + SMS OTPで認証完了
```

---

## Level 3: failure_conditions と lock_conditions

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
      },
      "failure_conditions": {
        "any_of": [
          [
            {
              "path": "$.password-authentication.failure_count",
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

---

### アカウントロック条件（lock_conditions）

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
      },
      "failure_conditions": {
        "any_of": [
          [
            {
              "path": "$.password-authentication.failure_count",
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
              "path": "$.password-authentication.failure_count",
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

## Level 4: step_definitions（マルチステップ認証フロー制御）

### step_definitions とは

**マルチステップ認証フローの詳細な動作を定義する機能**です。Keycloakの認証フローパターンに準拠しています。

### 主要な設定項目

```json
{
  "step_definitions": [
    {
      "method": "email",              // 認証方式
      "order": 1,                     // 実行順序
      "requires_user": false,         // ユーザー識別済みである必要があるか（1st/2nd factor）
      "allow_registration": true,     // リクエスト値での新規登録を許可するか
      "user_identity_source": "email" // ユーザー識別に使用するフィールド
    }
  ]
}
```

### 主要パラメータの説明

| パラメータ | 説明 |
|-----------|------|
| `method` | 認証方式（`email`, `sms`, `password`, `fido2` 等） |
| `order` | 実行順序（同じorder値は選択可能） |
| `requires_user` | ユーザー識別済みである必要があるか（`false`=1st factor, `true`=2nd factor） |
| `allow_registration` | **リクエスト値での新規登録を許可するか**<br />- `true`: リクエストのメール/電話番号で新規ユーザー作成可能<br />- `false`: 既存ユーザーのメール/電話番号と一致する必要 |
| `user_identity_source` | ユーザー識別に使用するフィールド（`email`, `phone_number`, `username` 等） |

### 1st Factor vs 2nd Factor

| 項目 | 1st Factor | 2nd Factor |
|------|-----------|-----------|
| `requires_user` | `false` | `true` |
| 役割 | ユーザー識別フェーズ | 認証検証フェーズ |
| 新規ユーザー | リクエスト値で登録可能<br />（`allow_registration=true`） | 登録不可<br />（既存ユーザーのみ） |
| 識別方法 | `user_identity_source` で指定 | 既に識別済みのユーザーを検証 |
| 例 | Email/SMS challenge, Password, 新規登録 | OTP検証, FIDO認証 |

**Keycloakの `requiresUser()` パターンを踏襲**:
- [Keycloak Authenticator.requiresUser()](https://www.keycloak.org/docs-api/latest/javadocs/org/keycloak/authentication/Authenticator.html#requiresUser())

---

### 例1: Email（1st） → SMS（2nd）の2要素認証

```json
{
  "policies": [
    {
      "description": "email_then_sms_2fa",
      "priority": 10,
      "conditions": { "scopes": ["openid"] },
      "available_methods": ["email", "sms"],
      "step_definitions": [
        {
          "method": "email",
          "order": 1,
          "requires_user": false,
          "allow_registration": true,
          "user_identity_source": "email"
        },
        {
          "method": "sms",
          "order": 2,
          "requires_user": true,
          "allow_registration": false,
          "user_identity_source": "phone_number"
        }
      ],
      "success_conditions": {
        "any_of": [
          [
            { "path": "$.email-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 },
            { "path": "$.sms-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }
          ]
        ]
      }
    }
  ]
}
```

**フロー**:
```
1. Email認証チャレンジ（1st factor）
   - requires_user=false → ユーザー未識別でOK
   - allow_registration=true → 新規ユーザー作成可能
   - user_identity_source=email → メールアドレスでユーザー識別
   ↓
2. Email OTP検証
   - ユーザーがトランザクションに紐付けられる
   ↓
3. SMS認証チャレンジ（2nd factor）
   - requires_user=true → ユーザー識別済みである必要
   - allow_registration=false → 新規ユーザー作成不可
   - user_identity_source=phone_number → 電話番号でユーザー検証
   ↓
4. SMS OTP検証
   ↓
5. 認証完了（両方の success_count >= 1）
```

---

### 例2: SSO（1st） → Email/SMS（2nd）の選択式2FA

```json
{
  "step_definitions": [
    {
      "method": "sso",
      "order": 1,
      "requires_user": false,
      "allow_registration": true,
      "user_identity_source": "sso"
    },
    {
      "method": "email",
      "order": 2,
      "requires_user": true,
      "allow_registration": false,
      "user_identity_source": "email"
    },
    {
      "method": "sms",
      "order": 2,
      "requires_user": true,
      "allow_registration": false,
      "user_identity_source": "phone_number"
    }
  ]
}
```

**フロー**:
```
1. SSO認証（1st factor, order=1）
   ↓
2. Email認証 OR SMS認証（2nd factor, order=2）
   - 同じorderは選択可能（どちらか1つでOK）
```

---

### user_identity_source の種類

| 値 | 意味 | 使用例 |
|---|------|-------|
| `email` | メールアドレスでユーザー識別 | Email認証 |
| `phone_number` | 電話番号でユーザー識別 | SMS認証 |
| `username` | ユーザー名でユーザー識別 | パスワード認証 |
| `webauthn_credential` | WebAuthn資格情報でユーザー識別 | FIDO2認証 |
| `sso` | SSO（外部IdP）でユーザー識別 | OIDC連携 |

---

### step_definitions を使うべきケース

✅ **使うべき**:
- マルチステップ認証フロー（Email → SMS等）を定義したい
- 1st/2nd factorの制御が必要
- 新規ユーザー登録を特定ステップでのみ許可したい
- 認証順序を明示的に制御したい

❌ **使わなくてよい**:
- シンプルなMFA（success_conditionsだけで十分）
- 認証順序の制御が不要
- すべてのステップで登録を許可する場合

**重要**: `step_definitions` は高度な機能です。通常のMFA設定では `success_conditions` のみで十分です。

---

## 条件式の詳細構文

### any_of の構造

認証ポリシーでは **`any_of` のみサポート**されています（`all_of` は存在しません）。

```json
"any_of": [
  [ 条件A ],  // ← この配列内は AND
  [ 条件B ],  // ← この配列内は AND
  [ 条件C ]   // ← この配列内は AND
]
```

**意味**: 条件A **OR** 条件B **OR** 条件C

**重要**:
- 外側の配列 = **OR条件**（いずれか1つ満たせばOK）
- 内側の配列 = **AND条件**（すべて満たす必要あり）
- この組み合わせで複雑な論理式を表現可能

---

### なぜ `all_of` が不要なのか

**`any_of` の内側配列がAND条件として機能するため、`all_of` は不要です。**

#### ❌ もし `all_of` があった場合（他システムの例）
```json
"all_of": [
  [ { "path": "$.password-authentication.success_count", "operation": "gte", "value": 1 } ],
  [ { "path": "$.sms-authentication.success_count", "operation": "gte", "value": 1 } ]
]
```
→ すべてのグループを満たす必要（AND）

#### ✅ `any_of` で同じことを実現
```json
"any_of": [
  [
    { "path": "$.password-authentication.success_count", "operation": "gte", "value": 1 },
    { "path": "$.sms-authentication.success_count", "operation": "gte", "value": 1 }
  ]
]
```
→ 内側配列の複数条件 = すべて満たす必要（AND）

**結論**: `any_of` の内側配列でAND条件を表現できるため、`all_of` は冗長です。

#### 複雑な例: (A AND B) OR (C AND D)

```json
"any_of": [
  [
    { "path": "$.password-authentication.success_count", "operation": "gte", "value": 1 },
    { "path": "$.sms-authentication.success_count", "operation": "gte", "value": 1 }
  ],
  [
    { "path": "$.email-authentication.success_count", "operation": "gte", "value": 1 },
    { "path": "$.fido2-authentication.success_count", "operation": "gte", "value": 1 }
  ]
]
```
→ (password AND sms) **OR** (email AND fido2)

この設計により、シンプルかつ柔軟な条件定義が可能になっています。

**重要**: パスの命名規則
- **基本形式**: `$.{method}-authentication.{property}`
  - ✅ `$.password-authentication.success_count`
  - ✅ `$.sms-authentication.success_count`
  - ✅ `$.email-authentication.success_count`
  - ✅ `$.fido2-authentication.success_count`
  - ✅ `$.fido-uaf-authentication.success_count`
  - ❌ `$.password.success_count` （間違い）

- **例外**: `external-token` と OIDC連携は `-authentication` サフィックスなし
  - ✅ `$.external-token.success_count`
  - ✅ `$.oidc-google.success_count`
  - ✅ `$.oidc-facebook.success_count`

---

## 実践例

### 例1: パスワード成功 OR SMS成功

```json
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
    ]
  ]
}
```

---

### 例2: （パスワード成功 AND SMS成功）OR FIDO2成功

```json
"success_conditions": {
  "any_of": [
    [
      {
        "path": "$.fido2-authentication.success_count",
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
        "path": "$.sms-authentication.success_count",
        "type": "integer",
        "operation": "gte",
        "value": 1
      }
    ]
  ]
}
```

**意味**:
- FIDO2だけで認証完了（高セキュリティ）
- または、パスワード + SMS OTPで認証完了（標準MFA）

---

### 例3: 失敗回数制限（failure_conditions）

```json
"failure_conditions": {
  "any_of": [
    [
      {
        "path": "$.password-authentication.failure_count",
        "type": "integer",
        "operation": "gte",
        "value": 3
      }
    ],
    [
      {
        "path": "$.sms-authentication.failure_count",
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
        "path": "$.password-authentication.failure_count",
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

## Level 5: ACRマッピングルール（詳細）

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
  "urn:mace:incommon:iap:gold": ["fido2", "fido-uaf"],
  "urn:mace:incommon:iap:silver": ["sms", "email", "totp"],
  "urn:mace:incommon:iap:bronze": ["password"]
}
```

**動作**:
```
FIDO2認証成功
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

**効果**: gold レベルの認証方式（FIDO2等）のみ許可される

---

## Level 6: 条件（conditions）によるポリシー分岐

### クライアント別ポリシー

**重要**: priorityは**値が大きいほど優先度が高い**です（100 > 50 > 1）。

```json
{
  "flow": "oauth",
  "enabled": true,
  "policies": [
    {
      "description": "admin app - high security",
      "priority": 100,
      "conditions": {
        "client_ids": ["admin-app", "super-admin-app"]
      },
      "available_methods": ["password", "initial-registration", "fido2"],
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
              "path": "$.fido2-authentication.success_count",
              "type": "integer",
              "operation": "gte",
              "value": 1
            }
          ],
          [
            {
              "path": "$.initial-registration.success_count",
              "type": "integer",
              "operation": "gte",
              "value": 1
            },
            {
              "path": "$.fido2-authentication.success_count",
              "type": "integer",
              "operation": "gte",
              "value": 1
            }
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
      "available_methods": ["password", "initial-registration", "sms"],
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
              "path": "$.initial-registration.success_count",
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
    },
    {
      "description": "default - password only",
      "priority": 1,
      "conditions": {},
      "available_methods": ["password", "initial-registration"],
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
              "path": "$.initial-registration.success_count",
              "type": "integer",
              "operation": "gte",
              "value": 1
            }
          ]
        ]
      }
    }
  ]
}
```

**動作**:
```
admin-app からのリクエスト
  → priority 100 にマッチ（client_ids条件、最高優先度）
  → ログイン: パスワード + FIDO2必須
  → 登録: 新規登録 + FIDO2必須

user-app からのリクエスト
  → priority 50 にマッチ
  → ログイン: パスワード + SMS OTP必須
  → 登録: 新規登録 + SMS OTP必須

other-app からのリクエスト
  → priority 1 にマッチ（デフォルト、最低優先度）
  → ログイン: パスワードのみ
  → 登録: 新規登録のみ
```

---

### スコープ別ポリシー

```json
{
  "description": "sensitive scope requires high auth",
  "priority": 100,
  "conditions": {
    "scopes": ["admin", "delete", "update_sensitive"]
  },
  "available_methods": ["password", "initial-registration", "fido2"],
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
          "path": "$.fido2-authentication.success_count",
          "type": "integer",
          "operation": "gte",
          "value": 1
        }
      ],
      [
        {
          "path": "$.initial-registration.success_count",
          "type": "integer",
          "operation": "gte",
          "value": 1
        },
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

**動作**:
```
scope=admin を要求
  → priority 100 にマッチ（最高優先度）
  → ログイン: パスワード + FIDO2必須（高セキュリティ）
  → 登録: 新規登録 + FIDO2必須

scope=read を要求
  → マッチせず、デフォルトポリシー（低いpriority）
  → パスワードのみ または 新規登録のみ
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
"path": "password-authentication.success_count"  // $ がない

// ✅ 正しい
"path": "$.password-authentication.success_count"
```

---

### エラー2: any_ofの構造ミス

**エラー**:
```json
{
  "error": "invalid_policy",
  "error_description": "success_conditions must have 'any_of'"
}
```

**原因**: `any_of`の二重配列を忘れている、または `any_of` フィールド自体がない

**例**:
```json
// ❌ 間違い: 単一配列
"any_of": [
  {
    "path": "$.password-authentication.success_count",
    ...
  }
]

// ✅ 正しい: 二重配列
"any_of": [
  [
    {
      "path": "$.password-authentication.success_count",
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
    { "path": "$.password-authentication.failure_count", "operation": "gte", "value": 5 }
  ]]
},
"lock_conditions": {
  "any_of": [[
    { "path": "$.password-authentication.failure_count", "operation": "gte", "value": 3 }
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
- [How-to: CIBA認証ポリシー](../phase-3-advanced/fido-uaf/01-ciba-flow.md)
- [How-to: FIDO UAF登録ポリシー](../phase-3-advanced/fido-uaf/02-registration.md)

---

## 関連ドキュメント

- [Concept: 認証ポリシー](../content_03_concepts/03-authentication-authorization/concept-01-authentication-policy.md) - 認証ポリシー概念
- [Developer Guide: Authentication実装](../content_06_developer-guide/03-application-plane/04-authentication.md) - 実装詳細
- [Implementation Guide: Authentication Policy](../content_06_developer-guide/04-implementation-guides/impl-05-authentication-policy.md) - 内部実装

---

**最終更新**: 2025-01-19
**難易度**: ⭐⭐⭐⭐☆（中級〜上級）
**対象**: 複雑な認証ポリシーを設定する管理者・開発者
**習得スキル**: JSONPath条件式、any_of条件式、failure_conditions、lock_conditions、step_definitions、ACRマッピング詳細
