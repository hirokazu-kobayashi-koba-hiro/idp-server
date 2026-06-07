/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

-- =====================================================
-- Issue #1550 follow-up: external_application_id backfill
--
-- After V0_10_0_2 adds the external_application_id column, existing rows
-- have it as NULL. This script copies the value from application_details
-- using the per-verification-type configuration key
-- (identity_verification_configuration.payload -> 'common' ->>
-- 'callback_application_id_param').
--
-- Idempotent: only touches rows where external_application_id IS NULL.
-- Safe to re-run.
-- =====================================================

\set ON_ERROR_STOP on

\echo 'Rows to backfill (external_application_id IS NULL):'
SELECT COUNT(*) AS rows_to_backfill
FROM identity_verification_application
WHERE external_application_id IS NULL;

BEGIN;

-- NULLIF: 万一 application_details の値が空文字の場合、UNIQUE 制約 (NULL は対象外、
-- 空文字は厳格判定) に違反するのを防ぐ。値オブジェクト側でも normalize しているが、
-- backfill 経路でも同等の保険を入れる。
UPDATE identity_verification_application AS app
SET external_application_id = NULLIF(
        app.application_details ->>
            (conf.payload -> 'common' ->> 'callback_application_id_param'),
        '')
FROM identity_verification_configuration AS conf
WHERE conf.tenant_id = app.tenant_id
  AND conf.type = app.verification_type
  AND app.external_application_id IS NULL
  AND conf.payload -> 'common' ->> 'callback_application_id_param' IS NOT NULL
  AND app.application_details ? (conf.payload -> 'common' ->> 'callback_application_id_param');

COMMIT;

\echo 'Rows still NULL after backfill (typically configs without callback_application_id_param):'
SELECT COUNT(*) AS rows_remaining_null
FROM identity_verification_application
WHERE external_application_id IS NULL;
