#!/bin/bash
# OpenID for Verifiable Credential Issuance 1.0 Final / HAIP の issuer 適合性テストを実行する。
#
# 前提:
#   1. idp-server 起動                docker compose up -d
#   2. テナント投入                    config/examples/oid4vci-haip/setup.sh
#   3. suite スタック起動              docker compose -f oidc-conformance-suite/docker-compose.yaml up -d
#   4. ブラウザ操作ドライバ常駐        oidc-conformance-suite/driver/（1 プロセスだけ）
#   5. suite のクローン                export CONFORMANCE_SUITE_DIR=/path/to/conformance-suite
#
# 使い方:
#   ./run.sh                                  全モジュール
#   MODULES=oid4vci-1_0-issuer-metadata-test ./run.sh
#                                             指定したモジュールだけ（カンマ区切り）
#
# プランの固定バリアント（VCIIssuerTestPlanHaip）: client_attestation / DPoP / PAR /
# scope ベースの認可 / authorization_code。選べるのは下の 2 つだけ。
#   credential_format                 = sd_jwt_vc         dc+sd-jwt のみ発行する
#   vci_authorization_code_flow_variant = wallet_initiated  Credential Offer を実装していない

set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")/../lib" && pwd)/runner.sh"

PLAN="oid4vci-1_0-issuer-haip-test-plan[credential_format=sd_jwt_vc][vci_authorization_code_flow_variant=wallet_initiated]"
if [ -n "${MODULES:-}" ]; then
  PLAN="${PLAN}:${MODULES}"
fi

conformance_run \
  "$@" \
  "${PLAN}" \
  /config/oid4vci-haip/oidc-test/haip.json
