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

package org.idp.server.platform.statistics.repository;

import java.time.LocalDate;
import java.util.List;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.statistics.StatisticsEventRecord;

/**
 * Command repository for statistics events batch processing.
 *
 * <p>Provides batch upsert operations for the normalized statistics_events table. This repository
 * is designed for high-throughput writes by accumulating events in memory and flushing
 * periodically.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * List<StatisticsEventRecord> records = List.of(
 *     new StatisticsEventRecord(tenant1, LocalDate.now(), "login_success", 10),
 *     new StatisticsEventRecord(tenant1, LocalDate.now(), "issue_token_success", 5),
 *     new StatisticsEventRecord(tenant2, LocalDate.now(), "login_success", 3)
 * );
 * repository.batchUpsert(records);
 * }</pre>
 *
 * <p>The upsert operation atomically increments the count for existing rows or inserts new rows.
 *
 * @see StatisticsEventRecord
 */
public interface StatisticsEventsCommandRepository {

  /**
   * Increment a single statistics event.
   *
   * <p>If a row with the same (tenant_id, stat_date, event_type) exists, the count is incremented.
   * Otherwise, a new row is inserted with count = 1.
   *
   * @param tenantId tenant identifier
   * @param statDate the date of the event
   * @param eventType the type of event
   */
  void increment(TenantIdentifier tenantId, LocalDate statDate, String eventType);

  /**
   * Batch upsert statistics event records.
   *
   * <p>For each record, if a row with the same (tenant_id, stat_date, event_type) exists, the count
   * is incremented. Otherwise, a new row is inserted.
   *
   * <p>This operation is designed to be efficient for bulk writes:
   *
   * <ul>
   *   <li>Uses multi-row INSERT ... ON CONFLICT for PostgreSQL
   *   <li>Uses multi-row INSERT ... ON DUPLICATE KEY UPDATE for MySQL
   * </ul>
   *
   * @param records list of statistics event records to upsert
   */
  void batchUpsert(List<StatisticsEventRecord> records);
}
