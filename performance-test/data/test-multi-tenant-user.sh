#!/bin/bash

# =============================================================================
# Multi-Tenant Test User Data Generator
# =============================================================================
# Generates JSON files for multi-tenant CIBA tests from generated TSV data.
# This script should be run after generate_multi_tenant_users.py
#
# Usage:
#   ./test-multi-tenant-user.sh
#
# Prerequisites:
#   - performance-test-tenant.json (created by register-tenants.sh)
#   - generated_multi_tenant_users.tsv (created by generate_multi_tenant_users.py)
#   - generated_multi_tenant_devices.tsv (created by generate_multi_tenant_users.py)
# =============================================================================

TENANT_FILE="./performance-test/data/performance-test-tenant.json"
INPUT_FILE_USERS="./performance-test/data/generated_multi_tenant_users.tsv"
INPUT_FILE_DEVICES="./performance-test/data/generated_multi_tenant_devices.tsv"
OUTPUT_DIR="./performance-test/data"
OUTPUT_FILE="${OUTPUT_DIR}/performance-test-multi-tenant-users.json"
USERS_PER_TENANT=500  # Number of test users per tenant

# Check prerequisites
if [ ! -f "$TENANT_FILE" ]; then
  echo "❌ Tenant file not found: $TENANT_FILE"
  echo "   Please run register-tenants.sh first."
  exit 1
fi

if [ ! -f "$INPUT_FILE_DEVICES" ]; then
  echo "❌ Device TSV not found: $INPUT_FILE_DEVICES"
  echo "   Please run generate_multi_tenant_users.py first."
  exit 1
fi

# Extract tenant IDs from JSON
echo "📋 Reading tenants from $TENANT_FILE..."
TENANT_IDS=$(python3 -c "
import json
with open('$TENANT_FILE') as f:
    tenants = json.load(f)
for t in tenants:
    print(t['tenantId'])
")

TENANT_COUNT=$(echo "$TENANT_IDS" | wc -l | tr -d ' ')
echo "   Found $TENANT_COUNT tenants"

# Start JSON output
echo "[" > "$OUTPUT_FILE"
first_tenant=1

# Process each tenant
tenant_index=0
while IFS= read -r TENANT_ID; do
  echo "📦 Processing tenant: $TENANT_ID"

  # Get client info from tenant file
  CLIENT_INFO=$(python3 -c "
import json
with open('$TENANT_FILE') as f:
    tenants = json.load(f)
for t in tenants:
    if t['tenantId'] == '$TENANT_ID':
        print(t['clientId'])
        print(t.get('clientSecret', 'clientSecretPostPassword1234567890123456789012345678901234567890123456789012345678901234567890'))
        break
")
  CLIENT_ID=$(echo "$CLIENT_INFO" | head -1)
  CLIENT_SECRET=$(echo "$CLIENT_INFO" | tail -1)

  if [ "$first_tenant" -eq 1 ]; then
    first_tenant=0
  else
    echo "," >> "$OUTPUT_FILE"
  fi

  # Start tenant object
  cat >> "$OUTPUT_FILE" << EOF
  {
    "tenantId": "$TENANT_ID",
    "clientId": "$CLIENT_ID",
    "clientSecret": "$CLIENT_SECRET",
    "users": [
EOF

  # Extract users for this tenant (first N users)
  first_user=1
  count=0

  while IFS=$'\t' read -r DEVICE_ID DEV_TENANT_ID USER_SUB OS MODEL PLATFORM LOCALE APP_NAME PRIORITY METHODS TOKEN CHANNEL; do
    if [ "$DEV_TENANT_ID" != "$TENANT_ID" ]; then
      continue
    fi

    if [ "$count" -ge "$USERS_PER_TENANT" ]; then
      break
    fi

    if [ "$first_user" -eq 1 ]; then
      first_user=0
    else
      echo "," >> "$OUTPUT_FILE"
    fi

    echo "      { \"device_id\": \"$DEVICE_ID\", \"user_id\": \"$USER_SUB\" }" >> "$OUTPUT_FILE"
    count=$((count + 1))
  done < "$INPUT_FILE_DEVICES"

  # Close users array and tenant object
  cat >> "$OUTPUT_FILE" << EOF

    ]
  }
EOF

  echo "   ✅ Added $count users"
  tenant_index=$((tenant_index + 1))

done <<< "$TENANT_IDS"

# Close JSON array
echo "]" >> "$OUTPUT_FILE"

echo ""
echo "✅ Multi-tenant test users JSON generated: $OUTPUT_FILE"
echo "   Tenants: $TENANT_COUNT"
echo "   Users per tenant: $USERS_PER_TENANT"
