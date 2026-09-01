#!/bin/bash
# 各テストスイートの run.sh から source して使う共通ライブラリ。
#
# ここに置くのは「ランナーコンテナをどう起動するか」だけ。プラン名・variants・前提手順は
# スイートごとに異なるので各 run.sh が持つ。
#
# ランナー（suite の scripts/run-test-plan.py）はコンテナで動かす。ホストに python の
# バージョンや依存パッケージを要求しないため、CI でもそのまま同じ経路を使える。

# suite の CI と同じ組み合わせ（conformance-suite の test/Dockerfile: python:3.13-alpine + httpx pyparsing）
CONFORMANCE_RUNNER_IMAGE="${CONFORMANCE_RUNNER_IMAGE:-python:3.13-alpine}"
# oidc-conformance-suite/docker-compose.yaml の name: と対応。変わるとネットワークに入れない。
CONFORMANCE_NETWORK="${CONFORMANCE_NETWORK:-idp-conformance_default}"

conformance_lib_dir() {
  cd "$(dirname "${BASH_SOURCE[0]}")" && pwd
}

conformance_repo_root() {
  cd "$(conformance_lib_dir)/../.." && pwd
}

conformance_check_prerequisites() {
  if [ -z "${CONFORMANCE_SUITE_DIR:-}" ]; then
    echo "❌ CONFORMANCE_SUITE_DIR が未設定です。conformance-suite のクローンを指してください。" >&2
    echo "   git clone https://gitlab.com/openid/conformance-suite.git" >&2
    echo "   export CONFORMANCE_SUITE_DIR=\"\$PWD/conformance-suite\"" >&2
    return 1
  fi

  local suite_dir
  suite_dir="$(cd "$CONFORMANCE_SUITE_DIR" && pwd)"
  if [ ! -f "${suite_dir}/scripts/run-test-plan.py" ]; then
    echo "❌ ランナーが見つかりません: ${suite_dir}/scripts/run-test-plan.py" >&2
    return 1
  fi

  if ! docker network inspect "$CONFORMANCE_NETWORK" > /dev/null 2>&1; then
    echo "❌ suite のスタックが起動していません（network: ${CONFORMANCE_NETWORK}）" >&2
    echo "   docker compose -f $(conformance_lib_dir)/../docker-compose.yaml up -d" >&2
    return 1
  fi
}

# run-test-plan.py を実行する。
#
# 引数はそのまま渡す。設定ファイルはコンテナ内の /config（= リポジトリの config/examples）を
# 基点にしたパスで指定すること。
#   例: /config/financial-grade/oidc-test/fapi/private_key_jwt.json
conformance_run() {
  conformance_check_prerequisites || return 1

  local suite_dir repo_root export_dir
  suite_dir="$(cd "$CONFORMANCE_SUITE_DIR" && pwd)"
  repo_root="$(conformance_repo_root)"
  export_dir="${EXPORT_DIR:-$(conformance_lib_dir)/../results}"
  mkdir -p "$export_dir"

  echo "🔍 suite   : https://localhost.emobix.co.uk:8443/ (network: ${CONFORMANCE_NETWORK})"
  echo "🔍 runner  : ${CONFORMANCE_RUNNER_IMAGE}"
  echo "🔍 results : ${export_dir}"
  echo

  # sh -c '<script>' <$0> <$1> ... の形で渡し、ランナーの引数は "$@" で受け直す。
  # 文字列に埋め込むとプラン名の [] やユーザー引数の空白で壊れるため。
  exec docker run --rm -i \
    --network "$CONFORMANCE_NETWORK" \
    -v "${suite_dir}:/suite:ro" \
    -v "${repo_root}/config/examples:/config:ro" \
    -v "${export_dir}:/results" \
    -e CONFORMANCE_SERVER="https://localhost.emobix.co.uk:8443/" \
    -e CONFORMANCE_SERVER_MTLS="https://localhost.emobix.co.uk:8443/" \
    -e CONFORMANCE_DEV_MODE=1 \
    -w /results \
    "$CONFORMANCE_RUNNER_IMAGE" \
    sh -c 'pip install --quiet --disable-pip-version-check --root-user-action=ignore httpx pyparsing \
      && exec python3 /suite/scripts/run-test-plan.py "$@"' \
    conformance-runner \
    --export-dir /results \
    "$@"
}
