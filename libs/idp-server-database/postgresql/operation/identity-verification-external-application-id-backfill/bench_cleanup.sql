/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

-- =====================================================
-- bench データ + index の後片付け
-- =====================================================

\set ON_ERROR_STOP on

\echo '=== ベンチ用 index を DROP CONCURRENTLY ==='
DROP INDEX CONCURRENTLY IF EXISTS idx_verification_external_application_id;

\echo ''
\echo '=== ベンチ用 identity_verification_application 削除 (verification_type = BENCH-EXTERNAL-APP-ID) ==='
DELETE FROM identity_verification_application
WHERE verification_type = 'BENCH-EXTERNAL-APP-ID';

\echo ''
\echo '=== 残存確認 (全て 0 のはず) ==='
SELECT COUNT(*) AS remaining_bench_rows
FROM identity_verification_application
WHERE verification_type = 'BENCH-EXTERNAL-APP-ID';

SELECT COUNT(*) AS remaining_bench_index
FROM pg_indexes
WHERE tablename = 'identity_verification_application'
  AND indexname = 'idx_verification_external_application_id';
