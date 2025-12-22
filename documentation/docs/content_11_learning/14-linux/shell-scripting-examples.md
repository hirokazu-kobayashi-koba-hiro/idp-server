# シェルスクリプト応用例

実践的なシェルスクリプトの例を紹介します。

---

## 目次

1. [CSVを読み取ってAPIテスト](#csvを読み取ってapiテスト)
2. [その他の応用例](#その他の応用例)

---

## CSVを読み取ってAPIテスト

CSVファイルからテストデータを読み取り、curlでAPIを呼び出してステータスコードに応じたログを出力するスクリプトです。

### テストデータ（CSV）

```csv
# testdata.csv
# method,endpoint,expected_status,description
GET,/api/users,200,ユーザー一覧取得
GET,/api/users/1,200,ユーザー詳細取得
GET,/api/users/9999,404,存在しないユーザー
POST,/api/users,201,ユーザー作成
DELETE,/api/users/1,204,ユーザー削除
GET,/api/health,200,ヘルスチェック
```

### スクリプト本体

```bash
#!/bin/bash
#
# api-test.sh - CSVからテストデータを読み取りAPIテストを実行
#
# Usage: ./api-test.sh <csv_file> [base_url]
#

set -euo pipefail

# =============================================================================
# 設定
# =============================================================================

readonly SCRIPT_NAME=$(basename "$0")
readonly DEFAULT_BASE_URL="http://localhost:8080"
readonly LOG_DIR="./logs"
readonly TIMESTAMP=$(date '+%Y%m%d_%H%M%S')
readonly LOG_FILE="${LOG_DIR}/api-test_${TIMESTAMP}.log"

# カラー出力
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[0;33m'
readonly BLUE='\033[0;34m'
readonly NC='\033[0m' # No Color

# カウンター
total_count=0
success_count=0
failure_count=0
error_count=0

# =============================================================================
# 関数
# =============================================================================

usage() {
    cat << EOF
Usage: $SCRIPT_NAME <csv_file> [base_url]

Arguments:
    csv_file    テストデータのCSVファイル
    base_url    APIのベースURL (デフォルト: $DEFAULT_BASE_URL)

CSV Format:
    method,endpoint,expected_status,description

Example:
    $SCRIPT_NAME testdata.csv
    $SCRIPT_NAME testdata.csv http://localhost:3000
EOF
}

log() {
    local level=$1
    shift
    local message="$*"
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')

    # ファイルに出力
    echo "[$timestamp] [$level] $message" >> "$LOG_FILE"

    # 画面に出力（色付き）
    case $level in
        INFO)
            echo -e "${BLUE}[$level]${NC} $message"
            ;;
        SUCCESS)
            echo -e "${GREEN}[$level]${NC} $message"
            ;;
        WARN)
            echo -e "${YELLOW}[$level]${NC} $message"
            ;;
        ERROR)
            echo -e "${RED}[$level]${NC} $message"
            ;;
        *)
            echo "[$level] $message"
            ;;
    esac
}

# curlでAPIを呼び出し、ステータスコードを返す
call_api() {
    local method=$1
    local url=$2
    local status_code

    # curlを実行してステータスコードのみ取得
    # -s: サイレント, -o /dev/null: レスポンスボディを捨てる, -w: 書式指定出力
    status_code=$(curl -s -o /dev/null -w "%{http_code}" -X "$method" "$url" 2>/dev/null) || {
        echo "000"  # 接続エラー時
        return
    }

    echo "$status_code"
}

# テスト結果を判定してログ出力
evaluate_result() {
    local method=$1
    local endpoint=$2
    local expected=$3
    local actual=$4
    local description=$5

    ((total_count++))

    if [[ "$actual" == "000" ]]; then
        # 接続エラー
        ((error_count++))
        log ERROR "[$method $endpoint] 接続エラー - $description"
        return 2
    elif [[ "$actual" == "$expected" ]]; then
        # 期待通り
        ((success_count++))
        log SUCCESS "[$method $endpoint] $actual (expected: $expected) - $description"
        return 0
    else
        # 期待と異なる
        ((failure_count++))
        log WARN "[$method $endpoint] $actual (expected: $expected) - $description"
        return 1
    fi
}

# サマリーを出力
print_summary() {
    echo ""
    echo "=============================================="
    echo "                テスト結果サマリー              "
    echo "=============================================="
    echo ""
    echo "  総テスト数:   $total_count"
    echo -e "  ${GREEN}成功:${NC}         $success_count"
    echo -e "  ${YELLOW}失敗:${NC}         $failure_count"
    echo -e "  ${RED}エラー:${NC}       $error_count"
    echo ""
    echo "  ログファイル: $LOG_FILE"
    echo ""

    if [[ $failure_count -eq 0 && $error_count -eq 0 ]]; then
        echo -e "  ${GREEN}すべてのテストが成功しました！${NC}"
        return 0
    else
        echo -e "  ${RED}一部のテストが失敗しました${NC}"
        return 1
    fi
}

# =============================================================================
# メイン処理
# =============================================================================

main() {
    # 引数チェック
    if [[ $# -lt 1 ]]; then
        usage
        exit 1
    fi

    local csv_file=$1
    local base_url=${2:-$DEFAULT_BASE_URL}

    # CSVファイルの存在確認
    if [[ ! -f "$csv_file" ]]; then
        echo "Error: CSVファイルが見つかりません: $csv_file" >&2
        exit 1
    fi

    # ログディレクトリ作成
    mkdir -p "$LOG_DIR"

    log INFO "=========================================="
    log INFO "APIテスト開始"
    log INFO "CSVファイル: $csv_file"
    log INFO "ベースURL: $base_url"
    log INFO "=========================================="

    # CSVを1行ずつ読み込み
    while IFS=',' read -r method endpoint expected_status description; do
        # コメント行・空行をスキップ
        [[ "$method" =~ ^#.*$ ]] && continue
        [[ -z "$method" ]] && continue

        # 前後の空白を削除
        method=$(echo "$method" | xargs)
        endpoint=$(echo "$endpoint" | xargs)
        expected_status=$(echo "$expected_status" | xargs)
        description=$(echo "$description" | xargs)

        # APIを呼び出し
        local url="${base_url}${endpoint}"
        local actual_status
        actual_status=$(call_api "$method" "$url")

        # 結果を評価
        evaluate_result "$method" "$endpoint" "$expected_status" "$actual_status" "$description" || true

        # 連続リクエストを避けるため少し待機
        sleep 0.1

    done < "$csv_file"

    log INFO "=========================================="
    log INFO "APIテスト完了"
    log INFO "=========================================="

    # サマリー出力
    print_summary
}

main "$@"
```

### 使い方

```bash
# 実行権限を付与
chmod +x api-test.sh

# デフォルトのベースURL（localhost:8080）で実行
./api-test.sh testdata.csv

# ベースURLを指定して実行
./api-test.sh testdata.csv http://api.example.com

# 結果例
# [INFO] ==========================================
# [INFO] APIテスト開始
# [INFO] CSVファイル: testdata.csv
# [INFO] ベースURL: http://localhost:8080
# [INFO] ==========================================
# [SUCCESS] [GET /api/users] 200 (expected: 200) - ユーザー一覧取得
# [SUCCESS] [GET /api/users/1] 200 (expected: 200) - ユーザー詳細取得
# [SUCCESS] [GET /api/users/9999] 404 (expected: 404) - 存在しないユーザー
# [WARN] [POST /api/users] 400 (expected: 201) - ユーザー作成
# [ERROR] [DELETE /api/users/1] 接続エラー - ユーザー削除
# ...
```

### スクリプトの解説

```
┌─────────────────────────────────────────────────────────────┐
│                   スクリプトの構造                           │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  1. 設定セクション                                           │
│     - 定数の定義（ログディレクトリ、カラーコードなど）       │
│     - カウンター変数の初期化                                 │
│                                                              │
│  2. 関数定義                                                 │
│     - usage()          : ヘルプ表示                         │
│     - log()            : レベル別ログ出力                   │
│     - call_api()       : curlでAPI呼び出し                  │
│     - evaluate_result(): 結果判定とログ出力                 │
│     - print_summary()  : テスト結果サマリー                 │
│                                                              │
│  3. メイン処理                                               │
│     - 引数チェック                                          │
│     - CSVを1行ずつ読み込み                                  │
│     - APIを呼び出して結果を評価                             │
│     - サマリーを出力                                        │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### ポイント解説

#### CSVの読み込み

```bash
while IFS=',' read -r method endpoint expected_status description; do
    # 処理
done < "$csv_file"
```

- `IFS=','` : 区切り文字をカンマに設定
- `read -r` : バックスラッシュをエスケープとして解釈しない
- 各フィールドが対応する変数に格納される

#### curlでステータスコードを取得

```bash
curl -s -o /dev/null -w "%{http_code}" -X "$method" "$url"
```

| オプション | 説明 |
|-----------|------|
| `-s` | サイレントモード（進捗を表示しない） |
| `-o /dev/null` | レスポンスボディを捨てる |
| `-w "%{http_code}"` | ステータスコードを出力 |
| `-X "$method"` | HTTPメソッドを指定 |

#### ステータスコードによる分岐

```bash
if [[ "$actual" == "000" ]]; then
    # 接続エラー（curlが失敗）
    log ERROR "..."
elif [[ "$actual" == "$expected" ]]; then
    # 期待通りのステータスコード
    log SUCCESS "..."
else
    # 期待と異なるステータスコード
    log WARN "..."
fi
```

#### カラー出力

```bash
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly NC='\033[0m'  # リセット

echo -e "${GREEN}成功${NC}"
```

---

## その他の応用例

### 認証付きAPIテスト

```bash
#!/bin/bash
#
# 認証トークンを使用するAPIテスト
#

BASE_URL="http://localhost:8080"
TOKEN=""

# ログイン
login() {
    TOKEN=$(curl -s -X POST "$BASE_URL/api/login" \
        -H "Content-Type: application/json" \
        -d '{"username":"admin","password":"secret"}' \
        | jq -r '.token')

    if [[ -z "$TOKEN" || "$TOKEN" == "null" ]]; then
        echo "Error: ログイン失敗" >&2
        exit 1
    fi
    echo "ログイン成功: トークン取得"
}

# 認証付きリクエスト
api_request() {
    local method=$1
    local endpoint=$2
    local data=${3:-}

    local curl_opts=(-s -X "$method" -H "Authorization: Bearer $TOKEN")

    if [[ -n "$data" ]]; then
        curl_opts+=(-H "Content-Type: application/json" -d "$data")
    fi

    curl "${curl_opts[@]}" "$BASE_URL$endpoint"
}

# メイン
login
api_request GET "/api/users"
api_request POST "/api/users" '{"name":"test"}'
```

### リトライ機能付きリクエスト

```bash
#!/bin/bash
#
# リトライ機能付きAPIリクエスト
#

retry_request() {
    local max_attempts=3
    local attempt=1
    local wait_time=2
    local url=$1

    while [[ $attempt -le $max_attempts ]]; do
        echo "試行 $attempt/$max_attempts: $url"

        local status
        status=$(curl -s -o /dev/null -w "%{http_code}" "$url")

        if [[ "$status" == "200" ]]; then
            echo "成功: $status"
            return 0
        elif [[ "$status" =~ ^5 ]]; then
            # 5xx エラーはリトライ
            echo "サーバーエラー: $status - リトライします..."
            sleep $wait_time
            ((attempt++))
            ((wait_time *= 2))  # 指数バックオフ
        else
            # その他のエラーはリトライしない
            echo "エラー: $status"
            return 1
        fi
    done

    echo "最大試行回数に達しました"
    return 1
}

retry_request "http://localhost:8080/api/health"
```

### 並列実行

```bash
#!/bin/bash
#
# 複数のAPIを並列でテスト
#

endpoints=(
    "/api/users"
    "/api/products"
    "/api/orders"
    "/api/health"
)

test_endpoint() {
    local endpoint=$1
    local status
    status=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:8080$endpoint")
    echo "$endpoint: $status"
}

# 並列実行
for endpoint in "${endpoints[@]}"; do
    test_endpoint "$endpoint" &
done

# すべてのバックグラウンドジョブを待機
wait

echo "完了"
```

---

## まとめ

### 学んだテクニック

| テクニック | 説明 |
|-----------|------|
| CSV読み込み | `IFS=',' read` でフィールド分割 |
| curlオプション | `-w` でステータスコード取得 |
| 条件分岐 | ステータスコードによるログ出力の変更 |
| カラー出力 | ANSIエスケープコード使用 |
| 関数化 | 再利用可能な部品に分割 |
| エラーハンドリング | 接続エラーの検出と処理 |

### 次のステップ

- [シェルスクリプトの基礎](shell-scripting.md) - 基本構文の復習
- [コマンドリファレンス](linux-commands.md) - curlなどのコマンド詳細
