# Federation設定ガイド

## このドキュメントの目的

外部IdP連携（Federation）の設定方法を理解します。

### 所要時間
⏱️ **約20分**

---

## Federationとは

**Federation（フェデレーション）**は外部Identity Provider（IdP）と連携してユーザー認証を行う機能です。

**ユースケース**:
- 既存の企業IdPと連携
- ソーシャルログイン（外部OIDC準拠IdP等）
- 他システムの認証情報を利用

---

## 設定ファイル構造

### federation/oidc/external-idp.json

```json
{
  "id": "dc000822-a7ca-47b9-aea2-f81e2772b037",
  "type": "oidc",
  "sso_provider": "external-idp",
  "payload": {
    "issuer": "${EXTERNAL_IDP_ISSUER}",
    "issuer_name": "external-idp",
    "type": "oauth-extension",
    "provider": "oauth-extension",
    "authorization_endpoint": "${EXTERNAL_IDP_AUTHORIZATION_ENDPOINT}",
    "token_endpoint": "${EXTERNAL_IDP_TOKEN_ENDPOINT}",
    "userinfo_endpoint": "${EXTERNAL_IDP_USERINFO_ENDPOINT}",
    "client_id": "${EXTERNAL_IDP_CLIENT_ID}",
    "client_secret": "${EXTERNAL_IDP_CLIENT_SECRET}",
    "client_authentication_type": "client_secret_post",
    "redirect_uri": "${IDP_SERVER_URL}/${TENANT_ID}/v1/federation/oidc/callback",
    "scopes_supported": [
      "openid",
      "profile",
      "email"
    ],
    "userinfo_mapping_rules": [
      {
        "from": "$.sub",
        "to": "external_user_id"
      },
      {
        "from": "$.name",
        "to": "name"
      },
      {
        "from": "$.email",
        "to": "email"
      }
    ],
    "access_token_expires_in": 300,
    "refresh_token_expires_in": 1800,
    "store_credentials": true
  }
}
```

---

## 主要なフィールド

### 基本情報

| フィールド | 必須 | 説明 | 例 |
|-----------|------|------|---|
| `id` | ✅ | Federation設定ID（UUID） | `dc000822-...` |
| `type` | ✅ | プロトコルタイプ | `oidc` / `saml` |
| `sso_provider` | ✅ | プロバイダーID | `external-idp` |

---

### Payloadセクション

#### OIDC基本設定

| フィールド | 必須 | 説明 |
|-----------|------|------|
| `issuer` | ✅ | 外部IdPのIssuer |
| `authorization_endpoint` | ✅ | 認可エンドポイント |
| `token_endpoint` | ✅ | トークンエンドポイント |
| `userinfo_endpoint` | ✅ | UserInfoエンドポイント |
| `client_id` | ✅ | idp-serverのクライアントID |
| `client_secret` | ✅ | idp-serverのクライアントシークレット |
| `redirect_uri` | ✅ | コールバックURI |
| `scopes_supported` | ✅ | リクエストするスコープ |

---

#### UserInfo Mapping Rules

外部IdPのUserInfo → idp-serverのUserへのマッピング：

```json
{
  "userinfo_mapping_rules": [
    {
      "from": "$.sub",
      "to": "external_user_id"
    },
    {
      "from": "$.name",
      "to": "name"
    },
    {
      "from": "$.email",
      "to": "email"
    },
    {
      "from": "$.custom_field",
      "to": "custom_properties.custom_field"
    }
  ]
}
```

**JSONPath**: `$.` で外部IdPのUserInfo JSONを参照

---

#### 高度なUserInfo取得（http_requests）

**単純なUserInfoエンドポイントでは不十分な場合**に、複数のAPIを連続実行してUserInfoを構築します。

##### 基本パターン: 複数API連続実行

```json
{
  "userinfo_execution": {
    "function": "http_requests",
    "http_requests": [
      {
        "url": "${EXTERNAL_API_URL}/accounts",
        "method": "GET",
        "note": "1. アカウント情報取得"
      },
      {
        "url": "${EXTERNAL_API_URL}/profile",
        "method": "GET",
        "note": "2. プロファイル情報取得"
      }
    ]
  },
  "userinfo_mapping_rules": [
    {
      "from": "$.userinfo_execution_http_requests[0].response_body.account_id",
      "to": "external_user_id"
    },
    {
      "from": "$.userinfo_execution_http_requests[1].response_body.name",
      "to": "name"
    }
  ]
}
```

**重要**:
- `userinfo_execution_http_requests[0]` - 1番目のAPIレスポンス
- `userinfo_execution_http_requests[1]` - 2番目のAPIレスポンス
- インデックス順に実行される

---

##### 高度なパターン: Access Tokenを使った認証

外部IdPから取得したAccess Tokenで、外部APIにアクセス：

```json
{
  "userinfo_execution": {
    "function": "http_requests",
    "http_requests": [
      {
        "url": "${EXTERNAL_API_URL}/accounts",
        "method": "GET",
        "note": "Access Tokenで外部API呼び出し",
        "header_mapping_rules": [
          {
            "static_value": "application/json",
            "to": "Accept"
          },
          {
            "from": "$.request_body.access_token",
            "to": "Authorization",
            "convertType": "string",
            "functions": [
              {
                "name": "format",
                "args": {
                  "template": "Bearer {{value}}"
                }
              }
            ]
          }
        ]
      }
    ]
  }
}
```

**重要なポイント**:
1. **`$.request_body.access_token`** - 外部IdPから取得したAccess Token
2. **`functions`** - `format`関数で"Bearer "プレフィックスを付与
3. **`convertType: "string"`** - 文字列として扱う

---

##### 最高度パターン: OAuth 2.0認証付きAPI呼び出し

外部APIが独自のOAuth 2.0認証を要求する場合：

```json
{
  "userinfo_execution": {
    "function": "http_requests",
    "http_requests": [
      {
        "url": "${EXTERNAL_API_URL}/secure-data",
        "method": "POST",
        "note": "OAuth 2.0認証が必要なAPI",
        "auth_type": "oauth2",
        "oauth_authorization": {
          "type": "client_credentials",
          "token_endpoint": "${EXTERNAL_AUTH_URL}/token",
          "client_id": "${EXTERNAL_CLIENT_ID}",
          "client_secret": "${EXTERNAL_CLIENT_SECRET}",
          "client_authentication_type": "client_secret_post",
          "scope": "read:data",
          "cache_enabled": true,
          "cache_ttl_seconds": 3600,
          "cache_buffer_seconds": 10
        }
      }
    ]
  }
}
```

**OAuth認証のキャッシュ設定**:
- `cache_enabled: true` - トークンをキャッシュ
- `cache_ttl_seconds: 3600` - キャッシュ有効期限（秒）
- `cache_buffer_seconds: 10` - 期限切れ10秒前に再取得

**動作**:
1. 初回: Token Endpointでトークン取得 → キャッシュ保存
2. 2回目以降: キャッシュから取得（高速）
3. 期限切れ前: 自動的に再取得

---

##### 複数APIの結果を統合する例

```json
{
  "userinfo_mapping_rules": [
    {
      "from": "$.userinfo_execution_http_requests[0].response_body.user_id",
      "to": "external_user_id",
      "note": "1番目のAPI（/accounts）から取得"
    },
    {
      "from": "$.userinfo_execution_http_requests[0].response_body.account_type",
      "to": "custom_properties.account_type",
      "note": "1番目のAPIから取得"
    },
    {
      "from": "$.userinfo_execution_http_requests[1].response_body.email",
      "to": "email",
      "note": "2番目のAPI（/email）から取得"
    },
    {
      "from": "$.userinfo_execution_http_requests[2].response_body.phone",
      "to": "phone_number",
      "note": "3番目のAPI（/phone）から取得"
    }
  ]
}
```

**重要**: 各APIのレスポンスを`[0]`, `[1]`, `[2]`のインデックスで参照

**詳細**: [HttpRequestExecutor実装ガイド](../04-implementation-guides/impl-16-http-request-executor.md)、[Mapping Functions](../04-implementation-guides/impl-20-mapping-functions.md)

---

#### トークン保存

| フィールド | 説明 | デフォルト |
|-----------|------|----------|
| `store_credentials` | 外部IdPのトークンを保存 | `false` |
| `access_token_expires_in` | Access Token有効期限 | 300秒 |
| `refresh_token_expires_in` | Refresh Token有効期限 | 1800秒 |

**用途**: 外部APIへの後続アクセスでトークンを再利用

---

## Management APIで登録

### API エンドポイント

**組織レベルAPI**:
```
POST /v1/management/organizations/{organization-id}/tenants/{tenant-id}/federation-configurations
```

### Federation設定登録

```bash
POST /v1/management/organizations/{organization-id}/tenants/{tenant-id}/federation-configurations
Content-Type: application/json

{
  "id": "uuid",
  "type": "oidc",
  "sso_provider": "external-idp",
  "payload": {
    "issuer": "https://external-idp.example.com",
    "authorization_endpoint": "https://external-idp.example.com/authorize",
    "token_endpoint": "https://external-idp.example.com/token",
    "userinfo_endpoint": "https://external-idp.example.com/userinfo",
    "client_id": "your-client-id",
    "client_secret": "your-client-secret",
    "redirect_uri": "https://idp.example.com/{tenant-id}/v1/federation/oidc/callback",
    "scopes_supported": ["openid", "profile"],
    "userinfo_mapping_rules": [
      {"from": "$.sub", "to": "external_user_id"}
    ]
  }
}
```

---

## Authentication Policyとの連携

Federation認証を有効化：

```json
{
  "flow": "oauth",
  "policies": [
    {
      "available_methods": [
        "oidc-external-idp",
        "password"
      ]
    }
  ]
}
```

**注意**: `oidc-{sso_provider}` 形式で指定

---

## よくある設定ミス

### ミス1: redirect_uri不一致

**エラー**: 外部IdPから`redirect_uri_mismatch`

**原因**: 外部IdPに登録したredirect_uriとFederation設定が不一致

**解決策**: 外部IdPの管理画面でredirect_uriを確認

### ミス2: userinfo_mapping_rules誤り

**問題**: ユーザー情報が正しくマッピングされない

**原因**: JSONPathが間違っている

**解決策**: 外部IdPのUserInfoレスポンスを確認してパスを修正

---

## 次のステップ

✅ Federation設定を理解した！

### 次に読むべきドキュメント

1. [Federation実装ガイド](../03-application-plane/08-federation.md) - フロー詳細
2. [HttpRequestExecutor実装ガイド](../04-implementation-guides/impl-16-http-request-executor.md)

---

**最終更新**: 2025-10-13

---

## 📊 初学者向けドキュメント品質レビュー

**レビュー日**: 2025-01-15
**レビュー対象**: 初学者（idp-server開発経験なし、Java/Spring Boot基礎知識あり）

### ✅ 良い点

1. **段階的な説明**: 基本→高度→最高度と3段階でhttp_requests機能を説明
2. **実用的な例**: 複数API連続実行の具体例が充実
3. **Mapping Functionsとの連携**: formatFunction使用例が明確
4. **OAuth認証キャッシュ**: パフォーマンス最適化の説明が詳細
5. **UserInfo Mapping**: JSONPathでのマッピング例が豊富
6. **関連ドキュメント**: HttpRequestExecutorとMapping Functionsへのリンク

### ⚠️ 改善推奨事項

- [ ] **Federationの概念説明**（重要度: 高）
  - なぜFederationが必要かのビジネスシナリオ
  - 外部IdPとidp-serverの役割分担
  - フェデレーション認証の流れ図

- [ ] **最小構成の例**（重要度: 高）
  - 最もシンプルな標準OIDC連携例（http_requests不使用）
  - 段階的に複雑化する説明

- [ ] **http_requests機能の位置づけ**（重要度: 高）
  - 「いつ標準UserInfoで十分か」
  - 「いつhttp_requestsが必要か」
  - 判断基準の明示

- [ ] **動作確認手順**（重要度: 高）
  - Federation認証のテスト方法
  - 外部IdPとの連携確認手順

- [ ] **前提知識の明記**（重要度: 中）
  - OAuth 2.0/OIDCフロー
  - JSONPath構文
  - Mapping Functions基礎

- [ ] **トラブルシューティング拡充**（重要度: 中）
  - UserInfo取得失敗時の確認ポイント
  - マッピングエラーのデバッグ方法

- [ ] **セキュリティ考慮事項**（重要度: 中）
  - 外部IdPの信頼性検証
  - トークン保存のリスク
  - SSL/TLS必須の明記

### 💡 追加推奨コンテンツ

1. **Federationフロー全体図**:
   ```
   ユーザー → idp-server → 外部IdP認証 →
   トークン取得 → UserInfo取得 → ユーザー作成/更新 →
   idp-serverトークン発行
   ```

2. **http_requests機能の使い分けガイド**:
   ```
   | シナリオ | UserInfo方式 | 理由 |
   |---------|-------------|------|
   | 標準OIDC IdP | userinfo_endpoint | シンプル |
   | 複数API必要 | http_requests | 情報分散 |
   | カスタム認証 | http_requests + OAuth | 独自API |
   ```

3. **実践的な外部IdP例**:
   - 標準OIDC IdP連携の完全設定
   - エンタープライズIdP連携の完全設定
   - カスタムOIDC IdP連携

4. **パフォーマンス最適化**:
   - キャッシュ設定の推奨値
   - 複数API呼び出しの影響
   - タイムアウト設定

5. **テスト手順**:
   - Federation認証のエンドツーエンドテスト
   - UserInfoマッピングの確認方法

### 📈 総合評価

- **理解しやすさ**: ⭐⭐⭐☆☆ (3/5) - 段階的だが前提知識が多い
- **実用性**: ⭐⭐⭐⭐⭐ (5/5) - 実践的な高度な例が豊富
- **完全性**: ⭐⭐⭐⭐⭐ (5/5) - http_requests機能を網羅的に説明
- **初学者適合度**: ⭐⭐☆☆☆ (2/5) - 高度な内容が多く初学者には難しい

### 🎯 推奨される学習パス

**このドキュメントの位置づけ**: 中級～上級（基本設定理解後）

**推奨順序**:
1. [Tenant設定](./tenant.md) + [Client設定](./client.md) - 基本理解
2. [Mapping Functions](../04-implementation-guides/impl-20-mapping-functions.md) - マッピング基礎
3. [HttpRequestExecutor](../04-implementation-guides/impl-16-http-request-executor.md) - HTTP通信基礎
4. **このドキュメント** - Federation設定
5. [Federation実装ガイド](../03-application-plane/08-federation.md) - 実装詳細

### 📝 具体的改善案（優先度順）

#### 1. Federationフロー全体図（最優先）

```markdown
## Federationフローの仕組み

\`\`\`
┌──────────┐                 ┌──────────┐                ┌──────────┐
│  User    │                 │idp-server│                │ 外部IdP  │
└─────┬────┘                 └────┬─────┘                └────┬─────┘
      │                           │                           │
      │ 1. 認証開始                │                           │
      ├──────────────────────────>│                           │
      │                           │                           │
      │                           │ 2. 外部IdPへリダイレクト    │
      │<──────────────────────────┤                           │
      │                           │                           │
      │ 3. 外部IdPで認証           │                           │
      ├───────────────────────────────────────────────────────>│
      │                           │                           │
      │ 4. コールバック（code付き） │                           │
      │<───────────────────────────────────────────────────────┤
      │                           │                           │
      │ 5. コールバック転送        │                           │
      ├──────────────────────────>│                           │
      │                           │                           │
      │                           │ 6. Token取得               │
      │                           ├──────────────────────────>│
      │                           │<──────────────────────────┤
      │                           │                           │
      │                           │ 7. UserInfo取得            │
      │                           ├──────────────────────────>│
      │                           │<──────────────────────────┤
      │                           │                           │
      │                           │ 8. Userマッピング          │
      │                           │ (userinfo_mapping_rules)  │
      │                           │                           │
      │ 9. idp-serverトークン発行  │                           │
      │<──────────────────────────┤                           │
\`\`\`
```

#### 2. 最小構成から段階的に

```markdown
## 段階的な設定例

### ステップ1: 基本OIDC連携（最小構成）

**シナリオ**: 標準的なOIDC準拠外部IdPとの連携

\`\`\`json
{
  "type": "oidc",
  "sso_provider": "external-idp",
  "payload": {
    "issuer": "https://external-idp.example.com",
    "authorization_endpoint": "https://external-idp.example.com/oauth2/authorize",
    "token_endpoint": "https://external-idp.example.com/oauth2/token",
    "userinfo_endpoint": "https://external-idp.example.com/oauth2/userinfo",
    "client_id": "your-client-id",
    "client_secret": "your-client-secret",
    "redirect_uri": "http://localhost:8080/tenant-id/v1/federation/oidc/callback",
    "scopes_supported": ["openid", "profile", "email"],
    "userinfo_mapping_rules": [
      {"from": "$.sub", "to": "external_user_id"},
      {"from": "$.email", "to": "email"}
    ]
  }
}
\`\`\`

**注意**: OSSプロジェクトのため、実在するサービス名ではなく`external-idp.example.com`等の例示用ドメインを使用

### ステップ2: 複数API連続実行

**シナリオ**: UserInfoだけでは不足、追加情報が別APIにある

\`\`\`json
{
  "userinfo_execution": {
    "function": "http_requests",
    "http_requests": [
      {
        "url": "https://api.example.com/user/profile",
        "method": "GET"
      }
    ]
  },
  "userinfo_mapping_rules": [
    {"from": "$.userinfo_execution_http_requests[0].response_body.user_id", "to": "external_user_id"}
  ]
}
\`\`\`

### ステップ3: OAuth認証付きAPI呼び出し

**シナリオ**: 外部APIが独自のOAuth 2.0認証を要求

（既存の最高度パターンの例を参照）
```
