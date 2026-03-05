# ログイン設定 Q&A

> **使い方**: 「やりたいこと」から逆引きで設定を見つける。各回答には設定キーと具体的な値を記載。

> **注意: テナント更新APIはフル置換**
> `PUT /v1/management/.../tenants/{tenant-id}` は**パーシャルアップデート非対応**です。
> 送らなかったフィールド（`session_config`、`cors_config`、`ui_config` 等）は空のデフォルトにリセットされます。
> 変更するときは現在の設定を取得し、変えたいフィールドだけ上書きして全体を送ってください。
>
> ```bash
> # 現在の設定を取得 → 変えたい箇所だけ jq で上書き → 送信
> TENANT_JSON=$(jq '.tenant' config/generated/${ORGANIZATION_NAME}/public-tenant.json)
> UPDATED=$(echo "${TENANT_JSON}" | jq '.identity_policy_config.password_policy.min_length = 12')
> curl -s -X PUT "${ORG_BASE_URL}/${PUBLIC_TENANT_ID}" \
>   -H "Authorization: Bearer ${ORG_ACCESS_TOKEN}" \
>   -H "Content-Type: application/json" \
>   -d "${UPDATED}"
> ```
>
> 認可サーバー更新（`PUT .../authorization-server`）も同様にフル置換です。

---

## パスワードセキュリティ

### Q: パスワードを複雑にしたい

**設定箇所**: テナント `identity_policy_config.password_policy`
**API**: `PUT /v1/management/organizations/{org-id}/tenants/{tenant-id}`

```json
{
  "identity_policy_config": {
    "password_policy": {
      "require_uppercase": true,
      "require_lowercase": true,
      "require_number": true,
      "require_special_char": true,
      "min_length": 12
    }
  }
}
```

**環境変数**（setup.sh）:
```bash
PASSWORD_REQUIRE_UPPERCASE=true
PASSWORD_REQUIRE_LOWERCASE=true
PASSWORD_REQUIRE_NUMBER=true
PASSWORD_REQUIRE_SPECIAL_CHAR=true
PASSWORD_MIN_LENGTH=12
```

> 登録フォームのバリデーションも合わせる場合は、認証メソッド設定の `password.pattern` も更新すること。例: `"^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[!@#$%^&*()]).{12,}$"`

---

### Q: 過去に使ったパスワードの再利用を防ぎたい

**設定箇所**: テナント `identity_policy_config.password_policy.max_history`

```json
{
  "identity_policy_config": {
    "password_policy": {
      "max_history": 5
    }
  }
}
```

**環境変数**: `PASSWORD_MAX_HISTORY=5`

> `max_history: 5` → 直近5回分のパスワードは再利用不可。`0` で無効。

---

## アカウント保護

### Q: ブルートフォース攻撃を防ぎたい（アカウントロック）

**設定箇所**: テナント `identity_policy_config.password_policy`

```json
{
  "identity_policy_config": {
    "password_policy": {
      "max_attempts": 5,
      "lockout_duration_seconds": 900
    }
  }
}
```

**環境変数**:
```bash
PASSWORD_MAX_ATTEMPTS=5
PASSWORD_LOCKOUT_DURATION_SECONDS=900
```

> `max_attempts: 5` + `lockout_duration_seconds: 900` → 5回失敗で15分ロック。
> 厳格にしたい場合: `max_attempts: 3`, `lockout_duration_seconds: 3600`（3回失敗で1時間ロック）

**注意**: 認証ポリシー側にも `failure_conditions` / `lock_conditions` を設定しないとロックが効かない。→ [認証ポリシーのロック設定](#q-認証失敗時にアカウントロックを効かせたい)

---

### Q: 認証失敗時にアカウントロックを効かせたい

**設定箇所**: 認証ポリシー `policies[].failure_conditions` / `lock_conditions`
**API**: `PUT /v1/management/organizations/{org-id}/tenants/{tenant-id}/authentication-policies`

```json
{
  "policies": [
    {
      "description": "password_only",
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
      }
    }
  ]
}
```

> `failure_conditions` → 認証フロー内でのブロック。`lock_conditions` → アカウント自体のロック。両方設定するのが一般的。
> `value` はテナントの `password_policy.max_attempts` と合わせる。

---

## セッション管理

### Q: セッションの有効期限を短くしたい（セキュリティ重視）

**設定箇所**: テナント `session_config.timeout_seconds`
**API**: `PUT /v1/management/organizations/{org-id}/tenants/{tenant-id}`

```json
{
  "session_config": {
    "timeout_seconds": 1800
  }
}
```

**環境変数**: `SESSION_TIMEOUT_SECONDS=1800`

> `1800` = 30分。金融系なら `900`（15分）も一般的。

---

### Q: ログイン状態を長く保ちたい（利便性重視）

**設定箇所**: テナント `session_config.timeout_seconds` + 認可サーバー `extension`

テナント:
```json
{
  "session_config": {
    "timeout_seconds": 604800
  }
}
```

認可サーバー extension:
```json
{
  "extension": {
    "refresh_token_duration": 2592000
  }
}
```

**環境変数**:
```bash
SESSION_TIMEOUT_SECONDS=604800
REFRESH_TOKEN_DURATION=2592000
```

> セッション: `604800` = 7日間。リフレッシュトークン: `2592000` = 30日間。
> セッションとRTの両方を延ばすことで「しばらくログインし直さなくていい」体験になる。

---

### Q: 1デバイスのみログインに制限したい

**設定箇所**: テナント `session_config.switch_policy`

```json
{
  "session_config": {
    "switch_policy": "STRICT"
  }
}
```

> `STRICT` → 新しいログインで既存セッションが無効化される。
> `SWITCH_ALLOWED` → 切り替え可能（デフォルト）。
> `MULTI_SESSION` → 複数セッション同時利用可能。

---

### Q: 複数タブ/デバイスで同時ログインを許可したい

**設定箇所**: テナント `session_config.switch_policy`

```json
{
  "session_config": {
    "switch_policy": "MULTI_SESSION"
  }
}
```

---

## トークン有効期限

### Q: アクセストークンの有効期限を短くしたい（API保護）

**設定箇所**: 認可サーバー `extension.access_token_duration`
**API**: `PUT /v1/management/organizations/{org-id}/tenants/{tenant-id}/authorization-server`

```json
{
  "extension": {
    "access_token_duration": 300
  }
}
```

**環境変数**: `ACCESS_TOKEN_DURATION=300`

> `300` = 5分。短くするほどセキュア。ただし頻繁にリフレッシュが必要になる。
> 一般的な設定: `300`〜`1800`（5分〜30分）

---

### Q: リフレッシュトークンで長期間ログイン維持したい

**設定箇所**: 認可サーバー `extension.refresh_token_duration`

```json
{
  "extension": {
    "refresh_token_duration": 2592000
  }
}
```

**環境変数**: `REFRESH_TOKEN_DURATION=2592000`

> `2592000` = 30日。モバイルアプリなら `7776000`（90日）も一般的。
> ATを短く + RTを長く が推奨パターン。

---

### Q: IDトークンの有効期限を変えたい

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

## ユーザー登録

### Q: 登録時に名前を必須にしたい

**設定箇所**: 認証メソッド設定 `interactions.initial-registration.request.schema`
**API**: `POST /v1/management/organizations/{org-id}/tenants/{tenant-id}/authentication-configurations`

```json
{
  "type": "initial-registration",
  "interactions": {
    "initial-registration": {
      "request": {
        "schema": {
          "required": ["email", "password", "name"],
          "properties": {
            "name": { "type": "string", "maxLength": 255 }
          }
        }
      }
    }
  }
}
```

**環境変数**: `REGISTRATION_REQUIRED_FIELDS=email,password,name`

---

### Q: 登録時に電話番号も取得したい

**設定箇所**: 認証メソッド設定 `interactions.initial-registration.request.schema`

```json
{
  "type": "initial-registration",
  "interactions": {
    "initial-registration": {
      "request": {
        "schema": {
          "required": ["email", "password", "name", "phone_number"],
          "properties": {
            "phone_number": { "type": "string", "maxLength": 20 }
          }
        }
      }
    }
  }
}
```

**環境変数**: `REGISTRATION_REQUIRED_FIELDS=email,password,name,phone_number`

---

### Q: メールとパスワードだけで登録させたい（最小構成）

**設定箇所**: 認証メソッド設定 `interactions.initial-registration.request.schema`

```json
{
  "type": "initial-registration",
  "interactions": {
    "initial-registration": {
      "request": {
        "schema": {
          "required": ["email", "password"],
          "properties": {
            "email": { "type": "string", "format": "email", "maxLength": 255 },
            "password": { "type": "string", "minLength": 8, "maxLength": 64 }
          }
        }
      }
    }
  }
}
```

**環境変数**: `REGISTRATION_REQUIRED_FIELDS=email,password`

---

## クレーム・UserInfo

### Q: UserInfo / IDトークンでメールを返したい

**設定箇所**: 認可サーバー `claims_supported`
**API**: `PUT /v1/management/organizations/{org-id}/tenants/{tenant-id}/authorization-server`

```json
{
  "claims_supported": [
    "sub", "iss", "auth_time", "acr",
    "email", "email_verified"
  ]
}
```

> クライアントのスコープに `email` を含めること。
> `claims_supported` 自体が未設定だと `sub` しか返らないので注意。

---

### Q: プロフィール情報（名前・写真等）を返したい

**設定箇所**: 認可サーバー `claims_supported`

```json
{
  "claims_supported": [
    "sub", "iss", "auth_time", "acr",
    "name", "given_name", "family_name", "nickname", "preferred_username",
    "profile", "picture", "website",
    "gender", "birthdate", "zoneinfo", "locale", "updated_at"
  ]
}
```

> クライアントのスコープに `profile` を含めること。

---

### Q: 電話番号を返したい

**設定箇所**: 認可サーバー `claims_supported`

```json
{
  "claims_supported": [
    "sub", "iss", "auth_time", "acr",
    "phone_number", "phone_number_verified"
  ]
}
```

> クライアントのスコープに `phone` を含めること。

---

### Q: 全部入りのクレーム設定にしたい

**設定箇所**: 認可サーバー `claims_supported`

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

> 必要なスコープ: `openid profile email phone address`

---

## ソーシャルログイン

### Q: Googleログインを追加したい

**必要な設定**（3箇所）:

**1. フェデレーション設定を作成**
**API**: `POST /v1/management/organizations/{org-id}/tenants/{tenant-id}/federation-configurations`

```json
{
  "type": "oidc",
  "sso_provider": "google",
  "enabled": true,
  "payload": {
    "type": "standard",
    "provider": "standard",
    "issuer": "https://accounts.google.com",
    "issuer_name": "google",
    "authorization_endpoint": "https://accounts.google.com/o/oauth2/v2/auth",
    "token_endpoint": "https://oauth2.googleapis.com/token",
    "userinfo_endpoint": "https://openidconnect.googleapis.com/v1/userinfo",
    "jwks_uri": "https://www.googleapis.com/oauth2/v3/certs",
    "client_id": "YOUR_GOOGLE_CLIENT_ID",
    "client_secret": "YOUR_GOOGLE_CLIENT_SECRET",
    "redirect_uri": "https://{domain}/{tenant-id}/v1/authorizations/federations/oidc/callback",
    "scopes_supported": ["openid", "profile", "email"],
    "userinfo_mapping_rules": [
      { "from": "$.http_request.response_body.sub", "to": "external_user_id" },
      { "from": "$.http_request.response_body.email", "to": "email" },
      { "from": "$.http_request.response_body.name", "to": "name" },
      { "from": "$.http_request.response_body.picture", "to": "picture" }
    ]
  }
}
```

**2. クライアントにフェデレーションを紐付け**
**API**: `PUT /v1/management/organizations/{org-id}/tenants/{tenant-id}/clients/{client-id}`

```json
{
  "extension": {
    "available_federations": [
      {
        "id": "{federation-config-id}",
        "type": "oidc",
        "sso_provider": "google"
      }
    ]
  }
}
```

**3. 認証ポリシーにソーシャル認証の成功条件を追加**

```json
{
  "success_conditions": {
    "any_of": [
      [{ "path": "$.password-authentication.success_count", "type": "integer", "operation": "gte", "value": 1 }],
      [{ "path": "$.initial-registration.success_count", "type": "integer", "operation": "gte", "value": 1 }],
      [{ "path": "$.oidc-google.success_count", "type": "integer", "operation": "gte", "value": 1 }]
    ]
  }
}
```

> Google Cloud Console 側で「承認済みリダイレクトURI」に `redirect_uri` を登録するのを忘れずに。

---

## トラブルシューティング

### Q: UserInfoが `sub` しか返さない

**原因**: 認可サーバーの `claims_supported` が未設定。

**解決**: → [全部入りのクレーム設定にしたい](#q-全部入りのクレーム設定にしたい)

---

### Q: 認証失敗してもアカウントロックされない

**原因**: 認証ポリシーに `failure_conditions` / `lock_conditions` が未設定。

**解決**: → [認証失敗時にアカウントロックを効かせたい](#q-認証失敗時にアカウントロックを効かせたい)

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

### Q: ソーシャルログイン後に「authentication is required」エラーになる

**原因**: 認証ポリシーの `success_conditions` にソーシャル認証の成功条件がない。

**解決**: `success_conditions.any_of` に該当プロバイダーの条件を追加。

```json
[{ "path": "$.oidc-google.success_count", "type": "integer", "operation": "gte", "value": 1 }]
```

---

### Q: ソーシャルログインボタンが表示されない

**原因**: クライアントに `available_federations` が未設定。

**解決**: → [Googleログインを追加したい](#q-googleログインを追加したい) のステップ2を確認。

---

### Q: 認証UIのオリジン設定がおかしい

**設定箇所**: テナント `ui_config.base_url`

```json
{
  "ui_config": {
    "base_url": "https://auth-ui.example.com"
  }
}
```

> `base_url` には認証UIフロントエンドのオリジンを設定する。APIサーバーのURLではない。
