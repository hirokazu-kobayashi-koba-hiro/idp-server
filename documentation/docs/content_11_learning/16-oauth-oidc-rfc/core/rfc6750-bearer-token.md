# RFC 6750: Bearer Token の使用方法

RFC 6750 は、OAuth 2.0 で発行されたアクセストークン（Bearer Token）をリソースサーバーに送信する方法を定義した仕様です。

---

## 第1部: 概要編

### Bearer Token とは何か？

Bearer Token（ベアラートークン）は、「持っている人が使える」トークンです。トークンを所持していれば、そのトークンの権限でリソースにアクセスできます。

```
Bearer = 持参人払い

Bearer Token を持っている人 = トークンの正当な利用者として扱われる
```

### なぜ RFC 6750 が必要なのか？

RFC 6749（OAuth 2.0 Core）はトークンの**発行方法**を定義しましたが、トークンの**送信方法**は定義していませんでした。RFC 6750 は、クライアントがリソースサーバーにトークンを送信する標準的な方法を規定します。

### 3つの送信方法

RFC 6750 では、Bearer Token を送信する 3 つの方法を定義しています。

| 方法 | 推奨度 | 説明 |
|------|--------|------|
| Authorization ヘッダー | ✅ 推奨 | `Authorization: Bearer <token>` |
| フォームパラメータ | ⚠️ 限定的 | POST ボディに `access_token=<token>` |
| クエリパラメータ | ❌ 非推奨 | URL に `?access_token=<token>` |

---

## 第2部: 詳細編

### 1. Authorization ヘッダー（推奨）

最も推奨される方法です。HTTP の `Authorization` ヘッダーを使用します。

```http
GET /resource HTTP/1.1
Host: api.example.com
Authorization: Bearer mF_9.B5f-4.1JqM
```

#### 構文

```
Authorization: Bearer <access_token>
```

- `Bearer` は大文字小文字を区別しない（ただし慣例として先頭大文字）
- `Bearer` とトークンの間はスペース 1 つ
- トークンは Base64 や Base64URL でエンコードされることが多い

#### メリット

| メリット | 説明 |
|----------|------|
| キャッシュ安全 | URL にトークンが含まれないため、キャッシュに残らない |
| ログ安全 | アクセスログに URL が記録されてもトークンが漏洩しない |
| 標準的 | HTTP 認証の標準的なメカニズムに準拠 |

### 2. フォームパラメータ

POST リクエストのボディに含める方法です。

```http
POST /resource HTTP/1.1
Host: api.example.com
Content-Type: application/x-www-form-urlencoded

access_token=mF_9.B5f-4.1JqM
```

#### 使用条件

この方法は以下の条件を**すべて**満たす場合にのみ使用可能：

1. HTTP メソッドが POST
2. Content-Type が `application/x-www-form-urlencoded`
3. リクエストボディが単一パートである

#### 制限事項

```
❌ GET リクエストでは使用不可
❌ multipart/form-data では使用不可
❌ JSON ボディでは使用不可
```

### 3. クエリパラメータ（非推奨）

URL のクエリパラメータに含める方法です。

```http
GET /resource?access_token=mF_9.B5f-4.1JqM HTTP/1.1
Host: api.example.com
```

#### 重大なセキュリティリスク

| リスク | 説明 |
|--------|------|
| ログ漏洩 | Web サーバーのアクセスログに URL が記録される |
| Referer 漏洩 | 別サイトへのリンクで Referer ヘッダーに URL が含まれる |
| ブラウザ履歴 | ブラウザの履歴に URL が残る |
| キャッシュ | プロキシやブラウザにキャッシュされる可能性 |

#### 使用が許容されるケース

> Authorization ヘッダーを使用できない場合にのみ使用すべき

例：
- レガシーシステムとの互換性
- JavaScript が制限された環境

### エラーレスポンス

リソースサーバーは、認証エラー時に `WWW-Authenticate` ヘッダーを返します。

#### 401 Unauthorized

```http
HTTP/1.1 401 Unauthorized
WWW-Authenticate: Bearer realm="example",
                  error="invalid_token",
                  error_description="The access token expired"
```

#### エラーコード

| エラーコード | 説明 |
|--------------|------|
| `invalid_request` | リクエストが不正（必須パラメータの欠落等） |
| `invalid_token` | トークンが無効（期限切れ、失効、改ざん等） |
| `insufficient_scope` | トークンのスコープが不足 |

#### WWW-Authenticate ヘッダーの属性

| 属性 | 説明 |
|------|------|
| `realm` | 保護されたリソースの識別子 |
| `scope` | 必要なスコープ |
| `error` | エラーコード |
| `error_description` | 人間が読めるエラー説明 |
| `error_uri` | エラーの詳細情報の URL |


### セキュリティ考慮事項

| 項目 | 推奨事項 |
|------|----------|
| TLS 必須 | すべての通信を HTTPS で保護 |
| Authorization ヘッダー使用 | クエリパラメータは避ける |
| トークン有効期限 | 短く設定（数分〜数時間） |
| トークン保管 | クライアントで安全に保管 |
| ログ除外 | トークンをログに記録しない |

### Bearer Token の限界

Bearer Token は「持っている人が使える」という性質上、盗まれると悪用されます。

```
Bearer Token が盗まれた場合:
  攻撃者 → 盗んだトークンを使用 → リソースにアクセス可能

対策:
  1. 短い有効期限を設定
  2. Sender-Constrained Token を使用
     - DPoP（RFC 9449）
     - mTLS（RFC 8705）
```

Sender-Constrained Token は、トークンを特定のクライアントにバインドすることで、盗まれても悪用できなくします。

---

## 参考リンク

- [RFC 6750 - The OAuth 2.0 Authorization Framework: Bearer Token Usage](https://datatracker.ietf.org/doc/html/rfc6750)
- [RFC 6749 - The OAuth 2.0 Authorization Framework](https://datatracker.ietf.org/doc/html/rfc6749)
- [RFC 9449 - OAuth 2.0 Demonstrating Proof of Possession (DPoP)](https://datatracker.ietf.org/doc/html/rfc9449)
