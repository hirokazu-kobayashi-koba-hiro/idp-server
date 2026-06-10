/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

-- =====================================================
-- create_index.sql 性能計測 (CREATE INDEX CONCURRENTLY)
--
-- 前提: bench_setup.sql を ROW_COUNT 指定で実行済みであること。
--
-- CREATE INDEX CONCURRENTLY はトランザクション内で実行不可なので、
-- このスクリプトは BEGIN/COMMIT を使わず psql から直接流す。
--
-- 何度でも繰り返し実行可能。
-- =====================================================

\set ON_ERROR_STOP on
\timing on

\echo '=== 計測前: テーブル状態 ==='
SELECT
    COUNT(*)                                       AS row_count_total,
    COUNT(external_application_id)                 AS rows_with_value,
    COUNT(*) - COUNT(external_application_id)      AS rows_with_null,
    pg_size_pretty(pg_total_relation_size('identity_verification_application')) AS table_size_total,
    pg_size_pretty(pg_relation_size('identity_verification_application'))       AS table_size_heap
FROM identity_verification_application;

\echo ''
\echo '=== Step 1: 既存 idx_verification_external_application_id を DROP CONCURRENTLY ==='
DROP INDEX CONCURRENTLY IF EXISTS idx_verification_external_application_id;

\echo ''
\echo '=== Step 2: CREATE UNIQUE INDEX CONCURRENTLY (計測対象) ==='
\echo '  別 session で進行状況を見るなら:'
\echo '    SELECT phase, blocks_done, blocks_total,'
\echo '           round(100.0 * blocks_done / NULLIF(blocks_total, 0), 1) AS pct'
\echo '    FROM pg_stat_progress_create_index;'
CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS idx_verification_external_application_id
    ON identity_verification_application (tenant_id, external_application_id);

\echo ''
\echo '=== 計測後: index 状態 ==='
SELECT
    i.indexrelid::regclass                                 AS index_name,
    i.indisvalid                                           AS is_valid,
    pg_size_pretty(pg_relation_size(i.indexrelid))         AS index_size
FROM pg_index i
WHERE i.indrelid = 'identity_verification_application'::regclass
  AND i.indexrelid::regclass::text = 'idx_verification_external_application_id';
