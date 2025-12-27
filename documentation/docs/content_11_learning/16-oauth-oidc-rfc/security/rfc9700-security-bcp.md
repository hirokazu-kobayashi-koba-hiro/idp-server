# RFC 9700: OAuth 2.0 Security Best Current Practice

RFC 9700 は、OAuth 2.0 のセキュリティベストプラクティスをまとめた仕様です。このドキュメントでは、主要な推奨事項を解説します。

---

## 第1部: 概要編

### RFC 9700 とは何か？

RFC 9700（旧 OAuth 2.0 Security BCP）は、OAuth 2.0 実装における**セキュリティのベストプラクティス**をまとめた文書です。

RFC 6749（OAuth 2.0 Core）の発行から 10 年以上が経過し、新たな攻撃手法や対策が明らかになったため、それらを集約しています。

### なぜ必要なのか？

| RFC 6749 の問題 | RFC 9700 の対応 |
|----------------|----------------|
| Implicit Grant を許可 | 非推奨、使用禁止を推奨 |
| PKCE は任意 | すべてのクライアントで推奨 |
| redirect_uri の部分一致 | 完全一致を必須化 |
| Bearer トークンのみ | DPoP/mTLS によるバインディングを推奨 |

### 主要な推奨事項サマリー

| カテゴリ | 推奨事項 |
|---------|---------|
| 認可グラント | Authorization Code + PKCE を使用 |
| redirect_uri | 完全一致（exact match）で検証 |
| トークン | Sender-Constrained Token を推奨 |
| リフレッシュトークン | ローテーションまたはバインディング |
| クライアント認証 | 可能な限り強力な方式を使用 |

---

## 第2部: 詳細編

### Authorization Code Grant の強化

#### PKCE の必須化

> Authorization servers SHOULD require the use of PKCE for all OAuth clients.

```
以前: パブリッククライアントのみ PKCE 推奨
現在: すべてのクライアントで PKCE 推奨
```

理由：
- Confidential クライアントでも認可コード横取り攻撃のリスクあり
- PKCE はコストが低く、導入障壁が低い

#### Implicit Grant の廃止

> The implicit grant MUST NOT be used.

```
以前: SPA 向けに Implicit Grant を使用
現在: Authorization Code + PKCE を使用
```

理由：
- アクセストークンが URL フラグメントに露出
- トークン漏洩リスクが高い
- リフレッシュトークンが使えない

#### Resource Owner Password Grant の廃止

> The resource owner password credentials grant MUST NOT be used.

理由：
- クライアントがユーザーのパスワードを知ることになる
- フィッシングに対する防御ができない
- MFA の実装が困難

### redirect_uri の検証

#### 完全一致の強制

> The authorization server MUST compare URIs using exact string matching.

#### ワイルドカードの禁止

> Authorization servers SHOULD NOT allow clients to register redirect URI patterns.

```
禁止:
  https://app.example.com/callback/*
  https://*.example.com/callback

許可:
  https://app.example.com/callback
  https://app.example.com/oauth/callback
```

#### localhost の扱い

> Clients SHOULD NOT use localhost as the hostname.

```
非推奨: http://localhost:8080/callback
推奨:   http://127.0.0.1:8080/callback
推奨:   http://[::1]:8080/callback
```

理由：DNS リバインディング攻撃のリスク。

### Sender-Constrained Tokens

#### 概念

> Access tokens SHOULD be sender-constrained.

従来の Bearer トークンは「持っている人が使える」。Sender-Constrained Token は「特定のクライアントのみが使える」。

| 方式 | 仕組み |
|------|--------|
| mTLS（RFC 8705） | トークンをクライアント証明書にバインド |
| DPoP（RFC 9449） | トークンを DPoP 鍵にバインド |

#### DPoP の推奨

```
リソースリクエスト:
GET /resource HTTP/1.1
Authorization: DPoP <access_token>
DPoP: <proof_jwt>

リソースサーバーは:
1. トークンから公開鍵のサムプリントを取得
2. DPoP Proof の公開鍵と照合
3. 一致しなければ拒否
```

### リフレッシュトークンのセキュリティ

#### ローテーション

> Authorization servers SHOULD rotate refresh tokens on each use.

```
1回目のリフレッシュ:
  refresh_token_v1 → access_token_new + refresh_token_v2

2回目のリフレッシュ:
  refresh_token_v2 → access_token_new + refresh_token_v3

もし refresh_token_v1 が使われたら:
  → トークン漏洩の可能性 → すべて無効化
```

#### Sender-Constrained Refresh Token

> Refresh tokens SHOULD be sender-constrained or rotated on each use.

```
mTLS バインディング:
  リフレッシュトークンを証明書にバインド
  同じ証明書を提示しないと使用不可

DPoP バインディング:
  リフレッシュトークンを DPoP 鍵にバインド
```

### クライアント認証

#### 強力な認証方式の推奨

> Confidential clients SHOULD use asymmetric cryptography for client authentication.

| 方式 | 強度 | 推奨度 |
|------|------|--------|
| client_secret_basic | 低 | ⚠️ |
| client_secret_post | 低 | ⚠️ |
| client_secret_jwt | 中 | △ |
| private_key_jwt | 高 | ✅ |
| tls_client_auth | 高 | ✅ |

### トークンの有効期限

#### 短い有効期限

> Access tokens SHOULD have a limited lifetime.

| トークンタイプ | 推奨有効期限 |
|---------------|-------------|
| アクセストークン | 数分〜1時間 |
| 認可コード | 数秒〜数分（使い捨て） |
| リフレッシュトークン | 用途による |

### クロスサイト攻撃対策

#### state パラメータ

> The client MUST use the state parameter for CSRF protection.

```
1. クライアントが state を生成してセッションに保存
2. 認可リクエストに state を含める
3. コールバックで state を検証
```

#### PKCE

PKCE は CSRF 対策としても機能する（state と併用を推奨）。

### オープンリダイレクタ対策

#### redirect_uri の厳密な検証

```
攻撃シナリオ:
1. 攻撃者が改ざんした redirect_uri を含む認可リクエストを生成
2. ユーザーをフィッシングサイトに誘導
3. 認可コードを攻撃者のサーバーに送らせる

対策:
- redirect_uri の完全一致検証
- 事前登録された URI のみ許可
```

### Mix-Up 攻撃対策

#### 問題

複数の IdP を使用する場合、攻撃者が認可レスポンスを別の IdP 向けに見せかける攻撃。

#### 対策

> Authorization servers SHOULD include the iss parameter in authorization responses.

```http
HTTP/1.1 302 Found
Location: https://client.example.org/callback?
  code=SplxlOBeZQQYbYS6WxSbIA
  &state=xyz
  &iss=https://auth.example.com  ← 認可サーバーの識別子
```

クライアントは `iss` が期待する認可サーバーと一致することを確認。

### 認可コードインジェクション対策

#### 問題

攻撃者が自分の認可コードを被害者のセッションに注入。

#### 対策

1. **PKCE**: code_verifier はセッションに紐づく
2. **nonce**: ID Token に含まれる nonce を検証
3. **state**: セッションに紐づく state を検証

### 実装チェックリスト

#### 認可サーバー

- [ ] PKCE を必須化
- [ ] Implicit Grant を無効化
- [ ] redirect_uri の完全一致検証
- [ ] 認可コードの一回限り使用
- [ ] リフレッシュトークンのローテーション
- [ ] iss パラメータの返却
- [ ] DPoP/mTLS のサポート

#### クライアント

- [ ] Authorization Code + PKCE を使用
- [ ] state パラメータの使用
- [ ] iss パラメータの検証
- [ ] トークンの安全な保存
- [ ] HTTPS の使用

#### リソースサーバー

- [ ] トークンの署名検証
- [ ] 有効期限の検証
- [ ] aud/scope の検証
- [ ] Sender-Constrained Token の検証（DPoP/mTLS）

### 推奨事項のまとめ

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        RFC 9700 推奨事項                                 │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ✅ 使うべき                      ❌ 使うべきでない                      │
│  ────────────────                ────────────────────                  │
│  Authorization Code + PKCE        Implicit Grant                       │
│  private_key_jwt / mTLS           Resource Owner Password Grant        │
│  DPoP / mTLS バインディング        client_secret_basic (可能なら)       │
│  redirect_uri 完全一致            redirect_uri ワイルドカード           │
│  リフレッシュトークンローテーション  長寿命アクセストークン                │
│  iss パラメータ                   localhost                            │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 参考リンク

- [RFC 9700 - OAuth 2.0 Security Best Current Practice](https://datatracker.ietf.org/doc/html/rfc9700)
- [RFC 7636 - PKCE](https://datatracker.ietf.org/doc/html/rfc7636)
- [RFC 9449 - DPoP](https://datatracker.ietf.org/doc/html/rfc9449)
- [RFC 8705 - mTLS](https://datatracker.ietf.org/doc/html/rfc8705)
