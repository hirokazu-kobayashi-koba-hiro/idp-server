# FAPI 1.0 Baseline Profile

FAPI 1.0 Baseline Profile は、金融 API 向けの OAuth 2.0 / OIDC セキュリティプロファイルの基本レベルです。

---

## 第1部: 概要編

### FAPI とは？

FAPI（Financial-grade API）は、OpenID Foundation が策定した**金融グレード**のセキュリティプロファイルです。

```
FAPI の目的:
  - 金融機関の API を安全に公開
  - オープンバンキング、PSD2 への対応
  - 高度なセキュリティ要件の標準化

FAPI 1.0 の構成:
  ┌─────────────────────────────────────┐
  │        FAPI 1.0 Advanced            │ ← より高いセキュリティ
  ├─────────────────────────────────────┤
  │        FAPI 1.0 Baseline            │ ← 基本レベル
  ├─────────────────────────────────────┤
  │     OAuth 2.0 / OpenID Connect      │
  └─────────────────────────────────────┘
```

### Baseline vs Advanced

| 項目 | Baseline | Advanced |
|------|----------|----------|
| リスクレベル | 中程度 | 高 |
| 用途 | 読み取り専用 API | 書き込み/決済 API |
| クライアント認証 | 機密クライアント推奨 | private_key_jwt/mTLS 必須 |
| レスポンス保護 | なし | JARM または PAR+FAPI |
| ID トークン署名 | PS256/ES256 推奨 | PS256/ES256 必須 |

---

## 第2部: 詳細編

### 認可サーバーの要件

#### 必須要件

| 要件 | 説明 |
|------|------|
| TLS 1.2+ | すべての通信を暗号化 |
| PKCE | パブリッククライアントに必須 |
| redirect_uri 完全一致 | ワイルドカード禁止 |
| state の検証 | CSRF 防止 |
| nonce の検証 | リプレイ防止 |

#### 推奨要件

| 要件 | 説明 |
|------|------|
| 機密クライアント | クライアント認証を推奨 |
| 認可コードの短い有効期限 | 通常 1 分以内 |
| リフレッシュトークンローテーション | 漏洩対策 |

### クライアント認証

```
Baseline で許可されるクライアント認証:

1. client_secret_basic / client_secret_post
   - 許可されるが非推奨
   - 秘密がネットワークを流れる

2. client_secret_jwt
   - 対称鍵 JWT で認証
   - 秘密は直接送信されない

3. private_key_jwt（推奨）
   - 非対称鍵 JWT で認証
   - 最も安全

4. tls_client_auth / self_signed_tls_client_auth
   - mTLS で認証
   - 証明書ベース
```

### ID トークンの要件

```json
{
  "iss": "https://auth.example.com",
  "sub": "user-123",
  "aud": "s6BhdRkqt3",
  "exp": 1704153600,
  "iat": 1704150000,
  "nonce": "n-0S6_WzA2Mj",
  "s_hash": "abc123...",
  "c_hash": "def456...",
  "auth_time": 1704149900,
  "acr": "urn:example:acr:loa2"
}
```

| クレーム | 必須 | 説明 |
|---------|------|------|
| `s_hash` | 条件付き | state のハッシュ（response_type に token/id_token 含む場合） |
| `c_hash` | 条件付き | code のハッシュ（response_type に code 含む場合） |
| `auth_time` | 推奨 | 認証時刻 |
| `acr` | 推奨 | 認証コンテキストクラス |

### 署名アルゴリズム

```
Baseline で許可される署名アルゴリズム:

ID トークン:
  ✅ PS256, PS384, PS512  - RSASSA-PSS
  ✅ ES256, ES384, ES512  - ECDSA
  ⚠️ RS256, RS384, RS512  - 許可されるが非推奨
  ❌ none                 - 禁止

トークンエンドポイント認証:
  ✅ PS256, ES256 推奨
  ⚠️ HS256 許可（client_secret_jwt）
```

### redirect_uri の検証

```
要件:
  - 完全一致での検証
  - ワイルドカード禁止
  - ローカルホストは許可されない（本番環境）
  - HTTPS 必須（localhost を除く）

例:
  登録: https://client.example.com/callback
  リクエスト: https://client.example.com/callback ✅
  リクエスト: https://client.example.com/callback?extra=1 ❌
  リクエスト: https://client.example.com/Callback ❌（大文字小文字区別）
```

### 認可リクエストの例

```http
GET /authorize?
  response_type=code
  &client_id=s6BhdRkqt3
  &redirect_uri=https://client.example.com/callback
  &scope=openid accounts
  &state=af0ifjsldkj
  &nonce=n-0S6_WzA2Mj
  &code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM
  &code_challenge_method=S256
```

### トークンリクエストの例

```http
POST /token HTTP/1.1
Host: auth.example.com
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code
&code=SplxlOBeZQQYbYS6WxSbIA
&redirect_uri=https://client.example.com/callback
&client_id=s6BhdRkqt3
&client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer
&client_assertion=eyJhbGciOiJQUzI1NiIsInR5cCI6IkpXVCJ9...
&code_verifier=dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk
```

### リソースサーバーの要件

```
アクセストークンの検証:

1. トークンの形式検証
   - JWT の場合は署名を検証
   - 参照トークンの場合はイントロスペクション

2. 有効期限の検証
   - exp クレームを確認

3. 発行者の検証
   - iss が信頼された認可サーバーか

4. 対象者の検証
   - aud が自分（リソースサーバー）を含むか

5. スコープの検証
   - 要求されたアクションが許可されているか
```


### セキュリティチェックリスト

| 項目 | 確認内容 |
|------|----------|
| TLS | 1.2 以上を使用 |
| PKCE | S256 を使用 |
| state | ランダムで十分な長さ |
| nonce | ID トークンで検証 |
| redirect_uri | 完全一致で登録・検証 |
| クライアント認証 | 機密クライアントを推奨 |
| 署名アルゴリズム | PS256 または ES256 |

---

## 参考リンク

- [FAPI 1.0 Baseline Profile](https://openid.net/specs/openid-financial-api-part-1-1_0.html)
- [FAPI 1.0 Advanced Profile](https://openid.net/specs/openid-financial-api-part-2-1_0.html)
- [FAPI Implementation Guide](https://openid.net/wg/fapi/specifications/)
