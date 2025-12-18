# HTTPの基礎

## このドキュメントの目的

**HTTP（Hypertext Transfer Protocol）** の基礎を理解し、OAuth 2.0/OIDCを学ぶ土台を作ることが目標です。

---

## HTTPとは

**HTTP（Hypertext Transfer Protocol）**:
- Web上でデータをやり取りするための通信プロトコル
- クライアント（ブラウザ、アプリ）とサーバー間の通信ルール
- リクエスト・レスポンス型（クライアントが要求、サーバーが応答）

### HTTP通信の全体像

```
┌──────────────────┐                    ┌──────────────────┐
│                  │                    │                  │
│  クライアント     │                    │  サーバー         │
│  (ブラウザ、      │                    │  (Web API)       │
│   モバイルアプリ)  │                    │                  │
│                  │                    │                  │
│  1. リクエスト作成│                    │                  │
│     ↓            │                    │                  │
│  ┌────────────┐  │                    │                  │
│  │GET /users  │  │──2. HTTPリクエスト→│  3. リクエスト受信│
│  │Host: api...│  │                    │     ↓            │
│  └────────────┘  │                    │  4. 処理実行     │
│                  │                    │     ↓            │
│  6. レスポンス受信│                    │  5. レスポンス作成│
│     ↓            │                    │     ↓            │
│  ┌────────────┐  │                    │  ┌────────────┐  │
│  │200 OK      │  │←─HTTPレスポンス─── │  │200 OK      │  │
│  │{user data} │  │                    │  │{user data} │  │
│  └────────────┘  │                    │  └────────────┘  │
│     ↓            │                    │                  │
│  7. データ利用   │                    │                  │
│                  │                    │                  │
└──────────────────┘                    └──────────────────┘
```

**特徴**:
- **ステートレス**: サーバーは前回のリクエストを覚えていない
- **1回のやり取り**: 1リクエスト → 1レスポンス
- **クライアント主導**: クライアントがリクエストを開始

---

## HTTPリクエスト・レスポンスの基本

### リクエストの構造

```
GET /users/123 HTTP/1.1
Host: api.example.com
Authorization: Bearer eyJ...
Content-Type: application/json

{リクエストボディ（POSTの場合）}
```

**4つの要素**:
1. **リクエストライン**: メソッド、パス、HTTPバージョン
2. **ヘッダー**: メタ情報（Host、Authorization等）
3. **空行**: ヘッダーとボディの区切り
4. **ボディ**: 送信データ（オプション）

### レスポンスの構造

```
HTTP/1.1 200 OK
Content-Type: application/json
Content-Length: 123

{"user_id": "123", "name": "John"}
```

**4つの要素**:
1. **ステータスライン**: HTTPバージョン、ステータスコード、ステータステキスト
2. **ヘッダー**: メタ情報（Content-Type等）
3. **空行**: ヘッダーとボディの区切り
4. **ボディ**: レスポンスデータ

---

## HTTPメソッド

### GET（取得）

**用途**: リソースの取得

```
GET /users/123 HTTP/1.1
```

**特徴**:
- ボディなし（URLのみでリクエスト）
- 安全（Safe）: サーバー状態を変更しない
- べき等（Idempotent）: 何度実行しても同じ結果
- キャッシュ可能

**OAuth/OIDCでの使用例**:
- UserInfo取得: `GET /{tenant-id}/v1/userinfo`
- Discovery: `GET /.well-known/openid-configuration`

---

### POST（作成）

**用途**: リソースの作成

```
POST /users HTTP/1.1
Content-Type: application/json

{"name": "John", "email": "john@example.com"}
```

**特徴**:
- ボディあり（作成するデータを送信）
- 安全でない: サーバー状態を変更
- べき等でない: 実行するたびに新しいリソース作成

**OAuth/OIDCでの使用例**:
- Token Request: `POST /{tenant-id}/v1/tokens`
- Authorization Request（PAR）: `POST /{tenant-id}/v1/authorizations/push`

---

### PUT（更新・置換）

**用途**: リソースの更新（完全置換）

```
PUT /users/123 HTTP/1.1
Content-Type: application/json

{"name": "John Doe", "email": "john.doe@example.com"}
```

**特徴**:
- ボディあり（更新後の完全なデータ）
- 安全でない: サーバー状態を変更
- べき等: 何度実行しても同じ結果

---

### PATCH（部分更新）

**用途**: リソースの部分更新

```
PATCH /users/123 HTTP/1.1
Content-Type: application/json

{"email": "new.email@example.com"}
```

**特徴**:
- ボディあり（変更する部分のみ）
- 安全でない
- べき等: 実装による

---

### DELETE（削除）

**用途**: リソースの削除

```
DELETE /users/123 HTTP/1.1
```

**特徴**:
- ボディなし（通常）
- 安全でない
- べき等: 何度実行しても削除済み状態

---

## HTTPヘッダー

### Host

**役割**: アクセス先のホスト名

```
Host: api.example.com
```

**必須**: HTTP/1.1では必須

---

### Authorization

**役割**: 認証情報

```
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
Authorization: Basic dXNlcm5hbWU6cGFzc3dvcmQ=
```

**OAuth 2.0での使用**:
- `Bearer {access_token}`: Access Token
- `Basic {base64(client_id:client_secret)}`: クライアント認証

---

### Content-Type

**役割**: ボディのデータ形式

```
Content-Type: application/json
Content-Type: application/x-www-form-urlencoded
```

**OAuth 2.0での使用**:
- Token Request: `application/x-www-form-urlencoded`
- UserInfo: `application/json`

---

### Content-Length

**役割**: ボディのサイズ（バイト数）

```
Content-Length: 123
```

---

## HTTPステータスコード（基礎）

### 2xx: 成功

- **200 OK**: 成功（GETの場合）
- **201 Created**: 作成成功（POSTの場合）
- **204 No Content**: 成功（レスポンスボディなし）

### 4xx: クライアントエラー

- **400 Bad Request**: リクエストが不正
- **401 Unauthorized**: 認証が必要
- **403 Forbidden**: 権限なし
- **404 Not Found**: リソースが存在しない

### 5xx: サーバーエラー

- **500 Internal Server Error**: サーバー内部エラー
- **502 Bad Gateway**: ゲートウェイエラー
- **503 Service Unavailable**: サービス利用不可

---

## HTTP vs HTTPS

### HTTP（暗号化なし）

```
http://api.example.com/users

問題点:
- 通信内容が平文（盗聴可能）
- 中間者攻撃（MITM）のリスク
```

### HTTPS（暗号化あり）

```
https://api.example.com/users

利点:
- 通信内容が暗号化
- 盗聴防止
- サーバー認証（証明書）
```

**OAuth 2.0/OIDC**: **HTTPS必須**

---

## OAuth 2.0/OIDCでのHTTP使用例

### Authorization Request（GET）

```
GET /authorize?response_type=code&client_id=xxx&redirect_uri=... HTTP/1.1
Host: idp.example.com
```

### Token Request（POST）

```
POST /token HTTP/1.1
Host: idp.example.com
Content-Type: application/x-www-form-urlencoded
Authorization: Basic Y2xpZW50LWlkOmNsaWVudC1zZWNyZXQ=

grant_type=authorization_code&code=abc123&redirect_uri=...
```

### UserInfo Request（GET）

```
GET /userinfo HTTP/1.1
Host: idp.example.com
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
```

---

## まとめ

### 学んだこと

- ✅ HTTPはリクエスト・レスポンス型の通信プロトコル
- ✅ HTTPメソッド（GET、POST、PUT、PATCH、DELETE）
- ✅ HTTPヘッダー（Host、Authorization、Content-Type）
- ✅ HTTPステータスコード（2xx成功、4xx/5xxエラー）
- ✅ HTTP vs HTTPS（OAuth/OIDCはHTTPS必須）

### 次に読むべきドキュメント

1. [RESTful API設計](./rest-api-design.md) - リソース指向設計
2. [HTTPS/TLS](./https-tls-basics.md) - 暗号化通信
3. [OAuth 2.0の基礎](../02-oauth-fundamentals/oauth-oidc-why-needed.md) - OAuth 2.0を学ぶ

---

**最終更新**: 2025-12-18
**対象**: IDサービス開発初心者
