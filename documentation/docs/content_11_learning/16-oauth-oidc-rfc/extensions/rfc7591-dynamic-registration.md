# RFC 7591: OAuth 2.0 動的クライアント登録

RFC 7591 は、OAuth 2.0 クライアントをプログラムで動的に登録するためのプロトコルを定義した仕様です。

---

## 第1部: 概要編

### 動的クライアント登録とは？

従来のクライアント登録は**手動**で行われていました。動的クライアント登録は、この処理を**自動化**します。

```
従来の登録（手動）:
  1. 管理者がコンソールにログイン
  2. クライアント情報を入力
  3. client_id と client_secret を取得
  4. 開発者に連絡

動的登録（自動）:
  ┌──────────┐       POST /register       ┌──────────────┐
  │ クライアント │ ──────────────────────► │  認可サーバー  │
  │           │      クライアント情報       │              │
  │           │ ◄────────────────────── │              │
  └──────────┘   client_id, secret 等    └──────────────┘
```

### なぜ動的登録が必要なのか？

| シナリオ | 説明 |
|---------|------|
| マイクロサービス | サービス間通信のクライアントを自動登録 |
| SaaS プラットフォーム | テナントごとにクライアントを自動作成 |
| モバイルアプリ | インストール時にクライアントを登録 |
| 開発環境 | 開発者が即座にクライアントを取得 |
| フェデレーション | 外部 IdP との連携を自動化 |

### 登録エンドポイント

```
POST /register HTTP/1.1
Host: auth.example.com
Content-Type: application/json
Authorization: Bearer initial_access_token

{
  "redirect_uris": ["https://client.example.com/callback"],
  "client_name": "My Application",
  "token_endpoint_auth_method": "client_secret_basic"
}
```

---

## 第2部: 詳細編

### クライアントメタデータ

#### 必須/推奨メタデータ

| メタデータ | 必須 | 説明 |
|-----------|------|------|
| `redirect_uris` | ✅ | リダイレクト URI の配列 |
| `token_endpoint_auth_method` | △ | トークンエンドポイントの認証方式 |
| `grant_types` | △ | 使用するグラントタイプ |
| `response_types` | △ | 使用するレスポンスタイプ |
| `client_name` | △ | クライアント名 |
| `client_uri` | △ | クライアントのホームページ URL |
| `logo_uri` | △ | ロゴ画像の URL |
| `scope` | △ | 使用可能なスコープ |
| `contacts` | △ | 連絡先メールアドレス |
| `tos_uri` | △ | 利用規約 URL |
| `policy_uri` | △ | プライバシーポリシー URL |
| `jwks_uri` | △ | JWK Set URL |
| `jwks` | △ | JWK Set（インライン） |
| `software_id` | △ | ソフトウェア識別子 |
| `software_version` | △ | ソフトウェアバージョン |

#### 認証方式（token_endpoint_auth_method）

| 値 | 説明 |
|----|------|
| `none` | 認証なし（パブリッククライアント） |
| `client_secret_post` | POST ボディで送信 |
| `client_secret_basic` | Authorization ヘッダー（デフォルト） |
| `client_secret_jwt` | JWT（対称鍵）で認証 |
| `private_key_jwt` | JWT（非対称鍵）で認証 |

#### グラントタイプとレスポンスタイプの関係

```
grant_types と response_types の整合性:

┌────────────────────────┬─────────────────────┐
│     grant_types        │   response_types    │
├────────────────────────┼─────────────────────┤
│ authorization_code     │ code                │
│ implicit               │ token               │
│ implicit               │ id_token            │
│ implicit               │ id_token token      │
│ client_credentials     │ （なし）             │
│ refresh_token          │ （なし）             │
└────────────────────────┴─────────────────────┘

デフォルト値:
  grant_types: ["authorization_code"]
  response_types: ["code"]
```

### 登録リクエスト

#### 基本的なリクエスト

```http
POST /register HTTP/1.1
Host: auth.example.com
Content-Type: application/json

{
  "redirect_uris": [
    "https://client.example.com/callback",
    "https://client.example.com/callback2"
  ],
  "client_name": "My Application",
  "client_uri": "https://client.example.com",
  "logo_uri": "https://client.example.com/logo.png",
  "contacts": ["admin@example.com"],
  "tos_uri": "https://client.example.com/tos",
  "policy_uri": "https://client.example.com/privacy",
  "token_endpoint_auth_method": "client_secret_basic",
  "grant_types": ["authorization_code", "refresh_token"],
  "response_types": ["code"],
  "scope": "openid profile email"
}
```

#### JWT Bearer 認証用のリクエスト

```http
POST /register HTTP/1.1
Host: auth.example.com
Content-Type: application/json

{
  "redirect_uris": ["https://client.example.com/callback"],
  "client_name": "Secure Client",
  "token_endpoint_auth_method": "private_key_jwt",
  "grant_types": ["authorization_code"],
  "response_types": ["code"],
  "jwks_uri": "https://client.example.com/.well-known/jwks.json"
}
```

または、JWKS をインラインで指定：

```json
{
  "redirect_uris": ["https://client.example.com/callback"],
  "token_endpoint_auth_method": "private_key_jwt",
  "jwks": {
    "keys": [
      {
        "kty": "RSA",
        "use": "sig",
        "kid": "client-key-1",
        "n": "...",
        "e": "AQAB"
      }
    ]
  }
}
```

### 登録レスポンス

#### 成功レスポンス

```http
HTTP/1.1 201 Created
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
  "registration_access_token": "reg-token-abc123",
  "registration_client_uri": "https://auth.example.com/register/s6BhdRkqt3"
}
```

| フィールド | 説明 |
|-----------|------|
| `client_id` | 発行されたクライアント ID |
| `client_secret` | 発行されたクライアントシークレット |
| `client_id_issued_at` | client_id の発行時刻（Unix タイムスタンプ） |
| `client_secret_expires_at` | client_secret の有効期限（0 = 無期限） |
| `registration_access_token` | 登録情報の管理用トークン |
| `registration_client_uri` | クライアント設定の URI |

#### エラーレスポンス

```http
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
  "error": "invalid_redirect_uri",
  "error_description": "The redirect_uri is not allowed"
}
```

| エラーコード | 説明 |
|-------------|------|
| `invalid_redirect_uri` | 不正な redirect_uri |
| `invalid_client_metadata` | 不正なクライアントメタデータ |
| `invalid_software_statement` | 不正なソフトウェアステートメント |
| `unapproved_software_statement` | 未承認のソフトウェアステートメント |

### Initial Access Token

認可サーバーは、登録エンドポイントへのアクセスを制限するために Initial Access Token を要求できます。

```
Initial Access Token の取得方法:
  1. 管理者が事前に発行
  2. 別の OAuth フローで取得
  3. ソフトウェアステートメントに含める

リクエスト例:
  POST /register HTTP/1.1
  Host: auth.example.com
  Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
  Content-Type: application/json

  { ... }
```

### Software Statement

ソフトウェアステートメントは、クライアントソフトウェアに関する**署名付き主張**です。

```
ソフトウェアステートメントの構造:
  ┌─────────────────────────────────────────────┐
  │            Software Statement               │
  │          （署名付き JWT）                    │
  ├─────────────────────────────────────────────┤
  │  iss: ソフトウェア発行者                      │
  │  software_id: ソフトウェア ID                │
  │  software_version: バージョン                │
  │  client_name: クライアント名                 │
  │  redirect_uris: リダイレクト URI             │
  │  ...                                        │
  └─────────────────────────────────────────────┘
          │
          ▼ 発行者の秘密鍵で署名
  ┌─────────────────────────────────────────────┐
  │  eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...    │
  └─────────────────────────────────────────────┘
```

リクエスト例：

```json
{
  "software_statement": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "redirect_uris": ["https://client.example.com/callback"]
}
```

### 認可サーバーの検証

```
登録リクエストの検証フロー:

1. 認証の検証（オプション）
   └── Initial Access Token があれば検証

2. メタデータの検証
   ├── redirect_uris の形式チェック
   ├── grant_types と response_types の整合性
   ├── token_endpoint_auth_method の妥当性
   └── jwks_uri または jwks の検証（必要な場合）

3. Software Statement の検証（あれば）
   ├── 署名の検証
   ├── 発行者の信頼性
   └── メタデータとの整合性

4. ポリシーの適用
   ├── 許可された redirect_uri のパターン
   ├── 許可された grant_types
   └── 組織固有のルール

5. クライアントの作成
   ├── client_id の生成
   ├── client_secret の生成（必要な場合）
   └── メタデータの保存
```


### セキュリティ考慮事項

| 項目 | 推奨事項 |
|------|----------|
| 登録エンドポイントの保護 | Initial Access Token を要求 |
| redirect_uri の検証 | 厳格なパターンマッチング |
| client_secret の強度 | 十分なエントロピー（最低 128 ビット） |
| HTTPS | 登録エンドポイントは TLS 必須 |
| レート制限 | DoS 攻撃対策 |
| Software Statement | 信頼できる発行者からのみ受け入れ |

### オープン登録 vs 保護された登録

```
オープン登録（Initial Access Token なし）:
  ✅ 開発者の利便性
  ❌ 悪用のリスク
  → パブリッククライアントのみ許可
  → 厳格な redirect_uri 検証

保護された登録（Initial Access Token あり）:
  ✅ アクセス制御
  ✅ 機密クライアントの登録
  → エンタープライズ環境に適切
```

---

## 参考リンク

- [RFC 7591 - OAuth 2.0 Dynamic Client Registration Protocol](https://datatracker.ietf.org/doc/html/rfc7591)
- [RFC 7592 - OAuth 2.0 Dynamic Client Registration Management Protocol](https://datatracker.ietf.org/doc/html/rfc7592)
- [OpenID Connect Dynamic Client Registration 1.0](https://openid.net/specs/openid-connect-registration-1_0.html)
