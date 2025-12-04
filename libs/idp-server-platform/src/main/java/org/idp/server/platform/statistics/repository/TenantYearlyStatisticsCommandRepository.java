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

import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

/**
 * Command repository for yearly TenantStatistics
 *
 * <p>Provides write operations for yearly tenant statistics summary.
 *
 * <p>Note: Monthly breakdown is available via statistics_monthly table, not duplicated here.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Increment yearly summary metric
 * repository.incrementYearlySummaryMetric(tenantId, "2025", "login_success_count", 1);
 *
 * // Increment YAU when new user becomes active
 * repository.incrementYau(tenantId, "2025", 1);
 * }</pre>
 */
public interface TenantYearlyStatisticsCommandRepository {

  /**
   * Increment a yearly summary metric
   *
   * <p>Creates new yearly record if (tenant_id, stat_year) doesn't exist. Updates yearly_summary
   * JSONB field.
   *
   * @param tenantId tenant identifier
   * @param statYear year in YYYY format (e.g., "2025")
   * @param metricName metric name to increment
   * @param increment value to add (can be negative for decrement)
   */
  void incrementYearlySummaryMetric(
      TenantIdentifier tenantId, String statYear, String metricName, int increment);

  /**
   * Increment yearly summary YAU
   *
   * <p>Creates new yearly record if (tenant_id, stat_year) doesn't exist. Increments
   * yearly_summary.yau by the specified amount.
   *
   * @param tenantId tenant identifier
   * @param statYear year in YYYY format (e.g., "2025")
   * @param increment value to add to YAU
   */
  void incrementYau(TenantIdentifier tenantId, String statYear, int increment);
}
