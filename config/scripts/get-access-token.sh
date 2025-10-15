#!/bin/bash
set -e

# Usage:
# ./get-access-token.sh -u admin@example.com -p secret -t tenant-abc -e http://localhost:8080 -c mgmt-client -s mgmt-secret
# =========================
# 🧾 Usage
# =========================
usage() {
  echo "Usage: $0 -u <username> -p <password> -t <tenant_id> -f <JSON_FILE> [-e <base_url>] [-c <client_id>] [-s <client_secret>]"
  echo
  echo "Arguments:"
  echo "  -u   Username (resource owner)"
  echo "  -p   Password"
  echo "  -t   Tenant ID"
  echo "  -e   Base URL of the IDP server (default: http://localhost:8080)"
  echo "  -c   Client ID for management API authentication (default: my-management-client)"
  echo "  -s   Client secret for management API authentication (default: my-management-secret)"
  echo
  echo "Example:"
  echo "  $0 -u admin@example.com -p secret -t tenant-abc -e http://localhost:8080 -c mgmt-client -s mgmt-secret"
  exit 1
}

while getopts ":u:p:t:e:c:s:" opt; do
  case $opt in
    u) USERNAME="$OPTARG" ;;
    p) PASSWORD="$OPTARG" ;;
    t) TENANT_ID="$OPTARG" ;;
    e) BASE_URL="$OPTARG" ;;
    c) CLIENT_ID="$OPTARG" ;;
    s) CLIENT_SECRET="$OPTARG" ;;
    *) echo "Usage: $0 -u <username> -p <password> -t <tenant_id> -e <base_url> -c <client_id> -s <client_secret>" && exit 1 ;;
  esac
done

[ -z "$USERNAME" ] || [ -z "$PASSWORD" ] || [ -z "$TENANT_ID" ] && echo "Missing required args" && exit 1

ACCESS_TOKEN=$(curl -s -X POST "${BASE_URL}/${TENANT_ID}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=password" \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode "client_secret=${CLIENT_SECRET}" \
  --data-urlencode "username=${USERNAME}" \
  --data-urlencode "password=${PASSWORD}" \
  --data-urlencode "scope=openid management phone email address offline_access" | jq -r .access_token)

if [ "$ACCESS_TOKEN" == "null" ] || [ -z "$ACCESS_TOKEN" ]; then
  echo "❌ Failed to get access token"
  curl -s -X POST "${BASE_URL}/${TENANT_ID}/v1/tokens" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    --data-urlencode "grant_type=password" \
    --data-urlencode "client_id=${CLIENT_ID}" \
    --data-urlencode "client_secret=${CLIENT_SECRET}" \
    --data-urlencode "username=${USERNAME}" \
    --data-urlencode "password=${PASSWORD}" \
    --data-urlencode "scope=openid management phone email address offline_access" | jq
  exit 1
fi

echo "$ACCESS_TOKEN"
