#!/usr/bin/env bash
# ====== .env ロード ======
# 使い方: ./verify-rls.sh [PATH_TO_ENV]
ENV_FILE="${1:-.env}"
if [[ -f "$ENV_FILE" ]]; then
  # CRLF対策（Windows由来の.envを安全に食べる）
  sed -i.bak 's/\r$//' "$ENV_FILE" 2>/dev/null || true
  # .env の key=value を一括で export
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
else
  echo "⚠ .env が見つからんかった: ${ENV_FILE}（環境変数はデフォルト値を使用）"
fi

set -euo pipefail

# ====== PG接続（.env に無ければデフォルト） ======
PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
PGDATABASE="${PGDATABASE:-idpserver}"

# ====== アプリ（RLS適用ユーザー） ======
# .env 由来: DB_WRITER_USER_NAME / DB_WRITER_PASSWORD
APP_USER="${DB_WRITER_USER_NAME:-idp_app_user}"
APP_PASS="${DB_WRITER_PASSWORD:-apppass}"

# ====== 管理者（BYPASSRLSユーザー） ======
# .env 由来: CONTROL_PLANE_DB_WRITER_USER_NAME / CONTROL_PLANE_DB_WRITER_PASSWORD
ADMIN_USER="${CONTROL_PLANE_DB_WRITER_USER_NAME:-idp_admin_user}"
ADMIN_PASS="${CONTROL_PLANE_DB_WRITER_PASSWORD:-adminpass}"

# ====== テナントID ======
# A は .env の ADMIN_TENANT_ID を既定に、B は任意（無ければダミー）
TENANT_A="${ADMIN_TENANT_ID:-00000000-0000-0000-0000-0000000000aa}"
TENANT_B="${TENANT_B:-67e7eae6-62b0-4500-9eff-87459f63fc66}"

# ====== DSN ======
APP_DSN="postgresql://${APP_USER}:${APP_PASS}@${PGHOST}:${PGPORT}/${PGDATABASE}"
ADMIN_DSN="postgresql://${ADMIN_USER}:${ADMIN_PASS}@${PGHOST}:${PGPORT}/${PGDATABASE}"

# ====== psql 共通 ======
PSQL_APP="psql \"$APP_DSN\" -v ON_ERROR_STOP=1 -X -t -A -F $'\t'"
PSQL_ADMIN="psql \"$ADMIN_DSN\" -v ON_ERROR_STOP=1 -X -t -A -F $'\t'"


RLS_TABLES=(
  tenant tenant_invitation organization_tenants authorization_server_configuration
  permission role role_permission idp_user_roles client_configuration
  authorization_request authorization_code_grant oauth_token
  backchannel_authentication_request ciba_grant authorization_granted
  security_event security_event_hook_configurations security_event_hook_results
  federation_configurations federation_sso_session idp_user_sso_credentials
  authentication_configuration authentication_policy authentication_transaction
  authentication_interactions identity_verification_configuration
  identity_verification_application identity_verification_result
  idp_user_lifecycle_event_result audit_log
)

# 見やすい色
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
SEP="--------------------------------------------------------"

# psql共通オプション（-t=ヘッダ無し, -A=区切り素のまま, -F=区切り文字）
PSQL="psql \"$APP_DSN\" -v ON_ERROR_STOP=1 -X -t -A -F $'\t'"

echo "$SEP"
echo "RLS Visibility Test for Tenant A ($TENANT_A)"
echo "$SEP"

run_select() {
  local table="$1" tenant="$2"
  # 1行: table_name \t visible_rows を返す
  # BEGIN後にtenantセット、SELECTしてROLLBACK
  local out err rc
  set +e
  out=$(eval "$PSQL" <<<"
BEGIN;
SELECT set_config('app.tenant_id', '${tenant}', true);
SELECT '${table}' AS table_name, count(*) AS visible_rows FROM ${table};
ROLLBACK;
")
  rc=$?
  err=$out
  set -e
  if [[ $rc -ne 0 ]]; then
    echo -e ">> ${table}\t${RED}ERROR${NC}\t${err}"
  else
    # out は「table_name\tvisible_rows」形式
    # 例: "permission\t2"
    if [[ -z "$out" ]]; then
      echo -e ">> ${table}\t${RED}NO_OUTPUT${NC}"
    else
      # 可視行数を抜き出して色付け
      local rows
      rows=$(awk -F'\t' '{print $2}' <<<"$out")
      if [[ "$rows" =~ ^[0-9]+$ ]]; then
        echo -e ">> ${table}\t${GREEN}${rows}${NC}"
      else
        echo -e ">> ${table}\t${YELLOW}${out}${NC}"
      fi
    fi
  fi
}

for t in "${RLS_TABLES[@]}"; do
  run_select "$t" "$TENANT_A"
done

echo "$SEP"
echo "RLS Visibility Test for Tenant B ($TENANT_B)"
echo "$SEP"

for t in "${RLS_TABLES[@]}"; do
  run_select "$t" "$TENANT_B"
done

echo "$SEP"
echo -e "${GREEN}✅ RLS確認完了${NC}"
echo "$SEP"

# ====== 未設定だとエラーになるか：全RLSテーブルを総点検 ======
echo -e "${YELLOW}未設定エラーチェック（全RLSテーブル）${NC}"

fail_cnt=0
ok_cnt=0

check_unset_error() {
  local table="$1"
  # app.tenant_id をセットせず、各テーブルに素のSELECTを投げる
  # current_setting('app.tenant_id') を policy で参照していればエラーになるはず
  set +e
  local out
  out=$(psql "$APP_DSN" -X -v ON_ERROR_STOP=1 -t -A -c "SELECT 1 FROM ${table} LIMIT 1;" 2>&1)
  local rc=$?
  if [[ $rc -ne 0 ]]; then
    echo -e ">> ${table}\t${GREEN}OK${NC}\tエラー検出: ${out}"
    ((ok_cnt++))
  else
    echo -e ">> ${table}\t${RED}NG${NC}\t未設定でも通った（policyが current_setting('app.tenant_id') を使ってない/ WITH CHECKのみ等の可能性）"
    ((fail_cnt++))
  fi
}

for t in "${RLS_TABLES[@]}"; do
  echo "RLS check: $t"
  check_unset_error "$t"
done

echo "$SEP"
if [[ $fail_cnt -eq 0 ]]; then
  echo -e "${GREEN}✅ 未設定エラーチェック 合格：${ok_cnt} / ${#RLS_TABLES[@]}${NC}"
else
  echo -e "${RED}❌ 未設定でも通ったテーブル：${fail_cnt}件${NC}（policy見直し推奨）"
fi
echo "$SEP"

