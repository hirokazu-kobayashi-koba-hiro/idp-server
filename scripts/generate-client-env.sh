#!/bin/zsh

echo "🔐 Generating secrets and configuration..."

# Generate admin client credentials
# Using base64url encoding without special characters (+, /, =)
# Alternative: Use alphanumeric characters only (a-zA-Z0-9)

# Option 1: base64url (URL-safe, no padding)
# CLIENT_SECRET=$(head -c 48 /dev/urandom | base64 | tr '+/' '-_' | tr -d '=')

# Option 2: Alphanumeric only (recommended for OAuth2 client secrets)
# Generate 64 characters from a-zA-Z0-9 (62 characters set)
# Read enough random data to ensure 64 alphanumeric characters
CLIENT_ID=$(uuidgen | tr A-Z a-z)
CLIENT_SECRET=$(LC_ALL=C tr -dc 'a-zA-Z0-9' < /dev/urandom | head -c 64)

echo "export CLIENT_ID='${CLIENT_ID}'"
echo "export CLIENT_SECRET='${CLIENT_SECRET}'"