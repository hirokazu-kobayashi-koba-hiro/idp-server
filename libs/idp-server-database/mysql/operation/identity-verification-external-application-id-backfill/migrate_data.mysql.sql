-- =====================================================
-- Issue #1550 follow-up: external_application_id backfill (MySQL)
--
-- See postgresql/operation/identity-verification-external-application-id-backfill/migrate_data.sql
-- for full context. Idempotent: only touches rows where
-- external_application_id IS NULL.
-- =====================================================

SELECT COUNT(*) AS rows_to_backfill
FROM identity_verification_application
WHERE external_application_id IS NULL;

-- NULLIF: 万一 application_details の値が空文字の場合、UNIQUE 制約 (NULL は対象外、
-- 空文字は厳格判定) に違反するのを防ぐ。値オブジェクト側でも normalize しているが、
-- backfill 経路でも同等の保険を入れる。
UPDATE identity_verification_application AS app
JOIN identity_verification_configuration AS conf
  ON conf.tenant_id = app.tenant_id
 AND conf.type      = app.verification_type
SET app.external_application_id = NULLIF(
        JSON_UNQUOTE(
            JSON_EXTRACT(
                app.application_details,
                CONCAT('$.', JSON_UNQUOTE(JSON_EXTRACT(conf.payload, '$.common.callback_application_id_param')))
            )
        ),
        '')
WHERE app.external_application_id IS NULL
  AND JSON_EXTRACT(conf.payload, '$.common.callback_application_id_param') IS NOT NULL;

SELECT COUNT(*) AS rows_remaining_null
FROM identity_verification_application
WHERE external_application_id IS NULL;
