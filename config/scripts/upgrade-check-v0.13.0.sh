#!/bin/bash
#
# v0.13.0 アップグレード前チェック（Basic 認証 / client_credentials の破壊的変更）
#
# v0.13.0 では OAuth 2.0 のクライアント認証まわりに破壊的変更が入る。該当するクライアントが
# 存在するとアップグレード直後に認証が失敗するため、事前に棚卸しするためのスクリプト。
#
# 詳細は documentation/docs/content_09_project/v0.13.0-impact.md の「13. RFC 6749 準拠強化」。
#
# 検査するもの:
#
#   [13.1] public client の client_credentials
#          token_endpoint_auth_method=none かつ grant_types に client_credentials を持つ。
#          v0.13.0 で 401 invalid_client になる。
#
#   [13.2] Appendix B デコードの影響
#          client_secret_basic かつ平文の client_secret に % または + を含む。
#          v0.13.0 は RFC 6749 Section 2.3.1 に従って percent-decode するため、
#          生のまま送っている場合に認証が失敗する（+ はスペースとして解釈される）。
#
#   [13.3] Base64 アルファベット
#          client_secret_basic かつ "client_id:client_secret" を Base64 した結果に
#          + または / が出る。Basic ヘッダを URL-safe Base64 で組んでいるクライアントが
#          いる場合に影響する（RFC 7617 は標準 Base64 を規定している）。
#
# 使い方:
#
#   ./config/scripts/upgrade-check-v0.13.0.sh
#   ./config/scripts/upgrade-check-v0.13.0.sh -d /etc/idp/clients -d ./my-config
#   ./config/scripts/upgrade-check-v0.13.0.sh -d ./config -x '/examples/e2e/'
#   ./config/scripts/upgrade-check-v0.13.0.sh -p "postgresql://user:pass@host:5432/db"
#   ./config/scripts/upgrade-check-v0.13.0.sh -m "-h host -u user -ppass dbname"
#
# -d と -x は繰り返し指定できる。-d にはディレクトリでも個別の JSON ファイルでも渡せる。
# 設定ファイルと DB は片方だけでも両方でも検査できる。DB を指定しない場合は
# 設定ファイルのみを見るため、管理 API で動的に登録したクライアントは対象外になる。
#
# 終了コード: 0 = 該当なし / 1 = 該当あり / 2 = 実行エラー

set -uo pipefail

CONFIG_PATHS=()
EXCLUDES=()
PSQL_CONN=""
MYSQL_CONN=""
SCAN_CONFIG=1

usage() {
  cat <<'USAGE'
Usage: upgrade-check-v0.13.0.sh [options]

  -d <path>   設定ファイルの走査対象。ディレクトリでも JSON ファイルでも可。
              繰り返し指定できる（既定: ./config）
  -x <pat>    除外するパスのパターン。パス全体に対する部分一致。繰り返し指定可
              例: -x '/examples/e2e/' -x '/templates/'
  -p <conn>   PostgreSQL 接続文字列。例: "postgresql://user:pass@host:5432/idpserver"
  -m <conn>   MySQL の接続引数。例: "-h host -u user -ppass idpserver"
  -D          設定ファイルの走査をスキップし、DB のみ検査する
  -h          このヘルプ

終了コード: 0 = 該当なし / 1 = 該当あり / 2 = 実行エラー
USAGE
  exit 2
}

while getopts ":d:x:p:m:Dh" opt; do
  case $opt in
    d) CONFIG_PATHS+=("$OPTARG") ;;
    x) EXCLUDES+=("$OPTARG") ;;
    p) PSQL_CONN="$OPTARG" ;;
    m) MYSQL_CONN="$OPTARG" ;;
    D) SCAN_CONFIG=0 ;;
    h) usage ;;
    *) usage ;;
  esac
done

if ! command -v python3 &> /dev/null; then
  echo "❌ python3 が必要です" >&2
  exit 2
fi

FOUND=0

echo "=================================================================="
echo " v0.13.0 アップグレード前チェック"
echo "=================================================================="
echo

# ------------------------------------------------------------------ 設定ファイル
if [ "$SCAN_CONFIG" -eq 1 ]; then
  if [ ${#CONFIG_PATHS[@]} -eq 0 ]; then
    CONFIG_PATHS=("./config")
  fi

  for path in "${CONFIG_PATHS[@]}"; do
    if [ ! -e "$path" ]; then
      echo "❌ 見つかりません: $path" >&2
      exit 2
    fi
  done

  echo "▶ 設定ファイル: ${CONFIG_PATHS[*]}"
  if [ ${#EXCLUDES[@]} -gt 0 ]; then
    echo "  除外: ${EXCLUDES[*]}"
  fi
  echo

  collect_files() {
    for path in "${CONFIG_PATHS[@]}"; do
      if [ -d "$path" ]; then
        find "$path" -name '*.json' -type f -print0 2>/dev/null
      else
        printf '%s\0' "$path"
      fi
    done
  }

  filter_excluded() {
    if [ ${#EXCLUDES[@]} -eq 0 ]; then
      cat
      return
    fi
    python3 -c '
import sys
pats = sys.argv[1:]
data = sys.stdin.buffer.read().split(b"\0")
out = [p for p in data if p and not any(x.encode() in p for x in pats)]
sys.stdout.buffer.write(b"\0".join(out))
' "${EXCLUDES[@]}"
  }

  CONFIG_RESULT=$(collect_files | filter_excluded \
    | python3 -c '
import base64, json, sys

def walk(node, path, out):
    """クライアント定義は単体 JSON にも配列にも入れ子にも現れるため再帰的に探す。"""
    if isinstance(node, dict):
        if "token_endpoint_auth_method" in node:
            out.append(node)
        for v in node.values():
            walk(v, path, out)
    elif isinstance(node, list):
        for v in node:
            walk(v, path, out)

hits = {"13.1": [], "13.2": [], "13.3": []}
files = sys.stdin.buffer.read().split(b"\0")

for raw in files:
    if not raw:
        continue
    path = raw.decode("utf-8", "replace")
    try:
        with open(path, encoding="utf-8") as f:
            doc = json.load(f)
    except Exception:
        continue

    clients = []
    walk(doc, path, clients)

    for c in clients:
        method = c.get("token_endpoint_auth_method")
        cid = c.get("client_id") or ""
        alias = c.get("client_id_alias") or ""
        secret = c.get("client_secret") or ""
        label = f"{alias or cid}  ({path})"

        grant_types = c.get("grant_types") or []
        if isinstance(grant_types, str):
            grant_types = [grant_types]

        if method == "none" and "client_credentials" in grant_types:
            hits["13.1"].append(label)

        if method == "client_secret_basic":
            if any(ch in secret for ch in "%+"):
                hits["13.2"].append(label)
            for user in filter(None, {alias, cid}):
                enc = base64.b64encode(f"{user}:{secret}".encode()).decode()
                if "+" in enc or "/" in enc:
                    hits["13.3"].append(label)
                    break

for key in ("13.1", "13.2", "13.3"):
    for label in sorted(set(hits[key])):
        print(f"{key}\t{label}")
')

  for key in 13.1 13.2 13.3; do
    lines=$(printf '%s\n' "$CONFIG_RESULT" | grep "^${key}	" | sed "s/^${key}	//")
    count=$(printf '%s' "$lines" | grep -c . )
    case "$key" in
      13.1) title="public client の client_credentials" ;;
      13.2) title="平文 client_secret に % または +" ;;
      13.3) title="Base64 結果に + または /" ;;
    esac
    if [ "$count" -eq 0 ]; then
      printf "  ✅ [%s] %-38s 該当なし\n" "$key" "$title"
    else
      printf "  ⚠️  [%s] %-38s %s 件\n" "$key" "$title" "$count"
      printf '%s\n' "$lines" | sed 's/^/       - /'
      FOUND=1
    fi
  done
  echo
fi

# ------------------------------------------------------------------ PostgreSQL
run_psql() {
  psql "$PSQL_CONN" -tAq -c "$1" 2>&1
}

if [ -n "$PSQL_CONN" ]; then
  if ! command -v psql &> /dev/null; then
    echo "❌ psql が見つかりません" >&2
    exit 2
  fi
  echo "▶ PostgreSQL"
  echo

  q131="select coalesce(payload->>'client_id_alias', payload->>'client_id')
        from client_configuration
        where payload->>'token_endpoint_auth_method' = 'none'
          and payload->>'grant_types' like '%client_credentials%';"

  q132="select coalesce(payload->>'client_id_alias', payload->>'client_id')
        from client_configuration
        where payload->>'token_endpoint_auth_method' = 'client_secret_basic'
          and payload->>'client_secret' ~ '[%+]';"

  q133="select coalesce(payload->>'client_id_alias', payload->>'client_id')
        from client_configuration
        where payload->>'token_endpoint_auth_method' = 'client_secret_basic'
          and (
            translate(encode(convert_to(coalesce(payload->>'client_id_alias', payload->>'client_id') || ':' || coalesce(payload->>'client_secret',''), 'UTF8'), 'base64'), chr(10), '') ~ '[+/]'
            or
            translate(encode(convert_to((payload->>'client_id') || ':' || coalesce(payload->>'client_secret',''), 'UTF8'), 'base64'), chr(10), '') ~ '[+/]'
          );"

  for pair in "13.1|$q131|public client の client_credentials" \
              "13.2|$q132|平文 client_secret に % または +" \
              "13.3|$q133|Base64 結果に + または /"; do
    key="${pair%%|*}"; rest="${pair#*|}"; sql="${rest%|*}"; title="${rest##*|}"
    out=$(run_psql "$sql")
    if [ $? -ne 0 ] || printf '%s' "$out" | grep -qi "error"; then
      printf "  ❌ [%s] クエリ失敗: %s\n" "$key" "$out"
      exit 2
    fi
    count=$(printf '%s' "$out" | grep -c .)
    if [ "$count" -eq 0 ]; then
      printf "  ✅ [%s] %-38s 該当なし\n" "$key" "$title"
    else
      printf "  ⚠️  [%s] %-38s %s 件\n" "$key" "$title" "$count"
      printf '%s\n' "$out" | sed 's/^/       - /'
      FOUND=1
    fi
  done
  echo
fi

# ------------------------------------------------------------------ MySQL
if [ -n "$MYSQL_CONN" ]; then
  if ! command -v mysql &> /dev/null; then
    echo "❌ mysql が見つかりません" >&2
    exit 2
  fi
  echo "▶ MySQL"
  echo

  # MySQL は TO_BASE64() が標準アルファベットを返す。76 文字ごとに改行が入るため除去する。
  m131="select coalesce(json_unquote(json_extract(payload,'\$.client_id_alias')), json_unquote(json_extract(payload,'\$.client_id')))
        from client_configuration
        where json_unquote(json_extract(payload,'\$.token_endpoint_auth_method')) = 'none'
          and json_search(payload,'one','client_credentials',null,'\$.grant_types') is not null;"

  m132="select coalesce(json_unquote(json_extract(payload,'\$.client_id_alias')), json_unquote(json_extract(payload,'\$.client_id')))
        from client_configuration
        where json_unquote(json_extract(payload,'\$.token_endpoint_auth_method')) = 'client_secret_basic'
          and json_unquote(json_extract(payload,'\$.client_secret')) regexp '[%+]';"

  m133="select coalesce(json_unquote(json_extract(payload,'\$.client_id_alias')), json_unquote(json_extract(payload,'\$.client_id')))
        from client_configuration
        where json_unquote(json_extract(payload,'\$.token_endpoint_auth_method')) = 'client_secret_basic'
          and replace(to_base64(concat(coalesce(json_unquote(json_extract(payload,'\$.client_id_alias')), json_unquote(json_extract(payload,'\$.client_id'))), ':', coalesce(json_unquote(json_extract(payload,'\$.client_secret')),''))), '\n','') regexp '[+/]';"

  for pair in "13.1|$m131|public client の client_credentials" \
              "13.2|$m132|平文 client_secret に % または +" \
              "13.3|$m133|Base64 結果に + または /"; do
    key="${pair%%|*}"; rest="${pair#*|}"; sql="${rest%|*}"; title="${rest##*|}"
    out=$(mysql $MYSQL_CONN -N -B -e "$sql" 2>&1)
    if [ $? -ne 0 ]; then
      printf "  ❌ [%s] クエリ失敗: %s\n" "$key" "$out"
      exit 2
    fi
    count=$(printf '%s' "$out" | grep -c .)
    if [ "$count" -eq 0 ]; then
      printf "  ✅ [%s] %-38s 該当なし\n" "$key" "$title"
    else
      printf "  ⚠️  [%s] %-38s %s 件\n" "$key" "$title" "$count"
      printf '%s\n' "$out" | sed 's/^/       - /'
      FOUND=1
    fi
  done
  echo
fi

echo "=================================================================="
if [ "$FOUND" -eq 0 ]; then
  echo " ✅ 該当なし。v0.13.0 の Basic 認証まわりの破壊的変更による影響はありません。"
  echo "=================================================================="
  exit 0
fi

cat <<'ACTION'
 ⚠️ 該当あり。アップグレード前に対処してください。

 [13.1] client_credentials は confidential client 専用です (RFC 6749 Section 4.4)。
        token_endpoint_auth_method を client_secret_basic 等に変更するか、
        grant_types から client_credentials を外してください。緩和フラグはありません。

 [13.2] client_secret から % と + を取り除く（推奨）か、クライアント側を
        RFC 6749 Section 2.3.1 どおりの encode に修正してください。
        Appendix B は HTML フォームエンコードなので + はスペースを表します。

 [13.3] Basic ヘッダを標準 Base64 (RFC 4648 Section 4) で組んでいるか確認してください。
        URL-safe Base64 で組んでいる場合は修正が必要です。

 詳細: documentation/docs/content_09_project/v0.13.0-impact.md
       https://github.com/hirokazu-kobayashi-koba-hiro/idp-server
ACTION
echo "=================================================================="
exit 1
