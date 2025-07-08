#!/bin/bash

OUTPUT_FILE="./performance-test/data/generated_users_100k.tsv"
TENANT_ID="67e7eae6-62b0-4500-9eff-87459f63fc66"
PROVIDER_ID="idp-server"
EMAIL_DOMAIN="example.com"

BUFFER=""

for i in $(seq 1 100000); do
  USER_SUB=$(uuidgen | tr 'A-Z' 'a-z')
  USER_ID="user_$(uuidgen | tr 'A-Z' 'a-z')"
  EMAIL="${USER_ID}@${EMAIL_DOMAIN}"
  PHONE="090$(printf "%08d" $i)"
  DEVICES='[{""id"": ""'"$USER_SUB"'"", ""os"": ""iOS 18.5"", ""model"": ""iPhone15"", ""platform"": ""iOS"", ""notification_token"": ""test token"", ""notification_channel"": ""fcm"", ""preferred_for_notification"": true}]'

  BUFFER+="${USER_SUB}\t${TENANT_ID}\t${PROVIDER_ID}\t${USER_ID}\t${USER_ID}\t${EMAIL}\ttrue\t${PHONE}\ttrue\tIDENTITY_VERIFIED\t\"${DEVICES}\"\n"

  if (( i % 1000 == 0 )); then
    TIMESTAMP=$(date "+%Y-%m-%d %H:%M:%S")
    echo "(${TIMESTAMP}) progress: ${i}"
    echo -e "$BUFFER" >> "$OUTPUT_FILE"
    BUFFER=""
  fi
done

if [ -n "$BUFFER" ]; then
  echo -e "$BUFFER" >> "$OUTPUT_FILE"
fi


echo "✅ TSV complete：$OUTPUT_FILE"
