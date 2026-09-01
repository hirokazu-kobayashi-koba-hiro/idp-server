# OpenID Connect Core

| | |
|---|---|
| 状態 | ❌ 未配線（`run.sh` なし） |
| テストプラン | `oidcc-test-plan`（55 モジュール）/ `oidcc-basic-certification-test-plan`（38 モジュール） |
| テナント | `oidcc-cross-site` / `oidcc-cross-site-context-path` / `oidcc-formpost-basic` の 3 種 |
| ブラウザ操作 | 必要と思われる（未検証） |

FAPI 系と違い、**3 つの独立したテナント設定**がそれぞれ自分のテスト設定を持っている。

```
config/examples/oidcc-cross-site/oidc-test/
config/examples/oidcc-cross-site-context-path/oidc-test/
config/examples/oidcc-formpost-basic/oidc-test/
  ├── oidc-core-basic.json
  ├── oidc-comprehensive-test.json
  └── oidcc-discovery-endpoint-verification.json
```

いずれの setup も `setup.sh` / `update.sh` / `delete.sh` を持っており、テナント側の準備は
FAPI 1.0 と同じ手順で行える。

## 何が足りないか

**どの設定をどのプランで回すかの対応づけ。**

テスト設定が 3 テナント × 3 ファイル = 9 通りあるが、それぞれが `oidcc-test-plan` と
`oidcc-basic-certification-test-plan` のどちらを想定しているか、また variants の値が
決まっていない。ファイル名から推測はできるが未検証。

## variants（未確定）

| プラン | variant キー |
|---|---|
| `oidcc-test-plan` | `client_registration`, `response_type`, `client_auth_type`, `response_mode` |
| `oidcc-basic-certification-test-plan` | `server_metadata`, `client_registration` |

`oidcc-formpost-basic` は名前から `response_mode=form_post` を想定していると思われる。
`oidcc-cross-site-context-path` はコンテキストパス付きデプロイの検証用で、テスト設定の
discovery URL がそれを反映しているはず。配線時に実物を確認すること。

## ブラウザ操作について

サインイン画面は FAPI 1.0 と同じ Next.js の CSR なので、`../driver/` がそのまま使える見込み。
ただし認証ポリシーがテナントごとに違う（financial-grade は email OTP → FIDO2 の 2 段だが、
oidcc 系は未確認）ため、ドライバの手順をポリシーに合わせる必要がある。

## セッション挙動のテストについて（配線時にぶつかる）

`oidcc-test-plan` には `prompt=none` / `prompt=login` / `max_age` など、**既存セッションの
有無で挙動が変わることを確認するテスト**が含まれる。ここで 2 つ課題がある。

### 1. ドライバがセッションを持たない

現在のドライバは URL ごとに使い捨ての browser context を作るため、クッキーを毎回捨てている。
これは適合性テストの独立性としては望ましい（あるテストのセッションが次に漏れない）が、
セッション有無で分岐するテストは通らない。

`driver.mjs` の `BEHAVIORS` にセッションスコープの軸を足して、必要なテストだけ
context を再利用する（testId 単位で共有する）形にするのが素直。既定は現行の
「使い捨て」のままにすること。

### 2. idp-server 側にセッションスキップの経路が無い

セッションがあっても認証画面を出さずに認可を完了させる経路が、現状は
`prompt=none` 限定でしか実装されていない（`OAuthRequestContext.canAutomaticallyAuthorize`）。

`OAuthRequestStatus.OK_SESSION_ENABLE` という状態が定義され、`OAuthController` /
`OAuthV1Api` にも分岐があるが、**これを返すコードが存在しない**
（`OAuthRequestContext.createResponse()` は `OK` か `OK_ACCOUNT_CREATION` しか返さない）。
`POST /{tenant}/v1/authorizations/{id}/authorize-with-session` という API も存在し、
旧画面 `app-view/src/pages/signin/index.tsx` は呼んでいるが、現行の `/auth/` 画面
（`pages/auth/index.tsx`）は呼んでいない。

つまり機能としては「半分ある」状態。OIDCC を配線する際は、この経路を実装するか
デッドコードとして整理するかの判断が先に要る。
