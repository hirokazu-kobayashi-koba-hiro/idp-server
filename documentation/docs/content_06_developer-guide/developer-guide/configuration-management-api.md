# 設定管理API

このドキュメントでは、idp-serverの設定管理APIと、全設定タイプにわたる統一された有効/無効機能について説明します。

## 概要

設定管理APIは、idp-serverの様々な設定エンティティのCRUD操作を提供します。すべての設定タイプは、管理者が設定を削除することなく有効化・無効化できる統一された有効/無効メカニズムをサポートしています。

### サポートされている設定タイプ

- **クライアント設定** - OAuth 2.0/OpenID Connectクライアント設定
- **認可サーバー設定** - サーバー全体のOAuth/OIDC設定
- **フェデレーション設定** - 外部アイデンティティプロバイダー連携設定
- **認証設定** - 認証方式設定（パスワード、MFA等）
- **セキュリティイベントフック設定** - セキュリティイベント通知フック

## 有効/無効機能

### 基本概念

すべての設定は`Configurable`インターフェースを実装し、以下を提供します：

```java
public interface Configurable {
    boolean isEnabled();     // 設定が有効
    boolean exists();        // 設定がデータベースに存在
    default boolean isActive() { 
        return isEnabled() && exists(); 
    }
}
```

### データベーススキーマ

各設定テーブルには`enabled`列が含まれています：

```sql
-- 例: client_configuration テーブル
CREATE TABLE client_configuration (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    payload JSONB NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

### フィルタリング動作

#### 一般API（デフォルト動作）
- **有効な設定のみを返却** (`enabled = true`)
- 無効な設定は自動的にフィルタリングされます
- 認証フロー、トークン発行等で使用

#### 管理API（管理者アクセス）
- **`include_disabled`パラメータをサポート**し、無効な設定にもアクセス可能
- 無効な設定の更新・再有効化に必要
- 完全な管理制御を提供

## APIエンドポイント

### クライアント設定管理

#### ベースURL
```
/v1/management/tenants/{tenant_id}/clients
```

#### 操作

**クライアント作成**
```http
POST /v1/management/tenants/{tenant_id}/clients
Content-Type: application/json

{
  "client_id": "my-app",
  "client_name": "マイアプリケーション",
  "client_secret": "secret123",
  "grant_types": ["authorization_code"],
  "redirect_uris": ["https://app.example.com/callback"],
  "enabled": true
}
```

**クライアント一覧取得**
```http
GET /v1/management/tenants/{tenant_id}/clients
```
*デフォルトでは有効なクライアントのみ返却*

**クライアント取得**
```http
GET /v1/management/tenants/{tenant_id}/clients/{client_id}
```

**クライアント更新**
```http
PUT /v1/management/tenants/{tenant_id}/clients/{client_id}?include_disabled=true
Content-Type: application/json

{
  "client_id": "my-app",
  "client_name": "マイアプリケーション（更新済み）",
  "enabled": false
}
```
*無効なクライアントを更新するには`include_disabled=true`を使用*

**クライアント削除**
```http
DELETE /v1/management/tenants/{tenant_id}/clients/{client_id}
```

### 認証設定管理

#### ベースURL
```
/v1/management/tenants/{tenant_id}/authentication-configurations
```

#### 例: パスワード設定

**パスワード設定の作成**
```http
POST /v1/management/tenants/{tenant_id}/authentication-configurations
Content-Type: application/json

{
  "id": "550e8400-e29b-41d4-a716-446655440001",
  "type": "password",
  "payload": {
    "min_length": 8,
    "require_uppercase": true,
    "require_lowercase": true,
    "require_digits": true,
    "require_special_chars": false
  },
  "enabled": true
}
```

**設定の無効化**
```http
PUT /v1/management/tenants/{tenant_id}/authentication-configurations/{config_id}
Content-Type: application/json

{
  "id": "550e8400-e29b-41d4-a716-446655440001",
  "type": "password",
  "payload": {
    "min_length": 8,
    "require_uppercase": true,
    "require_lowercase": true,
    "require_digits": true,
    "require_special_chars": false
  },
  "enabled": false
}
```

**無効な設定の再有効化**
```http
PUT /v1/management/tenants/{tenant_id}/authentication-configurations/{config_id}?include_disabled=true
Content-Type: application/json

{
  "id": "550e8400-e29b-41d4-a716-446655440001",
  "type": "password",
  "payload": {
    "min_length": 10,
    "require_uppercase": true,
    "require_lowercase": true,
    "require_digits": true,
    "require_special_chars": true
  },
  "enabled": true
}
```

### フェデレーション設定管理

#### ベースURL
```
/v1/management/tenants/{tenant_id}/federation-configurations
```

#### 例: Google OAuth設定

```http
POST /v1/management/tenants/{tenant_id}/federation-configurations
Content-Type: application/json

{
  "id": "550e8400-e29b-41d4-a716-446655440002",
  "type": "oidc",
  "sso_provider": "google",
  "payload": {
    "client_id": "google-client-id",
    "client_secret": "google-client-secret",
    "issuer": "https://accounts.google.com",
    "authorization_endpoint": "https://accounts.google.com/o/oauth2/auth",
    "token_endpoint": "https://oauth2.googleapis.com/token",
    "userinfo_endpoint": "https://openidconnect.googleapis.com/v1/userinfo"
  },
  "enabled": true
}
```

### セキュリティイベントフック設定管理

#### ベースURL
```
/v1/management/tenants/{tenant_id}/security-event-hook-configurations
```

#### 例: Webhookフック

```http
POST /v1/management/tenants/{tenant_id}/security-event-hook-configurations
Content-Type: application/json

{
  "id": "550e8400-e29b-41d4-a716-446655440003",
  "type": "webhook",
  "payload": {
    "url": "https://api.example.com/security-events",
    "method": "POST",
    "headers": {
      "Content-Type": "application/json",
      "Authorization": "Bearer webhook-token"
    },
    "timeout": 5000
  },
  "execution_order": 1,
  "enabled": true
}
```

## パラメータ

### クエリパラメータ

| パラメータ | 型 | 説明 | デフォルト |
|-----------|------|-------------|---------|
| `include_disabled` | boolean | 結果に無効な設定も含める | `false` |
| `limit` | integer | 返却する結果の最大数 | `10` |
| `offset` | integer | ページネーションのためにスキップする結果数 | `0` |

### include_disabledの使用

`include_disabled`パラメータは管理操作において重要です：

```http
# 管理用に無効な設定を取得
GET /v1/management/tenants/{tenant_id}/clients/{client_id}?include_disabled=true

# 無効な設定を更新
PUT /v1/management/tenants/{tenant_id}/clients/{client_id}?include_disabled=true

# 無効な設定を削除
DELETE /v1/management/tenants/{tenant_id}/clients/{client_id}?include_disabled=true
```

## レスポンス形式

### 成功レスポンス
```json
{
  "client_id": "my-app",
  "client_name": "マイアプリケーション",
  "enabled": true,
  "created_at": "2025-01-01T00:00:00Z",
  "updated_at": "2025-01-01T00:00:00Z"
}
```

### 一覧レスポンス
```json
{
  "list": [
    {
      "client_id": "app1",
      "client_name": "アプリケーション1",
      "enabled": true
    },
    {
      "client_id": "app2", 
      "client_name": "アプリケーション2",
      "enabled": true
    }
  ],
  "total_count": 2,
  "limit": 10,
  "offset": 0
}
```

### エラーレスポンス
```json
{
  "error": "not_found",
  "error_description": "設定が見つかりません"
}
```

## ステータスコード

| コード | 説明 |
|------|-------------|
| `200` | 成功 |
| `201` | 作成完了 |
| `204` | コンテンツなし（削除成功） |
| `400` | 不正なリクエスト |
| `401` | 認証が必要 |
| `403` | アクセス拒否 |
| `404` | 見つからない |
| `500` | 内部サーバーエラー |

## ベストプラクティス

### 1. 削除ではなく無効化を使用

- 監査証跡のため、**設定を削除するのではなく無効化**する
- 無効化された設定は後で再有効化できる
- 参照整合性が維持される

### 2. 管理操作でのinclude_disabledの使用

- 管理的な更新を行う際は常に`include_disabled=true`を使用
- 無効な設定にアクセスするために必要

### 3. 設定変更のテスト

- 利用可能な場合は`dry_run=true`パラメータを使用して変更をプレビュー
- ステージング環境で設定更新をテスト

### 4. 設定ステータスの監視

- 監視システムで有効/無効ステータスを追跡
- 予期しない設定無効化に対するアラートを設定

## セキュリティ考慮事項

### 認証・認可

- すべての管理APIは有効なアクセストークンが必要
- オペレータは設定管理に適切な権限を持つ必要がある
- 最小権限の原則を使用

### 監査ログ

- すべての設定変更は自動的にログ記録される
- オペレータID、タイムスタンプ、変更詳細を含む
- 監査ログは設定履歴の追跡に役立つ

### 機密データ

- 設定ペイロードには機密情報が含まれる可能性がある
- 安全な保存と送信を使用
- 秘密情報を定期的にローテーション

## サンプルコード

### 完全なクライアント管理ワークフロー

```bash
# 1. クライアント作成
curl -X POST "https://idp.example.com/v1/management/tenants/my-tenant/clients" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "client_id": "test-app",
    "client_name": "テストアプリケーション",
    "client_secret": "secret123",
    "grant_types": ["authorization_code"],
    "redirect_uris": ["https://test.example.com/callback"],
    "enabled": true
  }'

# 2. クライアント一覧取得（有効なもののみ）
curl "https://idp.example.com/v1/management/tenants/my-tenant/clients" \
  -H "Authorization: Bearer $TOKEN"

# 3. クライアント無効化
curl -X PUT "https://idp.example.com/v1/management/tenants/my-tenant/clients/test-app" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "client_id": "test-app",
    "client_name": "テストアプリケーション",
    "client_secret": "secret123",
    "grant_types": ["authorization_code"],
    "redirect_uris": ["https://test.example.com/callback"],
    "enabled": false
  }'

# 4. クライアント再有効化（include_disabledが必要）
curl -X PUT "https://idp.example.com/v1/management/tenants/my-tenant/clients/test-app?include_disabled=true" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "client_id": "test-app",
    "client_name": "テストアプリケーション（再有効化）",
    "client_secret": "newsecret456", 
    "grant_types": ["authorization_code"],
    "redirect_uris": ["https://test.example.com/callback"],
    "enabled": true
  }'

# 5. クライアント削除
curl -X DELETE "https://idp.example.com/v1/management/tenants/my-tenant/clients/test-app" \
  -H "Authorization: Bearer $TOKEN"
```

### E2Eテスト実行

設定管理機能のE2Eテストを実行：

```bash
# すべての設定管理テストを実行
cd e2e
npm test -- --testPathPattern="control_plane.*management"

# 特定の設定タイプのテストを実行
npm test -- --testPathPattern="client_management"
npm test -- --testPathPattern="authentication_management" 
npm test -- --testPathPattern="federation_management"
npm test -- --testPathPattern="security_event_hook_management"
```

## 実装アーキテクチャ

### レイヤー構造

```
Management API Layer
    ↓ include_disabled parameter
Management Service Layer  
    ↓ findWithDisabled(tenant, id, true)
Repository Interface
    ↓ includeDisabled parameter
Repository DataSource Implementation
    ↓ SQL executor with conditional filtering
Database Layer
```

### 主要コンポーネント

- **Configurable Interface**: 統一された設定インターフェース
- **SQL Executors**: 条件付きフィルタリングロジック
- **Repository Pattern**: データアクセス抽象化
- **Management Services**: ビジネスロジック層

追加サポートについては、[APIリファレンス](/docs/content_07_reference/api-reference)を参照するか、開発チームにお問い合わせください。