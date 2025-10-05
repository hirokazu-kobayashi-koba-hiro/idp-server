#!/bin/zsh

#admin
curl -X POST "${IDP_SERVER_DOMAIN}v1/admin/operations/delete-expired-data" \
-u "${IDP_SERVER_API_KEY}:${IDP_SERVER_API_SECRET}" \
-H "Content-Type:application/json" \
-d '{"max_deletion_number": 100}'
