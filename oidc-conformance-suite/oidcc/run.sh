#!/bin/bash
# OpenID Connect Core（Basic OP）の適合性テストを実行する。
#
# 前提（詳細は同ディレクトリの README.md）:
#   1. idp-server 起動                docker compose up -d
#   2. テナント投入                    config/examples/oidcc-cross-site/setup.sh
#                                     config/examples/oidcc-cross-site-context-path/setup.sh
#   3. suite スタック起動              docker compose -f oidc-conformance-suite/docker-compose.yaml up -d
#   4. ブラウザ操作ドライバ常駐        oidc-conformance-suite/driver/
#   5. suite のクローン                export CONFORMANCE_SUITE_DIR=/path/to/conformance-suite
#
# 使い方:
#   ./run.sh                 全プラン
#   ./run.sh --list          実行せずプラン一覧のみ
#   ./run.sh --rerun 1       プラン1のみ
#   ./run.sh --rerun 1:2     プラン1のモジュール2のみ（複数はカンマ区切り）

set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")/../lib" && pwd)/runner.sh"

# variants の根拠:
#   server_metadata      設定ファイルが discovery URL を持つか endpoint を直書きしているかで決まる。
#                        どちらの oidc-core-basic.json も server ブロックに issuer/endpoints を
#                        直書きしているため static。
#   client_registration  idp-server は動的クライアント登録を discovery で広告していないため static_client
STATIC="[server_metadata=static][client_registration=static_client]"

# Form Post OP（oidcc-formpost-basic-certification-test-plan）はここに入れていない。
# idp-server が response_mode=form_post を実装していないため、全モジュールが
# CheckCallbackHttpMethodIsPost で落ちる（実測 34 FAILED）。
#   ResponseMode.form_post の responseModeValue が空 → ResponseModeDecidable が
#   isDefinedResponseModeValue() を満たさず query へフォールバックする。
#   HTML の自動 POST を返す実装は存在しない。
# 対応が入ったら config/examples/oidcc-formpost-basic/oidc-test/oidc-core-basic.json を
# oidcc-formpost-basic-certification-test-plan で追加する。
conformance_run \
  "$@" \
  "oidcc-basic-certification-test-plan${STATIC}" \
  /config/oidcc-cross-site/oidc-test/oidc-core-basic.json \
  "oidcc-basic-certification-test-plan${STATIC}" \
  /config/oidcc-cross-site-context-path/oidc-test/oidc-core-basic.json
