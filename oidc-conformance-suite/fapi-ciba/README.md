# FAPI-CIBA ID1

| | |
|---|---|
| 状態 | ❌ 未配線（`run.sh` なし） |
| テストプラン | `fapi-ciba-id1-test-plan` |
| モジュール数 | 70 |
| テナント | `config/examples/financial-grade`（FAPI 1.0 Advanced と同じ） |
| テスト設定 | `config/examples/financial-grade/oidc-test/fapi-ciba/{private_key_jwt_poll,tls_client_auth_poll}.json`（存在する） |
| ブラウザ操作 | **不要**（バックチャネル） |

テストケース詳細は
[`config/examples/financial-grade/oidc-test/fapi-ciba/FAPI-CIBA-test-cases.md`](../../config/examples/financial-grade/oidc-test/fapi-ciba/FAPI-CIBA-test-cases.md)。

## 何が足りないか

**デバイス認証の承認を自動化する仕組み。**

CIBA はブラウザを使わない。認可リクエストはバックチャネルで飛び、ユーザーは自分のデバイスで
承認する。suite はその承認を待つだけなので、`../driver/`（ブラウザ操作）は出番がない。

現状は `config/examples/financial-grade/ciba-device-auth.sh` を人が手で叩く運用になっている。
テスト実行中の適切なタイミングで、これに相当する処理を自動で行うプロセスが要る。

FAPI 1.0 Advanced のドライバとは**別の仕組み**になる。ブラウザではなく、suite の状態を見て
デバイス承認 API を叩くポーラーになるはず。

## variants（未確定）

プランが受け付ける variant キー:

```
client_registration, ciba_mode, client_auth_type, fapi_ciba_profile
```

設定ファイル名が `*_poll.json` なので `ciba_mode=poll` と思われるが、他のキーの値は未調査。
配線する際は suite の `/api/plan/available` で各キーの取りうる値を確認すること。

## 参考

`config/examples/financial-grade/setup.sh` は CIBA 用のクライアントとポリシー
（`authentication-policy/ciba.json`）、テストユーザー `fapi-ciba-test@example.com` を
すでに作成している。テナント側の準備は済んでいる。
