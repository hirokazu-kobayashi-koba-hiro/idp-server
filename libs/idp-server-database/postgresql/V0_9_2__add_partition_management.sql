/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

-- ================================================
-- Automatic Partition Management Functions
-- Issue #950: Auto-create and auto-drop daily partitions
-- ================================================

-- Enable pg_cron extension
CREATE EXTENSION IF NOT EXISTS pg_cron;

-- ================================================
-- Function: Create next day partitions
-- ================================================

CREATE OR REPLACE FUNCTION create_next_day_partitions()
RETURNS void AS $$
DECLARE
    next_day DATE := CURRENT_DATE + interval '90 days';
    partition_name TEXT;
    start_date TEXT;
    end_date TEXT;
BEGIN
    -- Create partition for security_event
    partition_name := 'security_event_' || to_char(next_day, 'YYYY_MM_DD');
    start_date := to_char(next_day, 'YYYY-MM-DD');
    end_date := to_char(next_day + interval '1 day', 'YYYY-MM-DD');

    EXECUTE format(
        'CREATE TABLE IF NOT EXISTS %I PARTITION OF security_event FOR VALUES FROM (%L) TO (%L)',
        partition_name, start_date, end_date
    );
    RAISE NOTICE 'Created security_event partition: %', partition_name;

EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING 'Failed to create partitions for %: %', next_day, SQLERRM;
END;
$$ LANGUAGE plpgsql;

-- ================================================
-- Function: Drop partitions older than 90 days
-- ================================================

CREATE OR REPLACE FUNCTION drop_old_daily_partitions()
RETURNS void AS $$
DECLARE
    cutoff_date DATE := CURRENT_DATE - interval '90 days';
    partition_name TEXT;
BEGIN
    -- Drop security_event partition
    partition_name := 'security_event_' || to_char(cutoff_date, 'YYYY_MM_DD');

    EXECUTE format('DROP TABLE IF EXISTS %I CASCADE', partition_name);
    RAISE NOTICE 'Dropped security_event partition: % (cutoff date: %)', partition_name, cutoff_date;

EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING 'Failed to drop partitions for %: %', cutoff_date, SQLERRM;
END;
$$ LANGUAGE plpgsql;

-- ================================================
-- Schedule: Create next day partitions (daily at UTC 02:00)
-- ================================================

SELECT cron.schedule (
        'create-next-day-partitions', '0 2 * * *', 'SELECT create_next_day_partitions();'
    );

-- ================================================
-- Schedule: Drop old partitions (daily at UTC 03:00)
-- ================================================

SELECT cron.schedule (
        'drop-old-daily-partitions', '0 3 * * *', 'SELECT drop_old_daily_partitions();'
    );

-- ================================================
-- Verification query (for manual checking)
-- ================================================

-- List all partitions
COMMENT ON FUNCTION create_next_day_partitions () IS 'Creates daily partitions for security_event tables 90 days in advance. Scheduled to run daily at UTC 02:00.';

COMMENT ON FUNCTION drop_old_daily_partitions () IS 'Drops daily partitions older than 90 days for security_event tables. Scheduled to run daily at UTC 03:00.';