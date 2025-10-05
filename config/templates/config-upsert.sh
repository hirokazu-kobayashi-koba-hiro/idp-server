#!/bin/bash

#admin

echo "get access token"
echo "-------------------------------------------------"
echo ""

while getopts ":e:u:p:t:b:c:s:n:l:a:d:" opt; do
  case $opt in
    e) ENV="$OPTARG" ;;
    u) USERNAME="$OPTARG" ;;
    p) PASSWORD="$OPTARG" ;;
    t) ADMIN_TENANT_ID="$OPTARG" ;;
    b) BASE_URL="$OPTARG" ;;
    c) ADMIN_CLIENT_ID="$OPTARG" ;;
    s) ADMIN_CLIENT_SECRET="$OPTARG" ;;
    n) TENANT_ID="$OPTARG" ;;
    l) CLIENT_ID="$OPTARG" ;;
    a) USER_ID="$OPTARG" ;;
    d) DRY_RUN="$OPTARG" ;;
    *) echo "Usage: $0 -u <env> -u <username> -p <password> -t <tenant_id> -b <base_url> -c <client_id> -s <client_secret> -d <dry_run> "&& exit 1 ;;
  esac
done

echo $USERNAME
echo $PASSWORD

ACCESS_TOKEN=$(./config-templates/get-access-token.sh -u "$USERNAME" -p "$PASSWORD" -t "$ADMIN_TENANT_ID" -e "$BASE_URL" -c "$ADMIN_CLIENT_ID" -s "$ADMIN_CLIENT_SECRET")

echo "$ACCESS_TOKEN"

# tenant

echo "----------------------------------------"
echo "tenant"
echo ""

usage() {
  echo "Usage: $0 -i <TENANT_ID> -b <BASE_URL> -t <ACCESS_TOKEN>"
  exit 1
}

TEMPLATE_PATH="./config-templates/tenant-template.json"
OUTPUT_DIR="./config-templates/${TENANT_ID}"
OUTPUT_FILE="${OUTPUT_DIR}/tenant.json"

mkdir -p "$OUTPUT_DIR"

echo "$OUTPUT_FILE"

echo "📄 Generating config for $TENANT_ID"

export TENANT_ID BASE_URL

envsubst < "$TEMPLATE_PATH" > "$OUTPUT_FILE"

echo "📤 Registering tenant $TENANT_ID"

./config-templates/upsert-tenant.sh \
  -f "./config-templates/${TENANT_ID}/tenant.json" \
  -b "${BASE_URL}" \
  -a "${ACCESS_TOKEN}" \
  -d "${DRY_RUN}"


CLIENT_TEMPLATE_PATH="./config-templates/clientSecretPost-template.json"
CLIENT_OUTPUT_FILE="${OUTPUT_DIR}/clientSecretPost.json"

mkdir -p "$OUTPUT_DIR"

echo "$CLIENT_OUTPUT_FILE"

echo "📄 Generating client config for $CLIENT_ID"

export CLIENT_ID BASE_URL

envsubst < "$CLIENT_TEMPLATE_PATH" > "$CLIENT_OUTPUT_FILE"

echo "📤 Registering client $CLIENT_ID"

./config-templates/upsert-client.sh \
  -t "${TENANT_ID}" \
  -f "./config-templates/${TENANT_ID}/clientSecretPost.json" \
  -b "${BASE_URL}" \
  -a "${ACCESS_TOKEN}" \
  -d "${DRY_RUN}"

  USER_TEMPLATE_PATH="./config-templates/user-template.json"
  USER_OUTPUT_FILE="${OUTPUT_DIR}/user.json"

  mkdir -p "$OUTPUT_DIR"

  echo "$USER_OUTPUT_FILE"

  echo "📄 Generating user config for $USER_ID"

  export USER_ID BASE_URL

  envsubst < "$USER_TEMPLATE_PATH" > "$USER_OUTPUT_FILE"

  echo "📤 Registering user $USER_ID"

  ./config-templates/upsert-user.sh \
    -t "${TENANT_ID}" \
    -f "./config-templates/${TENANT_ID}/user.json" \
    -b "${BASE_URL}" \
    -a "${ACCESS_TOKEN}" \
    -d "${DRY_RUN}"