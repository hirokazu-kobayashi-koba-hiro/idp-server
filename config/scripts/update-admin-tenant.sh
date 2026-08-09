#!/bin/bash

# Applies config/templates/admin/initial.json to an ALREADY INITIALIZED admin tenant.
#
# setup.sh only bootstraps (POST /v1/admin/initialization) and is not idempotent, so edits to the
# admin template never reached an environment that had been set up once. Without this script the
# only way to change admin tenant settings was to call the management API by hand.
#
# The admin tenant is not reachable through the organization level management API, so every call
# here is system level (/v1/management/tenants/...). Bodies are taken from the generated template
# in full, never from a GET result: the authorization-server GET masks jwks as null and a
# GET -> edit -> PUT round trip would therefore drop the tenant signing keys.

set -e

usage() {
  echo "Usage: $0 [-b <base_url>] [-d true|false]"
  echo
  echo "  -b  Base URL of the IDP server (default: \$AUTHORIZATION_SERVER_URL)"
  echo "  -d  Dry run. true prints the diff without applying it (default: false)"
  exit 1
}

# read .env
set -a; [ -f .env ] && source .env; set +a

BASE_URL="${AUTHORIZATION_SERVER_URL}"
DRY_RUN="${DRY_RUN:-false}"

while getopts ":b:d:" opt; do
  case $opt in
    b) BASE_URL="$OPTARG" ;;
    d) DRY_RUN="$OPTARG" ;;
    *) usage ;;
  esac
done

echo "env: $ENV"
echo "url: $BASE_URL"

# regenerate from the template so that this always applies what is committed
./init-admin-tenant-config.sh > /dev/null

CONFIG_FILE="./config/generated/${ENV}/admin-tenant/initial.json"
[ ! -f "$CONFIG_FILE" ] && echo "❌ Generated config not found: $CONFIG_FILE" && exit 1

# Anything other than true/false is rejected rather than treated as false: a typo such as
# "-d ture" must not silently apply changes to the tenant that fronts every management API.
case "$DRY_RUN" in
  true)
    echo ""
    echo "DRY_RUN.........."
    echo ""
    DRY_RUN_PARM="?dry_run=true"
    ;;
  false)
    DRY_RUN_PARM="?dry_run=false"
    ;;
  *)
    echo "❌ -d must be true or false (got: '${DRY_RUN}')"
    usage
    ;;
esac

echo "get access token"
ACCESS_TOKEN=$(./config/scripts/get-access-token.sh \
  -u "$ADMIN_USER_EMAIL" \
  -p "$ADMIN_USER_PASSWORD" \
  -t "$ADMIN_TENANT_ID" \
  -e "$BASE_URL" \
  -c "$ADMIN_CLIENT_ID" \
  -s "$ADMIN_CLIENT_SECRET")

[ -z "$ACCESS_TOKEN" ] && echo "❌ Could not obtain an access token for the admin tenant" && exit 1

# put_resource <label> <path> <body>
put_resource() {
  LABEL="$1"
  RESOURCE_PATH="$2"
  REQUEST_BODY="$3"

  echo "-------------------------------------------------"
  echo "🔧 $LABEL"

  HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X GET \
    "${BASE_URL}/v1/management/${RESOURCE_PATH}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}")

  case "$HTTP_CODE" in
    200) ;;
    401)
      echo "❌ Unauthorized (HTTP 401). The access token was rejected for $LABEL."
      exit 1
      ;;
    403)
      echo "❌ Forbidden (HTTP 403). ${ADMIN_USER_EMAIL} lacks the permission to read $LABEL."
      exit 1
      ;;
    404)
      echo "❌ $LABEL not found (HTTP 404). Run setup.sh first to initialize the admin tenant."
      exit 1
      ;;
    *)
      echo "❌ Unexpected response from GET $LABEL: HTTP $HTTP_CODE"
      exit 1
      ;;
  esac

  RESPONSE=$(curl -s -w "\nHTTP_CODE:%{http_code}" -X PUT \
    "${BASE_URL}/v1/management/${RESOURCE_PATH}${DRY_RUN_PARM}" \
    -H "Authorization: Bearer ${ACCESS_TOKEN}" \
    -H "Content-Type: application/json" \
    --data "${REQUEST_BODY}")

  BODY=$(echo "$RESPONSE" | sed -n '1,/HTTP_CODE:/p' | sed '$d')
  FINAL_HTTP_CODE=$(echo "$RESPONSE" | grep HTTP_CODE | cut -d: -f2)

  echo "📡 HTTP $FINAL_HTTP_CODE"

  if [ "$FINAL_HTTP_CODE" != "200" ]; then
    echo "$BODY"
    echo "❌ $LABEL update failed"
    exit 1
  fi

  DIFF=$(echo "$BODY" | jq -r '.diff // empty')
  if [ -n "$DIFF" ] && [ "$DIFF" != "{}" ] && [ "$DIFF" != "null" ]; then
    echo "📝 Changes:"
    echo "$DIFF" | jq .
  else
    echo "📝 No changes detected"
  fi
  echo "✅ $LABEL successfully updated"
}

TENANT_ID=$(jq -r .tenant.id "$CONFIG_FILE")
CLIENT_ID=$(jq -r .client.client_id "$CONFIG_FILE")

# The template also carries "organization" and "user", which are deliberately left alone:
# the organization is created once at initialization, and re-applying "user" would reset the
# administrator password to the .env value. Drift in those two is therefore expected.

put_resource "tenant" \
  "tenants/${TENANT_ID}" \
  "$(jq -r .tenant "$CONFIG_FILE")"

put_resource "authorization-server" \
  "tenants/${TENANT_ID}/authorization-server" \
  "$(jq -r .authorization_server "$CONFIG_FILE")"

put_resource "client" \
  "tenants/${TENANT_ID}/clients/${CLIENT_ID}" \
  "$(jq -r .client "$CONFIG_FILE")"

echo "-------------------------------------------------"
echo "✅ admin tenant configuration applied"
