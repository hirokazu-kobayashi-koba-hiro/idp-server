#!/bin/bash
set -e

usage() {
  echo "Usage: $0 -t <tenant_id> -f <JSON_FILE> [-e <base_url>] [-a <access_token>]"
  exit 1
}

BASE_URL="http://localhost:8080"

while getopts ":t:f:e:a:" opt; do
  case $opt in
    t) TENANT_ID="$OPTARG" ;;
    f) JSON_FILE="$OPTARG" ;;
    e) BASE_URL="$OPTARG" ;;
    a) ACCESS_TOKEN="$OPTARG" ;;
    *) usage ;;
  esac
done

[ -z "$TENANT_ID" ] || [ -z "$JSON_FILE" ] && usage
[ ! -f "$JSON_FILE" ] && echo "❌ JSON file not found: $JSON_FILE" && exit 1
[ -z "$ACCESS_TOKEN" ] && echo "❌ Access token required (-a)" && exit 1

CLIENT_ID=$(jq -r .client.client_id "$JSON_FILE")

if [ -z "$CLIENT_ID" ] || [ "$CLIENT_ID" == "null" ]; then
  echo "❌ Could not extract client_id from $JSON_FILE"
  exit 1
fi

echo "🔍 Checking if client exists: $CLIENT_ID"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X GET \
  "${BASE_URL}/v1/management/tenants/${TENANT_ID}/clients/${CLIENT_ID}" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}")

if [ "$HTTP_CODE" == "200" ]; then
  echo "🔁 Client exists. Updating..."
  METHOD="PUT"
  URL="${BASE_URL}/v1/management/tenants/${TENANT_ID}/clients/${CLIENT_ID}"
elif [ "$HTTP_CODE" == "404" ]; then
  echo "🆕 Client not found. Registering new one..."
  METHOD="POST"
  URL="${BASE_URL}/v1/management/tenants/${TENANT_ID}/clients"
else
  echo "❌ Unexpected response from GET clients/${CLIENT_ID}: HTTP $HTTP_CODE"
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

if [[ "$METHOD" == "POST" && "$FINAL_HTTP_CODE" == "201" ]]; then
  echo "✅ Client successfully registered"
elif [[ "$METHOD" == "PUT" && "$FINAL_HTTP_CODE" == "200" ]]; then
  echo "✅ Client successfully updated"
else
  echo "❌ Client $METHOD failed"
  exit 1
fi
