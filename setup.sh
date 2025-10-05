#!/bin/zsh
# read .env
set -a; [ -f .env ] && source .env; set +a

echo "env: $ENV"
echo "url: $BASE_URL"

#admin
curl -X POST "${IDP_SERVER_DOMAIN}v1/admin/initialization" \
-u "${IDP_SERVER_API_KEY}:${IDP_SERVER_API_SECRET}" \
-H "Content-Type:application/json" \
--data @./config/examples/"${ENV}"/admin-tenant/initial.json | jq
