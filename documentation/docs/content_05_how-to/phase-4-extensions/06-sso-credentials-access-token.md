# SSOクレデンシャルのアクセストークン埋め込み

## このドキュメントの目的

フェデレーション（SSO）で取得した**外部IdPのアクセストークン**を、idp-serverが発行するアクセストークンのカスタムクレームとして埋め込み、Token Introspectionで参照可能にすることが目標です。

### 所要時間
⏱️ **約15分**（フェデレーション設定済みの場合）

### 前提条件
- 管理者トークンを取得済み
- 外部IdPとのフェデレーション設定済み（[外部IdP連携の設定](../phase-3-advanced/01-federation-setup.md)）
- 組織ID（organization-id）を取得済み

---

## ユースケース

旧IDサービスを運用しつつ、idp-serverで認証機能（FIDO認証、eKYC等）を拡張する移行パターンで使用します。

```
モバイルアプリ         idp-server              旧IDサービス
  │                      │                        │
  │  フェデレーションログイン │                        │
  │ ────────────────────→│  OIDC認可コードフロー    │
  │                      │ ──────────────────────→│
  │                      │  AT + RT（旧サービス）   │
  │                      │ ←──────────────────────│
  │                      │                        │
  │  opaque AT発行       │  SSOクレデンシャル保存    │
  │ ←────────────────────│                        │

バックエンド             idp-server
  │                      │
  │  Token Introspection  │
  │ ────────────────────→│
  │  { "sso_access_token":│
  │    "旧AT値" }        │
  │ ←────────────────────│
  │                      │
  │  旧ATで既存API呼び出し │
  │ ──────────────────────────────────→ 旧APIゲートウェイ
```

バックエンドは Token Introspection で旧アクセストークンを取得し、既存の認可基盤にそのまま渡せます。

---

## Step 1: 認可サーバーの設定変更

アクセストークンを **opaque（識別子型）** にし、SSOクレデンシャルの埋め込みを有効化します。

```
PUT /v1/management/organizations/{organization-id}/tenants/{tenant-id}/authorization-server
```

`extension` に以下を設定:

```json
{
  "extension": {
    "access_token_type": "opaque",
    "access_token_sso_credentials": true
  }
}
```

### セキュリティ上の制約

| アクセストークン種別 | SSOクレデンシャル | 理由 |
|-------------------|----------------|------|
| **opaque（識別子型）** | ✅ 含まれる | カスタムクレームはDB内にのみ保存。Token Introspection（TLS経由）でのみ参照可能 |
| **JWT（JWS署名型）** | ❌ 含まれない | JWTペイロードに旧アクセストークンが平文で含まれるため、第三者に漏洩するリスクがある |

JWT型が設定されている場合、SSOクレデンシャルは自動的にスキップされ、警告ログが出力されます。

---

## Step 2: フェデレーション設定で store_credentials を有効化

フェデレーション設定の `payload` に `store_credentials: true` を追加します。

```
POST /v1/management/organizations/{organization-id}/tenants/{tenant-id}/federation-configurations
```

```json
{
  "type": "oidc",
  "sso_provider": "legacy-idp",
  "payload": {
    "issuer": "https://legacy-idp.example.com",
    "authorization_endpoint": "https://legacy-idp.example.com/authorize",
    "token_endpoint": "https://legacy-idp.example.com/token",
    "userinfo_endpoint": "https://legacy-idp.example.com/userinfo",
    "jwks_uri": "https://legacy-idp.example.com/.well-known/jwks.json",
    "client_id": "{client_id}",
    "client_secret": "{client_secret}",
    "redirect_uri": "https://idp-server.example.com/{tenant-id}/v1/authorizations/federations/oidc/callback",
    "store_credentials": true,
    "userinfo_mapping_rules": [
      { "from": "$.http_request.response_body.sub", "to": "external_user_id" },
      { "from": "$.http_request.response_body.email", "to": "email" },
      { "from": "$.http_request.response_body.name", "to": "name" }
    ]
  }
}
```

`store_credentials: true` により、フェデレーションログイン時に外部IdPから取得したアクセストークンとリフレッシュトークンがDB内に保存されます。

---

## Step 3: バックエンドから Token Introspection で旧トークンを取得

フェデレーションログイン完了後、バックエンドは Token Introspection で旧アクセストークンを取得できます。

```bash
curl -X POST https://idp-server.example.com/{tenant-id}/v1/tokens/introspection \
  -d "token={opaque_access_token}" \
  -d "client_id={client_id}" \
  -d "client_secret={client_secret}"
```

レスポンス:

```json
{
  "active": true,
  "sub": "user-123",
  "scope": "openid profile email",
  "iss": "https://idp-server.example.com/{tenant-id}",
  "client_id": "{client_id}",
  "exp": 1775550497,
  "iat": 1775547357,
  "sso_provider": "legacy-idp",
  "sso_access_token": "eyJhbGciOiJSUzI1NiIs..."
}
```

| クレーム | 説明 |
|---------|------|
| `sso_provider` | フェデレーション設定の `provider`（外部IdPの種別） |
| `sso_access_token` | 外部IdPが発行したアクセストークンの値 |

バックエンドは `sso_access_token` を使って旧APIゲートウェイにリクエストを転送できます。

---

## 動作条件

SSOクレデンシャルがIntrospectionに含まれるには、以下の**すべて**を満たす必要があります。

| # | 条件 | 確認方法 |
|---|------|---------|
| 1 | `access_token_sso_credentials: true` | 認可サーバーの extension 設定 |
| 2 | `access_token_type: "opaque"` | 認可サーバーの extension 設定 |
| 3 | `store_credentials: true` | フェデレーション設定の payload |
| 4 | ユーザーがフェデレーションログイン済み | SSOクレデンシャルがDB内に存在すること |

条件を満たさない場合の動作:

| ケース | 動作 |
|--------|------|
| `access_token_sso_credentials: false` | SSOクレデンシャルのクレーム処理自体がスキップされる |
| `access_token_type: "JWT"` | 警告ログを出力してスキップ（セキュリティ保護） |
| `store_credentials: false` またはフェデレーション未実施 | Introspectionにssoクレームが含まれない（エラーにはならない） |

---

## トラブルシューティング

### Introspectionにsso_provider / sso_access_tokenが含まれない

1. 認可サーバー設定を確認:
   - `extension.access_token_type` が `"opaque"` か？（`"JWT"` だと非対応）
   - `extension.access_token_sso_credentials` が `true` か？

2. フェデレーション設定を確認:
   - `payload.store_credentials` が `true` か？

3. ユーザーのSSOクレデンシャルを確認:
   - フェデレーションログインを実施済みか？
   - パスワードログイン等、フェデレーション以外の方法でログインしていないか？

### サーバーログに警告が出ている

```
SSO credentials in access token is only supported with opaque (identifier) access token type.
Current type is JWT. Skipping to prevent external token exposure.
```

→ `access_token_type` を `"opaque"` に変更してください。

---

## 関連ドキュメント

- [クイックスタート: 既存IDサービスからの移行](../../content_02_quickstart/quickstart-13-id-service-migration.md)
- [How-to: 外部IdP連携（Federation）の設定](../phase-3-advanced/01-federation-setup.md)
- [How-to: トークン有効期限パターン](../phase-2-security/02-token-strategy.md)
- [Concept: トークン管理](../../content_03_concepts/04-tokens-claims/concept-02-token-management.md)
