#!/bin/zsh

echo "🔐 Generating secrets and configuration..."

# Generate secrets
IDP_SERVER_API_KEY=$(uuidgen | tr 'A-Z' 'a-z')
IDP_SERVER_API_SECRET=$(uuidgen | tr 'A-Z' 'a-z' | base64)
ENCRYPTION_KEY=$(head -c 32 /dev/urandom | base64)

# Generate secure database passwords
POSTGRES_PASSWORD=$(head -c 24 /dev/urandom | base64)
DB_OWNER_PASSWORD=$(head -c 24 /dev/urandom | base64)
IDP_DB_ADMIN_PASSWORD=$(head -c 24 /dev/urandom | base64)
IDP_DB_APP_PASSWORD=$(head -c 24 /dev/urandom | base64)

# Generate admin client credentials
ADMIN_USERNAME="administrator_$(date +%s)"
ADMIN_EMAIL="$ADMIN_USERNAME@mail.com"
ADMIN_PASSWORD=$(head -c 12 /dev/urandom | base64)
ADMIN_TENANT_ID=$(uuidgen | tr A-Z a-z)
ADMIN_CLIENT_ID=$(uuidgen | tr A-Z a-z)
ADMIN_CLIENT_ID_ALIAS="client_$(head -c 4 /dev/urandom | base64 | tr -dc 'a-zA-Z0-9' | head -c 8)"
ADMIN_CLIENT_SECRET=$(head -c 48 /dev/urandom | base64)

echo "Generated secrets:"
echo "  - API Key: $API_KEY"
echo "  - Encryption Key: ********"
echo "  - Admin Client ID: $ADMIN_CLIENT_ID"

# Create secrets directory
mkdir -p config/secrets/local

# Write encryption-keys.json
cat > config/secrets/local/encryption-keys.json <<EOF
{
  "api_key": "$API_KEY",
  "api_secret": "$API_SECRET",
  "encryption_key": "$ENCRYPTION_KEY"
}
EOF

# Write client-secrets.json
cat > config/secrets/local/client-secrets.json <<EOF
{
  "admin_client": {
    "client_id": "$ADMIN_CLIENT_ID",
    "client_id_alias": "$ADMIN_CLIENT_ID_ALIAS",
    "client_secret": "$ADMIN_CLIENT_SECRET"
  }
}
EOF

# Set proper permissions
chmod 600 config/secrets/local/*.json

echo "✅ Secrets saved to config/secrets/local/"

# Write .env file (references only, no hardcoded secrets)
cat > .env <<EOF
# Base Configuration
IDP_SERVER_DOMAIN=http://localhost:8080/
ENV=local
BASE_URL=http://localhost:8080
DRY_RUN=false

# API Authentication (for setup.sh and admin operations)
IDP_SERVER_API_KEY=$IDP_SERVER_API_KEY
IDP_SERVER_API_SECRET=$IDP_SERVER_API_SECRET
ENCRYPTION_KEY=$ENCRYPTION_KEY

# Secrets Configuration (Phase 2: Reference files instead of hardcoding)
SECRETS_DIR=./config/secrets/local
JWKS_FILE=\${SECRETS_DIR}/jwks.json
CLIENT_SECRETS_FILE=\${SECRETS_DIR}/client-secrets.json
ENCRYPTION_KEYS_FILE=\${SECRETS_DIR}/encryption-keys.json

# Admin User Configuration
ADMIN_USERNAME=$ADMIN_USERNAME
ADMIN_EMAIL=$ADMIN_EMAIL
ADMIN_PASSWORD=$ADMIN_PASSWORD
ADMIN_TENANT_ID=$ADMIN_TENANT_ID

# Admin Client Configuration (loaded from CLIENT_SECRETS_FILE)
ADMIN_CLIENT_ID=$ADMIN_CLIENT_ID
ADMIN_CLIENT_ID_ALIAS=$ADMIN_CLIENT_ID_ALIAS

# Database Configuration
# PostgreSQL superuser password
POSTGRES_PASSWORD=$POSTGRES_PASSWORD

# Database owner password (for migrations)
DB_OWNER_PASSWORD=$DB_OWNER_PASSWORD

# Admin user password (RLS bypass for management operations)
IDP_DB_ADMIN_PASSWORD=$IDP_DB_ADMIN_PASSWORD

# Application user password (RLS-compliant operations)
IDP_DB_APP_PASSWORD=$IDP_DB_APP_PASSWORD
EOF

echo "✅ .env file generated"
echo ""
echo "📋 Summary:"
echo "  - Secrets: config/secrets/local/encryption-keys.json, client-secrets.json"
echo "  - Environment: .env (references secrets, no hardcoded values)"
echo ""
echo "⚠️  IMPORTANT: Never commit config/secrets/ to git!"
echo ""
echo "Next steps:"
echo "  1. Generate JWKS: ./config/scripts/migrate-secrets.sh (if using template JWKS)"
echo "  2. Start services: docker-compose up -d"
echo "  3. Initialize: ./setup.sh"

echo "Admin Tenant env"
echo "export TENANT_ID='${ADMIN_TENANT_ID}'"
echo "export ADMIN_EMAIL='${ADMIN_EMAIL}'"
echo "export ADMIN_PASSWORD='${ADMIN_PASSWORD}'"
echo "export CLIENT_ID='${ADMIN_CLIENT_ID}'"
echo "export CLIENT_SECRET='${ADMIN_CLIENT_SECRET}'"