#!/bin/bash
set -e

# Generate JWKS with x5c from certificate
# Usage: ./generate-jwks-from-certificate.sh -c client-cert.pem -k client-key.pem -i key-id

usage() {
  echo "Usage: $0 -c <cert_file> -k <key_file> -i <key_id> [-o <output_file>]"
  echo
  echo "Arguments:"
  echo "  -c   Certificate file (PEM format)"
  echo "  -k   Private key file (PEM format)"
  echo "  -i   Key ID (kid)"
  echo "  -o   Output file (default: stdout)"
  echo
  echo "Example:"
  echo "  $0 -c client-cert.pem -k client-key.pem -i financial_client_key"
  echo "  $0 -c client-cert.pem -k client-key.pem -i my_key -o jwks.json"
  exit 1
}

OUTPUT_FILE=""

while getopts ":c:k:i:o:" opt; do
  case $opt in
    c) CERT_FILE="$OPTARG" ;;
    k) KEY_FILE="$OPTARG" ;;
    i) KEY_ID="$OPTARG" ;;
    o) OUTPUT_FILE="$OPTARG" ;;
    *) usage ;;
  esac
done

if [ -z "$CERT_FILE" ] || [ -z "$KEY_FILE" ] || [ -z "$KEY_ID" ]; then
  echo "❌ Error: Missing required arguments"
  usage
fi

if [ ! -f "$CERT_FILE" ]; then
  echo "❌ Error: Certificate file not found: $CERT_FILE"
  exit 1
fi

if [ ! -f "$KEY_FILE" ]; then
  echo "❌ Error: Key file not found: $KEY_FILE"
  exit 1
fi

# Check for required commands
if ! command -v openssl &> /dev/null; then
  echo "❌ Error: openssl command not found"
  exit 1
fi

if ! command -v jq &> /dev/null; then
  echo "❌ Error: jq command not found"
  exit 1
fi

# Base64url encoding function
base64url_encode() {
  openssl base64 -e -A | tr '+/' '-_' | tr -d '='
}

# Extract x5c (certificate chain in DER format, base64 encoded)
X5C=$(openssl x509 -in "$CERT_FILE" -outform DER | base64 | tr -d '\n')

# Extract public key components from private key
# Get key type by trying EC first
if openssl ec -in "$KEY_FILE" -text -noout &>/dev/null; then
  KEY_TYPE="EC"
elif openssl rsa -in "$KEY_FILE" -text -noout &>/dev/null; then
  KEY_TYPE="RSA"
else
  echo "❌ Error: Unable to determine key type"
  exit 1
fi

if [ "$KEY_TYPE" = "EC" ]; then
  # EC Key
  # Extract curve
  CURVE=$(openssl ec -in "$KEY_FILE" -text -noout 2>/dev/null | grep "ASN1 OID" | awk '{print $NF}')

  if [ "$CURVE" = "prime256v1" ] || [ "$CURVE" = "P-256" ]; then
    CRV="P-256"
  elif [ "$CURVE" = "secp384r1" ] || [ "$CURVE" = "P-384" ]; then
    CRV="P-384"
  elif [ "$CURVE" = "secp521r1" ] || [ "$CURVE" = "P-521" ]; then
    CRV="P-521"
  else
    echo "❌ Error: Unsupported curve: $CURVE"
    exit 1
  fi

  # Extract x, y, d coordinates
  # Get public key point
  PUB_KEY=$(openssl ec -in "$KEY_FILE" -pubout -outform DER 2>/dev/null | tail -c 65)

  # First byte is 0x04 (uncompressed point), then 32 bytes x, then 32 bytes y
  X=$(echo -n "$PUB_KEY" | tail -c 64 | head -c 32 | base64url_encode)
  Y=$(echo -n "$PUB_KEY" | tail -c 32 | base64url_encode)

  # Extract private key d
  D=$(openssl ec -in "$KEY_FILE" -text -noout 2>/dev/null | grep "priv:" -A 3 | tail -n +2 | tr -d ' :\n' | xxd -r -p | base64url_encode)

  # Build JWKS
  JWKS=$(jq -n \
    --arg kty "EC" \
    --arg crv "$CRV" \
    --arg x "$X" \
    --arg y "$Y" \
    --arg d "$D" \
    --arg kid "$KEY_ID" \
    --arg alg "ES256" \
    --arg use "sig" \
    --arg x5c "$X5C" \
    '{
      keys: [{
        kty: $kty,
        crv: $crv,
        x: $x,
        y: $y,
        d: $d,
        use: $use,
        kid: $kid,
        alg: $alg,
        x5c: [$x5c]
      }]
    }')

else
  echo "❌ Error: Only EC keys are supported in this version"
  exit 1
fi

# Output
if [ -n "$OUTPUT_FILE" ]; then
  echo "$JWKS" > "$OUTPUT_FILE"
  echo "✅ JWKS written to $OUTPUT_FILE" >&2
else
  echo "$JWKS"
fi
