# トークン保存のセキュリティ

## このドキュメントの目的

OAuth/OIDCで発行されるトークン（Access Token、Refresh Token、ID Token）を**どこに保存すべきか**、各方式のセキュリティリスクと対策を学びます。

---

## なぜトークン保存が重要なのか

```
OAuth/OIDCでは、認証後にトークンが発行される。
このトークンは「入場パス」のようなもの。

  トークンを持っている = その人として扱われる

つまり、トークンが盗まれると：
  - ユーザーのデータを閲覧される
  - ユーザーとして操作される
  - 長期間なりすまされる（Refresh Tokenの場合）

だから「どこに保存するか」が重要。
```

### ブラウザでの保存場所と攻撃の関係

```
┌─────────────────────────────────────────────────────────────┐
│                      ブラウザ                                │
│                                                             │
│  ┌─────────────┐   ┌─────────────┐   ┌─────────────┐       │
│  │ localStorage │   │sessionStorage│   │   Cookie    │       │
│  │             │   │             │   │             │       │
│  │ XSSで       │   │ XSSで       │   │ HttpOnlyなら │       │
│  │ 盗める ✗    │   │ 盗める ✗    │   │ 盗めない ◎  │       │
│  └─────────────┘   └─────────────┘   └──────┬──────┘       │
│                                             │               │
│  ┌─────────────┐                           │               │
│  │   メモリ     │                           │               │
│  │ (変数)      │   ←── 最も安全 ◎           │               │
│  └─────────────┘                           │               │
│                                             │               │
└─────────────────────────────────────────────┼───────────────┘
                                              │
                              CSRFで勝手に送信されるリスク △
```

---

## トークンの種類と機密性

| トークン | 用途 | 有効期限 | 機密性 |
|---------|------|---------|--------|
| Access Token | APIアクセス | 短期（1時間） | 高 |
| Refresh Token | Access Token更新 | 長期（30日） | 非常に高 |
| ID Token | ユーザー認証証明 | 短期（5分） | 中 |

---

## 保存場所の選択肢

### 比較表

| 保存場所 | XSS耐性 | CSRF耐性 | 使いやすさ | 推奨度 |
|---------|--------|---------|-----------|--------|
| メモリ（変数） | ◎ | ◎ | △ | ◎ |
| HttpOnly Cookie | ◎ | △ | ○ | ○ |
| localStorage | ✗ | ◎ | ◎ | △ |
| sessionStorage | ✗ | ◎ | ○ | △ |

---

## 1. メモリ保存（推奨）

### 概要

```javascript
// トークンをJavaScript変数として保持
let accessToken = null;

async function login() {
    const response = await fetch('/oauth/token', { ... });
    const data = await response.json();
    accessToken = data.access_token;  // メモリに保存
}

async function callApi() {
    const response = await fetch('/api/resource', {
        headers: {
            'Authorization': `Bearer ${accessToken}`
        }
    });
}
```

### メリット

```
✅ XSS攻撃に対して最も安全
   - JavaScriptのスコープ内でのみアクセス可能
   - グローバル変数にしなければ、外部スクリプトからアクセス困難

✅ CSRF攻撃に対して安全
   - Cookieを使用しないため、自動送信されない

✅ 永続化されないため、ブラウザを閉じればトークン消失
```

### デメリットと対策

```
❌ ページリロードでトークン消失
   対策: サイレントリフレッシュ（iframe + Refresh Token Cookie）

❌ 新しいタブでログイン状態が共有されない
   対策: BroadcastChannel API、SharedWorker

❌ Refresh Tokenの保存場所が別途必要
   対策: HttpOnly Cookieで保存
```

### 推奨パターン

```
Access Token: メモリ
Refresh Token: HttpOnly Cookie（BFF経由）
```

---

## 2. HttpOnly Cookie

### 概要

```http
Set-Cookie: refresh_token=xyz789;
            HttpOnly;
            Secure;
            SameSite=Strict;
            Path=/oauth;
            Max-Age=2592000
```

### メリット

```
✅ XSS攻撃に対して安全
   - JavaScriptからアクセス不可（HttpOnly属性）

✅ 自動的にリクエストに含まれる
   - API呼び出し時に明示的な処理不要
```

### デメリットと対策

```
❌ CSRF攻撃のリスク
   対策: SameSite属性（Strict/Lax）、CSRFトークン

❌ クロスオリジンリクエストで制限
   対策: 同一オリジンのBFF（Backend For Frontend）パターン
```

---

## 3. localStorage

### 概要

```javascript
// 保存
localStorage.setItem('access_token', accessToken);

// 取得
const token = localStorage.getItem('access_token');

// 削除
localStorage.removeItem('access_token');
```

### メリット

```
✅ 永続的な保存（ブラウザを閉じても維持）
✅ 使いやすいAPI
✅ CSRF攻撃に安全（Cookieではないため）
```

### デメリット（重大）

```
❌ XSS攻撃に対して脆弱
   - JavaScriptから直接アクセス可能
   - 悪意のあるスクリプトが実行されるとトークンが盗まれる
```

XSS攻撃でトークンが盗まれる具体的な流れは
[入力バリデーション - XSS攻撃シミュレーション](./03-input-validation.md#2-xsscross-site-scripting)を参照。

### 使用すべき場合

```
- 機密性の低いデータのみ
- XSS対策が十分に実装されている場合（CSP等）
- 短期間のAccess Tokenのみ（Refresh Tokenは不可）
```

---

## 結局どうすればいいのか？

ここまで見てきた保存場所には、それぞれ弱点がある。

```
┌─────────────────┬────────────────────────────────────┐
│    保存場所      │           弱点                     │
├─────────────────┼────────────────────────────────────┤
│ メモリ          │ ページリロードで消える              │
│                 │ タブ間で共有できない                │
├─────────────────┼────────────────────────────────────┤
│ HttpOnly Cookie │ SPAからAPIを直接呼ぶ時に不便        │
│                 │ CSRFリスクがある                    │
├─────────────────┼────────────────────────────────────┤
│ localStorage    │ XSSで盗まれる                      │
│                 │ Refresh Tokenは保存禁止            │
└─────────────────┴────────────────────────────────────┘

→ 単独では完璧な方法がない
→ 組み合わせて弱点を補う方法の1つがBFFパターン
```

---

## BFFパターン

BFFパターンは、**サーバーサイドとの組み合わせ**でクライアント側の弱点を補う。

ただし、BFFにもトレードオフがある。

```
BFFパターンのトレードオフ:

  メリット                      デメリット
  ─────────────────────────────────────────────────
  ✅ トークンをSPAに持たせない   ❌ 実装が複雑になる
  ✅ XSSでトークン漏洩しない     ❌ レイテンシが増加
  ✅ Refresh Tokenを安全に保持   ❌ サーバー運用コスト増
                                ❌ BFF自体が攻撃対象になる

→ セキュリティ要件とコストのバランスで判断
```

### SPAとBFFをセッションで繋ぐことの注意点

BFFパターンでは、SPAとBFFの間を**セッションCookie**で繋ぐことが多い。

```
┌───────┐  セッションCookie   ┌───────┐
│  SPA  │ ←────────────────→ │  BFF  │
└───────┘   (HttpOnly)        └───────┘
```

これはOAuthトークン管理の複雑さを、セッション管理の複雑さに置き換えているとも言える。

セッション管理の詳細（攻撃対策、Cookie属性、分散環境での課題）については
[セッションセキュリティ](./07-session-security.md)を参照。

---

### パターンA: BFFがAPI代理呼び出し（より安全）

```
┌───────┐                ┌───────┐                ┌───────┐
│  SPA  │                │  BFF  │                │  API  │
└───┬───┘                └───┬───┘                └───┬───┘
    │                        │                        │
    │ 1. リクエスト           │                        │
    │   (セッションCookie)    │                        │
    │───────────────────────>│                        │
    │                        │                        │
    │                        │ 2. API呼び出し         │
    │                        │   (Access Token)       │
    │                        │───────────────────────>│
    │                        │                        │
    │                        │ 3. レスポンス          │
    │                        │<───────────────────────│
    │                        │                        │
    │ 4. データ返却          │                        │
    │<───────────────────────│                        │

BFFが保持:
- Access Token（メモリ/セッション）
- Refresh Token（HttpOnly Cookie）

SPAが保持:
- セッションCookie のみ（トークンなし）
```

### パターンB: BFFがトークン管理のみ

```
┌───────┐                ┌───────┐                ┌───────┐
│  SPA  │                │  BFF  │                │  API  │
└───┬───┘                └───┬───┘                └───┬───┘
    │                        │                        │
    │ 1. トークン取得/更新    │                        │
    │   (セッションCookie)    │                        │
    │───────────────────────>│                        │
    │                        │                        │
    │ 2. Access Token        │                        │
    │<───────────────────────│                        │
    │                        │                        │
    │ 3. API呼び出し（直接）                           │
    │   (Access Token)                                │
    │────────────────────────────────────────────────>│
    │                        │                        │
    │ 4. レスポンス                                   │
    │<────────────────────────────────────────────────│

BFFが保持:
- Refresh Token（HttpOnly Cookie）

SPAが保持:
- Access Token（メモリ）
```

### どちらを選ぶか

| 観点 | パターンA | パターンB |
|------|----------|----------|
| セキュリティ | ◎ SPAにトークンなし | ○ Access Tokenのみ |
| 実装複雑度 | △ BFFでAPI代理が必要 | ○ トークン管理のみ |
| レイテンシ | △ BFF経由で増加 | ◎ 直接通信 |
| 推奨 | 金融系など高セキュリティ | 一般的なWebアプリ |

---

## セキュリティ比較：保存場所によるXSS耐性の違い

XSS攻撃が成功した場合、保存場所によって被害が異なる。

```
┌─────────────────────────────────────────────────────────────┐
│  XSS攻撃が発生した時...                                       │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  localStorage / sessionStorage                              │
│  ─────────────────────────────────────────                  │
│  localStorage.getItem('token')  →  トークン取得可能 ✗       │
│                                                             │
│  HttpOnly Cookie                                            │
│  ─────────────────────────────────────────                  │
│  document.cookie  →  取得不可（HttpOnly属性）◎              │
│                                                             │
│  メモリ（クロージャ内変数）                                   │
│  ─────────────────────────────────────────                  │
│  外部スクリプトからアクセス困難 ◎                             │
│                                                             │
└─────────────────────────────────────────────────────────────┘

→ XSS対策は必須だが、万が一に備えて保存場所も考慮する（多層防御）
```

XSS攻撃の詳細と対策は[入力バリデーション](./03-input-validation.md#2-xsscross-site-scripting)を参照。

---

## チェックリスト

### Web SPA
- [ ] Access Tokenはメモリに保存
- [ ] Refresh TokenはHttpOnly Cookieに保存
- [ ] BFFパターンを採用
- [ ] CSRF対策を実装
- [ ] CSP（Content Security Policy）を設定

### 共通
- [ ] Access Tokenは短い有効期限（1時間以下）
- [ ] Refresh Tokenは適切な有効期限
- [ ] トークン失効機能を実装

---

## 発展: モバイルアプリでのトークン保存

> この内容は発展的なトピックです。Webアプリの基礎を理解してから学習することを推奨します。

### iOS

```
Keychainに保存（推奨）
- kSecAttrAccessibleWhenUnlockedThisDeviceOnly
- デバイス固有、バックアップ対象外
```

### Android

```
EncryptedSharedPreferencesを使用（推奨）
- AES256-GCM暗号化
- Android Keystore連携
```

### モバイルアプリのチェックリスト
- [ ] iOS: Keychainを使用
- [ ] Android: EncryptedSharedPreferencesを使用
- [ ] トークンをログに出力しない
- [ ] デバイス紛失時の失効機能

---

## 参考資料

- [OAuth 2.0 for Browser-Based Apps](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-browser-based-apps)
- [OWASP HTML5 Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/HTML5_Security_Cheat_Sheet.html)

---

**最終更新**: 2025-12-25
**対象**: フロントエンド開発者、セキュリティエンジニア
