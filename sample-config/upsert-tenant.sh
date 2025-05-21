#!/bin/bash

set -e

usage() {
  echo "Usage: $0 -f <JSON_FILE> [-b <base_url>] [-a <access_token>] [-d <dry_run>]"
  exit 1
}

BASE_URL="http://localhost:8080"

while getopts ":f:b:a:d:" opt; do
  case $opt in
    f) JSON_FILE="$OPTARG" ;;
    b) BASE_URL="$OPTARG" ;;
    a) ACCESS_TOKEN="$OPTARG" ;;
    d) DRY_RUN="$OPTARG" ;;
    *) usage ;;
  esac
done

[ ! -f "$JSON_FILE" ] && echo "❌ JSON file not found: $JSON_FILE" && exit 1
[ -z "$ACCESS_TOKEN" ] && echo "❌ Access token required (-a)" && exit 1

TENANT_ID=$(jq -r .tenant.id "$JSON_FILE")

if [ -z "$TENANT_ID" ] || [ "$TENANT_ID" == "null" ]; then
  echo "❌ Could not extract tenant_id from $JSON_FILE"
  exit 1
fi

echo "🔍 Checking if tenant exists: $TENANT_ID"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X GET \
  "${BASE_URL}/v1/management/tenants/${TENANT_ID}" \
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
  URL="${BASE_URL}/v1/management/tenants/${TENANT_ID}${DRY_RUN_PARM}"
  REQUEST_BODY=$(jq -r .tenant "$JSON_FILE")
elif [ "$HTTP_CODE" == "404" ]; then
  echo "🆕 Config not found. Registering new one..."
  METHOD="POST"
  URL="${BASE_URL}/v1/management/tenants${DRY_RUN_PARM}"
  REQUEST_BODY=$(jq "$JSON_FILE")
else
  echo "❌ Unexpected response from GET: HTTP $HTTP_CODE"
  exit 1
fi

echo "REQUEST_BODY: $REQUEST_BODY"

RESPONSE=$(curl -s -w "\nHTTP_CODE:%{http_code}" -X "$METHOD" "$URL" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  --data "${REQUEST_BODY}")

BODY=$(echo "$RESPONSE" | sed -n '1,/HTTP_CODE:/p' | sed '$d')
FINAL_HTTP_CODE=$(echo "$RESPONSE" | grep HTTP_CODE | cut -d: -f2)

echo "📡 HTTP $FINAL_HTTP_CODE"
echo "$BODY"

if [[ "$METHOD" == "POST" && "$FINAL_HTTP_CODE" == "201" ]]; then
  echo "✅ tenants successfully registered"
elif [[ "$METHOD" == "PUT" && "$FINAL_HTTP_CODE" == "200" ]]; then
  echo "✅ tenants successfully updated"
else
  echo "❌ tenants $METHOD failed"
  exit 1
fi
