#!/bin/bash
# FAPI-CIBA ID1 の適合性テストを実行する。
#
# 前提（詳細は同ディレクトリの README.md）:
#   1. idp-server 起動                docker compose up -d
#   2. financial-grade テナント投入    config/examples/financial-grade/setup.sh
#   3. suite スタック起動              docker compose -f oidc-conformance-suite/docker-compose.yaml up -d
#   4. デバイス承認の常駐プロセス      oidc-conformance-suite/ciba-approver/
#   5. suite のクローン                export CONFORMANCE_SUITE_DIR=/path/to/conformance-suite
#
# FAPI 1.0 Advanced と違いブラウザを使わないため、../driver/ は不要。
# 代わりに ../ciba-approver/ を常駐させること。
#
# 使い方:
#   ./run.sh                 private_key_jwt と mtls の両方
#   ./run.sh --list          実行せずプラン一覧のみ
#   ./run.sh --rerun 1:2     プラン1のモジュール2のみ

set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")/../lib" && pwd)/runner.sh"

# variants の根拠:
#   fapi_ciba_profile   = plain_fapi     地域プロファイル(uk/brazil/connectid_au)ではない
#   ciba_mode           = poll           discovery の backchannel_token_delivery_modes_supported
#                                        に poll がある。設定ファイル名も *_poll.json
#   client_registration = static_client  クライアントは setup.sh で事前登録している
#   client_auth_type    = 方式ごとにコードパスが分かれるため両方流す
COMMON="[fapi_ciba_profile=plain_fapi][ciba_mode=poll][client_registration=static_client]"

conformance_run \
  "$@" \
  "fapi-ciba-id1-test-plan[client_auth_type=private_key_jwt]${COMMON}" \
  /config/financial-grade/oidc-test/fapi-ciba/private_key_jwt_poll.json \
  "fapi-ciba-id1-test-plan[client_auth_type=mtls]${COMMON}" \
  /config/financial-grade/oidc-test/fapi-ciba/tls_client_auth_poll.json
