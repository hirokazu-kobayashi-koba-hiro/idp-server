# トークンセキュリティ

OAuth 2.0 / OIDC におけるトークンのセキュリティに関するベストプラクティスをまとめます。

---

## 第1部: トークンの種類と特性

### トークンの分類

| トークン | 用途 | 有効期限 | 保存場所 |
|---------|------|----------|----------|
| アクセストークン | リソースアクセス | 短い（数分〜数時間） | メモリ |
| リフレッシュトークン | トークン更新 | 長い（数日〜数ヶ月） | 安全なストレージ |
| ID トークン | 認証結果 | 短い（数分〜1時間） | メモリ |
| 認可コード | トークン取得 | 非常に短い（数分） | メモリ |

### トークンの形式

```
参照トークン（Opaque Token）:
  - ランダムな文字列
  - 認可サーバーで検証が必要
  - 例: "eyJ0eXAiOiJKV1QiLC..."（実際はランダム）

自己完結型トークン（JWT）:
  - クレームを含む
  - リソースサーバーで直接検証可能
  - 例: ヘッダー.ペイロード.署名

  {
    "iss": "https://auth.example.com",
    "sub": "user-123",
    "aud": "api.example.com",
    "exp": 1704153600,
    "scope": "read write"
  }
```

---

## 第2部: アクセストークンのセキュリティ

### 有効期限の設定

```
推奨される有効期限:

一般的な Web アプリ:
  アクセストークン: 1時間
  リフレッシュトークン: 7日〜30日

高セキュリティ環境（金融など）:
  アクセストークン: 5分〜15分
  リフレッシュトークン: 1日

M2M（サーバー間通信）:
  アクセストークン: 1時間
  リフレッシュトークン: なし（client_credentials）
```

### トークンバインディング

#### DPoP（Demonstration of Proof-of-Possession）

```
DPoP の仕組み:

1. クライアントが鍵ペアを生成
2. トークンリクエストに DPoP Proof を添付
3. アクセストークンが公開鍵にバインド
4. リソースアクセス時も DPoP Proof が必要

  ┌────────┐  DPoP Proof + 公開鍵  ┌────────┐
  │ Client │ ─────────────────────► │   AS   │
  │        │ ◄───────────────────── │        │
  └────────┘  cnf.jkt 付きトークン    └────────┘
       │
       │  トークン + DPoP Proof
       ▼
  ┌────────┐
  │   RS   │ ── cnf.jkt と DPoP Proof の公開鍵を比較
  └────────┘

利点:
  - トークンが盗まれても秘密鍵がないと使用不可
  - リプレイ攻撃を防止
```

#### mTLS（Mutual TLS）

```
mTLS トークンバインディング:

1. クライアントが証明書を提示して TLS 接続
2. 認可サーバーが証明書のハッシュをトークンに含める
3. リソースサーバーも mTLS を要求
4. 証明書のハッシュを比較

  ┌────────┐  TLS + クライアント証明書  ┌────────┐
  │ Client │ ─────────────────────────► │   AS   │
  │        │ ◄─────────────────────────│        │
  └────────┘  cnf.x5t#S256 付きトークン  └────────┘
       │
       │  TLS + クライアント証明書
       ▼
  ┌────────┐
  │   RS   │ ── cnf.x5t#S256 と証明書のハッシュを比較
  └────────┘

利点:
  - 既存の TLS インフラを活用
  - 強力なクライアント認証
```

### スコープの最小化

```
原則: 必要最小限のスコープのみ要求

悪い例:
  scope=openid profile email address phone admin

良い例:
  scope=openid profile

リソースサーバーごとに異なるスコープ:
  API A 用: scope=api-a:read
  API B 用: scope=api-b:write
```

### audience の制限

```
RFC 8707 Resource Indicators:

認可リクエスト:
  resource=https://api-a.example.com
  resource=https://api-b.example.com

トークンリクエスト:
  resource=https://api-a.example.com
  → API-A 専用のトークンを取得

JWT:
  {
    "aud": "https://api-a.example.com",
    ...
  }

リソースサーバーは aud を検証し、
自分宛て以外のトークンを拒否
```

---

## 第3部: リフレッシュトークンのセキュリティ

### トークンローテーション

```
リフレッシュトークンローテーション:

1. リフレッシュトークン A を使用
2. 新しいアクセストークン + リフレッシュトークン B を受信
3. リフレッシュトークン A は無効化

  ┌────────┐   Refresh A    ┌────────┐
  │ Client │ ─────────────► │   AS   │
  │        │ ◄───────────── │        │
  └────────┘  Access + Refresh B
                            (A は無効化)

利点:
  - 漏洩したトークンの使用を検出可能
  - 攻撃者と正規ユーザーが競合

検出メカニズム:
  - 無効化された Refresh A が再使用される
  - → すべてのトークンを無効化
  - → ユーザーに再認証を要求
```

### Sender-Constrained Refresh Token

```
リフレッシュトークンもバインディング:

DPoP の場合:
  - リフレッシュトークンも DPoP にバインド
  - リフレッシュリクエストにも DPoP Proof が必要

mTLS の場合:
  - リフレッシュトークンも証明書にバインド
  - リフレッシュリクエストも mTLS が必要
```

### 保存場所

```
Web アプリケーション:

推奨:
  - HttpOnly Cookie（SameSite=Strict）
  - バックエンドセッションに保存

非推奨:
  - localStorage（XSS で漏洩）
  - sessionStorage（タブ間で共有不可）

モバイルアプリ:

推奨:
  - iOS: Keychain
  - Android: EncryptedSharedPreferences

非推奨:
  - SharedPreferences（暗号化なし）
  - ファイルストレージ
```

---

## 第4部: JWT のセキュリティ

### 署名アルゴリズム

```
推奨:
  ES256  - ECDSA with P-256 and SHA-256
  RS256  - RSASSA-PKCS1-v1_5 with SHA-256
  PS256  - RSASSA-PSS with SHA-256
  EdDSA  - Edwards-curve Digital Signature

非推奨:
  HS256  - 共有秘密鍵が必要（特定のケースのみ）
  none   - 署名なし（絶対に禁止）

鍵サイズ:
  RSA: 2048 ビット以上
  ECDSA: P-256 以上
```

### 署名検証

```java
// 署名検証の実装例
public JWT validateToken(String token) {
    SignedJWT jwt = SignedJWT.parse(token);

    // 1. アルゴリズムの検証
    JWSAlgorithm alg = jwt.getHeader().getAlgorithm();
    if (!allowedAlgorithms.contains(alg)) {
        throw new InvalidTokenException("Unsupported algorithm: " + alg);
    }

    // 2. 署名の検証
    JWKSet jwks = fetchJWKS(jwt.getHeader().getKeyID());
    JWSVerifier verifier = new RSASSAVerifier(jwks.getKey());
    if (!jwt.verify(verifier)) {
        throw new InvalidTokenException("Invalid signature");
    }

    // 3. クレームの検証
    JWTClaimsSet claims = jwt.getJWTClaimsSet();

    // iss の検証
    if (!expectedIssuer.equals(claims.getIssuer())) {
        throw new InvalidTokenException("Invalid issuer");
    }

    // aud の検証
    if (!claims.getAudience().contains(myAudience)) {
        throw new InvalidTokenException("Invalid audience");
    }

    // exp の検証
    if (claims.getExpirationTime().before(new Date())) {
        throw new InvalidTokenException("Token expired");
    }

    // nbf の検証（あれば）
    if (claims.getNotBeforeTime() != null &&
        claims.getNotBeforeTime().after(new Date())) {
        throw new InvalidTokenException("Token not yet valid");
    }

    return jwt;
}
```

### typ ヘッダーの検証

```
JWT の混同を防ぐ:

アクセストークン:
  "typ": "at+jwt"

ID トークン:
  "typ": "JWT"

Logout Token:
  "typ": "logout+jwt"

検証:
  受信したトークンの typ が期待値と一致するか確認
  → 別の用途の JWT を誤って受け入れることを防止
```

### ネストされた JWT（Sign-then-Encrypt）

```
機密情報を含む場合:

1. 署名（JWS）
   claims → 署名 → signed_jwt

2. 暗号化（JWE）
   signed_jwt → 暗号化 → encrypted_jwe

復号側:
1. JWE を復号 → signed_jwt を取得
2. JWS の署名を検証 → claims を取得

ヘッダー:
  {
    "alg": "RSA-OAEP-256",
    "enc": "A256GCM",
    "cty": "JWT"  ← ネストを示す
  }
```

---

## 第5部: トークンの保存と転送

### クライアント側の保存

```
Web アプリケーション（SPA）:

オプション 1: メモリのみ
  - リフレッシュ時に再取得
  - ページリロードでログアウト

オプション 2: BFF パターン
  - トークンはバックエンドで管理
  - フロントエンドはセッション Cookie のみ

オプション 3: Service Worker
  - トークンを Service Worker で管理
  - XSS からの保護が向上

避けるべき:
  - localStorage（XSS で漏洩）
  - Cookie（CSRF のリスク、SameSite で軽減可能）
```

### サーバー側の保存

```
リフレッシュトークンの保存:

データベース:
  - ハッシュ化して保存
  - トークン自体を保存しない

  CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    token_hash VARCHAR(256) NOT NULL,  -- SHA-256 hash
    user_id UUID NOT NULL,
    client_id VARCHAR(256) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP
  );

Redis など:
  - TTL を設定
  - 暗号化して保存
```

### 転送時のセキュリティ

```
HTTP ヘッダー:

Authorization: Bearer eyJhbGciOiJSUzI1NiIs...

セキュリティヘッダー:
  Strict-Transport-Security: max-age=31536000; includeSubDomains
  Cache-Control: no-store
  Pragma: no-cache

Cookie の場合:
  Set-Cookie: access_token=...; HttpOnly; Secure; SameSite=Strict

ログに記録しない:
  - Authorization ヘッダーをマスク
  - トークン全体をログに出力しない
```

---

## 第6部: トークンの取り消し

### リフレッシュトークンの取り消し

```http
POST /revoke HTTP/1.1
Host: auth.example.com
Content-Type: application/x-www-form-urlencoded
Authorization: Basic czZCaGRSa3F0MzpnWDFmQmF0M2JW

token=tGzv3JOkF0XG5Qx2TlKWIA
&token_type_hint=refresh_token
```

### アクセストークンの取り消し

```
JWT アクセストークンの課題:
  - 自己完結型なので即座に無効化できない
  - 有効期限まで有効

解決策:

1. 短い有効期限
   - 5分〜15分
   - 漏洩時の影響を限定

2. トークンブラックリスト
   - 取り消されたトークンの jti をキャッシュ
   - リソースサーバーで確認

3. イントロスペクション
   - 認可サーバーに都度確認
   - パフォーマンスへの影響あり

4. Token Revocation Event
   - リアルタイムでリソースサーバーに通知
   - 複雑な実装が必要
```

### セッション終了時の処理

```
ログアウト時:

1. リフレッシュトークンを取り消し
2. アクセストークンをブラックリストに追加（オプション）
3. Back-Channel Logout で RP に通知
4. クライアント側のトークンを削除

async function logout() {
  // リフレッシュトークンを取り消し
  await revokeRefreshToken(refreshToken);

  // ローカルのトークンを削除
  localStorage.removeItem('access_token');
  localStorage.removeItem('refresh_token');
  sessionStorage.clear();

  // OP のログアウトエンドポイントにリダイレクト
  window.location.href = logoutUrl;
}
```

---

## 参考リンク

- [RFC 9700 - OAuth 2.0 Security Best Current Practice](https://datatracker.ietf.org/doc/html/rfc9700)
- [RFC 9449 - OAuth 2.0 Demonstrating Proof of Possession (DPoP)](https://datatracker.ietf.org/doc/html/rfc9449)
- [RFC 8705 - OAuth 2.0 Mutual-TLS Client Authentication and Certificate-Bound Access Tokens](https://datatracker.ietf.org/doc/html/rfc8705)
- [RFC 7009 - OAuth 2.0 Token Revocation](https://datatracker.ietf.org/doc/html/rfc7009)
- [JWT Best Current Practices (RFC 8725)](https://datatracker.ietf.org/doc/html/rfc8725)
