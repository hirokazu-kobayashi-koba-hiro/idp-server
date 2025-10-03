#!/bin/zsh

echo "create api key and secret"

API_KEY=$(uuidgen | tr 'A-Z' 'a-z')
API_SECRET=$(uuidgen | tr 'A-Z' 'a-z' | base64)
ENCRYPTION_KEY=$(head -c 32 /dev/urandom | base64)

# Generate secure database passwords
POSTGRES_PASSWORD=$(head -c 24 /dev/urandom | base64)
DB_OWNER_PASSWORD=$(head -c 24 /dev/urandom | base64)
IDP_ADMIN_PASSWORD=$(head -c 24 /dev/urandom | base64)
DB_APP_PASSWORD=$(head -c 24 /dev/urandom | base64)

echo "Generated API Key: $API_KEY"
echo "Generated API Secret: $API_SECRET"
echo "Generated ENCRYPTION_KEY: $ENCRYPTION_KEY"
echo "Generated Database Passwords..."

echo "Setting environment variables..."

# write to .env file
echo "IDP_SERVER_DOMAIN=http://localhost:8080/" > .env
echo "IDP_SERVER_API_KEY=$API_KEY" >> .env
echo "IDP_SERVER_API_SECRET=$API_SECRET" >> .env
echo "ENCRYPTION_KEY=$ENCRYPTION_KEY" >> .env
echo "ENV=local" >> .env


BASE_URL="http://localhost:8080"
ADMIN_USERNAME="administrator_$(date +%s)"
ADMIN_EMAIL="$ADMIN_USERNAME"@mail.com
ADMIN_PASSWORD=$(head -c 12 /dev/urandom | base64)
ADMIN_TENANT_ID=$(uuidgen | tr A-Z a-z)
ADMIN_CLIENT_ID=$(uuidgen | tr A-Z a-z)
ADMIN_CLIENT_ID_ALIAS="client_$(head -c 4 /dev/urandom | base64 | tr -dc 'a-zA-Z0-9' | head -c 8)"
ADMIN_CLIENT_SECRET=$(head -c 48 /dev/urandom | base64)
DRY_RUN="false"

echo "Setting environment variables..."

echo "BASE_URL=$BASE_URL" >> .env
echo "ADMIN_USERNAME=$ADMIN_USERNAME" >> .env
echo "ADMIN_EMAIL=$ADMIN_EMAIL" >> .env
echo "ADMIN_PASSWORD=$ADMIN_PASSWORD" >> .env
echo "ADMIN_TENANT_ID=$ADMIN_TENANT_ID" >> .env
echo "ADMIN_CLIENT_ID=$ADMIN_CLIENT_ID" >> .env
echo "ADMIN_CLIENT_ID_ALIAS=$ADMIN_CLIENT_ID_ALIAS" >> .env
echo "ADMIN_CLIENT_SECRET=$ADMIN_CLIENT_SECRET" >> .env
echo "DRY_RUN=$DRY_RUN" >> .env

# Add database configuration section
echo "" >> .env
echo "# Database Configuration" >> .env
echo "# PostgreSQL superuser password" >> .env
echo "POSTGRES_PASSWORD=$POSTGRES_PASSWORD" >> .env
echo "" >> .env
echo "# Database owner password (for migrations)" >> .env
echo "DB_OWNER_PASSWORD=$DB_OWNER_PASSWORD" >> .env
echo "" >> .env
echo "# Admin user password (RLS bypass for management operations)" >> .env
echo "IDP_ADMIN_PASSWORD=$IDP_ADMIN_PASSWORD" >> .env
echo "" >> .env
echo "# Application user password (RLS-compliant operations)" >> .env
echo "DB_APP_PASSWORD=$DB_APP_PASSWORD" >> .env

echo ".env file generated:"
echo "  - Application credentials: API Key, Secret, Encryption Key"
echo "  - Admin user credentials: Username, Password, Tenant ID, Client ID"
echo "  - Database passwords: PostgreSQL, Owner, Admin, App users"
echo ""
echo "IMPORTANT: Store these passwords securely!"

#echo "export IDP_SERVER_DOMAIN=http://localhost:8080/"
#echo "export IDP_SERVER_API_KEY=$API_KEY"
#echo "export IDP_SERVER_API_SECRET=$API_SECRET"
#echo "export ENCRYPTION_KEY=$ENCRYPTION_KEY"
#echo "export ENV=local"
