-- V0_10_0_1__statistics_events_bucket_distribution.mysql.sql
-- Issue #1443: statistics_event_buckets (bucket-distributed counters)
--
-- Real-time UPSERT to statistics_events causes row lock contention on the
-- hot key (tenant_id, stat_date, event_type). This migration introduces a
-- NEW table statistics_event_buckets that scatters writes across N rows
-- per logical key via a bucket_id dimension.
--
-- Read side aggregates with SUM(count) ... GROUP BY tenant_id, stat_date,
-- event_type (handled in repository code).
--
-- The existing statistics_events table is left untouched by this migration
-- to keep the rollout side-effect free. Historical data is migrated to the
-- new table by a separate operational script after deploy completes:
--   libs/idp-server-database/mysql/operation/
--     statistics-events-bucket-migration/migrate_data.mysql.sql

CREATE TABLE statistics_event_buckets (
    tenant_id CHAR(36) NOT NULL,
    stat_date DATE NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    bucket_id SMALLINT NOT NULL,
    count BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (tenant_id, stat_date, event_type, bucket_id),
    KEY idx_statistics_event_buckets_tenant_date (tenant_id, stat_date DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Bucket-distributed statistics counters (Issue #1443). Successor of statistics_events.';
