/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

-- =====================================================
-- bench.sql で投入したダミーデータと検証用 index を片付ける。
-- 本番では絶対に実行しないこと。
-- =====================================================

\set ON_ERROR_STOP on

\echo '=== ベンチ用ダミーユーザー削除 (provider_id = bench-1460) ==='
DELETE FROM idp_user WHERE provider_id = 'bench-1460';

\echo ''
\echo '=== 検証用 index 削除 ==='
DROP INDEX IF EXISTS idx_idp_user_tenant_created_at;

\echo ''
\echo '=== 残存確認 (どちらも 0 のはず) ==='
SELECT COUNT(*) AS remaining_bench_users
FROM idp_user
WHERE provider_id = 'bench-1460';

SELECT COUNT(*) AS remaining_bench_index
FROM pg_indexes
WHERE tablename = 'idp_user'
  AND indexname = 'idx_idp_user_tenant_created_at';
