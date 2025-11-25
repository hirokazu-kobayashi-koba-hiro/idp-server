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
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

/**
 * Command repository for TenantStatistics
 *
 * <p>Provides write operations for daily tenant statistics.
 *
 * <p>Currently supports only real-time metric increments used by SecurityEventHandler.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Increment login_success_count by 1
 * repository.incrementMetric(tenantId, LocalDate.now(), "login_success_count", 1);
 *
 * // Increment DAU metric
 * repository.incrementMetric(tenantId, LocalDate.now(), "dau", 1);
 * }</pre>
 *
 * @see TenantStatistics
 * @see TenantStatisticsQueryRepository
 */
public interface TenantStatisticsCommandRepository {

  /**
   * Increment a numeric metric value (real-time update)
   *
   * <p>Creates new record if (tenant_id, stat_date) doesn't exist. If metric doesn't exist,
   * initializes to increment value.
   *
   * <p>Leverages PostgreSQL's ON CONFLICT DO UPDATE and MySQL's ON DUPLICATE KEY UPDATE.
   *
   * <p>Example:
   *
   * <pre>{@code
   * // Increment login_success_count by 1
   * repository.incrementMetric(tenantId, LocalDate.now(), "login_success_count", 1);
   * }</pre>
   *
   * @param tenantId tenant identifier
   * @param date statistics date
   * @param metricName metric name to increment
   * @param increment value to add (can be negative for decrement)
   */
  void incrementMetric(TenantIdentifier tenantId, LocalDate date, String metricName, int increment);
}
