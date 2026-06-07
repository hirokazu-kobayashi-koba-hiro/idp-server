-- =====================================================
-- Issue #1550 follow-up: external_application_id を独立カラム化 (MySQL)
--
-- See postgresql/V0_10_0_2__identity_verification_external_application_id.sql
-- for full context. index 追加は V0_10_0_3 に分離。
-- =====================================================

ALTER TABLE identity_verification_application
    ADD COLUMN external_application_id VARCHAR(255);
