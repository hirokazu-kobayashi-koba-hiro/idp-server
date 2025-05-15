#!/bin/bash

set -e

usage() {
  echo "Usage: $0 -t <tenant_id> -f <JSON_FILE> [-e <base_url>] [-a <access_token>]"
  exit 1
}

BASE_URL="http://localhost:8080"

while getopts ":t:f:e:a:d:" opt; do
  case $opt in
    t) TENANT_ID="$OPTARG" ;;
    f) JSON_FILE="$OPTARG" ;;
    e) BASE_URL="$OPTARG" ;;
    a) ACCESS_TOKEN="$OPTARG" ;;
    d) DRY_RUN="$OPTARG" ;;
    *) usage ;;
  esac
done

[ -z "$TENANT_ID" ] || [ -z "$JSON_FILE" ] && usage
[ ! -f "$JSON_FILE" ] && echo "❌ JSON file not found: $JSON_FILE" && exit 1
[ -z "$ACCESS_TOKEN" ] && echo "❌ Access token required (-a)" && exit 1

echo "🔍 Checking if authorization-server exists: $TENANT_ID"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X GET \
  "${BASE_URL}/v1/management/tenants/${TENANT_ID}/authorization-server" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}")


if [ "$DRY_RUN" == true ]; then
  echo ""
  echo "DRY_RUN.........."
  echo ""
  DRY_RUN_PARM="?dry_run=true"
else
  DRY_RUN_PARM="?dry_run=false"
fi

if [ "$HTTP_CODE" == "200" ]; then
  echo "🔁 Config exists. Updating..."
  METHOD="PUT"
  URL="${BASE_URL}/v1/management/tenants/${TENANT_ID}/authorization-server${DRY_RUN_PARM}"
else
  echo "❌ Unexpected response from GET: HTTP $HTTP_CODE"
  exit 1
fi

RESPONSE=$(curl -s -w "\nHTTP_CODE:%{http_code}" -X "$METHOD" "$URL" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  --data "@${JSON_FILE}")

BODY=$(echo "$RESPONSE" | sed -n '1,/HTTP_CODE:/p' | sed '$d')
FINAL_HTTP_CODE=$(echo "$RESPONSE" | grep HTTP_CODE | cut -d: -f2)

echo "📡 HTTP $FINAL_HTTP_CODE"
echo "$BODY"

if [[ "$METHOD" == "PUT" && "$FINAL_HTTP_CODE" == "200" ]]; then
  echo "✅ authorization-server successfully updated"
else
  echo "❌ authorization-server $METHOD failed"
  exit 1
fi
