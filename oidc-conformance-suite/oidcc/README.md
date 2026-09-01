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
