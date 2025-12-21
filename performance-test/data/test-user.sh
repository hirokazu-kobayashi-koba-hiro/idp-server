#!/bin/bash

# =============================================================================
# Test User Data Generator for Performance Tests
# =============================================================================
# Generates JSON files for different login_hint patterns used in CIBA tests.
#
# Usage:
#   ./test-user.sh [type]
#
# Types:
#   device  - Generate device_id data (default)
#   sub     - Generate subject (user_sub) data
#   email   - Generate email data
#   phone   - Generate phone data
#   ex-sub  - Generate external subject data
#   all     - Generate all types
# =============================================================================

INPUT_FILE_USERS="./performance-test/data/generated_users_100k.tsv"
INPUT_FILE_DEVICES="./performance-test/data/generated_user_devices_100k.tsv"
OUTPUT_DIR="./performance-test/data"
LIMIT=500

# Each pattern uses different user range to avoid overlap
# device: lines 1-500, sub: 501-1000, email: 1001-1500, phone: 1501-2000, ex-sub: 2001-2500
OFFSET_DEVICE=0
OFFSET_SUB=500
OFFSET_EMAIL=1000
OFFSET_PHONE=1500
OFFSET_EX_SUB=2000

TYPE="${1:-device}"

generate_device() {
  local output_file="${OUTPUT_DIR}/performance-test-user-device.json"
  echo "[" > "$output_file"

  local count=0
  local skip=$OFFSET_DEVICE
  local first=1

  while IFS=$'\t' read -r DEVICE_ID TENANT_ID USER_ID OS MODEL PLATFORM LOCALE APP_NAME PRIORITY AVAILABLE_METHODS NOTIFICATION_TOKEN NOTIFICATION_CHANNEL; do
    if [ "$skip" -gt 0 ]; then
      skip=$((skip - 1))
      continue
    fi
    if [ "$count" -ge "$LIMIT" ]; then
      break
    fi

    if [ "$first" -eq 1 ]; then
      first=0
    else
      echo "," >> "$output_file"
    fi

    echo "  { \"device_id\": \"$DEVICE_ID\", \"user_id\": \"$USER_ID\" }" >> "$output_file"
    count=$((count + 1))
  done < "$INPUT_FILE_DEVICES"

  echo "]" >> "$output_file"
  echo "✅ Device JSON generated: $output_file (users 1-500)"

  # Also create the default file for backward compatibility
  cp "$output_file" "${OUTPUT_DIR}/performance-test-user.json"
}

generate_sub() {
  local output_file="${OUTPUT_DIR}/performance-test-user-sub.json"
  local device_file="$INPUT_FILE_DEVICES"
  echo "[" > "$output_file"

  local count=0
  local skip=$OFFSET_SUB
  local first=1

  while IFS=$'\t' read -r USER_SUB TENANT_ID PROVIDER_ID USER_ID USER_ID_DUP EMAIL EMAIL_VERIFIED PHONE PHONE_VERIFIED PREFERRED_USERNAME STATUS DEVICES; do
    if [ "$skip" -gt 0 ]; then
      skip=$((skip - 1))
      continue
    fi
    if [ "$count" -ge "$LIMIT" ]; then
      break
    fi

    # Get device_id from devices file
    local line_num=$((OFFSET_SUB + count + 1))
    local DEVICE_ID=$(sed -n "${line_num}p" "$device_file" | cut -f1)

    if [ "$first" -eq 1 ]; then
      first=0
    else
      echo "," >> "$output_file"
    fi

    echo "  { \"sub\": \"$USER_SUB\", \"email\": \"$EMAIL\", \"device_id\": \"$DEVICE_ID\" }" >> "$output_file"
    count=$((count + 1))
  done < "$INPUT_FILE_USERS"

  echo "]" >> "$output_file"
  echo "✅ Subject JSON generated: $output_file (users 501-1000)"
}

generate_email() {
  local output_file="${OUTPUT_DIR}/performance-test-user-email.json"
  local device_file="$INPUT_FILE_DEVICES"
  echo "[" > "$output_file"

  local count=0
  local skip=$OFFSET_EMAIL
  local first=1

  while IFS=$'\t' read -r USER_SUB TENANT_ID PROVIDER_ID USER_ID USER_ID_DUP EMAIL EMAIL_VERIFIED PHONE PHONE_VERIFIED PREFERRED_USERNAME STATUS DEVICES; do
    if [ "$skip" -gt 0 ]; then
      skip=$((skip - 1))
      continue
    fi
    if [ "$count" -ge "$LIMIT" ]; then
      break
    fi

    # Get device_id from devices file
    local line_num=$((OFFSET_EMAIL + count + 1))
    local DEVICE_ID=$(sed -n "${line_num}p" "$device_file" | cut -f1)

    if [ "$first" -eq 1 ]; then
      first=0
    else
      echo "," >> "$output_file"
    fi

    echo "  { \"email\": \"$EMAIL\", \"sub\": \"$USER_SUB\", \"device_id\": \"$DEVICE_ID\" }" >> "$output_file"
    count=$((count + 1))
  done < "$INPUT_FILE_USERS"

  echo "]" >> "$output_file"
  echo "✅ Email JSON generated: $output_file (users 1001-1500)"
}

generate_phone() {
  local output_file="${OUTPUT_DIR}/performance-test-user-phone.json"
  local device_file="$INPUT_FILE_DEVICES"
  echo "[" > "$output_file"

  local count=0
  local skip=$OFFSET_PHONE
  local first=1

  while IFS=$'\t' read -r USER_SUB TENANT_ID PROVIDER_ID USER_ID USER_ID_DUP EMAIL EMAIL_VERIFIED PHONE PHONE_VERIFIED PREFERRED_USERNAME STATUS DEVICES; do
    if [ "$skip" -gt 0 ]; then
      skip=$((skip - 1))
      continue
    fi
    if [ "$count" -ge "$LIMIT" ]; then
      break
    fi

    # Get device_id from devices file
    local line_num=$((OFFSET_PHONE + count + 1))
    local DEVICE_ID=$(sed -n "${line_num}p" "$device_file" | cut -f1)

    if [ "$first" -eq 1 ]; then
      first=0
    else
      echo "," >> "$output_file"
    fi

    echo "  { \"phone\": \"$PHONE\", \"sub\": \"$USER_SUB\", \"device_id\": \"$DEVICE_ID\" }" >> "$output_file"
    count=$((count + 1))
  done < "$INPUT_FILE_USERS"

  echo "]" >> "$output_file"
  echo "✅ Phone JSON generated: $output_file (users 1501-2000)"
}

generate_ex_sub() {
  local output_file="${OUTPUT_DIR}/performance-test-user-ex-sub.json"
  local device_file="$INPUT_FILE_DEVICES"
  echo "[" > "$output_file"

  local count=0
  local skip=$OFFSET_EX_SUB
  local first=1

  while IFS=$'\t' read -r USER_SUB TENANT_ID PROVIDER_ID USER_ID USER_ID_DUP EMAIL EMAIL_VERIFIED PHONE PHONE_VERIFIED PREFERRED_USERNAME STATUS DEVICES; do
    if [ "$skip" -gt 0 ]; then
      skip=$((skip - 1))
      continue
    fi
    if [ "$count" -ge "$LIMIT" ]; then
      break
    fi

    # Get device_id from devices file
    local line_num=$((OFFSET_EX_SUB + count + 1))
    local DEVICE_ID=$(sed -n "${line_num}p" "$device_file" | cut -f1)

    if [ "$first" -eq 1 ]; then
      first=0
    else
      echo "," >> "$output_file"
    fi

    echo "  { \"external_user_id\": \"$USER_ID\", \"provider_id\": \"$PROVIDER_ID\", \"sub\": \"$USER_SUB\", \"device_id\": \"$DEVICE_ID\" }" >> "$output_file"
    count=$((count + 1))
  done < "$INPUT_FILE_USERS"

  echo "]" >> "$output_file"
  echo "✅ External Subject JSON generated: $output_file (users 2001-2500)"
}

case "$TYPE" in
  device)
    generate_device
    ;;
  sub)
    generate_sub
    ;;
  email)
    generate_email
    ;;
  phone)
    generate_phone
    ;;
  ex-sub)
    generate_ex_sub
    ;;
  all)
    generate_device
    generate_sub
    generate_email
    generate_phone
    generate_ex_sub
    echo ""
    echo "✅ All test user JSON files generated!"
    ;;
  *)
    echo "Unknown type: $TYPE"
    echo "Usage: $0 [device|sub|email|phone|ex-sub|all]"
    exit 1
    ;;
esac
