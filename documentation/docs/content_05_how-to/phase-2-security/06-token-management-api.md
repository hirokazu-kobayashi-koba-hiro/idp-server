# トークン管理API運用ガイド

## このドキュメントの目的

**トークン管理APIとGrant管理APIの違いを理解し、運用シーンに応じた使い分けができるようになる**ことが目標です。

### 学べること

✅ **トークン管理API vs Grant管理API**
- 操作対象と粒度の違い
- 副作用の違い（何が削除されるか）
- 運用シーンごとの使い分け

✅ **トークン管理APIの実践的な使い方**
- 不正アクセス時の即座失効
- ユーザー退職時の全トークン削除
- dry_runによる事前確認

### 所要時間
⏱️ **約10分**

### 前提条件
- [トークン管理の概念](../../content_03_concepts/04-tokens-claims/concept-02-token-management.md)を理解している
- 管理API（Control Plane）の基本的な認証・認可を理解している

---

## トークン管理API vs Grant管理API

idp-serverには、トークンに関連する2つの管理APIがあります。

### 操作対象の違い

| 観点 | トークン管理API | Grant管理API |
|:---|:---|:---|
| **操作対象** | `oauth_token`テーブルの個別レコード | `authorization_granted`テーブルのGrantレコード |
| **粒度** | トークン単位（1発行 = 1レコード） | ユーザー×クライアント単位の認可記録 |
| **参照できる情報** | トークンメタデータ（ID、client_id、scopes、有効期限等） | 同意済みスコープ、認可日時等 |
| **トークン値の公開** | しない（セキュリティ上非公開） | N/A |

### 削除時の副作用

```
トークン管理API: DELETE /tokens/{id}
└─→ 対象のoauth_tokenレコードを物理削除
    ├─ アクセストークン → 即座に無効化（introspectionでactive=false）
    └─ リフレッシュトークン → 同レコード内のため同時に無効化
    ※ Grantは削除されない（ユーザーの認可同意は維持）

Grant管理API: DELETE /grants/{id}
└─→ 対象のGrant + 関連する全トークンを削除
    ├─ authorization_grantedレコードを削除
    └─ 該当ユーザー×クライアントの全oauth_tokenレコードを一括削除
    ※ ユーザーの認可同意が取り消されるため、再ログイン時に同意画面が表示される
```

### 使い分けの判断基準

| 運用シーン | 推奨API | 理由 |
|:---|:---|:---|
| **特定トークンの即座失効** | トークン管理API | 対象を絞って最小限の影響で失効できる |
| **ユーザーの全トークン一括失効** | トークン管理API | Grant（認可同意）を維持したまま全セッションを切れる |
| **ユーザーのアプリ連携解除** | Grant管理API | 認可同意ごと削除し、再認可を要求する |
| **不正クライアントの全アクセス遮断** | Grant管理API | 該当クライアントへのGrant削除で関連トークンも一括削除 |
| **トークン発行状況の監査** | トークン管理API | 一覧取得でフィルタリング・ページネーション対応 |
| **トークン棚卸し（有効期限確認）** | トークン管理API | 有効期限付きメタデータを確認できる |

---

## エンドポイント一覧

### システムレベル

| メソッド | パス | 概要 |
|:---|:---|:---|
| GET | `/v1/management/tenants/{id}/tokens` | トークン一覧取得 |
| GET | `/v1/management/tenants/{id}/tokens/{id}` | トークン詳細取得 |
| DELETE | `/v1/management/tenants/{id}/tokens/{id}` | トークン個別失効 |
| DELETE | `/v1/management/tenants/{id}/users/{id}/tokens` | ユーザー全トークン失効 |

### 組織レベル

上記のシステムレベルAPIと同じ操作を、組織スコープで提供します。

パス: `/v1/management/organizations/{orgId}/tenants/{tenantId}/...`

### 必要な権限

| 権限 | 操作 |
|:---|:---|
| `idp:token:read` | 一覧取得、詳細取得 |
| `idp:token:delete` | 個別失効、ユーザー全トークン失効 |

---

## フィルタリング

一覧取得APIでは **`user_id` または `client_id` のどちらか1つ以上のフィルタが必須**です。大量データでのパフォーマンス劣化を防ぐため、フィルタなしのリクエストは `400 Bad Request` を返します。

| パラメータ | 型 | 説明 | 例 |
|:---|:---|:---|:---|
| `user_id` | UUID | ユーザーIDで絞り込み | `?user_id=3ec055a8-...` |
| `client_id` | string | クライアントIDで絞り込み | `?client_id=my-app` |
| `grant_type` | string | grant_typeで絞り込み | `?grant_type=authorization_code` |
| `from` | datetime | 作成日時の開始 | `?from=2026-03-01 00:00:00` |
| `to` | datetime | 作成日時の終了 | `?to=2026-03-31 23:59:59` |
| `expired` | boolean | 期限切れトークンを含める（デフォルト: false） | `?expired=true` |
| `limit` | integer | 最大件数（デフォルト: 20、最大: 1000） | `?limit=50` |
| `offset` | integer | 開始位置（デフォルト: 0） | `?offset=20` |

---

## 運用ユースケース

### 1. 不正アクセスの検知と対応

特定ユーザーのアカウントが侵害された疑いがある場合：

```bash
# Step 1: 該当ユーザーのトークン一覧を確認
GET /v1/management/tenants/{tenantId}/tokens?user_id={userId}

# Step 2: dry_runで影響範囲を確認
DELETE /v1/management/tenants/{tenantId}/users/{userId}/tokens?dry_run=true
# → {"dry_run": true, "user_id": "...", "affected_count": 5}

# Step 3: 全トークンを失効
DELETE /v1/management/tenants/{tenantId}/users/{userId}/tokens
```

### 2. 従業員退職時のアクセス剥奪

```bash
# Step 1: ユーザーの全トークンを即座に失効（セッション切断）
DELETE /v1/management/tenants/{tenantId}/users/{userId}/tokens

# Step 2: 認可同意も取り消す場合はGrant管理APIも使用
DELETE /v1/management/tenants/{tenantId}/grants/{grantId}
```

### 3. 特定クライアントのトークン調査

```bash
# 特定クライアントが発行したトークンの一覧
GET /v1/management/tenants/{tenantId}/tokens?client_id=suspicious-app&limit=100

# 期限切れを含めて全履歴を確認
GET /v1/management/tenants/{tenantId}/tokens?client_id=suspicious-app&expired=true&limit=100
```

### 4. トークン棚卸し

```bash
# 直近1ヶ月に発行されたトークンを確認
GET /v1/management/tenants/{tenantId}/tokens?from=2026-02-18 00:00:00&limit=100

# client_credentials（M2M）トークンの一覧
GET /v1/management/tenants/{tenantId}/tokens?grant_type=client_credentials
```

---

## レスポンス例

### トークン一覧

```json
{
  "list": [
    {
      "id": "283d1547-f8c4-4c50-9d33-188bb4e93bca",
      "tenant_id": "4c4ee0fd-bcb2-4a3c-9fcf-ffc2da535e37",
      "user_id": "b48b908c-6f48-4e43-9418-3378dd389c5a",
      "client_id": "my-web-app",
      "grant_type": "authorization_code",
      "scopes": "openid profile email",
      "token_type": "Bearer",
      "access_token_expires_at": "2026-03-18T07:19:39",
      "has_refresh_token": true,
      "refresh_token_expires_at": "2026-03-19T06:19:39",
      "created_at": "2026-03-18T06:19:39"
    }
  ],
  "total_count": 1,
  "limit": 20,
  "offset": 0
}
```

### dry_run削除（個別）

```json
{
  "dry_run": true,
  "target": {
    "id": "283d1547-f8c4-4c50-9d33-188bb4e93bca",
    "tenant_id": "4c4ee0fd-bcb2-4a3c-9fcf-ffc2da535e37",
    "user_id": "b48b908c-6f48-4e43-9418-3378dd389c5a",
    "client_id": "my-web-app",
    "grant_type": "authorization_code",
    "scopes": "openid profile email",
    "token_type": "Bearer",
    "access_token_expires_at": "2026-03-18T07:19:39",
    "has_refresh_token": true,
    "created_at": "2026-03-18T06:19:39"
  }
}
```

### dry_run削除（ユーザー全トークン）

```json
{
  "dry_run": true,
  "user_id": "b48b908c-6f48-4e43-9418-3378dd389c5a",
  "affected_count": 5
}
```

---

## 大量データに関する注意事項

- **`total_count`の上限**: 一覧取得APIの`total_count`は最大**1,000,001**で打ち止めになります。100万件を超えるレコードが存在する場合、厳密な件数は返しません。これはCOUNTクエリのパフォーマンス劣化を防ぐための設計です（セキュリティイベントAPIと同じ方式）
- **フィルタの活用**: 大量のトークンが存在するテナントでは、`user_id`や`client_id`でフィルタリングして対象を絞り込むことを推奨します
- **期限切れトークン**: デフォルトでは期限切れトークンは一覧に含まれません（`expired=false`）。期限切れトークンは定期バッチで削除されるため、通常は有効なトークンのみ確認すれば十分です

## セキュリティに関する注意事項

- **トークン値は非公開**: 管理APIのレスポンスにアクセストークン値・リフレッシュトークン値は含まれません。`has_refresh_token`フラグで有無のみ確認できます
- **物理削除**: トークン管理APIの削除は論理削除ではなく物理削除です。削除後は復元できません
- **キャッシュ**: `TOKEN_CACHE_ENABLED=true`の環境では、トークン削除後もキャッシュTTL（デフォルト60秒）の間はイントロスペクションで`active=true`が返る可能性があります。管理APIによる削除はキャッシュを削除しないため、即座失効が必要な場合は識別子型トークンの利用を推奨します

## 関連ドキュメント

- [トークン管理の概念](../../content_03_concepts/04-tokens-claims/concept-02-token-management.md) - トークンの種類・形式・ライフサイクル
- [トークン有効期限パターン](02-token-strategy.md) - 有効期限の設計と設定
- [トークン管理API仕様書（OpenAPI）](../../content_07_reference/cp-token-api-ja) - エンドポイント詳細仕様
- [Grant管理API仕様書（OpenAPI）](../../content_07_reference/api-grant-management-ja) - Grant管理エンドポイント詳細仕様
