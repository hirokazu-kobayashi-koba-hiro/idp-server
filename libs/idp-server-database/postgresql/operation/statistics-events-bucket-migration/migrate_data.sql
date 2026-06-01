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
--   needed (e.g. recovery), truncate the new table for the affected
--   rows first.
--
-- Verification:
--   See verify_migration.sql in the same directory.
-- =====================================================

\echo 'Counting rows before migration...'
SELECT
    (SELECT COUNT(*) FROM statistics_events) AS legacy_rows,
    (SELECT COUNT(*) FROM statistics_event_buckets) AS new_rows_before;

-- =====================================================
-- Pre-flight guard: abort if the new table already contains historical
-- data. Running migrate_data.sql twice would ADD legacy counts on top of
-- already-migrated rows and produce double-counts. If a re-run is
-- genuinely required (e.g. after a partial failure), explicitly clear
-- the affected rows first:
--
--   TRUNCATE TABLE statistics_event_buckets;
--   -- or, per-tenant:
--   DELETE FROM statistics_event_buckets WHERE tenant_id = '<uuid>';
-- =====================================================

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM statistics_event_buckets WHERE stat_date < CURRENT_DATE
    ) THEN
        RAISE EXCEPTION
            'statistics_event_buckets already contains historical rows (stat_date < CURRENT_DATE). '
            'Aborting to prevent double-counting. '
            'If a re-run is intended, clear the affected rows first and retry. '
            'See README.md for recovery steps.';
    END IF;
END
$$;

BEGIN;

-- updated_at: migration uses GREATEST() to preserve whichever timestamp is more
-- recent (typically the existing real-time post-deploy write). Real-time UPSERTs
-- in PostgresqlExecutor use now(), so the column remains monotonically
-- non-decreasing across migration + steady-state writes.
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
