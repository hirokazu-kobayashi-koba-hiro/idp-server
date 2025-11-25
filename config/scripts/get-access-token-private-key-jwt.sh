#!/bin/bash
set -e

# Usage:
# ./get-access-token-private-key-jwt.sh -u admin@example.com -p secret -t tenant-abc -e http://localhost:8080 -c client-id -k private-key.pem
# =========================
# 🧾 Usage
# =========================
usage() {
  echo "Usage: $0 -u <username> -p <password> -t <tenant_id> -c <client_id> -k <private_key_file> [-e <base_url>] [-a <algorithm>]"
  echo
  echo "Arguments:"
  echo "  -u   Username (resource owner)"
  echo "  -p   Password"
  echo "  -t   Tenant ID"
  echo "  -c   Client ID"
  echo "  -k   Private key file path (PEM format)"
  echo "  -e   Base URL of the IDP server (default: http://localhost:8080)"
  echo "  -a   JWT signing algorithm (default: ES256, options: RS256, ES256)"
  echo
  echo "Example:"
  echo "  $0 -u admin@example.com -p secret -t tenant-abc -c client-id -k private-key.pem -e http://localhost:8080"
  exit 1
}

BASE_URL="http://localhost:8080"
ALGORITHM="ES256"

while getopts ":u:p:t:e:c:k:a:" opt; do
  case $opt in
    u) USERNAME="$OPTARG" ;;
    p) PASSWORD="$OPTARG" ;;
    t) TENANT_ID="$OPTARG" ;;
    e) BASE_URL="$OPTARG" ;;
    c) CLIENT_ID="$OPTARG" ;;
    k) PRIVATE_KEY_FILE="$OPTARG" ;;
    a) ALGORITHM="$OPTARG" ;;
    *) usage ;;
  esac
done

# Validate required arguments
if [ -z "$USERNAME" ] || [ -z "$PASSWORD" ] || [ -z "$TENANT_ID" ] || [ -z "$CLIENT_ID" ] || [ -z "$PRIVATE_KEY_FILE" ]; then
  echo "❌ Error: Missing required arguments"
  usage
fi

if [ ! -f "$PRIVATE_KEY_FILE" ]; then
  echo "❌ Error: Private key file not found: $PRIVATE_KEY_FILE"
  exit 1
fi

# Check for required commands
if ! command -v openssl &> /dev/null; then
  echo "❌ Error: openssl command not found"
  exit 1
fi

if ! command -v base64 &> /dev/null; then
  echo "❌ Error: base64 command not found"
  exit 1
fi

# Base64 URL encoding function
base64url_encode() {
  openssl base64 -e -A | tr '+/' '-_' | tr -d '='
}

# Generate JWT Header
generate_jwt_header() {
  local alg=$1
  echo -n "{\"alg\":\"$alg\",\"typ\":\"JWT\"}" | base64url_encode
}

# Generate JWT Payload
generate_jwt_payload() {
  local iss=$1
  local sub=$2
  local aud=$3
  local exp=$4
  local iat=$5
  local jti=$6

  echo -n "{\"iss\":\"$iss\",\"sub\":\"$sub\",\"aud\":\"$aud\",\"exp\":$exp,\"iat\":$iat,\"jti\":\"$jti\"}" | base64url_encode
}

# Sign JWT
sign_jwt() {
  local header=$1
  local payload=$2
  local private_key_file=$3
  local algorithm=$4

  local signing_input="${header}.${payload}"

  if [ "$algorithm" = "ES256" ]; then
    # ES256 (ECDSA with SHA-256)
    echo -n "$signing_input" | openssl dgst -sha256 -sign "$private_key_file" | base64url_encode
  elif [ "$algorithm" = "RS256" ]; then
    # RS256 (RSA with SHA-256)
    echo -n "$signing_input" | openssl dgst -sha256 -sign "$private_key_file" | base64url_encode
  else
    echo "❌ Error: Unsupported algorithm: $algorithm"
    exit 1
  fi
}

# Generate JWT Client Assertion
IAT=$(date +%s)
EXP=$((IAT + 300))  # Valid for 5 minutes
JTI=$(cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 32 | head -n 1)
TOKEN_ENDPOINT="${BASE_URL}/${TENANT_ID}/v1/tokens"

HEADER=$(generate_jwt_header "$ALGORITHM")
PAYLOAD=$(generate_jwt_payload "$CLIENT_ID" "$CLIENT_ID" "$TOKEN_ENDPOINT" "$EXP" "$IAT" "$JTI")
SIGNATURE=$(sign_jwt "$HEADER" "$PAYLOAD" "$PRIVATE_KEY_FILE" "$ALGORITHM")

CLIENT_ASSERTION="${HEADER}.${PAYLOAD}.${SIGNATURE}"

# Request access token with private_key_jwt
TOKEN_RESPONSE=$(curl -s -X POST "$TOKEN_ENDPOINT" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=password" \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode "client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer" \
  --data-urlencode "client_assertion=${CLIENT_ASSERTION}" \
  --data-urlencode "username=${USERNAME}" \
  --data-urlencode "password=${PASSWORD}" \
  --data-urlencode "scope=openid management phone email address offline_access")

ACCESS_TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.access_token')

if [ "$ACCESS_TOKEN" = "null" ] || [ -z "$ACCESS_TOKEN" ]; then
  echo "❌ Failed to get access token" >&2
  echo "$TOKEN_RESPONSE" | jq >&2
  exit 1
fi

echo "$ACCESS_TOKEN"
