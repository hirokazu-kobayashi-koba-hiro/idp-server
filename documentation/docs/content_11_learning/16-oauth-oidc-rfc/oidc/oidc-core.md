# OpenID Connect Core 1.0

OpenID Connect（OIDC）は、OAuth 2.0 に Identity レイヤーを追加した ID 連携プロトコルです。このドキュメントでは、OIDC Core の仕組みを解説します。

---

## 第1部: 概要編

### OpenID Connect とは何か？

OpenID Connect は、**IdP（Identity Provider）が認証したユーザーの情報を RP（Relying Party）に提供する**ための ID 連携（Identity Federation）プロトコルです。

```
┌────────────┐                                      ┌────────────┐
│     RP     │                                      │    IdP     │
│ (Webアプリ) │                                      │ (認証基盤)  │
└─────┬──────┘                                      └──────┬─────┘
      │                                                    │
      │  「このユーザーは誰？」                              │
      │ ──────────────────────────────────────────────────►│
      │                                                    │
      │                                    IdP がユーザーを認証
      │                                                    │
      │  ID Token（認証結果 + ユーザー情報）                 │
      │ ◄──────────────────────────────────────────────────│
      │                                                    │
      │  RP は受け取った情報を使って                         │
      │  ・新規ユーザー登録（身元確認）                       │
      │  ・既存ユーザーのログイン（当人認証）                  │
      │  を行う                                             │
```

**重要**: 「OIDC は認証」という説明は不正確。認証を行うのは IdP であり、OIDC は IdP が認証した結果を RP に伝えるためのプロトコル。

### OAuth 2.0 と OIDC の関係

| OAuth 2.0 | OpenID Connect |
|-----------|----------------|
| 認可のためのフレームワーク | OAuth 2.0 + Identity レイヤー |
| 「何にアクセスできるか」を扱う | 「誰であるか」を扱う |
| アクセストークンを発行 | **ID トークン**を追加で発行 |
| ユーザー情報の形式は未定義 | ユーザー情報の形式を標準化 |

### OIDC の主要コンポーネント

| コンポーネント | 説明 |
|---------------|------|
| ID Token | ユーザーの認証情報を含む署名付き JWT |
| UserInfo Endpoint | ユーザーの属性情報を取得する API |
| 標準クレーム | ユーザー情報の標準的な表現方法 |
| 認証コンテキスト | いつ・どのように認証されたかの情報 |

---

## 第2部: 詳細編

### 3 つの認証フロー

OIDC は OAuth 2.0 のフローを拡張し、3 つの認証フローを定義。

| フロー | response_type | 用途 |
|--------|---------------|------|
| Authorization Code Flow | `code` | サーバーサイドアプリ（推奨） |
| Implicit Flow | `id_token` / `id_token token` | SPA（非推奨） |
| Hybrid Flow | `code id_token` など | 特殊なユースケース |

#### Authorization Code Flow

最も一般的で安全なフロー。

```
┌────────┐          ┌────────┐          ┌────────┐
│  User  │          │   RP   │          │  IdP   │
└───┬────┘          └───┬────┘          └───┬────┘
    │                   │                   │
    │  (1) ログイン要求  │                   │
    │ ─────────────────►│                   │
    │                   │                   │
    │  (2) 認可リクエスト │                   │
    │ ◄─────────────────│                   │
    │                   │                   │
    │  (3) IdP にリダイレクト                │
    │ ──────────────────────────────────────►│
    │                   │                   │
    │  (4) ユーザー認証 + 同意               │
    │ ◄────────────────────────────────────►│
    │                   │                   │
    │  (5) 認可コード    │                   │
    │ ◄──────────────────────────────────────│
    │ ─────────────────►│                   │
    │                   │                   │
    │                   │  (6) トークンリクエスト
    │                   │ ─────────────────►│
    │                   │                   │
    │                   │  (7) ID Token + Access Token
    │                   │ ◄─────────────────│
    │                   │                   │
    │  (8) ログイン完了  │                   │
    │ ◄─────────────────│                   │
```

### 認可リクエスト

```http
GET /authorize?
  response_type=code
  &client_id=s6BhdRkqt3
  &redirect_uri=https%3A%2F%2Fclient.example.org%2Fcallback
  &scope=openid%20profile%20email
  &state=af0ifjsldkj
  &nonce=n-0S6_WzA2Mj
HTTP/1.1
Host: idp.example.com
```

| パラメータ | 必須 | 説明 |
|-----------|------|------|
| `response_type` | ✅ | `code`（Authorization Code Flow） |
| `client_id` | ✅ | クライアント識別子 |
| `redirect_uri` | ✅ | コールバック URL |
| `scope` | ✅ | **`openid` を含む**ことが必須 |
| `state` | 推奨 | CSRF 対策 |
| `nonce` | 推奨 | リプレイ攻撃対策 |

#### scope の値

| scope | 取得できる情報 |
|-------|---------------|
| `openid` | **必須**。OIDC リクエストであることを示す |
| `profile` | name, family_name, given_name, picture など |
| `email` | email, email_verified |
| `address` | address |
| `phone` | phone_number, phone_number_verified |

### ID Token

ID Token は、IdP がユーザーを認証したことを証明する**署名付き JWT**。

```json
{
  "iss": "https://idp.example.com",
  "sub": "24400320",
  "aud": "s6BhdRkqt3",
  "exp": 1704070800,
  "iat": 1704067200,
  "auth_time": 1704067180,
  "nonce": "n-0S6_WzA2Mj",
  "acr": "urn:mace:incommon:iap:silver",
  "amr": ["pwd", "otp"],
  "azp": "s6BhdRkqt3"
}
```

#### ID Token の必須クレーム

| クレーム | 説明 |
|---------|------|
| `iss` | IdP の識別子（URL） |
| `sub` | ユーザーの識別子（IdP 内で一意） |
| `aud` | 対象の RP（client_id） |
| `exp` | 有効期限 |
| `iat` | 発行時刻 |

#### ID Token のオプションクレーム

| クレーム | 説明 |
|---------|------|
| `auth_time` | ユーザーが認証された時刻 |
| `nonce` | リクエスト時に送った nonce（一致確認必須） |
| `acr` | 認証コンテキストクラス（認証の強度） |
| `amr` | 認証方法（pwd, otp, fido など） |
| `azp` | 認可された当事者 |

### ID Token の検証

RP は ID Token を受け取ったら、以下を検証する必要があります。

```
1. 署名検証
   └── IdP の公開鍵（JWKS エンドポイントから取得）で検証

2. iss 検証
   └── 期待する IdP の識別子と一致するか

3. aud 検証
   └── 自分の client_id が含まれているか

4. exp 検証
   └── 現在時刻 < exp（有効期限内か）

5. iat 検証
   └── 発行時刻が許容範囲内か

6. nonce 検証（リクエスト時に送った場合）
   └── リクエスト時の nonce と一致するか

7. acr 検証（要求した場合）
   └── 要求した認証レベルを満たしているか
```

### UserInfo Endpoint

ID Token に含まれない追加のユーザー情報を取得。

```http
GET /userinfo HTTP/1.1
Host: idp.example.com
Authorization: Bearer SlAV32hkKG
```

レスポンス：

```json
{
  "sub": "24400320",
  "name": "Jane Doe",
  "given_name": "Jane",
  "family_name": "Doe",
  "email": "janedoe@example.com",
  "email_verified": true,
  "picture": "https://example.com/janedoe/photo.jpg"
}
```

### 標準クレーム

OIDC で定義されている標準的なユーザー属性。

| クレーム | 説明 | 例 |
|---------|------|-----|
| `sub` | 識別子 | `"24400320"` |
| `name` | フルネーム | `"Jane Doe"` |
| `given_name` | 名 | `"Jane"` |
| `family_name` | 姓 | `"Doe"` |
| `email` | メールアドレス | `"janedoe@example.com"` |
| `email_verified` | メール確認済みか | `true` |
| `phone_number` | 電話番号 | `"+1-555-555-5555"` |
| `address` | 住所 | `{"country": "JP", ...}` |
| `birthdate` | 生年月日 | `"1990-01-15"` |
| `picture` | プロフィール画像 URL | `"https://..."` |

### Discovery

IdP の設定情報を自動検出するための仕組み。

```http
GET /.well-known/openid-configuration HTTP/1.1
Host: idp.example.com
```

レスポンス：

```json
{
  "issuer": "https://idp.example.com",
  "authorization_endpoint": "https://idp.example.com/authorize",
  "token_endpoint": "https://idp.example.com/token",
  "userinfo_endpoint": "https://idp.example.com/userinfo",
  "jwks_uri": "https://idp.example.com/.well-known/jwks.json",
  "scopes_supported": ["openid", "profile", "email"],
  "response_types_supported": ["code", "id_token", "token id_token"],
  "subject_types_supported": ["public", "pairwise"],
  "id_token_signing_alg_values_supported": ["RS256", "ES256"]
}
```

### Subject Identifier Types

ユーザー識別子（sub）の生成方式。

| タイプ | 説明 |
|--------|------|
| `public` | 全ての RP に同じ sub を返す |
| `pairwise` | RP ごとに異なる sub を返す（プライバシー保護） |

```
Public:
  RP-A: sub = "user-123"
  RP-B: sub = "user-123"  ← 同じ

Pairwise:
  RP-A: sub = "abc123..."  ← RP-A 用に生成
  RP-B: sub = "xyz789..."  ← RP-B 用に生成（異なる）
```

### 実装例（RP 側）

```java
@Controller
public class OIDCLoginController {
    
    private final OIDCClient oidcClient;
    
    @GetMapping("/login")
    public String login(HttpSession session) {
        // 1. state と nonce を生成
        String state = UUID.randomUUID().toString();
        String nonce = UUID.randomUUID().toString();
        session.setAttribute("oidc_state", state);
        session.setAttribute("oidc_nonce", nonce);
        
        // 2. 認可リクエスト URL を構築
        String authUrl = UriComponentsBuilder
            .fromHttpUrl(oidcClient.getAuthorizationEndpoint())
            .queryParam("response_type", "code")
            .queryParam("client_id", oidcClient.getClientId())
            .queryParam("redirect_uri", oidcClient.getRedirectUri())
            .queryParam("scope", "openid profile email")
            .queryParam("state", state)
            .queryParam("nonce", nonce)
            .build()
            .toUriString();
        
        return "redirect:" + authUrl;
    }
    
    @GetMapping("/callback")
    public String callback(
            @RequestParam String code,
            @RequestParam String state,
            HttpSession session) {
        
        // 3. state 検証
        String savedState = (String) session.getAttribute("oidc_state");
        if (!state.equals(savedState)) {
            throw new SecurityException("Invalid state");
        }
        
        // 4. トークンリクエスト
        TokenResponse tokens = oidcClient.exchangeCode(code);
        
        // 5. ID Token 検証
        String savedNonce = (String) session.getAttribute("oidc_nonce");
        IDTokenClaims claims = oidcClient.validateIdToken(
            tokens.getIdToken(), 
            savedNonce
        );
        
        // 6. ユーザー情報を使ってログイン処理
        User user = userService.findOrCreateByOIDC(claims);
        session.setAttribute("user", user);
        
        return "redirect:/home";
    }
}
```

### セキュリティ考慮事項

| 項目 | 推奨事項 |
|------|----------|
| state | 必須。CSRF 対策 |
| nonce | 必須。リプレイ攻撃対策 |
| ID Token 検証 | すべてのクレームを検証 |
| HTTPS | 必須 |
| PKCE | 推奨（特にパブリッククライアント） |

---

## 参考リンク

- [OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html)
- [OpenID Connect Discovery 1.0](https://openid.net/specs/openid-connect-discovery-1_0.html)
- [OpenID Connect Dynamic Client Registration](https://openid.net/specs/openid-connect-registration-1_0.html)
