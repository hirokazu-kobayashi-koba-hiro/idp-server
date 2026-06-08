/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

-- =====================================================
-- idp_user index benchmark (Issue #1460 再現用)
--
-- 200 万行のダミーユーザーを既存テナント (TARGET_TENANT) に挿入し、
-- ORDER BY created_at DESC LIMIT 20 の性能を before/after 比較する。
--
-- 使い方:
--   1. TARGET_TENANT に既存テナント UUID を設定 (psql 変数で渡してもよい)
--   2. psql -f bench.sql で実行
--   3. ベンチ後は cleanup セクションをコメントアウト解除して片付ける
--
-- 注意: ローカル / ステージング以外では実行しないこと
-- =====================================================

\set TARGET_TENANT '\'67e7eae6-62b0-4500-9eff-87459f63fc66\''
\set ON_ERROR_STOP on

\echo '=== Step 1: 200 万行ダミーユーザー挿入 (provider_id = bench-1460) ==='
INSERT INTO idp_user (id, tenant_id, provider_id, preferred_username, status, created_at, updated_at)
SELECT
    gen_random_uuid(),
    :TARGET_TENANT::uuid,
    'bench-1460',
    'bench_user_' || g,
    'REGISTERED',
    NOW() - (random() * interval '365 days'),
    NOW()
FROM generate_series(1, 2000000) g;

\echo ''
\echo '=== Step 2: 統計更新 ==='
ANALYZE idp_user;

\echo ''
\echo '=== Step 3: index なしの計測 ==='
DROP INDEX IF EXISTS idx_idp_user_tenant_created_at;
EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT id FROM idp_user
WHERE tenant_id = :TARGET_TENANT::uuid
ORDER BY created_at DESC
LIMIT 20;

\echo ''
\echo '=== Step 4: index ありの計測 ==='
CREATE INDEX idx_idp_user_tenant_created_at
    ON idp_user (tenant_id, created_at DESC);
EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT id FROM idp_user
WHERE tenant_id = :TARGET_TENANT::uuid
ORDER BY created_at DESC
LIMIT 20;

-- =====================================================
-- Issue #1565: ページネーション総件数取得 selectCount のベンチ
--
-- selectCount は role/permission 絞り込みが無くても 4-way LEFT JOIN を
-- 実行している。selectList と同じ hasRoleOrPermissionFilter 分岐で
-- JOIN を外すと、絞り込み無し時は単表 COUNT(*) で済む。
-- (tenant_id, created_at DESC) index も index-only scan に活用される。
-- =====================================================

\echo ''
\echo '=== Step 5: selectCount 現状 (常に 4-way JOIN + COUNT(DISTINCT)) ==='
EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT COUNT(DISTINCT idp_user.id)
FROM idp_user
LEFT JOIN idp_user_roles  ON idp_user.id = idp_user_roles.user_id
LEFT JOIN role            ON idp_user_roles.role_id = role.id
LEFT JOIN role_permission ON role.id = role_permission.role_id
LEFT JOIN permission      ON role_permission.permission_id = permission.id
WHERE idp_user.tenant_id = :TARGET_TENANT::uuid;

\echo ''
\echo '=== Step 6: selectCount 改善後 (role/permission 絞り込み無しなら単表 COUNT(*)) ==='
EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT COUNT(*)
FROM idp_user
WHERE tenant_id = :TARGET_TENANT::uuid;

-- =====================================================
-- ベンチ完了後は bench_cleanup.sql を実行してダミーデータと
-- 検証用 index を片付ける。
--   psql -f bench_cleanup.sql
-- =====================================================
