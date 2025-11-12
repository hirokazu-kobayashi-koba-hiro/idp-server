#!/bin/bash

echo "🔍 Major IdP Content-Type Validation Research"
echo "Testing how major OAuth 2.0/OIDC providers handle invalid Content-Type"
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

test_endpoint() {
    local name=$1
    local url=$2
    local content_type=$3
    local data=$4

    echo -e "${BLUE}Testing: $name - Content-Type: $content_type${NC}"

    # Save response to temp file
    temp_file=$(mktemp)
    http_code=$(curl -s -w "%{http_code}" -X POST "$url" \
        -H "Content-Type: $content_type" \
        -d "$data" \
        -o "$temp_file" 2>&1 | tail -n 1)

    body=$(cat "$temp_file")
    rm -f "$temp_file"

    echo -e "  Status: ${YELLOW}$http_code${NC}"
    if [ -n "$body" ]; then
        echo "  Response: $body"
    else
        echo "  Response: (empty)"
    fi
    echo ""
}

echo "================================================================================"
echo "1. Google (oauth2.googleapis.com)"
echo "================================================================================"

# Google Token Endpoint
test_endpoint "Google Token (JSON)" \
    "https://oauth2.googleapis.com/token" \
    "application/json" \
    '{"grant_type":"authorization_code","code":"dummy","client_id":"dummy"}'

test_endpoint "Google Token (Form)" \
    "https://oauth2.googleapis.com/token" \
    "application/x-www-form-urlencoded" \
    "grant_type=authorization_code&code=dummy&client_id=dummy"

# Google Revocation Endpoint
test_endpoint "Google Revoke (JSON)" \
    "https://oauth2.googleapis.com/revoke" \
    "application/json" \
    '{"token":"dummy"}'

test_endpoint "Google Revoke (Form)" \
    "https://oauth2.googleapis.com/revoke" \
    "application/x-www-form-urlencoded" \
    "token=dummy"

echo "================================================================================"
echo "2. Microsoft Azure AD / Entra ID (login.microsoftonline.com)"
echo "================================================================================"

# Microsoft Token Endpoint (common endpoint)
test_endpoint "Microsoft Token (JSON)" \
    "https://login.microsoftonline.com/common/oauth2/v2.0/token" \
    "application/json" \
    '{"grant_type":"authorization_code","code":"dummy","client_id":"dummy"}'

test_endpoint "Microsoft Token (Form)" \
    "https://login.microsoftonline.com/common/oauth2/v2.0/token" \
    "application/x-www-form-urlencoded" \
    "grant_type=authorization_code&code=dummy&client_id=dummy"

echo "================================================================================"
echo "3. GitHub (github.com)"
echo "================================================================================"

# GitHub Token Endpoint
test_endpoint "GitHub Token (JSON)" \
    "https://github.com/login/oauth/access_token" \
    "application/json" \
    '{"grant_type":"authorization_code","code":"dummy","client_id":"dummy"}'

test_endpoint "GitHub Token (Form)" \
    "https://github.com/login/oauth/access_token" \
    "application/x-www-form-urlencoded" \
    "grant_type=authorization_code&code=dummy&client_id=dummy"

echo "================================================================================"
echo "4. Auth0 (example tenant - will fail but we can see error format)"
echo "================================================================================"

# Auth0 Token Endpoint (example tenant)
test_endpoint "Auth0 Token (JSON)" \
    "https://dev-example.auth0.com/oauth/token" \
    "application/json" \
    '{"grant_type":"authorization_code","code":"dummy","client_id":"dummy"}'

test_endpoint "Auth0 Token (Form)" \
    "https://dev-example.auth0.com/oauth/token" \
    "application/x-www-form-urlencoded" \
    "grant_type=authorization_code&code=dummy&client_id=dummy"

echo "================================================================================"
echo "5. Okta (example tenant - will fail but we can see error format)"
echo "================================================================================"

# Okta Token Endpoint (example tenant)
test_endpoint "Okta Token (JSON)" \
    "https://dev-12345678.okta.com/oauth2/default/v1/token" \
    "application/json" \
    '{"grant_type":"authorization_code","code":"dummy","client_id":"dummy"}'

test_endpoint "Okta Token (Form)" \
    "https://dev-12345678.okta.com/oauth2/default/v1/token" \
    "application/x-www-form-urlencoded" \
    "grant_type=authorization_code&code=dummy&client_id=dummy"

echo "================================================================================"
echo "6. Facebook (graph.facebook.com)"
echo "================================================================================"

# Facebook Token Endpoint
test_endpoint "Facebook Token (JSON)" \
    "https://graph.facebook.com/v18.0/oauth/access_token" \
    "application/json" \
    '{"grant_type":"authorization_code","code":"dummy","client_id":"dummy"}'

test_endpoint "Facebook Token (Form)" \
    "https://graph.facebook.com/v18.0/oauth/access_token" \
    "application/x-www-form-urlencoded" \
    "grant_type=authorization_code&code=dummy&client_id=dummy"

echo "================================================================================"
echo "7. Twitter/X (api.twitter.com)"
echo "================================================================================"

# Twitter Token Endpoint (OAuth 2.0)
test_endpoint "Twitter Token (JSON)" \
    "https://api.twitter.com/2/oauth2/token" \
    "application/json" \
    '{"grant_type":"authorization_code","code":"dummy","client_id":"dummy"}'

test_endpoint "Twitter Token (Form)" \
    "https://api.twitter.com/2/oauth2/token" \
    "application/x-www-form-urlencoded" \
    "grant_type=authorization_code&code=dummy&client_id=dummy"

echo "================================================================================"
echo "✅ Research completed"
echo "================================================================================"
echo ""
echo "Summary: Compare how different IdPs handle invalid Content-Type headers"
echo "Expected behavior per RFC 7009/6749: application/x-www-form-urlencoded only"
echo "Our implementation: Returns 415 Unsupported Media Type for non-compliant requests"

echo "================================================================================"
echo "8. Yahoo! JAPAN (auth.login.yahoo.co.jp)"
echo "================================================================================"

# Yahoo! JAPAN Token Endpoint
test_endpoint "Yahoo Token (JSON)" \
    "https://auth.login.yahoo.co.jp/yconnect/v2/token" \
    "application/json" \
    '{"grant_type":"authorization_code","code":"dummy","client_id":"dummy"}'

test_endpoint "Yahoo Token (Form)" \
    "https://auth.login.yahoo.co.jp/yconnect/v2/token" \
    "application/x-www-form-urlencoded" \
    "grant_type=authorization_code&code=dummy&client_id=dummy"

