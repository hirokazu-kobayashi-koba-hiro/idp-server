# FAPI-CIBA ID1

| | |
|---|---|
| 状態 | ✅ 配線済み（`run.sh` あり） |
| テストプラン | `fapi-ciba-id1-test-plan` |
| モジュール数 | 70（variants 適用後はこれより少ない） |
| テナント | `config/examples/financial-grade`（FAPI 1.0 Advanced と同じ） |
| テスト設定 | `config/examples/financial-grade/oidc-test/fapi-ciba/{private_key_jwt_poll,tls_client_auth_poll}.json` |
| ブラウザ操作 | **不要**（バックチャネル） |
| 必要な常駐プロセス | `../ciba-approver/`（デバイス承認） |

テストケース詳細は
[`FAPI-CIBA-test-cases.md`](../../config/examples/financial-grade/oidc-test/fapi-ciba/FAPI-CIBA-test-cases.md)。

## variants

```
fapi-ciba-id1-test-plan[client_auth_type=...][fapi_ciba_profile=plain_fapi][ciba_mode=poll][client_registration=static_client]
```

| variant | 値 | 根拠 |
|---|---|---|
| `fapi_ciba_profile` | `plain_fapi` | 地域プロファイル（uk / brazil / connectid_au）ではない |
| `ciba_mode` | `poll` | discovery の `backchannel_token_delivery_modes_supported` に `poll` がある。設定ファイル名も `*_poll.json` |
| `client_registration` | `static_client` | クライアントは `setup.sh` で事前登録している |
| `client_auth_type` | `private_key_jwt` / `mtls` | 方式ごとにコードパスが分かれるため両方流す |

## 手順

FAPI 1.0 Advanced と違い**ブラウザを使わない**ので `../driver/` は不要。代わりに
`../ciba-approver/` を常駐させる。

```bash
# 1. idp-server とテナント
docker compose up -d
cd config/examples/financial-grade && ./setup.sh

# 2. suite スタック
docker compose -f oidc-conformance-suite/docker-compose.yaml up -d

# 3. デバイス承認プロセス（別ターミナルで常駐）
cd oidc-conformance-suite/ciba-approver && node approver.mjs

# 4. テスト
export CONFORMANCE_SUITE_DIR=/path/to/conformance-suite
./oidc-conformance-suite/fapi-ciba/run.sh --rerun 1:2   # happy path 1 本
./oidc-conformance-suite/fapi-ciba/run.sh               # 両方式すべて
```

## デバイス承認の自動化

CIBA はブラウザを使わない。認可リクエストはバックチャネルで飛び、ユーザーは自分のデバイスで
承認する。`../ciba-approver/approver.mjs` は idp-server のデバイス側 API をポーリングして
それを肩代わりする。手動版は `config/examples/financial-grade/ciba-device-auth.sh`。

```
GET  /{tenant}/v1/authentication-devices/{deviceId}/authentications  保留中の認証取引
POST /{tenant}/v1/authentications/{txId}/password-authentication     承認
POST /{tenant}/v1/authentications/{txId}/authentication-cancel       拒否
```

CIBA の認証ポリシー（`authentication-policy/ciba.json`）は password 1 ステップなので、
承認は `password-authentication` 1 回で済む。

### すぐ承認してはいけない

**承認を遅らせないと happy path が落ちる。** テストは
`authorization_pending` (400) を **2 回**観測してから承認されることを期待している
（`Call token endpoint expecting pending (second time)`）。

すぐ承認すると最初のポーリングで既にトークンが発行され、こう落ちる。

```
Block name: 'Verify token endpoint response is pending or slow_down'
Condition: CheckTokenEndpointHttpStatus400   actual = 200, expected = 400
```

suite のポーリング間隔は約 5 秒なので、既定で **12 秒**待ってから承認する
（`CIBA_APPROVE_DELAY_MS` で変更可能）。人がデバイスで承認する実際の間合いにも近い。

### テストごとの振る舞い

`approver.mjs` の `BEHAVIORS` にテスト名で列挙する。

| action | 対象 | 内容 |
|---|---|---|
| `approve`（既定） | 大半 | デバイスで承認する |
| `cancel` | `user-rejects-authentication` | 拒否して `access_denied` を返させる |
| `ignore` | `auth-req-id-expired` | 何もしない（認証要求の期限切れを確認する） |

## 環境変数

| 変数 | 既定 | 用途 |
|---|---|---|
| `SUITE` | `https://localhost:8443` | suite の API（テスト名の判別に使う） |
| `IDP_BASE_URL` | `https://api.local.test` | idp-server |
| `IDP_ROOT_CA` | `<repo>/docker/nginx/certs/rootCA.pem` | ローカル CA |
| `CIBA_APPROVE_DELAY_MS` | `12000` | 承認までの待ち時間 |
| `CIBA_TENANT_ID` / `CIBA_DEVICE_ID` | financial-grade の値 | 対象テナント / デバイス |
| `CIBA_USERNAME` / `CIBA_PASSWORD` | `fapi-ciba-test@example.com` | 承認に使うユーザー |
