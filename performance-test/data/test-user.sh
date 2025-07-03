#!/bin/bash

INPUT_FILE="generated_users_100k.tsv"
OUTPUT_FILE="performance-test-user.json"
LIMIT=500

echo "[" > "$OUTPUT_FILE"

count=0
first=1

while IFS=$'\t' read -r USER_SUB TENANT_ID PROVIDER_ID USER_ID USER_ID_DUP EMAIL EMAIL_VERIFIED PHONE PHONE_VERIFIED STATUS DEVICES; do
  if [ "$count" -ge "$LIMIT" ]; then
    break
  fi

  JSON_DEVICES=$(echo "$DEVICES" | sed 's/^"//;s/"$//;s/""/"/g')

  DEVICE_ID=$(echo "$JSON_DEVICES" | jq -r '.[0].id')

  if [ "$first" -eq 1 ]; then
    first=0
  else
    echo "," >> "$OUTPUT_FILE"
  fi

  echo "  { \"device_id\": \"$DEVICE_ID\", \"email\": \"$EMAIL\" }" >> "$OUTPUT_FILE"

  count=$((count + 1))
done < "$INPUT_FILE"

echo "]" >> "$OUTPUT_FILE"

echo "✅ JSON generated: $OUTPUT_FILE"
