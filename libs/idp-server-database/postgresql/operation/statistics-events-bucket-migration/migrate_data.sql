/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

-- =====================================================
-- Issue #1443: Historical data migration from
--              statistics_events  →  statistics_event_buckets
--
-- Run AFTER:
--   1. V0_10_0_1 migration has created statistics_event_buckets.
--   2. All pods have rolled to the new code (writes target the new table).
--
-- What this does:
--   Copies every row from the legacy statistics_events table into
--   statistics_event_buckets at bucket_id = 0. If a logical key already
--   has rows in the new table (because new pods started writing real-time
--   during/after deploy), the legacy counts are ADDED to the existing
--   bucket_0 row. This preserves the additive semantics of real-time
--   UPSERTs: total count = sum of legacy events + sum of post-deploy
--   real-time events.
--
-- Idempotency:
--   Running this script twice will double-count. It is intended to be a
--   one-shot operation immediately after the rollout. If a re-run is
--   needed (e.g. recovery), delete bucket_0 rows first:
--     DELETE FROM statistics_event_buckets WHERE bucket_id = 0;
--   (do NOT TRUNCATE; that would also wipe real-time writes in
--   bucket_id ∈ [1, BUCKET_COUNT]).
--
-- Verification:
--   See verify_migration.sql in the same directory.
-- =====================================================

-- Abort immediately on the first error (including the guard's RAISE EXCEPTION
-- below). Without this, psql would print the error from the DO block and then
-- happily continue into the BEGIN/INSERT/COMMIT block, defeating the guard
-- entirely.
\set ON_ERROR_STOP on

\echo 'Counting rows before migration...'
SELECT
    (SELECT COUNT(*) FROM statistics_events) AS legacy_rows,
    (SELECT COUNT(*) FROM statistics_event_buckets) AS new_rows_before;

-- =====================================================
-- Pre-flight guard: abort only if THIS migration has run before.
--
-- The check is narrowed to "past-date rows at bucket_id = 0", because:
--   - bucket_id = 0 is reserved for migrate_data.sql output (see
--     StatisticsEventBuckets javadoc and README.md).
--   - Past-date rows at bucket_id in [1, BUCKET_COUNT] are legitimate
--     real-time writes that landed on a historical stat_date (e.g. an
--     event whose tenant timezone shifted the local date backwards).
--     They are unrelated to this migration and must not block it.
--
-- Running migrate_data.sql twice would ADD legacy counts on top of
-- already-migrated bucket_0 rows. If a re-run is genuinely required
-- (e.g. after a partial failure), explicitly clear the affected rows
-- first:
--
--   -- all migrated rows for all tenants:
--   DELETE FROM statistics_event_buckets WHERE bucket_id = 0;
--   -- or per-tenant:
--   DELETE FROM statistics_event_buckets WHERE tenant_id = '<uuid>' AND bucket_id = 0;
-- =====================================================

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM statistics_event_buckets
        WHERE stat_date < CURRENT_DATE AND bucket_id = 0
    ) THEN
        RAISE EXCEPTION
            'statistics_event_buckets already contains migrated rows (past-date at bucket_id = 0). '
            'Aborting to prevent double-counting. '
            'If a re-run is intended, clear the bucket_0 rows first and retry. '
            'See README.md for recovery steps.';
    END IF;
END
$$;

BEGIN;

-- updated_at: under the bucket_id = 0 reservation, the INSERT below normally
-- creates fresh rows (real-time writes go to bucket_id ∈ [1, BUCKET_COUNT]).
-- GREATEST() defends against the only remaining edge: aggregate or recovery
-- jobs that wrote bucket_0 directly. Picking the larger timestamp keeps the
-- column monotonically non-decreasing across migration + downstream writes.
INSERT INTO statistics_event_buckets
    (tenant_id, stat_date, event_type, bucket_id, count, created_at, updated_at)
SELECT
    tenant_id,
    stat_date,
    event_type,
    0 AS bucket_id,
    count,
    created_at,
    updated_at
FROM statistics_events
ON CONFLICT (tenant_id, stat_date, event_type, bucket_id) DO UPDATE
    SET count = statistics_event_buckets.count + EXCLUDED.count,
        updated_at = GREATEST(statistics_event_buckets.updated_at, EXCLUDED.updated_at);

COMMIT;

\echo 'Counting rows after migration...'
SELECT
    (SELECT COUNT(*) FROM statistics_events) AS legacy_rows,
    (SELECT COUNT(*) FROM statistics_event_buckets) AS new_rows_after;

\echo ''
\echo 'Migration complete. Run verify_migration.sql to spot-check totals.'
