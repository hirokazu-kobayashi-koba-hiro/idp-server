# 外部パスワード認証 Q&A

> **使い方**: 「やりたいこと」から逆引きで設定を見つける。各回答には設定キーと具体的な値を記載。

> **注意: テナント更新APIはフル置換**
> `PUT /v1/management/.../tenants/{tenant-id}` は**パーシャルアップデート非対応**です。
> 送らなかったフィールド（`session_config`、`cors_config`、`ui_config` 等）は空のデフォルトにリセットされます。
> 変更するときは現在の設定を取得し、変えたいフィールドだけ上書きして全体を送ってください。
>
> ```bash
> # 現在の設定を取得 -> 変えたい箇所だけ jq で上書き -> 送信
> TENANT_JSON=$(jq '.tenant' config/generated/${ORGANIZATION_NAME}/public-tenant.json)
> UPDATED=$(echo "${TENANT_JSON}" | jq '.session_config.timeout_seconds = 3600')
> curl -s -X PUT "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}" \
>   -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
>   -H "Content-Type: application/json" \
>   -d "${UPDATED}"
> ```
>
> 認可サーバー更新（`PUT .../authorization-server`）も同様にフル置換です。

---

## 外部認証サービス設定

### Q: 外部認証サービスURLを変更したい

**設定箇所**: 認証メソッド設定 `interactions.password-authentication.execution.http_request.url`
**API**: `PUT /v1/management/organizations/{org-id}/tenants/{tenant-id}/authentication-configurations/{config-id}`

```json
{
  "interactions": {
    "password-authentication": {
      "execution": {
        "http_request": {
          "url": "https://auth.internal.example.com/api/authenticate"
        }
      }
    }
  }
}
```

**環境変数**（setup.sh）: `EXTERNAL_AUTH_URL="https://auth.internal.example.com/api/authenticate"`

> Docker 環境の場合、`localhost` ではなく `host.docker.internal` を使うこと。
> 例: `http://host.docker.internal:4000/auth/password`

---

### Q: 外部プロバイダーIDを変更したい

**設定箇所**: 認証メソッド設定 `interactions.password-authentication.user_resolve.user_mapping_rules`
**API**: `PUT /v1/management/organizations/{org-id}/tenants/{tenant-id}/authentication-configurations/{config-id}`

`provider_id` の `static_value` を変更する:

```json
{
  "user_resolve": {
    "user_mapping_rules": [
      { "from": "$.execution_http_request.response_body.user_id", "to": "external_user_id" },
      { "from": "$.execution_http_request.response_body.email", "to": "email" },
      { "from": "$.execution_http_request.response_body.name", "to": "name" },
      { "static_value": "ldap-wrapper", "to": "provider_id" }
    ]
  }
}
```

**環境変数**: `EXTERNAL_PROVIDER_ID="ldap-wrapper"`

---

### Q: マッピングルールをカスタマイズしたい（レスポンスフィールド追加）

**設定箇所**: 認証メソッド設定 `interactions.password-authentication.user_resolve.user_mapping_rules`

外部サービスが追加フィールド（例: `department`, `employee_id`）を返す場合、`user_mapping_rules` にマッピングを追加する:

```json
{
  "user_resolve": {
    "user_mapping_rules": [
      { "from": "$.execution_http_request.response_body.user_id", "to": "external_user_id" },
      { "from": "$.execution_http_request.response_body.email", "to": "email" },
      { "from": "$.execution_http_request.response_body.name", "to": "name" },
      { "from": "$.execution_http_request.response_body.given_name", "to": "given_name" },
      { "from": "$.execution_http_request.response_body.family_name", "to": "family_name" },
      { "static_value": "external-auth", "to": "provider_id" }
    ]
  }
}
```

> マッピング可能な `to` フィールドは idp-server のユーザー属性（`email`, `name`, `given_name`, `family_name`, `nickname`, `picture`, `external_user_id`, `provider_id` 等）。

---

### Q: 外部サービスにカスタムヘッダーを送りたい

**設定箇所**: 認証メソッド設定 `interactions.password-authentication.execution.http_request.header_mapping_rules`

例: API キーをヘッダーで送る場合:

```json
{
  "execution": {
    "http_request": {
      "header_mapping_rules": [
        { "static_value": "application/json", "to": "Content-Type" },
        { "static_value": "Bearer your-api-key-here", "to": "Authorization" }
      ]
    }
  }
}
```

---

### Q: 外部サービスに送るリクエストボディをカスタマイズしたい

**設定箇所**: 認証メソッド設定 `interactions.password-authentication.execution.http_request.body_mapping_rules`

デフォルトは `username` / `password` を送信するが、外部サービスのフィールド名が異なる場合に変更する:

```json
{
  "execution": {
    "http_request": {
      "body_mapping_rules": [
        { "from": "$.request_body.username", "to": "login_id" },
        { "from": "$.request_body.password", "to": "secret" },
        { "static_value": "password", "to": "auth_type" }
      ]
    }
  }
}
```

---

### Q: 認証エラー時のレスポンスをカスタマイズしたい

**設定箇所**: 認証メソッド設定 `interactions.password-authentication.response.body_mapping_rules`

外部サービスのエラーレスポンス形式に合わせて `condition` と `from` を調整する:

```json
{
  "response": {
    "body_mapping_rules": [
      {
        "from": "$.execution_http_request.response_body.error_code",
        "to": "error",
        "condition": { "operation": "exists", "path": "$.execution_http_request.response_body.error_code" }
      },
      {
        "from": "$.execution_http_request.response_body.message",
        "to": "error_description",
        "condition": { "operation": "exists", "path": "$.execution_http_request.response_body.message" }
      }
    ]
  }
}
```

> `condition` を使うことで、フィールドが存在する場合のみマッピングされる。成功/失敗の両方のレスポンスを1つの設定で扱える。

---

## ブルートフォース防止

### Q: アカウントロックの閾値を変えたい

**設定箇所**: 認証ポリシー `policies[].failure_conditions` / `lock_conditions`
**API**: `PUT /v1/management/organizations/{org-id}/tenants/{tenant-id}/authentication-policies/{policy-id}`

例: 3回失敗でロック:

```json
{
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
          "value": 3
        }
      ]
    ]
  }
}
```

> `failure_conditions` -> 認証フロー内でのブロック。`lock_conditions` -> アカウント自体のロック。両方設定するのが一般的。
> 厳格にしたい場合: `value: 3`（3回失敗でロック）。緩めにしたい場合: `value: 10`。

---

### Q: アカウントロックを無効にしたい

**設定箇所**: 認証ポリシー

`failure_conditions` と `lock_conditions` を空にする（または削除する）:

```json
{
  "policies": [
    {
      "description": "external_password_only",
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
}
```

> ブルートフォース防止は外部サービス側で管理する場合に有効。

---

## セッション管理

### Q: セッションの有効期限を変更したい

**設定箇所**: テナント `session_config.timeout_seconds`
**API**: `PUT /v1/management/organizations/{org-id}/tenants/{tenant-id}`

```json
{
  "session_config": {
    "timeout_seconds": 3600
  }
}
```

**環境変数**: `SESSION_TIMEOUT_SECONDS=3600`

> `3600` = 1時間。`86400` = 24時間（デフォルト）。金融系なら `900`（15分）も一般的。

---

### Q: セッション切り替えポリシーを変更したい

use-case-login の qa.md と同様の設定。

**設定箇所**: テナント `session_config.switch_policy`

```json
{
  "session_config": {
    "switch_policy": "STRICT"
  }
}
```

> `STRICT` -> 新しいログインで既存セッションが無効化。
> `SWITCH_ALLOWED` -> 切り替え可能（デフォルト）。
> `MULTI_SESSION` -> 複数セッション同時利用可能。

---

## トークン有効期限

### Q: アクセストークンの有効期限を変更したい

**設定箇所**: 認可サーバー `extension.access_token_duration`
**API**: `PUT /v1/management/organizations/{org-id}/tenants/{tenant-id}/authorization-server`

```json
{
  "extension": {
    "access_token_duration": 1800
  }
}
```

**環境変数**: `ACCESS_TOKEN_DURATION=1800`

> `1800` = 30分。短くするほどセキュア。一般的な設定: `300`〜`1800`（5分〜30分）。

---

### Q: リフレッシュトークンの有効期限を変更したい

**設定箇所**: 認可サーバー `extension.refresh_token_duration`

```json
{
  "extension": {
    "refresh_token_duration": 604800
  }
}
```

**環境変数**: `REFRESH_TOKEN_DURATION=604800`

> `604800` = 7日。ATを短く + RTを長くが推奨パターン。

---

### Q: IDトークンの有効期限を変更したい

**設定箇所**: 認可サーバー `extension.id_token_duration`

```json
{
  "extension": {
    "id_token_duration": 600
  }
}
```

**環境変数**: `ID_TOKEN_DURATION=600`

> `600` = 10分。IDトークンは認証の証跡なので短めが推奨。

---

## クレーム・UserInfo

### Q: UserInfo / IDトークンで返すクレームを設定したい

use-case-login の qa.md と同様。認可サーバーの `claims_supported` を設定する。

**設定箇所**: 認可サーバー `claims_supported`
**API**: `PUT /v1/management/organizations/{org-id}/tenants/{tenant-id}/authorization-server`

```json
{
  "claims_supported": [
    "sub", "iss", "auth_time", "acr",
    "name", "given_name", "family_name", "nickname", "preferred_username", "middle_name",
    "profile", "picture", "website",
    "email", "email_verified",
    "gender", "birthdate", "zoneinfo", "locale", "updated_at",
    "address", "phone_number", "phone_number_verified"
  ]
}
```

> `claims_supported` 自体が未設定だと `sub` しか返らないので注意。

---

## トラブルシューティング

### Q: 外部サービスに接続できない（タイムアウト・接続拒否）

**原因**: idp-server コンテナから外部サービスに到達できない。

**確認ポイント**:
1. `EXTERNAL_AUTH_URL` で `localhost` を使っていないか -> Docker 内からは `host.docker.internal` を使う
2. 外部サービスが起動しているか
3. ファイアウォール/セキュリティグループが通信を許可しているか

**ローカルテスト**: モックサーバーを使って検証する:
```bash
node config/templates/use-cases/external-password-auth/mock-server.js
# EXTERNAL_AUTH_URL=http://host.docker.internal:4000/auth/password
```

---

### Q: 認証は成功するが UserInfo が `sub` しか返さない

**原因**: 認可サーバーの `claims_supported` が未設定。

**解決**: -> [UserInfo / IDトークンで返すクレームを設定したい](#q-userinfo--idトークンで返すクレームを設定したい)

---

### Q: 外部サービスの認証は成功するがユーザー情報がマッピングされない

**原因**: `user_mapping_rules` の `from` パスが外部サービスのレスポンスと一致していない。

**確認ポイント**:
1. 外部サービスのレスポンスJSONのフィールド名を確認
2. `from` パスが `$.execution_http_request.response_body.{フィールド名}` の形式であること
3. `to` が idp-server の有効なユーザー属性であること

例: 外部サービスが `userId` を返す場合（`user_id` ではなく）:
```json
{ "from": "$.execution_http_request.response_body.userId", "to": "external_user_id" }
```

---

### Q: 認証失敗してもアカウントロックされない

**原因**: 認証ポリシーに `failure_conditions` / `lock_conditions` が未設定。

**解決**: -> [アカウントロックの閾値を変えたい](#q-アカウントロックの閾値を変えたい)

---

### Q: CORSエラーが出る

**設定箇所**: テナント `cors_config`
**API**: `PUT /v1/management/organizations/{org-id}/tenants/{tenant-id}`

```json
{
  "cors_config": {
    "allow_origins": ["https://your-app.example.com"],
    "allow_methods": ["GET", "POST", "PUT", "DELETE", "OPTIONS"],
    "allow_headers": ["Content-Type", "Authorization"],
    "allow_credentials": true
  }
}
```

> `allow_origins` だけ設定して `allow_methods`, `allow_headers`, `allow_credentials` が抜けているケースが多い。全フィールド設定すること。

---

### Q: `identity_unique_key_type` はどう設定すべきか

**設定箇所**: テナント `identity_policy_config.identity_unique_key_type`

外部パスワード認証では `EMAIL_OR_EXTERNAL_USER_ID` を使う:

```json
{
  "identity_policy_config": {
    "identity_unique_key_type": "EMAIL_OR_EXTERNAL_USER_ID"
  }
}
```

> `EMAIL` だとメールアドレスのみで識別。外部サービスが `user_id`（external_user_id）を返す場合は `EMAIL_OR_EXTERNAL_USER_ID` にしないと外部ユーザーIDでの識別が機能しない。
