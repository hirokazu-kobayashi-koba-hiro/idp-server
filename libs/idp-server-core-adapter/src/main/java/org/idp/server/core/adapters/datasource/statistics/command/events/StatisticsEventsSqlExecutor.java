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

package org.idp.server.core.adapters.datasource.statistics.command.events;

import java.time.LocalDate;
import java.util.List;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.statistics.StatisticsEventRecord;

/**
 * SQL executor interface for statistics events operations.
 *
 * <p>Implementations provide database-specific upsert operations for the statistics_event_buckets
 * table (Issue #1443). Each UPSERT picks a bucket_id to scatter writes across N rows per logical
 * key (tenant_id, stat_date, event_type), eliminating hot-row lock contention. Reads aggregate with
 * SUM(count) GROUP BY (tenant_id, stat_date, event_type).
 */
public interface StatisticsEventsSqlExecutor {

  /**
   * Increment a single statistics event.
   *
   * @param tenantId tenant identifier
   * @param statDate the date of the event
   * @param eventType the type of event
   */
  void increment(TenantIdentifier tenantId, LocalDate statDate, String eventType);

  /**
   * Batch upsert statistics event records.
   *
   * @param records list of statistics event records to upsert
   */
  void batchUpsert(List<StatisticsEventRecord> records);
}
