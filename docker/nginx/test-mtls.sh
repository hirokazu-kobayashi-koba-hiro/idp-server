#!/bin/bash
#
# Simple mTLS Connection Test
#
# Usage: ./test-mtls.sh [tenant-id]
#

TENANT_ID="${1:-67e7eae6-62b0-4500-9eff-87459f63fc66}"
BASE_URL="https://localhost:8443"

echo "Testing mTLS connection to ${BASE_URL}/${TENANT_ID}/health"
echo ""

# Without client certificate (should fail)
echo "1. Without client certificate (expected: SSL error)"
curl -k "${BASE_URL}/${TENANT_ID}/health" 2>&1 | head -3
echo ""

# With client certificate (should succeed)
echo "2. With client certificate (expected: 200 OK)"
curl -k \
  --cert test-client.pem \
  --key test-client.key \
  "${BASE_URL}/${TENANT_ID}/health"
echo ""
