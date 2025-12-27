# RFC 6749: OAuth 2.0 認可フレームワーク

RFC 6749 は OAuth 2.0 の基本仕様です。このドキュメントでは、OAuth 2.0 の概念から各認可フローまでを解説します。

---

## 第1部: 概要編

### OAuth 2.0 とは何か？

OAuth 2.0 は、サードパーティアプリケーションがユーザーのリソースに安全にアクセスするための**認可フレームワーク**です。

重要なポイント：**認可（Authorization）** であり、**認証（Authentication）** ではありません。

### なぜ OAuth 2.0 が必要なのか？

OAuth 2.0 以前は、サードパーティアプリがユーザーのリソースにアクセスするには、ユーザーのパスワードを直接受け取る必要がありました。

| 問題 | 説明 |
|------|------|
| パスワード共有 | ユーザーがサードパーティにパスワードを教える必要がある |
| 過剰な権限 | サードパーティがユーザーの全権限を持ってしまう |
| 取り消し不可 | 特定のアプリだけアクセスを取り消すことが困難 |
| セキュリティリスク | パスワード漏洩時の影響が甚大 |

OAuth 2.0 は、**アクセストークン**という有効期限付きの限定的な権限を付与することで、これらの問題を解決します。

### 4つの登場人物（ロール）

OAuth 2.0 には 4 つのロールが登場します。

```
┌─────────────────┐      ┌─────────────────┐
│  Resource Owner │      │     Client      │
│    (ユーザー)    │      │   (アプリ)      │
└────────┬────────┘      └────────┬────────┘
         │ 認可                    │ トークン要求
         ▼                        ▼
┌─────────────────┐      ┌─────────────────┐
│ Authorization   │◄────►│    Resource     │
│    Server       │      │     Server      │
│  (認可サーバー)  │      │ (リソースサーバー) │
└─────────────────┘      └─────────────────┘
```

| ロール | 説明 | 例 |
|--------|------|-----|
| Resource Owner | 保護されたリソースの所有者 | エンドユーザー |
| Client | リソースにアクセスしたいアプリケーション | Webアプリ、モバイルアプリ |
| Authorization Server | アクセストークンを発行するサーバー | Google OAuth, Auth0 |
| Resource Server | 保護されたリソースをホストするサーバー | Google API, GitHub API |

### 4つの認可グラント

OAuth 2.0 では、アクセストークンを取得する方法として 4 つのグラントタイプが定義されています。

| グラント | 用途 | 推奨度 |
|----------|------|--------|
| Authorization Code | Webアプリ、モバイルアプリ | ✅ 推奨 |
| Implicit | SPA（非推奨） | ❌ 非推奨 |
| Resource Owner Password | 高信頼クライアント（レガシー） | ⚠️ 限定的 |
| Client Credentials | サーバー間通信 | ✅ 推奨 |

現代の OAuth 2.0 では、**Authorization Code Grant + PKCE** が標準です。

---

## 第2部: 詳細編

### Authorization Code Grant

最も一般的で推奨されるフローです。

#### フロー図

```
     ┌──────┐                               ┌───────────────┐
     │User  │                               │Authorization  │
     │Agent │                               │   Server      │
     └──┬───┘                               └───────┬───────┘
        │                                           │
        │  (1) Authorization Request                │
        │  (/authorize?response_type=code&...)      │
        │ ─────────────────────────────────────────►│
        │                                           │
        │  (2) User Authentication & Consent        │
        │ ◄────────────────────────────────────────►│
        │                                           │
        │  (3) Authorization Response               │
        │  (redirect_uri?code=xxx&state=yyy)        │
        │ ◄─────────────────────────────────────────│
        │                                           │
     ┌──┴───┐                               ┌───────┴───────┐
     │Client│                               │Authorization  │
     │      │                               │   Server      │
     └──┬───┘                               └───────┬───────┘
        │                                           │
        │  (4) Token Request                        │
        │  (POST /token, grant_type=authorization_  │
        │   code, code=xxx)                         │
        │ ─────────────────────────────────────────►│
        │                                           │
        │  (5) Token Response                       │
        │  (access_token, refresh_token, ...)       │
        │ ◄─────────────────────────────────────────│
        │                                           │
```

#### 認可リクエスト（Authorization Request）

```http
GET /authorize?
  response_type=code
  &client_id=s6BhdRkqt3
  &redirect_uri=https%3A%2F%2Fclient.example.org%2Fcallback
  &scope=read%20write
  &state=xyz123
HTTP/1.1
Host: auth.example.com
```

| パラメータ | 必須 | 説明 |
|------------|------|------|
| `response_type` | ✅ | `code` を指定 |
| `client_id` | ✅ | クライアント識別子 |
| `redirect_uri` | △ | コールバック URL（事前登録必須） |
| `scope` | △ | 要求するスコープ（スペース区切り） |
| `state` | 推奨 | CSRF 対策用のランダム値 |

#### 認可レスポンス（Authorization Response）

成功時：

```http
HTTP/1.1 302 Found
Location: https://client.example.org/callback?
  code=SplxlOBeZQQYbYS6WxSbIA
  &state=xyz123
```

エラー時：

```http
HTTP/1.1 302 Found
Location: https://client.example.org/callback?
  error=access_denied
  &error_description=The%20resource%20owner%20denied%20the%20request
  &state=xyz123
```

| エラーコード | 説明 |
|--------------|------|
| `invalid_request` | リクエストパラメータが不正 |
| `unauthorized_client` | クライアントが認可されていない |
| `access_denied` | ユーザーが拒否した |
| `unsupported_response_type` | サポートされていない response_type |
| `invalid_scope` | 不正なスコープ |
| `server_error` | サーバー内部エラー |
| `temporarily_unavailable` | 一時的に利用不可 |

#### トークンリクエスト（Token Request）

```http
POST /token HTTP/1.1
Host: auth.example.com
Content-Type: application/x-www-form-urlencoded
Authorization: Basic czZCaGRSa3F0MzpnWDFmQmF0M2JW

grant_type=authorization_code
&code=SplxlOBeZQQYbYS6WxSbIA
&redirect_uri=https%3A%2F%2Fclient.example.org%2Fcallback
```

#### トークンレスポンス（Token Response）

```json
{
  "access_token": "SlAV32hkKG",
  "token_type": "Bearer",
  "expires_in": 3600,
  "refresh_token": "8xLOxBtZp8",
  "scope": "read write"
}
```

### Client Credentials Grant

サーバー間通信（Machine-to-Machine）で使用します。ユーザーの関与がないフローです。

```http
POST /token HTTP/1.1
Host: auth.example.com
Content-Type: application/x-www-form-urlencoded
Authorization: Basic czZCaGRSa3F0MzpnWDFmQmF0M2JW

grant_type=client_credentials
&scope=read
```

レスポンス：

```json
{
  "access_token": "2YotnFZFEjr1zCsicMWpAA",
  "token_type": "Bearer",
  "expires_in": 3600
}
```

**注意**: このフローでは `refresh_token` は発行されません。

### Refresh Token の使用

アクセストークンの有効期限が切れた場合、リフレッシュトークンを使って新しいアクセストークンを取得できます。

```http
POST /token HTTP/1.1
Host: auth.example.com
Content-Type: application/x-www-form-urlencoded
Authorization: Basic czZCaGRSa3F0MzpnWDFmQmF0M2JW

grant_type=refresh_token
&refresh_token=8xLOxBtZp8
```

### クライアントタイプ

OAuth 2.0 では、クライアントを機密性によって 2 種類に分類します。

| タイプ | 説明 | 例 |
|--------|------|-----|
| Confidential | クライアントシークレットを安全に保管できる | サーバーサイド Web アプリ |
| Public | クライアントシークレットを安全に保管できない | SPA、モバイルアプリ、デスクトップアプリ |

Public クライアントでは、**PKCE（RFC 7636）** の使用が必須です。

### スコープ（Scope）

スコープは、クライアントが要求するアクセス権限を表します。

```
scope=read write delete
```

- スペース区切りで複数指定可能
- 認可サーバーは要求されたスコープの一部のみを許可することも可能
- トークンレスポンスの `scope` で実際に付与されたスコープが返される

### セキュリティ考慮事項

RFC 6749 で言及されている主なセキュリティ対策：

| 対策 | 説明 |
|------|------|
| HTTPS 必須 | 全ての通信を TLS で保護 |
| state パラメータ | CSRF 攻撃の防止 |
| redirect_uri の厳密な検証 | オープンリダイレクタ攻撃の防止 |
| 認可コードの一回限り使用 | リプレイ攻撃の防止 |
| クライアント認証 | Confidential クライアントは必須 |

**注意**: RFC 6749 だけでは不十分です。現代のベストプラクティスは RFC 9700（OAuth 2.0 Security BCP）を参照してください。

---

## 参考リンク

- [RFC 6749 - The OAuth 2.0 Authorization Framework](https://datatracker.ietf.org/doc/html/rfc6749)
- [RFC 6750 - Bearer Token Usage](https://datatracker.ietf.org/doc/html/rfc6750)
- [RFC 7636 - PKCE](https://datatracker.ietf.org/doc/html/rfc7636)
- [RFC 9700 - OAuth 2.0 Security Best Current Practice](https://datatracker.ietf.org/doc/html/rfc9700)
