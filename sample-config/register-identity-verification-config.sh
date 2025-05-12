#!/bin/bash

set -e

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
  echo "  -f   Client definition JSON file (e.g. ./sample-config/clients/clientSecretBasic.json)"
  echo "  -e   Base URL of the IDP server (default: http://localhost:8080)"
  echo "  -c   Client ID for management API authentication (default: my-management-client)"
  echo "  -s   Client secret for management API authentication (default: my-management-secret)"
  echo
  echo "Example:"
  echo "  $0 -u admin@example.com -p secret -t tenant-abc -f ./sample-config/clients/clientSecretBasic.json -e http://localhost:8080 -c mgmt-client -s mgmt-secret"
  exit 1
}

# =========================
# 🧩 Defaults
# =========================
BASE_URL="http://localhost:8080"
CLIENT_ID="clientSecretPost"
CLIENT_SECRET="clientSecretPostPassword1234567890123456789012345678901234567890123456789012345678901234567890"

# =========================
# 🎯 Parse arguments
# =========================
while getopts ":u:p:t:f:e:c:s:" opt; do
  case $opt in
    u) USERNAME="$OPTARG" ;;
    p) PASSWORD="$OPTARG" ;;
    t) TENANT_ID="$OPTARG" ;;
    f) JSON_FILE="$OPTARG" ;;
    e) BASE_URL="$OPTARG" ;;
    c) CLIENT_ID="$OPTARG" ;;
    s) CLIENT_SECRET="$OPTARG" ;;
    *) usage ;;
  esac
done

# =========================
# 🚨 Required check
# =========================
if [ -z "$USERNAME" ] || [ -z "$PASSWORD" ] || [ -z "$TENANT_ID" ] || [ -z "$JSON_FILE" ]; then
  usage
fi

if [ ! -f "$JSON_FILE" ]; then
  echo "❌ IdentityVerification config file not found: $JSON_FILE"
  exit 1
fi

# =========================
# 🔐 Fetch access token (ROPC)
# =========================
echo "🎫 Fetching access token..."

TOKEN=$(curl -s -X POST "${BASE_URL}/${TENANT_ID}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=${CLIENT_ID}" \
  -d "client_secret=${CLIENT_SECRET}" \
  -d "username=${USERNAME}" \
  -d "password=${PASSWORD}" \
  -d "scope=openid" | jq -r .access_token)

if [ "$TOKEN" == "null" ] || [ -z "$TOKEN" ]; then
  echo "❌ Failed to retrieve access token"
  exit 1
fi

echo "✅ Access token acquired"

# =========================
# 📦 IdentityVerification Configuration registration
# =========================
echo "🚀 Registering IdentityVerification Configuration using file: $JSON_FILE"

RESPONSE=$(curl -s -w "\nHTTP_CODE:%{http_code}" -X POST "${BASE_URL}/v1/management/tenants/${TENANT_ID}/identity-verification-configurations" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  --data "@${JSON_FILE}")

# =========================
# 📣 Result
# =========================
BODY=$(echo "$RESPONSE" | sed -n '1,/HTTP_CODE:/p' | sed '$d')
HTTP_CODE=$(echo "$RESPONSE" | grep HTTP_CODE | cut -d: -f2)

echo "📡 HTTP ${HTTP_CODE}"
echo "$BODY"

if [ "$HTTP_CODE" != "201" ]; then
  echo "❌ IdentityVerification Configuration registration failed"
  exit 1
else
  echo "✅ IdentityVerification Configuration successfully registered"
fi
