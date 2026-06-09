/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

-- =====================================================
-- security_event JSONB 検索ベンチ用データ投入
--
-- 100,000 件のダミー security_event を直近 30 日に分散投入する。
-- detail JSONB は意図的に多様な値を持たせ、複数のヒット率で計測可能にする。
--
-- bench マーカー: type = 'BENCH_SECURITY_EVENT' で識別 (cleanup 用)
--
-- 想定計測パターン:
--   - 高ヒット率   : detail.outcome = 'success' (50%)
--   - 中ヒット率   : detail.status = 'processed' (5%)
--   - 低ヒット率   : detail.resource = 'invoice' (1%)
--   - 極低ヒット率 : detail.event_id = '<特定 UUID>' (0.001%)
--   - 動的キー検索 : detail.outcome=success + detail.method=POST (AND)
--
-- 注意: ローカル / ステージング以外では実行しないこと
-- =====================================================

-- e2e の admin tenant (organization_security_event_management.test.js と整合)
\set TARGET_TENANT '\'952f6906-3e95-4ed3-86b2-981f90f785f9\''
\set ON_ERROR_STOP on

\echo '=== Step 1: 100,000 件ダミー security_event 投入 (type = BENCH_SECURITY_EVENT) ==='
\echo '  - created_at: 直近 30 日にランダム分散 (パーティション横断)'
\echo '  - detail.outcome: success (50%) / failure (50%)'
\echo '  - detail.status : pending (90%) / processed (5%) / retry (5%)'
\echo '  - detail.resource: 5 種類 (token / user / session / client / invoice 各 20%)'
\echo '  - detail.method  : POST / GET / PUT / DELETE'
\echo '  - detail.event_id: 全件ユニーク UUID'

INSERT INTO security_event (
    id, type, description, tenant_id, tenant_name,
    client_id, client_name, user_id, user_name, external_user_id,
    ip_address, user_agent, detail, created_at
)
SELECT
    gen_random_uuid(),
    'BENCH_SECURITY_EVENT',
    'bench security event #' || g,
    :TARGET_TENANT::uuid,
    'bench-tenant',
    'bench-client',
    'bench-client',
    gen_random_uuid(),
    'bench_user_' || g,
    'ext_' || g,
    ('192.168.' || (g % 256) || '.' || ((g / 256) % 256))::inet,
    'BenchAgent/1.0',
    jsonb_build_object(
        'event_id', gen_random_uuid()::text,
        'outcome',  CASE WHEN g % 2 = 0 THEN 'success' ELSE 'failure' END,
        'status',   CASE
                      WHEN g % 100 < 90 THEN 'pending'
                      WHEN g % 100 < 95 THEN 'processed'
                      ELSE 'retry'
                    END,
        'resource', CASE g % 5
                      WHEN 0 THEN 'token'
                      WHEN 1 THEN 'user'
                      WHEN 2 THEN 'session'
                      WHEN 3 THEN 'client'
                      ELSE 'invoice'
                    END,
        'method',   CASE g % 4
                      WHEN 0 THEN 'POST'
                      WHEN 1 THEN 'GET'
                      WHEN 2 THEN 'PUT'
                      ELSE 'DELETE'
                    END,
        -- bench_index は本番想定 (string 値) に合わせて文字列化
        'bench_index', g::text,
        -- ネスト検索 (`?details.user.sub=xxx`) を再現するためのネスト構造。
        -- 本番の detail.user.sub と同じ階層感を持たせる。
        'user', jsonb_build_object(
          'sub',  'sub-' || (g % 1000)::text,    -- 高ヒット率: 約 100 件/値
          'name', 'user-' || g::text             -- 極低ヒット率: ユニーク
        )
    ),
    -- 直近 30 日にランダム分散 (パーティション複数に渡る)
    NOW() - (random() * interval '30 days')
FROM generate_series(1, 100000) g;

\echo ''
\echo '=== Step 2: ANALYZE (パーティション含む) ==='
ANALYZE security_event;

\echo ''
\echo '=== 投入結果 ==='
SELECT 'security_event (bench)' AS t, COUNT(*) AS n
FROM security_event WHERE type = 'BENCH_SECURITY_EVENT';

\echo ''
\echo '=== ヒット率内訳 ==='
SELECT
    'outcome=success' AS pattern,
    COUNT(*) FILTER (WHERE detail->>'outcome' = 'success') AS n,
    COUNT(*) FILTER (WHERE detail->>'outcome' = 'success') * 100.0 / COUNT(*) AS pct
FROM security_event WHERE type = 'BENCH_SECURITY_EVENT'
UNION ALL
SELECT 'status=processed',
    COUNT(*) FILTER (WHERE detail->>'status' = 'processed'),
    COUNT(*) FILTER (WHERE detail->>'status' = 'processed') * 100.0 / COUNT(*)
FROM security_event WHERE type = 'BENCH_SECURITY_EVENT'
UNION ALL
SELECT 'resource=invoice',
    COUNT(*) FILTER (WHERE detail->>'resource' = 'invoice'),
    COUNT(*) FILTER (WHERE detail->>'resource' = 'invoice') * 100.0 / COUNT(*)
FROM security_event WHERE type = 'BENCH_SECURITY_EVENT';
