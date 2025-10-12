# Tenant設定ガイド

## このドキュメントの目的

Tenant（テナント）とAuthorization Serverの設定方法を理解します。

### 所要時間
⏱️ **約20分**

---

## Tenant設定とは

**Tenant**はマルチテナント環境における分離単位です。各テナントは独立したAuthorization Server設定を持ちます。

**設定内容**:
- テナント基本情報（ID、名前、ドメイン）
- Authorization Server設定（エンドポイント、サポート機能）
- トークン有効期限
- カスタムスコープ

---

## 設定ファイル構造

### tenant.json

```json
{
  "tenant": {
    "id": "${TENANT_ID}",
    "name": "Example Tenant",
    "domain": "${AUTHORIZATION_VIEW_URL}",
    "authorization_provider": "idp-server",
    "database_type": "postgresql",
    "attributes": {
      "cookie_name": "AUTH_SESSION",
      "use_secure_cookie": true,
      "allow_origins": ["https://app.example.com"],
      "signin_page": "/signin/",
      "security_event_log_persistence_enabled": true
    }
  },
  "authorization_server": {
    "issuer": "${AUTHORIZATION_SERVER_URL}/${TENANT_ID}",
    "authorization_endpoint": "${AUTHORIZATION_SERVER_URL}/${TENANT_ID}/v1/authorizations",
    "token_endpoint": "${AUTHORIZATION_SERVER_URL}/${TENANT_ID}/v1/tokens",
    "userinfo_endpoint": "${AUTHORIZATION_SERVER_URL}/${TENANT_ID}/v1/userinfo",
    "jwks_uri": "${AUTHORIZATION_SERVER_URL}/${TENANT_ID}/v1/jwks",
    "scopes_supported": [
      "openid",
      "profile",
      "email",
      "address",
      "phone"
    ],
    "grant_types_supported": [
      "authorization_code",
      "refresh_token",
      "client_credentials"
    ],
    "response_types_supported": [
      "code",
      "code id_token"
    ],
    "extension": {
      "access_token_duration": 3600,
      "refresh_token_duration": 86400,
      "id_token_duration": 3600,
      "authorization_code_valid_duration": 600,
      "oauth_authorization_request_expires_in": 1800
    }
  }
}
```

---

## 主要なフィールド

### Tenantセクション

| フィールド | 必須 | 説明 | 例 |
|-----------|------|------|---|
| `id` | ✅ | テナントID（UUID） | `18ffff8d-...` |
| `name` | ✅ | テナント名（最大255文字） | `Example Tenant` |
| `tenant_type` | ✅ | テナントタイプ | `BUSINESS` / `PERSONAL` |
| `domain` | ✅ | 認証画面のドメイン（URI） | `https://auth.example.com` |
| `description` | ❌ | テナント説明 | `説明文` |
| `authorization_provider` | ✅ | 認可プロバイダー（固定値） | `idp-server` |
| `database_type` | ✅ | データベースタイプ | `postgresql` / `mysql` |
| `attributes` | ❌ | テナント固有属性 | オブジェクト |

**OpenAPI仕様**: [swagger-control-plane-ja.yaml:4627-4665](../../../../documentation/openapi/swagger-control-plane-ja.yaml#L4627-L4665)

### Tenant Attributes

| フィールド | 説明 | デフォルト |
|-----------|------|----------|
| `cookie_name` | セッションCookie名 | `IDP_SESSION` |
| `use_secure_cookie` | Secure属性を付与 | `true` |
| `allow_origins` | CORS許可オリジン | `[]` |
| `signin_page` | サインインページパス | `/signin/` |
| `security_event_log_persistence_enabled` | イベントログ保存 | `true` |

---

### Authorization Serverセクション

#### 必須フィールド

| フィールド | 説明 | デフォルト |
|-----------|------|----------|
| `issuer` | Issuer識別子（HTTPS URL、クエリ/フラグメント不可） | - |
| `authorization_endpoint` | 認可エンドポイント（HTTPS URL） | - |
| `token_endpoint` | トークンエンドポイント | - |
| `jwks_uri` | JWKS URI（HTTPS URL） | - |
| `scopes_supported` | サポートするスコープ（`openid`必須） | - |
| `response_types_supported` | サポートするResponse Type（`code`, `id_token`必須） | - |
| `response_modes_supported` | サポートするResponse Mode | `["query", "fragment"]` |
| `subject_types_supported` | Subject識別子タイプ（`public`/`pairwise`） | - |

**OpenAPI仕様**: [swagger-control-plane-ja.yaml:3616-3627](../../../../documentation/openapi/swagger-control-plane-ja.yaml#L3616-L3627)

#### 推奨フィールド

| フィールド | 説明 | デフォルト |
|-----------|------|----------|
| `userinfo_endpoint` | UserInfoエンドポイント（HTTPS URL） | - |
| `registration_endpoint` | 動的クライアント登録エンドポイント | - |

#### オプショナルフィールド

| フィールド | 説明 | デフォルト |
|-----------|------|----------|
| `grant_types_supported` | サポートするGrant Type | `["authorization_code", "implicit"]` |
| `acr_values_supported` | サポートする認証コンテキストクラス | `[]` |
| `token_endpoint_auth_methods_supported` | Token Endpoint認証方式 | `["client_secret_basic"]` |
| `id_token_signing_alg_values_supported` | ID Token署名アルゴリズム（`RS256`必須） | - |
| `extension` | 拡張設定（トークン有効期限等） | - |

---

### Extension設定

| フィールド | 説明 | デフォルト値 | 単位 |
|-----------|------|------------|------|
| `access_token_duration` | Access Token有効期限 | 3600 | 秒 |
| `refresh_token_duration` | Refresh Token有効期限 | 86400 | 秒 |
| `id_token_duration` | ID Token有効期限 | 3600 | 秒 |
| `authorization_code_valid_duration` | Authorization Code有効期限 | 600 | 秒 |
| `oauth_authorization_request_expires_in` | AuthorizationRequest有効期限 | 1800 | 秒 |
| `custom_claims_scope_mapping` | カスタムClaimsマッピング有効化 | false | - |

**実装**: [AuthorizationServerExtensionConfiguration.java](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/configuration/AuthorizationServerExtensionConfiguration.java)

---

## カスタムスコープ

### 定義方法

`scopes_supported`にカスタムスコープを追加：

```json
{
  "scopes_supported": [
    "openid",
    "profile",
    "email",
    "identity_verification_application",
    "claims:custom_field",
    "claims:user_status"
  ]
}
```

### Claimsスコープパターン

`claims:` プレフィックスで、特定のClaimにアクセス：

```json
"claims:vip_status"     // VIPステータスにアクセス
"claims:verified_at"    // 確認日時にアクセス
"claims:account_type"   // アカウント種別にアクセス
```

**設定**: `extension.custom_claims_scope_mapping = true` で有効化

---

## Management APIで登録

### テナント作成

```bash
POST /v1/management/tenants
Content-Type: application/json

{
  "tenant": {
    "id": "18ffff8d-8d97-460f-a71b-33f2e8afd41e",
    "name": "Example Tenant",
    "domain": "https://auth.example.com",
    "authorization_provider": "idp-server",
    "database_type": "postgresql"
  },
  "authorization_server": {
    "issuer": "https://idp.example.com/18ffff8d-8d97-460f-a71b-33f2e8afd41e",
    "scopes_supported": ["openid", "profile", "email"],
    "grant_types_supported": ["authorization_code", "refresh_token"]
  }
}
```

### レスポンス

```json
{
  "dry_run": false,
  "result": {
    "tenant_id": "18ffff8d-8d97-460f-a71b-33f2e8afd41e",
    "created_at": "2025-10-13T10:00:00Z"
  }
}
```

---

## 環境変数プレースホルダー

### 使用方法

```json
{
  "id": "${TENANT_ID}",
  "issuer": "${AUTHORIZATION_SERVER_URL}/${TENANT_ID}",
  "domain": "${AUTHORIZATION_VIEW_URL}"
}
```

### 環境別の設定

```bash
# 開発環境
export TENANT_ID="dev-tenant-id"
export AUTHORIZATION_SERVER_URL="https://dev-idp.example.com"

# 本番環境
export TENANT_ID="prod-tenant-id"
export AUTHORIZATION_SERVER_URL="https://idp.example.com"
```

---

## よくある設定ミス

### ミス1: issuerの不一致

**エラー**:
```json
{
  "error": "invalid_issuer",
  "error_description": "issuer does not match token issuer"
}
```

**原因**: `issuer`が実際のURLと一致しない

**解決策**: `${AUTHORIZATION_SERVER_URL}/${TENANT_ID}` 形式を使用

### ミス2: カスタムスコープ未定義

**エラー**:
```json
{
  "error": "invalid_scope",
  "error_description": "scope 'claims:custom_field' is not supported"
}
```

**原因**: `scopes_supported`に未定義

**解決策**: `scopes_supported`に追加

---

## 次のステップ

✅ Tenant設定を理解した！

### 次に読むべきドキュメント

1. [Client設定](./client.md) - クライアント登録
2. [Authentication Policy](./authentication-policy.md) - 認証ポリシー設定

---

**最終更新**: 2025-10-13

---

## 📊 初学者向けドキュメント品質レビュー

**レビュー日**: 2025-01-15
**レビュー対象**: 初学者（idp-server開発経験なし、Java/Spring Boot基礎知識あり）

### ✅ 良い点

1. **完全な設定例**: コピペ可能な完全なtenant.json例
2. **フィールド説明の網羅**: 表形式で全フィールドを整理
3. **環境変数の説明**: プレースホルダーの使用方法が具体的
4. **エラー対処**: よくある設定ミスと解決策が実用的
5. **カスタムスコープの説明**: `claims:` パターンが明確
6. **デフォルト値の明記**: Extension設定のデフォルト値が全て記載

### ⚠️ 改善推奨事項

- [ ] **Tenantの概念説明**（重要度: 高）
  - 「Tenant」が何を意味するかの説明が不足
  - マルチテナントの利点・ユースケース
  - 1つのTenantで複数Clientを持つ関係性の図解

- [ ] **最小構成の例**（重要度: 高）
  - 最もシンプルな動作可能設定の提示
  - 必須フィールドのみの例を最初に提示

- [ ] **設定検証方法**（重要度: 高）
  - Tenant登録後の確認方法
  - `.well-known/openid-configuration` での確認手順

- [ ] **前提知識の明記**（重要度: 中）
  - OAuth 2.0/OIDCの基礎知識が必要と明示
  - Issuer, Scope, Grant Type等の用語理解が前提

- [ ] **アーキテクチャ図の追加**（重要度: 中）
  - Tenant → Client → User の関係図
  - Authorization Serverエンドポイントの全体図

- [ ] **実践的なシナリオ**（重要度: 中）
  - 「Webアプリケーション向けTenant設定」完全例
  - 「モバイルアプリ向けTenant設定」完全例

- [ ] **database_typeの詳細**（重要度: 低）
  - PostgreSQLとMySQLの違い・選択基準
  - 設定後の変更可否

### 💡 追加推奨コンテンツ

1. **Tenant設定の全体フロー**:
   ```
   Tenant作成 → 設定確認 → Client登録 → 認証テスト
   ```

2. **動作確認手順**:
   - Discovery Endpoint確認
   - JWKS URI確認
   - Token発行テスト

3. **セキュリティベストプラクティス**:
   - `use_secure_cookie: true` の重要性
   - CORS設定（allow_origins）の適切な設定
   - トークン有効期限の推奨値

4. **パフォーマンスチューニング**:
   - トークン有効期限とパフォーマンスの関係
   - refresh_token利用のメリット

5. **トラブルシューティング拡充**:
   - Tenant作成失敗時の確認ポイント
   - 環境変数が正しく展開されない場合
   - データベース接続エラー

### 📈 総合評価

- **理解しやすさ**: ⭐⭐⭐⭐☆ (4/5) - 表形式で整理されているが概念説明が弱い
- **実用性**: ⭐⭐⭐⭐⭐ (5/5) - 完全な設定例と実用的なエラー対処
- **完全性**: ⭐⭐⭐⭐⭐ (5/5) - 全フィールドを網羅し、デフォルト値も明記
- **初学者適合度**: ⭐⭐⭐⭐☆ (4/5) - Tenant概念の理解支援があれば5点に

### 🎯 推奨される学習パス

**このドキュメントの位置づけ**: 初級～中級（設定管理の最初に読むべき）

**推奨順序**:
1. [設定管理 Overview](./overview.md) - 設定全体像の把握
2. **このドキュメント** - Tenant設定の理解
3. [Client設定](./client.md) - クライアント登録
4. [Authentication Policy](./authentication-policy.md) - 認証ポリシー設定

### 📝 具体的改善案（優先度順）

#### 1. Tenantの概念説明（最優先）

```markdown
## Tenantとは

**Tenant（テナント）**は、マルチテナント環境における**完全に独立した認証・認可ドメイン**です。

### マルチテナントの例

\`\`\`
┌─────────────────────────────────────────┐
│ idp-server（1つのインスタンス）         │
│                                         │
│  ┌──────────────┐  ┌──────────────┐    │
│  │ Tenant A     │  │ Tenant B     │    │
│  │ (企業A用)    │  │ (企業B用)    │    │
│  ├──────────────┤  ├──────────────┤    │
│  │ - Client 1   │  │ - Client 1   │    │
│  │ - Client 2   │  │ - Client 2   │    │
│  │ - Users      │  │ - Users      │    │
│  └──────────────┘  └──────────────┘    │
└─────────────────────────────────────────┘
\`\`\`

### 分離されるもの

- ユーザーデータ（完全に分離）
- クライアント設定
- 認証ポリシー
- トークン有効期限
```

#### 2. 最小構成の例

```json
{
  "tenant": {
    "id": "test-tenant",
    "name": "Test Tenant",
    "domain": "http://localhost:8080",
    "authorization_provider": "idp-server",
    "database_type": "postgresql"
  },
  "authorization_server": {
    "issuer": "http://localhost:8080/test-tenant",
    "authorization_endpoint": "http://localhost:8080/test-tenant/v1/authorizations",
    "token_endpoint": "http://localhost:8080/test-tenant/v1/tokens",
    "userinfo_endpoint": "http://localhost:8080/test-tenant/v1/userinfo",
    "jwks_uri": "http://localhost:8080/test-tenant/v1/jwks",
    "scopes_supported": ["openid", "profile", "email"],
    "grant_types_supported": ["authorization_code", "refresh_token"],
    "response_types_supported": ["code"]
  }
}
```

**説明**: 最小限の設定で動作確認可能（ローカル開発環境用）

#### 3. 設定確認チェックリスト

```markdown
## Tenant設定確認チェックリスト

### 登録完了確認

- [ ] Management APIでTenant作成成功（200 OK）
- [ ] レスポンスで`tenant_id`が返却されている
- [ ] データベースに`tenant`レコードが保存されている

### Discovery Endpoint確認

\`\`\`bash
# .well-known/openid-configuration取得
curl http://localhost:8080/{tenant-id}/.well-known/openid-configuration
\`\`\`

確認項目:
- [ ] `issuer`が設定値と一致
- [ ] `authorization_endpoint`が正しく返却
- [ ] `scopes_supported`に設定したスコープが含まれる

### JWKS確認

\`\`\`bash
# JWKS取得
curl http://localhost:8080/{tenant-id}/v1/jwks
\`\`\`

- [ ] 公開鍵情報が返却される
```

#### 4. 実践的な設定例

```markdown
## シナリオ別設定例

### シナリオ1: Webアプリケーション向け

**要件**:
- Authorization Code Flow
- Refresh Token使用
- Access Token: 1時間
- Refresh Token: 7日間

\`\`\`json
{
  "grant_types_supported": ["authorization_code", "refresh_token"],
  "response_types_supported": ["code"],
  "extension": {
    "access_token_duration": 3600,
    "refresh_token_duration": 604800
  }
}
\`\`\`

### シナリオ2: モバイルアプリ向け

**要件**:
- Authorization Code Flow + PKCE
- 長期間のRefresh Token
- カスタムスコープ（プッシュ通知）

\`\`\`json
{
  "grant_types_supported": ["authorization_code", "refresh_token"],
  "response_types_supported": ["code"],
  "scopes_supported": [
    "openid", "profile", "email",
    "offline_access",
    "notifications:push"
  ],
  "extension": {
    "access_token_duration": 3600,
    "refresh_token_duration": 2592000
  }
}
\`\`\`
```
