#!/bin/zsh

#admin
curl -X POST "${IDP_SERVER_DOMAIN}v1/admin/initialization" \
-u "${IDP_SERVER_API_KEY}:${IDP_SERVER_API_SECRET}" \
-H "Content-Type:application/json" \
--data @./sample-config/admin-tenant/initial.json | jq
