# RFC 7523: JWT を使った OAuth 2.0 認証ガイド

RFC 7523 は、OAuth 2.0 で JWT（JSON Web Token）を活用するための仕様です。このドキュメントでは、初学者でも理解できるよう、概要から詳細まで段階的に解説します。

---

## 第1部: 概要編

### RFC 7523 とは何か？

RFC 7523 の正式名称は「**JSON Web Token (JWT) Profile for OAuth 2.0 Client Authentication and Authorization Grants**」です。

簡単に言うと、OAuth 2.0 において JWT を使って以下の2つを実現するためのルールを定めた仕様です。

1. **クライアント認証** — アプリケーション（クライアント）が「自分は正規のクライアントである」と認可サーバーに証明する
2. **認可グラント** — JWT を使ってアクセストークンを取得する

### なぜ RFC 7523 が必要なのか？

従来の OAuth 2.0 では、クライアント認証に `client_id` と `client_secret` をそのまま送信する方式（`client_secret_post` や `client_secret_basic`）が一般的でした。しかし、この方式にはいくつかの課題があります。

| 課題 | 説明 |
|------|------|
| 秘密情報の直接送信 | `client_secret` がリクエストごとにネットワーク上を流れる |
| 有効期限がない | 一度発行された `client_secret` は変更するまで永続的に有効 |
| リプレイ攻撃 | 傍受されたリクエストをそのまま再送される可能性がある |

RFC 7523 では、JWT を使った署名ベースの認証により、これらの課題を軽減します。

- **有効期限付き**: JWT には `exp` クレームがあり、短い有効期限を設定できる
- **一回限りの使用**: `jti` クレームでリプレイ攻撃を防止できる
- **署名による証明**: クライアントが署名を生成したことを検証可能

### 2つの主要なユースケース

#### ユースケース1: JWT クライアント認証

クライアントがトークンエンドポイントにアクセスする際、`client_secret` の代わりに署名付き JWT を送信して自分自身を証明します。

```
POST /token HTTP/1.1
Host: auth.example.com
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code
&code=AUTH_CODE_HERE
&client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer
&client_assertion=eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
```

この方式は OIDC では以下の名前で知られています。

| 方式 | 署名アルゴリズム | 鍵の種類 |
|------|------------------|----------|
| **`client_secret_jwt`** | HMAC（HS256, HS384, HS512） | 共有秘密（`client_secret` を鍵として使用） |
| **`private_key_jwt`** | RSA / ECDSA（RS256, ES256 など） | 公開鍵暗号（クライアントが秘密鍵を保持） |

#### 2つの方式の比較

| 観点 | client_secret_jwt | private_key_jwt |
|------|-------------------|-----------------|
| 鍵管理 | 認可サーバーとクライアントが同じ秘密を共有 | クライアントのみが秘密鍵を保持 |
| セキュリティ | `client_secret` が認可サーバーにも存在 | 秘密鍵はクライアント外に出ない |
| 否認防止 | 認可サーバーも署名を生成可能（否認可能） | クライアントのみが署名可能（否認不可） |
| 導入の容易さ | 既存の `client_secret` をそのまま利用可能 | 鍵ペア生成・公開鍵登録が必要 |
| 用途 | 一般的な OAuth 2.0 クライアント認証 | FAPI など高セキュリティ要件 |

どちらを選ぶかは、セキュリティ要件と運用コストのバランスで決定します。

#### ユースケース2: JWT Bearer グラント

外部システムで発行された JWT を、そのままアクセストークンに交換します。サービス間連携やフェデレーション認証で活用されます。

```
POST /token HTTP/1.1
Host: auth.example.com
Content-Type: application/x-www-form-urlencoded

grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer
&assertion=eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
```

### 関連する仕様

RFC 7523 は単独で存在するわけではなく、いくつかの仕様と関連しています。

```
RFC 7521 (Assertion Framework)
    ├── RFC 7522 (SAML Profile)
    └── RFC 7523 (JWT Profile) ← 本ドキュメント

RFC 7519 (JWT)
    └── JWT の基本構造を定義

OpenID Connect Core
    └── private_key_jwt, client_secret_jwt として参照
```

---

## 第2部: 詳細編

### JWT の構造と必須クレーム

RFC 7523 で使用する JWT には、特定のクレーム（claim）が必須または推奨として定められています。

#### 必須クレーム

| クレーム | 説明 | 例 |
|----------|------|-----|
| `iss` | 発行者。クライアント認証の場合は `client_id` | `"my-client-app"` |
| `sub` | 主体。クライアント認証では `client_id`、Bearer グラントではユーザー識別子 | `"my-client-app"` or `"user-123"` |
| `aud` | 対象者。認可サーバーのトークンエンドポイント URL または識別子 | `"https://auth.example.com/token"` |
| `exp` | 有効期限（Unix タイムスタンプ） | `1735689600` |

#### 推奨クレーム

| クレーム | 説明 | 用途 |
|----------|------|------|
| `iat` | 発行時刻 | JWT がいつ作成されたかを示す |
| `nbf` | 有効開始時刻 | この時刻より前は JWT が無効 |
| `jti` | JWT ID（一意識別子） | リプレイ攻撃の防止 |

#### JWT の例

```json
{
  "iss": "my-client-app",
  "sub": "my-client-app",
  "aud": "https://auth.example.com/token",
  "exp": 1735689600,
  "iat": 1735689300,
  "jti": "unique-jwt-id-12345"
}
```

### クライアント認証の詳細

クライアント認証で JWT を使用する場合、以下のパラメータをトークンリクエストに含めます。

| パラメータ | 値 |
|------------|-----|
| `client_assertion_type` | `urn:ietf:params:oauth:client-assertion-type:jwt-bearer` |
| `client_assertion` | 署名済み JWT |

#### client_secret_jwt の実装例

`client_secret` を HMAC の鍵として使用し、JWT に署名します。

```java
// 1. JWT クレームを構築
JWTClaimsSet claims = new JWTClaimsSet.Builder()
    .issuer(clientId)
    .subject(clientId)
    .audience(tokenEndpoint)
    .expirationTime(Date.from(Instant.now().plusSeconds(300)))
    .issueTime(new Date())
    .jwtID(UUID.randomUUID().toString())
    .build();

// 2. client_secret を鍵として HMAC 署名
SecretKey secretKey = new SecretKeySpec(
    clientSecret.getBytes(StandardCharsets.UTF_8),
    "HmacSHA256"
);
SignedJWT signedJWT = new SignedJWT(
    new JWSHeader(JWSAlgorithm.HS256),
    claims
);
signedJWT.sign(new MACSigner(secretKey));

// 3. トークンリクエストに含める
String clientAssertion = signedJWT.serialize();
```

#### private_key_jwt の実装例

クライアントが保持する秘密鍵で署名し、認可サーバーは公開鍵で検証します。

```java
// 1. JWT クレームを構築
JWTClaimsSet claims = new JWTClaimsSet.Builder()
    .issuer(clientId)
    .subject(clientId)
    .audience(tokenEndpoint)
    .expirationTime(Date.from(Instant.now().plusSeconds(300)))
    .issueTime(new Date())
    .jwtID(UUID.randomUUID().toString())
    .build();

// 2. 秘密鍵で署名
SignedJWT signedJWT = new SignedJWT(
    new JWSHeader.Builder(JWSAlgorithm.RS256)
        .keyID(keyId)
        .build(),
    claims
);
signedJWT.sign(new RSASSASigner(privateKey));

// 3. トークンリクエストに含める
String clientAssertion = signedJWT.serialize();
```

### JWT Bearer グラントの詳細

JWT Bearer グラントは、外部で発行された JWT をアクセストークンに交換する仕組みです。

#### 主なユースケース

1. **サービスアカウント認証** — バックエンドサービスがユーザー介在なしでAPIにアクセス
2. **トークン交換** — 他のIdPから発行されたトークンを自システムのトークンに変換
3. **SAML から OAuth への移行** — SAML アサーションを JWT に変換して使用

#### リクエスト例

```http
POST /token HTTP/1.1
Host: auth.example.com
Content-Type: application/x-www-form-urlencoded

grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer
&assertion=eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJodHRwczovL2V4dGVybmFsLWlkcC5jb20iLCJzdWIiOiJ1c2VyLTEyMyIsImF1ZCI6Imh0dHBzOi8vYXV0aC5leGFtcGxlLmNvbS90b2tlbiIsImV4cCI6MTczNTY4OTYwMH0.signature
&scope=read write
```

### 認可サーバーの検証手順

認可サーバーは受け取った JWT を以下の手順で検証します。

```
1. JWT の形式検証
   └── ヘッダー、ペイロード、署名の3パートに分割できるか

2. 署名検証
   ├── client_secret_jwt: client_secret を HMAC 鍵として検証
   ├── private_key_jwt: 公開鍵を取得（事前登録 or JWKS エンドポイント）
   └── 署名アルゴリズムに従って検証

3. クレーム検証
   ├── iss: 既知のクライアント/発行者か
   ├── sub: 適切な主体か
   ├── aud: 自身のトークンエンドポイントか
   ├── exp: 現在時刻より未来か
   └── nbf: 現在時刻より過去か（存在する場合）

4. リプレイ攻撃対策
   └── jti が過去に使用されていないか（キャッシュで管理）
```

### セキュリティ考慮事項

RFC 7523 を実装する際は、以下のセキュリティ事項に注意してください。

#### 必須の対策

| 対策 | 説明 |
|------|------|
| 短い有効期限 | JWT の有効期限は数分程度に設定する（推奨: 5分以内） |
| リプレイ攻撃対策 | `jti` クレームを使用し、使用済み JWT を一定期間キャッシュ |
| 署名アルゴリズム | `none` や弱いアルゴリズムを拒否。許可するアルゴリズムを明示的に指定 |
| オーディエンス検証 | `aud` クレームが自身のエンドポイントと一致することを必ず確認 |

#### 方式別の注意点

| 方式 | 注意点 |
|------|--------|
| `client_secret_jwt` | `client_secret` は十分な長さ（256ビット以上推奨）を確保。HS256 には最低32バイトの鍵が必要 |
| `private_key_jwt` | 秘密鍵の安全な保管（HSM, KMS の利用を推奨）。定期的な鍵ローテーション |

#### 推奨の対策

| 対策 | 説明 |
|------|------|
| 鍵・シークレットのローテーション | 定期的に署名鍵または `client_secret` を更新 |
| JWKS エンドポイント | `private_key_jwt` の場合、公開鍵を JWKS 形式で提供するとローテーションが容易 |
| TLS 必須 | すべての通信を HTTPS 経由で行う |
| 時刻同期 | サーバー間で NTP による時刻同期を行い、クロックスキューを最小化 |

### FAPI との関係

Financial-grade API（FAPI）では、高いセキュリティが求められるため、RFC 7523 の `private_key_jwt` が実質的に必須となっています。

| FAPI プロファイル | クライアント認証要件 |
|-------------------|----------------------|
| FAPI 1.0 Baseline | `client_secret_jwt` または `private_key_jwt` |
| FAPI 1.0 Advanced | `private_key_jwt` または `tls_client_auth` |
| FAPI 2.0 | `private_key_jwt` または `tls_client_auth`（推奨） |

### まとめ

RFC 7523 は、OAuth 2.0 エコシステムにおける JWT 活用の基盤となる仕様です。

- **クライアント認証** では、`client_secret` を直接送信する代わりに署名付き JWT を使用することで、有効期限やリプレイ攻撃対策を導入可能
  - **`client_secret_jwt`**: 導入が容易。既存の `client_secret` を流用可能
  - **`private_key_jwt`**: より高いセキュリティ。秘密鍵がクライアント外に出ない
- **JWT Bearer グラント** では、外部 IdP との連携やサービスアカウント認証を実現
- **FAPI** などの高セキュリティ要件では `private_key_jwt` が標準だが、一般的な用途では `client_secret_jwt` も有効な選択肢

IDP を実装する際は、用途とセキュリティ要件に応じて適切な方式を選択し、セキュリティ考慮事項を遵守することが重要です。

---

## 参考リンク

- [RFC 7523 - JWT Profile for OAuth 2.0](https://datatracker.ietf.org/doc/html/rfc7523)
- [RFC 7521 - Assertion Framework for OAuth 2.0](https://datatracker.ietf.org/doc/html/rfc7521)
- [RFC 7519 - JSON Web Token (JWT)](https://datatracker.ietf.org/doc/html/rfc7519)
- [OpenID Connect Core 1.0 - Client Authentication](https://openid.net/specs/openid-connect-core-1_0.html#ClientAuthentication)
