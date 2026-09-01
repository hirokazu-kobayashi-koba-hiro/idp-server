#!/bin/bash
# FAPI 2.0 Security Profile Final の適合性テストを実行する。
#
# 前提（詳細は同ディレクトリの README.md）:
#   1. idp-server 起動                docker compose up -d
#   2. fapi2-tenant 投入               config/scripts/e2e-test-data.sh
#   3. suite スタック起動              docker compose -f oidc-conformance-suite/docker-compose.yaml up -d
#   4. ブラウザ操作ドライバ常駐        oidc-conformance-suite/driver/
#   5. suite のクローン                export CONFORMANCE_SUITE_DIR=/path/to/conformance-suite
#
# 使い方:
#   ./run.sh                 private_key_jwt と mtls の両方
#   ./run.sh --list          実行せずプラン一覧のみ
#   ./run.sh --rerun 1:2     プラン1のモジュール2のみ

set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")/../lib" && pwd)/runner.sh"

# variants の根拠（いずれも fapi2-tenant の discovery とテスト設定から確認）:
#   fapi_profile               = plain_fapi      地域プロファイルではない
#   authorization_request_type = simple          RAR ではなく scope ベース
#   openid                     = openid_connect  設定の scope に openid が含まれる
#   grant_management           = disabled        discovery に grant_management_endpoint が無い
COMMON="[fapi_profile=plain_fapi][authorization_request_type=simple][openid=openid_connect][grant_management=disabled]"

# 2 つの設定ファイルは「クライアント認証方式の違い」ではなく **sender-constrain の方式の違い**。
# 取り違えると、証明書を持たない設定に mtls を当てて
# ExtractMTLSCertificatesFromConfiguration で落ちる。
#
#   private_key_jwt.json  client に dpop_signing_alg あり / mtls ブロック無し
#                         resourceUrl は api.local.test（非 mTLS）        → sender_constrain=dpop
#   mtls.json             mtls / mtls2 ブロックあり
#                         resourceUrl は mtls.api.local.test              → sender_constrain=mtls
conformance_run \
  "$@" \
  "fapi2-security-profile-final-test-plan[client_auth_type=private_key_jwt][sender_constrain=dpop]${COMMON}" \
  /config/financial-grade-2.0/oidc-test/fapi2/private_key_jwt.json \
  "fapi2-security-profile-final-test-plan[client_auth_type=mtls][sender_constrain=mtls]${COMMON}" \
  /config/financial-grade-2.0/oidc-test/fapi2/mtls.json
