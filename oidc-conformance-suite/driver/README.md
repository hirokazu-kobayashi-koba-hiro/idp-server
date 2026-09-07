# ブラウザ操作ドライバ

conformance suite が「ブラウザで訪問してほしい」と積んだ URL を、実 Chromium で消化し続ける
常駐プロセス。ブラウザ操作を伴うスイート（FAPI 1.0 Advanced / FAPI 2.0）で必要になる。

**スイートをまたいで 1 プロセスで足りる。** どのテナントのサインインかは URL の `tenant_id` で
判別するため、FAPI 1.0 を流したあとそのまま FAPI 2.0 を流せる。

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
起動時にさばけるテナントを出すので、対象のテナントが並んでいることを確認する。

```
conformance driver 起動 (suite=https://localhost:8443/)
  tenant financial-grade (c3d4e5f6-...) user=conformance-driver@example.com
  tenant financial-grade-2.0 (c3f4a5b6-...) user=fapi2-conformance-driver@example.com
▶ fapi1-advanced-final (xxxxx)
    email OTP ok (123456)
    passkey ok ("Use passkey")
    consent ok -> callback
    passkey 保存 (signCount 7 -> 8)
```

## テナント設定

対象テナントは `flow.mjs` の `TENANTS` に持っている。キーはテナント ID で、認可エンドポイントの
URL（`https://api.local.test/{tenantId}/v1/authorizations?...`）から引く。

| 項目 | 用途 |
|---|---|
| `label` | passkey ファイル名（`passkey-<label>.json`） |
| `email` | サインインに使うユーザー。テナントごとに別ユーザー |
| `organizationId` / `admin` | email の検証コードを管理 API から取るための組織管理者 |

新しいテナントを対象にするときはここに 1 エントリ足す。`TENANTS` に無いテナントの URL を拾うと
既定テナント（financial-grade）の管理者で検証コードを取りにいって失敗する。

## 環境変数

| 変数 | 既定 | 用途 |
|---|---|---|
| `SUITE` | `https://localhost:8443` | suite の API 接続先 |
| `DRIVER_PASSKEY_FILE` | `./passkey-<label>.json` | 登録した passkey の保存先（指定するとテナント別の分割が無効になる） |
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

新規ユーザーなら画面が「Set up passkey」（登録）を出すので登録し、鍵を `passkey-<label>.json` に
保存する。以降は `WebAuthn.addCredential` で注入して「Use passkey」（認証）を通す。

**ファイルはテナントごとに分ける。** ユーザーがテナントごとに別なので、別テナントの鍵を注入すると
クローン検知や credential 不一致に当たる。

## passkey ファイルの扱い（重要）

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

- `TENANTS` の `email` を変えたらそのテナントの passkey ファイルも消す（登録し直しになる）
- passkey ファイルだけ消すとサーバに登録済みの鍵と食い違う。メールも変えること
- テナントを作り直した（`setup.sh` の再実行）ら passkey ファイルは無効。消す
- ドライバを認証の途中で落とすと、サーバのカウンタだけ進んで保存が飛ぶ。次の 1 回は
  `Failed to verify authentication data` で落ちるが、その失敗でカウンタが追いつくので
  2 回目からは通る

### `TENANTS` の `email` を変えるコミットは、鍵を持っていない環境をすべて壊す

passkey は `passkey-<label>.json` にしか無く **gitignore なので共有されない**。一方 `flow.mjs` の
`TENANTS` は**コミットされる**。片方だけがリポジトリに乗るため、email を変えたコミットは
その鍵を登録した環境でしか動かない。

実際に踏んだ症状: `conformance-driver5` → `conformance-driver6` の変更を取り込んだ環境で、
サーバは driver6 の資格情報を持たないので**登録用**のオプションを返すが、画面は `user.status`
で分岐するため「Use passkey」（認証）を出し続ける。ドライバは認証だと思って進み 30 秒待って
落ちる。これが全モジュールで起きて **fapi1-advanced の 53 モジュールが WAITING のまま 3.6 時間**
かかり、成功は 0 件だった。画面のメッセージは `Passkey sign-in was cancelled` としか出ない。

対策を 2 つ入れてある。

**1. 起動時チェック。** passkey ファイルの `userHandle`（利用者の email が base64 で入っている）と
`TENANTS` の `email` を突き合わせ、食い違えば起動せずに終了する。鍵ファイルが無い場合は
画面が登録フローを出すので正常とみなす。

**2. `driver/local.json`（gitignore）で環境ごとに上書き。** テナント ID をキーに、上書きしたい
フィールドだけ書く。`TENANTS` 側は「新規環境の既定値」として扱う。

```json
{
  "c3d4e5f6-a7b8-c9d0-e1f2-a3b4c5d6e7f8": { "email": "conformance-driver5@example.com" },
  "c3f4a5b6-d7e8-4f9a-0b1c-2d3e4f5a6b7c": { "email": "fapi2-conformance-driver@example.com" }
}
```

手元の鍵に合わせるならこちらに書く。`TENANTS` を直接編集して commit すると、また他の環境が壊れる。

**検知できないケース。** 「鍵ファイルが無く、かつサーバ側にはその利用者の資格情報がある」状態は
起動時には分からない（サーバ照会が要る）。共有 DB を別マシンから使うときに起こりうる。

## 既知の制限

- FAPI-CIBA には使えない（ブラウザを使わないフロー。`../fapi-ciba/README.md` 参照）
- 認証ポリシーが **email OTP → Passkey** の 2 段であることを前提にしている。別構成のテナントを
  対象にする場合は `signIn()` の調整が要る
- 1 プロセスで 1 つの URL を順に処理する。suite 側も `alias` 付きプランは直列実行なので現状は足りている
