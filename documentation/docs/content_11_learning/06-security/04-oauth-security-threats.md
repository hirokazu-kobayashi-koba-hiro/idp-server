# OAuth 2.0のセキュリティ脅威と対策

## このドキュメントの目的

OAuth 2.0で発生しうる**セキュリティ脅威**と、それに対する**標準的な対策**を理解することが目標です。

---

## 主要な脅威と対策

### 1. Authorization Code横取り攻撃

#### 脅威シナリオ

```
┌─────────┐      ┌─────────┐      ┌─────────┐
│  ユーザー │      │   IdP   │      │ 攻撃者  │
└────┬────┘      └────┬────┘      └────┬────┘
     │                │                │
     │ 1. 認証リクエスト │                │
     │───────────────>│                │
     │                │                │
     │ 2. ログイン完了  │                │
     │<───────────────│                │
     │                │                │
     │ 3. Code発行     │                │
     │<───────────────│                │
     │                │                │
     │ ─ ─ ─ ─ ─ ─ ─ ─│─ ─ ─ ─ ─ ─ ─ >│ 4. マルウェアが
     │                │                │    Codeを横取り
     │                │                │
     │                │ 5. Codeでトークン要求
     │                │<───────────────│
     │                │                │
     │                │ 6. Token発行   │
     │                │───────────────>│
     │                │                │
     │                │      7. なりすましAPI操作
     │                │                │
```

#### 対策: PKCE（Proof Key for Code Exchange）

**仕組み**:
```
┌──────────┐                    ┌─────────┐
│ クライアント│                    │   IdP   │
└────┬─────┘                    └────┬────┘
     │                               │
     │ 1. code_verifier を生成        │
     │    (ランダム文字列)             │
     │                               │
     │ 2. code_challenge = SHA256(code_verifier)
     │                               │
     │ 3. 認証リクエスト               │
     │    + code_challenge            │
     │──────────────────────────────>│
     │                               │
     │ 4. Authorization Code         │
     │<──────────────────────────────│
     │                               │
     │  ┌─────────────────────────┐  │
     │  │ 攻撃者がCodeを横取りしても │  │
     │  │ code_verifierを知らない  │  │
     │  └─────────────────────────┘  │
     │                               │
     │ 5. Token リクエスト            │
     │    + code_verifier             │
     │──────────────────────────────>│
     │                               │
     │    6. SHA256(code_verifier)   │
     │       == code_challenge ?     │
     │       → 一致すればToken発行   │
     │                               │
     │ 7. Access Token               │
     │<──────────────────────────────│
```

**効果**:
- Authorization Codeを横取りしても、code_verifierがないとトークン取得不可
- code_verifierはクライアント内部でのみ保持（横取り不可能）

**RFC**: [RFC 7636 - PKCE](https://datatracker.ietf.org/doc/html/rfc7636)

**実装ガイド**: [PKCE実装](../../content_06_developer-guide/04-implementation-guides/oauth-oidc/pkce.md)

---

### 2. CSRF攻撃（Cross-Site Request Forgery）

#### 脅威シナリオ

```
1. 攻撃者が悪意のあるサイトを用意
2. ユーザーが悪意のあるサイトを訪問
3. 悪意のあるサイトが勝手にOAuth Authorization Requestを送信
4. ユーザーが気づかずに認証・認可を承認
5. 攻撃者が用意したredirect_uriにAuthorization Codeが送られる
6. 攻撃者がユーザーのアカウントを乗っ取る
```

#### 対策: state パラメータ

**仕組み**:
```
1. クライアントがstateパラメータ（ランダム文字列）を生成
2. Authorization Requestにstateを含める
3. サーバーがstateをそのまま返す（Callback時）
4. クライアントが元のstateと一致するか検証
```

**効果**:
- 攻撃者はstateの値を知らないため、検証で失敗
- リクエストが自分が開始したものであることを確認

**RFC**: [RFC 6749 Section 10.12](https://datatracker.ietf.org/doc/html/rfc6749#section-10.12)

---

### 3. リプレイ攻撃

#### 脅威シナリオ

```
1. 攻撃者がID Tokenを盗聴
2. 攻撃者が同じID Tokenを再利用してなりすまし
```

#### 対策: nonce パラメータ

**仕組み**:
```
1. クライアントがnonceパラメータ（ランダム文字列）を生成
2. Authorization Requestにnonceを含める
3. サーバーがID Tokenのnonceクレームに同じ値を設定
4. クライアントがID TokenのnonceとAuthorization Requestのnonceを照合
```

**効果**:
- ID Tokenが特定のAuthorization Requestに紐づく
- 盗聴されたID Tokenを別のセッションで再利用できない

**OpenID Connect**: [OpenID Connect Core Section 3.1.2.1](https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest)

---

### 4. トークン漏洩リスク

#### 脅威シナリオ

```
1. Access Tokenが盗聴される（HTTPSなし、ログ出力等）
2. 攻撃者が盗んだトークンでAPIにアクセス
3. ユーザーになりすましてリソースを操作
```

#### 対策1: 短い有効期限

**推奨値**:
- Access Token: 1時間（デフォルト）
- ID Token: 5分（認証完了直後のみ使用）
- Refresh Token: 30日（長期セッション用）

**効果**:
- トークンが盗まれても、短時間で無効化
- 被害を最小化

#### 対策2: Refresh Token Rotation

**仕組み**:
```
1. Refresh Token使用時に新しいRefresh Tokenを発行
2. 古いRefresh Tokenを無効化
3. 同じRefresh Tokenの再利用を検知したら、全トークンを失効
```

**効果**:
- Refresh Token盗聴を検知できる
- 盗聴時に被害を最小化

**RFC**: [RFC 6819 - OAuth 2.0 Threat Model](https://datatracker.ietf.org/doc/html/rfc6819)

---

### 5. Redirect URI検証漏れ

#### 脅威シナリオ

```
1. 攻撃者が悪意のあるredirect_uriを指定
   redirect_uri=https://attacker.com/callback
2. サーバーが検証せずにリダイレクト
3. Authorization Codeが攻撃者のサイトに送られる
4. 攻撃者がトークンを取得
```

#### 対策: Redirect URI完全一致検証

**RFC 6749の要件**:
- Authorization RequestとToken Requestのredirect_uriが**完全一致**
- 事前登録されたredirect_uriのみ許可
- ワイルドカード不可（`https://example.com/*`は不可）

**実装例**:
```java
// 検証ロジック例
if (!registeredRedirectUris.contains(requestRedirectUri)) {
    throw new InvalidRedirectUriException("redirect_uri not registered");
}
```

**よくあるミス**:
- 部分一致で検証（`startsWith`は危険）
- 末尾スラッシュの扱い（`/callback` vs `/callback/`は別物）

---

### 6. Open Redirect脆弱性

#### 脅威シナリオ

```
1. 攻撃者がフィッシングURLを作成
   https://idp.example.com/authorize?redirect_uri=https://phishing.com
2. ユーザーが信頼できるドメイン（idp.example.com）を見て安心
3. 認証後、フィッシングサイトにリダイレクト
4. ユーザーがフィッシングサイトで情報を入力
```

#### 対策: Redirect URI事前登録

**仕組み**:
- クライアント登録時にredirect_uriを事前登録
- 登録されていないURIへのリダイレクトを拒否

**一般的な実装**:
- クライアント設定で`redirect_uris`配列に明示的に登録
- 動的なredirect_uriは原則禁止

---

### 7. Client Secret漏洩

#### 脅威シナリオ

```
1. client_secretがGitHubに公開される
2. client_secretがフロントエンドコードにハードコード
3. 攻撃者がclient_secretを取得
4. 攻撃者がクライアントになりすましてトークン取得
```

#### 対策1: Public Clientでは使用しない

**Public Client（SPA、モバイルアプリ）**:
- client_secretを使用しない
- PKCEを必須にする
- `client_authentication_method: none`

**Confidential Client（サーバーサイド）**:
- client_secretを環境変数で管理
- Gitコミットに含めない（.env、シークレット管理）

#### 対策2: より強力な認証方式

**FAPI準拠の認証方式**:
- `private_key_jwt`: 秘密鍵署名（client_secretより安全）
- `tls_client_auth`: クライアント証明書（MTLS）

---

## セキュリティチェックリスト

IDサービス開発者が実装前に確認すべき項目：

- [ ] PKCEを実装している（Public Clientは必須）
- [ ] stateパラメータを検証している
- [ ] nonceパラメータを検証している（OIDC）
- [ ] redirect_uriを完全一致で検証している
- [ ] redirect_uriを事前登録している
- [ ] client_secretを環境変数で管理している（Confidential Client）
- [ ] Access Tokenの有効期限が適切（1時間以下推奨）
- [ ] Refresh Token Rotationを実装している
- [ ] HTTPSを必須にしている
- [ ] Authorization Codeをワンタイム使用にしている（使用後即削除）

---

## 発展: トークンバインディング（DPoP）

> この内容は発展的なトピックです。基礎を理解してから学習することを推奨します。

**課題**: Access Tokenが盗まれると、攻撃者がそのまま使用できてしまう

**DPoP（Demonstrating Proof of Possession）の仕組み**:
```
1. クライアントが公開鍵・秘密鍵ペアを生成
2. トークン取得時に公開鍵をサーバーに登録
3. Access Tokenが公開鍵にバインドされる
4. API呼び出し時にDPoP Proof（秘密鍵での署名）を添付
5. サーバーがDPoP Proofを検証
```

**効果**:
- トークンを盗んでも、秘密鍵がないと使用不可
- トークン漏洩時の被害を大幅に軽減

**RFC**: [RFC 9449 - DPoP](https://datatracker.ietf.org/doc/html/rfc9449)

---

## 参考資料

- [RFC 6819 - OAuth 2.0 Threat Model and Security Considerations](https://datatracker.ietf.org/doc/html/rfc6819)
- [OAuth 2.0 Security Best Current Practice](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-security-topics)
- [FAPI 2.0 Security Profile](https://openid.net/specs/fapi-2_0-security-profile.html)

---

**最終更新**: 2025-12-25
**対象**: IDサービス開発初心者
