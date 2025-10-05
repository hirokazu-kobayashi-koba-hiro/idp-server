#!/bin/bash

set -e

usage() {
  echo "Usage: $0 -t <tenant_id> -f <JSON_FILE> [-e <base_url>] [-a <access_token>]"
  exit 1
}

BASE_URL="http://localhost:8080"

while getopts ":t:f:b:a:d:" opt; do
  case $opt in
    t) TENANT_ID="$OPTARG" ;;
    f) JSON_FILE="$OPTARG" ;;
    b) BASE_URL="$OPTARG" ;;
    a) ACCESS_TOKEN="$OPTARG" ;;
    d) DRY_RUN="$OPTARG" ;;
    *) usage ;;
  esac
done

[ -z "$TENANT_ID" ] || [ -z "$JSON_FILE" ] && usage
[ ! -f "$JSON_FILE" ] && echo "❌ JSON file not found: $JSON_FILE" && exit 1
[ -z "$ACCESS_TOKEN" ] && echo "❌ Access token required (-a)" && exit 1

if [ ! -f "$JSON_FILE" ]; then
  echo "❌ Request file not found: $JSON_FILE"
  exit 1
fi

if [ "$DRY_RUN" == true ]; then
  echo ""
  echo "DRY_RUN.........."
  echo ""
fi

# =========================
# 🚀 Onboarding POST request
# =========================
echo "🚀 Sending organization initialization (dry_run=${DRY_RUN})"

RESPONSE=$(curl -s -w "\nHTTP_CODE:%{http_code}" -X POST "${BASE_URL}/v1/admin/organization/initialization?dry_run=${DRY_RUN}" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -H "Content-Type: application/json" \
  --data "@${JSON_FILE}")

BODY=$(echo "$RESPONSE" | sed -n '1,/HTTP_CODE:/p' | sed '$d')
HTTP_CODE=$(echo "$RESPONSE" | grep HTTP_CODE | cut -d: -f2)

echo "📡 HTTP ${HTTP_CODE}"
echo "$BODY"

if [ "$HTTP_CODE" != "200" ]; then
  echo "❌ organization initialization request failed"
  exit 1
else
  echo "✅ organization initialization request successful"
fi
