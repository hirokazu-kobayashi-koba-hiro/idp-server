# OpenID Connect Core

| | |
|---|---|
| 状態 | ✅ 配線済み（`run.sh` あり） |
| テストプラン | `oidcc-basic-certification-test-plan` |
| モジュール数 | 36 |
| テナント | `config/examples/oidcc-cross-site` / `config/examples/oidcc-cross-site-context-path` |
| テスト設定 | 各テナントの `oidc-test/oidc-core-basic.json` |
| ブラウザ操作 | 必要（`../driver/` を常駐させる） |

Form Post OP（`config/examples/oidcc-formpost-basic`）は**未配線**。理由は後述。

## variants

```
oidcc-basic-certification-test-plan[server_metadata=static][client_registration=static_client]
```

| variant | 値 | 根拠 |
|---|---|---|
| `server_metadata` | `static` | `oidc-core-basic.json` は `server` に issuer / 各 endpoint を直書きしている（`discoveryUrl` を持つ設定なら `discovery`） |
| `client_registration` | `static_client` | idp-server は動的クライアント登録を discovery で広告していない |

## 手順

```bash
# 1. idp-server とテナント
docker compose up -d
./config/examples/oidcc-cross-site/setup.sh
./config/examples/oidcc-cross-site-context-path/setup.sh

# 2. suite スタック
docker compose -f oidc-conformance-suite/docker-compose.yaml up -d

# 3. ブラウザ操作ドライバ（別ターミナルで常駐。他スイートと共用で 1 プロセスだけ）
cd oidc-conformance-suite/driver && npm install && node driver.mjs

# 4. テスト
export CONFORMANCE_SUITE_DIR=/path/to/conformance-suite
./oidc-conformance-suite/oidcc/run.sh --rerun 1:1   # happy path 1本
./oidc-conformance-suite/oidcc/run.sh               # 両テナント
```

## 認証方式が FAPI 系と違う

oidcc テナントの認証は **email + password の 1 段**（financial-grade は email OTP → Passkey の 2 段）。
ドライバは `flow.mjs` の `TENANTS` に持つ `signIn` でどちらを使うか決める。

```js
"e8c169c2-019f-46c9-af39-7be12ec51e4d": {
  label: "oidcc-cross-site",
  signIn: "password",
  email: "oidcc-test@example.com",
  password: "OidccTestPassword123!",
},
```

`signIn: "password"` のテナントでは仮想オーセンティケータを載せない（passkey ファイルも作らない）。

## セッションを引き継ぐテストがある

`prompt=none` / `max_age` / `id_token_hint` は **1 回目でログインし、2 回目は既存セッションでの
挙動を見る**。ドライバは既定で認可ごとに使い捨てのブラウザコンテキストを使う（テスト間で
セッションが漏れると適合性テストの独立性が壊れるため）ので、これらのテストだけ
`BEHAVIORS` で `session: "reuse"` にしている。

セッションはテスト ID をキーに保持する。**alias の違うプランは並列に実行される**ため、
1 本だけ持つ作りにすると別プランのテストに枠を奪われ、2 回目の認可が `login_required` になる。

## 既存セッションでの認可（`session_enabled`）

`max_age` や `id_token_hint` のテストは「2 回目の認可でも再認証されないこと」を見る。
セッションを使ってよいかはサーバーが判定して `view-data.session_enabled` で返す
（`OAuthViewDataCreator.isSessionEnabled()`。セッションが無い / `prompt=login` /
`max_age` 超過 / `acr_values` 不一致 なら false）。

画面（`app-view/src/auth/useSessionAuthorize.ts`）はその値だけを見て
`POST .../authorize-with-session` を呼び、認証画面を出さずに認可を完了させる。

**判定は最初の view-data だけを使う。** 画面はフローの進行に合わせて view-data を取り直すが、
そのトランザクションで認証するとセッションができるため、後の取得では `session_enabled` が
true になる。それを拾うと、ユーザーがこれから見るはずの同意画面を飛ばして認可を完了して
しまう（実際に `oidcc-max-age-1` などが同意待ちでタイムアウトした）。

## 2 回目のログイン画面はスクリーンショットを出す

`oidcc-prompt-login` と `oidcc-max-age-1` は「2 回目に再認証を求められること」を目視で確認する。
suite 側（`OIDCCMaxAge1.createPlaceholder()` / `OIDCCPromptLogin.createPlaceholder()`）が
`ExpectSecondLoginPage` で提出先を作って待つため、**検証がすべて成功していても提出しないと
240 秒でタイムアウトして UNKNOWN になる**。`BEHAVIORS` の `screenshot: "second-login"` で
2 回目のログイン画面を提出している。結果が `REVIEW` で終わるのは正常。

## Form Post OP を配線していない理由

**idp-server が `response_mode=form_post` を実装していない。** 実測で全 36 モジュール中
34 が FAILED になる。

```
FAILURE CheckCallbackHttpMethodIsPost   The HTTP method used at redirect_uri is not 'POST'
FAILURE RejectAuthCodeInUrlQuery        Authorization code is present in URL query
```

`ResponseMode.form_post` は `responseModeValue` が空なので
`ResponseModeDecidable.decideResponseModeValue()` の `isDefinedResponseModeValue()` を満たさず、
認可コードフローの分岐で `query` にフォールバックする。HTML の自動 POST を返す実装は無い。

テナント設定（`config/examples/oidcc-formpost-basic`）とテスト設定は残してあるので、
対応が入ったら `run.sh` にプランを 1 本足せば動く。

なお `oidcc-formpost-basic` のテスト設定は `oidcc-cross-site` と **alias が同じ**
（`oidc-core-basic`）。同時に流すと alias 衝突するので、配線する際は alias を分けること。

## 実測済みの結果

両テナントとも同じ内訳。

| 結果 | 件数 | |
|---|---|---|
| PASSED | 29 | |
| REVIEW | 3 | `prompt-login` / `max-age-1` / `ensure-registered-redirect-uri`。スクリーンショット提出で終わるテストで、正常 |
| WARNING | 3 | 下記 |
| FAILED | 0 | |

### WARNING

| テスト | 条件 | 内容 |
|---|---|---|
| `oidcc-userinfo-post-body` | `UserInfoEndpointWithAccessTokenInBodyNotSupported` | UserInfo に access_token を POST ボディで渡す形式が未対応 |
| `oidcc-ensure-request-with-acr-values-succeeds` | `ValidateIdTokenACRClaimAgainstAcrValuesRequest` | ID Token の `acr` が要求した `acr_values` と一致しない |
| `oidcc-codereuse-30seconds` | `EnsureHttpStatusCodeIs4xx` | 認可コード再利用後に発行済みアクセストークンを失効させていない（RFC 6749 §4.1.2 の SHOULD）。FAPI 側と同じ |
