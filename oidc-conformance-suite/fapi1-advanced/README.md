# FAPI 1.0 Advanced Final

| | |
|---|---|
| 状態 | ✅ 配線済み（`run.sh` あり） |
| テストプラン | `fapi1-advanced-final-test-plan` |
| モジュール数 | 68 |
| テナント | `config/examples/financial-grade` |
| テスト設定 | `config/examples/financial-grade/oidc-test/fapi/{private_key_jwt,tls_client_auth}.json` |
| ブラウザ操作 | 必要（`../driver/` を常駐させる） |

## variants

```
fapi1-advanced-final-test-plan[client_auth_type=...][fapi_profile=plain_fapi][fapi_response_mode=jarm][fapi_auth_request_method=pushed]
```

| variant | 値 | 根拠 |
|---|---|---|
| `fapi_profile` | `plain_fapi` | 地域プロファイル（brazil / uk / ksa）ではない |
| `fapi_auth_request_method` | `pushed` | discovery に `pushed_authorization_request_endpoint` がある |
| `fapi_response_mode` | `jarm` | discovery の `response_modes_supported` に `jwt` が含まれる |
| `client_auth_type` | `private_key_jwt` / `mtls` | 方式ごとにコードパスが分かれるため両方流す |

## 手順

```bash
# 1. idp-server とテナント
docker compose up -d
cd config/examples/financial-grade && ./setup.sh

# 2. suite スタック
docker compose -f oidc-conformance-suite/docker-compose.yaml up -d

# 3. ブラウザ操作ドライバ（別ターミナルで常駐）
cd oidc-conformance-suite/driver && npm install && node driver.mjs

# 4. テスト
export CONFORMANCE_SUITE_DIR=/path/to/conformance-suite
./oidc-conformance-suite/fapi1-advanced/run.sh --rerun 1:2   # happy path 1本
./oidc-conformance-suite/fapi1-advanced/run.sh               # 両方式すべて
```

## 実測済みの結果

`private_key_jwt` の happy path（`fapi1-advanced-final`）:

```
FINISHED - result PASSED. 333 log entries - 231 SUCCESS 0 FAILURE, 0 WARNING, 9.4 seconds
```

このモジュールは 1 テスト中に **2 回**ブラウザ認可を行う（2 クライアント目の JARM 確認）。
ドライバはその両方を処理する。

`fapi1-advanced-final-discovery-end-point-verification` は単独で完走し、結果は `WARNING`。

全 68 モジュールの通し実行と、`mtls` プランはまだ実測していない。

## 注意

**プランは直列実行になる。** テスト設定 JSON に `alias` があり callback URL を共有するため、
suite が並列化しない（実行時に下記が出る）。

```
Config '...' contains alias 'idp-server-fapi-private_key_jwt' - not running tests within this plan in parallel.
```

1 モジュール 10 秒として 68 モジュールで 10 分強。ドライバが止まっていると各モジュールが
WAITING のまま 240 秒でタイムアウトするので、先にドライバの常駐を確認すること。
