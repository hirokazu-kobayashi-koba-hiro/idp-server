# 認証ポリシー

`AuthenticationPolicy` は、OAuth / OIDC / CIBA などでのユーザー認証フローを柔軟に制御するためのポリシーです。  
ユーザー識別、MFAの適用、成功/失敗の評価、アカウントロックの条件を一元的に管理できます。

---

## 🎯 目的

- 認証時の **セキュリティ強度とUXのバランス** を制御
- 条件付きで **多要素認証（MFA）** を適用
- CIBAなど **バックチャネル型認証フロー** でも柔軟に対応
- 成功・失敗・ロック評価の **統一的な制御**

---

## 🏗️ 構造例

```json
{
  "id": "e1bf16bb-57ab-43bd-814c-1de232db24d2",
  "flow": "oauth",
  "enabled": true,
  "policies": [
    {
      "description": "MFA required for high-value transactions",
      "priority": 1,
      "conditions": {
        "scopes": ["openid", "transfers"],
        "acr_values": ["urn:mace:incommon:iap:gold"],
        "client_ids": ["client-id-123"]
      },
      "available_methods": [
        "password",
        "email",
        "sms",
        "webauthn",
        "fido-uaf"
      ],
      "acr_mapping_rules": {
        "urn:mace:incommon:iap:gold": ["fido-uaf", "webauthn"],
        "urn:mace:incommon:iap:silver": ["email", "sms"],
        "urn:mace:incommon:iap:bronze": ["password"]
      },
      "level_of_authentication_scopes": {
        "transfers": ["fido-uaf", "webauthn"]
      },
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
              "path": "$.fido-uaf-authentication.success_count",
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
      },
      "authentication_device_rule": {
        "max_devices": 100,
        "required_identity_verification": true
      }
    }
  ]
}
```

---

## 🧩 各フィールドの説明

### トップレベルフィールド

| フィールド     | 説明                                 | 必須 |
|-----------|------------------------------------|----|
| `id`      | 認証ポリシー設定のUUID                      | ✅  |
| `flow`    | 適用フロー (`oauth`, `ciba`, `fido-uaf-registration`等) | ✅  |
| `enabled` | ポリシーの有効/無効                         | ✅  |
| `policies` | ポリシー定義の配列（優先度順に評価）                 | ✅  |

### ポリシー内フィールド

| フィールド                              | 説明                                                  | 使用例                                                                 |
|------------------------------------|----------------------------------------------------|---------------------------------------------------------------------|
| `description`                      | ポリシーの説明                                            | `"MFA required for high-value transactions"`                       |
| `priority`                         | 優先度（数値が大きいほど優先）                                  | `1`                                                                 |
| `conditions`                       | 適用条件。`scopes`, `acr_values`, `client_ids`等を指定       | `{"scopes": ["openid"], "acr_values": ["urn:mace:incommon:iap:gold"]}` |
| `available_methods`                | 利用可能な認証方式のリスト                                     | `["password", "fido-uaf", "webauthn"]`                              |
| `acr_mapping_rules`                | ACR値と認証方式のマッピング                                   | `{"urn:mace:incommon:iap:gold": ["fido-uaf", "webauthn"]}`          |
| `level_of_authentication_scopes`   | スコープ別の必須認証レベル                                     | `{"transfers": ["fido-uaf", "webauthn"]}`                           |
| `success_conditions`               | 認証成功とみなす条件（JSONPath + 演算子）                        | 下記参照                                                                |
| `failure_conditions`               | 警告や統計記録の対象となる失敗条件                                 | 下記参照                                                                |
| `lock_conditions`                  | アカウントロックや認可拒否に至る失敗条件                               | 下記参照                                                                |
| `authentication_device_rule`       | デバイス登録ルール（MFA登録フロー用）                              | `{"max_devices": 100, "required_identity_verification": true}`      |
| `step_definitions`                 | 多段階認証の定義                                           | -                                                                   |

### 条件評価フィールド構造

`success_conditions`, `failure_conditions`, `lock_conditions`は以下の構造：

```json
{
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

- `any_of`: いずれかの条件グループが満たされればtrue（OR評価）
- 各条件グループ内の条件はすべて満たす必要あり（AND評価）
- `path`: JSONPathでの評価対象パス
- `operation`: 比較演算子（`eq`, `ne`, `gt`, `lt`, `gte`, `lte`, `in`, `nin`, `contains`, `regex`等）

---

## 🔁 評価フロー例

1. `conditions` に一致するポリシーが選択される
2. `available_methods` に定義された認証方式が提示・実行される
3. 各認証ステップの結果が収集される
4. `success_conditions` を満たせば → ✅ 認証成功
5. `failure_conditions` を満たせば → ⚠️ 警告や記録
6. `lock_conditions` を満たせば → 🔒 アカウントロックまたは認可拒否

---

## ✅ 運用のヒント

- 機密操作（例：送金）では厳格な `success_conditions` を設定
- ブルートフォース攻撃対策に `lock_conditions` を活用
- 信頼済み端末からのログインには `available_methods` を最小化
