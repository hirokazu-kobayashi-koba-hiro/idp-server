#!/bin/bash
set -e

# Extract private key from JWKS JSON and convert to PEM format (pure bash/openssl)
# Usage: ./extract-private-key-from-jwks.sh -j jwks.json -o private-key.pem

usage() {
  echo "Usage: $0 -j <jwks_json_string> -o <output_pem_file> [-k <kid>]"
  echo
  echo "Arguments:"
  echo "  -j   JWKS JSON string (inline or from file)"
  echo "  -o   Output PEM file path"
  echo "  -k   Key ID to extract (optional, uses first key if not specified)"
  echo
  echo "Example:"
  echo "  $0 -j '{\"keys\":[...]}' -o private-key.pem"
  echo "  $0 -j \"\$(cat jwks.json)\" -o private-key.pem -k my_key_id"
  exit 1
}

while getopts ":j:o:k:" opt; do
  case $opt in
    j) JWKS_JSON="$OPTARG" ;;
    o) OUTPUT_FILE="$OPTARG" ;;
    k) KID="$OPTARG" ;;
    *) usage ;;
  esac
done

if [ -z "$JWKS_JSON" ] || [ -z "$OUTPUT_FILE" ]; then
  echo "❌ Error: Missing required arguments"
  usage
fi

# Check for required commands
if ! command -v jq &> /dev/null; then
  echo "❌ Error: jq command not found. Please install jq."
  exit 1
fi

if ! command -v openssl &> /dev/null; then
  echo "❌ Error: openssl command not found"
  exit 1
fi

# Extract key from JWKS
if [ -n "$KID" ]; then
  KEY_JSON=$(echo "$JWKS_JSON" | jq -c ".keys[] | select(.kid == \"$KID\")")
else
  KEY_JSON=$(echo "$JWKS_JSON" | jq -c '.keys[0]')
fi

if [ -z "$KEY_JSON" ] || [ "$KEY_JSON" = "null" ]; then
  echo "❌ Error: Failed to extract key from JWKS" >&2
  exit 1
fi

KTY=$(echo "$KEY_JSON" | jq -r '.kty')
CRV=$(echo "$KEY_JSON" | jq -r '.crv')
D=$(echo "$KEY_JSON" | jq -r '.d')
X=$(echo "$KEY_JSON" | jq -r '.x')
Y=$(echo "$KEY_JSON" | jq -r '.y')

if [ -z "$D" ] || [ "$D" = "null" ]; then
  echo "❌ Error: Private key component 'd' not found in JWKS" >&2
  exit 1
fi

# Base64url decode function
base64url_decode() {
  local input="$1"
  # Add padding
  local padded="$input"
  while [ $((${#padded} % 4)) -ne 0 ]; do
    padded="${padded}="
  done
  # Decode
  echo -n "$padded" | tr '_-' '/+' | base64 -d 2>/dev/null
}

if [ "$KTY" = "EC" ]; then
  # EC Key conversion
  if [ "$CRV" != "P-256" ]; then
    echo "❌ Error: Only P-256 curve is supported in this script" >&2
    exit 1
  fi

  # Decode components
  D_HEX=$(base64url_decode "$D" | xxd -p | tr -d '\n')
  X_HEX=$(base64url_decode "$X" | xxd -p | tr -d '\n')
  Y_HEX=$(base64url_decode "$Y" | xxd -p | tr -d '\n')

  # Create EC parameters (P-256 / secp256r1 / prime256v1)
  # DER format for EC private key
  cat > "$OUTPUT_FILE" <<EOF
-----BEGIN EC PRIVATE KEY-----
$(cat <<HEREDOC | xxd -r -p | base64
308187020100301306072a8648ce3d020106082a8648ce3d030107046d306b0201010420${D_HEX}a14403420004${X_HEX}${Y_HEX}
HEREDOC
)
-----END EC PRIVATE KEY-----
EOF

  # Convert to PKCS8 format
  TEMP_EC_KEY=$(mktemp)
  mv "$OUTPUT_FILE" "$TEMP_EC_KEY"
  openssl pkcs8 -topk8 -nocrypt -in "$TEMP_EC_KEY" -out "$OUTPUT_FILE" 2>/dev/null
  rm -f "$TEMP_EC_KEY"

  echo "✅ Successfully extracted EC private key to $OUTPUT_FILE" >&2

elif [ "$KTY" = "RSA" ]; then
  echo "❌ Error: RSA key extraction not yet implemented in pure bash" >&2
  echo "Please use Python version or implement RSA support" >&2
  exit 1
else
  echo "❌ Error: Unsupported key type: $KTY" >&2
  exit 1
fi
