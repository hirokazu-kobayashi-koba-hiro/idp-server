#!/bin/bash
set -e

# Generate self-signed client certificate for MTLS
# Usage: ./generate-client-certificate.sh -c <client_id> -o <output_dir>

usage() {
  echo "Usage: $0 -c <client_id> -o <output_dir> [-s <subject>] [-d <days>]"
  echo
  echo "Arguments:"
  echo "  -c   Client ID (used in certificate CN)"
  echo "  -o   Output directory for certificate files"
  echo "  -s   Certificate subject (default: CN=<client_id>,O=Financial Institution,C=JP)"
  echo "  -d   Certificate validity in days (default: 365)"
  echo
  echo "Example:"
  echo "  $0 -c financial-web-app -o ./certs"
  echo
  echo "Output files:"
  echo "  - client-cert.pem      : Client certificate (public)"
  echo "  - client-key.pem       : Private key"
  echo "  - client-cert.der      : Certificate in DER format"
  echo "  - client-cert-info.txt : Certificate information"
  exit 1
}

DAYS=365

while getopts ":c:o:s:d:" opt; do
  case $opt in
    c) CLIENT_ID="$OPTARG" ;;
    o) OUTPUT_DIR="$OPTARG" ;;
    s) SUBJECT="$OPTARG" ;;
    d) DAYS="$OPTARG" ;;
    *) usage ;;
  esac
done

if [ -z "$CLIENT_ID" ] || [ -z "$OUTPUT_DIR" ]; then
  echo "❌ Error: Missing required arguments"
  usage
fi

# Default subject if not provided
if [ -z "$SUBJECT" ]; then
  SUBJECT="CN=${CLIENT_ID},O=Financial Institution,C=JP"
fi

# Create output directory
mkdir -p "$OUTPUT_DIR"

CERT_FILE="${OUTPUT_DIR}/client-cert.pem"
KEY_FILE="${OUTPUT_DIR}/client-key.pem"
DER_FILE="${OUTPUT_DIR}/client-cert.der"
INFO_FILE="${OUTPUT_DIR}/client-cert-info.txt"

echo "=========================================="
echo "🔐 Generating Client Certificate for MTLS"
echo "=========================================="
echo ""
echo "📋 Configuration:"
echo "   Client ID: ${CLIENT_ID}"
echo "   Subject:   ${SUBJECT}"
echo "   Validity:  ${DAYS} days"
echo "   Output:    ${OUTPUT_DIR}"
echo ""

# Step 1: Generate private key
echo "🔑 Step 1: Generating EC private key (P-256)..."
openssl ecparam -name prime256v1 -genkey -noout -out "${KEY_FILE}"

if [ ! -f "${KEY_FILE}" ]; then
  echo "❌ Error: Failed to generate private key"
  exit 1
fi

echo "✅ Private key generated: ${KEY_FILE}"
echo ""

# Step 2: Generate self-signed certificate
echo "📜 Step 2: Generating self-signed certificate..."
openssl req -new -x509 -key "${KEY_FILE}" -out "${CERT_FILE}" -days "${DAYS}" \
  -subj "/${SUBJECT}" \
  -addext "subjectAltName=DNS:${CLIENT_ID}.example.com,URI:https://${CLIENT_ID}.example.com"

if [ ! -f "${CERT_FILE}" ]; then
  echo "❌ Error: Failed to generate certificate"
  exit 1
fi

echo "✅ Certificate generated: ${CERT_FILE}"
echo ""

# Step 3: Convert to DER format
echo "🔄 Step 3: Converting to DER format..."
openssl x509 -in "${CERT_FILE}" -outform DER -out "${DER_FILE}"

if [ ! -f "${DER_FILE}" ]; then
  echo "❌ Error: Failed to convert to DER format"
  exit 1
fi

echo "✅ DER certificate generated: ${DER_FILE}"
echo ""

# Step 4: Extract certificate information
echo "📊 Step 4: Extracting certificate information..."
openssl x509 -in "${CERT_FILE}" -text -noout > "${INFO_FILE}"

echo "✅ Certificate info saved: ${INFO_FILE}"
echo ""

# Step 5: Display certificate details
echo "=========================================="
echo "✅ Certificate Generation Complete"
echo "=========================================="
echo ""
echo "📁 Generated Files:"
echo "   Certificate (PEM):  ${CERT_FILE}"
echo "   Private Key (PEM):  ${KEY_FILE}"
echo "   Certificate (DER):  ${DER_FILE}"
echo "   Certificate Info:   ${INFO_FILE}"
echo ""
echo "📋 Certificate Details:"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Extract key information
SERIAL=$(openssl x509 -in "${CERT_FILE}" -serial -noout | cut -d= -f2)
NOT_BEFORE=$(openssl x509 -in "${CERT_FILE}" -startdate -noout | cut -d= -f2)
NOT_AFTER=$(openssl x509 -in "${CERT_FILE}" -enddate -noout | cut -d= -f2)
FINGERPRINT=$(openssl x509 -in "${CERT_FILE}" -fingerprint -sha256 -noout | cut -d= -f2)

echo "   Subject:        ${SUBJECT}"
echo "   Serial:         ${SERIAL}"
echo "   Valid From:     ${NOT_BEFORE}"
echo "   Valid Until:    ${NOT_AFTER}"
echo "   SHA256 FP:      ${FINGERPRINT}"
echo ""

echo "🔍 Next Steps:"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "1. Use this certificate for MTLS authentication:"
echo "   curl --cert ${CERT_FILE} --key ${KEY_FILE} https://..."
echo ""
echo "2. Register client with idp-server using:"
echo "   - token_endpoint_auth_method: self_signed_tls_client_auth"
echo "   - tls_client_certificate_bound_access_tokens: true"
echo ""
echo "3. The certificate fingerprint will be validated against the client's JWKS"
echo ""
