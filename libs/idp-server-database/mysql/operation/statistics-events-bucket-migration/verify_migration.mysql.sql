-- =====================================================
-- Issue #1443: Migration verification for
--              statistics_events → statistics_event_buckets (MySQL).
--
-- Asserts that every legacy row was migrated into statistics_event_buckets
-- at bucket_id = 0 with the count preserved exactly. Empty result = OK.
--
-- bucket_id = 0 is reserved for migrate_data output (see
-- StatisticsEventBuckets.java). Real-time writes land in
-- bucket_id ∈ [1, BUCKET_COUNT] and are deliberately excluded here.
-- =====================================================

SELECT
    legacy.tenant_id,
    legacy.stat_date,
    legacy.event_type,
    legacy.count           AS legacy_count,
    new_bucket0.count      AS migrated_count,
    COALESCE(new_bucket0.count, 0) - legacy.count AS diff
FROM statistics_events legacy
LEFT JOIN statistics_event_buckets new_bucket0
    ON  new_bucket0.tenant_id  = legacy.tenant_id
   AND  new_bucket0.stat_date  = legacy.stat_date
   AND  new_bucket0.event_type = legacy.event_type
   AND  new_bucket0.bucket_id  = 0
WHERE new_bucket0.count IS NULL OR new_bucket0.count <> legacy.count
ORDER BY legacy.stat_date DESC, legacy.tenant_id, legacy.event_type
LIMIT 100;
