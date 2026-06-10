#!/bin/bash
set -e

# Financial-Grade - Client Certificate Generation Script
# Generates client certificates signed by the development CA for mTLS authentication.
# The CA (docker/nginx/ca.crt + ca.key) is already trusted by nginx's client-ca-bundle.pem,
# so certificates signed by this CA are automatically accepted without modifying the bundle.
#
# Usage:
#   ./generate-certs.sh
#   CERT_SUBJECT_DN="CN=my-app,O=My Org,C=US" ./generate-certs.sh

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../../.." && pwd)"

ORGANIZATION_NAME="${ORGANIZATION_NAME:-financial-grade}"
CERT_SUBJECT_DN="${CERT_SUBJECT_DN:-C=JP,O=Financial Institution,CN=financial-app}"
CERT_SAN_DNS="${CERT_SAN_DNS:-financial-app.example.com}"
OUTPUT_DIR="${PROJECT_ROOT}/config/generated/${ORGANIZATION_NAME}/certs"

# Development CA (used by nginx for mTLS verification)
CA_CERT="${PROJECT_ROOT}/docker/nginx/ca.crt"
CA_KEY="${PROJECT_ROOT}/docker/nginx/ca.key"

echo "=========================================="
echo "Client Certificate Generation (CA-signed)"
echo "=========================================="
echo ""
echo "  Subject DN: ${CERT_SUBJECT_DN}"
echo "  SAN DNS:    ${CERT_SAN_DNS}"
echo "  CA:         ${CA_CERT}"
echo "  Output:     ${OUTPUT_DIR}"
echo ""

if [ ! -f "${CA_CERT}" ] || [ ! -f "${CA_KEY}" ]; then
  echo "Error: CA certificate or key not found"
  echo "  Expected: ${CA_CERT}"
  echo "  Expected: ${CA_KEY}"
  exit 1
fi

mkdir -p "${OUTPUT_DIR}"

# Generate EC key pair (P-256)
echo "Step 1: Generating EC key pair (P-256)..."
openssl ecparam -genkey -name prime256v1 -noout -out "${OUTPUT_DIR}/client-key.pem" 2>/dev/null
echo "  Saved: ${OUTPUT_DIR}/client-key.pem"
echo ""

# Generate CSR (Certificate Signing Request)
# Convert RFC 2253 format (comma-separated) to openssl -subj format (slash-separated)
# e.g. "CN=financial-app,O=Financial Institution,C=JP" → "/CN=financial-app/O=Financial Institution/C=JP"
OPENSSL_SUBJ=$(echo "/${CERT_SUBJECT_DN}" | sed 's/,/\//g')
echo "Step 2: Generating CSR..."
echo "  OpenSSL Subject: ${OPENSSL_SUBJ}"
openssl req -new \
  -key "${OUTPUT_DIR}/client-key.pem" \
  -out "${OUTPUT_DIR}/client.csr" \
  -subj "${OPENSSL_SUBJ}" 2>/dev/null
echo "  Saved: ${OUTPUT_DIR}/client.csr"
echo ""

# Sign with development CA
echo "Step 3: Signing with development CA..."
openssl x509 -req -days 365 \
  -in "${OUTPUT_DIR}/client.csr" \
  -CA "${CA_CERT}" \
  -CAkey "${CA_KEY}" \
  -CAcreateserial \
  -out "${OUTPUT_DIR}/client-cert.pem" \
  -extfile <(echo "subjectAltName=DNS:${CERT_SAN_DNS}") 2>/dev/null
echo "  Saved: ${OUTPUT_DIR}/client-cert.pem"
echo ""

# Convert to DER format
echo "Step 4: Converting to DER format..."
openssl x509 -in "${OUTPUT_DIR}/client-cert.pem" -outform DER -out "${OUTPUT_DIR}/client-cert.der" 2>/dev/null
echo "  Saved: ${OUTPUT_DIR}/client-cert.der"
echo ""

# Generate PKCS#12 bundle (for browser import)
echo "Step 5: Generating PKCS#12 bundle..."
openssl pkcs12 -export \
  -in "${OUTPUT_DIR}/client-cert.pem" \
  -inkey "${OUTPUT_DIR}/client-key.pem" \
  -out "${OUTPUT_DIR}/client.p12" \
  -passout pass: 2>/dev/null
echo "  Saved: ${OUTPUT_DIR}/client.p12 (empty password)"
echo ""

# Clean up CSR
rm -f "${OUTPUT_DIR}/client.csr"

# Show certificate details
echo "=========================================="
echo "Certificate Details"
echo "=========================================="
echo ""

echo "Issuer (CA):"
openssl x509 -in "${OUTPUT_DIR}/client-cert.pem" -issuer -noout
echo ""

echo "Subject:"
openssl x509 -in "${OUTPUT_DIR}/client-cert.pem" -subject -noout
echo ""

echo "Fingerprint (SHA-256):"
openssl x509 -in "${OUTPUT_DIR}/client-cert.pem" -fingerprint -sha256 -noout
echo ""

echo "Validity:"
openssl x509 -in "${OUTPUT_DIR}/client-cert.pem" -dates -noout
echo ""

echo "=========================================="
echo "Certificate Generation Complete"
echo "=========================================="
echo ""
echo "Files generated:"
echo "  ${OUTPUT_DIR}/client-key.pem   - Private key (EC P-256)"
echo "  ${OUTPUT_DIR}/client-cert.pem  - Certificate (PEM, CA-signed)"
echo "  ${OUTPUT_DIR}/client-cert.der  - Certificate (DER)"
echo "  ${OUTPUT_DIR}/client.p12       - PKCS#12 bundle (empty password)"
echo ""
echo "Usage with curl:"
echo "  curl --cert ${OUTPUT_DIR}/client-cert.pem --key ${OUTPUT_DIR}/client-key.pem <url>"
echo ""
