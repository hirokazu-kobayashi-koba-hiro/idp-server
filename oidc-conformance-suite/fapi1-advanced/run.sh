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
#   ./run.sh                 private_key_jwt と mtls の両方（68 モジュール × 2）
#   ./run.sh --list          実行せずプラン一覧のみ
#   ./run.sh --rerun 1:2     プラン1のモジュール2のみ（happy path の動作確認用）
#
# run-test-plan.py に渡す追加オプションはそのまま透過する。

set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")/../lib" && pwd)/runner.sh"

# variants の根拠:
#   fapi_profile             = plain_fapi   地域プロファイル(brazil/uk/ksa)ではない
#   fapi_auth_request_method = pushed       financial テナントが PAR エンドポイントを公開している
#   fapi_response_mode       = jarm         response_modes_supported に jwt が含まれる
#   client_auth_type         = 方式ごとにコードパスが分かれるため両方流す
COMMON="[fapi_profile=plain_fapi][fapi_response_mode=jarm][fapi_auth_request_method=pushed]"

conformance_run \
  "$@" \
  "fapi1-advanced-final-test-plan[client_auth_type=private_key_jwt]${COMMON}" \
  /config/financial-grade/oidc-test/fapi/private_key_jwt.json \
  "fapi1-advanced-final-test-plan[client_auth_type=mtls]${COMMON}" \
  /config/financial-grade/oidc-test/fapi/tls_client_auth.json
