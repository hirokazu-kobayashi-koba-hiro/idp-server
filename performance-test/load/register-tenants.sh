#!/bin/bash

usage() {
  echo "Usage: $0 -e <env> -u <username> -p <password> -t <admin_tenant_id> -b <base_url> -c <client_id> -s <client_secret> -n <number_of_tenants> [-d <dry_run_flag>]"
  exit 1
}

while getopts ":e:u:p:t:b:c:s:n:d:" opt; do
  case $opt in
    e) ENV="$OPTARG" ;;
    u) USERNAME="$OPTARG" ;;
    p) PASSWORD="$OPTARG" ;;
    t) ADMIN_TENANT_ID="$OPTARG" ;;
    b) BASE_URL="$OPTARG" ;;
    c) ADMIN_CLIENT_ID="$OPTARG" ;;
    s) ADMIN_CLIENT_SECRET="$OPTARG" ;;
    n) NUM_TENANTS="$OPTARG" ;;
    d) DRY_RUN="$OPTARG" ;;
    *) usage ;;
  esac
done

[ -z "$NUM_TENANTS" ] && echo "❌ -n <number_of_tenants> は必須" && usage

echo "🚀 Start registering $NUM_TENANTS tenants..."

for ((i=1; i<=NUM_TENANTS; i++)); do
  TENANT_ID=$(uuidgen | tr 'A-Z' 'a-z')
  CLIENT_ID=$(uuidgen | tr 'A-Z' 'a-z')
  USER_ID=$(uuidgen | tr 'A-Z' 'a-z')
  echo "----------------------------------------------"
  echo "🆕 Registering tenant $i/$NUM_TENANTS: $TENANT_ID"

  ./config-templates/config-upsert.sh \
    -e "$ENV" \
    -u "$USERNAME" \
    -p "$PASSWORD" \
    -t "$ADMIN_TENANT_ID" \
    -b "$BASE_URL" \
    -c "$ADMIN_CLIENT_ID" \
    -s "$ADMIN_CLIENT_SECRET" \
    -n "$TENANT_ID" \
    -l "$CLIENT_ID" \
    -a "$USER_ID" \
    -d "$DRY_RUN"

  if [ $? -ne 0 ]; then
    echo "❌ Error while registering tenant $TENANT_ID"
    exit 1
  fi
done

echo "✅ All $NUM_TENANTS tenants registered successfully!"
