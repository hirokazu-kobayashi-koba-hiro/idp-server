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
  "authentication_policy": {
    "conditions": {
      "acr_values": [
        "urn:mfa:required"
      ],
      "scopes": [
        "read",
        "write"
      ],
      "authorization_flow": "ciba"
    },
    "available_methods": [
      "password",
      "email",
      "sms",
      "webauthn",
      "fido-uaf"
    ],
    "success_conditions": {
      "all_of": [
        {
          "type": "password",
          "success_count": 1
        },
        {
          "type": "fido-uaf-authentication",
          "success_count": 1
        }
      ]
    },
    "failure_conditions": {
      "any_of": [
        {
          "type": "password",
          "failure_count": 5
        }
      ]
    },
    "lock_conditions": {
      "any_of": [
        {
          "type": "fido-uaf-authentication",
          "failure_count": 5
        }
      ]
    }
  }
}
```

---

## 🧩 各フィールドの説明

| フィールド                | 説明                                                     |
|----------------------|--------------------------------------------------------|
| `conditions`         | 適用条件。`acr_values`, `scopes`, `authorization_flow`などを指定 |
| `available_methods`  | 利用可能な認証方式のリスト（UIや内部フローで利用）                             |
| `success_conditions` | 認証成功とみなす条件                                             |
| `failure_conditions` | 警告や統計記録の対象となる失敗条件                                      |
| `lock_conditions`    | アカウントロックや認可拒否に至る失敗条件                                   |

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
