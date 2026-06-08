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

\echo '=== ベンチ用 role/permission 関連データ削除 ==='
-- idp_user_roles は idp_user 削除より先に消す (user_id 経由なので残骸残らないが明示)
DELETE FROM idp_user_roles
WHERE role_id IN (SELECT id FROM role WHERE name LIKE 'bench-role-%');
DELETE FROM role_permission
WHERE role_id IN (SELECT id FROM role WHERE name LIKE 'bench-role-%')
   OR permission_id IN (SELECT id FROM permission WHERE name LIKE 'bench-perm-%');
DELETE FROM role WHERE name LIKE 'bench-role-%';
DELETE FROM permission WHERE name LIKE 'bench-perm-%';

\echo ''
\echo '=== ベンチ用ダミーユーザー削除 (provider_id = bench-1460) ==='
DELETE FROM idp_user WHERE provider_id = 'bench-1460';

\echo ''
\echo '=== 検証用 index 削除 ==='
DROP INDEX IF EXISTS idx_idp_user_tenant_created_at;

\echo ''
\echo '=== 残存確認 (全て 0 のはず) ==='
SELECT COUNT(*) AS remaining_bench_users
FROM idp_user
WHERE provider_id = 'bench-1460';

SELECT COUNT(*) AS remaining_bench_roles
FROM role
WHERE name LIKE 'bench-role-%';

SELECT COUNT(*) AS remaining_bench_permissions
FROM permission
WHERE name LIKE 'bench-perm-%';

SELECT COUNT(*) AS remaining_bench_index
FROM pg_indexes
WHERE tablename = 'idp_user'
  AND indexname = 'idx_idp_user_tenant_created_at';
