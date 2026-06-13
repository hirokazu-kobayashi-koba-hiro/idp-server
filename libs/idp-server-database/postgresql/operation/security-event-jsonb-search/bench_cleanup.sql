/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

-- =====================================================
-- security_event JSONB 検索ベンチデータの後片付け
--
-- type = 'BENCH_SECURITY_EVENT' のレコードを削除する。
-- 本番では絶対に実行しないこと。
-- =====================================================

\set ON_ERROR_STOP on

\echo '=== ベンチ用 security_event 削除 (type = BENCH_SECURITY_EVENT) ==='
DELETE FROM security_event WHERE type = 'BENCH_SECURITY_EVENT';

\echo ''
\echo '=== 残存確認 (0 のはず) ==='
SELECT COUNT(*) AS remaining_bench_events
FROM security_event
WHERE type = 'BENCH_SECURITY_EVENT';
