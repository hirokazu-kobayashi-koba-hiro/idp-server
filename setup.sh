#!/bin/zsh
# read .env
set -a; [ -f .env ] && source .env; set +a

echo "env: $ENV"
echo "url: $AUTHORIZATION_SERVER_URL"

#admin
curl -X POST "${AUTHORIZATION_SERVER_URL}/v1/admin/initialization" \
-u "${IDP_SERVER_API_KEY}:${IDP_SERVER_API_SECRET}" \
-H "Content-Type:application/json" \
--data @./config/generated/"${ENV}"/admin-tenant/initial.json | jq
