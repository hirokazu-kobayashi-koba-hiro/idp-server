# Authentication Policy設定ガイド

## このドキュメントの目的

認証ポリシー（Authentication Policy）の設定方法を理解します。

### 所要時間
⏱️ **約25分**

---

## Authentication Policyとは

**認証ポリシー**はフロー別の認証要件を定義します。

**設定内容**:
- 利用可能な認証方式
- ACR（Authentication Context Class Reference）マッピング
- 成功条件・失敗条件・ロック条件

---

## 設定ファイル構造

### authentication-policy/oauth.json

```json
{
  "id": "f0864ea0-c4a0-470f-af92-22f995c80b3a",
  "flow": "oauth",
  "enabled": true,
  "policies": [
    {
      "description": "default",
      "priority": 1,
      "conditions": {},
      "available_methods": [
        "password",
        "email",
        "fido-uaf"
      ],
      "acr_mapping_rules": {
        "urn:mace:incommon:iap:gold": ["fido-uaf", "webauthn"],
        "urn:mace:incommon:iap:silver": ["email", "sms"],
        "urn:mace:incommon:iap:bronze": ["password"]
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
          ]
        ]
      },
      "failure_conditions": {
        "any_of": []
      },
      "lock_conditions": {
        "any_of": []
      }
    }
  ]
}
```

---

## 主要なフィールド

### ポリシー基本情報

| フィールド | 必須 | 説明 | 例 |
|-----------|------|------|---|
| `id` | ✅ | ポリシーID（UUID） | `f0864ea0-...` |
| `flow` | ✅ | 対象フロー | `oauth` / `ciba` |
| `enabled` | ✅ | 有効/無効 | `true` / `false` |
| `policies` | ✅ | ポリシーリスト | 配列 |

---

### Policyオブジェクト

| フィールド | 必須 | 説明 |
|-----------|------|------|
| `description` | ❌ | ポリシー説明 |
| `priority` | ✅ | 優先度（高い値が優先） |
| `conditions` | ❌ | 適用条件 |
| `available_methods` | ✅ | UIに表示する認証方式（UIヒント） |
| `acr_mapping_rules` | ❌ | ACRマッピング |
| `step_definitions` | ❌ | 多段階認証のステップ定義 |
| `success_conditions` | ✅ | 成功条件 |
| `failure_conditions` | ❌ | 失敗条件 |
| `lock_conditions` | ❌ | ロック条件 |

---

## Available Methods

UIに表示する認証方式を指定します。この設定は**UIヒント**として機能し、フロントエンドが認証画面で表示する認証方式を決定するために使用されます。

```json
{
  "available_methods": [
    "password",
    "email",
    "fido-uaf",
    "webauthn"
  ]
}
```

**重要**:
- この設定はバックエンドでの認証方式の制限には使用されません
- 認証の成功条件は `success_conditions` で制御してください
- Authentication Configuration で登録済みの認証方式のみ指定可能

---

## ACR Mapping Rules

ACR値と認証方式のマッピング：

```json
{
  "acr_mapping_rules": {
    "urn:mace:incommon:iap:gold": ["fido-uaf", "webauthn"],
    "urn:mace:incommon:iap:silver": ["email", "sms"],
    "urn:mace:incommon:iap:bronze": ["password"]
  }
}
```

**動作**:
- クライアントが`acr_values=urn:mace:incommon:iap:gold`を要求
  → FIDO-UAFまたはWebAuthn認証が必要

---

## Success Conditions

認証成功の条件をJSONPathで定義：

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
          "path": "$.email-authentication.success_count",
          "type": "integer",
          "operation": "gte",
          "value": 1
        },
        {
          "path": "$.fido-uaf-authentication.success_count",
          "type": "integer",
          "operation": "gte",
          "value": 1
        }
      ]
    ]
  }
}
```

**意味**:
- パスワード認証成功 OR（Email認証成功 AND FIDO-UAF認証成功）

### サポートされる演算子

| 演算子 | 説明 | 例 |
|-------|------|---|
| `gte` | 以上 | `success_count >= 1` |
| `lte` | 以下 | `failure_count <= 3` |
| `eq` | 等しい | `status == "verified"` |
| `ne` | 等しくない | `status != "locked"` |

---

## Step Definitions（多段階認証）

**目的**: 認証ステップの実行順序とユーザー識別制御を定義します。

### step_definitionsとsuccess_conditionsの違い

| 項目 | `step_definitions` | `success_conditions` |
|------|-------------------|---------------------|
| **目的** | 認証の**実行順序**と**ユーザー識別** | 認証の**成功判定** |
| **制御内容** | どの順番で認証を実行するか | どの認証が成功すれば完了か |
| **使用シーン** | Email → SMS のような順序制御 | パスワード OR 生体認証 のような条件 |

### フィールド一覧

| フィールド | 型 | 必須 | 説明 | 例 |
|-----------|-----|------|------|---|
| `method` | string | ✅ | 認証方式 | `"email"`, `"sms"`, `"password"` |
| `order` | integer | ✅ | 実行順序（小さい値が先） | `1`, `2`, `3` |
| `requires_user` | boolean | ✅ | ユーザーが事前に識別されている必要があるか | `false`（1st factor）/ `true`（2nd factor） |
| `allow_registration` | boolean | ❌ | このステップでユーザー登録を許可するか | `true` / `false` |
| `user_identity_source` | string | ❌ | ユーザー識別に使用する属性 | `"email"`, `"phone_number"`, `"username"` |

### 設定例: Email → SMS 多段階認証

```json
{
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
  ]
}
```

**動作**:
1. **ステップ1（Email認証）**:
   - `requires_user: false` → ユーザー未特定でもOK
   - `allow_registration: true` → 新規ユーザー登録可能
   - メールアドレスでユーザーを識別

2. **ステップ2（SMS認証）**:
   - `requires_user: true` → ステップ1でユーザーが特定済みである必要
   - `allow_registration: false` → 登録不可（既存ユーザーのみ）
   - 電話番号で検証

### 1st Factor と 2nd Factor

**1st Factor (`requires_user: false`)**:
- ユーザー識別フェーズ
- ユーザーが未特定でも実行可能
- 新規ユーザー登録が可能（`allow_registration: true`）

**2nd Factor (`requires_user: true`)**:
- 認証検証フェーズ
- ユーザーが既に特定されている必要あり
- ユーザーを変更できない

### 使用シーン

- **段階的なユーザー登録**: Email認証 → SMS認証で段階的に情報を収集
- **ステップアップ認証**: 通常ログイン後、重要な操作前にSMS認証を要求
- **複合認証**: 複数の認証方式を組み合わせてセキュリティを強化

---

## 2要素認証（2FA）の設定例

```json
{
  "available_methods": ["password", "email"],
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
          "path": "$.email-authentication.success_count",
          "type": "integer",
          "operation": "gte",
          "value": 1
        }
      ]
    ]
  }
}
```

**動作**: パスワードANDメールOTPの両方成功で認証完了

---

## Management APIで登録

### API エンドポイント

**組織レベルAPI**:
```
POST /v1/management/organizations/{organization-id}/tenants/{tenant-id}/authentication-policies
```

### ポリシー登録

```bash
POST /v1/management/organizations/{organization-id}/tenants/{tenant-id}/authentication-policies
Content-Type: application/json

{
  "id": "uuid",
  "flow": "oauth",
  "enabled": true,
  "policies": [
    {
      "priority": 1,
      "available_methods": ["password"],
      "success_conditions": {
        "any_of": [
          [
            {"path": "$.password-authentication.success_count", "type": "integer", "operation": "gte", "value": 1}
          ]
        ]
      }
    }
  ]
}
```

---

## よくある設定ミス

### ミス1: 未登録の認証方式を指定

**エラー**:
```json
{
  "error": "invalid_request",
  "error_description": "authentication method 'fido-uaf' not found"
}
```

**原因**: `available_methods`に指定した認証方式が未登録

**解決策**: Authentication Configurationで先に登録

### ミス2: 成功条件の論理エラー

**問題**: すべての認証方式が成功しないと認証完了にならない

**原因**:
```json
{
  "success_conditions": {
    "any_of": [
      [
        {"path": "$.password-authentication.success_count", "type": "integer", "operation": "gte", "value": 1},
        {"path": "$.email-authentication.success_count", "type": "integer", "operation": "gte", "value": 1},
        {"path": "$.fido-uaf-authentication.success_count", "type": "integer", "operation": "gte", "value": 1}
      ]
    ]
  }
}
```

**解決策**: OR条件にする
```json
{
  "success_conditions": {
    "any_of": [
      [{"path": "$.password-authentication.success_count", "type": "integer", "operation": "gte", "value": 1}],
      [{"path": "$.email-authentication.success_count", "type": "integer", "operation": "gte", "value": 1}]
    ]
  }
}
```

---

## 次のステップ

✅ Authentication Policy設定を理解した！

### 次に読むべきドキュメント

1. [FIDO-UAF設定](./authn/fido-uaf.md) - 生体認証設定
2. [Password設定](./authn/password.md) - パスワード認証設定

### 関連ドキュメント

- [Authentication Policy実装ガイド](../04-implementation-guides/impl-05-authentication-policy.md)

---

**最終更新**: 2025-12-06

---

## 📊 初学者向けドキュメント品質レビュー

**レビュー日**: 2025-01-15
**レビュー対象**: 初学者（idp-server開発経験なし、Java/Spring Boot基礎知識あり）

### ✅ 良い点

1. **2FA設定例**: 実用的な2要素認証の設定例が明確
2. **演算子の説明**: JSONPath演算子（gte/lte/eq/ne）を表で整理
3. **論理条件の説明**: AND/ORの組み合わせを具体例で説明
4. **エラー対処**: よくある論理エラーと解決策が詳細
5. **ACRマッピング**: ACR値と認証方式のマッピングが明確
6. **フィールド説明**: 表形式で全フィールドを網羅

### ⚠️ 改善推奨事項

- [ ] **Authentication Policyの概念説明**（重要度: 高）
  - 「なぜAuthentication Policyが必要か」の説明不足
  - Tenant/Client/Authentication Policyの関係性
  - Policyがどのタイミングで評価されるか

- [ ] **JSONPath基礎の説明**（重要度: 高）
  - JSONPath構文（`$.password-authentication.success_count`）の読み方
  - どのようなデータ構造を参照しているか
  - 利用可能なパスの一覧

- [ ] **any_of論理構造の図解**（重要度: 高）
  - ネストされた配列の意味が分かりにくい
  - `any_of: [[A,B],[C]]` = (A AND B) OR C の視覚化

- [ ] **最小構成の例**（重要度: 高）
  - 最もシンプルな単一認証方式の例
  - 段階的に複雑にする説明

- [ ] **実践的なシナリオ**（重要度: 中）
  - 「一般ユーザー: パスワードのみ」
  - 「管理者: パスワード + 2FA必須」
  - 「高セキュリティ: 生体認証必須」

- [ ] **priority の動作説明**（重要度: 中）
  - 複数policyがある場合の選択ロジック
  - conditionsとの組み合わせ

- [ ] **デバッグ方法**（重要度: 中）
  - 成功条件が満たされない場合の確認方法
  - 認証状態の確認方法

### 💡 追加推奨コンテンツ

1. **認証フロー全体図**:
   ```
   認証開始 → Policy選択 → 認証方式提示 →
   認証実行 → 成功条件評価 → 認証完了/継続
   ```

2. **JSONPath参照データ構造**:
   ```json
   {
     "password-authentication": {
       "success_count": 1,
       "failure_count": 0
     },
     "email-authentication": {
       "success_count": 0,
       "failure_count": 0
     }
   }
   ```

3. **条件組み合わせパターン集**:
   - 単一認証
   - 2要素認証（2FA）
   - 多要素認証（MFA）
   - ステップアップ認証

4. **失敗条件・ロック条件の例**:
   - パスワード3回失敗でロック
   - 一定時間内の再試行制限

5. **トラブルシューティング拡充**:
   - 認証が完了しない原因特定
   - 条件式のテスト方法

### 📈 総合評価

- **理解しやすさ**: ⭐⭐⭐☆☆ (3/5) - JSONPath条件が初学者には難しい
- **実用性**: ⭐⭐⭐⭐☆ (4/5) - 2FA例など実用的
- **完全性**: ⭐⭐⭐⭐☆ (4/5) - 主要な設定を網羅
- **初学者適合度**: ⭐⭐⭐☆☆ (3/5) - 前提知識が多く、概念説明が不足

### 🎯 推奨される学習パス

**このドキュメントの位置づけ**: 中級（認証方式設定後）

**推奨順序**:
1. [Tenant設定](./tenant.md) - Tenant作成
2. [Client設定](./client.md) - Client登録
3. [Password設定](./authn/password.md) - 認証方式登録
4. **このドキュメント** - Authentication Policy設定
5. [Authentication Policy実装ガイド](../04-implementation-guides/impl-05-authentication-policy.md) - 実装詳細

### 📝 具体的改善案（優先度順）

#### 1. Authentication Policyの概念説明（最優先）

```markdown
## Authentication Policyとは（詳細）

**Authentication Policy（認証ポリシー）**は、**どの認証方式を使って、どのような条件で認証を成功とするか**を定義する設定です。

### なぜ必要か

- 異なるセキュリティ要件への対応（一般ユーザー vs 管理者）
- 複数認証方式の組み合わせ（2FA, MFA）
- ACR（認証レベル）による動的な要件変更

### Policyが評価されるタイミング

\`\`\`
1. ユーザーが認証開始
2. → Tenant/Client情報取得
3. → Authentication Policy選択（flow, priority, conditions）
4. → 利用可能な認証方式を提示
5. → ユーザーが認証実行
6. → 成功条件評価
7. → ✅ 成功 or ❌ 継続/失敗
\`\`\`

### Tenant-Client-Policy-Authentication Methodの関係

\`\`\`
┌────────────────────────────────┐
│ Tenant                         │
│                                │
│  ┌──────────────┐              │
│  │ Client       │              │
│  └──────┬───────┘              │
│         │                      │
│         ▼                      │
│  ┌─────────────────────────┐  │
│  │ Authentication Policy   │  │
│  │ (OAuth Flow用)          │  │
│  ├─────────────────────────┤  │
│  │ available_methods:      │  │
│  │  - password             │──┐│
│  │  - email                │──┤│
│  │  - fido-uaf             │──┤│
│  └─────────────────────────┘  │││
│                                │││
│  ┌──────────────────────────┐ │││
│  │ Authentication Methods   │ │││
│  ├──────────────────────────┤ │││
│  │ password config         │◀┘││
│  │ email config            │◀─┘│
│  │ fido-uaf config         │◀──┘
│  └──────────────────────────┘  │
└────────────────────────────────┘
\`\`\`
```

#### 2. JSONPath基礎の説明

```markdown
## JSONPath条件の仕組み

### 認証状態データ構造

認証実行時、以下のような状態データが内部で管理されます：

\`\`\`json
{
  "password-authentication": {
    "success_count": 1,
    "failure_count": 0,
    "last_attempt_at": "2025-01-15T10:00:00Z"
  },
  "email-authentication": {
    "success_count": 0,
    "failure_count": 0
  },
  "fido-uaf-authentication": {
    "success_count": 0,
    "failure_count": 0
  }
}
\`\`\`

### JSONPath構文

\`\`\`
$.password-authentication.success_count
│              │                   │
│              │                   └─ フィールド名
│              └─ 認証インタラクション名
└─ ルート

結果: 1（パスワード認証の成功回数）
\`\`\`

### 利用可能なパス

| JSONPath | 説明 | 型 |
|----------|------|-----|
| \`$.{method}-authentication.success_count\` | 成功回数 | integer |
| \`$.{method}-authentication.failure_count\` | 失敗回数 | integer |

**{method}**: password, email, sms, webauthn, fido-uaf など
```

#### 3. any_of論理構造の図解

```markdown
## any_of条件の論理構造

### 基本形式

\`\`\`json
{
  "any_of": [
    [条件A, 条件B],  // ← グループ1（AND）
    [条件C]          // ← グループ2
  ]
}
\`\`\`

**意味**: (条件A AND 条件B) OR 条件C

### 視覚化

\`\`\`
┌─────────────────────────┐
│ any_of（いずれか満たす） │
├─────────────────────────┤
│ Group 1:                │
│  ✓ 条件A AND            │ ─┐
│  ✓ 条件B                │ ─┤ OR
│                         │  │
│ Group 2:                │  │
│  ✓ 条件C                │ ─┘
└─────────────────────────┘
\`\`\`

### 実例: 2FA設定

\`\`\`json
{
  "success_conditions": {
    "any_of": [
      [
        {"path": "$.password-authentication.success_count", "type": "integer", "operation": "gte", "value": 1},
        {"path": "$.email-authentication.success_count", "type": "integer", "operation": "gte", "value": 1}
      ]
    ]
  }
}
\`\`\`

**読み方**:
- Group 1: パスワード成功 **AND** Email成功
- → 両方成功で認証完了（2FA）

### 実例: 複数認証方式の選択

\`\`\`json
{
  "success_conditions": {
    "any_of": [
      [{"path": "$.password-authentication.success_count", "type": "integer", "operation": "gte", "value": 1}],
      [{"path": "$.fido-uaf-authentication.success_count", "type": "integer", "operation": "gte", "value": 1}]
    ]
  }
}
\`\`\`

**読み方**:
- Group 1: パスワード成功 **OR**
- Group 2: FIDO-UAF成功
- → どちらか一つ成功で認証完了
```

#### 4. シナリオ別設定例

```markdown
## シナリオ別Authentication Policy設定

### シナリオ1: 単一認証（パスワードのみ）

**要件**: パスワード認証のみで完了

\`\`\`json
{
  "flow": "oauth",
  "policies": [{
    "priority": 1,
    "available_methods": ["password"],
    "success_conditions": {
      "any_of": [[
        {"path": "$.password-authentication.success_count", "type": "integer", "operation": "gte", "value": 1}
      ]]
    }
  }]
}
\`\`\`

### シナリオ2: 2要素認証（パスワード + OTP）

**要件**: パスワードとEmail OTP両方必須

\`\`\`json
{
  "flow": "oauth",
  "policies": [{
    "priority": 1,
    "available_methods": ["password", "email"],
    "success_conditions": {
      "any_of": [[
        {"path": "$.password-authentication.success_count", "type": "integer", "operation": "gte", "value": 1},
        {"path": "$.email-authentication.success_count", "type": "integer", "operation": "gte", "value": 1}
      ]]
    }
  }]
}
\`\`\`

### シナリオ3: ステップアップ認証

**要件**: 通常はパスワード、ACR要求時は生体認証必須

\`\`\`json
{
  "flow": "oauth",
  "policies": [{
    "priority": 1,
    "available_methods": ["password", "fido-uaf"],
    "acr_mapping_rules": {
      "urn:mace:incommon:iap:gold": ["fido-uaf"],
      "urn:mace:incommon:iap:bronze": ["password"]
    },
    "success_conditions": {
      "any_of": [
        [{"path": "$.password-authentication.success_count", "type": "integer", "operation": "gte", "value": 1}],
        [{"path": "$.fido-uaf-authentication.success_count", "type": "integer", "operation": "gte", "value": 1}]
      ]
    }
  }]
}
\`\`\`

**動作**:
- 通常リクエスト → パスワード認証
- \`acr_values=urn:mace:incommon:iap:gold\` → FIDO-UAF必須
```
