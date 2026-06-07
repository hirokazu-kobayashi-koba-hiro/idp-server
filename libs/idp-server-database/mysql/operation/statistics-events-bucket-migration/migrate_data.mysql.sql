-- Issue #1443: Historical data migration from
--              statistics_events  →  statistics_event_buckets (MySQL)
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
--   UPSERTs.
--
-- Idempotency:
--   Running this script twice would double-count. A pre-flight guard
--   below aborts if past-date rows at bucket_id = 0 already exist (i.e.
--   this migration has run before). If a re-run is intended, delete
--   bucket_0 rows first:
--     DELETE FROM statistics_event_buckets WHERE bucket_id = 0;
--   (do NOT TRUNCATE; that would also wipe real-time writes in
--   bucket_id ∈ [1, BUCKET_COUNT]).

SELECT
    (SELECT COUNT(*) FROM statistics_events) AS legacy_rows,
    (SELECT COUNT(*) FROM statistics_event_buckets) AS new_rows_before;

-- =====================================================
-- Pre-flight guard: abort if the new table already has historical rows.
-- MySQL doesn't support DO blocks at script level, so we wrap the check
-- and the INSERT in a temporary stored procedure.
-- =====================================================

DROP PROCEDURE IF EXISTS migrate_statistics_event_buckets_oneshot;

DELIMITER //
CREATE PROCEDURE migrate_statistics_event_buckets_oneshot()
BEGIN
    DECLARE existing_historical INT;

    SELECT COUNT(*) INTO existing_historical
    FROM statistics_event_buckets
    WHERE stat_date < CURRENT_DATE AND bucket_id = 0;

    IF existing_historical > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT =
                'statistics_event_buckets already contains migrated rows (past-date at bucket_id = 0). Aborting to prevent double-counting. Clear bucket_0 rows and retry. See README.md.';
    END IF;

    START TRANSACTION;

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
    ON DUPLICATE KEY UPDATE
        count = statistics_event_buckets.count + VALUES(count),
        updated_at = GREATEST(statistics_event_buckets.updated_at, VALUES(updated_at));

    COMMIT;
END //
DELIMITER ;

CALL migrate_statistics_event_buckets_oneshot();
DROP PROCEDURE migrate_statistics_event_buckets_oneshot;

SELECT
    (SELECT COUNT(*) FROM statistics_events) AS legacy_rows,
    (SELECT COUNT(*) FROM statistics_event_buckets) AS new_rows_after;
