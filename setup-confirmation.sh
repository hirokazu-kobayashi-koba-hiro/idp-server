#!/bin/zsh
# read .env
set -a; [ -f .env ] && source .env; set +a

echo "env: $ENV"
echo "url: $BASE_URL"


ACCESS_TOKEN=$(curl -s -X POST "${IDP_SERVER_DOMAIN}${ADMIN_TENANT_ID}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "username=$ADMIN_USERNAME" \
  -d "password=$ADMIN_PASSWORD" \
  -d "client_id=$ADMIN_CLIENT_ID" \
  -d "client_secret=$ADMIN_CLIENT_SECRET" \
  -d "scope=openid profile email management" | jq -r '.access_token')

echo "=== Tenants ==="
curl -X GET "${IDP_SERVER_DOMAIN}v1/management/tenants?limit=1" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -w "\ntime_total: %{time_total}s\n" -o /tmp/tenants.json -s
jq < /tmp/tenants.json

echo "=== Users ==="
curl -X GET "${IDP_SERVER_DOMAIN}v1/management/tenants/${ADMIN_TENANT_ID}/users?limit=1" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -w "\ntime_total: %{time_total}s\n" -o /tmp/users.json -s
jq < /tmp/users.json