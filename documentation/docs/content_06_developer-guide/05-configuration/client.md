# Client設定ガイド（開発者向け）

## 📍 このドキュメントの位置づけ

**対象読者**: Phase 1（how-to 01-05）完了済みの開発者

**このドキュメントで学べること**:
- 本番運用に向けた詳細なClient設定
- ユースケース別の設定パターン
- 高度な機能（暗号化、CIBA、Federation等）
- セキュリティとパフォーマンスのベストプラクティス

**How-toガイドとの違い**:

| ドキュメント | 目的 | 内容 |
|------------|------|------|
| **How-to** | 最小構成で動かす | 実践的な手順（動作確認重視） |
| **Developer Guide** | 本番設定を理解する | 詳細仕様と設計パターン |

**前提知識**:
- [how-to-03: クライアント登録](../../content_05_how-to/how-to-04-client-registration.md)完了
- OAuth 2.0/OpenID Connectの基礎理解

---

## 🧭 Client概念の理解

**Client（クライアント）**は、OAuth 2.0/OIDCプロトコルを使用してリソースにアクセスする**アプリケーション**です。

クライアントには以下の2種類があります：
- **Confidential Client（機密クライアント）**: `client_secret`を安全に保管できる（例：サーバーサイドWebアプリ）
- **Public Client（公開クライアント）**: `client_secret`を保管できない（例：SPA、モバイルアプリ）

**詳細な説明は専用コンセプトドキュメントを参照**:
- [📖 Concept 19: Client](../../content_03_concepts/01-foundation/concept-03-client.md) - Client種別、認証方法の詳細、Tenant-Client-User関係図、セキュリティベストプラクティス

---

## 📖 API仕様リファレンス

Client登録・更新のAPI詳細仕様（リクエスト/レスポンススキーマ、全パラメータ説明）は、OpenAPI仕様書を参照してください。

**📖 OpenAPI仕様書**:
- [swagger-control-plane-ja.yaml](../../openapi/swagger-control-plane-ja.yaml) - 日本語版
- [swagger-control-plane-en.yaml](../../openapi/swagger-control-plane-en.yaml) - 英語版

---

## 🎯 シナリオ別設定例

実際のユースケースに応じたClient設定例を紹介します。

| # | シナリオ | ユースケース | 主なポイント | 詳細 |
|---|---------|------------|------------|------|
| 1 | **Webアプリケーション（標準）** | サーバーサイドアプリがユーザー認証を行い、バックエンドでトークンを安全に管理する | • client_secret_basic<br/>• Authorization Code Flow<br/>• Refresh Token | [詳細](#1-webアプリケーション標準) |
| 2 | **SPA（Single Page App）** | ブラウザのみで動作するアプリがユーザー認証を行い、短命トークンで安全性を確保する | • PKCE必須<br/>• token_auth: none<br/>• 短いAccess Token | [詳細](#2-spasingle-page-app) |
| 3 | **モバイルアプリ（iOS/Android）** | モバイルユーザーが長期間（30日）ログインを維持し、アプリを快適に利用する | • PKCE必須<br/>• カスタムURLスキーム<br/>• 長期Refresh Token | [詳細](#3-モバイルアプリiosandroid) |
| 4 | **M2M（Machine-to-Machine）** | バックエンドサービスがユーザー認証なしで他のサービスのAPIにアクセスする | • Client Credentials Flow<br/>• redirect_uri不要<br/>• カスタムスコープ | [詳細](#4-m2mmachine-to-machine) |
| 5 | **金融グレード（FAPI）** | 銀行システムが最高レベルのセキュリティでユーザー取引情報にアクセスする | • private_key_jwt / mTLS<br/>• 短いAccess Token<br/>• PAR必須 | [詳細](#5-金融グレードfapi) |

---

## 📋 シナリオ詳細設定

### 1. Webアプリケーション（標準）

**要件**:
- Authorization Code Flow
- client_secret使用
- Refresh Token対応

**ユースケース**: 一般的なサーバーサイドWebアプリケーション

<details>
<summary>設定JSON例を表示</summary>

```json
{
  "client_id": "web-app-client",
  "client_secret": "your-secret-here",
  "client_name": "My Web Application",
  "redirect_uris": [
    "https://app.example.com/callback",
    "http://localhost:3000/callback"
  ],
  "response_types": ["code"],
  "grant_types": ["authorization_code", "refresh_token"],
  "scope": "openid profile email",
  "token_endpoint_auth_method": "client_secret_basic",
  "application_type": "web",
  "extension": {
    "access_token_duration": 3600,
    "refresh_token_duration": 86400
  }
}
```

</details>

**設定ポイント**:
- `token_endpoint_auth_method: "client_secret_basic"`: HTTP Basic認証（最も一般的）
- `grant_types`: `authorization_code`, `refresh_token`の2つ
- `extension.access_token_duration: 3600`: 1時間（標準的）

---

### 2. SPA（Single Page App）

**要件**:
- Authorization Code Flow + PKCE
- client_secret不要
- 短いトークン有効期限

**ユースケース**: React/Vue/Angularなどのブラウザアプリ

<details>
<summary>設定JSON例を表示</summary>

```json
{
  "client_id": "spa-client",
  "client_name": "My SPA Application",
  "redirect_uris": [
    "https://spa.example.com/callback",
    "https://spa.example.com/silent-renew"
  ],
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
```

</details>

**設定ポイント**:
- `token_endpoint_auth_method: "none"`: Public Client（PKCE必須）
- `extension.access_token_duration: 900`: 15分（セキュリティ重視）
- `extension.refresh_token_duration: 3600`: 1時間（短め）

**重要**: 実装時にcode_challenge/code_verifierを必ず使用

---

### 3. モバイルアプリ（iOS/Android）

**要件**:
- Authorization Code Flow + PKCE
- カスタムURLスキーム
- 長期間のRefresh Token

**ユースケース**: iOSアプリ、Androidアプリ

<details>
<summary>設定JSON例を表示</summary>

```json
{
  "client_id": "mobile-app-client",
  "client_name": "My Mobile App",
  "redirect_uris": [
    "com.example.myapp://callback",
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
```

</details>

**設定ポイント**:
- `application_type: "native"`: モバイルアプリ専用
- `redirect_uris`: カスタムURLスキーム（`com.example.myapp://`）
- `extension.refresh_token_duration: 2592000`: 30日間
- `scope`: `offline_access`で長期トークン取得

---

### 4. M2M（Machine-to-Machine）

**要件**:
- Client Credentials Flow
- ユーザー認証不要
- サービス間認証

**ユースケース**: バックエンドサービス、APIゲートウェイ

<details>
<summary>設定JSON例を表示</summary>

```json
{
  "client_id": "backend-service",
  "client_secret": "service-secret-here",
  "client_name": "Backend Service",
  "redirect_uris": [],
  "response_types": [],
  "grant_types": ["client_credentials"],
  "scope": "api:read api:write",
  "token_endpoint_auth_method": "client_secret_basic",
  "application_type": "web"
}
```

</details>

**設定ポイント**:
- `grant_types: ["client_credentials"]`: M2M専用
- `redirect_uris: []`: リダイレクト不要
- `response_types: []`: 認可コード不要
- `scope`: カスタムスコープ（`api:read`, `api:write`等）

---

### 5. 金融グレード（FAPI）

**要件**:
- FAPI 1.0 Advanced Profile準拠
- 強力なクライアント認証（Private Key JWT, mTLS）
- PAR（Pushed Authorization Request）

**ユースケース**: オンラインバンキング、証券取引

<details>
<summary>設定JSON例を表示</summary>

```json
{
  "client_id": "banking-client",
  "client_name": "Online Banking System",
  "redirect_uris": [
    "https://banking.example.com/callback"
  ],
  "response_types": ["code"],
  "grant_types": ["authorization_code", "refresh_token"],
  "scope": "openid profile email openbanking:accounts openbanking:payments",
  "token_endpoint_auth_method": "private_key_jwt",
  "application_type": "web",
  "jwks_uri": "https://banking.example.com/.well-known/jwks.json",
  "require_pushed_authorization_requests": true,
  "extension": {
    "access_token_duration": 600,
    "refresh_token_duration": 3600
  }
}
```

</details>

**設定ポイント**:
- `token_endpoint_auth_method: "private_key_jwt"`: 秘密鍵署名（最高セキュリティ）
- `jwks_uri`: クライアント公開鍵の配置場所
- `require_pushed_authorization_requests: true`: PAR必須
- `extension.access_token_duration: 600`: 10分（短い有効期限）

**FAPI準拠の利点**:
- 金融機関レベルのセキュリティ
- 国際標準への準拠
- 監査対応の容易さ

---

## ⚙️ 高度な設定

### Extension設定の詳細

`extension`オブジェクトには、idp-server固有の拡張設定を含めます。

#### トークン有効期限のカスタマイズ

```json
{
  "extension": {
    "access_token_duration": 7200,
    "refresh_token_duration": 172800,
    "id_token_duration": 1800,
    "refresh_token_strategy": "EXTENDS",
    "rotate_refresh_token": false
  }
}
```

**デフォルト値**: Tenant設定の値を継承

| フィールド | 説明 | 設定値 |
|-----------|------|--------|
| `access_token_duration` | アクセストークンの有効期限（秒） | 正の整数 |
| `refresh_token_duration` | リフレッシュトークンの有効期限（秒） | 正の整数 |
| `id_token_duration` | IDトークンの有効期限（秒） | 正の整数 |
| `refresh_token_strategy` | リフレッシュトークンの有効期限戦略 | `"FIXED"` / `"EXTENDS"` |
| `rotate_refresh_token` | リフレッシュトークンのローテーション有無 | `true` / `false` |

すべてオプションで、未設定時はTenant設定にフォールバックします。

#### アプリケーション種類別の推奨値

| アプリ種類 | Access Token | Refresh Token | 理由 |
|----------|-------------|--------------|------|
| **モバイルアプリ** | 1時間（3600秒） | 30日（2592000秒） | バランス型 |
| **SPA** | 15分（900秒） | 1時間（3600秒） | 短命推奨 |
| **Webアプリ** | 1時間（3600秒） | 1日（86400秒） | 標準的 |
| **管理画面** | 15分（900秒） | 1時間（3600秒） | 高セキュリティ |
| **M2M** | 1時間（3600秒） | - | Refresh不要 |

---

### CIBA設定

CIBA（Client Initiated Backchannel Authentication）対応のクライアント設定。

```json
{
  "grant_types": [
    "authorization_code",
    "refresh_token",
    "urn:openid:params:grant-type:ciba"
  ],
  "extension": {
    "default_ciba_authentication_interaction_type": "authentication-device-notification-no-action"
  }
}
```

**CIBA認証インタラクションタイプ**:

| タイプ | 意味 | ユーザー操作 |
|-------|------|-----------|
| `authentication-device-notification` | プッシュ通知 | デバイスで承認 |
| `authentication-device-notification-no-action` | プッシュ通知（自動承認） | 操作不要 |
| `poll` | ポーリング | 別途ログイン |

**詳細**: [CIBA Flow実装ガイド](../03-application-plane/06-ciba-flow.md)

#### CIBA RAR必須化

CIBAリクエスト時に、authorization_details（RAR: Rich Authorization Requests）を必須とする設定：

```json
{
  "extension": {
    "ciba_require_rar": true
  },
  "authorization_details_types": ["transaction"]
}
```

**フィールド説明**:
- `ciba_require_rar`: CIBAリクエスト時に`authorization_details`パラメータを必須とするか
- `authorization_details_types`: サポートするauthorization detailsのタイプ

**動作**:
- `ciba_require_rar: true`の場合、CIBAリクエストに`authorization_details`が含まれていないとエラー
- トランザクション詳細（金額、送金先等）をユーザーに明示的に提示して承認を得る

**使用シーン**:
- 金融トランザクション（送金、振込等）の承認
- 高リスク操作の詳細確認
- FAPI準拠のトランザクション承認

**参照仕様**:
- [RFC 9396: OAuth 2.0 Rich Authorization Requests](https://www.rfc-editor.org/rfc/rfc9396.html)

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

**フィールド**:

| フィールド | 必須 | 説明 |
|-----------|------|------|
| `id` | ✅ | Federation設定ID |
| `type` | ✅ | フェデレーションタイプ（`oauth2`, `saml2`, `oidc`） |
| `sso_provider` | ❌ | SSOプロバイダー名 |
| `auto_selected` | ❌ | 自動選択フラグ（デフォルト: `false`） |

**用途**: このクライアントで利用可能な外部IdPを制限

---

### 暗号化設定

#### ID Token暗号化

```json
{
  "id_token_signed_response_alg": "RS256",
  "id_token_encrypted_response_alg": "RSA-OAEP",
  "id_token_encrypted_response_enc": "A128GCM"
}
```

**サポートされるアルゴリズム**:

| 用途 | アルゴリズム |
|------|------------|
| 署名 | `RS256`, `ES256`, `HS256`, `none` |
| 暗号化 | `RSA-OAEP`, `RSA1_5`, `A128KW` |
| エンコーディング | `A128GCM`, `A256GCM`, `A128CBC-HS256` |

**注意**: 暗号化を使用する場合、`jwks_uri`または`jwks`の設定が必要

---

### JWKs設定

クライアントの公開鍵セットを指定：

**方法1: jwks_uri**
```json
{
  "token_endpoint_auth_method": "private_key_jwt",
  "jwks_uri": "https://app.example.com/.well-known/jwks.json"
}
```

**方法2: jwks（直接指定）**
```json
{
  "token_endpoint_auth_method": "private_key_jwt",
  "jwks": {
    "keys": [
      {
        "kty": "RSA",
        "kid": "client-key-1",
        "use": "sig",
        "n": "...",
        "e": "AQAB"
      }
    ]
  }
}
```

**使用ケース**:
- `private_key_jwt`認証方式使用時
- ID Token/UserInfo/Request Object暗号化使用時

---

### その他のOIDC設定

| フィールド | 説明 | デフォルト |
|-----------|------|----------|
| `subject_type` | Subject識別子タイプ | `public` / `pairwise` |
| `sector_identifier_uri` | Pairwise識別子計算用URI | - |
| `default_max_age` | デフォルト最大認証経過時間（秒） | - |
| `require_auth_time` | `auth_time` Claim必須フラグ | `false` |
| `default_acr_values` | デフォルトACR値 | - |
| `request_uris` | 事前登録されたRequest URI | - |

---

## 🛠️ 運用ノウハウ

### トークン有効期限の選択

#### セキュリティとUXのトレードオフ

| 設定 | セキュリティ | UX | 推奨 |
|------|------------|----|----|
| **短い有効期限** | 高い | 頻繁な再認証で低い | 金融・管理画面 |
| **長い有効期限** | 低い | 便利 | モバイルアプリ |

**推奨設定**:
- **Access Token**: 15分〜1時間
- **Refresh Token**: 1時間〜30日（用途による）

---

### Refresh Tokenの活用

```json
{
  "extension": {
    "access_token_duration": 900,       // 15分（短め）
    "refresh_token_duration": 2592000   // 30日（長め）
  }
}
```

**メリット**:
- Access Tokenは短くしてセキュリティ確保
- Refresh Tokenで頻繁な再認証を回避
- ユーザー体験とセキュリティの両立

---

### セキュリティベストプラクティス

#### 1. クライアント認証方式の適切な選択

| 用途 | 推奨方式 |
|-----|---------|
| Webアプリケーション | `client_secret_basic` |
| SPA / モバイル | `none`（PKCE必須） |
| 金融グレード | `private_key_jwt`, `tls_client_auth` |

#### 2. redirect_urisの厳密な設定

```json
// ✅ 正しい
"redirect_uris": ["https://app.example.com/callback"]

// ❌ 間違い（開発用URLを本番に残す）
"redirect_uris": [
  "https://app.example.com/callback",
  "http://localhost:3000/callback"
]
```

#### 3. 最小権限の原則

```json
// ✅ 必要なスコープのみ
"scope": "openid profile email"

// ❌ 全部許可（危険）
"scope": "openid profile email phone address update delete admin"
```

#### 4. client_secretの安全な管理 (RFC 6749 Section 2.3.1, 10.1)

- 環境変数で管理（コードにハードコードしない）
- HTTPSで送信（平文送信禁止）
- 漏洩が疑われる場合は即座に無効化

---

### トラブルシューティング

#### 問題1: redirect_uri不一致

**エラー**:
```json
{
  "error": "invalid_request",
  "error_description": "redirect_uri does not match registered URIs"
}
```

**原因**: Authorization Requestの`redirect_uri`が登録URIに含まれていない

**解決策**: 完全一致を確認（末尾スラッシュ、プロトコル、ポート）

#### 問題2: unsupported_grant_type

**エラー**:
```json
{
  "error": "unsupported_grant_type",
  "error_description": "grant_type 'refresh_token' is not allowed"
}
```

**原因**: `grant_types`に未登録

**解決策**: `grant_types`配列に追加

#### 問題3: invalid_scope

**エラー**:
```json
{
  "error": "invalid_scope",
  "error_description": "Requested scope 'admin' is not allowed"
}
```

**原因**: スコープがクライアントまたはテナントで未定義

**解決策**: クライアントの`scope`またはテナントの`scopes_supported`に追加

---

## 📚 リファレンス（付録）

### 全フィールド一覧表

#### 基本情報

| フィールド | 必須 | 説明 | 例 |
|-----------|------|------|---|
| `client_id` | ✅ | クライアントID（UUID形式必須） | `550e8400-e29b-41d4-a716-446655440000` |
| `client_id_alias` | ❌ | クライアントIDエイリアス | `web-app-alias` |
| `client_secret` | 条件付き | クライアントシークレット | `secret-xxx` |
| `client_name` | ❌ | クライアント名 | `Example Web App` |
| `application_type` | ✅ | アプリケーションタイプ | `web` / `native` |

**client_secret必須条件**:
- `token_endpoint_auth_method`が`client_secret_*`の場合
- Confidential Clientの場合

#### OAuth/OIDC設定

| フィールド | 必須 | 説明 | デフォルト |
|-----------|------|------|----------|
| `redirect_uris` | ✅ | リダイレクトURI配列 | - |
| `response_types` | ✅ | レスポンスタイプ | - |
| `grant_types` | ✅ | グラントタイプ | - |
| `scope` | ❌ | デフォルトスコープ | - |
| `token_endpoint_auth_method` | ❌ | トークンエンドポイント認証方式 | `client_secret_basic` |

**OpenAPI仕様**: [swagger-control-plane-ja.yaml:4686-4702](../../openapi/swagger-control-plane-ja.yaml#L4686-L4702)

---

### デフォルト値一覧

#### Extension設定デフォルト値

| フィールド | デフォルト値 | 単位 |
|-----------|------------|------|
| `access_token_duration` | Tenant設定を継承 | 秒 |
| `refresh_token_duration` | Tenant設定を継承 | 秒 |
| `id_token_duration` | Tenant設定を継承 | 秒 |
| `refresh_token_strategy` | Tenant設定を継承 | - |
| `rotate_refresh_token` | Tenant設定を継承 | - |

---

### 実装クラスへのリンク

**Core**:
- [ClientConfiguration.java](../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/configuration/client/ClientConfiguration.java)

**Control Plane**:
- [ClientManagementRegistrationContextCreator.java](../../../libs/idp-server-control-plane/src/main/java/org/idp/server/control_plane/management/client/ClientManagementRegistrationContextCreator.java)

---

## 次のステップ

✅ Client設定を理解した！

### 次に読むべきドキュメント

1. [Authentication Policy](./authentication-policy.md) - 認証ポリシーとMFA設定
3. [Federation](./federation.md) - 外部IdP連携

---

**最終更新**: 2025-10-14
