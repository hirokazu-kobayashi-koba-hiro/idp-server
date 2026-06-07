/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

-- =====================================================
-- Issue #1550 follow-up: backfill verification
--
-- Returns rows whose external_application_id does NOT match what is
-- still inside application_details under the configured key. Empty
-- result = OK.
-- =====================================================

\echo '=== Mismatched rows (expected = 0) ==='
SELECT app.tenant_id,
       app.id,
       app.verification_type,
       app.external_application_id,
       app.application_details ->>
           (conf.payload -> 'common' ->> 'callback_application_id_param') AS expected_value
FROM identity_verification_application AS app
JOIN identity_verification_configuration AS conf
  ON  conf.tenant_id = app.tenant_id
  AND conf.type      = app.verification_type
WHERE conf.payload -> 'common' ->> 'callback_application_id_param' IS NOT NULL
  AND app.external_application_id IS DISTINCT FROM
      app.application_details ->>
          (conf.payload -> 'common' ->> 'callback_application_id_param')
LIMIT 100;

\echo ''
\echo '=== Duplicate external_application_id within same tenant (expected = 0) ==='
\echo '   UNIQUE (tenant_id, external_application_id) 制約に違反する重複の早期検知。'
SELECT tenant_id, external_application_id, COUNT(*) AS dup_count
FROM identity_verification_application
WHERE external_application_id IS NOT NULL
  AND external_application_id <> ''
GROUP BY tenant_id, external_application_id
HAVING COUNT(*) > 1
ORDER BY dup_count DESC
LIMIT 100;
