#!/bin/bash
#
# ストレステスト実行スクリプト
# - 日付ディレクトリに結果を格納
# - ファイル名に実行日時を記録
#
# Usage: ./run-stress-test.sh [シナリオ名|all]
#
# Examples:
#   ./run-stress-test.sh scenario-5-token-client-credentials
#   ./run-stress-test.sh all
#   VU_COUNT=200 DURATION=60s ./run-stress-test.sh all
#

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PERF_TEST_DIR="$(dirname "$SCRIPT_DIR")"
TODAY=$(date +%Y-%m-%d)
RESULT_DIR="${PERF_TEST_DIR}/result/stress/${TODAY}"
EXECUTION_LOG="${RESULT_DIR}/execution-log.json"

# 結果ディレクトリ作成
mkdir -p "$RESULT_DIR"

# 実行ログ初期化（存在しない場合）
if [ ! -f "$EXECUTION_LOG" ]; then
    echo '{"date": "'"$TODAY"'", "tests": []}' > "$EXECUTION_LOG"
fi

# シナリオ一覧（配列で定義）
SCENARIOS=(
    "scenario-1-authorization-request:認可リクエスト"
    "scenario-2-bc:Backchannel Authentication"
    "scenario-3-ciba-device:CIBA (device)"
    "scenario-3-ciba-sub:CIBA (sub)"
    "scenario-3-ciba-email:CIBA (email)"
    "scenario-3-ciba-phone:CIBA (phone)"
    "scenario-3-ciba-ex-sub:CIBA (ex-sub)"
    "scenario-4-token-password:Token (password)"
    "scenario-5-token-client-credentials:Token (client_credentials)"
    "scenario-6-jwks:JWKS"
    "scenario-7-token-introspection:Token Introspection"
    "scenario-8-authentication-device:デバイス認証"
    "scenario-9-identity-verification-application:本人確認申請"
)

# シナリオ名から説明を取得
get_description() {
    local target="$1"
    for item in "${SCENARIOS[@]}"; do
        local name="${item%%:*}"
        local desc="${item#*:}"
        if [ "$name" = "$target" ]; then
            echo "$desc"
            return 0
        fi
    done
    echo "$target"
}

# テスト実行関数
run_test() {
    local scenario="$1"
    local description=$(get_description "$scenario")
    local script_path="${PERF_TEST_DIR}/stress/${scenario}.js"

    # 実行日時をファイル名に含める
    local exec_timestamp=$(date +%Y%m%d-%H%M%S)
    local result_path="${RESULT_DIR}/${scenario}-${exec_timestamp}.json"

    if [ ! -f "$script_path" ]; then
        echo "警告: スクリプトが見つかりません: $script_path"
        return 1
    fi

    echo ""
    echo "========================================"
    echo "実行中: ${description}"
    echo "シナリオ: ${scenario}"
    echo "========================================"

    local start_time=$(date +%s)
    local start_datetime=$(date '+%Y-%m-%d %H:%M:%S')

    # k6実行
    k6 run --summary-export="$result_path" "$script_path"
    local exit_code=$?

    local end_time=$(date +%s)
    local end_datetime=$(date '+%Y-%m-%d %H:%M:%S')
    local duration=$((end_time - start_time))

    # 実行ログに記録
    local test_entry=$(cat <<EOF
{
    "scenario": "$scenario",
    "description": "$description",
    "start_time": "$start_datetime",
    "end_time": "$end_datetime",
    "duration_seconds": $duration,
    "exit_code": $exit_code,
    "result_file": "$(basename "$result_path")",
    "vu_count": "${VU_COUNT:-120}",
    "test_duration": "${DURATION:-30s}"
}
EOF
)

    # jqで実行ログに追記
    if command -v jq &> /dev/null; then
        local tmp_file=$(mktemp)
        jq --argjson entry "$test_entry" '.tests += [$entry]' "$EXECUTION_LOG" > "$tmp_file"
        mv "$tmp_file" "$EXECUTION_LOG"
    fi

    echo ""
    echo "完了: ${description}"
    echo "  開始: ${start_datetime}"
    echo "  終了: ${end_datetime}"
    echo "  実行時間: ${duration}秒"
    echo "  結果: ${result_path}"
    echo ""

    return $exit_code
}

# シナリオ一覧を表示
show_scenarios() {
    echo "利用可能なシナリオ:"
    for item in "${SCENARIOS[@]}"; do
        local name="${item%%:*}"
        local desc="${item#*:}"
        echo "  $name - $desc"
    done
    echo ""
    echo "  all - 全シナリオを実行"
}

# メイン処理
main() {
    local target="${1:-}"

    if [ -z "$target" ]; then
        echo "Usage: $0 [シナリオ名|all]"
        echo ""
        show_scenarios
        exit 1
    fi

    echo "========================================"
    echo "ストレステスト実行"
    echo "日付: ${TODAY}"
    echo "結果ディレクトリ: ${RESULT_DIR}"
    echo "VU数: ${VU_COUNT:-120}"
    echo "実行時間: ${DURATION:-30s}"
    echo "========================================"

    local total_start=$(date +%s)
    local failed_tests=()

    if [ "$target" = "all" ]; then
        for item in "${SCENARIOS[@]}"; do
            local scenario="${item%%:*}"
            if ! run_test "$scenario"; then
                failed_tests+=("$scenario")
            fi
        done
    else
        if ! run_test "$target"; then
            failed_tests+=("$target")
        fi
    fi

    local total_end=$(date +%s)
    local total_duration=$((total_end - total_start))

    echo ""
    echo "========================================"
    echo "実行完了"
    echo "========================================"
    echo "総実行時間: ${total_duration}秒"
    echo "結果ディレクトリ: ${RESULT_DIR}"
    echo "実行ログ: ${EXECUTION_LOG}"

    if [ ${#failed_tests[@]} -gt 0 ]; then
        echo ""
        echo "失敗したテスト:"
        for t in "${failed_tests[@]}"; do
            echo "  - $t"
        done
        exit 1
    fi

    echo ""
    echo "レポート生成:"
    echo "  ./performance-test/scripts/generate-daily-report.sh ${TODAY}"
}

main "$@"
