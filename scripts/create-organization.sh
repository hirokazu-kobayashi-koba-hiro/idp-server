#!/bin/zsh
# Organization Initialization Script
# This script creates a new organization with tenant, admin user, and client configuration

set -e  # Exit on error

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
TEMP_DIR="$PROJECT_ROOT/config/tmp"

# Create temp directory if it doesn't exist
mkdir -p "$TEMP_DIR"

# Load environment variables
set -a; [ -f .env ] && source .env; set +a

# Color codes for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo "${BLUE}=== Organization Initialization ===${NC}"
echo

# Check required commands
for cmd in curl jq uuidgen; do
  if ! command -v $cmd &> /dev/null; then
    echo "${RED}❌ Error: $cmd is required but not installed${NC}"
    exit 1
  fi
done

# Verify required environment variables
echo "${YELLOW}🔐 Verifying credentials...${NC}"
if [ -z "$ADMIN_TENANT_ID" ]; then
  echo "${RED}❌ ADMIN_TENANT_ID not set in .env${NC}"
  exit 1
fi
if [ -z "$ADMIN_USERNAME" ]; then
  echo "${RED}❌ ADMIN_USERNAME not set in .env${NC}"
  exit 1
fi
if [ -z "$ADMIN_PASSWORD" ]; then
  echo "${RED}❌ ADMIN_PASSWORD not set in .env${NC}"
  exit 1
fi
if [ -z "$ADMIN_CLIENT_ID" ]; then
  echo "${RED}❌ ADMIN_CLIENT_ID not set in .env${NC}"
  exit 1
fi
if [ -z "$ADMIN_CLIENT_SECRET" ]; then
  echo "${RED}❌ ADMIN_CLIENT_SECRET not set in .env${NC}"
  exit 1
fi
echo "${GREEN}✅ Environment credentials verified${NC}"
echo

# Get admin access token
echo "${YELLOW}🔐 Obtaining admin access token...${NC}"
TOKEN_RESPONSE=$(curl -s -X POST "${IDP_SERVER_DOMAIN}${ADMIN_TENANT_ID}/v1/tokens" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "username=${ADMIN_USERNAME}" \
  -d "password=${ADMIN_PASSWORD}" \
  -d "client_id=${ADMIN_CLIENT_ID}" \
  -d "client_secret=${ADMIN_CLIENT_SECRET}" \
  -d "scope=management")

ACCESS_TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.access_token // empty')
if [ -z "$ACCESS_TOKEN" ]; then
  echo "${RED}❌ Failed to obtain access token${NC}"
  echo "$TOKEN_RESPONSE" | jq .
  exit 1
fi
echo "${GREEN}✅ Access token obtained${NC}"
echo

# Prompt for organization details
read "org_name?Organization Name [Test Organization]: "
org_name=${org_name:-Test Organization}

read "tenant_name?Tenant Name [Test Organizer Tenant]: "
tenant_name=${tenant_name:-Test Organizer Tenant}

read "admin_email?Admin Email [admin@test-org.com]: "
admin_email=${admin_email:-admin@test-org.com}

read "admin_username?Admin Username [org.admin]: "
admin_username=${admin_username:-org.admin}

read -s "admin_password?Admin Password [TestOrgPassword123!]: "
admin_password=${admin_password:-TestOrgPassword123!}
echo

read "client_name?Client Name [Test Organization Client]: "
client_name=${client_name:-Test Organization Client}

read "redirect_uri?Redirect URI [http://localhost:8081/callback]: "
redirect_uri=${redirect_uri:-http://localhost:8081/callback}

read "domain?Domain [http://localhost:8080]: "
domain=${domain:-http://localhost:8080}

echo
echo "${BLUE}--- Configuration Summary ---${NC}"
echo "Organization: ${org_name}"
echo "Tenant: ${tenant_name}"
echo "Admin Email: ${admin_email}"
echo "Admin Username: ${admin_username}"
echo "Client: ${client_name}"
echo "Domain: ${domain}"
echo

# Generate UUIDs
ORG_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
TENANT_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
USER_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
CLIENT_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
CLIENT_SECRET="test-org-secret-$(openssl rand -hex 16)"

echo "${BLUE}--- Generated IDs ---${NC}"
echo "Organization ID: ${ORG_ID}"
echo "Tenant ID: ${TENANT_ID}"
echo "User ID: ${USER_ID}"
echo "Client ID: ${CLIENT_ID}"
echo "Client Secret: ${CLIENT_SECRET}"
echo

# Generate JWKS (EC P-256 keypair for signing)
echo "${YELLOW}🔐 Generating JWKS keypair...${NC}"

# Generate EC private key
TEMP_KEY="$TEMP_DIR/ec-key-${ORG_ID}.pem"
TEMP_PUB="$TEMP_DIR/ec-pub-${ORG_ID}.pem"
openssl ecparam -name prime256v1 -genkey -noout -out "$TEMP_KEY"
openssl ec -in "$TEMP_KEY" -pubout -out "$TEMP_PUB" 2>/dev/null

# Convert to JWK format using Python
JWKS_CONTENT=$(python3 << PYTHON_SCRIPT
import json
import base64
import subprocess
import sys

# Extract key components using OpenSSL
def get_ec_params(pem_file):
    # Get private key (d)
    d_hex = subprocess.check_output(
        ['openssl', 'ec', '-in', pem_file, '-text', '-noout'],
        stderr=subprocess.DEVNULL
    ).decode()

    # Extract d (private key)
    d_section = False
    d_hex_str = ""
    for line in d_hex.split('\n'):
        if 'priv:' in line:
            d_section = True
            continue
        if d_section:
            if 'pub:' in line:
                break
            d_hex_str += line.strip().replace(':', '')

    # Get public key coordinates
    pub_hex = subprocess.check_output(
        ['openssl', 'ec', '-in', pem_file, '-pubout', '-text', '-noout'],
        stderr=subprocess.DEVNULL
    ).decode()

    # Extract x and y
    pub_section = False
    pub_hex_str = ""
    for line in pub_hex.split('\n'):
        if 'pub:' in line:
            pub_section = True
            continue
        if pub_section:
            if 'ASN1' in line or 'NIST' in line:
                break
            pub_hex_str += line.strip().replace(':', '')

    # Remove leading 04 (uncompressed point indicator)
    if pub_hex_str.startswith('04'):
        pub_hex_str = pub_hex_str[2:]

    # Split into x and y (each 32 bytes for P-256)
    x_hex = pub_hex_str[:64]
    y_hex = pub_hex_str[64:128]

    # Base64url encode
    def hex_to_b64url(hex_str):
        b = bytes.fromhex(hex_str)
        return base64.urlsafe_b64encode(b).decode().rstrip('=')

    return {
        'd': hex_to_b64url(d_hex_str),
        'x': hex_to_b64url(x_hex),
        'y': hex_to_b64url(y_hex)
    }

params = get_ec_params('$TEMP_KEY')

jwks = {
    "keys": [
        {
            "kty": "EC",
            "crv": "P-256",
            "x": params['x'],
            "y": params['y'],
            "d": params['d'],
            "use": "sig",
            "kid": "signing_key_1",
            "alg": "ES256"
        }
    ]
}

# Escape for JSON embedding
jwks_json = json.dumps(jwks, separators=(',', ':'))
escaped = json.dumps(jwks_json)[1:-1]
print(escaped)
PYTHON_SCRIPT
)

# Clean up temporary files
rm -f "$TEMP_KEY" "$TEMP_PUB"

TOKEN_SIGNING_KEY_ID="signing_key_1"
ID_TOKEN_SIGNING_KEY_ID="signing_key_1"

echo "${GREEN}✅ JWKS generated (Signing Key ID: $TOKEN_SIGNING_KEY_ID)${NC}"

# Load template
echo "${YELLOW}📄 Loading organization template...${NC}"
TEMPLATE_FILE="config/templates/organization-initialization-template.json"
if [ ! -f "$TEMPLATE_FILE" ]; then
  echo "${RED}❌ Template file not found: $TEMPLATE_FILE${NC}"
  exit 1
fi

# Export variables for envsubst
export ORGANIZATION_ID="$ORG_ID"
export ORGANIZATION_NAME="$org_name"
export ORGANIZATION_DESCRIPTION="Created on $(date '+%Y-%m-%d %H:%M:%S')"
export TENANT_ID
export TENANT_NAME="$tenant_name"
export BASE_URL="$domain"
export COOKIE_NAME="ORG_SESSION_${ORG_ID:0:8}"
export JWKS_CONTENT
export TOKEN_SIGNING_KEY_ID
export ID_TOKEN_SIGNING_KEY_ID
export USER_SUB="$USER_ID"
export USER_NAME="$admin_username"
export USER_EMAIL="$admin_email"
export USER_PASSWORD="$admin_password"
export CLIENT_ID
export CLIENT_ALIAS="${org_name// /-}-client"
export CLIENT_NAME="$client_name"
export CLIENT_SECRET
export REDIRECT_URI="$redirect_uri"

# Generate configuration from template
ORG_CONFIG_FILE="$TEMP_DIR/org-init-${ORG_ID}.json"
envsubst < "$TEMPLATE_FILE" > "$ORG_CONFIG_FILE"

echo "${YELLOW}📄 Configuration file created: $ORG_CONFIG_FILE${NC}"
echo

# Execute using onboarding script
echo "${BLUE}🔍 Executing Dry Run validation...${NC}"
./config/scripts/onboarding.sh -t "$ADMIN_TENANT_ID" -f "$ORG_CONFIG_FILE" -b "${IDP_SERVER_DOMAIN%/}" -a "$ACCESS_TOKEN" -d true

echo
echo "${GREEN}✅ Dry Run validation successful${NC}"
echo

# Confirm execution
read "confirm?${YELLOW}Execute organization initialization? (y/N): ${NC}"
if [[ ! $confirm =~ ^[Yy]$ ]]; then
  echo "${RED}❌ Cancelled${NC}"
  exit 0
fi

# Execute organization initialization
echo
echo "${BLUE}🚀 Creating organization...${NC}"
./config/scripts/onboarding.sh -t "$ADMIN_TENANT_ID" -f "$ORG_CONFIG_FILE" -b "${IDP_SERVER_DOMAIN%/}" -a "$ACCESS_TOKEN" -d false

echo
echo "${GREEN}✅ Organization created successfully!${NC}"
echo
echo "${BLUE}=== Login Information ===${NC}"
echo "Organization ID: ${GREEN}${ORG_ID}${NC}"
echo "Tenant ID: ${GREEN}${TENANT_ID}${NC}"
echo "Token Endpoint: ${GREEN}${domain}/${TENANT_ID}/v1/tokens${NC}"
echo
echo "Admin Credentials:"
echo "  Username: ${GREEN}${admin_username}${NC}"
echo "  Email: ${GREEN}${admin_email}${NC}"
echo "  Password: ${GREEN}${admin_password}${NC}"
echo
echo "Client Credentials:"
echo "  Client ID: ${GREEN}${CLIENT_ID}${NC}"
echo "  Client Secret: ${GREEN}${CLIENT_SECRET}${NC}"
echo
echo "${YELLOW}💾 Configuration saved to: $ORG_CONFIG_FILE${NC}"
echo "${YELLOW}🗑️  You can delete config/tmp/ directory when done${NC}"
