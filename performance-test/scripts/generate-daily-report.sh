#!/bin/bash
#
# 日付ディレクトリの結果をまとめてMarkdownレポートを生成
#
# Usage: ./generate-daily-report.sh [日付]
#
# Examples:
#   ./generate-daily-report.sh           # 今日の結果
#   ./generate-daily-report.sh 2026-02-04
#

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PERF_TEST_DIR="$(dirname "$SCRIPT_DIR")"
TARGET_DATE="${1:-$(date +%Y-%m-%d)}"
RESULT_DIR="${PERF_TEST_DIR}/result/stress/${TARGET_DATE}"
EXECUTION_LOG="${RESULT_DIR}/execution-log.json"
REPORT_FILE="${RESULT_DIR}/daily-report.md"

if [ ! -d "$RESULT_DIR" ]; then
    echo "エラー: 結果ディレクトリが見つかりません: $RESULT_DIR"
    exit 1
fi

# JSONからメトリクスを抽出
extract_metric() {
    local json_file="$1"
    local jq_path="$2"
    local default="${3:-0}"
    jq -r "$jq_path // $default" "$json_file" 2>/dev/null || echo "$default"
}

# レポートヘッダー
cat > "$REPORT_FILE" << EOF
# パフォーマンステスト日報

**実行日**: ${TARGET_DATE}

---

## サマリー

EOF

# 実行ログからテスト一覧を取得
if [ -f "$EXECUTION_LOG" ]; then
    total_tests=$(jq '.tests | length' "$EXECUTION_LOG")
    total_duration=$(jq '[.tests[].duration_seconds] | add // 0' "$EXECUTION_LOG")

    cat >> "$REPORT_FILE" << EOF
| 項目 | 値 |
|------|-----|
| 実行テスト数 | ${total_tests} |
| 総実行時間 | ${total_duration}秒 |

---

## テスト実行履歴

| シナリオ | 説明 | 開始時刻 | 実行時間 | 結果 |
|---------|------|---------|---------|------|
EOF

    jq -r '.tests[] | "| \(.scenario) | \(.description) | \(.start_time) | \(.duration_seconds)秒 | \(if .exit_code == 0 then "成功" else "失敗" end) |"' "$EXECUTION_LOG" >> "$REPORT_FILE"
fi

cat >> "$REPORT_FILE" << EOF

---

## パフォーマンス結果

EOF

# 各結果JSONファイルを処理
for result_file in "$RESULT_DIR"/*.json; do
    [ -f "$result_file" ] || continue
    [ "$(basename "$result_file")" = "execution-log.json" ] && continue

    filename=$(basename "$result_file" .json)

    # メトリクス抽出
    tps=$(extract_metric "$result_file" ".metrics.http_reqs.rate")
    iterations=$(extract_metric "$result_file" ".metrics.iterations.count")
    avg=$(extract_metric "$result_file" ".metrics.http_req_duration.avg")
    p90=$(extract_metric "$result_file" '.metrics.http_req_duration["p(90)"]')
    p95=$(extract_metric "$result_file" '.metrics.http_req_duration["p(95)"]')
    max=$(extract_metric "$result_file" ".metrics.http_req_duration.max")
    vus=$(extract_metric "$result_file" ".metrics.vus.value")
    check_passes=$(extract_metric "$result_file" ".metrics.checks.passes")
    check_fails=$(extract_metric "$result_file" ".metrics.checks.fails")
    http_failed=$(extract_metric "$result_file" ".metrics.http_req_failed.value")

    # 成功率計算
    total_checks=$((check_passes + check_fails))
    if [ "$total_checks" -gt 0 ]; then
        success_rate=$(echo "scale=2; ($check_passes / $total_checks) * 100" | bc 2>/dev/null || echo "100")
    else
        success_rate="100"
    fi

    # エラー率
    error_rate=$(echo "scale=4; $http_failed * 100" | bc 2>/dev/null || echo "0")

    # p95判定
    if (( $(echo "$p95 < 500" | bc -l 2>/dev/null || echo "0") )); then
        p95_status="合格"
    else
        p95_status="不合格"
    fi

    cat >> "$REPORT_FILE" << EOF
### ${filename}

| 指標 | 値 | 判定 |
|------|-----|------|
| VU数 | ${vus} | - |
| TPS | $(printf "%.2f" "$tps") req/s | - |
| 総リクエスト数 | ${iterations} | - |
| 成功率 | ${success_rate}% | $(if (( $(echo "$success_rate >= 100" | bc -l 2>/dev/null || echo "0") )); then echo "合格"; else echo "不合格"; fi) |
| エラー率 | ${error_rate}% | $(if (( $(echo "$error_rate < 0.1" | bc -l 2>/dev/null || echo "0") )); then echo "合格"; else echo "不合格"; fi) |

**レスポンスタイム (ms)**

| 平均 | p90 | p95 | 最大 | p95判定 |
|------|-----|-----|------|--------|
| $(printf "%.2f" "$avg") | $(printf "%.2f" "$p90") | $(printf "%.2f" "$p95") | $(printf "%.2f" "$max") | **${p95_status}** |

---

EOF
done

# フッター
cat >> "$REPORT_FILE" << EOF

## 判定基準

| 指標 | 閾値 |
|------|------|
| p95レイテンシ | < 500ms |
| エラー率 | < 0.1% |
| 成功率 | 100% |

---

*生成日時: $(date '+%Y-%m-%d %H:%M:%S')*
EOF

echo "レポート生成完了: $REPORT_FILE"
echo ""
cat "$REPORT_FILE"
