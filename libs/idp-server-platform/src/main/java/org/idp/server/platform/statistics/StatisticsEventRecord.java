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

package org.idp.server.platform.statistics;

import java.time.LocalDate;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

/**
 * Represents a statistics event record for batch processing.
 *
 * <p>Used to accumulate event counts in memory and batch-write to the statistics_events table.
 *
 * <p>Example:
 *
 * <pre>{@code
 * var record = new StatisticsEventRecord(tenantId, LocalDate.now(), "login_success", 5);
 * repository.batchUpsert(List.of(record));
 * }</pre>
 *
 * @param tenantId the tenant identifier
 * @param statDate the date of the statistics
 * @param eventType the type of event (e.g., login_success, issue_token_success, dau, mau)
 * @param count the aggregated count for this event type
 */
public record StatisticsEventRecord(
    TenantIdentifier tenantId, LocalDate statDate, String eventType, long count) {

  public StatisticsEventRecord {
    if (tenantId == null) {
      throw new IllegalArgumentException("tenantId must not be null");
    }
    if (statDate == null) {
      throw new IllegalArgumentException("statDate must not be null");
    }
    if (eventType == null || eventType.isEmpty()) {
      throw new IllegalArgumentException("eventType must not be null or empty");
    }
    if (count < 0) {
      throw new IllegalArgumentException("count must not be negative");
    }
  }

  public String tenantIdValue() {
    return tenantId.value();
  }
}
