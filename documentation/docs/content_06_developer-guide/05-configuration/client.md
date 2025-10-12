# Client設定ガイド

## このドキュメントの目的

OAuth 2.0/OIDCクライアント（Relying Party）の設定方法を理解します。

### 所要時間
⏱️ **約20分**

---

## Client設定とは

**Client**はOAuth 2.0/OIDCプロトコルでリソースにアクセスするアプリケーションです。

**設定内容**:
- クライアント認証情報（ID、Secret）
- リダイレクトURI
- 許可するGrant Type、Response Type
- スコープ
- CIBA設定（拡張機能）

---

## 設定ファイル構造

### clients/web-app.json

```json
{
  "client_id": "${CLIENT_ID}",
  "client_secret": "${CLIENT_SECRET}",
  "redirect_uris": [
    "https://app.example.com/callback",
    "https://app.example.com/silent-renew"
  ],
  "response_types": [
    "code",
    "code id_token"
  ],
  "grant_types": [
    "authorization_code",
    "refresh_token"
  ],
  "scope": "openid profile email",
  "client_name": "Example Web App",
  "token_endpoint_auth_method": "client_secret_basic",
  "application_type": "web",
  "extension": {
    "access_token_duration": 3600,
    "refresh_token_duration": 86400
  }
}
```

---

## 主要なフィールド

### 基本情報

| フィールド | 必須 | 説明 | 例 |
|-----------|------|------|---|
| `client_id` | ✅ | クライアントID（UUID推奨） | `web-app-prod` |
| `client_id_alias` | ❌ | クライアントIDエイリアス（最大255文字） | `web-app-alias` |
| `client_secret` | 条件付き | クライアントシークレット | `secret-xxx` |
| `client_name` | ❌ | クライアント名（表示用） | `Example Web App` |
| `application_type` | ✅ | アプリケーションタイプ | `web` / `native` |

**client_id_alias**:
- 用途: 人間が読みやすいクライアント識別子
- UUIDの`client_id`の代わりに使用可能
- 他のクライアントと重複不可

**client_secret必須条件**:
- `token_endpoint_auth_method`が`client_secret_*`の場合
- Confidential Clientの場合
- 複数クライアントで同じ値を使用不可

**OpenAPI仕様**: [swagger-control-plane-ja.yaml:4686-4702](../../../../documentation/openapi/swagger-control-plane-ja.yaml#L4686-L4702)

---

### リダイレクトURI

| フィールド | 必須 | 説明 |
|-----------|------|------|
| `redirect_uris` | ✅ | リダイレクトURI配列 |

**重要**:
- 完全一致が必須（パス、ポート、プロトコル）
- 複数登録可能
- フラグメント（`#`）は禁止

**例**:
```json
{
  "redirect_uris": [
    "https://app.example.com/callback",
    "http://localhost:3000/callback"
  ]
}
```

---

### Response Type / Grant Type

| フィールド | 説明 | 推奨値 |
|-----------|------|--------|
| `response_types` | 認可レスポンスタイプ | `["code", "code id_token"]` |
| `grant_types` | トークン発行方式 | `["authorization_code", "refresh_token"]` |

**主要なGrant Type**:
- `authorization_code` - Authorization Code Flow
- `refresh_token` - Refresh Token使用
- `client_credentials` - Client Credentials Flow
- `urn:openid:params:grant-type:ciba` - CIBA Flow

---

### クライアント認証方式

| フィールド | 説明 | デフォルト |
|-----------|------|----------|
| `token_endpoint_auth_method` | Token Endpoint認証方式 | `client_secret_basic` |

**サポートされる認証方式**:
- `client_secret_basic` - Basic認証
- `client_secret_post` - POSTボディでシークレット送信
- `client_secret_jwt` - JWT（HMAC）
- `private_key_jwt` - JWT（RSA/ECDSA）
- `none` - Public Client（シークレット不要）

---

### Scope

| フィールド | 説明 | 例 |
|-----------|------|---|
| `scope` | デフォルトスコープ | `openid profile email` |

**注意**: `scopes_supported`（Tenant設定）で定義されたスコープのみ使用可能

---

### メタデータURL（OIDC Dynamic Registration）

| フィールド | 必須 | 説明 | 形式 |
|-----------|------|------|------|
| `client_uri` | ❌ | クライアントのホームページURL | URI |
| `logo_uri` | ❌ | クライアントロゴのURL | URI |
| `policy_uri` | ❌ | プライバシーポリシーURL | URI |
| `tos_uri` | ❌ | 利用規約URL | URI |
| `contacts` | ❌ | 担当者メールアドレス配列 | 文字列配列 |

**用途**: 同意画面でエンドユーザーに表示

**例**:
```json
{
  "client_name": "Example Web App",
  "client_uri": "https://example.com",
  "logo_uri": "https://example.com/logo.png",
  "policy_uri": "https://example.com/privacy",
  "tos_uri": "https://example.com/terms",
  "contacts": ["support@example.com"]
}
```

---

### 暗号化設定（高度）

#### ID Token暗号化

| フィールド | 説明 | 対応値 |
|-----------|------|--------|
| `id_token_signed_response_alg` | ID Token署名アルゴリズム | `none`, `RS256`, `ES256`, `HS256` |
| `id_token_encrypted_response_alg` | ID Token暗号化アルゴリズム | `RSA1_5`, `A128KW` |
| `id_token_encrypted_response_enc` | ID Token暗号化エンコーディング | `A128CBC-HS256`, `A128GCM`, `A256GCM` |

#### UserInfo暗号化

| フィールド | 説明 | 対応値 |
|-----------|------|--------|
| `userinfo_signed_response_alg` | UserInfo署名アルゴリズム | `none`, `RS256`, `ES256`, `HS256` |
| `userinfo_encrypted_response_alg` | UserInfo暗号化アルゴリズム | `RSA1_5`, `A128KW` |
| `userinfo_encrypted_response_enc` | UserInfo暗号化エンコーディング | `A128CBC-HS256`, `A128GCM`, `A256GCM` |

#### Request Object暗号化

| フィールド | 説明 | 対応値 |
|-----------|------|--------|
| `request_object_signing_alg` | Request Object署名アルゴリズム | `none`, `RS256`, `ES256`, `HS256` |
| `request_object_encryption_alg` | Request Object暗号化アルゴリズム | `RSA1_5`, `A128KW` |
| `request_object_encryption_enc` | Request Object暗号化エンコーディング | `A128CBC-HS256`, `A128GCM`, `A256GCM` |

**注意**: 暗号化を使用する場合、`jwks_uri`または`jwks`の設定が必要

---

### JWKs設定

| フィールド | 必須 | 説明 |
|-----------|------|------|
| `jwks_uri` | 条件付き | クライアントの公開鍵セットURL（HTTPS必須） |
| `jwks` | 条件付き | クライアントの公開鍵セット（JSON） |

**使用ケース**:
- `private_key_jwt`認証方式使用時
- ID Token/UserInfo/Request Object暗号化使用時

**例**:
```json
{
  "token_endpoint_auth_method": "private_key_jwt",
  "jwks_uri": "https://app.example.com/.well-known/jwks.json"
}
```

または

```json
{
  "token_endpoint_auth_method": "private_key_jwt",
  "jwks": {
    "keys": [
      {
        "kty": "RSA",
        "kid": "client-key-1",
        "n": "...",
        "e": "AQAB"
      }
    ]
  }
}
```

---

### その他のOIDC設定

| フィールド | 説明 | デフォルト |
|-----------|------|----------|
| `subject_type` | Subject識別子タイプ | `public` / `pairwise` |
| `sector_identifier_uri` | Pairwise識別子計算用URI | - |
| `default_max_age` | デフォルト最大認証経過時間（秒） | - |
| `require_auth_time` | `auth_time` Claim必須フラグ | `false` |
| `default_acr_values` | デフォルトACR値 | - |
| `initiate_login_uri` | サードパーティログイン開始URI | - |
| `request_uris` | 事前登録されたRequest URI | - |

---

## Extension設定

クライアント固有の拡張設定。

### トークン有効期限のカスタマイズ

```json
{
  "extension": {
    "access_token_duration": 7200,
    "refresh_token_duration": 172800
  }
}
```

**デフォルト値**: Tenant設定の値を継承

---

### CIBA設定

```json
{
  "extension": {
    "default_ciba_authentication_interaction_type": "authentication-device-notification-no-action"
  }
}
```

**詳細**: [CIBA Flow実装ガイド](../03-application-plane/06-ciba-flow.md)

---

### Federation設定

クライアント固有の利用可能なFederation（外部IdP連携）を定義：

```json
{
  "extension": {
    "available_federations": [
      {
        "id": "external-idp-a",
        "type": "oidc",
        "sso_provider": "external-idp-a",
        "auto_selected": false
      },
      {
        "id": "external-idp-b",
        "type": "oidc",
        "sso_provider": "external-idp-b",
        "auto_selected": false
      }
    ]
  }
}
```

**注意**: OSSプロジェクトのため、実在するサービス名ではなく`external-idp-a`等の一般的な識別子を使用

**フィールド**:
| フィールド | 必須 | 説明 |
|-----------|------|------|
| `id` | ✅ | Federation設定ID |
| `type` | ✅ | フェデレーションタイプ（`oauth2`, `saml2`, `oidc`） |
| `sso_provider` | ❌ | SSOプロバイダー名 |
| `auto_selected` | ❌ | 自動選択フラグ（デフォルト: `false`） |

**用途**: このクライアントで利用可能な外部IdPを制限

**詳細**: [Federation設定ガイド](./federation.md)

---

### JWT Authorization Request (JAR)

```json
{
  "extension": {
    "supported_jar": true
  }
}
```

**デフォルト**: `false`

**用途**: Request Objectによる認可リクエスト送信をサポート

---

## Management APIで登録

### API エンドポイント

**組織レベルAPI**（推奨）:
```
POST /v1/management/organizations/{organization-id}/tenants/{tenant-id}/clients
```

**注意**: 現在の実装では組織レベルAPIのみが提供されています。

### クライアント登録

```bash
POST /v1/management/organizations/{organization-id}/tenants/{tenant-id}/clients
Content-Type: application/json

{
  "client_id": "web-app",
  "client_secret": "secret-xxx",
  "redirect_uris": ["https://app.example.com/callback"],
  "response_types": ["code"],
  "grant_types": ["authorization_code", "refresh_token"],
  "scope": "openid profile email",
  "token_endpoint_auth_method": "client_secret_basic",
  "application_type": "web"
}
```

### Dry Runで検証

Dry Runパラメータでバリデーションのみ実行：

```bash
POST /v1/management/organizations/{organization-id}/tenants/{tenant-id}/clients?dry_run=true
Content-Type: application/json

{
  "client_id": "web-app",
  "client_secret": "secret-xxx",
  "redirect_uris": ["https://app.example.com/callback"],
  "response_types": ["code"],
  "grant_types": ["authorization_code"],
  "scope": "openid profile",
  "application_type": "web"
}
```

**レスポンス**:
```json
{
  "dry_run": true,
  "result": {
    "validation_errors": [],
    "warnings": []
  }
}
```

---

## よくある設定ミス

### ミス1: redirect_uri不一致

**実行時エラー**:
```
GET /v1/authorizations?redirect_uri=https://app.example.com/wrong
→ エラー: redirect_uri does not match registered URIs
```

**原因**: 登録済みURIと完全一致しない

**解決策**: 登録したURIを正確に使用

### ミス2: 未サポートのgrant_type

**エラー**:
```json
{
  "error": "unauthorized_client",
  "error_description": "client is not authorized to use grant_type: password"
}
```

**原因**: `grant_types`に未登録

**解決策**: `grant_types`に追加

---

## 次のステップ

✅ Client設定を理解した！

### 次に読むべきドキュメント

1. [Authentication Policy](./authentication-policy.md) - 認証ポリシー設定
2. [Federation](./federation.md) - 外部IdP連携

---

**最終更新**: 2025-10-13

---

## 📊 初学者向けドキュメント品質レビュー

**レビュー日**: 2025-01-15
**レビュー対象**: 初学者（idp-server開発経験なし、Java/Spring Boot基礎知識あり）

### ✅ 良い点

1. **完全な設定例**: コピペ可能なclient設定例が充実
2. **フィールド説明の網羅**: 表形式で全フィールドを整理
3. **認証方式の説明**: 5つの認証方式を明確に列挙
4. **Dry Run機能**: 設定検証方法の紹介
5. **エラー対処**: よくある設定ミスと解決策が具体的
6. **条件付き必須の明記**: client_secretの必須条件を明示

### ⚠️ 改善推奨事項

- [ ] **Clientの概念説明**（重要度: 高）
  - 「Client」が何を意味するかの説明が不足
  - Confidential Client vs Public Clientの違い
  - Tenant-Client-Userの関係性図

- [ ] **application_typeの詳細**（重要度: 高）
  - `web` と `native` の違い・選択基準
  - それぞれに推奨される設定パターン

- [ ] **最小構成の例**（重要度: 高）
  - 最もシンプルなPublic Client例
  - 最もシンプルなConfidential Client例

- [ ] **実践的なシナリオ**（重要度: 高）
  - Webアプリケーション向け完全設定
  - SPAアプリケーション向け完全設定
  - モバイルアプリ向け完全設定

- [ ] **セキュリティガイダンス**（重要度: 中）
  - client_secretの安全な管理方法
  - redirect_urisのセキュリティベストプラクティス
  - Public Clientの制約事項

- [ ] **動作確認手順**（重要度: 中）
  - Client登録後の確認方法
  - Authorization Code Flowの実行テスト

- [ ] **PKCEの説明**（重要度: 中）
  - PKCEとは何か
  - どのようなクライアントで必須か

### 💡 追加推奨コンテンツ

1. **Client種別の比較表**:
   ```
   | 種別 | application_type | auth_method | redirect_uri | 例 |
   |------|-----------------|-------------|--------------|-----|
   | Web | web | client_secret_basic | https:// | サーバーサイドアプリ |
   | SPA | web | none | https:// | React/Vueアプリ |
   | Mobile | native | none | custom scheme | iOS/Androidアプリ |
   ```

2. **認証フローとの対応**:
   - Authorization Code Flow → 推奨Client設定
   - CIBA Flow → 推奨Client設定
   - Client Credentials Flow → 推奨Client設定

3. **トラブルシューティング拡充**:
   - Client認証失敗時の確認ポイント
   - scopeエラーの対処
   - redirect_uri検証失敗の詳細

4. **テスト用設定**:
   - ローカル開発用Client設定例
   - テスト環境用Client設定例

5. **設定変更の影響範囲**:
   - redirect_uris変更時の影響
   - grant_types変更時の影響

### 📈 総合評価

- **理解しやすさ**: ⭐⭐⭐⭐☆ (4/5) - 表形式で整理されているが概念説明が弱い
- **実用性**: ⭐⭐⭐⭐⭐ (5/5) - 完全な設定例とDry Run機能の紹介
- **完全性**: ⭐⭐⭐⭐⭐ (5/5) - 全フィールドを網羅
- **初学者適合度**: ⭐⭐⭐⭐☆ (4/5) - Client概念の理解支援があれば5点に

### 🎯 推奨される学習パス

**このドキュメントの位置づけ**: 初級～中級（Tenant設定後に読むべき）

**推奨順序**:
1. [設定管理 Overview](./overview.md) - 設定全体像
2. [Tenant設定](./tenant.md) - Tenant作成
3. **このドキュメント** - Client設定
4. [Authentication Policy](./authentication-policy.md) - 認証ポリシー
5. OAuth 2.0フロー実行・動作確認

### 📝 具体的改善案（優先度順）

#### 1. Clientの概念説明（最優先）

```markdown
## Clientとは

**Client（クライアント）**は、OAuth 2.0/OIDCプロトコルを使用してリソースにアクセスする**アプリケーション**です。

### Client種別

#### Confidential Client（機密クライアント）
- **特徴**: client_secretを安全に保管できる
- **例**: サーバーサイドWebアプリケーション
- **設定**: `token_endpoint_auth_method: client_secret_basic`

#### Public Client（公開クライアント）
- **特徴**: client_secretを安全に保管できない
- **例**: SPA（Single Page Application）、モバイルアプリ
- **設定**: `token_endpoint_auth_method: none`
- **要件**: PKCE必須

### Tenant-Client-Userの関係

\`\`\`
┌─────────────────────────────────────┐
│ Tenant (企業A)                      │
│                                     │
│  ┌──────────────┐  ┌─────────────┐ │
│  │ Client 1     │  │ Client 2    │ │
│  │ (Webアプリ)  │  │ (モバイル)  │ │
│  └──────┬───────┘  └──────┬──────┘ │
│         │                  │        │
│         └──────┬──────────┘        │
│                │                    │
│         ┌──────▼───────┐           │
│         │   Users      │           │
│         │ (従業員100名) │           │
│         └──────────────┘           │
└─────────────────────────────────────┘
\`\`\`
```

#### 2. シナリオ別設定例

```markdown
## シナリオ別Client設定

### シナリオ1: サーバーサイドWebアプリケーション

**要件**:
- Authorization Code Flow
- client_secret使用
- session管理

\`\`\`json
{
  "client_id": "web-app-server",
  "client_secret": "secret-xxx",
  "redirect_uris": ["https://app.example.com/callback"],
  "response_types": ["code"],
  "grant_types": ["authorization_code", "refresh_token"],
  "scope": "openid profile email",
  "token_endpoint_auth_method": "client_secret_basic",
  "application_type": "web"
}
\`\`\`

### シナリオ2: SPA（Single Page Application）

**要件**:
- Authorization Code Flow + PKCE
- client_secret不要（Public Client）
- 短いtoken有効期限

\`\`\`json
{
  "client_id": "spa-app",
  "redirect_uris": ["https://spa.example.com/callback"],
  "response_types": ["code"],
  "grant_types": ["authorization_code", "refresh_token"],
  "scope": "openid profile email",
  "token_endpoint_auth_method": "none",
  "application_type": "web",
  "extension": {
    "access_token_duration": 900,
    "refresh_token_duration": 3600
  }
}
\`\`\`

**重要**: PKCE必須（コード内でcode_verifier生成）

### シナリオ3: モバイルアプリ（iOS/Android）

**要件**:
- Authorization Code Flow + PKCE
- カスタムURLスキーム
- 長期間のrefresh_token

\`\`\`json
{
  "client_id": "mobile-app",
  "redirect_uris": [
    "com.example.app://callback",
    "https://app.example.com/mobile-callback"
  ],
  "response_types": ["code"],
  "grant_types": ["authorization_code", "refresh_token"],
  "scope": "openid profile email offline_access",
  "token_endpoint_auth_method": "none",
  "application_type": "native",
  "extension": {
    "access_token_duration": 3600,
    "refresh_token_duration": 2592000
  }
}
\`\`\`

### シナリオ4: バックエンドサービス（M2M通信）

**要件**:
- Client Credentials Flow
- ユーザー認証不要
- サービス間認証

\`\`\`json
{
  "client_id": "backend-service",
  "client_secret": "service-secret-xxx",
  "response_types": [],
  "grant_types": ["client_credentials"],
  "scope": "api:read api:write",
  "token_endpoint_auth_method": "client_secret_basic",
  "application_type": "web"
}
\`\`\`
```

#### 3. Client設定確認チェックリスト

```markdown
## Client設定確認チェックリスト

### 登録完了確認

- [ ] Management APIでClient登録成功（200 OK）
- [ ] レスポンスで`client_id`が返却されている
- [ ] データベースに`client_configuration`レコードが保存されている

### 設定内容確認

- [ ] `redirect_uris`が正確に登録されている
- [ ] `grant_types`が要件を満たしている
- [ ] `scope`がTenantの`scopes_supported`に含まれる
- [ ] `application_type`が適切（web/native）
- [ ] Public Clientの場合、`token_endpoint_auth_method: none`

### 動作確認

\`\`\`bash
# Authorization Code Flow開始
open "https://idp.example.com/{tenant-id}/v1/authorizations?
  client_id={client-id}&
  redirect_uri={redirect-uri}&
  response_type=code&
  scope=openid profile email"
\`\`\`

- [ ] 認証画面が表示される
- [ ] 認証後、redirect_uriにリダイレクトされる
- [ ] authorization_codeが取得できる
- [ ] Token Endpointでtokenが取得できる
```

#### 4. セキュリティチェックリスト

```markdown
## セキュリティチェックリスト

### Confidential Client（client_secret使用）

- [ ] client_secretは環境変数で管理
- [ ] client_secretはコードにハードコードしない
- [ ] HTTPSを使用（本番環境）
- [ ] redirect_urisは厳密に設定（ワイルドカード禁止）

### Public Client（SPA/モバイル）

- [ ] PKCE実装済み
- [ ] token_endpoint_auth_method: none
- [ ] access_token有効期限を短く設定（推奨: 15分～1時間）
- [ ] refresh_tokenローテーション実装推奨
- [ ] redirect_urisにlocalhost不可（本番環境）
```
