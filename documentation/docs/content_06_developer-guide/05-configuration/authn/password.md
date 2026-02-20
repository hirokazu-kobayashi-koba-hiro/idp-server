# パスワード認証

このドキュメントは、パスワードによる認証処理の`概要`・`設定`・`利用方法`及び`内部ロジック`について説明します。

この方式では、ユーザーID（通常はメールアドレス）とパスワードを用いた、一般的な認証フローを実装しています。

---

## 概要
パスワード認証は、ユーザーがID（一般にメールアドレス）とパスワードを入力してログインする、もっとも基本的で広く使われている認証方式です。

この方式は、認可リクエストに対応したログイン画面から実行され、ユーザーのクレデンシャルを検証することで認証を完了します。

本実装では、以下の特徴を備えています：
* ユーザー入力のパスワードと事前に登録されたハッシュ化されたパスワードを比較します。
* 成功時にはpwd認証手段およびACR値を付加した認証オブジェクトを返却
* 失敗時にはセキュリティイベント（例：password_failure）をトリガー

テナントは `password-authentication` の登録を行うことでこの機能を利用することが可能です。


## 設定

パスワード認証を使用するには、テナントに `type = "password"` の認証設定を登録する必要があります。

### 基本構造

すべての認証設定は、統一されたinteractions形式を使用します：

```json
{
  "id": "UUID",
  "type": "password",
  "attributes": {},
  "metadata": {
    "type": "password",
    "description": "Standard password authentication"
  },
  "interactions": {
    "password-authentication": {
      "request": {
        "schema": {
          "type": "object",
          "properties": {
            "username": {
              "type": "string",
              "description": "Username (preferred_username)"
            },
            "password": {
              "type": "string",
              "description": "Password"
            },
            "provider_id": {
              "type": "string",
              "description": "Provider ID (default: idp-server)"
            }
          },
          "required": ["username", "password"]
        }
      },
      "pre_hook": {},
      "execution": {
        "function": "password_verification"
      },
      "post_hook": {},
      "response": {
        "body_mapping_rules": [
          { "from": "$.user_id", "to": "user_id" },
          { "from": "$.username", "to": "username" }
        ]
      }
    }
  }
}
```

**情報源**: `config/examples/e2e/test-tenant/authentication-config/password/standard.json`

### 設定項目

| フィールド | 説明 |
|-----------|------|
| `id` | 設定ID（UUID） |
| `type` | `"password"` 固定 |
| `attributes` | カスタム属性（オプション） |
| `metadata` | メタデータ（説明等） |
| `interactions` | インタラクション定義 |

### Request Schema

`password-authentication` interactionで受け付けるリクエストの構造：

| フィールド | 型 | 必須 | 説明 |
|-----------|-----|------|------|
| `username` | string | ✅ | ユーザー名（通常はメールアドレス） |
| `password` | string | ✅ | パスワード |
| `provider_id` | string | ❌ | プロバイダーID（デフォルト: "idp-server"） |

### Execution Function

**`password_verification`**: パスワード検証を実行します。

**処理内容**:
1. ユーザー検索（username + provider_id）
2. パスワードハッシュ照合
3. 成功時にユーザー情報とAuthentication返却

### Response Mapping

**`body_mapping_rules`**: 認証成功時のレスポンスマッピング

- `$.user_id` → `user_id`: ユーザーID
- `$.username` → `username`: ユーザー名

### 前提条件

* ユーザーがテナントに登録されていること
* ユーザーのパスワードが適切にハッシュ化されて保存されていること

## 利用方法

### 事前準備

1. テナントに `type = "password"` の認証設定を登録する（上記設定例を参照）

### 認証フロー

1. 認可リクエストにてログイン画面を表示（例：`prompt=login`）
2. ログインフォームで入力された `username` と `password` を使って、以下のエンドポイントにPOSTする：

```http
POST /v1/authorizations/{id}/password-authentication
Content-Type: application/json
```

**リクエストボディ例**:

```json
{
  "username": "user@example.com",
  "password": "P@ssw0rd!",
  "provider_id": "idp-server"
}
```

**レスポンス例（成功時）**:

```json
{
  "user_id": "550e8400-e29b-41d4-a716-446655440000",
  "username": "user@example.com"
}
```

---

## ブルートフォース対策

パスワード認証には、テナント単位で設定可能なブルートフォース攻撃対策が組み込まれています。

### 仕組み

Redisのアトミックインクリメント（INCR）を使用して、テナント+ユーザー単位で失敗回数を追跡します。

```
キーフォーマット: password_attempt:{tenant_id}:{username}
```

1. パスワード認証リクエストのたびにカウンターをインクリメント
2. カウンターが `max_attempts` を超過した場合、認証を拒否（`too_many_attempts` エラー）
3. 認証成功時にカウンターをリセット（削除）
4. TTL（`lockout_duration_seconds`）の経過で自動リセット

### 設定

テナントの `identity_policy_config.password_policy` で設定します。

| フィールド | デフォルト | 説明 |
|-----------|----------|------|
| `max_attempts` | `5` | 最大連続失敗回数（0で無制限） |
| `lockout_duration_seconds` | `900` | ロックアウト期間（秒、デフォルト15分） |

### エラーレスポンス（ロックアウト時）

```json
{
  "error": "too_many_attempts",
  "error_description": "Too many failed attempts. Please try again later."
}
```

### 関連する3つの設定の関係

パスワード認証に関わる設定は3つのレイヤーに分かれています。

| 設定 | 管理対象 | 役割 |
|------|---------|------|
| **認証コンフィグ** (`authentication-config`) | 認証の動作定義 | interactions、execution function（`password_verification`）、リクエストスキーマ、レスポンスマッピングを定義 |
| **パスワードポリシー** (`identity_policy_config.password_policy`) | テナント全体のポリシー | パスワード複雑性要件（文字数、文字種）とブルートフォース対策（`max_attempts`）を定義 |
| **認証ポリシー** (`authentication-policy`) | 認証フローの制御 | 認証ステップの順序、`lock_conditions` によるセッション内ロックを定義 |

ブルートフォース対策については、2つのロックアウト機構があります。

| 項目 | `password_policy.max_attempts` | `authentication_policy.lock_conditions` |
|------|-------------------------------|----------------------------------------|
| **追跡単位** | テナント + ユーザー名（セッション横断） | 認証トランザクション内の `failure_count` |
| **追跡方法** | Redis カウンター（INCR + TTL） | セッション内の状態管理 |
| **リセット** | 認証成功時 or TTL経過 | 認証トランザクション終了時 |
| **用途** | ブルートフォース攻撃（セッション横断の総当たり） | 1セッション内での連続失敗によるロック |

`password_policy.max_attempts` はセッションをまたいだ攻撃を防ぎ、`lock_conditions` は1つの認証フロー内での失敗回数を制御します。両方を設定することで多層防御が可能です。

### 注意事項

- Redis未使用環境（`NoOperationCacheStore`）ではブルートフォース対策は無効になります（フェイルオープン）
- カウンターはセッション横断で追跡されるため、異なるセッションからの攻撃にも有効です

---

## 内部ロジック

1. **ブルートフォースチェック**
   Redisカウンター（`password_attempt:{tenant_id}:{username}`）をインクリメントし、`max_attempts`超過時は認証を拒否。

2. **ユーザー検索**
   `userQueryRepository.findByPreferredUsername(...)` により、テナント + ユーザー名 + provider\_id でユーザー情報を検索。

3. **パスワード検証**
   `passwordVerificationDelegation.verify(password, user.hashedPassword())` によって、入力されたパスワードをハッシュと比較。

4. **認証失敗時の応答**
   ユーザーが存在しない、またはパスワードが不一致の場合は `CLIENT_ERROR` を返し、`password_failure` イベントを発行。

5. **認証成功処理**
   カウンターをリセットし、以下の情報で `Authentication` オブジェクトを構成：

    * `time`: 現在時刻（SystemDateTime）
    * `methods`: `["pwd"]`
    * `acr_values`: `["urn:mace:incommon:iap:silver"]`

6. **成功レスポンス**
   `user` と `authentication` を含むレスポンスを生成し、`password_success` イベントを発行。

---

## スキーマ設定

この方式はユーザー属性定義スキーマを直接使わず、`username`（= email等）と `password` の検証に特化しています。
ただし、事前に `User` 情報が正しく登録されている必要があります。

---

## 備考

* `provider_id` は省略時 `"idp-server"` が使用されます。
* 認証手段（methods）とACR値（acr\_values）は固定で `pwd` / `urn:mace:incommon:iap:silver` を使用しています。
* セキュリティイベント（成功・失敗）はそれぞれ `password_success`, `password_failure` としてログに記録されます。
