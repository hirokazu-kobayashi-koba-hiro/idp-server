#!/bin/bash
# =============================================================================
# Update Performance-Test Tenants' user_attribute_load_rule
# =============================================================================
# For each tenant in performance-test-tenant.json, sets:
#   identity_policy_config.user_attribute_load_rule = {
#     include_assigned_organizations: false,
#     include_assigned_tenants: false,
#     include_roles: false,
#     include_permissions: false
#   }
#
# Existing name / domain / other identity_policy_config fields are preserved
# via a GET-then-merge step.
#
# Usage:
#   ./update-tenant-load-rule.sh
# =============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
CONFIG_SCRIPTS_DIR="$PROJECT_ROOT/config/scripts"
TENANTS_FILE="$PROJECT_ROOT/performance-test/data/performance-test-tenant.json"
ENV_FILE="$PROJECT_ROOT/.env"

if [ -f "$ENV_FILE" ]; then
  echo "📂 Loading configuration from .env..."
  set -a
  source "$ENV_FILE"
  set +a
else
  echo "❌ .env file not found at $ENV_FILE"
  exit 1
fi

USERNAME="${ADMIN_USER_EMAIL:-}"
PASSWORD="${ADMIN_USER_PASSWORD:-}"
ADMIN_TENANT_ID="${ADMIN_TENANT_ID:-}"
ADMIN_CLIENT_ID="${ADMIN_CLIENT_ID:-}"
ADMIN_CLIENT_SECRET="${ADMIN_CLIENT_SECRET:-}"
BASE_URL="${AUTHORIZATION_SERVER_URL:-http://localhost:8080}"

[ -z "$USERNAME" ] && echo "❌ ADMIN_USER_EMAIL not set" && exit 1
[ -z "$PASSWORD" ] && echo "❌ ADMIN_USER_PASSWORD not set" && exit 1
[ -z "$ADMIN_TENANT_ID" ] && echo "❌ ADMIN_TENANT_ID not set" && exit 1
[ -z "$ADMIN_CLIENT_ID" ] && echo "❌ ADMIN_CLIENT_ID not set" && exit 1
[ -z "$ADMIN_CLIENT_SECRET" ] && echo "❌ ADMIN_CLIENT_SECRET not set" && exit 1
[ ! -f "$TENANTS_FILE" ] && echo "❌ Tenants file not found: $TENANTS_FILE" && exit 1

echo "=============================================="
echo "Update tenants' user_attribute_load_rule"
echo "=============================================="
echo "Base URL: $BASE_URL"
echo "Tenants file: $TENANTS_FILE"
echo ""

echo "🔑 Getting access token..."
ACCESS_TOKEN=$("$CONFIG_SCRIPTS_DIR/get-access-token.sh" \
  -u "$USERNAME" \
  -p "$PASSWORD" \
  -t "$ADMIN_TENANT_ID" \
  -e "$BASE_URL" \
  -c "$ADMIN_CLIENT_ID" \
  -s "$ADMIN_CLIENT_SECRET")

if [ -z "$ACCESS_TOKEN" ] || [ "$ACCESS_TOKEN" == "null" ]; then
  echo "❌ Failed to get access token"
  exit 1
fi
echo "✅ Access token obtained"
echo ""

TENANT_IDS=$(jq -r '.[].tenantId' "$TENANTS_FILE")
COUNT=$(echo "$TENANT_IDS" | wc -l | tr -d ' ')
echo "🚀 Updating $COUNT tenants..."
echo ""

i=0
for TENANT_ID in $TENANT_IDS; do
  i=$((i+1))
  echo "----------------------------------------------"
  echo "🔧 [$i/$COUNT] $TENANT_ID"

  GET_RESPONSE=$(curl -sS -w "\n%{http_code}" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    "$BASE_URL/v1/management/tenants/$TENANT_ID")
  GET_BODY=$(echo "$GET_RESPONSE" | sed '$d')
  GET_STATUS=$(echo "$GET_RESPONSE" | tail -n1)

  if [ "$GET_STATUS" != "200" ]; then
    echo "❌ GET failed (status $GET_STATUS): $GET_BODY"
    continue
  fi

  PUT_BODY=$(echo "$GET_BODY" | jq '{
    name: .name,
    domain: .domain,
    identity_policy_config: ((.identity_policy_config // {}) + {
      user_attribute_load_rule: ((.identity_policy_config.user_attribute_load_rule // {}) + {
        include_assigned_organizations: false,
        include_assigned_tenants: false,
        include_roles: false,
        include_permissions: false
      })
    })
  }')

  PUT_RESPONSE=$(curl -sS -w "\n%{http_code}" \
    -X PUT \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -H "Content-Type: application/json" \
    -d "$PUT_BODY" \
    "$BASE_URL/v1/management/tenants/$TENANT_ID")
  PUT_BODY_RESP=$(echo "$PUT_RESPONSE" | sed '$d')
  PUT_STATUS=$(echo "$PUT_RESPONSE" | tail -n1)

  if [ "$PUT_STATUS" != "200" ]; then
    echo "❌ PUT failed (status $PUT_STATUS): $PUT_BODY_RESP"
    continue
  fi

  VERIFY=$(echo "$PUT_BODY_RESP" | jq -c '.identity_policy_config.user_attribute_load_rule // .result.identity_policy_config.user_attribute_load_rule // "(not in response)"')
  echo "✅ Updated. user_attribute_load_rule = $VERIFY"
done

echo ""
echo "=============================================="
echo "✅ Done"
echo "=============================================="
