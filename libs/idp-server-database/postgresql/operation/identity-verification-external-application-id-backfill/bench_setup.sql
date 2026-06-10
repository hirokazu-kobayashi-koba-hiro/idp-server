/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

-- =====================================================
-- create_index.sql ベンチ用データ投入
--
-- identity_verification_application に ROW_COUNT 行のダミーを投入する。
-- 半数の external_application_id は NULL、半数は UUID テキスト (重複なし)。
-- bench マーカー: verification_type = 'BENCH-EXTERNAL-APP-ID' で識別 (cleanup 用)
--
-- 注意:
--   - ローカル / ステージング以外では実行しないこと
--   - external_application_id カラムは V0_10_0_2 で追加済みの前提
--   - ROW_COUNT はコマンドラインで指定 (例: psql -v ROW_COUNT=1000000)
-- =====================================================

\set ON_ERROR_STOP on
\set TARGET_TENANT '\'67e7eae6-62b0-4500-9eff-87459f63fc66\''

\echo '=== Step 1: ダミー identity_verification_application 投入 ==='
\echo '  verification_type = BENCH-EXTERNAL-APP-ID'
\echo '  external_application_id = 半数 NULL / 半数 UUID テキスト'

INSERT INTO identity_verification_application (
    id,
    tenant_id,
    client_id,
    user_id,
    verification_type,
    external_application_id,
    application_details,
    processes,
    status,
    requested_at,
    created_at,
    updated_at
)
SELECT
    gen_random_uuid(),
    :TARGET_TENANT::uuid,
    'bench-client',
    gen_random_uuid(),
    'BENCH-EXTERNAL-APP-ID',
    CASE WHEN g % 2 = 0 THEN gen_random_uuid()::text ELSE NULL END,
    '{}'::jsonb,
    '{}'::jsonb,
    'requested',
    NOW(),
    NOW(),
    NOW()
FROM generate_series(1, :ROW_COUNT) g
ON CONFLICT DO NOTHING;

\echo ''
\echo '=== Step 2: 統計更新 ==='
ANALYZE identity_verification_application;

\echo ''
\echo '=== 投入結果 ==='
SELECT
    COUNT(*)                                       AS bench_rows_total,
    COUNT(external_application_id)                 AS bench_rows_with_value,
    COUNT(*) - COUNT(external_application_id)      AS bench_rows_with_null,
    pg_size_pretty(pg_total_relation_size('identity_verification_application')) AS table_size_total
FROM identity_verification_application
WHERE verification_type = 'BENCH-EXTERNAL-APP-ID';
