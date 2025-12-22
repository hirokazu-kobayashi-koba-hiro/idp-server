# シェルスクリプトの基礎

シェルスクリプトの基本的な書き方を学びます。

---

## 目次

1. [シェルスクリプトとは](#シェルスクリプトとは)
2. [基本構文](#基本構文)
3. [変数](#変数)
4. [条件分岐](#条件分岐)
5. [ループ](#ループ)
6. [関数](#関数)
7. [実践的なパターン](#実践的なパターン)

---

## シェルスクリプトとは

### 概要

**シェルスクリプトは、シェルコマンドをファイルに記述して自動実行するもの**です。

```
┌─────────────────────────────────────────────────────────────┐
│                シェルスクリプトの用途                        │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  1. 定型作業の自動化                                         │
│     - バックアップ処理                                       │
│     - ログのローテーション                                   │
│     - デプロイ作業                                          │
│                                                              │
│  2. システム管理                                             │
│     - サービスの起動・停止                                   │
│     - ヘルスチェック                                        │
│     - 監視・アラート                                        │
│                                                              │
│  3. 開発支援                                                 │
│     - ビルドスクリプト                                       │
│     - テスト実行                                            │
│     - 環境構築                                              │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 最初のスクリプト

```bash
#!/bin/bash

# これはコメントです
echo "Hello, World!"
```

```bash
# スクリプトを作成
vim hello.sh

# 実行権限を付与
chmod +x hello.sh

# 実行
./hello.sh
```

### シバン（shebang）

スクリプトの1行目 `#!/bin/bash` は**シバン**と呼ばれ、このスクリプトを解釈するインタプリタを指定します。

```bash
#!/bin/bash      # bash で実行
#!/bin/sh        # POSIX 互換シェルで実行
#!/usr/bin/env bash  # PATH から bash を探して実行（推奨）
#!/usr/bin/env python3  # Python スクリプトの場合
```

---

## 基本構文

### コマンドの実行

```bash
#!/bin/bash

# 単純なコマンド実行
echo "Hello"
ls -la

# コマンドを連続実行
echo "Step 1" && echo "Step 2"  # 前が成功したら次を実行
echo "Step 1" || echo "Failed"  # 前が失敗したら次を実行
echo "Step 1" ; echo "Step 2"   # 前の結果に関わらず次を実行
```

### コマンドの結果を取得

```bash
#!/bin/bash

# コマンド置換
current_date=$(date)
echo "Today is: $current_date"

# バッククォート（古い書き方）
current_date=`date`

# 終了ステータス
ls /nonexistent
echo "Exit status: $?"   # 0 = 成功, 0以外 = 失敗
```

### 終了ステータス

```
┌─────────────────────────────────────────────────────────────┐
│                    終了ステータス                            │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  すべてのコマンドは終了時に「終了ステータス」を返す          │
│                                                              │
│  0      : 成功                                              │
│  1-255  : 失敗（値はエラーの種類を示す）                    │
│                                                              │
│  確認方法: $?                                                │
│                                                              │
│  $ ls /etc/passwd                                           │
│  $ echo $?                                                  │
│  0                                                          │
│                                                              │
│  $ ls /nonexistent                                          │
│  $ echo $?                                                  │
│  2                                                          │
│                                                              │
│  スクリプトからの終了:                                       │
│  exit 0    # 成功で終了                                     │
│  exit 1    # 失敗で終了                                     │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## 変数

### 変数の基本

```bash
#!/bin/bash

# 変数への代入（= の前後にスペースを入れない）
name="Alice"
count=10

# 変数の参照
echo "Hello, $name"
echo "Count: ${count}"    # 中括弧は変数名を明確にする

# 読み取り専用
readonly PI=3.14159

# 変数の削除
unset name
```

### 特殊変数

```bash
#!/bin/bash

echo "スクリプト名: $0"
echo "第1引数: $1"
echo "第2引数: $2"
echo "全引数: $@"
echo "引数の数: $#"
echo "現在のPID: $$"
echo "直前のコマンドの終了ステータス: $?"
echo "バックグラウンドプロセスのPID: $!"
```

```
┌─────────────────────────────────────────────────────────────┐
│                      特殊変数一覧                            │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  $0   : スクリプト自身の名前                                │
│  $1-$9: 位置パラメータ（引数）                              │
│  $#   : 引数の数                                            │
│  $@   : すべての引数（個別に展開）                          │
│  $*   : すべての引数（1つの文字列として展開）               │
│  $$   : 現在のプロセスID                                    │
│  $?   : 直前のコマンドの終了ステータス                      │
│  $!   : 最後のバックグラウンドプロセスのPID                 │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 文字列操作

```bash
#!/bin/bash

str="Hello, World!"

# 文字列の長さ
echo ${#str}           # 13

# 部分文字列
echo ${str:0:5}        # Hello

# 置換
echo ${str/World/Linux}  # Hello, Linux!

# デフォルト値
echo ${var:-"default"}   # var が未定義なら "default"
echo ${var:="default"}   # var が未定義なら代入して "default"
```

### 配列

```bash
#!/bin/bash

# 配列の定義
fruits=("apple" "banana" "cherry")

# 要素へのアクセス
echo ${fruits[0]}      # apple
echo ${fruits[1]}      # banana

# 全要素
echo ${fruits[@]}      # apple banana cherry

# 要素数
echo ${#fruits[@]}     # 3

# 要素の追加
fruits+=("date")

# ループ
for fruit in "${fruits[@]}"; do
    echo "Fruit: $fruit"
done
```

---

## 条件分岐

### if 文

```bash
#!/bin/bash

# 基本形
if [ 条件 ]; then
    処理
fi

# if-else
if [ 条件 ]; then
    処理1
else
    処理2
fi

# if-elif-else
if [ 条件1 ]; then
    処理1
elif [ 条件2 ]; then
    処理2
else
    処理3
fi
```

### 条件式

```bash
#!/bin/bash

# 文字列比較
if [ "$str1" = "$str2" ]; then    # 等しい
if [ "$str1" != "$str2" ]; then   # 等しくない
if [ -z "$str" ]; then            # 空文字列
if [ -n "$str" ]; then            # 空でない

# 数値比較
if [ $a -eq $b ]; then   # 等しい (equal)
if [ $a -ne $b ]; then   # 等しくない (not equal)
if [ $a -lt $b ]; then   # より小さい (less than)
if [ $a -le $b ]; then   # 以下 (less or equal)
if [ $a -gt $b ]; then   # より大きい (greater than)
if [ $a -ge $b ]; then   # 以上 (greater or equal)

# ファイル判定
if [ -e "$file" ]; then   # 存在する
if [ -f "$file" ]; then   # 通常ファイル
if [ -d "$dir" ]; then    # ディレクトリ
if [ -r "$file" ]; then   # 読み取り可能
if [ -w "$file" ]; then   # 書き込み可能
if [ -x "$file" ]; then   # 実行可能
if [ -s "$file" ]; then   # サイズが0より大きい

# 論理演算
if [ 条件1 ] && [ 条件2 ]; then  # AND
if [ 条件1 ] || [ 条件2 ]; then  # OR
if [ ! 条件 ]; then              # NOT
```

### [[ ]] と [ ] の違い

```bash
#!/bin/bash

# [[ ]] は bash の拡張機能（推奨）
if [[ "$str" == pattern* ]]; then   # パターンマッチング
if [[ "$str" =~ ^[0-9]+$ ]]; then   # 正規表現

# [ ] は POSIX 標準（移植性が高い）
if [ "$str" = "value" ]; then
```

### 実例

```bash
#!/bin/bash

# ファイルの存在確認
if [ -f "/etc/passwd" ]; then
    echo "File exists"
else
    echo "File not found"
fi

# 引数のチェック
if [ $# -eq 0 ]; then
    echo "Usage: $0 <filename>"
    exit 1
fi

# コマンドの成功判定
if command -v docker &> /dev/null; then
    echo "Docker is installed"
else
    echo "Docker is not installed"
fi
```

### case 文

```bash
#!/bin/bash

case $1 in
    start)
        echo "Starting..."
        ;;
    stop)
        echo "Stopping..."
        ;;
    restart)
        echo "Restarting..."
        ;;
    *)
        echo "Usage: $0 {start|stop|restart}"
        exit 1
        ;;
esac
```

---

## ループ

### for ループ

```bash
#!/bin/bash

# リストをループ
for i in 1 2 3 4 5; do
    echo "Number: $i"
done

# 範囲指定
for i in {1..5}; do
    echo "Number: $i"
done

# ステップ付き
for i in {0..10..2}; do
    echo "Even: $i"
done

# C言語スタイル
for ((i=0; i<5; i++)); do
    echo "Index: $i"
done

# ファイルをループ
for file in *.txt; do
    echo "Processing: $file"
done

# コマンドの出力をループ
for user in $(cat /etc/passwd | cut -d: -f1); do
    echo "User: $user"
done
```

### while ループ

```bash
#!/bin/bash

# カウンター
count=0
while [ $count -lt 5 ]; do
    echo "Count: $count"
    ((count++))
done

# ファイルを1行ずつ読み込み
while read line; do
    echo "Line: $line"
done < /etc/passwd

# 無限ループ（監視など）
while true; do
    echo "Running..."
    sleep 5
done
```

### until ループ

```bash
#!/bin/bash

# 条件が真になるまでループ
count=0
until [ $count -ge 5 ]; do
    echo "Count: $count"
    ((count++))
done
```

### ループ制御

```bash
#!/bin/bash

for i in {1..10}; do
    if [ $i -eq 5 ]; then
        continue   # 次のイテレーションへ
    fi
    if [ $i -eq 8 ]; then
        break      # ループを抜ける
    fi
    echo "Number: $i"
done
```

---

## 関数

### 関数の定義と呼び出し

```bash
#!/bin/bash

# 関数定義
greet() {
    echo "Hello, $1!"
}

# 関数呼び出し
greet "World"
greet "Alice"

# 別の書き方
function greet2 {
    echo "Hi, $1!"
}
```

### 引数と戻り値

```bash
#!/bin/bash

# 引数を受け取る
add() {
    local result=$(($1 + $2))
    echo $result   # 出力で結果を返す
}

# 関数の結果を取得
sum=$(add 3 5)
echo "Sum: $sum"

# 終了ステータスで成功/失敗を返す
check_file() {
    if [ -f "$1" ]; then
        return 0   # 成功
    else
        return 1   # 失敗
    fi
}

if check_file "/etc/passwd"; then
    echo "File exists"
fi
```

### ローカル変数

```bash
#!/bin/bash

global_var="I'm global"

my_function() {
    local local_var="I'm local"
    global_var="Modified"

    echo "Inside: $local_var"
    echo "Inside: $global_var"
}

my_function
echo "Outside: $global_var"   # Modified
echo "Outside: $local_var"    # (空)
```

---

## 実践的なパターン

### エラーハンドリング

```bash
#!/bin/bash

# エラー時に即座に終了
set -e

# 未定義変数の使用をエラーに
set -u

# パイプのエラーを検出
set -o pipefail

# よく使う組み合わせ
set -euo pipefail

# エラーメッセージを表示して終了
die() {
    echo "Error: $1" >&2
    exit 1
}

# 使用例
[ -f "$config_file" ] || die "Config file not found"
```

### 引数の解析

```bash
#!/bin/bash

# シンプルな引数解析
while getopts "hv:o:" opt; do
    case $opt in
        h)
            echo "Usage: $0 [-h] [-v verbose] [-o output]"
            exit 0
            ;;
        v)
            verbose=$OPTARG
            ;;
        o)
            output=$OPTARG
            ;;
        \?)
            echo "Invalid option: -$OPTARG" >&2
            exit 1
            ;;
    esac
done

# 残りの引数
shift $((OPTIND-1))
echo "Remaining args: $@"
```

### ログ出力

```bash
#!/bin/bash

LOG_FILE="/var/log/myscript.log"

log() {
    local level=$1
    shift
    local message="$@"
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    echo "[$timestamp] [$level] $message" | tee -a "$LOG_FILE"
}

log INFO "Script started"
log WARN "This is a warning"
log ERROR "Something went wrong"
```

### 一時ファイルの扱い

```bash
#!/bin/bash

# 安全な一時ファイル作成
tmp_file=$(mktemp)

# 終了時に確実に削除
trap "rm -f $tmp_file" EXIT

# 一時ファイルを使用
echo "data" > "$tmp_file"
cat "$tmp_file"
```

### スクリプトのテンプレート

```bash
#!/bin/bash
#
# スクリプトの説明
#
# Usage: script.sh [options] <arguments>
#

set -euo pipefail

# 定数
readonly SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
readonly SCRIPT_NAME="$(basename "$0")"

# デフォルト値
verbose=false
output_file=""

# 関数定義
usage() {
    cat << EOF
Usage: $SCRIPT_NAME [options] <input_file>

Options:
    -h, --help      Show this help
    -v, --verbose   Verbose output
    -o, --output    Output file

Examples:
    $SCRIPT_NAME -v input.txt
    $SCRIPT_NAME -o result.txt input.txt
EOF
}

log() {
    if $verbose; then
        echo "[$(date '+%H:%M:%S')] $@"
    fi
}

die() {
    echo "Error: $1" >&2
    exit 1
}

# 引数解析
while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            usage
            exit 0
            ;;
        -v|--verbose)
            verbose=true
            shift
            ;;
        -o|--output)
            output_file="$2"
            shift 2
            ;;
        -*)
            die "Unknown option: $1"
            ;;
        *)
            break
            ;;
    esac
done

# 引数チェック
if [[ $# -eq 0 ]]; then
    usage
    exit 1
fi

input_file="$1"

# メイン処理
main() {
    log "Starting process..."

    [[ -f "$input_file" ]] || die "File not found: $input_file"

    log "Processing: $input_file"

    # 処理をここに書く

    log "Done"
}

main
```

---

## まとめ

### よく使う構文

| 構文 | 説明 |
|------|------|
| `$var` | 変数展開 |
| `$(cmd)` | コマンド置換 |
| `$?` | 終了ステータス |
| `[ ]` / `[[ ]]` | 条件式 |
| `&&` / `||` | 論理演算 |

### ベストプラクティス

- `set -euo pipefail` を使用
- 変数は `"$var"` とクォート
- 一時ファイルは `mktemp` + `trap`
- エラーは stderr (`>&2`) に出力
- 関数内は `local` 変数を使用

### 次のステップ

- [コマンドリファレンス](linux-commands.md) - よく使うコマンド
- [systemd](systemd.md) - サービス管理

---

## 参考リソース

- [Bash Reference Manual](https://www.gnu.org/software/bash/manual/bash.html)
- [ShellCheck](https://www.shellcheck.net/) - シェルスクリプトの静的解析
- [Google Shell Style Guide](https://google.github.io/styleguide/shellguide.html)
