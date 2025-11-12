#!/bin/bash

echo "🔍 Google OAuth 2.0 Content-Type Validation Test"
echo "Testing how Google handles invalid Content-Type headers"
echo ""

echo "================================================================================"
echo "Test 1: Google Token Endpoint - application/json (Invalid)"
echo "================================================================================"
curl -v -X POST https://oauth2.googleapis.com/token \
  -H "Content-Type: application/json" \
  -d '{
    "grant_type": "authorization_code",
    "code": "dummy_code",
    "client_id": "dummy_client_id",
    "client_secret": "dummy_client_secret",
    "redirect_uri": "https://example.com/callback"
  }'
echo -e "\n"

echo "================================================================================"
echo "Test 2: Google Token Endpoint - application/x-www-form-urlencoded (Valid)"
echo "================================================================================"
curl -v -X POST https://oauth2.googleapis.com/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code&code=dummy_code&client_id=dummy_client_id&client_secret=dummy_client_secret&redirect_uri=https://example.com/callback"
echo -e "\n"

echo "================================================================================"
echo "Test 3: Google Revocation Endpoint - application/json (Invalid)"
echo "================================================================================"
curl -v -X POST https://oauth2.googleapis.com/revoke \
  -H "Content-Type: application/json" \
  -d '{
    "token": "dummy_token"
  }'
echo -e "\n"

echo "================================================================================"
echo "Test 4: Google Revocation Endpoint - application/x-www-form-urlencoded (Valid)"
echo "================================================================================"
curl -v -X POST https://oauth2.googleapis.com/revoke \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "token=dummy_token"
echo -e "\n"

echo "================================================================================"
echo "Test 5: Google Token Introspection - application/json (Invalid)"
echo "================================================================================"
curl -v -X POST https://oauth2.googleapis.com/tokeninfo \
  -H "Content-Type: application/json" \
  -d '{
    "access_token": "dummy_token"
  }'
echo -e "\n"

echo "================================================================================"
echo "✅ Test completed"
echo "================================================================================"
