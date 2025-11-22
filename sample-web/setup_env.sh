#!/bin/zsh

cp .env.template .env

if [ -z "$AUTH_SECRET" ]; then
  AUTH_SECRET=$(openssl rand -base64 32)
  echo "Generated AUTH_SECRET: $AUTH_SECRET"
else
  echo "Using existing AUTH_SECRET from environment."
fi

if [[ "$OSTYPE" == "darwin"* ]]; then
  # macOS（-i ''）
  sed -i '' "s|AUTH_SECRET=\".*\"|AUTH_SECRET=\"$AUTH_SECRET\"|" .env
  sed -i '' "s|NEXT_PUBLIC_IDP_SERVER_ISSUER=.*|NEXT_PUBLIC_IDP_SERVER_ISSUER=${NEXT_PUBLIC_IDP_SERVER_ISSUER}|" .env
  sed -i '' "s|NEXT_PUBLIC_FRONTEND_URL=.*|NEXT_PUBLIC_FRONTEND_URL=${NEXT_PUBLIC_FRONTEND_URL}|" .env
  sed -i '' "s|NEXT_PUBLIC_IDP_CLIENT_ID=.*|NEXT_PUBLIC_IDP_CLIENT_ID=${NEXT_PUBLIC_IDP_CLIENT_ID}|" .env
  sed -i '' "s|NEXT_IDP_CLIENT_SECRET=.*|NEXT_IDP_CLIENT_SECRET=${NEXT_IDP_CLIENT_SECRET}|" .env
else
  # Linux
  sed -i "s|AUTH_SECRET=\".*\"|AUTH_SECRET=\"$AUTH_SECRET\"|" .env
  sed -i "s|NEXT_PUBLIC_IDP_SERVER_ISSUER=.*|NEXT_PUBLIC_IDP_SERVER_ISSUER=${NEXT_PUBLIC_IDP_SERVER_ISSUER}|" .env
  sed -i "s|NEXT_PUBLIC_FRONTEND_URL=.*|NEXT_PUBLIC_FRONTEND_URL=${NEXT_PUBLIC_FRONTEND_URL}|" .env
  sed -i "s|NEXT_PUBLIC_IDP_CLIENT_ID=.*|NEXT_PUBLIC_IDP_CLIENT_ID=${NEXT_PUBLIC_IDP_CLIENT_ID}|" .env
  sed -i "s|NEXT_IDP_CLIENT_SECRET=.*|NEXT_IDP_CLIENT_SECRET=${NEXT_IDP_CLIENT_SECRET}|" .env
fi

echo ".env has been created successfully!"

