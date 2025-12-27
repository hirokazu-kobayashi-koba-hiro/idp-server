# RFC 9207: OAuth 2.0 認可サーバー発行者識別

RFC 9207 は、認可レスポンスに発行者（Issuer）識別子を含めることで、Mix-Up 攻撃を防止するための仕様です。

---

## 第1部: 概要編

### 発行者識別とは？

RFC 9207 は、認可レスポンスに `iss` パラメータを追加して、どの認可サーバーがレスポンスを発行したかを明示します。

```
従来:
  認可サーバー ──► redirect_uri?code=xxx&state=yyy ──► クライアント
                        │
                        └── どの認可サーバーからのレスポンス？

RFC 9207:
  認可サーバー ──► redirect_uri?code=xxx&state=yyy&iss=https://as.example.com ──► クライアント
                        │
                        └── iss で認可サーバーを識別
```

### Mix-Up 攻撃とは？

Mix-Up 攻撃は、複数の認可サーバーを使用するクライアントを標的にした攻撃です。

```
攻撃シナリオ:

1. クライアントが複数の認可サーバー（AS-1, AS-2）をサポート
2. ユーザーが AS-1 で認可を開始
3. 攻撃者が AS-2 の認可レスポンスを AS-1 のものとして偽装
4. クライアントが AS-2 の認可コードを AS-1 に送信
5. AS-1 がエラーを返す、または攻撃者がトークンを取得

  ┌──────────┐    ┌─────────────┐    ┌──────────┐
  │  ユーザー  │    │   攻撃者     │    │ AS-1     │
  │          │    │             │    │          │
  │          │ ◄──┤ AS-2 の     │    │          │
  │          │    │ code を     │    │          │
  │          │    │ AS-1 として  │    │          │
  └──────────┘    │ 送信        │    └──────────┘
                  └─────────────┘
```

### iss パラメータによる防止

```
正当なレスポンス（AS-1）:
  redirect_uri?code=abc&state=xyz&iss=https://as1.example.com
                                       │
                                       └── AS-1 の識別子

攻撃レスポンス（AS-2 の code を AS-1 として偽装）:
  redirect_uri?code=def&state=xyz&iss=https://as2.example.com
                                       │
                                       └── AS-2 の識別子

クライアントの検証:
  1. state から期待される issuer を取得: https://as1.example.com
  2. レスポンスの iss と比較
  3. 一致しなければ拒否 → 攻撃防止
```

---

## 第2部: 詳細編

### iss パラメータ

| 特性 | 説明 |
|------|------|
| 形式 | 認可サーバーの Issuer 識別子（URL） |
| 必須 | 認可サーバーがサポートする場合は必須 |
| 場所 | 認可レスポンス |
| 対象 | 成功レスポンス・エラーレスポンスの両方 |

```
Issuer 識別子の要件:
  - HTTPS スキーム
  - ポート番号はデフォルト（443）または明示的に指定
  - パスはオプション
  - クエリ・フラグメントは禁止

例:
  ✅ https://auth.example.com
  ✅ https://auth.example.com:8443
  ✅ https://auth.example.com/tenant/123
  ❌ https://auth.example.com?query=value
  ❌ https://auth.example.com#fragment
```

### 認可レスポンス

#### 成功レスポンス

```
HTTP/1.1 302 Found
Location: https://client.example.com/callback?
  code=SplxlOBeZQQYbYS6WxSbIA
  &state=af0ifjsldkj
  &iss=https%3A%2F%2Fauth.example.com
```

| パラメータ | 説明 |
|-----------|------|
| `code` | 認可コード |
| `state` | クライアントが送信した state |
| `iss` | 認可サーバーの Issuer 識別子 |

#### エラーレスポンス

```
HTTP/1.1 302 Found
Location: https://client.example.com/callback?
  error=access_denied
  &error_description=The+user+denied+the+request
  &state=af0ifjsldkj
  &iss=https%3A%2F%2Fauth.example.com
```

エラーレスポンスにも `iss` を含めることが推奨されます。

### ディスカバリーによるサポート表明

認可サーバーは、メタデータで `iss` のサポートを表明します。

```json
{
  "issuer": "https://auth.example.com",
  "authorization_endpoint": "https://auth.example.com/authorize",
  "token_endpoint": "https://auth.example.com/token",
  "authorization_response_iss_parameter_supported": true
}
```

| メタデータ | 説明 |
|-----------|------|
| `authorization_response_iss_parameter_supported` | `iss` パラメータをサポートする場合 `true` |

### クライアントの検証手順

```
認可レスポンスの検証フロー:

1. state の検証
   └── 自分が生成した state か

2. state から期待される issuer を取得
   └── 認可リクエスト時に保存しておく

3. iss パラメータの検証
   ├── iss が存在するか
   ├── iss が期待される issuer と一致するか
   └── 一致しなければエラー

4. 以降の通常の処理
   └── トークンリクエストなど
```

### Java（認可サーバー）

### 暗黙的フローとの関係

```
暗黙的フロー（Implicit Flow）:
  response_type=token の場合も iss を含める

  redirect_uri#
    access_token=...
    &token_type=Bearer
    &state=xyz
    &iss=https://auth.example.com

ただし、暗黙的フローは非推奨（RFC 9700 BCP）
```

### OIDC との関係

OpenID Connect では、ID トークンに `iss` クレームが含まれます。RFC 9207 はこれを補完し、認可レスポンス自体にも `iss` を含めます。

```
OIDC:
  ID トークンの iss クレーム
  → トークンレスポンスで取得（トークンエンドポイント後）

RFC 9207:
  認可レスポンスの iss パラメータ
  → 認可レスポンスで即座に検証可能
  → Mix-Up 攻撃をより早い段階で防止
```

### JARM との関係

JWT Secured Authorization Response Mode（JARM）を使用する場合、JWT 自体に `iss` クレームが含まれます。

```
JARM:
  認可レスポンス自体が JWT
  JWT の iss クレームで発行者を識別

RFC 9207:
  通常のクエリパラメータとして iss を追加
  JARM を使用しない場合の解決策
```

### セキュリティ考慮事項

| 項目 | 推奨事項 |
|------|----------|
| 必須検証 | クライアントは iss を必ず検証 |
| 厳格な一致 | 文字列の完全一致で比較 |
| state との連携 | state から期待される issuer を取得 |
| エラーレスポンス | エラーにも iss を含める |
| ディスカバリー | メタデータでサポートを確認 |

### 移行戦略

```
既存システムへの導入:

1. 認可サーバー側
   └── 認可レスポンスに iss を追加
   └── メタデータを更新

2. クライアント側
   └── iss の検証ロジックを追加
   └── iss がない場合のフォールバック（移行期間中）

3. 完全移行
   └── iss がないレスポンスを拒否
```

---

## 参考リンク

- [RFC 9207 - OAuth 2.0 Authorization Server Issuer Identification](https://datatracker.ietf.org/doc/html/rfc9207)
- [OAuth 2.0 Mix-Up Mitigation](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-mix-up-mitigation)
- [RFC 8414 - OAuth 2.0 Authorization Server Metadata](https://datatracker.ietf.org/doc/html/rfc8414)
