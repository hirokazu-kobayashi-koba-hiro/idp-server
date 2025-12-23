#!/bin/bash
# =============================================================================
# Performance Test Tenant Registration Script
# =============================================================================
# Registers multiple tenants for performance testing using the onboarding API.
# Each tenant is created with its own organization, authorization server,
# client, and initial user.
#
# Usage:
#   ./register-tenants.sh -n 5
#   ./register-tenants.sh -n 5 -d true   # dry run
#
# The script reads admin credentials from .env file in project root.
#
# Prerequisites:
#   - jq installed
#   - curl installed
#   - envsubst installed (part of gettext)
#   - .env file with admin credentials
# =============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
CONFIG_SCRIPTS_DIR="$PROJECT_ROOT/config/scripts"
TEMPLATE_FILE="$PROJECT_ROOT/performance-test/templates/onboarding-template.json"
OUTPUT_FILE="$SCRIPT_DIR/performance-test-tenant.json"
WORK_DIR="$SCRIPT_DIR/.tenant-work"
ENV_FILE="$PROJECT_ROOT/.env"

# =============================================================================
# Load .env file
# =============================================================================
if [ -f "$ENV_FILE" ]; then
  echo "📂 Loading configuration from .env..."
  set -a
  source "$ENV_FILE"
  set +a
else
  echo "⚠️  .env file not found at $ENV_FILE"
fi

# Set defaults from .env
USERNAME="${ADMIN_USER_EMAIL:-}"
PASSWORD="${ADMIN_USER_PASSWORD:-}"
ADMIN_TENANT_ID_DEFAULT="${ADMIN_TENANT_ID:-}"
ADMIN_CLIENT_ID_DEFAULT="${ADMIN_CLIENT_ID:-}"
ADMIN_CLIENT_SECRET_DEFAULT="${ADMIN_CLIENT_SECRET:-}"
BASE_URL="${AUTHORIZATION_SERVER_URL:-http://localhost:8080}"

usage() {
  echo "Usage: $0 -n <number_of_tenants> [-d <dry_run_flag>]"
  echo ""
  echo "Parameters (optional - defaults loaded from .env):"
  echo "  -n   Number of tenants to register (required)"
  echo "  -d   Dry run flag (true/false, default: false)"
  echo "  -b   Base URL (default: from .env AUTHORIZATION_SERVER_URL or http://localhost:8080)"
  exit 1
}

while getopts ":n:d:b:" opt; do
  case $opt in
    n) NUM_TENANTS="$OPTARG" ;;
    d) DRY_RUN="$OPTARG" ;;
    b) BASE_URL="$OPTARG" ;;
    *) usage ;;
  esac
done

# Use defaults
ADMIN_TENANT_ID="$ADMIN_TENANT_ID_DEFAULT"
ADMIN_CLIENT_ID="$ADMIN_CLIENT_ID_DEFAULT"
ADMIN_CLIENT_SECRET="$ADMIN_CLIENT_SECRET_DEFAULT"

# Validate required parameters
[ -z "$NUM_TENANTS" ] && echo "❌ -n <number_of_tenants> is required" && usage
[ -z "$USERNAME" ] && echo "❌ Admin username not found in .env (ADMIN_USER_EMAIL)" && exit 1
[ -z "$PASSWORD" ] && echo "❌ Admin password not found in .env (ADMIN_USER_PASSWORD)" && exit 1
[ -z "$ADMIN_TENANT_ID" ] && echo "❌ Admin tenant ID not found in .env (ADMIN_TENANT_ID)" && exit 1
[ -z "$ADMIN_CLIENT_ID" ] && echo "❌ Admin client ID not found in .env (ADMIN_CLIENT_ID)" && exit 1
[ -z "$ADMIN_CLIENT_SECRET" ] && echo "❌ Admin client secret not found in .env (ADMIN_CLIENT_SECRET)" && exit 1
[ ! -f "$TEMPLATE_FILE" ] && echo "❌ Template file not found: $TEMPLATE_FILE" && exit 1

DRY_RUN="${DRY_RUN:-false}"

echo "=============================================="
echo "Performance Test Tenant Registration"
echo "=============================================="
echo "Base URL: $BASE_URL"
echo "Admin Tenant: $ADMIN_TENANT_ID"
echo "Tenants to register: $NUM_TENANTS"
echo "Dry run: $DRY_RUN"
echo "Template: $TEMPLATE_FILE"
echo ""

# =============================================================================
# Step 1: Get Access Token
# =============================================================================
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

# =============================================================================
# Step 2: Prepare work directory
# =============================================================================
mkdir -p "$WORK_DIR"

# =============================================================================
# Step 3: Register tenants via onboarding
# =============================================================================
echo "🚀 Start registering $NUM_TENANTS tenants..."
echo ""

echo "[" > "$OUTPUT_FILE"
first=1

for ((i=1; i<=NUM_TENANTS; i++)); do
  export ORGANIZATION_ID=$(uuidgen | tr 'A-Z' 'a-z')
  export TENANT_ID=$(uuidgen | tr 'A-Z' 'a-z')
  export USER_ID=$(uuidgen | tr 'A-Z' 'a-z')
  export DEVICE_ID=$(uuidgen | tr 'A-Z' 'a-z')
  export CLIENT_UUID=$(uuidgen | tr 'A-Z' 'a-z')
  export TENANT_INDEX=$i
  export BASE_URL

  echo "----------------------------------------------"
  echo "🆕 Registering tenant $i/$NUM_TENANTS"
  echo "   Organization: $ORGANIZATION_ID"
  echo "   Tenant: $TENANT_ID"

  # Create tenant-specific directory
  TENANT_WORK_DIR="$WORK_DIR/$TENANT_ID"
  mkdir -p "$TENANT_WORK_DIR"

  # Generate onboarding JSON from template
  envsubst < "$TEMPLATE_FILE" > "$TENANT_WORK_DIR/initial.json"

  # Call onboarding API
  "$CONFIG_SCRIPTS_DIR/onboarding.sh" \
    -t "$ADMIN_TENANT_ID" \
    -f "$TENANT_WORK_DIR/initial.json" \
    -b "$BASE_URL" \
    -a "$ACCESS_TOKEN" \
    -d "$DRY_RUN"

  # Add to output JSON
  if [ "$first" -eq 1 ]; then
    first=0
  else
    echo "," >> "$OUTPUT_FILE"
  fi

  cat >> "$OUTPUT_FILE" << EOF
  {
    "organizationId": "$ORGANIZATION_ID",
    "tenantId": "$TENANT_ID",
    "clientId": "clientSecretPost",
    "clientSecret": "clientSecretPostPassword1234567890123456789012345678901234567890123456789012345678901234567890",
    "userId": "$USER_ID",
    "deviceId": "$DEVICE_ID",
    "userEmail": "perf-test-${i}@example.com",
    "userPassword": "perfTestPassword001"
  }
EOF

  echo "✅ Tenant $i registered successfully"
  echo ""
done

echo "]" >> "$OUTPUT_FILE"

# Cleanup work directory
rm -rf "$WORK_DIR"

echo "=============================================="
echo "✅ All $NUM_TENANTS tenants registered successfully!"
echo "Output file: $OUTPUT_FILE"
echo "=============================================="
echo ""
echo "📝 Next steps:"
echo "   1. Generate multi-tenant user data:"
echo "      python3 ./performance-test/data/generate_multi_tenant_users.py"
echo "   2. Import data to PostgreSQL (see test execution guide)"
