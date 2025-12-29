# OpenID Connect RP-Initiated Logout 1.0

RP-Initiated Logout は、Relying Party（RP）から OpenID Provider（OP）にログアウトを要求するための仕様です。

---

## 第1部: 概要編

### RP-Initiated Logout とは？

RP-Initiated Logout は、ユーザーが RP のアプリケーションで「ログアウト」ボタンをクリックした際に、OP のセッションも終了させるための仕組みです。

```
RP-Initiated Logout のフロー:

  ┌────────────┐     ログアウト      ┌────────────┐
  │  ユーザー    │ ───────────────► │     RP     │
  │  ブラウザ    │                   │            │
  │            │ ◄──────────────── │            │
  │            │  OP へリダイレクト   │            │
  └────────────┘                   └────────────┘
       │
       │  GET /logout?id_token_hint=...&post_logout_redirect_uri=...
       ▼
  ┌────────────┐
  │     OP     │  セッション終了
  │            │  ↓
  │            │  post_logout_redirect_uri へリダイレクト
  └────────────┘
       │
       ▼
  ┌────────────┐
  │     RP     │  ログアウト完了画面
  └────────────┘
```

### 他のログアウト仕様との違い

| 観点 | RP-Initiated | Front-Channel | Back-Channel |
|------|--------------|---------------|--------------|
| 起点 | RP | OP | OP |
| 目的 | OP セッション終了 | 複数 RP 同時ログアウト | 複数 RP 同時ログアウト |
| 通信 | ブラウザリダイレクト | iframe | サーバー間 |
| 信頼性 | 高い | 低い | 高い |

```
ログアウトの全体像:

  ユーザー ──► RP「ログアウト」
               │
               ▼
         RP-Initiated Logout
               │
               ▼
         OP セッション終了
               │
         ┌─────┴─────┐
         ▼           ▼
   Front-Channel  Back-Channel
   (iframe通知)   (サーバー通知)
         │           │
         ▼           ▼
      他の RP     他の RP
```

---

## 第2部: 詳細編

### エンドポイント

```
GET /logout?
  id_token_hint=eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
  &post_logout_redirect_uri=https://rp.example.com/logout/callback
  &state=abc123
  &client_id=s6BhdRkqt3
```

### リクエストパラメータ

| パラメータ | 仕様 | idp-server | 説明 |
|-----------|------|-----------|------|
| `id_token_hint` | RECOMMENDED | **REQUIRED** | 以前発行された ID Token。ユーザー識別に使用 |
| `client_id` | OPTIONAL | OPTIONAL | クライアント識別子 |
| `post_logout_redirect_uri` | OPTIONAL | OPTIONAL | ログアウト後のリダイレクト先 |
| `state` | OPTIONAL | OPTIONAL | CSRF 対策用の状態値 |
| `logout_hint` | OPTIONAL | - | ログアウト対象のヒント（OP 裁量） |
| `ui_locales` | OPTIONAL | OPTIONAL | UI の言語設定 |

> **Note**: idp-server では `id_token_hint` を必須としています。詳細は「第5部: idp-server の実装ポリシー」を参照してください。

### id_token_hint の役割

```
id_token_hint が重要な理由:

1. ユーザー識別
   JWT の sub claim からユーザーを特定

2. クライアント識別
   JWT の aud claim からクライアントを特定

3. セッション識別
   JWT の sid claim からセッションを特定（オプション）

4. セキュリティ
   正当なリクエストであることの証明
```

### クライアント識別の優先順位

```
クライアント識別の優先順位:

1. client_id パラメータ（明示的に指定）
      ↓ なければ
2. id_token_hint の aud claim
      ↓ なければ
3. クライアント識別不可
```

### id_token_hint がない場合の処理

仕様では、`id_token_hint` がない場合の OP の動作について重要な規定があります:

> "the OP MUST ask the End-User this question if an id_token_hint was not provided or if the supplied ID Token does not belong to the current OP session"

```
id_token_hint の有無による処理の違い:

id_token_hint あり
    ↓
ユーザー・セッション特定可能
    ↓
自動ログアウト（確認不要）
    ↓
post_logout_redirect_uri へリダイレクト


id_token_hint なし
    ↓
誰のリクエストか不明
    ↓
ユーザーに確認を求める（MUST）
「ログアウトしますか？」
    ↓
ユーザーが承認 → ログアウト実行
```

この確認が必要な理由:

```
攻撃シナリオ: DoS によるログアウト強制

1. 攻撃者が悪意あるサイトを作成
2. 隠し iframe で OP の logout エンドポイントに誘導
3. id_token_hint なし（攻撃者は持っていない）

確認なしの場合:
   → ユーザーが知らないうちにログアウトされる

確認ありの場合:
   → 「ログアウトしますか？」画面が表示
   → ユーザーが気づく → 攻撃失敗
```

### post_logout_redirect_uri の検証

`post_logout_redirect_uri` が指定された場合、OP は必ず検証を行う必要があります。

```
post_logout_redirect_uri の検証フロー:

1. クライアント識別可能か？
   - client_id または id_token_hint.aud から識別
   - 識別不可 → エラー（400 Bad Request）

2. クライアントに登録済みか？
   - post_logout_redirect_uris に完全一致で存在するか
   - 未登録 → エラー（400 Bad Request）

3. 検証成功
   - ログアウト後にリダイレクト
```

仕様の引用:

> "This URI MUST have been previously registered with the OP, either using the post_logout_redirect_uris Registration parameter or via another mechanism."

```
オープンリダイレクト攻撃の防止:

攻撃シナリオ:
  GET /logout?
    id_token_hint=valid_token
    &post_logout_redirect_uri=https://evil.com/phishing

対策:
  事前登録された URI のみ許可
  完全一致（ワイルドカード不可）
```

### state パラメータ

```
state の役割:

1. RP → OP へのリクエスト
   GET /logout?
     id_token_hint=...
     &post_logout_redirect_uri=https://rp.example.com/callback
     &state=xyz789

2. OP → RP へのリダイレクト
   GET https://rp.example.com/callback?
     state=xyz789

3. RP での検証
   - セッションに保存した state と比較
   - 一致しなければ不正なリクエスト
```

### クライアント登録

RP は登録時に `post_logout_redirect_uris` を指定します。

```json
{
  "client_id": "s6BhdRkqt3",
  "redirect_uris": ["https://rp.example.com/callback"],
  "post_logout_redirect_uris": [
    "https://rp.example.com/logout/callback",
    "https://rp.example.com/signed-out"
  ]
}
```

### ディスカバリーメタデータ

```json
{
  "issuer": "https://op.example.com",
  "end_session_endpoint": "https://op.example.com/logout"
}
```

| メタデータ | 説明 |
|-----------|------|
| `end_session_endpoint` | RP-Initiated Logout エンドポイント |

---

## 第3部: 実装編

### RP 側の実装（JavaScript）

```javascript
// ログアウトボタンのハンドラ
async function logout() {
  const idToken = getStoredIdToken();
  const state = generateRandomState();

  // state をセッションに保存（CSRF 対策）
  sessionStorage.setItem('logout_state', state);

  // OP のログアウトエンドポイントにリダイレクト
  const logoutUrl = new URL('https://op.example.com/logout');
  logoutUrl.searchParams.set('id_token_hint', idToken);
  logoutUrl.searchParams.set('post_logout_redirect_uri',
    'https://rp.example.com/logout/callback');
  logoutUrl.searchParams.set('state', state);
  logoutUrl.searchParams.set('client_id', 'my-client-id');

  window.location.href = logoutUrl.toString();
}

// ログアウトコールバック
function handleLogoutCallback() {
  const params = new URLSearchParams(window.location.search);
  const returnedState = params.get('state');
  const savedState = sessionStorage.getItem('logout_state');

  if (returnedState !== savedState) {
    console.error('State mismatch - potential CSRF attack');
    return;
  }

  // ローカルセッションもクリア
  sessionStorage.clear();
  localStorage.removeItem('access_token');
  localStorage.removeItem('id_token');

  // ログアウト完了画面を表示
  showLogoutComplete();
}
```

### OP 側の実装（Java - 仕様準拠版）

```java
public class LogoutHandler {

  public LogoutResponse handleLogout(LogoutRequest request) {
    // 1. パラメータ検証
    validator.validate(request.parameters());

    // 2. id_token_hint の解析（あれば）
    JsonWebTokenClaims claims = null;
    if (request.hasIdTokenHint()) {
      claims = parseAndValidateIdToken(request.idTokenHint());
    }

    // 3. クライアント識別
    ClientIdentifier clientId = resolveClientId(request, claims);

    // 4. post_logout_redirect_uri の検証
    if (request.hasPostLogoutRedirectUri()) {
      validatePostLogoutRedirectUri(clientId, request.postLogoutRedirectUri());
    }

    // 5. id_token_hint がない場合は確認が必要
    if (!request.hasIdTokenHint()) {
      return LogoutResponse.confirmationRequired();
    }

    // 6. セッション終了
    sessionManager.terminateSession(claims.getSub(), claims.getSid());

    // 7. レスポンス
    if (request.hasPostLogoutRedirectUri()) {
      String redirectUri = buildRedirectUri(
        request.postLogoutRedirectUri(),
        request.state()
      );
      return LogoutResponse.redirect(redirectUri);
    }

    return LogoutResponse.ok();
  }
}
```

### OP 側の実装（Java - idp-server 版）

```java
public class LogoutHandler {

  public LogoutResponse handleLogout(LogoutRequest request) {
    // 1. パラメータ検証（id_token_hint 必須）
    validator.validate(request.parameters());
    // → id_token_hint がなければ 400 Bad Request

    // 2. id_token_hint の解析（常に存在）
    JsonWebTokenClaims claims = parseAndValidateIdToken(request.idTokenHint());

    // 3. クライアント識別（id_token_hint.aud から）
    ClientIdentifier clientId = resolveClientId(request, claims);

    // 4. post_logout_redirect_uri の検証
    if (request.hasPostLogoutRedirectUri()) {
      validatePostLogoutRedirectUri(clientId, request.postLogoutRedirectUri());
    }

    // 5. セッション終了
    sessionManager.terminateSession(claims.getSub(), claims.getSid());

    // 6. レスポンス
    if (request.hasPostLogoutRedirectUri()) {
      String redirectUri = buildRedirectUri(
        request.postLogoutRedirectUri(),
        request.state()
      );
      return LogoutResponse.redirect(redirectUri);
    }

    return LogoutResponse.ok();
  }
}
```

### エラーレスポンス

| 状況 | エラー | 説明 |
|------|--------|------|
| post_logout_redirect_uri がクライアント識別なしで指定 | invalid_request | クライアントを特定できない |
| post_logout_redirect_uri が未登録 | invalid_request | 事前登録されていない |
| id_token_hint が不正な JWT | invalid_request | JWT 形式エラー |
| id_token_hint の iss が不一致 | invalid_request | 発行者が異なる |
| id_token_hint の署名が無効 | invalid_request | 署名検証失敗 |

---

## 第4部: セキュリティ考慮事項

### オープンリダイレクト対策

```
必須の対策:

1. post_logout_redirect_uri の事前登録
   - 動的登録 API または管理画面で登録
   - ワイルドカード不可

2. 完全一致検証
   - パスの正規化後に比較
   - クエリパラメータも含めて比較

3. HTTPS 必須
   - HTTP の URI は拒否
```

### CSRF 対策

```
state パラメータの使用:

1. RP がランダムな state を生成
2. セッションに保存
3. logout リクエストに含める
4. コールバックで検証
```

### id_token_hint の検証

```
検証項目:

1. 署名検証
   - OP の公開鍵で署名を検証

2. iss claim
   - この OP が発行したトークンか

3. aud claim
   - 登録されたクライアントか

4. 有効期限
   - 期限切れでも許容するか（OP 裁量）
```

### DoS 対策

```
確認画面の表示:

id_token_hint がない場合:
  → 自動ログアウトしない
  → ユーザーに確認を求める
  → 悪意あるサイトからの攻撃を防止
```

---

## 第5部: idp-server の実装ポリシー

### id_token_hint を必須に

idp-server では、`id_token_hint` を **必須（REQUIRED）** としています。

```
RFC仕様:
  id_token_hint は RECOMMENDED（推奨）
  なければ確認画面を表示（MUST）

idp-server:
  id_token_hint は REQUIRED（必須）
  なければ 400 Bad Request
```

### この設計の理由

```
1. 実装のシンプル化
   - 確認画面のフロー不要
   - 常にユーザー識別可能

2. セキュリティ
   - id_token_hint がなければ拒否
   - DoS 攻撃を根本から防止

3. RP への明確なガイダンス
   - id_token を保存しておく必要性が明確
   - 「推奨」ではなく「必須」なので実装漏れなし
```

### 他の OP との比較

| 観点 | idp-server | Keycloak | 仕様 |
|------|-----------|----------|------|
| id_token_hint | **必須** | 推奨（なければ確認画面） | 推奨 |
| 確認画面 | なし | あり（条件付き） | 条件付きで必須 |
| 実装複雑度 | 低 | 高 | - |

### エラーレスポンス

```json
{
  "error": "invalid_request",
  "error_description": "logout request must contain id_token_hint"
}
```

### RP 実装への影響

RP は必ず `id_token` を保存し、ログアウト時に `id_token_hint` として送信する必要があります。

```javascript
// ログイン成功時
function handleLoginCallback(tokenResponse) {
  // id_token を保存（ログアウト時に必要）
  localStorage.setItem('id_token', tokenResponse.id_token);
}

// ログアウト時
function logout() {
  const idToken = localStorage.getItem('id_token');

  if (!idToken) {
    // id_token がない場合はローカルログアウトのみ
    clearLocalSession();
    return;
  }

  // OP にログアウトリクエスト
  const logoutUrl = new URL(endSessionEndpoint);
  logoutUrl.searchParams.set('id_token_hint', idToken);
  // ...
}
```

---

## 参考リンク

- [OpenID Connect RP-Initiated Logout 1.0](https://openid.net/specs/openid-connect-rpinitiated-1_0.html)
- [OpenID Connect Front-Channel Logout 1.0](https://openid.net/specs/openid-connect-frontchannel-1_0.html)
- [OpenID Connect Back-Channel Logout 1.0](https://openid.net/specs/openid-connect-backchannel-1_0.html)
- [OpenID Connect Session Management 1.0](https://openid.net/specs/openid-connect-session-1_0.html)
