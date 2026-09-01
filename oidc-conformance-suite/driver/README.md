# ブラウザ操作ドライバ

conformance suite が「ブラウザで訪問してほしい」と積んだ URL を、実 Chromium で消化し続ける
常駐プロセス。ブラウザ操作を伴うスイート（FAPI 1.0 Advanced 等）で必要になる。

## なぜ必要か

suite 内蔵のブラウザは Selenium **HtmlUnit**（`BrowserControl.java`）で、JS エンジンが ES6 を
解釈できない。idp-server のサインイン画面は Next.js の CSR なので、実測すると全チャンクが
パースエラーになりフォームが 1 つも描画されない。

```
#__next   : MUI のローディングスピナーのみ
inputs    : 0
buttons   : 0
js errors : identifier is a reserved word: class / syntax error / invalid property id ...
```

したがってテスト設定 JSON の `browser` ブロックによる自動操作は成立しない。

一方 suite は、自動操作できない URL を捨てずに**人間向けに公開している**。

```java
// BrowserControl.goToUrl:214
logger.debug(testId + ": Could not find a match for url: " + url);
// if we couldn't find a command for this URL, leave it up to the user to do something with it
urls.add(url);
```

このドライバはその API を使い、人間の代わりに実ブラウザでサインインする。

```
GET  /api/runner/browser/{id}        訪問してほしい URL を取得
POST /api/runner/browser/{id}/visit  訪問済みとしてマーク
```

## 起動

```bash
npm install          # 初回のみ。chromium も一緒に入る
node driver.mjs
```

テストを流す間は常駐させておく。進行はターミナルと `driver.log` の両方に出る。

```
▶ fapi1-advanced-final (xxxxx)
    email OTP ok (123456)
    passkey ok ("Use passkey")
    consent ok -> callback
    passkey 保存 (signCount 7 -> 8)
```

## 環境変数

| 変数 | 既定 | 用途 |
|---|---|---|
| `SUITE` | `https://localhost:8443` | suite の API 接続先 |
| `DRIVER_EMAIL` | `conformance-driver@example.com` | ログインに使うユーザー |
| `DRIVER_PASSKEY_FILE` | `./passkey.json` | 登録した passkey の保存先 |
| `DRIVER_LOG` | `./driver.log` | ログ出力先 |
| `IDP_BASE_URL` | `https://api.local.test` | idp-server |
| `IDP_ROOT_CA` | `<repo>/docker/nginx/certs/rootCA.pem` | ローカル CA |
| `DRIVER_HEADED` | （未設定 = ヘッドレス） | `1` で実ウィンドウを表示 |
| `DRIVER_SLOWMO` | `0` | ひと操作ごとの待ち時間(ms)。画面ありで目で追うとき用 |

## 画面を見ながら動かす

ヘッドレスだと 1 回のサインインが数秒で終わってしまうので、目で追いたいときは遅延を入れる。

```bash
DRIVER_HEADED=1 DRIVER_SLOWMO=500 node driver.mjs
```

Chromium のウィンドウが開き、email 入力 → コード入力 → パスキー → 同意 →
callback へのリダイレクトが実際に見える。FIDO2 は仮想オーセンティケータが処理するため
OS の生体認証ダイアログは出ない。

デバッグ用途では `DRIVER_SLOWMO=1000` くらいにして、どの画面で止まっているかを確認する。
失敗時は `/tmp/conformance-driver-fail-{testId}.png` にスクリーンショットが残る。

`docker/nginx/certs/*.pem` は mkcert が生成するもので gitignore されている。git worktree など
証明書が無いチェックアウトから動かす場合は `IDP_ROOT_CA` でメインのチェックアウトを指す。

## 認証の 2 段をどう突破しているか

financial-grade テナントの認証ポリシーは **email OTP → Passkey(FIDO2)** の 2 段。

### email OTP

`no_action` 設定で実メールが飛ばないため、コードは画面にもチャレンジのレスポンスにも出てこない。
管理 API から 2 ホップで取る（`e2e/src/user/index.js` と同じ経路）。

```
authorization_id
  → GET .../authentication-transactions?authorization_id={id}     transaction を引く
  → GET .../authentication-interactions/{txId}/email-authentication-challenge
       → payload.verification_code
```

`authorization_id` はサインイン画面の URL クエリ `?id=` から取れる。

### FIDO2

CDP の**仮想オーセンティケータ**（`WebAuthn.addVirtualAuthenticator`）を使う。モック実装は不要で、
実ブラウザが本物の WebAuthn 儀式を行う。テナントの fido2 設定に合わせて platform / resident key /
user verification を有効にしている。

新規ユーザーなら画面が「Set up passkey」（登録）を出すので登録し、鍵を `passkey.json` に保存する。
以降は `WebAuthn.addCredential` で注入して「Use passkey」（認証）を通す。

## passkey.json の扱い（重要）

**署名カウンタごと保存し直している。** idp-server は WebAuthn §6.1.1 のクローン検知を実装している。

```java
// WebAuthn4jAuthenticationExecutor.java:117
if (newSignCount > 0 && newSignCount <= webAuthn4jCredential.signCount()) {
  // "webauthn4j credential clone detected"
```

仮想オーセンティケータはブラウザコンテキストごとに空なので毎回ファイルから鍵を注入するが、
**カウンタも一緒に巻き戻すと 2 回目の認証が必ず失敗する**（`Failed to verify authentication data`）。
そのため成功・失敗にかかわらず、コンテキストを閉じる前にカウンタを書き戻している。

このため以下に注意する。

- `DRIVER_EMAIL` を変えたら `passkey.json` も消す（新規ユーザーとして登録し直しになる）
- `passkey.json` だけ消すとサーバに登録済みの鍵と食い違う。メールも変えること
- テナントを作り直した（`setup.sh` の再実行）ら `passkey.json` は無効。消す

## 既知の制限

- FAPI-CIBA には使えない（ブラウザを使わないフロー。`../fapi-ciba/README.md` 参照）
- 認証ポリシーが financial-grade 前提。別テナントを対象にする場合は手順の調整が要る
- 1 プロセスで 1 つの URL を順に処理する。suite 側も `alias` 付きプランは直列実行なので現状は足りている
