#!/bin/bash
# FAPI 1.0 Advanced Final の適合性テストを実行する。
#
# 前提（詳細は同ディレクトリの README.md）:
#   1. idp-server 起動                docker compose up -d
#   2. financial-grade テナント投入    config/examples/financial-grade/setup.sh
#   3. suite スタック起動              docker compose -f oidc-conformance-suite/docker-compose.yaml up -d
#   4. ブラウザ操作ドライバ常駐        oidc-conformance-suite/driver/
#   5. suite のクローン                export CONFORMANCE_SUITE_DIR=/path/to/conformance-suite
#
# 使い方:
#   ./run.sh                 private_key_jwt と mtls × pushed と by_value（4 プラン）
#   ./run.sh --list          実行せずプラン一覧のみ
#   ./run.sh --rerun 1:2     プラン1のモジュール2のみ（happy path の動作確認用）
#
# run-test-plan.py に渡す追加オプションはそのまま透過する。

set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")/../lib" && pwd)/runner.sh"

# variants の根拠:
#   fapi_profile             = plain_fapi   地域プロファイル(brazil/uk/ksa)ではない
#   fapi_auth_request_method = 両方流す     pushed は PAR の中のリクエストオブジェクト、by_value は
#                                           認可エンドポイントに値で渡すリクエストオブジェクト。
#                                           組み立ての経路が分かれる（カスタムパラメータの扱い等）
#   fapi_response_mode       = jarm         response_modes_supported に jwt が含まれる
#   client_auth_type         = 方式ごとにコードパスが分かれるため両方流す
# by_value は後ろに置く（--rerun のプラン番号 1・2 を pushed のまま変えないため）
PUSHED="[fapi_profile=plain_fapi][fapi_response_mode=jarm][fapi_auth_request_method=pushed]"
BY_VALUE="[fapi_profile=plain_fapi][fapi_response_mode=jarm][fapi_auth_request_method=by_value]"

conformance_run \
  "$@" \
  "fapi1-advanced-final-test-plan[client_auth_type=private_key_jwt]${PUSHED}" \
  /config/financial-grade/oidc-test/fapi/private_key_jwt.json \
  "fapi1-advanced-final-test-plan[client_auth_type=mtls]${PUSHED}" \
  /config/financial-grade/oidc-test/fapi/tls_client_auth.json \
  "fapi1-advanced-final-test-plan[client_auth_type=private_key_jwt]${BY_VALUE}" \
  /config/financial-grade/oidc-test/fapi/private_key_jwt.json \
  "fapi1-advanced-final-test-plan[client_auth_type=mtls]${BY_VALUE}" \
  /config/financial-grade/oidc-test/fapi/tls_client_auth.json
