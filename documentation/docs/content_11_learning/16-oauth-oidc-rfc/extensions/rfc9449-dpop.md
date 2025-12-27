# RFC 9449: DPoP（Demonstrating Proof of Possession）

RFC 9449 は、アクセストークンを特定のクライアントにバインドするための仕様です。このドキュメントでは、DPoP の仕組みと実装方法を解説します。

---

## 第1部: 概要編

### DPoP とは何か？

DPoP（ディーポップと読む）は「**Demonstrating Proof of Possession**」の略で、アクセストークンを特定の鍵ペアに紐づける（バインドする）仕組みです。

Bearer トークンの「盗まれたら誰でも使える」という問題を解決します。

### なぜ DPoP が必要なのか？

従来の Bearer トークンには大きな弱点があります。

#### Bearer トークンの問題

```
攻撃者がトークンを盗んだ場合...

    攻撃者                     リソースサーバー
       │                              │
       │  Authorization: Bearer xxx   │
       │ ────────────────────────────►│
       │                              │
       │  200 OK (リソース)            │
       │ ◄────────────────────────────│
       │                              │

→ 盗んだトークンをそのまま使用できてしまう！
```

#### DPoP による解決

```
攻撃者がトークンを盗んでも...

    攻撃者                     リソースサーバー
       │                              │
       │  Authorization: DPoP xxx     │
       │  DPoP: <proof JWT>           │
       │ ────────────────────────────►│
       │                              │
       │  401 Unauthorized            │
       │  (秘密鍵がないので           │
       │   proof を作れない)          │
       │ ◄────────────────────────────│
```

DPoP では、リクエストごとに秘密鍵で署名した「証明」を送る必要があるため、秘密鍵を持たない攻撃者はトークンを使用できません。

### DPoP の基本概念

DPoP は以下の 2 つの要素で構成されます。

| 要素 | 説明 |
|------|------|
| DPoP Proof | リクエストごとに生成する署名付き JWT |
| DPoP-bound Token | 特定の公開鍵にバインドされたアクセストークン |

```
クライアント                   認可サーバー                 リソースサーバー
    │                              │                              │
    │  (1) トークンリクエスト       │                              │
    │  + DPoP proof (公開鍵含む)   │                              │
    │ ────────────────────────────►│                              │
    │                              │                              │
    │  (2) DPoP-bound トークン      │                              │
    │ ◄────────────────────────────│                              │
    │                              │                              │
    │  (3) リソースリクエスト       │                              │
    │  + DPoP proof (新しいもの)   │                              │
    │ ────────────────────────────────────────────────────────────►│
    │                              │                              │
    │  (4) リソース                │                              │
    │ ◄────────────────────────────────────────────────────────────│
```

---

## 第2部: 詳細編

### DPoP Proof JWT の構造

DPoP Proof は以下の構造を持つ JWT です。

#### ヘッダー

```json
{
  "typ": "dpop+jwt",
  "alg": "ES256",
  "jwk": {
    "kty": "EC",
    "crv": "P-256",
    "x": "l8tFrhx-34tV3hRICRDY9zCkDlpBhF42UQUfWVAWBFs",
    "y": "9VE4jf_Ok_o64zbTTlcuNJajHmt6v9TDVrU0CdvGRDA"
  }
}
```

| フィールド | 必須 | 説明 |
|------------|------|------|
| `typ` | ✅ | `dpop+jwt` 固定 |
| `alg` | ✅ | 署名アルゴリズム（`ES256`, `RS256` など） |
| `jwk` | ✅ | 公開鍵（秘密鍵成分は含まない） |

#### ペイロード

```json
{
  "jti": "e1j3V_bKic8-LAEB_lccDA",
  "htm": "POST",
  "htu": "https://auth.example.com/token",
  "iat": 1704067200,
  "ath": "fUHyO2r2Z3DZ53EsNrWBb0xWXoaNy59IiKCAqksmQEo"
}
```

| クレーム | 必須 | 説明 |
|----------|------|------|
| `jti` | ✅ | 一意識別子（リプレイ攻撃防止） |
| `htm` | ✅ | HTTP メソッド（`POST`, `GET` など） |
| `htu` | ✅ | HTTP URI（クエリ・フラグメント除く） |
| `iat` | ✅ | 発行時刻 |
| `ath` | △ | アクセストークンハッシュ（リソースリクエスト時に必須） |
| `nonce` | △ | サーバーが要求した場合に含める |

### トークン取得フロー

#### Step 1: DPoP 鍵ペアの生成

クライアントは最初に鍵ペアを生成します。

#### Step 2: トークンリクエスト

```http
POST /token HTTP/1.1
Host: auth.example.com
Content-Type: application/x-www-form-urlencoded
DPoP: eyJ0eXAiOiJkcG9wK2p3dCIsImFsZyI6IkVTMjU2IiwiandrIjp7Imt0eSI...

grant_type=authorization_code
&code=SplxlOBeZQQYbYS6WxSbIA
&redirect_uri=https%3A%2F%2Fclient.example.org%2Fcallback
&client_id=s6BhdRkqt3
&code_verifier=dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk
```

この時の DPoP Proof のペイロード：

```json
{
  "jti": "unique-id-1",
  "htm": "POST",
  "htu": "https://auth.example.com/token",
  "iat": 1704067200
}
```

#### Step 3: トークンレスポンス

```json
{
  "access_token": "Kz~8mXK1EalYznwH-LC-1fBAo.4Ljp~zsPE_NeO.gxU",
  "token_type": "DPoP",
  "expires_in": 3600,
  "refresh_token": "Q..Zkm29lexi8VnWg2zPW1x-tgGad0Ibc3s3EwM_Ni4-g"
}
```

**注意**: `token_type` が `DPoP` になっています。

### リソースアクセスフロー

#### リソースリクエスト

```http
GET /resource HTTP/1.1
Host: api.example.com
Authorization: DPoP Kz~8mXK1EalYznwH-LC-1fBAo.4Ljp~zsPE_NeO.gxU
DPoP: eyJ0eXAiOiJkcG9wK2p3dCIsImFsZyI6IkVTMjU2IiwiandrIjp7Imt0eSI...
```

この時の DPoP Proof には `ath`（アクセストークンハッシュ）が必要：

```json
{
  "jti": "unique-id-2",
  "htm": "GET",
  "htu": "https://api.example.com/resource",
  "iat": 1704067260,
  "ath": "fUHyO2r2Z3DZ53EsNrWBb0xWXoaNy59IiKCAqksmQEo"
}
```

`ath` の計算方法：

```
ath = BASE64URL(SHA256(access_token))
```

### サーバー側の検証

#### 認可サーバー（トークン発行時）

```
1. DPoP ヘッダーの JWT を検証
   - 署名検証
   - typ = "dpop+jwt"
   - htm = "POST"
   - htu = トークンエンドポイント URL
   - iat が許容範囲内
   - jti が未使用

2. 公開鍵のフィンガープリントを計算
   - jkt = BASE64URL(SHA256(JWK Thumbprint))

3. アクセストークンに jkt を埋め込む（または紐づけて保存）
```

#### リソースサーバー（リソースアクセス時）

```
1. Authorization ヘッダーから DPoP トークンを取得

2. DPoP ヘッダーの JWT を検証
   - 署名検証
   - typ = "dpop+jwt"
   - htm = リクエストのメソッド
   - htu = リクエストの URI
   - iat が許容範囲内
   - jti が未使用
   - ath = BASE64URL(SHA256(access_token))

3. トークンの jkt と DPoP Proof の公開鍵を照合
   - 一致しなければ拒否
```

### nonce による追加保護

サーバーは `DPoP-Nonce` ヘッダーで nonce を要求できます。

```http
HTTP/1.1 401 Unauthorized
WWW-Authenticate: DPoP error="use_dpop_nonce"
DPoP-Nonce: eyJ7S_zG.eyJH0-Z.HX4w-7v
```

クライアントは次のリクエストで nonce を含める必要があります。

### トークンイントロスペクションとの連携

DPoP バウンドトークンのイントロスペクション応答：

```json
{
  "active": true,
  "token_type": "DPoP",
  "cnf": {
    "jkt": "0ZcOCORZNYy-DWpqq30jZyJGHTN0d2HglBV3uiguA4I"
  }
}
```

`cnf.jkt` は公開鍵のサムプリントです。

### セキュリティ考慮事項

| 項目 | 推奨事項 |
|------|----------|
| 鍵の保管 | 秘密鍵はセキュアストレージに保存 |
| 鍵のローテーション | 定期的に鍵ペアを更新 |
| jti の管理 | 使用済み jti をキャッシュしてリプレイ攻撃を防止 |
| 時刻検証 | iat の許容範囲を適切に設定（数分程度） |
| nonce の活用 | 高セキュリティ環境では nonce を必須化 |

### DPoP vs mTLS

| 観点 | DPoP | mTLS |
|------|------|------|
| 証明書管理 | 不要（JWK で完結） | PKI インフラが必要 |
| ブラウザサポート | JavaScript で実装可能 | 制限あり |
| バインディング | アプリケーション層 | TLS 層 |
| 導入コスト | 低い | 高い |

---

## 参考リンク

- [RFC 9449 - OAuth 2.0 Demonstrating Proof of Possession (DPoP)](https://datatracker.ietf.org/doc/html/rfc9449)
- [RFC 7638 - JSON Web Key (JWK) Thumbprint](https://datatracker.ietf.org/doc/html/rfc7638)
- [RFC 9700 - OAuth 2.0 Security Best Current Practice](https://datatracker.ietf.org/doc/html/rfc9700)
