/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

-- =====================================================
-- bench データ投入 (200 万行 + role/permission + idp_user_roles)
--
-- bench.sql で計測を流す前に 1 回だけ実行する。
-- 冪等: 既にデータがあれば再投入はスキップ (ON CONFLICT DO NOTHING)。
-- 投入後、idx_idp_user_tenant_created_at も作成して ANALYZE まで実行する。
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
FROM generate_series(1, 2000000) g
ON CONFLICT DO NOTHING;

\echo ''
\echo '=== Step 2: role / permission / role_permission 投入 ==='
-- 5 個の role (bench-role-1 〜 5)
INSERT INTO role (id, tenant_id, name, description, created_at, updated_at)
SELECT gen_random_uuid(), :TARGET_TENANT::uuid, 'bench-role-' || g, 'bench role', NOW(), NOW()
FROM generate_series(1, 5) g
ON CONFLICT DO NOTHING;

-- 10 個の permission (bench-perm-1 〜 10)
INSERT INTO permission (id, tenant_id, name, description, created_at, updated_at)
SELECT gen_random_uuid(), :TARGET_TENANT::uuid, 'bench-perm-' || g, 'bench permission', NOW(), NOW()
FROM generate_series(1, 10) g
ON CONFLICT DO NOTHING;

-- 各 bench-role に bench-perm を 2 個ずつ割り当て (1=>1,2 / 2=>3,4 / ... / 5=>9,10)
INSERT INTO role_permission (id, tenant_id, role_id, permission_id, created_at)
SELECT gen_random_uuid(), :TARGET_TENANT::uuid, r.id, p.id, NOW()
FROM role r
JOIN permission p ON p.tenant_id = r.tenant_id
WHERE r.name LIKE 'bench-role-%' AND p.name LIKE 'bench-perm-%'
  AND CAST(SUBSTRING(p.name FROM 12) AS INT) IN (
    CAST(SUBSTRING(r.name FROM 12) AS INT) * 2 - 1,
    CAST(SUBSTRING(r.name FROM 12) AS INT) * 2
  )
ON CONFLICT DO NOTHING;

\echo ''
\echo '=== Step 3: idp_user_roles 割り当て (5% / 0.5% / 0.05%) ==='
\echo '  - bench-role-1 に 100,000 ユーザー'
INSERT INTO idp_user_roles (id, tenant_id, user_id, role_id, assigned_at)
SELECT gen_random_uuid(), :TARGET_TENANT::uuid, u.id, r.id, NOW()
FROM (SELECT id FROM idp_user WHERE tenant_id = :TARGET_TENANT::uuid AND provider_id = 'bench-1460' LIMIT 100000) u,
     (SELECT id FROM role WHERE tenant_id = :TARGET_TENANT::uuid AND name = 'bench-role-1') r
WHERE NOT EXISTS (
    SELECT 1 FROM idp_user_roles existing
    WHERE existing.user_id = u.id AND existing.role_id = r.id
  );

\echo '  - bench-role-2 に 10,000 ユーザー'
INSERT INTO idp_user_roles (id, tenant_id, user_id, role_id, assigned_at)
SELECT gen_random_uuid(), :TARGET_TENANT::uuid, u.id, r.id, NOW()
FROM (SELECT id FROM idp_user WHERE tenant_id = :TARGET_TENANT::uuid AND provider_id = 'bench-1460' OFFSET 100000 LIMIT 10000) u,
     (SELECT id FROM role WHERE tenant_id = :TARGET_TENANT::uuid AND name = 'bench-role-2') r
WHERE NOT EXISTS (
    SELECT 1 FROM idp_user_roles existing
    WHERE existing.user_id = u.id AND existing.role_id = r.id
  );

\echo '  - bench-role-3 に 1,000 ユーザー'
INSERT INTO idp_user_roles (id, tenant_id, user_id, role_id, assigned_at)
SELECT gen_random_uuid(), :TARGET_TENANT::uuid, u.id, r.id, NOW()
FROM (SELECT id FROM idp_user WHERE tenant_id = :TARGET_TENANT::uuid AND provider_id = 'bench-1460' OFFSET 110000 LIMIT 1000) u,
     (SELECT id FROM role WHERE tenant_id = :TARGET_TENANT::uuid AND name = 'bench-role-3') r
WHERE NOT EXISTS (
    SELECT 1 FROM idp_user_roles existing
    WHERE existing.user_id = u.id AND existing.role_id = r.id
  );

\echo ''
\echo '=== Step 4: idx_idp_user_tenant_created_at 作成 ==='
CREATE INDEX IF NOT EXISTS idx_idp_user_tenant_created_at
    ON idp_user (tenant_id, created_at DESC);

\echo ''
\echo '=== Step 5: 統計更新 ==='
ANALYZE idp_user;
ANALYZE idp_user_roles;
ANALYZE role;
ANALYZE role_permission;
ANALYZE permission;

\echo ''
\echo '=== 投入結果 ==='
SELECT 'idp_user (bench)' AS t, COUNT(*) AS n FROM idp_user WHERE provider_id = 'bench-1460'
UNION ALL SELECT 'role (bench)', COUNT(*) FROM role WHERE name LIKE 'bench-role-%'
UNION ALL SELECT 'permission (bench)', COUNT(*) FROM permission WHERE name LIKE 'bench-perm-%'
UNION ALL SELECT 'idp_user_roles (bench)', COUNT(*) FROM idp_user_roles
  WHERE role_id IN (SELECT id FROM role WHERE name LIKE 'bench-role-%');
