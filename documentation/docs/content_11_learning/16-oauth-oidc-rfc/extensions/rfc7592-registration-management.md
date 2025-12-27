# RFC 7592: OAuth 2.0 動的クライアント登録管理

RFC 7592 は、動的に登録されたクライアントの設定を読み取り・更新・削除するためのプロトコルを定義した仕様です。

---

## 第1部: 概要編

### クライアント登録管理とは？

RFC 7591（動的クライアント登録）で登録されたクライアントを、その後**管理**するための仕様です。

```
RFC 7591: 登録（Create）
RFC 7592: 管理（Read / Update / Delete）

┌──────────────────────────────────────────────────────────┐
│                 クライアントライフサイクル                  │
├──────────────────────────────────────────────────────────┤
│                                                          │
│   ┌────────┐    ┌────────┐    ┌────────┐    ┌────────┐  │
│   │ Create │ ─► │  Read  │ ─► │ Update │ ─► │ Delete │  │
│   │(7591)  │    │(7592)  │    │(7592)  │    │(7592)  │  │
│   └────────┘    └────────┘    └────────┘    └────────┘  │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

### Registration Access Token

クライアント登録時に発行される特別なトークンで、そのクライアントの設定を管理するために使用します。

```
登録時:
  POST /register
  → client_id, client_secret, registration_access_token, registration_client_uri

管理時:
  GET/PUT/DELETE {registration_client_uri}
  Authorization: Bearer {registration_access_token}
```

### 操作一覧

| 操作 | HTTP メソッド | 説明 |
|------|--------------|------|
| 読み取り | GET | クライアント設定の取得 |
| 更新 | PUT | クライアント設定の更新 |
| 削除 | DELETE | クライアントの削除 |

---

## 第2部: 詳細編

### クライアント設定の読み取り（GET）

登録済みクライアントの現在の設定を取得します。

#### リクエスト

```http
GET /register/s6BhdRkqt3 HTTP/1.1
Host: auth.example.com
Authorization: Bearer reg-token-abc123
```

#### 成功レスポンス

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "client_id": "s6BhdRkqt3",
  "client_secret": "cf136dc3c1fc93f31185e5885805d",
  "client_id_issued_at": 1704067200,
  "client_secret_expires_at": 1735689600,
  "redirect_uris": [
    "https://client.example.com/callback"
  ],
  "client_name": "My Application",
  "token_endpoint_auth_method": "client_secret_basic",
  "grant_types": ["authorization_code", "refresh_token"],
  "response_types": ["code"],
  "scope": "openid profile email",
  "registration_access_token": "reg-token-abc123",
  "registration_client_uri": "https://auth.example.com/register/s6BhdRkqt3"
}
```

**注意**: `client_secret` が含まれるのは、認可サーバーがシークレットを保存している場合のみです。

### クライアント設定の更新（PUT）

クライアントの設定を更新します。

#### リクエスト

```http
PUT /register/s6BhdRkqt3 HTTP/1.1
Host: auth.example.com
Authorization: Bearer reg-token-abc123
Content-Type: application/json

{
  "client_id": "s6BhdRkqt3",
  "client_secret": "cf136dc3c1fc93f31185e5885805d",
  "redirect_uris": [
    "https://client.example.com/callback",
    "https://client.example.com/callback2"
  ],
  "client_name": "My Updated Application",
  "token_endpoint_auth_method": "client_secret_basic",
  "grant_types": ["authorization_code", "refresh_token"],
  "response_types": ["code"],
  "scope": "openid profile email offline_access"
}
```

#### 重要なルール

```
更新リクエストの要件:

1. client_id は必須
   └── リクエストボディに含める

2. 完全置換
   └── パッチではなく、全メタデータを送信
   └── 送信しないフィールドはデフォルト値にリセット

3. 読み取り専用フィールド
   ├── client_id_issued_at（変更不可）
   ├── registration_access_token（認可サーバーが更新可能）
   └── registration_client_uri（変更不可）

4. client_secret の扱い
   ├── 含める → そのまま維持
   └── 含めない → 新しいシークレットが発行される可能性
```

#### 成功レスポンス

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "client_id": "s6BhdRkqt3",
  "client_secret": "cf136dc3c1fc93f31185e5885805d",
  "client_id_issued_at": 1704067200,
  "client_secret_expires_at": 1735689600,
  "redirect_uris": [
    "https://client.example.com/callback",
    "https://client.example.com/callback2"
  ],
  "client_name": "My Updated Application",
  "token_endpoint_auth_method": "client_secret_basic",
  "grant_types": ["authorization_code", "refresh_token"],
  "response_types": ["code"],
  "scope": "openid profile email offline_access",
  "registration_access_token": "reg-token-xyz789",
  "registration_client_uri": "https://auth.example.com/register/s6BhdRkqt3"
}
```

**注意**: `registration_access_token` が更新される場合があります。クライアントは新しいトークンを保存する必要があります。

### クライアントの削除（DELETE）

クライアントを削除（登録解除）します。

#### リクエスト

```http
DELETE /register/s6BhdRkqt3 HTTP/1.1
Host: auth.example.com
Authorization: Bearer reg-token-abc123
```

#### 成功レスポンス

```http
HTTP/1.1 204 No Content
```

#### 削除の影響

```
クライアント削除時:
  1. クライアント設定が削除される
  2. 発行済みのアクセストークンは？
     └── 認可サーバーのポリシー次第
     └── 即座に無効化することを推奨
  3. 発行済みのリフレッシュトークンは？
     └── 無効化される
  4. registration_access_token は？
     └── 無効化される
```

### エラーレスポンス

#### 401 Unauthorized

```http
HTTP/1.1 401 Unauthorized
WWW-Authenticate: Bearer error="invalid_token"
```

Registration Access Token が無効または期限切れの場合。

#### 403 Forbidden

```http
HTTP/1.1 403 Forbidden
Content-Type: application/json

{
  "error": "access_denied",
  "error_description": "The client is not allowed to perform this operation"
}
```

トークンは有効だが、操作が許可されていない場合。

#### 400 Bad Request

```http
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
  "error": "invalid_client_metadata",
  "error_description": "The redirect_uri is not valid"
}
```

更新リクエストのメタデータが不正な場合。

### Registration Access Token のローテーション

セキュリティのため、認可サーバーは操作ごとに新しい Registration Access Token を発行できます。

```
トークンローテーションのフロー:

リクエスト:
  PUT /register/client123
  Authorization: Bearer old-token

レスポンス:
  {
    "client_id": "client123",
    ...
    "registration_access_token": "new-token"  ← 新しいトークン
  }

クライアントは new-token を保存し、次回から使用
old-token は無効化される
```


### セキュリティ考慮事項

| 項目 | 推奨事項 |
|------|----------|
| トークンの保護 | Registration Access Token を安全に保管 |
| トークンローテーション | 操作ごとにトークンを更新 |
| HTTPS | 管理エンドポイントは TLS 必須 |
| client_id の検証 | URL パスと body の client_id が一致することを確認 |
| 監査ログ | すべての管理操作を記録 |
| レート制限 | ブルートフォース攻撃対策 |

### Registration Access Token の紛失

```
トークンを紛失した場合:

1. 認可サーバーに連絡
   └── 管理者が新しいトークンを発行

2. client_secret で認証（認可サーバーがサポートする場合）
   └── 非標準の機能

3. 再登録
   └── 新しい client_id が発行される
   └── 既存のトークンは無効化

ベストプラクティス:
  - Registration Access Token を安全にバックアップ
  - トークンをシークレット管理システムで管理
```

---

## 参考リンク

- [RFC 7592 - OAuth 2.0 Dynamic Client Registration Management Protocol](https://datatracker.ietf.org/doc/html/rfc7592)
- [RFC 7591 - OAuth 2.0 Dynamic Client Registration Protocol](https://datatracker.ietf.org/doc/html/rfc7591)
- [OpenID Connect Dynamic Client Registration 1.0](https://openid.net/specs/openid-connect-registration-1_0.html)
