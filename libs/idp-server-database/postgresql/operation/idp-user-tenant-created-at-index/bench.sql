/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

-- =====================================================
-- idp_user 検索性能ベンチ (計測のみ)
--
-- 前提: bench_setup.sql を先に実行してデータ + index + ANALYZE を済ませる
--
-- 計測内容:
--   Step 1   : selectList (#1460) - ORDER BY created_at DESC LIMIT 20
--   Step 2-3 : selectCount 絞込なし (#1565) 現状 4-way JOIN vs 単表 COUNT(*)
--   Step 4-6 : selectCount role 絞込 (5% / 0.5% / 0.05%)
--   Step 7   : selectCount permission 絞込
--   Step 8   : selectList role 絞込 + LIMIT 20 (現状 DISTINCT JOIN)
--   Step 9-10: 改善案 B (EXISTS) selectCount / selectList
--
-- 何度でも繰り返し実行可能。状態を変えない。
-- =====================================================

\set TARGET_TENANT '\'67e7eae6-62b0-4500-9eff-87459f63fc66\''
\set ON_ERROR_STOP on

\echo '=== Step 1: selectList (index あり, 絞込なし) ==='
EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT id FROM idp_user
WHERE tenant_id = :TARGET_TENANT::uuid
ORDER BY created_at DESC
LIMIT 20;

\echo ''
\echo '=== Step 2: selectCount 現状 (4-way JOIN + COUNT DISTINCT, 絞込なし) ==='
EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT COUNT(DISTINCT idp_user.id)
FROM idp_user
LEFT JOIN idp_user_roles  ON idp_user.id = idp_user_roles.user_id
LEFT JOIN role            ON idp_user_roles.role_id = role.id
LEFT JOIN role_permission ON role.id = role_permission.role_id
LEFT JOIN permission      ON role_permission.permission_id = permission.id
WHERE idp_user.tenant_id = :TARGET_TENANT::uuid;

\echo ''
\echo '=== Step 3: selectCount 改善後 #1568 (単表 COUNT(*), 絞込なし) ==='
EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT COUNT(*) FROM idp_user
WHERE tenant_id = :TARGET_TENANT::uuid;

\echo ''
\echo '=== Step 4: selectCount role 絞込 5% (bench-role-1 / 100,000 ユーザー) ==='
EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT COUNT(DISTINCT idp_user.id)
FROM idp_user
LEFT JOIN idp_user_roles  ON idp_user.id = idp_user_roles.user_id
LEFT JOIN role            ON idp_user_roles.role_id = role.id
LEFT JOIN role_permission ON role.id = role_permission.role_id
LEFT JOIN permission      ON role_permission.permission_id = permission.id
WHERE idp_user.tenant_id = :TARGET_TENANT::uuid
  AND role.name ILIKE '%bench-role-1%';

\echo ''
\echo '=== Step 5: selectCount role 絞込 0.5% (bench-role-2 / 10,000 ユーザー) ==='
EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT COUNT(DISTINCT idp_user.id)
FROM idp_user
LEFT JOIN idp_user_roles  ON idp_user.id = idp_user_roles.user_id
LEFT JOIN role            ON idp_user_roles.role_id = role.id
LEFT JOIN role_permission ON role.id = role_permission.role_id
LEFT JOIN permission      ON role_permission.permission_id = permission.id
WHERE idp_user.tenant_id = :TARGET_TENANT::uuid
  AND role.name ILIKE '%bench-role-2%';

\echo ''
\echo '=== Step 6: selectCount role 絞込 0.05% (bench-role-3 / 1,000 ユーザー) ==='
EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT COUNT(DISTINCT idp_user.id)
FROM idp_user
LEFT JOIN idp_user_roles  ON idp_user.id = idp_user_roles.user_id
LEFT JOIN role            ON idp_user_roles.role_id = role.id
LEFT JOIN role_permission ON role.id = role_permission.role_id
LEFT JOIN permission      ON role_permission.permission_id = permission.id
WHERE idp_user.tenant_id = :TARGET_TENANT::uuid
  AND role.name ILIKE '%bench-role-3%';

\echo ''
\echo '=== Step 7: selectCount permission 絞込 (bench-perm-3 = bench-role-2 経由 / 10,000) ==='
EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT COUNT(DISTINCT idp_user.id)
FROM idp_user
LEFT JOIN idp_user_roles  ON idp_user.id = idp_user_roles.user_id
LEFT JOIN role            ON idp_user_roles.role_id = role.id
LEFT JOIN role_permission ON role.id = role_permission.role_id
LEFT JOIN permission      ON role_permission.permission_id = permission.id
WHERE idp_user.tenant_id = :TARGET_TENANT::uuid
  AND permission.name ILIKE '%bench-perm-3%';

\echo ''
\echo '=== Step 8: selectList role 絞込 (現状: DISTINCT JOIN, bench-role-2, LIMIT 20) ==='
EXPLAIN (ANALYZE, BUFFERS, TIMING)
WITH paged_users AS (
  SELECT DISTINCT idp_user.id, idp_user.created_at FROM idp_user
  LEFT JOIN idp_user_roles  ON idp_user.id = idp_user_roles.user_id
  LEFT JOIN role            ON idp_user_roles.role_id = role.id
  LEFT JOIN role_permission ON role.id = role_permission.role_id
  LEFT JOIN permission      ON role_permission.permission_id = permission.id
  WHERE idp_user.tenant_id = :TARGET_TENANT::uuid
    AND role.name ILIKE '%bench-role-2%'
  ORDER BY idp_user.created_at DESC, idp_user.id DESC
  LIMIT 20 OFFSET 0
)
SELECT idp_user.id, idp_user.created_at
FROM idp_user
WHERE idp_user.id IN (SELECT id FROM paged_users)
ORDER BY idp_user.created_at DESC, idp_user.id DESC;

-- =====================================================
-- 改善案 B: DISTINCT JOIN → EXISTS 書き換え
--
-- 狙い: idp_user (tenant_id, created_at DESC) index を直接活用し、
--       Sort/Unique をスキップする。
-- =====================================================

\echo ''
\echo '=== Step 9: 【B案】selectCount role 絞込 (EXISTS, bench-role-2) ==='
EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT COUNT(*) FROM idp_user
WHERE tenant_id = :TARGET_TENANT::uuid
  AND EXISTS (
    SELECT 1 FROM idp_user_roles ur
    JOIN role r ON ur.role_id = r.id
    WHERE ur.user_id = idp_user.id
      AND r.name ILIKE '%bench-role-2%'
  );

\echo ''
\echo '=== Step 10: 【B案】selectList role 絞込 (EXISTS, bench-role-2, LIMIT 20) ==='
EXPLAIN (ANALYZE, BUFFERS, TIMING)
WITH paged_users AS (
  SELECT idp_user.id, idp_user.created_at FROM idp_user
  WHERE idp_user.tenant_id = :TARGET_TENANT::uuid
    AND EXISTS (
      SELECT 1 FROM idp_user_roles ur
      JOIN role r ON ur.role_id = r.id
      WHERE ur.user_id = idp_user.id
        AND r.name ILIKE '%bench-role-2%'
    )
  ORDER BY idp_user.created_at DESC, idp_user.id DESC
  LIMIT 20 OFFSET 0
)
SELECT idp_user.id, idp_user.created_at
FROM idp_user
WHERE idp_user.id IN (SELECT id FROM paged_users)
ORDER BY idp_user.created_at DESC, idp_user.id DESC;

\echo ''
\echo '=== Step 11: 【B案】selectCount permission 絞込 (EXISTS, bench-perm-3) ==='
EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT COUNT(*) FROM idp_user
WHERE tenant_id = :TARGET_TENANT::uuid
  AND EXISTS (
    SELECT 1 FROM idp_user_roles ur
    JOIN role r ON ur.role_id = r.id
    JOIN role_permission rp ON r.id = rp.role_id
    JOIN permission p ON rp.permission_id = p.id
    WHERE ur.user_id = idp_user.id
      AND p.name ILIKE '%bench-perm-3%'
  );

-- =====================================================
-- 改善案 A: ILIKE '%xxx%' (部分一致) → = (完全一致)
--
-- 狙い: role.name / permission.name を 1 行に絞り込み、
--       idp_user_roles.role_id index を効かせて Seq Scan を回避する。
-- =====================================================

\echo ''
\echo '=== Step 12: 【A案】selectCount role 絞込 (DISTINCT JOIN + =, bench-role-2) ==='
EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT COUNT(DISTINCT idp_user.id)
FROM idp_user
LEFT JOIN idp_user_roles  ON idp_user.id = idp_user_roles.user_id
LEFT JOIN role            ON idp_user_roles.role_id = role.id
LEFT JOIN role_permission ON role.id = role_permission.role_id
LEFT JOIN permission      ON role_permission.permission_id = permission.id
WHERE idp_user.tenant_id = :TARGET_TENANT::uuid
  AND role.name = 'bench-role-2';

\echo ''
\echo '=== Step 13: 【A+B案】selectCount role 絞込 (EXISTS + =, bench-role-2) ==='
EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT COUNT(*) FROM idp_user
WHERE tenant_id = :TARGET_TENANT::uuid
  AND EXISTS (
    SELECT 1 FROM idp_user_roles ur
    JOIN role r ON ur.role_id = r.id
    WHERE ur.user_id = idp_user.id
      AND r.name = 'bench-role-2'
  );

\echo ''
\echo '=== Step 14: 【A+B案】selectList role 絞込 (EXISTS + =, bench-role-2, LIMIT 20) ==='
EXPLAIN (ANALYZE, BUFFERS, TIMING)
WITH paged_users AS (
  SELECT idp_user.id, idp_user.created_at FROM idp_user
  WHERE idp_user.tenant_id = :TARGET_TENANT::uuid
    AND EXISTS (
      SELECT 1 FROM idp_user_roles ur
      JOIN role r ON ur.role_id = r.id
      WHERE ur.user_id = idp_user.id
        AND r.name = 'bench-role-2'
    )
  ORDER BY idp_user.created_at DESC, idp_user.id DESC
  LIMIT 20 OFFSET 0
)
SELECT idp_user.id, idp_user.created_at
FROM idp_user
WHERE idp_user.id IN (SELECT id FROM paged_users)
ORDER BY idp_user.created_at DESC, idp_user.id DESC;

\echo ''
\echo '=== Step 15: 【A+B案】selectCount permission 絞込 (EXISTS + =, bench-perm-3) ==='
EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT COUNT(*) FROM idp_user
WHERE tenant_id = :TARGET_TENANT::uuid
  AND EXISTS (
    SELECT 1 FROM idp_user_roles ur
    JOIN role r ON ur.role_id = r.id
    JOIN role_permission rp ON r.id = rp.role_id
    JOIN permission p ON rp.permission_id = p.id
    WHERE ur.user_id = idp_user.id
      AND p.name = 'bench-perm-3'
  );
