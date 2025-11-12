#!/bin/bash

# Mockoon Error Response Endpoint Test Script
# Tests that /e2e/error-responses returns correct status codes

MOCKOON_URL="http://localhost:4000/e2e/error-responses"

echo "========================================"
echo "Mockoon Error Response Endpoint Test"
echo "========================================"
echo ""

# Test function
test_status() {
  local status_code=$1
  local test_name=$2

  echo "🧪 Testing: $test_name (Expected: $status_code)"

  if [ -z "$status_code" ]; then
    # Default 200 test (no status parameter)
    response=$(curl -s -w "\n%{http_code}" -X POST \
      -H "Content-Type: application/json" \
      -d '{"test":"default_success"}' \
      "$MOCKOON_URL")
  else
    # Send status in request body (not query parameter)
    response=$(curl -s -w "\n%{http_code}" -X POST \
      -H "Content-Type: application/json" \
      -d '{"status":"'$status_code'","test":"error_'$status_code'"}' \
      "$MOCKOON_URL")
  fi

  # Extract status code (last line)
  actual_status=$(echo "$response" | tail -n 1)

  # Extract body (all lines except last)
  body=$(echo "$response" | sed '$d')

  if [ "$actual_status" = "$status_code" ]; then
    echo "✅ PASS: Received $actual_status"
    echo "   Response: $(echo "$body" | jq -c '.')"
  else
    echo "❌ FAIL: Expected $status_code, got $actual_status"
    echo "   Response: $body"
  fi

  echo ""
}

# Client Errors (4xx)
echo "📍 Client Errors (4xx)"
echo "----------------------------------------"
test_status "400" "Bad Request"
test_status "401" "Unauthorized"
test_status "403" "Forbidden - Insufficient Scope"
test_status "404" "Not Found"
test_status "408" "Request Timeout"
test_status "409" "Conflict - State Transition"
test_status "413" "Payload Too Large"
test_status "415" "Unsupported Media Type"
test_status "422" "Unprocessable Entity"
test_status "429" "Too Many Requests"

echo ""
echo "📍 Server Errors (5xx)"
echo "----------------------------------------"
test_status "500" "Internal Server Error"
test_status "502" "Bad Gateway"
test_status "503" "Service Unavailable"
test_status "504" "Gateway Timeout"

echo ""
echo "📍 Success Response"
echo "----------------------------------------"
test_status "200" "Success (Default)"

echo ""
echo "========================================"
echo "Test Complete"
echo "========================================"
