# トランザクションデータの有効期限設定

認可サーバーが管理するトランザクションデータの有効期限/持続時間に関する全設定を記載する。

全パラメータはテナントの `authorization_server.extension` JSONオブジェクトで設定する。

## パラメータ一覧

| パラメータ (JSONキー) | デフォルト | FAPI | 単位 | 制御対象 |
|---|---|---|---|---|
| `authorization_code_valid_duration` | 600 | 60 | 秒 | 認可コードの有効期限 |
| `access_token_duration` | 1800 | 300 | 秒 | アクセストークンの有効期限 |
| `refresh_token_duration` | 3600 | 2592000 | 秒 | リフレッシュトークンの有効期限 |
| `id_token_duration` | 3600 | 300 | 秒 | IDトークンの `exp` クレーム |
| `authorization_response_duration` | 60 | 60 | 秒 | JARM応答JWTの `exp` クレーム |
| `oauth_authorization_request_expires_in` | 1800 | 1800 | 秒 | PAR request_uriの有効期限 / 認証トランザクションの有効期限 |
| `default_max_age` | 86400 | 300 | 秒 | 認証の鮮度 (`max_age` のフォールバック) |
| `backchannel_authentication_request_expires_in` | 300 | 120 | 秒 | CIBAバックチャネル認証リクエストの有効期限 |
| `backchannel_authentication_polling_interval` | 5 | 5 | 秒 | CIBAポーリング間隔 |

## 詳細説明

### 認可コード (`authorization_code_valid_duration`)

- **デフォルト**: 600秒 (10分)
- **FAPI**: 60秒 (1分)
- **DBカラム**: `authorization_code_grant.expires_at`
- **設定元**: `OAuthAuthorizeContext.authorizationCodeGrantExpiresDateTime()`
- **検証箇所**: トークンエンドポイントでコード交換時に有効期限を検証
- **RFC**: RFC 6749 Section 4.1.2 - 短い有効期限を推奨 (最大10分)
- **クライアント別オーバーライド**: 不可

### アクセストークン (`access_token_duration`)

- **デフォルト**: 1800秒 (30分)
- **FAPI**: 300秒 (5分)
- **DBカラム**: `oauth_token.access_token_expires_at`
- **設定元**: `AccessTokenCreator.create()`
- **検証箇所**: `RefreshTokenVerifier.throwExceptionIfExpiredToken()`、リソースサーバーのイントロスペクション
- **RFC**: RFC 6749 Section 4.2.2
- **クライアント別オーバーライド**: 可 - `ClientConfiguration.accessTokenDuration` がサーバーデフォルトを上書き

### リフレッシュトークン (`refresh_token_duration`)

- **デフォルト**: 3600秒 (1時間)
- **FAPI**: 2592000秒 (30日)
- **DBカラム**: `oauth_token.refresh_token_expires_at` (nullable)
- **設定元**: `RefreshTokenCreatable.createRefreshToken()`
- **検証箇所**: `RefreshTokenVerifier.throwExceptionIfExpiredToken()`
- **RFC**: RFC 6749 Section 6
- **クライアント別オーバーライド**: 可 - `ClientConfiguration.refreshTokenDuration` がサーバーデフォルトを上書き

**関連パラメータ**:

| パラメータ | デフォルト | 説明 |
|---|---|---|
| `refresh_token_strategy` | `FIXED` | `FIXED`: 元の有効期限を維持。`EXTENDS`: 使用ごとに有効期限を延長 |
| `rotate_refresh_token` | `true` | 使用ごとに新しいリフレッシュトークンを発行するかどうか |

### IDトークン (`id_token_duration`)

- **デフォルト**: 3600秒 (1時間)
- **FAPI**: 300秒 (5分)
- **格納先**: JWTの `exp` クレーム (専用のDBカラムなし)
- **設定元**: `IdTokenCreator.createIdToken()`
- **検証箇所**: クライアント側でJWTの `exp` クレームを検証
- **RFC**: OIDC Core Section 3.1.3.3
- **クライアント別オーバーライド**: 不可

### 認可レスポンス - JARM (`authorization_response_duration`)

- **デフォルト**: 60秒 (1分)
- **FAPI**: 60秒
- **格納先**: JARM JWTの `exp` クレーム
- **設定元**: `JarmCreatable.createJarmResponse()`
- **検証箇所**: クライアント側で受信時にJWTの `exp` クレームを検証
- **仕様**: JWT Secured Authorization Response Mode (JARM)
- **クライアント別オーバーライド**: 不可
- **備考**: 署名付き認可レスポンスJWTの有効期限であり、内包する認可コードの有効期限ではない

### OAuth認可リクエスト / PAR (`oauth_authorization_request_expires_in`)

- **デフォルト**: 1800秒 (30分)
- **FAPI**: 1800秒 (デフォルトと同じ)
- **DBカラム**: `authorization_request.expires_at`、`authentication_transaction.expires_at`
- **設定元**: `NormalRequestFactory`、`RequestObjectPatternFactory`、`OAuthAuthenticationTransactionCreator.create()`
- **検証箇所**: PAR request_uriの取得時、認証トランザクションの有効期限チェック
- **RFC**: RFC 9126 Section 2.2 - PARレスポンスで `expires_in` として返却
- **クライアント別オーバーライド**: 不可

このパラメータは以下の2つを制御する:
1. **PAR request_uriの有効期限**: PARエンドポイントが返す `request_uri` の有効期間
2. **認証トランザクションの有効期限**: 認可エンドポイントにアクセス後、ユーザーが認証フローを完了するまでの制限時間

### デフォルト最大年齢 (`default_max_age`)

- **デフォルト**: 86400秒 (24時間)
- **FAPI**: 300秒 (5分)
- **格納先**: 認証リクエストコンテキスト
- **設定元**: `NormalRequestFactory`、`RequestObjectPatternFactory` (リクエストに `max_age` パラメータがない場合)
- **検証箇所**: 認証検証で `auth_time` + `max_age` > 現在時刻 を確認
- **RFC**: OIDC Core Section 3.1.2.1
- **クライアント別オーバーライド**: 不可 (`max_age` リクエストパラメータが優先)

### CIBAバックチャネル認証リクエスト (`backchannel_authentication_request_expires_in`)

- **デフォルト**: 300秒 (5分)
- **FAPI**: 120秒 (2分)
- **DBカラム**: `backchannel_authentication_request.expires_at`
- **設定元**: CIBAバックチャネル認証ハンドラー
- **検証箇所**: CIBAトークンエンドポイントで有効期限を検証
- **RFC**: CIBA Core Section 7.1
- **クライアント別オーバーライド**: 不可

### CIBAポーリング間隔 (`backchannel_authentication_polling_interval`)

- **デフォルト**: 5秒
- **FAPI**: 5秒
- **備考**: 有効期限ではなく、クライアントのポーリングリクエスト間の最小間隔。CIBA認証レスポンスで `interval` として返却される。
- **RFC**: CIBA Core Section 7.3

## クライアントレベルのオーバーライド

以下のパラメータは `ClientConfiguration` でクライアントごとに上書き可能:

| パラメータ | サーバーJSONキー | クライアントJSONキー |
|---|---|---|
| アクセストークンの有効期限 | `access_token_duration` | `access_token_duration` |
| リフレッシュトークンの有効期限 | `refresh_token_duration` | `refresh_token_duration` |

クライアントに設定された場合、クライアントの値がサーバーデフォルトより優先される。

## 認可リクエストライフサイクルの関連テーブル

認可リクエスト (`oauth_authorization_request_expires_in`) を起点とするフローでは、複数のテーブルにまたがってトランザクションデータが生成される。以下にフロー別の関連テーブルとその関係をまとめる。

### 認可コードフロー (Authorization Code Flow)

```
authorization_request (PAR/認可エンドポイントで作成)
├─ id = authorization_request_id
├─ expires_at ← oauth_authorization_request_expires_in
│
├─→ authentication_transaction (認証フロー開始時に作成)
│   ├─ authorization_id = authorization_request.id
│   ├─ expires_at ← oauth_authorization_request_expires_in
│   └─→ authentication_interactions (認証ステップごとに作成)
│       └─ authentication_transaction_id = authentication_transaction.id
│
├─→ authorization_code_grant (認可完了時に作成)
│   ├─ authorization_request_id = authorization_request.id
│   ├─ expires_at ← authorization_code_valid_duration
│   └─ トークンエンドポイントでコード交換後に削除
│
└─→ oauth_token (トークンエンドポイントで作成)
    ├─ access_token_expires_at ← access_token_duration
    ├─ refresh_token_expires_at ← refresh_token_duration
    └─ authorization_requestとの直接FKなし (大規模運用のため)
```

### CIBAフロー (Backchannel Authentication Flow)

```
backchannel_authentication_request (CIBAエンドポイントで作成)
├─ id = backchannel_authentication_request_id
├─ expires_at ← backchannel_authentication_request_expires_in
│
├─→ authentication_transaction (認証フロー開始時に作成)
│   ├─ authorization_id = null (CIBAでは認可リクエストなし)
│   ├─ expires_at ← oauth_authorization_request_expires_in
│   └─→ authentication_interactions
│
├─→ ciba_grant (ユーザー承認時に作成)
│   ├─ backchannel_authentication_request_id = backchannel_authentication_request.id
│   ├─ expires_at ← authorization_code_valid_duration
│   └─ status: PENDING → AUTHENTICATED / REJECTED
│
└─→ oauth_token (トークンエンドポイントで作成)
    ├─ access_token_expires_at ← access_token_duration
    └─ refresh_token_expires_at ← refresh_token_duration
```

### テーブル間の関係

| テーブル | 主キー | 関連テーブルへの参照 | 有効期限カラム | 制御パラメータ |
|---|---|---|---|---|
| `authorization_request` | `id` | - | `expires_at` | `oauth_authorization_request_expires_in` |
| `authentication_transaction` | `id` | `authorization_id` → `authorization_request.id` | `expires_at` | `oauth_authorization_request_expires_in` |
| `authentication_interactions` | (`authentication_transaction_id`, `interaction_type`) | `authentication_transaction_id` → `authentication_transaction.id` | - | 親テーブルに従う |
| `authorization_code_grant` | `authorization_request_id` | `authorization_request_id` → `authorization_request.id` | `expires_at` | `authorization_code_valid_duration` |
| `oauth_token` | `id` | FKなし (アプリケーション層で整合性管理) | `access_token_expires_at`, `refresh_token_expires_at` | `access_token_duration`, `refresh_token_duration` |
| `authorization_granted` | `id` | `tenant_id` + `client_id` + `user_id` で検索 | `revoked_at` (無効化時のみ) | 有効期限なし (永続的な同意) |
| `backchannel_authentication_request` | `id` | - | `expires_at` | `backchannel_authentication_request_expires_in` |
| `ciba_grant` | `backchannel_authentication_request_id` | → `backchannel_authentication_request.id` | `expires_at` | `authorization_code_valid_duration` |

### PAR利用時の注意事項

PARを利用する場合、同一の `request_uri` で認可エンドポイントに複数回アクセスされる可能性がある。この場合:

- `authorization_request` は同一レコードが再利用される (PARで事前に登録済み)
- `authentication_transaction` は認可エンドポイントアクセスのたびに新規作成される
- 同一の `authorization_id` で複数の `authentication_transaction` が存在するとクエリエラーになるため、PAR利用時はdelete-insertパターンで既存レコードを削除してから新規作成する

### 有効期限インデックス

全テーブルに `(tenant_id, expires_at)` のインデックスがあり、有効期限ベースのクリーンアップを効率的に実行できる。

| テーブル | インデックス名 |
|---|---|
| `authorization_request` | `idx_authorization_request_expires_at` |
| `authorization_code_grant` | `idx_auth_code_expires_at` |
| `oauth_token` | `idx_oauth_token_expires_at` |
| `authentication_transaction` | `idx_authentication_transaction_expires_at` |
| `backchannel_authentication_request` | `idx_bc_auth_request_expires_at` |
| `ciba_grant` | `idx_ciba_grant_expires_at` |

## FAPI設定に関する注意事項

金融グレードのデプロイでは、トークン窃取やリプレイ攻撃のリスクを低減するため、大幅に短い有効期限を使用する:

- 認可コード: 60秒 (デフォルト600秒) - FAPIは短命な認可コードを要求
- アクセストークン: 300秒 (デフォルト1800秒) - 窃取されたトークンの露出時間を制限
- IDトークン: 300秒 (デフォルト3600秒) - 最新のアイデンティティアサーションを保証
- デフォルト最大年齢: 300秒 (デフォルト86400秒) - 直近の認証を強制
- CIBAリクエスト: 120秒 (デフォルト300秒) - バックチャネル認証のウィンドウを短縮

`oauth_authorization_request_expires_in` (PAR request_uri) はFAPIでもデフォルトの1800秒を使用する。これはユーザーが認証を完了するまでの最大時間を表すものであり、セキュリティ上機密なトークンの有効期限ではないため。RFC 9126はPAR request_uriの最小有効期限として60秒を推奨している。
