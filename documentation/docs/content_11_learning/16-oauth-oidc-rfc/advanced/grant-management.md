# OAuth 2.0 Grant Management

Grant Management は、ユーザーが付与した認可（Grant）を照会・管理するための API を定義した仕様です。

---

## 第1部: 概要編

### Grant Management とは？

Grant Management は、エンドユーザーと RP が認可サーバーで管理される**認可（Grant）を照会・更新・削除**できる API です。

```
Grant のライフサイクル:

  ┌────────────┐      認可リクエスト      ┌────────────┐
  │     RP     │ ──────────────────────► │     AS     │
  │            │ ◄────────────────────── │            │
  └────────────┘     Grant 作成           └────────────┘
                          │
                          ▼
                   ┌─────────────┐
                   │    Grant    │
                   │ - sub       │
                   │ - client_id │
                   │ - scope     │
                   │ - claims    │
                   └─────────────┘
                          │
       ┌──────────────────┼──────────────────┐
       ▼                  ▼                  ▼
    照会(GET)          更新(PATCH)        削除(DELETE)
```

### なぜ Grant Management が必要なのか？

| 課題 | Grant Management による解決 |
|------|----------------------------|
| ユーザーが認可状況を把握できない | 付与した認可を一覧表示 |
| 認可の取り消しが困難 | API で簡単に取り消し |
| 段階的な認可ができない | 既存の Grant を更新 |
| GDPR 等の規制対応 | ユーザーに制御権を提供 |

### 主要なユースケース

```
1. ユーザー向けダッシュボード
   「このアプリに何を許可していますか？」

2. RP による認可状況の確認
   「ユーザーはどのスコープを許可していますか？」

3. 段階的な認可
   最初: scope=openid email
   後で: scope=openid email profile payment

4. 認可の取り消し
   ユーザーが特定アプリへの認可を削除
```

---

## 第2部: 詳細編

### Grant ID

Grant はユニークな識別子（Grant ID）で識別されます。

```
トークンレスポンスに Grant ID が含まれる:

{
  "access_token": "eyJhbGciOiJSUzI1NiIs...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "refresh_token": "tGzv3JOkF0XG5Qx2TlKWIA",
  "grant_id": "PTRWWMo_YsGxl17r6MBj5"
}
```

### Grant の照会（GET）

```http
GET /grants/PTRWWMo_YsGxl17r6MBj5 HTTP/1.1
Host: auth.example.com
Authorization: Bearer eyJhbGciOiJSUzI1NiIs...
```

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "grant_id": "PTRWWMo_YsGxl17r6MBj5",
  "status": "active",
  "client_id": "s6BhdRkqt3",
  "created_at": "2024-01-01T00:00:00Z",
  "updated_at": "2024-01-15T00:00:00Z",
  "expires_at": "2024-07-01T00:00:00Z",
  "scope": "openid profile email",
  "claims": {
    "userinfo": {
      "name": null,
      "email": {
        "essential": true
      }
    }
  },
  "authorization_details": [
    {
      "type": "account_information",
      "actions": ["read"],
      "accounts": [
        {
          "iban": "DE89370400440532013000"
        }
      ]
    }
  ]
}
```

### Grant の一覧（GET）

```http
GET /grants HTTP/1.1
Host: auth.example.com
Authorization: Bearer eyJhbGciOiJSUzI1NiIs...
```

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "grants": [
    {
      "grant_id": "PTRWWMo_YsGxl17r6MBj5",
      "client_id": "s6BhdRkqt3",
      "client_name": "Banking App",
      "status": "active",
      "scope": "openid profile email",
      "created_at": "2024-01-01T00:00:00Z"
    },
    {
      "grant_id": "abc123def456",
      "client_id": "another_client",
      "client_name": "Payment App",
      "status": "active",
      "scope": "openid payment",
      "created_at": "2024-01-10T00:00:00Z"
    }
  ]
}
```

### Grant の更新（認可の拡張）

既存の Grant に新しいスコープを追加する場合。

```http
POST /par HTTP/1.1
Host: auth.example.com
Content-Type: application/x-www-form-urlencoded

grant_id=PTRWWMo_YsGxl17r6MBj5
&scope=openid profile email payment
&grant_management_action=merge
&...
```

| アクション | 説明 |
|-----------|------|
| `create` | 新しい Grant を作成（デフォルト） |
| `replace` | 既存の Grant を置換 |
| `merge` | 既存の Grant に追加 |

### Grant の削除（DELETE）

```http
DELETE /grants/PTRWWMo_YsGxl17r6MBj5 HTTP/1.1
Host: auth.example.com
Authorization: Bearer eyJhbGciOiJSUzI1NiIs...
```

```http
HTTP/1.1 204 No Content
```

**効果**:
- 関連するすべてのアクセストークンが無効化
- 関連するすべてのリフレッシュトークンが無効化
- Grant が削除

### ディスカバリーメタデータ

```json
{
  "issuer": "https://auth.example.com",
  "grant_management_endpoint": "https://auth.example.com/grants",
  "grant_management_actions_supported": ["create", "replace", "merge"]
}
```

### 段階的認可のフロー

```
段階的認可の例:

1. 初回認可
   scope=openid profile
   → Grant 作成（grant_id: ABC123）

2. 追加認可が必要になった時
   grant_id=ABC123
   grant_management_action=merge
   scope=openid profile payment

3. ユーザーに追加の同意を求める
   「このアプリが決済機能へのアクセスを求めています」

4. 同意後、Grant が更新
   Grant ABC123:
     scope=openid profile payment

5. 新しいアクセストークンを発行
   スコープ: openid profile payment
```

### 認可サーバーの実装考慮事項

### セキュリティ考慮事項

| 項目 | 推奨事項 |
|------|----------|
| アクセス制御 | Grant は所有者（subject）のみアクセス可能 |
| トークンスコープ | Grant 管理には特別なスコープが必要 |
| 監査ログ | すべての操作を記録 |
| レート制限 | 一覧取得に制限を設定 |
| 同意の再確認 | Grant 拡張時はユーザー同意を再取得 |

---

## 参考リンク

- [Grant Management for OAuth 2.0](https://openid.bitbucket.io/fapi/fapi-grant-management.html)
- [FAPI 2.0 Security Profile](https://openid.bitbucket.io/fapi/fapi-2_0-security-profile.html)
- [RFC 9396 - Rich Authorization Requests](https://datatracker.ietf.org/doc/html/rfc9396)
