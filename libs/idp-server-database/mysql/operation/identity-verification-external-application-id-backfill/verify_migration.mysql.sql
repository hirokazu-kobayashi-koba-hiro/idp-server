-- =====================================================
-- Issue #1550 follow-up: backfill verification (MySQL)
--
-- Empty result = OK. See postgresql/ counterpart for context.
-- =====================================================

SELECT app.tenant_id,
       app.id,
       app.verification_type,
       app.external_application_id,
       JSON_UNQUOTE(
           JSON_EXTRACT(
               app.application_details,
               CONCAT('$.', JSON_UNQUOTE(JSON_EXTRACT(conf.payload, '$.common.callback_application_id_param')))
           )
       ) AS expected_value
FROM identity_verification_application AS app
JOIN identity_verification_configuration AS conf
  ON  conf.tenant_id = app.tenant_id
  AND conf.type      = app.verification_type
WHERE JSON_EXTRACT(conf.payload, '$.common.callback_application_id_param') IS NOT NULL
  AND NOT (app.external_application_id <=>
           JSON_UNQUOTE(
               JSON_EXTRACT(
                   app.application_details,
                   CONCAT('$.', JSON_UNQUOTE(JSON_EXTRACT(conf.payload, '$.common.callback_application_id_param')))
               )
           ))
LIMIT 100;
