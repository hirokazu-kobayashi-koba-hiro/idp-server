# RFC 7636: PKCE（Proof Key for Code Exchange）

RFC 7636 は、認可コード横取り攻撃を防ぐための OAuth 2.0 拡張です。このドキュメントでは、PKCE の仕組みと実装方法を解説します。

---

## 第1部: 概要編

### PKCE とは何か？

PKCE（ピクシーと読む）は「**Proof Key for Code Exchange**」の略で、OAuth 2.0 Authorization Code Grant のセキュリティを強化する仕様です。

元々はモバイルアプリ向けに設計されましたが、現在は**すべてのクライアントで使用が推奨**されています。

### なぜ PKCE が必要なのか？

Authorization Code Grant には「**認可コード横取り攻撃**」という脆弱性があります。

#### 攻撃シナリオ（PKCE なし）

```
    正規アプリ                 攻撃者                 認可サーバー
       │                        │                        │
       │  (1) 認可リクエスト     │                        │
       │ ──────────────────────────────────────────────►│
       │                        │                        │
       │  (2) 認可コード         │                        │
       │ ◄──────────────────────┼────────────────────────│
       │        ↓ 横取り！      │                        │
       │                        │  (3) トークンリクエスト │
       │                        │ ─────────────────────►│
       │                        │                        │
       │                        │  (4) アクセストークン   │
       │                        │ ◄─────────────────────│
       │                        │                        │
```

モバイル OS では、カスタム URL スキーム（`myapp://callback`）が他のアプリに乗っ取られる可能性があります。攻撃者は認可コードを横取りし、それを使ってアクセストークンを取得できてしまいます。

### PKCE の解決策

PKCE では、認可リクエスト時に「**検証用のコード**」を送り、トークンリクエスト時に「**元のコード**」を送ることで、認可コードを取得したクライアントが正規のクライアントであることを証明します。

```
    正規アプリ                 攻撃者                 認可サーバー
       │                        │                        │
       │  (1) 認可リクエスト     │                        │
       │  + code_challenge      │                        │
       │ ──────────────────────────────────────────────►│
       │                        │                        │
       │  (2) 認可コード         │                        │
       │ ◄──────────────────────┼────────────────────────│
       │        ↓ 横取り！      │                        │
       │                        │  (3) トークンリクエスト │
       │                        │  + code_verifier=???   │
       │                        │ ─────────────────────►│
       │                        │                        │
       │                        │  (4) ❌ エラー         │
       │                        │  (verifier不一致)      │
       │                        │ ◄─────────────────────│
```

攻撃者は `code_verifier` を知らないため、横取りした認可コードを使ってもトークンを取得できません。

---

## 第2部: 詳細編

### コードの生成と検証

PKCE では 2 つのコードを使用します。

| 用語 | 説明 |
|------|------|
| `code_verifier` | クライアントが生成するランダムな文字列（43〜128文字） |
| `code_challenge` | `code_verifier` から生成されるチャレンジ値 |

#### code_verifier の要件

- 文字種: `[A-Z]`, `[a-z]`, `[0-9]`, `-`, `.`, `_`, `~`
- 長さ: 43〜128 文字
- 十分なエントロピー（ランダム性）を持つこと

#### code_challenge の生成方法

| メソッド | 計算方法 | 推奨度 |
|----------|----------|--------|
| `S256` | `BASE64URL(SHA256(code_verifier))` | ✅ 推奨 |
| `plain` | `code_verifier` そのまま | ⚠️ S256 が使えない場合のみ |

**S256 の計算例:**

```javascript
// code_verifier を生成
const array = new Uint8Array(32);
crypto.getRandomValues(array);
const code_verifier = base64url(array);  // 43文字

// code_challenge を計算
const encoder = new TextEncoder();
const data = encoder.encode(code_verifier);
const digest = await crypto.subtle.digest('SHA-256', data);
const code_challenge = base64url(new Uint8Array(digest));
```

### フローの詳細

#### Step 1: 認可リクエスト

```http
GET /authorize?
  response_type=code
  &client_id=s6BhdRkqt3
  &redirect_uri=https%3A%2F%2Fclient.example.org%2Fcallback
  &scope=read
  &state=xyz123
  &code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM
  &code_challenge_method=S256
HTTP/1.1
Host: auth.example.com
```

| パラメータ | 必須 | 説明 |
|------------|------|------|
| `code_challenge` | ✅ | 生成したチャレンジ値 |
| `code_challenge_method` | △ | `S256`（推奨）または `plain` |

#### Step 2: 認可レスポンス

通常の Authorization Code Grant と同じです。

```http
HTTP/1.1 302 Found
Location: https://client.example.org/callback?
  code=SplxlOBeZQQYbYS6WxSbIA
  &state=xyz123
```

#### Step 3: トークンリクエスト

```http
POST /token HTTP/1.1
Host: auth.example.com
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code
&code=SplxlOBeZQQYbYS6WxSbIA
&redirect_uri=https%3A%2F%2Fclient.example.org%2Fcallback
&client_id=s6BhdRkqt3
&code_verifier=dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk
```

| パラメータ | 必須 | 説明 |
|------------|------|------|
| `code_verifier` | ✅ | 認可リクエスト時に使用した元のコード |

#### Step 4: 認可サーバーの検証

認可サーバーは以下の手順で検証します。

```
1. code_verifier を受け取る
2. 認可リクエスト時の code_challenge_method を確認
3. code_verifier から code_challenge を再計算
   - S256: BASE64URL(SHA256(code_verifier))
   - plain: code_verifier そのまま
4. 認可リクエスト時に保存した code_challenge と比較
5. 一致すればトークンを発行、不一致ならエラー
```

### 実装例

#### クライアント側（JavaScript）

```javascript
// PKCE ユーティリティ
class PKCEUtil {
  static generateCodeVerifier() {
    const array = new Uint8Array(32);
    crypto.getRandomValues(array);
    return this.base64url(array);
  }

  static async generateCodeChallenge(verifier) {
    const encoder = new TextEncoder();
    const data = encoder.encode(verifier);
    const digest = await crypto.subtle.digest('SHA-256', data);
    return this.base64url(new Uint8Array(digest));
  }

  static base64url(buffer) {
    return btoa(String.fromCharCode(...buffer))
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
      .replace(/=+$/, '');
  }
}

// 認可リクエスト
async function startAuthorization() {
  const codeVerifier = PKCEUtil.generateCodeVerifier();
  const codeChallenge = await PKCEUtil.generateCodeChallenge(codeVerifier);
  
  // code_verifier をセッションストレージに保存
  sessionStorage.setItem('code_verifier', codeVerifier);
  
  const params = new URLSearchParams({
    response_type: 'code',
    client_id: 'my-client',
    redirect_uri: 'https://myapp.example.com/callback',
    scope: 'read write',
    state: crypto.randomUUID(),
    code_challenge: codeChallenge,
    code_challenge_method: 'S256'
  });
  
  window.location.href = `https://auth.example.com/authorize?${params}`;
}

// トークンリクエスト（コールバック後）
async function exchangeCode(code) {
  const codeVerifier = sessionStorage.getItem('code_verifier');
  sessionStorage.removeItem('code_verifier');
  
  const response = await fetch('https://auth.example.com/token', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded'
    },
    body: new URLSearchParams({
      grant_type: 'authorization_code',
      code: code,
      redirect_uri: 'https://myapp.example.com/callback',
      client_id: 'my-client',
      code_verifier: codeVerifier
    })
  });
  
  return response.json();
}
```

#### 認可サーバー側（Java）

```java
// PKCE 検証
public class PKCEValidator {
    
    public boolean validate(String codeVerifier, String codeChallenge, 
                           String codeChallengeMethod) {
        if ("S256".equals(codeChallengeMethod)) {
            String computed = computeS256Challenge(codeVerifier);
            return computed.equals(codeChallenge);
        } else if ("plain".equals(codeChallengeMethod)) {
            return codeVerifier.equals(codeChallenge);
        }
        return false;
    }
    
    private String computeS256Challenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                codeVerifier.getBytes(StandardCharsets.US_ASCII)
            );
            return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
```

### エラーレスポンス

PKCE 関連のエラー：

| エラーコード | 説明 |
|--------------|------|
| `invalid_request` | `code_challenge` または `code_verifier` が不正 |
| `invalid_grant` | `code_verifier` が `code_challenge` と一致しない |

### セキュリティ考慮事項

| 項目 | 推奨事項 |
|------|----------|
| メソッド | `S256` を使用（`plain` は非推奨） |
| エントロピー | `code_verifier` は十分なランダム性を持つこと |
| 保存場所 | `code_verifier` はセキュアな場所に保存（メモリ、セッションストレージ等） |
| 有効期限 | `code_verifier` は短時間のみ保持 |

### PKCE 必須化の流れ

- **RFC 7636（2015）**: モバイルアプリ向けに PKCE を定義
- **RFC 9700（2024）**: すべてのクライアントで PKCE を推奨
- **OAuth 2.1（策定中）**: PKCE が必須に

現在の実装では、クライアントタイプに関わらず **PKCE を使用することを強く推奨** します。

---

## 参考リンク

- [RFC 7636 - Proof Key for Code Exchange](https://datatracker.ietf.org/doc/html/rfc7636)
- [RFC 9700 - OAuth 2.0 Security Best Current Practice](https://datatracker.ietf.org/doc/html/rfc9700)
- [OAuth 2.1 Draft](https://datatracker.ietf.org/doc/draft-ietf-oauth-v2-1/)
