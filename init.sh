#!/bin/zsh

echo "create api key and secret"

API_KEY=$(uuidgen | tr 'A-Z' 'a-z')
API_SECRET=$(uuidgen | tr 'A-Z' 'a-z' | base64)
ENCRYPTION_KEY=$(head -c 32 /dev/urandom | base64)

echo "Generated API Key: $API_KEY"
echo "Generated API Secret: $API_SECRET"
echo "Generated ENCRYPTION_KEY: $ENCRYPTION_KEY"

echo "Setting environment variables..."

echo "export IDP_SERVER_DOMAIN=http://localhost:8080/"
echo "export IDP_SERVER_API_KEY=$API_KEY"
echo "export IDP_SERVER_API_SECRET=$API_SECRET"
echo "export ENCRYPTION_KEY=$ENCRYPTION_KEY"
