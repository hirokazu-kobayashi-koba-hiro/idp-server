/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

-- =====================================================
-- Issue #1443: Verification queries for the
--              statistics_events → statistics_event_buckets migration.
--
-- Each query below returns rows ONLY when the legacy table and the new
-- bucket-aggregated counts disagree. An empty result set means the
-- migration is consistent for that check.
--
-- A small per-tenant discrepancy on CURRENT_DATE is expected if:
--   - Real-time writes from new pods occurred between deploy completion
--     and migrate_data.sql execution (those events are only in the new
--     table). The total on the new side will be HIGHER than the legacy
--     side by the volume of post-deploy events. That is correct.
--
-- A discrepancy on PAST dates indicates a real problem.
-- =====================================================

\echo '=== 1. Past-date totals must match exactly ==='
SELECT
    legacy.tenant_id,
    legacy.stat_date,
    legacy.event_type,
    legacy.count        AS legacy_count,
    new_agg.total_count AS new_total_count,
    new_agg.total_count - legacy.count AS diff
FROM statistics_events legacy
LEFT JOIN (
    SELECT tenant_id, stat_date, event_type, SUM(count) AS total_count
    FROM statistics_event_buckets
    WHERE stat_date < CURRENT_DATE
    GROUP BY tenant_id, stat_date, event_type
) new_agg
    ON legacy.tenant_id  = new_agg.tenant_id
   AND legacy.stat_date  = new_agg.stat_date
   AND legacy.event_type = new_agg.event_type
WHERE legacy.stat_date < CURRENT_DATE
  AND (new_agg.total_count IS NULL OR new_agg.total_count <> legacy.count)
ORDER BY legacy.stat_date DESC, legacy.tenant_id, legacy.event_type
LIMIT 100;

\echo ''
\echo '=== 2. Current-date totals: new side must be >= legacy (post-deploy events added on top) ==='
SELECT
    legacy.tenant_id,
    legacy.stat_date,
    legacy.event_type,
    legacy.count        AS legacy_count,
    new_agg.total_count AS new_total_count,
    new_agg.total_count - legacy.count AS diff_should_be_ge_0
FROM statistics_events legacy
LEFT JOIN (
    SELECT tenant_id, stat_date, event_type, SUM(count) AS total_count
    FROM statistics_event_buckets
    WHERE stat_date = CURRENT_DATE
    GROUP BY tenant_id, stat_date, event_type
) new_agg
    ON legacy.tenant_id  = new_agg.tenant_id
   AND legacy.stat_date  = new_agg.stat_date
   AND legacy.event_type = new_agg.event_type
WHERE legacy.stat_date = CURRENT_DATE
  AND (new_agg.total_count IS NULL OR new_agg.total_count < legacy.count)
ORDER BY legacy.tenant_id, legacy.event_type
LIMIT 100;

\echo ''
\echo '=== 3. Sample top tenants/dates for spot-check ==='
SELECT
    b.tenant_id,
    b.stat_date,
    b.event_type,
    SUM(b.count) AS total_count,
    COUNT(*)     AS bucket_rows
FROM statistics_event_buckets b
GROUP BY b.tenant_id, b.stat_date, b.event_type
ORDER BY b.stat_date DESC, total_count DESC
LIMIT 20;
