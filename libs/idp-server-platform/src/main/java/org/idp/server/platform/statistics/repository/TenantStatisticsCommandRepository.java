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
 * <p>Provides write operations for monthly tenant statistics with daily breakdown.
 *
 * <p>Uses monthly rows with JSONB daily_metrics for efficient storage and querying.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Increment daily login_success_count by 1
 * repository.incrementDailyMetric(tenantId, "2025-01", "15", "login_success_count", 1);
 *
 * // Increment monthly summary metric
 * repository.incrementMonthlySummaryMetric(tenantId, "2025-01", "total_logins", 1);
 * }</pre>
 *
 * @see TenantStatisticsQueryRepository
 */
public interface TenantStatisticsCommandRepository {

  /**
   * Increment a daily metric value within the monthly record
   *
   * <p>Creates new monthly record if (tenant_id, stat_month) doesn't exist. Updates nested JSONB
   * structure for the specific day and metric.
   *
   * <p>Leverages PostgreSQL's jsonb_set() and MySQL's JSON_SET() for partial updates.
   *
   * <p>Example:
   *
   * <pre>{@code
   * // Increment login_success_count for day 15 in January 2025
   * LocalDate monthStart = LocalDate.of(2025, 1, 1);
   * repository.incrementDailyMetric(tenantId, monthStart, "15", "login_success_count", 1);
   * }</pre>
   *
   * @param tenantId tenant identifier
   * @param statMonth first day of month (e.g., 2025-01-01 for January 2025)
   * @param day day of month as string (e.g., "1", "15", "31")
   * @param metricName metric name to increment
   * @param increment value to add (can be negative for decrement)
   */
  void incrementDailyMetric(
      TenantIdentifier tenantId, LocalDate statMonth, String day, String metricName, int increment);

  /**
   * Increment a monthly summary metric
   *
   * <p>Creates new monthly record if (tenant_id, stat_month) doesn't exist. Updates monthly_summary
   * JSONB field.
   *
   * <p>Example:
   *
   * <pre>{@code
   * // Increment total logins for January 2025
   * LocalDate monthStart = LocalDate.of(2025, 1, 1);
   * repository.incrementMonthlySummaryMetric(tenantId, monthStart, "total_logins", 1);
   * }</pre>
   *
   * @param tenantId tenant identifier
   * @param statMonth first day of month (e.g., 2025-01-01 for January 2025)
   * @param metricName metric name to increment
   * @param increment value to add (can be negative for decrement)
   */
  void incrementMonthlySummaryMetric(
      TenantIdentifier tenantId, LocalDate statMonth, String metricName, int increment);

  /**
   * Increment monthly summary MAU and set cumulative MAU in daily metrics
   *
   * <p>This method atomically:
   *
   * <ol>
   *   <li>Increments monthly_summary.mau by the specified amount
   *   <li>Sets daily_metrics[day].mau to the new cumulative MAU value
   * </ol>
   *
   * <p>This ensures daily_metrics contains the running total of unique users up to each day.
   *
   * <p>Example:
   *
   * <pre>{@code
   * // After this call, if monthly MAU was 10, it becomes 11
   * // and daily_metrics["15"].mau is set to 11
   * LocalDate monthStart = LocalDate.of(2025, 1, 1);
   * repository.incrementMauWithDailyCumulative(tenantId, monthStart, "15", 1);
   * }</pre>
   *
   * @param tenantId tenant identifier
   * @param statMonth first day of month (e.g., 2025-01-01 for January 2025)
   * @param day day of month as string (e.g., "1", "15", "31")
   * @param increment value to add to MAU
   */
  void incrementMauWithDailyCumulative(
      TenantIdentifier tenantId, LocalDate statMonth, String day, int increment);
}
