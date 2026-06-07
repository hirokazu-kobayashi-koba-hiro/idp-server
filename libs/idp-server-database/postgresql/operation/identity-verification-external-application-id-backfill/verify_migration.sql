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
