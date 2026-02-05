#!/bin/bash
#
# k6 結果JSONからMarkdownレポートを生成するスクリプト
#
# Usage: ./generate-report.sh <result-json-path> [compare-json-path]
#

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PERF_TEST_DIR="$(dirname "$SCRIPT_DIR")"
REPORTS_DIR="${PERF_TEST_DIR}/result/reports"

# 引数チェック
if [ -z "$1" ]; then
    echo "Usage: $0 <result-json-path> [compare-json-path]"
    exit 1
fi

RESULT_JSON="$1"
COMPARE_JSON="${2:-}"

if [ ! -f "$RESULT_JSON" ]; then
    echo "Error: 結果ファイルが見つかりません: $RESULT_JSON"
    exit 1
fi

# レポートディレクトリ作成
mkdir -p "$REPORTS_DIR"

# ファイル名からシナリオ名を抽出
BASENAME=$(basename "$RESULT_JSON" .json)
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
REPORT_FILE="${REPORTS_DIR}/${BASENAME}-report-${TIMESTAMP}.md"

# JSONからメトリクスを抽出する関数
extract_metric() {
    local json_file="$1"
    local jq_path="$2"
    local default="${3:-0}"

    local value
    value=$(jq -r "$jq_path // $default" "$json_file" 2>/dev/null || echo "$default")
    echo "$value"
}

# パーセンテージ計算
calc_percentage() {
    local current="$1"
    local previous="$2"

    if [ "$previous" = "0" ] || [ -z "$previous" ]; then
        echo "N/A"
        return
    fi

    if command -v bc &> /dev/null; then
        local pct
        pct=$(echo "scale=2; (($current - $previous) / $previous) * 100" | bc)
        echo "${pct}%"
    else
        echo "N/A"
    fi
}

# メトリクス抽出
TPS=$(extract_metric "$RESULT_JSON" ".metrics.http_reqs.rate")
ITERATIONS=$(extract_metric "$RESULT_JSON" ".metrics.iterations.count")
DURATION_AVG=$(extract_metric "$RESULT_JSON" ".metrics.http_req_duration.avg")
DURATION_P90=$(extract_metric "$RESULT_JSON" '.metrics.http_req_duration["p(90)"]')
DURATION_P95=$(extract_metric "$RESULT_JSON" '.metrics.http_req_duration["p(95)"]')
DURATION_MAX=$(extract_metric "$RESULT_JSON" ".metrics.http_req_duration.max")
DURATION_MIN=$(extract_metric "$RESULT_JSON" ".metrics.http_req_duration.min")
VUS=$(extract_metric "$RESULT_JSON" ".metrics.vus.value")
VUS_MAX=$(extract_metric "$RESULT_JSON" ".metrics.vus_max.value")
DATA_SENT=$(extract_metric "$RESULT_JSON" ".metrics.data_sent.count")
DATA_RECEIVED=$(extract_metric "$RESULT_JSON" ".metrics.data_received.count")
CHECK_PASSES=$(extract_metric "$RESULT_JSON" ".metrics.checks.passes")
CHECK_FAILS=$(extract_metric "$RESULT_JSON" ".metrics.checks.fails")
HTTP_FAILED=$(extract_metric "$RESULT_JSON" ".metrics.http_req_failed.value")

# チェック結果から成功率を計算
TOTAL_CHECKS=$((CHECK_PASSES + CHECK_FAILS))
if [ "$TOTAL_CHECKS" -gt 0 ]; then
    SUCCESS_RATE=$(echo "scale=2; ($CHECK_PASSES / $TOTAL_CHECKS) * 100" | bc 2>/dev/null || echo "100")
else
    SUCCESS_RATE="100"
fi

# エラー率計算
ERROR_RATE=$(echo "scale=4; $HTTP_FAILED * 100" | bc 2>/dev/null || echo "0")

# 判定関数
evaluate_metric() {
    local value="$1"
    local threshold="$2"
    local comparison="$3"

    if [ "$comparison" = "lt" ]; then
        if (( $(echo "$value < $threshold" | bc -l 2>/dev/null || echo "0") )); then
            echo "合格"
        else
            echo "不合格"
        fi
    else
        if (( $(echo "$value > $threshold" | bc -l 2>/dev/null || echo "0") )); then
            echo "合格"
        else
            echo "不合格"
        fi
    fi
}

# データサイズを人間が読みやすい形式に変換
format_bytes() {
    local bytes="$1"
    if [ -z "$bytes" ] || [ "$bytes" = "0" ]; then
        echo "0 B"
        return
    fi

    if command -v bc &> /dev/null; then
        if (( $(echo "$bytes >= 1073741824" | bc -l) )); then
            echo "$(echo "scale=2; $bytes / 1073741824" | bc) GB"
        elif (( $(echo "$bytes >= 1048576" | bc -l) )); then
            echo "$(echo "scale=2; $bytes / 1048576" | bc) MB"
        elif (( $(echo "$bytes >= 1024" | bc -l) )); then
            echo "$(echo "scale=2; $bytes / 1024" | bc) KB"
        else
            echo "${bytes} B"
        fi
    else
        echo "${bytes} B"
    fi
}

# 判定
P95_RESULT=$(evaluate_metric "$DURATION_P95" "500" "lt")
ERROR_RESULT=$(evaluate_metric "$ERROR_RATE" "0.1" "lt")

# レポート生成
cat > "$REPORT_FILE" << EOF
# パフォーマンステストレポート

## テスト情報

| 項目 | 値 |
|------|-----|
| シナリオ | \`${BASENAME}\` |
| 実行日時 | $(date '+%Y-%m-%d %H:%M:%S') |
| 結果ファイル | \`$(basename "$RESULT_JSON")\` |

---

## 設定

| パラメータ | 値 |
|-----------|-----|
| VU数 | ${VUS} |
| 最大VU数 | ${VUS_MAX} |

---

## サマリー

| 指標 | 値 | 判定 |
|------|-----|------|
| **TPS** | $(printf "%.2f" "$TPS") req/s | - |
| **総リクエスト数** | ${ITERATIONS} | - |
| **成功率** | ${SUCCESS_RATE}% | $(if [ "$SUCCESS_RATE" = "100" ]; then echo "合格"; else echo "不合格"; fi) |
| **エラー率** | ${ERROR_RATE}% | ${ERROR_RESULT} |

---

## レスポンスタイム (ms)

| パーセンタイル | 値 | 閾値 | 判定 |
|---------------|-----|------|------|
| 平均 | $(printf "%.2f" "$DURATION_AVG") ms | - | - |
| p90 | $(printf "%.2f" "$DURATION_P90") ms | - | - |
| **p95** | $(printf "%.2f" "$DURATION_P95") ms | < 500ms | **${P95_RESULT}** |
| 最大 | $(printf "%.2f" "$DURATION_MAX") ms | - | - |
| 最小 | $(printf "%.2f" "$DURATION_MIN") ms | - | - |

---

## データ転送量

| 方向 | サイズ |
|------|--------|
| 送信 | $(format_bytes "$DATA_SENT") |
| 受信 | $(format_bytes "$DATA_RECEIVED") |

---

## チェック結果

| チェック | 成功 | 失敗 | 成功率 |
|---------|------|------|--------|
| 全チェック | ${CHECK_PASSES} | ${CHECK_FAILS} | ${SUCCESS_RATE}% |

EOF

# 前回比較がある場合
if [ -n "$COMPARE_JSON" ] && [ -f "$COMPARE_JSON" ]; then
    PREV_TPS=$(extract_metric "$COMPARE_JSON" ".metrics.http_reqs.rate")
    PREV_P95=$(extract_metric "$COMPARE_JSON" '.metrics.http_req_duration["p(95)"]')

    TPS_CHANGE=$(calc_percentage "$TPS" "$PREV_TPS")
    P95_CHANGE=$(calc_percentage "$DURATION_P95" "$PREV_P95")

    cat >> "$REPORT_FILE" << EOF

---

## 前回比較

| 指標 | 今回 | 前回 | 変化率 |
|------|------|------|--------|
| TPS | $(printf "%.2f" "$TPS") | $(printf "%.2f" "$PREV_TPS") | ${TPS_CHANGE} |
| p95 | $(printf "%.2f" "$DURATION_P95") ms | $(printf "%.2f" "$PREV_P95") ms | ${P95_CHANGE} |

**前回結果:** \`$(basename "$COMPARE_JSON")\`

EOF
fi

# 最終判定
OVERALL="合格"
if [ "$P95_RESULT" = "不合格" ] || [ "$ERROR_RESULT" = "不合格" ]; then
    OVERALL="不合格"
fi

cat >> "$REPORT_FILE" << EOF

---

## 総合判定

| 結果 |
|------|
| **${OVERALL}** |

EOF

echo "レポート生成完了: $REPORT_FILE"
echo ""
cat "$REPORT_FILE"
